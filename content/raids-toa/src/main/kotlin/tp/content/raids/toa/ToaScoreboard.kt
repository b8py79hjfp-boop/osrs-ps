package tp.content.raids.toa

import tp.content.raids.Raids
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits

/**
 * The lobby scoreboard 775 (toa.md TA66; client-contract 2.7). The tab is varbit 14320 (0 Normal, 1 Entry, 2 Expert); the
 * client switches it locally and the click reaches the server, which mirrors it and sends the cells for that mode as text
 * (OPEN-TC6: the cell format is not in the cache; the times use the scoreboard's `m:ss.cc`). Personal values are the
 * player's saves ([ToaRecords.key]); the Global column is this world's counters (OPEN-TA31: no global service).
 */
internal object ToaScoreboard {
    /** Client-contract 2.7: `TOA_SCOREBOARD_TAB` values. */
    const val TAB_NORMAL = 0
    const val TAB_ENTRY = 1
    const val TAB_EXPERT = 2

    private const val NO_VALUE = "-"
    private const val NO_SLOT = -1
    private const val OP_VIEW = 1

    /** Per party size 1..8: personal overall, personal room (challenge), global overall, global room (client-contract 2.7). */
    private val TIME_CELLS: List<IntArray> =
        listOf(
            cells(
                Components.Toa_Scoreboard.DATA_SOLO_P_O,
                Components.Toa_Scoreboard.DATA_SOLO_P_R,
                Components.Toa_Scoreboard.DATA_SOLO_G_O,
                Components.Toa_Scoreboard.DATA_SOLO_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_2MAN_P_O,
                Components.Toa_Scoreboard.DATA_2MAN_P_R,
                Components.Toa_Scoreboard.DATA_2MAN_G_O,
                Components.Toa_Scoreboard.DATA_2MAN_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_3MAN_P_O,
                Components.Toa_Scoreboard.DATA_3MAN_P_R,
                Components.Toa_Scoreboard.DATA_3MAN_G_O,
                Components.Toa_Scoreboard.DATA_3MAN_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_4MAN_P_O,
                Components.Toa_Scoreboard.DATA_4MAN_P_R,
                Components.Toa_Scoreboard.DATA_4MAN_G_O,
                Components.Toa_Scoreboard.DATA_4MAN_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_5MAN_P_O,
                Components.Toa_Scoreboard.DATA_5MAN_P_R,
                Components.Toa_Scoreboard.DATA_5MAN_G_O,
                Components.Toa_Scoreboard.DATA_5MAN_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_6MAN_P_O,
                Components.Toa_Scoreboard.DATA_6MAN_P_R,
                Components.Toa_Scoreboard.DATA_6MAN_G_O,
                Components.Toa_Scoreboard.DATA_6MAN_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_7MAN_P_O,
                Components.Toa_Scoreboard.DATA_7MAN_P_R,
                Components.Toa_Scoreboard.DATA_7MAN_G_O,
                Components.Toa_Scoreboard.DATA_7MAN_G_R,
            ),
            cells(
                Components.Toa_Scoreboard.DATA_8MAN_P_O,
                Components.Toa_Scoreboard.DATA_8MAN_P_R,
                Components.Toa_Scoreboard.DATA_8MAN_G_O,
                Components.Toa_Scoreboard.DATA_8MAN_G_R,
            ),
        )

    private fun cells(vararg c: Int): IntArray = c

    private const val CELL_P_OVERALL = 0
    private const val CELL_P_ROOM = 1
    private const val CELL_G_OVERALL = 2
    private const val CELL_G_ROOM = 3

    fun open(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_SCOREBOARD)
        for (tab in intArrayOf(
            Components.Toa_Scoreboard.STORY_TAB,
            Components.Toa_Scoreboard.NORMAL_TAB,
            Components.Toa_Scoreboard.HARD_TAB,
        )) {
            p.ifEvents(tab, NO_SLOT..NO_SLOT, IfEvents.op(OP_VIEW))
        }
        send(p)
    }

    /** A tab click: the server mirrors the client's local varbit change and re-sends the cells for that mode. */
    fun tab(p: PlayerRef, value: Int) {
        if (!p.isOpen(Interfaces.TOA_SCOREBOARD)) return
        p.setVarbit(Varbits.TOA_SCOREBOARD_TAB, value)
        send(p)
    }

    private fun modeOf(p: PlayerRef): String =
        when (p.varbit(Varbits.TOA_SCOREBOARD_TAB)) {
            TAB_ENTRY -> ToaRaid.ENTRY
            TAB_EXPERT -> ToaRaid.EXPERT
            else -> ToaRaid.NORMAL
        }

    private fun send(p: PlayerRef) {
        val mode = modeOf(p)
        val world = ToaWorldStats.of(p)

        fun count(row: String) = ToaRecords.key(mode, row)
        p.ifText(Components.Toa_Scoreboard.DATA_A_P, number(p.saved(count(ToaRecords.ATTEMPTS))))
        p.ifText(Components.Toa_Scoreboard.DATA_A_G, number(world.total[count(ToaRecords.ATTEMPTS)]))
        p.ifText(Components.Toa_Scoreboard.DATA_C_P, number(p.saved(count(ToaRecords.COMPLETIONS))))
        p.ifText(Components.Toa_Scoreboard.DATA_C_G, number(world.total[count(ToaRecords.COMPLETIONS)]))
        p.ifText(Components.Toa_Scoreboard.DATA_D_P, number(p.saved(count(ToaRecords.DEATHS))))
        p.ifText(Components.Toa_Scoreboard.DATA_D_G, number(world.total[count(ToaRecords.DEATHS)]))
        for ((i, c) in TIME_CELLS.withIndex()) {
            val size = i + 1
            val overall = ToaRecords.key(mode, ToaRecords.OVERALL, size)
            val room = ToaRecords.key(mode, ToaRecords.CHALLENGE, size)
            p.ifText(c[CELL_P_OVERALL], time(p.saved(overall)))
            p.ifText(c[CELL_P_ROOM], time(p.saved(room)))
            p.ifText(c[CELL_G_OVERALL], time(world.total[overall]))
            p.ifText(c[CELL_G_ROOM], time(world.total[room]))
        }
    }

    private fun number(value: Long?): String = (value ?: 0L).toString()

    private fun time(ticks: Long?): String = if (ticks == null || ticks <= 0) NO_VALUE else Raids.formatTicks(ticks)
}

/** The lobby Scoreboard "Read" (loc 46071) and its three tabs. */
public class ToaScoreboardPlugin : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.TOA_SCOREBOARD, OP1) { ToaScoreboard.open(player) }
        onButton(Components.Toa_Scoreboard.STORY_TAB) { ToaScoreboard.tab(this, ToaScoreboard.TAB_ENTRY) }
        onButton(Components.Toa_Scoreboard.NORMAL_TAB) { ToaScoreboard.tab(this, ToaScoreboard.TAB_NORMAL) }
        onButton(Components.Toa_Scoreboard.HARD_TAB) { ToaScoreboard.tab(this, ToaScoreboard.TAB_EXPERT) }
    }

    private companion object {
        const val OP1 = 1
    }
}
