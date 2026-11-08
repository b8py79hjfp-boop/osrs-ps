# Instances

Instanced map areas (player-owned houses, boss lairs, minigames): `engine/.../map/Instances.kt`, plugin-api `InstanceRef`.

## Rules

- **IN1: instance space.** An instance is a block of 1..4 × 1..4 map squares at map-square x 100 and up, where the
  rev-242 cache has no map. Allocation skips any square the cache has. Server coordinates in an instance are ordinary
  coordinates there, so movement, route finding, zone updates, ground items, NPCs and the info packets need no special
  case. The client accepts such coordinates: tiles go up to 16383 and REBUILD_REGION names the centre zone directly.
- **IN2: copied zones.** A zone of an instance is a copy of one cache zone, the template, turned 0..3 quarter turns
  clockwise. Its collision is rebuilt from the template:
  - tiles flagged "block map square" become blocked;
  - "remove roofs" becomes the roof flag;
  - each template loc is applied with its rotated tile and angle.
  The bridge rule (`LINK_BELOW`) is applied when reading the template. Uncopied zones of an instance are unallocated,
  which means fully blocked.
- **IN3: rotation.** These are the client's scene-copy formulas from RuneLite's deobfuscated `Client`. For a footprint
  of `sizeX × sizeZ` at zone-local (x, z), turned r times:
  - r = 1: (z, 7 − x − (sizeX − 1))
  - r = 2: (7 − x − (sizeX − 1), 7 − z − (sizeZ − 1))
  - r = 3: (7 − z − (sizeZ − 1), x)
  The sizes are swapped first when the loc's own angle is odd. The new angle is (angle + r) & 3.
  - Copied locs are base locs: they act as the cache map of that tile. Clicks validate against them, and doors and
    other dynamic changes layer on top. No zone update is sent for them, because the client draws them from the
    region.
- **IN4: region rebuild.** A player inside an instance gets REBUILD_REGION_V2 instead of REBUILD_NORMAL. It carries
  4 levels × 13 × 13 reference zones around the player's zone: (template zone x, z, level, rotation) or none.
  - It is sent when the player nears the edge of the build area (as for normal rebuilds) and whenever the instance
    changes: a zone copied or cleared bumps its version, and players inside are rebuilt with `reload` on the next info
    pass.
  - Rev 242 sends no XTEA keys (RSProt `RebuildRegionV2Encoder`).
- **IN5: teardown.** Destroying an instance clears its zones (collision, base locs and dynamic changes) and frees its
  squares for reuse. A player found in instance space with no live instance is moved to the world spawn. That covers
  logging in inside a torn-down house.

- **IN6: off-thread build.** `World.buildInstance` allocates the instance on the world thread and prepares its zones on
  a builder pool: each (template zone, rotation) is turned once into collision bits and zone-local locs and cached for
  every later instance. The plan attaches in the pre-tick of the **next** tick, in request order; if it is not ready
  the world thread waits for it, so the attach tick is always request tick + 1 (determinism, replay). The last attach
  time is in `TickMetrics.instanceAttachNanos` (budget 1 ms, raids brief §7). Small builds (houses) may still copy
  zone by zone on the world thread.
- **IN7: whole squares and template coordinates.** A template map square can be copied whole, turned 0-3 times: zone
  positions rotate inside the square with the same formula as tiles (IN3, over 8 × 8 zones) and each zone turns too.
  Content writes coordinates in template space; `fromTemplate` maps a template tile (and footprint) to the instance
  and `toTemplate` maps back, rotation-aware (side 64: (x, z) → r1 (z, 63 − x − (w − 1)), r2 (63 − x − (w − 1),
  63 − z − (l − 1)), r3 (63 − z − (l − 1), x)).
- **IN8: lost instance.** A player found in instance space with no live instance (a restart, a torn-down raid, a login
  inside one) goes to the exit content saved for them (`PlayerRef.setInstanceExit`, saved key `instance.exit`, cleared
  on use) or else the world spawn, and content gets `InstanceLost(player, hadExit)` to apply its leave rules (raid
  items, wipe rules).
- **IN9: guaranteed teardown.** `World.destroyInstance` removes every NPC standing or spawned inside (live, dead or
  waiting to respawn), every ground obj in its zones, then its zones, collision and locs, and frees the space. Players
  still inside follow IN8 on the next info pass.
- **IN10: per-instance NPC stats.** An NPC can carry its own base levels and max hit (raid scaling) without changing
  the shared NPC config: hitpoints max, stat restore and drains measure against them; they survive respawns and are
  cleared by a stat-restoring transform (content re-applies them per phase). The NPC attack uses the own max hit
  before the combat data's.
- Size: up to 8 × 8 map squares per instance (TFA spans 3 × 5).

## OPEN
- **OPEN-IN2:** template zones whose locs cross a zone edge (a 2×2 loc on the last row) put collision into the
  neighbouring instance zone only if that zone has been copied.
- **OPEN-IN1 (closed by IN9).**
- **OPEN-IN3:** instances are per world and not persisted; content rebuilds them (a house is rebuilt from saved room
  data each visit); a player saved inside one follows IN8 after a restart.
