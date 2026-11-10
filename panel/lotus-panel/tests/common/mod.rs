//! Shared harness for the brief §10 tests: the real router, a fake hub, in-memory sessions and a manual clock.
#![allow(dead_code, clippy::unwrap_used, clippy::expect_used, clippy::missing_panics_doc)]

use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use axum::Router;
use axum::body::Body;
use axum::http::{Request, Response, StatusCode, header};
use http_body_util::BodyExt;
use lotus_panel::auth::{AuthSettings, Lockout};
use lotus_panel::clock::ManualClock;
use lotus_panel::hub::{AuditEntry, HubApi, HubError, RankInfo, Verified};
use lotus_panel::rank::Rank;
use lotus_panel::store::MemorySessions;
use lotus_panel::web::{AppState, router};
use time::OffsetDateTime;
use tower::ServiceExt;

pub const PASSWORD: &str = "secret1";
pub const ORIGIN: &str = "https://panel.test";

#[derive(Clone)]
pub struct Account {
    pub id: i64,
    pub rank: Rank,
    pub banned: bool,
}

#[derive(Default)]
pub struct FakeHub {
    pub accounts: Mutex<HashMap<String, Account>>,
    pub audits: Mutex<Vec<String>>,
}

impl FakeHub {
    pub fn add(&self, name: &str, rank: Rank) -> i64 {
        let mut a = self.accounts.lock().unwrap();
        let id = i64::try_from(a.len()).unwrap() + 1;
        a.insert(
            name.to_lowercase(),
            Account {
                id,
                rank,
                banned: false,
            },
        );
        id
    }

    pub fn set_rank(&self, name: &str, rank: Rank) {
        self.accounts
            .lock()
            .unwrap()
            .get_mut(&name.to_lowercase())
            .unwrap()
            .rank = rank;
    }
}

#[async_trait]
impl HubApi for FakeHub {
    async fn verify_staff(&self, username: &str, password: &str, _ip: &str) -> Result<Verified, HubError> {
        let a = self.accounts.lock().unwrap().get(&username.to_lowercase()).cloned();
        Ok(match a {
            Some(a) if password == PASSWORD && a.banned => Verified::Banned,
            Some(a) if password == PASSWORD && !a.rank.is_staff() => Verified::NotStaff,
            Some(a) if password == PASSWORD => Verified::Ok {
                account_id: a.id,
                display_name: username.to_owned(),
                rank: a.rank,
            },
            _ => Verified::InvalidCredentials,
        })
    }

    async fn get_rank(&self, account_id: i64) -> Result<RankInfo, HubError> {
        let a = self
            .accounts
            .lock()
            .unwrap()
            .values()
            .find(|a| a.id == account_id)
            .cloned();
        Ok(match a {
            Some(a) => RankInfo {
                exists: true,
                rank: a.rank,
                banned: a.banned,
            },
            None => RankInfo {
                exists: false,
                rank: Rank::Player,
                banned: false,
            },
        })
    }

    async fn audit(&self, e: AuditEntry<'_>) -> Result<(), HubError> {
        self.audits
            .lock()
            .unwrap()
            .push(format!("{} {} {}", e.action, e.target, e.result));
        Ok(())
    }

    async fn bootstrap_owner(&self, _username: &str) -> Result<(), HubError> {
        Ok(())
    }

    async fn healthy(&self) -> bool {
        true
    }
}

pub struct Harness {
    pub app: Router,
    pub hub: Arc<FakeHub>,
    pub clock: Arc<ManualClock>,
}

impl Harness {
    pub fn new() -> Self {
        let hub = Arc::new(FakeHub::default());
        let clock = Arc::new(ManualClock::new(
            OffsetDateTime::from_unix_timestamp(1_800_000_000).unwrap(),
        ));
        let state = AppState {
            hub: hub.clone(),
            sessions: Arc::new(MemorySessions::default()),
            clock: clock.clone(),
            lockout: Lockout::default(),
            auth: AuthSettings::new(30, 12, ORIGIN),
            allowed: Vec::new(),
        };
        Self {
            app: router(Arc::new(state)),
            hub,
            clock,
        }
    }

    pub async fn send(&self, req: Request<Body>) -> Response<Body> {
        self.app.clone().oneshot(req).await.unwrap()
    }

    pub async fn get(&self, path: &str, session: Option<&str>) -> Response<Body> {
        let mut b = Request::get(path);
        if let Some(s) = session {
            b = b.header(header::COOKIE, format!("__Host-lotus={s}"));
        }
        self.send(b.body(Body::empty()).unwrap()).await
    }

    pub async fn post(&self, path: &str, cookies: &str, form: &str, origin: Option<&str>) -> Response<Body> {
        let mut b = Request::post(path).header(header::CONTENT_TYPE, "application/x-www-form-urlencoded");
        if !cookies.is_empty() {
            b = b.header(header::COOKIE, cookies);
        }
        if let Some(o) = origin {
            b = b.header(header::ORIGIN, o);
        }
        self.send(b.body(Body::from(form.to_owned())).unwrap()).await
    }

    /// Opens the sign-in form: (pre-cookie value, form token).
    pub async fn login_form(&self) -> (String, String) {
        let r = self.get("/login", None).await;
        let pre = set_cookie(&r, "__Host-lotus-login").expect("login cookie");
        let html = text(r).await;
        (pre, hidden(&html, "csrf").expect("csrf field"))
    }

    pub async fn try_sign_in(&self, name: &str, password: &str) -> Response<Body> {
        let (pre, token) = self.login_form().await;
        let form = format!("username={name}&password={password}&csrf={token}");
        self.post("/login", &format!("__Host-lotus-login={pre}"), &form, Some(ORIGIN))
            .await
    }

    /// Signs in; returns (session cookie value, session CSRF token).
    pub async fn sign_in(&self, name: &str) -> (String, String) {
        let r = self.try_sign_in(name, PASSWORD).await;
        assert_eq!(r.status(), StatusCode::SEE_OTHER, "sign-in of {name}");
        let session = set_cookie(&r, "__Host-lotus").expect("session cookie");
        let page = text(self.get("/", Some(&session)).await).await;
        (session.clone(), hidden(&page, "csrf").expect("session csrf"))
    }
}

pub fn set_cookie(r: &Response<Body>, name: &str) -> Option<String> {
    r.headers()
        .get_all(header::SET_COOKIE)
        .iter()
        .filter_map(|v| v.to_str().ok())
        .filter_map(|v| v.split(';').next()?.split_once('='))
        .find(|(k, v)| *k == name && !v.is_empty())
        .map(|(_, v)| v.to_owned())
}

pub fn location(r: &Response<Body>) -> Option<String> {
    r.headers()
        .get(header::LOCATION)
        .and_then(|v| v.to_str().ok())
        .map(str::to_owned)
}

pub async fn text(r: Response<Body>) -> String {
    String::from_utf8(r.into_body().collect().await.unwrap().to_bytes().to_vec()).unwrap()
}

pub fn hidden(html: &str, name: &str) -> Option<String> {
    let key = format!("name=\"{name}\" value=\"");
    let start = html.find(&key)? + key.len();
    Some(html[start..].split('"').next()?.to_owned())
}
