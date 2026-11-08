package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TeleportRequest
import tp.plugin.commands.AuditLevel
import tp.plugin.commands.Category
import tp.plugin.commands.CommandResult
import tp.plugin.commands.SkullRequest
import tp.plugin.commands.Tags
import tp.plugin.commands.UNKNOWN_COMMAND
import tp.plugin.commands.changePassword
import tp.plugin.commands.commandRegistry
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.donator
import tp.plugin.commands.guidePrice
import tp.plugin.commands.homeCoord
import tp.plugin.commands.isMuted
import tp.plugin.commands.onlineEverywhere
import tp.plugin.commands.openUrl
import tp.plugin.commands.perk
import tp.plugin.commands.sanctions
import tp.plugin.commands.setYellOff
import tp.plugin.commands.showList
import tp.plugin.commands.worldId
import tp.plugin.commands.worldPlayerCount
import tp.plugin.commands.yell
import tp.plugin.commands.yellOff
import tp.plugin.savedText
import tp.symbols.Invs
import tp.util.DonatorPerk
import tp.util.DonatorTier
import tp.util.Rank
import tp.util.commands.Impact
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** The player-rank commands of commands brief §6 (the staff suites live with their own modules). */
public class PlayerCommands : ContentPlugin {
    override fun ContentScope.register() {
        info()
        social()
        account()
        convenience()
    }

    private fun ContentScope.info() {
        command("commands", Rank.PLAYER, Category.GENERAL, Impact.INFO) {
            description("Lists the commands you can use here; a search word narrows the list.")
            example("::commands", "::commands yell")
            val search = text("search", optional = true)
            signature(search) { ctx -> listCommands(ctx.player, ctx.opt(search)) }
        }
        command("help", Rank.PLAYER, Category.GENERAL, Impact.INFO) {
            description("Shows a command's usage, aliases and description.")
            example("::help yell")
            val name = text("command")
            signature(name) { ctx -> help(ctx.player, ctx[name]) }
        }
        command("players", Rank.PLAYER, Category.INFO, Impact.INFO) {
            description("Player counts for this world and all worlds, and the list of visible players.")
            example("::players")
            signature { ctx -> players(ctx.player) }
        }
        command("staff", Rank.PLAYER, Category.INFO, Impact.INFO) {
            description("Visible online staff, with rank and world.")
            example("::staff")
            signature { ctx -> staff(ctx.player) }
        }
        command("rules", Rank.PLAYER, Category.INFO, Impact.INFO) {
            description("Shows the rules.")
            example("::rules")
            signature { ctx ->
                val rules = ctx.player.commandsConfig.rules
                ctx.player.showList("Rules", rules.ifEmpty { listOf(NO_RULES) })
                ctx.done()
            }
        }
        command("time", Rank.PLAYER, Category.INFO, Impact.INFO) {
            aliases("uptime", "serverinfo")
            description("Server time, uptime, world and player counts.")
            example("::time")
            signature { ctx -> time(ctx.player) }
        }
        command("price", Rank.PLAYER, Category.INFO, Impact.INFO) {
            description("Grand Exchange guide price of an item.")
            example("::price abyssal whip", "::price 4151")
            val item = item("item")
            signature(item) { ctx ->
                val p = ctx.player
                val obj = p.unnoted(ctx[item].id)
                val price = p.guidePrice(obj)
                ctx.done(
                    if (price == null) {
                        "${p.objName(obj)} has no Grand Exchange guide price."
                    } else {
                        "${p.objName(obj)}: ${"%,d".format(price)} coins (guide price)."
                    },
                )
            }
        }
        for ((key, label) in URLS) {
            command(key, Rank.PLAYER, Category.INFO, Impact.INFO) {
                description("Opens the $label page.")
                example("::$key")
                availableWhen { it.urls[key] != null }
                signature { ctx ->
                    val url = ctx.player.commandsConfig.urls[key] ?: return@signature ctx.fail("No $label page is configured.")
                    ctx.player.openUrl(url)
                    ctx.done("Opening the $label page.")
                }
            }
        }
    }

    private fun ContentScope.social() {
        command("yell", Rank.PLAYER, Category.SOCIAL, Impact.SOCIAL) {
            description("Sends a line to every world with your rank tag; ::yell off|on stops or resumes receiving yells.")
            example("::yell anyone up for a raid?", "::yell off")
            val toggle = choice("off|on", "off", "on")
            signature(toggle) { ctx ->
                val off = ctx[toggle] == "off"
                ctx.player.setYellOff(off)
                ctx.done(if (off) "You will no longer see yells." else "You will see yells again.")
            }
            val message = text("message")
            signature(message) { ctx -> yell(ctx.player, ctx[message]) }
        }
    }

    private fun ContentScope.account() {
        command("changepassword", Rank.PLAYER, Category.GENERAL, Impact.ACCOUNT) {
            description("Changes your password: the current one, then the new one twice.")
            example("::changepassword")
            // §4: sensitive player commands are audited without their arguments (there are none: dialogs carry them).
            audit = AuditLevel.REDACTED
            signature { ctx ->
                passwordDialogs(ctx.player)
                CommandResult.Pending()
            }
        }
    }

    private fun ContentScope.convenience() {
        command("home", Rank.PLAYER, Category.GENERAL, Impact.CONVENIENCE) {
            description("Teleports you home.")
            example("::home")
            signature { ctx ->
                val p = ctx.player
                val home = p.homeCoord
                // The standard teleport checks (Tele Block, Wilderness above level 20, one at a time; teleports.md T7).
                val request = TeleportRequest(p, home)
                p.events.publish(request)
                request.denied?.let { return@signature ctx.fail(it) }
                if (!request.handled) p.teleport(home)
                ctx.done()
            }
        }
        command("skull", Rank.PLAYER, Category.GENERAL, Impact.CONVENIENCE) {
            description("Skulls you, as attacking another player would.")
            example("::skull")
            confirm = true
            signature { ctx ->
                val request = SkullRequest(ctx.player)
                ctx.player.events.publish(request)
                if (request.handled) ctx.done("You are now skulled.") else ctx.fail("Skulls are not available here.")
            }
        }
        command("empty", Rank.PLAYER, Category.GENERAL, Impact.CONVENIENCE) {
            description("Destroys everything in your inventory.")
            example("::empty")
            confirm = true
            signature { ctx ->
                val p = ctx.player
                val inv = p.inv(Invs.INV)
                val ok =
                    p.transaction("::empty") {
                        (0 until inv.size).all { slot -> inv.obj(slot) < 0 || removeAt(Invs.INV, slot, inv.obj(slot), inv.count(slot)) }
                    }
                if (ok) ctx.done("Your inventory is empty.") else ctx.fail("Your inventory could not be emptied.")
            }
        }
    }

    // ---- handlers

    private fun listCommands(p: PlayerRef, search: String?): CommandResult {
        val registry = p.commandRegistry ?: return CommandResult.Failed("No commands here.")
        // Commands brief section 5: the interface when this world has one; the chat list below is the fallback.
        if (p.openCommands(search)) return CommandResult.Done()
        val word = search?.lowercase()
        val defs =
            registry
                .usableBy(p.rank, p.commandsConfig)
                .filter { d -> word == null || word in d.name || d.aliases.any { word in it } || word in d.description.lowercase() }
                .sortedWith(compareBy({ it.category }, { it.name }))
        if (defs.isEmpty()) return CommandResult.Failed("No commands match '${Tags.escape(search.orEmpty())}'.")
        p.showList("Commands", defs.map { "${it.usages.first()} - ${it.description}" })
        return CommandResult.Done()
    }

    private fun help(p: PlayerRef, name: String): CommandResult {
        val registry = p.commandRegistry ?: return CommandResult.Failed(UNKNOWN_COMMAND)
        val def = registry.find(name.removePrefix("::").lowercase())
        // §2: a command the player may not use is answered exactly like one that does not exist.
        if (def == null || !registry.usable(def, p.rank, p.commandsConfig)) return CommandResult.Failed(UNKNOWN_COMMAND)
        p.message("::${def.name}: ${def.description}")
        p.message("Usage: ${def.usages.joinToString(" or ")}")
        if (def.aliases.isNotEmpty()) p.message("Aliases: ${def.aliases.joinToString(", ") { "::$it" }}")
        def.examples.firstOrNull()?.let { p.message("Example: $it") }
        return CommandResult.Done()
    }

    private fun players(p: PlayerRef): CommandResult {
        val online = p.onlineEverywhere()
        val here = online.count { it.worldId == p.worldId }
        p.message("There ${if (here == 1) "is 1 player" else "are $here players"} on this world and ${online.size} on all worlds.")
        val rows = online.sortedWith(compareBy({ it.worldId }, { it.name.lowercase() })).map { "${it.name} (world ${it.worldId})" }
        p.showList("Players", rows)
        return CommandResult.Done()
    }

    private fun staff(p: PlayerRef): CommandResult {
        val staff = p.onlineEverywhere().filter { it.rank.isStaff }.sortedWith(compareBy({ -it.rank.ordinal }, { it.name.lowercase() }))
        if (staff.isEmpty()) return CommandResult.Done("No staff are online.")
        p.showList("Staff online", staff.map { "${it.name} - ${rankName(it.rank)}, world ${it.worldId}" })
        return CommandResult.Done()
    }

    private fun time(p: PlayerRef): CommandResult {
        val now = TIME.format(Instant.ofEpochMilli(System.currentTimeMillis()))
        val uptime = tp.plugin.commands.CmdDuration(p.tick * MS_PER_TICK)
        val all = p.onlineEverywhere()
        val worlds =
            all
                .map { it.worldId }
                .toSet()
                .size
                .coerceAtLeast(1)
        p.message("Server time: $now. Uptime: $uptime (tick ${p.tick}).")
        p.message("World ${p.worldId}: ${p.worldPlayerCount} players. All worlds: ${all.size} players on $worlds world(s).")
        return CommandResult.Done()
    }

    /** §4: per-rank cooldown from the config (none above the listed ranks), mute check, then every world. */
    private fun yell(p: PlayerRef, text: String): CommandResult {
        if (p.isMuted) return CommandResult.Failed("You are muted and can't yell.")
        val s = p.sanctions
        if (s.yellMuted(System.currentTimeMillis())) {
            val reason = if (s.yellMuteReason.isEmpty()) "" else " (${Tags.escape(s.yellMuteReason)})"
            return CommandResult.Failed("You can't yell ${tp.util.Sanctions.describeUntil(s.yellMutedUntil)}$reason.")
        }
        if (p.yellOff) return CommandResult.Failed("Turn yells back on with ::yell on first.")
        // Keyed by exact rank: a rank the config does not list (administrator and above by default) has none.
        val rankSeconds = p.commandsConfig.yellCooldownSeconds[p.rank] ?: 0
        // D-093: a donator's yell timer (perks.tsv "Yell-channel (::yell) timer") when it is shorter.
        val seconds = if (p.donator == DonatorTier.NONE) rankSeconds else minOf(rankSeconds, p.perk(DonatorPerk.YELL_CHANNEL_TIMER))
        val until = p[YELL_UNTIL] ?: 0L
        if (p.tick < until) {
            val left = ((until - p.tick) * MS_PER_TICK + MS_PER_SECOND - 1) / MS_PER_SECOND
            return CommandResult.Failed("You can yell again in $left s.")
        }
        if (seconds > 0) p[YELL_UNTIL] = p.tick + seconds * MS_PER_SECOND / MS_PER_TICK
        val tag = p.savedText(DonatorCommands.YELL_TAG_KEY)?.takeIf { p.perk(DonatorPerk.ABILITY_TO_SET_A_CUSTOM_YELL_TAG) > 0 }
        p.yell(if (tag == null) text else "[$tag] $text")
        return CommandResult.Done()
    }

    /** §6: current password, then the new one twice; the hub checks, throttles and audits (never the text). */
    private fun passwordDialogs(p: PlayerRef) {
        p.stringDialog("Current password:") { current ->
            stringDialog("New password:") { first ->
                stringDialog("New password again:") { second ->
                    if (first != second) {
                        message("The new passwords don't match.")
                    } else {
                        changePassword(current, first) { reason -> message(reason ?: "Your password has been changed.") }
                    }
                }
            }
        }
    }

    private companion object {
        /** One game tick (tick.md). */
        const val MS_PER_TICK = 600L
        const val MS_PER_SECOND = 1000L
        const val NO_RULES = "Be respectful, don't cheat and have fun."

        /** §6 URL commands, by config key. */
        val URLS = listOf("discord" to "Discord", "forums" to "forums", "vote" to "voting", "store" to "store", "hiscores" to "hiscores")

        val YELL_UNTIL = AttrKey<Long>("commands.yell.until")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

        fun rankName(rank: Rank): String = rank.name.lowercase().replaceFirstChar(Char::uppercase)
    }
}
