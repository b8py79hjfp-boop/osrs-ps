package tp.util

/**
 * A world tile: x, z (0..16383) and level (0..3), packed into one Int. Layout matches the client's packed coord
 * (`level << 28 | x << 14 | z`), which RSProt and rsmod-pathfinder also use.
 */
@JvmInline
value class CoordGrid(val packed: Int) {
    val x: Int get() = (packed ushr X_SHIFT) and AXIS_MASK
    val z: Int get() = packed and AXIS_MASK
    val level: Int get() = (packed ushr LEVEL_SHIFT) and LEVEL_MASK

    fun translate(dx: Int, dz: Int, dLevel: Int = 0): CoordGrid = CoordGrid(x + dx, z + dz, level + dLevel)

    /** Chebyshev distance on x/z, ignoring level (the distance OSRS uses for most range checks). */
    fun chebyshev(other: CoordGrid): Int = maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    override fun toString(): String = "CoordGrid($x, $z, $level)"

    companion object {
        private const val X_SHIFT = 14
        private const val LEVEL_SHIFT = 28
        private const val AXIS_MASK = 0x3FFF
        private const val LEVEL_MASK = 0x3

        operator fun invoke(x: Int, z: Int, level: Int = 0): CoordGrid {
            require(x in 0..AXIS_MASK && z in 0..AXIS_MASK && level in 0..LEVEL_MASK) { "Coord out of range: $x, $z, $level" }
            return CoordGrid((level shl LEVEL_SHIFT) or (x shl X_SHIFT) or z)
        }
    }
}
