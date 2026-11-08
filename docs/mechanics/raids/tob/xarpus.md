# Xarpus (room 5)

Date anchor 2026-11-03; sources read 2026-11-08: OSRS Wiki "Xarpus" (Page), "Exhumed", "Dawnbringer", "Theatre of
Blood/Strategies" (Strat), "Theatre of Blood/Entry Mode" (Entry), "Theatre of Blood/Hard Mode" (Hard); cache 2757
(`cache-dump config npc <id>`; stats array order attack, defence, strength, hitpoints, ranged, magic; gamevals
`TOB_XARPUS_*`). Raid-wide rules: [../tob.md](../tob.md). Tick timelines below list only what a source states;
everything else is `OPEN-XA<n>`.

## Data

- NPC ids per phase (wiki; cache names `TOB_XARPUS_STATIC` / `_FEEDING` / `_COMBAT` / `XARPUS_DEATH`):

| Mode | Phase 1 (static, feeding) | Phase 2 and 3 (combat) | Death |
|---|---|---|---|
| Entry | 10766, 10767 | 10768 | 10769 |
| Normal | 8338, 8339 | 8340 | 8341 |
| Hard | 10770, 10771 | 10772 | 10773 |

- Combat level **331 / 960 / 1160**; size 5 in the combat form (the cache has size 3 on the phase 1 NPCs); attack speed
  **4**; aggressive; poisonous; attack style typeless ranged plus poison (protection prayers do nothing, Entry page).
  Max hit shown: Entry **6 (38+ with recoil)**, Normal and Hard **11 (50+ with recoil)**. Poison and venom resistance 100.
- HP: Normal **5,000**, Hard **6,000** (5 players). By party size: Normal 1-3 players 3,750, 4 players 4,375, 5 players
  5,000; Hard 4,500 / 5,250 / 6,000. Entry: the wiki infobox says **3,400**, but the cache config (combat form 10768)
  holds **520** (the 1-player base: x4 at five players would be 2,080); see conflicts (**OPEN-XA2**).
- Stats: Normal att 1, str 1, def **250**, magic 220, ranged 100; Hard def **200** (else as Normal); Entry def 100, magic
  80, ranged 50. Ranged defence bonuses (light / standard / heavy) 160 (Entry 100); all other bonuses 0. Weak to air /
  wind spells by **50%** (25 Jun 2025); can be burned (24 Jul 2024). The cache stats arrays agree (Normal
  [1, 250, 1, 5000, 100, 220], Hard def 200, Entry [1, 100, 1, 520, 50, 80]).
- **Exhumed** (scenery 32743, "Exhumed", examine "Nourishment for a famished yarasa."): the sources of healing in phase 1.
- Drops: book "The Wild Hunt" (always unless read; obj `TOB_BOOK_XARPUS` 22512) and, after the kill, the Dawnbringer is
  picked up from the skeleton by the door to Verzik (XA6).

## Fight

- **XA1 Phase 1 "Recovery":** Xarpus stands in the middle with **75% of his HP** (the Page: "the remaining 25% of his
  missing health" is restored by absorbing) and does not attack. Exhumed rise from the ground across the arena; each
  fires an energy orb (spot anim `TOB_XARPUS_EXHUMED_ENERGYORB` 1550) at him that **heals him a percentage of his HP**.
  A player **standing on the exhumed's tile** stops that one. Missed absorbs raise his stats and the poison and counter
  damage (XA2, XA3). Counts: Strat says "12 exhumed in trios, 9 in 4-man and 6 in 5-man scales" and, a paragraph later,
  "After 15 exhumed have been exhausted (12 in trio), Xarpus will initiate the next phase": **conflict** (**OPEN-XA1**).
  2 May 2019: phase 1 shortened and scaled for small teams, each absorption more powerful; 16 May 2019: phase 1
  difficulty reduced for solo-trio.
- **XA2 Phase 2 "Poison":** begins once enough exhumed have been spawned. He spits acid at players, targeting by orb
  order (Strat), at the player's current location. The splash covers a **3 x 3** around the target and poisons anyone in
  it (starting at 2 damage); the centre tile stays covered **for the rest of the fight** and each tick a player stands on
  or runs over it deals **4-8 base** damage (Page, from Mod Kieren 10 May 2019; Strat "up to 8"), further raised by a
  percentage that depends on how many absorbs were missed. Skipping over a tile deals a **delayed** hit. The first player
  in sequence splashes onto the next player in sequence; the rest onto the next two (Strat); if every player stands on
  one tile, or only one is present, the splash goes to a random uncovered tile. Standing **under** Xarpus: light damage
  each tick (pebbles, up to 9 per tick, Strat), interrupting his poison attack.
- **XA3 Phase 3 "Counter":** when he drops to about **25%** he screeches once, stops the poison, and stares at one of the
  four quadrants (corners). He **rotates every 8 ticks** to another corner and never looks at the same corner twice in a
  row (Strat). A player who **attacks him from the quadrant he is looking at** triggers an unblockable poison hit of
  **50-75 per hitsplat** (Page; Strat 50-80), so multi-hit weapons are fatal. The base is raised by 40% of the relative
  increase to his stats from the absorbs (Mod Ash 25 Sep 2021). Message "Xarpus is staring in your direction and retaliates
  with a powerful attack!" (quoted from Mod Ash, exact in-game wording **OPEN-XA3**). Entry: threshold "below 22.5%" per
  the Entry page (**conflict**), no time limit, retaliation "high-hitting".
- **XA4 Melee:** his stab, slash and crush defence bonuses are 0 (Strat: "no melee defence", ranged +160). Melee players
  use Redemption against the low poison hits; the melee tiles are the ring next to him, which must not be splashed (Strat),
  and standing under him triggers the pebble attack (XA2).
- **XA5 Kill:** he drops the book; boss kill => restores (TB9).
- **XA6 Dawnbringer:** after Xarpus dies a player takes the **Dawnbringer** (obj `VERZIK_SPECIAL_WEAPON` 22516) from the
  skeleton beside the door to the final room, with the message "You find the Dawnbringer; you feel a pulse of energy burst
  through it." It is a powered staff whose special "Pulsate" deals 75-150 magic damage to Verzik's shield and costs 35%
  special energy, ignoring accuracy. It crumbles to dust when the shield breaks ("The weapon falls apart in your hand as
  Verzik's shield is destroyed."), is destroyed if taken out of Verzik's room, and drops to the floor if its holder uses an
  escape crystal (3 Mar 2021). Its normal max hit (Dawnbringer page): floor((floor(Magic / 3) - 2) / 2), minimum 2.

## Per-mode differences

- **Entry:** HP see above; max hit 6 (38+ recoil); phase 3 at "22.5%"; no time limit in phase 3.
- **Normal:** as above.
- **Hard:** at the start he splatters poison on the **two outermost layers** of the arena except the **six tiles (two rows
  of three) at the entrance**, leaving a 3-tile radius around Xarpus clear (Page: "barring the six tiles"; Strat: a
  three-tile radius around him untouched), so the exhumed cluster and the fight starts in a smaller space; his Defence
  is **200** instead of 250; in phase 3 he **immediately looks at the quadrant he was hit from**, overriding the 8-tick
  rotation (Page, Hard page). Phase 2 is as Normal. Hard tile positions and which tiles: **OPEN-XA6**.

## Tick timeline (stated parts)

- Attack speed 4 ticks. Phase 3 quadrant rotation every 8 ticks (never the same corner twice in a row).
- Exhumed absorb timing and the delay between spawns: **not stated** (**OPEN-XA4**).

## Changes up to 2026-11-03 that matter

3 Jun 2021 modes added; 2 May 2019 and 16 May 2019 phase 1 changes and the small-team damage bug fix; 24 Jul 2024 burn; 25 Jun
2025 air weakness; 3 Mar 2021 Dawnbringer drop on crystal use. No Xarpus entry after 25 Jun 2025 on the Page.

## As implemented (content/combat TobXarpus.kt)

Mode from the NPC id (`TobModes.of`). Entry damage = Normal / 2 (floor) where noted. Arena = offsets -5..7 x -6..8 from his
spawn tile, level 1; "raiders" = living players there. One per-tick loop; it keeps the last 3 positions of every raider.

- **Phase 0 (asleep):** no damage taken (`incoming` returns 0 for phases 0 and 1). The first tick a raider is in the arena starts
  phase 1.
- **Phase 1 (XA1, OPEN-XA1/XA4):** `transform(type + 1)` (static -> feeding), HP set to **75%** of max. With n = raiders in the arena at
  that tick (1..5): total exhumeds Normal `7 / 9 / 12 / 15 / 18`, Hard `9 / 13 / 16 / 20 / 24`; a new one every `12 / 8 / 8 / 4 /
  4` ticks; the first at tick + **8 + random(4)** (8..11). Party size of 5 uses the 4-player gap and heal (OPEN-XA1/XA4).
  - Each exhumed: random tile in the arena (rerolled while under his footprint, with 1/20 chance to keep an under-him tile), scenery
    32743 for **11** ticks (Hard **9**); first orb at its tick + **3**; from then every tick it is not covered by a raider
    standing on its tile (`coord ==`), it fires an orb: graphic 1550, counter `missed++`, and a flat heal of `20 / 16 / 12 / 9 /
    9` HP (index = raiders alive in the arena that tick, not the start count) landing `10 x d / 30 + 1` ticks later
    (d = Chebyshev distance from his SW tile; capped at max HP). It is a flat HP amount, not a percentage of max HP (the wiki XA1
    says "a percentage"). Xarpus is not hit by anything in this phase.
  - **End:** all exhumeds spawned and gone, then **8** ticks after the last closed: phase 2 (`transform(type + 1)`, teleport
    (-1, -1) for the larger footprint), HP kept, first spit at tick + **9**.
  - **Hard:** at the start every tile of the two outermost layers of the arena is an acid pool (permanent), except the six entrance
    tiles (rows z offset 7 and 8, x offset -1..1). The 3-tile clear radius around him is not modelled (**OPEN-XA6**).
- **Phase 2 (XA2):** every tick, each raider standing under his footprint gets a guano graphic and **two** typeless hits of `3..7` (Entry
  halved) landing +1, every tick they stay (no interruption of the spit; **OPEN-XA10**). Spit every **4** ticks while raiders exist: target
  = the next raider in orb order (`RAID_ORDER`, unlisted names last), cycling by a spit counter; aimed at the tile that raider stood
  on 2 ticks ago (oldest of the last 3 positions); anim 8059, projectile 1550-series acid spit (height 90), flight `30 + 30 x
  max(1, d)` cycles, lands `(30 + 30d) / 30` ticks later. On landing, a **3 x 3** splash at the tile: every raider within 1 gets
  poison with starting damage **2**, and the centre tile becomes a permanent acid pool (scenery). Then it splashes on to the
  raiders after the target in orb order (no wrap-around): 1 if the target was first in order, else 2 (fewer when the order runs
  out), each at that raider's locked tile one tick later (skipped if the same tile as the first). Only with a single raider in
  the arena does it add one splash on a random arena tile. Not modelled: "everyone on one tile" random splash.
  - **Pools:** each tick a raider stands on a pool tile (any pool, Hard outer ones too) takes typeless `4..8 x (100 + missed) / 100`
    (floor, i.e. +1% per orb fired; **OPEN-XA4**), Entry halved after scaling, immediate (no delayed-hit rule for skipping over).
- **Phase 3 (XA3, OPEN-XA7):** enters from phase 2 when `hp x 1000 / maxHp <= 250` (Entry **225**) and at least **3** ticks since the
  last spit: he says "Screeeeech!", every raider gets "Xarpus begins to stare intently.", quadrant = random of 4 (NW/NE/SW/SE
  by the raider being at-or-east and at-or-north of his centre tile `coord + size / 2`), first turn at tick + **9**, then every
  **8** ticks to a different random quadrant (never the same twice). Spits stop; pools and guano continue. **Hard:** never rotates.
  - **Retaliation:** every player hit received while in phase 3 from a player in the stared quadrant (Hard: the quadrant is first
    set to the attacker's, so every hit) sends "Xarpus is staring in your direction and retaliates with a powerful attack!" and an
    immediate typeless `50..75 x (100 + missed x 40 / 100) / 100` (integer maths, so +0.4% per orb; **OPEN-XA3** wording and
    scaling); Entry halved. The hit on Xarpus itself is unchanged.
- **Death:** plays `TOB_XARPUS_DEATH_A` only; the Dawnbringer (XA6), restores and chest are in raids-tob.

## Open list

OPEN-XA1 exhumed counts per party size and what ends phase 1; OPEN-XA2 Entry HP (3,400 vs 520 base) and rounding for 4 / 3
players; OPEN-XA3 exact retaliation message and the damage formula (base 50-75 vs 50-80, scaling); OPEN-XA4 exhumed spawn
rate, tile choice, lifetime, heal percentage per absorb and the stat-buff percentage; OPEN-XA5 poison base damage (4-8
vs "up to 8"), the percentage scaling, the delayed-hit rule and splash targeting for fewer than three players; OPEN-XA6 Hard
poison layout; OPEN-XA7 phase 2 -> 3 threshold (25% vs 22.5%) and whether it is exact; OPEN-XA8 when the first quadrant is
chosen and how the next is picked (random assumed); OPEN-XA9 Entry damage numbers other than the max hit; OPEN-XA10 pebble
(stomp) trigger range and damage. **10 open items.**

## Conflicts between sources

1. Exhumed counts: 12 / 9 / 6 (Strat paragraph 1) vs 15 (12 in trio) (Strat paragraph 2).
2. Phase 3 threshold: 25% (Page, Strat) vs below 22.5% (Entry page).
3. Retaliation damage: 50-75 (Page) vs 50-80 (Strat).
4. Entry HP: 3,400 (Page infobox) vs cache base 520 (x4.0 at five players = 2,080; x5 = 2,600). 3,400 matches neither, and no
   source explains it; unresolved (**OPEN-XA2**).
5. Poison tile damage: 4-8 base (Page) vs "up to 8" (Strat).
