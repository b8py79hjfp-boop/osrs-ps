package tp.hub

import tp.services.GeOffer
import tp.services.HiscoreEntry
import tp.services.Rights

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

    fun offers(id: Long): List<GeOffer>

    fun openOffers(obj: Int, buy: Boolean): List<GeOffer>

    fun putOffer(offer: GeOffer): GeOffer

    fun deleteOffer(offerId: Long)

    fun submitHiscores(id: Long, name: String, xpTenths: IntArray)

    fun topHiscores(stat: Int, count: Int): List<HiscoreEntry>

    /** Append-only audit log (staff actions, logins): who, what, when. */
    fun audit(actor: String, action: String, target: String, detail: String)
}
