@file:Suppress("MagicNumber") // data tables: Wiki levels, xp, charts; cache 2757 tiles (docs/mechanics/agility.md)

package tp.content.skills

import tp.symbols.Seqs
import tp.util.CoordGrid

/*
 * The six higher rooftop courses (docs/mechanics/agility.md). Loc ids, options, levels and xp are the course and obstacle pages of
 * the Wiki; placements are cache 2757 (`tools/wiki-import/agility_courses.py`). Landing tiles are generated: the free tile (cache
 * collision) of the next obstacle's roof that is nearest to the obstacle, or the nearest free ground tile after the last obstacle
 * (OPEN-AG3). Tick counts are estimates (OPEN-AG1) except where noted. The Wiki folds the lap bonus into the last obstacle's xp.
 */

/** The Canifis jump between the tanner and the general store: Wiki "Gap (Canifis Rooftop Course)" success chart, low 175, high 300. */
private val CANIFIS_GAP_FAIL = Fail(175, 300, CoordGrid(3486, 3499, 0))

/** The Pollnivneach market stall: Wiki "Market stall (Pollnivneach Rooftop Course)" success chart, low 60, high 300. */
private val POLLNIVNEACH_STALL_FAIL = Fail(60, 300, CoordGrid(3349, 2970, 0))

/**
 * Canifis Rooftop Course (Wiki; level 40, 240 xp per lap with the 175 of the last gap). Marks: 2/3 and no 20-levels-over
 * reduction (Wiki "Marks of grace"). The third gap is the only failable obstacle; the second gap is one tick slower
 * than the other gaps (Wiki "Canifis Rooftop Course" trivia).
 */
internal val CANIFIS =
    Course(
        "Canifis Rooftop Course",
        40,
        listOf(
            Obstacle(14843, 1, CLIMB, 3, 10.0, tile(3506, 3491, 3)),
            Obstacle(14844, 1, JUMP, 3, 8.0, tile(3504, 3503, 3)),
            Obstacle(14845, 1, JUMP, 4, 8.0, tile(3494, 3503, 3)),
            Obstacle(14848, 1, JUMP, 3, 10.0, tile(3479, 3499, 3), fail = CANIFIS_GAP_FAIL),
            Obstacle(14846, 1, JUMP, 3, 8.0, tile(3478, 3487, 3)),
            Obstacle(14894, 1, JUMP, 4, 10.0, tile(3489, 3476, 3)),
            Obstacle(14847, 1, JUMP, 3, 11.0, tile(3508, 3478, 3)),
            Obstacle(14897, 1, JUMP, 3, 175.0, tile(3509, 3482, 0)),
        ),
        0.0,
        listOf(
            CoordGrid(3506, 3495, 3),
            CoordGrid(3501, 3505, 3),
            CoordGrid(3490, 3501, 3),
            CoordGrid(3478, 3496, 3),
            CoordGrid(3478, 3484, 3),
        ),
        markNumerator = 2,
        markDenominator = 3,
        markLevelPenalty = false,
    )

/**
 * Falador Rooftop Course (Wiki; level 50, 586 xp per lap with the 241 of the edge). The hand holds can be failed
 * (OPEN-AG2: no chance on the Wiki). The third ledge is two locs, 14922 and 14923.
 */
internal val FALADOR =
    Course(
        "Falador Rooftop Course",
        50,
        listOf(
            Obstacle(14898, 1, CLIMB, 3, 11.0, tile(3036, 3342, 3)),
            Obstacle(14899, 1, BALANCE, 5, 22.0, tile(3044, 3343, 3)),
            Obstacle(14901, 1, CLIMB, 7, 61.0, tile(3050, 3357, 3)),
            Obstacle(14903, 1, JUMP, 3, 27.0, tile(3048, 3361, 3)),
            Obstacle(14904, 1, JUMP, 3, 26.0, tile(3041, 3362, 3)),
            Obstacle(14905, 1, BALANCE, 7, 61.0, tile(3029, 3354, 3)),
            Obstacle(14911, 1, BALANCE, 5, 53.0, tile(3021, 3354, 3)),
            Obstacle(14919, 1, JUMP, 3, 30.0, tile(3017, 3349, 3)),
            Obstacle(14920, 1, JUMP, 3, 14.0, tile(3014, 3345, 3)),
            Obstacle(14921, 1, JUMP, 3, 13.0, tile(3012, 3342, 3)),
            Obstacle(14922, 1, JUMP, 3, 13.0, tile(3012, 3333, 3), listOf(14923)),
            Obstacle(14924, 1, JUMP, 3, 14.0, tile(3019, 3333, 3)),
            Obstacle(14925, 1, JUMP, 3, 241.0, tile(3025, 3333, 0)),
        ),
        0.0,
        listOf(
            CoordGrid(3045, 3345, 3),
            CoordGrid(3049, 3357, 3),
            CoordGrid(3019, 3345, 3),
            CoordGrid(3013, 3345, 3),
            CoordGrid(3015, 3333, 3),
        ),
    )

/**
 * Seers' Village Rooftop Course (Wiki; level 60, 570 xp per lap with the 435 of the edge). The first gap and the tightrope can
 * be failed until level 79 (OPEN-AG2: no chance on the Wiki). The Kandarin diary mark rerolls are OPEN-AG8.
 */
internal val SEERS_VILLAGE =
    Course(
        "Seers' Village Rooftop Course",
        60,
        listOf(
            Obstacle(14927, 1, CLIMB, 3, 45.0, tile(2729, 3490, 3)),
            Obstacle(14928, 1, JUMP, 3, 20.0, tile(2713, 3494, 2)),
            Obstacle(14932, 1, BALANCE, 8, 20.0, tile(2710, 3481, 2)),
            Obstacle(14929, 1, JUMP, 3, 35.0, tile(2712, 3472, 3)),
            Obstacle(14930, 1, JUMP, 3, 15.0, tile(2701, 3465, 2)),
            Obstacle(14931, 1, JUMP, 3, 435.0, tile(2702, 3463, 0)),
        ),
        0.0,
        listOf(
            CoordGrid(2727, 3498, 3),
            CoordGrid(2707, 3493, 2),
            CoordGrid(2710, 3493, 2),
            CoordGrid(2712, 3481, 2),
            CoordGrid(2698, 3465, 2),
        ),
    )

/**
 * Pollnivneach Rooftop Course (Wiki; level 70, 890 xp per lap without the Desert hard diary, OPEN-AG8 for the diary's 666 on
 * the drying line). The market stall can be failed (success chart, fall damage); the Wiki says nothing can be failed from 85.
 */
internal val POLLNIVNEACH =
    Course(
        "Pollnivneach Rooftop Course",
        70,
        listOf(
            Obstacle(14935, 1, CLIMB, 3, 10.0, tile(3351, 2969, 1)),
            Obstacle(14936, 1, JUMP, 3, 45.0, tile(3355, 2977, 1), fail = POLLNIVNEACH_STALL_FAIL),
            Obstacle(14937, 1, SWING, 4, 65.0, tile(3362, 2978, 1)),
            Obstacle(14938, 1, JUMP, 3, 35.0, tile(3366, 2976, 1)),
            Obstacle(14939, 1, JUMP, 3, 75.0, tile(3364, 2983, 1)),
            Obstacle(14940, 1, CLIMB, 3, 5.0, tile(3362, 2983, 2)),
            Obstacle(14941, 1, Seqs.HUMAN_MONKEYBARS_WALK, 12, 55.0, tile(3358, 2995, 2)),
            Obstacle(14944, 1, JUMP, 3, 60.0, tile(3362, 2999, 2)),
            Obstacle(14945, 1, JUMP, 3, 540.0, tile(3364, 3001, 0)),
        ),
        0.0,
        listOf(
            CoordGrid(3347, 2969, 1),
            CoordGrid(3353, 2975, 1),
            CoordGrid(3359, 2983, 1),
            CoordGrid(3357, 3002, 2),
            CoordGrid(3362, 2993, 2),
        ),
    )

/**
 * Rellekka Rooftop Course (Wiki; level 80, mark chance 2/5). The first tightrope can be failed until 85 (10% of the current
 * hitpoints; OPEN-AG2: no chance on the Wiki). The pile of fish gives 475 xp, 615 with the Fremennik hard diary (OPEN-AG8).
 */
internal val RELLEKKA =
    Course(
        "Rellekka Rooftop Course",
        80,
        listOf(
            Obstacle(14946, 1, CLIMB, 3, 20.0, tile(2625, 3676, 3)),
            Obstacle(14947, 1, JUMP, 3, 30.0, tile(2622, 3668, 3), level = 1),
            Obstacle(14987, 1, BALANCE, 5, 40.0, tile(2627, 3655, 3), level = 80),
            Obstacle(14990, 1, JUMP, 3, 85.0, tile(2639, 3653, 3), level = 80),
            Obstacle(14991, 1, Seqs.HUMAN_JUMP_HURDLE, 3, 25.0, tile(2643, 3657, 3), level = 1),
            Obstacle(14992, 1, BALANCE, 8, 105.0, tile(2655, 3665, 3), level = 1),
            Obstacle(14994, 1, JUMP, 3, 475.0, tile(2653, 3675, 0), level = 1),
        ),
        0.0,
        listOf(
            CoordGrid(2623, 3675, 3),
            CoordGrid(2628, 3652, 3),
            CoordGrid(2629, 3653, 3),
            CoordGrid(2641, 3650, 3),
            CoordGrid(2642, 3651, 3),
        ),
        markNumerator = 2,
        markDenominator = 5,
    )

/**
 * Ardougne Rooftop Course (Wiki; level 90, mark chance 2/3, a single spawn tile). The first two gaps can be failed until 95
 * (OPEN-AG2: no chance on the Wiki); the other obstacles have no requirement. The elite Ardougne diary's timer and reroll are
 * OPEN-AG8.
 */
internal val ARDOUGNE =
    Course(
        "Ardougne Rooftop Course",
        90,
        listOf(
            Obstacle(15608, 1, CLIMB, 3, 43.0, tile(2671, 3299, 3)),
            Obstacle(15609, 1, JUMP, 3, 65.0, tile(2665, 3318, 3), level = 90),
            Obstacle(26635, 1, BALANCE, 5, 50.0, tile(2656, 3318, 3), level = 1),
            Obstacle(15610, 1, JUMP, 3, 21.0, tile(2653, 3314, 3), level = 90),
            Obstacle(15611, 1, JUMP, 3, 28.0, tile(2651, 3308, 3), level = 1),
            Obstacle(28912, 1, BALANCE, 3, 57.0, tile(2655, 3297, 3), level = 1),
            Obstacle(15612, 1, JUMP, 3, 625.0, tile(2656, 3296, 0), level = 1),
        ),
        0.0,
        listOf(CoordGrid(2657, 3318, 3)),
        markNumerator = 2,
        markDenominator = 3,
    )
