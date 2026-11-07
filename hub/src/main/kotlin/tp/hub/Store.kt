package tp.hub

import tp.services.ChannelSettings
import tp.services.ClanRecord
import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.Rights
import tp.services.TpCollect
import tp.services.TpListing
import tp.services.TpTrade

class AccountRow(
    val id: Long,
    val username: String,
    val displayName: String,
    val passwordHash: String,
    val rights: Rights,
    val mutedUntil: Long,
    val bannedUntil: Long,
)

/**
 * Hub persistence (brief §7, §12). [MemoryStore] for dev and tests, [PostgresStore] for production. Blocking calls:
 * the hub runs them on virtual threads. Usernames are compared case-insensitively (lower-cased keys).
 */
interface HubStore : AutoCloseable {
    fun account(username: String): AccountRow?

    fun account(id: Long): AccountRow?

    /** Creates an account; null if the name is taken. */
    fun createAccount(username: String, passwordHash: String, rights: Rights): AccountRow?

    fun setRights(id: Long, rights: Rights)

    fun setMuted(id: Long, until: Long)

    fun setBanned(id: Long, until: Long)

    fun ipBanned(ip: String): Boolean

    fun setIpBan(ip: String, banned: Boolean)

    fun lastIp(id: Long): String?

    fun setLastIp(id: Long, ip: String)

    fun loadSave(id: Long): String?

    /** Durable when this returns (brief §7: a logout save must be durable before the next login). */
    fun writeSave(id: Long, save: String)

    fun friends(id: Long): List<String>

    fun setFriend(id: Long, name: String, present: Boolean)

    fun ignores(id: Long): List<String>

    fun setIgnore(id: Long, name: String, present: Boolean)

    /** Friends chat settings of [id]'s channel (null = never configured). */
    fun channelSettings(id: Long): ChannelSettings?

    fun setChannelSettings(id: Long, settings: ChannelSettings)

    /** Friends chat ranks the owner [id] gave (lower-cased name -> rank 0..6). */
    fun channelRanks(id: Long): Map<String, Int>

    /** Sets (rank >= 0) or removes (rank < 0) a friends chat rank. */
    fun setChannelRank(id: Long, name: String, rank: Int)

    /** Every clan (loaded once at hub start; clans are few and small). */
    fun clans(): List<ClanRecord>

    /** Inserts or replaces a clan, members and bans included. */
    fun putClan(clan: ClanRecord)

    fun deleteClan(id: Long)

    /** Guide prices (obj -> price) and their trade weight, for the moving average. */
    fun guidePrices(): Map<Int, Pair<Int, Int>>

    fun setGuidePrice(obj: Int, price: Int, weight: Int)

    fun offers(id: Long): List<GeOffer>

    fun openOffers(obj: Int, buy: Boolean): List<GeOffer>

    fun putOffer(offer: GeOffer): GeOffer

    fun deleteOffer(offerId: Long)

    /** Appends a trade record (GE12) and keeps the newest [GE_HISTORY_SIZE]. */
    fun addHistory(offer: GeOffer)

    /** Newest first. */
    fun history(id: Long): List<GeOffer>

    /** Trading post: open listings (all accounts), oldest first. */
    fun tpOpenListings(): List<TpListing>

    /** Inserts ([TpListing.id] 0) or updates a listing; returns it with its id. */
    fun putTpListing(listing: TpListing): TpListing

    fun tpCollect(id: Long): TpCollect

    fun setTpCollect(id: Long, collect: TpCollect)

    /** Appends a trade and keeps the newest [TP_HISTORY_SIZE] per account. */
    fun addTpTrade(trade: TpTrade)

    /** Newest first. */
    fun tpTrades(id: Long): List<TpTrade>

    fun submitHiscores(id: Long, name: String, xpTenths: IntArray)

    fun topHiscores(stat: Int, count: Int): List<HiscoreEntry>

    /** Append-only audit log (staff actions, logins): who, what, when. */
    fun audit(actor: String, action: String, target: String, detail: String)
}

/** GE history length: "now extends to 40 previous listings" (OSRS Wiki "Grand Exchange" changes). */
const val GE_HISTORY_SIZE: Int = 40

/** Trading post trades kept per account (the History view). */
const val TP_HISTORY_SIZE: Int = 50
