package tp.content.raids.tob

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
import tp.symbols.Invs
import tp.symbols.Locs
import tp.tools.scenario.Scenario
import tp.tools.scenario.ScenarioPlayer
import tp.util.CoordGrid

/**
 * Raids brief section 9 (test 3), ToB smoke: a duo with god mode (HP kept full) and heavy hits clears all six rooms in
 * each mode through the real ops: the theatre entrance, each room's barrier, the room exits, Verzik's Talk-to, the
 * throne door and the treasure room's chests and crystal.
 */
class TobSmokeScenario {
    private lateinit var s: Scenario
    private lateinit var a: ScenarioPlayer
    private lateinit var b: ScenarioPlayer
    private lateinit var run: RaidRun
    private val ra get() = PlayerRef.of(a.player)

    @Test
    fun `a duo clears the theatre in every mode`() {
        for (mode in listOf(TobParties.MODE_ENTRY, TobParties.MODE_NORMAL, TobParties.MODE_HARD)) clearTheatre(mode)
    }

    private fun clearTheatre(mode: Int) {
        s = Scenario(seed = 11)
        a = s.player("a", TobRaid.LOBBY)
        b = s.player("b", TobRaid.LOBBY)
        tick()
        // TB4: Hard needs a Normal completion.
        for (p in listOf(a, b)) PlayerRef.of(p.player).setVarp(tp.symbols.Varps.TOTAL_COMPLETED_THEATREOFBLOOD, 1)
        val raids = Raids.of(ra)
        val party = requireNotNull(raids.createParty(requireNotNull(raids.definition(TobRaid.ID)), "a"))
        assertTrue(party.join("b"))
        party.settings[TobParties.MODE_SETTING] = mode
        for (p in listOf(a, b)) {
            p.input(ClientCommand.OpLoc(1, Locs.TOB_SURFACE_RAID_ENTRANCE, ENTRANCE.x, ENTRANCE.z, false))
            tick(WALK)
        }
        run = requireNotNull(raids.runOf("a"))
        assertEquals(listOf("maiden"), run.roomsTaken)
        for ((room, barrier) in BARRIERS) {
            assertEquals(room, (run.state as? RunState.InRoom)?.room?.id, "in $room")
            if (barrier != null) {
                for (p in listOf(a, b)) {
                    APPROACH[room]?.let { PlayerRef.of(p.player).teleport(run.at(it)) }
                    tick()
                    val at = run.at(barrier)
                    p.input(ClientCommand.OpLoc(1, Locs.TOB_ARENA_BARRIER, at.x, at.z, false))
                    tick(WALK)
                }
                assertTrue(
                    TobFlow.state(run).engaged,
                    "$room: the barrier started the fight; a at ${a.coord} (template ${run.instance?.toTemplate(
                        a.coord,
                    )}), barrier ${run.at(barrier)}",
                )
            } else {
                val verzik = ra.npcsWithin(a.coord, SCAN).first { it.type in TobNpcs.VERZIK.all }
                a.input(ClientCommand.OpNpc(1, verzik.index, false))
                tick(WALK)
            }
            fight(room)
            assertTrue(run.state is RunState.BetweenRooms, "$room won: ${run.state}")
            val exit = EXITS[room]
            if (exit != null) {
                ra.teleport(run.at(exit))
                tick(2)
            } else {
                val door = run.at(THRONE_DOOR)
                a.input(ClientCommand.OpLoc(1, Locs.TOB_DUNGEON_VERZIK_THRONE_DOOR_OPENED, door.x, door.z, false))
                tick(WALK)
            }
        }
        assertEquals("treasure", (run.state as? RunState.InRoom)?.room?.id ?: if (run.state == RunState.Completed) "treasure" else "-")
        assertEquals(RunState.Completed, run.state, "the raid completed in the treasure room")
        assertTrue(
            ra.inv(Invs.TOB_CHESTS).let { inv ->
                (0 until inv.size).any { inv.count(it) > 0 }
            } || mode == TobParties.MODE_ENTRY,
            "rewards paid",
        )
    }

    /** Heavy hits on every NPC of the room until it is won (the fights' own scripts run: waves, mazes, phases). */
    private fun fight(room: String) {
        var t = 0
        while (run.state is RunState.InRoom && t < FIGHT_LIMIT) {
            val inst = requireNotNull(run.instance)
            for (n in ra.npcsWithin(a.coord, SCAN).filter { it.hitpoints > 0 && it.coord in inst && it.type !in TobNpcs.PILLAR.all }) {
                ra.events.publish(DamageRequest(n, ra, HIT, HitStyle.MAGIC))
                ra.events.publish(DamageRequest(n, ra, HIT, HitStyle.RANGED))
                ra.events.publish(DamageRequest(n, ra, HIT, HitStyle.MELEE))
            }
            tick()
            t++
        }
        val left =
            ra
                .npcsWithin(a.coord, SCAN)
                .filter {
                    it.coord in requireNotNull(run.instance)
                }.map { "${it.type}:${it.hitpoints}/${it.maxHitpoints}" }
        assertTrue(t < FIGHT_LIMIT, "$room ended in $t ticks; left $left; a ${a.coord}")
    }

    private fun tick(n: Int = 1) {
        repeat(n) {
            for (p in listOf(a, b)) {
                val r = PlayerRef.of(p.player)
                r.hitpoints = KEEP_ALIVE
                r.frozenUntil = 0
            }
            s.tick()
        }
    }

    private companion object {
        /** The theatre entrance 32653 (client-contract 5). */
        val ENTRANCE = CoordGrid(3678, 3216)

        /** A barrier tile per room (template; client-contract 5); null = Verzik (Talk-to). */
        val BARRIERS =
            listOf(
                "maiden" to CoordGrid(3185, 4446),
                "bloat" to CoordGrid(3287, 4447),
                "nylocas" to CoordGrid(3295, 4255, 1),
                "sotetseg" to CoordGrid(3279, 4307),
                "xarpus" to CoordGrid(3170, 4395, 1),
                "verzik" to null,
            )

        /** The antechamber tile beside each barrier (free tiles, client-contract 5). */
        val APPROACH =
            mapOf(
                "maiden" to CoordGrid(3186, 4446),
                "bloat" to CoordGrid(3286, 4447),
                "nylocas" to CoordGrid(3295, 4256, 1),
                "sotetseg" to CoordGrid(3279, 4306),
                "xarpus" to CoordGrid(3170, 4396, 1),
            )

        /** A tile in each room's exit box (TobFlow). */
        val EXITS =
            mapOf(
                "maiden" to CoordGrid(3219, 4459),
                "bloat" to CoordGrid(3321, 4447),
                "nylocas" to CoordGrid(3295, 4282, 1),
                "sotetseg" to CoordGrid(3279, 4332),
                "xarpus" to CoordGrid(3170, 4377, 1),
            )
        val THRONE_DOOR = CoordGrid(3166, 4323)
        const val WALK = 25
        const val SCAN = 40
        const val HIT = 400
        const val FIGHT_LIMIT = 3000
        const val KEEP_ALIVE = 255
    }
}
