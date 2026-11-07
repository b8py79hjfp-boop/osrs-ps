# Hunter

Code: `content/skills/.../Hunter.kt` (bird snares, box traps, butterflies, implings), `HunterBirdhouses.kt`, `HunterStaticTraps.kt`
(deadfalls, net traps), `HunterFalconry.kt`, `HunterRumours.kt`; data in `HunterData.kt` and the resource table `hunter.tsv` (written by
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
| HU42 | Handing the part in (talk to the hunter): Hunter xp (base level + 5) * 50 (novice, adept), 55 (expert) or 60 (master) and the tier's loot sack (objs `HG_LOOTSACK_T0..T3`); the sack is not opened (OPEN-HU14). | HR |

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
  ("Needs skilling success chart"), embertailed jerboas, letveks, maniacal monkeys (Kruk's Dungeon), moths and rabbit snares (the
  cache has `hunting_snare` locs and rabbit seqs but the Wiki names no creature) are not implemented. Deadfalls, net traps, falconry and
  bird houses are (HU20 .. HU35); pitfalls, tracking and the Herbiboar are OPEN-HU17 .. HU21.
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
  linear rise between 50 and 99 is an assumption.
- **OPEN-HU12: seeds in several uses and clue nests.** The seeds go in at once (all 10 points must be in the backpack), not over several
  uses. Clue nests (1/1500 .. 30/1500, Combat Achievements, scroll boxes after X Marks the Spot) are not rolled: there is no Combat
  Achievement or quest state.
- **OPEN-HU13: bird house details.** 2 ticks to place, seed or dismantle (no source); no Fossil Island access, ironman Crafting rule or
  seed-warning setting; with several log types the best usable one is taken without a prompt.
- **OPEN-HU14: Hunters' Rumours extras.** No quest gates (At First Light for master, Priest in Peril for swamp lizards, Bone Voyage for the
  Herbiboar, Eagles' Peak for box trap creatures), no back-to-back setting (Guild Scribe Verity), no tattered request notes or milestone
  unlocks (cooking hunter meat, quetzal whistles); loot sacks are handed out but not opened (no drop table imported); the pity count is
  shared by all creatures; "wearing the outfit" means all four pieces; the creature is picked evenly from the eligible list.
- **OPEN-HU15: deadfall and net trap details.** Net traps replace the tile's young tree loc (same shape and rotation) with the state locs
  instead of adding a second loc, which the client may draw differently; the "setting" locs (8733 ...) are not shown. A deadfall that
  fails drops its boulder and disappears (the Wiki gives no failed state); "Dismantle" on a deadfall returns nothing. A net trap that
  collapses puts the item on the ground for 100 ticks. Baiting, smoking, Kandarin headgear (double logs) and logs from the log basket are
  not implemented. Deadfalls are limited to 2 (not by level) and do not count against the level limit; net traps do.
- **OPEN-HU17: pitfalls.** Not implemented. Cache: locs `HUNTING_PITFALL_*`, `HUNTER_PITFALL_FULL_*`, varbits `HUNT_PITFALL_STATE1..25` (2958 ..
  2973, 9767 .. 9775) with the base varps 917 / 918 / 4123, npcs 2907 (sabre-toothed kyatt), 2908 (spined larupia), 2909 (horned graahk), 13133
  (sunlight antelope) and 13132 (moonlight antelope) with the op "Tease", the `hunting_teasing_stick`. The pit set, jump and collapse timers,
  the creature's chase and the teasing chance need the Wiki "Pitfall trap" page (fetched, not yet turned into a rule).
- **OPEN-HU18: tracking.** Noose-wand kebbits (polar, common, feldip, desert; razor-backed) need the trail varbits `HUNTING_TRAIL_STATE1..8_*`
  (2974 .. 3061), `HUNTING_TRAILSTART_*` and the footprint locs; none is implemented.
- **OPEN-HU19: falconry timings.** The flight time (2 ticks plus one per tile) and the wait at the prey (100 ticks) are not on the Wiki.
- **OPEN-HU20: falconry area.** The glove is not taken away when leaving the Piscatoris falconry area (no area test), it goes into the
  backpack (not auto-equipped, and weapons are not unequipped), Matthias' talk is a menu, and bonecrusher / meat pouch / fur pouch
  collection is not implemented.
- **OPEN-HU21: Herbiboar.** Not implemented. The tracks are driven by the varbits `HUNTING_TRAIL_STATE9..12_*` (5737 .. 5777),
  `HUNTING_TRAIL_ENDS_FOSSIL` 5766, `HUNTING_TRAILS_USED_FOSSIL` 5767 and `FOSSIL_HERBIBOAR_VISIBLE` 5943; the cache has the npcs 7785
  (op "Harvest") / 7786 and the tunnel, mushroom, rock, log, seaweed and muddy-patch locs, but the Wiki page lists only a few guaranteed
  tracks, not the full trail graph (which object follows which), so a source for it is needed. Known: the xp (1,950 at Hunter 80, +30 a
  level to 94, 2,385 at 95, +19 a level to 99), 1 - 3 herbs (2 - 4 with magic secateurs), 25 Herblore xp per extra herb, and the message
  "The creature has successfully confused you with its tracks, leading you round in circles. You'll need to start again."
- **OPEN-HU22: outfits and salamander weapons.** The larupia, graahk and kyatt outfits are cache items only; no worn bonus is
  implemented. Salamanders as weapons are combat and stay with the main session (see the task report).
