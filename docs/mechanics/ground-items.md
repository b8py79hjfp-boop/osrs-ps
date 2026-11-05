# Ground items

Code: `engine/src/main/kotlin/tp/engine/zone/*`, `engine/.../inv/ItemTransaction.kt` (`dropToGround`, `takeFromGround`).
Source (W): OSRS Wiki "Drops", https://oldschool.runescape.wiki/w/Drops (quoted 2026-11-05): "Tradeable drops will
remain on the ground for 200 game ticks ... Untradeable drops will disappear after 300 game ticks ... Drops are
invisible to other players for the first 100 game ticks ... after which time, anyone will be able to see (and take)
the item if it is tradeable. The only exception is that if a player manually drops a non-food item in the Wilderness,
it will become visible to other players immediately."

## Rules

| # | Rule | Source |
|---|---|---|
| G1 | A dropped obj is visible only to its owner for 100 ticks. | W |
| G2 | Tradeable: it then turns public; it despawns 200 ticks after the drop. | W |
| G3 | Untradeable: never turns public; despawns after 300 ticks. | W |
| G4 | Wilderness: a manually dropped non-food item is public immediately. **Not implemented yet** (needs the wilderness area check, Phase 4). | W |
| G5 | Every drop and pick-up is an item transaction (atomic, journaled; brief §12). | brief |

## OPEN

- **OPEN-G1:** whether G2's 200 ticks count from the drop (Wiki wording, implemented) or from turning public. Behind
  `WorldConfig.groundTiming` (`GroundTiming.tradeableDespawn`).
- **OPEN-G2:** where in the tick obj timers run. We run them in post-tick, after players; not documented.

## Zone updates

Each player observes the 7x7 zones around their own zone that lie inside their build area (RS Mod
`PlayerZoneUpdateProcessor`, radius 3). A zone that becomes observed is reset (`UPDATE_ZONE_FULL_FOLLOWS`) and gets
its visible objs; an observed zone gets this tick's adds, deletes and reveals (`UPDATE_ZONE_PARTIAL_FOLLOWS`), each
filtered by what that player can see. Sent in the info pass after vars, stats and inventories.
