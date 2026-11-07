package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerScript
import tp.plugin.Stats

/**
 * Agility shortcuts (docs/mechanics/agility.md AG7): one handler for the table in [SHORTCUTS]. A click needs the shortcut's
 * Agility level (current level), plays its animation for its tick count and moves the player to the end of the shortcut that
 * is farther from them; the xp is the Wiki's. Shortcuts that can be failed, need another skill or a quest, or whose far side
 * the cache does not show clearly are not in the table (OPEN-AG9).
 */
public class Shortcuts : ContentPlugin {
    override fun ContentScope.register() {
        for (shortcut in SHORTCUTS) {
            for (loc in shortcut.locs) onOpLoc(loc, shortcut.op) { cross(shortcut) }
        }
    }

    private suspend fun PlayerScript.cross(shortcut: Shortcut) {
        val p = player
        if (p.level(Stats.AGILITY) < shortcut.level) {
            p.message("You need an Agility level of ${shortcut.level} to ${shortcut.verb}.")
            return
        }
        p.stopInteraction()
        val at = p.coord
        val toB = at.chebyshev(shortcut.a) <= at.chebyshev(shortcut.b)
        p.anim(shortcut.anim)
        delay(shortcut.ticks)
        p.teleport(if (toB) shortcut.b else shortcut.a)
        p.anim(-1)
        if (shortcut.xp > 0.0) p.addXp(Stats.AGILITY, shortcut.xp)
    }
}
