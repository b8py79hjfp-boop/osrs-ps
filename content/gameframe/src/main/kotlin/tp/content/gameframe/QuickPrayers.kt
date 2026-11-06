package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * Quick-prayer setup (orb op 2): the selection panel stores the chosen prayers in varbit `quickprayer_selected` (same
 * bit layout as `prayer_allactive`: RS Mod copies one into the other). Activating them (orb op 1) lives with the
 * prayer drain in content/combat (`Prayers`, prayers.md P6), which also resolves conflicts between selected prayers.
 */
public class QuickPrayers : ContentPlugin {
    /** Prayer slot (cache enum 4956 key) -> its varbit bit and level. */
    private var bits: Map<Int, Int> = emptyMap()
    private var levels: Map<Int, Int> = emptyMap()
    private var names: Map<Int, String> = emptyMap()

    override fun ContentScope.register() {
        onWorldStart {
            val prayers = enumInts(PRAYER_ENUM)
            bits = prayers.mapNotNull { (slot, obj) -> objParamInt(obj, PARAM_BIT)?.let { slot to it } }.toMap()
            levels = prayers.mapValues { (_, obj) -> objParamInt(obj, PARAM_LEVEL) ?: 1 }
            names = prayers.mapValues { (_, obj) -> objParamString(obj, PARAM_NAME) ?: "this prayer" }
        }
        onButton(Components.Orbs.PRAYERBUTTON, Orbs.OP_SETUP) { openSetup() }
        onButton(Components.Quickprayer.BUTTONS) { click -> toggle(click.slot) }
        onButton(Components.Quickprayer.CLOSE) { closeSetup() }
    }

    private fun PlayerRef.openSetup() {
        closeModal()
        openOverlay(Components.Toplevel.SIDE5, Interfaces.QUICKPRAYER)
        ifEvents(Components.Quickprayer.BUTTONS, 0..(bits.keys.maxOrNull() ?: 0), IfEvents.op(1))
    }

    private fun PlayerRef.closeSetup() {
        openOverlay(Components.Toplevel.SIDE5, Interfaces.PRAYERBOOK)
    }

    private fun PlayerRef.toggle(slot: Int) {
        val bit = bits[slot] ?: return
        val mask = 1 shl bit
        val selected = varbit(Varbits.QUICKPRAYER_SELECTED)
        if (selected and mask != 0) {
            setVarbit(Varbits.QUICKPRAYER_SELECTED, selected and mask.inv())
            return
        }
        val level = levels[slot] ?: 1
        if (baseLevel(PRAYER_STAT) < level) {
            message("You need a Prayer level of $level to use ${names[slot]}.")
            return
        }
        setVarbit(Varbits.QUICKPRAYER_SELECTED, selected or mask)
    }

    private companion object {
        /** Cache enum of prayer objs (key = quick-prayer slot), same enum content/combat reads (combat-data.md). */
        const val PRAYER_ENUM = 4956

        /** Prayer obj params (cache 2757): bit in `prayer_allactive`, name, level. */
        const val PARAM_BIT = 630
        const val PARAM_NAME = 1752
        const val PARAM_LEVEL = 1753

        /** Prayer skill index. */
        const val PRAYER_STAT = 5
    }
}
