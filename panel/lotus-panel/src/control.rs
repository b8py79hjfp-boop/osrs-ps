//! Server control (brief §3): start in order (database, hub, worlds, each health-gated), graceful stop and restart (lock
//! logins, the in-game update countdown, then stop in reverse order: the service wrapper's Ctrl+C makes the game save
//! everyone), emergency stop, cancel, maintenance mode, and crash-loop detection. One control job runs at a time; each
//! is a job row with its log.
//!
//! The database service is started but never stopped here: the panel keeps its own sessions in it.

use std::collections::{HashMap, HashSet, VecDeque};
use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use async_trait::async_trait;
use time::OffsetDateTime;
use tokio_util::sync::CancellationToken;

use crate::clock::Clock;
use crate::hub::{Actor, HubApi};
use crate::jobs::{Alerts, JobState, JobStore};
use crate::units::{ServiceState, Units};

/// A world node's service and how to tell it is healthy.
#[derive(Debug, Clone)]
pub struct WorldUnit {
    pub unit: String,
    pub world_id: i32,
    /// The game port; a world "accepts logins" when it is listening here.
    pub game_port: u16,
    /// The node's `/health` (200 = ticking within budget).
    pub health_url: String,
}

#[derive(Debug, Clone)]
pub struct Topology {
    pub database: String,
    pub hub: String,
    pub worlds: Vec<WorldUnit>,
    /// Where each service's log lives: `<logs>\<unit>\<unit>_rCURRENT.log`.
    pub logs: PathBuf,
}

#[async_trait]
pub trait Probe: Send + Sync {
    async fn database_up(&self) -> bool;
    async fn hub_up(&self) -> bool;
    async fn world_up(&self, world: &WorldUnit) -> bool;
}

/// Health as the brief defines it: the database answers, the hub answers, a world accepts connections on its game port
/// and its `/health` is 200.
pub struct LiveProbe {
    pub sessions: Arc<dyn crate::store::SessionStore>,
    pub hub: Arc<dyn HubApi>,
    pub http: reqwest::Client,
}

#[async_trait]
impl Probe for LiveProbe {
    async fn database_up(&self) -> bool {
        self.sessions.healthy().await
    }

    async fn hub_up(&self) -> bool {
        self.hub.healthy().await
    }

    async fn world_up(&self, w: &WorldUnit) -> bool {
        let listening = tokio::time::timeout(
            Duration::from_secs(2),
            tokio::net::TcpStream::connect(("127.0.0.1", w.game_port)),
        )
        .await
        .is_ok_and(|c| c.is_ok());
        listening
            && self
                .http
                .get(&w.health_url)
                .timeout(Duration::from_secs(3))
                .send()
                .await
                .is_ok_and(|r| r.status().is_success())
    }
}

/// What a stop or restart covers.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Scope {
    /// Every world and the hub.
    All,
    /// One world.
    World(i32),
}

/// A unit as the dashboard shows it.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UnitView {
    Running,
    Starting,
    Stopping,
    Stopped,
    Failed,
    Missing,
}

impl UnitView {
    pub const fn label(self) -> &'static str {
        match self {
            Self::Running => "running",
            Self::Starting => "starting",
            Self::Stopping => "stopping",
            Self::Stopped => "stopped",
            Self::Failed => "failed",
            Self::Missing => "not installed",
        }
    }
}

#[derive(Debug, thiserror::Error)]
pub enum ControlError {
    #[error("another control job (#{0}) is running; wait for it or cancel it")]
    Busy(i64),
    #[error("{0}")]
    Invalid(String),
    #[error(transparent)]
    Other(#[from] anyhow::Error),
}

/// Brief §3: three crashes within ten minutes is a crash loop.
pub const CRASH_LOOP_COUNT: usize = 3;
pub const CRASH_LOOP_WINDOW: time::Duration = time::Duration::minutes(10);
/// Brief §3: show the last 200 log lines of a crash loop.
const CRASH_LOG_LINES: usize = 200;
/// How long each unit may take to become healthy after a start (our limits: a world loads the cache first).
const DATABASE_TIMEOUT: Duration = Duration::from_secs(60);
const HUB_TIMEOUT: Duration = Duration::from_secs(120);
const WORLD_TIMEOUT: Duration = Duration::from_secs(300);
/// A stop waits this long for the service to report stopped (the wrapper itself kills after 60 s).
const STOP_TIMEOUT: Duration = Duration::from_secs(120);
const POLL: Duration = Duration::from_secs(1);
/// Brief §3: the update timer is 5 minutes unless chosen; an hour at most (the hub's limit).
pub const DEFAULT_TIMER_SECONDS: i32 = 300;
pub const MAX_TIMER_SECONDS: i32 = 3600;

struct Running {
    job: i64,
    cancel: CancellationToken,
}

pub struct Controller {
    pub topology: Topology,
    pub units: Arc<dyn Units>,
    pub probe: Arc<dyn Probe>,
    pub hub: Arc<dyn HubApi>,
    pub jobs: Arc<dyn JobStore>,
    pub alerts: Arc<dyn Alerts>,
    pub clock: Arc<dyn Clock>,
    running: Mutex<Option<Running>>,
    /// Units the panel stopped on purpose: their going down is not a crash.
    expected_down: Mutex<HashSet<String>>,
    /// Units stopped after a crash loop; they stay down until an owner starts them.
    failed: Mutex<HashSet<String>>,
    was_up: Mutex<HashMap<String, bool>>,
    crashes: Mutex<HashMap<String, VecDeque<OffsetDateTime>>>,
}

fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
}

impl Controller {
    pub fn new(
        topology: Topology,
        units: Arc<dyn Units>,
        probe: Arc<dyn Probe>,
        hub: Arc<dyn HubApi>,
        jobs: Arc<dyn JobStore>,
        alerts: Arc<dyn Alerts>,
        clock: Arc<dyn Clock>,
    ) -> Self {
        Self {
            topology,
            units,
            probe,
            hub,
            jobs,
            alerts,
            clock,
            running: Mutex::new(None),
            expected_down: Mutex::new(HashSet::new()),
            failed: Mutex::new(HashSet::new()),
            was_up: Mutex::new(HashMap::new()),
            crashes: Mutex::new(HashMap::new()),
        }
    }

    /// The running control job, if any.
    pub fn running_job(&self) -> Option<i64> {
        lock(&self.running).as_ref().map(|r| r.job)
    }

    pub(crate) async fn begin(&self, kind: &str, actor_name: &str) -> Result<(i64, CancellationToken), ControlError> {
        if let Some(r) = lock(&self.running).as_ref() {
            return Err(ControlError::Busy(r.job));
        }
        let job = self.jobs.create(kind, actor_name).await?;
        let cancel = CancellationToken::new();
        let mut running = lock(&self.running);
        if let Some(r) = running.as_ref() {
            return Err(ControlError::Busy(r.job));
        }
        *running = Some(Running {
            job,
            cancel: cancel.clone(),
        });
        Ok((job, cancel))
    }

    pub(crate) async fn end(&self, job: i64, state: JobState) {
        let _ = self.jobs.finish(job, state).await;
        let mut running = lock(&self.running);
        if running.as_ref().is_some_and(|r| r.job == job) {
            *running = None;
        }
    }

    pub(crate) async fn log(&self, job: i64, line: &str) {
        tracing::info!(job, "{line}");
        let _ = self.jobs.append(job, line).await;
    }

    fn worlds_in(&self, scope: Scope) -> Vec<WorldUnit> {
        self.topology
            .worlds
            .iter()
            .filter(|w| scope == Scope::All || Scope::World(w.world_id) == scope)
            .cloned()
            .collect()
    }

    // ---------------------------------------------------------------- views

    pub async fn view(&self, unit: &str, healthy: impl Future<Output = bool>) -> UnitView {
        if lock(&self.failed).contains(unit) {
            return UnitView::Failed;
        }
        match self.units.state(unit).await {
            ServiceState::Missing => UnitView::Missing,
            ServiceState::Stopped => UnitView::Stopped,
            ServiceState::StopPending => UnitView::Stopping,
            ServiceState::StartPending => UnitView::Starting,
            ServiceState::Running => {
                if healthy.await {
                    UnitView::Running
                } else {
                    UnitView::Starting
                }
            }
        }
    }

    /// Every unit, in start order, with its live state.
    pub async fn views(&self) -> Vec<(String, String, UnitView)> {
        let t = &self.topology;
        let mut out = vec![
            (
                "Database".to_owned(),
                t.database.clone(),
                self.view(&t.database, self.probe.database_up()).await,
            ),
            (
                "Hub".to_owned(),
                t.hub.clone(),
                self.view(&t.hub, self.probe.hub_up()).await,
            ),
        ];
        for w in &t.worlds {
            out.push((
                format!("World {}", w.world_id),
                w.unit.clone(),
                self.view(&w.unit, self.probe.world_up(w)).await,
            ));
        }
        out
    }

    // ---------------------------------------------------------------- start

    async fn wait_until<F: Future<Output = bool>>(
        &self,
        job: i64,
        what: &str,
        timeout: Duration,
        mut check: impl FnMut() -> F,
    ) -> bool {
        let deadline = tokio::time::Instant::now() + timeout;
        while tokio::time::Instant::now() < deadline {
            if check().await {
                self.log(job, &format!("{what} is up")).await;
                return true;
            }
            tokio::time::sleep(POLL).await;
        }
        self.log(job, &format!("{what} did not come up within {} s", timeout.as_secs()))
            .await;
        false
    }

    async fn start_unit(&self, job: i64, unit: &str) -> bool {
        lock(&self.failed).remove(unit);
        lock(&self.expected_down).remove(unit);
        lock(&self.was_up).remove(unit);
        match self.units.start(unit).await {
            Ok(()) => {
                self.log(job, &format!("started {unit}")).await;
                true
            }
            Err(e) => {
                self.log(job, &format!("could not start {unit}: {e}")).await;
                false
            }
        }
    }

    /// Brief §3 Start: database check, then the hub, then the worlds; each must be healthy before the next.
    pub(crate) async fn start_sequence(&self, job: i64, worlds: &[WorldUnit], with_core: bool) -> bool {
        let t = self.topology.clone();
        if with_core {
            if self.units.state(&t.database).await != ServiceState::Running && !self.start_unit(job, &t.database).await
            {
                return false;
            }
            if !self
                .wait_until(job, "the database", DATABASE_TIMEOUT, || self.probe.database_up())
                .await
            {
                return false;
            }
            if self.units.state(&t.hub).await != ServiceState::Running && !self.start_unit(job, &t.hub).await {
                return false;
            }
            if !self
                .wait_until(job, "the hub", HUB_TIMEOUT, || self.probe.hub_up())
                .await
            {
                return false;
            }
        }
        for w in worlds {
            if self.units.state(&w.unit).await != ServiceState::Running && !self.start_unit(job, &w.unit).await {
                return false;
            }
            if !self
                .wait_until(job, &format!("world {}", w.world_id), WORLD_TIMEOUT, || {
                    self.probe.world_up(w)
                })
                .await
            {
                return false;
            }
        }
        true
    }

    /// Starts everything (or one world) in order. Returns the job id; the work continues in the background.
    pub async fn start(self: &Arc<Self>, scope: Scope, actor_name: &str) -> Result<i64, ControlError> {
        let (job, _) = self.begin("start", actor_name).await?;
        let this = Arc::clone(self);
        tokio::spawn(async move {
            let worlds = this.worlds_in(scope);
            let ok = this.start_sequence(job, &worlds, scope == Scope::All).await;
            this.end(job, if ok { JobState::Succeeded } else { JobState::Failed })
                .await;
        });
        Ok(job)
    }

    // ---------------------------------------------------------------- stop / restart

    pub(crate) async fn stop_unit(&self, job: i64, unit: &str) -> bool {
        lock(&self.expected_down).insert(unit.to_owned());
        if let Err(e) = self.units.stop(unit).await {
            self.log(job, &format!("could not stop {unit}: {e}")).await;
            return false;
        }
        self.log(job, &format!("stopping {unit} (it saves everyone first)"))
            .await;
        let deadline = tokio::time::Instant::now() + STOP_TIMEOUT;
        while tokio::time::Instant::now() < deadline {
            if matches!(
                self.units.state(unit).await,
                ServiceState::Stopped | ServiceState::Missing
            ) {
                self.log(job, &format!("{unit} stopped")).await;
                return true;
            }
            tokio::time::sleep(POLL).await;
        }
        self.log(
            job,
            &format!("{unit} is still stopping after {} s", STOP_TIMEOUT.as_secs()),
        )
        .await;
        false
    }

    /// Brief §3 Stop / Restart: lock logins, run the in-game update timer (cancellable), then stop the worlds (the
    /// wrapper's Ctrl+C makes each save everyone) and, for everything, the hub. A restart then starts them again in
    /// order and unlocks logins once healthy.
    pub async fn stop(
        self: &Arc<Self>,
        scope: Scope,
        seconds: i32,
        message: String,
        restart: bool,
        actor: Actor,
        actor_name: &str,
    ) -> Result<i64, ControlError> {
        if !(1..=MAX_TIMER_SECONDS).contains(&seconds) {
            return Err(ControlError::Invalid(format!(
                "The timer must be 1 to {MAX_TIMER_SECONDS} seconds."
            )));
        }
        let (job, cancel) = self.begin(if restart { "restart" } else { "stop" }, actor_name).await?;
        let this = Arc::clone(self);
        tokio::spawn(async move {
            let state = this
                .stop_sequence(job, &cancel, scope, seconds, &message, restart, &actor)
                .await;
            this.end(job, state).await;
        });
        Ok(job)
    }

    #[allow(clippy::too_many_arguments)]
    async fn stop_sequence(
        &self,
        job: i64,
        cancel: &CancellationToken,
        scope: Scope,
        seconds: i32,
        message: &str,
        restart: bool,
        actor: &Actor,
    ) -> JobState {
        let lock_world = match scope {
            Scope::All => 0,
            Scope::World(id) => id,
        };
        if !self.countdown(job, cancel, lock_world, seconds, message, actor).await {
            return JobState::Cancelled;
        }
        let mut ok = true;
        for w in self.worlds_in(scope).iter().rev() {
            ok &= self.stop_unit(job, &w.unit).await;
        }
        if scope == Scope::All {
            ok &= self.stop_unit(job, &self.topology.hub).await;
        }
        if !ok {
            return JobState::Failed;
        }
        if !restart {
            self.log(job, "stopped").await;
            return JobState::Succeeded;
        }
        let worlds = self.worlds_in(scope);
        if !self.start_sequence(job, &worlds, scope == Scope::All).await {
            return JobState::Failed;
        }
        // A restarted hub forgets its locks; a single world's restart needs its lock lifted.
        if let Err(e) = self.hub.set_login_lock(actor, lock_world, false, "").await {
            self.log(job, &format!("could not unlock logins: {e}")).await;
        } else {
            self.log(job, "logins unlocked").await;
        }
        JobState::Succeeded
    }

    /// Brief §3: lock logins on `lock_world` (0 = every world), start the in-game update timer and wait it out.
    /// False when cancelled (the timer is cleared and logins unlocked). Without the hub it goes on with no timer.
    pub(crate) async fn countdown(
        &self,
        job: i64,
        cancel: &CancellationToken,
        lock_world: i32,
        seconds: i32,
        message: &str,
        actor: &Actor,
    ) -> bool {
        let hub_reachable = match self.hub.set_login_lock(actor, lock_world, true, "Restarting").await {
            Ok(()) => {
                self.log(job, "logins locked (staff only)").await;
                true
            }
            Err(e) => {
                self.log(
                    job,
                    &format!("could not lock logins ({e}); stopping without the in-game timer"),
                )
                .await;
                false
            }
        };
        if hub_reachable {
            match self.hub.system_update(actor, seconds, message).await {
                Ok(()) => self.log(job, &format!("update timer started: {seconds} s")).await,
                Err(e) => self.log(job, &format!("could not start the update timer: {e}")).await,
            }
            let mut left = seconds;
            while left > 0 {
                tokio::select! {
                    () = cancel.cancelled() => {
                        let _ = self.hub.cancel_update(actor).await;
                        let _ = self.hub.set_login_lock(actor, lock_world, false, "").await;
                        self.log(job, "cancelled: the timer is cleared and logins are unlocked").await;
                        return false;
                    }
                    () = tokio::time::sleep(Duration::from_secs(1)) => {}
                }
                left -= 1;
                if left > 0 && (left % 60 == 0 || left == 30 || left == 10) {
                    self.log(job, &format!("{left} s left")).await;
                }
            }
        }
        true
    }

    /// Brief §3 Emergency stop: no countdown. The wrapper's Ctrl+C is the game's save-all-then-shut-down; after its 60 s
    /// it kills the process. The caller has checked the password and the typed confirmation.
    pub async fn emergency_stop(self: &Arc<Self>, actor_name: &str) -> Result<i64, ControlError> {
        if let Some(job) = self.running_job() {
            self.cancel_running().await;
            self.log(job, "superseded by an emergency stop").await;
        }
        let (job, _) = self.begin("emergency stop", actor_name).await?;
        self.alerts
            .alert(
                "Emergency stop",
                &format!("{actor_name} stopped every world and the hub without a countdown."),
            )
            .await;
        let this = Arc::clone(self);
        tokio::spawn(async move {
            let mut ok = true;
            for w in this.topology.worlds.clone().iter().rev() {
                ok &= this.stop_unit(job, &w.unit).await;
            }
            let hub = this.topology.hub.clone();
            ok &= this.stop_unit(job, &hub).await;
            this.end(job, if ok { JobState::Succeeded } else { JobState::Failed })
                .await;
        });
        Ok(job)
    }

    async fn cancel_running(&self) {
        let token = lock(&self.running).as_ref().map(|r| r.cancel.clone());
        if let Some(t) = token {
            t.cancel();
        }
        // Let the job see the cancellation and end.
        for _ in 0..50 {
            if lock(&self.running).is_none() {
                return;
            }
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        *lock(&self.running) = None;
    }

    /// Brief §3: a stop can be cancelled while its timer runs.
    pub async fn cancel(&self) -> bool {
        if self.running_job().is_none() {
            return false;
        }
        self.cancel_running().await;
        true
    }

    /// Brief §3 Maintenance mode: logins on every world locked to staff, the message shown in the world list.
    pub async fn maintenance(&self, actor: &Actor, on: bool, message: &str) -> anyhow::Result<()> {
        self.hub.set_login_lock(actor, 0, on, message).await?;
        Ok(())
    }

    // ---------------------------------------------------------------- crash loops

    /// One pass of the crash watcher (every few seconds): a unit that was healthy and stops being so without the
    /// panel stopping it has crashed; [`CRASH_LOOP_COUNT`] crashes within [`CRASH_LOOP_WINDOW`] stop its retries, alert
    /// owners and record the last log lines.
    pub async fn watch_once(&self) {
        if self.running_job().is_some() {
            return;
        }
        let mut units: Vec<(String, bool)> = vec![(self.topology.hub.clone(), self.probe.hub_up().await)];
        for w in &self.topology.worlds {
            units.push((w.unit.clone(), self.probe.world_up(w).await));
        }
        let now = self.clock.now();
        for (unit, up) in units {
            if lock(&self.expected_down).contains(&unit) || lock(&self.failed).contains(&unit) {
                continue;
            }
            let was_up = lock(&self.was_up).insert(unit.clone(), up).unwrap_or(false);
            if !was_up || up {
                continue;
            }
            let count = {
                let mut crashes = lock(&self.crashes);
                let q = crashes.entry(unit.clone()).or_default();
                q.push_back(now);
                while q.front().is_some_and(|t| now - *t > CRASH_LOOP_WINDOW) {
                    q.pop_front();
                }
                q.len()
            };
            tracing::warn!(unit, count, "crash detected");
            // Brief §7: a world or the hub is down (the service wrapper restarts it; a loop stops that).
            if count < CRASH_LOOP_COUNT {
                self.alerts
                    .alert(
                        &format!("{unit} went down"),
                        &format!("{unit} stopped unexpectedly; it restarts by itself."),
                    )
                    .await;
            }
            if count >= CRASH_LOOP_COUNT {
                self.crash_loop(&unit).await;
            }
        }
    }

    async fn crash_loop(&self, unit: &str) {
        lock(&self.failed).insert(unit.to_owned());
        lock(&self.expected_down).insert(unit.to_owned());
        lock(&self.crashes).remove(unit);
        let stopped = self.units.stop(unit).await;
        let tail = self.log_tail(unit, CRASH_LOG_LINES);
        if let Ok(job) = self.jobs.create("crash loop", "panel").await {
            let _ = self
                .jobs
                .append(
                    job,
                    &format!("{unit} crashed {CRASH_LOOP_COUNT} times in 10 minutes; retries stopped"),
                )
                .await;
            if let Err(e) = &stopped {
                let _ = self.jobs.append(job, &format!("could not stop it: {e}")).await;
            }
            let _ = self
                .jobs
                .append(job, &format!("last {CRASH_LOG_LINES} log lines:\n{tail}"))
                .await;
            let _ = self.jobs.finish(job, JobState::Failed).await;
        }
        self.alerts
            .alert(
                &format!("Crash loop: {unit}"),
                &format!("{unit} crashed {CRASH_LOOP_COUNT} times in 10 minutes and was stopped."),
            )
            .await;
    }

    /// The last [n] lines of a unit's current log file.
    pub fn log_tail(&self, unit: &str, n: usize) -> String {
        let path = self.topology.logs.join(unit).join(format!("{unit}_rCURRENT.log"));
        std::fs::read_to_string(&path).map_or_else(
            |e| format!("(no log at {}: {e})", path.display()),
            |text| {
                let lines: Vec<&str> = text.lines().collect();
                lines[lines.len().saturating_sub(n)..].join("\n")
            },
        )
    }
}
