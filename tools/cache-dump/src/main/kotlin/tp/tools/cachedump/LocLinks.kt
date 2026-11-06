package tp.tools.cachedump

import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import tp.cache.config.Configs
import tp.cache.config.Js5Archive
import tp.cache.config.loc.LocType
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.cache.map.MapSquare
import java.nio.file.Files
import java.nio.file.Path

/**
 * Generates the data for generic doors, gates, ladders, trapdoors and stairs (docs/mechanics/doors-and-climbing.md):
 *
 * - `doors.tsv`: closed loc, opened loc, kind (single / left / right / gate_left / gate_right). The cache has no link
 *   between a door's two states; their gamevals do (`poordoor` / `poordooropen`, `opencastledoubledoorl`,
 *   `rustic_openfencegate_l`, `..._closed` / `..._open`). A closed loc has the op "Open", an opened one "Close", and
 *   the two names are equal once "open" / "closed" / "shut" are removed.
 * - `climbs.tsv`: for every placement of a loc with a climb op, where it leads. The destination is the matching loc on
 *   the floor above or below (or 6,400 tiles north/south for dungeons) with the opposite op, and the walkable tile
 *   beside it nearest to the climbed loc; failing that, the walkable tile above/below the loc itself.
 */
internal object LocLinks {
    private const val GAMEVAL_LOCS = 6
    private const val DUNGEON_OFFSET = 6400
    private const val SEARCH = 5
    private const val FALLBACK = 2
    private const val TOP_LEVEL = 3

    private val UP = Regex("(?i)^(climb|go|walk)[- ]?up(stairs)?$")
    private val DOWN = Regex("(?i)^(climb|go|walk)[- ]?down(stairs)?$")
    private val EITHER = Regex("(?i)^climb$")

    private class Placement(val id: Int, val x: Int, val z: Int, val level: Int, val shape: Int, val angle: Int, val w: Int, val l: Int) {
        /** Unrotated config size and approach flags (the route finder's reach check takes these with the angle). */
        var width = 1
        var length = 1
        var approach = 0
    }

    fun write(cache: Js5Cache, cacheDir: Path, outDir: Path) {
        val configs = Configs.load(cache)
        val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
        val flags = CollisionBuilder.build(map, configs.locs)
        Files.createDirectories(outDir)
        val names = gamevals(cache)
        val doors = doors(configs, names)
        Files.write(outDir.resolve("doors.tsv"), listOf("closed\topened\tkind\topen_op\tclose_op") + doors)
        report(configs, map, names, doors)
        val climbs = climbs(configs, map, flags)
        Files.write(outDir.resolve("climbs.tsv"), listOf("x\tz\tlevel\tloc\top\tdx\tdz\tdlevel\tstairs\toption") + climbs.first)
        println("doors: ${doors.size} pairs; climbs: ${climbs.first.size} links, ${climbs.second} placements unlinked")
    }

    /** Prints the most-placed wall locs with "Open" that have no pair (to check the pairing's coverage). */
    private fun report(configs: Configs, map: GameMap, names: Map<Int, String>, doors: List<String>) {
        val paired = doors.map { it.substringBefore('\t').toInt() }.toSet()
        val counts = HashMap<Int, Int>()
        var total = 0
        for (square in map.squares) {
            for (i in 0 until square.locCount) {
                val id = square.locIds[i]
                val shape = MapSquare.locShape(square.locInfo[i])
                if (shape > WALL_DIAGONAL || (shape > WALL_LAST && shape < WALL_DIAGONAL)) continue
                if (configs.locs[id]?.hasOp("Open") != true) continue
                total++
                if (id !in paired) counts.merge(id, 1, Int::plus)
            }
        }
        println("wall placements with Open: $total, unpaired: ${counts.values.sum()}")
        for ((id, n) in counts.entries
            .sortedByDescending {
                it.value
            }.take(REPORT_TOP)) {
            println("  $n x $id ${names[id]} '${configs.locs[id]?.name}'")
        }
    }

    private const val WALL_LAST = 3
    private const val WALL_DIAGONAL = 9
    private const val REPORT_TOP = 40

    private fun gamevals(cache: Js5Cache): Map<Int, String> {
        val files = cache.readFiles(Js5Archive.GAMEVALS, GAMEVAL_LOCS) ?: return emptyMap()
        return (0 until files.ids.size).associate { files.ids[it] to String(files.data[it], Charsets.UTF_8).lowercase() }
    }

    private fun LocType.hasOp(text: String) = ops.ops.any { it.equals(text, ignoreCase = true) }

    /** The name without its state words ("open", "opened", "closed", "shut"), as `_`-separated tokens or embedded. */
    private fun key(name: String): String =
        name
            .split('_')
            .map {
                it
                    .replace("opened", "")
                    .replace("closed", "")
                    .replace("open", "")
                    .replace("shut", "")
            }.filter { it.isNotEmpty() }
            .joinToString("_")

    private fun doors(configs: Configs, names: Map<Int, String>): List<String> {
        val rows = ArrayList<String>()
        val paired = HashSet<Int>()
        pairByName(configs, names, rows, paired)
        pairByModel(configs, names, rows, paired)
        return rows.sorted()
    }

    private fun isOpener(type: LocType, name: String) = type.hasOp("Open") && !name.contains("open")

    private fun isCloser(type: LocType, name: String) = type.hasOp("Close") && name.contains("open")

    /** First pass: gameval names equal once the state words are removed (one closed and one opened loc per name). */
    private fun pairByName(configs: Configs, names: Map<Int, String>, rows: MutableList<String>, paired: MutableSet<Int>) {
        val closed = HashMap<String, MutableList<Int>>()
        val opened = HashMap<String, MutableList<Int>>()
        for ((id, name) in names) {
            val type = configs.locs[id] ?: continue
            if (isOpener(type, name)) closed.getOrPut(key(name)) { ArrayList() } += id
            if (isCloser(type, name)) opened.getOrPut(key(name)) { ArrayList() } += id
        }
        for ((k, shut) in closed) {
            val open = opened[k] ?: continue
            if (shut.size != 1 || open.size != 1) continue
            rows += row(configs, shut[0], open[0], kind(names.getValue(shut[0])))
            paired += shut[0]
            paired += open[0]
        }
    }

    /** Second pass: same display name and models, one "Open" and one "Close" loc. */
    private fun pairByModel(configs: Configs, names: Map<Int, String>, rows: MutableList<String>, paired: Set<Int>) {
        val byModel = HashMap<String, MutableList<Int>>()
        for (id in names.keys) {
            val type = configs.locs[id] ?: continue
            val toggles = type.hasOp("Open") || type.hasOp("Close")
            if (id in paired || type.models.isEmpty() || !toggles) continue
            byModel.getOrPut("${type.name}|${type.models.joinToString(",")}") { ArrayList() } += id
        }
        for (group in byModel.values) {
            val shut = group.filter { configs.locs[it]?.hasOp("Open") == true }
            val open = group.filter { configs.locs[it]?.hasOp("Close") == true }
            if (shut.size != 1 || open.size != 1) continue
            rows += row(configs, shut[0], open[0], kind(names[shut[0]].orEmpty()))
        }
    }

    private fun row(configs: Configs, closed: Int, opened: Int, kind: String): String {
        val openOp =
            configs.locs
                .getValue(closed)
                .ops.ops
                .indexOfFirst { it.equals("Open", ignoreCase = true) } + 1
        val closeOp =
            configs.locs
                .getValue(opened)
                .ops.ops
                .indexOfFirst { it.equals("Close", ignoreCase = true) } + 1
        return "$closed\t$opened\t$kind\t$openOp\t$closeOp"
    }

    /** Double doors and gates are named per half: `..l`, `.._l`, `..left` / `..r`, `.._r`, `..right`. */
    private fun kind(name: String): String {
        val gate = name.contains("gate")
        val left = name.endsWith("_l") || name.endsWith("left") || (!gate && name.endsWith("doorl"))
        val right = name.endsWith("_r") || name.endsWith("right") || (!gate && name.endsWith("doorr"))
        return when {
            gate && left -> "gate_left"
            gate && right -> "gate_right"
            left -> "left"
            right -> "right"
            else -> "single"
        }
    }

    private fun isClimbOp(op: String) = UP.matches(op) || DOWN.matches(op) || EITHER.matches(op)

    private fun placements(configs: Configs, map: GameMap): List<Placement> {
        val all = ArrayList<Placement>()
        for (square in map.squares) {
            for (i in 0 until square.locCount) {
                val id = square.locIds[i]
                val type = configs.locs[id] ?: continue
                if (type.ops.ops.none { it != null && isClimbOp(it) }) continue
                val info = square.locInfo[i]
                val angle = MapSquare.locAngle(info)
                val rotated = angle == 1 || angle == TOP_LEVEL
                all +=
                    Placement(
                        id,
                        square.key.baseX + MapSquare.locX(info),
                        square.key.baseZ + MapSquare.locZ(info),
                        MapSquare.locLevel(info),
                        MapSquare.locShape(info),
                        angle,
                        if (rotated) type.length else type.width,
                        if (rotated) type.width else type.length,
                    ).also {
                        it.width = type.width
                        it.length = type.length
                        it.approach = type.forceApproach
                    }
            }
        }
        return all
    }

    private fun climbs(configs: Configs, map: GameMap, flags: CollisionFlagMap): Pair<List<String>, Int> {
        val all = placements(configs, map)
        val byPlane = all.groupBy { plane(it.level, it.z) }
        val rows = ArrayList<String>()
        val missing = HashMap<Int, Int>()
        for (p in all) {
            val type = configs.locs[p.id] ?: continue
            val links = links(p, type, configs, byPlane, flags)
            rows += links
            if (links.isEmpty()) missing.merge(p.id, 1, Int::plus)
        }
        for ((id, n) in missing.entries.sortedByDescending { it.value }.take(REPORT_TOP)) {
            println("  unlinked $n x $id '${configs.locs[id]?.name}' ${configs.locs[id]?.ops?.ops?.filterNotNull()}")
        }
        return rows.sorted() to missing.values.sum()
    }

    private fun plane(level: Int, z: Int): Long = level.toLong() shl Int.SIZE_BITS or (z / DUNGEON_OFFSET).toLong()

    /** One row per climb op of placement [p] that leads somewhere. */
    private fun links(
        p: Placement,
        type: LocType,
        configs: Configs,
        byPlane: Map<Long, List<Placement>>,
        flags: CollisionFlagMap,
    ): List<String> {
        val rows = ArrayList<String>()
        val stairs = if (type.name.contains("stair", ignoreCase = true)) 1 else 0
        val directions = type.ops.ops.count { it != null && (UP.matches(it) || DOWN.matches(it)) }
        type.ops.ops.forEachIndexed { index, op ->
            if (op == null || !isClimbOp(op)) return@forEachIndexed
            val either = EITHER.matches(op)
            // A lone "Climb" goes wherever a matching loc is (up first); no fallback tile, since agility rocks and
            // ledges use the same op on one level.
            val dest =
                if (either) {
                    destination(p, true, configs, byPlane, flags, fallback = false)
                        ?: destination(p, false, configs, byPlane, flags, fallback = false)
                } else {
                    destination(p, UP.matches(op), configs, byPlane, flags, fallback = climbable(type.name))
                } ?: return@forEachIndexed
            val option = if (either && directions >= 2) 1 else 0
            rows += "${p.x}\t${p.z}\t${p.level}\t${p.id}\t${index + 1}\t${dest.first}\t${dest.second}\t${dest.third}\t$stairs\t$option"
        }
        return rows
    }

    /** Only real ladders, stairs and trapdoors may use the "tile above/below" fallback. */
    private fun climbable(name: String): Boolean = CLIMBABLE.any { name.contains(it, ignoreCase = true) }

    private val CLIMBABLE = listOf("ladder", "stair", "trapdoor", "staircase")

    /** Planes a climb can lead to: the floor above/below, and the dungeon 6,400 tiles away. */
    private fun targets(p: Placement, up: Boolean): List<Triple<Int, Int, Int>> {
        val out = ArrayList<Triple<Int, Int, Int>>()
        if (up && p.level < TOP_LEVEL) out += Triple(p.x, p.z, p.level + 1)
        if (!up && p.level > 0) out += Triple(p.x, p.z, p.level - 1)
        if (up && p.z >= DUNGEON_OFFSET) out += Triple(p.x, p.z - DUNGEON_OFFSET, p.level)
        if (!up && p.z < DUNGEON_OFFSET) out += Triple(p.x, p.z + DUNGEON_OFFSET, p.level)
        return out
    }

    private fun destination(
        p: Placement,
        up: Boolean,
        configs: Configs,
        byPlane: Map<Long, List<Placement>>,
        flags: CollisionFlagMap,
        fallback: Boolean,
    ): Triple<Int, Int, Int>? {
        val cx = p.x * 2 + p.w
        val cz = p.z * 2 + p.l
        for ((tx, tz, tl) in targets(p, up)) {
            val dz = tz - p.z
            val plane = byPlane[plane(tl, tz)] ?: continue
            val match =
                plane
                    .filter { c ->
                        val type = configs.locs[c.id] ?: return@filter false
                        val reverse =
                            type.ops.ops.any {
                                it != null &&
                                    ((if (up) DOWN.matches(it) else UP.matches(it)) || EITHER.matches(it))
                            }
                        reverse && gap(c, tx, tz, p) <= SEARCH
                    }.minByOrNull { c -> gap(c, tx, tz, p) }
            if (match != null) {
                val tile = besides(match, flags) { x, z -> dist2(x * 2 + 1, z * 2 + 1, cx, cz + dz * 2) }
                if (tile != null) return Triple(tile.first, tile.second, tl)
            }
            if (!fallback) continue
            val near = nearestWalkable(tx + p.w / 2, tz + p.l / 2, tl, flags)
            if (near != null) return Triple(near.first, near.second, tl)
        }
        return null
    }

    /** Chebyshev gap between [c]'s footprint and [p]'s footprint moved to ([tx], [tz]). */
    private fun gap(c: Placement, tx: Int, tz: Int, p: Placement): Int {
        val dx = maxOf(0, c.x - (tx + p.w - 1), tx - (c.x + c.w - 1))
        val dz = maxOf(0, c.z - (tz + p.l - 1), tz - (c.z + c.l - 1))
        return maxOf(dx, dz)
    }

    private fun dist2(ax: Int, az: Int, bx: Int, bz: Int) = (ax - bx) * (ax - bx) + (az - bz) * (az - bz)

    private fun walkable(x: Int, z: Int, level: Int, flags: CollisionFlagMap): Boolean =
        flags[x, z, level] and (CollisionFlag.LOC or CollisionFlag.BLOCK_WALK) == 0 && flags.isZoneAllocated(x, z, level)

    /** The walkable tile next to [c]'s footprint that minimises [cost]. */
    private fun besides(c: Placement, flags: CollisionFlagMap, cost: (Int, Int) -> Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestCost = Int.MAX_VALUE
        for (x in c.x - 1..c.x + c.w) {
            for (z in c.z - 1..c.z + c.l) {
                if (x in c.x until c.x + c.w && z in c.z until c.z + c.l) continue
                if (!walkable(x, z, c.level, flags)) continue
                // The tile must be one the loc can be used from (not behind a wall next to it).
                val reach =
                    org.rsmod.routefinder.reach.ReachStrategy.reached(
                        flags,
                        c.level,
                        x,
                        z,
                        c.x,
                        c.z,
                        c.width,
                        c.length,
                        1,
                        c.angle,
                        c.shape,
                        c.approach,
                    )
                if (!reach) continue
                val k = cost(x, z)
                if (k < bestCost) {
                    bestCost = k
                    best = x to z
                }
            }
        }
        return best
    }

    private fun nearestWalkable(x: Int, z: Int, level: Int, flags: CollisionFlagMap): Pair<Int, Int>? {
        for (r in 0..FALLBACK) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == r &&
                        walkable(x + dx, z + dz, level, flags)
                    ) {
                        return (x + dx) to (z + dz)
                    }
                }
            }
        }
        return null
    }
}
