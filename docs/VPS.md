# Hosting Lotus on a Windows Server 2025 VPS

## Install (one command)

1. Connect to the VPS with Remote Desktop.
2. Open the Start menu, type `PowerShell`, right-click **Windows PowerShell** and choose **Run as administrator**.
3. Paste this line and press Enter:

```powershell
irm https://raw.githubusercontent.com/b8py79hjfp-boop/osrs-ps/main/deploy/windows/install-lotus.ps1 | iex
```

It asks one question: your in-game account name, which becomes the developer rank. The first run takes about 15–30
minutes. At the end it prints the client download link.

If your VPS provider has its own firewall in its website, open **TCP 43594** and **TCP 8080** there too.

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
| Watch the server log | `Get-Content C:\lotus\logs\server_rCURRENT.log -Wait -Tail 50` |
| Stop (saves everyone first) | `Stop-Service lotus` |
| Start | `Start-Service lotus` |
| Restart | `Restart-Service lotus` |
| Change settings | edit `C:\lotus\server.toml`, then `Restart-Service lotus` |

Never stop the server by killing `java.exe` in Task Manager: that skips the final save. A crash, or `::update` reaching
zero, restarts the server automatically after 10 seconds.

## What the installer sets up

| Where | What |
|---|---|
| `C:\lotus\tools` | Java 25 (server) and 21 (client build), Eclipse Temurin; MinGit; Shawl (the service wrapper, pinned by checksum) |
| `C:\lotus\game` | The code (git clone), the cache, the login key (`.data\rsa`, keep it secret), accounts and saves (`.data\accounts\hub-store.json`) |
| `C:\lotus\server.toml` | Your settings, made once from `config\server.toml` with your name as developer. Updates never overwrite it. |
| `C:\lotus\logs` | Server log, rotated at 10 MB, 5 kept |
| `C:\lotus\backups` | `hub-store-<date>.json`, made daily at 04:00 and kept 30 days. Copy these off the VPS now and then. |
| Service `lotus` | Starts with Windows. It runs as its own restricted account (`NT SERVICE\lotus`), not as Administrator, with a Java heap of half the RAM (1–4 GB). |
| Windows Firewall | TCP 43594 (game) and 8080 (website, world list, client download) open |

The server address comes from `site` in `client/runelite/release.properties` (now `194.238.27.209`). The control
panel (panel/) will later move accounts to PostgreSQL. That needs no action now.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Run this in PowerShell opened with Run as administrator" | Close PowerShell; open it again with right-click, Run as administrator. |
| A download failed | Check the VPS's internet access and run the command again; finished steps are skipped. |
| "The server did not come up" | Read the last log lines: `Get-Content C:\lotus\logs\server_rCURRENT.log -Tail 50`. |
| Players can't connect, but it works on the VPS | Open TCP 43594 and 8080 in the provider's firewall (their website). |
| "git pull failed" | Something changed files in `C:\lotus\game`. Run `git -C C:\lotus\game status`, then `git -C C:\lotus\game checkout .`. |
