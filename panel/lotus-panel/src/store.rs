//! Server-side sessions (brief §2): the cookie holds a random token; the store keys sessions by its SHA-256, so a leaked
//! database row can't be replayed. [`PgSessions`] in production, [`MemorySessions`] in the tests.

use std::sync::Mutex;

use async_trait::async_trait;
use time::OffsetDateTime;

use crate::rank::Rank;

#[derive(Debug, Clone)]
pub struct Session {
    pub id_hash: Vec<u8>,
    pub account_id: i64,
    /// The login name (what `VerifyStaff` takes), for the password re-check.
    pub username: String,
    pub display_name: String,
    pub rank: Rank,
    pub ip: String,
    pub user_agent: String,
    /// Per-session CSRF token, sent back by every form and htmx request.
    pub csrf: String,
    pub created_at: OffsetDateTime,
    pub last_seen_at: OffsetDateTime,
    pub rank_checked_at: OffsetDateTime,
    pub revoked_at: Option<OffsetDateTime>,
}

impl Session {
    /// The session's handle in pages and forms: the hex of its hash (never the token itself).
    pub fn handle(&self) -> String {
        hex(&self.id_hash)
    }
}

pub fn hex(bytes: &[u8]) -> String {
    use std::fmt::Write;
    bytes.iter().fold(String::with_capacity(bytes.len() * 2), |mut s, b| {
        let _ = write!(s, "{b:02x}");
        s
    })
}

pub fn unhex(text: &str) -> Option<Vec<u8>> {
    if !text.len().is_multiple_of(2) {
        return None;
    }
    (0..text.len())
        .step_by(2)
        .map(|i| u8::from_str_radix(text.get(i..i + 2)?, 16).ok())
        .collect()
}

#[async_trait]
pub trait SessionStore: Send + Sync {
    async fn insert(&self, session: &Session) -> anyhow::Result<()>;
    async fn get(&self, id_hash: &[u8]) -> anyhow::Result<Option<Session>>;
    async fn touch(&self, id_hash: &[u8], at: OffsetDateTime) -> anyhow::Result<()>;
    async fn set_rank(&self, id_hash: &[u8], rank: Rank, at: OffsetDateTime) -> anyhow::Result<()>;
    async fn revoke(&self, id_hash: &[u8], at: OffsetDateTime) -> anyhow::Result<()>;
    /// Revokes every live session of `account_id`; returns how many.
    async fn revoke_account(&self, account_id: i64, at: OffsetDateTime) -> anyhow::Result<u64>;
    /// Live (not revoked) sessions created after `since`, newest first; one account or, with `None`, everyone.
    async fn live(&self, account_id: Option<i64>, since: OffsetDateTime) -> anyhow::Result<Vec<Session>>;
    async fn healthy(&self) -> bool;
}

pub struct PgSessions(pub sqlx::PgPool);

#[async_trait]
impl SessionStore for PgSessions {
    async fn insert(&self, s: &Session) -> anyhow::Result<()> {
        sqlx::query!(
            "INSERT INTO panel.sessions (id_hash, account_id, username, display_name, rank, ip, user_agent, csrf, \
             created_at, last_seen_at, rank_checked_at) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11)",
            s.id_hash,
            s.account_id,
            s.username,
            s.display_name,
            s.rank.stored(),
            s.ip,
            s.user_agent,
            s.csrf,
            s.created_at,
            s.last_seen_at,
            s.rank_checked_at,
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }

    async fn get(&self, id_hash: &[u8]) -> anyhow::Result<Option<Session>> {
        let row = sqlx::query!(
            "SELECT id_hash, account_id, username, display_name, rank, ip, user_agent, csrf, created_at, last_seen_at, \
             rank_checked_at, revoked_at FROM panel.sessions WHERE id_hash = $1",
            id_hash
        )
        .fetch_optional(&self.0)
        .await?;
        Ok(row.map(|r| Session {
            id_hash: r.id_hash,
            account_id: r.account_id,
            username: r.username,
            display_name: r.display_name,
            rank: Rank::parse(&r.rank).unwrap_or(Rank::Player),
            ip: r.ip,
            user_agent: r.user_agent,
            csrf: r.csrf,
            created_at: r.created_at,
            last_seen_at: r.last_seen_at,
            rank_checked_at: r.rank_checked_at,
            revoked_at: r.revoked_at,
        }))
    }

    async fn touch(&self, id_hash: &[u8], at: OffsetDateTime) -> anyhow::Result<()> {
        sqlx::query!(
            "UPDATE panel.sessions SET last_seen_at = $2 WHERE id_hash = $1",
            id_hash,
            at
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }

    async fn set_rank(&self, id_hash: &[u8], rank: Rank, at: OffsetDateTime) -> anyhow::Result<()> {
        sqlx::query!(
            "UPDATE panel.sessions SET rank = $2, rank_checked_at = $3 WHERE id_hash = $1",
            id_hash,
            rank.stored(),
            at
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }

    async fn revoke(&self, id_hash: &[u8], at: OffsetDateTime) -> anyhow::Result<()> {
        sqlx::query!(
            "UPDATE panel.sessions SET revoked_at = $2 WHERE id_hash = $1 AND revoked_at IS NULL",
            id_hash,
            at
        )
        .execute(&self.0)
        .await?;
        Ok(())
    }

    async fn revoke_account(&self, account_id: i64, at: OffsetDateTime) -> anyhow::Result<u64> {
        let done = sqlx::query!(
            "UPDATE panel.sessions SET revoked_at = $2 WHERE account_id = $1 AND revoked_at IS NULL",
            account_id,
            at
        )
        .execute(&self.0)
        .await?;
        Ok(done.rows_affected())
    }

    async fn live(&self, account_id: Option<i64>, since: OffsetDateTime) -> anyhow::Result<Vec<Session>> {
        let rows = sqlx::query!(
            "SELECT id_hash, account_id, username, display_name, rank, ip, user_agent, csrf, created_at, last_seen_at, \
             rank_checked_at, revoked_at FROM panel.sessions WHERE revoked_at IS NULL AND created_at > $2 \
             AND ($1::bigint IS NULL OR account_id = $1) ORDER BY created_at DESC LIMIT 500",
            account_id,
            since
        )
        .fetch_all(&self.0)
        .await?;
        Ok(rows
            .into_iter()
            .map(|r| Session {
                id_hash: r.id_hash,
                account_id: r.account_id,
                username: r.username,
                display_name: r.display_name,
                rank: Rank::parse(&r.rank).unwrap_or(Rank::Player),
                ip: r.ip,
                user_agent: r.user_agent,
                csrf: r.csrf,
                created_at: r.created_at,
                last_seen_at: r.last_seen_at,
                rank_checked_at: r.rank_checked_at,
                revoked_at: r.revoked_at,
            })
            .collect())
    }

    async fn healthy(&self) -> bool {
        crate::db::healthy(&self.0).await
    }
}

#[derive(Default)]
pub struct MemorySessions(Mutex<Vec<Session>>);

impl MemorySessions {
    fn with<T>(&self, f: impl FnOnce(&mut Vec<Session>) -> T) -> T {
        let mut all = self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        f(&mut all)
    }
}

#[async_trait]
impl SessionStore for MemorySessions {
    async fn insert(&self, s: &Session) -> anyhow::Result<()> {
        self.with(|all| all.push(s.clone()));
        Ok(())
    }

    async fn get(&self, id_hash: &[u8]) -> anyhow::Result<Option<Session>> {
        Ok(self.with(|all| all.iter().find(|s| s.id_hash == id_hash).cloned()))
    }

    async fn touch(&self, id_hash: &[u8], at: OffsetDateTime) -> anyhow::Result<()> {
        self.with(|all| {
            for s in all.iter_mut().filter(|s| s.id_hash == id_hash) {
                s.last_seen_at = at;
            }
        });
        Ok(())
    }

    async fn set_rank(&self, id_hash: &[u8], rank: Rank, at: OffsetDateTime) -> anyhow::Result<()> {
        self.with(|all| {
            for s in all.iter_mut().filter(|s| s.id_hash == id_hash) {
                s.rank = rank;
                s.rank_checked_at = at;
            }
        });
        Ok(())
    }

    async fn revoke(&self, id_hash: &[u8], at: OffsetDateTime) -> anyhow::Result<()> {
        self.with(|all| {
            for s in all
                .iter_mut()
                .filter(|s| s.id_hash == id_hash && s.revoked_at.is_none())
            {
                s.revoked_at = Some(at);
            }
        });
        Ok(())
    }

    async fn revoke_account(&self, account_id: i64, at: OffsetDateTime) -> anyhow::Result<u64> {
        Ok(self.with(|all| {
            let mut n = 0;
            for s in all
                .iter_mut()
                .filter(|s| s.account_id == account_id && s.revoked_at.is_none())
            {
                s.revoked_at = Some(at);
                n += 1;
            }
            n
        }))
    }

    async fn live(&self, account_id: Option<i64>, since: OffsetDateTime) -> anyhow::Result<Vec<Session>> {
        let mut out: Vec<Session> = self.with(|all| {
            all.iter()
                .filter(|s| {
                    s.revoked_at.is_none() && s.created_at > since && account_id.is_none_or(|a| s.account_id == a)
                })
                .cloned()
                .collect()
        });
        out.sort_by_key(|s| std::cmp::Reverse(s.created_at));
        Ok(out)
    }

    async fn healthy(&self) -> bool {
        true
    }
}
