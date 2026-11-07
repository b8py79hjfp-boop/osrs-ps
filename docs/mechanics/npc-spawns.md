# NPC spawns

Code: `content/world/.../Spawns.kt`; data `npc-spawns.tsv` from `tools/wiki-import/npc_spawns.py`.

| # | Rule | Source |
|---|---|---|
| SP1 | Each Wiki `{{LocLine}}` pin is one spawn of the NPC it names; the NPC id is the page's infobox version whose cache name and combat level match the line (pins rotate through matching versions). | OSRS Wiki LocLine templates |
| SP2 | Pages without LocLines use the infobox `map` field: a pin/rectangle centre (polygon: bounding-box centre) is the spawn, half the shape's smaller side (at most 5) its wander range. | OSRS Wiki Infobox NPC |
| SP3 | Rows outside planes 0–3 or the 16,384-tile world are dropped (Wiki typos). | — |
| SP2a | `{{Map}}` comes in two spellings: positional pairs (`3185,3441`, also `x:3185,y:3441`) and keywords (`x=3185\|y=3441\|r=9\|mtype=square`, newer pages such as Hudo, Vannaka, Olivia). Every pin of a multi-pin `mtype=pin` map is a spawn (wander 0); before, the pins' bounding-box centre was used. A square's `r` is the map's display radius, not a wander area, so squares don't wander. A `map` shared by several id versions spawns the first id only (the versions are alternative forms of one NPC; spawning all stacked N copies on a tile). | OSRS Wiki Infobox NPC / Template:Map |
| SP4 | **Bankers.** The Wiki "Banker" page has `map = No` and no LocLines, so only banks with their own "Banker (...)" page have banker tiles. For the rest a banker (cache "Banker" id 1613 or 1618 by tile parity, wander 0) stands directly behind each cache "Bank booth" loc: the side comes from the Wiki's banker tiles next to booths of the same booth model (loc id), turned by the loc angle, else from the collision map (staff floor blocked for players, `BLOCK_WALK` without a loc, opposite a standable tile). Booths with neither get no banker. | OSRS Wiki Banker pages (tiles); cache 2757 loc placements and collision (`cache-dump banker-spawns`, `docs/research/banker-spawns.tsv`) |

## OPEN

- **OPEN-SP1:** wander ranges aren't published; LocLine spawns use 5 (RS Mod `DEFAULT_WANDER_RANGE`).
- **OPEN-SP2:** 240 LocLines name NPCs that no cache id matches by name and level (renamed or removed NPCs); they're
  skipped. Instanced copies (raids, minigames) are spawned where the Wiki pins them.
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

