package tp.engine.script

import tp.engine.entity.Npc
import tp.engine.entity.Player

/** Player queue strengths (docs/mechanics/scripts.md Q1–Q9). */
enum class QueueType { WEAK, NORMAL, STRONG, SOFT }

/** Long-queue logout behaviour (Q8). */
enum class LogoutBehaviour { ACCELERATE, DISCARD }

internal class QueueEntry<E>(
    val type: QueueType,
    /** Earliest tick this entry may run. */
    var notBefore: Long,
    val logout: LogoutBehaviour?,
    val script: Script<E>,
) where E : tp.engine.entity.Entity

/**
 * The single player queue (Q1) with osrs-docs' processing loop (Q2–Q6). Pseudocode: osrs-docs Queues §"Pseudocode".
 */
class PlayerQueue internal constructor(private val player: Player) {
    private val entries = ArrayList<QueueEntry<Player>>()
    private var processing = false

    val size: Int get() = entries.size

    fun hasStrong(): Boolean = entries.any { it.type == QueueType.STRONG }

    fun add(type: QueueType, delayTicks: Int, logout: LogoutBehaviour? = null, script: Script<Player>) {
        // Q6/Q13: the delay counts the player's own turns; if its turn already started this tick (including queue
        // processing), the first one is next tick.
        val earliest = player.queueTick(delayTicks)
        entries += QueueEntry(type, earliest, logout, script)
    }

    /** Q7 / Q3: interruptions and modal closes remove every weak script. */
    fun clearWeak() {
        entries.removeIf { it.type == QueueType.WEAK }
    }

    /** Q8: on logout, accelerate long scripts and discard the rest of the long ones marked discard. */
    fun onLogout() {
        entries.removeIf { it.logout == LogoutBehaviour.DISCARD }
        for (e in entries) if (e.logout == LogoutBehaviour.ACCELERATE) e.notBefore = 0
    }

    /** The player turn's queue step (Q2–Q6). The strong-check modal close (Q3) runs before this, in the pipeline. */
    internal fun process() {
        processing = true
        try {
            while (entries.isNotEmpty()) {
                if (processPass() == 0) break
            }
        } finally {
            processing = false
        }
    }

    private fun processPass(): Int {
        var processed = 0
        val now = player.world.tick
        var i = 0
        while (i < entries.size) {
            val e = entries[i]
            if (e.type == QueueType.STRONG || e.type == QueueType.SOFT) player.closeModal()
            val canRun = (e.type == QueueType.SOFT || !player.isDelayed) && !player.hasModal && e.notBefore <= now
            if (canRun) {
                entries.removeAt(i)
                ScriptRunner.start(player, e.script, "queue ${e.type}")
                processed++
            } else {
                i++
            }
        }
        return processed
    }
}

/** NPCs: one queue, one strength (Q10, OPEN-S1: FIFO, skip future entries, not while delayed). */
class NpcQueue internal constructor(private val npc: Npc) {
    private val entries = ArrayList<QueueEntry<Npc>>()
    private var processing = false

    fun add(delayTicks: Int, script: Script<Npc>) {
        entries += QueueEntry(QueueType.NORMAL, npc.queueTick(delayTicks), null, script)
    }

    internal fun process() {
        if (npc.isDelayed) return
        processing = true
        try {
            val now = npc.world.tick
            var i = 0
            while (i < entries.size && !npc.isDelayed) {
                val e = entries[i]
                if (e.notBefore <= now) {
                    entries.removeAt(i)
                    ScriptRunner.start(npc, e.script, "queue")
                } else {
                    i++
                }
            }
        } finally {
            processing = false
        }
    }

    fun clear() {
        entries.clear()
    }
}

/** Area queue (Q11/Q12, OPEN-S2): FIFO, run when not delayed and no modal. */
class AreaQueue internal constructor(private val player: Player) {
    private val entries = ArrayDeque<Script<Player>>()

    val isEmpty: Boolean get() = entries.isEmpty()

    fun add(script: Script<Player>) {
        entries.addLast(script)
    }

    internal fun process() {
        while (entries.isNotEmpty() && !player.isDelayed && !player.hasModal) {
            ScriptRunner.start(player, entries.removeFirst(), "area queue")
        }
    }
}
