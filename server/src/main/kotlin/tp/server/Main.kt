package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.net.http.ClientConfigServer
import tp.net.http.WorldEntry
import tp.util.Revision
import java.nio.file.Path
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

private const val DEFAULT_GAME_PORT = 43594
private const val DEFAULT_HTTP_PORT = 8080

/**
 * World-node entry point.
 *
 * Args: `--cache-dir <path>` (default `.data/cache/<rev>/disk`), `--boot-check` (verify data and exit; CI),
 * `--port` (game + JS5, default 43594), `--http-port` (jav_config/worldlist, default 8080),
 * `--public-host` (address advertised to clients, default 127.0.0.1), `--world` (id, default 1).
 */
fun main(args: Array<String>) {
    val options = parseArgs(args)
    val cacheDir = options["--cache-dir"]?.let(Path::of) ?: Path.of(".data", "cache", Revision.NUMBER.toString(), "disk")
    val data = WorldNode.loadData(cacheDir)
    if (data == null) {
        logger.error { "Cache verification failed; refusing to start." }
        exitProcess(1)
    }
    if ("--boot-check" in options) {
        data.cache.close()
        logger.info { "Boot check passed." }
        return
    }

    val gamePort = options["--port"]?.toIntOrNull() ?: DEFAULT_GAME_PORT
    val httpPort = options["--http-port"]?.toIntOrNull() ?: DEFAULT_HTTP_PORT
    val host = options["--public-host"] ?: "127.0.0.1"
    val worldId = options["--world"]?.toIntOrNull() ?: 1
    val rsa = RsaKeys.load(Path.of(".data", "rsa", "private.pem"))
    val world = WorldNode.startWorld(data, rsa, gamePort, worldId, seed = System.nanoTime())
    val http =
        ClientConfigServer(
            port = httpPort,
            publicBaseUrl = "http://$host:$httpPort",
            javConfigTemplate = Path.of(".data", "client", "jav_config.ws"),
            worlds = {
                listOf(
                    WorldEntry(worldId, host, "tickperfect dev", ClientConfigServer.FLAG_MEMBERS, population = world.playerCount),
                )
            },
            initialWorld = worldId,
        )
    http.start()
    Runtime.getRuntime().addShutdownHook(
        Thread {
            world.stop()
            http.close()
            data.cache.close()
        },
    )
    Thread.currentThread().join()
}

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
