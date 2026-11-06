# Revision upgrade

The revision is named in two places only: `osrs.revision` / `osrs.openrs2CacheId` in `gradle.properties`, and
`Revision.NUMBER` in `util`. Everything revision-specific in code lives in `protocol/osrs-<rev>` (adapter + vendored
RSProt) and in generated `symbols`.

## 1. Mechanical step

```bash
./gradlew upgradeRevision -Prev=243
```

This finds the newest OpenRS2 `oldschool/live/en` cache for build 243 (`archive.openrs2.org/caches.json`) and rewrites
the two places above. To pin a specific cache, add `-PcacheId=<id>`. It then prints the remaining steps.

## 2. Cache

```bash
./gradlew setup
```

This downloads the new cache, keys and Jagex `jav_config.ws`. On the first run it writes
`gradle/cache-checksums/<rev>.sha256`. Commit that file and nothing from `.data/`.

## 3. Protocol

1. Vendor RSProt `<rev>` into `protocol/osrs-<rev>/rsprot/`. Keep its LICENSE and add a DECISIONS entry naming the
   commit.
2. Copy `protocol/osrs-<old>/src` to `protocol/osrs-<rev>/src` and fix compile errors against the new RSProt: renamed
   messages, V2 to V3 packets, new login block fields.
3. Point `settings.gradle.kts` and `server/build.gradle.kts` at the new module. Keep the old one until the new one
   passes the checks in step 5.
4. Ask `scout` to list the RSProx protocol diffs between the two revisions (opcodes, sizes, field order). Each diff
   needs a matching adapter change or a check in botswarm.

## 4. Content data

`./gradlew build` regenerates `symbols` from the new cache. A renamed or removed component, inv or varp breaks the
compile at its use site. Fix each one against the cache with `./gradlew :tools:cache-dump:run --args=...`. Never guess.

## 5. Checks before merging

| Check | Command |
|---|---|
| Test 1: cache smoke | `./gradlew :tools:symbol-gen:test` |
| Test 2: protocol round trip | `./gradlew :tools:botswarm:test` |
| Golden scenarios + DPS matrix | `./gradlew build` |
| Bots log in, walk, fight, hop | `./gradlew :tools:botswarm:run --args="50"` (also `fight`, `hop` modes) |
| Real client | Update `runelite_bootstrap_commithash` in docs/CLIENT.md, then log in through RSProx |

## 6. Rollout

Before the upgrade:

1. Announce it with `::update <seconds>`. Every world saves all players and stops at zero.
2. Wait for all logout saves to be durable; the hub shows `online=0` at `/health`.

Saves are revision-independent (`PlayerSave.VERSION` covers the format), so no data migration is needed unless obj or
varp ids changed. In that case, add a `PlayerSave` migration that remaps them.

After the upgrade, start the hub, then the world nodes.
