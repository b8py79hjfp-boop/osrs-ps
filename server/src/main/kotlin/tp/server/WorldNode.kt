package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import net.rsprot.crypto.rsa.RsaKeyPair
import tp.cache.config.Configs
import tp.cache.interfaces.InterfaceTypes
import tp.cache.js5.CacheSpotChecks
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.engine.World
import tp.engine.WorldConfig
import tp.engine.WorldData
import tp.engine.map.StaticLocs
import tp.protocol.osrs242.Osrs242Network
import tp.util.CoordGrid
import tp.util.Revision
import java.nio.file.Path
import java.util.Locale

private val logger = KotlinLogging.logger {}

/** Immutable data shared by every world in this JVM (brief §7 topology). */
class BootData(val cache: Js5Cache, val configs: Configs, val world: WorldData)

/** Composition root pieces for a world-node; used by `main` and by test 2. */
object WorldNode {
    /** Lumbridge castle courtyard: the classic new-player spawn tile. */
    val DEFAULT_SPAWN: CoordGrid = CoordGrid(3222, 3218, 0)

    /** Opens, verifies and decodes the cache, then builds collision. Null if spot-checks fail. Caller owns [BootData.cache]. */
    fun loadData(cacheDir: Path): BootData? {
        val cache = Js5Cache.open(cacheDir)
        val opened = System.nanoTime()
        logger.info {
            String.format(
                Locale.ROOT,
                "rev %d: %d archives, %,d groups (%d populated archives)",
                Revision.NUMBER,
                cache.archiveCount,
                cache.groupCount,
                cache.populatedArchiveCount,
            )
        }
        if (!CacheSpotChecks.verify(cache) { logger.error { it } }) {
            cache.close()
            return null
        }
        val configs = Configs.load(cache)
        logger.info { "configs decoded in ${millisSince(opened)} ms: " + configs.summary().joinToString { (n, c) -> "$n=$c" } }
        val interfacesStart = System.nanoTime()
        val interfaces = InterfaceTypes.load(cache)
        logger.info {
            "interfaces decoded in ${millisSince(interfacesStart)} ms: " +
                "interfaces=${interfaces.interfaceCount}, components=${interfaces.componentCount}"
        }
        val mapStart = System.nanoTime()
        val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
        val mapMillis = millisSince(mapStart)
        val collisionStart = System.nanoTime()
        val collision = CollisionBuilder.build(map, configs.locs)
        logger.info {
            "map: ${map.squares.size} squares, ${map.locCount} locs in $mapMillis ms; " +
                "collision built in ${millisSince(collisionStart)} ms; " +
                "skipped groups ${map.skippedGroups}, terrain extensions ${map.extendedSquares} (collision.md OPEN-1/2)"
        }
        logger.info { "boot data ready in ${millisSince(opened)} ms, heap used ${usedHeapMib()} MiB" }
        return BootData(cache, configs, WorldData(configs, collision, StaticLocs.build(map, configs.locs)))
    }

    /** Creates and starts one world with its rev-242 network on [gamePort]. */
    fun startWorld(data: BootData, rsa: RsaKeyPair, gamePort: Int, worldId: Int, seed: Long): World {
        val network = Osrs242Network(data.cache, rsa, gamePort)
        val world = World(WorldConfig(id = worldId, spawn = DEFAULT_SPAWN, seed = seed), data.world, network, DevContent)
        world.start()
        return world
    }

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val BYTES_PER_MIB = 1L shl 20

    /** Heap in use right now (not a GC'd figure; PROGRESS.md measurements are taken after a GC). */
    private fun usedHeapMib(): Long = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / BYTES_PER_MIB }

    private fun millisSince(start: Long): Long = (System.nanoTime() - start) / NANOS_PER_MILLI
}
