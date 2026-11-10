# Hosting on a VPS (Windows Server 2025, one world, embedded hub)

The simplest live setup: one VPS runs the world node with its embedded hub. The hub handles login, the world list,
jav_config, the client API and the update directory. Accounts and saves go to the JSON file store (D-097). For many
players, or more than one node, use PostgreSQL and the topology in OPERATIONS.md. A Linux VPS: see the appendix.

Every command below is PowerShell, run in an **elevated** window (Run as administrator) on the VPS unless it says "on
your PC".

## 0. What you need

- Windows Server 2025 (Desktop Experience) with 2 or more cores and 4 GB RAM or more. Give the server a 2–3 GB heap.
- Its public IP, or a domain that points at it (for example `play.example.com`). Players use this address.
- Open ports: **43594/tcp** (game and JS5 cache) and **8080/tcp** (jav_config, world list, client API, updates).
  Keep **9090** (health and metrics) closed to the internet. Many VPS providers also have their own firewall in the
  web panel; open the two ports there as well.

## 1. Install the tools

Install Git, a Java 25 JDK (Temurin) and NSSM, which runs the server as a Windows service:

```powershell
winget install --id Git.Git -e --source winget
```

```powershell
winget install --id EclipseAdoptium.Temurin.25.JDK -e --source winget
```

```powershell
winget install --id NSSM.NSSM -e --source winget
```

If `winget` is missing, install the same three from their sites instead:
- git-scm.com;
- adoptium.net, Temurin 25 JDK, Windows x64 `.msi`. Tick "Set JAVA_HOME" in the installer.
- nssm.cc. Unzip it and put `nssm.exe` on `PATH`.

Close and reopen PowerShell, then check that Java 25 is on `PATH`:

```powershell
java -version
```

## 2. Get and build the server

Clone into a short path. Long Windows paths break deep build folders, hence also the `core.longpaths` setting:

```powershell
git config --global core.longpaths true
```

```powershell
git clone <your repo url> C:\tickperfect
```

```powershell
cd C:\tickperfect
```

Download and verify cache 2757 into `.data\`:

```powershell
.\gradlew.bat setup
```

Build the server. This also packs the custom cache into `.data\cache\242-packed`:

```powershell
.\gradlew.bat :server:installDist
```

The result is `server\build\install\world-node\bin\world-node.bat`.

## 3. The login RSA key

The server holds the private key. The client has the public modulus built in. Both sides must use the same key pair.

1. Generate it once, on the PC that builds the client:

   ```powershell
   .\gradlew.bat generateRsa
   ```

2. Copy that PC's `.data\rsa\` folder to `C:\tickperfect\.data\rsa\` on the VPS. Copy-paste over Remote Desktop works.
3. Keep `private.pem` secret. A new key means every player needs a new client.

## 4. Build the client for the VPS address (on your PC)

Set the address players will use in `client/runelite/release.properties`:

```properties
site=http://play.example.com:8080
api=http://play.example.com:8080/runelite
```

`plugins=open` (Plugin Hub on, every plugin loads) is the current setting; see client/runelite/README.md "Plugin modes".
Build the client. It must use the same `.data\rsa` key as the VPS:

```powershell
.\gradlew.bat setupClient
```

```powershell
.\gradlew.bat releaseClient
```

The result is `.data\client\fork\client-1.13.6-shaded.jar`. Give that jar to players; it runs on Java 21 or newer:

```powershell
java -Xmx768m -Xss2m --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -jar client-1.13.6-shaded.jar
```

For the installer and launcher route (signed updates from the VPS's `/update` directory), see CLIENT.md
"Release client: packaging, signing and updates". Copy `.data\update` to the VPS after `publish`.

## 5. Configure and test

Edit `C:\tickperfect\config\server.toml` for a public world:

- `[ranks.bootstrap]`: replace `developers = ["bot*"]` with your own account name, for example
  `developers = ["yourname"]`. Leaving `bot*` there would make anyone who registers `bot...` a developer.
- `live = true` under `[commands]` if the world should behave like a 1:1 world. With it, spawning and economy commands
  are off for players.
- Fill `[commands.urls]` and `rules` as you like.

Open the two public ports in Windows Firewall:

```powershell
New-NetFirewallRule -DisplayName "tickperfect game" -Direction Inbound -Protocol TCP -LocalPort 43594 -Action Allow
```

```powershell
New-NetFirewallRule -DisplayName "tickperfect http" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

Start the server by hand once to check it:

```powershell
cd C:\tickperfect
```

```powershell
.\server\build\install\world-node\bin\world-node.bat --public-host play.example.com
```

Wait for `world 1 registered at ...` in the log. Then, on your PC, open `http://play.example.com:8080/jav_config.ws`.
It should show `codebase=http://play.example.com/`. Log in with the client, then press **Ctrl+C** in the server window
to stop it. Ctrl+C saves every player before it exits.

## 6. Run it as a service

Register the service. The working directory matters: the server reads `.data\` and `config\` relative to it.

```powershell
nssm install tickperfect C:\tickperfect\server\build\install\world-node\bin\world-node.bat --public-host play.example.com
```

```powershell
nssm set tickperfect AppDirectory C:\tickperfect
```

```powershell
nssm set tickperfect AppEnvironmentExtra "JAVA_OPTS=-Xms2g -Xmx2g -XX:+UseZGC"
```

On stop, NSSM sends Ctrl+C and waits 60 s, so the final saves can finish:

```powershell
nssm set tickperfect AppStopMethodConsole 60000
```

```powershell
nssm set tickperfect AppStdout C:\tickperfect\logs\server.log
```

```powershell
nssm set tickperfect AppStderr C:\tickperfect\logs\server.log
```

```powershell
nssm set tickperfect AppRotateFiles 1
```

```powershell
mkdir C:\tickperfect\logs; nssm start tickperfect
```

Follow the log:

```powershell
Get-Content C:\tickperfect\logs\server.log -Wait -Tail 50
```

The service starts with Windows. Stop it only with `nssm stop tickperfect` or `Stop-Service tickperfect`. Never kill
`java.exe` in Task Manager: that skips the final save, and the last autosave (at most 500 ticks, about 5 minutes,
earlier) is what remains.

## 7. Updating

Pull and rebuild while the server keeps running:

```powershell
cd C:\tickperfect; git pull; .\gradlew.bat :server:installDist
```

If the build complains that files are in use, stop the service first and build again. Then warn players in game with
`::update 60` and restart:

```powershell
Restart-Service tickperfect
```

A server-only change needs nothing on the players' side. A change to the packed cache (interfaces, scripts, sprites)
changes the cache CRCs. A running client then gets "out of date" and must be restarted; it downloads the new cache on
the next start. A new client build is needed only when `release.properties`, the RSA key or the patches change.

## 8. Back up

`.data\accounts\hub-store.json` holds every account, save and rank. It is written atomically, so a copy is always a
complete file. A daily copy with Task Scheduler:

```powershell
mkdir C:\tickperfect-backup
```

```powershell
$a = New-ScheduledTaskAction -Execute powershell.exe -Argument '-NoProfile -Command "Copy-Item C:\tickperfect\.data\accounts\hub-store.json (\"C:\tickperfect-backup\hub-store-\" + (Get-Date -Format yyyy-MM-dd) + \".json\")"'
```

```powershell
Register-ScheduledTask -TaskName "tickperfect backup" -Action $a -Trigger (New-ScheduledTaskTrigger -Daily -At 4am) -User SYSTEM
```

Copy `C:\tickperfect-backup` off the VPS regularly (OneDrive, S3 or your PC). A backup on the same disk does not
survive a lost VPS. Also keep `.data\rsa\` and `config\server.toml`. Once you move to PostgreSQL (`--db`), follow
OPERATIONS.md "PostgreSQL backups".

## Notes

- Use a domain rather than a bare IP in `site`. A domain lets you move to another VPS by changing DNS, with no new
  client.
- The HTTP side is plain HTTP on 8080. To serve it as HTTPS on 443, put Caddy in front with a certificate (it runs on
  Windows, also as an NSSM service), proxying to `127.0.0.1:8080`. Then set `site=https://play.example.com` and
  `api=https://play.example.com/runelite`, and rebuild the client. The game port 43594 stays raw TCP.
- Windows Update restarts: set active hours or a maintenance window, so the server is not rebooted at peak time. The
  service stops cleanly (with saves) on a restart.
- DDoS protection or a proxy in front of 43594: OPERATIONS.md (`--proxy-protocol`).

## Appendix: Linux VPS

The steps are the same, with these differences:
- Install with `apt install git unzip openjdk-25-jdk` (or Temurin 25).
- Use `./gradlew` instead of `.\gradlew.bat`, and `bin/world-node` instead of `bin\world-node.bat`.
- Run it as a systemd service:
  - `WorkingDirectory=` the checkout;
  - `Environment=JAVA_OPTS=-Xms2g -Xmx2g -XX:+UseZGC`;
  - `ExecStart=<checkout>/server/build/install/world-node/bin/world-node --public-host <host>`;
  - `TimeoutStopSec=60`, because SIGTERM saves every player first;
  - `Restart=on-failure`.
- Open the ports with `ufw allow 43594/tcp` and `ufw allow 8080/tcp`.
