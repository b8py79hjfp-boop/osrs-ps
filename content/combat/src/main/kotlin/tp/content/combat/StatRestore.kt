package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId

/**
 * Stat restoration (brief §8; OSRS Wiki "Temporary skill boost", "Stat restoration"): boosted and drained stats move 1
 * level per 100 ticks (Preserve: boosts every 150), checked every 25 ticks on a continuous cycle; Hitpoints regenerate
 * 1 per 100 ticks; Rapid Restore doubles drained-stat restoration, Rapid Heal doubles Hitpoints regeneration. Prayer
 * doesn't restore. OPEN-R1: combat and non-combat skills use separate cycles on live; one cycle here.
 */
public class StatRestore : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { timer(RESTORE, CHECK_INTERVAL, soft = true) { check() } }
    }

    private fun PlayerRef.check() {
        val step = (this[STEP] ?: 0) + 1
        this[STEP] = step
        val restoreEvery = if (hasPrayer("Rapid Restore")) FAST_STEPS else NORMAL_STEPS
        val boostEvery = if (hasPrayer("Preserve")) PRESERVE_STEPS else NORMAL_STEPS
        val healEvery = if (hasPrayer("Rapid Heal")) FAST_STEPS else NORMAL_STEPS
        for (stat in 0 until STAT_COUNT) {
            if (stat == Stat.PRAYER) continue
            val current = level(stat)
            val base = baseLevel(stat)
            when {
                stat == Stat.HITPOINTS && current < base && step % healEvery == 0 -> hitpoints = current + 1
                stat == Stat.HITPOINTS && current > base && step % NORMAL_STEPS == 0 -> hitpoints = current - 1
                stat == Stat.HITPOINTS -> Unit
                current < base && step % restoreEvery == 0 -> setLevel(stat, current + 1)
                current > base && step % boostEvery == 0 -> setLevel(stat, current - 1)
            }
        }
    }

    private companion object {
        val RESTORE = TimerId("stat_restore")
        val STEP = AttrKey<Int>("stat_restore_step")

        /** Checked every 25 ticks (brief §8). */
        const val CHECK_INTERVAL = 25

        /** 100 ticks = 4 checks; Preserve 150 = 6; Rapid Restore / Rapid Heal 50 = 2. */
        const val NORMAL_STEPS = 4
        const val PRESERVE_STEPS = 6
        const val FAST_STEPS = 2
        const val STAT_COUNT = 24
    }
}
