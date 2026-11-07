package tp.content.skills

import tp.symbols.Locs

/**
 * A piece of furniture that can be built: the table row [furniture] and the loc ids [locs] it becomes (the first one names it
 * in saved layouts: it is unique among the table's buildable rows). [suffix] tells apart two pieces made from one row (a
 * staircase built "up" or "down").
 */
internal class Piece(val furniture: Furniture, val locs: List<Int>, val suffix: String = "") {
    val id: Int get() = locs.first()
    val name: String get() = furniture.name + suffix
}

/** The furniture table as the house sees it (docs/mechanics/construction.md H6). */
internal object PohFurniture {
    /** The default house's exit portal has no loc ids in the Wiki table; the cache names it `poh_exit_portal`. */
    private const val EXIT_PORTAL = "Exit portal"

    /** Lower-case name of the staircase hotspot (cache 2757: "Stair Space", category 207, op 5 "Build"). */
    const val STAIR_SPACE = "stair space"

    /**
     * Straight staircases: Wiki name to the loc built for "up" and for "down" (cache 2757: 13497 "Climb-up" / 13498 "Climb-down",
     * and the same pairs for teak 13499/13500 and marble 13501/13502; construction.md CO23). The Wiki lists only one id for teak.
     */
    val STAIRS: Map<String, Pair<Int, Int>> =
        mapOf("Oak staircase" to (13497 to 13498), "Teak staircase" to (13499 to 13500), "Marble staircase" to (13501 to 13502))

    /** Spiral staircases have one loc with the ops "Climb", "Climb-up" and "Climb-down" (cache 2757: 13503, 13505). */
    val SPIRALS: Map<String, Int> = mapOf("Spiral staircase" to 13503, "Marble spiral" to 13505)

    /** Rows with at least one built loc id; rows without (Mahogany Homes clients, versioned infoboxes) cannot be built. */
    val pieces: List<Piece> by lazy {
        ConstructionData.furniture().flatMap { f ->
            val stairs = STAIRS[f.name]
            val spiral = SPIRALS[f.name]
            when {
                f.name == EXIT_PORTAL -> listOf(Piece(f, listOf(Locs.POH_EXIT_PORTAL)))
                stairs != null -> listOf(Piece(f, listOf(stairs.first), " (up)"), Piece(f, listOf(stairs.second), " (down)"))
                spiral != null -> listOf(Piece(f, listOf(spiral)))
                f.locs.isEmpty() -> emptyList()
                else -> listOf(Piece(f, f.locs))
            }
        }
    }

    private val byId: Map<Int, Piece> by lazy { pieces.associateBy { it.id } }
    private val byName: Map<String, Piece> by lazy {
        val out = HashMap<String, Piece>()
        for (piece in pieces) out.putIfAbsent(piece.furniture.name, piece)
        out
    }

    /** Every loc id a built piece can have; "Remove" is registered on all of them. */
    val builtLocs: Set<Int> by lazy { pieces.flatMapTo(HashSet()) { it.locs } }

    /** The pieces that only exist as the result of an upgrade (construction.md CO29): they are not in the build menus. */
    private val upgradeOnly: Set<String> by lazy { ConstructionData.upgrades().mapTo(HashSet()) { it.to } }

    /** The distinct lower-case hotspot names of the table. */
    val hotspotNames: Set<String> by lazy { pieces.flatMapTo(HashSet()) { hotspotsOf(it) } }

    private val byRoomHotspot: Map<String, List<Piece>> by lazy {
        val out = HashMap<String, MutableList<Piece>>()
        for (piece in pieces) {
            if (piece.furniture.name in upgradeOnly) continue
            for (room in tokens(piece.furniture.room)) {
                for (hotspot in hotspotsOf(piece)) out.getOrPut("$room|$hotspot") { ArrayList() } += piece
            }
        }
        out.mapValues { (_, list) -> list.sortedWith(compareBy({ it.furniture.level }, { it.name })) }
    }

    fun byLoc(id: Int): Piece? = byId[id]

    /** The piece made from the table row called [name]; the "up" staircase for a straight one. */
    fun byName(name: String): Piece? = byName[name]

    /** What may be built on a hotspot called [hotspot] (lower case) in a room called [roomKey] (a [RoomTemplate.furnitureKey]). */
    fun forHotspot(roomKey: String, hotspot: String): List<Piece> = byRoomHotspot["$roomKey|$hotspot"].orEmpty()

    /**
     * The hotspots a piece may be built on: its table row, except that a staircase goes on the stair space only and nothing else is
     * offered there (a rug could also cover the stairwell in the game: OPEN-CO23).
     */
    private fun hotspotsOf(piece: Piece): List<String> {
        val name = piece.furniture.name
        if (name in STAIRS || name in SPIRALS) return listOf(STAIR_SPACE)
        return tokens(piece.furniture.hotspot).filter { it != STAIR_SPACE }
    }

    private fun tokens(text: String): List<String> = text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
