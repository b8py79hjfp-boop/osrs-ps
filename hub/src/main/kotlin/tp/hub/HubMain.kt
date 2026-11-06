package tp.hub

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.net.http.ClientConfigServer
import tp.net.http.WorldEntry
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Hub process (brief §7). Options: `--grpc-port` (43600), `--http-port` (8080), `--db jdbc:postgresql://…`
 * `--db-user` `--db-password` (default: in-memory store), `--auto-register` (dev), `--initial-world` (1).
 * HTTP: `/jav_config.ws`, `/worldlist.ws` (from the world directory), `POST /register` (sign-up, brief §12),
 * `/health`.
 */
fun main(args: Array<String>) {
    val options =
        args.toList().windowed(2, 1, partialWindows = true).filter { it[0].startsWith("--") }.associate {
            it[0] to
                it.getOrNull(1)?.takeUnless { v -> v.startsWith("--") }
        }
    val store: HubStore =
        options["--db"]?.let { PostgresStore(it, options["--db-user"] ?: "postgres", options["--db-password"] ?: "") } ?: MemoryStore()
    val admins = options["--admins"]?.split(',')?.map { it.trim().replace('_', ' ') }?.toSet() ?: emptySet()
    val hub = Hub(store, HubConfig(autoRegister = "--auto-register" in options, admins = admins))
    val grpc = HubGrpcServer(hub, options["--grpc-port"]?.toIntOrNull() ?: DEFAULT_GRPC_PORT).start()
    val httpPort = options["--http-port"]?.toIntOrNull() ?: DEFAULT_HTTP_PORT
    val http =
        ClientConfigServer(
            port = httpPort,
            publicBaseUrl = options["--public-url"] ?: "http://127.0.0.1:$httpPort",
            javConfigTemplate = Path.of(".data", "client", "jav_config.ws"),
            worlds = {
                runBlocking { hub.worlds.list() }.map {
                    WorldEntry(
                        it.id,
                        it.host,
                        it.activity,
                        it.flags,
                        population = it.population,
                    )
                }
            },
            initialWorld = options["--initial-world"]?.toIntOrNull() ?: 1,
            extraRoutes = HubHttp.routes(hub),
        )
    http.start()
    logger.info { "hub: gRPC :${grpc.port}, HTTP :$httpPort, store ${store.javaClass.simpleName}" }
    Runtime.getRuntime().addShutdownHook(
        Thread {
            http.close()
            grpc.close()
            hub.close()
        },
    )
    Thread.currentThread().join()
}

private const val DEFAULT_GRPC_PORT = 43600
private const val DEFAULT_HTTP_PORT = 8080
