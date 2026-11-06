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
    fun social(player: Player, action: SocialAction, name: String, text: String) {}

    /**
     * Staff command from [player] (`kick|mute|ban|ipban|unmute|unban <name> [minutes] [reason]`, `broadcast <text>`,
     * `update <seconds>`). Content has already checked the staff level; the hub checks rights again and audits.
     */
    fun staff(player: Player, command: String, args: List<String>) {}
}
