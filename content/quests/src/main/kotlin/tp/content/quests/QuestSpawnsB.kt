package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Spawns of the "B" quest batch (Pirate's Treasure, Prince Ali Rescue, Shield of Arrav, Dragon Slayer I, Below Ice Mountain, The
 * Corsair Curse, Misthalin Mystery, X Marks the Spot) that `content/world` `npc-spawns.tsv` lacks (docs/mechanics/quests.md QU9).
 * Kept apart from [QuestSpawns] so the two quest batches merge cleanly. Positions are the Wiki infobox maps (the wander range is
 * the map shape's radius, at most 5, as the spawn importer does, docs/mechanics/npc-spawns.md SP2). NPCs whose look depends on the
 * quest variable are spawned as their multi-NPC parent (the cache's `multi` config). When the spawn table or the item spawn table
 * gains one of these, remove it here.
 */
public class QuestSpawnsB : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            for (s in SPAWNS) spawnNpc(s.npc, CoordGrid(s.x, s.z, s.level), s.wander)
            // Quest ground items (white apron, Phoenix crossbows) come from the general item-spawns table.
        }
    }

    private class Spawn(val npc: Int, val x: Int, val z: Int, val level: Int, val wander: Int)

    private companion object {
        val SPAWNS =
            listOf(
                // Pirate's Treasure and the Port Sarim - Musa Point ferry
                Spawn(Npcs.REDBEARD_FRANK, 3052, 3250, 0, 5),
                Spawn(Npcs.LUTHAS, 2939, 3154, 0, 5),
                Spawn(Npcs.CUSTOMS_OFFICER, 2950, 3151, 0, 5),
                Spawn(Npcs.ZEMBO, 2927, 3145, 0, 3),
                Spawn(Npcs.WYDIN, 3013, 3207, 0, 5),
                Spawn(Npcs.CAPTAIN_TOBIAS, 3028, 3218, 0, 5),
                Spawn(Npcs.SEAMAN_LORRIS, 3028, 3218, 0, 5),
                Spawn(Npcs.SEAMAN_THRESNOR, 3028, 3218, 0, 5),
                // Prince Ali Rescue (Aggie is in the spawn table)
                Spawn(Npcs.HASSAN, 3302, 3163, 0, 3),
                Spawn(Npcs.OSMAN, 3286, 3180, 0, 3),
                Spawn(Npcs.LEELA, 3113, 3262, 0, 3),
                Spawn(Npcs.NED, 3099, 3259, 0, 3),
                Spawn(Npcs.LADY_KELI, 3128, 3244, 0, 4),
                Spawn(Npcs.JOE, 3124, 3244, 0, 4),
                Spawn(Npcs.PRINCE_ALI_PRISON, 3128, 3248, 0, 0),
                Spawn(Npcs.PRINCE_ALI_PALACE, 3286, 3163, 0, 3),
                // Shield of Arrav (Baraek, the Curator and Jonny the Beard / the Weaponsmaster variants are in the table)
                Spawn(Npcs.TRAMPPG, 3206, 3390, 0, 4),
                Spawn(Npcs.KATRINE, 3186, 3385, 0, 5),
                Spawn(Npcs.STRAVEN, 3246, 9780, 0, 3),
                Spawn(Npcs.KING_ROALD, 3222, 3472, 0, 3),
                // Dragon Slayer I (Oziach, Duke Horacio, Wormbrain, Melzar and his maze, Elvarg are in the table)
                Spawn(Npcs.GUILDMASTER, 3191, 3360, 0, 4),
                Spawn(Npcs.ORACLE, 3011, 3501, 0, 4),
                Spawn(Npcs.KLARENSE, 3047, 3204, 0, 3),
                Spawn(Npcs.GENERAL_BENTNOZE, 2959, 3513, 0, 3),
                Spawn(Npcs.GENERAL_WARTFACE, 2959, 3513, 0, 3),
                Spawn(Npcs.SARIM_PUB_SITTING_PATRON, 3050, 3257, 0, 0),
                Spawn(Npcs.SARIM_PUB_DRINKER_1, 3050, 3257, 0, 0),
                Spawn(Npcs.SARIM_PUB_DRINKER_2, 3047, 3258, 0, 0),
                // Below Ice Mountain
                Spawn(Npcs.BIM_WILLOW, 3002, 3436, 0, 4),
                Spawn(Npcs.BIM_CHECKAL, 3087, 3415, 0, 2),
                Spawn(Npcs.BIM_BURNTOF, 2957, 3367, 0, 2),
                Spawn(Npcs.BIM_MARLEY, 3088, 3472, 0, 2),
                Spawn(Npcs.BIM_ATLAS, 3075, 3439, 0, 0),
                Spawn(Npcs.FAI_VARROCK_BLUEMOON_CHEF, 3230, 3400, 0, 0),
                // X Marks the Spot
                Spawn(Npcs.VEOS_LUMBRIDGE, 3228, 3242, 0, 3),
                Spawn(Npcs.VEOS_SARIM, 3054, 3245, 0, 3),
                // The Corsair Curse (Chief Tess is in the table)
                Spawn(Npcs.CORSAIR_CAPTAIN_CROSSROADS, 3030, 3273, 0, 3),
                Spawn(Npcs.CORSAIR_CAPTAIN_2OPS, 2910, 3226, 0, 0),
                Spawn(Npcs.CORSAIR_CAPTAIN_2OPS, 2574, 2835, 0, 0),
                Spawn(Npcs.CORSCURS_NAVIGATOR_SICK, 2531, 2833, 1, 0),
                Spawn(Npcs.CORSCURS_THIEF_SICK, 2556, 2856, 1, 0),
                Spawn(Npcs.CORSCURS_CABINBOY_SICK, 2556, 2856, 1, 0),
                Spawn(Npcs.CORSCURS_COOK_SICK, 2545, 2862, 1, 0),
                // Misthalin Mystery (Mandy is in the table)
                Spawn(Npcs.MISTMYST_ABIGALE, 3236, 3155, 0, 2),
                Spawn(Npcs.MISTMYST_HEWEY, 3236, 3155, 0, 2),
            )
    }
}
