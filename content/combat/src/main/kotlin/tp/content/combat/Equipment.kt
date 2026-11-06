package tp.content.combat

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/** Appearance = worn objs + combat level + icons; sent whenever any of them change. */
internal object Appearance {
    /** Skull icon per player, set by PvP content through [skullIcon]. */
    val SKULL = tp.plugin.AttrKey<Int>("skull_icon")

    fun refresh(player: PlayerRef) {
        val worn = IntArray(APPEARANCE_SLOTS) { player.worn(it) }
        player.appearance(worn, player.combatLevel(), player[SKULL] ?: -1, player.headIcon())
    }

    private const val APPEARANCE_SLOTS = 12
}

/**
 * Wield/wear from the backpack (iop1 = button op 2), remove from the worn tab, weapon category for the combat tab,
 * appearance. Every inventory item action interrupts (interactions.md I7); worn-tab actions don't.
 */
public class Equipment : ContentPlugin {
    override fun ContentScope.register() {
        onLogin {
            Appearance.refresh(this)
            setVarbit(Varbits.COMBAT_WEAPON_CATEGORY, weaponCategory().id)
        }
        onButton(Components.Inventory.ITEMS, OP_ITEM1) { click -> itemOp1(click) }
        for ((slot, component) in WORN_COMPONENTS) {
            onButton(component, OP_REMOVE) { remove(slot) }
        }
    }

    /** Backpack op 1 is wield/wear for equipment and eat/drink for consumables. */
    private fun PlayerRef.itemOp1(click: ButtonClick) {
        val inv = inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        if (Consumables.consume(this, click.slot, click.obj)) return
        if (objWearPos(click.obj) >= 0) wield(click.slot, click.obj)
    }

    private fun PlayerRef.wield(slot: Int, obj: Int) {
        interrupt()
        val pos = objWearPos(obj)
        val twoHanded = pos == Wear.WEAPON && objWearPos2(obj) == Wear.SHIELD
        val displaced = ArrayList<Int>(2)
        displaced += pos
        if (twoHanded) displaced += Wear.SHIELD
        // A shield displaces a two-handed weapon.
        val weapon = worn(Wear.WEAPON)
        if (pos == Wear.SHIELD && weapon >= 0 && objWearPos2(weapon) == Wear.SHIELD) displaced += Wear.WEAPON
        val count = inv(Invs.INV).count(slot)
        val ok =
            transaction("wield") {
                removeAt(Invs.INV, slot, obj, count) &&
                    displaced.all { d -> worn(d) < 0 || move(Invs.WORN, d, worn(d), inv(Invs.WORN).count(d), Invs.INV) } &&
                    setAt(Invs.WORN, pos, obj, count)
            }
        if (!ok) {
            message("You don't have enough free inventory space to do that.")
            return
        }
        afterChange(pos)
    }

    private fun PlayerRef.remove(slot: Int) {
        val obj = worn(slot)
        if (obj < 0) return
        val ok = transaction("unequip") { move(Invs.WORN, slot, obj, inv(Invs.WORN).count(slot), Invs.INV) }
        if (!ok) {
            message("You don't have enough free inventory space to do that.")
            return
        }
        afterChange(slot)
    }

    private fun PlayerRef.afterChange(pos: Int) {
        if (pos == Wear.WEAPON || pos == Wear.SHIELD) {
            setVarbit(Varbits.COMBAT_WEAPON_CATEGORY, weaponCategory().id)
            // Switching weapons turns the special attack bar off (RS Mod `resetSpecialAttack` on weapon change).
            setVarp(Varps.SA_ATTACK, 0)
            val slots = weaponCategory().slots
            if (slots.getOrNull(varp(Varps.COM_MODE)) == null) setVarp(Varps.COM_MODE, slots.indexOfFirst { it != null })
        }
        Appearance.refresh(this)
    }

    private companion object {
        /** IF button op of iop1 (Wield/Wear/Eat/Drink): RS Mod `InvOpenScript` op mapping. */
        const val OP_ITEM1 = 2

        /** Worn tab slot click (Remove). */
        const val OP_REMOVE = 1

        val WORN_COMPONENTS: List<Pair<Int, Int>> =
            listOf(
                Wear.HEAD to Components.Wornitems.SLOT0,
                Wear.CAPE to Components.Wornitems.SLOT1,
                Wear.NECK to Components.Wornitems.SLOT2,
                Wear.WEAPON to Components.Wornitems.SLOT3,
                Wear.BODY to Components.Wornitems.SLOT4,
                Wear.SHIELD to Components.Wornitems.SLOT5,
                Wear.LEGS to Components.Wornitems.SLOT7,
                Wear.HANDS to Components.Wornitems.SLOT9,
                Wear.FEET to Components.Wornitems.SLOT10,
                Wear.RING to Components.Wornitems.SLOT12,
                Wear.AMMO to Components.Wornitems.SLOT13,
            )
    }
}
