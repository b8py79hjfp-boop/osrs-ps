package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Varbits

/**
 * Minimap orbs (docs/mechanics/gameframe-buttons.md O1..O5): run toggle, XP drops toggle (its setup panel is
 * [XpDropsSetup]), quick-prayer setup and the world map orb. The special attack orb lives in content/combat; the
 * quick-prayer toggle (op 1) is OPEN-O3.
 */
public class Orbs : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Orbs.RUNBUTTON) { toggleRun() }
        onButton(Components.Settings_Side.RUNMODE) { toggleRun() }

        onButton(Components.Orbs.XP_DROPS, OP_PRIMARY) { setVarbit(Varbits.XPDROPS_ENABLED, 1 - varbit(Varbits.XPDROPS_ENABLED)) }

        // The world map is drawn by the client; RS Mod has no server handler for the orb (O5).
        onButton(Components.Orbs.WORLDMAP) { }
    }

    private fun PlayerRef.toggleRun() {
        closeModal()
        runMode = !runMode
    }

    internal companion object {
        const val OP_PRIMARY = 1

        /** Orb op 2 is "Setup" for the XP drops and quick-prayer orbs (cache interface 160 actions). */
        const val OP_SETUP = 2
    }
}
