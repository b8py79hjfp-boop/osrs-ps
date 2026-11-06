package tp.engine

import tp.engine.entity.Player
import tp.protocol.api.LoginRequest
import tp.protocol.api.SocialAction

/** What the account authority decided for a login: the account and its latest durable save. */
class AccountProfile(
    val accountId: Long,
    val displayName: String,
    /** Staff level for the login response and permission checks (0 player, 1 moderator, 2 admin). */
    val staffLevel: Int,
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
)

/** Grand Exchange requests (hub skeleton). Items and coins move only through the world's item transactions. */
sealed interface ExchangeRequest {
    data object Offers : ExchangeRequest

    /** The world has already removed the coins (buy) or items (sell). */
    class Place(val slot: Int, val buy: Boolean, val obj: Int, val quantity: Int, val price: Int) : ExchangeRequest

    /** Takes what waits in [slot]; the world then adds it. */
    class Collect(val slot: Int) : ExchangeRequest

    /** Removes the offer in [slot]; everything still in it (filled or not) comes back for the world to add. */
    class Abort(val slot: Int) : ExchangeRequest
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
}
