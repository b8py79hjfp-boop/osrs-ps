package tp.content.combat

import tp.plugin.NpcRef
import tp.plugin.SharedAttrs

/** Raid scaling's per-mille multipliers on an NPC's rolls and damage (SharedAttrs.RAID_*_PERMILLE; ToA TA31). */
internal object RaidScale {
    private const val PER_MILLE = 1000L

    fun defence(npc: NpcRef, roll: Int): Int = scale(roll, npc[SharedAttrs.RAID_DEFENCE_PERMILLE])

    fun accuracy(npc: NpcRef, roll: Int): Int = scale(roll, npc[SharedAttrs.RAID_ACCURACY_PERMILLE])

    /** A boss script's or the NPC's own max hit, scaled (floored). */
    fun damage(npc: NpcRef, maxHit: Int): Int = scale(maxHit, npc[SharedAttrs.RAID_DAMAGE_PERMILLE])

    private fun scale(value: Int, permille: Int?): Int = if (permille == null) value else (value * permille.toLong() / PER_MILLE).toInt()
}
