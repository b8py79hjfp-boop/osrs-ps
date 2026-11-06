package tp.hub

import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.Rights
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** In-memory [HubStore] (dev, tests). Thread-safe; nothing survives a restart. */
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
    val auditLog: MutableList<String> = java.util.Collections.synchronizedList(ArrayList())

    override fun account(username: String): AccountRow? = byName[username.lowercase()]

    override fun account(id: Long): AccountRow? = byId[id]

    @Synchronized
    override fun createAccount(username: String, passwordHash: String, rights: Rights): AccountRow? {
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

    override fun setRights(id: Long, rights: Rights) =
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

    override fun offers(id: Long): List<GeOffer> = geOffers.values.filter { it.accountId == id }.sortedBy { it.slot }

    override fun openOffers(obj: Int, buy: Boolean): List<GeOffer> =
        geOffers.values.filter { it.obj == obj && it.buy == buy && it.completed < it.quantity }.sortedBy { it.id }

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

    override fun submitHiscores(id: Long, name: String, xpTenths: IntArray) {
        hiscores[id] = name to xpTenths.copyOf()
    }

    override fun topHiscores(stat: Int, count: Int): List<HiscoreEntry> = Hiscores.rank(hiscores.values.toList(), stat, count)

    override fun audit(actor: String, action: String, target: String, detail: String) {
        auditLog += "${System.currentTimeMillis()} $actor $action $target $detail"
    }

    override fun close() = Unit
}

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
