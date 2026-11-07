@file:Suppress("MagicNumber") // data table: Wiki "Shortcut" levels, xp; cache 2757 tiles (docs/mechanics/agility.md)

package tp.content.skills

import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * One two-way agility shortcut (docs/mechanics/agility.md AG7): the [locs] that start it with option [op] (the Wiki
 * "Shortcut" list gives the [level] and [xp]), crossed with [anim] over [ticks] ticks between the tiles [a] and [b].
 */
internal class Shortcut(
    val verb: String,
    val level: Int,
    val xp: Double,
    val locs: List<Int>,
    val op: Int,
    val anim: Int,
    val ticks: Int,
    val a: CoordGrid,
    val b: CoordGrid,
)

/** Wiki "Underwall tunnel (Falador)": level 26, xp 0; the two holes at (2948, 3310) and (2948, 3312) lead under the wall. */
private val FALADOR_UNDERWALL =
    Shortcut(
        "use this shortcut",
        26,
        0.0,
        listOf(16527, 16528),
        1,
        Seqs.AGILTY_SHORTCUT_ENTER_HOLE,
        4,
        CoordGrid(2948, 3309, 0),
        CoordGrid(2948, 3313, 0),
    )

/** Wiki "Underwall tunnel (Draynor Village)": level 42, xp 0; the holes at (3067, 3257) and (3069, 3257). */
private val DRAYNOR_UNDERWALL =
    Shortcut(
        "use this shortcut",
        42,
        0.0,
        listOf(19032, 19036),
        1,
        Seqs.AGILTY_SHORTCUT_ENTER_HOLE,
        4,
        CoordGrid(3066, 3257, 0),
        CoordGrid(3070, 3257, 0),
    )

/** Wiki "Fence (Varrock)": level 13, xp 0; the fence at (3240, 3335) is the south edge of that tile. */
private val VARROCK_FENCE =
    Shortcut(
        "jump this fence",
        13,
        0.0,
        listOf(16518),
        1,
        Seqs.HUMAN_LOWWALL,
        2,
        CoordGrid(3240, 3335, 0),
        CoordGrid(3240, 3334, 0),
    )

internal val SHORTCUTS: List<Shortcut> = listOf(FALADOR_UNDERWALL, DRAYNOR_UNDERWALL, VARROCK_FENCE)
