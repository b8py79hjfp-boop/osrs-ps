package tp.content.skills

import tp.plugin.LocRef
import tp.plugin.PlayerRef

/**
 * Finds the hotspots and doorways of a room right after its template zone was copied (docs/mechanics/construction.md H4).
 * The copied locs are readable like cache locs ([PlayerRef.locAt]); once furniture is built they are covered by dynamic
 * changes, so a room is scanned exactly once.
 */
internal object PohScanner {
    /** One representative shape per loc layer: walls (0), wall decor (4), ground (10) and ground decor (22). */
    private val LAYER_SHAPES = intArrayOf(0, 4, 10, 22)

    /** The cache name of the plain wall pieces of a room (cache 2757: `null`, shape 0). */
    private const val WALL_NAME = "null"

    private class Found(val loc: LocRef, val name: String, val dx: Int, val dz: Int)

    fun scan(p: PlayerRef, house: House, room: PlacedRoom): RoomScan {
        val origin = house.cellOrigin(room)
        val names = HashMap<Int, String>()
        val hotspots = ArrayList<Found>()
        val doors = ArrayList<DoorSpot>()
        val walls = HashMap<Int, Int>()
        for (dz in 0 until PohRooms.ZONE) {
            for (dx in 0 until PohRooms.ZONE) {
                val tile = origin.translate(dx, dz)
                for (shape in LAYER_SHAPES) {
                    val loc = p.locAt(tile, shape) ?: continue
                    if (loc.coord != tile) continue
                    val name = names.getOrPut(loc.id) { p.locName(loc.id).lowercase() }
                    when {
                        name == PohRooms.DOOR_HOTSPOT -> doors += DoorSpot(loc, sideOf(dx, dz))
                        name == WALL_NAME && loc.shape == 0 -> walls.putIfAbsent(loc.angle, loc.id)
                        PohRooms.isHotspot(name) -> hotspots += Found(loc, name, dx, dz)
                    }
                }
            }
        }
        return RoomScan(room, group(room, hotspots), doors, walls)
    }

    /** The side of the room a doorway tile on its edge belongs to. */
    private fun sideOf(dx: Int, dz: Int): Side =
        when {
            dz == 0 -> Side.SOUTH
            dz == PohRooms.LAST -> Side.NORTH
            dx == 0 -> Side.WEST
            else -> Side.EAST
        }

    /** Index of a tile on a loc layer; two hotspots may share a tile on different layers (a chair on its rug). */
    private fun slot(dx: Int, dz: Int, shape: Int): Int =
        HouseLayout.layerOf(shape) * PohRooms.ZONE * PohRooms.ZONE + dz * PohRooms.ZONE + dx

    /** Hotspot locs of one name that touch (4-neighbours) are one hotspot: a rug of 16 tiles is built in one go. */
    private fun group(room: PlacedRoom, found: List<Found>): List<HotspotGroup> {
        val at = HashMap<Int, Found>()
        for (f in found) at[slot(f.dx, f.dz, f.loc.shape)] = f
        val seen = HashSet<Found>()
        val groups = ArrayList<HotspotGroup>()
        for (start in found) {
            if (!seen.add(start)) continue
            val members = ArrayList<Found>()
            val queue = ArrayDeque<Found>()
            queue.add(start)
            while (queue.isNotEmpty()) {
                val f = queue.removeFirst()
                members += f
                for (side in Side.entries) {
                    val nx = f.dx + side.dx
                    val nz = f.dz + side.dz
                    if (nx !in 0 until PohRooms.ZONE || nz !in 0 until PohRooms.ZONE) continue
                    val next = at[slot(nx, nz, f.loc.shape)] ?: continue
                    if (next.name == start.name && seen.add(next)) queue.add(next)
                }
            }
            val anchorLoc = members.minBy { PohRooms.templateTile(it.dx, it.dz, room.rotation) }
            val anchor = PohRooms.templateTile(anchorLoc.dx, anchorLoc.dz, room.rotation)
            val key = HouseLayout.furnitureKey(room.floor, room.x, room.z, HouseLayout.layerOf(anchorLoc.loc.shape), anchor)
            groups += HotspotGroup(key, room, start.name, members.map { it.loc })
        }
        return groups
    }
}
