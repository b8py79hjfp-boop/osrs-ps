package tp.content.raids.tfa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.protocol.api.ClientCommand
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Varps
import tp.tools.scenario.Scenario
import tp.tools.scenario.ScenarioPlayer
import tp.util.CoordGrid

/**
 * Raids brief section 8, TFA smoke: a duo walks the whole Archive through the real loc and NPC ops (lobby door, one
 * door of each nexus section, passageway and transporters, the Ocellus passage, the Blade, the vault chest and rift).
 * Boss fights are replaced by [TfaFlow.cleared].
 */
class TfaSmokeScenario {
    private lateinit var s: Scenario
    private lateinit var a: ScenarioPlayer
    private lateinit var b: ScenarioPlayer
    private lateinit var run: RaidRun
    private val ra get() = PlayerRef.of(a.player)

    @Test
    @Suppress("LongMethod") // one scripted raid, room by room
    fun `a duo walks every door of the archive`() {
        s = Scenario(seed = 7)
        a = s.player("a", TfaRaid.LOBBY_DOOR)
        b = s.player("b", TfaRaid.LOBBY_DOOR)
        tick()
        val raids = Raids.of(ra)
        val party = requireNotNull(raids.createParty(requireNotNull(raids.definition(TfaRaid.ID)), "a"))
        assertTrue(party.join("b"))

        // Lobby: the leader opens the raid, the second player follows through the door.
        a.input(ClientCommand.OpLoc(1, Locs.TFA_LOBBY_RAID_ENTRY, DOOR.x, DOOR.z, false))
        tick(WALK)
        run = requireNotNull(raids.runOf("a"))
        assertEquals(listOf("guardian"), run.roomsTaken)
        assertEquals(run.at(TfaRaid.GUARDIAN_ENTRY), a.coord, "leader at the Guardian")
        assertNotNull(npc(Npcs.TFA_GUARDIAN_START), "Inert Guardian waits")
        assertTrue(b.coord !in requireNotNull(run.instance), "b has not entered yet")
        b.input(ClientCommand.OpLoc(1, Locs.TFA_LOBBY_RAID_ENTRY, DOOR.x, DOOR.z, false))
        tick(WALK)
        assertEquals(run.at(TfaRaid.GUARDIAN_ENTRY), b.coord, "b followed")
        assertTrue(a.player.let { PlayerRef.of(it).isOpen(Interfaces.TFA_HUD) }, "HUD open")

        // TFA17: a third player spectates from the lobby: hidden observer following the leader, then leaves.
        val c = s.player("c", TfaSpectate.SPECTATE_RETURN)
        tick()
        val rc = PlayerRef.of(c.player)
        TfaSpectate().start(rc, run)
        tick(2)
        assertTrue(c.player.observer && c.coord == a.coord, "spectator follows the leader: ${c.coord}")
        c.input(ClientCommand.MoveClick(c.coord.x + 3, c.coord.z, false, false))
        tick(2)
        assertEquals(a.coord, c.coord, "an observer cannot walk")
        TfaSpectate().stop(rc, toLobby = true)
        tick()
        assertEquals(TfaSpectate.SPECTATE_RETURN, c.coord, "back at the Strategy Board")
        assertTrue(!c.player.observer && run.spectators.isEmpty())

        // Section 1 west (Ezekiel), back through the passageway.
        clear()
        door(Locs.TFA_NEXUS_SECTION1_DOOR_WEST, CoordGrid(4113, 5584), op = 1)
        assertRoom("ezekiel", TfaRaid.EZEKIEL_ENTRY)
        loc(Locs.TFA_EXIT, CoordGrid(4085, 5533))
        assertTrue(a.messages.last().startsWith("The transporter will not work"), "exit refused during the fight")
        clear()
        loc(Locs.TFA_EXIT, CoordGrid(4085, 5533))
        assertEquals(run.at(TfaRaid.NEXUS_RETURN), a.coord, "back in the nexus")

        // Section 2 east (Dreadnoth) by Quick-enter, back by its transporter; the Quartermaster arrives after room 3.
        door(Locs.TFA_NEXUS_SECTION2_DOOR_EAST, CoordGrid(4141, 5597), op = 2)
        assertRoom("dreadnoth", TfaRaid.DREADNOTH_ENTRY)
        clear()
        loc(Locs.TFA_TELEPORTER_JELLY, CoordGrid(4177, 5663))
        assertEquals(run.at(TfaRaid.NEXUS_RETURN), a.coord)
        assertNotNull(npc(Npcs.TFA_MIDRAID_SUPPLIES_NPC), "Quartermaster in the nexus")

        // Section 3 west (Dagon'hai), back by the transporter; then the Ocellus passage opens.
        door(Locs.TFA_NEXUS_SECTION3_DOOR_WEST, CoordGrid(4113, 5610), op = 1)
        assertRoom("dagonhai", TfaRaid.DAGONHAI_ENTRY)
        clear()
        loc(Locs.TFA_BOSS_TELEPORTER, CoordGrid(4079, 5599))
        loc(Locs.TFA_EXIT_LARGE, CoordGrid(4124, 5625))
        assertRoom("ocellus", TfaRaid.OCELLUS_ENTRY)

        // Ocellus down: the Blade carries the party to the vault; the raid completes once.
        clear()
        val blade = requireNotNull(npc(Npcs.TFA_BLADE_NPC_VAULT_ENTRY)) { "the Blade" }
        a.input(ClientCommand.OpNpc(OP_TRAVEL, blade.index, false))
        tick(WALK)
        assertEquals(RunState.Completed, run.state)
        assertEquals(run.at(TfaRaid.VAULT_ENTRY), a.coord)
        assertEquals(1, a.player.vars[Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE])
        assertTrue(a.messages.any { it.startsWith("Your completed Fractured Archive count is: <col=ff0000>1</col>") })
        tick(TfaConfig.brazierTicks + 1)
        val chest = requireNotNull(npc(Npcs.TFA_REWARDS_CHEST_VAULT_1_CLOSED)) { "chest after the braziers" }
        assertNotNull(npc(Npcs.TFA_REWARDS_CHEST_VAULT_2_CLOSED), "b's chest")
        ra.teleport(run.at(PLATFORM)) // the chests are out of view from the entrance
        tick()
        a.input(ClientCommand.OpNpc(1, chest.index, false))
        tick(WALK)
        assertEquals(Npcs.TFA_REWARDS_CHEST_VAULT_1_OPEN, chest.type, "chest shattered into a rift")
        assertTrue(ra.isOpen(Interfaces.TFA_CHESTS), "loot interface")

        // The vault rift leaves to the lobby.
        ra.teleport(run.at(TfaRaid.VAULT_ENTRY))
        tick()
        val rift = requireNotNull(npc(Npcs.TFA_VAULT_EXIT_SMALL))
        a.input(ClientCommand.OpNpc(1, rift.index, false))
        tick(WALK)
        assertEquals(TfaRaid.LOBBY_DOOR, a.coord)
    }

    private fun clear() = TfaFlow.cleared(ra, run)

    private fun near(c: CoordGrid, to: CoordGrid) =
        c.level == to.level && maxOf(kotlin.math.abs(c.x - to.x), kotlin.math.abs(c.z - to.z)) <= NEAR

    /** Ticks with both players kept alive: the rooms' bosses fight, but this test only walks the doors. */
    private fun tick(n: Int = 1) {
        repeat(n) {
            for (p in listOf(a, b)) PlayerRef.of(p.player).hitpoints = KEEP_ALIVE
            s.tick()
        }
    }

    private fun door(id: Int, template: CoordGrid, op: Int) {
        val at = run.at(template)
        a.input(ClientCommand.OpLoc(op, id, at.x, at.z, false))
        tick(WALK)
    }

    private fun loc(id: Int, template: CoordGrid) = door(id, template, op = 1)

    private fun assertRoom(room: String, entry: CoordGrid) {
        assertEquals(room, run.roomsTaken.last()) { "at ${a.coord} ${a.messages.takeLast(3)}" }
        // The room's boss fights back and auto-retaliate walks players off the entry tile: near it is enough.
        assertTrue(near(a.coord, run.at(entry)), "a at $room: ${a.coord}")
        assertTrue(near(b.coord, run.at(entry)), "b moved with the party: ${b.coord}")
    }

    private fun npc(type: Int): NpcRef? =
        requireNotNull(run.instance).let { inst -> ra.npcsWithin(a.coord, NPC_RADIUS).firstOrNull { it.type == type && it.coord in inst } }

    private companion object {
        val DOOR = CoordGrid(4123, 5562)
        val PLATFORM = CoordGrid(4127, 5797, 2)

        /** Ticks to walk to a loc and use it (the longest walk is across the nexus). */
        const val WALK = 45
        const val OP_TRAVEL = 3
        const val NPC_RADIUS = 40
        const val KEEP_ALIVE = 255
        const val NEAR = 10
    }
}
