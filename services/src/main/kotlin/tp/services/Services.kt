package tp.services

/**
 * The hub's services as seen by world-nodes (brief §7). Kotlin interfaces first; the in-process implementation lives
 * in `hub`, and the gRPC adapters (services.proto) implement the same interfaces across processes. All calls are
 * suspend: world threads never call them directly, they hand work to virtual threads (login, saves) and get results
 * back through the world's inbox.
 */
interface HubServices {
    val accounts: AccountService
    val saves: SaveService
    val worlds: WorldDirectory
    val social: SocialService
    val exchange: ExchangeService
    val hiscores: HiscoreService
    val moderation: ModerationService
    val broadcasts: BroadcastService
}

// ---------------------------------------------------------------- accounts and saves

enum class LoginResult { OK, INVALID_CREDENTIALS, ALREADY_ONLINE, BANNED, THROTTLED, SAVE_PENDING, SERVER_ERROR }

/** Staff rights (permissions as data: brief §12). */
enum class Rights { PLAYER, MODERATOR, ADMIN }

class LoginGrant(
    val result: LoginResult,
    val accountId: Long = 0,
    val displayName: String = "",
    val rights: Rights = Rights.PLAYER,
    /** Latest durable save (JSON, versioned), or null for a new character. */
    val save: String? = null,
    /** Muted until (epoch ms), 0 = not muted. */
    val mutedUntil: Long = 0,
)

interface AccountService {
    /**
     * Authenticates and takes the account's session lock for [worldId]. Fails with ALREADY_ONLINE while another world
     * holds it, and SAVE_PENDING while the last logout save isn't durable yet (dupe prevention: brief §7).
     */
    suspend fun login(username: String, password: String, worldId: Int, ip: String): LoginGrant

    /** Writes the final save durably (blank = nothing to write), then releases the session lock (world hop / logout). */
    suspend fun logout(accountId: Long, worldId: Int, save: String)

    /** Sign-up (hub HTTP endpoint). Returns null on success, else a reason. */
    suspend fun register(username: String, password: String, ip: String): String?
}

interface SaveService {
    /** Autosave (write-behind on the world side); durable when this returns. */
    suspend fun save(accountId: Long, worldId: Int, save: String)
}

// ---------------------------------------------------------------- worlds

class WorldInfo(val id: Int, val host: String, val port: Int, val activity: String, val flags: Int, val population: Int)

interface WorldDirectory {
    suspend fun register(world: WorldInfo)

    suspend fun heartbeat(worldId: Int, population: Int)

    suspend fun list(): List<WorldInfo>
}

// ---------------------------------------------------------------- social

/** A friends-list row; [rank] is the friends chat rank the owner gave (0 = plain friend). */
class FriendEntry(val name: String, val worldId: Int, val rank: Int = 0)

/** Something a world must deliver to one of its players (streamed from the hub). */
sealed interface SocialEvent {
    val toAccount: Long

    class PrivateMessage(override val toAccount: Long, val from: String, val fromRights: Rights, val text: String) : SocialEvent

    /** A friend logged in/out or hopped ([worldId] 0 = offline). */
    class FriendStatus(override val toAccount: Long, val name: String, val worldId: Int) : SocialEvent

    /** Mute changed while online ([until] epoch ms; 0 = unmuted, negative = permanent). */
    class Muted(override val toAccount: Long, val until: Long) : SocialEvent

    /** Joined a friends chat channel: the full member list. */
    class ChannelJoined(
        override val toAccount: Long,
        val owner: String,
        val channelName: String,
        val kickRank: Int,
        val members: List<ChannelMember>,
    ) : SocialEvent

    /** Left the channel, or was [kicked] from it. */
    class ChannelLeft(override val toAccount: Long, val kicked: Boolean = false) : SocialEvent

    /** One member joined, left ([removed]) or changed rank. */
    class ChannelMemberUpdate(override val toAccount: Long, val member: ChannelMember, val removed: Boolean) : SocialEvent

    class ChannelMessage(
        override val toAccount: Long,
        val from: String,
        val fromRights: Rights,
        val channelName: String,
        val worldId: Int,
        val text: String,
    ) : SocialEvent
}

/**
 * A friends chat member. Ranks follow the client's values: -1 unranked guest, 0 friend, 1 recruit .. 6 general,
 * 7 owner, 127 Jagex moderator.
 */
class ChannelMember(val name: String, val worldId: Int, val rank: Int)

/**
 * A friends chat channel's settings. A null [name] means the channel is disabled and can't be joined. [enterRank],
 * [talkRank] and [kickRank] are the minimum ranks: -1 anyone, 0 any friends, 1..6 recruit+..general+, 7 only the owner.
 */
class ChannelSettings(val name: String?, val enterRank: Int, val talkRank: Int, val kickRank: Int) {
    companion object {
        /** New channels: disabled until named; anyone may enter and talk, only the owner kicks (friends-chat.md OPEN-FC1). */
        val DEFAULT = ChannelSettings(null, -1, -1, 7)
    }
}

interface SocialService {
    suspend fun friends(accountId: Long): List<FriendEntry>

    suspend fun ignores(accountId: Long): List<String>

    /** Null on success, else the game message to show. */
    suspend fun addFriend(accountId: Long, name: String): String?

    suspend fun removeFriend(accountId: Long, name: String)

    suspend fun addIgnore(accountId: Long, name: String): String?

    suspend fun removeIgnore(accountId: Long, name: String)

    /** Null on success, else the game message to show ("That player is currently offline."). */
    suspend fun privateMessage(fromAccount: Long, toName: String, text: String): String?

    // Friends chat (docs/mechanics/friends-chat.md). Each returns null on success, else the game message to show.

    suspend fun joinChannel(accountId: Long, owner: String): String?

    suspend fun leaveChannel(accountId: Long)

    suspend fun channelMessage(accountId: Long, text: String): String?

    suspend fun channelKick(accountId: Long, name: String): String?

    suspend fun channelSettings(accountId: Long): ChannelSettings

    /** Updates [accountId]'s own channel; members below the new enter rank are removed. */
    suspend fun setChannelSettings(accountId: Long, settings: ChannelSettings)

    /** The channel owner ranks a friend ([rank] -1 removes the rank). */
    suspend fun setChannelRank(accountId: Long, name: String, rank: Int): String?

    /** Events for players on [worldId] (stream). */
    fun subscribe(worldId: Int, sink: (SocialEvent) -> Unit): AutoCloseable
}

// ---------------------------------------------------------------- Grand Exchange skeleton

class GeOffer(
    val id: Long,
    val accountId: Long,
    val slot: Int,
    val buy: Boolean,
    val obj: Int,
    val quantity: Int,
    val price: Int,
    val completed: Int,
    /** Coins / items waiting to be collected. */
    val collectCoins: Long,
    val collectItems: Int,
)

interface ExchangeService {
    /** Places an offer; the world has already removed coins/items through an item transaction. */
    suspend fun place(accountId: Long, slot: Int, buy: Boolean, obj: Int, quantity: Int, price: Int): GeOffer

    suspend fun offers(accountId: Long): List<GeOffer>

    /** Takes what is waiting in [slot]; the world adds it through an item transaction. */
    suspend fun collect(accountId: Long, slot: Int): GeOffer?

    suspend fun abort(accountId: Long, slot: Int): GeOffer?

    /** Guide prices (obj -> coins) from completed trades; objs never traded are absent (callers fall back to cost). */
    suspend fun guidePrices(): Map<Int, Int>
}

// ---------------------------------------------------------------- hiscores, moderation, broadcasts

class HiscoreEntry(val name: String, val level: Int, val xp: Long)

interface HiscoreService {
    suspend fun submit(accountId: Long, name: String, xpTenths: IntArray)

    /** Top [count] for [stat] (-1 = overall). */
    suspend fun top(stat: Int, count: Int): List<HiscoreEntry>
}

enum class Sanction { KICK, MUTE, BAN, IP_BAN, UNMUTE, UNBAN }

interface ModerationService {
    /** Applies [sanction] to [target] (duration in minutes, 0 = permanent). Every action is audit-logged. */
    suspend fun apply(staffAccount: Long, target: String, sanction: Sanction, minutes: Int, reason: String): String?

    /** Pending kicks for players on [worldId] (stream): account ids. */
    fun subscribeKicks(worldId: Int, sink: (Long) -> Unit): AutoCloseable
}

interface BroadcastService {
    suspend fun broadcast(text: String)

    /** System update in [seconds] (client countdown; worlds save and stop). */
    suspend fun systemUpdate(seconds: Int)

    fun subscribe(sink: (Broadcast) -> Unit): AutoCloseable
}

sealed interface Broadcast {
    class Message(val text: String) : Broadcast

    class SystemUpdate(val seconds: Int) : Broadcast
}
