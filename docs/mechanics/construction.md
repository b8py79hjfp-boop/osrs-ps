# Construction

Code: `content/skills/.../Construction.kt` (sawmill), data in `ConstructionData.kt` and the resource tables
`construction-furniture.tsv` and `construction-rooms.tsv` (written by `tools/wiki-import/construction.py`, re-runnable); the
player-owned house is `PlayerOwnedHouse.kt` (plugin), `HouseMenus.kt` (build-mode actions), `HouseBuilder.kt`, `House.kt`,
`PohRooms.kt` (template table), `PohScanner.kt`, `PohFurniture.kt` and `PohLayout.kt` (saved layout). Instances: instances.md.
Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06 (W: 2026-11-07):
- (C) "Construction" (house cost, entering, materials and the xp each material gives), https://oldschool.runescape.wiki/w/Construction
- (S) "Sawmill operator" and "Sawmill" (Buy-plank, prices, unnoted logs only), https://oldschool.runescape.wiki/w/Sawmill_operator
- (P) "Player-owned house" (the Rooms tables: level and cost; layout, room limits and grid sizes, guests),
  https://oldschool.runescape.wiki/w/Player-owned_house
- (F) every page that embeds `{{Infobox Construction}}` and its `{{Recipe}}` (level, xp, room, hotspot, built loc ids, materials,
  tools, ticks), e.g. https://oldschool.runescape.wiki/w/Oak_chair
- (W) "Portal (Player-owned house)", https://oldschool.runescape.wiki/w/Portal_(Player-owned_house); "Building mode",
  https://oldschool.runescape.wiki/w/Building_mode
- (L) the cache (cache 2757): npc 3101 `poh_sawmill_opp` ops 1 "Talk-to", 3 "Buy-plank", 4 "Trade"; interfaces `poh_*` (below); the
  house template map (squares 29..32 x 110..111) and the loc configs of the hotspots, portals and doors (`cache-dump map`,
  `find-locs`, `config loc`); varbit `poh_building_mode` 2176 (varp 780 bit 0)

## Rules implemented

| # | Rule | Source |
|---|---|---|
| CO1 | The sawmill operator's op 3 "Buy-plank" turns unnoted logs into planks: Logs to Plank 100 coins, Oak 250, Teak 500, Mahogany 1,500, Camphor 2,500, Ironwood 5,000, Rosewood 7,500, one log and the fee per plank. Noted logs are not accepted. | S, L |
| CO2 | The amount made is the amount asked, capped by the unnoted logs held and by what the coins pay for. Coins, logs and planks move in one item transaction; nothing is taken if it fails. | S |
| CO3 | The "Trade" op (the Construction supplies shop: saw, bolt of cloth, three nails) is the existing shop table (`shops.tsv`, "Sawmill"). | S, L |

## Data

- `construction-furniture.tsv` (451 rows): name, level, xp per build, room, hotspot, built loc ids, flat-pack item, ticks (5 in
  every row), tools (saw 8794, hammer 2347) and materials as `obj:count` (F). Xp per material, for checking the table (C):
  plank and nails 29 (a plank is counted as 29 together with its nails), oak plank 60, teak plank 90 (123 on the mounted mythical
  cape), mahogany plank 140, soft clay 10, bolt of cloth 15, steel bar 20, limestone brick 20, gold leaf 300, marble block 500
  (450 in the Wiki's cost column), magic stone 1,000, condensed gold 894.4. 358 rows have built loc ids and can be built; the rest
  (Mahogany Homes clients, versioned infoboxes) cannot.
- `construction-rooms.tsv` (24 rows): room, level, cost (P). Unlocks: first house 1,000 coins (C); house moves 5,000 to 50,000.
- 61 pages with a versioned infobox (plants, hedges, pools, curtains, fireplaces, rune cases, trees, armour cases, ...) and 10 with a
  material that is not a unique cache name ("Skull (item)", "Small map (item)", ...) are dropped by the importer and listed on its
  stderr. They are OPEN-CO3.

## Player-owned house

A house is an instance (instances.md): a block of 1 or 2 map squares in instance space whose 8x8 zones are copies of the cache's
room templates. It exists while somebody is inside. Everything below is `content/skills`; the engine is unchanged.

| # | Rule | Source |
|---|---|---|
| H1 | The first house has a garden and a parlour (the parlour south of the garden, turned so a door faces it) and an exit portal on the garden's centrepiece. Entering with no saved layout creates and saves it for free (the 1,000 coin fee and the estate agent are OPEN-CO5). A house with no exit portal gets one on its first free centrepiece. | P; cells ours |
| H2 | The nine house portals (loc ids 15477-15482, 28822, 34947, 55353; cache category 486) have ops 1 "Enter" (a menu: your house / your house in building mode / a friend's house / never mind), 2 "Home", 3 "Build mode" and 4 "Friend's house". Your own house opens only at the portal of the house's location (saved `poh.location`, default 0 = Rimmington; Wiki order Rimmington, Taverley, Pollnivneach, Hosidius, Rellekka, Aldarin, Brimhaven, Yanille, Prifddinas). A friend's house needs the friend to be in a live house at the same location, not in building mode and not locked. | W, L |
| H3 | The house instance is `ceil((side + 2) * 8 / 64)` squares wide and long, where side is the grid side of the owner's unboosted Construction level (3x3 at level 1 up to 7x7 at level 60: the Wiki table in `HouseBuilder.LIMITS`, which also gives the room limit 24-38). Room cell (x, z) of floor f is the zone at instance base + (8x, 8z) on level f; rooms live in cells 1..side, cells 0 and side+1 are the yard (left empty: OPEN-CO6). Floors are instance levels: basement 0, ground 1, upper 2; the template is copied from the same level. | P (limits), ours (cells) |
| H4 | Hotspots and doorways: the copied template locs are scanned once per room (`PohScanner`). Furniture hotspots are locs whose cache name ends in " space" (plus fencing, hedging, plants; cache category 207, op 5 "Build"); hotspot locs of one name on touching tiles form one hotspot (a rug). Doorways are the "Door hotspot" locs (category 485) on the room's edge, two tiles per doorway. Hotspots exist as ghost locs only in building mode ("vacant hotspots appear as ghostly figures"): outside it the server deletes them (zone updates). Doorways: in building mode the door hotspots show; otherwise a doorway whose neighbour room exists and has a door on the matching side is opened (hotspot deleted) and any other is closed with the room's own wall loc. | W (building mode); hiding is OPEN-CO8 |
| H5 | "Build" on a hotspot (building mode, owner only): a menu lists the pieces of the furniture table whose room and hotspot name match, sorted by level. Picking one checks level, tools and materials; the player plays the build animation and waits the row's `ticks` (5); then everything is checked again, the materials are taken in one item transaction, each hotspot loc becomes the piece's loc (same tile, shape and angle; OPEN-CO10), the xp is added and the piece is saved. | F |
| H6 | "Remove" on built furniture (building mode, owner only): after "Really remove it?" the hotspot loc is restored. Nothing is refunded and no xp is given (C: removing gives nothing back "in most cases"). The exit portal cannot be removed. | C |
| H7 | "Build" on a door hotspot: if the room beyond exists, a confirmation removes it (not the room with the exit portal); otherwise a menu lists the ground-floor rooms with level and cost, and picking one checks level, room limit (H3) and coins, asks which way it faces when more than one rotation puts a door back toward the clicked doorway, takes the coins, copies the zone rotated and saves. Building a room gives no xp. | P |
| H8 | Saved values (`PohSave`): `poh.rooms.count` rooms in `poh.rooms.<n>`, three 18-bit slots per Long, each (room type + 1, 6 bits)(rotation, 2)(floor, 2)(x, 4)(z, 4); `poh.furn.count` entries in `poh.furn.<n>`, one per built hotspot: first built loc id << 18 or hotspot key, key = floor (2) cell x (4) cell z (4) layer of the anchor loc (2) template tile of the anchor (6; the lowest tile of the hotspot as it is in the template, so it does not depend on the room's rotation). `poh.location` and `poh.style` hold the portal location and the style column (default 0). Room types index `PohRooms.ALL` (append only). | ours |
| H9 | Leaving: the exit portal (op 1), the options panel's "Leave house" button, a teleport or a logout all end the visit. The player returns to the tile they stood on when they used the portal. A soft timer every 2 ticks notices teleports. The last player out destroys the instance; guests stay if the owner leaves ("Guests will remain in a house if the host leaves"). Building mode cannot be turned on while guests are in the house. The exit portal's op 2 "Lock" toggles the house for friends. | W |

Tick timelines (the scripts run when the player reaches the loc):
- Enter: tick T, on arrival: instance allocated, rooms copied, locs scanned, hotspots and doorways set, the player teleported
  beside the exit portal; T+1 info pass: REBUILD_REGION for the instance plus the zone state of the dynamic changes.
- Build furniture: tick T the menu pick arrives: level, tools and materials checked, animation starts; T+5: checked again, materials
  taken, locs swapped, xp added, layout saved. Walking away or opening another interface during the wait cancels nothing
  (the player is delayed), the house being destroyed does.
- Remove furniture and rooms: tick T of the confirming button: hotspot restored / zone cleared in the same tick; the player is moved
  to the exit portal if their room went away.
- Add room: tick T of the last menu pick: coins taken, zone copied, doorways of the new room and its neighbours reset; players
  inside get the region rebuild on the next info pass (instances.md IN4).

### Template table (OPEN-CO2 answered)

How it was derived: the room templates are in map squares (29..32, 110..111), x 1856..2111, z 7040..7167. Each house style is one
64-tile column: x 1856, 1920 and 1984 hold every room on all four levels (the column at 2048 only a ground-level sketch), so three
styles; which is "basic wood", "basic stone" and "whitewashed stone" is unverified. Rooms are 8x8 zones in a checkerboard (neighbouring
zones are empty). `cache-dump map <squareX> <squareZ>` lists every loc with its tile; each zone was matched to a room by comparing
the set of its hotspot loc names with the hotspot column of `construction-furniture.tsv` (best Jaccard score 0.57 to 0.86), and the
doorways are the "Door hotspot" locs on the zone's edge rows (row 0 south, row 7 north, column 0 west, column 7 east). In a
template the parlour has doors east, south and west and a fireplace on the north wall. A copy turned r times moves each side
clockwise r steps (south, west, north, east).

| Id | Room | South-west tile of the zone (first style) | Doors |
|---|---|---|---|
| 0 | Parlour | 1856, 7096 | E S W |
| 1 | Garden | 1856, 7048 | N E S W |
| 2 | Kitchen | 1872, 7096 | S W |
| 3 | Dining room | 1888, 7096 | E S W |
| 4 | Workshop | 1856, 7080 | N S |
| 5 | Bedroom | 1904, 7096 | S W |
| 6 | Hall (skill trophies) | 1864, 7088 | N E S W |
| 7 | League hall | 1896, 7104 | E S W |
| 8 | Games room | 1896, 7072 | E S W |
| 9 | Combat room | 1880, 7072 | E S W |
| 10 | Hall (quest trophies) | 1896, 7088 | N E S W |
| 11 | Menagerie | 1912, 7040 | N E S W |
| 12 | Study | 1888, 7080 | E S W |
| 13 | Costume room | 1904, 7048 | S |
| 14 | Chapel | 1872, 7080 | S W |
| 15 | Portal chamber | 1864, 7072 | S |
| 16 | Formal garden | 1872, 7048 | N E S W |
| 17 | Throne room | 1904, 7080 | S |
| 18 | Superior garden | 1896, 7040 | N E S W |
| 19 | Portal nexus | 1880, 7104 | N E S W |
| 20 | Achievement gallery | 1864, 7104 | N S |
| 21 | Oubliette (basement) | 1904, 7064 | N E S W |
| 22 | Dungeon cross (basement) | 1856, 7064 | N E S W |
| 23 | Treasure room (basement) | 1912, 7072 | S |
| 24 | Dungeon corridor (basement) | 1888, 7064 | N S |

Zones not used: (1880, 7088) skill hall and (1912, 7088) quest hall (same hotspots, the rug area altered around the staircase),
(1912, 7056) menagerie (no habitat hotspots) and (1872, 7064) dungeon with a staircase. They look like the staircase variants
(OPEN-CO6). Style n adds 64 n to the x of every zone. The same zone on level 0, 1, 2 differs slightly (wall loc ids, the stair
hotspot), which is why the template level equals the floor.

## OPEN

- **OPEN-CO1: the Buy-plank interface.** The Wiki shows a dedicated "Sawmill interface" the cache gives no name for. Plank type and
  amount are asked with the chatbox option menu (planks 1-4, then "More..." for 5-7) and the number prompt instead, and the game
  messages are plausible, not sourced. Sawmill vouchers (`sawmill_coupon*`: twice the planks per log) and the plank sack are not
  implemented, and the "Dragon Slayer I free-to-play" nine-plank variant is not either.
- **OPEN-CO2: house template.** Answered above (zones, doors, styles). Not verified: which style column has which name; the style
  (`poh.style`) can only be changed in the saved values (no estate agent).
- **OPEN-CO3: furniture not in the table** (see Data) and the quest/skill-hall requirements, optional inputs (any nail type in place
  of the listed one; the Wiki names one) and boosts (carpenter's outfit +2.5% xp, mythical cape 123 xp per teak plank, crystal saw
  and tea level boosts). Built furniture is mostly only placed (the chapel altar and burners, servants, workbenches and the cape do something: see the last section; pools, portals, larders,
  lecterns, costume storage, menagerie pets, the jewellery box and the Portal nexus do not).
- **OPEN-CO4: interfaces.** The furniture and room pickers are chatbox option menus (4 entries a page, "More..."), not
  `poh_furniture_creation` 458 / `poh_furniture_creation_menu` 397 / `poh_add_room` 212. Client scripts 1405, 1406, 1407, 1632 and
  7987 build 31 slots of 458 from enum 1461 and answer with a resume-pausebutton, but the script that opens it and its argument
  order could not be recovered from the disassembly. The viewer `poh_viewer` 422 (moving rooms), the loading screen `poh_loading`
  71, the board `poh_board` 52 and the rest of `poh_options` 370 (Expel guests, doors setting, Call servant, default build mode)
  are not driven. The options panel's "Build mode" and "Leave house" buttons are registered, but `SettingsTab` opens the panel
  without enabling their events, so use the portal ops.
- **OPEN-CO5: estate agent.** Buying the house (1,000 coins) or Daddy's Home, moving it to another location, redecorating the style
  and the unlock rules other than the Wiki table above are not implemented; the first entry creates the house free.
- **OPEN-CO6: floors and yard.** Only the ground floor can be built: upper-floor and basement rooms exist in the template table and
  the save format, but no staircase, trapdoor or dungeon entrance moves a player between floors, so the menu offers only
  ground-floor rooms. The yard around the house is left empty (void, blocked) instead of the Wiki's yard. "Stair space" hotspots
  are ignored. Rooms cannot be moved (viewer).
- **OPEN-CO7: texts and animations.** The game messages, menu titles and the animation per hotspot kind (floor `HUMAN_POH_BUILD_FLOOR`
  3683 for ground-decor hotspots, wall `HUMAN_POH_BUILD_WALL` 3684 for wall and wall-decor hotspots, otherwise `HUMAN_POH_BUILD`
  3676) are plausible, not sourced. Remove takes a single tick for lack of a source.
- **OPEN-CO8: hotspot visibility.** The Wiki says vacant hotspots appear (as ghosts) only in building mode and that the room doors
  become white door hotspots. The cache hotspot locs are not multi-locs and no client hook for it was found; varbit
  `poh_building_mode` 2176 is set (the options panel reads it), but the server hides the hotspots itself by deleting them outside
  building mode and closes unconnected doorways with the room's own wall loc. If the real client already hides them the deletions
  are harmless. Unverified in the real client.
- **OPEN-CO9: guests.** The Ignore list, the Ironman rule, the "Private" chat setting, the House Advertisement board and Kick /
  Expel Guests are not implemented. "Lock" is a plain flag.
- **OPEN-CO10: built loc ids.** Each hotspot loc becomes the piece's loc of the same index among the hotspot's sorted loc ids (a
  rug's centre / edge / corner, a chair's three hotspots); a piece with fewer ids reuses its last. The Wiki lists the ids but not
  which hotspot loc each belongs to, so multi-id pieces (rugs, boxing rings, prisons) may have a wrong id on a tile. The built loc
  keeps the hotspot's shape and angle. Furniture is saved by its first loc id.
- **OPEN-CO11: logout.** A player logging out inside a house is moved to the tile they entered from first (so the saved coordinate
  is outside). After a crash they are in instance space, where the engine moves them to the world spawn (instances.md IN5).

## Chapel, servants, flat-packs and the cape

Code: `PohAltar.kt` (altar, burners), `PohServants.kt` (servants), `PohFlatpacks.kt` (workbench), `PlayerOwnedHouse.kt` (cape op), the tables
`construction-extra.tsv` and `construction-flatpacks.tsv`; the Mahogany Homes minigame is mahogany-homes.md. Extra sources (fetched
2026-11-07): (K) "Chapel" (altar and lamp tables), "Gilded altar", the infoboxes of "Incense burner (Oak)", "Incense burner (Mahogany)",
"Incense burner (Marble)", "Wooden torches"; (V) "Servant", "Hiring servants", "Servants' Guild", "Rick", "Chief servant"; (P) "Flatpack",
"Workbench"; (T) "Construct. cape".

| # | Rule | Source |
|---|---|---|
| CO13 | The furniture table lacks the built loc ids of the chapel's altars, lamps and the marble burners (versioned Wiki infoboxes). `construction-extra.tsv` replaces those rows (altars Oak 45 / Teak 50 / Cloth 56 / Mahogany 60 / Limestone 64 / Marble 70 / Gilded 75, with the Saradomin, Guthix, Zamorak and gnome child locs; wooden and steel torches, steel and gold candlesticks, oak / mahogany / marble incense burners with their unlit and lit locs) and adds "Marble burners" (level 69, 1,040 xp, 2 marble block, 2 steel bar) and "Bench with vice" (level 62, 580 xp as the Wiki's "Total" recipe, 8 oak plank, 5 steel bar). The first loc id is the one built. The chapel's two lamp spaces are one: building or removing on either does both (K). | K, P |
| PO1 | The altar's op 1 "Pray" restores prayer points to the base level. | K |
| PO2 | A bone used on a chapel altar in a house sacrifices the first bone of the backpack (not the one used; Wiki change 15 February 2023) for its bury xp times the altar's percent with 0 / 1 / 2 lit burners in the room: Oak 100 / 150 / 200, Teak 110 / 160 / 210, Cloth 125 / 175 / 225, Mahogany 150 / 200 / 250, Limestone 175 / 225 / 275, Marble 200 / 250 / 300, Gilded 250 / 300 / 350. With both burners lit the message is "The gods are very pleased with your offering.", else "The gods are pleased with your offering." The Prayer level of the bone applies as at the Chaos altar. | K |
| PO3 | A burner's op 1 "Light" / "Re-light" needs a tinderbox and a clean marrentill (taken) and puts the lit loc on the burner; it goes out again after a time (OPEN-CO17). Torches and candlesticks are cosmetic. | K |
| SV1 | Servants: Rick (Construction 20, 500 coins, 6 items, trip 100 ticks), Maid (25, 1,000, 10, 50), Cook (30, 3,000, 16, 28, sawmill), Butler (40, 5,000, 20, 20, sawmill), Demon butler (50, 10,000, 26, 12, sawmill). Hired by talking to one at the Servants' Guild (the guild's servants are the same npc types): the Construction level (may be boosted), two bedrooms with a bed built in each, and no servant yet. Dismiss: the servant at home or the chief servant (npc 3095). The saved type is `poh.servant`. | V |
| SV2 | At home the hired servant stands beside the exit portal while the owner is in the house; "Talk-to": fetch one of plank, oak plank, teak plank, mahogany plank, soft clay, limestone brick, steel bar, bolt of cloth, gold leaf, marble block, magic stone from the bank (an amount up to the capacity), or (cook and better) take logs to the sawmill (planks as the sawmill, fee per plank). The request is only recorded; after the trip time the servant says it is back, and talking to it then does the move in one item transaction (bank to backpack; logs and coins to planks), so nothing is held by the servant. | V |
| SV3 | After eight services the wage is due; the servant does nothing else until it is paid or it is dismissed. | V |
| FP1 | "Work-at" a workbench (wooden 20, oak 40, steel framed 60, bench with vice 80, bench with lathe all) makes the flat-pack of any piece up to that level whose cache obj is known (80 pieces, `construction-flatpacks.tsv`): level (boostable), hammer and saw and the same materials as building it, 8 ticks, un-noted, with the piece's xp. | P |
| FP2 | In build mode a flat-pack held for a piece that fits the hotspot is offered first in the build menu and builds it without the level, the materials or any xp ("No experience is received when using a flatpack on a building hotspot"). | P |
| CP1 | The Construction cape (9789, 9790) backpack op 3 "Tele to POH" enters your own house wherever you are, unlimited; leaving puts you outside the house portal. | T |

### Added OPEN

- **OPEN-CO12 .. OPEN-CO15:** Mahogany Homes, see mahogany-homes.md.
- **OPEN-CO16: still not buildable.** Pieces whose Wiki infobox is versioned or has no id and that are not in `construction-extra.tsv`: ranges,
  ovens, sinks, firepits, shelves (the pairs of ids in the Wiki are not documented as versions or lit states), chests, wardrobes, armour
  cases, costume boxes, toy boxes, trapdoors, statues, windows, curtains, thrones, shields, the combat dummy, banner stands, the
  jester, pools, plants, trees. The ids the Wiki lists were fetched with `tools/wiki-import/construction_extra.py` (run it again to see them).
- **OPEN-CO17: burner time.** "Burn longer with a high Firemaking level" (Wiki change) but no number is given; `BURN_TICKS` is 100 as a
  placeholder. Who may light a burner (owner only, guests) is not sourced; any player in the house can.
- **OPEN-CO18: altars.** The god of the altar (Wiki: from the chapel icon) is not modelled: the Saradomin loc is built and all four are
  accepted. The pace between two offerings is the Chaos altar's 2 ticks (prayer-training.md OPEN-PT4); the message with one burner lit is
  the "pleased" one. `PrayerTraining.kt` was not touched: `PohAltar` reads `remains.tsv` itself.
- **OPEN-CO19: servants.** Not implemented: the bell-pull and the options panel's "Call servant", serving food and drinks, greeting and
  leading guests, un-noting, "take items to the bank", the servant's money bag, following the owner. Whether hiring costs the wage up
  front is not documented (the first wage is taken after eight services). The servant is spawned as a normal npc at the house tile and
  despawned with the house; unverified in the real client. Texts are plausible. Hiring is by talking to any servant outside a house.
- **OPEN-CO20: flat-packs.** Item on hotspot is not wired (hotspot loc ids are only known after a room is copied), so the build menu
  offers held flat-packs instead. The Wiki does not say that creating a flat-pack gives the piece's xp; it is assumed (the only place
  the xp can come from). Pieces whose cache obj name differs from the furniture name (e.g. 4-poster bed, crude wooden chair, wardrobes)
  have no flat-pack in `construction-flatpacks.tsv` and cannot be made. The Bench with vice and Bench with lathe rows are the Wiki's
  totals / upgrade recipes; upgrading a bench in place ("Upgrade" op 4) is not implemented.
- **OPEN-CO21: cape.** Only the backpack op "Tele to POH"; the worn op, the "Teleport" sub-options (Home, Rimmington ... Prifddinas),
  the teleport animation and the arrival tile ("just north of the exit portal" or outside the portal depending on the house setting)
  are not implemented; the arrival is the usual tile beside the exit portal.
- **OPEN-CO22: not done.** Basement and dungeon rooms are in the room table (CO6) but no stairs, trapdoors or dungeon doors connect floors;
  the oak and other dungeon doors, the jewellery box, the ornate pool and other pools, the portal chamber / nexus teleports and the
  mounted cape / talisman teleports, the larder, the lectern, costume storage and the menagerie have no behaviour. Proposed order: (1) floor
  links (the "Stair space" hotspots and the dungeon entrance: a build menu entry per staircase, "Climb-up / Climb-down" moving the player
  to the same tile on the next instance level, rooms of the floor above or below having to exist), then the dungeon doors; (2) pools
  (op "Drink": Wiki restore values; they need the combat module's stat restore for run energy, hitpoints, prayer, special attack and
  stat boosts, so that part is a request to the main session); (3) the portal chamber and nexus teleports through the existing
  teleport helpers of content/magic.
