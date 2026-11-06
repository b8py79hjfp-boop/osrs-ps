# Phase 6 — Release client

Done when (brief §8):
- test 5 passes;
- on clean Windows, macOS and Linux machines, the signed package installs, auto-updates, logs in to the cluster and
  world-hops through the world hopper;
- a traffic capture shows no connections to Jagex or RuneLite hosts.

## Inputs (pinned)

| What | Value | Source |
|---|---|---|
| RuneLite release | 1.13.6, tag `runelite-parent-1.13.6` (commit d62b158) | bootstrap `b4dab8e7…` (D-007) |
| Game client | `injected-client-1.13.6.jar`, sha256 `6a9dc96d…6255` | bootstrap.json `artifacts` |
| Jagex RSA modulus | one 256-hex-digit constant (class `ar` in this build) | found by value, never by name |
| Jagex URLs in the game client | support/legal/account-recovery/display-name links (classes ak, se, kr, abt) | found by value |

Jagex and RuneLite binaries never enter git (§0.6). `./gradlew setupClient` mirrors the pinned bootstrap's
artifacts into `.data/client/artifacts/` and verifies each sha256. In CI they come from the private artifact store.

## Layout (`client/`)

- `client/gamepatch/` (Kotlin + ASM): rewrites constants in the injected client by value.
  - **Inputs:** our modulus from `.data/rsa/modulus.hex`; a URL map from `client/gamepatch/patches.json`.
  - **Rule:** every patch must match exactly once (URL patches: their declared count). Anything else fails the build.
  - **Model:** RSProx's patcher techniques (constant-string replacement, regex string replacement; MIT, read only).
- `client/runelite/`: our fork as a patch series (`patches/*.patch`) applied to the pinned RuneLite tag by
  `./gradlew :client:fork` (clone outside the repo, apply, build). Changes, kept to as few files as possible:
  1. `runelite.properties`:
     - title;
     - jav_config, plus its backup URL;
     - plugin hub disabled;
     - api/session/ws/static/config pointed at the hub;
     - Discord disabled;
     - help links pointed at ours.
  2. External plugin hub off. A plugin allowlist (core plugins only, list in `client/runelite/allowlist.txt`).
  3. No Jagex Account / launcher-token login; the normal username/password flow only.
  4. Branding: name, icons, splash, About/Licenses screen keeping RuneLite's BSD-2 notice.
- Hub endpoints for the fork, under `runelite.api.base`:
  - `worlds.js` (RuneLite's world list format, built from the hub's world directory);
  - stubs for item prices (from GE guide prices), config sync (204) and session (disabled).
- `client/launcher/` (Kotlin):
  - fetches `manifest.json` (artifact list + sha256) with a detached Ed25519 signature;
  - verifies it against the public key built into the launcher, downloads changed artifacts, and starts the client
    with the bootstrap's JVM arguments.
- `client/dist/`:
  - a jlink runtime (Java major taken from RuneLite's own launcher, checked, not assumed);
  - jpackage installers for Windows, macOS and Linux, x64 and arm64, built in a CI matrix;
  - Authenticode signing (Windows), and signing plus notarization (macOS, needs an Apple Developer ID).
- Test 5 (`client` build check):
  - the fork builds against the pinned tag;
  - each game-client patch matches exactly once;
  - the patched jar contains our modulus and endpoints and none of the replaced ones.

## Order

1. Plan; `setupClient`; game-client patcher and its exactly-once checks.
2. Fork patch series and hub endpoints; build the patched client locally.
3. Launcher + signed manifest + update endpoint on the hub.
4. Packaging (jlink/jpackage), CI matrix, signing hooks (secrets supplied by the user), docs/CLIENT.md release section.
5. Test 5 in the build. Then the user verifies on clean machines, with a traffic capture.

## Needs from the user

- Code-signing material:
  - a Windows Authenticode certificate;
  - an Apple Developer ID with notarization credentials;
  - our Ed25519 manifest key, which `./gradlew generateManifestKey` creates; keep the private half out of git.
- The name, icon and splash for the branding.
- Clean Windows/macOS machines for the final check.
