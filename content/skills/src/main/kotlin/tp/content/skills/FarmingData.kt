@file:Suppress("MagicNumber") // data tables: patch ids, tiles and rates from the sources named in docs/mechanics/farming.md

package tp.content.skills

import tp.symbols.Objs
import tp.symbols.Varbits

/** The kinds of farming patch this plugin covers; [tsv] is the name used in `farming-varbits.tsv` and `farming-crops.tsv`. */
internal enum class PatchType(val tsv: String) {
    ALLOTMENT("ALLOTMENT"),
    HERB("HERB"),
    FLOWER("FLOWER"),
    TREE("TREE"),
    FRUIT_TREE("FRUIT_TREE"),
    ;

    /** Whether the crop gives its produce through harvest lives (docs/mechanics/farming.md F6). */
    val usesLives: Boolean get() = this == ALLOTMENT || this == HERB

    /** Whether the crop is a tree that must be health-checked once grown (F7). */
    val isTree: Boolean get() = this == TREE || this == FRUIT_TREE

    /** Whether the patch can be watered (Wiki "Crop disease": allotment, flower and hops patches only). */
    val canWater: Boolean get() = this == ALLOTMENT || this == FLOWER

    /** The patch's name in game messages. */
    val label: String
        get() =
            when (this) {
                ALLOTMENT -> "allotment"
                HERB -> "herb patch"
                FLOWER -> "flower patch"
                TREE -> "tree patch"
                FRUIT_TREE -> "fruit tree patch"
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
    val ctsLow: Int,
    val ctsHigh: Int,
    val name: String,
) {
    /** Wiki "Crop disease": fruit trees 18/128, maple 13, magic 9, herbs 27; the rest is OPEN-FM2 (see [FarmingConst]). */
    val diseaseBase: Int =
        when {
            type == PatchType.FRUIT_TREE -> FarmingConst.FRUIT_DISEASE
            key == "MAPLE" -> FarmingConst.MAPLE_DISEASE
            key == "MAGIC" -> FarmingConst.MAGIC_DISEASE
            type == PatchType.HERB -> FarmingConst.HERB_DISEASE
            type == PatchType.TREE -> FarmingConst.OPEN_TREE_DISEASE
            else -> FarmingConst.OPEN_CROP_DISEASE
        }

    /** Wiki "Crop disease": herbs 3 of 4 cycles, maple 7 of 8, magic 11 of 12 (all but the first), fruit trees 4 of 6. */
    val diseaseCycles: Int = if (type == PatchType.FRUIT_TREE) cycles - 2 else cycles - 1
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

    /** OPEN-FM2: the Wiki gives no rate for oak, willow and yew; maple's is used. */
    const val OPEN_TREE_DISEASE = 13

    /** OPEN-FM2: the Wiki gives no rate for allotments and flowers; the herbs' is used. */
    const val OPEN_CROP_DISEASE = 27

    /** The disease roll is out of 128 (Wiki "Crop disease": "rounded down to the nearest 1/128th"). */
    const val DISEASE_DENOMINATOR = 128

    /** Compost (1), supercompost (2) and ultracompost (3) cut the chance by 50%, 80% and 90% (Wiki "Crop disease"): kept = 1/DIVISOR. */
    val COMPOST_DIVISOR = intArrayOf(1, 2, 5, 10)

    /** OPEN-FM3: the Wiki says watering "reduces" the chance without a number; half is used. */
    const val WATERED_DIVISOR = 2

    /** Wiki "Farming#Variable crop yield": a fully grown patch has 3 lives, plus 1 / 2 / 3 for compost / super / ultra. */
    const val BASE_LIVES = 3

    /** Wiki "Farming#Growth timing": weeds grow back every 5 minutes (the shortest growth tick). */
    const val WEEDS_MIN = 5

    /** Weed levels: 3 is full weeds, 0 is raked clear (RuneLite `PatchImplementation` WEEDS stages 3 .. 0). */
    const val FULL_WEEDS = 3

    /** Wiki "Fruit tree": a fully grown fruit tree bears six fruit. */
    const val MAX_FRUIT = 6

    /** Wiki "Fruit tree": a new fruit every 40 minutes, on the tree growth tick. */
    const val FRUIT_REGROW_MIN = 40

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
}

/** One patch the player can farm: the multi-loc [loc] a click on it reports, drawn through the shared varbit [slot]. */
internal class Patch(val loc: Int, val type: PatchType, val slot: Int, val name: String) {
    /** Saved keys of the patch's packed state and of the minute its growth was last brought up to. */
    val stateKey: String = "farming.$loc"
    val timeKey: String = "farming.$loc.t"
}

/** A farm: the [patches] whose states the client needs while the player is within [FarmingData.SITE_RADIUS] of ([x], [z]). */
internal class Site(val name: String, val x: Int, val z: Int, val patches: List<Patch>)

/**
 * The patches covered (docs/mechanics/farming.md F8). Loc ids, shared varbits and tiles are from cache 2757 (the multi-loc
 * parents with `multiVarbit` 4771 .. 4774, `find-locs`); which patch uses which slot follows RuneLite's `FarmingWorld` (BSD-2).
 */
internal object FarmingData {
    /** Half the client's 104-tile scene: patches farther than this from the player are not loaded by the client. */
    const val SITE_RADIUS = 52

    private fun farm(name: String, x: Int, z: Int, a: Int, b: Int, flower: Int, herb: Int): Site =
        Site(
            name,
            x,
            z,
            listOf(
                Patch(a, PatchType.ALLOTMENT, Varbits.FARMING_TRANSMIT_A, "$name north allotment"),
                Patch(b, PatchType.ALLOTMENT, Varbits.FARMING_TRANSMIT_B, "$name south allotment"),
                Patch(flower, PatchType.FLOWER, Varbits.FARMING_TRANSMIT_C, "$name flower patch"),
                Patch(herb, PatchType.HERB, Varbits.FARMING_TRANSMIT_D, "$name herb patch"),
            ),
        )

    private fun tree(name: String, x: Int, z: Int, loc: Int): Site =
        Site(name, x, z, listOf(Patch(loc, PatchType.TREE, Varbits.FARMING_TRANSMIT_A, "$name tree patch")))

    private fun fruit(name: String, x: Int, z: Int, loc: Int): Site =
        Site(name, x, z, listOf(Patch(loc, PatchType.FRUIT_TREE, Varbits.FARMING_TRANSMIT_A, "$name fruit tree patch")))

    val SITES: List<Site> =
        listOf(
            farm("Falador", 3055, 3308, 8550, 8551, 7847, 8150),
            farm("Catherby", 2810, 3463, 8552, 8553, 7848, 8151),
            farm("Ardougne", 2666, 3374, 8554, 8555, 7849, 8152),
            farm("Port Phasmatys", 3602, 3525, 8556, 8557, 7850, 8153),
            tree("Taverley", 2935, 3437, 8388),
            tree("Falador", 3003, 3372, 8389),
            tree("Varrock", 3228, 3458, 8390),
            tree("Lumbridge", 3192, 3230, 8391),
            Site(
                "Gnome Stronghold",
                2455,
                3430,
                listOf(
                    Patch(19147, PatchType.TREE, Varbits.FARMING_TRANSMIT_A, "Gnome Stronghold tree patch"),
                    Patch(7962, PatchType.FRUIT_TREE, Varbits.FARMING_TRANSMIT_B, "Gnome Stronghold fruit tree patch"),
                ),
            ),
            fruit("Tree Gnome Village", 2489, 3179, 7963),
            fruit("Brimhaven", 2764, 3212, 7964),
            fruit("Catherby", 2860, 3433, 7965),
        )

    /** Tool leprechaun npc ids of the cache (`config npc`: "Tool Leprechaun" 0 and 12109; Wiki "Tool Leprechaun" lists both). */
    val LEPRECHAUNS: IntArray = intArrayOf(0, 12109)

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

/** The crops of `farming-crops.tsv`. */
internal class CropTable private constructor(val crops: List<Crop>) {
    private val byIndex: Array<Crop?> = arrayOfNulls<Crop>(crops.size + 1).also { a -> crops.forEach { a[it.index] = it } }
    val bySeed: Map<Int, Crop> = crops.associateBy { it.seed }
    private val byKey: Map<String, Crop> = crops.associateBy { it.key }

    /** The crop stored as [index] in a saved state (1-based), or null. */
    fun at(index: Int): Crop? = byIndex.getOrNull(index)

    fun key(key: String): Crop? = byKey[key]

    companion object {
        /** The columns of `farming-crops.tsv`; the file's header names them. */
        private const val COLUMNS = 16

        /** Reads `farming-crops.tsv`. */
        fun load(): CropTable {
            val text = CropTable::class.java.getResource("farming-crops.tsv")?.readText() ?: error("farming-crops.tsv missing")
            val rows =
                text
                    .lineSequence()
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .map { it.split('\t') }
                    .toList()
            return CropTable(rows.mapIndexed { i, cols -> parse(i + 1, cols) })
        }

        private fun parse(index: Int, cols: List<String>): Crop {
            require(cols.size == COLUMNS) { "farming-crops.tsv row has ${cols.size} columns: $cols" }
            val type = PatchType.entries.firstOrNull { it.tsv == cols[1] } ?: error("farming-crops.tsv: unknown patch ${cols[1]}")
            return Crop(
                index = index,
                key = cols[0],
                type = type,
                level = cols[2].toInt(),
                seedsPer = cols[3].toInt(),
                seed = cols[4].toInt(),
                produce = cols[5].toInt(),
                clean = cols[6].toInt(),
                plantXp = cols[7].toDoubleOrNull() ?: 0.0,
                harvestXp = cols[8].toDoubleOrNull() ?: 0.0,
                checkXp = cols[9].toDoubleOrNull() ?: 0.0,
                cycleMin = cols[10].toInt(),
                firstMin = cols[11].toInt(),
                cycles = cols[12].toInt(),
                ctsLow = cols[13].toIntOrNull() ?: 0,
                ctsHigh = cols[14].toIntOrNull() ?: 0,
                name = cols[15],
            )
        }
    }
}

/**
 * The patch varbit layouts of `farming-varbits.tsv` (RuneLite `PatchImplementation`, BSD-2; tools/wiki-import/farming_varbits.py),
 * turned around: the varbit values that draw a (patch type, produce, state, stage). A state can have several values: the
 * lowest is the plain one, the next the watered one (allotment, flower), the last the stump of a tree.
 */
internal class VarbitTable private constructor(private val values: Map<String, IntArray>) {
    /** The values for the produce [produce] of a patch of [type] in [state] ([CropState]) at [stage], or empty. */
    fun values(type: PatchType, produce: String, state: CropState, stage: Int): IntArray =
        values[key(type, produce, state, stage)] ?: EMPTY

    /** The plain value, or [fallback]. */
    fun plain(type: PatchType, produce: String, state: CropState, stage: Int, fallback: Int): Int =
        values(type, produce, state, stage).firstOrNull() ?: fallback

    /** The watered value if there is one, else the plain value, else [fallback]. */
    fun watered(type: PatchType, produce: String, stage: Int, fallback: Int): Int {
        val v = values(type, produce, CropState.GROWING, stage)
        return if (v.size > 1) v[1] else v.firstOrNull() ?: fallback
    }

    /** The last value (the stump of a tree), or [fallback]. */
    fun last(type: PatchType, produce: String, state: CropState, stage: Int, fallback: Int): Int =
        values(type, produce, state, stage).lastOrNull() ?: fallback

    /** The crop states of the layouts (RuneLite `CropState`). */
    enum class CropState { HARVESTABLE, GROWING, DISEASED, DEAD }

    companion object {
        private val EMPTY = IntArray(0)

        private const val COLUMNS = 7

        private fun key(type: PatchType, produce: String, state: CropState, stage: Int): String =
            "${type.ordinal}|$produce|${state.ordinal}|$stage"

        /** Reads `farming-varbits.tsv`. */
        fun load(): VarbitTable {
            val resource = VarbitTable::class.java.getResource("farming-varbits.tsv")
            val text = resource?.readText() ?: error("farming-varbits.tsv missing")
            val lists = HashMap<String, MutableList<Int>>()
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith('#') || line.startsWith("patch\t")) continue
                val cols = line.split('\t')
                require(cols.size == COLUMNS) { "farming-varbits.tsv row has ${cols.size} columns: $cols" }
                val type = PatchType.entries.firstOrNull { it.tsv == cols[0] } ?: continue
                val state = CropState.entries.firstOrNull { it.name == cols[4] } ?: continue
                val first = cols[1].toInt()
                val last = cols[2].toInt()
                val stageFirst = cols[5].toInt()
                val stageLast = cols[6].toInt()
                val step = Integer.signum(stageLast - stageFirst)
                for (v in first..last) {
                    val stage = stageFirst + (v - first) * step
                    lists.getOrPut(key(type, cols[3], state, stage)) { ArrayList() } += v
                }
            }
            return VarbitTable(lists.mapValues { it.value.toIntArray() })
        }
    }
}
