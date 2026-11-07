# Quest: Below Ice Mountain

Code: `content/quests` `BelowIceMountain` (Charlie the Tramp's Marley question is in `ShieldOfArrav.charlieMarley`, Charlie being one NPC), spawns in `QuestSpawnsB`.
Sources: OSRS Wiki /w/Below_Ice_Mountain, /w/Transcript:Below_Ice_Mountain, /w/Steak_sandwich; cache `Quest` row 6 (id 162): varbit 12063, end state 120, 1 quest point;
npc 10654-10660 (`bim_*`), locs 41357-41460 (`bim_*`). Rewards: 2,000 coins, 1,200 Mining XP, the Flex emote, steak sandwich, the Ruins of Camdozaal.

| # | Rule |
|---|---|
| BI1 | Start: Willow (10655, south of Ice Mountain 3002,3436) asks the player to bring back her crew; varbit 12063: 0, 10 started, 30 the guardian awake, 40 the guardian dead, 120 finished (OPEN-QU1: 0 and 120 are cache values). Recruited members are account flags `quests.bim.checkal`, `.burntof`, `.marley`. |
| BI2 | Checkal (10657, Barbarian Village 3087,3415) sends the player to Atlas (10658, Long Hall 3075,3439) whose training (dialogue, the cutscene is not staged) unlocks the flex show; Checkal then joins. Burntof (10659, Rising Sun Inn 2957,3367) wants an alcoholic drink (beer, Asgarnian ale, Dwarven stout or Wizard's mind bomb) and plays a best-of-three rock paper scissors game that the player always wins. Marley (10656, Edgeville 3088,3472) is found through Charlie the Tramp (cousin) and wants a steak sandwich: the Blue Moon Inn cook (2895, 3230,3400) tells the recipe, a knife on bread with cooked meat in the backpack makes it (obj 25631). |
| BI3 | With the crew together, Willow's cutscene dialogue plays and the Ancient Guardian (10654, level 25) appears beside the player (stage 30); killing it (or by mining pillars, not implemented) shows the "Finishing up" lines, gives the bag's coins and finishes the quest; Ramarno (10684 / 10685) is then spoken to when the reward scroll is closed. |

## OPEN

- **OPEN-QUB14:** Atlas' workouts are dialogue only; the unblocking of the entrance, Marley's trap scene and Burntof's game are staged with `Scene` (BI5: the crew are scene NPCs round the player, the blast shakes the camera, the fades are camera cuts; the ruins' interior is not on the map; camera OPEN-QUC5); the Flex emote is not unlocked and Checkal does not wait for the emote; the ruins' entrance and the guardian room (locs 41357-41460, instanced) are not placed, so the guardian appears next to the player and is fought by the NPC hunt; the structural pillars method is not implemented; the 1,000-coin bag and the 2,000-coin reward are both given as the Wiki lists; the post-quest desert dialogue of the crew and Ramarno's workshop are not implemented; making the sandwich has our own message; Atlas' repeat sessions are dialogue only.
