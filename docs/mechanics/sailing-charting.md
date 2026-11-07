# Sailing: sea charting

Skeleton for milestone S5. Research: docs/plans/sailing.md section 5.1. `content/sailing` reads `Sailing_Charting_Core` (db 154)
joined with its type table, `Sailing_Sea` (db 201) and the tool recovery rows (db 161).

Sources: cache 2757 db 154-161, 201 (`dbrows 154`); OSRS Wiki https://oldschool.runescape.wiki/w/Sea_charting (the `SeaChartRow`
templates, imported to `charting-tasks.tsv` by tools/wiki-import/sailing.py), /Sailing, /Captain%27s_log, /Spyglass.

## Rules

| # | Rule | Source |
|---|---|---|
| C1 | 358 tasks. `Sailing_Charting_Core` columns: `UNIQUE_ID` (the Wiki's `SeaChartRow` id), `HINT`, `SAILING_SEA` (+ secondary), `GIVE_REPEAT_XP` (set on the 54 current-duck tasks), `CHARTING_TYPE` (the id of the type table), `HAZARD` (db 200 row), `REQUIRED_DOCK` (db 194 row, 6 tasks). Type tables hold only the back link to the core row in the shipped cache (counts: generic 94 db 155, spyglass 57 db 156, current duck 54 db 157, drink crate 65 db 158, weather troll 31 db 159, mermaid guide 57 db 160). The Wiki calls drink crate "Crate" and mermaid guide "Diving". | cache db 154-160; Wiki Sea charting |
| C2 | One-off XP per task by type: generic 35, spyglass 50, crate 75, current 125, diving 175, weather 250. Sea and ocean completion bonuses are claimed from Chartin' Charles McAtless; all 358 tasks give +100,000; total 691,270. | Wiki Sea charting |
| C3 | Tool requirements and levels: generic needs the Captain's log (Sailing 1); spyglass; crate needs a crowbar and Prying Times (level 12); current needs the current duck and Current Affairs (22); diving needs a fishbowl helmet and diving apparatus or the Medallion of the Deep (38); weather needs a portable weather station (57). Charting levels and hazards are boostable (since 10 Dec 2025); tasks are once-off. | Wiki Sea charting, Sailing |
| C4 | Task locations are server-only; `charting-tasks.tsv` has the Wiki's `location` (tile x,z) and `location2` (second point of a current route), the sea and ocean names, the task level, the hazard and the hint. Sea names match `Sailing_Sea.NAME` of the cache. | Wiki Sea charting |
| C5 | Completing a sea places its rapids (70 sea rapids, locs `SAILING_CHARTING_RAPIDS_*` 59376-59445). Soup pet 1/30,000 per task. | Wiki Sea charting; cache locs |
| C6 | `Sailing_Sea` rows: `TYPE` 0 sea (70), 1 ocean (6), 2 bonus group (4: Drinks, Currents, Dives, Weather), 3 Miscellaneous; `REQUIRED_LEVEL`, `CENTRE_COORD`. Ocean levels: Ardent 38, Unquiet 45, Shrouded 72, Sunset 38, Western 66, Northern 78. | cache db 201 |

## OPEN

- **OPEN-C1:** per-type behaviour data (spyglass cutscene camera, duck routes, drink effects, weather data tiles) is stripped from
  the cache and only partly on the Wiki; later S5 work needs the Wiki's tile-marker pages per type.
- **OPEN-C2:** Wiki `hazard` and `sea` names versus cache rows (join by name; verify there is no mismatch before S5).
- **OPEN-C3:** meaning of `Sailing_Sea.XP_LEVEL` (set on two rows) and the `HAS_CHARTING_RAPIDS` flag in the cache (unset on all rows read).
