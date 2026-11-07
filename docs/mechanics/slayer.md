# Slayer

Code: `content/skills/.../Slayer*.kt` (`Slayer` the plugin and conversation, `SlayerAssign` the pools and rolls, `SlayerRewards` the rewards
interface, `SlayerSuperiors`, `SlayerGate` the requirement expressions, `SlayerState` the variables, `SlayerCache` the cache tables, `SlayerData`
the resource tables). Data: `slayer-tasks.tsv`, `slayer-monsters.tsv` and `slayer-masters.tsv` (written by `tools/wiki-import/slayer_tasks.py`,
re-runnable), `slayer-requirements.tsv` and `slayer-quests.tsv` (written by `tools/wiki-import/slayer_requirements.py`, re-runnable),
`slayer-bosses.tsv` and `slayer-superiors.tsv` (written by hand from the Wiki tables named below). Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06 and 2026-11-07:
- (M) each master's page and its task table: https://oldschool.runescape.wiki/w/Turael (transcluded `Turael/Slayer assignments`),
  /w/Spria, /w/Mazchna, /w/Vannaka, /w/Chaeldar, /w/Konar_quo_Maten, /w/Nieve, /w/Duradel, /w/Krystilia (monster, amount range, extended
  range, unlock requirement, task weight; points per task interval)
- (W) "Slayer task" (task weighting: "the weights of blocked tasks, tasks that are toggled off, and tasks that the player does not have
  the Slayer level or quest requirements for are not added to the sum"), https://oldschool.runescape.wiki/w/Slayer_task
- (P) "Slayer reward point" (points are "not awarded for the first four tasks"; the intervals 10, 50, 100, 250, 1,000; the points
  table per master; the Elite Western and Kourend diary columns), https://oldschool.runescape.wiki/w/Slayer_reward_point
- (S) "Slayer Master" (combat and Slayer level needed to be assigned tasks), https://oldschool.runescape.wiki/w/Slayer_Master
- (I) the `{{Infobox Monster}}` of every monster with a `cat` (task group), `slaylvl` (Slayer level to harm it) and `slayxp`
  (Slayer xp per kill), e.g. https://oldschool.runescape.wiki/w/Aberrant_spectre
- (G) "Enchanted gem" (the "Check" op), https://oldschool.runescape.wiki/w/Enchanted_gem
- (K) "Krystilia" ("Only kills within the Wilderness count towards her tasks"; a separate counter; refuses Slayer level 1; the footnotes of
  her table give the quests of Abyssal demons, Bloodveld, Dust devils, Nechryael and Spiritual creatures), https://oldschool.runescape.wiki/w/Krystilia
- (T) npc-dialogue.tsv tree 40 (Wiki transcript of Vannaka, Chaeldar, Mazchna and Nieve): the shared greeting and options, and the
  "Look at rewards." / "Look at shop." menu
- (L) the cache (cache 2757): the "Assignment" (op 3), "Trade" (op 4) and "Rewards" (op 5) ops and "Talk-to" on the master npcs, obj 4155
  `slayer_gem` with "Check"
- (R) "Slayer Rewards" (the unlock, extend, buy and tasks tables; costs; toggles; block slots; Task Storage; Watch the Birdie needs 60 Agility
  or Strength), https://oldschool.runescape.wiki/w/Slayer_Rewards
- (B) "Boss Slayer" (the 33 assignable bosses and their requirements, amounts 3-35, Barrows 3-36, +5,000 xp, weights 12 / 8, Krystilia only
  Wilderness bosses, Konar alone assigns the Alchemical Hydra), https://oldschool.runescape.wiki/w/Boss_Slayer
- (U) "Superior slayer monster" (spawn chance, ownership, despawn, unique drops, the table of superiors),
  https://oldschool.runescape.wiki/w/Superior_slayer_monster
- (TU) "Turael" (Task changing), https://oldschool.runescape.wiki/w/Turael
- (E) "Slayer equipment" (the shop and the protective items), https://oldschool.runescape.wiki/w/Slayer_equipment
- (D) cache db tables of rev 242 (dumped with `cache-dump dbrows <table>`): 113 `slayer_task` (task ids as the client shows them, the longer-task
  unlocks, the weighting unlock, Stop the Wyvern), 114 `slayer_master_task` (tasks per master, boss weights), 115 `slayer_area`, 116
  `slayer_task_sublist` (boss ids), 117 `slayer_unlock` (bit, cost, name, tab); enums 840-842 (the Buy tab)
- (C) rev 242 client script disassembly (`rs2asm`, the scripts of interface 426): 405 (init), 407/408 (tabs), 413/414 (a reward row and its
  confirm panel), 423/426/427 (task actions and block slots), 1089/1092 (extend), 9736/9738 (tasks tab), 9789/322 (Buy), 8048/8051 (reward
  bit lookup), 823/9741 (prices), 824/8025 (block lists), 5319 (the task overlay); all read cache variables, none is called by the server

## Rules

| # | Rule | Source |
|---|---|---|
| SL1 | The task lives in the cache's own variables, so the client, RuneLite's Slayer plugin and the rewards interface need nothing else: varp `slayer_count` (394, kills left; -1 while a boss task waits for its amount), varp `slayer_target` (395, the cache `slayer_task` id the client shows; 98 = boss), varbit `slayer_target_bossid` (4723), varbit `slayer_master` (4067, the cache master id of the giver). Points are varbit `slayer_points` (4068, 17 bits); the task streak is varbit `slayer_tasks_completed` (4069, every master but Krystilia) and `slayer_wilderness_tasks_completed` (5617, Krystilia's own counter). Rewards are bits of varps 1076, 1344 and 5587 (bit n of the cache table 117 row is bit n % 32 of the varp n / 32). Block lists are the varbits `slayer_blocked_<master>_1..6` and `_diary` holding task ids. All are persisted with the player's varps. Only the stored task (Task Storage) is a `saved` value (`slayer.stored.id/left/boss/master`). A second task cannot be taken while the kills left are not 0. | D, C |
| SL2 | Masters are matched by cache npc name (`onOpNpcNamed`): Turael / Aya, Spria, Mazchna / Achtryn, Vannaka, Chaeldar, Konar quo Maten, Nieve / Steve, Duradel / Kuradal, Krystilia. The "Assignment" op (op 3), "Talk-to" and "Rewards" (op 5) are theirs; "Trade" (op 4) is the Slayer Equipment shop of content/shops (SL15). Krystilia is spawned by `npc-spawns.tsv`; the other masters are not in it and are spawned at world start at the position of their Wiki infobox map (`slayer-masters.tsv`: Turael 2931,3536; Spria 3091,3267; Mazchna 3511,3509; Vannaka 3147,9914; Chaeldar 2444,4431; Konar 1309,3785; Nieve 2432,3423; Duradel 2869,2982 level 1). The pre-quest npcs are spawned (Turael, Mazchna, Duradel). Master ids of the cache: Turael 1, Mazchna 2, Vannaka 3, Chaeldar 4, Duradel 5, Nieve 6, Krystilia 7, Konar 8, Spria 9 (Mortimer 10 is not implemented). | M, L, D |
| SL3 | A master can assign a task when the player's combat level reaches the master's (Mazchna 20, Vannaka 40, Chaeldar 70, Konar 75, Nieve 85, Duradel 100 and Slayer 50; Turael, Spria and Krystilia none) and, for Krystilia, Slayer is above 1. A task row is eligible when the player's base levels meet its `slayer`, `combat` and other skill levels (`skills`), its quest and unlock requirements are met (SL10), it is not in the master's block list (SL11) and, for Fossil Island wyverns, Stop the Wyvern is not bought and switched on. | M, S, K, W, R |
| SL4 | The task is one weighted pick among the eligible rows of the master and the boss entry (SL14); the weight of Frost dragons is 8 instead of 5 once "Chance of Heavy Frost" is bought. The amount is uniform in `min..max`, or in the extended range `emin..emax` of the master table once the task's "Extend" reward is bought (the cache's amounts of table 113 where the Wiki table has none; an additive extension adds to the base range). Konar gives the task in a place: one of the `slayer_area` rows of the cache for the task, said in the assignment ("... in Brimhaven Dungeon") and stored in varp `slayer_area` (2096). | M, W, R, D |
| SL5 | When an NPC dies, the player credited with it (`DeathEvent.lootFor`) with a task whose group contains the NPC type gets the monster's `slayxp` Slayer xp and one kill off the task. The group of an npc is the infobox category of its page (so "Cow calf" and "Undead cow" count as cows); the cache task of a Wiki task is the one with the same name (five Wiki names are spelled differently by the cache: `SlayerCache.ALIASES`). A boss task counts the kills of the boss's npcs (`slayer-bosses.tsv`, by cache name). A Krystilia task counts only while the player stands in the Wilderness (`SharedAttrs.IN_WILDERNESS`). | I, K, B |
| SL6 | The last kill clears the task, adds 1 to the streak of the master's counter and pays points: none for streak 1-4, else the master's points for the largest interval (1, 10, 50, 100, 250, 1,000) the streak is a multiple of. Per master (every / 10th / 50th / 100th / 250th / 1,000th): Turael and Spria 0; Mazchna 6 / 30 / 90 / 150 / 210 / 300; Vannaka 8 / 40 / 120 / 200 / 280 / 400; Chaeldar 10 / 50 / 150 / 250 / 350 / 500; Konar 18 / 90 / 270 / 450 / 630 / 900 (20 / 100 / 300 / 500 / 700 / 1,000 with the Elite Kourend & Kebos diary); Nieve 12 / 60 / 180 / 300 / 420 / 600 (Duradel's with the Elite Western Provinces diary); Duradel 15 / 75 / 225 / 375 / 525 / 750; Krystilia 25 / 125 / 375 / 625 / 875 / 1,250. Turael's and Spria's tasks count toward the four tasks before points start. A boss task also gives 5,000 Slayer xp. | P, M, B |
| SL7 | A player attacks a Slayer monster only with its `slaylvl` in current Slayer (boosts count, OPEN-SL6): the `AttackCheck` event is denied with "You need a Slayer level of N to attack this monster." (`content/combat` publishes it before every attack; no combat file is touched). The level of an npc type is the highest of its groups. A superior can be attacked only by the player whose kill spawned it: "That is not your superior creature." (SL12). | I, U |
| SL8 | The enchanted gem (obj 4155) "Check" op reports the task and the streak ("You're assigned to kill X; only N more to go."), or that there is none. | G |
| SL9 | "Talk-to" plays the shared greeting "'Ello, and what are you after then?" and a menu: "I need another assignment." (rolls a task), "Have you any rewards for me, or anything to trade?" (the master's reply, then "Look at rewards." opens the rewards, "Look at shop." the shop, "Cancel.") and "Er... Nothing...". With a task, an assignment request answers "You're still hunting X; you have N to go. Come back when you've finished your task." | T |
| SL10 | Requirements that are not levels are expressions (`slayer-requirements.tsv`): `&` joins clauses, `\|` the alternatives of a clause, atoms are `quest:<name>:complete\|started`, `unlock:<name>` and `skill:<name>:<level>`. The key is the lower-case Wiki text of the `other` column, or `Master\|group` for what the master tables do not print (Krystilia's footnotes: Priest in Peril for Abyssal demons, Bloodveld and Nechryael, Desert Treasure I for Dust devils, Death Plateau and 60 Strength or Agility for Spiritual creatures, Watch the Birdie for Aviansie; "I Wildy More Slayer" for her Jellies, Dust devils, Nechryael and Abyssal demons), or `unlock:<name>` for what buying a reward needs (Watch the Birdie: 60 Agility or Strength). A quest is read from its cache state variable (`slayer-quests.tsv`, the rows of content/quests `quest-states.tsv`, the same approach as content/diaries `QuestGate`): complete = the end value or more, started = past the unstarted value ("partial completion", OPEN-SL8). A quest no content implements stays unstarted, so the tasks that need it are never assigned (OPEN-SL2). An unlock is its bit (SL1) found by its cache name. | M, K, R, D |
| SL11 | Cancel, block and the block lists. The tasks tab of the rewards (SL13) acts on the master whose rewards were opened (`slayer_master_in_focus`, varbit 17868). Cancel costs 30 points and drops the task (the streak stays). Block costs the master's price (Turael / Spria 40, Mazchna 50, Vannaka 60, Chaeldar 70, Konar 80, Nieve 90, Duradel / Krystilia 100), drops the task and puts its id in the first open empty slot of that master's list; a blocked task is not assigned by that master (Turael and Spria share one list). Slots 1-6 open at 50, 100, ... 300 quest points (varp `qp`), slot 7 with the Elite Lumbridge & Draynor diary reward (varbit 4538). Unblocking is free and refunds nothing. The boss, TzTok-Jad and TzKal-Zuk tasks cannot be blocked. Turael (not Spria) changes a task of another master into one of his own: not if he could assign that task himself, not his own; it costs the streak (Krystilia's tasks reset the Wilderness streak). | R, TU, C |
| SL12 | Superior monsters. With "Bigger and Badder" bought and switched on (varbit 14825 off), the death of a monster of the player's task whose name is in `slayer-superiors.tsv` spawns its superior on the same tile with a 1/200 chance (1/180 in the Wilderness), if the player has none out; the player is told "A superior foe has appeared..." (red). Only that player can attack it. It is gone after 2 minutes unfought, or once its player has left or logged out (checked every 30 seconds). Its death gives the table's Slayer xp (once: the kill also counts for the task), 3 rolls on the monster's drop table (content/drops, the superior npc ids), and the two unique tables, each with the chance `1/(200 - (Slayer + 55)^2 / 125)`: the first gives the imbued heart 1/8, the dust battlestaff 7/16 or the mist battlestaff 7/16, the second the eternal gem 1/8. | U |
| SL13 | The Slayer Rewards interface (cache interface 426, SL1 variables). "Rewards" sets `slayer_master_in_focus`, copies the current and stored task into the scratch varps 261-266 the tasks tab reads (count, task id, boss id, then the stored ones), runs client script 2524 and opens the modal; the client builds every tab by itself. The server answers three kinds of click (derived from the scripts, OPEN-SL15): IF_BUTTON1 on `confirm_button` with the slot = the reward's bit (buy it; or switch it off; bits 35 Bigger and Badder, 43 Stop the Wyvern and 53 Revenants switch off and on for free with `slayer_toggleoff_*`), or 66 + n for 1 cancel, 2 block, 9 extend everything, 10 store, 11 swap / unstore, 3-7, 12, 8 unblock slots 1-7; IF_BUTTON2-5 on `buy_items` for Buy 1 / 5 / 10 / 50 of item n of enum 840 (price enum 842 per item, set size enum 841: broad arrows and bolts come in 250s; the herb sack, rune pouch and looting bag only singly; one rune pouch can be owned). Buying a reward needs the points ("You need N Slayer reward points for that; you have M."). "Extend everything" costs the sum of the extensions not yet bought, cut to 95% (at least 2) when there is more than one. Task Storage (500 points) stores one task and swaps or unstores it for free. | R, C, D |
| SL14 | Boss tasks. With "Like a Boss" bought, a master's boss entry (cache weights: Duradel 12, Konar / Nieve / Krystilia 8) is one more weighted choice; the boss is uniform among the bosses whose requirements are met (`slayer-bosses.tsv`: Slayer levels and skills from the cache table 113, quests from the Wiki); Krystilia only gives Wilderness bosses (not the King Black Dragon), only Konar the Alchemical Hydra. The master asks how many to kill (3-35, Barrows 3-36; count dialog) and the task waits at -1 kills until answered (the master reminds the player when asked again). Blocks and switches do not apply to the boss entry. | B, D |
| SL15 | Each master's "Trade" op opens the Slayer Equipment shop (content/shops shop 339, stock from the Wiki "Slayer Equipment (shop)"; the owners column of `shops.tsv` lists the masters and their quest replacements). | E, L |

## Data

`slayer-tasks.tsv`: 326 rows (Turael 24, Spria 25, Mazchna 30, Vannaka 45, Chaeldar 40, Konar 39, Nieve 46, Duradel 43, Krystilia 37 less
the "Boss" rows). `slayer-monsters.tsv`: 2,217 rows for the 1,660 `Infobox Monster` pages. Krystilia's table has no requirement column; her
Slayer levels are those of the six groups her page names (abyssal demons 85, bloodveld 50, dust devils 65, jellies 52, nechryael 80,
spiritual creatures 63), the lowest level above 1 in the group. `slayer-requirements.tsv`: 50 rows. `slayer-quests.tsv`: 34 quests. `slayer-bosses.tsv`:
33 bosses. `slayer-superiors.tsv`: 38 superiors (cache npc ids; where the cache has several of one name and combat level the first).

Quests that content/quests implements and the tasks use: Dragon Slayer I (started: blue, black, red, metal and frost dragons) and Ernest the
Chicken (killerwatts). Of the 326 rows, 109 are never assigned yet because they need only quests nobody implements, and so are these groups:
araxyte, banshee, brine rat, cave horror, crawling hand, custodian stalker, dagannoth, dark beast, elf, fever spider, fossil island wyvern, gargoyle,
ghoul, gryphon, infernal mage, jungle horror, lesser nagua, mogre, molanisk, mutated zygomite, otherworldly being, scabarite, sea snake, shadow
warrior, skeletal wyvern, sourhog, suqah, terror dog, waterfiend, werewolf (the quests: Priest in Peril, Cabin Fever, Horror from the Deep,
Mourning's End Part II, Regicide, Rum Deal, Troubled Tortugans, Elemental Workshop I, Lost City, Olaf's Quest, Perilous Moons, Shadows of Custodia,
Legends' Quest, Death to the Dorgeshuun, Haunted Mine, Royal Trouble, Contact!, Lunar Diplomacy, Barbarian Training, A Porcine of Interest,
Skippy and the Mogres, Song of the Elves). Dust devils, Spiritual creatures, Kurask and Abyssal demons wait for Desert Treasure I, Death Plateau,
Song of the Elves and Priest in Peril (or Fairytale II). They need no code change: when a quest sets its state variable the task opens.

## Protective equipment (proposals for content/combat, not implemented)

Nothing the Wiki lists as protective equipment (E) is a check in this module: in game the player can attack without it and it changes what
the monster does or what the hit does, which is combat (implemented where the Wiki gives numbers: slayer-equipment.md). Proposals, each from the "Slayer equipment" page: earmuffs (or a Slayer helmet) against
banshees' stat-draining screech; nose peg against aberrant spectres' stench; spiny helmet against wall beasts; facemask against dust devils'
attack and the Smoke Dungeon; witchwood icon against cave horrors; mirror shield against basilisks and cockatrice; reinforced goggles against
sourhogs; insulated boots against killerwatts and rune dragons' electricity; slayer gloves against fever spiders; Shayzien armour against the
lizardman shaman's poison; Tortugan shield against dire and shellbane gryphons; boots of stone / granite boots / boots of brimstone against the
Karuulm floor; leaf-bladed weapons, broad ammunition and Magic Dart as the only damage to turoth and kurask; a lit bug lantern as the only damage to
harpie bug swarms; a crystal chime for warped creatures; the finishing items (rock hammer, bag of salt, ice cooler, fungicide spray) behind the
"Gargoyle Smasher", "Slug Salter", "Reptile Freezer" and "'Shroom Sprayer" rewards (a kill at critical health; the unlock bits 0-3 are set by the
rewards); the Slayer ring and gem "Check"; lure items (slayer bell for molanisks, fishing explosive for mogres). The Slayer helmet / black mask
bonus is already in combat (combat-formulas.md F23-F24).

## OPEN

- **OPEN-SL1: masters' positions.** The spawn import has no slayer masters except Krystilia. Positions are the infobox map of each
  master's page; Vannaka's is the Edgeville Dungeon, Duradel's is on level 1. If a later spawn import adds them they would be doubled.
  Aya, Achtryn, Kuradal and Steve (the quest replacements) answer the same ops but are not spawned. Mortimer (master 10, Venator tasks) is not implemented.
- **OPEN-SL2: quests.** See Data: tasks behind quests that no content module implements are not assigned. The quests the masters themselves
  need (Priest in Peril for Mazchna, Lost City for Chaeldar, Shilo Village for Duradel, A Porcine of Interest for Spria) are not checked: they are walking access.
- **OPEN-SL3: still not implemented.** The difficulty option of the shared conversation ("Let's talk about the difficulty of my assignments",
  tree 40: its variable is unknown), the "View List" panel (OPEN-SL10), Mortimer, the Slayer ring / helmet "Check", partner tasks and the
  Slayer cape's re-roll.
- **OPEN-SL4: wording.** The Wiki has no text for the task-complete message, the gem's "Check" reply, the level-requirement refusals, the rewards
  messages (bought, cancelled, blocked, not enough points, no free slot), Turael's change of task, or the boss amount prompt; the texts in the code are plausible, not sourced.
- **OPEN-SL5: Konar's location is not enforced.** The cache names the places (`slayer_area`, 56 rows: "in the Smoke Dungeon", ...) but has no
  region or polygon for them (the world-region column is empty), so the area is chosen, said and stored in `slayer_area` but a kill anywhere counts.
  Needs the area geometry. Monsters that are only killable on a task (the Wiki "Slayer task" page) are not restricted.
- **OPEN-SL6: gating details.** Equipment that is required to fight is not modelled (see the proposals); the black mask / Slayer helmet bonus is
  (combat-formulas.md F24); the Slayer level of the gate uses boosts, which the Wiki does not say.
- **OPEN-SL7: weights in the pool.** The weights are the Wiki's current table (summer 2025 sweep-up); a task whose group has no cache npc in
  `slayer-monsters.tsv` is dropped by the importer.
- **OPEN-SL8: partial completion.** "Partial completion of" and "started" a quest are read as past the unstarted state; the Wiki names no stage
  (the same as OPEN-AD14). Dragon Slayer I (blue, black, red, metal and frost dragons) is the only task quest implemented, so a started quest opens them.
- **OPEN-SL9: superior spawn.** A superior appears on the tile of the monster that died; the Wiki's order for larger superiors (south-west tile if
  unblocked, then the respawn tile, then near the player) and the 1/150 chance after claiming the Elite Combat Achievements are not implemented. The
  10x clue chance of a superior is in the drop data only if the drop import has it (the superior ids have their normal tables with 3 rolls).
- **OPEN-SL10: "View List".** The tasks tab's "View List" opens interface 924, which a server script fills (client scripts 8060 / 8061, arguments the
  two unlock words and the master); the click is answered with a message. Blocking works for the current task.
- **OPEN-SL11: superior leave radius.** "Leaves the area" has no distance in the Wiki; 15 tiles.
- **OPEN-SL12: boss amounts.** The cap rises by 5 per Combat Achievement tier (6 for Barrows) up to 65 / 72; the tiers are not read (35 / 36). A boss
  task from the Slayer cape re-roll and the boss-specific Wiki requirements without data (the brittle key of the Grotesque Guardians, "having accessed Great
  Kourend by boat" for Sarachnis) are not checked. Vorkath's Slayer 18 of the Wiki table is not in the cache and is not checked.
- **OPEN-SL13: points cap.** Points stop at what the 17-bit varbit holds (131,071); the Wiki names no cap.
- **OPEN-SL14: Krystilia notes not modelled.** The footnote requirements of her other rows (if any more are listed) beyond the seven in `slayer_requirements.py`.
- **OPEN-SL15: the interface clicks are derived from the scripts, not seen on a real client.** The confirm child indices, the Buy ops and the
  IF_SETEVENTS ranges (confirm_button 0-78, buy_items 0-9, op 1 of view_tasks) follow the disassembly; the scratch varps 261-266 are written only while
  the interface is open.
