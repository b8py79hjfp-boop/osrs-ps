package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces

/**
 * The skill guide (docs/mechanics/gameframe-settings.md G9): op 2 of a skill in the Skills tab (`stats` 320; the client's
 * hotkey script 7780 triggers the same op) opens `skill_guide_v2` (860) in the main modal and runs its init script 1902
 * (guide id, tab, 0, 0). The guide draws itself from the cache and keeps its own state in varcs 1168..1173; its tab
 * buttons are client-side. Guide ids are enum 108's keys (1 Attack .. 24 Sailing).
 */
public class SkillGuide : ContentPlugin {
    override fun ContentScope.register() {
        for ((component, guide) in GUIDES) onButton(component, OP_GUIDE) { open(guide) }
        onButton(Components.Skill_Guide_V2.CLOSE) { closeModal() }
    }

    private fun PlayerRef.open(guide: Int) {
        openModal(Components.Toplevel.MAINMODAL, Interfaces.SKILL_GUIDE_V2)
        clientScript(CS_GUIDE_INIT, guide, FIRST_TAB, 0, 0)
        ifEvents(Components.Skill_Guide_V2.CLOSE, -1..-1, IfEvents.op(1))
    }

    private companion object {
        /** `stats` skill op 2 (cache op slot 1, text set by the client). */
        const val OP_GUIDE = 2

        /** Client script 1902: guide init (sets varcs 1172 / 1173, the window and its timers, then draws via 1904). */
        const val CS_GUIDE_INIT = 1902

        const val FIRST_TAB = 0

        /** Stats component -> guide id (enum 108: 1 Attack, 2 Strength, 3 Ranged, 4 Magic, 5 Defence, 6 Hitpoints, ...). */
        val GUIDES: List<Pair<Int, Int>> =
            listOf(
                Components.Stats.ATTACK to 1,
                Components.Stats.STRENGTH to 2,
                Components.Stats.RANGED to 3,
                Components.Stats.MAGIC to 4,
                Components.Stats.DEFENCE to 5,
                Components.Stats.HITPOINTS to 6,
                Components.Stats.PRAYER to 7,
                Components.Stats.AGILITY to 8,
                Components.Stats.HERBLORE to 9,
                Components.Stats.THIEVING to 10,
                Components.Stats.CRAFTING to 11,
                Components.Stats.RUNECRAFT to 12,
                Components.Stats.MINING to 13,
                Components.Stats.SMITHING to 14,
                Components.Stats.FISHING to 15,
                Components.Stats.COOKING to 16,
                Components.Stats.FIREMAKING to 17,
                Components.Stats.WOODCUTTING to 18,
                Components.Stats.FLETCHING to 19,
                Components.Stats.SLAYER to 20,
                Components.Stats.FARMING to 21,
                Components.Stats.CONSTRUCTION to 22,
                Components.Stats.HUNTER to 23,
                Components.Stats.SAILING to 24,
            )
    }
}
