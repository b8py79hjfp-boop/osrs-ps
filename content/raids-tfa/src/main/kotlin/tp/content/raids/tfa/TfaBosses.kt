package tp.content.raids.tfa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Starts and ends TFA's boss rooms: the fights themselves are boss scripts in content/combat; this module spawns each
 * room's NPCs when the room starts, wakes the Archive Guardian ("Touch"), and ends the room once every NPC the room
 * needs is dead.
 */
public class TfaBosses : ContentPlugin {
    override fun ContentScope.register() {
        // AG0: touching the Inert Guardian wakes the Archive Guardian (the party leader should start the fight).
        onOpNpc(Npcs.TFA_GUARDIAN_START, 1) { inert ->
            val run = runOf(inert) ?: return@onOpNpc
            if ((run.state as? RunState.InRoom)?.room?.id != "guardian") return@onOpNpc
            if (inert[WOKEN] == true) return@onOpNpc
            inert[WOKEN] = true
            inert.anim(tp.symbols.Seqs.NPC_ARCHIVE_GUARDIAN_SPAWN01)
            // RuneLite (blert GuardianDataTracker): it stays the inert NPC for 5 ticks after the start animation.
            inert.queueHit(WAKE_TICKS) {
                inert.transform(Npcs.TFA_GUARDIAN, restoreStats = true)
                inert.huntRange = ARENA_HUNT
            }
        }
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            val run = runOf(npc) ?: return@on
            val roomId = (run.state as? RunState.InRoom)?.room?.id ?: return@on
            val room = ROOMS[roomId] ?: return@on
            if (npc.type !in room.required) return@on
            val inst = run.instance ?: return@on
            val others =
                npc.npcsWithin(npc.coord, ROOM_SCAN).any {
                    it != npc && it.type in room.required && it.hitpoints > 0 &&
                        it.coord in inst
                }
            if (!others) TfaFlow.cleared(npc, run)
        }
    }

    private fun runOf(npc: NpcRef): RaidRun? {
        val id = RaidRun.npcRun(npc) ?: return null
        return Raids.of(npc).run(id)?.takeIf { it.raid.id == TfaRaid.ID }
    }

    /** A room's NPCs (type at template tile) and the types that must all be dead for it to end. */
    private class Room(val spawns: List<Pair<Int, CoordGrid>>, val required: Set<Int>)

    internal companion object {
        /** Spawns [roomId]'s NPCs for [run] (RoomDef.onStart). */
        fun start(run: RaidRun, any: EntityRef, roomId: String) {
            for ((type, at) in ROOMS[roomId]?.spawns ?: return) Raids.spawn(any, run, type, at)
        }

        /**
         * Room NPCs. Spawn tiles are free tiles near each room's centre (collision; OPEN-NX2: the stripped room table
         * 259 has the real ones). The Guardian's room spawns its Inert Guardian from TfaFlow.
         */
        private val ROOMS: Map<String, Room> =
            mapOf(
                "guardian" to Room(emptyList(), setOf(Npcs.TFA_GUARDIAN, Npcs.TFA_GUARDIAN_UNSHIELDED)),
                "ezekiel" to Room(listOf(Npcs.TFA_EZEKIEL to CoordGrid(4060, 5533)), setOf(Npcs.TFA_EZEKIEL)),
                "bork" to
                    Room(
                        listOf(Npcs.TFA_BORK to CoordGrid(4189, 5599), Npcs.TFA_SMORK to CoordGrid(4195, 5604)),
                        setOf(Npcs.TFA_BORK, Npcs.TFA_SMORK),
                    ),
                "moia" to Room(listOf(Npcs.TFA_MOIA to CoordGrid(4060, 5660)), setOf(Npcs.TFA_MOIA)),
                "dreadnoth" to Room(listOf(Npcs.TFA_JELLY_BOSS to CoordGrid(4190, 5662)), setOf(Npcs.TFA_JELLY_BOSS)),
                "dagonhai" to
                    Room(
                        listOf(
                            Npcs.TFA_DAGONHAI_LEADER to CoordGrid(4060, 5598),
                            Npcs.TFA_DAGONHAI_MINION_1 to CoordGrid(4056, 5594),
                            Npcs.TFA_DAGONHAI_MINION_2 to CoordGrid(4064, 5594),
                        ),
                        setOf(
                            Npcs.TFA_DAGONHAI_LEADER,
                            Npcs.TFA_DAGONHAI_LEADER_UNSHIELDED,
                            Npcs.TFA_DAGONHAI_MINION_1,
                            Npcs.TFA_DAGONHAI_MINION_1_UNSHIELDED,
                            Npcs.TFA_DAGONHAI_MINION_2,
                            Npcs.TFA_DAGONHAI_MINION_2_UNSHIELDED,
                        ),
                    ),
                "xarth" to Room(listOf(Npcs.TFA_XARTH to CoordGrid(4192, 5535)), setOf(Npcs.TFA_XARTH)),
                "ocellus" to Room(listOf(Npcs.TFA_OCELLUS_P1 to CoordGrid(4126, 5672, 1)), setOf(Npcs.TFA_OCELLUS_P3)),
            )

        /** The nexus arena: the Guardian hunts anyone in it. */
        const val ARENA_HUNT = 16

        /** blert-io/plugin GuardianDataTracker.java:39-50: 5 ticks from the start animation to the fighting form. */
        const val WAKE_TICKS = 5
        val WOKEN = tp.plugin.AttrKey<Boolean>("tfa_guardian_woken")

        /** A side room fits within this distance of any of its NPCs. */
        const val ROOM_SCAN = 40
    }
}
