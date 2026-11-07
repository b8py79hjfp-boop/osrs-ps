package tp.content.sailing

import tp.plugin.PlayerRef

/**
 * One owned boat (a boat slot), as persisted. Parts are hull-tier indices 0..6 (wooden .. rosewood, the order of the db
 * option lists); the keel only counts for the skiff and sloop.
 */
internal data class OwnedBoat(
    /** `Sailing_Boat.TYPE_ID` 0 raft, 1 skiff, 2 sloop. */
    val type: Int,
    /** `Dock.id` the boat is moored at, or [BoatSave.AT_SEA]. */
    val dock: Int,
    val hull: Int,
    val sail: Int,
    val helm: Int,
    val keel: Int,
    /** Current hitpoints. */
    val hp: Int,
    /** Three name word indices, 8 bits each (db 187 name options, OPEN-B8: not shown yet). */
    val name: Int,
    /** Flag part: option index + 1 in `Sailing_Boat.FLAG` (0 none). Stored apart from the first Long ([BoatSave], S4). */
    val flag: Int = 0,
    /** Trim part: option index in `Sailing_Boat.TRIM` (0 is the "No trim" row). */
    val trim: Int = 0,
    /** Eternal brazier: option index + 1 in `Sailing_Boat.BRAZIER` (0 none). */
    val brazier: Int = 0,
    /** Sail pattern: option index in `Sailing_Boat.SAIL_PATTERN` (0 is the plain sail). */
    val pattern: Int = 0,
    /** Facility per hotspot: option index + 1 in the hotspot row's options, 0 empty (the encoding of `SAILING_BOAT_n_HOTSPOT_k`). */
    val hotspots: List<Int> = emptyList(),
    /** The boat is in a boat bottle (docs/mechanics/sailing-building.md BB5). */
    val bottled: Boolean = false,
    /** The boat capsized at sea and must be recovered from a shipwright (BB6). */
    val capsized: Boolean = false,
) {
    /** The facility option (index + 1) built on hotspot [index], 0 if empty. */
    fun hotspotAt(index: Int): Int = hotspots.getOrElse(index) { 0 }

    /** A copy with hotspot [index] set to [option] (index + 1, 0 to clear). */
    fun withHotspot(index: Int, option: Int): OwnedBoat {
        val list = ArrayList<Int>(maxOf(hotspots.size, index + 1))
        list.addAll(hotspots)
        while (list.size <= index) list.add(0)
        list[index] = option
        return copy(hotspots = list)
    }
}

/**
 * Boat ownership in the player's `saved` values (docs/mechanics/sailing-boats.md B10). Slot s (0..4) is one Long under
 * `sailing.boat.s`; bit 0 owned, then the fields of [OwnedBoat] at the shifts below (61 bits). Further values:
 * `sailing.lastboat` (slot boarded last, 0..4), `sailing.lastdock` (dock id last disembarked at, the escape / capsize
 * destination) and `sailing.quest.*` (Pandemonium, see [Pandemonium]).
 *
 * S4 adds two further keys per slot without touching the layout above (a save without them reads as "nothing built"):
 * `sailing.boatx.s` (cosmetics and flags: bits 1-4 flag, 5-10 trim, 11-12 brazier, 13-15 pattern, 16 bottled, 17 capsized) and
 * `sailing.boatf.s.0` / `.1` (facility per hotspot, 8 bits each, hotspots 0-7 and 8-12). Empty slots and all-default extras
 * leave no key behind. Further per-player S4 values: `sailing.fbottle.count` / `sailing.fbottle.n` ([FacilityBottles]).
 */
internal object BoatSave {
    /** Boat slots at Sailing 91 (docs/plans/sailing.md 2.1). */
    const val SLOTS: Int = 5

    /** Dock field value of a boat that is not moored anywhere (bit-pattern all ones). */
    const val AT_SEA: Int = 63

    const val LAST_BOAT_KEY: String = "sailing.lastboat"
    const val LAST_DOCK_KEY: String = "sailing.lastdock"

    private const val KEY_PREFIX = "sailing.boat."

    private const val TYPE_SHIFT = 1
    private const val TYPE_BITS = 2
    private const val DOCK_SHIFT = 3
    private const val DOCK_BITS = 6
    private const val HULL_SHIFT = 9
    private const val TIER_BITS = 3
    private const val SAIL_SHIFT = 12
    private const val HELM_SHIFT = 15
    private const val KEEL_SHIFT = 18
    private const val HP_SHIFT = 21
    private const val HP_BITS = 16
    private const val NAME_SHIFT = 37
    private const val NAME_BITS = 24

    private const val EXTRA_PREFIX = "sailing.boatx."
    private const val FACILITY_PREFIX = "sailing.boatf."
    private const val FLAG_SHIFT = 1
    private const val FLAG_BITS = 4
    private const val TRIM_SHIFT = 5
    private const val TRIM_BITS = 6
    private const val BRAZIER_SHIFT = 11
    private const val BRAZIER_BITS = 2
    private const val PATTERN_SHIFT = 13
    private const val PATTERN_BITS = 3
    private const val BOTTLED_BIT = 16
    private const val CAPSIZED_BIT = 17

    /** Most hotspots on a boat (the sloop, `Sailing_Boat.FACILITY_AMOUNT` 13) and hotspots per saved Long (8 bits each). */
    const val MAX_HOTSPOTS: Int = 13
    private const val HOTSPOTS_PER_LONG = 8
    private const val HOTSPOT_BITS = 8

    private fun mask(bits: Int): Long = (1L shl bits) - 1

    private fun key(slot: Int): String = KEY_PREFIX + slot

    /** The boat in [slot] (0 until [SLOTS]), or null if the slot is empty. */
    fun get(player: PlayerRef, slot: Int): OwnedBoat? {
        if (slot !in 0 until SLOTS) return null
        val v = player.saved(key(slot)) ?: return null
        if (v and 1L == 0L) return null
        val x = player.saved(EXTRA_PREFIX + slot) ?: 0L
        return OwnedBoat(
            type = field(v, TYPE_SHIFT, TYPE_BITS),
            dock = field(v, DOCK_SHIFT, DOCK_BITS),
            hull = field(v, HULL_SHIFT, TIER_BITS),
            sail = field(v, SAIL_SHIFT, TIER_BITS),
            helm = field(v, HELM_SHIFT, TIER_BITS),
            keel = field(v, KEEL_SHIFT, TIER_BITS),
            hp = field(v, HP_SHIFT, HP_BITS),
            name = field(v, NAME_SHIFT, NAME_BITS),
            flag = field(x, FLAG_SHIFT, FLAG_BITS),
            trim = field(x, TRIM_SHIFT, TRIM_BITS),
            brazier = field(x, BRAZIER_SHIFT, BRAZIER_BITS),
            pattern = field(x, PATTERN_SHIFT, PATTERN_BITS),
            hotspots = readHotspots(player, slot),
            bottled = x ushr BOTTLED_BIT and 1L == 1L,
            capsized = x ushr CAPSIZED_BIT and 1L == 1L,
        )
    }

    private fun readHotspots(player: PlayerRef, slot: Int): List<Int> {
        val lo = player.saved("$FACILITY_PREFIX$slot.0") ?: 0L
        val hi = player.saved("$FACILITY_PREFIX$slot.1") ?: 0L
        if (lo == 0L && hi == 0L) return emptyList()
        return List(MAX_HOTSPOTS) { i ->
            val word = if (i < HOTSPOTS_PER_LONG) lo else hi
            field(word, (i % HOTSPOTS_PER_LONG) * HOTSPOT_BITS, HOTSPOT_BITS)
        }
    }

    private fun writeExtras(player: PlayerRef, slot: Int, boat: OwnedBoat?) {
        val x =
            if (boat == null) {
                0L
            } else {
                put(boat.flag, FLAG_SHIFT, FLAG_BITS) or
                    put(boat.trim, TRIM_SHIFT, TRIM_BITS) or
                    put(boat.brazier, BRAZIER_SHIFT, BRAZIER_BITS) or
                    put(boat.pattern, PATTERN_SHIFT, PATTERN_BITS) or
                    (if (boat.bottled) 1L shl BOTTLED_BIT else 0L) or
                    (if (boat.capsized) 1L shl CAPSIZED_BIT else 0L)
            }
        player.setSaved(EXTRA_PREFIX + slot, if (x == 0L) null else x)
        var lo = 0L
        var hi = 0L
        for (i in 0 until MAX_HOTSPOTS) {
            val option = boat?.hotspotAt(i) ?: 0
            val bits = put(option, (i % HOTSPOTS_PER_LONG) * HOTSPOT_BITS, HOTSPOT_BITS)
            if (i < HOTSPOTS_PER_LONG) lo = lo or bits else hi = hi or bits
        }
        player.setSaved("$FACILITY_PREFIX$slot.0", if (lo == 0L) null else lo)
        player.setSaved("$FACILITY_PREFIX$slot.1", if (hi == 0L) null else hi)
    }

    /** Writes [boat] into [slot]; null empties it. */
    fun set(player: PlayerRef, slot: Int, boat: OwnedBoat?) {
        require(slot in 0 until SLOTS) { "boat slot $slot" }
        writeExtras(player, slot, boat)
        if (boat == null) return player.setSaved(key(slot), null)
        val v =
            1L or
                put(boat.type, TYPE_SHIFT, TYPE_BITS) or
                put(boat.dock, DOCK_SHIFT, DOCK_BITS) or
                put(boat.hull, HULL_SHIFT, TIER_BITS) or
                put(boat.sail, SAIL_SHIFT, TIER_BITS) or
                put(boat.helm, HELM_SHIFT, TIER_BITS) or
                put(boat.keel, KEEL_SHIFT, TIER_BITS) or
                put(boat.hp, HP_SHIFT, HP_BITS) or
                put(boat.name, NAME_SHIFT, NAME_BITS)
        player.setSaved(key(slot), v)
    }

    /** Slots that hold a boat. */
    fun owned(player: PlayerRef): List<Int> = (0 until SLOTS).filter { get(player, it) != null }

    /** Boats a player may own at Sailing level [level] (docs/plans/sailing.md 2.1: 1 / 15 / 50 / 78 / 91). */
    fun slotsAt(level: Int): Int = BOAT_LEVELS.count { level >= it }

    private val BOAT_LEVELS = intArrayOf(1, 15, 50, 78, 91)

    private fun field(v: Long, shift: Int, bits: Int): Int = ((v ushr shift) and mask(bits)).toInt()

    private fun put(value: Int, shift: Int, bits: Int): Long = (value.toLong() and mask(bits)) shl shift
}
