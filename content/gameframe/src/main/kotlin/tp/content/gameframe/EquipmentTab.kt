package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.symbols.Components

/**
 * Worn-items tab buttons that need no panel (docs/mechanics/gameframe-buttons.md Q4); the panels are
 * [EquipmentStats], [PriceChecker] and [ItemsKeptOnDeath]. Component names: RS Mod `EquipmentTabComponents` (ISC, D-027).
 */
public class EquipmentTab : ContentPlugin {
    override fun ContentScope.register() {
        // No followers exist yet (RS Mod `EquipmentTabScript.callFollower` has the same TODO).
        onButton(Components.Wornitems.CALL_FOLLOWER) { message("You do not have a follower.") }
    }
}
