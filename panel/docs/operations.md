# Lotus panel: operations guide

For the owner of the Lotus VPS (Windows Server 2025). It covers install, upgrades, deploys and rollback, a forgotten
staff password, backups and restore, alerts, and what to do when the panel itself is down. Commands are PowerShell,
run as Administrator on the VPS unless a step says otherwise.

## 1. What runs where

| Service | What it is | Listens on |
|---|---|---|
| `lotus-postgresql` | The database (`lotus` for the game, `lotus_panel` for the panel) | 127.0.0.1:5432 |
| `lotus-hub` | Accounts, saves, world list, website and client download; the panel's API (mTLS) | :8080 public, 127.0.0.1:43600 and :50051 |
| `lotus` | Game world 1 | :43594 public, 127.0.0.1:9090 (health and metrics) |
| `lotus-panel` | The control panel | 127.0.0.1:8088 |
| `lotus-caddy` | HTTPS for the panel, `https://lotusps.duckdns.org` | :443 public |

Each runs under its own restricted Windows account (`NT SERVICE\<name>`), never Administrator. Only 43594, 8080 and
443 are open in Windows Firewall.

| Folder | Holds |
|---|---|
| `C:\lotus\game` | The source checkout, the game cache and the login key (`.data`) |
| `C:\lotus\releases\<version>` | Each installed or deployed release; `incoming` for hand-copied ones |
| `C:\lotus\nodes\<service>` | One junction per service, pointing at the release it runs |
| `C:\lotus\backups` | Encrypted backups (`*.dump.age`) and the panel database's daily dump |
| `C:\lotus\logs\<service>` | Each service's log (`<service>_rCURRENT.log`), 5 × 10 MB kept |
| `C:\lotus\pki`, `C:\lotus\pg` | Certificates, the release signing key, database passwords, the panel database's daily dump: Administrators only |
| `C:\lotus\link` | The token the hub and world use to talk to each other (only they can read it) |
| `C:\lotus\panel` | `panel.toml` and the panel's secret files (`secrets`: the panel's account only) |

## 2. Install

1. Point `lotusps.duckdns.org` at the VPS on duckdns.org.
2. If the VPS provider has its own firewall, open TCP 43594, 8080 and 443 there.
3. Run the one-command installer (docs/VPS.md):

   ```powershell
   irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/install-lotus.ps1 | iex
   ```

   It asks for the owner's account name once. That account becomes a developer at its first sign-in to the game or
   the panel.
4. Nothing else is needed for deploys: the installer made this server's release signing key, and
   `build-release.ps1` (section 3) uses it. (The optional GitHub `game-release` workflow needs that key copied into
   two repository secrets, `LOTUS_RELEASE_KEY` and `LOTUS_RELEASE_KEY_PASSWORD`.)
5. Sign in at `https://lotusps.duckdns.org` with the owner's game account. Then, on **Settings**:
   - **Make an owner key** for backups and keep the downloaded `lotus-backup-key.txt` off the server (a password
     manager or a USB stick);
   - add a **Discord webhook** and/or email for alerts, then press **Send a test alert**.

## 3. Upgrades

There are two ways. Both keep players' data, and both are logged.

- **From the panel (normal).**
  1. On the VPS, in PowerShell opened as Administrator, run
     `irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/build-release.ps1 | iex`.
     It pulls the latest code, builds it and signs it with this server's key into the incoming folder (about 10
     minutes; the game keeps running). With GitHub access, Actions › game-release › Run workflow does the same.
  2. On the panel's **Deploys** page, press **Check the incoming folder** (or **Download and verify** for a GitHub
     build). The panel refuses anything whose signature doesn't match.
  3. Open the release. It shows the commits and any database changes; set the timer and press **Deploy**.
  4. Players get the in-game countdown and are saved. The hub and then each world switch over, each one
     health-checked. If one fails, it goes back to the old release and the others stay there too.
- **Re-running the installer.** It pulls the latest code, builds it on the VPS, installs it as release
  `src-<commit>` and restarts everything. There is no countdown: warn players first with `::update 60`. It also
  updates the panel, Caddy settings and permissions, so run it after an update that changes the installer.

**Rollback.** Deploys › the previous release › **Roll back**. It is the same flow as a deploy. If a newer release
changed data in a way the older one can't run on, the page says so and points to restoring a backup instead.

## 4. A forgotten password

Staff sign in to the panel with their game account and password; there is no separate panel password and no 2FA.

- **Staff member (not the owner).** An owner can sign them out everywhere (Staff sessions) at once. To set a new
  password, run on the VPS:

  ```powershell
  $pw = 'C:\lotus\hub\db-password'
  & C:\lotus\tools\jdk25\bin\java.exe -cp 'C:\lotus\nodes\lotus-hub\hub\lib\*' tp.hub.AdminKt `
      --setPassword=Their_Name --db=jdbc:postgresql://127.0.0.1:5432/lotus --dbUser=lotus_hub --dbPasswordFile=$pw
  ```

  It asks for the new password twice and writes an audit row. Give them the password privately; they can change
  it in game with `::changepassword`.
- **The owner.** Same command, with your own name. If the owner account lost its rank, set it back with
  `--setRank=Your_Name:DEVELOPER` instead of `--setPassword`.
- **Repeated failed sign-ins** lock the account (or the address) for 15 minutes and send an alert.

## 5. Backups and restore

- **Automatic.** Every night at 04:00 UTC (Settings › Backups changes the time) the game database is dumped,
  encrypted to the panel's key and every owner key, and rotated. It keeps 14 daily, 8 weekly and 6 monthly backups,
  plus the last 5 taken before deploys. An S3-compatible bucket (AWS, Backblaze B2, Cloudflare R2, Wasabi) on the
  Settings page gets a copy of each.
- **Check one.** Backups › **Verify** restores it into a scratch database, counts the accounts and drops it. The live
  game isn't touched. Do this after setting up and now and then.
- **Restore over the live game (developer).** Backups › Restore: pick the backup and type `RESTORE <name>` and your
  password. It locks logins, runs the in-game countdown, stops the worlds and the hub (everyone is saved), replaces
  the database, and starts everything again. Anything since the backup is lost, so take a fresh backup first if you
  can.
- **Without the panel** (for example, onto a new VPS):
  1. Install Lotus on the new VPS (section 2), then stop the game: `Stop-Service lotus, lotus-hub`.
  2. Download `age` for Windows from https://github.com/FiloSottile/age/releases.
  3. Decrypt with your owner key:

     ```powershell
     age.exe -d -i lotus-backup-key.txt -o lotus.dump lotus-20261110-040000.dump.age
     ```
  4. Restore it:

     ```powershell
     $env:PGPASSWORD = (Get-Content C:\lotus\hub\db-password -Raw).Trim()
     C:\lotus\tools\pgsql\bin\pg_restore.exe -h 127.0.0.1 -U lotus_hub -d lotus --clean --if-exists --no-owner --single-transaction lotus.dump
     Remove-Item Env:\PGPASSWORD
     ```
  5. Start the game: `Start-Service lotus-hub; Start-Service lotus`.

  The panel's own settings (alert targets, owner keys) are in `lotus_panel`, dumped daily to
  `C:\lotus\pg\lotus_panel-<date>.dump` (Administrators only). That dump is not encrypted, so copy it somewhere
  private.

## 6. Alerts

Alerts go to the dashboard and, once set up on the Settings page, to Discord and/or email. They fire when:

- a world or the hub stops unexpectedly (three times in ten minutes stops the retries and says so);
- tick time p99 is over budget for 5 minutes;
- the disk is over 85 % full;
- a backup, an off-site copy, a deploy or a restore fails;
- someone keeps failing to sign in to the panel.

## 7. When the panel is down

The game keeps running without the panel. On the VPS:

```powershell
C:\lotus\tools\lotus-panel.exe --config C:\lotus\panel\panel.toml ctl status
```

The same `ctl` command does `start`, `stop`, `restart`, `lock` and `unlock` (`--as Your_Name` asks for your password
for the ones that need an owner). These take the same steps as the panel's Server page.

| Problem | What to check |
|---|---|
| The panel page doesn't load | `Get-Service lotus-panel, lotus-caddy`; `C:\lotus\logs\lotus-panel\` and `...\lotus-caddy\` |
| Certificate warning | duckdns.org points at the VPS; TCP 443 open at the provider. Let's Encrypt retries by itself |
| "Hub not reachable" on the dashboard | `Get-Service lotus-hub`; its log |
| A world shows "failed" | It crashed three times in ten minutes. Read the job's last log lines, fix, then Start it on the Server page |

## 8. What has and hasn't been tested

Everything above was built and checked off the VPS:
- Panel tests 1–4 (permissions, sign-in, server control, deploy) run against fake Windows services.
- Live checks against a real hub, world and PostgreSQL covered: the console and moderation actions, a 119 MB signed
  release (good and tampered), and a backup with its verify.

Not yet run anywhere, because they need the Windows VPS:
- the junction switch and service restarts of a real deploy;
- a live restore;
- Discord and SMTP delivery;
- the S3 upload;
- the installer's new steps.

Do a first deploy and a backup Verify on the VPS before relying on them.
