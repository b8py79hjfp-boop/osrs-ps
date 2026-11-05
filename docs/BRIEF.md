# Build brief: tick-perfect OSRS server, revision 242, Kotlin

You are building a from-scratch Old School RuneScape game server that the RuneLite client cannot tell apart from the real game at the tick level, with combat and PvP as the first-class goal, scaling to 4,096+ concurrent players. Read this entire brief before writing any code. It is the source of truth for the project: where it conflicts with your defaults, it wins; where it is silent, choose what is most faithful to OSRS first, fastest second.

---

## 0. Working agreement

1. **Persist this brief.** First action: save this file verbatim as `docs/BRIEF.md`. Then write a short `CLAUDE.md` (under ~150 lines): build/run/test commands, the module map, the conventions in §11, the model policy in item 8, and the line "At the start of every session, read `docs/BRIEF.md` and `docs/PROGRESS.md`."
2. **Track state in the repo.** Maintain `docs/PROGRESS.md` (phase checklist, done, next, open questions) and `docs/DECISIONS.md` (one entry per non-obvious choice: options considered, choice, why). Update both at every milestone.
3. **Plan, then build, per phase.** Before each phase in §10, write `docs/plans/phase-N.md` (scope, modules touched, risks, definition of done). Commit at every green milestone using conventional commits. Stop at the end of each phase and summarize what changed and what is blocking.
4. **Never invent mechanics.** Every gameplay rule gets an entry in `docs/mechanics/<topic>.md` with the rule, a tick-by-tick timeline, and the source URL (§2). If sources conflict or are silent, write it down as `OPEN`, implement the most plausible behaviour behind a named config flag, and keep going. No unexplained magic numbers in code.
5. **Use subagents for independent, well-scoped work**, following the model policy in item 8. Keep architecture decisions in the main thread.
6. **Jagex and RuneLite binaries never enter git.** The cache, keys, client and any gamepack belong to Jagex. A setup task downloads them into `.data/` (gitignored) and verifies them; everything reads from there. The same applies to the game-client artifact the release client patches and to the built client (§4.2): CI pulls them from a private artifact store. Our RuneLite fork's source (BSD-2) can live in git.
7. **Minimal tests:** exactly the set in §9. Add nothing else unless the same bug comes back twice.
8. **Models and effort.** Requires Claude Code v2.1.293 or later (the minimum for Haiku 5.5).
   - **Core — Claude Opus 5.5 at medium effort.** This is the main session. It owns architecture and every change to `engine/`, `protocol-api/`, `protocol/`, `net/`, `content/combat` and `content/pvp`, plus concurrency, persistence and security-critical code (packet validation, item transactions). It resolves `OPEN` mechanics and reviews every subagent's output before it is committed. Pin it in `.claude/settings.json` with `"model": "claude-opus-5-5"` and `"effortLevel": "medium"`. If you are not running as Opus 5.5, say so before doing anything else. Don't set `CLAUDE_CODE_EFFORT_LEVEL`: it would override the subagents' own effort levels.
   - **`scout` — Claude Haiku 5.5, low effort, read-only** (Read, Grep, Glob, WebFetch, WebSearch). Finds where things live in this repo and in the reference repos (RS Mod, RSProt, RSProx, RuneLite), pulls values from wiki pages into structured notes, summarizes long build, test and benchmark logs, and lists raw protocol differences between RSProx revision modules for the main session to act on.
   - **`implementer` — Claude Sonnet 5.5, medium effort.** Well-specified work that follows a pattern the main session has already set: the remaining cache config decoders after the first, content plugins once `plugin-api` is stable (skills, NPCs, areas, quests), `tools/wiki-import`, botswarm behaviours, release-client packaging and CI, and documentation drafts.
   - **`verifier` — Claude Sonnet 5.5, high effort, read-only.** Checks mechanics docs and golden-scenario expectations against the cited sources without seeing the implementation, and reports every mismatch.
   - **Rules.** Define these in `.claude/agents/` with full model IDs (`claude-haiku-5-5`, `claude-sonnet-5-5`) so they can't drift, and don't override their model per call. Subagents never commit. Anything touching tick order, determinism, the protocol, combat or PvP, security or persistence stays with the main session; subagents may only research it.

---

## 1. Targets and non-negotiables

- **Revision 242.** Cache: OpenRS2 archive cache 2757 — oldschool / live / en, build 242, captured 2026-11-03 10:00:06; 25 archives, 120,374 groups, ~204 MiB.
  - Cache: `https://archive.openrs2.org/caches/runescape/2757/disk.zip`
  - Keys: `https://archive.openrs2.org/caches/runescape/2757/keys.json`
  - OpenRS2 reports 0 of 0 XTEA keys for this cache. Still download `keys.json` and keep XTEA support in the loader.
  - Spot-check after load: archive 0 version 1,793,635,657 with CRC 1319971744; archive 24 version 1 with CRC -1931708361 (signed).
  - The number 242 appears in exactly one `Revision` constant plus config.
- **Clients:** RuneLite (`github.com/runelite/runelite`) at the build matching rev 242, in two forms. *Testing:* stock RuneLite launched through RSProx (§4.1). *Release:* a standalone, rebranded, patched RuneLite that players install and that connects straight to our worlds (§4.2). 242 is the live revision as of this brief, so current RuneLite matches; pin its bootstrap commit now — both forms use it — so later OSRS updates break neither.
- **Kotlin-first.** Server code, build logic (Gradle Kotlin DSL + convention plugins) and tooling are Kotlin. Other languages only where they're clearly the right tool: our RuneLite fork (Java), protobuf schemas for the hub's gRPC APIs, and SQL migrations.
- **Modular and plugin-based.** The engine knows nothing about content. Combat formulas, prayers, spells, skills, NPC behaviour, areas and minigames all live in plugin modules written against a stable API.
- **Tick-perfect combat and PvP first.** Nothing else is prioritized until the Phase 4 golden scenarios pass.
- **4,096+ concurrent players** across a cluster of worlds (§7).
- **Deterministic.** Same per-tick inputs + same RNG seed ⇒ identical world state every tick. Replay, PvP debugging and the golden tests depend on this.

---

## 2. Sources of truth (priority order)

1. **The cache.** Configs (obj, npc, loc, seq, spotanim, varp, varbit, enum, struct, param, inv, dbrow/dbtable, area, …), maps, interfaces, clientscripts. **Archive 24 holds Jagex's gameval names** (symbolic names for objs, npcs, locs, interfaces, varbits, dbtables, …). Generate Kotlin symbols from it (§5.1).
2. **osrs-docs.com → Mechanics:** Queues, Timers, Delays, Entity Interactions, Entity Collision, Instancing, Interface Interactions, Random Walk, Retreat, plus its packet and update-mask pages. Written by the author of RSProt/RSProx from Mod Ash's statements. Implement these semantics exactly.
3. **OSRS Wiki mechanics pages:** Game tick, Player identification number, Hit delay, Attack speed, Food/Fast foods (combo eating), Freeze, Protection prayers and Protect from Magic/Missiles/Melee, Prayer drain mechanics, Special attacks, Maximum melee/ranged/magic hit, Damage per second (accuracy), Line of sight, Single-way combat, Multicombat area, PJ timer, Skull (status), Wilderness, Flinching, Retaliation, Tick manipulation, Tele Block. DPS calculator: `tools.runescape.wiki/osrs-dps`.
4. **"Henke's model" of the tick** — `github.com/data-dependent/osrs-guides/blob/master/skilling.md`. The pipeline in §5.3 comes from it and agrees with osrs-docs.
5. **RS Mod** (`github.com/rsmod/rsmod`, Kotlin, ISC, targets rev 233, aims for mechanical accuracy) — reference for engine semantics and module boundaries. If you adapt code, keep the ISC notice and log it in DECISIONS.md.
6. **RSProt** (`github.com/blurite/rsprot`, MIT) — OSRS networking: JS5, login, game packets, player/NPC/world-entity info. **RSProx** (`github.com/blurite/rsprox`, MIT) — local proxy with revision-specific decoders and a client patcher/launcher; it is the dev client path (§4).
7. **RuneLite** — `cache/` module (definition loaders as a decoding reference) and `runelite-api` gameval constants.

---

## 3. Toolchain

Verify the latest patch versions at setup, then pin everything in `gradle/libs.versions.toml`.

- **JDK 25 LTS** (Temurin). **Kotlin 2.4.x** (K2). **Gradle 9.8.x** wrapper, configuration cache on, convention plugins in `build-logic/`.
- kotlinx.coroutines (content scripts suspend on the game thread, §5.4), kotlinx.serialization, **KSP** (version matching Kotlin) for plugin registries and symbol generation.
- Use Kotlin 2.4's now-stable **context parameters** to make "protected access" a compile-time requirement: functions that mutate a player take `context(access: ProtectedAccess)`.
- **Netty 4.2.x**: io_uring transport on Linux (first-class since 4.2), epoll fallback, NIO on dev machines; pooled direct buffers.
- **`org.rsmod:rsmod-pathfinder`** (ISC) — RS-accurate BFS routing, naive NPC pathing, line of sight/walk, reach strategies, collision flag map. Check for a newer maintained successor before adopting.
- **ASM** for the release client's game-client patch step (§4.2).
- **PostgreSQL + HikariCP**; blocking DB and hub calls on virtual threads (JDK 24+ no longer pins on `synchronized`). No reflection-heavy ORM or DI: constructor injection and one composition root.
- **Observability:** Micrometer → Prometheus, JFR (JDK 25 adds method timing/tracing and cooperative sampling), kotlin-logging + Logback.
- **JVM baseline — benchmark, don't assume:** generational ZGC vs Generational Shenandoah vs G1; `-XX:+UseCompactObjectHeaders` (a product feature in JDK 25, off by default) where the chosen collector supports it; the AOT cache (JEPs 483/514/515) for startup.
- JMH / kotlinx-benchmark for hot paths only.

---

## 4. Clients and protocol

### 4.1 Testing client: RuneLite via RSProx

Stock RuneLite launched by RSProx through a custom proxy target — for development and testing only, never shipped to players. RSProx reads `~/.rsprox/proxy-targets.yaml`; a target needs `name`, `jav_config_url`, `modulus` (hex RSA modulus), and optionally `revision`, `game_server_port` (if not 43594) and `runelite_bootstrap_commithash`. Limits: private-server targets work on Windows and Linux only (not macOS), revision ≥ 223, no protocol-breaking client changes. Deliver:

- An HTTP endpoint on the server serving `/jav_config.ws` and `/worldlist.ws` listing every world in the cluster. RSProx has its own HTTP server for both files; use it as the format reference.
- A `generateRsa` task: 1024-bit RSA keypair into `.data/rsa/`; print the modulus for the YAML. The private key never enters git.
- `docs/CLIENT.md`: the exact `proxy-targets.yaml`, the pinned RuneLite bootstrap commit for rev 242 (from the `static.runelite.net` `bootstrap.json` history), and troubleshooting. Phase 6 extends it with the release client's build, signing and update process.

### 4.2 Release client: standalone patched RuneLite

The client players install: a standalone RuneLite build that connects straight to our worlds — no RSProx, no proxy hop. Built in Phase 6 under `client/`.

- **Fork RuneLite's client; binary-patch only the game client.** RuneLite-side changes live in our fork of `runelite-client` (Java) at `client/runelite/`: a small, isolated patch series rebased onto the RuneLite release that matches each revision. The game client RuneLite runs isn't built from RuneLite's public repo — it comes in as a published artifact — so its changes are a build-time binary patch (`client/gamepatch/`, Kotlin + ASM). Inputs: RuneLite's source at the tag matching the pinned bootstrap commit, plus the game-client artifact listed in that `bootstrap.json`, mirrored to our private artifact store.
- **Write our own game-client patch step.** RSProx's RuneLite patcher is tied to RSProx's backend and, per its own PATCHER.md, can't be used for private servers without major changes. Read it (MIT) for what needs changing; its native patcher's techniques — constant-string replacement, wildcard byte patterns, regex string replacement — are the model for ours.
- **Change set.** Fork changes are ordinary source edits confined to as few files as possible; binary patches find their target by value — never by obfuscated names — and must match exactly once:
  1. Fork — `runelite.properties`: `runelite.title` → our name; `runelite.jav_config` and `runelite.jav_config_backup` → our `/jav_config.ws`; `runelite.pluginhub.url` → our curated hub, or the hub disabled; `runelite.api.base`, `runelite.session`, `runelite.ws`, `runelite.static.base` and `runelite.config` → our services or stubs; `runelite.discord.*` → ours or disabled; help and support links → ours.
  2. Binary — Jagex's RSA modulus in the game client → ours, located by its value; any game-client URLs, such as the login screen's account-creation link → ours.
  3. Fork — RuneLite's artifact and integrity verification → re-rooted to our own signing key (preferred) or removed; record which, per check, in DECISIONS.md.
  4. Fork — anything else that contacts Jagex or RuneLite services (telemetry, world list, hiscore and price lookups, account/config sync, Jagex Account sign-in) → redirected to us, stubbed, or disabled. No player data goes to third parties. The world hopper must list our worlds: the hub serves a compatible worlds endpoint under our `runelite.api.base`.
- **Plugin policy (PvP integrity).** The fork enforces an allowlist of core and hub plugins. A self-hosted hub means swapping the key the client uses to trust hub manifests; otherwise disable the hub. The server stays authoritative — plugin policy only limits information advantages.
- **Login.** The client's normal username/password flow against our login service; no dependency on Jagex Account or launcher tokens.
- **Branding and licenses.** Fully rebranded: name, icons, window title, splash, login-screen text. RuneLite's name and logo aren't ours to ship. Keep RuneLite's BSD-2 notices in an About/Licenses screen and in the distribution.
- **Packaging and updates.** A jlink runtime (the Java major version RuneLite's own launcher bundles — check it, don't assume) plus jpackage installers for Windows, macOS and Linux on x64 and arm64; players never install Java or RSProx. A small Kotlin launcher fetches a signed manifest (artifact list + hashes, like RuneLite's bootstrap) from our update endpoint, verifies it, updates, and starts the client. Authenticode-sign on Windows; sign and notarize on macOS (needs an Apple Developer ID).
- **Revision coupling.** Client and server revisions move together. A revision bump = new cache + protocol port + rebuilt patched client + new manifest; outdated clients get the login "client out of date" response and the launcher updates them on next start.

### 4.3 Server-side protocol

**Login:** proof-of-work on fresh logins (the OSRS client supports SHA-256 PoW; RSProt defaults to difficulty 18), none on reconnects. RSA decode off the Netty threads (about 0.5 ms per 1024-bit op). Validate the client's archive CRCs against our master index. ISAAC. Bounded admissions per tick.

**JS5:** pre-load and pre-encode every group at startup into direct buffers; serve fairly per client in blocks (RSProt's model: 512-byte blocks, three times larger for logged-in clients, flush thresholds); support the XOR-key opcode.

**Protocol implementation — follow this decision procedure and record the outcome in DECISIONS.md:**
1. If Maven Central has `net.rsprot:osrs-242-api`, use it.
2. Otherwise vendor RSProt's newest revision module (the upstream README lists 221–240; a 241 artifact may also exist — take the newest) into `protocol/osrs-242/`, keep the MIT headers, and port it to 242 using **RSProx's rev-242 decoders as ground truth**: diff RSProx's decoders between that revision and 242 to find every changed opcode, size and field transform, and mirror them in our encoders/decoders.
3. If neither path is available, stop and ask before attempting to derive the protocol from the client.

Put everything behind our own `protocol-api` interfaces so a revision bump is a module swap.

**Info packets:** player info (≤ 2,047 players per world; each observer holds roughly 250 high-resolution players), NPC info (roughly 250 rendered per observer), **world entity info** (rev 242 has Sailing boats as world entities — model them in the engine now, content later), and zone updates built once per zone per tick and shared by every observer of that zone. Encode extended-info blocks once per entity per tick and copy them into observers' buffers. Update avatars in a dedicated pass after all game logic, as RSProt recommends.

**Recording:** enable RSProt's RSProx-compatible `.bin` export behind a dev flag so our own sessions can be transcribed in RSProx.

---

## 5. Engine

### 5.1 Modules

```
build-logic/          convention plugins
util/                 primitive collections, buffers, CoordGrid, typed ids
cache/                JS5 store, archives/groups, XTEA, every config decoder, maps -> collision
symbols/              generated from cache archive 24 (gamevals): Objs.*, Npcs.*, Locs.*, Interfaces.*, Varbits.*
protocol-api/         our message types and codec interfaces
protocol/osrs-242/    the revision implementation (RSProt or the vendored port)
net/                  Netty bootstrap, sessions, JS5, login, HTTP (jav_config / worldlist)
engine/               world, tick pipeline, entities, zones, collision, routing, queues/timers/delays,
                      interactions, varps, inventories, interfaces, combat primitives, info pass
plugin-api/           stable content API + Kotlin DSL (explicit API mode)
content/<area>/       plugins: combat, pvp, prayer, magic, equipment, consumables, wilderness,
                      npcs/..., skills/..., areas/..., minigames/...
services/             account, social (friends/ignore/PM/clan chats), exchange, hiscores
server/               world-node main, config, wiring
hub/                  hub main
client/runelite/      release client: our RuneLite fork (Java), a small patch series on the pinned RuneLite tag
client/gamepatch/     release client: build-time binary patch of the game client (RSA key, URLs; Kotlin + ASM)
client/launcher/      release client: signed-manifest updater and launcher
client/dist/          release client: jlink runtime + jpackage installers per OS/arch
tools/                botswarm (load test), scenario (golden tests), cache-dump, wiki-import, replay
```

Content modules depend only on `plugin-api` and `symbols`. Engine internals are `internal`.

### 5.2 World model

- Players in slots 1–2,047. NPC slot space per the rev-242 NPC info encoding (verify the bit width). World entities 1–4,094 (per RSProt). Arrays and free lists; reuse objects; **zero steady-state allocation in tick hot paths**.
- `@JvmInline value class CoordGrid(val packed: Int)` (x, z, level); zones 8×8, map squares 64×64. Instancing per osrs-docs.
- Collision built from map locs at startup: an immutable base shared by every world in the JVM, plus a per-world dynamic overlay for loc changes. Routing, line of sight and line of walk follow rsmod-pathfinder semantics. Player routes are computed server-side from the clicked destination; NPCs use naive pathing. Walk 1 tile per tick, run 2.
- One seeded RNG per world. Content may only use the world RNG.

### 5.3 The tick

A fixed 600 ms cadence anchored to the start instant (no drift). An overrun runs the next tick immediately and records a missed-deadline metric; logic phases are never skipped. Encode the pipeline as an ordered list of phases and document it, with sources, in `docs/mechanics/tick.md`:

1. **Pre-tick:** advance the clock; drain the cross-thread inbox (completed logins, hub messages) into world state.
2. **Client input, every player (PID order):** consume up to 10 queued commands in arrival order (the OSRS limit; matches RSProt's 10 user-packet threshold). Input sets intent and runs immediate scripts — equip, eat, drink, prayer toggles, interface buttons, route and interaction targets. Clicks received during tick N are acted on in tick N+1's input phase.
3. **NPCs in index order:** delay ends → timers → queue → loc/obj interactions → movement → player/NPC interactions (aggression, retreat and random walk per osrs-docs).
4. **Players in PID order:** delay ends → if a strong script is queued, close the modal (which clears weak scripts) → queue → timers → area queue → loc/obj interactions → movement → player/NPC interactions → logout check.
5. **Post-tick:** logouts, bounded logins, PID reshuffles when due (100–150 ticks normally, 40–60 on PvP, Bounty Hunter and tournament worlds; seeded; whether the window is per-player or global goes in tick.md, marked `OPEN` if unresolved).
6. **Info pass (parallel):** world state is now frozen. Compute player/NPC/world-entity info and zone updates on a fixed pool sized to the cores, encode, and flush each session once.

These consequences must fall out of the order, never be special-cased: NPC hits on players land the same tick; player hits on NPCs land a tick later; in PvP the lower PID's zero-delay hit lands the same tick and the higher PID's lands the next.

### 5.4 Scripts, queues, timers, delays, interactions (exact semantics: osrs-docs)

Content is Kotlin `suspend` code run by a single-threaded tick dispatcher bound to the world thread (no real thread hops). Provide `delay(n)`, `arriveDelay()`, dialogue/pause-button suspension, `queue` / `weakQueue` / `strongQueue` / `softQueue` / `longQueue(accelerate | discard)`, `timer(interval)`, `softTimer(interval)`, and protected access (a script may mutate a player only when that player is not delayed and has no modal open).

- **Player queue:** a single FIFO for every type. Not processed while delayed, except soft scripts. If any strong script is present, close the modal first (which removes weak scripts). Loop over the queue until a full pass executes nothing; skip entries scheduled for the future; skip normal scripts while a modal is open; strong and soft scripts close the modal before running; once a script sets a delay, nothing further runs except soft scripts; a script queued from inside another script runs no earlier than the next tick. Weak scripts are removed by interruptions: interacting with an entity or tile, inventory item actions, unequipping, opening or closing interfaces, dragging items. Most damage is a strong script (recoil uses normal; some effects apply directly inside the item script). NPCs have one queue with one strength. Players also have an **area queue** (multiway borders, music unlocks, farming).
- **Timers:** NPCs run timers before their queue; players run them after. A timer never fires on the tick it is added — this is why prayer flicking does not drain. Soft timers tick regardless of state and cannot read or change inventories or vars. Normal timers (players only) halt at zero while the player is delayed or has a modal open, retry each tick, then reset to their interval. An NPC can hold only one timer. Prayer drain, freeze/teleblock immunity and stat replenishment are player soft timers; toxins are normal timers.
- **Delays:** arrive delay is 1 tick if the entity moved this tick or last tick; a normal delay is N ticks. While delayed: no interactions, no queue processing, timers pause at zero, route events don't run (already-determined movement continues), and most input is ignored.
- **Interactions:** `op` requires line of walk and cardinal adjacency; `ap` requires line of sight within the approach range (default 10; −1 converts it to op). Priority: script op → script ap → engine ap → engine op ("Nothing interesting happens." / "I can't reach that!"). Attempt, move, attempt again — exactly as osrs-docs' pseudocode, including which target types may op after moving (this produces banker-vs-booth timing). Interactions are interrupted by a new interaction, death, player-invoked teleports, certain buttons and any inventory item action; equipment-tab actions do not interrupt. Modals and delays pause them.

### 5.5 Other engine systems

Varps/varbits (dirty-tracked, sent at end of tick), inventories from `inv` configs (partial updates), IF3 interfaces (modal/overlay, events), public chat and PMs, ground items, dynamic locs, NPC spawns, death/respawn, appearance.

- **Tick inspector (required):** a per-entity, per-tick trace of every pipeline phase — commands consumed, scripts run, timers fired, interaction result, steps taken, hits applied — written as JSONL behind a flag, plus a dev command that streams your own trace to the chatbox.
- **Replay (required):** record decoded inputs per tick plus the RNG seed; `tools/replay` re-runs a world offline and diff-checks per-tick state hashes.

---

## 6. Plugin system

- **Discovery at compile time:** KSP generates a registry per content module; no runtime classpath scanning. Each module exposes `ContentPlugin { fun ContentScope.register() }`.
- **Typed handlers keyed by gameval symbols** (names below are illustrative; use whatever archive 24 generates):

```kotlin
class Consumables : ContentPlugin {
    override fun ContentScope.register() {
        onInvOp(Objs.SHARK, op = 1) { eat(it, heal = 20, attackDelay = 3) }
        onInvOp(Objs.COOKED_KARAMBWAN, op = 1) { comboEat(it, heal = 18, attackDelay = 2) }
        onOpPlayer(PlayerOp.ATTACK) { target -> attack(target) }
        onButton(Interfaces.PRAYERBOOK, Components.PROTECT_FROM_MAGIC) { togglePrayer(Prayer.PROTECT_FROM_MAGIC) }
    }
}
```

- Handlers run synchronously on the game thread. A typed event bus carries cross-plugin hooks (on hit, on death, on equip, on login). No reflection on hot paths.
- No hot reload. Instead, fast restarts: a dev boot (cache, collision, world) in under 10 seconds using the JVM AOT cache and a prebuilt collision snapshot; replay (§5.5) gets you back to a scenario after a restart.

---

## 7. Scale: 4,096+ concurrent

- **Hard limit:** player indices allow at most 2,047 players per world, and official members worlds cap at 2,000. Two worlds top out at 4,094, so **4,096+ requires at least 3 worlds**. Target 3–4 worlds at 2,000 each (6,000–8,000 capacity), with each world proven at 2,000.
- **Topology:** a `world-node` JVM hosts 1..N worlds, each on its own dedicated platform thread; the immutable cache, definitions and base collision are shared in memory across the worlds in that JVM. The `hub` owns login authority and accounts, friends/ignore/PMs, clan and friends chats, the Grand Exchange, hiscores, the world directory and broadcasts. Define these as Kotlin interfaces with an in-process implementation first; in Phase 5 put them behind gRPC (grpc-kotlin coroutine stubs) — schema-first, streaming for chat and broadcasts, and callable from a future website or admin panel in any language. World hopping = durable save, then the hub hands the session to the new world.
- **Per-world budget** at 2,000 players and tens of thousands of NPCs (Jagex's own description of a live world), on a 16-core machine: logic phases 1–5 p99 ≤ 120 ms; info pass + flush p99 ≤ 80 ms; GC pause p99.9 < 2 ms; zero steady-state allocation in hot loops, proven with JFR. Reference point: RSProt's benchmark computes player info for 2,046 players packed into a 13×13 area in about 11.5 ms per tick multithreaded on a Ryzen 3950X.
- **Login storms:** bounded admissions per tick, PoW difficulty that scales with load, RSA on a pool.
- **Persistence:** write-behind saves on virtual threads; staggered autosaves; a logout save must be durable before the account can log in anywhere else (dupe prevention).
- **botswarm — the scale acceptance test:** headless clients that speak the real rev-242 login and game protocol (reuse our protocol module), each running scripted behaviour: walking, clumped multi-combat, chat, gear-switch spam, world hops. It must sustain 4,096+ players across ≥ 3 worlds for 30 minutes within budget and emit tick-time histograms.

---

## 8. Combat and PvP

Model every attack as a timeline: **attack tick** (accuracy and damage rolled) → hit queued on the defender as a strong script with delay = hit delay (+1 if the defender is processed before the attacker this tick) → **application tick** (HP change, hitsplat, retaliation/flinch, death queue). `docs/mechanics/combat-timeline.md` must state, for each attack type, the roll tick, the protection-prayer check tick and the application tick, with sources; anything unverified is `OPEN`.

**Hit delay.** Distance is Chebyshev, edge to edge; barrage spells and thralls measure to the defender's south-west tile.

| Attack | Delay (ticks) |
|---|---|
| Melee (exceptions such as the dragon claws spec) | 0 |
| Bows, crossbows, eclipse atlatl | 1 + floor((3 + d) / 6) |
| Thrown weapons, chinchompas, toxic blowpipe | 1 + floor(d / 6) |
| Most magic (spells, powered staves) | 1 + floor((1 + d) / 3) |
| Dark bow, second arrow | 1 + floor((2 + d) / 3) |
| Tumeken's shadow | 2 + floor((1 + d) / 3) |
| Dragonfire shield/ward, ancient wyvern shield specs | 2 + floor((4 + d) / 6) |
| Ballistae | 2 at d 1–4, 3 at d 5–10 |
| ACB/ZCB spec 2; Grasp spells 1; demonbane and Nightmare-staff specs 2 | fixed |

**Attack clock.** Attack when the clock is ≤ now and the interaction is in range; on attacking, set it to now + weapon speed (rapid style per the wiki). Eating adds to the clock: standard food +3; karambwan, gnome foods and halibut +2. The addition is unconditional, so it only matters when the result lands in the future — eating before engaging costs nothing. Combo eating stacks (shark + karambwan = +5). Potions don't touch the attack clock.

**Consumption clocks.** Keep separate eat, potion and karambwan clocks. Standard food blocks only further food, for 3 ticks; potions have their own sip clock; a karambwan blocks food, potions and karambwans for 3 ticks. So food → potion → karambwan works within one tick (e.g. marlin + Saradomin brew + halibut). Fast foods with 1–2 tick eat delays follow the wiki table.

**PID.** Lower PID processes first; reshuffle per §5.3. Golden tests force PID.

**Prayers.** Protection prayers block 40% of damage from players (100% from most NPC attacks). Protect from Magic doesn't change splash chance, no longer shortens Bind/Snare/Entangle (removed 5 Nov 2019), and halves Tele Block (5:00 → 2:30). Drain is a soft timer, so a correctly timed flick costs nothing; formulas from "Prayer drain mechanics".

**Freezes.** Bind/Snare/Entangle 8/16/24 ticks; Ice Rush/Burst/Blitz/Barrage 8/16/24/32; then a 5-tick immunity (Grasp spells: 2). Ancient sceptre +10% (rounded down); Zamorak godsword spec 32 ticks on a successful hit; abyssal tentacle spec 8 ticks regardless. In PvP a freeze only holds while both players stay within 10 tiles (x/y only). Immunity is a soft timer.

**Regeneration.** Special attack energy +10% per 50 ticks (Lightbearer: 25). Boosted and drained stats move 1 level per 100 ticks (Preserve: 150 for boosts), checked every 25 ticks.

**Equipment data.** Bonuses, attack speeds and weapon categories come from obj params, enums and structs in the cache wherever present — document the param ids you find; fall back to wiki-import only where the cache lacks data.

**Also in scope for Phase 4** (formulas from the §2 wiki pages): melee/ranged/magic accuracy and max hit; special attacks as data plus hooks; flinching; auto-retaliate; recoil (normal queue), vengeance, Smite, Redemption, Retribution; skull and PJ timers; single/multi-way (area queue on borders); Wilderness levels and combat brackets; PvP worlds and safe zones; the combat logout lock; death, items kept on death, Protect Item; Tele Block; projectiles and spot anims timed from the hit delay so visuals land on the hitsplat tick. Validate damage formulas against the wiki DPS calculator for a recorded gear matrix.

---

## 9. Tests — the complete list

All deterministic and headless (no network) unless stated; each under 1 second.

1. **Cache smoke:** opens cache 2757, decodes every config type and every map square without error, runs the archive spot-checks from §1, generates symbols.
2. **Protocol round-trip (the only networked test):** the botswarm client completes the JS5 handshake, login (PoW + RSA + ISAAC + CRC check) and a handful of game packets against a local server.
3. **Golden PvP scenarios** through `tools/scenario` — the real engine, scripted per-tick inputs, forced PID, fixed seed, tick-by-tick assertions:
   - a. Both players melee each other on tick T: the lower PID's hit lands on T, the other's on T+1.
   - b. Ranged and magic at several distances land on the table's tick (+1 when the defender processes first).
   - c. Shark + brew + karambwan in one tick: all heals apply; attack clock +5; food blocked for 3 ticks.
   - d. Ice Barrage: frozen 32 ticks per combat-timeline.md, then 5 ticks of immunity; the freeze breaks once the caster is more than 10 tiles away.
   - e. Prayer flick: protect prayer activated on T, deactivated on T+1 — protection applies to a hit checked on T, and zero prayer points drain.
   - f. Inventory weapon switch + special attack + attack click in one tick: the new weapon's spec and speed apply, energy is deducted, and the earlier interaction is interrupted (per osrs-docs) then replaced by the new attack.
   - g. Eating while the attack clock is in the future delays the next attack by 3; eating while idle doesn't.
   - h. NPC melee on a player lands the same tick; player melee on an NPC lands the next.
4. **Load (manual or nightly, not in CI):** botswarm at 4,096+ across ≥ 3 worlds (§7).
5. **Release-client build check (from Phase 6; may exceed 1 second):** the RuneLite fork builds against the pinned tag; every game-client binary patch matches exactly once; the packaged client contains our modulus and endpoints and none of the ones it replaced. This is the first thing a revision bump breaks.

---

## 10. Phases and definitions of done

- **Phase 0 — Scaffold.** Gradle multi-module, version catalog, convention plugins, CLAUDE.md and docs, `.claude/settings.json` and the three subagent definitions (§0 item 8), a `setup` task (downloads cache 2757 and keys.json to `.data/cache/242/`, records SHA-256s, verifies), `generateRsa`, CI running build + tests. **Done when** `./gradlew build` is green and the server boots logging "rev 242: 25 archives, 120,374 groups".
- **Phase 1 — Cache and world data.** All config decoders, map/loc decoding, collision for the whole map, gameval symbol generation, the cache-dump tool. **Done when** test 1 passes and startup time and heap for cache + collision are recorded.
- **Phase 2 — Networking and client.** JS5, login, HTTP jav_config/worldlist, the §4 protocol decision executed; RuneLite via RSProx downloads the cache, logs in, walks and runs with server-side routing, and sees bots. **Done when** test 2 passes and docs/CLIENT.md works from a clean machine.
- **Phase 3 — Engine core.** The tick pipeline (§5.3); scripts, queues, timers, delays and interactions (§5.4); varps, inventories, equipment, interfaces, chat, NPC spawns and basic AI, ground items, dynamic locs, tick inspector, replay, packet validation and the item-transaction API (§12). **Done when** the scenario runner exists and tick.md plus the mechanics docs cite sources.
- **Phase 4 — Combat and PvP.** Everything in §8. **Done when** every golden scenario passes, the DPS-calculator parity matrix passes, and a 1v1 plus a 20-player multi fight in RuneLite time correctly in the tick inspector.
- **Phase 5 — Scale and operations.** Hub, multi-world node, world hopping, social, Grand Exchange skeleton, persistence with versioned saves, plus accounts, moderation, operations, DDoS setup and the revision-upgrade pipeline (§12). **Done when** the §7 load acceptance holds for 30 minutes.
- **Phase 6 — Release client.** Everything in §4.2. **Done when** test 5 passes and, on clean Windows, macOS and Linux machines, the signed package installs, auto-updates, logs in to the cluster and world-hops through the world hopper, with a traffic capture showing no connections to Jagex or RuneLite hosts.
- **Phase 7+ — Content breadth** as plugins, prioritized in PROGRESS.md: PvP-relevant content first (Wilderness, arenas), then skills, quests, bosses. Content work can run in parallel with Phase 6.

**Data not in the cache** (NPC spawns, drop tables, shops, monster bonuses): `tools/wiki-import` pulls structured data from the OSRS Wiki API into versioned JSON under `data/`, with source URLs, a descriptive User-Agent and rate limiting. Wiki content is CC BY-NC-SA 3.0; record provenance and license per dataset in `data/PROVENANCE.md`.

---

## 11. Conventions

- Kotlin: explicit API mode in `plugin-api`; `internal` by default elsewhere; no `!!`; value classes for ids and coordinates; sealed types for messages; no global mutable singletons (world-scoped state only); no blocking calls, logging or allocation in tick hot loops (sampled logging only).
- Formatting and lint: ktlint via Spotless plus a lean detekt config, both in CI.
- Config: typed Kotlin data classes loaded from TOML, with per-world overrides (PvP world flags, PID shuffle window, PoW difficulty).
- Every non-obvious constant links to its source in a one-line KDoc.

---

## 12. Security and operations

- **Treat every client packet as hostile.** Before acting, a handler confirms that the referenced interface and component are actually open; that inventory slots, item ids and counts are in range and match server state; and that targets are in range and visible. Most private-server exploits come from skipping one of these checks. Drop invalid input, count it per session, and disconnect repeat offenders.
- **Accounts.** Argon2id password hashing; sign-up through a web endpoint on the hub (the release client's "New user" link points there, §4.2); login throttling per IP and per account; configurable connection limits per IP.
- **DDoS protection.** Production sits behind a DDoS-protected TCP proxy with PROXY protocol v2 enabled, so the server still sees real client IPs (RSProt includes a HAProxy module). World-node addresses are never published.
- **One item-transaction path.** Every item movement — trade, Grand Exchange, bank, drops and pickups, death, shops, rewards — goes through a single atomic inventory-transaction API: all-or-nothing across every inventory involved, and journaled to an append-only audit log (who, what, where, tick). This is the anti-duplication design and the tool for tracing one.
- **Versioned saves.** Player saves carry a schema version with forward migrations, so a revision bump never corrupts or drops account data.
- **Moderation.** Kick, mute and ban (account and IP), with permissions as data and every staff action in the audit log.
- **Operations.** Graceful shutdown through the client's built-in system-update countdown, saving every player before stopping; health checks; alerts on tick p99, missed tick deadlines and save latency; PostgreSQL backups with point-in-time recovery, and a restore rehearsed before launch.
- **Revision upgrades.** `./gradlew upgradeRevision -Prev=N` plus `docs/REVISION-UPGRADE.md`: fetch the new cache from OpenRS2, regenerate symbols, report protocol changes (RSProt/RSProx diffs), add save migrations if needed, run every test, rebuild the release client and publish its update manifest. Goal: ship a new OSRS revision within days of Jagex releasing it.

---

## 13. Start now

1. Save this brief as `docs/BRIEF.md`; write `CLAUDE.md`, `docs/PROGRESS.md` and `docs/DECISIONS.md`; set up `.claude/settings.json` and `.claude/agents/` per §0 item 8.
2. Check and record in DECISIONS.md: does `net.rsprot:osrs-242-api` exist on Maven Central? Does RSProx have a rev-242 protocol module? What is the latest `rsmod-pathfinder` (or successor)? Which RuneLite bootstrap commit matches rev 242 (both clients pin it)? What are the exact latest patch versions of JDK 25, Kotlin 2.4, Gradle 9.8, Netty 4.2 and KSP?
3. Write `docs/plans/phase-0.md`, implement Phase 0, then stop and report, including any blocking questions.
