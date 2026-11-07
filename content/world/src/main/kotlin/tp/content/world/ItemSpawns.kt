package tp.content.world

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.util.CoordGrid

/**
 * Static item spawns from the OSRS Wiki (`item-spawns.tsv`, written by tools/wiki-import/item_spawns.py; CC BY-NC-SA 3.0,
 * data/PROVENANCE.md; docs/mechanics/ground-items.md G6). Each row is `obj count x z level members`; the server is
 * members-enabled, so members rows spawn too. Every spawn is public, never despawns and reappears [RESPAWN_TICKS] after a take.
 */
public class ItemSpawns : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            // Headless scenarios turn the world's spawns off (golden tests need empty ground).
            if (System.getProperty("tp.worldSpawns") == "false") return@onWorldStart
            val text = ItemSpawns::class.java.getResource("item-spawns.tsv")?.readText() ?: error("item-spawns.tsv missing")
            for (line in text.lineSequence().drop(1)) {
                val c = line.split('\t')
                if (c.size < COLUMNS) continue
                val level = c[LEVEL].toInt()
                val x = c[X].toInt()
                val z = c[Z].toInt()
                if (level !in 0..MAX_LEVEL || x !in 0 until MAX_COORD || z !in 0 until MAX_COORD) continue
                spawnGroundObj(c[OBJ].toInt(), c[COUNT].toInt(), CoordGrid(x, z, level), RESPAWN_TICKS)
            }
        }
    }

    private companion object {
        const val OBJ = 0
        const val COUNT = 1
        const val X = 2
        const val Z = 3
        const val LEVEL = 4
        const val COLUMNS = 6
        const val MAX_LEVEL = 3

        /** The world is 16,384 tiles on each axis (CoordGrid). */
        const val MAX_COORD = 16384

        /** OPEN-G6: the Wiki gives no respawn time (its `respawn:` values have no published unit); 100 ticks for every spawn. */
        const val RESPAWN_TICKS = 100
    }
}
