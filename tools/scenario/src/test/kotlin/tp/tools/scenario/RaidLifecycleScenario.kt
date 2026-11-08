package tp.tools.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.content.raids.ContributionRules
import tp.content.raids.DeathPolicy
import tp.content.raids.Raids
import tp.content.raids.RecordsDef
import tp.content.raids.RewardResult
import tp.content.raids.RoomDef
import tp.content.raids.RoomKind
import tp.content.raids.RunState
import tp.content.raids.WipePolicy
import tp.content.raids.raid
import tp.plugin.DeathPolicyRequest
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Raids brief section 8 test 1: a test-only fixture raid built from a real template square (Lumbridge castle, map
 * square 50, 50) with a party of three runs the whole framework lifecycle (docs/mechanics/raids/common.md).
 */
class RaidLifecycleScenario {
    private val courtyard = CoordGrid(3222, 3218)

    private val fixture =
        raid("fixture") {
            partySize = 1..3
            squares = listOf(50 to 50)
            exit = CoordGrid(3222, 3230)
            deaths = DeathPolicy.WaitUntilRoomEnds(WipePolicy.EjectWithGraves)
            raidOnlyItems = setOf(Objs.CABBAGE)
            claimInv = Invs.TFA_CHESTS
            contribution = ContributionRules(normalisedRooms = true, deathPenalty = { _, run -> 10.0 / run.scaling.partySize })
            rewards = { snap, rng ->
                snap.members.filterValues { it.third }.mapValues { (_, v) ->
                    RewardResult(listOf(Objs.COINS to 1 + v.first.toInt() + rng.next(PURSE, "purse")))
                }
            }
            records =
                RecordsDef(
                    kcVarp = Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE,
                    kcMessage = { "Your completed fixture count is: <col=ff0000>$it</col>." },
                    timeMessage = { t, _, newBest -> "Fixture time: $t" + if (newBest) " (new personal best)" else "" },
                    pbKey = "raids.fixture.pb",
                )
            room(RoomDef("a", RoomKind.ENCOUNTER, CoordGrid(3222, 3218), contribution = 100))
            room(RoomDef("b1", RoomKind.ENCOUNTER, CoordGrid(3225, 3218), contribution = 100))
            room(RoomDef("b2", RoomKind.ENCOUNTER, CoordGrid(3222, 3221), contribution = 100, waitAt = CoordGrid(3220, 3221)))
            room(RoomDef("reward", RoomKind.REWARD, CoordGrid(3220, 3220)))
            slot("a")
            slot("b1", "b2")
            slot("reward")
        }

    /** Plays the fixture end to end and returns its state hash (for the replay check). */
    @Suppress("LongMethod") // one scripted run, step by step, as the brief's test 1 lists them
    private fun play(seed: Long, check: Boolean): String {
        val s = Scenario(seed = seed)
        val a = s.player("a", courtyard)
        val b = s.player("b", courtyard)
        val c = s.player("c", courtyard)
        s.tick()
        val ra = PlayerRef.of(a.player)
        val raids = Raids.of(ra)
        raids.register(fixture)
        val party = requireNotNull(raids.createParty(fixture, "a"))
        assertTrue(party.join("b") && party.join("c"))
        assertNull(Raids.start(ra, party))
        val run = requireNotNull(raids.runOf("a"))
        s.tick() // the instance attaches in this tick's pre-tick, then everyone enters room a
        val inst = requireNotNull(run.instance)
        if (check) {
            assertTrue(a.coord in inst && b.coord in inst && c.coord in inst, "members inside the instance")
            assertEquals(run.at(CoordGrid(3222, 3218)), a.coord, "room a entry, template-mapped")
            assertEquals(listOf("a"), run.roomsTaken)
        }

        // c leaves holding a raid-only item: scaling stays locked, the item is removed, c is at the exit.
        val rc = PlayerRef.of(c.player)
        rc.transaction("test") { add(Invs.INV, Objs.CABBAGE, 1) }
        Raids.leave(rc, run)
        s.tick()
        if (check) {
            assertEquals(3, run.scaling.partySize, "scaling locked after a member leaves")
            assertEquals(0L, c.player.let { PlayerRef.of(it).inv(Invs.INV).total(Objs.CABBAGE) }, "raid-only item removed")
            assertEquals(fixture.exit, c.coord)
        }

        // Damage on a run NPC feeds the ledger; room a is normalised to 100 by damage share.
        val npc = Raids.spawn(ra, run, NPC, CoordGrid(3224, 3219))
        val rb = PlayerRef.of(b.player)
        ra.events.publish(HitEvent(npc, ra, 30, HitStyle.MELEE))
        ra.events.publish(HitEvent(npc, rb, 10, HitStyle.MELEE))
        Raids.roomCleared(ra, run)
        if (check) {
            assertEquals(75.0, requireNotNull(run.member("a")).points, 1e-9)
            assertEquals(25.0, requireNotNull(run.member("b")).points, 1e-9)
            assertTrue(run.state is RunState.BetweenRooms, "choice slot waits for the party")
            assertFalse(Raids.advance(ra, run, "reward"), "a room not on offer is refused")
        }
        assertTrue(Raids.advance(ra, run, "b2"))
        if (check) assertEquals(listOf("a", "b2"), run.roomsTaken, "room order")

        // a dies while b lives: items kept, waits at the room's wait tile, death penalty applied.
        val death = DeathPolicyRequest(ra, null)
        ra.events.publish(death)
        if (check) {
            assertTrue(death.handled && death.keepItems, "wait-until-room-ends keeps items")
            assertEquals(run.at(CoordGrid(3220, 3221)), death.respawnAt)
            assertEquals(1, requireNotNull(run.member("a")).deaths)
        }

        // b disconnects and rejoins.
        b.session.disconnectAfterInput()
        s.tick(2)
        if (check) assertFalse(requireNotNull(run.member("b")).online, "b offline")
        val b2 = s.player("b", run.at(CoordGrid(3222, 3221)))
        s.tick()
        if (check) assertTrue(requireNotNull(run.member("b")).online, "b rejoined")

        // Room b2 cleared -> the fixed reward slot starts; completion computes rewards once.
        Raids.roomCleared(ra, run)
        Raids.complete(ra, run)
        val rewardA = requireNotNull(run.member("a")).reward
        if (check) {
            assertNotNull(rewardA)
            assertNull(requireNotNull(run.member("c")).reward, "the leaver gets nothing")
            val claim = ra.inv(Invs.TFA_CHESTS)
            val coins = claim.total(Objs.COINS)
            assertTrue(coins > 0, "reward paid into the claim inventory")
            Raids.complete(ra, run) // a second completion does nothing
            assertEquals(coins, ra.inv(Invs.TFA_CHESTS).total(Objs.COINS), "rewards computed and paid once")
            assertTrue(a.messages.any { it.startsWith("Your completed fixture count is: <col=ff0000>") }, "KC message")
            assertEquals(1, a.player.vars[Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE], "KC varp")
        }

        // Everyone leaves: the run closes and its instance, NPCs and space are freed.
        val rb2 = PlayerRef.of(b2.player)
        Raids.leave(ra, run)
        Raids.leave(rb2, run)
        s.tick()
        if (check) {
            assertEquals(RunState.Closed, run.state)
            assertTrue(inst.destroyed, "instance destroyed")
            assertNull(Raids.of(ra).run(run.id.value), "run released")
            assertTrue(npc.isGone(), "run NPC removed")
        }
        return run.rng.rolls.joinToString() + run.members.values.joinToString { "${it.name}:${it.points}:${it.deaths}" }
    }

    @Test
    fun `raid lifecycle with a party of three`() {
        play(seed = 11, check = true)
    }

    @Test
    fun `replay of the same seed gives the same run`() {
        assertEquals(play(seed = 21, check = false), play(seed = 21, check = false))
    }

    private fun tp.plugin.NpcRef.isGone(): Boolean = !isInWorld

    private companion object {
        /** A plain cache NPC (man) as the fixture's room NPC. */
        const val NPC = 3106
        const val PURSE = 50
    }
}
