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
}
