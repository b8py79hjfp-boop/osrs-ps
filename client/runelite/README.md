# RuneLite fork (release client)

The release client's RuneLite side: a small `git format-patch` series against RuneLite's tag
`runelite-parent-1.13.6` (commit `d62b158`, `osrs.runeliteTag` in `gradle.properties`), plus a few files that
`build.sh` overlays. RuneLite itself is not vendored (brief §0.6, D-028). Why each choice was made:
docs/DECISIONS.md D-028. What the client calls: [ENDPOINTS.md](ENDPOINTS.md).

| File | Purpose |
|---|---|
| `patches/*.patch` | The source changes (below), in apply order. |
| `release.properties` | Non-secret release tokens: `name`, `site`, `api`, and the commit the tag must resolve to. |
| `allowlist.txt` | The plugins that may load, with the reasons for the ones that may not. Copied into the jar (ignored in `open` mode). |
| `patches-open/` | `open` plugin mode only (below): `restore-from-base.txt` (RuneLite's hub icons and manifest certificate, checked out from `base.commit` so the repository carries no RuneLite binaries), then `*.patch`. |
| `LICENSES.txt` | RuneLite's BSD-2 notice and the bundled libraries. Copied into the jar root; shown by the Info panel's "About and open source licenses" entry. |
| `branding/` | Placeholder icons and splash (`generate.py` makes them; replace the PNGs with real artwork). |
| `build.sh` | Clone, patch, overlay, substitute, build, copy to `.data/client/fork/`. |
| `scan.sh` | Lists jar entries that still mention Jagex, RuneLite or other third-party hosts. |

## Build

```bash
client/runelite/build.sh                 # scratch clone in ${TMPDIR:-/tmp}/tp-runelite-fork
client/runelite/build.sh ~/work/rl-fork  # or choose the scratch directory (must be outside the repo)
client/runelite/scan.sh                  # host scan of .data/client/fork/client-1.13.6-shaded.jar
```

Needs git and a JDK for RuneLite's Gradle 8 build (verified with JDK 21; the project JDK 25 is not needed here). Output:
`.data/client/fork/client-1.13.6-shaded.jar` plus a `.sha256`. This is the runnable jar
(`java -jar ... [--help]`, main class `net.runelite.client.RuneLite`). It still contains Jagex's unpatched
`injected-client`; `client/gamepatch` rewrites that part.

`RUNELITE_GIT=<path or url>` changes the clone source (a local clone works offline), `GRADLE_ARGS=--offline` is
passed to Gradle, `SKIP_BUILD=1` stops after patching so the tree can be inspected. The script wipes its work
directory on every run, but only if it carries the marker file it created.

### Release tokens

The patches contain `@@NAME@@`, `@@SITE@@` and `@@API@@` in `runelite.properties` (and `@@NAME@@` in the license
text). `build.sh` replaces them from `release.properties` and fails if any `@@...@@` is left over.

| Token | Meaning | Dev value |
|---|---|---|
| `@@NAME@@` | Product name: window title, splash, About, user agent, data directory `~/.<name>` | `tickperfect` (placeholder) |
| `@@SITE@@` | Hub HTTP base; jav_config is `{site}/jav_config.ws` | `http://127.0.0.1:8080` |
| `@@API@@` | RuneLite API base the hub serves | `http://127.0.0.1:8080/runelite` |

## Plugin modes

`plugins` in `release.properties` (override: `PLUGINS=` for build.sh, `-PforkPlugins=` for Gradle):

| Mode | Effect |
|---|---|
| `allowlist` | The hardened client: no Plugin Hub, only `allowlist.txt` loads, the HTTP client refuses every non-release host. |
| `open` (current, owner's choice 2026-11-10) | `patches-open/` restores RuneLite's Plugin Hub (`repo.runelite.net/plugins`, signed manifests as upstream), loads every plugin (core, hub, sideloaded) and turns off the release-host check. The installed-plugin report stays off. |

Open mode trades the allowlist's protection for choice: hub plugins are third-party code, can contact any host, and
include PvP-information plugins the allowlist excluded. The server stays authoritative either way. The plugins the
series deletes (Discord, Wiki, XP updater, ...) stay deleted in both modes. Why: docs/DECISIONS.md D-099.

## The patch series

| # | Patch | What it does |
|---|---|---|
| 1 | Point runelite.properties at release tokens | Title, jav_config (+ backup), api/static/session/ws/config, help links, oauth redirect and domain block. |
| 2 | Disable the external plugin hub | No hub manifest/jar/icon download, no installed-plugin report, no hub tab; the manifest signing certificate is deleted. |
| 3 | Enforce a plugin allowlist | `PluginManager.loadPlugins` (the one entry point for core, sideloaded and external plugins) skips every class not in `allowlist.txt`; a missing list is fatal. |
| 4 | Remove Jagex Account and launcher token login | Deletes `rs/auth`, the Jagex Auth plugin, the `jagexauth` library dependency, `--character` and `--insecure-write-credentials`. |
| 5 | Disable telemetry, session ping, updater and RuneLite accounts | Telemetry provider returns nothing; no session ping; the RuneLite installer updater (downloads and runs an exe from GitHub) is deleted; stored RuneLite sessions are never loaded, so config sync cannot start. |
| 6 | Remove Discord integration | Deletes the Discord service, plugin and library. |
| 7 | Remove plugins that talk to third-party services | Deletes Account, Crowdsourcing, Twitch, Wiki, XP Updater and HiScore. |
| 8 | Redirect or remove the remaining endpoints | Hiscore URLs to `{api}/hiscore/...`; Grand Exchange, help and status links to `{site}`; Wise Old Man links and XP submission removed; no Jagex world-address fallback for jav_config; the plugin hub panel is deleted; an HTTP interceptor refuses any host that is not a release host. |
| 9 | Rebrand user-visible text, add About and licenses | Every user-visible "RuneLite" / "RuneScape" string uses the name token; Patreon entry removed; Info panel gets an About/licenses entry. |
| 10 | Per-release data directory | `~/.<name>` instead of `~/.runelite`, so a player's stock RuneLite settings and cache are untouched; the `~/jagexcache` copy is dropped. |
| 11 | Adjust the client tests | Tests follow the allowlist and the removed classes. |

## Rebasing onto a new RuneLite tag

Each revision bump (new cache, new RuneLite release) means re-applying this series:

```bash
git clone --branch <new-tag> https://github.com/runelite/runelite.git ~/work/rl && cd ~/work/rl
git switch -c fork
git am --3way /path/to/osrs-ps/client/runelite/patches/*.patch   # resolve conflicts, then: git am --continue
git format-patch --binary -N -o /path/to/osrs-ps/client/runelite/patches <new-tag>..HEAD   # delete the old patches first
```

Then update `osrs.runeliteTag` in `gradle.properties` and `base.commit` in `release.properties`, and check:

1. `build.sh` succeeds and `scan.sh` reports only the (unpatched) game-client classes.
2. Any plugin RuneLite added is new and not on the allowlist, so it does not load. Decide, then add it to
   `allowlist.txt` with a reason (fail closed is the default).
3. `grep -rnE "runelite\.net|runescape\.|jagex|https?://"` over `runelite-client/src/main` finds no new third-party
   host; new `*Client` classes that use `Request.Builder` go through the API base or are removed.
4. The client tests pass in the built tree: `cd <workdir>/src && ./gradlew :client:test` (needs network for the test
   dependencies).
5. ENDPOINTS.md still matches what the client requests.

Rebase `patches-open/*.patch` the same way, on top of the series and the restored files (export it with
`git format-patch` excluding the paths in `restore-from-base.txt`).

Patches are kept small and topic-based on purpose; do not fold unrelated edits into them.
