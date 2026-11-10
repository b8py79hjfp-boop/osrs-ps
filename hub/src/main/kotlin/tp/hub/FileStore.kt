package tp.hub

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import tp.services.ChannelSettings
import tp.services.ClanMemberRow
import tp.services.ClanRecord
import tp.services.ClanRules
import tp.services.GeOffer
import tp.services.TpCollect
import tp.services.TpListing
import tp.services.TpTrade
import tp.util.ModRecord
import tp.util.Rank
import tp.util.RecordType
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

private val log = KotlinLogging.logger {}

/**
 * A [MemoryStore] kept in one JSON file (D-097): the local default when no `--db` is given, so accounts, saves (stats,
 * items, position), ranks, friends, GE offers and the rest survive a restart without a database. Every [FLUSH_MS] the
 * whole store is written, if it changed, to a temporary file that then atomically replaces [path]; [close] (server
 * shutdown, after the final player saves) writes it once more. A crash loses at most the last [FLUSH_MS]. Not for live
 * worlds: one process, whole-file writes, no history; use PostgreSQL there.
 */
class FileStore private constructor(
    private val path: Path,
    private val memory: MemoryStore,
) : HubStore by memory {
    private var written: String? = null
    private val flusher =
        Thread
            .ofPlatform()
            .daemon()
            .name("file-store")
            .unstarted {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        Thread.sleep(FLUSH_MS)
                        flush()
                    } catch (_: InterruptedException) {
                        return@unstarted
                    } catch (e: IOException) {
                        log.error(e) { "Could not write $path" }
                    }
                }
            }

    /** Writes the store if it changed since the last write. */
    @Synchronized
    fun flush() {
        val text = memory.exportState().toString()
        if (text == written) return
        Files.createDirectories(path.toAbsolutePath().parent)
        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(tmp, text)
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        written = text
    }

    override fun close() {
        flusher.interrupt()
        flush()
        memory.close()
    }

    companion object {
        /** Default location: `.data/` is gitignored, next to the cache and keys. */
        val DEFAULT_PATH: Path = Path.of(".data", "accounts", "hub-store.json")

        /** How often a changed store is written (our choice: a crash loses at most this much). */
        const val FLUSH_MS = 5_000L

        /**
         * Opens [path], loading it when it exists. A file that can't be read stops the start instead of being
         * overwritten with an empty store.
         */
        fun open(path: Path = DEFAULT_PATH): FileStore {
            val memory = MemoryStore()
            if (Files.exists(path)) {
                try {
                    memory.importState(Json.parseToJsonElement(Files.readString(path)).jsonObject)
                } catch (e: IllegalArgumentException) {
                    throw IOException("$path is not a readable hub store (${e.message}); move it away to start empty", e)
                }
                log.info { "Loaded accounts and saves from $path" }
            } else {
                log.info { "No $path yet: starting with an empty store" }
            }
            val store = FileStore(path, memory)
            store.written = memory.exportState().toString()
            store.flusher.start()
            return store
        }
    }
}

// ---- JSON helpers for MemoryStore.exportState / importState

internal fun prim(v: Any): JsonPrimitive =
    when (v) {
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

internal fun strings(values: Collection<String>): JsonArray = JsonArray(values.map(::JsonPrimitive))

internal fun <V> longMap(map: Map<Long, V>, f: (V) -> JsonElement): JsonObject =
    JsonObject(
        map.entries.associate { (k, v) ->
            k.toString() to
                f(v)
        },
    )

internal fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.long ?: 0L

internal fun JsonObject.arr(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

internal fun JsonObject.obj(key: String): JsonObject = this[key]?.jsonObject ?: JsonObject(emptyMap())

private fun JsonObject.int(key: String): Int = this[key]?.jsonPrimitive?.int ?: 0

private fun JsonObject.str(key: String): String = this[key]?.jsonPrimitive?.content ?: ""

private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.boolean ?: false

/** The rows of [MemoryStore]'s tables to and from JSON; enums by name, ranks by their stored value. */
internal object StateCodec {
    fun account(r: AccountRow): JsonObject =
        buildJsonObject {
            put("id", r.id)
            put("username", r.username)
            put("displayName", r.displayName)
            put("passwordHash", r.passwordHash)
            put("rights", r.rights.stored)
            put("mutedUntil", r.mutedUntil)
            put("bannedUntil", r.bannedUntil)
        }

    fun account(o: JsonObject): AccountRow =
        AccountRow(
            o.long("id"),
            o.str("username"),
            o.str("displayName"),
            o.str("passwordHash"),
            Rank.parseOrPlayer(o.str("rights")),
            o.long("mutedUntil"),
            o.long("bannedUntil"),
        )

    fun audit(r: AuditRow): JsonObject =
        buildJsonObject {
            put("id", r.id)
            put("at", r.atMs)
            put("actor", r.actor)
            put("action", r.action)
            put("target", r.target)
            put("detail", r.detail)
            put("source", r.source)
            put("ip", r.ip)
            put("result", r.result)
        }

    fun audit(o: JsonObject): AuditRow =
        AuditRow(
            o.long("id"),
            o.long("at"),
            o.str("actor"),
            o.str("action"),
            o.str("target"),
            o.str("detail"),
            o.str("source"),
            o.str("ip"),
            o.str("result"),
        )

    fun record(r: ModRecord): JsonObject =
        buildJsonObject {
            put("id", r.id)
            put("accountId", r.accountId)
            put("type", r.type.name)
            put("actor", r.actor)
            put("reason", r.reason)
            put("start", r.start)
            put("expires", r.expires)
            put("scope", r.scope)
            put("liftedBy", r.liftedBy)
            put("liftedAt", r.liftedAt)
        }

    fun record(o: JsonObject): ModRecord =
        ModRecord(
            o.long("id"),
            o.long("accountId"),
            RecordType.valueOf(o.str("type")),
            o.str("actor"),
            o.str("reason"),
            o.long("start"),
            o.long("expires"),
            o.str("scope"),
            o.str("liftedBy"),
            o.long("liftedAt"),
        )

    fun channel(c: ChannelSettings): JsonObject =
        buildJsonObject {
            c.name?.let { put("name", it) }
            put("enterRank", c.enterRank)
            put("talkRank", c.talkRank)
            put("kickRank", c.kickRank)
        }

    fun channel(o: JsonObject): ChannelSettings =
        ChannelSettings(o["name"]?.jsonPrimitive?.content, o.int("enterRank"), o.int("talkRank"), o.int("kickRank"))

    fun clan(c: ClanRecord): JsonObject =
        buildJsonObject {
            put("id", c.id)
            put("name", c.name)
            put("createdMinute", c.createdMinute)
            put(
                "members",
                JsonArray(
                    c.members.map {
                        buildJsonObject {
                            put("accountId", it.accountId)
                            put("name", it.name)
                            put("rank", it.rank)
                            put("joinedDay", it.joinedDay)
                        }
                    },
                ),
            )
            put("banned", strings(c.banned))
            put("talkRank", c.rules.talkRank)
            put("kickRank", c.rules.kickRank)
            put("allowGuests", c.rules.allowGuests)
            put("recruitRank", c.rules.recruitRank)
        }

    fun clan(o: JsonObject): ClanRecord =
        ClanRecord(
            o.long("id"),
            o.str("name"),
            o.long("createdMinute"),
            o.arr("members").map {
                val m = it.jsonObject
                ClanMemberRow(m.long("accountId"), m.str("name"), m.int("rank"), m.int("joinedDay"))
            },
            o.arr("banned").map { it.jsonPrimitive.content },
            ClanRules(o.int("talkRank"), o.int("kickRank"), o.bool("allowGuests"), o.int("recruitRank")),
        )

    fun offer(g: GeOffer): JsonObject =
        buildJsonObject {
            put("id", g.id)
            put("accountId", g.accountId)
            put("slot", g.slot)
            put("buy", g.buy)
            put("obj", g.obj)
            put("quantity", g.quantity)
            put("price", g.price)
            put("completed", g.completed)
            put("collectCoins", g.collectCoins)
            put("collectItems", g.collectItems)
            put("gold", g.gold)
            put("aborted", g.aborted)
            put("taxRate", g.taxRate)
            put("tax", g.tax)
        }

    fun offer(o: JsonObject): GeOffer =
        GeOffer(
            o.long("id"),
            o.long("accountId"),
            o.int("slot"),
            o.bool("buy"),
            o.int("obj"),
            o.int("quantity"),
            o.int("price"),
            o.int("completed"),
            o.long("collectCoins"),
            o.int("collectItems"),
            o.long("gold"),
            o.bool("aborted"),
            o.int("taxRate"),
            o.long("tax"),
        )

    fun listing(l: TpListing): JsonObject =
        buildJsonObject {
            put("id", l.id)
            put("accountId", l.accountId)
            put("seller", l.seller)
            put("obj", l.obj)
            put("quantity", l.quantity)
            put("sold", l.sold)
            put("price", l.price)
            put("listedAtMs", l.listedAtMs)
            put("closed", l.closed)
        }

    fun listing(o: JsonObject): TpListing =
        TpListing(
            o.long("id"),
            o.long("accountId"),
            o.str("seller"),
            o.int("obj"),
            o.int("quantity"),
            o.int("sold"),
            o.int("price"),
            o.long("listedAtMs"),
            o.bool("closed"),
        )

    fun collect(c: TpCollect): JsonObject =
        buildJsonObject {
            put("coins", c.coins)
            put("items", JsonObject(c.items.entries.associate { (k, v) -> k.toString() to JsonPrimitive(v) }))
        }

    fun collect(o: JsonObject): TpCollect =
        TpCollect(
            o.long("coins"),
            o.obj("items").entries.associate { (k, v) ->
                k.toInt() to
                    v.jsonPrimitive.int
            },
        )

    fun trade(t: TpTrade): JsonObject =
        buildJsonObject {
            put("accountId", t.accountId)
            put("obj", t.obj)
            put("quantity", t.quantity)
            put("price", t.price)
            put("bought", t.bought)
            put("other", t.other)
            put("atMs", t.atMs)
        }

    fun trade(o: JsonObject): TpTrade =
        TpTrade(o.long("accountId"), o.int("obj"), o.int("quantity"), o.int("price"), o.bool("bought"), o.str("other"), o.long("atMs"))
}
