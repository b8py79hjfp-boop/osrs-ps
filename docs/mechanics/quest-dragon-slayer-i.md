# Quest: Dragon Slayer I

Code: `content/quests` `DragonSlayer` (also Ned's single talk handler, shared with Prince Ali Rescue), `PortSarimFerry` (the sailors' Crandor refusal), spawns in `QuestSpawnsB`.
Sources: OSRS Wiki /w/Dragon_Slayer_I, /w/Transcript:Dragon_Slayer_I, /w/Transcript:Ned, /w/Elvarg, /w/Crandor, item pages (Map part, Elvarg's head, keys),
Melzar's Maze monster pages; cache `Quest` row 31 (id 17): varp 176, end state 10, 2 quest points. Rewards: 18,650 Strength and 18,650 Defence XP,
rune platebody / green d'hide body / dragon platebody wear rights (equipment requirement is in the equipment module).

| # | Rule |
|---|---|
| DS1 | Guildmaster (814, Champions' Guild 3191,3360) starts the quest; Oziach (822, Edgeville) sets the task; the Guildmaster then explains Crandor and answers four questions (map, ship, shield, "what is special") - the map question lists the three pieces and gives the maze key (obj 1542). Varp 176: 0, 1 started, 2 Oziach, 3 told, 4 ship bought, 5 hole patched, 6 Ned has the map, 7 on Crandor, 8 Elvarg dead, 10 finished (OPEN-QU1). Asked questions and taken pieces are `quests.ds1.*` flags. The 32 quest point entry rule of the guild is not enforced. |
| DS2 | Anti-dragon shield (obj 1540): Duke Horacio (815, Lumbridge Castle first floor) gives it for "Elvarg" after the confirmation questions (more can be had by asking again). |
| DS3 | Melzar's piece (obj 1535): Melzar's Maze (surface 2929,3248 area; basement 2929-2945,9640-9660). Coloured doors (locs `melzardoor` 2595, `reddoor` 2596 ... `greendoor` 2601) open with the key of their colour, which disintegrates ("The key disintegrates as it unlocks the door."). Keys drop (private ground item) from the maze monsters: zombie rats 3969-3971 red, ghosts 3975-3979 orange, skeletons 3972-3974 yellow, zombies 3980-3981 blue, Melzar the Mad (823) magenta, the lesser demon (3982) green. The chest (loc 2603 at 2935,9657) gives the piece once. |
| DS4 | Thalzar's piece (obj 1537): the Oracle (821, Ice Mountain 3011,3501) answers the riddle (only after the Guildmaster's answer); the four items silk, lobster pot, unfired bowl and Wizard's mind bomb are used on the door (loc 25115 at 3050,9839) one by one, the door opens, the chest (loc 2587 at 3057,9841) shows the inscription and gives the piece. |
| DS5 | Lozar's piece (obj 1536): General Bentnoze (675 / 669, Goblin Village 2959,3513) names Wormbrain (820, Port Sarim jail), who sells it for 10,000 coins (dialogue; the Wiki's telegrab route is not implemented). Any two pieces used on each other: "You still need one more piece of map."; all three give the Crandor map (obj 1538). |
| DS6 | Klarense (819, Port Sarim 3047,3204) sells the Lady Lumbridge for 2,000 coins (stage 4). The hole (loc 25036) takes 3 planks, 30 steel nails per plank (obj 1539 or 32923) and a hammer (stage 5 after the third). |
| DS7 | Captains: Ahab, Jack Seagull, Longbow Ben (Rusty Anchor), the Port Sarim sailors refuse; Ned (4280, Draynor 3099,3259) agrees (dialogue "Ah, you only live once! I'll do it!"), takes the map once the ship is repaired (stage 6), and after "I'll meet you on board" the boarding talk and the sailing cutscene play as dialogue and the player is moved to Crandor (2836,3266; stage 7). |
| DS8 | Elvarg (817, cave 2852,9637) dying for a player on stage 7 gives Elvarg's head (obj 11279, stage 8) and "You cut off Elvarg's head as proof of your victory!". Oziach takes the claim (the head is not taken) and finishes the quest. NPC combat, dragonfire and the anti-dragon shield are the combat module's. |

## OPEN

- **OPEN-QUB10:** which variant of a Melzar's Maze monster group carries the key is not in the cache names (the Wiki says "the skeleton with the round shield" etc.); every variant drops it (`KEY_FROM_ANY_VARIANT`). The maze's other flavour (wardrobes, bookshelves with Melzar's notes, Melzar's overhead lines) is not implemented.
- **OPEN-QUB11:** the sawmill operator's plank dialogue (planks for 100 coins and logs, nine at most), the Trader Stan / Captain Barnaby charter refusals, Wormbrain's telegrab route (attack options, "Ow!") and the Crandor shortcut are not implemented. The Champions' Guild entry check and Jenkins' ship dialogue are skipped.
- **OPEN-QUB12:** no ship scene: boarding, the hull patch picture, the sailing cutscene (camera, Elvarg overhead, Jenkins' death) and the crash are dialogue only; the arrival tile and the volcano entrance are unchecked. Hook proposal: a plugin-api cutscene/boat-travel step (fade, camera, scripted NPCs).
- **OPEN-QUB1:** the maze doors, Thalzar's door are passed by moving the player through them. Ned's menu offers either the wig (Prince Ali Rescue) or the Crandor questions, not both at once.
