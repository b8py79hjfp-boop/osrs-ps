package tp.plugin.commands

import tp.engine.World
import tp.plugin.PlayerRef
import tp.plugin.PluginContent
import tp.protocol.api.ServerMessage
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.CommandsConfig

// What command handlers reach beyond the usual PlayerRef API: hub presence, yell, hidden mode, passwords and URLs.

/** This world's commands policy (config/server.toml, commands brief §9). */
public val PlayerRef.commandsConfig: CommandsConfig get() = p.world.config.commands

/** This world's id. */
public val PlayerRef.worldId: Int get() = p.world.config.id

/** One online account as command content sees it (hub presence). */
public class OnlinePlayer(public val name: String, public val worldId: Int, public val rank: Rank)

/**
 * Every online account on every world [this] may see (commands brief §1: hidden staff only for equal or higher
 * ranks), from the hub's pushed presence; without a hub, this world's players.
 */
public fun PlayerRef.onlineEverywhere(): List<OnlinePlayer> {
    val world = p.world
    val out = ArrayList<OnlinePlayer>()

    fun visible(name: String, rank: Rank, invisible: Boolean) =
        !invisible || this.rank.atLeast(rank) || name.equals(username, ignoreCase = true)
    if (world.presence.isEmpty()) {
        world.forEachPlayer { if (visible(it.username, it.rank, it.invisible)) out += OnlinePlayer(it.username, world.config.id, it.rank) }
    } else {
        for (a in world.presence.values) if (visible(a.name, a.rank, a.invisible)) out += OnlinePlayer(a.name, a.worldId, a.rank)
    }
    return out
}

/** Players on this world now. */
public val PlayerRef.worldPlayerCount: Int get() = p.world.playerCount

/** This world's home tile (`::home`, the spawn point). */
public val PlayerRef.homeCoord: CoordGrid get() = p.world.config.spawn

/** The Grand Exchange guide price of [obj], or null when it has none. */
public fun PlayerRef.guidePrice(obj: Int): Int? = p.world.guidePrices[obj]

/** The command registry of this world's content (`::commands`, `::help`). */
public val PlayerRef.commandRegistry: CommandRegistry? get() = (p.world.content as? PluginContent)?.registry

/** The reply to a command that does not exist or that the player may not use (commands brief §2). */
public const val UNKNOWN_COMMAND: String = World.UNKNOWN_COMMAND

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

/** Whether a mute stops this player's chat now (public, private, clan and yell; commands brief §6). */
public val PlayerRef.isMuted: Boolean get() = with(p.world) { p.isMuted() }
