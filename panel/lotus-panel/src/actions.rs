//! Actions through the game (brief §5): every player or moderation action here becomes a `::` command line the hub
//! routes to a world, where it runs through the same pipeline as a typed command, as the signed-in staff member
//! (rank, targeting, world policy, confirmation, audit). The panel only builds the line; it never decides the rules.
//!
//! Pages: the console (output streamed over SSE), actions on the player card, lift and extend on the moderation page,
//! help tickets, chat and broadcast, worlds (login lock, command toggles) and staff ranks.

use std::collections::HashMap;
use std::convert::Infallible;
use std::fmt::Write as _;
use std::sync::Mutex;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{Duration, Instant};

use askama::Template;
use axum::Extension;
use axum::Form;
use axum::extract::{FromRequest, Path, Query, Request, State};
use axum::http::{Method, StatusCode};
use axum::response::sse::{Event, KeepAlive, Sse};
use axum::response::{IntoResponse, Redirect, Response};
use axum::routing::{get, post};
use futures_util::stream;
use serde::Deserialize;
use time::OffsetDateTime;

use crate::auth::Auth;
use crate::hub::{Actor, OpLine};
use crate::pages::askama_escape;
use crate::rank::Rank;
use crate::store::Session;
use crate::web::{Nav, RouteSpec, Shared, audit, confirm_password, nav, peer_ip, render, when};

/// A console run's output is kept this long for its page (memory only: output can hold IPs, so it is never stored).
const RUN_TTL: Duration = Duration::from_secs(600);
/// At most this many runs are kept at once; the oldest go first.
const MAX_RUNS: usize = 200;
/// The console stream polls the run this often.
const RUN_POLL: Duration = Duration::from_millis(200);
/// A run's stream gives up after this long (the world answers within ~10 s; `WorldOpsHandler` waits at most 5 + 5 s).
const RUN_STREAM_LIFETIME: Duration = Duration::from_secs(30);
/// Listing tickets waits this long for each world.
const COLLECT_TIMEOUT: Duration = Duration::from_secs(12);
/// Longest command line the console accepts (a few chat lines).
const MAX_LINE: usize = 300;
/// Longest reason or note (the game's ticket and chat limits are in this range).
const MAX_REASON: usize = 200;

#[allow(clippy::too_many_lines)] // one entry per route: the table reads best whole
pub fn routes() -> Vec<RouteSpec> {
    let r = RouteSpec::new;
    let m = Some(Rank::Moderator);
    let a = Some(Rank::Administrator);
    let o = Some(Rank::Owner);
    vec![
        r(Method::GET, "/console", m, "/console", Some("Console"), || {
            get(console_page)
        }),
        r(Method::POST, "/console/run", m, "/console/run", None, || {
            post(console_run)
        }),
        r(
            Method::GET,
            "/events/console/{id}",
            m,
            "/events/console/1",
            None,
            || get(console_events),
        ),
        r(
            Method::POST,
            "/players/{name}/act",
            m,
            "/players/someone/act",
            None,
            || post(player_act),
        ),
        r(Method::POST, "/moderation/lift", m, "/moderation/lift", None, || {
            post(lift)
        }),
        r(
            Method::POST,
            "/moderation/extend",
            m,
            "/moderation/extend",
            None,
            || post(extend),
        ),
        r(
            Method::GET,
            "/moderation/tickets",
            m,
            "/moderation/tickets",
            None,
            || get(tickets_fragment),
        ),
        r(
            Method::POST,
            "/moderation/tickets",
            m,
            "/moderation/tickets",
            None,
            || post(ticket_act),
        ),
        r(Method::GET, "/chat", m, "/chat", Some("Chat"), || get(chat_page)),
        r(Method::POST, "/chat/broadcast", a, "/chat/broadcast", None, || {
            post(broadcast)
        }),
        r(Method::GET, "/worlds", o, "/worlds", Some("Worlds"), || {
            get(worlds_page)
        }),
        r(Method::POST, "/worlds/lock", o, "/worlds/lock", None, || {
            post(world_lock)
        }),
        r(Method::POST, "/worlds/command", o, "/worlds/command", None, || {
            post(world_command)
        }),
        r(Method::GET, "/staff", o, "/staff", Some("Staff"), || get(staff_page)),
        r(Method::POST, "/staff/rank", o, "/staff/rank", None, || post(set_rank)),
    ]
}

fn actor(s: &Session, ip: &str) -> Actor {
    Actor {
        account_id: s.account_id,
        ip: ip.to_owned(),
    }
}

// ---------------------------------------------------------------- command lines

/// A game account name as one command token: letters, digits, spaces, `-` and `_`, at most 12 (the game's rule);
/// spaces become `_`, which the game reads as a space.
pub fn name_token(name: &str) -> Option<String> {
    let n = name.trim();
    let ok = !n.is_empty()
        && n.chars().count() <= 12
        && n.chars()
            .all(|c| c.is_ascii_alphanumeric() || c == ' ' || c == '-' || c == '_');
    ok.then(|| n.replace(' ', "_"))
}

/// Free text for the end of a line: no control characters or double quotes (the tokenizer groups on quotes), single
/// spaces, at most [`MAX_REASON`] characters.
pub fn clean_text(text: &str) -> String {
    text.chars()
        .map(|c| if c.is_control() { ' ' } else { c })
        .filter(|c| *c != '"')
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .chars()
        .take(MAX_REASON)
        .collect()
}

/// The game's durations: `30s`, `10m`, `2h`, `7d` or `perm` (plugin-api `Durations`).
pub fn duration_ok(d: &str) -> bool {
    if d == "perm" {
        return true;
    }
    let Some(unit) = d.chars().last() else { return false };
    let digits = &d[..d.len() - unit.len_utf8()];
    "smhd".contains(unit) && (1..=5).contains(&digits.len()) && digits.chars().all(|c| c.is_ascii_digit())
}

/// The sanctions the player card offers, with the lowest rank the game allows each (commands brief §6) and whether it
/// takes a duration. The game checks again; this only decides what the form shows.
pub const SANCTIONS: [(&str, Rank, bool); 17] = [
    ("warn", Rank::Moderator, false),
    ("note", Rank::Moderator, false),
    ("kick", Rank::Moderator, false),
    ("mute", Rank::Moderator, true),
    ("unmute", Rank::Moderator, false),
    ("yellmute", Rank::Moderator, true),
    ("unyellmute", Rank::Moderator, false),
    ("jail", Rank::Moderator, true),
    ("unjail", Rank::Moderator, false),
    ("freeze", Rank::Administrator, false),
    ("unfreeze", Rank::Administrator, false),
    ("ban", Rank::Administrator, true),
    ("unban", Rank::Administrator, false),
    ("ipban", Rank::Administrator, true),
    ("unipban", Rank::Administrator, false),
    ("deviceban", Rank::Administrator, true),
    ("undeviceban", Rank::Administrator, false),
];

/// Bans ask for the password again (brief §2: dangerous actions).
pub fn needs_password(action: &str) -> bool {
    matches!(action, "ban" | "ipban" | "deviceban")
}

/// The line for one player-card action, or why it can't be built.
pub fn sanction_line(action: &str, name: &str, duration: &str, reason: &str) -> Result<String, &'static str> {
    let Some((_, _, timed)) = SANCTIONS.iter().find(|s| s.0 == action) else {
        return Err("Unknown action.");
    };
    let name = name_token(name).ok_or("That isn't a valid account name.")?;
    let reason = clean_text(reason);
    let mut line = format!("{action} {name}");
    if *timed {
        if !duration_ok(duration) {
            return Err("Give a duration like 30m, 2h, 7d or perm.");
        }
        line.push(' ');
        line.push_str(duration);
    }
    let reason_required = *timed || matches!(action, "warn" | "note");
    if reason_required && reason.is_empty() {
        return Err("Give a reason.");
    }
    if !reason.is_empty() && action != "freeze" && action != "unfreeze" {
        line.push(' ');
        line.push_str(&reason);
    }
    Ok(line)
}

/// The lift command for a record kind from the moderation page.
pub fn lift_of(kind: &str) -> Option<&'static str> {
    Some(match kind {
        "mute" => "unmute",
        "yell_mute" => "unyellmute",
        "jail" => "unjail",
        "ban" => "unban",
        "ip_ban" => "unipban",
        "device_ban" => "undeviceban",
        _ => return None,
    })
}

/// The command that applies a record kind again (extend = the same sanction with a new duration from now).
pub fn apply_of(kind: &str) -> Option<&'static str> {
    Some(match kind {
        "mute" => "mute",
        "yell_mute" => "yellmute",
        "jail" => "jail",
        "ban" => "ban",
        "ip_ban" => "ipban",
        "device_ban" => "deviceban",
        _ => return None,
    })
}

// ---------------------------------------------------------------- console runs

struct Run {
    owner: i64,
    title: String,
    created: Instant,
    lines: Vec<OpLine>,
    done: bool,
}

/// Console output in memory, per run, readable only by the staff member who started it.
#[derive(Default)]
pub struct ConsoleRuns {
    runs: Mutex<HashMap<u64, Run>>,
    next: AtomicU64,
}

impl ConsoleRuns {
    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<u64, Run>> {
        self.runs.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
    }

    fn start(&self, owner: i64, title: String) -> u64 {
        let id = self.next.fetch_add(1, Ordering::Relaxed) + 1;
        let mut runs = self.lock();
        runs.retain(|_, r| r.created.elapsed() < RUN_TTL);
        while runs.len() >= MAX_RUNS {
            let Some(oldest) = runs.iter().min_by_key(|(_, r)| r.created).map(|(k, _)| *k) else {
                break;
            };
            runs.remove(&oldest);
        }
        runs.insert(
            id,
            Run {
                owner,
                title,
                created: Instant::now(),
                lines: Vec::new(),
                done: false,
            },
        );
        id
    }

    fn push(&self, id: u64, line: OpLine) {
        if let Some(r) = self.lock().get_mut(&id) {
            r.done |= line.done;
            r.lines.push(line);
        }
    }

    /// Lines from `from` on and whether the run is over; `None` when it isn't this owner's (or is gone).
    fn read(&self, id: u64, owner: i64, from: usize) -> Option<(Vec<OpLine>, bool)> {
        let runs = self.lock();
        let r = runs.get(&id).filter(|r| r.owner == owner)?;
        Some((r.lines.iter().skip(from).cloned().collect(), r.done))
    }

    fn title(&self, id: u64, owner: i64) -> Option<String> {
        self.lock()
            .get(&id)
            .filter(|r| r.owner == owner)
            .map(|r| r.title.clone())
    }

    /// The owner's runs, newest first: (id, title, done).
    fn recent(&self, owner: i64) -> Vec<(u64, String, bool)> {
        let mut v: Vec<_> = self
            .lock()
            .iter()
            .filter(|(_, r)| r.owner == owner)
            .map(|(id, r)| (*id, r.title.clone(), r.done))
            .collect();
        v.sort_by_key(|r| std::cmp::Reverse(r.0));
        v.truncate(10);
        v
    }
}

/// Starts `line` on `world` (0 = any) as the session's staff member; the output collects in a run.
fn spawn_command(state: &Shared, s: &Session, ip: &str, world: i32, line: &str) -> u64 {
    let where_ = if world == 0 {
        "any world".to_owned()
    } else {
        format!("world {world}")
    };
    let id = state.console.start(s.account_id, format!("::{line} ({where_})"));
    let st = state.clone();
    let a = actor(s, ip);
    let line = line.to_owned();
    tokio::spawn(async move {
        match st.hub.world_op(&a, world, "command", &line).await {
            Ok(mut rx) => {
                while let Some(l) = rx.recv().await {
                    let done = l.done;
                    st.console.push(id, l);
                    if done {
                        return;
                    }
                }
                st.console.push(id, end_line("The world stopped answering."));
            }
            Err(e) => st.console.push(id, end_line(&format!("The hub didn't take it: {e}"))),
        }
    });
    id
}

fn end_line(text: &str) -> OpLine {
    OpLine {
        line: text.to_owned(),
        done: true,
        error: true,
    }
}

/// Runs `line` on `world` and waits for the whole answer (ticket listings).
async fn collect(state: &Shared, a: &Actor, world: i32, line: &str) -> Result<Vec<OpLine>, String> {
    let mut rx = state
        .hub
        .world_op(a, world, "command", line)
        .await
        .map_err(|e| e.to_string())?;
    let mut out = Vec::new();
    let deadline = tokio::time::Instant::now() + COLLECT_TIMEOUT;
    loop {
        match tokio::time::timeout_at(deadline, rx.recv()).await {
            Ok(Some(l)) => {
                let done = l.done;
                out.push(l);
                if done {
                    return Ok(out);
                }
            }
            Ok(None) => return Ok(out),
            Err(_) => return Err(format!("World {world} didn't answer in time.")),
        }
    }
}

/// The world a target is on (to run world-local commands there), or 0 when offline.
async fn world_of(state: &Shared, a: &Actor, name: &str) -> i32 {
    let wanted = name.trim().replace('_', " ").to_lowercase();
    state
        .hub
        .list_online(a)
        .await
        .unwrap_or_default()
        .into_iter()
        .find(|p| p.name.to_lowercase() == wanted)
        .map_or(0, |p| p.world)
}

// ---------------------------------------------------------------- console page

#[derive(Template)]
#[template(path = "console.html")]
struct ConsolePage {
    nav: Option<Nav>,
    worlds: Vec<i32>,
    run: Option<(u64, String)>,
    recent: Vec<(u64, String, bool)>,
    error: Option<String>,
    line: String,
}

#[derive(Deserialize, Default)]
struct ConsoleQuery {
    #[serde(default)]
    run: Option<u64>,
    #[serde(default)]
    error: Option<String>,
}

async fn worlds_known(state: &Shared, a: &Actor) -> Vec<i32> {
    let mut w: Vec<i32> = state
        .hub
        .list_worlds(a)
        .await
        .unwrap_or_default()
        .into_iter()
        .map(|w| w.id)
        .collect();
    if w.is_empty() {
        w = state.control.topology.worlds.iter().map(|w| w.world_id).collect();
    }
    w.sort_unstable();
    w.dedup();
    w
}

async fn console_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<ConsoleQuery>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let run = q
        .run
        .and_then(|id| state.console.title(id, s.account_id).map(|t| (id, t)));
    let error = q.error.map(|e| clean_text(&e));
    let page = ConsolePage {
        nav: Some(nav(&s)),
        worlds: worlds_known(&state, &actor(&s, &ip)).await,
        run,
        recent: state.console.recent(s.account_id),
        error,
        line: String::new(),
    };
    render(StatusCode::OK, &page)
}

#[derive(Deserialize)]
struct RunForm {
    line: String,
    #[serde(default)]
    world: i32,
    #[serde(default)]
    password: String,
}

/// Console commands that ask for the password again, like their buttons elsewhere in the panel (security review F3):
/// bans, rank and name changes, and the owner commands that affect every player.
pub const DANGEROUS: [&str; 12] = [
    "ban",
    "ipban",
    "deviceban",
    "setrank",
    "rename",
    "update",
    "cancelupdate",
    "kickall",
    "lockworld",
    "command",
    "config",
    "confirm",
];

/// Whether `line` (no `::`) starts with a command that asks for the password again (aliases resolve in the game, so
/// the check is on the typed word).
pub fn needs_password_line(line: &str) -> bool {
    let word = line.split_whitespace().next().unwrap_or_default().to_lowercase();
    DANGEROUS.contains(&word.as_str())
}

async fn console_run(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<RunForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let line: String = f
        .line
        .trim()
        .trim_start_matches("::")
        .chars()
        .map(|c| if c.is_control() { ' ' } else { c })
        .collect();
    if line.trim().is_empty() || line.chars().count() > MAX_LINE || f.world < 0 {
        return Redirect::to("/console?error=Type+a+command.").into_response();
    }
    if needs_password_line(&line) && !confirm_password(&state, &s, &f.password, &ip).await {
        audit(&state, &s, &ip, "panel command", "", line.trim(), "refused: password").await;
        return back_with_error(
            "/console",
            "That command needs your password: fill in the password box.",
        );
    }
    let id = spawn_command(&state, &s, &ip, f.world, line.trim());
    Redirect::to(&format!("/console?run={id}")).into_response()
}

async fn console_events(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Path(id): Path<u64>,
) -> Response {
    // Someone else's run, or one that expired, reads as gone (a 404 would look like a hidden route).
    if state.console.title(id, s.account_id).is_none() {
        let gone = stream::iter([
            Ok::<Event, Infallible>(
                Event::default()
                    .event("line")
                    .data("<div class=\"error\">That output is gone.</div>"),
            ),
            Ok(Event::default().event("done").data("")),
        ]);
        return Sse::new(gone).into_response();
    }
    let deadline = tokio::time::Instant::now() + RUN_STREAM_LIFETIME;
    let owner = s.account_id;
    let stream = stream::unfold((state, 0usize, false), move |(state, from, finished)| async move {
        if finished {
            return None;
        }
        loop {
            let (lines, done) = state.console.read(id, owner, from)?;
            if !lines.is_empty() {
                let html = lines.iter().fold(String::new(), |mut out, l| {
                    out.push_str(if l.error { "<div class=\"error\">" } else { "<div>" });
                    out.push_str(&askama_escape(&l.line));
                    out.push_str("</div>");
                    out
                });
                return Some((
                    Ok::<Event, Infallible>(Event::default().event("line").data(html)),
                    (state, from + lines.len(), false),
                ));
            }
            if done {
                return Some((Ok(Event::default().event("done").data("")), (state, from, true)));
            }
            if tokio::time::Instant::now() >= deadline {
                return None;
            }
            tokio::time::sleep(RUN_POLL).await;
        }
    });
    Sse::new(stream).keep_alive(KeepAlive::default()).into_response()
}

// ---------------------------------------------------------------- player card and moderation actions

#[derive(Deserialize)]
struct ActForm {
    action: String,
    #[serde(default)]
    duration: String,
    #[serde(default)]
    reason: String,
    #[serde(default)]
    password: String,
}

fn back_with_error(to: &str, error: &str) -> Response {
    let sep = if to.contains('?') { '&' } else { '?' };
    Redirect::to(&format!("{to}{sep}error={}", urlencoding(error))).into_response()
}

pub fn urlencoding(s: &str) -> String {
    s.bytes().fold(String::new(), |mut out, b| {
        if b.is_ascii_alphanumeric() || b"-_.~".contains(&b) {
            out.push(char::from(b));
        } else {
            let _ = write!(out, "%{b:02X}");
        }
        out
    })
}

async fn player_act(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Path(name): Path<String>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<ActForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let line = match sanction_line(&f.action, &name, f.duration.trim(), &f.reason) {
        Ok(l) => l,
        Err(e) => return back_with_error("/console", e),
    };
    if needs_password(&f.action) && !confirm_password(&state, &s, &f.password, &ip).await {
        audit(&state, &s, &ip, "panel action", &name, &f.action, "refused: password").await;
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    // Online targets: on their own world (freeze, kick and the rest act there at once); offline: any world.
    let world = world_of(&state, &actor(&s, &ip), &name).await;
    let id = spawn_command(&state, &s, &ip, world, &line);
    Redirect::to(&format!("/console?run={id}")).into_response()
}

#[derive(Deserialize)]
struct RecordForm {
    account: String,
    kind: String,
    #[serde(default)]
    duration: String,
    #[serde(default)]
    reason: String,
    #[serde(default)]
    password: String,
}

async fn lift(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<RecordForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let Some(cmd) = lift_of(&f.kind) else {
        return back_with_error("/console", "That record can't be lifted.");
    };
    let line = match sanction_line(cmd, &f.account, "", &f.reason) {
        Ok(l) => l,
        Err(e) => return back_with_error("/console", e),
    };
    let world = world_of(&state, &actor(&s, &ip), &f.account).await;
    let id = spawn_command(&state, &s, &ip, world, &line);
    Redirect::to(&format!("/console?run={id}")).into_response()
}

async fn extend(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<RecordForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let Some(cmd) = apply_of(&f.kind) else {
        return back_with_error("/console", "That record can't be extended.");
    };
    let line = match sanction_line(cmd, &f.account, f.duration.trim(), &f.reason) {
        Ok(l) => l,
        Err(e) => return back_with_error("/console", e),
    };
    if needs_password(cmd) && !confirm_password(&state, &s, &f.password, &ip).await {
        audit(&state, &s, &ip, "panel action", &f.account, cmd, "refused: password").await;
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let world = world_of(&state, &actor(&s, &ip), &f.account).await;
    let id = spawn_command(&state, &s, &ip, world, &line);
    Redirect::to(&format!("/console?run={id}")).into_response()
}

// ---------------------------------------------------------------- tickets

/// One help ticket as `::tickets` prints it: `#id | owner | open or claimer | minutes | text`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Ticket {
    pub world: i32,
    pub id: u32,
    pub owner: String,
    pub claimed_by: String,
    pub minutes: u32,
    pub text: String,
}

/// Reads one `::tickets` line; anything else (the "No open tickets." reply, the end line) is skipped.
pub fn parse_ticket(world: i32, line: &str) -> Option<Ticket> {
    let rest = line.strip_prefix('#')?;
    let mut parts = rest.splitn(5, " | ");
    let id = parts.next()?.trim().parse().ok()?;
    let owner = parts.next()?.trim().to_owned();
    let by = parts.next()?.trim();
    let minutes = parts.next()?.trim().parse().ok()?;
    let text = parts.next()?.to_owned();
    Some(Ticket {
        world,
        id,
        owner,
        claimed_by: if by == "open" { String::new() } else { by.to_owned() },
        minutes,
        text,
    })
}

#[derive(Template)]
#[template(path = "tickets.html")]
struct TicketsFragment {
    tickets: Vec<Ticket>,
    errors: Vec<String>,
    csrf: String,
    me: String,
}

async fn tickets_fragment(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let a = actor(&s, &ip);
    let mut tickets = Vec::new();
    let mut errors = Vec::new();
    for w in worlds_known(&state, &a).await {
        match collect(&state, &a, w, "tickets").await {
            Ok(lines) => tickets.extend(lines.iter().filter_map(|l| parse_ticket(w, &l.line))),
            Err(e) => errors.push(e),
        }
    }
    render(
        StatusCode::OK,
        &TicketsFragment {
            tickets,
            errors,
            csrf: s.csrf.clone(),
            me: s.display_name.clone(),
        },
    )
}

#[derive(Deserialize)]
struct TicketForm {
    world: i32,
    id: u32,
    action: String,
    #[serde(default)]
    note: String,
}

async fn ticket_act(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<TicketForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if f.world <= 0 {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let line = match f.action.as_str() {
        "claim" => format!("claimticket {}", f.id),
        "release" => format!("claimticket {} off", f.id),
        "close" => format!("closeticket {} {}", f.id, clean_text(&f.note)),
        _ => return StatusCode::BAD_REQUEST.into_response(),
    };
    let id = spawn_command(&state, &s, &ip, f.world, line.trim());
    Redirect::to(&format!("/console?run={id}")).into_response()
}

// ---------------------------------------------------------------- chat and broadcast

pub struct ChatRow {
    pub at: String,
    pub kind: String,
    pub from: String,
    pub world: i32,
    pub text: String,
}

#[derive(Template)]
#[template(path = "chat.html")]
struct ChatPage {
    nav: Option<Nav>,
    rows: Vec<ChatRow>,
    worlds: Vec<i32>,
    can_broadcast: bool,
    error: Option<String>,
}

/// Chat text without the game's colour and image tags (askama escapes the rest).
pub fn plain(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    let mut rest = text;
    while let Some(i) = rest.find('<') {
        out.push_str(&rest[..i]);
        let after = &rest[i..];
        if let Some(j) = after.find('>') {
            match &after[1..j] {
                "lt" => out.push('<'),
                "gt" => out.push('>'),
                _ => {}
            }
            rest = &after[j + 1..];
        } else {
            out.push_str(after);
            rest = "";
        }
    }
    out.push_str(rest);
    out
}

#[derive(Deserialize, Default)]
struct ErrorQuery {
    #[serde(default)]
    error: Option<String>,
}

async fn chat_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<ErrorQuery>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let a = actor(&s, &ip);
    let (rows, error) = match state.hub.recent_chat(&a).await {
        Ok(lines) => (
            lines
                .into_iter()
                .rev()
                .map(|c| ChatRow {
                    at: OffsetDateTime::from_unix_timestamp(c.at_ms / 1000).map_or_else(|_| "—".into(), when),
                    kind: c.kind,
                    from: c.from,
                    world: c.world,
                    text: plain(&c.text),
                })
                .collect(),
            q.error.map(|e| clean_text(&e)),
        ),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };
    let page = ChatPage {
        nav: Some(nav(&s)),
        rows,
        worlds: worlds_known(&state, &a).await,
        can_broadcast: s.rank >= Rank::Administrator,
        error,
    };
    render(StatusCode::OK, &page)
}

#[derive(Deserialize)]
struct BroadcastForm {
    text: String,
    #[serde(default)]
    world: i32,
}

async fn broadcast(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<BroadcastForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    // `--all` is the command's own flag: typed text can't widen a one-world broadcast.
    let mut text = clean_text(&f.text);
    while let Some(t) = text.strip_suffix("--all") {
        text = t.trim_end().to_owned();
    }
    if text.is_empty() || f.world < 0 {
        return back_with_error("/chat", "Write the broadcast first.");
    }
    // `::broadcast` (administrator) on one world, or with --all on any world for every world.
    let line = if f.world == 0 {
        format!("broadcast {text} --all")
    } else {
        format!("broadcast {text}")
    };
    let id = spawn_command(&state, &s, &ip, f.world, &line);
    Redirect::to(&format!("/console?run={id}")).into_response()
}

// ---------------------------------------------------------------- worlds

pub struct WorldLine {
    pub id: i32,
    pub online: bool,
    pub locked: bool,
    pub message: String,
    pub players: i32,
}

#[derive(Template)]
#[template(path = "worlds.html")]
struct WorldsPage {
    nav: Option<Nav>,
    worlds: Vec<WorldLine>,
    all_locked: Option<String>,
    error: Option<String>,
}

async fn worlds_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<ErrorQuery>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let a = actor(&s, &ip);
    let status = state.hub.list_worlds(&a).await;
    let locks = state.hub.login_locks(&a).await.unwrap_or_default();
    let mut error = q.error.map(|e| clean_text(&e));
    let online = state.hub.list_online(&a).await.unwrap_or_default();
    let mut worlds: Vec<WorldLine> = state
        .control
        .topology
        .worlds
        .iter()
        .map(|w| WorldLine {
            id: w.world_id,
            online: false,
            locked: false,
            message: String::new(),
            players: 0,
        })
        .collect();
    match status {
        Ok(list) => {
            for w in list {
                if !worlds.iter().any(|l| l.id == w.id) {
                    worlds.push(WorldLine {
                        id: w.id,
                        online: false,
                        locked: false,
                        message: String::new(),
                        players: 0,
                    });
                }
                if let Some(l) = worlds.iter_mut().find(|l| l.id == w.id) {
                    l.online = true;
                    l.locked = w.locked;
                }
            }
        }
        Err(e) => error = Some(e.to_string()),
    }
    for l in &mut worlds {
        if let Some((_, m)) = locks.iter().find(|(id, _)| *id == l.id) {
            l.locked = true;
            l.message.clone_from(m);
        }
        l.players = i32::try_from(online.iter().filter(|p| p.world == l.id).count()).unwrap_or(i32::MAX);
    }
    worlds.sort_by_key(|w| w.id);
    let all_locked = locks.iter().find(|(id, _)| *id == 0).map(|(_, m)| m.clone());
    render(
        StatusCode::OK,
        &WorldsPage {
            nav: Some(nav(&s)),
            worlds,
            all_locked,
            error,
        },
    )
}

#[derive(Deserialize)]
struct LockForm {
    world: i32,
    locked: String,
    #[serde(default)]
    message: String,
}

async fn world_lock(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<LockForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    if f.world < 0 {
        return StatusCode::BAD_REQUEST.into_response();
    }
    let locked = f.locked == "on";
    let message = clean_text(&f.message);
    let result = state
        .hub
        .set_login_lock(&actor(&s, &ip), f.world, locked, &message)
        .await;
    // The hub audits the lock with this IP and its result.
    match result {
        Ok(()) => Redirect::to("/worlds").into_response(),
        Err(e) => back_with_error("/worlds", &e.to_string()),
    }
}

#[derive(Deserialize)]
struct WorldCommandForm {
    world: i32,
    action: String,
    command: String,
}

async fn world_command(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<WorldCommandForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let name = f.command.trim().trim_start_matches("::").to_lowercase();
    let ok_name = !name.is_empty() && name.len() <= 32 && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
    if f.world <= 0 || !ok_name || !matches!(f.action.as_str(), "enable" | "disable") {
        return back_with_error("/worlds", "Pick a world, enable or disable, and a command name.");
    }
    // `::command` (owner) on that world; the game refuses turning an ECONOMY command on.
    let id = spawn_command(&state, &s, &ip, f.world, &format!("command {} {name}", f.action));
    Redirect::to(&format!("/console?run={id}")).into_response()
}

// ---------------------------------------------------------------- staff

pub struct StaffLine {
    pub name: String,
    pub rank: Rank,
    pub world: i32,
}

#[derive(Template)]
#[template(path = "staff.html")]
struct StaffPage {
    nav: Option<Nav>,
    staff: Vec<StaffLine>,
    ranks: Vec<&'static str>,
    error: Option<String>,
}

/// The ranks `::setrank` takes; the game decides who may give which (owners up to administrator, developers any).
const RANK_WORDS: [&str; 5] = ["player", "moderator", "administrator", "owner", "developer"];

async fn staff_page(
    State(state): State<Shared>,
    Extension(Auth(s)): Extension<Auth>,
    Query(q): Query<ErrorQuery>,
    req: Request,
) -> Response {
    let ip = peer_ip(&req).to_string();
    let (staff, error) = match state.hub.list_staff(&actor(&s, &ip)).await {
        Ok(list) => (
            list.into_iter()
                .map(|m| StaffLine {
                    name: m.name,
                    rank: m.rank,
                    world: m.world,
                })
                .collect(),
            q.error.map(|e| clean_text(&e)),
        ),
        Err(e) => (Vec::new(), Some(e.to_string())),
    };
    let ranks = RANK_WORDS
        .iter()
        .copied()
        .filter(|r| s.rank >= Rank::Developer || *r != "owner" && *r != "developer")
        .collect();
    render(
        StatusCode::OK,
        &StaffPage {
            nav: Some(nav(&s)),
            staff,
            ranks,
            error,
        },
    )
}

#[derive(Deserialize)]
struct RankForm {
    name: String,
    rank: String,
    password: String,
}

async fn set_rank(State(state): State<Shared>, Extension(Auth(s)): Extension<Auth>, req: Request) -> Response {
    let ip = peer_ip(&req).to_string();
    let Ok(Form(f)) = Form::<RankForm>::from_request(req, &state).await else {
        return StatusCode::BAD_REQUEST.into_response();
    };
    let Some(name) = name_token(&f.name) else {
        return back_with_error("/staff", "That isn't a valid account name.");
    };
    if !RANK_WORDS.contains(&f.rank.as_str()) {
        return StatusCode::BAD_REQUEST.into_response();
    }
    if !confirm_password(&state, &s, &f.password, &ip).await {
        audit(&state, &s, &ip, "panel set rank", &name, &f.rank, "refused: password").await;
        return StatusCode::UNPROCESSABLE_ENTITY.into_response();
    }
    let id = spawn_command(&state, &s, &ip, 0, &format!("setrank {name} {}", f.rank));
    Redirect::to(&format!("/console?run={id}")).into_response()
}
