package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
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
        // Q1b: the side grid's "Equip" runs the backpack's own Wield/Wear op; a worn slot's Remove runs the worn tab's.
        onButton(Components.Equipment_Side.ITEMS, OP_EQUIP) { click -> equip(click.slot, click.obj) }
        for ((panelSlot, wornSlot) in SLOT_COMPONENTS) {
            onButton(panelSlot, OP_REMOVE) { dispatchButton(wornSlot, OP_REMOVE) }
        }
    }

    /** Dispatches the obj's wield-type iop (Wield/Wear/Equip) as the backpack button op it maps to (iop i -> op). */
    private fun PlayerRef.equip(slot: Int, obj: Int) {
        val inv = inv(Invs.INV)
        if (slot !in 0 until inv.size || inv.obj(slot) != obj) return
        val iop = (0 until IOP_BUTTON_OPS.size).firstOrNull { objIop(obj, it)?.lowercase() in WIELD_VERBS }
        if (iop == null) return message("You can't equip that.")
        dispatchButton(Components.Inventory.ITEMS, IOP_BUTTON_OPS[iop], slot, obj)
    }

    private fun PlayerRef.open() {
        transmitInv(Invs.INV)
        SideInventory.openPair(this, Interfaces.EQUIPMENT, Interfaces.EQUIPMENT_SIDE)
        SideInventory.bindGrid(this, Components.Equipment_Side.ITEMS, Invs.INV, dragType = 0)
        // RS Mod EquipmentStats: op1 "Equip" on every backpack slot of the side grid.
        ifEvents(Components.Equipment_Side.ITEMS, 0 until inv(Invs.INV).size, IfEvents.op(OP_EQUIP))
        showBonuses()
        // The panel refreshes when the worn items change while it is open (RS Mod calls updateBonuses after its own
        // equip/unequip; here the worn tab can change them too, so the worn inv is compared once per tick).
        val seen = WornBonusPanel.wornObjs(this)
        ModalWatch.start(
            this,
            Interfaces.EQUIPMENT,
            whileOpen = {
                val now = WornBonusPanel.wornObjs(this)
                if (!now.contentEquals(seen)) {
                    now.copyInto(seen)
                    showBonuses()
                }
            },
            onClose = { SideInventory.restore(this) },
        )
    }

    private fun PlayerRef.showBonuses() {
        WornBonusPanel.show(this, COMPONENTS)
    }

    private companion object {
        const val OP_EQUIP = 1
        const val OP_REMOVE = 1

        /** Backpack iop index -> button op (iop1..iop5 = ops 2, 3, 4, 6, 7; combat Equipment.ITEM_OPS). */
        val IOP_BUTTON_OPS = intArrayOf(2, 3, 4, 6, 7)
        val WIELD_VERBS = setOf("wield", "wear", "equip")

        /** Equipment panel worn slot -> worn tab slot (same SLOTn names in both cache interfaces). */
        val SLOT_COMPONENTS: List<Pair<Int, Int>> =
            listOf(
                Components.Equipment.SLOT0 to Components.Wornitems.SLOT0,
                Components.Equipment.SLOT1 to Components.Wornitems.SLOT1,
                Components.Equipment.SLOT2 to Components.Wornitems.SLOT2,
                Components.Equipment.SLOT3 to Components.Wornitems.SLOT3,
                Components.Equipment.SLOT4 to Components.Wornitems.SLOT4,
                Components.Equipment.SLOT5 to Components.Wornitems.SLOT5,
                Components.Equipment.SLOT7 to Components.Wornitems.SLOT7,
                Components.Equipment.SLOT9 to Components.Wornitems.SLOT9,
                Components.Equipment.SLOT10 to Components.Wornitems.SLOT10,
                Components.Equipment.SLOT12 to Components.Wornitems.SLOT12,
                Components.Equipment.SLOT13 to Components.Wornitems.SLOT13,
            )

        /**
         * The `equipment` interface's lines. RS Mod writes the ranged defence into the component the cache names `magicdef`
         * and magic into `rangedef`.
         */
        val COMPONENTS =
            BonusComponents(
                stab = Components.Equipment.STABATT,
                slash = Components.Equipment.SLASHATT,
                crush = Components.Equipment.CRUSHATT,
                magic = Components.Equipment.MAGICATT,
                range = Components.Equipment.RANGEATT,
                speedBase = Components.Equipment.ATTACKSPEEDBASE,
                speedActual = Components.Equipment.ATTACKSPEEDACTUAL,
                defStab = Components.Equipment.STABDEF,
                defSlash = Components.Equipment.SLASHDEF,
                defCrush = Components.Equipment.CRUSHDEF,
                defMagic = Components.Equipment.RANGEDEF,
                defRange = Components.Equipment.MAGICDEF,
                meleeStrength = Components.Equipment.MELEESTRENGTH,
                rangedStrength = Components.Equipment.RANGESTRENGTH,
                magicDamage = Components.Equipment.MAGICDAMAGE,
                prayer = Components.Equipment.PRAYER,
                undead = Components.Equipment.TYPEMULTIPLIER,
                slayer = Components.Equipment.SLAYERMULTIPLIER,
                tooltip = Components.Equipment.TOOLTIP,
            )
    }
}
