@file:Suppress("MagicNumber") // data tables: cache ids, levels and xp sourced in docs/mechanics/barbarian-training.md

package tp.content.skills

import tp.symbols.Objs

/**
 * A barbarian mix (Wiki "Barbarian Training" > Barbarian potions): [potion] is the two-dose potion, [mix] the two-dose mix it
 * becomes with one roe (or, when [caviarOnly], one caviar), [level] Herblore, [xp] experience. Ids are the cache's `<potion>(2)` and
 * `<mix>(2)` items (the lowest id of each name).
 */
internal class Mix(val potion: Int, val mix: Int, val level: Int, val xp: Double, val caviarOnly: Boolean)

/** One spear and hasta pair of the barbarian anvil (Wiki "Barbarian Training" > Making the weapons). */
internal class Spear(val bar: Int, val logs: Int, val spear: Int, val hasta: Int, val level: Int, val xp: Double)

internal object BarbarianData {
    /** The mixes in Wiki table order; roe works up to the restore mix, every later one needs caviar. */
    val MIXES: List<Mix> =
        listOf(
            Mix(123, 11429, 4, 8.0, false), // attack
            Mix(177, 11433, 6, 12.0, false), // antipoison
            Mix(4846, 11437, 9, 14.0, false), // Relicym's
            Mix(117, 11443, 14, 17.0, false), // strength
            Mix(129, 11449, 24, 21.0, false), // restore
            Mix(3012, 11453, 29, 23.0, true), // energy
            Mix(135, 11457, 33, 25.0, true), // defence
            Mix(3036, 11461, 37, 27.0, true), // agility
            Mix(9743, 11445, 40, 28.0, true), // combat
            Mix(141, 11465, 42, 29.0, true), // prayer
            Mix(147, 11469, 47, 33.0, true), // superattack
            Mix(183, 11473, 51, 35.0, true), // anti-poison supermix
            Mix(153, 11477, 53, 38.0, true), // fishing
            Mix(3020, 11481, 56, 39.0, true), // super energy
            Mix(10002, 11517, 58, 40.0, true), // hunting
            Mix(159, 11485, 59, 42.0, true), // super strength
            Mix(9023, 11489, 61, 43.0, true), // magic essence
            Mix(3028, 11493, 67, 48.0, true), // super restore
            Mix(165, 11497, 71, 50.0, true), // super defence
            Mix(5947, 11501, 74, 52.0, true), // antidote+
            Mix(2456, 11505, 75, 53.0, true), // antifire
            Mix(171, 11509, 80, 54.0, true), // ranging
            Mix(3044, 11513, 83, 57.0, true), // magic
            Mix(191, 11521, 85, 58.0, true), // Zamorak
            Mix(12629, 12633, 86, 60.0, true), // stamina
            Mix(11955, 11960, 91, 61.0, true), // extended antifire
            Mix(26344, 26350, 92, 63.0, true), // ancient
            Mix(21984, 21994, 98, 70.0, true), // super antifire
            Mix(22215, 22221, 99, 78.0, true), // extended super antifire
        )

    /** The attack mix (2 doses) Otto asks for (Wiki "Attack mix"). */
    val ATTACK_MIX: Int = MIXES.first().mix

    /**
     * Spears and hastae by metal (Wiki "Barbarian Training" > Making the weapons; hasta ids are the cache's `<metal> hasta` items,
     * 11367 + 2 per tier). The Zamorakian pair is a conversion by Otto, not a smithing recipe.
     */
    val SPEARS: List<Spear> =
        listOf(
            Spear(Objs.BRONZE_BAR, Objs.LOGS, Objs.BRONZE_SPEAR, Objs.BRUT_BRONZE_SPEAR, 5, 25.0),
            Spear(Objs.IRON_BAR, Objs.OAK_LOGS, Objs.IRON_SPEAR, Objs.BRUT_IRON_SPEAR, 20, 50.0),
            Spear(Objs.STEEL_BAR, Objs.WILLOW_LOGS, Objs.STEEL_SPEAR, Objs.BRUT_STEEL_SPEAR, 35, 75.0),
            Spear(Objs.MITHRIL_BAR, Objs.MAPLE_LOGS, Objs.MITHRIL_SPEAR, Objs.BRUT_MITHRIL_SPEAR, 55, 100.0),
            Spear(Objs.ADAMANTITE_BAR, Objs.YEW_LOGS, Objs.ADAMANT_SPEAR, Objs.BRUT_ADAMANT_SPEAR, 75, 125.0),
            Spear(Objs.RUNITE_BAR, Objs.MAGIC_LOGS, Objs.RUNE_SPEAR, Objs.BRUT_RUNE_SPEAR, 90, 150.0),
        )
}
