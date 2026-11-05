@file:Suppress("MagicNumber") // spawn coordinates are data, documented in the KDoc below

package tp.content.dev

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * A handful of Lumbridge NPCs so interactions and random walk can be tried in-game. Dev data only: positions are
 * approximate and not sourced; real spawns come from tools/wiki-import with provenance (phase-3 plan, risks).
 */
public class DevSpawns : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            spawnNpc(Npcs.HANS, CoordGrid(3221, 3221), wanderRange = 5)
            spawnNpc(Npcs.MAN, CoordGrid(3226, 3216), wanderRange = 4)
            spawnNpc(Npcs.WOMAN, CoordGrid(3218, 3214), wanderRange = 4)
            spawnNpc(Npcs.LUMBRIDGE_GUIDE, CoordGrid(3238, 3220), wanderRange = 0)
            for (i in 0 until GOBLINS) spawnNpc(Npcs.GOBLIN, CoordGrid(3245 + i * 2, 3245), wanderRange = 6)
            for (i in 0 until COWS) spawnNpc(Npcs.COW, CoordGrid(3256 + i * 2, 3266), wanderRange = 6)
        }
    }

    private companion object {
        const val GOBLINS = 3
        const val COWS = 4
    }
}
