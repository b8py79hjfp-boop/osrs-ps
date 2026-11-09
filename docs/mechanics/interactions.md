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
| I8 | Guarded by not delayed and no modal: if nothing interacted, the entity didn't move and has no steps left, send "I can't reach that!" and reset. After an interaction ran, reset unless `aprange` was called or it's persistent; this reset is not guarded, so a script that opens a modal or delays itself still ends its interaction (with the guard, closing the modal ran the op again). **OPEN-I3:** what makes an interaction persistent (the pseudocode declares it, never sets it). Ours: a script asks for it on each run (`keepInteraction`), and the request is cleared at the start of every turn, so an interaction whose script stops asking ends after that run (combat stops when its target dies instead of resuming on the respawn). | I pseudocode |
| I9 | NPCs use the same algorithm without modals, engine scripts or messages. | I |
| I10 | **Facing ends with the interaction (players).** A hard interruption (I7: a walk click, a new interaction, …) stops the player facing an entity at once, and a player with no interaction at the start of its turn stops facing (so after an attack or a conversation ends, the next tick). The client keeps the last direction and turns with its walk. Combat keeps facing while it keeps its interaction (`keepInteraction`). NPC facing is unchanged. | RS Mod (ISC) `MoveGameClickHandler` / `ProtectedAccess.stopAction` (`resetFaceEntity`), `PlayerMainProcess.refreshFaceEntity` (commit fa13b3f) |
| I11 | **Which script an NPC op runs.** A multi-NPC is clicked by its spawned (parent) id but shows a child type. Lookup order: handlers bound to the shown type's id or name, then to the parent's, then op-text fallbacks (`onOpNpcOption`, e.g. the generic Wiki "Talk-to" dialogue) of the shown type, then of the parent. Before this, the generic Talk-to of a child (Lady Keli 11578, Captain Tobias 14978) shadowed the quest handler on the parent and the click did nothing visible. | engine dispatch rule (no OSRS source needed: server-side routing); seen in the client 2026-11-09 |

## SOURCE-CONFLICT (decided)

- **Engine ap in block 2.** The pseudocode's block 2 sends the engine ap message whenever the target is in approach
  distance, even when an op script exists. Read literally, an op-only interaction (e.g. taking a ground item) would
  print "Nothing interesting happens." from up to 10 tiles away instead of walking up and running the op. The prose
  of the same page says "The default engine versions can only execute in the absence of the runescript equivalent"
  and that only the *undefined* script becomes the engine default. Decision: the engine ap branch also requires that
  no op script exists. Found in real-client testing, 2026-11-06.

## OPEN

- **OPEN-I1:** route recalculation for moving targets (players/NPCs). We re-path toward the target when it moves out
  of reach, as RS Mod does; osrs-docs doesn't specify.
- **OPEN-I2:** ground-item ("obj") op distance: objs use I1's loc rule with a 1×1 footprint (rsmod-pathfinder
  rectangle reach).

## Using items and spells on things (I10)

- **Packets.** OPLOCT, OPOBJT, OPNPCT, OPPLAYERT and IF_BUTTONT carry the used component, slot and obj. The targeted
  interaction routes and reaches like an op, then runs the use-on script on arrival.
- **Content API.** `onUseOnLoc` / `onUseOnNpc` / `onUseOnObj` take an item id, or -1 for any item.
  `onUseOnItem(a, b)` fires either way round. Spells use `onOpLocT` / `onOpObjT` / `onButtonT`, keyed by component.
  The used slot is `PlayerScript.used`.
- **Validation.** Client input is hostile (brief §12):
  - the used backpack slot must still hold the item when the script runs;
  - both slots of an item-on-item must hold what the client says, and they must be different slots.
- **Default.** An item use with no handler answers "Nothing interesting happens.", OSRS's line for a use with no
  effect.
