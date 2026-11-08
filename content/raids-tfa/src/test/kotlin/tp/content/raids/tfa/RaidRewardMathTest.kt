package tp.content.raids.tfa

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Raids brief section 8 test 2: reward math against the wiki's published numbers (TFA rows; others join per raid). */
class RaidRewardMathTest {
    @Test
    fun `tfa unique chance by party size`() {
        // Chest (The Fractured Archive): overall rate table.
        val table = mapOf(1 to 14.0, 2 to 14.0, 3 to 13.46, 4 to 12.96, 5 to 12.50, 6 to 12.07, 7 to 11.67, 8 to 11.29)
        for ((players, denominator) in table) {
            assertEquals(denominator, 1.0 / TfaRewards.uniqueChance(players), 0.005, "$players players")
        }
    }

    @Test
    fun `tfa contribution and deaths`() {
        assertEquals(700, TfaRewards.RAID_CONTRIBUTION)
        assertEquals(35.0, TfaRewards.deathPenalty(2), 1e-9)
        assertEquals(8.75, TfaRewards.deathPenalty(8), 1e-9)
        val chances = TfaRewards.recipientChances(mapOf("a" to 490.0, "b" to 210.0))
        assertEquals(0.70, chances.getValue("a"), 1e-9)
        assertEquals(0.30, chances.getValue("b"), 1e-9)
    }

    @Test
    fun `tfa unique weights match the published table and the cache rows`() {
        assertEquals(15, TfaRewards.UNIQUE_WEIGHTS.values.sum())
        assertEquals(listOf(5, 4, 3, 2, 1), TfaRewards.UNIQUE_WEIGHTS.values.toList())
        assertEquals(TfaRewards.UNIQUE_WEIGHTS.keys, TfaRewards.UNIQUE_ROWS.values.toSet())
    }
}
