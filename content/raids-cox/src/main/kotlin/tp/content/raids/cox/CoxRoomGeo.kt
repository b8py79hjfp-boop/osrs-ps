package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.plugin.EntityRef
import tp.util.CoordGrid

/**
 * A cell's walkable floor, flood-filled from its entry (OPEN-CX32: the server's NPC spawn table is not in the cache, so
 * spawn tiles are chosen on this floor): its centre, spots spread across it, its four corners and the tiles beside the
 * exit doorway.
 */
internal class CoxRoomGeo(private val any: EntityRef, private val run: RaidRun, private val cell: CoxCell) {
    private val entry = CoxMap.arrival(any, run, cell, if (cell.entrySide >= 0) cell.entrySide else cell.exitSide)
    private val exit = CoxMap.arrival(any, run, cell, if (cell.exitSide >= 0) cell.exitSide else cell.entrySide)
    val floor: List<CoordGrid> = flood()
    private val inFloor = floor.toHashSet()

    private fun flood(): List<CoordGrid> {
        val out = ArrayList<CoordGrid>()
        val seen = HashSet<CoordGrid>()
        val queue = ArrayDeque<CoordGrid>()
        queue += entry
        seen += entry
        while (queue.isNotEmpty()) {
            val t = queue.removeFirst()
            out += t
            for ((dx, dz) in STEPS) {
                val n = CoordGrid(t.x + dx, t.z + dz, t.level)
                if (n in seen || !inCell(n) || !any.canStep(t, dx, dz)) continue
                seen += n
                queue += n
            }
        }
        return out
    }

    private fun inCell(t: CoordGrid): Boolean {
        val o = CoxMap.inCell(run, cell, 0, 0)
        return t.x - o.x in 0 until CoxLayout.CELL && t.z - o.z in 0 until CoxLayout.CELL
    }

    /** Whether a [size]-tile NPC fits with its south-west tile on [t]. */
    private fun fits(t: CoordGrid, size: Int): Boolean {
        for (dx in 0 until size) for (dz in 0 until size) if (CoordGrid(t.x + dx, t.z + dz, t.level) !in inFloor) return false
        return true
    }

    private fun far(t: CoordGrid, from: CoordGrid): Int = maxOf(kotlin.math.abs(t.x - from.x), kotlin.math.abs(t.z - from.z))

    /** The fitting tile nearest the floor's middle. */
    fun centre(size: Int): CoordGrid {
        val cx = floor.sumOf { it.x } / maxOf(1, floor.size)
        val cz = floor.sumOf { it.z } / maxOf(1, floor.size)
        val mid = CoordGrid(cx - size / 2, cz - size / 2, entry.level)
        return floor.filter { fits(it, size) }.minByOrNull { far(it, mid) } ?: entry
    }

    /**
     * [n] fitting spots spread over the floor (each as far as possible from the entry and the spots before it), away
     * from the entry so a raider walks in before the fight; [fromEntry] false: the spot farthest from the entry first.
     */
    fun spread(n: Int, size: Int, fromEntry: Boolean = true): List<CoordGrid> {
        val candidates = floor.filter { fits(it, size) && far(it, entry) >= MIN_FROM_ENTRY }.ifEmpty { floor.filter { fits(it, size) } }
        val out = ArrayList<CoordGrid>()
        val anchors = arrayListOf(entry)
        repeat(n) {
            val pick =
                candidates
                    .filter { c -> out.none { far(it, c) < size } }
                    .maxByOrNull { c -> if (fromEntry && out.isEmpty()) -far(c, centre(size)) else anchors.minOf { far(c, it) } }
                    ?: return out.ifEmpty { listOf(entry) }
            out += pick
            anchors += pick
        }
        return out
    }

    /** Four spots in the corners of the floor (Vasa's crystals). */
    fun corners(size: Int): List<CoordGrid> {
        val fit = floor.filter { fits(it, size) }
        if (fit.isEmpty()) return emptyList()
        return listOf(
            fit.minBy { it.x + it.z },
            fit.maxBy { it.x - it.z },
            fit.maxBy { it.x + it.z },
            fit.minBy { it.x - it.z },
        ).distinct()
    }

    /** The fitting spot nearest [at] (on the floor; [walkable] false: anywhere in the cell, e.g. a portal in the wall). */
    fun spotNear(at: CoordGrid, size: Int, walkable: Boolean = true): CoordGrid {
        if (!walkable) return at
        return floor.filter { fits(it, size) }.minByOrNull { far(it, at) } ?: at
    }

    /** Two spots on either side of the exit (the Guardians stand by the passage they guard). */
    fun flankExit(size: Int): List<CoordGrid> {
        val near = floor.filter { fits(it, size) && far(it, exit) in FLANK_MIN..FLANK_MAX }
        val first = near.minByOrNull { far(it, exit) } ?: return spread(2, size)
        val second = near.filter { far(it, first) >= size + 1 }.minByOrNull { far(it, exit) } ?: return listOf(first) + spread(1, size)
        return listOf(first, second)
    }

    private companion object {
        val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        const val MIN_FROM_ENTRY = 6
        const val FLANK_MIN = 1
        const val FLANK_MAX = 4
    }
}
