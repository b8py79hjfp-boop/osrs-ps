package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.util.CoordGrid

/**
 * Tile arithmetic over a run's generated instance (client-contract 6.1-6.6): cells are 32 x 32 tiles at
 * (col * 32, row * 32) of plane 3 - floor; the Olm square sits [CoxLayout.OLM_DX] squares east.
 */
internal object CoxMap {
    private const val SQUARE = 64
    private const val LAST = CoxLayout.CELL - 1
    private const val MID = CoxLayout.CELL / 2
    private const val NEAR = 6
    private const val LATERAL = 8
    private const val PASSAGE_DEPTH = 2

    /** The Olm square's template south-west tile (client-contract 6.6). */
    private const val OLM_X0 = 3200
    private const val OLM_Z0 = 5696

    fun base(run: RaidRun): CoordGrid = requireNotNull(run.instance) { "cox run ${run.id.value}: no instance" }.base

    /** Template-cell tile ([tx], [tz]) of [cell] as an instance tile (the cell's rotation applied). */
    fun tile(run: RaidRun, cell: CoxCell, tx: Int, tz: Int): CoordGrid {
        val (lx, lz) = CoxLayout.local(cell, tx, tz)
        return inCell(run, cell, lx, lz)
    }

    /** Instance-frame cell-local tile ([lx], [lz]) of [cell]. */
    fun inCell(run: RaidRun, cell: CoxCell, lx: Int, lz: Int): CoordGrid {
        val b = base(run)
        return CoordGrid(b.x + cell.localX + lx, b.z + cell.localZ + lz, cell.plane)
    }

    /** The Olm arena tile showing template tile [t]. */
    fun olm(run: RaidRun, t: CoordGrid): CoordGrid {
        val b = base(run)
        return CoordGrid(b.x + CoxLayout.OLM_DX * SQUARE + (t.x - OLM_X0), b.z + (t.z - OLM_Z0), t.level)
    }

    /** Whether [c] lies in the Olm square. */
    fun inOlm(run: RaidRun, c: CoordGrid): Boolean {
        val b = base(run)
        val x = c.x - b.x - CoxLayout.OLM_DX * SQUARE
        val z = c.z - b.z
        return x in 0 until SQUARE && z in 0 until SQUARE
    }

    /** The cell [c] stands in, or null. */
    fun cellAt(plan: CoxLayoutPlan, run: RaidRun, c: CoordGrid): CoxCell? {
        val b = base(run)
        val lx = c.x - b.x
        val lz = c.z - b.z
        if (lx < 0 || lz < 0) return null
        val floor = CoxLayout.TOP_PLANE - c.level
        return plan.cells.firstOrNull { it.floor == floor && it.col == lx / CoxLayout.CELL && it.row == lz / CoxLayout.CELL }
    }

    /**
     * Where a player arrives in [cell] through its [side]: the first walkable tile going in from the middle of that edge
     * (the edges themselves are blocked, client-contract 6.5; OPEN-CX31: the corridors are not in the cache).
     */
    fun arrival(any: EntityRef, run: RaidRun, cell: CoxCell, side: Int): CoordGrid {
        // From two tiles in: a spawned passage (2 deep) fits between the edge and the arrival tile.
        for (depth in PASSAGE_DEPTH until CoxLayout.CELL) {
            for (k in 0..LATERAL) {
                for (sign in intArrayOf(1, -1)) {
                    val (lx, lz) = edgeTile(side, depth, k * sign)
                    if (lx !in 0..LAST || lz !in 0..LAST) continue
                    val t = inCell(run, cell, lx, lz)
                    if (any.isWalkable(t)) return t
                }
            }
        }
        return inCell(run, cell, MID, MID)
    }

    /** The cell-local tile [depth] tiles in from the middle of edge [side], moved [lateral] tiles along it. */
    private fun edgeTile(side: Int, depth: Int, lateral: Int): Pair<Int, Int> =
        when (side) {
            0 -> MID + lateral to LAST - depth
            1 -> LAST - depth to MID + lateral
            2 -> MID + lateral to depth
            else -> depth to MID + lateral
        }

    /**
     * A spawned 3 x 2 passage on [side] of [cell] in front of arrival tile [w]: its south-west tile and angle (N / S 0,
     * E / W 1, so the long side runs along the edge), on the two tiles between [w] and the edge.
     */
    fun passageAt(run: RaidRun, cell: CoxCell, side: Int, w: CoordGrid): Pair<CoordGrid, Int> {
        val b = base(run)
        val lx = w.x - b.x - cell.localX
        val lz = w.z - b.z - cell.localZ
        val (px, pz) =
            when (side) {
                0 -> (lx - 1) to (lz + 1)
                1 -> (lx + 1) to (lz - 1)
                2 -> (lx - 1) to (lz - PASSAGE_DEPTH)
                else -> (lx - PASSAGE_DEPTH) to (lz - 1)
            }
        return inCell(run, cell, px.coerceIn(0, LAST - 2), pz.coerceIn(0, LAST - 2)) to (side and 1)
    }

    /** The walkable tile nearest [at] (within [NEAR] tiles), else [at]. */
    fun freeNear(any: EntityRef, at: CoordGrid): CoordGrid {
        if (any.isWalkable(at)) return at
        for (r in 1..NEAR) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                    val t = CoordGrid(at.x + dx, at.z + dz, at.level)
                    if (any.isWalkable(t)) return t
                }
            }
        }
        return at
    }

    /** The loc [id] inside [cell] (centrepiece shapes), or null. */
    fun findLoc(any: EntityRef, run: RaidRun, cell: CoxCell, id: Int): LocRef? {
        for (lx in 0..LAST) {
            for (lz in 0..LAST) {
                val t = inCell(run, cell, lx, lz)
                for (shape in LOC_SHAPES) any.locAt(t, shape)?.takeIf { it.id == id }?.let { return it }
            }
        }
        return null
    }

    /** All locs [id] of [cell]. */
    fun findLocs(any: EntityRef, run: RaidRun, cell: CoxCell, id: Int): List<LocRef> {
        val out = ArrayList<LocRef>()
        for (lx in 0..LAST) {
            for (lz in 0..LAST) {
                val t = inCell(run, cell, lx, lz)
                for (shape in LOC_SHAPES) any.locAt(t, shape)?.takeIf { it.id == id && it.coord == t }?.let { out += it }
            }
        }
        return out
    }

    /** Centrepiece loc shapes (10 plain, 11 diagonal). */
    private val LOC_SHAPES = intArrayOf(10, 11)
}
