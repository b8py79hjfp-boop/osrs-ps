package tp.content.sailing

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.StatAdvanceEvent
import tp.plugin.Stats
import tp.symbols.Invs

/** What the Pandemonium quest needs to know about its two delivery tasks (docs/mechanics/sailing-pandemonium.md). */
internal interface QuestCargo {
    /** A crate of quest task [taskId] was picked up at the ledger table. */
    fun cargoTaken(p: PlayerRef, taskId: Int)

    /** The last crate of quest task [taskId] was delivered and the task completed. */
    fun cargoDelivered(p: PlayerRef, taskId: Int)
}

/**
 * Port task slots and their life cycle (docs/mechanics/sailing-port-tasks.md PT2-PT4, PT7, PT8): accepting at a notice
 * board, cancelling at a board, the captain's log or any port master, and completing. State lives in [PortTaskSave]; the
 * boards ([PortBoards]), ledger tables ([PortLedger]) and port masters ([PortMaster]) call in here.
 */
internal class PortTasks(private val state: SailingState) {
    /** Set by [Sailing]: the Pandemonium quest, which takes one of the two quest tasks. */
    var quest: QuestCargo? = null

    private val catalog: PortCatalog get() = state.catalog

    fun ContentScope.register() {
        onLogin { PortTaskSave.mirror(this) }
        events.on<StatAdvanceEvent> { if (it.stat == Stats.SAILING) PortTaskSave.mirror(it.player) }
    }

    /** The active tasks of [p] as (slot, task, state), in slot order. */
    fun active(p: PlayerRef): List<ActiveTask> =
        (0 until PortTaskSave.SLOTS).mapNotNull { slot ->
            val ts = PortTaskSave.get(p, slot) ?: return@mapNotNull null
            catalog.task(ts.taskId)?.let { ActiveTask(slot, it, ts) }
        }

    /** The slot of task id [taskId] in [p]'s slots, or -1. */
    fun slotOf(p: PlayerRef, taskId: Int): Int = PortTaskSave.slotOf(p, taskId)

    // ---- accepting

    /**
     * Accepts [task] into the first free unlocked slot of [p]. Needs the task's Sailing level (not boostable; bounty tasks
     * from level 30), a free slot and that the task is not already taken. False (with a message) when refused.
     */
    fun accept(p: PlayerRef, task: PortTask): Boolean {
        val level = p.baseLevel(Stats.SAILING)
        val needed = if (task.isBounty) maxOf(task.levelRequired, BOUNTY_LEVEL) else task.levelRequired
        val free = (0 until minOf(PortTaskSave.slotsAt(level), PortTaskSave.SLOTS)).firstOrNull { PortTaskSave.get(p, it) == null }
        val refusal =
            when {
                level < needed -> "You need a Sailing level of $needed to accept that task."
                PortTaskSave.slotOf(p, task.id) >= 0 -> "You have already accepted that task."
                free == null -> "You have no free port task slots."
                else -> null
            }
        if (refusal != null || free == null) {
            refusal?.let { p.message(it) }
            return false
        }
        PortTaskSave.set(p, free, TaskSlot(task.id, 0, 0))
        PortTaskSave.mirror(p)
        p.message("You accept the task: ${task.name}.")
        return true
    }

    /** Gives the quest task [taskId] (Pandemonium) to [p] if it has none yet; it bypasses the level and board rules. */
    fun assignQuest(p: PlayerRef, taskId: Int) {
        val task = catalog.task(taskId) ?: return
        if (PortTaskSave.slotOf(p, taskId) >= 0) return
        val free = (0 until PortTaskSave.SLOTS).firstOrNull { PortTaskSave.get(p, it) == null }
        if (free == null) return p.message("You have no free port task slot for: ${task.name}.")
        PortTaskSave.set(p, free, TaskSlot(task.id, 0, 0))
        PortTaskSave.mirror(p)
        p.message("You have a new port task: ${task.name}.")
    }

    // ---- cancelling

    /**
     * Cancels the task in [slot]. Tasks the cache marks not cancellable (the quest deliveries) stay. Crates of the task still
     * in the hands or the holds are taken away (OPEN-PT4); bounty parts already collected are kept (Wiki Bounty tasks).
     */
    fun cancel(p: PlayerRef, slot: Int): Boolean {
        val ts = PortTaskSave.get(p, slot) ?: return false
        val task = catalog.task(ts.taskId) ?: return false
        if (!task.cancellable) {
            p.message("You can't cancel that task.")
            return false
        }
        PortTaskSave.set(p, slot, null)
        if (task.isCourier) takeAwayCrates(p, task, ts.taken - ts.delivered)
        PortTaskSave.mirror(p)
        p.message("You cancel the task: ${task.name}.")
        return true
    }

    /** Removes up to [count] crates of [task]: the one in the hands first, then from the holds. */
    private fun takeAwayCrates(p: PlayerRef, task: PortTask, count: Int) {
        val crate = task.cargo.firstOrNull()?.obj ?: return
        var left = count
        if (Cargo.held(p) == crate && left > 0) {
            p.transaction("port task cancel") { removeAt(Invs.WORN, Cargo.WEAPON_SLOT, crate, 1) }
            Cargo.refresh(p)
            left--
        }
        for (boat in 0 until BoatSave.SLOTS) {
            if (left <= 0) break
            val inHold = p.inv(Cargo.holdInv(boat)).total(crate).toInt()
            val n = minOf(left, inHold)
            if (n > 0 && p.transaction("port task cancel") { remove(Cargo.holdInv(boat), crate, n) }) left -= n
        }
    }

    // ---- completing

    /** Completes the task in [slot]: removes it, counts it, pays the XP and the rewards (PT7), tells the quest. */
    fun complete(p: PlayerRef, slot: Int) {
        val ts = PortTaskSave.get(p, slot) ?: return
        val task = catalog.task(ts.taskId) ?: return
        PortTaskSave.set(p, slot, null)
        PortTaskSave.recordCompleted(p)
        PortTaskSave.mirror(p)
        val xp = catalog.xpOf(task)
        p.addXp(Stats.SAILING, xp * XP_MULTIPLIER)
        if (!catalog.isQuestTask(task)) PortRewards.grant(p, task, xp, state.ports)
        quest?.cargoDelivered(p, task.id)
    }

    /**
     * Hook for S10 (boat combat): [p] killed a monster of NPC type [npcType]. Rolls the bounty part of the first active bounty
     * task for that monster whose parts are not yet all collected, at the task's rarity 1/n (Wiki Bounty tasks: one roll per
     * kill, a second task for the same monster only progresses once the first is done). The part goes to the backpack
     * (OPEN-PT8: the part's drop location and jingle are the combat milestone's).
     */
    fun onBountyKill(p: PlayerRef, npcType: Int) {
        val task =
            active(p)
                .map { it.task }
                .firstOrNull { t ->
                    t.isBounty &&
                        (t.bountyTargetAlive == npcType || t.bountyTargetDead == npcType) &&
                        p.inv(Invs.INV).total(t.bountyObj ?: -1) < (t.bountyAmount ?: 0)
                } ?: return
        val part = task.bountyObj ?: return
        if (p.random(task.bountyRarity ?: 1) != 0) return
        p.transaction("bounty part") { addOrDrop(Invs.INV, part, 1) }
    }

    /** One occupied task slot. */
    class ActiveTask(val slot: Int, val task: PortTask, val state: TaskSlot)

    companion object {
        /** Sailing level at which bounty tasks unlock (Wiki Bounty tasks). */
        const val BOUNTY_LEVEL: Int = 30

        /**
         * Multiplier on port task XP. The Horizon's lure ale (+2.5%, Wiki Facilities) is a keg of the boat (S4/S11), so it is
         * 1.0 until kegs exist (OPEN-PT7).
         */
        const val XP_MULTIPLIER: Double = 1.0
    }
}
