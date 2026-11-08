# Raids plan

Brief: [raids-brief.md](raids-brief.md) (saved verbatim 2026-11-08). The user asked to **start with The Fractured
Archive (TFA)** instead of the brief's ToB-first order (§10); DECISIONS D-079 records the change.

## Name mapping (brief → repo)

| Brief | Repo |
|---|---|
| `content/raids-core`, `content/raids-tfa`, … | `content/raids-core`, `content/raids-tfa`, … (new Gradle modules, `tp.content.raids.*`) |
| `data/raids/`, `data/PROVENANCE.md` | `content/<raid>/src/main/resources/` TSVs (as every other content module); provenance in `data/PROVENANCE.md` (new) |
| `RebuildRegionV2` | protocol-api `RebuildRegion` → RSProt `RebuildRegionV2` (already sent for instances, instances.md IN4) |
| BRIEF §0 item 8 model policy | `.claude/agents/{scout,implementer,verifier}.md` |
| "world RNG" | `World.random` (deterministic, seeded) |
| item-transaction API | `PlayerRef.transaction { … }` (engine `ItemTransaction`) |

## What already exists

- **Instancing** (`engine/.../map/Instances.kt`, plugin-api `InstanceRef`, docs/mechanics/instances.md IN1-IN5):
  allocation in instance space, rotated zone copies (collision + locs), REBUILD_REGION_V2 with version bumps, destroy.
  Used by houses and Tempoross.
- **NPC fights**: `content/combat` `Boss` scripts (attack hand-off, incoming/afterHit/onLethal hooks, minions, damage
  tracking per NPC), boss HP bar, NPC combat data import, aggression.
- **Death pipeline**: `CombatSystem` death → `DeathRules.dropItems` (kept items, PvP/PvM split, `StorageDeath`), graves
  and Death's Office (reclaim fees), respawn.
- **Collection log** (`content/collectionlog`), **KC-style varps and messages** (boss KC), **hiscores** via the hub,
  **broadcasts** (`StaffCommands`/hub broadcast).
- **Event bus**, item transactions with commit hooks, area queue, timers, per-player saved keys and varps.

## What is missing

1. **Engine instancing** (R1):
   - instances are capped at 4 × 4 map squares and built on the world thread; TFA/CoX need larger or multi-part
     instances and the brief wants off-thread build + pre-tick attach;
   - no **template-coordinate mapping** (rotation-aware) helper for spawns, locs, areas;
   - no **guaranteed cleanup** of NPCs and ground items on destroy (instances.md OPEN-IN1);
   - no **per-instance NPC stat overrides** (HP, levels, max hit) without touching shared types;
   - no **lost-session / restart** hook that places a player saved inside a dead instance at the raid exit with the
     raid's rules.
2. **`content/raids-core`** (R2): party core, formation surfaces, lifecycle state machine, rooms, scaling context,
   contribution ledger, death/disconnect/teleport/item policies, rewards (computed once, persisted, idempotent
   payout), records, spectating, client drivers, DSL, per-run RNG and roll log.
3. **Each raid's content** (R3-R6), its data (TSVs) and its docs.

## Order (changed: TFA first)

| Phase | Scope | Done when |
|---|---|---|
| R0 | Research and plan: this file; `docs/mechanics/raids/{common,tfa,client-contract}.md`; TFA room doc stubs with sources; CoX/ToB/ToA fact sheets deferred to their phases | verifier pass on the TFA fact sheet and client contract |
| R1 | Instancing gaps above | an instance of TFA's size builds off-thread and attaches in pre-tick; destroy leaves no NPCs/locs/ground items; restart-inside lands at the exit |
| R2 | `raids-core` + fixture raid + tests 1 and 2 (TFA rows first) | `RaidLifecycleScenario` and `RaidRewardMathTest` (TFA rows) green |
| R3 | **TFA**: lobby, Grouping Board, selection rooms and doors, the eight bosses, Quartermaster, Void reward room, spectating, stats, capes, miniquest start | `TfaSmokeScenario` takes every door; RuneLite Loot Tracker records the chest; KC/PB messages match |
| R4 | ToB | as brief |
| R5 | CoX | as brief |
| R6 | ToA | as brief |
| R7 | Load and soak, RuneLite end-to-end, docs | brief §7 budgets |

### R4 (ToB) sources

Fact sheets (written in R4 research, date anchor 2026-11-03, wiki read 2026-11-08, wiki copies for the verifier in
`/tmp/claude-0/ref/wiki-tob/`): [tob.md](../mechanics/raids/tob.md) (TB1-TB35, 17 `OPEN-TB`), and per room under
`docs/mechanics/raids/tob/`: [maiden.md](../mechanics/raids/tob/maiden.md), [bloat.md](../mechanics/raids/tob/bloat.md),
[nylocas.md](../mechanics/raids/tob/nylocas.md), [sotetseg.md](../mechanics/raids/tob/sotetseg.md),
[xarpus.md](../mechanics/raids/tob/xarpus.md), [verzik.md](../mechanics/raids/tob/verzik.md),
[lobby-rewards.md](../mechanics/raids/tob/lobby-rewards.md). Sources, in the brief's order of authority:

1. **Cache 2757:** NPC configs (ids, size, combat level, base HP; Entry configs hold the 1-player base, TB11), objs (escape
   crystal `cost` 10,000 = the 3 Nov 2026 change), gamevals (`TOB_*` NPCs / locs / seqs / varps / varbits / interfaces,
   `TOTAL_COMPLETED_THEATREOFBLOOD` 1748, `_HARD` 3057, `_STORY` 3058). No ToB dbtables, so all rates and timings are wiki.
2. **OSRS Wiki:** [Theatre of Blood](https://oldschool.runescape.wiki/w/Theatre_of_Blood), [Entry
   Mode](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Entry_Mode), [Hard
   Mode](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Hard_Mode), [Strategies](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies)
   and its [Nylocas wave chart](https://oldschool.runescape.wiki/w/Theatre_of_Blood/Strategies/Nylocas), [Monumental
   chest](https://oldschool.runescape.wiki/w/Monumental_chest), [Chest (Theatre of
   Blood)](https://oldschool.runescape.wiki/w/Chest_(Theatre_of_Blood)), the six boss pages and their Nylocas / blood spawn /
   exhumed / web / pillar pages, [Escape crystal](https://oldschool.runescape.wiki/w/Escape_crystal), [Bandages (Theatre of
   Blood)](https://oldschool.runescape.wiki/w/Bandages_(Theatre_of_Blood)), [Lil' Zik](https://oldschool.runescape.wiki/w/Lil'_Zik),
   [Scoreboard](https://oldschool.runescape.wiki/w/Scoreboard_(Theatre_of_Blood)), Abigaila, Mysterious Stranger, and the
   unique item pages. The reward model is the wiki's own calculator: [Module:Theatre of Blood
   calculator](https://oldschool.runescape.wiki/w/Module:Theatre_of_Blood_calculator).
3. **Updates** (changelog entries up to 2026-11-03): 12 Aug 2026 (party cooldown removed), 3 Nov 2026 (escape crystal 10,000 and
   Death's shop), 22 Jul 2026 (rapier / Sanguinesti item changes), 1 Oct 2025 (Verzik quick-start), 26 Mar 2025 (crystal on
   logout, lore books), 23 Oct 2024 (spectator scrying), 23 May 2024 (Verzik one-player scaling), 1 Mar 2023 (Entry scaling,
   unlimited attempts, free reclaim, 20% loot).
4. **RuneLite** (fixtures only): Loot Tracker regions (ToB 12867, lobby 14642, agree with the wiki map pins) and the chat
   command patterns for the KC / PB messages (still `OPEN-TB17`).

Why TFA can go first despite the brief's reasoning (ToB exercises the most framework with the fewest players): TFA
has the same building blocks — fixed-then-chosen room graph, per-player chests, dangerous deaths with wipe eject and
reclaim, spectating, supply shop — so the framework gets the same coverage, just against the newest and least
documented raid.

## Source situation for TFA (found in R0)

- **The cache does not hold TFA's server data.** Its 14 TFA dbtables (136-141, 259-265) have their rows but **no
  column values** except the stats tables 264/265: drop tables, door sections, room configs, Ezekiel finale patterns,
  Ocellus barrages and the supply shop are server-only and stripped from the client cache. Only their **row names**
  survive (gamevals), which give the item lists of each drop table and shop.
- So, against the brief's §0.4 order, the **wiki is the authority for TFA numbers** (rates, weights, quantities,
  prices, attack patterns); the cache gives ids, item sets, varbits, interfaces, NPC/loc configs and maps. Every
  number that the wiki does not give stays `OPEN` with the most plausible behaviour behind a named config flag
  (BRIEF §0.4).
- The brief's "`ZEAL` in `TFA_DROPTABLE_UNIQUE`" is a row name (`TFA_DROPTABLE_ENTRY_UNIQUE_ZEAL`); with no weights in
  the cache the reconciliation is wiki-only (tfa.md).

## Risks

- **Boss detail**: TFA is three weeks old; wiki pages may lack tick timings and HP tables. Those stay `OPEN`.
- **Instance size and memory**: TFA's arenas may span several map squares far apart; multi-part instances or
  per-room instances with a transfer are both possible (decided in R1 from the map research).
- **Death pipeline**: raids need dangerous-death variants (TFA gravestones outside, reclaim fee) and safe variants
  (CoX); changes must not touch the PvP path used by the golden scenarios (brief §0.8: stop and ask first).
- **Client encodings**: HUD/party varbits must be decoded from client scripts, never guessed.

## Threat list (security, §3.16)

1. Double claim of a chest (two clicks, relog, two clients) → one persisted `RewardResult` per (run, player), payout
   journaled and idempotent.
2. Joining or rejoining after start to farm loot → membership fixed at start; rejoin only for members.
3. Points from NPCs outside the run (or from another run in the same area) → ledger accepts damage only on NPCs
   spawned by this run.
4. Interface ops on components not open (party board, chest, shop) → engine already checks the interface is open;
   every handler re-checks run membership and state.
5. Leaving the instance by teleport or by walking off → teleports refused (escape crystal only where allowed),
   movement clamped to the instance.
6. Raid-only items (supplies, keys) leaving the raid → flagged items removed on exit, never bankable/tradable.
7. Seed or RNG manipulation → per-run RNG seeded from the world RNG at start; rolls logged.
8. Spectators affecting the fight → never targeted, scaled, counted or rewarded; no item ops.

## Not now (brief §13)

Listed in PROGRESS.md: custom raids; the 2023 CoX custom-layout proposal (only the shipped scaled size, Reload and full
layout); TFA Practice Mode and the 9 November 2026 balancing; Leagues/Deadman variants; raid Combat Achievements
(events only); cross-world parties.
