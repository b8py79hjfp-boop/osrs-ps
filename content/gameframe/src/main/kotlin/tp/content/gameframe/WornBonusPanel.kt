package tp.content.gameframe

import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * The components of one worn-bonus panel (the `equipment` interface and the bank's worn items page): the attack, defence,
 * other bonus and speed lines. [defMagic] and [defRange] are the components that receive the "Magic:" and the "Range:"
 * defence line, because the two interfaces name them differently (see [EquipmentStats], [Bank]).
 */
internal class BonusComponents(
    val stab: Int,
    val slash: Int,
    val crush: Int,
    val magic: Int,
    val range: Int,
    val speedBase: Int,
    val speedActual: Int,
    val defStab: Int,
    val defSlash: Int,
    val defCrush: Int,
    val defMagic: Int,
    val defRange: Int,
    val meleeStrength: Int,
    val rangedStrength: Int,
    val magicDamage: Int,
    val prayer: Int,
    val undead: Int,
    val slayer: Int,
    val tooltip: Int,
)

/** The summed bonuses of the worn items written into a [BonusComponents] (RS Mod `WornBonuses`, ISC, DECISIONS D-027). */
internal object WornBonusPanel {
    /** Worn inv positions 0..13 (head .. ammo; cache `wearpos`). */
    const val WORN_SLOTS = 14

    /** The worn obj of every worn position (-1 = empty). */
    fun wornObjs(p: PlayerRef): IntArray {
        val worn = p.inv(Invs.WORN)
        return IntArray(WORN_SLOTS) { worn.obj(it) }
    }

    fun show(p: PlayerRef, c: BonusComponents) {
        val sums = IntArray(PARAMS.size)
        val worn = wornObjs(p)
        for (obj in worn) {
            if (obj < 0) continue
            for (i in PARAMS.indices) sums[i] += p.objParam(obj, PARAMS[i], 0)
        }
        val weapon = worn[WEAPON_SLOT]
        val speed = if (weapon < 0) DEFAULT_ATTACK_RATE else p.objParam(weapon, PARAM_ATTACK_RATE, DEFAULT_ATTACK_RATE)

        p.ifText(c.stab, "Stab: ${signed(sums[ATT_STAB])}")
        p.ifText(c.slash, "Slash: ${signed(sums[ATT_STAB + 1])}")
        p.ifText(c.crush, "Crush: ${signed(sums[ATT_STAB + 2])}")
        p.ifText(c.magic, "Magic: ${signed(sums[ATT_STAB + 3])}")
        p.ifText(c.range, "Range: ${signed(sums[ATT_STAB + 4])}")
        // Rapid style (-1 tick) needs the weapon category's stance, which lives in content/combat: OPEN-Q1c.
        p.ifText(c.speedBase, "Base: ${seconds(speed)}")
        p.ifText(c.speedActual, "Actual: ${seconds(speed)}")
        p.ifText(c.defStab, "Stab: ${signed(sums[DEF_STAB])}")
        p.ifText(c.defSlash, "Slash: ${signed(sums[DEF_STAB + 1])}")
        p.ifText(c.defCrush, "Crush: ${signed(sums[DEF_STAB + 2])}")
        p.ifText(c.defMagic, "Magic: ${signed(sums[DEF_STAB + 3])}")
        p.ifText(c.defRange, "Range: ${signed(sums[DEF_STAB + 4])}")
        p.ifText(c.meleeStrength, "Melee STR: ${signed(sums[MELEE_STR])}")
        p.ifText(c.rangedStrength, "Ranged STR: ${signed(sums[RANGED_STR])}")
        p.ifText(c.magicDamage, "Magic DMG: +${sums[MAGIC_DMG] / TENTHS}%")
        p.ifText(c.prayer, "Prayer: ${signed(sums[PRAYER])}")
        // OPEN-Q1d: the undead and Slayer multipliers are not obj params in cache 2757 (the salve amulet (e) and Slayer
        // helmet carry none), so they always read +0%. The trailing space on undead is RS Mod's text for 0.
        p.ifText(c.undead, "Undead: +0% ")
        p.ifText(c.slayer, "Slayer: +0%")
        p.clientScript(CS_STAT_GROUP_TOOLTIP, c.tooltip, c.undead, UNDEAD_TOOLTIP)
    }

    private fun signed(value: Int): String = if (value < 0) "$value" else "+$value"

    /** Ticks as seconds with one decimal: a tick is 0.6 s. */
    private fun seconds(ticks: Int): String = "${ticks * TICK_MILLIS / MILLIS_PER_SECOND}s"

    private const val WEAPON_SLOT = 3

    /**
     * Summed obj params, in this order: attack stab, slash, crush, magic, ranged (0-4); defence stab, slash, crush,
     * magic, ranged (5-9); melee strength (10); prayer (11); ranged strength (12); magic damage in tenths of a
     * percent (299). docs/mechanics/combat-data.md, same ids as RS Mod `BaseParams`.
     */
    private val PARAMS: IntArray = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 299)
    private const val ATT_STAB = 0
    private const val DEF_STAB = 5
    private const val MELEE_STR = 10
    private const val PRAYER = 11
    private const val RANGED_STR = 12
    private const val MAGIC_DMG = 13

    /** Obj param 14: weapon attack rate in ticks (combat-data.md); unarmed is 4 (RS Mod `combat_default_attackrate`). */
    private const val PARAM_ATTACK_RATE = 14
    private const val DEFAULT_ATTACK_RATE = 4

    private const val TENTHS = 10.0
    private const val TICK_MILLIS = 600.0
    private const val MILLIS_PER_SECOND = 1000.0

    /** Client script `stat_group_tooltip` (RS Mod `ClientScripts.statGroupTooltip`; cache script 7065 takes tooltip, container, text). */
    private const val CS_STAT_GROUP_TOOLTIP = 7065
    private const val UNDEAD_TOOLTIP =
        "Increases your effective accuracy and damage against undead creatures. For multi-target Ranged and " +
            "Magic attacks, this applies only to the primary target. It does not stack with the Slayer multiplier."
}
