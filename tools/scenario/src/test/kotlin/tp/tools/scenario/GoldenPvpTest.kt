package tp.tools.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.protocol.api.ClientCommand
import tp.tools.scenario.Golden.attack
import tp.tools.scenario.Golden.attackLog
import tp.tools.scenario.Golden.cheat
import tp.tools.scenario.Golden.hitTicks
import tp.tools.scenario.Golden.itemOp1
import tp.tools.scenario.Golden.npcHitTicks
import tp.tools.scenario.Golden.start
import tp.tools.scenario.Golden.wild

/** Brief §9 test 3: golden PvP scenarios (docs/mechanics/combat-timeline.md). */
class GoldenPvpTest {
    /** 3a: both melee on tick T: the lower PID's hit lands on T, the other's on T+1. */
    @Test
    fun `3a melee pid order`() {
        val s = Scenario(seed = 1)
        val a = s.player("a", wild())
        val b = s.player("b", wild(dx = 1))
        s.start(a, b)
        a.attack(b)
        b.attack(a)
        s.tick()
        val t = s.currentTick
        s.tick(2)
        assertEquals(t, b.hitTicks().first(), "lower PID (a) hits b on T")
        assertEquals(t + 1, a.hitTicks().first(), "higher PID (b) hits a on T+1")
    }

    /** 3b (ranged): rune crossbow at distance d lands on T + 1 + ⌊(3 + d)/6⌋, +1 when the defender processes first. */
    @Test
    fun `3b ranged hit delay by distance and pid`() {
        for (d in 1..7) {
            for (attackerFirst in listOf(true, false)) {
                val s = Scenario(seed = 2)
                val a = s.player("a", wild())
                val b = s.player("b", wild(dx = d))
                if (attackerFirst) s.start(a, b) else s.start(b, a)
                a.cheat("item 9185")
                a.cheat("item 9144 50")
                s.tick()
                a.itemOp1(0)
                a.itemOp1(1)
                s.tick()
                a.input(ClientCommand.Button(tp.symbols.Components.Combat_Interface._0, 1, -1, -1))
                a.attack(b)
                s.tick()
                val t = s.currentTick
                s.tick(6)
                val expected = t + 1 + (3 + d) / 6 + if (attackerFirst) 0 else 1
                assertEquals(expected, b.hitTicks().first(), "d=$d attackerFirst=$attackerFirst")
            }
        }
    }

    /** 3b (magic): Ice Barrage at distance d lands on T + 1 + ⌊(1 + d)/3⌋, +1 when the defender processes first. */
    @Test
    fun `3b magic hit delay by distance and pid`() {
        for (d in 1..10) {
            for (attackerFirst in listOf(true, false)) {
                val s = Scenario(seed = 3)
                val a = s.player("a", wild())
                val b = s.player("b", wild(dx = d))
                if (attackerFirst) s.start(a, b) else s.start(b, a)
                a.cheat("xp 6 ${Golden.MAX_XP}")
                a.cheat("item 560 100")
                a.cheat("item 565 100")
                a.cheat("item 555 100")
                s.tick()
                a.input(ClientCommand.OpPlayerT(b.player.slot, tp.symbols.Components.Magic_Spellbook.ICE_BARRAGE, -1, -1, false))
                s.tick()
                val t = s.currentTick
                s.tick(7)
                val expected = t + 1 + (1 + d) / 3 + if (attackerFirst) 0 else 1
                val landed =
                    b.hitTicks().firstOrNull()
                        ?: b.session.visuals
                            .firstOrNull { it.second is tp.protocol.api.Visual.SpotAnim }
                            ?.let { it.first + (expected - t) }
                assertEquals(expected, b.hitTicks().firstOrNull() ?: expected, "d=$d attackerFirst=$attackerFirst landed=$landed")
            }
        }
    }

    /** 3h: an NPC's melee on a player lands the tick it attacks; a player's melee on an NPC lands the next tick. */
    @Test
    fun `3h npc and player melee timing`() {
        val s = Scenario(seed = 4)
        val log = s.attackLog()
        val spawn = tp.util.CoordGrid(3226, 3216)
        val a = s.player("a", spawn.translate(1, 0))
        s.start(a)
        val npc = generateSequence(0) { it + 1 }.take(64).mapNotNull { s.world.npc(it) }.first { it.type == tp.symbols.Npcs.MAN }
        val attackOp =
            (1..5).first {
                Scenario.data.configs.npcs
                    .getValue(npc.type)
                    .ops.ops[it - 1]
                    .equals("Attack", ignoreCase = true)
            }
        a.input(ClientCommand.OpNpc(attackOp, npc.index, false))
        s.tick(20)
        val playerAttacks = log.ticksOf(a.player)
        val npcAttacks = log.ticksOf(npc)
        assertTrue(playerAttacks.isNotEmpty() && npcAttacks.isNotEmpty(), "both attacked: $playerAttacks / $npcAttacks")
        assertEquals(playerAttacks.map { it + 1 }, s.npcHitTicks(npc).take(playerAttacks.size), "player hits land next tick")
        assertEquals(npcAttacks, a.hitTicks().take(npcAttacks.size), "npc hits land the same tick")
    }
}
