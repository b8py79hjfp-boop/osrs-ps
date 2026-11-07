# Sailing: steering, speed, rapids and hazards

Skeleton for milestone S2 (main session: tick order, protocol). Research: docs/plans/sailing.md section 3 and 4.1 (hazards).
No behaviour is implemented; the numbers below are the sourced starting point.

Sources: cache 2757 db 164 (`BASESPEED`, `SPEEDCAP`, `ACCELERATION`, `SPEEDBOOST_DURATION`), db 200 (hazard names); RSProt 242
`SetHeading`, `SetInteractionMode`; OSRS Wiki https://oldschool.runescape.wiki/w/Sailing, /Rapids, /Sailing_hazards, /Mast_and_sails,
/Helm, /Anchor_(facility), /Update:Adding_A_New_Skill:_Sailing_Navigation_Mechanics (design blog 2023), /March_2025_Sailing_Alpha,
/June_2025_Sailing_Beta.

## Rules

| # | Rule | Source |
|---|---|---|
| M1 | The captain right-clicks the helm: Navigate locks them at the wheel and their clicks set the heading (16 steps, `SetHeading` 0..15 = angle / 128); Stop navigating keeps the boat on its heading. One navigator at a time; crew with helmsmanship may steer (a sloop needs 3). | Wiki Sailing, Helm; protocol 242 |
| M2 | After the heading is reached the boat moves while the throttle is raised; the set / unset sails button starts and stops at once; reverse is possible when stationary. Sail states: down, half, full, reverse. | Wiki Sailing, Sailing Options |
| M3 | Speeds are fine units per tick (128 per tile). Hull base speed 1.5 / 2.0 / 2.5 / 3.0 tiles per tick by tier; the speed cap is 2.5 / 3.0 / 3.5 by boat size and tier. | cache db 164; plan 2.2, 3.2 |
| M4 | Sails can be trimmed every 30 s (50 ticks); a gust opens a trim window of about 12 s (20 ticks); a trim boosts speed by up to 0.5 tiles per tick for 20..36 ticks by sail tier (db 164 `SPEEDBOOST_DURATION`); XP per trim 10.5 .. 125 by sail tier. | Wiki Sailing, Mast and sails, Alpha, Beta |
| M5 | Rapids add a stack per pass (strong 2, powerful 3 when unboosted), the 15-tick decay timer is renewed per pass, cap 15 stacks, each stack +0.5 tiles per tick up to the speed cap; too low a helm means damage instead. | Wiki Rapids |
| M6 | Hazards (db 200 names): stormy seas (oak mast), jagged reefs, fetid waters (inoculation station), crystal-flecked waters (adamant keel), tangled kelp (adamant helm), icy seas (eternal brazier). Unprotected boats are slowed and damaged; the world border refuses with a message. Areas and damage numbers are server-only. | Wiki Sailing hazards; plan 4.1 |
| M7 | The anchor stops the boat faster than furling sails and allows rotation in place. | Wiki Anchor |
| M8 | Collisions: the boat is blocked by land and rocks; boat HP 0 capsizes it (player returns to the last gangplank). | Wiki Sailing, Boat |

## Implemented (S2 core, `content/sailing/.../BoatMotion.kt`)

- **SM1, helm:** `navigate` puts the player at the helm and switches tile clicks in every world (`SET_INTERACTION_MODE`
  world -2) to heading mode; entity clicks become examine-only. `stopNavigating` restores walking. Verified in the
  client: hovering the sea shows "Set heading", and a click sends SET_HEADING.
- **SM2, sails:** down / half / full / reverse set the target speed: 0, half the base, the base (capped), and a quarter
  of the base astern (OPEN-M4). Setting a heading starts a stopped boat (OPEN-M5).
- **SM3, the tick:** in the world-entity phase (world-entities.md WE10), each boat:
  - turns toward the heading by its turn rate;
  - moves its speed toward the target by its acceleration;
  - moves its pivot along its facing (angle 0 south, 512 west, 1024 north, 1536 east).
- **SM5, collision:** the centre, bow, stern and sides of the footprint must be sea with no blocking loc
  (world-entities.md WE9). Otherwise the boat stops and the navigator gets "Your boat can't go any further that way."
  (OPEN text).
- **Turn rates and acceleration** are passed in by the boat's spawner from its stats (OPEN-M1, OPEN-M3).
- **Verified in the client:** a click east turned the boat and it sailed away from the Port Sarim docks with the
  camera following.

## OPEN

- **OPEN-M1:** turn rate per tick and throttle steps. The 2023 design blog proposed 2 / 4 / 6 ticks per 90 degrees but is not the
  released numbers.
- **OPEN-M2:** how the sailable-sea mask and rock collision are encoded (overlay type, a tile flag, or a server table).
- **OPEN-M3:** acceleration units (cache 64 vs Wiki 0.5 / 1.0) and the default for tiers without the stat.
- **OPEN-M4:** units of the side panel speed setting ("quarter tiles per tick") vs the cache fine units.
- **OPEN-H1:** hazard area polygons, damage per tick and frequency for each hazard (db 200 columns are stripped).
