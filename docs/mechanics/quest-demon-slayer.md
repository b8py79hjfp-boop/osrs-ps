# Quest: Demon Slayer

Code: `content/quests` `DemonSlayer`, `QuestSpawns`. Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Demon_Slayer,
/w/Transcript:Demon_Slayer, /w/Transcript:Sir_Prysin, /w/Transcript:Captain_Rovin, /w/Delrith, /w/Silverlight_key_(*); cache `Quest` row 25
(id 2): varbit 2561, end state 3, 3 quest points, no cache XP.

| # | Rule |
|---|---|
| DS1 | Start: Aris (npc 11868, 3204,3425; a coin is needed, none is taken). Varbit 2561: 0, 1 started, 2 Silverlight given (ours, OPEN-QU1), 3 finished (cache). The incantation words Aber, Gabindo, Purchai, Camerinthum, Carlem come in a random order per player, kept in account value `quests.ds.words` (told by Aris and in Wally's scene). |
| DS2 | Sir Prysin (5083, 3203,3472) explains the three keys; Captain Rovin (5085, 3204,3497 level 2) gives key 2400 after the Wiki's persuasion (Aris -> demon -> "Sir Prysin said you would give me the key" -> "Why did he give you one of the keys then?"); Wizard Traiborn (5081, 3112,3157 level 1) wants 25 unnoted bones (deliverable in parts, kept in `quests.ds.bones`) and gives key 2399. |
| DS3 | Sir Prysin's key 2401: a bucket of water (1929) on the palace drain (locs 17423/17424) washes it down; "Search" on the drain then gives the key (OPEN-QUA20). |
| DS4 | With the three keys Sir Prysin hands over Silverlight (2402); a lost one is given again during the quest; after the quest it costs 500 coins (OPEN-QUA21). |
| DS5 | Entering the stone circle (centre 3227,3368, within 2 tiles) at stage 2 with Silverlight plays Denath's lines and spawns Delrith (npc 5079). Delrith can only be attacked with Silverlight wielded ("I'd better wield Silverlight first."). When he dies, Weakened Delrith (5080, op 1 "Banish") appears; the five words in the right order complete the quest, a wrong order brings Delrith back at full hitpoints. |
| DS6 | Reward: 3 quest points, Silverlight. |

OPEN-QUA19: the plugin API has no area-entry event; the circle is polled every 3 ticks (a soft timer) and its bounds come from the dark
wizards' spawns in `npc-spawns.tsv`; Denath, the dark wizards and the Wally cutscene are told in chat boxes only. OPEN-QUA20: the sewer
key lies by a muddy patch in the Varrock sewers, whose loc the cache gives no name for; the key is handed over by the drain. OPEN-QUA21: the
Wiki's Silverlight repurchase says 1,000 coins in the dialogue and checks 500; 500 is used (quest page: "after the quest he will charge
500"). OPEN-QUA22: Traiborn's other conversation ("Talk about something else") is not transcribed; the kitchen sink, the manhole and the
Wally scene's staging are not implemented. OPEN-QUA5: the antique lamps of 3 November 2026 are not given. Captain Rovin's options "Erm I
forgot." and "The castle has just received its ale delivery." have no transcript.
