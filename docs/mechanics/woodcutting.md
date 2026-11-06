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

## OPEN

- **OPEN-WC1:** bird nests, Forestry events, axe special effects (infernal, crystal charges) and the lumberjack outfit
  are not implemented.
- **OPEN-WC2:** the stump respawn overlay timer (client script `add_overlay_timer_loc`) is not sent.
- **OPEN-WC3:** the blessed axe, quest axes and the Gauntlet axes are not included (RS Mod leaves them out too).
