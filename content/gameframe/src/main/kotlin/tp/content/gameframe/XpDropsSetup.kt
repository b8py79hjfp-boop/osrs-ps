package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * XP drops "Setup" panel (docs/mechanics/gameframe-buttons.md O2), opened from op 2 of the XP drops orb. The panel's
 * dropdown rows are dynamic children whose varbits the cache names after them; Save keeps the choices and Cancel puts
 * back the values the panel opened with. RS Mod has no handler for this panel; the click protocol (1-based row in the
 * child slot, stored as row - 1) was verified against the rev 242 client (O2).
 */
public class XpDropsSetup : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Orbs.XP_DROPS, Orbs.OP_SETUP) { open() }
        for ((component, varbit) in DROPDOWNS) {
            onButton(component) { click -> choose(varbit, click.slot) }
        }
        onButton(Components.Xpdrops_Setup.SAVE) { closeModal() }
        onButton(Components.Xpdrops_Setup.CANCEL) { cancel() }
    }

    private fun PlayerRef.open() {
        this[SNAPSHOT] = IntArray(DROPDOWNS.size) { varbit(DROPDOWNS[it].second) }
        openModal(Components.Toplevel.MAINMODAL, Interfaces.XPDROPS_SETUP)
        // Rows report their 1-based position as the child slot (verified with the rev 242 client, O2).
        for ((component, _) in DROPDOWNS) ifEvents(component, 1..OPTION_LAST_SLOT, IfEvents.op(1))
    }

    private fun PlayerRef.choose(varbit: Int, slot: Int) {
        // The slot comes from the client: it must be a row and fit the varbit before it is stored (value = row - 1).
        val value = slot - 1
        if (slot >= 1 && varbitFits(varbit, value)) setVarbit(varbit, value)
    }

    private fun PlayerRef.cancel() {
        val saved = this[SNAPSHOT]
        if (saved != null) for (i in DROPDOWNS.indices) setVarbit(DROPDOWNS[i].second, saved[i])
        this[SNAPSHOT] = null
        closeModal()
    }

    private companion object {
        val SNAPSHOT: AttrKey<IntArray> = AttrKey("xpdrops_setup_snapshot")

        /** Highest option row of a dropdown (the widest has fewer; the varbit width bounds what is stored). */
        const val OPTION_LAST_SLOT = 15

        /** Dropdown option list -> the varbit it sets (cache names: `xpdrops_position`, ..., `xpdrops_fakesoff`). */
        val DROPDOWNS: List<Pair<Int, Int>> =
            listOf(
                Components.Xpdrops_Setup.POSITION_OPTIONS to Varbits.XPDROPS_POSITION,
                Components.Xpdrops_Setup.SIZE_OPTIONS to Varbits.XPDROPS_SIZE,
                Components.Xpdrops_Setup.DURATION_OPTIONS to Varbits.XPDROPS_DURATION,
                Components.Xpdrops_Setup.COUNTER_OPTIONS to Varbits.XPDROPS_COUNTER_TYPE,
                Components.Xpdrops_Setup.PROGRESS_OPTIONS to Varbits.XPDROPS_PROGRESS_TYPE,
                Components.Xpdrops_Setup.COLOUR_OPTIONS to Varbits.XPDROPS_COLOUR,
                Components.Xpdrops_Setup.GROUPING_OPTIONS to Varbits.XPDROPS_GROUPSKILLS,
                Components.Xpdrops_Setup.SPEED_OPTIONS to Varbits.XPDROPS_SPEED,
                Components.Xpdrops_Setup.FAKES_OPTIONS to Varbits.XPDROPS_FAKESOFF,
            )
    }
}
