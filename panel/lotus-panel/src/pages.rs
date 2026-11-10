//! Read-only pages (brief §4): the dashboard (live over SSE), players and the player card, moderation, metrics, logs
//! (live over SSE) and the audit log with CSV export. Everything a player typed is escaped by askama.

use std::collections::HashMap;
use std::convert::Infallible;
use std::time::Duration;

use askama::Template;
use axum::Extension;
use axum::extract::{Path, Query, Request, State};
use axum::http::{Method, StatusCode, header};
use axum::response::sse::{Event, KeepAlive, Sse};
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use futures_util::stream::{self, Stream};
use serde::Deserialize;
use time::OffsetDateTime;

use crate::auth::Auth;
use crate::control::UnitView;
use crate::hub::{Actor, AuditFilter, AuditRow};
use crate::logs;
use crate::metrics::svg_chart;
use crate::rank::Rank;
use crate::store::Session;
use crate::web::{Nav, RouteSpec, Shared, nav, peer_ip, render, when};

/// A live stream ends after this long and the browser reconnects, which re-runs the session and rank checks.
const STREAM_LIFETIME: Duration = Duration::from_secs(60);
const DASHBOARD_EVERY: Duration = Duration::from_secs(5);
const LOG_POLL: Duration = Duration::from_secs(1);
/// §7 tick budget (brief): logic p99 ≤ 120 ms, info p99 ≤ 80 ms (server/Ops.kt).
const LOGIC_BUDGET_MS: f64 = 120.0;
const INFO_BUDGET_MS: f64 = 80.0;

pub fn routes() -> Vec<RouteSpec> {
    let r = RouteSpec::new;
    let m = Some(Rank::Moderator);
    let o = Some(Rank::Owner);
    vec![
        r(Method::GET, "/events/dashboard", m, "/events/dashboard", None, || {
            get(dashboard_events)
        }),
        r(Method::GET, "/players", m, "/players", Some("Players"), || get(players)),
        r(Method::GET, "/players/{name}", m, "/players/someone", None, || {
            get(player_card)
        }),
        r(Method::GET, "/moderation", m, "/moderation", Some("Moderation"), || {
            get(moderation)
        }),
        r(Method::GET, "/metrics", m, "/metrics", Some("Metrics"), || {
            get(metrics_page)
        }),
        r(Method::GET, "/logs", o, "/logs", Some("Logs"), || get(logs_page)),
        r(
            Method::GET,
            "/events/logs",
            o,
            "/events/logs?unit=lotus-hub",
            None,
            || get(log_events),
        ),
        r(Method::GET, "/audit", o, "/audit", Some("Audit"), || get(audit_page)),
        r(Method::GET, "/audit.csv", o, "/audit.csv", None, || get(audit_csv)),
    ]
}

fn actor(s: &Session, ip: &str) -> Actor {
    Actor {
        account_id: s.account_id,
        ip: ip.to_owned(),
    }
}

fn ms_when(ms: i64) -> String {
    OffsetDateTime::from_unix_timestamp(ms / 1000).map_or_else(|_| "—".into(), when)
}

fn gib(bytes: f64) -> String {
    format!("{:.1} GB", bytes / 1_073_741_824.0)
}

// ---------------------------------------------------------------- dashboard

pub struct WorldRow {
    pub id: i32,
    pub state: &'static str,
    pub css: &'static str,
    pub locked: bool,
    pub players: String,
    pub logic_p50: String,
    pub logic_p99: String,
    pub info_p99: String,
    pub over_budget: bool,
    pub uptime: String,
}

/// The live part of the dashboard, sent again every few seconds over SSE.
pub struct DashboardLive {
    pub worlds: Vec<WorldRow>,
    pub hub: &'static str,
    pub hub_css: &'static str,
    pub version: String,
    pub cpu: String,
    pub memory: String,
    pub disk: String,
    pub network: String,
    pub alerts: Vec<(String, String, String)>,
    pub stamp: String,
}

fn css(v: UnitView) -> &'static str {
    match v {
        UnitView::Running => "ok",
        UnitView::Starting | UnitView::Stopping => "busy",
        UnitView::Stopped | UnitView::Missing => "off",
        UnitView::Failed => "down",
    }
}

fn get_metric(latest: &HashMap<String, f64>, key: &str) -> Option<f64> {
    latest.get(key).copied()
}

pub async fn dashboard_live(state: &Shared, s: &Session) -> DashboardLive {
    let latest = state.metrics.latest().await.unwrap_or_default();
    let views = state.control.views().await;
    let locked: Vec<i32> = state
        .hub
        .list_worlds(&actor(s, "dashboard"))
        .await
        .unwrap_or_default()
        .into_iter()
        .filter(|w| w.locked)
        .map(|w| w.id)
        .collect();
    let worlds = state
        .control
        .topology
        .worlds
        .iter()
        .map(|w| {
            let id = w.world_id;
            let view = views.iter().find(|v| v.1 == w.unit).map_or(UnitView::Missing, |v| v.2);
            let q = |phase: &str, quantile: &str| {
                get_metric(
                    &latest,
                    &format!("tp_tick_ms{{world=\"{id}\",phase=\"{phase}\",quantile=\"{quantile}\"}}"),
                )
            };
            let (median, logic99, info99) = (q("logic", "0.5"), q("logic", "0.99"), q("info", "0.99"));
            let fmt = |v: Option<f64>| v.map_or_else(|| "—".to_owned(), |v| format!("{v:.0} ms"));
            WorldRow {
                id,
                state: view.label(),
                css: css(view),
                locked: locked.contains(&id) || locked.contains(&0),
                players: get_metric(&latest, &format!("tp_players{{world=\"{id}\"}}"))
                    .map_or_else(|| "—".into(), |v| format!("{v:.0}")),
                logic_p50: fmt(median),
                logic_p99: fmt(logic99),
                info_p99: fmt(info99),
                over_budget: logic99.is_some_and(|v| v > LOGIC_BUDGET_MS) || info99.is_some_and(|v| v > INFO_BUDGET_MS),
                uptime: get_metric(&latest, &format!("tp_uptime_seconds{{node=\"{id}\"}}")).map_or_else(
                    || "—".into(),
                    |secs| {
                        #[allow(clippy::cast_possible_truncation)]
                        let s = secs as i64;
                        format!("{}h {:02}m", s / 3600, s / 60 % 60)
                    },
                ),
            }
        })
        .collect();
    let hub_view = views
        .iter()
        .find(|v| v.1 == state.control.topology.hub)
        .map_or(UnitView::Missing, |v| v.2);
    let version = latest
        .keys()
        .find_map(|k| {
            k.strip_prefix("tp_build_info{version=\"")
                .and_then(|v| v.split('"').next())
        })
        .unwrap_or("—")
        .to_owned();
    let pair = |used: &str, total: &str| match (get_metric(&latest, used), get_metric(&latest, total)) {
        (Some(u), Some(t)) if t > 0.0 => format!("{} of {} ({:.0}%)", gib(u), gib(t), u / t * 100.0),
        _ => "—".into(),
    };
    let network = match (
        get_metric(&latest, "vps_net_rx_bytes_per_s"),
        get_metric(&latest, "vps_net_tx_bytes_per_s"),
    ) {
        (Some(rx), Some(tx)) => format!("{:.0} KB/s in, {:.0} KB/s out", rx / 1024.0, tx / 1024.0),
        _ => "—".into(),
    };
    DashboardLive {
        worlds,
        hub: hub_view.label(),
        hub_css: css(hub_view),
        version,
        cpu: get_metric(&latest, "vps_cpu_percent").map_or_else(|| "—".into(), |v| format!("{v:.0}%")),
        memory: pair("vps_mem_used_bytes", "vps_mem_total_bytes"),
        disk: pair("vps_disk_used_bytes", "vps_disk_total_bytes"),
        network,
        alerts: state
            .alerts
            .recent(5)
            .await
            .into_iter()
            .map(|(at, t, b)| (when(at), t, b))
            .collect(),
        stamp: crate::web::clock_time(state.clock.now()),
    }
}

#[derive(Template)]
#[template(path = "dashboard_live.html")]
struct LiveFragment {
    l: DashboardLive,
}

#[derive(Template)]
#[template(path = "dashboard.html")]
pub struct DashboardPage {
    pub nav: Option<Nav>,
    pub name: String,
    pub rank: Rank,
    pub live: DashboardLive,
}

async fn dashboard_events(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let deadline = tokio::time::Instant::now() + STREAM_LIFETIME;
    let stream = stream::unfold((state, s, true), move |(state, s, first)| async move {
        if tokio::time::Instant::now() >= deadline {
            return None;
        }
        if !first {
            tokio::time::sleep(DASHBOARD_EVERY).await;
        }
        let html = LiveFragment {
            l: dashboard_live(&state, &s).await,
        }
        .render()
        .unwrap_or_default();
        Some((Ok(Event::default().event("dashboard").data(html)), (state, s, false)))
    });
    Sse::new(stream).keep_alive(KeepAlive::default())
}

// ---------------------------------------------------------------- players

#[derive(Deserialize, Default)]
struct PlayerQuery {
    #[serde(default)]
    q: String,
    #[serde(default)]
    world: String,
    #[serde(default)]
    rank: String,
}

#[derive(Template)]
#[template(path = "players.html")]
struct PlayersPage {
    nav: Option<Nav>,
    rows: Vec<crate::hub::OnlinePlayer>,
    total: usize,
    q: String,
    world: String,
    rank: String,
    worlds: Vec<i32>,
    error: Option<String>,
}

async fn players(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(f): Query<PlayerQuery>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let (all, error) = match state.hub.list_online(&actor(&s, &ip)).await {
        Ok(p) => (p, None),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };
    let total = all.len();
    let q = f.q.trim().to_lowercase();
    let rows = all
        .into_iter()
        .filter(|p| q.is_empty() || p.name.to_lowercase().contains(&q))
        .filter(|p| f.world.is_empty() || p.world.to_string() == f.world)
        .filter(|p| f.rank.is_empty() || p.rank.stored().eq_ignore_ascii_case(&f.rank))
        .collect();
    let worlds = state.control.topology.worlds.iter().map(|w| w.world_id).collect();
    render(
        StatusCode::OK,
        &PlayersPage {
            nav: Some(nav(&s)),
            rows,
            total,
            q: f.q,
            world: f.world,
            rank: f.rank,
            worlds,
            error,
        },
    )
}

pub struct RecordRow {
    /// The record type as the game names it (`yell_mute`), for lift and extend.
    pub code: String,
    pub kind: String,
    pub actor: String,
    pub reason: String,
    pub start: String,
    pub expires: String,
    pub scope: String,
    pub lifted: String,
    pub active: bool,
}

fn record_row(r: &crate::hub::ModRecord, now_ms: i64) -> RecordRow {
    let lasting = matches!(
        r.kind.as_str(),
        "MUTE" | "YELL_MUTE" | "JAIL" | "BAN" | "IP_BAN" | "DEVICE_BAN"
    );
    RecordRow {
        code: r.kind.to_lowercase(),
        kind: r.kind.to_lowercase().replace('_', " "),
        actor: r.actor.clone(),
        reason: r.reason.clone(),
        start: ms_when(r.start_ms),
        expires: if !lasting {
            String::new()
        } else if r.expires_ms < 0 {
            "permanent".into()
        } else {
            ms_when(r.expires_ms)
        },
        scope: r.scope.clone(),
        lifted: if r.lifted_by.is_empty() {
            String::new()
        } else {
            format!("lifted by {} {}", r.lifted_by, ms_when(r.lifted_at_ms))
        },
        active: lasting && r.lifted_by.is_empty() && (r.expires_ms < 0 || r.expires_ms > now_ms),
    }
}

#[derive(Template)]
#[template(path = "player.html")]
struct PlayerCardPage {
    nav: Option<Nav>,
    name: String,
    card: Option<crate::hub::PlayerCard>,
    records: Vec<RecordRow>,
    notes: Vec<RecordRow>,
    admin: bool,
    ips: Vec<String>,
    alts: Vec<String>,
    error: Option<String>,
    muted: String,
    jailed: String,
    banned: String,
    /// (command, timed) the viewer's rank offers (brief §5: the game decides again).
    actions: Vec<(&'static str, bool)>,
}

fn until(ms: i64, now_ms: i64) -> String {
    if ms < 0 {
        "permanently".into()
    } else if ms > now_ms {
        format!("until {}", ms_when(ms))
    } else {
        String::new()
    }
}

async fn player_card(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Path(name): Path<String>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let a = actor(&s, &ip);
    let now_ms = state.clock.now().unix_timestamp() * 1000;
    let card = state.hub.lookup(&a, &name).await;
    let error = card.as_ref().err().map(ToString::to_string);
    let card = card.ok();
    let history = if card.is_some() {
        state.hub.history(&a, &name).await.unwrap_or_default()
    } else {
        Vec::new()
    };
    let (notes, records): (Vec<_>, Vec<_>) = history
        .iter()
        .map(|r| record_row(r, now_ms))
        .partition(|r| r.kind == "note");
    // Brief §8: IPs and alt links are for administrators and above; the hub audits every view.
    let admin = s.rank >= Rank::Administrator;
    let (ips, alts) = if admin && card.is_some() {
        (
            state.hub.investigate(&a, &name, "ips", "").await.unwrap_or_default(),
            state.hub.investigate(&a, &name, "alts", "").await.unwrap_or_default(),
        )
    } else {
        (Vec::new(), Vec::new())
    };
    let (muted, jailed, banned) = card.as_ref().map_or_else(Default::default, |c| {
        (
            until(c.muted_until, now_ms),
            until(c.jailed_until, now_ms),
            until(c.banned_until, now_ms),
        )
    });
    let page = PlayerCardPage {
        nav: Some(nav(&s)),
        name,
        card,
        records,
        notes,
        admin,
        ips,
        alts,
        error,
        muted,
        jailed,
        banned,
        actions: crate::actions::SANCTIONS
            .iter()
            .filter(|a| s.rank >= a.1)
            .map(|a| (a.0, a.2))
            .collect(),
    };
    render(StatusCode::OK, &page)
}

// ---------------------------------------------------------------- moderation

#[derive(Deserialize, Default)]
struct ModQuery {
    #[serde(default)]
    kind: String,
    #[serde(default)]
    staff: String,
    #[serde(default)]
    all: Option<String>,
}

pub struct SanctionRow {
    pub account: String,
    pub record: RecordRow,
}

#[derive(Template)]
#[template(path = "moderation.html")]
struct ModerationPage {
    nav: Option<Nav>,
    rows: Vec<SanctionRow>,
    staff: String,
    all: bool,
    kinds: Vec<(&'static str, bool)>,
    error: Option<String>,
}

const KINDS: [&str; 10] = [
    "MUTE",
    "YELL_MUTE",
    "JAIL",
    "BAN",
    "IP_BAN",
    "DEVICE_BAN",
    "KICK",
    "WARN",
    "NOTE",
    "RENAME",
];

async fn moderation(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(f): Query<ModQuery>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let all = f.all.is_some();
    let kind = if KINDS.contains(&f.kind.as_str()) {
        f.kind.clone()
    } else {
        String::new()
    };
    let now_ms = state.clock.now().unix_timestamp() * 1000;
    let (rows, error) = match state
        .hub
        .list_sanctions(&actor(&s, &ip), &kind, f.staff.trim(), !all, 200)
        .await
    {
        Ok(rows) => (
            rows.iter()
                .map(|(r, account)| SanctionRow {
                    account: account.clone(),
                    record: record_row(r, now_ms),
                })
                .collect(),
            None,
        ),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };
    let kinds = KINDS.iter().map(|k| (*k, *k == kind)).collect();
    render(
        StatusCode::OK,
        &ModerationPage {
            nav: Some(nav(&s)),
            rows,
            staff: f.staff,
            all,
            kinds,
            error,
        },
    )
}

// ---------------------------------------------------------------- metrics

#[derive(Deserialize, Default)]
struct RangeQuery {
    #[serde(default)]
    range: String,
}

pub struct Chart {
    pub title: String,
    pub svg: String,
    pub last: String,
}

#[derive(Template)]
#[template(path = "metrics.html")]
struct MetricsPage {
    nav: Option<Nav>,
    ranges: Vec<(&'static str, bool)>,
    charts: Vec<Chart>,
}

async fn metrics_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<RangeQuery>,
) -> Response {
    let (range, span, bucket) = match q.range.as_str() {
        "24h" => ("24h", time::Duration::hours(24), 300),
        "7d" => ("7d", time::Duration::days(7), 1800),
        "30d" => ("30d", time::Duration::days(30), 7200),
        _ => ("1h", time::Duration::hours(1), 30),
    };
    let since = state.clock.now() - span;
    let mut specs: Vec<(String, String, Option<f64>, &'static str)> = Vec::new();
    for w in &state.control.topology.worlds {
        let id = w.world_id;
        specs.push((
            format!("World {id}: players"),
            format!("tp_players{{world=\"{id}\"}}"),
            None,
            "",
        ));
        specs.push((
            format!("World {id}: logic tick p99 (budget 120 ms)"),
            format!("tp_tick_ms{{world=\"{id}\",phase=\"logic\",quantile=\"0.99\"}}"),
            Some(LOGIC_BUDGET_MS),
            " ms",
        ));
        specs.push((
            format!("World {id}: info tick p99 (budget 80 ms)"),
            format!("tp_tick_ms{{world=\"{id}\",phase=\"info\",quantile=\"0.99\"}}"),
            Some(INFO_BUDGET_MS),
            " ms",
        ));
        specs.push((
            format!("World {id}: instances (raids)"),
            format!("tp_instances{{world=\"{id}\"}}"),
            None,
            "",
        ));
        specs.push((
            format!("World {id}: GC pause per 15 s"),
            format!("tp_gc_ms_interval{{node=\"{id}\"}}"),
            None,
            " ms",
        ));
    }
    specs.push(("VPS CPU".into(), "vps_cpu_percent".into(), None, "%"));
    specs.push(("VPS network in".into(), "vps_net_rx_bytes_per_s".into(), None, " B/s"));
    specs.push(("VPS network out".into(), "vps_net_tx_bytes_per_s".into(), None, " B/s"));
    let mut charts = Vec::new();
    for (title, series, budget, unit) in specs {
        let points = state.metrics.range(&series, since, bucket).await.unwrap_or_default();
        let last = points
            .last()
            .map_or_else(|| "no data".to_owned(), |p| format!("{:.1}{unit}", p.1));
        charts.push(Chart {
            title,
            svg: svg_chart(&points, 600, 120, budget),
            last,
        });
    }
    let ranges = ["1h", "24h", "7d", "30d"]
        .into_iter()
        .map(|r| (r, r == range))
        .collect();
    render(
        StatusCode::OK,
        &MetricsPage {
            nav: Some(nav(&s)),
            ranges,
            charts,
        },
    )
}

// ---------------------------------------------------------------- logs

#[derive(Deserialize, Default, Clone)]
struct LogQuery {
    #[serde(default)]
    unit: String,
    #[serde(default)]
    level: String,
    #[serde(default)]
    text: String,
    #[serde(default)]
    since: String,
}

#[derive(Template)]
#[template(path = "logs.html")]
struct LogsPage {
    nav: Option<Nav>,
    units: Vec<(String, bool)>,
    levels: Vec<(&'static str, bool)>,
    text: String,
    since: String,
    lines: Vec<String>,
    error: Option<String>,
    stream: String,
}

fn log_units(state: &Shared) -> Vec<String> {
    let t = &state.control.topology;
    let mut units = vec![t.hub.clone()];
    units.extend(t.worlds.iter().map(|w| w.unit.clone()));
    units.extend(["lotus-panel".to_owned(), "lotus-caddy".to_owned(), t.database.clone()]);
    units
}

async fn logs_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<LogQuery>,
) -> Response {
    let units = log_units(&state);
    let unit = if units.contains(&q.unit) {
        q.unit.clone()
    } else {
        units[0].clone()
    };
    let level = logs::LEVELS.iter().find(|l| **l == q.level).copied();
    let path = logs::current(&state.paths.logs, &unit);
    let (lines, error) = match tokio::task::spawn_blocking(move || logs::tail(&path)).await {
        Ok(Ok(all)) => (logs::filter(&all, level, &q.text, &q.since), None),
        Ok(Err(e)) => (Vec::new(), Some(format!("Can't read the log: {e}"))),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };
    let stream = format!(
        "/events/logs?{}",
        serde_urlencoded::to_string([
            ("unit", unit.as_str()),
            ("level", level.unwrap_or("")),
            ("text", q.text.as_str())
        ])
        .unwrap_or_default()
    );
    let page = LogsPage {
        nav: Some(nav(&s)),
        units: units.iter().map(|u| (u.clone(), *u == unit)).collect(),
        levels: logs::LEVELS.iter().map(|l| (*l, Some(*l) == level)).collect(),
        text: q.text,
        since: q.since,
        lines,
        error,
        stream,
    };
    render(StatusCode::OK, &page)
}

/// New lines of a log as they are written (brief §4: live logs over SSE).
async fn log_events(State(state): State<Shared>, Query(q): Query<LogQuery>) -> Response {
    let units = log_units(&state);
    if !units.contains(&q.unit) {
        return StatusCode::NOT_FOUND.into_response();
    }
    let path = logs::current(&state.paths.logs, &q.unit);
    let level = logs::LEVELS.iter().find(|l| **l == q.level).copied();
    let text = q.text.clone();
    let start = tokio::fs::metadata(&path).await.map_or(0, |m| m.len());
    let deadline = tokio::time::Instant::now() + STREAM_LIFETIME;
    let stream = stream::unfold((path, start), move |(path, mut pos)| {
        let text = text.clone();
        async move {
            loop {
                if tokio::time::Instant::now() >= deadline {
                    return None;
                }
                tokio::time::sleep(LOG_POLL).await;
                let len = tokio::fs::metadata(&path).await.map_or(0, |m| m.len());
                if len < pos {
                    pos = 0; // rotated
                }
                if len == pos {
                    continue;
                }
                let Ok(bytes) = read_range(&path, pos, len).await else {
                    continue;
                };
                pos = len;
                let lines: Vec<String> = String::from_utf8_lossy(&bytes).lines().map(str::to_owned).collect();
                let shown = logs::filter(&lines, level, &text, "");
                if shown.is_empty() {
                    continue;
                }
                let html = shown.iter().fold(String::new(), |mut out, l| {
                    out.push_str(&askama_escape(l));
                    out.push('\n');
                    out
                });
                return Some((
                    Ok::<Event, Infallible>(Event::default().event("lines").data(html)),
                    (path, pos),
                ));
            }
        }
    });
    Sse::new(stream).keep_alive(KeepAlive::default()).into_response()
}

async fn read_range(path: &std::path::Path, from: u64, to: u64) -> std::io::Result<Vec<u8>> {
    use tokio::io::{AsyncReadExt, AsyncSeekExt};
    let mut f = tokio::fs::File::open(path).await?;
    f.seek(std::io::SeekFrom::Start(from)).await?;
    let mut buf = vec![0; usize::try_from((to - from).min(logs::TAIL_BYTES)).unwrap_or(0)];
    f.read_exact(&mut buf).await?;
    Ok(buf)
}

/// HTML-escapes text sent outside a template (the live log lines).
pub fn askama_escape(s: &str) -> String {
    s.chars().fold(String::with_capacity(s.len()), |mut out, c| {
        match c {
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '&' => out.push_str("&amp;"),
            '"' => out.push_str("&quot;"),
            '\'' => out.push_str("&#x27;"),
            c => out.push(c),
        }
        out
    })
}

// ---------------------------------------------------------------- audit

#[derive(Deserialize, Default)]
struct AuditQueryForm {
    #[serde(default)]
    actor: String,
    #[serde(default)]
    target: String,
    #[serde(default)]
    action: String,
    #[serde(default)]
    source: String,
    #[serde(default)]
    text: String,
    /// `YYYY-MM-DD`
    #[serde(default)]
    since: String,
    #[serde(default)]
    until: String,
    #[serde(default)]
    before: Option<i64>,
}

fn day_ms(day: &str) -> i64 {
    let fmt = time::macros::format_description!("[year]-[month]-[day]");
    time::Date::parse(day, fmt).map_or(0, |d| d.midnight().assume_utc().unix_timestamp() * 1000)
}

fn audit_filter(f: &AuditQueryForm, limit: i32) -> AuditFilter {
    AuditFilter {
        actor: f.actor.trim().to_owned(),
        target: f.target.trim().to_owned(),
        action: f.action.trim().to_owned(),
        source: f.source.trim().to_owned(),
        text: f.text.trim().to_owned(),
        since_ms: day_ms(f.since.trim()),
        until_ms: if f.until.trim().is_empty() {
            0
        } else {
            day_ms(f.until.trim()) + 86_400_000
        },
        before_id: f.before.unwrap_or(0),
        limit,
    }
}

pub struct AuditView {
    pub row: AuditRow,
    pub at: String,
}

#[derive(Template)]
#[template(path = "audit.html")]
struct AuditPage {
    nav: Option<Nav>,
    rows: Vec<AuditView>,
    actor: String,
    target: String,
    action: String,
    source: String,
    text: String,
    since: String,
    until: String,
    next: Option<String>,
    csv: String,
    error: Option<String>,
}

/// Rows per audit page.
const AUDIT_PAGE: i32 = 100;

async fn audit_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(f): Query<AuditQueryForm>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let (rows, error) = match state
        .hub
        .query_audit(&actor(&s, &ip), &audit_filter(&f, AUDIT_PAGE))
        .await
    {
        Ok(r) => (r, None),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };
    let params = |before: Option<i64>| {
        let mut p = vec![
            ("actor", f.actor.clone()),
            ("target", f.target.clone()),
            ("action", f.action.clone()),
            ("source", f.source.clone()),
            ("text", f.text.clone()),
            ("since", f.since.clone()),
            ("until", f.until.clone()),
        ];
        if let Some(b) = before {
            p.push(("before", b.to_string()));
        }
        serde_urlencoded::to_string(p).unwrap_or_default()
    };
    let next = (rows.len() == usize::try_from(AUDIT_PAGE).unwrap_or(0))
        .then(|| format!("/audit?{}", params(rows.last().map(|r| r.id))));
    let csv = format!("/audit.csv?{}", params(None));
    let rows = rows
        .into_iter()
        .map(|row| AuditView {
            at: ms_when(row.at_ms),
            row,
        })
        .collect();
    let page = AuditPage {
        nav: Some(nav(&s)),
        rows,
        actor: f.actor,
        target: f.target,
        action: f.action,
        source: f.source,
        text: f.text,
        since: f.since,
        until: f.until,
        next,
        csv,
        error,
    };
    render(StatusCode::OK, &page)
}

/// A CSV cell: quoted, and a leading `=`, `+`, `-` or `@` defused so a spreadsheet won't run it as a formula.
fn csv_cell(s: &str) -> String {
    let s = if s.starts_with(['=', '+', '-', '@', '\t', '\r']) {
        format!("'{s}")
    } else {
        s.to_owned()
    };
    format!("\"{}\"", s.replace('"', "\"\""))
}

/// Rows per CSV export (the hub's largest page; our limit).
const CSV_ROWS: i32 = 500;

async fn audit_csv(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(f): Query<AuditQueryForm>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let a = actor(&s, &ip);
    let Ok(rows) = state.hub.query_audit(&a, &audit_filter(&f, CSV_ROWS)).await else {
        return StatusCode::SERVICE_UNAVAILABLE.into_response();
    };
    crate::web::audit(
        &state,
        &s,
        &ip,
        "panel audit export",
        "audit log",
        &format!("{} rows", rows.len()),
        "ok",
    )
    .await;
    let mut out = String::from("id,time_utc,actor,action,target,detail,source,ip,result\n");
    for r in rows {
        let cells = [
            r.id.to_string(),
            ms_when(r.at_ms),
            r.actor,
            r.action,
            r.target,
            r.detail,
            r.source,
            r.ip,
            r.result,
        ];
        out.push_str(&cells.iter().map(|c| csv_cell(c)).collect::<Vec<_>>().join(","));
        out.push('\n');
    }
    (
        [
            (header::CONTENT_TYPE, "text/csv; charset=utf-8"),
            (header::CONTENT_DISPOSITION, "attachment; filename=\"lotus-audit.csv\""),
        ],
        out,
    )
        .into_response()
}
