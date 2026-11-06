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
        onWorldStart {
            val text = Spawns::class.java.getResource("npc-spawns.tsv")?.readText() ?: error("npc-spawns.tsv missing")
            for (line in text.lineSequence().drop(1)) {
                val c = line.split('\t')
                if (c.size < COLUMNS) continue
                val wander = c[WANDER].toInt().let { if (it < 0) DEFAULT_WANDER else it }
                val level = c[LEVEL].toInt()
                val x = c[X].toInt()
                val z = c[Z].toInt()
                if (level !in 0..MAX_LEVEL || x !in 0 until MAX_COORD || z !in 0 until MAX_COORD) continue
                spawnNpc(c[NPC].toInt(), CoordGrid(x, z, level), wander)
            }
        }
    }

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

        /** OPEN-SP1: per-NPC wander ranges aren't published; RS Mod `NpcTypeBuilder.DEFAULT_WANDER_RANGE`. */
        const val DEFAULT_WANDER = 5
    }
}
