@file:Suppress("MagicNumber") // column counts of the generated tables

package tp.content.skills

/** Reads a resource table of this package; `#` lines and the header line are skipped. */
private fun rows(name: String, columns: Int, headerStart: String): List<List<String>> {
    val text = CropTable::class.java.getResource(name)?.readText() ?: error("$name missing")
    return text
        .lineSequence()
        .filter { it.isNotBlank() && !it.startsWith('#') && !it.startsWith(headerStart) }
        .map { it.split('\t') }
        .onEach { require(it.size >= columns) { "$name row has ${it.size} columns: $it" } }
        .toList()
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
            val rows = rows("farming-crops.tsv", COLUMNS, "key\t")
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
                ctsLowRaw = cols[13].toIntOrNull() ?: 0,
                ctsHighRaw = cols[14].toIntOrNull() ?: 0,
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

    /** The last value (the stump of a tree, the "check health" look of a spirit tree), or [fallback]. */
    fun last(type: PatchType, produce: String, state: CropState, stage: Int, fallback: Int): Int =
        values(type, produce, state, stage).lastOrNull() ?: fallback

    /** The highest stage the layout has for [produce] in [state] (the full bush, the full cactus), or -1. */
    fun maxStage(type: PatchType, produce: String, state: CropState): Int {
        var stage = MAX_PROBE
        while (stage >= 0 && values(type, produce, state, stage).isEmpty()) stage--
        return stage
    }

    /** The crop states of the layouts (RuneLite `CropState`). */
    enum class CropState { HARVESTABLE, GROWING, DISEASED, DEAD }

    companion object {
        private val EMPTY = IntArray(0)

        private const val COLUMNS = 7

        /** The most growth stages any layout has (redwood 11, spirit tree 13). */
        private const val MAX_PROBE = 16

        private fun key(type: PatchType, produce: String, state: CropState, stage: Int): String =
            "${type.ordinal}|$produce|${state.ordinal}|$stage"

        /** Reads `farming-varbits.tsv`. */
        fun load(): VarbitTable {
            val lists = HashMap<String, MutableList<Int>>()
            for (cols in rows("farming-varbits.tsv", COLUMNS, "patch\t")) {
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

/**
 * `farming-saplings.tsv` (Wiki "Seedling" / "Sapling"): a tree [seed] in a filled plant pot makes the [seedling], watering makes the
 * [wet] seedling, and the next growth tick (five minutes) turns it into the [sapling] (the crop's own seed).
 */
internal class SaplingChain(val key: String, val seed: Int, val seedling: Int, val wet: Int, val sapling: Int)

internal class SaplingTable private constructor(val chains: List<SaplingChain>) {
    val bySeed: Map<Int, SaplingChain> = chains.associateBy { it.seed }
    val byWet: Map<Int, SaplingChain> = chains.associateBy { it.wet }
    val bySeedling: Map<Int, SaplingChain> = chains.associateBy { it.seedling }

    companion object {
        fun load(): SaplingTable =
            SaplingTable(
                rows(
                    "farming-saplings.tsv",
                    5,
                    "key\t",
                ).map { SaplingChain(it[0], it[1].toInt(), it[2].toInt(), it[3].toInt(), it[4].toInt()) },
            )
    }
}

/** One payment item of a farmer's protection price (`farming-payments.tsv`, Wiki "Seeds"). */
internal class Payment(val item: Int, val count: Int)

internal class PaymentTable private constructor(private val byCrop: Map<String, List<Payment>>) {
    /** The price for protecting a patch of the crop [key], or empty if the crop cannot be protected. */
    fun of(key: String): List<Payment> = byCrop[key] ?: emptyList()

    companion object {
        fun load(): PaymentTable =
            PaymentTable(rows("farming-payments.tsv", 3, "key\t").groupBy({ it[0] }, { Payment(it[1].toInt(), it[2].toInt()) }))
    }
}

/** A farming contract task (`farming-contracts.tsv`, Wiki "Farming contract"): grow [seed] in a [patch] for a tier [tier] seed pack. */
internal class ContractTask(val difficulty: String, val unlockLevel: Int, val seed: Int, val patch: String, val tier: Int)

internal object ContractTable {
    fun load(): List<ContractTask> =
        rows("farming-contracts.tsv", 5, "difficulty\t").map { ContractTask(it[0], it[1].toInt(), it[2].toInt(), it[3], it[4].toInt()) }
}

/** `farming-seedpacks.tsv` (Wiki "Seed pack"): rolls per tier and the three seed reward tables. */
internal class SeedPackTable private constructor(
    val rolls: Map<Int, IntArray>,
    val drops: Map<String, List<IntArray>>,
) {
    companion object {
        /** Reads the file: ROLLS rows `lowMin lowMax medMin medMax highMin highMax highOneIn total`, DROP rows `seed min max num den`. */
        fun load(): SeedPackTable {
            val rolls = HashMap<Int, IntArray>()
            val drops = HashMap<String, MutableList<IntArray>>()
            for (cols in rows("farming-seedpacks.tsv", 9, "kind\t")) {
                val numbers = cols.drop(2).map { it.toInt() }.toIntArray()
                if (cols[0] == "ROLLS") {
                    rolls[cols[1].toInt()] = numbers
                } else {
                    drops.getOrPut(cols[1]) { ArrayList() } += numbers
                }
            }
            return SeedPackTable(rolls, drops)
        }
    }
}
