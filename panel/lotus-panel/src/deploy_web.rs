//! The Deploys page (brief §4 Deploys, §6; owner): which release each service runs, the releases on disk, fetching a
//! signed release from CI or from the incoming folder, the plan (version, commits, schema changes, rollback warnings)
//! and the deploy itself, which needs the password again and runs as a control job.

use std::path::{Path, PathBuf};
use std::time::Duration;

use askama::Template;
use axum::Extension;
use axum::Form;
use axum::extract::{FromRequest, Path as UrlPath, Request, State};
use axum::http::{Method, StatusCode};
use axum::response::{IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use serde::Deserialize;

use crate::auth::Auth;
use crate::control::ControlError;
use crate::deploy::{self, Plan, Release};
use crate::hub::Actor;
use crate::jobs::JobState;
use crate::rank::Rank;
use crate::web::{Nav, RouteSpec, Shared, audit, confirm_password, nav, peer_ip, render};

/// A release zip downloads in well under this on a VPS link; a stuck download gives up.
const DOWNLOAD_TIMEOUT: Duration = Duration::from_mins(15);
/// The zip and the signature file are refused past these sizes.
const MAX_ZIP: u64 = 512 * 1024 * 1024;
const MAX_SIG: usize = 4096;
/// Release assets every CI release carries (deploy/windows/make-release.ps1).
const ASSET: &str = "lotus.zip";

pub fn routes() -> Vec<RouteSpec> {
    let r = RouteSpec::new;
    let o = Some(Rank::Owner);
    vec![
        r(Method::GET, "/deploys", o, "/deploys", Some("Deploys"), || {
            get(deploys_page)
        }),
        r(Method::GET, "/deploys/ci", o, "/deploys/ci", None, || get(ci_fragment)),
        r(Method::POST, "/deploys/fetch", o, "/deploys/fetch", None, || {
            post(fetch)
        }),
        r(Method::POST, "/deploys/incoming", o, "/deploys/incoming", None, || {
            post(incoming)
        }),
        r(
            Method::GET,
            "/deploys/release/{version}",
            o,
            "/deploys/release/1.0",
            None,
            || get(plan_page),
        ),
        r(
            Method::POST,
            "/deploys/release/{version}",
            o,
            "/deploys/release/1.0",
            None,
            || post(start_deploy),
        ),
        r(Method::POST, "/deploys/clean", o, "/deploys/clean", None, || {
            post(clean)
        }),
    ]
}

fn actor(s: &crate::store::Session, ip: &str) -> Actor {
    Actor {
        account_id: s.account_id,
        ip: ip.to_owned(),
    }
}

pub struct ReleaseRow {
    pub version: String,
    pub commit: String,
    pub built: String,
    pub verified: bool,
    pub running: String,
}

#[derive(Template)]
#[template(path = "deploys.html")]
struct DeploysPage {
    nav: Option<Nav>,
    running: Vec<(String, String)>,
    rollback: Option<String>,
    releases: Vec<ReleaseRow>,
    history: Vec<deploy::Deployed>,
    removable: Vec<String>,
    keyed: bool,
    incoming: String,
    error: Option<String>,
    job: Option<i64>,
}

#[derive(Deserialize, Default)]
struct PageQuery {
    #[serde(default)]
    error: Option<String>,
}

fn in_use(state: &Shared) -> Vec<(String, Option<String>)> {
    let d = &state.deployer;
    let t = &d.control.topology;
    std::iter::once(t.hub.clone())
        .chain(t.worlds.iter().map(|w| w.unit.clone()))
        .map(|u| {
            let v = d.links.current(&u);
            (u, v)
        })
        .collect()
}

async fn deploys_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    axum::extract::Query(q): axum::extract::Query<PageQuery>,
) -> Response {
    let d = &state.deployer;
    let running = in_use(&state);
    let versions: Vec<String> = running.iter().filter_map(|(_, v)| v.clone()).collect();
    let rollback = d.current().and_then(|c| deploy::previous(&d.releases, &c));
    let releases = deploy::list(&d.releases)
        .into_iter()
        .map(|r: Release| ReleaseRow {
            running: running
                .iter()
                .filter(|(_, v)| v.as_deref() == Some(r.version()))
                .map(|(u, _)| u.clone())
                .collect::<Vec<_>>()
                .join(", "),
            version: r.manifest.version.clone(),
            commit: r.manifest.commit.chars().take(10).collect(),
            built: r.manifest.built_at.clone(),
            verified: r.verified(),
        })
        .collect();
    let page = DeploysPage {
        nav: Some(nav(&s)),
        running: running
            .into_iter()
            .map(|(u, v)| (u, v.unwrap_or_else(|| "no link yet".into())))
            .collect(),
        rollback,
        releases,
        history: deploy::history(&d.releases).into_iter().take(10).collect(),
        removable: deploy::removable(&d.releases, &versions),
        keyed: !d.settings.public_key.trim().is_empty(),
        incoming: d.settings.incoming.display().to_string(),
        error: q.error.map(|e| crate::actions::clean_text(&e)),
        job: state.control.running_job(),
    };
    render(StatusCode::OK, &page)
}

// ---------------------------------------------------------------- CI releases

#[derive(Template)]
#[template(path = "deploys_ci.html")]
struct CiFragment {
    tags: Vec<(String, String, bool)>,
    error: Option<String>,
    csrf: String,
}

#[derive(Deserialize)]
struct GhRelease {
    tag_name: String,
    #[serde(default)]
    published_at: Option<String>,
}

/// The newest `lotus-*` releases of the CI repository (GitHub's public API; no token).
async fn ci_fragment(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    let d = &state.deployer;
    if d.settings.ci_repo.is_empty() {
        let page = CiFragment {
            tags: Vec::new(),
            error: Some("No CI repository is configured ([deploy] ci_repo).".into()),
            csrf: s.csrf.clone(),
        };
        return render(StatusCode::OK, &page);
    }
    let url = format!(
        "https://api.github.com/repos/{}/releases?per_page=15",
        d.settings.ci_repo
    );
    let result = async {
        let r = reqwest::Client::new()
            .get(&url)
            .header("User-Agent", "lotus-panel")
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration::from_secs(15))
            .send()
            .await?
            .error_for_status()?;
        let body = r.bytes().await?;
        Ok::<Vec<GhRelease>, anyhow::Error>(serde_json::from_slice(&body)?)
    }
    .await;
    let (tags, error) = match result {
        Ok(list) => (
            list.into_iter()
                .filter(|r| r.tag_name.starts_with("lotus-"))
                .map(|r| {
                    let version = r.tag_name.trim_start_matches("lotus-").to_owned();
                    let here = d.releases.join(&version).is_dir();
                    (r.tag_name, r.published_at.unwrap_or_default(), here)
                })
                .collect(),
            None,
        ),
        Err(e) => (Vec::new(), Some(format!("Could not list the CI releases: {e}"))),
    };
    render(
        StatusCode::OK,
        &CiFragment {
            tags,
            error,
            csrf: s.csrf.clone(),
        },
    )
}

#[derive(Deserialize)]
struct FetchForm {
    tag: String,
}

/// Downloads, verifies and unpacks one CI release as a job.
async fn fetch(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<FetchForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let d = state.deployer.clone();
    if d.settings.public_key.trim().is_empty() {
        return Redirect::to("/deploys?error=Set+the+release+public+key+first.").into_response();
    }
    if !f.tag.starts_with("lotus-") || !deploy::valid_version(&f.tag) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let Ok(job) = state.jobs.create(&format!("fetch {}", f.tag), &s.display_name).await else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    audit(&state, &s, &ip, "panel fetch release", &f.tag, "", "started").await;
    let st = state.clone();
    tokio::spawn(async move {
        let base = format!("https://github.com/{}/releases/download/{}/", d.settings.ci_repo, f.tag);
        let ok = match download_and_install(&d, &base, &st, job).await {
            Ok(version) => {
                let _ = st
                    .jobs
                    .append(job, &format!("release {version} is ready to deploy"))
                    .await;
                true
            }
            Err(e) => {
                let _ = st.jobs.append(job, &format!("refused: {e}")).await;
                false
            }
        };
        let _ = st
            .jobs
            .finish(job, if ok { JobState::Succeeded } else { JobState::Failed })
            .await;
    });
    Redirect::to(&format!("/control/jobs/{job}")).into_response()
}

async fn download_and_install(d: &deploy::Deployer, base: &str, st: &Shared, job: i64) -> anyhow::Result<String> {
    let http = reqwest::Client::builder().timeout(DOWNLOAD_TIMEOUT).build()?;
    let sig = http
        .get(format!("{base}{ASSET}.minisig"))
        .header("User-Agent", "lotus-panel")
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await?;
    anyhow::ensure!(sig.len() <= MAX_SIG, "the signature file is too large");
    let sig = String::from_utf8(sig.to_vec())?;
    let _ = st.jobs.append(job, "downloading the release").await;
    tokio::fs::create_dir_all(&d.releases).await?;
    let tmp = d.releases.join(format!(".download-{job}.zip"));
    let result = async {
        let mut r = http
            .get(format!("{base}{ASSET}"))
            .header("User-Agent", "lotus-panel")
            .send()
            .await?
            .error_for_status()?;
        let mut file = tokio::fs::File::create(&tmp).await?;
        let mut size = 0u64;
        while let Some(chunk) = r.chunk().await? {
            size += chunk.len() as u64;
            anyhow::ensure!(size <= MAX_ZIP, "the release is too large");
            tokio::io::AsyncWriteExt::write_all(&mut file, &chunk).await?;
        }
        tokio::io::AsyncWriteExt::flush(&mut file).await?;
        let _ = st
            .jobs
            .append(
                job,
                &format!("downloaded {} MB; checking the signature", size / 1_048_576),
            )
            .await;
        verify_and_install(d, &tmp, &sig, st, job).await
    }
    .await;
    let _ = tokio::fs::remove_file(&tmp).await;
    result
}

async fn verify_and_install(
    d: &deploy::Deployer,
    zip: &Path,
    sig: &str,
    st: &Shared,
    job: i64,
) -> anyhow::Result<String> {
    let (zip, sig, key, releases) = (
        zip.to_path_buf(),
        sig.to_owned(),
        d.settings.public_key.clone(),
        d.releases.clone(),
    );
    let sha = tokio::task::spawn_blocking({
        let zip = zip.clone();
        move || deploy::verify_signature(&zip, &sig, &key)
    })
    .await??;
    let _ = st.jobs.append(job, &format!("good signature; sha256 {sha}")).await;
    tokio::task::spawn_blocking(move || deploy::install(&zip, &sha, &releases)).await?
}

/// Verifies and unpacks every `<name>.zip` with a `<name>.zip.minisig` in the incoming folder, as a job.
async fn incoming(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let d = state.deployer.clone();
    if d.settings.public_key.trim().is_empty() {
        return Redirect::to("/deploys?error=Set+the+release+public+key+first.").into_response();
    }
    let Ok(job) = state.jobs.create("check incoming releases", &s.display_name).await else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    audit(&state, &s, &ip, "panel check incoming releases", "", "", "started").await;
    let st = state.clone();
    tokio::spawn(async move {
        let mut ok = true;
        let zips: Vec<PathBuf> = std::fs::read_dir(&d.settings.incoming)
            .map(|rd| {
                rd.flatten()
                    .map(|e| e.path())
                    .filter(|p| p.extension().is_some_and(|x| x == "zip"))
                    .collect()
            })
            .unwrap_or_default();
        if zips.is_empty() {
            let _ = st
                .jobs
                .append(job, &format!("no zips in {}", d.settings.incoming.display()))
                .await;
        }
        for zip in zips {
            let name = zip
                .file_name()
                .map(|n| n.to_string_lossy().into_owned())
                .unwrap_or_default();
            let mut sig_path = zip.clone().into_os_string();
            sig_path.push(".minisig");
            let result = match tokio::fs::read_to_string(&sig_path).await {
                Ok(sig) if sig.len() <= MAX_SIG => verify_and_install(&d, &zip, &sig, &st, job).await,
                Ok(_) => Err(anyhow::anyhow!("the signature file is too large")),
                Err(_) => Err(anyhow::anyhow!("no {name}.minisig next to it")),
            };
            match result {
                Ok(version) => {
                    let _ = st
                        .jobs
                        .append(job, &format!("{name}: release {version} is ready"))
                        .await;
                    let _ = tokio::fs::remove_file(&zip).await;
                    let _ = tokio::fs::remove_file(&sig_path).await;
                }
                Err(e) => {
                    ok = false;
                    let _ = st.jobs.append(job, &format!("{name}: refused: {e}")).await;
                }
            }
        }
        let _ = st
            .jobs
            .finish(job, if ok { JobState::Succeeded } else { JobState::Failed })
            .await;
    });
    Redirect::to(&format!("/control/jobs/{job}")).into_response()
}

// ---------------------------------------------------------------- plan and deploy

#[derive(Template)]
#[template(path = "deploy_plan.html")]
struct PlanPage {
    nav: Option<Nav>,
    plan: Option<Plan>,
    version: String,
    timer: i32,
    rollback: bool,
    error: Option<String>,
}

async fn plan_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    UrlPath(version): UrlPath<String>,
    axum::extract::Query(q): axum::extract::Query<PageQuery>,
) -> Response {
    let d = &state.deployer;
    let plan = d.plan(&version);
    let rollback = d
        .current()
        .and_then(|c| deploy::previous(&d.releases, &c))
        .is_some_and(|p| p == version);
    render(
        StatusCode::OK,
        &PlanPage {
            nav: Some(nav(&s)),
            plan,
            version,
            timer: d.settings.timer_seconds,
            rollback,
            error: q.error.map(|e| crate::actions::clean_text(&e)),
        },
    )
}

#[derive(Deserialize)]
struct DeployForm {
    seconds: i32,
    #[serde(default)]
    message: String,
    password: String,
}

async fn start_deploy(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    UrlPath(version): UrlPath<String>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<DeployForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &f.password, &ip).await {
        audit(&state, &s, &ip, "panel deploy", &version, "", "refused: password").await;
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let message = crate::actions::clean_text(&f.message);
    let result = state
        .deployer
        .deploy(&version, f.seconds, message, actor(&s, &ip), &s.display_name)
        .await;
    let outcome = match &result {
        Ok(job) => format!("job {job}"),
        Err(e) => format!("refused: {e}"),
    };
    audit(
        &state,
        &s,
        &ip,
        "panel deploy",
        &version,
        &format!("timer {} s", f.seconds),
        &outcome,
    )
    .await;
    match result {
        // Started, or another control job is already running: show that job.
        Ok(job) | Err(ControlError::Busy(job)) => Redirect::to(&format!("/control/jobs/{job}")).into_response(),
        Err(e) => Redirect::to(&format!(
            "/deploys/release/{}?error={}",
            crate::actions::urlencoding(&version),
            crate::actions::urlencoding(&e.to_string())
        ))
        .into_response(),
    }
}

#[derive(Deserialize)]
struct CleanForm {
    password: String,
}

/// Housekeeping (brief §6 via the plan): removes releases beyond the newest five, never one in use or the rollback
/// target. Needs the password again.
async fn clean(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<CleanForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &f.password, &ip).await {
        audit(
            &state,
            &s,
            &ip,
            "panel remove old releases",
            "",
            "",
            "refused: password",
        )
        .await;
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let d = &state.deployer;
    let versions: Vec<String> = in_use(&state).into_iter().filter_map(|(_, v)| v).collect();
    let mut removed = Vec::new();
    for v in deploy::removable(&d.releases, &versions) {
        if deploy::remove(&d.releases, &v).is_ok() {
            removed.push(v);
        }
    }
    audit(
        &state,
        &s,
        &ip,
        "panel remove old releases",
        "",
        &removed.join(", "),
        "ok",
    )
    .await;
    Redirect::to("/deploys").into_response()
}
