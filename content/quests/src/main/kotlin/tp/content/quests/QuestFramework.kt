package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.Event
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.DbTables
import tp.symbols.Interfaces

/**
 * Published when the player picks "Read journal" on a quest of the quest list: the quest's plugin appends its journal lines for
 * the quest's current stage to [lines] (docs/mechanics/quests.md QU6). A quest nobody answers for shows a fixed notice.
 */
internal class JournalRequest(val player: PlayerRef, val quest: Quest, val lines: MutableList<String>) : Event

/**
 * The quest system's shared part (docs/mechanics/quests.md): the quest list's counters at login, the "Read journal" op of the
 * quest list, and `::quest` for administrators. The state of each quest is the variable the cache names for it ([Quest]); the
 * client colours the list from that variable by itself.
 */
public class QuestFramework : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { verify() }
        onLogin { QuestBook.refreshCounts(this) }
        // "Read journal:" is op 2 of the quest list's children, which are indexed by the quest id (client script 2633).
        onButton(Components.Questlist.LIST, JOURNAL_OP) { click ->
            QuestBook.byQuestId(click.slot)?.let { openJournal(this, it) }
        }
        onCommand("quest") { args -> command(this, args) }
    }

    /**
     * Fails the world start when `quest-states.tsv` no longer matches the cache's `Quest` rows (a revision bump): every row's
     * quest id, unstarted and end state, so the file is regenerated together with the cache (docs/REVISION-UPGRADE.md).
     */
    private fun WorldRef.verify() {
        val rows = dbRows(DbTables.Quest.ID).associateBy { it.id }
        check(rows.keys == QuestBook.all.map { it.row }.toSet()) { "quest-states.tsv rows differ from the cache's Quest table" }
        for (quest in QuestBook.all) {
            val row = rows.getValue(quest.row)
            val id = row.int(DbTables.Quest.COL_ID_0)
            val unstarted = row.int(DbTables.Quest.COL_UNSTARTEDSTATE) ?: 0
            val end = row.int(DbTables.Quest.COL_ENDSTATE)
            check(
                id == quest.id && unstarted == quest.unstarted && end == quest.end,
            ) { "quest-states.tsv differs from cache for ${quest.name}" }
        }
    }

    /**
     * For other plugins (the summary panel's rows): opens the journal of the quest with quest id [questId] (column 0 of the
     * `Quest` row, the id the quest list indexes its children by). False when there is no such quest.
     */
    public fun openJournal(p: PlayerRef, questId: Int): Boolean {
        val quest = QuestBook.byQuestId(questId) ?: return false
        openJournal(p, quest)
        return true
    }

    /**
     * Opens `questjournal` (interface 119) as the main modal with the quest name as title and the lines of the quest's plugin
     * in the line components `qj1..qj199`. OPEN-QU6: the real journal's wording and colours are server-only; the scroll size
     * is the interface's own.
     */
    private fun openJournal(p: PlayerRef, quest: Quest) {
        val lines = ArrayList<String>()
        p.events.publish(JournalRequest(p, quest, lines))
        if (lines.isEmpty()) lines += NOT_IMPLEMENTED
        if (quest.state(p) == QuestState.FINISHED) lines += listOf("", COMPLETE_LINE)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.QUESTJOURNAL)
        p.ifText(Components.Questjournal.TITLE, quest.name)
        for ((i, line) in lines.take(MAX_LINES).withIndex()) p.ifText(Components.Questjournal.QJ1 + i, line)
    }

    /** `::quest` lists the stage of the quests of this module; `::quest <row> <stage>` sets it (administrators, for testing). */
    private fun command(p: PlayerRef, args: List<String>) {
        if (!p.rank.atLeast(ADMIN)) return
        val row = args.getOrNull(0)?.toIntOrNull()
        if (row == null) {
            for (q in IMPLEMENTED.map { it() } + QuestsB.ALL) p.message("${q.row} ${q.name}: stage ${q.stage(p)} (${q.state(p)})")
            return
        }
        val quest = QuestBook.all.firstOrNull { it.row == row } ?: return p.message("No quest row $row.")
        val stage = args.getOrNull(1)?.toIntOrNull() ?: return p.message("${quest.name}: stage ${quest.stage(p)} (${quest.state(p)})")
        quest.set(p, stage)
        QuestBook.refreshCounts(p)
        p.message("${quest.name} set to stage $stage.")
    }

    private companion object {
        /** Op 2 "Read journal:" of the quest list rows (client script 2633). */
        const val JOURNAL_OP = 2
        val ADMIN = tp.util.Rank.ADMINISTRATOR

        /** `questjournal` has the line components `qj1..qj199`. */
        const val MAX_LINES = 199
        const val NOT_IMPLEMENTED = "This quest is not available yet."
        const val COMPLETE_LINE = "<col=ff0000>QUEST COMPLETE!</col>"

        val IMPLEMENTED: List<() -> Quest> =
            listOf(
                { Quests.COOKS_ASSISTANT },
                { Quests.SHEEP_SHEARER },
                { Quests.RUNE_MYSTERIES },
                { Quests.ROMEO_AND_JULIET },
                { Quests.RESTLESS_GHOST },
                { Quests.IMP_CATCHER },
                { Quests.DORICS_QUEST },
                { Quests.WITCHS_POTION },
                { Quests.ERNEST_THE_CHICKEN },
                { Quests.GOBLIN_DIPLOMACY },
                { Quests.KNIGHTS_SWORD },
                { Quests.BLACK_KNIGHTS_FORTRESS },
                { Quests.VAMPYRE_SLAYER },
                { Quests.DEMON_SLAYER },
                { Quests.ENTER_THE_ABYSS },
                { Quests.TEMPLE_OF_THE_EYE },
            )
    }
}
