@file:Suppress("MagicNumber") // data tables: patch ids, tiles and rates from the sources named in docs/mechanics/farming.md

package tp.content.skills

import tp.symbols.Objs

/** What a fully grown crop yields (docs/mechanics/farming.md F6, F7). */
internal enum class Harvest {
    /** Picked until the harvest lives run out (allotment, herb, hops, seaweed, bush, cactus, celastrus). */
    LIVES,

    /** One harvest of the whole patch (flower, belladonna, crystal tree). */
    SINGLE,

    /** Fruit picked one at a time, [FarmingConst.MAX_FRUIT] of them (fruit tree, calquat, mushroom). */
    FRUIT,

    /** Chopped down with an axe (tree, hardwood, redwood). */
    CHOP,

    /** Nothing to take: the plant itself is the product (spirit tree, anima plants, Hespori). */
    NONE,
}

/** Which tool treats a diseased crop (docs/mechanics/farming.md F4; Wiki "Disease (Farming)"). */
internal enum class Cure { PLANT_CURE, SECATEURS, EITHER }

/**
 * The kinds of farming patch this plugin covers; [tsv] is the name used in `farming-varbits.tsv` and `farming-crops.tsv` (RuneLite's
 * `PatchImplementation`). [harvest] is what the grown crop gives, [check] whether a grown crop first waits for "Check-health",
 * [canWater] whether watering helps (Wiki "Crop disease": allotment, flower and hops patches only), [compostLives] whether compost
 * adds harvest lives (Wiki "Farming#Compost": allotment, hops, herb, seaweed, celastrus), [baseLives] the lives of a fresh crop.
 */
internal enum class PatchType(
    val tsv: String,
    val label: String,
    val harvest: Harvest,
    val check: Boolean = false,
    val canWater: Boolean = false,
    val compostLives: Boolean = false,
    val baseLives: Int = FarmingConst.BASE_LIVES,
    val cure: Cure = Cure.PLANT_CURE,
) {
    ALLOTMENT("ALLOTMENT", "allotment", Harvest.LIVES, canWater = true, compostLives = true),
    HERB("HERB", "herb patch", Harvest.LIVES, compostLives = true),
    FLOWER("FLOWER", "flower patch", Harvest.SINGLE, canWater = true),
    TREE("TREE", "tree patch", Harvest.CHOP, check = true, cure = Cure.SECATEURS),
    FRUIT_TREE("FRUIT_TREE", "fruit tree patch", Harvest.FRUIT, check = true, cure = Cure.SECATEURS),
    HOPS("HOPS", "hops patch", Harvest.LIVES, canWater = true, compostLives = true),
    BUSH("BUSH", "bush patch", Harvest.LIVES, check = true, baseLives = FarmingConst.BUSH_LIVES, cure = Cure.SECATEURS),
    CACTUS("CACTUS", "cactus patch", Harvest.LIVES, check = true),
    MUSHROOM("MUSHROOM", "mushroom patch", Harvest.FRUIT),
    BELLADONNA("BELLADONNA", "belladonna patch", Harvest.SINGLE),
    CALQUAT("CALQUAT", "calquat patch", Harvest.FRUIT, check = true, cure = Cure.SECATEURS),
    SPIRIT_TREE("SPIRIT_TREE", "spirit tree patch", Harvest.NONE, check = true, cure = Cure.SECATEURS),
    SEAWEED("SEAWEED", "seaweed patch", Harvest.LIVES, compostLives = true),
    CELASTRUS("CELASTRUS", "celastrus patch", Harvest.LIVES, check = true, compostLives = true, cure = Cure.EITHER),
    REDWOOD("REDWOOD", "redwood patch", Harvest.CHOP, check = true, cure = Cure.SECATEURS),
    HESPORI("HESPORI", "Hespori patch", Harvest.NONE),
    CRYSTAL_TREE("CRYSTAL_TREE", "crystal tree patch", Harvest.SINGLE, check = true),
    HARDWOOD_TREE("HARDWOOD_TREE", "hardwood tree patch", Harvest.CHOP, check = true, cure = Cure.EITHER),
    ANIMA("ANIMA", "anima patch", Harvest.NONE),
    ;

    /** Whether the crop gives its produce through harvest lives (docs/mechanics/farming.md F6). */
    val usesLives: Boolean get() = harvest == Harvest.LIVES

    /** Whether the crop is a tree (or tree-like plant) pruned with secateurs, not cured (F4). */
    val isTree: Boolean get() = cure == Cure.SECATEURS

    /** Whether a stump is left when the crop is chopped down (F7). */
    val chopped: Boolean get() = harvest == Harvest.CHOP

    /** Whether the patch holds saplings (planted with a spade) and not seeds (a seed dibber), Wiki "Sapling" / "Seed dibber". */
    val takesSaplings: Boolean
        get() = this in SAPLING_TYPES

    private companion object {
        val SAPLING_TYPES: Set<PatchType> =
            setOf(TREE, FRUIT_TREE, HARDWOOD_TREE, CALQUAT, SPIRIT_TREE, CELASTRUS, REDWOOD, CRYSTAL_TREE)
    }
}

/** What a patch holds (docs/mechanics/farming.md F1). The ordinal is stored in the saved state, so only append. */
internal enum class Phase {
    /** Weeds: [PatchState.stage] 3 (full) .. 0 (raked clear). */
    WEEDS,

    /** A crop growing: [PatchState.stage] is the growth stage, 0 until [Crop.cycles] - 1. */
    GROWING,

    /** Diseased at [PatchState.stage]; it dies at the end of the (slower) cycle unless cured. */
    DISEASED,

    /** Dead at [PatchState.stage]; cleared with a spade. */
    DEAD,

    /** Fully grown: harvest lives in [PatchState.lives] (allotment, herb, flower), choppable (tree), or fruit in [PatchState.stage]. */
    HARVESTABLE,

    /** A fully grown tree or fruit tree whose health has not been checked. */
    CHECK,

    /** The stump of a chopped tree; cleared with a spade. */
    STUMP,
}

/**
 * One crop of `farming-crops.tsv` (tools/wiki-import/farming_crops.py, OSRS Wiki `{{Farming info}}`): planted with [seed]
 * ([seedsPer] of them, a sapling for trees) at [level]; it advances every [cycleMin] minutes ([firstMin] for the first stage)
 * through [cycles] growth ticks; [ctsLow] / [ctsHigh] are its chance (of 256) to save a harvest life at levels 1 / 99.
 * [diseaseBase] / 128 is its chance to fall ill at the end of a growth cycle, which can happen in stages 1..[diseaseCycles] (F4).
 */
@Suppress("LongParameterList") // one row of farming-crops.tsv
internal class Crop(
    val index: Int,
    val key: String,
    val type: PatchType,
    val level: Int,
    val seedsPer: Int,
    val seed: Int,
    val produce: Int,
    val clean: Int,
    val plantXp: Double,
    val harvestXp: Double,
    val checkXp: Double,
    val cycleMin: Int,
    val firstMin: Int,
    val cycles: Int,
    ctsLowRaw: Int,
    ctsHighRaw: Int,
    val name: String,
) {
    /** Chance to save a harvest life at level 1 (of 256); crops with no published value use the OPEN-FA3 stand-in. */
    val ctsLow: Int = if (ctsLowRaw > 0 || !type.usesLives) ctsLowRaw else FarmingConst.OPEN_CTS_LOW

    /** Chance to save a harvest life at level 99 (of 256). */
    val ctsHigh: Int = if (ctsHighRaw > 0 || !type.usesLives) ctsHighRaw else FarmingConst.OPEN_CTS_HIGH

    /**
     * Wiki "Crop disease": fruit trees 18/128, maple 13, magic 9, herbs 27; poison ivy, Hespori, anima plants and the crystal tree
     * are immune (0); the rest is OPEN-FM2 / OPEN-FA1 (see [FarmingConst]).
     */
    val diseaseBase: Int =
        when {
            key == "POISON_IVY" || type == PatchType.HESPORI || type == PatchType.ANIMA || type == PatchType.CRYSTAL_TREE -> 0
            type == PatchType.FRUIT_TREE -> FarmingConst.FRUIT_DISEASE
            key == "MAPLE" -> FarmingConst.MAPLE_DISEASE
            key == "MAGIC" -> FarmingConst.MAGIC_DISEASE
            type == PatchType.HERB -> FarmingConst.HERB_DISEASE
            type.isTree || type == PatchType.HARDWOOD_TREE || type == PatchType.CELASTRUS -> FarmingConst.OPEN_TREE_DISEASE
            else -> FarmingConst.OPEN_CROP_DISEASE
        }

    /** Wiki "Crop disease": herbs 3 of 4 cycles, maple 7 of 8, magic 11 of 12 (all but the first), fruit trees 4 of 6. */
    val diseaseCycles: Int = if (type == PatchType.FRUIT_TREE) cycles - 2 else cycles - 1

    /** Minutes between the regrowth ticks of a picked-bare bush or cactus, 0 if the crop does not regrow (F6). */
    val regrowMin: Int =
        when (key) {
            "CACTUS" -> FarmingConst.CACTUS_REGROW_MIN
            "POTATO_CACTUS" -> FarmingConst.POTATO_CACTUS_REGROW_MIN
            else -> if (type == PatchType.BUSH) FarmingConst.BUSH_REGROW_MIN else 0
        }

    /** Regrowth ticks until a picked-bare bush or cactus bears again. */
    val regrowTicks: Int =
        when (key) {
            "CACTUS" -> FarmingConst.CACTUS_REGROW_TICKS
            "POTATO_CACTUS" -> FarmingConst.POTATO_CACTUS_REGROW_TICKS
            else -> if (type == PatchType.BUSH) FarmingConst.BUSH_REGROW_TICKS else 0
        }

    /** Whether the patch keeps the plant after its harvest lives run out and bears again (bush, cacti). */
    val regrows: Boolean get() = regrowMin > 0

    /** Whether the crop is planted as a sapling. */
    val isSapling: Boolean get() = type.takesSaplings
}

/** Numbers of the farming rules, each with its source (docs/mechanics/farming.md). */
internal object FarmingConst {
    /** Wiki "Crop disease" table: disease chance per cycle in 128ths, fruit trees. */
    const val FRUIT_DISEASE = 18

    /** Wiki "Crop disease" table: maple trees. */
    const val MAPLE_DISEASE = 13

    /** Wiki "Crop disease" table: magic trees. */
    const val MAGIC_DISEASE = 9

    /** Wiki "Crop disease" table: all herbs. */
    const val HERB_DISEASE = 27

    /** OPEN-FM2 / OPEN-FA1: no Wiki rate for oak, willow, yew, hardwoods, calquat, redwood, celastrus and spirit trees; maple's is used. */
    const val OPEN_TREE_DISEASE = 13

    /** OPEN-FM2 / OPEN-FA1: the Wiki gives no rate for allotments, flowers, hops, bushes, cacti ...; the herbs' is used. */
    const val OPEN_CROP_DISEASE = 27

    /** OPEN-FA3: no published chance to save a life for bushes, cacti, celastrus ...; level 1 stand-in (herbs, allotments: 82-140). */
    const val OPEN_CTS_LOW = 100

    /** OPEN-FA3: level 99 stand-in; the Wiki says CTS-high is 180 for nearly every crop with a published value. */
    const val OPEN_CTS_HIGH = 180

    /** The disease roll is out of 128 (Wiki "Crop disease": "rounded down to the nearest 1/128th"). */
    const val DISEASE_DENOMINATOR = 128

    /** Compost (1), supercompost (2) and ultracompost (3) cut the chance by 50%, 80% and 90% (Wiki "Crop disease"): kept = 1/DIVISOR. */
    val COMPOST_DIVISOR = intArrayOf(1, 2, 5, 10)

    /** Wiki "Disease (Farming)": a live Iasor plant cuts every patch's disease chance to 20% (a divisor of 5), never below 1/128. */
    const val IASOR_DIVISOR = 5

    /** OPEN-FM3: the Wiki says watering "reduces" the chance without a number; half is used. */
    const val WATERED_DIVISOR = 2

    /** Wiki "Farming#Variable crop yield": a fully grown patch has 3 lives, plus 1 / 2 / 3 for compost / super / ultra. */
    const val BASE_LIVES = 3

    /** Wiki "Redberry bush" (and the other bushes, "Bush Yield Improvements" 23 Aug 2018): a bush gives at least four berries. */
    const val BUSH_LIVES = 4

    /** Wiki "Farming#Variable crop yield" (Attas): the Attas plant raises the chance to save a life by 5%. */
    const val ATTAS_PERCENT = 105

    /** Wiki "Farming#Growth timing": weeds grow back every 5 minutes (the shortest growth tick). */
    const val WEEDS_MIN = 5

    /** Weed levels: 3 is full weeds, 0 is raked clear (RuneLite `PatchImplementation` WEEDS stages 3 .. 0). */
    const val FULL_WEEDS = 3

    /** Wiki "Fruit tree" / "Calquat tree" / "Bittercap Mushrooms": a fully grown crop of this kind bears six items. */
    const val MAX_FRUIT = 6

    /** Wiki "Fruit tree": a new fruit every 40 minutes, on the tree growth tick. */
    const val FRUIT_REGROW_MIN = 40

    /** RuneLite `Produce.REDBERRIES` ... (BSD-2): bushes regrow on a 20 minute tick; Wiki "Redberry bush": about 80 minutes. */
    const val BUSH_REGROW_MIN = 20
    const val BUSH_REGROW_TICKS = 4

    /** RuneLite `Produce.CACTUS` (BSD-2): regrowth tick 20 minutes over 3 stages. */
    const val CACTUS_REGROW_MIN = 20
    const val CACTUS_REGROW_TICKS = 3

    /** RuneLite `Produce.POTATO_CACTUS` (BSD-2): regrowth tick 5 minutes over 6 stages. */
    const val POTATO_CACTUS_REGROW_MIN = 5
    const val POTATO_CACTUS_REGROW_TICKS = 6

    /** Wiki "Crop disease": a diseased crop uses the next slowest growth tick, which doubles each tier (Wiki "Growth timing"). */
    const val DISEASED_CYCLE_FACTOR = 2

    /** Wiki "Weeds": raking gives 4 Farming xp per weed. */
    const val RAKE_XP = 4.0

    /** Wiki "Compost", "Supercompost", "Ultracompost": Farming xp for treating a patch. */
    val COMPOST_XP = doubleArrayOf(0.0, 18.0, 26.0, 36.0)

    /** Wiki "Crop disease": secateurs prune a diseased tree with a 75% chance, so a 1-in-4 failure. */
    const val PRUNE_FAIL_IN = 4

    /** Wiki "Tree seed" (Oak tree page): farmed trees are depleted by a log with chance 1/8. */
    const val TREE_DEPLETE_IN = 8

    /** Wiki "Magic secateurs": +10% on the chance-to-save constants (Farming#Variable crop yield, `ItemBoost`). */
    const val SECATEURS_PERCENT = 110

    /** Wiki "Crystal tree": a harvest gives 28-32 crystal shards. */
    const val CRYSTAL_SHARDS_MIN = 28
    const val CRYSTAL_SHARDS_SPAN = 5

    /** Wiki "Belladonna": at least six nightshade, up to 18 at level 99 (the bonus step is OPEN-FA4: 12 over levels 1..99). */
    const val BELLADONNA_MIN = 6
    const val BELLADONNA_BONUS_MAX = 12
    const val BELLADONNA_KEY = "BELLADONNA"

    /** Cache seq 2273 (raking): 56 client ticks of 20 ms = 1.9 game ticks (OPEN-FM8). */
    const val RAKE_TICKS = 2

    /** Cache seq 2291 (seed dibbing): 92 client ticks = 3.1 game ticks (OPEN-FM8). */
    const val PLANT_TICKS = 3

    /** Cache seq 2283 (pouring compost): 160 client ticks = 5.3 game ticks (OPEN-FM8). */
    const val COMPOST_TICKS = 5

    /** Cache seq 2293 (watering can): 139 client ticks = 4.6 game ticks (OPEN-FM8). */
    const val WATER_TICKS = 5

    /** Cache seq 2288 (plant cure): 190 client ticks = 6.3 game ticks (OPEN-FM8). */
    const val CURE_TICKS = 6

    /** Cache seq 2282 (picking): 55 client ticks = 1.8 game ticks; Wiki "Allotment" says a patch is picked every 2 ticks. */
    const val PICK_TICKS = 2

    /** Cache seq 830 (digging): 41 client ticks = 1.4 game ticks (OPEN-FM8). */
    const val DIG_TICKS = 2

    /** A tree is cut with one roll per 4 ticks, like any tree (woodcutting.md WC1). */
    const val CHOP_TICKS = 4

    /** Wiki "Limpwurt plant": three roots, plus a random bonus up to one root per 10 levels from level 11 (OPEN-FM9: uniform). */
    const val LIMPWURT_MIN = 3
    const val LIMPWURT_STEP = 10
    const val LIMPWURT_KEY = "LIMPWURT"

    /** Ticks between redraws of the visible patches while standing at a farm (one minute: growth ticks are 5 minutes or more). */
    const val REFRESH_TICKS = 100
}

/** The farming compost buckets by tier (1 compost, 2 supercompost, 3 ultracompost). */
internal object FarmingItems {
    val COMPOST: Map<Int, Int> = mapOf(Objs.BUCKET_COMPOST to 1, Objs.BUCKET_SUPERCOMPOST to 2, Objs.BUCKET_ULTRACOMPOST to 3)

    /** The bucket of compost of [tier] (1..3). */
    val BUCKET_OF: IntArray = intArrayOf(0, Objs.BUCKET_COMPOST, Objs.BUCKET_SUPERCOMPOST, Objs.BUCKET_ULTRACOMPOST)

    /** Watering cans holding water, by remaining doses (Wiki "Watering can": 8 doses at most). */
    val CANS: Map<Int, Int> =
        mapOf(
            Objs.WATERING_CAN_1 to 1,
            Objs.WATERING_CAN_2 to 2,
            Objs.WATERING_CAN_3 to 3,
            Objs.WATERING_CAN_4 to 4,
            Objs.WATERING_CAN_5 to 5,
            Objs.WATERING_CAN_6 to 6,
            Objs.WATERING_CAN_7 to 7,
            Objs.WATERING_CAN_8 to 8,
        )

    /** The can with [doses] doses left. */
    val CAN_WITH: IntArray =
        intArrayOf(
            Objs.WATERING_CAN_0,
            Objs.WATERING_CAN_1,
            Objs.WATERING_CAN_2,
            Objs.WATERING_CAN_3,
            Objs.WATERING_CAN_4,
            Objs.WATERING_CAN_5,
            Objs.WATERING_CAN_6,
            Objs.WATERING_CAN_7,
        )

    val SECATEURS: Set<Int> = setOf(Objs.SECATEURS, Objs.FAIRY_ENCHANTED_SECATEURS)

    /** Compost potion doses to the potion item (Wiki "Compost bin#Upgrading": one dose upgrades a bin of compost to supercompost). */
    val COMPOST_POTIONS: Map<Int, Int> =
        mapOf(
            Objs.SUPERCOMPOST_POTION_1 to 1,
            Objs.SUPERCOMPOST_POTION_2 to 2,
            Objs.SUPERCOMPOST_POTION_3 to 3,
            Objs.SUPERCOMPOST_POTION_4 to 4,
        )
}

/**
 * One patch the player can farm: the multi-loc parents [locs] a click on it reports (several for a patch drawn from many locs),
 * drawn through the shared varbit [slot]. [farmers] are the NPCs that take payment to protect it ([payIndex]: the farmer's op 3 /
 * op 4 "Pay" for the first / second patch), [neverDiseased] marks the patches the Wiki says never fall ill, [guild] the Farming
 * Guild patches (farming contracts).
 */
@Suppress("LongParameterList") // one patch row of FarmingSites
internal class Patch(
    val locs: IntArray,
    val type: PatchType,
    val slot: Int,
    val name: String,
    val farmers: IntArray = EMPTY,
    val payIndex: Int = 0,
    val neverDiseased: Boolean = false,
    val guild: Boolean = false,
) {
    /** The patch's identity in saved keys. */
    val loc: Int get() = locs[0]

    /** Saved keys of the patch's packed state and of the minute its growth was last brought up to. */
    val stateKey: String = "farming.${locs[0]}"
    val timeKey: String = "farming.${locs[0]}.t"

    private companion object {
        val EMPTY = IntArray(0)
    }
}

/** A compost bin: the multi-loc parent [loc] drawn through [slot]; [big] is the Farming Guild's giant bin (30 items). */
internal class Bin(val loc: Int, val slot: Int, val name: String, val big: Boolean = false) {
    val stateKey: String = "farming.bin.$loc"
    val timeKey: String = "farming.bin.$loc.t"
}

/** A farm: the [patches] and [bins] whose states the client needs while the player is within [FarmingSites.SITE_RADIUS] of ([x], [z]). */
internal class Site(val name: String, val x: Int, val z: Int, val patches: List<Patch>, val bins: List<Bin> = emptyList())
