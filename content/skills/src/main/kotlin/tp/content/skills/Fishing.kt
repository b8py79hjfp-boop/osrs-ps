package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * Fishing (docs/mechanics/fishing.md). Spots are NPCs; the ids by spot type come from the Wiki (`fishing-spots.tsv`) and
 * op 1 / op 3 of each pick the [Method]. The tick timeline is Woodcutting's with the Wiki's 5-tick fishing cycle
 * (OPEN-FS2 for the first roll): the click arms the clock, the cast starts the next tick, and a catch is rolled every 5th
 * tick. A roll cascades from the best fish the level allows down to the worst ([PlayerRef.statRandom] each).
 */
public class Fishing : ContentPlugin {
    override fun ContentScope.register() {
        for ((npc, kind) in loadSpots()) {
            onOpNpc(npc, OP_PRIMARY) { target -> fish(target, kind.primary) }
            val secondary = kind.secondary
            if (secondary != null) onOpNpc(npc, OP_SECONDARY) { target -> fish(target, secondary) }
        }
    }

    private fun loadSpots(): List<Pair<Int, SpotKind>> {
        val text = Fishing::class.java.getResource("fishing-spots.tsv")?.readText() ?: error("fishing-spots.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val cols = line.split('\t')
                cols[1].toInt() to SpotKind.valueOf(cols[0])
            }.toList()
    }

    private fun PlayerScript.fish(npc: NpcRef, method: Method) {
        val p = player
        if (!npc.isInWorld) return Skilling.stop(p)
        val clock = Skilling.clock(p, Skilling.locKey(method.ordinal, npc.index))
        val tool = usableTool(p, method) ?: return Skilling.stop(p)
        val bait = usableBait(p, method) ?: return Skilling.stop(p)
        keepInteraction()
        val tick = p.tick
        if (clock.fresh && clock.actionDelay < tick) {
            // Same arming tick as woodcutting (RS Mod `Woodcutting.attempt`): the cast comes on the next tick.
            clock.actionDelay = tick + ROLL_DELAY
            clock.animDelay = tick + ROLL_DELAY
        } else {
            if (clock.stage == 0) {
                clock.stage = 1
                p.anim(tool.anim)
                p.message(method.start, Skilling.SPAM)
            }
            cast(p, method, bait, tool, clock)
        }
    }

    /** The best tool of [method] the player can use, or null (with the message). */
    private fun usableTool(p: PlayerRef, method: Method): Tool? {
        val toolObj = Skilling.bestTool(p, Stats.FISHING, method.toolLevels)
        if (toolObj < 0) p.message(method.noTool)
        return method.tools[toolObj]
    }

    /** The bait of [method] the player can fish with, or null (with the message): the right level, bait and a free slot. */
    private fun usableBait(p: PlayerRef, method: Method): Bait? {
        val inv = p.inv(Invs.INV)
        val bait = method.baits.firstOrNull { it.obj < 0 || inv.total(it.obj) > 0 }
        val problem =
            when {
                bait == null -> method.noBait
                p.level(Stats.FISHING) < bait.minLevel -> "You need a Fishing level of ${bait.minLevel} to fish at this spot."
                inv.freeSlots == 0 -> "Your inventory is too full to hold any more fish."
                else -> null
            }
        if (problem != null) p.message(problem)
        return if (problem == null) bait else null
    }

    private fun cast(p: PlayerRef, method: Method, bait: Bait, tool: Tool, clock: SkillClock) {
        val tick = p.tick
        if (clock.animDelay <= tick) {
            clock.animDelay = tick + ANIM_TICKS
            p.anim(tool.anim)
        }
        if (clock.actionDelay < tick) {
            val slow = method.slowOneIn > 0 && p.random(method.slowOneIn) == 0
            clock.actionDelay = tick + ROLL_DELAY + if (slow) 1 else 0
        } else if (clock.actionDelay == tick) {
            if (method == Method.BIG_NET) bigNet(p) else catchOne(p, bait, tool)
        }
    }

    /** One cascading roll: the best fish the level allows that the roll lands, if any. */
    private fun catchOne(p: PlayerRef, bait: Bait, tool: Tool) {
        val level = p.level(Stats.FISHING)
        val fish =
            bait.catches.firstOrNull { level >= it.level && p.statRandom(Stats.FISHING, it.rates.low(tool.tier), it.rates.high(tool.tier)) }
                ?: return
        val big = fish.bigOneIn > 0 && p.random(fish.bigOneIn) == 0
        val obj = if (big) fish.bigObj else fish.obj
        val stored =
            p.transaction("fishing") {
                (bait.obj < 0 || remove(Invs.INV, bait.obj, 1)) && add(Invs.INV, obj, 1)
            }
        if (!stored) {
            Skilling.stop(p)
            return p.stopInteraction()
        }
        p.message(if (big) fish.bigMessage else fish.message, Skilling.SPAM)
        p.addXp(Stats.FISHING, fish.xp)
    }

    /** A big net cast makes every roll of [BIG_NET_ROLLS] independently, so it can land several fish and items at once. */
    private fun bigNet(p: PlayerRef) {
        val level = p.level(Stats.FISHING)
        var xp = 0.0
        p.transaction("fishing") {
            for (roll in BIG_NET_ROLLS) {
                if (level < roll.level || !p.statRandom(Stats.FISHING, roll.low, roll.high)) continue
                val fish = roll.fish
                if (fish == null) {
                    addOrDrop(Invs.INV, roll.obj, 1)
                    continue
                }
                val big = fish.bigOneIn > 0 && p.random(fish.bigOneIn) == 0
                addOrDrop(Invs.INV, if (big) fish.bigObj else fish.obj, 1)
                p.message(if (big) fish.bigMessage else fish.message, Skilling.SPAM)
                xp += fish.xp
            }
            true
        }
        if (xp > 0.0) p.addXp(Stats.FISHING, xp)
    }

    private companion object {
        /** Op 1 of a spot: Net, Lure, Cage or Big Net. */
        const val OP_PRIMARY = 1

        /** Op 3 of a spot: Bait or Harpoon. */
        const val OP_SECONDARY = 3

        /** A roll lands every 5th tick (Wiki "Tick manipulation": fishing is a 5-tick action), the clock arms 4 ahead as in woodcutting. */
        const val ROLL_DELAY = 4

        /** The fishing animation is replayed with each roll (OPEN-FS3). */
        const val ANIM_TICKS = 5
    }
}
