package tp.content.sailing

import tp.plugin.DbRowRef
import tp.util.CoordGrid

// Cell readers shared by the Sailing table readers. A db column holds `fieldCount` repetitions of a tuple; the plugin-api
// `DbRowRef.int(column, field, component)` addresses one cell, `field` being the repetition and `component` the tuple slot.

/** Int cell of [column], 0 when the row leaves it unset (stats columns: unset means the part gives none of it). */
internal fun DbRowRef.zero(column: Int): Int = int(column) ?: 0

/** Boolean cell (cache type `boolean`, stored as 0 or 1) of [column]; unset reads false. */
internal fun DbRowRef.flag(column: Int): Boolean = int(column) == 1

/** Component [component] of every repetition of [column], in order (a list column such as a row-reference list). */
internal fun DbRowRef.ints(column: Int, component: Int = 0): List<Int> {
    val out = ArrayList<Int>()
    var field = 0
    while (true) {
        out += int(column, field, component) ?: break
        field++
    }
    return out
}

/** Coordinate cell of [column] (cache type `coord`, same packing as [CoordGrid]), or null if unset. */
internal fun DbRowRef.coord(column: Int, field: Int = 0, component: Int = 0): CoordGrid? =
    int(column, field, component)?.let { CoordGrid(it) }

/** A (obj, quantity) material list column (cache tuple `obj, int`). */
internal fun DbRowRef.materials(column: Int): List<Material> {
    val out = ArrayList<Material>()
    var field = 0
    while (true) {
        val obj = int(column, field, MATERIAL_OBJ) ?: break
        out += Material(obj, int(column, field, MATERIAL_QUANTITY) ?: 1)
        field++
    }
    return out
}

/** Tuple slot of the obj in a material cell. */
private const val MATERIAL_OBJ = 0

/** Tuple slot of the quantity in a material cell. */
private const val MATERIAL_QUANTITY = 1

/** Slots of a loc placement tuple. The tuple is (coord, loc, angle, shape) or (loc, coord, angle, shape) depending on the table. */
internal enum class PlacementLayout(val coordSlot: Int, val locSlot: Int) {
    /** `coord, loc, angle, shape` (keel, flag, brazier, trim, steering). */
    COORD_FIRST(0, 1),

    /** `loc, coord, angle, shape` (sail, sail pattern). */
    LOC_FIRST(1, 0),
}

/** Tuple slot of the loc angle in a placement. */
private const val PLACEMENT_ANGLE = 2

/** Tuple slot of the loc shape in a placement. */
private const val PLACEMENT_SHAPE = 3

/** The loc placement tuple at [column] in the given [layout], or null if unset. */
internal fun DbRowRef.placement(column: Int, layout: PlacementLayout): LocPlacement? {
    val loc = int(column, 0, layout.locSlot) ?: return null
    val at = coord(column, 0, layout.coordSlot) ?: return null
    return LocPlacement(loc, at, zero0(column, PLACEMENT_ANGLE), zero0(column, PLACEMENT_SHAPE))
}

private fun DbRowRef.zero0(column: Int, component: Int): Int = int(column, 0, component) ?: 0
