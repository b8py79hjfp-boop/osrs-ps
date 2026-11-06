package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * Fixed-mode gameframe (toplevel 548) with every standard overlay and side tab. Overlay list and targets: RS Mod
 * `StandardOverlays.open` (its `toplevel_target_*` names map to `toplevel:*` components in fixed mode).
 */
public class Gameframe : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { openGameframe() }
        // Logout tab <-> World Switcher (same side slot; the client closes the old sub on IF_OPENSUB).
        onButton(Components.Logout.WORLD_SWITCHER) { openWorldSwitcher() }
        onButton(Components.Worldswitcher.CLOSE) { openOverlay(Components.Toplevel.SIDE10, Interfaces.LOGOUT) }
    }

    private fun PlayerRef.openWorldSwitcher() {
        openOverlay(Components.Toplevel.SIDE10, Interfaces.WORLDSWITCHER)
        // World rows are dynamic children whose slot is the world id (docs/mechanics/world-hop.md H2, OPEN).
        ifEvents(Components.Worldswitcher.BUTTONS, 0..MAX_WORLD_ID, IfEvents.op(1))
    }

    private fun PlayerRef.openGameframe() {
        openTop(Interfaces.TOPLEVEL)
        for ((iface, target) in OVERLAYS) openOverlay(target, iface)
        // Inventory tab item ops (RS Mod `InvOpenScript`).
        SideInventory.enableEvents(this)
        // Run orb shows varp option_run, which the saved run mode must set (the engine does not).
        runMode = runMode
        SettingsPanel.enableEvents(this)
        EmoteTab.enableEvents(this)
        // The side journal opens empty; fill its active sub-tab (RS Mod `JournalTabScript.onIfOpen`).
        with(JournalTab) { open(varbit(Varbits.SIDE_JOURNAL_TAB)) }
    }

    private companion object {
        /** Highest world id the switcher can send (world ids are u16 in LOGOUT_TRANSFER; OSRS uses up to ~600). */
        const val MAX_WORLD_ID = 2047

        val OVERLAYS: List<Pair<Int, Int>> =
            listOf(
                Interfaces.CHATBOX to Components.Toplevel.CHAT_CONTAINER,
                Interfaces.BUFF_BAR to Components.Toplevel.BUFF_BAR,
                Interfaces.STAT_BOOSTS_HUD to Components.Toplevel.STAT_BOOSTS_HUD,
                Interfaces.PM_CHAT to Components.Toplevel.PM_CONTAINER,
                Interfaces.HPBAR_HUD to Components.Toplevel.HPBAR_HUD,
                Interfaces.PVP_ICONS to Components.Toplevel.PVP_ICONS,
                Interfaces.ORBS to Components.Toplevel.ORBS,
                Interfaces.XP_DROPS to Components.Toplevel.XP_DROPS,
                Interfaces.POPOUT to Components.Toplevel.POPOUT,
                Interfaces.STATS to Components.Toplevel.SIDE1,
                Interfaces.SIDE_JOURNAL to Components.Toplevel.SIDE2,
                Interfaces.INVENTORY to Components.Toplevel.SIDE3,
                Interfaces.WORNITEMS to Components.Toplevel.SIDE4,
                Interfaces.PRAYERBOOK to Components.Toplevel.SIDE5,
                Interfaces.MAGIC_SPELLBOOK to Components.Toplevel.SIDE6,
                Interfaces.FRIENDS to Components.Toplevel.SIDE9,
                Interfaces.ACCOUNT to Components.Toplevel.SIDE8,
                Interfaces.LOGOUT to Components.Toplevel.SIDE10,
                Interfaces.SETTINGS_SIDE to Components.Toplevel.SIDE11,
                Interfaces.EMOTE to Components.Toplevel.SIDE12,
                Interfaces.MUSIC to Components.Toplevel.SIDE13,
                Interfaces.SIDE_CHANNELS to Components.Toplevel.SIDE7,
                Interfaces.COMBAT_INTERFACE to Components.Toplevel.SIDE0,
            )
    }
}
