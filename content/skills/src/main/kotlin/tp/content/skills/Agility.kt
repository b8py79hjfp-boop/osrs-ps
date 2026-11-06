package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Agility courses (docs/mechanics/agility.md): the Gnome Stronghold course and the Draynor, Al Kharid and Varrock rooftops.
 * Crossing an obstacle plays its animation for its tick count and then moves the player to the far side (no forced-movement
 * message exists yet, OPEN-AG1). Obstacles must be crossed in order for a lap to count: finishing one earns the lap bonus
 * and a chance of a mark of grace (AG4, AG5). Failing obstacles is OPEN-AG2: the Wiki gives the damage but no chance.
 */
public class Agility : ContentPlugin {
    /** How far a player has got in a lap: [next] is the order of the obstacle that continues it. */
    private class Lap(val course: Course, var next: Int)

    override fun ContentScope.register() {
        for (course in COURSES) {
            course.obstacles.forEachIndexed { order, obstacle ->
                for (loc in listOf(obstacle.loc) + obstacle.alsoLocs) onOpLoc(loc, obstacle.op) { cross(course, order) }
            }
        }
    }

    private suspend fun PlayerScript.cross(course: Course, order: Int) {
        val p = player
        val obstacle = course.obstacles[order]
        if (p.level(Stats.AGILITY) < course.level) {
            p.message("You need an Agility level of ${course.level} to attempt this obstacle.")
            return
        }
        p.stopInteraction() // one crossing per click (the delay would otherwise leave the interaction running)
        p.anim(obstacle.anim)
        delay(obstacle.ticks)
        val dest = obstacle.dest
        val at = p.coord
        p.teleport(if (dest.relative) at.translate(dest.x, dest.z, dest.level) else CoordGrid(dest.x, dest.z, dest.level))
        p.anim(-1)
        p.addXp(Stats.AGILITY, obstacle.xp)
        if (progress(p, course, order)) lapDone(p, course)
    }

    /** Tracks the lap: true when [order] was the last obstacle of an unbroken lap. */
    private fun progress(p: PlayerRef, course: Course, order: Int): Boolean {
        if (order == 0) {
            p[LAP] = Lap(course, 1)
            return false
        }
        val lap = p[LAP]
        if (lap == null || lap.course !== course || lap.next != order) {
            p[LAP] = null
            return false
        }
        lap.next = order + 1
        if (order != course.obstacles.size - 1) return false
        p[LAP] = null
        return true
    }

    private fun lapDone(p: PlayerRef, course: Course) {
        if (course.lapXp > 0.0) p.addXp(Stats.AGILITY, course.lapXp)
        rollMark(p, course)
    }

    /**
     * Wiki "Marks of grace": after the cooldown, a finished lap spawns a mark with chance 2/6 (80% lower once the unboosted
     * level is 20 over the course's), for the player alone, on one of the course's spots. The cooldown is in real minutes and
     * persists across logins.
     */
    private fun rollMark(p: PlayerRef, course: Course) {
        if (course.markSpots.isEmpty()) return
        val minute = p.epochMinutes
        val last = p.saved(SAVED_LAST_MARK)
        if (last != null && minute < last + MARK_COOLDOWN_MINUTES) return
        val reduction = if (p.baseLevel(Stats.AGILITY) >= course.level + MARK_LEVEL_PENALTY_OVER) MARK_PENALTY_FACTOR else 1
        if (p.random(MARK_CHANCE_DENOMINATOR * reduction) >= MARK_CHANCE_NUMERATOR) return
        p.setSaved(SAVED_LAST_MARK, minute)
        val spot = course.markSpots[p.random(course.markSpots.size)]
        p.transaction("mark of grace") { createOnGround(Objs.GRACE, 1, spot, p.username) }
    }

    private companion object {
        val LAP = AttrKey<Lap>("agility.lap")

        /** Saved-state key: the epoch minute of the last mark of grace spawn. */
        const val SAVED_LAST_MARK = "agility.mark_minute"

        /** Minutes between two mark spawns (Wiki "Marks of grace" Spawning mechanics: three minutes). */
        const val MARK_COOLDOWN_MINUTES = 3L

        /** A lap spawns a mark with chance 2/6 (Wiki "Marks of grace": "2/6 for all other rooftop courses"; the Gnome course OPEN-AG5). */
        const val MARK_CHANCE_NUMERATOR = 2
        const val MARK_CHANCE_DENOMINATOR = 6

        /** Levels over the course's requirement from which marks are 80% rarer (Wiki "Marks of grace": 20 levels). */
        const val MARK_LEVEL_PENALTY_OVER = 20

        /** The 80% reduction: a fifth of the chance, so the denominator is five times larger. */
        const val MARK_PENALTY_FACTOR = 5
    }
}
