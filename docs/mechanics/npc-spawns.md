# NPC spawns

Code: `content/world/.../Spawns.kt`; data `npc-spawns.tsv` from `tools/wiki-import/npc_spawns.py`.

| # | Rule | Source |
|---|---|---|
| SP1 | Each Wiki `{{LocLine}}` pin is one spawn of the NPC it names; the NPC id is the page's infobox version whose cache name and combat level match the line (pins rotate through matching versions). | OSRS Wiki LocLine templates |
| SP2 | Pages without LocLines use the infobox `map` field: a pin/rectangle centre (polygon: bounding-box centre) is the spawn, half the shape's smaller side (at most 5) its wander range. | OSRS Wiki Infobox NPC |
| SP3 | Rows outside planes 0–3 or the 16,384-tile world are dropped (Wiki typos). | — |
| SP2a | `{{Map}}` comes in two spellings: positional pairs (`3185,3441`, also `x:3185,y:3441`) and keywords (`x=3185\|y=3441\|r=9\|mtype=square`, newer pages such as Hudo, Vannaka, Olivia). Every pin of a multi-pin `mtype=pin` map is a spawn (wander 0); before, the pins' bounding-box centre was used. A square's `r` is the map's display radius, not a wander area, so squares don't wander. A `map` shared by several id versions spawns the first id only (the versions are alternative forms of one NPC; spawning all stacked N copies on a tile). | OSRS Wiki Infobox NPC / Template:Map |
| SP3a | **Other game modes are skipped.** Pages about Deadman Mode (and Deadman: All Stars), Leagues, Grid Master, the RuneScape 2 beta and beta tests describe NPCs that never stand in the main game, so they spawn nothing. Before this, "Guard (Deadman Mode)" put 134 level-1337 guards with no animations (cache ids 3361, 6574..6583, 6699..6702, 11201..11211) in Lumbridge, Falador and the Gnome Stronghold. | Wiki page titles; cache 2757 npc configs |
| SP3b | **Pets are not world spawns.** Five pet NPCs (cache op "Pick-up") that Wiki pins placed in the world are dropped: Rock Golem (2182, Lumbridge castle courtyard), Great blue heron (6817), Tiny Tempor (10562), Tumeken's Guardian (11652), Elidinis' Guardian (11653). | seen in the client 2026-11-09 |
| SP3c | **Quest NPCs stand once.** Where a quest plugin spawns an NPC (a multi-NPC whose look follows the quest varp, or the quest's own id), the importer drops the Wiki-pinned child / look-alike on that tile (`npc_spawns.py` `QUEST_CHILDREN`), and `QuestSpawns`/`QuestSpawnsB` no longer spawn NPCs the table already has. Before this, 30 quest NPCs stood twice (seen in the client: two Father Aereck in Lumbridge church). | quests.md QU9 |
| SP3d | **NPCs that plugins spawn are not in the table** (`CONTENT_SPAWNED`): Sea Charting's weather trolls, Tempoross' harpoonfish spot, Wintertodt's pyromancers, Pyramid Plunder's guardian mummy, Otto, the Ourania banker, the Abyss dark mage, Volcanic Mine's Pete, the tea seller, Distentor; and the Restless Ghost, which its quest spawns only mid-quest. | the plugins' own docs |
| SP3e | **Table rows yield to plugin spawns at runtime.** `Spawns` loads the table in `onWorldStartLast`, after every plugin's world-start spawns, and skips a row when a plugin already placed an NPC of the same name (multi-NPCs by the name of the first type they show) within 8 tiles. This catches every overlap whatever ids the two sides use (a quest's multi-NPC vs. the table's child). | engine data rule; 8 = the largest Wiki-pin vs. plugin offset seen (Osman, 7) |
| SP4 | **Bankers.** The Wiki "Banker" page has `map = No` and no LocLines, so only banks with their own "Banker (...)" page have banker tiles. For the rest a banker (cache "Banker" id 1613 or 1618 by tile parity, wander 0) stands directly behind each cache "Bank booth" loc: the side comes from the Wiki's banker tiles next to booths of the same booth model (loc id), turned by the loc angle, else from the collision map (staff floor blocked for players, `BLOCK_WALK` without a loc, opposite a standable tile). Booths with neither get no banker. | OSRS Wiki Banker pages (tiles); cache 2757 loc placements and collision (`cache-dump banker-spawns`, `docs/research/banker-spawns.tsv`) |

| SP10 | **Spawns on tiles nothing can stand on** (`cache-dump spawn-check`, the importer's second pass): a spawn in a map square cache 2757 doesn't have is dropped (358 rows: Ancient Cavern, Karuulm, Mage Arena pins point at squares absent from the client's cache too); one inside solid scenery moves to the nearest standable tile within 3 (a pin a tile off); one on blocked floor stays (ducks and water monsters, bankers behind booths, fliers over gaps). | collision map of cache 2757 |
| SP11 | **Wiki-text faults.** HTML comments are stripped first (pages park unverified pins there: "Citizen (Tal Teklan)" held a copy of Kastori's). Pages without an NPC infobox are skipped ("Splashing/Guide" repeats the Rat, Spider and Seagull pins: 451 doubled NPCs). Versions whose infobox `removal` date is on or before the cache (3 Nov 2026) are skipped (holiday events, Realm of Memories, historical Shayzien). A page's LocLines that list the same pin (one LocLine per combat level: Minotaurs 12/27) give one NPC per pin, ids in turn. Across pages, one NPC of a name per tile; the page titled exactly as the NPC wins. | Wiki page sources |
| SP12 | **Multi-NPCs.** The Wiki pins the type a multi-NPC shows; the game spawns the multi-NPC and the client picks what each player sees. A pinned child becomes its parent when exactly one non-Deadman multi-NPC can show it, all that parent's states are this NPC (same name) or hidden, and either its default state is this child or it only shows/hides it (a quest's visibility switch). Otherwise the child stays (the Tool Leprechaun's only parent is a My Arm's Big Adventure variant that hides it in most states). 668 rows changed. | cache npc `multinpc`; client behaviour |
| SP13 | **Cutscene copies never spawn** (gameval `..._CUTSCENE...`): the page's next version is used instead (Elvarg: 816, the multi-NPC Dragon Slayer I uses, not 817 of the boat scene). | cache gamevals (archive 24) |
| SP14 | **Other-context copies come last.** A version's ids and the by-name fallback prefer ids whose gameval doesn't mark another context (`POH_`, `MACRO_`, `NZONE_`, `DEADMAN`, `SOUL_WARS`, `BARBASSAULT`, `LEAGUE`): Ogre 136 is the house guard, Elvarg 6349 Nightmare Zone's. When only such an id fits, it is used (random-event islands, the Stronghold's Shades). | cache gamevals |

## OPEN

- **OPEN-SP1:** wander ranges aren't published; LocLine spawns use 5 (RS Mod `DEFAULT_WANDER_RANGE`).
- **OPEN-SP2:** 51 LocLines (was 240) name NPCs that no cache id matches by name and level (renamed or removed NPCs); they're
  skipped. Instanced copies (raids, minigames) are spawned where the Wiki pins them.
- **OPEN-SP5:** 358 pins point at map squares absent from cache 2757 (Ancient Cavern's Mithril dragons, Karuulm's Sulphur
  Lizards and Drakes, the Mage Arena's Kolodion, Stronghold/dungeon squares); those areas moved or were rebuilt, and their
  current spawn tiles are not on the Wiki pages yet. Those NPCs are missing until they are found.
- **OPEN-SP6:** Mort Myre's Ghasts are pinned as their visible form (GHAST_VIS, 184); the game shows them only while they
  attack. The Ghast mechanic isn't implemented, so they stay visible.
- **OPEN-SP3:** banker sides are inferred, not published. Leave-one-out over the 37 booths with a Wiki banker beside them: 28 right,
  5 wrong, 4 without a prediction. 94 of the 219 cache booths have no "Banker" beside them (no Wiki neighbour of their model, no collision evidence,
  or bankers of another name such as Ghost banker / Nardah Banker), among them Burgh de Rott, the Kourend
  mine banks and Ardougne south's second row. The "Grand Exchange booth"
  locs (ids 10060/10061) sit on raw level 1 with no collision on either level, so the GE bankers are not placed; the
  Banker male/female/GE model per bank is unpublished (1613/1618 alternate).
- **OPEN-SP4:** multi-version pages with one shared `map` spawn only their first id. For NPCs that are really separate ids at
  one spot (not forms of one NPC) the others are missing; before, every version was spawned on the same tile (SP2a).
- The golden scenarios keep the small dev spawn set (`content/dev` DevSpawns, enabled by `-Dtp.devSpawns=true`).

- **Disambiguated pages.** Names and titles like "Scorpia's offspring (monster)" drop the parenthesised part before the
  cache-name match. Unmatched LocLines fell from 239 to 74, and the table grew to 21,062 spawns.
- **Keyword maps and bankers.** The map importer ignored `{{Map|x=..|y=..}}`, so NPCs of many pages (Hudo, Vannaka, Olivia,
  Fishmonger, ...) had no spawn at all; with it fixed and bankers added, the table has 24,588 spawns (+4,113, -578; the removals
  are version stacks and multi-pin bounding-box centres, see SP2a).

- **SP5 (Grand Exchange desk):** the Wiki gives the centre's 4 bankers and 4 clerks only as one map polygon, so the importer places
  them by hand (`MANUAL_SPAWNS`): bankers behind the "Bank" booths (loc 10060, west / east), clerks behind the "Exchange" booths
  (loc 10061, south / north). The booths' own ops (Bank, Exchange, Collect) are handled like the NPCs' (gameframe Bank / GE).

