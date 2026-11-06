package tp.hub

import com.sun.net.httpserver.HttpExchange
import kotlinx.coroutines.runBlocking
import java.net.URLDecoder

/** The hub's public HTTP routes (served next to jav_config / worldlist, by the hub or a world node embedding it). */
object HubHttp {
    fun routes(hub: Hub): Map<String, (HttpExchange) -> Unit> =
        mapOf(
            "/register" to { ex -> register(hub, ex) },
            "/health" to { ex -> health(hub, ex) },
            "/hiscores" to { ex -> hiscores(hub, ex) },
        )

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
}
