package tp.content.world

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.util.CoordGrid

/**
 * World NPC spawns from the OSRS Wiki (`npc-spawns.tsv`, written by tools/wiki-import/npc_spawns.py; CC BY-NC-SA 3.0,
 * data/PROVENANCE.md; docs/mechanics/npc-spawns.md). Each row is `npc x z level wander`; wander -1 means the default.
 */
public class Spawns : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStartLast {
            // Headless scenarios turn the world's NPCs off (golden tests need empty ground).
            if (System.getProperty("tp.worldSpawns") == "false") return@onWorldStartLast
            val text = Spawns::class.java.getResource("npc-spawns.tsv")?.readText() ?: error("npc-spawns.tsv missing")
            // SP3e: NPCs plugins placed (quest NPCs, minigame staff) win over the table's copy of the same NPC nearby.
            val placed = HashMap<String, MutableList<CoordGrid>>()
            forEachNpc { type, at -> placed.getOrPut(npcFamilyName(type)) { ArrayList() } += at }
            for (line in text.lineSequence().drop(1)) {
                val c = line.split('\t')
                if (c.size < COLUMNS) continue
                val wander = c[WANDER].toInt().let { if (it < 0) DEFAULT_WANDER else it }
                val level = c[LEVEL].toInt()
                val x = c[X].toInt()
                val z = c[Z].toInt()
                if (level !in 0..MAX_LEVEL || x !in 0 until MAX_COORD || z !in 0 until MAX_COORD) continue
                val type = c[NPC].toInt()
                val at = CoordGrid(x, z, level)
                if (placed[npcFamilyName(type)]?.any { near(it, at) } == true) continue
                spawnNpc(type, at, wander)
            }
        }
    }

    private fun near(a: CoordGrid, b: CoordGrid): Boolean =
        a.level == b.level && maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z)) <= YIELD_RADIUS

    private companion object {
        const val NPC = 0
        const val X = 1
        const val Z = 2
        const val LEVEL = 3
        const val WANDER = 4
        const val COLUMNS = 5
        const val MAX_LEVEL = 3

        /** The world is 16,384 tiles on each axis (CoordGrid). */
        const val MAX_COORD = 16384

        /**
         * SP3e: a table row yields to a plugin-spawned NPC of the same name this close (Chebyshev). Wiki pins and the
         * plugins' sourced tiles differ by up to 7 tiles (Osman: 3286 vs 3293).
         */
        const val YIELD_RADIUS = 8

        /** OPEN-SP1: per-NPC wander ranges aren't published; RS Mod `NpcTypeBuilder.DEFAULT_WANDER_RANGE`. */
        const val DEFAULT_WANDER = 5
    }
}
