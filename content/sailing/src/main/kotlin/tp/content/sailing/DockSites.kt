package tp.content.sailing

import tp.symbols.Locs
import tp.util.CoordGrid

/**
 * A mooring point in the world: the cache dock row, the land tile of its gangplank or mooring point loc (`docks.tsv`,
 * the placement of the loc itself, level 0 after the bridge rule) and the sea tile of its buoy (`dock-buoys.tsv`, south-west
 * tile of the 2x2 buoy loc). Both coordinates are server-only data (docs/mechanics/sailing-ports.md P1, P7).
 */
internal class DockSite(val dock: Dock, val land: CoordGrid?, val sea: CoordGrid?) {
    /**
     * Facing a boat takes when it moors here: the bow points away from the land, along the dominant axis (0 south, 512 west,
     * 1024 north, 1536 east). OPEN-P5: the real mooring rotation is the server-only `BOAT_ROTATION` column.
     */
    val angle: Int
        get() {
            val l = land ?: return 0
            val s = sea ?: return 0
            val dx = s.x - l.x
            val dz = s.z - l.z
            return if (kotlin.math.abs(dz) >= kotlin.math.abs(dx)) {
                if (dz < 0) ANGLE_SOUTH else ANGLE_NORTH
            } else {
                if (dx < 0) ANGLE_WEST else ANGLE_EAST
            }
        }

    private companion object {
        const val ANGLE_SOUTH = 0
        const val ANGLE_WEST = 512
        const val ANGLE_NORTH = 1024
        const val ANGLE_EAST = 1536
    }
}

/** The docks of the world by dock id, and the loc id to dock id mapping of gangplanks, mooring points and buoys. */
internal class DockSites(docks: List<Dock>) {
    private val byId: Map<Int, DockSite>

    init {
        val land = readTsv("docks.tsv", NAME_COL, LAND_X_COL, LAND_Z_COL)
        val sea = readTsv("dock-buoys.tsv", NAME_COL, SEA_X_COL, SEA_Z_COL)
        byId =
            docks.associate { d ->
                val key = normalise(d.name)
                d.id to DockSite(d, land[key]?.firstOrNull(), sea[key]?.firstOrNull())
            }
    }

    fun of(dockId: Int): DockSite? = byId[dockId]

    val all: Collection<DockSite> get() = byId.values

    /** Dock id of a gangplank loc (base id, before the client resolves the embark / disembark variant), or null. */
    fun ofGangplank(loc: Int): Int? =
        when (loc) {
            in Locs.SAILING_GANGPLANK_PORT_SARIM..Locs.SAILING_GANGPLANK_LUNAR_ISLE -> loc - Locs.SAILING_GANGPLANK_PORT_SARIM
            Locs.SAILING_GANGPLANK_WYRMSCRAIG -> DOCK_WYRMSCRAIG
            Locs.SAILING_GANGPLANK_WYRMSCRAIG_CAVE -> DOCK_WYRMSCRAIG_CAVE
            else -> null
        }

    /** Dock id of a mooring point loc, or null. */
    fun ofMooring(loc: Int): Int? =
        if (loc in Locs.SAILING_MOORING_ISLE_OF_SOULS..Locs.SAILING_MOORING_GRIMSTONE) {
            loc - Locs.SAILING_MOORING_ISLE_OF_SOULS + FIRST_MOORING_DOCK
        } else {
            null
        }

    /** Dock id of a buoy loc, or null. */
    fun ofBuoy(loc: Int): Int? =
        when (loc) {
            in Locs.SAILING_DOCKING_BUOY_PORT_SARIM..Locs.SAILING_DOCKING_BUOY_GRIMSTONE -> loc - Locs.SAILING_DOCKING_BUOY_PORT_SARIM
            Locs.SAILING_DOCKING_BUOY_WYRMSCRAIG -> DOCK_WYRMSCRAIG
            Locs.SAILING_DOCKING_BUOY_WYRMSCRAIG_CAVE -> DOCK_WYRMSCRAIG_CAVE
            else -> null
        }

    private fun readTsv(file: String, name: Int, x: Int, z: Int): Map<String, List<CoordGrid>> {
        val text = DockSites::class.java.getResource(file)?.readText() ?: error("$file missing")
        val out = HashMap<String, MutableList<CoordGrid>>()
        for (line in text.lineSequence().drop(1)) {
            val cells = line.split('\t')
            val cx = cells.getOrNull(x)?.toIntOrNull() ?: continue
            val cz = cells.getOrNull(z)?.toIntOrNull() ?: continue
            out.getOrPut(normalise(cells[name])) { ArrayList() } += CoordGrid(cx, cz, 0)
        }
        return out
    }

    private fun normalise(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    private companion object {
        /** `Sailing_Dock.DOCK_ID` of the first island mooring point (Isle of Souls); gangplank docks are 0..31. */
        const val FIRST_MOORING_DOCK = 32
        const val DOCK_WYRMSCRAIG = 59
        const val DOCK_WYRMSCRAIG_CAVE = 60
        const val NAME_COL = 0

        /** docks.tsv: name, level, requirement, 6 facility flags, x, z. */
        const val LAND_X_COL = 9
        const val LAND_Z_COL = 10

        /** dock-buoys.tsv: name, x, z. */
        const val SEA_X_COL = 1
        const val SEA_Z_COL = 2
    }
}
