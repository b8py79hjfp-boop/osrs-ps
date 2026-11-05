# Movement (Phase 2 slice)

Code: `engine/src/main/kotlin/tp/engine/World.kt` (`setRoute`, `move`), `Player.kt` (`BuildArea`).
Phase 3 completes this with osrs-docs' Entity Interactions (approach and op, arrive delay), run energy and the full
tick pipeline (`tick.md`).

## Rules

| # | Rule | Source |
|---|---|---|
| M1 | Routes are computed **server-side** from the player's tile to the clicked tile, with a BFS that emulates the client (rsmod-pathfinder `RouteFinding`, `moveNear = true`, ≤ 25 waypoints). A failed route leaves the player idle. | Brief §5.2; RS Mod routefinder README |
| M2 | Walking moves **1 tile per tick**, running **2**. | Brief §5.2; OSRS Wiki "Run" (not yet fetched: host unreachable) |
| M3 | Each step heads straight or diagonally toward the next waypoint. Every step is checked against collision (`StepValidator.canTravel`); a blocked step ends the route. | rsmod-pathfinder `StepValidator`; RS Mod `PlayerMovementProcessor` |
| M4 | A click received during tick N is acted on in tick N+1's input phase. | Brief §5.3 phase 2 |
| M5 | Ctrl-click inverts the run mode for that route only. | **OPEN**: from memory of the client; confirm against the Wiki when reachable. Behaviour: `Player.routeCtrl`. |
| M6 | Map rebuild: 104×104-tile build area centred on the player's zone, rebuilt on stepping within 16 tiles (2 zones) of its edge. | RS Mod `BuildAreaUtils` |
| M7 | Player-info move speed: 1 walk / 2 run; a single step while running is shown as a walk step; teleports use 127. | RSProt `PlayerAvatarExtendedInfo.setMoveSpeed/setTempMoveSpeed` docs; RS Mod `RspCycle.updateMoveSpeed` |

## Tick timeline (one click)

| Tick | Event |
|---|---|
| N | MOVE_GAMECLICK arrives; RSProt buffers it |
| N+1, input phase | Route computed and stored |
| N+1, player phase | First step (walk: 1 tile; run: 2) |
| N+1, info pass | Player info carries the step(s) |
| N+2… | One (two) tiles per tick until the last waypoint |

Verified by test 2: a walk click 3 tiles east arrives at the destination.
