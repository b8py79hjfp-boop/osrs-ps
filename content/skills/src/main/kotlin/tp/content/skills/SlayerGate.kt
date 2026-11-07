package tp.content.skills

import tp.plugin.PlayerRef

/**
 * Evaluates the requirement expressions of `slayer-requirements.tsv` and `slayer-bosses.tsv` (docs/mechanics/slayer.md SL10):
 * `&` joins clauses that must all hold, `|` joins the alternatives of a clause, and an atom is
 * `quest:<Quest>:complete|started`, `unlock:<Unlock name>` or `skill:<Skill>:<level>`.
 *
 * A quest is read from its state variable like content/diaries `QuestGate` does: complete = the quest's end value or more,
 * started = past the unstarted value. A quest that is not in `slayer-quests.tsv`, or an unlock the cache lacks, is never met.
 * Quests that no content module implements stay at their unstarted value, so the tasks that need them are never assigned.
 */
internal class SlayerGate(private val cache: SlayerCache) {
    /** Whether [player] meets [expr]; an empty expression is always met. */
    fun met(player: PlayerRef, expr: String): Boolean {
        if (expr.isEmpty()) return true
        return expr.split('&').all { clause -> clause.split('|').any { atom(player, it) } }
    }

    private fun atom(player: PlayerRef, atom: String): Boolean {
        val parts = atom.split(':')
        return when (parts[0]) {
            QUEST -> quest(player, parts[1], parts[2])
            UNLOCK -> cache.unlockOrNull(parts[1])?.let { SlayerState.unlocked(player, it.bit) } == true
            SKILL -> SlayerData.stat(parts[1]).let { it >= 0 && player.baseLevel(it) >= parts[2].toInt() }
            else -> error("slayer requirement atom '$atom'")
        }
    }

    private fun quest(player: PlayerRef, name: String, mode: String): Boolean {
        val v = SlayerData.QUESTS[name] ?: return false
        val stage = if (v.varbit) player.varbit(v.id) else player.varp(v.id)
        // "partial completion" and "started" are past the unstarted state: the Wiki gives no stage (OPEN-SL8).
        return if (mode == COMPLETE) stage >= v.end else stage > v.unstarted
    }

    private companion object {
        const val QUEST = "quest"
        const val UNLOCK = "unlock"
        const val SKILL = "skill"
        const val COMPLETE = "complete"
    }
}
