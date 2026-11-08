package tp.content.raids.tfa

import tp.content.raids.ScalingContext
import tp.plugin.NpcRef

/**
 * TFA9: boss hitpoints scale with the players present (a solo raid scales as two). Each boss's HP table
 * (tfa/<boss>.md) is listed here; NPCs without a table keep their cache stats.
 */
internal object TfaScaling {
    /** Hitpoints by NPC type, indexed by scaled party size (index 0 = 2 players ... index 6 = 8 players). */
    private val tables: Map<Int, IntArray> = emptyMap()

    fun apply(ctx: ScalingContext, npc: NpcRef) {
        val table = tables[npc.type] ?: return
        val size = ctx.scaledSize.coerceIn(TfaRaid.MIN_SCALE, TfaRaid.MAX_PARTY)
        val levels = IntArray(STATS) { npc.baseLevel(it) }
        levels[HITPOINTS] = table[size - TfaRaid.MIN_SCALE]
        npc.overrideStats(levels)
        npc.hitpoints = levels[HITPOINTS]
    }

    /** NPC stat order (attack, defence, strength, hitpoints, ranged, magic). */
    private const val STATS = 6
    private const val HITPOINTS = 3
}
