package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Agility courses (docs/mechanics/agility.md): the Gnome Stronghold course and the ten rooftops (Draynor to Ardougne).
 * Crossing an obstacle plays its animation for its tick count and then moves the player to the far side (no forced-movement
 * message exists yet, OPEN-AG1). Obstacles must be crossed in order for a lap to count: finishing one earns the lap bonus
 * and a chance of a mark of grace (AG4, AG5). Obstacles fail only where the Wiki gives a success chart (AG6, OPEN-AG2).
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
        val required = if (obstacle.level > 0) obstacle.level else course.level
        if (p.level(Stats.AGILITY) < required) {
            p.message("You need an Agility level of $required to attempt this obstacle.")
            return
        }
        p.stopInteraction() // one crossing per click (the delay would otherwise leave the interaction running)
        p.anim(obstacle.anim)
        val fail = obstacle.fail
        if (fail != null && !p.statRandom(Stats.AGILITY, fail.low, fail.high)) {
            fall(p, fail)
            return
        }
        val dest = obstacle.dest
        val at = p.coord
        val end = if (dest.relative) at.translate(dest.x, dest.z, dest.level) else CoordGrid(dest.x, dest.z, dest.level)
        if (end.level == at.level) {
            // AG1: forced movement across the obstacle, arriving as the obstacle's ticks end (RS Mod `exactMove`).
            p.exactMove(at, end, 0, obstacle.ticks * CLIENT_CYCLES_PER_TICK, angleOf(end.x - at.x, end.z - at.z))
            delay(obstacle.ticks)
        } else {
            delay(obstacle.ticks)
            p.teleport(end)
        }
        p.anim(-1)
        p.addXp(Stats.AGILITY, obstacle.xp)
        if (progress(p, course, order)) lapDone(p, course)
    }

    /**
     * AG6: a failed crossing keeps the lap (the obstacle is tried again, Wiki "Barbarian Outpost Agility Course"), drops the
     * player to [Fail.fall] after a tick and deals `hitpoints / 17 + 2` damage (Wiki "Hand holds (Falador Rooftop Course)");
     * the animation, delay and message are OPEN-AG7.
     */
    private suspend fun PlayerScript.fall(p: PlayerRef, fail: Fail) {
        delay(FALL_DELAY_TICKS)
        p.teleport(fail.fall)
        p.anim(-1)
        val damage = p.hitpoints / FALL_DAMAGE_DIVISOR + FALL_DAMAGE_BASE
        val request = DamageRequest(p, null, damage)
        p.events.publish(request)
        if (!request.handled) p.hitpoints = (p.hitpoints - damage).coerceAtLeast(1) // no combat module: the fall never kills
        p.message("You slip and fall to the ground.")
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
        val penalised = course.markLevelPenalty && p.baseLevel(Stats.AGILITY) >= course.level + MARK_LEVEL_PENALTY_OVER
        val reduction = if (penalised) MARK_PENALTY_FACTOR else 1
        if (p.random(course.markDenominator * reduction) >= course.markNumerator) return
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

        /** Ticks a failed player takes to fall (OPEN-AG7: not on the Wiki). */
        const val FALL_DELAY_TICKS = 1

        /** Fall damage is `hitpoints / 17 + 2` (Wiki "Hand holds (Falador Rooftop Course)": floor(1/17 hitpoints + 2)). */
        const val FALL_DAMAGE_DIVISOR = 17
        const val FALL_DAMAGE_BASE = 2

        /** Levels over the course's requirement from which marks are 80% rarer (Wiki "Marks of grace": 20 levels). */
        const val MARK_LEVEL_PENALTY_OVER = 20

        /** The 80% reduction: a fifth of the chance, so the denominator is five times larger. */
        const val MARK_PENALTY_FACTOR = 5
    }
}

/** Client cycles (20 ms) per game tick (600 ms). */
private const val CLIENT_CYCLES_PER_TICK = 30

/** Client angle units in a full turn (`exactmove` angle: 0 south, 512 west, 1024 north, 1536 east). */
private const val FULL_TURN = 2048

/** The client angle facing along (dx, dz). */
private fun angleOf(dx: Int, dz: Int): Int {
    val turns = kotlin.math.atan2(-dx.toDouble(), -dz.toDouble()) / (2 * Math.PI)
    return Math.floorMod(Math.round(turns * FULL_TURN).toInt(), FULL_TURN)
}
