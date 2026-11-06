package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs

/**
 * Worn items tab "Equipment stats" (docs/mechanics/gameframe-buttons.md Q1): the `equipment` panel with the summed
 * bonuses of the worn items, next to `equipment_side` (a view of the backpack) in the inventory side slot. Layout,
 * texts and sums follow RS Mod `EquipmentStats` / `WornBonuses` (ISC, DECISIONS D-027).
 */
public class EquipmentStats : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Wornitems.EQUIPMENT) { open() }
    }

    private fun PlayerRef.open() {
        transmitInv(Invs.INV)
        SideInventory.openPair(this, Interfaces.EQUIPMENT, Interfaces.EQUIPMENT_SIDE)
        // Read-only grid: equipping from the panel is OPEN-Q1b (no ops, so no events are enabled either).
        SideInventory.bindGrid(this, Components.Equipment_Side.ITEMS, Invs.INV, dragType = 0)
        showBonuses()
        // The panel refreshes when the worn items change while it is open (RS Mod calls updateBonuses after its own
        // equip/unequip; here the worn tab can change them too, so the worn inv is compared once per tick).
        val seen = wornObjs()
        ModalWatch.start(
            this,
            Interfaces.EQUIPMENT,
            whileOpen = {
                val now = wornObjs()
                if (!now.contentEquals(seen)) {
                    now.copyInto(seen)
                    showBonuses()
                }
            },
            onClose = { SideInventory.restore(this) },
        )
    }

    private fun PlayerRef.wornObjs(): IntArray {
        val worn = inv(Invs.WORN)
        return IntArray(WORN_SLOTS) { worn.obj(it) }
    }

    private fun PlayerRef.showBonuses() {
        val sums = IntArray(PARAMS.size)
        val worn = wornObjs()
        for (obj in worn) {
            if (obj < 0) continue
            for (i in PARAMS.indices) sums[i] += objParam(obj, PARAMS[i], 0)
        }
        val weapon = worn[WEAPON_SLOT]
        val speed = if (weapon < 0) DEFAULT_ATTACK_RATE else objParam(weapon, PARAM_ATTACK_RATE, DEFAULT_ATTACK_RATE)

        ifText(Components.Equipment.STABATT, "Stab: ${signed(sums[ATT_STAB])}")
        ifText(Components.Equipment.SLASHATT, "Slash: ${signed(sums[ATT_STAB + 1])}")
        ifText(Components.Equipment.CRUSHATT, "Crush: ${signed(sums[ATT_STAB + 2])}")
        ifText(Components.Equipment.MAGICATT, "Magic: ${signed(sums[ATT_STAB + 3])}")
        ifText(Components.Equipment.RANGEATT, "Range: ${signed(sums[ATT_STAB + 4])}")
        // Rapid style (-1 tick) needs the weapon category's stance, which lives in content/combat: OPEN-Q1c.
        ifText(Components.Equipment.ATTACKSPEEDBASE, "Base: ${seconds(speed)}")
        ifText(Components.Equipment.ATTACKSPEEDACTUAL, "Actual: ${seconds(speed)}")
        ifText(Components.Equipment.STABDEF, "Stab: ${signed(sums[DEF_STAB])}")
        ifText(Components.Equipment.SLASHDEF, "Slash: ${signed(sums[DEF_STAB + 1])}")
        ifText(Components.Equipment.CRUSHDEF, "Crush: ${signed(sums[DEF_STAB + 2])}")
        // RS Mod writes the ranged defence into the component the cache names `magicdef` and magic into `rangedef`.
        ifText(Components.Equipment.MAGICDEF, "Range: ${signed(sums[DEF_STAB + 4])}")
        ifText(Components.Equipment.RANGEDEF, "Magic: ${signed(sums[DEF_STAB + 3])}")
        ifText(Components.Equipment.MELEESTRENGTH, "Melee STR: ${signed(sums[MELEE_STR])}")
        ifText(Components.Equipment.RANGESTRENGTH, "Ranged STR: ${signed(sums[RANGED_STR])}")
        ifText(Components.Equipment.MAGICDAMAGE, "Magic DMG: +${sums[MAGIC_DMG] / TENTHS}%")
        ifText(Components.Equipment.PRAYER, "Prayer: ${signed(sums[PRAYER])}")
        // OPEN-Q1d: the undead and Slayer multipliers are not obj params in cache 2757 (the salve amulet (e) and Slayer
        // helmet carry none), so they always read +0%. The trailing space on undead is RS Mod's text for 0.
        ifText(Components.Equipment.TYPEMULTIPLIER, "Undead: +0% ")
        ifText(Components.Equipment.SLAYERMULTIPLIER, "Slayer: +0%")
        clientScript(CS_STAT_GROUP_TOOLTIP, Components.Equipment.TOOLTIP, Components.Equipment.TYPEMULTIPLIER, UNDEAD_TOOLTIP)
    }

    private fun signed(value: Int): String = if (value < 0) "$value" else "+$value"

    /** Ticks as seconds with one decimal: a tick is 0.6 s. */
    private fun seconds(ticks: Int): String = "${ticks * TICK_MILLIS / MILLIS_PER_SECOND}s"

    private companion object {
        /** Worn inv positions 0..13 (head .. ammo; cache `wearpos`). */
        const val WORN_SLOTS = 14
        const val WEAPON_SLOT = 3

        /**
         * Summed obj params, in this order: attack stab, slash, crush, magic, ranged (0-4); defence stab, slash, crush,
         * magic, ranged (5-9); melee strength (10); prayer (11); ranged strength (12); magic damage in tenths of a
         * percent (299). docs/mechanics/combat-data.md, same ids as RS Mod `BaseParams`.
         */
        val PARAMS: IntArray = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 299)
        const val ATT_STAB = 0
        const val DEF_STAB = 5
        const val MELEE_STR = 10
        const val PRAYER = 11
        const val RANGED_STR = 12
        const val MAGIC_DMG = 13

        /** Obj param 14: weapon attack rate in ticks (combat-data.md); unarmed is 4 (RS Mod `combat_default_attackrate`). */
        const val PARAM_ATTACK_RATE = 14
        const val DEFAULT_ATTACK_RATE = 4

        const val TENTHS = 10.0
        const val TICK_MILLIS = 600.0
        const val MILLIS_PER_SECOND = 1000.0

        /** Client script `stat_group_tooltip` (RS Mod `ClientScripts.statGroupTooltip`). */
        const val CS_STAT_GROUP_TOOLTIP = 7065
        const val UNDEAD_TOOLTIP =
            "Increases your effective accuracy and damage against undead creatures. For multi-target Ranged and " +
                "Magic attacks, this applies only to the primary target. It does not stack with the Slayer multiplier."
    }
}
