# Mining

`content/skills/.../Mining.kt`, data in `MiningData.kt` (OSRS Wiki, DECISIONS D-036). RS Mod has no mining skill, so every number below
comes from the Wiki pages named in the rule. Shared rules (success roll, clocks, tools, messages): skilling.md.

## Rules

- **MN1: tick timeline.** The same shape as woodcutting (woodcutting.md WC1) with the pickaxe cycle P (MN3) in place of 4:
  - Tick T (click, arrived, action clock expired): both clocks are armed to T+P-1. Nothing else happens.
  - T+1: the pickaxe animation plays and "You swing your pick at the rock." is sent.
  - T+P-1: the first roll (MN2). Rolls then land every P ticks (the clock is re-armed the tick after a roll).
  - The roll cadence is the Wiki's "ticks between rolls"; the first-roll offset is not sourced (OPEN-MN2).
- **MN2: the roll.** Each roll of an ordinary rock is `statRandom(MINING, low, high)` (skilling.md SK1) with the rock's rates; the
  pickaxe does not change the chance, only how often it is rolled. Source: Wiki "Pickaxe" ("Higher-level pickaxes grant more
  frequent opportunities to obtain an ore from a rock, but have no effect upon the chance of successfully doing so").
- **MN3: pickaxes** (Wiki "Pickaxe" > "Ticks between rolls"; levels from the cache `levelrequire` param, SK4):

  | Pickaxe | Mining level | P (ticks) |
  |---|---|---|
  | bronze / iron / steel | 1 / 1 / 6 | 8 / 7 / 6 |
  | black / mithril | 11 / 21 | 5 / 5 |
  | adamant / rune / gilded | 31 / 41 / 41 | 4 / 3 / 3 |
  | dragon (also upgraded, 3rd age, infernal, trailblazer) / crystal | 61 / 71 | 3, 1 roll in 6 is one tick faster / 3, 1 in 4 |

  A fast roll shortens that one cycle to P-1 (Wiki: dragon "3 ticks by default, 1/6 chance to be 2 ticks", crystal "1/4").
  The best usable pickaxe is chosen as in SK4 (worn weapon or backpack).
- **MN4: rocks.** Level, xp, rates (low, high out of 256), respawn. Rates: the Wiki's "Skilling success chart" on each rock page.
  Respawn: the page's Mining info `time` in seconds / 0.6.

  | Rock | Level | xp | low | high | Respawn (ticks) |
  |---|---|---|---|---|---|
  | copper, tin | 1 | 17.5 | 100 | 350 | 4 (2.4 s) |
  | clay | 1 | 5 | 128 | 400 | 2 (1.2 s) |
  | iron | 15 | 35 | 96 | 350 | 9 (5.4 s) |
  | silver | 20 | 40 | 25 | 200 | 100 (1 min) |
  | coal | 30 | 50 | 16 | 100 | 50 (30 s) |
  | gold | 40 | 65 | 7 | 75 | 100 (1 min) |
  | mithril | 55 | 80 | 4 | 50 | 200 (2 min) |
  | adamantite | 70 | 95 | 2 | 25 | 400 (4 min) |
  | runite | 85 | 125 | 1 | 18 | 1200 (12 min) |
  | amethyst | 92 | 240 | -18 | 10 | 125 (75 s) |

  Pages: https://oldschool.runescape.wiki/w/Copper_rocks (and Tin, Clay, Iron, Silver, Coal, Gold, Mithril, Adamantite, Runite
  rocks), /w/Amethyst_crystals. The amethyst pair (-18, 10) reproduces the page's 3.5% at level 92 and 4.3% at level 99.
- **MN5: depletion.** An ore rock turns into its empty rock with the first ore mined and reverts after the respawn time. The empty
  rock is a loc swap (`addLoc` with a duration) that applies to every player. Mining an empty rock sends "There is currently no ore
  available in this rock." (Wiki "Rocks (empty)").
  - Amethyst gives 2 or 3 amethysts before it depletes (Wiki "Amethyst crystals"; the choice is uniform, OPEN-MN4).
  - Essence rocks never deplete (Wiki "Rune Essence (rock)").
  - The player's mining stops when the rock depletes.
- **MN6: essence.** Every attempt succeeds whatever the level, 5 xp, at the pickaxe cycle. Level 30 and above mines pure essence,
  below that rune essence (Wiki "Rune Essence Mine", "Rune Essence (rock)"). Members-only rules are not applied: this server is one
  members world.
- **MN7: gem rocks.** Level 40, 65 xp, rates 28 / 70, respawn 99 ticks (59.4 s). A success gives one gem, weighted out of 128:
  opal 60, jade 30, red topaz 15, sapphire 9, emerald 5, ruby 5, diamond 4 (Wiki "Gem rocks").
- **MN8: sandstone and granite.** Cascading rolls from the heaviest block to the lightest; the first success is the product and
  the xp of that block; all four fail = nothing. Respawn 9 ticks (5.4 s). Level 35 (sandstone) / 45 (granite).

  | Block | xp | low | high |
  |---|---|---|---|
  | sandstone 10 kg / 5 kg / 2 kg / 1 kg | 60 / 50 / 40 / 30 | 4 / 8 / 16 / 25 | 50 / 75 / 100 / 200 |
  | granite 5 kg / 2 kg / 500 g | 75 / 60 / 50 | 6 / 8 / 16 | 64 / 75 / 100 |

  Pages: /w/Sandstone_rocks, /w/Granite_rocks.
- **MN9: gem drop table.** Before each roll of copper, tin, clay, iron, silver, coal, gold, mithril, adamantite, runite and
  amethyst, the player rolls 1 in 256 for the Mining gem drop table. A hit consumes the attempt: no ore, no xp, no depletion. It
  gives nothing (70/128), a sapphire (32), emerald (16), ruby (8) or diamond (2). Source: Wiki "Mining gem drop table"
  (Template:MiningPreGemDropTable, quoting Mod Ash: "It adds a gem roll before each ore roll, and if you receive a gem you don't get
  the ore roll").
- **MN10: requirements and messages.**
  - Level too low: "You need a Mining level of N to mine this rock."
  - Full backpack: "Your inventory is too full to hold any more <ore>." (the action ends)
  - No usable pickaxe: "You need a pickaxe to mine this rock." then "You do not have a pickaxe which you have the Mining level to use."
  - Each swing start: "You swing your pick at the rock." (filterable). Each ore: "You manage to mine some <ore>." ("quarry"
    for sandstone and granite); a gem rock: "You get an uncut <gem>."; a gem table hit: "You just mined an uncut <gem>!"
  - Only the empty-rock text is sourced; the others are the game's wording as the Wiki leaves them out (OPEN-MN5).
- **MN11: locs.** Rock loc ids and the empty rock each leaves come from the cache (`COPPERROCK1/2`, `TINROCK1/2`, ... `Locs`
  symbols), plus the Tutorial Island (`NEWBIE*`, `TUT2_*`) and Prifddinas (`PRIF_MINE_*`) rocks, which share their ore's data.

## OPEN

- **OPEN-MN1:** which of the two empty rocks (`ROCKS1` 11390 / `ROCKS2` 11391) goes with which ore variant is not documented; the
  "...ROCK1" locs leave `ROCKS1`, the "...ROCK2" locs `ROCKS2`. Cosmetic only. Sandstone and granite use `ROCKS1`.
- **OPEN-MN2:** the first-roll offset after the click is assumed to match woodcutting (roll on T+P-1); the Wiki only gives the cycle.
- **OPEN-MN3:** the mining animation is replayed once per pickaxe cycle (woodcutting replays every 4 ticks); real animation timing
  is not sourced.
- **OPEN-MN4:** amethyst's 2-or-3 choice is assumed uniform and drawn when the first amethyst is mined.
- **OPEN-MN5:** message texts other than the empty-rock one are not from a cited source.
- **OPEN-MN7:** blurite rocks (level 10, 17.5 xp, 42-tick respawn; Wiki "Blurite ore") use copper's success rates (100 / 350); the Wiki has no blurite curve.
- **OPEN-MN6:** not implemented: the amulet of glory (gem table 1/64.2 and the gem rock rates 84 / 210), mining gloves, Varrock
  armour, mining cape, prospector outfit xp, celestial ring/signet, bracelet of clay and soft clay, gem/coal bags, clue geodes and
  the unidentified minerals / golden nugget / pet tertiary drops, Mining Guild and Wilderness respawn reductions (adamantite 2 min,
  runite 6 min, iron/coal faster), the per-skill Infernal and Crystal pickaxe charges and extra requirements (Smithing 85,
  Song of the Elves, Agility 50), 3rd age/gilded pickaxe special rules, the ore veins, Motherlode Mine, Volcanic Mine and other
  minigame rocks (limestone, blurite, barronite, daeyalt, lovakite, nickel, salt, calcified, sunstone, lunar, ...).
