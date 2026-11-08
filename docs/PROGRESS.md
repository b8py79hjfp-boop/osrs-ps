# Progress

## Phase checklist

- [x] **Phase 0 — Scaffold** — done 2026-11-05; CI green on Temurin 25.0.5
- [x] **Phase 1 — Cache and world data** — done 2026-11-05 (pending CI confirmation on JDK 25)
- [x] **Phase 2 — Networking and client** — done 2026-11-05: RuneLite 1.13.6 via RSProx (Windows) downloads the cache, logs in, walks/runs, `::commands` work, sees 20 bots
- [x] **Phase 3 — Engine core** — done 2026-11-06 (awaiting real-client check)
- [x] **Phase 4 — Combat and PvP** — done 2026-11-06: golden 3a–3h + DPS parity matrix pass; user confirmed 1v1 and 20-bot multi in RuneLite
- [x] **Phase 5 — Scale and operations** — closed 2026-11-06 at the user's request. 20-minute 3-world run in the sandbox: 3,008 players, logic within budget, no missed deadlines. The §7 4,096-player run was completed by the user on 2026-11-06 (docs/LOAD-TEST.md; detailed numbers not yet recorded).
- [ ] Phase 6 — Release client
- [ ] Phase 7+ — Content breadth

## Done

- Phase 0 (2026-11-05):
  - Gradle 9.8.2 wrapper (checksums verified), version catalog, convention plugins (`tp.kotlin-base/-library/-api/-app`,
    `tp.root`), all §5.1 modules except `client/*`.
  - Spotless/ktlint and detekt wired into `check`.
  - `setup` task (download, SHA-256 pin, extract, sanity check) and `generateRsa` task (verified locally).
  - JS5 reader: disk store (normal and extended sectors), container (none/bzip2/gzip, XTEA), index (protocols 5–7).
    Exercised against a synthetic cache: multi-sector extended group, big-smart ids, CRC without trailer, absent groups.
  - Server boot: opens the cache, logs the summary, verifies the §1 spot-checks, `--boot-check` for CI.
  - CI workflow; docs; `.claude/` settings and agents.
  - §13.2 checks recorded (D-002).

- Phase 1 (2026-11-05):
  - 22 strict config decoders + IF1/IF3 interfaces; undecoded groups listed (D-012).
  - All 2,949 map squares / 5,021,872 locs (unnamed rev-242 layout, D-013); full-map collision with the client
    bridge rule (D-011, docs/mechanics/collision.md, verifier pass applied).
  - Vendored RS Mod routefinder (D-006 final); symbols generated as Java constant chains (D-014).
  - Test 1 (`./gradlew :tools:symbol-gen:test`) passes in 3.9 s (> 1 s budget, D-016).
  - tools/cache-dump: summary / config / interface / map / collision.

### Phase 1 measurements (4-core sandbox, JDK 21, cold JVM)

| Step | Time | Notes |
|---|---|---|
| Open cache + master index | ~0.1 s | |
| All configs (22 types) | 0.8–1.5 s | |
| Interfaces | ~0.1 s | |
| All map squares | 0.7–1.6 s | 4 decode threads |
| Full collision | 0.3–1.6 s | single-threaded |
| **Total boot data** | **~3.5 s** | under the §6 10 s dev-boot target without a snapshot |
| Heap | 384 MiB after GC (locs + map + collision); ~460 MiB live at end of boot | CollisionFlagMap's zone array alone is 16.7 M refs (~64 MiB) |

- Phase 2 (2026-11-05):
  - RSProt 242 vendored (D-005) + ground-truth diff vs RSProx (all opcodes/sizes match; 2 OPEN).
  - `protocol-api` contracts; `protocol/osrs-242` adapter. JS5 pre-encoded (120,398 groups, 204 MiB, 1.3 s parallel).
    Login with PoW/RSA/ISAAC/CRC via RSProt; bounded admissions per tick.
  - `net`: `/jav_config.ws` (Jagex template rewritten, D-017) + `/worldlist.ws`.
  - Engine slice: anchored 600 ms tick, PID-ordered phases, server-side routing, walk/run, build-area rebuilds, info pass.
  - `tools/botswarm`: headless rev-242 client + `Swarm` runner. **Test 2 passes** (JS5, login, rebuild, player info,
    walk, second observer).
  - docs/CLIENT.md, docs/mechanics/movement.md.
  - Real-client acceptance (user, Windows): two fixes found and made. jav_config `codebase` must be the world host
    (RSProx `findCodebaseReplacementWorld`); rev-242 login without a jxSessionId section (vendored-decoder patch,
    D-005 OPEN-B).

- Phase 3 (2026-11-06):
  - Tick pipeline per docs/mechanics/tick.md: PID-ordered input (≤ 10), NPCs then players, post-tick (logouts,
    bounded logins, per-player PID re-randomisation, ground obj timers), info pass. D-020, D-021.
  - Script runtime (suspend, stdlib intrinsics, no dispatcher), queues (weak/normal/strong/soft, long queue),
    timers (normal/soft), delays (normal/arrive), protected access; interactions (osrs-docs op/ap pseudocode,
    two blocks around movement) for locs, NPCs, players, ground objs. Verifier pass on tick/scripts/interactions.
  - Vars (dirty, end-of-tick), stats (24 incl. Sailing), run energy, inventories and the single atomic, journaled
    item transaction API (inventories and ground). Ground items with zone updates (docs/mechanics/ground-items.md).
  - Input validation: op ranges, loc at clicked tile (`StaticLocs`), NPC/player in view, button on an open
    interface, chat length; invalid input counted with a rate-based disconnect (D-022).
  - plugin-api (facades, `@RestrictsSuspension` scripts, typed handlers, event bus) with KSP-generated registries
    (D-023). Content modules: `content/gameframe` (fixed toplevel + all tabs, login sequence, drop/take) and
    `content/dev` (`::pos ::run ::tele ::item ::empty ::xp ::trace`, a few Lumbridge NPCs).
  - Tick inspector (`--trace file.jsonl`, `::trace` to chatbox), replay (`--record file`, `tools/replay`), scenario
    runner (`tools/scenario`, headless). Live 5-bot recording replayed headlessly: all 149 tick hashes match.
- Phase 4 (2026-11-06; user-confirmed in RuneLite):
  - Mechanics docs with sources and verifier passes: combat-timeline, combat-formulas, combat-data, prayers,
    consumables, freeze, wilderness (+ scripts.md Q13: queue delays count the defender's own turns).
  - Engine: visuals on the protocol seam (anim, spot anim, hitsplat, head bar, face, projectiles as zone ops),
    appearance, entity attributes, freeze movement block, NPC interactions, NPC respawn, targeted ops (spells).
  - content/combat: equipment from cache params, weapon styles, prayers (cache enum 4956 + Wiki), food/brew/karambwan
    clocks, melee/ranged/Ice spells, specials as data, freezes with immunity and the 10-tile break, hit queue,
    retaliation, death, Smite/Redemption/Retribution, items kept on death, stat restoration.
  - content/pvp: Wilderness levels/brackets, PvP worlds, multi areas (RS Mod polygons), single-way PJ timers, skulls,
    logout lock, Tele Block, Vengeance, recoil.
  - Test 3 (golden 3a–3h) passes; DPS parity matrix (688 rows from the Wiki DPS calculator) passes.
  - 20-bot multi fight (`botswarm 20 … fight`, `--max-per-ip 100`): 486 attacks, attack clocks respected.
- Deferred from the Phase 3 plan (tracked for Phase 4 start): dynamic locs (zone loc add/del), inventory drag
  (IF_BUTTOND), examine ops, resizable gameframes, wilderness drop rule (ground-items G4), RSProx `.bin` recording.

- Phase 5 (2026-11-06, implemented; user test pending):
  - Hub (separate JVM or embedded): Argon2id accounts, `/register`, login throttling, session locks held until the
    logout save is durable, PostgreSQL store (versioned jsonb saves, audit log) or in-memory, gRPC services.
  - World node: N worlds per JVM; staggered autosave, save-all on shutdown, system-update countdown; world hopping
    through the World Switcher (LOGOUT_TRANSFER) and `--loopback-hosts` for local hops.
  - Social: friends, ignores, PMs, live mutes. Staff `::kick ::mute ::unmute ::ban ::ipban ::unban ::broadcast
    ::update` (`--admins`). GE skeleton commands. `/hiscores`.
  - Ops: tick histograms, `:9090/health` + `/metrics`, ALERT logs, PROXY v2 (`--proxy-protocol`),
    `upgradeRevision`, docs/OPERATIONS.md (PITR), docs/REVISION-UPGRADE.md.
  - Checks: 300 bots over 3 worlds clean, 179 bot hops with saves carried, PostgreSQL saves survive restart, scaled
    load 417 players / 3 worlds with 0 missed deadlines (docs/LOAD-TEST.md). Fixed: logout lock overflow (logout was
    always refused), bot PoW starving carriers, ZoneSync boxing.
  - Since then:
    - friends chat (channels, Clan Setup, ranks);
    - gameframe buttons: orbs, settings, emotes, equipment stats/price checker/kept on death, music with Wiki area
      geometry, XP setup;
    - GE guide prices;
    - input dialogs;
    - RuneLite MCP dev plugin.
  - Load-test fixes: Argon2 concurrency cap; a failed login accept or a faulty tick no longer kills a world.
  - Load acceptance (§7): the user completed the 4,000-player run on 2026-11-06.
  - Still open:
    - OPEN-H1/H2 (world-hop.md);
    - other OPENs in docs/mechanics.
- Phase 7 content (2026-11-06, started alongside Phase 6):
  - Launcher prompt removed from the release client (structural loginIndex patch, D-028).
  - NPC combat data for 4,304 NPC ids from the OSRS Wiki (`tools/wiki-import/npc_combat.py`, npc-combat.md):
    speed, max hits, styles, bonuses (light/standard/heavy ranged defence), respawn; ranged/magic NPCs attack from range.
  - Poison and venom with the antipoison family (poison.md).
  - PvP worlds: cache-derived safe zones and 16-tick PJ timer; singles-plus areas; Tele Block accuracy (wilderness.md).
  - Grand Exchange interface (grand-exchange.md GE4–GE11), verified in the client: sell, buy, abort.
  - Fixes: NPCs no longer random-walk in combat; replay parses dialog answers.
  - GE completed: modify offer, history, custom %, 2 % tax (5M cap, exempt list), collection box, repeat/buy-from-
    history (GE1–GE15). Verified in the client.
  - NPC aggression and tolerance (aggression.md): hunt, 21×21 tolerance regions, max range, retreat, single-way rule.
  - Bank (bank.md, D-029): tabs, drag (IF_BUTTOND end to end), placeholders, deposit-all, always-stacking bank inv.
  - World spawns: 21k NPC spawns from the Wiki in `content/world` (npc-spawns.md); dev spawns only under
    `-Dtp.devSpawns=true`.
  - Login nag skip in the release client.
  - Wilderness bosses and revenants (bosses.md, boss-lairs.md, revenant-caves.md):
    - a boss framework, plus Callisto/Artio, Vet'ion/Calvar'ion, Venenatis/Spindel, Chaos Elemental, Scorpia,
      Chaos Fanatic, Crazy archaeologist and KBD;
    - dragonfire protection and antifire potions; revenants, bracelet of ethereum and the maledictus;
    - lair and Revenant Caves fees, Escape Caves.
    - Checked headless: every boss fights; traps, hounds, the enraged form, webs, spiderlings and guardians work.
  - Teleports: spells, tablets, jewellery, home teleport (teleports.md).
  - Engine: dynamic locs per world, NPC transform, persisted player content state.
  - Fixes:
    - the content event bus is per world (it was shared by all worlds);
    - spawning NPCs mid-tick no longer throws;
    - the spawn import matches disambiguated Wiki pages (21,062 spawns).
  - Classic Duel Arena with staking (duel-arena.md), chosen by the user over the Emir's Arena minigame:
    - challenge, rules and disabled worn slots, presets, coin/platinum stakes, confirm;
    - four arenas, countdown, forfeit, safe death, taxed winnings.
    - Verified in the client with two accounts, and headless.
  - Generic doors, gates, ladders and stairs (doors-and-climbing.md): 387 door/gate pairs and 4,369 climb links,
    generated from the cache.
  - Teleport leftovers: IF_SUBOP right-click Rub, home cooldown in varp 892, Revenant Caves 2-tick delay.
  - Player trading (trade.md): request, offer with a mirrored inventory, confirm, one atomic swap. Verified in the
    client.
  - Dialogue system (dialogue.md): NPC/player chat heads, options, message and item boxes, page wrapping on real glyph
    widths. Verified in the client.
  - Using items on items, locs, NPCs and ground items (interactions.md I10).
  - Equipment level requirements (cache params 434–437).
  - Examine texts from the Wiki (examine.md).
  - In progress (agents): NPC drop tables; shops.

## Blocking

- Repo: github.com/b8py79hjfp-boop/osrs-ps. CI (JDK 25) runs setup → build → boot check. The dev sandbox still
  builds on JDK 21 locally (D-004); CI is the JDK 25 gate.

### Phase 0 acceptance (2026-11-05)

`./gradlew setup` downloaded cache 2757 and pinned its SHA-256s. `./gradlew :server:run --args=--boot-check` logged
`rev 242: 25 archives, 120,374 groups (23 populated archives, 174 ms)` and passed the archive 0/24 CRC and version
spot-checks. CI on Temurin 25.0.5 shows the same, with checksums matching `242.sha256`.

## Raids (docs/plans/raids.md, brief docs/plans/raids-brief.md)

- R0 research and plan: done for TFA (verifier-checked, 2026-11-08). TFA first (D-079); CoX/ToB/ToA fact sheets come with their phases. Fact sheets: docs/mechanics/raids/{common,client-contract,tfa}.md,
  docs/mechanics/raids/tfa/*.md.
- Not now (brief §13): new custom raids; the 2023 CoX custom-layout / paid-scaling proposal (only the shipped scaled
  party size, Reload and full-layout toggle); TFA Practice Mode and the 9-11 November 2026 TFA balancing; Leagues and
  Deadman variants; raid Combat Achievements (events only); cross-world parties.

## Next

1. Phase 6 (in progress, see docs/plans/phase-6.md).
   - Done:
     - `setupClient`;
     - game-client patcher + test 5 (patch half);
     - RuneLite fork (12 patches): built from source, starts, downloads the cache from our JS5 and reaches the game's
       terms screen on World 1 under Xvfb;
     - signed-update launcher (tamper/offline tested);
     - jlink runtime (Java 11) + jpackage `.deb`/`.tar.gz` built here; Windows/macOS packaging, signing hooks and the
       release workflow are written but not run.
   - Needs the user:
     - branding;
     - code-signing certificates and Apple notarization credentials;
     - production URLs and manifest key;
     - clean-machine install/update/login/hop test and a traffic capture.
2. Record the 4,000-player run's numbers in docs/LOAD-TEST.md (tick percentiles, missed deadlines, GC p99.9).
3. Phase 7 order (the user's list):
   1. ~~Wilderness bosses and revenants~~ done;
   2. ~~Duel Arena~~ done;
   3. ~~doors, gates, ladders and stairs~~;
   4. ~~teleport leftovers~~ (Tele Group, Arceuus and Teleport to Target move to full magic);
   5. drop tables (agent);
   6. ~~trade~~;
   7. shops (agent);
   8. ~~dialogue system~~ (NPC conversation content still to import);
   9. ~~item-on-item, item-on-loc and item-on-NPC~~;
   10. ~~equip requirements and examine texts~~;
   11. non-combat skills;
   12. full magic;
   13. gravestones;
   14. clans, quests, diaries (tasks, tiers, rewards, lamps and the kill tasks and 88 trigger-table tasks done; the other 365 tasks wait for their content, docs/mechanics/achievement-diaries.md), collection log;
   15. generic dragonfire, ammo recovery, multi-target specials;
   16. bank leftovers, destroy confirmation (OPEN-DS1), venom from magic (OPEN-PO3).

## Open questions

- collision.md OPEN-1..4 (terrain extension on square 64,73; group 25287 and square files 2–4; osrs-docs check;
  wall route blockers).
- D-012 undecoded config groups; gameval groups 16–19 unnamed.
- Interface IF3 model-size bytes (ComponentTypeDecoder KDoc).

- D-007: confirm the RuneLite 1.13.6 bootstrap against a live RSProx session in Phase 2.
