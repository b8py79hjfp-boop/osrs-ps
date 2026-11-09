package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Spawns of the NPCs the implemented quests need that `content/world` `npc-spawns.tsv` lacks (docs/mechanics/quests.md QU9);
 * the rest were removed once the table had them (each stood twice: seen in the client, two Father Aereck in the church).
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
                // The table has these three only as multi-NPC children / another form (npc_spawns.py QUEST_CHILDREN), which ignore
                // the quest varp. Every other quest NPC is in the table (Wiki infobox maps).
                Spawn(Npcs.HEAD_WIZARD, 3103, 9571, 0, 0),
                Spawn(Npcs.WIZARD_MIZGOG, 3104, 3164, 2, 3),
                Spawn(Npcs.SIR_AMIK_VARZE, 2960, 3337, 2, 3),
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
