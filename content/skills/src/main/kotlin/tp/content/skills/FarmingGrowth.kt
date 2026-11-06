@file:Suppress("MagicNumber") // bit layout of the saved patch state

package tp.content.skills

import tp.plugin.PlayerRef

/**
 * The state of one patch packed into a saved Long (docs/mechanics/farming.md F1): crop (6 bits, 1-based index into
 * [CropTable], 0 = none), [Phase] (3), stage (5), harvest lives (4), compost tier (2) and the watered flag (1).
 */
@JvmInline
internal value class PatchState(val bits: Long) {
    val crop: Int get() = (bits and CROP_MASK).toInt()
    val phase: Phase get() = Phase.entries[((bits shr PHASE_SHIFT) and PHASE_MASK).toInt()]
    val stage: Int get() = ((bits shr STAGE_SHIFT) and STAGE_MASK).toInt()
    val lives: Int get() = ((bits shr LIVES_SHIFT) and LIVES_MASK).toInt()
    val compost: Int get() = ((bits shr COMPOST_SHIFT) and COMPOST_MASK).toInt()
    val watered: Boolean get() = (bits shr WATERED_SHIFT) and 1L == 1L

    fun with(
        crop: Int = this.crop,
        phase: Phase = this.phase,
        stage: Int = this.stage,
        lives: Int = this.lives,
        compost: Int = this.compost,
        watered: Boolean = this.watered,
    ): PatchState = of(crop, phase, stage, lives, compost, watered)

    companion object {
        private const val CROP_MASK = 0x3FL
        private const val PHASE_SHIFT = 6
        private const val PHASE_MASK = 0x7L
        private const val STAGE_SHIFT = 9
        private const val STAGE_MASK = 0x1FL
        private const val LIVES_SHIFT = 14
        private const val LIVES_MASK = 0xFL
        private const val COMPOST_SHIFT = 18
        private const val COMPOST_MASK = 0x3L
        private const val WATERED_SHIFT = 20

        fun of(crop: Int, phase: Phase, stage: Int, lives: Int, compost: Int, watered: Boolean): PatchState =
            PatchState(
                crop.toLong() or
                    (phase.ordinal.toLong() shl PHASE_SHIFT) or
                    (stage.toLong() shl STAGE_SHIFT) or
                    (lives.toLong() shl LIVES_SHIFT) or
                    (compost.toLong() shl COMPOST_SHIFT) or
                    ((if (watered) 1L else 0L) shl WATERED_SHIFT),
            )

        /** A patch nobody has touched: full weeds (docs/mechanics/farming.md F5). */
        val FRESH: PatchState = of(0, Phase.WEEDS, FarmingConst.FULL_WEEDS, 0, 0, false)
    }
}

/** A patch's state and the minute its growth was last brought up to; one per player is reused for every patch. */
internal class Cursor {
    var state: PatchState = PatchState.FRESH
    var anchor: Long = 0
}

/**
 * Lazy growth (docs/mechanics/farming.md F2-F4). Growth ticks fall on every multiple of the cycle length of real time
 * (Wiki "Farming#Growth timing"), so a patch is brought up to date from the minute it was last updated whenever it is looked
 * at, which also covers the time the player was offline.
 */
internal object FarmingGrowth {
    /** Safety bound on ticks processed in one catch-up (the longest crop has 12 cycles, plus weeds and fruit regrowth). */
    private const val MAX_TICKS = 64

    /** Brings [cursor] up to minute [now]; true if anything changed. */
    fun advance(p: PlayerRef, crops: CropTable, cursor: Cursor, now: Long): Boolean {
        var changed = false
        repeat(MAX_TICKS) {
            val crop = crops.at(cursor.state.crop)
            val cycle = cycleOf(cursor.state, crop)
            if (cycle <= 0) return changed
            val due = (cursor.anchor / cycle + 1) * cycle
            if (due > now) return changed
            cursor.anchor = due
            cursor.state = tick(p, cursor.state, crop)
            changed = true
        }
        return changed
    }

    /** Minutes between the growth ticks of a patch in state [st], or 0 if it is not growing. */
    fun cycleOf(st: PatchState, crop: Crop?): Int =
        when (st.phase) {
            Phase.WEEDS -> {
                if (st.stage < FarmingConst.FULL_WEEDS) FarmingConst.WEEDS_MIN else 0
            }

            Phase.GROWING -> {
                if (crop == null) {
                    0
                } else if (st.stage == 0) {
                    crop.firstMin
                } else {
                    crop.cycleMin
                }
            }

            Phase.DISEASED -> {
                if (crop == null) 0 else crop.cycleMin * FarmingConst.DISEASED_CYCLE_FACTOR
            }

            Phase.HARVESTABLE -> {
                if (crop != null && crop.type == PatchType.FRUIT_TREE &&
                    st.stage < FarmingConst.MAX_FRUIT
                ) {
                    FarmingConst.FRUIT_REGROW_MIN
                } else {
                    0
                }
            }

            else -> {
                0
            }
        }

    private fun tick(p: PlayerRef, st: PatchState, crop: Crop?): PatchState =
        when (st.phase) {
            Phase.WEEDS -> st.with(stage = st.stage + 1)
            Phase.DISEASED -> st.with(phase = Phase.DEAD, watered = false)
            Phase.HARVESTABLE -> st.with(stage = st.stage + 1)
            Phase.GROWING -> if (crop == null) st else grow(p, st, crop)
            else -> st
        }

    /** One growth tick of a healthy crop: it may fall ill (stages 1..diseaseCycles), else it moves on a stage. */
    private fun grow(p: PlayerRef, st: PatchState, crop: Crop): PatchState {
        val s = st.stage
        if (s in 1..crop.diseaseCycles && p.random(FarmingConst.DISEASE_DENOMINATOR) < diseaseNumerator(crop, st.compost, st.watered)) {
            return st.with(phase = Phase.DISEASED, watered = false)
        }
        val next = s + 1
        if (next < crop.cycles) return st.with(stage = next, watered = false)
        return when {
            crop.type.isTree -> {
                st.with(phase = Phase.CHECK, stage = crop.cycles, watered = false)
            }

            crop.type.usesLives -> {
                st.with(
                    phase = Phase.HARVESTABLE,
                    stage = 0,
                    lives = FarmingConst.BASE_LIVES + st.compost,
                    watered = false,
                )
            }

            else -> {
                st.with(phase = Phase.HARVESTABLE, stage = 0, lives = 1, watered = false)
            }
        }
    }

    /**
     * The disease chance of [crop] in 128ths: its base, cut by the compost tier and halved when watered, each rounded up (Wiki
     * "Crop disease" table: 18 -> 9 / 4 / 2, 27 -> 14 / 6 / 3 for compost / supercompost / ultracompost).
     */
    fun diseaseNumerator(crop: Crop, compost: Int, watered: Boolean): Int {
        var n = ceilDiv(crop.diseaseBase, FarmingConst.COMPOST_DIVISOR[compost])
        if (watered) n = ceilDiv(n, FarmingConst.WATERED_DIVISOR)
        return maxOf(n, 1)
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}
