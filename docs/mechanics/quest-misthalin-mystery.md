# Quest: Misthalin Mystery

Code: `content/quests` `MisthalinMystery`, spawns in `QuestSpawnsB`. Sources: OSRS Wiki /w/Misthalin_Mystery, /w/Transcript:Misthalin_Mystery; cache `Quest` row 94 (id 141):
varbit 3468, end state 135, 1 quest point; npc 7428-7441 and 7623-7641 (`mistmyst_*`), locs 29648-30156 (`mistmyst_*`), obj 21052-21060, interfaces 554 (piano) and 555 (gem puzzle).
Multi-NPCs on varbit 3468 fix: Abigale and Hewey (7428 / 7429) gone above 15, Sid (7430) above 19, Mandy (7433) above 89, the unmasked Abigale 115-129,
the post-quest Mandy (7434) 130-135. Rewards: uncut ruby, emerald and sapphire, 600 Crafting XP, 1 quest point.

| # | Rule |
|---|---|
| MM1 | Start: Abigale / Hewey (parents 7428 / 7429, Lumbridge Swamp 3236,3155) tell of the attack and point to the rowboat (loc 30108 at 3238,3139, "Board"; the island boat is loc 30109 at 1638,4800). Varbit 3468: 0, 10 started, 16 on the island, 20 Sid dead, 30 manor key found, 130 the killers are beaten, 135 finished (OPEN-QU1). |
| MM2 | On the island: the bucket (loc 30147 "Take"; taking it plays Sid's murder, ours as trigger), the barrel of rainwater (loc 30124 "Search", the bucket on it fills the bucket and empties the barrel to loc 30123) gives the manor key (obj 21052); the large doors (30110 / 30111) need the key; the knife (loc 30145) and the tinderbox (loc 30146) are taken from the manor's table and shelves. |
| MM3 | Mandy (post-quest form 7434 / 7630, outside the manor) finishes the quest from stage 130 with three empty slots for the gems. |

## OPEN

- **OPEN-QUB16:** the rest of the quest is not implemented: Tayten's death and the notes (objs 21056-21058), slashing the painting for the ruby key, the fuse / candles / explosive barrel and the damaged wall, Lacey's death, the piano (interface 554, D-E-A-D) and emerald key, Mandy's kitchen scene, the gem switch panel (interface 555, S-D-Z-E-O-R) and the sapphire key, the mirror showdown with the killer (wardrobes, thrown knives, the mirror NPCs 7640 / 7641) and the Abigale / Hewey fight, and the cutscenes (they need camera, scripted NPCs and an interface-driven puzzle). Until then the quest cannot be completed in play; `::quest 94 130` lets Mandy finish it for testing. The rowboat is a plain move and lands at the NPC spawn tiles of the Wiki; the Wiki's rowboat message texts are used.
