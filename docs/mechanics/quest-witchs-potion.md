# Quest: Witch's Potion

Code: `content/quests` `WitchsPotion`, `QuestSpawns`. Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Witch%27s_Potion,
/w/Transcript:Witch%27s_Potion, /w/Rat (drops); cache `Quest` row 161 (id 13): varp 67, end state 3, 1 quest point, (6, 3250 tenths) Magic XP.

| # | Rule |
|---|---|
| WP1 | Start: Hetty (npc 4619, 2968,3206, Rimmington). Varp 67: 0 not started, 1 started, 2 ingredients handed in and the potion waits (ours, OPEN-QU1), 3 finished (cache). |
| WP2 | Ingredients: eye of newt, rat's tail, onion, burnt meat, handed in together ("In fact I have everything!"); with only some the player lists what is missing in the Wiki's phrasing. |
| WP3 | The rat's tail drops from rats (levels 1: npc 2854, 2855, 13624 and the Stronghold of Security rats 2492, 2513) only while the quest is in progress (Wiki "Rat": "Rat's tails is only dropped during Witch's Potion", always, 1). It is created on the ground at the rat's tile, private to the killer. |
| WP4 | Drinking from the cauldron (loc 2024, op 1 "Drink-from") with the potion ready gives the reward: 1 quest point, 325 Magic XP (cache). Otherwise "As nice as that looks, I think I'll give it a miss for now." |

OPEN-QUA3: the Wiki has no transcript for Hetty after the quest; she only repeats the witch lines. OPEN-QUA4: content/drops attaches the Wiki
row "Rat's tail, always" to npc 2866, 2867, 3607-3609 (dungeon rats) for all players; the importer matched the wrong infobox version, and
the regular rats have no row, hence the quest plugin's own drop.
