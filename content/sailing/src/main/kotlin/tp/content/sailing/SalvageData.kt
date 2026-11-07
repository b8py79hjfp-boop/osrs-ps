package tp.content.sailing

import tp.plugin.WorldRef
import tp.symbols.Locs
import tp.symbols.Objs

/**
 * The eight shipwreck types (docs/mechanics/sailing-salvaging.md S3). Levels and XP are the Wiki's "Shipwreck salvaging" table,
 * [lifeTicks] its average duration converted at 0.6 s per tick, [size] the larger side of the wreck loc in the cache (`config
 * loc`), [salvage] the salvage obj and [page] the Wiki page whose drop table [SalvageLoot] holds.
 */
internal enum class WreckType(
    val key: String,
    val level: Int,
    val xp: Double,
    val sortXp: Double,
    val lifeTicks: Int,
    val size: Int,
    val wreck: Int,
    val stump: Int,
    val salvage: Int,
    val page: String,
) {
    SMALL(
        "small",
        15,
        10.0,
        5.5,
        100,
        2,
        Locs.SAILING_SMALL_SHIPWRECK,
        Locs.SAILING_SMALL_SHIPWRECK_STUMP,
        Objs.SAILING_SMALL_SHIPWRECK_SALVAGE,
        "Small salvage",
    ),
    FISHERMAN(
        "fisherman",
        26,
        17.0,
        9.0,
        300,
        3,
        Locs.SAILING_FISHERMAN_SHIPWRECK,
        Locs.SAILING_FISHERMAN_SHIPWRECK_STUMP,
        Objs.SAILING_FISHERMAN_SHIPWRECK_SALVAGE,
        "Fishy salvage",
    ),
    BARRACUDA(
        "barracuda",
        35,
        31.0,
        15.5,
        300,
        3,
        Locs.SAILING_BARRACUDA_SHIPWRECK,
        Locs.SAILING_BARRACUDA_SHIPWRECK_STUMP,
        Objs.SAILING_BARRACUDA_SHIPWRECK_SALVAGE,
        "Barracuda salvage",
    ),
    LARGE(
        "large",
        53,
        48.0,
        24.0,
        300,
        4,
        Locs.SAILING_LARGE_SHIPWRECK,
        Locs.SAILING_LARGE_SHIPWRECK_STUMP,
        Objs.SAILING_LARGE_SHIPWRECK_SALVAGE,
        "Large salvage",
    ),
    PIRATE(
        "pirate",
        64,
        76.0,
        31.5,
        300,
        2,
        Locs.SAILING_PIRATE_SHIPWRECK,
        Locs.SAILING_PIRATE_SHIPWRECK_STUMP,
        Objs.SAILING_PIRATE_SHIPWRECK_SALVAGE,
        "Plundered salvage",
    ),
    MERCENARY(
        "mercenary",
        73,
        138.0,
        63.5,
        325,
        4,
        Locs.SAILING_MERCENARY_SHIPWRECK,
        Locs.SAILING_MERCENARY_SHIPWRECK_STUMP,
        Objs.SAILING_MERCENARY_SHIPWRECK_SALVAGE,
        "Martial salvage",
    ),
    FREMENNIK(
        "fremennik",
        80,
        162.0,
        75.0,
        367,
        5,
        Locs.SAILING_FREMENNIK_SHIPWRECK,
        Locs.SAILING_FREMENNIK_SHIPWRECK_STUMP,
        Objs.SAILING_FREMENNIK_SHIPWRECK_SALVAGE,
        "Fremennik salvage",
    ),
    MERCHANT(
        "merchant",
        87,
        200.0,
        95.0,
        400,
        4,
        Locs.SAILING_MERCHANT_SHIPWRECK,
        Locs.SAILING_MERCHANT_SHIPWRECK_STUMP,
        Objs.SAILING_MERCHANT_SHIPWRECK_SALVAGE,
        "Opulent salvage",
    ),
    ;

    companion object {
        fun ofKey(key: String): WreckType? = entries.firstOrNull { it.key == key }

        fun ofSalvage(obj: Int): WreckType? = entries.firstOrNull { it.salvage == obj }
    }
}

/** One shipwreck site of `salvage-wrecks.tsv`: the tile and angle of the stump the cache map places, and its cluster. */
internal class WreckSite(val type: WreckType, val x: Int, val z: Int, val level: Int, val angle: Int, val cluster: Int)

/** One drop line of a Wiki drop table: [item] (a name) with [min]..[max] of it at [num]/[den]. */
internal class LootLine(val section: String, val item: String, val min: Int, val max: Int, val num: Int, val den: Int)

/**
 * The drop tables of the salvage items, from `salvage-loot.tsv` (the `DropsLineSkill` rows of the Wiki pages "Small salvage" ...,
 * tools/wiki-import/sailing_salvage.py). Sorting rolls the "Pre-roll" lines each on their own, then one line of the main
 * sections by weight over their common denominator, then the "Tertiary" lines each on their own (OPEN-SV5: whether a pre-roll
 * hit replaces the main roll; independent here).
 */
internal class SalvageLoot private constructor(private val tables: Map<String, List<LootLine>>) {
    /** Pre-roll lines of the salvage with Wiki page [page]. */
    fun preRoll(page: String): List<LootLine> = tables[page].orEmpty().filter { it.section == PRE_ROLL }

    /** Main table lines of [page] (every section that is neither the pre-roll nor the tertiary one). */
    fun main(page: String): List<LootLine> = tables[page].orEmpty().filter { it.section != PRE_ROLL && it.section != TERTIARY }

    fun tertiary(page: String): List<LootLine> = tables[page].orEmpty().filter { it.section == TERTIARY }

    /** Every distinct item name of the tables. */
    fun names(): Set<String> =
        tables.values
            .flatten()
            .map { it.item }
            .toSet()

    /** Item names in the tables that resolve to no obj in this cache (Soup, the pet, is left out). */
    fun unresolved(world: WorldRef): Set<String> =
        tables.values
            .flatten()
            .map { it.item }
            .filter { it != SOUP && resolve(world, it) == null }
            .toSet()

    companion object {
        private const val PRE_ROLL = "Pre-roll"
        private const val TERTIARY = "Tertiary"
        private const val CLUE_PREFIX = "clue:"
        private const val COINS = "Coins"
        private const val SOUP = "Soup"

        /** Columns of `salvage-loot.tsv` after the salvage name (column 0). */
        private const val SECTION = 1
        private const val ITEM = 2
        private const val MIN = 3
        private const val MAX = 4
        private const val NUM = 5
        private const val DEN = 6

        fun load(): SalvageLoot {
            val text = SalvageLoot::class.java.getResource("salvage-loot.tsv")?.readText() ?: error("salvage-loot.tsv missing")
            val tables = HashMap<String, MutableList<LootLine>>()
            for (line in text.lineSequence().drop(1).filter { it.isNotBlank() }) {
                val c = line.split('\t')
                tables
                    .getOrPut(
                        c[0],
                    ) { ArrayList() }
                    .add(LootLine(c[SECTION], c[ITEM], c[MIN].toInt(), c[MAX].toInt(), c[NUM].toInt(), c[DEN].toInt()))
            }
            return SalvageLoot(tables)
        }

        /**
         * The obj of a Wiki item name: the lowest id with that cache name (the plain item before its variants; checked for the
         * 28 names with several ids), the coins by their symbol, clue lines as "Clue scroll (tier)". Soup is the pet and has
         * no obj line here (OPEN-SV8), so it resolves to null.
         */
        fun resolve(world: WorldRef, name: String): Int? =
            when {
                name == SOUP -> null
                name == COINS -> Objs.COINS
                name.startsWith(CLUE_PREFIX) -> world.objIdsNamed("Clue scroll (${name.removePrefix(CLUE_PREFIX)})").minOrNull()
                else -> world.objIdsNamed(name).minOrNull()
            }
    }
}

/** The data of salvaging read once at world start. */
internal class SalvageData(val sites: List<WreckSite>, val loot: SalvageLoot) {
    companion object {
        fun load(): SalvageData {
            val text = SalvageData::class.java.getResource("salvage-wrecks.tsv")?.readText() ?: error("salvage-wrecks.tsv missing")
            val sites =
                text
                    .lineSequence()
                    .drop(1)
                    .filter { it.isNotBlank() }
                    .map { line ->
                        val c = line.split('\t')
                        WreckSite(
                            WreckType.ofKey(c[0]) ?: error("unknown wreck type ${c[0]}"),
                            c[1].toInt(),
                            c[2].toInt(),
                            c[3].toInt(),
                            c[4].toInt(),
                            c[5].toInt(),
                        )
                    }.toList()
            return SalvageData(sites, SalvageLoot.load())
        }
    }
}
