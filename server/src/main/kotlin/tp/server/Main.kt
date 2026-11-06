package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.hub.Hub
import tp.hub.HubConfig
import tp.hub.MemoryStore
import tp.hub.PostgresStore
import tp.net.http.ClientConfigServer
import tp.net.http.WorldEntry
import tp.services.GrpcHubClient
import tp.services.HubServices
import tp.util.Revision
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

private const val DEFAULT_GAME_PORT = 43594
private const val DEFAULT_HTTP_PORT = 8080

/**
 * World-node entry point.
 *
 * Args: `--cache-dir <path>` (default `.data/cache/<rev>/disk`), `--boot-check` (verify data and exit; CI),
 * `--port` (first world's game + JS5 port, default 43594), `--worlds <n>` (worlds in this JVM, default 1; world i
 * listens on port + i), `--world` (first world id, default 1), `--http-port` (jav_config/worldlist with an embedded
 * hub, default 8080), `--public-host` (address advertised to clients, default 127.0.0.1),
 * `--hub host:port` (remote hub over gRPC; default: an embedded hub), `--db <jdbc url>` `--db-user` `--db-password`
 * (embedded hub's PostgreSQL; default in-memory), `--trace <file>` (tick inspector JSONL, first world),
 * `--record <file>` (replay log, first world), `--max-per-ip <n>` (default 10; raise for local bot swarms).
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
    val firstWorld = options["--world"]?.toIntOrNull() ?: 1
    val worldCount = options["--worlds"]?.toIntOrNull() ?: 1
    val rsa = RsaKeys.load(Path.of(".data", "rsa", "private.pem"))
    val traceOut = options["--trace"]?.let { Files.newBufferedWriter(Path.of(it)) }
    val recordOut = options["--record"]?.let { Files.newBufferedWriter(Path.of(it)) }
    val maxPerIp = options["--max-per-ip"]?.toIntOrNull() ?: WorldNode.DEFAULT_MAX_PER_IP

    val remoteHub = options["--hub"]?.let { GrpcHubClient(it.substringBefore(':'), it.substringAfter(':').toInt()) }
    val embeddedHub = if (remoteHub != null) null else embeddedHub(options)
    val hub: HubServices = remoteHub ?: embeddedHub ?: error("no hub")
    val worlds =
        (0 until worldCount).map { i ->
            WorldNode.startWorld(
                data,
                rsa,
                gamePort + i,
                firstWorld + i,
                seed = System.nanoTime(),
                traceOut.takeIf { i == 0 },
                recordOut.takeIf { i == 0 },
                maxPerIp,
                hub,
            )
        }
    val links = worlds.mapIndexed { i, w -> WorldLinks(w, hub, host, gamePort + i).start() }
    val http = embeddedHub?.let { worldListServer(worlds, host, httpPort, firstWorld) }
    Runtime.getRuntime().addShutdownHook(
        Thread {
            // Graceful shutdown (brief §12): every player is saved (final) before the process stops.
            worlds.forEach { it.execute { it.saveAll(final = true) } }
            Thread.sleep(SHUTDOWN_SAVE_GRACE_MS)
            links.forEach { it.close() }
            worlds.forEach { it.stop() }
            traceOut?.close()
            recordOut?.close()
            http?.close()
            remoteHub?.close()
            embeddedHub?.close()
            data.cache.close()
        },
    )
    Thread.currentThread().join()
}

private fun embeddedHub(options: Map<String, String?>): Hub {
    val store =
        options["--db"]?.let { PostgresStore(it, options["--db-user"] ?: "postgres", options["--db-password"] ?: "") }
            ?: MemoryStore()
    // Dev: the first login with a new name creates the account with that password.
    return Hub(store, HubConfig(autoRegister = true))
}

private fun worldListServer(worlds: List<tp.engine.World>, host: String, httpPort: Int, firstWorld: Int): ClientConfigServer =
    ClientConfigServer(
        port = httpPort,
        publicBaseUrl = "http://$host:$httpPort",
        javConfigTemplate = Path.of(".data", "client", "jav_config.ws"),
        worlds = {
            worlds.map {
                WorldEntry(it.config.id, host, "tickperfect dev", ClientConfigServer.FLAG_MEMBERS, population = it.playerCount)
            }
        },
        initialWorld = firstWorld,
    ).also { it.start() }

/** Time for the final-save tick to run and the hub writes to land before stopping. */
private const val SHUTDOWN_SAVE_GRACE_MS = 2_000L

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
