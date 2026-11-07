# Sailing: boats, parts and facilities

Skeleton for milestones S1 (raft, boarding) and S4 (building, facilities). Implementation: `content/sailing` (`SailingData`
reads the tables; no behaviour yet). Research and full tables: docs/plans/sailing.md sections 2 and 6.2. Rule ids are
referenced from the KDoc of the readers.

Sources: cache 2757 db tables 164-166, 175-186, 230 (`./gradlew :tools:cache-dump:run --args="dbrows 166"`); OSRS Wiki
https://oldschool.runescape.wiki/w/Boat, /Raft, /Hull, /Mast_and_sails, /Helm, /Keel, /Facilities, /Cargo_hold, /Shipbuilding,
/Sailing/Level_up_table. Wiki cross-check rows: `part-stats.tsv` (tools/wiki-import/sailing.py, not loaded by the server).

## Rules

| # | Rule | Source |
|---|---|---|
| B1 | Four boat types, `Sailing_Boat` (db 166) `TYPE_ID`: 0 raft (Sailing 1, 2 players, 0 crew, 1 hotspot), 1 skiff (15, 5 players, 2 crew, 7 hotspots), 2 sloop (50, 10 players, 5 crew, 13 hotspots), 3 Will and Anne's boat (temporary, quest only, static facility). Boats owned: 1 at Sailing 1, 2 at 15, 3 at 50, 4 at 78, 5 at 91; one boat per port. | cache db 166; Wiki Boat; plan 2.1 |
| B2 | Recovery cost: cache base `RETRIEVAL_COST` 250 / 3,750 / 50,000 / 3,750 plus the hull's `ADDITIONAL_RECOVERY_COST_PERCENTAGE` (0, 5, 10, 15, 20, 25, 30 by tier). The Wiki shows 4,125 for a skiff, which equals base plus 10% (a teak hull) [D, hypothesis, OPEN-B2]. | cache db 164 col 34, db 166 col 18; Wiki Boat |
| B3 | A part row lists its requirements: `SAILING_REQUIREMENT`, `CONSTRUCTION_REQUIREMENT` (boostable), `CONSTRUCTION_COINS_ALTERNATIVE`, `QUEST_REQUIREMENT` and `MATERIAL` as (obj, quantity) pairs. Nail tier follows log tier. Boat part levels are not boostable. | cache db 176-186; Wiki Hull, Shipbuilding |
| B4 | Each tier of hull, sail, helm and keel has a stats row (db 164): HP, defences, armours, `BASESPEED` and `SPEEDCAP` in fine units (128 per tile), `STORMRESISTANCE`, `RAPIDRESISTANCE`, `CARGOHOLD_SIZE`, hazard resistances, `MAX_WIND_MOTES`, `AMMO_SAVE_PERCENTAGE`, `AUTO_REPAIR_RATE`. A boat's stats are the sum / max over its mounted parts (combination rule: OPEN-B3). | cache db 164; plan 2.2 |
| B5 | A boat's hotspots are `Sailing_Boat.HOTSPOT` tuples (deck tile, a, b, hotspot row); the hotspot row (db 175) lists the facility rows (db 176) buildable there. Tile coordinates are for the tier-0 template; add 8 x the hull tier to x. | cache db 166, 175; plan 2.3, 2.4 |
| B6 | Core part locs are placed by the server on the deck template at the db coordinates (helm, sail, keel, flag, trim, brazier); facility locs replace the hotspot loc when built. | cache db 177-186, 230; plan 2.4 |
| B7 | Cargo hold capacity per boat size and tier is the stats row's `CARGOHOLD_SIZE` (raft 20..120, skiff 30..180, sloop 40..240); inventories 963-967; the hold accepts the objs of db 196 and no noted items. | cache db 164, 196; Wiki Cargo hold |
| B8 | A boat's name is random on purchase; Sleve McDichael renames for one unnoted cider at Sailing 61; the cape allows a free choice (db 187 word lists). | Wiki Boat |
| B9 | Kegs hold one ale each and do not stack; Whirlpool surprise (+2 invisible Sailing for salvaging), Kraken ink stout (encounter timer 115), Perildance bitter (+1 cannon max hit), Trawler's trust (+5% fish), Horizon's lure (+2.5% XP). | Wiki Facilities; plan 2.3 |
| B10 | **Ownership** is stored per boat slot (0..4) in one `saved` Long `sailing.boat.<slot>`: bit 0 owned; bits 1-2 type (0 raft, 1 skiff, 2 sloop); 3-8 dock id (63 = at sea); 9-11 hull tier; 12-14 sail tier; 15-17 helm tier; 18-20 keel tier; 21-36 hitpoints; 37-60 name (three 8-bit word indices, not shown yet, OPEN-B8). `sailing.lastboat` is the slot boarded last, `sailing.lastdock` the dock id last disembarked / docked at (the escape / capsize destination). Tier = index into the db option lists (wooden .. rosewood). Slots available: 1 / 2 / 3 / 4 / 5 at Sailing 1 / 15 / 50 / 78 / 91. | plan 2.1; `BoatSave` |
| B11 | **Templates** (`BoatTemplates`): the deck is level 1 of template zones, tier t at x + 8t. Raft: zone (3840,6456), deck x 3843, z 6458..6460, 1x1 zones, config 1. Skiff: zone (3840,6448), deck x 3843..3844, interior z 6450..6453, 1x1, config 2. Sloop: zones (3840,6432)+(3840,6440), interior x 3842..3844 z 6437..6442, 1x2, config 3. Will and Anne's boat: zone (3840,6400) (not per tier), config 10 (OPEN-W1). Hull ring tiles block walking; part locs are added by the server at the db coordinates (tier-0 template coordinates, level 1, relative to the zone origin). Boarding players appear on a free interior tile: raft (3843,6459), skiff (3844,6450), sloop (3843,6440), W&A (3844,6403). Only level 1 is copied (level 0 is open sea and draws a dark rectangle). | cache map + `collision` dumps; plan 2.4 |
| B12 | **Live boats**: one world entity per owner, tracked in `ActiveBoats` (world attribute). Spawned when the owner boards at the dock the boat is moored at, removed when the owner logs out (guests are put ashore) or boards another boat. Stats come from the mounted parts' db 164 rows; motion stats: hull base speed / speed cap, sail acceleration (default 32 = Wiki 0.5, OPEN-S3), footprint from the config bounds, turn 256 per tick (sloop 128, OPEN-M1). Hitpoints are the sum of the parts' maximum (OPEN-B3). | `Boats` |

## OPEN

- **OPEN-B1:** exact walkable deck rectangle and pivot offset of the skiff and sloop (config bounds 2x5 / 3x10 are larger than
  the hotspot extent). Read the tile flags of each template zone; compare with RuneLite.
- **OPEN-B2:** whether the recovery cost is cache base plus the hull percentage (skiff 3,750 vs Wiki 4,125 fits a 10% hull); confirm
  against a wooden-hull skiff in the Wiki table.
- **OPEN-B3:** how part stats combine into boat stats (sum of HP and defence, speed from hull with sail cap, resistances as
  max); the Wiki states per-part values only.
- **OPEN-B4:** `FACILITY_BOTTLE_ID` is not an obj id; the facility bottle items (`SAILING_*_BOTTLE` objs) must be matched by name.
- **OPEN-S1:** the hotspot tuple codes a (0, 1, 3) and b (1, 2, 3): probably loc rotation and footprint.
- **OPEN-S3:** acceleration is stored only on camphor and better sails (64); the default for lower tiers is not in the cache.
  `CONTENT_RESTRICT_SAILING_SERVERSIDE` (varbit 18166): purpose unknown.
- **OPEN-B5:** where the gangplank puts a boarding player on the deck (the entry tiles above are chosen free interior tiles) and the pivot of the deck inside the config bounds (OPEN-WE3).
- **OPEN-B6:** whether the hull is drawn by level-1 locs of the copied template (assumed; verify in the client).
- **OPEN-B8:** boat names (db 187 word lists) are stored but not shown; the shipwright (buy, repair, recover, rename) is S4.
