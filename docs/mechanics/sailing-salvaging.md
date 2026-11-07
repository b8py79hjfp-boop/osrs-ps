# Sailing: shipwreck salvaging

Milestone S6 (implemented in `Salvaging.kt`, `SalvageData.kt`). Research: docs/plans/sailing.md section 5.2. The cache has no wreck table
(db 202 / 203 are empty client-side), but the map holds a stump loc on every wreck site, which gives the sites.

Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Shipwreck_salvaging, /Crew_Management, /Sailing_training, /Salvaging_hook
(facility tiers in /Facilities); cache 2757 salvage objs `SAILING_*_SHIPWRECK_SALVAGE` 32847-32861, `SALVAGING_RARE_*` 32863-32873,
varp `SAILING_SHIPWRECK_RAREITEMS` 5208; hook stats db 176 (subtype 1).

## Rules

| # | Rule | Source |
|---|---|---|
| S1 | Seven salvaging hook tiers (bronze 15 .. dragon 86; Construction 1, 9, 18, 30, 52, 66, 78); crew deckhandiness cap 1, 1, 1, 2, 2, 3, 4. | cache db 176 subtype 1; Wiki Facilities, Crew Management |
| S2 | Range 7 tiles from the edge of the wreck, measured from the hook's position. Player cycle 4 ticks (the 5 Dec 2025 hotfix restored 4 after a one-day 5); crew cycle 5 ticks. | Wiki Shipwreck salvaging, Sailing training |
| S3 | Wreck types (level, salvage XP, sorting XP, average life): small 15 (10, 5.5, 1 min), fisherman's 26 (17, 9, 3 min), Barracuda 35 (31, 15.5, 3 min), large 53 (48, 24, 3 min), pirate 64 (76, 31.5, 3 min), mercenary 73 (138, 63.5, 3:15), Fremennik 80 (162, 75, 3:40), merchant 87 (200, 95, 4 min). | Wiki Shipwreck salvaging |
| S4 | Crew salvage XP is 10 / 20 / 30 / 40% of the player's by deckhandiness 1..4 (3 and 4 reduced from 30 / 40 on 5 Dec 2025 as listed on the Wiki). | Wiki Crew Management |
| S5 | A cluster shares a spawn pool (typically 4 of 6 sites active); the despawn timer starts when anyone starts to salvage; salvaging is refused in hazardous waters. Salvage is sorted at a salvaging station (port or boat facility) for sorting XP and items. | Wiki Shipwreck salvaging |

| S6 | **Sites.** The cache map places a stump (`SAILING_*_SHIPWRECK_STUMP`, 60465 ... 60479, shape 10) on each of the 186 sites (small 16, fisherman 18, Barracuda 43, large 30, pirate 24, mercenary 18, Fremennik 18, merchant 18); their tiles are the Wiki's map pins and their angles are the wrecks'. Sites of one type within 80 tiles of each other (single linkage) form a cluster (hotspot): clusters of 6 (8 for small), the Barracuda sites merge into clusters of 10 and 14 and one stray [D]. `salvage-wrecks.tsv`. | cache map (`find-locs`, `map`); Wiki Shipwreck salvaging |
| S7 | **Pool.** In each cluster all but 2 sites show their wreck (loc 60464 + 2 x type, same tile and angle), the rest keep the stump; at start the first sites in file order are up. The first roll of anyone on a wreck starts its life timer (the type's average duration in ticks: 100, 300, 300, 300, 300, 325, 367, 400). When it ends the wreck becomes its stump and a random down site of the cluster (the starter's RNG) rises; with no other down site it rises again. | Wiki Shipwreck salvaging, Spawn mechanics |
| S8 | **Hook.** Op "Deploy" (op 1 of the 28 hook locs, by cache name "<Tier> salvaging hook") on the deck: needs the boat, the facility's Sailing level (current level, `ShipbuildingData.canOperate`) and the wreck's level (boostable). The nearest standing wreck whose edge is within 7 tiles of the boat's pivot is salvaged; every 4 ticks the hook rolls `statRandom(SAILING, low, high)` and a hit adds one salvage obj of the wreck's type to the backpack and pays its salvage XP. The run stops when no wreck is in range (with the Wiki's message when it sank), on a full backpack or on any other action. Crew do not salvage yet (S9). | Wiki Shipwreck salvaging, Salvaging hook |
| S9 | **Sorting.** Op "Sort-salvage" on any loc named "Salvaging station" (ports, 60462, 60463 and the boat facility 59699-59701): one piece of salvage from the backpack every 3 ticks, needs the wreck type's level; pays the sorting XP (5.5, 9, 15.5, 24, 31.5, 63.5, 75, 95) and rolls the type's Wiki drop table (`salvage-loot.tsv`, 193 lines of 8 tables): every "Pre-roll" and "Tertiary" line on its own at num/den, then one line of the other sections by weight over their common denominator. Items that do not fit are dropped. | Wiki Salvage, the eight salvage pages, Salvaging station |
| S10 | The 8 Soup lines (pet, 1/800,000 per sort) are not rolled; clue lines give the clue scroll item of the tier. | Wiki salvage pages |

## OPEN

- **OPEN-SV1:** cluster boundaries are derived (see S6); the Barracuda clusters (the Wiki lists 6 areas) are merged wrongly into 10 and 14. Which sites are up at world start, and the pool refill rule, are the Wiki's description only.
- **OPEN-SV2:** success rates per hook tier, wreck and Sailing level (the Wiki gives only the shape: hook and level raise it, crew get D x 10% of the player's). `Salvaging.SUCCESS_*` are placeholders. The merchant wreck is as the Wiki's table (87, 200 XP, 95 sorting, 4 min). Loot tables are complete.
- **OPEN-SV3:** exact despawn timer distribution (the Wiki gives averages only).
- **OPEN-SV4:** the hook's own position (the Wiki measures 7 tiles from the hook) is the boat's pivot here; the wreck footprint is a square of the wreck loc's longer side from its tile.
- **OPEN-SV5:** whether a pre-roll hit replaces the main roll (independent here).
- **OPEN-SV6:** salvage goes to the backpack; storing it in the cargo hold when the backpack is full is not done. Salvage hazards ("it is not safe to salvage while in hazardous waters") are not checked (hazard areas, OPEN-H1).
- **OPEN-SV7:** the 3-tick sort cadence (the Wiki quotes about 1800 sorts an hour) and any sorting animation or interface; the invisible +2 level of the whirlpool surprise keg (needs kegs, S11); the Horizon's lure XP bonus.
- **OPEN-SV8:** the Soup pet (no pet infrastructure here); salvage items are not noted in the collection log.
- **Crew salvaging** (S9): crew salvage every 5 ticks at D x 10% of the player's chance and XP (S4 above), from the hook's position; needs NPCs on decks (OPEN-WE2).
