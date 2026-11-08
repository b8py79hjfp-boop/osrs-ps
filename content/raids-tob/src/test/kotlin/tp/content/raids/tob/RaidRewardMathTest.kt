package tp.content.raids.tob

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Raids brief section 8 test 2, ToB rows: unique chance by mode, 14 MVP points, the 19 / 18 weight tables. */
class RaidRewardMathTest {
    @Test
    fun `tob unique chance by mode`() {
        assertEquals(9.1, 1.0 / TobRewards.baseUniqueChance("normal"), 1e-9)
        assertEquals(7.7, 1.0 / TobRewards.baseUniqueChance("hard"), 1e-9)
        assertEquals(0.0, TobRewards.baseUniqueChance("entry"))
    }

    @Test
    fun `tob mvp points and weights`() {
        assertEquals(14, TobRewards.MVP_POINTS.values.sum())
        assertEquals(19, TobRewards.uniqueWeights("normal").values.sum())
        assertEquals(18, TobRewards.uniqueWeights("hard").values.sum())
    }
}
