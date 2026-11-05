# tickperfect — OSRS rev-242 server (Kotlin)

At the start of every session, read `docs/BRIEF.md` and `docs/PROGRESS.md`.

`docs/BRIEF.md` is the source of truth. Where it is silent: most faithful to OSRS first, fastest second.
Non-obvious choices go in `docs/DECISIONS.md`; phase plans in `docs/plans/phase-N.md`.

## Commands

| What | Command |
|---|---|
| Build + lint + tests (needs `setup` once: symbols are generated from the cache) | `./gradlew build` |
| Format | `./gradlew spotlessApply` |
| Download + verify cache 2757 into `.data/cache/242/` | `./gradlew setup` |
| Login RSA keypair into `.data/rsa/` (prints modulus) | `./gradlew generateRsa` (`-Pforce` to regenerate) |
| Boot the world node (game+JS5 :43594, HTTP :8080) | `./gradlew :server:run` |
| Bots | `./gradlew :tools:botswarm:run --args="20"` |
| Test 2 (protocol round trip) | `./gradlew :tools:botswarm:test` |
| Real client | docs/CLIENT.md (RuneLite via RSProx) |
| Boot check (CI) | `./gradlew :server:run --args=--boot-check` |
| Test 1 (cache smoke) | `./gradlew :tools:symbol-gen:test` |
| Inspect cache data | `./gradlew :tools:cache-dump:run --args="summary"` (also `config obj 4151`, `map 50 50`, `collision 3222 3218 0`) |

JDK toolchain comes from `osrs.javaVersion` in `gradle.properties` (25). A machine without JDK 25 can override it in
`~/.gradle/gradle.properties` — never change the repo value (DECISIONS D-004).

## Module map

```
build-logic/        convention plugins: tp.kotlin-base / -library / -api (explicit API) / -app, tp.root (setup, generateRsa)
util/               primitives, buffers, CoordGrid, typed ids, Revision (the only 242 in code)
cache/              JS5 disk store, containers, XTEA, indexes, config decoders, maps -> collision
routefinder/        vendored RS Mod routefinder (ISC, unmodified; see its NOTICE.md) — collision flags, BFS, LoS
symbols/            generated at build time from cache archive 24 (Java constant chains, D-014; never committed)
protocol-api/       our message types + codec interfaces
protocol/osrs-242/  adapter (our code) + rsprot/{api,common,desktop,internal,model,shared} (vendored RSProt 242, MIT)
net/                Netty bootstrap, sessions, JS5, login, HTTP (jav_config / worldlist)
engine/             world, tick pipeline, entities, zones, collision, routing, queues/timers/delays, info pass
plugin-api/         stable content API + Kotlin DSL (explicit API mode)
content/<area>/     plugins (combat, pvp, ...). Depend only on plugin-api + symbols
services/           account, social, exchange, hiscores (interfaces + in-process impls)
server/             world-node main, config, wiring (composition root)
hub/                hub main (Phase 5)
tools/              symbol-gen (+ test 1), cache-dump, scenario (golden tests), botswarm, replay, wiki-import
client/             release client (Phase 6): runelite fork, gamepatch, launcher, dist
```

Root package is `tp.<module>`. `.data/` (cache, keys, RSA, client artifacts) is gitignored — never commit Jagex/RuneLite binaries.

## Conventions (brief §11)

- `internal` by default; `public` only for cross-module API. Explicit API mode in `plugin-api`.
- No `!!`. Value classes for ids and coordinates. Sealed types for messages.
- No global mutable singletons — world-scoped state only. Content uses only the world RNG.
- Tick hot loops: no blocking, no logging (sampled only), no allocation.
- Every non-obvious constant has a one-line KDoc naming its source. No unexplained magic numbers.
- Never invent mechanics: each rule gets `docs/mechanics/<topic>.md` (rule, tick timeline, source URL).
  Unresolved → mark `OPEN`, implement the most plausible behaviour behind a named config flag.
- Treat every client packet as hostile (§12). Every item movement goes through the single transaction API.
- ktlint (via Spotless) + detekt (`config/detekt/detekt.yml`) run in `check`.
- Tests: exactly the set in brief §9. Add nothing else unless the same bug comes back twice.
- Conventional commits; commit at every green milestone. Stop at each phase end and summarize.

## Model policy (brief §0.8)

- **Main session: Claude Opus 5.5, medium effort** (pinned in `.claude/settings.json`). Owns architecture and every
  change to `engine/`, `protocol-api/`, `protocol/`, `net/`, `content/combat`, `content/pvp`, concurrency,
  persistence and security-critical code. Resolves `OPEN` mechanics. Reviews all subagent output before commit.
  Do not set `CLAUDE_CODE_EFFORT_LEVEL` (it overrides subagent effort).
- **`scout`** (Haiku 5.5, low, read-only): locate code, extract wiki values, summarize logs, list RSProx protocol diffs.
- **`implementer`** (Sonnet 5.5, medium): pattern-following work — further cache decoders, content plugins on a
  stable plugin-api, wiki-import, botswarm behaviours, client packaging/CI, doc drafts.
- **`verifier`** (Sonnet 5.5, high, read-only): checks mechanics docs / golden expectations against sources without
  seeing the implementation.
- Subagents never commit. Tick order, determinism, protocol, combat/PvP, security and persistence stay in the main
  session; subagents may only research them. Agent definitions: `.claude/agents/`.

## Reference repos

RS Mod (ISC), RSProt (MIT), RSProx (MIT), RuneLite (BSD-2). If code is adapted, keep its license header and log it
in DECISIONS.md. Clone them outside the repo (e.g. a scratch dir), never into it.
