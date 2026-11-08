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

        /** Map squares copied whole with [Instances.squareCopies] (IN7: template-coordinate mapping). */
        internal val squares = ArrayList<SquareCopy>()

        /**
         * The instance tile of template tile [template] for a [sizeX] x [sizeZ] footprint (IN7), or null when no copied
         * square holds it.
         */
        fun fromTemplate(template: CoordGrid, sizeX: Int = 1, sizeZ: Int = 1): CoordGrid? {
            val copy = squares.firstOrNull { it.holdsTemplate(template) } ?: return null
            val lx = template.x and SQUARE_MASK
            val lz = template.z and SQUARE_MASK
            return CoordGrid(
                ((squareX + copy.dstDx) shl SQUARE_SHIFT) + rotateIn(lx, lz, sizeX, sizeZ, copy.rotation, SQUARE_MASK, true),
                ((squareZ + copy.dstDz) shl SQUARE_SHIFT) + rotateIn(lx, lz, sizeX, sizeZ, copy.rotation, SQUARE_MASK, false),
                template.level - copy.srcLevel + copy.dstLevel,
            )
        }

        /** The template tile an instance tile shows (inverse of [fromTemplate] for 1 x 1), or null. */
        fun toTemplate(coord: CoordGrid): CoordGrid? {
            val dx = (coord.x shr SQUARE_SHIFT) - squareX
            val dz = (coord.z shr SQUARE_SHIFT) - squareZ
            val copy =
                squares.firstOrNull { it.dstDx == dx && it.dstDz == dz && coord.level - it.dstLevel in 0 until it.levels } ?: return null
            val inverse = (ROTATIONS - copy.rotation) and MAX_ROTATION
            val lx = coord.x and SQUARE_MASK
            val lz = coord.z and SQUARE_MASK
            return CoordGrid(
                (copy.srcSquareX shl SQUARE_SHIFT) + rotateIn(lx, lz, 1, 1, inverse, SQUARE_MASK, true),
                (copy.srcSquareZ shl SQUARE_SHIFT) + rotateIn(lx, lz, 1, 1, inverse, SQUARE_MASK, false),
                coord.level - copy.dstLevel + copy.srcLevel,
            )
        }
    }

    /** One template map square copied whole into an instance: [levels] levels from [srcLevel] to [dstLevel]. */
    class SquareCopy(
        val srcSquareX: Int,
        val srcSquareZ: Int,
        val dstDx: Int,
        val dstDz: Int,
        val rotation: Int,
        val srcLevel: Int = 0,
        val dstLevel: Int = 0,
        val levels: Int = LEVELS,
    ) {
        internal fun holdsTemplate(t: CoordGrid): Boolean =
            t.x shr SQUARE_SHIFT == srcSquareX && t.z shr SQUARE_SHIFT == srcSquareZ && t.level in srcLevel until srcLevel + levels
    }

    /** One zone to copy: template tile [from] (its zone and level) to the instance zone holding [to], [rotation] turns. */
    class ZoneCopy(val from: CoordGrid, val to: CoordGrid, val rotation: Int)

    /**
     * A template zone turned [reference]'s rotation, ready to apply (IN6): the tiles' collision bits and the locs in
     * zone-local coordinates. Built once per (template zone, rotation) and shared by every instance.
     */
    internal class PreparedZone(
        val reference: Int,
        val tileOffsets: IntArray,
        val tileBits: IntArray,
        val locs: List<InteractionTarget.Loc>,
    )

    /** A built but not yet attached instance (IN6): its zones, prepared off the world thread. */
    class BuildPlan internal constructor(
        val instance: Instance,
        internal val zones: List<Pair<ZoneKey, PreparedZone>>,
        internal val squares: List<SquareCopy>,
    )

    private val prepared = java.util.concurrent.ConcurrentHashMap<Long, PreparedZone>()

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
     * turns clockwise (0..3), replacing what was there (IN2, IN3). World thread; for big builds use [prepare] + [attach].
     */
    fun copyZone(instance: Instance, from: CoordGrid, to: CoordGrid, rotation: Int) {
        require(to in instance && !instance.destroyed) { "$to is not in instance ${instance.id}" }
        require(rotation in 0..MAX_ROTATION) { "rotation $rotation" }
        apply(instance, ZoneKey.of(to.level, to.x shr ZONE_SHIFT, to.z shr ZONE_SHIFT), prepareZone(from, rotation))
        instance.version++
    }

    /**
     * IN6: prepares [copies] (and whole-square [squares], expanded with [squareCopies]) for [instance]. Thread-safe and
     * free of world state: run it off the world thread, then [attach] the plan on it.
     */
    fun prepare(instance: Instance, copies: List<ZoneCopy>, squares: List<SquareCopy> = emptyList()): BuildPlan {
        val all = copies + squares.flatMap { squareCopies(instance, it) }
        val zones =
            all.map { c ->
                require(c.to in instance) { "${c.to} is not in instance ${instance.id}" }
                ZoneKey.of(c.to.level, c.to.x shr ZONE_SHIFT, c.to.z shr ZONE_SHIFT) to prepareZone(c.from, c.rotation)
            }
        return BuildPlan(instance, zones, squares)
    }

    /** IN6: applies a prepared plan (world thread, pre-tick). */
    fun attach(plan: BuildPlan) {
        val instance = plan.instance
        if (instance.destroyed) return
        for ((zone, prep) in plan.zones) apply(instance, zone, prep)
        instance.squares += plan.squares
        instance.version++
    }

    /** IN7: the zone copies of template square [copy] turned as a whole: zone positions rotate inside the square too. */
    fun squareCopies(instance: Instance, copy: SquareCopy): List<ZoneCopy> {
        require(copy.rotation in 0..MAX_ROTATION) { "rotation ${copy.rotation}" }
        val out = ArrayList<ZoneCopy>(ZONES_PER_SQUARE * ZONES_PER_SQUARE * copy.levels)
        for (l in 0 until copy.levels) {
            for (zx in 0 until ZONES_PER_SQUARE) {
                for (zz in 0 until ZONES_PER_SQUARE) {
                    val from =
                        CoordGrid(
                            (copy.srcSquareX shl SQUARE_SHIFT) + (zx shl ZONE_SHIFT),
                            (copy.srcSquareZ shl SQUARE_SHIFT) + (zz shl ZONE_SHIFT),
                            copy.srcLevel + l,
                        )
                    val tx = rotateX(zx, zz, 1, 1, copy.rotation)
                    val tz = rotateZ(zx, zz, 1, 1, copy.rotation)
                    val to =
                        CoordGrid(
                            ((instance.squareX + copy.dstDx) shl SQUARE_SHIFT) + (tx shl ZONE_SHIFT),
                            ((instance.squareZ + copy.dstDz) shl SQUARE_SHIFT) + (tz shl ZONE_SHIFT),
                            copy.dstLevel + l,
                        )
                    out += ZoneCopy(from, to, copy.rotation)
                }
            }
        }
        return out
    }

    private fun prepareZone(from: CoordGrid, rotation: Int): PreparedZone {
        val zx = from.x shr ZONE_SHIFT
        val zz = from.z shr ZONE_SHIFT
        val ref = reference(from.level, zx, zz, rotation)
        return prepared.computeIfAbsent(ref.toLong()) {
            val template = statics.zone(from.level, zx, zz)
            val offsets = ArrayList<Int>()
            val bits = ArrayList<Int>()
            for (dx in 0 until ZONE_SIZE) {
                for (dz in 0 until ZONE_SIZE) {
                    val b = template.tiles[dx * ZONE_SIZE + dz]
                    if (b == 0) continue
                    offsets += (rotateX(dx, dz, 1, 1, rotation) shl ZONE_SHIFT) or rotateZ(dx, dz, 1, 1, rotation)
                    bits += b
                }
            }
            PreparedZone(ref, offsets.toIntArray(), bits.toIntArray(), template.locs.map { rotated(it, 0, 0, 0, rotation) })
        }
    }

    private fun apply(instance: Instance, zone: ZoneKey, prep: PreparedZone) {
        val level = zone.level
        clear(instance, level, zone.zoneX, zone.zoneZ)
        collision.resetZone(level, zone.zoneX, zone.zoneZ)
        val baseX = zone.zoneX shl ZONE_SHIFT
        val baseZ = zone.zoneZ shl ZONE_SHIFT
        for (i in prep.tileOffsets.indices) {
            val x = baseX + (prep.tileOffsets[i] shr ZONE_SHIFT)
            val z = baseZ + (prep.tileOffsets[i] and ZONE_MASK)
            val b = prep.tileBits[i]
            if (b and StaticLocs.ZoneTemplate.BLOCKED != 0) collision.flags.add(x, z, level, CollisionFlag.BLOCK_WALK)
            if (b and StaticLocs.ZoneTemplate.ROOF != 0) collision.flags.add(x, z, level, CollisionFlag.ROOF)
        }
        for (loc in prep.locs) {
            locs.placeBase(
                InteractionTarget.Loc(
                    CoordGrid(baseX + loc.coord.x, baseZ + loc.coord.z, level),
                    loc.id,
                    loc.shape,
                    loc.angle,
                    loc.width,
                    loc.length,
                    loc.forceApproach,
                ),
            )
        }
        instance.zones[zone.packed] = prep.reference
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
        instance.squares.clear()
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

        /** [rotateX] (when [x]) or [rotateZ] over a grid of side [mask] + 1 (a map square: 63). */
        internal fun rotateIn(px: Int, pz: Int, sizeX: Int, sizeZ: Int, rotation: Int, mask: Int, x: Boolean): Int =
            if (x) {
                when (rotation and MAX_ROTATION) {
                    0 -> px
                    1 -> pz
                    2 -> mask - px - (sizeX - 1)
                    else -> mask - pz - (sizeZ - 1)
                }
            } else {
                when (rotation and MAX_ROTATION) {
                    0 -> pz
                    1 -> mask - px - (sizeX - 1)
                    2 -> mask - pz - (sizeZ - 1)
                    else -> px
                }
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

        /** Largest side in map squares (TFA spans 3 x 5 squares: raids/tfa/client-contract.md section 5). */
        private const val MAX_SQUARES = 8
        private const val ZONES_PER_SQUARE = 8
        private const val SQUARE_MASK = 63
        private const val ROTATIONS = 4
        private const val SQUARE_SHIFT = 6
        private const val SQUARE_AXIS_BITS = 8
        private const val ZONE_SHIFT = 3
        private const val ZONE_SIZE = 8
        private const val ZONE_MASK = 7
        private const val MAX_ROTATION = 3
        const val LEVELS = 4
        private const val LEVEL_MASK = 3
        private const val VIEW_ZONES = 13
        private const val VIEW_RADIUS = 6
        const val NO_ZONE = -1

        /** Player saved key (packed coord): where a player found in a lost instance goes (IN8). */
        const val EXIT_KEY = "instance.exit"
        private const val REF_Z_MASK = 0x7FF
        private const val REF_X_MASK = 0x3FF
        private const val REF_Z_SHIFT = 3
        private const val REF_X_SHIFT = 14
        private const val REF_LEVEL_SHIFT = 24

        private fun squareKey(x: Int, z: Int): Int = (x shl SQUARE_AXIS_BITS) or z
    }
}
