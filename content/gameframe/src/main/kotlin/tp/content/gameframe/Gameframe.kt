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
        // G7: the chatbox "Report" button opens the report window (`reportabuse` 875, init script 5084 (reports available 1, no
        // name, "")); its submit arrives as SEND_SNAPSHOT (engine).
        onButton(Components.Chatbox.REPORTABUSE, OP_REPORT_ABUSE) {
            openModal(Components.Toplevel.MAINMODAL, Interfaces.REPORTABUSE)
            clientScript(CS_REPORT_INIT, REPORTS_AVAILABLE, 0, "")
            // The rule rows are children of `reportabuse:rules` built by script 5103; their op 1 only reaches the
            // client's own handler (which sends SEND_SNAPSHOT) when transmitted.
            ifEvents(Components.Reportabuse.RULES, 0..REPORT_RULE_ROWS, IfEvents.op(1))
        }
        // G8: the "Game client layout" dropdown (1 fixed, 2 resizable classic, 3 resizable modern).
        onButton(Components.Settings_Side.DISPLAY_DYNAMIC_SETTING_1_BUTTONS) { click -> changeLayout(click.slot) }
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
        // G8: the saved layout when the client's window mode matches it, else fixed / resizable classic (RS Mod
        // `openLoginGameframe` fallback).
        val saved = saved(SAVED_LAYOUT)?.toInt()
        val top =
            when {
                !loginResizable -> Interfaces.TOPLEVEL
                saved == Interfaces.TOPLEVEL_PRE_EOC -> Interfaces.TOPLEVEL_PRE_EOC
                else -> Interfaces.TOPLEVEL_OSRS_STRETCH
            }
        setVarbit(Varbits.RESIZABLE_STONE_ARRANGEMENT, if (top == Interfaces.TOPLEVEL_PRE_EOC) 1 else 0)
        openTop(top)
        for ((iface, target) in OVERLAYS) openOverlay(target, iface)
        // Inventory tab item ops (RS Mod `InvOpenScript`).
        SideInventory.enableEvents(this)
        // Run orb shows varp option_run, which the saved run mode must set (the engine does not).
        runMode = runMode
        SettingsPanel.enableEvents(this)
        EmoteTab.enableEvents(this)
        // The side journal opens empty; fill its active sub-tab (RS Mod `JournalTabScript.onIfOpen`).
        with(JournalTab) { open(varbit(Varbits.SIDE_JOURNAL_TAB)) }
        // G11: the backpack tab is the selected side tab after login (client script 915 `toplevel_sidebutton_switch`
        // sets varc `toplevel_panel` 171; RS Mod `focusTab`). Without it a fresh client shows no side panel.
        clientScript(CS_SIDEBUTTON_SWITCH, PANEL_INVENTORY)
    }

    /**
     * G8 (RS Mod `DisplaySettingsScript` / `GameframeScript.queueGameframeMove`): a change between fixed and resizable first
     * asks the client to switch its window mode (client script 3998 with the mode 0 / 1 / 2), and the toplevel moves 2
     * ticks later (1 when the window mode stays), on a soft timer so a modal does not stop it.
     */
    private fun PlayerRef.changeLayout(sub: Int) {
        val target = LAYOUTS.getOrNull(sub - 1) ?: return
        val current = toplevel
        if (target == current) return
        val windowChange = (target == Interfaces.TOPLEVEL) != (current == Interfaces.TOPLEVEL)
        if (windowChange) clientScript(CS_CLIENT_MODE, sub - 1)
        timer(LAYOUT_TIMER, if (windowChange) 2 else 1, soft = true) {
            stopTimer(LAYOUT_TIMER)
            if (target != Interfaces.TOPLEVEL) {
                setVarbit(Varbits.RESIZABLE_STONE_ARRANGEMENT, if (target == Interfaces.TOPLEVEL_PRE_EOC) 1 else 0)
            }
            moveTop(target)
            setSaved(SAVED_LAYOUT, target.toLong())
            // RS Mod reopens the orbs after the move (their init script lays them out for the new frame).
            openOverlay(Components.Toplevel.ORBS, Interfaces.ORBS)
        }
    }

    private companion object {
        const val CS_SIDEBUTTON_SWITCH = 915

        /** `chatbox:reportabuse` op 2 "Report abuse" (op 4 "Report game bug" has no window in rev 242, OPEN-G7). */
        const val OP_REPORT_ABUSE = 2

        /** Varc 224 for script 5105: 0 answers "System unavailable", anything else sends the report. */
        const val REPORTS_AVAILABLE = 1

        /** Rule rows created under `reportabuse:rules` (5 children per rule, < 30 rules). */
        const val REPORT_RULE_ROWS = 255

        /** `reportabuse` init (varc 224 = source, flag + name to prefill). */
        const val CS_REPORT_INIT = 5084

        /** `settings_client_mode` (RS Mod `runClientScript(3998, clientMode)`): 0 fixed, 1 resizable, 2 resizable modern. */
        const val CS_CLIENT_MODE = 3998

        /** Dropdown rows 1..3 (RS Mod `toggleClientType`). */
        val LAYOUTS = listOf(Interfaces.TOPLEVEL, Interfaces.TOPLEVEL_OSRS_STRETCH, Interfaces.TOPLEVEL_PRE_EOC)

        const val SAVED_LAYOUT = "gameframe_toplevel"
        val LAYOUT_TIMER = tp.plugin.TimerId("gameframe_layout")

        /** Side panel index of the backpack (varc `toplevel_panel`: 0 combat, 1 skills, 2 quests, 3 inventory). */
        const val PANEL_INVENTORY = 3

        /** Highest world id the switcher can send (world ids are u16 in LOGOUT_TRANSFER; OSRS uses up to ~600). */
        const val MAX_WORLD_ID = 2047

        val OVERLAYS: List<Pair<Int, Int>> =
            listOf(
                Interfaces.CHATBOX to Components.Toplevel.CHAT_CONTAINER,
                Interfaces.BUFF_BAR to Components.Toplevel.BUFF_BAR,
                Interfaces.STAT_BOOSTS_HUD to Components.Toplevel.STAT_BOOSTS_HUD,
                Interfaces.PM_CHAT to Components.Toplevel.PM_CONTAINER,
                Interfaces.HPBAR_HUD to Components.Toplevel.HPBAR_HUD,
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
