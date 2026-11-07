package tp.engine.zone

import tp.engine.entity.Player
import tp.engine.map.DynamicLocs
import tp.engine.map.WorldEntities
import tp.protocol.api.ServerMessage
import tp.protocol.api.ZoneOp
import tp.util.CoordGrid

/**
 * Per-player zone synchronisation of world-entity decks (docs/mechanics/world-entities.md WE6): every deck within
 * [VIEW_DISTANCE] of the player's root position gets its full zone state the first tick it is seen, then each tick's
 * loc changes, as zone messages addressed to that entity's world (the session sends them inside its block).
 */
internal class DeckSync {
    private val seen = HashSet<Int>()
    private val now = HashSet<Int>()

    fun process(player: Player, root: CoordGrid, entities: WorldEntities, locs: DynamicLocs, objs: GroundObjs) {
        now.clear()
        if (entities.all().isEmpty()) return seen.clear()
        for (entity in entities.all()) {
            if (!entity.visible || entity.level != root.level || !near(entity.rootCoord, root)) continue
            now += entity.index
            val fresh = entity.index !in seen
            for (packed in entity.instance.zones.keys) {
                val zone = ZoneKey(packed)
                val dx = zone.baseX - entity.instance.baseX
                val dz = zone.baseZ - entity.instance.baseZ
                if (fresh) full(player, entity.index, zone, dx, dz, locs, objs) else events(player, entity.index, zone, dx, dz, locs)
            }
        }
        seen.retainAll(now)
        seen += now
    }

    /** The client rebuilt its view; every deck is new again. */
    fun reset() {
        seen.clear()
    }

    private fun full(player: Player, world: Int, zone: ZoneKey, dx: Int, dz: Int, locs: DynamicLocs, objs: GroundObjs) {
        player.session.send(ServerMessage.ZoneReset(dx, dz, zone.level, world))
        val ops = locs.zoneState(zone) + objs.inZone(zone).filter { it.visibleTo(player.username) }.map(::add)
        if (ops.isNotEmpty()) player.session.send(ServerMessage.ZoneOps(dx, dz, zone.level, ops, world))
    }

    private fun events(player: Player, world: Int, zone: ZoneKey, dx: Int, dz: Int, locs: DynamicLocs) {
        val ops = locs.events[zone.packed] ?: return
        if (ops.isNotEmpty()) player.session.send(ServerMessage.ZoneOps(dx, dz, zone.level, ops, world))
    }

    private fun add(obj: GroundObj): ZoneOp = ZoneOp.ObjAdd(obj.coord.x and IN_ZONE, obj.coord.z and IN_ZONE, obj.id, obj.count)

    private fun near(a: CoordGrid, b: CoordGrid): Boolean = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)) <= VIEW_DISTANCE

    private companion object {
        /** RSProt `WorldEntityInfo.DEFAULT_RENDER_DISTANCE` (tiles). */
        const val VIEW_DISTANCE = 31
        const val IN_ZONE = 7
    }
}
