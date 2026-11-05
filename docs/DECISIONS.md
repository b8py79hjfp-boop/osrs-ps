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
