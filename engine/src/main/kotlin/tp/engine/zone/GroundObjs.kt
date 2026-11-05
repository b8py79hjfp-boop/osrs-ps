package tp.engine.zone

import tp.engine.entity.Player
import tp.util.CoordGrid

/** 8x8 zone key: level(2) | zoneX(11) | zoneZ(11). */
@JvmInline
value class ZoneKey(val packed: Int) {
    val level: Int get() = packed ushr LEVEL_SHIFT
    val zoneX: Int get() = (packed ushr X_SHIFT) and AXIS_MASK
    val zoneZ: Int get() = packed and AXIS_MASK

    /** South-west tile x/z of the zone. */
    val baseX: Int get() = zoneX shl ZONE_SHIFT
    val baseZ: Int get() = zoneZ shl ZONE_SHIFT

    companion object {
        private const val ZONE_SHIFT = 3
        private const val X_SHIFT = 11
        private const val LEVEL_SHIFT = 22
        private const val AXIS_MASK = 0x7FF

        fun of(level: Int, zoneX: Int, zoneZ: Int): ZoneKey = ZoneKey((level shl LEVEL_SHIFT) or (zoneX shl X_SHIFT) or zoneZ)

        fun of(coord: CoordGrid): ZoneKey = of(coord.level, coord.x shr ZONE_SHIFT, coord.z shr ZONE_SHIFT)
    }
}

/**
 * An obj on the ground (docs/mechanics/ground-items.md). [owner] = the only player who can see it until it turns
 * public at [publicAt] (never, if -1); it disappears at [despawnAt]. Static spawns would have no owner.
 */
class GroundObj internal constructor(
    val id: Int,
    val count: Int,
    val coord: CoordGrid,
    /** Username of the player it is private to, or null when public. */
    owner: String?,
    internal val publicAt: Long,
    internal val despawnAt: Long,
) {
    var owner: String? = owner
        internal set

    internal var removed = false

    fun visibleTo(username: String): Boolean = owner == null || owner == username
}

/** What happened to an obj this tick, for zone updates in the info pass. */
internal class ZoneEvent(val kind: Kind, val obj: GroundObj, val previousOwner: String?) {
    enum class Kind { ADD, DEL, REVEAL }
}

/**
 * The world's ground objs by zone, their timers, and the per-tick zone event log the info pass sends. Only
 * [tp.engine.inv.ItemTransaction] adds or takes objs (brief §12); timers reveal and despawn them.
 */
class GroundObjs internal constructor() {
    private val byZone = HashMap<Int, MutableList<GroundObj>>()
    internal val events = HashMap<Int, MutableList<ZoneEvent>>()
    private val live = ArrayList<GroundObj>()

    fun at(coord: CoordGrid): List<GroundObj> = byZone[ZoneKey.of(coord).packed]?.filter { it.coord == coord }.orEmpty()

    internal fun inZone(zone: ZoneKey): List<GroundObj> = byZone[zone.packed].orEmpty()

    /** The obj [id] at [coord] that [viewer] can see (oldest first), or null. */
    fun find(coord: CoordGrid, id: Int, viewer: Player): GroundObj? =
        byZone[ZoneKey.of(coord).packed]?.firstOrNull { it.coord == coord && it.id == id && it.visibleTo(viewer.username) }

    internal fun add(obj: GroundObj) {
        val zone = ZoneKey.of(obj.coord).packed
        byZone.getOrPut(zone) { ArrayList() } += obj
        live += obj
        event(zone, ZoneEvent(ZoneEvent.Kind.ADD, obj, obj.owner))
    }

    internal fun remove(obj: GroundObj) {
        if (obj.removed) return
        obj.removed = true
        val zone = ZoneKey.of(obj.coord).packed
        byZone[zone]?.remove(obj)
        live.remove(obj)
        event(zone, ZoneEvent(ZoneEvent.Kind.DEL, obj, obj.owner))
    }

    /** Re-adds an obj removed in a rolled-back transaction. */
    internal fun restore(obj: GroundObj) {
        obj.removed = false
        add(obj)
    }

    /** Timers: reveal private objs, despawn expired ones. Runs once per tick in post-tick. */
    internal fun process(tick: Long) {
        var i = 0
        while (i < live.size) {
            val obj = live[i]
            if (tick >= obj.despawnAt) {
                remove(obj)
                continue
            }
            val owner = obj.owner
            if (owner != null && obj.publicAt in 0..tick) {
                obj.owner = null
                event(ZoneKey.of(obj.coord).packed, ZoneEvent(ZoneEvent.Kind.REVEAL, obj, owner))
            }
            i++
        }
    }

    private fun event(zone: Int, e: ZoneEvent) {
        events.getOrPut(zone) { ArrayList() } += e
    }

    internal fun clearEvents() {
        events.clear()
    }
}
