# Quest: Vampyre Slayer

Code: `content/quests` `VampyreSlayer`, `QuestSpawns`. Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Vampyre_Slayer,
/w/Transcript:Vampyre_Slayer, /w/Transcript:Morgan, /w/Transcript:Dr_Harlow, /w/Count_Draynor; cache `Quest` row 155 (id 8): varp 178, end
state 3, 3 quest points, (0, 48250 tenths) Attack XP.

| # | Rule |
|---|---|
| VS1 | Start: Morgan (multi-NPC 3479 on varbit 15611: 0 = npc 16274, 1 = 16275; spawned at 3099,3269). The plugin sets varbit 15611 to 1 on completion. Varp 178: 0, 1 started, 2 Dr Harlow gave the stake (ours, OPEN-QU1), 3 finished (cache). |
| VS2 | Dr Harlow (npc 3480, 3222,3397, Blue Moon Inn): asks for a beer (obj 1917), then gives a stake (1549) and advice on the hammer and garlic; a lost stake is given again. |
| VS3 | Garlic (1550) is found by searching Morgan's cupboard upstairs (loc 2613, the open one of 2612, 3096,3269 level 1). |
| VS4 | Opening the coffin (loc 46237, 3077,9775) spawns Count Draynor (npc 3481) beside it, hunting within 5 tiles; he leaves after 500 ticks (the Wiki: "returns to his coffin and heals"; no time given). |
| VS5 | When Count Draynor dies to a player at stage 2 with the stake and a hammer in the backpack the quest completes ("You hammer the stake into the vampyre's chest!"): 3 quest points, 4,825 Attack XP (cache). Without a hammer: "You're unable to push the stake far enough in!" and no completion. |

OPEN-QUA16: without a stake and hammer the Count heals to full at 0 hitpoints (plugin-api `LethalHitEvent`, Wiki "Count Draynor"); no message for it is sourced, so none is shown. OPEN-QUA17: the Count's 500 tick lifetime and hunt range are ours. Garlic in the backpack (on each hit that lands): at full health 10 damage; full Attack and Strength -10, full Defence -40; "The vampyre seems to weaken." with the Attack drain (Wiki "Count Draynor", Mod Ash). OPEN-QUA18: garlic's +1 damage on every hit, the 30-second wear-off and the Count's 20x health regeneration are not modelled.