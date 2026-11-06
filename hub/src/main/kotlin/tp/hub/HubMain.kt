package tp.hub

import com.sun.net.httpserver.HttpExchange
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.net.http.ClientConfigServer
import tp.net.http.WorldEntry
import java.net.URLDecoder
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
            extraRoutes = mapOf("/register" to { ex -> register(hub, ex) }, "/health" to { ex -> health(hub, ex) }),
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

/** `POST /register` with form fields `username`, `password` (the release client's "New user" link: brief §4.2, §12). */
private fun register(hub: Hub, ex: HttpExchange) {
    if (ex.requestMethod != "POST") return reply(ex, HTTP_METHOD_NOT_ALLOWED, "POST username=…&password=…")
    val form =
        ex.requestBody
            .readNBytes(MAX_FORM)
            .toString(Charsets.UTF_8)
            .split('&')
            .mapNotNull {
                val k = it.substringBefore('=', "")
                if (k.isEmpty()) null else k to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
            }.toMap()
    val ip = ex.remoteAddress.address.hostAddress
    val error = runBlocking { hub.accounts.register(form["username"].orEmpty(), form["password"].orEmpty(), ip) }
    if (error == null) reply(ex, HTTP_OK, "Account created.") else reply(ex, HTTP_BAD_REQUEST, error)
}

private fun health(hub: Hub, ex: HttpExchange) = reply(ex, HTTP_OK, "ok online=${hub.onlineCount}")

private fun reply(ex: HttpExchange, code: Int, text: String) {
    val body = text.toByteArray()
    ex.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    ex.sendResponseHeaders(code, body.size.toLong())
    ex.responseBody.use { it.write(body) }
}

private const val DEFAULT_GRPC_PORT = 43600
private const val DEFAULT_HTTP_PORT = 8080
private const val MAX_FORM = 4096
private const val HTTP_OK = 200
private const val HTTP_BAD_REQUEST = 400
private const val HTTP_METHOD_NOT_ALLOWED = 405
