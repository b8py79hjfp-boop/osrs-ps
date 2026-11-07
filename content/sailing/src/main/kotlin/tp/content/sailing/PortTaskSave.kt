package tp.content.sailing

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Varbits
import tp.symbols.Varps

/** One active port task slot: the `Port_Task.TASK_ID`, the crates picked up so far and the crates delivered so far. */
internal data class TaskSlot(val taskId: Int, val taken: Int, val delivered: Int)

/**
 * Per-player port task state in the `saved` values (docs/mechanics/sailing-port-tasks.md PT3). Slot s (0..4) is one Long
 * under `sailing.task.s`: bit 0 active, then the task id, the crates taken and the crates delivered at the shifts below.
 * Further values: `sailing.task.total` (tasks completed), `sailing.task.today` and `sailing.task.day` (completed since
 * 00:00 UTC of that day), `sailing.task.last` (task id of the last crate taken). The cache varbits the client scripts read
 * (`PORT_TASK_SLOT_n_*`, `PORT_TASK_EXTRA_SLOTS_UNLOCKED`, ...) are mirrored from here by [mirror]; the 3-bit
 * `PORT_TASK_LAST_CARGO_TAKEN` holds the slot (1..5) of the last crate's task, 0 if none (no client script reads it, OPEN-PT3).
 */
internal object PortTaskSave {
    /** Port task slots at Sailing 84 (Wiki Courier tasks, docs/mechanics/sailing-port-tasks.md PT2). */
    const val SLOTS: Int = 5

    private const val KEY_PREFIX = "sailing.task."
    private const val TOTAL_KEY = "sailing.task.total"
    private const val TODAY_KEY = "sailing.task.today"
    private const val DAY_KEY = "sailing.task.day"
    private const val LAST_KEY = "sailing.task.last"

    private const val ID_SHIFT = 1
    private const val ID_BITS = 10
    private const val TAKEN_SHIFT = 11
    private const val DELIVERED_SHIFT = 19
    private const val COUNT_BITS = 8

    /** Largest value of the 5-bit varbits `PORT_TASK_SLOT_n_CARGO_TAKEN` and `_DELIVERED` (cache varbit definitions). */
    private const val VARBIT_COUNT_MAX = 31

    /** Largest value of the 10-bit varbit `PORT_TASKS_COMPLETED_TODAY` (cache varbit definition). */
    private const val VARBIT_TODAY_MAX = 1023

    /** Minutes in a UTC day: the daily reset of the notice boards is at 00:00 UTC (Wiki Notice board). */
    private const val MINUTES_PER_DAY = 1440L

    /** Sailing levels at which the 2nd .. 5th slot unlocks (Wiki Courier tasks). */
    private val SLOT_LEVELS = intArrayOf(1, 7, 28, 56, 84)

    private val SLOT_ID =
        intArrayOf(
            Varbits.PORT_TASK_SLOT_0_ID,
            Varbits.PORT_TASK_SLOT_1_ID,
            Varbits.PORT_TASK_SLOT_2_ID,
            Varbits.PORT_TASK_SLOT_3_ID,
            Varbits.PORT_TASK_SLOT_4_ID,
        )
    private val SLOT_TAKEN =
        intArrayOf(
            Varbits.PORT_TASK_SLOT_0_CARGO_TAKEN,
            Varbits.PORT_TASK_SLOT_1_CARGO_TAKEN,
            Varbits.PORT_TASK_SLOT_2_CARGO_TAKEN,
            Varbits.PORT_TASK_SLOT_3_CARGO_TAKEN,
            Varbits.PORT_TASK_SLOT_4_CARGO_TAKEN,
        )
    private val SLOT_DELIVERED =
        intArrayOf(
            Varbits.PORT_TASK_SLOT_0_CARGO_DELIVERED,
            Varbits.PORT_TASK_SLOT_1_CARGO_DELIVERED,
            Varbits.PORT_TASK_SLOT_2_CARGO_DELIVERED,
            Varbits.PORT_TASK_SLOT_3_CARGO_DELIVERED,
            Varbits.PORT_TASK_SLOT_4_CARGO_DELIVERED,
        )

    private fun mask(bits: Int): Long = (1L shl bits) - 1

    private fun key(slot: Int): String = KEY_PREFIX + slot

    /** The task in [slot] (0 until [SLOTS]), or null if the slot is empty. */
    fun get(player: PlayerRef, slot: Int): TaskSlot? {
        if (slot !in 0 until SLOTS) return null
        val v = player.saved(key(slot)) ?: return null
        if (v and 1L == 0L) return null
        return TaskSlot(
            taskId = field(v, ID_SHIFT, ID_BITS),
            taken = field(v, TAKEN_SHIFT, COUNT_BITS),
            delivered = field(v, DELIVERED_SHIFT, COUNT_BITS),
        )
    }

    /** Writes [task] into [slot]; null empties it. The client varbits are not touched, call [mirror]. */
    fun set(player: PlayerRef, slot: Int, task: TaskSlot?) {
        require(slot in 0 until SLOTS) { "task slot $slot" }
        if (task == null) return player.setSaved(key(slot), null)
        val v =
            1L or
                put(task.taskId, ID_SHIFT, ID_BITS) or
                put(task.taken, TAKEN_SHIFT, COUNT_BITS) or
                put(task.delivered, DELIVERED_SHIFT, COUNT_BITS)
        player.setSaved(key(slot), v)
    }

    /** The slot holding task id [taskId], or -1. */
    fun slotOf(player: PlayerRef, taskId: Int): Int = (0 until SLOTS).firstOrNull { get(player, it)?.taskId == taskId } ?: -1

    /** Slots a player may use at Sailing level [level] (Wiki Courier tasks: 1 / 7 / 28 / 56 / 84). Not boostable. */
    fun slotsAt(level: Int): Int = SLOT_LEVELS.count { level >= it }

    fun total(player: PlayerRef): Int = (player.saved(TOTAL_KEY) ?: 0L).toInt()

    fun lastCargoTask(player: PlayerRef): Int = (player.saved(LAST_KEY) ?: 0L).toInt()

    fun setLastCargoTask(player: PlayerRef, taskId: Int) {
        player.setSaved(LAST_KEY, taskId.toLong())
    }

    /** Tasks completed since 00:00 UTC (the streak of the notice board), 0 once the day has changed. */
    fun today(player: PlayerRef): Int = if (dayOf(player) == player.saved(DAY_KEY)) (player.saved(TODAY_KEY) ?: 0L).toInt() else 0

    /** The UTC day number of "now" (days since the epoch). */
    fun dayOf(player: PlayerRef): Long = player.epochMinutes / MINUTES_PER_DAY

    /** Counts one completed task (total and today's streak). */
    fun recordCompleted(player: PlayerRef) {
        val streak = today(player) + 1
        player.setSaved(TOTAL_KEY, (total(player) + 1).toLong())
        player.setSaved(TODAY_KEY, streak.toLong())
        player.setSaved(DAY_KEY, dayOf(player))
    }

    /** Copies the saved state into the cache varbits and varp the client scripts read (captain's log list, boards). */
    fun mirror(player: PlayerRef) {
        for (slot in 0 until SLOTS) {
            val task = get(player, slot)
            player.setVarbit(SLOT_ID[slot], task?.taskId ?: 0)
            player.setVarbit(SLOT_TAKEN[slot], minOf(task?.taken ?: 0, VARBIT_COUNT_MAX))
            player.setVarbit(SLOT_DELIVERED[slot], minOf(task?.delivered ?: 0, VARBIT_COUNT_MAX))
        }
        player.setVarbit(Varbits.PORT_TASK_EXTRA_SLOTS_UNLOCKED, slotsAt(player.baseLevel(Stats.SAILING)) - 1)
        player.setVarbit(Varbits.PORT_TASK_LAST_CARGO_TAKEN, slotOf(player, lastCargoTask(player)) + 1)
        player.setVarbit(Varbits.PORT_TASKS_COMPLETED_TODAY, minOf(today(player), VARBIT_TODAY_MAX))
        player.setVarp(Varps.PORT_TASKS_COMPLETED, total(player))
    }

    private fun field(v: Long, shift: Int, bits: Int): Int = ((v ushr shift) and mask(bits)).toInt()

    private fun put(value: Int, shift: Int, bits: Int): Long = (value.toLong() and mask(bits)) shl shift
}
