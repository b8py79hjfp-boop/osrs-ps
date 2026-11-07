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
)

/**
 * Boat ownership in the player's `saved` values (docs/mechanics/sailing-boats.md B10). Slot s (0..4) is one Long under
 * `sailing.boat.s`; bit 0 owned, then the fields of [OwnedBoat] at the shifts below (61 bits). Further values:
 * `sailing.lastboat` (slot boarded last, 0..4), `sailing.lastdock` (dock id last disembarked at, the escape / capsize
 * destination) and `sailing.quest.*` (Pandemonium, see [Pandemonium]).
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

    private fun mask(bits: Int): Long = (1L shl bits) - 1

    private fun key(slot: Int): String = KEY_PREFIX + slot

    /** The boat in [slot] (0 until [SLOTS]), or null if the slot is empty. */
    fun get(player: PlayerRef, slot: Int): OwnedBoat? {
        if (slot !in 0 until SLOTS) return null
        val v = player.saved(key(slot)) ?: return null
        if (v and 1L == 0L) return null
        return OwnedBoat(
            type = field(v, TYPE_SHIFT, TYPE_BITS),
            dock = field(v, DOCK_SHIFT, DOCK_BITS),
            hull = field(v, HULL_SHIFT, TIER_BITS),
            sail = field(v, SAIL_SHIFT, TIER_BITS),
            helm = field(v, HELM_SHIFT, TIER_BITS),
            keel = field(v, KEEL_SHIFT, TIER_BITS),
            hp = field(v, HP_SHIFT, HP_BITS),
            name = field(v, NAME_SHIFT, NAME_BITS),
        )
    }

    /** Writes [boat] into [slot]; null empties it. */
    fun set(player: PlayerRef, slot: Int, boat: OwnedBoat?) {
        require(slot in 0 until SLOTS) { "boat slot $slot" }
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
