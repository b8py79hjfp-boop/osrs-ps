# Quest: Sheep Shearer

Code: `content/quests` `SheepShearer`, `FarmSupplies`, `QuestSpawns`. Sources: OSRS Wiki /w/Sheep_Shearer, /w/Transcript:Sheep_Shearer,
/w/Transcript:Fred_the_Farmer, /w/Sheep, /w/Sheep_(penguins); cache `Quest` row 131 (id 5): varp 179 (`sheep`), end state 21,
1 quest point, (12, 1500 tenths) Crafting XP.

| # | Rule |
|---|---|
| SS1 | Start: Fred the Farmer (npc 732, 3189,3273). Varp 179: 0 not started, 1 started, each ball of wool handed in adds 1, 21 finished (1 + 20, cache end state). |
| SS2 | Fred gives shears if the player has none (during the start dialogue or "How do I shear sheep, again?"). |
| SS3 | Balls of wool (unnoted) are handed in on talking to him: the number carried up to what is left; the last ones finish the quest. A player with 20 balls at the start finishes at once (transcript). |
| SS4 | Reward: 1 quest point, 150 Crafting XP (cache), 60 coins (Wiki). The extra 50 Crafting XP of spinning is the spinning wheel's own. |
| SS5 | Sheep (unsheared types 2693-2699, 2786-2789): op "Shear" or shears used on them gives 1 wool, 100% success (Wiki, 3 November 2022), the sheep turns into the sheared type and regrows after 50 ticks. Shearing without shears: "You need a set of shears to do this." (wording OPEN-QU4). |
| SS6 | The Thing (npc 731, 3203,3267): shearing it says "The... whatever it is... manages to get away from you!" and sets a flag; Fred then offers "Fred! Fred! I've seen The Thing!". |

OPEN: the cache's spawn table puts sheared sheep in the pen (QuestSpawns converts them); sheep wander speed and regrow animation.
