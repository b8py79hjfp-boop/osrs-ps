package tp.content.skills

import tp.plugin.PlayerRef

/** A room of the house: [type] indexes [PohRooms.ALL], turned [rotation] quarter turns clockwise, at grid cell ([x], [z]) of [floor]. */
internal class PlacedRoom(val type: Int, val rotation: Int, val floor: Int, val x: Int, val z: Int) {
    val template: RoomTemplate get() = PohRooms.ALL[type]
    val doors: Set<Side> get() = PohRooms.doors(type, rotation)

    fun at(floor: Int, x: Int, z: Int): Boolean = this.floor == floor && this.x == x && this.z == z
}

/**
 * The saved layout of a house: its [rooms] and the furniture built on their hotspots ([furniture] maps a hotspot key, see
 * [furnitureKey], to the first loc id of the piece built there). Encoding: docs/mechanics/construction.md H7.
 */
internal class HouseLayout(val rooms: MutableList<PlacedRoom>, val furniture: MutableMap<Int, Int>) {
    fun roomAt(floor: Int, x: Int, z: Int): PlacedRoom? = rooms.firstOrNull { it.at(floor, x, z) }

    companion object {
        private const val LAYER_SHIFT = 6
        private const val Z_SHIFT = 8
        private const val X_SHIFT = 12
        private const val FLOOR_SHIFT = 16

        /**
         * Key of a hotspot: floor (2 bits), room cell x and z (4 bits each), the loc layer of its anchor loc (2 bits) and the
         * number of its anchor template tile (6 bits). A chair and the rug under it may share a tile, but not a layer.
         */
        fun furnitureKey(floor: Int, x: Int, z: Int, layer: Int, tile: Int): Int =
            (floor shl FLOOR_SHIFT) or (x shl X_SHIFT) or (z shl Z_SHIFT) or (layer shl LAYER_SHIFT) or tile

        /** The key bits that name the room cell of a [furnitureKey]. */
        fun cellOf(key: Int): Int = key ushr Z_SHIFT

        fun cellKey(floor: Int, x: Int, z: Int): Int = furnitureKey(floor, x, z, 0, 0) ushr Z_SHIFT

        /** The loc layer of a loc shape: walls 0, wall decor 1, ground 2, ground decor 3 (the client's layer rule). */
        fun layerOf(shape: Int): Int =
            when {
                shape <= WALL_LAST -> 0
                shape <= WALL_DECOR_LAST -> 1
                shape <= GROUND_LAST -> 2
                else -> 3
            }

        private const val WALL_LAST = 3
        private const val WALL_DECOR_LAST = 8
        private const val GROUND_LAST = 21

        /** The first house (Wiki "Player-owned house"): a garden with the exit portal and a parlour south of it, turned to face it. */
        fun starter(): HouseLayout {
            val rooms =
                mutableListOf(
                    PlacedRoom(PohRooms.GARDEN, 0, PohRooms.GROUND, START_X, START_Z),
                    PlacedRoom(PohRooms.PARLOUR, PARLOUR_TURNS, PohRooms.GROUND, START_X, START_Z - 1),
                )
            return HouseLayout(rooms, HashMap())
        }

        /** The starter garden is the middle cell of the 3x3 house of a level 1 player. */
        const val START_X = 2
        const val START_Z = 2

        /** The parlour has no door on its north side (fireplace); two turns put its doors on the north, east and west. */
        const val PARLOUR_TURNS = 2
    }
}

/** Reads and writes a [HouseLayout] in the player's saved values (docs/mechanics/construction.md H7). */
internal object PohSave {
    private const val ROOM_COUNT = "poh.rooms.count"
    private const val ROOM_KEY = "poh.rooms."
    private const val FURN_COUNT = "poh.furn.count"
    private const val FURN_KEY = "poh.furn."

    /** Rooms per saved Long; a room slot is type + 1 (6 bits, the rest of the slot), rotation (2), floor (2), x (4) and z (4) bits. */
    private const val ROOMS_PER_LONG = 3
    private const val SLOT_BITS = 18
    private const val SLOT_MASK = (1L shl SLOT_BITS) - 1
    private const val ROT_BITS = 2
    private const val FLOOR_BITS = 2
    private const val CELL_BITS = 4

    /** A furniture entry: the loc id above the 18-bit hotspot key. */
    private const val KEY_BITS = 18
    private const val KEY_MASK = (1L shl KEY_BITS) - 1

    /** The saved layout, or null if the player has no house yet. */
    fun load(p: PlayerRef): HouseLayout? {
        val roomCount = p.saved(ROOM_COUNT)?.toInt() ?: return null
        val rooms = ArrayList<PlacedRoom>(roomCount)
        for (i in 0 until roomCount) {
            val packed = p.saved(ROOM_KEY + i / ROOMS_PER_LONG) ?: continue
            val slot = (packed ushr (SLOT_BITS * (i % ROOMS_PER_LONG))) and SLOT_MASK
            unpackRoom(slot)?.let { rooms += it }
        }
        val furniture = HashMap<Int, Int>()
        val furnCount = p.saved(FURN_COUNT)?.toInt() ?: 0
        for (i in 0 until furnCount) {
            val entry = p.saved(FURN_KEY + i) ?: continue
            furniture[(entry and KEY_MASK).toInt()] = (entry ushr KEY_BITS).toInt()
        }
        return HouseLayout(rooms, furniture)
    }

    fun save(p: PlayerRef, layout: HouseLayout) {
        val oldRooms = p.saved(ROOM_COUNT)?.toInt() ?: 0
        val oldFurn = p.saved(FURN_COUNT)?.toInt() ?: 0
        val longs = (layout.rooms.size + ROOMS_PER_LONG - 1) / ROOMS_PER_LONG
        for (l in 0 until longs) {
            var packed = 0L
            for (j in 0 until ROOMS_PER_LONG) {
                val room = layout.rooms.getOrNull(l * ROOMS_PER_LONG + j) ?: continue
                packed = packed or (packRoom(room) shl (SLOT_BITS * j))
            }
            p.setSaved(ROOM_KEY + l, packed)
        }
        for (l in longs until (oldRooms + ROOMS_PER_LONG - 1) / ROOMS_PER_LONG) p.setSaved(ROOM_KEY + l, null)
        p.setSaved(ROOM_COUNT, layout.rooms.size.toLong())
        var n = 0
        for ((key, loc) in layout.furniture) p.setSaved(FURN_KEY + n++, (loc.toLong() shl KEY_BITS) or key.toLong())
        for (i in n until oldFurn) p.setSaved(FURN_KEY + i, null)
        p.setSaved(FURN_COUNT, n.toLong())
    }

    private fun packRoom(r: PlacedRoom): Long {
        var v = (r.type + 1).toLong()
        v = (v shl ROT_BITS) or r.rotation.toLong()
        v = (v shl FLOOR_BITS) or r.floor.toLong()
        v = (v shl CELL_BITS) or r.x.toLong()
        return (v shl CELL_BITS) or r.z.toLong()
    }

    private fun unpackRoom(slot: Long): PlacedRoom? {
        val cell = (1 shl CELL_BITS) - 1
        val z = (slot and cell.toLong()).toInt()
        val x = ((slot ushr CELL_BITS) and cell.toLong()).toInt()
        val floor = ((slot ushr (2 * CELL_BITS)) and ((1L shl FLOOR_BITS) - 1)).toInt()
        val rotation = ((slot ushr (2 * CELL_BITS + FLOOR_BITS)) and ((1L shl ROT_BITS) - 1)).toInt()
        val type = (slot ushr (2 * CELL_BITS + FLOOR_BITS + ROT_BITS)).toInt() - 1
        if (type !in PohRooms.ALL.indices) return null
        return PlacedRoom(type, rotation, floor, x, z)
    }
}
