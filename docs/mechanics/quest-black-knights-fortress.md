# Quest: Black Knights' Fortress

Code: `content/quests` `BlackKnightsFortress`, `QuestSpawns`, `stepAcross` (QuestSupport). Sources: OSRS Wiki
https://oldschool.runescape.wiki/w/Black_Knights%27_Fortress, /w/Transcript:Black_Knights%27_Fortress, /w/Transcript:Sir_Amik_Varze;
cache `Quest` row 10 (id 12): varp 130, end state 4, 3 quest points, no cache XP.

| # | Rule |
|---|---|
| BK1 | Start: Sir Amik Varze (npc 4771, 2960,3337 level 2) with 12 quest points. Accepting gives the dossier (obj 30803, "Open" counts down and destroys it). Varp 130: 0, 1 started, 2 overheard the witch, 3 potion ruined (ours, OPEN-QU1), 4 finished (cache). |
| BK2 | The fortress's south door (sturdy door loc 2337, 3016,3514) opens for a player wearing an iron chainbody and a bronze med helm, or a black full helm + black platebody + black platelegs/plateskirt; otherwise a Fortress Guard (npc 4772) turns the player away. The fake walls (loc 2341, op "Push") and the meeting door (loc 2338, with the guard's warning) are passed by moving the player across. |
| BK3 | Grill (loc 2342, 3026,3507, "Listen-at") plays the witch's conversation (sets stage 2); after the potion is ruined it plays the angry version. |
| BK4 | A normal cabbage (obj 1965) used on the hole (loc 2336, 3031,3507 level 1) after overhearing ruins the potion; a Draynor Manor cabbage (1967) is "the wrong sort"; before overhearing: "Why exactly would I want to do that?". |
| BK5 | Reporting back: Sir Amik pays 2,500 coins; 3 quest points. |

OPEN-QUA6: no open model for the sturdy doors and fake walls (player is teleported across); the Black Knights, the witch, Greldo and the
cutscene are not staged; use-any-item on the hole is not intercepted. OPEN-QUA5: the 3 November 2026 antique lamps are not given.
OPEN-QUA14: the 12 quest point refusal line is ours (the Wiki gives the requirement, not the words). OPEN-QUA15: Sir Amik's post-quest
dialogue is not published.
