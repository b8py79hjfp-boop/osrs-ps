@file:Suppress("MagicNumber") // data tables: Wiki "Reward pool", "Casket (Reward pool)" (docs/mechanics/tempoross.md TM9, TM10)

package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.content.skills.TemporossConfig as C

/** A weighted item of a table: Wiki [name] (a cache name resolved at world start, or an obj when [obj] is set), amount range and weight. */
private class Item(val name: String, val min: Int, val max: Int, val weight: Int, val obj: Int = -1, val noted: Boolean = true)

/**
 * The reward pool and the casket (Wiki "Reward pool", docs/mechanics/tempoross.md TM9, TM10). One search is one roll: the main
 * table (flakes 1/4, casket 1/20, fish by base Fishing level 45/80, junk 22/160) plus independent unique rolls. Obj ids with
 * no gameval symbol are found by cache name when the world starts.
 */
internal class TemporossRewards {
    private var fishTables: List<List<Pair<Item, Int>>> = emptyList()
    private var junk: List<Pair<Item, Int>> = emptyList()
    private var casket: List<Pair<Item, Int>> = emptyList()

    fun resolve(world: WorldRef) {
        fun id(item: Item): Int = if (item.obj >= 0) item.obj else world.objIdsNamed(item.name).minOrNull() ?: -1
        fishTables = FISH_TABLES.map { rows -> rows.map { it to id(it) }.filter { it.second >= 0 } }
        junk = JUNK.map { it to id(it) }.filter { it.second >= 0 }
        casket = CASKET.map { it to id(it) }.filter { it.second >= 0 }
    }

    /**
     * Permits for [points] (Wiki "Tempoross" Rewards): none under 2,000, then 1 plus one per 700 points beyond, and the
     * remainder is a linear chance (remainder / 700) for one more.
     */
    fun permits(p: PlayerRef, points: Int): Int {
        if (points < C.PERMIT_FIRST) return 0
        val past = points - C.PERMIT_FIRST
        val extra = if (p.random(C.PERMIT_STEP) < past % C.PERMIT_STEP) 1 else 0
        return 1 + past / C.PERMIT_STEP + extra
    }

    /** Rolls one search of the pool for [p] and gives the result. False when it could not be delivered. */
    fun search(p: PlayerRef): Boolean {
        val loot = ArrayList<Pair<Int, Int>>()
        uniques(p, loot)
        main(p, loot)
        val ok =
            p.transaction("tempoross reward") {
                for ((obj, count) in loot) if (!addOrDrop(Invs.INV, obj, count)) return@transaction false
                true
            }
        if (!ok) return false
        for ((obj, count) in loot) p.events.publish(tp.plugin.LootEvent(p, obj, count))
        p.addXp(tp.plugin.Stats.FISHING, C.REWARD_XP)
        return true
    }

    /** The independent rolls of the pool's unique table. A duplicate barrel or box becomes 25 soaked pages (Wiki "Fish barrel"). */
    private fun uniques(p: PlayerRef, loot: MutableList<Pair<Int, Int>>) {
        if (p.random(PAGE_DENOMINATOR) < PAGE_NUMERATOR) loot += Objs.SOAKED_PAGE to range(p, PAGE_MIN, PAGE_MAX)
        if (p.random(BARREL_ODDS) == 0) {
            val owned = owned(p, Objs.FISH_BARREL_CLOSED) + owned(p, Objs.FISH_BARREL_OPEN)
            loot += if (owned > 0) Objs.SOAKED_PAGE to DUPLICATE_PAGES else Objs.FISH_BARREL_CLOSED to 1
        }
        if (p.random(BARREL_ODDS) == 0) {
            loot += if (owned(p, Objs.TACKLE_BOX) > 0) Objs.SOAKED_PAGE to DUPLICATE_PAGES else Objs.TACKLE_BOX to 1
        }
        if (p.random(RARE_ODDS) == 0) loot += Objs.POH_TROPHYDROP_HARPOONFISH to 1
        if (p.random(RARE_ODDS) == 0) loot += Objs.TOME_OF_WATER_UNCHARGED to 1
        if (p.random(PET_ODDS) == 0) loot += Objs.TEMPOROSSPET to 1
        if (p.random(PET_ODDS) == 0) loot += Objs.DRAGON_HARPOON to 1
    }

    /** The main table: weights out of 6,400 (flakes 1,600, casket 320, fish 3,600, junk 880). */
    private fun main(p: PlayerRef, loot: MutableList<Pair<Int, Int>>) {
        var r = p.random(MAIN_TOTAL)
        if (r < FLAKES_WEIGHT) return run { loot += Objs.SPIRIT_FLAKES to range(p, FLAKES_MIN, FLAKES_MAX) }
        r -= FLAKES_WEIGHT
        if (r < CASKET_WEIGHT) return run { loot += Objs.TEMPOROSS_REWARD_CASKET to 1 }
        r -= CASKET_WEIGHT
        if (r < FISH_WEIGHT) {
            val row = fishTables[fishBracket(p.baseLevel(tp.plugin.Stats.FISHING))]
            pick(p, row, r)?.let { loot += it }
            return
        }
        pick(p, junk, r - FISH_WEIGHT)?.let { loot += it }
    }

    /** The item of [rows] whose cumulative weight holds [roll] (rolls past the end give nothing). */
    private fun pick(p: PlayerRef, rows: List<Pair<Item, Int>>, roll: Int): Pair<Int, Int>? {
        var left = roll
        for ((item, id) in rows) {
            if (left < item.weight) return deliver(p, item, id)
            left -= item.weight
        }
        return null
    }

    private fun deliver(p: PlayerRef, item: Item, id: Int): Pair<Int, Int> {
        val obj = if (item.noted) p.noted(id).takeIf { it >= 0 } ?: id else id
        return obj to range(p, item.min, item.max)
    }

    /** Opens one casket: a weighted pick out of 230 (Wiki "Casket (Reward pool)"; OPEN-TM10: the clue scroll tertiaries are not given). */
    fun openCasket(p: PlayerRef): Boolean {
        val pick = pick(p, casket, p.random(CASKET_TOTAL)) ?: return false
        val ok =
            p.transaction("tempoross casket") {
                remove(Invs.INV, Objs.TEMPOROSS_REWARD_CASKET, 1) && addOrDrop(Invs.INV, pick.first, pick.second)
            }
        if (ok) p.events.publish(tp.plugin.LootEvent(p, pick.first, pick.second))
        return ok
    }

    private fun owned(p: PlayerRef, obj: Int): Long = p.inv(Invs.INV).total(obj) + p.inv(Invs.BANK).total(obj) + p.inv(Invs.WORN).total(obj)

    private fun range(p: PlayerRef, min: Int, max: Int): Int = min + p.random(max - min + 1)

    private companion object {
        /** Wiki "Reward pool": the fish table follows the base Fishing level: 35-39, 40-45, 46-49, 50-75, 76-78, 79-80, 81+. */
        val BRACKET_STARTS = intArrayOf(35, 40, 46, 50, 76, 79, 81)

        fun fishBracket(level: Int): Int = BRACKET_STARTS.indexOfLast { level >= it }.coerceAtLeast(0)

        const val MAIN_TOTAL = 6400
        const val FLAKES_WEIGHT = 1600
        const val CASKET_WEIGHT = 320
        const val FISH_WEIGHT = 3600
        const val FLAKES_MIN = 32
        const val FLAKES_MAX = 64

        /** Wiki "Reward pool/Rewards unique": soaked pages 149/8000 for 5 to 9 (OPEN-TM9: the Tempoross page says 5 to 25). */
        const val PAGE_NUMERATOR = 149
        const val PAGE_DENOMINATOR = 8000
        const val PAGE_MIN = 5
        const val PAGE_MAX = 9
        const val DUPLICATE_PAGES = 25
        const val BARREL_ODDS = 400
        const val RARE_ODDS = 1600
        const val PET_ODDS = 8000

        const val CASKET_TOTAL = 230

        /** Quantity ranges of the five fish of a bracket, best first, with their weights out of 6,400 (Wiki "Reward pool" Fish). */
        private val QUANTITIES = listOf(30 to 40, 20 to 30, 10 to 20, 7 to 12, 5 to 10)
        private val WEIGHTS = intArrayOf(900, 810, 720, 630, 540)

        private fun bracket(vararg names: String): List<Item> =
            names.mapIndexed { i, n -> Item(n, QUANTITIES[i].first, QUANTITIES[i].second, WEIGHTS[i]) }

        val FISH_TABLES: List<List<Item>> =
            listOf(
                bracket("Raw herring", "Raw mackerel", "Raw pike", "Raw salmon", "Raw tuna"),
                bracket("Raw mackerel", "Raw pike", "Raw salmon", "Raw tuna", "Raw lobster"),
                bracket("Raw pike", "Raw salmon", "Raw tuna", "Raw lobster", "Raw bass"),
                bracket("Raw salmon", "Raw tuna", "Raw lobster", "Raw bass", "Raw swordfish"),
                bracket("Raw tuna", "Raw lobster", "Raw bass", "Raw swordfish", "Raw shark"),
                bracket("Raw lobster", "Raw bass", "Raw swordfish", "Raw shark", "Raw sea turtle"),
                bracket("Raw bass", "Raw swordfish", "Raw shark", "Raw sea turtle", "Raw manta ray"),
            )

        /** Wiki "Reward pool/Rewards other", as weights out of 6,400 (160ths times 40). */
        val JUNK: List<Item> =
            listOf(
                Item("Plank", 20, 30, 280),
                Item("Oak plank", 15, 25, 200),
                Item("Seaweed", 20, 60, 160, obj = Objs.SEAWEED),
                Item("Steel nails", 300, 500, 80, noted = false),
                Item("Feather", 800, 1600, 80, obj = Objs.FEATHER, noted = false),
                Item("Fishing bait", 800, 1600, 80, obj = Objs.FISHING_BAIT, noted = false),
            )

        /** Wiki "Casket (Reward pool)": weights out of 230; everything is delivered noted. */
        val CASKET: List<Item> =
            listOf(
                Item("Rune med helm", 1, 1, 9),
                Item("Rune full helm", 1, 1, 9),
                Item("Rune chainbody", 1, 1, 9),
                Item("Rune platebody", 2, 3, 1),
                Item("Rune platelegs", 2, 3, 1),
                Item("Rune plateskirt", 2, 3, 1),
                Item("Rune sq shield", 3, 5, 1),
                Item("Rune kiteshield", 3, 5, 1),
            ) +
                listOf("ring", "necklace", "bracelet").flatMap { kind ->
                    listOf(
                        Item("Sapphire $kind", 10, 14, 9),
                        Item("Emerald $kind", 7, 11, 9),
                        Item("Ruby $kind", 6, 8, 9),
                        Item("Diamond $kind", 3, 4, 9),
                        Item("Sapphire $kind", 20, 28, 1),
                        Item("Emerald $kind", 14, 22, 1),
                        Item("Ruby $kind", 12, 16, 1),
                        Item(
                            "Diamond $kind",
                            if (kind == "bracelet") {
                                3
                            } else if (kind == "necklace") {
                                4
                            } else {
                                6
                            },
                            8,
                            1,
                        ),
                    )
                } +
                listOf(
                    Item("Silver bar", 50, 100, 9),
                    Item("Gold bar", 50, 100, 9),
                    Item("Uncut opal", 25, 50, 9),
                    Item("Uncut jade", 25, 50, 9),
                    Item("Uncut red topaz", 10, 15, 9),
                    Item("Uncut sapphire", 20, 30, 1),
                    Item("Uncut emerald", 15, 25, 1),
                    Item("Uncut ruby", 10, 20, 1),
                    Item("Uncut diamond", 8, 13, 1),
                    Item("Uncut dragonstone", 2, 3, 1),
                    Item("Coins", 4000, 6000, 9, obj = Objs.COINS, noted = false),
                    Item("Coins", 25000, 50000, 1, obj = Objs.COINS, noted = false),
                    Item("Loop half of key", 1, 1, 9),
                    Item("Tooth half of key", 1, 1, 9),
                )
    }
}
