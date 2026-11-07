package tp.content.sailing

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * The port masters (docs/mechanics/sailing-port-tasks.md PT8): "Claim-rewards" hands in the collected parts of bounty tasks
 * at any port, "Cancel-task" cancels any active task. Their "Talk-to" conversation is not transcribed (OPEN-PT9).
 */
internal class PortMaster(private val tasks: PortTasks) {
    fun ContentScope.register() {
        for (npc in PortSites.MASTERS) {
            onOpNpc(npc, OP_CLAIM) { claim(player) }
            onOpNpc(npc, OP_CANCEL) { cancelMenu(player) }
        }
    }

    /**
     * Completes every active bounty task whose parts [p] carries in the backpack. Parts in a cargo hold have to be taken out
     * first (OPEN-PT8: the Wiki only says the parts are "brought back to port").
     */
    private fun claim(p: PlayerRef) {
        var claimed = 0
        for (active in tasks.active(p)) {
            val task = active.task
            val part = task.bountyObj ?: continue
            val amount = task.bountyAmount ?: continue
            if (!task.isBounty || p.inv(Invs.INV).total(part) < amount) continue
            if (p.transaction("bounty claim") { remove(Invs.INV, part, amount) }) {
                tasks.complete(p, active.slot)
                claimed++
            }
        }
        if (claimed == 0) p.message("You have no completed tasks to claim rewards for.")
    }

    private fun cancelMenu(p: PlayerRef) {
        val options = tasks.active(p).filter { it.task.cancellable }
        if (options.isEmpty()) return p.message("You have no tasks you can cancel.")
        p.choiceDialog("Which task do you want to cancel?", options.map { it.task.name } + KEEP) { choice ->
            options.getOrNull(choice - 1)?.let { tasks.cancel(this, it.slot) }
        }
    }

    private companion object {
        /** Port master options (cache NPC 15459: Talk-to, -, Claim-rewards, Cancel-task). */
        const val OP_CLAIM = 3
        const val OP_CANCEL = 4
        const val KEEP = "Keep my tasks"
    }
}
