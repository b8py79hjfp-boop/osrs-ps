@file:Suppress("MagicNumber") // data tables: Wiki "Volcanic Mine" and subpages, client scripts 2020-2027 (docs/mechanics/volcanic-mine.md)

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * One ore fragment (Wiki "Volcanic Mine" > Boulder): the item, the Mining level to receive it, the points and Mining xp one gives, and the
 * success chart's low / high rates (Wiki "Skilling success chart" of the boulder; the last, iron, is the default when every
 * other roll fails).
 */
internal class Fragment(val obj: Int, val level: Int, val points: Int, val xp: Double, val low: Int, val high: Int)

/** A shop line of Petrified Pete's Ore Shop (Wiki "Petrified Pete's Ore Shop" > Stock): [obj] at [cost] points; [noted] sold noted. */
internal class VmReward(val name: String, val obj: Int, val cost: Int, val noted: Boolean)

internal object VolcanicMineData {
    /** Wiki "Volcanic Mine": 50 Mining to enter, 30 numulite each time or 3,000 once for permanent access. */
    const val MINING_LEVEL = 50
    const val ENTRY_FEE = 30
    const val PERMANENT_FEE = 3000

    /** Wiki "Volcanic Mine" > Preparation: 30 s to prepare (50 ticks), then 10 minutes (1,000 ticks) to mine and leave. */
    const val PREPARATION_TICKS = 50
    const val GAME_TICKS = 1000

    /** Wiki "Volcanic Mine" > Mine stability: the mine shifts after 5 minutes (500 ticks), re-randomising the pressures. */
    const val SHIFT_TICKS = 500

    /**
     * Wiki "Volcanic Mine" > Eruption: with less than 30 s (50 ticks) left nothing can be mined or fixed, and the stability is
     * held at 5%.
     */
    const val CLOSING_TICKS = 50
    const val CLOSING_STABILITY = 5

    /** Wiki "Volcanic Mine" > Mine stability: pressures should stay between 40% and 60%. */
    const val SAFE_LOW = 40
    const val SAFE_HIGH = 60

    /**
     * The three gas chambers, as the HUD names them (A west, B north, C east), their loc tiles (cache locs 31043 / 31044, level
     * 1) and large rock tiles.
     */
    val CHAMBERS = arrayOf(CoordGrid(3785, 10156, 1), CoordGrid(3813, 10172, 1), CoordGrid(3828, 10155, 1))
    val ROCKS = arrayOf(CoordGrid(3784, 10158, 1), CoordGrid(3815, 10170, 1), CoordGrid(3831, 10161, 1))

    /** Wiki "Volcanic Mine" > Strategy: blocking or unblocking a chamber earns 50 points, up to 300 per player. */
    const val CHAMBER_POINTS = 50
    const val CHAMBER_POINTS_CAP = 300

    /** Wiki "Blocked volcanic chamber" (Mining info): clearing a blockage gives 25 Mining xp. */
    const val UNBLOCK_XP = 25.0

    /** Wiki "Boulder (Volcanic Mine)": a boulder carried to the end falls into the vortex and every player gets 100 more points. */
    const val COMPLETION_POINTS = 100

    /** Wiki "Volcanic Mine" > Rewards: 3.8 Mining xp per point when leaving alive by the exit. */
    const val XP_PER_POINT = 3.8

    /** Wiki "Volcanic Mine": the points a player can keep are capped at 2^21 - 1. */
    const val POINTS_CAP = 2_097_151

    // ---- the boulder

    /**
     * Boulder stages (Wiki "Boulder (Volcanic Mine)"): the loc of each, giant attached .. small, and the odds (1 in N) of a
     * fragment per success.
     */
    val STAGE_LOCS =
        intArrayOf(
            Locs.FOSSIL_MINING_BOULDER_ATTACHED,
            Locs.FOSSIL_MINING_BOULDER_GIANT,
            Locs.FOSSIL_MINING_BOULDER_LARGE,
            Locs.FOSSIL_MINING_BOULDER_MEDIUM,
            Locs.FOSSIL_MINING_BOULDER_SMALL,
        )
    val STAGE_ONE_IN = intArrayOf(20, 12, 8, 5, 3)
    const val STAGES = 5

    /**
     * Wiki "Boulder (Volcanic Mine)" map pins: the channel x of the central, western and eastern boulders; z of each stage; the
     * small stage's z differs per channel.
     */
    val CHANNEL_X = intArrayOf(3807, 3791, 3823)
    val STAGE_Z = intArrayOf(10127, 10136, 10149, 10172, 10200)
    val SMALL_Z = intArrayOf(10200, 10196, 10198)

    /** Wiki "Volcanic Mine" > Boulder: one boulder below 10 players, a second from 10, a third from 20. */
    fun boulders(players: Int): Int =
        if (players >= 20) {
            3
        } else if (players >= 10) {
            2
        } else {
            1
        }

    /**
     * Wiki "Boulder (Volcanic Mine)": about 40 hitpoints per player per stage, 60 per player for the final small boulder (OPEN-
     * VM6 on the split).
     */
    const val STAGE_HP_PER_PLAYER = 40
    const val SMALL_HP_PER_PLAYER = 60

    /**
     * Wiki "Boulder (Volcanic Mine)": mining a boulder without a fragment still gives 1 point and 5 xp ("You chip off a part of
     * the boulder.").
     */
    const val CHIP_POINTS = 1
    const val CHIP_XP = 5.0

    /** Wiki "Boulder (Volcanic Mine)" > Mining info (cascade from runite down, iron by default). */
    val FRAGMENTS: List<Fragment> =
        listOf(
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_RUNITE, 85, 70, 150.0, -10, 80),
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_ADAMANTITE, 70, 55, 100.0, -90, 90),
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_MITHRIL, 55, 44, 60.0, -75, 150),
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_GOLD, 50, 34, 40.0, -40, 160),
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_COAL, 50, 25, 30.0, -20, 170),
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_SILVER, 50, 20, 20.0, 0, 185),
            Fragment(Objs.FOSSIL_MINE_FRAGMENT_IRON, 50, 15, 10.0, 0, 510),
        )

    /**
     * Wiki "Boulder (Volcanic Mine)" > Conditional drops: a fragment is followed by a 1 in 20 calcite and a 1 in 20
     * pyrophosphite (1 to 4 each).
     */
    const val MINERAL_ONE_IN = 20
    const val MINERAL_MAX = 4

    /**
     * Wiki "Template:FossilDropLines" (fossil = 5, numulite = 4, access = 9/10): if no mineral dropped, out of 3,500 numulite 5
     * to 8 takes the first
     * 900, then unidentified small (50), medium (25), large (20) and rare (5) fossils; the rest is nothing.
     */
    const val FOSSIL_ROLL = 3500
    const val NUMULITE_ROLL = 900
    const val NUMULITE_MIN = 5
    const val NUMULITE_MAX = 8
    val FOSSILS = intArrayOf(Objs.FOSSIL_SMALL_UNID, Objs.FOSSIL_MEDIUM_UNID, Objs.FOSSIL_LARGE_UNID, Objs.FOSSIL_RARE_UNID)
    val FOSSIL_SHARES = intArrayOf(50, 25, 20, 5)

    // ---- the heat-proof vessel (Wiki "Heat-proof vessel")

    /** The vessel's uses per game: 36, or 72 with the upgrade (OPEN-VM8: the Volcanic Mine page says 35 and 70). */
    const val WATER_USES = 36
    const val WATER_USES_UPGRADED = 72

    /** Hardened lava melts after 90 s (150 ticks), turns orange 45 s before that (after 75 ticks) and white for the last 10 ticks. */
    const val HARDEN_TICKS = 150
    const val HARDEN_ORANGE_AT = 75
    const val HARDEN_WHITE_AT = 140

    // ---- OPEN: dynamics the Wiki does not give (docs/mechanics/volcanic-mine.md)

    /** OPEN-VM2: a blocked chamber gains 1% pressure and an open one loses 1% every this many ticks. */
    const val PRESSURE_STEP_TICKS = 5

    /** OPEN-VM3: the stability loses 1% every this many ticks for each chamber outside [SAFE_LOW]..[SAFE_HIGH]; it never regains. */
    const val STABILITY_STEP_TICKS = 20

    /** OPEN-VM4: ticks before an emptied rock pile gives another large rock. */
    const val ROCK_RESPAWN_TICKS = 10

    /** OPEN-VM6: a small boulder takes this many times its base damage per player not mining it, split over the miners. */
    const val SMALL_BOOST = 2

    /** A player counts as mining the boulder if they swung within this many ticks. */
    const val MINING_RECENT_TICKS = 6

    // ---- places

    /** Cache locs: the vents (31040) and their chambers by x; the eastern / western stairs tops are on level 3 and bottoms on level 1. */
    fun chamberOfVent(vent: CoordGrid): Int =
        if (vent.x < 3795) {
            0
        } else if (vent.x < 3820) {
            1
        } else {
            2
        }

    /**
     * OPEN-VM1: the mine's map squares (59,158 and 59,159); the lobby deck, the tiles below the stairs and the exit outside are
     * read off the cache.
     */
    val MINE_X = 3770..3840
    val MINE_Z = 10100..10215
    val LOBBY = CoordGrid(3807, 10162, 3)
    val WEST_TOP = CoordGrid(3802, 10159, 3)
    val EAST_TOP = CoordGrid(3813, 10159, 3)
    val OUTSIDE = CoordGrid(3816, 3808, 0)
    val PETE_AT = CoordGrid(3817, 3809, 0)
    val EXIT_AT = CoordGrid(3806, 10162, 3)

    /** The prospector pieces and ore pack are found by cache name when the world starts. */
    val SHOP: List<VmReward> =
        listOf(
            VmReward("Iron ore", Objs.IRON_ORE, 30, true),
            VmReward("Silver ore", Objs.SILVER_ORE, 55, true),
            VmReward("Coal", Objs.COAL, 60, true),
            VmReward("Gold ore", Objs.GOLD_ORE, 150, true),
            VmReward("Mithril ore", Objs.MITHRIL_ORE, 150, true),
            VmReward("Adamantite ore", Objs.ADAMANTITE_ORE, 300, true),
            VmReward("Runite ore", Objs.RUNITE_ORE, 855, true),
            VmReward("Volcanic ash", Objs.FOSSIL_VOLCANIC_ASH, 40, true),
            VmReward("Calcite", Objs.FOSSIL_CALCITE, 70, true),
            VmReward("Pyrophosphite", Objs.FOSSIL_PYROPHOSPHITE, 70, true),
            VmReward("Volcanic mine teleport", Objs.FOSSIL_TABLET_VOLCANOTELEPORT, 200, false),
        )

    /**
     * Wiki "Petrified Pete's Ore Shop": the one-off unlocks (large water container 10,000, ash covered tome 40,000) and name-
     * resolved items.
     */
    const val WATER_UPGRADE_COST = 10_000
    const val TOME_COST = 40_000
    val NAMED_REWARDS: List<Pair<String, Int>> =
        listOf(
            "Ore pack (Volcanic Mine)" to 4000,
            "Prospector helmet" to 26_000,
            "Prospector jacket" to 39_000,
            "Prospector legs" to 34_000,
            "Prospector boots" to 21_000,
        )
}
