@file:Suppress("MagicNumber") // data tables: Wiki levels, xp, charts; cache 2757 tiles (docs/mechanics/agility.md)

package tp.content.skills

import tp.symbols.Seqs
import tp.util.CoordGrid

/*
 * Courses beyond the rooftops (docs/mechanics/agility.md AG11-AG13, OPEN-AG1, OPEN-AG3, OPEN-AG11): the Barbarian Outpost and
 * Wilderness courses. Loc ids, options, levels, xp and success charts are the course and obstacle pages of the Wiki (fetched
 * 2026-11-07); placements are cache 2757. Landing tiles and tick counts are estimates (OPEN-AG1, OPEN-AG3): the Wiki gives neither.
 */

/** Wiki "Ropeswing (Barbarian Outpost Agility Course)" success chart, low 200, high 280; damage and fall tile are OPEN-AG11. */
private val BARBARIAN_ROPE_FAIL = Fail(200, 280, null)

/** Wiki "Log balance (Barbarian Outpost Agility Course)" success chart, low 180, high 260. */
private val BARBARIAN_LOG_FAIL = Fail(180, 260, null)

/** Wiki "Balancing ledge (Barbarian Outpost Agility Course)" success chart, low 200, high 280. */
private val BARBARIAN_LEDGE_FAIL = Fail(200, 280, null)

/**
 * The three crumbling walls share loc 1948 and are told apart by their placement (cache 2757: (2536|2539|2542, 3553)). Each wall
 * is crossed from the west to the tile just east of it (OPEN-AG3: the course runs east, the Wiki gives no tiles).
 */
private fun crumblingWall(x: Int) = Obstacle(1948, 1, CLIMB, 2, 13.7, tile(x + 1, 3553, 0), at = CoordGrid(x, 3553, 0))

/**
 * Barbarian Outpost Agility Course (Wiki: level 35, 153.3 xp a lap = 46.3 bonus + 41.3 Strength). The ladder is loc 42487 (placed on
 * level 1 of the cache) with its multi-loc parent 16682 (the id on the Wiki). The pipe at the entrance is a shortcut (Shortcuts.kt).
 * Marks of grace spawn "after the balancing ledge" (Wiki "Mark of grace spawns"), taken as the ledge's landing tile (OPEN-AG11).
 */
internal val BARBARIAN_OUTPOST =
    Course(
        "Barbarian Outpost Agility Course",
        35,
        listOf(
            Obstacle(23131, 1, SWING, 4, 22.0, tile(2551, 3549, 0), fail = BARBARIAN_ROPE_FAIL),
            Obstacle(23144, 1, BALANCE, 10, 13.7, tile(2541, 3546, 0), fail = BARBARIAN_LOG_FAIL),
            Obstacle(20211, 1, CLIMB, 2, 8.2, tile(2537, 3546, 1)),
            Obstacle(23547, 1, BALANCE, 4, 22.0, tile(2532, 3547, 1), fail = BARBARIAN_LEDGE_FAIL),
            Obstacle(42487, 1, Seqs.HUMAN_CLIMBING_DOWN, 2, 0.0, tile(2532, 3546, 0), listOf(16682)),
            crumblingWall(2536),
            crumblingWall(2539),
            crumblingWall(2542),
        ),
        46.3,
        listOf(CoordGrid(2532, 3547, 1)),
        lapStrengthXp = 41.3,
    )

/** Wiki "Wilderness Agility Course": obstacle 1 needs level 49; the gate (not implemented, OPEN-AG12) needs 52. */
private const val WILDERNESS_PIPE_LEVEL = 49

/** Wiki "Ropeswing (Wilderness Agility Course)": chart 200/250; damage `floor(hp * 3 / 20) + 1`; the dungeon below is OPEN-AG12. */
private val WILDERNESS_ROPE_FAIL = Fail(200, 250, null, 3, 20, 1)

/** Wiki "Stepping stone (Wilderness Agility Course)": chart low 180, high 250; damage `floor(hp * 4 / 20) + 1`; no dungeon fall. */
private val WILDERNESS_STONES_FAIL = Fail(180, 250, null, 4, 20, 1)

/** Wiki "Log balance (Wilderness Agility Course)": chart 200/250; damage `floor(hp * 3 / 20) + 1`; the dungeon below is OPEN-AG12. */
private val WILDERNESS_LOG_FAIL = Fail(200, 250, null, 3, 20, 1)

/**
 * Wilderness Agility Course (Wiki: 571.4 xp a lap, no marks of grace; the rocks' 498.9 xp is paid only for an unbroken lap). The
 * south pipe end is told apart from the north end (3004, 3948), which is not part of the lap. The dispenser at the start
 * (WildernessAgility.kt) gives the ticket; [wildernessLapDone] and [wildernessLapStarted] keep its streak.
 */
internal val WILDERNESS =
    Course(
        "Wilderness Agility Course",
        WILDERNESS_PIPE_LEVEL,
        listOf(
            Obstacle(23137, 1, Seqs.HUMAN_PIPESQUEEZE, 12, 12.5, tile(3004, 3950, 0), at = CoordGrid(3004, 3938, 0)),
            Obstacle(23132, 1, SWING, 4, 20.0, tile(3005, 3958, 0), level = 1, fail = WILDERNESS_ROPE_FAIL),
            Obstacle(23556, 1, JUMP, 6, 20.0, tile(2996, 3960, 0), level = 1, fail = WILDERNESS_STONES_FAIL),
            Obstacle(23542, 1, BALANCE, 8, 20.0, tile(2994, 3945, 0), level = 1, fail = WILDERNESS_LOG_FAIL),
            Obstacle(23640, 1, CLIMB, 4, 498.9, tile(2994, 3933, 0), level = 1, xpOnlyInLap = true),
        ),
        0.0,
        emptyList(),
        onLap = ::wildernessLapDone,
        onStart = ::wildernessLapStarted,
    )

internal val MORE_COURSES: List<Course> = listOf(BARBARIAN_OUTPOST, WILDERNESS)
