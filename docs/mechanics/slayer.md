# Slayer

Code: `content/skills/.../Slayer.kt`, data in `SlayerData.kt` and the resource tables `slayer-tasks.tsv`, `slayer-monsters.tsv` and
`slayer-masters.tsv` (written by `tools/wiki-import/slayer_tasks.py`, re-runnable). Shared skilling rules: skilling.md.

Sources, fetched as raw wikitext from oldschool.runescape.wiki on 2026-11-06:
- (M) each master's page and its task table: https://oldschool.runescape.wiki/w/Turael (transcluded `Turael/Slayer assignments`),
  /w/Spria, /w/Mazchna, /w/Vannaka, /w/Chaeldar, /w/Konar_quo_Maten, /w/Nieve, /w/Duradel, /w/Krystilia (monster, amount range, extended
  range, unlock requirement, task weight; points per task interval)
- (W) "Slayer task" (task weighting: "the weights of blocked tasks, tasks that are toggled off, and tasks that the player does not have
  the Slayer level or quest requirements for are not added to the sum"), https://oldschool.runescape.wiki/w/Slayer_task
- (P) "Slayer reward point" (points are "not awarded for the first four tasks"; the intervals 10, 50, 100, 250, 1,000; the points
  table per master), https://oldschool.runescape.wiki/w/Slayer_reward_point
- (S) "Slayer Master" (combat and Slayer level needed to be assigned tasks), https://oldschool.runescape.wiki/w/Slayer_Master
- (I) the `{{Infobox Monster}}` of every monster with a `cat` (task group), `slaylvl` (Slayer level to harm it) and `slayxp`
  (Slayer xp per kill), e.g. https://oldschool.runescape.wiki/w/Aberrant_spectre
- (G) "Enchanted gem" (the "Check" op), https://oldschool.runescape.wiki/w/Enchanted_gem
- (K) "Krystilia" ("Only kills within the Wilderness count towards her tasks"; a separate counter; refuses Slayer level 1)
- (T) npc-dialogue.tsv tree 40 (Wiki transcript of Vannaka, Chaeldar, Mazchna and Nieve): the shared greeting and options
- (L) the cache (cache 2757): the "Assignment" op (op 3) and "Talk-to" on the master npcs, obj 4155 `slayer_gem` with "Check"

## Rules

| # | Rule | Source |
|---|---|---|
| SL1 | A task is three persisted values (`saved` / `setSaved`): `slayer.group` (the hash of the group key), `slayer.left` (kills left) and `slayer.master` (the hash of the master's key); `String.hashCode` is specified, so the values survive restarts. A second task cannot be taken while `slayer.left` > 0. Also persisted: `slayer.streak` (tasks in a row from every master but Krystilia), `slayer.wstreak` (Krystilia's own counter) and `slayer.points`. | K, P |
| SL2 | Masters are matched by cache npc name (`onOpNpcNamed`): Turael / Aya, Spria, Mazchna / Achtryn, Vannaka, Chaeldar, Konar quo Maten, Nieve / Steve, Duradel / Kuradal, Krystilia. The "Assignment" op (op 3) and "Talk-to" both give a task. Krystilia is spawned by `npc-spawns.tsv`; the other masters are not in it and are spawned at world start at the position of their Wiki infobox map (`slayer-masters.tsv`: Turael 2931,3536; Spria 3091,3267; Mazchna 3511,3509; Vannaka 3147,9914; Chaeldar 2444,4431; Konar 1309,3785; Nieve 2432,3423; Duradel 2869,2982 level 1). The pre-quest npcs are spawned (Turael, Mazchna, Duradel). | M, L |
| SL3 | A master can assign a task when the player's combat level reaches the master's (Mazchna 20, Vannaka 40, Chaeldar 70, Konar 75, Nieve 85, Duradel 100 and Slayer 50; Turael, Spria and Krystilia none) and, for Krystilia, Slayer is above 1. A task row is eligible when the player's base levels meet its `slayer`, `combat` and other skill levels (`skills`) and it has no `other` requirement: a quest or a reward unlock ("completion of Priest in Peril", "Unlocked the 'Seeing red' ability ..."). The server has neither, so such rows are never assigned (OPEN-SL2). | M, S, K |
| SL4 | The task is one weighted pick among the eligible rows of the master (`p.random(sum of weights)`; weights from the table), its amount uniform in `min..max`. The extended range of the "Extend" unlock is not used (OPEN-SL3). | M, W |
| SL5 | When an NPC dies, the player credited with it (`DeathEvent.lootFor`) with a task whose group contains the NPC type gets the monster's `slayxp` Slayer xp and one kill off the task. A Krystilia task counts only while the player stands in the Wilderness (`SharedAttrs.IN_WILDERNESS`). Each NPC type has one group per Wiki `cat`; the group of an npc is the infobox category of its page (so "Cow calf" and "Undead cow" count as cows). | I, K |
| SL6 | The last kill clears the task, adds 1 to the master's streak and pays points: none for streak 1-4, else the master's points for the largest interval (1, 10, 50, 100, 250, 1,000) the streak is a multiple of. Per master (every / 10th / 50th / 100th / 250th / 1,000th): Turael and Spria 0; Mazchna 6 / 30 / 90 / 150 / 210 / 300; Vannaka 8 / 40 / 120 / 200 / 280 / 400; Chaeldar 10 / 50 / 150 / 250 / 350 / 500; Konar 18 / 90 / 270 / 450 / 630 / 900; Nieve 12 / 60 / 180 / 300 / 420 / 600; Duradel 15 / 75 / 225 / 375 / 525 / 750; Krystilia 25 / 125 / 375 / 625 / 875 / 1,250. Turael's and Spria's tasks count toward the four tasks before points start. | P, M |
| SL7 | A player attacks a Slayer monster only with its `slaylvl` in current Slayer (boosts count, OPEN-SL6): the `AttackCheck` event is denied with "You need a Slayer level of N to attack this monster." (`content/combat` publishes it before every attack; no combat file is touched). The level of an npc type is the highest of its groups. | I |
| SL8 | The enchanted gem (obj 4155) "Check" op reports the task and the streak ("You're assigned to kill X; only N more to go."), or that there is none. | G |
| SL9 | "Talk-to" plays the shared greeting "'Ello, and what are you after then?" and a menu: "I need another assignment." (rolls a task), "Have you any rewards for me, or anything to trade?" (the master's reply only, OPEN-SL4) and "Er... Nothing...". With a task, an assignment request answers "You're still hunting X; you have N to go. Come back when you've finished your task." | T |

## Data

`slayer-tasks.tsv`: 326 rows (Turael 24, Spria 25, Mazchna 30, Vannaka 45, Chaeldar 40, Konar 39, Nieve 46, Duradel 43, Krystilia 37 less
the "Boss" rows). `slayer-monsters.tsv`: 2,217 rows for the 1,660 `Infobox Monster` pages. Krystilia's table has no requirement column; her
Slayer levels are those of the six groups her page names (abyssal demons 85, bloodveld 50, dust devils 65, jellies 52, nechryael 80,
spiritual creatures 63), the lowest level above 1 in the group.

## OPEN

- **OPEN-SL1: masters' positions.** The spawn import has no slayer masters except Krystilia. Positions are the infobox map of each
  master's page; Vannaka's is the Edgeville Dungeon, Duradel's is on level 1. If a later spawn import adds them they would be doubled.
  Aya, Achtryn, Kuradal and Steve (the quest replacements) answer the same ops but are not spawned.
- **OPEN-SL2: quests and unlocks.** Rows with a quest or unlock requirement are never assigned (`QUESTS_ASSUMED_COMPLETE = false`).
  This drops, among others, banshees, crawling hands, abyssal demons, basilisks, bloodveld and every task behind a Slayer reward unlock
  (about 180 of the 326 rows have no such requirement, so the rest are never assigned). The quests the masters themselves need (Priest in Peril for Mazchna, Lost City for Chaeldar,
  Shilo Village for Duradel, A Porcine of Interest for Spria) are not checked: they are walking access.
- **OPEN-SL3: extended amounts** ("Extend" unlock), block lists, skips, cancels and the rewards shop ("Look at rewards.") are not
  implemented; neither is the Slayer equipment shop, Turael's task changing (and its streak reset), the Elite diary point bonus of
  Nieve and Konar, boss tasks (the "Boss" rows of Nieve, Duradel and Krystilia) and Mortimer.
- **OPEN-SL4: wording.** The Wiki has no text for the task-complete message, the gem's "Check" reply, the level-requirement refusals or
  the rewards line for masters other than those in tree 40; the texts in `Slayer.kt` are plausible, not sourced.
- **OPEN-SL5: locations.** Konar's tasks count only in her named location, and monsters that are only killable on a task (the
  Wiki "Slayer task" page) are not restricted.
- **OPEN-SL6: gating details.** Equipment that is required to fight (nose peg, earmuffs, spiny helmet, ...) is not modelled; the black mask /
  Slayer helmet bonus is (combat-formulas.md F24); the Slayer level of the gate uses boosts, which the Wiki does not say.
- **OPEN-SL7: weights in the pool.** The weights are the Wiki's current table (summer 2025 sweep-up); a task whose group has no cache npc in
  `slayer-monsters.tsv` is dropped by the importer.
