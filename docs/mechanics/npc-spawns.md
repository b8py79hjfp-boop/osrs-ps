# NPC spawns

Code: `content/world/.../Spawns.kt`; data `npc-spawns.tsv` from `tools/wiki-import/npc_spawns.py`.

| # | Rule | Source |
|---|---|---|
| SP1 | Each Wiki `{{LocLine}}` pin is one spawn of the NPC it names; the NPC id is the page's infobox version whose cache name and combat level match the line (pins rotate through matching versions). | OSRS Wiki LocLine templates |
| SP2 | Pages without LocLines use the infobox `map` field: a pin/rectangle centre (polygon: bounding-box centre) is the spawn, half the shape's smaller side (at most 5) its wander range. | OSRS Wiki Infobox NPC |
| SP3 | Rows outside planes 0–3 or the 16,384-tile world are dropped (Wiki typos). | — |

## OPEN

- **OPEN-SP1:** wander ranges aren't published; LocLine spawns use 5 (RS Mod `DEFAULT_WANDER_RANGE`).
- **OPEN-SP2:** 240 LocLines name NPCs that no cache id matches by name and level (renamed or removed NPCs); they're
  skipped. Instanced copies (raids, minigames) are spawned where the Wiki pins them.
- The golden scenarios keep the small dev spawn set (`content/dev` DevSpawns, enabled by `-Dtp.devSpawns=true`).
