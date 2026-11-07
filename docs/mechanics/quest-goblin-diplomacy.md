# Quest: Goblin Diplomacy

Code: `content/quests` `GoblinDiplomacy` (also Aggie's dyes), `QuestSpawns`. Sources: OSRS Wiki
https://oldschool.runescape.wiki/w/Goblin_Diplomacy, /w/Transcript:Goblin_Diplomacy, /w/Transcript:General_Bentnoze,
/w/Transcript:General_Wartface, /w/Transcript:Aggie, /w/Crate_(Goblin_Village); cache `Quest` row 64 (id 15): varbit 2378, end state 6,
5 quest points, (12, 2000 tenths) Crafting XP.

| # | Rule |
|---|---|
| GD1 | Start: General Bentnoze (npc 669) or General Wartface (670), Grubfoot (671), all spawned at 2959,3513. Their talk is one of five argument dialogues, then a menu; "What about a different colour?" offers the quest. Varbit 2378: 0, 1 started (orange wanted), 2 orange shown (blue wanted), 3 blue shown (brown wanted) (stages ours, OPEN-QU1), 6 finished (cache). |
| GD2 | Goblin mail (obj 288) is found once per crate in the three crates (locs 16559-16561, account flags). Orange dye (1769) or blue dye (1767) used on goblin mail make orange (286) or blue (287) mail; red dye (1763) + yellow dye (1765) make orange dye. |
| GD3 | Goblin mail of the wanted colour used on a general is taken and plays the Wiki cutscene lines (orange, then blue, then plain brown); a wrong colour gets "That wrong colour." / "We tell you get <colour> armour.". The brown one completes the quest: 5 quest points, 200 Crafting XP (cache), a gold bar (Wiki). |
| GD4 | Aggie (npc 120/121, op 3 "Dyes" on 121): red dye = 3 redberries + 5 coins, yellow = 2 onions + 5 coins, blue = 2 woad leaves + 5 coins (Wiki transcript); the insult fines are 5 coins (21-100 carried), 20 coins (101+), or a pot of flour. |
| GD5 | After the quest the generals play the five standard dialogues of "Transcript:General Bentnoze" (the three that need Another Slice of H.A.M. are not played). |

OPEN-QUA7: woad leaves come from Wyson the gardener's offer (20 coins for two leaves); his generic tree in `npc-dialogue.tsv` does not
carry the sale, so blue dye has no source yet. OPEN-QUA8: what the generals say during the quest (after accepting) is not published beyond
the wrong-colour lines; the ordering lines are reused. The Grubfoot cutscene (orange/blue Grubfoot npc 672/673, changing room) is told in
chat boxes only. Aggie's "no coins" line is ours (OPEN-QU4).
