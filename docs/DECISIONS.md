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
