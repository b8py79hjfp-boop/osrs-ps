@file:Suppress("MagicNumber") // data tables: levels, xp from the OSRS Wiki course pages; tiles from cache 2757 (docs/mechanics/agility.md)

package tp.content.skills

import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Where an obstacle puts the player. [relative] false: the absolute tile ([x], [z], [level]). True: the offset from the
 * player's own tile, for obstacles several adjacent locs share (the nets and the pipes of the Gnome Stronghold course).
 */
internal class Dest(val x: Int, val z: Int, val level: Int, val relative: Boolean = false)

/**
 * One obstacle of a course (docs/mechanics/agility.md AG1-AG3): loc [loc] with op [op], the [anim] played while the
 * player crosses for [ticks] ticks (there is no forced-movement message yet, OPEN-AG1), [xp] from the course page, and
 * the [dest] the player is moved to. [alsoLocs] are other locs of the same obstacle (the rest of a rope, the second pipe).
 */
internal class Obstacle(
    val loc: Int,
    val op: Int,
    val anim: Int,
    val ticks: Int,
    val xp: Double,
    val dest: Dest,
    val alsoLocs: List<Int> = emptyList(),
)

/**
 * An agility course: [obstacles] in lap order, the Wiki [lapXp] granted for a completed lap on top of the last
 * obstacle (0 when the Wiki folds it into the last obstacle's xp, as on every rooftop course), and the tiles marks of
 * grace appear on ([markSpots], empty where the spawn tiles are not sourced).
 */
internal class Course(
    val name: String,
    val level: Int,
    val obstacles: List<Obstacle>,
    val lapXp: Double,
    val markSpots: List<CoordGrid>,
)

private fun tile(x: Int, z: Int, level: Int) = Dest(x, z, level)

private fun by(dx: Int, dz: Int, dLevel: Int) = Dest(dx, dz, dLevel, relative = true)

/** Climbing up (RS Mod / cache `human_reachforladder`) and the balance, swing and jump animations, by gameval name. */
private const val CLIMB = Seqs.HUMAN_REACHFORLADDER
private const val BALANCE = Seqs.HUMAN_WALK_LOGBALANCE
private const val SIDESTEP = Seqs.HUMAN_WALK_SIDESTEPL
private const val SWING = Seqs.HUMAN_ROPESWING_LONG
private const val JUMP = Seqs.AGILITY_SHORTCUT_WALL_JUMPDOWN

/**
 * Gnome Stronghold Agility Course (Wiki "Gnome Stronghold Agility Course"; locs and tiles from cache 2757). Tiles: the log
 * leaves the player at (2474, 3429); the nets and pipes keep the player's x; the platforms are levels 1 and 2.
 */
internal val GNOME_STRONGHOLD =
    Course(
        "Gnome Stronghold Agility Course",
        1,
        listOf(
            Obstacle(23145, 1, Seqs.HUMAN_WALK_LOGBALANCE, 7, 10.0, tile(2474, 3429, 0)),
            Obstacle(23134, 1, Seqs.HUMAN_REACHFORLADDER, 2, 10.0, by(0, -3, 1)),
            Obstacle(23559, 1, Seqs.HUMAN_REACHFORLADDER, 2, 6.5, tile(2473, 3420, 2)),
            Obstacle(23557, 1, Seqs.HUMAN_WALK_LOGBALANCE, 6, 10.0, tile(2483, 3420, 2), listOf(23558)),
            Obstacle(23560, 1, Seqs.HUMAN_CLIMBING_DOWN, 2, 6.5, tile(2487, 3420, 0)),
            Obstacle(23135, 1, Seqs.HUMAN_REACHFORLADDER, 2, 10.0, by(0, 2, 0)),
            Obstacle(23138, 1, Seqs.HUMAN_PIPESQUEEZE, 7, 7.5, by(0, 7, 0), listOf(23139)),
        ),
        50.0,
        listOf(CoordGrid(2486, 3421, 2)),
    )

/** Draynor Village Rooftop Course (Wiki; tiles from cache 2757 level 3). The crate's 79 xp includes the lap bonus. */
internal val DRAYNOR =
    Course(
        "Draynor Village Rooftop Course",
        1,
        listOf(
            Obstacle(11404, 1, CLIMB, 3, 5.0, tile(3102, 3279, 3)),
            Obstacle(11405, 1, BALANCE, 9, 8.0, tile(3091, 3276, 3)),
            Obstacle(11406, 1, BALANCE, 10, 7.0, tile(3092, 3266, 3)),
            Obstacle(11430, 1, SIDESTEP, 4, 7.0, tile(3088, 3261, 3)),
            Obstacle(11630, 1, JUMP, 3, 10.0, tile(3088, 3255, 3)),
            Obstacle(11631, 1, JUMP, 3, 4.0, tile(3096, 3256, 3)),
            Obstacle(11632, 1, Seqs.HUMAN_CLIMBING_DOWN, 3, 79.0, tile(3103, 3261, 0)),
        ),
        0.0,
        listOf(CoordGrid(3099, 3279, 3), CoordGrid(3088, 3274, 3), CoordGrid(3092, 3266, 3), CoordGrid(3099, 3259, 3)),
    )

/** Al Kharid Rooftop Course (Wiki; tiles from cache 2757). Total 216 xp, no separate lap bonus. */
internal val AL_KHARID =
    Course(
        "Al Kharid Rooftop Course",
        20,
        listOf(
            Obstacle(11633, 1, CLIMB, 3, 12.0, tile(3273, 3192, 3)),
            Obstacle(14398, 1, BALANCE, 9, 36.0, tile(3272, 3172, 3)),
            Obstacle(14402, 1, SWING, 4, 48.0, tile(3284, 3166, 3)),
            Obstacle(14403, 1, Seqs.ZIPLINE_SLIDE, 6, 48.0, tile(3317, 3163, 1)),
            Obstacle(14404, 1, SWING, 5, 12.0, tile(3316, 3175, 2)),
            Obstacle(11634, 1, CLIMB, 3, 6.0, tile(3316, 3180, 3)),
            Obstacle(14409, 1, BALANCE, 11, 18.0, tile(3302, 3187, 3)),
            Obstacle(14399, 1, JUMP, 3, 36.0, tile(3300, 3196, 0)),
        ),
        0.0,
        listOf(
            CoordGrid(3315, 3161, 1),
            CoordGrid(3266, 3166, 3),
            CoordGrid(3284, 3172, 3),
            CoordGrid(3276, 3185, 3),
            CoordGrid(3302, 3187, 3),
        ),
    )

/**
 * Varrock Rooftop Course (Wiki; the loc ids and tiles are from cache 2757). The destinations are the tile beside the next
 * obstacle's loc (OPEN-AG3): the real landing tiles are not sourced. The last edge's 143.7 xp includes the lap bonus.
 */
internal val VARROCK =
    Course(
        "Varrock Rooftop Course",
        30,
        listOf(
            Obstacle(14412, 1, CLIMB, 3, 13.5, tile(3220, 3414, 3)),
            Obstacle(14413, 1, SWING, 5, 23.0, tile(3208, 3414, 3)),
            Obstacle(14414, 1, JUMP, 3, 19.0, tile(3192, 3415, 1)),
            Obstacle(14832, 1, SIDESTEP, 5, 28.0, tile(3193, 3402, 3)),
            Obstacle(14833, 1, JUMP, 3, 10.0, tile(3208, 3397, 3)),
            Obstacle(14834, 1, JUMP, 3, 24.5, tile(3232, 3402, 3)),
            Obstacle(14835, 1, JUMP, 3, 4.5, tile(3236, 3408, 3)),
            Obstacle(14836, 1, Seqs.HUMAN_JUMP_HURDLE, 3, 3.5, tile(3236, 3415, 3)),
            Obstacle(14841, 1, JUMP, 3, 143.7, tile(3237, 3414, 0)),
        ),
        0.0,
        emptyList(),
    )

internal val COURSES: List<Course> = listOf(GNOME_STRONGHOLD, DRAYNOR, AL_KHARID, VARROCK)
