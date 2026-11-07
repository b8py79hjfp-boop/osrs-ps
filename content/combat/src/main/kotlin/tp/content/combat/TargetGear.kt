package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SlayerTaskCheck

/**
 * Target-specific gear multipliers on accuracy and max hit (combat-formulas.md F23-F24): the salve amulet family
 * against undead, else the black mask / Slayer helmet against the current Slayer task. They don't stack; the salve
 * wins (Wiki "Salve amulet", "Slayer helmet"). Applied as ⌊value × num / den⌋ to the attack roll and the max hit.
 */
internal object TargetGear {
    class Mult(val num: Int, val den: Int)

    private val NONE = Mult(1, 1)
    private val SEVEN_SIXTHS = Mult(7, 6)
    private val SIX_FIFTHS = Mult(6, 5)
    private val IMBUED_FIFTEEN = Mult(23, 20)

    fun of(player: PlayerRef, target: EntityRef?, style: CombatStyle): Mult {
        if (target !is NpcRef) return NONE
        salve(player, target, style)?.let { return it }
        return slayer(player, target, style) ?: NONE
    }

    fun apply(value: Int, m: Mult): Int = (value.toLong() * m.num / m.den).toInt()

    /** F23 (Wiki salve pages): salve 7/6 melee; (i) 7/6 melee and ranged, 15% magic; (e) 6/5 melee; (ei) 6/5 all. */
    private fun salve(player: PlayerRef, target: NpcRef, style: CombatStyle): Mult? {
        val name = player.objName(player.worn(Wear.NECK))
        if (!name.startsWith("Salve amulet")) return null
        if (NpcCombatTable.of(target.type)?.attributes?.contains("undead") != true) return null
        val enchanted = name.contains("(e)") || name.contains("(ei)")
        val imbued = name.contains("(i)") || name.contains("(ei)")
        return when {
            style == CombatStyle.MELEE -> if (enchanted) SIX_FIFTHS else SEVEN_SIXTHS
            !imbued -> null
            enchanted -> SIX_FIFTHS
            style == CombatStyle.RANGED -> SEVEN_SIXTHS
            else -> IMBUED_FIFTEEN
        }
    }

    /** F24 (Wiki "Black mask", "Slayer helmet"): on task, 7/6 melee; imbued, 15% ranged and magic too. */
    private fun slayer(player: PlayerRef, target: NpcRef, style: CombatStyle): Mult? {
        val name = player.objName(player.worn(Wear.HEAD))
        if (!name.startsWith("Black mask") && !name.startsWith("Slayer helmet")) return null
        val imbued = name.contains("(i)")
        if (style != CombatStyle.MELEE && !imbued) return null
        val check = SlayerTaskCheck(player, target)
        player.events.publish(check)
        if (!check.onTask) return null
        return if (style == CombatStyle.MELEE) SEVEN_SIXTHS else IMBUED_FIFTEEN
    }
}
