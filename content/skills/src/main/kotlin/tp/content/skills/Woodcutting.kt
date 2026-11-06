package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * Woodcutting (docs/mechanics/woodcutting.md), following RS Mod's `Woodcutting` script tick for tick: the click arms
 * the action clock, the swing starts the next tick, and a log is rolled every 4th tick from the 3rd
 * ([PlayerRef.statRandom] with the axe's rates for the tree). Regular trees fall with their first log; Forestry
 * trees fall once they have been cut for their timer's length (WC4).
 */
public class Woodcutting : ContentPlugin {
    /** Axe -> woodcutting level it needs (cache param `levelrequire`). */
    private var axeLevels: Map<Int, Int> = emptyMap()

    /** Forestry tree timers of one world, by packed tile (WC4). */
    private class TreeTimer(var lastCut: Long, var active: Int)

    override fun ContentScope.register() {
        onWorldStart {
            axeLevels = AXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 }
        }
        for ((id, tree) in TREES) {
            onOpLoc(id, OP_CHOP) { loc -> chop(loc, tree) }
            for (axe in AXES.keys) onUseOnLoc(axe, id) { loc -> chop(loc, tree) }
        }
    }

    private fun PlayerScript.chop(loc: LocRef, tree: Tree) {
        val p = player
        if (p.locAt(loc.coord, loc.shape)?.id != loc.id) return Skilling.stop(p)
        val clock = Skilling.clock(p, Skilling.locKey(loc.id, loc.coord.packed))
        val kind = tree.kind
        if (!canCut(p, kind)) return Skilling.stop(p)
        // Checked before the interaction is kept, so a missing axe ends the action (OPEN-WC4: RS Mod checks it a tick later).
        val axe = Skilling.bestTool(p, Stats.WOODCUTTING, axeLevels)
        if (axe < 0) {
            p.message("You need an axe to chop down this tree.")
            p.message("You do not have an axe which you have the woodcutting level to use.")
            return Skilling.stop(p)
        }
        keepInteraction()
        val tick = p.tick
        if (clock.fresh && clock.actionDelay < tick) {
            // RS Mod `attempt`: a click with no action running arms both clocks; the swing comes on the next tick.
            clock.actionDelay = tick + ACTION_TICKS
            clock.animDelay = tick + ACTION_TICKS
            return
        }
        val anim = AXES.getValue(axe).anim
        if (clock.stage == 0) {
            clock.stage = 1
            p.anim(anim)
            p.message("You swing your axe at the tree.", Skilling.SPAM)
        }
        cut(p, loc, tree, axe, clock)
    }

    private fun canCut(p: PlayerRef, kind: TreeKind): Boolean {
        if (p.level(Stats.WOODCUTTING) < kind.level) {
            p.message("You need a Woodcutting level of ${kind.level} to chop down this tree.")
            return false
        }
        if (p.inv(Invs.INV).freeSlots == 0) {
            p.message("Your inventory is too full to hold any more ${p.objName(kind.logs).lowercase()}.")
            return false
        }
        return true
    }

    private fun cut(p: PlayerRef, loc: LocRef, tree: Tree, axe: Int, clock: SkillClock) {
        val kind = tree.kind
        val tick = p.tick
        if (clock.animDelay <= tick) {
            clock.animDelay = tick + ANIM_TICKS
            p.anim(AXES.getValue(axe).anim)
        }
        var logs = false
        if (clock.actionDelay < tick) {
            clock.actionDelay = tick + ACTION_TICKS
        } else if (clock.actionDelay == tick) {
            val tier = AXES.getValue(axe).tier
            logs = p.statRandom(Stats.WOODCUTTING, kind.rates.low(tier), kind.rates.high(tier))
        }
        val fall =
            if (kind.despawnTicks > 0) {
                val cutFor = swing(p, loc, tick)
                logs && cutFor >= kind.despawnTicks
            } else {
                // RS Mod: `random.of(1, 255) > deplete_chance`.
                logs && p.random(DEPLETE_RANGE) + 1 > kind.depleteChance
            }
        if (logs) {
            p.message("You get some ${p.objName(kind.logs).lowercase()}.", Skilling.SPAM)
            p.transaction("woodcutting") { add(Invs.INV, kind.logs, 1) }
            p.addXp(Stats.WOODCUTTING, kind.xp)
        }
        if (fall) {
            p.worldAttr(TIMERS)?.remove(loc.coord.packed)
            p.addLoc(tree.stump, loc.coord, loc.shape, loc.angle, respawn(p, kind.respawn))
            p.anim(-1)
            Skilling.stop(p)
        }
    }

    /**
     * A Forestry tree was swung at this tick: its timer counts one tick up per tick it is cut and one down per tick it
     * is not (RS Mod `treeDespawnTick`, applied lazily here). Returns how long it has now been cut.
     */
    private fun swing(p: PlayerRef, loc: LocRef, tick: Long): Int {
        val timers = p.worldAttr(TIMERS) ?: HashMap<Int, TreeTimer>().also { p.setWorldAttr(TIMERS, it) }
        val timer = timers.getOrPut(loc.coord.packed) { TreeTimer(tick - 1, 0) }
        if (timer.lastCut == tick) return timer.active
        val idle = (tick - timer.lastCut - 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        timer.active = maxOf(0, timer.active - idle) + 1
        timer.lastCut = tick
        return timer.active
    }

    private fun respawn(p: PlayerRef, range: IntRange): Int =
        if (range.first == range.last) range.first else range.first + p.random(range.last - range.first + 1)

    private companion object {
        val TIMERS = AttrKey<HashMap<Int, TreeTimer>>("woodcutting.timers")

        /** Op 1 of every tree ("Chop down"). */
        const val OP_CHOP = 1

        /** The action clock: a roll lands every 4th tick, 3 ticks after the clock is armed (RS Mod `mapClock + 3`). */
        const val ACTION_TICKS = 3

        /** The axe animation is replayed every 4 ticks (RS Mod `skillAnimDelay = mapClock + 4`). */
        const val ANIM_TICKS = 4

        /** RS Mod `random.of(1, 255)`. */
        const val DEPLETE_RANGE = 255
    }
}
