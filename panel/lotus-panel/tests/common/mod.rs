//! Shared harness for the brief §10 tests: the real router, a fake hub, fake Windows services, in-memory stores and a
//! manual clock.
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
use lotus_panel::control::{Controller, Probe, Topology, WorldUnit};
use lotus_panel::hub::{Actor, AuditEntry, HubApi, HubError, OpLine, RankInfo, Verified, WorldStatus};
use lotus_panel::jobs::{JobState, JobStore, MemoryAlerts, MemoryJobs, MemorySettings};
use lotus_panel::rank::Rank;
use lotus_panel::store::MemorySessions;
use lotus_panel::units::{ServiceState, Units};
use lotus_panel::web::{AppState, PanelPaths, router};
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
    /// Calls in order, shared with [`FakeUnits`] so tests can check the sequence ("hub lock 0", "unit stop lotus", …).
    pub calls: Arc<Mutex<Vec<String>>>,
    pub down: Mutex<bool>,
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

    fn call(&self, c: String) {
        self.calls.lock().unwrap().push(c);
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
        !*self.down.lock().unwrap()
    }

    async fn list_worlds(&self, _actor: &Actor) -> Result<Vec<WorldStatus>, HubError> {
        Ok(Vec::new())
    }

    async fn login_locks(&self, _actor: &Actor) -> Result<Vec<(i32, String)>, HubError> {
        Ok(Vec::new())
    }

    async fn set_login_lock(&self, _actor: &Actor, world: i32, locked: bool, _message: &str) -> Result<(), HubError> {
        self.call(format!("hub {} {world}", if locked { "lock" } else { "unlock" }));
        Ok(())
    }

    async fn system_update(&self, _actor: &Actor, seconds: i32, message: &str) -> Result<(), HubError> {
        self.call(format!("hub update {seconds} {message}"));
        Ok(())
    }

    async fn cancel_update(&self, _actor: &Actor) -> Result<(), HubError> {
        self.call("hub cancel".into());
        Ok(())
    }

    async fn world_op(
        &self,
        _actor: &Actor,
        _world: i32,
        kind: &str,
        _args: &str,
    ) -> Result<tokio::sync::mpsc::Receiver<OpLine>, HubError> {
        let (tx, rx) = tokio::sync::mpsc::channel(4);
        let _ = tx
            .send(OpLine {
                line: format!("{kind} done"),
                done: true,
                error: false,
            })
            .await;
        Ok(rx)
    }
}

/// Windows services, faked: start makes a unit running, stop stops it; calls go into the shared log.
pub struct FakeUnits {
    pub states: Mutex<HashMap<String, ServiceState>>,
    pub calls: Arc<Mutex<Vec<String>>>,
}

#[async_trait]
impl Units for FakeUnits {
    async fn state(&self, unit: &str) -> ServiceState {
        *self.states.lock().unwrap().get(unit).unwrap_or(&ServiceState::Missing)
    }

    async fn start(&self, unit: &str) -> anyhow::Result<()> {
        self.calls.lock().unwrap().push(format!("unit start {unit}"));
        self.states
            .lock()
            .unwrap()
            .insert(unit.to_owned(), ServiceState::Running);
        Ok(())
    }

    async fn stop(&self, unit: &str) -> anyhow::Result<()> {
        self.calls.lock().unwrap().push(format!("unit stop {unit}"));
        self.states
            .lock()
            .unwrap()
            .insert(unit.to_owned(), ServiceState::Stopped);
        Ok(())
    }
}

/// Health follows the fake service state, unless a unit is forced down (a crash).
pub struct FakeProbe {
    pub units: Arc<FakeUnits>,
    pub crashed: Mutex<Vec<String>>,
}

impl FakeProbe {
    fn up(&self, unit: &str) -> bool {
        *self
            .units
            .states
            .lock()
            .unwrap()
            .get(unit)
            .unwrap_or(&ServiceState::Missing)
            == ServiceState::Running
            && !self.crashed.lock().unwrap().iter().any(|u| u == unit)
    }
}

#[async_trait]
impl Probe for FakeProbe {
    async fn database_up(&self) -> bool {
        self.up("lotus-postgresql")
    }

    async fn hub_up(&self) -> bool {
        self.up("lotus-hub")
    }

    async fn world_up(&self, w: &WorldUnit) -> bool {
        self.up(&w.unit)
    }
}

pub fn topology() -> Topology {
    Topology {
        database: "lotus-postgresql".into(),
        hub: "lotus-hub".into(),
        worlds: vec![WorldUnit {
            unit: "lotus".into(),
            world_id: 1,
            game_port: 43594,
            health_url: String::new(),
        }],
        logs: std::env::temp_dir(),
    }
}

pub struct Harness {
    pub app: Router,
    pub hub: Arc<FakeHub>,
    pub clock: Arc<ManualClock>,
    pub units: Arc<FakeUnits>,
    pub probe: Arc<FakeProbe>,
    pub control: Arc<Controller>,
    pub jobs: Arc<MemoryJobs>,
    pub alerts: Arc<MemoryAlerts>,
}

impl Harness {
    pub fn new() -> Self {
        let hub = Arc::new(FakeHub::default());
        let clock = Arc::new(ManualClock::new(
            OffsetDateTime::from_unix_timestamp(1_800_000_000).unwrap(),
        ));
        let units = Arc::new(FakeUnits {
            states: Mutex::new(HashMap::new()),
            calls: hub.calls.clone(),
        });
        for u in ["lotus-postgresql", "lotus-hub", "lotus"] {
            units.states.lock().unwrap().insert(u.into(), ServiceState::Stopped);
        }
        let probe = Arc::new(FakeProbe {
            units: units.clone(),
            crashed: Mutex::new(Vec::new()),
        });
        let jobs = Arc::new(MemoryJobs::default());
        let alerts = Arc::new(MemoryAlerts::default());
        let control = Arc::new(Controller::new(
            topology(),
            units.clone(),
            probe.clone(),
            hub.clone(),
            jobs.clone(),
            alerts.clone(),
            clock.clone(),
        ));
        // A job and a dump file, so the permission test's sample URLs exist.
        let _ = block_now(jobs.create("sample", "test"));
        let dumps = std::env::temp_dir().join(format!("lotus-panel-test-{}", std::process::id()));
        std::fs::create_dir_all(&dumps).unwrap();
        std::fs::write(dumps.join("sample.txt"), "dump").unwrap();
        let paths = PanelPaths {
            logs: dumps.clone(),
            dumps: dumps.clone(),
            backups: dumps.clone(),
            releases: dumps.clone(),
            certificates: dumps,
        };
        let state = AppState {
            hub: hub.clone(),
            sessions: Arc::new(MemorySessions::default()),
            clock: clock.clone(),
            lockout: Lockout::default(),
            auth: AuthSettings::new(30, 12, ORIGIN),
            allowed: Vec::new(),
            control: control.clone(),
            jobs: jobs.clone(),
            settings: Arc::new(MemorySettings::default()),
            paths,
            domain: "panel.test".into(),
        };
        Self {
            app: router(Arc::new(state)),
            hub,
            clock,
            units,
            probe,
            control,
            jobs,
            alerts,
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

/// Runs a future that never waits (the in-memory stores) outside an async context.
pub fn block_now<F: std::future::Future>(f: F) -> F::Output {
    let mut cx = std::task::Context::from_waker(std::task::Waker::noop());
    let mut f = std::pin::pin!(f);
    loop {
        if let std::task::Poll::Ready(v) = f.as_mut().poll(&mut cx) {
            return v;
        }
    }
}

/// Waits (in paused tokio time) until job [id] has finished; returns its state.
pub async fn finished(jobs: &MemoryJobs, id: i64) -> JobState {
    for _ in 0..100_000 {
        if let Some(j) = jobs.get(id).await.unwrap()
            && j.state != JobState::Running
        {
            return j.state;
        }
        tokio::time::sleep(std::time::Duration::from_millis(500)).await;
    }
    panic!("job {id} never finished");
}

pub fn actor() -> Actor {
    Actor {
        account_id: 1,
        ip: "127.0.0.1".into(),
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
