# Sailing: the Pandemonium quest

Implementation: `content/sailing` `Pandemonium`, `PandemoniumDialogue`, `PandemoniumJim` (milestone S1). Research: docs/plans/sailing.md 1.2.

Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Pandemonium and /w/Transcript:Pandemonium (all dialogue is transcribed from it);
cache 2757 db `Quest` row 206 (quest 174), varp `SAILING_INTRO_PRIMARY` 4959, varbits 18314-18316, NPCs 14957-14977, locs 59620-59622, 60480-60482.

## Rules

| # | Rule | Source |
|---|---|---|
| Q1 | Start: Talk-to Will or Anne at Port Sarim (NPCs 14957 / 14962). No requirements. The interview ends with "ready to hop aboard" (needs one free backpack slot), then the player is taken to their boat. | Wiki Pandemonium, Transcript |
| Q2 | Progress is varp 4959: 0 not started, 1 started, 2 offered, 3 aboard, 4 briefed, 5 at the wreck, 6 washed up, 7 told by Steve, 8 told by Ribs, 9 sent to Jim, 10 hold built, 11 errand (log given), 12 cargo taken, 13 cargo delivered, 14 Jim reported, 50 complete (quest row end state). The in-between values are ours (OPEN-PAN6). | cache db Quest 206 |
| Q3 | The intro boat is boat type 3 (skiff-sized, template zone (3840,6400), config 10, OPEN-W1) spawned at the Port Sarim mooring with Will (14958) and Anne (14963) on deck. The player steers it with the helm (loc 59620 family). Within 5 tiles of the wreck (3031,3035) the leg ends: varbit 18315 = 1, the boat's sails go down, Will and Anne speak. | Wiki Pandemonium |
| Q4 | The boat's static facility (salvaging hook, loc 60480) lowered at the wreck: the player gets the old cup (the medallion goes to Will), Anne pushes them overboard, they appear at The Pandemonium dock and the boat and NPCs are removed. Lowering it elsewhere: Will says to go to the wreck. | Transcript |
| Q5 | Steve (14967-14969), Ribs (14970-14971), Junior Jim (14972-14975): conversations by stage as in the transcript. Jim takes the cup and gives the raft (boat slot 0, wooden parts, moored at The Pandemonium), then the captain's log. | Transcript |
| Q6 | The cargo run (take a crate at the Port Sarim ledger table, deposit it at The Pandemonium) is S3: `Pandemonium.cargoTaken` / `cargoDelivered` move the stage and give the extra 100 Sailing XP. Jim then gives 2 repair kits and a spyglass (Wiki: put in the cargo hold, backpack until S3, OPEN-PAN3); varbit 18316 = 1. | Wiki Pandemonium |
| Q7 | Steve's finale (Old Grog is spawned next to the player and removed) completes the quest: 300 Sailing XP, 25 sawmill coupons (wood plank). | Wiki rewards |
| Q8 | `::pandemonium <stage>` (administrators) sets the stage to test later steps. | - |

## OPEN

- **OPEN-PAN1:** the shipyard trip and building the cargo hold with a hammer and saw (S4): Jim fits the hold in the conversation. The hammer / saw crates and the schematics board are not implemented.
- **OPEN-PAN2:** camera work, Anne's push animation, the boat cutscene positions, the exact wreck radius (Wiki gives a 3-tile square) and the fixed Port Sarim start dock (3025,3209, not the sea mooring used for the boat).
- **OPEN-PAN3:** repair kits and spyglass go to the backpack until the cargo hold exists.
- **OPEN-PAN4:** captain's log obj: 31986 (`SAILING_LOG`) used; 31985 `SAILING_LOG_INITIAL` may be the quest copy. Asking Steve for a lost cup, the Wiki's lost-cup branches, and the shop (Steve "Trade") are not implemented.
- **OPEN-PAN5:** varbit 18314 `SAILING_INTRO` and the intro HUD interface 345 are not driven.
- **OPEN-PAN6:** the real stage values are server-only.
- **OPEN-PAN7:** quest point and the reward scroll interface (no quest point store exists yet); the port masters' dialogue (S3).
