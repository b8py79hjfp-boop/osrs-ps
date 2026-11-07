# Quest: The Restless Ghost

Code: `content/quests` `RestlessGhost`. Sources: OSRS Wiki /w/The_Restless_Ghost, /w/Transcript:The_Restless_Ghost,
/w/Transcript:Father_Aereck, /w/Transcript:Father_Urhney; cache `Quest` row 120 (id 3): varp 107 (`priestStart`), end state 5,
1 quest point, (5, 11250 tenths) Prayer XP; varp 728 bits: varbits 2128 skeleton, 2129 coffin, 2130 altar; locs 2145 coffin
(op "Open"), 15061 open coffin (multi-loc varbit 2129 -> 15052 / 15053, ops "Search" / "Close"), 2146 altar (multi-loc varbit
2130 -> 15050 / 15051, op "Search").

| # | Rule |
|---|---|
| RG1 | Start: Father Aereck (2812, Lumbridge church). Varp 107: 0, 1 started, 2 amulet from Urhney, 3 the ghost told the player of its skull, 4 skull taken, 5 finished (OPEN-QU1: 0 and 5 are cache values). |
| RG2 | Father Urhney (923, swamp hut): "Father Aereck sent me" gives the amulet of ghostspeak (obj 552); a lost one is given again if the backpack has room and it is neither carried nor worn. |
| RG3 | The coffin (loc 2145, graveyard 3249,3192) opens to loc 15061 ("You open the coffin."); "Close" puts 2145 back. Searching it spawns the restless ghost (922, 3250,3193) if absent; "There's a skeleton without a skull in here." (varbit 2129 = 0) or "...a nice and complete skeleton..." (after the quest). |
| RG4 | The ghost (922): without the worn amulet (neck slot 2) it only says "Wooo"; with it the first talk explains the skull and sets stage 3 (OPEN-QU12), later talks ask about the skull. |
| RG5 | The altar in the Wizards' Tower basement (2146, 3120,9566): searching it from stage 3 gives the ghost's skull (obj 553), sets varbit 2130 and spawns a skeleton (924, level 13) that hunts the player (OPEN-QU11); already holding the skull or after the quest gives the transcript's messages. |
| RG6 | Using the skull on the open coffin at stage 4 takes it, sets varbit 2129 = 1, the ghost says "Release! Thank you stranger..", is removed, and the quest ends. On the closed coffin: "Maybe I should open it first."; on the ghost: "I can't give it to him. It goes right through him." |
| RG7 | Reward: 1 quest point, 1,125 Prayer XP (cache), keep the amulet. |

OPEN: OPEN-QU10..OPEN-QU13. Dropping the skull ("Dropping this skull here will destroy it!") and the Item Retrieval Service check
for lost amulets are not implemented.
