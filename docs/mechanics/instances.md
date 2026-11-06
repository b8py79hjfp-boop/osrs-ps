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

## OPEN

- **OPEN-IN1:** ground items and NPCs left in a destroyed instance are not removed by the engine; content must
  despawn its own NPCs. Ground items expire normally.
- **OPEN-IN2:** template zones whose locs cross a zone edge (a 2×2 loc on the last row) put collision into the
  neighbouring instance zone only if that zone has been copied.
- **OPEN-IN3:** instances are per world and not persisted; content rebuilds them (a house is rebuilt from saved room
  data each visit).
