package tp.content.raids.toa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tp.symbols.Objs

/** Raids brief section 8 test 2, ToA rows: the worked examples and tables of docs/mechanics/raids/toa/lobby-rewards.md. */
class RaidRewardMathTest {
    @Test
    fun `toa unique chance by raid level and the 55 percent cap`() {
        // TR3: denominators by raid level (integer rule).
        val denominators =
            mapOf(
                0 to 10_500,
                150 to 7_500,
                300 to 4_500,
                350 to 4_040,
                400 to 3_700,
                450 to 3_440,
                500 to 3_280,
                550 to 3_100,
                600 to 2_940,
            )
        for ((level, denominator) in denominators) assertEquals(denominator, ToaLoot.uniqueDenominator(level), "RL $level")
        // Worked examples: solo C = 20,000 at RL 300; four players at RL 400 (T = 80,000); RL 0 with T = 10,500.
        assertEquals(0.04444, ToaLoot.uniqueChance(20_000, 300), 1e-5)
        assertEquals(0.2162, ToaLoot.uniqueChance(80_000, 400), 1e-4)
        assertEquals(0.01, ToaLoot.uniqueChance(10_500, 0), 1e-9)
        // Cap: 55% needs 203,500 points at RL 400, and more adds nothing.
        assertEquals(0.55, ToaLoot.uniqueChance(203_500, 400), 1e-9)
        assertEquals(0.55, ToaLoot.uniqueChance(500_000, 400), 1e-9)
        assertEquals(0.0, ToaLoot.uniqueChance(0, 400))
    }

    @Test
    fun `toa unique weights at the anchors and one band`() {
        // TR7 / TR9 table.
        val expected =
            mapOf(
                0 to (70 to 70),
                300 to (70 to 70),
                325 to (65 to 65),
                350 to (60 to 60),
                355 to (58 to 59),
                375 to (50 to 55),
                400 to (40 to 50),
            )
        for ((level, weights) in expected) {
            assertEquals(weights.first, ToaLoot.fangWeight(level), "fang at $level")
            assertEquals(weights.second, ToaLoot.lightbearerWeight(level), "lightbearer at $level")
        }
        assertEquals(39 to 40, ToaLoot.fangWeight(455) to ToaLoot.lightbearerWeight(455))
        assertEquals(30 to 35, ToaLoot.fangWeight(600) to ToaLoot.lightbearerWeight(600))
        val totals = mapOf(0 to 240, 310 to 236, 350 to 220, 400 to 190, 450 to 180, 500 to 165, 600 to 165)
        for ((level, total) in totals) assertEquals(total, ToaLoot.uniqueWeights(level).sumOf { it.second }, "total at $level")
        // TR10: the Fang and Lightbearer need level 50, the others 150.
        assertEquals(50, ToaLoot.uniqueGate(Objs.OSMUMTENS_FANG))
        assertEquals(150, ToaLoot.uniqueGate(Objs.TUMEKENS_SHADOW_UNCHARGED))
    }

    @Test
    fun `toa common quantities and the dung threshold`() {
        // TR13 worked example, C = 20,000.
        val c = 20_000
        assertEquals(23_000, ToaLoot.commonQuantity(c, 1, 300))
        assertEquals(1_150, ToaLoot.commonQuantity(c, 20, 300))
        assertEquals(575, ToaLoot.commonQuantity(c, 40, 300))
        assertEquals(255, ToaLoot.commonQuantity(c, 90, 300))
        assertEquals(127, ToaLoot.commonQuantity(c, 180, 300))
        assertEquals(75, ToaLoot.commonQuantity(c, 300, 300))
        assertEquals(27_000, ToaLoot.commonQuantity(c, 1, 400))
        assertEquals(299, ToaLoot.commonQuantity(c, 90, 400))
        assertEquals(35_000, ToaLoot.commonQuantity(c, 1, 600))
        assertEquals(15_000, ToaLoot.commonQuantity(c, 1, 0), "Entry x0.75")
        assertEquals(750, ToaLoot.commonQuantity(c, 20, 0))
        assertEquals(20_000, ToaLoot.commonQuantity(c, 1, 200), "Normal below 300 is unscaled")
        // At C = 1,500 the smallest entries are 1.
        assertEquals(1, ToaLoot.commonQuantity(1_500, 1_100, 300))
        assertEquals(1, ToaLoot.commonQuantity(1_500, 7_500, 300))
        // TR6: fossilised dung below 1,500 Total (reading A).
        assertEquals(true, ToaLoot.dungApplies(1_499.0, 0))
        assertEquals(false, ToaLoot.dungApplies(1_500.0, 0))
    }

    @Test
    fun `toa pet, tertiary and points rows`() {
        // TR21: denominators and the worked example (RL 300, C = 20,000 is 1/700).
        val pet =
            mapOf(
                0 to 350_000,
                150 to 245_000,
                300 to 140_000,
                400 to 70_000,
                450 to 58_800,
                500 to 46_900,
                550 to 35_000,
                600 to 35_000,
            )
        for ((level, denominator) in pet) assertEquals(denominator, ToaLoot.petDenominator(level), "pet at $level")
        assertEquals(1.0 / 700, ToaLoot.petChance(20_000, 300), 1e-9)
        // TR18: thread 3/10 at 15 kills, flat 1/50 after the first; any jewel 12/50 at 75 kills.
        assertEquals(0.1, ToaLoot.threadChance(0, false), 1e-9)
        assertEquals(0.3, ToaLoot.threadChance(15, false), 1e-9)
        assertEquals(0.3, ToaLoot.threadChance(40, false), 1e-9)
        assertEquals(0.02, ToaLoot.threadChance(0, true), 1e-9)
        assertEquals(0.08, ToaLoot.jewelChance(0), 1e-9)
        assertEquals(0.24, ToaLoot.jewelChance(75), 1e-9)
        // TR20: 1% per 2,000 points, at most 25%.
        assertEquals(0.1, ToaLoot.clueChance(20_000), 1e-9)
        assertEquals(0.25, ToaLoot.clueChance(100_000), 1e-9)
        // TA35 / TA37 / TA39: the 5,000 start is not contribution; a death costs 20% with a 1,000 minimum.
        assertEquals(20_000, ToaPoints.contribution(25_000.0))
        assertEquals(0, ToaPoints.contribution(3_000.0))
        assertEquals(4_000.0, ToaPoints.deathPenaltyOf(20_000.0))
        assertEquals(1_000.0, ToaPoints.deathPenaltyOf(3_000.0))
        // TA36: multipliers in tenths (Ba-Ba 2x, Zebak 1.5x, baboons 1.2x, scarabs 0.5x, the cores 0x, Akkha 1x).
        assertEquals(listOf(20, 15, 12, 5, 0, 10), listOf(11778, 11730, 11709, 11724, 11770, 11789).map(ToaPoints::multiplierTenths))
    }
}
