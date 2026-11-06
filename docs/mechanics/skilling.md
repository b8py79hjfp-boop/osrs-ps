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

## OPEN

- **OPEN-SK1:** sound effects are not sent (no synth-sound message in the protocol yet).
- **OPEN-SK2:** invisible level boosts (RS Mod `InvisibleLevels`) and xp modifiers (outfits) are not applied.
