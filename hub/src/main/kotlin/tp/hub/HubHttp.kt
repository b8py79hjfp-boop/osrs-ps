package tp.hub

import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder

/** The hub's public HTTP routes (served next to jav_config / worldlist, by the hub or a world node embedding it). */
object HubHttp {
    fun routes(
        hub: Hub,
        updateDir: java.nio.file.Path =
            java.nio.file.Path
                .of(".data", "update"),
    ): Map<String, (HttpExchange) -> Unit> =
        mapOf(
            "/update" to { ex -> update(updateDir, ex) },
            "/register" to { ex -> register(hub, ex) },
            "/health" to { ex -> health(hub, ex) },
            "/hiscores" to { ex -> hiscores(hub, ex) },
            "/runelite" to { ex -> runelite(hub, ex) },
        )

    /**
     * The release client's RuneLite API base (`runelite.api.base` = `<site>/runelite`; client/runelite/ENDPOINTS.md).
     * - `worlds.js`: the world hopper's list, RuneLite `WorldResult` JSON built from the hub's world directory.
     * - `item/prices.js`: RuneLite `ItemPrice[]` from GE guide prices (grand-exchange.md GE2).
     * - `config.json`: RuneLite's static runtime config; empty, so the client keeps its defaults.
     * Anything else answers 404, so a client feature we haven't served fails closed instead of reaching RuneLite.
     */
    private fun runelite(hub: Hub, ex: HttpExchange) {
        val path = ex.requestURI.path.removePrefix("/runelite")
        when (path) {
            "/worlds.js" -> {
                json(ex, worlds(hub))
            }

            "/item/prices.js" -> {
                json(ex, prices(hub))
            }

            "/config.json" -> {
                json(ex, "{}")
            }

            // Item stats and NPC hitpoints, generated from the cache (world node at boot, or cache-dump runelite-static).
            else -> {
                if (path.startsWith(
                        "/static/",
                    )
                ) {
                    serveFile(staticDir, path.removePrefix("/static"), ex)
                } else {
                    reply(ex, HTTP_NOT_FOUND, "not served")
                }
            }
        }
    }

    /** Where the world node / cache-dump writes RuneLite's static data. */
    private val staticDir: java.nio.file.Path =
        java.nio.file.Path
            .of(".data", "runelite-static")

    /**
     * The launcher's update endpoint: static files from [dir] (manifest.json, manifest.json.sig and the artifacts directory), written
     * by `release publish`. Integrity comes from the signed manifest; this only serves bytes. Paths can't leave [dir].
     */
    private fun update(dir: java.nio.file.Path, ex: HttpExchange) = serveFile(dir, ex.requestURI.path.removePrefix("/update"), ex)

    /** Serves [relative] from [dir]; paths resolving outside [dir] (e.g. `..`) or to missing files answer 404. */
    private fun serveFile(dir: java.nio.file.Path, relative: String, ex: HttpExchange) {
        val root = dir.toAbsolutePath().normalize()
        val file = root.resolve(relative.trimStart('/')).normalize()
        if (!file.startsWith(root) ||
            !java.nio.file.Files
                .isRegularFile(file)
        ) {
            return reply(ex, HTTP_NOT_FOUND, "not found")
        }
        val type = if (file.toString().endsWith(".json")) "application/json" else "application/octet-stream"
        ex.responseHeaders.add("Content-Type", type)
        ex.sendResponseHeaders(
            HTTP_OK,
            java.nio.file.Files
                .size(file),
        )
        ex.responseBody.use { out ->
            java.nio.file.Files
                .copy(file, out)
        }
    }

    private fun worlds(hub: Hub): String {
        val worlds = runBlocking { hub.worlds.list() }
        return worlds.joinToString(",", prefix = "{\"worlds\":[", postfix = "]}") { w ->
            val types = if (w.flags and MEMBERS_FLAG != 0) "[\"MEMBERS\"]" else "[]"
            "{\"id\":${w.id},\"types\":$types,\"address\":${quote(w.host)},\"activity\":${quote(w.activity)}," +
                "\"region\":\"UNITED_STATES_OF_AMERICA\",\"players\":${w.population}}"
        }
    }

    private fun prices(hub: Hub): String {
        val prices = runBlocking { hub.exchange.guidePrices() }
        return prices.entries.joinToString(",", prefix = "[", postfix = "]") { (obj, price) ->
            "{\"id\":$obj,\"name\":\"\",\"price\":$price,\"wikiPrice\":$price}"
        }
    }

    private fun quote(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun json(ex: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(HTTP_OK, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
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

    /** `GET /hiscores?stat=<id|-1 overall>&count=<n>`: plain-text "rank name level xp" lines. */
    private fun hiscores(hub: Hub, ex: HttpExchange) {
        val query =
            ex.requestURI.rawQuery
                .orEmpty()
                .split('&')
                .associate { it.substringBefore('=') to it.substringAfter('=', "") }
        val stat = query["stat"]?.toIntOrNull() ?: OVERALL
        val count = (query["count"]?.toIntOrNull() ?: DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
        val rows = runBlocking { hub.hiscores.top(stat, count) }
        reply(ex, HTTP_OK, rows.withIndex().joinToString("\n", postfix = "\n") { (i, e) -> "${i + 1} ${e.name} ${e.level} ${e.xp}" })
    }

    private const val MAX_FORM = 4096
    private const val HTTP_OK = 200
    private const val HTTP_BAD_REQUEST = 400
    private const val HTTP_METHOD_NOT_ALLOWED = 405
    private const val OVERALL = -1
    private const val DEFAULT_COUNT = 25
    private const val MAX_COUNT = 100
    private const val HTTP_NOT_FOUND = 404

    /** World-list members flag (ClientConfigServer.FLAG_MEMBERS / LogoutTransfer properties 0x1). */
    private const val MEMBERS_FLAG = 1
}
