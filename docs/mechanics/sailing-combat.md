# Sailing: boat combat and bounty tasks (S10, main session only)

Nothing here is implemented. This lists what S10 must provide so that the content already written (port bounty tasks, salvaging, charting) works, and what
it needs from the sources. Research: docs/plans/sailing.md 5.7; port-tasks: docs/mechanics/sailing-port-tasks.md PT11.

Sources: cache 2757 db 162 (cannon combat stats), db 165 (ammunition), db 197 (bounty task columns), NPC symbols `SAILING_*` 15194-15235 (sea creatures),
objs 32809-32846 (bounty parts); OSRS Wiki https://oldschool.runescape.wiki/w/Bounty_tasks, /Boat_combat (if present), /Cannon_(facility), /Sailing.

## What sea combat must call

| # | Call | When | Why |
|---|---|---|---|
| X1 | `PortTasks.onBountyKill(player, npcType)` (`content/sailing/.../PortTasks.kt`, `internal`; the hook exists, nothing calls it) | once for each sea creature a player's boat (or the player's cannon) kills, with the killed NPC's type id; for a creature that changes form on death the id matches either `PortTask.bountyTargetAlive` or `bountyTargetDead` | rolls 1/`bountyRarity` for the first active bounty task of that monster whose parts are not all collected and puts the part (`bountyObj`) in the backpack. Without it no bounty task can be completed. |
| X2 | credit rule | the player who dealt the killing blow, or every player who contributed? | OPEN-PT8: the Wiki does not say; `onBountyKill` takes one player. |
| X3 | the part's drop | backpack today; the Wiki says the part drops from the monster | OPEN-PT8 |

`PortTasks.onBountyKill` needs no change for the first two; if combat lives in another module, expose it as a plugin-api event
(`SeaCreatureKilled(player, npcType)`), which is a main-session API decision.

## What S10 needs to exist first

1. Boat HP as the combat HP while aboard (`ActiveBoat.hp` / `maxHp` exist; persisted when moored); capsize at 0 (`BoatServices` has the capsized flag and
   recovery).
2. Cannons (facility subtype 4, max 2, rows in db 162: ranged level, accuracy, damage, ammunition tier, `ATTACK_RATE` 7, `ATTACK_RANGE` 10 (cache wins over
   the Wiki's 15), fire arc 180 degrees from the cannon, ammunition db 165 (22 rows), ballistic attractor saves 60%, Perildance bitter +1 max hit,
   crew privateering multiplier `(P + 13) / 20`).
3. Sea creature NPCs on the root world with attack patterns against a world entity (a boat is a world entity: `onOpNpc` from a deck, projectiles between
   a deck and the root world). Their stats are not in the cache; the Wiki creature pages are the source.
4. Repair kits (objs 31964+, 7 kinds), repair rate of the bosun's workbench (db 164 row 16256), the hazard damage ticks (OPEN-H1).
5. The drops: bounty parts (objs 32809-32846) and creature drop tables.
