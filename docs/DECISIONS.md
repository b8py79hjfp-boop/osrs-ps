# Decisions

One entry per non-obvious choice: options considered, choice, why. Newest at the bottom.

---

## D-001 — Project codename and package root (2026-11-05)

- **Options:** a final player-facing name now; a neutral codename.
- **Choice:** codename `tickperfect`, root package `tp.<module>`, Gradle plugin ids `tp.*`.
- **Why:** the release-client branding (§4.2) isn't decided; a short neutral root keeps imports terse. Renaming the
  player-facing brand later doesn't touch packages.

## D-002 — Checks required by brief §13.2 (2026-11-05)

| Question | Finding | Source |
|---|---|---|
| `net.rsprot:osrs-242-api` on Maven Central? | **No** (404). Newest published: `osrs-241-api` / `osrs-240-api` `1.0.0-ALPHA-20261014`. | `repo1.maven.org/maven2/net/rsprot/` |
| RSProt rev-242 source? | **Yes, unpublished.** `blurite/rsprot` master has `protocol/osrs-242/` (api, common, desktop, internal, model, shared), commit `67d2136882fbc6072d1b72e935025209d9f63b5a` "Revision 242 (#82)", 2026-11-03. | github.com/blurite/rsprot |
| RSProx rev-242 module? | **Yes.** `protocol/osrs-242`, `CURRENT_REVISION = 242`, `DecoderLoader.loadRevision242`. HEAD `f1081e91839fee9af02d090ade82dfc47e234120` (2026-11-04). | github.com/blurite/rsprox |
| Latest `rsmod-pathfinder` / successor | Maven artifact `org.rsmod:rsmod-pathfinder:5.0.0` (2024-04-12, no release since). Successor: RS Mod's in-tree `engine/routefinder` (+ `api/route`), RS Mod HEAD `fa13b3f` 2025-09-30, ISC. See D-006. | Maven Central; github.com/rsmod/rsmod |
| RuneLite bootstrap commit for rev 242 | **`b4dab8e7cfdef726c45d2772d60d8a633d397361`** in `runelite/static.runelite.net` ("Release 1.13.6", 2026-11-03T08:53:35Z) → RuneLite tag `runelite-parent-1.13.6` = `d62b158e83450b0708f053c753e3508c762a0803`. See D-007. | static.runelite.net git history |
| JDK 25 latest patch | **Temurin 25.0.5+8**, the version CI's `setup-java` resolved on 2026-11-05. The repo pins major `25`. foojay and adoptium are blocked in the dev sandbox. | CI run on `b8py79hjfp-boop/osrs-ps` |
| Kotlin 2.4 | **2.4.21** (2.5.0-Beta2 exists; not used) | Maven Central |
| Gradle 9.8 | **9.8.2** (wrapper SHA-256 `3b8a2577…8e98`, dist SHA-256 `21bd5da2…9607`, both verified) | services.gradle.org |
| Netty 4.2 | **4.2.20.Final** | Maven Central |
| KSP | **2.3.13** (KSP2 versions are decoupled from Kotlin's since 2.3.0; works with Kotlin 2.4.x) | Maven Central |

## D-003 — Toolchain and library pins (2026-11-05)

Everything is in `gradle/libs.versions.toml`, checked 2026-11-05. Notable choices:
- **detekt `2.0.0-alpha.6`** (`dev.detekt`). The last stable release, 1.23.8, is built against Kotlin 2.0 and
  can't parse 2.4 features such as context parameters. Move to 2.0.0 stable when it ships.
- **ktlint 1.8.0 via Spotless 8.9.0**, `ktlint_official` style, max line 140 (`.editorconfig`).
- **Config format:** TOML via `ktoml` 0.7.1 (wired in Phase 2/3 when config appears).
- **Bzip2** via commons-compress 1.28.0 (the JDK has no bzip2; some JS5 groups use it).

## D-004 — JDK 25 baseline vs this sandbox (2026-11-05)

- **Problem:** the development container has only JDK 21, and its network policy blocks JDK downloads
  (foojay/adoptium/GitHub release assets → 403).
- **Options:** (a) drop the baseline to 21; (b) keep 25 in the repo and let a machine override it locally; (c) block.
- **Choice:** (b). `osrs.javaVersion=25` in `gradle.properties` drives `jvmToolchain`. This sandbox overrides it with
  `osrs.javaVersion=21` in `~/.gradle/gradle.properties` (not in git). CI uses Temurin 25.
- **Why:** Phase 0 code uses nothing beyond Java 21. JDK-25-only features (compact object headers, AOT cache,
  JFR method timing) only arrive in Phase 5 benchmarking, which has to run on 25 anyway.
- **Risk:** something 25-only could slip in and fail only in CI. CI is the gate.

## D-005 — Protocol implementation path (§4.3 decision procedure) (2026-11-05)

- Step 1: `net.rsprot:osrs-242-api` isn't on Maven Central (D-002), so step 2 applies.
- Step 2: "vendor RSProt's newest revision module". The newest is `protocol/osrs-242`, already in RSProt source at
  `67d2136` though unpublished. **Choice:** in Phase 2, vendor RSProt `osrs-242` at that commit into
  `protocol/osrs-242/`, keeping the MIT headers, and diff its codecs against RSProx's `osrs-242` decoders as
  ground truth (an empty diff is the expected result). If RSProt publishes `osrs-242-api` before Phase 2 starts,
  step 1 applies and we depend on the artifact instead.
- Everything sits behind `protocol-api`, so either path is a module swap.

## D-006 — Routing library (2026-11-05, finalize in Phase 1)

- `org.rsmod:rsmod-pathfinder:5.0.0` is stale (2024-04). RS Mod moved routing in-tree (`engine/routefinder`,
  `api/route`, ISC, maintained through at least 2025-09).
- **Provisional choice:** vendor RS Mod's `engine/routefinder` with its ISC notice in Phase 1, when collision is
  built, after diffing its semantics against the 5.0.0 artifact. Decide then; record here.

## D-007 — RuneLite bootstrap pin for rev 242 (2026-11-05)

- `static.runelite.net` (HTTP) is blocked here, but its git history is readable. RSProx's own table
  (`ProxyService.getBootstrapCommitHash`) maps rev 241 → `1e634e64…` = "Release 1.13.4" (2026-10-24) and has no rev-242
  entry yet.
- "Release 1.13.6" (`b4dab8e7…`, 2026-11-03 08:53Z) landed the same day as RSProt's "Revision 242" merge and about an
  hour before the OpenRS2 capture of cache 2757 (10:00Z). 1.13.5 (2026-11-01) predates the update.
- **Choice:** pin `b4dab8e7cfdef726c45d2772d60d8a633d397361` (client 1.13.6, injected-client 1.13.6).
- **Verify (Phase 2):** RSProx launching RuneLite at this commit against our server completes login. Also check
  whether RSProx adds a 242 entry, and confirm it matches.

## D-008 — Cache download: checksums and verification (2026-11-05)

- `./gradlew setup` downloads `disk.zip` and `keys.json` to `.data/cache/242/` and extracts the cache files flat into
  `.data/cache/242/disk/`.
- Pins the SHA-256s in `gradle/cache-checksums/242.sha256`, written on the first successful run (trust-on-first-use),
  then committed. Later runs fail on mismatch.
- Structural checks (25 archives, 120,374 groups, master-index CRC/version spot-checks for archives 0 and 24) run in
  the `cache` reader at server boot (`--boot-check`) and, from Phase 1, in test 1.
- Master-index CRC = CRC-32 over the container header + payload, excluding the version trailer.
- **Resolved 2026-11-05 (first real run):** OpenRS2's figures are master-index **slots** (25: `idx255` holds 25 entries,
  of which 16 and 23 are empty, size 0) and the **sum of index group counts** (120,374; master-index entries are not
  included). `Js5Cache.archiveCount` counts slots; `populatedArchiveCount` = 23. The client's master index likewise
  lists all 25 slots (CRC 0 / version 0 for the empty ones) — relevant for the Phase 2 login CRC check.
- Pinned: `disk.zip` `940e86f3…d6e9`, `keys.json` `5984eac0…afa` (`[]`: 0 XTEA keys, matching OpenRS2).

## D-009 — Sandbox Maven mirror (2026-11-05)

Maven Central rate-limits (429) this container's IP. A **local, uncommitted** init script
(`~/.gradle/init.d/central-mirror.init.gradle.kts`) puts Google's Central mirror
(`maven-central.storage-download.googleapis.com`) first. The repo build itself still declares only `mavenCentral()`.

## D-010 — `.gitignore` and source packages named `build` (2026-11-05)

An unanchored `build/` rule also matched `build-logic/src/main/kotlin/tp/build/`, so two task sources were never
committed and the first CI run couldn't compile build-logic. Fix: keep `build/` for Gradle output and add
`!**/src/**/build/`. Release gate: build from a fresh clone of the commit, never from the working copy.

## D-011 — Static collision semantics: client bridge rule (2026-11-05)

- **Options:** RS Mod's rule (locs check the tile one level up; floor blocking uses the tile's own bridge bit) vs the
  client's rule (every level checks the **level-1** tile's bridge bit, everything shifts down one, below 0 dropped).
- **Choice:** the client's rule. Sources: RuneLite `MapImageDumper` (`getTileSetting(1, ...) & 2`) and the LostCity
  engine's `CollisionManager`. Loc shape → flag rules adapted from RS Mod (ISC, header kept in `LocCollision.kt`).
- **Why:** faithful first. RS Mod's variant would block bridge decks over water at level 0.
- Details, sources and OPEN items: docs/mechanics/collision.md.

## D-006 (final) — Routing library: vendored RS Mod routefinder (2026-11-05)

- Vendored `rsmod/rsmod` `engine/routefinder` at `fa13b3f` (ISC) as module `routefinder/`, `src/main` unchanged,
  with NOTICE.md and LICENSE. It replaces the stale `org.rsmod:rsmod-pathfinder:5.0.0` artifact. Built without
  ktlint/detekt (`tp.kotlin-vendored`) so upstream diffs stay clean. Local changes must be listed in its NOTICE.md.

## D-012 — Config groups with no reference decoder (2026-11-05)

- 22 config types decode strictly (an unknown opcode or unread bytes fail boot and test 1).
- **Not decoded:** groups 15, 19, 20 and 70 (named varclientstring, varclient, varobj, gamelogevent by RS Mod /
  RuneLite, but neither has a decoder), plus 7, 22, 24–31, 37, 47, 54 and 71–73, which no reference names. Contents
  observed: 20 and 70 are all empty `[00]`; 19 is 654 × `02 00`; 15 has 21 structured files that don't look like
  varclientstring. All OPEN. They're plausibly client-only; revisit if a mechanic needs one.
- Groups 18 (area) and 2 (hunt) decode, but every file in 2757 is empty, so their opcode layouts (RS Mod only)
  are unverified against real data.

## D-013 — Map archive layout and terrain trailer (2026-11-05)

- Cache 2757's archive 5 is **unnamed**: a group id is the map-square id, file 0 is terrain, file 1 is locs, and
  there's no XTEA (which is why OpenRS2 lists 0 keys). Source: RuneLite 1.13.6 `RegionLoader`. The named layout is
  still supported for older caches.
- Only groups with file ids exactly `[0..4]` are squares (2,949). Group 25287 (37 × 3-byte files) is skipped (OPEN).
  Files 2–4 are unused (OPEN; likely spawn/area data).
- Terrain files end with a u8 trailer: `0` on 2,948 squares, `1` plus a 9,563-byte extension on square (64, 73). The
  extension is kept only as a size (OPEN; possibly Sailing-related). Strictness is preserved for anything else.

## D-014 — Symbols generated as Java constant chains (2026-11-05)

- **Problem:** objs (34,766) and locs (63,320) exceed one JVM class's 65,535-entry constant pool. Kotlin `object`s
  can't inherit constants, and non-const `val`s lose `when`-constant use and hit the 64 KB `<clinit>` limit.
- **Choice:** `tools/symbol-gen` (Kotlin) emits **Java** classes, each namespace a chain of ≤ 20,000-constant classes
  (`Locs extends Locs$3 extends ...`). Verified: Kotlin resolves inherited Java statics via the subclass, as
  compile-time constants. Shape follows RuneLite's gameval classes: flat namespaces, `DbTables.<Table>.ID/COL_*`,
  `Interfaces.X` (id) and `Components.<Iface>.Y` (packed `iface << 16 | comp`).
- Generated at build time into `symbols/build/generated` from `.data` (never committed: Jagex-derived). A fresh clone
  must run `./gradlew setup` before `build`; CI already does.
- Gameval groups 16–19 are unnamed by every reference; they're emitted as `Gameval16..19` (OPEN). Blank names
  become `UNNAMED`, and collisions get `_<id>` (none occur in 2757).
- Ids stay `Int` for now; typed wrappers are a Phase 3 plugin-api decision.

## D-015 — detekt policy for decoders (2026-11-05)

`MagicNumber`, `LongMethod` and `CyclomaticComplexMethod` are excluded for `tp/cache/config/**` and
`tp/cache/interfaces/**`: opcode numbers are the format, and each decoder cites its reference loader.
`LoopWithTooManyJumpStatements` is off (opcode loops). `NestedBlockDepth` allows 6 (tile loops). Everything else in
detekt applies as before.

## D-016 — Test 1 time budget (2026-11-05)

The brief asks for each test to take < 1 s. Test 1 decodes ~200k configs, 992 interfaces and 2,949 map squares,
builds full collision and generates ~210k symbols. That takes **3.9 s** on this 4-core sandbox. We accept the
overrun rather than shrink the test's scope, which the brief also fixes. Revisit with JDK 25 + AOT cache numbers
from CI.

## D-005 (executed) — RSProt 242 vendored; RSProx ground-truth diff (2026-11-05)

- Vendored `osrs-242-{api,common,desktop,internal,model,shared}` `src/main` at `67d2136` into
  `protocol/osrs-242/rsprot/` (MIT, NOTICE.md, no modifications). Shared modules come from Maven at
  `1.0.0-ALPHA-20261014`, verified unchanged by the 242 commit. Compiles under Kotlin 2.4 with
  `jvmDefault = NO_COMPATIBILITY`, matching upstream's `-Xjvm-default=all`.
- `protocol/osrs-242` (our adapter) implements `protocol-api`. Only it and `tools/botswarm` import `net.rsprot`.
- **Ground-truth diff** (scout, RSProt 242 vs RSProx 242): every game opcode and size matches in both directions,
  and the sampled packets match field for field (move clicks, op*, IF buttons, inventory, varps, MESSAGE_GAME,
  RUNCLIENTSCRIPT, rebuild incl. player-info init). OPEN:
  - **OPEN-A CLIENT_CHEAT:** RSProt reads a trailing `g1` unconditionally; RSProx only if bytes remain. If a real
    client omits it, `::commands` will fail to decode. Fix (local patch, NOTICE) only on evidence.
  - **OPEN-B login `jxSessionId`:** RSProt reads a flag (+ string) between the RSA and XTEA blocks for version ≥ 242.
    RSProx doesn't parse it. Confirm with the first real-client login.
  - Everything else differs only in RSProx parsing less deeply (login header, RSA contents and CRCs kept raw).

## D-017 — jav_config.ws from Jagex's live template (2026-11-05)

`./gradlew setup` downloads `oldschool.runescape.com/jav_config.ws` into `.data/client/` (Jagex content, never
committed) and warns if its `param=25` revision isn't ours. `ClientConfigServer` serves it with `codebase`, world list
(17), initial world (12), members (3) and world properties (5) rewritten, as RSProx does. `worldlist.ws` uses the
client binary format (RSProx `WorldList.encode`).

## D-018 — No accounts until Phase 5 (dev login policy) (2026-11-05)

Any non-blank username logs in, the password is ignored, and duplicate names are refused. Appearance is a fixed
default (RS Mod's default male). **This is dev-only**: the Phase 5 hub adds accounts (Argon2id), throttling and
moderation (brief §12). Reconnects are refused (ConnectFail) until Phase 5 session handover.

## D-019 — Test 2 runtime (2026-11-05)

Test 2 boots a real world (cache decode, collision, JS5 pre-encode), solves proof of work at difficulty 18 and walks a
player. It takes ~9 s here, over the brief's 1 s per-test budget, for the same reason as D-016. JS5 pre-encoding was
parallelised (13 s → 1.3 s), which also brought dev boot under 10 s (~3 s to listening).

## D-005 OPEN-B (resolved by local patch) — rev-242 login layout (2026-11-05)

- **Evidence:** RuneLite 1.13.6 via RSProx failed upstream RSProt decoding with "Unterminated string" right after the
  RSA block. RSProt reads a jxSessionId flag (+ string) there for version ≥ 242. RSProx's live proxy treats those
  bytes as the start of the XTEA block.
- **Choice:** local patch to the vendored `LoginBlockDecoder`. Decode the no-jx layout first, fall back to upstream's
  layout; a misaligned attempt fails fast on the block's jstr2 version bytes. Both paths are verified with the bot.
  Recorded in `protocol/osrs-242/rsprot/NOTICE.md`. The server logs which layout each login used
  (`JxLayoutStats`), so we learn whether any client sends the jx section. Upstream issue worth filing with RSProt.

## D-005 OPEN-A (resolved) — CLIENT_CHEAT trailing byte (2026-11-05)

RuneLite 1.13.6 `::pos` / `::tele` / `::run` decode and work with RSProt's unconditional trailing `g1`, so the real
client sends the byte. No patch needed.

## D-020 — Interaction model: osrs-docs pseudocode for players and NPCs (2026-11-05)

Henke splits "loc/obj interaction → movement → player/npc interaction"; osrs-docs gives one routine with two attempt
blocks around movement. The brief requires osrs-docs' semantics exactly (§5.4), so we implement its pseudocode, and
its "same concept applies for NPCs" for NPCs. Both models give the banker-vs-booth timing. Verifier SOURCE-CONFLICT
recorded in tick.md.

## D-021 — PID re-randomisation is per player (2026-11-05)

OSRS Wiki "Player identification number": PID "will randomise every 100 to 150 game ticks ... for each player" (40–60
on BH/PvP/tournament worlds). Each player gets their own window; on expiry they swap to a random position in the
processing order (seeded world RNG). The scenario runner can force an order (`World.forcePidOrder`).

## D-022 — Invalid client input: drop, count, disconnect on sustained rate

Every client command is validated before it reaches content (brief §12): op ranges, loc existence at the clicked tile
(`StaticLocs`), NPC/player existence and view distance (15 tiles, RSProt's info default), button on an open interface,
chat length. Invalid commands are dropped and counted. Stale clicks are legitimate (an NPC walked away), so the
threshold is a rate: more than 50 invalid commands in a 100-tick window closes the session. The numbers are ours.

## D-023 — plugin-api facades + KSP registry found via ServiceLoader

Content modules see only `plugin-api` facades (`PlayerRef`, `PlayerScript`, `InvView`, `ItemTx`, ...); the engine is an
`implementation` dependency of plugin-api, so content can't reach engine internals. `PlayerScript` is
`@RestrictsSuspension`, so content can only suspend through engine suspension points (no thread hops); plugin-api
starts the restricted lambda on the engine script's continuation. KSP (`tools/ksp-registry`) generates each content
module's `<Module>Registry` and its `META-INF/services/tp.plugin.ContentRegistry` line; the server loads registries
with `ServiceLoader`, which reads those generated index files (no classpath scanning). Duplicate handler keys fail at
boot.

## D-024 — DPS-calculator parity matrix; magic accuracy +9

Phase 4's "DPS-calculator parity matrix" is recorded from the OSRS Wiki DPS calculator itself
(github.com/weirdgloop/osrs-dps-calc @ 498bd2ae666c83b0af3ac2265076ed237a27d1aa): `tools/dps-matrix/Matrix.test.ts`
runs it for 688 combinations (14 gear sets, 9 prayer sets, 4–6 levels, every stance; melee, ranged, Ice spells) and
`content/combat`'s `DpsParityTest` recomputes max hit and max attack roll from our formulas and cache params. Only the
output numbers are stored (no calculator code). Two rules came from it:
- Magic accuracy uses +9 (+2 more on accurate staves), where the Wiki "Damage per second/Magic" page shows +8. The
  brief names the calculator as the validation target, so we match it (combat-formulas.md SOURCE-CONFLICT).
- Burst of Strength and Sharp Eye's damage part give at least +1 level (the calculator adds 1 at level ≤ 20; MM:42).

## D-025 — Content modules: combat owns prayers, death items; pvp owns Wilderness rules

`content/combat` holds equipment, styles, prayers (cache enum 4956 + Wiki effects), consumables, attacks, specials,
magic, freezes, Smite/Redemption/Retribution and items kept on death (they need prayer state). `content/pvp` holds
Wilderness levels/brackets, single-way PJ timers, skulls, the logout lock, Tele Block, Vengeance and recoil. They talk
through plugin-api events (`AttackCheck`, `AttackEvent`, `HitEvent`, `DeathEvent`, `AppearanceChanged`) and
`SharedAttrs`. Hits are queued with `EntityRef.queueHit` (strong queue on players, NPC queue on NPCs); queue delays
count the defender's own turns (scripts.md Q13), which yields the processing-order +1 for every hit delay.

## D-026 — Phase 5 topology, versions and multi-world JVMs

- Hub and world-node talk only through `tp.services.HubServices`. The world node embeds a `Hub` (memory or PostgreSQL)
  by default; `--hub host:port` uses the gRPC client instead. The schema is `services/src/main/proto/hub.proto`.
- Pinned versions: grpc-java 1.85.1, grpc-kotlin 1.5.0, protobuf 4.36.2 (Gradle plugin 0.10.1), PostgreSQL JDBC
  42.7.14, HikariCP 7.1.0, Argon2id from BouncyCastle (m=19 MiB, t=2, p=1; OWASP minimum).
- One JVM can run N worlds, each with its own thread and game port (base + i). The OSRS client always connects to
  43594, so in production each world gets its own address (proxy/DNS) in the world list; bots use the ports directly.
- RSProt's thread guard is process-wide. It pins the world thread when the JVM runs one world and is cleared once a
  second world starts; every `Osrs242Network` is still only driven by its own world thread.
- Saves are versioned JSON (`PlayerSave.VERSION` + forward migrations) stored as jsonb. A logout save is final and the
  hub holds the account's session lock until it lands, so a world hop cannot load a stale save.

## D-027 — Gameframe buttons ported from RS Mod

`content/gameframe` handles orbs, settings, emotes, equipment-tab buttons and the side journal sub-tabs
(docs/mechanics/gameframe-buttons.md). The behaviour and the emote/skill-cape seq and spotanim tables come from RS Mod
v2 (ISC, github.com/rsmod/rsmod `content/interfaces/{settings,emotes,equipment,journal-tab,prayer-tab}`, read 2026-11-06).
No source was copied: it was re-expressed against `plugin-api` with gameval symbols, so no ISC header is kept in the
files; each KDoc names the RS Mod script it follows. Plugin-api gained `WorldRef.enumStrings` and `PlayerRef.runMode`
now also writes varp `option_run` (the run orb reads it). The quick-prayer toggle lives in content/combat (prayers.md P6).

Addendum (equipment, price checker, items kept on death, music, XP drops, bond/house options): modals that come with a
list of items borrow the backpack's side slot (`openOverlay` on `toplevel:side3`, backpack reopened afterwards) instead of
RS Mod's `sidemodal`, because plugin-api cannot close a sub-interface; the close is detected by a soft once-per-tick timer
(`ModalWatch`), which also returns price-checker items at logout. plugin-api gained `PlayerRef.varbitFits` (check a client
value before `setVarbit`) and `WorldRef.dbRows` / `DbRowRef` (cache db rows, used for the music table). Items kept on death
sends display-only inventory updates for 584/468 (`PlayerRef.showInv`). No server inventory changes, so nothing goes
through a transaction or the item journal. Display copies must never be real items (brief §12).

## D-028 — Release client: by-value game-client patching and a patch-series fork

- **Game client** (`client/gamepatch`, Kotlin + ASM): RuneLite's `injected-client` jar, mirrored by `setupClient`
  and checked against the pinned bootstrap's sha256. It is patched by rewriting string constants (LDC operands and
  field values) found by value:
  - the RSA modulus (the jar's only 256-hex-digit constant);
  - Jagex URLs, from `client/gamepatch/patches.json`.

  Every rule declares how many sites it must match, and any difference fails the build (test 5). The display-name URL
  is concatenated at runtime (`"https://secure"` + `".runescape.com"` + path), so its two parts are rewritten to
  `{site}` and `""`. RSProx's patcher techniques were the model (MIT, read only, nothing copied).
- **RuneLite fork** (`client/runelite`): a patch series applied to tag `runelite-parent-1.13.6`. RuneLite's full
  source is not vendored, so a revision bump is a rebase of a few small patches. The tag builds from source in the
  sandbox; Gradle 8, JDK 11 target.
- **Artifact integrity:** the RuneLite client itself checks only the external-plugin manifest signature
  (`externalplugins.crt`), which goes away with the plugin hub. Launcher-side bootstrap verification is replaced by
  our launcher's Ed25519-signed manifest.
- **Launcher** (`client/launcher`):
  - **Runtime:** targets Java 11, the major version RuneLite's launcher 2.6.7 bundles (`.jdk-versions.sh`: 11.0.19
    on every platform except macOS arm64, which gets 17.0.7). The jlink runtime follows the same split.
  - **Signature:** the manifest is signed with ECDSA P-256 (`SHA256withECDSA`). Ed25519 would be preferred but needs
    Java 15+.
  - **Behaviour:**
    - a manifest whose signature fails is never used, even when the server is up;
    - when the server is unreachable, the last verified manifest is used;
    - each artifact is sha256-checked;
    - files not in the manifest are deleted.
  - **Hosting:** the hub serves the update directory at `/update`; `release publish` writes it.
  - **Keys:** the dev key pair is in `.data/manifest/`. Its public half is in `launcher.properties`; replace it
    before a public release.

- **Launcher prompt removed (structural patch, `LoginNagPatch`):** "Existing User" sets the login state to 38 ("The
  move is almost here", the Jagex Launcher prompt). No jav_config param removes it (3, 8, 16 tried; 30=false shows a
  stricter screen). The field and its multiplier are found through RuneLite's `client.getLoginIndex()`, not by
  obfuscated name. After each of its 9 writes (pinned as `loginNagSites` in patches.json), `tp.gamepatch.LoginNag.fix`
  turns state 38 into 2 (the login form). The game's methods gain no branches; the helper is a new class in the jar.
  Verified in the client: Existing User opens the login form, and login succeeds.

### D-028 addendum — RuneLite fork: decision per check (brief §4.2 items 3 and 4)

Fork source: `client/runelite/patches`; what the client calls: `client/runelite/ENDPOINTS.md`. The tag, the data
directory and the host guard are the cross-cutting choices; the rest is per check.

- **Allowlist, fail closed:** `PluginManager.loadPlugins`, the one entry point for core, sideloaded and external
  plugins, skips any class not in `allowlist.txt`; a missing list is fatal. A plugin RuneLite adds in a later
  release therefore does not load until it is reviewed. 117 of the 131 core plugins are allowed. Not loaded:
  chatcommands (kc/pb/hiscore lookups through the RuneLite API), opponentinfo (looks up the opponent's hiscores:
  a PvP information advantage), party (needs the RuneLite websocket), loginscreen and emojis (static-host assets),
  devtools. Deleted from the source (third-party endpoints): account, crowdsourcing, discord, hiscore, jagexauth,
  twitch, wiki, xpupdater. Loot Tracker stays: its cloud sync needs a RuneLite session, which cannot exist.
- **Host guard:** the base OkHttp client refuses every host except those of `site`, `api` and loopback
  (`RuneLiteProperties.isReleaseHost`), so a missed call site cannot reach Jagex, RuneLite or anyone else. It
  does not cover the game client's own traffic.
- **Plugin manifest signature (`externalplugins.crt`):** removed with the hub (the certificate is deleted, manifest
  download throws). Not re-rooted, because there is no hub to sign for.
- **RuneLite's installer updater** (downloads `RuneLiteSetup.exe` from GitHub, checks a pinned sha256, runs it):
  deleted. Our launcher's signed manifest replaces it.
- **Telemetry, session ping, XP-tracker submission:** removed; `--disable-telemetry` is now a no-op.
- **RuneLite account, config sync, websocket:** `SessionManager` never loads or opens a session, so `ConfigClient`
  and `AccountClient` are unreachable; the Account plugin is deleted. `runelite.session`, `.ws` and
  `.pluginhub.url` stay as properties pointing at unused hub paths only because the client binds them at startup.
- **Jagex Account / launcher tokens:** `rs/auth`, the Jagex Auth plugin and the `net.runelite:jagexauth` dependency
  are gone, as are `--character` and `--insecure-write-credentials`. Reading `JX_*` environment variables inside
  the game client itself is a game-client matter (`client/gamepatch`).
- **Runtime config (`{api}/config.json`):** kept, served by the hub. The hub may set client properties and system
  properties through it, which is acceptable because the hub is ours and the host guard still applies.
- **World list, item prices, item stats and NPC data:** served by the hub (`worlds.js`, `item/prices.js`,
  `static/item/stats.ids.min.json`, `static/npcs/npcs.min.json`). The Jagex world-address fallback for jav_config
  is deleted. Hiscore URLs point at `{api}/hiscore/<slug>/index_lite.json`; only Player-owned House still uses them.
- **Data directory:** `~/.<name>` (placeholder `~/.tickperfect`) instead of `~/.runelite`, so a player's stock
  RuneLite settings, session and cache are neither read nor overwritten; the `~/jagexcache` copy is dropped.
- **Branding:** placeholder icons and splash are generated (`client/runelite/branding/generate.py`); no RuneLite
  artwork or logo is shipped. The BSD-2 notice and the bundled-library list ship as `LICENSES.txt` in the jar and
  open from the Info panel.
- **Kept as is:** `--insecure-skip-tls-verification` and `--jav_config` (a player can only point their own client
  elsewhere); the allowlist already stops `--developer-mode` from loading anything not listed.

- **Game-client host check:** the class holding the string `invalidhost` accepts a codebase host only if it is under
  `jagex.com` or `runescape.com`, is `127.0.0.1`, or starts with `192.168.1.`. The rules rewrite `.runescape.com` to
  `""`, so its suffix test is true for every host, and rewrite the bare `jagex.com` / `runescape.com` /
  `.jagex.com` hosts to ours. Together these let the client use our own domains and leave no Jagex host in the
  client.
- **Bundling the patched client:** `client/runelite/build.sh` runs the game-client patcher after the fork build and
  overwrites the injected-client classes inside the shaded jar (753 classes). Final-jar scan:
  - exactly one 1024-bit modulus, ours;
  - no Jagex, RuneLite or Discord URLs.

## D-029 — Bank ported from RS Mod; packed layout; name-based loc and NPC handlers

`content/gameframe` has the bank (`Bank`, `BankLayout`, `BankDrag`; docs/mechanics/bank.md). The rules, op numbers, component
roles and tab/placeholder behaviour come from RS Mod v2 `content/interfaces/bank` (ISC, github.com/rsmod/rsmod, read
2026-11-06). No source was copied: it was re-expressed against `plugin-api` with gameval symbols, so no ISC header is kept in the
files; each KDoc names the RS Mod function it follows.

- **Packed layout instead of gaps.** RS Mod lets inv 95 have empty slots between items and compacts on the `bank_compress`
  queue after the bank closes. Here the occupied slots are always packed at the front and the main tab's size is derived
  (RS Mod keeps it in a server-only varbit), so the cache's `bank_tab_1..9` varbits are the only layout state. Every slot move is
  a swap inside one `PlayerRef.transaction` (blocks move by three reversals), which is atomic and journaled; the varbits are
  written after the transaction commits. Cost: a deposit into a numbered tab or a collapse is O(bank size) swaps, on a player
  action, not on the tick path.
- **plugin-api** gained `ContentScope.onOpLocNamed` / `onOpNpcNamed` (loc and NPC ops by cache name + op text, resolved once at
  world start into id-keyed maps, so a click is one lookup; an id-specific handler wins; `onOpNpcNamed` wins over
  `onOpNpcOption`), `IfEvents.DEPTH2`, and `PlayerRef.objStackable / isPlaceholder / placeholderOf / placeholderTarget`. The
  cache has no content groups for "bank booth", so names stand in for RS Mod's `content.bank_booth` / `content.banker`.
- **Not ported at first** (bank.md "Not implemented"): potion store, PIN, confirmation popups, sold slots. Later done: drags
  (onButtonDrag), fillers, worn-bonus texts, wear / eat / drink from the bank (plugin-api EquipRequest / ConsumeRequest,
  handled by content/combat without closing the bank).

## D-030 — Teleports in their own module; backpack item ops by text; chatbox option menu

`content/magic` (registered in `settings.gradle.kts` and `server/build.gradle.kts` like the other content modules) holds the
standard, ancient and lunar teleport spells, the three home teleports, magic tablets and the six charged jewellery families
(docs/mechanics/teleports.md). It is a module of its own so the 700-line gameframe plugins stay about interface panels.

- **Delay.** A teleport is a stalled action: the click handler checks and pays (level, Tele Block, Wilderness, runes or charge, all in
  one `transaction`), then `PlayerRef.startScript` (a strong queue entry, delay 0) runs `delay(n)` and `teleport`. A button handler is
  not a script, and `queueHit` would not stall the player (no `isDelayed`), so `startScript` was added to plugin-api.
- **Backpack ops.** `Equipment` already owns `onButton(Components.Inventory.ITEMS, op)` for ops 2/3/4/6, and one owner per handler
  is enforced at boot, so "Break" and "Rub" could not be registered as buttons. `ContentScope.onItemOp(text)` hands a backpack op to
  its owner by cache iop text before the generic buttons; the owner decides if the obj is its own.
- **Choice menu.** `PlayerRef.choiceDialog` (chatmenu 219 + client script 58, answered by the pause-button path of `ResumePause`)
  is the first multi-option chatbox dialogue; the pending menu lives in `PluginContent` beside `pendingDialogs`.
- **Wilderness.** content/magic cannot see content/pvp, so `Wilderness.updateArea` publishes `SharedAttrs.WILDERNESS_LEVEL`
  (written only when it changes).
- **Not done here (main-session areas):** IF_SUBOP delivery (right-click "Rub" sub-options) is a protocol change, and persisting the
  home teleport cooldown is a persistence change (teleports.md OPEN-TP9, OPEN-TP12).

## D-031: Generic doors and climbing from generated tables, RS Mod translations

The cache has no link between a door's open and closed states or between a ladder and where it leads. `cache-dump
loc-links` derives both:
- door pairs from gameval names, then shared models;
- climb destinations by finding the matching loc on the other floor or 6,400 tiles away, and the reachable tile beside
  it.

The door, double-door and gate movement adapts RS Mod `generic-locs` (ISC; licence header kept in
`content/world/.../Doors.kt`). These are fallback handlers, so content-specific door and ladder code always wins
(docs/mechanics/doors-and-climbing.md).

## D-032: Dialogue steps adapted from RS Mod

The chatbox dialogue functions follow RS Mod (ISC) `ProtectedAccess` and `PlayerInterfaceExtensions`: which
interfaces, client scripts, line heights and page widths. Text wraps on the cache's real `q8_full` glyph advances.
Dialogues are suspending `PlayerScript` extensions, so content scripts read top to bottom (docs/mechanics/dialogue.md).

## D-033: NPC shops from Wiki data, RS Mod rules

- **Data.** The cache has no shop stock, only the client inv ids and their sizes. Stock, multipliers, delta, restock and owner come from
  the Wiki's `{{Infobox Shop}}` / `{{StoreTableHead}}` / `{{StoreLine}}` (`tools/wiki-import/shops.py`, CC BY-NC-SA 3.0, `data/PROVENANCE.md`).
  `cache-dump config npc|obj --all` gained trailing `ops` and `tradeable certTemplate placeholderTemplate` columns for it.
- **Rules.** Price formulas, the message texts, the quantity-mode ops, the restock clock (`tick / rate`) and the default-item slot
  behaviour follow RS Mod `api/shops` (ISC, github.com/rsmod/rsmod, read 2026-11-06). No source was copied: it was re-expressed against
  `plugin-api`, so no ISC header is kept in `content/shops`. Where the Wiki states a formula (Shop, General store, Module:Shop
  calculator) it agrees with RS Mod except for overstocked buying prices (OPEN-SH1).
- **World state.** Stock lives in a world attribute (`EntityRef.worldAttr`), because plugins are shared by every world in the JVM. There is
  no world tick hook, so restocking is lazy: `ShopStock.sync` applies the steps due since an item last changed, and each open shop
  polls it with a one-tick soft timer to refresh the client.
- **plugin-api.** Added `PlayerRef.objCost` (the cache `cost`, unlike `objValue` which prefers Grand Exchange prices).
- **Duplicated gameframe helpers.** `content/shops` cannot see `content/gameframe`, so it repeats the main-modal opening and the backpack
  restore (`SideInventory`) in a few lines.

## D-034: Skilling base and woodcutting data adapted from RS Mod

All non-combat skills live in one module, `content/skills`, over a shared base (`Skilling.kt`, docs/mechanics/skilling.md):
repeating op scripts that keep their interaction, player-global action/animation clocks, and the Wiki success roll
(`PlayerRef.statRandom`). Woodcutting's tick timeline follows RS Mod `Woodcutting` and its tree, stump, axe and rate
tables are adapted from RS Mod (ISC; licence header kept in `WoodcuttingData.kt`). Forestry tree timers are applied
lazily on each swing instead of with a per-tick controller (same counts, no per-tick work).

## D-035: Generic NPC dialogue from Wiki transcripts (default branch, no conditions)

"Talk-to" on NPCs plays the OSRS Wiki `Transcript:` pages (tools/wiki-import/npc_dialogue.py, content/world `NpcTalk`). Conditions and
actions are not modelled: the importer takes the default (negative-reading) branch and ends the conversation at any action it
cannot run. No new plugin-api hook was needed: `onOpNpcOption` already only applies where no id-specific or named handler
exists, which is the loc-fallback behaviour. The data is a node graph (not nested text) so the plugin walks it in a loop
and never recurses in a suspending function. See docs/mechanics/npc-dialogue.md.

## D-036: Mining and fishing from OSRS Wiki data

RS Mod has no mining and an empty fishing module, so both skills are built on the `content/skills` base (D-034) from the OSRS Wiki
(CC BY-NC-SA 3.0, `data/PROVENANCE.md`), with no RS Mod code or data adapted.
- **Mining.** Rock levels, xp, success rates, respawn times, pickaxe cycles and the gem table come from the Wiki's rock, "Pickaxe",
  "Skilling success rate" and "Mining gem drop table" pages (`MiningData.kt`, docs/mechanics/mining.md). Rock loc ids and empty
  rocks come from the cache symbols. The roll cadence is the pickaxe's ticks between rolls; the arming tick follows woodcutting.
- **Fishing.** Spot NPC ids per spot type are scraped from the Wiki's spot pages by `tools/wiki-import/fishing_spots.py` into
  `fishing-spots.tsv`; fish, rates, tiers and the big net table are Kotlin data (`FishingData.kt`, docs/mechanics/fishing.md).
  Spot movement is OPEN-FS1 (the rule is sourced, the tile sets and an NPC relocation call are not).
- **Interaction end.** After a rock depletes or a fish cannot be stored the scripts call `PlayerRef.stopInteraction()`; the tool check
  comes before `keepInteraction()` so a missing pickaxe does not repeat its message every tick. (Woodcutting still checks its axe
  after `keepInteraction()`, so a missing axe repeats its message every tick.)
- **World state.** The amethyst mined-count lives in a world attribute, as the Forestry timers do.

## D-037: Firemaking and cooking from the Wiki, with a "cook all" fallback

- **Sources.** Firemaking logs, levels and xp, the Wiki's lighting success chart (low 64, high 512), food levels and xp, and
  the burn rolls (each food's skilling success chart) are taken from the OSRS Wiki (CC BY-NC-SA 3.0; `data/PROVENANCE.md`). No RS Mod code
  was used (its cooking module is empty and it has no firemaking). Rules and OPEN items: `docs/mechanics/{firemaking,cooking}.md`.
- **Burn roll.** Each food's `low` / `high` per heat source and gauntlets come from its Wiki skilling success chart
  (`tools/wiki-import/cooking_rates.py`).
- **Make-X (OPEN-CK3).** The skillmulti dialogue is not implemented (client script arguments unknown); cooking always cooks all
  raw items of the kind, the Wiki's default left click.
- **Firemaking is a player timer**, not a delayed script, so walking away cancels it like OSRS; a timer waits while a modal is open.
  Ashes are queued on the lighting player when the fire is lit (no world hook for loc expiry: OPEN-FM6).
- **plugin-api.** Added `PlayerRef.walkTo` (a one-step route for the step off a fresh fire).
- **Loc id lists.** The item-on-loc ids for fires and ranges (`RANGE_LOCS`, `FIRE_LOCS`) were generated from cache 2757 by loc
  name and ops; `onUseOnLoc` has no by-name variant.

## D-038: Smithing and Crafting on a shared make-X menu, weak-queue production batches, Wiki recipe importer

Smithing and Crafting live in `content/skills` (D-034) over three shared pieces (docs/mechanics/skilling.md MU, PR):
- **`SkillMulti`** drives the cache's `skillmulti` interface (270) through client script 2046, found by disassembling the rev 242 client
  scripts (RS Mod has no equivalent). A product click arrives as RESUME_PAUSEBUTTON with the quantity as `sub`, which the engine already
  routes to `onButton(op 1)` when no dialogue script waits, so the helper needs no suspension. Other skills (cooking, fletching, herblore)
  reuse `SkillMulti` and `Production`.
- **Batches are weak queue scripts, not `delay()` loops.** `PlayerScript.delay` delays the player (scripts.md D2: clicks are ignored while
  delayed), which would lock a player out of walking away from a 28-bar smelt. Production is therefore a chain of weak queue scripts
  (scripts.md Q7: any click that interacts or walks, and any interface opening, removes them). This needed one `plugin-api` addition,
  `PlayerRef.weakQueue(delay, script)`, mirroring `startScript` with `QueueType.WEAK`; no engine change.
- **Recipes** are `recipes.tsv`, generated by `tools/wiki-import/recipes.py` from the Wiki `{{Recipe}}` templates (levels, xp, ticks, tools,
  materials matched to cache objs). Where Wiki pages disagree the code applies the sourced rule instead (smelting 5 ticks, spinning 2 then
  3, leather 3; smithing.md OPEN-SM1, crafting.md OPEN-CR1). Anvil products, bars and levels are read from the cache (client script 430,
  enums 844 / 845 / 846), not copied; anvil xp is the Wiki's 12.5 per bar times the tier. The anvil keeps interface 312 but takes its
  quantity from the make-X menu (OPEN-SM2). No RS Mod code or data was adapted.
- A menu opened from an op script calls `stopInteraction()`: the engine otherwise runs the interaction again when the modal closes.


## D-039: Thieving (pickpocketing, stalls) and Agility (four courses) from the Wiki and the cache

- **Sources.** Pickpocket levels, xp, stun damage, success `low` / `high` (including gloves of silence) and loot are each NPC page's `{{Thieving info}}`,
  `{{Skilling success chart}}` and `{{DropsLineSkill}}` rows, imported by `tools/wiki-import/thieving_pickpocket.py` into `pickpocket.tsv`. Stall levels, xp,
  respawn, loc ids and loot are the stall pages and "Stall/Thievable" (`thieving_stalls.py` -> `stalls.tsv`). Agility course order, loc ids, xp and levels
  are the course and obstacle pages, with loc placements from the cache (`agility_courses.py`). No RS Mod code was used (it has no thieving or agility).
  Rules and OPEN items: `docs/mechanics/{thieving,agility}.md`.
- **Coin pouches.** The Wiki loot tables say "Coins"; in game the loot is a coin pouch (cache objs 22521..22538, 24703). The importer replaces the coin row by
  the NPC's pouch with the contents from the "Coin pouch" page; "Open" / "Open-all" are `onItemOp` handlers (one owner per op text).
- **One attempt per click.** Pickpocket, steal and obstacle scripts end with `stopInteraction()` before they `delay`: a delayed player is "blocked", so the engine
  does not clear the interaction and would otherwise run the op again every time the delay ends.
- **NPC matching.** `onOpNpcOption("Pickpocket")` looks the NPC up by cache name (id ranges for the elves, the vyres and the Bandit Camp bandits), so one handler
  covers every spawn; NPCs without a sourced success chart are left out (OPEN-TH1).
- **plugin-api.** Added `DamageRequest` (CombatEvents.kt): content asks the combat module to deal a plain hit. Until combat subscribes (a main-session change
  in `content/combat`), the stun hit is applied inline and cannot kill (OPEN-TH2).
- **Forced movement.** No exact-move message exists in plugin-api or protocol-api; obstacles animate, delay and teleport (OPEN-AG1). Failing obstacles are not
  implemented because the Wiki gives no chance (OPEN-AG2).

## D-040: Fletching, Herblore and the Cooking make-X menu on the shared recipe table (fletching/herblore/cooking agent)

Fletching and Herblore live in `content/skills` over the pieces of D-038 (`SkillMulti`, `Production`, `recipes.tsv`); no engine or
plugin-api change.
- **Recipes.** `tools/wiki-import/recipes.py` also reads the Fletching and Herblore `{{Recipe}}` templates (lists `ANY_OUTPUT_PAGES`,
  `GRIND_PAGES`): `recipes.tsv` grew from 108 to 469 rows and the first 108 are byte-identical to before. A potion page's output is the
  dose-suffixed item, so those pages are read for every Fletching / Herblore recipe with at most two materials (`MAX_ANY_MATERIALS`);
  the pestle-and-mortar pages have no skill in the template and are imported as level 1, 0 xp Herblore rows. `Recipe` gained
  `firstTicks` (the Wiki's "Make-X is 2, then 3"); the skill column maps to a stat in `RecipeTable`.
- **Starting a recipe** is an item-on-item use: the pair is the two materials, or the tool and the only material (knife on logs, chisel on
  a gem). Rows with the same pair share one menu.
- **Darts and bolts are instant** (Wiki `ticks` 0: "two clicks per set"); the account setting that turns their make-X menu on is not
  modelled (fletching.md OPEN-FL1). Sets of arrows, javelins and tipped bolts use the "Make sets:" menu type (cache enum 1809 key 2) at 2
  ticks; javelins are forced to 2 because their Recipe ticks disagree by tier.
- **Animations** are looked up by the Wiki product name in `FletchingData` (the cache's `human_fletching_add_*`, `xbows_*`, `stringing_*`
  seqs). Javelins and chisel bolt tips have none (OPEN-FL3).
- **Cooking make-X** (closes OPEN-CK3, found on the Wiki "Cooking" page): two or more raw items of a kind open `SkillMulti` (type 6, the
  raw food as the product); one item is cooked at once as before. A menu choice is a weak-queue batch (first cook at the choice, then
  every 4 ticks, waiting for the player's cooking clock if they cooked in the last 4), a single item stays the op script that keeps its
  interaction. Burn logic and tick timing of the cook are unchanged. `SkillMulti` gained the `COOK` and `MAKE_SETS` type keys.
- **Herblore** "Clean" is an item op (`onItemOp`) that auto-continues over the other herbs of the kind, one per tick (unsourced timing:
  herblore.md OPEN-HB2); the pestle crushes every item of a kind, first at once, then after 2 ticks, then every 3.

## D-041: Runecraft and Prayer training (rune crafting at 13 altars, bury / scatter, Chaos Temple altar)

- **Sources.** Rune levels, xp, multiple-rune levels, tiara xp and ruin tiles are the Wiki's "Runecraft", "Mysterious ruins", "Runic altar" and altar pages, copied by
  hand into `RunecraftData.kt`; loc ids, ops, the `rc_no_tally_required_*` varbits and the altar and exit-portal placements are read from the cache. Bone and ash levels and
  xp are the `{{Prayer info}}` templates, imported by `tools/wiki-import/prayer_remains.py` into `remains.tsv`. No RS Mod code was used. Rules and OPEN items:
  `docs/mechanics/{runecraft,prayer-training}.md`.
- **Mysterious ruins are multi-locs.** The map holds the parent (e.g. 34813); the client shows child 0 (no op) or child 1 ("Enter") by a varbit. The plugin registers the
  parent and both children, and sets each varbit from the worn head and cape slots with a soft timer every 3 ticks, because plugin-api has no equipment-changed event and
  `content/combat` owns equipment (OPEN-RC4). The "Enter" op is re-checked on the server.
- **Arrival and exit tiles are derived.** No source gives them; the plugin picks the walkable tile beside the exit portal nearest the altar, and the walkable tile around the ruins
  nearest the middle of the south side (OPEN-RC1).
- **Per-obj item ops.** "Bury" and "Scatter" are registered per obj with `onItemOp(obj, text)` (one handler per bone), leaving the global op texts free.
- **The 1-tick hold after burying** is the brief's figure, not a sourced one (OPEN-PT1); it is a named constant.
- **plugin-api.** No change.

## D-042: Slayer and Hunter skills (slayer/hunter agent)

Both live in `content/skills` (`Slayer.kt`, `Hunter.kt`) with no engine, combat or plugin-api change. Rules and OPEN items:
docs/mechanics/slayer.md and hunter.md.
- **Data over code.** `tools/wiki-import/slayer_tasks.py` writes `slayer-tasks.tsv` (326 weighted task rows of nine masters),
  `slayer-monsters.tsv` (2,217 npc ids with their task group, Slayer level and Slayer xp from each `Infobox Monster`) and
  `slayer-masters.tsv` (where to place the masters); `tools/wiki-import/hunter_creatures.py` writes `hunter.tsv` (21 creatures: five
  birds, three chinchompas, a butterfly and twelve implings). Both re-run against the live Wiki.
- **Persisted task.** The task is three `saved` Longs (group and master as `String.hashCode`, kills left), plus streak, Krystilia's
  own streak and points. The group key is the infobox `cat` (lower case letters and digits, plural s dropped) shared by the task
  tables and the monster rows; a hash collision between two groups fails the plugin's first use.
- **Quests and unlocks are never assumed.** Task rows with a quest or a reward-unlock requirement are skipped (`QUESTS_ASSUMED_COMPLETE`
  flag, default false), as the brief asks for unsourced requirements; this leaves about 180 of 326 rows. The masters' own access quests
  are walking access and are not checked.
- **The Slayer level gate is the `AttackCheck` event** that `content/combat` already publishes before every attack; no combat code changed.
- **Masters are spawned by the Slayer plugin** at their Wiki infobox positions because `npc-spawns.tsv` only has Krystilia. Turael, Mazchna
  and Duradel (the pre-quest npcs) are spawned; their quest replacements answer the same ops.
- **Traps are loc state plus a per-player timer.** A laid trap is a dynamic loc and an entry in a player attribute; a player timer runs
  once a tick over the player's (at most five) traps and a lure every third tick scans the world's tracked creature npcs
  (`onNpcSpawn` collects the trapped kinds in a world attribute, in spawn order). No per-tick work happens for a player without traps.
  Creatures removed by a catch use `NpcRef.respawnAfter`.
- **Ferret, three butterflies and the other box-trap creatures are not implemented**: the Wiki has no success chart for them.

## D-043: Construction groundwork: sawmill planks and Wiki furniture/room tables; player-owned house blocked on dynamic regions (construction agent)

`content/skills` gets `Construction.kt` (sawmill operator "Buy-plank") and `ConstructionData.kt`; no engine, protocol or plugin-api change.
Rules and OPEN items: docs/mechanics/construction.md.
- **The house is not implemented.** The engine has only the static `Rebuild` (build area on the real map); a POH needs `RebuildRegionV2`
  (present in vendored RSProt), per-player instance coordinates, instance collision and visibility. The design note is in construction.md
  ("Engine gap"); it belongs to the main session (engine/, protocol*).
- **Data first.** `tools/wiki-import/construction.py` writes `construction-furniture.tsv` (451 pieces: level, xp, room, hotspot, loc ids, materials)
  and `construction-rooms.tsv` (24 rooms). Both are validated at plugin start so a bad import fails early; nothing reads them yet.
- **Buy-plank uses the chatbox option menu and number prompt** because the cache does not name the Wiki's sawmill interface (OPEN-CO1).

## D-044: Farming (allotment, flower, herb, tree and fruit tree patches; patch varbit tables adapted from RuneLite)

- **Sources.** Crop levels, xp, growth times, seeds per patch and harvest-life constants are the Wiki's `{{Farming info}}` / `{{Farming yield calculator}}` templates of 41
  plant pages, imported by `tools/wiki-import/farming_crops.py` into `farming-crops.tsv`; disease, growth timing and harvest lives are the Wiki's "Farming" and "Crop
  disease" (docs/mechanics/farming.md, with OPEN-FM1..12). Loc ids, shared varbits and patch placements are read from the cache.
- **Adapted RuneLite table (BSD-2).** `farming-varbits.tsv` is generated by `tools/wiki-import/farming_varbits.py` from RuneLite's
  `runelite-client/.../timetracking/farming/PatchImplementation.java` (`forVarbitValue` of ALLOTMENT, HERB, FLOWER, TREE and FRUIT_TREE; Copyright (c) 2019 Abex, BSD-2-Clause).
  The file keeps the copyright notice and licence text as `#` header lines; nothing else of RuneLite is used. The table was checked against the cache: every
  diseased / dead row resolves to a child loc named "Diseased ..." / "Dead ...". It is the oracle for rev 242; a revision bump re-runs the importer.
- **Patches share four varbits.** Every farm's patch locs use `FARMING_TRANSMIT_A..D`, so the client draws whichever farm the server last wrote. A soft timer (3 ticks) finds the
  nearest farm (52 tiles) and writes its patches' varbits, again once a minute. A click reports the multi-loc parent id (unique per patch), which names the patch; the action
  is chosen from the saved state, not the child's op text.
- **Growth is lazy and wall-clock.** The state of a patch is one saved Long plus the minute it was last updated (`saved`); nothing runs per tick. Looking at a patch simulates the growth
  ticks since (multiples of the cycle in epoch minutes), so crops grow offline. Disease rolls use the world RNG when the patch is looked at, not at the tick.
- **Rev-242 behaviour.** The Wiki's 3 June 2026 change (planting xp at the end, spade on growing plants) is not applied (OPEN-FM1).
- **plugin-api.** No change. `content/skills` only; the sapling / plant-pot stage, compost bins, tool store and farmer protection are OPEN-FM4/FM12.
- **Expansion (all patch types, saplings, compost bins, tool store, protection, contracts).** `farming_varbits.py` now imports 20 `PatchImplementation` layouts
  (same BSD-2 source and header), `farming_crops.py` 76 crops plus `farming-saplings.tsv` and `farming-payments.tsv`, `farming_contracts.py` the 71 contract tasks and the
  seed pack tables. The patch behaviours are table-driven by `PatchType` (harvest mode, check-health, cure tool, compost lives) instead of branches on type. Patch locs, slots and gardeners
  are `FarmingSites.kt` (loc ids found with `find-locs` by `multiVarbit`, slots and gardeners from RuneLite `FarmingWorld`). `PatchState` keeps its layout and gains the
  paid flag and two more crop bits at the top, so saved states stay valid. Seedlings grow lazily (a saved clock, converted in the backpack and bank on login, on the
  3-tick timer and when watering), a compost bin is one saved Long plus the closing minute, the tool store one saved count per tool. Unknowns are OPEN-FA1..18 in
  docs/mechanics/farming.md (Tithe Farm is OPEN-FA15). plugin-api: no change; proposals are listed in the task report.

## D-045: Instances as copied zones in instance space

Instanced areas are real coordinates in empty map squares (x ≥ 100), not a coordinate translation layer: every system
already keyed on coordinates (collision, zone sync, ground items, NPCs, the info packets) works unchanged, and only the
build-area rebuild differs (REBUILD_REGION with the template zone table). Collision is rebuilt from the template's tile
settings and rotated locs rather than by rotating flag bits, and copied locs are a silent base layer under the dynamic
loc changes. See docs/mechanics/instances.md.

## D-046: World entity configs decoded from the rev-242 client

Boats are world entities whose configs live in archive 2 group 72, which no reference decoder (RuneLite 1.13.6, RS
Mod, RSProt, RSProx) covers. The opcode table comes from the rev-242 client: the class RuneLite maps to
`net.runelite.api.WorldEntityConfig` is constructed from the config buffer, and each `case` of its opcode switch was
matched to the buffer read it performs (byte, signed/unsigned short, string, 2-or-4-byte smart). Field meanings beyond
the RuneLite API names (id, category, bounds) are kept as neutral `opN` names until content needs them, OPEN.

## D-047: Player-owned houses as content on instances (construction agent)

The house is `content/skills` only (`PlayerOwnedHouse`, `HouseMenus`, `HouseBuilder`, `House`, `Poh*`); no engine, protocol or
plugin-api change. Rules and OPEN items: docs/mechanics/construction.md H1-H9, OPEN-CO2..CO11.
- **Locs are read back from the instance, not from a data table.** After a room zone is copied, `PlayerRef.locAt` is asked for the
  four loc layers of each of its 64 tiles; locs whose cache name marks a hotspot or doorway are grouped (same name, touching tiles)
  and keyed by their lowest template tile, so a saved layout does not depend on a room's rotation. This avoids a generated hotspot
  table that would go stale with the cache; the cost is one scan per room per visit.
- **Only the template zone table is data** (`PohRooms.ALL`, found by scanning the template map for each room's hotspot names, see
  construction.md). The room type is its index there (append only), as in the saved layout.
- **The layout is saved in the player's `saved` Longs** (three 18-bit room slots per Long, one furniture entry per Long), no
  persistence change; encoding in construction.md H8.
- **Hotspots are hidden by the server** outside building mode (loc deletion) because the cache gives the client no way to do it that
  was found (OPEN-CO8). Switching building mode reverts or deletes them in place, with no house reload.
- **Floors are instance levels** (basement 0, ground 1, upper 2) and the template level equals the floor, but only the ground floor is
  buildable until stairs exist (OPEN-CO6). The pickers are chatbox menus (OPEN-CO4): the cache interfaces that would replace them
  could not be driven from the disassembled client scripts.
- **Worlds hold the houses** in a world attribute keyed by owner; the last player out destroys the instance. A soft timer per
  visitor (2 ticks) notices teleports out.

## D-048: Sailing data layer reads the cache at world start; Wiki-only data is checked-in TSV (S0)

`content/sailing` (depends on plugin-api + symbols only) holds `SailingData`: typed, immutable readers for the Sailing db tables
(docs/plans/sailing.md 6.2), built from `WorldRef.dbRows` when the world starts, and cross-checked (`check()`: every part option,
hotspot, stats link and port task dock must resolve) by the `Sailing` plugin, so a wrong cache stops the boot. No plugin-api change.
- **Cache first, no copies.** Tables are read, not generated into source, so the readers stay correct when the cache changes. Column
  ids are the generated `DbTables` symbols; a tuple column is addressed as (repetition, slot) as in `MusicTab`. Unset stats cells read
  0, except acceleration (OPEN-S3) which stays null. Part references are kept as db row ids; lookups are maps built once.
- **Server-only data is imported once.** The cache strips task XP, dock coordinates, charting locations and more (plan 0, 6.5). `tools/wiki-import/sailing.py`
  writes TSVs under `content/sailing/src/main/resources` (courier and bounty tasks with XP, charting tasks with tiles, level-up table, docks,
  part-stat cross-check rows, buoy coordinates from the plan). Only the task XP is loaded so far (`PortTaskXp`); the others are
  data for S3, S5 and S4 to wire when they need them. The importer is re-runnable and reads only Wiki templates (no scraped HTML).
- **Wiki gaps recorded, not guessed.** The Wiki lists 441 of the cache's 443 courier tasks (the two missing are the placeholder rows
  601 and 602) and 60 of its 61 docks (Last Light is missing); mechanics docs `docs/mechanics/sailing-*.md` carry the OPEN items.
- **Placeholder rules.** Barracuda rank tuples were decoded as (target ticks, laps, supplies, bonus XP x10, trial XP x10) and checked against the three Wiki
  tables; the crew id-to-name mapping and the `(stat, level)` crew requirement tuples are derived by matching values, flagged [D] in the docs.

## D-049: Non-combat spells as content/magic, with one plugin-api hook (magic-utility agent)

Rules and OPEN items: docs/mechanics/magic-utility.md. All spells are `content/magic` plugins; no engine, protocol, combat, pvp or skills change.
- **One rune-payment path.** `SpellRunes.take` pays a spell inside the spell's own item transaction (equipped elemental staff supplies its element, combination runes cover both elements, the rest is plain runes), so a spell that fails after paying costs nothing. The existing spell teleports were switched to it; content/combat's combat spells still remove plain runes (main session).
- **Cast clock and a one-deep queue** (`Casting`): a spell cast before the previous one's Wiki "speed" is over is queued once as a weak script (Wiki: Superheat / enchant queue one spell), not dropped and not stacked.
- **Items by cache name.** The spell tables list the Wiki's item names and resolve them once at world start (`WorldRef.objIdsNamed`); a name the cache lacks drops that row. Exact items (runes, bones, fruit, vessels) use `tp.symbols`.
- **`ContentScope.onApObjT`** is the one plugin-api addition: Telekinetic Grab must run from range 10 over obstacles, and the engine already supports ap scripts on ground-item targets (`ContentHandlers.apObjT`) but the plugin facade returned null.
- **Effects owned by other modules stay OPEN** instead of being edited from here: the Charge boost to god spells (combat), Shadow Veil in pickpocketing and Resurrect Crops (skills), Vengeance Other, combat spells ending a Spellbook Swap. The timers and attrs they need are kept here under named keys.
- **Reanimation** is limited to the Dark Altar's range because no drop-location record exists for ensouled heads (OPEN-MU12), which is stricter than the game rather than a guess at its rule.

## D-050: Sailing S1 - boats are world entities owned per player, saved in `saved`, found by loc id (S1)

`content/sailing` gains `Boats` (spawn/despawn a boat: world entity, deck copied from the hull tier's template, part locs at db coordinates),
`ActiveBoats` (world-attribute registry; the interface for the motion code), `Docking` (gangplank, mooring point, buoy, helm ops, logout),
`BoatSave` (ownership) and the Pandemonium quest. Encodings and rules: docs/mechanics/sailing-boats.md B10-B12, sailing-ports.md P9-P13,
sailing-pandemonium.md.
- **Ownership in `saved`, one Long per slot** (61 bits, documented in `BoatSave`); no persistence change. The cache boat varbits (`SAILING_BOAT_n_*`)
  that interface 934 reads are not mirrored (OPEN-SEL1).
- **Docks are found by loc id arithmetic** (gangplank 59835 + id, mooring 59867 + id - 32, buoy 59769 + id), because the cache locs are nameless
  multi locs and the client sends the base id; land / sea tiles come from the checked-in TSVs joined to db 194 by normalised name.
- **Server state in the plugin instance** (`SailingState`, set at world start): plugins are per world, tables exist only after world start.
- **BoatMotion is reached over the world's event bus** (`BoatMotion.Lookup`), since plugins are separate instances and `WorldRef` has no attributes.
- **plugin-api addition:** `chatNpcAs(npcType, name, text)` (a chat head that is not the clicked NPC; `chatNpc` now calls it), for conversations between
  two characters.
- **Will and Anne's boat uses config 10** (not 3 as first assumed): 3 is the sloop (OPEN-W1).
- **Only the deck level (1) of a template is copied** (level 0 draws a dark rectangle).

## D-051: Combat spells are one generated table and one casting engine; autocast mirrors client script 243

- **Generated table** (`CombatSpellTable.kt`, from `docs/research/combat-spells.tsv` by `tools/wiki-import/combat_spells_kt.py`): every symbol is
  checked by the generator; effects are mapped per spell in the generator, not parsed at runtime. Tele Block and Vengeance stay in content/pvp.
- **`SpellRunes` moved to plugin-api** so combat and magic pay runes the same way (staves, combination runes).
- **Cross-module state goes through `SharedAttrs`** (Charge, Shadow Veil) and events (`CombatSpellCast` ends a Spellbook Swap, `SlayerTaskCheck`
  asks the Slayer module about the slayer's staff (e)).
- **Autocast lists are a server copy of client script 243** keyed by the varp 664 layout key, so the server accepts exactly what the client shows;
  the autocast id maps to our spell through cache enum 1986 and obj param 596.
- **Item charges per player** (Iban's staff, slayer's staff (e)) in `saved` counters until an item-charge store exists (OPEN-CM4).
- **NPC attributes and elemental weakness** were added to the Wiki NPC import (`attributes`, `weak`, `weakpct` columns).
- **Interactions from a deck into the root world** (WE11): no route, reach while the pivot is within 31 tiles; content checks the real distance.
## D-052: Sailing S3 - port task state in `saved`, client-built boards, hold sized by `showInv` (S3)

`content/sailing` gains `PortTasks`, `PortBoards`, `PortLedger`, `CargoHold`, `PortMaster`, `PortRewards`, `PortCatalog`, `PortSites`
and `PortTaskSave`. Rules and OPEN items: docs/mechanics/sailing-port-tasks.md. No plugin-api, engine or protocol change.
- **Task state is in `saved`, the cache varbits are a mirror.** One Long per slot plus counters (`PortTaskSave`), copied into the
  `PORT_TASK_*` varbits at login, on every change and on a Sailing level gain; the client scripts of the captain's log read only those.
  `setBit` throws when a value exceeds the varbit width, so every mirrored value is clamped to the width in the cache (found by running
  it: `PORT_TASK_LAST_CARGO_TAKEN` is 3 bits, so it holds a slot, not a task id).
- **The boards are the cache interfaces, driven from the disassembled client scripts.** Unlike the construction pickers (D-047) the
  scripts for 941/942/935 could be followed: 8912 takes 8 db row ids, 8901 fills the info panel, 8707 builds the log from the slot varbits,
  and the board's "Select" op is the first of 6 components per task. That mapping and the hold's item grid component are unverified in a
  real client (OPEN-PT2); a chatbox menu fallback was not built.
- **Crates are worn, as in the cache.** A crate has wear positions 3 and 5, so "carrying" is the weapon slot; `Cargo` mirrors it into
  `SAILING_CARRYING_CARGO`, which the ledger-table and hold multi locs resolve through. Taking and handing in are single transactions. Blocking
  unequip and teleports belongs to `content/combat` / `content/magic` (OPEN-PT3); a 22-tick timer confiscates a crate carried out of port.
- **The hold is sent with its capacity as size.** The client reads capacity as `inv_size`, so `showInv` sends `CARGOHOLD_SIZE` slots of
  inventory 963 + slot instead of the cache's 240; the server inventory keeps 240 slots and fills them lowest-first, and moves check the
  capacity first. Inventories are saved by the existing player save. Until the shipyard (S4) every owned boat gets the cheapest hold its
  hotspots allow (OPEN-PT5).
- **Board selection is a pure hash, not the RNG.** Which tasks a board shows is unknown (OPEN-PT1); the pick is a SplitMix64 mix of
  (account, UTC day, completed / 8, board), stable and replayable; the world RNG is used for bags and paint only.
- **Bounty tasks use the Wiki's current rewards:** coins equal to the base XP and no bag (2 Sep 2026), not the plan's "+ a coin bag".
- **Wiki importer:** `bounty-tasks.tsv` gained a `guaranteed` column (the board's always-available bounty).
- **Pandemonium integration:** Jim's send-off gives port task 599 through `QuestCargo`; its extra 100 XP is the task's, no longer paid by the quest.
## D-053: Sailing S4 - the shipyard is a build-space boat per player, interface 939 is driven for real, extras are new saved keys (S4)

Rules, tick timelines and OPEN items: docs/mechanics/sailing-building.md. New files in `content/sailing`: `Shipyard` (session, island locs, shipwright
Customise-boat), `BoatBuilder` (checks, build, removal), `Customisation` (interface 939), `BoatServices` (buy, recover, destroy, repair, rename, bottles,
summon), `BoatStats`, `ShipbuildingData`, `BoatItems`; `Boats` gained `rebuild`; `Docking` got two hooks. No engine, protocol or plugin-api change.
- **The shipyard is a per-player build space, not an instance copy.** "Customise-boat" despawns the live boat of the port and spawns the same boat as a
  motion-less world entity at the dry dock of the island (pseudo dock id 62, `Shipyard.DOCK`); the owner boards it with the normal boat op, hotspot
  placeholders show on its empty hotspots. The island coordinates are chosen from the collision (OPEN-SY1) because db 174 is stripped. `Docking.escape` and
  `Docking.leave` call the shipyard (get off the deck / end the session on logout) so the boat is never saved with dock 62.
- **Interface 939 is driven, not replaced by a menu.** The client builds the screen; the server writes varp 5190, varbits 19523-19525 and the
  `SAILING_BOAT_n_*` varbits of the slot (encodings read from client scripts 9067/8798-8805: parts are option indices, flag/brazier/hotspots +1) and handles
  the Build button, whose cc sub id is the db row (script 8832). The row is validated against the option lists. The boat selection interface 934 is still the
  chatbox stand-in (OPEN-SEL1) used by every picker here.
- **Saved layout stays; extras are new keys.** `sailing.boatx.s` and `sailing.boatf.s.0/.1` hold cosmetics, facilities, bottled and capsized flags;
  `OwnedBoat` got defaulted fields so every existing constructor call and save is unchanged. Facility bottles are a list under `sailing.fbottle.*` because
  the item carries no data.
- **Wiki-only costs are a checked-in TSV.** `shipbuilding.tsv` (Construction XP, shipwright fees) is written by `tools/wiki-import/sailing_build.py` from
  `part-stats.tsv` plus the fee pages; `ShipbuildingData.check()` fails the world start if a cache facility has no entry or a facility loc is not in
  `Shipyard.FACILITY_LOCS` (locs are registered by id before the db tables exist).
- **Boost rule.** Build levels use the current level (crystal saw +3 Construction); the cargo hold is the one support facility with no level on use,
  `ShipbuildingData.canOperate` is the check for S6/S7/S9/S10. Shipwright fee only replaces the Construction level, gives no XP.
- **NPC and loc scripts call `stopInteraction()`** first, as the thieving and hunter content does, because a chat menu leaves the interaction running and
  the op would run twice.
- **BoatRelocated event** tells the cargo content (S3) to drop crates and salvage on recover, destroy, summon and unbottle; S3 subscribes, nothing here
  touches the hold inventories except the emptiness checks.
- Not done: Bank PIN (OPEN-SY2), Seb's shop, dialogue text of the shipwrights, schematic discovery (`LOST_SCHEMATIC_*` are only read), Pandemonium's cargo hold
  still bypasses the builder (OPEN-PAN1).


## D-054: Weapon and NPC animation data from RS Mod's obj data and from the cache's skeletons

- **`weapons.tsv`** is converted from RS Mod `api/cache-enricher/.../objs.toml` (ISC) by `tools/wiki-import/rsmod_weapons.py`: weapon
  category, per-slot attack anims, block anim, render anims, sounds, speed / range overrides, ammo projectiles (1,331 objs). The live game keeps
  these server-side; RS Mod's table is the most complete open source.
- **`npc-anims.tsv`** is derived from our own cache (seq skeleton + gameval names), because no open data source lists NPC combat anims.
- **Projectile heights** are MAP_PROJANIM_V2 units (RSProt dropped the implicit × 4); the earlier 40 / 43 values were 4× too low. Shapes
  are RS Mod's `ProjAnimBuilds`; arrival stays on our hit tick (golden scenarios).
- **protocol-api `Appearance.renderAnims`** carries the weapon's base animation set.

## D-055: Special attacks are a generated table plus rules by special name

`SpecialTable.kt` is generated from `docs/research/special-attacks.tsv` (cache enum 906 lists the objs, so no weapon is missed). Numbers the
Wiki gives as plain multipliers are data; the formula specials, hit patterns, multi-target shapes and effects are code in `SpecialEffects.kt`
keyed by the special's name, so one rule serves every variant obj. Leagues / Deadman specials keep only their generic row (OPEN-SP2).

## D-056: Clans live in the hub; worlds forward requests and draw full snapshots

Clan state (members, ranks, bans, rules) is cross-world, so it is owned by the hub (`Clans.kt`, PostgreSQL tables `clans`, `clan_members`,
`clan_bans`), like friends chat. Every change resends the whole channel and settings to each online listener (CLANCHANNEL_FULL /
CLANSETTINGS_FULL) instead of deltas: clans are at most 500 members and change rarely, and full snapshots cannot drift. Content asks
for actions through `PlayerRef.clan(ClanAction)`; client packets (clan chat types 3/4, kick, ban) are validated by the engine and go to the
same hub calls. Founding, recruiting and guest joins are driven from content buttons because the client's own join packet is unconfirmed
(docs/mechanics/clans.md OPEN-CL8).

## D-057: Collection log keeps counts as saved values; inv 620 is display state

- **Counts are `saved` values** (`clog.<key>`, kill counts `clog.kc.<name>`), not an inventory. The cache's only collection inv,
  `collection_transmit` (620), has 500 slots, the log has 1,738 distinct slots; the client reads it with `inv_total` per obj, so the
  server fills it per page click (current page first, then other obtained items while slots remain) and empties it when the log
  closes. That keeps the save small (no 500-entry inventory per player who ever opened the log).
- **Layout is read from the cache, not the Wiki**: `collection-log.tsv` is written by `tools/wiki-import/collection_log.py` (cache
  structs/enums plus the client scripts 2735 and 4103), so the pages and slot lists are the ones the client draws. A revision bump regenerates it.
- **Redraw by the client's own script** 7797 (the resize handler of the interface, which re-lays out the window, selects the tab and
  draws the list and page) rather than recreating the layout with several scripts; it is sent one tick after the counts, because vars and
  invs go out in the info pass at the end of a tick.
- **`LootEvent`** (plugin-api) is the one hook for "player was given X": published by content/drops after the ground objs exist; other
  sources (chests, caskets, rewards) publish the same event when they exist.
- Sources and OPEN items: docs/mechanics/collection-log.md.

## D-059: Quests are one module; the cache's Quest table is the data, a derived TSV maps rows to variables

- **`content/quests`** holds the shared quest API (`Quest`, `QuestCompletion`, `QuestFramework`) and one plugin per quest. A quest's
  state is the varp or varbit the client reads (client script 4024 maps a `Quest` db row to it), so the client colours the quest
  list itself; the server writes the variable (docs/mechanics/quests.md QU1-QU2).
- **`quest-states.tsv`** (219 rows) is generated from `cache-dump dbrows 0` plus the switch of client script 4024, because the
  variable is not in the db table. `QuestFramework` compares the file with the live rows at world start (fail fast on a revision
  bump), the same pattern as `ShipbuildingData.check`. It lets the server keep the "Completed x/y" varbits right for quests that
  other plugins implement later.
- **Intermediate stage values are ours** (the cache only gives not-started and finished); every quest file lists them as OPEN-QU1.
- **Quest NPCs that the Wiki spawn import misses are spawned by `QuestSpawns`**; sheared sheep in Fred's pen are turned into
  shearable ones there (the import picked the sheared infobox version).
- **Ingredient sources** (milking, wheat, windmill, shearing) live in the quest module (`FarmSupplies`) until a farming-sources
  plugin exists.
- Rule ids are `QU`, open items `OPEN-QU` (`CL` is taken by clans).

## D-058: Achievement diaries: Wiki data, saved task bitmasks, kill tasks through DeathEvent

- **Data from the Wiki, shape from the cache.** The tasks, requirements and rewards are the Wiki's (`achievement_diaries.py`); the
  number of tasks per tier is checked against the client's own table (script 4073) at import, and the area order is cache enum 595.
  The diary task varps (1176..1199) have no varbits and no client script reads them, so only the varbits the scripts do read are
  mirrored (counts, tier complete, reward claimed, started).
- **State** is one saved bitmask per area and tier (`diary.<area>.<tier>`), not a varbit per task: no per-task varbit exists for 11 of
  the 12 diaries and the largest tier has 19 tasks.
- **API**: `Diaries.complete(player, TaskId)` in the module (content modules depend on plugin-api only, so other content reaches it
  by publishing an event; none is defined yet). Only kill tasks are hooked, through the existing `DeathEvent`; every other trigger is
  OPEN until its content exists, instead of adding events to plugin-api or handlers that would collide with the content's own.
- **Taskmasters replace the generic Talk-to** (their Wiki transcript is no longer played); a claim menu is the only way to give the
  reward without a conditional-dialogue feature in the transcript engine.
- Sources and OPEN items: docs/mechanics/achievement-diaries.md.

## D-060: Static item spawns from the Wiki; F2P quests that need world puzzles ship with named gaps instead of invented mechanics

- **Static item spawns** (`item-spawns.tsv`, tools/wiki-import/item_spawns.py, `content/world` `ItemSpawns`): 2,615 spawns from
  `{{ItemSpawnLine}}`. Rows of instanced / minigame Wiki maps (map id 10000 and up) are skipped, quantity ranges take the lower bound,
  every spawn respawns after 100 ticks (the Wiki's optional `respawn:n` has no published unit; ground-items.md OPEN-G6). Members rows are
  included: the server is members-enabled.
- **Quest-owned rules go in the quest plugin, not in other modules.** The rat's tail (Witch's Potion) is dropped by the quest plugin
  because content/drops attaches the Wiki row to the wrong NPC version (OPEN-QUA4); Count Draynor / Delrith use `DeathEvent` and
  `AttackCheck` only. Where a rule needs a combat hook the plugin API lacks (death prevention until a condition, area entry) the step
  is marked OPEN (OPEN-QUA16, OPEN-QUA19) and a hook is proposed rather than worked around in combat code.
- **Walls without an open model** (closet door, sturdy doors, fake walls): `stepAcross` moves the player to the other side of the wall
  (OPEN-QUA6). The Ernest the Chicken lever maze is not implemented because the door / lever locs cannot be tied to the Wiki's labels
  from published sources (OPEN-QUA1).
- Open items of these quests are `OPEN-QUA<n>` (docs/mechanics/quests.md) to stay apart from the parallel `OPEN-QU` list.

## D-061: Quest batch B: shared helpers, multi-NPC stages, dialogue generated from the Wiki transcripts

- **Batch B** (Pirate's Treasure, Prince Ali Rescue, Shield of Arrav, Dragon Slayer I, Below Ice Mountain, The Corsair Curse,
  Misthalin Mystery, X Marks the Spot) lives in `content/quests` beside the first batch and follows D-059. Its own helpers are in
  `QbHelpers.kt` (`QuestsB` rows, `passDoor`, `DigEvent` with the `QuestDigging` spade plugin) and its spawns in `QuestSpawnsB`
  (separate from `QuestSpawns` so two batches merge cleanly). Open items are `OPEN-QUB<n>`.
- **Stage values come from the cache where it has them**: the quest NPCs are multi-NPCs on the quest variable, so their arrays fix
  real thresholds (Keli gone above 40 and the jailed Prince above 90 in Prince Ali Rescue, Tock gone at 6 and Ithoi at 52 in The
  Corsair Curse, Abigale and Hewey above 15 in Misthalin Mystery). Other stages stay ours (OPEN-QU1). Quests whose cache variable
  has only 0 / 1 / 2 (Shield of Arrav) keep their detail in account `saved` values.
- **Gated doors**: plugin-api cannot ask the generic door script, so a door that a quest condition guards moves the player to the
  other side of the wall loc (`passDoor`); hook proposal in the quest files (OPEN-QUB1).
- **Dialogue** was converted from the Wiki transcript wikitext by a throw-away script (not committed); the generated Kotlin was then
  edited by hand where the transcript's "above / below / other" links make loops and where the conditions needed the game's state.

## D-062: Quest cutscenes are staged with a small `Scene` helper; the remaining quest gaps use plugin-api only

- **`Cutscene.kt`** (`content/quests`) wraps the plugin-api camera calls (`camMoveTo`, `camLookAt`, `camReset`, `camShake`, `minimapState`)
  and temporary scene NPCs (`PlayerRef.spawnNpc`, removed in a `finally`, so a logout mid-scene cleans up). It adds no engine API. The
  Wiki names scenes, not camera placement, so heights, rates and offsets are ours (OPEN-QUC5, docs/mechanics/quests.md QU13).
- **Misthalin Mystery's second half** (`MisthalinManor`) takes its stage numbers from the cache's multi-loc / multi-NPC thresholds on
  varbit 3468 and drives the piano (554) and gem (555) interfaces through `onButton` + `ifEvents`; the showdown's knife game is a
  2-tick timer with constants of ours (OPEN-QUC6). The island's multi-NPCs are spawned in `QuestSpawnsB`; the Wiki-import rows for their
  visible forms are excluded in `npc_spawns.py` (CONTENT_SPAWNED) and removed from `npc-spawns.tsv`.
- **Dragon Slayer I side routes** (`CrandorRoutes`) use `AttackCheck` / `LethalHitEvent` for Wormbrain; the combat module is untouched.
- **Quest-only drops** (Witch's Potion's rat's tail) are skipped by the drops importer (`QUEST_ONLY_ITEMS`), so the quest plugin is the
  only source.

## D-063: Slayer complete: the task in cache variables, the cache's db tables as data, the rewards interface from the client scripts

Supersedes the "Persisted task" and "Quests and unlocks are never assumed" bullets of D-042. Rules and OPEN items: docs/mechanics/slayer.md
SL1-SL15, OPEN-SL1-SL15. No engine, protocol, combat or plugin-api change.
- **The task, points, streaks, rewards and block lists are the cache's own varps and varbits** (`slayer_count`/`slayer_target`, `slayer_points`,
  `slayer_tasks_completed`, the three unlock varps, `slayer_blocked_<master>_n`), so the client, RuneLite and the rewards interface read them with
  no transmit code and they persist with the player's varps. Only the stored task is a `saved` value. Old `slayer.*` saved values are not read.
- **The cache is the data where it has it.** Db tables 113-117 give the reward rows (bit, cost, name, tab), the task ids the client shows, the
  longer-task amounts, the boss weights and Konar's places; they are read at world start (`SlayerCache`) and joined to the Wiki task tables by task
  name. The cache has no quest requirements for assignments (those are server scripts), so the Wiki's text becomes expressions in
  `slayer-requirements.tsv` (written by `tools/wiki-import/slayer_requirements.py`, every text must be mapped or the script stops).
- **Quest gating reads the quest's own state variable** (a copy of the `quest-states.tsv` rows in `slayer-quests.tsv`, as content/diaries does). A quest
  nobody implements never leaves its unstarted state, so its tasks stay closed without a list to maintain. The opposite of diaries' OPEN-AD2, on purpose:
  the brief asks for no unsourced assumption.
- **The rewards interface is driven by the client's own scripts.** The server opens interface 426 and answers the clicks that reach it; the
  protocol (which child of `confirm_button` is which action) was found by disassembling the rev 242 scripts, as D-038 did for skillmulti.
- **Superiors despawn through `NpcRef.despawn()` from the `DeathEvent` handler**: combat calls `respawnAfter` after the event and the engine ignores it
  for a removed npc, so no combat file knows about superiors.
- **The equipment checks are proposals, not code** (docs/mechanics/slayer.md): none is a "cannot attack" in the sources, all change hits or monsters' attacks.

## D-064: Guardians of the Rift as two modules' work, the tutorial as a second copy of the floor, unsourced numbers behind `GotrConfig`

Guardians of the Rift needs altar crafting (`content/skills` `Runecraft`), Temple of the Eye needs quest dialogue (`content/quests`), and
the two content modules see only plugin-api. Options: a new plugin-api event for the quest to learn about the tutorial's progress, or the
quest reading account values the minigame writes. Chosen: the account values (`gotr.t.*`) and the cache's own quest varbits; the quest polls
the one fact it needs (the third feeding of the Great Guardian) every 2 ticks, as Demon Slayer polls the stone circle. The cache holds the
tutorial as an exact copy of the temple shifted by (-1152,-3776), so the same loc handlers serve both (`Arena`); it is one shared copy, not an
instance. The Wiki gives no length for the lobby, the portal rotation, the monster waves, barrier health or the Great Guardian's drain: they
are named constants in `GotrConfig` and listed as OPEN-GR1..GR9, and monsters are a numeric simulation without NPC combat. Heavy long-line
dialogue follows the existing quests (strings wrapped with `+`).

## D-065: Hunter: pitfalls per player through the pit varbits, loot sacks from the Wiki tables, RuneLite's Herbiboar plugin read for data only

Pitfalls (`HunterPitfalls`): the 25 pits are multi-locs whose varbits are per player, so a pit is a player value, not a world loc (no
`addLoc`, no other player sees it); the click reports the parent id, so the plugin registers ops 1, 2 and 3 on the 25 parent ids and chooses
by the player's own state. Teasing, jumping and the chase were not given numbers by the Wiki and are named constants (OPEN-HU25); the
creature does not walk to the pit (that needs npc movement and combat, which stay with the main session), the jump carries the player.
Loot sacks (`HunterLootSacks`): every row of a tier is equally likely per roll (the Wiki lists 1/7, 1/12, 1/15 for each), the two cascade
rows follow the Wiki charts like fishing does. Herbiboar: the RuneLite plugin (BSD-2, `herbiboars/HerbiboarSearchSpot.java` etc.) was
read to extract which varbit joins which search objects (docs/mechanics/hunter.md OPEN-HU21); no code was adapted or copied, so no licence
header is needed, but the reading is logged here as the brief asks. The puzzle stays unimplemented: the start-to-first-spot mapping, the tunnel
of each ending and the varbit values the server writes (the cache's multi-locs show footprints at 3 / 4, RuneLite compares 1 / 2) are not in
any source. The number D-065 is a placeholder if another branch used it first.

## D-066: Sailing activities: the cache map and the type ops give the sites; unsourced numbers are named constants; trawling and Barracuda stay open

Sea charting, salvaging and sorting use only plugin-api. Sites come from the cache wherever it has them: the 92 charting locs and the 186 wreck stumps
stand at the Wiki's tiles in the map, so tasks are matched by tile (no name reflection) and wrecks swap between their stump and wreck locs. What the cache
strips (spyglass zones, duck routes, drinks, troll-to-task links, success rates, sort cadence, pre-roll rule) is a named constant or a plain rule marked
OPEN in docs/mechanics/sailing-charting.md and sailing-salvaging.md (OPEN-C3..C12, OPEN-SV1..SV8), not a config flag, because nothing else reads them.
Deep sea trawling (no shoal paths, no catch chance, nothing that moves on the sea) and the Barracuda Trials (course rules only as map drawings) are not
implemented: a plausible stand-in would invent the mechanic. Their needs and the S10 hook list are in sailing-trawling.md, sailing-barracuda.md and
sailing-combat.md. Wiki tables were imported with python scripts under tools/wiki-import in the repo's pattern (`sailing_charting.py`, `sailing_salvage.py`).

## D-067 Bank PIN storage and real-time clocks

The PIN is saved as the first 8 bytes of SHA-256 over "bankpin:<account id>:<pin>" (the display name stands in without a hub), never as
the digits. Four digits cannot resist a search by someone holding the save, so this only keeps the PIN out of plain sight; the real
protection is the server-side lockout (BP4). Lockouts, the recovery delay and the 5-minute logout grace run on wall-clock seconds
(`PlayerRef.epochSeconds`), because the Wiki states them in real time and they must survive logout. "Same place" for the grace is a
SHA-256 hash of the login address (`PlayerRef.loginAddressHash`); the address itself never reaches content or the save.

## D-068 Abuse reports go to the world log

The report window (G7) sends SEND_SNAPSHOT (name, rule, mute). The world validates it and logs one line per report, which ops can
grep; there is no report queue in the hub yet, because nothing reviews one. A staff member's "mute" is the only report that acts: it
calls the hub's audit-logged `ModerationService.apply(MUTE, 48 h)`. A hub report table is a later addition when a review tool exists.

## D-069 Code adapted from OpenRune-Server

OpenRune-Server (ISC, Copyright (c) 2025 RS Mod; an RS Mod fork) is a reference repo from now on, cloned outside the repo. The
boss health bar (`content/combat/BossHpBar.kt`) adapts its `BossHpBarScript` (varps, colours, client script sequence, timings) and
keeps the ISC notice in the file header. Where the cache disagrees (script 2102's argument order) the cache wins.

## D-070 Trading Post is a custom, hub-backed feature

The owner asked for a "Trading Post" like the screenshots of another server. It is not OSRS, so its rules live in
docs/mechanics/trading-post.md as our own (TP1..TP6), not as sourced mechanics. Listings are global (hub, Postgres), a single
lock in the hub serialises list / buy / cancel / collect so a listing is never oversold, and worlds move every coin and item
through item transactions on their side before (list, buy) or after (collect) the hub call, refunding on failure.


## D-071 Cache packing: `tools/cache-pack` writes a copy of the cache, Neptune compiles the ClientScripts

Custom client content (interfaces, clientscripts, inv / varp / varbit configs) is packed from `content/<module>/src/main/resources/pack/`
(`cs2/script`, `cs2/symbols`, `interfaces`, `configs`) into `.data/cache/242-packed/disk` by `./gradlew :tools:cache-pack:run --args="pack"`.
The source cache `.data/cache/242/disk` is only read. Options considered for the compiler: write our own (no), depend on Neptune from Maven
(not published; its build needs a jitpack repository for 4koma), vendor it. **Vendored** under `tools/cache-pack/neptune/` (MIT, same
pattern as routefinder / rsprot; `NOTICE.md` has the commit). Three modules (parser with its ANTLR grammar, compiler, clientscript-compiler),
no source edits; the CLI wrapper (clikt, gson, toml) is replaced by `ScriptPacker`, which drives `ClientScriptCompiler` and throws instead of
exiting the JVM on diagnostics. New catalog entries: `antlr` 4.13.1 and `guava` 33.6.0-android (Neptune's `MoreObjects`, `HashBasedTable`,
`HashMultimap`), both already in the local Gradle cache.

Writing: generic and small parts went to `cache/` (`Js5Container.encode`, `Js5DiskStoreWriter`: append sectors to `dat2`, point the `idx`
entry at them); the rest is in the tool. Groups are gzip containers with the 2-byte version trailer, which the client accepts like the
cache's own. A rewritten group gets CRC, compressed / uncompressed length (the archives' `lengths` flag) and version + 1 in its archive
index; the index is re-encoded losslessly (`IndexTable`, proven by re-encoding all 22 archive indexes byte for byte) and stored as
`255/<archive>`, which is where `Js5Responses` builds the 255/255 checksum table from. Archives 0 and 24 are never touched, so the CRC /
version spot checks still pass; only the group count differs (`pack.properties` records it). Name hashes: scripts are named, and the cache
hashes `"[trigger,name]"` (e.g. `[clientscript,stoneborder]`), so new scripts do the same.

Ids: everything new goes above the cache's maximum (scripts 10316+, interface 993 = highest group id + 1 rather than a gap, inv / varp /
varbit above the config group maximum and the gameval maximum), assigned in name order so a rerun gives the same ids. They are written to
`custom-symbols.tsv` (`type name id attributes`; components are `interface:component` with the packed id; the attributes column carries
server-only config keys such as inv `scope` / `stack` that the cache cannot hold). The server's symbol generation is deliberately not wired
to it yet: a build whose output depends on an optional packed cache would not be reproducible.

## D-072 Neptune against the rev 242 client: opcode table, script format, `if_runscript`

Checked by `cache-pack verify` on every run (Verifier.opcodeTable): all 10,303 scripts of cache 2757 walk cleanly (instruction stream ends
exactly at the trailer, instruction count equals the trailer's), every core opcode (< 100) the cache uses exists in Neptune's
`ClientScriptOpcode` with the operand width the cache needs (46 opcodes, no mismatch; 64 / 65 / 74 / 76 included), and the trailer layout
(counts incl. long locals / args, switch tables, length) is the one Neptune writes. Against RuneLite 1.13.6's `Opcodes.java` every shared
core id agrees (ICONST 0 ... GET_VARCLAN 76; ADD 4000, SUB 4001, MULTIPLY 4002, DIV 4003, MOD 4011, AND 4014, OR 4015). End to end: a
Neptune compile of `if ($colour = -1) { $colour = 0xffffff; } if_setcolour($colour, $text);` produces script bytes with the same
instruction sequence and the same 74 bytes as the cache's own script 3874 (`searchbar_mouseleave`). No fix to Neptune was needed.
Not verifiable from the cache, left as Neptune has them: `POP_LONG_DISCARD` (62; Neptune writes a 1-byte operand, RuneLite's loader
would read 4, and no cache script uses it), and the long arithmetic opcodes 4037-4040 / 4055-4057 (absent from RuneLite and the cache).
Neptune ships no command table, so `commands.sym` (opcodes, from RuneLite `Opcodes.java`) and `commands.cs2` (signatures) are curated for
the commands the pack scripts use. **`if_runscript*` = 2929** is in neither RuneLite nor any cache script; it was read from the 242 client
(injected-client jar, obfuscated): the handler for opcode 2929 pops three ints (crc, component, sub id) and a type string, builds the typed
argument array and calls the method that sends client packet 14, which is `IF_SCRIPT_TRIGGER` in `GameClientProtId`. One reading of
flattened bytecode, so treat it as the first thing to confirm in the client (OPEN-CP1: a search keystroke must reach the server as
`IfScriptTrigger` with the crc given in the `if_script` symbol). **Confirmed in the client (OPEN-CP1 closed):** the packet is only sent if
the target component's server-set events have **bit 23** (`(events >> 23) & 1`); without it the opcode silently does nothing. Content
sets it with `ifEvents(component, -1..-1, IfEvents.SCRIPT_TRIGGER)`; the Trading Post search box filters live with it.

## D-073 `.if3` format, click mask, fonts

The `.if3` files are TOML in the shape of OpenRune's (ISC; scripts and the `spawn_menu.if3` layout are adapted from OpenRune-Server, D-069
applies) with our field names documented on `InterfacePacker`. Decisions: component 0 is a root layer sized by the interface's width /
height and carries the `[[interface]]` hooks (`on_load`); children without `parent` hang off it (the cache's interfaces have exactly one
root in 746 of 992 cases, and the layout is the same when mounted). IF3 click mask: op N (1-based) is enabled by bit N, matching every
op-bearing component in the cache (e.g. 1 op = 0x2, 2 ops = 0x6, the 10-slot layers 0x400 + bits); `click_mask` overrides it. Fonts:
`regular` = p12_full (495), `bold` = b12_full (496), `small` = p11_full (494) (RuneLite `FontID`); text shadow defaults to on, as in the
cache's own text. `MiniToml` reads the TOML subset instead of a library (no catalog entry works without the serialization plugin).
The IF3 encoder (`If3Codec`) is the inverse of `ComponentTypeDecoder` and keeps every field; `cache-pack` proves it on all 25,152 IF3
components of the cache (decode, encode, same bytes). Not encodable: IF1 components (the legacy layout), and IF3 types other than layer,
rectangle, text, graphic, model, line (the cache has no others).

## D-074 Where the base symbols come from

Generated from the cache on every run (`CacheSymbols`): obj, npc, inv, varp, varbit, loc, seq, spotanim, dbrow, dbtable, jingle, graphic
(sprites, names like `checkbox,1`), varc, interface and component names from the gamevals in archive 24; string var-clients by scanning
every script for `get/set_varc_string` (the varc config files carry no type). Curated in `tools/cache-pack/src/main/resources/cs2/`
because the cache has no names for them: `commands.sym` / `commands.cs2` (see D-071), `legacy.sym` / `legacy.cs2` (the 12 existing
scripts the pack scripts call: ids from OpenRune's rev 240 `clientscript.rscm`, each confirmed against the cache script's argument counts
and body; signatures typed from the call sites), `fontmetrics.sym` (RS Mod `font.sym`, ids checked against archive 13), `constant.sym`
(`^iftype_*`, `^setpos_*`, `^setsize_*`, `^settextalign_*`; key codes from RuneLite `KeyCode`, `key_del` = 101 confirmed by script 3876).
Not available anywhere we can read: the names of the other ~7,900 cache scripts, and enum / struct / param / dbcolumn names. A pack
script that needs one adds a line to the pack's own `cs2/symbols/*.sym` (types `varp`, `varc`, `param`, `if_script`, `dbcolumn`, ...) or
extends `legacy.*` / `commands.*` here. `if_script` symbols are the server-side script crc as a signed int (the spawn menu needs
`-880770536 ifscript_3414196760 int,int,string`).

## D-075 `::spawn` is OpenRune's spawn menu, server side adapted to our plugin-api

`content/dev/SpawnMenu.kt` adapts OpenRune `SpawnMenuScript` (ISC, attribution in the file header and the pack's NOTICE.md); the
interface and client scripts are OpenRune's pack unchanged. Same rules: administrators only (staff level 2; `--admins` on the
embedded hub), search on exact / prefix / substring of name or gameval with shortest label first, stack-size variants and
note / placeholder copies left out (OpenRune also drops its server-only "dummy" items; the 242 cache has no such flag, so we
additionally drop bought-template copies), quantity 1 / 100 / 1000 / X, note, bank and include-nulls toggles, auto-note when a
non-stackable stack does not fit the backpack, and only what fits is added. Two deliberate differences: a card click is
resolved by card index against the server's own result list rather than the obj the client names (§12), and only the click on
the card's icon sub counts, since the card's onop script re-triggers op 1 on the icon and each click otherwise arrives twice
(OpenRune ignores the obj-less one the same way). The gameval labels need obj gameval names at runtime, so `WorldData` now loads
cache archive 24 group 0; `WorldNode` also now passes the dialogue font, which it had left empty (dialogue paging fell back to
one page).

## D-076 Gear effects follow the Wiki DPS calculator's order; ranged strength is two params

Weapon passives, set effects, bolts and immunities (docs/mechanics/gear-effects.md) are applied in the order and with
the floors of the Wiki DPS calculator (`PlayerVsNPCCalc`), which encodes the Wiki's per-item rules; where the Wiki text
disagrees (tome of water accuracy) we follow the Wiki and mark it OPEN. They live in pure functions (`GearEffects`)
fed by a per-player cached snapshot of worn item names, so `DpsParityTest` checks them against 33 calculator rows
(`gear-matrix.json`, generated by tools/dps-matrix `GearMatrix.test.ts`; only numbers are used). Two findings: the
cache stores ranged strength in param 12 for ammo / some bows and in param 189 for others (twisted bow, necklace of
anguish, Masori (f)), and both are summed now (they were 189-blind before); and the calculator's name lookup lands on
"(broken)" void pieces, so the matrix names those by id. Blowpipe scales and darts are kept per player, not per item:
our objs carry no state, and the blowpipe only trades empty, so one player's pipes share one store (a PvP death drops
the pipe empty with its charges, as the Wiki says).

## D-077 Rune pouch adapted from OpenRune; storage items reach the bank through one transfer event

The rune pouch's varbit layout (rune = enum 55 compact id in `rune_pouch_type_n`, amount in `rune_pouch_quantity_n`), its
interface ops and messages are adapted from OpenRune-Server `content/skills/magic/rune-pouch` (ISC, Copyright (c) 2025
RS Mod); the adapted files (`plugin-api` `RunePouch.kt`, `content/gameframe` `RunePouchItems.kt`) keep that licence
header. Keeping the contents in the client's own varbits means the pouch interface, the spellbook and RuneLite show them
with no extra packets. Storage that is not an inventory (pouch varbits, essence pouch varbits, the coal bag's saved
count) moves to and from the bank only through `BankTransfer`, which the bank plugin runs through its normal deposit plan
or slot withdrawal and then reports the amount moved; the storage changes by that amount after the bank's transaction
committed (same tick, single thread). The looting bag is an inventory (516), so its deposits are taken in the bank's own
transaction. Load-out varbits and the name list's index rule come from the rev-242 client scripts (4895, 8092), checked
against packets logged in game.

## D-078 Settings window adapted from OpenRune; settings are player variables

The All-settings window's server side follows OpenRune-Server `content/interfaces/settings` (ISC, Copyright (c) 2025 RS
Mod): its `settings_configs` table (setting id -> varbit / varp, default, range, prompt, enable toggle) and
`account_default_vars` were converted to `content/gameframe/src/main/resources/settings/*.tsv` with variable ids
resolved from our gamevals; `AllSettings.kt` keeps the licence header. Every setting is a player variable, so persistence
is the existing varp save; nothing setting-specific is stored. New-account defaults are applied once and only to
variables still at 0, so accounts that existed before this change keep their values.

## D-079 Raids: TFA first; post-3 November state; wiki numbers where the cache is stripped

The raids brief (docs/plans/raids-brief.md) orders ToB, CoX, ToA, TFA; the user asked to start with The Fractured
Archive, so the order is R0-R2 framework, then TFA, then ToB, CoX, ToA. TFA covers the same framework pieces (fixed then
chosen room graph, per-player chests, dangerous deaths with wipe eject and reclaim, spectating, a supply shop).
Cache 2757 includes the 3 November 2026 TFA update: the Archive Guardian (16659/16660) and Ocellus P3 (16677) carry
extra clickbox models 51447/51448, changed in the 2026-11-02 build, which the debug copy 16682 lacks. TFA is built to
the post-3 November rules. The brief ranks cache dbtables above the wiki, but TFA's drop tables, doors, room configs,
boss patterns and shop are server-only tables whose columns are stripped from the client cache (only row names and the
stats tables 264/265 survive), so the wiki is the authority for those numbers and everything else is OPEN with a named
config flag (BRIEF §0.4).

## D-080 Raid boss values: which source wins (2026-11-08)

The wiki leaves most raid boss timings and numbers unstated. Two further sources now cover TFA and ToB:
RuneLite plugins (docs/mechanics/raids/{tfa,tob}/runelite-timings.md) and RSProx packet-capture analyses of the live game
(`MRobertEvers/oldschool-clientc`, docs/mechanics/raids/{tfa,tob}/capture-values.md). Order, per value:
- **Timings** (cycles, delays, telegraphs): the cache's own names first (e.g. `NPC_BORK_14_CYCLE_STUN01`), then the RuneLite
  plugin value (the user asked for plugin timings; LU was updated 2026-11-07, after the last patch), then the capture,
  then the wiki. Where the plugin itself marks a value medium/low and the captures and another source agree against it,
  the captures win (Ocellus P4 deflect order: Deflect Melee first, as the capture and the trainer show).
- **Damage, HP, counts, heals, rotations** (what client plugins cannot see): the capture first, then the wiki, then a named
  OPEN model. TFA captures are rev 241 from 20-22 Oct (before the 28 Oct / 3 Nov patches): a value a later patch notes
  changed keeps the patch note. ToB capture damage is from seasonal worlds (about 10% less damage taken), so only ToB
  timings and counts are taken from it.
- No code is copied from either source (LU has no licence file; oldschool-clientc has none either): facts only.

## D-081 Theatre of Blood structure on this server (2026-11-08)

- **Hallways:** the cache has only the nine room squares (client-contract 5); the links between rooms are not in it. A
  room is left through an exit box at the end of its own outbound corridor (`TobFlow.EXITS`) and the whole party moves to
  the next room's antechamber, outside its barrier, where each player decides whether to cross (TB8). Verzik's room is
  left through the throne door (32738), placed on the arena level at its north edge. OPEN-TB7.
- **Levels:** the Nylocas room and Xarpus play on level 1 (their barriers and walkable floors are level 1 in the cache);
  the other rooms on level 0.
- **Raid NPCs never respawn:** any NPC tagged with a run (`SharedAttrs.RAID_RUN`) is removed after its death, like a
  minion; boss minions can inherit the tag (`Bosses.summon(inheritRun = true)`) so their damage counts for the MVP.
- **Wipes:** Normal / Hard wipes move unprotected items into the packed inventory `tob_reclaim` (death pipeline
  `storeInv`), claimed at the Ver Sinhaza chest for 100,000 coins; Entry wipes restart the room (OPEN-TB5,
  `tp.tob.entry.wipeRestartsRoom`).
- **Run time** ends at Verzik's final blow (LR22) even though rewards are computed when the party enters the treasure
  room (`Raids.complete(endedAt)`).

## D-082 Loc approach flags use the low four bits (2026-11-08)

The loc config's force-approach byte (opcode 69) is rotated by the loc's angle before reach checks (N 1, E 2, S 4,
W 8). Some locs carry a higher bit as well (the Chambers of Xeric entrance steps 29777 hold 30 = 0b11110); the vendored
routefinder's rotation shifts that bit into the side bits and blocks every side, so the steps could never be used. The
engine passes `forceApproach and 0xF` to both route finding and reach (`InteractionTarget.Loc.approachFlags`), which
leaves the steps open from the south, where Captain Rimor stands. The routefinder stays unmodified. What the high bit
means is not known.

## D-083 Chambers of Xeric structure on this server (2026-11-08)

- **Instance:** each run builds its own instance (`RaidDefinition.layout`): both generated floors as zone copies of
  their template cells (4 x 4 zones each, client-contract 6.2-6.4) on planes 3 and 2 of a 128 x 64 area, so RuneLite's
  CoX plugins read the real template references, and the Olm square (50, 89) copied whole two squares east.
- **Layout:** one of RuneLite's 26 patterns (small or medium with equal weight, OPEN-CX7), placed on the 4 x 2 grids
  from every possible start until it fits; floor 2 starts in floor 1's end cell. Combat rooms follow the drawn rotation
  cycle and offset by grid slot (CX11); a placement that would repeat a combat room is skipped. A cell's variant and
  rotation come from its entry and exit directions (rotation = entry move, winding = exit - rotation).
- **Traversal (OPEN-CX31):** the corridors between cells are not in the cache (cell edges are blocked). Every passage
  (29789) teleports to the arrival tile of the neighbouring cell (the first walkable tile in from the middle of the
  shared edge); a passage is spawned on each side a template has none. Forward passages of combat and puzzle rooms
  wait for the room to be done; the side a room was entered by leads back.
- **Start:** the leader's entry at the steps creates the party if there is none and builds the instance (the run's
  membership and scaling are fixed then, earlier than CX4's "at the start"); members follow while the run is in the
  lobby; the leader's lobby passage starts the raid (`The raid has begun!`), after which nobody joins (OPEN-CX3).
- **Rooms in raids-core:** `lobby`, `chambers` (both floors, one encounter; content tracks the cells), `olm`, `rewards`.
- **Combat rooms:** the fights are boss scripts in content/combat (`Cox*.kt`); raids-cox spawns each room when a member is
  first seen in its cell, tags its NPCs with the room frame (`SharedAttrs.RAID_ROOM`, template tiles per rotation) and the
  run's mode, spawns the room-lock blocker the RuneLite timer plugins watch and removes it once the room's required NPCs
  are dead. Spawn tiles are OPEN-CX32 (Tekton at his anvil, Vespula's portal at its template tile, the Guardians beside
  the exit doorway, the rest spread over the cell's floor). NPC stats follow the wiki calculator's CoX scaling (cox.md
  CX17a), applied through `SharedAttrs.RAID_SCALER` to summoned minions as well (`Bosses.summon(scale = true)`).
- **Puzzle rooms:** thieving, tightrope, ice demon and crabs run their room rules in raids-cox (`CoxThieving`,
  `CoxTightrope`, `CoxIceDemonRoom`, `CoxCrabs`) and their foes in content/combat (`CoxPuzzleFoes`). Each room's blocker
  stands on the floor in front of its exit doorway and its tile is kept (it blocks the tile it is on). The crab orb flies
  one tile a tick from the carving along the side whose run heads closest to the crystals (OPEN-PZ4); shared NPC state
  between the two modules uses `SharedAttrs.RAID_METER / RAID_FOCUS / RAID_UNTIL` (attr keys compare by identity).
- **Resource rooms** (`CoxResources` plugin: scavengers, shortcuts, farming, food, Herblore, kindling) follow
  resources.md; the raid-made items in `CoxItems.RAID_ONLY` never leave the Chambers. Food and potion points use per-run
  maker ledgers (the oldest maker of that item is credited first), since items carry no maker.
- **Great Olm:** one fight script on the head (`CoxOlm`) drives the claws, the 12-step rotation, the three powers, the
  specials, the clench and healing claw and the phase changes; the room (`CoxOlmRoom`) spawns the parts when the first
  raider passes the barrier and completes the raid on the head's death (the time ends there; the exit crystal shatters).
  The visible parts are the cave locs swapped on the side Olm rises on. Damage on a recovered claw earns no points
  (`SharedAttrs.RAID_NO_POINTS`); the head takes no damage (so earns nothing) before the head phase.
- **Challenge Mode** uses a fixed three-floor snake layout in the CM page's room order (OPEN-CX9); its middle floor ends in
  FL_END1 and the bottom floor starts in FL_START2.
- **Party = friends chat (CX2, CX6):** a forming party whose leader owns the friends chat channel they are in follows
  that channel every tick (`CoxChannel`): members with rank recruit or higher are in, anyone who leaves, is kicked or
  ranks below recruit is out. The board's Join joins the leader's channel; leaving the party leaves it. Varbit 5428 is
  1 for a viewer in the leader's channel, so the client offers Kick by rank and sends the friends-chat kick itself. The
  world sees the channel through `Player.channel`, set from the hub's channel events; without a hub (no channel data)
  the board joins directly. When the leader leaves the raid, the highest-ranked channel member still inside (recruit
  at least) leads, else everyone is thrown out; after the start only with `tp.cox.leaderHandoverAfterStart` (OPEN-CX5).
  A logout is not leaving (CX36).
- **Large pool (OPEN-CX7):** generated, since RL's 26 patterns are the pre-2020 pool: two full floors of six rooms
  (start, six rooms, end over all eight cells), 5 C / 3 P in total plus one S and one F per floor, with the order
  rules all 26 patterns share (floor 1 opens with S or F; floor 2 opens with C or P and ends with F; no P after P).
  `tp.cox.largePoolGenerated=false` draws from the regular pool instead.


## D-084 Tombs of Amascut structure on this server (2026-11-08)

- **Instance:** one per run, the 12 raid squares (Nexus 14160, tomb 14672, Wardens 15184 / 15696, the four puzzles and
  four bosses) as unrotated whole-square copies in a 7 x 5 square box, because RuneLite's ToA plugins read template region
  ids and fixed scene coordinates (toa/runelite-timings.md section 10). The pyramid and lobby are the real map.
- **Rooms:** raids-core rooms `nexus`, the eight path rooms, `wardens`, `tomb`; `ToaFlow` drives them with `enterRoom`
  (any path order). The puzzle's flame barrier on the continue-door side opens only once the puzzle is done; a boss
  room's exit returns to the Nexus once the boss is dead; the static Nexus door 46167 becomes 46168 "Entry" when all four
  paths are done; the run completes when the burial chamber starts, timed at the Wardens' final blow (as ToB).
- **Wipes:** new `WipePolicy.ByRun` decides per wipe: retry the room, or fail with the reclaim store once the attempts
  invocation's count is used up (TA42-TA44).
- **Scaling:** HP by the wiki DPS calculator's order and NPC sets; raid-level defence, accuracy and damage as per-mille
  attrs (`SharedAttrs.RAID_DEFENCE/ACCURACY/DAMAGE_PERMILLE`) applied by the combat formulas, since ToA scales the rolls,
  not the levels (TA31).
- **Invocations:** read from the cache (enum 4664 structs) and kept as script 6729's three words in the party settings.
- **Pinned config ids:** `cache-pack` inventories accept `num = <id>`; ToB's reclaim inventory stays 1036 and ToA's is
  1037, because player saves key inventories by id and name-ordered allocation would shift them when packs are added.

## D-085 Tombs of Amascut hooks into combat and items (2026-11-08)

- **Fights live in content/combat** (`Toa*.kt`, the shared `ToaKit`). raids-toa only places NPCs and reacts to deaths
  and to `RaidSignal(npc, player, key, amount)` events (`toa.*` keys). This keeps combat, boss and RNG code in one
  module (brief §0.8) and needs no dependency between the two.
- **Invocations that change items or specials** use cancellable or adjustable plugin-api events published by the
  generic code:
  - `ConsumeCheck`: On a Diet, Dehydration.
  - `SpecialCostQuery`: Overly Draining.
  - `AttackDelayRequest`: Nectar.
  - `CurePoisonRequest(curesVenom)`.
  - `SpecialUsed`: summary tallies.
  These are used instead of raid checks inside Consumables or Specials. With no raid running, nothing listens and the
  generic path is unchanged.
- **Records by final raid level:** `RecordsDef.modeOf` lets a raid choose the KC / PB bucket. ToA uses the final level
  (`toa.modeUsesFinalLevel`).
- **One time formatter:** `Raids.formatTime` (m:ss, h:mm:ss, precise centiseconds) is shared by CoX and ToA.
- **Deposit box:** Deposit inventory and Deposit worn go through the bank's own deposit-all (`DepositAllRequest`). One
  item-transaction path, one summary line. A single-item box deposit is accepted only from the backpack or worn slots.
- **Spawn tagging before spawn handlers:** `spawnNpc(type, at, wanderRange, init)` runs `init` before content's spawn
  hooks. Raids.spawn / spawnAt and Bosses.summon tag the run, scaling and room frame there, so a boss's `spawned` sees
  them (before this, the Wardens' phase 1 Wardens spawned on the real map).
- **Room frames are per level:** a ToA NPC's frame is on its spawn level, because `RaidRoomFrame.contains` checks the
  level (Akkha and the Wardens' P1 / P2 are on level 1).
- **Private views:** `privateSpotAnimAt`, `privateLocAnim` and `privateLocChange` send a zone op to one player (the
  transient zone event's viewer, the rule ground items already use). They are look only and change no world state. Used
  for the Apmeken Sight's hints and the sarcophagus reveal. A new `ZoneOp.LocAnim` maps to RSProt's LOC_ANIM.

## D-086 Raid scale: instance attach, spatial index, cached refs (2026-11-08)

- **Instances build off-thread, attach is a swap.** The build computes each target zone's final tile array and
  packed base-loc table (`ZoneBases`). Attach installs the arrays and hands the instance its zone and base tables.
  Base-loc lookups read the owning instance's table. Re-attaching over live zones still goes zone by zone. Empty zones
  share one tile array, which `ownZones` copies on the first write.
- **A turned base loc starting outside its instance** keeps its collision but is not registered. Its origin zone
  belongs to the map or a neighbouring instance, and registering it there leaked and could overwrite that zone.
- **Square index.** Players and NPCs are indexed by map square through `Entity.coord`'s setter. Radius queries keep
  `forEachPlayer`'s PID order and `forEachNpc`'s index order, so random picks, golden scenarios and replays are
  unchanged. Boxes wider than 8 x 8 squares fall back to the full scan. `onlinePlayer` is a name map lookup.
- **Cached refs.** plugin-api keeps one `PlayerRef` / `NpcRef` per entity (`Entity.contentRef`). Refs already
  compared by entity, so nothing observes the change.
- **Scratch queries.** `playersWithinScratch` returns the world's reused list, valid until the next scratch call,
  for per-tick loops (NPC aggression).
- **Dev command `::raid <id> | next | leave`** (raids-core) drives the botswarm `raids` load profile. Like the other
  dev commands it is ungated on this server; gating commands is the moderation system's job.

## D-087 Command system: TOML config and five ranks (2026-11-08)

- **Config is TOML** (the user chose option 1, as the commands brief §9 says), in `config/server.toml` or the file
  given by `--config`.
  - ktoml decodes it into `@Serializable` classes in `server`; the kotlinx-serialization compiler plugin is applied
    there only.
  - Unknown keys and bad values fail the boot with the line named.
  - Every key is optional, and a missing file means a dev world.
  - `[worlds.N]` overrides the shared `[commands]` for world N.
  - Flat `server.properties` was rejected: half the settings are lists or maps.
- **Ranks.** `tp.util.Rank` (PLAYER < MODERATOR < ADMINISTRATOR < OWNER < DEVELOPER) replaces `services.Rights`.
  - It is stored by name; ADMINISTRATOR keeps the existing `ADMIN` value, so no account row changes.
  - The chat crown, login `staffModLevel` and `playerMod` are explicit properties, not `ordinal`.
  - The §1 targeting and rank-setting rules live on the enum.
  - "Administrator" checks are now "administrator and above", so owners and developers pass them.
- **Login and crowns.** The login response now sends `playerMod` (moderator and above; it was hardcoded false) and
  `staffModLevel` 2 for administrator and above. Public chat sends the crown (it was 0).
  - OPEN-CM1: which client scripts read `staffmodlevel` and `playermod`, and what each value unlocks, is unverified
    (the cs2 dumps could not be read). It is checked in RuneLite.
  - OPEN-CM2: the crown values (1 player-moderator, 2 J-Mod) follow the existing PM, friends-chat and clan code;
    public chat is to be checked in RuneLite.
- **Bootstrap.**
  - `[ranks.bootstrap]`: developers, owners, administrators and moderators lists, applied at the account's next
    successful login. It is once per account, tracked by an audit entry `bootstrap-rank` on `account:<id>`, so a later
    demotion sticks. It used to run before the password check.
  - `--admins` still works and means ADMINISTRATOR.
  - `./gradlew admin --setRank=<account>:<RANK> --db=<jdbc url>` (`tp.hub.AdminKt`) sets a rank in the database
    and audits it as actor `cli`.

## D-088 Command system: moderation storage, bot ranks, suite order (2026-11-08, the user's answers)

- **Moderation records:** a new `moderation_records` table, additive.
  - Each record holds type, actor, reason, start, expiry, scope, and who lifted it and when.
  - Device bans are keyed on the login block's 24-byte `uuid`.
  - The existing `accounts.muted_until` / `banned_until` and `ip_bans` stay as the fast login-time checks, written
    alongside the records. Nothing existing is rewritten or dropped.
- **Bots on dev worlds:** `[ranks.bootstrap]` accepts a trailing-`*` name prefix (`developers = ["bot*"]`). It is
  applied once per account and audited like any bootstrap entry, and only where the config lists it.
- **Suite order:** rank by rank, as commands brief §11 lists: player, moderator, administrator, owner, developer.
  The raid tables come last.


## D-089 Legacy `::` commands are rank-gated until they move onto the registry (2026-11-08)

- Before this change, every `onCommand` handler ran for any player, including `::item`, `::max` and `::tele`.
  `onCommand(name, rank)` now takes a minimum rank. Below that rank, the player gets the unknown-command reply
  (commands brief §2), so the command's existence never leaks.
  - The dev cheats, `::raid`, `::pandemonium`, `::openbank`, the `::ge*` shortcuts and the music unlocks are DEVELOPER.
  - `::pos` is MODERATOR, as brief §6 lists it.
  - The dev `::empty` is gone: the player `::empty` (CONVENIENCE, with confirmation) replaces it.
- **Scenario players default to DEVELOPER** (a dev world), so the golden scenarios' setup cheats run unchanged. The
  permission scenario passes explicit ranks.
- **Dev bots:** `config/server.toml` lists `developers = ["bot*"]` (D-088), so botswarm load runs keep their cheats.
  Remove it on a live world.
- **Official fallbacks** (`::bank`, emote names) run when no usable command matches, even when a same-named staff
  command exists. A player who types `::bank` sees the official joke, never a hint that a staff `::bank` exists.
- `Impact`, `CommandOverride` and `CommandsConfig` moved to `util` (`tp.util.commands`), next to `Rank`. Content
  modules depend only on plugin-api and symbols, and they declare an impact on every command.
