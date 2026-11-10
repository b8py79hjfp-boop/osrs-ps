package tp.hub

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.net.http.ClientConfigServer
import tp.net.http.WorldEntry
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Hub process (brief §7). Options: `--grpc-port` (43600), `--grpc-bind` (127.0.0.1; the private address when world
 * nodes run on other hosts), `--http-port` (8080), `--db jdbc:postgresql://…`
 * `--db-user` `--db-password` (default: in-memory store), `--auto-register` (dev), `--initial-world` (1).
 * HTTP: `/jav_config.ws`, `/worldlist.ws` (from the world directory), `POST /register` (sign-up, brief §12),
 * `/health`. `--bootstrap developer=Name,owner=Other` gives first ranks (commands brief §1; the standalone twin of
 * `[ranks.bootstrap]`, once per account, audited). `--db-password-file` reads the password from a file instead of the
 * command line. Control panel API (mTLS, loopback): `--panel-cert` `--panel-key` `--panel-ca` [`--panel-port` 50051].
 * `--print-schema [file]` writes the PostgreSQL schema statements, one per line, and exits (the release build ships them).
 */
fun main(args: Array<String>) {
    if ("--print-schema" in args) {
        // To a file when one is given: stdout also carries the logging library's start-up line.
        val lines = PostgresStore.SCHEMA.joinToString("\n", postfix = "\n") { it.replace(Regex("\\s+"), " ").trim() }
        val out = args.getOrNull(args.indexOf("--print-schema") + 1)?.takeUnless { it.startsWith("--") }
        if (out == null) {
            print(lines)
        } else {
            java.nio.file.Files
                .writeString(
                    java.nio.file.Path
                        .of(out),
                    lines,
                )
        }
        return
    }
    val options =
        args.toList().windowed(2, 1, partialWindows = true).filter { it[0].startsWith("--") }.associate {
            it[0] to
                it.getOrNull(1)?.takeUnless { v -> v.startsWith("--") }
        }
    val dbPassword =
        options["--db-password-file"]?.let {
            java.nio.file.Files
                .readString(
                    java.nio.file.Path
                        .of(it),
                ).trim()
        } ?: options["--db-password"].orEmpty()
    val store: HubStore =
        options["--db"]?.let { PostgresStore(it, options["--db-user"] ?: "postgres", dbPassword) } ?: MemoryStore()
    val admins = options["--admins"]?.split(',')?.map { it.trim().replace('_', ' ') }?.toSet() ?: emptySet()
    val bootstrap = bootstrapRanks(options["--bootstrap"])
    val hub = Hub(store, HubConfig(autoRegister = "--auto-register" in options, admins = admins, bootstrap = bootstrap))
    val grpcPort = options["--grpc-port"]?.toIntOrNull() ?: DEFAULT_GRPC_PORT
    val grpc = HubGrpcServer(hub, grpcPort, options["--grpc-bind"] ?: "127.0.0.1").start()
    val panel = PanelGrpcServer.fromOptions(hub, options)
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
            panel?.close()
            grpc.close()
            hub.close()
        },
    )
    Thread.currentThread().join()
}

private const val DEFAULT_GRPC_PORT = 43600
private const val DEFAULT_HTTP_PORT = 8080

/**
 * `--bootstrap developer=Name,owner=Other`: rank=name pairs, comma-separated (names hold no `,` or `=`). Keys are
 * normalised like `[ranks.bootstrap]` (lower case, `_` as space); a trailing `*` is a name prefix.
 */
internal fun bootstrapRanks(spec: String?): Map<String, tp.util.Rank> =
    spec
        .orEmpty()
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val rank = tp.util.Rank.parse(pair.substringBefore('=').trim()) ?: error("--bootstrap: unknown rank in '$pair'")
            val name =
                pair
                    .substringAfter('=', "")
                    .trim()
                    .replace('_', ' ')
                    .lowercase()
            require(name.isNotEmpty()) { "--bootstrap: no name in '$pair'" }
            name to rank
        }
