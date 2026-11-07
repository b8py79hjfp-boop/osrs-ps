# NPC combat (rev 242)

The cache gives NPC levels and hitpoints only. Everything else comes from the OSRS Wiki's `{{Infobox Monster}}`,
imported by `tools/wiki-import/npc_combat.py` into `content/combat/.../npc-combat.tsv` (4,304 npc ids from 1,660
pages, 2026-11-06). Re-run the script to refresh.

Sources:
- IM: OSRS Wiki `Template:Infobox Monster` fields on each monster page (https://oldschool.runescape.wiki/w/Template:Infobox_Monster).
- RW: OSRS Wiki "Ranged weapons" (https://oldschool.runescape.wiki/w/Ranged_weapons): "Standard: bows, salamanders;
  Heavy: chinchompas, crossbows; Light: thrown weapons".
- Existing rules: combat-formulas.md (F-rules), combat-timeline.md (C-rules), prayers.md (P5).

| # | Rule | Source |
|---|---|---|
| N1 | Attack speed (ticks), max hit, attack styles, attack/strength bonuses per style, seven defence bonuses (stab, slash, crush, magic, light/standard/heavy ranged), respawn (ticks), poison, poison/venom immunity and aggression are per npc id from the infobox. Versioned infoboxes (`id1`, `max hit2`, ...) are expanded per id. When versions share one id (raid scaling, phases, Deadman), the first by page title then version is kept (normally the base version); 200 such ids. | IM |
| N2 | NPC attack roll = (level + 9) × (bonus + 64) with the style's level (Attack/Ranged/Magic) and attack bonus (F-rules). The max hit is the infobox number for that style when the field names styles ("55 (Crush), 31 (Ranged)"), else the largest number in the field. Protection prayers block NPC attacks completely (P5). | IM, F-rules, P5 |
| N3 | Player attacks on NPCs use the NPC's defence bonus for the attack type; magic uses the NPC's Magic level and magic defence. | IM, F6/F7 |
| N4 | Player ranged attacks use the NPC's light, standard or heavy ranged defence by weapon: thrown → light, crossbow (and chinchompa) → heavy, bow and anything else → standard. | RW |
| N5 | A melee-only NPC walks into reach. An NPC with ranged or magic attacks from range (OPEN-N5) and stays there; if it also has a melee style it keeps closing in and melees once in reach. | IM (styles), OPEN-N5 |
| N6 | Respawn after death uses the infobox `respawn` ticks; NPCs without one use 25 ticks. | IM |

## Timeline

- Attack tick T: style chosen (N5/OPEN-N3), accuracy and damage rolled, attack clock = T + speed.
- Melee: hit applied in the defender's queue at T (C-rules). Ranged: T + 1 + ⌊(3 + d) / 6⌋. Magic: T + 1 + ⌊(1 + d) / 3⌋
  (d = distance; OPEN-N8).
- Death: removed 4 ticks after the killing hit (OPEN-N2), respawns `respawn` ticks later (N6).

## OPEN

- **OPEN-N2:** death length (no infobox field). Implemented: 4 ticks.
- **OPEN-N3:** style choice for NPCs with several styles. The infobox lists styles but not when each is used (Callisto
  "will use ranged against players if he cannot reach them"). Implemented: melee in reach when available, otherwise a
  uniform pick between ranged and magic per attack. Hand-written NPC scripts override this.
- **OPEN-N5:** ranged/magic attack distance (not in the cache NPC config or the infobox). Implemented: 7 tiles with line
  of sight (`NpcCombat.DISTANCE_RANGE`).
- **OPEN-N7:** a plain "Melee" style is treated as crush.
- **OPEN-N8:** NPC projectile hit delays use the player formulas (C1). NPC attack animations and projectiles aren't sent
  (no data source; visual only).
- Aggression, tolerance, max range and retreat: aggression.md.

## Animations

- **N10:** NPCs play an attack anim per style, a block anim when hit and a death anim when they die. The cache has no
  such fields on NPC configs, so `npc-anims.tsv` is derived from it (tools/wiki-import/npc_anims.py over `cache-dump
  anim-groups`): the seqs animating the stand seq's skeleton (frame base or keyframe set), picked by their gameval
  names (`*_ATTACK`, `*_DEFEND` / `*_BLOCK`, `*_DEATH` and variants), preferring the stand seq's name prefix and the
  nearest id. 3,864 of 4,303 fighting NPCs get an attack anim.
- **OPEN-N11:** the derived anims are a heuristic (no server data source); NPC projectiles for ordinary ranged / magic
  NPCs are not known and none are shown (bosses send their own).
