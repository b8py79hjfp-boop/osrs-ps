//! The panel's HTTP side (brief §1, §2, §4, §8): axum behind Caddy on 127.0.0.1, server-rendered askama pages,
//! embedded assets, security headers, and the route table: every route declares its minimum rank, and the router is
//! built from that table, so no route can skip the guard (brief §10 test 1 runs over the same table).

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration as StdDuration;

use askama::Template;
use axum::body::Body;
use axum::extract::{ConnectInfo, FromRequest, Path, Request, State};
use axum::http::{HeaderMap, HeaderValue, Method, StatusCode, header};
use axum::middleware::{self, Next};
use axum::response::{Html, IntoResponse, Redirect, Response};
use axum::routing::{MethodRouter, get, post};
use axum::{Extension, Form, Router};
use rust_embed::Embed;
use serde::Deserialize;
use time::OffsetDateTime;
use tower::ServiceBuilder;
use tower_http::compression::CompressionLayer;
use tower_http::limit::RequestBodyLimitLayer;
use tower_http::request_id::{MakeRequestUuid, PropagateRequestIdLayer, SetRequestIdLayer};
use tower_http::set_header::SetResponseHeaderLayer;
use tower_http::timeout::TimeoutLayer;
use tower_http::trace::TraceLayer;

use crate::auth::{
    Auth, AuthSettings, Denied, LOGIN_COOKIE, Lockout, RANK_GRACE, RANK_RECHECK, SESSION_COOKIE, TOUCH_EVERY, allowed,
    clear_cookie, cookie, csrf_of, hash_token, is_safe, new_token, origin_ok, same, set_cookie,
};
use crate::clock::Clock;
use crate::hub::{AuditEntry, HubApi, HubError, Verified};
use crate::net::{Cidr, client_ip};
use crate::rank::Rank;
use crate::store::{Session, SessionStore, unhex};

pub struct AppState {
    pub hub: Arc<dyn HubApi>,
    pub sessions: Arc<dyn SessionStore>,
    pub clock: Arc<dyn Clock>,
    pub lockout: Lockout,
    pub auth: AuthSettings,
    pub allowed: Vec<Cidr>,
    pub control: Arc<crate::control::Controller>,
    pub jobs: Arc<dyn crate::jobs::JobStore>,
    pub settings: Arc<dyn crate::jobs::Settings>,
    pub paths: PanelPaths,
    /// The panel's host name (for the TLS expiry check).
    pub domain: String,
    pub metrics: Arc<dyn crate::metrics::MetricsStore>,
    pub alerts: Arc<dyn crate::jobs::Alerts>,
}

/// Directories the panel reads (logs, dumps) or reports on (backups, releases, certificates).
#[derive(Debug, Clone)]
pub struct PanelPaths {
    pub logs: std::path::PathBuf,
    pub dumps: std::path::PathBuf,
    pub backups: std::path::PathBuf,
    pub releases: std::path::PathBuf,
    pub certificates: std::path::PathBuf,
}

pub type Shared = Arc<AppState>;

// ---------------------------------------------------------------- route table

/// One route: who may use it, and how it's served.
pub struct RouteSpec {
    pub method: Method,
    pub path: &'static str,
    /// `None` = public (sign-in, health, assets). Everything else needs a session of at least this rank.
    pub min_rank: Option<Rank>,
    /// A concrete URL for this route (the permission test requests it).
    pub sample: &'static str,
    /// The navigation label, for GET pages that appear in the menu.
    pub nav: Option<&'static str>,
    handler: fn() -> MethodRouter<Shared>,
}

impl RouteSpec {
    pub fn new(
        method: Method,
        path: &'static str,
        min_rank: Option<Rank>,
        sample: &'static str,
        nav: Option<&'static str>,
        handler: fn() -> MethodRouter<Shared>,
    ) -> Self {
        Self {
            method,
            path,
            min_rank,
            sample,
            nav,
            handler,
        }
    }
}

/// Every route the panel serves.
pub fn routes() -> Vec<RouteSpec> {
    let mut all = core_routes();
    all.extend(crate::control_web::routes());
    all.extend(crate::pages::routes());
    all
}

fn core_routes() -> Vec<RouteSpec> {
    let r = RouteSpec::new;
    vec![
        r(Method::GET, "/", Some(Rank::Moderator), "/", Some("Dashboard"), || {
            get(dashboard)
        }),
        r(Method::GET, "/login", None, "/login", None, || get(login_form)),
        r(Method::POST, "/login", None, "/login", None, || post(login_submit)),
        r(Method::POST, "/logout", Some(Rank::Moderator), "/logout", None, || {
            post(logout)
        }),
        r(
            Method::GET,
            "/sessions",
            Some(Rank::Moderator),
            "/sessions",
            Some("My sessions"),
            || get(sessions_page),
        ),
        r(
            Method::POST,
            "/sessions/revoke",
            Some(Rank::Moderator),
            "/sessions/revoke",
            None,
            || post(sessions_revoke),
        ),
        r(
            Method::GET,
            "/staff/sessions",
            Some(Rank::Owner),
            "/staff/sessions",
            Some("Staff sessions"),
            || get(staff_sessions_page),
        ),
        r(
            Method::POST,
            "/staff/sessions/revoke",
            Some(Rank::Owner),
            "/staff/sessions/revoke",
            None,
            || post(staff_sessions_revoke),
        ),
        r(Method::GET, "/healthz", None, "/healthz", None, || get(healthz)),
        r(Method::GET, "/assets/{*path}", None, "/assets/panel.css", None, || {
            get(asset)
        }),
    ]
}

#[derive(Embed)]
#[folder = "assets/"]
struct Assets;

/// Brief §8: a strict CSP with no inline script or style (htmx is a file from 'self').
const CSP: &str = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; \
                   form-action 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'";
/// Two years, as browsers' preload lists expect.
const HSTS: &str = "max-age=63072000; includeSubDomains";
/// Forms and htmx requests only; nothing here uploads files yet (brief §6 deploy upload raises it then).
const MAX_BODY: usize = 64 * 1024;
const REQUEST_TIMEOUT: StdDuration = StdDuration::from_secs(30);
/// The sign-in form's double-submit cookie lives this long.
const LOGIN_FORM_TTL: time::Duration = time::Duration::hours(1);

pub fn router(state: Shared) -> Router {
    let mut app = Router::new();
    for spec in routes() {
        let guarded =
            (spec.handler)().route_layer(middleware::from_fn_with_state((state.clone(), spec.min_rank), guard));
        app = app.route(spec.path, guarded);
    }
    let x_request_id = header::HeaderName::from_static("x-request-id");
    let headers = ServiceBuilder::new()
        .layer(SetResponseHeaderLayer::overriding(
            header::CONTENT_SECURITY_POLICY,
            HeaderValue::from_static(CSP),
        ))
        .layer(SetResponseHeaderLayer::overriding(
            header::STRICT_TRANSPORT_SECURITY,
            HeaderValue::from_static(HSTS),
        ))
        .layer(SetResponseHeaderLayer::overriding(
            header::X_FRAME_OPTIONS,
            HeaderValue::from_static("DENY"),
        ))
        .layer(SetResponseHeaderLayer::overriding(
            header::REFERRER_POLICY,
            HeaderValue::from_static("same-origin"),
        ))
        .layer(SetResponseHeaderLayer::overriding(
            header::X_CONTENT_TYPE_OPTIONS,
            HeaderValue::from_static("nosniff"),
        ))
        .layer(SetResponseHeaderLayer::overriding(
            header::CACHE_CONTROL,
            HeaderValue::from_static("no-store"),
        ))
        .layer(SetResponseHeaderLayer::overriding(
            header::HeaderName::from_static("cross-origin-opener-policy"),
            HeaderValue::from_static("same-origin"),
        ));
    app.fallback(not_found)
        .layer(middleware::from_fn_with_state(state.clone(), allow_networks))
        .layer(headers)
        .layer(
            ServiceBuilder::new()
                .layer(SetRequestIdLayer::new(x_request_id.clone(), MakeRequestUuid))
                .layer(TraceLayer::new_for_http())
                .layer(PropagateRequestIdLayer::new(x_request_id))
                .layer(TimeoutLayer::with_status_code(
                    StatusCode::REQUEST_TIMEOUT,
                    REQUEST_TIMEOUT,
                ))
                .layer(RequestBodyLimitLayer::new(MAX_BODY))
                .layer(CompressionLayer::new()),
        )
        .with_state(state)
}

// ---------------------------------------------------------------- guard

pub fn peer_ip(req: &Request) -> std::net::IpAddr {
    let peer = req
        .extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .map_or(std::net::IpAddr::from([127, 0, 0, 1]), |c| c.0.ip());
    client_ip(peer, req.headers().get("x-forwarded-for").and_then(|v| v.to_str().ok()))
}

/// The request's live session, its rank re-checked with the hub when due. `Ok(None)` = signed out.
async fn current_session(state: &AppState, headers: &HeaderMap) -> Result<Option<Session>, Denied> {
    let Some(token) = cookie(headers, SESSION_COOKIE) else {
        return Ok(None);
    };
    let id = hash_token(&token);
    let now = state.clock.now();
    let Some(mut s) = state.sessions.get(&id).await.map_err(|_| Denied::HubDown)? else {
        return Ok(None);
    };
    if !state.auth.live(&s, now) {
        if s.revoked_at.is_none() {
            let _ = state.sessions.revoke(&id, now).await;
        }
        return Ok(None);
    }
    if now - s.rank_checked_at >= RANK_RECHECK {
        match state.hub.get_rank(s.account_id).await {
            Ok(info) if !info.exists || info.banned || !info.rank.is_staff() => {
                let _ = state.sessions.revoke(&id, now).await;
                tracing::info!(account = s.account_id, "session ended: rank or ban changed");
                return Ok(None);
            }
            Ok(info) => {
                let _ = state.sessions.set_rank(&id, info.rank, now).await;
                s.rank = info.rank;
                s.rank_checked_at = now;
            }
            Err(e) => {
                tracing::warn!(error = %e, "rank re-check failed");
                if now - s.rank_checked_at > RANK_GRACE {
                    return Err(Denied::HubDown);
                }
            }
        }
    }
    if now - s.last_seen_at >= TOUCH_EVERY {
        let _ = state.sessions.touch(&id, now).await;
        s.last_seen_at = now;
    }
    Ok(Some(s))
}

async fn guard(State((state, min)): State<(Shared, Option<Rank>)>, req: Request, next: Next) -> Response {
    let method = req.method().clone();
    if min.is_none() {
        return next.run(req).await;
    }
    let session = match current_session(&state, req.headers()).await {
        Ok(s) => s,
        Err(d) => return d.respond(&method),
    };
    if let Err(d) = allowed(min, session.as_ref().map(|s| s.rank)) {
        return d.respond(&method);
    }
    let Some(session) = session else {
        return Denied::SignedOut.respond(&method);
    };
    let mut req = req;
    if !is_safe(&method) {
        if !origin_ok(req.headers(), &state.auth.origin) {
            return Denied::Forbidden.respond(&method);
        }
        let (r, token) = csrf_of(req).await;
        if !token.is_some_and(|t| same(&t, &session.csrf)) {
            return Denied::Forbidden.respond(&method);
        }
        req = r;
    }
    req.extensions_mut().insert(Auth(session));
    next.run(req).await
}

/// Brief §8 `allowed_networks`: when set, every other client gets 404, as if the panel weren't there.
async fn allow_networks(State(state): State<Shared>, req: Request, next: Next) -> Response {
    if !state.allowed.is_empty() {
        let ip = peer_ip(&req);
        if !state.allowed.iter().any(|n| n.contains(ip)) {
            return StatusCode::NOT_FOUND.into_response();
        }
    }
    next.run(req).await
}

// ---------------------------------------------------------------- pages

pub struct NavLink {
    pub href: &'static str,
    pub label: &'static str,
}

pub struct Nav {
    pub name: String,
    pub rank: Rank,
    pub csrf: String,
    pub links: Vec<NavLink>,
}

pub fn nav(s: &Session) -> Nav {
    let links = routes()
        .into_iter()
        .filter(|r| r.method == Method::GET && r.min_rank.is_some_and(|m| s.rank >= m))
        .filter_map(|r| r.nav.map(|label| NavLink { href: r.path, label }))
        .collect();
    Nav {
        name: s.display_name.clone(),
        rank: s.rank,
        csrf: s.csrf.clone(),
        links,
    }
}

pub fn when(t: OffsetDateTime) -> String {
    format!(
        "{:04}-{:02}-{:02} {:02}:{:02} UTC",
        t.year(),
        u8::from(t.month()),
        t.day(),
        t.hour(),
        t.minute()
    )
}

/// `HH:MM:SS` UTC, for log lines.
pub fn clock_time(t: OffsetDateTime) -> String {
    format!("{:02}:{:02}:{:02}", t.hour(), t.minute(), t.second())
}

pub fn render(status: StatusCode, page: &impl Template) -> Response {
    match page.render() {
        Ok(html) => (status, Html(html)).into_response(),
        Err(e) => {
            tracing::error!(error = %e, "template render failed");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}

pub async fn audit(state: &AppState, s: &Session, ip: &str, action: &str, target: &str, detail: &str, result: &str) {
    let entry = AuditEntry {
        actor_id: s.account_id,
        ip,
        action,
        target,
        detail,
        result,
    };
    if let Err(e) = state.hub.audit(entry).await {
        tracing::warn!(error = %e, action, "audit write failed");
    }
}

#[derive(Template)]
#[template(path = "login.html")]
struct LoginPage {
    nav: Option<Nav>,
    csrf: String,
    error: Option<String>,
    username: String,
    hub_up: bool,
    db_up: bool,
}

async fn login_page(state: &AppState, csrf: String, error: Option<String>, username: String) -> LoginPage {
    let (hub_up, db_up) = tokio::join!(state.hub.healthy(), state.sessions.healthy());
    LoginPage {
        nav: None,
        csrf,
        error,
        username,
        hub_up,
        db_up,
    }
}

async fn login_form(State(state): State<Shared>, headers: HeaderMap) -> Response {
    if matches!(current_session(&state, &headers).await, Ok(Some(_))) {
        return Redirect::to("/").into_response();
    }
    let token = new_token();
    let page = login_page(&state, token.clone(), None, String::new()).await;
    let mut r = render(StatusCode::OK, &page);
    if let Ok(v) = set_cookie(LOGIN_COOKIE, &token, LOGIN_FORM_TTL).parse() {
        r.headers_mut().insert(header::SET_COOKIE, v);
    }
    r
}

#[derive(Deserialize)]
struct LoginForm {
    username: String,
    password: String,
    csrf: String,
}

const SIGN_IN_FAILED: &str = "Sign-in failed: wrong name or password, or the account isn't staff.";
const LOCKED: &str = "Too many failed sign-ins. Try again in 15 minutes.";

async fn login_submit(State(state): State<Shared>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let headers = req.headers().clone();
    let user_agent = headers
        .get(header::USER_AGENT)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .chars()
        .take(200)
        .collect();
    let Ok(Form(form)) = Form::<LoginForm>::from_request(req, &state).await else {
        return Denied::Forbidden.respond(&Method::POST);
    };
    let pre = cookie(&headers, LOGIN_COOKIE);
    if !pre.is_some_and(|p| same(&p, &form.csrf)) || !origin_ok(&headers, &state.auth.origin) {
        return Denied::Forbidden.respond(&Method::POST);
    }
    let name = form.username.trim().to_owned();
    let now = state.clock.now();
    if state.lockout.locked(&name, &ip, now) {
        return render(
            StatusCode::TOO_MANY_REQUESTS,
            &login_page(&state, form.csrf, Some(LOCKED.into()), name).await,
        );
    }
    let verified = match state.hub.verify_staff(&name, &form.password, &ip).await {
        Ok(v) => v,
        Err(e) => {
            tracing::warn!(error = %e, "sign-in: hub unavailable");
            let msg = "The game hub isn't answering; sign-in is unavailable until it is back.".to_owned();
            return render(
                StatusCode::SERVICE_UNAVAILABLE,
                &login_page(&state, form.csrf, Some(msg), name).await,
            );
        }
    };
    let Verified::Ok {
        account_id,
        display_name,
        rank,
    } = verified
    else {
        if verified == Verified::Throttled {
            return render(
                StatusCode::TOO_MANY_REQUESTS,
                &login_page(&state, form.csrf, Some(LOCKED.into()), name).await,
            );
        }
        if state.lockout.fail(&name, &ip, now) {
            // Brief §2: repeated failures alert owners (the alert channels arrive in phase 8; the log carries it now).
            tracing::warn!(alert = true, account = %name, %ip, "panel sign-in locked after repeated failures");
        }
        return render(
            StatusCode::UNAUTHORIZED,
            &login_page(&state, form.csrf, Some(SIGN_IN_FAILED.into()), name).await,
        );
    };
    state.lockout.succeed(&name);
    let token = new_token();
    let session = Session {
        id_hash: hash_token(&token),
        account_id,
        username: name,
        display_name,
        rank,
        ip,
        user_agent,
        csrf: new_token(),
        created_at: now,
        last_seen_at: now,
        rank_checked_at: now,
        revoked_at: None,
    };
    if let Err(e) = state.sessions.insert(&session).await {
        tracing::error!(error = %e, "could not store the session");
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    }
    let mut r = Redirect::to("/").into_response();
    for c in [
        set_cookie(SESSION_COOKIE, &token, state.auth.absolute),
        clear_cookie(LOGIN_COOKIE),
    ] {
        if let Ok(v) = c.parse() {
            r.headers_mut().append(header::SET_COOKIE, v);
        }
    }
    r
}

async fn logout(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let _ = state.sessions.revoke(&s.id_hash, state.clock.now()).await;
    audit(&state, &s, &ip, "panel logout", &s.display_name, "", "ok").await;
    signed_out()
}

fn signed_out() -> Response {
    let mut r = Redirect::to("/login").into_response();
    if let Ok(v) = clear_cookie(SESSION_COOKIE).parse() {
        r.headers_mut().insert(header::SET_COOKIE, v);
    }
    r
}

async fn dashboard(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    let live = crate::pages::dashboard_live(&state, &s).await;
    let page = crate::pages::DashboardPage {
        nav: Some(nav(&s)),
        name: s.display_name.clone(),
        rank: s.rank,
        live,
    };
    render(StatusCode::OK, &page)
}

pub struct SessionRow {
    pub handle: String,
    pub name: String,
    pub account_id: i64,
    pub rank: Rank,
    pub ip: String,
    pub user_agent: String,
    pub created: String,
    pub last_seen: String,
    pub current: bool,
}

async fn live_rows(state: &AppState, account: Option<i64>, current: &Session) -> Vec<SessionRow> {
    let now = state.clock.now();
    let rows = state
        .sessions
        .live(account, now - state.auth.absolute)
        .await
        .unwrap_or_default();
    rows.into_iter()
        .filter(|s| state.auth.live(s, now))
        .map(|s| SessionRow {
            handle: s.handle(),
            current: s.id_hash == current.id_hash,
            name: s.display_name,
            account_id: s.account_id,
            rank: s.rank,
            ip: s.ip,
            user_agent: s.user_agent,
            created: when(s.created_at),
            last_seen: when(s.last_seen_at),
        })
        .collect()
}

#[derive(Template)]
#[template(path = "sessions.html")]
struct SessionsPage {
    nav: Option<Nav>,
    rows: Vec<SessionRow>,
}

async fn sessions_page(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    let rows = live_rows(&state, Some(s.account_id), &s).await;
    render(
        StatusCode::OK,
        &SessionsPage {
            nav: Some(nav(&s)),
            rows,
        },
    )
}

#[derive(Deserialize)]
struct RevokeForm {
    id: String,
}

async fn sessions_revoke(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<RevokeForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let Some(id) = unhex(&form.id) else {
        return StatusCode::NOT_FOUND.into_response();
    };
    // Only your own sessions here (an owner uses the staff page for others').
    match state.sessions.get(&id).await {
        Ok(Some(target)) if target.account_id == s.account_id => {
            let _ = state.sessions.revoke(&id, state.clock.now()).await;
            audit(
                &state,
                &s,
                &ip,
                "panel revoke session",
                &s.display_name,
                &target.ip,
                "ok",
            )
            .await;
            if id == s.id_hash {
                signed_out()
            } else {
                Redirect::to("/sessions").into_response()
            }
        }
        _ => StatusCode::NOT_FOUND.into_response(),
    }
}

#[derive(Template)]
#[template(path = "staff_sessions.html")]
struct StaffSessionsPage {
    nav: Option<Nav>,
    rows: Vec<SessionRow>,
    error: Option<String>,
}

async fn staff_sessions_page(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>) -> Response {
    let rows = live_rows(&state, None, &s).await;
    render(
        StatusCode::OK,
        &StaffSessionsPage {
            nav: Some(nav(&s)),
            rows,
            error: None,
        },
    )
}

#[derive(Deserialize)]
struct RevokeAccountForm {
    account_id: i64,
    password: String,
}

/// Brief §2: dangerous actions ask again; with no second factor (plan §1a) that is the password.
pub async fn confirm_password(state: &AppState, s: &Session, password: &str, ip: &str) -> bool {
    let now = state.clock.now();
    if state.lockout.locked(&s.username, ip, now) {
        return false;
    }
    match state.hub.verify_staff(&s.username, password, ip).await {
        Ok(Verified::Ok { account_id, .. }) if account_id == s.account_id => true,
        Ok(_) => {
            state.lockout.fail(&s.username, ip, now);
            false
        }
        Err(HubError::Unavailable(_) | HubError::Refused(_)) => false,
    }
}

async fn staff_sessions_revoke(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(form)) = Form::<RevokeAccountForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if !confirm_password(&state, &s, &form.password, &ip).await {
        audit(
            &state,
            &s,
            &ip,
            "panel revoke sessions",
            &format!("account {}", form.account_id),
            "",
            "password check failed",
        )
        .await;
        let rows = live_rows(&state, None, &s).await;
        let error = Some("Your password didn't match, so nothing was changed.".to_owned());
        return render(
            StatusCode::UNPROCESSABLE_ENTITY,
            &StaffSessionsPage {
                nav: Some(nav(&s)),
                rows,
                error,
            },
        );
    }
    let n = state
        .sessions
        .revoke_account(form.account_id, state.clock.now())
        .await
        .unwrap_or(0);
    audit(
        &state,
        &s,
        &ip,
        "panel revoke sessions",
        &format!("account {}", form.account_id),
        &format!("{n} sessions"),
        "ok",
    )
    .await;
    if form.account_id == s.account_id {
        signed_out()
    } else {
        Redirect::to("/staff/sessions").into_response()
    }
}

/// For Caddy and the install script: 200 when the panel and its database answer.
async fn healthz(State(state): State<Shared>) -> Response {
    if state.sessions.healthy().await {
        (StatusCode::OK, "ok").into_response()
    } else {
        (StatusCode::SERVICE_UNAVAILABLE, "database down").into_response()
    }
}

async fn asset(Path(path): Path<String>) -> Response {
    match Assets::get(&path) {
        Some(file) => {
            let mime = mime_guess::from_path(&path).first_or_octet_stream();
            Response::builder()
                .header(header::CONTENT_TYPE, mime.as_ref())
                .header(header::CACHE_CONTROL, "public, max-age=3600")
                .body(Body::from(file.data.into_owned()))
                .unwrap_or_else(|_| StatusCode::INTERNAL_SERVER_ERROR.into_response())
        }
        None => not_found().await,
    }
}

#[derive(Template)]
#[template(path = "not_found.html")]
struct NotFoundPage {
    nav: Option<Nav>,
}

async fn not_found() -> Response {
    render(StatusCode::NOT_FOUND, &NotFoundPage { nav: None })
}
