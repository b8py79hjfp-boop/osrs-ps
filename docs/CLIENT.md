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

Phase 6 extends this document with the release client's build, signing and update process (see below).

## Test the release client locally (dev settings, no branding or signing)

Everything defaults to `127.0.0.1`, so the release client can be tested on one machine before any branding,
certificates, URLs or keys exist. Requirements:
- a JDK 21+ on `PATH`;
- git;
- internet access.

Every step is a Gradle task, so it works the same in PowerShell, cmd or a Unix shell (no bash needed).

**A. The client on its own (no launcher):**

```bash
git pull
```

```bash
./gradlew setupClient
```

```bash
./gradlew releaseClient
```

This last step takes a few minutes. It clones RuneLite 1.13.6 into your temp directory, applies our patches, builds
it, and patches the game client with **your** `.data/rsa` key, writing the result to `.data/client/fork/`.

Start the server, using a second terminal:

```bash
./gradlew :server:run --args="--worlds 2 --loopback-hosts"
```

Then start the client:

```bash
java -XX:+DisableAttachMechanism -Xmx768m -Xss2m --add-opens=java.base/java.net=ALL-UNNAMED --add-opens=java.base/java.io=ALL-UNNAMED -jar .data/client/fork/client-1.13.6-shaded.jar
```

What should happen:
1. The first start downloads the cache from your server, which takes a while.
2. The game's terms screen appears; accept it.
3. Log in with any name and password (the dev hub auto-registers).
4. The RuneLite world hopper lists your worlds (127.0.0.1 and 127.0.0.2), and hopping works.

The client keeps its own data in `~/.tickperfect`, so your normal RuneLite is untouched.

**B. Through the launcher** (signed update, the way players would get it). Do this after A.

Create your own manifest key pair (once; it stays in `.data/`):

```bash
./gradlew :client:launcher:release --args="keygen .data/manifest"
```

Assemble the client jars:

```bash
./gradlew assembleReleaseArtifacts
```

Sign them into the hub's update directory:

```bash
./gradlew :client:launcher:release --args="publish .data/client/release-artifacts .data/update 1.0.0-dev 242 .data/manifest/manifest-private.key"
```

With the server still running, start the launcher:

```bash
./gradlew :client:launcher:run
```

The launcher downloads and verifies the jars into `~/.tickperfect/repository`, then starts the client. A dev launcher
build trusts `.data/manifest/manifest-public.key` automatically.

## Release client: the RuneLite fork (Phase 6)

The release client is a rebranded, patched RuneLite (brief §4.2). The RuneLite side is a patch series applied to the
pinned tag `runelite-parent-1.13.6`; the game client is patched separately by `client/gamepatch`. Details:
`client/runelite/README.md`; what it requests from the hub: `client/runelite/ENDPOINTS.md`.

```bash
client/runelite/build.sh        # clone tag, apply patches, overlay allowlist/licenses/branding, build the shaded jar
client/runelite/scan.sh         # list jar entries that still mention Jagex/RuneLite/third-party hosts
java -jar .data/client/fork/client-1.13.6-shaded.jar --help
```

- Release values (`name`, `site`, `api`) come from `client/runelite/release.properties`. `site` is the hub's HTTP
  base (`{site}/jav_config.ws`), `api` the RuneLite API base the hub serves (`worlds.js`, `item/prices.js`,
  `config.json`, `static/...`).
- Plugins: `plugins` in `release.properties`. `open` (current) has RuneLite's Plugin Hub and loads every plugin;
  `allowlist` loads only `client/runelite/allowlist.txt` and refuses any host other than the hub's
  (client/runelite/README.md "Plugin modes"). Telemetry, RuneLite accounts, Jagex Account sign-in, Discord, the wiki
  and third-party XP trackers are gone in both modes.
- Hosting on a VPS: docs/VPS.md.
- The client keeps its settings in `~/.<name>` (placeholder `~/.tickperfect`), separate from a stock RuneLite.
- Login is the normal username/password screen against our login service.

### Release client troubleshooting

| Symptom | Likely cause |
|---|---|
| Dialog "unable to connect to the game update server" at start | `{site}/jav_config.ws` is not reachable or lacks `codebase`, `initial_jar` or `initial_class`. |
| `Refusing request to <host>: not a release host` in the log | Something tried to leave the hub's host; the request was blocked on purpose. Report the host. |
| World hopper is empty | `{api}/worlds.js` is missing or malformed (ENDPOINTS.md has the exact shape). |
| A plugin you expect is missing | `allowlist` mode: it is not in `client/runelite/allowlist.txt`. Either mode: it was removed by the patch series (the list says why). |
| No "Plugin Hub" tab | The client was built in `allowlist` mode; set `plugins=open` and rebuild. |
| `Plugin allowlist allowlist.txt is missing` | The jar was not built with `build.sh` (the allowlist is overlaid at build time). |

## Release client: packaging, signing and updates (Phase 6)

Players install a small package (launcher + a jlink Java runtime, about 35 MB) and never see Java or RSProx. The client
itself is not in the installer: the launcher downloads it from the hub's `/update` directory, checks every file against
a signed manifest, and starts it on the bundled runtime (D-028).

```
build:    setupClient -> client/runelite/build.sh (fork) -> client/gamepatch (game client) -> release publish -> update-dir/
package:  client/dist/package.sh <os> <arch> -> installers in client/dist/build/out/
publish:  copy update-dir/ to the hub's update directory (served at /update)
install:  player runs the installer once; the launcher updates the client on every start
```

### Files (`client/dist/`)

| File | What |
|---|---|
| `app.properties` | App name/id/vendor, installer version, MSI upgrade UUID, Linux metadata. `name`/`id` must equal `launcher.properties` (checked) |
| `jdk-versions.sh` | Pinned JDKs and sha256 per platform (RuneLite launcher 2.6.7's table: Java 11.0.19 everywhere, 17.0.7 on macOS arm64) |
| `modules.txt`, `build-runtime.sh <jdk> <out>` | jlink module list (one reason per line) and the jlink call (`--strip-debug --no-header-files --no-man-pages --compress 2`) |
| `package.sh <os> <arch>` | Downloads the pinned JDK, builds the runtime and launcher, runs jpackage, signs, smoke-tests the launcher |
| `sign.sh` | Signing and notarization hooks (sourced by `package.sh`) |
| `assemble-artifacts.sh <dir>` | Collects the manifest's jars: bootstrap mirror + patched injected client + fork client jar |
| `entitlements.plist` | macOS hardened-runtime entitlements (JIT, unsigned executable memory, JNA/LWJGL natives) |
| `branding/` | Placeholder icons; `python3 branding/generate.py` regenerates them (needs Pillow). Replace with real artwork |

### Build the installers

Run on the target OS (jpackage cannot cross-package). Needs a JDK 17+ with jpackage (`JPACKAGE_JDK`, else `JAVA_HOME`,
else `PATH`) plus: Linux `fakeroot` and `dpkg-deb`; Windows WiX 3.x; macOS Xcode command line tools.

```bash
client/dist/package.sh linux x64      # .deb and .tar.gz
client/dist/package.sh windows x64    # .msi and .exe
client/dist/package.sh macos arm64    # .dmg and .pkg
```

Output: `client/dist/build/out/<name>-<version>-<os>-<arch>.<ext>` (gitignored). `INSTALLER_VERSION` overrides
`installer.version`; `UPDATE_URL` bakes another update endpoint into the installed app (`-Dlauncher.updateUrl`);
`SKIP_LAUNCHER_BUILD=1` reuses the last `:client:launcher:installDist`. On Linux and macOS the script finishes by
starting the packaged launcher against a closed port and expects `Can't reach the update server and no verified client
is installed yet.` (this proves the bundled runtime starts and the ECDSA code path loads).

The runtime's modules and the reason for each are in `client/dist/modules.txt`. To re-derive them after a RuneLite
bump: `jdeps --multi-release 11 --ignore-missing-deps --print-module-deps <launcher jars> <client jars>`, then keep the
service-lookup modules jdeps cannot see (`jdk.crypto.ec`, `jdk.charsets`, `jdk.zipfs`). Check the result by starting
the client on the new runtime (no `NoClassDefFoundError` / `module ... not found` in its log).

### Signing

No secrets live in the repo. Without them `package.sh` builds **unsigned** packages and prints a warning
(`REQUIRE_SIGNING=1` turns the warning into an error; the tag workflow sets it).

| Platform | Variables | Tools |
|---|---|---|
| Windows (Authenticode) | `WINDOWS_CERT_PFX` (path to the .pfx), `WINDOWS_CERT_PASSWORD`, optional `WINDOWS_TIMESTAMP_URL` | `signtool` (Windows SDK) or `osslsigncode`. Signs the app launcher exe, then the MSI/EXE |
| macOS (Developer ID) | `APPLE_SIGNING_USER` (certificate name without the "Developer ID Application: " prefix, e.g. `Example Ltd (ABCDE12345)`), optional `APPLE_KEYCHAIN` | jpackage `--mac-sign` signs the app (hardened runtime, `entitlements.plist`) and the pkg; `codesign` signs the dmg |
| macOS notarization | `APPLE_ID`, `APPLE_TEAM_ID`, `APPLE_APP_PASSWORD` (app-specific password) | `xcrun notarytool submit --wait`, then `stapler staple` |

CI secrets (workflow `release-client`): `WINDOWS_CERT_PFX_BASE64`, `WINDOWS_CERT_PASSWORD`, `APPLE_CERT_P12_BASE64` (one
.p12 with the Developer ID Application and Installer certificates), `APPLE_CERT_PASSWORD`, `APPLE_SIGNING_USER`,
`APPLE_ID`, `APPLE_TEAM_ID`, `APPLE_APP_PASSWORD`, plus `MANIFEST_PRIVATE_KEY` and `RSA_MODULUS_HEX` below.
Repository variables: `UPDATE_URL` (production hub `/update`), `ARTIFACT_STORE_URL` (reserved, see Publish).
The signing paths are written against the tools' documented usage and have not been run (no certificates yet).

### Publish (the update directory)

`release publish` writes the signed update directory; the hub serves it at `/update`.

```bash
./gradlew setupClient                       # bootstrap mirror into .data/client/artifacts (checks sha256)
client/runelite/build.sh                    # fork -> .data/client/fork/
./gradlew :client:gamepatch:run --args="$PWD/.data/client/artifacts/injected-client-1.13.6.jar \
  $PWD/.data/client/patched/injected-client-1.13.6.jar --site https://<hub> \
  --config $PWD/client/gamepatch/patches.json --modulus $PWD/.data/rsa/modulus.hex"
client/dist/assemble-artifacts.sh /tmp/client-artifacts
./gradlew :client:launcher:release --args="publish /tmp/client-artifacts update-dir 1.0.0 242 .data/manifest/manifest-private.key"
```

Then copy `update-dir/` (`manifest.json`, `manifest.json.sig`, `artifacts/`) to the hub's update directory. The workflow
does all of this in its `update-dir` job and uploads it as an artifact; deploying it to the hub is still manual
(TODO hook in the workflow). The workflow's artifacts still come from `repo.runelite.net` via `setupClient`; the brief
wants a private artifact store (`ARTIFACT_STORE_URL` is reserved for it, nothing reads it yet).

The manifest key pair: `./gradlew :client:launcher:release --args="keygen <dir>"`. The public half goes into
`launcher.properties` (`publicKey`) and therefore into the next installer; the private half is the secret
`MANIFEST_PRIVATE_KEY`. Losing or replacing the key means every player must reinstall.

### Release workflow

`.github/workflows/release-client.yml` runs on manual dispatch (dry run: throwaway keys are allowed, with warnings) and
on tags `client-v<major.minor.patch>` (a release: refuses to run without the real secrets, refuses development URLs in
`release.properties` / `launcher.properties`). Jobs: `update-dir` (ubuntu: setupClient, fork, gamepatch, assemble,
signed publish), `package` (matrix: windows x64, windows arm64 (experimental), macOS x64 on `macos-15-intel`, macOS arm64,
linux x64, linux arm64), and for tags a draft GitHub release holding the installers. Hosted arm64 runners are free for
public repositories only.

### Install and update flow

1. The player installs the package (Windows per-user MSI/EXE, macOS dmg/pkg, Linux deb or tar.gz). No Java needed.
2. Each start, the launcher downloads `manifest.json` and `manifest.json.sig` from the update URL, verifies the
   ECDSA signature against the public key built into the launcher, downloads changed jars into `~/.<id>/repository`,
   deletes jars no longer listed, and starts `net.runelite.client.RuneLite` on the bundled runtime.
3. Server unreachable: the last verified manifest is reused. Invalid signature: never started.

### Revision bump

1. Bump the server revision and cache as usual; the server answers old clients with "client out of date".
2. Update `osrs.runeliteBootstrap` / `osrs.runeliteTag` (gradle.properties), rebase `client/runelite/patches/`, fix
   `client/gamepatch/patches.json` until every rule matches its declared count (test 5).
3. Rebuild the update directory (steps in Publish) with the new version and revision; deploy it to the hub.
4. Players get the new client on their next launch; no new installer is needed unless the launcher, the Java major
   version (`jdk-versions.sh`) or the module list changed. If RuneLite's launcher moves to a newer Java, update
   `jdk-versions.sh` from its `.jdk-versions.sh`, re-derive `modules.txt`, and release new installers.

### Still needs you

- **Certificates:** a Windows Authenticode certificate, an Apple Developer ID (Application + Installer) with an
  app-specific password for notarization; set the secrets above. Until then all packages are unsigned and
  SmartScreen/Gatekeeper will warn.
- **Keys and URLs:** a production manifest key pair (public half into `launcher.properties`), the production
  `updateUrl`, `site`/`api` in `client/runelite/release.properties`, `RSA_MODULUS_HEX` of the production login key.
- **Branding:** name, id (also the `~/.<id>` directory), vendor, MSI upgrade UUID (fixed once released) and real icons
  in `client/dist/branding/`.
- **macOS JVM flags:** RuneLite's own macOS launcher also passes `--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED`
  and `--add-opens=java.desktop/sun.awt=ALL-UNNAMED` (launcher 2.6.7 `packr/macos-*-config.json`); the manifest's JVM
  arguments (`ReleaseTool.kt`) are the same on all platforms and do not include them. Test on a Mac and add them if
  the dock icon/fullscreen code needs them.
- **Launcher errors on Windows/macOS:** the launcher reports problems on stderr only, which a double-clicked GUI
  app does not show. A dialog (or a log file) for "Can't reach the update server..." is not built yet.
- **Clean-machine tests:** install on clean Windows 10/11, macOS (Intel and Apple silicon) and Linux machines: install,
  start, auto-update (publish a new version, restart), login, world hop, uninstall.
- **Traffic capture** (Phase 6 done-criterion): run the installed client with Wireshark or `tcpdump` capturing, log in
  and hop worlds, then check that only the hub and game hosts appear. Useful filters: DNS names, `dns.qry.name
  contains "jagex"`, `contains "runescape"`, `contains "runelite"`; or
  `tls.handshake.extensions_server_name matches "(jagex|runescape|runelite)"`; and a catch-all
  `not ip.addr == <hub ip> and not dns` to see anything else.
- **Optional:** an AppImage (needs `appimagetool`), an RPM, and Linux repository signing are not built.
