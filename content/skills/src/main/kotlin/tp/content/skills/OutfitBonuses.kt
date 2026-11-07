package tp.content.skills

import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Experience-boosting skilling outfits worn by a player (docs/mechanics/firemaking.md FM9, woodcutting.md WC7). Bonuses
 * are in tenths of a percent and add up; the full set adds [SET_BONUS_TENTHS] on top.
 */
internal object OutfitBonuses {
    /** Wiki "Pyromancer outfit" set bonus: 0.8 + 0.6 + 0.4 + 0.2 for the pieces plus 0.5 gives the 2.5% quoted for the full set. */
    const val SET_BONUS_TENTHS = 5

    /** Wiki "Pyromancer garb" 0.8%, "Pyromancer robe" 0.6%, "Pyromancer hood" 0.4%, "Pyromancer boots" 0.2% (tenths of a percent). */
    private val PYROMANCER: Map<Int, Int> =
        mapOf(Objs.PYROMANCER_TOP to 8, Objs.PYROMANCER_BOTTOM to 6, Objs.PYROMANCER_HOOD to 4, Objs.PYROMANCER_BOOTS to 2)

    /** The Firemaking xp multiplier from the pyromancer outfit worn by [player] (Wiki "Pyromancer outfit"). */
    fun firemaking(player: PlayerRef): Double {
        val worn = player.inv(Invs.WORN)
        var tenths = 0
        var pieces = 0
        for (slot in 0 until worn.size) {
            val bonus = PYROMANCER[worn.obj(slot)] ?: continue
            tenths += bonus
            pieces++
        }
        if (pieces == PYROMANCER.size) tenths += SET_BONUS_TENTHS
        return 1.0 + tenths / TENTHS_PER_UNIT
    }

    /** Adds [xp] Firemaking xp with the outfit bonus (every Firemaking source: logs, braziers, the infernal axe). */
    fun addFiremakingXp(player: PlayerRef, xp: Double) {
        player.addXp(tp.plugin.Stats.FIREMAKING, xp * firemaking(player))
    }

    /** Tenths of a percent per 1.0 of multiplier (1% = 10 tenths, so 100% = 1000). */
    private const val TENTHS_PER_UNIT = 1000.0
}
