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
        ifEvents(Components.Inventory.ITEMS, 0..INV_LAST_SLOT, INV_ITEM_EVENTS)
        // Run orb shows varp option_run, which the saved run mode must set (the engine does not).
        runMode = runMode
        enableSettingsEvents()
        EmoteTab.enableEvents(this)
        // The side journal opens empty; fill its active sub-tab (RS Mod `JournalTabScript.onIfOpen`).
        with(JournalTab) { open(varbit(Varbits.SIDE_JOURNAL_TAB)) }
    }

    /** Slider and button grids of the settings panel send op 1 per slot (RS Mod `SettingsSideScript.updateIfEvents`). */
    private fun PlayerRef.enableSettingsEvents() {
        val op1 = IfEvents.op(1)
        for (slider in SETTINGS_SLIDERS) ifEvents(slider, 0..SLIDER_LAST_SLOT, op1)
        ifEvents(Components.Settings_Side.ATTACK_PRIORITY_PLAYER_BUTTONS, 1..PLAYER_PRIORITY_LAST, op1)
        ifEvents(Components.Settings_Side.ATTACK_PRIORITY_NPC_BUTTONS, 1..NPC_PRIORITY_LAST, op1)
    }

    private companion object {
        /** Volume and brightness sliders have bobble slots 0..21. */
        const val SLIDER_LAST_SLOT = 21
        const val PLAYER_PRIORITY_LAST = 5
        const val NPC_PRIORITY_LAST = 4

        val SETTINGS_SLIDERS: List<Int> =
            listOf(
                Components.Settings_Side.MASTER_BOBBLE_CONTAINER,
                Components.Settings_Side.MUSIC_BOBBLE_CONTAINER,
                Components.Settings_Side.SOUND_BOBBLE_CONTAINER,
                Components.Settings_Side.AREASOUNDS_BOBBLE_CONTAINER,
                Components.Settings_Side.BRIGHTNESS_BOBBLE_CONTAINER,
            )

        /** Highest world id the switcher can send (world ids are u16 in LOGOUT_TRANSFER; OSRS uses up to ~600). */
        const val MAX_WORLD_ID = 2047

        /** Backpack slots 0..27. */
        const val INV_LAST_SLOT = 27

        @Suppress("MagicNumber") // op numbers
        val INV_ITEM_EVENTS: Long =
            IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(6) or IfEvents.op(7) or IfEvents.op(10) or
                IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
                IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET

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
