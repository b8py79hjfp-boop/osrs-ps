# NPC random walk

Code: `engine/src/main/kotlin/tp/engine/npc/NpcAi.kt`. Source: osrs-docs Random Walk (RW),
https://osrs-docs.com/docs/mechanics/random-walk/.

| # | Rule | Source |
|---|---|---|
| RW1 | Only NPCs that aren't occupied (no interaction or combat) may wander. An NPC with wander range 0 never moves. | RW "Conditions" |
| RW2 | Wandering may start even with movement still queued (erratic direction changes are observed). | RW "Conditions" |
| RW3 | Destination: a random tile within the wander range of the spawn point. | RW "Client Code" (the client uses ±5; the server uses the NPC's wander range) |
| RW4 | Path: the "dumb" naive pathfinder (diagonal, else slide along one axis), ignoring entity collision when choosing. Processing halts at entity collision **without clearing** the path. | RW "Server-sided implementation" |
| RW5 | Probability per server tick: **OPEN-RW1**. RW gives either 300/1000 (10/1000 × 30 client ticks) or 260/1000 (repeated-event). Flag `NpcAiConfig.randomWalkChance`; default 260/1000. | RW "Probability" |

Entity collision (NPCs blocking NPCs/players) arrives with the PvM work; RW4's "halt, don't clear" is already
implemented for map collision.
