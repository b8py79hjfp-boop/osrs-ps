package tp.content.pvp

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.commands.Category
import tp.plugin.commands.PlayerTarget
import tp.util.Rank
import tp.util.commands.Impact

/**
 * PvP kill/death tracking for `::kdr` (commands brief §6: "Needs PvP stat tracking, so add it"). Saved per account:
 * kills, deaths, the current kill streak (reset by a PvP death) and the best streak. Duels do not count.
 */
internal object PvpStats {
    const val KILLS = "pvp.kills"
    const val DEATHS = "pvp.deaths"
    const val STREAK = "pvp.streak"
    const val BEST_STREAK = "pvp.best_streak"

    fun record(killer: PlayerRef, dead: PlayerRef) {
        val streak = killer.get(STREAK) + 1
        killer.setSaved(KILLS, killer.get(KILLS) + 1)
        killer.setSaved(STREAK, streak)
        if (streak > killer.get(BEST_STREAK)) killer.setSaved(BEST_STREAK, streak)
        dead.setSaved(DEATHS, dead.get(DEATHS) + 1)
        dead.setSaved(STREAK, null)
    }

    fun PlayerRef.get(key: String): Long = saved(key) ?: 0L

    fun line(p: PlayerRef): String {
        val kills = p.get(KILLS)
        val deaths = p.get(DEATHS)
        val ratio = if (deaths == 0L) kills.toDouble() else kills.toDouble() / deaths
        return "${p.username}: $kills kills, $deaths deaths, ratio ${"%.2f".format(ratio)}, " +
            "streak ${p.get(STREAK)} (best ${p.get(BEST_STREAK)})."
    }
}

/** `::kdr [player]`. */
public class PvpStatsCommands : ContentPlugin {
    override fun ContentScope.register() {
        command("kdr", Rank.PLAYER, Category.INFO, Impact.INFO) {
            description("PvP kills, deaths, ratio and best streak (yours, or an online player's).")
            example("::kdr", "::kdr Zezima")
            val who = player("player", optional = true)
            signature(who) { ctx ->
                val target = (ctx.opt(who) as? PlayerTarget.Online)?.player ?: ctx.player
                ctx.done(PvpStats.line(target))
            }
        }
    }
}
