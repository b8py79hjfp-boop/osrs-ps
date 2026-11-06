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
    private var observed = HashSet<Int>()
    private var lastZone = -1

    /** The client cleared every zone (rebuild / login). */
    fun reset() {
        observed.clear()
        lastZone = -1
    }

    fun process(player: Player, objs: GroundObjs) {
        val zone = ZoneKey.of(player.coord)
        if (zone.packed == lastZone) {
            for (z in observed) sendEvents(player, ZoneKey(z), objs)
            return
        }
        val now = HashSet<Int>()
        for (dx in -VIEW_RADIUS..VIEW_RADIUS) {
            for (dz in -VIEW_RADIUS..VIEW_RADIUS) {
                val key = ZoneKey.of(zone.level, zone.zoneX + dx, zone.zoneZ + dz)
                if (inBuildArea(player, key)) now += key.packed
            }
        }
        for (z in now) {
            val key = ZoneKey(z)
            // A zone seen for the first time gets its full state, not this tick's events (they're already in it).
            if (z in observed) sendEvents(player, key, objs) else sendFull(player, key, objs)
        }
        observed = now
        lastZone = zone.packed
    }

    private fun inBuildArea(player: Player, key: ZoneKey): Boolean {
        val dx = key.baseX - player.buildArea.x
        val dz = key.baseZ - player.buildArea.z
        return dx in 0 until BuildArea.SIZE && dz in 0 until BuildArea.SIZE
    }

    private fun sendFull(player: Player, key: ZoneKey, objs: GroundObjs) {
        val dx = key.baseX - player.buildArea.x
        val dz = key.baseZ - player.buildArea.z
        player.session.send(ServerMessage.ZoneReset(dx, dz, key.level))
        val ops = objs.inZone(key).filter { it.visibleTo(player.username) }.map(::add)
        if (ops.isNotEmpty()) player.session.send(ServerMessage.ZoneOps(dx, dz, key.level, ops))
    }

    private fun sendEvents(player: Player, key: ZoneKey, objs: GroundObjs) {
        val events = objs.events[key.packed] ?: return
        val ops = events.mapNotNull { messageFor(player.username, it) }
        if (ops.isEmpty()) return
        player.session.send(ServerMessage.ZoneOps(key.baseX - player.buildArea.x, key.baseZ - player.buildArea.z, key.level, ops))
    }

    /** What [viewer] sees of [e]: adds/removes only for objs they could see; a reveal is an add for everyone else. */
    private fun messageFor(viewer: String, e: ZoneEvent): ZoneOp? {
        val couldSee = e.previousOwner == null || e.previousOwner == viewer
        return when (e.kind) {
            ZoneEvent.Kind.ADD -> if (couldSee) add(e.obj) else null
            ZoneEvent.Kind.DEL -> if (couldSee) del(e.obj) else null
            ZoneEvent.Kind.REVEAL -> if (e.previousOwner != viewer && !e.obj.removed) add(e.obj) else null
        }
    }

    private fun add(obj: GroundObj): ZoneOp = ZoneOp.ObjAdd(obj.coord.x and IN_ZONE, obj.coord.z and IN_ZONE, obj.id, obj.count)

    private fun del(obj: GroundObj): ZoneOp = ZoneOp.ObjDel(obj.coord.x and IN_ZONE, obj.coord.z and IN_ZONE, obj.id, obj.count)

    private companion object {
        /** Zones observed around the player's zone (RS Mod `ZONE_VIEW_RADIUS` = 3: a 7x7 zone square). */
        const val VIEW_RADIUS = 3
        const val IN_ZONE = 7
    }
}
