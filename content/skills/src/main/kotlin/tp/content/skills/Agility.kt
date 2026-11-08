package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.commands.perk
import tp.symbols.Objs
import tp.util.CoordGrid
import tp.util.DonatorPerk

/**
 * Agility courses (docs/mechanics/agility.md): the Gnome Stronghold course, the ten rooftops (Draynor to Ardougne) and the
 * Barbarian Outpost and Wilderness courses of `AgilityCourses.kt`. Obstacles that several courses share (one loc in
 * two courses) advance each of those courses' laps; obstacles that share a loc id on one course are told apart by placement.
 * Crossing an obstacle plays its animation for its tick count and then moves the player to the far side (no forced-movement
 * message exists yet, OPEN-AG1). Obstacles must be crossed in order for a lap to count: finishing one earns the lap bonus
 * and a chance of a mark of grace (AG4, AG5). Obstacles fail only where the Wiki gives a success chart (AG6, OPEN-AG2).
 */
public class Agility : ContentPlugin {
    /** The unbroken laps a player has under way: for each course the order of the obstacle that continues it. */
    private class Laps {
        val next = HashMap<Course, Int>(2)
    }

    /** One obstacle of one course that a (loc, op) pair stands for; courses that share an obstacle (Shayzien) have several. */
    private class Entry(val course: Course, val order: Int, val obstacle: Obstacle)

    override fun ContentScope.register() {
        val byLoc = LinkedHashMap<Long, MutableList<Entry>>()
        for (course in COURSES) {
            course.obstacles.forEachIndexed { order, obstacle ->
                for (loc in listOf(obstacle.loc) + obstacle.alsoLocs) {
                    byLoc
                        .getOrPut(
                            (loc.toLong() shl Int.SIZE_BITS) or obstacle.op.toLong(),
                        ) { ArrayList() }
                        .add(Entry(course, order, obstacle))
                }
            }
        }
        for ((key, entries) in byLoc) {
            val loc = (key shr Int.SIZE_BITS).toInt()
            onOpLoc(loc, key.toInt()) { ref -> cross(entries.filter { it.obstacle.at == null || it.obstacle.at == ref.coord }) }
        }
    }

    private suspend fun PlayerScript.cross(entries: List<Entry>) {
        val p = player
        val first = entries.firstOrNull() ?: return
        val course = first.course
        val obstacle = first.obstacle
        val required = if (obstacle.level > 0) obstacle.level else course.level
        if (p.level(Stats.AGILITY) < required) {
            p.message("You need an Agility level of $required to attempt this obstacle.")
            return
        }
        val refusal = course.entry?.invoke(p) ?: obstacle.check?.invoke(p)
        if (refusal != null) {
            p.message(refusal)
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
        val completed = entries.filter { progress(p, it.course, it.order) }
        // A lap-only obstacle (Wilderness rocks) pays its xp only for an unbroken lap.
        if (!obstacle.xpOnlyInLap || completed.isNotEmpty()) p.addXp(Stats.AGILITY, obstacle.xp)
        p[LAPS]?.next?.keys?.retainAll(entries.mapTo(HashSet(2)) { it.course }) // doing another course's obstacle drops this lap
        for (entry in entries) if (entry.order == 0) entry.course.onStart?.invoke(p)
        for (entry in completed) lapDone(p, entry.course)
    }

    /**
     * AG6: a failed crossing keeps the lap (the obstacle is tried again, Wiki "Barbarian Outpost Agility Course"), drops the
     * player to [Fail.fall] (where the Wiki gives one) after a tick and deals `hitpoints * n / d + base` damage (Wiki "Hand holds
     * (Falador Rooftop Course)": floor(1/17 hitpoints + 2); Wilderness "Ropeswing": floor(3/20 hitpoints) + 1); the animation,
     * delay and message are OPEN-AG7.
     */
    private suspend fun PlayerScript.fall(p: PlayerRef, fail: Fail) {
        delay(FALL_DELAY_TICKS)
        fail.fall?.let { p.teleport(it) }
        p.anim(-1)
        val damage = p.hitpoints * fail.damageNumerator / fail.damageDenominator + fail.damageBase
        val request = DamageRequest(p, null, damage)
        p.events.publish(request)
        if (!request.handled) p.hitpoints = (p.hitpoints - damage).coerceAtLeast(1) // no combat module: the fall never kills
        p.message("You slip and fall to the ground.")
    }

    /**
     * Tracks the lap of [course]: true when [order] was the last obstacle of an unbroken lap. Courses that share their first
     * obstacles (Shayzien) each track their own lap.
     */
    private fun progress(p: PlayerRef, course: Course, order: Int): Boolean {
        val laps = p[LAPS] ?: Laps().also { p[LAPS] = it }
        if (order == 0) {
            laps.next[course] = 1
            return false
        }
        if (laps.next[course] != order) {
            laps.next.remove(course)
            return false
        }
        if (order != course.obstacles.size - 1) {
            laps.next[course] = order + 1
            return false
        }
        laps.next.remove(course)
        return true
    }

    private fun lapDone(p: PlayerRef, course: Course) {
        if (course.lapXp > 0.0) p.addXp(Stats.AGILITY, course.lapXp)
        if (course.lapStrengthXp > 0.0) p.addXp(Stats.STRENGTH, course.lapStrengthXp)
        course.onLap?.invoke(p)
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
        // D-093 donator perk (perks.tsv row "Increased chance of receiving a Mark of grace"): numerator x (100+p)/100 over a
        // x100 bound, one roll; at perk 0 the original roll is untouched. A threshold at or over the bound always passes.
        val boost = p.perk(DonatorPerk.INCREASED_CHANCE_OF_RECEIVING_A_MARK_OF_GRACE)
        if (boost == 0) {
            if (p.random(course.markDenominator * reduction) >= course.markNumerator) return
        } else if (p.random(course.markDenominator * reduction * DONATOR_PERCENT) >= course.markNumerator * (DONATOR_PERCENT + boost)) {
            return
        }
        p.setSaved(SAVED_LAST_MARK, minute)
        val spot = course.markSpots[p.random(course.markSpots.size)]
        p.transaction("mark of grace") { createOnGround(Objs.GRACE, 1, spot, p.username, MARK_LIFETIME) }
    }

    private companion object {
        /** Wiki "Mark of grace": a spawned mark stays 10 minutes = 1000 ticks (OPEN-AG4). */
        const val MARK_LIFETIME = 1000

        val LAPS = AttrKey<Laps>("agility.laps")

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
