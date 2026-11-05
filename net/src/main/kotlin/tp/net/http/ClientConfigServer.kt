package tp.net.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/** One world as advertised in `/worldlist.ws`. */
data class WorldEntry(
    val id: Int,
    val host: String,
    val activity: String,
    val flags: Int,
    val location: Int = 0,
    val population: Int = 0,
)

/**
 * Serves `/jav_config.ws` and `/worldlist.ws` for clients (brief §4.1).
 *
 * `jav_config.ws` is Jagex's live file (downloaded by `./gradlew setup` into `.data/client/`, never committed — D-017)
 * with the codebase, world-list URL, initial world and world flags rewritten, as RSProx does (`JavConfig.replace*`).
 * `worldlist.ws` uses the client's binary format: i32 body length, u16 count, then per world u16 id, i32 flags,
 * jstr host, jstr activity, u8 location, u16 population (RSProx `WorldList.encode`).
 */
class ClientConfigServer(
    private val port: Int,
    private val publicBaseUrl: String,
    private val javConfigTemplate: Path,
    private val worlds: () -> List<WorldEntry>,
    private val initialWorld: Int,
) : AutoCloseable {
    private var server: HttpServer? = null

    fun start() {
        val template = Files.readString(javConfigTemplate, Charsets.ISO_8859_1)
        val http = HttpServer.create(InetSocketAddress(port), 0)
        http.executor = Executors.newVirtualThreadPerTaskExecutor()
        http.createContext("/jav_config.ws") { ex -> respond(ex, "text/plain", javConfig(template).toByteArray(Charsets.ISO_8859_1)) }
        http.createContext("/worldlist.ws") { ex -> respond(ex, "application/octet-stream", worldList(worlds())) }
        http.start()
        server = http
        logger.info { "client config HTTP on :$port (jav_config.ws, worldlist.ws)" }
    }

    override fun close() {
        server?.stop(0)
    }

    internal fun javConfig(template: String): String {
        val world = worlds().firstOrNull { it.id == initialWorld }
        return template.lineSequence().joinToString("\n") { line ->
            when {
                line.startsWith("codebase=") -> {
                    "codebase=$publicBaseUrl/"
                }

                line.startsWith("param=$PARAM_WORLD_LIST=") -> {
                    "param=$PARAM_WORLD_LIST=$publicBaseUrl/worldlist.ws"
                }

                line.startsWith("param=$PARAM_INITIAL_WORLD=") -> {
                    "param=$PARAM_INITIAL_WORLD=$initialWorld"
                }

                world != null && line.startsWith("param=$PARAM_MEMBERS=") -> {
                    "param=$PARAM_MEMBERS=${world.flags and FLAG_MEMBERS != 0}"
                }

                world != null && line.startsWith("param=$PARAM_WORLD_PROPERTIES=") -> {
                    "param=$PARAM_WORLD_PROPERTIES=${world.flags}"
                }

                else -> {
                    line
                }
            }
        }
    }

    internal fun worldList(list: List<WorldEntry>): ByteArray {
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            out.writeShort(list.size)
            for (w in list) {
                out.writeShort(w.id)
                out.writeInt(w.flags)
                out.writeJstr(w.host)
                out.writeJstr(w.activity)
                out.writeByte(w.location)
                out.writeShort(w.population)
            }
        }
        val bytes = body.toByteArray()
        val framed = ByteArrayOutputStream(bytes.size + Int.SIZE_BYTES)
        DataOutputStream(framed).use {
            it.writeInt(bytes.size)
            it.write(bytes)
        }
        return framed.toByteArray()
    }

    private fun DataOutputStream.writeJstr(s: String) {
        write(s.toByteArray(Charsets.ISO_8859_1))
        writeByte(0)
    }

    private fun respond(ex: HttpExchange, type: String, body: ByteArray) {
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(HTTP_OK, body.size.toLong())
        ex.responseBody.use { it.write(body) }
    }

    companion object {
        /** jav_config param ids (RSProx `JavConfig` companion). */
        const val PARAM_MEMBERS = 3
        const val PARAM_WORLD_PROPERTIES = 5
        const val PARAM_INITIAL_WORLD = 12
        const val PARAM_WORLD_LIST = 17
        const val PARAM_REVISION = 25

        /** World flag bit for members worlds (RSProx `WorldFlag.MEMBERS`). */
        const val FLAG_MEMBERS = 0x1
        private const val HTTP_OK = 200
    }
}
