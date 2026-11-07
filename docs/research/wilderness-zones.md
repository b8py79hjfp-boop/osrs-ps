# Wilderness zones: levels, dungeons, multi/singles, safe zones, HUD (research, 2026-11-07)

Scope: audit of `content/pvp` (Wilderness.kt, Lairs.kt, RevenantCaves.kt, PvpCombat.kt), `plugin-api/PolygonAreas.kt`,
`content/pvp/src/main/resources/tp/content/pvp/multiway.json` and `docs/mechanics/wilderness.md` against
authoritative data. Research only; no code changed.

## Sources

| Tag | Source | Notes |
|---|---|---|
| **CS384** | Cache 2757 (`.data/cache/242`), clientscript **384** (the proc the client uses to compute the Wilderness level), disassembled with a throwaway JS5 + cs2 reader (RuneLite `ScriptLoader` rev237 layout; opcode names from RuneLite `cache/script/Opcodes.java`, with 3310 = coord level and 3311 = coord z, which the arithmetic confirms) | **Authoritative** for level numbers: it is what the client draws as "Level: N". Helper proc **247** = `inzone(c1, c2, c)`, inclusive on x, level and z. Coords are packed `level<<28 \| x<<14 \| z` |
| CS388/386/387/385 | Same cache: 388 sets `pvp_icons:wildernesslevel` text, 387 sets `pvp_icons:comlevelrange`, 386 builds `pvp_icons` (interface 90), 385 is its timer (re-runs every 100 client cycles) | HUD behaviour |
| CACHE-MAP | Cache map + collision via `tools/cache-dump` jars (flood fill with RS Mod `StepValidator`) | Walkable extents, ditch and barrier placements |
| WIKI | OSRS Wiki raw wikitext (2026-11-07), saved under `/tmp/claude-0/-home-user-osrs-ps/b1718ccc-d6fb-5afc-a85d-9fc37fb3c2a5/scratchpad/wild/*.wiki` | Pages: Wilderness, Multicombat area, Singles-plus combat, Single-way combat, Ferox Enclave, Revenant Caves, Wilderness Slayer Cave, Wilderness God Wars Dungeon, Lava Maze Dungeon, Deep Wilderness Dungeon, Abyss, Resource Area, Escape Caves, Callisto's Den, Vet'ion's Rest, Silk Chasm, Hunter's End, Skeletal Tomb, Web Chasm, Scorpion Pit, KBD Lair, Corporeal Beast's lair, Mage Arena bank, Wilderness ditch, Edgeville Dungeon, Wilderness Agility Course, PvP world |
| WIKI-MC | Wiki `Module:DeadmanAnnihilationMulticombatZones.json` (GeoJSON, 135 polygons, plane 0, mapID -1, i.e. the surface). It is the map the Wiki's "Multicombat area" page shows for the main game | Saved as `.../scratchpad/wild/Module_DeadmanAnnihilationMulticombatZones.json`. Coordinates are tile **corners** (tile (x,z) covers [x,x+1)×[z,z+1)) |
| RSMOD | RS Mod `fa13b3f` (ISC): `multiway.json`, `MultiwayAreaCacheEnricher`, `PolygonMapSquareBuilder`, `MultiwayAreaScript`, `SinglesPlusAreaScript`, `StandardOverlays` | Our `multiway.json` is an unmodified copy |
| RL | RuneLite `d62b158` (BSD-2): `gameval/VarbitID`, `Varbits`, `InterfaceID.PvpIcons`, `NpcAggroAreaPlugin`, `CombatLevelPlugin`, `PlayerIndicatorsService`, `WikiDpsManager` | Varbit names and client-side wilderness checks |

Raw artefacts (outside the repo): clientscript dumps `.../scratchpad/cs/384_388.txt`, `.../scratchpad/cs/all.txt` (every
script); multi diff `.../scratchpad/wild/multi_diff.txt`; flood fills `.../scratchpad/wild/ferox_flood.txt`,
`.../scratchpad/wild/underground_components.txt`, `.../scratchpad/wild/north_components.txt`.

## 1. The level rule: clientscript 384, transcribed

The client checks these boxes **in order**; the first match wins. All boxes cover all four levels (planes 0..3),
inclusive. `z` is the north coordinate (`coordy` in Jagex naming). Integer division truncates toward zero.

| # | Box x1,z1 – x2,z2 (planes 0-3) | Level | What it is |
|---|---|---|---|
| 1 | 3328,3968 – 3519,4159 **and not** 3413,4053 – 3434,4074 | **5** (constant) | Unknown walled area north-east of the map edge (cache squares 52-54 × 62-64: spear walls, bloodsplatter, beds). The inner box falls through to 0. **OPEN-Z1**: identity unknown, probably not reachable in normal play |
| 2 | 2944,3520 – 3391,4351 | `(z - 3520) / 8 + 1` | **Surface Wilderness.** Note z runs to 4351, not 3967 |
| 3 | 3008,10112 – 3071,10175 | `(z - 9920) / 8 - 1` | **Wilderness God Wars Dungeon** (map square 47,158): 2 lower than the generic underground rule |
| 4 | 3264,10176 – 3327,10239 | **35** | Vet'ion's Rest square (51,159) |
| 5 | 3392,10176 – 3455,10239 | **35** | Silk Chasm square (53,159) |
| 6 | 3328,10304 – 3391,10367 | **40** | Callisto's Den square (52,161) |
| 7 | 1728,11520 – 1791,11583 | **21** | Hunter's End square (27,180) |
| 8 | 1856,11520 – 1919,11583 | **21** | Skeletal Tomb square (29,180) |
| 9 | 1600,11520 – 1663,11583 | **29** | Web Chasm square (25,180) |
| 10 | 3328,10240 – 3391,10303 | `33 + 7 * ((z mod 64) - 6) / 50` | **Escape Caves** square (52,160). Inner division truncates, so the walkable z 10245..10298 gives **33..40** |
| 11 | 2944,9920 – 3455,10879 | `(z - 9920) / 8 + 1` | **Underground Wilderness** (the same as "surface z + 6400"). Note x runs to **3455**, z to 10879 |
| — | anything else | 0 | |

Decoded constants, for checking (packed coord → (level, x, z)): 54529920/862965823 → (0,3328,3968)/(3,3519,4159);
55922645/861573098 → (0,3413,4053)/(3,3434,4074); 48238016/860868863 → (0,2944,3520)/(3,3391,4351);
49293184/855631807 → (0,3008,10112)/(3,3071,10175); 53487552/859826175 → (0,3264,10176)/(3,3327,10239);
55584704/861923327 → (0,3392,10176)/(3,3455,10239); 54536256/860874879 → (0,3328,10304)/(3,3391,10367);
28323072/834661695 → (0,1728,11520)/(3,1791,11583); 30420224/836758847 → (0,1856,11520)/(3,1919,11583);
26225920/832564543 → (0,1600,11520)/(3,1663,11583); 54536192/860874815 → (0,3328,10240)/(3,3391,10303);
48244416/861923967 → (0,2944,9920)/(3,3455,10879).

Box 10 in the client's terms: `iconst 33; coord; coordy; iconst 64; mod; iconst 6; sub; iconst 50; iconst 7; scale; add`.
`scale(a, b, c)` = `c * a / b`, which gives 7·(z%64−6)/50. The other operand order would give levels in the hundreds.

The client draws this number. **It is not the PvP rule by itself**: the server decides who may attack, which teleports
work and when the HUD opens. Several places inside the boxes are not Wilderness (see §4). Still, wherever the server
treats a tile as Wilderness, the level it uses should equal CS384 at that tile. The Wiki numbers agree with CS384
everywhere they are given (table in §2).

## 2. Area table

Legend: Lvl = Wilderness level by CS384 (walkable extent from CACHE-MAP); Multi/Safe from the sources given; "Ours" =
what our code computes now.

| Area | Coordinate box(es) / polygon | Level rule (CS384) | Multi | Safe | Source | Ours |
|---|---|---|---|---|---|---|
| Surface Wilderness (all planes) | 2944,3520 – 3391,4351 (walkable to z 3967; see §5 for z > 3967) | (z−3520)/8+1 → 1..56 at z ≤ 3967 | mixed, §3 | first two rows z 3520..3521 (south half of the ditch, which occupies z 3521..3522) | CS384 #2; WIKI Wilderness:134,140; CACHE-MAP ditch loc 23271/50652 at z 3521, 1×2 | x/z same but **plane 0 only** and z ≤ 3967 |
| Wilderness ditch | locs 23271 (x 2946..2991, 3042..3122, 3136..3327) and 50652 (x 3328..3340), all at z 3521, size 1×2 (z 3521..3522); short N-S piece at x 2996, z 3530..3533 | — | — | — | CACHE-MAP; WIKI Wilderness_ditch (map line 2946,3522 – 3341,3522) | not modelled (not needed) |
| Ferox Enclave | walled; barriers at W x3123 z3628-3629 (west wall), E x3154 z3634-3635 (east wall), S x3134-3135 z3617 (south wall), N x3134-3135 z3639 (north wall); walkable plane-0 interior = 414 tiles in bbox 3123..3155 × 3617..3645 (rows in §6); upper floors 3125..3146 × 3618..3646 (planes 1-2) | 13..16 (CS384 #2; WIKI "level 13-16") | single (not in WIKI-MC or RSMOD) | **Yes**: "players cannot kill other players while within the boundaries of the town"; teleblocked players *can* be attacked inside and may retaliate | WIKI Ferox_Enclave:28-30; CACHE-MAP | **not safe at all** (no Ferox check) |
| Mage Arena (Kolodion's arena, surface) | ~3093..3117 × 3921..3946 (plane-1 parts 3082..3128 × 3912..3954) | 51..53 | single | no ("still considered part of the Wilderness, level 52") | WIKI Mage_Arena | plane 0 ok; plane 1 → 0 |
| Resource Area | polygon 3174,3944 · 3174,3925 · 3175,3924 · 3196,3924 · 3197,3925 · 3197,3944 · 3196,3945 · 3175,3945 | 51..53 | single (outside both multi datasets) | no | WIKI Wilderness_Resource_Area | ok |
| Wilderness Agility Course | yard ≈ 2988..3008 × 3931..3966; south part 2984..3007 × 3912..3927 multi; underground pit 2992..3006 × 10340..10364 | 50..56 (pit 53..56) | yard **singles-plus**; south part multi | no | WIKI Singles-plus_combat, Multicombat_area; RSMOD | yard + south ok (edge off-by-one, §3); pit singles-plus **OPEN-Z2** |
| Edgeville Dungeon (north half) | walkable 3078..3134 × 9920..9973, 3115..3127 × 9969..9999 | (z−9920)/8+1 → 1..10 | single | no | CS384 #11; WIKI Edgeville_Dungeon ("1 - 5") | rows z 9920..9921 → **0** (safe rows applied underground) |
| Revenant Caves | square-aligned 3136..3263 × 10048..10239; walkable 3145..3261 × 10054..10234 | 17..40 | **singles-plus** ("entirely") | no | CS384 #11; WIKI Revenant_Caves:16 | level ok; **multi=true** (multiway.json entry "Revenant caves singles-plus"), so singles-plus never activates |
| Wilderness Slayer Cave | 3328..3455 × 10048..10175 (4 squares); walkable 3330..3451 × 10050..10171 | 17..32 | multi | no | CS384 #11; WIKI ("17 to 32", "multi-combat"); RSMOD 4 boxes | **x 3392..3455 → level 0** (isUnderground stops at 3391) |
| Wilderness God Wars Dungeon | square 3008..3071 × 10112..10175; walkable 3016..3066 × 10116..10165 | **(z−9920)/8−1 → 23..29** | main cavern multi, entrance cavern single (RSMOD polygon: 3008,10112 · 3071,10112 · 3071,10135 · 3063,10135 · 3063,10143 · 3055,10143 · 3055,10151 · 3047,10151 · 3047,10175 · 3008,10175) | no | CS384 #3; WIKI WGWD ("23 - 29"; "This empty cavern is a single-way combat zone") | **25..31** (generic rule) |
| Lava Maze Dungeon (east) | walkable 3013..3059 × 10243..10290 | 41..47 | single ("entirely single-way") | no | CS384 #11; WIKI Lava_Maze_Dungeon | ok |
| Lava Maze Dungeon (west, KBD lever room) | near 3069,10255 (exact OPEN-Z3) | ≈42 | single | no | WIKI | ok via the generic rule |
| King Black Dragon Lair | 2240..2303 × 4672..4735 (walkable 2255..2287 × 4680..4711) | 0 | multi | **not PvP** ("not a PvP area once inside") | WIKI Wilderness:606, KBD_Lair; RSMOD | ok (0, multi) |
| Deep Wilderness Dungeon | walkable 3014..3049 × 10306..10347, 3018..3069 × 10332..10364, 2992..3006 × 10340..10364 | 49..56 | single ("entirely single-way") | no | CS384 #11; WIKI DWD ("49 to 56") | ok |
| Scorpia's lair | RSMOD 3216..3247 × 10328..10351; walkable 3219..3246 × 10332..10351 | 52..54 (Wiki's "53-55" is the Scorpion Pit on the surface) | multi | no | CS384 #11; WIKI Scorpion_Pit ("both inside and outside ... multi-combat") | level ok; multi misses row z 10351 (§3) |
| Callisto's Den | square 3328..3391 × 10304..10367; walkable 3350..3367 × 10317..10336 | **40** | multi | no | CS384 #6; WIKI Callisto's_Den `wilderness = 40` | **42** (entrance level); lair box starts at x 3351 |
| Vet'ion's Rest | square 3264..3327 × 10176..10239; walkable 3285..3304 × 10190..10214 | **35** | multi | no | CS384 #4; WIKI `wilderness = 35` | **34**; box starts at x 3286 |
| Silk Chasm | square 3392..3455 × 10176..10239; walkable 3410..3436 × 10190..10217 | 35 | multi | no | CS384 #5; WIKI `wilderness = 35` | 35 ok |
| Hunter's End (Artio) | square 1728..1791 × 11520..11583; walkable 1750..1767 × 11533..11552 | **21** ("entrance ... level 20, the cave itself is in level 21") | singles-plus | no | CS384 #7; WIKI | **20**; box starts at x 1751 |
| Skeletal Tomb (Calvar'ion) | square 1856..1919 × 11520..11583; walkable 1877..1896 × 11534..11558 | 21 | singles-plus | no | CS384 #8; WIKI | 21 ok; box starts at x 1878 |
| Web Chasm (Spindel) | square 1600..1663 × 11520..11583; walkable 1618..1644 × 11534..11561 | 29 | singles-plus | no | CS384 #9; WIKI ("interior adjusted to level 29") | 29 ok |
| Escape Caves | square 3328..3391 × 10240..10303; walkable 3331..3387 × 10245..10298 | **33 + 7·((z%64)−6)/50 → 33..40** (Wiki prose says "30-42") | multi | no | CS384 #10; WIKI Escape_Caves | **41..48** (generic rule); box starts at x 3332 |
| Abyss | 2944..3135 × 4736..4863 (RSMOD polygon with notch; walkable outer ring 2956..3124 × 4745..4849, inner 3011..3066 × 4804..4858) | 0 | multi (outer ring) | not PvP (outside every CS384 box) | RSMOD; WIKI Abyss | ok |
| Corporeal Beast's lair | 2960..2999 × 4368..4399 (+4240..4271) | 0 | multi | "the lair itself is not in the Wilderness" | WIKI; RSMOD | ok |
| Mage Arena bank | 2496..2559 × 4672..4735 (walkable 2497..2519 × 4684..4729, 2529..2547 × 4711..4723) | 0 | RSMOD marks multi | safe (not PvP) | WIKI Wilderness:24, Mage_Arena_bank | ok |
| Chaos Temple (hut, east of Chaos Temple church) and Chaos Temple (church) | church multi: RSMOD 2944..2959 × 3816..3831, WIKI-MC 2945..2959 × 3816..3831 | 38 | multi (the church in the west, "not the Chaos Temple in the eastern Wilderness") | no | WIKI Multicombat_area; WIKI-MC | edge off-by-one (§3) |
| Upper floors in the surface Wilderness | plane 1: 2946..2957 × 3817..3824 (Chaos Temple church), 3025..3033 × 3628..3635 (Dark Warriors' Fortress), 3038..3044 × 3949..3959 (DWD house), 3082..3128 × 3912..3954 (Mage Arena / Deserted Keep), 3126..3146 × 3618..3646 (Ferox), 3278..3292 × 3926..3939 (Rogues' Castle); plane 2: 3038..3043 × 3950..3958, 3125..3143 × 3618..3639 (Ferox) | same formula (CS384 boxes cover planes 0-3) | OPEN-M4 (both multi datasets are plane-0 only) | Ferox floors safe | CS384; CACHE-MAP | **level 0** on every upper floor |

## 3. Multi-combat inside and around the Wilderness

### 3a. Our polygon semantics drop the east column and the north row of every RS Mod polygon

`PolygonAreas` (plugin-api/src/main/kotlin/tp/plugin/PolygonAreas.kt:32-38) tests the **tile centre** against the
vertices, so it treats vertices as tile corners. RS Mod's `multiway.json` vertices are **tile coordinates** (inclusive).
`PolygonMapSquareBuilder.fillPolygon` fills `ceil(prevX)..floor(cx)`, inclusive in x, from vertex tiles. WIKI-MC (corner
coordinates) agrees that the edge tiles are multi:

- Main Wilderness east edge: WIKI-MC is multi at **x 3327, z 3520..3839** (RS Mod vertex x 3327). We stop at 3326.
- Main Wilderness west-part top edge: WIKI-MC is multi at **z 3903, x 3008..3199** (RS Mod vertex z 3903). We stop at 3902.
- Wildy bandit camp (actually the Dark Warriors' Fortress / Bandit Camp box): WIKI-MC covers tiles 3008..3071 ×
  3600..3711 (corners 3008,3600 – 3072,3712), which is the RS Mod box taken inclusively. We cover 3008..3070 × 3600..3710
  (175 tiles missing).
- The same holds at every other max edge (Scorpia row z 10351, Slayer Cave x 3455 / z 10175, etc.).

Fix: treat vertices as inclusive tiles. One way is a point-in-polygon test with the max edges pushed out by one; a
cleaner way is to rasterise at load time like RS Mod, x inclusive and **z inclusive** (WIKI-MC also has row 3903, which
RS Mod's own `zMax = z2 - 1` would drop).

### 3b. Revenant Caves are listed as multi

`multiway.json` has an entry `"Revenant caves singles-plus"` (3136,10048 · 3263,10048 · 3263,10239 · 3136,10239, levels
0-3), which RS Mod loads into the **multiway** area. The Wiki says the caves are "exclusively a singles-plus combat
area". The only multi exception there is the Revenant maledictus itself, which "can still be attacked by multiple
players". Effects in our code:

- `Wilderness.isMulti` is true in the caves (Wilderness.kt:172), so the multi icon (varbit 4605) shows there;
- `inSinglesPlus = !inMulti && …` (Wilderness.kt:67) is never true, so varbit 5961 never shows;
- `PvpCombat` gives no PJ protection in the caves (PvpCombat.kt:61);
- combat's multi-target attacks work in the caves (world attr `MULTI_AREA`, Wilderness.kt:21).

### 3c. Surface polygon: RS Mod vs Wiki GeoJSON (plane 0, x 2900..3460, z 3500..4000)

Tile counts: WIKI-MC 89,964 multi tiles, ours 92,905. Wiki-only 1,880, ours-only 4,821.

Wiki multi, ours single (inside or touching the Wilderness):

| Tiles | Region | Cause |
|---|---|---|
| 473 | x 3327..3345, z 3520..3839 (column x 3327, plus a wedge 3327..3345 × 3520..3530 at the SE ditch corner) | §3a edge + extra wedge |
| 192 | z 3903, x 3008..3199 | §3a edge |
| 175 | 3008..3071 × 3600..3711 edges | §3a edge |
| 45 + 8 + 6 + 3 + 2 | around the Graveyard of Shadows step edge 3146..3191 × 3593..3647 (e.g. 3151..3186 × 3619..3625, 3174..3191 z 3647, 3136..3151 z 3583) | Wiki polygon is one tile further out at each step |
| 40 | 2984..3007 × 3912..3928 (south of the agility course) | WIKI-MC polygon (2984,3912)…(3008,3929) is larger |
| 38 + 8 + 6 | 3047..3056 × 3856..3895; 3021..3024 × 3854..3855 | different notch shape near the Lava Maze |
| 30 | 2945..2959 × 3816..3831 (Chaos Temple church) | edge |
| 3 | 3364..3366 z 3967 | edge |

Wiki single, ours multi:

| Tiles | Region |
|---|---|
| 2,232 | 3343..3390 × 3840..3932 (east of Callisto's Den entrance, the lava area west of the Fountain of Rune). WIKI-MC traces walkable cliff and lava edges and leaves this out; the Fountain of Rune itself (≈3354..3385 × 3880..3900) is multi in both |
| 1,951 | 3277..3363 × 3907..3966 (east of Rogues' Castle / Scorpion Pit fringe) |
| 296 | 3366..3390 × 3936..3966 |
| 158 | 3200..3274 × 3963..3966 (north map edge) |
| 15 | x 2944, z 3816..3830 (Chaos Temple church west column) |
| 12 | 3362..3365 × 3875..3877: WIKI-MC has a **hole** ring 3362,3875 – 3366,3878 (a singles island inside multi) |
| 11, 10, 3 | 3050..3054 × 3864..3867; 3183..3186 × 3632..3635; 3149..3150 × 3620..3621 (step shapes) |

Outside the Wilderness (for completeness): Wiki-only 2903..2942 × 3780..3836 (Ice Plateau / Weiss approach, a 2-ring
feature), 2911..2919 × 3688..3759 (Trollheim), 2900..2903 × 3520..3543 (Burthorpe edge), 2928..2943 × 3512..3519
(Chaos Temple north of Falador, edge). Ours-only 2900..2910 × 3760..3774.

WIKI-MC also has small separate multi features in the NE that RS Mod covers with its big box: 3360..3367 × 3846..3852,
3353..3361 × 3851..3860, 3369..3371 × 3868..3870, 3379..3384 × 3869..3874, 3348..3351 × 3892..3895, 3380..3383 ×
3898..3902, 3384..3389 × 3913..3918, 3329..3333 × 3922..3926, 3336..3343 × 3943..3950, 3386..3391 × 3953..3958,
3336..3341 × 3957..3962 (corner coordinates).

**OPEN-M3:** neither dataset is Jagex's (multi areas are server-side, no client data). WIKI-MC is newer, tile-traced
and plausibly reflects the 28 March 2019 change ("A few isolated single-way combat tiles in the Wilderness have been
made multi-way combat"). RS Mod's boxes look hand-drawn. Most plausible: take WIKI-MC for the surface Wilderness
(x 2944..3391, z 3520..3967) and keep RS Mod's underground boxes (with the §3a fix and without the Rev Caves entry),
behind a config flag (e.g. `multiSource = WIKI|RSMOD`) until it is checked in game with the multi icon.

Underground multi (WIKI-MC has no underground data):

| Area | Multi? | Data |
|---|---|---|
| Wilderness Slayer Cave | yes | RSMOD 4 boxes 3328..3455 × 10048..10175 |
| Wilderness GWD | main cavern yes, entrance cavern no | RSMOD stepped polygon (§2) |
| Scorpia's lair | yes | RSMOD 3216..3247 × 10328..10351 |
| Callisto / Vet'ion / Silk Chasm lairs, Escape Caves | yes | Lairs.kt boxes (edge fixes in §7) |
| Revenant Caves, Hunter's End, Skeletal Tomb, Web Chasm | **no: singles-plus** | — |
| Lava Maze Dungeon, Deep Wilderness Dungeon, Edgeville Dungeon | no | — |
| KBD lair, Corp lair, Abyss, Mage Arena bank | yes (not Wilderness) | RSMOD |

## 4. Safe zones and non-Wilderness pockets inside the CS384 boxes

- **Ferox Enclave** (normal worlds): safe area, §6. Rule (WIKI Ferox_Enclave:28-30): players cannot kill each other
  inside. A teleblocked player can't enter through the barriers; while teleblocked, "you cannot attack anyone by
  default. However, anyone within your combat level range is able to attack you in this 'safe' zone. You are able to
  retaliate". On PvP worlds "some tiles on the upper floor at the southern end ... are now acting as safe zones"
  (2020+ change).
- **Wilderness ditch rows**: z 3520..3521 safe (W2). Our reading, consistent with CS384 (the box starts at z 3520) and
  with the ditch loc (z 3521..3522): the Wilderness technically starts at z 3520, the two safe rows are 3520 (south of
  the ditch) and 3521 (the ditch's southern tile), and z 3523 is the first walkable attackable row. RuneLite's
  `NpcAggroAreaPlugin` uses 2944..3391 × 3523..3970 above ground and 2944..3263 × 9918..10359 underground. Those are
  approximations, not authoritative.
- **Pockets inside CS384 box #11 that are not Wilderness** (the client would compute a level if the HUD were open):
  Bryophyta's lair / Varrock Sewers north end 3200..3235 × 9920..9951 (levels 1..4); instance-template squares 46-47 ×
  155 (x 2944..3071, z 9920..9983: spotlights, braziers, "Pyromancer"); squares under Ferox 3135..3197 × 9990..10046
  (a "Soul Wars Portal" loc at 3156,10025, "Opening" at 3162,10042). **OPEN-Z4**: these must stay non-Wilderness
  (server-side exclusion). Our current `isUnderground` (Wilderness.kt:166-167) **includes all of them**: Bryophyta's
  lair is attackable at level 1-4 in our server.
- **North of the map edge in box #2** (z 3968..4351, x ≤ 3391): walkable pockets at 3153..3301 × 4243..4333 would get
  levels 91..102. They look like instance templates. OPEN-Z5: keep the server's surface Wilderness at z ≤ 3967 (current
  behaviour); the client box is larger only because Jagex rounds to whole squares.

## 5. Client side: varbits, varps, interface

| Id | Name (gameval) | Who reads it | What our code does |
|---|---|---|---|
| varbit 4605 | `multiway_indicator` | CS386 (icon position), client multi icon | set by Wilderness.updateArea (Wilderness.kt:64), but wrongly 1 in the Rev Caves |
| varbit 5961 | `singleway_plus_indicator` | CS386 | set (Wilderness.kt:70), but never 1 in the Rev Caves (§3b) |
| varbit 5963 | `inside_wilderness` | CS2874 (K/D stats panel: hidden unless `kd_toggle` 4143 ≠ 0 and inside_wilderness = 1 or proc 2871); RuneLite `PlayerIndicatorsService` (PvP highlight), `WikiDpsManager` (`inWilderness` buff) | **never set** |
| varbit 8121 | `pvp_area_client` | CS2611; RuneLite `PlayerIndicatorsService`, `StatusBarsPlugin` | **never set** (PvP worlds outside safe zones) |
| varbit 13027 | `combatlevel_transmit` | CS387 `pvp_icons:comlevelrange` text = `max(3, cb − L)`–`min(126, cb + L)`, with L = level (+15 if varp 3717 bit 2) | **never set**, so the range text is computed from 0 |
| varbit 10530 | `wildy_hub_buffer` | CS388: 1 shows "Level: --" | not used (OPEN-Z6: purpose unknown) |
| varbit 5954 | `deadman_inwilderness` | CS388 (Deadman worlds only) | n/a |
| varp 3717 | `map_flags_cached` | procs 1138/6941/6942/5548: world-type bits; bit 2 = PvP world (adds 15 in CS387), bits 26/29/30 = Deadman/seasonal modes | **never set** (PvP-world range text would show the plain level) |
| varc 78 | `deadman_insidesafezone` | CS388/386 ("Guarded"/"No PvP") | Deadman only |
| interface 90 | `pvp_icons` (`wildernesslevel` = 90:50, `comlevelrange` = 90:49, `skullduration` = 90:51, `icons` = 90:44) | CS386 builds, CS385 timer refreshes | opened at login as a standard overlay (content/gameframe Gameframe.kt:53), like RS Mod `StandardOverlays` |

How the HUD gets its level: on a normal world CS388 sets the text to `"Level: " + proc384()` when 384 > 0, else `""`
(or `"Level: --"` if varbit 10530 = 1). This is **purely client-side from the player's coordinates**. On a normal world
no CS386 branch hides interface 90 based on location (only the `if_gettop = 80` and special-world branches hide parts).
If the overlay is open everywhere, the client therefore prints "Level: N" wherever CS384 is non-zero, including the
non-Wilderness pockets in §4 (Bryophyta's lair, z 3520 south of the ditch).

**OPEN-H1:** does OSRS keep `pvp_icons` open everywhere, or open it on Wilderness entry and close it on exit? RS Mod
opens it at login. Mechanically, the client logic fits open/close on entry: otherwise "Level: 1" would show in
Bryophyta's lair and south of the ditch. Resolve with an RSProx capture (docs/CLIENT.md) of walking across the ditch.
Watch for IF_OPENSUB/IF_CLOSESUB of interface 90 and the VARBIT 5963 writes.

## 6. Ferox Enclave safe area (plane 0, flood fill from 3138,3630 inside the barriers; walls and closed doors stop it)

414 tiles, rows `z: x-runs`:

```
3617: 3131-3132 3134-3135 3137-3138        3632: 3129 3131-3137 3141-3142 3145 3148-3151 3154
3618: 3128-3130 3132-3137 3139-3141        3633: 3126 3132-3135 3137 3140-3143 3146-3148 3150-3151
3619: 3128-3129 3133-3136 3138-3141        3634: 3125-3127 3130 3133-3137 3140-3143 3149-3154
3620: 3128-3129 3133-3136 3138-3141        3635: 3125 3127-3137 3140-3144 3149-3154
3621: 3127-3139                            3636: 3127-3137 3140-3143 3147-3148 3150-3151 3155
3622: 3127-3139 3145-3146                  3637: 3127-3131 3133-3136 3140-3143 3147-3151 3154-3155
3623: 3127-3129 3133-3136 3138-3141 3145   3638: 3126-3127 3134-3135 3141-3143 3147-3155
3624: 3128-3129 3133-3136 3138-3141 3145   3639: 3126 3134-3135 3147-3155
3625: 3128-3130 3132-3136 3139-3141 3145-3146  3640: 3147-3148 3150-3155
3626: 3132-3136 3140-3142 3146-3147 3151   3641: 3150-3151
3627: 3124 3127-3152                       3642: 3150-3152
3628: 3123-3154                            3643: 3150-3152
3629: 3123-3154                            3644: 3150-3152
3630: 3127-3154                            3645: 3152
3631: 3128-3137 3140-3142 3145 3148-3154
```

Building interiors behind closed doors (bank, Clan Wars, Last Man Standing, chapel, pub) are **not** in the fill and
belong to the town too. A flood from outside the west barrier (3122,3628) reaches 3122..3124 × 3617..3623 and
3141..3162 × 3617..3627, so the south-west and south-east corners of the bbox are outside. **OPEN-F1:** the exact Jagex
safe-area polygon is not published. Most plausible: the union of (a) this fill, (b) every tile inside the wall ring
x 3123..3155 × 3617..3646 that the outside flood cannot reach, on planes 0-2. That is computable with the same
`cache-dump` flood (seed outside, invert within the bbox).

## 7. Gap list (file:line, then the exact correct value)

1. **Surface level is plane-0 only.** `content/pvp/src/main/kotlin/tp/content/pvp/Wilderness.kt:158`
   `c.level == 0 && …`. CS384 box #2 covers planes 0-3. Drop the `c.level == 0` check, so the upper floors in §2 get
   `(z−3520)/8+1` (Rogues' Castle L1 = 51..53, Dark Warriors' Fortress L1 = 14..15, Mage Arena/Deserted Keep L1 =
   50..55, Ferox L1/L2 = 13..16 but safe).
2. **Underground x range too narrow.** Wilderness.kt:166-167 / :143-146: `isUnderground` uses x 2944..3391.
   Correct: **x 2944..3455** (CS384 #11). This fixes the Slayer Cave east half (x 3392..3451, levels 17..32, now 0).
   Keep z capped where real content ends (z ≤ 10367 is fine; CS384 goes to 10879 but nothing walkable lies there).
3. **Special-case boxes missing.** Wilderness.kt:151-160: implement CS384 in order instead of
   "Lairs entrance level, else generic":
   - WGWD 3008..3071 × 10112..10175 → `(z − 9920)/8 − 1` (23..29; ours now 25..31);
   - Escape Caves 3328..3391 × 10240..10303 → `33 + 7*((z % 64) − 6)/50` with truncating division (33..40; ours 41..48);
   - Callisto square 3328..3391 × 10304..10367 → **40**; Vet'ion square 3264..3327 × 10176..10239 → **35**; Silk Chasm
     square 3392..3455 × 10176..10239 → 35; Hunter's End 1728..1791 × 11520..11583 → **21**; Skeletal Tomb
     1856..1919 × 11520..11583 → 21; Web Chasm 1600..1663 × 11520..11583 → 29.
4. **Lair levels from entrance tiles are wrong for three lairs.** `content/pvp/src/main/kotlin/tp/content/pvp/Lairs.kt:285`
   (`levelAt` = level of `entranceTile`) with entrances at :199 (3291,3849 → 42), :212 (3115,3676 → 20),
   :225 (3221,3787 → 34). Correct: Callisto's Den **40**, Hunter's End **21**, Vet'ion's Rest **35** (CS384; WIKI
   infoboxes). Replace BL7 "entrance level" with the CS384 constants. Hunter's End's own page says "entrance ... level
   20, the cave itself is in level 21".
5. **Lair/escape boxes one tile short on the west.** Lairs.kt:200 (Callisto x 3351), :226 (Vet'ion x 3286), :213
   (Hunter's End x 1751), :239 (Skeletal Tomb x 1878), :293 (Escape Caves x 3332). The walkable floor starts at x
   **3350, 3285, 1750, 1877, 3331**. Better: use the CS384 map-square boxes for "inside the lair" (each lair fills its own
   square), which also makes `onLogin` relocation (Lairs.kt:46) and the multi/singles-plus checks match.
6. **Revenant Caves counted as multi.**
   `content/pvp/src/main/resources/tp/content/pvp/multiway.json`, entry `"Revenant caves singles-plus"`
   (3136,10048 – 3263,10239). Remove it from the multi set (keep the box only for singles-plus). Knock-on lines:
   Wilderness.kt:67 (singles-plus gate), :172 (`isMulti`), PvpCombat.kt:61 (PJ skipped). Correct: Rev Caves = singles-plus,
   multi = false, except multi-attackability of the Revenant maledictus NPC itself (WIKI Singles-plus_combat).
7. **Polygon edge semantics.** `plugin-api/src/main/kotlin/tp/plugin/PolygonAreas.kt:32-38` (centre test against
   vertices as corners). Correct: vertices are inclusive tile coordinates (RS Mod builder; WIKI-MC confirms x 3327 and
   z 3903 are multi). Missing tiles today include column x 3327 (z 3520..3839), row z 3903 (x 3008..3199), column x 3071
   and row z 3711 of the bandit camp box, row z 10351 at Scorpia, x 3455 / z 10175 of the Slayer Cave, and z 3967 of
   the main box.
8. **Surface multi shape differs from the Wiki** (OPEN-M3, §3c): about 4,650 tiles in the NE (3200..3390 × 3840..3966)
   are multi for us and single per WIKI-MC. The singles hole 3362..3365 × 3875..3877 is missing. Proposed: load WIKI-MC
   polygons (GeoJSON, corner semantics, even-odd per feature, union across features) for plane 0 of the surface
   Wilderness, behind a flag.
9. **Underground mapping catches non-Wilderness pockets.** Wilderness.kt:166-167. Correct: exclude Bryophyta's lair
   3200..3235 × 9920..9951 (and the rest of Varrock Sewers z ≥ 9920 if any), squares 46-47 × 155 (2944..3071 ×
   9920..9983) and 3135..3197 × 9990..10046 (under Ferox) from the Wilderness (OPEN-Z4: exact server list unknown; an
   allow-list of the real dungeons from §2 is safer than a deny-list).
10. **Safe rows applied underground.** Wilderness.kt:152 + :158: the `SAFE_ROWS` check runs after mapping z − 6400,
    so the Edgeville Dungeon rows z 9920..9921 are level 0. CS384 gives 1. The Wiki rule is about the surface ditch.
    Correct: no safe rows underground (OPEN-W4 if a gate tile needs it).
11. **Ferox Enclave not safe.** Wilderness.kt:23-46 (AttackCheck): no Ferox check. Correct: deny attacks when attacker or
    target is inside the Ferox safe area (§6), except attacks *on* a teleblocked player (who may then retaliate). Level
    stays 13..16 for the HUD (CS384). Also stop teleblocked players at the barriers (`Barrier` locs 39652/39653).
12. **`inside_wilderness` varbit never set.** Wilderness.kt:55-59: set `Varbits.INSIDE_WILDERNESS` (5963) = 1 on entry,
    0 on exit, in the same area-queue job as the Attack op.
13. **`combatlevel_transmit` never set.** The server must set varbit 13027 to the player's combat level (on login and
    every combat-level change) for `pvp_icons:comlevelrange`. Owner: wherever combat level is recomputed (gameframe/combat).
14. **PvP worlds: client flags missing.** Set varp 3717 (`map_flags_cached`) bit 2 on PvP worlds (CS387 adds 15 only
    then) and varbit 8121 (`pvp_area_client`) = 1 outside PvP-world safe zones.
15. **PvP-world +15 leaks into non-combat rules.** Wilderness.kt:52-54 stores `level(coord, isPvpWorld)` (with +15)
    into `SharedAttrs.WILDERNESS_LEVEL`, which Teleports.kt:90 uses for the level-20/30 limits. Per WIKI Wilderness:142,
    "Death mechanics are still based on the actual Wilderness level without this 15 level modifier". Correct: keep
    `WILDERNESS_LEVEL` = real CS384 level and use `level + 15` only for the attack bracket. OPEN-P1: whether teleport
    limits on PvP worlds use the real level (most plausible: yes).
16. **HUD open policy.** content/gameframe Gameframe.kt:53 opens `pvp_icons` at login. OPEN-H1 (§5): if OSRS
    opens/closes it on Wilderness entry/exit, move the open/close into Wilderness.updateArea (area queue), or else the
    client shows "Level: 1-4" in Bryophyta's lair and "Level: 1" at z 3520.
17. **Agility course underground pit** (2992..3006 × 10340..10364, levels 53..56) is not singles-plus in our code
    (Wilderness.kt:117 is plane 0, surface only). OPEN-Z2: the Wiki says "The area is now Singles-plus combat"
    without coordinates; most plausible: include the pit.
18. **docs/mechanics/wilderness.md OPEN-W1** can be resolved: the bounds and formula are CS384 (§1). W14
    ("underground = surface 6,400 tiles north") holds for box #11 but not for the GWD, the Escape Caves or the lairs.
19. **Rev Caves `inCaves` box** (RevenantCaves.kt:80, x 3136..3265) runs 2 columns past the caves' square (x ≤ 3263);
    harmless today (nothing walkable there), but align with 3136..3263 × 10048..10239.

## OPEN

- **OPEN-Z1** CS384 box #1 (3328..3519 × 3968..4159 = level 5, inner 3413..3434 × 4053..4074 = 0): area unidentified.
- **OPEN-Z2** Agility course pit singles-plus coordinates.
- **OPEN-Z3** KBD lever room exact tiles in the Lava Maze Dungeon (level by the generic rule either way).
- **OPEN-Z4** The exact server-side list of non-Wilderness pockets inside the underground box.
- **OPEN-Z5** Walkable pockets north of z 3967 inside box #2 (instance templates?).
- **OPEN-Z6** Purpose of varbit 10530 `wildy_hub_buffer` ("Level: --").
- **OPEN-M3** Surface multi polygons: Wiki GeoJSON vs RS Mod (§3c).
- **OPEN-M4** Multi on upper floors (both datasets plane 0 only).
- **OPEN-F1** Ferox safe-area polygon including building interiors and upper floors.
- **OPEN-H1** `pvp_icons` open at login vs on Wilderness entry.
- **OPEN-P1** PvP-world teleport limits: real vs +15 level.
- **OPEN-W4** Whether any underground row is "safe" like the ditch rows.
