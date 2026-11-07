# Quest: Prince Ali Rescue

Code: `content/quests` `PrinceAliRescue`, spawns in `QuestSpawnsB`. Sources: OSRS Wiki /w/Prince_Ali_Rescue, /w/Transcript:Prince_Ali_Rescue,
/w/Transcript:Aggie, /w/Transcript:Ned (rope); cache `Quest` row 112 (id 10): varp 273, end state 110, 3 quest points; npc configs 11642-11645
(multi-NPCs on varp 273). Rewards: 700 coins, 500 Smithing, 500 Crafting (Wiki change of 3 November 2026), free passage of the Al Kharid toll gate.

| # | Rule |
|---|---|
| PA1 | Start: Chancellor Hassan (4285, Al Kharid palace 3302,3163) sends the player to Osman (4286, 3286,3180), who sends the player to Leela (4274, Draynor 3113,3262). Varp 273: 0, 10 started, 20 Osman, 30 Leela's plan, 32 everything gathered, 34 Joe talked to, 40 Joe drunk, 50 Keli tied up, 60 cell unlocked, 100 Prince escaped, 110 finished. 0 and 110 are the cache's values; the bounds 40 (Lady Keli gone above it), 90 (jailed Prince gone above it) and 100 (palace Prince shows 100-110) are read from the multi-NPC configs 11643, 11644, 11645 (Joe 11642 shows up to 100). |
| PA2 | Key: Lady Keli (parent npc 11643, jail 3128,3244) is asked about joining her, then about the key; "touch the key" with soft clay in the backpack gives a key print (obj 2423). The key print and a bronze bar used on a furnace make the bronze key (obj 2418, Wiki change of 14 January 2026). The older flow where Osman takes the print and a bar and Leela hands over the key once everything is gathered is also kept (the transcript still shows it): account flag `quests.par.osman_key`, the lost key costs 15 coins at Leela. |
| PA3 | Disguise: Ned (4280, 3099,3259) makes a wig (obj 2421) from 3 balls of wool; yellow dye (made by Aggie for 2 onions and 5 coins, or bought) used on the wig gives the blonde wig (obj 2419); Aggie (120/121, 3086,3259) makes paste (obj 2424) from ash, flour, water (bucket or jug) and redberries; the pink skirt is bought. |
| PA4 | Leela checks key (or Osman's copy), disguise, rope and three beers (dialogue "I do indeed."), then sends the player to Joe (parent npc 11642, 3124,3244). Joe drinks one beer at once and, if two more are given, is drunk (stage 40). |
| PA5 | Rope on Lady Keli (or talking to her when ready) ties her up and puts her in the cupboard (stage 50). Key on the cell door (loc 1543 at 3128,3246) unlocks it (stage 60; the player is moved through the door). Giving Prince Ali (parent npc 11644) the wig, skirt and paste lets him escape (stage 100). |
| PA6 | Hassan pays (700 coins, 500 Smithing, 500 Crafting, 3 quest points) and the quest finishes. |

## OPEN

- **OPEN-QUB6:** details the transcripts do not give: whether the empty bucket or jug comes back from Aggie's paste; the messages for dyeing the wig and making the key; red and blue dyes of Aggie's NPC transcript are not implemented; Osman and Leela before the Hassan step play the first dialogue; Joe's beer option is shown only while a beer is carried; the answer to "I've heard a little, but I think Katrine is tougher." is untranscribed ({{tmissing}}) and reuses the other Katrine answer; the cell door loc (1543, named `elfdoor` in the symbols) is the Wiki-less guess from the loc placements at the jail; Prince Ali's cell position (the NPC spawns by the multi-NPC at the jail) and the Osman toll gate / Sorceress's Garden unlocks are not implemented.
- **OPEN-QUB1** (door passing, see quest-pirates-treasure.md): the unlocked cell door does not swing, the player is moved through it.
