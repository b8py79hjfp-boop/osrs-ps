# Wilderness and PvP rules

Code: `content/pvp/*`. Sources (OSRS Wiki raw wikitext, 2026-11-06): Wilderness (W), PJ timer (PJ), Skull (status) (SK),
Logout button (LO), Tele Block (TB), Vengeance (VG), Ring of recoil (RR), Multicombat area (MC).

| # | Rule | Source |
|---|---|---|
| W1 | Players can attack each other if their combat levels differ by at most the Wilderness level (both must be in the Wilderness). | W:136-138 |
| W2 | The first two squares of the Wilderness are safe. | W:140 |
| W3 | PvP worlds: outside a safe zone counts as level 15, inside the Wilderness level 15 + the real level. | W:142 |
| W4 | Single-way PvP: both players get a 20-tick PJ timer, refreshed on every attack; the attacked player also can't attack anyone but their attacker for 12 s (20 ticks). | PJ |
| W5 | Multi-combat areas: no PJ protection. Area polygons: RS Mod `multiway.json` (ISC, data/PROVENANCE.md). The multi icon (varbit `multiway_indicator`) and the Attack option change through the area queue on border crossings (brief §8). | MC, brief |
| W6 | Skull: attacking a player unprovoked skulls you for 30 minutes (3000 ticks); attacking again resets it; it doesn't expire during PvP combat. | SK:11-13, SK:138 |
| W7 | Logout lock: no logout for 16 ticks after being attacked. | PJ:3, LO:9 |
| W8 | Tele Block: 5 minutes, halved to 2:30 if the target had Protect from Magic up when it landed. Runes: law 1, chaos 1, death 1. | TB:25 |
| W9 | Vengeance: reflects 75% of the next damage (rounded down); 50-tick cooldown; Magic 94; runes astral 4, death 2, earth 10. | VG:14-29 |
| W10 | Ring of recoil: reflects ⌊damage × 10%⌋ + 1 to the attacker; 40 damage per ring; queued as a normal script (brief §8). | RR:46, brief |
| W11 | Items kept on death: 3 most valuable (0 when skulled) +1 with Protect Item; the rest drop for the killer. | Items Kept on Death:14-15, :286 |

## OPEN

- **OPEN-W1:** Wilderness bounds and level formula: x 2944..3391, z 3520..3967, level ⌊(z − 3520) / 8⌋ + 1. Not stated by
  the Wiki (the client computes the overlay in a clientscript).
- **OPEN-W2:** PvP-world safe zones aren't modelled (banks, etc.).
- **OPEN-SK1:** how long a player who was attacked may retaliate without a skull; we use the skull length.
- **OPEN-M1:** singles-plus areas (Revenant caves etc.) are treated like single-way.
- **OPEN-D1:** stackables are never "kept"; untradeable death handling isn't modelled.
- **OPEN-TB1:** Tele Block accuracy (it currently always lands); teleports don't exist yet to block.
- Area changes are detected by a 1-tick soft timer, then queued on the area queue; Jagex triggers area scripts from
  zone/area configs (OPEN-A1).
