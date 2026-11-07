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

/**
 * A two-way pipe, crevice or underwall tunnel (Wiki "Shortcut", contortion rows; docs/mechanics/agility.md AG9): [locs] with option 1,
 * [level] and [xp] from the shortcut's page, crossed over [ticks] ticks (one per tile, OPEN-AG1). The ends [a] and [b] are the first
 * free tiles (cache collision) beyond the two mouths of the loc placements (OPEN-AG9: not checked on a client).
 */
private fun squeeze(verb: String, level: Int, xp: Double, locs: List<Int>, anim: Int, a: CoordGrid, b: CoordGrid) =
    Shortcut(verb, level, xp, locs, 1, anim, a.chebyshev(b), a, b)

/** Wiki "Obstacle pipe (Barbarian Outpost)": level 35, 10 xp; loc 20210 at (2552, 3559) runs north-south to the course. */
private val BARBARIAN_PIPE =
    squeeze("squeeze through the pipe", 35, 10.0, listOf(20210), Seqs.HUMAN_PIPESQUEEZE, CoordGrid(2552, 3561, 0), CoordGrid(2552, 3558, 0))

/** Wiki "Obstacle pipe (Edgeville Dungeon)": level 51, 10 xp, to the Varrock Sewers; pipe locs at (3150, 9906) and (3153, 9906). */
private val EDGEVILLE_PIPE =
    squeeze("squeeze through the pipe", 51, 10.0, listOf(16511), Seqs.HUMAN_PIPESQUEEZE, CoordGrid(3149, 9906, 0), CoordGrid(3155, 9906, 0))

/** Wiki "Obstacle pipe (Taverley Dungeon)": level 70, 10 xp; pipe ends at (2887, 9799) and (2890, 9799). */
private val TAVERLEY_PIPE =
    squeeze("squeeze through the pipe", 70, 10.0, listOf(16509), Seqs.HUMAN_PIPESQUEEZE, CoordGrid(2886, 9799, 0), CoordGrid(2892, 9799, 0))

/** Wiki "Narrow crevice (Dwarven Mine)": level 42, 0 xp; crevice locs at (3029, 9806) and (3034, 9806). */
private val DWARVEN_CREVICE =
    squeeze(
        "squeeze through the crevice",
        42,
        0.0,
        listOf(16543),
        Seqs.AGILTY_SHORTCUT_ENTER_HOLE,
        CoordGrid(3027, 9806, 0),
        CoordGrid(3036, 9806, 0),
    )

/** Wiki "Crevice (Fremennik Slayer Dungeon)": level 61, 0 xp; crevice locs at (2731, 10008) and (2734, 10008). */
private val FREMENNIK_CREVICE =
    squeeze(
        "squeeze through the crevice",
        61,
        0.0,
        listOf(16539),
        Seqs.AGILTY_SHORTCUT_ENTER_HOLE,
        CoordGrid(2729, 10008, 0),
        CoordGrid(2736, 10008, 0),
    )

/** Wiki "Underwall tunnel (Yanille)": level 16, 0 xp; wall 16519 at (2575, 3108) and hole 16520 at (2575, 3111). */
private val YANILLE_UNDERWALL =
    squeeze(
        "use this shortcut",
        16,
        0.0,
        listOf(16519, 16520),
        Seqs.AGILTY_SHORTCUT_ENTER_HOLE,
        CoordGrid(2575, 3107, 0),
        CoordGrid(2575, 3112, 0),
    )

internal val SHORTCUTS: List<Shortcut> =
    listOf(
        FALADOR_UNDERWALL,
        DRAYNOR_UNDERWALL,
        VARROCK_FENCE,
        BARBARIAN_PIPE,
        EDGEVILLE_PIPE,
        TAVERLEY_PIPE,
        DWARVEN_CREVICE,
        FREMENNIK_CREVICE,
        YANILLE_UNDERWALL,
    )
