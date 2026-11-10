//! Job rows (brief §6: every control, deploy and backup step is a job with its log), panel settings (key/value JSON),
//! and the alert sink (phase 8 adds Discord and e-mail; until then alerts go to the log, marked `alert=true`).

use std::collections::HashMap;
use std::sync::Mutex;

use async_trait::async_trait;
use time::OffsetDateTime;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum JobState {
    Running,
    Succeeded,
    Failed,
    Cancelled,
}

impl JobState {
    pub const fn stored(self) -> &'static str {
        match self {
            Self::Running => "running",
            Self::Succeeded => "succeeded",
            Self::Failed => "failed",
            Self::Cancelled => "cancelled",
        }
    }

    pub fn parse(s: &str) -> Self {
        match s {
            "succeeded" => Self::Succeeded,
            "failed" => Self::Failed,
            "cancelled" => Self::Cancelled,
            _ => Self::Running,
        }
    }
}

#[derive(Debug, Clone)]
pub struct Job {
    pub id: i64,
    pub kind: String,
    pub actor: String,
    pub state: JobState,
    pub log: String,
    pub created_at: OffsetDateTime,
    pub finished_at: Option<OffsetDateTime>,
}

#[async_trait]
pub trait JobStore: Send + Sync {
    async fn create(&self, kind: &str, actor: &str) -> anyhow::Result<i64>;
    async fn append(&self, id: i64, line: &str) -> anyhow::Result<()>;
    async fn finish(&self, id: i64, state: JobState) -> anyhow::Result<()>;
    async fn get(&self, id: i64) -> anyhow::Result<Option<Job>>;
    async fn recent(&self, limit: i64) -> anyhow::Result<Vec<Job>>;
}

#[async_trait]
pub trait Settings: Send + Sync {
    async fn get(&self, key: &str) -> anyhow::Result<Option<serde_json::Value>>;
    async fn set(&self, key: &str, value: &serde_json::Value, by: &str) -> anyhow::Result<()>;
}

#[async_trait]
pub trait Alerts: Send + Sync {
    async fn alert(&self, title: &str, body: &str);
    /// The newest [n] alerts: (time, title, body).
    async fn recent(&self, _n: i64) -> Vec<(OffsetDateTime, String, String)> {
        Vec::new()
    }
}

/// Alerts stored in `panel.alerts` (dashboard) and logged; phase 8 sends the unsent ones out.
pub struct DbAlerts(pub sqlx::PgPool);

#[async_trait]
impl Alerts for DbAlerts {
    async fn alert(&self, title: &str, body: &str) {
        tracing::warn!(alert = true, title, body, "alert");
        if let Err(e) = sqlx::query!("INSERT INTO panel.alerts (title, body) VALUES ($1, $2)", title, body)
            .execute(&self.0)
            .await
        {
            tracing::error!(error = %e, "could not store an alert");
        }
    }

    async fn recent(&self, n: i64) -> Vec<(OffsetDateTime, String, String)> {
        sqlx::query!("SELECT at, title, body FROM panel.alerts ORDER BY at DESC LIMIT $1", n)
            .fetch_all(&self.0)
            .await
            .map(|rows| rows.into_iter().map(|r| (r.at, r.title, r.body)).collect())
            .unwrap_or_default()
    }
}

/// Alerts into the log, for the phase 8 channels to pick up.
pub struct LogAlerts;

#[async_trait]
impl Alerts for LogAlerts {
    async fn alert(&self, title: &str, body: &str) {
        tracing::warn!(alert = true, title, body, "alert");
    }
}

pub struct PgJobs(pub sqlx::PgPool);

#[async_trait]
impl JobStore for PgJobs {
    async fn create(&self, kind: &str, actor: &str) -> anyhow::Result<i64> {
        Ok(sqlx::query_scalar!(
            "INSERT INTO panel.jobs (kind, actor, state) VALUES ($1, $2, 'running') RETURNING id",
            kind,
            actor
        )
        .fetch_one(&self.0)
        .await?)
    }

    async fn append(&self, id: i64, line: &str) -> anyhow::Result<()> {
        let stamped = format!("{} {line}\n", crate::web::clock_time(OffsetDateTime::now_utc()));
        sqlx::query!("UPDATE panel.jobs SET log = log || $2 WHERE id = $1", id, stamped)
            .execute(&self.0)
            .await?;
        Ok(())
    }

    async fn finish(&self, id: i64, state: JobState) -> anyhow::Result<()> {
        sqlx::query!(
            "UPDATE panel.jobs SET state = $2, finished_at = now() WHERE id = $1",
            id,
            state.stored()
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }

    async fn get(&self, id: i64) -> anyhow::Result<Option<Job>> {
        let row = sqlx::query!(
            "SELECT id, kind, actor, state, log, created_at, finished_at FROM panel.jobs WHERE id = $1",
            id
        )
        .fetch_optional(&self.0)
        .await?;
        Ok(row.map(|r| Job {
            id: r.id,
            kind: r.kind,
            actor: r.actor,
            state: JobState::parse(&r.state),
            log: r.log,
            created_at: r.created_at,
            finished_at: r.finished_at,
        }))
    }

    async fn recent(&self, limit: i64) -> anyhow::Result<Vec<Job>> {
        let rows = sqlx::query!(
            "SELECT id, kind, actor, state, log, created_at, finished_at FROM panel.jobs ORDER BY id DESC LIMIT $1",
            limit
        )
        .fetch_all(&self.0)
        .await?;
        Ok(rows
            .into_iter()
            .map(|r| Job {
                id: r.id,
                kind: r.kind,
                actor: r.actor,
                state: JobState::parse(&r.state),
                log: r.log,
                created_at: r.created_at,
                finished_at: r.finished_at,
            })
            .collect())
    }
}

pub struct PgSettings(pub sqlx::PgPool);

#[async_trait]
impl Settings for PgSettings {
    async fn get(&self, key: &str) -> anyhow::Result<Option<serde_json::Value>> {
        Ok(
            sqlx::query_scalar!("SELECT value FROM panel.settings WHERE key = $1", key)
                .fetch_optional(&self.0)
                .await?,
        )
    }

    async fn set(&self, key: &str, value: &serde_json::Value, by: &str) -> anyhow::Result<()> {
        sqlx::query!(
            "INSERT INTO panel.settings (key, value, updated_by) VALUES ($1, $2, $3) \
             ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_by = EXCLUDED.updated_by, updated_at = now()",
            key,
            value,
            by
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }
}

#[derive(Default)]
pub struct MemoryJobs(Mutex<Vec<Job>>);

impl MemoryJobs {
    fn with<T>(&self, f: impl FnOnce(&mut Vec<Job>) -> T) -> T {
        f(&mut self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner))
    }
}

#[async_trait]
impl JobStore for MemoryJobs {
    async fn create(&self, kind: &str, actor: &str) -> anyhow::Result<i64> {
        Ok(self.with(|all| {
            let id = i64::try_from(all.len()).unwrap_or(i64::MAX) + 1;
            all.push(Job {
                id,
                kind: kind.to_owned(),
                actor: actor.to_owned(),
                state: JobState::Running,
                log: String::new(),
                created_at: OffsetDateTime::now_utc(),
                finished_at: None,
            });
            id
        }))
    }

    async fn append(&self, id: i64, line: &str) -> anyhow::Result<()> {
        self.with(|all| {
            if let Some(j) = all.iter_mut().find(|j| j.id == id) {
                j.log.push_str(line);
                j.log.push('\n');
            }
        });
        Ok(())
    }

    async fn finish(&self, id: i64, state: JobState) -> anyhow::Result<()> {
        self.with(|all| {
            if let Some(j) = all.iter_mut().find(|j| j.id == id) {
                j.state = state;
                j.finished_at = Some(OffsetDateTime::now_utc());
            }
        });
        Ok(())
    }

    async fn get(&self, id: i64) -> anyhow::Result<Option<Job>> {
        Ok(self.with(|all| all.iter().find(|j| j.id == id).cloned()))
    }

    async fn recent(&self, limit: i64) -> anyhow::Result<Vec<Job>> {
        Ok(self.with(|all| {
            all.iter()
                .rev()
                .take(usize::try_from(limit).unwrap_or(0))
                .cloned()
                .collect()
        }))
    }
}

#[derive(Default)]
pub struct MemorySettings(Mutex<HashMap<String, serde_json::Value>>);

#[async_trait]
impl Settings for MemorySettings {
    async fn get(&self, key: &str) -> anyhow::Result<Option<serde_json::Value>> {
        Ok(self
            .0
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .get(key)
            .cloned())
    }

    async fn set(&self, key: &str, value: &serde_json::Value, _by: &str) -> anyhow::Result<()> {
        self.0
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .insert(key.to_owned(), value.clone());
        Ok(())
    }
}

/// Alerts collected in memory (tests).
#[derive(Default)]
pub struct MemoryAlerts(pub Mutex<Vec<String>>);

#[async_trait]
impl Alerts for MemoryAlerts {
    async fn alert(&self, title: &str, _body: &str) {
        self.0
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .push(title.to_owned());
    }
}
