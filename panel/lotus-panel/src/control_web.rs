//! Server control pages (brief §3): the units and their live state, start / stop / restart / emergency stop / cancel,
//! maintenance mode, scheduled restarts, job progress, diagnostics (developer) and housekeeping (owner).

use askama::Template;
use axum::body::Body;
use axum::extract::{FromRequest, Path, Request, State};
use axum::http::{Method, StatusCode, header};
use axum::response::{IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use axum::{Extension, Form};
use serde::Deserialize;

use crate::auth::Auth;
use crate::control::{ControlError, DEFAULT_TIMER_SECONDS, Scope, UnitView};
use crate::housekeeping;
use crate::hub::Actor;
use crate::jobs::JobState;
use crate::rank::Rank;
use crate::schedule::{self, RestartSchedule};
use crate::web::{Nav, RouteSpec, Shared, audit, confirm_password, nav, peer_ip, render, when};

/// Brief §3: an emergency stop needs this typed exactly.
pub const EMERGENCY_PHRASE: &str = "STOP EVERYTHING";

#[allow(clippy::too_many_lines)] // one line per route: the table reads best whole
pub fn routes() -> Vec<RouteSpec> {
    let r = RouteSpec::new;
    let owner = Some(Rank::Owner);
    let dev = Some(Rank::Developer);
    vec![
        r(Method::GET, "/control", owner, "/control", Some("Server"), || {
            get(control_page)
        }),
        r(Method::GET, "/control/units", owner, "/control/units", None, || {
            get(units_fragment)
        }),
        r(Method::POST, "/control/start", owner, "/control/start", None, || {
            post(start)
        }),
        r(Method::POST, "/control/stop", owner, "/control/stop", None, || {
            post(stop)
        }),
        r(
            Method::POST,
            "/control/restart",
            owner,
            "/control/restart",
            None,
            || post(restart),
        ),
        r(
            Method::POST,
            "/control/emergency",
            owner,
            "/control/emergency",
            None,
            || post(emergency),
        ),
        r(Method::POST, "/control/cancel", owner, "/control/cancel", None, || {
            post(cancel)
        }),
        r(
            Method::POST,
            "/control/maintenance",
            owner,
            "/control/maintenance",
            None,
            || post(maintenance),
        ),
        r(
            Method::POST,
            "/control/schedule",
            owner,
            "/control/schedule",
            None,
            || post(set_schedule),
        ),
        r(
            Method::GET,
            "/control/jobs/{id}",
            owner,
            "/control/jobs/1",
            None,
            || get(job_page),
        ),
        r(
            Method::GET,
            "/control/jobs/{id}/log",
            owner,
            "/control/jobs/1/log",
            None,
            || get(job_log),
        ),
        r(
            Method::GET,
            "/diagnostics",
            dev,
            "/diagnostics",
            Some("Diagnostics"),
            || get(diagnostics_page),
        ),
        r(Method::POST, "/diagnostics/run", dev, "/diagnostics/run", None, || {
            post(diagnostics_run)
        }),
        r(
            Method::GET,
            "/diagnostics/dumps/{name}",
            dev,
            "/diagnostics/dumps/sample.txt",
            None,
            || get(dump_download),
        ),
        r(
            Method::GET,
            "/housekeeping",
            owner,
            "/housekeeping",
            Some("Housekeeping"),
            || get(housekeeping_page),
        ),
        r(
            Method::POST,
            "/housekeeping/clear-dumps",
            owner,
            "/housekeeping/clear-dumps",
            None,
            || post(clear_dumps),
        ),
    ]
}

fn actor(s: &crate::store::Session, ip: &str) -> Actor {
    Actor {
        account_id: s.account_id,
        ip: ip.to_owned(),
    }
}

pub struct UnitRow {
    pub label: String,
    pub unit: String,
    pub state: &'static str,
    pub css: &'static str,
}

async fn unit_rows(state: &Shared) -> Vec<UnitRow> {
    state
        .control
        .views()
        .await
        .into_iter()
        .map(|(label, unit, v)| UnitRow {
            label,
            unit,
            state: v.label(),
            css: match v {
                UnitView::Running => "ok",
                UnitView::Starting | UnitView::Stopping => "busy",
                UnitView::Stopped | UnitView::Missing => "off",
                UnitView::Failed => "down",
            },
        })
        .collect()
}

pub struct JobRow {
    pub id: i64,
    pub kind: String,
    pub actor: String,
    pub state: &'static str,
    pub when: String,
}

#[derive(Template)]
#[template(path = "control.html")]
struct ControlPage {
    nav: Option<Nav>,
    units: Vec<UnitRow>,
    worlds: Vec<i32>,
    running: Option<i64>,
    locks: Vec<(i32, String)>,
    schedule: RestartSchedule,
    jobs: Vec<JobRow>,
    error: Option<String>,
    timer: i32,
    phrase: &'static str,
    days: Vec<(&'static str, bool)>,
}

async fn control_page_with(state: &Shared, s: &crate::store::Session, ip: &str, error: Option<String>) -> ControlPage {
    let locks = state.hub.login_locks(&actor(s, ip)).await.unwrap_or_default();
    let schedule = state
        .settings
        .get(schedule::KEY)
        .await
        .ok()
        .flatten()
        .and_then(|v| serde_json::from_value(v).ok())
        .unwrap_or(RestartSchedule {
            enabled: false,
            every: "daily".into(),
            at: "05:00".into(),
            seconds: DEFAULT_TIMER_SECONDS,
            message: "Daily restart".into(),
            actor_id: 0,
            actor_name: String::new(),
            last_run: 0,
        });
    let jobs = state
        .jobs
        .recent(10)
        .await
        .unwrap_or_default()
        .into_iter()
        .map(|j| JobRow {
            id: j.id,
            kind: j.kind,
            actor: j.actor,
            state: j.state.stored(),
            when: when(j.created_at),
        })
        .collect();
    let days = [
        "daily",
        "monday",
        "tuesday",
        "wednesday",
        "thursday",
        "friday",
        "saturday",
        "sunday",
    ]
    .into_iter()
    .map(|d| (d, d == schedule.every))
    .collect();
    ControlPage {
        nav: Some(nav(s)),
        units: unit_rows(state).await,
        worlds: state.control.topology.worlds.iter().map(|w| w.world_id).collect(),
        running: state.control.running_job(),
        locks,
        schedule,
        jobs,
        error,
        timer: DEFAULT_TIMER_SECONDS,
        phrase: EMERGENCY_PHRASE,
        days,
    }
}

async fn control_page(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    render(StatusCode::OK, &control_page_with(&state, &s, &ip, None).await)
}

#[derive(Template)]
#[template(path = "units.html")]
struct UnitsFragment {
    units: Vec<UnitRow>,
}

async fn units_fragment(State(state): State<Shared>) -> Response {
    render(
        StatusCode::OK,
        &UnitsFragment {
            units: unit_rows(&state).await,
        },
    )
}

#[derive(Deserialize)]
struct ScopeForm {
    #[serde(default)]
    scope: String,
}

fn scope_of(s: &str) -> Scope {
    s.parse::<i32>().map_or(Scope::All, Scope::World)
}

fn to_job(r: Result<i64, ControlError>) -> Result<Response, String> {
    r.map(|job| Redirect::to(&format!("/control/jobs/{job}")).into_response())
        .map_err(|e| e.to_string())
}

async fn refuse(state: &Shared, s: &crate::store::Session, ip: &str, error: String) -> Response {
    render(
        StatusCode::UNPROCESSABLE_ENTITY,
        &control_page_with(state, s, ip, Some(error)).await,
    )
}

async fn start(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<ScopeForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let scope = scope_of(&form.scope);
    let result = state.control.start(scope, &s.display_name).await;
    audit(
        &state,
        &s,
        &ip,
        "panel start",
        &format!("{scope:?}"),
        "",
        if result.is_ok() { "ok" } else { "refused" },
    )
    .await;
    match to_job(result) {
        Ok(r) => r,
        Err(e) => refuse(&state, &s, &ip, e).await,
    }
}

#[derive(Deserialize)]
struct StopForm {
    #[serde(default)]
    scope: String,
    #[serde(default)]
    seconds: Option<i32>,
    #[serde(default)]
    message: String,
    #[serde(default)]
    password: String,
}

async fn stop_or_restart(state: Shared, s: crate::store::Session, req: Request, restart: bool) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<StopForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let action = if restart { "panel restart" } else { "panel stop" };
    // Brief §2: a dangerous action asks again (the password; no second factor, plan §1a).
    if !confirm_password(&state, &s, &form.password, &ip).await {
        audit(&state, &s, &ip, action, &form.scope, "", "password check failed").await;
        return refuse(
            &state,
            &s,
            &ip,
            "Your password didn't match, so nothing was changed.".into(),
        )
        .await;
    }
    let scope = scope_of(&form.scope);
    let seconds = form.seconds.unwrap_or(DEFAULT_TIMER_SECONDS);
    let message = form.message.trim().chars().take(200).collect::<String>();
    let result = state
        .control
        .stop(
            scope,
            seconds,
            message.clone(),
            restart,
            actor(&s, &ip),
            &s.display_name,
        )
        .await;
    audit(
        &state,
        &s,
        &ip,
        action,
        &format!("{scope:?}"),
        &format!("{seconds}s {message}"),
        if result.is_ok() { "ok" } else { "refused" },
    )
    .await;
    match to_job(result) {
        Ok(r) => r,
        Err(e) => refuse(&state, &s, &ip, e).await,
    }
}

async fn stop(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    stop_or_restart(state, s, req, false).await
}

async fn restart(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    stop_or_restart(state, s, req, true).await
}

#[derive(Deserialize)]
struct EmergencyForm {
    #[serde(default)]
    password: String,
    #[serde(default)]
    confirm: String,
}

async fn emergency(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<EmergencyForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if form.confirm.trim() != EMERGENCY_PHRASE {
        return refuse(
            &state,
            &s,
            &ip,
            format!("Type {EMERGENCY_PHRASE} exactly to confirm an emergency stop."),
        )
        .await;
    }
    if !confirm_password(&state, &s, &form.password, &ip).await {
        audit(
            &state,
            &s,
            &ip,
            "panel emergency stop",
            "everything",
            "",
            "password check failed",
        )
        .await;
        return refuse(
            &state,
            &s,
            &ip,
            "Your password didn't match, so nothing was changed.".into(),
        )
        .await;
    }
    let result = state.control.emergency_stop(&s.display_name).await;
    audit(
        &state,
        &s,
        &ip,
        "panel emergency stop",
        "everything",
        "",
        if result.is_ok() { "ok" } else { "refused" },
    )
    .await;
    match to_job(result) {
        Ok(r) => r,
        Err(e) => refuse(&state, &s, &ip, e).await,
    }
}

async fn cancel(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let cancelled = state.control.cancel().await;
    audit(
        &state,
        &s,
        &ip,
        "panel cancel",
        "control job",
        "",
        if cancelled { "ok" } else { "nothing running" },
    )
    .await;
    Redirect::to("/control").into_response()
}

#[derive(Deserialize)]
struct MaintenanceForm {
    #[serde(default)]
    action: String,
    #[serde(default)]
    message: String,
}

async fn maintenance(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<MaintenanceForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let on = form.action == "on";
    let message = form.message.trim().chars().take(30).collect::<String>();
    // The hub audits the lock itself (panel lock / panel unlock).
    match state.control.maintenance(&actor(&s, &ip), on, &message).await {
        Ok(()) => Redirect::to("/control").into_response(),
        Err(e) => refuse(&state, &s, &ip, format!("Maintenance mode didn't change: {e}")).await,
    }
}

#[derive(Deserialize)]
struct ScheduleForm {
    #[serde(default)]
    enabled: Option<String>,
    #[serde(default)]
    every: String,
    #[serde(default)]
    at: String,
    #[serde(default)]
    seconds: Option<i32>,
    #[serde(default)]
    message: String,
}

async fn set_schedule(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<ScheduleForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let sched = RestartSchedule {
        enabled: form.enabled.is_some(),
        every: form.every.trim().to_lowercase(),
        at: form.at.trim().to_owned(),
        seconds: form
            .seconds
            .unwrap_or(DEFAULT_TIMER_SECONDS)
            .clamp(1, crate::control::MAX_TIMER_SECONDS),
        message: form.message.trim().chars().take(200).collect(),
        actor_id: s.account_id,
        actor_name: s.display_name.clone(),
        last_run: 0,
    };
    if !sched.valid() {
        return refuse(
            &state,
            &s,
            &ip,
            "Choose daily or a weekday, and a time as HH:MM (UTC).".into(),
        )
        .await;
    }
    let value = serde_json::to_value(&sched).unwrap_or_default();
    if let Err(e) = state.settings.set(schedule::KEY, &value, &s.display_name).await {
        return refuse(&state, &s, &ip, format!("Couldn't save the schedule: {e}")).await;
    }
    let detail = format!(
        "{} {} {} at {} UTC",
        if sched.enabled { "on" } else { "off" },
        sched.every,
        sched.seconds,
        sched.at
    );
    audit(&state, &s, &ip, "panel restart schedule", "all worlds", &detail, "ok").await;
    Redirect::to("/control").into_response()
}

#[derive(Template)]
#[template(path = "job.html")]
struct JobPage {
    nav: Option<Nav>,
    found: bool,
    id: i64,
    kind: String,
    actor: String,
    state: &'static str,
    running: bool,
    log: String,
    can_cancel: bool,
}

async fn job_view(state: &Shared, id: i64) -> (bool, String, String, JobState, String) {
    match state.jobs.get(id).await.ok().flatten() {
        Some(j) => (true, j.kind, j.actor, j.state, j.log),
        None => (false, String::new(), String::new(), JobState::Failed, String::new()),
    }
}

async fn job_page(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, Path(id): Path<i64>) -> Response {
    let (found, kind, actor, job_state, log) = job_view(&state, id).await;
    let running = found && job_state == JobState::Running;
    let can_cancel = running && state.control.running_job() == Some(id);
    render(
        StatusCode::OK,
        &JobPage {
            nav: Some(nav(&s)),
            found,
            id,
            kind,
            actor,
            state: job_state.stored(),
            running,
            log,
            can_cancel,
        },
    )
}

#[derive(Template)]
#[template(path = "job_log.html")]
struct JobLog {
    id: i64,
    state: &'static str,
    running: bool,
    log: String,
}

async fn job_log(State(state): State<Shared>, Path(id): Path<i64>) -> Response {
    let (_, _, _, job_state, log) = job_view(&state, id).await;
    render(
        StatusCode::OK,
        &JobLog {
            id,
            state: job_state.stored(),
            running: job_state == JobState::Running,
            log,
        },
    )
}

// ---------------------------------------------------------------- diagnostics

pub struct DumpRow {
    pub name: String,
    pub size: String,
}

#[derive(Template)]
#[template(path = "diagnostics.html")]
struct DiagnosticsPage {
    nav: Option<Nav>,
    worlds: Vec<i32>,
    dumps: Vec<DumpRow>,
}

async fn diagnostics_page(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    let dumps = housekeeping::dumps(&state.paths.dumps)
        .into_iter()
        .map(|d| DumpRow {
            name: d.name,
            size: housekeeping::human(d.size),
        })
        .collect();
    let worlds = state.control.topology.worlds.iter().map(|w| w.world_id).collect();
    render(
        StatusCode::OK,
        &DiagnosticsPage {
            nav: Some(nav(&s)),
            worlds,
            dumps,
        },
    )
}

#[derive(Deserialize)]
struct DiagForm {
    world: i32,
    kind: String,
    #[serde(default)]
    args: String,
}

const DIAG_KINDS: [&str; 5] = [
    "diag.threads",
    "diag.heap",
    "diag.jfr.start",
    "diag.jfr.stop",
    "diag.tick",
];

async fn diagnostics_run(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<DiagForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !DIAG_KINDS.contains(&form.kind.as_str()) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let Ok(job) = state
        .jobs
        .create(&format!("diagnostics {}", form.kind), &s.display_name)
        .await
    else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    let a = actor(&s, &ip);
    let st = state.clone();
    tokio::spawn(async move {
        let _ = st
            .jobs
            .append(job, &format!("{} on world {}", form.kind, form.world))
            .await;
        let ok = match st.hub.world_op(&a, form.world, &form.kind, form.args.trim()).await {
            Ok(mut rx) => {
                let mut ok = true;
                while let Some(line) = rx.recv().await {
                    let _ = st.jobs.append(job, &line.line).await;
                    ok &= !line.error;
                    if line.done {
                        break;
                    }
                }
                ok
            }
            Err(e) => {
                let _ = st.jobs.append(job, &format!("the hub didn't take it: {e}")).await;
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

async fn dump_download(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Path(name): Path<String>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    if !housekeeping::safe_name(&name) {
        return StatusCode::NOT_FOUND.into_response();
    }
    let path = state.paths.dumps.join(&name);
    let Ok(file) = tokio::fs::File::open(&path).await else {
        return StatusCode::NOT_FOUND.into_response();
    };
    audit(&state, &s, &ip, "panel download dump", &name, "", "ok").await;
    let stream = tokio_util::io::ReaderStream::new(file);
    Response::builder()
        .header(header::CONTENT_TYPE, "application/octet-stream")
        .header(header::CONTENT_DISPOSITION, format!("attachment; filename=\"{name}\""))
        .body(Body::from_stream(stream))
        .unwrap_or_else(|_| StatusCode::INTERNAL_SERVER_ERROR.into_response())
}

// ---------------------------------------------------------------- housekeeping

#[derive(Template)]
#[template(path = "housekeeping.html")]
struct HousekeepingPage {
    nav: Option<Nav>,
    sizes: Vec<(&'static str, String, String)>,
    tls: String,
    firewall: Result<Vec<(String, String, String)>, String>,
    message: Option<String>,
}

async fn housekeeping_with(state: &Shared, s: &crate::store::Session, message: Option<String>) -> HousekeepingPage {
    let p = state.paths.clone();
    let dirs = [
        ("Logs", p.logs),
        ("Dumps", p.dumps),
        ("Backups", p.backups),
        ("Releases", p.releases),
    ];
    let sizes = tokio::task::spawn_blocking(move || {
        dirs.into_iter()
            .map(|(label, dir)| {
                (
                    label,
                    dir.display().to_string(),
                    housekeeping::human(housekeeping::dir_size(&dir)),
                )
            })
            .collect::<Vec<_>>()
    })
    .await
    .unwrap_or_default();
    let tls = match housekeeping::tls_expiry(&state.paths.certificates, &state.domain) {
        Ok(t) => time::OffsetDateTime::from_unix_timestamp(t).map_or_else(
            |_| "unknown".into(),
            |t| {
                let days = (t - state.clock.now()).whole_days();
                format!("{} (in {days} days; Caddy renews it automatically)", when(t))
            },
        ),
        Err(e) => e,
    };
    HousekeepingPage {
        nav: Some(nav(s)),
        sizes,
        tls,
        firewall: housekeeping::firewall_open_ports(),
        message,
    }
}

async fn housekeeping_page(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    render(StatusCode::OK, &housekeeping_with(&state, &s, None).await)
}

#[derive(Deserialize)]
struct PasswordForm {
    #[serde(default)]
    password: String,
}

async fn clear_dumps(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<PasswordForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &form.password, &ip).await {
        let page = housekeeping_with(
            &state,
            &s,
            Some("Your password didn't match, so nothing was deleted.".into()),
        )
        .await;
        return render(StatusCode::UNPROCESSABLE_ENTITY, &page);
    }
    let dir = state.paths.dumps.clone();
    let n = tokio::task::spawn_blocking(move || housekeeping::clear_old_dumps(&dir))
        .await
        .unwrap_or(0);
    audit(
        &state,
        &s,
        &ip,
        "panel clear dumps",
        "older than 7 days",
        &format!("{n} files"),
        "ok",
    )
    .await;
    render(
        StatusCode::OK,
        &housekeeping_with(&state, &s, Some(format!("Deleted {n} dumps older than 7 days."))).await,
    )
}

/// Background: the crash watcher every 5 s and the restart schedule every 30 s.
pub fn spawn_background(state: Shared) {
    let s = state.clone();
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(std::time::Duration::from_secs(5)).await;
            s.control.watch_once().await;
        }
    });
    tokio::spawn(async move {
        loop {
            tokio::time::sleep(std::time::Duration::from_secs(30)).await;
            run_schedule(&state).await;
        }
    });
}

async fn run_schedule(state: &Shared) {
    let Some(mut sched) = state
        .settings
        .get(schedule::KEY)
        .await
        .ok()
        .flatten()
        .and_then(|v| serde_json::from_value::<RestartSchedule>(v).ok())
    else {
        return;
    };
    let now = state.clock.now();
    if !sched.due(now) || state.control.running_job().is_some() {
        return;
    }
    let a = Actor {
        account_id: sched.actor_id,
        ip: "scheduled".into(),
    };
    match state
        .control
        .stop(
            Scope::All,
            sched.seconds,
            sched.message.clone(),
            true,
            a,
            &format!("schedule ({})", sched.actor_name),
        )
        .await
    {
        Ok(job) => tracing::info!(job, "scheduled restart started"),
        Err(e) => tracing::warn!(error = %e, "scheduled restart skipped"),
    }
    sched.last_run = now.unix_timestamp();
    if let Ok(v) = serde_json::to_value(&sched) {
        let _ = state.settings.set(schedule::KEY, &v, "schedule").await;
    }
}
