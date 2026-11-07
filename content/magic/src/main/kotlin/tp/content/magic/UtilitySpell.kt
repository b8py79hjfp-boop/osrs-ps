package tp.content.magic

/**
 * A non-combat spell cast from the spellbook tab (OSRS Wiki spell pages, one row per spell in
 * docs/mechanics/magic-utility.md). [ticks] is the Wiki "speed": the next spell can't be cast sooner (MU2). [seq] and
 * [spot] are the cast animation and graphic (cache symbols, OPEN-MU3); [spot] -1 = none.
 */
@Suppress("LongParameterList") // one data row per spell
internal class UtilitySpell(
    val component: Int,
    val book: Int,
    val level: Int,
    val xp: Double,
    val runes: List<Pair<Int, Int>>,
    val ticks: Int,
    val seq: Int,
    val spot: Int,
)
