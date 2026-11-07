# Hunter

Code: `content/skills/.../Hunter.kt` (bird snares, box traps, butterflies, implings), `HunterBirdhouses.kt`, `HunterStaticTraps.kt`
(deadfalls, net traps), `HunterFalconry.kt`, `HunterRumours.kt`, `HunterPitfalls.kt`, `HunterLootSacks.kt`; data in `HunterData.kt` and the resource table `hunter.tsv` (written by
`tools/wiki-import/hunter_creatures.py`, re-runnable). Shared skilling rules (success roll SK1): skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (H) "Hunter" (multiple traps by level, bait and smoking, techniques), https://oldschool.runescape.wiki/w/Hunter
- (B) "Bird snare" and "Box trap" (creature lists, "Mechanics": "an attempt every 3 ticks ... within a 2-tile radius ... won't trap prey if
  players are standing on the trap", prohibited areas), https://oldschool.runescape.wiki/w/Box_trap
- (C) each creature's page: `{{Hunter info}}` (level, xp), `{{Skilling success chart}}` (`low` / `high` of the catch roll),
  `{{DropsLineSkill}}` (loot), e.g. /w/Crimson_swift, /w/Chinchompa_(Hunter) (the chart for the three chinchompas), /w/Baby_impling
- (T) "Tick manipulation" (laying "uses the skilling timer and has a default duration of four ticks"; picking up a trap "takes three
  ticks and stalls the player"), https://oldschool.runescape.wiki/w/Tick_manipulation
- (N) "Butterfly net" / "Impling" (a net wielded, an empty jar, barehanded needs 10 more levels, the magic net adds about 8
  percentage points: the chart's second line), https://oldschool.runescape.wiki/w/Impling
- (BH) "Bird house trapping" with its subpages "/Table" (levels, xp) and "/Seeds", https://oldschool.runescape.wiki/w/Bird_house_trapping ; "Bird house (item)" (recipe, 2 ticks)
- (BI) "Bird house (item)", Changes 12 Feb 2025 / 18 Apr 2019 (the single-click build, pre-made houses first)
- (SE) "Bird house trapping/Seeds"
- (DF) "Deadfall" (set-trap, two traps, 8 ticks, 75% log, the creature table), https://oldschool.runescape.wiki/w/Deadfall
- (NT) "Net trap" (state locs, 29 / 47 / 59 / 67 / 79, "reverts to item form after approximately a minute"), https://oldschool.runescape.wiki/w/Net_trap
- (FA) "Falconry", (M) "Matthias", "Falconer's glove", https://oldschool.runescape.wiki/w/Falconry
- (HR) "Hunters' Rumours" (tiers, creature table, drop rates and pity, xp formula), "Hunter Guild", https://oldschool.runescape.wiki/w/Hunters%27_Rumours
- (PF) "Pitfall trap" (pit states, tease and jump, "will not jump the same pit twice in a row", multiple traps, creature table) and the creature pages
  "Spined larupia", "Horned graahk", "Sabre-toothed kyatt", "Sunlight antelope", "Moonlight antelope" (levels, xp, Always drops, "Common" furs,
  the antelopes "always succeed", the 27 second respawn), https://oldschool.runescape.wiki/w/Pitfall_trap
- (MO) "Sunlight moth", "Moonlight moth" (level, xp, jar, `Skilling success chart` barehanded or net / magic net), "Butterfly jar"
- (LS) "Hunters' loot sack (basic)", "(adept)", "(expert)", "(master)" (rolls, rows, herb and log cascades, tertiary drops, torn blueprints),
  https://oldschool.runescape.wiki/w/Hunters%27_loot_sack_(adept)
- (TR) "Tracking" (kebbit nodes, Nmin, creatures), "Noose wand"; (HB) "Herbiboar" (xp table, herb chart, guaranteed tracks, trail rules);
  (RS) "Rabbit snare", "White rabbit" (no chart, ferret saving chance 80.9% .. 100%)
- (RL) RuneLite `HerbiboarSearchSpot` / `HerbiboarStart` / `TrailToSpot` / `HerbiboarPlugin` (BSD-2, read only for the layout of the trail varbits and
  the end tunnels; no code was copied, see DECISIONS D-065), raw files in `runelite-client/src/main/java/net/runelite/client/plugins/herbiboars/`
- (L) the cache (cache 2757): obj 10006 `hunting_ojibway_bird_snare` and 10008 `hunting_box_trap` with the "Lay" op; the trap locs 9344-9348
  (bird snare: set 9345 "Dismantle" / "Investigate", failed 9344 "Dismantle" / "Reset", full 9348 "Check" / "Reset"), 9380, 9382, 9383,
  721, 9385 and the trapping locs 9386, 9390, 2025 (box trap); the "Catch" op on butterfly and impling npcs; seqs `hunting_setting_trap_small`
  5212, `human_hunting_dismantle_net` 5207, `human_butterflynet_swing` 5209

## Rules

| # | Rule | Source |
|---|---|---|
| HU1 | "Lay" on a bird snare or box trap stops the player for 4 ticks (animation 5212 for the snare, the tinderbox animation for the box trap: Wiki "the animation used for laying a box trap is identical to that of the tinderbox"), then removes the item and puts the trap loc (shape 10, angle 0) on the player's tile. It is refused if the player already has as many traps as their base Hunter level allows (1: 2, 40: 3, 60: 4, 80: 5; one more while in the Wilderness) or if the tile's centrepiece layer is taken. The checks are made again after the delay. | H, B, T, L |
| HU2 | A trap belongs to the player who laid it; another player's "Dismantle" / "Check" answers "This isn't your trap." The limit counts every trap of the player (snares and boxes together, OPEN-HU4). A logout removes the player's traps from the world. | H |
| HU3 | Every 3 ticks from the tick it was laid, a set trap looks for a creature of its kind (bird snare: birds, box trap: chinchompas) within 2 tiles, same level, in the world. It does nothing while its owner stands on the trap's tile. The first creature in spawn order is the target. | B |
| HU4 | The catch is `statRandom(HUNTER, low, high)` of the creature's Skilling success chart, and always fails below the creature's Hunter level ("If the player's Hunter level is too low, the trap will always fail"). The chinchompa chart has `low` below zero for red and black; SK1 handles that. | C, B |
| HU5 | A catch removes the creature (it respawns at its spawn after 25 ticks, OPEN-HU3), shows the "trapping" loc for 3 ticks (OPEN-HU2) and then the full loc (bird snare 9348, grey chinchompa 9382, red 9383, black 721). A failed roll turns the trap into its failed loc (bird snare 9344, box 9385); the creature stays. A set trap collapses to the failed loc 100 ticks after it was laid or reset (OPEN-HU1); a creature that is lured on the final tick is decided first and so delays the collapse (Wiki "Black chinchompa": "begins to enter a Box trap on the final tick, it will delay the boxes collapse"). | C, B, L |
| HU6 | "Check" on a full trap (op 1) stalls the player 3 ticks, gives the creature's loot (the `Always` rows: bones, raw bird meat and 5-10 feathers for a bird; the chinchompa for a box) and Hunter xp, and returns the trap item; "Reset" (op 2) does the same but lays the trap again (a new 100-tick life) instead of returning it. "Reset" on a failed trap lays it again; "Dismantle" on a set or failed trap returns the item. All of it is one item transaction; with no room the trap stays and the player is told. | T, L |
| HU7 | "Catch" on a butterfly or impling in `hunter.tsv`: the player needs the Hunter level, a butterfly net in the weapon slot (`hunting_butterfly_net` or `ii_magic_butterfly_net`) and an empty jar (butterfly jar 10012, impling jar 11260) in the backpack. The attempt repeats every 3 ticks (OPEN-HU6) while the player stays; the roll is the chart's first line, or its second with the magic net. A catch swaps the empty jar for the filled one (black warlock, or the impling's jar), gives the xp, removes the creature (respawn 25 ticks) and ends the action. | N, C |

| HU20 | Bird houses: the four spaces of Fossil Island are the multi-locs 30565 .. 30568 (placements 3677,3882 / 3679,3815 / 3768,3761 / 3763,3755) that the client draws from the varps `BIRDHOUSE_TRANSMIT_A..D` (1626 .. 1629): value 0 the empty space, then three values per tier (cache loc 30565 `multiLocs`; tier 0 regular .. 8 redwood). The state of each space lives in `saved` (so it keeps running offline) and the varp is written on login and after every change. | BH, L |
| HU21 | Building: logs used on a clockwork with a chisel and a hammer make the tier's bird house (Crafting level 5 / 15 / 25 / 35 / 45 / 50 / 60 / 75 / 90 for regular, oak, willow, teak, maple, mahogany, yew, magic, redwood; xp 15 .. 55; 2 ticks). "Build" on an empty space (or the house item used on it) places the best pre-made house the player's Hunter level allows (Hunter level 5 / 14 / 24 / 34 / 44 / 49 / 59 / 74 / 89), else crafts one from the best logs they can use first. | BH, BI |
| HU22 | Seeds: 10 seeds, or 5 for wildblood, ranarr, toadflax, irit, avantoe, kwuarm, snapdragon, cadantine, lantadyme, dwarf weed and torstol; hop, allotment, flower, bush and low herb seeds count as 10 (the Wiki's table, matched by item name; noted seeds do not count). "Seeds" (or a seed used on the house) takes them from the backpack; the house then fills for 50 minutes (OPEN-HU9). | BH, SE |
| HU23 | Emptying a full house: Hunter xp 112 / 168 / 224 / 280 / 369 / 480 / 612 / 969 / 1200 by tier, the clockwork back, 10 raw bird meat on the ground, 30 / 40 / 50 / 60 feathers, at most one seed nest (`statRandom(HUNTER, 0, 200)`: 0% at level 1, 78.5% at 99) and 5 rolls of the nest table with a per-roll chance of 30 / 37.5 / 38.4 / 39 / 42 / 45 / 48 / 51 / 52.5% by tier at level 99 (halved at level 50 and below, OPEN-HU11): ring nest 32/100, each egg nest 1/100, empty 65/100 (32/95, 1/95, 60/95 with a strung rabbit foot). Items that do not fit are dropped at the player's feet. | BH |
| HU24 | Emptying early (before 50 minutes) asks first, then loses the birds and seeds but keeps the clockwork; "Dismantle" on a built, unseeded house returns the house; "Reset" on a full house does HU23 and then builds and places the next house (HU21) at once. | BH |
| HU28 | Deadfalls: "Set-trap" on a boulder (cache loc 19215) needs a knife (or fletching knife), any log but redwood and arctic pine and Hunter 23; it takes 4 ticks, keeps the log 3 times in 4, and a player may have 2 deadfalls. The set trap (19217) tries for a creature from tick 8 and then every 3 ticks (range 2: wild, barb-tailed, prickly and sabre-toothed kebbit, pyre fox). A catch shows the creature's boulder (20648 .. 20651, 50726); "Check" gives bones, meat and fur (hunter.tsv), the xp, and clears the boulder; a failed catch drops the boulder and the trap is gone (OPEN-HU15). | DF |
| HU29 | Net traps: "Set-trap" on a young tree (the five `HUNTING_SAPLING_UP_*` locs) or the net trap item (34631) used on it; the Hunter level of the salamander of that colour (swamp lizard 29, orange 47, red 59, black 67, tecu 79). The trap counts against the same per-level limit as snares and boxes (HU1). It tries every 3 ticks within 2 tiles, never while the owner stands on it. A catch shows the full net (Check = loot, xp and the item back; Reset = the same and set again); a failure shows the failed net (Reset, Dismantle). A trap not used for 100 ticks reverts to the item on the ground for another 100 (OPEN-HU1, HU15). | NT |
| HU35 | Falconry: Matthias (npc 1340 / 1341) gives a falconer's glove with a gyr falcon (obj 10024; 10023 is the empty glove) for 500 coins, for ever for 500,000; "Quick-falcon" skips the talk. "Catch" on a spotted (Hunter 43), dark (57) or dashing (69) kebbit with the glove worn: after the falcon's flight (2 ticks plus one per tile, OPEN-HU19) the catch is `statRandom(HUNTER, low, high)`. A catch empties the glove and a falcon npc waits at the kebbit's tile; "Retrieve" gives bones, meat and fur (what does not fit is discarded), the xp and the falcon back. After 100 ticks the falcon leaves ("Your falcon has left its prey. You see it heading back toward the falconer."). | FA, M |
| HU40 | Hunters' Rumours: the Guild Hunters (npcs 13121 .. 13126, "Talk-to" or "Rumour") need Hunter 46 / 57 / 57 / 72 / 72 / 91 (base level). They assign one of the creatures that this server can catch from the Wiki table, each hunter its own list, never a creature already assigned by another hunter; the player's Hunter level must reach the creature's. | HR |
| HU41 | While a creature is assigned, each of its catches (by any method of this plugin) may drop the creature's rare part (`hunter.tsv` `part`): 1/20 bird snare, 1/50 box trap, 1/40 butterfly net, 1/15 deadfall, 1/10 falcon, 1/25 net trap; for sure after 40 / 100 / 80 / 30 / 20 / 50 catches (38 / 94 / 76 / 28 / 18 / 46 in the whole guild hunter outfit). Holding a part blocks another; a full backpack drops a normal part on the ground and destroys a pity part. The message is "You find a rare piece of the creature! You should take it back to the Hunter Guild." | HR |
| HU42 | Handing the part in (talk to the hunter): Hunter xp (base level + 5) * 50 (novice, adept), 55 (expert) or 60 (master) and the tier's loot sack (objs `HG_LOOTSACK_T0..T3`); the sack is opened with "Open" (HU47). | HR |
| HU43 | Moths: the sunlight moth (npc 12770, Hunter 65, xp 74, jar 28890) and the moonlight moth (npcs 12771 .. 12773, Hunter 75, xp 84, jar 28893) are `hunter.tsv` net creatures: "Catch" with a butterfly net and an empty butterfly jar, as HU7, with the charts 20 / 296 (magic net 40 / 316) and 0 / 276 (20 / 286). The wings are their Rumour parts (1/40, HU41). | MO |
| HU44 | Pitfalls: the 25 pits of the map are multi-locs 19253 .. 19268 and 51673 .. 51681 whose varbits `HUNT_PITFALL_STATE1..25` (2958 .. 2973, 9767 .. 9775) are per player: 0 empty ("Trap" is op 3), 1 set ("Jump" op 1, "Dismantle" op 2), 3 / 4 a creature in the pit ("Dismantle" op 2). Pits 1 .. 6 are the kyatt's (Hunter 55), 7 .. 11 the larupia's (31), 12 .. 16 the graahk's (41), 17 .. 25 the antelopes' (72 east of x 1700, else 91). "Trap" needs the creature's Hunter level, a knife (or fletching knife) and logs (the cheapest, not redwood or arctic pine), takes 4 ticks, uses one log and counts against the per-level trap limit (the same table as HU1). | PF, L |
| HU45 | "Tease" (a teasing stick in the backpack or a hunter's spear in hand) angers the creature for 30 ticks (OPEN-HU25). "Jump" over a set pit carries the player across; if an angry creature is within 8 tiles it follows over the pit and `statRandom(HUNTER, low, high)` decides whether it falls in (state 3) or crosses (it will not cross the pit it just refused, so that pit is skipped). A creature that falls is away for 45 ticks (antelopes: Wiki 27 seconds) or 25. The antelopes always fall in; the large cats have no chart (OPEN-HU24). A set pit that is not used collapses after 100 ticks, pits are cleared on login and logout. | PF |
| HU46 | "Dismantle" a pit with a creature: 3 ticks, then big bones, the raw meat, the fur (cats: undamaged or tatty, OPEN-HU26) or antler, fur and sunfire splinters 2-6 (sunlight antelope), the Hunter xp (180 / 240 / 300 / 380 / 450) and the Rumour part chance (1/15, pity 30 / 28 like deadfalls); the pit is empty again. "Dismantle" on a set pit empties it (no refund, OPEN-HU27). | PF |
| HU47 | Hunters' loot sacks: "Open" on a sack removes one and rolls the tier's table 5 / 7 / 9 / 11 times (basic, adept, expert, master); every row of the table has the same chance (1/7, 1/12, 1/15, 1/15 with the herb and log rows counted). Rows: quetzal feed 1, blessed bone shards 100-200, hunter spear tips 15-30, noted raw kyatt 2, raw pyre fox 3, coins 750-1250; from adept noted bird nest (empty) 1 and 2-4, noted sun-kissed bones 2, noted raw sunlight antelope 2, four noted herbs and four noted logs; from expert noted sun-kissed bones 3, noted raw moonlight antelope 2, coins 2500-3500. All items are added in one transaction; with no room the sack stays. | LS |
| HU48 | The herb row is a cascade on the current Herblore level (best first, the first success wins: lantadyme from level 34 -30 / 60, cadantine from 13 -10 / 70, kwuarm 10 / 85, avantoe 20 / 100, irit 30 / 115, ranarr 10 / 170, tarromin 70 / -20, harralander sure); the log row the same on Woodcutting (magic from 50 -60 / 60, yew from 36 -50 / 90, mahogany from 24 -40 / 130, maple 0 / 160, teak sure). | LS |
| HU49 | Tertiary rolls, once per adept, expert or master sack (the basic sack has none), each 1/50: enhanced quetzal whistle blueprint; the perfected one (expert, master) once the enhanced is owned; the huntsman's kit (not if owned); one guild hunter piece in the order hood, top, legs, boots (the first not owned, else the hood). A torn enhanced blueprint is given by the first such sack opened with 100 rumours handed in, a torn perfected one at 250 if no perfected blueprint is owned. | LS |

### Creatures in `hunter.tsv` (level / xp / catch `low` / `high`)

| Creature | Trap | Lvl | Xp | Low / high |
|---|---|---|---|---|
| Crimson swift | bird snare | 1 | 34 | 100 / 420 |
| Golden warbler | bird snare | 5 | 47 | 92 / 400 |
| Copper longtail | bird snare | 9 | 61.2 | 85 / 390 |
| Cerulean twitch | bird snare | 11 | 64.5 | 82 / 380 |
| Tropical wagtail | bird snare | 19 | 95.2 | 75 / 370 |
| Chinchompa (grey) | box trap | 53 | 198.4 | 6 / 268 |
| Carnivorous chinchompa (red) | box trap | 63 | 265 | -78 / 228 |
| Black chinchompa | box trap | 73 | 315 | -78 / 228 |
| Black warlock (butterfly) | net | 45 | 54 | 20 / 296 (magic net 40 / 316) |
| Sunlight moth / Moonlight moth | net | 65 / 75 | 74 / 84 | 20 / 296 (40 / 316) and 0 / 276 (20 / 286) |
| Spined larupia / Horned graahk / Sabre-toothed kyatt | pitfall | 31 / 41 / 55 | 180 / 240 / 300 | none on the Wiki (OPEN-HU24) |
| Sunlight antelope / Moonlight antelope | pitfall | 72 / 91 | 380 / 450 | "always succeed" |
| Baby / young / gourmet / earth / essence / eclectic / nature / magpie / ninja / dragon / crystal / lucky impling | net | 17 / 22 / 28 / 36 / 42 / 50 / 58 / 65 / 74 / 83 / 80 / 89 | Gielinor xp: 20 / 22 / 24 / 27 / 29 / 32 / 36 / 216 / 240 / 300 / 280 / 380 | see the file |

## OPEN

- **OPEN-HU1: trap lifetime.** The Wiki gives none; 100 ticks (`TRAP_LIFETIME_TICKS`). No collapse message is sent.
- **OPEN-HU2 / HU3 / HU6: timings.** How long the catching animation shows (3 ticks), how long a caught creature is away (25 ticks) and
  the net cadence (3 ticks) are not on the Wiki; they are named constants. The "trapping" locs are the north variant only.
- **OPEN-HU4: shared limit.** The Wiki lists bird snaring, box trapping, net trapping and rabbit snaring under one table; whether
  the count is shared is unstated. It is shared here.
- **OPEN-HU5: quest and area rules.** Box traps need partial completion of Eagles' Peak and cannot be laid indoors or in the listed
  areas (Wiki "Box trap": Taverley, Wintertodt, banks, ...). There is no quest state and no indoor flag, so neither is checked.
- **OPEN-HU7: creatures without a sourced chance.** Ferrets (no success chart), ruby harvest, snowy knight and sapphire glacialis
  ("Needs skilling success chart"), embertailed jerboas, letveks and maniacal monkeys (Kruk's Dungeon) are not implemented; the
  sunlight and moonlight moths are (HU43). Deadfalls, net traps, falconry, bird houses and pitfalls are (HU20 .. HU46); rabbit
  snares, tracking and the Herbiboar are OPEN-HU29, HU18 and HU21.
- **OPEN-HU8: extras.** Baiting (+3%) and smoking (+2%), the Hunters' Rumours tufts, baby chinchompa pets, Puro-Puro (impling xp and
  a jar being required there; the Gielinor xp is used everywhere), catching implings barehanded or without a jar (loot at once),
  opening impling and butterfly jars, and the camouflage/guild outfit bonuses are not implemented. Message texts that the Wiki does
  not give ("You can't have more than N traps laid at your Hunter level.", "This isn't your trap.", ...) are plausible, not sourced.

### Added OPEN items (bird houses, deadfalls, net traps, falconry, rumours)

- **OPEN-HU9: bird house timing and loot amounts.** The Wiki says "an average of once every 5 minutes, up to 10" birds and "around 50
  minutes"; a house is full after exactly 50 real minutes here (`FILL_MINUTES`, epoch minutes, so it also runs offline). The feathers are
  one of 30 / 40 / 50 / 60 chosen evenly (the Wiki's `quantity=30,40,50,60`).
- **OPEN-HU10: the house variants.** The multi-loc lists three variants per tier. By their ops: 3t+1 "Birdhouse (empty)" has only "Seeds"
  (unused), 3t+2 has "Seeds" and "Dismantle" (used: built, no seeds), 3t+3 has "Interact / Seeds / Empty / Reset" (used: seeded, also when
  full, because the client cannot tell them apart; RuneLite shows the timer itself).
- **OPEN-HU11: nest chance.** Mod Ash (via the Wiki): the per-roll chance "scale[s] down by half at level 50" and is "constant" below; the
  linear rise between 50 and 99 is an assumption. Checked again on the Wiki (Nov 2026): its chart repeats the nine per-roll values and "the rate
  is constant at levels below 50", still without the shape between 50 and 99.
- **OPEN-HU12: seeds in several uses and clue nests.** The seeds go in at once (all 10 points must be in the backpack), not over several
  uses. Clue nests are rolled from the Wiki's base rates (1/1500 elite, 2 hard, 3 medium, 4 easy, 30 beginner, tried in that order after
  every successful nest roll, not given if that level's nest is in the backpack or bank); the Combat Achievement bonus is not applied
  and the scroll box replacement after X Marks the Spot is not made (no Combat Achievement or quest state). The Wiki's "up to 5
  attempts" wording is read as one try per tier per successful nest roll.
- **OPEN-HU13: bird house details.** 2 ticks to place, seed or dismantle (no source); no Fossil Island access, ironman Crafting rule or
  seed-warning setting; with several log types the best usable one is taken without a prompt.
- **OPEN-HU14: Hunters' Rumours extras.** No quest gates (At First Light for master, Priest in Peril for swamp lizards, Bone Voyage for the
  Herbiboar, Eagles' Peak for box trap creatures), no back-to-back setting (Guild Scribe Verity), no tattered request notes or milestone
  unlocks (cooking hunter meat, quetzal whistles); loot sacks are opened since HU47 (the Wiki tables are in `HunterLootSacks.kt`); the pity count is
  shared by all creatures; "wearing the outfit" means all four pieces; the creature is picked evenly from the eligible list.
- **OPEN-HU15: deadfall and net trap details.** Net traps replace the tile's young tree loc (same shape and rotation) with the state locs
  instead of adding a second loc, which the client may draw differently; the "setting" locs (8733 ...) are not shown. A deadfall that
  fails drops its boulder and disappears (the Wiki gives no failed state); "Dismantle" on a deadfall returns nothing. A net trap that
  collapses puts the item on the ground for 100 ticks. Baiting, smoking, Kandarin headgear (double logs) and logs from the log basket are
  not implemented. Deadfalls are limited to 2 (not by level) and do not count against the level limit; net traps do.
- **OPEN-HU17: pitfalls (resolved by HU44 .. HU46).** The state varbits, locs and creatures are in the cache; the Wiki's "Pitfall trap"
  gives the order of play, the levels, xp and loot. What it lacks is OPEN-HU24 .. HU27 below.
- **OPEN-HU18: tracking.** Noose-wand kebbits (polar, common, feldip, desert, razor-backed) are not implemented. The Wiki "Tracking" gives
  per creature the den, the connectors, the hiding spot, the number of nodes (4 / 11 / 10 / 8 / 12) and N_min (3 / 5 / 5 / 5 / 5: a
  path "will try to end once N_min nodes (including the starting node) have been discovered and is guaranteed to end at the first hiding
  spot encountered at or beyond this step", "a path may only use a track segment once, however a node may be encountered twice", "at a
  given node each possible track segment has exactly one connector"), the xp (30 / 36 / 48 / 66 / 348), the loot (bones, raw beast meat, kebbit
  fur; the razor-backed kebbit a long kebbit spike) and the Rumour chance (1/15, pity 30 / 28). The graph itself (which node borders which, which
  connector belongs to which segment) is only in the map images ("Polar Kebbit Trail Map.png" ...), and the trail varbits `HUNTING_TRAIL_STATE1..8_*`
  (2974 .. 3061) with the `HUNTING_TRAILSTART_*` / `HUNTING_TRAIL_ENDS_*` varbits show only that the client draws footprints from them. No
  source for the graph exists in text, so the plugin waits for it. Needed: the node tables (positions, neighbours) from the maps or from
  a client observation, then the rules above are enough to generate paths.
- **OPEN-HU19: falconry timings.** The flight time (2 ticks plus one per tile) and the wait at the prey (100 ticks) are not on the Wiki.
- **OPEN-HU20: falconry area.** The glove is not taken away when leaving the Piscatoris falconry area (no area test), it goes into the
  backpack (not auto-equipped, and weapons are not unequipped), Matthias' talk is a menu, and bonecrusher / meat pouch / fur pouch
  collection is not implemented.
- **OPEN-HU21: Herbiboar.** Not implemented. The Wiki (HB) gives: Hunter 80 (boostable) and Herblore 31; the start objects (a rock, mushroom or
  driftwood, 5 places: loc 30519 .. 30523 `HUNTING_TRAIL_SPAWN_FOSSIL1..5`, op "Inspect"); the search objects (muddy patch, seaweed, normal
  and smelly mushroom: locs 30533 ..); the tunnel (loc 30532, ops "Attack", "Search"); a failed trail gives "The creature has
  successfully confused you with its tracks, leading you round in circles. You'll need to start again." (no chance is stated);
  every inspection of the trail "can grant 50 experience" and some fossil drops (table of
  `FossilDropLines`, not imported); the kick gives 1,950 xp at Hunter 80, +30 a level to 94 (2,370), 2,385 at 95 and +19 a level to 99
  (2,461); the herbs 1 - 3 (2 - 4 with magic secateurs in backpack or worn), 25 Herblore xp for each herb after the first, the herb kind from
  a cascade on Herblore (ranarr -10 / 20, torstol -70 / 20, snapdragon -60 / 20, dwarf weed -50 / 30, lantadyme -30 / 40, cadantine -10 / 50,
  kwuarm 10 / 60, avantoe 20 / 60, irit 30 / 70, tarromin 70 / -20, harralander 100 / -30, marrentill 170 / -40, guam 225 / 324, all from
  31), the pet Herbi 1/6500, the Rumour tuft 1/7 (pity 14 / 12). The trail graph is in RuneLite (RL): the search spots come in 11 groups
  (A .. K) of one or two objects; each of the 24 trail varbits (`HUNTING_TRAIL_STATE9_0 .. 12_?` = 5737 .. 5777, 3 bits, values 0 .. 7) is a
  segment of footprints between two spots, and the object whose "value" matches is the one the footprints point to:
  `9_0`: start -> C mushroom; `9_1`: start -> A patch; `9_2`: start -> F patch; `9_3`: C patch / D seaweed; `9_4`: C patch / B seaweed; `9_5`:
  A mushroom / B seaweed; `9_6`: A mushroom / E patch; `9_7`: F mushroom / I patch; `9_8`: F mushroom / G patch; `9_9`: D patch / H west
  seaweed; `10_0`: D patch / G mushroom; `10_1`: B seaweed -> tunnel; `10_2`: B seaweed / D seaweed; `10_3`: E mushroom / F mushroom; `10_4`: E
  mushroom / I patch; `10_5`: I mushroom / K patch; `10_6`: I mushroom -> tunnel; `10_7`: G mushroom -> tunnel; `10_8`: H east seaweed / J patch;
  `10_9`: H east seaweed -> tunnel; `11_0`: H west seaweed -> tunnel; `11_1`, `11_2`: K patch -> tunnel; `11_3`: J patch -> tunnel (the first name has
  RuneLite value 1, the second value 2). The footprint locs are `HUNTING_TRAIL9_0 ..` (31303 .. : straight, corner, mirrored corner per
  varbit) whose multi-loc shows footprints at varbit 3 / 4 (two directions) and fades them at 5 / 6, while RuneLite compares the varbits with
  1 and 2. Not known and so not implemented: which start rock begins at which spot, how a segment chooses between its two ends, which
  of the 9 tunnels (`HUNTING_TRAIL_ENDS_FOSSIL` 1 .. 9 = RuneLite's end list) each ending leads to, the failure chance, and the exact
  varbit values the server writes (the cache and RuneLite disagree on 1 / 2 against 3 / 4). A client observation (RSProx log of a trail)
  would settle these.
- **OPEN-HU22: outfits and salamander weapons.** The larupia, graahk and kyatt outfits are cache items only; no worn bonus is
  implemented. Salamanders as weapons are combat and stay with the main session (see the task report).

### Added OPEN items (pitfalls, loot sacks, rabbit snares)

- **OPEN-HU23: moths.** Catching a moth barehanded (Hunter 75 / 85, no jar, no better chance: Wiki "Sunlight moth" trivia), the stat and
  hitpoint restoring when one is caught without a jar or a jarred moth is opened (6 + 20% of the drained stats, 8 hitpoints, 5% run energy),
  the moth mixes and the other butterflies without a chart (HU7) are not implemented; the moths need the net and a jar.
- **OPEN-HU24: catch chance of the large cats.** The pages of the spined larupia, horned graahk and sabre-toothed kyatt are in "Needs
  skilling success chart"; the Wiki says only that the creature "has a chance" to be caught. Written as `-` in `hunter.tsv` and read as
  always (255 / 255, `UNCHARTED_ROLL`); the antelopes "always succeed" on their pages.
- **OPEN-HU25: teasing, chasing and jumping.** No chance for "Tease" (hunter's spear: +5%), no length of the anger (30 ticks), no
  distance the creature may follow (8 tiles) and no jump animation or length (the player is carried one tile beyond the pit in 2 ticks
  with `HUMAN_SPOT_JUMP`); the creature does not walk to the pit or hit back (max 5 / 6 / 7, Protect from Melee, hunter gear): that needs
  npc movement and combat. The glitch and "trap trick" of the Wiki (two creatures at one pit) are not reproduced.
- **OPEN-HU26: fur.** "The chance of receiving an undamaged fur scales with Hunter level" (Mod Ash) without numbers: half the catches
  give the undamaged fur, the rest the tatty one.
- **OPEN-HU27: pit extras.** A set pit collapses after 100 ticks (no source, OPEN-HU1); "Dismantle" on a set pit returns no logs;
  logs in the log basket (September 2026 change) are not used; "using logs on a collapsed trap" to set it again is not handled; the
  broav (Wild broav, Hunter 63) pits and the Wiki's quest/area rules are not implemented; the antelope pits 17 .. 25 are told apart by
  x only; the pit's creature level for "Trap" is the area's creature level (the Wiki says "the required Hunter level").
- **OPEN-HU28: loot sack extras.** No Quetzin pet; the 250-rumour torn blueprint is given only if no perfected blueprint is in the
  backpack, bank or worn; the 100- and 250-rumour milestone counts the hand-ins of all tiers; with no free slots the sack is not opened
  (the Wiki does not say whether the loot is dropped).
- **OPEN-HU29: rabbit snares.** "Lay" a rabbit snare (obj 10031, stackable), "Flush" a rabbit hole with a ferret (Hunter 27, Eagles'
  Peak), the rabbit runs to another hole and is caught by a snare on the path; xp 144, loot bones, raw rabbit and a rabbit foot, ferret
  kept with a chance from 75% at level 1 to 100% at 99 (80.9% at 27). Not implemented: the Wiki gives no chart for the catch, no
  rabbit or hole ids and no path rule (which snare tile a rabbit crosses), so there is nothing to run.
