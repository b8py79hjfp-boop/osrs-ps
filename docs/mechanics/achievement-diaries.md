# Achievement diaries (rev 242)

Code: `content/diaries/` (`Diaries.kt` state and API, `DiaryHooks.kt` kill tasks, `DiaryTriggers.kt` / `DiaryTriggerData.kt` the other
triggers, `QuestGate.kt` quest requirements, `DiaryDialogue.kt` taskmaster conversations, `DiaryLamps.kt` Antique lamps,
`AchievementDiaries.kt` plugin: journal clicks, taskmasters, login sync). Data: `diary-areas.tsv`, `diary-tasks.tsv` (492 tasks),
`diary-rewards.tsv`, `diary-triggers.tsv`, `diary-quests.tsv`, `diary-dialogue.tsv`, written by `tools/wiki-import/achievement_diaries.py`
(the trigger table itself is `tools/wiki-import/achievement_diary_triggers.py`) from the Wiki's twelve "<Area> Diary" pages; the number of tasks per tier is
checked by the importer against the client (script 4073).

Sources:
- AD: "Achievement Diary", https://oldschool.runescape.wiki/w/Achievement_Diary (fetched 2026-11-07)
- ADP: the twelve pages "<Area> Diary", e.g. https://oldschool.runescape.wiki/w/Ardougne_Diary (fetched 2026-11-07): task tables
  (`data-diary-name` / `data-diary-tier`), requirements, Rewards lists, taskmaster in the infobox
- CS: client scripts of cache 2757 (rs2asm): 56 (area status 0/1/2), 709 + 2197 (the diary list rows, op "Open <area> Journal"),
  3310 / 3971 / 3980 (summary "Achievement Diaries: X/Y"), 3806 / 3807 / 3808 / 3809 / 8948 (experience interface, lamp skill
  choice), 4072 / 4073 (tasks done / tasks in a tier per area), 2200 / 4134 (counts and claimed flags), enum 595 (area order),
  enum 681 / 81 (interface 240 component to stat to bit)
- DECISIONS D-058

Area order is cache enum 595: 0 Karamja, 1 Ardougne, 2 Falador, 3 Fremennik, 4 Kandarin, 5 Desert, 6 Lumbridge & Draynor,
7 Morytania, 8 Varrock, 9 Wilderness, 10 Western Provinces, 11 Kourend & Kebos. Tiers 0..3 = easy, medium, hard, elite.

## Rules

| # | Rule | Source |
|---|---|---|
| AD1 | Twelve diaries, four tiers each (492 tasks in all: tier sizes per area are those of CS 4073). Tasks can be done in any order and across tiers. | AD; CS 4073 |
| AD2 | A task needs its listed skill levels (boostable ones may use the current boosted level, the others the base level) and quests. The diary re-checks the skills when it is completed, and the quests whose content exists (the 22 quests of content/quests; `diary-quests.tsv`) against their cache variable: "complete" = the quest's end state, "started" and "partial" = past the unstarted state; other quests are not checked (OPEN-AD2, OPEN-AD14). Triggers check the requirements before they record a step, so a player who lacks them keeps no progress. | ADP ("Unless stated otherwise, temporary skill boosts can be used") |
| AD3 | State: a bit per task in saved `diary.<area>.<tier>`. The client reads only counts and flags, mirrored at login and on every change: tasks done per tier (`<area>_easy_count` ... , CS 4072/2200), tier complete (`<area>_diary_<tier>_complete`, CS 56), reward claimed (`<area>_<tier>_reward`, CS 2200/4134), started (`started_<area>_diary`, CS 56) and, for the Wilderness, varbit 4470 "any tier complete" (CS 99/2520). The summary row "Achievement Diaries: X/Y" and the list percentages come from those. | CS 56, 2200, 3971, 3980, 4072, 4134 |
| AD4 | A tier is complete when all its tasks are done: the tier-complete varbit is set and the player is told to speak to the taskmaster. Karamja's easy/medium/hard fields are two bits wide and script 56 compares them with 2, so they are set to 2 (OPEN-AD2). | CS 56; chat wording OPEN-AD8 |
| AD5 | Rewards: from the taskmaster (Talk-to) when the tier is complete: the tier item and an Antique lamp, given in one item transaction (nothing is given and nothing marked without room for both). A tier can be claimed only when every tier before it is complete ("claiming the rewards for a tier requires all preceding tiers of that diary to be completed"). | AD; ADP Rewards; Two-pints transcript |
| AD6 | "Can I have another <item>?" gives the highest claimed tier's item again (no charge) when the player holds none in backpack, worn items or bank. | Two-pints transcript ("Can I have another cloak?" / "Two pints gives you another cloak."); OPEN-AD9 |
| AD7 | Kill tasks complete when the player the loot is made for (else the killer) kills an NPC of the named type; multi-boss tasks keep a bit per boss in saved `diary.part.<task id>`. "In the Wilderness" tasks also need the player in the Wilderness. | ADP task texts; `DeathEvent.lootFor` as in the collection log |
| AD8 | The side journal's Tasks tab (interface 259) is drawn by the client from the varbits; its 12 rows (op 1 "Open <area> Journal", child index = area) open the area's task list: the quest-journal window with the area as title, a heading per tier with the count and each task red (open) or green (done). `open(player)` shows the Tasks tab for the summary panel's row. | CS 709 / 2197; `JournalTab`; OPEN-AD11 |
| AD9 | Antique lamps: easy 2,500 xp (skill level 30+), medium 7,500 (40+), hard 15,000 (50+), elite 50,000 (70+). Karamja's own lamps: 1,000 (any level), 5,000 (30+), 10,000 (40+), elite 50,000 (70+). "Rub" opens interface 240 with the minimum level (varp `if1`) and the skill mask (varp `if2`); picking a skill (levels below the minimum are refused with the client's own text) and Confirm exchange the lamp for the experience. | ADP Rewards; CS 3806, 3807, 3809, 8948 |

## Tasks

State per task is in the appendix. 127 of 492 are hooked: 39 kill tasks (observed through `DeathEvent`) and 88 through the trigger table
(`diary-triggers.tsv`, below); 365 stay OPEN-AD4 with the missing piece named in the appendix.
`Diaries.complete(player, TaskId.of(area, tier, n))` is the one call each trigger ends in.

Kill hooks match the NPC's cache name ignoring case and punctuation. They do not check weapons or equipment ("with a Slayer helmet",
"with Keris", "using the Wolfbane dagger"; those tasks are not hooked) nor places, except the Wilderness flag. The names used are
unique to the task's place in the cache (jogre, deathwing, swordchick, mogre, Giant Mole, brine rat, Dagannoth Kings, God Wars
bosses, ...); a few also exist elsewhere (OPEN-AD4: vulture, terrorbird, wyrm, lizardman, ghoul, banshee).

## Triggers

`diary-triggers.tsv` has one row per hooked task: `area tier index label parts`; `label` is a word of the task text that the importer and
the server check. A task completes when all its parts have happened (a bit per part in the saved `diary.part.<task id>`); the skill
and quest requirements are checked at every step. A part is one of:

| Kind | Observes | Details |
|---|---|---|
| `item` | `ItemsCreated` (net gain of a committed item transaction) | cache name(s) or `*` (any), `reason` prefix of the transaction (`runecraft`, `mining`, `woodcutting`, `herblore`, `fletching`, `crafting`, `cooking`, `tiara`, `sawmill`, `lunar batch`, `charge orb`, `bones to fruit`, `stall`, `shop buy`, `pickpocket`, `farming-harvest`, `hunter`), `min` = one event must bring that many ("simultaneously"), `count` = total over several events |
| `kill` | `DeathEvent` of an NPC | cache name(s), optional tile box of the NPC, `count` |
| `npc`, `loc` | `NpcInteraction`, `LocInteraction` (published when an op script of content starts) | cache names or ids, op number or text |
| `xp` | `XpGained` | stat and the exact experience (a spell's or action's Wiki experience) |
| `cast` | `XpGained` of a teleport spell, then arrival | the spell's Wiki experience, then the player within 2 tiles of the tile content/magic teleports to, within 20 ticks (OPEN-AD15) |
| `area` | a soft timer on every player, every 3 ticks | tile boxes; only the areas of tasks not yet done are looked at |
| `seq` | ordered `LocInteraction`s | an agility lap: the course's obstacle locs in order (a step may name alternative locs) |

Filters on any part: `in` (tile boxes of the player, or of the NPC for kills), `wild` (the player is in the Wilderness), `worn` (items worn),
`target` (the player interacted with that NPC in the last 8 ticks: a pickpocket's loot belongs to the NPC). Boxes are generous
rectangles around a place; where the Wiki names a place without tiles the box is derived from cache loc positions or NPC spawns
(commented in the table) and otherwise approximate (OPEN-AD14).

Hooked by group: 12 teleport spells, 10 runecrafting, 4 Slayer Master assignments, 7 kills with a place, 5 pickpockets, 3 shops, 2 stalls,
4 agility laps, 4 mining, 4 woodcutting, 5 chop-and-burn, 13 production (herblore, fletching, crafting, cooking, tiara, sawmill, Lunar
planks), 4 farming harvests, 3 hunted birds, 3 spell products, 5 places entered.

## Storage

Saved values: `diary.<area>.<tier>` (bitmask, bit n-1 = Wiki task n), `diary.claimed.<area>` (bitmask of claimed tiers),
`diary.part.<task id>` (bitmask of bosses killed for a multi-boss task). Varbits are derived from them at login. D-058.

## Perks of the rewards

Out of scope here: every perk the Wiki lists under a tier's Rewards besides the item and the lamp (teleports and charges of the tier
items, shop discounts, double ore and bars, daily supplies, noted drops, increased rates, ...). Each needs the content it changes; the
Wiki text of every perk is kept in `diary-rewards.tsv` (column `perks`). OPEN-AD5.

## OPEN

- **OPEN-AD1** Which skills a lamp offers: all 24 stats with a bit in the mask except Sailing (client script 8948 refuses it). The
  client applies further per-skill checks itself (members-only skills on free worlds, Construction without a house, Slayer and
  Herblore quest gates, script 3809); the server does not repeat them.
- **OPEN-AD2** Quest requirements (`quests` column) are checked only for the 22 quests whose content exists (`diary-quests.tsv`); the
  others cannot be started yet. Quest-point requirements are not counted. Karamja's tier-complete fields (2 bits) are set to 2 because script 56 compares them with 2; whether the real server sets 1 on task
  completion and 2 on claiming is unknown.
- **OPEN-AD3** The per-task bits the real game keeps in varps 1176..1199, 1000..1002, 1200 are not mirrored: no client script reads
  them (checked in the exported scripts) and the bit layout of the 11 non-Karamja diaries is not in the cache.
- **OPEN-AD4** The other 365 tasks (appendix, with the missing piece), and the place/equipment conditions of the hooked kill tasks. Missing
  content: fishing spots are not spawned (21 tasks); the rooftop courses other than Draynor, Al Kharid, Varrock and the Gnome
  Stronghold, and the shortcuts (28); boats, fairy rings, spirit trees, gliders, balloons, canoes, carpets, levers (39); minigames and
  activities (25); altars and prayer restoring (10); hunter creatures missing from hunter.tsv (11); chests and unsourced stalls (11);
  teleport items and the essence-mine teleport (OPEN-QU7) (13); farming patches and crops outside farming-crops.tsv (17). Missing
  plugin-api events (proposals): see OPEN-AD16.
- **OPEN-AD5** Reward perks (above), including unlimited and daily teleports of the tier items.
- **OPEN-AD6** The "started" flag is set on the first completed task; when the real game sets it (probably when first speaking to the
  taskmaster) is unknown.
- **OPEN-AD7** The kill credit goes to the player the loot is made for (most damage), as in the collection log; the Wiki states no rule.
- **OPEN-AD8** Chat wording of a completed task and of a completed tier, and the taskmaster's claim lines, are not in the cache or on
  the Wiki; they follow the usual in-game phrasing from memory.
- **OPEN-AD9** "Can I have another <item>?" has no cost and no cooldown here; the real server's rule is unknown.
- **OPEN-AD10** The row layer of the diary list: script 709 fills a scroll layer of interface 259 that the decoded component fields
  do not identify; all of `area_task`'s layers answer op 1 with the child index as the area. Verify with a real client.
- **OPEN-AD11** The area task list is drawn in the quest-journal window (interface 119), the server-drawn text window the cache
  offers; the real diary journal's title, colours and layout are not in the cache. Lines wrap at 70 characters.
- **OPEN-AD12** Resolved: Talk-to on a taskmaster plays the Wiki transcript (`Transcript:<NPC>`, `diary-dialogue.tsv`): the standard
  conversation, or, with a tier to claim, the "<Tier> tasks completed" conversation, which hands the reward over at the point the
  transcript says (the reward and lamp in one item transaction; without room the taskmaster says so and nothing is given). "Can I have
  another ..." is offered once a tier was claimed. Karamja's transcript (Pirate Jackie the Fruit) has no claim section: it keeps
  the claim menu. The inventory-space branches and the "earlier tiers not done" branches of the Wiki are replaced by the server's
  checks; Mikasi's mind-rune claim variants are not modelled.
- **OPEN-AD13** The experience interface's selection: clicking a skill is assumed to send an IF_BUTTON for that component, which
  records the choice; the client's highlight is its own script. Verify with a real client.

- **OPEN-AD14** Approximations of the hooks: a "partial completion" quest requirement is checked as "started" (the stage it needs is
  not in the Wiki table); "from scratch" tasks (tiara in Rellekka, Yew longbow, super antipoison, rune crossbow) check the last product
  and, where listed in the table, the first, not the steps between; the item parts of a place do not check how the item was made
  beyond the transaction reason; the boxes of places the Wiki gives no tiles for are generous approximations.
- **OPEN-AD15** A teleport spell is detected as its Wiki experience (`XpGained` in Magic) followed by the player standing at the
  spell's destination within 20 ticks: the cast event is not published by content/magic and plugin-api has none.
- **OPEN-AD16** Proposals for plugin-api events that would hook large task groups: `TeleportEvent(player, source, from, to)` (spells,
  tablets, jewellery; ~20 tasks), `AltarEvent(player, loc, action)` (prayer restoring, offering; ~10), `EmoteEvent(player, emote)` (cape
  emotes; ~3), `EquipEvent(player, obj, slot)` (equip tasks; ~10), `ShopEvent(player, npc, kind)` (browse / buy / sell, with the NPC),
  `TravelEvent(player, kind, from, to)` (boats, balloons, gliders, rings, trees, carpets; ~35), `LapEvent(player, course)` (all rooftop
  and agility courses), `MinigameEvent(player, minigame, result)`, `SpellCast(caster, spell, target)` (every spell, not only combat
  ones), and an `ItemsCreated.source` (the NPC or loc a catch, pickpocket or harvest came from).

## Task states

Every task of the twelve diaries (Wiki wording). `hooked (kill)` completes through `DeathEvent`, `hooked (<kinds>)` through the trigger
table; `OPEN-AD4: ...` has no trigger yet. The reason of an OPEN row is a keyword classification of the task text (rules in the
generator used for this table), not an audit of the code: it names the likeliest missing piece.

**Karamja Easy** (task id = area*1000 + tier*100 + n, area 0, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Pick 5 bananas from the plantation located east of the volcano. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 2 | Use the rope swing to travel to the Moss Giant Island north-west of Karamja. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 3 | Mine some gold from the rocks on the north-west peninsula of Karamja. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 4 | Travel to Port Sarim via the dock, east of Musa Point. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 5 | Travel to Ardougne via the port near Brimhaven. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 6 | Explore Cairn Island to the west of Karamja. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 7 | Use the Fishing spots north of the banana plantation. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 8 | Collect 5 seaweed from anywhere on Karamja. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 9 | Attempt the TzHaar Fight Pits or Fight Cave. | OPEN-AD4: minigame or activity not in the repository |
| 10 | Kill a jogre in the Pothole dungeon. | hooked (kill) |

**Karamja Medium** (task id = area*1000 + tier*100 + n, area 0, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Claim a ticket from the Agility Arena in Brimhaven. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 2 | Discover hidden wall in the dungeon below the volcano. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 3 | Visit the Isle of Crandor via the dungeon below the volcano. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Use Vigroy and Hajedy's cart service. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 5 | Earn 100% favour in the village of Tai Bwo Wannai. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 6 | Cook a spider on a stick. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 7 | Charter the Lady of the Waves from Cairn Isle to Port Khazard. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 8 | Cut a log from a teak tree. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 9 | Cut a log from a mahogany tree. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 10 | Catch a karambwan. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 11 | Exchange gems for a machete. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 12 | Use the gnome glider to travel to Karamja. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 13 | Grow a healthy fruit tree in the patch near Brimhaven. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 14 | Trap a horned graahk. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 15 | Chop the vines to gain deeper access to Brimhaven Dungeon. | OPEN-AD4: woodcutting: the tree or obstacle is not implemented |
| 16 | Cross the lava using the stepping stones within Brimhaven Dungeon. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 17 | Climb the stairs within Brimhaven Dungeon. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 18 | Charter a ship from the shipyard in the far east of Karamja. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 19 | Mine a red topaz from a gem rock. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |

**Karamja Hard** (task id = area*1000 + tier*100 + n, area 0, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Become the Champion of the Fight Pits. | OPEN-AD4: minigame or activity not in the repository |
| 2 | Kill a Ket-Zek in the Fight Caves. | hooked (kill) |
| 3 | Eat an oomlie wrap. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Craft some nature runes from Essence. | hooked (item) |
| 5 | Cook a karambwan thoroughly. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 6 | Kill a deathwing in the dungeon under the Kharazi Jungle. | hooked (kill) |
| 7 | Use the crossbow shortcut south of the volcano. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 8 | Collect 5 palm leaves. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 9 | Be assigned a Slayer task by the Slayer Master in Shilo Village. | hooked (npc) |
| 10 | Kill a metal dragon in Brimhaven Dungeon. | hooked (kill) |

**Karamja Elite** (task id = area*1000 + tier*100 + n, area 0, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Craft 56 Nature runes simultaneously from Essence without the use of Extracts. | hooked (item) |
| 2 | Equip a Fire Cape or Infernal Cape in Mor Ul Rek. | OPEN-AD4: equipment or emote event not observable |
| 3 | Check the health of a palm tree in Brimhaven. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 4 | Create an antivenom potion whilst standing in the horse shoe mine. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 5 | Check the health of your Calquat tree patch. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |

**Ardougne Easy** (task id = area*1000 + tier*100 + n, area 1, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Have Wizard Cromperty teleport you to the Rune Essence mine. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 2 | Steal a cake from the Ardougne market stalls. | hooked (item) |
| 3 | Sell Silk to Silk trader in Ardougne for 60 coins each. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 4 | Use the altar in East Ardougne's church. | OPEN-AD4: no observable event (dig / generic item use) |
| 5 | Go out fishing on the Fishing Trawler | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 6 | Enter the Combat Training Camp north of W. Ardougne. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 7 | Have Tindel Marchant identify a Rusted Sword for you. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 8 | Use the Ardougne Lever to teleport to the Wilderness. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 9 | View Aleck's Hunter Emporium in Yanille. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 10 | Check what pets you have Insured with Probita in Ardougne. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |

**Ardougne Medium** (task id = area*1000 + tier*100 + n, area 1, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Enter the Unicorn pen in Ardougne zoo using Fairy rings. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 2 | Grapple over Yanille's south wall. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Harvest some strawberries from the Ardougne farming patch. | hooked (item) |
| 4 | Cast the Ardougne Teleport spell. | hooked (cast) |
| 5 | Travel to Castlewars by Hot Air Balloon. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 6 | Claim buckets of sand from Bert in Yanille. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 7 | Catch any fish on the Fishing Platform. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 8 | Pickpocket the master farmer north of Ardougne. | hooked (item) |
| 9 | Collect some Nightshade from the Skavid Caves. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 10 | Kill a swordchick in the Tower of Life. | hooked (kill) |
| 11 | Equip Iban's upgraded staff or upgrade an Iban staff. | OPEN-AD4: equipment or emote event not observable |
| 12 | Visit the Island East of the Necromancer's Tower. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |

**Ardougne Hard** (task id = area*1000 + tier*100 + n, area 1, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Recharge some Jewellery at the Totem in the Legends Guild. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 2 | Enter the Magic Guild. | hooked (area) |
| 3 | Steal from a chest in Ardougne Castle. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 4 | Have a zookeeper put you in Ardougne Zoo's monkey cage. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 5 | Teleport to the Watchtower. | hooked (cast) |
| 6 | Catch a Red Salamander. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 7 | Check the health of a Palm tree near tree gnome village. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 8 | Pick some Poison Ivy berries from the patch south of Ardougne. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 9 | Smith a Mithril platebody near Ardougne. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 10 | Enter your POH from Yanille. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 11 | Smith a Dragon sq shield in West Ardougne. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 12 | Craft some Death runes from Essence. | hooked (item) |

**Ardougne Elite** (task id = area*1000 + tier*100 + n, area 1, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Catch a Manta ray in the Fishing Trawler and cook it in Port Khazard. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 2 | Picklock the door to the basement of Yanille Agility Dungeon. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 3 | Pickpocket a Hero. | hooked (item) |
| 4 | Make a rune crossbow yourself from scratch within Witchaven or Yanille. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 5 | Imbue a Salve amulet at Nightmare Zone, or equip a Salve amulet that was imbued there. | OPEN-AD4: minigame or activity not in the repository |
| 6 | Pick some Torstol from the patch north of Ardougne. | hooked (item) |
| 7 | Complete a lap of Ardougne's rooftop agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 8 | Cast Ice Barrage on another player within Castle Wars. | OPEN-AD4: magic: the spell has no observable cast event or its place is not sourced |

**Falador Easy** (task id = area*1000 + tier*100 + n, area 2, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Find out what your family crest is from Sir Renitee | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 2 | Climb over the western Falador wall. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Browse Sarah's farm shop. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 4 | Get a Haircut or a Shave from the Falador Hairdresser. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 5 | Fill a bucket from the pump north of Falador west bank. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 6 | Kill a duck in Falador Park. | hooked (kill) |
| 7 | Make a mind tiara. | hooked (item) |
| 8 | Take the boat to Entrana. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 9 | Repair a broken strut in the Motherlode Mine. | OPEN-AD4: minigame or activity not in the repository |
| 10 | Claim a security book from the Security guard at Port Sarim jail. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 11 | Smith some Blurite Limbs on Doric's anvil. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |

**Falador Medium** (task id = area*1000 + tier*100 + n, area 2, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Light a Bullseye lantern at the Chemist's in Rimmington. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 2 | Telegrab some Wine of Zamorak at the Chaos Temple by the Wilderness. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 3 | Unlock the Crystal chest in Taverley. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Place a Scarecrow in the Falador farm flower patch. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 5 | Kill a Mogre at Mudskipper Point. | hooked (kill) |
| 6 | Visit the Port Sarim Rat Pits. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 7 | Grapple up and then jump off the north Falador wall. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 8 | Pickpocket a Falador guard. | hooked (item) |
| 9 | Pray at the Altar of Guthix in Taverley whilst wearing full Initiate. | OPEN-AD4: equipment or emote event not observable |
| 10 | Mine some Gold ore at the Crafting Guild. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 11 | Squeeze through the crevice in the Dwarven mines. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 12 | Chop and burn some Willow logs in Taverley. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 13 | Craft a fruit basket on the Falador Farm loom. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 14 | Teleport to Falador. | hooked (cast) |

**Falador Hard** (task id = area*1000 + tier*100 + n, area 2, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Craft 140 Mind runes simultaneously from Essence without the use of Extracts. | hooked (item) |
| 2 | Change your family crest to the Saradomin symbol. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 3 | Kill the Giant Mole beneath Falador Park. | hooked (kill) |
| 4 | Kill a Skeletal Wyvern in the Asgarnia Ice Dungeon. | hooked (kill) |
| 5 | Complete a lap of the Falador rooftop agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 6 | Enter the mining guild while wearing a Prospector helmet. | OPEN-AD4: equipment or emote event not observable |
| 7 | Kill the Blue Dragon under the Heroes' Guild. | hooked (kill) |
| 8 | Crack a wall safe within Rogues' Den. | OPEN-AD4: minigame or activity not in the repository |
| 9 | Recharge your prayer in the Port Sarim church while wearing full Proselyte. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 10 | Enter the Warriors' Guild. | hooked (area) |
| 11 | Equip a dwarven helmet within the dwarven mines. | OPEN-AD4: equipment or emote event not observable |

**Falador Elite** (task id = area*1000 + tier*100 + n, area 2, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Craft 252 Air Runes simultaneously from Essence without the use of Extracts. | hooked (item) |
| 2 | Purchase a White 2h Sword from Sir Vyvin. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 3 | Find at least 3 magic roots at once when digging up your magic tree in Falador. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Perform a skillcape or quest cape emote at the top of Falador Castle. | OPEN-AD4: equipment or emote event not observable |
| 5 | Jump over the strange floor in Taverley dungeon. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 6 | Mix a Saradomin brew in Falador east bank. | hooked (item) |

**Fremennik Easy** (task id = area*1000 + tier*100 + n, area 3, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Catch a Cerulean twitch. | hooked (item) |
| 2 | Change your boots at Yrsa's Shoe Store. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 3 | Kill 5 Rock crabs. | hooked (kill) |
| 4 | Craft a tiara from scratch in Rellekka. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 5 | Browse the Stonemasons shop. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 6 | Collect 5 Snape grass on Waterbirth Island. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 7 | Steal from the Keldagrim crafting or baker's stall. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 8 | Fill a bucket with water at the Rellekka well. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 9 | Enter the Troll Stronghold. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 10 | Chop and burn some oak logs in the Fremennik Province. | hooked (item, xp) |

**Fremennik Medium** (task id = area*1000 + tier*100 + n, area 3, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Slay a Brine rat. | hooked (kill) |
| 2 | Travel to the Snowy Hunter Area via Eagle. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 3 | Mine some coal in Rellekka. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 4 | Steal from the Rellekka Fish stalls. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 5 | Travel to Miscellania by Fairy ring. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 6 | Catch a Snowy knight. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 7 | Pick up your Pet Rock from your POH Menagerie. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 8 | Visit the Lighthouse from Waterbirth island. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 9 | Mine some gold at the Arzinian mine. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |

**Fremennik Hard** (task id = area*1000 + tier*100 + n, area 3, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Teleport to Trollheim. | hooked (cast) |
| 2 | Catch a Sabre-toothed Kyatt. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 3 | Mix a super defence potion in the Fremennik Province. | hooked (item) |
| 4 | Steal from the Keldagrim Gem Stall. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 5 | Craft a Neitiznot shield on Neitiznot. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 6 | Mine 5 Adamantite ores on Jatizso. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 7 | Obtain 100% support from your kingdom subjects. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 8 | Teleport to Waterbirth Island. | hooked (cast) |
| 9 | Obtain the Blast Furnace Foreman's permission to use the Blast Furnace for free. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |

**Fremennik Elite** (task id = area*1000 + tier*100 + n, area 3, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Kill each of the Dagannoth Kings. | hooked (kill) |
| 2 | Craft 56 astral runes simultaneously from Essence without the use of Extracts. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 3 | Create a dragonstone amulet in the Neitiznot furnace. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 4 | Complete a lap of the Rellekka agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 5 | Kill the generals of Armadyl, Bandos, Saradomin and Zamorak in the God Wars Dungeon. | hooked (kill) |
| 6 | Slay a Spiritual mage within the Godwars Dungeon. | OPEN-AD4: combat: weapon or equipment condition, or the NPC is not spawned |

**Kandarin Easy** (task id = area*1000 + tier*100 + n, area 4, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Catch a Mackerel at Catherby. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 2 | Buy a candle from the Chandler in Catherby. | hooked (item) |
| 3 | Collect five Flax from the Seers' flax fields. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Play the Organ in Seers' church. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 5 | Plant some Jute seeds in the patch north of McGrubor's Wood. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 6 | Have Galahad make you a cup of tea. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 7 | Defeat one of each elemental in the workshop. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 8 | Get a pet fish from Harry in Catherby. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 9 | Buy a Stew from the Seers' pub. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 10 | Speak to Sherlock. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 11 | Cross the Coal truck log shortcut. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |

**Kandarin Medium** (task id = area*1000 + tier*100 + n, area 4, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Complete a lap of the Barbarian agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 2 | Create a Super Antipoison potion from scratch in the Seers/Catherby Area. | hooked (item) |
| 3 | Enter the Ranging guild. | hooked (area) |
| 4 | Use the grapple shortcut to get from the water obelisk to Catherby shore. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 5 | Catch and cook a Bass in Catherby. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 6 | Teleport to Camelot. | hooked (cast) |
| 7 | String a Maple shortbow in Seers' Village bank. | hooked (item) |
| 8 | Pick some Limpwurt root from the farming patch in Catherby. | hooked (item) |
| 9 | Create a Mind helmet. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 10 | Kill a Fire Giant inside Baxtorian Waterfall. | hooked (kill) |
| 11 | Complete a wave of Barbarian Assault. | OPEN-AD4: minigame or activity not in the repository |
| 12 | Steal from the chest in Hemenster. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 13 | Travel to McGrubor's Wood by Fairy Ring. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 14 | Mine some coal near the coal trucks. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |

**Kandarin Hard** (task id = area*1000 + tier*100 + n, area 4, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Catch a Leaping Sturgeon. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 2 | Complete a lap of the Seers' Village agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Create a Yew Longbow from scratch around Seers' Village. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 4 | Enter the Seers' Village courthouse with piety turned on. | OPEN-AD4: equipment or emote event not observable |
| 5 | Charge a Water Orb. | OPEN-AD4: magic: the spell has no observable cast event or its place is not sourced |
| 6 | Burn some Maple logs with a bow in Seers' Village. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 7 | Kill a Shadow Hound in the Shadow dungeon. | hooked (kill) |
| 8 | Kill a Mithril Dragon. | hooked (kill) |
| 9 | Purchase and equip a granite body from Barbarian Assault. | OPEN-AD4: minigame or activity not in the repository |
| 10 | Have the Seers' estate agent decorate your house with Fancy Stone. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 11 | Smith an Adamant spear at Otto's Grotto. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |

**Kandarin Elite** (task id = area*1000 + tier*100 + n, area 4, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Read the Blackboard at Barbarian Assault after reaching level 5 in every role. | OPEN-AD4: minigame or activity not in the repository |
| 2 | Pick some Dwarf weed from the herb patch at Catherby. | hooked (item) |
| 3 | Fish and Cook 5 Sharks in Catherby using the Cooking gauntlets. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 4 | Mix a Stamina Mix on top of the Seers' Village bank. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 5 | Smith a Rune Hasta at Otto's Grotto. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 6 | Construct a Pyre ship from Magic Logs. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 7 | Teleport to Catherby. | hooked (cast) |

**Desert Easy** (task id = area*1000 + tier*100 + n, area 5, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Catch a Golden Warbler. | hooked (item) |
| 2 | Mine 5 clay in the north-eastern desert. | OPEN-AD4: mining: the ore or rock is not in MiningData, or its place is not sourced |
| 3 | Enter the Kalphite Hive. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Enter the Desert with a set of Desert robes equipped. | OPEN-AD4: equipment or emote event not observable |
| 5 | Kill a Vulture | hooked (kill) |
| 6 | Have the Nardah Herbalist clean a Herb for you. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 7 | Collect 5 Potato Cactus from the Kalphite Hive. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 8 | Sell some artefacts to Simon Templeton. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 9 | Open the Sarcophagus in the first room of Pyramid Plunder | OPEN-AD4: minigame or activity not in the repository |
| 10 | Cut a desert cactus open to fill a waterskin. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 11 | Travel from the Shantay Pass to Pollnivneach by Magic Carpet. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |

**Desert Medium** (task id = area*1000 + tier*100 + n, area 5, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Climb to the summit of the Agility Pyramid. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 2 | Slay a desert lizard. | OPEN-AD4: combat: weapon or equipment condition, or the NPC is not spawned |
| 3 | Catch an Orange Salamander. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 4 | Steal a feather from the Desert Phoenix. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 5 | Travel to Uzer via Magic Carpet. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 6 | Travel to the Desert via Eagle. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 7 | Pray at the Elidinis statuette in Nardah | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 8 | Create a combat potion in the desert. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 9 | Teleport to Enakhra's Temple with the Camulet. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 10 | Visit the Genie. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 11 | Teleport to Pollnivneach with a redirected teleport to house tablet. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 12 | Chop some Teak Logs near Uzer. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |

**Desert Hard** (task id = area*1000 + tier*100 + n, area 5, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Knock out and pickpocket a Menaphite Thug. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 2 | Mine some Granite. | OPEN-AD4: mining: the ore or rock is not in MiningData, or its place is not sourced |
| 3 | Refill your waterskins in the Desert using Lunar magic. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Kill the Kalphite Queen. | hooked (kill) |
| 5 | Complete a lap of the Pollnivneach agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 6 | Slay a Dust Devil in the desert cave with a Slayer helmet equipped. | OPEN-AD4: equipment or emote event not observable |
| 7 | Activate Ancient Magicks at the altar in the Jaldraocht Pyramid. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 8 | Defeat a Locust Rider with Keris. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 9 | Burn some yew logs on the Nardah Mayor's balcony. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 10 | Create a Mithril Platebody in Nardah. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |

**Desert Elite** (task id = area*1000 + tier*100 + n, area 5, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Bake a wild pie at the Nardah Clay Oven. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 2 | Cast Ice Barrage against a foe in the Desert | OPEN-AD4: magic: the spell has no observable cast event or its place is not sourced |
| 3 | Fletch some Dragon darts at the Bedabin Camp. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 4 | Speak to the KQ head in your POH. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 5 | Steal from the Grand Gold Chest in the final room of Pyramid Plunder. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 6 | Restore at least 85 Prayer points when praying at the Altar in Sophanem. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |

**Lumbridge & Draynor Easy** (task id = area*1000 + tier*100 + n, area 6, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Complete a lap of the Draynor Village agility course. | hooked (seq) |
| 2 | Slay a Cave bug beneath Lumbridge Swamp. | hooked (kill) |
| 3 | Have Sedridor teleport you to the Rune essence mine. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 4 | Craft some water runes from Essence. | hooked (item) |
| 5 | Learn your age from Hans in Lumbridge. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 6 | Pickpocket a man or woman in Lumbridge. | hooked (item) |
| 7 | Chop and burn some oak logs in Lumbridge. | hooked (item, xp) |
| 8 | Kill a Zombie in Draynor Sewers. | OPEN-AD4: combat: weapon or equipment condition, or the NPC is not spawned |
| 9 | Catch some Anchovies in Al Kharid. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 10 | Bake some Bread on the Lumbridge kitchen range. | hooked (item) |
| 11 | Mine some Iron ore at the Al Kharid mine. | hooked (item) |
| 12 | Enter the H.A.M. Hideout. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |

**Lumbridge & Draynor Medium** (task id = area*1000 + tier*100 + n, area 6, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Complete a lap of the Al Kharid agility course. | hooked (seq) |
| 2 | Grapple across the River Lum. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Purchase an upgraded device from Ava. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 4 | Travel to the Wizards' Tower by Fairy ring. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 5 | Cast the Teleport to Lumbridge spell. | hooked (cast) |
| 6 | Catch some Salmon in Lumbridge. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 7 | Craft a coif in the Lumbridge cow pen. | hooked (item) |
| 8 | Chop some willow logs in Draynor Village. | hooked (item) |
| 9 | Pickpocket Martin the Master Gardener. | OPEN-AD4: thieving: the NPC is not in pickpocket.tsv or is not spawned |
| 10 | Get a slayer task from Chaeldar. | hooked (npc) |
| 11 | Catch an Essence or Eclectic impling in Puro-Puro. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 12 | Craft some Lava runes at the fire altar in Al Kharid. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |

**Lumbridge & Draynor Hard** (task id = area*1000 + tier*100 + n, area 6, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Cast Bones to Peaches in Al Kharid palace. | hooked (item) |
| 2 | Squeeze past the jutting wall on your way to the cosmic altar. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Craft 56 Cosmic runes simultaneously from Essence without the use of Extracts. | hooked (item) |
| 4 | Travel from Lumbridge to Edgeville on a Waka Canoe. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 5 | Collect at least 100 Tears of Guthix in one visit. | OPEN-AD4: minigame or activity not in the repository |
| 6 | Take the train from Dorgesh-Kaan to Keldagrim. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 7 | Purchase some Barrows gloves from the Lumbridge bank chest. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 8 | Pick some Belladonna from the farming patch at Draynor Manor. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 9 | Light your mining helmet in the Lumbridge castle basement. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 10 | Recharge your prayer at Emir's Arena with Smite activated. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 11 | Craft, string and enchant an Amulet of Power in Lumbridge. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |

**Lumbridge & Draynor Elite** (task id = area*1000 + tier*100 + n, area 6, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Steal from a Dorgesh-Kaan rich chest. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 2 | Grapple across a pylon on the Dorgesh-Kaan Agility Course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Chop some magic logs at the Mage Training Arena. | OPEN-AD4: minigame or activity not in the repository |
| 4 | Smith an Adamant platebody down Draynor sewer. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 5 | Craft 140 or more Water runes simultaneously from Essence without the use of Extracts. | hooked (item) |
| 6 | Perform the Quest cape emote in the Wise Old Man's house. | OPEN-AD4: equipment or emote event not observable |

**Morytania Easy** (task id = area*1000 + tier*100 + n, area 7, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Craft any Snelm from scratch in Morytania. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 2 | Cook a thin Snail on the Port Phasmatys range. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 3 | Get a slayer task from the Slayer Master in Canifis. | hooked (npc) |
| 4 | Kill a Banshee in the Slayer Tower. | hooked (kill) |
| 5 | Have Sbott in Canifis tan something for you. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 6 | Enter Mort Myre Swamp. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 7 | Kill a Ghoul. | hooked (kill) |
| 8 | Place a Scarecrow in the Morytania flower patch. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 9 | Offer some bonemeal at the Ectofuntus. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 10 | Kill a werewolf in its human form using the Wolfbane Dagger. | OPEN-AD4: combat: weapon or equipment condition, or the NPC is not spawned |
| 11 | Restore your prayer points at the nature altar. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |

**Morytania Medium** (task id = area*1000 + tier*100 + n, area 7, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Catch a swamp lizard. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 2 | Complete a lap of the Canifis agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Obtain some Bark from a Hollow tree. | hooked (item) |
| 4 | Travel to Dragontooth Isle. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 5 | Kill a Terror Dog. | hooked (kill) |
| 6 | Complete a game of trouble brewing. | OPEN-AD4: minigame or activity not in the repository |
| 7 | Board the Swampy boat at the Hollows. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 8 | Make a batch of cannonballs at the Port Phasmatys furnace. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 9 | Kill a Fever Spider on Braindeath Island. | hooked (kill) |
| 10 | Use an ectophial to return to Port Phasmatys. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 11 | Mix a Guthix Balance potion while in Morytania. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |

**Morytania Hard** (task id = area*1000 + tier*100 + n, area 7, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Enter the Kharyrll Portal in your POH. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 2 | Climb up the advanced spike chain within Slayer Tower. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 3 | Harvest some Watermelon from the Allotment patch on Harmony Island. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 4 | Chop and burn some mahogany logs on Mos Le'Harmless. | hooked (item, xp) |
| 5 | Complete a temple trek with a hard companion. | OPEN-AD4: minigame or activity not in the repository |
| 6 | Kill a Cave Horror. | hooked (kill) |
| 7 | Harvest some Bittercap Mushrooms from the patch in Canifis. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 8 | Pray at the Altar of Nature with Piety activated. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 9 | Use the shortcut to get to the bridge over the Salve. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 10 | Mine some Mithril ore in the Abandoned Mine. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |

**Morytania Elite** (task id = area*1000 + tier*100 + n, area 7, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Catch a shark in Burgh de Rott with your bare hands. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 2 | Cremate any Shade remains on a Magic or Redwood pyre. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 3 | Fertilize the Morytania herb patch using Lunar Magic. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 4 | Craft a Black dragonhide body in Canifis bank. | hooked (item) |
| 5 | Kill an Abyssal demon in the Slayer Tower. | hooked (kill) |
| 6 | Loot the Barrows chest while wearing any complete barrows set. | OPEN-AD4: equipment or emote event not observable |

**Varrock Easy** (task id = area*1000 + tier*100 + n, area 8, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Browse Thessalia's store. | hooked (npc) |
| 2 | Have Aubury teleport you to the Essence mine. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 3 | Mine some Iron in the south-east mining patch near Varrock. | hooked (item) |
| 4 | Make a normal plank at the Sawmill. | hooked (item) |
| 5 | Enter the second level of the Stronghold of Security. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 6 | Jump over the fence south of Varrock. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 7 | Chop down a dying tree in the Lumber Yard. | hooked (item) |
| 8 | Buy a newspaper. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 9 | Give a dog a bone! | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 10 | Spin a bowl on the pottery wheel and fire it in the oven in Barb Village. | hooked (item) |
| 11 | Speak to Haig Halen after obtaining at least 50 Kudos. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 12 | Craft some Earth runes from Essence. | hooked (item) |
| 13 | Catch some trout in the River Lum at Barbarian Village. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 14 | Steal from the Tea stall in Varrock. | hooked (item) |

**Varrock Medium** (task id = area*1000 + tier*100 + n, area 8, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Have the Apothecary in Varrock make you a strength potion. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 2 | Enter the Champions' Guild. | hooked (area) |
| 3 | Select a colour for your kitten. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 4 | Use the spirit tree north of Varrock. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 5 | Perform the 4 emotes from the Stronghold of Security. | OPEN-AD4: equipment or emote event not observable |
| 6 | Enter the Tolna dungeon after completing A Soul's Bane. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 7 | Teleport to the digsite using a Digsite pendant. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 8 | Cast the teleport to Varrock spell. | hooked (cast) |
| 9 | Get a Slayer task from Vannaka. | hooked (npc) |
| 10 | Make 20 Mahogany Planks in one go. | hooked (item) |
| 11 | Pick a White tree fruit. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 12 | Use the balloon to travel from Varrock. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 13 | Complete a lap of the Varrock Agility course. | hooked (seq) |

**Varrock Hard** (task id = area*1000 + tier*100 + n, area 8, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Trade furs with the Fancy Dress Seller for a spottier cape and equip it. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 2 | Speak to Orlando Smith when you have achieved 153 Kudos. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 3 | Make a Waka canoe near Edgeville. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 4 | Teleport to Paddewwa. | hooked (cast) |
| 5 | Teleport to Barbarian Village with a skull sceptre. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 6 | Chop some yew logs in Varrock and burn them at the top of the Varrock church. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |
| 7 | Have the Varrock estate agent decorate your house with Fancy Stone. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 8 | Collect at least 2 yew roots from the Tree patch in Varrock Palace. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 9 | Pray at the altar in Varrock palace with Smite active. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 10 | Squeeze through the obstacle pipe in Edgeville dungeon. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |

**Varrock Elite** (task id = area*1000 + tier*100 + n, area 8, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Create a super combat potion in Varrock west bank. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 2 | Use Lunar magic to make 20 mahogany planks at the Lumberyard. | hooked (item) |
| 3 | Bake a summer pie in the Cooking Guild. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 4 | Smith and fletch ten rune darts within Varrock. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 5 | Craft 100 or more earth runes simultaneously from Essence without the use of Extracts. | hooked (item) |

**Wilderness Easy** (task id = area*1000 + tier*100 + n, area 9, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Cast Low Alchemy at the Fountain of Rune. | OPEN-AD4: magic: the spell has no observable cast event or its place is not sourced |
| 2 | Enter the Wilderness from the Ardougne or Edgeville lever. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 3 | Pray at the Chaos Altar in the Western Wilderness. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 4 | Enter the Chaos Runecrafting temple. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 5 | Kill a Mammoth in the Wilderness. | hooked (kill) |
| 6 | Kill an Earth Warrior in the Wilderness beneath Edgeville. | hooked (kill) |
| 7 | Restore some prayer points at the demonic ruins. | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 8 | Enter the King Black Dragon's lair. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 9 | Collect 5 Red spiders' eggs from the Wilderness | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 10 | Mine some Iron ore in the Wilderness. | hooked (item) |
| 11 | Have the Mage of Zamorak teleport you to the Abyss. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 12 | Equip any team cape in the Wilderness. | OPEN-AD4: equipment or emote event not observable |

**Wilderness Medium** (task id = area*1000 + tier*100 + n, area 9, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Mine some Mithril ore in the wilderness. | hooked (item) |
| 2 | Chop some yew logs from a fallen Ent. | OPEN-AD4: woodcutting: the tree or obstacle is not implemented |
| 3 | Enter the Wilderness Godwars Dungeon. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 4 | Complete a lap of the Wilderness Agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 5 | Kill a Green Dragon. | hooked (kill) |
| 6 | Kill an Ankou in the Wilderness. | hooked (kill) |
| 7 | Charge an Earth Orb. | hooked (item) |
| 8 | Kill a Bloodveld in the Wilderness Godwars Dungeon. | hooked (kill) |
| 9 | Talk to the Emblem Trader in Edgeville about emblems. (He's just north of the bank) | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 10 | Smith a Golden helmet in the Resource Area. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 11 | Open the Muddy Chest in the lava maze. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |

**Wilderness Hard** (task id = area*1000 + tier*100 + n, area 9, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Cast any of the 3 God spells against another player in the Wilderness. | OPEN-AD4: magic: the spell has no observable cast event or its place is not sourced |
| 2 | Charge an Air Orb. | hooked (item) |
| 3 | Catch a Black Salamander in the Wilderness. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 4 | Smith an Adamant scimitar in the Resource Area. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 5 | Kill a Lava Dragon. | hooked (kill) |
| 6 | Kill the Chaos Elemental. | hooked (kill) |
| 7 | Kill the Crazy Arc., Chaos Fanatic & Scorpia. | hooked (kill) |
| 8 | Take the agility shortcut from Trollheim into the Wilderness. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 9 | Kill a Spiritual warrior in the Wilderness Godwars Dungeon. | hooked (kill) |
| 10 | Fish some Raw Lava Eel in the Wilderness. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |

**Wilderness Elite** (task id = area*1000 + tier*100 + n, area 9, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Kill Callisto, Venenatis & Vet'ion. | hooked (kill) |
| 2 | Teleport to Ghorrock. | hooked (cast) |
| 3 | Fish and Cook a Dark Crab in the Resource Area. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 4 | Smith a rune scimitar from scratch in the Resource Area. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 5 | Steal from the Rogues' chest. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 6 | Slay a spiritual mage inside the wilderness Godwars Dungeon. | hooked (kill) |
| 7 | Cut and burn some magic logs in the Resource Area. | OPEN-AD4: woodcutting / firemaking: the step's place (and for Burn the fire's tile) is not sourced (OPEN-AD14) |

**Western Provinces Easy** (task id = area*1000 + tier*100 + n, area 10, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Catch a Copper Longtail. | hooked (item) |
| 2 | Complete a novice game of Pest Control. | OPEN-AD4: minigame or activity not in the repository |
| 3 | Mine some Iron Ore near Piscatoris. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 4 | Complete a lap of the Gnome agility course. | hooked (seq) |
| 5 | Score a goal in a Gnomeball match. | OPEN-AD4: minigame or activity not in the repository |
| 6 | Claim any Chompy bird hat from Rantz. | OPEN-AD4: minigame or activity not in the repository |
| 7 | Teleport to Pest Control using the Minigame Teleport. | OPEN-AD4: minigame or activity not in the repository |
| 8 | Collect a swamp toad at the Gnome Stronghold. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 9 | Have Brimstail teleport you to the Essence Mine. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 10 | Fletch an Oak Shortbow in the Gnome Stronghold. | hooked (item) |
| 11 | Kill a Terrorbird in the Terrorbird enclosure. | hooked (kill) |

**Western Provinces Medium** (task id = area*1000 + tier*100 + n, area 10, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Take the agility shortcut from the Grand Tree to Otto's Grotto. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 2 | Travel to the Gnome Stronghold by Spirit Tree. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 3 | Trap a Spined Larupia. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 4 | Fish some Bass on Ape Atoll. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 5 | Chop and burn some teak logs on Ape Atoll. | hooked (item, xp) |
| 6 | Complete an intermediate game of Pest Control. | OPEN-AD4: minigame or activity not in the repository |
| 7 | Travel to the Feldip Hills by Gnome Glider. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 8 | Claim a Chompy bird hat from Rantz after registering at least 125 kills. | OPEN-AD4: minigame or activity not in the repository |
| 9 | Travel from Eagles' Peak to the Feldip Hills by Eagle. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 10 | Make a Chocolate Bomb at the Grand Tree. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 11 | Complete a delivery for the Gnome Restaurant. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 12 | Turn your small crystal seed into a Crystal saw. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 13 | Mine some Gold ore underneath the Grand Tree. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |

**Western Provinces Hard** (task id = area*1000 + tier*100 + n, area 10, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Kill an Elf with a Crystal bow. | OPEN-AD4: combat: weapon or equipment condition, or the NPC is not spawned |
| 2 | Catch and cook a Monkfish in Piscatoris. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 3 | Complete a Veteran game of Pest Control. | OPEN-AD4: minigame or activity not in the repository |
| 4 | Catch a Dashing Kebbit. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 5 | Complete a lap of the Ape Atoll agility course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 6 | Chop and burn some Mahogany logs on Ape Atoll. | hooked (item, xp) |
| 7 | Mine some Adamantite ore in Tirannwn. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 8 | Check the health of your Palm tree in Lletya. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
| 9 | Claim a Chompy bird hat from Rantz after registering at least 300 kills. | OPEN-AD4: minigame or activity not in the repository |
| 10 | Build an Isafdar painting in your POH Quest hall. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 11 | Kill Zulrah. | hooked (kill) |
| 12 | Teleport to Ape Atoll. | hooked (cast) |
| 13 | Pickpocket a Gnome. | hooked (item) |

**Western Provinces Elite** (task id = area*1000 + tier*100 + n, area 10, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Fletch a Magic Longbow in Tirannwn. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 2 | Kill the Thermonuclear Smoke Devil. | hooked (kill) |
| 3 | Have Prissy Scilla protect your Magic tree. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 4 | Use the Elven overpass advanced cliffside shortcut. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 5 | Equip any complete void set. | OPEN-AD4: equipment or emote event not observable |
| 6 | Claim a Chompy bird hat from Rantz after registering at least 1,000 kills. | OPEN-AD4: minigame or activity not in the repository |
| 7 | Pickpocket an Elf. | OPEN-AD4: thieving: the NPC is not in pickpocket.tsv or is not spawned |

**Kourend & Kebos Easy** (task id = area*1000 + tier*100 + n, area 11, tier 0)

| # | Task | State |
|---|---|---|
| 1 | Mine some Iron at the Mount Karuulm mine. | OPEN-AD4: mining: ItemsCreated would fire, but the rock's place is not sourced (OPEN-AD14) |
| 2 | Kill a Sandcrab. | hooked (kill) |
| 3 | Hand in a book at the Arceuus Library. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 4 | Steal from a Hosidius Food Stall. | OPEN-AD4: thieving: chests and the stall's place are not implemented / not sourced |
| 5 | Browse the Warrens General Store. | hooked (npc) |
| 6 | Take a boat to Land's End. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 7 | Pray at the Altar in Kourend Castle | OPEN-AD4: prayer: altar worship is not implemented (only bones on the Chaos altar) |
| 8 | Dig up some Saltpetre. | OPEN-AD4: no observable event (dig / generic item use) |
| 9 | Enter your Player Owned House from Hosidius. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 10 | Do a lap of either tier of the Shayzien Agility Course. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 11 | Create a Strength potion in the Lovakengj Pub. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 12 | Fish a Trout from the River Molch. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |

**Kourend & Kebos Medium** (task id = area*1000 + tier*100 + n, area 11, tier 1)

| # | Task | State |
|---|---|---|
| 1 | Travel to the Fairy Ring south of Mount Karuulm. | OPEN-AD4: transport (boat, ring, tree, glider, balloon, canoe, carpet, lever) not in the repository |
| 2 | Kill a Lizardman. | hooked (kill) |
| 3 | Use Kharedst's memoirs to teleport to all five cities in Great Kourend. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 4 | Mine some Volcanic Sulphur. | OPEN-AD4: mining: the ore or rock is not in MiningData, or its place is not sourced |
| 5 | Enter the Farming Guild. | hooked (area) |
| 6 | Switch to the Necromancy Spellbook at Tyss. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 7 | Repair a Piscarilius crane. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 8 | Deliver some intelligence to Captain Ginea. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 9 | Catch a Bluegill on Molch Island. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 10 | Use the boulder leap shortcut in the Arceuus essence mine. | OPEN-AD4: agility: the obstacle or course is not in AgilityData (only the Gnome, Draynor, Al Kharid and Varrock courses are) |
| 11 | Subdue the Wintertodt | OPEN-AD4: minigame or activity not in the repository |
| 12 | Catch a Chinchompa in the Kourend Woodland. | OPEN-AD4: hunter: the creature or trap is not in hunter.tsv |
| 13 | Chop some Mahogany logs north of the Farming Guild. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |

**Kourend & Kebos Hard** (task id = area*1000 + tier*100 + n, area 11, tier 2)

| # | Task | State |
|---|---|---|
| 1 | Enter the Woodcutting Guild. | OPEN-AD4: no observable event (location, interaction or dialogue content not in the repository) |
| 2 | Smelt an Adamantite bar in The Forsaken Tower. | OPEN-AD4: smithing: the anvil or furnace place, or the product (not an anvil product), is not sourced |
| 3 | Kill a Lizardman Shaman in the Lizardman Temple. | OPEN-AD4: combat: weapon or equipment condition, or the NPC is not spawned |
| 4 | Mine some Lovakite. | OPEN-AD4: mining: the ore or rock is not in MiningData, or its place is not sourced |
| 5 | Plant some Logavano seeds at the Tithe Farm. | OPEN-AD4: minigame or activity not in the repository |
| 6 | Kill a Zombie in the Shayzien Crypts. | hooked (kill) |
| 7 | Teleport to Xeric's Heart using Xeric's Talisman. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 8 | Deliver an artefact to Captain Khaled. | OPEN-AD4: dialogue, shop or NPC service with no event (or the NPC is not spawned) |
| 9 | Kill a Wyrm in the Karuulm Slayer Dungeon. | hooked (kill) |
| 10 | Cast Monster Examine on a Troll south of Mount Quidamortem. | OPEN-AD4: magic: the spell has no observable cast event or its place is not sourced |

**Kourend & Kebos Elite** (task id = area*1000 + tier*100 + n, area 11, tier 3)

| # | Task | State |
|---|---|---|
| 1 | Craft one or more Blood runes from Dark essence. | OPEN-AD4: production: the product's recipe is missing or the place is not sourced (OPEN-AD14) |
| 2 | Chop some Redwood logs. | hooked (item) |
| 3 | Defeat Skotizo in the Catacombs of Kourend. | hooked (kill) |
| 4 | Catch an Anglerfish and cook it whilst in Great Kourend. | OPEN-AD4: fishing: no fishing spots are spawned (the Wiki spawn list has none, OPEN-FS1) and the fish/cooking data lacks some species |
| 5 | Kill a Hydra in the Karuulm Slayer Dungeon. | hooked (kill) |
| 6 | Create an Ape Atoll teleport tablet. | OPEN-AD4: teleport item, tablet or unlocked spell not implemented |
| 7 | Complete a Raid in the Chambers of Xeric. | OPEN-AD4: minigame or activity not in the repository |
| 8 | Create your own Battlestaff from scratch within the Farming Guild. | OPEN-AD4: farming: the crop or patch is not in the farming data, or the step has no event |
