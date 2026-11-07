package tp.content.skills

import tp.plugin.DbRowRef
import tp.plugin.WorldRef
import tp.symbols.DbTables

/** A Slayer reward (a row of cache db table 117 `slayer_unlock`): [bit] is its unlock bit, [tab] 0 unlock / 1 extend / 2 cosmetic. */
internal class SlayerUnlock(
    val row: Int,
    val bit: Int,
    val cost: Int,
    val name: String,
    val tab: Int,
    /** Whether the client offers "Disable" once it is bought (client script 413 reads column 5 of the row). */
    val disableable: Boolean,
)

/**
 * A task as the cache lists it (db table 113 `slayer_task`). [id] is what the client shows from `slayer_target` (-1 for the
 * boss rows). [extension] / [additive] are the longer-task unlocks (the amounts the unlock gives, or what it adds), [heavy] the
 * unlock that raises the weight and [blockUnlock] the unlock that removes the task from the lists (Stop the Wyvern).
 */
internal class SlayerTaskRow(
    val row: Int,
    val id: Int,
    val name: String,
    val extension: Extension?,
    val additive: Extension?,
    val heavy: Extension?,
    val blockUnlock: Int,
)

/** A place a master gives a task in (db table 115 row): [id] is what the `slayer_area` varp holds, [text] reads "in the Smoke Dungeon". */
internal class Area(val id: Int, val text: String)

/** An unlock row with the amounts it sets ([min], [max]) or, for a weighting unlock, the new weight in [min]. */
internal class Extension(val unlockRow: Int, val min: Int, val max: Int)

/**
 * The Slayer tables of the cache (rev 242 db tables 113 task, 114 master task, 115 area, 116 sublist, 117 unlock), read once at
 * world start. The cache has no quest or unlock requirements for assignments (those are Jagex server scripts); they come from
 * the Wiki (`slayer-requirements.tsv`). What the cache has: the rewards (cost, bit, tab), the task ids the client displays,
 * the longer-task amounts and the weight of the boss entry of each master.
 */
internal class SlayerCache(world: WorldRef) {
    val unlocksByBit: Map<Int, SlayerUnlock>
    val unlocksByRow: Map<Int, SlayerUnlock>
    private val unlocksByName: Map<String, SlayerUnlock>
    private val tasksByRow: Map<Int, SlayerTaskRow>
    val tasksById: Map<Int, SlayerTaskRow>
    private val tasksByName: Map<String, SlayerTaskRow>

    /** Weight of the boss entry of a master (master id to weight): Slayer_Master_Task rows of the task `boss`. */
    val bossWeight: Map<Int, Int>

    /** Areas a master gives a task in (master id and task row to Slayer_Area ids), empty for masters without locations. */
    private val areas: Map<Long, List<Int>>

    /** Slayer_Area row to its area id (what `slayer_area` holds) and its text ("in the Catacombs of Kourend"). */
    val areaOf: Map<Int, Area>

    /** The Buy tab: enums 840 (index to obj), 841 (obj to set size, default 1) and 842 (obj to points). */
    val buyObjs: Map<Int, Int> = world.enumInts(ENUM_BUY_OBJS)
    val buySet: Map<Int, Int> = world.enumInts(ENUM_BUY_SET)
    val buyCost: Map<Int, Int> = world.enumInts(ENUM_BUY_COST)

    init {
        val unlocks = world.dbRows(DbTables.Slayer_Unlock.ID).mapNotNull { unlockOf(it) }
        unlocksByBit = unlocks.associateBy { it.bit }
        unlocksByRow = unlocks.associateBy { it.row }
        unlocksByName = unlocks.associateBy { it.name.lowercase() }
        val tasks = world.dbRows(DbTables.Slayer_Task.ID).map { task(it) }
        tasksByRow = tasks.associateBy { it.row }
        tasksById = tasks.filter { it.id != -1 }.associateBy { it.id }
        tasksByName = tasks.filter { it.id != -1 }.associateBy { it.name }
        val boss = tasksById[BOSS_TASK_ID]?.row ?: -1
        val master = world.dbRows(DbTables.Slayer_Master_Task.ID)
        bossWeight =
            master
                .filter { it.int(DbTables.Slayer_Master_Task.COL_TASK) == boss }
                .mapNotNull { r ->
                    val id = r.int(DbTables.Slayer_Master_Task.COL_MASTER_ID) ?: return@mapNotNull null
                    id to (r.int(DbTables.Slayer_Master_Task.COL_WEIGHT) ?: 0)
                }.toMap()
        areas =
            master
                .mapNotNull { r ->
                    val id = r.int(DbTables.Slayer_Master_Task.COL_MASTER_ID) ?: return@mapNotNull null
                    val task = r.int(DbTables.Slayer_Master_Task.COL_TASK) ?: return@mapNotNull null
                    val list = list(r, DbTables.Slayer_Master_Task.COL_AREAS)
                    if (list.isEmpty()) null else key(id, task) to list
                }.toMap()
        areaOf =
            world
                .dbRows(DbTables.Slayer_Area.ID)
                .mapNotNull { r ->
                    val id = r.int(DbTables.Slayer_Area.COL_AREA_ID) ?: return@mapNotNull null
                    r.id to Area(id, r.string(DbTables.Slayer_Area.COL_AREA_TEXT).orEmpty())
                }.toMap()
    }

    /** The unlock called [name] (case-insensitive); fails fast on a name the cache lacks, so a typo cannot silently never unlock. */
    fun unlock(name: String): SlayerUnlock = unlocksByName[name.lowercase()] ?: error("no Slayer unlock named '$name' in db table 117")

    /** The unlock called [name], or null. */
    fun unlockOrNull(name: String): SlayerUnlock? = unlocksByName[name.lowercase()]

    /** The cache task for the Wiki task [name] ("Dust devils"), or null; the Wiki and cache spell a few names differently. */
    fun taskNamed(name: String): SlayerTaskRow? {
        val lower = name.lowercase()
        return tasksByName[ALIASES[lower] ?: lower]
    }

    /** The areas master [master] may give task row [task] in (Slayer_Area rows), empty if it gives no location. */
    fun areasOf(master: Int, task: SlayerTaskRow): List<Int> = areas[key(master, task.row)].orEmpty()

    private fun unlockOf(r: DbRowRef): SlayerUnlock? {
        val bit = r.int(DbTables.Slayer_Unlock.COL_BIT) ?: return null
        val tab = r.int(DbTables.Slayer_Unlock.COL_LIST_POSITION, 0, 0) ?: -1
        return SlayerUnlock(
            r.id,
            bit,
            r.int(DbTables.Slayer_Unlock.COL_COST) ?: 0,
            r.string(DbTables.Slayer_Unlock.COL_NAME).orEmpty(),
            tab,
            r.int(DbTables.Slayer_Unlock.COL_REFUNDABLE) == 1,
        )
    }

    private fun task(r: DbRowRef): SlayerTaskRow =
        SlayerTaskRow(
            r.id,
            r.int(DbTables.Slayer_Task.COL_ID_0) ?: -1,
            r.string(DbTables.Slayer_Task.COL_NAME_LOWERCASE).orEmpty(),
            extension(r, DbTables.Slayer_Task.COL_EXTENSION_MIN_MAX),
            extension(r, DbTables.Slayer_Task.COL_EXTENSION_ADDITIVE),
            weighting(r, DbTables.Slayer_Task.COL_UNLOCK_WEIGHTING),
            r.int(DbTables.Slayer_Task.COL_BLOCK_UNLOCK) ?: -1,
        )

    private fun extension(r: DbRowRef, column: Int): Extension? {
        val unlock = r.int(column, 0, 0) ?: return null
        return Extension(unlock, r.int(column, 0, 1) ?: 0, r.int(column, 0, 2) ?: 0)
    }

    /** A weighting unlock: the unlock row and the new weight (the column is a pair, not a triple). */
    private fun weighting(r: DbRowRef, column: Int): Extension? {
        val unlock = r.int(column, 0, 0) ?: return null
        return Extension(unlock, r.int(column, 0, 1) ?: 0, 0)
    }

    /** Every entry of a column that holds a list of one value. */
    private fun list(r: DbRowRef, column: Int): List<Int> {
        val out = ArrayList<Int>()
        while (true) out += r.int(column, out.size) ?: break
        return out
    }

    companion object {
        /** Task id of the generic `boss` task (client scripts 9736 and 5319 compare `slayer_target` with 98). */
        const val BOSS_TASK_ID = 98

        /** Enums of the Buy tab, read by client script 9789: 840 index to obj, 841 obj to set size, 842 obj to points. */
        private const val ENUM_BUY_OBJS = 840
        private const val ENUM_BUY_SET = 841
        private const val ENUM_BUY_COST = 842

        /** Wiki task names that the cache spells differently (lower case). */
        private val ALIASES =
            mapOf(
                "kalphite" to "kalphites",
                "flesh crawlers" to "fleshcrawlers",
                "aviansie" to "aviansies",
                "custodian stalker" to "custodian stalkers",
                "bosses" to "boss",
            )

        private fun key(master: Int, task: Int): Long = (master.toLong() shl Int.SIZE_BITS) or task.toLong()
    }
}
