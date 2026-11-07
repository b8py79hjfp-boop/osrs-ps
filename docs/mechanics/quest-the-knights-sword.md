# Quest: The Knight's Sword

Code: `content/quests` `KnightsSword`, `QuestSpawns`. Sources: OSRS Wiki https://oldschool.runescape.wiki/w/The_Knight%27s_Sword,
/w/Transcript:The_Knight%27s_Sword, /w/Transcript:Reldo, /w/Transcript:Thurgo, /w/Transcript:Squire, /w/Transcript:Sir_Vyvin; cache `Quest`
row 83 (id 14): varp 122, end state 7, 1 quest point, (13, 127250 tenths) Smithing XP.

| # | Rule |
|---|---|
| KS1 | Start: the squire (npc 4737, 2977,3342). Varp 122: 0, 1 started, 2 Reldo told the player about the Imcando, 3 Thurgo ate the pie, 4 portrait given, 5 sword made (stages ours, OPEN-QU1), 7 finished (cache). |
| KS2 | Reldo (4243, 3210,3495) answers "What do you know about the Imcando dwarves?" during the quest (stage 2). Thurgo (4733, 3001,3144) eats a redberry pie (2325) once Reldo told the player; he asks for a picture of the sword. |
| KS3 | The portrait (obj 666) is in Sir Vyvin's cupboard (closed loc 2271 "Open", open 2272 "Search"/"Shut", 2984,3336 level 2) once the squire mentioned it. Thurgo takes it and asks for two iron bars and a blurite ore (668); he makes the blurite sword (667) and the squire takes it for the reward. |
| KS4 | After the quest Thurgo makes another sword for a blurite ore and two iron bars ("Can you make me another of Sir Vyvin's swords?"). |
| KS5 | Reward: 1 quest point, 12,725 Smithing XP (cache). |

OPEN-QUA9: Sir Vyvin catching the player in his room is not modelled. OPEN-QUA10: blurite rocks of the Asgarnian Ice Dungeon are not
minable (mining.md OPEN-MN6); blurite ore needs a rock entry in content/skills `MiningData` (level 10, Wiki "Blurite rocks") before the
quest is completable by players (administrators: `::quest 83 7`). OPEN-QUA11: Reldo's offer of Shield of Arrav gives no quest (the book
does not exist). OPEN-QUA12: the Wiki's "Before starting this quest, be aware..." box is shown when Mining is below 10 or combat below 20.
OPEN-QUA13: Sir Vyvin's White Knights armoury (ranks) answers "No, I'm sorry." only. Thurgo's skillcape, crowbar, Camdozaal and Wise Old Man
branches are not transcribed.
