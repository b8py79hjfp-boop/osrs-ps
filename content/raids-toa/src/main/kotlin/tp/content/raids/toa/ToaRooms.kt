package tp.content.raids.toa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.RaidRoomFrame
import tp.plugin.SharedAttrs
import tp.plugin.WorldRef
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * ToA rooms (plan raids-toa.md): each room is one unrotated map square, so its frame is that square; NPCs spawned for a
 * room carry the frame (`SharedAttrs.RAID_ROOM`, the fights find their raiders with it). The boss rooms spawn their boss
 * when they start and are done when it dies (the fights are content/combat's `Toa*`); a wipe with RetryRoom (TA42)
 * clears the room's NPCs once the party stands again and starts the room afresh, for puzzles and bosses alike.
 */
public class ToaRooms : ContentPlugin {
    override fun ContentScope.register() {
        events.on<RaidEvent.RoomFailed> { e ->
            if (e.run.raid.id == ToaRaid.ID && e.run.state is RunState.InRoom) ToaFlow.state(e.run)?.retryRoom = e.room.id
        }
        events.on<DeathEvent> { e -> (e.entity as? NpcRef)?.let { died(it) } }
        onWorldTick { retry(this) }
        // WD1: every member speaks to Osmumten ("Begin", op3) before the Wardens' fight starts.
        onOpNpc(Npcs.TOA_OSMUMTEN_WARDENS_VIS, OP_BEGIN) { npc -> begin(player, npc) }
    }

    /** A boss room's boss: its type at the room start, the template spawn tile, and the types whose death ends the room. */
    internal class BossSpawn(val type: Int, val at: CoordGrid, val kills: Set<Int>)

    internal companion object {
        /** The room squares (client-contract 6.1). */
        private val SQUARES =
            mapOf(
                ToaRaid.NEXUS to (55 to 80),
                ToaRaid.TOMB to (57 to 80),
                ToaRaid.WARDENS to (59 to 80),
                "scabaras" to (55 to 82),
                "het" to (57 to 82),
                "apmeken" to (59 to 82),
                "crondis" to (61 to 82),
                "kephri" to (55 to 84),
                "akkha" to (57 to 84),
                "baba" to (59 to 84),
                "zebak" to (61 to 84),
            )

        /** Wardens phase 3 is the next square east of the phase 1 / 2 square (client-contract 6.1). */
        private val WARDENS_P3 = 61 to 80

        private const val SQUARE = 64

        /**
         * Spawn tiles: Zebak and Kephri from the wiki map pins (centres (3921, 5408) and (3551, 5409), sizes 9 and 5);
         * Ba-Ba and Akkha are not pinned (OPEN-BB2 / OPEN-AK1: the arena middle); the Wardens' obelisk phase is the
         * Wardens fight's own (ToaWardens spawns its parts from the obelisk NPC).
         */
        val BOSSES: Map<String, BossSpawn> =
            mapOf(
                "baba" to
                    BossSpawn(
                        Npcs.TOA_BABA,
                        CoordGrid(3810, 5405),
                        setOf(Npcs.TOA_BABA, Npcs.TOA_BABA_COFFIN, Npcs.TOA_BABA_DIGGING),
                    ),
                "zebak" to BossSpawn(Npcs.TOA_ZEBAK, CoordGrid(3917, 5404), setOf(Npcs.TOA_ZEBAK, Npcs.TOA_ZEBAK_ENRAGED)),
                "akkha" to BossSpawn(Npcs.AKKHA_SPAWN, CoordGrid(3679, 5406, 1), ToaNpcs.AKKHA),
                "kephri" to BossSpawn(Npcs.TOA_KEPHRI_BOSS_SHIELDED, CoordGrid(3549, 5407), ToaNpcs.KEPHRI),
                ToaRaid.WARDENS to BossSpawn(Npcs.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE, CoordGrid(3807, 5153, 1), ToaNpcs.P3_WARDENS),
            )

        fun frame(run: RaidRun, room: String): RaidRoomFrame? = square(room)?.let { frameOf(run, it) }

        private fun frameOf(run: RaidRun, sq: Pair<Int, Int>): RaidRoomFrame {
            val template = CoordGrid(sq.first * SQUARE, sq.second * SQUARE)
            return RaidRoomFrame(run.at(template), 0, SQUARE, template)
        }

        private fun square(room: String): Pair<Int, Int>? = SQUARES[room]

        /** [Raids.spawn] for [room], with the room's frame on the NPC. */
        fun spawn(any: EntityRef, run: RaidRun, room: String, type: Int, template: CoordGrid): NpcRef {
            val npc = Raids.spawn(any, run, type, template)
            frame(run, room)?.let { npc[SharedAttrs.RAID_ROOM] = it }
            return npc
        }

        /** The Wardens' phase 3 square's frame (the fight moves there). */
        fun wardensP3Frame(run: RaidRun): RaidRoomFrame = frameOf(run, WARDENS_P3)

        /** A boss room started (ToaFlow.roomStarted): its boss appears (the fights take it from there). */
        fun spawnBoss(any: EntityRef, run: RaidRun, room: String) {
            val b = BOSSES[room] ?: return
            spawn(any, run, room, b.type, b.at)
            if (room == ToaRaid.WARDENS) {
                ToaFlow.state(run)?.let {
                    it.begun.clear()
                    it.wardensStarted = false
                }
                spawn(any, run, room, Npcs.TOA_OSMUMTEN_WARDENS_VIS, OSMUMTEN_WARDENS).frozenUntil = Long.MAX_VALUE
            }
        }

        /** OPEN-WD14: Osmumten stands by the Wardens' entry corridor. */
        private val OSMUMTEN_WARDENS = CoordGrid(3809, 5173, 1)
        private const val OP_BEGIN = 3

        /**
         * WD1: "Begin" marks the speaker; once everyone inside has, the obelisk wakes (11750 -> the active 11751, whose fight
         * script spawns the charging Wardens).
         */
        private fun begin(player: tp.plugin.PlayerRef, osmumten: NpcRef) {
            val run = ToaFlow.runOf(player) ?: return
            val st = ToaFlow.state(run) ?: return
            if ((run.state as? RunState.InRoom)?.room?.id != ToaRaid.WARDENS || st.wardensStarted) return
            st.begun += player.username
            val present = run.presentPlayers(player)
            if (present.any { it.username !in st.begun }) {
                player.message(WAITING)
                return
            }
            st.wardensStarted = true
            val b = BOSSES.getValue(ToaRaid.WARDENS)
            for (n in player.npcsWithin(run.at(b.at), SQUARE / 2)) {
                if (n.type == Npcs.TOA_WARDENS_P1_OBELISK_NPC_INACTIVE && n[SharedAttrs.RAID_RUN] == run.id.value) n.despawn()
            }
            spawn(player, run, ToaRaid.WARDENS, Npcs.TOA_WARDENS_P1_OBELISK_NPC, b.at)
            osmumten.despawn()
        }

        /** OPEN-TA30: not captured. */
        private const val WAITING = "Osmumten waits for the rest of your party."

        /** A boss room's boss died: the room is done (TA20). */
        private fun died(npc: NpcRef) {
            val runId = npc[SharedAttrs.RAID_RUN] ?: return
            val run = Raids.of(npc).run(runId)?.takeIf { it.raid.id == ToaRaid.ID } ?: return
            val room = (run.state as? RunState.InRoom)?.room?.id ?: return
            val b = BOSSES[room] ?: return
            if (npc.type in b.kills) ToaFlow.roomDone(npc, run, room)
        }

        /** OPEN-TA20 (as ToB's Entry retry): once every member stands again, the room's NPCs go and it starts afresh. */
        private fun retry(world: WorldRef) {
            for (run in Raids.of(world).activeRuns) {
                if (run.raid.id != ToaRaid.ID) continue
                val st = ToaFlow.state(run) ?: continue
                val room = st.retryRoom ?: continue
                val present =
                    run.members.values
                        .filter { it.inside && it.online }
                        .mapNotNull { world.onlinePlayer(it.name) }
                val any = present.firstOrNull() ?: continue
                if (present.any { it.hitpoints <= 0 }) continue
                st.retryRoom = null
                clear(any, run, room)
                if (room == ToaRaid.WARDENS) clearSquare(any, run, WARDENS_P3)
                Raids.enterRoom(any, run, room)
            }
        }

        private fun clear(any: EntityRef, run: RaidRun, room: String) {
            clearSquare(any, run, square(room) ?: return)
        }

        private fun clearSquare(any: EntityRef, run: RaidRun, sq: Pair<Int, Int>) {
            val f = frameOf(run, sq)
            val centre = CoordGrid(f.origin.x + SQUARE / 2, f.origin.z + SQUARE / 2)
            for (level in 0..MAX_LEVEL) {
                for (n in any.npcsWithin(CoordGrid(centre.x, centre.z, level), SQUARE / 2)) {
                    if (n[SharedAttrs.RAID_RUN] == run.id.value && n.coord in f) n.despawn()
                }
            }
        }

        private const val MAX_LEVEL = 3
    }
}
