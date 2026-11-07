package tp.tools.cachedump

import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import tp.cache.config.Configs
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.cache.map.MapSquare
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bank booth facts for the banker spawns (docs/mechanics/npc-spawns.md SP4). A banker stands directly behind a bank booth,
 * but the Wiki lists banker tiles for only some banks, so the importer (`tools/wiki-import/npc_spawns.py`) needs every
 * booth: TSV rows `level x z loc angle open evidence`.
 *  - `open`: four digits for a banker at the west, north, east, south neighbour; 1 when that tile holds no loc and the tile
 *    on the other side of the booth is standable.
 *  - `evidence`: W, N, E or S when the collision map shows the staff side (a row of booths, same loc id and angle, at most
 *    [ROW_GAP] tiles apart, shares one side: the one whose floor the map blocks for players, see [score]),
 *    else `-`. It is a hint only: the booth model's own front is not in the angle, so the importer prefers the sides it
 *    learns from the Wiki's banker tiles.
 * Only locs named "Bank booth" count: closed booths have no banker, and the "Grand Exchange booth" locs sit on raw level 1
 * with no collision on either level.
 */
internal object BankerSpawns {
    private const val BOOTH = "bank booth"

    /** The four step directions (dx, dz), named west, north, east, south. */
    private val DIRS = listOf(-1 to 0, 0 to 1, 1 to 0, 0 to -1)
    private const val DIR_NAMES = "WNES"

    /** Points for one booth whose back is blocked floor and whose front is not; a row needs the sum of one side to reach it. */
    private const val EVIDENCE = 2

    /** Booths of one row are at most this many tiles apart (Chebyshev); some banks leave a gap between booths. */
    private const val ROW_GAP = 3

    private val BLOCKED = CollisionFlag.LOC or CollisionFlag.BLOCK_WALK

    private data class Booth(val id: Int, val angle: Int, val level: Int, val x: Int, val z: Int)

    fun write(cache: Js5Cache, cacheDir: Path, out: Path) {
        val configs = Configs.load(cache)
        val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
        val flags = CollisionBuilder.build(map, configs.locs)
        val booths = ArrayList<Booth>()
        for (square in map.squares) {
            for (i in 0 until square.locCount) {
                if (configs.locs[square.locIds[i]]?.name?.lowercase() != BOOTH) continue
                val info = square.locInfo[i]
                booths +=
                    Booth(
                        square.locIds[i],
                        MapSquare.locAngle(info),
                        MapSquare.locLevel(info),
                        square.key.baseX + MapSquare.locX(info),
                        square.key.baseZ + MapSquare.locZ(info),
                    )
            }
        }
        val evidence = HashMap<Booth, Pair<Int, Int>>()
        for (group in rows(booths)) {
            val side = sideOf(flags, group) ?: continue
            for (booth in group) evidence[booth] = side
        }
        val rows =
            booths.sortedWith(compareBy({ it.level }, { it.x }, { it.z })).map { booth ->
                val open = DIRS.map { (dx, dz) -> if (usable(flags, booth, -dx, -dz)) "1" else "0" }.joinToString("")
                val side = evidence[booth]?.let { (dx, dz) -> "${DIR_NAMES[DIRS.indexOf(-dx to -dz)]}" } ?: "-"
                "${booth.level}\t${booth.x}\t${booth.z}\t${booth.id}\t${booth.angle}\t$open\t$side"
            }
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.write(out, listOf("level\tx\tz\tloc\tangle\topen\tevidence") + rows)
        println("${booths.size} bank booths, ${evidence.size} with collision evidence -> $out")
    }

    /** Groups booths of the same loc id, angle and level that lie within [ROW_GAP] tiles of each other. */
    private fun rows(booths: List<Booth>): List<List<Booth>> {
        val groups = ArrayList<List<Booth>>()
        for (same in booths.groupBy { Triple(it.id, it.angle, it.level) }.values) {
            val left = same.toMutableList()
            while (left.isNotEmpty()) {
                val group = mutableListOf(left.removeAt(0))
                var grew = true
                while (grew) {
                    val next = left.filter { b -> group.any { near(it, b) } }
                    grew = next.isNotEmpty()
                    group += next
                    left.removeAll(next.toSet())
                }
                groups += group
            }
        }
        return groups
    }

    private fun near(a: Booth, b: Booth): Boolean = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)) <= ROW_GAP

    /** Direction (dx, dz) from the banker tile to the booth, i.e. the customer-facing direction, with the best summed score. */
    private fun sideOf(flags: CollisionFlagMap, group: List<Booth>): Pair<Int, Int>? {
        val totals = DIRS.map { (dx, dz) -> group.sumOf { score(flags, it, dx, dz) } }
        val best = totals.max()
        if (best < EVIDENCE || totals.count { it == best } != 1) return null
        return DIRS[totals.indexOf(best)]
    }

    /**
     * Evidence that the booth faces (dx, dz), i.e. its banker stands at (x - dx, z - dz): the staff floor is blocked for
     * players (`BLOCK_WALK` without a loc) and the customer floor is not ([EVIDENCE] points). Wall edges were tried as well
     * and misplaced a third of the Wiki's banker tiles (Al Kharid, Shayzien), so they are not used.
     */
    private fun score(flags: CollisionFlagMap, booth: Booth, dx: Int, dz: Int): Int {
        if (!usable(flags, booth, dx, dz)) return 0
        val back = flags[booth.x - dx, booth.z - dz, booth.level]
        val front = flags[booth.x + dx, booth.z + dz, booth.level]
        return if (back and CollisionFlag.BLOCK_WALK != 0 && front and CollisionFlag.BLOCK_WALK == 0) EVIDENCE else 0
    }

    /** The banker tile holds no loc and the customer tile in front of the booth is standable. */
    private fun usable(flags: CollisionFlagMap, booth: Booth, dx: Int, dz: Int): Boolean =
        flags[booth.x - dx, booth.z - dz, booth.level] and CollisionFlag.LOC == 0 &&
            flags[booth.x + dx, booth.z + dz, booth.level] and BLOCKED == 0
}
