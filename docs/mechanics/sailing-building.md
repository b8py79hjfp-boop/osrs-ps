# Sailing: boat building, the shipyard and the shipwright services (S4)

Milestone S4 of docs/plans/sailing.md (2.2, 2.3, 7.3). Implementation: `content/sailing` - `Shipyard`, `BoatBuilder`, `Customisation`,
`BoatServices`, `BoatStats`, `ShipbuildingData`, `BoatItems` and the changed `Boats`, `ActiveBoats`, `BoatSave`. Data: cache db 164, 166,
175-189, 230 (the part, facility, hotspot, stats and tab tables); Wiki-only Construction XP and shipwright fees in
`content/sailing/src/main/resources/tp/content/sailing/shipbuilding.tsv`, written by `tools/wiki-import/sailing_build.py` from
`part-stats.tsv` and the Cargo hold, Salvaging hook, Cannon (facility), Eternal brazier, Flags and Sail patterns pages.
Client scripts read for the interface: 8807-8846, 9055-9078 (disassembly `rs2asm`, the Sailing block).

Sources: Wiki https://oldschool.runescape.wiki/w/Shipbuilding, /Shipyard, /Boat_schematics, /Facility_hotspot, /Facilities, /Hull,
/Mast_and_sails, /Helm, /Keel, /Cargo_hold, /Salvaging_hook, /Cannon_(facility), /Eternal_brazier, /Flags, /Sail_patterns,
/Boat_paint, /Crystal_saw, /Shipwright, /Shipwright_Seb, /Sleve_McDichael, /Boat, /Boat_bottle, /Facility_bottle. Cache 2757.

## Rules

| # | Rule | Source |
|---|---|---|
| BU1 | A build needs: Sailing level and Construction level of the cache row (both boostable, so the current level counts; a crystal saw in the backpack adds 3 Construction levels), the lost schematic where one is needed (BU3), a hammer and a saw (facilities only; the crystal saw replaces the saw; core parts need no tools), and every material of the row (`MATERIAL` tuples, noted items do not count). Nothing is taken unless every step passes. | Shipbuilding; Crystal saw; Facilities; cache db 176-186 |
| BU2 | Tick timeline: tick T the player clicks Build (or Build on a hotspot then Build in the menu): the interface closes, the build is checked, the build animation plays; tick T+5 (`BUILD_TICKS`, OPEN-BLD2) it is checked again, materials (and fee) are taken in one item transaction, the part/facility is saved, the locs on the deck are swapped (zone update goes out the same tick), the stats are recomputed and the XP is added; the chat line follows. If the player left the shipyard in between nothing happens. | Eternal brazier recipe `ticks = 5`; ours |
| BU3 | The tiers whose schematic must be found first are hidden by client script 9078 and refused by the server unless the lost-schematic varbit is set: rosewood hull (19550, db rows 8270/8277/8284), rosewood sail (19551), dragon helm (19552), dragon keel (19553), rosewood cargo hold (19548), dragon hook (19547), dragon cannon (19549), eternal brazier (19546), salvaging station (19544), gale catcher (19545), ballistic attractor (20227), bosun's workbench (13318). Finding the schematics is not implemented (Lost schematics, later work). | cache client script 9078; Lost schematics |
| BU4 | **Shipwright fee.** If only the Construction level is missing and the row's `CONSTRUCTION_COINS_ALTERNATIVE` is set (oak, teak and mahogany tiers / mithril facilities of keels, hulls, masts, helms, cargo holds, cannons and salvaging hooks), a shipwright builds it for the fee of the Wiki tables in coins, taken from the backpack together with the materials. The Wiki lists no fee above mahogany/mithril; the cache flag of the rosewood cargo hold row is ignored because the Wiki says N/A. | Shipbuilding "Shipwright assistance"; Hull, Mast, Helm, Keel, Cargo hold, Salvaging hook, Cannon pages |
| BU5 | Construction XP is added when the player's own Construction level was enough. A build the shipwright did (BU4) or from a facility bottle (FB2) gives none (OPEN-BLD4). The XP per part/facility is the Wiki value (hull wooden raft 19, dragon helm 780, brazier 1,373, flags 34, sail patterns 1 (plain) / 10, bronze cannon 157 ...). Trims give no XP (OPEN-BLD9). Sailing XP: none. | Raft, Hull and the other pages; Eternal brazier; Flags; Sail patterns |
| BU6 | **Boost rules.** Levels are checked on the current (boosted) level at build time. The cargo hold is a support facility: it needs no level after it is built ("the boost does not need to be maintained"). Every other facility is active: the Sailing level of the row must hold when a player or crew uses it (the salvaging hook page states it; applied to all, OPEN-BLD6). The user of an active facility checks `p.level(SAILING) >= facility.requirements.sailing`; support = `FACILITY_SUBTYPE` 2. | Cargo hold; Salvaging hook |
| BU7 | A facility fits a hotspot only if it is in the hotspot row's option list (db 175 `OPTION`); at most `BUILD_MAX` of a subtype per boat (cargo hold 1, cannon 2). The saved value is the option index + 1. Replacing a facility needs no removal first; the old one is lost with no refund. | Facility hotspot; cache db 175, 176 (OPEN-BLD5) |
| BU8 | A cargo hold is only changed, removed or bottled while its inventory (963 + slot) is empty (OPEN-BLD8). | ours |
| BU9 | Building a different hull or keel heals the boat to full hitpoints (`rebuild` clamps the hitpoints to the new maximum otherwise). | Boat, 3 December 2025 |
| BU10 | Part locs: helm, sail (the pattern's loc for a coloured pattern, OPEN-BLD3), keel, flag, trim, brazier and the static facility are placed at their db coordinates; a built facility is placed at the hotspot tile with the facility row's loc, shape 10 (centrepiece) and the hotspot tuple's `a` as the angle (OPEN-S1); in the shipyard an empty hotspot shows its placeholder loc (raft 59663, skiff 59664+i, sloop 59671+i, 60720, 60721; op 1 "Build"), elsewhere nothing. A different hull tier copies the other tier's template over the deck zone (which clears every dynamic loc), then everything is placed again. | cache db 166, 175-186; Facility hotspot; `Boats` |
| ST1 | **Stats of a boat** (`BoatStats`, OPEN-B3): hitpoints, defence and armour add up over hull, sail, helm, keel, brazier, flag, trim, pattern and facilities (db 164 rows); base speed, speed cap from the hull; acceleration, boost duration and storm resistance from the sail; rapid and crystal-helm resistance from the helm; the resistance flags (fetid water, crystal-flecked, tangled kelp, icy seas) are true if any part gives them; wind motes, ammo saving and auto repair take the best facility; the recovery percentage is the hull's. Recomputed after every change and pushed into `BoatMotion.Motion.stats`. | cache db 164; Shipbuilding "Stats" |
| ST2 | **Cargo hold size** is `CARGOHOLD_SIZE` of the stats row of the built hold (raft 20, 30, 45, 60, 80, 105, 120; skiff 30 ... 180; sloop 40 ... 240; 0 without a hold): `ActiveBoat.stats.cargoHoldSize`. The hold inventories belong to S3. | Cargo hold; cache db 164 |
| ST3 | Acceleration defaults to 32 (Wiki 0.5) when the sail row has none (OPEN-S3 / OPEN-M3), as `Boats` did before. | plan 2.2 |
| ST4 | A boat spawned for the shipyard has no motion; a moored or sailing one keeps its `BoatMotion` and has its stats replaced in place when it is rebuilt. | `Boats` |
| SY1 | **The shipyard** is the island at (2078, 2720). A shipwright's op 4 "Customise-boat" lists the owner's boats moored at his port (not bottled, not capsized), takes the chosen one into the build space, removes the live boat of that port (everyone else must be off it), puts the player next to the boat schematics board and remembers where he stood. The port is the dock whose land tile is nearest to the shipwright (OPEN-SY3). The boat is a build-space entity with the pseudo dock id 62 so its owner can board it with op 1 on the boat; the helm's Escape (op 4) gets off the deck onto the island. | Shipyard; Shipwright |
| SY2 | Shipyard Portal (loc 59722, op 1 "Exit"), a logout inside the shipyard, or the end of the session: everyone aboard is put on the island, the build-space boat is removed, the boat's hitpoints are saved and the player returns to the tile he left from ("near the Shipwright you used"). The boat stays moored at its port in the save. | Shipyard |
| SY3 | The boat schematics board (loc 59718, op 1 "Modify") opens interface 939 on the core parts; a facility hotspot (op 1 "Build") opens it on that hotspot; a built facility's op 5 "Modify" offers change, remove or never mind. All need the owner's session; facilities need the player on the deck. | Boat schematics; Facility hotspot; cache loc configs |
| SY4 | The build space is the walkable 11 x 8 dry dock at x 2088-2098, z 2720-2727, the boat's pivot at (2093, 2724) facing east, arrival at (2084, 2725) searched outward for a walkable tile (OPEN-SY1). | cache `collision 2096 2723 0`; ours |
| SY5 | A Bank PIN is not asked for (OPEN-SY2). | Shipyard |
| UI1 | **Interface 939** is built by the client: tabs from db 189 (group 0 core parts, group 1 facility categories), the options from the boat's db 166 lists (keel 24, hull 25, sail 26, pattern 27, helm 28, flag 29, trim 30, brazier 31) or the hotspot's db 175 options, "currently built" from the varbits `SAILING_BOAT_n_*` of the boat slot. | cache client scripts 8809-8846, 9064-9069 |
| UI2 | Server inputs: varp 5190 = the `Sailing_Boat` row (8110 raft, 8111 skiff, 8112 sloop); varbit 19523 = 0 core / 1 facilities; 19524 = hotspot index; 19525 = boat slot + 1; then the modal is opened on `MAINMODAL`. | scripts 8812, 8818 |
| UI3 | Mirrored boat varbits (slot n = 0..4, stride 38; hotspots 11-12 at 20207 + 4n): keel, hull, sail, steering = option index; flag, brazier = index + 1; trim = index (0 is "No trim"); sail pattern = index (13313 + n); hotspot k = option index + 1; owned = 1; type. Not mirrored: teleport focus (bits 16-17 of the first customisation varp, OPEN-CUS1). | scripts 9067, 8798-8805 |
| UI4 | The Build button is a full-size child of `FACILITY_CLICK_LAYER` (939:17) whose sub id is the db row; the click arrives as IF_BUTTON op 1 with that row in `slot`. The row is checked against the boat's option list or the hotspot's options before anything is built. | script 8832 |
| UI5 | Chat lines: hull and sail use the board's lines ("You get to work with the shipyard workers to change out the hull of your boat..." / "... the mast and sail ..."), the rest "You build the <name>." (OPEN-BLD9). | Boat schematics |
| SW1 | Shipwright ops (cache 2757): Talk-to 1, Buy-boat 3, Customise-boat 4, Recover-boat 5 (ids 15363-15377 and Junior Jim 14972-14975, op 1 of Jim stays with the Pandemonium quest). Talk-to is a menu of the same services (the dialogue text is not in the Wiki: OPEN-SHIP1); below the level to dock at the shipwright's port only Destroy is offered. | Shipwright |
| SW2 | **Buy:** a type whose Sailing level the player has (raft 1,000 coins, skiff 15,000, sloop 200,000), into the first free slot below the slots his level allows (1/2/3/4/5 at 1/15/50/78/91), moored at the shipwright's port, wooden parts, random name; only one boat per port at a time. | Shipwright; Boat |
| SW3 | **Destroy:** any boat not in a bottle, after a confirmation; parts and cargo are lost; the slot is empty. | Boat "Destroy a boat" |
| SW4 | **Recover:** a boat at another port, capsized, or whose bottle is gone (more bottled boats than full bottles held) is brought to this port for the recovery cost: `RETRIEVAL_COST` + that times the hull's `ADDITIONAL_RECOVERY_COST_PERCENTAGE` / 100 (B2, OPEN-B2), and returns at full hitpoints (OPEN-REC1). Cargo is lost (event `BoatRelocated`). | Shipwright; Boat |
| SW5 | **Repair:** Shipwright Seb's op 4 "Repair-boat" restores any chosen boat to full hitpoints for 50 coins per missing hitpoint, taken from the backpack first and the bank for the rest in one transaction. | Shipwright Seb |
| SW6 | **Rename:** Sleve McDichael's op 2 "Rename-boat" gives any chosen boat a new random name for one unnoted cider. The names are not shown yet (OPEN-B8). | Sleve McDichael |
| SW7 | **Summon** (`BoatServices.summon`, called by the Summon Boat spell of S11, OPEN-S4): needs a teleport focus (`FACILITY_SUBTYPE` 9), not bottled or capsized, the dock's level, no other boat at that port; cargo is lost. | Boat; Summon Boat |
| SW8 | **Cargo loss** on recover, destroy, summon: the event `BoatRelocated(owner, slot, loseCargo)` is published for the cargo content (crates, bounty items, salvage and fish are lost; cannonballs, repair kits, offcuts, empty crates stay). | Boat; Cargo hold |
| BB1 | **Boat bottle** "Bottle-boat" on an empty bottle (31989): the player must stand within `BOTTLE_RANGE` of a gangplank or mooring point (OPEN-P5); a boat moored there, not bottled or capsized, with an empty hold (963 + slot), no one else aboard, goes into the bottle (item 31992); the save has the bottled flag. | Boat bottle |
| BB2 | One bottle item stands for the lowest bottled slot when released (OPEN-BB1: the item carries no data). | ours |
| BB3 | "Unbottle-boat": after a confirmation, at a dock in range whose level the player has and where he has no other boat, the boat is moored there and the bottle is used up. | Boat bottle |
| BB4 | "Check" lists the bottled boat. A full bottle is always kept on death (engine); destroying it makes the boat recoverable (SW4). | Boat bottle |
| BB5 | Saved flags: `sailing.boatx.s` bit 16 bottled, bit 17 capsized (the capsized flag is written by the S2 capsize code, read by the recover and customise rules). | `BoatSave` |
| FB1 | **Facility bottle** (empty 33074, full 33077): an empty bottle used on a built facility in the shipyard stores it (the bottle becomes full, the facility is removed, no refund); the stored kinds (`FACILITY_BOTTLE_ID` 1..42 of db 176) are a list in the player's saved values (`sailing.fbottle.*`, `FacilityBottles`). A keg loses its ale. | Facility bottle |
| FB2 | Building a facility whose kind is stored, with a full bottle in the backpack, offers "use the bottled facility": it needs the hammer and saw and the hotspot to accept the facility, nothing else (no level, no materials, no XP, OPEN-BLD7); the bottle and its entry are used up. | Facility bottle |
| FB3 | "Check" lists the stored facilities; "Empty" pours the oldest out and turns the bottle into an empty one (OPEN-FB2); destroyed bottles leave stale entries which are not checked against the items (OPEN-FB1). | Facility bottle |

## Saved values (S4, additions to B10)

`sailing.boatx.s`: bits 1-4 flag (option index + 1), 5-10 trim, 11-12 brazier (index + 1), 13-15 sail pattern, 16 bottled, 17 capsized.
`sailing.boatf.s.0` / `.1`: hotspots 0-7 / 8-12, 8 bits each, option index + 1. Absent keys read as "nothing built"; the first Long
`sailing.boat.s` is unchanged, so saves from S1 load as before. `sailing.fbottle.count`, `sailing.fbottle.n`: stored facility kinds, 8 per Long.

## OPEN

- **OPEN-SY1:** the build space, boat pose and arrival tile of the shipyard (db 174 `Sailing_Shipyard` is stripped); chosen from the island's collision.
- **OPEN-SY2:** the Bank PIN check before travelling to the shipyard is not implemented.
- **OPEN-SY3:** the shipwright's port is the nearest dock land tile within 60 tiles; the real mapping is a server-only column.
- **OPEN-BLD1:** loc shape/angle of a placed facility (shape 10, angle = hotspot `a`); same as OPEN-S1.
- **OPEN-BLD2:** build time 5 ticks for every part and facility; only the brazier recipe states it. The animation is the POH build one.
- **OPEN-BLD3:** a coloured sail pattern replaces the sail loc with the pattern row's loc at the same tile; the plain pattern keeps the sail's loc.
- **OPEN-BLD4:** no Construction XP when a shipwright or a bottle did the work.
- **OPEN-BLD5:** removing or replacing a facility refunds nothing.
- **OPEN-BLD6:** only the cargo hold is a support facility; others are "active" (their Sailing level must hold on use), not stated for kegs, anchors and others.
- **OPEN-BLD7:** a bottled facility is built without level, materials and XP.
- **OPEN-BLD8:** a non-empty cargo hold blocks changing it.
- **OPEN-BLD9:** trim paints give no Construction XP and need whatever their db materials list; chat lines of the other parts are ours.
- **OPEN-CUS1:** the teleport-focus bits of the boat customisation varp are not mirrored (the part and hotspot encodings were read from the scripts; teleport focus is used by the boat selection interface, S11).
- **OPEN-FB1 / FB2:** a facility bottle item carries no data; entries and items can drift apart; "Empty" removes the oldest entry.
- **OPEN-BB1:** with several bottled boats the lowest slot is released.
- **OPEN-REC1:** a recovered boat returns repaired.
- **OPEN-SHIP1:** shipwright, Seb and Sleve dialogue text; Seb's shop (Trade, op 3) is not part of S4.
- **OPEN-PAN1** (S1): the Pandemonium cargo hold is still fitted by the quest code, not through the builder.
- **OPEN-B2 / B3 / B8 / S1 / S3 / S4 / P5:** as in sailing-boats.md and plan section 8.
