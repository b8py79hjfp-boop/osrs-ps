@file:Suppress("MagicNumber") // data tables: cache ids, xp and gates sourced in docs/mechanics/smithing.md

package tp.content.skills

import tp.symbols.Components
import tp.symbols.Objs

/**
 * One metal of the anvil interface: [index] is the value of varbit `smithing_bar_type` (cache enum 1253: 1 bronze .. 6
 * rune), [bar] the bar it is made of and [products] the product obj on each interface component (client script 430).
 * Smithing xp is 12.5 per bar times [index] (Wiki "Smithing": 1 bar of bronze is 12.5 xp, iron 25, steel 37.5, ...).
 */
internal class AnvilBar(val index: Int, val bar: Int, val products: Map<Int, Int>)

/** Anvil, furnace and wheel locs of the cache that carry the op (cache loc config: name "Anvil" op "Smith", ...). */
internal object SmithingData {
    /** Smithing xp of one bronze bar (Wiki "Smithing": "1 bar is 12.5 exp"). */
    const val XP_PER_BAR = 12.5

    /** Smithing xp of a gold bar smelted in goldsmith gauntlets (Wiki "Smelting": "22.5 (56.2)"). */
    const val GOLDSMITH_GOLD_XP = 56.2

    /** Charges of a new ring of forging (Wiki "Ring of forging": 140; RuneLite `MAX_RING_OF_FORGING_CHARGES`). */
    const val FORGING_CHARGES = 140

    /** Worn-inventory slot of the ring (cache wear position 12). */
    const val RING_SLOT = 12

    /** Worn-inventory slot of the gloves (cache wear position 9). */
    const val GLOVES_SLOT = 9

    /**
     * Client script 430 shows claws once varp 314 (`death_equiproom`) is at least 80 and dart tips once varp 197
     * (`desertrescue`) is at least 16.
     */
    const val CLAWS_VARP_MIN = 80
    const val DART_TIPS_VARP_MIN = 16

    /** Furnaces: cache locs named "Furnace" with the op "Smelt" (op 2); the Blast Furnace and quest furnaces are not here. */
    val FURNACES: IntArray =
        intArrayOf(
            2030,
            3294,
            4304,
            6189,
            11010,
            12100,
            12809,
            16469,
            16657,
            24009,
            26300,
            28565,
            33504,
            36555,
            39241,
            43895,
            47927,
            48171,
            50698,
            51509,
            56366,
            57788,
            58661,
            60145,
        )

    /** Anvils: cache locs named "Anvil" with the op "Smith" (op 1). */
    val ANVILS: IntArray =
        intArrayOf(2031, 2097, 4306, 6150, 22725, 28563, 31623, 32215, 39242, 40725, 51501, 56368, 58657)

    /** The six metals of the anvil interface, lowest first. */
    val ANVIL_BARS: List<AnvilBar> =
        listOf(
            bar(
                1,
                Objs.BRONZE_BAR,
                listOf(
                    Components.Smithing.DAGGER to 1205,
                    Components.Smithing.SWORD to 1277,
                    Components.Smithing.SCIMITAR to 1321,
                    Components.Smithing.LONGSWORD to 1291,
                    Components.Smithing._2H to 1307,
                    Components.Smithing.AXE to 1351,
                    Components.Smithing.MACE to 1422,
                    Components.Smithing.WARHAMMER to 1337,
                    Components.Smithing.BATTLEAXE to 1375,
                    Components.Smithing.CLAWS to 3095,
                    Components.Smithing.CHAINBODY to 1103,
                    Components.Smithing.PLATELEGS to 1075,
                    Components.Smithing.PLATESKIRT to 1087,
                    Components.Smithing.PLATEBODY to 1117,
                    Components.Smithing.NAILS to 4819,
                    Components.Smithing.MEDHELM to 1139,
                    Components.Smithing.FULLHELM to 1155,
                    Components.Smithing.SQUARESHIELD to 1173,
                    Components.Smithing.KITESHIELD to 1189,
                    Components.Smithing.DARTTIPS to 819,
                    Components.Smithing.ARROWHEADS to 39,
                    Components.Smithing.KNIVES to 864,
                    Components.Smithing.OTHER_1 to 1794,
                    Components.Smithing.OTHER_3 to 19570,
                    Components.Smithing.BOLTS to 9375,
                    Components.Smithing.LIMBS to 9420,
                ),
            ),
            bar(
                2,
                Objs.IRON_BAR,
                listOf(
                    Components.Smithing.DAGGER to 1203,
                    Components.Smithing.SWORD to 1279,
                    Components.Smithing.SCIMITAR to 1323,
                    Components.Smithing.LONGSWORD to 1293,
                    Components.Smithing._2H to 1309,
                    Components.Smithing.AXE to 1349,
                    Components.Smithing.MACE to 1420,
                    Components.Smithing.WARHAMMER to 1335,
                    Components.Smithing.BATTLEAXE to 1363,
                    Components.Smithing.CLAWS to 3096,
                    Components.Smithing.CHAINBODY to 1101,
                    Components.Smithing.PLATELEGS to 1067,
                    Components.Smithing.PLATESKIRT to 1081,
                    Components.Smithing.PLATEBODY to 1115,
                    Components.Smithing.NAILS to 4820,
                    Components.Smithing.MEDHELM to 1137,
                    Components.Smithing.FULLHELM to 1153,
                    Components.Smithing.SQUARESHIELD to 1175,
                    Components.Smithing.KITESHIELD to 1191,
                    Components.Smithing.OTHER_2 to 4540,
                    Components.Smithing.DARTTIPS to 820,
                    Components.Smithing.ARROWHEADS to 40,
                    Components.Smithing.KNIVES to 863,
                    Components.Smithing.OTHER_1 to 7225,
                    Components.Smithing.OTHER_3 to 19572,
                    Components.Smithing.BOLTS to 9377,
                    Components.Smithing.LIMBS to 9423,
                ),
            ),
            bar(
                3,
                Objs.STEEL_BAR,
                listOf(
                    Components.Smithing.DAGGER to 1207,
                    Components.Smithing.SWORD to 1281,
                    Components.Smithing.SCIMITAR to 1325,
                    Components.Smithing.LONGSWORD to 1295,
                    Components.Smithing._2H to 1311,
                    Components.Smithing.AXE to 1353,
                    Components.Smithing.MACE to 1424,
                    Components.Smithing.WARHAMMER to 1339,
                    Components.Smithing.BATTLEAXE to 1365,
                    Components.Smithing.CLAWS to 3097,
                    Components.Smithing.CHAINBODY to 1105,
                    Components.Smithing.PLATELEGS to 1069,
                    Components.Smithing.PLATESKIRT to 1083,
                    Components.Smithing.PLATEBODY to 1119,
                    Components.Smithing.NAILS to 1539,
                    Components.Smithing.MEDHELM to 1141,
                    Components.Smithing.FULLHELM to 1157,
                    Components.Smithing.SQUARESHIELD to 1177,
                    Components.Smithing.KITESHIELD to 1193,
                    Components.Smithing.OTHER_2 to 4544,
                    Components.Smithing.DARTTIPS to 821,
                    Components.Smithing.ARROWHEADS to 41,
                    Components.Smithing.KNIVES to 865,
                    Components.Smithing.OTHER_1 to 2370,
                    Components.Smithing.OTHER_3 to 19574,
                    Components.Smithing.BOLTS to 9378,
                    Components.Smithing.LIMBS to 9425,
                ),
            ),
            bar(
                4,
                Objs.MITHRIL_BAR,
                listOf(
                    Components.Smithing.DAGGER to 1209,
                    Components.Smithing.SWORD to 1285,
                    Components.Smithing.SCIMITAR to 1329,
                    Components.Smithing.LONGSWORD to 1299,
                    Components.Smithing._2H to 1315,
                    Components.Smithing.AXE to 1355,
                    Components.Smithing.MACE to 1428,
                    Components.Smithing.WARHAMMER to 1343,
                    Components.Smithing.BATTLEAXE to 1369,
                    Components.Smithing.CLAWS to 3099,
                    Components.Smithing.CHAINBODY to 1109,
                    Components.Smithing.PLATELEGS to 1071,
                    Components.Smithing.PLATESKIRT to 1085,
                    Components.Smithing.PLATEBODY to 1121,
                    Components.Smithing.NAILS to 4822,
                    Components.Smithing.MEDHELM to 1143,
                    Components.Smithing.FULLHELM to 1159,
                    Components.Smithing.SQUARESHIELD to 1181,
                    Components.Smithing.KITESHIELD to 1197,
                    Components.Smithing.DARTTIPS to 822,
                    Components.Smithing.ARROWHEADS to 42,
                    Components.Smithing.KNIVES to 866,
                    Components.Smithing.OTHER_1 to 9416,
                    Components.Smithing.OTHER_3 to 19576,
                    Components.Smithing.BOLTS to 9379,
                    Components.Smithing.LIMBS to 9427,
                ),
            ),
            bar(
                5,
                Objs.ADAMANTITE_BAR,
                listOf(
                    Components.Smithing.DAGGER to 1211,
                    Components.Smithing.SWORD to 1287,
                    Components.Smithing.SCIMITAR to 1331,
                    Components.Smithing.LONGSWORD to 1301,
                    Components.Smithing._2H to 1317,
                    Components.Smithing.AXE to 1357,
                    Components.Smithing.MACE to 1430,
                    Components.Smithing.WARHAMMER to 1345,
                    Components.Smithing.BATTLEAXE to 1371,
                    Components.Smithing.CLAWS to 3100,
                    Components.Smithing.CHAINBODY to 1111,
                    Components.Smithing.PLATELEGS to 1073,
                    Components.Smithing.PLATESKIRT to 1091,
                    Components.Smithing.PLATEBODY to 1123,
                    Components.Smithing.NAILS to 4823,
                    Components.Smithing.MEDHELM to 1145,
                    Components.Smithing.FULLHELM to 1161,
                    Components.Smithing.SQUARESHIELD to 1183,
                    Components.Smithing.KITESHIELD to 1199,
                    Components.Smithing.DARTTIPS to 823,
                    Components.Smithing.ARROWHEADS to 43,
                    Components.Smithing.KNIVES to 867,
                    Components.Smithing.OTHER_1 to 19578,
                    Components.Smithing.BOLTS to 9380,
                    Components.Smithing.LIMBS to 9429,
                ),
            ),
            bar(
                6,
                Objs.RUNITE_BAR,
                listOf(
                    Components.Smithing.DAGGER to 1213,
                    Components.Smithing.SWORD to 1289,
                    Components.Smithing.SCIMITAR to 1333,
                    Components.Smithing.LONGSWORD to 1303,
                    Components.Smithing._2H to 1319,
                    Components.Smithing.AXE to 1359,
                    Components.Smithing.MACE to 1432,
                    Components.Smithing.WARHAMMER to 1347,
                    Components.Smithing.BATTLEAXE to 1373,
                    Components.Smithing.CLAWS to 3101,
                    Components.Smithing.CHAINBODY to 1113,
                    Components.Smithing.PLATELEGS to 1079,
                    Components.Smithing.PLATESKIRT to 1093,
                    Components.Smithing.PLATEBODY to 1127,
                    Components.Smithing.NAILS to 4824,
                    Components.Smithing.MEDHELM to 1147,
                    Components.Smithing.FULLHELM to 1163,
                    Components.Smithing.SQUARESHIELD to 1185,
                    Components.Smithing.KITESHIELD to 1201,
                    Components.Smithing.DARTTIPS to 824,
                    Components.Smithing.ARROWHEADS to 44,
                    Components.Smithing.KNIVES to 868,
                    Components.Smithing.OTHER_1 to 19580,
                    Components.Smithing.BOLTS to 9381,
                    Components.Smithing.LIMBS to 9431,
                ),
            ),
        )

    private fun bar(index: Int, bar: Int, products: List<Pair<Int, Int>>): AnvilBar = AnvilBar(index, bar, products.toMap())
}
