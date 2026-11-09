package tp.tools.cachedump

import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag
import tp.cache.config.Configs
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks NPC spawn rows (`npc x z level wander`, the `npc-spawns.tsv` format) against the cache's collision map
 * (docs/mechanics/npc-spawns.md SP10). Writes one TSV row per spawn whose tile an NPC can't stand on:
 * `npc x z level reason free_x free_z`, where `reason` is `nomap` (no map square loaded there), `floor` (blocked floor:
 * water, void, a roof edge) or `loc` (inside solid scenery), and `free_x free_z` is the nearest standable tile within
 * [SEARCH] tiles on the same level (`-` when there is none). Large NPCs are checked on their south-west tile only.
 */
internal object SpawnCheck {
    /** How far (Chebyshev) the nearest standable tile is searched. */
    private const val SEARCH = 3

    private const val COLUMNS = 4

    fun write(cache: Js5Cache, cacheDir: Path, spawns: Path, out: Path) {
        val configs = Configs.load(cache)
        val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
        val flags = CollisionBuilder.build(map, configs.locs)
        val lines = ArrayList<String>()
        var checked = 0
        for (line in Files.readAllLines(spawns).drop(1)) {
            val c = line.split('\t')
            if (c.size < COLUMNS) continue
            val v = c.take(COLUMNS).map(String::toInt)
            val npc = v[0]
            val x = v[1]
            val z = v[2]
            val level = v[COLUMNS - 1]
            checked++
            val reason = reason(flags[x, z, level]) ?: continue
            val free = nearestFree(flags, x, z, level)
            lines += listOf(npc, x, z, level, reason, free?.first ?: "-", free?.second ?: "-").joinToString("\t")
        }
        Files.write(out, listOf("npc\tx\tz\tlevel\treason\tfree_x\tfree_z") + lines)
        println("$checked spawns checked, ${lines.size} on tiles an NPC can't stand on -> $out")
    }

    private fun reason(flag: Int): String? =
        when {
            flag == CollisionFlagMap.DEFAULT_COLLISION_FLAG -> "nomap"
            flag and CollisionFlag.BLOCK_WALK != 0 -> "floor"
            flag and CollisionFlag.LOC != 0 -> "loc"
            else -> null
        }

    private fun nearestFree(flags: CollisionFlagMap, x: Int, z: Int, level: Int): Pair<Int, Int>? {
        for (r in 1..SEARCH) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                    if (reason(flags[x + dx, z + dz, level]) == null) return (x + dx) to (z + dz)
                }
            }
        }
        return null
    }
}
