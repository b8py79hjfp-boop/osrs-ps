@file:Suppress("MagicNumber") // data tables: levels and xp from the OSRS Wiki "Cooking"; burn rolls from each food's Wiki success chart

package tp.content.skills

import tp.symbols.Objs

/** The `low` / `high` of a cooking success roll (Wiki "Skilling success rate"): chances out of 256 at levels 1 and 99. */
internal class Rate(val low: Int, val high: Int)

/**
 * A cookable raw food (docs/mechanics/cooking.md CK2, CK3). [fire] / [range] / [gauntlets] are its burn rolls on a fire,
 * on a range and with cooking gauntlets worn, from the food's Wiki "Skilling success chart"
 * (tools/wiki-import/cooking_rates.py). [fire] null means a range is needed; [gauntlets] null means they do nothing for it.
 */
internal class Food(
    val raw: Int,
    val cooked: Int,
    val burnt: Int,
    val level: Int,
    val xp: Double,
    val fire: Rate?,
    val range: Rate,
    val gauntlets: Rate? = null,
) {
    val rangeOnly: Boolean = fire == null
}

/** Raw obj -> food. Levels and xp: Wiki "Cooking"; burn rolls: the cooked food's Wiki page (skilling success chart). */
internal val FOODS: Map<Int, Food> =
    listOf(
        Food(Objs.RAW_BEEF, Objs.COOKED_MEAT, Objs.BURNT_MEAT, 1, 30.0, Rate(128, 512), Rate(128, 512)),
        Food(Objs.RAW_CHICKEN, Objs.COOKED_CHICKEN, Objs.BURNT_CHICKEN, 1, 30.0, Rate(128, 512), Rate(128, 512)),
        Food(Objs.RAW_SHRIMP, Objs.SHRIMP, Objs.BURNT_SHRIMP, 1, 30.0, Rate(128, 512), Rate(128, 512)),
        Food(Objs.RAW_ANCHOVIES, Objs.ANCHOVIES, Objs.BURNTFISH1, 1, 30.0, Rate(128, 512), Rate(128, 512)),
        Food(Objs.RAW_SARDINE, Objs.SARDINE, Objs.BURNTFISH5, 1, 40.0, Rate(118, 492), Rate(118, 492)),
        Food(Objs.RAW_HERRING, Objs.HERRING, Objs.BURNTFISH3, 5, 50.0, Rate(108, 472), Rate(108, 472)),
        Food(Objs.RAW_MACKEREL, Objs.MACKEREL, Objs.BURNTFISH3, 10, 60.0, Rate(98, 452), Rate(98, 452)),
        Food(Objs.RAW_TROUT, Objs.TROUT, Objs.BURNTFISH2, 15, 70.0, Rate(88, 432), Rate(88, 432)),
        Food(Objs.RAW_COD, Objs.COD, Objs.BURNTFISH2, 18, 75.0, Rate(83, 422), Rate(88, 432)),
        Food(Objs.RAW_PIKE, Objs.PIKE, Objs.BURNTFISH2, 20, 80.0, Rate(78, 412), Rate(78, 412)),
        Food(Objs.RAW_SALMON, Objs.SALMON, Objs.BURNTFISH2, 25, 90.0, Rate(68, 392), Rate(68, 392)),
        Food(Objs.RAW_TUNA, Objs.TUNA, Objs.BURNTFISH4, 30, 100.0, Rate(58, 372), Rate(58, 372)),
        Food(Objs.RAW_CAVE_EEL, Objs.CAVE_EEL, Objs.BURNT_CAVE_EEL, 38, 115.0, Rate(38, 332), Rate(38, 332)),
        Food(Objs.RAW_LOBSTER, Objs.LOBSTER, Objs.BURNT_LOBSTER, 40, 120.0, Rate(38, 332), Rate(38, 332), Rate(55, 368)),
        Food(Objs.RAW_BASS, Objs.BASS, Objs.BURNTFISH4, 43, 130.0, Rate(33, 312), Rate(33, 312)),
        Food(Objs.RAW_SWORDFISH, Objs.SWORDFISH, Objs.BURNT_SWORDFISH, 45, 140.0, Rate(18, 292), Rate(30, 310), Rate(30, 310)),
        Food(Objs.RAW_MONKFISH, Objs.MONKFISH, Objs.BURNT_MONKFISH, 62, 150.0, Rate(11, 275), Rate(13, 280), Rate(24, 290)),
        Food(Objs.RAW_SHARK, Objs.SHARK, Objs.BURNT_SHARK, 80, 210.0, Rate(1, 202), Rate(1, 232), Rate(15, 270)),
        Food(Objs.RAW_SEATURTLE, Objs.SEATURTLE, Objs.BURNT_SEATURTLE, 82, 211.3, Rate(1, 202), Rate(1, 222)),
        Food(Objs.RAW_ANGLERFISH, Objs.ANGLERFISH, Objs.BURNT_ANGLERFISH, 84, 230.0, Rate(1, 200), Rate(1, 220), Rate(12, 260)),
        Food(Objs.RAW_DARK_CRAB, Objs.DARK_CRAB, Objs.BURNT_DARK_CRAB, 90, 215.0, Rate(10, 222), Rate(10, 222)),
        Food(Objs.RAW_MANTARAY, Objs.MANTARAY, Objs.BURNT_MANTARAY, 91, 216.3, Rate(1, 202), Rate(1, 222)),
        Food(Objs.BREAD_DOUGH, Objs.BREAD, Objs.BURNT_BREAD, 1, 40.0, null, Rate(118, 492)),
        Food(Objs.UNCOOKED_PITTA_BREAD, Objs.PITTA_BREAD, Objs.BURNT_PITTA_BREAD, 58, 40.0, null, Rate(118, 492)),
        Food(Objs.UNCOOKED_REDBERRY_PIE, Objs.REDBERRY_PIE, Objs.BURNT_PIE, 10, 78.0, null, Rate(98, 452)),
        Food(Objs.UNCOOKED_MEAT_PIE, Objs.MEAT_PIE, Objs.BURNT_PIE, 20, 110.0, null, Rate(78, 412)),
        Food(Objs.UNCOOKED_APPLE_PIE, Objs.APPLE_PIE, Objs.BURNT_PIE, 30, 130.0, null, Rate(58, 372)),
        Food(Objs.UNCOOKED_PIZZA, Objs.PLAIN_PIZZA, Objs.BURNT_PIZZA, 35, 143.0, null, Rate(48, 352)),
        Food(Objs.UNCOOKED_CAKE, Objs.CAKE, Objs.BURNT_CAKE, 40, 180.0, null, Rate(38, 332)),
    ).associateBy { it.raw }

/**
 * Range-like locs (cache loc type with the op "Cook"; names Range, Cooking range, Stove, Oven, Clay oven), generated
 * from cache 2757 with `cache-dump config loc`. Used for item-on-loc; the "Cook" op itself is resolved by name.
 */
internal val RANGE_LOCS: IntArray =
    intArrayOf(
        114,
        7183,
        7184,
        7421,
        8750,
        9682,
        9736,
        12102,
        12269,
        12611,
        16641,
        16893,
        21302,
        21792,
        22713,
        22714,
        25730,
        26181,
        27516,
        27517,
        27724,
        31631,
        32739,
        35980,
        36077,
        37728,
        39391,
        39458,
        39490,
        40149,
        47925,
        48169,
        50563,
        51540,
        52647,
        54049,
        54449,
        57332,
        57333,
        59682,
    )

/**
 * Fire locs: cache locs named "Fire" that show the ordinary flame (idle anim 475) and have no op or "Cook", from cache
 * 2757. Includes the player-made fires (26185, 26186, 26575, 26576, 20000, 20001).
 */
internal val FIRE_LOCS: IntArray =
    intArrayOf(
        3769,
        3775,
        4265,
        4266,
        5249,
        5499,
        9735,
        10433,
        10660,
        15156,
        20000,
        20001,
        21620,
        26185,
        26186,
        26575,
        26576,
        28791,
        43475,
        44021,
        49342,
        51728,
        51729,
        57311,
    )

/** Names of the locs whose "Cook" op starts cooking (everything in [RANGE_LOCS] plus the cooking fire 43475). */
internal val COOK_LOC_NAMES: List<String> = listOf("Range", "Cooking range", "Stove", "Oven", "Clay oven", "Fire")
