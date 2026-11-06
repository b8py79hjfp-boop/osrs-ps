package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * Side journal tab (docs/mechanics/gameframe-buttons.md J1..J3): Summary / Quest List / Tasks sub-tabs. Behaviour
 * ported from RS Mod `JournalTabScript` and `journal-tab/PlayerExtensions` (ISC, DECISIONS D-027).
 */
public class JournalTab : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Side_Journal.SUMMARY_LIST) { switchTo(TAB_SUMMARY) }
        onButton(Components.Side_Journal.QUEST_LIST) { switchTo(TAB_QUESTS) }
        onButton(Components.Side_Journal.TASK_LIST) { switchTo(TAB_TASKS) }
    }

    private fun PlayerRef.switchTo(tab: Int) {
        setVarbit(Varbits.SIDE_JOURNAL_TAB, tab)
        open(tab)
    }

    internal companion object {
        /** `side_journal_tab` values (RS Mod `SideJournalTab`). */
        const val TAB_SUMMARY = 0
        const val TAB_QUESTS = 1
        const val TAB_TASKS = 2

        /**
         * Opens the sub-interface of [tab] in the journal's tab container; the client replaces the previous one. The
         * summary needs its two client scripts afterwards (RS Mod `updateSummaryTimePlayed` / `updateSummaryCombatLevel`).
         */
        fun PlayerRef.open(tab: Int) {
            when (tab) {
                TAB_QUESTS -> {
                    openOverlay(Components.Side_Journal.TAB_CONTAINER, Interfaces.QUESTLIST)
                }

                TAB_TASKS -> {
                    openOverlay(Components.Side_Journal.TAB_CONTAINER, Interfaces.AREA_TASK)
                }

                else -> {
                    openOverlay(Components.Side_Journal.TAB_CONTAINER, Interfaces.ACCOUNT_SUMMARY_SIDEPANEL)
                    val contents = Components.Account_Summary_Sidepanel.SUMMARY_CONTENTS
                    val layer = Components.Account_Summary_Sidepanel.SUMMARY_CLICK_LAYER
                    // OPEN-J1: time played has no varp in our cache symbols; shown as 0 minutes.
                    clientScript(CS_SUMMARY_PLAYTIME, contents, layer, 0)
                    clientScript(CS_SUMMARY_COMBAT_LEVEL, contents, layer, combatLevel)
                }
            }
        }

        /** Client scripts that fill the summary panel (RS Mod: 3970 time played, 3954 combat level). */
        private const val CS_SUMMARY_PLAYTIME = 3970
        private const val CS_SUMMARY_COMBAT_LEVEL = 3954
    }
}
