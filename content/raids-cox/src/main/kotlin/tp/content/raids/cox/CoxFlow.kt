package tp.content.raids.cox

import tp.content.raids.PartyState
import tp.content.raids.RaidEvent
import tp.content.raids.RaidLayout
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.util.CoordGrid

/** A run's CoX state: its layout and which rooms are started and done. */
internal class CoxState(val plan: CoxLayoutPlan) {
    val started = BooleanArray(plan.cells.size)
    val done = BooleanArray(plan.cells.size)

    /** CX4: the leader has started the raid (the lobby passage); nobody else may join after this. */
    var begun = false
    var begunTick = -1L

    /** The deepest floor any member has reached (floors = the Olm floor). */
    var floorReached = 0

    /** Passages spawned where a template has none (OPEN-CX31): tile -> the cell side they stand on. */
    val spawnedPassages = HashMap<CoordGrid, Int>()

    /** The mystics' portals per cell: (start, end) arrival tiles (CoxRoomActions). */
    val mysticPortals = HashMap<Int, Pair<CoordGrid, CoordGrid>>()

    /** Combat rooms: per cell, the required NPCs still alive (CoxRooms). */
    val roomAlive = HashMap<Int, MutableSet<tp.plugin.NpcRef>>()

    /** Each room's blocker tile, while it stands (CoxRooms). */
    val blockers = HashMap<Int, CoordGrid>()

    /** Puzzle rooms' own state, per puzzle kind (CoxThieving, CoxTightrope, CoxIceDemonRoom, CoxCrabs). */
    val puzzles = HashMap<String, Any>()

    /** Team-only point changes (CX30's 5% case, CX32's Challenge Mode bonus). */
    var teamAdjust = 0.0

    /** Whether the Olm fight has been started (the barrier). */
    var olmStarted = false

    /** The tick the Great Olm died (the raid time ends there), or -1. */
    var olmDeadTick = -1L

    /** Floors whose completion line was sent. */
    val floorsAnnounced = BooleanArray(MAX_FLOORS + 1)

    private companion object {
        const val MAX_FLOORS = 3
    }
}

/** What a room needs from its content: started when the first player walks in; it calls [CoxFlow.roomDone]. */
internal fun interface CoxRoomScript {
    fun start(run: RaidRun, cell: CoxCell, any: EntityRef)
}

/**
 * The Chambers' traversal (cox.md CX4, CX8, CX34; client-contract 6). The cells are not joined by corridors in the
 * cache (OPEN-CX31), so a room's Passage (29789 "Enter") takes the player to the arrival tile of the next room, or back
 * to the previous one through the passage on the side it was entered by; a combat or puzzle room lets nobody on until
 * it is done. A floor's end hole goes down to the next floor's start cell, the rope there back up, and the last floor's
 * hole (29735) into the Great Olm's antechamber.
 */
public class CoxFlow : ContentPlugin {
    override fun ContentScope.register() {
        var world: tp.plugin.WorldRef? = null
        onWorldStart {
            world = this
            Raids.of(this).register(CoxRaid.DEFINITION)
        }
        onOpLoc(Locs.RAIDS_ENTRANCE_STEPS, OP1) { enterChambers(player) }
        onOpLoc(Locs.RAIDS_DOORWAY, OP1) { loc -> passage(player, loc) }
        for (hole in FLOOR_HOLES) onOpLoc(hole, OP1) { descend(player) }
        for (rope in FLOOR_ROPES) onOpLoc(rope, OP1) { ascend(player) }
        onOpLoc(Locs.RAIDS_BOSSENTRANCE, OP1) { toOlm(player) }
        onOpLoc(Locs.RAIDS_BOSSEXIT, OP1) { fromOlm(player) }
        onOpLoc(Locs.RAIDS_OLM_BARRIER, OP1) { loc -> passBarrier(player, loc) }
        // The lobby staircase (multi 49999 on varbit 5423: Climb for everyone, Reload for the leader); the client may send
        // the parent or the child it resolved.
        for (steps in intArrayOf(Locs.RAIDS_EXIT_STEPS_MULTI, Locs.RAIDS_EXIT_STEPS, Locs.RAIDS_EXIT_STEPS_RELOAD)) {
            onOpLoc(steps, OP1) {
                climb(player)
            }
        }
        for (steps in intArrayOf(Locs.RAIDS_EXIT_STEPS_MULTI, Locs.RAIDS_EXIT_STEPS_RELOAD)) onOpLoc(steps, OP2) { reload(player) }
        onWorldTick {
            CoxHud.tick(this)
            watchCells(this)
        }
        CoxRooms.register(this)
        CoxRoomActions.register(this)
        CoxOlmRoom.register(this)
        events.on<tp.plugin.DeathEvent> { e ->
            val npc = e.entity as? tp.plugin.NpcRef ?: return@on
            val id =
                tp.content.raids.RaidRun
                    .npcRun(npc) ?: return@on
            val run = Raids.of(npc).run(id)?.takeIf { it.raid.id == CoxRaid.ID } ?: return@on
            if (npc.type == tp.symbols.Npcs.OLM_HEAD) CoxOlmRoom.olmDied(run, npc) else CoxRooms.npcDied(run, npc, npc)
        }
        events.on<RaidEvent.Left> { e -> if (e.run.raid.id == CoxRaid.ID) world?.onlinePlayer(e.member)?.let(CoxHud::leave) }
    }

    /**
     * CX2-CX4 (OPEN-CX1 / OPEN-CX3): the leader's entry builds the instance and the layout; members of a started party
     * follow while it is still in the lobby. A player with no party enters as the leader of a new one.
     */
    private fun enterChambers(player: PlayerRef) {
        val raids = Raids.of(player)
        val party =
            raids.partyOf(player.username)
                ?: raids.createParty(requireNotNull(raids.definition(CoxRaid.ID)), player.username)
                ?: return
        if (party.raid.id != CoxRaid.ID) {
            player.message(OTHER_PARTY)
            return
        }
        when (val s = party.state) {
            is PartyState.Locked -> {
                val st = state(s.run)
                if (st?.begun == true) {
                    player.message(ALREADY_BEGUN)
                } else {
                    Raids.enter(player, s.run)?.let { player.message(it) }
                }
            }

            PartyState.Forming -> {
                if (!party.leader.equals(player.username, ignoreCase = true)) {
                    player.message(LEADER_FIRST)
                    return
                }
                val cm = party.settings[CM_SETTING] == 1
                Raids
                    .start(
                        player,
                        party,
                        mode = if (cm) CoxRaid.CHALLENGE else CoxRaid.NORMAL,
                        enterNow = false,
                    )?.let { player.message(it) }
            }

            else -> {
                return
            }
        }
    }

    /** The lobby steps: Climb leaves the raid (Mount Quidamortem). */
    private fun climb(player: PlayerRef) {
        val run = runOf(player) ?: return
        Raids.leave(player, run)
    }

    /**
     * CX5 (29 Nov 2023 / 27 Aug 2025): the leader's Reload before the start draws a new layout: everyone else is booted
     * (OPEN-CX4: to Mount Quidamortem, no message) and items left on the floor are lost; Challenge Mode cannot be reloaded.
     */
    private fun reload(player: PlayerRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        if (!run.party.leader.equals(player.username, ignoreCase = true) || st.begun) return
        if (run.scaling.mode == CoxRaid.CHALLENGE) {
            player.message(NO_CM_RELOAD)
            return
        }
        val party = run.party
        val mode = run.scaling.mode
        Raids.reset(player, run)
        Raids.start(player, party, mode = mode, enterNow = false)?.let { player.message(it) }
    }

    /** A room passage: back through the side the room was entered by, else on once the room is done. */
    private fun passage(player: PlayerRef, loc: LocRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        // A spawned passage can stand just past the cell edge: the player's own cell is the one being left.
        val cell = CoxMap.cellAt(st.plan, run, player.coord) ?: CoxMap.cellAt(st.plan, run, loc.coord) ?: return
        if (cell.kind == CoxKind.START && cell.floor == 0) {
            beginRaid(player, run, st)
            return
        }
        val side = passageSide(st, cell, loc)
        if (side == cell.entrySide) {
            val back = st.plan.previous(cell) ?: return
            if (back.floor != cell.floor) return
            if (back.kind == CoxKind.START && back.floor == 0) {
                player.teleport(lobbyArrival(run, player, back))
            } else {
                player.teleport(CoxMap.arrival(player, run, back, back.exitSide))
            }
            return
        }
        if (!st.done[cell.index]) {
            player.message(NOT_DONE)
            return
        }
        val next = st.plan.next(cell) ?: return
        if (next.floor != cell.floor) return
        player.teleport(CoxMap.arrival(player, run, next, next.entrySide))
        enterCell(run, st, next, player)
    }

    /** CX4: the leader starts the raid by walking on from the lobby; members who are not inside now are left out. */
    private fun beginRaid(player: PlayerRef, run: RaidRun, st: CoxState) {
        if (!st.begun) {
            if (!run.party.leader.equals(player.username, ignoreCase = true)) {
                player.message(LEADER_STARTS)
                return
            }
            st.begun = true
            st.begunTick = player.tick
            for (p in run.presentPlayers(player)) p.message(RAID_BEGUN)
            Raids.roomCleared(player, run)
            Raids.advance(player, run, CoxRaid.CHAMBERS)
        }
        val next = st.plan.next(st.plan.cells.first()) ?: return
        player.teleport(CoxMap.arrival(player, run, next, next.entrySide))
        enterCell(run, st, next, player)
    }

    /** The floor-end hole: down to the next floor's start cell (CX8), once the floor's rooms are done. */
    private fun descend(player: PlayerRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, player.coord) ?: return
        val next = st.plan.next(cell)?.takeIf { it.kind == CoxKind.START } ?: return
        floorComplete(run, st, player, cell.floor)
        player.teleport(CoxMap.freeNear(player, CoxMap.tile(run, next, ROPE_X, ROPE_Z)))
        st.floorReached = maxOf(st.floorReached, next.floor)
        enterCell(run, st, next, player)
    }

    /** The rope in a floor's start cell: back up to the floor above's end cell. */
    private fun ascend(player: PlayerRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, player.coord) ?: return
        val up = st.plan.previous(cell)?.takeIf { it.kind == CoxKind.END } ?: return
        player.teleport(CoxMap.arrival(player, run, up, up.entrySide))
    }

    /** The last floor's hole (29735): into the Great Olm's antechamber; the first one through moves the raid on. */
    private fun toOlm(player: PlayerRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        if ((run.state as? RunState.InRoom)?.room?.id == CoxRaid.CHAMBERS) {
            floorComplete(
                run,
                st,
                player,
                st.plan.cells
                    .last()
                    .floor,
            )
            Raids.roomCleared(player, run)
            Raids.advance(player, run, CoxRaid.OLM)
        }
        st.floorReached = st.plan.floors
        player.teleport(olmEntry(run, player))
    }

    /** The antechamber rope (29996): back up to the last floor's end cell. */
    private fun fromOlm(player: PlayerRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        val end = st.plan.cells.last()
        player.teleport(CoxMap.freeNear(player, CoxMap.tile(run, end, OLM_HOLE_X, OLM_HOLE_Z)))
    }

    /** The mystical barrier (29879 "Pass", 4 x 1, facing south): two tiles across. */
    private fun passBarrier(player: PlayerRef, loc: LocRef) {
        val from = player.coord
        val inward = from.z < loc.coord.z
        val to = CoordGrid(from.x, if (inward) loc.coord.z + 1 else loc.coord.z - 1, from.level)
        player.teleport(to)
        if (inward) runOf(player)?.let { CoxOlmRoom.entered(it, player) }
    }

    internal companion object {
        fun state(run: RaidRun): CoxState? = run.content as? CoxState

        fun runOf(player: PlayerRef): RaidRun? {
            val run = Raids.of(player).runOf(player.username) ?: return null
            return run.takeIf { it.raid.id == CoxRaid.ID && it.member(player.username)?.inside == true }
        }

        /** RaidDefinition.layout: draws the run's layout (CX11-CX13) and describes its instance. */
        fun build(run: RaidRun): RaidLayout {
            // CX15 / CX48: Challenge Mode always uses the fixed three-floor layout.
            val plan = if (run.scaling.mode == CoxRaid.CHALLENGE) CoxLayout.challenge() else CoxLayout.generate(run.rng)
            run.content = CoxState(plan)
            return CoxLayout.instance(plan)
        }

        /** Content registers a room's script here (combat rooms, puzzles); a room without one is done at once. */
        val scripts: MutableMap<CoxKind, CoxRoomScript> = HashMap()

        /** A member first seen inside a cell starts it, however they got there (walking, a respawn, a teleport). */
        fun watchCells(world: tp.plugin.WorldRef) {
            for (run in Raids.of(world).activeRuns) {
                if (run.raid.id != CoxRaid.ID) continue
                val st = state(run) ?: continue
                if (!st.begun || run.instance == null) continue
                for (m in run.members.values) {
                    if (!m.inside || !m.online) continue
                    val p = world.onlinePlayer(m.name) ?: continue
                    val cell = CoxMap.cellAt(st.plan, run, p.coord) ?: continue
                    if (!st.started[cell.index]) enterCell(run, st, cell, p)
                }
            }
        }

        /** The first player into a room starts it; rooms that hold the party back wait for [roomDone]. */
        fun enterCell(run: RaidRun, st: CoxState, cell: CoxCell, any: EntityRef) {
            st.floorReached = maxOf(st.floorReached, cell.floor)
            if (st.started[cell.index]) return
            st.started[cell.index] = true
            val script = scripts[cell.kind]
            if (script == null || !(cell.kind.combat || cell.kind.puzzle)) {
                st.done[cell.index] = true
                script?.start(run, cell, any)
            } else {
                script.start(run, cell, any)
            }
            run.markDirty()
        }

        /** A combat or puzzle room's condition is met: its passage lets the party on. */
        fun roomDone(run: RaidRun, cell: CoxCell) {
            val st = state(run) ?: return
            st.done[cell.index] = true
            run.markDirty()
        }

        /**
         * The side of [cell] a passage [loc] leads through: a spawned one's own side; a template doorway is the exit of its
         * room (client-contract 6.4), except in the floor-end cells, where it is the way back in.
         */
        fun passageSide(st: CoxState, cell: CoxCell, loc: LocRef): Int =
            st.spawnedPassages[loc.coord] ?: if (cell.kind == CoxKind.END) cell.entrySide else cell.exitSide

        /** The lobby's arrival: by the staircase (client-contract 6.4: THRU lobby steps multi at (2, 1)). */
        fun lobbyArrival(run: RaidRun, any: EntityRef, lobby: CoxCell): CoordGrid =
            CoxMap.freeNear(
                any,
                CoxMap.tile(
                    run,
                    lobby,
                    LOBBY_STEPS_X,
                    LOBBY_STEPS_Z + 1,
                ),
            )

        fun lobbyEntry(run: RaidRun, any: EntityRef): CoordGrid {
            val lobby = requireNotNull(state(run)).plan.cells.first()
            return lobbyArrival(run, any, lobby)
        }

        /** Room `lobby` started (the instance is ready): passages for the cells whose template has none (OPEN-CX31). */
        fun lobbyStarted(run: RaidRun, any: EntityRef) {
            val st = state(run) ?: return
            st.started[0] = true
            st.done[0] = true
            for (cell in st.plan.cells) {
                for (side in spawnedPassageSides(cell)) {
                    val (at, angle) = CoxMap.passageAt(run, cell, side, CoxMap.arrival(any, run, cell, side))
                    any.addLoc(Locs.RAIDS_DOORWAY, at, CENTREPIECE, angle)
                    st.spawnedPassages[at] = side
                }
            }
        }

        fun firstRoomEntry(run: RaidRun, any: EntityRef): CoordGrid {
            val plan = requireNotNull(state(run)).plan
            val first = plan.cells[1]
            return CoxMap.arrival(any, run, first, first.entrySide)
        }

        fun olmEntry(run: RaidRun, any: EntityRef): CoordGrid = olmTile(run, any, CoxRaid.OLM_ENTRY)

        fun olmTile(run: RaidRun, any: EntityRef, template: CoordGrid): CoordGrid = CoxMap.freeNear(any, CoxMap.olm(run, template))

        /**
         * CX34 (OPEN-CX17, Strat's reading): a death on floor 1 respawns at the start of floor 1; once a later floor is
         * reached, at the end of the floor above it. [CoxConfig.respawnAtPreviousFloorStart]: the start of that floor.
         */
        fun respawn(run: RaidRun, player: PlayerRef): CoordGrid? {
            val st = state(run) ?: return null
            val plan = st.plan
            val floor =
                if (CoxMap.inOlm(run, player.coord)) plan.floors else CoxMap.cellAt(plan, run, player.coord)?.floor ?: st.floorReached
            val target = (floor - 1).coerceAtLeast(0)
            val startOnly = floor == 0 || CoxConfig.respawnAtPreviousFloorStart
            val cell =
                if (startOnly) {
                    plan.cells.first { it.floor == target && it.kind == CoxKind.START }
                } else {
                    plan.cells.first { it.floor == target && it.kind == CoxKind.END }
                }
            return when {
                cell.kind == CoxKind.START && cell.floor == 0 -> lobbyArrival(run, player, cell)
                cell.kind == CoxKind.START -> CoxMap.freeNear(player, CoxMap.tile(run, cell, ROPE_X, ROPE_Z))
                else -> CoxMap.arrival(player, run, cell, cell.entrySide)
            }
        }

        /** The run's driver: HUD 513 is rebuilt every world tick by [CoxHud.tick] (a run alone reaches no player). */
        fun sync(
            @Suppress("UNUSED_PARAMETER") run: RaidRun,
        ) {
            // Intentionally empty: see the KDoc.
        }

        /**
         * CX53 (OPEN-CX29): the first member to leave a floor tells the party it is complete, with the time since the
         * start; the words follow the progress names (client-contract 2.2).
         */
        fun floorComplete(run: RaidRun, st: CoxState, any: EntityRef, floor: Int) {
            if (floor !in 0 until st.plan.floors || st.floorsAnnounced[floor]) return
            st.floorsAnnounced[floor] = true
            val word =
                when {
                    floor == 0 -> "Upper"
                    floor == st.plan.floors - 1 -> "Lower"
                    else -> "Middle"
                }
            val ticks = CoxHud.elapsed(run, st, any.tick).toLong()
            for (p in run.presentPlayers(any)) {
                p.message("<col=ef20ff>$word level complete!</col> Duration: <col=ff0000>${CoxRaid.formatTime(p, ticks)}</col>")
            }
        }

        /**
         * The sides of [cell] with no template doorway (client-contract 6.4: combat and puzzle cells carry one on the exit
         * side, the lobby on its exit, the floor ends on their entry; floor starts below the first, scavenger and resource
         * cells carry none).
         */
        fun spawnedPassageSides(cell: CoxCell): List<Int> =
            when {
                cell.kind == CoxKind.END -> emptyList()
                cell.kind == CoxKind.START -> if (cell.floor == 0) emptyList() else listOf(cell.exitSide)
                cell.kind.combat || cell.kind.puzzle -> listOf(cell.entrySide)
                else -> listOf(cell.entrySide, cell.exitSide)
            }.filter { it >= 0 }

        private const val OP1 = 1
        private const val OP2 = 2

        /** OPEN-CX4: the refusal line is not captured. */
        private const val NO_CM_RELOAD = "Challenge Mode layouts cannot be reloaded."
        private const val CENTREPIECE = 10

        /** Client-contract 6.4: the floor-start rope at (16, 15), the lobby steps at (2, 1), END3's hole at (11, 22). */
        private const val ROPE_X = 16
        private const val ROPE_Z = 14
        private const val LOBBY_STEPS_X = 2
        private const val LOBBY_STEPS_Z = 1
        private const val OLM_HOLE_X = 11
        private const val OLM_HOLE_Z = 21

        /** Party setting: Challenge Mode (the board's checkbox, CX3; varbit 6385). */
        const val CM_SETTING = "cox_cm"

        private val FLOOR_HOLES = intArrayOf(Locs.RAIDS_DESCENTTO1, Locs.RAIDS_DESCENTTO2)
        private val FLOOR_ROPES = intArrayOf(Locs.RAIDS_ASCENTTO2, Locs.RAIDS_ASCENTTO3)

        /** CX53 (RL constant). */
        const val RAID_BEGUN = "The raid has begun!"

        /** OPEN-CX3 / OPEN-CX31: these lines are not captured. */
        private const val LEADER_FIRST = "Your party leader must enter the Chambers first."
        private const val LEADER_STARTS = "Only the party leader can start the raid."
        private const val ALREADY_BEGUN = "Your party's raid has already begun."
        private const val OTHER_PARTY = "You are already in a party for a different raid."
        private const val NOT_DONE = "You need to complete this room before moving on."
    }
}
