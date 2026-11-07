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
| P9 | **Locs and ops.** Gangplank locs 59835 + dock id (docks 0..31), mooring points 59867 + (dock id - 32) (docks 32..58), Wyrmscraig 62397 / 62398; buoys 59769 + dock id. The cache gangplank and mooring locs are multi locs on varbit `SAILING_PLAYER_IS_ON_PLAYER_BOAT` (19104): 0 gives the Board / Board-previous / Board-friend variant (59832 / 59834), 1 the Disembark variant (59831 / 59833). The client sends the base loc id and the op number, so the server keeps 19104 equal to "standing on a player boat" and branches on it. The helm is the multi loc 59554 + 3t (raft), 59576 + 3t (skiff), 59598 + 3t (sloop) on varbit `SAILING_BOAT_FACILITY_LOCKEDIN` (19105): 0 Navigate, 1 Stop-navigating; op 4 Escape. | cache loc configs 59554, 59769, 59831-59835, 59867 |
| P10 | **Board**: lists the player's boats moored at this dock (a chatbox menu, interface 934 is OPEN-SEL1); one boat boards at once. The boat is spawned beside the buoy if it is not in the world, and the player is put on its deck. Capacity = `PLAYER_CAPACITY` of the boat row. The Sailing level of the boat type is needed (not boostable). **Board-previous**: the boat of `sailing.lastboat` if moored here. **Board-friend**: asks for a name; boards that player's moored boat at this dock; anyone may be a guest. | plan 2.5; Wiki Gangplank, Boat |
| P11 | **Disembark** (gangplank or mooring point op 1 while aboard): the boat must be within 15 tiles of the dock's land tile (OPEN-P5, value assumed); the player returns to the tile they boarded from, or the first walkable tile around the landing; the owner's boat is moored at the dock pose and the dock is saved as the last dock. A guest only leaves. The Sailing level of the dock (db 194) is needed to dock (not boostable); quest and visit gates other than level are OPEN-P6. | Wiki Gangplank, Mooring point |
| P12 | **Buoy Dock**: the owner's boat within 15 tiles of the buoy is moored at the dock pose and the dock becomes the escape / capsize destination. **Escape** (helm op 4): the player goes ashore at the last dock; the boat is moored there (no recovery fee, OPEN-ESC). | Wiki Buoy, Helm |
| P13 | **Dock pose**: boat pivot on the buoy's south-west tile, bow pointing away from the gangplank along the dominant axis (OPEN-P5: the real rotation is the stripped `BOAT_ROTATION` column). Land and sea tiles are `docks.tsv` / `dock-buoys.tsv` joined to db 194 by name with punctuation ignored. | `DockSites` |

## OPEN

- **OPEN-P4:** meaning of the port columns on bounty rows (board port assumed; checked on the Port Sarim rows only).
- **OPEN-T1:** courier / bounty XP formula (imported data used instead).
- **OPEN-P1:** the Wiki docking-point table has no row for Last Light (cache dock, buoy (2845,2334) in `dock-buoys.tsv`); its land
  coordinate, level facts and facilities are unsourced.
- **OPEN-P2:** ledger-table and notice-board tile coordinates per dock: only the two quest tasks give ledger pins in the Wiki
  (`courier-tasks.tsv` `ledger_*` columns); the rest come from the cache map (loc placements `LEDGER_TABLE_ICON_*`, `PORT_TASK_BOARD_*`).
- **OPEN-P3:** guaranteed bounty per board (`PORT_TASK_GAURANTEED_BOUNTY`, stripped): the Wiki says each board has exactly one
  always-available bounty.
- **OPEN-P5:** the docking radius (15 tiles) and the mooring pose are assumptions.
- **OPEN-P6:** quest and visit gates of docks other than the level.
- **OPEN-SEL1:** boat selection interface 934 is replaced by a chatbox menu.
- **OPEN-ESC:** Escape does not charge a recovery fee or mark the boat capsized.
- **OPEN-DECK1:** whether the engine routes a click on a root-world gangplank loc made from a deck (the disembark path); if it does not, the engine must accept it.
