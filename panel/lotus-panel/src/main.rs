//! `lotus-panel`: the Lotus control panel (panel/docs/brief.md, plan panel/docs/plan.md).
//!
//! - `serve`: the web panel on 127.0.0.1, behind Caddy.
//! - `certs`: the panel ↔ hub mTLS certificates.
//! - `secret set <name>`: store a secret (value read from standard input).
//! - `check`: validate the config, the secrets, the database and the hub link, for the install script.
//! - `bootstrap --account <name>`: make the first owner (only while there is none; brief §2, run on the VPS).
//! - `ctl <start|stop|restart|status|lock|unlock> [world]`: server control from the VPS, same code and rules as the
//!   web panel (brief §3 CLI twin), for when the panel is unreachable.

use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;

use anyhow::Context;
use clap::{Parser, Subcommand};
use lotus_panel::control::{Controller, Scope, Topology, WorldUnit};
use lotus_panel::hub::HubApi;
use lotus_panel::jobs::{JobState, JobStore};
use lotus_panel::{auth, certs, clock, config, control, control_web, db, hub, jobs, secrets, store, units, web};
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
    /// Server control from the VPS (brief §3 CLI twin). Needs the local ctl token (Administrators and the panel's
    /// service account can read it); lock, unlock and a graceful stop or restart also need an owner (`--as`, password
    /// from standard input).
    Ctl {
        #[arg(value_parser = ["start", "stop", "restart", "status", "lock", "unlock"])]
        action: String,
        /// A world id; everything when left out.
        world: Option<i32>,
        /// Update timer for stop and restart.
        #[arg(long, default_value_t = control::DEFAULT_TIMER_SECONDS)]
        seconds: i32,
        #[arg(long, default_value = "")]
        message: String,
        /// Owner account to act as (the hub checks the rank and audits the action).
        #[arg(long = "as")]
        as_account: Option<String>,
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
        Command::Ctl {
            action,
            world,
            seconds,
            message,
            as_account,
        } => runtime.block_on(ctl(
            &config_path,
            &action,
            world,
            seconds,
            &message,
            as_account.as_deref(),
        )),
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

fn topology(config: &config::Config) -> Topology {
    let c = &config.control;
    Topology {
        database: c.database_unit.clone(),
        hub: c.hub_unit.clone(),
        worlds: c
            .worlds
            .iter()
            .map(|w| WorldUnit {
                unit: w.unit.clone(),
                world_id: w.world_id,
                game_port: w.game_port,
                health_url: w.health_url.clone(),
            })
            .collect(),
        logs: c.logs.clone(),
    }
}

fn domain_of(url: &str) -> String {
    url.split("://")
        .nth(1)
        .unwrap_or(url)
        .split(['/', ':'])
        .next()
        .unwrap_or("")
        .to_owned()
}

async fn state(config: &config::Config) -> anyhow::Result<web::AppState> {
    let url = secrets::read(&config.paths.secrets, &config.database.url_credential)?;
    let pool = db::connect(&url).await.context("panel database")?;
    let hub: Arc<dyn HubApi> = Arc::new(hub_of(config)?);
    let sessions: Arc<dyn store::SessionStore> = Arc::new(store::PgSessions(pool.clone()));
    let jobs: Arc<dyn JobStore> = Arc::new(jobs::PgJobs(pool.clone()));
    let clock: Arc<dyn clock::Clock> = Arc::new(clock::SystemClock);
    let probe = Arc::new(control::LiveProbe {
        sessions: sessions.clone(),
        hub: hub.clone(),
        http: reqwest::Client::new(),
    });
    let controller = Controller::new(
        topology(config),
        Arc::new(units::SystemUnits),
        probe,
        hub.clone(),
        jobs.clone(),
        Arc::new(jobs::LogAlerts),
        clock.clone(),
    );
    let c = &config.control;
    Ok(web::AppState {
        hub,
        sessions,
        clock,
        lockout: auth::Lockout::default(),
        auth: auth::AuthSettings::new(
            config.sessions.idle_minutes,
            config.sessions.absolute_hours,
            &config.server.public_url,
        ),
        allowed: config.server.allowed_networks.clone(),
        control: Arc::new(controller),
        jobs,
        settings: Arc::new(jobs::PgSettings(pool)),
        paths: web::PanelPaths {
            logs: c.logs.clone(),
            dumps: c.dumps.clone(),
            backups: c.backups.clone(),
            releases: c.releases.clone(),
            certificates: c.certificates.clone(),
        },
        domain: domain_of(&config.server.public_url),
    })
}

/// The ctl actor: an owner who signs in on the console (the hub checks the rank and audits every action).
async fn ctl_actor(state: &web::AppState, account: Option<&str>) -> anyhow::Result<(hub::Actor, String)> {
    let Some(name) = account else {
        return Ok((
            hub::Actor {
                account_id: 0,
                ip: "local ctl".into(),
            },
            "local ctl".into(),
        ));
    };
    eprint!("Password for {name}: ");
    let password = read_line().await?;
    match state.hub.verify_staff(name, &password, "local ctl").await? {
        hub::Verified::Ok {
            account_id,
            display_name,
            rank,
        } if rank >= lotus_panel::rank::Rank::Owner => Ok((
            hub::Actor {
                account_id,
                ip: "local ctl".into(),
            },
            display_name,
        )),
        hub::Verified::Ok { .. } => anyhow::bail!("{name} is not an owner"),
        other => anyhow::bail!("sign-in refused: {other:?}"),
    }
}

async fn ctl(
    path: &std::path::Path,
    action: &str,
    world: Option<i32>,
    seconds: i32,
    message: &str,
    as_account: Option<&str>,
) -> anyhow::Result<()> {
    let config = config::Config::load(path)?;
    // Brief §3: the CLI needs the local owner token file, readable only by the panel's account and Administrators.
    secrets::read(&config.paths.secrets, "ctl-token")
        .context("the ctl token (only Administrators and the panel can read it)")?;
    let state = state(&config).await?;
    let control = state.control.clone();
    let scope = world.map_or(Scope::All, Scope::World);
    if action == "status" {
        for (label, unit, view) in control.views().await {
            println!("{label:<10} {unit:<18} {}", view.label());
        }
        if let Some(job) = control.running_job() {
            println!("control job #{job} is running");
        }
        return Ok(());
    }
    let (actor, name) = ctl_actor(&state, as_account).await?;
    let job = match action {
        "start" => control.start(scope, &name).await?,
        "stop" | "restart" => {
            anyhow::ensure!(
                as_account.is_some(),
                "a graceful {action} needs --as <owner> (for the lock and the countdown)"
            );
            control
                .stop(scope, seconds, message.to_owned(), action == "restart", actor, &name)
                .await?
        }
        "lock" | "unlock" => {
            anyhow::ensure!(as_account.is_some(), "{action} needs --as <owner>");
            control
                .maintenance(
                    &actor,
                    action == "lock",
                    if message.is_empty() { "Maintenance" } else { message },
                )
                .await?;
            println!(
                "logins {}",
                if action == "lock" {
                    "locked to staff"
                } else {
                    "unlocked"
                }
            );
            return Ok(());
        }
        _ => anyhow::bail!("unknown action {action}"),
    };
    // Follow the job's log until it ends.
    let mut shown = 0;
    loop {
        tokio::time::sleep(std::time::Duration::from_secs(1)).await;
        let Some(j) = state.jobs.get(job).await? else { continue };
        let lines: Vec<&str> = j.log.lines().collect();
        for l in &lines[shown.min(lines.len())..] {
            println!("{l}");
        }
        shown = lines.len();
        if j.state != JobState::Running {
            println!("job #{job}: {}", j.state.stored());
            anyhow::ensure!(j.state == JobState::Succeeded, "the job didn't succeed");
            return Ok(());
        }
    }
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
    control_web::spawn_background(state.clone());
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
