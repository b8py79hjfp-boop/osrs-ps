# Woodcutting

`content/skills/.../Woodcutting.kt`, data in `WoodcuttingData.kt` (adapted from RS Mod, ISC, DECISIONS D-034).

## Rules

- **WC1: tick timeline** (RS Mod `Woodcutting.attempt` / `cut`).
  - Tick T (click, arrived, action clock expired): both clocks are armed to T+3. Nothing else happens.
  - T+1: the axe animation plays and "You swing your axe at the tree." is sent.
  - T+3: the first roll for a log (SK1, the axe's rates for the tree). The animation replays at T+3 and every 4 ticks.
  - T+4: the clock is re-armed to T+7. Rolls land at T+3, T+7, T+11 and so on: one every 4 ticks.
  - If the action clock is still running when the player clicks (SK3), the swing happens on the click tick.
- **WC2: trees.** Level, xp, product and success rates per tree type and axe: RS Mod `TreeLocs`, `WoodcuttingRates`
  (they match the Wiki's tree pages, e.g. https://oldschool.runescape.wiki/w/Tree, /w/Yew_tree).
- **WC3: requirements and messages.**
  - Level too low: "You need a Woodcutting level of N to chop down this tree."
  - Full backpack: "Your inventory is too full to hold any more <logs>."
  - No usable axe: "You need an axe to chop down this tree." then "You do not have an axe which you have the
    woodcutting level to use."
  - Each log: "You get some <logs>." (filterable).
- **WC4: depletion.**
  - Regular, dead, evergreen, jungle, achey and burnt trees fall on their first log.
  - The juniper tree falls with 239 in 255 after each log (RS Mod `deplete_chance` 16: `random(1..255) > 16`).
  - Every other tree has a Forestry timer of N ticks (oak 45, willow and teak 50, maple and mahogany 100, hollow 60,
    arctic pine 140, yew 190, magic 390, redwood 440). The timer counts one tick up for each tick anyone is cutting it
    and one down for each tick nobody is. The tree falls on the first log after the count reaches N.
    Source: Wiki "Forestry" (https://oldschool.runescape.wiki/w/Forestry#Tree_timers); RS Mod `treeDespawnTick`.
  - A fallen tree becomes its stump for the respawn time: regular trees 60 to 100 ticks at random, the others fixed.
    Then it reverts.

- **WC5: bird nests** (`WoodcuttingExtras.kt`). Each log has a 1/256 chance (11/2560 with a Woodcutting cape) of a nest on a
  random adjacent walkable tile ("<col=ef1020>A bird's nest falls out of the tree.</col>"). The type is a pick of 0..99
  (0..94 with a strung rabbit foot): 0 red, 1 blue, 2 green egg, 3 to 34 ring, else seeds. "Search" turns a nest into an
  empty nest and gives its contents: an egg of the nest's colour; a ring (gold 35, sapphire 40, emerald 15, ruby 9, diamond
  1 of 100); a seed (the Wiki's 19-entry table, weights out of 1011). Source: Wiki "Bird nest", "Bird nest (ring)",
  "Bird nest (seeds)", "Bird nest (egg)", "Strung rabbit foot", "Woodcutting cape".
- **WC6: camphor, ironwood and rosewood trees** (cache locs 58557, 58559, 58561 and their stumps): level 66 / 80 / 92, xp
  143.5 / 175 / 212.5, they fall after 200 / 400 / 458 ticks of Forestry timer (WC4), cut chances from each Wiki "Cut chance"
  chart (low, high) for bronze to crystal axes.
- **WC7: lumberjack outfit and infernal axe.** Lumberjack (or forestry) hat 0.4%, top 0.8%, legs 0.6%, boots 0.2%, +0.5% for
  the set (2.5%) on all Woodcutting xp. With an infernal axe, each cut log has a 1/3 chance to burn instead of being kept: half
  the log's Firemaking xp with the pyromancer bonus, one of 5,000 charges (player saved value
  `woodcutting.infernal_charges`). Source: Wiki "Lumberjack outfit", "Infernal axe".
- **WC8: crystal and dragon axes.** Their passive "efficiency" is the tier 7 and 8 cut chances already in `AxeRates`; the special
  attack (Lumber Up, +3 Woodcutting) needs the special-attack bar and is not implemented.

## OPEN

- **OPEN-WC1:** Forestry events (Rising Roots, Struggling Sapling, Friendly Ent, Pheasant Control, Egg Hunt, Beehive,
  Leprechaun, Enchantment Ritual, Flowering Tree, Poachers and the Forestry kit, rations, felling axes, the Forestry shop) and
  the crystal-axe special are not implemented. They need world-level timers and entity spawns per tree group.
- **OPEN-WC2:** the stump respawn overlay timer (client script `add_overlay_timer_loc`) is not sent.
- **OPEN-WC3:** the blessed axe, quest axes and the Gauntlet axes are not included (RS Mod leaves them out too).
- **OPEN-WC4:** the axe check runs on the click tick (RS Mod runs it on the swing tick, one tick later), so a player with
  no usable axe gets the message once and the action ends.
- **OPEN-WC5:** blisterwood (Sins of the Father gate, spider on depletion, only bronze and dragon cut chances published),
  sulliuscep (mushroom, 1/16 depletion, no loc symbol found), bloodwood (bucket and wounding mechanic) and clue nests (a chance
  from tree type, Woodcutting level and clue tier; needs the pet-roll formula) are not implemented.
- **OPEN-WC6:** the stump respawn of camphor, ironwood and rosewood is not published; 200 ticks (RS Mod's value for magic and
  redwood) is used. The Wiki's camphor black-axe chart entry (17, 71) breaks the monotone order and is used as published.
- **OPEN-WC7:** infernal axe: charges are one counter per player rather than per item, the toggle option, the uncharged item swap
  and the burn message are not implemented. Max capes are not counted for the nest bonus. The Forestry outfit ids are found by
  name ("Forestry hat" and so on) and fit the same slots as the lumberjack pieces.
- **OPEN-WC8:** a nest lands on a random adjacent tile with no west bias (Mod Ash) and with the default ground-item lifetime
  (the 2022 change makes nests last four times longer). The search messages are not sourced and not sent. A full backpack
  refuses a search ("You don't have enough inventory space.").
