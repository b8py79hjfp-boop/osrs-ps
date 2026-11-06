# Skilling base

Shared rules for every non-combat skill (`content/skills/.../Skilling.kt`, `PlayerRef.statRandom`).

## Rules

- **SK1: success roll.** Gathering and many production actions succeed with
  `(1 + floor(low * (99 - L) / 98 + high * (L - 1) / 98 + 0.5)) / 256`, where L is the current level clamped to 1..99
  and `low` / `high` are the chances out of 256 at levels 1 and 99. Sources: OSRS Wiki "Skilling success rate"
  (https://oldschool.runescape.wiki/w/Skilling_success_rate); RS Mod `SkillingSuccessRate`.
- **SK2: repeating actions.** A repeating action is the op script running again every tick while the player stays in
  range (OSRS re-triggers `p_oploc`; RS Mod `opLoc1` / `opLoc3`). Moving, clicking elsewhere or an interrupting
  interface ends it (interactions.md).
- **SK3: action clocks.** The player has an action clock and a skill-animation clock (RS Mod `actionDelay`,
  `skillAnimDelay`), in world ticks. They belong to the player, not the action: clicking again while the action clock
  is still running skips the arming tick.
- **SK4: tools.** The best usable tool is the worn weapon or any backpack item whose level requirement (cache obj param
  23, `levelrequire`) the current level meets; the higher requirement wins, the worn one on a tie (RS Mod `findAxe`).
- **SK5: filterable messages.** Per-action messages ("You swing your axe at the tree.", "You get some logs.") are sent
  as filterable game messages, chat type 105 (RS Mod `ChatType.Spam`).

- **SK6: ending an action.** A script ends its action by not calling `keepInteraction()`. Mining and fishing check their tool before
  calling it, and call `stopInteraction()` when they end after it (a rock that depletes, a catch that cannot be stored). See
  mining.md and fishing.md for their timelines.

## Make-X menu (MU) and production batches (PR)

Code: `content/skills/.../SkillMulti.kt`, `Production.kt`, `Recipes.kt`. Used by smithing.md, crafting.md, fletching.md, herblore.md
and cooking.md.

- **MU1: the interface.** `skillmulti` (270) opens in the chatbox modal (`chatbox:chatmodal`, unclamped like every chat
  interface: varbit `chatmodal_unclamp` 1 and client script 2379 first). Client script 2046 (`skillmulti_setup`) fills it
  from 21 ints and one string: `type`, the maximum quantity, 18 product obj ids (-1 = unused; the script counts the leading
  non -1 slots), the suggested quantity, and the title. Source: the rev 242 client scripts 2046, 2048, 2052, 2054-2063
  (disassembled from the cache) and enums 1809 / 1810 / 5178.
  - `type` is a key of cache enum 1809, which gives the verb on each product: 22 "Make", 13 "Smelt", 25 "Smith", 16 "Spin",
    12 "Cut", 10 "String", 9 "Fire", 6-8 "Cook", 35 "Weave" (further keys exist for buying, planting, carving, ...).
    Enum 5178 (types 26, 29-34) and enum 1810 (types 1, 3, 8) decide whether the quantity buttons (1, 5, 10, X, All) are shown.
  - The Make-X text box is client side (scripts 2060-2063); the server sees only the quantity.
- **MU2: the pick.** Clicking a product runs client script 2050 / 2052, which sends RESUME_PAUSEBUTTON for component
  `skillmulti:a`..`r` (consecutive, A = 17694735) with `sub` = the chosen quantity (varc 200: 1..28; "All" is the maximum
  quantity passed to the setup script; 0 if the type has no quantity buttons). The client only sends it for child slots
  0..28 that carry the PAUSE_BUTTON event, so the server sends IF_SETEVENTS for slots 0..28 of every product component it shows.
- **MU3: server side.** `SkillMulti.open(player, type, title, objs) { index, quantity -> ... }` stores the menu on the player and opens
  the interface; the pick arrives as a normal button (op 1, `ResumePause` with no waiting script: scripts.md) and is validated:
  the player must have a menu, the index must be one of its products, and the quantity is clamped to 1..28. The menu is closed
  before the callback runs. `open` ends the player's current interaction: a menu opened by an op script would otherwise make the
  engine run that op again when the menu closes (interactions.md I6, I8).
- **MU4: callers validate again.** The menu is a request. The callback re-checks level, tools and materials (the client could
  have sent anything the cache lets it send).
- **PR1: batch timeline.** A batch is "N actions of T ticks". On the tick the quantity is chosen (input phase) the animation plays
  and the first action is queued `first` ticks ahead; after each action the animation plays again and the next is queued `T`
  ticks ahead. So the first product lands `first` ticks after the click, then every `T`. `first` is the recipe's own `T` unless a
  source gives another value (smelting, spinning).
- **PR2: interruption.** Each action is a weak queue script (`PlayerRef.weakQueue`, scripts.md Q4, Q7): a click that walks or
  interacts, any interface opening or closing (the level-up popup included, Q3) removes it before it runs, and it is held while
  a modal is open. The batch carries no other cancel state.
- **PR3: ending.** A batch also ends when an action cannot be made (missing level or tool: the message says which; running
  out of materials part-way ends it without a message, since "All" means as many as the materials allow — OPEN-PR4),
  when the quantity is done, and on a level-up (Wiki "Smithing": the player keeps smithing until "the player levels up").

### API (content/skills, `internal`)

```kotlin
SkillMulti.open(player, SkillMulti.SMELT, "What would you like to smelt?", intArrayOf(bar1, bar2)) { index, quantity ->
    val batch = Batch(quantity, animSeq, intervalTicks) { /* one action; return false to stop the batch */ true }
    Production.start(this, batch, firstDelayTicks)
}
```

`SkillMulti.MAKE / SMELT / SMITH / SPIN / CUT / STRING / FIRE / COOK / MAKE_SETS` are the type keys (fletching.md, herblore.md, cooking.md CK1); `Production.award(p, stat, xp)` adds xp and
returns false on a level-up; `Production.shortfall(p, recipe)` returns the message for a missing level, tool or material;
`RecipeTable.load()` reads `recipes.tsv` (tools/wiki-import/recipes.py). `SkillMultiMenus` registers the 18 product buttons once.

## OPEN

- **OPEN-SK1:** sound effects are not sent (no synth-sound message in the protocol yet).
- **OPEN-SK2:** invisible level boosts (RS Mod `InvisibleLevels`) and xp modifiers (outfits) are not applied.
- **OPEN-PR1:** the first-action offset of anvil smithing, leather, gems, pottery, glass and jewellery (assumed one interval)
  is unsourced; only smelting and spinning have sourced offsets (smithing.md SM2, crafting.md CR4).
- **OPEN-PR2:** "the player is attacked" also ends smithing (Wiki "Smithing" note); only the interruptions of PR2 are modelled.
- **OPEN-PR4:** no source states that running out mid-batch is silent; it follows how "All" behaves in play.
- **OPEN-PR3:** the suggested quantity is always 28 ("All"); OSRS remembers the previous choice (`skillmulti_previousselection`,
  varp 2673, is not written).
- **OPEN-PR4:** success messages of production actions are mostly unsourced; only those named in smithing.md / crafting.md come
  from the Wiki or RuneLite.

