package tp.engine.map

import org.rsmod.routefinder.flag.CollisionFlag
import tp.cache.config.TypeList
import tp.cache.config.loc.LocType
import tp.engine.interact.InteractionTarget
import tp.engine.zone.ZoneKey
import tp.util.CoordGrid

/**
 * Instanced map areas of one world (docs/mechanics/instances.md): player-owned houses, boss lairs and minigames built
 * from template zones of the cache map. An instance is a block of map squares in "instance space", real coordinates
 * where the cache has no map, so movement, collision, zone updates and the info packets work on it unchanged. Each of
 * its zones is a copy of a template zone, optionally rotated: the server copies the collision and locs (IN2, IN3) and
 * the client is told which template zone to draw where with REBUILD_REGION (IN4).
 */
class Instances internal constructor(
    private val statics: StaticLocs,
    private val types: TypeList<LocType>,
    private val locs: DynamicLocs,
    private val collision: WorldCollision,
) {
    /** One instance: [widthSquares] × [lengthSquares] map squares from square ([squareX], [squareZ]). */
    class Instance internal constructor(
        val id: Int,
        val squareX: Int,
        val squareZ: Int,
        val widthSquares: Int,
        val lengthSquares: Int,
    ) {
        val baseX: Int get() = squareX shl SQUARE_SHIFT
        val baseZ: Int get() = squareZ shl SQUARE_SHIFT

        /** Copied zones: [ZoneKey] packed -> reference zone (protocol-api `RebuildRegion` layout). */
        internal val zones = HashMap<Int, Int>()

        /** Bumped on every change, so players inside get a fresh REBUILD_REGION (IN4). */
        var version: Int = 0
            internal set

        var destroyed: Boolean = false
            internal set

        operator fun contains(coord: CoordGrid): Boolean =
            (coord.x shr SQUARE_SHIFT) - squareX in 0 until widthSquares && (coord.z shr SQUARE_SHIFT) - squareZ in 0 until lengthSquares
    }

    private val bySquare = HashMap<Int, Instance>()
    private var nextId = 1

    /** Allocates a free block of [widthSquares] × [lengthSquares] map squares in instance space (IN1). */
    fun create(widthSquares: Int, lengthSquares: Int): Instance {
        require(widthSquares in 1..MAX_SQUARES && lengthSquares in 1..MAX_SQUARES) { "instance size $widthSquares×$lengthSquares" }
        for (sx in FIRST_SQUARE_X..LAST_SQUARE - widthSquares) {
            for (sz in 0..LAST_SQUARE - lengthSquares) {
                if (!free(sx, sz, widthSquares, lengthSquares)) continue
                val instance = Instance(nextId++, sx, sz, widthSquares, lengthSquares)
                forSquares(instance) { bySquare[it] = instance }
                return instance
            }
        }
        error("instance space exhausted")
    }

    /** The live instance holding [coord], or null. */
    fun at(coord: CoordGrid): Instance? = bySquare[squareKey(coord.x shr SQUARE_SHIFT, coord.z shr SQUARE_SHIFT)]

    /** Whether [coord] lies in instance space (whether or not an instance is there now). */
    fun isInstanceSpace(coord: CoordGrid): Boolean = coord.x shr SQUARE_SHIFT >= FIRST_SQUARE_X

    /**
     * Copies template zone [from] (its zone and level) to the instance zone holding [to], turned [rotation] quarter
     * turns clockwise (0..3), replacing what was there (IN2, IN3).
     */
    fun copyZone(instance: Instance, from: CoordGrid, to: CoordGrid, rotation: Int) {
        require(to in instance && !instance.destroyed) { "$to is not in instance ${instance.id}" }
        require(rotation in 0..MAX_ROTATION) { "rotation $rotation" }
        val dstZoneX = to.x shr ZONE_SHIFT
        val dstZoneZ = to.z shr ZONE_SHIFT
        clear(instance, to.level, dstZoneX, dstZoneZ)
        collision.resetZone(to.level, dstZoneX, dstZoneZ)
        val template = statics.zone(from.level, from.x shr ZONE_SHIFT, from.z shr ZONE_SHIFT)
        copyTiles(template, to.level, dstZoneX, dstZoneZ, rotation)
        for (loc in template.locs) locs.placeBase(rotated(loc, to.level, dstZoneX, dstZoneZ, rotation))
        instance.zones[ZoneKey.of(to.level, dstZoneX, dstZoneZ).packed] =
            reference(from.level, from.x shr ZONE_SHIFT, from.z shr ZONE_SHIFT, rotation)
        instance.version++
    }

    /** Empties the instance zone holding [at]: no collision (blocked), no locs, nothing drawn. */
    fun clearZone(instance: Instance, at: CoordGrid) {
        require(at in instance) { "$at is not in instance ${instance.id}" }
        clear(instance, at.level, at.x shr ZONE_SHIFT, at.z shr ZONE_SHIFT)
        instance.version++
    }

    /** Tears the instance down and frees its squares (IN5). Players still inside must be moved out first. */
    fun destroy(instance: Instance) {
        if (instance.destroyed) return
        for (packed in instance.zones.keys.toList()) {
            val zone = ZoneKey(packed)
            clear(instance, zone.level, zone.zoneX, zone.zoneZ)
        }
        instance.destroyed = true
        instance.version++
        forSquares(instance) { bySquare.remove(it) }
    }

    /**
     * The reference zones of the 13 × 13 build area centred on zone ([centreZoneX], [centreZoneZ]), 4 levels, in the
     * order of protocol-api `ServerMessage.RebuildRegion` (level, then x, then z); -1 where nothing is drawn.
     */
    fun buildArea(instance: Instance, centreZoneX: Int, centreZoneZ: Int): IntArray {
        val out = IntArray(LEVELS * VIEW_ZONES * VIEW_ZONES) { NO_ZONE }
        var i = 0
        for (level in 0 until LEVELS) {
            for (zx in centreZoneX - VIEW_RADIUS..centreZoneX + VIEW_RADIUS) {
                for (zz in centreZoneZ - VIEW_RADIUS..centreZoneZ + VIEW_RADIUS) {
                    out[i++] = instance.zones[ZoneKey.of(level, zx, zz).packed] ?: NO_ZONE
                }
            }
        }
        return out
    }

    private fun clear(instance: Instance, level: Int, zoneX: Int, zoneZ: Int) {
        val zone = ZoneKey.of(level, zoneX, zoneZ)
        locs.clearZone(zone)
        collision.freeZone(level, zoneX, zoneZ)
        instance.zones.remove(zone.packed)
    }

    private fun copyTiles(template: StaticLocs.ZoneTemplate, level: Int, zoneX: Int, zoneZ: Int, rotation: Int) {
        val baseX = zoneX shl ZONE_SHIFT
        val baseZ = zoneZ shl ZONE_SHIFT
        for (dx in 0 until ZONE_SIZE) {
            for (dz in 0 until ZONE_SIZE) {
                val bits = template.tiles[dx * ZONE_SIZE + dz]
                if (bits == 0) continue
                val x = baseX + rotateX(dx, dz, 1, 1, rotation)
                val z = baseZ + rotateZ(dx, dz, 1, 1, rotation)
                if (bits and StaticLocs.ZoneTemplate.BLOCKED != 0) collision.flags.add(x, z, level, CollisionFlag.BLOCK_WALK)
                if (bits and StaticLocs.ZoneTemplate.ROOF != 0) collision.flags.add(x, z, level, CollisionFlag.ROOF)
            }
        }
    }

    /** [loc] moved into the destination zone, turned like the client turns a copied zone (IN3). */
    private fun rotated(loc: InteractionTarget.Loc, level: Int, zoneX: Int, zoneZ: Int, rotation: Int): InteractionTarget.Loc {
        val type = types[loc.id]
        val odd = loc.angle and 1 == 1
        val width = type?.width ?: 1
        val length = type?.length ?: 1
        val sizeX = if (odd) length else width
        val sizeZ = if (odd) width else length
        val dx = loc.coord.x and ZONE_MASK
        val dz = loc.coord.z and ZONE_MASK
        val x = (zoneX shl ZONE_SHIFT) + rotateX(dx, dz, sizeX, sizeZ, rotation)
        val z = (zoneZ shl ZONE_SHIFT) + rotateZ(dx, dz, sizeX, sizeZ, rotation)
        return InteractionTarget.Loc(
            CoordGrid(x, z, level),
            loc.id,
            loc.shape,
            (loc.angle + rotation) and MAX_ROTATION,
            loc.width,
            loc.length,
            loc.forceApproach,
        )
    }

    private fun free(sx: Int, sz: Int, w: Int, l: Int): Boolean {
        for (x in sx until sx + w) {
            for (z in sz until sz + l) {
                if (bySquare.containsKey(squareKey(x, z)) || statics.hasSquare(x, z)) return false
            }
        }
        return true
    }

    private inline fun forSquares(instance: Instance, action: (Int) -> Unit) {
        for (x in instance.squareX until instance.squareX + instance.widthSquares) {
            for (z in instance.squareZ until instance.squareZ + instance.lengthSquares) action(squareKey(x, z))
        }
    }

    companion object {
        /** Reference zone of protocol-api `ServerMessage.RebuildRegion` (RSProt `ReferenceZone` packing). */
        fun reference(level: Int, zoneX: Int, zoneZ: Int, rotation: Int): Int =
            ((rotation and MAX_ROTATION) shl 1) or ((zoneZ and REF_Z_MASK) shl REF_Z_SHIFT) or
                ((zoneX and REF_X_MASK) shl REF_X_SHIFT) or ((level and LEVEL_MASK) shl REF_LEVEL_SHIFT)

        /**
         * Client scene-copy rotation of a [sizeX] × [sizeZ] footprint at zone-local ([x], [z]): quarter turns clockwise
         * (the client's `rotateObjectX` / `rotateObjectZ` as in RuneLite's deobfuscated `Client`).
         */
        fun rotateX(x: Int, z: Int, sizeX: Int, sizeZ: Int, rotation: Int): Int =
            when (rotation and MAX_ROTATION) {
                0 -> x
                1 -> z
                2 -> ZONE_MASK - x - (sizeX - 1)
                else -> ZONE_MASK - z - (sizeZ - 1)
            }

        fun rotateZ(x: Int, z: Int, sizeX: Int, sizeZ: Int, rotation: Int): Int =
            when (rotation and MAX_ROTATION) {
                0 -> z
                1 -> ZONE_MASK - x - (sizeX - 1)
                2 -> ZONE_MASK - z - (sizeZ - 1)
                else -> x
            }

        /** Squares x 100+ hold no cache map in rev 242 (checked at allocation with [StaticLocs.hasSquare]). */
        const val FIRST_SQUARE_X = 100
        private const val LAST_SQUARE = 255
        private const val MAX_SQUARES = 4
        private const val SQUARE_SHIFT = 6
        private const val SQUARE_AXIS_BITS = 8
        private const val ZONE_SHIFT = 3
        private const val ZONE_SIZE = 8
        private const val ZONE_MASK = 7
        private const val MAX_ROTATION = 3
        private const val LEVELS = 4
        private const val LEVEL_MASK = 3
        private const val VIEW_ZONES = 13
        private const val VIEW_RADIUS = 6
        const val NO_ZONE = -1
        private const val REF_Z_MASK = 0x7FF
        private const val REF_X_MASK = 0x3FF
        private const val REF_Z_SHIFT = 3
        private const val REF_X_SHIFT = 14
        private const val REF_LEVEL_SHIFT = 24

        private fun squareKey(x: Int, z: Int): Int = (x shl SQUARE_AXIS_BITS) or z
    }
}
