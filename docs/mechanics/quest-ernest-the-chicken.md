# Quest: Ernest the Chicken

Code: `content/quests` `ErnestTheChicken`, `QuestSpawns`, `stepAcross` (QuestSupport). Sources: OSRS Wiki
https://oldschool.runescape.wiki/w/Ernest_the_Chicken, /w/Transcript:Ernest_the_Chicken, /w/Transcript:Veronica,
/w/Transcript:Professor_Oddenstein, /w/Draynor_Manor (basement table), /w/Key_(Ernest_the_Chicken); cache `Quest` row 44 (id 7): varp 32,
end state 3, 4 quest points, no cache XP.

| # | Rule |
|---|---|
| EC1 | Start: Veronica (npc 3561, 3110,3330). Professor Oddenstein (3562, 3111,3367 level 2) and the chicken Ernest (10556, 3109,3366 level 2) are spawned. Varp 32: 0 not started, 1 started, 2 the professor explained Ernest is a chicken (ours, OPEN-QU1), 3 finished (cache). |
| EC2 | Fish food (obj 272) + poison (273) make poisoned fish food (274). Poisoned fish food on the fountain (loc 153, 3087,3334) kills the piranhas and sets an account flag; searching the fountain then gives the pressure gauge (271) once. Searching before: "Something in the water bites you." and "Ow!". |
| EC3 | A spade on the compost heap (loc 152, 3084,3360) during the quest gives the key (275). The key opens the closet door (loc 131, 3107,3367) to the rubber tube's room (the tube is a static item spawn, obj 276 at 3111,3367). |
| EC4 | The professor takes the rubber tube, pressure gauge and oil can together. Reward: 4 quest points, 300 coins (Wiki). Veronica then answers with the post-quest lines. |
| EC5 | Item spawns (item-spawns.tsv): poison 3097,3366; fish food 3109,3357 level 1; rubber tube 3111,3367; oil can 3092,9755 (basement). |
| EC6 | Secret room: the bookcase in the manor's west room (locs 155 / 156, "Search") puts the player in the secret room at 3096,3358; the lever there (loc 160, "Pull") lets the player out ("The lever opens the secret door!"); the ladder (133) goes down to the basement (3116,9754) and the basement ladder (132) back up. Going down resets the six levers ("The levers can be reset by leaving the room and returning."). |
| EC7 | Levers A-F are locs 146-151 ("Pull"); each toggles its varbit (`ernestlever_a..f`, 1788-1793): 0 up, 1 down. After every pull the server writes the door varbits (`ernestdoor_*`) from the table below, which the cache uses to show an open door ("Open") or a plain wall. |
| EC8 | Doors (Wiki "Draynor Manor" basement table, D down / U up / . no effect; letters A-F). Wiki door 1 = cache `4to7` (loc 144): DDU.UU; 2 = `4to5` (139): D.U.UU; 3 = `5to8` (145): ...DU.; 4 = `5to6` (140): ...DU.; 5 = `3to6` (143): .U.D.U; 6 = `2to3` (138): .U.D.D; 7 = `1to2` (137): UU..DD; 8 = `2to5` (142): ...DUD; 9 = `8to9` (141): ..DD.D (the oil can room). The cache labels its 3x3 room grid 1-9; the match to the Wiki numbers follows the Wiki's map description (levers A and B in the entrance room, C and D in the room north of door 1, E and F in the north-west room; checked against the loc tiles in `cache-dump map 48 152`). A door that is open moves the player to the tile across it ("Open" on the loc). |
| EC9 | The Wiki solution (B, A down; door 1; D down; doors 2, 3; B, A up; doors 3, 4, 5; E, F down; doors 6, 7; C down; doors 7, 6; E up; doors 6, 8, 3; door 9) satisfies the table above step by step; the oil can (obj 277) is the item spawn at 3092,9755 (EC5). |

OPEN-QUC1: the Wiki marks three cells "?" (door 1 lever E, door 7 lever B, door 8 lever D); they are taken as printed (U, U, D). OPEN-QUC2: the doors, the bookcase and the ladders have no open model in `doors.tsv`; the player is moved across (OPEN-QUA6).
OPEN-QUA5: the 3 November 2026
antique lamp (Veronica) is not given. OPEN-QUA6: the closet door has no open model in `doors.tsv`, so the player is moved across the wall.
Other items used on the fountain ("Something in the water bites you.") are not intercepted (no use-any-item-on-loc hook).
