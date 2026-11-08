package tp.content.raids.cox

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.DamageRequest
import tp.plugin.HitStyle
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.protocol.api.ClientCommand
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
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
    private val trace = ArrayList<String>()

    @Test
    fun `a solo raider clears generated layouts and Challenge Mode`() {
        for (seed in SEEDS) walk(seed, cm = false)
        walk(CM_SEED, cm = true)
    }

    private fun walk(seed: Long, cm: Boolean) {
        s = Scenario(seed = seed)
        a = s.player("a", CoxRaid.OUTSIDE)
        s.tick()
        ra.events.on<tp.plugin.HitEvent> { e ->
            if (e.target == ra) trace += "hit ${e.damage} ${e.style} by ${(e.source as? tp.plugin.NpcRef)?.type}"
        }
        // A maxed raider (the Guardians are fought with real pickaxe attacks).
        for (stat in MAXED) a.input(ClientCommand.Cheat("xp $stat $MAX_XP"))
        s.tick()
        // GU1: the Guardians take only pickaxe hits.
        assertTrue(ra.transaction("smoke") { setAt(Invs.WORN, WEAPON_SLOT, Objs.DRAGON_PICKAXE) }, "pickaxe worn")
        assertEquals(Objs.DRAGON_PICKAXE, ra.inv(Invs.WORN).obj(WEAPON_SLOT))
        if (cm) {
            val raids = Raids.of(ra)
            val party = requireNotNull(raids.createParty(requireNotNull(raids.definition(CoxRaid.ID)), "a"))
            party.settings[CoxFlow.CM_SETTING] = 1
        }
        a.input(ClientCommand.OpLoc(1, Locs.RAIDS_ENTRANCE_STEPS, CoxRaid.ENTRANCE.x, CoxRaid.ENTRANCE.z, false))
        s.tick(WALK)
        val run = requireNotNull(Raids.of(ra).runOf("a")) { "seed $seed: no run; ${a.messages}" }
        val st = requireNotNull(CoxFlow.state(run))
        assertEquals(CoxRaid.LOBBY, (run.state as? RunState.InRoom)?.room?.id, "seed $seed: in the lobby")
        checkTemplates(run, st.plan)
        val plan = st.plan
        assertEquals(plan.cells.first(), CoxMap.cellAt(plan, run, a.coord), "seed $seed ${plan.code}: arrived in the lobby cell")
        for (cell in plan.cells) {
            val at = CoxMap.cellAt(plan, run, a.coord)
            assertEquals(
                cell,
                at,
                "seed $seed ${plan.code}: in cell ${cell.index} ${cell.kind}, not ${at?.index} ${at?.kind}; ${a.messages.takeLast(2)}; " +
                    "cells ${plan.cells.map { it.kind }}; ${trace.takeLast(TRACE)}",
            )
            val last = cell === plan.cells.last()
            if (cell.kind.combat) fight(run, cell)
            if (cell.kind.puzzle) puzzle(run, cell)
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
        val member = requireNotNull(run.member("a"))
        val before = member.points
        CoxPoints.award(run, "a", POINTS)
        s.tick()
        assertEquals((before + POINTS).toInt(), ra.varp(Varps.RAIDS_PLAYERSCORE))
        assertEquals(CoxPoints.team(run).toInt(), ra.varbit(Varbits.RAIDS_CLIENT_PARTYSCORE))
        assertEquals(member.points * DEATH_SHARE, CoxPoints.deathPenalty(member, run))
        assertTrue(CoxMap.inOlm(run, a.coord), "seed $seed: in the Olm square at ${a.coord}")
        // CX34 (Strat): a death now respawns at the end of the last generated floor.
        val respawn = requireNotNull(CoxFlow.respawn(run, ra))
        assertEquals(plan.cells.last(), CoxMap.cellAt(plan, run, respawn), "seed $seed: respawn at the last floor's end")
        if (cm) assertEquals(CoxRaid.CHALLENGE, run.scaling.mode, "Challenge Mode run")
        if (cm) assertEquals(3, plan.floors, "CX15: the Challenge Mode layout has three floors")
        olm(run, seed)
    }

    /** Walks up to the room's NPCs (they wake on a raider nearby) and lands heavy hits until the room is done. */
    private fun fight(run: RaidRun, cell: CoxCell) {
        val st = requireNotNull(CoxFlow.state(run))
        val inCell = { c: tp.util.CoordGrid -> CoxMap.cellAt(st.plan, run, c) == cell }
        var t = 0
        while (!st.done[cell.index] && t < FIGHT_LIMIT) {
            val npcs = ra.npcsWithin(a.coord, SCAN).filter { it.hitpoints > 0 && inCell(it.coord) && it.type !in DEMOLISHED }
            if (t % APPROACH_EVERY == 0 && npcs.none { ra.distanceTo(it) <= 1 }) {
                npcs.firstOrNull()?.let {
                    val near = CoxMap.freeNear(ra, tp.util.CoordGrid(it.coord.x - 1, it.coord.z, it.coord.level))
                    if (inCell(near)) ra.teleport(near)
                }
            }
            if (cell.kind == CoxKind.GUARDIANS) {
                // GU3: crush is their weakness (the pickaxe's third stance, Smash).
                ra.setVarp(COMBAT_STANCE_VARP, CRUSH_STANCE)
                // GU1: pickaxe hits from the raider (a DamageRequest has no source, so it would not count).
                if (t % ATTACK_EVERY == 0) npcs.firstOrNull()?.let { a.input(ClientCommand.OpNpc(ATTACK_OP, it.index, false)) }
            } else {
                for (n in npcs) {
                    for (style in STYLES) ra.events.publish(DamageRequest(n, ra, HIT, style))
                }
            }
            ra.hitpoints = KEEP_ALIVE
            // Protect from Magic (Vasa's blast, the mystics), as a raider would.
            ra.setVarbit(Varbits.PRAYER_ALLACTIVE, 1 shl protectMagicBit)
            ra.setLevel(tp.plugin.Stats.PRAYER, PRAYER_LEVEL)
            s.tick()
            trace += "t$t hp ${ra.hitpoints} at ${a.coord}"
            t++
        }
        val left = ra.npcsWithin(a.coord, SCAN).filter { inCell(it.coord) }.map { "${it.type}:${it.hitpoints}" }
        val alive = st.roomAlive[cell.index]?.map { "${it.type}@${it.coord} in=${it.isInWorld} hp=${it.hitpoints}" }
        assertTrue(st.done[cell.index], "${cell.kind} cleared in $t ticks; left $left; required $alive; a ${a.coord}; ${trace.takeLast(3)}")
    }

    /** Passes the barrier and beats the Great Olm through every phase; the raid completes on its death. */
    private fun olm(run: RaidRun, seed: Long) {
        val st = requireNotNull(CoxFlow.state(run))
        val barrier = run.instance!!.let { CoxMap.olm(run, tp.util.CoordGrid(BARRIER_X, BARRIER_Z)) }
        ra.teleport(CoxMap.freeNear(ra, tp.util.CoordGrid(barrier.x + 1, barrier.z - 1, barrier.level)))
        s.tick()
        a.input(ClientCommand.OpLoc(1, Locs.RAIDS_OLM_BARRIER, barrier.x, barrier.z, false))
        repeat(WALK) { keepAlive() }
        assertTrue(st.olmStarted, "seed $seed: the barrier woke the Olm; ${a.messages.takeLast(3)}")
        val parts = setOf(tp.symbols.Npcs.OLM_HEAD, tp.symbols.Npcs.OLM_HAND_LEFT, tp.symbols.Npcs.OLM_HAND_RIGHT)
        var t = 0
        while (run.state != RunState.Completed && t < OLM_LIMIT) {
            for (n in ra.npcsWithin(a.coord, SCAN).filter { it.type in parts && it.hitpoints > 0 }) {
                val style =
                    when (n.type) {
                        tp.symbols.Npcs.OLM_HAND_LEFT -> HitStyle.MELEE
                        tp.symbols.Npcs.OLM_HAND_RIGHT -> HitStyle.MAGIC
                        else -> HitStyle.RANGED
                    }
                ra.events.publish(DamageRequest(n, ra, HIT, style))
            }
            ra.setVarbit(Varbits.PRAYER_ALLACTIVE, 1 shl protectMagicBit)
            keepAlive()
            t++
        }
        assertEquals(RunState.Completed, run.state, "seed $seed: the Olm died and the raid completed in $t ticks")
        assertTrue(a.messages.any { "Congratulations - your raid is complete!" in it }, "seed $seed: completion line")
        val kc =
            if (run.scaling.mode ==
                CoxRaid.CHALLENGE
            ) {
                Varps.TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE
            } else {
                Varps.TOTAL_COMPLETED_XERICCHAMBERS
            }
        assertEquals(1, ra.varp(kc), "seed $seed: KC")
        // CR7: commons and the Dark journal wait in the reward inventory (581) for the chest.
        val rewards = ra.inv(Invs.RAIDS_REWARDS)
        assertTrue((0 until rewards.size).any { rewards.count(it) > 0 }, "seed $seed: rewards paid")
    }

    /** Solves a puzzle room through its real ops (the crab bounce geometry excepted: the orb is checked, then the room). */
    private fun puzzle(run: RaidRun, cell: CoxCell) {
        val st = requireNotNull(CoxFlow.state(run))
        for (t in 0 until 2) s.tick()
        when (cell.kind) {
            CoxKind.THIEVING -> {
                thieving(run, cell, st)
            }

            CoxKind.TIGHTROPE -> {
                tightrope(run, cell, st)
            }

            CoxKind.ICE_DEMON -> {
                iceDemon(run, cell, st)
            }

            CoxKind.CRABS -> {
                crabs(run, cell)
            }

            else -> {
                Unit
            }
        }
        assertTrue(st.done[cell.index], "${cell.kind} solved; ${a.messages.takeLast(3)}")
    }

    private fun thieving(run: RaidRun, cell: CoxCell, st: CoxState) {
        val trough = CoxMap.findLoc(ra, run, cell, Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY)
        assertNotNull(trough, "thieving trough")
        var t = 0
        while (!st.done[cell.index] && t < FIGHT_LIMIT) {
            if (t % GRUB_EVERY == 0) {
                ra.transaction("smoke") { add(Invs.INV, Objs.RAIDS_THIEVINGCHEST_GRUBS, GRUBS) }
                val at =
                    requireNotNull(
                        CoxMap.findLoc(ra, run, cell, Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_EMPTY)
                            ?: CoxMap.findLoc(ra, run, cell, Locs.RAIDS_THIEVINGCHEST_FOODTROUGH_FULL),
                    )
                ra.teleport(CoxMap.freeNear(ra, at.coord))
                a.input(ClientCommand.OpLoc(1, at.id, at.coord.x, at.coord.z, false))
            }
            keepAlive()
            t++
        }
    }

    private fun tightrope(run: RaidRun, cell: CoxCell, st: CoxState) {
        val key = requireNotNull(CoxMap.findLoc(ra, run, cell, Locs.RAIDS_TIGHTROPE_KEYSTONE_LOC)) { "keystone loc" }
        ra.teleport(CoxMap.freeNear(ra, key.coord))
        a.input(ClientCommand.OpLoc(1, key.id, key.coord.x, key.coord.z, false))
        repeat(WALK) { keepAlive() }
        assertTrue(ra.inv(Invs.INV).total(Objs.RAIDS_TIGHTROPE_KEYSTONE) > 0, "took the keystone")
        val barrier = requireNotNull(st.blockers[cell.index]) { "the barrier" }
        // TR5: the keystone comes back across to the barrier (to a free tile beside it).
        val beside =
            listOf(0 to -1, 0 to 2, 1 to 0, -1 to 0, 1 to 1, -1 to 1)
                .map { (dx, dz) -> tp.util.CoordGrid(barrier.x + dx, barrier.z + dz, barrier.level) }
                .firstOrNull { ra.isWalkable(it) } ?: CoxMap.arrival(ra, run, cell, cell.entrySide)
        ra.teleport(beside)
        s.tick()
        a.input(ClientCommand.OpLoc(1, Locs.RAIDS_TIGHTROPE_BARRIER, barrier.x, barrier.z, false))
        repeat(WALK) { keepAlive() }
        if (!st.done[cell.index]) {
            val loc = requireNotNull(ra.locAt(barrier, 10)) { "barrier at $barrier" }
            error(
                "tightrope: barrier $barrier key ${key.coord} a ${a.coord} keystone ${ra.inv(
                    Invs.INV,
                ).total(Objs.RAIDS_TIGHTROPE_KEYSTONE)} ${a.messages.takeLast(3)}\n" +
                    picture(run, cell, loc),
            )
        }
    }

    private fun iceDemon(run: RaidRun, cell: CoxCell, st: CoxState) {
        var t = 0
        while (!st.done[cell.index] && t < FIGHT_LIMIT) {
            if (t % KINDLE_EVERY == 0) {
                ra.transaction("smoke") {
                    add(Invs.INV, Objs.RAIDS_WOOD, KINDLING)
                    if (ra.inv(Invs.INV).total(Objs.TINDERBOX) == 0L) add(Invs.INV, Objs.TINDERBOX)
                    true
                }
                val brazier =
                    CoxMap.findLoc(ra, run, cell, Locs.RAIDS_ICEDEMON_BRAZIER_UNLIT)
                        ?: CoxMap.findLoc(ra, run, cell, Locs.RAIDS_ICEDEMON_BRAZIER_LIT)
                if (brazier != null) {
                    ra.teleport(CoxMap.freeNear(ra, brazier.coord))
                    a.input(ClientCommand.OpLoc(1, brazier.id, brazier.coord.x, brazier.coord.z, false))
                }
            }
            for (n in ra.npcsWithin(a.coord, SCAN).filter { it.type == tp.symbols.Npcs.RAIDS_ICEDEMON_COMBAT }) {
                ra.events.publish(DamageRequest(n, ra, HIT, HitStyle.MAGIC))
            }
            keepAlive()
            t++
        }
    }

    private fun crabs(run: RaidRun, cell: CoxCell) {
        // CR1: the carving sends an orb that travels (the bounce geometry is not solved here).
        val seen = HashSet<tp.util.CoordGrid>()
        repeat(ORB_TICKS) {
            keepAlive()
            seen += ra.npcsWithin(a.coord, SCAN).filter { it.type in ORBS }.map { it.coord }
        }
        assertTrue(seen.size >= 2, "an orb flies in the crab room: $seen")
        CoxRooms.complete(run, cell, ra)
    }

    private fun keepAlive() {
        ra.hitpoints = KEEP_ALIVE
        s.tick()
    }

    /** The loc that leads on from [cell]: the lobby / room passage, the floor hole or the Olm hole. */
    private fun exitOf(run: RaidRun, cell: CoxCell): Pair<Int, LocRef?> {
        val plan = requireNotNull(CoxFlow.state(run)).plan
        val id =
            when {
                cell.kind != CoxKind.END -> Locs.RAIDS_DOORWAY
                cell === plan.cells.last() -> Locs.RAIDS_BOSSENTRANCE
                CoxMap.findLocs(ra, run, cell, Locs.RAIDS_DESCENTTO1).isNotEmpty() -> Locs.RAIDS_DESCENTTO1
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
            if (attempt == 1) {
                // From a free tile beside the loc, inside the cell (the walk there is blocked).
                val near = CoxMap.freeNear(ra, loc.coord)
                ra.teleport(if (CoxMap.cellAt(plan, run, near) == cell) near else CoxMap.arrival(ra, run, cell, cell.exitSide))
            }
            s.tick()
            a.input(ClientCommand.OpLoc(1, id, loc.coord.x, loc.coord.z, false))
            for (t in 0 until WALK_LIMIT) {
                ra.hitpoints = KEEP_ALIVE
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
        const val FIGHT_LIMIT = 1500
        const val APPROACH_EVERY = 10
        const val SCAN = 40
        const val HIT = 400
        const val KEEP_ALIVE = 255
        const val TRACE = 10
        const val CM_SEED = 7L
        const val OLM_LIMIT = 1500
        const val BARRIER_X = 3231
        const val BARRIER_Z = 5729
        const val ATTACK_OP = 2
        const val COMBAT_STANCE_VARP = 43
        const val CRUSH_STANCE = 2
        const val GRUB_EVERY = 6
        const val GRUBS = 28
        const val KINDLE_EVERY = 5
        const val KINDLING = 28
        const val ORB_TICKS = 12
        val ORBS =
            setOf(
                tp.symbols.Npcs.RAIDS_LASERCRABS_ENERGY_WHITE,
                tp.symbols.Npcs.RAIDS_LASERCRABS_ENERGY_RED,
                tp.symbols.Npcs.RAIDS_LASERCRABS_ENERGY_GREEN,
                tp.symbols.Npcs.RAIDS_LASERCRABS_ENERGY_BLUE,
            )
        val STYLES = listOf(HitStyle.MELEE, HitStyle.RANGED, HitStyle.MAGIC)
        val DEMOLISHED = setOf(tp.symbols.Npcs.RAIDS_STONEGUARDIANS_LEFT_DEAD, tp.symbols.Npcs.RAIDS_STONEGUARDIANS_RIGHT_DEAD)
        const val ATTACK_EVERY = 5
        const val MAX_XP = 13_034_431

        /** Attack, defence, strength, hitpoints, ranged, prayer, magic, firemaking, mining. */
        val MAXED = intArrayOf(0, 1, 2, 3, 4, 5, 6, 11, 14)

        /** The prayer bit of Protect from Magic (prayer objs in enum 4956: param 1752 name, 630 bit). */
        val protectMagicBit: Int by lazy {
            val objs = Scenario.data.configs.objs
            Scenario.data.configs.enums
                .getValue(PRAYER_ENUM)
                .intValues.values
                .first { objs.getValue(it).params.string(PARAM_NAME) == "Protect from Magic" }
                .let { requireNotNull(objs.getValue(it).params.int(PARAM_BIT)) }
        }
        const val PRAYER_ENUM = 4956
        const val PARAM_NAME = 1752
        const val PARAM_BIT = 630
        const val PRAYER_LEVEL = 99
        const val WEAPON_SLOT = 3
        const val POINTS = 1000.0
        const val DEATH_SHARE = 0.4
    }
}
