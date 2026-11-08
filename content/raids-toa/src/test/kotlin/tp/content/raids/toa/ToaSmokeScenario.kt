package tp.content.raids.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.DamageRequest
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.protocol.api.ClientCommand
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.tools.scenario.Scenario
import tp.tools.scenario.ScenarioPlayer
import tp.util.CoordGrid

/**
 * Raids brief section 9 (test 3), ToA smoke: a solo player sets invocations at the party, enters from the lobby, walks
 * the four paths in a chosen order through the real door ops (Nexus door -> puzzle -> continue door -> boss -> exit ->
 * Nexus), takes the Wardens door once it opens and the white crystal to the burial chamber. The rooms' own content
 * are fought with stubbed raider damage (the fights' own phases run); see [solvePuzzle] for the puzzles. Checks the HUD
 * contract on the way.
 */
class ToaSmokeScenario {
    private lateinit var s: Scenario
    private lateinit var a: ScenarioPlayer
    private val ra get() = PlayerRef.of(a.player)

    @Test
    fun `a solo raider walks every path and reaches the burial chamber`() {
        s = Scenario(seed = SEED)
        a = s.player("a", ToaRaid.LOBBY)
        s.tick()
        val raids = Raids.of(ra)
        val party = requireNotNull(raids.createParty(requireNotNull(raids.definition(ToaRaid.ID)), "a"))
        // TI12 Walk the Path (+50) and TI13 Pathseeker (+15): raid level 65, every path starts at level 1.
        var words = ToaInvocations.words(party)
        for (key in intArrayOf(WALK_THE_PATH_KEY, PATHSEEKER_KEY)) words = ToaInvocations.toggle(ra, words, key)
        ToaInvocations.setWords(party, words)
        assertEquals(LEVEL, ToaInvocations.raidLevel(ra, words), "TA10 raid level")
        op(Locs.TOA_LOBBY_RAID_ENTRY, LOBBY_DOOR)
        val run = requireNotNull(Raids.of(ra).runOf("a")) { "no run: ${a.messages}" }
        assertEquals(ToaRaid.NEXUS, room(run))
        assertEquals(ToaRaid.ENTRY, run.scaling.mode, "TA18: level 65 is Entry")
        assertTrue(ra.isOpen(Interfaces.TOA_HUD), "HUD 481 open in the raid")
        assertEquals(LEVEL, ra.varbit(Varbits.TOA_CLIENT_RAID_LEVEL))
        assertTrue(a.messages.any { it.startsWith(ToaFlow.ENTER_MESSAGE) }, "TA64 entry message")
        val st = requireNotNull(ToaFlow.state(run))
        for (p in ToaPath.entries) assertEquals(1, st.pathLevels[p.ordinal], "TA25 Pathseeker: $p at level 1")
        assertTrue(ra.varbit(Varbits.TOA_CLIENT_P0) in 1..ORB_FULL, "own orb alive")
        for ((path, doors) in ORDER) {
            val (nexusDoor, continueDoor, exitDoor) = doors
            op(nexusDoor.first, nexusDoor.second)
            assertEquals(path.puzzle, room(run), "$path: in the puzzle; ${a.messages.takeLast(2)}")
            assertEquals(path.hudPath, ra.varbit(Varbits.TOA_CLIENT_CURRENT_PATH))
            solvePuzzle(run, path)
            crossTo(run, path, continueDoor.first, continueDoor.second)
            assertEquals(path.boss, room(run), "$path: in the boss room; ${a.messages.takeLast(2)}")
            assertTrue(a.messages.any { it == "Challenge started: ${path.bossName}." }, "TA64 boss start line")
            fight(run, path.boss)
            // The fight moves the raider about (knock-backs, Kephri's swarm, Akkha's quadrants): stand inside the exit.
            val exit = run.at(exitDoor.second)
            ra.teleport(CoordGrid(exit.x + EXIT_SIDE.getValue(path), exit.z, exit.level))
            s.tick()
            op(exitDoor.first, exitDoor.second)
            assertEquals(ToaRaid.NEXUS, room(run), "$path: back in the Nexus; ${a.messages.takeLast(2)}")
        }
        // TA26: Walk the Path gave four level-ups in all (2 + 1 + 1) on top of Pathseeker's four.
        assertEquals(ToaPath.entries.size + WALK_LEVEL_UPS, st.pathLevels.sum(), "TA26 Walk the Path")
        assertEquals(WALK_LEVEL_UPS, a.messages.count { it.startsWith("You hear a mysterious rumbling") })
        op(Locs.TOA_NEXUS_WARDENS_DOOR_OPEN, WARDENS_DOOR)
        assertEquals(ToaRaid.WARDENS, room(run), "TA22 the Wardens; ${a.messages.takeLast(2)}")
        val osmumten = ra.npcsWithin(ra.coord, OSMUMTEN_SCAN).first { it.type == tp.symbols.Npcs.TOA_OSMUMTEN_WARDENS_VIS }
        a.input(ClientCommand.OpNpc(OP_BEGIN, osmumten.index, false))
        walk()
        assertTrue(requireNotNull(ToaFlow.state(run)).wardensStarted, "WD1 Begin woke the obelisk")
        // WD46: the white crystal is where the phase 3 Warden fell.
        val warden = requireNotNull(fight(run, ToaRaid.WARDENS)) { "no phase 3 Warden seen" }
        val crystal = warden.translate(P3_WARDEN_SIZE / 2, P3_WARDEN_SIZE / 2)
        a.input(ClientCommand.OpLoc(1, Locs.TOA_TELEPORT_CRYSTAL_CONTINUE_WARDENS, crystal.x, crystal.z, false))
        walk()
        assertEquals(RunState.Completed, run.state, "the raid is complete in the burial chamber; ${a.messages.takeLast(3)}; a ${a.coord}")
        assertEquals(1, ra.varp(tp.symbols.Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY), "TA60 Entry KC")
    }

    /**
     * Crondis is solved through real ops (fill at two waterfalls, water the palm). Apmeken (eight baboon waves need real
     * combat), Het (a drafted mirror route and Mining the Seal) and Scabaras (five stepping puzzles with random layouts)
     * cannot be driven blind through ops here, so their room reports itself done as before.
     */
    private fun solvePuzzle(run: RaidRun, path: ToaPath) {
        if (path != ToaPath.CRONDIS) {
            ToaFlow.roomDone(ra, run, path.puzzle)
            return
        }
        for (i in WATERFALLS.indices) {
            ra.teleport(run.at(WATERFALL_STANDS[i]))
            s.tick(2)
            val fall = run.at(WATERFALLS[i])
            a.input(ClientCommand.OpLoc(1, Locs.TOA_CRONDIS_WATER_SOURCE, fall.x, fall.z, false))
            s.tick(WATER_TICKS)
            ra.teleport(run.at(PALM_STAND))
            s.tick(2)
            val palm =
                ra.npcsWithin(run.at(PALM_AT), PALM_RADIUS).firstOrNull { it.displayName.contains("Palm of Resourcefulness") } ?: break
            a.input(ClientCommand.OpNpc(1, palm.index, false))
            s.tick(WATER_TICKS)
        }
        assertTrue("crondis" in requireNotNull(ToaFlow.state(run)).done, "the palm is fully grown; ${a.messages.takeLast(3)}")
    }

    /**
     * Heavy hits on every NPC of the run in the room's square (and the Wardens' phase 3 square) until the room is done.
     * The fights' own scripts run (phases, shields, shadows, cores, adds); only the raider's damage is stubbed. Returns
     * where a phase 3 Warden was last seen (the Wardens only).
     */
    private fun fight(run: RaidRun, room: String): CoordGrid? {
        var lastWarden: CoordGrid? = null
        val squares = listOfNotNull(ToaRooms.frame(run, room), if (room == ToaRaid.WARDENS) ToaRooms.wardensP3Frame(run) else null)
        var t = 0
        while (room !in requireNotNull(ToaFlow.state(run)).done && t < FIGHT_LIMIT) {
            for (f in squares) {
                for (level in 0..MAX_LEVEL) {
                    val centre = CoordGrid(f.origin.x + HALF, f.origin.z + HALF, level)
                    for (n in ra.npcsWithin(centre, HALF)) {
                        if (n.hitpoints <= 0 || n[tp.plugin.SharedAttrs.RAID_RUN] != run.id.value) continue
                        if (n.type in ToaNpcs.P3_WARDENS) lastWarden = n.coord
                        for (style in HitStyle.entries) ra.events.publish(DamageRequest(n, ra, HIT, style))
                    }
                }
            }
            walk(1)
            t++
        }
        val left =
            squares
                .flatMap { f ->
                    (0..MAX_LEVEL).flatMap { l -> ra.npcsWithin(CoordGrid(f.origin.x + HALF, f.origin.z + HALF, l), HALF) }
                }.map { "${it.type}@${it.coord}:${it.hitpoints}" }
        assertTrue(t < FIGHT_LIMIT, "$room won in $t ticks; ${a.messages.takeLast(3)}; a ${a.coord}; left $left")
        return lastWarden
    }

    private fun room(run: RaidRun): String? = (run.state as? RunState.InRoom)?.room?.id

    /** The nearest flame barrier ahead of the player along x ([dx] = +1 east, -1 west), a few rows either side. */
    private fun nearestBarrier(dx: Int): tp.plugin.LocRef? {
        for (d in 1..BARRIER_SCAN) {
            for (dz in -BARRIER_ROWS..BARRIER_ROWS) {
                barrierAt(CoordGrid(a.coord.x + d * dx, a.coord.z + dz, a.coord.level))?.let { return it }
            }
        }
        return null
    }

    private fun barrierAt(t: CoordGrid) = BARRIER_SHAPES.firstNotNullOfOrNull { ra.locAt(t, it) }?.takeIf { it.id == Locs.TOA_PATH_BARRIER }

    /** Walks to the continue door through the flame barriers (45135 "Pass") in the way. */
    private fun crossTo(run: RaidRun, path: ToaPath, door: Int, template: CoordGrid) {
        val target = run.at(template)
        repeat(BARRIER_TRIES) {
            op(door, template)
            if (room(run) == path.boss) return
            val dx = if (target.x > a.coord.x) 1 else -1
            val barrier = nearestBarrier(dx)
            if (barrier == null) {
                // Milestone 2 (the puzzles) opens the room's inner ways; until then, step up to the door's west side.
                ra.teleport(CoordGrid(target.x - 1, target.z, target.level))
                return@repeat
            }
            a.input(ClientCommand.OpLoc(1, barrier.id, barrier.coord.x, barrier.coord.z, false))
            walk()
        }
    }

    /** Lets a walk finish; the raider is kept alive (the room's NPCs fight back; the fights are not under test here). */
    private fun walk(ticks: Int = WALK) {
        repeat(ticks) {
            ra.hitpoints = maxOf(ra.hitpoints, KEEP_ALIVE)
            ra.frozenUntil = 0
            s.tick()
        }
    }

    /** Ops loc [id] at template tile [template] (inside the run's instance once one exists), then lets the walk finish. */
    private fun op(id: Int, template: CoordGrid) {
        val run = Raids.of(ra).runOf("a")
        val at = if (run?.instance != null && template.z < LOBBY_Z) run.at(template) else template
        a.input(ClientCommand.OpLoc(1, id, at.x, at.z, false))
        walk()
    }

    private companion object {
        const val SEED = 1L
        const val WALK = 40
        const val KEEP_ALIVE = 99
        const val HIT = 60
        const val FIGHT_LIMIT = 3000
        const val HALF = 32
        const val MAX_LEVEL = 3
        const val OSMUMTEN_SCAN = 24
        const val OP_BEGIN = 3
        const val P3_WARDEN_SIZE = 5

        /** Which side of each boss exit is the arena (+1 east of the door, -1 west). */
        val EXIT_SIDE = mapOf(ToaPath.HET to -1, ToaPath.SCABARAS to 1, ToaPath.APMEKEN to 1, ToaPath.CRONDIS to -1)
        const val WATER_TICKS = 6
        const val PALM_RADIUS = 4
        val WATERFALLS = listOf(CoordGrid(3926, 5250), CoordGrid(3940, 5250))
        val WATERFALL_STANDS = listOf(CoordGrid(3927, 5255), CoordGrid(3941, 5255))
        val PALM_AT = CoordGrid(3934, 5278)
        val PALM_STAND = CoordGrid(3933, 5277)
        const val LEVEL = 65
        const val WALK_THE_PATH_KEY = 11
        const val PATHSEEKER_KEY = 12
        const val WALK_LEVEL_UPS = 4
        const val ORB_FULL = 27
        const val BARRIER_TRIES = 4
        const val BARRIER_SCAN = 40
        const val BARRIER_ROWS = 6
        val BARRIER_SHAPES = listOf(10, 0, 2, 11)

        /** Template tiles z >= this are the real lobby (not instanced). */
        const val LOBBY_Z = 9000

        val LOBBY_DOOR = CoordGrid(3357, 9112)
        val WARDENS_DOOR = CoordGrid(3548, 5134)

        /** Path -> (Nexus door, continue door, boss exit) as (loc, template tile) (client-contract 6.2). */
        val ORDER =
            listOf(
                ToaPath.HET to
                    Triple(
                        Locs.TOA_NEXUS_HET_DOOR to CoordGrid(3539, 5146),
                        Locs.TOA_DOOR_CONTINUE to CoordGrid(3659, 5278),
                        Locs.TOA_ENTRANCE_AKKHA01 to CoordGrid(3699, 5406, 1),
                    ),
                ToaPath.SCABARAS to
                    Triple(
                        Locs.TOA_NEXUS_SCABARAS_DOOR to CoordGrid(3559, 5155),
                        Locs.TOA_SCABARAS_CONTINUE to CoordGrid(3582, 5279),
                        Locs.TOA_ENTRANCE_KEPHRI_MAIN to CoordGrid(3533, 5407),
                    ),
                ToaPath.APMEKEN to
                    Triple(
                        Locs.TOA_NEXUS_APMEKEN_DOOR to CoordGrid(3562, 5146),
                        Locs.TOA_PATH_APMEKEN_CONTINUE to CoordGrid(3825, 5279),
                        Locs.TOA_ENTRANCE_BABA02 to CoordGrid(3788, 5407),
                    ),
                ToaPath.CRONDIS to
                    Triple(
                        Locs.TOA_NEXUS_CRONDIS_DOOR to CoordGrid(3541, 5155),
                        Locs.TOA_PATH_CRONDIS_CONTINUE to CoordGrid(3916, 5279),
                        Locs.TOA_ZEBAK_EXIT to CoordGrid(3961, 5407, 1),
                    ),
            )
    }
}
