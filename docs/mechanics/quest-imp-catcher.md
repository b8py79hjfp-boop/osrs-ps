# Quest: Imp Catcher

Code: `content/quests` `ImpCatcher`, `QuestSpawns`. Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Imp_Catcher,
/w/Transcript:Imp_Catcher, /w/Transcript:Wizard_Mizgog, /w/Imp (drops); cache `Quest` row 76 (id 9): varp 160 (`impcatcher`), end state 2,
1 quest point, (6, 8750 tenths) Magic XP.

| # | Rule |
|---|---|
| IC1 | Start: Wizard Mizgog (multi-NPC 5005 on varp 160: 0/1 -> npc 7746, 2 -> npc 7747 with op 3 "Purchase Amulet"; spawned at 3104,3164 level 2). Varp 160: 0 not started, 1 started, 2 finished (cache). Wizard Grayzag (5006, 3110,3161 level 2) only taunts. |
| IC2 | The beads (black 1474, yellow 1472, red 1470, white 1476) come from imp drops: content/drops has them as 5/128 rows for the imp npcs 3134 and 5007 (Wiki "Imp"). Mizgog takes all four at once ("He won't accept them one at a time"). A player carrying all four when accepting the quest hands them over at once (transcript). |
| IC3 | Reward: 1 quest point, 875 Magic XP (cache), an amulet of accuracy. After the quest, one bead of each colour buys another amulet (talk option or op 3 "Purchase Amulet"). |

OPEN: the "Purchase Amulet" op has no transcript of its own, so it reuses the lines of the talk option and the "Without all four beads"
section of the Wiki transcript; the imp variants 14207 and 14233 (level 2) have no drop table in content/drops (npc-drops.tsv lists only
3134 and 5007; report to the drops owner); journal wording (OPEN-QU6).
