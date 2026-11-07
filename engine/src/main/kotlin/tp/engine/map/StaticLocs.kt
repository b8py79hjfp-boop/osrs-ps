package tp.engine.map

import tp.cache.config.TypeList
import tp.cache.config.loc.LocType
import tp.cache.map.GameMap
import tp.cache.map.MapSquare
import tp.cache.map.TileSettings
import tp.engine.interact.InteractionTarget
import tp.util.CoordGrid

/**
 * Read-only index of the cache's map locs, used to validate loc clicks (brief §12: the client names a loc id and tile;
 * the server must find that exact loc there) and to build its interaction target. Shared by every world in the JVM.
 * Dynamic loc changes (doors, depleted rocks) layer on top later via zone updates.
 */
class StaticLocs private constructor(
    private val squares: Map<Int, MapSquare>,
    private val types: TypeList<LocType>,
) {
    /**
     * The loc [id] whose origin is ([x], [z]) as seen from [level], or null. Bridges: a loc stored on level L+1 over a
     * `LINK_BELOW` tile is seen on level L (collision.md bridge rule; client applies the same shift).
     */
    fun find(level: Int, x: Int, z: Int, id: Int): InteractionTarget.Loc? {
        val square = squares[squareId(x, z)] ?: return null
        val lx = x and LOCAL_MASK
        val lz = z and LOCAL_MASK
        val bridge = level + 1 < MapSquare.LEVELS && (square.settings(1, lx, lz) and TileSettings.LINK_BELOW) != 0
        for (i in 0 until square.locCount) {
            if (square.locIds[i] != id) continue
            val info = square.locInfo[i]
            if (MapSquare.locX(info) != lx || MapSquare.locZ(info) != lz) continue
            val stored = MapSquare.locLevel(info)
            if (stored != level && !(bridge && stored == level + 1)) continue
            val type = types[id] ?: return null
            return InteractionTarget.Loc(
                CoordGrid(x, z, level),
                id,
                MapSquare.locShape(info),
                MapSquare.locAngle(info),
                type.width,
                type.length,
                type.forceApproach,
            )
        }
        return null
    }

    /** The cache loc whose origin is the tile and whose shape is on [layer] (DynamicLocs layers), or null. */
    fun atLayer(level: Int, x: Int, z: Int, layer: Int): InteractionTarget.Loc? {
        val square = squares[squareId(x, z)] ?: return null
        val lx = x and LOCAL_MASK
        val lz = z and LOCAL_MASK
        val bridge = level + 1 < MapSquare.LEVELS && (square.settings(1, lx, lz) and TileSettings.LINK_BELOW) != 0
        for (i in 0 until square.locCount) {
            val info = square.locInfo[i]
            if (MapSquare.locX(info) != lx || MapSquare.locZ(info) != lz) continue
            val stored = MapSquare.locLevel(info)
            if (stored != level && !(bridge && stored == level + 1)) continue
            if (DynamicLocs.layerOf(MapSquare.locShape(info)) != layer) continue
            return find(level, x, z, square.locIds[i])
        }
        return null
    }

    /** Whether level-0 tile ([x], [z]) is sea (sailing-movement.md SM1: sea overlays; boats sail only on sea). */
    fun isSea(x: Int, z: Int): Boolean = squares[squareId(x, z)]?.isSea(x and LOCAL_MASK, z and LOCAL_MASK) ?: false

    /** Whether the cache has map square ([squareX], [squareZ]) (instance space must avoid real squares). */
    fun hasSquare(squareX: Int, squareZ: Int): Boolean = squares.containsKey((squareX shl SQUARE_AXIS_BITS) or squareZ)

    /**
     * The cache contents of zone ([zoneX], [zoneZ]) as seen from [level] (bridge rule applied), for copying into an
     * instance: per tile its [ZoneTemplate.BLOCKED] / [ZoneTemplate.ROOF] bits, and every loc whose origin is in the zone.
     */
    fun zone(level: Int, zoneX: Int, zoneZ: Int): ZoneTemplate {
        val tiles = IntArray(ZONE_TILES)
        val locs = ArrayList<InteractionTarget.Loc>()
        val baseX = zoneX shl ZONE_SHIFT
        val baseZ = zoneZ shl ZONE_SHIFT
        val square = squares[squareId(baseX, baseZ)] ?: return ZoneTemplate(tiles, locs)
        val zx = baseX and LOCAL_MASK
        val zz = baseZ and LOCAL_MASK
        for (dx in 0 until ZONE_SIZE) {
            for (dz in 0 until ZONE_SIZE) {
                val bridged = (square.settings(1, zx + dx, zz + dz) and TileSettings.LINK_BELOW) != 0
                val stored = if (bridged) level + 1 else level
                if (stored >= MapSquare.LEVELS) continue
                val settings = square.settings(stored, zx + dx, zz + dz)
                var bits = 0
                if (settings and TileSettings.BLOCK_MAP_SQUARE != 0) bits = bits or ZoneTemplate.BLOCKED
                if (settings and TileSettings.REMOVE_ROOFS != 0) bits = bits or ZoneTemplate.ROOF
                tiles[dx * ZONE_SIZE + dz] = bits
            }
        }
        for (i in 0 until square.locCount) {
            val info = square.locInfo[i]
            val lx = MapSquare.locX(info)
            val lz = MapSquare.locZ(info)
            if (lx - zx !in 0 until ZONE_SIZE || lz - zz !in 0 until ZONE_SIZE) continue
            val loc = find(level, baseX + lx - zx, baseZ + lz - zz, square.locIds[i]) ?: continue
            if (loc.shape == MapSquare.locShape(info) && loc.angle == MapSquare.locAngle(info)) locs += loc
        }
        return ZoneTemplate(tiles, locs)
    }

    /** One cache zone for instancing: [tiles] indexed `dx * 8 + dz`, [locs] at their cache coordinates. */
    class ZoneTemplate(val tiles: IntArray, val locs: List<InteractionTarget.Loc>) {
        companion object {
            const val BLOCKED = 1
            const val ROOF = 2
        }
    }

    companion object {
        private const val ZONE_SHIFT = 3
        private const val ZONE_SIZE = 8
        private const val ZONE_TILES = ZONE_SIZE * ZONE_SIZE
        private const val SQUARE_SHIFT = 6
        private const val SQUARE_AXIS_BITS = 8
        private const val LOCAL_MASK = 0x3F

        private fun squareId(x: Int, z: Int): Int = ((x shr SQUARE_SHIFT) shl SQUARE_AXIS_BITS) or (z shr SQUARE_SHIFT)

        fun build(map: GameMap, types: TypeList<LocType>): StaticLocs = StaticLocs(map.squares.associateBy { it.key.id }, types)

        /** No locs (scenario runner / test 2 without a map). */
        val EMPTY: StaticLocs = StaticLocs(emptyMap(), TypeList(emptyArray<LocType?>()))
    }
}
