# Hosting Lotus on a Windows Server 2025 VPS

## Install (one command)

1. Connect to the VPS with Remote Desktop.
2. Open the Start menu, type `PowerShell`, right-click **Windows PowerShell** and choose **Run as administrator**.
3. Paste this line and press Enter:

```powershell
irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/install-lotus.ps1 | iex
```

It asks one question: your in-game account name, which becomes the developer rank. The first run takes about 15–30
minutes. At the end it prints the client download link and the control panel address
(`https://lotusps.duckdns.org`).

If your VPS provider has its own firewall in its website, open **TCP 43594**, **TCP 8080** and **TCP 443** there
too.

Accounts and saves live in PostgreSQL (database `lotus`). An earlier install kept them in
`C:\lotus\game\.data\accounts\hub-store.json`; that file is left untouched but no longer used (the owner chose a
fresh database, 2026-11-10).

## Update

Run the same command again. It pulls the latest code, rebuilds the server and the client, and restarts. Players are
disconnected for a few minutes, so warn them in game first with `::update 60`.

## Players

Players download the client from `http://194.238.27.209:8080/update/Lotus.jar`. They need Java 21 or newer
(adoptium.net); then they double-click the jar or run:

```powershell
java -Xmx768m -Xss2m --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -jar Lotus.jar
```

The client is built on the VPS, with the VPS's own login key. A client you build on your PC uses your PC's key, so it
**can't** log in to the VPS. Always hand out the one from the link above (also at `C:\lotus\Lotus.jar`).

## Everyday commands (PowerShell as administrator, on the VPS)

| What | Command |
|---|---|
| Watch the game log | `Get-Content C:\lotus\logs\lotus\lotus_rCURRENT.log -Wait -Tail 50` |
| Watch the hub log | `Get-Content C:\lotus\logs\lotus-hub\lotus-hub_rCURRENT.log -Wait -Tail 50` |
| Stop the game (saves everyone first) | `Stop-Service lotus` |
| Start the game | `Start-Service lotus` |
| Restart the game | `Restart-Service lotus` (the hub and database keep running) |
| Restart everything | `Restart-Service lotus-hub -Force` (restarts the game and panel too) |
| Change settings | edit `C:\lotus\server.toml`, then `Restart-Service lotus` |

Never stop the server by killing `java.exe` in Task Manager: that skips the final save. A crash, or `::update` reaching
zero, restarts the server automatically after 10 seconds.

## What the installer sets up

| Where | What |
|---|---|
| `C:\lotus\tools` | Java 25 (server) and 21 (client build), Eclipse Temurin; MinGit; Shawl (service wrapper); PostgreSQL 17.11; Caddy 2.11.9; `lotus-panel.exe` (built by CI). Pinned downloads are checked against their SHA-256. |
| `C:\lotus\game` | The code (git clone), the cache and the login key (`.data\rsa`). Only Administrators, the game and the hub can open it. |
| `C:\lotus\pgdata` | The database files (`lotus` for the game, `lotus_panel` for the panel), listening on 127.0.0.1 only |
| `C:\lotus\pg`, `C:\lotus\pki` | The database superuser password and the panel's certificate authority: Administrators only |
| `C:\lotus\hub`, `C:\lotus\panel` | The hub's and the panel's certificates and secrets, each readable only by its own service |
| `C:\lotus\server.toml`, `owner.txt` | Your settings and your account name. Updates never overwrite them. |
| `C:\lotus\logs\<service>` | Each service's log, rotated at 10 MB, 5 kept |
| `C:\lotus\releases`, `C:\lotus\nodes` | Each installed build or deployed release, and one junction per service pointing at the release it runs (panel Deploys page) |
| `C:\lotus\backups` | `lotus-<date>.dump` and `lotus_panel-<date>.dump` (`pg_dump`), daily at 04:00, kept 30 days. Copy these off the VPS now and then. |
| Services | `lotus-postgresql` → `lotus-hub` → `lotus` (game) → `lotus-panel` → `lotus-caddy`, each under its own restricted account, none as Administrator |
| Windows Firewall | TCP 43594 (game), 8080 (website, world list, client download) and 443 (panel) open |

The server address comes from `site` in `client/runelite/release.properties` (now `194.238.27.209`). The panel
address is `$PanelDomain` at the top of the installer (`lotusps.duckdns.org`, which must point at the VPS on
duckdns.org). Its HTTPS certificate comes from Let's Encrypt through port 443 alone. Signing in to the panel arrives
with its next update (panel phase 3); until then it shows a status page.

**Updating with a countdown and rollback (from the panel).** On the VPS, in PowerShell opened as Administrator:

```powershell
irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/build-release.ps1 | iex
```

It builds the latest code into a release signed with this server's own key (the game keeps running). Then in the
panel: Deploys › Check the incoming folder › open the release › Deploy. No GitHub settings are needed; the
`game-release` GitHub workflow is an optional alternative for whoever can set the repository's secrets.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Run this in PowerShell opened with Run as administrator" | Close PowerShell; open it again with right-click, Run as administrator. |
| A download failed | Check the VPS's internet access and run the command again; finished steps are skipped. |
| "... did not come up within 3 minutes" | Read that service's log in `C:\lotus\logs\<service>\`. |
| "The control panel isn't available yet" | The panel's Windows build hasn't been published yet; the game installs anyway. Run the installer again later. |
| The panel page shows a certificate warning | Let's Encrypt takes a minute or two. Check `lotusps.duckdns.org` points at the VPS and TCP 443 is open at the provider. |
| Players can't connect, but it works on the VPS | Open TCP 43594 and 8080 in the provider's firewall (their website). |
| "git pull failed" | Something changed files in `C:\lotus\game`. Run `git -C C:\lotus\game status`, then `git -C C:\lotus\game checkout .`. |
