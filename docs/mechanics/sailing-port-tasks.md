# Sailing: port tasks, notice boards, ledger tables, cargo holds (S3)

Milestone S3 of docs/plans/sailing.md (4.2-4.4, 7.3). Implementation: `content/sailing` `PortTasks` (slots and life cycle),
`PortTaskSave` (saved state), `PortCatalog` (tasks per board), `PortBoards` (notice boards, captain's log task list),
`PortLedger` (ledger tables), `CargoHold` (hold interface and inventories), `PortMaster`, `PortRewards`, `PortSites` (which dock a
loc / NPC / bag belongs to), `Cargo` (crates in hand).

Sources: OSRS Wiki, fetched with `action=parse&prop=wikitext` (UA `tickperfect-wiki-import/1.0`):
https://oldschool.runescape.wiki/w/Courier_tasks, /Bounty_tasks, /Notice_board, /Ledger_table, /Port_master, /Courier_crate,
/Cargo_hold, /Port_coin_bag, /Port_reward_bag, /Shark_paint; cache 2757 db 194/196/197/198, objs 32435+ / 34076+, locs 60245-60351 /
60287-60309, NPCs 15459-15489, varbits 19574-19591 and 19134; rev 242 client scripts 7856-9098 (disassembly, `rs2asm`). Imported data:
`courier-tasks.tsv`, `bounty-tasks.tsv` (`tools/wiki-import/sailing.py`; the `guaranteed` column is new).

## Rules

| # | Rule | Source |
|---|---|---|
| PT1 | **Which dock a thing belongs to.** The cache names boards (`PORT_TASK_BOARD_*`, locs 60287-60309, op 1 "Inspect"), ledger tables (`DOCK_LOADING_BAY_LEDGER_TABLE_<PORT>`, locs 60322-60351), port masters (`PORT_TASK_MASTER_*`, NPCs 15459-15489: Talk-to, Claim-rewards op 3, Cancel-task op 4) and reward bags (`PORT_TASK_LOOTSACK_T0..T4_<PORT>`, obj ids 34077+, 10 apart per port, 2 apart per tier) per port, but links none to a `Sailing_Dock` row; the symbol order is listed in `PortSites` by dock name and joined to db 194 by name with punctuation ignored. Boards exist at 23 docks, ledger tables at 30, bags for 30 ports. | cache symbols; Wiki Notice board, Ledger table |
| PT2 | **Slots.** 1 slot at Sailing 1, 2 at 7, 3 at 28, 4 at 56, 5 at 84, shared by courier and bounty tasks; bounty tasks need level 30 on top of their row's level. The accept level and the slot levels are not boostable (`baseLevel`). `PORT_TASK_EXTRA_SLOTS_UNLOCKED` (19589, 3 bits) = slots - 1, re-sent at login and on each Sailing level gained; client script 8707 locks the slots above it in the log. | Wiki Courier tasks, Bounty tasks; script 8707 |
| PT3 | **State.** Per account, in `saved`: `sailing.task.0..4` (bit 0 active, task id bits 1-10, crates taken bits 11-18, crates delivered bits 19-26), `sailing.task.total`, `.today` / `.day` (completed since 00:00 UTC; resets at the day change), `.last` (task id of the last crate taken). Mirrored to the cache varbits the client scripts read: `PORT_TASK_SLOT_n_ID` (12 bits, holds `Port_Task.TASK_ID`), `_CARGO_TAKEN` / `_CARGO_DELIVERED` (5 bits), `PORT_TASK_LAST_CARGO_TAKEN` (3 bits: slot + 1 of the last crate's task, 0 none), `PORT_TASKS_COMPLETED_TODAY` (10 bits), varp `PORT_TASKS_COMPLETED`. The captain's log list (script 8707 -> 8916 -> 8917) is built by the client from these alone. | cache varbits; scripts 8707, 9095, 9098 |
| PT4 | **Notice board.** Op "Inspect" opens interface 941 as the main modal and runs client script 8912 with 8 db row ids (`Port_Task` rows, -1 = empty): `Task_Board_Layout` row 0 (db 198) has 4 + 4 positions. A board shows its guaranteed bounty plus a deterministic pick of its other tasks (tasks whose `STARTING_PORT` is the board's dock, quest tasks 599 / 600 never), sorted by level. The pick is a hash of account, rotation epoch and board, so it is stable until the epoch changes; the epoch changes at 00:00 UTC and after every 8 completed tasks (OPEN-PT1). The guaranteed bounty of each board is the Wiki's `guaranteed=yes` bounty row (23, one per board). On bounty rows all three port columns are the board's dock. | Wiki Notice board, Bounty tasks; cache db 197, 198; script 8912 |
| PT5 | **Selecting and accepting.** The "Select" op of a board task arrives as a click on `Port_Task_Board.CONTAINER`; the client builds 6 components per task (scripts 8916, 8917) and the op is on the first, so task n is child 6n. The server then opens interface 942 in `Port_Task_Board.POPUP` and runs client script 8901 (popup, row), which labels the button "Accept task" or "Cancel task" from the slot varbits. A click on `Port_Task_Info.SELECT` accepts (needs the level, a free unlocked slot, the task not already taken) or, when the task is already taken, cancels it. `Port_Task_Info.CANCEL` ("Back") only clears the selection. Accepting costs no tick of its own (it runs when the packet is processed). | scripts 8901, 8903, 8916, 8917; Wiki Courier tasks |
| PT6 | **Captain's log.** The log item's "View-tasks" opens interface 935 and mounts 942 in its `TASK_WINDOW`; the five slots are rows the client builds, and clicking one arrives as a click on `Sailing_Log.TASKS_CLICK` with the slot number as child. The server remembers the slot; the panel's button then cancels that task. Charting ("View-charts") is S5. | scripts 8664, 8707, 8917, 8927 |
| PT7 | **Courier flow.** Ledger tables are multi locs on varbit `SAILING_CARRYING_CARGO` (19134): 0 gives "Take-cargo" / "Take-last-cargo" / "Take-any-cargo" (ops 1-3), 1 gives "Deposit-cargo" (op 1). The server keeps 19134 equal to "a crate is in the weapon slot" and branches on the real state. **Take**: both hands free (weapon and shield slots empty), a courier task of the player's whose `CARGO_PORT` is this dock and with crates left; Take-cargo asks which when several qualify, Take-last-cargo takes the task of the last crate, Take-any-cargo picks one at random. The crate (obj of `CARGO`; wear positions 3 and 5, category 2294) goes into the weapon slot and the task's taken count rises, all in one transaction. **Deposit**: the carried crate is handed in at a table of the task's `ENDING_PORT`; the delivered count rises; when it reaches the quantity the task completes (PT9). A crate cannot be handed in at the cargo port or at a wrong port. | Wiki Courier tasks, Ledger table, Courier crate; cache locs 60320-60322 |
| PT8 | **Leaving port with a crate.** On the first tick a carried crate is outside the port area (not on a boat, farther than 40 tiles from every dock landing: OPEN-PT3) the player gets the red message "You're leaving port with some cargo. ..." (Wiki text). 22 ticks later the crate is removed with "Your cargo has been confiscated by the S.W.A.T. team."; returning inside the area before that cancels the countdown. A confiscated crate counts as not taken, so it can be collected again (OPEN-PT3). Without a crewmate the crate must be stored in the hold before the helm can be used (the helm's Navigate option refuses while 19134 is 1). | Wiki Courier crate |
| PT9 | **Completing a courier task.** Removes the task, counts it (total, today), pays the imported base XP (`courier-tasks.tsv`, OPEN-T1) and the reward: a port coin bag 4 times in 5, otherwise the reward bag of the task's destination, the bag size set by the base XP (tiny 0-399, small 400-999, medium 1,000-2,499, large 2,500-5,999, huge 6,000+); then shark paint 1 time in 36 ("You've received some paint!" in purple). Rewards go into the backpack and at the player's feet when it is full (the 26 Aug 2026 hotfix: a full backpack no longer blocks the reward). The Horizon's lure ale multiplier is 1.0 until kegs exist (OPEN-PT7). Quest tasks give XP only (OPEN-PT10). | Wiki Courier tasks, Port coin bag, Port reward bag, Shark paint |
| PT10 | **Pandemonium delivery.** Jim's send-off gives port task 599 (cache row: not cancellable, Port Sarim -> The Pandemonium, 100 XP). Taking the crate at the Port Sarim table moves the quest to "cargo taken", handing it in at The Pandemonium's table to "delivered"; the extra 100 XP is the task's own XP. Task 600 (Prying Times) is not given: that quest is not implemented. | Wiki Courier tasks (quest table), Pandemonium; cache db 197 rows 9262, 9263 |
| PT11 | **Bounty tasks (accept / claim only).** A bounty task is accepted like any task. "Claim-rewards" at any port master completes every active bounty task whose parts (`BOUNTY_OBJECT` x `BOUNTY_OBJECT_AMOUNT`) are in the backpack: the parts are removed, the player gets Sailing XP and coins equal to the base XP, shark paint 1/36; no bag (the 2 Sep 2026 change removed loot sacks from bounty rewards, which supersedes the plan's "+ a coin bag"). Cancelling keeps collected parts. The kills are S10: `PortTasks.onBountyKill(player, npcType)` is the hook; it rolls 1/rarity for the first active bounty task of that monster whose parts are not all collected and puts the part in the backpack. | Wiki Bounty tasks, Port coin bag |
| PT12 | **Cancelling.** Allowed for tasks the cache marks cancellable (`CANCELLABLE`; the two quest tasks are not): at the board (PT5), in the captain's log (PT6) and with a port master's "Cancel-task" (a menu of the player's tasks). A courier task's crates still carried or in a hold are taken away (OPEN-PT4). | Wiki Courier tasks, Bounty tasks, Port master |
| PT13 | **Cargo hold.** The boat's hold loc is a multi loc on 19134 too: "Open" (no crate in hand) / "Deposit-held" (op 1), "Deposit-all" (op 2), "Modify" (op 5, S4). "Open" is for the owner on their own boat only. It sets varp `SAILING_BOAT_CARGOHOLD_INV` (5204) to the boat slot's inventory (963 + slot), opens interface 943 as the main modal over side panel 944 and sends the hold with exactly its capacity as size (the client reads the capacity as `inv_size`); the capacity is the `CARGOHOLD_SIZE` of the hold facility's stats row (db 164; the raft's basic hold has 20). Varp `SAILING_BOAT_CARGOHOLD_SIDE_WHITELIST` (5205) is the bit set of backpack slots the hold accepts (script 8896). The hold accepts the objs and obj categories of db 196 (crates 2294, bounty parts 2295, ...), never a noted item; each non-stackable item takes a slot, a stack takes one. Moves use the bank's quantity ops (op 1 the left-click amount of varbit 4430, 2 = 1, 3 = 5, 4 = 10, 5 = X, 6 = all); a crate comes out into the hands only (needs both hands free), one at a time. Buttons: deposit all cargo (crate in hand, crates and bounty parts), deposit all salvage (objs 32847-32873), deposit all inventory. Closing the interface gives the backpack its side slot back. Every move is one item transaction. | Wiki Cargo hold; cache db 164, 196, scripts 8869-8899 |

## Tick timeline

| Tick | Event |
|---|---|
| T | The player clicks a notice board, ledger table, hold or port master: the interaction walks up; the option runs on arrival (same tick as the engine's loc/npc op processing). |
| T | Board: interface 941 opens and script 8912 is sent. A task click, accept, cancel, take, deposit, claim, hold move: processed when the packet is, results sent at the end of the tick. |
| T of "leaving port" | Warning message; timer `sailing_cargo_watch` (every tick while a crate is carried). |
| T + 22 | Crate confiscated (PT8). |

## OPEN

- **OPEN-PT1:** which tasks a notice board offers a player and when they rotate. Known: a board lists its guaranteed bounty plus others, different per
  player, rotating after 8 completed tasks and daily at 00:00 UTC. Implemented: 7 other tasks picked by a hash of (account, day, completed / 8, board).
- **OPEN-PT2:** the interface wiring has not been seen in a real client. Derived from scripts: the board's task child index (6 per task), the info
  panel's mount points, the hold's item grid component (`ITEMS` and `ITEMS_CONTAINER` both enabled), the main-modal destination (`MAINMODAL`),
  the grouping behind the three "deposit all" buttons, the log's slot children (0-4).
- **OPEN-PT3:** the port area (assumed 40 tiles around any dock landing), blocking teleports and unequipping a crate (those belong to
  `content/magic` and `content/combat`), the crate counting as not taken after confiscation, crews carrying crates (S9), and the use of
  `PORT_TASK_LAST_CARGO_TAKEN` (3 bits, no client script reads it; slot + 1 stored). The helm refuses while carrying; the crew exception is S9.
- **OPEN-PT4:** what cancelling does to crates in hand or in a hold (taken away here so none is stuck).
- **OPEN-PT5:** the shipyard (S4) does not exist, so every owned boat gets the cheapest cargo hold its hotspots allow on the first hotspot that
  allows one, with the hotspot code `a` as loc angle (OPEN-S1). The hold facility choice and its tier are S4's.
- **OPEN-PT6:** opening port reward bags ("Open"): the contents tables (a shared table per size plus a signature drop per port, Wiki Port reward
  bag) are not imported. Coin bags open (PT14). The pre-hotfix "claim a held-back courier reward at the port master" is not needed.
- **OPEN-PT7:** Horizon's lure (+2.5% port task XP) needs the boat's kegs (S4 / S11).
- **OPEN-PT8:** where a bounty part lands when it drops (backpack here) and the claim only counting the backpack; the drop-rate boosts of cannon
  barrels and similar items; S10.
- **OPEN-PT9:** the port masters' "Talk-to" conversation is not transcribed.
- **OPEN-PT10:** quest deliveries (599, 600) give no bag or paint here (the Wiki lists none for them).
- **OPEN-PT11:** captain's log, spyglass and the other tools take no hold space in the real game; here the spyglass from Jim takes a slot. The
  tools panel (`TOOLS`) is S5.
- **OPEN-PT12:** the coin bag amount is uniform in +/- 20% of the cache average; only the ranges are in the Wiki.
- Unsourced game message texts: all messages of this milestone except the Wiki's two crate messages and the paint message.

## PT14 (coin bags)

Port coin bags (`PORT_TASK_LOOTSACK_T0..T4_COINS`) have the average coins in obj param 2647 (1,000 / 2,000 / 3,000 / 4,500 / 8,000); "Open"
turns one into that +/- 20% (the Wiki's 800-1,200, 1,600-2,400, 2,400-3,600, 3,600-5,400, 6,400-9,600), one transaction.
