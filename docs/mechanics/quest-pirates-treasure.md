# Quest: Pirate's Treasure

Code: `content/quests` `PiratesTreasure`, `PortSarimFerry`, `QuestDigging` (`QbHelpers.kt`), spawns in `QuestSpawnsB`.
Sources: OSRS Wiki /w/Pirate%27s_Treasure, /w/Transcript:Pirate%27s_Treasure, /w/Transcript:Pirate_message, /w/Casket_(Pirate%27s_Treasure),
/w/White_apron; cache `Quest` row 108 (id 16): varp 71, end state 4, 2 quest points; XP reward 800 Cooking (Wiki change of 3 November 2026).
Rule ids `PT*`, open questions `OPEN-QUB*` (batch B of the quest work; `OPEN-QUA*` belongs to the parallel batch).

| # | Rule |
|---|---|
| PT1 | Start: Redbeard Frank (3643, Port Sarim 3052,3250) asks for a bottle of Karamjan rum. Varp 71: 0, 1 started, 2 Frank gave the chest key, 3 the message is taken from the chest, 4 finished (OPEN-QU1: 0 and 4 are cache values). After finishing a player who completed Rum Deal gets the "Braindeath rum" answer (Wiki transcript). |
| PT2 | Karamja: the player gets the rum in the Musa Point bar (Zembo's shop, shops module). Luthas (3647, 2939,3154) employs the player ("Could you offer me employment"); account flag `quests.pt.luthas_job`. |
| PT3 | The crate by Luthas' hut (loc 2072 "Crate": op 1 Search, op 2 Fill): holds up to 10 bananas (`quests.pt.crate_bananas`) and one rum (`quests.pt.crate_rum`). Fill packs all carried bananas, a banana on the crate packs one, the rum on the crate stashes it; without the job the reply is "Why would I want to do that?". Search says how many bananas, "The crate is full of bananas." and "There is also some rum stashed in here too.". Banana and sliced-banana rum are refused with the Wiki lines. |
| PT4 | Luthas pays 30 coins for a full crate (dialogue "I've filled a crate with bananas."); the crate is emptied and the rum in it travels to Wydin (`quests.pt.rum_at_wydin`). |
| PT5 | Customs: the Musa Point customs officer (3648, 2950,3151) searches for rum: rum is confiscated (the Ring of Charos(a) offers a charm that fails and drops the rum); without rum the boarding charge is 30 coins (free with the ring) and the player is moved to Port Sarim. Op 3 "Travel" skips the questions. The Port Sarim sailors (3644-3646, 3028,3218) take the player to Musa Point for 30 coins the same way (shared with Dragon Slayer I). |
| PT6 | Wydin (2890, 3013,3207): "Can I get a job here?" hires a player who has a white apron (account flag `quests.pt.wydin_job`; the apron must be worn to pass the back room door, loc 2069). The crate in the back room (loc 2071 "Search") gives the rum back when `rum_at_wydin` is set. The white apron hangs on the hook in Gerrant's shop (item spawn 3016,3229 in `QuestSpawnsB`). |
| PT7 | Redbeard Frank takes the rum (dialogue, or the rum used on him) and gives chest key 432 (stage 2); a lost key is replaced ("Reclaiming the key"). Rum with a banana or sliced banana is given back with the Wiki lines. |
| PT8 | The key used on Hector's chest (loc 2079, Blue Moon Inn upstairs; "Open" says "The chest is locked.") gives the pirate message (obj 433, stage 3). Reading it: "Visit the city of the White Knights. In the park, Saradomin points to the X which marks the spot." |
| PT9 | Digging with a spade (`QuestDigging`, `DigEvent`) at Falador Park 2999,3383 with the message: the first dig calls the Gardener (npc 3651, overhead "First moles, now this! Take this, vandal!"); while he is alive "I can't dig up anything with him attacking me!"; the second dig gives the casket (obj 7956) and finishes the quest. Rewards: 2 quest points, 800 Cooking XP. The casket opens to 450 coins, a gold ring and an emerald; used on Frank he declines a share (Wiki transcript). |

## OPEN

- **OPEN-QUB1:** the boat ride has no scene or cutscene; the player is moved to the other dock (arrival tiles are the NPC spawn tiles of the Wiki). Door passing of locked doors (Wydin's back room) moves the player across the wall without the door swinging (plugin-api has no way to ask the generic door script).
- **OPEN-QUB2:** the spade's generic messages ("You dig a hole in the ground...", "You find nothing of interest.") and the 2-tick dig delay are not published.
- **OPEN-QUB3:** replies for filling the crate without carrying bananas and the ferry messages ("You board the ship.", "The ship arrives at ...") are not in the transcript. Frank's "Arr!" and "Do you have anything for trade?" options, and Wydin's "What can you recommend?", have no transcript: the player says the line and the conversation ends.
- **OPEN-QUB4:** banana tree picking (loc 2073-2078 "Pick"): message, bananas per tree (5) and regrow time (100 ticks) are ours; a search of Wydin's crate without the rum.
- **OPEN-QUB5:** the Gardener hunts within 8 tiles and leaves after 300 ticks (Wiki: "run out of the park to despawn him"); the "dug once" state is not saved over a logout.
