package tp.content.sailing

import tp.plugin.PlayerRef

/**
 * The port tasks (db 197) grouped by notice board, and the per-player selection a board shows (docs/mechanics/
 * sailing-port-tasks.md PT1, PT4). The cache gives each task its board (`STARTING_PORT`); which of a board's tasks are on
 * offer to a player is server-side and unknown (OPEN-PT1), so [boardFor] picks a deterministic subset that changes when
 * the Wiki says the tasks rotate: after 8 completed tasks and at 00:00 UTC.
 */
internal class PortCatalog(data: SailingData) {
    private val byId: Map<Int, PortTask> = data.portTasks.associateBy { it.id }
    private val byRow: Map<Int, PortTask> = data.portTasks.associateBy { it.row }
    private val xp: PortTaskXp = data.taskXp

    /** Task ids of the bounty each board always offers (`guaranteed=yes` of the Wiki's `BountyTaskLine`, OPEN-P3 resolved). */
    private val guaranteed: Set<Int> = loadGuaranteed()

    private val pools: Map<Int, List<PortTask>> =
        data.portTasks
            .filter { it.startingDock >= 0 && it.id !in QUEST_TASKS }
            .groupBy { it.startingDock }
            .mapValues { (_, tasks) -> tasks.sortedBy { it.id } }

    fun task(id: Int): PortTask? = byId[id]

    fun byRow(row: Int): PortTask? = byRow[row]

    /** Base Sailing XP of [task] before any boost, or 0 if the Wiki import has none. */
    fun xpOf(task: PortTask): Int = xp.of(task.id) ?: 0

    fun isQuestTask(task: PortTask): Boolean = task.id in QUEST_TASKS

    /**
     * The tasks the notice board of [dock] shows [player] now: the board's guaranteed bounty plus a deterministic pick of
     * its other tasks, [BOARD_SIZE] in all, ordered by level. Nothing here uses randomness: the pick is a hash of the
     * account, the rotation epoch and the board, so reopening a board shows the same tasks until the epoch changes.
     */
    fun boardFor(player: PlayerRef, dock: Int): List<PortTask> {
        val pool = pools[dock] ?: return emptyList()
        val fixed = pool.filter { it.id in guaranteed }
        val seed =
            mix(
                player.username
                    .lowercase()
                    .hashCode()
                    .toLong() xor (epoch(player) * EPOCH_STRIDE) xor dock.toLong(),
            )
        val rest = pool.filter { it.id !in guaranteed }.sortedBy { mix(seed xor it.id.toLong()) }
        return (fixed + rest).take(BOARD_SIZE).sortedWith(compareBy({ it.levelRequired }, { it.id }))
    }

    private fun epoch(player: PlayerRef): Long = PortTaskSave.dayOf(player) * DAY_STRIDE + PortTaskSave.total(player) / ROTATION_TASKS

    private fun loadGuaranteed(): Set<Int> {
        val text = PortCatalog::class.java.getResource("bounty-tasks.tsv")?.readText() ?: error("bounty-tasks.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .map { it.split('\t') }
            .filter { it.getOrNull(GUARANTEED_COL) == "yes" }
            .mapNotNull { it.getOrNull(TASK_COL)?.toIntOrNull() }
            .toSet()
    }

    companion object {
        /** Tasks a board lists: `Task_Board_Layout` row 0 (db 198) has 4 + 4 positions and client script 8912 takes 8 task rows. */
        const val BOARD_SIZE: Int = 8

        /** Completed tasks after which every board's selection rotates (Wiki Notice board: "completing 8 port tasks"). */
        private const val ROTATION_TASKS = 8

        /** Task ids of the two quest deliveries (Wiki Courier tasks, first table; plan 4.3): never on a board. */
        val QUEST_TASKS: Set<Int> = setOf(599, 600)

        /** Spreads the day number past the rotation counter inside the epoch. */
        private const val DAY_STRIDE = 1_000_000L

        /** Multiplier folding the epoch into the seed (an odd constant, any would do). */
        private const val EPOCH_STRIDE = 0x9E3779B1L

        /** SplitMix64 finaliser constants (Steele, Lea, Flood 2014): a stateless bit mixer, so the pick is a pure function. */
        private const val MIX_1 = -4658895280553007687L
        private const val MIX_2 = -7723592293110705685L
        private const val MIX_SHIFT_1 = 30
        private const val MIX_SHIFT_2 = 27
        private const val MIX_SHIFT_3 = 31

        /** Columns of `bounty-tasks.tsv`. */
        private const val TASK_COL = 0
        private const val GUARANTEED_COL = 8

        private fun mix(value: Long): Long {
            var z = value
            z = (z xor (z ushr MIX_SHIFT_1)) * MIX_1
            z = (z xor (z ushr MIX_SHIFT_2)) * MIX_2
            return z xor (z ushr MIX_SHIFT_3)
        }
    }
}
