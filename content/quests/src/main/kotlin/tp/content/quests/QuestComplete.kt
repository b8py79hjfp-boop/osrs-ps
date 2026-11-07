package tp.content.quests

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Jingles
import tp.symbols.Varps

/**
 * What a quest gives on completion (docs/mechanics/quests.md QU4). [xp] is `stat to experience`, [items] is `obj to count`,
 * [notes] are reward lines for unlocks that are not experience or items. [model] is the obj shown on the reward scroll
 * (OPEN-QU5: the real scroll's picture per quest is not published).
 */
internal class QuestReward(
    val model: Int,
    val xp: List<Pair<Int, Double>> = emptyList(),
    val items: List<Pair<Int, Int>> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Finishing a quest: variable, quest points, rewards, counters, chat line, jingle and the reward scroll. */
internal object QuestCompletion {
    /**
     * Finishes [quest] for [p] (no-op if it already is): the quest variable takes its end state, the quest points are added to
     * varp `qp`, the [reward] experience and items are given (items through one transaction), the quest list counters are
     * refreshed and the reward scroll opens. Called by the quest's last dialogue step.
     */
    fun complete(p: PlayerRef, quest: Quest, reward: QuestReward) {
        if (quest.state(p) == QuestState.FINISHED) return
        quest.set(p, quest.end)
        val points = quest.points.coerceAtLeast(0)
        p.setVarp(Varps.QP, p.varp(Varps.QP) + points)
        for ((stat, xp) in reward.xp) p.addXp(stat, xp)
        if (reward.items.isNotEmpty()) {
            p.transaction("quest reward ${quest.name}") {
                reward.items.all { (obj, count) -> addOrDrop(Invs.INV, obj, count) }
            }
        }
        QuestBook.refreshCounts(p)
        p.message(COMPLETED_LINE + quest.name + COMPLETED_END)
        p.jingle(Jingles.QUEST_COMPLETE_1)
        scroll(p, quest, reward)
    }

    /**
     * Opens `questscroll` (interface 153) as the main modal and fills it: "Congratulations!", the quest name, the quest points
     * and up to 7 reward lines (components `quest_reward1..7`), plus the reward obj on the model (docs/mechanics/quests.md QU5).
     */
    fun scroll(p: PlayerRef, quest: Quest, reward: QuestReward) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.QUESTSCROLL)
        p.ifText(Components.Questscroll.CONGRATULATIONS, "Congratulations!")
        p.ifText(Components.Questscroll.QUEST_TITLE, "You have completed ${quest.name}!")
        val points = quest.points.coerceAtLeast(0)
        p.ifText(Components.Questscroll.QUEST_POINTS, if (points == 1) "1 Quest Point" else "$points Quest Points")
        p.ifText(Components.Questscroll.AWARD_TEXT, "You are awarded:")
        p.ifObject(Components.Questscroll.QUEST_MODEL, reward.model)
        val lines = ArrayList<String>()
        for ((stat, xp) in reward.xp) lines += "${xp.toInt()} ${Stats.name(stat)} XP"
        for ((obj, count) in reward.items) lines += "$count ${p.objName(obj)}"
        lines += reward.notes
        for ((i, component) in REWARD_LINES.withIndex()) {
            p.ifText(component, lines.getOrElse(i) { "" })
        }
    }

    /** The chat line RuneLite matches for finished quests (`ChatCommandsPlugin` "Congratulations, you've completed a quest: "). */
    private const val COMPLETED_LINE = "Congratulations, you've completed a quest: <col=0000ff>"
    private const val COMPLETED_END = "</col>"

    /** `questscroll` components `quest_reward1..7` (symbols `Components.Questscroll`). */
    private val REWARD_LINES =
        intArrayOf(
            Components.Questscroll.QUEST_REWARD1,
            Components.Questscroll.QUEST_REWARD2,
            Components.Questscroll.QUEST_REWARD3,
            Components.Questscroll.QUEST_REWARD4,
            Components.Questscroll.QUEST_REWARD5,
            Components.Questscroll.QUEST_REWARD6,
            Components.Questscroll.QUEST_REWARD7,
        )
}
