package tp.content.quests

import tp.plugin.PlayerRef
import tp.symbols.DbRows
import tp.symbols.Varbits

/**
 * Every quest of the cache's `Quest` table (219 rows), read once from `quest-states.tsv` (docs/mechanics/quests.md QU1). The file
 * is derived from cache 2757: db table 0 (columns 0, 2, 3, 4, 5, 17, 18, 19, 21) and client script 4024, which maps a quest
 * row to the varp or varbit that holds its state. [QuestFramework.verify] compares it with the live db rows at world start.
 */
internal object QuestBook {
    val all: List<Quest> by lazy { load() }

    private val byRow: Map<Int, Quest> by lazy { all.associateBy { it.row } }
    private val byId: Map<Int, Quest> by lazy { all.associateBy { it.id } }

    fun row(row: Int): Quest = byRow[row] ?: error("no quest row $row")

    fun byQuestId(id: Int): Quest? = byId[id]

    /** Quests of the "Completed: x/y" count that [p] has finished. */
    fun completed(p: PlayerRef): Int = all.count { it.counts && it.state(p) == QuestState.FINISHED }

    /** Total of the "Completed: x/y" count (OPEN-QU3: the real server's rule for y is not published). */
    val total: Int by lazy { all.count { it.counts } }

    /** Quest points of every quest of the count (OPEN-QU3), the "Quest Points: x/y" maximum (varbit `qp_max`). */
    val maxPoints: Int by lazy { all.filter { it.counts }.sumOf { it.points.coerceAtLeast(0) } }

    /**
     * Brings the quest list's counters up to date: varbits `quests_completed_count`, `quests_total_count` and `qp_max`
     * (client scripts 5995 and 1356 read them; the server owns them, docs/mechanics/quests.md QU7).
     */
    fun refreshCounts(p: PlayerRef) {
        p.setVarbit(Varbits.QUESTS_COMPLETED_COUNT, completed(p))
        p.setVarbit(Varbits.QUESTS_TOTAL_COUNT, total)
        p.setVarbit(Varbits.QP_MAX, maxPoints)
    }

    private fun load(): List<Quest> {
        val text = QuestBook::class.java.getResource("quest-states.tsv")?.readText() ?: error("quest-states.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val c = line.split('\t')
                Quest(
                    row = c[COL_ROW].toInt(),
                    id = c[COL_ID].toInt(),
                    name = c[COL_NAME],
                    isVarbit = c[COL_KIND] == "varbit",
                    varId = c[COL_VAR].toInt(),
                    unstarted = c[COL_UNSTARTED].toInt(),
                    end = c[COL_END].toInt(),
                    points = c[COL_POINTS].toInt(),
                    members = c[COL_MEMBERS] == "1",
                    counts = c[COL_COUNTS] == "1",
                )
            }.toList()
    }

    /** Columns of `quest-states.tsv` (header row 1). */
    private const val COL_ROW = 0
    private const val COL_ID = 1
    private const val COL_NAME = 2
    private const val COL_KIND = 3
    private const val COL_VAR = 4
    private const val COL_UNSTARTED = 5
    private const val COL_END = 6
    private const val COL_POINTS = 7
    private const val COL_MEMBERS = 8
    private const val COL_COUNTS = 9
}

/** The quests this module implements (rows of `tp.symbols.DbRows`). */
internal object Quests {
    val COOKS_ASSISTANT: Quest by lazy { QuestBook.row(DbRows.QUEST_COOKSASSISTANT) }
    val SHEEP_SHEARER: Quest by lazy { QuestBook.row(DbRows.QUEST_SHEEPSHEARER) }
    val RUNE_MYSTERIES: Quest by lazy { QuestBook.row(DbRows.QUEST_RUNEMYSTERIES) }
    val ROMEO_AND_JULIET: Quest by lazy { QuestBook.row(DbRows.QUEST_ROMEOANDJULIET) }
    val RESTLESS_GHOST: Quest by lazy { QuestBook.row(DbRows.QUEST_RESTLESSGHOST) }
    val IMP_CATCHER: Quest by lazy { QuestBook.row(DbRows.QUEST_IMPCATCHER) }
    val DORICS_QUEST: Quest by lazy { QuestBook.row(DbRows.QUEST_DORICS) }
    val WITCHS_POTION: Quest by lazy { QuestBook.row(DbRows.QUEST_WITCHSPOTION) }
    val ERNEST_THE_CHICKEN: Quest by lazy { QuestBook.row(DbRows.QUEST_ERNESTTHECHICKEN) }
    val GOBLIN_DIPLOMACY: Quest by lazy { QuestBook.row(DbRows.QUEST_GOBLINDIPLOMACY) }
    val KNIGHTS_SWORD: Quest by lazy { QuestBook.row(DbRows.QUEST_KNIGHTSSWORD) }
    val BLACK_KNIGHTS_FORTRESS: Quest by lazy { QuestBook.row(DbRows.QUEST_BLACKKNIGHTSFORTRESS) }
    val VAMPYRE_SLAYER: Quest by lazy { QuestBook.row(DbRows.QUEST_VAMPYRESLAYER) }
    val DEMON_SLAYER: Quest by lazy { QuestBook.row(DbRows.QUEST_DEMONSLAYER) }
    val ENTER_THE_ABYSS: Quest by lazy { QuestBook.row(DbRows.MINIQUEST_ENTERTHEABYSS) }
    val TEMPLE_OF_THE_EYE: Quest by lazy { QuestBook.row(DbRows.QUEST_TEMPLEOFTHEEYE) }
}
