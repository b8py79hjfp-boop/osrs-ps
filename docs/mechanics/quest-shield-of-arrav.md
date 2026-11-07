# Quest: Shield of Arrav

Code: `content/quests` `ShieldOfArrav`, spawns in `QuestSpawnsB`. Sources: OSRS Wiki /w/Shield_of_Arrav (+ subpages Phoenix Gang, Black Arm Gang),
/w/Transcript:Shield_of_Arrav, /w/Transcript:Book_(Shield_of_Arrav), /w/Phoenix_crossbow (item spawns); cache `Quest` row 132 (id 6): varbit 13716
(0 / 1 / 2), 1 quest point; npc configs 5213 (Jonny the Beard, multi on varp 145: 0-7 not attackable, 8 attackable, 9+ gone) and 5211
(Weaponsmaster, multi on varbit 11513: 1 = gone); locs 2397-2405. Rewards: 600 coins, 1 quest point (Wiki: an antique lamp, no obj in cache 2757, OPEN-QUB9).

| # | Rule |
|---|---|
| SA1 | Start: Reldo (4243, Varrock Palace library 3210,3495). Progress is the account value `quests.soa.stage` (1 started, 2 book found, 3 book read, 4 Reldo told the player of the gangs, 10 Baraek paid, 11 Straven's task, 12 Phoenix member, 21 Katrine's task, 22 Black Arm member, 30 shield half handed in) and `quests.soa.gang` (1 Phoenix, 2 Black Arm); varbit 13716 is set to 1 at the start and 2 at the end. |
| SA2 | The bookcase (loc 2402 "Search") gives the book (obj 757) after the start; reading it shows the Wiki text and moves the quest on; Reldo then tells of the gangs. |
| SA3 | Phoenix Gang: Baraek (2881) asks 20 coins for the hideout; Straven (5212, hideout 3246,9780) sets the task to kill Jonny the Beard (5213 / 14139, Blue Moon Inn 3223,3395; varp 145 = 8 while tasked, 9 after his death; he drops the intel report, obj 761, for the killer). Straven takes the report, welcomes the player and gives the weapon store key (obj 759); the door 2397 beside Straven opens for members; the depot door 2398 needs the key; the chest 2403 (3235,9761) gives the left shield half (obj 763), one half at a time. |
| SA4 | Black Arm Gang: Charlie the Tramp (5209, 3206,3390) points to Katrine (5210, 3186,3385), who asks for two Phoenix crossbows (obj 767; ground spawns at 3243,3383 and 3245,3385 level 1, respawn 100 ticks per the Wiki) from the depot, reached with the partner's key. The Weaponsmaster (5211 / 14137) attacks non-members who talk to him or take a crossbow; his death sets varbit 11513 for the killer. Katrine takes the crossbows, the door 2399 opens, the cupboard 2400 (3189,3385 level 1) gives the right half (obj 765). Charlie's directions to the Phoenix Gang cost 10 coins (or 5 + 5). A Phoenix member is attacked/refused by Katrine and the other way round; one gang per account. |
| SA5 | The Curator (5214) takes the half and gives two half certificates of the player's gang (obj 11173 left = Phoenix, 11174 right = Black Arm; more on asking again). One of each combined (use on each other, or Inspect) makes the certificate (obj 769). The halves, the key and the crossbows can be passed by trade or by using them on the partner (drops the item for them, red message). |
| SA6 | King Roald (5215, 3222,3472) takes the certificate: 600 coins and the quest finishes; both partners must do this on their own account. |

## OPEN

- **OPEN-QUB9:** the cache variable has no stage detail, so the stages above are ours; the Wiki's antique lamp (3 November 2026) has no obj in cache 2757 and is not given; the Wiki's "Historian Minas" kudos part is not implemented; Baraek, Charlie and Straven give their information without checking the Reldo stage; Baraek's other options (furs, quest) and Reldo's trade option have no transcript; the Weaponsmaster attacks by the NPC hunt (range 6, only players of at most twice his combat level); the Jonny refusal message ("You can't attack him right now.") is ours; the post-quest Straven/Katrine texts apply to anyone who finished the quest; Jonny's drop is a private ground item (a ring of recoil check from the Wiki is not implemented).
- **OPEN-QUB1:** doors 2397, 2398, 2399 are passed by moving the player through them.
