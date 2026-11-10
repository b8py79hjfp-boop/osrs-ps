# Lotus control panel — plan

**Lotus runs on Windows only** (the owner's rule, 2026-11-10): the VPS, the installer, the services and the panel's
CI build are all Windows. Linux is neither supported nor needed; the brief's Linux terms below are only mapped to
their Windows equivalents.

Brief: [brief.md](brief.md) (saved verbatim). This plan adapts it to the live VPS, **Windows Server 2025**, on the
owner's instruction (2026-11-10). The game moves from the JSON file store to **PostgreSQL**, with the hub as its own
process. Where the brief names a Linux mechanism, the table below gives the Windows equivalent. Everything else in the
brief stands as written. The decision is recorded in docs/DECISIONS.md D-100.

## 1. Windows adaptation

| Brief (Linux) | Windows Server 2025 | Notes |
|---|---|---|
| systemd units `lotus-*.service`, `Restart=on-failure` | Windows services `lotus-hub`, `lotus-world-<n>`, `lotus-panel` registered with the Service Control Manager (SCM). The game units are hosted by WinSW: its XML holds the arguments, `stopparentprocessfirst` (Ctrl+C, so the JVM shutdown hook saves everyone), `onfailure restart` with a 10 s delay, and a clean exit (code 0) leaves the service stopped. | WinSW (MIT) instead of NSSM: its config is a file the install script writes and the panel can read. It also rotates logs. |
| `zbus` → systemd over D-Bus | The `windows-service` crate (SCM API: open, query, start, stop, query config). Status comes from `QueryServiceStatusEx`; crash counts come from the panel's own job log plus the WinSW wrapper log. | Still never shells out. |
| polkit rule: only `lotus-*.service` | The panel runs as the virtual account `NT SERVICE\lotus-panel`. The install script grants it SERVICE_START, SERVICE_STOP and SERVICE_QUERY_* on each `lotus-*` service only (a service security descriptor set with `sc sdset`) and nothing on any other service. | It isn't an Administrator and has no other service rights. |
| journald logs | WinSW writes `<unit>.out.log` / `.err.log` to `C:\lotus\logs\<unit>\`, rolled by size, and the game's Logback writes JSON lines there too. The panel tails and searches these files. The panel's own logs go to its own directory. | The Event Log is used only for the panel's own start/stop and fatal errors. |
| `LoadCredential=` secrets | Secret files under `C:\lotus\panel\secrets\`, each encrypted with DPAPI (`CryptProtectData`, machine scope plus a per-file entropy) and readable only by `NT SERVICE\lotus-panel` (ACL). `lotus-panel secret set <name>` writes them, run elevated once. | Same rule as the brief: never in the TOML file. |
| `ProtectSystem`, `ProtectHome`, `NoNewPrivileges`, … | Virtual service account, no admin group. NTFS ACLs: read-only on `C:\lotus\releases`; read-write only on `C:\lotus\panel\data`, `C:\lotus\logs`, `C:\lotus\dumps`, `C:\lotus\current` (the junction) and the backup directory. A Job Object caps panel memory (the brief's `MemoryMax`). Service SID type restricted, and the service gets only the privileges it needs (`SeChangeNotifyPrivilege`). | The verifier checks this list in phase 9. |
| `/opt/lotus/releases/<v>`, `current` symlink | `C:\lotus\releases\<v>\`, and `C:\lotus\current` as a **directory junction**. Junctions need no symlink privilege; switching one is a rename of a new junction over the old, so the switch is atomic. | |
| Caddy on 443 | Caddy for Windows as its own service (`lotus-caddy`), automatic HTTPS. Without a domain, Let's Encrypt can still issue a short-lived certificate for the bare IP 194.238.27.209; Caddy supports this. Fallback: Tailscale-only access (`allowed_networks`). | Open question for the owner, phase 2: domain, IP certificate or Tailscale. |
| `pg_dump` + `age` | PostgreSQL 17 for Windows (EDB installer, as a service). The panel runs `pg_dump.exe` (custom format) through `std::process::Command` (the one allowed process: a named, absolute binary path from config, no shell). It encrypts with the `age` crate. | §7 unchanged otherwise. |
| `jcmd` for dumps and JFR | `jcmd.exe` from the JDK, run by the world service itself through the game's admin RPC (below), so it runs as the service user. | |
| Firewall: only 443 | Windows Defender Firewall: allow 443 (Caddy), 43594 and 8080 (game, unchanged). Hub gRPC (43600) and panel gRPC (50051) bind to 127.0.0.1 only. The panel's read-only check lists the firewall's inbound allow rules. | Opening any other port requires asking the owner first (brief §0.6). |
| "Running anything as root" | Running anything elevated (Administrator). The install script needs elevation once (create services, ACLs, firewall rules). It is the owner's to run, and the panel never asks for it. | |

## 1a. Decisions made in phase 2 (2026-11-10)

- **Service wrapper: Shawl, not WinSW.** Shawl (MIT, one exe, pinned by SHA-256) sends Ctrl+C on stop and restarts on
  any other exit, including the clean exit after `::update`. That matches the brief's `Restart=on-failure` plus the
  game's update flow. WinSW leaves a service stopped after exit code 0.
- **Secrets: ACL-protected files, without DPAPI.** Machine-scope DPAPI adds nothing over an ACL against a local
  Administrator. User-scope DPAPI for a virtual account would need the secret to be written from inside the service.
  It also needs Win32 calls that `forbid(unsafe_code)` rules out in our crates. Each secrets directory has inheritance
  removed and grants only Administrators, SYSTEM and the one service account.
- **HTTPS: `lotusps.duckdns.org`** (the owner's DuckDNS name). Caddy gets the certificate through TLS-ALPN on 443
  (`disable_http_challenge`), so port 80 stays closed. Caddy's admin API is off.
- **Database: fresh.** The owner chose a new PostgreSQL database over importing the JSON store. The old file is left
  in place and isn't read. The hub runs standalone (`lotus-hub`), and the world connects to it with `--hub`. The first
  developer comes from `--bootstrap`.
- **Panel binary: built by CI** (`.github/workflows/panel.yml`, Windows runner, published as release
  `panel-latest` with its SHA-256). The VPS needs no Rust or Visual Studio toolchain. Signing (minisign) arrives with
  deploys in phase 7.

- **No two-factor authentication** (the owner's decision, 2026-11-10; brief §2 overridden). Staff sign in with their
  game account name and password through `VerifyStaff`. Wherever the brief asks for the 2FA code again (restart, stop,
  emergency stop, deploy, rollback, rank changes, bans, restore, reset of another user's sessions), the panel asks for
  the **password again** instead; it is checked through `VerifyStaff`, so it is throttled and audited the same way. The
  rest of the brief's protections stay: per-IP and per-account lockout, alerts on repeated failures, short sessions,
  CSRF, the 60 s rank re-check, and the optional `allowed_networks` (for example Tailscale) to keep the panel off the
  public internet entirely.

## 2. What the hub already offers

- **Accounts:** `Accounts.Login` creates a game session, so the panel can't use it; there is no check of credentials
  alone. Passwords are hashed with Argon2id. Login is throttled per IP and per account.
- **Ranks:** `Rank` (player < moderator < administrator < owner < developer), `canTarget` and `canSetRank`.
  `Moderation.SetRank` enforces both and pushes the change to the player's world and to presence.
- **Moderation:**
  - `Moderation.Apply`, `History`, `Lookup` and `Investigate` all take a staff account and enforce rank, targeting and
    the administrator-only IP rule.
  - The records live in `moderation_records`.
  - There is no listing of active sanctions across all accounts.
- **Audit:** `Moderation.Audit(actor, action, target, detail)` writes to `audit_log`. There are no source, IP or result
  fields. The only query is `auditQuery(who, filter, 100)`.
- **Presence:** the `Broadcasts.Subscribe` stream (snapshot, then deltas) and `Worlds.List`. There is no
  request/response list of who is online.
- **Updates:** `Broadcasts.SystemUpdate(seconds)` runs the countdown on every world. `0` cancels it (OPEN-CMD9). At
  zero each world saves everyone and the process exits 0, so "stop" and "update" are the same thing. No actor is
  attached, and the hub doesn't check rank on it.
- **Broadcast and yell:** `Broadcasts.Broadcast(text)` and `Yell`.
- **Health:** each world node has `:9090/health` (200 or 503 with the reasons) and `/metrics` (Prometheus: players,
  ticks, missed deadlines, tick ms per phase, saves). The hub has `/health`.
- **Commands:** the `::` pipeline (rate limit, lookup, arguments, targeting, confirmation, run, audit) runs on the world
  thread with a `PlayerRef` actor. There is no way to run a command for someone who isn't logged in.
- **Transport:** the hub's gRPC listens on all interfaces on 43600, in plaintext, with no client authentication. It
  is built for world nodes on a private network.

## 3. Gap list: what the hub needs (Kotlin)

Every gap below is a new `Panel` gRPC service on a **separate listener**. It is bound to `127.0.0.1:50051` and requires
**mTLS**: the client certificate must chain to the panel CA. That listener serves the `Panel` service and nothing
else, so the panel's certificate reaches only panel RPCs (§8). The world-facing listener moves to 127.0.0.1 by default
(`--grpc-bind` to change it).

The hub re-checks rank on every Panel RPC from the actor's stored rank, so a compromised panel can do no more than its
signed-in staff member.

| # | RPC | Why | Phase |
|---|---|---|---|
| 1 | `VerifyStaff(username, password, ip) → result, account, name, rank` | Panel login (§2). It uses the same throttles and bans as game login, creates **no** game session, refuses below moderator, and is audited (`panel login`, ok or failed). | 1 |
| 2 | `GetRank(account) → rank, banned` | The 60 s re-check (§2). | 1 |
| 3 | `Audit(actor, action, target, detail, source, ip, result)` | §8. `audit_log` gains `source`, `ip` and `result` columns, an additive migration in all three stores. | 1 |
| 4 | `QueryAudit(actor, target, action, source, text, since, until, before_id, limit)` | Audit page, filters and CSV export (§4). Owner and above. | 1 |
| 5 | `ListOnline() → presence rows` | Players page and dashboard. Moderator and above. Invisible staff are hidden from lower ranks, as in game. | 1 |
| 6 | `ListWorlds() → worlds + locked + last heartbeat age` | Dashboard and Worlds. | 1 |
| 7 | `SetLoginLock(actor, world (0 = all), locked, message)` and `GetLoginLock` | Maintenance mode (§3). When locked, the hub refuses logins below moderator. The client shows its standard "being updated" line, the only login text the 242 client allows. The world list shows the world's activity as the configured message. Owner and above. | 1 |
| 8 | `SystemUpdate(actor, seconds, message)` and `CancelUpdate(actor)` | Graceful stop/restart with an actor and a rank check (owner and above). The optional message is broadcast first. Audited. | 1 |
| 9 | `Broadcast(actor, text, world)` | Chat and broadcast page, administrator and above (§4). This needs a world filter on broadcast events. | 1 |
| 10 | `SetRank`, `ApplySanction`, `History`, `Lookup`, `Investigate` with actor and source PANEL | §5. These reuse the existing moderation code paths unchanged. The panel source and IP are added to the audit row. Every view of an IP or alt link is audited. | 1 |
| 11 | `ListSanctions(type, actor, active_only, expiring_before, limit)` | Moderation page. This needs a new store query. | 1 |
| 12 | `ListStaff()` | Staff page. This needs a new store query (rank ≥ moderator). | 1 |
| 13 | `ExecuteCommand(actor, line, source = PANEL) → stream lines` | Console and the player actions (§5). It needs **a world-side remote actor**: the pipeline today needs a logged-in `PlayerRef`. The hub routes the call to a world (the target's world, or world 1). The world runs the same pipeline as a remote staff actor with the hub-checked rank. That actor may run only commands marked `remoteSafe`: target-based moderation, info and operations; never anything that needs the actor's own position or inventory. It returns output lines. This is plugin-api and engine work for the main session. | 6 |
| 14 | `Diagnostics(actor, world, kind)`: thread dump, heap dump, JFR start/stop, tick-inspector capture | §3, developer only. The hub routes it to the world, which runs it in-process (`ManagementFactory`, `HotSpotDiagnosticMXBean.dumpHeap`, `jdk.jfr.Recording`). jcmd isn't needed after all. | 4 |
| 15 | `Tickets` and `Reports` listings | The Moderation page queue. The tickets are world-side today (`CommandTickets`). Moving them to the hub is part of #13's work. Reports: OPEN, since the game doesn't record report-abuse submissions yet. | 6 |

Already enough: health and metrics (scraped directly from `127.0.0.1:9090+` and the hub's `/health`), crash detection
(SCM state plus the job log) and the start order (PostgreSQL service, then hub, then worlds).

## 4. Risks

- **Windows hardening is weaker than the brief's systemd set.** Mitigations: the virtual service account; per-service
  SDDL rights; NTFS ACLs; a Job Object; the hub's own rank checks; and mTLS-only panel RPCs. The verifier reviews §8 in
  phase 9.
- **Moving live data from JSON to PostgreSQL.** A one-time `importStore` task (FileStore → PostgresStore) copies
  everything, with counts compared before and after. A backup of the JSON file is kept. This is done during phase 2's
  install, before the panel goes live.
- **ExecuteCommand touches engine and plugin-api.** It is kept to a remote-actor adapter and a `remoteSafe` flag, and
  the game's command scenarios (test set §9) must stay green.
- **Bare-IP HTTPS.** IP certificates are short-lived (about 6 days). Caddy renews them automatically, but if renewal
  fails the panel is unreachable until it succeeds. The `ctl` CLI on the VPS is the fallback.
- **One VPS.** The panel, hub, worlds and database share 2 or more cores. The panel's scrape and SSE costs are capped
  (15 s scrape, bounded fan-out).
- **Password-only sign-in.** A leaked staff password is enough to get in. Mitigations: strong passwords (the game's
  rules), lockout, the password asked again for dangerous actions, audit of every panel login with its IP, owner
  alerts on failures, and `allowed_networks` when the panel should not be public at all.

## 5. Phases (brief §11) and done-when

1. **Plan and hub API.** Done 2026-11-10 (docs/PROGRESS.md "Control panel").
   - Done when:
     - this plan and DECISIONS D-100 are written;
     - RPCs #1–12 are in `hub.proto`, implemented in the hub and served on the mTLS listener (`--panel-port`,
       `--panel-cert`, `--panel-key`, `--panel-ca`);
     - the audit columns are added in all stores;
     - the login lock is enforced;
     - `./gradlew build` is green.
2. **Skeleton.** Done 2026-11-10, with a fresh database instead of `importStore` (§1a).
   - Done when:
     - the `panel/` Cargo workspace (toolchain pinned, edition 2024, `forbid(unsafe_code)`, clippy `-D warnings`,
       `cargo deny`) has typed config, an sqlx `panel` schema with migrations, assets embedded with `rust-embed`, and an
       askama + htmx shell;
     - `lotus-panel certs` (rcgen) makes the panel CA plus the hub and panel certificates;
     - `lotus-panel secret set` stores DPAPI secrets;
     - `deploy/windows/install.ps1` creates the services (WinSW XMLs), ACLs, SDDL rights and firewall rules, and the
       Caddyfile;
     - `importStore` moves the JSON store to PostgreSQL;
     - the panel starts as a service and serves a logged-out page over HTTPS.
3. **Auth.** Done 2026-11-10 (alerts on repeated failures go to the log until phase 8 adds the channels).
   - Done when:
     - login through `VerifyStaff` works (game account name and password; no second factor, §1a);
     - dangerous actions ask for the password again;
     - sessions have idle and absolute expiry and can be listed and revoked;
     - CSRF protection, per-route minimum ranks, the 60 s re-check, lockout and the owner bootstrap CLI are in place;
     - tests 1 (`permissions`) and 2 (`auth`) pass.
4. **Control.** Done 2026-11-10.
   - Done when:
     - start (database, hub, then worlds, each health-gated), graceful stop/restart (lock logins, countdown with cancel,
       wait for exit 0, reverse order), emergency stop, crash-loop detection, maintenance mode, scheduled restarts and
       diagnostics (#14) work;
     - the `lotus-panel ctl` twin exists;
     - test 3 (`control`) passes.
5. **Read-only pages.** Done 2026-11-10 (tickets and reports: phase 6 / OPEN).
   - Done when the dashboard (SSE), players, moderation, metrics, logs and audit pages work at phone width.
6. **Actions.** Done 2026-11-10 (D-101: tickets stay on their world, reached through `::tickets`; no report queue).
   - Done when:
     - ExecuteCommand (#13) and the remote actor are in place;
     - the console, moderation actions, broadcast, world lock and staff ranks work;
     - tickets are moved to the hub.
7. **Deploys.** Done 2026-11-10 (D-102). Not yet run on the VPS: junctions and the SCM path are Windows-only.
   - Done when:
     - releases are signed with minisign;
     - the diff, migration-with-backup, lock, countdown, junction switch, ordered restart, health check and rollback all
       work, with job rows;
     - test 4 (`deploy`) passes.
8. **Backups and alerts.** Done 2026-11-10 (D-103). Alert delivery to real Discord/SMTP and the live restore run
   only on the VPS.
   - Done when:
     - nightly `pg_dump` runs, encrypted with age and rotated 14/8/6, with optional S3 copies;
     - verify restores into a scratch database;
     - restore is developer-only;
     - Discord and email alerts work.
9. **Staging run.**
   - Done when:
     - `panel/docs/operations.md` covers install, upgrade, a forgotten staff password and restore;
     - the verifier has reviewed §8;
     - a full run has been done on a scratch Windows Server VM.

## 6. Not now (brief §13)

- A public player-facing website (hiscores, store, vote).
- Managing more than one VPS.
- A mobile app.
- In-browser editing of game data files.
