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
