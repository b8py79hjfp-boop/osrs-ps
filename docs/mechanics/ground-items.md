# Ground items (player drops)

Code: `engine/src/main/kotlin/tp/engine/zone/*`, `engine/.../inv/ItemTransaction.kt` (`dropToGround`, `takeFromGround`).

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (D) OSRS Wiki "Drop" (player dropping), https://oldschool.runescape.wiki/w/Drop: "Tradeable items appear to other
  players 60 seconds (one minute) after being dropped, and disappear after another 120 seconds (two minutes) if not
  picked up." / "Items dropped in the Wilderness become visible to other players immediately, except for consumables
  (e.g., food, potions) and raw food. Consumables dropped in the Wilderness despawn after 15 seconds." / "Untradeable
  items disappear after 180 seconds (three minutes). This timer pauses if logged out. Untradeable items dropped on the
  ground will also follow the player between worlds."
- (M) OSRS Wiki "Drops" (monster drops), https://oldschool.runescape.wiki/w/Drops: tradeable drops remain 200 ticks,
  untradeable 300, invisible to others for the first 100. This covers **monster loot** (Phase 4), not player drops.
  (`Dropped_items` redirects here, which is how the first draft of this doc came to cite it for player drops.)

## Rules

| # | Rule | Source |
|---|---|---|
| G1 | A dropped tradeable obj is visible only to its owner for 60 s = 100 ticks, then to everyone. | D |
| G2 | It disappears 120 s = 200 ticks after turning public: 300 ticks after the drop. | D |
| G3 | Untradeable: never visible to others ("tradeable items appear to other players"); disappears after 300 ticks. | D |
| G4 | Wilderness: non-consumables are public immediately; consumables despawn after 15 s = 25 ticks. **Not implemented** (needs the wilderness area check, Phase 4). | D |
| G5 | Every drop and pick-up is an item transaction (atomic, journaled; brief §12). | brief |
| G6 | Static item spawns (eggs, buckets, quest items lying around) are public, never despawn, and reappear a fixed time after being taken (per item; OPEN-G6: the Wiki rarely gives the time, so spawn data carries one, default 100 ticks). They are world-owned, so they are the one obj not created by a transaction; taking one is a normal transaction. | Wiki item spawn tables |

Not modelled yet (D): the untradeable timer pauses while logged out and the obj follows the player between worlds;
a dropped item becomes visible only to players who were logged in before it was dropped and stayed logged in.

## OPEN

- **OPEN-G2:** where in the tick obj timers run. We run them in post-tick, after players; not documented.
- Seconds-to-ticks: 60 s / 0.6 = 100 ticks exactly; same for 120 s and 180 s.

## Zone updates

Each player observes the 7x7 zones around their own zone that lie inside their build area (RS Mod
`PlayerZoneUpdateProcessor`, radius 3). A zone that becomes observed is reset (`UPDATE_ZONE_FULL_FOLLOWS`) and gets
its visible objs; an observed zone gets this tick's adds, deletes and reveals (`UPDATE_ZONE_PARTIAL_FOLLOWS`), each
filtered by what that player can see. Sent in the info pass after vars, stats and inventories.
