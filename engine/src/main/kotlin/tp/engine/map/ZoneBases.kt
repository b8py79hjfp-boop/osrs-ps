package tp.engine.map

import tp.engine.interact.InteractionTarget

/**
 * One instance zone's base locs, packed (raids brief section 7 memory: about 8 bytes a loc instead of a Loc object, a
 * boxed key and a map node). Entry i is [packed] (tile in zone x 3 bits, z 3 bits, layer 2, shape 5, angle 2) and
 * [ids]; width, length and approach come from the loc type when a lookup builds the Loc, as StaticLocs does.
 */
internal class ZoneBases private constructor(private val packed: IntArray, private val ids: IntArray) {
    val size: Int get() = ids.size

    /** The entry on [layer] at zone tile ([x], [z]) (0..7), or -1. A zone holds a few dozen locs: a scan is enough. */
    fun indexOf(x: Int, z: Int, layer: Int): Int {
        val k = x or (z shl Z_SHIFT) or (layer shl LAYER_SHIFT)
        for (i in packed.indices) if (packed[i] and KEY_MASK == k) return i
        return -1
    }

    fun id(i: Int): Int = ids[i]

    fun shape(i: Int): Int = (packed[i] ushr SHAPE_SHIFT) and SHAPE_MASK

    fun angle(i: Int): Int = (packed[i] ushr ANGLE_SHIFT) and ANGLE_MASK

    /** Collects a zone's locs in placing order: a later loc on the same tile layer replaces the earlier one. */
    class Builder {
        private val byKey = LinkedHashMap<Int, Pair<Int, Int>>()

        /** Adds [loc] (its origin in this zone); true when the tile layer was free (only the first one's collision counts). */
        fun put(loc: InteractionTarget.Loc): Boolean {
            val k = (loc.coord.x and IN_ZONE) or ((loc.coord.z and IN_ZONE) shl Z_SHIFT) or (DynamicLocs.layerOf(loc.shape) shl LAYER_SHIFT)
            val p = k or (loc.shape shl SHAPE_SHIFT) or ((loc.angle and ANGLE_MASK) shl ANGLE_SHIFT)
            return byKey.put(k, p to loc.id) == null
        }

        fun build(): ZoneBases {
            val packed = IntArray(byKey.size)
            val ids = IntArray(byKey.size)
            var i = 0
            for ((p, id) in byKey.values) {
                packed[i] = p
                ids[i] = id
                i++
            }
            return ZoneBases(packed, ids)
        }
    }

    private companion object {
        const val IN_ZONE = 7
        const val Z_SHIFT = 3
        const val LAYER_SHIFT = 6
        const val KEY_MASK = 0xFF
        const val SHAPE_SHIFT = 8
        const val SHAPE_MASK = 0x1F
        const val ANGLE_SHIFT = 13
        const val ANGLE_MASK = 3
    }
}
