# Static map collision

How the base collision map is built from cache data at boot. Code: `cache/src/main/kotlin/tp/cache/map/GameMap.kt`
(`CollisionBuilder`) and `LocCollision.kt`. Flag bits are rsmod-pathfinder's `CollisionFlag` (vendored module
`routefinder/`), which mirror the client's `CollisionMap` flags.

This is static data, not a tick-timeline mechanic. Dynamic changes (doors opening, locs added or removed) reuse
`LocCollision.toggle` in Phase 3.

## Sources

| # | Source | What it supports |
|---|---|---|
| S1 | RuneLite 1.13.6 `cache/.../region/RegionLoader.java` (`loadMapDef`, `loadLocDef`) | unnamed archive-5 layout: group = square id, file 0 terrain, file 1 locs |
| S2 | RuneLite 1.13.6 `cache/.../loaders/MapLoader.java`, `LocationsLoader.java` | terrain and loc file encodings |
| S3 | RuneLite 1.13.6 `cache/.../MapImageDumper.java` lines 322–332, 1325–1326 | bridge = `getTileSetting(1, x, y) & 2` (level 1 checked for every level) |
| S4 | LostCity engine `CollisionManager.ts` (https://gist.github.com/ultraviolet-jordan/b0a0b0ff5be8a0cc80cd12b266a43372) | land collision bit 0x1, bridge 0x2 read from level 1, roof 0x4; loc collision level = `bridged ? level - 1 : level`, skipped below 0 |
| S5 | RS Mod `engine/game/.../map/collision/CollisionFlagMapExtensions.kt`, `api/cache/.../types/loc/LocTypeDecoder.kt`, `engine/routefinder/.../flag/CollisionFlag.kt` (commit fa13b3f, ISC) | loc shape → flags, wall offsets, ground-decor rule, projectile/route-blocker flags, loc field names, `-1` default for unallocated zones |
| S6 | osrs-docs.com "Entity Collision" | **not yet read**: host unreachable from the dev environment (see OPEN-3) |

## Rules

1. **Map squares.** In the live (unnamed) layout a group id is the square id `x << 8 | z`; file 0 is terrain, file 1
   is locs (S1). *Our observation, not a source fact:* map-square groups in 2757 have file ids exactly `[0..4]`;
   we treat only those as squares. Corroboration: RuneLite loops `i < MAX_REGION` with `MAX_REGION = 25287`
   (`RegionLoader.java:46,70`), which excludes exactly the one odd group we skip. *Our design choice:* every zone of
   a decoded square is allocated walkable (0) on all 4 levels. Unallocated tiles read as fully blocked (`-1`, S5
   `CollisionFlagMap.DEFAULT_COLLISION_FLAG`).
2. **Tile settings** (S2, S4): opcode 50–81 sets `settings = opcode - 49`. Bit `0x1` = blocked, `0x2` = bridge,
   `0x4` = remove roofs (bit names: S5 `MapTileDefinition`). Bits `0x8` (visible below) and `0x10` (not low detail)
   affect rendering only. Note: S4 is an older revision that reads 1-byte overlay ids, so S2 is authoritative for the
   242 terrain encoding.
3. **Bridge level shift** (S3, S4): a tile column is *bridged* when the **level-1** tile has bit `0x2`. Then every
   collision write for that (x, z) — tile flags and locs, at any level — goes to `level - 1`, and writes that would
   land below level 0 are dropped. So the water under a bridge (level 0, blocked) is dropped, and the bridge deck
   (level 1) becomes walkable at level 0.
   - Mechanism note: S4 drops locs below 0 the same way, but handles floor flags by explicitly clearing level-0 land
     collision when the level-1 tile is bridged. The net result for the static map is identical; S4 quotes were
     read through a summarising fetch, so re-check against the raw gist when reachable.
   - Deliberate divergence from RS Mod, which for locs checks the tile above (falling back to the loc tile's own
     flags) and for floor blocking uses the tile's own bit with no below-0 guard. We follow S3 and S4.
4. **Floor flags**: blocked → `BLOCK_WALK`; remove-roofs → `ROOF`. `ROOF` is a custom server flag (S5
   `CollisionFlag.kt`: "used to bind NPCs to not leave the buildings they spawn in"). It isn't client collision;
   routing ignores it. Its use for NPC binding is a Phase 3 decision.
5. **Locs** (S5): nothing is added when `blockWalk == 0`. Otherwise, by shape:
   - `0` wall straight, `1` diagonal corner, `2` L, `3` square corner: wall edge flags on the loc tile plus the
     opposite edge on each neighbour across that edge (one neighbour for straights and corners, two for L shapes;
     offsets in S5 `toggleWall`). Angles: 0 W, 1 N, 2 E, 3 S. Straight angle a → edge W/N/E/S. Corner angle a
     → NW/NE/SE/SW. L angle a → (W+N)/(N+E)/(E+S)/(S+W). Projectile-blocker variants are added too when `blockRange`.
   - `4`–`8` wall decor: no collision.
   - `9` diagonal wall, `10`/`11` centrepieces, `12`–`21` roofs: `LOC` on the `width × length` footprint (swapped
     when the angle is 1 or 3), plus `LOC_PROJ_BLOCKER` if `blockRange` and `LOC_ROUTE_BLOCKER` if
     `breakRouteFinding`.
   - `22` ground decor: `GROUND_DECOR` only when `blockWalk == 1`.
6. **Loc config fields** (S2 `ObjectLoader`/`ObjectDefinition`; names from S5 `LocTypeDecoder`): `blockWalk`
   (RuneLite `interactType`) defaults to 2 and `blockRange` to true. Op 17 sets `blockWalk = 0` and clears
   `blockRange`, op 18 clears `blockRange`, op 27 sets `blockWalk = 1`, op 74 sets `breakRouteFinding` (RuneLite
   `hollow`), op 69 reads one byte as the force-approach mask (RuneLite `blockingMask`; RuneLite reads it signed, RS Mod
   unsigned — we read unsigned, only the low 4 bits are used).

## OPEN

- **OPEN-1:** terrain files in cache 2757 end with a u8 trailer: `0` on 2,948 squares, `1` plus a 9,563-byte
  section on square (64, 73). No reference decodes this section. We keep only its size. If it carries collision
  (e.g. Sailing water levels), boats/sea areas will be wrong there. Flag: none yet (data recorded per square).
- **OPEN-2:** archive-5 group 25287 has 37 three-byte files and isn't a map square; it's skipped. Files 2–4 of the
  real squares (typically 228, 2 and 1 bytes) aren't decoded either. They're plausibly the newer per-square npc/obj/
  area spawn data (RS Mod's `n`/`o`/`a` groups), unconfirmed.
- **OPEN-3:** osrs-docs "Entity Collision" (S6) hasn't been checked. Verify rules 3 and 5 against it when the host is
  reachable.
- **OPEN-4:** RS Mod adds route-blocker flags only for ground footprints, never for walls with
  `breakRouteFinding`. We follow it; unverified against the client.

## Verification so far

- Verifier pass (2026-11-05, independent of the code): all rule 5 edge/angle/offset entries, the width/length
  swap, the ground-decor condition and rule 6 opcodes CONFIRMED against S2/S5; rule 3's level-1 check CONFIRMED
  against S3/S4. Wording and attribution fixes applied from its report.

- Every map square in cache 2757 decodes, with 5,021,872 locs, all loc ids resolved.
- An ASCII render of Lumbridge (3200–3263, 3200–3239, level 0) shows the castle walls and towers, the River Lum
  blocked, and the bridge deck across it (y 3225–3227) walkable with railing edges. Water under the deck is
  correctly absent.
- To do (Phase 2): walk the RuneLite client over bridges, through doors and around diagonal walls, and compare the
  server routes with the client's.
