package tp.content.skills

import tp.symbols.Locs

/**
 * A piece of furniture that can be built: the table row [furniture] and the loc ids [locs] it becomes (the first one names it
 * in saved layouts: it is unique among the table's buildable rows).
 */
internal class Piece(val furniture: Furniture, val locs: List<Int>) {
    val id: Int get() = locs.first()
    val name: String get() = furniture.name
}

/** The furniture table as the house sees it (docs/mechanics/construction.md H6). */
internal object PohFurniture {
    /** The default house's exit portal has no loc ids in the Wiki table; the cache names it `poh_exit_portal`. */
    private const val EXIT_PORTAL = "Exit portal"

    /** Rows with at least one built loc id; rows without (Mahogany Homes clients, versioned infoboxes) cannot be built. */
    val pieces: List<Piece> by lazy {
        ConstructionData.furniture().mapNotNull { f ->
            val locs = if (f.name == EXIT_PORTAL) listOf(Locs.POH_EXIT_PORTAL) else f.locs
            if (locs.isEmpty()) null else Piece(f, locs)
        }
    }

    private val byId: Map<Int, Piece> by lazy { pieces.associateBy { it.id } }

    /** Every loc id a built piece can have; "Remove" is registered on all of them. */
    val builtLocs: Set<Int> by lazy { pieces.flatMapTo(HashSet()) { it.locs } }

    /** The distinct lower-case hotspot names of the table. */
    val hotspotNames: Set<String> by lazy { pieces.flatMapTo(HashSet()) { hotspotsOf(it.furniture) } }

    private val byRoomHotspot: Map<String, List<Piece>> by lazy {
        val out = HashMap<String, MutableList<Piece>>()
        for (piece in pieces) {
            for (room in tokens(piece.furniture.room)) {
                for (hotspot in hotspotsOf(piece.furniture)) out.getOrPut("$room|$hotspot") { ArrayList() } += piece
            }
        }
        out.mapValues { (_, list) -> list.sortedWith(compareBy({ it.furniture.level }, { it.name })) }
    }

    fun byLoc(id: Int): Piece? = byId[id]

    /** What may be built on a hotspot called [hotspot] (lower case) in a room called [roomKey] (a [RoomTemplate.furnitureKey]). */
    fun forHotspot(roomKey: String, hotspot: String): List<Piece> = byRoomHotspot["$roomKey|$hotspot"].orEmpty()

    private fun hotspotsOf(f: Furniture): List<String> = tokens(f.hotspot)

    private fun tokens(text: String): List<String> = text.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
}
