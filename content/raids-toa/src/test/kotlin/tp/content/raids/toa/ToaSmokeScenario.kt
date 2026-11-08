package tp.content.raids.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
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
 * reports them done here (their milestones add the real puzzles and fights). Checks the HUD contract on the way.
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
            ToaFlow.roomDone(ra, run, path.boss)
            op(exitDoor.first, exitDoor.second)
            assertEquals(ToaRaid.NEXUS, room(run), "$path: back in the Nexus; ${a.messages.takeLast(2)}")
        }
        // TA26: Walk the Path gave four level-ups in all (2 + 1 + 1) on top of Pathseeker's four.
        assertEquals(ToaPath.entries.size + WALK_LEVEL_UPS, st.pathLevels.sum(), "TA26 Walk the Path")
        assertEquals(WALK_LEVEL_UPS, a.messages.count { it.startsWith("You hear a mysterious rumbling") })
        op(Locs.TOA_NEXUS_WARDENS_DOOR_OPEN, WARDENS_DOOR)
        assertEquals(ToaRaid.WARDENS, room(run), "TA22 the Wardens; ${a.messages.takeLast(2)}")
        ToaFlow.roomDone(ra, run, ToaRaid.WARDENS)
        op(Locs.TOA_TELEPORT_CRYSTAL_CONTINUE_WARDENS, ToaRaid.WARDENS_ENTRY)
        assertEquals(RunState.Completed, run.state, "the raid is complete in the burial chamber")
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
    private fun walk() {
        repeat(WALK) {
            ra.hitpoints = maxOf(ra.hitpoints, KEEP_ALIVE)
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
