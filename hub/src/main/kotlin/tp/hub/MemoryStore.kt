package tp.hub

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import tp.services.ChannelSettings
import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.TpCollect
import tp.services.TpListing
import tp.services.TpTrade
import tp.util.Rank
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [HubStore] (tests, `--memory`). Thread-safe; nothing survives a restart unless [FileStore] snapshots it
 * ([exportState] / [importState]).
 */
@Suppress("LargeClass", "TooManyFunctions") // one map per HubStore table, plus the snapshot codec
class MemoryStore : HubStore {
    private val ids = AtomicLong()
    private val offerIds = AtomicLong()
    private val byName = ConcurrentHashMap<String, AccountRow>()
    private val byId = ConcurrentHashMap<Long, AccountRow>()
    private val saves = ConcurrentHashMap<Long, String>()
    private val friendLists = ConcurrentHashMap<Long, MutableSet<String>>()
    private val ignoreLists = ConcurrentHashMap<Long, MutableSet<String>>()
    private val bannedIps = ConcurrentHashMap.newKeySet<String>()
    private val lastIps = ConcurrentHashMap<Long, String>()
    private val geOffers = ConcurrentHashMap<Long, GeOffer>()
    private val hiscores = ConcurrentHashMap<Long, Pair<String, IntArray>>()
    private val auditLog: MutableList<AuditRow> = java.util.Collections.synchronizedList(ArrayList())
    private val auditIds = AtomicLong()

    override fun account(username: String): AccountRow? = byName[username.lowercase()]

    override fun account(id: Long): AccountRow? = byId[id]

    @Synchronized
    override fun createAccount(username: String, passwordHash: String, rights: Rank): AccountRow? {
        val key = username.lowercase()
        if (byName.containsKey(key)) return null
        val row = AccountRow(ids.incrementAndGet(), key, username, passwordHash, rights, 0, 0)
        byName[key] = row
        byId[row.id] = row
        return row
    }

    private fun update(id: Long, f: (AccountRow) -> AccountRow) {
        val row = byId[id] ?: return
        val next = f(row)
        byId[id] = next
        byName[next.username] = next
    }

    override fun setPasswordHash(id: Long, hash: String) =
        update(id) {
            AccountRow(it.id, it.username, it.displayName, hash, it.rights, it.mutedUntil, it.bannedUntil)
        }

    override fun accountByDisplayName(name: String): AccountRow? =
        byId.values.firstOrNull {
            it.displayName.equals(
                name,
                ignoreCase = true,
            )
        }

    override fun setDisplayName(id: Long, name: String) =
        update(id) {
            AccountRow(it.id, it.username, name, it.passwordHash, it.rights, it.mutedUntil, it.bannedUntil)
        }

    override fun auditQuery(who: String, filter: String, limit: Int): List<String> =
        synchronized(auditLog) { auditLog.toList() }
            .asReversed()
            .filter { r -> who.isBlank() || r.actor.equals(who, true) || r.target.equals(who, true) }
            .map { r -> "${r.atMs} ${r.actor} ${r.action} ${r.target} ${r.detail}" }
            .filter { line -> filter.isBlank() || line.contains(filter, ignoreCase = true) }
            .take(limit)

    override fun auditSearch(q: AuditSearch): List<AuditRow> =
        synchronized(auditLog) { auditLog.toList() }.asReversed().filter(q::matches).take(q.limit)

    override fun allRecords(limit: Int): List<tp.util.ModRecord> = records.sortedByDescending { it.id }.take(limit)

    override fun staffAccounts(): List<AccountRow> = byId.values.filter { it.rights.isStaff }.sortedBy { it.id }

    override fun setRights(id: Long, rights: Rank) =
        update(id) {
            AccountRow(it.id, it.username, it.displayName, it.passwordHash, rights, it.mutedUntil, it.bannedUntil)
        }

    override fun setMuted(id: Long, until: Long) =
        update(id) {
            AccountRow(it.id, it.username, it.displayName, it.passwordHash, it.rights, until, it.bannedUntil)
        }

    override fun setBanned(id: Long, until: Long) =
        update(id) {
            AccountRow(it.id, it.username, it.displayName, it.passwordHash, it.rights, it.mutedUntil, until)
        }

    override fun ipBanned(ip: String): Boolean = ip in bannedIps

    override fun setIpBan(ip: String, banned: Boolean) {
        if (banned) bannedIps += ip else bannedIps -= ip
    }

    override fun lastIp(id: Long): String? = lastIps[id]

    /** (account, kind, value) -> last seen. */
    private val seenRows = ConcurrentHashMap<Triple<Long, String, String>, Long>()

    override fun recordSeen(accountId: Long, kind: String, value: String, at: Long) {
        if (value.isNotEmpty()) seenRows[Triple(accountId, kind, value)] = at
    }

    override fun seen(accountId: Long, kind: String): List<String> =
        seenRows.entries
            .filter { it.key.first == accountId && it.key.second == kind }
            .sortedByDescending { it.value }
            .map { it.key.third }

    override fun accountsSeen(kind: String, value: String): List<Long> =
        seenRows.keys
            .filter { it.second == kind && it.third == value }
            .map { it.first }
            .distinct()

    private val donations = ConcurrentHashMap<Long, Long>()

    override fun addDonation(accountId: Long, cents: Long, actor: String, note: String, at: Long) {
        donations.merge(accountId, cents, Long::plus)
    }

    override fun donationTotal(accountId: Long): Long = donations[accountId] ?: 0L

    private val records = java.util.concurrent.CopyOnWriteArrayList<tp.util.ModRecord>()
    private val recordIds =
        java.util.concurrent.atomic
            .AtomicLong()

    override fun addRecord(record: tp.util.ModRecord): tp.util.ModRecord {
        val stored =
            with(record) { tp.util.ModRecord(recordIds.incrementAndGet(), accountId, type, actor, reason, start, expires, scope) }
        records += stored
        return stored
    }

    override fun records(accountId: Long): List<tp.util.ModRecord> =
        records.filter { it.accountId == accountId }.sortedByDescending { it.id }

    @Synchronized
    override fun liftRecords(accountId: Long, type: tp.util.RecordType, by: String, at: Long): Int {
        var lifted = 0
        for ((i, r) in records.withIndex()) {
            if (r.accountId != accountId || r.type != type || !r.active(at)) continue
            records[i] = with(r) { tp.util.ModRecord(id, accountId, type, actor, reason, start, expires, scope, by, at) }
            lifted++
        }
        return lifted
    }

    override fun scopeBanned(type: tp.util.RecordType, scope: String, now: Long): Boolean =
        scope.isNotEmpty() && records.any { it.type == type && it.scope == scope && it.active(now) }

    override fun setLastIp(id: Long, ip: String) {
        lastIps[id] = ip
    }

    override fun loadSave(id: Long): String? = saves[id]

    override fun writeSave(id: Long, save: String) {
        saves[id] = save
    }

    override fun friends(id: Long): List<String> = friendLists[id]?.toList().orEmpty()

    override fun setFriend(id: Long, name: String, present: Boolean) {
        val set = friendLists.getOrPut(id) { ConcurrentHashMap.newKeySet() }
        if (present) set += name else set.removeIf { it.equals(name, ignoreCase = true) }
    }

    override fun ignores(id: Long): List<String> = ignoreLists[id]?.toList().orEmpty()

    override fun setIgnore(id: Long, name: String, present: Boolean) {
        val set = ignoreLists.getOrPut(id) { ConcurrentHashMap.newKeySet() }
        if (present) set += name else set.removeIf { it.equals(name, ignoreCase = true) }
    }

    private val channelRanks = ConcurrentHashMap<Long, ConcurrentHashMap<String, Int>>()
    private val channelSettings = ConcurrentHashMap<Long, ChannelSettings>()

    override fun channelSettings(id: Long): ChannelSettings? = channelSettings[id]

    override fun setChannelSettings(id: Long, settings: ChannelSettings) {
        channelSettings[id] = settings
    }

    override fun channelRanks(id: Long): Map<String, Int> = channelRanks[id]?.toMap().orEmpty()

    override fun setChannelRank(id: Long, name: String, rank: Int) {
        val map = channelRanks.getOrPut(id) { ConcurrentHashMap() }
        if (rank >= 0) map[name.lowercase()] = rank else map.remove(name.lowercase())
    }

    private val clans = ConcurrentHashMap<Long, tp.services.ClanRecord>()

    override fun clans(): List<tp.services.ClanRecord> = clans.values.toList()

    override fun putClan(clan: tp.services.ClanRecord) {
        clans[clan.id] = clan
    }

    override fun deleteClan(id: Long) {
        clans.remove(id)
    }

    private val prices = ConcurrentHashMap<Int, Pair<Int, Int>>()

    override fun guidePrices(): Map<Int, Pair<Int, Int>> = prices.toMap()

    override fun setGuidePrice(obj: Int, price: Int, weight: Int) {
        prices[obj] = price to weight
    }

    override fun offers(id: Long): List<GeOffer> = geOffers.values.filter { it.accountId == id }.sortedBy { it.slot }

    override fun openOffers(obj: Int, buy: Boolean): List<GeOffer> =
        geOffers.values.filter { it.obj == obj && it.buy == buy && !it.aborted && it.completed < it.quantity }.sortedBy { it.id }

    override fun putOffer(offer: GeOffer): GeOffer {
        val stored =
            if (offer.id == 0L) {
                GeOffer(
                    offerIds.incrementAndGet(),
                    offer.accountId,
                    offer.slot,
                    offer.buy,
                    offer.obj,
                    offer.quantity,
                    offer.price,
                    offer.completed,
                    offer.collectCoins,
                    offer.collectItems,
                    offer.gold,
                    offer.aborted,
                    offer.taxRate,
                    offer.tax,
                )
            } else {
                offer
            }
        geOffers[stored.id] = stored
        return stored
    }

    override fun deleteOffer(offerId: Long) {
        geOffers.remove(offerId)
    }

    private val geHistory = java.util.concurrent.ConcurrentHashMap<Long, ArrayDeque<GeOffer>>()

    override fun addHistory(offer: GeOffer) {
        val rows = geHistory.computeIfAbsent(offer.accountId) { ArrayDeque() }
        synchronized(rows) {
            rows.addFirst(offer)
            while (rows.size > GE_HISTORY_SIZE) rows.removeLast()
        }
    }

    override fun history(id: Long): List<GeOffer> = geHistory[id]?.let { synchronized(it) { it.toList() } }.orEmpty()

    private val tpIds = AtomicLong()
    private val tpListings = ConcurrentHashMap<Long, TpListing>()
    private val tpCollects = ConcurrentHashMap<Long, TpCollect>()
    private val tpTrades = ConcurrentHashMap<Long, ArrayDeque<TpTrade>>()

    override fun tpOpenListings(): List<TpListing> = tpListings.values.filter { !it.closed && it.remaining > 0 }.sortedBy { it.id }

    override fun putTpListing(listing: TpListing): TpListing {
        val stored =
            if (listing.id == 0L) {
                with(listing) { TpListing(tpIds.incrementAndGet(), accountId, seller, obj, quantity, sold, price, listedAtMs, closed) }
            } else {
                listing
            }
        tpListings[stored.id] = stored
        return stored
    }

    override fun tpCollect(id: Long): TpCollect = tpCollects[id] ?: TpCollect(0, emptyMap())

    override fun setTpCollect(id: Long, collect: TpCollect) {
        if (collect.empty) tpCollects.remove(id) else tpCollects[id] = collect
    }

    override fun addTpTrade(trade: TpTrade) {
        val rows = tpTrades.computeIfAbsent(trade.accountId) { ArrayDeque() }
        synchronized(rows) {
            rows.addFirst(trade)
            while (rows.size > TP_HISTORY_SIZE) rows.removeLast()
        }
    }

    override fun tpTrades(id: Long): List<TpTrade> = tpTrades[id]?.let { synchronized(it) { it.toList() } }.orEmpty()

    override fun submitHiscores(id: Long, name: String, xpTenths: IntArray) {
        hiscores[id] = name to xpTenths.copyOf()
    }

    override fun topHiscores(stat: Int, count: Int): List<HiscoreEntry> = Hiscores.rank(hiscores.values.toList(), stat, count)

    private val auditKeys: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    override fun audited(action: String, target: String): Boolean = "$action\u0000$target" in auditKeys

    override fun auditEntry(actor: String, action: String, target: String, detail: String, source: String, ip: String, result: String) {
        auditKeys += "$action\u0000$target"
        auditLog += AuditRow(auditIds.incrementAndGet(), System.currentTimeMillis(), actor, action, target, detail, source, ip, result)
    }

    override fun close() = Unit

    /** Everything this store holds, as one JSON document (D-097). Weakly consistent while writers run. */
    fun exportState(): JsonObject =
        buildJsonObject {
            put("v", STATE_VERSION)
            put("ids", ids.get())
            put("offerIds", offerIds.get())
            put("recordIds", recordIds.get())
            put("tpIds", tpIds.get())
            put("accounts", JsonArray(byId.values.sortedBy { it.id }.map(StateCodec::account)))
            put("saves", longMap(saves) { JsonPrimitive(it) })
            put("friends", longMap(friendLists) { strings(it) })
            put("ignores", longMap(ignoreLists) { strings(it) })
            put("bannedIps", strings(bannedIps))
            put("lastIps", longMap(lastIps) { JsonPrimitive(it) })
            put("seen", JsonArray(seenRows.map { (k, at) -> JsonArray(listOf(k.first, k.second, k.third, at).map(::prim)) }))
            put("donations", longMap(donations) { JsonPrimitive(it) })
            put("records", JsonArray(records.map(StateCodec::record)))
            put("channelRanks", longMap(channelRanks) { m -> JsonObject(m.mapValues { JsonPrimitive(it.value) }) })
            put("channelSettings", longMap(channelSettings, StateCodec::channel))
            put("clans", JsonArray(clans.values.map(StateCodec::clan)))
            put(
                "prices",
                JsonObject(
                    prices.entries.associate { (k, v) ->
                        k.toString() to JsonArray(listOf(prim(v.first), prim(v.second)))
                    },
                ),
            )
            put("geOffers", JsonArray(geOffers.values.map(StateCodec::offer)))
            put("geHistory", longMap(geHistory) { q -> JsonArray(synchronized(q) { q.toList() }.map(StateCodec::offer)) })
            put("tpListings", JsonArray(tpListings.values.map(StateCodec::listing)))
            put("tpCollects", longMap(tpCollects, StateCodec::collect))
            put("tpTrades", longMap(tpTrades) { q -> JsonArray(synchronized(q) { q.toList() }.map(StateCodec::trade)) })
            put("hiscores", longMap(hiscores) { (name, xp) -> JsonArray(listOf(prim(name), JsonArray(xp.map(::prim)))) })
            put("auditRows", JsonArray(synchronized(auditLog) { auditLog.toList() }.map(StateCodec::audit)))
            put("auditKeys", strings(synchronized(auditKeys) { auditKeys.toList() }))
        }

    /** Loads a document written by [exportState] into this (empty) store. Unknown keys are ignored; missing ones stay empty. */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // one line per table
    fun importState(o: JsonObject) {
        val v = o["v"]?.jsonPrimitive?.int ?: 0
        require(v in 1..STATE_VERSION) { "unknown state version $v" }
        ids.set(o.long("ids"))
        offerIds.set(o.long("offerIds"))
        recordIds.set(o.long("recordIds"))
        tpIds.set(o.long("tpIds"))
        o.arr("accounts").map { StateCodec.account(it.jsonObject) }.forEach {
            byId[it.id] = it
            byName[it.username] = it
        }
        o.obj("saves").forEach { (k, e) -> saves[k.toLong()] = e.jsonPrimitive.content }
        o.obj("friends").forEach { (k, e) -> friendLists[k.toLong()] = keySet(e) }
        o.obj("ignores").forEach { (k, e) -> ignoreLists[k.toLong()] = keySet(e) }
        o.arr("bannedIps").forEach { bannedIps += it.jsonPrimitive.content }
        o.obj("lastIps").forEach { (k, e) -> lastIps[k.toLong()] = e.jsonPrimitive.content }
        o.arr("seen").forEach {
            val a = it.jsonArray
            seenRows[Triple(a[0].jsonPrimitive.long, a[1].jsonPrimitive.content, a[2].jsonPrimitive.content)] = a[3].jsonPrimitive.long
        }
        o.obj("donations").forEach { (k, e) -> donations[k.toLong()] = e.jsonPrimitive.long }
        o.arr("records").forEach { records += StateCodec.record(it.jsonObject) }
        o.obj("channelRanks").forEach { (k, e) ->
            channelRanks[k.toLong()] = ConcurrentHashMap(e.jsonObject.mapValues { it.value.jsonPrimitive.int })
        }
        o.obj("channelSettings").forEach { (k, e) -> channelSettings[k.toLong()] = StateCodec.channel(e.jsonObject) }
        o.arr("clans").map { StateCodec.clan(it.jsonObject) }.forEach { clans[it.id] = it }
        o.obj("prices").forEach { (k, e) -> prices[k.toInt()] = e.jsonArray[0].jsonPrimitive.int to e.jsonArray[1].jsonPrimitive.int }
        o.arr("geOffers").map { StateCodec.offer(it.jsonObject) }.forEach { geOffers[it.id] = it }
        o.obj("geHistory").forEach { (k, e) -> geHistory[k.toLong()] = ArrayDeque(e.jsonArray.map { StateCodec.offer(it.jsonObject) }) }
        o.arr("tpListings").map { StateCodec.listing(it.jsonObject) }.forEach { tpListings[it.id] = it }
        o.obj("tpCollects").forEach { (k, e) -> tpCollects[k.toLong()] = StateCodec.collect(e.jsonObject) }
        o.obj("tpTrades").forEach { (k, e) -> tpTrades[k.toLong()] = ArrayDeque(e.jsonArray.map { StateCodec.trade(it.jsonObject) }) }
        o.obj("hiscores").forEach { (k, e) ->
            val a = e.jsonArray
            hiscores[k.toLong()] = a[0].jsonPrimitive.content to a[1].jsonArray.map { it.jsonPrimitive.int }.toIntArray()
        }
        // Version 1 files hold "ms actor action target detail" strings; later ones structured rows.
        o.arr("audit").forEach { line ->
            val f = line.jsonPrimitive.content.split(' ', limit = AUDIT_FIELDS)
            auditLog +=
                AuditRow(
                    auditIds.incrementAndGet(),
                    f[0].toLongOrNull() ?: 0,
                    f.getOrElse(1) { "" },
                    f.getOrElse(2) { "" },
                    f.getOrElse(AUDIT_TARGET) { "" },
                    f.getOrElse(AUDIT_DETAIL) { "" },
                    AUDIT_SOURCE_GAME,
                    "",
                    "",
                )
        }
        o.arr("auditRows").forEach { auditLog += StateCodec.audit(it.jsonObject).also { r -> auditIds.accumulateAndGet(r.id, ::maxOf) } }
        o.arr("auditKeys").forEach { auditKeys += it.jsonPrimitive.content }
    }

    private fun keySet(e: JsonElement): MutableSet<String> =
        ConcurrentHashMap.newKeySet<String>().also { s ->
            e.jsonArray.forEach {
                s +=
                    it.jsonPrimitive.content
            }
        }
}

/** Version of [MemoryStore.exportState]'s document; bump with a migration in importState when a table changes shape. */
private const val STATE_VERSION = 1

/** Hiscore ranking shared by the stores: xp tenths per stat, overall = sum. */
internal object Hiscores {
    fun rank(rows: List<Pair<String, IntArray>>, stat: Int, count: Int): List<HiscoreEntry> =
        rows
            .map { (name, xp) ->
                val total = if (stat < 0) xp.sumOf { it.toLong() } else xp.getOrElse(stat) { 0 }.toLong()
                val level = if (stat < 0) xp.sumOf { Levels.of(it / TENTHS) } else Levels.of(xp.getOrElse(stat) { 0 } / TENTHS)
                HiscoreEntry(name, level, total / TENTHS)
            }.sortedWith(compareByDescending<HiscoreEntry> { it.xp }.thenBy { it.name })
            .take(count)

    private const val TENTHS = 10
}

/** OSRS experience table (OSRS Wiki "Experience"), for hiscore levels. */
internal object Levels {
    private val table: IntArray =
        IntArray(MAX + 1).also { t ->
            var points = 0.0
            for (level in 2..MAX) {
                val l = level - 1
                @Suppress("MagicNumber")
                points += kotlin.math.floor(l + 300.0 * Math.pow(2.0, l / 7.0))
                t[level] = (points / 4).toInt()
            }
        }

    fun of(xp: Int): Int {
        var level = 1
        while (level < MAX && xp >= table[level + 1]) level++
        return level
    }

    private const val MAX = 99
}

/** Memory audit rows are "ms actor action target detail". */
private const val AUDIT_FIELDS = 5
private const val AUDIT_TARGET = 3
private const val AUDIT_DETAIL = 4
