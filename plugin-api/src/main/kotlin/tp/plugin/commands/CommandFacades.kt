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
public val PlayerRef.commandsConfig: CommandsConfig get() = p.world.commands

/** Replaces this world's commands policy until restart (`::command`, `::config`; commands brief §6). */
public fun PlayerRef.setCommandsConfig(config: CommandsConfig) {
    p.world.commands = config
}

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

/** D-096 `::sc`: [text] (escaped here) to staff on every world, through the hub. */
public fun PlayerRef.staffChat(text: String) {
    p.world.accounts?.yell(p, Tags.escape(text), staff = true)
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

/** Mutes, yell-mutes and jail in force (commands brief §7). */
public val PlayerRef.sanctions: tp.util.Sanctions get() = p.sanctions

/** Wall-clock now (sanction expiry is in epoch ms, commands brief §7). */
public fun PlayerRef.nowMillis(): Long = System.currentTimeMillis()

/**
 * One moderation action through the hub (commands brief §6, §7): records, the targeting rule against the stored rank,
 * cross-world and offline. [reply] runs on the world thread with null on success, else the reason.
 */
public fun PlayerRef.moderate(
    sanction: tp.util.Sanction,
    target: String,
    durationMs: Long,
    reason: String,
    reply: PlayerRef.(String?) -> Unit,
) {
    val accounts = p.world.accounts ?: return reply("Moderation needs the account server.")
    accounts.moderate(p, sanction, target, durationMs, reason) { r -> this.reply(r) }
}

/** `::history`: [reply] gets an error, or [target]'s records newest first. */
public fun PlayerRef.moderationHistory(target: String, reply: PlayerRef.(String?, List<tp.util.ModRecord>) -> Unit) {
    val accounts = p.world.accounts ?: return reply("Moderation needs the account server.", emptyList())
    accounts.history(p, target) { e, records -> this.reply(e, records) }
}

/** `::lookup`: [reply] gets an error, or [target]'s card. */
public fun PlayerRef.lookupPlayer(target: String, reply: PlayerRef.(String?, tp.util.PlayerCard?) -> Unit) {
    val accounts = p.world.accounts ?: return reply("Moderation needs the account server.", null)
    accounts.lookup(p, target) { e, card -> this.reply(e, card) }
}

/** `::alts` ("alts") / `::checkip` ("ips"): administrators only, audited by the hub (commands brief §8). */
public fun PlayerRef.investigate(
    target: String,
    kind: String,
    filter: String = "",
    reply: PlayerRef.(String?, List<String>) -> Unit,
) {
    val accounts = p.world.accounts ?: return reply("Moderation needs the account server.", emptyList())
    accounts.investigate(p, target, kind, filter) { e, lines -> this.reply(e, lines) }
}

/** A game broadcast on this world, or on every world through the hub ([everywhere]). */
public fun PlayerRef.broadcast(text: String, everywhere: Boolean) {
    val world = p.world
    val accounts = world.accounts
    if (everywhere && accounts != null) {
        accounts.broadcastAll(p, text)
    } else {
        world.forEachPlayer { it.session.send(ServerMessage.GameMessage(text)) }
    }
}

/** `::setrank` through the hub; [reply] gets null on success, else the reason. */
public fun PlayerRef.setRank(target: String, rank: Rank, reply: PlayerRef.(String?) -> Unit) {
    val accounts = p.world.accounts ?: return reply("Ranks need the account server.")
    accounts.setRank(p, target, rank) { r -> this.reply(r) }
}

/** `::rename` through the hub; [reply] gets null on success, else the reason. */
public fun PlayerRef.rename(target: String, newName: String, reply: PlayerRef.(String?) -> Unit) {
    val accounts = p.world.accounts ?: return reply("Renames need the account server.")
    accounts.rename(p, target, newName) { r -> this.reply(r) }
}

/** `::update` / `::cancelupdate` (owner): every world through the hub, or this world without one. [seconds] < 0 cancels. */
public fun PlayerRef.systemUpdate(seconds: Int) {
    val accounts = p.world.accounts
    if (accounts != null) accounts.systemUpdateAll(p, seconds) else p.world.systemUpdate(seconds)
}

/** `::shutdown` (owner): this world only; the node stops once everyone is logged out and saved. */
public fun PlayerRef.shutdownWorld(seconds: Int) {
    p.world.systemUpdate(seconds)
}

/** `::saveall` (owner): every player on this world now (not final). */
public fun PlayerRef.saveAll() {
    p.world.saveAll(final = false)
}

/** `::lockworld` (owner): only staff may log in while on. */
public var PlayerRef.worldLocked: Boolean
    get() = p.world.staffOnly
    set(value) {
        p.world.staffOnly = value
    }

/** Every player on this world (owner tools such as `::kickall`). */
public fun PlayerRef.worldPlayers(): List<PlayerRef> {
    val out = ArrayList<PlayerRef>()
    p.world.forEachPlayer { out += tp.plugin.refOf(it) }
    return out
}

/**
 * Whether [this] may use a developer tool that creates value (spawn menu, quest stages, diary tasks): a developer, on
 * a world whose policy allows ECONOMY (commands brief §3, §6). Checked on every click, not only when it opens (§8).
 */
public val PlayerRef.economyToolsAllowed: Boolean
    get() = rank.atLeast(Rank.DEVELOPER) && commandsConfig.allows(tp.util.commands.Impact.ECONOMY)

/**
 * Runs [line] (no `::`) through the same pipeline as a typed command (commands brief §4, §8: the interface carries no
 * rights of its own), as [source]. Null when this world has no command pipeline.
 */
public fun PlayerRef.runCommandLine(line: String, source: Source = Source.INTERFACE): CommandDispatcher.Outcome? =
    (p.world.content as? PluginContent)?.dispatcher?.dispatch(this, line, source)

/** The viewer's last ten commands, newest first (the interface's Recent view, §5). */
public fun PlayerRef.recentCommands(): List<String> = (p.world.content as? PluginContent)?.dispatcher?.recentOf(this).orEmpty()

/** This player's donator tier (D-093). */
public val PlayerRef.donator: tp.util.DonatorTier get() = p.donator

/**
 * The donator [perk]'s value for this player in the perk's unit (tp.util.DonatorPerk). Gameplay boosts are 0 unless
 * the world enables `donator_boosts` (off on live 1:1 worlds by default: D-093); cosmetic perks always apply.
 */
public fun PlayerRef.perk(perk: tp.util.DonatorPerk): Int {
    if (perk !in COSMETIC_PERKS && !commandsConfig.donatorBoosts) return 0
    return perk.value(p.donator)
}

/** Perks that change no gameplay: always on (D-093). */
private val COSMETIC_PERKS =
    setOf(
        tp.util.DonatorPerk.EXCLUSIVE_CHAT_CROWN,
        tp.util.DonatorPerk.YELL_CHANNEL_TIMER,
        tp.util.DonatorPerk.ABILITY_TO_SET_A_CUSTOM_YELL_TAG,
        tp.util.DonatorPerk.EXTRA_BANK_SPACE,
    )

/** `::donated` through the hub; [reply] gets an error, or the target's new tier. */
public fun PlayerRef.recordDonation(target: String, cents: Long, note: String, reply: PlayerRef.(String?, tp.util.DonatorTier?) -> Unit) {
    val accounts = p.world.accounts ?: return reply("Donations need the account server.", null)
    accounts.recordDonation(p, target, cents, note) { e, tier -> this.reply(e, tier) }
}
