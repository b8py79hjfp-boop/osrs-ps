package tp.content.quests

import tp.plugin.PlayerRef

/** What the client's quest list shows for a quest: red, yellow or green (client script 4029, docs/mechanics/quests.md QU2). */
internal enum class QuestState { NOT_STARTED, IN_PROGRESS, FINISHED }

/**
 * One row of the cache's `Quest` db table (table 0) plus the variable the client reads its state from. The values come from
 * `quest-states.tsv` ([QuestBook]); the variable is the one client script 4024 returns for the row, [unstarted] and [end]
 * are columns 18 and 19 of the row (docs/mechanics/quests.md QU1).
 */
internal class Quest(
    /** Row id in db table `Quest` (`tp.symbols.DbRows.QUEST_*`). */
    val row: Int,
    /** Column 0: the quest id; the quest list's children are indexed by it. */
    val id: Int,
    val name: String,
    /** True when [varId] is a varbit, false for a varp. */
    val isVarbit: Boolean,
    val varId: Int,
    /** Variable value of a quest that is not started (column 18, default 0). */
    val unstarted: Int,
    /** Variable value of a finished quest (column 19). */
    val end: Int,
    /** Quest points (column 17 "questpoints_override"), or -1 when the row has none. */
    val points: Int,
    val members: Boolean,
    /** Whether the quest is part of the "Completed: x/y" count of the quest list (docs/mechanics/quests.md QU7). */
    val counts: Boolean,
) {
    /** The raw variable value: 0 until the quest is started, [end] once it is finished. */
    fun stage(p: PlayerRef): Int = if (isVarbit) p.varbit(varId) else p.varp(varId)

    fun state(p: PlayerRef): QuestState = stateOf(stage(p))

    /** Client script 4029: [end] or more is finished, [unstarted] or less is not started, anything between is in progress. */
    fun stateOf(stage: Int): QuestState =
        when {
            stage >= end -> QuestState.FINISHED
            stage <= unstarted -> QuestState.NOT_STARTED
            else -> QuestState.IN_PROGRESS
        }

    /** Writes the quest variable (the client colours the list from it). Content moves stages only through [advance]. */
    fun set(p: PlayerRef, value: Int) {
        if (isVarbit) p.setVarbit(varId, value) else p.setVarp(varId, value)
    }

    /** True once the quest is at least at stage [from] (a stage that is past or current). */
    fun atLeast(p: PlayerRef, from: Int): Boolean = stage(p) >= from

    /** Moves the quest to stage [to]; never backwards and never past a finished quest. Returns true when the stage changed. */
    fun advance(p: PlayerRef, to: Int): Boolean {
        val now = stage(p)
        if (to <= now || now >= end) return false
        set(p, to)
        return true
    }

    override fun toString(): String = "Quest($row $name)"
}
