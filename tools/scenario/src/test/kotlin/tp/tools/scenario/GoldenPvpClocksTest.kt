package tp.tools.scenario

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tp.protocol.api.ClientCommand
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varps
import tp.tools.scenario.Golden.attack
import tp.tools.scenario.Golden.button
import tp.tools.scenario.Golden.cheat
import tp.tools.scenario.Golden.hitTicks
import tp.tools.scenario.Golden.hits
import tp.tools.scenario.Golden.itemOp1
import tp.tools.scenario.Golden.start
import tp.tools.scenario.Golden.wild

/** Brief §9 test 3c–3g: consumption clocks, freezes, prayer flicks, switches (combat-timeline.md, consumables.md). */
class GoldenPvpClocksTest {
    private fun duel(seed: Long, distance: Int = 1): Triple<Scenario, ScenarioPlayer, ScenarioPlayer> {
        val s = Scenario(seed = seed)
        val a = s.player("a", wild())
        val b = s.player("b", wild(dx = distance))
        s.start(a, b)
        b.button(Components.Combat_Interface.RETALIATE) // b doesn't fight back
        return Triple(s, a, b)
    }

    private fun ScenarioPlayer.invObj(slot: Int): Int = player.world.inventory(player, Invs.INV).obj(slot)

    /** 3c: shark + brew + karambwan in one tick: all heal, attack clock +5, food blocked for 3 ticks. */
    @Test
    fun `3c combo eat`() {
        val (s, a, b) = duel(seed = 10)
        a.cheat("xp 3 ${Golden.MAX_XP}")
        a.cheat("hp 10")
        a.cheat("item ${Objs.SHARK}")
        a.cheat("item ${Objs._4DOSEPOTIONOFSARADOMIN}")
        a.cheat("item ${Objs.TBWT_COOKED_KARAMBWAN}")
        s.tick()
        a.attack(b)
        s.tick()
        val t = s.currentTick
        a.itemOp1(0)
        a.itemOp1(1)
        a.itemOp1(2)
        a.attack(b) // eating interrupted the attack (I7); click again
        s.tick()
        // 20 + (⌊99 × 15/100⌋ + 2 = 16) + 18
        assertEquals(10 + 20 + 16 + 18, a.player.hitpoints)
        s.tick(12)
        assertEquals(listOf(t, t + 4 + 5), b.hitTicks().take(2), "attack clock +3 (shark) +2 (karambwan); brew adds nothing")

        // Food blocked for 3 ticks after a shark.
        val (s2, c, _) = duel(seed = 11)
        repeat(2) { c.cheat("item ${Objs.SHARK}") }
        s2.tick()
        c.itemOp1(0)
        s2.tick()
        for (i in 1..2) {
            c.itemOp1(1)
            s2.tick()
            assertEquals(Objs.SHARK, c.invObj(1), "second shark blocked $i tick(s) later")
        }
        c.itemOp1(1)
        s2.tick()
        assertEquals(-1, c.invObj(1), "second shark eaten 3 ticks later")
    }

    /** 3d: Ice Barrage freezes 32 ticks, then 5 ticks of immunity; the freeze breaks beyond 10 tiles. */
    @Test
    fun `3d ice barrage freeze immunity and break`() {
        val (s, a, b) = duel(seed = 12, distance = 5)
        a.cheat("xp 6 ${Golden.MAX_XP}")
        a.cheat("spellbook 1") // Ancient Magicks
        b.cheat("xp 3 ${Golden.MAX_XP}") // keeps b inside a's combat bracket
        for (rune in intArrayOf(Objs.DEATHRUNE, Objs.BLOODRUNE, Objs.WATERRUNE)) a.cheat("item $rune 500")
        s.tick()
        val cast = { a.input(ClientCommand.OpPlayerT(b.player.slot, Components.Magic_Spellbook.ICE_BARRAGE, -1, -1, false)) }
        cast()
        s.tick()
        val t = s.currentTick
        val land = t + 1 + (1 + 5) / 3
        s.tick((land - s.currentTick).toInt())
        assertEquals(land + 32, b.player.frozenUntil, "frozen for 32 ticks from the application tick")
        // Movement blocked until land + 32.
        val start = b.player.coord
        b.input(ClientCommand.MoveClick(start.x, start.z + 3, ctrl = false, minimap = false))
        while (s.currentTick < land + 31) {
            s.tick()
            if (s.currentTick < land + 32) assertEquals(start, b.player.coord, "still frozen at ${s.currentTick}")
        }
        // Immunity (separate duel, target standing still): ends = land + 32; a barrage landing at ends + 2 is ignored,
        // one landing after ends + 5 freezes again (the next cast is at ends + 7: spell speed 5).
        val (s3, e, f) = duel(seed = 12, distance = 5)
        e.cheat("xp 6 ${Golden.MAX_XP}")
        e.cheat("spellbook 1")
        f.cheat("xp 3 ${Golden.MAX_XP}")
        for (rune in intArrayOf(Objs.DEATHRUNE, Objs.BLOODRUNE, Objs.WATERRUNE)) e.cheat("item $rune 500")
        s3.tick()
        val barrage = { e.input(ClientCommand.OpPlayerT(f.player.slot, Components.Magic_Spellbook.ICE_BARRAGE, -1, -1, false)) }
        val travel = 1 + (1 + 5) / 3
        barrage()
        s3.tick()
        val ends = s3.currentTick + travel + 32
        while (s3.currentTick < ends - travel + 2 - 1) s3.tick()
        barrage() // lands at ends + 2
        s3.tick(travel + 1)
        assertTrue(!f.player.isFrozen, "immune at ends + 2")
        // Re-cast every 5 ticks until one lands (a splash doesn't freeze): it freezes from its landing tick.
        var landed = -1L
        for (attempt in 0 until 5) {
            barrage()
            s3.tick(travel + 1)
            val hit = f.hitTicks().lastOrNull { it > ends + 5 }
            if (hit != null) {
                landed = hit
                break
            }
            s3.tick(5 - travel - 1)
        }
        assertTrue(landed > ends + 5, "a barrage landed after immunity")
        assertEquals(landed + 32, f.player.frozenUntil, "frozen again once immunity is over")

        // Break: caster more than 10 tiles away ends the freeze.
        val (s2, c, d) = duel(seed = 13, distance = 5)
        c.cheat("xp 6 ${Golden.MAX_XP}")
        c.cheat("spellbook 1")
        d.cheat("xp 3 ${Golden.MAX_XP}")
        for (rune in intArrayOf(Objs.DEATHRUNE, Objs.BLOODRUNE, Objs.WATERRUNE)) c.cheat("item $rune 500")
        s2.tick()
        c.input(ClientCommand.OpPlayerT(d.player.slot, Components.Magic_Spellbook.ICE_BARRAGE, -1, -1, false))
        s2.tick(4)
        assertTrue(d.player.isFrozen, "frozen")
        c.input(ClientCommand.MoveClick(c.player.coord.x - 7, c.player.coord.z, ctrl = true, minimap = false))
        s2.tick(6)
        assertTrue(!d.player.isFrozen, "freeze broken once the caster is more than 10 tiles away")
    }

    /** 3e: protect prayer on at T, off at T+1: a hit checked on T is protected and no prayer drains. */
    @Test
    fun `3e prayer flick`() {
        val protectMelee =
            Scenario.data.configs.enums[4956]!!
                .intValues.values
                .first {
                    Scenario.data.configs.objs[it]!!
                        .params
                        .string(1752) == "Protect from Melee"
                }.let {
                    Scenario.data.configs.objs[it]!!
                        .params
                        .int(1751)!!
                }
        for (seed in 20L..29L) {
            val (s, a, b) = duel(seed = seed)
            a.cheat("xp 0 ${Golden.MAX_XP}")
            a.cheat("xp 2 ${Golden.MAX_XP}")
            b.cheat("xp 5 50339") // Prayer 43
            b.cheat("xp 3 ${Golden.MAX_XP}") // bracket
            b.cheat("xp 1 ${Golden.MAX_XP}")
            s.tick()
            val prayer = b.player.stats.level(5)
            a.attack(b)
            b.button(protectMelee)
            s.tick()
            val t = s.currentTick
            b.button(protectMelee)
            s.tick(3)
            val hit = b.hits().first { it.first == t }.second
            // Unarmed, Strength 99: max ⌊(107 × 64 + 320) / 640⌋ = 11, protected ⌊11 × 6/10⌋ = 6.
            assertTrue(hit.value <= 6, "seed $seed: protected hit ${hit.value}")
            assertEquals(prayer, b.player.stats.level(5), "seed $seed: no prayer drained")
        }
    }

    /** 3f: inventory weapon switch + special + attack in one tick: new weapon's spec and speed, energy deducted. */
    @Test
    fun `3f switch spec attack`() {
        val (s, a, b) = duel(seed = 30)
        a.cheat("xp 0 ${Golden.MAX_XP}") // the whip, dragon dagger and AGS need Attack 70 / 60 / 75 (EQ1)
        a.cheat("item ${Objs.ABYSSAL_WHIP}")
        a.cheat("item ${Objs.DRAGON_DAGGER}")
        a.cheat("item ${Objs.AGS}")
        s.tick()
        a.itemOp1(0) // wield whip
        s.tick()
        a.attack(b)
        s.tick()
        val t = s.currentTick
        s.tick(3) // whip clock: next attack at t + 4
        val ddsSlot = (0 until 28).first { a.invObj(it) == Objs.DRAGON_DAGGER }
        a.itemOp1(ddsSlot)
        a.button(Components.Combat_Interface.SPECIAL_ATTACK)
        a.attack(b)
        s.tick()
        assertEquals(t + 4, s.currentTick)
        assertEquals(listOf(t, t + 4, t + 4), b.hitTicks().take(3), "dds special: two hits on the switch tick")
        assertEquals(750, a.player.vars[Varps.SA_ENERGY], "25% energy deducted")
        // Switch to the AGS (speed 6) and spec: lands now, the next attack waits 6.
        s.tick(3)
        val agsSlot = (0 until 28).first { a.invObj(it) == Objs.AGS }
        a.itemOp1(agsSlot)
        a.button(Components.Combat_Interface.SPECIAL_ATTACK)
        a.attack(b)
        s.tick()
        val u = s.currentTick
        s.tick(7)
        assertEquals(listOf(u, u + 6), b.hitTicks().drop(3).take(2), "AGS spec on the switch tick, then AGS speed 6")
        assertEquals(250, a.player.vars[Varps.SA_ENERGY], "50% more energy deducted")
    }

    /** 3g: eating while the attack clock is in the future delays the next attack by 3; eating while idle doesn't. */
    @Test
    fun `3g eat delay`() {
        val (s, a, b) = duel(seed = 40)
        a.cheat("item ${Objs.SHARK}")
        s.tick()
        a.attack(b)
        s.tick()
        val t = s.currentTick
        a.itemOp1(0)
        a.attack(b)
        s.tick(9)
        assertEquals(listOf(t, t + 4 + 3), b.hitTicks().take(2), "busy: +3")

        val (s2, c, d) = duel(seed = 41)
        c.cheat("item ${Objs.SHARK}")
        s2.tick()
        c.itemOp1(0)
        s2.tick()
        c.attack(d)
        s2.tick()
        val u = s2.currentTick
        s2.tick(2)
        assertEquals(u, d.hitTicks().first(), "idle: no delay")
    }
}
