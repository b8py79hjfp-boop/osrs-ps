# Phase 3 — Engine core

## Scope (brief §5.3–5.5, §6, §12)

1. **Tick pipeline** (§5.3) as an ordered phase list, documented in `docs/mechanics/tick.md`: pre-tick → client input
   (PID order, ≤ 10 commands) → NPCs (index order: delay end → timers → queue → loc/obj interaction → movement →
   player/npc interaction) → players (PID order: delay end → strong-queue modal close → queue → timers → area
   queue → interactions-with-movement → logout check) → post-tick (logouts, bounded logins, PID shuffle) → info pass.
2. **Script runtime** (§5.4): content is Kotlin `suspend` code run synchronously on the world thread with the stdlib
   coroutine intrinsics (no dispatcher, no thread hops). Suspension points: `delay(n)`, `arriveDelay()`,
   pause-button/dialogue resume. Protected access via a `ProtectedAccess` context parameter that the engine grants
   only when the player isn't delayed and has no modal open.
3. **Queues** (normal/weak/strong/soft, long queue accelerate/discard, area queue), **timers** (normal/soft; NPC
   single soft timer), **delays** (normal, arrive), exactly as osrs-docs; see `queues.md`, `timers.md`, `delays.md`.
4. **Interactions** (§5.4): op/ap for locs, objs, NPCs and players per osrs-docs' pseudocode, using rsmod-pathfinder
   reach strategies (op) and line of sight (ap). Interruptions as listed. See `interactions.md`.
5. **World systems** (§5.5): varps/varbits (dirty-tracked, sent at end of tick), inventories from `inv` configs
   (partial updates) + equipment, IF3 interfaces (top/modal/overlay, events, close), public chat + display name,
   ground items, dynamic locs (zone updates built once per zone per tick), NPC spawns + random walk (osrs-docs Random
   Walk), death/respawn skeleton, appearance.
6. **plugin-api** (§6): `ContentPlugin { fun ContentScope.register() }`, typed handlers keyed by gameval symbols, a
   typed event bus. KSP generates a registry per content module (no classpath scanning). Phase 2 dev content
   moves into `content/` modules.
7. **Tick inspector** (§5.5): per-entity, per-tick JSONL trace of every phase behind a flag, plus `::trace` streaming
   your own trace to the chatbox.
8. **Replay** (§5.5): record decoded inputs per tick + RNG seed; `tools/replay` re-runs offline and diffs per-tick
   state hashes.
9. **Security** (§12): packet validation (interface open, slot/obj/count match, target in range/visible), invalid
   input counted per session with disconnect, and the single atomic, journaled **item transaction API**.
10. **Scenario runner** (`tools/scenario`): headless world (no network) driven by scripted per-tick inputs, forced
    PID, fixed seed, tick-by-tick assertions. The Phase 4 golden tests are written on it.

## Order of work

1. Mechanics docs from sources → tick pipeline + entity base (Player, Npc) + script runtime/queues/timers/delays.
2. Interactions + movement integration.
3. Vars, inventories/equipment, interfaces, chat → protocol-api messages + adapter.
4. NPCs (spawns, info, random walk), ground items, dynamic locs, zone updates.
5. plugin-api + KSP registry; move dev content to `content/`.
6. Inspector, replay, validation, transactions, scenario runner.

## Risks

| Risk | Mitigation |
|---|---|
| Coroutine resume semantics drift from osrs-docs (e.g. where a delayed script resumes) | Single place (`ScriptRunner`), documented in tick.md; scenario runner exercises it |
| Allocation in hot loops (coroutines allocate) | Scripts run on actions, not every tick per entity; pooled per-tick collections; JFR check in Phase 5 |
| NPC spawn data isn't in the cache | Small dev spawn file now; `tools/wiki-import` (implementer) for real spawns later, provenance in data/PROVENANCE.md |
| KSP + Kotlin 2.4 compatibility | KSP 2.3.13 is KSP2 (decoupled); fall back to a hand-written registry behind the same interface if blocked |
| Interactions vs Henke's split | Implement osrs-docs pseudocode verbatim; it produces Henke's banker-vs-booth timing |

## Definition of done (§10)

The scenario runner exists, and `tick.md` plus the mechanics docs cite their sources, with a verifier pass. The
real client still logs in, walks and opens the gameframe after the refactor.
