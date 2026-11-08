package tp.content.staff

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.choice
import tp.plugin.commands.Category
import tp.plugin.commands.CommandBuilder
import tp.plugin.commands.CommandContext
import tp.plugin.commands.CommandResult
import tp.plugin.commands.PlayerTarget
import tp.plugin.commands.Tags
import tp.plugin.commands.lookupPlayer
import tp.plugin.commands.moderate
import tp.plugin.commands.moderationHistory
import tp.plugin.commands.onlineEverywhere
import tp.plugin.commands.showList
import tp.plugin.commands.worldId
import tp.symbols.Invs
import tp.util.CoordGrid
import tp.util.ModRecord
import tp.util.Rank
import tp.util.Sanction
import tp.util.Sanctions
import tp.util.commands.Impact

/**
 * The moderator commands of commands brief §6. Every sanction goes through the hub (records, the targeting rule against
 * the stored rank, cross-world and offline; §7); this module only parses and replies.
 */
public class ModeratorCommands : ContentPlugin {
    override fun ContentScope.register() {
        sanctions()
        records()
        movement()
        views()
    }

    private fun ContentScope.sanctions() {
        timed(
            "mute",
            "muted",
            Sanction.MUTE,
            "Mutes public, private, clan and yell chat. Works cross-world and offline.",
            "::mute Big_Bob 1h spam",
        )
        lift("unmute", "unmuted", Sanction.UNMUTE, "Lifts a mute.")
        timed("yellmute", "yell-muted", Sanction.YELL_MUTE, "Mutes yell only.", "::yellmute Big_Bob 1d yell spam")
        lift("unyellmute", "yell-unmuted", Sanction.YELL_UNMUTE, "Lifts a yell mute.")
        timed(
            "jail",
            "jailed",
            Sanction.JAIL,
            "Sends the player to the jail with no way out until it expires; applied at their next login if offline.",
            "::jail Big_Bob 2h scamming",
        )
        lift("unjail", "released", Sanction.UNJAIL, "Releases a jailed player.")
        // Administrator (commands brief §6): bans are cluster-wide; the hub kicks the account wherever it is.
        timed("ban", "banned", Sanction.BAN, "Account ban, cluster-wide.", "::ban Big_Bob 7d real-world trading")
        lift("unban", "unbanned", Sanction.UNBAN, "Lifts an account ban.")
        timed("ipban", "IP-banned", Sanction.IP_BAN, "Bans the player's last IP address.", "::ipban Big_Bob perm botting")
        lift("unipban", "IP-unbanned", Sanction.UN_IP_BAN, "Lifts an IP ban.")
        mod("kick", "Logs the player out. Works cross-world.", "::kick Big_Bob afk in a minigame") {
            val who = player("player", allowOffline = true)
            val reason = text("reason", optional = true)
            signature(who, reason) { ctx -> act(ctx, Sanction.KICK, ctx[who], 0, ctx.opt(reason).orEmpty(), "kicked") }
        }
        mod("warn", "Shows the player a warning and records it.", "::warn Big_Bob keep chat civil") {
            val who = player("player", allowOffline = true)
            val reason = text("reason")
            signature(who, reason) { ctx -> act(ctx, Sanction.WARN, ctx[who], 0, ctx[reason], "warned") }
        }
    }

    private fun ContentScope.records() {
        mod("note", "Adds a private staff note to the player's record.", "::note Big_Bob asked about rule 3") {
            val who = player("player", allowOffline = true)
            val text = text("text")
            signature(who, text) { ctx -> act(ctx, Sanction.NOTE, ctx[who], 0, ctx[text], "noted") }
        }
        mod("history", "Shows the player's moderation record.", "::history Big_Bob") {
            val who = player("player", allowOffline = true)
            signature(who) { ctx ->
                val name = ctx[who].name
                ctx.player.moderationHistory(name) { error, records ->
                    if (error !=
                        null
                    ) {
                        message(error)
                    } else {
                        showList("History of $name", records.map(::row).ifEmpty { listOf("No records.") })
                    }
                }
                CommandResult.Pending()
            }
        }
        mod("lookup", "Shows the player's card: rank, world and sanctions in force.", "::lookup Big_Bob") {
            val who = player("player", allowOffline = true)
            signature(who) { ctx ->
                ctx.player.lookupPlayer(ctx[who].name) { error, card ->
                    if (error != null || card == null) return@lookupPlayer message(error ?: "No such account.")
                    val now = System.currentTimeMillis()
                    val s = card.sanctions
                    val rows =
                        buildList {
                            add("Rank: ${rankName(card.rank)}. ${if (card.world == 0) "Offline." else "Online on world ${card.world}."}")
                            add("Muted: ${if (s.muted(now)) Sanctions.describeUntil(s.mutedUntil) else "no"}.")
                            add("Yell-muted: ${if (s.yellMuted(now)) Sanctions.describeUntil(s.yellMutedUntil) else "no"}.")
                            add("Jailed: ${if (s.jailed(now)) Sanctions.describeUntil(s.jailedUntil) else "no"}.")
                            add(
                                "Banned: ${if (Sanctions.inForce(
                                        card.bannedUntil,
                                        now,
                                    )
                                ) {
                                    Sanctions.describeUntil(card.bannedUntil)
                                } else {
                                    "no"
                                }}.",
                            )
                            add("Records: ${card.records} (::history ${card.name}).")
                        }
                    showList(card.name, rows)
                }
                CommandResult.Pending()
            }
        }
    }

    private fun ContentScope.movement() {
        mod("teleto", "Teleports you to the player; on another world, offers a hop first.", "::teleto Big_Bob", Impact.MODERATION) {
            aliases("goto")
            val who = player("player", allowOffline = true)
            signature(who) { ctx -> teleTo(ctx.player, ctx[who]) }
        }
        mod("teletome", "Brings the player to you (same world); ::return sends them back.", "::teletome Big_Bob", Impact.MODERATION) {
            aliases("bring")
            val who = player("player")
            signature(who) { ctx ->
                val target = online(ctx[who]) ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                if (target == ctx.player) return@signature ctx.fail("You are already here.")
                if (target[RETURN_TILE] == null) target[RETURN_TILE] = target.coord
                target.teleport(ctx.player.coord)
                target.message("You have been moved by ${Tags.escape(ctx.player.username)}.")
                ctx.done("Brought ${target.username} to you.")
            }
        }
        mod("return", "Sends a player you brought back to where they were.", "::return Big_Bob", Impact.MODERATION) {
            val who = player("player")
            signature(who) { ctx ->
                val target = online(ctx[who]) ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                val back = target[RETURN_TILE] ?: return@signature ctx.fail("${target.username} has nowhere to return to.")
                target[RETURN_TILE] = null
                target.teleport(back)
                ctx.done("Returned ${target.username}.")
            }
        }
        command("pos", Rank.MODERATOR, Category.MODERATION, Impact.INFO) {
            aliases("mypos", "coords")
            description("Your tile, map square (region) and zone.")
            example("::pos")
            signature { ctx ->
                val c = ctx.player.coord
                val region = (c.x shr SQUARE_SHIFT shl REGION_SHIFT) or (c.z shr SQUARE_SHIFT)
                ctx.done(
                    "Tile ${c.x}, ${c.z}, ${c.level} | region $region (${c.x shr SQUARE_SHIFT}, ${c.z shr SQUARE_SHIFT}) " +
                        "local ${c.x and SQUARE_MASK}, ${c.z and SQUARE_MASK} | zone ${c.x shr ZONE_SHIFT}, ${c.z shr ZONE_SHIFT}",
                )
            }
        }
    }

    private fun ContentScope.views() {
        for ((name, inv, label) in listOf(
            Triple("checkinv", Invs.INV, "inventory"),
            Triple("checkequip", Invs.WORN, "equipment"),
            Triple("checkbank", Invs.BANK, "bank"),
        )) {
            mod(name, "Read-only view of an online player's $label.", "::$name Big_Bob", Impact.MODERATION) {
                val who = player("player")
                signature(who) { ctx ->
                    val target = online(ctx[who]) ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                    val view = target.inv(inv)
                    val rows =
                        (0 until view.size)
                            .filter { view.obj(it) >= 0 }
                            .map { slot -> "${slot + 1}: ${target.objName(view.obj(slot))} x${"%,d".format(view.count(slot))}" }
                    ctx.player.showList("${target.username}'s $label", rows.ifEmpty { listOf("Empty.") })
                    ctx.done()
                }
            }
        }
    }

    // ---- DSL helpers

    private fun ContentScope.mod(
        name: String,
        description: String,
        example: String,
        impact: Impact = Impact.MODERATION,
        rank: Rank = Rank.MODERATOR,
        block: CommandBuilder.() -> Unit,
    ) {
        val category = if (rank.atLeast(Rank.ADMINISTRATOR)) Category.ADMINISTRATION else Category.MODERATION
        command(name, rank, category, impact) {
            description(description)
            example(example)
            target()
            crossWorld = true
            block()
        }
    }

    private fun ContentScope.timed(name: String, done: String, sanction: Sanction, description: String, example: String) {
        mod(name, description, example, rank = sanction.minRank) {
            val who = player("player", allowOffline = true)
            val duration = duration("duration")
            val reason = text("reason")
            signature(who, duration, reason) { ctx ->
                act(ctx, sanction, ctx[who], ctx[duration].millis, ctx[reason], "$done for ${ctx[duration]}")
            }
        }
    }

    private fun ContentScope.lift(name: String, done: String, sanction: Sanction, description: String) {
        mod(name, description, "::$name Big_Bob", rank = sanction.minRank) {
            val who = player("player", allowOffline = true)
            val reason = text("reason", optional = true)
            signature(who, reason) { ctx -> act(ctx, sanction, ctx[who], 0, ctx.opt(reason).orEmpty(), done) }
        }
    }

    /** One hub action; the reply comes back through the pre-tick inbox (commands brief §4: Pending). */
    private fun act(
        ctx: CommandContext,
        sanction: Sanction,
        who: PlayerTarget,
        durationMs: Long,
        reason: String,
        done: String,
    ): CommandResult {
        val name = who.name
        ctx.player.moderate(sanction, name, durationMs, reason) { error -> message(error ?: "${Tags.escape(name)}: $done.") }
        return CommandResult.Pending()
    }

    private fun teleTo(p: PlayerRef, who: PlayerTarget): CommandResult {
        online(who)?.let { target ->
            if (target == p) return CommandResult.Failed("You are already there.")
            p.teleport(target.coord)
            return CommandResult.Done("Teleported to ${target.username}.")
        }
        val elsewhere = p.onlineEverywhere().firstOrNull { it.name.equals(who.name, ignoreCase = true) && it.worldId != p.worldId }
        elsewhere ?: return CommandResult.Failed("${Tags.escape(who.name)} is not online.")
        val world = elsewhere.worldId
        // Commands brief §6: another world offers a hop first; ::teleto again there to finish.
        p.weakQueue(0) {
            if (choice("${elsewhere.name} is on world $world. Hop there?", "Yes", "No") == 1 && !player.hop(world)) {
                player.message("World $world is not available.")
            }
        }
        return CommandResult.Done()
    }

    private fun online(who: PlayerTarget): PlayerRef? = (who as? PlayerTarget.Online)?.player

    private fun row(r: ModRecord): String {
        val now = System.currentTimeMillis()
        val reason = if (r.reason.isEmpty()) "" else ": ${Tags.escape(r.reason)}"
        val state =
            when {
                r.liftedBy.isNotEmpty() -> " (lifted by ${Tags.escape(r.liftedBy)})"
                !r.type.lasting -> ""
                r.active(now) -> " (${Sanctions.describeUntil(r.expires)})"
                else -> " (expired)"
            }
        // §8: IP addresses and device ids never appear in chat, so a record's scope is not shown here.
        return "${DATE.format(java.time.Instant.ofEpochMilli(r.start))} ${r.type.name.lowercase().replace('_', ' ')} " +
            "by ${Tags.escape(r.actor)}$reason$state"
    }

    private companion object {
        val RETURN_TILE = AttrKey<CoordGrid>("staff.return_tile")
        val DATE: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm")
                .withZone(java.time.ZoneOffset.UTC)

        /** Map squares are 64 tiles; region id = square x << 8 | square z (OSRS region numbering). */
        const val SQUARE_SHIFT = 6
        const val REGION_SHIFT = 8
        const val SQUARE_MASK = 63

        /** Zones are 8x8 tiles. */
        const val ZONE_SHIFT = 3

        fun rankName(rank: Rank): String = rank.name.lowercase().replaceFirstChar(Char::uppercase)
    }
}
