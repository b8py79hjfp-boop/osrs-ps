package tp.engine

import tp.engine.entity.Player
import tp.protocol.api.LoginRequest
import tp.protocol.api.SocialAction

/** What the account authority decided for a login: the account and its latest durable save. */
class AccountProfile(
    val accountId: Long,
    val displayName: String,
    /** Staff rank (docs/plans/commands-brief.md §1): the login response's staff fields and every permission check. */
    val rank: tp.util.Rank,
    /** Versioned save JSON (engine `PlayerSave`), null for a new character. */
    val save: String?,
    /** Muted until (epoch ms); 0 = not muted, negative = permanently. */
    val mutedUntil: Long = 0,
)

/** A Grand Exchange offer as the hub reports it ([collectCoins]/[collectItems] wait to be collected). */
class ExchangeOffer(
    val slot: Int,
    val buy: Boolean,
    val obj: Int,
    val quantity: Int,
    val price: Int,
    val completed: Int,
    val collectCoins: Long,
    val collectItems: Int,
    /** Coins exchanged so far (spent or received). */
    val gold: Long = 0,
    val aborted: Boolean = false,
    /** Sell tax paid so far. */
    val tax: Long = 0,
)

/** Trading post (docs/mechanics/trading-post.md) requests a world sends to the hub for a player. */
sealed interface TradingPostRequest {
    class Listing(val obj: Int, val quantity: Int, val price: Int, val maxListings: Int) : TradingPostRequest

    class Cancel(val id: Long) : TradingPostRequest

    /** [objs] / [seller] null = any. */
    class Search(val objs: Set<Int>?, val seller: String?, val limit: Int) : TradingPostRequest

    data object Mine : TradingPostRequest

    class Buy(val id: Long, val quantity: Int, val price: Int) : TradingPostRequest

    data object Collect : TradingPostRequest

    data object Pending : TradingPostRequest

    data object History : TradingPostRequest
}

class TpListingRow(
    val id: Long,
    val accountId: Long,
    val seller: String,
    val obj: Int,
    val quantity: Int,
    val sold: Int,
    val price: Int,
    val listedAtMs: Long,
)

class TpTradeRow(val obj: Int, val quantity: Int, val price: Int, val bought: Boolean, val other: String, val atMs: Long)

/** One reply: whichever parts the request fills (listings, a buy count / error, a collection box, trades). */
class TradingPostReply(
    val listings: List<TpListingRow> = emptyList(),
    val bought: Int = 0,
    val error: String? = null,
    val coins: Long = 0,
    val items: Map<Int, Int> = emptyMap(),
    val trades: List<TpTradeRow> = emptyList(),
)

/** Grand Exchange requests (hub skeleton). Items and coins move only through the world's item transactions. */
sealed interface ExchangeRequest {
    data object Offers : ExchangeRequest

    /** The world has already removed the coins (buy) or items (sell). */
    class Place(val slot: Int, val buy: Boolean, val obj: Int, val quantity: Int, val price: Int, val taxRate: Int = 0) : ExchangeRequest

    /** Takes what waits in [slot]; the world then adds it. */
    class Collect(val slot: Int, val items: Boolean = true, val coins: Boolean = true) : ExchangeRequest

    /** Aborts the offer in [slot]: its unfilled part moves to the collect box. */
    class Abort(val slot: Int) : ExchangeRequest

    /** The account's trade history, newest first (completed = amount traded, gold = coins exchanged). */
    data object History : ExchangeRequest
}

/** A friends chat channel's settings as content sees them (services `ChannelSettings`; null [name] = disabled). */
class ChannelConfig(val name: String?, val enterRank: Int, val talkRank: Int, val kickRank: Int)

/** Where a world is reached (hub world directory), for world hops. */
class WorldAddress(val host: String, val flags: Int)

/**
 * The world's link to the account authority (the hub, brief §7). Without one, logins are admitted by username only
 * (dev, tests). With one:
 * - [authorize] is called on a network thread; it decides off the world thread and answers with
 *   [World.admitAuthorized] or `request.reject`.
 * - [saved] gets a save snapshot built on the world thread: [final] at logout (must become durable before the account
 *   may log in again: the hub enforces it), otherwise an autosave. Implementations write it off-thread.
 */
interface AccountBridge {
    fun authorize(request: LoginRequest, world: World)

    /** G7: [player] reported [name] for [rule] ([mute]: a staff member asked for a 48-hour mute). Default: logged only. */
    fun reported(player: Player, name: String, rule: Int, mute: Boolean) {}

    fun saved(player: Player, save: String, final: Boolean)

    /** The hub accepted the login but the world refused it (world full, ...): release the account. */
    fun refused(profile: AccountProfile) {}

    /** [player] is in the world: load friends/ignores (answers arrive through [World.execute]). World thread. */
    fun loggedIn(player: Player) {}

    /** A validated friends/ignore/PM action from [player]. World thread; the hub call runs elsewhere. */
    fun social(player: Player, action: SocialAction, name: String, text: String, rank: Int = 0) {}

    /**
     * Staff command from [player] (`kick|mute|ban|ipban|unmute|unban <name> [minutes] [reason]`, `broadcast <text>`,
     * `update <seconds>`). Content has already checked the staff level; the hub checks rights again and audits.
     */
    fun staff(player: Player, command: String, args: List<String>) {}

    /** Commands brief §4: one command execution for the hub's append-only audit log (off the world thread). */
    fun audit(actor: String, action: String, target: String, detail: String) {}

    /** `::yell` (commands brief §6): [text] (already escaped) to every world. */
    fun yell(player: Player, text: String) {}

    /** `::invisible` (commands brief §1): the account's hidden flag on every world's presence. */
    fun setInvisible(player: Player, invisible: Boolean) {}

    /** `::changepassword` (commands brief §8); [reply] runs on the world thread with null on success, else the reason. */
    fun changePassword(player: Player, current: String, new: String, reply: (String?) -> Unit) {
        reply("Passwords can't be changed without an account server.")
    }

    /**
     * Reads [player]'s own friends chat settings, or stores [update] first if given; [reply] runs on the world thread
     * with the current settings (null: hub unavailable).
     */
    fun channelConfig(player: Player, update: ChannelConfig?, reply: (Player, ChannelConfig?) -> Unit) {
        reply(player, null)
    }

    /**
     * Grand Exchange call; [reply] runs on the world thread with the affected offers (null: the hub failed), only if
     * the player is still online.
     */
    fun exchange(player: Player, request: ExchangeRequest, reply: (Player, List<ExchangeOffer>?) -> Unit) {
        reply(player, null)
    }

    /** Trading post call; [reply] runs on the world thread (null = no hub / failure). */
    fun tradingPost(player: Player, request: TradingPostRequest, reply: (Player, TradingPostReply?) -> Unit) {
        reply(player, null)
    }

    /**
     * Clan call (docs/mechanics/clans.md); [reply] runs on the world thread with null on success or the message to
     * show, only if the player is still online. The clan channel itself arrives through the social stream.
     */
    fun clan(player: Player, request: ClanRequest, reply: (Player, String?) -> Unit) {
        reply(player, NO_HUB)
    }

    companion object {
        const val NO_HUB = "Clans are unavailable right now."
    }
}

/** A clan action for the hub (docs/mechanics/clans.md CL1-CL8). */
sealed interface ClanRequest {
    data class Found(val name: String) : ClanRequest

    data class Invite(val name: String) : ClanRequest

    data object Accept : ClanRequest

    data object Leave : ClanRequest

    data class SetRank(val name: String, val rank: Int) : ClanRequest

    data class Kick(val name: String, val ban: Boolean) : ClanRequest

    data class SetRules(val talkRank: Int, val kickRank: Int, val allowGuests: Boolean, val recruitRank: Int) : ClanRequest

    data class JoinGuest(val clanName: String) : ClanRequest

    data object LeaveGuest : ClanRequest

    data class Message(val guest: Boolean, val text: String) : ClanRequest
}
