//! Database backups with `pg_dump` (brief §6 step 2, §7). The panel reaches the game database as a read-only role
//! (`pg_read_all_data`, made by the installer); its password is a DPAPI secret and goes to `pg_dump` through the
//! `PGPASSWORD` environment variable, never the command line.

use std::path::{Path, PathBuf};

use async_trait::async_trait;
use time::OffsetDateTime;

use crate::config;

/// Where and how to dump.
#[derive(Debug, Clone)]
pub struct PgDump {
    pub pg_bin: PathBuf,
    pub host: String,
    pub port: u16,
    pub database: String,
    pub user: String,
    pub password: String,
    pub dir: PathBuf,
}

impl PgDump {
    pub fn from_config(c: &config::Backups, password: String, dir: &Path) -> Self {
        Self {
            pg_bin: c.pg_bin.clone(),
            host: c.host.clone(),
            port: c.port,
            database: c.database.clone(),
            user: c.user.clone(),
            password,
            dir: dir.to_path_buf(),
        }
    }

    fn exe(&self, name: &str) -> PathBuf {
        let file = if cfg!(windows) {
            format!("{name}.exe")
        } else {
            name.to_owned()
        };
        self.pg_bin.join(file)
    }

    /// A custom-format dump into `<dir>\<prefix>-<UTC stamp>.dump`; returns its path.
    pub async fn dump(&self, prefix: &str, now: OffsetDateTime) -> anyhow::Result<PathBuf> {
        tokio::fs::create_dir_all(&self.dir).await?;
        let stamp = format!(
            "{:04}{:02}{:02}-{:02}{:02}{:02}",
            now.year(),
            u8::from(now.month()),
            now.day(),
            now.hour(),
            now.minute(),
            now.second()
        );
        let out = self.dir.join(format!("{prefix}-{stamp}.dump"));
        let status = tokio::process::Command::new(self.exe("pg_dump"))
            .arg("--format=custom")
            .arg("--no-password")
            .args([
                "--host",
                &self.host,
                "--port",
                &self.port.to_string(),
                "--username",
                &self.user,
            ])
            .arg("--file")
            .arg(&out)
            .arg(&self.database)
            .env("PGPASSWORD", &self.password)
            .kill_on_drop(true)
            .output()
            .await?;
        if !status.status.success() {
            let _ = tokio::fs::remove_file(&out).await;
            anyhow::bail!(
                "pg_dump failed: {}",
                String::from_utf8_lossy(&status.stderr)
                    .lines()
                    .last()
                    .unwrap_or("no output")
            );
        }
        Ok(out)
    }
}

#[async_trait]
impl crate::deploy::Backup for PgDump {
    async fn before_deploy(&self, version: &str) -> anyhow::Result<String> {
        let safe: String = version
            .chars()
            .filter(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_'))
            .collect();
        let path = self
            .dump(&format!("pre-deploy-{safe}"), OffsetDateTime::now_utc())
            .await?;
        Ok(path.display().to_string())
    }
}
