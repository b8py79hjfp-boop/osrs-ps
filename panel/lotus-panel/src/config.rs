//! Typed panel config (panel brief §9; Windows paths per panel/docs/plan.md §1). Secrets are never in this file: each
//! `*_credential` key names a file in `[paths] secrets`, readable only by the panel's service account.

use std::net::SocketAddr;
use std::path::{Path, PathBuf};

use serde::Deserialize;

use crate::net::Cidr;

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Config {
    pub server: Server,
    pub hub: Hub,
    pub database: Database,
    #[serde(default)]
    pub sessions: Sessions,
    pub paths: Paths,
    #[serde(default)]
    pub control: Control,
    #[serde(default)]
    pub deploy: Deploy,
    #[serde(default)]
    pub backups: Backups,
}

/// Brief §6 / §9 `[deploy]`.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Deploy {
    /// One directory junction per service, pointing into `[control] releases`.
    #[serde(default = "Deploy::default_nodes")]
    pub nodes: PathBuf,
    /// Zips (and their `.minisig`) copied here by hand are offered on the Deploys page.
    #[serde(default = "Deploy::default_incoming")]
    pub incoming: PathBuf,
    /// The base64 line of the minisign public key releases are signed with; empty = deploys are off.
    #[serde(default)]
    pub public_key: String,
    /// GitHub `owner/repo` whose releases (`lotus-<version>`) the panel can fetch.
    #[serde(default = "Deploy::default_repo")]
    pub ci_repo: String,
    /// Brief §6: the in-game update timer, 5 minutes by default.
    #[serde(default = "Deploy::default_timer")]
    pub update_timer_seconds: i32,
}

impl Deploy {
    fn default_nodes() -> PathBuf {
        PathBuf::from(r"C:\lotus\nodes")
    }
    fn default_incoming() -> PathBuf {
        PathBuf::from(r"C:\lotus\releases\incoming")
    }
    fn default_repo() -> String {
        "b8py79hjfp-boop/osrs-ps".into()
    }
    const fn default_timer() -> i32 {
        300
    }
}

impl Default for Deploy {
    fn default() -> Self {
        Self {
            nodes: Self::default_nodes(),
            incoming: Self::default_incoming(),
            public_key: String::new(),
            ci_repo: Self::default_repo(),
            update_timer_seconds: Self::default_timer(),
        }
    }
}

/// Brief §7 `[backups]`: how the panel reaches the game database with `pg_dump`.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Backups {
    #[serde(default = "Backups::default_pg_bin")]
    pub pg_bin: PathBuf,
    #[serde(default = "Backups::default_host")]
    pub host: String,
    #[serde(default = "Backups::default_port")]
    pub port: u16,
    #[serde(default = "Backups::default_database")]
    pub database: String,
    /// A role with `pg_read_all_data` (the installer makes `lotus_backup`).
    #[serde(default = "Backups::default_user")]
    pub user: String,
    #[serde(default = "Backups::default_password_credential")]
    pub password_credential: String,
    /// The role restores to live run as (the hub's: it owns the game tables).
    #[serde(default = "Backups::default_restore_user")]
    pub restore_user: String,
    #[serde(default = "Backups::default_restore_password_credential")]
    pub restore_password_credential: String,
    /// The panel's own age key, so it can verify and restore.
    #[serde(default = "Backups::default_identity_credential")]
    pub identity_credential: String,
}

impl Backups {
    fn default_pg_bin() -> PathBuf {
        PathBuf::from(r"C:\lotus\tools\pgsql\bin")
    }
    fn default_host() -> String {
        "127.0.0.1".into()
    }
    const fn default_port() -> u16 {
        5432
    }
    fn default_database() -> String {
        "lotus".into()
    }
    fn default_user() -> String {
        "lotus_backup".into()
    }
    fn default_password_credential() -> String {
        "backup-db-password".into()
    }
    fn default_restore_user() -> String {
        "lotus_hub".into()
    }
    fn default_restore_password_credential() -> String {
        "restore-db-password".into()
    }
    fn default_identity_credential() -> String {
        "backup-identity".into()
    }
}

impl Default for Backups {
    fn default() -> Self {
        Self {
            pg_bin: Self::default_pg_bin(),
            host: Self::default_host(),
            port: Self::default_port(),
            database: Self::default_database(),
            user: Self::default_user(),
            password_credential: Self::default_password_credential(),
            restore_user: Self::default_restore_user(),
            restore_password_credential: Self::default_restore_password_credential(),
            identity_credential: Self::default_identity_credential(),
        }
    }
}

/// Brief §3: the units the panel controls and how to check them. Defaults match deploy/windows/install-lotus.ps1.
#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Control {
    #[serde(default = "Control::default_database")]
    pub database_unit: String,
    #[serde(default = "Control::default_hub")]
    pub hub_unit: String,
    #[serde(default = "Control::default_worlds")]
    pub worlds: Vec<WorldConfig>,
    #[serde(default = "Control::default_logs")]
    pub logs: PathBuf,
    #[serde(default = "Control::default_dumps")]
    pub dumps: PathBuf,
    /// Caddy's certificate store, for the TLS expiry check.
    #[serde(default = "Control::default_certificates")]
    pub certificates: PathBuf,
    /// Directories whose size the housekeeping page shows.
    #[serde(default = "Control::default_backups")]
    pub backups: PathBuf,
    #[serde(default = "Control::default_releases")]
    pub releases: PathBuf,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct WorldConfig {
    pub unit: String,
    pub world_id: i32,
    pub game_port: u16,
    pub health_url: String,
}

impl Control {
    fn default_database() -> String {
        "lotus-postgresql".into()
    }
    fn default_hub() -> String {
        "lotus-hub".into()
    }
    fn default_worlds() -> Vec<WorldConfig> {
        vec![WorldConfig {
            unit: "lotus".into(),
            world_id: 1,
            game_port: 43594,
            health_url: "http://127.0.0.1:9090/health".into(),
        }]
    }
    fn default_logs() -> PathBuf {
        PathBuf::from(r"C:\lotus\logs")
    }
    fn default_dumps() -> PathBuf {
        PathBuf::from(r"C:\lotus\game\.data\dumps")
    }
    fn default_certificates() -> PathBuf {
        PathBuf::from(r"C:\lotus\caddy\data\caddy\certificates")
    }
    fn default_backups() -> PathBuf {
        PathBuf::from(r"C:\lotus\backups")
    }
    fn default_releases() -> PathBuf {
        PathBuf::from(r"C:\lotus\releases")
    }
}

impl Default for Control {
    fn default() -> Self {
        Self {
            database_unit: Self::default_database(),
            hub_unit: Self::default_hub(),
            worlds: Self::default_worlds(),
            logs: Self::default_logs(),
            dumps: Self::default_dumps(),
            certificates: Self::default_certificates(),
            backups: Self::default_backups(),
            releases: Self::default_releases(),
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Server {
    /// Loopback only: Caddy terminates HTTPS on 443 and proxies here (brief §8).
    pub bind: SocketAddr,
    pub public_url: String,
    /// Empty = anyone who reaches Caddy; otherwise only these networks (e.g. Tailscale `100.64.0.0/10`).
    #[serde(default)]
    pub allowed_networks: Vec<Cidr>,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Hub {
    /// The hub's panel listener, e.g. `https://127.0.0.1:50051`.
    pub endpoint: String,
    /// The panel CA certificate (verifies the hub).
    pub ca: PathBuf,
    /// The panel's client certificate (public); its key is the `key_credential` secret.
    pub cert: PathBuf,
    pub key_credential: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Database {
    /// Secret holding the PostgreSQL URL of the panel's own schema.
    pub url_credential: String,
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Sessions {
    #[serde(default = "Sessions::default_idle")]
    pub idle_minutes: u32,
    #[serde(default = "Sessions::default_absolute")]
    pub absolute_hours: u32,
}

impl Sessions {
    /// Brief §2: 30 minutes idle.
    const fn default_idle() -> u32 {
        30
    }

    /// Brief §2: 12 hours absolute.
    const fn default_absolute() -> u32 {
        12
    }
}

impl Default for Sessions {
    fn default() -> Self {
        Self {
            idle_minutes: Self::default_idle(),
            absolute_hours: Self::default_absolute(),
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(deny_unknown_fields)]
pub struct Paths {
    pub secrets: PathBuf,
}

#[derive(Debug, thiserror::Error)]
pub enum ConfigError {
    #[error("cannot read {path}: {source}")]
    Read { path: PathBuf, source: std::io::Error },
    #[error("invalid config {path}: {source}")]
    Parse { path: PathBuf, source: toml::de::Error },
    #[error("[server] bind must be a loopback address, not {0}")]
    PublicBind(SocketAddr),
}

impl Config {
    pub fn load(path: &Path) -> Result<Self, ConfigError> {
        let text = std::fs::read_to_string(path).map_err(|source| ConfigError::Read {
            path: path.to_owned(),
            source,
        })?;
        let config: Self = toml::from_str(&text).map_err(|source| ConfigError::Parse {
            path: path.to_owned(),
            source,
        })?;
        if !config.server.bind.ip().is_loopback() {
            return Err(ConfigError::PublicBind(config.server.bind));
        }
        Ok(config)
    }
}

/// The default config location on the VPS.
pub fn default_path() -> PathBuf {
    if cfg!(windows) {
        PathBuf::from(r"C:\lotus\panel\panel.toml")
    } else {
        PathBuf::from("panel.toml")
    }
}
