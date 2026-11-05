package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.cache.config.Configs
import tp.cache.interfaces.InterfaceTypes
import tp.cache.js5.CacheSpotChecks
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.util.Revision
import java.nio.file.Path
import java.util.Locale
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * World-node entry point. Phase 1: open and verify the cache, decode configs, log summaries and timings.
 *
 * Args: `--cache-dir <path>` (default `.data/cache/<rev>/disk`), `--boot-check` (exit after verification; used by CI).
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    val cacheDir =
        options["--cache-dir"]?.let(Path::of)
            ?: Path.of(".data", "cache", Revision.NUMBER.toString(), "disk")

    val ok =
        Js5Cache.open(cacheDir).use { cache ->
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
            if (!CacheSpotChecks.verify(cache) { logger.error { it } }) return@use false

            val configs = Configs.load(cache)
            logger.info {
                "configs decoded in ${millisSince(opened)} ms: " +
                    configs.summary().joinToString { (name, count) -> "$name=$count" }
            }
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
            CollisionBuilder.build(map, configs.locs)
            logger.info {
                "map: ${map.squares.size} squares, ${map.locCount} locs in $mapMillis ms; " +
                    "collision built in ${millisSince(collisionStart)} ms; " +
                    "skipped groups ${map.skippedGroups}, terrain extensions ${map.extendedSquares} (collision.md OPEN-1/2)"
            }
            logger.info { "boot data ready in ${millisSince(opened)} ms, heap used ${usedHeapMib()} MiB" }
            true
        }
    if (!ok) {
        logger.error { "Cache verification failed; refusing to start." }
        exitProcess(1)
    }
    if ("--boot-check" in options) {
        logger.info { "Boot check passed." }
        return
    }
    logger.info { "No world loop yet (Phase 3); exiting." }
}

private const val NANOS_PER_MILLI = 1_000_000L

private const val BYTES_PER_MIB = 1L shl 20

/** Heap in use right now (not a GC'd figure; the cache-smoke measurements in PROGRESS.md are taken after a GC). */
private fun usedHeapMib(): Long = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / BYTES_PER_MIB }

private fun millisSince(start: Long): Long = (System.nanoTime() - start) / NANOS_PER_MILLI

private fun parseArgs(args: Array<String>): Map<String, String?> {
    val out = HashMap<String, String?>()
    var i = 0
    while (i < args.size) {
        val key = args[i]
        val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
        out[key] = value
        i += if (value == null) 1 else 2
    }
    return out
}
