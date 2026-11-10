# Lotus control panel (Rust) — Claude Code prompt

Build a **web control panel** for **Lotus**, our RSPS. It runs on the VPS next to the game servers and is written in **Rust**. Staff use it to see what's running, manage worlds, players and moderation, deploy builds, and restart safely, without SSH.

The game itself is Kotlin: world-node JVMs plus a hub, using gRPC, PostgreSQL and Micrometer metrics. The panel is a separate Rust service. It talks to the game **through the hub** and to the machine through systemd. It never edits game state behind the server's back.

---

## 0. How to work

1. **Read first.** Read the game repo's `CLAUDE.md`, `docs/BRIEF.md`, `docs/PROGRESS.md` and `docs/DECISIONS.md`, and `docs/features/commands.md` if it exists. Use the hub's gRPC services and protobuf schemas, the audit log, the moderation records, and the rank model (player < moderator < administrator < owner < developer). Where something the panel needs doesn't exist yet, add it to the hub (in Kotlin, following the game repo's rules) instead of working around it.
2. **Where it lives.** Put the panel in `panel/` in the game repo, as its own Cargo workspace. Save this prompt verbatim as `panel/docs/brief.md`, then write `panel/docs/plan.md`: what the hub already offers, what's missing, the risks, and a done-when list for each phase in §11.
3. **Model policy.** BRIEF §0 item 8 applies.
   - **Main session (Opus 5.5, medium) owns:**
     - authentication, sessions and permissions;
     - the hub API additions;
     - the systemd and deploy actions;
     - audit, and every security item in §8.
   - **`implementer`** takes the read-only pages and UI components once the API is set.
   - **`verifier`** reviews §8 against the code without seeing your notes.
4. **Toolchain.** Use the latest stable Rust release as of today, pinned in `rust-toolchain.toml`, with edition 2024. Pin dependency versions in `Cargo.lock`.
   - **Clippy:** `-D warnings`.
   - **`cargo deny`** for licences and advisories.
   - **No `unsafe`:** `#![forbid(unsafe_code)]`.
5. **Commit and report.** Commit at each green milestone using conventional commits. Stop at the end of each phase and report.
6. **Stop and ask** before:
   - opening any port to the internet other than 443;
   - running anything as root;
   - adding an action that can create items, gold or XP;
   - deleting data, logs or backups.

## 1. Stack

- **Web server:** `axum` on `tokio`, with `tower-http` for compression, timeouts, request ids, security headers and rate limiting.
- **Game:** `tonic` gRPC client to the hub, using the hub's `.proto` files compiled with `tonic-build`. Talk to the hub over mTLS.
- **Database:** `sqlx` on PostgreSQL, with checked queries and migrations.
  - The panel has its own schema (`panel`) for panel sessions, panel settings and job history.
  - The panel reads game data, and gets the game to change anything, through the hub. It only writes to its own schema.
- **Frontend:** server-rendered HTML with `askama` templates and htmx for interactivity. Live views (console, logs, metrics) use Server-Sent Events.
  - CSS and JS are vendored and embedded in the binary with `rust-embed`, so there's no Node build and no CDN.
  - It works at phone width.
- **Machine:**
  - `zbus` talks to systemd over D-Bus to start, stop and restart units and to read their status. The panel never shells out.
  - Logs are read from the systemd journal.
  - Metrics come from the game's existing Prometheus/Micrometer endpoints, scraped locally.
- **Errors and logging:** `tracing` with JSON logs; `thiserror` in libraries and `anyhow` at the edges.
- **Config:** a typed TOML config with `serde`. Secrets come from systemd credentials (`LoadCredential=`), never from the TOML file.
- **Output:** one static binary, `lotus-panel`.

## 2. Access and accounts

- **Who can log in.** Moderator and above, signing in with their **game account**. The panel checks the password and rank through a hub RPC; it stores no game passwords.
- **Two-factor.**
  - Two-factor authentication is **required for every panel login**: TOTP, or WebAuthn passkeys (`webauthn-rs`).
  - If the game's accounts don't support 2FA yet, the panel enrols and stores it in its own schema, encrypted at rest.
- **Rank always comes from the hub.**
  - The rank is checked when a session is created and re-checked at least every 60 s, so a demotion takes effect within a minute.
  - Every page and action declares its minimum rank. The server checks it on every request; hiding a button is never the only protection.
- **Sessions.**
  - The cookie is `HttpOnly`, `Secure` and `SameSite=Strict`, with a server-side session row.
  - Sessions time out after 30 minutes idle and 12 hours absolute.
  - Each user can list and revoke their own sessions, and an owner can revoke anyone's.
- **CSRF and confirmation.**
  - Every state-changing request carries a CSRF token.
  - Dangerous actions ask for the 2FA code again: restart, deploy, rollback, rank changes, bans and anything else in §8.
- **Lockout.** Login attempts are rate-limited per IP and per account. Repeated failures lock the account for 15 minutes and alert owners.
- **First owner.** The first owner is bootstrapped with `lotus-panel bootstrap --account <name>` run on the VPS, matching the game's own bootstrap rule. There's no web path to it.

## 3. Server control (build this first)

The panel's core job is starting and stopping the server. Build this first, before any other page.

- **Big buttons on the dashboard.** Owner and above get **Start**, **Stop**, **Restart** and **Emergency stop**, for everything or for one unit (hub or a world). Each button shows the unit's live state: running, starting, stopping, stopped or failed.
  - **Start** brings units up in order: database check, then the hub, then the worlds. A world isn't marked running until it accepts logins and ticks within budget.
  - **Stop** and **Restart** are graceful by default. They lock logins, run the in-game update timer (length chosen in the dialog, default 5 minutes, with an optional message), let the game save everyone, then stop the units in reverse order.
  - **Emergency stop** skips the countdown. It sends the game's "save all, then shut down" call and only falls back to a systemd stop after a timeout. It needs the 2FA code and a typed confirmation, and alerts owners.
  - A stop can be cancelled while the timer is running.
- **Crash handling.**
  - Units restart automatically through systemd (`Restart=on-failure`).
  - The panel detects a crash loop (3 crashes in 10 minutes), stops retrying, alerts owners, and shows the last 200 log lines and the exit reason.
- **Scheduled restarts.** Optional daily or weekly restarts, with the usual countdown, set in the Worlds page. A schedule is skipped if a deploy is running.
- **Maintenance mode.** Lock logins on every world to staff only. The world list shows the worlds as locked, and players get a configurable message.
- **Diagnostics** (developer). Use these when a world misbehaves, without taking it down:
  - take a thread dump;
  - take a heap dump, written to disk and listed for download;
  - start and stop a JFR recording (default 2 minutes);
  - trigger the tick inspector's capture.

  All of these go through the game's admin RPC or `jcmd`, run as the service user.
- **Housekeeping** (owner):
  - delete old releases, keeping the newest 5 and never the current or previous one;
  - see disk use by logs, dumps, backups and releases, and clear dumps older than 7 days;
  - see the TLS certificate's expiry and the firewall's open ports, as read-only checks.
- **CLI twin.** Every control action is also available as `lotus-panel ctl <start|stop|restart|status|lock|unlock> [unit]` on the VPS. It uses the same code, rules and audit, for when the web panel is unreachable. It runs as the panel user and needs a local owner token file readable only by that user.

## 4. Pages

Each page shows its minimum rank; anything above the viewer's rank isn't shown at all.

| Page | Rank | Contents |
|---|---|---|
| **Dashboard** | Moderator | Every world: online, locked, starting or stopped, plus players, tick time p50/p99 against budget, uptime and version. Also the hub's status, CPU, memory, disk and network for the VPS, and the latest alerts. Live via SSE. |
| **Players** | Moderator | Online players across all worlds, with search and filters by world, rank and status. The player card shows rank, world, account age, status flags, moderation history and notes. Actions follow the command system's rules (§5). IP addresses, device ids and alts are visible to administrators and above only. |
| **Moderation** | Moderator | Active mutes, jails and bans, filtered by type, staff member and expiry; lift or extend them. Also the help-ticket queue and the report queue, if the game records reports. |
| **Console** | Moderator | Runs a game command as yourself (§5), with the output streamed back. |
| **Chat & broadcast** | Moderator | Reading yell and staff chat. Administrators and above can send a broadcast to one world or all. |
| **Audit log** | Owner | The game's audit log plus the panel's own entries, searchable by staff member, player, action, world and time. Export to CSV. |
| **Worlds** | Owner | Per world: lock or unlock logins, start, stop, graceful restart with the in-game update timer, and toggle commands and raids per world. |
| **Deploys** | Owner | Upload or select a build, see what changed, deploy, and roll back (§6). |
| **Logs** | Owner | Live and searchable journal logs per unit (worlds, hub, panel, database), filtered by level and time. |
| **Metrics** | Moderator | Charts of players, tick time per phase, GC pauses, network, raids and instances, and phasing, from the scraped metrics. Retention follows §7. |
| **Backups** | Owner | List, run now, download and verify backups; restore is developer-only (§7). |
| **Staff** | Owner | Staff accounts and ranks: set ranks through the hub (owners up to administrator, developers anything), revoke sessions and reset 2FA. |
| **Settings** | Developer | Panel config, alert targets, and feature flags that are safe to change at runtime. |

## 5. Actions go through the game

- **One set of rules.** Every player or moderation action in the panel calls the hub. The hub runs it through the **same command pipeline as `::` commands**, as the logged-in staff member: rank check, targeting rule, world policy, confirmation and audit. So a mute from the panel and `::mute` in game are the same thing with the same limits. Add a hub RPC `ExecuteCommand(actor, command line, source = PANEL)` if it doesn't exist.
- **Console.** The console page sends command lines to that same RPC. Output streams back over SSE.
- **No economy actions on live.** Nothing in the panel creates items, gold or XP on a live world, because the game's world policy refuses it. The panel doesn't add a way around that.
- **No direct writes.** The panel never writes to the game database tables directly.

## 6. Deploys and restarts

- **Releases on disk.**
  - Builds live in `/opt/lotus/releases/<version>/`, with `/opt/lotus/current` a symlink to the active one.
  - The panel uploads a build artifact or picks one from CI, and checks its SHA-256 and signature (minisign or Sigstore, whichever the game repo's release pipeline uses) before anything else.
- **Deploy flow:**
  1. Show the diff: version, commits and migrations.
  2. Run database migrations with a backup taken first.
  3. Lock the worlds.
  4. Start the in-game update timer (default 5 minutes).
  5. The game saves everyone and stops gracefully.
  6. Switch the symlink.
  7. Restart the hub, then the worlds, through systemd.
  8. Health-check each one (it accepts logins and ticks within budget), then unlock.
- **Rolling deploys.** With more than one world-node, deploy one node at a time. Players on a node get the update timer and can hop to a world that's still up.
- **Rollback.** Switch back to the previous release, through the same flow. A migration that can't be reversed blocks one-click rollback, and the panel says so before the deploy starts.
- **Health and history.** A failed health check stops the rollout and leaves the remaining nodes on the old version. Every step is a job row with logs, and the deploy page streams progress.
- **Least privilege.**
  - The panel runs as its own `lotus-panel` user, never root.
  - systemd access is limited by a polkit rule to managing only `lotus-*.service` units.
  - File access is limited to `/opt/lotus` and the backup directory.

## 7. Backups, metrics and alerts

- **Database backups.** A nightly `pg_dump` (custom format) is compressed and encrypted with `age` to an owner-held public key. Keep 14 daily, 8 weekly and 6 monthly.
- **Off-site.** Copy each backup off-site to an S3-compatible bucket, if one is configured.
- **Verify.** "Verify" restores the latest backup into a scratch database and runs a sanity query.
- **Restore** to live is developer-only. It needs the 2FA code and a typed confirmation, and locks all worlds first.
- **Metrics.** Scrape every 15 s and store in the panel schema, downsampled to 1-minute after 24 hours and kept 30 days. If the VPS already runs Prometheus, read from it instead and say so in the plan.
- **Alerts** go to a Discord webhook and/or email, configured in Settings. They fire when:
  - a world or the hub is down;
  - tick p99 is over budget for 5 minutes;
  - disk is over 85%;
  - a backup fails;
  - repeated panel login failures happen;
  - a deploy fails.

## 8. Security

- **One public port.** Only 443 is open, through Caddy with automatic HTTPS. Caddy proxies to the panel on `127.0.0.1`.
  - Optionally restrict the panel to a VPN or Tailscale address in config; then nothing is public at all.
  - The game ports are separate and unchanged.
- **Hub connection.** The hub's gRPC listens only on localhost or the private network, with mTLS. The panel's client certificate allows only the panel's RPCs.
- **Headers.** A strict CSP with no inline script (htmx works with that), HSTS, `X-Frame-Options: DENY` and `Referrer-Policy: same-origin`.
- **Untrusted text.** Every string from players (names, chat, reasons, notes) is escaped by askama. That includes OSRS text tags, which must not pass through as HTML.
- **Privacy.** IPs, device ids and alt links are shown to administrators and above only, and every view of them is audited.
- **Audit.** Every panel action writes to the game's audit log through the hub with source `PANEL`: staff member, IP, action, target, result and time. Logins, failures, 2FA changes and session revocations are logged too.
- **Process hardening.** A systemd unit hardened with `NoNewPrivileges`, `ProtectSystem=strict`, `ProtectHome`, `PrivateTmp`, `ReadWritePaths` for only what it needs, `CapabilityBoundingSet=` empty, and `MemoryMax`.
- **Dependencies.** `cargo audit` and `cargo deny` run in CI and fail the build on known advisories.

## 9. Config (`/etc/lotus-panel/panel.toml`; secrets via systemd credentials)

```toml
[server]
bind = "127.0.0.1:8088"
public_url = "https://panel.example.com"
allowed_networks = []                 # e.g. ["100.64.0.0/10"] for Tailscale only

[hub]
endpoint = "https://127.0.0.1:50051"
ca = "/etc/lotus-panel/ca.pem"         # client cert and key come from credentials

[database]
url_credential = "panel-db-url"

[sessions]
idle_minutes = 30
absolute_hours = 12

[deploy]
releases_dir = "/opt/lotus/releases"
update_timer_seconds = 300
units = { hub = "lotus-hub.service", worlds = "lotus-world@*.service" }

[backups]
dir = "/var/backups/lotus"
age_recipient = "age1..."
keep = { daily = 14, weekly = 8, monthly = 6 }

[alerts]
discord_webhook_credential = "panel-discord-webhook"
```

## 10. Tests (minimal)

1. **`permissions`:** every route, tried as each rank (and logged out), returns the right result. This test is generated from the route table, so a new route can't skip it.
2. **`auth`:** login with and without 2FA, lockout, CSRF rejection, idle and absolute expiry, and a demotion taking effect at the next re-check.
3. **`control`:** against a fake systemd and a fake hub, start brings units up in order, a graceful stop runs the countdown and saves before stopping, and a crash loop stops retrying.
4. **`deploy`:** against the same fakes, a deploy runs every step in order. A failed health check stops it and leaves the remaining nodes on the old version. Rollback restores the symlink.

Nothing else. Do a manual check on a staging VPS before the first real deploy.

## 11. Order of work

1. Plan; the hub API gap list, with the missing RPCs added in Kotlin.
2. Skeleton: config, database, embedded assets, the hardened unit, Caddy config and an install script.
3. Authentication, 2FA, sessions and the permission layer, plus tests 1 and 2.
4. Server control (§3) and the `ctl` CLI: start, stop, restart, emergency stop, crash-loop detection and maintenance mode.
5. Read-only pages: dashboard, players, moderation, metrics, logs and audit.
6. Actions through the game: moderation, console, broadcast, world lock and staff ranks.
7. Deploys, restarts and rollback, plus test 4.
8. Backups and alerts.
9. A staging run. Write `panel/docs/operations.md`: install, upgrade, recovering a lost 2FA and restoring a backup.

## 12. Done when

- An owner with 2FA can see every world live, act on players with the same results as in-game commands, deploy a new build with a graceful in-game countdown, and roll it back.
- A moderator sees only moderator pages and actions. A demoted user loses access within a minute.
- Backups run nightly, are encrypted, and are verified by a test restore.
- Only port 443 is open. The panel runs as an unprivileged user and passes `cargo audit` and `cargo deny`.
- The §10 tests pass, and the game's own tests are unchanged.

## 13. Not now

List these in `panel/docs/plan.md`:

- a public player-facing website (hiscores, store, vote);
- managing more than one VPS;
- a mobile app;
- in-browser editing of game data files.
