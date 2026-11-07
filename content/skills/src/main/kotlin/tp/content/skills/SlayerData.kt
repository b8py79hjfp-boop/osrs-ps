@file:Suppress("MagicNumber") // The Slayer master table below is data: every number cites the Wiki page named at the table.

package tp.content.skills

import tp.plugin.Stats

/**
 * One task a master can assign (a row of `slayer-tasks.tsv`, written by tools/wiki-import/slayer_tasks.py): [group] is the
 * monster group (key shared with `slayer-monsters.tsv`), [min]..[max] the amount, [emin]..[emax] the amount with the "Extend"
 * unlock (0 if the Wiki lists none), [weight] its share of the master's list. [slayer] / [combat] are the levels needed,
 * [skills] other skill levels (stat id to level) and [requirement] the Wiki text of any requirement that is not a level (a quest
 * or a reward unlock, `-` if none), which `slayer-requirements.tsv` turns into an expression (docs/mechanics/slayer.md SL3, SL10).
 */
internal class SlayerTask(
    val group: String,
    val name: String,
    val min: Int,
    val max: Int,
    val emin: Int,
    val emax: Int,
    val weight: Int,
    val slayer: Int,
    val combat: Int,
    val skills: Map<Int, Int>,
    val requirement: String,
)

/** A Slayer monster npc type (a row of `slayer-monsters.tsv`): the Slayer level to harm it and the Slayer xp per kill. */
internal class SlayerMonster(val group: String, val slayer: Int, val xp: Double)

/**
 * A Slayer master. [names] are the cache NPC names that act as this master (the Wiki lists the quest replacements as the
 * same master: Aya, Achtryn, Kuradal and Steve). [points] are the Slayer reward points of a task by interval, in order:
 * every task, 10th, 50th, 100th, 250th, 1,000th (Wiki "Slayer reward point", "Assignments" table). [combat] / [slayer] are
 * the levels needed to be assigned tasks (Wiki "Slayer Master" table). [id] is the master id of the cache db tables and of
 * the `slayer_master` varbits, [blockCost] the points a block costs (client script 823, Wiki "Slayer Rewards" changes of 27 August
 * 2025) and [blockVarbits] the seven block slots of the master's list (varbits `slayer_blocked_<master>_1..6` and `_diary`; Turael
 * and Spria share one list). [elite] is the Slayer reward point table once the Elite diary of [eliteDiary] (a diary-complete
 * varbit) is done, null if the diary changes nothing (Wiki "Slayer reward point" notes on Nieve and Konar).
 */
internal class SlayerMaster(
    val key: String,
    val names: List<String>,
    val combat: Int,
    val slayer: Int,
    val points: IntArray,
    val id: Int,
    val blockCost: Int,
    val blockVarbits: IntArray,
    val eliteDiary: Int = -1,
    val elite: IntArray? = null,
)

/**
 * A boss a master can assign with the "Like a boss" unlock (a row of `slayer-bosses.tsv`, Wiki "Boss Slayer"): [id] is the cache's
 * boss id (`slayer_target_bossid`), [npcs] the lower-case cache names of the npcs whose kills count, [wild] whether the boss is in
 * the Wilderness (Krystilia assigns only those), [only] the one master that can assign it, [expr] its requirements.
 */
internal class SlayerBoss(val id: Int, val name: String, val npcs: Set<String>, val wild: Boolean, val only: String?, val expr: String)

/**
 * The superior form of a Slayer monster (a row of `slayer-superiors.tsv`, Wiki "Superior slayer monster"): [normal] are the
 * lower-case cache names of the monsters that can spawn it, [npc] its cache npc id, [slayer] the Slayer level of the monster (the
 * drop formula uses it), [xp] its Slayer xp and [wild] whether it appears in the Wilderness (the spawn chance is higher there).
 */
internal class SlayerSuperior(val normal: Set<String>, val npc: Int, val slayer: Int, val xp: Double, val wild: Boolean)

/** The state variable of a quest (a row of `slayer-quests.tsv`): its [id] is a varbit if [varbit], else a varp. */
internal class QuestVar(val varbit: Boolean, val id: Int, val unstarted: Int, val end: Int)

/** The Slayer data tables, parsed once from the resource files. */
internal object SlayerData {
    private const val T_MASTER = 0
    private const val T_GROUP = 1
    private const val T_NAME = 2
    private const val T_MIN = 3
    private const val T_MAX = 4
    private const val T_EMIN = 5
    private const val T_EMAX = 6
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

    private const val NO_REQUIREMENT = "-"

    /** Block slots of a master's list: six for quest points plus the Elite Lumbridge diary one (Wiki "Slayer Rewards", Tasks). */
    const val BLOCK_SLOTS = 7

    /**
     * The masters by the `master` column of `slayer-tasks.tsv`, in the order they are listed on the Wiki's "Slayer Master" page.
     * Master ids: the cache's Slayer_Master_Task rows per master match the Wiki task counts (Turael 24 = 1, Mazchna 30 = 2,
     * Chaeldar 40 = 4, Duradel 43 = 5, Nieve 46 = 6, Krystilia 37 = 7, Konar 39 = 8, Spria 25 = 9) and client script 823 prices a
     * block as the Wiki does (Turael / Spria 40, Mazchna 50, Vannaka 60, Chaeldar 70, Konar 80, Nieve 90, Duradel / Krystilia 100).
     * Block varbits: `Varbits.SLAYER_BLOCKED_*` (cache varbits 17812-17867); elite diary varbits `WESTERN_DIARY_ELITE_COMPLETE`
     * (4474) and `KOUREND_DIARY_ELITE_COMPLETE` (7928).
     */
    val MASTERS: List<SlayerMaster> =
        listOf(
            SlayerMaster("Turael", listOf("Turael", "Aya"), 0, 0, intArrayOf(0, 0, 0, 0, 0, 0), 1, 40, blocks(17812, 17860)),
            SlayerMaster("Spria", listOf("Spria"), 0, 0, intArrayOf(0, 0, 0, 0, 0, 0), 9, 40, blocks(17812, 17860)),
            SlayerMaster("Mazchna", listOf("Mazchna", "Achtryn"), 20, 0, intArrayOf(6, 30, 90, 150, 210, 300), 2, 50, blocks(17818, 17861)),
            SlayerMaster("Vannaka", listOf("Vannaka"), 40, 0, intArrayOf(8, 40, 120, 200, 280, 400), 3, 60, blocks(17824, 17862)),
            SlayerMaster("Chaeldar", listOf("Chaeldar"), 70, 0, intArrayOf(10, 50, 150, 250, 350, 500), 4, 70, blocks(17830, 17863)),
            SlayerMaster(
                "Konar quo Maten",
                listOf("Konar quo Maten"),
                75,
                0,
                intArrayOf(18, 90, 270, 450, 630, 900),
                8,
                80,
                blocks(17836, 17864),
                7928,
                intArrayOf(20, 100, 300, 500, 700, 1000),
            ),
            SlayerMaster(
                "Nieve",
                listOf("Nieve", "Steve"),
                85,
                0,
                intArrayOf(12, 60, 180, 300, 420, 600),
                6,
                90,
                blocks(17842, 17865),
                4474,
                intArrayOf(15, 75, 225, 375, 525, 750),
            ),
            SlayerMaster(
                "Duradel",
                listOf("Duradel", "Kuradal"),
                100,
                50,
                intArrayOf(15, 75, 225, 375, 525, 750),
                5,
                100,
                blocks(17848, 17866),
            ),
            SlayerMaster("Krystilia", listOf("Krystilia"), 0, 0, intArrayOf(25, 125, 375, 625, 875, 1250), 7, 100, blocks(17854, 17867)),
        )

    /** The six consecutive quest-point block varbits from [first] and the diary one [diary]. */
    private fun blocks(first: Int, diary: Int): IntArray = IntArray(BLOCK_SLOTS) { if (it < BLOCK_SLOTS - 1) first + it else diary }

    /** The interval of each entry of [SlayerMaster.points]: a task whose number is a multiple of it earns that entry. */
    val INTERVALS: IntArray = intArrayOf(1, 10, 50, 100, 250, 1000)

    /** Tasks by master key. */
    val TASKS: Map<String, List<SlayerTask>> by lazy { loadTasks() }

    /** Slayer monsters by npc type: a type can belong to more than one group. */
    val MONSTERS: Map<Int, List<SlayerMonster>> by lazy { loadMonsters() }

    /** Requirement expressions by the lower-case Wiki text or `Master|group` key (`slayer-requirements.tsv`). */
    val REQUIREMENTS: Map<String, String> by lazy { loadRequirements() }

    /** Quest state variables by quest name (`slayer-quests.tsv`). */
    val QUESTS: Map<String, QuestVar> by lazy { loadQuests() }

    /** The bosses that can be assigned, in cache boss id order. */
    val BOSSES: List<SlayerBoss> by lazy { loadBosses() }

    /** Superiors by the lower-case name of their normal monster. */
    val SUPERIORS: Map<String, SlayerSuperior> by lazy { loadSuperiors().flatMap { s -> s.normal.map { it to s } }.toMap() }

    /** Superiors by their own npc id. */
    val SUPERIOR_BY_NPC: Map<Int, SlayerSuperior> by lazy { SUPERIORS.values.associateBy { it.npc } }

    /** A master to place in the world: cache npc, tile and level. */
    class Placed(val name: String, val npc: Int, val x: Int, val z: Int, val level: Int)

    /** Masters placed in the world by this plugin (the ones the Wiki spawn import lacks). */
    val PLACED: List<Placed> by lazy { loadPlaced() }

    /** Stat id of a Wiki skill name, or -1. */
    fun stat(name: String): Int = (0 until Stats.COUNT).firstOrNull { Stats.name(it).equals(name, ignoreCase = true) } ?: -1

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
                        c[T_EMIN].toIntOrNull() ?: 0,
                        c[T_EMAX].toIntOrNull() ?: 0,
                        c[T_WEIGHT].toInt(),
                        c[T_SLAYER].toInt(),
                        c[T_COMBAT].toInt(),
                        skills,
                        c[T_OTHER],
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

    private fun loadRequirements(): Map<String, String> = rows("slayer-requirements.tsv", 2).associate { it[0] to it[1] }

    private fun loadQuests(): Map<String, QuestVar> =
        rows("slayer-quests.tsv", 5).associate { c -> c[0] to QuestVar(c[1] == "varbit", c[2].toInt(), c[3].toInt(), c[4].toInt()) }

    private fun loadBosses(): List<SlayerBoss> =
        rows("slayer-bosses.tsv", 6).map { c ->
            SlayerBoss(
                c[0].toInt(),
                c[1],
                c[2].split(';').map { it.lowercase() }.toSet(),
                c[3] == "1",
                c[4].takeIf { it != "-" },
                c[5].takeIf { it != NO_REQUIREMENT }.orEmpty(),
            )
        }

    private fun loadSuperiors(): List<SlayerSuperior> =
        rows("slayer-superiors.tsv", 5).map { c ->
            SlayerSuperior(c[0].split(';').toSet(), c[1].toInt(), c[2].toInt(), c[3].toDouble(), c[4] == "1")
        }

    /** The requirement expression of [task] of master [master] (empty if it has none): its Wiki text and the `Master|group` key. */
    fun requirementOf(master: String, task: SlayerTask): String {
        val parts = ArrayList<String>(2)
        if (task.requirement != NO_REQUIREMENT) {
            parts += REQUIREMENTS[task.requirement.lowercase()] ?: error("slayer-requirements.tsv lacks '${task.requirement}'")
        }
        REQUIREMENTS["$master|${task.group}"]?.let { parts += it }
        return parts.joinToString("&")
    }
}
