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
        internal var zones = HashMap<Int, Int>()

        /** Base locs by zone, from the build plan (IN6); zones copied later one by one keep theirs in DynamicLocs. */
        internal var bases: HashMap<Int, ZoneBases> = HashMap()

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
                squares.firstOrNull { it.dstDx == dx && it.dstDz == dz && coord.level - it.dstLevel in 0 until it.levels }
                    ?: return zoneTemplate(coord)
            val inverse = (ROTATIONS - copy.rotation) and MAX_ROTATION
            val lx = coord.x and SQUARE_MASK
            val lz = coord.z and SQUARE_MASK
            return CoordGrid(
                (copy.srcSquareX shl SQUARE_SHIFT) + rotateIn(lx, lz, 1, 1, inverse, SQUARE_MASK, true),
                (copy.srcSquareZ shl SQUARE_SHIFT) + rotateIn(lx, lz, 1, 1, inverse, SQUARE_MASK, false),
                coord.level - copy.dstLevel + copy.srcLevel,
            )
        }

        /** [toTemplate] for a tile of a single copied zone: its reference names the template zone and turn (IN3). */
        private fun zoneTemplate(coord: CoordGrid): CoordGrid? {
            val ref = zones[ZoneKey.of(coord.level, coord.x shr ZONE_SHIFT, coord.z shr ZONE_SHIFT).packed] ?: return null
            val rotation = (ref shr 1) and MAX_ROTATION
            val inverse = (ROTATIONS - rotation) and MAX_ROTATION
            val lx = coord.x and ZONE_MASK
            val lz = coord.z and ZONE_MASK
            return CoordGrid(
                (((ref shr REF_X_SHIFT) and REF_X_MASK) shl ZONE_SHIFT) + rotateIn(lx, lz, 1, 1, inverse, ZONE_MASK, true),
                (((ref shr REF_Z_SHIFT) and REF_Z_MASK) shl ZONE_SHIFT) + rotateIn(lx, lz, 1, 1, inverse, ZONE_MASK, false),
                (ref shr REF_LEVEL_SHIFT) and LEVEL_MASK,
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
        /** Locs relative to a zone at ([PREP_BIAS], [PREP_BIAS]) zones: a turned multi-tile loc can start west / south of its zone. */
        val locs: List<InteractionTarget.Loc>,
    )

    /**
     * A built but not yet attached instance (IN6): its zones, prepared off the world thread. [tiles] and [bases] hold each
     * target zone's final collision (template tiles plus every base loc's, across zone edges) and base locs, so attaching
     * is a swap per zone; [spill] is base-loc collision that falls in a zone outside the plan (x, z, level, mask).
     */
    class BuildPlan internal constructor(
        val instance: Instance,
        internal val zones: List<Pair<ZoneKey, PreparedZone>>,
        internal val squares: List<SquareCopy>,
        internal val tiles: Map<Int, IntArray>,
        internal val bases: HashMap<Int, ZoneBases>,
        internal val spill: IntArray,
        internal val zoneRefs: HashMap<Int, Int>,
    )

    private val prepared = java.util.concurrent.ConcurrentHashMap<Long, PreparedZone>()

    private val bySquare = HashMap<Int, Instance>()

    init {
        // IN6: base-loc lookups in an attached instance read its own table (handed over whole at attach).
        locs.instanceBases = { packed ->
            val zone = ZoneKey(packed)
            bySquare[squareKey(zone.zoneX shr ZONES_PER_SQUARE_SHIFT, zone.zoneZ shr ZONES_PER_SQUARE_SHIFT)]?.bases?.get(packed)
        }
    }

    private var nextId = 1

    /** Instances allocated and not yet destroyed (raids brief section 7 soak: back to the baseline after teardown). */
    @Volatile var live: Int = 0
        private set

    /** Allocates a free block of [widthSquares] × [lengthSquares] map squares in instance space (IN1). */
    fun create(widthSquares: Int, lengthSquares: Int): Instance {
        require(widthSquares in 1..MAX_SQUARES && lengthSquares in 1..MAX_SQUARES) { "instance size $widthSquares×$lengthSquares" }
        for (sx in FIRST_SQUARE_X..LAST_SQUARE - widthSquares) {
            for (sz in 0..LAST_SQUARE - lengthSquares) {
                if (!free(sx, sz, widthSquares, lengthSquares)) continue
                val instance = Instance(nextId++, sx, sz, widthSquares, lengthSquares)
                forSquares(instance) { bySquare[it] = instance }
                live++
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
        val tiles = HashMap<Int, IntArray>(zones.size * 2)
        for ((zone, _) in zones) tiles.getOrPut(zone.packed) { IntArray(ZONE_TILES) }
        val building = HashMap<Int, ZoneBases.Builder>(zones.size * 2)
        val spill = IntArrayList()
        val sink =
            tp.cache.map.LocCollision.FlagSink { x, z, level, mask, add ->
                val zone = tiles[ZoneKey.of(level, x shr ZONE_SHIFT, z shr ZONE_SHIFT).packed]
                if (zone == null) {
                    if (add) spill.add(x, z, level, mask)
                } else {
                    val i = (x and ZONE_MASK) or ((z and ZONE_MASK) shl ZONE_SHIFT)
                    zone[i] = if (add) zone[i] or mask else zone[i] and mask.inv()
                }
            }
        // As applying zone after zone onto cleared targets: tile bits first, then the base locs and their collision.
        for ((zone, prep) in zones) {
            val arr = tiles.getValue(zone.packed)
            for (i in prep.tileOffsets.indices) {
                val b = prep.tileBits[i]
                val t = (prep.tileOffsets[i] shr ZONE_SHIFT) or ((prep.tileOffsets[i] and ZONE_MASK) shl ZONE_SHIFT)
                if (b and StaticLocs.ZoneTemplate.BLOCKED != 0) arr[t] = arr[t] or CollisionFlag.BLOCK_WALK
                if (b and StaticLocs.ZoneTemplate.ROOF != 0) arr[t] = arr[t] or CollisionFlag.ROOF
            }
        }
        for ((zone, prep) in zones) {
            for (loc in placedLocs(zone, prep)) {
                val at = ZoneKey.of(loc.coord.level, loc.coord.x shr ZONE_SHIFT, loc.coord.z shr ZONE_SHIFT).packed
                // A turned multi-tile loc can start in a zone outside the plan (west / south of the instance): its
                // collision still lands in the plan's zones, but it is not registered there (that zone belongs to the
                // map or a neighbouring instance, and nothing could tear it down with this one).
                if (tiles.containsKey(at) && !building.getOrPut(at) { ZoneBases.Builder() }.put(loc)) continue
                val type = types[loc.id] ?: continue
                tp.cache.map.LocCollision.toggle(
                    sink,
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
                    true,
                )
            }
        }
        val refs = HashMap<Int, Int>(zones.size * 2)
        for ((zone, prep) in zones) refs[zone.packed] = prep.reference
        val bases = HashMap<Int, ZoneBases>(building.size * 2)
        for ((zone, b) in building) bases[zone] = b.build()
        // Zones with no collision at all (empty template space) share one array; a later edit copies it (ownZones).
        for (e in tiles.entries) if (e.value.all { it == 0 }) e.setValue(EMPTY_TILES)
        return BuildPlan(instance, zones, squares, tiles, bases, spill.toArray(), refs)
    }

    /** IN6: applies a prepared plan (world thread, pre-tick): a swap per zone, nothing recomputed (budget 1 ms). */
    fun attach(plan: BuildPlan) {
        val instance = plan.instance
        if (instance.destroyed) return
        if (instance.zones.isEmpty() && instance.bases.isEmpty()) {
            // A fresh instance (the usual case): tile arrays go in, the zone and base tables are handed over whole.
            for ((zone, _) in plan.zones) install(zone, plan.tiles.getValue(zone.packed))
            instance.zones = plan.zoneRefs
            instance.bases = plan.bases
        } else {
            // A re-attach over live zones clears its targets first, then installs zone by zone.
            for ((zone, _) in plan.zones) clear(instance, zone.level, zone.zoneX, zone.zoneZ)
            for ((zone, prep) in plan.zones) {
                install(zone, plan.tiles.getValue(zone.packed))
                plan.bases[zone.packed]?.let { instance.bases[zone.packed] = it }
                instance.zones[zone.packed] = prep.reference
            }
        }
        // Base locs that live in a target zone but cast collision outside the plan (rare: an instance's own edges).
        val spill = plan.spill
        var i = 0
        while (i < spill.size) {
            collision.ownZones(spill[i + 2], spill[i], spill[i + 1], 1, 1)
            collision.sink.change(spill[i], spill[i + 1], spill[i + 2], spill[i + SPILL_MASK], true)
            i += SPILL_STRIDE
        }
        instance.squares += plan.squares
        instance.version++
    }

    /** The shared empty array goes in unowned, so the first edit copies it; a zone with collision is this world's own. */
    private fun install(zone: ZoneKey, tiles: IntArray) {
        collision.installZone(zone.level, zone.zoneX, zone.zoneZ, tiles, owned = tiles !== EMPTY_TILES)
    }

    /** [prep]'s locs placed in [zone] (absolute tiles); a turned loc of a map-edge zone can start off the map: dropped. */
    private fun placedLocs(zone: ZoneKey, prep: PreparedZone): List<InteractionTarget.Loc> {
        val baseX = zone.zoneX shl ZONE_SHIFT
        val baseZ = zone.zoneZ shl ZONE_SHIFT
        val locBase = PREP_BIAS shl ZONE_SHIFT
        val out = ArrayList<InteractionTarget.Loc>(prep.locs.size)
        for (loc in prep.locs) {
            val x = baseX + loc.coord.x - locBase
            val z = baseZ + loc.coord.z - locBase
            if (x < 0 || z < 0) continue
            out +=
                InteractionTarget.Loc(CoordGrid(x, z, zone.level), loc.id, loc.shape, loc.angle, loc.width, loc.length, loc.forceApproach)
        }
        return out
    }

    /** A growable int list (spill quadruples) without boxing. */
    private class IntArrayList {
        private var a = IntArray(SPILL_STRIDE * 4)
        private var n = 0

        fun add(x: Int, z: Int, level: Int, mask: Int) {
            if (n + SPILL_STRIDE > a.size) a = a.copyOf(a.size * 2)
            a[n] = x
            a[n + 1] = z
            a[n + 2] = level
            a[n + SPILL_MASK] = mask
            n += SPILL_STRIDE
        }

        fun toArray(): IntArray = a.copyOf(n)
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
            PreparedZone(ref, offsets.toIntArray(), bits.toIntArray(), template.locs.map { rotated(it, 0, PREP_BIAS, PREP_BIAS, rotation) })
        }
    }

    private fun reset(instance: Instance, zone: ZoneKey) {
        clear(instance, zone.level, zone.zoneX, zone.zoneZ)
        collision.resetZone(zone.level, zone.zoneX, zone.zoneZ)
    }

    private fun apply(instance: Instance, zone: ZoneKey, prep: PreparedZone, cleared: Boolean = false) {
        val level = zone.level
        if (!cleared) reset(instance, zone)
        val baseX = zone.zoneX shl ZONE_SHIFT
        val baseZ = zone.zoneZ shl ZONE_SHIFT
        for (i in prep.tileOffsets.indices) {
            val x = baseX + (prep.tileOffsets[i] shr ZONE_SHIFT)
            val z = baseZ + (prep.tileOffsets[i] and ZONE_MASK)
            val b = prep.tileBits[i]
            if (b and StaticLocs.ZoneTemplate.BLOCKED != 0) collision.flags.add(x, z, level, CollisionFlag.BLOCK_WALK)
            if (b and StaticLocs.ZoneTemplate.ROOF != 0) collision.flags.add(x, z, level, CollisionFlag.ROOF)
        }
        for (placed in placedLocs(zone, prep)) {
            // As in [prepare]: a loc starting outside the instance keeps its collision but is not registered there.
            if (placed.coord in instance) locs.placeBase(placed) else locs.applyCollision(placed)
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
        live--
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
        instance.bases.remove(zone.packed)
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

        /** Prepared zones keep their locs one zone in from the origin, so a turned loc never has a negative tile. */
        private const val PREP_BIAS = 1
        private const val ZONE_SIZE = 8
        private const val ZONE_MASK = 7
        private const val ZONE_TILES = 64

        /** Zones to a square side: 8 = 1 shl 3. */
        private const val ZONES_PER_SQUARE_SHIFT = 3
        private const val SPILL_STRIDE = 4
        private const val SPILL_MASK = 3

        /** Every empty instance zone's tiles until something changes them (never written: ownZones copies first). */
        private val EMPTY_TILES = IntArray(ZONE_TILES)
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
