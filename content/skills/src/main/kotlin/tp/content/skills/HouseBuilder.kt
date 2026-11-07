package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.util.CoordGrid

/**
 * Builds and changes a live [House] (docs/mechanics/construction.md H3-H6): copies the room zones into the instance, puts
 * furniture on hotspots, hides the hotspots outside build mode and opens or closes the doorways between rooms.
 */
internal object HouseBuilder {
    /** Tiles per map square, the unit instances are allocated in. */
    private const val SQUARE = 64

    /** The exit portal (cache 2757: `poh_exit_portal`, width 2, length 2). */
    private const val PORTAL_SIZE = 2

    /** Construction level where the house gets its next size: (level, room limit, grid side) rows of the Wiki table. */
    private val LIMITS =
        listOf(
            Triple(99, 38, 7),
            Triple(96, 37, 7),
            Triple(92, 36, 7),
            Triple(86, 35, 7),
            Triple(80, 34, 7),
            Triple(74, 33, 7),
            Triple(68, 32, 7),
            Triple(62, 31, 7),
            Triple(60, 30, 7),
            Triple(56, 30, 6),
            Triple(50, 29, 6),
            Triple(45, 28, 6),
            Triple(44, 28, 5),
            Triple(38, 27, 5),
            Triple(32, 26, 5),
            Triple(30, 25, 5),
            Triple(26, 25, 4),
            Triple(15, 24, 4),
            Triple(1, 24, 3),
        )

    /** The largest number of rooms a player of (unboosted) Construction [level] may have (Wiki "Player-owned house", Restrictions). */
    fun maxRooms(level: Int): Int = LIMITS.first { level >= it.first }.second

    /** The side of the square grid of rooms a player of Construction [level] may build in (same table). */
    fun maxDimension(level: Int): Int = LIMITS.first { level >= it.first }.third

    /** Allocates the instance for [layout] and builds every room (hotspots hidden unless [buildMode]). */
    fun create(p: PlayerRef, layout: HouseLayout, style: Int, buildMode: Boolean): House {
        val dim = maxOf(maxDimension(p.baseLevel(Stats.CONSTRUCTION)), extent(layout))
        // The yard is one ring of cells around the house grid (Wiki table: 3x3 house, 5x5 yard); cell 0 is the yard's edge.
        val squares = ((dim + 2) * PohRooms.ZONE + SQUARE - 1) / SQUARE
        val house = House(p.username.lowercase(), layout, p.createInstance(squares, squares), style, dim, buildMode)
        for (room in layout.rooms) copyRoom(house, room)
        for (room in layout.rooms) populate(p, house, room)
        ensureExitPortal(p, house)
        for (scan in house.scans.values) applyDoors(p, house, scan)
        return house
    }

    /** The grid side the saved rooms need (rooms occupy cells 1..side). */
    private fun extent(layout: HouseLayout): Int = layout.rooms.maxOfOrNull { maxOf(it.x, it.z) } ?: 0

    private fun copyRoom(house: House, room: PlacedRoom) {
        house.instance.copyZone(PohRooms.zone(room.type, house.style, room.floor), house.cellOrigin(room), room.rotation)
    }

    /** Scans a freshly copied room and puts its saved furniture on it. */
    private fun populate(p: PlayerRef, house: House, room: PlacedRoom) {
        val scan = PohScanner.scan(p, house, room)
        house.register(scan)
        for (g in scan.groups) {
            val piece = house.layout.furniture[g.key]?.let(PohFurniture::byLoc)
            if (piece != null) {
                place(p, g, piece)
            } else if (!house.buildMode) {
                hide(p, g)
            }
        }
    }

    /** A house always has an exit portal (Wiki: the starter garden has one): a house without gets it on the first free centrepiece. */
    private fun ensureExitPortal(p: PlayerRef, house: House) {
        if (house.exitPortal() != null) return
        val spot =
            house.scans.values
                .flatMap { it.groups }
                .firstOrNull { it.name == CENTREPIECE && it.built == null } ?: return
        val piece = PohFurniture.byLoc(Locs.POH_EXIT_PORTAL) ?: return
        place(p, spot, piece)
        house.layout.furniture[spot.key] = piece.id
        PohSave.save(p, house.layout)
    }

    /** Puts [piece] on the hotspot: each hotspot loc becomes the piece's loc with the same index, keeping shape and angle. */
    fun place(p: PlayerRef, g: HotspotGroup, piece: Piece) {
        for (loc in g.locs) p.addLoc(piece.locs[locIndex(g, loc, piece)], loc.coord, loc.shape, loc.angle)
        g.built = piece
        PohPortals.decorate(p, g, piece)
    }

    /**
     * Which of the piece's loc ids a hotspot loc becomes. The i-th hotspot loc id of the group picks the i-th loc id; when the
     * group has fewer ids than the piece but the room has exactly as many kinds of this hotspot as the piece has ids (wall
     * shelves, the sixteen decoration spaces), the kind's rank picks it (construction.md CO24, OPEN-CO10). A piece whose extra
     * ids are states (open and closed, lit and unlit) always uses the first.
     */
    private fun locIndex(g: HotspotGroup, loc: tp.plugin.LocRef, piece: Piece): Int {
        val last = piece.locs.size - 1
        if (g.ids.size < piece.locs.size && g.kinds.size == piece.locs.size) return g.kinds.indexOf(loc.id).coerceIn(0, last)
        return g.ids.indexOf(loc.id).coerceAtMost(last)
    }

    private fun hide(p: PlayerRef, g: HotspotGroup) {
        for (loc in g.locs) p.deleteLoc(loc.coord, loc.shape)
    }

    private fun show(p: PlayerRef, g: HotspotGroup) {
        for (loc in g.locs) p.revertLoc(loc.coord, loc.shape)
    }

    /** Opens or closes the doorways of a room: see construction.md H4. */
    private fun applyDoors(p: PlayerRef, house: House, scan: RoomScan) {
        for (door in scan.doors) {
            val at = door.loc.coord
            val wall = scan.walls[door.loc.angle]
            when {
                house.buildMode -> p.revertLoc(at, door.loc.shape)
                connected(house, scan.room, door.side) || wall == null -> p.deleteLoc(at, door.loc.shape)
                else -> p.addLoc(wall, at, door.loc.shape, door.loc.angle)
            }
        }
    }

    /** Whether the room next to [room] on [side] exists and has a door on the opposite side. */
    fun connected(house: House, room: PlacedRoom, side: Side): Boolean {
        val next = house.roomAt(room.floor, room.x + side.dx, room.z + side.dz) ?: return false
        return side.opposite in next.doors
    }

    fun setBuildMode(p: PlayerRef, house: House, on: Boolean) {
        house.buildMode = on
        for (scan in house.scans.values) {
            for (g in scan.groups) {
                if (g.built == null) {
                    if (on) {
                        show(p, g)
                    } else {
                        hide(p, g)
                    }
                }
            }
            applyDoors(p, house, scan)
        }
    }

    fun buildFurniture(p: PlayerRef, house: House, g: HotspotGroup, piece: Piece) {
        for (target in linked(house, g)) {
            place(p, target, piece)
            house.layout.furniture[target.key] = piece.id
        }
        PohSave.save(p, house.layout)
    }

    fun removeFurniture(p: PlayerRef, house: House, g: HotspotGroup) {
        for (target in linked(house, g)) {
            house.lit -= target
            show(p, target)
            target.built = null
            house.layout.furniture.remove(target.key)
        }
        PohSave.save(p, house.layout)
    }

    /**
     * The hotspots that change together with [g]: the chapel's two lamp spaces ("building an item of furniture in either one will
     * build it in both places at once", Wiki "Chapel"; removing it likewise removes both), else just [g].
     */
    private fun linked(house: House, g: HotspotGroup): List<HotspotGroup> {
        if (g.name != LAMP_SPACE) return listOf(g)
        val pair = house.scans[g.room]?.groups?.filter { it.name == LAMP_SPACE && (it === g || it.built === g.built) }
        return pair.orEmpty().ifEmpty { listOf(g) }
    }

    /** The lower-case name of the chapel's lamp hotspots. */
    private const val LAMP_SPACE = "lamp space"

    fun addRoom(p: PlayerRef, house: House, room: PlacedRoom) {
        house.layout.rooms += room
        copyRoom(house, room)
        populate(p, house, room)
        refreshAround(p, house, room)
        PohSave.save(p, house.layout)
    }

    fun removeRoom(p: PlayerRef, house: House, room: PlacedRoom) {
        house.layout.rooms.remove(room)
        val cell = HouseLayout.cellKey(room.floor, room.x, room.z)
        house.layout.furniture.keys
            .removeAll { HouseLayout.cellOf(it) == cell }
        house.unregister(room)
        house.instance.clearZone(house.cellOrigin(room))
        refreshAround(p, house, room)
        PohSave.save(p, house.layout)
    }

    /** Re-applies the doorways of [room] (if it stands) and of its four neighbours. */
    private fun refreshAround(p: PlayerRef, house: House, room: PlacedRoom) {
        house.scans[room]?.let { applyDoors(p, house, it) }
        for (side in Side.entries) {
            val next = house.roomAt(room.floor, room.x + side.dx, room.z + side.dz) ?: continue
            house.scans[next]?.let { applyDoors(p, house, it) }
        }
    }

    /** Where a player arrives: beside the exit portal (nearest walkable tile of the ring around it, south first). */
    fun arrival(p: PlayerRef, house: House): CoordGrid {
        val portal =
            house
                .exitPortal()
                ?.locs
                ?.firstOrNull()
                ?.coord
        if (portal != null) {
            for (dz in -1..PORTAL_SIZE) {
                for (dx in -1..PORTAL_SIZE) {
                    if (dx in 0 until PORTAL_SIZE && dz in 0 until PORTAL_SIZE) continue
                    val tile = portal.translate(dx, dz)
                    if (p.isWalkable(tile)) return tile
                }
            }
            return portal
        }
        val first = house.layout.rooms.firstOrNull() ?: return house.instance.base
        return house.cellOrigin(first).translate(PohRooms.ZONE / 2, PohRooms.ZONE / 2)
    }

    /** The lower-case name of centrepiece hotspots, where the exit portal stands. */
    private const val CENTREPIECE = "centrepiece space"
}
