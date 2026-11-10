//! The Backups page (brief §4, §7; owner: list, run now, download, verify; developer: restore) and the Settings page
//! (developer: alert targets, backup keys, schedule and off-site copy, a test alert).

use askama::Template;
use axum::Extension;
use axum::Form;
use axum::body::Body;
use axum::extract::{FromRequest, Path, Query, Request, State};
use axum::http::{Method, StatusCode, header};
use axum::response::{IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use serde::Deserialize;

use crate::alerts::{self, AlertTargets, Email};
use crate::auth::Auth;
use crate::backup::{self, BackupSettings, S3};
use crate::control::ControlError;
use crate::hub::Actor;
use crate::jobs::JobState;
use crate::rank::Rank;
use crate::web::{Nav, RouteSpec, Shared, audit, confirm_password, nav, peer_ip, render, when};

/// Restore needs this typed before the backup's name (brief §7: a typed confirmation).
pub const RESTORE_WORD: &str = "RESTORE";

pub fn routes() -> Vec<RouteSpec> {
    let r = RouteSpec::new;
    let o = Some(Rank::Owner);
    let d = Some(Rank::Developer);
    vec![
        r(Method::GET, "/backups", o, "/backups", Some("Backups"), || {
            get(backups_page)
        }),
        r(Method::POST, "/backups/run", o, "/backups/run", None, || post(run_now)),
        r(Method::POST, "/backups/verify", o, "/backups/verify", None, || {
            post(verify)
        }),
        r(
            Method::GET,
            "/backups/download/{name}",
            o,
            "/backups/download/lotus-20260101-040000.dump.age",
            None,
            || get(download),
        ),
        r(Method::POST, "/backups/restore", d, "/backups/restore", None, || {
            post(restore)
        }),
        r(Method::GET, "/settings", d, "/settings", Some("Settings"), || {
            get(settings_page)
        }),
        r(Method::POST, "/settings/alerts", d, "/settings/alerts", None, || {
            post(save_alerts)
        }),
        r(Method::POST, "/settings/backups", d, "/settings/backups", None, || {
            post(save_backups)
        }),
        r(
            Method::POST,
            "/settings/test-alert",
            d,
            "/settings/test-alert",
            None,
            || post(test_alert),
        ),
        r(
            Method::POST,
            "/settings/owner-key",
            d,
            "/settings/owner-key",
            None,
            || post(owner_key),
        ),
    ]
}

fn actor(s: &crate::store::Session, ip: &str) -> Actor {
    Actor {
        account_id: s.account_id,
        ip: ip.to_owned(),
    }
}

#[derive(Deserialize, Default)]
struct PageQuery {
    #[serde(default)]
    error: Option<String>,
    #[serde(default)]
    saved: Option<String>,
}

fn back(to: &str, error: &str) -> Response {
    Redirect::to(&format!("{to}?error={}", crate::actions::urlencoding(error))).into_response()
}

// ---------------------------------------------------------------- backups page

pub struct BackupRow {
    pub name: String,
    pub size: String,
    pub at: String,
    pub kind: &'static str,
    pub verified: String,
}

#[derive(Template)]
#[template(path = "backups.html")]
struct BackupsPage {
    nav: Option<Nav>,
    rows: Vec<BackupRow>,
    time: String,
    owner_keys: usize,
    offsite: bool,
    developer: bool,
    restore_word: &'static str,
    timer: i32,
    error: Option<String>,
    keyed: bool,
}

async fn backups_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<PageQuery>,
) -> Response {
    let b = &state.backups;
    let settings = b.settings().await;
    let rows = b
        .list()
        .await
        .into_iter()
        .map(|f| BackupRow {
            size: crate::housekeeping::human(f.size),
            at: when(f.at),
            kind: f.kind,
            verified: f.verified.unwrap_or_default(),
            name: f.name,
        })
        .collect();
    render(
        StatusCode::OK,
        &BackupsPage {
            nav: Some(nav(&s)),
            rows,
            time: settings.time,
            owner_keys: settings.owner_recipients.len(),
            offsite: settings.s3.is_some(),
            developer: s.rank >= Rank::Developer,
            restore_word: RESTORE_WORD,
            timer: crate::control::DEFAULT_TIMER_SECONDS,
            error: q.error.map(|e| crate::actions::clean_text(&e)),
            keyed: !b.identity.trim().is_empty(),
        },
    )
}

/// Runs `work` as a job row (backups and verifies don't take the control lock: the game keeps running).
async fn as_job<F>(state: &Shared, kind: &str, actor: &str, work: F) -> Response
where
    F: std::future::Future<Output = anyhow::Result<String>> + Send + 'static,
{
    let Ok(job) = state.jobs.create(kind, actor).await else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    let st = state.clone();
    tokio::spawn(async move {
        let (line, result) = match work.await {
            Ok(done) => (done, JobState::Succeeded),
            Err(e) => (format!("failed: {e}"), JobState::Failed),
        };
        let _ = st.jobs.append(job, &line).await;
        let _ = st.jobs.finish(job, result).await;
    });
    Redirect::to(&format!("/control/jobs/{job}")).into_response()
}

async fn run_now(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    audit(&state, &s, &ip, "panel backup now", "", "", "started").await;
    let (b, now) = (state.backups.clone(), state.clock.now());
    as_job(&state, "backup", &s.display_name, async move {
        let name = b.create("manual", now).await?;
        Ok(format!("wrote {name}"))
    })
    .await
}

#[derive(Deserialize)]
struct NameForm {
    name: String,
}

async fn verify(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<NameForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !backup::safe_name(&f.name) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    audit(&state, &s, &ip, "panel verify backup", &f.name, "", "started").await;
    let (b, now, name) = (state.backups.clone(), state.clock.now(), f.name.clone());
    as_job(&state, &format!("verify {}", f.name), &s.display_name, async move {
        let summary = b.verify(&name, now).await?;
        Ok(format!("{name}: {summary}"))
    })
    .await
}

async fn download(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Path(name): Path<String>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    if !backup::safe_name(&name) {
        return StatusCode::NOT_FOUND.into_response();
    }
    let Ok(file) = tokio::fs::File::open(state.backups.dir.join(&name)).await else {
        // A missing file is "nothing there", not a hidden route.
        return (StatusCode::OK, "That backup is gone.").into_response();
    };
    audit(&state, &s, &ip, "panel download backup", &name, "", "ok").await;
    Response::builder()
        .header(header::CONTENT_TYPE, "application/octet-stream")
        .header(header::CONTENT_DISPOSITION, format!("attachment; filename=\"{name}\""))
        .body(Body::from_stream(tokio_util::io::ReaderStream::new(file)))
        .unwrap_or_else(|_| StatusCode::INTERNAL_SERVER_ERROR.into_response())
}

#[derive(Deserialize)]
struct RestoreForm {
    name: String,
    confirm: String,
    password: String,
    seconds: i32,
}

/// Brief §7: developer only, password and typed confirmation, every world locked first.
async fn restore(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<RestoreForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !backup::safe_name(&f.name) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    if f.confirm.trim() != format!("{RESTORE_WORD} {}", f.name) {
        return back(
            "/backups",
            &format!("Type {RESTORE_WORD} {} exactly to restore it.", f.name),
        );
    }
    if !confirm_password(&state, &s, &f.password, &ip).await {
        audit(
            &state,
            &s,
            &ip,
            "panel restore backup",
            &f.name,
            "",
            "refused: password",
        )
        .await;
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let result = backup::restore(
        &state.control,
        &state.backups,
        &f.name,
        f.seconds,
        actor(&s, &ip),
        &s.display_name,
    )
    .await;
    let outcome = result
        .as_ref()
        .map_or_else(|e| format!("refused: {e}"), |j| format!("job {j}"));
    audit(&state, &s, &ip, "panel restore backup", &f.name, "", &outcome).await;
    match result {
        Ok(job) | Err(ControlError::Busy(job)) => Redirect::to(&format!("/control/jobs/{job}")).into_response(),
        Err(e) => back("/backups", &e.to_string()),
    }
}

// ---------------------------------------------------------------- settings

#[derive(Template)]
#[template(path = "settings.html")]
struct SettingsPage {
    nav: Option<Nav>,
    alerts: AlertTargets,
    backups: BackupSettings,
    error: Option<String>,
    saved: bool,
    /// The panel's own backup key (public half), or empty.
    panel_key: String,
}

async fn settings_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<PageQuery>,
) -> Response {
    let panel_key = std::str::FromStr::from_str(state.backups.identity.trim())
        .map(|i: age::x25519::Identity| i.to_public().to_string())
        .unwrap_or_default();
    render(
        StatusCode::OK,
        &SettingsPage {
            nav: Some(nav(&s)),
            alerts: alerts::targets(&*state.settings).await,
            backups: state.backups.settings().await,
            error: q.error.map(|e| crate::actions::clean_text(&e)),
            saved: q.saved.is_some(),
            panel_key,
        },
    )
}

#[derive(Deserialize)]
struct AlertForm {
    #[serde(default)]
    discord_webhook: String,
    #[serde(default)]
    smtp_host: String,
    #[serde(default)]
    smtp_port: String,
    #[serde(default)]
    smtp_username: String,
    /// Empty keeps the stored password.
    #[serde(default)]
    smtp_password: String,
    #[serde(default)]
    email_from: String,
    #[serde(default)]
    email_to: String,
    password: String,
}

async fn save_alerts(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<AlertForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &f.password, &ip).await {
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let webhook = f.discord_webhook.trim().to_owned();
    if !alerts::valid_webhook(&webhook) {
        return back("/settings", "That isn't a Discord webhook address.");
    }
    let old = alerts::targets(&*state.settings).await;
    let email = if f.smtp_host.trim().is_empty() {
        None
    } else {
        let Ok(port) = f.smtp_port.trim().parse::<u16>() else {
            return back("/settings", "The SMTP port must be a number (465 or 587).");
        };
        let password = if f.smtp_password.is_empty() {
            old.email.as_ref().map(|e| e.password.clone()).unwrap_or_default()
        } else {
            f.smtp_password.clone()
        };
        Some(Email {
            host: f.smtp_host.trim().to_owned(),
            port,
            username: f.smtp_username.trim().to_owned(),
            password,
            from: f.email_from.trim().to_owned(),
            to: f.email_to.trim().to_owned(),
        })
    };
    let targets = AlertTargets {
        discord_webhook: webhook,
        email,
    };
    let Ok(v) = serde_json::to_value(&targets) else {
        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
    };
    if let Err(e) = state.settings.set(alerts::SETTINGS_KEY, &v, &s.display_name).await {
        return back("/settings", &e.to_string());
    }
    let what = format!(
        "discord {}, email {}",
        if targets.discord_webhook.is_empty() {
            "off"
        } else {
            "on"
        },
        if targets.email.is_some() { "on" } else { "off" }
    );
    audit(&state, &s, &ip, "panel alert settings", "", &what, "ok").await;
    Redirect::to("/settings?saved=1").into_response()
}

#[derive(Deserialize)]
struct BackupForm {
    #[serde(default)]
    owner_recipients: String,
    time: String,
    #[serde(default)]
    s3_endpoint: String,
    #[serde(default)]
    s3_region: String,
    #[serde(default)]
    s3_bucket: String,
    #[serde(default)]
    s3_prefix: String,
    #[serde(default)]
    s3_access_key: String,
    /// Empty keeps the stored secret key.
    #[serde(default)]
    s3_secret_key: String,
    password: String,
}

async fn save_backups(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<BackupForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &f.password, &ip).await {
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let recipients: Vec<String> = f
        .owner_recipients
        .split(['\n', ','])
        .map(str::trim)
        .filter(|r| !r.is_empty())
        .map(str::to_owned)
        .collect();
    if let Some(bad) = recipients.iter().find(|r| !backup::valid_recipient(r)) {
        return back("/settings", &format!("{bad} isn't an age public key (age1...)."));
    }
    if !backup::valid_time(f.time.trim()) {
        return back("/settings", "The backup time must be HH:MM (UTC).");
    }
    let old = state.backups.settings().await;
    let s3 = if f.s3_endpoint.trim().is_empty() {
        None
    } else {
        if !f.s3_endpoint.trim().starts_with("https://") {
            return back("/settings", "The S3 endpoint must start with https://.");
        }
        let secret = if f.s3_secret_key.is_empty() {
            old.s3.as_ref().map(|s| s.secret_key.clone()).unwrap_or_default()
        } else {
            f.s3_secret_key.clone()
        };
        Some(S3 {
            endpoint: f.s3_endpoint.trim().to_owned(),
            region: f.s3_region.trim().to_owned(),
            bucket: f.s3_bucket.trim().to_owned(),
            prefix: f.s3_prefix.trim().to_owned(),
            access_key: f.s3_access_key.trim().to_owned(),
            secret_key: secret,
        })
    };
    let new = BackupSettings {
        owner_recipients: recipients,
        time: f.time.trim().to_owned(),
        s3,
    };
    let Ok(v) = serde_json::to_value(&new) else {
        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
    };
    if let Err(e) = state.settings.set(backup::SETTINGS_KEY, &v, &s.display_name).await {
        return back("/settings", &e.to_string());
    }
    let what = format!(
        "{} owner key(s), {} UTC, off-site {}",
        new.owner_recipients.len(),
        new.time,
        if new.s3.is_some() { "on" } else { "off" }
    );
    audit(&state, &s, &ip, "panel backup settings", "", &what, "ok").await;
    Redirect::to("/settings?saved=1").into_response()
}

async fn test_alert(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    state
        .alerts
        .alert(
            "Test alert",
            &format!("{} sent a test alert from the panel.", s.display_name),
        )
        .await;
    Redirect::to("/settings?saved=1").into_response()
}

#[derive(Deserialize)]
struct KeyForm {
    password: String,
}

/// Makes an owner backup key: the public half joins the recipients, the secret half is downloaded once and never
/// stored on the server.
async fn owner_key(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<KeyForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &f.password, &ip).await {
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let (identity, public) = backup::new_identity();
    let mut settings = state.backups.settings().await;
    settings.owner_recipients.push(public.clone());
    let Ok(v) = serde_json::to_value(&settings) else {
        return StatusCode::INTERNAL_SERVER_ERROR.into_response();
    };
    if let Err(e) = state.settings.set(backup::SETTINGS_KEY, &v, &s.display_name).await {
        return back("/settings", &e.to_string());
    }
    audit(&state, &s, &ip, "panel owner backup key", &public, "", "ok").await;
    let file = format!(
        "# Lotus backup key, made {} UTC by {}.\n# Keep this file safe and off the server: it decrypts every backup made from now on.\n# public key: {public}\n{identity}\n",
        when(state.clock.now()),
        s.display_name
    );
    Response::builder()
        .header(header::CONTENT_TYPE, "text/plain; charset=utf-8")
        .header(
            header::CONTENT_DISPOSITION,
            "attachment; filename=\"lotus-backup-key.txt\"",
        )
        .header(header::CACHE_CONTROL, "no-store")
        .body(Body::from(file))
        .unwrap_or_else(|_| StatusCode::INTERNAL_SERVER_ERROR.into_response())
}
