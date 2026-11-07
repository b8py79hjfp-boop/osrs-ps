package tp.cache.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import tp.cache.config.Js5Archive
import tp.cache.config.TypeList
import tp.cache.config.loc.LocType
import tp.cache.js5.Js5Cache
import tp.cache.js5.XteaKey
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Every map square in the cache, decoded. Immutable; shared by all worlds in the JVM.
 * [skippedGroups] are archive-5 groups that don't have the map-square file layout (D-013, OPEN).
 */
class GameMap(val squares: List<MapSquare>, val skippedGroups: List<Int>) {
    val locCount: Int get() = squares.sumOf { it.locCount }
    val extendedSquares: List<MapSquareKey> get() = squares.filter { it.terrainExtensionBytes > 0 }.map { it.key }

    companion object {
        /**
         * Decodes every map square on [threads] threads. Two archive-5 layouts exist:
         * - **unnamed** (live OSRS, incl. cache 2757): group id = square id, file 0 = terrain, file 1 = locs, no XTEA;
         *   files 2..4 are not used yet (DECISIONS D-013).
         * - **named** (older caches, RS Mod): groups `m{x}_{z}` / `l{x}_{z}`, locs XTEA-encrypted.
         * Source: RuneLite 1.13.6 `cache/.../region/RegionLoader.java` (`loadMapDef` / `loadLocDef`).
         */
        fun load(cache: Js5Cache, keys: MapKeys, threads: Int = Runtime.getRuntime().availableProcessors()): GameMap {
            val index = cache.index(Js5Archive.MAPS)
            val jobs = ArrayList<Callable<MapSquare>>()
            val skipped = ArrayList<Int>()
            if (index.groupNameHashes == null) {
                for (slot in 0 until index.groupCount) {
                    val group = index.groupIds[slot]
                    if (!index.fileIds[slot].contentEquals(SQUARE_FILE_IDS)) {
                        skipped += group
                        continue
                    }
                    jobs += Callable { decodeUnnamed(cache, MapSquareKey(group)) }
                }
            } else {
                for (x in 0 until MapSquareKey.AXIS_COUNT) {
                    for (z in 0 until MapSquareKey.AXIS_COUNT) {
                        val terrainGroup = index.groupIdByName("m${x}_$z")
                        if (terrainGroup < 0) continue
                        val locGroup = index.groupIdByName("l${x}_$z")
                        val key = MapSquareKey.of(x, z)
                        jobs += Callable { decodeNamed(cache, key, terrainGroup, locGroup, keys[key]) }
                    }
                }
            }
            val pool = Executors.newFixedThreadPool(threads)
            try {
                return GameMap(pool.invokeAll(jobs).map { it.get() }, skipped)
            } finally {
                pool.shutdown()
            }
        }

        private const val FILE_TERRAIN = 0
        private const val FILE_LOCS = 1

        /** File ids of a map-square group in the unnamed layout: terrain, locs, and three files not yet used. */
        private val SQUARE_FILE_IDS = intArrayOf(0, 1, 2, 3, 4)

        private fun decodeUnnamed(cache: Js5Cache, key: MapSquareKey): MapSquare {
            val files = cache.readFiles(Js5Archive.MAPS, key.id) ?: throw IOException("$key: group listed but missing")

            fun file(id: Int): ByteArray? =
                files.ids
                    .indexOf(id)
                    .takeIf { it >= 0 }
                    ?.let { files.data[it] }
            val terrain = file(FILE_TERRAIN) ?: throw IOException("$key: no terrain file")
            return decode(key, terrain, file(FILE_LOCS))
        }

        private fun decodeNamed(cache: Js5Cache, key: MapSquareKey, terrainGroup: Int, locGroup: Int, xtea: XteaKey): MapSquare {
            val terrain =
                cache.readGroup(Js5Archive.MAPS, terrainGroup)
                    ?: throw IOException("$key: terrain group $terrainGroup listed but missing")
            val locs =
                if (locGroup < 0) {
                    null
                } else {
                    cache.readGroup(Js5Archive.MAPS, locGroup, xtea)
                        ?: throw IOException("$key: loc group $locGroup listed but missing")
                }
            return decode(key, terrain, locs)
        }

        /** Level-0 sea tiles as 64 rows of 64 bits, or null when the square has no sea (saves memory inland). */
        private fun seaMask(overlays: ShortArray?): LongArray? {
            if (overlays == null) return null
            val mask = LongArray(MapSquare.SIZE)
            var any = false
            for (x in 0 until MapSquare.SIZE) {
                for (z in 0 until MapSquare.SIZE) {
                    if (!SeaOverlays.isSea(overlays[x * MapSquare.SIZE + z].toInt())) continue
                    mask[x] = mask[x] or (1L shl z)
                    any = true
                }
            }
            return if (any) mask else null
        }

        private fun decode(key: MapSquareKey, terrain: ByteArray, locData: ByteArray?): MapSquare {
            val decoded =
                try {
                    MapTerrainDecoder.decode(terrain, keepOverlays0 = true)
                } catch (e: IOException) {
                    throw IOException("$key terrain: ${e.message}", e)
                }
            val (ids, infos) =
                if (locData == null) {
                    IntArray(0) to IntArray(0)
                } else {
                    try {
                        MapLocDecoder.decode(locData)
                    } catch (e: IOException) {
                        throw IOException("$key locs: ${e.message}", e)
                    }
                }
            return MapSquare(key, decoded.settings, ids, infos, decoded.extensionBytes, seaMask(decoded.overlays0))
        }
    }
}

object MapKeysLoader {
    /** Parses OpenRS2 `keys.json`: `[{"mapsquare": id, "key": [k0, k1, k2, k3], ...}]`. Missing file = no keys. */
    fun load(path: Path): MapKeys {
        if (!Files.isRegularFile(path)) return MapKeys.NONE
        val root = Json.parseToJsonElement(Files.readString(path)).jsonArray
        val keys = HashMap<Int, XteaKey>(root.size * 2)
        for (entry in root) {
            val obj = entry.jsonObject
            val square = obj["mapsquare"]?.jsonPrimitive?.int ?: continue
            val words = obj["key"]?.jsonArray?.map { it.jsonPrimitive.int }?.toIntArray() ?: continue
            keys[square] = XteaKey.of(words)
        }
        return MapKeys(keys)
    }
}

/**
 * Builds the static base collision for the whole map (docs/mechanics/collision.md). Single-threaded: locs near square
 * edges write into neighbouring squares, so per-square parallelism would race.
 */
object CollisionBuilder {
    fun build(map: GameMap, locTypes: TypeList<LocType>): CollisionFlagMap {
        val flags = CollisionFlagMap()
        for (square in map.squares) allocate(flags, square)
        for (square in map.squares) {
            applyTiles(flags, square)
            applyLocs(flags, square, locTypes)
        }
        return flags
    }

    /** Every zone of a square that exists in the cache starts walkable (0); absent squares stay fully blocked (-1). */
    private fun allocate(flags: CollisionFlagMap, square: MapSquare) {
        for (level in 0 until MapSquare.LEVELS) {
            for (x in 0 until MapSquare.SIZE step ZONE_SIZE) {
                for (z in 0 until MapSquare.SIZE step ZONE_SIZE) {
                    flags.allocateIfAbsent(square.key.baseX + x, square.key.baseZ + z, level)
                }
            }
        }
    }

    /** Client: a tile column is "bridged" when the level-1 tile has LINK_BELOW; every level then drops by one. */
    private fun collisionLevel(square: MapSquare, level: Int, x: Int, z: Int): Int {
        val bridged = square.settings(BRIDGE_LEVEL, x, z) and TileSettings.LINK_BELOW != 0
        return if (bridged) level - 1 else level
    }

    private fun applyTiles(flags: CollisionFlagMap, square: MapSquare) {
        for (level in 0 until MapSquare.LEVELS) {
            for (x in 0 until MapSquare.SIZE) {
                for (z in 0 until MapSquare.SIZE) {
                    val settings = square.settings(level, x, z)
                    if (settings and (TileSettings.BLOCK_MAP_SQUARE or TileSettings.REMOVE_ROOFS) == 0) continue
                    val target = collisionLevel(square, level, x, z)
                    if (target < 0) continue
                    val absX = square.key.baseX + x
                    val absZ = square.key.baseZ + z
                    if (settings and TileSettings.BLOCK_MAP_SQUARE != 0) flags.add(absX, absZ, target, CollisionFlag.BLOCK_WALK)
                    if (settings and TileSettings.REMOVE_ROOFS != 0) flags.add(absX, absZ, target, CollisionFlag.ROOF)
                }
            }
        }
    }

    private fun applyLocs(flags: CollisionFlagMap, square: MapSquare, locTypes: TypeList<LocType>) {
        for (i in 0 until square.locCount) {
            val info = square.locInfo[i]
            val x = MapSquare.locX(info)
            val z = MapSquare.locZ(info)
            val target = collisionLevel(square, MapSquare.locLevel(info), x, z)
            if (target < 0) continue
            val type = locTypes[square.locIds[i]] ?: throw IOException("${square.key}: unknown loc ${square.locIds[i]}")
            LocCollision.toggle(
                flags = flags,
                x = square.key.baseX + x,
                z = square.key.baseZ + z,
                level = target,
                shape = MapSquare.locShape(info),
                angle = MapSquare.locAngle(info),
                width = type.width,
                length = type.length,
                blockWalk = type.blockWalk,
                blockRange = type.blockRange,
                breakRouteFinding = type.breakRouteFinding,
                add = true,
            )
        }
    }

    private const val ZONE_SIZE = 8

    /** The client reads the bridge flag from level 1 for every level (see collision.md). */
    private const val BRIDGE_LEVEL = 1
}
