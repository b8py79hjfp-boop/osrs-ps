package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.InstanceRef
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.util.CoordGrid

/** A doorway tile of a room: the door-hotspot [loc] on [side]. */
internal class DoorSpot(val loc: LocRef, val side: Side)

/**
 * One hotspot of a room: the [locs] (copied template locs) of one furniture hotspot named [name] (lower case). [key] is the
 * stable key that names it in the saved layout; [built] is the piece standing on it, if any.
 */
internal class HotspotGroup(
    val key: Int,
    val room: PlacedRoom,
    val name: String,
    val locs: List<LocRef>,
    /** The distinct hotspot loc ids of every hotspot called [name] in the room, sorted (construction.md CO24). */
    val kinds: List<Int> = emptyList(),
) {
    var built: Piece? = null

    /** The hotspot loc ids in a fixed order; the i-th picks the i-th loc id of a built piece. */
    val ids: List<Int> = locs.map { it.id }.distinct().sorted()
}

/** What a room's template looked like right after it was copied: its hotspot groups, doorways and wall loc per angle. */
internal class RoomScan(val room: PlacedRoom, val groups: List<HotspotGroup>, val doors: List<DoorSpot>, val walls: Map<Int, Int>)

/**
 * A live house: the instance, the saved layout and the scans of the copied rooms (docs/mechanics/construction.md H3). [dim] is the
 * side of the grid of room cells 1..dim the instance was allocated for.
 */
internal class House(
    val owner: String,
    val layout: HouseLayout,
    val instance: InstanceRef,
    val style: Int,
    val dim: Int,
    var buildMode: Boolean,
) {
    val players = LinkedHashSet<PlayerRef>()
    var locked = false

    /** The owner's hired servant standing in the house, and what it is doing for the owner ([PohServants]). */
    var servant: NpcRef? = null
    var task: ServantTask? = null

    /** The incense burners (hotspot groups) that are lit now ([PohAltar]); a burner that is not built is never in it. */
    val lit = HashSet<HotspotGroup>()

    /** Which lighting of each lit burner is the current one, so an old expiry timer does not put out a refuelled burner. */
    val litStamp = HashMap<HotspotGroup, Int>()
    var burnStamp = 0

    /** The house location (portal) it was entered from; guests must use a portal of the same location. */
    var location = 0
    val scans = HashMap<PlacedRoom, RoomScan>()
    private val byCoord = HashMap<Int, HotspotGroup>()

    /** The south-west tile of the room's zone in the instance. */
    fun cellOrigin(room: PlacedRoom): CoordGrid = cellOrigin(room.floor, room.x, room.z)

    fun cellOrigin(floor: Int, x: Int, z: Int): CoordGrid = instance.base.translate(x * PohRooms.ZONE, z * PohRooms.ZONE, floor)

    fun roomAt(floor: Int, x: Int, z: Int): PlacedRoom? = layout.roomAt(floor, x, z)

    /** The hotspot group one of whose locs (hotspot or built piece) has its origin on [coord]. */
    fun groupAt(coord: CoordGrid): HotspotGroup? = byCoord[coord.packed]

    /** The room cell holding the instance tile [coord], or null. */
    fun roomOf(coord: CoordGrid): PlacedRoom? {
        val cx = (coord.x - instance.base.x) / PohRooms.ZONE
        val cz = (coord.z - instance.base.z) / PohRooms.ZONE
        return roomAt(coord.level, cx, cz)
    }

    fun register(scan: RoomScan) {
        scans[scan.room] = scan
        for (g in scan.groups) for (loc in g.locs) byCoord[loc.coord.packed] = g
    }

    fun unregister(room: PlacedRoom) {
        val scan = scans.remove(room) ?: return
        for (g in scan.groups) for (loc in g.locs) byCoord.remove(loc.coord.packed)
    }

    /** The group holding the exit portal, or null if the house has none. */
    fun exitPortal(): HotspotGroup? = scans.values.firstNotNullOfOrNull { s -> s.groups.firstOrNull { it.built?.id == EXIT_PORTAL_PIECE } }

    companion object {
        /** The exit portal piece, named by its first loc id. */
        val EXIT_PORTAL_PIECE: Int = Locs.POH_EXIT_PORTAL

        /** The house the player is in (set on entering, removed on leaving). */
        val CURRENT = AttrKey<House>("poh.house")

        /** Where the player stood outside when they entered; leaving returns them there. */
        val RETURN = AttrKey<CoordGrid>("poh.return")

        /** All live houses of a world, by lower-case owner name. */
        val HOUSES = AttrKey<HashMap<String, House>>("poh.houses")
    }
}
