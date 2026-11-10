//! The panel's HTTP side (brief §1, §8): axum behind Caddy on 127.0.0.1, server-rendered askama pages, vendored and
//! embedded assets, and the security headers on every response.

use std::net::SocketAddr;
use std::sync::Arc;
use std::time::Duration;

use askama::Template;
use axum::Router;
use axum::body::Body;
use axum::extract::{ConnectInfo, Path, Request, State};
use axum::http::{HeaderValue, StatusCode, header};
use axum::middleware::{self, Next};
use axum::response::{Html, IntoResponse, Response};
use axum::routing::get;
use rust_embed::Embed;
use tower::ServiceBuilder;
use tower_http::compression::CompressionLayer;
use tower_http::limit::RequestBodyLimitLayer;
use tower_http::request_id::{MakeRequestUuid, PropagateRequestIdLayer, SetRequestIdLayer};
use tower_http::set_header::SetResponseHeaderLayer;
use tower_http::timeout::TimeoutLayer;
use tower_http::trace::TraceLayer;

use crate::net::{Cidr, client_ip};

pub struct AppState {
    pub db: sqlx::PgPool,
    pub hub: crate::hub::Client,
    pub allowed: Vec<Cidr>,
}

pub type Shared = Arc<AppState>;

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
const REQUEST_TIMEOUT: Duration = Duration::from_secs(30);

pub fn router(state: Shared) -> Router {
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
            header::HeaderName::from_static("cross-origin-opener-policy"),
            HeaderValue::from_static("same-origin"),
        ));
    Router::new()
        .route("/", get(index))
        .route("/healthz", get(healthz))
        .route("/assets/{*path}", get(asset))
        .fallback(not_found)
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

/// Brief §8 `allowed_networks`: when set, every other client gets 404, as if the panel weren't there.
async fn allow_networks(
    State(state): State<Shared>,
    ConnectInfo(peer): ConnectInfo<SocketAddr>,
    req: Request,
    next: Next,
) -> Response {
    if !state.allowed.is_empty() {
        let forwarded = req.headers().get("x-forwarded-for").and_then(|v| v.to_str().ok());
        let ip = client_ip(peer.ip(), forwarded);
        if !state.allowed.iter().any(|n| n.contains(ip)) {
            return not_found().await.into_response();
        }
    }
    next.run(req).await
}

#[derive(Template)]
#[template(path = "login.html")]
struct LoginPage {
    hub_up: bool,
    db_up: bool,
}

async fn index(State(state): State<Shared>) -> Response {
    let (hub_up, db_up) = tokio::join!(crate::hub::healthy(&state.hub), crate::db::healthy(&state.db));
    render(&LoginPage { hub_up, db_up })
}

/// For Caddy and the install script: 200 when the panel and its database answer.
async fn healthz(State(state): State<Shared>) -> impl IntoResponse {
    if crate::db::healthy(&state.db).await {
        (StatusCode::OK, "ok")
    } else {
        (StatusCode::SERVICE_UNAVAILABLE, "database down")
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
        None => not_found().await.into_response(),
    }
}

#[derive(Template)]
#[template(path = "not_found.html")]
struct NotFoundPage;

async fn not_found() -> impl IntoResponse {
    (StatusCode::NOT_FOUND, render(&NotFoundPage))
}

fn render(page: &impl Template) -> Response {
    match page.render() {
        Ok(html) => Html(html).into_response(),
        Err(e) => {
            tracing::error!(error = %e, "template render failed");
            StatusCode::INTERNAL_SERVER_ERROR.into_response()
        }
    }
}
