package tp.content.raids.cox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.protocol.api.ClientCommand
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.tools.scenario.Scenario
import tp.tools.scenario.ScenarioPlayer

/**
 * Raids brief section 9 (test 3), CoX smoke: a solo player enters from Mount Quidamortem, starts the raid from the
 * lobby and walks a generated layout room by room through the real ops (passages, the floor hole, the Olm hole) to the
 * Great Olm's antechamber, for several seeds. Every cell is checked to show its template cell (the RuneLite contract).
 */
class CoxSmokeScenario {
    private lateinit var s: Scenario
    private lateinit var a: ScenarioPlayer
    private val ra get() = PlayerRef.of(a.player)

    @Test
    fun `a solo raider walks generated layouts to the Olm`() {
        for (seed in SEEDS) walk(seed)
    }

    private fun walk(seed: Long) {
        s = Scenario(seed = seed)
        a = s.player("a", CoxRaid.OUTSIDE)
        s.tick()
        a.input(ClientCommand.OpLoc(1, Locs.RAIDS_ENTRANCE_STEPS, CoxRaid.ENTRANCE.x, CoxRaid.ENTRANCE.z, false))
        s.tick(WALK)
        val run = requireNotNull(Raids.of(ra).runOf("a")) { "seed $seed: no run; ${a.messages}" }
        val st = requireNotNull(CoxFlow.state(run))
        assertEquals(CoxRaid.LOBBY, (run.state as? RunState.InRoom)?.room?.id, "seed $seed: in the lobby")
        checkTemplates(run, st.plan)
        val plan = st.plan
        assertEquals(plan.cells.first(), CoxMap.cellAt(plan, run, a.coord), "seed $seed ${plan.code}: arrived in the lobby cell")
        for (cell in plan.cells) {
            assertEquals(cell, CoxMap.cellAt(plan, run, a.coord), "seed $seed ${plan.code}: in cell ${cell.index} ${cell.kind}")
            val last = cell === plan.cells.last()
            val (id, loc) = exitOf(run, cell)
            assertNotNull(loc, "seed $seed ${plan.code}: cell ${cell.index} ${cell.kind} has its way on ($id)")
            use(run, plan, cell, id, requireNotNull(loc))
            if (last) break
        }
        assertEquals(CoxRaid.OLM, (run.state as? RunState.InRoom)?.room?.id, "seed $seed: the Olm room started")
        s.tick()
        // HUD 513 (client-contract 2.2): in the dungeon, progress 4 in the Olm's room, the timer started.
        assertEquals(1, ra.varbit(Varbits.RAIDS_CLIENT_INDUNGEON))
        assertEquals(4, ra.varbit(Varbits.RAIDS_CLIENT_PROGRESS), "seed $seed: progress in the Olm room")
        assertTrue(ra.varbit(Varbits.RAIDS_TIMER) > 0, "seed $seed: the raid timer runs")
        assertTrue(a.messages.contains(CoxFlow.RAID_BEGUN), "seed $seed: start message")
        assertTrue(
            a.messages.any { "Upper level complete!" in it } && a.messages.any { "Lower level complete!" in it },
            "seed $seed: floor lines",
        )
        // CX21 / CX30: points show on the HUD; a death costs 40% of them.
        CoxPoints.award(run, "a", POINTS)
        s.tick()
        assertEquals(POINTS.toInt(), ra.varp(Varps.RAIDS_PLAYERSCORE))
        assertEquals(POINTS.toInt(), ra.varbit(Varbits.RAIDS_CLIENT_PARTYSCORE))
        assertEquals(POINTS * DEATH_SHARE, CoxPoints.deathPenalty(requireNotNull(run.member("a")), run))
        assertTrue(CoxMap.inOlm(run, a.coord), "seed $seed: in the Olm square at ${a.coord}")
        // CX34 (Strat): a death now respawns at the end of the last generated floor.
        val respawn = requireNotNull(CoxFlow.respawn(run, ra))
        assertEquals(plan.cells.last(), CoxMap.cellAt(plan, run, respawn), "seed $seed: respawn at the last floor's end")
    }

    /** The loc that leads on from [cell]: the lobby / room passage, the floor hole or the Olm hole. */
    private fun exitOf(run: RaidRun, cell: CoxCell): Pair<Int, LocRef?> {
        val plan = requireNotNull(CoxFlow.state(run)).plan
        val id =
            when {
                cell.kind != CoxKind.END -> Locs.RAIDS_DOORWAY
                cell === plan.cells.last() -> Locs.RAIDS_BOSSENTRANCE
                else -> Locs.RAIDS_DESCENTTO2
            }
        val locs = CoxMap.findLocs(ra, run, cell, id)
        val loc =
            if (id ==
                Locs.RAIDS_DOORWAY
            ) {
                locs.firstOrNull { CoxFlow.passageSide(requireNotNull(CoxFlow.state(run)), cell, it) == cell.exitSide }
            } else {
                locs.firstOrNull()
            }
        return id to loc
    }

    /** Ops [loc]; when the walk there is blocked (puzzle rooms: no tightrope yet), from a tile beside it. */
    private fun use(run: RaidRun, plan: CoxLayoutPlan, cell: CoxCell, id: Int, loc: LocRef) {
        for (attempt in 0 until 2) {
            if (attempt == 1) ra.teleport(CoxMap.freeNear(ra, loc.coord))
            s.tick()
            a.input(ClientCommand.OpLoc(1, id, loc.coord.x, loc.coord.z, false))
            for (t in 0 until WALK_LIMIT) {
                s.tick()
                if (CoxMap.inOlm(run, a.coord) || CoxMap.cellAt(plan, run, a.coord) != cell) return
            }
        }
        error(
            "cell ${cell.index} ${cell.kind} r${cell.rotation} in ${cell.entrySide} out ${cell.exitSide} (${plan.code}): " +
                "$id at ${loc.coord} did not move a from ${a.coord}; ${a.messages.takeLast(3)}\n" +
                picture(run, cell, loc),
        )
    }

    /** The cell's walkable tiles (north up): P the player, D the loc. */
    private fun picture(run: RaidRun, cell: CoxCell, loc: LocRef): String {
        val sb = StringBuilder()
        for (lz in CoxLayout.CELL - 1 downTo 0) {
            for (lx in 0 until CoxLayout.CELL) {
                val t = CoxMap.inCell(run, cell, lx, lz)
                sb.append(
                    when {
                        t == a.coord -> 'P'
                        t == loc.coord -> 'D'
                        ra.isWalkable(t) -> '.'
                        else -> '#'
                    },
                )
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Client-contract 6.1: the middle of every cell shows a tile of its template cell (RuneLite reads the chunk). */
    private fun checkTemplates(run: RaidRun, plan: CoxLayoutPlan) {
        val inst = requireNotNull(run.instance)
        for (cell in plan.cells) {
            val t = requireNotNull(inst.toTemplate(CoxMap.inCell(run, cell, MID, MID))) { "cell ${cell.index}: no template" }
            val tx = t.x - cell.template.x
            val tz = t.z - cell.template.z
            assertTrue(
                tx in 0 until CoxLayout.CELL && tz in 0 until CoxLayout.CELL && t.level == cell.template.level,
                "cell ${cell.index} ${cell.kind} shows $t, not template ${cell.template}",
            )
            assertEquals(
                CoxMap.tile(run, cell, tx, tz),
                CoxMap.inCell(run, cell, MID, MID),
                "cell ${cell.index}: rotation ${cell.rotation}",
            )
        }
    }

    private companion object {
        val SEEDS = longArrayOf(1, 2, 3, 4, 5, 6)
        const val WALK = 10
        const val WALK_LIMIT = 60
        const val MID = 16
        const val POINTS = 1000.0
        const val DEATH_SHARE = 0.4
    }
}
