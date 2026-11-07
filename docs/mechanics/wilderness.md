# Wilderness and PvP rules

Code: `content/pvp/*`. Sources (OSRS Wiki raw wikitext, 2026-11-06): Wilderness (W), PJ timer (PJ), Skull (status) (SK),
Logout button (LO), Tele Block (TB), Vengeance (VG), Ring of recoil (RR), Multicombat area (MC).

| # | Rule | Source |
|---|---|---|
| W1 | Players can attack each other if their combat levels differ by at most the Wilderness level (both must be in the Wilderness). | W:136-138 |
| W2 | The first two squares of the Wilderness are safe. | W:140 |
| W3 | PvP worlds: outside a safe zone counts as level 15, inside the Wilderness level 15 + the real level. | W:142 |
| W4 | Single-way PvP: both players get a 20-tick PJ timer, refreshed on every attack (16 ticks on PvP worlds: "increased in the PvP worlds to 16 ticks"); the attacked player also can't attack anyone but their attacker for that time. Not yet modelled: Bounty Hunter/Deadman are exempt; the killer gets a 12 s timer; the singles-attacks-multi asymmetry. | PJ |
| W5 | Multi-combat areas. **OPEN-M2:** "no PJ protection in multi" is our inference (MC doesn't mention PJ). Area polygons: RS Mod `multiway.json` (ISC, data/PROVENANCE.md); the multi icon varbit `multiway_indicator` is RS Mod's. The icon and the Attack option change through the area queue on border crossings (brief §8). | MC, RS Mod, brief |
| W6 | Skull: attacking a player unprovoked skulls you for 30 minutes (3000 ticks); attacking again resets it; it doesn't expire during PvP combat. | SK:11-13, SK:138 |
| W7 | Logout lock: no logout for 16 ticks after being attacked. | PJ:3, LO:9 |
| W8 | Tele Block: applied only "if it successfully hits the target" (the normal magic accuracy roll, F14–F16); 5 minutes, halved to 2:30 "if the target had Protect from Magic up before it was cast and landed" (implemented: checked at cast). Level 85, speed 5, runes law 1, chaos 1, death 1; PfM doesn't change the splash chance. | TB:14-25 |
| W9a | Vengeance Other: Magic 93, astral 3 + death 2 + earth 10, 108 xp; cast on a player within 10 tiles whose Accept Aid is on (varbit `option_acceptaid`) and who has no vengeance yet; shares the caster's 50-tick cooldown; gives the target the W9 reflection and "You have the power of vengeance!"; not castable in a single-combat part of the Wilderness, nor in a player-owned house (OPEN-VO1: not checked). Vengeance gives 112 xp. Refusal messages OPEN (Wiki gives none). | Vengeance_Other |
| W9 | Vengeance: reflects 75% of the next hit, rounded down, minimum 1; not poison/venom/disease; stacks with recoil; 50-tick cooldown; Magic 94; runes astral 4, death 2, earth 10. | VG:14-31 |
| W10 | Ring of recoil: reflects ⌊damage × 10%⌋ + 1 to the attacker; 40 damage, tracked per player (not per ring); queued as a normal script (brief §8). | RR:46-48, brief |
| W12 | PvP worlds: "players may attack each other within all locations outside of safe zones, which include banks and respawn areas"; "Players may not initiate combat within safe areas". A safe zone has Wilderness level 0 on a PvP world. Geometry: OPEN-W2. | PvP world |
| W13 | Singles-plus: like single-way, "with the exception that if a player is in combat with a monster, another player attacking will take priority". In single-way, a player who was hit by an NPC within the PJ window can't be attacked by another player; in singles-plus they can. Areas: the Revenant Caves ("entirely singles-plus") and the Wilderness Agility Course. The icon is varbit `singleway_plus_indicator` (RS Mod `SinglesPlusAreaScript`). | Singles-plus combat, RS Mod |
| W11 | Items kept on death: 3 most valuable (0 when skulled) +1 with Protect Item; the rest drop for the killer (below level 20, most untradeables go to a grave: not modelled, OPEN-D1). | Items Kept on Death:14-15, :286, :306 |

## OPEN

- **OPEN-W1:** Wilderness bounds and level formula: x 2944..3391, z 3520..3967, level ⌊(z − 3520) / 8⌋ + 1. The Wiki gives
  only the range (level 1 at the ditch, 56 in the far north: W:134), which the formula reproduces; the client computes
  the overlay in a clientscript.
- **OPEN-W3:** whether a splash/miss counts as "received a hit" for the logout lock (W7).
- **OPEN-W2:** PvP-world safe-zone geometry isn't published. Derived from the cache (`cache-dump pvp-safe-zones`): every
  tile reachable on foot within 6 tiles of a bank booth or bank chest outside the Wilderness (walls and closed doors stop
  the fill), plus the same around our respawn point. Not modelled: continuing a fight inside a safe zone after having
  retaliated, varbit `pvp_area_client`.
- **OPEN-SK1:** how long a player who was attacked may retaliate without a skull; we use the skull length.
- **OPEN-M1:** singles-plus coordinates: the Revenant Caves are cache map squares 49-50 × 157-159; the agility course is
  its walled yard from the collision map (x 2988..3008, z 3931..3966); the Artio, Calvar'ion and Spindel lairs have no
  published coordinates and are not included. The "player takes priority" rule doesn't yet make the NPC stop attacking.
- **OPEN-D1:** stackables are never "kept"; untradeable death handling isn't modelled.
- **OPEN-TB1 (resolved):** teleports exist now (teleports.md T4): `TELEBLOCK_UNTIL` blocks all of them, and
  `Wilderness.updateArea` keeps `SharedAttrs.WILDERNESS_LEVEL` current for the level-20 / level-30 limits.
- Area changes are detected by a 1-tick soft timer, then queued on the area queue; Jagex triggers area scripts from
  zone/area configs (OPEN-A1).
