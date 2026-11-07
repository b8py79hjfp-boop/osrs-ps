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
                // Imp Catcher, Doric's Quest, Witch's Potion, Ernest the Chicken, Goblin Diplomacy (Wiki infobox maps).
                Spawn(Npcs.WIZARD_MIZGOG, 3104, 3164, 2, 3),
                Spawn(Npcs.WIZARD_GRAYZAG, 3110, 3161, 2, 3),
                Spawn(Npcs.DORIC, 2952, 3452, 0, 4),
                Spawn(Npcs.HETTY, 2968, 3206, 0, 4),
                Spawn(Npcs.VERONICA, 3110, 3330, 0, 3),
                Spawn(Npcs.PROFESSOR_ODDENSTEIN, 3111, 3367, 2, 4),
                Spawn(Npcs.ERNEST_THE_CHICKEN, 3109, 3366, 2, 4),
                Spawn(Npcs.GENERAL_BENTNOZE_RED, 2959, 3513, 0, 3),
                Spawn(Npcs.GENERAL_WARTFACE_GREEN, 2959, 3513, 0, 3),
                Spawn(Npcs.CATWALK_GOBLIN_BROWN, 2959, 3513, 0, 3),
                // The Knight's Sword, Black Knights' Fortress, Vampyre Slayer, Demon Slayer.
                Spawn(Npcs.SQUIRE, 2977, 3342, 0, 3),
                Spawn(Npcs.RELDO_NORMAL, 3210, 3495, 0, 3),
                Spawn(Npcs.THURGO, 3001, 3144, 0, 2),
                Spawn(Npcs.SIR_VYVIN, 2984, 3339, 2, 5),
                Spawn(Npcs.SIR_AMIK_VARZE, 2960, 3337, 2, 3),
                Spawn(Npcs.MORGAN, 3099, 3269, 0, 3),
                Spawn(Npcs.DR_HARLOW, 3222, 3397, 0, 4),
                Spawn(Npcs.ARIS, 3204, 3425, 0, 3),
                Spawn(Npcs.SIR_PRYSIN, 3203, 3472, 0, 4),
                Spawn(Npcs.CAPTAIN_ROVIN, 3204, 3497, 2, 4),
                Spawn(Npcs.TRAIBORN, 3112, 3157, 1, 3),
                // Goblin Diplomacy's woad leaves (Wiki "Wyson the gardener" map 3027,3379 r=3).
                Spawn(Npcs.WYSON, 3027, 3379, 0, 3),
                // Dragon Slayer I's planks: the Varrock sawmill operator (Wiki "Sawmill operator" map 3302,3492 r=4).
                Spawn(Npcs.POH_SAWMILL_OPP, 3302, 3492, 0, 4),
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
