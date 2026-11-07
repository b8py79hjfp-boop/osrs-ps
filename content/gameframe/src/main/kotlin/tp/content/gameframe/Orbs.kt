package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * Minimap orbs (docs/mechanics/gameframe-buttons.md O1..O5): run toggle, XP drops toggle (its setup panel is
 * [XpDropsSetup]), quick-prayer setup and the world map orb. The special attack orb and the quick-prayer toggle (op 1)
 * live in content/combat.
 */
public class Orbs : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Orbs.RUNBUTTON) { toggleRun() }
        onButton(Components.Settings_Side.RUNMODE) { toggleRun() }

        onButton(Components.Orbs.XP_DROPS, OP_PRIMARY) { setVarbit(Varbits.XPDROPS_ENABLED, 1 - varbit(Varbits.XPDROPS_ENABLED)) }

        // O5: the orb opens `worldmap` (595) after client script 1749 sets its markers (varcs `worldmap_youarehere` 188,
        // `worldmap_clue` 1078, `worldmap_gravestone` 401; packed coords, -1 = none).
        onButton(Components.Orbs.WORLDMAP, OP_FLOATING_MAP) { openWorldMap() }
        onButton(Components.Worldmap.CLOSE) {
            if (isOpen(Interfaces.WORLDMAP)) closeModal()
        }
    }

    private fun PlayerRef.openWorldMap() {
        val c = coord
        clientScript(CS_WORLDMAP_MARKERS, (c.level shl LEVEL_SHIFT) or (c.x shl X_SHIFT) or c.z, NO_MARKER, NO_MARKER)
        openModal(Components.Toplevel.FLOATER, Interfaces.WORLDMAP)
    }

    private fun PlayerRef.toggleRun() {
        closeModal()
        runMode = !runMode
    }

    internal companion object {
        /** Sets the world map's "you are here", clue and gravestone markers (varcs 188 / 1078 / 401). */
        const val CS_WORLDMAP_MARKERS = 1749

        /** World map orb op 1 "Floating World Map" (op 3 "Fullscreen World Map" is OPEN-O5b). */
        const val OP_FLOATING_MAP = 1
        const val NO_MARKER = -1

        /** Packed coordinate: level << 28 | x << 14 | z. */
        const val LEVEL_SHIFT = 28
        const val X_SHIFT = 14

        const val OP_PRIMARY = 1

        /** Orb op 2 is "Setup" for the XP drops and quick-prayer orbs (cache interface 160 actions). */
        const val OP_SETUP = 2
    }
}
