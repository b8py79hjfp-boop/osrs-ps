package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Components
import tp.symbols.Interfaces

/**
 * Worn-items tab buttons (docs/mechanics/gameframe-buttons.md Q1..Q4). Component names: RS Mod `EquipmentTabComponents`
 * (ISC, DECISIONS D-027).
 */
public class EquipmentTab : ContentPlugin {
    override fun ContentScope.register() {
        // OPEN-Q1: the stat texts need an IF_SETTEXT message (protocol-api has none), the paired side inventory
        // (`equipment_side`) needs a second modal destination in the engine; only the main panel opens, with cache defaults.
        onButton(Components.Wornitems.EQUIPMENT) { openModal(Components.Toplevel.MAINMODAL, Interfaces.EQUIPMENT) }
        // OPEN-Q2/Q3: price checker and items kept on death need paired interfaces and their inv-driven client scripts.
        onButton(Components.Wornitems.PRICECHECKER) { message("The price checker isn't available yet.") }
        onButton(Components.Wornitems.DEATHKEEP) { message("The items kept on death screen isn't available yet.") }
        // No followers exist yet (RS Mod `EquipmentTabScript.callFollower` has the same TODO).
        onButton(Components.Wornitems.CALL_FOLLOWER) { message("You do not have a follower.") }
    }
}
