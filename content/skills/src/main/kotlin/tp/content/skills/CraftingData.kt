@file:Suppress("MagicNumber") // data tables: cache ids, gem odds and xp sourced in docs/mechanics/crafting.md

package tp.content.skills

import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * How a semi-precious gem can be crushed instead of cut: the chance to cut it is `((level - 1) * [slope] / 98 + [base]) / 256`
 * (Wiki "Uncut opal" / "Uncut jade" / "Uncut red topaz", "Cutting chance"); a crush gives a crushed gem and [xp].
 */
internal class Crush(val slope: Int, val base: Int, val xp: Double)

/** A gem: the cutting animation and, for the semi-precious ones, how it crushes. */
internal class Gem(val anim: Int, val crush: Crush?)

internal object CraftingData {
    /** Spinning wheels: cache locs named "Spinning wheel" with the op "Spin" (op 2). */
    val SPINNING_WHEELS: IntArray = intArrayOf(4309, 8748, 14889, 20365, 21304, 25824, 26143, 30934, 40735, 55330, 56964, 58649)

    /** Potter's wheels: cache locs named "Potter's Wheel" with the op "Use" (op 1). */
    val POTTERS_WHEELS: IntArray = intArrayOf(4310, 14887, 20375, 39395, 40733, 50732, 58653)

    /** Pottery ovens: cache locs named "Pottery Oven" with the op "Fire" (op 1). */
    val POTTERY_OVENS: IntArray = intArrayOf(4308, 11601, 14888, 39396, 58655)

    /** Uncut gem -> cutting animation and crush odds (Wiki "Crafting" Gems table: crush xp 3.8 / 5 / 6.3). */
    val GEMS: Map<Int, Gem> =
        mapOf(
            Objs.UNCUT_OPAL to Gem(Seqs.HUMAN_OPALCUTTING, Crush(122, 129, 3.8)),
            Objs.UNCUT_JADE to Gem(Seqs.HUMAN_JADECUTTING, Crush(145, 101, 5.0)),
            Objs.UNCUT_RED_TOPAZ to Gem(Seqs.HUMAN_REDTOPAZCUTTING, Crush(150, 91, 6.3)),
            Objs.UNCUT_SAPPHIRE to Gem(Seqs.HUMAN_SAPPHIRECUTTING, null),
            Objs.UNCUT_EMERALD to Gem(Seqs.HUMAN_EMERALDCUTTING, null),
            Objs.UNCUT_RUBY to Gem(Seqs.HUMAN_RUBYCUTTING, null),
            Objs.UNCUT_DIAMOND to Gem(Seqs.HUMAN_DIAMONDCUTTING, null),
            Objs.UNCUT_DRAGONSTONE to Gem(Seqs.HUMAN_DRAGONSTONECUTTING, null),
            Objs.UNCUT_ONYX to Gem(Seqs.HUMAN_ONYXCUTTING, null),
        )

    /** Cache seq 894 is the spinning animation (RuneLite `AnimationID.CRAFTING_SPINNING`); the symbol table names it by another gameval. */
    const val SPINNING_ANIM = 894

    /** Chances out of this many in the gem crush formula (Wiki "Uncut opal": `(...) / 256`). */
    const val GEM_ODDS = 256

    /** Divisor of the level slope in the gem crush formula (Wiki "Uncut opal": `(level - 1) * 122 / 98 + 129`). */
    const val GEM_SLOPE_DIVISOR = 98.0

    /** Actions one spool of thread lasts (Wiki "Thread": "Each thread spool has 5 uses"). */
    const val THREAD_USES = 5

    /** Every leather item takes 3 ticks (Wiki "Pay-to-play Crafting training": "created in 3 ticks"). */
    const val LEATHER_TICKS = 3

    /** Spinning: the first item after 2 ticks, then 3 (Wiki "Crossbow string" note; "Bow string": 28 in 50.2 s). */
    const val SPIN_FIRST_TICKS = 2
    const val SPIN_TICKS = 3
}
