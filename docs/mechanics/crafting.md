# Crafting

`content/skills/.../Crafting.kt`, `CraftingData.kt`; every recipe is a row of `recipes.tsv` (OSRS Wiki `{{Recipe}}` templates,
`tools/wiki-import/recipes.py`, DECISIONS D-038). Shared rules (make-X menu, production batches, xp, messages): skilling.md
SK, MU, PR. Levels and xp below are the Wiki's `skill1lvl` / `skill1exp` (rounded to tenths).

- **CR1: how an action starts.** The player picks a product in the make-X menu (skilling.md MU) after one of:

  | Family | Start | Menu type | Animation | Ticks (first, then each) |
  |---|---|---|---|---|
  | leather, dragonhide, snakeskin | needle or thread used on the hide | Make | `human_leather_crafting` 1249 | 3, 3 |
  | gems | chisel used on an uncut gem | Cut | per gem (below) | 2, 2 |
  | spinning | "Spin" (op 2) on, or wool / flax / sinew / roots on, a spinning wheel | Spin | 894 | 2, 3 |
  | pottery shaping | "Use" (op 1) on, or soft clay on, a potter's wheel | Make | `human_potterywheel` 883 | 3, 3 |
  | pottery firing | "Fire" (op 1) on, or an unfired item on, a pottery oven | Fire | 883 | 7, 7 |
  | glassblowing | pipe used on molten glass | Make | `human_glassblowing` 884 | 3, 3 |
  | molten glass | soda ash or a bucket of sand on a furnace | Make | `human_furnace` 899 | 2, 2 |
  | jewellery, silver | gold or silver bar on a furnace | Make | 899 | 3, 3 |
  | stringing | ball of wool on an unstrung amulet / symbol | String | none | 2, 2 |

  The menu lists only the products the player has the tools and materials for right now (at most 18); the choice is
  validated again for each action. Locs: the cache locs named "Spinning wheel" (op "Spin"), "Potter's Wheel" (op "Use"), "Pottery
  Oven" (op "Fire") and the furnaces of smithing.md SM1. Ticks: leather 3 (Wiki "Pay-to-play Crafting training": "Every leather
  item is created in 3 ticks"; the Hardleather body template says 2 and is overridden: OPEN-CR1), gem cutting 2 ("Uncut opal"
  Recipe), pottery 3 / 7 and glass 3 / 2 (Recipe `ticks`), stringing 2 ("Free-to-play Crafting training": "only takes two ticks").
- **CR2: leather.** Needle (tool, kept) plus thread plus leather. Thread is used up one spool per 5 actions, not per leather: the
  fifth action on a spool removes one thread and sends "You use up a reel of your thread." (Wiki "Thread": "Each thread spool has 5
  uses before it is consumed"; the use counter is kept per player and not saved, OPEN-CR3). A new spool needs a thread in the
  backpack. Products (level, xp, hides): leather gloves (1, 13.8, 1 leather), boots (7, 16.3), cowl (9, 18.5), vambraces (11, 22),
  body (14, 25), chaps (18, 27), coif (38, 37), hard leather body (28, 35, 1 hard leather); green / blue / red / black d'hide
  vambraces (57 / 66 / 73 / 79; 62 / 70 / 78 / 86; 1 hide), chaps (60 / 68 / 75 / 82; 124 / 140 / 156 / 172; 2 hides), body
  (63 / 71 / 77 / 84; 186 / 210 / 234 / 258; 3 hides); snakeskin boots (45, 30, 6 snakeskin), vambraces (47, 35, 8), bandana (48, 45, 5),
  chaps (51, 50, 12), body (53, 55, 15). Not modelled: studded armour, spiky vambraces, shields, yak-hide, snelms, other hide sets
  (OPEN-CR2).
- **CR3: gem cutting.** Chisel (kept) on an uncut gem, one gem per action. The jeweller's chisel is not modelled.

  | Gem | Level | xp | Animation | Crush |
  |---|---|---|---|---|
  | opal | 1 | 15 | `human_opalcutting` 890 | 3.8 xp, cut chance `((L-1) x 122 / 98 + 129) / 256` |
  | jade | 13 | 20 | 891 | 5 xp, `((L-1) x 145 / 98 + 101) / 256` |
  | red topaz | 16 | 25 | 892 | 6.3 xp, `((L-1) x 150 / 98 + 91) / 256` |
  | sapphire | 20 | 50 | 888 | never |
  | emerald | 27 | 67.5 | 889 | never |
  | ruby | 34 | 85 | 887 | never |
  | diamond | 43 | 107.5 | 886 | never |
  | dragonstone | 55 | 137.5 | 885 | never |
  | onyx | 67 | 167.5 | 2717 | never |

  A crush (L = current Crafting level) uses up the gem, gives a crushed gem and the crush xp, and sends "You mis-hit the chisel and
  smash the opal into pieces!" (Wiki "Gems" for the text; "Uncut opal" / "Uncut jade" / "Uncut red topaz" for the formulas, from Mod Ash,
  13 Jan 2019). The chance is compared as a real number against a roll out of 256 (rounding is OPEN-CR4). Zenyte is not modelled.
- **CR4: spinning** (Wiki "Crafting" Spinning table, "Crossbow string" Recipe note): ball of wool (1, 2.5, 1 wool), bow string (10, 15, 1
  flax), crossbow string (10, 15, 1 sinew or tree roots), magic string (19, 30, 1 magic roots). The first item takes 2 ticks, then 3 each
  ("Make-all is 2 tick, then 3 ticks"; "Bow string": 28 in 50.2 s = 83.7 ticks = 2 + 27 x 3). The yarn, rope and Sailing variants are
  not modelled.
- **CR5: pottery** (Wiki "Crafting" Pottery table). Shaping at a potter's wheel, 1 soft clay each: pot (1, 6.3), pie dish (7, 15), bowl (8,
  18), plant pot (19, 20), pot lid (25, 20); firing at a pottery oven, 1 unfired item each: pot (1, 6.3), pie dish (1, 10), bowl (1, 15),
  empty plant pot (1, 17.5), pot lid (1, 20). Firing has no failure chance. Cups (four per clay) are not modelled. The player firing
  animation is 883 like the wheel's (RuneLite `AnimationID.CRAFTING_POTTERY_OVEN` 24975 is not in this cache; OPEN-CR5).
- **CR6: glass.** Molten glass: 1 soda ash and 1 bucket of sand on a furnace give 1 molten glass and return the empty bucket, 20 xp
  (Wiki "Molten glass" Recipe; the returned bucket is from the Wiki "Crafting" Glass text, "use a bucket of sand and soda ash in a
  furnace"). Glassblowing (pipe kept, 1 molten glass each): beer glass (1, 17.5), empty candle lantern (4, 19), empty oil lamp (12, 25),
  vial (33, 35), unpowered orb (46, 52.5), lantern lens (49, 55), empty light orb (87, 70). The fishbowl page has no parseable recipe
  and the oil lantern and light orb are two-item recipes: not modelled.
- **CR7: jewellery at a furnace.** One gold bar plus the mould (kept) plus, for gem pieces, one cut gem. Gold: ring (5, 15), necklace (6, 20),
  bracelet (7, 25), amulet (u) (8, 30); with a gem: sapphire ring / necklace / bracelet / amulet (20 / 22 / 23 / 24; 40 / 55 / 60 / 65), emerald
  (27 / 29 / 30 / 31; 55 / 60 / 65 / 70), ruby (34 / 40 / 42 / 50; 70 / 75 / 80 / 85), diamond (43 / 56 / 58 / 70; 85 / 90 / 95 / 100),
  dragonstone (55 / 72 / 74 / 80; 100 / 105 / 110 / 150), onyx (67 / 82 / 84 / 90; 115 / 120 / 125 / 165). Silver bar: unstrung holy symbol
  (16, 50), silver sickle (18, 50), tiara (23, 52.5). The pieces the player can make with what is carried are listed (up to 18); the
  gold and silver crafting interfaces (446 and 6, with their mould-needed labels) are not driven (OPEN-CR6). The amulets and the symbol
  are strung with a ball of wool for 4 xp each (Wiki "Free-to-play Crafting training": "Each amulet strung awards 4 crafting
  experience").
- **CR8: timeline and ending.** As skilling.md PR: the animation plays at the choice, the first product lands after "first" ticks, then
  every interval; walking, clicking or opening an interface ends it; a missing tool or material ends it with a message; a level-up ends it.

- **CR9: elemental battlestaves and studded leather.** An elemental orb (water 571, earth 575, fire 569, air 573) used on a battlestaff
  (1391) opens the Make menu: water battlestaff 54 / 100 xp, earth 58 / 112.5, fire 62 / 125, air 66 / 137.5; 2 ticks, the first after 3 ("Make-X is 3t, then
  2t", Wiki "Water battlestaff"). Steel studs on a leather body or chaps make studded body (41 / 40 xp) and chaps (44 / 42); 3 ticks. Any recipe
  of two materials with no tool, thread, wool or facility works this way. No animation (none is sourced).
- **CR10: amethyst.** A chisel on an amethyst (21347) opens the Cut menu: bolt tips (83 / 60 xp, 15), arrowtips (85, 15), javelin tips (87, 5),
  dart tips (89, 8); 2 ticks each, no animation.
- **CR11: silver gem jewellery.** Opal, jade and topaz (red topaz) rings, necklaces, bracelets and amulets (u) at a furnace from a silver bar
  (levels 1 to 38, rows in `recipes.tsv`), their strung amulets with a ball of wool, and the empty fishbowl (42 / 42.5) with the glassblowing pipe.

## OPEN

- **OPEN-CR1:** the Wiki's Hardleather body Recipe says 2 ticks while every other leather item and the P2P training page say 3; 3 is used.
- **OPEN-CR2:** leather and hide products beyond CR2 (studded, spiky vambraces, shields, yak, snelms, crab, splitbark, mixed hide,
  hueycoatl), cups, yarn / rope / loom weaving, the oil lantern / light orb / fishbowl, silver gem jewellery and bolts, battlestaves
  and the amethyst, zenyte and golem crafting are not implemented.
- **OPEN-CR3:** the spool use counter is not saved; costume needle and the jeweller's chisel (10% double cut, no crushes from level
  62 / 71 / 76) are not modelled.
- **OPEN-CR4:** rounding of the gem cut chance (the Wiki gives the real-valued formula).
- **OPEN-CR5:** pottery oven animation (883 assumed).
- **OPEN-CR6:** the jewellery interfaces 446 / 6 are replaced by the make-X list; 18 entries at most (a player with every mould and every
  gem sees the lowest-level 18).
- **OPEN-CR7:** success messages ("You cut the opal.") and the "no materials" lines are unsourced; the thread, crush and ring texts are
  sourced above.
- **OPEN-CR8:** only the first action offset of spinning is sourced (CR4); the others assume one interval (OPEN-PR1).
- **OPEN-CR9:** the battlestaff rows are used with the plain menu; the Wiki's account setting, orb charging prerequisites and the elemental
  upgrade to mystic staves are not modelled; the amethyst cutting has no animation. Zenyte jewellery and uncut zenyte, the slayer ring, studded
  vambraces and the other leather items of OPEN-CR2 are still missing (no importer pages were added for them), as are the golem, goblin lamp and
  light-orb filling recipes; "Fishbowl (water)" (a Water facility row) has no handler.
