# Connecting a client

Phase 2 dev path: stock RuneLite launched by **RSProx** through a custom proxy target (brief §4.1). Never shipped to
players; the release client is Phase 6. RSProx private-server targets work on **Windows and Linux only** (not macOS),
for revisions ≥ 223, with no protocol-breaking client changes.

## 1. Prepare the server (once)

```bash
./gradlew setup          # cache 2757 + keys.json + Jagex's live jav_config.ws template, into .data/ (never committed)
```

```bash
./gradlew generateRsa    # login RSA keypair into .data/rsa/; prints the modulus (hex) — copy it for step 3
```

The modulus is also saved to `.data/rsa/modulus.hex`. Keep `.data/rsa/private.pem` private.

## 2. Run the server

```bash
./gradlew :server:run
```

Defaults: game + JS5 on **43594**, `jav_config.ws` / `worldlist.ws` on **8080**, world 1 advertised at `127.0.0.1`.
For another machine on your LAN, pass the address clients should use:

```bash
./gradlew :server:run --args="--public-host 192.168.1.20"
```

Check it: `http://<host>:8080/jav_config.ws` should show `codebase=http://<host>/` (the world's game host, no port) and `param=25=242`.

## 3. RSProx proxy target

Create `~/.rsprox/proxy-targets.yaml` (Windows: `%USERPROFILE%\.rsprox\proxy-targets.yaml`):

```yaml
config:
  - name: tickperfect
    jav_config_url: http://127.0.0.1:8080/jav_config.ws
    revision: 242
    modulus: <paste the hex modulus from ./gradlew generateRsa>
    game_server_port: 43594
    runelite_bootstrap_commithash: b4dab8e7cfdef726c45d2772d60d8a633d397361
```

- `runelite_bootstrap_commithash` pins RuneLite **1.13.6**, the bootstrap for revision 242
  (`runelite/static.runelite.net` commit `b4dab8e7…`, 2026-11-03; how it was chosen: docs/DECISIONS.md D-007).
  RSProx's built-in table stops at 241, so this line is required until it adds 242.
- Replace `127.0.0.1` with the server's address if RSProx runs elsewhere.

Start RSProx, choose the **tickperfect** target and launch **RuneLite**. Log in with any username and password:
there are no accounts until Phase 5 (D-018).

## 4. What should happen

1. RuneLite downloads the cache over JS5 from our server (first launch takes a while).
2. Login (proof of work, then RSA).
3. You spawn in the Lumbridge castle courtyard (3222, 3218). Click to walk; ctrl-click runs that path; `::run` toggles
   run; `::tele x z [level]` teleports; `::pos` prints your position.
4. Bots: in another terminal, `./gradlew :tools:botswarm:run --args="20"` logs in 20 bots that wander near spawn.

## Driving the client from Claude Code (optional dev tool)

`tools/runelite-mcp/` is a RuneLite plugin that runs a local MCP server (127.0.0.1 only), so a Claude Code session on
the tester's PC can read game state, widgets, varps and chat, click interface buttons, run `::commands`, and read a
ring buffer of client events (what the client sent, what the server changed). RSProx already starts RuneLite with
`--developer-mode`, so the jar only needs to be copied to `~/.runelite/sideloaded-plugins/`. Build, install and
`claude mcp add` steps: `tools/runelite-mcp/README.md`. It is a standalone Gradle build and never part of `./gradlew build`.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| RSProx log: `Required value was null` at `findCodebaseReplacementWorld` | jav_config `codebase` host must equal a world-list host. Fixed 2026-11-05: `git pull`. |
| "Error connecting to server" | Server not running, or `game_server_port` / the host in `jav_config_url` is wrong. |
| Stuck on "Loading..." / cache errors | JS5 not reachable, or a stale RuneLite cache for this target: delete RSProx's cache folder for the target and retry. |
| "Login server rejected session" / RSA errors | `modulus` doesn't match `.data/rsa/modulus.hex` (regenerated keys?). Paste it again. |
| "Client out of date" | The client sent CRCs that don't match our master index: wrong revision (`revision: 242`) or a different cache. |
| RuneLite version mismatch | Check `runelite_bootstrap_commithash` above. |
| Login hangs after proof of work | Check the server log for a login-decoding error. Known risk: rev-242 `jxSessionId` handling (D-005 OPEN-B). Please report the log. |
| `::commands` disconnect you | Known risk: CLIENT_CHEAT trailing byte (D-005 OPEN-A). Report it; it's a one-line codec fix. |

Phase 6 extends this document with the release client's build, signing and update process.
