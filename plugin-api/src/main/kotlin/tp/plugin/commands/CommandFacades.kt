package tp.plugin.commands

import tp.engine.OnlineAccount
import tp.engine.commands.CommandsConfig
import tp.plugin.PlayerRef
import tp.protocol.api.ServerMessage

// What command handlers reach beyond the usual PlayerRef API: hub presence, yell, hidden mode, passwords and URLs.

/** This world's commands policy (config/server.toml, commands brief §9). */
public val PlayerRef.commandsConfig: CommandsConfig get() = p.world.config.commands

/** This world's id. */
public val PlayerRef.worldId: Int get() = p.world.config.id

/**
 * Every online account on every world [this] may see (commands brief §1: hidden staff only for equal or higher
 * ranks), from the hub's pushed presence; without a hub, this world's players.
 */
public fun PlayerRef.onlineEverywhere(): List<OnlineAccount> {
    val world = p.world
    val all =
        if (world.presence.isEmpty()) {
            val out = ArrayList<OnlineAccount>()
            world.forEachPlayer { out += OnlineAccount(it.accountId, it.username, world.config.id, it.rank, it.invisible) }
            out
        } else {
            world.presence.values.toList()
        }
    return all.filter { !it.invisible || rank.atLeast(it.rank) || it.name.equals(username, ignoreCase = true) }
}

/** `::yell`: [text] to every world (escaped here: §8). */
public fun PlayerRef.yell(text: String) {
    p.world.accounts?.yell(p, Tags.escape(text))
}

/** Hidden mode on or off, here and in every world's presence (§1). */
public fun PlayerRef.setInvisible(invisible: Boolean) {
    p.invisible = invisible
    p.world.accounts?.setInvisible(p, invisible)
}

public val PlayerRef.isInvisible: Boolean get() = p.invisible

/** `::changepassword` through the hub; [reply] gets null on success, else the reason (world thread). */
public fun PlayerRef.changePassword(current: String, new: String, reply: PlayerRef.(String?) -> Unit) {
    val accounts = p.world.accounts ?: return reply("Passwords can't be changed without an account server.")
    accounts.changePassword(p, current, new) { reason -> this.reply(reason) }
}

/** Opens [url] in the player's browser (URL_OPEN). */
public fun PlayerRef.openUrl(url: String) {
    p.session.send(ServerMessage.UrlOpen(url))
}

/** Whether this player turned yells off (`::yell off`). */
public val PlayerRef.yellOff: Boolean get() = (saved(YELL_OFF_KEY) ?: 0L) != 0L

public fun PlayerRef.setYellOff(off: Boolean) {
    setSaved(YELL_OFF_KEY, if (off) 1L else null)
}

/** Saved flag key: 1 = the player stopped receiving yells (read by the server's yell delivery). */
public const val YELL_OFF_KEY: String = "commands.yell.off"
