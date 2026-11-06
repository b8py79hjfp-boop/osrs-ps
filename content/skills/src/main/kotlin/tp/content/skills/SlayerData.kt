@file:Suppress("MagicNumber") // The Slayer master table below is data: every number cites the Wiki page named at the table.

package tp.content.skills

import tp.plugin.Stats

/**
 * One task a master can assign (a row of `slayer-tasks.tsv`, written by tools/wiki-import/slayer_tasks.py): [group] is the
 * monster group (key shared with `slayer-monsters.tsv`), [min]..[max] the amount, [weight] its share of the master's list.
 * [slayer] / [combat] are the levels needed, [skills] other skill levels (stat id to level) and [other] is true when the
 * Wiki lists a requirement that is not a level (a quest or a reward unlock), which this server cannot check
 * (docs/mechanics/slayer.md SL3).
 */
internal class SlayerTask(
    val group: String,
    val name: String,
    val min: Int,
    val max: Int,
    val weight: Int,
    val slayer: Int,
    val combat: Int,
    val skills: Map<Int, Int>,
    val other: Boolean,
)

/** A Slayer monster npc type (a row of `slayer-monsters.tsv`): the Slayer level to harm it and the Slayer xp per kill. */
internal class SlayerMonster(val group: String, val slayer: Int, val xp: Double)

/**
 * A Slayer master. [names] are the cache NPC names that act as this master (the Wiki lists the quest replacements as the
 * same master: Aya, Achtryn, Kuradal and Steve). [points] are the Slayer reward points of a task by interval, in order:
 * every task, 10th, 50th, 100th, 250th, 1,000th (Wiki "Slayer reward point", "Assignments" table). [combat] / [slayer] are
 * the levels needed to be assigned tasks (Wiki "Slayer Master" table).
 */
internal class SlayerMaster(val key: String, val names: List<String>, val combat: Int, val slayer: Int, val points: IntArray)

/** The Slayer data tables, parsed once from the resource files. */
internal object SlayerData {
    private const val T_MASTER = 0
    private const val T_GROUP = 1
    private const val T_NAME = 2
    private const val T_MIN = 3
    private const val T_MAX = 4
    private const val T_WEIGHT = 7
    private const val T_SLAYER = 8
    private const val T_COMBAT = 9
    private const val T_SKILLS = 10
    private const val T_OTHER = 11
    private const val TASK_COLUMNS = 12

    private const val M_ID = 0
    private const val M_GROUP = 1
    private const val M_SLAYER = 2
    private const val M_XP = 3
    private const val MONSTER_COLUMNS = 5

    private const val N_NAME = 0
    private const val N_NPC = 1
    private const val N_X = 2
    private const val N_Z = 3
    private const val N_LEVEL = 4
    private const val NPC_COLUMNS = 5

    /** The masters by the `master` column of `slayer-tasks.tsv`, in the order they are listed on the Wiki's "Slayer Master" page. */
    val MASTERS: List<SlayerMaster> =
        listOf(
            SlayerMaster("Turael", listOf("Turael", "Aya"), 0, 0, intArrayOf(0, 0, 0, 0, 0, 0)),
            SlayerMaster("Spria", listOf("Spria"), 0, 0, intArrayOf(0, 0, 0, 0, 0, 0)),
            SlayerMaster("Mazchna", listOf("Mazchna", "Achtryn"), 20, 0, intArrayOf(6, 30, 90, 150, 210, 300)),
            SlayerMaster("Vannaka", listOf("Vannaka"), 40, 0, intArrayOf(8, 40, 120, 200, 280, 400)),
            SlayerMaster("Chaeldar", listOf("Chaeldar"), 70, 0, intArrayOf(10, 50, 150, 250, 350, 500)),
            SlayerMaster("Konar quo Maten", listOf("Konar quo Maten"), 75, 0, intArrayOf(18, 90, 270, 450, 630, 900)),
            SlayerMaster("Nieve", listOf("Nieve", "Steve"), 85, 0, intArrayOf(12, 60, 180, 300, 420, 600)),
            SlayerMaster("Duradel", listOf("Duradel", "Kuradal"), 100, 50, intArrayOf(15, 75, 225, 375, 525, 750)),
            SlayerMaster("Krystilia", listOf("Krystilia"), 0, 0, intArrayOf(25, 125, 375, 625, 875, 1250)),
        )

    /** The interval of each entry of [SlayerMaster.points]: a task whose number is a multiple of it earns that entry. */
    val INTERVALS: IntArray = intArrayOf(1, 10, 50, 100, 250, 1000)

    /** Tasks by master key. */
    val TASKS: Map<String, List<SlayerTask>> by lazy { loadTasks() }

    /** Slayer monsters by npc type: a type can belong to more than one group. */
    val MONSTERS: Map<Int, List<SlayerMonster>> by lazy { loadMonsters() }

    /** A master to place in the world: cache npc, tile and level. */
    class Placed(val name: String, val npc: Int, val x: Int, val z: Int, val level: Int)

    /** Masters placed in the world by this plugin (the ones the Wiki spawn import lacks). */
    val PLACED: List<Placed> by lazy { loadPlaced() }

    /** Stat id of a Wiki skill name, or -1. */
    private fun stat(name: String): Int = (0 until Stats.COUNT).firstOrNull { Stats.name(it).equals(name, ignoreCase = true) } ?: -1

    private fun rows(file: String, columns: Int): List<List<String>> {
        val text = SlayerData::class.java.getResource(file)?.readText() ?: error("$file missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
            .filter { it.size >= columns }
            .toList()
    }

    private fun loadTasks(): Map<String, List<SlayerTask>> =
        rows("slayer-tasks.tsv", TASK_COLUMNS)
            .map { c ->
                val skills = HashMap<Int, Int>()
                if (c[T_SKILLS] != "-") {
                    for (part in c[T_SKILLS].split(';')) {
                        val (skill, level) = part.split(':')
                        skills[stat(skill)] = level.toInt()
                    }
                }
                c[T_MASTER] to
                    SlayerTask(
                        c[T_GROUP],
                        c[T_NAME],
                        c[T_MIN].toInt(),
                        c[T_MAX].toInt(),
                        c[T_WEIGHT].toInt(),
                        c[T_SLAYER].toInt(),
                        c[T_COMBAT].toInt(),
                        skills,
                        c[T_OTHER] != "-",
                    )
            }.groupBy({ it.first }, { it.second })

    private fun loadMonsters(): Map<Int, List<SlayerMonster>> =
        rows("slayer-monsters.tsv", MONSTER_COLUMNS)
            .map { c -> c[M_ID].toInt() to SlayerMonster(c[M_GROUP], c[M_SLAYER].toInt(), c[M_XP].toDouble()) }
            .groupBy({ it.first }, { it.second })

    private fun loadPlaced(): List<Placed> =
        rows("slayer-masters.tsv", NPC_COLUMNS).map { c ->
            Placed(c[N_NAME], c[N_NPC].toInt(), c[N_X].toInt(), c[N_Z].toInt(), c[N_LEVEL].toInt())
        }
}
