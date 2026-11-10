# Hosting on a VPS (one world, embedded hub)

The simplest live setup: one Linux VPS runs the world node with its embedded hub (login, world list, jav_config, client
API, update directory). Accounts and saves go to the JSON file store (D-097). For many players, or more than one
node, use PostgreSQL and the topology in OPERATIONS.md.

## 0. What you need

- A VPS running Ubuntu 24.04 (or similar) with 2 or more cores and 4 GB RAM or more. Give the server a 2–3 GB heap.
- Its public IP, or a domain that points at it (for example `play.example.com`). Players use this address.
- Open ports: **43594/tcp** (game and JS5 cache) and **8080/tcp** (jav_config, world list, client API, updates).
  Keep **9090** (health and metrics) closed to the internet.

## 1. Install the server (on the VPS)

Install git, unzip and a Java 25 JDK. If `openjdk-25-jdk` is not in your distribution, use Temurin 25 from
adoptium.net.

```bash
sudo apt update && sudo apt install -y git unzip openjdk-25-jdk
```

Create a user for the server:

```bash
sudo useradd -m -s /bin/bash tp
```

```bash
sudo -iu tp
```

Get the code:

```bash
git clone <your repo url> osrs-ps && cd osrs-ps
```

Download and verify cache 2757 into `.data/`:

```bash
./gradlew setup
```

Build the server. This also packs the custom cache into `.data/cache/242-packed`:

```bash
./gradlew :server:installDist
```

## 2. The login RSA key

The server holds the private key. The client has the public modulus built in. Both sides must use the same key pair.

1. Generate it once, on the machine that builds the client:

   ```bash
   ./gradlew generateRsa
   ```

2. Copy `.data/rsa/` to the VPS with `scp -r .data/rsa tp@<vps>:osrs-ps/.data/`.
3. Keep `private.pem` secret. A new key means every player needs a new client.

## 3. Build the client for the VPS address (on your PC)

Set the address players will use in `client/runelite/release.properties`:

```properties
site=http://play.example.com:8080
api=http://play.example.com:8080/runelite
```

`plugins=open` (Plugin Hub on, every plugin loads) is the current setting; see client/runelite/README.md "Plugin modes".
Build the client. It must use the same `.data/rsa` key as the VPS:

```bash
./gradlew setupClient
```

```bash
./gradlew releaseClient
```

The result is `.data/client/fork/client-1.13.6-shaded.jar`. Give that jar to players; it runs on Java 21 or newer:

```bash
java -Xmx768m -Xss2m --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -jar client-1.13.6-shaded.jar
```

For the installer and launcher route (signed updates from the VPS's `/update` directory), see CLIENT.md
"Release client: packaging, signing and updates". Run `publish` on the VPS, or copy `.data/update` there.

## 4. Configure and start

Edit `config/server.toml` for a public world:

- `[ranks.bootstrap]`: replace `developers = ["bot*"]` with your own account name, for example
  `developers = ["yourname"]`. Leaving `bot*` there would make anyone who registers `bot...` a developer.
- `live = true` under `[commands]` if the world should behave like a 1:1 world. With it, spawning and economy commands
  are off for players.
- Fill `[commands.urls]` and `rules` as you like.

Start the server by hand once to check it:

```bash
server/build/install/world-node/bin/world-node --public-host play.example.com
```

Wait for the log to say the world is up. Then, from your PC, open `http://play.example.com:8080/jav_config.ws`. It
should show `codebase=http://play.example.com/`. Then log in with the client.

## 5. Run it as a service

Create `/etc/systemd/system/tickperfect.service`:

```ini
[Unit]
Description=tickperfect world node
After=network-online.target
Wants=network-online.target

[Service]
User=tp
WorkingDirectory=/home/tp/osrs-ps
Environment=JAVA_OPTS=-Xms2g -Xmx2g -XX:+UseZGC
ExecStart=/home/tp/osrs-ps/server/build/install/world-node/bin/world-node --public-host play.example.com
# SIGTERM saves every player before the process exits; give it time.
TimeoutStopSec=60
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

The working directory matters: the server reads `.data/` and `config/` relative to it. Enable and start the service:

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now tickperfect
```

Follow the log:

```bash
journalctl -u tickperfect -f
```

Open the two public ports (ufw):

```bash
sudo ufw allow OpenSSH && sudo ufw allow 43594/tcp && sudo ufw allow 8080/tcp && sudo ufw enable
```

## 6. Updating

On the VPS, as `tp`, pull and rebuild:

```bash
cd ~/osrs-ps && git pull && ./gradlew :server:installDist
```

Warn players in game first with `::update 60`, then restart:

```bash
sudo systemctl restart tickperfect
```

A server-only change needs nothing on the players' side. A change to the packed cache (interfaces, scripts, sprites)
changes the cache CRCs. A running client then gets "out of date" and must be restarted; it downloads the new cache on
the next start. A new client build is needed only when `release.properties`, the RSA key or the patches change.

## 7. Back up

- `.data/accounts/hub-store.json` holds every account, save and rank. Copy it off the VPS daily, for example with a
  cron job running `cp` and `rsync`. It is written atomically, so a copy is always a complete file.
- Also keep `.data/rsa/` and `config/server.toml`.
- Once you move to PostgreSQL (`--db`), follow OPERATIONS.md "PostgreSQL backups".

## Notes

- Use a domain rather than a bare IP in `site`. A domain lets you move to another VPS by changing DNS, with no new
  client.
- The HTTP side is plain HTTP on 8080. To serve it as HTTPS on 443, put Caddy or nginx in front with a certificate,
  proxying to `127.0.0.1:8080`. Then set `site=https://play.example.com` and `api=https://play.example.com/runelite`,
  and rebuild the client. The game port 43594 stays raw TCP.
- DDoS protection or a proxy in front of 43594: OPERATIONS.md (`--proxy-protocol`).
