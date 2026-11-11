//! Deploys and rollback (brief §6), on Windows (panel/docs/plan.md §1).
//!
//! - **Releases on disk.** Each release is a folder `<releases>\<version>` holding `hub\lib\*.jar`, `world\lib\*.jar`,
//!   `release.json` (version, commit, build time, commits since the previous release) and `schema.sql` (the hub's
//!   schema statements, from `hub --print-schema`). A release arrives as a zip with a minisign signature; the signature
//!   is checked against the configured public key before the zip is opened at all. `.verified` (the zip's SHA-256)
//!   marks a release as checked; the panel deploys nothing without it.
//! - **One link per service.** `<nodes>\<service>` is a directory junction (a symlink off Windows) to the release the
//!   service runs; the services' class paths go through it. Game data (`C:\lotus\game\.data`) never moves.
//! - **Deploy.** Backup first when the schema changes; lock logins; the in-game update timer (cancellable); stop the
//!   worlds and the hub (each saves everyone); point the hub at the new release, start it, health-check; then each world
//!   in turn. A failed check puts that service back on its old release, starts the rest of the worlds on theirs, and
//!   stops: the remaining worlds stay on the old version. Then logins are unlocked.
//! - **Rollback** is a deploy of the previous release through the same flow. Schema statements are additive and the
//!   hub applies them at start, so an older release runs on a newer schema; a statement that drops or rewrites data
//!   blocks the one-click rollback, and the page says so before the deploy starts.

use std::fs;
use std::io::{self, Read, Seek, Write};
use std::path::{Path, PathBuf};
use std::sync::Arc;

use async_trait::async_trait;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use time::OffsetDateTime;
use tokio_util::sync::CancellationToken;

use crate::control::{ControlError, Controller, MAX_TIMER_SECONDS, WorldUnit};
use crate::hub::Actor;
use crate::jobs::JobState;

pub const MANIFEST: &str = "release.json";
pub const SCHEMA: &str = "schema.sql";
pub const VERIFIED: &str = ".verified";
const HISTORY: &str = "history.json";
/// Releases kept by housekeeping (brief §6 via the plan): the newest five, plus whatever is running or was before.
pub const KEEP_RELEASES: usize = 5;
/// A release zip larger than this is refused (the game's jars are well under 200 MB).
const MAX_UNPACKED: u64 = 1024 * 1024 * 1024;
/// Top-level entries a release may contain.
const ALLOWED_TOP: [&str; 4] = ["hub", "world", MANIFEST, SCHEMA];

/// `release.json`, written by the release build (deploy/windows/make-release.ps1).
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct Manifest {
    pub version: String,
    pub commit: String,
    /// RFC 3339.
    pub built_at: String,
    /// One-line commit subjects since the previous release, newest first.
    #[serde(default)]
    pub commits: Vec<String>,
}

#[derive(Debug, Clone)]
pub struct Release {
    pub manifest: Manifest,
    pub schema: Vec<String>,
    /// The verified zip's SHA-256 (empty when it was never verified).
    pub sha256: String,
}

impl Release {
    pub fn version(&self) -> &str {
        &self.manifest.version
    }

    pub fn verified(&self) -> bool {
        !self.sha256.is_empty()
    }

    /// The commit as the page shows it (10 characters).
    pub fn short_commit(&self) -> String {
        self.manifest.commit.chars().take(10).collect()
    }
}

/// A version is a folder name: letters, digits, `.`, `-`, `_`; not starting with a dot; at most 64.
pub fn valid_version(v: &str) -> bool {
    !v.is_empty()
        && v.len() <= 64
        && !v.starts_with('.')
        && v.chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_'))
}

fn sha256_of(mut r: impl Read) -> io::Result<String> {
    let mut h = Sha256::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = r.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(h.finalize().iter().fold(String::new(), |mut s, b| {
        use std::fmt::Write as _;
        let _ = write!(s, "{b:02x}");
        s
    }))
}

/// Brief §6: checks `zip` against its minisign signature and the configured public key (the base64 line of a minisign
/// public key). Returns the zip's SHA-256.
pub fn verify_signature(zip: &Path, signature: &str, public_key: &str) -> anyhow::Result<String> {
    let pk = minisign::PublicKey::from_base64(public_key.trim())
        .map_err(|e| anyhow::anyhow!("the release public key in panel.toml is not valid: {e}"))?;
    let sig = minisign::SignatureBox::from_string(signature).map_err(|e| anyhow::anyhow!("bad signature file: {e}"))?;
    let mut f = fs::File::open(zip)?;
    minisign::verify(&pk, &sig, &mut f, true, false, false)
        .map_err(|e| anyhow::anyhow!("the signature does not match: {e}"))?;
    f.rewind()?;
    Ok(sha256_of(f)?)
}

/// Signs `zip` with a minisign secret key (the release build); returns the signature file's text.
pub fn sign(zip: &Path, secret_key: &str, password: Option<String>, comment: &str) -> anyhow::Result<String> {
    let sk_box = minisign::SecretKeyBox::from_string(secret_key)?;
    let sk = sk_box.into_secret_key(password)?;
    let f = fs::File::open(zip)?;
    Ok(minisign::sign(None, &sk, f, Some(comment), None)?.into_string())
}

/// A new release key pair: (public key base64 line, secret key file text).
pub fn keygen(password: Option<String>) -> anyhow::Result<(String, String)> {
    let kp = minisign::KeyPair::generate_encrypted_keypair(password)?;
    Ok((kp.pk.to_base64(), kp.sk.to_box(None)?.into_string()))
}

/// Brief §6: checks the signature and unpacks from the same open file, so the zip can't be swapped in between
/// (security review F2). Unpacks into `<releases>\<version>` (version from its `release.json`), marks it verified
/// and returns (version, SHA-256).
pub fn verify_and_install(
    zip: &Path,
    signature: &str,
    public_key: &str,
    releases: &Path,
) -> anyhow::Result<(String, String)> {
    let pk = minisign::PublicKey::from_base64(public_key.trim())
        .map_err(|e| anyhow::anyhow!("the release public key in panel.toml is not valid: {e}"))?;
    let sig = minisign::SignatureBox::from_string(signature).map_err(|e| anyhow::anyhow!("bad signature file: {e}"))?;
    let mut f = fs::File::open(zip)?;
    minisign::verify(&pk, &sig, &mut f, true, false, false)
        .map_err(|e| anyhow::anyhow!("the signature does not match: {e}"))?;
    f.rewind()?;
    let sha = sha256_of(&mut f)?;
    f.rewind()?;
    let version = install_from(f, &sha, releases)?;
    Ok((version, sha))
}

fn install_from(file: fs::File, sha256: &str, releases: &Path) -> anyhow::Result<String> {
    let mut archive = zip::ZipArchive::new(file)?;
    fs::create_dir_all(releases)?;
    let staging = releases.join(format!(".staging-{}", &sha256[..sha256.len().min(16)]));
    if staging.exists() {
        fs::remove_dir_all(&staging)?;
    }
    let result = unpack(&mut archive, &staging).and_then(|()| {
        let manifest: Manifest = serde_json::from_slice(&fs::read(staging.join(MANIFEST))?)?;
        anyhow::ensure!(
            valid_version(&manifest.version),
            "the release has an invalid version name"
        );
        anyhow::ensure!(staging.join("hub").join("lib").is_dir(), "the release has no hub\\lib");
        anyhow::ensure!(
            staging.join("world").join("lib").is_dir(),
            "the release has no world\\lib"
        );
        let dest = releases.join(&manifest.version);
        anyhow::ensure!(!dest.exists(), "release {} is already here", manifest.version);
        fs::write(staging.join(VERIFIED), sha256)?;
        fs::rename(&staging, &dest)?;
        Ok(manifest.version)
    });
    if result.is_err() {
        let _ = fs::remove_dir_all(&staging);
    }
    result
}

fn unpack(archive: &mut zip::ZipArchive<fs::File>, to: &Path) -> anyhow::Result<()> {
    let mut total = 0u64;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i)?;
        // Zip-slip: only names that stay inside, and only the expected top-level entries.
        let Some(name) = entry.enclosed_name() else {
            anyhow::bail!("the release has an unsafe path: {}", entry.name().unwrap_or_default());
        };
        let top = name
            .components()
            .next()
            .map(|c| c.as_os_str().to_string_lossy().into_owned())
            .unwrap_or_default();
        anyhow::ensure!(
            ALLOWED_TOP.contains(&top.as_str()),
            "unexpected entry in the release: {top}"
        );
        let path = to.join(&name);
        if entry.is_dir() {
            fs::create_dir_all(&path)?;
            continue;
        }
        total += entry.size();
        anyhow::ensure!(total <= MAX_UNPACKED, "the release is too large");
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent)?;
        }
        let mut out = fs::File::create(&path)?;
        io::copy(&mut (&mut entry).take(MAX_UNPACKED), &mut out)?;
        out.flush()?;
    }
    Ok(())
}

/// Reads one release folder.
pub fn read(releases: &Path, version: &str) -> Option<Release> {
    if !valid_version(version) {
        return None;
    }
    let dir = releases.join(version);
    let manifest: Manifest = serde_json::from_slice(&fs::read(dir.join(MANIFEST)).ok()?).ok()?;
    let schema = fs::read_to_string(dir.join(SCHEMA))
        .unwrap_or_default()
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty())
        .map(str::to_owned)
        .collect();
    let sha256 = fs::read_to_string(dir.join(VERIFIED))
        .unwrap_or_default()
        .trim()
        .to_owned();
    Some(Release {
        manifest,
        schema,
        sha256,
    })
}

/// Every release on disk, newest build first.
pub fn list(releases: &Path) -> Vec<Release> {
    let mut out: Vec<Release> = fs::read_dir(releases)
        .map(|d| {
            d.flatten()
                .filter(|e| e.path().is_dir())
                .filter_map(|e| read(releases, &e.file_name().to_string_lossy()))
                .collect()
        })
        .unwrap_or_default();
    out.sort_by(|a, b| b.manifest.built_at.cmp(&a.manifest.built_at));
    out
}

/// What a deploy changes in the database.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct SchemaDiff {
    /// Statements the new release has and the running one doesn't (the hub applies them at start).
    pub added: Vec<String>,
    /// Of those, the ones an older release can't run on (they drop or rewrite data).
    pub irreversible: Vec<String>,
}

/// A statement that removes or rewrites data: after it, the older release may not work.
pub fn irreversible(statement: &str) -> bool {
    let s = statement.to_ascii_uppercase();
    ["DROP ", "TRUNCATE ", "DELETE ", "RENAME ", " TYPE ", "UPDATE "]
        .iter()
        .any(|w| s.contains(w))
}

pub fn schema_diff(running: &[String], new: &[String]) -> SchemaDiff {
    let added: Vec<String> = new.iter().filter(|s| !running.contains(s)).cloned().collect();
    let irreversible = added.iter().filter(|s| irreversible(s)).cloned().collect();
    SchemaDiff { added, irreversible }
}

// ---------------------------------------------------------------- links

/// Which release each service runs.
pub trait Links: Send + Sync {
    fn current(&self, unit: &str) -> Option<String>;
    fn point(&self, unit: &str, version: &str) -> io::Result<()>;
}

/// `<nodes>\<unit>` → `<releases>\<version>`: a directory junction on Windows (no special rights needed), a symlink
/// elsewhere (tests).
pub struct FsLinks {
    pub releases: PathBuf,
    pub nodes: PathBuf,
}

impl Links for FsLinks {
    fn current(&self, unit: &str) -> Option<String> {
        let target = read_link(&self.nodes.join(unit))?;
        target.file_name().map(|n| n.to_string_lossy().into_owned())
    }

    fn point(&self, unit: &str, version: &str) -> io::Result<()> {
        if !valid_version(version) || !self.releases.join(version).is_dir() {
            return Err(io::Error::new(io::ErrorKind::NotFound, format!("no release {version}")));
        }
        fs::create_dir_all(&self.nodes)?;
        let link = self.nodes.join(unit);
        remove_link(&link)?;
        make_link(&self.releases.join(version), &link)
    }
}

/// Reads a junction (Windows) or symlink (elsewhere) with std, which opens it shared: the junction crate's reader
/// opens it exclusively and fails whenever another process (the running JVM, an antivirus scan) has it open.
fn read_link(link: &Path) -> Option<PathBuf> {
    match fs::read_link(link) {
        Ok(t) => Some(t),
        Err(e) => {
            if e.kind() != io::ErrorKind::NotFound {
                tracing::warn!(error = %e, link = %link.display(), "could not read a release link");
            }
            None
        }
    }
}

/// Removes the link itself, never its target: on Windows a junction is an empty directory to `RemoveDirectory`.
fn remove_link(link: &Path) -> io::Result<()> {
    match fs::symlink_metadata(link) {
        Ok(m) if cfg!(windows) || m.is_dir() && !m.file_type().is_symlink() => fs::remove_dir(link),
        Ok(_) => fs::remove_file(link),
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(()),
        Err(e) => Err(e),
    }
}

#[cfg(windows)]
fn make_link(target: &Path, link: &Path) -> io::Result<()> {
    junction::create(target, link)
}

#[cfg(not(windows))]
fn make_link(target: &Path, link: &Path) -> io::Result<()> {
    std::os::unix::fs::symlink(target, link)
}

// ---------------------------------------------------------------- history

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Deployed {
    pub version: String,
    pub at: String,
    pub actor: String,
}

/// Deployed versions, newest first (`<releases>\history.json`).
pub fn history(releases: &Path) -> Vec<Deployed> {
    fs::read(releases.join(HISTORY))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_default()
}

fn record(releases: &Path, version: &str, actor: &str, at: OffsetDateTime) -> io::Result<()> {
    let mut h = history(releases);
    h.insert(
        0,
        Deployed {
            version: version.to_owned(),
            at: at
                .format(&time::format_description::well_known::Rfc3339)
                .unwrap_or_default(),
            actor: actor.to_owned(),
        },
    );
    h.truncate(50);
    let tmp = releases.join(".history.tmp");
    fs::write(&tmp, serde_json::to_vec_pretty(&h).map_err(io::Error::other)?)?;
    fs::rename(tmp, releases.join(HISTORY))
}

/// The release deployed before `current` (rollback target).
pub fn previous(releases: &Path, current: &str) -> Option<String> {
    history(releases)
        .into_iter()
        .map(|d| d.version)
        .find(|v| v != current && releases.join(v).is_dir())
}

/// Housekeeping: releases to delete (all but the newest [`KEEP_RELEASES`], never one a service runs or the rollback
/// target).
pub fn removable(releases: &Path, in_use: &[String]) -> Vec<String> {
    let all = list(releases);
    let protected: Vec<String> = in_use
        .iter()
        .flat_map(|v| [Some(v.clone()), previous(releases, v)])
        .flatten()
        .collect();
    all.iter()
        .skip(KEEP_RELEASES)
        .map(|r| r.version().to_owned())
        .filter(|v| !protected.contains(v))
        .collect()
}

pub fn remove(releases: &Path, version: &str) -> io::Result<()> {
    if !valid_version(version) {
        return Err(io::Error::new(io::ErrorKind::InvalidInput, "bad version"));
    }
    fs::remove_dir_all(releases.join(version))
}

// ---------------------------------------------------------------- backup before migrations

/// A database backup taken before a deploy changes the schema (brief §6 step 2; phase 8 adds the schedule).
#[async_trait]
pub trait Backup: Send + Sync {
    /// Returns where the backup went.
    async fn before_deploy(&self, version: &str) -> anyhow::Result<String>;
}

// ---------------------------------------------------------------- the deploy job

pub struct Deployer {
    pub control: Arc<Controller>,
    pub links: Arc<dyn Links>,
    pub backup: Arc<dyn Backup>,
    pub releases: PathBuf,
    pub settings: DeploySettings,
}

/// `[deploy]` as the pages need it.
#[derive(Debug, Clone, Default)]
pub struct DeploySettings {
    pub public_key: String,
    pub ci_repo: String,
    pub incoming: PathBuf,
    pub timer_seconds: i32,
}

/// What the deploy page shows before anything starts.
#[derive(Debug, Clone)]
pub struct Plan {
    pub release: Release,
    /// Each service and the release it runs now.
    pub running: Vec<(String, Option<String>)>,
    pub schema: SchemaDiff,
    /// Set when the deploy can't start (a service without a link, an unverified release...).
    pub blocked: Option<String>,
    /// Going back to an older release over statements it can't run on.
    pub rollback_blocked: Option<String>,
}

impl Deployer {
    fn units(&self) -> Vec<String> {
        let t = &self.control.topology;
        std::iter::once(t.hub.clone())
            .chain(t.worlds.iter().map(|w| w.unit.clone()))
            .collect()
    }

    /// The version the hub runs (the reference for the schema diff).
    pub fn current(&self) -> Option<String> {
        self.links.current(&self.control.topology.hub)
    }

    pub fn plan(&self, version: &str) -> Option<Plan> {
        let release = read(&self.releases, version)?;
        let running: Vec<(String, Option<String>)> = self
            .units()
            .into_iter()
            .map(|u| (u.clone(), self.links.current(&u)))
            .collect();
        let current = self.current().and_then(|v| read(&self.releases, &v));
        let schema = current.as_ref().map_or_else(
            || schema_diff(&[], &release.schema),
            |c| schema_diff(&c.schema, &release.schema),
        );
        // Going back: what the running release added on top of the target, if any of it rewrote data.
        let rollback_blocked = current.as_ref().and_then(|c| {
            let back = schema_diff(&release.schema, &c.schema);
            (!back.irreversible.is_empty()).then(|| {
                format!(
                    "{} changed data in a way {} can't run on ({}). Restore a backup instead.",
                    c.version(),
                    release.version(),
                    back.irreversible.join("; ")
                )
            })
        });
        let blocked = if !release.verified() {
            Some("This release was never verified against the signing key.".to_owned())
        } else if let Some((u, _)) = running.iter().find(|(_, v)| v.is_none()) {
            Some(format!(
                "{u} has no release link yet; run the installer once to create it."
            ))
        } else if running.iter().all(|(_, v)| v.as_deref() == Some(version)) {
            Some("Every service already runs this release.".to_owned())
        } else {
            None
        };
        Some(Plan {
            release,
            running,
            schema,
            blocked,
            rollback_blocked,
        })
    }

    /// Starts the deploy job. The caller checked the password; `rollback` only changes the job's name.
    pub async fn deploy(
        self: &Arc<Self>,
        version: &str,
        seconds: i32,
        message: String,
        actor: Actor,
        actor_name: &str,
    ) -> Result<i64, ControlError> {
        if !(1..=MAX_TIMER_SECONDS).contains(&seconds) {
            return Err(ControlError::Invalid(format!(
                "The timer must be 1 to {MAX_TIMER_SECONDS} seconds."
            )));
        }
        let plan = self
            .plan(version)
            .ok_or_else(|| ControlError::Invalid(format!("There is no release {version}.")))?;
        if let Some(why) = plan.blocked.clone().or_else(|| plan.rollback_blocked.clone()) {
            return Err(ControlError::Invalid(why));
        }
        let (job, cancel) = self.control.begin(&format!("deploy {version}"), actor_name).await?;
        let this = Arc::clone(self);
        let actor_name = actor_name.to_owned();
        tokio::spawn(async move {
            let state = this
                .run(job, &cancel, &plan, seconds, &message, &actor, &actor_name)
                .await;
            if state == JobState::Failed {
                this.control
                    .alerts
                    .alert(
                        "Deploy failed",
                        &format!("Deploying {} failed; see job #{job}.", plan.release.version()),
                    )
                    .await;
            }
            this.control.end(job, state).await;
        });
        Ok(job)
    }

    #[allow(clippy::too_many_arguments, clippy::too_many_lines)] // one step per brief §6 item, in order
    async fn run(
        &self,
        job: i64,
        cancel: &CancellationToken,
        plan: &Plan,
        seconds: i32,
        message: &str,
        actor: &Actor,
        actor_name: &str,
    ) -> JobState {
        let c = &self.control;
        let version = plan.release.version().to_owned();
        let old = |unit: &str| {
            plan.running
                .iter()
                .find(|(u, _)| u == unit)
                .and_then(|(_, v)| v.clone())
                .unwrap_or_default()
        };
        // 1. What changes.
        let m = &plan.release.manifest;
        c.log(
            job,
            &format!("release {version} (commit {}, built {})", m.commit, m.built_at),
        )
        .await;
        for (u, v) in &plan.running {
            c.log(job, &format!("{u} runs {}", v.as_deref().unwrap_or("?"))).await;
        }
        // 2. Backup before the schema changes.
        if plan.schema.added.is_empty() {
            c.log(job, "no schema changes; no backup needed").await;
        } else {
            c.log(
                job,
                &format!("{} new schema statement(s); backing up first", plan.schema.added.len()),
            )
            .await;
            match self.backup.before_deploy(&version).await {
                Ok(file) => c.log(job, &format!("backup written: {file}")).await,
                Err(e) => {
                    c.log(job, &format!("backup failed, nothing was changed: {e}")).await;
                    return JobState::Failed;
                }
            }
        }
        // 3-4. Lock logins and run the in-game timer.
        if !c.countdown(job, cancel, 0, seconds, message, actor).await {
            return JobState::Cancelled;
        }
        // 5. The game saves everyone and stops gracefully.
        let worlds: Vec<WorldUnit> = c.topology.worlds.clone();
        let hub = c.topology.hub.clone();
        let mut ok = true;
        for w in worlds.iter().rev() {
            ok &= c.stop_unit(job, &w.unit).await;
        }
        ok &= c.stop_unit(job, &hub).await;
        if !ok {
            c.log(
                job,
                "a service did not stop; nothing was switched. Start it again from the Server page.",
            )
            .await;
            return JobState::Failed;
        }
        // 6-8. Switch and start the hub, then each world, each health-checked.
        if let Err(e) = self.links.point(&hub, &version) {
            c.log(job, &format!("could not switch {hub}: {e}")).await;
            let _ = c.start_sequence(job, &worlds, true).await;
            self.unlock(job, actor).await;
            return JobState::Failed;
        }
        c.log(job, &format!("{hub} -> {version}")).await;
        if !c.start_sequence(job, &[], true).await {
            c.log(
                job,
                &format!(
                    "{hub} failed its health check on {version}; putting it back on {}",
                    old(&hub)
                ),
            )
            .await;
            let _ = c.stop_unit(job, &hub).await;
            if let Err(e) = self.links.point(&hub, &old(&hub)) {
                c.log(job, &format!("could not switch {hub} back: {e}")).await;
            }
            let _ = c.start_sequence(job, &worlds, true).await;
            self.unlock(job, actor).await;
            return JobState::Failed;
        }
        for (i, w) in worlds.iter().enumerate() {
            if let Err(e) = self.links.point(&w.unit, &version) {
                c.log(job, &format!("could not switch {}: {e}", w.unit)).await;
                let _ = c.start_sequence(job, &worlds[i..], false).await;
                self.unlock(job, actor).await;
                return JobState::Failed;
            }
            c.log(job, &format!("{} -> {version}", w.unit)).await;
            if !c.start_sequence(job, std::slice::from_ref(w), false).await {
                let back = old(&w.unit);
                c.log(
                    job,
                    &format!("{} failed its health check; putting it back on {back}", w.unit),
                )
                .await;
                let _ = c.stop_unit(job, &w.unit).await;
                if let Err(e) = self.links.point(&w.unit, &back) {
                    c.log(job, &format!("could not switch {} back: {e}", w.unit)).await;
                }
                let _ = c.start_sequence(job, &worlds[i..], false).await;
                let rest: Vec<&str> = worlds[i..].iter().map(|w| w.unit.as_str()).collect();
                c.log(
                    job,
                    &format!("stopped the rollout; {} stay on the old version", rest.join(", ")),
                )
                .await;
                self.unlock(job, actor).await;
                return JobState::Failed;
            }
        }
        self.unlock(job, actor).await;
        if let Err(e) = record(&self.releases, &version, actor_name, c.clock.now()) {
            c.log(job, &format!("could not record the deploy in the history: {e}"))
                .await;
        }
        c.log(job, &format!("deployed {version}")).await;
        JobState::Succeeded
    }

    async fn unlock(&self, job: i64, actor: &Actor) {
        let c = &self.control;
        match c.hub.set_login_lock(actor, 0, false, "").await {
            Ok(()) => c.log(job, "logins unlocked").await,
            Err(e) => c.log(job, &format!("could not unlock logins: {e}")).await,
        }
    }
}
