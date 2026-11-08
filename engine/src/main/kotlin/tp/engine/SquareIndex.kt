package tp.engine

import tp.engine.entity.Entity
import tp.util.CoordGrid

/**
 * Entities by map square (64 x 64 tiles, every level), kept up to date as they move (Entity.coord's setter), so radius
 * queries look at a few squares instead of every entity in the world (raids brief section 7: 300 instances with
 * thousands of NPCs made NPC aggression scan every player for every NPC). World thread only.
 */
internal class SquareIndex<T : Entity> {
    private val bySquare = HashMap<Int, ArrayList<T>>()

    fun add(e: T) {
        bySquare.getOrPut(key(e.coord)) { ArrayList(INITIAL) } += e
    }

    fun remove(e: T) {
        remove(e, key(e.coord))
    }

    fun moved(e: T, old: CoordGrid) {
        val from = key(old)
        val to = key(e.coord)
        if (from == to) return
        remove(e, from)
        bySquare.getOrPut(to) { ArrayList(INITIAL) } += e
    }

    /**
     * Adds to [out] every entity whose square overlaps the box of [radius] around ([x], [z]) (any level; the caller
     * checks the exact distance and level). False when the box spans too many squares: the caller scans everything.
     */
    fun collect(x: Int, z: Int, radius: Int, out: ArrayList<T>): Boolean {
        if (radius < 0) return true
        val sx0 = (x - radius) shr SQUARE_SHIFT
        val sx1 = (x + radius) shr SQUARE_SHIFT
        val sz0 = (z - radius) shr SQUARE_SHIFT
        val sz1 = (z + radius) shr SQUARE_SHIFT
        if ((sx1 - sx0 + 1) * (sz1 - sz0 + 1) > MAX_SQUARES) return false
        for (sx in sx0..sx1) {
            for (sz in sz0..sz1) {
                val list = bySquare[squareKey(sx, sz)] ?: continue
                out.addAll(list)
            }
        }
        return true
    }

    private fun remove(e: T, k: Int) {
        val list = bySquare[k] ?: return
        list.remove(e)
        if (list.isEmpty()) bySquare.remove(k)
    }

    private fun key(c: CoordGrid): Int = squareKey(c.x shr SQUARE_SHIFT, c.z shr SQUARE_SHIFT)

    private fun squareKey(sx: Int, sz: Int): Int = (sx shl SQUARE_BITS) or (sz and SQUARE_MASK)

    private companion object {
        const val SQUARE_SHIFT = 6
        const val SQUARE_BITS = 16
        const val SQUARE_MASK = 0xFFFF
        const val INITIAL = 4

        /** A box wider than 8 x 8 squares (radius about 250) is cheaper to answer with a full scan. */
        const val MAX_SQUARES = 64
    }
}
