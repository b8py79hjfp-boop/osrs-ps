package tp.content.skills

import kotlin.math.roundToInt

/** One line of a loot table: [min]..[max] of [obj], picked with probability [chance] (the Wiki's exact fraction). */
internal class LootRow(val obj: Int, val min: Int, val max: Int, val chance: Double, val coins: IntRange?)

/**
 * A pickpocketable NPC (docs/mechanics/thieving.md TH1-TH4), read from `pickpocket.tsv` (tools/wiki-import/thieving_pickpocket.py).
 * [rate] / [glovesRate] are the Wiki "Skilling success chart" `low` / `high` without and with gloves of silence; [damage]
 * is the stun damage range. [always] rows are given every time, [main] rows are mutually exclusive.
 */
internal class Pickable(
    val level: Int,
    val xp: Double,
    val damage: IntRange,
    val rate: Rate,
    val glovesRate: Rate,
    val always: List<LootRow>,
    val main: List<LootRow>,
)

/** A thievable stall (docs/mechanics/thieving.md TH6), read from `stalls.tsv` (tools/wiki-import/thieving_stalls.py). */
internal class Stall(val name: String, val level: Int, val xp: Double, val respawn: Int, val loot: List<LootRow>)

/**
 * A thievable chest (docs/mechanics/thieving.md TH11), read from `thieving-chests.tsv` (tools/wiki-import/thieving_chests.py).
 * [rate] / [lockpickRate] are the Wiki "Skilling success chart" `low` / `high` of the unlock without and with a lockpick in the
 * backpack; [teleportRate] the chance chart of being teleported out on a failure; the chest shows [openLoc] for [ticks] ticks.
 */
internal class Chest(
    val name: String,
    val closedLoc: Int,
    val openLoc: Int,
    val level: Int,
    val xp: Double,
    val ticks: Int,
    val rate: Rate,
    val lockpickRate: Rate,
    val teleportRate: Rate,
    val loot: List<LootRow>,
)

/** The thieving data tables, parsed once from the resource files. */
internal object ThievingData {
    /** Seconds per game tick (Wiki "Game tick"), to turn the Wiki's respawn seconds into ticks. */
    private const val TICK_SECONDS = 0.6

    private const val PICKPOCKET_COLUMNS = 16
    private const val STALL_COLUMNS = 10

    /** Columns of `pickpocket.tsv` (the header of tools/wiki-import/thieving_pickpocket.py). */
    private const val P_LEVEL = 1
    private const val P_XP = 2
    private const val P_DAMAGE = 3
    private const val P_LOW = 5
    private const val P_HIGH = 6
    private const val P_GLOVES_LOW = 7
    private const val P_GLOVES_HIGH = 8
    private const val P_KIND = 9
    private const val P_ITEM = 10
    private const val P_QTY = 12
    private const val P_NUM = 13
    private const val P_DEN = 14
    private const val P_POUCH = 15

    /** Columns of `stalls.tsv` (the header of tools/wiki-import/thieving_stalls.py). */
    private const val S_LOCS = 1
    private const val S_LEVEL = 2
    private const val S_XP = 3
    private const val S_RESPAWN = 4
    private const val S_ITEM = 5
    private const val S_QTY = 7
    private const val S_NUM = 8
    private const val S_DEN = 9

    private const val CHEST_COLUMNS = 17

    /** Columns of `thieving-chests.tsv` (the header of tools/wiki-import/thieving_chests.py). */
    private const val C_CLOSED = 1
    private const val C_OPEN = 2
    private const val C_LEVEL = 3
    private const val C_XP = 4
    private const val C_TICKS = 5
    private const val C_LOW = 6
    private const val C_HIGH = 7
    private const val C_PLOW = 8
    private const val C_PHIGH = 9
    private const val C_TLOW = 10
    private const val C_THIGH = 11
    private const val C_ITEM = 12
    private const val C_QTY = 14
    private const val C_NUM = 15
    private const val C_DEN = 16

    /** Chests by closed loc id. */
    val CHESTS: Map<Int, Chest> by lazy { loadChests() }

    /** Pickpocketable NPCs by Wiki page name. */
    val PICKPOCKETS: Map<String, Pickable> by lazy { loadPickpockets() }

    /** The coins inside each coin pouch obj (Wiki "Coin pouch" quantities). */
    val POUCHES: Map<Int, IntRange> by lazy { loadPouches() }

    /** Stalls by loc id. */
    val STALLS: Map<Int, Stall> by lazy { loadStalls() }

    private fun rows(name: String): List<List<String>> {
        val text = ThievingData::class.java.getResource(name)?.readText() ?: error("$name missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
            .toList()
    }

    private fun range(text: String): IntRange {
        val (min, max) = text.split('-')
        return min.toInt()..max.toInt()
    }

    private fun row(obj: String, qty: String, num: String, den: String, coins: IntRange?): LootRow {
        val quantity = range(qty)
        return LootRow(obj.toInt(), quantity.first, quantity.last, num.toDouble() / den.toDouble(), coins)
    }

    private fun loadPickpockets(): Map<String, Pickable> =
        rows("pickpocket.tsv").groupBy { it[0] }.mapValues { (_, lines) ->
            val head = lines.first()
            check(head.size == PICKPOCKET_COLUMNS) { "pickpocket.tsv: bad column count" }
            val always = ArrayList<LootRow>()
            val main = ArrayList<LootRow>()
            for (cols in lines) {
                val coins = if (cols[P_POUCH] == "-") null else range(cols[P_POUCH])
                // Pre-roll rows are the clue-scroll pre-roll and "nothing" (OPEN-TH5): not rolled.
                val target =
                    when (cols[P_KIND]) {
                        "always" -> always
                        "main" -> main
                        else -> continue
                    }
                target += row(cols[P_ITEM], cols[P_QTY], cols[P_NUM], cols[P_DEN], coins)
            }
            Pickable(
                level = head[P_LEVEL].toInt(),
                xp = head[P_XP].toDouble(),
                damage = range(head[P_DAMAGE]),
                rate = Rate(head[P_LOW].toInt(), head[P_HIGH].toInt()),
                glovesRate = Rate(head[P_GLOVES_LOW].toInt(), head[P_GLOVES_HIGH].toInt()),
                always = always,
                main = main,
            )
        }

    private fun loadPouches(): Map<Int, IntRange> {
        val out = HashMap<Int, IntRange>()
        for (cols in rows("pickpocket.tsv")) if (cols[P_POUCH] != "-") out[cols[P_ITEM].toInt()] = range(cols[P_POUCH])
        return out
    }

    private fun loadChests(): Map<Int, Chest> {
        val out = HashMap<Int, Chest>()
        for ((_, lines) in rows("thieving-chests.tsv").groupBy { it[0] }) {
            val head = lines.first()
            check(head.size == CHEST_COLUMNS) { "thieving-chests.tsv: bad column count" }
            val loot = lines.map { cols -> row(cols[C_ITEM], cols[C_QTY], cols[C_NUM], cols[C_DEN], null) }
            out[head[C_CLOSED].toInt()] =
                Chest(
                    name = "chest",
                    closedLoc = head[C_CLOSED].toInt(),
                    openLoc = head[C_OPEN].toInt(),
                    level = head[C_LEVEL].toInt(),
                    xp = head[C_XP].toDouble(),
                    ticks = head[C_TICKS].toInt(),
                    rate = Rate(head[C_LOW].toInt(), head[C_HIGH].toInt()),
                    lockpickRate = Rate(head[C_PLOW].toInt(), head[C_PHIGH].toInt()),
                    teleportRate = Rate(head[C_TLOW].toInt(), head[C_THIGH].toInt()),
                    loot = loot,
                )
        }
        return out
    }

    private fun loadStalls(): Map<Int, Stall> {
        val out = HashMap<Int, Stall>()
        for ((_, lines) in rows("stalls.tsv").groupBy { it[0] }) {
            val head = lines.first()
            check(head.size == STALL_COLUMNS) { "stalls.tsv: bad column count" }
            val seconds = head[S_RESPAWN].substringBefore(' ').toDouble()
            val loot = lines.map { cols -> row(cols[S_ITEM], cols[S_QTY], cols[S_NUM], cols[S_DEN], null) }
            val stall = Stall(head[0], head[S_LEVEL].toInt(), head[S_XP].toDouble(), (seconds / TICK_SECONDS).roundToInt(), loot)
            for (loc in head[S_LOCS].split(',')) out[loc.toInt()] = stall
        }
        return out
    }
}
