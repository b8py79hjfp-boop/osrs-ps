package tp.content.diaries

import tp.plugin.PlayerRef

/**
 * Quest requirements of tasks (docs/mechanics/achievement-diaries.md AD2). The state of a quest is the cache variable that
 * content/quests also reads (`quest-states.tsv`); `diary-quests.tsv` repeats the rows of the quests whose content exists, written
 * by tools/wiki-import/achievement_diaries.py from that file. A quest that has no row is not implemented, so nothing could
 * satisfy it and it is not checked (OPEN-AD2).
 */
internal class QuestGate private constructor(private val vars: Map<String, QuestVar>) {
    private class QuestVar(val varbit: Boolean, val id: Int, val unstarted: Int, val end: Int)

    /** Whether [player] meets the quest requirements [reqs]; quests without a row pass. */
    fun met(player: PlayerRef, reqs: List<QuestReq>): Boolean {
        for (req in reqs) {
            val v = vars[req.name.lowercase()] ?: continue
            val stage = if (v.varbit) player.varbit(v.id) else player.varp(v.id)
            // "complete" = the quest's end state or later; "started" and "partial" = past the unstarted state (OPEN-AD14: the
            // stage a "partial completion" asks for is not in the Wiki table).
            val ok = if (req.mode == COMPLETE) stage >= v.end else stage > v.unstarted
            if (!ok) return false
        }
        return true
    }

    companion object {
        private const val TSV = "diary-quests.tsv"
        private const val COLUMNS = 5
        private const val COMPLETE = "complete"
        private const val VARBIT = "varbit"

        fun load(): QuestGate {
            val text = QuestGate::class.java.getResource(TSV)?.readText() ?: error("$TSV missing")
            val vars = HashMap<String, QuestVar>()
            for (line in text.lines().drop(1)) {
                if (line.isEmpty()) continue
                val c = line.split('\t')
                check(c.size >= COLUMNS) { "$TSV: expected $COLUMNS columns in '$line'" }
                vars[c[0].lowercase()] = QuestVar(c[1] == VARBIT, c[2].toInt(), c[3].toInt(), c[4].toInt())
            }
            return QuestGate(vars)
        }
    }
}
