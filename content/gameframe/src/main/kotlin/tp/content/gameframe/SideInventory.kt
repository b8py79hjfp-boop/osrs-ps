package tp.content.gameframe

import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces

/**
 * The backpack lives in side slot 3. Modals that come with their own item list (equipment stats, price checker) borrow
 * that slot, as RS Mod's `ifOpenMainSidePair` does with the toplevel `sidemodal`; [restore] puts the backpack back.
 * plugin-api has no way to close a sub-interface, so reopening the backpack replaces the borrowed one.
 */
internal object SideInventory {
    /** Backpack slots 0..27. */
    private const val LAST_SLOT = 27

    /** Client script `toplevel_mainmodal_open`: sets the main modal background (colour, transparency; -1 = default). */
    private const val CS_MAINMODAL_BACKGROUND = 2524

    /** Client script `interface_inv_init`: binds an inv to a grid component (RS Mod `ClientScripts.interfaceInvInit`). */
    private const val CS_INTERFACE_INV_INIT = 149

    @Suppress("MagicNumber") // op numbers
    val ITEM_EVENTS: Long =
        IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(6) or IfEvents.op(7) or IfEvents.op(10) or
            IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
            IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET

    /** Inventory tab item ops (RS Mod `InvOpenScript`). */
    fun enableEvents(player: PlayerRef) {
        player.ifEvents(Components.Inventory.ITEMS, 0..LAST_SLOT, ITEM_EVENTS)
    }

    /** Opens [main] as the main modal with the default background (RS Mod `ifOpenMainModal`). */
    fun openMain(player: PlayerRef, main: Int, transparency: Int = -1) {
        player.clientScript(CS_MAINMODAL_BACKGROUND, -1, transparency)
        player.openModal(Components.Toplevel.MAINMODAL, main)
    }

    /** Opens [main] as the main modal and [side] over the backpack (RS Mod `ifOpenMainSidePair`). */
    fun openPair(player: PlayerRef, main: Int, side: Int) {
        openMain(player, main)
        player.openOverlay(Components.Toplevel.SIDE3, side)
    }

    /** Puts the backpack back in its side slot. */
    fun restore(player: PlayerRef) {
        player.openOverlay(Components.Toplevel.SIDE3, Interfaces.INVENTORY)
        enableEvents(player)
    }

    /** Binds [invId] to the 4x7 item grid [target] with up to five op labels (RS Mod `interfaceInvInit`). */
    fun bindGrid(player: PlayerRef, target: Int, invId: Int, dragType: Int = 0, vararg ops: String) {
        val labels = Array(OP_LABELS) { ops.getOrElse(it) { "" } }
        player.clientScript(CS_INTERFACE_INV_INIT, target, invId, GRID_ROWS, GRID_COLUMNS, dragType, -1, *labels)
    }

    private const val GRID_ROWS = 4
    private const val GRID_COLUMNS = 7
    private const val OP_LABELS = 5
}
