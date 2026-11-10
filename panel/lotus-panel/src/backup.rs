//! Database backups (brief §6 step 2, §7).
//!
//! - **Dump.** `pg_dump --format=custom` of the game database as the read-only `lotus_backup` role (made by the
//!   installer); its password is a DPAPI secret passed through `PGPASSWORD`, never the command line.
//! - **Encrypt.** The dump is encrypted with age to the owner's public key(s), set on the Settings page, and to the
//!   panel's own key (a DPAPI secret), so the panel can verify and restore without the owner's key. Only the `.age`
//!   file stays on disk.
//! - **Rotate.** Nightly backups are kept 14 daily, 8 weekly and 6 monthly (the newest of each day, ISO week and month);
//!   pre-deploy backups, the newest five.
//! - **Off-site.** Each new backup is copied to an S3-compatible bucket when one is configured (`SigV4` PUT).
//! - **Verify** restores a backup into a scratch database (`lotus_verify`) and runs a sanity query.
//! - **Restore** to live is developer-only (the page checks the password and a typed confirmation), runs as a control
//!   job, and locks and stops every world and the hub first; the hub's own role restores (it owns the tables).

use std::collections::HashSet;
use std::fs;
use std::io::{self, BufReader, BufWriter, Write};
use std::path::{Path, PathBuf};
use std::str::FromStr;
use std::sync::Arc;

use age::secrecy::ExposeSecret;
use async_trait::async_trait;
use hmac::{Hmac, KeyInit, Mac};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use time::{Date, OffsetDateTime};
use tokio_util::sync::CancellationToken;

use crate::config;
use crate::control::{ControlError, Controller};
use crate::hub::Actor;
use crate::jobs::{Alerts, JobState, Settings};

/// Settings key for [`BackupSettings`].
pub const SETTINGS_KEY: &str = "backups";
/// Settings key for the last verify of each backup.
const VERIFIED_KEY: &str = "backups.verified";
/// Settings key for the day the nightly backup last ran.
const LAST_NIGHTLY_KEY: &str = "backups.last_nightly";
/// Brief §7: 14 daily, 8 weekly, 6 monthly.
pub const KEEP_DAILY: usize = 14;
pub const KEEP_WEEKLY: usize = 8;
pub const KEEP_MONTHLY: usize = 6;
/// Pre-deploy backups kept (our choice: the last few deploys).
pub const KEEP_PRE_DEPLOY: usize = 5;
const NIGHTLY: &str = "lotus";
const PRE_DEPLOY: &str = "pre-deploy";
const EXT: &str = ".dump.age";
/// The scratch database verify restores into.
const VERIFY_DB: &str = "lotus_verify";

/// What the owner sets on the Settings page.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct BackupSettings {
    /// age public keys (`age1...`) whose holders can decrypt every backup.
    #[serde(default)]
    pub owner_recipients: Vec<String>,
    /// UTC time of the nightly backup, `HH:MM`.
    #[serde(default = "default_time")]
    pub time: String,
    #[serde(default)]
    pub s3: Option<S3>,
}

fn default_time() -> String {
    "04:00".into()
}

/// An S3-compatible bucket (AWS, Backblaze B2, Cloudflare R2, Wasabi, MinIO): path-style URLs.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct S3 {
    /// `https://s3.eu-central-1.amazonaws.com`, `https://<account>.r2.cloudflarestorage.com`, ...
    pub endpoint: String,
    pub region: String,
    pub bucket: String,
    #[serde(default)]
    pub prefix: String,
    pub access_key: String,
    pub secret_key: String,
}

/// One backup file on disk.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BackupFile {
    pub name: String,
    pub size: u64,
    pub at: OffsetDateTime,
    /// `nightly`, `pre-deploy` or `manual`.
    pub kind: &'static str,
    pub verified: Option<String>,
}

/// `pg_dump` and friends, as one role.
#[derive(Debug, Clone)]
pub struct PgTools {
    pub pg_bin: PathBuf,
    pub host: String,
    pub port: u16,
    pub database: String,
    pub user: String,
    pub password: String,
}

impl PgTools {
    pub fn from_config(c: &config::Backups, password: String) -> Self {
        Self {
            pg_bin: c.pg_bin.clone(),
            host: c.host.clone(),
            port: c.port,
            database: c.database.clone(),
            user: c.user.clone(),
            password,
        }
    }

    fn exe(&self, name: &str) -> PathBuf {
        let file = if cfg!(windows) {
            format!("{name}.exe")
        } else {
            name.to_owned()
        };
        self.pg_bin.join(file)
    }

    /// Runs one PostgreSQL tool with the connection options; returns its standard output.
    async fn run(&self, tool: &str, args: &[&std::ffi::OsStr]) -> anyhow::Result<String> {
        let out = tokio::process::Command::new(self.exe(tool))
            .arg("--no-password")
            .args([
                "--host",
                &self.host,
                "--port",
                &self.port.to_string(),
                "--username",
                &self.user,
            ])
            .args(args)
            .env("PGPASSWORD", &self.password)
            .kill_on_drop(true)
            .output()
            .await?;
        if !out.status.success() {
            anyhow::bail!(
                "{tool} failed: {}",
                String::from_utf8_lossy(&out.stderr)
                    .lines()
                    .rfind(|l| !l.trim().is_empty())
                    .unwrap_or("no output")
            );
        }
        Ok(String::from_utf8_lossy(&out.stdout).into_owned())
    }

    async fn psql(&self, database: &str, sql: &str) -> anyhow::Result<String> {
        self.run(
            "psql",
            &[
                "--dbname".as_ref(),
                database.as_ref(),
                "--tuples-only".as_ref(),
                "--no-align".as_ref(),
                "--set=ON_ERROR_STOP=1".as_ref(),
                "--command".as_ref(),
                sql.as_ref(),
            ],
        )
        .await
    }
}

/// The backup service.
pub struct Backups {
    /// Dumps and verifies (`lotus_backup`: read-only, may create the scratch database).
    pub reader: PgTools,
    /// Restores to live (the hub's role, owner of the game tables).
    pub writer: PgTools,
    /// The panel's own age identity (`AGE-SECRET-KEY-1...`).
    pub identity: String,
    pub dir: PathBuf,
    pub settings: Arc<dyn Settings>,
    pub alerts: Arc<dyn Alerts>,
    pub http: reqwest::Client,
    busy: tokio::sync::Mutex<()>,
}

impl Backups {
    pub fn new(
        reader: PgTools,
        writer: PgTools,
        identity: String,
        dir: PathBuf,
        settings: Arc<dyn Settings>,
        alerts: Arc<dyn Alerts>,
    ) -> Self {
        Self {
            reader,
            writer,
            identity,
            dir,
            settings,
            alerts,
            http: crate::net::http_client(),
            busy: tokio::sync::Mutex::new(()),
        }
    }

    pub async fn settings(&self) -> BackupSettings {
        self.settings
            .get(SETTINGS_KEY)
            .await
            .ok()
            .flatten()
            .and_then(|v| serde_json::from_value(v).ok())
            .unwrap_or_else(|| BackupSettings {
                time: default_time(),
                ..BackupSettings::default()
            })
    }

    /// Every recipient a backup is encrypted to: the owner's keys and the panel's own.
    fn recipients(&self, s: &BackupSettings) -> anyhow::Result<Vec<age::x25519::Recipient>> {
        let own = age::x25519::Identity::from_str(self.identity.trim())
            .map_err(|e| anyhow::anyhow!("the panel's backup key is not valid: {e}"))?
            .to_public();
        let mut all = vec![own];
        for r in &s.owner_recipients {
            all.push(
                age::x25519::Recipient::from_str(r.trim())
                    .map_err(|e| anyhow::anyhow!("owner key {r} is not valid: {e}"))?,
            );
        }
        Ok(all)
    }

    /// Dumps, encrypts and (when configured) copies one backup; returns its file name.
    pub async fn create(&self, prefix: &str, now: OffsetDateTime) -> anyhow::Result<String> {
        let _busy = self.busy.lock().await;
        let settings = self.settings().await;
        let recipients = self.recipients(&settings)?;
        tokio::fs::create_dir_all(&self.dir).await?;
        let name = format!("{prefix}-{}{EXT}", stamp(now));
        let plain = self.dir.join(format!(".{name}.plain"));
        let result = async {
            self.reader
                .run(
                    "pg_dump",
                    &[
                        "--format=custom".as_ref(),
                        "--file".as_ref(),
                        plain.as_os_str(),
                        self.reader.database.as_ref(),
                    ],
                )
                .await?;
            let (from, to) = (plain.clone(), self.dir.join(&name));
            tokio::task::spawn_blocking(move || encrypt(&from, &to, &recipients)).await??;
            Ok::<(), anyhow::Error>(())
        }
        .await;
        let _ = tokio::fs::remove_file(&plain).await;
        result?;
        if let Some(s3) = &settings.s3
            && let Err(e) = self.upload(s3, &name, now).await
        {
            self.alerts
                .alert(
                    "Backup copy failed",
                    &format!("{name} was made but not copied off-site: {e}"),
                )
                .await;
        }
        Ok(name)
    }

    /// Brief §7 nightly backup, then rotation; a failure alerts owners.
    pub async fn nightly(&self, now: OffsetDateTime) -> anyhow::Result<String> {
        match self.create(NIGHTLY, now).await {
            Ok(name) => {
                self.rotate();
                Ok(name)
            }
            Err(e) => {
                self.alerts
                    .alert("Backup failed", &format!("The nightly backup failed: {e}"))
                    .await;
                Err(e)
            }
        }
    }

    /// Runs the nightly backup when its time has come and it hasn't run today.
    pub async fn tick(&self, now: OffsetDateTime) {
        let s = self.settings().await;
        let (h, m) = parse_time(&s.time).unwrap_or((4, 0));
        if (now.hour(), now.minute()) < (h, m) {
            return;
        }
        let today = now.date().to_string();
        let last = self.settings.get(LAST_NIGHTLY_KEY).await.ok().flatten();
        if last.as_ref().and_then(|v| v.as_str()) == Some(today.as_str()) {
            return;
        }
        let _ = self
            .settings
            .set(LAST_NIGHTLY_KEY, &serde_json::Value::String(today), "panel")
            .await;
        if let Err(e) = self.nightly(now).await {
            tracing::warn!(error = %e, "nightly backup failed");
        }
    }

    /// Backups on disk, newest first (the installer's old plain `.dump` files are listed too).
    pub async fn list(&self) -> Vec<BackupFile> {
        let verified: std::collections::HashMap<String, String> = self
            .settings
            .get(VERIFIED_KEY)
            .await
            .ok()
            .flatten()
            .and_then(|v| serde_json::from_value(v).ok())
            .unwrap_or_default();
        let mut out: Vec<BackupFile> = fs::read_dir(&self.dir)
            .map(|d| {
                d.flatten()
                    .filter_map(|e| {
                        let name = e.file_name().to_string_lossy().into_owned();
                        // The panel's own database dumps (the installer's task) aren't game backups.
                        if name.starts_with('.')
                            || name.starts_with("lotus_panel")
                            || !(name.ends_with(EXT) || is_plain_dump(&name))
                        {
                            return None;
                        }
                        let meta = e.metadata().ok()?;
                        let at = parse_stamp(&name).unwrap_or_else(|| {
                            meta.modified().map_or(OffsetDateTime::UNIX_EPOCH, OffsetDateTime::from)
                        });
                        Some(BackupFile {
                            kind: kind_of(&name),
                            verified: verified.get(&name).cloned(),
                            name,
                            size: meta.len(),
                            at,
                        })
                    })
                    .collect()
            })
            .unwrap_or_default();
        out.sort_by_key(|f| std::cmp::Reverse(f.at));
        out
    }

    /// Brief §7 rotation (the brief's retention, approved by the owner): deletes nightly backups outside 14 daily, 8
    /// weekly and 6 monthly, and pre-deploy backups beyond the newest five. Never touches other files.
    pub fn rotate(&self) -> Vec<String> {
        let names: Vec<String> = fs::read_dir(&self.dir)
            .map(|d| {
                d.flatten()
                    .map(|e| e.file_name().to_string_lossy().into_owned())
                    .filter(|n| n.ends_with(EXT))
                    .collect()
            })
            .unwrap_or_default();
        let doomed = to_delete(&names);
        for n in &doomed {
            if let Err(e) = fs::remove_file(self.dir.join(n)) {
                tracing::warn!(error = %e, name = %n, "could not rotate a backup");
            }
        }
        doomed
    }

    /// Decrypts a backup into a temporary plain dump next to it.
    async fn decrypted(&self, name: &str) -> anyhow::Result<PathBuf> {
        anyhow::ensure!(safe_name(name), "no such backup");
        let src = self.dir.join(name);
        let out = self.dir.join(format!(".{name}.restore"));
        if is_plain_dump(name) {
            tokio::fs::copy(&src, &out).await?;
            return Ok(out);
        }
        let identity = age::x25519::Identity::from_str(self.identity.trim())
            .map_err(|e| anyhow::anyhow!("the panel's backup key is not valid: {e}"))?;
        let to = out.clone();
        tokio::task::spawn_blocking(move || decrypt(&src, &to, &identity)).await??;
        Ok(out)
    }

    /// Brief §7 verify: restore into the scratch database and count the accounts.
    pub async fn verify(&self, name: &str, now: OffsetDateTime) -> anyhow::Result<String> {
        let _busy = self.busy.lock().await;
        let plain = self.decrypted(name).await?;
        let result = async {
            let r = &self.reader;
            r.psql("postgres", &format!("DROP DATABASE IF EXISTS {VERIFY_DB}"))
                .await?;
            r.psql("postgres", &format!("CREATE DATABASE {VERIFY_DB}")).await?;
            let restored = r
                .run(
                    "pg_restore",
                    &[
                        "--no-owner".as_ref(),
                        "--no-privileges".as_ref(),
                        "--exit-on-error".as_ref(),
                        "--dbname".as_ref(),
                        VERIFY_DB.as_ref(),
                        plain.as_os_str(),
                    ],
                )
                .await;
            let counted = match &restored {
                Ok(_) => r.psql(VERIFY_DB, "SELECT count(*) FROM accounts").await,
                Err(_) => Ok(String::new()),
            };
            let _ = r
                .psql("postgres", &format!("DROP DATABASE IF EXISTS {VERIFY_DB}"))
                .await;
            restored?;
            Ok::<String, anyhow::Error>(format!("restored; {} accounts", counted?.trim()))
        }
        .await;
        let _ = tokio::fs::remove_file(&plain).await;
        let summary = result?;
        let mut verified: std::collections::HashMap<String, String> = self
            .settings
            .get(VERIFIED_KEY)
            .await
            .ok()
            .flatten()
            .and_then(|v| serde_json::from_value(v).ok())
            .unwrap_or_default();
        verified.insert(name.to_owned(), format!("{} UTC: {summary}", stamp_human(now)));
        verified.retain(|k, _| self.dir.join(k).exists());
        let _ = self
            .settings
            .set(VERIFIED_KEY, &serde_json::to_value(&verified)?, "panel")
            .await;
        Ok(summary)
    }

    /// Restores a backup over the live database (the services must be stopped).
    async fn restore_live(&self, name: &str) -> anyhow::Result<()> {
        let _busy = self.busy.lock().await;
        let plain = self.decrypted(name).await?;
        let result = self
            .writer
            .run(
                "pg_restore",
                &[
                    "--clean".as_ref(),
                    "--if-exists".as_ref(),
                    "--no-owner".as_ref(),
                    "--no-privileges".as_ref(),
                    "--single-transaction".as_ref(),
                    "--exit-on-error".as_ref(),
                    "--dbname".as_ref(),
                    self.writer.database.as_ref(),
                    plain.as_os_str(),
                ],
            )
            .await;
        let _ = tokio::fs::remove_file(&plain).await;
        result.map(|_| ())
    }

    /// Copies a backup to the bucket (one SigV4-signed PUT).
    async fn upload(&self, s3: &S3, name: &str, now: OffsetDateTime) -> anyhow::Result<()> {
        let body = tokio::fs::read(self.dir.join(name)).await?;
        let key = format!("{}{name}", s3.prefix.trim_start_matches('/'));
        let (url, headers) = sign_put(s3, &key, &body, now)?;
        let mut req = self.http.put(url).body(body);
        for (k, v) in headers {
            req = req.header(k, v);
        }
        req.send().await?.error_for_status()?;
        Ok(())
    }
}

#[async_trait]
impl crate::deploy::Backup for Backups {
    async fn before_deploy(&self, version: &str) -> anyhow::Result<String> {
        let safe: String = version
            .chars()
            .filter(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '_'))
            .collect();
        let name = self
            .create(&format!("{PRE_DEPLOY}-{safe}"), OffsetDateTime::now_utc())
            .await?;
        self.rotate();
        Ok(name)
    }
}

/// Brief §7 restore to live as a control job: lock logins, the in-game timer (cancellable), stop the worlds and the
/// hub, restore, start everything in order, unlock. The caller has checked the rank, password and confirmation.
pub async fn restore(
    control: &Arc<Controller>,
    backups: &Arc<Backups>,
    name: &str,
    seconds: i32,
    actor: Actor,
    actor_name: &str,
) -> Result<i64, ControlError> {
    if !backups.dir.join(name).is_file() || !safe_name(name) {
        return Err(ControlError::Invalid("There is no such backup.".into()));
    }
    let (job, cancel) = control.begin(&format!("restore {name}"), actor_name).await?;
    let (c, b, name) = (Arc::clone(control), Arc::clone(backups), name.to_owned());
    tokio::spawn(async move {
        let state = restore_steps(&c, &b, job, &cancel, &name, seconds, &actor).await;
        if state == JobState::Failed {
            c.alerts
                .alert("Restore failed", &format!("Restoring {name} failed; see job #{job}."))
                .await;
        }
        c.end(job, state).await;
    });
    Ok(job)
}

async fn restore_steps(
    c: &Controller,
    b: &Backups,
    job: i64,
    cancel: &CancellationToken,
    name: &str,
    seconds: i32,
    actor: &Actor,
) -> JobState {
    if !c.countdown(job, cancel, 0, seconds, "Restoring a backup", actor).await {
        return JobState::Cancelled;
    }
    let mut ok = true;
    for w in c.topology.worlds.iter().rev() {
        ok &= c.stop_unit(job, &w.unit).await;
    }
    ok &= c.stop_unit(job, &c.topology.hub).await;
    if !ok {
        c.log(job, "a service did not stop; nothing was restored").await;
        return JobState::Failed;
    }
    c.log(job, &format!("restoring {name} over the live database")).await;
    let restored = b.restore_live(name).await;
    match &restored {
        Ok(()) => c.log(job, "restored").await,
        Err(e) => {
            c.log(job, &format!("restore failed, the database is unchanged: {e}"))
                .await;
        }
    }
    let worlds = c.topology.worlds.clone();
    let started = c.start_sequence(job, &worlds, true).await;
    if let Err(e) = c.hub.set_login_lock(actor, 0, false, "").await {
        c.log(job, &format!("could not unlock logins: {e}")).await;
    }
    if restored.is_ok() && started {
        JobState::Succeeded
    } else {
        JobState::Failed
    }
}

// ---------------------------------------------------------------- helpers

fn encrypt(from: &Path, to: &Path, recipients: &[age::x25519::Recipient]) -> anyhow::Result<()> {
    let dyns: Vec<&dyn age::Recipient> = recipients.iter().map(|r| r as &dyn age::Recipient).collect();
    let encryptor = age::Encryptor::with_recipients(dyns.into_iter())?;
    let tmp = to.with_extension("partial");
    let mut out = encryptor.wrap_output(BufWriter::new(fs::File::create(&tmp)?))?;
    io::copy(&mut BufReader::new(fs::File::open(from)?), &mut out)?;
    out.finish()?.flush()?;
    fs::rename(tmp, to)?;
    Ok(())
}

fn decrypt(from: &Path, to: &Path, identity: &age::x25519::Identity) -> anyhow::Result<()> {
    let decryptor = age::Decryptor::new(BufReader::new(fs::File::open(from)?))?;
    let mut reader = decryptor.decrypt(std::iter::once(identity as &dyn age::Identity))?;
    let mut out = BufWriter::new(fs::File::create(to)?);
    io::copy(&mut reader, &mut out)?;
    out.flush()?;
    Ok(())
}

/// The installer's older unencrypted `.dump` files (listed and restorable).
fn is_plain_dump(name: &str) -> bool {
    Path::new(name)
        .extension()
        .is_some_and(|e| e.eq_ignore_ascii_case("dump"))
}

/// A new age key pair: (identity, public key).
pub fn new_identity() -> (String, String) {
    let id = age::x25519::Identity::generate();
    let public = id.to_public().to_string();
    (id.to_string().expose_secret().to_owned(), public)
}

pub fn valid_recipient(r: &str) -> bool {
    age::x25519::Recipient::from_str(r.trim()).is_ok()
}

/// A backup file name the panel made or lists: no path parts.
pub fn safe_name(name: &str) -> bool {
    !name.is_empty()
        && !name.starts_with('.')
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_'))
        && (name.ends_with(EXT) || is_plain_dump(name))
}

fn stamp(t: OffsetDateTime) -> String {
    format!(
        "{:04}{:02}{:02}-{:02}{:02}{:02}",
        t.year(),
        u8::from(t.month()),
        t.day(),
        t.hour(),
        t.minute(),
        t.second()
    )
}

fn stamp_human(t: OffsetDateTime) -> String {
    format!(
        "{:04}-{:02}-{:02} {:02}:{:02}",
        t.year(),
        u8::from(t.month()),
        t.day(),
        t.hour(),
        t.minute()
    )
}

/// The `YYYYMMDD-HHMMSS` in a backup's name (the installer's old `lotus-YYYY-MM-DD.dump` reads as midnight).
fn parse_stamp(name: &str) -> Option<OffsetDateTime> {
    let digits: String = name.chars().filter(char::is_ascii_digit).collect();
    // The last 14 digits are the stamp (a version in a pre-deploy name may hold digits too).
    let (d, t) = if name.ends_with(EXT) && digits.len() >= 14 {
        let s = &digits[digits.len() - 14..];
        (&s[..8], &s[8..])
    } else if digits.len() >= 8 {
        (&digits[digits.len() - 8..], "000000")
    } else {
        return None;
    };
    let n = |s: &str| s.parse::<u32>().ok();
    let date = Date::from_calendar_date(
        i32::try_from(n(&d[..4])?).ok()?,
        time::Month::try_from(u8::try_from(n(&d[4..6])?).ok()?).ok()?,
        u8::try_from(n(&d[6..8])?).ok()?,
    )
    .ok()?;
    let time = time::Time::from_hms(
        u8::try_from(n(&t[..2])?).ok()?,
        u8::try_from(n(&t[2..4])?).ok()?,
        u8::try_from(n(&t[4..6])?).ok()?,
    )
    .ok()?;
    Some(date.with_time(time).assume_utc())
}

fn kind_of(name: &str) -> &'static str {
    if name.starts_with(PRE_DEPLOY) {
        "pre-deploy"
    } else if name.starts_with(&format!("{NIGHTLY}-")) {
        "nightly"
    } else {
        "manual"
    }
}

fn parse_time(s: &str) -> Option<(u8, u8)> {
    let (h, m) = s.split_once(':')?;
    let (h, m) = (h.parse::<u8>().ok()?, m.parse::<u8>().ok()?);
    (h < 24 && m < 60).then_some((h, m))
}

pub fn valid_time(s: &str) -> bool {
    parse_time(s).is_some()
}

/// Brief §7 retention over backup names: which ones go.
pub fn to_delete(names: &[String]) -> Vec<String> {
    let mut nightly: Vec<(OffsetDateTime, &String)> = names
        .iter()
        .filter(|n| kind_of(n) == "nightly")
        .filter_map(|n| parse_stamp(n).map(|t| (t, n)))
        .collect();
    nightly.sort_by_key(|a| std::cmp::Reverse(a.0));
    let mut keep: HashSet<&String> = HashSet::new();
    let mut pick = |bucket: &dyn Fn(OffsetDateTime) -> (i32, u32), limit: usize| {
        let mut seen = Vec::new();
        for (t, n) in &nightly {
            let b = bucket(*t);
            if !seen.contains(&b) {
                if seen.len() == limit {
                    break;
                }
                seen.push(b);
                keep.insert(n);
            }
        }
    };
    pick(&|t| (t.year(), t.ordinal().into()), KEEP_DAILY);
    pick(
        &|t| {
            let (y, w, _) = t.to_iso_week_date();
            (y, w.into())
        },
        KEEP_WEEKLY,
    );
    pick(&|t| (t.year(), u8::from(t.month()).into()), KEEP_MONTHLY);
    let mut pre: Vec<(OffsetDateTime, &String)> = names
        .iter()
        .filter(|n| kind_of(n) == "pre-deploy")
        .filter_map(|n| parse_stamp(n).map(|t| (t, n)))
        .collect();
    pre.sort_by_key(|a| std::cmp::Reverse(a.0));
    keep.extend(pre.iter().take(KEEP_PRE_DEPLOY).map(|(_, n)| *n));
    names
        .iter()
        .filter(|n| matches!(kind_of(n), "nightly" | "pre-deploy") && parse_stamp(n).is_some() && !keep.contains(n))
        .cloned()
        .collect()
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().fold(String::new(), |mut s, b| {
        use std::fmt::Write as _;
        let _ = write!(s, "{b:02x}");
        s
    })
}

fn hmac(key: &[u8], data: &[u8]) -> anyhow::Result<Vec<u8>> {
    let mut mac = <Hmac<Sha256> as KeyInit>::new_from_slice(key).map_err(|e| anyhow::anyhow!("{e}"))?;
    mac.update(data);
    Ok(mac.finalize().into_bytes().to_vec())
}

/// AWS Signature Version 4 for one path-style `PUT /<bucket>/<key>`: the URL and the headers to send.
pub fn sign_put(
    s3: &S3,
    key: &str,
    body: &[u8],
    now: OffsetDateTime,
) -> anyhow::Result<(String, Vec<(String, String)>)> {
    let endpoint = s3.endpoint.trim_end_matches('/');
    let host = endpoint
        .split("://")
        .nth(1)
        .ok_or_else(|| anyhow::anyhow!("the S3 endpoint needs https://"))?
        .split('/')
        .next()
        .unwrap_or_default()
        .to_owned();
    anyhow::ensure!(
        key.chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_' | '/')),
        "unexpected characters in the object key"
    );
    let path = format!("/{}/{key}", s3.bucket);
    let date = format!("{:04}{:02}{:02}", now.year(), u8::from(now.month()), now.day());
    let amz_date = format!("{date}T{:02}{:02}{:02}Z", now.hour(), now.minute(), now.second());
    let payload = hex(&Sha256::digest(body));
    let canonical = format!(
        "PUT\n{path}\n\nhost:{host}\nx-amz-content-sha256:{payload}\nx-amz-date:{amz_date}\n\nhost;x-amz-content-sha256;x-amz-date\n{payload}"
    );
    let scope = format!("{date}/{}/s3/aws4_request", s3.region);
    let to_sign = format!(
        "AWS4-HMAC-SHA256\n{amz_date}\n{scope}\n{}",
        hex(&Sha256::digest(canonical.as_bytes()))
    );
    let k_date = hmac(format!("AWS4{}", s3.secret_key).as_bytes(), date.as_bytes())?;
    let k_region = hmac(&k_date, s3.region.as_bytes())?;
    let k_service = hmac(&k_region, b"s3")?;
    let k_signing = hmac(&k_service, b"aws4_request")?;
    let signature = hex(&hmac(&k_signing, to_sign.as_bytes())?);
    let auth = format!(
        "AWS4-HMAC-SHA256 Credential={}/{scope}, SignedHeaders=host;x-amz-content-sha256;x-amz-date, Signature={signature}",
        s3.access_key
    );
    Ok((
        format!("{endpoint}{path}"),
        vec![
            ("x-amz-date".into(), amz_date),
            ("x-amz-content-sha256".into(), payload),
            ("authorization".into(), auth),
        ],
    ))
}
