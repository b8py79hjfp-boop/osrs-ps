@file:Suppress("MagicNumber") // data table: levels, xp, tiles from the OSRS Wiki altar pages and cache 2757 (docs/mechanics/runecraft.md)

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** Which shared talisman and gold tiara reach an altar: the elemental ones (air, water, earth, fire) or the catalytic ones. */
internal enum class TalismanGroup(val talisman: Int, val tiara: Int) {
    ELEMENTAL(Objs.ELEMENTAL_TALISMAN, Objs.TIARA_ELEMENTAL),
    CATALYTIC(Objs.CATALYTIC_TALISMAN, Objs.TIARA_CATALYTIC),
}

/**
 * One runic altar and the ruins that lead to it (docs/mechanics/runecraft.md).
 *
 * - Rune: [rune] needs [level] and gives [xp] per essence; rune essence works unless [pureOnly]. [multiples] are the
 *   Runecraft levels for 2x, 3x, ... runes per essence (Wiki "Runecraft", Multiple runes).
 * - Ruins: [ruinLocs] are the multi-loc parent and its two children; [ruin] is the tile of the one the exit portal returns
 *   to (the Wiki's map pin); [noTallyVarbit] makes the client offer "Enter" (1) or not (0).
 * - Tiaras: [talisman] and [tiara]; [tiaraXp] for the plain one, [groupTiaraXp] for the elemental / catalytic one.
 * - Inside: [altarLoc] at [altar], and the exit [portalLoc] placed at each of [portals] (entering lands beside one).
 */
@Suppress("LongParameterList") // one row of the altar table below
internal class Temple(
    val name: String,
    val rune: Int,
    val level: Int,
    val xp: Double,
    val pureOnly: Boolean,
    val multiples: List<Int>,
    val ruinLocs: List<Int>,
    val ruin: CoordGrid,
    val noTallyVarbit: Int,
    val talisman: Int,
    val tiara: Int,
    val group: TalismanGroup,
    val tiaraXp: Double,
    val groupTiaraXp: Double,
    val altarLoc: Int,
    val altar: CoordGrid,
    val portalLoc: Int,
    val portals: List<CoordGrid>,
) {
    /** Whether wearing [head] (or [cape]) lets the player left-click "Enter" on the ruins (Wiki "Runic altar"). */
    fun grantsEntry(head: Int, cape: Int): Boolean =
        head == tiara || head == group.tiara || cape == Objs.SKILLCAPE_RUNECRAFTING || cape == Objs.SKILLCAPE_RUNECRAFTING_TRIMMED

    /** Whether using [obj] on the ruins takes the player in: the altar's own talisman or its group's (Wiki "Talisman"). */
    fun opensWith(obj: Int): Boolean = obj == talisman || obj == group.talisman

    /** Runes made from one essence at Runecraft [level] (Wiki "Runecraft", Multiple runes). */
    fun runesPerEssence(level: Int): Int = 1 + multiples.count { level >= it }
}

internal object RunecraftData {
    /** Worn slot of the head (cache wear position 0): where tiaras go. */
    const val HEAD_SLOT = 0

    /** Worn slot of the cape (cache wear position 1): the Runecraft cape. */
    const val CAPE_SLOT = 1

    /** Rune essence and pure essence share their ids with the cache names `blankrune` and `blankrune_high`. */
    const val RUNE_ESSENCE = Objs.BLANKRUNE
    const val PURE_ESSENCE = Objs.BLANKRUNE_HIGH

    private fun at(x: Int, z: Int, level: Int = 0) = CoordGrid(x, z, level)

    val TEMPLES: List<Temple> =
        listOf(
            Temple(
                "air",
                Objs.AIRRUNE,
                1,
                5.0,
                false,
                listOf(11, 22, 33, 44, 55, 66, 77, 88, 99),
                listOf(Locs.AIRTEMPLE_RUINED, Locs.AIRTEMPLE_RUINED_OLD, Locs.AIRTEMPLE_RUINED_NEW),
                at(2984, 3291),
                Varbits.RC_NO_TALLY_REQUIRED_AIR,
                Objs.AIR_TALISMAN,
                Objs.TIARA_AIR,
                TalismanGroup.ELEMENTAL,
                25.0,
                40.0,
                Locs.AIR_ALTAR,
                at(2843, 4833),
                Locs.AIRTEMPLE_EXIT_PORTAL,
                listOf(at(2841, 4828)),
            ),
            Temple(
                "mind",
                Objs.MINDRUNE,
                2,
                5.5,
                false,
                listOf(14, 28, 42, 56, 70, 84, 98),
                listOf(Locs.MINDTEMPLE_RUINED, Locs.MINDTEMPLE_RUINED_OLD, Locs.MINDTEMPLE_RUINED_NEW),
                at(2981, 3513),
                Varbits.RC_NO_TALLY_REQUIRED_MIND,
                Objs.MIND_TALISMAN,
                Objs.TIARA_MIND,
                TalismanGroup.CATALYTIC,
                27.5,
                60.0,
                Locs.MIND_ALTAR,
                at(2785, 4840),
                Locs.MINDTEMPLE_EXIT_PORTAL,
                listOf(at(2793, 4827)),
            ),
            Temple(
                "water",
                Objs.WATERRUNE,
                5,
                6.0,
                false,
                listOf(19, 38, 57, 76, 95),
                listOf(Locs.WATERTEMPLE_RUINED, Locs.WATERTEMPLE_RUINED_OLD, Locs.WATERTEMPLE_RUINED_NEW),
                at(3184, 3164),
                Varbits.RC_NO_TALLY_REQUIRED_WATER,
                Objs.WATER_TALISMAN,
                Objs.TIARA_WATER,
                TalismanGroup.ELEMENTAL,
                30.0,
                40.0,
                Locs.WATER_ALTAR,
                at(2715, 4835),
                Locs.WATERTEMPLE_EXIT_PORTAL,
                listOf(at(2727, 4832)),
            ),
            Temple(
                "earth",
                Objs.EARTHRUNE,
                9,
                6.5,
                false,
                listOf(26, 52, 78, 104),
                listOf(Locs.EARTHTEMPLE_RUINED, Locs.EARTHTEMPLE_RUINED_OLD, Locs.EARTHTEMPLE_RUINED_NEW),
                at(3305, 3473),
                Varbits.RC_NO_TALLY_REQUIRED_EARTH,
                Objs.EARTH_TALISMAN,
                Objs.TIARA_EARTH,
                TalismanGroup.ELEMENTAL,
                32.5,
                40.0,
                Locs.EARTH_ALTAR,
                at(2657, 4840),
                Locs.EARTHTEMPLE_EXIT_PORTAL,
                listOf(at(2655, 4829)),
            ),
            Temple(
                "fire",
                Objs.FIRERUNE,
                14,
                7.0,
                false,
                listOf(35, 70),
                listOf(Locs.FIRETEMPLE_RUINED, Locs.FIRETEMPLE_RUINED_OLD, Locs.FIRETEMPLE_RUINED_NEW),
                at(3312, 3254),
                Varbits.RC_NO_TALLY_REQUIRED_FIRE,
                Objs.FIRE_TALISMAN,
                Objs.TIARA_FIRE,
                TalismanGroup.ELEMENTAL,
                35.0,
                40.0,
                Locs.FIRE_ALTAR,
                at(2584, 4837),
                Locs.FIRETEMPLE_EXIT_PORTAL,
                listOf(at(2574, 4850)),
            ),
            Temple(
                "body",
                Objs.BODYRUNE,
                20,
                7.5,
                false,
                listOf(46, 92),
                listOf(Locs.BODYTEMPLE_RUINED, Locs.BODYTEMPLE_RUINED_OLD, Locs.BODYTEMPLE_RUINED_NEW),
                at(3052, 3444),
                Varbits.RC_NO_TALLY_REQUIRED_BODY,
                Objs.BODY_TALISMAN,
                Objs.TIARA_BODY,
                TalismanGroup.CATALYTIC,
                37.5,
                60.0,
                Locs.BODY_ALTAR,
                at(2522, 4839),
                Locs.BODYTEMPLE_EXIT_PORTAL,
                listOf(at(2521, 4833)),
            ),
            Temple(
                "cosmic",
                Objs.COSMICRUNE,
                27,
                8.0,
                true,
                listOf(59),
                listOf(Locs.COSMICTEMPLE_RUINED, Locs.COSMICTEMPLE_RUINED_OLD, Locs.COSMICTEMPLE_RUINED_NEW),
                at(2407, 4376),
                Varbits.RC_NO_TALLY_REQUIRED_COSMIC,
                Objs.COSMIC_TALISMAN,
                Objs.TIARA_COSMIC,
                TalismanGroup.CATALYTIC,
                40.0,
                60.0,
                Locs.COSMIC_ALTAR,
                at(2141, 4832),
                Locs.COSMICTEMPLE_EXIT_PORTAL,
                listOf(at(2121, 4833), at(2142, 4812), at(2142, 4854), at(2163, 4833)),
            ),
            Temple(
                "chaos",
                Objs.CHAOSRUNE,
                35,
                8.5,
                true,
                listOf(74),
                listOf(Locs.CHAOSTEMPLE_RUINED, Locs.CHAOSTEMPLE_RUINED_OLD, Locs.CHAOSTEMPLE_RUINED_NEW),
                at(3059, 3590),
                Varbits.RC_NO_TALLY_REQUIRED_CHAOS,
                Objs.CHAOS_TALISMAN,
                Objs.TIARA_CHAOS,
                TalismanGroup.CATALYTIC,
                37.5,
                42.5,
                Locs.CHAOS_ALTAR,
                at(2270, 4841),
                Locs.CHAOSTEMPLE_EXIT_PORTAL,
                listOf(at(2273, 4856, 3)),
            ),
            Temple(
                "nature",
                Objs.NATURERUNE,
                44,
                9.0,
                true,
                listOf(91),
                listOf(Locs.NATURETEMPLE_RUINED, Locs.NATURETEMPLE_RUINED_OLD, Locs.NATURETEMPLE_RUINED_NEW),
                at(2868, 3018),
                Varbits.RC_NO_TALLY_REQUIRED_NATURE,
                Objs.NATURE_TALISMAN,
                Objs.TIARA_NATURE,
                TalismanGroup.CATALYTIC,
                45.0,
                42.5,
                Locs.NATURE_ALTAR,
                at(2399, 4840),
                Locs.NATURETEMPLE_EXIT_PORTAL,
                listOf(at(2400, 4834)),
            ),
            Temple(
                "law",
                Objs.LAWRUNE,
                54,
                9.5,
                true,
                listOf(95),
                listOf(Locs.LAWTEMPLE_RUINED, Locs.LAWTEMPLE_RUINED_OLD, Locs.LAWTEMPLE_RUINED_NEW),
                at(2857, 3380),
                Varbits.RC_NO_TALLY_REQUIRED_LAW,
                Objs.LAW_TALISMAN,
                Objs.TIARA_LAW,
                TalismanGroup.CATALYTIC,
                47.5,
                42.5,
                Locs.LAW_ALTAR,
                at(2463, 4831),
                Locs.LAWTEMPLE_EXIT_PORTAL,
                listOf(at(2464, 4817)),
            ),
            Temple(
                "death",
                Objs.DEATHRUNE,
                65,
                10.0,
                true,
                listOf(99),
                listOf(Locs.DEATHTEMPLE_RUINED, Locs.DEATHTEMPLE_RUINED_OLD, Locs.DEATHTEMPLE_RUINED_NEW),
                at(1859, 4638),
                Varbits.RC_NO_TALLY_REQUIRED_DEATH,
                Objs.DEATH_TALISMAN,
                Objs.TIARA_DEATH,
                TalismanGroup.CATALYTIC,
                50.0,
                42.5,
                Locs.DEATH_ALTAR,
                at(2204, 4835),
                Locs.DEATHTEMPLE_EXIT_PORTAL,
                listOf(at(2208, 4829)),
            ),
            Temple(
                "blood",
                Objs.BLOODRUNE,
                77,
                10.5,
                true,
                emptyList(),
                listOf(Locs.BLOODTEMPLE_RUINED, Locs.BLOODTEMPLE_RUINED_0OP, Locs.BLOODTEMPLE_RUINED_1OP),
                at(3560, 9780),
                Varbits.RC_NO_TALLY_REQUIRED_BLOOD,
                Objs.BLOOD_TALISMAN,
                Objs.TIARA_BLOOD,
                TalismanGroup.CATALYTIC,
                52.5,
                42.5,
                Locs.BLOOD_ALTAR,
                at(3231, 4831),
                Locs.BLOODTEMPLE_EXIT_PORTAL,
                listOf(at(3224, 4832), at(3240, 4832)),
            ),
            Temple(
                "wrath",
                Objs.WRATHRUNE,
                95,
                8.0,
                true,
                emptyList(),
                listOf(Locs.WRATHTEMPLE_RUINED, Locs.WRATHTEMPLE_RUINED_0OP, Locs.WRATHTEMPLE_RUINED_1OP),
                at(2445, 2824),
                Varbits.RC_NO_TALLY_REQUIRED_WRATH,
                Objs.WRATH_TALISMAN,
                Objs.TIARA_WRATH,
                TalismanGroup.CATALYTIC,
                52.5,
                60.0,
                Locs.WRATH_ALTAR,
                at(2334, 4833, 1),
                Locs.WRATHTEMPLE_EXIT_PORTAL,
                listOf(at(2335, 4825, 1)),
            ),
        )
}
