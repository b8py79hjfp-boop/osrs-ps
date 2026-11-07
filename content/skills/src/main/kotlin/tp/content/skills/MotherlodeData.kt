@file:Suppress("MagicNumber") // data tables: Wiki ore chances, xp, ticks and prices (docs/mechanics/motherlode.md)

package tp.content.skills

import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * One ore the sack can hold, in the order its counts are packed ([Motherlode.COAL] .. [Motherlode.NUGGET]): item, the Mining
 * level to receive it, the success chance as `statRandom` low/high (Wiki "Motherlode Mine" > Skilling success chart), the bonus xp
 * when it reaches the sack (Wiki "Pay-dirt" table) and the percentage points the elite Falador diary adds to the chance (Wiki
 * "Motherlode Mine": about 1 for runite, 2 for adamantite, 1 for mithril; Mod Ash, 27 January 2022).
 */
internal class SackOre(val obj: Int, val level: Int, val low: Int, val high: Int, val bonusXp: Double, val diaryPoints: Int)

internal object MotherlodeData {
    /** Wiki "Pay-dirt": a cleaned pay-dirt is a golden nugget with probability 1 in 32 (about 3.13%), whatever the level. */
    const val NUGGET_ONE_IN = 32

    /** Wiki "Motherlode Mine" > Rewards: at most 3 golden nuggets per batch of pay-dirt deposited into the hopper. */
    const val NUGGETS_PER_DEPOSIT = 3

    /** Wiki "Motherlode Mine": the sack holds 108 pay-dirt, 189 once upgraded (200 golden nuggets). */
    const val SACK_CAPACITY = 108
    const val SACK_CAPACITY_UPGRADED = 189

    /** Wiki "Motherlode Mine" > Mine upgrades (golden nuggets): the upper floor 100, the upper hopper 50, the larger sack 200. */
    const val UPPER_FLOOR_COST = 100
    const val UPPER_HOPPER_COST = 50
    const val BIGGER_SACK_COST = 200

    /** Wiki "Motherlode Mine": the upper floor needs 57 base Mining (not boostable); the mine itself 30. */
    const val UPPER_FLOOR_LEVEL = 57

    /** Wiki "Motherlode Mine" > Pay-dirt: "After 8 seconds" the deposited pay-dirt reaches the sack (8 s / 0.6 s rounded down). */
    const val FLOW_TICKS = 13

    /**
     * Wiki "Ore vein": lower veins disappear 23-27 s after the first ore, upper veins 36-40 s (ticks = seconds / 0.6, rounded).
     */
    val LOWER_VEIN_TICKS = 38..45
    val UPPER_VEIN_TICKS = 60..67

    /** Wiki "Ore vein": upper-level and outer-ring veins respawn after 100 ticks (OPEN-ML5: the lower inner ring's 150 is not applied). */
    const val VEIN_RESPAWN_TICKS = 100

    /** Wiki "Strut": rounds of 58 s (96.67 ticks, rounded up); a repaired strut breaks on round 2, 3 or 4 (OPEN-ML6: uniform). */
    const val STRUT_ROUND_TICKS = 97
    val STRUT_BREAK_ROUNDS = 2..4

    /** Wiki "Strut": the repair rolls every tick with the success chart low 30 / high 70 on Smithing; xp is 1.5 x the Smithing level. */
    const val STRUT_LOW = 30
    const val STRUT_HIGH = 70
    const val STRUT_XP_PER_LEVEL = 1.5

    /** Cleaned ores, in sack order: coal, gold, mithril, adamantite, runite, nugget (Wiki "Motherlode Mine" ore chances). */
    val ORES: List<SackOre> =
        listOf(
            SackOre(Objs.COAL, 30, 226, 325, 15.0, 0),
            SackOre(Objs.GOLD_ORE, 40, -40, 126, 15.0, 0),
            SackOre(Objs.MITHRIL_ORE, 55, -19, 90, 30.0, 1),
            SackOre(Objs.ADAMANTITE_ORE, 70, -90, 50, 45.0, 2),
            SackOre(Objs.RUNITE_ORE, 85, -20, 5, 75.0, 1),
            SackOre(Objs.MOTHERLODE_NUGGET, 30, 0, 0, 0.0, 0),
        )

    /** The mine's map square (region 14936, the cache `map 58 88`): the HUD and the water flow only run inside it. */
    const val MIN_X = 3712
    const val MAX_X = 3775
    const val MIN_Z = 5632
    const val MAX_Z = 5695

    fun inMine(c: CoordGrid): Boolean = c.level == 0 && c.x in MIN_X..MAX_X && c.z in MIN_Z..MAX_Z

    /** The upper hopper tile (cache loc 26674 on raw level 1); the lower hopper is at (3748, 5672). */
    val UPPER_HOPPER = CoordGrid(3755, 5677, 0)

    /** Ladder destinations, one tile beyond the ladder (cache locs 19044 at (3755, 5673) and 19045 at (3755, 5674); OPEN-ML8). */
    val UPPER_LANDING = CoordGrid(3755, 5675, 0)
    val LOWER_LANDING = CoordGrid(3755, 5672, 0)

    /** Strut and wheel tiles from the cache (locs 26669 / 26670 / 26672), per strut index. */
    val STRUT_FIXED_AT = arrayOf(CoordGrid(3744, 5663, 0), CoordGrid(3744, 5669, 0))
    val STRUT_BROKEN_AT = arrayOf(CoordGrid(3742, 5663, 0), CoordGrid(3742, 5669, 0))
    val WHEEL_AT = arrayOf(CoordGrid(3743, 5662, 0), CoordGrid(3743, 5668, 0))
}
