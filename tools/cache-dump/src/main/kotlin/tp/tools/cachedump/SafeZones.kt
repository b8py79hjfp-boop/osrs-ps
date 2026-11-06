package tp.tools.cachedump

import org.rsmod.routefinder.StepValidator
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
 * PvP-world safe zones (docs/mechanics/wilderness.md W12, OPEN-W2). The Wiki names them ("the majority of the banks,
 * as well as respawn points and locations such as the Grand Exchange") but publishes no geometry, so we derive it from
 * the cache: every tile reachable on foot within [RADIUS] tiles of a bank booth or bank chest (walls and closed doors
 * stop the fill, so an enclosed bank yields its interior), plus the same fill around our respawn point. Banks inside
 * the Wilderness are left out (they're not PvP-world safe zones). Output: TSV rows `level x1 x2 z` (one row-run each).
 */
internal object SafeZones {
    private val BANK_NAMES = setOf("Bank booth", "Bank chest")

    /** Fill cap around each seed (Chebyshev tiles); our choice (OPEN-W2). */
    private const val RADIUS = 6

    /** Respawn point the server uses (content/combat `CombatSystem.RESPAWN`, Lumbridge). */
    private val RESPAWNS = listOf(Triple(3222, 3218, 0))

    // Wilderness bounds (wilderness.md OPEN-W1).
    private const val WILD_MIN_X = 2944
    private const val WILD_MAX_X = 3391
    private const val WILD_MIN_Z = 3520
    private const val WILD_MAX_Z = 3967

    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    fun write(cache: Js5Cache, cacheDir: Path, out: Path) {
        val configs = Configs.load(cache)
        val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
        val flags = CollisionBuilder.build(map, configs.locs)
        val steps = StepValidator(flags)
        val tiles = HashSet<Long>()
        var seeds = 0
        for (square in map.squares) {
            for (i in 0 until square.locCount) {
                val name = configs.locs[square.locIds[i]]?.name ?: continue
                if (name !in BANK_NAMES) continue
                val info = square.locInfo[i]
                val x = square.key.baseX + MapSquare.locX(info)
                val z = square.key.baseZ + MapSquare.locZ(info)
                if (inWilderness(x, z)) continue
                seeds++
                fill(flags, steps, MapSquare.locLevel(info), x, z, tiles)
            }
        }
        for ((x, z, level) in RESPAWNS) fill(flags, steps, level, x, z, tiles)
        val rows = toRuns(tiles)
        Files.createDirectories(out.toAbsolutePath().parent)
        Files.write(out, listOf("level\tx1\tx2\tz") + rows.map { "${it.level}\t${it.x1}\t${it.x2}\t${it.z}" })
        println("$seeds bank booths/chests -> ${tiles.size} safe tiles in ${rows.size} runs -> $out")
    }

    private fun inWilderness(x: Int, z: Int) = x in WILD_MIN_X..WILD_MAX_X && z in WILD_MIN_Z..WILD_MAX_Z

    /** Tile key: level above x above z, 20 bits per coordinate. */
    private const val LEVEL_SHIFT = 40
    private const val X_SHIFT = 20
    private const val COORD_MASK = 0xFFFFFL

    private fun pack(level: Int, x: Int, z: Int): Long = (level.toLong() shl LEVEL_SHIFT) or (x.toLong() shl X_SHIFT) or z.toLong()

    /** BFS from the walkable neighbours of ([x], [z]) (the seed itself may be a blocking booth). */
    private fun fill(flags: CollisionFlagMap, steps: StepValidator, level: Int, x: Int, z: Int, out: MutableSet<Long>) {
        val queue = ArrayDeque<Triple<Int, Int, Int>>()
        val seen = HashSet<Long>()
        // Seeds: the tile itself if standable (respawn points), else its standable neighbours (booths block).
        val starts = if (standable(flags, level, x, z)) listOf(x to z) else DIRS.map { (dx, dz) -> x + dx to z + dz }
        for ((sx, sz) in starts) {
            if (standable(flags, level, sx, sz) && seen.add(pack(level, sx, sz))) queue.add(Triple(level, sx, sz))
        }
        while (queue.isNotEmpty()) {
            val (l, cx, cz) = queue.removeFirst()
            out += pack(l, cx, cz)
            for ((dx, dz) in DIRS) {
                val nx = cx + dx
                val nz = cz + dz
                if (maxOf(kotlin.math.abs(nx - x), kotlin.math.abs(nz - z)) > RADIUS) continue
                if (inWilderness(nx, nz) || !steps.canTravel(l, cx, cz, dx, dz)) continue
                if (seen.add(pack(l, nx, nz))) queue.add(Triple(l, nx, nz))
            }
        }
    }

    private fun standable(flags: CollisionFlagMap, level: Int, x: Int, z: Int): Boolean =
        flags[x, z, level] and (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK) == 0

    private data class Run(val level: Int, val x1: Int, val x2: Int, val z: Int)

    private fun toRuns(tiles: Set<Long>): List<Run> {
        val byRow = tiles.groupBy { (it ushr LEVEL_SHIFT).toInt() to (it and COORD_MASK).toInt() }
        val runs = ArrayList<Run>()
        for ((key, row) in byRow.entries.sortedWith(compareBy({ it.key.first }, { it.key.second }))) {
            val xs = row.map { ((it ushr X_SHIFT) and COORD_MASK).toInt() }.sorted()
            var start = xs.first()
            var prev = start
            for (xv in xs.drop(1)) {
                if (xv != prev + 1) {
                    runs += Run(key.first, start, prev, key.second)
                    start = xv
                }
                prev = xv
            }
            runs += Run(key.first, start, prev, key.second)
        }
        return runs
    }
}
