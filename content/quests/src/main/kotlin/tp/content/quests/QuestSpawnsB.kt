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
 * gains one of these, remove it here (done 2026-11-09: 25 stood twice, seen in the client; the Dragon Slayer I Bentnoze and
 * Wartface 675/676 went too, the table's Goblin Diplomacy pair 669/670 hands over to that dialogue).
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
                Spawn(Npcs.CUSTOMS_OFFICER, 2950, 3151, 0, 5),
                Spawn(Npcs.CAPTAIN_TOBIAS, 3028, 3218, 0, 5),
                Spawn(Npcs.SEAMAN_LORRIS, 3028, 3218, 0, 5),
                Spawn(Npcs.SEAMAN_THRESNOR, 3028, 3218, 0, 5),
                // Prince Ali Rescue (Aggie is in the spawn table)
                Spawn(Npcs.LADY_KELI, 3128, 3244, 0, 4),
                Spawn(Npcs.JOE, 3124, 3244, 0, 4),
                Spawn(Npcs.PRINCE_ALI_PRISON, 3128, 3248, 0, 0),
                Spawn(Npcs.PRINCE_ALI_PALACE, 3286, 3163, 0, 3),
                // Shield of Arrav (Baraek, the Curator and Jonny the Beard / the Weaponsmaster variants are in the table)
                Spawn(Npcs.KING_ROALD, 3222, 3472, 0, 3),
                // Dragon Slayer I (Oziach, Duke Horacio, Wormbrain, Melzar and his maze, Elvarg are in the table)
                // X Marks the Spot
                Spawn(Npcs.VEOS_LUMBRIDGE, 3228, 3242, 0, 3),
                Spawn(Npcs.VEOS_SARIM, 3054, 3245, 0, 3),
                // The Corsair Curse (Chief Tess is in the table)
                Spawn(Npcs.CORSAIR_CAPTAIN_CROSSROADS, 3030, 3273, 0, 3),
                Spawn(Npcs.CORSCURS_NAVIGATOR_SICK, 2531, 2833, 1, 0),
                Spawn(Npcs.CORSCURS_THIEF_SICK, 2556, 2856, 1, 0),
                Spawn(Npcs.CORSCURS_CABINBOY_SICK, 2556, 2856, 1, 0),
                Spawn(Npcs.CORSCURS_COOK_SICK, 2545, 2862, 1, 0),
                // Misthalin Mystery: Abigale and Hewey at the swamp, and the island's multi-NPCs (varbit 3468 shows and hides them;
                // Wiki NPC maps: Sid 1615,4832, Tayten 1639,4838, Lacey 1629,4850, Mandy 1626,4840 and 1636,4817, shady figure
                // 1644,4819; the mirror and the unmasked Abigale stand in the showdown room west of the sapphire door).
                Spawn(Npcs.MISTMYST_ABIGALE, 3236, 3155, 0, 2),
                Spawn(Npcs.MISTMYST_HEWEY, 3236, 3155, 0, 2),
                Spawn(Npcs.MISTMYST_SID, 1615, 4832, 0, 2),
                Spawn(Npcs.MISTMYST_TAYTEN, 1639, 4838, 0, 2),
                Spawn(Npcs.MISTMYST_LACEY, 1629, 4850, 0, 0),
                Spawn(Npcs.MISTMYST_MANDY, 1626, 4840, 0, 2),
                Spawn(Npcs.MISTMYST_MANDY_POST, 1636, 4817, 0, 0),
                Spawn(Npcs.MISTMYST_KILLER_BACKGROUND, 1644, 4819, 0, 0),
                Spawn(Npcs.MISTMYST_MIRROR, 1623, 4830, 0, 0),
                Spawn(Npcs.MISTMYST_ABIGALE_CUTSCENE_MULTI, 1622, 4830, 0, 0),
            )
    }
}
