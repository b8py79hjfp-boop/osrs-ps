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
    /** Static spawn (G6): ticks until it reappears after being taken; 0 = an ordinary drop. */
    internal val respawn: Int = 0,
) {
    var owner: String? = owner
        internal set

    internal var removed = false

    fun visibleTo(username: String): Boolean = owner == null || owner == username
}

/** What happened in a zone this tick, for zone updates in the info pass. [transient] ops are seen by everyone once. */
internal class ZoneEvent(val kind: Kind, val obj: GroundObj?, val previousOwner: String?, val transient: tp.protocol.api.ZoneOp? = null) {
    enum class Kind { ADD, DEL, REVEAL, TRANSIENT }
}

/**
 * The world's ground objs by zone, their timers, and the per-tick zone event log the info pass sends. Only
 * [tp.engine.inv.ItemTransaction] adds or takes objs (brief §12); timers reveal and despawn them.
 */
class GroundObjs internal constructor() {
    private val byZone = HashMap<Int, MutableList<GroundObj>>()
    internal val events = HashMap<Int, MutableList<ZoneEvent>>()
    private val live = ArrayList<GroundObj>()

    /** G6: taken static spawns and the ticks left until each reappears. */
    private val respawning = LinkedHashMap<GroundObj, Int>()

    /** G6: a world-owned obj that is always there (public, never despawns) and reappears [respawn] ticks after a take. */
    fun spawnStatic(id: Int, count: Int, coord: CoordGrid, respawn: Int) {
        require(respawn > 0) { "static spawns respawn" }
        add(GroundObj(id, count, coord, null, -1, Long.MAX_VALUE, respawn))
    }

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
        if (obj.respawn > 0) respawning[obj] = obj.respawn
    }

    /** Re-adds an obj removed in a rolled-back transaction. */
    internal fun restore(obj: GroundObj) {
        respawning.remove(obj)
        obj.removed = false
        add(obj)
    }

    /** Timers: reveal private objs, despawn expired ones. Runs once per tick in post-tick. */
    internal fun process(tick: Long) {
        if (respawning.isNotEmpty()) respawnDue()
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

    private fun respawnDue() {
        val entries = respawning.entries.iterator()
        val due = ArrayList<GroundObj>(0)
        while (entries.hasNext()) {
            val e = entries.next()
            if (e.value > 1) {
                e.setValue(e.value - 1)
            } else {
                entries.remove()
                due += e.key
            }
        }
        for (o in due) add(GroundObj(o.id, o.count, o.coord, null, -1, Long.MAX_VALUE, o.respawn))
    }

    private fun event(zone: Int, e: ZoneEvent) {
        events.getOrPut(zone) { ArrayList() } += e
    }

    /** A one-off zone op (projectile) in the zone of [at], sent to every observer this tick. */
    internal fun transient(at: CoordGrid, op: tp.protocol.api.ZoneOp) {
        event(ZoneKey.of(at).packed, ZoneEvent(ZoneEvent.Kind.TRANSIENT, null, null, op))
    }

    internal fun clearEvents() {
        events.clear()
    }
}
