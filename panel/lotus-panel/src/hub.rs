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

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct OnlinePlayer {
    pub account_id: i64,
    pub name: String,
    pub world: i32,
    pub rank: Rank,
    pub invisible: bool,
}

/// A moderation record (`moderation_records`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ModRecord {
    pub id: i64,
    pub account_id: i64,
    pub kind: String,
    pub actor: String,
    pub reason: String,
    pub start_ms: i64,
    /// -1 = permanent.
    pub expires_ms: i64,
    /// IP or device for those bans (shown to administrators only; the hub blanks it for others).
    pub scope: String,
    pub lifted_by: String,
    pub lifted_at_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PlayerCard {
    pub account_id: i64,
    pub name: String,
    pub rank: Rank,
    pub world: i32,
    pub muted_until: i64,
    pub mute_reason: String,
    pub jailed_until: i64,
    pub jail_reason: String,
    pub banned_until: i64,
    pub records: i32,
    /// Administrators and above only (the hub blanks it otherwise, and audits every view).
    pub last_ip: String,
    pub donator: String,
}

#[derive(Debug, Clone, Default)]
pub struct AuditFilter {
    pub actor: String,
    pub target: String,
    pub action: String,
    pub source: String,
    pub text: String,
    pub since_ms: i64,
    pub until_ms: i64,
    pub before_id: i64,
    pub limit: i32,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AuditRow {
    pub id: i64,
    pub at_ms: i64,
    pub actor: String,
    pub action: String,
    pub target: String,
    pub detail: String,
    pub source: String,
    pub ip: String,
    pub result: String,
}

/// One line of the hub's recent chat (brief §4 Chat & broadcast): `yell`, `staff` or `broadcast`.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ChatLine {
    pub at_ms: i64,
    pub kind: String,
    pub from: String,
    pub world: i32,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StaffMember {
    pub account_id: i64,
    pub name: String,
    pub rank: Rank,
    pub world: i32,
}

fn record_of(r: pb::ModRecordPb) -> ModRecord {
    ModRecord {
        id: r.id,
        account_id: r.account_id,
        kind: r.r#type,
        actor: r.actor,
        reason: r.reason,
        start_ms: r.start,
        expires_ms: r.expires,
        scope: r.scope,
        lifted_by: r.lifted_by,
        lifted_at_ms: r.lifted_at,
    }
}

fn check(error: String) -> Result<(), HubError> {
    if error.is_empty() {
        Ok(())
    } else {
        Err(HubError::Refused(error))
    }
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
    /// The password asked again before a dangerous action: the same check, audited as a re-check, not a sign-in.
    async fn recheck_password(&self, username: &str, password: &str, ip: &str) -> Result<Verified, HubError> {
        self.verify_staff(username, password, ip).await
    }
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
    async fn list_online(&self, _actor: &Actor) -> Result<Vec<OnlinePlayer>, HubError> {
        unsupported()
    }
    async fn lookup(&self, _actor: &Actor, _target: &str) -> Result<PlayerCard, HubError> {
        unsupported()
    }
    async fn history(&self, _actor: &Actor, _target: &str) -> Result<Vec<ModRecord>, HubError> {
        unsupported()
    }
    /// `alts`, `ips` (administrators) or `audit` (owners); every call is audited by the hub.
    async fn investigate(
        &self,
        _actor: &Actor,
        _target: &str,
        _kind: &str,
        _filter: &str,
    ) -> Result<Vec<String>, HubError> {
        unsupported()
    }
    /// (record, account name), newest first.
    async fn list_sanctions(
        &self,
        _actor: &Actor,
        _kind: &str,
        _staff: &str,
        _active_only: bool,
        _limit: i32,
    ) -> Result<Vec<(ModRecord, String)>, HubError> {
        unsupported()
    }
    async fn query_audit(&self, _actor: &Actor, _filter: &AuditFilter) -> Result<Vec<AuditRow>, HubError> {
        unsupported()
    }
    async fn list_staff(&self, _actor: &Actor) -> Result<Vec<StaffMember>, HubError> {
        unsupported()
    }
    async fn recent_chat(&self, _actor: &Actor) -> Result<Vec<ChatLine>, HubError> {
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
    async fn verify(&self, username: &str, password: &str, ip: &str, recheck: bool) -> Result<Verified, HubError> {
        let req = pb::VerifyStaffRequest {
            username: username.to_owned(),
            password: password.to_owned(),
            ip: ip.to_owned(),
            recheck,
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
        self.verify(username, password, ip, false).await
    }

    async fn recheck_password(&self, username: &str, password: &str, ip: &str) -> Result<Verified, HubError> {
        self.verify(username, password, ip, true).await
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

    async fn list_online(&self, actor: &Actor) -> Result<Vec<OnlinePlayer>, HubError> {
        let r = self
            .client()
            .list_online(actor_pb(actor))
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.players
            .into_iter()
            .map(|p| OnlinePlayer {
                account_id: p.account_id,
                name: p.name,
                world: p.world_id,
                rank: Rank::parse(&p.rank).unwrap_or(Rank::Player),
                invisible: p.invisible,
            })
            .collect())
    }

    async fn lookup(&self, actor: &Actor, target: &str) -> Result<PlayerCard, HubError> {
        let req = pb::PanelTargetRequest {
            actor: Some(actor_pb(actor)),
            target: target.to_owned(),
            ..Default::default()
        };
        let r = self
            .client()
            .lookup(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        let s = r.sanctions.unwrap_or_default();
        Ok(PlayerCard {
            account_id: r.account_id,
            name: r.name,
            rank: Rank::parse(&r.rank).unwrap_or(Rank::Player),
            world: r.world,
            muted_until: s.muted_until,
            mute_reason: s.mute_reason,
            jailed_until: s.jailed_until,
            jail_reason: s.jail_reason,
            banned_until: r.banned_until,
            records: r.records,
            last_ip: r.last_ip,
            donator: r.donator,
        })
    }

    async fn history(&self, actor: &Actor, target: &str) -> Result<Vec<ModRecord>, HubError> {
        let req = pb::PanelTargetRequest {
            actor: Some(actor_pb(actor)),
            target: target.to_owned(),
            ..Default::default()
        };
        let r = self
            .client()
            .history(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.records.into_iter().map(record_of).collect())
    }

    async fn investigate(
        &self,
        actor: &Actor,
        target: &str,
        kind: &str,
        filter: &str,
    ) -> Result<Vec<String>, HubError> {
        let req = pb::PanelTargetRequest {
            actor: Some(actor_pb(actor)),
            target: target.to_owned(),
            kind: kind.to_owned(),
            filter: filter.to_owned(),
        };
        let r = self
            .client()
            .investigate(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.lines)
    }

    async fn list_sanctions(
        &self,
        actor: &Actor,
        kind: &str,
        staff: &str,
        active_only: bool,
        limit: i32,
    ) -> Result<Vec<(ModRecord, String)>, HubError> {
        let req = pb::SanctionsQuery {
            actor: Some(actor_pb(actor)),
            r#type: kind.to_owned(),
            staff: staff.to_owned(),
            active_only,
            limit,
        };
        let r = self
            .client()
            .list_sanctions(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.rows
            .into_iter()
            .map(|row| (record_of(row.record.unwrap_or_default()), row.account_name))
            .collect())
    }

    async fn query_audit(&self, actor: &Actor, f: &AuditFilter) -> Result<Vec<AuditRow>, HubError> {
        let req = pb::AuditQuery {
            actor: Some(actor_pb(actor)),
            by: f.actor.clone(),
            target: f.target.clone(),
            action: f.action.clone(),
            source: f.source.clone(),
            text: f.text.clone(),
            since_ms: f.since_ms,
            until_ms: f.until_ms,
            before_id: f.before_id,
            limit: f.limit,
        };
        let r = self
            .client()
            .query_audit(req)
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.rows
            .into_iter()
            .map(|a| AuditRow {
                id: a.id,
                at_ms: a.at_ms,
                actor: a.actor,
                action: a.action,
                target: a.target,
                detail: a.detail,
                source: a.source,
                ip: a.ip,
                result: a.result,
            })
            .collect())
    }

    async fn list_staff(&self, actor: &Actor) -> Result<Vec<StaffMember>, HubError> {
        let r = self
            .client()
            .list_staff(actor_pb(actor))
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.staff
            .into_iter()
            .map(|m| StaffMember {
                account_id: m.account_id,
                name: m.name,
                rank: Rank::parse(&m.rank).unwrap_or(Rank::Player),
                world: m.world_id,
            })
            .collect())
    }

    async fn recent_chat(&self, actor: &Actor) -> Result<Vec<ChatLine>, HubError> {
        let r = self
            .client()
            .recent_chat(actor_pb(actor))
            .await
            .map_err(|s| unavailable(&s))?
            .into_inner();
        check(r.error)?;
        Ok(r.rows
            .into_iter()
            .map(|c| ChatLine {
                at_ms: c.at_ms,
                kind: c.kind,
                from: c.from,
                world: c.world_id,
                text: c.text,
            })
            .collect())
    }
}
