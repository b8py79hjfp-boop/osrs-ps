# Quests: the framework

Code: `content/quests` (`Quest`, `QuestBook`, `QuestComplete`, `QuestFramework`, `QuestSupport`, `QuestSpawns`, `FarmSupplies`);
data `content/quests/src/main/resources/tp/content/quests/quest-states.tsv`. Per-quest rules: `quest-<name>.md`.

Sources: cache 2757 (db table 0 `Quest`, 219 rows; client scripts 4024, 4029, 2633, 6372, 6373, 5995, 1356; interfaces 119
`questjournal`, 153 `questscroll`, 399 `questlist`; varp `qp` 101; varbits `quests_completed_count` 6347, `quests_total_count` 11877,
`qp_max` 1782), the OSRS Wiki quest and Transcript pages named in each quest file. Decision D-059.

## Rules

| # | Rule | Source |
|---|---|---|
| QU1 | The quest list is built by the client from db table 0. Each row carries the id (col 0), name (col 2), members (col 5), quest points (col 17 `questpoints_override`), the variable value of "not started" (col 18, default 0) and of "finished" (col 19). The variable itself (varp or varbit) is not in the table: client script 4024 returns it by row. `quest-states.tsv` holds that mapping for all 219 rows plus cols 0/17/18/19; `QuestFramework.verify` compares the file with the live rows at world start and fails the boot when they differ (regenerate it with a cache bump). | cache db table 0, script 4024 |
| QU2 | Colour of a row (script 4029): value >= finished is green, value <= not started is red, between is yellow. The server only writes the variable; the client recolours. A quest's variable never moves backwards (`Quest.advance`). | script 4029, 6373 |
| QU3 | Finishing a quest adds its `questpoints_override` to varp `qp` (101) once. The quest list reads varp 101 for "Quest Points: x/y". | script 3310, 1356 |
| QU4 | `QuestCompletion.complete` is the single finishing step: sets the end value, adds QP, gives experience (`Player.addXp`) and items (one `addOrDrop` transaction), refreshes the counters (QU7), sends the chat line "Congratulations, you've completed a quest: <col=0000ff>Name</col>" (the line RuneLite matches), plays `quest_complete_1` and opens the reward scroll. It is idempotent: a finished quest does nothing. Rewards are the quest's cache stat XP (db col 33) and Wiki reward list. | cache col 33, Wiki |
| QU5 | Reward scroll: `questscroll` (153) as the main modal; texts set with IF_SETTEXT: `congratulations` "Congratulations!", `quest_title` "You have completed <name>!", `quest_points` "<n> Quest Point(s)", `award_text` "You are awarded:", `quest_reward1..7` one line each (XP "<n> <Skill> XP", items "<n> <name>", unlock notes); the reward obj on `quest_model` (IF_SETOBJECT). Strings and picture per quest are OPEN-QU5. | interface 153 component names |
| QU6 | "Read journal" is op 2 of the quest list's rows (script 2633; row children are indexed by quest id = col 0, op 1 "View info" is a client toggle). `QuestFramework` opens `questjournal` (119) as the main modal, title = quest name, lines in `qj1..qj199`, and publishes `JournalRequest` on the event bus so the quest's plugin appends its lines. Finished quests end with "QUEST COMPLETE!". Wording is ours (OPEN-QU6). `QuestFramework.openJournal(player, questId)` is the entry point for other panels. | scripts 2633, 6375, interface 119 |
| QU7 | Counters the client shows: varbit 6347 = number of finished quests of the count, 11877 = size of the count, 1782 `qp_max` = sum of their points. The server owns them: set at login and after every completion over all rows of `quest-states.tsv` with `counts` = type quest (col 4 = 0), no parent quest (col 21), released (col 3 = 2), non-empty name. Today that is 186 quests / 336 points; the real totals are OPEN-QU3. | scripts 5995, 1356, 3310 |
| QU8 | Conversations use the dialogue steps (`chatNpc`, `chatPlayer`, `mesbox`, `objbox`, `choice`); item steps use transactions (`QuestSupport`: `give`, `take`, `giveOrDrop`). Quest NPC scripts call `stopInteraction()` first. Flags that are not the quest variable (ingredients handed in, "met Sedridor") are account `saved` values `quests.*`. | docs/mechanics/dialogue.md |
| QU9 | Quest NPCs missing from `npc-spawns.tsv` are spawned by `QuestSpawns` at the Wiki infobox position (cook, Fred, The Thing, Millie, Gillie, Aereck, Romeo, Juliet, Phillipa, Draul, Lawrence, Archmage Sedridor's multi-NPC 5034, and the NPCs of Imp Catcher, Doric's Quest, Witch's Potion, Ernest the Chicken, Goblin Diplomacy, The Knight's Sword, Black Knights' Fortress, Vampyre Slayer and Demon Slayer). Fred's pen sheep are spawned there as the sheared variant; `QuestSpawns` turns them into the unsheared sheep. | Wiki NPC pages, npc-spawns.md SP1-SP2 |
| QU10 | Ingredient sources (`FarmSupplies`): milk (empty bucket on a dairy cow, op "Milk"), grain (op "Pick" on wheat), the windmill (grain into the hopper "Fill", hopper controls "Operate" moves one grain to the flour bin, bin op "Empty" or an empty pot on it gives a pot of flour; the bin holds 30; bin content is varbit `mill_showflour` 5325), shearing (op "Shear" or shears on a sheep gives 1 wool, 100% success, the sheep is sheared and regrows after 50 ticks). | Wiki Dairy cow, Wheat, Windmill, Hopper, Flour bin, Sheep |
| QU11 | `::quest` lists the stage of the implemented quests, `::quest <row> <stage>` sets any quest row's variable (administrators). | - |
| QU12 | The F2P quests Imp Catcher, Doric's Quest, Witch's Potion, Ernest the Chicken, Goblin Diplomacy, The Knight's Sword, Black Knights' Fortress, Vampyre Slayer and Demon Slayer follow the same pattern (`quest-<name>.md`). Locked or secret walls are passed by `stepAcross` (the player is moved to the other side; OPEN-QUA6). Combat-bound steps use plugin-api events only: `AttackCheck` (Delrith needs Silverlight), `DeathEvent` (Count Draynor, Delrith, the rat's tail). | quest-*.md |

## OPEN

- **OPEN-QU1:** the cache gives only "not started" and "finished". Intermediate stage values of each implemented quest are ours
  (documented per quest); a real client does not care, as it only colours by the two bounds.
- **OPEN-QU2:** Cook's Assistant tracks which ingredients were handed in by account flags; the real game uses its own variables.
- **OPEN-QU3:** `quests_total_count` and `qp_max` are computed from the cache rows (186 / 336); the real game's values differ if it
  excludes more rows (Wiki lists fewer quests).
- **OPEN-QU4:** the messages of milking, picking, the windmill and shearing are not published; the wording used is the common one.
- **OPEN-QU5:** the reward scroll's strings, layout and per quest picture are not published; chat colour and jingle per quest unknown.
- **OPEN-QU6:** quest journal wording per stage is not published (no source); journal lines are short Wiki-walkthrough summaries.
  The scroll size of `questjournal` is the interface's own; not seen on a real client.
- **OPEN-QU7:** the teleport to the Rune Essence Mine (Sedridor, Aubury) is not implemented.
- **OPEN-QU8:** the Romeo & Juliet cutscenes (camera, Juliet drinking, the crypt) are not staged; their dialogue plays in place.
- **OPEN-QU9:** the Wiki's antique lamp for Romeo & Juliet (added 3 November 2026) has no obj in cache 2757 and is not given.
- **OPEN-QU10:** the restless ghost's appearance and removal: it appears when the coffin is searched and is removed for good when
  the skull is placed (visibility per player is not modelled; the coffin opens for everyone).
- **OPEN-QU11:** the altar skeleton: spawned beside the player, hunts within 5 tiles, leaves after 200 ticks; a lost skull can be
  searched for again.
- **OPEN-QU12:** the ghost only moves the quest on once the quest has been started.
- **OPEN-QU13:** Father Aereck's "Could you tell me more about Saradomin?" branch after the quest is not transcribed.
- **OPEN-QU14:** picked wheat and cadava bushes do not deplete or respawn (Wiki wheat respawn 20).
- **QU15 (resolved):** dialogue answers that open a shop publish plugin-api `OpenShopRequest` (by NPC type, or by shop name for the Apothecary).
## OPEN (quests added with D-060; numbered OPEN-QUA to stay apart from the parallel QU list)

- **OPEN-QUA1:** Ernest the Chicken's basement lever puzzle (oil can) is not implemented; door/lever locs 137-151 cannot be mapped to the Wiki's labels.
- **OPEN-QUA2:** Doric's anvils are not gated before the quest.
- **OPEN-QUA3:** Hetty has no published post-quest dialogue.
- **OPEN-QUA4:** content/drops attaches the rat's tail row to the dungeon rats for every player; the quest plugin drops it for the regular rats.
- **OPEN-QUA5:** the antique lamps added on 3 November 2026 (Ernest, Black Knights' Fortress, Demon Slayer) are not given.
- **OPEN-QUA6:** locked / secret walls (closet door, sturdy doors, fake walls, meeting door) have no open model and cutscenes are told in chat boxes.
- **OPEN-QUA7:** blue dye has no woad leaf source (Wyson's sale is not in his generic tree).
- **OPEN-QUA8:** what the goblin generals say during the quest is not published beyond the wrong-colour lines.
- **OPEN-QUA9:** Sir Vyvin catching the player at the cupboard is not modelled.
- **OPEN-QUA10:** blurite rocks are not minable (mining.md OPEN-MN6).
- **OPEN-QUA11:** Shield of Arrav is not implemented (Reldo's offer).
- **OPEN-QUA12:** the start warning of The Knight's Sword shows when Mining < 10 or combat < 20 (the Wiki's condition is ambiguous).
- **OPEN-QUA13:** the White Knights' ranks / armoury (Sir Vyvin) are not modelled.
- **OPEN-QUA14:** Black Knights' Fortress refusal below 12 quest points: the wording is ours.
- **OPEN-QUA15:** Sir Amik Varze's post-quest dialogue is not published.
- **OPEN-QUA16:** Count Draynor regenerates without a stake: needs a plugin-api death-prevention hook.
- **OPEN-QUA17:** Count Draynor's lifetime (500 ticks) and hunt range are ours.
- **OPEN-QUA18:** garlic weakening of Count Draynor is not applied.
- **OPEN-QUA19:** Demon Slayer's stone circle entry is polled (no area-entry event); bounds from the dark wizards' spawns.
- **OPEN-QUA20:** Sir Prysin's drain key is handed out by the drain; the sewer's muddy patch loc is unidentified.
- **OPEN-QUA21:** Silverlight repurchase price: 500 (quest page) vs 1,000 (dialogue).
- **OPEN-QUA22:** Wizard Traiborn's non-quest conversation is not transcribed.
