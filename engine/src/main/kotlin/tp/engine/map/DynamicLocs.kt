package tp.engine.map

import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.cache.config.TypeList
import tp.cache.config.loc.LocType
import tp.cache.map.LocCollision
import tp.engine.interact.InteractionTarget
import tp.engine.zone.ZoneKey
import tp.protocol.api.ZoneOp
import tp.util.CoordGrid

/**
 * World-scoped loc changes on top of the cache map (doors, spawned traps and webs, depleted resources): a loc can be
 * added where there is none, deleted, or replaced by another id, optionally reverting after a number of ticks. Each
 * change updates this world's collision and is sent to observers as LOC_ADD_CHANGE / LOC_DEL (zone updates, like
 * RS Mod's `LocRegistry` + `ZoneUpdateTransformer`). A tile holds one loc per layer: walls (shapes 0–3), wall decor
 * (4–8), ground (9–21) and ground decor (22) — the client's layer rule.
 */
class DynamicLocs internal constructor(
    private val types: TypeList<LocType>,
    private val statics: StaticLocs,
    private val collision: WorldCollision,
) {
    /** One overridden layer: [id] -1 = deleted. [base] is the cache loc it replaces (null if the tile had none). */
    internal class Change(
        val level: Int,
        val x: Int,
        val z: Int,
        val layer: Int,
        val id: Int,
        val shape: Int,
        val angle: Int,
        val base: InteractionTarget.Loc?,
        val revertAt: Long,
    )

    private val changes = HashMap<Long, Change>()
    private val byZone = HashMap<Int, MutableList<Change>>()

    /**
     * Instance base locs (instances.md IN3): locs copied from template zones into instance space. They play the part of
     * the cache map there: no zone updates are sent for them (the client builds them from the region rebuild), and
     * dynamic changes layer on top of them as on top of cache locs.
     */
    private val bases = HashMap<Long, InteractionTarget.Loc>()
    private val basesByZone = HashMap<Int, MutableList<Long>>()

    /** This tick's zone ops per zone (sent to observers in the info pass, then cleared). */
    internal val events = HashMap<Int, MutableList<ZoneOp>>()

    /** The loc [id] whose origin is the tile, as the world currently is (dynamic first, then the cache). */
    fun find(level: Int, x: Int, z: Int, id: Int): InteractionTarget.Loc? {
        for (layer in 0 until LAYERS) {
            val c = changes[key(level, x, z, layer)] ?: continue
            if (c.id == id) return target(c)
        }
        for (layer in 0 until LAYERS) {
            val b = bases[key(level, x, z, layer)] ?: continue
            if (b.id == id) return if (changes.containsKey(key(level, x, z, layer))) null else b
        }
        val base = statics.find(level, x, z, id) ?: return null
        return if (changes.containsKey(key(level, x, z, layerOf(base.shape)))) null else base
    }

    /** The loc on [layer] at the tile now (dynamic or cache), or null. */
    fun at(level: Int, x: Int, z: Int, layer: Int): InteractionTarget.Loc? {
        val c = changes[key(level, x, z, layer)]
        if (c != null) return if (c.id < 0) null else target(c)
        return bases[key(level, x, z, layer)] ?: statics.atLayer(level, x, z, layer)
    }

    /** Puts loc [id] at the tile (replacing what is on its layer); reverts after [duration] ticks if positive. */
    fun add(id: Int, coord: CoordGrid, shape: Int, angle: Int, tick: Long, duration: Int = -1) {
        val layer = layerOf(shape)
        val current = at(coord.level, coord.x, coord.z, layer)
        if (current != null) toggle(current, add = false)
        val base = baseOf(coord.level, coord.x, coord.z, layer)
        set(Change(coord.level, coord.x, coord.z, layer, id, shape, angle, base, if (duration > 0) tick + duration else -1))
        val placed = InteractionTarget.Loc(coord, id, shape, angle, width(id), length(id), 0)
        toggle(placed, add = true)
        event(coord, ZoneOp.LocAddChange(coord.x and IN_ZONE, coord.z and IN_ZONE, id, shape, angle))
    }

    /** Removes the loc on [shape]'s layer at the tile; reverts after [duration] ticks if positive. */
    fun delete(coord: CoordGrid, shape: Int, tick: Long, duration: Int = -1) {
        val layer = layerOf(shape)
        val current = at(coord.level, coord.x, coord.z, layer) ?: return
        toggle(current, add = false)
        val base = baseOf(coord.level, coord.x, coord.z, layer)
        if (base == null) {
            remove(key(coord.level, coord.x, coord.z, layer))
        } else {
            set(
                Change(
                    coord.level,
                    coord.x,
                    coord.z,
                    layer,
                    -1,
                    current.shape,
                    current.angle,
                    base,
                    if (duration >
                        0
                    ) {
                        tick + duration
                    } else {
                        -1
                    },
                ),
            )
        }
        event(coord, ZoneOp.LocDel(coord.x and IN_ZONE, coord.z and IN_ZONE, current.shape, current.angle))
    }

    /** Restores the cache state of the tile's [layer] (or nothing, if the cache has no loc there). */
    fun revert(level: Int, x: Int, z: Int, layer: Int) {
        val k = key(level, x, z, layer)
        val c = changes[k] ?: return
        val coord = CoordGrid(x, z, level)
        if (c.id >= 0) {
            toggle(target(c), add = false)
            event(coord, ZoneOp.LocDel(x and IN_ZONE, z and IN_ZONE, c.shape, c.angle))
        }
        remove(k)
        val base = c.base ?: return
        toggle(base, add = true)
        event(coord, ZoneOp.LocAddChange(x and IN_ZONE, z and IN_ZONE, base.id, base.shape, base.angle))
    }

    /** Reverts changes whose time is up (world post-tick). */
    internal fun process(tick: Long) {
        if (changes.isEmpty()) return
        val due = changes.values.filter { it.revertAt in 0..tick }
        for (c in due) revert(c.level, c.x, c.z, c.layer)
    }

    /** Full state of a zone for a newly observing player. */
    internal fun zoneState(zone: ZoneKey): List<ZoneOp> =
        byZone[zone.packed].orEmpty().map { c ->
            if (c.id < 0) {
                ZoneOp.LocDel(c.x and IN_ZONE, c.z and IN_ZONE, c.shape, c.angle)
            } else {
                ZoneOp.LocAddChange(c.x and IN_ZONE, c.z and IN_ZONE, c.id, c.shape, c.angle)
            }
        }

    /** Places an instance base loc (no zone update) and applies its collision. */
    internal fun placeBase(loc: InteractionTarget.Loc) {
        val k = key(loc.coord.level, loc.coord.x, loc.coord.z, layerOf(loc.shape))
        if (bases.put(k, loc) != null) return
        val zone = ZoneKey.of(loc.coord.level, loc.coord.x shr ZONE_SHIFT, loc.coord.z shr ZONE_SHIFT)
        basesByZone.getOrPut(zone.packed) { ArrayList() } += k
        toggle(loc, add = true)
    }

    /**
     * Forgets every base loc and dynamic change of a zone without zone updates (instance zone recopied or torn down);
     * its collision is reset by the caller.
     */
    internal fun clearZone(zone: ZoneKey) {
        basesByZone.remove(zone.packed)?.forEach { bases.remove(it) }
        byZone.remove(zone.packed)?.forEach { changes.remove(key(it.level, it.x, it.z, it.layer)) }
    }

    internal fun clearEvents() {
        events.clear()
    }

    private fun baseOf(level: Int, x: Int, z: Int, layer: Int): InteractionTarget.Loc? =
        changes[key(level, x, z, layer)]?.base ?: bases[key(level, x, z, layer)] ?: statics.atLayer(level, x, z, layer)

    private fun target(c: Change): InteractionTarget.Loc =
        InteractionTarget.Loc(CoordGrid(c.x, c.z, c.level), c.id, c.shape, c.angle, width(c.id), length(c.id), forceApproach(c.id))

    private fun width(id: Int) = types[id]?.width ?: 1

    private fun length(id: Int) = types[id]?.length ?: 1

    private fun forceApproach(id: Int) = types[id]?.forceApproach ?: 0

    private fun toggle(loc: InteractionTarget.Loc, add: Boolean) {
        val type = types[loc.id] ?: return
        val rotated = loc.angle == 1 || loc.angle == 3
        val w = if (rotated) type.length else type.width
        val l = if (rotated) type.width else type.length
        collision.ownZones(loc.coord.level, loc.coord.x, loc.coord.z, w, l)
        LocCollision.toggle(
            collision.flags,
            loc.coord.x,
            loc.coord.z,
            loc.coord.level,
            loc.shape,
            loc.angle,
            type.width,
            type.length,
            type.blockWalk,
            type.blockRange,
            type.breakRouteFinding,
            add,
        )
    }

    private fun set(c: Change) {
        val k = key(c.level, c.x, c.z, c.layer)
        remove(k)
        changes[k] = c
        byZone.getOrPut(ZoneKey.of(c.level, c.x shr ZONE_SHIFT, c.z shr ZONE_SHIFT).packed) { ArrayList() } += c
    }

    private fun remove(k: Long) {
        val old = changes.remove(k) ?: return
        byZone[ZoneKey.of(old.level, old.x shr ZONE_SHIFT, old.z shr ZONE_SHIFT).packed]?.remove(old)
    }

    private fun event(coord: CoordGrid, op: ZoneOp) {
        events.getOrPut(ZoneKey.of(coord).packed) { ArrayList() } += op
    }

    companion object {
        const val LAYERS = 4
        private const val IN_ZONE = 7
        private const val ZONE_SHIFT = 3

        /** Client layer of a loc shape: walls 0–3, wall decor 4–8, ground 9–21, ground decor 22. */
        fun layerOf(shape: Int): Int =
            when {
                shape <= LAST_WALL -> 0
                shape <= LAST_WALL_DECOR -> 1
                shape <= LAST_GROUND -> 2
                else -> GROUND_DECOR_LAYER
            }

        private const val LAST_WALL = 3
        private const val LAST_WALL_DECOR = 8
        private const val LAST_GROUND = 21
        private const val GROUND_DECOR_LAYER = 3

        private fun key(level: Int, x: Int, z: Int, layer: Int): Long =
            (level.toLong() shl LEVEL_SHIFT) or (x.toLong() shl X_SHIFT) or (z.toLong() shl Z_SHIFT) or layer.toLong()

        private const val LEVEL_SHIFT = 50
        private const val X_SHIFT = 30
        private const val Z_SHIFT = 4
    }
}

/**
 * This world's collision: it starts as a shallow copy of the shared cache collision (one array of zone references)
 * and copies a zone's tile flags the first time this world changes them, so worlds in one JVM never share edits.
 */
class WorldCollision(shared: CollisionFlagMap) {
    val flags: CollisionFlagMap = CollisionFlagMap(shared.flags.copyOf())
    private val owned = HashSet<Int>()

    /** Gives zone ([zoneX], [zoneZ]) on [level] fresh, empty (walkable) tiles owned by this world (instance space). */
    internal fun resetZone(level: Int, zoneX: Int, zoneZ: Int) {
        val index = index(level, zoneX, zoneZ)
        flags.flags[index] = IntArray(ZONE_TILES)
        owned.add(index)
    }

    /** Returns zone ([zoneX], [zoneZ]) on [level] to unallocated (fully blocked), as instance space starts. */
    internal fun freeZone(level: Int, zoneX: Int, zoneZ: Int) {
        val index = index(level, zoneX, zoneZ)
        flags.flags[index] = null
        owned.remove(index)
    }

    private fun index(level: Int, zx: Int, zz: Int): Int =
        (zx and AXIS_MASK) or ((zz and AXIS_MASK) shl Z_BITS) or ((level and LEVEL_MASK) shl LEVEL_BITS)

    /** Makes the zones under a [width]×[length] footprint (plus one tile around, for wall edges) private to this world. */
    internal fun ownZones(level: Int, x: Int, z: Int, width: Int, length: Int) {
        for (zx in ((x - 1) shr ZONE_SHIFT)..((x + width) shr ZONE_SHIFT)) {
            for (zz in ((z - 1) shr ZONE_SHIFT)..((z + length) shr ZONE_SHIFT)) {
                val index = index(level, zx, zz)
                if (!owned.add(index)) continue
                flags.flags[index] = flags.flags[index]?.copyOf()
            }
        }
    }

    private companion object {
        // rsmod-pathfinder `CollisionFlagMap.zoneIndex`.
        const val ZONE_SHIFT = 3
        const val AXIS_MASK = 0x7FF
        const val Z_BITS = 11
        const val LEVEL_MASK = 0x3
        const val LEVEL_BITS = 22
        const val ZONE_TILES = 64
    }
}
