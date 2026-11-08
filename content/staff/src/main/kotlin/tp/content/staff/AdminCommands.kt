package tp.content.staff

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.commands.Category
import tp.plugin.commands.CommandResult
import tp.plugin.commands.PlayerTarget
import tp.plugin.commands.Tags
import tp.plugin.commands.broadcast
import tp.plugin.commands.investigate
import tp.plugin.commands.isInvisible
import tp.plugin.commands.setInvisible
import tp.plugin.commands.showList
import tp.util.CoordGrid
import tp.util.Rank
import tp.util.commands.Impact

/**
 * The administrator commands of commands brief §6 besides the bans (those share the moderator sanction path):
 * investigations (audited by the hub, never shown in chat: §8), hidden mode, freeze, teleport and broadcasts.
 */
public class AdminCommands : ContentPlugin {
    override fun ContentScope.register() {
        investigation("alts", "alts", "Accounts sharing an IP address or device with the player.", "Alts of")
        investigation("checkip", "ips", "The IP addresses the player logged in from.", "IP addresses of")
        command("invisible", Rank.ADMINISTRATOR, Category.ADMINISTRATION, Impact.MODERATION) {
            aliases("hide")
            description("Hidden mode on or off: left out of player lists for lower ranks.")
            example("::invisible")
            signature { ctx ->
                val p = ctx.player
                p.setInvisible(!p.isInvisible)
                ctx.done(if (p.isInvisible) "You are now hidden." else "You are visible again.")
            }
        }
        command("freeze", Rank.ADMINISTRATOR, Category.ADMINISTRATION, Impact.MODERATION) {
            description("Stops the player moving while you investigate.")
            example("::freeze Big_Bob")
            target()
            val who = player("player")
            signature(who) { ctx ->
                val target = online(ctx[who]) ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                target[STAFF_FROZEN] = true
                target.frozenUntil = Long.MAX_VALUE
                target.message("A staff member has frozen you.")
                ctx.done("Froze ${target.username}.")
            }
        }
        command("unfreeze", Rank.ADMINISTRATOR, Category.ADMINISTRATION, Impact.MODERATION) {
            description("Lets a frozen player move again.")
            example("::unfreeze Big_Bob")
            target()
            val who = player("player")
            signature(who) { ctx ->
                val target = online(ctx[who]) ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                if (target[STAFF_FROZEN] != true) return@signature ctx.fail("${target.username} is not frozen by staff.")
                target[STAFF_FROZEN] = null
                target.frozenUntil = 0
                target.message("You can move again.")
                ctx.done("Unfroze ${target.username}.")
            }
        }
        command("tele", Rank.ADMINISTRATOR, Category.ADMINISTRATION, Impact.OPERATIONS) {
            description("Teleports you to a tile (x z [level], x,z,level or Jagex level,mx,mz,lx,lz) or a named place.")
            example("::tele 3222 3218", "::tele 0,50,50,22,18", "::tele varrock")
            val tile = coord("tile")
            signature(tile) { ctx ->
                ctx.player.teleport(ctx[tile])
                ctx.done()
            }
            val place = text("place")
            signature(place) { ctx ->
                val name = ctx[place].lowercase().replace('_', ' ')
                val to =
                    Places.ALL[name] ?: return@signature ctx.fail("Unknown place. Places: ${Places.ALL.keys.sorted().joinToString(", ")}.")
                ctx.player.teleport(to)
                ctx.done()
            }
        }
        command("broadcast", Rank.ADMINISTRATOR, Category.ADMINISTRATION, Impact.OPERATIONS) {
            description("A game broadcast on this world; end with --all for every world.")
            example("::broadcast Double xp starts in 10 minutes --all")
            val text = text("message")
            signature(text) { ctx ->
                val raw = ctx[text].trim()
                val all = raw.endsWith(ALL_FLAG)
                val line = Tags.escape(raw.removeSuffix(ALL_FLAG).trim())
                if (line.isEmpty()) return@signature ctx.fail("Usage: ::broadcast <message…> [--all]")
                ctx.player.broadcast("<col=$BROADCAST_COLOUR>Broadcast: $line</col>", everywhere = all)
                ctx.done(if (all) "Broadcast sent to every world." else "Broadcast sent.")
            }
        }
    }

    private fun ContentScope.investigation(name: String, kind: String, description: String, title: String) {
        command(name, Rank.ADMINISTRATOR, Category.ADMINISTRATION, Impact.MODERATION) {
            description("$description Audited.")
            example("::$name Big_Bob")
            target()
            crossWorld = true
            val who = player("player", allowOffline = true)
            signature(who) { ctx ->
                val target = ctx[who].name
                ctx.player.investigate(target, kind) { error, lines ->
                    // §8: never in chat; the staff interface shows private lists.
                    if (error != null) message(error) else showList("$title $target", lines.ifEmpty { listOf("None.") }, private = true)
                }
                CommandResult.Pending()
            }
        }
    }

    private fun online(who: PlayerTarget): PlayerRef? = (who as? PlayerTarget.Online)?.player

    private companion object {
        val STAFF_FROZEN = AttrKey<Boolean>("staff.frozen")
        const val ALL_FLAG = "--all"

        /** The game's broadcast red (OSRS news broadcasts use `<col=ff0000>`). */
        const val BROADCAST_COLOUR = "ff0000"
    }
}

/**
 * `::tele <place>` (commands brief §6: a named-locations data file): the standard teleport destinations, taken from
 * docs/mechanics/teleports.md (spell, glory and ring of wealth destinations).
 */
internal object Places {
    /** places.tsv columns: name, x, z, level. */
    private const val LEVEL_FIELD = 3

    val ALL: Map<String, CoordGrid> by lazy {
        val stream = Places::class.java.getResourceAsStream("/staff/places.tsv") ?: return@lazy emptyMap()
        stream.bufferedReader().useLines { lines ->
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t') }
                .associate { f -> f[0].lowercase() to CoordGrid(f[1].toInt(), f[2].toInt(), f[LEVEL_FIELD].toInt()) }
        }
    }
}
