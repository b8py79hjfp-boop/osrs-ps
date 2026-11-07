# Quest: Rune Mysteries

Code: `content/quests` `RuneMysteries`. Sources: OSRS Wiki /w/Rune_Mysteries, /w/Transcript:Rune_Mysteries,
/w/Transcript:Archmage_Sedridor, /w/Transcript:Duke_Horacio, /w/Transcript:Aubury; cache `Quest` row 125 (id 53): varp 63
(`runemysteries`), end state 6, 1 quest point, (20, 5000 tenths) Runecraft XP. The cache's multi-NPCs Sedridor (5034) and Aubury
(2886) switch on varp 63: value 6 shows the Teleport child.

| # | Rule |
|---|---|
| RM1 | Start: Duke Horacio (815, first floor of Lumbridge Castle) gives an air talisman. Varp 63: 0, 1 talisman given, 2 talisman handed to Sedridor, 3 research package given, 4 package handed to Aubury, 5 notes given, 6 finished (OPEN-QU1: 0 and 6 are cache values). A lost talisman is given again. |
| RM2 | Archmage Sedridor (spawned at 3103,9571, npc 5034 -> 11432 / 11433): takes the talisman, explains the lost essence mine, asks the player to take a package to Aubury; one free slot needed. A flag remembers whether the player doubted his identity (changes the closing lines). A lost package is given again. |
| RM3 | Aubury (2886, Varrock rune shop): takes the package, gives the research notes and offers tea (restores run energy fully; the Wiki says "completely restore"). Lost notes are given again. |
| RM4 | Sedridor takes the notes: quest complete. Reward: 1 quest point, 500 Runecraft XP (cache), the air talisman back, "Rune Essence Mine Access". Kudos (5) not implemented. |
| RM5 | After the quest Sedridor's menu offers the information branches (Wiki "after Rune Mysteries"). |

OPEN: OPEN-QU7 (essence mine teleport), OPEN-QU15 (Aubury's shop). The Duke's old standard text ("head wizard") differs from the
quest transcript; the quest transcript is used for the steps and the standard text for the unrelated options. Dragon-shield branch
(Dragon Slayer I) of the Duke is not implemented.
