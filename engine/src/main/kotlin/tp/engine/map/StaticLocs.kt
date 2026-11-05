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

    companion object {
        private const val SQUARE_SHIFT = 6
        private const val SQUARE_AXIS_BITS = 8
        private const val LOCAL_MASK = 0x3F

        private fun squareId(x: Int, z: Int): Int = ((x shr SQUARE_SHIFT) shl SQUARE_AXIS_BITS) or (z shr SQUARE_SHIFT)

        fun build(map: GameMap, types: TypeList<LocType>): StaticLocs = StaticLocs(map.squares.associateBy { it.key.id }, types)

        /** No locs (scenario runner / test 2 without a map). */
        val EMPTY: StaticLocs = StaticLocs(emptyMap(), TypeList(emptyArray<LocType?>()))
    }
}
