package tp.services

import tp.util.Rank

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
    val tradingPost: TradingPostService
    val hiscores: HiscoreService
    val moderation: ModerationService
    val broadcasts: BroadcastService
}

// ---------------------------------------------------------------- accounts and saves

enum class LoginResult { OK, INVALID_CREDENTIALS, ALREADY_ONLINE, BANNED, THROTTLED, SAVE_PENDING, SERVER_ERROR }

class LoginGrant(
    val result: LoginResult,
    val accountId: Long = 0,
    val displayName: String = "",
    val rights: Rank = Rank.PLAYER,
    /** Latest durable save (JSON, versioned), or null for a new character. */
    val save: String? = null,
    /** Mutes, yell-mutes and jail in force (commands brief §7: applied at the next login when set offline). */
    val sanctions: tp.util.Sanctions = tp.util.Sanctions.NONE,
)

interface AccountService {
    /**
     * Authenticates and takes the account's session lock for [worldId]. Fails with ALREADY_ONLINE while another world
     * holds it, and SAVE_PENDING while the last logout save isn't durable yet (dupe prevention: brief §7).
     */
    suspend fun login(username: String, password: String, worldId: Int, ip: String, device: String = ""): LoginGrant

    /** Writes the final save durably (blank = nothing to write), then releases the session lock (world hop / logout). */
    suspend fun logout(accountId: Long, worldId: Int, save: String)

    /** Sign-up (hub HTTP endpoint). Returns null on success, else a reason. */
    suspend fun register(username: String, password: String, ip: String): String?

    /**
     * Commands brief §8 `::changepassword`: needs the current password, is throttled like a login and audited without
     * its arguments. Returns null on success, else a reason.
     */
    suspend fun changePassword(accountId: Long, current: String, new: String): String? = "Not available."
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

    class PrivateMessage(override val toAccount: Long, val from: String, val fromRights: Rank, val text: String) : SocialEvent

    /** A friend logged in/out or hopped ([worldId] 0 = offline). */
    class FriendStatus(override val toAccount: Long, val name: String, val worldId: Int) : SocialEvent

    /** The account's sanctions changed (commands brief §7: applied at once on the world it is on). */
    class Sanctioned(override val toAccount: Long, val sanctions: tp.util.Sanctions) : SocialEvent

    /** `::warn`: shown to the player with the reason (commands brief §6). */
    class Warned(override val toAccount: Long, val reason: String) : SocialEvent

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

    /** The player's clan ([guest] = the clan they listen to as a guest) changed, or they left it ([clan] null). */
    class ClanState(
        override val toAccount: Long,
        val guest: Boolean,
        val clan: ClanRecord?,
        val online: List<ClanChannelMember>,
    ) : SocialEvent

    class ClanMessage(
        override val toAccount: Long,
        val guest: Boolean,
        val clanName: String,
        val from: String,
        val fromRights: Rank,
        val rank: Int,
        val worldId: Int,
        val text: String,
    ) : SocialEvent

    /** An invitation to join (or found) [clanName] from [from]. */
    class ClanInvite(override val toAccount: Long, val from: String, val clanName: String) : SocialEvent

    class ChannelMessage(
        override val toAccount: Long,
        val from: String,
        val fromRights: Rank,
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

/** A clan as the hub stores it (docs/mechanics/clans.md). [createdMinute] is the epoch minute it was founded. */
class ClanRecord(
    val id: Long,
    val name: String,
    val createdMinute: Long,
    val members: List<ClanMemberRow>,
    val banned: List<String>,
    val rules: ClanRules,
)

/** A ranked clan member; [joinedDay] is the rune day (days since 2002-02-27) they joined. */
class ClanMemberRow(val accountId: Long, val name: String, val rank: Int, val joinedDay: Int)

/** Minimum ranks for talking / kicking in the clan channel, whether guests may join, and who may recruit. */
class ClanRules(val talkRank: Int, val kickRank: Int, val allowGuests: Boolean, val recruitRank: Int)

/** A clan channel member as the client shows it: rank (-1 guest) and world (0 offline). */
class ClanChannelMember(val name: String, val rank: Int, val worldId: Int)

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

    // Clans (docs/mechanics/clans.md). Each returns null on success, else the game message to show.

    suspend fun clanFound(accountId: Long, name: String): String?

    suspend fun clanInvite(accountId: Long, name: String): String?

    suspend fun clanAccept(accountId: Long): String?

    suspend fun clanLeave(accountId: Long): String?

    suspend fun clanSetRank(accountId: Long, name: String, rank: Int): String?

    suspend fun clanKick(accountId: Long, name: String, ban: Boolean): String?

    suspend fun clanSetRules(accountId: Long, rules: ClanRules): String?

    suspend fun clanJoinGuest(accountId: Long, clanName: String): String?

    suspend fun clanLeaveGuest(accountId: Long)

    suspend fun clanMessage(accountId: Long, guest: Boolean, text: String): String?

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
    /** Coins actually exchanged so far, at trade prices (spent buying, or the gross received selling). */
    val gold: Long = 0,
    /** Aborted: no longer matched; shown as finished until collected (grand-exchange.md GE13). */
    val aborted: Boolean = false,
    /** Sell tax in tenths of a percent for this offer (GE14; 0 = exempt or a buy offer). */
    val taxRate: Int = 0,
    /** Tax paid so far (sell offers). */
    val tax: Long = 0,
)

/** What a collect takes from a slot. */
enum class CollectPart { ALL, ITEMS, COINS }

interface ExchangeService {
    /** Places an offer; the world has already removed coins/items through an item transaction. */
    suspend fun place(accountId: Long, slot: Int, buy: Boolean, obj: Int, quantity: Int, price: Int, taxRate: Int = 0): GeOffer

    suspend fun offers(accountId: Long): List<GeOffer>

    /** Takes what is waiting in [slot]; the world adds it through an item transaction. */
    suspend fun collect(accountId: Long, slot: Int, part: CollectPart = CollectPart.ALL): GeOffer?

    /** Marks the offer aborted and moves the unfilled part (coins or items) into its collect box (GE13). */
    suspend fun abort(accountId: Long, slot: Int): GeOffer?

    /**
     * The account's last trades, newest first (grand-exchange.md GE12): each finished or aborted-with-trades offer as a
     * [GeOffer] whose [GeOffer.completed] is the amount traded and [GeOffer.gold] the coins exchanged.
     */
    suspend fun history(accountId: Long): List<GeOffer>

    /** Guide prices (obj -> coins) from completed trades; objs never traded are absent (callers fall back to cost). */
    suspend fun guidePrices(): Map<Int, Int>
}

// ---------------------------------------------------------------- trading post (custom, docs/mechanics/trading-post.md)

/** One trading post listing: [quantity] of [obj] at [price] coins each, [sold] of them bought so far. */
class TpListing(
    val id: Long,
    val accountId: Long,
    val seller: String,
    val obj: Int,
    val quantity: Int,
    val sold: Int,
    val price: Int,
    val listedAtMs: Long,
    val closed: Boolean = false,
) {
    val remaining: Int get() = quantity - sold
}

/** What waits to be collected: coins from sales and items from cancelled listings. */
class TpCollect(val coins: Long, val items: Map<Int, Int>) {
    val empty: Boolean get() = coins == 0L && items.isEmpty()
}

/** One past trade from [accountId]'s side: [bought] or sold [quantity] of [obj] at [price] each with [other]. */
class TpTrade(val accountId: Long, val obj: Int, val quantity: Int, val price: Int, val bought: Boolean, val other: String, val atMs: Long)

/** Result of [TradingPostService.buy]: how many were bought (0 = none) and an error message when nothing was. */
class TpBuyResult(val bought: Int, val listing: TpListing?, val error: String?)

interface TradingPostService {
    /** Lists [quantity] [obj] at [price] each; the world has already taken the items. Null when [maxListings] are open. */
    suspend fun list(accountId: Long, seller: String, obj: Int, quantity: Int, price: Int, maxListings: Int): TpListing?

    /** Closes listing [id] of [accountId]; the unsold rest goes to the collection box. */
    suspend fun cancel(accountId: Long, id: Long): TpListing?

    /** Open listings, newest first; [objs] / [seller] filter when not null (seller matched case-insensitively). */
    suspend fun search(objs: Set<Int>?, seller: String?, limit: Int): List<TpListing>

    /** [accountId]'s open listings, oldest first. */
    suspend fun mine(accountId: Long): List<TpListing>

    /**
     * Buys up to [quantity] from listing [id] for [buyer]; the world has already taken quantity * price coins and
     * refunds the difference for whatever was not bought. Coins for the seller go to the seller's collection box.
     */
    suspend fun buy(accountId: Long, buyer: String, id: Long, quantity: Int, price: Int): TpBuyResult

    /** Takes everything waiting in [accountId]'s collection box. */
    suspend fun collect(accountId: Long): TpCollect

    /** Peeks at the collection box without taking it. */
    suspend fun pending(accountId: Long): TpCollect

    /** [accountId]'s last trades, newest first. */
    suspend fun history(accountId: Long): List<TpTrade>
}

// ---------------------------------------------------------------- hiscores, moderation, broadcasts

class HiscoreEntry(val name: String, val level: Int, val xp: Long)

interface HiscoreService {
    suspend fun submit(accountId: Long, name: String, xpTenths: IntArray)

    /** Top [count] for [stat] (-1 = overall). */
    suspend fun top(stat: Int, count: Int): List<HiscoreEntry>
}

/** A moderation reply: [error] for the staff member, else [value]. */
class ModAnswer<T>(val error: String?, val value: T?)

interface ModerationService {
    /**
     * Applies [sanction] to [target] for [durationMs] ([tp.util.ModRecord.PERMANENT] = permanent; ignored by moments
     * and lifts). Writes a `moderation_records` row (commands brief §7), checks the targeting rule against the stored
     * rank (offline targets too), and pushes the change to the target's world. Null on success, else the reason.
     */
    suspend fun apply(staffAccount: Long, target: String, sanction: tp.util.Sanction, durationMs: Long, reason: String): String?

    /** `::history`: [target]'s records, newest first. IP and device scopes only for administrators (§8). */
    suspend fun history(staffAccount: Long, target: String): ModAnswer<List<tp.util.ModRecord>> = ModAnswer("Not available.", null)

    /**
     * Administrator investigations (commands brief §6, §8; audited): [kind] "alts" lists accounts sharing an IP or
     * device with [target]; "ips" lists the IPs [target] logged in from.
     */
    suspend fun investigate(staffAccount: Long, target: String, kind: String): ModAnswer<List<String>> = ModAnswer("Not available.", null)

    /** `::lookup`: [target]'s player card. */
    suspend fun lookup(staffAccount: Long, target: String): ModAnswer<tp.util.PlayerCard> = ModAnswer("Not available.", null)

    /**
     * Commands brief §4: one command execution in the append-only audit log. [actor] is the display name; [detail]
     * carries rank, world, tick, outcome, source and the (redacted where needed) arguments.
     */
    suspend fun audit(actor: String, action: String, target: String, detail: String) {}

    /** Pending kicks for players on [worldId] (stream): account ids. */
    fun subscribeKicks(worldId: Int, sink: (Long) -> Unit): AutoCloseable
}

interface BroadcastService {
    suspend fun broadcast(text: String)

    /** System update in [seconds] (client countdown; worlds save and stop). */
    suspend fun systemUpdate(seconds: Int)

    /** Commands brief §6 `::yell`: a line to every world, tagged with [rank]. */
    suspend fun yell(from: String, rank: Rank, worldId: Int, text: String)

    /** Commands brief §1 hidden staff (`::invisible`): updates the account's presence on every world. */
    suspend fun setInvisible(accountId: Long, invisible: Boolean)

    /** Every event to every world; a new subscriber first gets a [Broadcast.Presence] snapshot. */
    fun subscribe(sink: (Broadcast) -> Unit): AutoCloseable
}

/** One online account as every world sees it (commands brief §5: pushed presence, cached per world). */
class PresenceEntry(val accountId: Long, val name: String, val worldId: Int, val rank: Rank, val invisible: Boolean)

sealed interface Broadcast {
    class Message(val text: String) : Broadcast

    class SystemUpdate(val seconds: Int) : Broadcast

    /** [online] entries are added or replaced, [offline] account ids removed; [snapshot] replaces everything. */
    class Presence(val online: List<PresenceEntry>, val offline: List<Long>, val snapshot: Boolean) : Broadcast

    class Yell(val from: String, val rank: Rank, val worldId: Int, val text: String) : Broadcast
}
