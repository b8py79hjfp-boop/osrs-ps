//! Sign-in, sessions, CSRF and the per-route rank guard (brief §2, §8; no second factor, plan §1a).
//!
//! - The session cookie `__Host-lotus` holds 32 random bytes; the store keeps only their SHA-256.
//! - Sessions end after [`AuthSettings::idle`] without a request or [`AuthSettings::absolute`] after sign-in.
//! - The rank comes from the hub at sign-in and again at least every [`RANK_RECHECK`]: a demotion below moderator, a ban
//!   or a deleted account ends the session at the next request after that.
//! - Every state-changing request carries the session's CSRF token (form field `csrf` or header `x-csrf-token`); the
//!   sign-in form uses a double-submit cookie. A request with a foreign `Origin` is refused.
//! - Failed sign-ins lock the account (and, at a higher count, the address) for [`LOCK_FOR`].

use std::collections::{HashMap, VecDeque};
use std::sync::Mutex;

use axum::body::{Body, to_bytes};
use axum::extract::Request;
use axum::http::{HeaderMap, Method, StatusCode, header};
use axum::response::{IntoResponse, Redirect, Response};
use base64::Engine;
use base64::engine::general_purpose::URL_SAFE_NO_PAD;
use sha2::{Digest, Sha256};
use time::{Duration, OffsetDateTime};

use crate::rank::Rank;
use crate::store::Session;

pub const SESSION_COOKIE: &str = "__Host-lotus";
pub const LOGIN_COOKIE: &str = "__Host-lotus-login";

/// Brief §2: the rank is re-checked at least every 60 s.
pub const RANK_RECHECK: Duration = Duration::seconds(60);
/// While the hub can't be asked, the last confirmed rank is trusted this long, then every page refuses (our choice:
/// the `ctl` CLI on the VPS is the way in while the hub is down).
pub const RANK_GRACE: Duration = Duration::minutes(10);
/// `last_seen_at` is written at most this often (fewer writes; idle expiry stays accurate to the minute).
pub const TOUCH_EVERY: Duration = Duration::seconds(30);

/// Brief §2: repeated failures lock the account for 15 minutes.
pub const LOCK_FOR: Duration = Duration::minutes(15);
/// Failures within [`LOCK_FOR`] that lock an account (our choice).
pub const ACCOUNT_FAILURES: usize = 5;
/// Failures from one address within [`LOCK_FOR`] that lock the address (higher: several staff can share one).
pub const IP_FAILURES: usize = 20;

/// Largest form the guard reads to find the CSRF field (the body limit is the same).
const MAX_FORM: usize = 64 * 1024;

pub struct AuthSettings {
    pub idle: Duration,
    pub absolute: Duration,
    /// `scheme://host[:port]` of the public URL; requests whose `Origin` differs are refused.
    pub origin: String,
}

impl AuthSettings {
    pub fn new(idle_minutes: u32, absolute_hours: u32, public_url: &str) -> Self {
        Self {
            idle: Duration::minutes(i64::from(idle_minutes)),
            absolute: Duration::hours(i64::from(absolute_hours)),
            origin: origin_of(public_url),
        }
    }

    pub fn live(&self, s: &Session, now: OffsetDateTime) -> bool {
        s.revoked_at.is_none() && now - s.last_seen_at <= self.idle && now - s.created_at <= self.absolute
    }
}

fn origin_of(url: &str) -> String {
    let url = url.trim_end_matches('/');
    match url.find("://") {
        Some(i) => match url[i + 3..].find('/') {
            Some(j) => url[..i + 3 + j].to_owned(),
            None => url.to_owned(),
        },
        None => url.to_owned(),
    }
}

// ---------------------------------------------------------------- tokens

/// 32 random bytes, URL-safe base64.
pub fn new_token() -> String {
    let mut bytes = [0u8; 32];
    getrandom::fill(&mut bytes).unwrap_or_else(|e| panic!("the OS random source failed: {e}"));
    URL_SAFE_NO_PAD.encode(bytes)
}

pub fn hash_token(token: &str) -> Vec<u8> {
    Sha256::digest(token.as_bytes()).to_vec()
}

/// Equal-length comparison that doesn't stop at the first difference.
pub fn same(a: &str, b: &str) -> bool {
    a.len() == b.len() && a.bytes().zip(b.bytes()).fold(0u8, |acc, (x, y)| acc | (x ^ y)) == 0
}

pub fn cookie(headers: &HeaderMap, name: &str) -> Option<String> {
    headers
        .get_all(header::COOKIE)
        .iter()
        .filter_map(|v| v.to_str().ok())
        .flat_map(|v| v.split(';'))
        .filter_map(|pair| pair.trim().split_once('='))
        .find(|(k, _)| *k == name)
        .map(|(_, v)| v.to_owned())
}

/// `__Host-` cookies: `Secure`, `Path=/`, no `Domain`; `HttpOnly` and `SameSite=Strict` (brief §2).
pub fn set_cookie(name: &str, value: &str, max_age: Duration) -> String {
    format!(
        "{name}={value}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age={}",
        max_age.whole_seconds()
    )
}

pub fn clear_cookie(name: &str) -> String {
    format!("{name}=; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=0")
}

// ---------------------------------------------------------------- lockout

/// Game account names are at most 12 characters; anything longer is cut before it becomes a key.
const MAX_NAME: usize = 32;
/// Lockout entries kept at most (memory bound under a flood of names or addresses).
const MAX_ENTRIES: usize = 10_000;

/// Sign-in failures per account from one address, and per address (in memory: one panel process; a restart forgets).
/// An account lock is per address (security review F8): a stranger can't lock a staff member out from elsewhere,
/// and the hub's own per-account throttle still slows guessing spread over many addresses.
#[derive(Default)]
pub struct Lockout(Mutex<HashMap<String, VecDeque<OffsetDateTime>>>);

impl Lockout {
    fn with<T>(&self, f: impl FnOnce(&mut HashMap<String, VecDeque<OffsetDateTime>>) -> T) -> T {
        f(&mut self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner))
    }

    fn account_key(account: &str, ip: &str) -> String {
        // Bounded: an attacker chooses the name.
        let name: String = account.to_lowercase().chars().take(MAX_NAME).collect();
        format!("acct:{name}@{ip}")
    }

    /// Keeps memory bounded: expired entries go, and past [`MAX_ENTRIES`] everything is forgotten (a flood).
    fn trim(map: &mut HashMap<String, VecDeque<OffsetDateTime>>, now: OffsetDateTime) {
        if map.len() < MAX_ENTRIES {
            return;
        }
        map.retain(|_, q| q.back().is_some_and(|t| now - *t <= LOCK_FOR));
        if map.len() >= MAX_ENTRIES {
            map.clear();
        }
    }

    fn count(map: &mut HashMap<String, VecDeque<OffsetDateTime>>, key: &str, now: OffsetDateTime) -> usize {
        map.get_mut(key).map_or(0, |q| {
            while q.front().is_some_and(|t| now - *t > LOCK_FOR) {
                q.pop_front();
            }
            q.len()
        })
    }

    /// Whether a sign-in for [account] from [ip] is refused right now.
    pub fn locked(&self, account: &str, ip: &str, now: OffsetDateTime) -> bool {
        self.with(|m| {
            Self::count(m, &Self::account_key(account, ip), now) >= ACCOUNT_FAILURES
                || Self::count(m, &format!("ip:{ip}"), now) >= IP_FAILURES
        })
    }

    /// Records a failure; returns true when it is the one that locks the account.
    pub fn fail(&self, account: &str, ip: &str, now: OffsetDateTime) -> bool {
        self.with(|m| {
            Self::trim(m, now);
            let key = Self::account_key(account, ip);
            m.entry(key.clone()).or_default().push_back(now);
            m.entry(format!("ip:{ip}")).or_default().push_back(now);
            Self::count(m, &key, now) == ACCOUNT_FAILURES
        })
    }

    pub fn succeed(&self, account: &str, ip: &str) {
        self.with(|m| m.remove(&Self::account_key(account, ip)));
    }
}

// ---------------------------------------------------------------- the guard

/// The signed-in staff member, put into the request by [`guard`] for handlers.
#[derive(Clone)]
pub struct Auth(pub Session);

pub enum Denied {
    /// No valid session: pages go to the sign-in form, everything else gets 401.
    SignedOut,
    /// Below the route's rank: 404, as if the page didn't exist (brief §4: not shown at all).
    Hidden,
    /// Missing or wrong CSRF token, or a foreign Origin.
    Forbidden,
    /// The hub hasn't confirmed the rank within [`RANK_GRACE`].
    HubDown,
}

impl Denied {
    pub fn respond(self, method: &Method) -> Response {
        match self {
            Self::SignedOut if method == Method::GET || method == Method::HEAD => {
                let mut r = Redirect::to("/login").into_response();
                r.headers_mut().insert(
                    header::SET_COOKIE,
                    clear_cookie(SESSION_COOKIE).parse().unwrap_or_else(|_| unreachable!()),
                );
                r
            }
            Self::SignedOut => (StatusCode::UNAUTHORIZED, "Signed out.").into_response(),
            Self::Hidden => StatusCode::NOT_FOUND.into_response(),
            Self::Forbidden => (StatusCode::FORBIDDEN, "Refused: reload the page and try again.").into_response(),
            Self::HubDown => (
                StatusCode::SERVICE_UNAVAILABLE,
                "The game hub isn't answering, so your rank can't be confirmed. Use `lotus-panel ctl` on the VPS.",
            )
                .into_response(),
        }
    }
}

/// Whether the request's `Origin`, when present, is the panel's own.
pub fn origin_ok(headers: &HeaderMap, origin: &str) -> bool {
    headers
        .get(header::ORIGIN)
        .and_then(|v| v.to_str().ok())
        .is_none_or(|o| o == origin)
}

/// The CSRF token of a state-changing request: the `x-csrf-token` header (htmx), else the `csrf` form field. The body
/// is read and put back unchanged.
pub async fn csrf_of(req: Request) -> (Request, Option<String>) {
    if let Some(h) = req.headers().get("x-csrf-token").and_then(|v| v.to_str().ok()) {
        let token = h.to_owned();
        return (req, Some(token));
    }
    let (parts, body) = req.into_parts();
    let Ok(bytes) = to_bytes(body, MAX_FORM).await else {
        return (Request::from_parts(parts, Body::empty()), None);
    };
    let token = serde_urlencoded::from_bytes::<Vec<(String, String)>>(&bytes)
        .ok()
        .and_then(|fields| fields.into_iter().find(|(k, _)| k == "csrf").map(|(_, v)| v));
    (Request::from_parts(parts, Body::from(bytes)), token)
}

/// The rank to allow a route, given the session's freshly checked rank (`None` = public route).
pub fn allowed(min: Option<Rank>, rank: Option<Rank>) -> Result<(), Denied> {
    match (min, rank) {
        (None, _) => Ok(()),
        (Some(_), None) => Err(Denied::SignedOut),
        (Some(min), Some(rank)) if rank >= min => Ok(()),
        (Some(_), Some(_)) => Err(Denied::Hidden),
    }
}

pub fn is_safe(method: &Method) -> bool {
    method == Method::GET || method == Method::HEAD
}
