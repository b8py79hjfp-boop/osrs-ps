//! `lotus-panel`: the Lotus control panel (panel/docs/brief.md, plan panel/docs/plan.md).
//!
//! - `serve`: the web panel on 127.0.0.1, behind Caddy.
//! - `certs`: the panel ↔ hub mTLS certificates.
//! - `secret set <name>`: store a secret (value read from standard input).
//! - `check`: validate the config, the secrets, the database and the hub link, for the install script.
//! - `bootstrap --account <name>`: make the first owner (only while there is none; brief §2, run on the VPS).

use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;

use anyhow::Context;
use clap::{Parser, Subcommand};
use lotus_panel::hub::HubApi;
use lotus_panel::{auth, certs, clock, config, db, hub, secrets, store, web};
use tokio::io::AsyncBufReadExt;

#[derive(Parser)]
#[command(name = "lotus-panel", version, about = "Lotus control panel")]
struct Cli {
    /// Config file (default C:\lotus\panel\panel.toml on Windows, ./panel.toml elsewhere).
    #[arg(long, global = true)]
    config: Option<PathBuf>,
    #[command(subcommand)]
    command: Command,
}

#[derive(Subcommand)]
enum Command {
    /// Run the web panel.
    Serve,
    /// Make the CA, hub and panel certificates for the hub link.
    Certs {
        /// Directory to write ca.pem, hub.pem, panel.pem and their keys into.
        #[arg(long)]
        out: PathBuf,
        /// Replace existing certificates.
        #[arg(long)]
        force: bool,
    },
    /// Manage secrets.
    Secret {
        #[command(subcommand)]
        action: SecretAction,
    },
    /// Check the config, secrets, database and hub link; exits non-zero on a problem.
    Check,
    /// Make the first owner. Refused once any account is an owner or developer; there is no web path to this.
    Bootstrap {
        #[arg(long)]
        account: String,
    },
}

#[derive(Subcommand)]
enum SecretAction {
    /// Store a secret: one line from standard input, or a whole file (`--from-file`, e.g. a PEM key). Never a
    /// command-line value, which would land in the shell history and process list.
    Set {
        name: String,
        #[arg(long)]
        from_file: Option<PathBuf>,
    },
}

fn main() -> anyhow::Result<()> {
    let cli = Cli::parse();
    tracing_subscriber::fmt()
        .json()
        .with_env_filter(
            tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info,sqlx=warn".into()),
        )
        .init();
    let config_path = cli.config.unwrap_or_else(config::default_path);
    let runtime = tokio::runtime::Runtime::new()?;
    match cli.command {
        Command::Certs { out, force } => {
            certs::generate(&out, force)?;
            println!("wrote {} certificates to {}", certs::FILES.len(), out.display());
            Ok(())
        }
        Command::Secret {
            action: SecretAction::Set { name, from_file },
        } => {
            let config = config::Config::load(&config_path)?;
            let value = match from_file {
                Some(file) => std::fs::read_to_string(&file).with_context(|| file.display().to_string())?,
                None => runtime.block_on(read_line())?,
            };
            secrets::write(&config.paths.secrets, &name, &value)?;
            println!("secret {name} saved");
            Ok(())
        }
        Command::Check => runtime.block_on(check(&config_path)),
        Command::Bootstrap { account } => runtime.block_on(bootstrap(&config_path, &account)),
        Command::Serve => runtime.block_on(serve(&config_path)),
    }
}

async fn read_line() -> anyhow::Result<String> {
    let mut line = String::new();
    tokio::io::BufReader::new(tokio::io::stdin())
        .read_line(&mut line)
        .await?;
    Ok(line.trim_end_matches(['\r', '\n']).to_owned())
}

fn hub_of(config: &config::Config) -> anyhow::Result<hub::GrpcHub> {
    let key = secrets::read(&config.paths.secrets, &config.hub.key_credential)?;
    hub::GrpcHub::connect(&config.hub.endpoint, &config.hub.ca, &config.hub.cert, &key).context("hub link")
}

async fn state(config: &config::Config) -> anyhow::Result<web::AppState> {
    let url = secrets::read(&config.paths.secrets, &config.database.url_credential)?;
    let pool = db::connect(&url).await.context("panel database")?;
    Ok(web::AppState {
        hub: Arc::new(hub_of(config)?),
        sessions: Arc::new(store::PgSessions(pool)),
        clock: Arc::new(clock::SystemClock),
        lockout: auth::Lockout::default(),
        auth: auth::AuthSettings::new(
            config.sessions.idle_minutes,
            config.sessions.absolute_hours,
            &config.server.public_url,
        ),
        allowed: config.server.allowed_networks.clone(),
    })
}

async fn bootstrap(path: &std::path::Path, account: &str) -> anyhow::Result<()> {
    let config = config::Config::load(path)?;
    hub_of(&config)?.bootstrap_owner(account).await?;
    println!("{account} is now an owner");
    Ok(())
}

async fn check(path: &std::path::Path) -> anyhow::Result<()> {
    let config = config::Config::load(path)?;
    let state = state(&config).await?;
    println!("config ok, database ok (migrations applied)");
    anyhow::ensure!(
        state.hub.healthy().await,
        "the hub's panel API at {} does not answer",
        config.hub.endpoint
    );
    println!("hub ok");
    Ok(())
}

async fn serve(path: &std::path::Path) -> anyhow::Result<()> {
    let config = config::Config::load(path)?;
    let state = Arc::new(state(&config).await?);
    if !state.hub.healthy().await {
        tracing::warn!(endpoint = %config.hub.endpoint, "hub not reachable yet; the panel starts anyway");
    }
    let app = web::router(state).into_make_service_with_connect_info::<SocketAddr>();
    let listener = tokio::net::TcpListener::bind(config.server.bind).await?;
    tracing::info!(
        bind = %config.server.bind,
        public_url = %config.server.public_url,
        idle_minutes = config.sessions.idle_minutes,
        absolute_hours = config.sessions.absolute_hours,
        "panel listening"
    );
    axum::serve(listener, app).with_graceful_shutdown(shutdown()).await?;
    Ok(())
}

/// Ctrl+C (the service wrapper's stop signal on Windows) or SIGTERM.
async fn shutdown() {
    let ctrl_c = async {
        let _ = tokio::signal::ctrl_c().await;
    };
    #[cfg(unix)]
    let term = async {
        if let Ok(mut s) = tokio::signal::unix::signal(tokio::signal::unix::SignalKind::terminate()) {
            s.recv().await;
        }
    };
    #[cfg(not(unix))]
    let term = std::future::pending::<()>();
    tokio::select! { () = ctrl_c => {}, () = term => {} }
    tracing::info!("shutting down");
}
