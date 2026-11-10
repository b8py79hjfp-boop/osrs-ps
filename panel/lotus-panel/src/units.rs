//! The machine's services (panel plan §1: the Windows Service Control Manager stands in for systemd). The panel only
//! ever starts, stops and queries `lotus-*` services; the install script grants its account exactly those rights on
//! exactly those services, and nothing else (no shell, no other service).

use async_trait::async_trait;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ServiceState {
    Running,
    StartPending,
    StopPending,
    Stopped,
    /// The service isn't installed (or the panel may not open it).
    Missing,
}

#[async_trait]
pub trait Units: Send + Sync {
    async fn state(&self, unit: &str) -> ServiceState;
    async fn start(&self, unit: &str) -> anyhow::Result<()>;
    /// Asks the service to stop. For the game services the wrapper sends Ctrl+C first, so the server saves everyone.
    async fn stop(&self, unit: &str) -> anyhow::Result<()>;
}

/// Only `lotus-*` services, and `lotus` itself (the world), may be touched.
pub fn allowed_unit(unit: &str) -> bool {
    unit == "lotus"
        || (unit.starts_with("lotus-")
            && unit.len() <= 64
            && unit.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-'))
}

#[cfg(windows)]
pub use self::scm::Scm as SystemUnits;

#[cfg(not(windows))]
pub use self::unsupported::Unsupported as SystemUnits;

#[cfg(windows)]
mod scm {
    use std::ffi::OsString;

    use async_trait::async_trait;
    use windows_service::service::{ServiceAccess, ServiceState as WinState};
    use windows_service::service_manager::{ServiceManager, ServiceManagerAccess};

    use super::{ServiceState, Units, allowed_unit};

    #[derive(Default)]
    pub struct Scm;

    fn open(unit: &str, access: ServiceAccess) -> anyhow::Result<windows_service::service::Service> {
        anyhow::ensure!(allowed_unit(unit), "{unit} is not a Lotus service");
        let manager = ServiceManager::local_computer(None::<&str>, ServiceManagerAccess::CONNECT)?;
        Ok(manager.open_service(unit, access)?)
    }

    #[async_trait]
    impl Units for Scm {
        async fn state(&self, unit: &str) -> ServiceState {
            let unit = unit.to_owned();
            tokio::task::spawn_blocking(move || {
                let Ok(service) = open(&unit, ServiceAccess::QUERY_STATUS) else {
                    return ServiceState::Missing;
                };
                match service.query_status().map(|s| s.current_state) {
                    Ok(WinState::Running) => ServiceState::Running,
                    Ok(WinState::StartPending | WinState::ContinuePending) => ServiceState::StartPending,
                    Ok(WinState::StopPending | WinState::PausePending | WinState::Paused) => ServiceState::StopPending,
                    Ok(WinState::Stopped) => ServiceState::Stopped,
                    Err(_) => ServiceState::Missing,
                }
            })
            .await
            .unwrap_or(ServiceState::Missing)
        }

        async fn start(&self, unit: &str) -> anyhow::Result<()> {
            let unit = unit.to_owned();
            tokio::task::spawn_blocking(move || {
                let service = open(&unit, ServiceAccess::START | ServiceAccess::QUERY_STATUS)?;
                if service.query_status()?.current_state == WinState::Running {
                    return Ok(());
                }
                service.start::<OsString>(&[])?;
                Ok(())
            })
            .await?
        }

        async fn stop(&self, unit: &str) -> anyhow::Result<()> {
            let unit = unit.to_owned();
            tokio::task::spawn_blocking(move || {
                let service = open(&unit, ServiceAccess::STOP | ServiceAccess::QUERY_STATUS)?;
                if service.query_status()?.current_state == WinState::Stopped {
                    return Ok(());
                }
                service.stop()?;
                Ok(())
            })
            .await?
        }
    }
}

#[cfg(not(windows))]
mod unsupported {
    use async_trait::async_trait;

    use super::{ServiceState, Units};

    /// Development machines: there are no Windows services to control.
    #[derive(Default)]
    pub struct Unsupported;

    #[async_trait]
    impl Units for Unsupported {
        async fn state(&self, _unit: &str) -> ServiceState {
            ServiceState::Missing
        }

        async fn start(&self, unit: &str) -> anyhow::Result<()> {
            anyhow::bail!("{unit}: service control runs on Windows only")
        }

        async fn stop(&self, unit: &str) -> anyhow::Result<()> {
            anyhow::bail!("{unit}: service control runs on Windows only")
        }
    }
}
