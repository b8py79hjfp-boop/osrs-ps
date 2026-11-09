@file:Suppress("MagicNumber") // clientscript 384 constants

package tp.plugin

import tp.util.CoordGrid

/**
 * The Wilderness level at a tile: the client's own rule, clientscript 384 (docs/mechanics/wilderness.md W1 / W14,
 * docs/research/wilderness-zones.md §1), without the PvP-world bonus. Shared so content that is not the Wilderness plugin
 * can ask about a tile the player isn't on (teleport destinations, teleports.md T15); content/pvp `Wilderness.realLevel`
 * is this.
 */
public object WildernessArea {
    // An ordered list of boxes on all four planes; the first match wins. The surface ends at z 3967 (real content ends
    // there, OPEN-Z5), the first two rows above the ditch are safe (W2), and the non-Wilderness pockets inside the
    // underground box are left out (OPEN-Z4).
    private const val MIN_X = 2944
    private const val MAX_X = 3391
    private const val MIN_Z = 3520
    private const val MAX_Z = 3967
    private const val SAFE_ROWS = 2
    private const val ROWS_PER_LEVEL = 8
    private const val UNDER_MAX_X = 3455
    private const val UNDER_MIN_Z = 9920
    private const val UNDER_MAX_Z = 10879

    /** CS384 boxes with a fixed level (x1, z1, x2, z2, level): the boss-lair squares. */
    private val FIXED: List<IntArray> =
        listOf(
            intArrayOf(3264, 10176, 3327, 10239, 35), // Vet'ion's Rest
            intArrayOf(3392, 10176, 3455, 10239, 35), // Silk Chasm
            intArrayOf(3328, 10304, 3391, 10367, 40), // Callisto's Den
            intArrayOf(1728, 11520, 1791, 11583, 21), // Hunter's End
            intArrayOf(1856, 11520, 1919, 11583, 21), // Skeletal Tomb
            intArrayOf(1600, 11520, 1663, 11583, 29), // Web Chasm
        )

    /** OPEN-Z4: tiles inside the underground box that are not Wilderness (Bryophyta's lair, templates, under Ferox). */
    private val NOT_WILD: List<IntArray> =
        listOf(
            intArrayOf(3200, 9920, 3235, 9951),
            intArrayOf(2944, 9920, 3071, 9983),
            intArrayOf(3135, 9990, 3197, 10046),
        )

    private fun IntArray.has(c: CoordGrid): Boolean = c.x in this[0]..this[2] && c.z in this[1]..this[3]

    /** Wilderness level at [c] (no PvP-world bonus), 0 outside or in the safe rows. */
    public fun level(c: CoordGrid): Int {
        val x = c.x
        val z = c.z
        return when {
            x in MIN_X..MAX_X && z in MIN_Z..MAX_Z -> if (z < MIN_Z + SAFE_ROWS) 0 else (z - MIN_Z) / ROWS_PER_LEVEL + 1

            // Wilderness God Wars Dungeon: 2 below the generic underground rule.
            x in 3008..3071 && z in 10112..10175 -> (z - UNDER_MIN_Z) / ROWS_PER_LEVEL - 1

            else -> FIXED.firstOrNull { it.has(c) }?.get(4) ?: underground(c)
        }
    }

    private fun underground(c: CoordGrid): Int {
        val x = c.x
        val z = c.z
        // Escape Caves: 33 + 7 * ((z mod 64) - 6) / 50.
        if (x in 3328..3391 && z in 10240..10303) return 33 + 7 * (z % 64 - 6) / 50
        if (x !in MIN_X..UNDER_MAX_X || z !in UNDER_MIN_Z..UNDER_MAX_Z || NOT_WILD.any { it.has(c) }) return 0
        return (z - UNDER_MIN_Z) / ROWS_PER_LEVEL + 1
    }
}
