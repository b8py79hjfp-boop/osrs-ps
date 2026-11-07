package tp.content.sailing

import tp.plugin.PlayerRef
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Where one charting task is done, from `charting-tasks.tsv` (the Wiki's `SeaChartRow`, docs/mechanics/sailing-charting.md C4):
 * the task's tile ([x], [z]) and for a current-duck task the tile where the duck comes to rest ([x2], [z2]). The generic tasks'
 * tiles are the exact tiles of their locs in the cache map (checked with `find-locs`).
 */
internal class ChartingSite(
    val uniqueId: Int,
    val level: Int,
    val type: ChartingType,
    /** Wiki ocean name of the task ("Bonus charts" for the miscellaneous groups, as in `charting-seas.tsv`). */
    val ocean: String,
    val x: Int,
    val z: Int,
    val x2: Int,
    val z2: Int,
) {
    val tile: CoordGrid get() = CoordGrid(x, z, 0)
    val endTile: CoordGrid get() = CoordGrid(x2, z2, 0)
    val hasEnd: Boolean get() = x2 != 0 || z2 != 0
}

/** Sailing XP per sea bonus row and ocean bonus row of the Wiki ("Sea charting/By oceans"), docs/mechanics/sailing-charting.md C7. */
internal class ChartingBonuses(
    /** Bonus XP for charting every task of a sea, by [normalise]d sea name. */
    val sea: Map<String, Int>,
    /** Ocean completion bonus (XP and the level needed to claim it) by Wiki ocean name. */
    val ocean: Map<String, Pair<Int, Int>>,
    /** Bonus for charting all 358 tasks. */
    val full: Int,
) {
    companion object {
        /** Wiki ocean name used for the 34 tasks of the miscellaneous groups (Wiki "Miscellaneous"). */
        const val MISC = "Bonus charts"

        fun normalise(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
    }
}

/** The static data of sea charting: the sites of the 358 tasks and the completion bonuses. Read once at world start. */
internal class ChartingData private constructor(
    val sites: Map<Int, ChartingSite>,
    val bonuses: ChartingBonuses,
) {
    companion object {
        private const val TASK = 0
        private const val LEVEL = 1
        private const val TYPE = 2
        private const val OCEAN = 5
        private const val X = 6
        private const val Z = 7
        private const val X2 = 8
        private const val Z2 = 9

        /** The `type` cell of `charting-tasks.tsv` (the Wiki's names) per [ChartingType]. */
        private val TYPE_NAMES =
            mapOf(
                "Generic" to ChartingType.GENERIC,
                "Spyglass" to ChartingType.SPYGLASS,
                "Current" to ChartingType.CURRENT_DUCK,
                "Crate" to ChartingType.DRINK_CRATE,
                "Weather" to ChartingType.WEATHER_TROLL,
                "Diving" to ChartingType.MERMAID_GUIDE,
            )

        fun load(): ChartingData {
            val sites = HashMap<Int, ChartingSite>()
            for (cells in rows("charting-tasks.tsv")) {
                val id = cells[TASK].toIntOrNull() ?: continue
                val type = TYPE_NAMES[cells[TYPE]] ?: continue
                sites[id] =
                    ChartingSite(
                        id,
                        cells[LEVEL].toInt(),
                        type,
                        cells[OCEAN],
                        cells[X].toInt(),
                        cells[Z].toInt(),
                        cells.getOrNull(X2)?.toIntOrNull() ?: 0,
                        cells.getOrNull(Z2)?.toIntOrNull() ?: 0,
                    )
            }
            val sea = HashMap<String, Int>()
            for (cells in rows("charting-seas.tsv")) sea[ChartingBonuses.normalise(cells[1])] = cells[4].toInt()
            val ocean = HashMap<String, Pair<Int, Int>>()
            var full = 0
            for (cells in rows("charting-oceans.tsv")) {
                if (cells[0] == FULL_ROW) full = cells[2].toInt() else ocean[cells[0]] = cells[2].toInt() to cells[1].toInt()
            }
            return ChartingData(sites, ChartingBonuses(sea, ocean, full))
        }

        /** Row of `charting-oceans.tsv` holding the bonus for all tasks. */
        private const val FULL_ROW = "*"

        private fun rows(file: String): List<List<String>> {
            val text = ChartingData::class.java.getResource(file)?.readText() ?: error("$file missing")
            return text
                .lineSequence()
                .drop(1)
                .filter { it.isNotBlank() }
                .map { it.split('\t') }
                .toList()
        }
    }
}

/**
 * Per-player charting state in the `saved` values (docs/mechanics/sailing-charting.md C8). Task `uniqueId` n is bit n % 32 of
 * the Long `sailing.chart.w` with w = n / 32: the same layout as the varps `SAILING_CHARTING_COMPLETION_0..11` the captain's
 * log reads (client script 9004), so [mirror] copies the words. Further values: `sailing.chart.seas.w` (bit per sea
 * `UNIQUE_ID`: its bonus was paid) and `sailing.chart.claims` (bit per ocean in [ChartingBonuses.ocean] order: claimed; the top
 * bit is the 100% bonus).
 */
internal object ChartingSave {
    private const val WORD_BITS = 32
    private const val WORDS = 12
    private const val SEA_WORDS = 3
    private const val KEY = "sailing.chart."
    private const val SEAS_KEY = "sailing.chart.seas."
    const val CLAIMS_KEY: String = "sailing.chart.claims"

    fun isDone(p: PlayerRef, uniqueId: Int): Boolean = bit(p, KEY + uniqueId / WORD_BITS, uniqueId % WORD_BITS)

    fun setDone(p: PlayerRef, uniqueId: Int) {
        setBit(p, KEY + uniqueId / WORD_BITS, uniqueId % WORD_BITS)
        p.setVarp(Varps.SAILING_CHARTING_COMPLETION_0 + uniqueId / WORD_BITS, (p.saved(KEY + uniqueId / WORD_BITS) ?: 0L).toInt())
    }

    fun seaPaid(p: PlayerRef, seaId: Int): Boolean = bit(p, SEAS_KEY + seaId / WORD_BITS, seaId % WORD_BITS)

    fun setSeaPaid(p: PlayerRef, seaId: Int) {
        require(seaId / WORD_BITS < SEA_WORDS) { "sea id $seaId outside the saved words" }
        setBit(p, SEAS_KEY + seaId / WORD_BITS, seaId % WORD_BITS)
    }

    fun claimed(p: PlayerRef, index: Int): Boolean = bit(p, CLAIMS_KEY, index)

    fun setClaimed(p: PlayerRef, index: Int) = setBit(p, CLAIMS_KEY, index)

    /** Copies the saved words into the completion varps (login). */
    fun mirror(p: PlayerRef) {
        for (w in 0 until WORDS) {
            val word = p.saved(KEY + w)?.toInt() ?: 0
            if (word != 0) p.setVarp(Varps.SAILING_CHARTING_COMPLETION_0 + w, word)
        }
    }

    private fun bit(p: PlayerRef, key: String, n: Int): Boolean = (p.saved(key) ?: 0L) and (1L shl n) != 0L

    private fun setBit(p: PlayerRef, key: String, n: Int) = p.setSaved(key, (p.saved(key) ?: 0L) or (1L shl n))
}
