package tp.content.raids.cox

import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * The Ice Demon approach's saplings (resources.md KN1-KN4): any axe chops kindling, the yield follows the Woodcutting
 * level, a chop may topple the tree, and a player holding 28 kindling cannot chop. The brazier side is in puzzles.md.
 */
internal object CoxKindling {
    /** KN2: a random 0 .. floor(visible Woodcutting level / 12), 0 counting as 1. */
    private const val LEVELS_PER_KINDLING = 12

    /** KN2: the XP of a chop by the kindling it gave, 1..8 (the table's "XP at max" column; the averages of the table follow). */
    private val XP_BY_YIELD = doubleArrayOf(0.0, 30.0, 45.0, 55.0, 62.5, 68.5, 73.5, 77.7, 81.5)

    /** KN3: a successful chop topples the tree with chance 1/X, X = max(3, 2 x players at the start) x 5 in the Ice Demon room. */
    private const val MIN_DEPLETION_BASE = 3
    private const val ICE_DEMON_ROOM_FACTOR = 5

    /** KN4: kindling is stackable up to 28; a player holding 28 or more cannot chop. */
    private const val STACK_CAP = 28

    /** OPEN-RS19: the stump respawns "after a few seconds"; the infobox gives 30 seconds (50 ticks). */
    private const val RESPAWN_TICKS = 50

    /** OPEN-RS32: ticks per swing and the success chance out of 256 at levels 1 and 99; the doc states neither. */
    private const val SWING_TICKS = 4
    private const val CHANCE_LOW = 64
    private const val CHANCE_HIGH = 256

    private const val OP1 = 1

    fun register(scope: ContentScope) {
        scope.onOpLoc(Locs.RAIDS_WOODSOURCE_ROOTS, OP1) { loc -> chop(loc) }
    }

    private suspend fun PlayerScript.chop(loc: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        if (!CoxItems.hasAxe(player)) return player.message("You need an axe to chop the sapling.")
        while (player.locAt(loc.coord, loc.shape)?.id == Locs.RAIDS_WOODSOURCE_ROOTS) {
            val held = CoxRes.held(player, Objs.RAIDS_WOOD).toInt()
            if (held >= STACK_CAP) return player.message("You can't carry any more kindling.")
            player.anim(Seqs.HUMAN_WOODCUTTING_BRONZE_AXE)
            delay(SWING_TICKS)
            if (!player.statRandom(Stats.WOODCUTTING, CHANCE_LOW, CHANCE_HIGH)) continue
            val gain = kindlingFor(player.level(Stats.WOODCUTTING)) { player.random(it) }
            player.transaction("cox_kindling") { add(Invs.INV, Objs.RAIDS_WOOD, minOf(gain, STACK_CAP - held)) }
            player.addXp(Stats.WOODCUTTING, XP_BY_YIELD[gain])
            val x = maxOf(MIN_DEPLETION_BASE, 2 * run.scaling.partySize) * ICE_DEMON_ROOM_FACTOR
            if (player.random(x) == 0) {
                player.addLoc(Locs.RAIDS_WOODSOURCE_ROOTS_DEPLETED, loc.coord, loc.shape, loc.angle, RESPAWN_TICKS)
                return
            }
        }
    }

    /** KN2: kindling per successful chop at Woodcutting [level]: a roll of 0 .. level / 12, with 0 counting as 1. */
    fun kindlingFor(level: Int, roll: (Int) -> Int): Int = maxOf(1, roll(level / LEVELS_PER_KINDLING + 1))
}
