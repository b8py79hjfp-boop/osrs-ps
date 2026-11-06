# NPC drops (rev 242)

Code: `content/drops/.../Drops.kt` (roll + ground objs), `DropData.kt` (TSV loader). Data: `npc-drops.tsv` and
`drop-tables.tsv` from `tools/wiki-import/npc_drops.py` (3,132 npc ids from 1,061 of the 1,660 Infobox Monster pages,
16,737 drop lines, 57 shared tables, 2026-11-06). Re-run the script to refresh.

Sources, fetched from oldschool.runescape.wiki on 2026-11-06:
- DR: "Drops", https://oldschool.runescape.wiki/w/Drops
- DT: "Drop table", https://oldschool.runescape.wiki/w/Drop_table
- GDT: "Gem drop table", https://oldschool.runescape.wiki/w/Gem_drop_table
- RDT: "Rare drop table", https://oldschool.runescape.wiki/w/Rare_drop_table
- DL: `Template:DropsLine/doc`, https://oldschool.runescape.wiki/w/Template:DropsLine/doc
- RM: RS Mod `api/death/.../NpcDeath.kt` (`spawnDeathDrops(dropCoords = access.coords)`)
- GI: ground-items.md (G1-G3: visibility and despawn timers)

## Rules

| # | Rule | Source |
|---|---|---|
| D1 | A monster page's drop rows are its `{{DropsLine}}` lines and table templates, expanded by the Wiki's own parser. A row applies to every infobox version unless the row or its `DropsTableHead` names `\|dropversion=`, then only to versions whose infobox `dropversion` shares a name. | DL: "Any drop without a dropversion is considered to apply to all infobox versions at all locations." |
| D2 | **Main drop**: the table is rolled once per kill and one row is chosen; the rows are exclusive and their rarities add up to 1, the rest is empty (explicit "Nothing" rows are the same). A row with `rolls=N` ("N × 1/128") makes the table roll N times. | DR: "The main drop table, if a monster has one, is guaranteed to be rolled when a monster dies. It contains a selection of items, with one randomly chosen. For a some monsters, this table can be rolled multiple times in one kill. It can also contain 'empty' drops". |
| D3 | **Pre-roll**: pre-roll rows are rolled first, in the Wiki's order, each on its own chance; a hit gives that item and skips the main table, otherwise the main table is rolled. | DT: "A pre-roll is a drop table which is rolled before rolling the main drop table. ... if no pre-rolled items are hit, the main table is then rolled." |
| D4 | A quantity is one integer, a range (`5-10`) or a list (`1-3,5`); every listed integer is equally likely. `(noted)` drops the noted form. | DL: "If multiple quantities are randomly dropped, list them all and separate with commas"; DR: "in almost all cases every option in the range is equally likely". |
| D5 | **100% drops** (table "100%", rarity Always) are dropped on every kill. | DR: "These are the items that a monster is guaranteed to drop when it dies." |
| D6 | **Tertiary drops** (tables whose heading says "tertiary": clue scrolls, pets, champion scrolls, looting bags, ensouled heads) are each rolled on their own chance, so several can drop together. The curved bone is rolled only if the long bone was not given. | DR / DT: "Tertiary drops are drops that are rolled independently of the main drop table ... Unlike the main drop, multiple tertiary drops can be obtained in a single kill. The long bone and curved bone are unique among tertiary drops in that the curved bone is only rolled if the long bone is not given". |
| D7 | **Secondary drops** (tables whose heading says "secondary") are one more roll of their own beside the main roll. | DR: "Secondary drops are drops alongside main drops." |
| D8 | **Shared tables** (herb, seed, talisman, rare, gem, mega-rare, ...): a monster line gives the chance of rolling the table (`{{HerbDropLines\|7/128}}`, a main-table row); the table is then rolled once with its own chances (sum 1). Seeds that depend on combat level (`{{GeneralSeedDropLines\|18/128\|28}}`) are expanded by the Wiki template for that level. | DT: "the rare drop table must be hit first, which in this example has a 1/50 chance, then it must hit the sapphire's 1/4 after that. This brings the chance of a sapphire to a 1/200 drop rate". |
| D9 | The rare table lists the gem table (20/128) and the mega-rare table (15/128) as rows; the gem table lists the mega-rare table (1/128). A worn ring of wealth removes the empty slots of the gem table (63/128) and the mega-rare table (113/128): the roll then spans what is left (1/128 becomes 1/65, 4/128 becomes 4/15). Any worn obj whose cache name starts with "Ring of wealth" counts. | GDT / RDT: "Wearing a ring of wealth will remove all empty slots from the table." / "it removes the 63 empty slots, turning a 1/128 chance of moving to the megarare list into a 1/65 chance." |
| D10 | The gem table's talisman is a chaos talisman inside the rectangle x 1920-3903, z 9474-11263 (underground), else a nature talisman. A monster whose page names only one talisman (`chaostalisman=yes` or `naturetalisman=yes`) drops that one. | GDT "Talisman": "Overworld monsters drop the nature talisman, whereas underground monsters drop the chaos talisman if they are found within the region shown below" (polygon 1920,9474 / 1920,11263 / 3903,11263 / 3903,9474). |
| D11 | Loot goes to the player who dealt the most damage (`DeathEvent.lootFor`), who sees it first; a large monster drops it on its south-west tile. | DR: "The player who has done the most damage will see the drop before the other players." / "Large monsters ... will always drop their drops in the south-westernmost square."; RM: drops at the npc's coords (its south-west tile). |
| D12 | Drop objs are created through the item transaction API (`createOnGround`), private to the killer first, then public (G1-G3: 100 ticks private, tradeables despawn 200 ticks after, untradeables 300). Unstackable objs drop one by one (one ground obj is one stack). | GI, DR: "Tradeable drops remain on the ground for 200 game ticks ... Untradeable drops will disappear after 300 game ticks ... invisible to other players for the first 100 game ticks". |

The roll uses the world RNG only (`EntityRef.random`), in the order always, pre-roll or main, secondary, tertiary.
Chances are exact fractions in the TSV (`num/den`); a roll draws a uniform integer below 2^30 and compares it with the
cumulative chance, so every Wiki chance is exact to better than one in a billion.

## Data format

`npc-drops.tsv`: `npcs kind item qty noted num den rolls`, one row per drop line shared by comma-separated npc ids.
`kind` is always, pre, main, independent, secondary or tertiary. `item` is a cache obj id, `@table` (a shared table) or
`-` (empty). `drop-tables.tsv`: `table item qty noted num den`; `-w` is an empty slot a ring of wealth removes,
`nature/chaos` is the talisman pair of D10.

## OPEN

- **OPEN-D1:** conditional drops are not modelled and are skipped, because their conditions (Slayer task, instance, quest
  state, region) are not tracked: tertiary rows with rarity "Always" (168, e.g. the medium clue key), rows with a
  `leagueRegion` (288, e.g. the looting bag, which the Wiki lists for the Wilderness only), Brimstone key rows (58, Konar
  task), and the templates `WildernessSlayerDropTable`, `SuperiorDropTable`, `SuperiorDropTableTBL2` (1,806 rows in all).
- **OPEN-D2:** revenant drops (`{{Revenants/Drops}}`) change with skull and Slayer task and are not imported; revenants
  drop nothing yet. `DropsLineSkill` and `DropsLineReward` rows (skilling, rewards) are not monster drops.
- **OPEN-D3:** 78 npc ids list main tables that add up to well over 1 (several tables or variants on one page, for
  example Bloodthirsty nechryarchs). Their rows are marked `independent` and each rolls on its own chance, which keeps
  every item's chance as the Wiki prints it but can drop more than one item.
- **OPEN-D4:** when one infobox lists several drop versions for one npc id (`Normal,Greater,Wilderness Slayer Cave`),
  the first listed is used. The Wiki rows for the others are not reachable for that id.
- **OPEN-D5:** worded rarities have only a documented range (DL: Common 1/2 to 1/25, Uncommon 1/26 to 1/99, Rare 1/100
  to 1/999, Very rare 1/1000 or rarer). Implemented: 1/7, 1/50, 1/316, 1/1000 (the middle of each range, 126 rows).
  "Varies", "Random", "Once", "Unknown" and blank rarities (95 rows) are skipped.
- **OPEN-D6:** the server is a members world: rows marked free-to-play-only `(f)` are excluded, members rows are kept.
- **OPEN-D7:** 117 rows name items the cache lists under another name (the Wiki's parenthesised page titles), 59 distinct,
  and are skipped. Most frequent: "Bird nest (egg/seeds/ring)", "Grym leaf (corrupted)", "Weapon frame (corrupted)",
  "Crystal shards (The Gauntlet)", "Teleport crystal (The Gauntlet)", "Tooth half of key (moon key)", "Monodon bones".
  Pets are dropped as their item on the ground (no follower system yet).
- **OPEN-D8:** the gem table's mega-rare row is only reachable with Legends' Quest done (GDT); we do not check it.
- **OPEN-D9:** an NPC killed by anything but a player (`lootFor` null) drops nothing: item transactions need a player and
  the Wiki describes no unowned monster loot.
- **OPEN-D10:** where a page lists a row for one dropversion and its note says "only dropped by X" without a
  `dropversion` (hill giant iron full helm on the Giants' Plateau), the row applies to every version.
- Item names resolve to the lowest cache obj id of that name (noted copies and placeholders come later); "Coins" is
  fixed to 995 because id 617 is an unstackable members-only variant.
