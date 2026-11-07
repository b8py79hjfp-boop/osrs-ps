package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.confirmDestroy
import tp.symbols.Components
import tp.symbols.Invs

/**
 * Backpack item basics: drop (button op 7 = iop5, RS Mod `InvOpenScript` op mapping) and ground Take (ground op 3,
 * the cache default). Every inventory item action interrupts (interactions.md I7).
 */
public class Backpack : ContentPlugin {
    override fun ContentScope.register() {
        // RS Mod `HeldOpScript.dragHeld`: closes the modal, refused while delayed, then swaps the two backpack slots.
        onButtonDrag(Components.Inventory.ITEMS) { d ->
            val size = inv(Invs.INV).size
            val valid = d.toComponent == Components.Inventory.ITEMS && d.fromSlot in 0 until size && d.toSlot in 0 until size
            closeModal()
            if (valid && d.fromSlot != d.toSlot && !isDelayed) transaction("inv drag") { swap(Invs.INV, d.fromSlot, d.toSlot) }
        }
        onButton(Components.Inventory.ITEMS, OP_DROP) { click ->
            interrupt()
            val inv = inv(Invs.INV)
            if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return@onButton
            // DS1: items whose iop5 is "Destroy" (untradeables) are destroyed, after the confirmation, not dropped.
            if (objIop(click.obj, IOP5_INDEX).equals("Destroy", ignoreCase = true)) {
                val slot = click.slot
                val obj = click.obj
                val count = inv.count(slot)
                weakQueue(0) {
                    if (!confirmDestroy(obj, count, DESTROY_MESSAGE)) return@weakQueue
                    player.transaction("destroy") { removeAt(Invs.INV, slot, obj, count) }
                }
            } else {
                transaction("drop") { drop(Invs.INV, click.slot, click.obj, inv.count(click.slot)) }
            }
        }
        onOpObjDefault(OP_TAKE) { obj ->
            if (!player.transaction("take") { take(obj, Invs.INV) }) {
                player.message("You don't have enough inventory space to hold that item.")
            }
        }
    }

    private companion object {
        /** IF button op of iop5 ("Drop"): iop n = button op n + 1, except iop4/iop5 = ops 6/7 (RS Mod `InvOpenScript`). */
        const val OP_DROP = 7

        /** OPEN-DS1: the item-specific destroy text is not in our cache data; a generic warning is shown. */
        const val DESTROY_MESSAGE = "If you destroy this item, you may not be able to get it back."

        /** iop5 (0-based index 4): Drop or Destroy. */
        const val IOP5_INDEX = 4

        /** Ground op 3 = "Take" (ObjType default ground ops). */
        const val OP_TAKE = 3
    }
}
