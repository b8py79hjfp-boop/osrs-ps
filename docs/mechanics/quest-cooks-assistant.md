# Quest: Cook's Assistant

Code: `content/quests` `CooksAssistant`, `FarmSupplies`. Sources: OSRS Wiki https://oldschool.runescape.wiki/w/Cook%27s_Assistant,
/w/Transcript:Cook%27s_Assistant, /w/Transcript:Cook_(Lumbridge), /w/Transcript:Millie_Miller, /w/Transcript:Gillie_Groats;
cache `Quest` row 17 (id 1): varp 29 (`cookquest`), end state 2, 1 quest point.

| # | Rule |
|---|---|
| CA1 | Start: Cook (npc 4626, kitchen of Lumbridge Castle, 3209,3214). Varp 29: 0 not started, 1 started, 2 finished (cache). |
| CA2 | Needs a bucket of milk, an egg, a pot of flour. Items are handed in on talking to the cook, in the order milk, flour, egg; each handed item sets an account flag (OPEN-QU2); the remaining ones are listed in a message box. All three finish the quest; a player carrying all three when starting hands them over at once (transcript "If the player already has the necessary items"). |
| CA3 | Reward: 1 quest point, 300 Cooking XP (cache col 33 = (7, 3000 tenths)), the Cook-o-matic 100 (not gated yet). |
| CA4 | After the quest the cook asks "How is the adventuring going, my friend?" (4 options). Members-world branch (Recipe for Disaster) is not implemented. |
| CA5 | Millie Miller (4627, 3167,3307) and Gillie Groats (4628, 3253,3275) answer as in their standard transcripts. |
| CA6 | Sources: see quests.md QU10 (dairy cow loc 8689 / 60788, wheat 15506-15508, hopper 24960-24963, controls 24964-24966, flour bin 1781 / 1782). Milking without a bucket plays Gillie's "Tee hee!" lines. |

Ground spawns (closed with static item spawns, ground-items.md G6, `content/world` `ItemSpawns`): the egg at the Lumbridge chicken farms (3169,3291, 3172,3301, 3175,3305, 3185,3297, 3226,3301, 3229,3299), the bucket (3225,3294) and the pot in the kitchen (3209,3214), all from the Wiki's item spawn tables; they reappear 100 ticks after being taken (OPEN-G6).

OPEN: the Cook-o-matic gate ("Hey, who said you could use that?"),
journal wording. The Wiki cook page lists a 3 November 2026 change (the cook now gives cooking tips); not applied.
