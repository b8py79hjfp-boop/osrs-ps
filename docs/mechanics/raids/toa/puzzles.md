# Tombs of Amascut puzzle rooms (Apmeken, Crondis, Het, Scabaras)

Date anchor 2026-11-03; sources read 2026-11-08. Wiki (OSRS Wiki, local copies): "Tombs of Amascut" (Main), "Tombs of
Amascut/Strategies" (Strat), "Path of Apmeken" / "Path of Crondis" / "Path of Het" / "Path of Scabaras" (PA / PC / PH / PS),
"Baboon Brawler" / "Baboon Thrower" / "Baboon Mage" / "Baboon Shaman" / "Baboon Thrall" / "Volatile Baboon" / "Cursed Baboon"
(Brawler / Thrower / Mage / Shaman / Thrall / Volatile / Cursed), "Baboon" (Ba-Ba's baboon, out of scope), "Neutralising
potion" (Potion), "Banana peel" (Peel), "Crocodile (Tombs of Amascut)" (Croc), "Palm of Resourcefulness" (Palm), "Water
container (Tombs of Amascut)" (Container), "Waterfall (Tombs of Amascut)" (Fall), "Het's Seal" (Seal), "Orb of Darkness"
(Orb), "Mirror (Tombs of Amascut)" (Mirror), "Statue (Tombs of Amascut)" (Statue), "Caster statue" (Caster), "Shielded
statue" (Shielded), "Scarab (Tombs of Amascut)" (Scarab), "Scarab Swarm (Tombs of Amascut)" (Swarm), "Soldier Scarab" /
"Spitting Scarab" / "Arcane Scarab" / "Agile Scarab" (Soldier / Spitting / Arcane / Agile; Kephri's fight, see SC11), "Perfect
Apmeken" / "Perfect Crondis" / "Perfect Scabaras" / "Perfect Het" / "Insanity" (CA pages). RuneLite plugins, facts only, no
code copied: `tombs-of-amascut` by LlemonDuck (LD), `toa-mistake-tracker` (MT), `toa-points-tracker` (PT), `toa-puzzle-helper`
(PHp; only region ids, Ba-Ba wave and Kephri-light images), `toa-modern` and `toa-supplies` (read, no puzzle content), `tombs-of-
amascut-stats` (messages only). Cache 2757 (`cache-dump config npc|loc|obj <id>`; NPC `stats` array order is
`[attack, defence, strength, hitpoints, ranged, magic]`; `params` 5 / 6 / 7 / 8 = stab / slash / crush / magic defence bonus,
14 = attack speed in ticks, 26 = attack style, which agree with the wiki infobox on every NPC checked); generated symbols
(`Npcs`, `Locs`, `Objs`, `Seqs`, `SpotAnims`, `Varbits`, `Varps`). Raid-wide rules (party, invocations, raid level maths,
points totals, chest, death): the ToA raid-wide doc `../toa.md` (not part of this set) and `../common.md`. Seconds are
converted at 0.6 s per tick. Tick timelines list only what a source states; everything unstated is `OPEN-PZ<n>`. Source tags in
brackets: (Main), (Strat), (Brawler) ..., (LD), (MT), (PT), (cache), (symbols), (derived) = arithmetic on stated numbers.

Scope: the four challenge ("puzzle") rooms. A **path** is a challenge room followed by its boss: Apmeken -> Ba-Ba, Crondis ->
Zebak, Het -> Akkha, Scabaras -> Kephri (Main). The bosses are not covered here. Two corrections to the request: (1) the
Path of Scabaras has **five** puzzles, not four (Strat: "There are five puzzles to complete between two branched paths"); (2)
"Insanity" is a **Wardens** invocation and a Grandmaster Combat Achievement (CA page, Strat), not a puzzle-room effect, and the
spitting / arcane / soldier scarabs are **Kephri's fight** NPCs (Soldier / Spitting / Arcane pages: "summoned by Kephri");
they are recorded in the Data section and SC11 so nothing is lost, but they never appear in the Scabaras puzzle room.

## Data

### Room and raid ids

- Template region ids used by RuneLite (LD `RaidRoom`, PT `ToARegion`, PHp): Nexus **14160**, lobby **13454**, tomb (chest)
  **14672**; Apmeken puzzle **15186**, Ba-Ba **15188**; Crondis puzzle **15698**, Zebak **15700**; Het puzzle **14674**,
  Akkha **14676**; Scabaras puzzle **14162**, Kephri **14164**; Wardens **15184** / **15696**; PHp also lists 15444, 15955, 13906, 15954 and 15953 as "ToA instance ids" (rooms not named). RuneLite recognises the room from
  the region of the player's instance template, so the instance must be built from those template regions.
- Nexus doors (loc, cache names agree with the wiki "Normal / Started / Completed" ids; all 3x3 or 3x2): Scabaras
  **46155** (`TOA_NEXUS_SCABARAS_DOOR`, ops Enter / Quick-Enter) / **46156** (`_UNSELECTED`, same ops; another path started,
  head removed) / **46157** (`_CLOSED`, no ops; completed, fire gone); Apmeken **46158** / **46159** / **46160**; Crondis
  **46161** / **46162** / **46163**; Het **46164** / **46165** / **46166**. Wiki examines: "Walk the Path of X?" / "Another
  path has been started." / "The Path of X has been completed." (PA, PC, PH, PS).
- Room end ("continue") locs, name "Entry", ops Enter / Quick-Enter (LD `QuickProceedSwaps`): Het **45131**
  `TOA_DOOR_CONTINUE` (1x5), Scabaras **45337** `TOA_SCABARAS_CONTINUE` (1x3), Crondis **45397** `TOA_PATH_CRONDIS_CONTINUE`
  (2x3), Apmeken **45500** `TOA_PATH_APMEKEN_CONTINUE` (1x3). Flame "Barrier" **45135** `TOA_PATH_BARRIER` (1x1, ops Pass /
  Quick-Pass, seq 9495), shared by the four rooms (LD: it is the Crondis room's entry gate, and in Scabaras two copies mark the
  two halves). Crondis `TOA_CRONDIS_EXIT` **45453** (name "Exit", op Leave, 5x2) and `_EXIT_TOP` **45454**. Other door symbols
  (`TOA_DOOR_EXIT` 45128, `_EXIT_WARDENS` 45129, `_EXIT_SMALL` 45130, `_TOP` 45132, `_TOP02` 45133, `_TOP02_NEXUS` 45134): the
  placements below show `TOA_DOOR_EXIT` 45128 as the plain door at the entry side of the Apmeken, Het and Scabaras rooms; the others
  are **OPEN-PZ1**.
- Flame barrier 45135 and door placements per room (cache map, `find-locs`; region squares Scabaras (55, 82), Het (57, 82), Apmeken
  (59, 82), Crondis (61, 82); sides are derived from the coordinates, see PC7): Apmeken barriers at x 3796 and 3820 (z 5279-5281,
  3 tiles each), door 45128 at (3791, 5278), continue 45500 at (3825, 5279); Het barriers x 3669 and 3691 (z 5279-5281), door 45128
  at (3699, 5278), continue 45131 at (3659, 5278); Crondis barriers x 3922 and 3950 (z 5279-5281), exit loc 45453 at (3955, 5278),
  continue 45397 at (3916, 5279); Scabaras two single barriers at (3532, 5274) and (3532, 5286), door 45128 at (3521, 5278),
  continue 45337 at (3582, 5279).
- Client / HUD state (symbols): varbits `TOA_CLIENT_CRONDIS_LEVEL` **14376**, `_SCABARAS_LEVEL` **14377**, `_HET_LEVEL`
  **14378**, `_APMEKEN_LEVEL` **14379**, `TOA_CLIENT_RAID_LEVEL` **14380**, `TOA_CLIENT_CURRENT_PATH` **14381**,
  `TOA_CLIENT_P0..P7` **14346-14353**, `TOA_DAMAGE_DONE` 14325, `TOA_DAMAGE_TAKEN` 14323, `TOA_PICKAXE_STORED` **14440**,
  `TOA_PICKAXE_CHARGES` **14439**; varps `TOA_PERSONAL_CONTRIBUTION` **3606**, `TOA_PICKAXE_STORAGE` **3635**. LD reads the
  path level of the room's path from interface `TOA_HUD` (`PATHS_LEVEL` text, `NEXUS_*_LEVEL` texts in the Nexus), so the HUD
  must show the level inside the puzzle room as well as the boss room (LD `PathLevelTracker`).
- Chat messages the plugins match: "Challenge started: Path of Het." and "Challenge complete: Path of Het." (LD `BeamTimerTracker`
  only; MT matches none of them); the
  completion line is `Challenge complete: Path of <Name>.` followed by `Duration` and `Total` times (LD `SplitsTracker` regex
  `Challenge complete: (?:Path of )?([A-Za-z-]+).*Total:.*?([0-9]+:[.0-9]+).*`); failure "Your party failed to complete the
  challenge" (LD and MT; LD resets every solver and wave counter on it, MT uses it as the wipe signal); "violently!" suffix (LD `HetSolver`; used to clear the
  Het solver, the full line is not shown); "You hear a mysterious rumbling coming from the Path of <god>." when Walk the Path
  levels an uncompleted path (Strat).

### Path of Apmeken

- NPCs (cache names; **ids agree with the wiki**; size 1; op2 "Attack"; walk seq 9739, stand seq 9741 = `NPC_MANDRILL_*`):

  | Id | Cache name | Wiki name | Level | HP | Att / Def / Str / Rng / Mag | Style | Speed | Max hit | Defence bonus stab / slash / crush / magic (cache) |
  |---|---|---|---|---|---|---|---|---|---|
  | 11709 | `..BABOON_MELEE_1` | Baboon Brawler | 56 | 25 | 40 / 12 / 40 / 40 / 40 | crush | 4 | 5 | 900 / 900 / 900 / -60 |
  | 11712 | `..MELEE_2` | Baboon Brawler | 68 | 30 | 60 / 20 / 60 / 60 / 60 | crush | 4 | 7 | 900 / 900 / 900 / -60 |
  | 11710 | `..RANGED_1` | Baboon Thrower | 56 | 30 | 40 / 12 / 40 / 40 / 40 | ranged | 4 | 5 | -50 / -50 / -50 / 900 |
  | 11713 | `..RANGED_2` | Baboon Thrower | 68 | 35 | 60 / 20 / 60 / 60 / 60 | ranged | 4 | 7 | -50 / -50 / -50 / 900 |
  | 11711 | `..MAGIC_1` | Baboon Mage | 56 | 20 | 40 / 12 / 40 / 40 / 40 | magic | 4 | 5 | 900 / 900 / 900 / 900 |
  | 11714 | `..MAGIC_2` | Baboon Mage | 68 | 25 | 60 / 20 / 60 / 60 / 60 | magic | 4 | 7 | 900 / 900 / 900 / 900 |
  | 11715 | `..SHAMAN` | Baboon Shaman | 74 | 40 | 60 / 20 / 60 / 60 / 60 | magic | **8** | 7 | 900 / 900 / 900 / 900 |
  | 11716 | `..ZOMBIE` | Volatile Baboon | 72 | 8 | 60 / 20 / 60 / 60 / 60 | none | 4 | 7 (infobox) | 900 / 900 / 900 / -60 |
  | 11717 | `..CURSED` | Cursed Baboon | **70** (wiki 77) | 20 | 60 / 20 / 60 / 60 / 60 | none | 4 | 7 (infobox) | 900 / 900 / 900 / -60 |
  | 11718 | `..THRALL` | Baboon Thrall | 52 | **2** | 40 / 12 / 40 / 40 / 40 | crush | 4 | 5 | 0 / 0 / 0 / 0 |

  Cache `param 26` values 2 / 4 / 5 match crush / ranged / magic on every baboon. The ranged defence bonus is not in the cache
  params, so the infobox values are used (all seven infoboxes state it): ranged defence Brawler **900**, Thrower **900**, Mage
  **-50**, Shaman **-50**, Volatile **-50**, Cursed **-50**, Thrall **0**. Attack bonus 20 (level 56 and thrall) / 25 (level 68, shaman,
  volatile, cursed) for melee, magic and ranged (infoboxes). Poison and venom: Shaman resistance **0** (vulnerable), every other
  baboon 100 (infoboxes); Cursed and Volatile pages: Cursed immune to thralls (`immunethrall = Yes`). All immune to cannons.
  Level 56 / 68 are the "small" / "large" variants (Strat table: "25 (small) / 30 (large)"); which variant spawns when is
  **OPEN-PZ3**.
- Animations (symbols, names only; which one belongs to which baboon is not stated, **OPEN-PZ4**): `NPC_MANDRILL_ATTACK_MELEE`
  9742, `_RANGED_01` 9744, `_RANGED_02` 9745, `_MAGIC` 9746, `_MAGIC_02` 9747, `_SPECIAL_JUMP01` 9748, `_SPECIAL_RANGED01`
  9749, `_SPECIAL_IDLE01` 9750, `_SPECIAL_END01` 9751, `_SPAWN01` 9752, `_SPAWN02` 9753, `_DESPAWN01` 9754, `_DESPAWN02` 9755,
  `NPC_MANDRILL_EXPLODE` 9756, seq 9757 (also named `SPOTANIM_MANDRILL_EXPLODE`); the spot anim of that name is **2249**
  (baba.md lists it for the Volatile baboon). Projectiles: `TOA_BABOON_RANGED_TRAVEL_1 / _2`
  **2242 / 2243**, `TOA_BABOON_MAGIC_TRAVEL_1 / _2` **2247 / 2248**.
- Locs: `TOA_PATH_APMEKEN_PILLAR` **45494** ("Roof support", 3x3, op1 "Repair", idle seq 9503 `TOA_PILLAR_IDLE`),
  `_PILLAR_SHAKING` **45495** (same name and op, seq 9502 `TOA_PILLAR_SHAKE`), `_PILLAR_NO_REPAIR` **45496** (name "Statue",
  no ops), `_HAMMERS` **45497** (op "Take"), `_POTIONS` **45498** (op "Take"), `_VENT` **45499** (name null, 1x1),
  `_VENOM_POOL` **45493** ("Venom pool", no ops), `_TRANSITION` **45501** / `_TRANSITION_M` **45502** (2x2, seq 9497, the
  wave-gate; role not stated, **OPEN-PZ4**), `_CONTINUE` **45500**.
- Items: hammer `HAMMER` **2347**; `TOA_PATH_APMEKEN_POTION` **27297** "Neutralising potion" (stackable, op "Pour", value 1,
  untradeable; Potion).
- Spot anims (symbols; all LD / MT use them): `TOA_PATH_APMEKEN_GAIN_SIGHT` **2132**, `_LOSE_SIGHT` **2133**,
  `_SIGHT_WARNING` **2134**, `_SIGHT_WARNING02` **2135**, `_SIGHT_FLOOR` **2136**, `_GAIN_CURSED` **2137** (the corruption
  mark), `_GAS` **2138** (the vent-failure graphic; MT `VENT_GRAPHICS_ID`), `_RUBBLE` **2139** (the pillar-failure graphic; MT
  `PILLAR_GRAPHICS_ID`). The volatile explosion graphic MT matches is spot anim **131** (symbol `FIREBLAST_IMPACT`, reused), at
  the centre of the 3x3, while the cache has the dedicated **2249** (conflict 13, **OPEN-PZ16**). What 2134 / 2135 / 2136 mark exactly is not stated (**OPEN-PZ4**).
- Messages (MT / Strat): "You sense an issue somewhere in the room." (Sight prompt), "The fumes filling the room suddenly
  ignite!" (vent failure), "Damaged roof supports cause some debris to fall on you!" (pillar failure), "Your group is
  overwhelmed by Amascut's corruption!" (corruption failure).
- Wave spawn tiles (LD `ApmekenWaveOverlay`, region-local x, y): (24, 40), (32, 40), (40, 40), (24, 24), (32, 24), (40, 24);
  LD advances its wave counter on the tick an NPC first spawns on one of them.

### Path of Crondis

- NPCs: Palm of Resourcefulness `TOA_CRONDIS_TREE_1..5` **11700-11704** (size 5, name "Palm of Resourcefulness", op1 "Water" on
  1-4, none on 5 = fully grown; wiki stage labels 0% / 25% / 50% / 75% / 100% and examines "needs a lot more water" (0 and
  25%), "needs more water" (50%), "needs a bit more water" (75%), "A healthy and nourished palm." (100%)). Crocodile
  `TOA_CRONDIS_CROCODILE` **11705** (size 2, level 71, op2 "Attack", stand `CROC_READY` 2037, walk `CROC_WALK` 2036, attack
  `CROC_ATTACK` 2039, parry 2035, death 2038, merge 9804): cache `stats` [150, 100, 60, 30, 1, 30] = attack 150, defence 100,
  strength 60, **HP 30**, ranged 1, magic 30; params 5-8 = 150 / 350 / 350 / 5, 10 (melee strength bonus) = 100, 14 = **7**
  (attack speed), 26 = 2 (crush); all agree with Croc. Crush, **max hit 18**, not aggressive in the usual sense (it picks
  targets by CR4), poison and venom immune, elemental weakness **earth 40%** (25 Jun 2025), cannon immune.
- Locs: waterfall `TOA_CRONDIS_WATER_SOURCE` **45398** (name "Waterfall", 7x5, op1 "Fill") and `_EMPTY` **45399** ("Waterfall
  (dry)", same op), floors `TOA_CRONDIS_WATERFALL_FLOOR_01/_01_MIRROR/_02` 45400 / 45401 / 45402; traps `TOA_CRONDIS_COLUMN_TRAP`
  **45414** ("Statue", 1x2, seq 9562 `CRONDIS_SPEAR_TRAP_IDLE`), `_ROW_TRAP` **45415** ("Statue", 2x1), `_ROW_TRAP_FIRE` **45416**
  ("Spear", 3x1), acid `TOA_CRONDIS_ORB` **45403** ("Acid Orb", seq 9520 `TOA_LIFE_LEACH`), poison tiles `_POISON_TILE`
  **45418**, `_RECOL01` **45419**, `_RECOL02` **45420** (the dark and light clouds; which is which is not stated, **OPEN-PZ5**),
  `_INACTIVE` **45421** ("Vines"), edges 45422-45425; decor `_STATUE01_3X3` 45206, bushes 45404-45413, `_DIRTPATCH` 45430,
  patches 45437-45444.
- Items: `TOA_CRONDIS_WATER_CONTAINER` **27295** (wiki ops Check, Empty, Destroy; value 100; weight 2 kg; Container, cache agrees on
  value and weight).
- Player animation LD / MT match for "emptying a container": **827** (symbol `HUMAN_PICKUPFLOOR`; MT `PLAYER_WATER_ANIMATION_ID`).
  Hitsplat types on the palm: **11** (water up) and **15** (water down) (MT constants `WATER_HITSPLAT_UP_ID`, `_DOWN_ID`); a
  palm "up" hitsplat below 100 means the container was not full (MT).
- Tile sets MT uses, region-local to room 15698: waterfall interaction tiles x = 23, 24, 26, 27 and 37, 38, 40, 41 at y = 7
  (south) and y = 57 (north), i.e. **four waterfalls**, one per corner (wiki pins agree: 3926 / 3940, 5250 / 5306); palm
  interaction ring: the 5-tile sides at y = 29 and y = 35 (x 30-34) and x = 29 and x = 35 (y 30-34), so the palm occupies
  x 30-34, y 30-34 (5x5).

### Path of Het

- NPCs: Het's Seal protected `TOA_HET_GOAL` **11706** and weakened `TOA_HET_GOAL_VULNERABLE` **11707** (size 3, op1 "Destroy",
  names "Het's Seal (protected)" / "(weakened)", examines "It looks to be glimmering slightly." / "The cracks are starting to
  show."); cache hitpoints stat of 11706 = **119** (agrees with Seal, 20 May 2026) and 11707 = 1 (presumably the HP carries over at
  runtime; not stated). Orb of Darkness `TOA_HET_ORB` **11708** (size 1, stand `SPECTER_GOO_TRAVEL_LOOP` 8652, walk
  `TOA_HET_ORB_WALK01` **9542**; the Akkha variant 11801 is out of scope). Broken seal locs `TOA_HET_GOAL_DEAD` **45489**
  (3x3), `TOA_HET_GOAL_BOX` 45488, `TOA_HET_GOAL` 45490 (3x3), wiki "Broken seal" ids 45489 and 45491.
- Statues: caster `TOA_HET_SOURCE` **45486** (3x3, "Caster statue"; LD `BeamTimerTracker` uses it as the beam origin), shielded
  `TOA_HET_TARGET` **45485** (3x3, "Shielded statue"; LD `HetSolver` uses its position as the puzzle origin).
- Mirrors: item `TOA_HET_MIRROR` **27296** ("Mirror", ops Place / Drop, value 100, weight 1 kg); locs `TOA_HET_MIRROR` **45455**
  (movable placed mirror: ops Pick-up, Rotate-clockwise, Rotate-anticlockwise, Push), `_MIRROR_FIXED` **45456** (no ops),
  `_MIRROR_DIRTY` **45457** ("Mirror (dirty)", op "Clean"), `_MIRROR_DESTROYED` **45467**. Orientation of a mirror object is
  config bits 6-7 (`(config & 0b11000000) >> 6`: 0 NE, 1 SE, 2 SW, 3 NW; LD). Null object **29733** is placed to clear a tile
  (mirror picked up, wall gone; LD).
- Barriers: `TOA_HET_BARRIER_INTERNAL` **45458** and `_END` **45460** (indestructible, seq 9517 `TOA_WALL_STATIC`),
  `_DESTRUCTABLE_INTERNAL` **45462** and `_DESTRUCTABLE_END` **45464** (op1 "Break", the "worn" walls), the `_RISING` variants
  45459 / 45461 / 45463 / 45465 (seq 9516 `TOA_WALL_RAISING`, the wall coming up at puzzle start), `_BARRIER_DESTROYED` **45466**
  (rubble left), `_BARRIER_MOUND` 45487 (name "Nothing"), `_RUBBLE` 45491, `_PILLAR01` 45492.
- Pickaxe statue: parent `TOA_HET_STATUE_PARENT` **45468** (3x3, ops Take-pickaxe / Deposit-pickaxe, multi-loc driven by varbit
  `TOA_PICKAXE_STORED` **14440**, 24 values): value 0 -> 45470 bronze (the free pickaxe), 1 iron 45471, 2 steel 45472, 3 black
  45473, 4 mithril 45474, 5 adamant 45475, 6 rune 45476, 7 gilded 45477, 8 dragon 45478, 9 dragon (upgraded) 45480, 10 dragon (or)
  45479, 11-12 infernal 45482 (two values: charged / uncharged), 13 3rd age 45481, 14-15 crystal 45483, 16 `TOA_HET_STATUE_PICKAXE_
  LEAGUES` 55808 (Echo), 17-19 trailblazer 45484, 20-22 trailblazer reloaded 55809, 23 -> 45470 (derived from the cache multi-loc
  order). Empty statue 45469. Lobby cavity (same varbit): parent 49566, 49567, pickaxes 49568-49582, 55810,
  55811. Pickaxe items LD accepts as "a pickaxe" include bronze to rune, dragon, dragon (or), Zalcano, infernal (and empty),
  trailblazer variants, crystal (and inactive), 3rd age, gilded (LD `PickaxeUtil`).
- Spot anims: beam graphics `FX_BEAM_ATTACK01` **2064** (vertical segment, LD), `FX_BEAM_ATTACK03` **2114** (horizontal
  segment), `FX_BEAM_IMPACT01` **2120** (crash into an object); LD says "there are others for each corner direction"; the symbol
  names `FX_BEAM_ATTACK05 / 07 / 08` = **2116 / 2118 / 2119** are the candidates (names only, **OPEN-PZ6**). The Orb of Darkness
  hit graphic MT matches is spot anim **379** (symbol `SHADOW_RUSH_IMPACT`, reused), spawned on the tile of the player it hits.
- Tick facts the plugins assume (LD `BeamTimerTracker`): beam period `BEAM_FIRE_RATE_TICKS` = **9**; first fire = **10** ticks
  after "Challenge started: Path of Het."; the timer is cleared (-1) while the seal is weakened (NPC change 11706 -> 11707) and
  restarted at +10 ticks when it changes back (11707 -> 11706).
- Puzzle space (LD `HetSolver`): a 21x21 grid whose origin is the shielded statue's south-west scene tile shifted by (-12, -9);
  the plugin carries **nine** solution layouts (3 or 4 mirror positions each, plus the tiles that must be clear; solutions
  start at puzzle-space (5, 10), (4, 10), (3, 10) or (2, 10) (`SOLUTION_5` starts at (2, 10))).

### Path of Scabaras

- NPCs: puzzle scarab `TOA_SCABARAS_SCARAB` **11697** (name "Scarab", level 56, size 1, op2 "Attack", stand `NPC_SCARAB_IDLE` 9586,
  walk 9584, ranged attack anim `NPC_SCARAB_RANGED_ATTACK` 9588, spawn 9589, death 9590; cache `stats` [20, 28, 32, 12, 50, 1],
  param 4 = 150, params 5-8 = 0, 26 = 4 ranged; infobox: **HP 12**, attack speed **4**, max hit **6**, ranged, aggressive, fire
  weakness **40%** (25 Jun 2025), poison and venom immune, cannon immune). Obelisk `TOA_SCABARAS_GUESSER_OBELISK` **11698**
  (name "Obelisk", size 1, op2 "Hit", cache hitpoints 120) and `_HIT` **11699** (lit, no ops). Projectile / impact spot anims
  `TOA_SCARAB_RANGED_PROJECTILE01 / IMPACT01` **2152 / 2153**, `..02` **2154 / 2155** (kephri.md KP7 gives the same four to the
  Spitting Scarab; which scarab uses them is not stated, **OPEN-PZ31**).
- Locs (all names from the cache): barrier `TOA_SCABARAS_BARRIER` 45335 (null name) and `_BARRIER_ACTIVE` **45336** ("Barrier"),
  `TOA_SCABARAS_CONTINUE` **45337**, `SIMONSAYS_BUTTON` **45338** ("Ancient button", op Push), `SIMONSAYS_TILE_UP` **45340**
  (pressure plate at rest), `_TILE_DOWN` **45341** (plate lit, seq 9496 `TOA_TILE_GLOW_IDLE`), `_TILE_DOWN_PLAYER` **45342** (plate
  stepped on), `TOTALTILES_TABLET` **45339** ("Ancient tablet", op Inspect), `TOTALTILES_TILE1..9` **45345-45353** (the nine
  symbol plates), `LIGHTSOUT_TILE_OFF` **45344** ("Pressure plate", op5 "Flip", unlit), `FX01` **45384** (lit-plate object, seq
  9496), `MEMORYGAME_BUTTON1..9` **45356-45364** ("Tile", op Activate), `MEMORYGAME_TILE1..9` **45365-45373** ("Symbol"),
  matched-pair markers `FX03..FX12` **45386-45395** (LD maps them: FX05 line, FX06 knives, FX03 crook, FX08 diamond, FX09 hand,
  FX04 star, FX10 bird, FX11 wiggle, FX12 boot; FX07 = triangle in the addition puzzle), `FINAL_BUTTON` **45355** ("Ancient
  button"), `SPAWN_HOLE` **45343** ("Passage", op "Crawl-through"), `STEPPING_STONE` **45396** ("Platform", op "Jump-to"),
  `SWARM` 45354, `DIVIDER` 45374, `HINTS01 / 02` **45139 / 45140** ("Strange panels", 1x3; probably the symbol-value panels the
  wiki puts "on the north and south wall near the entrance", inferred from names), `STATUE01_3X3` 45205, `FX02` 45385.
  Symbol order (LD): matching tiles `MEMORYGAME_TILE1..9` = line, knives, crook, diamond, hand, star, bird, wiggle, boot; addition
  plates `TOTALTILES_TILE1..9` = line, knives, triangle, diamond, hand, bird, crook, wiggle, foot (values 1-9, SC6).
- Seqs: `TOA_FLIP_TILE` **9490**, `TOA_TILE_GLOW_IDLE` 9496, `TOA_DUST_FALLING` **9498** (the falling rocks), `TOA_OBELISK01_GLOW`
  9515. Spot anim `MM_ROOFCOLLAPSE` **302** (LD matches it on a plate to detect a sequence failure).
- Layout facts LD assumes (scene tile coordinates, `SkipObeliskOverlay`, `LightPuzzleSolver`, `AdditionPuzzleSolver`): the four
  puzzle slots start at scene (36, 56), (36, 44), (53, 56), (53, 44); the obelisk puzzle sits in one of the four (obelisk NPC
  seen at (36, 57), (53, 57), (36, 45) or (53, 45)); the two barrier flames (loc 45135) are at scene (28, 54) (upper half) and
  (28, 42) (lower half).

### Kephri's scarabs (not in the puzzle room; data kept for reuse)

- `TOA_KEPHRI_SHIELD_SCARAB` **11723** "Scarab Swarm" (level 8, HP 10, size 1, cache stats [1, 0, 1, 10, 1, 1], defence bonuses -100, fire
  weakness 40%, thrall immune, restores Kephri's shield), `TOA_KEPHRI_GUARDIAN_MELEE` **11724** "Soldier Scarab" (size 3, level
  89, HP 40, slash, speed **6** per cache / **7** per infobox (conflict 8), max hit 17, fire weakness 50%), `_GUARDIAN_RANGED`
  **11725** "Spitting Scarab" (ranged, speed **3**, max hit 10, poison starting at 6, ranged defence 125), `_GUARDIAN_MAGE`
  **11726** "Arcane Scarab" (magic, speed 6, max hit 22, red-swarm charge, relocates after `2 + N` hits for N players or on a
  single hit of **39** or more; missed hits count, thrall and burn damage do not; Mod Ash 28 Oct 2024), `_SCARAB_RANGEKITE` **11727**
  "Agile Scarab" (level 53, size 1, HP 30, ranged, speed 4, max hit 5, retreats at running speed).

## Raid level, path level and invocations (apply to all four rooms)

- **PC1 Raid level:** every 5 levels add 2% to enemy hitpoints, defence, accuracy and damage, the damage part capped at +150%
  (`stat = base x (1 + level / 250)`, defence is a modifier on the defence roll, not on the Defence level) (Main, Strat). That
  is the general rule and the puzzle rooms contradict it in several places: baboon HP is static since 29 May 2024 (Brawler,
  Thrower, Mage, Shaman, Thrall, Volatile, Cursed; Strat "same amount of health regardless of raid level"), the crocodile
  "does not scale with raid level" and its damage is fixed (Strat, Croc), and Het's Seal HP "scales with the number of players but
  not with raid level or invocations" (Strat, Seal). The specific statements win. Whether the baboons', crocodile's and scarabs'
  accuracy, damage and defence scale with raid level is **OPEN-PZ7**; the puzzle scarab infobox still says `scaledhp = Yes`
  (conflict 2).
- **PC2 Path level:** Walk the Path levels uncompleted paths up (4 level ups per raid; completing the first path gives levels
  to two other paths, the second to one, the third to the last; random without bias, Mod Ash 8 Nov 2023); Pathseeker /
  Pathfinder / Pathmaster give every path 1 / 2 / 3 levels on entry (Main). The first path level adds +8% hitpoints and damage
  to **most** enemies, each further level +5%, up to six levels; every two levels change the **boss** mechanics, capped at four
  (Main, Strat; 8 Sep 2022 change "+8 / 13 / 18 / 23 / 28 / 33%"). The text lists boss changes only (Akkha, Ba-Ba, Kephri, Zebak);
  it does not say that the puzzle room's NPCs get the +8% / +5%, nor which NPCs "most" excludes (**OPEN-PZ8**). Room and boss
  of a path share one level and one HUD value (LD `PathLevelTracker`). The message on a level-up is in the room data above.
  Which event counts as "completing a path" (puzzle room cleared or boss killed) is **OPEN-PZ8**; Strat's path-order advice
  speaks of bosses.
- **PC3 Invocations that touch the puzzle rooms** (Main, Strat; no invocation targets a puzzle room): Quiet Prayers (protection
  prayers 10% less effective: baboon, scarab and croc hits), Deadly Prayers (prayer drained by 20% of damage taken, self-
  inflicted excluded, so puzzle-trap damage is excluded or not, **OPEN-PZ9**), On a Diet (no food; honey locusts and silk
  dressings not offered), Dehydration (no health-restoring potions), Overly Draining (special attacks cost 100%; the Dragon
  pickaxe special then would need full energy, **OPEN-PZ9**), the attempts group (Try Again 10, Persistence 5, Softcore Run 3,
  Hardcore Run 1: **an attempt is spent when the whole team dies during a challenge**, so a wipe in a puzzle room costs one),
  the time-limit group (Walk for It 40 min, Jog for It 35, Run for It 30, Sprint for It 25 across all rooms). Jungle Japes
  (banana peels, 3 s stun) is a Ba-Ba **fight** invocation (Peel, Baboon, Main): the Apmeken puzzle baboons drop nothing. The
  Kephri invocations (Lively Larvae, More Overlords, Blowing Mud, Medic!, Aerial Assault) are boss-only.
- **PC4 Wipe, restart, disconnect:** after a wipe with attempts left the team **restarts the room it died in**, keeping earlier
  progress (Main, Strat); a wipe on the very tick the challenge completes is not an attempt (Main). After a team wipe with
  attempts left the players are given honey locusts unless On a Diet is active (Main). The **Het** puzzle is re-drafted: "If
  the team wipes during this room, the statue progress is reset, along with a new puzzle being drafted" (Strat). The Apmeken
  wave counter and the Scabaras solvers reset on "Your party failed to complete the challenge" (LD). Disconnect outcomes (rejoin, eject, wipe)
  are raid-wide (Main); Main notes that disconnecting during the Kephri (Scabaras) puzzle does not eject a player. After each cleared challenge poison and venom are removed (Strat, "Nexus" section).
- **PC5 Points:** the **Chest (Tombs of Amascut)** page states the damage rule (Chest): "Room points are awarded per damage dealt to every
  NPC in the raid. For most NPCs, 1 damage dealt equates to 1 room point"; room points cap at **20,000**, total reward points at
  **64,000** on room completion; multipliers: Apmeken's path baboons **1.2x**, Ba-Ba **2x**, Zebak **1.5x**, Kephri's scarabs
  **0.5x**, Het's Seal **2.5x**, Obelisk **1.5x**, Wardens P2 **2x**, P3 **2.5x**, Warden's Core **0x**; the room's top scorer gets an
  MVP bonus of `300 x TeamSize` on completion. Death -20% with a 1,000 minimum and base 5,000 are from LD / PT and the raids brief.
  The puzzle-room multipliers therefore are: baboons (all of 11709-11718 incl. thralls and volatile) 1.2, Seal 2.5 (LD / PT apply it to the
  weakened Seal 11707 only; the Chest page does not say whether the protected form counts, **OPEN-PZ10**), crocodile 1 by default,
  Palm **2.0** (PT only; LD has a TODO "tree is 2"; PT also splits palm points by group size and caps them at 10,000 in groups and
  20,000 solo; LD caps Zebak at 10,000 and Wardens at 60,000). The puzzle scarab 11697 and the obelisk 11698 (puzzle) are not
  listed, so 1.0 by default (the Chest "Obelisk" row is the Wardens' obelisk). Non-damage points (LD TODO comments, "insider
  knowledge", unconfirmed): 50 per puzzle completed and 50 to each player in the lane on completion; memory puzzle 100 per matched
  tile, only to the second player; Apmeken 25 per pillar repaired and 25 per vent neutralised, -25 for a mistimed repair or pour,
  25 to the sight holder on a corruption clear and -25 on a fail, 25 to the healer on a cure. Plugin completion estimates when leaving
  a puzzle for its boss: Scabaras +300, Apmeken +450, Crondis +400 to the personal total (PT, LD) and +300 in LD's `nonPartyPoints`
  (PT calls the 300 an MVP assumption; Chest says `300 x TeamSize` for the top scorer); Het has no personal estimate. Everything
  except the Chest rules is **OPEN-PZ10**.
- **PC6 Combat Achievements that read these rooms** (CA pages; all need raid level **150+**): Perfect Apmeken (group of 2+,
  no danger allowed to trigger, nobody stands in venom or is hit by a volatile, room cleared in under **3 minutes** = 300
  ticks), Perfect Crondis (no crocodile reaches the tree, nobody loses water, under **1 minute** = 100 ticks), Perfect
  Scabaras (under 1 minute, nobody takes puzzle damage), Perfect Het (no light-beam or orb damage, Seal destroyed in one cycle).
  The Raid Summary titles "The Moth" (died to a light beam in Het) and "The Entomophobe" (killed the most scarabs) read the
  same events (Main). The server must keep these as events, **OPEN-PZ10**.

- **PC7 Flame barrier sides (map-derived; the wiki gives no layout):** in all four rooms two barrier pairs gate the challenge area.
  Apmeken and Scabaras are entered from the **west** (door 45128 at the west edge) and left through the continue loc on the **east**;
  Het and Crondis are entered from the **east** (Het door 45128 at x 3699, Crondis exit loc 45453 at x 3955) and left through the
  continue loc on the **west**. The barrier nearest the entry door is the challenge-area entry gate (LD for Crondis), the one nearest
  the continue loc closes the challenge area on the exit side: Apmeken entry x 3796, exit x 3820; Het entry x 3691, exit x 3669;
  Crondis entry x 3950, exit x 3922; Scabaras has two single barriers at x 3532 (z 5274 and 5286) near the west door, one per half
  (LD calls z 5286 the upper half). Which barrier opens when, and whether they stay shut after a wipe, is **OPEN-PZ1**.

## Path of Apmeken (baboon waves and Apmeken's Sight)

- **AP1 Room type:** "the only path that is not a puzzle room, rather it is a pure combat room" with quick-time events (Strat).
  Entry through the Nexus door (46158 / 46159 states) into the room; exit through the continue loc 45500. The fire in front of
  the door disappears once the path is cleared (PA).
- **AP2 Contents:** pillars (roof supports 45494, 3x3) on either side, four vents in the corners, crates of hammers (45497) and
  neutralising potions (45498) (Strat). A take gives **100** neutralising potions (20 before 1 Sep 2022) and a hammer; the
  hammer is **removed when the challenge is completed** (1 Sep 2022). A Dragon warhammer replaces the hammer (Strat).
- **AP3 Apmeken's Sight:** the **first player who enters** the room gets the Sight (groups choose who enters first); in groups the
  Sight "will randomly cycle between players until the encounter is completed" (Strat). The cycle interval and the selection
  rule are **OPEN-PZ11**. Spot anims 2132 (gain) / 2133 (lose). "You sense an issue somewhere in the room." appears
  throughout the encounter; the Sight lets the holder see the problem (the Strat caption: the Sight "can be seen by the vents,
  indicating players to pour neutralising potions on it") and, in groups, see corrupted players glow dark red (Strat).
- **AP4 Three danger types** (Strat; calls "P", "V" and "DD" are community convention):
  1. **Roof supports** (pillar 45495 shaking, spot anim 2139 on failure): a player clicks the support once ("Repair") with a
     hammer in the inventory.
  2. **Floor vents** (spot anim 2138 on failure): a player stands in the **centre of the vent** and uses "Pour" on the
     neutralising potion.
  3. **Corruption** (groups only; mark spot anim 2137): the Sight holder sees every corrupted player glow dark red; all players
     gather on **one tile** and the holder pours a potion, which cures everyone; alternatively the holder uses the potion on one
     corrupted player to cure that player.
- **AP5 How many actions:** "The amount of actions required depends on the player count"; solo needs **one** vent or **one**
  pillar; it "scales up in single increments", so 3 players need 3 vents or 3 pillars (Strat). Which danger appears, how often,
  and how many pillars / vents exist in total are **OPEN-PZ12**.
- **AP6 Failure:** when the required repairs / pours / cure are not done, **every player in the room takes damage**, "scaled to
  the amount of actions missed" in groups; a player who fixes a pillar or cleans a vent that did not need it also takes damage
  (Strat). Messages: "The fumes filling the room suddenly ignite!", "Damaged roof supports cause some debris to fall on you!",
  "Your group is overwhelmed by Amascut's corruption!" (MT). The damage amounts, the countdown from the warning to the
  failure, and the penalty for a wrong action are **OPEN-PZ12**. MT counts the failure on every raider for the tick the message
  is received.
- **AP7 Waves:** eight waves must be cleared (Strat; PA changelog: waves 2 and 3 were merged on 2 Sep 2022; Main and the 25 Jun 2025 "Summer Sweep Up: Combat" post: two waves
  removed; the LD overlay also lists 8). The table is for a **solo** raid; in a group "an extra combat baboon is
  added into the mix" (the type is **OPEN-PZ13**). "Special" stands for the "specialist baboons"
  (shaman, volatile, cursed; inferred from the Strat table); how the Special slots are chosen is **OPEN-PZ13**.

  | Wave | Monsters (Strat, LD `Baboon.WAVES`) |
  |---|---|
  | 1 | 2 Brawler, 1 Shaman |
  | 2 | 2 Thrower, 1 Volatile |
  | 3 | 2 Mage, 1 Cursed |
  | 4 | 2 Thrower, 3 Special |
  | 5 | 2 Mage, 1 Shaman, 2 Special |
  | 6 | 2 Brawler, 1 Shaman, 2 Special |
  | 7 | 1 Brawler, 1 Thrower, 1 Shaman, 2 Cursed |
  | 8 | 1 Shaman, 2 Volatile, 2 Special |

  The next wave starts before the current one is dead: 30 Aug 2023 "The minimum and maximum amount of baboons for most waves
  have been increased, meaning fewer baboons must be killed before the next wave begins". The thresholds are **OPEN-PZ13**
  (LD detects a wave by an NPC spawning on one of the six spawn tiles in the Data section). The room is cleared when the eighth wave
  is dead (Strat "clear 8 waves"); the exact trigger for the challenge-complete message is **OPEN-PZ13**.
- **AP8 Defences and the max-hit rule:** each combat baboon "is weak to their corresponding style": Brawler (melee) weak to
  **magic**, Thrower (ranged) weak to **melee**, Mage (magic) weak to **ranged**, Shaman weak to ranged, Thrall weak to melee,
  ranged and magic (defence 0), Volatile and Cursed weak to ranged and magic (Strat table; cache defence bonuses agree: the
  weak slot is -50 / -60, every other slot 900, thrall 0). A hit with the **weak** style **always deals the player's maximum
  hit regardless of accuracy**: Brawler by magic, Thrower by melee, Mage by ranged (29 May 2024); Shaman and Cursed by ranged
  (25 Jun 2025); Strat: "for the specialist baboons, using a ranged weapon will guarantee a maximum hit". Strat also says the
  triangle rule "only applies to the brawler, thrower and mages", so the Thrall is excluded; whether the Volatile counts as a
  "specialist baboon" (ranged guarantee) is **OPEN-PZ14**. Brawler magic accuracy uses the **Defence level**, not the Magic level (Brawler, Mod Ash 10 Aug 2023); that sentence names
  only the brawler (the other baboons are **OPEN-PZ14**).
  Magic damage against a brawler "equal to the player's maximum hit" (Brawler).
- **AP9 Baboon attacks:** Brawler melee, Thrower ranged, Mage magic; attack speed 4; max hit 5 (level 56) / 7 (level 68); "moderately
  high accuracy and damage" (Strat). Level 56 / 68 split: **OPEN-PZ3**. Accuracy / damage by raid level: **OPEN-PZ7**.
- **AP10 Shaman:** magic attack with **speed 8**, max hit 7; the magic hit "drains prayer points equal to the amount of damage
  dealt" (Shaman) and Strat adds "if not protected against" (**OPEN-PZ15**: is prayer drained when the hit is blocked); summons
  Baboon Thralls; "each shaman has a maximum amount of thralls it can have summoned at once" and "up to four or five" (Strat;
  the cap is **OPEN-PZ15**); at the cap, or when **poisoned or envenomed**, it **stops summoning and attacks with magic**
  instead, with no visual indicator (Shaman, Strat; 30 Aug 2023). The summon rate and range are **OPEN-PZ15**.
- **AP11 Thrall:** summoned by a shaman, melee, speed 4, max hit 5, HP **2** (cache, infobox; Strat says 9, conflict 1); it
  drains prayer by "a percentage of what the hit dealt, including successful hits that would otherwise be blocked by prayer"
  (Thrall; the percentage is **OPEN-PZ15**). Cursed baboons are immune to thralls. Thralls despawn with their shaman: not
  stated (**OPEN-PZ15**).
- **AP12 Volatile baboon:** on spawn it picks a **random party member** (not distance, not any attribute; Mod Ash 9 Nov 2023)
  and walks towards it; it does not attack. On reaching melee distance it "charge[s] up slightly" and explodes in a **3x3 AoE**
  centred on itself (MT: spot anim 131 at the centre, all nine tiles damage), for "massive" damage that "appears to be based on the
  amount of health they had prior to exploding" (Strat, the Vorkath zombified spawn comparison; no number). A player who steps
  at least one tile away avoids it (Volatile). The blast **kills every baboon in the 3x3 regardless of health** (30 Aug 2023
  change, Volatile) and does not kill pets (30 Aug 2023 hotfix). The damage, the charge-up ticks, the retarget rule when the
  target dies or leaves, and whether the volatile dies from its own blast are **OPEN-PZ16**.
- **AP13 Cursed baboon:** not aggressive, wanders and leaves **venom pools** (loc 45493) where it walks; a pool lasts **20 s**
  (33.3 ticks, rounding **OPEN-PZ17**; was 30 s until 25 Aug 2022); standing on one deals **6-8 damage per tick** and
  inflicts venom unless immune (Strat; MT: a pool does not hurt on the tick it spawns, so a one-tick grace). It never walks over a
  vent tile or the tile in front of a pillar and usually does not walk under a player (Strat). It is immune to thralls.
  The pool interval, whether the 6-8 is the venom hit or the pool's own damage, and the venom ramp are **OPEN-PZ17**.
- **AP14 Status effects:** the Shaman is the only poison-vulnerable baboon (Shaman page, resistance 0; 30 Aug 2023). Perfect
  Apmeken advises "Freeze any Cursed Baboons", so ice spells work on at least that baboon; freeze and bind values for the others
  are **OPEN-PZ14**.
- **AP15 Completion and reset:** all eight waves dead -> a "Challenge complete: Path of Apmeken." line (format from the LD `SplitsTracker` pattern; the Apmeken text itself is not shown), the hammer
  is removed, the continue loc 45500 opens and the room's fire is gone; on a wipe the room restarts at wave 1 (LD resets its
  counter on the failure message, PC4). Reward / Helpful Spirit after two cleared paths is raid-wide.
- **AP16 Per-level differences:** none stated for waves (same eight in every mode); baboon HP identical at every raid level;
  group size adds one combat baboon and raises the required actions (AP5); path level: PC2.
- **AP17 Tick timeline (stated only):** Shaman attack every 8 ticks; others every 4; venom pool 20 s; Perfect Apmeken limit
  300 ticks; nothing is stated for the wave gap, the warning-to-failure countdown, or the volatile fuse.

## Path of Crondis (the Palm of Resourcefulness)

- **CR1 Goal:** "fully grow the Palm of Resourcefulness in the centre of the room" by watering it from water containers filled
  at the waterfalls (Strat). Entry through the Nexus door into the room (the `TOA_PATH_BARRIER` 45135 "Pass" gate is the
  challenge-area entry, LD), exit through continue loc 45397; the fire goes once the path is cleared (PC).
- **CR2 Water needed:** Strat: "**175 units** of water, with an additional **125** for every additional player"; the 25 Jun 2025
  change (listed under Zebak) says "The amount of water required for the Palm of Resourcefulness **no longer scales linearly**
  with the number of players". The two statements conflict (conflict 4): the old formula gives 175 / 300 / 425 / 550 /
  675 / 800 / 925 / 1,050 for 1-8 players; the new curve is not given (**OPEN-PZ18**). A container holds **100 units** when full (inferred, not stated: MT's a palm "water up" hitsplat is
  capped at 100 via `MAX_WATER_HITSPLAT_UP_AMOUNT`, and below 100 counts as a spilled container). Growth stages 0 / 25 / 50 / 75 / 100% map to NPCs 11700-11704; the thresholds are
  derived from the stage names only (**OPEN-PZ18**). Watering with a normal watering can or Gricoller's can adds only
  **1 hitpoint** of growth and a Gricoller's can uses **10** charges per pour (Palm).
- **CR3 Water containers:** one is picked up from the ground (item 27295; "Another will be nearby" if destroyed), filled
  with "Fill" on a waterfall (45398; the waterfall goes dry, 45399, and refills later; the refill time is shortened by the
  8 Sep 2022 change and "respawns accordingly to team size" but is **OPEN-PZ19**), and emptied with "Water" on the palm
  (MT sees player animation 827 both when filling at a waterfall tile and when watering from a palm tile). Humidify does nothing to it (Container).
- **CR4 Traps:** statues around each waterfall fire **retracting spikes** from the sides (45414-45416) and the statue in front
  of the waterfall discharges **poison clouds** on the tiles in front of it which flow along the path (45418-45420); only the
  darker cloud damages, the lighter cloud is a warning (Strat); the traps run on a fixed sequence (Strat: "traps being on a
  sequence", spike pattern "a specific pattern"). **A hit by either trap** halves the water in the container (below), lowers
  Defence by **6**, Agility by **3** and Prayer by **2** per hit (Strat). Damage per hit, cycle lengths and phases, and the
  spike line geometry are **OPEN-PZ20**. Container loss: **50% of the current amount** per hit, with a small chance of **16%**
  instead; "stacks effectively infinitely", never below **1%**; a crocodile bite also takes 50%, but once the container is under
  **15%** a bite takes **10%** (Container). The chance of the 16% case is **OPEN-PZ20**.
  Tile-skipping (Strat): spikes can be skipped entirely by starting one tile north or south of the first statue and running past
  without stopping; this needs a **moving start** (a spike cannot be tile-skipped if the player stood on the start tile longer than
  one tick); the poison clouds can be dodged within the **2 closest rows** to the statues. The traps' timing must therefore
  be tick-deterministic; the exact cycle is **OPEN-PZ20**.
- **CR5 Crocodiles:** red crocodiles spawn "periodically" from the **corners** of the room; their number depends on the player count;
  **at most eight alive** at once (Strat, Croc). The first ones need about **40 s** (about 67 ticks) from the start of the
  challenge to reach the palm; a team that waters before that can ignore them (Strat). Spawn period, count by team size, spawn
  corners and despawn on completion are **OPEN-PZ21**. 21 Sep 2022 made the spawn pattern "more consistent" (no pattern given).
  Crocodiles can be delayed with Binding or ice spells; while they cannot reach the palm or a player, no water drains (Croc).
- **CR6 Crocodile target priority:** Croc (NPC page): (1) a player carrying a **non-empty** container within aggression range;
  (2) the Palm when it has any growth; (3) a player **without** a container who has attacked the crocodile; so a player who
  attacks while holding an **empty** container is safe. Strat lists tree first, then carriers, then non-carriers (conflict 5).
  The NPC page is used (more specific, detailed enough to describe the empty-container exception); the order is **OPEN-PZ22**.
  Aggression range value: **OPEN-PZ22**.
- **CR7 Crocodile attack:** crush, speed **7**; a successful hit deals a fixed, predetermined damage **18**
  plus **3** for each trap hit (acid pool or statue spear) the player has taken in the last **30 s (50 ticks)**, up to **36**,
  "regardless of raid level"; other players' trap hits do not count (Strat, Croc). With **Protect from Melee** on, a successful
  bite drains **12** Prayer and deals a guaranteed **one third** of that damage (18 -> 6, 36 -> 12; derived); with a non-empty
  container, every successful bite also costs water (CR4). Whether a missed attack drains anything, the Quiet Prayers
  interaction, and how bites shrink the palm are **OPEN-PZ23**. The crocodile is a level 71 NPC with 30 HP; players can kill it.
- **CR8 Completion and failure:** the room completes when the palm reaches 100% (11704, no more "Water" op); crocodiles are then
  removed (not stated, **OPEN-PZ21**). No room timer is stated (none found in Strat, PC or Palm; only the raid-wide limit invocations); the pressure is the
  crocodiles shrinking the palm. A wipe restarts the room (PC4). The palm is shrunk by bites when it has growth (Strat).
- **CR9 Fastest solo route (Strat, for timing validation):** 34.8 s = **58 ticks**, or 32.4 s = **54 ticks** using "use the
  container on the palm and waterfalls" instead of left-click, saving 1 tick per interaction (4 ticks in total); the route needs
  two fills (northern container, NE waterfall, palm, NW waterfall, palm) with no trap damage, which implies two full containers
  (200 units) cover the solo requirement of 175 (derived).
- **CR10 Per-level differences:** crocodile damage fixed at 18 / 36 for every raid level (Strat); crocodile stats do not scale
  with raid level (Strat); water needed per CR2; path level: PC2 (only the Zebak boss is described). Perfect Crondis (PC6) needs
  no croc at the palm, no water lost and < 100 ticks; a player who dies gets the water container back in the inventory (CA page
  strategy), so the container must be re-issued on death inside the room.
- **CR11 Tick timeline (stated only):** croc attack every 7 ticks; trap-hit memory 50 ticks; first croc at the palm after
  about 67 ticks; the Perfect Crondis limit is 100 ticks; trap period and the waterfall refill are not stated.

## Path of Het (mirrors, beam and Het's Seal)

- **HE1 Goal:** break **Het's Seal** at the centre of the room: redirect the beam fired by the caster statue (45486) with mirrors
  so that it hits the shielded statue (45485), which weakens the Seal (NPC 11706 -> 11707); then mine the Seal (op "Destroy") with
  a pickaxe until it is dead (Strat, Seal). Exit through continue loc 45131; the fire in front of the door goes when the path
  is cleared (PH).
- **HE2 Pickaxe statues:** there are three statues (entrance, within the room, exit; 14 Sep 2022 added the one near the exit)
  that each hold a pickaxe. The default is a free **bronze pickaxe**; a player can **deposit** a better pickaxe, which stays
  stored for later raids, and take it back at any statue or from the cavity in the lobby (Strat, Statue; varbit 14440
  and varp 3635; both charged and uncharged infernal and the trailblazer ornamental are accepted). Strat: "Better pickaxes break the stone
  barriers faster and deal more damage to the Seal". The Statue page pins five positions (3662 / 5275, 3662 / 5283, 3679 /
  5267, 3679 / 5291, 3695 / 5284) against the three statues the text names (**OPEN-PZ24**). The exact rules of storage with several players and across wipes are
  **OPEN-PZ24**.
- **HE3 Mirrors and walls:** three mirrors can be **picked up, placed, rotated and pushed** (item 27296, loc ops Pick-up /
  Rotate-clockwise / Rotate-anticlockwise / Push); the other mirrors in the room are fixed; "in group encounters, some of these
  are dirty and must be cleaned first" (loc 45457 op "Clean"). Undamaged stone barriers block the beam; "worn" barriers (op
  "Break", 45462 / 45464) are broken with a pickaxe (rubble 45466, mirror rubble 45467). "The light can always be reflected
  correctly using 1-3 mirrors, with the removal of some barriers." Strat: "In group encounters orbs may cause mirrors they collide with to become dirty"; LD's comment
  says movable and already-fixed mirrors can both end up dirty ("black orbs hitting mirrors"), so which mirrors can be dirtied is
  **OPEN-PZ25**. Fixes: 24 Aug 2022 two mirrors placed on one tile no longer cost a mirror; pushing a mirror into a wall no longer traps
  it; 19 Oct 2022 no more smuggling mirrors between phases. How the layout (which walls are worn, where fixed and dirty mirrors
  are, which of the nine solution shapes) is chosen for a given raid, and whether it depends on team size, is **OPEN-PZ25**. Mirror
  push distance, rotation steps (4 diagonals, LD) and whether a carried mirror can be dropped are **OPEN-PZ25**.
- **HE4 Beam:** the caster statue "periodically" fires an energy beam (Strat). LD assumes **one beam every 9 ticks** (the wiki gives no
  figure; **OPEN-PZ26**), the first **10** ticks after the challenge-start message, and **no beam while the Seal is weakened**,
  resuming 10 ticks after it protects itself again. The beam travels its path (graphics 2114 horizontal, 2064 vertical, 2120
  crash, corner variants OPEN-PZ6). Being in the beam deals damage, "lower than the orbs" (Strat); LD / MT: the damage arrives **one tick after** the
  beam graphic appears and **all** players on beam tiles are hit. The amount is **OPEN-PZ27**.
- **HE5 Orbs of Darkness:** orbs "appear at the edges and move in a straight line to the other end of the room", dealing "moderate
  damage" to players they hit; occasionally two share a tile and **both** hit (Strat, Orb). Orb NPC 11708, walk anim 9542. When
  two players share the tile only one is hit (MT observation). Spawn rate, speed, damage, the tile pattern and the dirtying rule
  are **OPEN-PZ27**. The Duke Sucellus fight reuses the NPC with "up to 27 damage" and stat drains (Orb): a different encounter,
  not evidence for this room.
- **HE6 The Seal's HP:** **119** with one player (cache and Seal, lowered from 122 on 20 May 2026); 2 -> **225**, 3 -> **321**,
  4 -> **417**, 5 -> **512**, 6 -> **608**, 7 -> **704**, 8 -> **800** (Seal). It "scales with the number of players but not
  with raid level or invocations".
- **HE7 Weakened window:** once the beam hits the shielded statue the Seal is exposed for **15 ticks (9 s)** and then protected again
  (Strat, Seal). It is possible to land **8 hits per cycle** by standing one tile from the Seal and clicking "the tick before the
  statue fires the beam" (Strat, Seal, CA page). The Seal page gives **35 Mining XP every 3 ticks** while mining. These
  numbers do not fit together: 15 ticks at a 3-tick cadence is 5-6 hits, and the wiki's own one-cycle claims need 7 hits
  (7 x 17 = 119, "a Dragon pickaxe with 85 Mining ... only hitting 7 times") or 8 hits (8 x 15 = 120, rune 85 "tick-perfect
  start"): conflict 6, **OPEN-PZ28**. The server must pick a cadence. Seal also says 99+ Mining with a Dragon pickaxe "can one cycle the room without
  needing the extra hit"; at minimum damage 19 that needs at least 7 hits (6 x 19 = 114 < 119 <= 7 x 19 = 133; derived).
- **HE8 Damage to the Seal (Seal, Strat, Mod Ash 25 Mar 2023):** a roll per hit, **not** influenced by mining speed, scaled by the
  **visible** Mining level (invisible boosts such as the celestial ring do not count) and pickaxe tier:
  base **8 / 15 / 17** for visible level **< 85 / 85-98 / 99+** (Mod Ash gave the top bracket as 100+; the Seal page
  was changed from 100 to 99 on 25 Jun 2025 and wins, conflict 7); **+2** for a dragon pickaxe or better; **-3** for a pickaxe
  below rune; then **+0, +1 or +2 uniformly**. Resulting table (matches Seal and Strat except one cell):

  | Visible Mining | Dragon (and Crystal, 3rd age equivalent) | Rune | Bronze-Adamant |
  |---|---|---|---|
  | 99+ | 19-21 | 17-19 | 14-16 |
  | 85-98 | 17-19 | 15-17 | 12-14 (page prints 12-15, conflict 7) |
  | 1-84 | 10-12 | 8-10 | 5-7 |

  Seal page, 84 Mining or lower: a Dragon pickaxe's max of 12 "will always guarantee a two-cycle in a solo run" with the 8-hit method,
  a rune pickaxe's max of 10 may force a three-cycle on low rolls (Seal). Perfect Het strategy: "a boosted Mining level of 100 or
  higher" removes the need for the early click (a third threshold statement, conflict 14). A Dragon pickaxe special can lift a level 82+ player into the 85 bracket (Strat; the boost size is not stated). Which tier
  class the gilded, infernal, trailblazer, trailblazer reloaded and Echo pickaxes fall in, and whether the Seal damage uses the
  pickaxe in the inventory or equipped, is **OPEN-PZ29**. Mining XP: 35 per hit (Seal). The Seal's example claims: 85+ Mining
  with a Dragon pickaxe one-cycles by hitting 7 times; Mining 99+ one-cycles without the early click; 82 with a Dragon spec boost
  to 85 one-cycles (CA page, Strat).
- **HE9 Completion:** when the Seal dies it becomes the Broken seal (loc 45489) and the room completes ("Challenge complete: Path of
  Het." with duration; LD stops the solver on it); Perfect Het needs the Seal destroyed in **one** cycle without light-beam or orb
  damage (CA page). The continue loc 45131 opens.
- **HE10 Failure:** a wipe resets the statue progress and drafts a new puzzle (Strat, PC4); damage taken only subtracts hit points.
  Whether the Seal returns to its protected state and the beam timer restarts is not stated (assumed, **OPEN-PZ28**).
- **HE11 Per-level differences:** none for the room: Seal HP scales with players only; the orbs and beam damage may scale with
  raid level (**OPEN-PZ7**); path level (PC2) lists only Akkha's mechanics.
- **HE12 Tick timeline (stated only):** window 15 ticks (9 s) after the beam hits the shielded statue; XP tick 3; beam period 9
  (LD, not wiki); light damage applied the tick after the graphic (MT); nothing is stated for orb speed.

## Path of Scabaras (five puzzles and the scarabs)

- **SC1 Structure:** "five puzzles to complete between two branched paths"; the players "must complete the puzzles in both paths
  before moving forward" (Strat). Solo: after the first puzzle is cleared the player takes the shortcuts between the rooms, passes the opened door and does
  the next puzzle on the other side; "solo raiders should always avoid the pillar room" because it is random (Strat; that this is the
  obelisk puzzle is inferred from the randomness and the obelisks being the "pillars"). Cache locs that look like the shortcuts (roles inferred from names only): "Passage" (op Crawl-through, 45343), "Platform"
  (op Jump-to, 45396), flame barriers 45135 and Scabaras barriers 45336. The room-to-room geometry and which puzzle is on which side is **OPEN-PZ30**; LD's skip rule: the
  obelisk puzzle is in one of the four slots, and the upper or lower flame barrier is the one to pass to avoid it
  (obelisk at top-left or bottom-right -> use the lower flame; top-right or bottom-left -> use the upper flame).
- **SC2 Scarabs in the room:** "scarabs will appear from the first two puzzle rooms"; they "attack with ranged and serve little
  outside of being a nuisance" so Protect from Missiles blocks them; the **spawn point is the start of the first room**, and "in
  larger groups they may move up to the second room" (Strat). NPC 11697: level 56, **HP 12**, attack speed **4**, max hit **6**,
  aggressive, ranged, fire weakness 40% (Scarab). Spawn count, rate and any cap, and the damage formula by raid level are **OPEN-PZ31**.
- **SC3 Puzzle 1, sequence ("Simon says"):** pressing the **Ancient button** (45338) lights a sequence of pressure plates in order
  (LD keeps the last 5 lit plates, so at most 5 is the plugin's assumption); the players must then step on those plates **in the
  same order** without stepping on any other; a wrong plate "will inflict damage" (Strat); LD treats a roof-collapse graphic
  (spot anim 302) on a plate as a failed attempt. The button can be pressed again
  for another order if the player forgot (Strat). Plate objects: 45340 (idle), 45341 (lit), 45342 (stepped on). The damage per wrong step, the
  lit time per plate, the delay before input, and whether the new order replaces the old are **OPEN-PZ32**.
- **SC4 Puzzle 2, obelisks:** five obelisks (NPC 11698, op "Hit"); the players must hit them in a **specific order** with a ranged
  weapon (Strat: "requires a weapon with an attack range of 10" to stand on the lower step; magic is implied by the 4 Sep 2024
  note that spell XP on the obelisks was removed, melee is not mentioned; **OPEN-PZ33**). A correct hit lights the obelisk (11699); a wrong
  one makes the lit ones revert to the base NPC 11698 (LD relies on that) and drops **rocks from the ceiling** (dust anim
  9498), avoidable; at the **lower step of the stairs** no rocks target the player (Strat). The sequence is a random
  permutation of 5: 5P5 = 120 orders, 1/120 per first try (Strat). Spell XP on the obelisks was removed (4 Sep 2024). Rock
  damage, targeting and fall delay, and whether melee can hit are **OPEN-PZ33**.
- **SC5 Puzzle 3, lights ("lights out"):** a 3x3 field of plates with the **middle plate absent**, eight plates in all; stepping
  on one plate **toggles it and its four orthogonal neighbours** (derived: the lights-out matrix that reproduces LD's eight XOR
  masks; the wiki says "stepping on one tile causes adjacent tiles to shut off"). A plate can also be flipped by the right-click "Flip" (op5 on
  the unlit plate) for **20 unavoidable damage** (Strat). The goal is every plate lit; Strat shows nine target configurations with their solutions. The start state
  generation, whether it is always solvable, and any step damage are **OPEN-PZ34**.
- **SC6 Puzzle 4, addition:** all earlier puzzles must be completed before the tablet (45339, "Inspect") can be read; the read
  gives a random number between **20 and 45** (chat "The number <n> has been hastily chipped into the stone."); the players step on
  plates whose symbols add to the number; **going over** the number damages the player. The symbols' values are 1-9 (Strat
  example: 24 = 7 + 8 + 9; LD maps the plates `TOTALTILES_TILE1..9` to 1 line, 2 knives, 3 triangle, 4 diamond, 5 hand, 6
  bird, 7 crook, 8 wiggle, 9 foot/boot), shown on the north and south walls near the entrance; the grid is **5x5**, and LD stores one fixed plate-index solution per target 20-45, which implies a fixed layout (inferred). The grid contents, whether the target is uniform, the
  overshoot damage and whether stepping off removes a plate are **OPEN-PZ35**.
- **SC7 Puzzle 5, matching:** two boards of nine symbol pairs; the players must step on **identical** symbols on both boards, so the
  layout must be memorised; **in a solo raid four of the nine sets are already solved** (LD: line, crook, hand, bird) and "the
  statue is moved up one tile to allow the player to move between both grids"; otherwise the statue blocks access and all
  sets start unsolved (Strat). Tiles: buttons 45356-45364, symbols 45365-45373, matched marker FX objects. The last puzzle
  "is incapable of damaging players" (CA page, Perfect Scabaras). Points: 100 per matched tile to the second player (LD TODO).
  Flip animation 9490 (`TOA_FLIP_TILE`). The board shuffle, the reveal time and the mismatch rule are **OPEN-PZ36**.
- **SC8 Completion:** the room completes when the puzzles of both branches are done (Strat); a "Final" ancient button (45355) exists
  but its role is not stated, and the exact trigger and the continue loc 45337 are **OPEN-PZ30**. Perfect Scabaras: under **100 ticks** and nobody takes puzzle
  damage.
- **SC9 Per-level differences:** none stated for the puzzles; scarabs' scaling **OPEN-PZ31**; path level: PC2 lists only Kephri's
  mechanics (faster autos, swarms) for the boss.
- **SC10 Tick timeline (stated only):** scarab attack every 4 ticks; Perfect Scabaras limit 100 ticks; everything else is unstated.
- **SC11 Kephri's scarabs (not this room):** Soldier (slash, melee, heals Kephri's shield with a swarm when the shield is down, amount
  scales to its health), Spitting (ranged, speed 3, poison from 6, hits through protection prayers), Arcane (magic, charges a red
  swarm, relocates after `2 + N` hits or one hit of 39+; Protect from Magic reduces the swarm), Agile (retreats at run speed),
  Swarm (restores shield, HP 10). Defence values no longer scale with raid level (25 Jun 2025); fire weakness 50% (Soldier,
  Spitting, Arcane; 29 May 2024) and 40% (Scarab, Swarm; 25 Jun 2025). See kephri.md (KP7, KP8) for the fight rules.

## What RuneLite plugins assume (summary)

- **Apmeken:** NPC ids 11709-11718 for outlines and the waves overlay, volatile 3x3 box on 11716 positions, venom pool loc 45493,
  vent / pillar / volatile spot anims 2138 / 2139 / 131, the three failure messages, spawn tiles for wave counting, 8 waves in the
  table above (LD, MT).
- **Crondis:** palm NPC name "Palm of Resourcefulness", animation 827 on waterfall and palm tiles, hitsplat types 11 / 15, palm hitsplat < 100
  = a lost-water mistake, the TOA_PATH_BARRIER 45135 "Pass" on the entrance (LD, MT).
- **Het:** caster 45486, shielded statue 45485, mirror locs 45455-45457, barrier locs 45458-45466, null 29733, seal NPCs 11706 /
  11707, beam graphics 2064 / 2114 / 2120, orb graphic 379, the start / complete messages (LD only), varbit 14440 (pickaxe stored), 9-tick beam period
  (LD, MT).
- **Scabaras:** obelisk NPCs 11698 / 11699, plate objects 45340-45342, light-plate object 45384 and unlit plate 45344, addition tiles
  45345-45353 and tablet message, memory tiles 45365-45373 and matched markers 45386-45395, roof-collapse spot anim 302, flame
  barrier 45135, the four slot positions (LD).
- **Common:** region ids per room (see Data), `TOA_CLIENT_P1..P7` for party size, `TOA_CLIENT_RAID_LEVEL`, HUD path levels,
  "Quick-Enter / Quick-Pass" ops on the doors and barriers (`QuickProceedSwaps`).

## Changes up to 2026-11-03 that matter

Also: 13 Dec 2022 Crondis "purgatory" mapping fixed; 11 Oct 2022 Scabaras puzzle corner tiles made easier to step on with the camera facing
east; 21 Sep 2022 Seal max-hit threshold 102 -> 100 Mining; 2 Sep 2022 (Seal) the extra early-click hit is deliberate ("attentive
players are rewarded with an opportunity to slip in an extra hit", balance post, quoted from the verifier note, not re-read here).

20 May 2026 Het's Seal HP 122 -> 119; 2 Jul 2025 shaman HP 50 -> 40 and cursed 30 -> 20; 25 Jun 2025 shaman and cursed always take
a max hit from ranged (HP raised), two Apmeken waves removed, croc earth weakness 40%, puzzle scarab fire weakness 40%, Seal max-hit
threshold 100 -> 99, palm water no longer linear in players (listed under Zebak); 4 Sep 2024 no spell XP on the Scabaras obelisks;
29 May 2024 baboon HP static, brawler / thrower / mage max-hit rule; 9 Nov 2023 volatile target choice (quote); 30 Aug 2023
volatile explosion hits baboons and not pets, shaman poison vulnerability, smaller wave thresholds; 25 Mar 2023 Mod Ash Seal damage
formula; 21 Sep 2022 croc spawn pattern; 14 Sep 2022 third pickaxe statue, near the exit; 8 Sep 2022 waterfall refill time shortened; 2 Sep 2022
waves 2 and 3 merged; 1 Sep 2022 100 potions per take, hammer removed on completion, fewer baboons, Seal HP raised for groups; 25
Aug 2022 venom pool 30 s -> 20 s, Seal damage scales with tier; 24 Aug 2022 mirror fixes.

## Open list

OPEN-PZ1 which room the remaining door symbols (`TOA_DOOR_EXIT*`, `TOA_DOOR_TOP*`) and the Crondis exit loc serve; OPEN-PZ2 withdrawn (ranged
defence is given by the infoboxes, Data); OPEN-PZ3 level 56 vs 68 baboon variant selection; OPEN-PZ4 animation-to-baboon mapping,
role of transition locs 45501 / 45502 and spot anims 2134-2136; OPEN-PZ5 which poison tile id is the dark (damaging) cloud; OPEN-PZ6
corner beam graphic ids; OPEN-PZ7 whether raid level scales accuracy / damage / defence of baboons, croc, orbs, beam and scarabs;
OPEN-PZ8 whether path level +8% / +5% applies to puzzle NPCs and what "completing a path" means for Walk the Path; OPEN-PZ9 Deadly
Prayers vs trap damage, Overly Draining vs the pickaxe special; OPEN-PZ10 puzzle points and CA tracking; OPEN-PZ11 Sight cycle rule
and interval; OPEN-PZ12 Apmeken danger schedule, pillar / vent counts, failure damage and countdown; OPEN-PZ13 wave advance thresholds,
extra group baboon, Special slot choice, challenge-complete trigger; OPEN-PZ14 max-hit guarantee for the Volatile, Defence-level
magic defence for non-brawlers, freeze / bind immunity; OPEN-PZ15 shaman and thrall details (cap 4 or 5, drain percent, blocked
hits, summon rate); OPEN-PZ16 volatile damage, fuse and retarget; OPEN-PZ17 cursed baboon pool rate, damage and venom;
OPEN-PZ18 water needed per team size and the palm stage thresholds; OPEN-PZ19 waterfall refill time; OPEN-PZ20 trap damage, cycle
timing, geometry and the 16% container-loss chance; OPEN-PZ21 crocodile spawn count, period, corners and despawn; OPEN-PZ22
crocodile target priority order and aggression range; OPEN-PZ23 crocodile bite effect on the palm, miss and prayer interactions;
OPEN-PZ24 pickaxe storage rules; OPEN-PZ25 Het layout generation, mirror push and carry rules; OPEN-PZ26 beam period (only LD's 9 ticks; the wiki gives none); OPEN-PZ27 beam and orb damage, orb spawn pattern and speed; OPEN-PZ28 Seal hit cadence vs 15-tick window; OPEN-PZ29 pickaxe tier
classes and Seal damage source; OPEN-PZ30 Scabaras room geometry and completion trigger; OPEN-PZ31 scarab count, rate, cap and
scaling; OPEN-PZ32 sequence puzzle numbers; OPEN-PZ33 obelisk rock damage and targeting; OPEN-PZ34 lights start layouts and damage;
OPEN-PZ35 addition grid, target roll and overshoot damage; OPEN-PZ36 matching board and mismatch rule; OPEN-PZ37 whether the palm's water survives a wipe (as-built: it does,
`toa.crondis.keepGrowthOnWipe`); OPEN-PZ38 whether the lobby cavity hands out a spare bronze pickaxe (as-built: no). **37 open
items** (PZ2 withdrawn).
Suggested config flags: `toa.apmeken.waveThreshold`, `toa.crondis.waterPerPlayer`, `toa.het.beamPeriodTicks` (default 9),
`toa.het.sealHitIntervalTicks`, `toa.scabaras.scarabRate`, `toa.puzzles.pathLevelAppliesToPuzzleNpcs`.

## Conflicts between sources

1. Thrall HP: Thrall infobox and cache **2** vs Strat table **9**. The cache is used.
2. Baboon and scarab `scaledhp = Yes` infoboxes vs the 29 May 2024 change and Strat ("static, rather than scaling"). The
   prose is used: static HP for the baboons; the puzzle scarab is **OPEN-PZ7**.
3. Cursed Baboon combat level: wiki 77 vs cache **70**. The cache is used.
4. Palm water: Strat "175 + 125 per extra player" vs the 25 Jun 2025 change "no longer scales linearly". The newer change is
   used as direction only; the numbers are **OPEN-PZ18**.
5. Crocodile targeting order: Strat (tree, then carriers) vs Croc (carriers, then tree). The NPC page is used.
6. Seal hits: "35 XP every 3 ticks" and a 15-tick window vs "8 hits per cycle" and the 7-hit one-cycle claim. Unresolved
   (**OPEN-PZ28**).
7. Seal damage table: adamant 85-98 prints 12-15 vs the Mod Ash formula 12-14; top bracket 100+ (Mod Ash 2023) vs 99+ (Seal, Strat).
   The formula and the 99 threshold are used.
8. Soldier Scarab attack speed: cache 6 vs infobox 7 (Kephri's fight, out of scope). The cache is used, as in kephri.md.
9. Equivalent pickaxes: Seal "Dragon and Crystal", Strat "Dragon, Crystal and 3rd Age". Strat used.
10. Scabaras puzzle count: request says four, the wiki says five (SC1). The wiki is used.
11. Arcane Scarab: "reduced by Protect from Magic" (Arcane) vs "attacks can hit through protection prayers" (Spitting). Kephri's
    fight; not resolved here.
14. Seal top Mining bracket: Mod Ash 100+, Seal page and Strat 99+, Perfect Het "boosted Mining level of 100 or higher". Seal / Strat used (99).
15. Pickaxe statue locs: wiki Statue infobox "Bronze" 45469 / 45470 and "Empty (unused)" 45468 vs cache symbols 45469 `_EMPTY01`
    and 45468 `_PARENT` (multi-loc). The cache is used.
12. Banana peels: attributed to "baboons" (Baboon, Peel); the Peel page limits them to the Ba-Ba fight, so none in the Apmeken room.
13. Volatile explosion graphic: MT matches spot anim 131, the cache has `SPOTANIM_MANDRILL_EXPLODE` 2249. Both listed; the
    server should send 2249 (cache name) until a capture shows otherwise (**OPEN-PZ16**).

## As built (2026-11-08)

- **Points (PC5).**
  - Default: the flat estimates.
  - With `toa.puzzlePoints.flat=false`, the per-action points instead:
    - Apmeken: ±25.
    - Scabaras: 50 per puzzle solved and 100 per memory match.
    - Crondis: 2 per water.
    - Het: 50 to each raider per statue hit.
  - Deductions never go below 0.
- **AP3:** the holder alone sees the vent warnings and the corrupted glow (private map anims, shown when the danger starts
  and when the Sight moves). The shaking pillars are a loc, which everyone sees.
- **AP13:** a pool tick also envenoms (the combat poison rules, immunity included).
- **CR4:** spears are out while dangerous. Cloud tiles show the light cloud the tick before, the dark cloud while
  dangerous, then the vines.
- **SC1 shortcuts (cache map and collision):**
  - Passage 45343: crawl between z 5276 and 5284 (3 ticks).
  - Platform 45396: jump between z 5277 and 5283 (2 ticks).
- **HE2:** the lobby cavity shares varbit 14440. It hands out no spare bronze pickaxe (OPEN-PZ38).

