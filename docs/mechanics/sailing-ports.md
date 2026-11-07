# Sailing: mooring points and port tasks

Skeleton for milestones S1 (docking, boarding) and S3 (notice boards, courier and bounty tasks). Research: docs/plans/sailing.md
sections 2.5, 4. Implementation so far: `content/sailing` reads `Sailing_Dock` (db 194), `Port_Task` (db 197) and `Sailing_Sea`
(db 201), and `PortTaskXp` loads the imported task XP.

Sources: cache 2757 db 194, 197, 201 and 149; OSRS Wiki https://oldschool.runescape.wiki/w/Mooring_point, /Courier_tasks,
/Bounty_tasks, /Gangplank_(Sailing), /Buoy_(Sailing), /Port_master, /Sailing. Imported rows (tools/wiki-import/sailing.py):
`docks.tsv`, `dock-buoys.tsv`, `courier-tasks.tsv`, `bounty-tasks.tsv`, `level-ups.tsv`.

## Rules

| # | Rule | Source |
|---|---|---|
| P1 | 61 docks in the cache (`DOCK_ID` 0..60) with name, Sailing level, quest requirement; coordinates are server-only. Land-side map pins come from the Wiki (`docks.tsv`, 60 of 61 rows); sea-side buoy coordinates are cache-derived (`dock-buoys.tsv`, from plan 4.1). Docking requires the level (not boostable) and the quest or visit gates of the Wiki table. | cache db 194; Wiki Mooring point |
| P2 | Gangplank options: Board, Board-previous, Board-friend; on the boat Disembark. Disembarking at a gangplank or mooring point sets the respawn dock and persists. Buoy option Dock sets the capsize / escape destination. | Wiki Gangplank, Buoy |
| P3 | Task slots: 1 at level 1, 2 at 7, 3 at 28, 4 at 56, 5 at 84; courier and bounty share them; bounty tasks unlock at 30. Slot and accept levels are not boostable. | Wiki Courier tasks, Bounty tasks |
| P4 | `Port_Task` (db 197): `TASK_TYPE` 0 courier (443 rows) / 1 bounty (163); `LEVEL_REQUIRED`, `STARTING_PORT`, `CARGO_PORT`, `ENDING_PORT` (db 194 rows), `CARGO` (crate obj, quantity) or the `BOUNTY_*` columns (target npc, part obj, amount, rarity 1/n). Bounty rows fill the three port columns too (for the Port Sarim bounties all three are Port Sarim; read as the board the bounty is posted at, OPEN-P4). Rows 601 "No Task" and 602 "Locked task slot" are slot placeholders, not tasks. | cache db 197 |
| P5 | Task XP is not in the cache. Imported per task id from the Wiki (`courier-tasks.tsv`: 441 of 443 courier ids, the two missing are the placeholders 601 and 602; `bounty-tasks.tsv`: all 163). No closed formula exists (the same route differs by quantity and level), so the imported value is the rule: OPEN-T1. Courier XP +10% since 3 Jun 2026 is already in the Wiki table. | Wiki Courier tasks, Bounty tasks |
| P6 | Courier flow: take crate(s) from the ledger table at `CARGO_PORT` with both hands free (weapons unequipped), load into the cargo hold, sail, withdraw, deposit at the ledger table of `ENDING_PORT`. Own boat only. Teleporting while carrying confiscates cargo. | Wiki Courier tasks |
| P7 | Rewards: Sailing XP; courier 4/5 coin bag or 1/5 reward bag by base XP (tiny 0-399, small 400-999, medium 1,000-2,499, large 2,500-5,999, huge 6,000+); bounty: coins equal to base XP plus a coin bag; shark paint 1/36 per task. | Wiki Courier tasks, Bounty tasks |
| P8 | Boat selection interface 934 modes are the 12 rows of `Boat_Selection_Type` (db 149): their flags (bottled / capsized / lost not allowed, only boats at the current port, needs teleport focus, needs empty hold) are read by `BoatSelectionType`. | cache db 149 |

## OPEN

- **OPEN-P4:** meaning of the port columns on bounty rows (board port assumed; checked on the Port Sarim rows only).
- **OPEN-T1:** courier / bounty XP formula (imported data used instead).
- **OPEN-P1:** the Wiki docking-point table has no row for Last Light (cache dock, buoy (2845,2334) in `dock-buoys.tsv`); its land
  coordinate, level facts and facilities are unsourced.
- **OPEN-P2:** ledger-table and notice-board tile coordinates per dock: only the two quest tasks give ledger pins in the Wiki
  (`courier-tasks.tsv` `ledger_*` columns); the rest come from the cache map (loc placements `LEDGER_TABLE_ICON_*`, `PORT_TASK_BOARD_*`).
- **OPEN-P3:** guaranteed bounty per board (`PORT_TASK_GAURANTEED_BOUNTY`, stripped): the Wiki says each board has exactly one
  always-available bounty.
