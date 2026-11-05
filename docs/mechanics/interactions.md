# Entity interactions

Code: `engine/src/main/kotlin/tp/engine/interact/*`. Source: osrs-docs Entity Interactions (I),
https://osrs-docs.com/docs/mechanics/entity-interactions/, and Henke's model (H). The engine implements I's
pseudocode verbatim.

## Rules

| # | Rule | Source |
|---|---|---|
| I1 | **op** (operable) needs line of walk and cardinal adjacency to the target (N/E/S/W, no diagonals). | I |
| I1b | For locs, "adjacent" means reached per the loc's shape (walls, wall decor, rectangles). **Derived**, not in osrs-docs: RS Mod `BoundValidator` / rsmod-pathfinder `ReachStrategy`. | RS Mod |
| I2 | **ap** (approachable) needs line of sight and being within the approach range: default 10, 0–10 allowed. `aprange(-1)` turns an ap into an op. | I |
| I3 | Priority: script op → script ap → engine ap → engine op. Engine variants run only if the script variant is missing; they send "Nothing interesting happens." | I |
| I4 | Two blocks around movement. **Block 1:** op only for player/NPC targets; ap for any target; then a **no-op** branch when within approach distance (it swallows the engine message); then engine op for player/NPC targets. Block 1's ap doesn't reset `apRangeCalled`. **Block 2** runs only if nothing interacted or `aprange` was called: op if in range and (target is a player/NPC **or the entity didn't move**); ap (resets `apRangeCalled`); engine ap message; engine op message with the same `!moved` rule. Produces banker-vs-booth timing. | I pseudocode, H |
| I5 | Moving sets the last-movement clock to `tick + 1`; arrive delay reads it. | I pseudocode |
| I6 | Interactions are skipped (paused) while delayed or while a modal is open. | I |
| I7 | Interrupted by a new interaction, death (and similar manual cancels), player-invoked teleports, certain buttons (e.g. "View equipment stats") and every inventory item action. Equipment-tab actions and ordinary interface clicks don't interrupt. | I |
| I8 | Guarded by not delayed and no modal: if nothing interacted, the entity didn't move and has no steps left, send "I can't reach that!" and reset. After an interaction ran, reset unless `aprange` was called or it's persistent. **OPEN-I3:** what makes an interaction persistent (the pseudocode declares it, never sets it). | I pseudocode |
| I9 | NPCs use the same algorithm without modals, engine scripts or messages. | I |

## OPEN

- **OPEN-I1:** route recalculation for moving targets (players/NPCs). We re-path toward the target when it moves out
  of reach, as RS Mod does; osrs-docs doesn't specify.
- **OPEN-I2:** ground-item ("obj") op distance: objs use I1's loc rule with a 1×1 footprint (rsmod-pathfinder
  rectangle reach).
