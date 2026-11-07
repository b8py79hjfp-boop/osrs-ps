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

OPEN-QUA1: the basement lever puzzle that guards the oil can is NOT implemented. The cache has the doors as locs 137-145 and the levers as
locs 146-151 near 3096-3118, 9745-9767; the Wiki table of lever positions per door has uncertain cells and the mapping of those locs to
the Wiki's door numbers 1-9 and lever letters A-F is only in an image, so the oil can cannot be reached (the quest is finishable only with
`::quest 44 3`). Bookcase (locs 155/156) and lever (160/161) secret room are likewise not implemented. OPEN-QUA5: the 3 November 2026
antique lamp (Veronica) is not given. OPEN-QUA6: the closet door has no open model in `doors.tsv`, so the player is moved across the wall.
Other items used on the fountain ("Something in the water bites you.") are not intercepted (no use-any-item-on-loc hook).
