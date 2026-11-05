package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.cache.js5.Js5Cache
import tp.util.Revision
import java.nio.file.Path
import java.util.Locale
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * World-node entry point. Phase 0: open and verify the cache, log the summary.
 *
 * Args: `--cache-dir <path>` (default `.data/cache/<rev>/disk`), `--boot-check` (exit after verification; used by CI).
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    val cacheDir =
        options["--cache-dir"]?.let(Path::of)
            ?: Path.of(".data", "cache", Revision.NUMBER.toString(), "disk")

    val started = System.nanoTime()
    val ok =
        Js5Cache.open(cacheDir).use { cache ->
            logger.info {
                String.format(
                    Locale.ROOT,
                    "rev %d: %d archives, %,d groups (%d populated archives, %d ms)",
                    Revision.NUMBER,
                    cache.archiveCount,
                    cache.groupCount,
                    cache.populatedArchiveCount,
                    (System.nanoTime() - started) / NANOS_PER_MILLI,
                )
            }
            CacheSpotChecks.verify(cache) { logger.error { it } }
        }
    if (!ok) {
        logger.error { "Cache verification failed; refusing to start." }
        exitProcess(1)
    }
    if ("--boot-check" in options) {
        logger.info { "Boot check passed." }
        return
    }
    logger.info { "Phase 0: no world loop yet; exiting." }
}

private const val NANOS_PER_MILLI = 1_000_000L

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
