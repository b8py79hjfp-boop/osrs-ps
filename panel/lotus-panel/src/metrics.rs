//! Metrics (brief §7): every 15 s the panel scrapes each world node's `/metrics` (players, tick time per phase, missed
//! deadlines, instances, GC, uptime, build) and the VPS itself (CPU, memory, disk, network), and stores the samples in
//! its own schema. After 24 hours they are averaged into 1-minute rows, which are kept 30 days. Charts are drawn as
//! inline SVG on the server (no scripts, so the strict CSP holds).

use std::collections::HashMap;
use std::fmt::Write as _;
use std::sync::Mutex;
use std::time::Duration;

use async_trait::async_trait;
use time::OffsetDateTime;

/// Brief §7: scrape every 15 s.
pub const SCRAPE_EVERY: Duration = Duration::from_secs(15);
/// Downsampling and pruning run hourly.
pub const PRUNE_EVERY: Duration = Duration::from_secs(3600);

#[async_trait]
pub trait MetricsStore: Send + Sync {
    async fn insert(&self, at: OffsetDateTime, samples: &[(String, f64)]) -> anyhow::Result<()>;
    /// The newest value of every series seen in the last few minutes.
    async fn latest(&self) -> anyhow::Result<HashMap<String, f64>>;
    /// `series` averaged into buckets of `bucket_secs` since `since`, oldest first.
    async fn range(&self, series: &str, since: OffsetDateTime, bucket_secs: i64) -> anyhow::Result<Vec<(i64, f64)>>;
    /// Brief §7 retention: 15 s rows older than 24 h become 1-minute rows; anything older than 30 days goes.
    async fn prune(&self) -> anyhow::Result<()>;
}

pub struct PgMetrics(pub sqlx::PgPool);

#[async_trait]
impl MetricsStore for PgMetrics {
    async fn insert(&self, at: OffsetDateTime, samples: &[(String, f64)]) -> anyhow::Result<()> {
        let series: Vec<String> = samples.iter().map(|s| s.0.clone()).collect();
        let values: Vec<f64> = samples.iter().map(|s| s.1).collect();
        sqlx::query!(
            "INSERT INTO panel.metrics (ts, series, value) SELECT $1, s, v FROM unnest($2::text[], $3::float8[]) AS t(s, v)",
            at,
            &series,
            &values
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }

    async fn latest(&self) -> anyhow::Result<HashMap<String, f64>> {
        let rows = sqlx::query!(
            "SELECT DISTINCT ON (series) series, value FROM panel.metrics WHERE ts > now() - interval '2 minutes' \
             ORDER BY series, ts DESC"
        )
        .fetch_all(&self.0)
        .await?;
        Ok(rows.into_iter().map(|r| (r.series, r.value)).collect())
    }

    async fn range(&self, series: &str, since: OffsetDateTime, bucket_secs: i64) -> anyhow::Result<Vec<(i64, f64)>> {
        let rows = sqlx::query!(
            "SELECT (floor(extract(epoch FROM ts)::float8 / $3::float8) * $3::float8)::bigint AS \"bucket!\", avg(value) AS \"value!\" \
             FROM panel.metrics WHERE series = $1 AND ts > $2 GROUP BY 1 ORDER BY 1",
            series,
            since,
            f64::from(i32::try_from(bucket_secs).unwrap_or(i32::MAX))
        )
        .fetch_all(&self.0)
        .await?;
        Ok(rows.into_iter().map(|r| (r.bucket, r.value)).collect())
    }

    async fn prune(&self) -> anyhow::Result<()> {
        let mut tx = self.0.begin().await?;
        sqlx::query!(
            "INSERT INTO panel.metrics (ts, series, value, minute) SELECT date_trunc('minute', ts), series, avg(value), true \
             FROM panel.metrics WHERE NOT minute AND ts < now() - interval '24 hours' GROUP BY 1, 2"
        )
        .execute(&mut *tx)
        .await?;
        sqlx::query!("DELETE FROM panel.metrics WHERE NOT minute AND ts < now() - interval '24 hours'")
            .execute(&mut *tx)
            .await?;
        sqlx::query!("DELETE FROM panel.metrics WHERE ts < now() - interval '30 days'")
            .execute(&mut *tx)
            .await?;
        sqlx::query!("DELETE FROM panel.alerts WHERE at < now() - interval '30 days'")
            .execute(&mut *tx)
            .await?;
        tx.commit().await?;
        Ok(())
    }
}

/// In memory (tests): keeps everything.
#[derive(Default)]
pub struct MemoryMetrics(Mutex<Vec<(OffsetDateTime, String, f64)>>);

#[async_trait]
impl MetricsStore for MemoryMetrics {
    async fn insert(&self, at: OffsetDateTime, samples: &[(String, f64)]) -> anyhow::Result<()> {
        let mut all = self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        all.extend(samples.iter().map(|(s, v)| (at, s.clone(), *v)));
        Ok(())
    }

    async fn latest(&self) -> anyhow::Result<HashMap<String, f64>> {
        let all = self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        Ok(all.iter().map(|(_, s, v)| (s.clone(), *v)).collect())
    }

    async fn range(&self, series: &str, since: OffsetDateTime, bucket_secs: i64) -> anyhow::Result<Vec<(i64, f64)>> {
        let all = self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        let mut buckets: std::collections::BTreeMap<i64, (f64, f64)> = std::collections::BTreeMap::new();
        for (t, _, v) in all.iter().filter(|(t, s, _)| s == series && *t > since) {
            let b = t.unix_timestamp() / bucket_secs * bucket_secs;
            let e = buckets.entry(b).or_insert((0.0, 0.0));
            e.0 += v;
            e.1 += 1.0;
        }
        Ok(buckets.into_iter().map(|(b, (sum, n))| (b, sum / n)).collect())
    }

    async fn prune(&self) -> anyhow::Result<()> {
        Ok(())
    }
}

/// `name{labels} value` lines of the Prometheus text format (comments and malformed lines skipped).
pub fn parse_prometheus(text: &str) -> Vec<(String, f64)> {
    text.lines()
        .filter(|l| !l.starts_with('#') && !l.trim().is_empty())
        .filter_map(|l| {
            let (key, value) = l.rsplit_once(' ')?;
            Some((key.trim().to_owned(), value.trim().parse::<f64>().ok()?))
        })
        .collect()
}

/// The scraper's memory between passes: the VPS counters and the GC total (stored as per-interval deltas).
pub struct Scraper {
    system: Option<sysinfo::System>,
    networks: Option<sysinfo::Networks>,
    gc_total: HashMap<String, f64>,
    http: reqwest::Client,
}

impl Default for Scraper {
    fn default() -> Self {
        Self {
            system: sysinfo::System::new().ok(),
            networks: sysinfo::Networks::new_with_refreshed_list().ok(),
            gc_total: HashMap::new(),
            http: crate::net::http_client(),
        }
    }
}

impl Scraper {
    /// One pass: every world node's metrics (by URL) plus the VPS.
    pub async fn sample(&mut self, metric_urls: &[(i32, String)]) -> Vec<(String, f64)> {
        let mut out = Vec::new();
        for (world, url) in metric_urls {
            let Ok(r) = self.http.get(url).timeout(Duration::from_secs(5)).send().await else {
                continue;
            };
            let Ok(text) = r.text().await else { continue };
            for (key, value) in parse_prometheus(&text) {
                // Node-wide series get the world of the node they came from.
                let key = if key.contains('{') {
                    key
                } else {
                    format!("{key}{{node=\"{world}\"}}")
                };
                if key.starts_with("tp_gc_ms_total") {
                    let prev = self.gc_total.insert(key.clone(), value);
                    if let Some(p) = prev {
                        out.push((key.replace("tp_gc_ms_total", "tp_gc_ms_interval"), (value - p).max(0.0)));
                    }
                }
                out.push((key, value));
            }
        }
        #[allow(clippy::cast_precision_loss)] // bytes as f64: exact well past any VPS size
        {
            if let Some(system) = self.system.as_mut() {
                system.refresh_cpu_usage();
                system.refresh_memory();
                out.push(("vps_cpu_percent".into(), f64::from(system.global_cpu_usage())));
                out.push(("vps_mem_used_bytes".into(), system.used_memory() as f64));
                out.push(("vps_mem_total_bytes".into(), system.total_memory() as f64));
            }
            if let Ok(disks) = sysinfo::Disks::new_with_refreshed_list() {
                let (total, avail) = disks
                    .iter()
                    .fold((0u64, 0u64), |(t, a), d| (t + d.total_space(), a + d.available_space()));
                out.push(("vps_disk_used_bytes".into(), total.saturating_sub(avail) as f64));
                out.push(("vps_disk_total_bytes".into(), total as f64));
            }
            if let Some(networks) = self.networks.as_mut() {
                networks.refresh(true);
                let (rx, tx) = networks
                    .iter()
                    .fold((0u64, 0u64), |(r, t), (_, n)| (r + n.received(), t + n.transmitted()));
                let secs = SCRAPE_EVERY.as_secs_f64();
                out.push(("vps_net_rx_bytes_per_s".into(), rx as f64 / secs));
                out.push(("vps_net_tx_bytes_per_s".into(), tx as f64 / secs));
            }
        }
        out
    }
}

/// The node's metrics URL from its health URL (`…/health` → `…/metrics`).
pub fn metrics_url(health_url: &str) -> String {
    health_url
        .strip_suffix("/health")
        .map_or_else(|| health_url.to_owned(), |base| format!("{base}/metrics"))
}

/// A line chart as inline SVG (no script, no inline style: presentation attributes only).
pub fn svg_chart(points: &[(i64, f64)], width: u32, height: u32, budget: Option<f64>) -> String {
    let mut svg =
        format!("<svg viewBox=\"0 0 {width} {height}\" class=\"chart\" role=\"img\" preserveAspectRatio=\"none\">");
    if points.is_empty() {
        svg.push_str("</svg>");
        return svg;
    }
    let (t0, t1) = (points[0].0, points[points.len() - 1].0.max(points[0].0 + 1));
    let max = points
        .iter()
        .map(|p| p.1)
        .fold(budget.unwrap_or(0.0), f64::max)
        .max(1e-9)
        * 1.1;
    #[allow(clippy::cast_precision_loss)]
    let x = |t: i64| (t - t0) as f64 / (t1 - t0) as f64 * f64::from(width);
    let y = |v: f64| f64::from(height) - v / max * f64::from(height);
    if let Some(b) = budget {
        let _ = write!(
            svg,
            "<line x1=\"0\" x2=\"{width}\" y1=\"{:.1}\" y2=\"{:.1}\" class=\"budget\"/>",
            y(b),
            y(b)
        );
    }
    svg.push_str("<polyline fill=\"none\" class=\"line\" points=\"");
    for (t, v) in points {
        let _ = write!(svg, "{:.1},{:.1} ", x(*t), y(*v));
    }
    svg.push_str("\"/></svg>");
    svg
}
