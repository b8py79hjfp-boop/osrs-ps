package tp.content.staff

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.commands.Category
import tp.plugin.commands.CommandResult
import tp.plugin.commands.RankChanged
import tp.plugin.commands.Tags
import tp.plugin.commands.commandRegistry
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.investigate
import tp.plugin.commands.rename
import tp.plugin.commands.saveAll
import tp.plugin.commands.setCommandsConfig
import tp.plugin.commands.setRank
import tp.plugin.commands.showList
import tp.plugin.commands.shutdownWorld
import tp.plugin.commands.systemUpdate
import tp.plugin.commands.worldLocked
import tp.plugin.commands.worldPlayers
import tp.util.Rank
import tp.util.commands.CommandOverride
import tp.util.commands.Impact

/** The owner commands of commands brief §6: ranks, restarts, world locks, runtime policy, the audit log and renames. */
public class OwnerCommands : ContentPlugin {
    override fun ContentScope.register() {
        ranks()
        lifecycle()
        policy()
        records()
        // §8: a demotion closes staff views at once (the list view and any staff interface listen for this).
        events.on<RankChanged> { e ->
            val p = e.player
            if (p.rank.ordinal < e.before.ordinal) p.closeModal()
            p.message("Your rank is now ${rankName(p.rank)}.")
        }
    }

    private fun ContentScope.ranks() {
        owner("setrank", "Sets a player's rank (owners: up to administrator).", "::setrank Big_Bob moderator") {
            target()
            crossWorld = true
            val who = player("player", allowOffline = true)
            val rank = rank("rank")
            signature(who, rank) { ctx ->
                val name = ctx[who].name
                ctx.player.setRank(name, ctx[rank]) { error ->
                    message(error ?: "${Tags.escape(name)} is now ${rankName(ctx[rank])}.")
                }
                CommandResult.Pending()
            }
        }
        owner("rename", "Changes a player's display name, kept in their history; their login name stays.", "::rename Big_Bob Bob") {
            target()
            crossWorld = true
            val who = player("player", allowOffline = true)
            val name = text("new name")
            signature(who, name) { ctx ->
                val old = ctx[who].name
                ctx.player.rename(old, ctx[name]) { error ->
                    message(error ?: "${Tags.escape(old)} is now ${Tags.escape(ctx[name])}.")
                }
                CommandResult.Pending()
            }
        }
    }

    private fun ContentScope.lifecycle() {
        owner("update", "Starts the system-update timer on every world, then a graceful restart.", "::update 300 New content") {
            val seconds = int("seconds", min = 0, max = MAX_UPDATE_SECONDS.toLong())
            val message = text("message", optional = true)
            signature(seconds, message) { ctx ->
                ctx.player.systemUpdate(ctx[seconds])
                ctx.opt(message)?.let { m -> ctx.player.worldPlayers().forEach { it.message("Update: ${Tags.escape(m)}") } }
                ctx.done("System update in ${ctx[seconds]} s.")
            }
        }
        owner("cancelupdate", "Cancels the system-update timer on every world.", "::cancelupdate") {
            signature { ctx ->
                ctx.player.systemUpdate(-1)
                ctx.done("System update cancelled.")
            }
        }
        owner("shutdown", "Stops this world gracefully, saving everyone first.", "::shutdown 60") {
            confirm = true
            remote = false
            val seconds = int("seconds", optional = true, min = 0, max = MAX_UPDATE_SECONDS.toLong())
            signature(seconds) { ctx ->
                ctx.player.shutdownWorld(ctx.opt(seconds) ?: 0)
                ctx.done("Shutting down.")
            }
        }
        owner("saveall", "Saves every player on this world now.", "::saveall") {
            signature { ctx ->
                ctx.player.saveAll()
                ctx.done("Saving ${ctx.player.worldPlayers().size} players.")
            }
        }
        owner("kickall", "Kicks everyone on this world except staff.", "::kickall restarting a service") {
            confirm = true
            val reason = text("reason", optional = true)
            signature(reason) { ctx ->
                val kicked = ctx.player.worldPlayers().filter { !it.rank.isStaff }
                for (p in kicked) {
                    ctx.opt(reason)?.let { p.message("You have been logged out: ${Tags.escape(it)}") }
                    p.logout()
                }
                ctx.done("Kicked ${kicked.size} players.")
            }
        }
        owner("lockworld", "Staff-only logins on this world, for maintenance.", "::lockworld on") {
            val on = bool("on|off")
            signature(on) { ctx ->
                ctx.player.worldLocked = ctx[on]
                ctx.done(if (ctx[on]) "World locked: staff-only logins." else "World unlocked.")
            }
        }
    }

    private fun ContentScope.policy() {
        owner("command", "Enables or disables a command on this world until restart (never ECONOMY on).", "::command disable yell") {
            val action = choice("enable|disable", "enable", "disable")
            val name = text("command")
            signature(action, name) { ctx -> toggle(ctx.player, ctx[action] == "enable", ctx[name].removePrefix("::").lowercase()) }
        }
        owner("config", "Shows or changes a runtime-safe config key on this world until restart.", "::config rate_limit_per_minute 40") {
            val key = choice("key", *SAFE_KEYS.toTypedArray())
            val value = int("value", optional = true, min = 0, max = MAX_CONFIG_VALUE.toLong())
            signature(key, value) { ctx -> config(ctx.player, ctx[key], ctx.opt(value)) }
        }
    }

    private fun ContentScope.records() {
        owner("audit", "Shows the audit log: a player's or staff member's rows, optionally filtered.", "::audit Big_Bob mute") {
            val who = text("player|staff filter", optional = true)
            signature(who) { ctx ->
                val words =
                    ctx
                        .opt(who)
                        .orEmpty()
                        .trim()
                        .split(' ')
                        .filter { it.isNotEmpty() }
                val name = words.firstOrNull().orEmpty().replace('_', ' ')
                val filter = words.drop(1).joinToString(" ")
                ctx.player.investigate(name, "audit", filter) { error, rows ->
                    // §8: audit details hold IP addresses (logins), so the log is never shown in chat.
                    val shown = rows.map(Tags::escape).ifEmpty { listOf("No rows.") }
                    if (error != null) message(error) else showList("Audit log", shown, private = true)
                }
                CommandResult.Pending()
            }
        }
    }

    private fun toggle(p: tp.plugin.PlayerRef, enable: Boolean, name: String): CommandResult {
        val def = p.commandRegistry?.find(name) ?: return CommandResult.Failed("There is no command ::$name.")
        val c = p.commandsConfig
        if (def.name in PROTECTED) return CommandResult.Failed("::${def.name} can't be disabled.")
        val updated =
            if (enable) {
                // §3: ECONOMY only ever comes from the file (and a restart).
                if (def.impact == Impact.ECONOMY && !c.allows(Impact.ECONOMY)) {
                    return CommandResult.Failed("::${def.name} is ECONOMY: only the world's config file can allow it.")
                }
                c.copy(runtimeEnabled = c.runtimeEnabled + def.name, overrides = c.overrides - def.name)
            } else {
                c.copy(runtimeEnabled = c.runtimeEnabled - def.name, overrides = c.overrides + (def.name to CommandOverride.Disabled))
            }
        p.setCommandsConfig(updated)
        return CommandResult.Done("::${def.name} ${if (enable) "enabled" else "disabled"} on this world until restart.")
    }

    private fun config(p: tp.plugin.PlayerRef, key: String, value: Int?): CommandResult {
        val c = p.commandsConfig
        val current =
            when (key) {
                "rate_limit_per_second" -> c.rateLimitPerSecond
                "rate_limit_per_minute" -> c.rateLimitPerMinute
                "confirm_timeout_seconds" -> c.confirmTimeoutSeconds
                "yell_cooldown_player" -> c.yellCooldownSeconds[Rank.PLAYER] ?: 0
                else -> c.yellCooldownSeconds[Rank.MODERATOR] ?: 0
            }
        if (value == null) return CommandResult.Done("$key = $current")
        if (key.startsWith("rate_limit") && value < 1) return CommandResult.Failed("$key must be at least 1.")
        val updated =
            when (key) {
                "rate_limit_per_second" -> c.copy(rateLimitPerSecond = value)
                "rate_limit_per_minute" -> c.copy(rateLimitPerMinute = value)
                "confirm_timeout_seconds" -> c.copy(confirmTimeoutSeconds = value)
                "yell_cooldown_player" -> c.copy(yellCooldownSeconds = c.yellCooldownSeconds + (Rank.PLAYER to value))
                else -> c.copy(yellCooldownSeconds = c.yellCooldownSeconds + (Rank.MODERATOR to value))
            }
        p.setCommandsConfig(updated)
        return CommandResult.Done("$key: $current -> $value (until restart).")
    }

    private fun ContentScope.owner(
        name: String,
        description: String,
        example: String,
        block: tp.plugin.commands.CommandBuilder.() -> Unit,
    ) {
        command(name, Rank.OWNER, Category.OPERATIONS, Impact.OPERATIONS) {
            description(description)
            example(example)
            // None of these needs a body; ::shutdown opts out (the panel stops services through its own controls).
            remote = true
            block()
        }
    }

    private companion object {
        /** A system-update timer of at most one hour. */
        const val MAX_UPDATE_SECONDS = 3600

        /** Upper bound for the numeric `::config` keys. */
        const val MAX_CONFIG_VALUE = 3600

        /** `::config` keys that are safe to change while the world runs (rate limits, timeouts, cooldowns). */
        val SAFE_KEYS =
            listOf(
                "rate_limit_per_second",
                "rate_limit_per_minute",
                "confirm_timeout_seconds",
                "yell_cooldown_player",
                "yell_cooldown_moderator",
            )

        /** Commands the owner must keep, or they could lock themselves out of the policy tools. */
        val PROTECTED = setOf("command", "confirm", "commands", "help")

        fun rankName(rank: Rank): String = rank.name.lowercase().replaceFirstChar(Char::uppercase)
    }
}
