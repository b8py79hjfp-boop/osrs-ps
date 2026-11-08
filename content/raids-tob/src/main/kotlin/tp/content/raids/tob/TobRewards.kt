package tp.content.raids.tob

import tp.symbols.Objs

/**
 * The Theatre of Blood's reward math (raids brief section 1.3; docs/mechanics/raids/tob.md). Pure functions: no world
 * state.
 */
public object TobRewards {
    /** Brief 1.3 / Monumental chest: one unique in 9.1 deathless Normal raids, 7.7 in Hard, none in Entry. */
    public const val NORMAL_UNIQUE_DENOMINATOR: Double = 9.1
    public const val HARD_UNIQUE_DENOMINATOR: Double = 7.7

    /** The team's base unique chance for [mode] ("entry", "normal", "hard") before the death reduction. */
    public fun baseUniqueChance(mode: String): Double =
        when (mode) {
            "normal" -> 1.0 / NORMAL_UNIQUE_DENOMINATOR
            "hard" -> 1.0 / HARD_UNIQUE_DENOMINATOR
            else -> 0.0
        }

    /**
     * Brief 1.3: 14 MVP points, split by top damage per boss: Maiden 2, Bloat 2, Nylocas 1, Sotetseg 1, Xarpus 2 and
     * each of Verzik's three phases 2.
     */
    public val MVP_POINTS: Map<String, Int> =
        linkedMapOf(
            "maiden" to 2,
            "bloat" to 2,
            "nylocas" to 1,
            "sotetseg" to 1,
            "xarpus" to 2,
            "verzik_p1" to 2,
            "verzik_p2" to 2,
            "verzik_p3" to 2,
        )

    /** Brief 1.3: unique weights in Normal (out of 19); Hard drops the hilt to 7 (out of 18). */
    public fun uniqueWeights(mode: String): Map<Int, Int> =
        linkedMapOf(
            AVERNIC_DEFENDER_HILT to if (mode == "hard") HARD_HILT_WEIGHT else NORMAL_HILT_WEIGHT,
            Objs.GHRAZI_RAPIER to 2,
            Objs.SANGUINESTI_STAFF to 2,
            Objs.JUSTICIAR_FACEGUARD to 2,
            Objs.JUSTICIAR_CHESTGUARD to 2,
            Objs.JUSTICIAR_LEG_GUARDS to 2,
            Objs.SCYTHE_OF_VITUR to 1,
        )

    /** The MVP of a raid: the most MVP points (ties: the earlier in [points]' order). */
    public fun mvp(points: Map<String, Int>): String? = points.maxByOrNull { it.value }?.key

    /** The Avernic defender hilt (cache gameval `infernal_defender_hilt`, obj 22477; the wiki infobox id). */
    public const val AVERNIC_DEFENDER_HILT: Int = Objs.INFERNAL_DEFENDER_HILT

    /** Lil' Zik (gameval `verzikpet`). */
    public const val LIL_ZIK: Int = Objs.VERZIKPET

    private const val NORMAL_HILT_WEIGHT = 8
    private const val HARD_HILT_WEIGHT = 7
}
