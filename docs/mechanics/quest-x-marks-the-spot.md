# Quest: X Marks the Spot

Code: `content/quests` `XMarksTheSpot`, `QuestDigging` (`QbHelpers.kt`), spawns in `QuestSpawnsB`. Sources: OSRS Wiki /w/X_Marks_the_Spot, /w/Transcript:X_Marks_the_Spot,
/w/Map:X_Marks_the_Spot, /w/Mysterious_orb_(X_Marks_the_Spot), /w/Ancient_casket; cache `Quest` row 162 (id 154): varbit 8063, end state 8, 1 quest point;
obj 23067-23072 (`cluequest_clue1..4`, casket, lamp), varbit 8064 (`cluequest_prev_distance`), npc configs 8632 / 1063 (Veos multi-NPCs).
Rewards: 200 coins, antique lamp (300 experience in a skill, obj 23072), 1 quest point.

| # | Rule |
|---|---|
| XM1 | Start: Veos (Lumbridge, The Sheared Ram 3228,3242, parent npc 8632; Port Sarim dock 3054,3245, parent npc 1063). With a free slot he gives the treasure scroll (obj 23067, stage 1). Varbit 8063: 0, 1 first scroll, 2 map, 3 orb, 4 cipher, 5 casket dug up, 8 finished (OPEN-QU1: 0 and 8 are cache values). A lost clue is replaced by Veos; at every stage he gives the Wiki hint. |
| XM2 | Digs (spade "Dig", `DigEvent`): step 1 at 3230,3210 gives the map (obj 23068); step 2 at 3203,3212 the mysterious orb (obj 23069); step 3 at 3108,3264 the cipher (obj 23070, "ESBZOPS QJH QFO"); step 4 at 3077,3260 the ancient casket (obj 23071) with the Wiki's chat lines. Coordinates are the Wiki map pins; each dig needs the matching clue in the backpack. |
| XM3 | Giving the casket to Veos (Port Sarim) finishes the quest and gives the rewards. |

## OPEN

- **OPEN-QUB13:** the map clue is an interface picture (the item only says that it is a map); the orb's words and distance bands are ours (Wiki: temperature by Chebyshev distance, no table); the message for a successful intermediate dig is ours; the Great Kourend "visited before" dialogue variant is not selected (first-visit text always); the hot/cold varbit 8064 is not written. Veos' location varbits (12151 pub, 12148 dock) are set by the server at login and on progress so that the pub Veos shows until the quest ends and the dock Veos from the first scroll on (the values are not published; the cache arrays fix which variant a value shows); the post-quest travel options of Veos (Port Piscarilius) are not implemented.
