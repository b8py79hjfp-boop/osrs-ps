package tp.content.raids.toa

import tp.content.raids.PartyState
import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.content.raids.WipePolicy
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs

/** Per-run ToA state (run.content). */
internal class ToaState(
    /** TA10: the raid level fixed at the start; at completion the time-limit penalty lowers it (TA12, [ToaLoot.prepare]). */
    var raidLevel: Int,
    val words: IntArray,
) {
    /** TA35-TA41: the points ledger and the figures of the summary. */
    val ledger = ToaLedger()

    /** TA25: per path (ToaPath order), 0..6. */
    val pathLevels = IntArray(ToaPath.entries.size)

    /** Rooms finished (puzzle and boss ids, `wardens`). */
    val done = LinkedHashSet<String>()

    /** The path being walked, or null in the Nexus. */
    var path: ToaPath? = null

    /** TA44: wipes so far, and the wipes the attempts invocation allows (0 = unlimited, TA11). */
    var wipes = 0
    var attempts = 0

    /** TA12: the tick the party first entered a path (the raid timer), -1 before. */
    var timerStart = -1L

    /** TA12: the time-limit invocation's limit in ticks and its level (-1 / 0 without one), and whether it was missed. */
    var limitTicks = -1L
    var limitLevel = 0
    var timePenalised = false

    /** The tick the current room started (room splits). */
    var roomStart = -1L

    /** WD1: the members who have spoken to Osmumten ("Begin"), and whether the Wardens' fight began. */
    val begun = HashSet<String>()
    var wardensStarted = false

    /** A room wiped with RetryRoom: ToaRooms restarts it once the party stands again. */
    var retryRoom: String? = null

    /** TA12: the tick of the Wardens' final blow (the raid timer's end), -1 before. */
    var endTick = -1L

    /** The active invocations' cache names (the fights read them through SharedAttrs.RAID_MODIFIERS). */
    var modifierNames: Set<String> = emptySet()

    /** The Helpful Spirit's visits, rolls and claims (TA51-TA52). */
    val supplies = ToaSupplyState()

    fun pathDone(p: ToaPath): Boolean = p.boss in done
}

/**
 * The Tombs of Amascut room flow (toa.md TA18-TA29, TA42-TA48; plan docs/plans/raids-toa.md): the lobby raid door,
 * the Nexus doors into any unfinished path, each puzzle's continue door into its boss, the boss room's exit back to the
 * Nexus once the boss is dead, the Wardens door once all four paths are done, and the burial chamber. Path levels
 * (Pathseeker / Pathfinder / Pathmaster at the start, Walk the Path after each path) and the attempts rule live here.
 */
public class ToaFlow : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart {
            world = this
            Raids.of(this).register(ToaRaid.DEFINITION)
        }
        onOpLoc(Locs.TOA_LOBBY_RAID_ENTRY, OP1) {
            // TR29: "the player will be warned of this prior to entering another raid" (OPEN-TR16: the wording).
            if (ToaConfig.unclaimedLootLostOnEntry && !ToaChestUi.isEmpty(player)) {
                if (choice(UNCLAIMED_WARNING, ENTER_ANYWAY, STAY) != 1) return@onOpLoc
            }
            enterRaid(player)
        }
        for ((door, path) in NEXUS_DOORS) {
            onOpLoc(door, OP1) { choosePath(player, path) }
            onOpLoc(door, OP2) { choosePath(player, path) }
        }
        onOpLoc(Locs.TOA_NEXUS_WARDENS_DOOR_OPEN, OP1) { toWardens(player) }
        onOpLoc(Locs.TOA_NEXUS_WARDENS_DOOR_OPEN, OP2) { toWardens(player) }
        for ((door, path) in CONTINUE_DOORS) {
            onOpLoc(door, OP1) { toBoss(player, path) }
            onOpLoc(door, OP2) { toBoss(player, path) }
        }
        for ((door, path) in BOSS_EXITS) onOpLoc(door, OP1) { leaveBossRoom(player, path) }
        onOpLoc(Locs.TOA_DOOR_EXIT, OP1) { leavePuzzle(player) }
        onOpLoc(Locs.TOA_PATH_BARRIER, OP1) { loc -> passBarrier(player, loc) }
        onOpLoc(Locs.TOA_PATH_BARRIER, OP2) { loc -> passBarrier(player, loc) }
        onOpLoc(Locs.TOA_TELEPORT_CRYSTAL_CONTINUE_WARDENS, OP1) { toTomb(player) }
        onWorldTick { ToaHud.tick(this) }
        events.on<RaidEvent.Left> { e -> if (e.run.raid.id == ToaRaid.ID) world?.onlinePlayer(e.member)?.let(ToaHud::leave) }
        events.on<RaidEvent.RoomFailed> { e -> if (e.run.raid.id == ToaRaid.ID) world?.let { roomFailed(it, e.run) } }
    }

    /**
     * TA1 / TA3 / OPEN-TA2: the leader's entry starts the raid with the party's invocations; members follow while the
     * party is in the Nexus. Without a party, a solo raid starts ([ToaConfig.soloWithoutParty]).
     */
    private fun enterRaid(player: PlayerRef) {
        val raids = Raids.of(player)
        val def = raids.definition(ToaRaid.ID) ?: return
        var party = raids.partyOf(player.username)?.takeIf { it.raid.id == ToaRaid.ID }
        if (party == null) {
            if (raids.partyOf(player.username) != null || !ToaConfig.soloWithoutParty) {
                player.message(NO_PARTY)
                return
            }
            party = raids.createParty(def, player.username) ?: return
        }
        when (val s = party.state) {
            is PartyState.Locked -> {
                Raids.enter(player, s.run)?.let { player.message(it) }
            }

            PartyState.Forming -> {
                if (!party.leader.equals(player.username, ignoreCase = true)) {
                    player.message(LEADER_FIRST)
                    return
                }
                val words = ToaInvocations.words(party)
                val level = ToaInvocations.raidLevel(player, words)
                party.settings[ToaScaling.LEVEL_SETTING] = level
                Raids.start(player, party, mode = ToaRaid.modeOf(level), enterNow = false)?.let { player.message(it) }
            }

            else -> {
                Unit
            }
        }
    }

    /** TA20: any unfinished path; the leader goes first (TA3), the others follow while the room has just started. */
    private fun choosePath(player: PlayerRef, path: ToaPath) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        val inNexus = (run.state as? RunState.InRoom)?.room?.id == ToaRaid.NEXUS || run.state is RunState.BetweenRooms
        if (st.pathDone(path)) {
            player.message(PATH_DONE)
            return
        }
        if (inNexus) {
            if (!run.party.leader.equals(player.username, ignoreCase = true)) {
                player.message(LEADER_FIRST)
                return
            }
            ToaSupplies.warnForfeit(player, run)
            st.path = path
            if (st.timerStart < 0) st.timerStart = player.tick
            Raids.enterRoom(player, run, path.puzzle)
            return
        }
        // A member who stayed behind follows the party into the path it is on.
        if (st.path == path) {
            val room = (run.state as? RunState.InRoom)?.room ?: return
            player.teleport(Raids.entryOf(run, room, player))
        }
    }

    /** The puzzle's continue door: into the boss once the puzzle is done (TA20). */
    private fun toBoss(player: PlayerRef, path: ToaPath) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        if (path.puzzle !in st.done) {
            player.message(PUZZLE_NOT_DONE)
            return
        }
        val inRoom = (run.state as? RunState.InRoom)?.room?.id
        if (inRoom == path.boss) {
            player.teleport(Raids.entryOf(run, requireNotNull(run.raid.rooms[path.boss]), player))
        } else {
            Raids.enterRoom(player, run, path.boss)
        }
    }

    /** OPEN-TA20 working assumption: a boss room's exit returns to the Nexus once its boss is dead. */
    private fun leaveBossRoom(player: PlayerRef, path: ToaPath) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        if (!st.pathDone(path)) {
            player.message(NOT_YET)
            return
        }
        if ((run.state as? RunState.InRoom)?.room?.id == ToaRaid.NEXUS) {
            player.teleport(Raids.entryOf(run, requireNotNull(run.raid.rooms[ToaRaid.NEXUS]), player))
        } else {
            Raids.enterRoom(player, run, ToaRaid.NEXUS)
        }
    }

    /**
     * The flame barrier 45135 ("Pass" / "Quick-Pass", puzzles.md: the challenge area's gate): crosses it along the room's
     * x axis. OPEN-PZ1 working assumption: the barrier on the continue door's side opens only once the puzzle is done.
     */
    private fun passBarrier(player: PlayerRef, loc: tp.plugin.LocRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        val path = st.path ?: return
        val dx = if (player.coord.x < loc.coord.x) 1 else -1
        val exitX = run.at(CONTINUE_TILES.getValue(path)).x
        val towardsExit = (exitX - loc.coord.x) * dx > 0
        val nearExit =
            kotlin.math.abs(exitX - loc.coord.x) < kotlin.math.abs(run.at(requireNotNull(ROOM_ENTRIES[path.puzzle])).x - loc.coord.x)
        if (towardsExit && nearExit && path.puzzle !in st.done) {
            player.message(PUZZLE_NOT_DONE)
            return
        }
        player.teleport(tp.util.CoordGrid(loc.coord.x + dx, player.coord.z, player.coord.level))
    }

    /** TA20: "a party cannot return to the Nexus until the path it is on is finished". */
    private fun leavePuzzle(player: PlayerRef) {
        if (runOf(player) != null) player.message(NOT_YET)
    }

    /** TA22: the Wardens door opens once all four paths are done. */
    private fun toWardens(player: PlayerRef) {
        val run = runOf(player) ?: return
        val st = state(run) ?: return
        if (ToaPath.entries.any { !st.pathDone(it) }) {
            player.message(WARDENS_LOCKED)
            return
        }
        // A member who stayed behind follows; entering the room again would restart the fight (a second boss spawn).
        if ((run.state as? RunState.InRoom)?.room?.id == ToaRaid.WARDENS) {
            player.teleport(Raids.entryOf(run, requireNotNull(run.raid.rooms[ToaRaid.WARDENS]), player))
            return
        }
        if (!run.party.leader.equals(player.username, ignoreCase = true)) {
            player.message(LEADER_FIRST)
            return
        }
        ToaSupplies.warnForfeit(player, run)
        st.path = null
        Raids.enterRoom(player, run, ToaRaid.WARDENS)
    }

    /** TA42 / TA44: a wipe in a room; the room's content restarts it (RetryRoom) unless the attempts ran out. */
    private fun roomFailed(world: WorldRef, run: RaidRun) {
        val st = state(run) ?: return
        if (run.state is RunState.Failed) return
        st.roomStart = world.tick
    }

    internal companion object {
        private const val OP1 = 1
        private const val OP2 = 2

        private val NEXUS_DOORS =
            mapOf(
                Locs.TOA_NEXUS_SCABARAS_DOOR to ToaPath.SCABARAS,
                Locs.TOA_NEXUS_HET_DOOR to ToaPath.HET,
                Locs.TOA_NEXUS_APMEKEN_DOOR to ToaPath.APMEKEN,
                Locs.TOA_NEXUS_CRONDIS_DOOR to ToaPath.CRONDIS,
                Locs.TOA_NEXUS_SCABARAS_DOOR_UNSELECTED to ToaPath.SCABARAS,
                Locs.TOA_NEXUS_HET_DOOR_UNSELECTED to ToaPath.HET,
                Locs.TOA_NEXUS_APMEKEN_DOOR_UNSELECTED to ToaPath.APMEKEN,
                Locs.TOA_NEXUS_CRONDIS_DOOR_UNSELECTED to ToaPath.CRONDIS,
            )

        /** The puzzle rooms' continue doors (client-contract 7.1). */
        private val CONTINUE_DOORS =
            mapOf(
                Locs.TOA_SCABARAS_CONTINUE to ToaPath.SCABARAS,
                Locs.TOA_DOOR_CONTINUE to ToaPath.HET,
                Locs.TOA_PATH_APMEKEN_CONTINUE to ToaPath.APMEKEN,
                Locs.TOA_PATH_CRONDIS_CONTINUE to ToaPath.CRONDIS,
            )

        /** The continue doors' template tiles (client-contract 6.2) and the puzzle rooms' arrival tiles. */
        private val CONTINUE_TILES =
            mapOf(
                ToaPath.SCABARAS to tp.util.CoordGrid(3582, 5279),
                ToaPath.HET to tp.util.CoordGrid(3659, 5278),
                ToaPath.APMEKEN to tp.util.CoordGrid(3825, 5279),
                ToaPath.CRONDIS to tp.util.CoordGrid(3916, 5279),
            )
        private val ROOM_ENTRIES =
            mapOf(
                "scabaras" to ToaRaid.SCABARAS_ENTRY,
                "het" to ToaRaid.HET_ENTRY,
                "apmeken" to ToaRaid.APMEKEN_ENTRY,
                "crondis" to ToaRaid.CRONDIS_ENTRY,
            )

        /** The boss rooms' exits (client-contract 6.2). */
        private val BOSS_EXITS =
            mapOf(
                Locs.TOA_ENTRANCE_KEPHRI_MAIN to ToaPath.SCABARAS,
                Locs.TOA_ENTRANCE_AKKHA01 to ToaPath.HET,
                Locs.TOA_ENTRANCE_BABA02 to ToaPath.APMEKEN,
                Locs.TOA_ZEBAK_EXIT to ToaPath.CRONDIS,
            )

        fun state(run: RaidRun): ToaState? = run.content as? ToaState

        fun runOf(player: PlayerRef): RaidRun? = Raids.of(player).runOf(player.username)?.takeIf { it.raid.id == ToaRaid.ID }

        fun pathLevel(run: RaidRun, path: ToaPath): Int = state(run)?.pathLevels?.get(path.ordinal) ?: 0

        /** The Nexus starts the run: the state, the starting path levels (TA25) and the entry message (TA64). */
        fun nexusStarted(run: RaidRun, any: EntityRef) {
            val st = state(run) ?: newState(run, any).also { run.content = it }
            st.path = null
            st.roomStart = any.tick
            if (run.roomsTaken.size == 1) {
                for (p in run.presentPlayers(any)) {
                    p.message(ENTER_MESSAGE)
                    // TA64: with a time-limit invocation the target time follows as its own message.
                    ToaRecords.targetTimeMessage(st, p)?.let(p::message)
                }
            }
        }

        private fun newState(run: RaidRun, any: EntityRef): ToaState {
            val words = ToaInvocations.words(run.scaling.settings)
            val st = ToaState(run.scaling.settings[ToaScaling.LEVEL_SETTING] ?: 0, words)
            val leader = run.presentPlayers(any).firstOrNull() ?: return st
            // TA25: Pathseeker / Pathfinder / Pathmaster add 1 / 2 / 3 to every path at the start.
            val initial = ToaInvocations.activeValue(leader, words, ToaInvocations.CAT_PATH_LEVEL) ?: 0
            st.pathLevels.fill(initial)
            st.modifierNames = ToaInvocations.active(leader, words).map { it.name }.toSet()
            // TA11: the attempts invocation's value (10 / 5 / 3 / 1, param 1299).
            st.attempts = ToaInvocations.activeValue(leader, words, ToaInvocations.CAT_ATTEMPTS) ?: 0
            ToaInvocations.active(leader, words).firstOrNull { it.category == ToaInvocations.CAT_TIME_LIMIT }?.let {
                st.limitTicks = it.value.toLong()
                st.limitLevel = it.level
            }
            return st
        }

        fun roomStarted(run: RaidRun, any: EntityRef, room: String) {
            val st = state(run) ?: return
            st.roomStart = any.tick
            ToaRooms.spawnBoss(any, run, room)
            ToaPuzzles.roomStarted(run, any, room)
            ToaPath.ofRoom(room)?.let { st.path = it }
            // TA64: the boss rooms announce the challenge (RL's `Challenge started: <boss>.`).
            // The Wardens' line comes when the fight begins (after Osmumten, WD1), from the fight itself.
            val boss = ToaPath.entries.firstOrNull { it.boss == room }?.bossName
            if (boss != null) for (p in run.presentPlayers(any)) p.message("Challenge started: $boss.")
        }

        /**
         * A room's content reports its room done (the puzzle solved, the boss dead): the split message, the room cleared in
         * raids-core, and after a boss the path is complete (Walk the Path, TA26).
         */
        fun roomDone(any: EntityRef, run: RaidRun, room: String) {
            val st = state(run) ?: return
            if (room in st.done) return
            st.done += room
            val now = any.tick
            val label =
                ToaPath.entries.firstOrNull { it.puzzle == room }?.pathName
                    ?: ToaPath.entries.firstOrNull { it.boss == room }?.bossName
                    ?: WARDENS_NAME
            val duration = Raids.formatTicks(now - st.roomStart)
            val total = Raids.formatTicks(now - maxOf(st.timerStart, run.startedTick))
            // OPEN-TA30: the colour tags and punctuation of the room lines (RL matches `Challenge complete: <room>.`).
            if (room != ToaRaid.WARDENS) {
                for (p in run.presentPlayers(any)) {
                    p.message("Challenge complete: $label. Duration: <col=ef1020>$duration</col>. Total: <col=ef1020>$total</col>")
                }
            }
            Raids.roomCleared(any, run)
            if (room == ToaRaid.WARDENS) {
                // TA12 / TA22: the time ends at the final blow (the run completes when the burial chamber starts, as in
                // ToB); the white teleport crystal leads there (OPEN-TA33: where it appears; assumed the arrival tile).
                st.endTick = now
                // WD46: the white crystal appears where the Warden stood: the middle of its footprint (the fight reports the
                // dying NPC, whose coord is its south-west tile).
                val at = (any as? tp.plugin.NpcRef)?.let { n -> n.coord.translate(n.size / 2, n.size / 2) } ?: run.at(ToaRaid.WARDENS_ENTRY)
                any.addLoc(Locs.TOA_TELEPORT_CRYSTAL_CONTINUE_WARDENS, at, CENTREPIECE, 0)
                return
            }
            val path = ToaPath.entries.firstOrNull { it.boss == room } ?: return
            pathCompleted(any, run, st, path)
            if (ToaPath.entries.all { st.pathDone(it) }) openWardensDoor(any, run)
        }

        /**
         * TA20: the centre door "will unlock" once all four paths are done: the static door 46167 (no ops, (3548, 5134))
         * becomes 46168 "Entry" (ops Enter / Quick-Enter) in place.
         */
        private fun openWardensDoor(any: EntityRef, run: RaidRun) {
            val at = run.at(WARDENS_DOOR)
            val door = any.locAt(at, CENTREPIECE) ?: return
            any.addLoc(Locs.TOA_NEXUS_WARDENS_DOOR_OPEN, door.coord, door.shape, door.angle)
        }

        /** The Wardens door's tile in the Nexus template (cache map: loc 46167). */
        private val WARDENS_DOOR = tp.util.CoordGrid(3548, 5134)

        /** The Wardens' white crystal: into Osmumten's burial chamber (TA22). */
        fun toTomb(player: PlayerRef) {
            val run = runOf(player) ?: return
            if (ToaRaid.WARDENS !in (state(run)?.done ?: return)) return
            // Once the raid is complete the crystal only moves a latecomer: entering the room again would reopen the run.
            if ((run.state as? RunState.InRoom)?.room?.id == ToaRaid.TOMB || run.state == RunState.Completed) {
                player.teleport(Raids.entryOf(run, requireNotNull(run.raid.rooms[ToaRaid.TOMB]), player))
            } else {
                Raids.enterRoom(player, run, ToaRaid.TOMB)
            }
        }

        /** Loc shape 10 (centrepiece). */
        private const val CENTREPIECE = 10

        /** TA26: Walk the Path levels up 2 / 1 / 1 of the remaining paths after the 1st / 2nd / 3rd path. */
        private fun pathCompleted(any: EntityRef, run: RaidRun, st: ToaState, path: ToaPath) {
            val leader = run.presentPlayers(any).firstOrNull() ?: return
            if (!ToaInvocations.hasNamed(leader, st.words, ToaInvocations.WALK_THE_PATH)) return
            val remaining = ToaPath.entries.filter { it != path && !st.pathDone(it) }.toMutableList()
            val count =
                when (ToaPath.entries.count { st.pathDone(it) }) {
                    1 -> 2
                    2, 3 -> 1
                    else -> 0
                }
            repeat(minOf(count, remaining.size)) {
                val pick = remaining.removeAt(run.rng.next(remaining.size, "toa walk the path"))
                st.pathLevels[pick.ordinal] = (st.pathLevels[pick.ordinal] + 1).coerceAtMost(MAX_PATH_LEVEL)
                for (p in run.presentPlayers(any)) p.message("You hear a mysterious rumbling coming from the ${pick.pathName}.")
            }
            run.markDirty()
        }

        /** TA42-TA44: without an attempts invocation every wipe retries the room; with one, the N-th wipe fails the raid. */
        fun wipeRule(run: RaidRun): WipePolicy {
            val st = state(run) ?: return WipePolicy.RetryRoom
            if (st.attempts <= 0) return WipePolicy.RetryRoom
            st.wipes++
            return if (st.wipes >= st.attempts) WipePolicy.FailRaid(storeInv = Invs.TOA_RECLAIM) else WipePolicy.RetryRoom
        }

        /**
         * The burial chamber: the run is complete (TA22). Once only: the Wardens block of the chat (TA62), the final raid
         * level and the reward inputs (TR28) go first, then raids-core rolls and pays the rewards and adds the total time
         * and the kill count; the chests and the summary follow.
         */
        fun tombStarted(run: RaidRun, any: EntityRef) {
            val st = state(run) ?: return
            st.path = null
            if (st.ledger.completed) return
            ToaRecords.wardensBlock(run, any)
            ToaLoot.prepare(run, any, st)
            Raids.complete(any, run, endedAt = st.endTick.takeIf { it >= 0 } ?: any.tick)
            ToaTomb.completed(run, any)
        }

        private const val MAX_PATH_LEVEL = 6
        private const val WARDENS_NAME = "The Wardens"

        /** TA64: RL-TOA matches this prefix; whatever follows it in game is OPEN-TA30. */
        const val ENTER_MESSAGE = "You enter the Tombs of Amascut"

        // OPEN-TA30: texts not captured; placeholders until seen in game.
        private const val NO_PARTY = "You are already in a party for another raid."

        /** OPEN-TR16: the warning before entering with unclaimed rewards is not captured. */
        private const val UNCLAIMED_WARNING = "Unclaimed rewards will be lost if you enter."
        private const val ENTER_ANYWAY = "Enter the tomb anyway."
        private const val STAY = "Stay."
        private const val LEADER_FIRST = "Your party leader must enter first."
        private const val PATH_DONE = "You have already completed this path."
        private const val PUZZLE_NOT_DONE = "You must complete the challenge before you can proceed."
        private const val NOT_YET = "You cannot leave until the path is complete."
        private const val WARDENS_LOCKED = "You must complete all four paths before facing the Wardens."
    }
}
