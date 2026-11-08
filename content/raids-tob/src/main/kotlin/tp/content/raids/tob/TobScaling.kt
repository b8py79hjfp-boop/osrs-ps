package tp.content.raids.tob

import tp.content.raids.ScalingContext
import tp.plugin.NpcRef

/**
 * HP scaling by party size (tob.md TB10, TB11). Normal and Hard: the cache holds the 5-player HP; 87.5% for four and
 * 75% for three or fewer (OPEN-TB3: floored). Entry: the cache holds the 1-player HP; x1.9, x2.7, x3.4, x4.0 for two
 * to five (the 1 Mar 2023 cumulative +90 / +80 / +70 / +60%).
 */
internal object TobScaling {
    /** Per mille of the cache HP by party size 1..5. */
    private val NORMAL = intArrayOf(750, 750, 750, 875, 1000)
    private val ENTRY = intArrayOf(1000, 1900, 2700, 3400, 4000)

    fun apply(ctx: ScalingContext, npc: NpcRef) {
        if (npc.type in UNSCALED) return
        val n = ctx.partySize.coerceIn(1, TobRaid.MAX_PARTY)
        val permille = if (ctx.mode == TobRaid.ENTRY) ENTRY[n - 1] else NORMAL[n - 1]
        val levels = IntArray(STATS) { npc.baseLevel(it) }
        val hp = maxOf(1, levels[HITPOINTS] * permille / PER_MILLE)
        levels[HITPOINTS] = hp
        npc.overrideStats(levels)
        npc.hitpoints = hp
    }

    /** NPCs whose HP is not scaled: blood spawns and webs (fixed in the cache, room docs). */
    private val UNSCALED = TobNpcs.UNSCALED

    private const val STATS = 6
    private const val HITPOINTS = 3
    private const val PER_MILLE = 1000
}
