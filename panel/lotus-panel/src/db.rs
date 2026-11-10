//! The panel's own PostgreSQL schema (`panel`; brief §1). The panel never writes the game's tables: game data is read,
//! and changed, only through the hub.

use sqlx::postgres::{PgConnectOptions, PgPoolOptions};
use sqlx::{ConnectOptions, PgPool};

/// Small: one VPS, a handful of staff (our choice).
const MAX_CONNECTIONS: u32 = 8;

pub async fn connect(url: &str) -> anyhow::Result<PgPool> {
    let options: PgConnectOptions = url.parse()?;
    let options = options.log_statements(tracing::log::LevelFilter::Debug);
    let pool = PgPoolOptions::new()
        .max_connections(MAX_CONNECTIONS)
        .acquire_timeout(std::time::Duration::from_secs(5))
        .connect_with(options)
        .await?;
    sqlx::migrate!("./migrations").run(&pool).await?;
    Ok(pool)
}

pub async fn healthy(pool: &PgPool) -> bool {
    sqlx::query_scalar!("SELECT 1 AS \"one!\"")
        .fetch_one(pool)
        .await
        .is_ok()
}
