# Construction

Code: `content/skills/.../Construction.kt` (sawmill), data in `ConstructionData.kt` and the resource tables
`construction-furniture.tsv` and `construction-rooms.tsv` (written by `tools/wiki-import/construction.py`, re-runnable).
Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (C) "Construction" (house cost, entering, materials and the xp each material gives), https://oldschool.runescape.wiki/w/Construction
- (S) "Sawmill operator" and "Sawmill" (Buy-plank, prices, unnoted logs only), https://oldschool.runescape.wiki/w/Sawmill_operator
- (P) "Player-owned house" (the Rooms tables: level and cost), https://oldschool.runescape.wiki/w/Player-owned_house
- (F) every page that embeds `{{Infobox Construction}}` and its `{{Recipe}}` (level, xp, room, hotspot, built loc ids, materials,
  tools, ticks), e.g. https://oldschool.runescape.wiki/w/Oak_chair
- (L) the cache (cache 2757): npc 3101 `poh_sawmill_opp` ops 1 "Talk-to", 3 "Buy-plank", 4 "Trade"; interfaces `poh_*` (below)

## Rules implemented

| # | Rule | Source |
|---|---|---|
| CO1 | The sawmill operator's op 3 "Buy-plank" turns unnoted logs into planks: Logs to Plank 100 coins, Oak 250, Teak 500, Mahogany 1,500, Camphor 2,500, Ironwood 5,000, Rosewood 7,500, one log and the fee per plank. Noted logs are not accepted. | S, L |
| CO2 | The amount made is the amount asked, capped by the unnoted logs held and by what the coins pay for. Coins, logs and planks move in one item transaction; nothing is taken if it fails. | S |
| CO3 | The "Trade" op (the Construction supplies shop: saw, bolt of cloth, three nails) is the existing shop table (`shops.tsv`, "Sawmill"). | S, L |

## Data (no behaviour yet)

- `construction-furniture.tsv` (451 rows): name, level, xp per build, room, hotspot, built loc ids, flat-pack item, ticks (5 in
  every row), tools (saw 8794, hammer 2347) and materials as `obj:count` (F). Xp per material, for checking the table (C):
  plank and nails 29 (a plank is counted as 29 together with its nails), oak plank 60, teak plank 90 (123 on the mounted mythical
  cape), mahogany plank 140, soft clay 10, bolt of cloth 15, steel bar 20, limestone brick 20, gold leaf 300, marble block 500
  (450 in the Wiki's cost column), magic stone 1,000, condensed gold 894.4.
- `construction-rooms.tsv` (24 rows): room, level, cost (P). Unlocks: first house 1,000 coins (C); house moves 5,000 to 50,000.
- 61 pages with a versioned infobox (plants, hedges, pools, curtains, fireplaces, rune cases, trees, armour cases, ...) and 10 with a
  material that is not a unique cache name ("Skull (item)", "Small map (item)", ...) are dropped by the importer and listed on its
  stderr. They are OPEN-CO3.

## Engine gap: player-owned houses (investigated, not implemented)

A house is an instanced map built from template chunks. Findings:
- The vendored RSProt 242 has the packet: `RebuildRegionV2(zoneX, zoneZ, reload, zoneProvider)` (also `RebuildWorldEntityV4`), which
  sends up to 13x13 (build area 104x104) zones of `RebuildRegionZone(zoneX, zoneZ, level, rotation)` per level copied from a static
  map square, plus the XTEA keys of every source map square.
- Our engine only has the static kind: `ServerMessage.Rebuild(x, z)` (protocol-api `Messages.kt`) -> `RebuildNormalV2` in
  `RspSession`; `Player.buildArea` is an origin on the real map (`BuildArea` in `Player.kt`); collision (`World.collision`, copy-on-write over
  the shared base), `DynamicLocs`, ground items, zones and the info packets all key on real world coordinates. RS Mod
  (/tmp/claude-0/rsmod) has no instance support either.
- What the main session needs to add (not done here; engine/, protocol* belong to it):
  1. A message `ServerMessage.RebuildRegion(centreZone, reload, zones: 4 levels x 13 x 13 of (sourceZone, rotation)?)` and its `RspSession`
     encoder (`RebuildRegionV2`; needs the template square's XTEA keys from the cache).
  2. A per-player build area that is either `real(origin)` or `dynamic(instance)`; `inBuildArea`, `ZoneSync` and the info passes must
     convert between the player's instance coordinates and the template/real coordinates, and `World.inBuildArea` must accept them.
  3. An allocation of instance space: a region of the plane far from real maps (the instance coordinates are the build area the
     client shows; any free 104x104 block works) with its own collision, built by copying the template zones' collision with the
     rotation applied (rotated loc shape/angle and collision flag rotation per zone), and its own loc/obj/NPC visibility so other
     players do not see it (guests of the house share one instance).
  4. Tear-down on leave/logout and the 'Leave house' / portal exit flow (interface `poh_options` 370, `poh_viewer` 422).
- Template data (OPEN-CO2): the template map square holding every room (garden, parlour, ... at fixed zone positions, plus the
  four-wall variants) is in the cache but its square coordinates and per-room chunk coordinates are not on the Wiki; find them by
  scanning the map squares for the room hotspot locs (loc ids in column `locs` of the furniture table, hotspot names in `hotspot`).
  The room table (sort order, door positions, rotation rules) must come from that scan or a sourced server (not invented).
- Client interfaces found in the cache for the build menus (L): `poh_furniture_creation_menu` 397 (component `poh_furnituremenu`),
  `poh_furniture_creation` 458 (24+ slots `_01`..., scrollbar), `poh_add_room` 212, `poh_options` 370, `poh_viewer` 422, `poh_loading` 71,
  `poh_board` 52. Their client scripts and the varbits that drive them have not been read.

## OPEN

- **OPEN-CO1: the Buy-plank interface.** The Wiki shows a dedicated "Sawmill interface" the cache gives no name for. Plank type and
  amount are asked with the chatbox option menu (planks 1-4, then "More..." for 5-7) and the number prompt instead, and the game
  messages are plausible, not sourced. Sawmill vouchers (`sawmill_coupon*`: twice the planks per log) and the plank sack are not
  implemented, and the "Dragon Slayer I free-to-play" nine-plank variant is not either.
- **OPEN-CO2: house template.** See above. House portals (Rimmington etc.), build mode, hotspot "Build"/"Remove", room creation and
  rotation, flat-packs at a workbench and the estate agent need dynamic regions and are not implemented. Their data is in the tables.
- **OPEN-CO3: furniture not in the table** (see Data) and the quest/skill-hall requirements, optional inputs (any nail type in place
  of the listed one; the Wiki names one) and boosts (carpenter's outfit +2.5% xp, mythical cape 123 xp per teak plank).
