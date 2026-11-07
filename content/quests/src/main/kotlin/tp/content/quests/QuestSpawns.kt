package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Spawns of the NPCs the implemented quests need that `content/world` `npc-spawns.tsv` lacks (docs/mechanics/quests.md QU9).
 * Positions are the Wiki infobox maps; the wander range is half the map shape's smaller side, at most 5, as the spawn
 * importer does (docs/mechanics/npc-spawns.md SP2). If the spawn table later gains one of these NPCs, remove it here.
 */
public class QuestSpawns : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            for (s in SPAWNS) spawnNpc(s.npc, CoordGrid(s.x, s.z, s.level), s.wander)
        }
        // Fred's pen: the Wiki-derived spawn table puts the sheared variant of the sheep there; the quest needs the shearable ones.
        onNpcSpawn {
            val unsheared = SHEARED_TO_UNSHEARED[type] ?: return@onNpcSpawn
            if (coord.level == 0 && coord.x in PEN_X && coord.z in PEN_Z) transform(unsheared, restoreStats = false)
        }
    }

    private class Spawn(val npc: Int, val x: Int, val z: Int, val level: Int, val wander: Int)

    internal companion object {
        private val SPAWNS =
            listOf(
                Spawn(Npcs.COOK, 3209, 3214, 0, 3),
                Spawn(Npcs.FRED_THE_FARMER, 3189, 3273, 0, 3),
                Spawn(Npcs.SHEEP_SHEARER_THE_THING, 3203, 3267, 0, 5),
                Spawn(Npcs.MILLIE_THE_MILLER, 3167, 3307, 0, 5),
                Spawn(Npcs.GILLIE_THE_MILKMAID, 3253, 3275, 0, 3),
                Spawn(Npcs.FATHER_AERECK, 3243, 3210, 0, 5),
                Spawn(Npcs.ROMEO, 3211, 3425, 0, 5),
                Spawn(Npcs.JULIET, 3159, 3426, 1, 3),
                Spawn(Npcs.PHILLIPA, 3162, 3431, 1, 4),
                Spawn(Npcs.DRAUL_LEPTOC, 3161, 3436, 0, 5),
                Spawn(Npcs.FATHER_LAWRENCE, 3254, 3485, 0, 4),
                Spawn(Npcs.HEAD_WIZARD, 3103, 9571, 0, 0),
            )

        /** Fred's sheep pen (Wiki "Fred the Farmer" map 3189,3273 and "Sheep (penguins)" map 3203,3267 r=10). */
        private val PEN_X = 3188..3212
        private val PEN_Z = 3257..3277

        /** Sheared sheep NPC → the unsheared sheep of the same colour (symbols `sheepsheered*` / `sheepunsheered*`). */
        val SHEARED_TO_UNSHEARED: Map<Int, Int> =
            mapOf(
                Npcs.SHEEPSHEERED to Npcs.SHEEPUNSHEERED,
                Npcs.SHEEPSHEEREDG to Npcs.SHEEPUNSHEEREDG,
                Npcs.SHEEPSHEEREDW to Npcs.SHEEPUNSHEEREDW,
                Npcs.SHEEPSHEERED2 to Npcs.SHEEPUNSHEERED2,
                Npcs.SHEEPSHEERED2G to Npcs.SHEEPUNSHEERED2G,
                Npcs.SHEEPSHEERED2W to Npcs.SHEEPUNSHEERED2W,
                Npcs.SHEEPSHEERED3 to Npcs.SHEEPUNSHEERED3,
                Npcs.SHEEPSHEERED3G to Npcs.SHEEPUNSHEERED3G,
                Npcs.SHEEPSHEERED3W to Npcs.SHEEPUNSHEERED3W,
                Npcs.SHEEPSHEEREDSHAGGY to Npcs.SHEEPUNSHEEREDSHAGGY,
                Npcs.SHEEPSHEEREDSHAGGY2 to Npcs.SHEEPUNSHEEREDSHAGGY2,
            )
    }
}
