package tp.engine.zone

import tp.engine.entity.BuildArea
import tp.engine.entity.Player
import tp.protocol.api.ServerMessage
import tp.protocol.api.ZoneOp

/**
 * Per-player zone synchronisation in the info pass (structure from RS Mod `PlayerZoneUpdateProcessor`): the player
 * observes the zones within [VIEW_RADIUS] of their own zone that lie inside the build area. A newly observed zone is
 * reset and gets its full state; an already observed zone gets this tick's events.
 */
internal class ZoneSync {
    // Observed zones as packed keys in two ping-pong arrays: no boxing or allocation per tick (hot loop, brief §11).
    private var observed = IntArray(MAX_ZONES)
    private var observedCount = 0
    private var scratch = IntArray(MAX_ZONES)
    private var lastZone = -1

    /** The client cleared every zone (rebuild / login). */
    fun reset() {
        observedCount = 0
        lastZone = -1
    }

    /** [centre]: the player's tile, or the pivot of the world entity they stand on (world-entities.md WE4). */
    fun process(player: Player, objs: GroundObjs, locs: tp.engine.map.DynamicLocs, centre: tp.util.CoordGrid = player.coord) {
        val zone = ZoneKey.of(centre)
        if (zone.packed == lastZone) {
            for (i in 0 until observedCount) sendEvents(player, ZoneKey(observed[i]), objs, locs)
            return
        }
        val now = scratch
        var nowCount = 0
        for (dx in -VIEW_RADIUS..VIEW_RADIUS) {
            for (dz in -VIEW_RADIUS..VIEW_RADIUS) {
                val key = ZoneKey.of(zone.level, zone.zoneX + dx, zone.zoneZ + dz)
                if (inBuildArea(player, key)) now[nowCount++] = key.packed
            }
        }
        for (i in 0 until nowCount) {
            val key = ZoneKey(now[i])
            // A zone seen for the first time gets its full state, not this tick's events (they're already in it).
            if (wasObserved(now[i])) sendEvents(player, key, objs, locs) else sendFull(player, key, objs, locs)
        }
        scratch = observed
        observed = now
        observedCount = nowCount
        lastZone = zone.packed
    }

    private fun wasObserved(packed: Int): Boolean {
        for (i in 0 until observedCount) if (observed[i] == packed) return true
        return false
    }

    private fun inBuildArea(player: Player, key: ZoneKey): Boolean {
        val dx = key.baseX - player.buildArea.x
        val dz = key.baseZ - player.buildArea.z
        return dx in 0 until BuildArea.SIZE && dz in 0 until BuildArea.SIZE
    }

    private fun sendFull(player: Player, key: ZoneKey, objs: GroundObjs, locs: tp.engine.map.DynamicLocs) {
        val dx = key.baseX - player.buildArea.x
        val dz = key.baseZ - player.buildArea.z
        player.session.send(ServerMessage.ZoneReset(dx, dz, key.level))
        val ops = locs.zoneState(key) + objs.inZone(key).filter { it.visibleTo(player.username) }.map(::add)
        if (ops.isNotEmpty()) player.session.send(ServerMessage.ZoneOps(dx, dz, key.level, ops))
    }

    private fun sendEvents(player: Player, key: ZoneKey, objs: GroundObjs, locs: tp.engine.map.DynamicLocs) {
        val locOps = locs.events[key.packed].orEmpty()
        val events = objs.events[key.packed]
        if (events == null && locOps.isEmpty()) return
        val ops = locOps + events.orEmpty().mapNotNull { messageFor(player.username, it) }
        if (ops.isEmpty()) return
        player.session.send(ServerMessage.ZoneOps(key.baseX - player.buildArea.x, key.baseZ - player.buildArea.z, key.level, ops))
    }

    /** What [viewer] sees of [e]: adds/removes only for objs they could see; a reveal is an add for everyone else. */
    private fun messageFor(viewer: String, e: ZoneEvent): ZoneOp? {
        val couldSee = e.previousOwner == null || e.previousOwner == viewer
        // A transient's previousOwner is its only viewer (a private map anim), else everyone sees it.
        val obj = e.obj ?: return if (couldSee) e.transient else null
        return when (e.kind) {
            ZoneEvent.Kind.ADD -> if (couldSee) add(obj) else null
            ZoneEvent.Kind.DEL -> if (couldSee) del(obj) else null
            ZoneEvent.Kind.REVEAL -> if (e.previousOwner != viewer && !obj.removed) add(obj) else null
            ZoneEvent.Kind.TRANSIENT -> e.transient
        }
    }

    private fun add(obj: GroundObj): ZoneOp = ZoneOp.ObjAdd(obj.coord.x and IN_ZONE, obj.coord.z and IN_ZONE, obj.id, obj.count)

    private fun del(obj: GroundObj): ZoneOp = ZoneOp.ObjDel(obj.coord.x and IN_ZONE, obj.coord.z and IN_ZONE, obj.id, obj.count)

    private companion object {
        /** Zones observed around the player's zone (RS Mod `ZONE_VIEW_RADIUS` = 3: a 7x7 zone square). */
        const val VIEW_RADIUS = 3
        const val IN_ZONE = 7
        const val MAX_ZONES = (2 * VIEW_RADIUS + 1) * (2 * VIEW_RADIUS + 1)
    }
}
