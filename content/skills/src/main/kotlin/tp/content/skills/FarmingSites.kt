@file:Suppress("MagicNumber") // data tables: loc ids and tiles read from cache 2757 (docs/mechanics/farming.md F8)

package tp.content.skills

import tp.symbols.Npcs
import tp.symbols.Varbits

/**
 * The farms covered (docs/mechanics/farming.md F8): every farming patch and compost bin of the Wiki "Farming/Patch locations"
 * that the cache has a loc for, except grape vines and coral (OPEN-FA6) and the quest-only patches. Loc ids are the multi-loc
 * parents with `multiVarbit` 4771 .. 4775, 7904 .. 7914 found with `find-locs`; the varbit slot of each patch and its gardener
 * follow RuneLite's `FarmingWorld` (BSD-2, D-044). A farm's anchor is the middle of its patches.
 */
internal object FarmingSites {
    /** Half the client's 104-tile scene: patches farther than this from the player are not loaded by the client. */
    const val SITE_RADIUS = 52

    private const val A = Varbits.FARMING_TRANSMIT_A
    private const val B = Varbits.FARMING_TRANSMIT_B
    private const val C = Varbits.FARMING_TRANSMIT_C
    private const val D = Varbits.FARMING_TRANSMIT_D
    private const val E = Varbits.FARMING_TRANSMIT_E
    private const val F = Varbits.FARMING_TRANSMIT_F
    private const val G = Varbits.FARMING_TRANSMIT_G
    private const val H = Varbits.FARMING_TRANSMIT_H
    private const val I = Varbits.FARMING_TRANSMIT_I
    private const val J = Varbits.FARMING_TRANSMIT_J
    private const val K = Varbits.FARMING_TRANSMIT_K
    private const val L = Varbits.FARMING_TRANSMIT_L
    private const val M = Varbits.FARMING_TRANSMIT_M
    private const val N = Varbits.FARMING_TRANSMIT_N

    @Suppress("LongParameterList") // the patch rows below
    private fun patch(
        type: PatchType,
        slot: Int,
        name: String,
        vararg locs: Int,
        farmer: Int = -1,
        pay: Int = 0,
        never: Boolean = false,
        guild: Boolean = false,
    ): Patch = Patch(locs, type, slot, name, if (farmer >= 0) intArrayOf(farmer) else IntArray(0), pay, never, guild)

    /** The allotment (A, B), flower (C) and herb (D) patches and the compost bin (E) of a standard farm. */
    private fun farm(name: String, x: Int, z: Int, farmer: Int, a: Int, b: Int, flower: Int, herb: Int, bin: Int): Site =
        Site(
            name,
            x,
            z,
            listOf(
                patch(PatchType.ALLOTMENT, A, "$name north allotment", a, farmer = farmer, pay = 0),
                patch(PatchType.ALLOTMENT, B, "$name south allotment", b, farmer = farmer, pay = 1),
                patch(PatchType.FLOWER, C, "$name flower patch", flower),
                patch(PatchType.HERB, D, "$name herb patch", herb),
            ),
            listOf(Bin(bin, E, "$name compost bin")),
        )

    private fun single(name: String, x: Int, z: Int, type: PatchType, slot: Int, loc: Int, farmer: Int = -1, never: Boolean = false): Site =
        Site(name, x, z, listOf(patch(type, slot, name, loc, farmer = farmer, never = never)))

    private val REDWOOD_LOCS: IntArray = (34051..34059).toList().plus(34632..34641).toIntArray()

    val SITES: List<Site> =
        listOf(
            farm("Falador", 3055, 3308, Npcs.ELSTAN, 8550, 8551, 7847, 8150, 7836),
            farm("Catherby", 2810, 3463, Npcs.DANTAERA, 8552, 8553, 7848, 8151, 7837),
            farm("Ardougne", 2666, 3374, Npcs.KRAGEN, 8554, 8555, 7849, 8152, 7839),
            farm("Port Phasmatys", 3602, 3525, Npcs.LYRA, 8556, 8557, 7850, 8153, 7838),
            farm("Civitas illa Fortis", 1585, 3098, Npcs.FORTIS_GARDENER, 50696, 50695, 50693, 50697, 50694),
            Site(
                "Hosidius",
                1735,
                3554,
                listOf(
                    patch(
                        PatchType.ALLOTMENT,
                        A,
                        "Hosidius north east allotment",
                        27113,
                        farmer = Npcs.HOSIDIUS_ALLOTMENT_GARDENER,
                        pay = 0,
                    ),
                    patch(
                        PatchType.ALLOTMENT,
                        B,
                        "Hosidius south west allotment",
                        27114,
                        farmer = Npcs.HOSIDIUS_ALLOTMENT_GARDENER,
                        pay = 1,
                    ),
                    patch(PatchType.FLOWER, C, "Hosidius flower patch", 27111),
                    patch(PatchType.HERB, D, "Hosidius herb patch", 27115),
                    patch(PatchType.SPIRIT_TREE, F, "Hosidius spirit tree patch", 27116, farmer = Npcs.FARMING_GARDENER_SPIRIT_TREE_4),
                ),
                listOf(Bin(27112, E, "Hosidius compost bin")),
            ),
            Site(
                "Prifddinas",
                3290,
                6105,
                listOf(
                    patch(PatchType.ALLOTMENT, A, "Prifddinas north allotment", 34922, farmer = Npcs.PRIF_GARDENER, pay = 0),
                    patch(PatchType.ALLOTMENT, B, "Prifddinas south allotment", 34921, farmer = Npcs.PRIF_GARDENER, pay = 1),
                    patch(PatchType.FLOWER, C, "Prifddinas flower patch", 34919),
                    patch(PatchType.CRYSTAL_TREE, E, "Prifddinas crystal tree patch", 34906),
                ),
                listOf(Bin(34920, D, "Prifddinas compost bin")),
            ),
            Site(
                "Harmony Island",
                3792,
                2836,
                listOf(
                    patch(PatchType.ALLOTMENT, A, "Harmony Island allotment", 21950),
                    patch(PatchType.HERB, B, "Harmony Island herb patch", 9372, never = true),
                ),
            ),
            single("Trollheim herb patch", 2826, 3694, PatchType.HERB, A, 18816, never = true),
            single("Weiss herb patch", 2848, 3934, PatchType.HERB, A, 33176, never = true),
            // hops
            single("Lumbridge hops patch", 3229, 3315, PatchType.HOPS, A, 8175, Npcs.FARMING_GARDENER_HOPS_3),
            single("Seers' Village hops patch", 2666, 3525, PatchType.HOPS, A, 8176, Npcs.FARMING_GARDENER_HOPS_4),
            single("Yanille hops patch", 2575, 3104, PatchType.HOPS, A, 8173, Npcs.FARMING_GARDENER_HOPS_1),
            single("Entrana hops patch", 2810, 3336, PatchType.HOPS, A, 8174, Npcs.FRANCIS),
            single("Aldarin hops patch", 1364, 2938, PatchType.HOPS, A, 55341, Npcs.FARMING_GARDENER_HOPS_5),
            // bushes
            single("Champions' Guild bush patch", 3181, 3357, PatchType.BUSH, A, 7577, Npcs.FARMING_GARDENER_BUSH_1),
            single("Rimmington bush patch", 2940, 3221, PatchType.BUSH, A, 7578, Npcs.FARMING_GARDENER_BUSH_2),
            single("Ardougne bush patch", 2617, 3225, PatchType.BUSH, A, 7580, Npcs.FARMING_GARDENER_BUSH_4),
            Site(
                "Etceteria",
                2600,
                3860,
                listOf(
                    patch(PatchType.BUSH, A, "Etceteria bush patch", 7579, farmer = Npcs.FARMING_GARDENER_BUSH_3),
                    patch(PatchType.SPIRIT_TREE, B, "Etceteria spirit tree patch", 8382, farmer = Npcs.FARMING_GARDENER_SPIRIT_TREE_2),
                ),
            ),
            // trees
            single("Taverley tree patch", 2935, 3437, PatchType.TREE, A, 8388, Npcs.FARMING_GARDENER_TREE_1),
            single("Falador tree patch", 3003, 3372, PatchType.TREE, A, 8389, Npcs.FARMING_GARDENER_TREE_2),
            single("Varrock tree patch", 3228, 3458, PatchType.TREE, A, 8390, Npcs.FARMING_GARDENER_TREE_3_02),
            single("Lumbridge tree patch", 3192, 3230, PatchType.TREE, A, 8391, Npcs.FARMING_GARDENER_TREE_4),
            single("Auburnvale tree patch", 1365, 3320, PatchType.TREE, A, 56953, Npcs.FARMING_GARDENER_TREE_7),
            Site(
                "Gnome Stronghold",
                2455,
                3430,
                listOf(
                    patch(PatchType.TREE, A, "Gnome Stronghold tree patch", 19147, 19146, farmer = Npcs.FARMING_GARDENER_TREE_GNOME),
                    patch(PatchType.FRUIT_TREE, B, "Gnome Stronghold fruit tree patch", 7962, farmer = Npcs.FARMING_GARDENER_FRUIT_1),
                ),
            ),
            // fruit trees
            single("Tree Gnome Village fruit tree patch", 2489, 3179, PatchType.FRUIT_TREE, A, 7963, Npcs.FARMING_GARDENER_FRUIT_2),
            Site(
                "Brimhaven",
                2780,
                3207,
                listOf(
                    patch(PatchType.FRUIT_TREE, A, "Brimhaven fruit tree patch", 7964, farmer = Npcs.GARTH),
                    patch(PatchType.SPIRIT_TREE, B, "Brimhaven spirit tree patch", 8383, farmer = Npcs.FARMING_GARDENER_SPIRIT_TREE_3),
                ),
            ),
            single("Catherby fruit tree patch", 2860, 3433, PatchType.FRUIT_TREE, A, 7965, Npcs.FARMING_GARDENER_FRUIT_4),
            single("Lletya fruit tree patch", 2346, 3161, PatchType.FRUIT_TREE, A, 26579, Npcs.FARMING_GARDENER_FRUIT_TREE_5),
            Site(
                "Kastori",
                1358,
                3040,
                listOf(
                    patch(PatchType.CALQUAT, A, "Kastori calquat patch", 56954, farmer = Npcs.FARMING_GARDENER_CALQUAT_2),
                    patch(PatchType.FRUIT_TREE, B, "Kastori fruit tree patch", 56955, farmer = Npcs.FARMING_GARDENER_FRUIT_7),
                    patch(PatchType.FLOWER, C, "Kastori flower patch", 56959),
                ),
            ),
            // spirit trees
            single("Port Sarim spirit tree patch", 3059, 3257, PatchType.SPIRIT_TREE, A, 8338, Npcs.FARMING_GARDENER_SPIRIT_TREE_1),
            // calquat
            single("Tai Bwo Wannai calquat patch", 2795, 3100, PatchType.CALQUAT, A, 7807, Npcs.FARMING_GARDENER_CALQUAT),
            single("Great Conch calquat patch", 3127, 2404, PatchType.CALQUAT, C, 58675, Npcs.FARMING_GARDENER_CALQUAT_3),
            // hardwood
            single("Anglers' Retreat hardwood patch", 2470, 2702, PatchType.HARDWOOD_TREE, A, 58834, Npcs.FARMING_GARDENER_HARDWOOD_TREE_5),
            single("Avium Savannah hardwood patch", 1686, 2971, PatchType.HARDWOOD_TREE, A, 50692, Npcs.FROG_QUEST_MARCELLUS),
            Site(
                "Fossil Island",
                3708,
                3834,
                listOf(
                    patch(
                        PatchType.HARDWOOD_TREE,
                        A,
                        "Fossil Island east hardwood patch",
                        30482,
                        31300,
                        farmer = Npcs.FOSSIL_SQUIRREL_GARDENER1,
                    ),
                    patch(
                        PatchType.HARDWOOD_TREE,
                        B,
                        "Fossil Island middle hardwood patch",
                        30480,
                        31301,
                        farmer = Npcs.FOSSIL_SQUIRREL_GARDENER2,
                    ),
                    patch(
                        PatchType.HARDWOOD_TREE,
                        C,
                        "Fossil Island west hardwood patch",
                        30481,
                        31302,
                        farmer = Npcs.FOSSIL_SQUIRREL_GARDENER3,
                    ),
                ),
            ),
            Site(
                "Fossil Island seaweed",
                3733,
                10270,
                listOf(
                    patch(
                        PatchType.SEAWEED,
                        A,
                        "Fossil Island north seaweed patch",
                        30500,
                        farmer = Npcs.FOSSIL_GARDENER_UNDERWATER,
                        pay = 0,
                    ),
                    patch(
                        PatchType.SEAWEED,
                        B,
                        "Fossil Island south seaweed patch",
                        30501,
                        farmer = Npcs.FOSSIL_GARDENER_UNDERWATER,
                        pay = 1,
                    ),
                ),
            ),
            // special
            single("Al Kharid cactus patch", 3315, 3202, PatchType.CACTUS, A, 7771, Npcs.FARMING_GARDENER_CACTUS),
            single("Morytania mushroom patch", 3451, 3472, PatchType.MUSHROOM, A, 8337),
            single("Draynor Manor belladonna patch", 3086, 3354, PatchType.BELLADONNA, A, 7572),
            single("Auburnvale belladonna patch", 1450, 3354, PatchType.BELLADONNA, B, 56960),
            // the Farming Guild
            Site(
                "Farming Guild",
                1248,
                3738,
                listOf(
                    patch(
                        PatchType.SPIRIT_TREE,
                        A,
                        "Farming Guild spirit tree patch",
                        33733,
                        farmer = Npcs.FARMING_GARDENER_SPIRIT_TREE_5,
                        guild = true,
                    ),
                    patch(
                        PatchType.BUSH,
                        B,
                        "Farming Guild bush patch",
                        34006,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_T1,
                        pay = 3,
                        guild = true,
                    ),
                    patch(
                        PatchType.ALLOTMENT,
                        C,
                        "Farming Guild north allotment",
                        33694,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_T1,
                        pay = 1,
                        guild = true,
                    ),
                    patch(
                        PatchType.ALLOTMENT,
                        D,
                        "Farming Guild south allotment",
                        33693,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_T1,
                        pay = 2,
                        guild = true,
                    ),
                    patch(PatchType.HERB, E, "Farming Guild herb patch", 33979, guild = true),
                    patch(
                        PatchType.CACTUS,
                        F,
                        "Farming Guild cactus patch",
                        33761,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_T1,
                        pay = 0,
                        guild = true,
                    ),
                    patch(
                        PatchType.TREE,
                        G,
                        "Farming Guild tree patch",
                        33732,
                        33731,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_T2,
                        guild = true,
                    ),
                    patch(PatchType.FLOWER, H, "Farming Guild flower patch", 33649, guild = true),
                    patch(
                        PatchType.REDWOOD,
                        I,
                        "Farming Guild redwood patch",
                        *REDWOOD_LOCS,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_REDWOOD,
                        guild = true,
                    ),
                    patch(
                        PatchType.FRUIT_TREE,
                        K,
                        "Farming Guild fruit tree patch",
                        34007,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_T3,
                        guild = true,
                    ),
                    patch(
                        PatchType.CELASTRUS,
                        L,
                        "Farming Guild celastrus patch",
                        34629,
                        farmer = Npcs.FARMING_GARDENER_FARMGUILD_CELASTRUS,
                        guild = true,
                    ),
                    patch(PatchType.ANIMA, M, "Farming Guild anima patch", 33998, guild = true),
                ),
                listOf(Bin(34631, N, "Farming Guild giant compost bin", big = true)),
            ),
            Site("Hespori", 1246, 10086, listOf(patch(PatchType.HESPORI, J, "Hespori patch", 34630, guild = true))),
        )

    /** The anima patch (Attas, Iasor and Kronos plants act worldwide while the plant lives, Wiki "Anima seed"). */
    val ANIMA_PATCH: Patch = SITES.first { it.name == "Farming Guild" }.patches.first { it.type == PatchType.ANIMA }

    /**
     * Tool leprechaun npc ids of the cache (`config npc`: "Tool Leprechaun" 0 and 12109 [Draynor variant], 12765 [Varlamore] and
     * 7757 [underwater]; Wiki "Tool Leprechaun" lists all four).
     */
    val LEPRECHAUNS: IntArray =
        intArrayOf(
            Npcs.FARMING_TOOLS_LEPRECHAUN,
            Npcs.FARMING_TOOLS_LEPRECHAUN_DRAYNOR,
            Npcs.FARMING_TOOLS_LEPRECHAUN_VARLAMORE,
            Npcs.FOSSIL_LEPRECHAUN_UNDERWATER,
        )

    /** Index of the site nearest ([x], [z]) within [SITE_RADIUS] (Chebyshev), or -1. */
    fun nearestSite(x: Int, z: Int): Int {
        var best = -1
        var bestDistance = Int.MAX_VALUE
        for (i in SITES.indices) {
            val site = SITES[i]
            val d = maxOf(kotlin.math.abs(site.x - x), kotlin.math.abs(site.z - z))
            if (d <= SITE_RADIUS && d < bestDistance) {
                best = i
                bestDistance = d
            }
        }
        return best
    }
}
