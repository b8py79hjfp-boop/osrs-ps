package tp.content.raids.tfa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.symbols.Npcs

/**
 * Starts and ends TFA's boss rooms: the fights themselves are boss scripts in content/combat; this module wakes them
 * (the Inert Guardian's "Touch") and ends the room when the room's boss dies.
 */
public class TfaBosses : ContentPlugin {
    override fun ContentScope.register() {
        // AG0: touching the Inert Guardian wakes the Archive Guardian (the party leader should start the fight).
        onOpNpc(Npcs.TFA_GUARDIAN_START, 1) { inert ->
            val run = runOf(inert) ?: return@onOpNpc
            if ((run.state as? RunState.InRoom)?.room?.id != "guardian") return@onOpNpc
            inert.anim(tp.symbols.Seqs.NPC_ARCHIVE_GUARDIAN_SPAWN01)
            inert.transform(Npcs.TFA_GUARDIAN, restoreStats = true)
            inert.huntRange = ARENA_HUNT
        }
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            val room = ROOM_BOSSES[npc.type] ?: return@on
            val run = runOf(npc) ?: return@on
            if ((run.state as? RunState.InRoom)?.room?.id == room) TfaFlow.cleared(npc, run)
        }
    }

    private fun runOf(npc: NpcRef): RaidRun? {
        val id = RaidRun.npcRun(npc) ?: return null
        return Raids.of(npc).run(id)?.takeIf { it.raid.id == TfaRaid.ID }
    }

    private companion object {
        /** The NPC whose death ends each room (every type a boss can die as). */
        val ROOM_BOSSES: Map<Int, String> =
            mapOf(
                Npcs.TFA_GUARDIAN to "guardian",
                Npcs.TFA_GUARDIAN_UNSHIELDED to "guardian",
            )

        /** The nexus arena: the Guardian hunts anyone in it. */
        const val ARENA_HUNT = 16
    }
}
