# Quest: The Corsair Curse

Code: `content/quests` `CorsairCurse`, spawns in `QuestSpawnsB`. Sources: OSRS Wiki /w/The_Corsair_Curse, /w/Transcript:The_Corsair_Curse; cache `Quest` row 18 (id 147):
varbit 6071, end state 60, 2 quest points; npc 7956-7978 (`corscurs_*`, `corsair_captain_*`), locs 31632-31724 (`corscurs_*`), obj 21837. Multi-NPCs on varbit 6071 fix:
Tock (7959) leaves Port Sarim at 6, Ithoi (7962) is gone from 52, the post-quest crew shows from 60.

| # | Rule |
|---|---|
| CC1 | Start: Captain Tock (7959, north of Port Sarim 3030,3273) asks for help and goes to his ship (7958) west of Rimmington (2910,3226), which sails to Corsair Cove (ship pin 2574,2835). Varbit 6071: 0, 6 started, 10 Ithoi talked to, 20 the three curses ruled out, 30 traitor hunt, 40 Ithoi confessed, 50 lie proven, 52 Ithoi dead, 60 finished. |
| CC2 | Ithoi (7962, 2531,2833 upstairs), Arsen (7977, 2556,2856), Colin (7968, same hut) and Gnocci (7973, 2545,2862) each blame a different cause. Arsen: Tock gives the ogre relic (obj 21837), Chief Tess (7988, in the table) laughs it off (her tooth-pick), Arsen is told. Colin: the telescope (loc 31632 "Observe", 2528,2835 level 1) shows an ogre called Bugs. Gnocci: dig by the sand west of Ithoi's hut for the toy doll. When all three are ruled out the traitor hunt opens. |
| CC3 | Traitor hunt: the crew's remarks, Ithoi's confession, the tinderbox on the driftwood (loc 31721) makes the sick Ithoi run out; Tock asks the player to kill him; Ithoi (turned into the combat variant 7964, level 35) is killed (stage 52); Tock thanks the player and the quest finishes (2 quest points; access to Yusuf's bank at the cove). |

## OPEN

- **OPEN-QUB15:** boat trips are plain moves, the flashback/telescope cutscenes are dialogue only, the fight is not instanced (Ithoi turns into the combat NPC where he lies and hunts within 8 tiles); the doll's dig tile (Wiki map, west of the fishing spot) is within 3 tiles of 2524,2833 and unverified; the driftwood loc state (burning) is not shown; Francois and the Cove's bank/mermaid post-quest content are not implemented; Colin's and Arsen's "again" menus follow one pass of the transcript.
