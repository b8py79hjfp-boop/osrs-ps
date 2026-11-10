//! The hub's panel API over mutual TLS (plan §3), behind [`HubApi`] so the auth tests can use a fake hub.

use std::path::Path;

use async_trait::async_trait;
use tonic::transport::{Certificate, Channel, ClientTlsConfig, Identity};

use crate::rank::Rank;

#[allow(clippy::all, clippy::pedantic, missing_docs, unreachable_pub)]
pub mod pb {
    tonic::include_proto!("tp.hub.v1");
}

#[derive(Debug, thiserror::Error)]
pub enum HubError {
    #[error("the hub did not answer: {0}")]
    Unavailable(String),
    #[error("{0}")]
    Refused(String),
}

/// The answer to a staff sign-in.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Verified {
    Ok {
        account_id: i64,
        display_name: String,
        rank: Rank,
    },
    InvalidCredentials,
    Throttled,
    Banned,
    NotStaff,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RankInfo {
    pub exists: bool,
    pub rank: Rank,
    pub banned: bool,
}

/// The staff member a hub call acts for: the hub re-checks this account's stored rank on every call.
#[derive(Debug, Clone)]
pub struct Actor {
    pub account_id: i64,
    pub ip: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WorldStatus {
    pub id: i32,
    pub host: String,
    pub port: i32,
    pub activity: String,
    pub population: i32,
    pub locked: bool,
    pub last_beat_ms: i64,
}

/// One line of a world operation's answer (diagnostics, panel commands).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OpLine {
    pub line: String,
    pub done: bool,
    pub error: bool,
}

fn unsupported<T>() -> Result<T, HubError> {
    Err(HubError::Unavailable("not supported here".into()))
}

/// One panel action for the game's audit log (brief §8: actor, IP, action, target, result; the hub adds the time).
pub struct AuditEntry<'a> {
    pub actor_id: i64,
    pub ip: &'a str,
    pub action: &'a str,
    pub target: &'a str,
    pub detail: &'a str,
    pub result: &'a str,
}

#[async_trait]
pub trait HubApi: Send + Sync {
    async fn verify_staff(&self, username: &str, password: &str, ip: &str) -> Result<Verified, HubError>;
    async fn get_rank(&self, account_id: i64) -> Result<RankInfo, HubError>;
    async fn audit(&self, entry: AuditEntry<'_>) -> Result<(), HubError>;
    async fn bootstrap_owner(&self, username: &str) -> Result<(), HubError>;
    async fn healthy(&self) -> bool;

    async fn list_worlds(&self, _actor: &Actor) -> Result<Vec<WorldStatus>, HubError> {
        unsupported()
    }
    /// Maintenance mode per world (0 = every world): world id and message of each lock in force.
    async fn login_locks(&self, _actor: &Actor) -> Result<Vec<(i32, String)>, HubError> {
        unsupported()
    }
    async fn set_login_lock(&self, _actor: &Actor, _world: i32, _locked: bool, _message: &str) -> Result<(), HubError> {
        unsupported()
    }
    /// The in-game update countdown on every world; the message is broadcast first when not empty.
    async fn system_update(&self, _actor: &Actor, _seconds: i32, _message: &str) -> Result<(), HubError> {
        unsupported()
    }
    async fn cancel_update(&self, _actor: &Actor) -> Result<(), HubError> {
        unsupported()
    }
    /// Routes an operation to a world; its answer arrives line by line until one has `done`.
    async fn world_op(
        &self,
        _actor: &Actor,
        _world: i32,
        _kind: &str,
        _args: &str,
    ) -> Result<tokio::sync::mpsc::Receiver<OpLine>, HubError> {
        unsupported()
    }
}

fn actor_pb(a: &Actor) -> pb::PanelActor {
    pb::PanelActor {
        account_id: a.account_id,
        ip: a.ip.clone(),
    }
}

pub struct GrpcHub {
    client: pb::panel_client::PanelClient<Channel>,
}

impl GrpcHub {
    /// A lazily connecting channel: the panel starts (and shows the hub as down) even while the hub is stopped.
    pub fn connect(endpoint: &str, ca: &Path, cert: &Path, key_pem: &str) -> anyhow::Result<Self> {
        let tls = ClientTlsConfig::new()
            .ca_certificate(Certificate::from_pem(std::fs::read(ca)?))
            .identity(Identity::from_pem(std::fs::read(cert)?, key_pem.as_bytes()));
        let channel = Channel::from_shared(endpoint.to_owned())?
            .tls_config(tls)?
            .connect_timeout(std::time::Duration::from_secs(3))
            .timeout(std::time::Duration::from_secs(10))
            .connect_lazy();
        Ok(Self {
            client: pb::panel_client::PanelClient::new(channel),
        })
    }

    fn client(&self) -> pb::panel_client::PanelClient<Channel> {
        self.client.clone()
    }
}

fn unavailable(status: &tonic::Status) -> HubError {
    HubError::Unavailable(status.message().to_owned())
}

fn refused(error: String) -> Result<(), HubError> {
    if error.is_empty() {
        Ok(())
    } else {
        Err(HubError::Refused(error))
    }
}

#[async_trait]
impl HubApi for GrpcHub {
    async fn verify_staff(&self, username: &str, password: &str, ip: &str) -> Result<Verified, HubError> {
        let req = pb::VerifyStaffRequest {
            username: username.to_owned(),
            password: password.to_owned(),
            ip: ip.to_owned(),
        };
        let reply = self
            .client()
            .verify_staff(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        Ok(match reply.result.as_str() {
            "OK" => match Rank::parse(&reply.rank) {
                Some(rank) => Verified::Ok {
                    account_id: reply.account_id,
                    display_name: reply.display_name,
                    rank,
                },
                None => Verified::NotStaff,
            },
            "THROTTLED" => Verified::Throttled,
            "BANNED" => Verified::Banned,
            "NOT_STAFF" => Verified::NotStaff,
            _ => Verified::InvalidCredentials,
        })
    }

    async fn get_rank(&self, account_id: i64) -> Result<RankInfo, HubError> {
        let reply = self
            .client()
            .get_rank(pb::AccountRef { account_id })
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        Ok(RankInfo {
            exists: reply.exists,
            rank: Rank::parse(&reply.rank).unwrap_or(Rank::Player),
            banned: reply.banned,
        })
    }

    async fn audit(&self, e: AuditEntry<'_>) -> Result<(), HubError> {
        let req = pb::PanelAuditRequest {
            actor: Some(pb::PanelActor {
                account_id: e.actor_id,
                ip: e.ip.to_owned(),
            }),
            action: e.action.to_owned(),
            target: e.target.to_owned(),
            detail: e.detail.to_owned(),
            result: e.result.to_owned(),
        };
        let reply = self
            .client()
            .audit(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        refused(reply.error)
    }

    async fn bootstrap_owner(&self, username: &str) -> Result<(), HubError> {
        let req = pb::BootstrapOwnerRequest {
            username: username.to_owned(),
        };
        let reply = self
            .client()
            .bootstrap_owner(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        refused(reply.error)
    }

    /// `GetRank` of account 0, which never exists and changes nothing.
    async fn healthy(&self) -> bool {
        self.client().get_rank(pb::AccountRef { account_id: 0 }).await.is_ok()
    }

    async fn list_worlds(&self, actor: &Actor) -> Result<Vec<WorldStatus>, HubError> {
        let reply = self
            .client()
            .list_worlds(actor_pb(actor))
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        if !reply.error.is_empty() {
            return Err(HubError::Refused(reply.error));
        }
        Ok(reply
            .worlds
            .into_iter()
            .map(|w| WorldStatus {
                id: w.id,
                host: w.host,
                port: w.port,
                activity: w.activity,
                population: w.population,
                locked: w.locked,
                last_beat_ms: w.last_beat_ms,
            })
            .collect())
    }

    async fn login_locks(&self, actor: &Actor) -> Result<Vec<(i32, String)>, HubError> {
        let reply = self
            .client()
            .get_login_lock(actor_pb(actor))
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        if !reply.error.is_empty() {
            return Err(HubError::Refused(reply.error));
        }
        Ok(reply.locks.into_iter().map(|l| (l.world_id, l.message)).collect())
    }

    async fn set_login_lock(&self, actor: &Actor, world: i32, locked: bool, message: &str) -> Result<(), HubError> {
        let req = pb::LoginLockRequest {
            actor: Some(actor_pb(actor)),
            world_id: world,
            locked,
            message: message.to_owned(),
        };
        refused(
            self.client()
                .set_login_lock(req)
                .await
                .map_err(|s| unavailable(&s))?
                .into_inner()
                .error,
        )
    }

    async fn system_update(&self, actor: &Actor, seconds: i32, message: &str) -> Result<(), HubError> {
        let req = pb::PanelUpdateRequest {
            actor: Some(actor_pb(actor)),
            seconds,
            message: message.to_owned(),
        };
        refused(
            self.client()
                .system_update(req)
                .await
                .map_err(|s| unavailable(&s))?
                .into_inner()
                .error,
        )
    }

    async fn cancel_update(&self, actor: &Actor) -> Result<(), HubError> {
        refused(
            self.client()
                .cancel_update(actor_pb(actor))
                .await
                .map_err(|s| unavailable(&s))?
                .into_inner()
                .error,
        )
    }

    async fn world_op(
        &self,
        actor: &Actor,
        world: i32,
        kind: &str,
        args: &str,
    ) -> Result<tokio::sync::mpsc::Receiver<OpLine>, HubError> {
        let req = pb::PanelWorldOpRequest {
            actor: Some(actor_pb(actor)),
            world_id: world,
            kind: kind.to_owned(),
            args: args.to_owned(),
        };
        let mut stream = self
            .client()
            .world_op(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        let (tx, rx) = tokio::sync::mpsc::channel(64);
        tokio::spawn(async move {
            loop {
                match stream.message().await {
                    Ok(Some(o)) => {
                        let done = o.done;
                        if tx
                            .send(OpLine {
                                line: o.line,
                                done: o.done,
                                error: o.error,
                            })
                            .await
                            .is_err()
                            || done
                        {
                            break;
                        }
                    }
                    Ok(None) => break,
                    Err(s) => {
                        let _ = tx
                            .send(OpLine {
                                line: format!("The hub stopped answering: {}", s.message()),
                                done: true,
                                error: true,
                            })
                            .await;
                        break;
                    }
                }
            }
        });
        Ok(rx)
    }
}
