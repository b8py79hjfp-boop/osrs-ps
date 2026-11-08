package tp.content.staff

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TeleportRequest
import tp.plugin.TimerId
import tp.plugin.commands.SanctionsChanged
import tp.plugin.commands.Tags
import tp.plugin.commands.Warned
import tp.plugin.commands.commandsConfig
import tp.plugin.commands.homeCoord
import tp.plugin.commands.sanctions
import tp.util.CoordGrid
import tp.util.Sanctions

/**
 * Applies the hub's sanctions on this world (commands brief §7): the jail (a tile and an area a jailed player cannot
 * leave; checked every tick, so walking, teleports and logging back in all end inside), and what the player sees when
 * a mute, yell-mute, jail or warning reaches them. The reason and the expiry are always shown.
 */
public class Jail : ContentPlugin {
    override fun ContentScope.register() {
        onLogin {
            val now = System.currentTimeMillis()
            when {
                sanctions.jailed(now) -> jail(this, announce = true)

                // Expired (or lifted) while offline: let them out.
                (saved(JAILED_KEY) ?: 0L) != 0L -> release(this)
            }
        }
        events.on<SanctionsChanged> { e -> changed(e.player, e.before) }
        events.on<Warned> { e -> e.player.message("<col=ff0000>Warning from staff: ${Tags.escape(e.reason)}</col>") }
        events.on<TeleportRequest> { r ->
            if (!r.player.sanctions.jailed(System.currentTimeMillis())) return@on
            r.denied = jailedMessage(r.player)
            r.handled = true
        }
    }

    private fun changed(p: PlayerRef, before: Sanctions) {
        val now = System.currentTimeMillis()
        val after = p.sanctions
        if (after.muted(now) && after.mutedUntil != before.mutedUntil) {
            p.message("You have been muted ${Sanctions.describeUntil(after.mutedUntil)}${reason(after.muteReason)}.")
        } else if (!after.muted(now) && before.muted(now)) {
            p.message("You have been unmuted.")
        }
        if (after.yellMuted(now) && after.yellMutedUntil != before.yellMutedUntil) {
            p.message("You can't yell ${Sanctions.describeUntil(after.yellMutedUntil)}${reason(after.yellMuteReason)}.")
        } else if (!after.yellMuted(now) && before.yellMuted(now)) {
            p.message("You can yell again.")
        }
        if (after.jailed(now)) {
            jail(p, announce = after.jailedUntil != before.jailedUntil)
        } else if (before.jailed(now)) {
            release(p)
        }
    }

    private fun jail(p: PlayerRef, announce: Boolean) {
        val area = p.commandsConfig.jail
        p.setSaved(JAILED_KEY, 1L)
        if (!area.contains(p.coord.x, p.coord.z, p.coord.level)) p.teleport(CoordGrid(area.x, area.z, area.level))
        if (announce) p.message(jailedMessage(p))
        p.timer(JAIL_TIMER, 1, soft = false) { check(this) }
    }

    /** Every tick while jailed: out when it expires, back in when they got out. */
    private fun check(p: PlayerRef) {
        if (!p.sanctions.jailed(System.currentTimeMillis())) return release(p)
        val area = p.commandsConfig.jail
        if (area.contains(p.coord.x, p.coord.z, p.coord.level)) return
        p.teleport(CoordGrid(area.x, area.z, area.level))
        p.message(jailedMessage(p))
    }

    private fun release(p: PlayerRef) {
        p.stopTimer(JAIL_TIMER)
        p.setSaved(JAILED_KEY, null)
        p.teleport(p.homeCoord)
        p.message("You have been released from jail.")
    }

    private fun jailedMessage(p: PlayerRef): String {
        val s = p.sanctions
        return "You are jailed ${Sanctions.describeUntil(s.jailedUntil)}${reason(s.jailReason)}."
    }

    private fun reason(text: String): String = if (text.isEmpty()) "" else " (${Tags.escape(text)})"

    private companion object {
        val JAIL_TIMER = TimerId("staff.jail")

        /** Saved flag: 1 while the player is kept in the jail (so an expiry while offline releases them at login). */
        const val JAILED_KEY = "staff.jailed"
    }
}
