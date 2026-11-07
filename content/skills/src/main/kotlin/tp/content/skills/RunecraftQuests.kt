package tp.content.skills

import tp.plugin.PlayerRef

/**
 * A quest the Runecraft content asks about. The quest state lives in a cache varp or varbit, so this module reads it
 * without depending on content/quests: [id] and [end] are the `var` and `end` columns of that module's `quest-states.tsv`
 * (docs/mechanics/quests.md QU1); the quest is finished when the variable has reached [end].
 */
internal class QuestGate(val name: String, val isVarbit: Boolean, val id: Int, val end: Int) {
    fun done(p: PlayerRef): Boolean = (if (isVarbit) p.varbit(id) else p.varp(id)) >= end
}

internal object RunecraftQuests {
    val ENTER_THE_ABYSS = QuestGate("Enter the Abyss", false, 492, 4)
    val LUNAR_DIPLOMACY = QuestGate("Lunar Diplomacy", true, 2448, 190)
    val LOST_CITY = QuestGate("Lost City", false, 147, 6)
    val TROLL_STRONGHOLD = QuestGate("Troll Stronghold", false, 317, 50)
    val MOURNINGS_END_II = QuestGate("Mourning's End Part II", true, 1103, 60)
    val SINS_OF_THE_FATHER = QuestGate("Sins of the Father", true, 7255, 138)
}
