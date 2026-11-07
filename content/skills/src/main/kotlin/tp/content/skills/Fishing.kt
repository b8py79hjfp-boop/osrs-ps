package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * Fishing (docs/mechanics/fishing.md). Spots are NPCs; the ids by spot type come from the Wiki (`fishing-spots.tsv`) and
 * op 1 / op 3 of each pick the [Method]. The tick timeline is Woodcutting's with the Wiki's 5-tick fishing cycle
 * (OPEN-FS2 for the first roll): the click arms the clock, the cast starts the next tick, and a catch is rolled every 5th
 * tick. A roll cascades from the best fish the level allows down to the worst ([PlayerRef.statRandom] each). Where the spots
 * stand and how they move is [FishingSpots].
 */
public class Fishing : ContentPlugin {
    private val spots = FishingSpots()

    override fun ContentScope.register() {
        for ((npc, kind) in loadSpots()) {
            kind.primary?.let { method -> onOpNpc(npc, OP_PRIMARY) { target -> fish(target, method, OP_PRIMARY) } }
            kind.secondary?.let { method -> onOpNpc(npc, OP_SECONDARY) { target -> fish(target, method, OP_SECONDARY) } }
        }
        spots.install(this)
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

    private fun PlayerScript.fish(npc: NpcRef, method: Method, op: Int) {
        val p = player
        if (!npc.isInWorld) {
            // The spot moved: run to its new place and carry on there (Wiki "Fishing spot" trivia).
            Skilling.stop(p)
            spots.replacement(npc.index, p.tick)?.let { p.interact(it, op) }
            return
        }
        val clock = Skilling.clock(p, Skilling.locKey(method.ordinal, npc.index))
        gateProblem(p, method.gate)?.let {
            p.message(it)
            return Skilling.stop(p)
        }
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
                else -> otherSkillProblem(p, bait) ?: if (inv.freeSlots == 0) FULL else null
            }
        if (problem != null) p.message(problem)
        return if (problem == null) bait else null
    }

    /** Barbarian fishing needs Strength and Agility too: the message if the easiest fish of [bait] is out of reach (OPEN-FS13 wording). */
    private fun otherSkillProblem(p: PlayerRef, bait: Bait): String? {
        val easiest = bait.catches.minBy { it.level }
        val missing = easiest.requires.firstOrNull { p.level(it.stat) < it.level } ?: return null
        return "You need a ${Stats.name(missing.stat)} level of ${missing.level} to fish at this spot."
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
            if (method == Method.BIG_NET) bigNet(p) else catchOne(p, method, bait, tool)
        }
    }

    /** One cascading roll: the best fish the level allows that the roll lands, if any. */
    private fun catchOne(p: PlayerRef, method: Method, bait: Bait, tool: Tool) {
        val fish =
            bait.catches.firstOrNull { reaches(p, it) && p.statRandom(Stats.FISHING, it.rates.low(tool.tier), it.rates.high(tool.tier)) }
        if (fish == null) {
            // Karambwanji is used up on a miss too (Wiki "Raw karambwan").
            if (method.baitOnMiss && !p.transaction("fishing") { remove(Invs.INV, bait.obj, 1) }) {
                Skilling.stop(p)
                p.stopInteraction()
            }
            return
        }
        val big = fish.bigOneIn > 0 && p.random(fish.bigOneIn) == 0
        val obj = if (big) fish.bigObj else fish.obj
        val cooked = !big && tool.cooks && infernalCooks(p, fish)
        val stored =
            p.transaction("fishing") {
                (bait.obj < 0 || remove(Invs.INV, bait.obj, 1)) && (cooked || add(Invs.INV, obj, 1))
            }
        if (!stored) {
            Skilling.stop(p)
            return p.stopInteraction()
        }
        p.message(if (big) fish.bigMessage else fish.message, Skilling.SPAM)
        p.addXp(Stats.FISHING, fish.xp * anglerBonus(p))
        for (extra in fish.extras) p.addXp(extra.stat, extra.xp)
    }

    /** Whether the level (and the other skills) allow [fish]. */
    private fun reaches(p: PlayerRef, fish: Fish): Boolean =
        p.level(Stats.FISHING) >= fish.level && fish.requires.all { p.level(it.stat) >= it.level }

    /**
     * The infernal harpoon: with a charge left, 1 in 3 fish is cooked and destroyed at once for half its Cooking xp (Wiki "Infernal
     * harpoon"). The charges are a per-player count (OPEN-FS4: plugin-api has no per-item charges). True when the fish was cooked.
     */
    private fun infernalCooks(p: PlayerRef, fish: Fish): Boolean {
        val food = FOODS[fish.obj] ?: return false
        val charges = p.saved(INFERNAL_CHARGES_KEY) ?: INFERNAL_CHARGES
        if (charges <= 0 || p.random(INFERNAL_ONE_IN) != 0) return false
        p.setSaved(INFERNAL_CHARGES_KEY, charges - 1)
        p.addXp(Stats.COOKING, food.xp / 2)
        return true
    }

    /** The full angler's outfit (or its spirit version, piece for piece) gives 2.5% more Fishing xp (Wiki "Angler's outfit"). */
    private fun anglerBonus(p: PlayerRef): Double {
        val worn = p.inv(Invs.WORN)
        return if (ANGLER_PIECES.all { (slot, objs) -> worn.obj(slot) in objs }) ANGLER_MULTIPLIER else 1.0
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
        if (xp > 0.0) p.addXp(Stats.FISHING, xp * anglerBonus(p))
    }

    /** Why [p] may not use a method that needs [gate], or null (FS11: only enforced when [ENFORCE_QUESTS] is on). */
    private fun gateProblem(p: PlayerRef, gate: Gate): String? {
        if (!ENFORCE_QUESTS) return null
        val met =
            when (gate) {
                Gate.NONE -> true
                Gate.TAI_BWO_WANNAI_TRIO -> p.varp(TBWT_VARP) >= TBWT_DONE
                Gate.BARBARIAN_TRAINING -> p.varbit(Varbits.BRUT_FISHING_R) != 0
                Gate.REGICIDE -> p.varp(REGICIDE_VARP) >= REGICIDE_DONE
            }
        return if (met) null else "You have not completed the quest you need to fish here."
    }

    private companion object {
        /** Op 1 of a spot: Net, Lure, Cage, Big Net, Fish, Use-rod, Bait or Catch. */
        const val OP_PRIMARY = 1

        /** Op 3 of a spot: Bait or Harpoon. */
        const val OP_SECONDARY = 3

        /** A roll lands every 5th tick (Wiki "Tick manipulation": fishing is a 5-tick action), the clock arms 4 ahead as in woodcutting. */
        const val ROLL_DELAY = 4

        /** The fishing animation is replayed with each roll (OPEN-FS3). */
        const val ANIM_TICKS = 5

        const val FULL = "Your inventory is too full to hold any more fish."

        /**
         * OPEN-FS11: Tai Bwo Wannai Trio, Barbarian Training and Regicide are not implemented, so nobody could ever meet their gates.
         * Switch this on when they are; the varps are the "quest-states.tsv" rows of content/quests.
         */
        const val ENFORCE_QUESTS = false

        /** Tai Bwo Wannai Trio: state varp 320, complete at 6 (content/quests quest-states.tsv). */
        const val TBWT_VARP = 320
        const val TBWT_DONE = 6

        /** Regicide: state varp 328, complete at 15 (content/quests quest-states.tsv). */
        const val REGICIDE_VARP = 328
        const val REGICIDE_DONE = 15

        /** The infernal harpoon "is able to cook 5,000 fish before degrading" and cooks 1 in 3 catches (Wiki "Infernal harpoon"). */
        const val INFERNAL_CHARGES = 5000L
        const val INFERNAL_ONE_IN = 3
        const val INFERNAL_CHARGES_KEY = "fishing.infernal_charges"

        /** 2.5% Fishing xp from the full angler's outfit (Wiki "Angler's outfit"); per-piece shares are not sourced (OPEN-FS14). */
        const val ANGLER_MULTIPLIER = 1.025

        /** Worn slots (cache wear positions) head 0, body 4, legs 7, feet 10 and the pieces of each: angler's or spirit angler's. */
        val ANGLER_PIECES: List<Pair<Int, Set<Int>>> =
            listOf(
                0 to setOf(Objs.TRAWLER_REWARD_HAT, Objs.SPIRIT_ANGLER_HAT),
                4 to setOf(Objs.TRAWLER_REWARD_TOP, Objs.SPIRIT_ANGLER_TOP),
                7 to setOf(Objs.TRAWLER_REWARD_LEGS, Objs.SPIRIT_ANGLER_LEGS),
                10 to setOf(Objs.TRAWLER_REWARD_BOOTS, Objs.SPIRIT_ANGLER_BOOTS),
            )
    }
}
