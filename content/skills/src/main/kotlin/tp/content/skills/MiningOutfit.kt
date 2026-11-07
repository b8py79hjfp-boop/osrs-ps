package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * The prospector outfit's Mining xp bonus (docs/mechanics/motherlode.md ML12; Wiki "Prospector kit"). Bonuses are in tenths
 * of a percent and add up; the full set adds [SET_BONUS_TENTHS]. The bonus applies to every Mining xp source.
 */
internal object MiningOutfit {
    /** Wiki "Prospector kit": the set bonus is +0.5% (0.4 + 0.8 + 0.6 + 0.2 + 0.5 = the 2.5% quoted for the full set). */
    const val SET_BONUS_TENTHS = 5

    /** Wiki "Prospector kit" table: helmet +0.4%, jacket +0.8%, legs +0.6%, boots +0.2% (tenths of a percent). */
    private const val HELMET_TENTHS = 4
    private const val JACKET_TENTHS = 8
    private const val LEGS_TENTHS = 6
    private const val BOOTS_TENTHS = 2

    /** Worn-inventory slots (cache wear positions): head 0, body 4, legs 7, boots 10. */
    private const val HEAD_SLOT = 0
    private const val BODY_SLOT = 4
    private const val LEGS_SLOT = 7
    private const val BOOTS_SLOT = 10

    /** Wiki "Prospector kit": the golden recolour and the Petrified Pete (Volcanic Mine) copies give the same bonus. */
    private val HELMETS = setOf(Objs.MOTHERLODE_REWARD_HAT, Objs.MOTHERLODE_REWARD_HAT_GOLD, Objs.FOSSIL_MOTHERLODE_REWARD_HAT)

    /** Wiki "Prospector kit": "Varrock armour 4 may be worn in place of the jacket" (OPEN-ML9: it also counts for the set bonus). */
    private val JACKETS =
        setOf(Objs.MOTHERLODE_REWARD_TOP, Objs.MOTHERLODE_REWARD_TOP_GOLD, Objs.FOSSIL_MOTHERLODE_REWARD_TOP, Objs.VARROCK_ARMOUR_ELITE)
    private val LEGS = setOf(Objs.MOTHERLODE_REWARD_LEGS, Objs.MOTHERLODE_REWARD_LEGS_GOLD, Objs.FOSSIL_MOTHERLODE_REWARD_LEGS)
    private val BOOTS = setOf(Objs.MOTHERLODE_REWARD_BOOTS, Objs.MOTHERLODE_REWARD_BOOTS_GOLD, Objs.FOSSIL_MOTHERLODE_REWARD_BOOTS)

    /** Tenths of a percent per 1.0 of multiplier (1% = 10 tenths). */
    private const val TENTHS_PER_UNIT = 1000.0

    /** The Mining xp multiplier of the prospector pieces [player] wears. */
    fun multiplier(player: PlayerRef): Double {
        val worn = player.inv(Invs.WORN)
        var tenths = 0
        var pieces = 0
        if (worn.obj(HEAD_SLOT) in HELMETS) {
            tenths += HELMET_TENTHS
            pieces++
        }
        if (worn.obj(BODY_SLOT) in JACKETS) {
            tenths += JACKET_TENTHS
            pieces++
        }
        if (worn.obj(LEGS_SLOT) in LEGS) {
            tenths += LEGS_TENTHS
            pieces++
        }
        if (worn.obj(BOOTS_SLOT) in BOOTS) {
            tenths += BOOTS_TENTHS
            pieces++
        }
        if (pieces == FULL_SET) tenths += SET_BONUS_TENTHS
        return 1.0 + tenths / TENTHS_PER_UNIT
    }

    /** Pieces of the full set. */
    private const val FULL_SET = 4

    /** Adds [xp] Mining xp with the outfit bonus (every Mining source). */
    fun addXp(player: PlayerRef, xp: Double): Int = player.addXp(Stats.MINING, xp * multiplier(player))
}
