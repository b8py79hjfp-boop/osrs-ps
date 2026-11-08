# Chambers of Xeric: implementation plan (R5)

Sources: `docs/mechanics/raids/cox.md`, `cox/lobby-rewards.md`, the room docs in `cox/`, `cox/client-contract.md`,
`cox/runelite-timings.md`. Brief section 1 / 3 / 4.

## Structure on this server

- **Instance:** a run's instance holds both generated floors on one 128 x 64 area (planes 3 and 2, a 4 x 2 grid of
  32 x 32 room cells each; CM adds plane 1) plus the Olm arena square (50, 89) copied whole beside them. Every cell is a
  zone copy (4 x 4 zones) of its template variant (client-contract 6.2-6.4), so RuneLite's room and layout plugins see
  the real template references.
- **Layout:** one of the 26 RuneLite layout codes (cox.md CX12; pool rule CX13 / OPEN-CX7), placed on the grid by its
  direction string; combat slots from one of the four rotation cycles (CX11); puzzle rooms drawn without repeats
  (OPEN-CX6); scavenger rooms small or large, the resource room fishing or bats. A cell's variant (CCW / THRU / CW) and
  rotation come from the directions it is entered and left by (client-contract 6.2: rotation = entry move direction,
  winding = exit side - rotation). Full / CM: the fixed CM layout (CX15).
- **Traversal:** a room's Passage (29789 "Enter") takes the party on to the next room's arrival tile once the room is
  done (combat and puzzle rooms) or at once (scavengers, resources); the floor-end hole goes down to the next floor's
  start cell; the boss-entrance hole leads to the Olm arena (barrier 29879 "Pass"). OPEN-CX20: the corridors between
  cells are not in the cache.
- **raids-core** gains a per-run layout (`RaidDefinition.layout`) and per-run room entries (`RoomDef.entryAt`); CoX uses
  framework rooms `chambers` (both floors, encounter), `olm` (encounter) and `rewards`.

## Milestones

1. Layout generator, instance build, entrance from Mount Quidamortem, passages, holes and ropes, room-done gating,
   respawn at the previous floor (CX34), smoke scenario that walks a whole layout.
2. Points ledger (CX21-CX32) with OPEN models, HUD 513 driver, records (KC 1532 / CM 1735, messages, PB buckets).
3. Combat rooms (content/combat, main session): Tekton, Muttadiles, Guardians, Vespula, Shamans, Vasa, Vanguards, Mystics.
4. Puzzle rooms: crabs, ice demon, tightrope, thieving; resource rooms: scavengers, farming, fishing / bats, cooking,
   herblore potions.
5. Great Olm (all phases and specials) and Challenge Mode.
6. Board / party (499 / 507 / 500), storage units (271 / 550 / 551), reward chest 539 and the loot model, CM scoreboard 21
   (implementers, against the stable flow).

## Status (2026-11-08)

1. Done: layouts (26 codes + rotation cycles), per-run instance of zone copies, entrance, lobby start and Reload,
   passages / holes / ropes, room gating, CX34 respawn (commits ac8f991, 6c4b2a6).
2. Done: points ledger (OPEN-CX13 rate), death penalty, HUD 513, records with team size and PB buckets (c0209db).
3. Done: the eight combat rooms with the wiki calculator's scaling (06236c2).
4. Done: thieving, tightrope, ice demon, crabs; scavengers, shortcuts, farming, food, Herblore, kindling (fdf1f57,
   2a4d505).
5. Done: the Great Olm (all phases, powers, specials) and the Challenge Mode layout and bonus (f4f13ac).
6. Done: loot model (513bed2); board 499 / 507, party tab 500, storage units 271 / 550 / 551, reward chest 539 and its
   light, CM scoreboard 21, Rimor's capes, lore books (`CoxInterfaces`; OPEN-CXUI1-11 in the code).
