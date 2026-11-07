@file:Suppress("MagicNumber") // data table: template zones scanned from cache 2757 (docs/mechanics/construction.md H2)

package tp.content.skills

import tp.util.CoordGrid

/** The four sides of a room. The order is the clockwise-turn cycle of a room: south -> west -> north -> east (instances.md IN3). */
internal enum class Side(val dx: Int, val dz: Int) {
    SOUTH(0, -1),
    WEST(-1, 0),
    NORTH(0, 1),
    EAST(1, 0),
    ;

    /** The side this one lands on when its room is turned [quarterTurns] quarter turns clockwise. */
    fun turned(quarterTurns: Int): Side = entries[(ordinal + quarterTurns) % entries.size]

    val opposite: Side get() = turned(2)
}

/**
 * One room of the house: the cache template zone it is copied from ([zoneX], [zoneZ] are the south-west tile of the 8x8 zone in
 * the first house style, `docs/mechanics/construction.md` H2), the sides that carry door hotspots in the template ([doors]) and
 * whether it belongs under the ground floor ([basement]). [name] is the room's name in `construction-rooms.tsv`; [furnitureKey] is
 * how the furniture table (`construction-furniture.tsv`, column `room`) names it, lower case; [label] is what menus say.
 */
internal class RoomTemplate(
    val name: String,
    val furnitureKey: String,
    val zoneX: Int,
    val zoneZ: Int,
    val doors: Set<Side>,
    val basement: Boolean = false,
    val label: String = name,
)

internal object PohRooms {
    /** Floors of a house are instance levels: basement 0, ground floor 1, upper floor 2 (the template zones exist on all four levels). */
    const val BASEMENT = 0
    const val GROUND = 1

    /** Tiles per room side: one zone. */
    const val ZONE = 8
    const val LAST = ZONE - 1

    /** Quarter turns in a full circle. */
    const val TURNS = 4

    /** Tiles between two style columns of the template map (x 1856, 1920, 1984; the column at 2048 has only a ground sketch). */
    const val STYLE_STRIDE = 64

    /** Number of complete style columns in the cache (basic wood, basic stone, whitewashed stone: construction.md H2). */
    const val STYLES = 3

    private val N = Side.NORTH
    private val E = Side.EAST
    private val S = Side.SOUTH
    private val W = Side.WEST

    /**
     * Every room, in saved-id order (the index is stored in the layout: append only). Zones and door sides come from a scan of the
     * template map squares (29..32, 110..111) for the hotspot locs of every room (construction.md H2). Zones not listed here
     * (the second skill / quest hall, the second menagerie, the dungeon junction) are variants with a staircase and stay unused
     * (OPEN-CO6).
     */
    val ALL: List<RoomTemplate> =
        listOf(
            RoomTemplate("Parlour", "parlour", 1856, 7096, setOf(E, S, W)),
            RoomTemplate("Garden", "garden", 1856, 7048, setOf(N, E, S, W)),
            RoomTemplate("Kitchen", "kitchen", 1872, 7096, setOf(S, W)),
            RoomTemplate("Dining room", "dining room", 1888, 7096, setOf(E, S, W)),
            RoomTemplate("Workshop", "workshop", 1856, 7080, setOf(N, S)),
            RoomTemplate("Bedroom", "bedroom", 1904, 7096, setOf(S, W)),
            RoomTemplate("Hall (skill trophies)", "skill hall", 1864, 7088, setOf(N, E, S, W)),
            RoomTemplate("League hall", "league hall", 1896, 7104, setOf(E, S, W)),
            RoomTemplate("Games room", "games room", 1896, 7072, setOf(E, S, W)),
            RoomTemplate("Combat room", "combat room", 1880, 7072, setOf(E, S, W)),
            RoomTemplate("Hall (quest trophies)", "quest hall", 1896, 7088, setOf(N, E, S, W)),
            RoomTemplate("Menagerie", "menagerie", 1912, 7040, setOf(N, E, S, W)),
            RoomTemplate("Study", "study", 1888, 7080, setOf(E, S, W)),
            RoomTemplate("Costume room", "costume room", 1904, 7048, setOf(S)),
            RoomTemplate("Chapel", "chapel", 1872, 7080, setOf(S, W)),
            RoomTemplate("Portal chamber", "portal chamber", 1864, 7072, setOf(S)),
            RoomTemplate("Formal garden", "formal garden", 1872, 7048, setOf(N, E, S, W)),
            RoomTemplate("Throne room", "throne room", 1904, 7080, setOf(S)),
            RoomTemplate("Superior garden", "superior garden", 1896, 7040, setOf(N, E, S, W)),
            RoomTemplate("Portal nexus", "portal nexus", 1880, 7104, setOf(N, E, S, W)),
            RoomTemplate("Achievement gallery", "achievement gallery", 1864, 7104, setOf(N, S)),
            RoomTemplate("Oubliette", "oubliette", 1904, 7064, setOf(N, E, S, W), basement = true),
            RoomTemplate("Dungeon (Construction)", "dungeon", 1856, 7064, setOf(N, E, S, W), basement = true, label = "Dungeon cross"),
            RoomTemplate("Treasure room", "treasure room", 1912, 7072, setOf(S), basement = true),
            RoomTemplate("Dungeon (Construction)", "dungeon", 1888, 7064, setOf(N, S), basement = true, label = "Dungeon corridor"),
        )

    /** Room ids of the default house (construction.md H1). */
    val PARLOUR: Int = ALL.indexOfFirst { it.name == "Parlour" }
    val GARDEN: Int = ALL.indexOfFirst { it.name == "Garden" }

    /** The template zone (south-west tile) a room of [type] is copied from on [floor] in house style [style]. */
    fun zone(type: Int, style: Int, floor: Int): CoordGrid {
        val t = ALL[type]
        return CoordGrid(t.zoneX + style * STYLE_STRIDE, t.zoneZ, floor)
    }

    /** The door sides of a room of [type] turned [rotation] quarter turns clockwise. */
    fun doors(type: Int, rotation: Int): Set<Side> = ALL[type].doors.mapTo(HashSet()) { it.turned(rotation) }

    /** The rotations (0..3) of a room of [type] that put a door on [side]. */
    fun rotationsFacing(type: Int, side: Side): List<Int> = (0 until TURNS).filter { side in doors(type, it) }

    /** Number of the room's template tile (z * 8 + x) that the tile ([dx], [dz]) of the copy turned [rotation] times came from. */
    fun templateTile(dx: Int, dz: Int, rotation: Int): Int {
        var x = dx
        var z = dz
        // One clockwise turn maps (x, z) to (z, 7 - x) (instances.md IN3); undo it by turning the remaining quarter turns.
        repeat((TURNS - rotation) % TURNS) {
            val nx = z
            z = LAST - x
            x = nx
        }
        return z * ZONE + x
    }

    /** Hotspot names that are not "... space" (cache 2757: Formal garden fence and hedge, garden plants; all with op 5 "Build"). */
    private val ODD_HOTSPOTS = setOf("fencing", "hedging", "big plant", "big plant 2", "small plant", "small plant 2")

    /**
     * Hotspot names of the cache templates that no row of the furniture table names (the Wiki pages for pools, topiary, trees and
     * the armour / rune case / map displays carry versioned infoboxes the importer drops: construction.md OPEN-CO3). Clicking
     * them says there is nothing to build.
     */
    private val UNLISTED_HOTSPOTS =
        listOf(
            "armour space",
            "barrel space",
            "big tree space",
            "map space",
            "pool space",
            "rune case space",
            "topiary space",
            "tree space",
            "small plant space 1",
            "small plant space 2",
            "floor space mid",
            "floor space side",
            "floor space corner",
        )

    /** The name (lower case) of the loc that marks the doorways of a room (cache: category 485, op 5 "Build"). */
    const val DOOR_HOTSPOT = "door hotspot"

    /** Staircase hotspots are left out: floors are not connected yet (OPEN-CO6). */
    private const val STAIRS = "stair space"

    private const val SPACE = " space"

    /** Whether a loc of this lower-case cache name is a furniture hotspot (cache category 207, op 5 "Build"). */
    fun isHotspot(name: String): Boolean = ((name.endsWith(SPACE) || name.contains("$SPACE ")) && name != STAIRS) || name in ODD_HOTSPOTS

    /** Every hotspot name a "Build" click is registered for: the furniture table's and the unlisted ones. */
    fun hotspotNames(): Set<String> = (PohFurniture.hotspotNames + UNLISTED_HOTSPOTS + ODD_HOTSPOTS).filterTo(HashSet()) { it != STAIRS }
}
