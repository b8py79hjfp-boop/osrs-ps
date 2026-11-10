//! Alert delivery and the metric-based alerts (brief §7).
//!
//! Every alert is a row in `panel.alerts` (the dashboard shows the latest). The sender passes over the unsent rows
//! every [`SEND_EVERY`] and delivers each to a Discord webhook and/or by email, whichever the Settings page
//! configures; with neither configured the rows are only marked sent. The watcher raises the alerts that come from
//! metrics: tick p99 over budget for [`OVER_BUDGET_FOR`], and the disk over [`DISK_ALERT`]. A world or the hub going
//! down, a failed backup, repeated panel sign-in failures and a failed deploy raise theirs where they happen.

use std::collections::HashMap;
use std::sync::Mutex;
use std::time::Duration;

use serde::{Deserialize, Serialize};
use time::OffsetDateTime;

use crate::jobs::{Alerts, Settings};

/// Settings key for [`AlertTargets`].
pub const SETTINGS_KEY: &str = "alerts";
pub const SEND_EVERY: Duration = Duration::from_secs(30);
/// Brief §7: tick p99 over budget for 5 minutes.
pub const OVER_BUDGET_FOR: time::Duration = time::Duration::minutes(5);
/// Brief §7: disk over 85 %.
pub const DISK_ALERT: f64 = 0.85;
/// The disk alert clears below this, so a disk hovering at 85 % alerts once.
const DISK_CLEAR: f64 = 0.80;
/// §7 tick budget (brief): logic p99 ≤ 120 ms (server/Ops.kt).
const LOGIC_BUDGET_MS: f64 = 120.0;
/// Discord rejects messages over 2000 characters.
const DISCORD_MAX: usize = 1900;
/// At most this many alerts go out per pass (a storm drains over a few passes).
const BATCH: i64 = 20;

/// Where alerts go (Settings page, developer).
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct AlertTargets {
    /// `https://discord.com/api/webhooks/...`; empty = off.
    #[serde(default)]
    pub discord_webhook: String,
    #[serde(default)]
    pub email: Option<Email>,
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
pub struct Email {
    /// SMTP server; port 465 is implicit TLS, anything else STARTTLS.
    pub host: String,
    pub port: u16,
    pub username: String,
    pub password: String,
    pub from: String,
    /// Comma-separated.
    pub to: String,
}

pub async fn targets(settings: &dyn Settings) -> AlertTargets {
    settings
        .get(SETTINGS_KEY)
        .await
        .ok()
        .flatten()
        .and_then(|v| serde_json::from_value(v).ok())
        .unwrap_or_default()
}

/// A Discord webhook URL (and nothing else: the panel posts to it).
pub fn valid_webhook(url: &str) -> bool {
    url.is_empty()
        || [
            "https://discord.com/api/webhooks/",
            "https://discordapp.com/api/webhooks/",
        ]
        .iter()
        .any(|p| url.starts_with(p) && url.len() > p.len())
}

pub async fn send_discord(http: &reqwest::Client, webhook: &str, title: &str, body: &str) -> anyhow::Result<()> {
    let mut text = format!("**Lotus: {title}**\n{body}");
    if text.chars().count() > DISCORD_MAX {
        text = text.chars().take(DISCORD_MAX).collect::<String>() + "…";
    }
    // No pings from alert text, whatever it contains.
    let payload = serde_json::json!({ "content": text, "allowed_mentions": { "parse": [] } });
    http.post(webhook)
        .json_body(&payload)?
        .timeout(Duration::from_secs(10))
        .send()
        .await?
        .error_for_status()?;
    Ok(())
}

/// reqwest is built without its `json` feature; this sets the body and header by hand.
trait JsonBody: Sized {
    fn json_body(self, v: &serde_json::Value) -> anyhow::Result<Self>;
}

impl JsonBody for reqwest::RequestBuilder {
    fn json_body(self, v: &serde_json::Value) -> anyhow::Result<Self> {
        Ok(self
            .header("content-type", "application/json")
            .body(serde_json::to_vec(v)?))
    }
}

pub async fn send_email(e: &Email, title: &str, body: &str) -> anyhow::Result<()> {
    use lettre::message::Mailbox;
    use lettre::transport::smtp::authentication::Credentials;
    use lettre::{AsyncSmtpTransport, AsyncTransport, Message, Tokio1Executor};
    let mut msg = Message::builder()
        .from(e.from.parse::<Mailbox>()?)
        .subject(format!("Lotus: {title}"));
    for to in e.to.split(',').map(str::trim).filter(|t| !t.is_empty()) {
        msg = msg.to(to.parse::<Mailbox>()?);
    }
    let msg = msg.body(body.to_owned())?;
    let builder = if e.port == 465 {
        AsyncSmtpTransport::<Tokio1Executor>::relay(&e.host)?
    } else {
        AsyncSmtpTransport::<Tokio1Executor>::starttls_relay(&e.host)?
    };
    let mailer = builder
        .port(e.port)
        .credentials(Credentials::new(e.username.clone(), e.password.clone()))
        .timeout(Some(Duration::from_secs(20)))
        .build();
    mailer.send(msg).await?;
    Ok(())
}

/// One pass of the sender over `panel.alerts`.
pub async fn send_pending(pool: &sqlx::PgPool, settings: &dyn Settings, http: &reqwest::Client) -> anyhow::Result<()> {
    let rows = sqlx::query!(
        "SELECT id, title, body FROM panel.alerts WHERE NOT sent ORDER BY id LIMIT $1",
        BATCH
    )
    .fetch_all(pool)
    .await?;
    if rows.is_empty() {
        return Ok(());
    }
    let t = targets(settings).await;
    for r in rows {
        let mut delivered = t.discord_webhook.is_empty() && t.email.is_none();
        if !t.discord_webhook.is_empty() {
            match send_discord(http, &t.discord_webhook, &r.title, &r.body).await {
                Ok(()) => delivered = true,
                Err(e) => tracing::warn!(error = %e, "Discord alert failed"),
            }
        }
        if let Some(email) = &t.email {
            match send_email(email, &r.title, &r.body).await {
                Ok(()) => delivered = true,
                Err(e) => tracing::warn!(error = %e, "email alert failed"),
            }
        }
        // Undelivered rows wait for the next pass (a channel may be down for a moment).
        if delivered {
            sqlx::query!("UPDATE panel.alerts SET sent = true WHERE id = $1", r.id)
                .execute(pool)
                .await?;
        } else {
            break;
        }
    }
    Ok(())
}

/// Metric alerts, kept between scrapes: when each world went over budget, and whether the disk alert is raised.
#[derive(Default)]
pub struct Watcher {
    over_since: Mutex<HashMap<i32, OffsetDateTime>>,
    alerted: Mutex<HashMap<i32, bool>>,
    disk_alerted: Mutex<bool>,
}

fn lock<T>(m: &Mutex<T>) -> std::sync::MutexGuard<'_, T> {
    m.lock().unwrap_or_else(std::sync::PoisonError::into_inner)
}

impl Watcher {
    /// Looks at one scrape's samples (`world ids` are the configured worlds).
    pub async fn check(&self, samples: &[(String, f64)], worlds: &[i32], now: OffsetDateTime, alerts: &dyn Alerts) {
        let get = |k: &str| samples.iter().find(|(s, _)| s == k).map(|(_, v)| *v);
        let mut raise = Vec::new();
        for w in worlds {
            let key = format!("tp_tick_ms{{world=\"{w}\",phase=\"logic\",quantile=\"0.99\"}}");
            let over = get(&key).is_some_and(|v| v > LOGIC_BUDGET_MS);
            let mut since = lock(&self.over_since);
            let mut alerted = lock(&self.alerted);
            if over {
                let start = *since.entry(*w).or_insert(now);
                if now - start >= OVER_BUDGET_FOR && !alerted.get(w).copied().unwrap_or(false) {
                    alerted.insert(*w, true);
                    raise.push((
                        format!("World {w} is slow"),
                        format!("Tick p99 has been over {LOGIC_BUDGET_MS} ms for 5 minutes."),
                    ));
                }
            } else {
                since.remove(w);
                alerted.remove(w);
            }
        }
        if let (Some(used), Some(total)) = (get("vps_disk_used_bytes"), get("vps_disk_total_bytes"))
            && total > 0.0
        {
            let share = used / total;
            let mut d = lock(&self.disk_alerted);
            if share > DISK_ALERT && !*d {
                *d = true;
                raise.push((
                    "Disk almost full".to_owned(),
                    format!("The VPS disk is {:.0} % full.", share * 100.0),
                ));
            } else if share < DISK_CLEAR {
                *d = false;
            }
        }
        for (title, body) in raise {
            alerts.alert(&title, &body).await;
        }
    }
}
