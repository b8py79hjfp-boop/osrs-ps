package tp.content.raids.cox

import tp.content.raids.RaidLayout
import tp.content.raids.RaidRng
import tp.plugin.InstanceSquare
import tp.plugin.InstanceZone
import tp.util.CoordGrid

/** What a cell holds. */
internal enum class CoxKind(val combat: Boolean = false, val puzzle: Boolean = false) {
    START,
    END,
    SCAVENGERS,
    SCAVENGERS_LARGE,
    FARMING_FISH,
    FARMING_BATS,
    TEKTON(combat = true),
    MUTTADILES(combat = true),
    GUARDIANS(combat = true),
    VESPULA(combat = true),
    SHAMANS(combat = true),
    VASA(combat = true),
    VANGUARDS(combat = true),
    MYSTICS(combat = true),
    CRABS(puzzle = true),
    ICE_DEMON(puzzle = true),
    TIGHTROPE(puzzle = true),
    THIEVING(puzzle = true),
}

/**
 * One placed room: grid cell ([col], [row]) of floor [floor] (0 = top, plane 3), its template cell (south-west tile,
 * level), the quarter turns it is copied with, and the sides (N 0, E 1, S 2, W 3, instance frame) it is entered and left
 * by (-1 = none).
 */
internal class CoxCell(
    val index: Int,
    val floor: Int,
    val col: Int,
    val row: Int,
    val kind: CoxKind,
    val template: CoordGrid,
    val rotation: Int,
    val entrySide: Int,
    val exitSide: Int,
) {
    /** The cell's south-west tile, relative to the instance's south-west corner. */
    val localX: Int get() = col * CoxLayout.CELL
    val localZ: Int get() = row * CoxLayout.CELL
    val plane: Int get() = CoxLayout.TOP_PLANE - floor
}

/** A run's generated chambers (cox.md CX8-CX16; client-contract 6). */
internal class CoxLayoutPlan(val code: String, val cells: List<CoxCell>, val floors: Int) {
    fun next(cell: CoxCell): CoxCell? = cells.getOrNull(cell.index + 1)

    fun previous(cell: CoxCell): CoxCell? = cells.getOrNull(cell.index - 1)
}

/**
 * The layout generator. A layout is one of RuneLite's 26 two-floor codes (cox.md CX12: rooms between start and end, in
 * path order, and the grid moves of the path), placed on the 4 x 2 grid of each floor; floor 2 starts in the cell
 * floor 1 ends in. Combat rooms follow one of the four rotation cycles (CX11): slot s (0-15 across the floors, column
 * s mod 4, row (s mod 8) > 3) holds cycle[(k + s) mod 8]. A cell is entered from its template south side, so a move in
 * direction d into it means rotation d, and it leaves by side (winding + rotation) & 3 with winding CCW 3, THRU 0, CW 1
 * (client-contract 6.2).
 */
internal object CoxLayout {
    const val CELL = 32
    const val TOP_PLANE = 3
    private const val COLS = 4
    private const val ROWS = 2
    private const val SLOTS_PER_FLOOR = COLS * ROWS
    private const val CYCLE = 8
    private const val NORTH = 0
    private const val EAST = 1
    private const val SOUTH = 2
    private const val WEST = 3
    private const val SIDES = 4

    /** RuneLite LayoutSolver (cox.md CX12), `floor1.floor2 - #path1#path2`. */
    val CODES =
        listOf(
            "FSCCP.PCSCF - #WNWSWN#ESEENW",
            "SCFPC.CSPCF - #WSWWNE#WSEENE",
            "SFCCS.PCPSF - #ENWWSW#ENESEN",
            "FSCCS.PCPSF - #WSEEEN#WSWNWS",
            "SCFPC.PCCSF - #WSEENE#WWWSEE",
            "SPCFC.CSPCF - #WWNEEE#WSWNWS",
            "FSCPC.CSCPF - #WNWWSE#EENWWW",
            "SCFPC.SCPCF - #NESENE#WSWWNE",
            "SPCFC.SCCPF - #ESENES#WWWNEE",
            "SCCFC.PSCSF - #EEENWW#WSEEEN",
            "SCPFC.CCPSF - #NWWWSE#WNEESE",
            "SPSFP.CCCSF - #NWSWWN#ESEENW",
            "SCCFP.CCSPF - #NESEEN#WSWNWS",
            "SCPFC.CSPCF - #NEEESW#WWNEEE",
            "SCFCP.CSCPF - #ENESEN#WWWSEE",
            "SCFCP.CCSPF - #ESEENW#ESWWNW",
            "SCPFC.CSPSF - #WWSEEE#NWSWWN",
            "SCPFC.PCSCF - #WNEEES#NWSWNW",
            "SCFCP.CSCFS - #ENEESW#ENWWSW",
            "SCSPF.CCSPF - #ESWWNW#ESENES",
            "SFCCPC.PCSCPF - #WSEENES#WWWNEEE",
            "SCFCPC.CSPCSF - #ESWWNWS#NESENES",
            "SFCCP.CSCPF - #WNEESE#NWSWWN",
            "FSPCC.PSCCF - #WWWSEE#ENWWSW",
            "FSCCP.PCSCF - #ENWWWS#NEESEN",
            "SCPFC.CCSSF - #NEESEN#WSWWNE",
        )

    /** RuneLite RotationSolver (cox.md CX11): the four combat cycles. */
    private val CYCLES =
        listOf(
            listOf(
                CoxKind.TEKTON,
                CoxKind.VASA,
                CoxKind.GUARDIANS,
                CoxKind.MYSTICS,
                CoxKind.SHAMANS,
                CoxKind.MUTTADILES,
                CoxKind.VANGUARDS,
                CoxKind.VESPULA,
            ),
            listOf(
                CoxKind.TEKTON,
                CoxKind.MUTTADILES,
                CoxKind.GUARDIANS,
                CoxKind.VESPULA,
                CoxKind.SHAMANS,
                CoxKind.VASA,
                CoxKind.VANGUARDS,
                CoxKind.MYSTICS,
            ),
            listOf(
                CoxKind.VESPULA,
                CoxKind.VANGUARDS,
                CoxKind.MUTTADILES,
                CoxKind.SHAMANS,
                CoxKind.MYSTICS,
                CoxKind.GUARDIANS,
                CoxKind.VASA,
                CoxKind.TEKTON,
            ),
            listOf(
                CoxKind.MYSTICS,
                CoxKind.VANGUARDS,
                CoxKind.VASA,
                CoxKind.SHAMANS,
                CoxKind.VESPULA,
                CoxKind.GUARDIANS,
                CoxKind.MUTTADILES,
                CoxKind.TEKTON,
            ),
        )
    private val PUZZLES = listOf(CoxKind.CRABS, CoxKind.ICE_DEMON, CoxKind.TIGHTROPE, CoxKind.THIEVING)

    /** Client contract 6.2-6.3: (template level, band z0) per room kind; the three variants at x 3264 / 3296 / 3328. */
    private val BANDS =
        mapOf(
            CoxKind.SCAVENGERS to (0 to 5216),
            CoxKind.SCAVENGERS_LARGE to (1 to 5216),
            CoxKind.SHAMANS to (0 to 5248),
            CoxKind.MYSTICS to (1 to 5248),
            CoxKind.GUARDIANS to (2 to 5248),
            CoxKind.VASA to (0 to 5280),
            CoxKind.TEKTON to (1 to 5280),
            CoxKind.VESPULA to (2 to 5280),
            CoxKind.VANGUARDS to (0 to 5312),
            CoxKind.MUTTADILES to (1 to 5312),
            CoxKind.ICE_DEMON to (0 to 5344),
            CoxKind.TIGHTROPE to (1 to 5344),
            CoxKind.CRABS to (2 to 5344),
            CoxKind.THIEVING to (0 to 5376),
            CoxKind.FARMING_FISH to (0 to 5440),
            CoxKind.FARMING_BATS to (1 to 5440),
        )
    private const val X_CCW = 3264
    private const val X_THRU = 3296
    private const val X_CW = 3328
    private const val LOBBY_Z = 5184
    private const val START1_Z = 5696
    private const val START2_Z = 5728
    private const val END1_Z = 5120
    private const val END2_Z = 5152
    private const val WINDING_CCW = 3
    private const val WINDING_CW = 1

    /** The Olm arena square (client-contract 6.6), copied beside the floors. */
    const val OLM_SQUARE_X = 50
    const val OLM_SQUARE_Z = 89
    const val OLM_DX = 2

    /** CX13 / OPEN-CX7 working assumption: the "small" patterns are the 5-room ones with 3 combat rooms. */
    fun isSmall(code: String): Boolean {
        val rooms = code.substringBefore(" - ")
        return rooms.split('.').all { it.length == FIVE_ROOMS } && rooms.count { it == 'C' } == SMALL_COMBAT
    }

    /**
     * Draws a regular-pool layout: small or medium with equal weight (CX13, OPEN-CX7 behind
     * [CoxConfig.poolSmallPatterns]), then a pattern of that size; a pattern that cannot be placed without a repeated
     * combat room falls through to the next one.
     */
    fun generate(rng: RaidRng): CoxLayoutPlan {
        val pool =
            if (CoxConfig.poolSmallPatterns) {
                val small = rng.next(2, "cox layout size") == 0
                CODES.filter { isSmall(it) == small }
            } else {
                CODES
            }
        val first = rng.next(pool.size, "cox layout")
        val cycle = CYCLES[rng.next(CYCLES.size, "cox cycle")]
        val offset = rng.next(CYCLE, "cox cycle offset")
        val puzzles = PUZZLES.shuffled(rng)
        val resources = List(MAX_FLOORS) { if (rng.next(2, "cox resource") == 0) CoxKind.FARMING_FISH else CoxKind.FARMING_BATS }
        for (i in pool.indices) {
            val code = pool[(first + i) % pool.size]
            place(code, cycle, offset, puzzles, resources)?.let { return it }
        }
        error("no cox layout fits the grid")
    }

    /**
     * CX13 / OPEN-CX7 working assumption for the Large pool (11 Nov 2020: "five combat and three puzzle rooms"): two
     * floors of six rooms that fill the 4 x 2 grids (start, six rooms, end), each with one farming and one scavenger
     * room, the 5 C / 3 P split across the floors at random. The room order follows the rules all 26 captured patterns
     * share: floor 1 opens with S or F, floor 2 opens with C or P and its last room is F, and no P follows a P
     * ([LARGE_ORDER_OK]). Paths are random Hamiltonian paths; floor 2 starts where floor 1 ended. Behind
     * [CoxConfig.largePoolGenerated].
     */
    fun generateLarge(rng: RaidRng): CoxLayoutPlan {
        val cycle = CYCLES[rng.next(CYCLES.size, "cox cycle")]
        val offset = rng.next(CYCLE, "cox cycle offset")
        val puzzles = PUZZLES.shuffled(rng)
        val resources = List(MAX_FLOORS) { if (rng.next(2, "cox resource") == 0) CoxKind.FARMING_FISH else CoxKind.FARMING_BATS }
        repeat(LARGE_TRIES) {
            val combat1 = LARGE_MIN_COMBAT1 + rng.next(LARGE_COMBAT1_CHOICES, "cox large split")
            val floor1 = largeFloor(rng, combat1, LARGE_C_P_PER_FLOOR - combat1, first = true) ?: return@repeat
            val floor2 =
                largeFloor(rng, LARGE_COMBAT - combat1, LARGE_PUZZLES - (LARGE_C_P_PER_FLOOR - combat1), first = false)
                    ?: return@repeat
            val starts = HAMILTONIAN.keys.toList()
            val start = starts[rng.next(starts.size, "cox large start")]
            val paths1 = HAMILTONIAN.getValue(start)
            val path1 = paths1[rng.next(paths1.size, "cox large path")]
            val end1 = path(start, path1)?.last() ?: return@repeat
            val paths2 = HAMILTONIAN.getValue(end1)
            val path2 = paths2[rng.next(paths2.size, "cox large path")]
            val code = "$floor1.$floor2 - #$path1#$path2"
            val cells =
                tryPlace(listOf(floor1, floor2), listOf(path1, path2), start.first, start.second, cycle, offset, puzzles, resources)
                    ?: return@repeat
            val kinds = cells.filter { it.kind.combat }.map { it.kind }
            if (kinds.distinct().size == kinds.size) return CoxLayoutPlan(code, cells, 2)
        }
        error("no cox large layout fits the grid")
    }

    /** One Large floor's room string with [combat] C and [puzzles] P, or null when the draw breaks the order rules. */
    private fun largeFloor(rng: RaidRng, combat: Int, puzzles: Int, first: Boolean): String? {
        val rooms = (List(combat) { 'C' } + List(puzzles) { 'P' } + listOf('S', 'F')).shuffled(rng).joinToString("")
        return rooms.takeIf { LARGE_ORDER_OK(it, first) }
    }

    /** The room-order rules every floor of RL's 26 patterns keeps (cox.md CX13). */
    private val LARGE_ORDER_OK: (String, Boolean) -> Boolean = { r, first ->
        "PP" !in r && if (first) r[0] == 'S' || r[0] == 'F' else (r[0] == 'C' || r[0] == 'P') && r.last() == 'F'
    }

    /** Every 7-move path that visits all eight cells of a 4 x 2 floor, by start cell. */
    private val HAMILTONIAN: Map<Pair<Int, Int>, List<String>> by lazy {
        val out = LinkedHashMap<Pair<Int, Int>, List<String>>()
        for (row in 0 until ROWS) {
            for (col in 0 until COLS) {
                val found = ArrayList<String>()

                fun walk(at: Pair<Int, Int>, seen: Set<Pair<Int, Int>>, moves: String) {
                    if (seen.size == SLOTS_PER_FLOOR) {
                        found += moves
                        return
                    }
                    for (m in "NESW") {
                        val (dx, dz) = step(m) ?: continue
                        val next = at.first + dx to at.second + dz
                        if (next.first !in 0 until COLS || next.second !in 0 until ROWS || next in seen) continue
                        walk(next, seen + next, moves + m)
                    }
                }
                walk(col to row, setOf(col to row), "")
                out[col to row] = found
            }
        }
        out
    }

    /** Lays [code] out with the drawn rotation; null when its path does not fit the 4 x 2 grids. */
    fun place(code: String, cycle: List<CoxKind>, offset: Int, puzzles: List<CoxKind>, resources: List<CoxKind>): CoxLayoutPlan? {
        val (rooms, paths) = code.split(" - ")
        val floorsRooms = rooms.split('.')
        val floorsPaths = paths.trim('#').split('#')
        for (startCol in 0 until COLS) {
            for (startRow in 0 until ROWS) {
                val cells = tryPlace(floorsRooms, floorsPaths, startCol, startRow, cycle, offset, puzzles, resources) ?: continue
                val combat = cells.filter { it.kind.combat }.map { it.kind }
                if (combat.distinct().size != combat.size) continue
                return CoxLayoutPlan(code, cells, floorsRooms.size)
            }
        }
        return null
    }

    /**
     * CX15 / OPEN-CX9: the fixed Challenge Mode layout: three floors of six rooms in the CM page's order, each laid as a
     * snake over its 4 x 2 grid (start, three rooms east, up, three rooms west, end); the next floor starts in the cell
     * the floor above ended in.
     */
    fun challenge(): CoxLayoutPlan {
        val floors =
            listOf(
                listOf(CoxKind.TEKTON, CoxKind.CRABS, CoxKind.SCAVENGERS, CoxKind.ICE_DEMON, CoxKind.SHAMANS, CoxKind.FARMING_FISH),
                listOf(
                    CoxKind.VANGUARDS,
                    CoxKind.THIEVING,
                    CoxKind.SCAVENGERS_LARGE,
                    CoxKind.VESPULA,
                    CoxKind.FARMING_BATS,
                    CoxKind.TIGHTROPE,
                ),
                listOf(CoxKind.GUARDIANS, CoxKind.VASA, CoxKind.SCAVENGERS, CoxKind.MYSTICS, CoxKind.MUTTADILES, CoxKind.FARMING_FISH),
            )
        // The snake from the start cell's row: three east, one north (or south), three west.
        val up = "EEENWWW"
        val down = "EEESWWW"
        val out = ArrayList<CoxCell>()
        var at = 0 to 0
        for ((floor, rooms) in floors.withIndex()) {
            val moves = if (at.second == 0) up else down
            val pos = path(at, moves) ?: error("cox challenge layout does not fit")
            at = pos.last()
            for ((k, p) in pos.withIndex()) {
                val inMove = if (k == 0) -1 else side(moves[k - 1])
                val outMove = if (k == pos.size - 1) -1 else side(moves[k])
                val kind =
                    when (k) {
                        0 -> CoxKind.START
                        pos.size - 1 -> CoxKind.END
                        else -> rooms[k - 1]
                    }
                out += cell(out.size, floor, p.first, p.second, kind, inMove, outMove, floor == floors.size - 1, threeFloors = true)
                    ?: error("cox challenge cell $kind")
            }
        }
        return CoxLayoutPlan("challenge", out, floors.size)
    }

    /** Test fixture: [code] placed with cycle [cycleIndex] at [offset]. */
    fun fixed(code: String, cycleIndex: Int, offset: Int): CoxLayoutPlan? =
        place(code, CYCLES[cycleIndex], offset, PUZZLES, List(MAX_FLOORS) { CoxKind.FARMING_FISH })

    /** The drawn contents a placement fills its slots from. */
    private class Draws(val cycle: List<CoxKind>, val offset: Int, puzzlesIn: List<CoxKind>, val resources: List<CoxKind>) {
        val all = puzzlesIn
        val puzzles = ArrayDeque(puzzlesIn)
    }

    @Suppress("LongParameterList")
    private fun tryPlace(
        floorsRooms: List<String>,
        floorsPaths: List<String>,
        startCol: Int,
        startRow: Int,
        cycle: List<CoxKind>,
        offset: Int,
        puzzlesIn: List<CoxKind>,
        resources: List<CoxKind>,
    ): List<CoxCell>? {
        val draws = Draws(cycle, offset, puzzlesIn, resources)
        val out = ArrayList<CoxCell>()
        var at = startCol to startRow
        for ((floor, rooms) in floorsRooms.withIndex()) {
            val moves = floorsPaths.getOrNull(floor) ?: return null
            if (moves.length != rooms.length + 1) return null
            val pos = path(at, moves) ?: return null
            at = pos.last()
            var largeGiven = false
            for ((k, p) in pos.withIndex()) {
                val inMove = if (k == 0) -1 else side(moves[k - 1])
                val outMove = if (k == pos.size - 1) -1 else side(moves[k])
                val slot = floor * SLOTS_PER_FLOOR + p.first + COLS * p.second
                val kind =
                    when (k) {
                        0 -> {
                            CoxKind.START
                        }

                        pos.size - 1 -> {
                            CoxKind.END
                        }

                        else -> {
                            roomKind(rooms, k - 1, slot, floor, out.size, draws, largeGiven).also {
                                if (it ==
                                    CoxKind.SCAVENGERS_LARGE
                                ) {
                                    largeGiven = true
                                }
                            }
                        }
                    }
                val last = floor == floorsRooms.size - 1
                out += cell(out.size, floor, p.first, p.second, kind, inMove, outMove, last) ?: return null
            }
        }
        return out
    }

    /** The grid cells of a floor's path from [start] (start, each room, end); null when it leaves the grid or crosses. */
    private fun path(start: Pair<Int, Int>, moves: String): List<Pair<Int, Int>>? {
        var (col, row) = start
        val pos = arrayListOf(start)
        for (m in moves) {
            val (dx, dz) = step(m) ?: return null
            col += dx
            row += dz
            if (col !in 0 until COLS || row !in 0 until ROWS) return null
            pos += col to row
        }
        return pos.takeIf { it.distinct().size == it.size }
    }

    @Suppress("LongParameterList")
    private fun roomKind(rooms: String, i: Int, slot: Int, floor: Int, placed: Int, draws: Draws, largeGiven: Boolean): CoxKind =
        when (rooms[i]) {
            'C' -> draws.cycle[(draws.offset + slot) % CYCLE]

            // OPEN-CX6: no repeat until all four puzzles are used.
            'P' -> draws.puzzles.removeFirstOrNull() ?: draws.all[placed % draws.all.size]

            // CX9: with two scavenger rooms one is the large layout (OPEN-CX6: which one; the first).
            'S' -> if (rooms.count { it == 'S' } > 1 && !largeGiven) CoxKind.SCAVENGERS_LARGE else CoxKind.SCAVENGERS

            else -> draws.resources[floor]
        }

    @Suppress("LongParameterList")
    private fun cell(
        index: Int,
        floor: Int,
        col: Int,
        row: Int,
        kind: CoxKind,
        inMove: Int,
        outMove: Int,
        lastFloor: Boolean,
        threeFloors: Boolean = false,
    ): CoxCell? {
        val entrySide = if (inMove < 0) -1 else (inMove + 2) % SIDES
        return when (kind) {
            CoxKind.START -> {
                // Start cells: the lobby (floor 1) or the rope cell; the THRU variant turned to face the way out.
                val z0 =
                    when (floor) {
                        0 -> LOBBY_Z
                        1 -> START1_Z
                        else -> START2_Z
                    }
                CoxCell(index, floor, col, row, kind, CoordGrid(X_THRU, z0, 0), outMove, -1, outMove)
            }

            CoxKind.END -> {
                // END2 (hole down) is the CCW cell, END3 (the Olm hole) the THRU cell of band 5152; entered from south.
                // The middle floor of a three-floor (Challenge Mode) raid ends in END1 (5120), its hole DESCENTTO1.
                val x0 = if (lastFloor) X_THRU else X_CCW
                val z0 = if (!lastFloor && floor == 0 && threeFloors) END1_Z else END2_Z
                CoxCell(index, floor, col, row, kind, CoordGrid(x0, z0, 0), inMove, entrySide, -1)
            }

            else -> {
                val (level, z0) = BANDS.getValue(kind)
                val rotation = inMove
                val winding = (outMove - rotation + SIDES) % SIDES
                val x0 =
                    when (winding) {
                        0 -> X_THRU
                        WINDING_CW -> X_CW
                        WINDING_CCW -> X_CCW
                        else -> return null
                    }
                CoxCell(index, floor, col, row, kind, CoordGrid(x0, z0, level), rotation, entrySide, outMove)
            }
        }
    }

    private fun step(m: Char): Pair<Int, Int>? =
        when (m) {
            'N' -> 0 to 1
            'S' -> 0 to -1
            'E' -> 1 to 0
            'W' -> -1 to 0
            else -> null
        }

    private fun side(m: Char): Int =
        when (m) {
            'N' -> NORTH
            'E' -> EAST
            'S' -> SOUTH
            else -> WEST
        }

    /** The instance: both floors (two squares wide) and the Olm arena beside them. */
    fun instance(plan: CoxLayoutPlan): RaidLayout {
        val zones = ArrayList<InstanceZone>()
        for (c in plan.cells) {
            for (zx in 0 until ZONES_PER_CELL) {
                for (zz in 0 until ZONES_PER_CELL) {
                    val from = CoordGrid(c.template.x + zx * ZONE, c.template.z + zz * ZONE, c.template.level)
                    // The engine turns a zone in place: its place inside the 4 x 4 cell turns with it.
                    val (tx, tz) = turn(zx, zz, ZONES_PER_CELL - 1, c.rotation)
                    zones += InstanceZone(from, CoordGrid(c.localX + tx * ZONE, c.localZ + tz * ZONE, c.plane), c.rotation)
                }
            }
        }
        val olm = InstanceSquare(OLM_SQUARE_X, OLM_SQUARE_Z, OLM_DX, 0, levels = OLM_LEVELS)
        return RaidLayout(OLM_DX + 1, 1, listOf(olm), zones)
    }

    /** Engine rotation (Instances.rotateX / rotateZ): r quarter turns clockwise over a grid of side [mask] + 1. */
    fun turn(x: Int, z: Int, mask: Int, rotation: Int): Pair<Int, Int> =
        when (rotation and (SIDES - 1)) {
            0 -> x to z
            1 -> z to mask - x
            2 -> mask - x to mask - z
            else -> mask - z to x
        }

    /** A template-local tile (0..31) of [cell] as an offset inside its instance cell. */
    fun local(cell: CoxCell, tx: Int, tz: Int): Pair<Int, Int> = turn(tx, tz, CELL - 1, cell.rotation)

    private const val ZONE = 8
    private const val FIVE_ROOMS = 5

    /** CX13: the Large pool's five combat and three puzzle rooms; with F and S on each six-room floor, four C/P per floor. */
    private const val LARGE_COMBAT = 5
    private const val LARGE_PUZZLES = 3
    private const val LARGE_C_P_PER_FLOOR = 4

    /** Floor 1 holds 2, 3 or 4 of the combat rooms (floor 2 the rest; each floor's puzzles fill its four C/P rooms). */
    private const val LARGE_MIN_COMBAT1 = 2
    private const val LARGE_COMBAT1_CHOICES = 3

    /** Rejection-sampling bound for a Large draw (the order rules and the no-repeat combat rule). */
    private const val LARGE_TRIES = 500
    private const val SMALL_COMBAT = 3
    private const val MAX_FLOORS = 3
    private const val ZONES_PER_CELL = 4
    private const val OLM_LEVELS = 2

    private fun <T> List<T>.shuffled(rng: RaidRng): List<T> {
        val out = toMutableList()
        for (i in out.indices.reversed()) {
            val j = rng.next(i + 1, "cox shuffle")
            val t = out[i]
            out[i] = out[j]
            out[j] = t
        }
        return out
    }
}
