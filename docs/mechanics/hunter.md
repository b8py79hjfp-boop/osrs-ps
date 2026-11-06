# Hunter

Code: `content/skills/.../Hunter.kt`, data in `HunterData.kt` and the resource table `hunter.tsv` (written by
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
  ("Needs skilling success chart"), embertailed jerboas, letveks, kebbits, salamanders, deadfalls, pitfalls, bird houses and every
  tracking creature are not implemented.
- **OPEN-HU8: extras.** Baiting (+3%) and smoking (+2%), the Hunters' Rumours tufts, baby chinchompa pets, Puro-Puro (impling xp and
  a jar being required there; the Gielinor xp is used everywhere), catching implings barehanded or without a jar (loot at once),
  opening impling and butterfly jars, and the camouflage/guild outfit bonuses are not implemented. Message texts that the Wiki does
  not give ("You can't have more than N traps laid at your Hunter level.", "This isn't your trap.", ...) are plausible, not sourced.
