package tp.content.skills

import tp.plugin.PlayerRef
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * A player's Slayer state, kept in the cache variables the client reads (docs/mechanics/slayer.md SL1), so the rewards interface,
 * RuneLite's Slayer plugin and the Slayer helper show it without any extra transmit. Every varp is persisted with the player.
 * Rewards are bits of three varps (client script 8048: bits 0-31 in `slayer_rewards_unlocks`, 32-63 in `_unlocks1`, 64-95 in `_unlocks2`).
 */
internal object SlayerState {
    /** The stored task's persisted values: the Slayer_Task id, the kills left, the boss id and the master id. */
    const val STORED_ID = "slayer.stored.id"
    const val STORED_LEFT = "slayer.stored.left"
    const val STORED_BOSS = "slayer.stored.boss"
    const val STORED_MASTER = "slayer.stored.master"

    /** Quest points per block slot (Wiki "Slayer Rewards": "one block slot is given for every 50 quest points"). */
    private const val QP_PER_SLOT = 50

    /** Bits in one rewards varp. */
    private const val BITS_PER_VARP = 32

    /** What the `slayer_points` varbit holds: cache varbit 4068 is bits 6-22 of varp 661, 17 bits. */
    private const val POINTS_MAX = (1 shl 17) - 1

    /** Width of the `slayer_tasks_completed` varbits (cache varbits 4069 and 5617: bits 0-13 of their varp). */
    private const val STREAK_MAX = (1 shl 14) - 1

    private val UNLOCK_VARPS = intArrayOf(Varps.SLAYER_REWARDS_UNLOCKS, Varps.SLAYER_REWARDS_UNLOCKS1, Varps.SLAYER_REWARDS_UNLOCKS2)

    // ---- rewards

    /** Whether reward bit [bit] is bought (it is not set for bits beyond the three varps). */
    fun unlocked(p: PlayerRef, bit: Int): Boolean {
        val varp = UNLOCK_VARPS.getOrNull(bit / BITS_PER_VARP) ?: return false
        return (p.varp(varp) ushr (bit % BITS_PER_VARP)) and 1 == 1
    }

    fun setUnlocked(p: PlayerRef, bit: Int, on: Boolean) {
        val varp = UNLOCK_VARPS.getOrNull(bit / BITS_PER_VARP) ?: return
        val mask = 1 shl (bit % BITS_PER_VARP)
        p.setVarp(varp, if (on) p.varp(varp) or mask else p.varp(varp) and mask.inv())
    }

    // ---- points and streaks

    fun points(p: PlayerRef): Int = p.varbit(Varbits.SLAYER_POINTS)

    /** Stores [value] points, at most what the 17-bit `slayer_points` varbit holds. */
    fun setPoints(p: PlayerRef, value: Int) {
        p.setVarbit(Varbits.SLAYER_POINTS, value.coerceIn(0, POINTS_MAX))
    }

    /** The task streak of the Wilderness ([wild], Krystilia) or of every other master. */
    fun streak(
        p: PlayerRef,
        wild: Boolean,
    ): Int = p.varbit(if (wild) Varbits.SLAYER_WILDERNESS_TASKS_COMPLETED else Varbits.SLAYER_TASKS_COMPLETED)

    fun setStreak(p: PlayerRef, wild: Boolean, value: Int) {
        p.setVarbit(
            if (wild) Varbits.SLAYER_WILDERNESS_TASKS_COMPLETED else Varbits.SLAYER_TASKS_COMPLETED,
            value.coerceIn(0, STREAK_MAX),
        )
    }

    // ---- the current task

    /** Kills left: 0 or less with no task; -1 with [taskId] = boss while the amount is still to be chosen. */
    fun left(p: PlayerRef): Int = p.varp(Varps.SLAYER_COUNT)

    /** The Slayer_Task id of the task (0 = none), `SlayerCache.BOSS_TASK_ID` for a boss task. */
    fun taskId(p: PlayerRef): Int = p.varp(Varps.SLAYER_TARGET)

    fun bossId(p: PlayerRef): Int = p.varbit(Varbits.SLAYER_TARGET_BOSSID)

    /** The id of the master that gave the task (0 = none). */
    fun masterId(p: PlayerRef): Int = p.varbit(Varbits.SLAYER_MASTER)

    /** Whether a task is active or waits for its amount (a boss). */
    fun hasTask(p: PlayerRef): Boolean = taskId(p) > 0 && left(p) != 0

    fun set(p: PlayerRef, taskId: Int, left: Int, boss: Int, master: Int) {
        p.setVarp(Varps.SLAYER_TARGET, taskId)
        p.setVarp(Varps.SLAYER_COUNT, left)
        p.setVarbit(Varbits.SLAYER_TARGET_BOSSID, boss)
        p.setVarbit(Varbits.SLAYER_MASTER, master)
        if (taskId <= 0) p.setVarp(Varps.SLAYER_AREA, 0)
    }

    fun clear(p: PlayerRef) = set(p, 0, 0, 0, 0)

    // ---- block lists

    /**
     * Whether block slot [slot] (1-7) of a list is open to [p]: slots 1-6 need 50 quest points each (50, 100, ... 300), slot 7 the
     * Elite Lumbridge & Draynor diary reward (Wiki "Slayer Rewards", Tasks; client script 426 reads `qp` and varbit 4538).
     */
    fun slotOpen(p: PlayerRef, slot: Int): Boolean =
        if (slot == SlayerData.BLOCK_SLOTS) p.varbit(Varbits.LUMBRIDGE_ELITE_REWARD) != 0 else p.varp(Varps.QP) >= QP_PER_SLOT * slot

    /** The task ids blocked in [master]'s list (0 = empty slot), one entry per slot. */
    fun blocked(p: PlayerRef, master: SlayerMaster): IntArray = IntArray(SlayerData.BLOCK_SLOTS) { p.varbit(master.blockVarbits[it]) }

    fun isBlocked(p: PlayerRef, master: SlayerMaster, taskId: Int): Boolean = blocked(p, master).any { it == taskId }

    /** Puts [taskId] into the first open empty slot of [master]'s list; false if there is none. */
    fun block(p: PlayerRef, master: SlayerMaster, taskId: Int): Boolean {
        val list = blocked(p, master)
        val slot = (1..SlayerData.BLOCK_SLOTS).firstOrNull { list[it - 1] == 0 && slotOpen(p, it) } ?: return false
        p.setVarbit(master.blockVarbits[slot - 1], taskId)
        return true
    }

    // ---- the stored task

    fun hasStored(p: PlayerRef): Boolean = (p.saved(STORED_ID) ?: 0L) > 0L

    /** Copies the current task into the stored slot (Task Storage); the caller clears the current one. */
    fun store(p: PlayerRef) {
        p.setSaved(STORED_ID, taskId(p).toLong())
        p.setSaved(STORED_LEFT, left(p).toLong())
        p.setSaved(STORED_BOSS, bossId(p).toLong())
        p.setSaved(STORED_MASTER, masterId(p).toLong())
        p.setVarbit(Varbits.SLAYER_MASTER_STORED, masterId(p))
        p.setVarbit(Varbits.SLAYER_TARGET_BOSSID_STORED, bossId(p))
    }

    fun clearStored(p: PlayerRef) {
        p.setSaved(STORED_ID, null)
        p.setSaved(STORED_LEFT, null)
        p.setSaved(STORED_BOSS, null)
        p.setSaved(STORED_MASTER, null)
        p.setVarbit(Varbits.SLAYER_MASTER_STORED, 0)
        p.setVarbit(Varbits.SLAYER_TARGET_BOSSID_STORED, 0)
    }
}
