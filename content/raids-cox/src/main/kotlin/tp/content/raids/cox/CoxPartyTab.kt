package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits

/**
 * The Raiding Party side tab 500 (cox.md CX4; client-contract 2.5 and 3): open for the members inside the Chambers (the
 * lobby room and beyond). Script 1540 draws the header and the status line from the varbits [CoxHud] writes (5423 leader,
 * 5424 size, 5425 progress) and the two written here (12285 highest combat, 9540 scaled size), and shows "Start raid" to
 * the leader while progress is 0. The member rows are server-built: 1547(count), then 1548(row, `name|combat|skilltotal`).
 */
internal object CoxPartyTab {
    /** Roster, highest combat and scaled size are checked this often (ticks); like the ToB lobby text, not every tick. */
    private const val SYNC_TICKS = 5L

    fun tick(world: WorldRef) {
        if (world.tick % SYNC_TICKS != 0L) return
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != CoxRaid.ID || run.state == RunState.Closed) continue
            val inst = run.instance ?: continue
            var rows: List<String>? = null
            for (m in run.members.values) {
                if (!m.inside || !m.online) continue
                val p = world.onlinePlayer(m.name)?.takeIf { it.coord in inst } ?: continue
                if (!p.isOpen(Interfaces.RAIDS_SIDEPANEL)) {
                    p.openOverlay(TAB_SLOT, Interfaces.RAIDS_SIDEPANEL)
                    p[SENT] = null
                }
                setIfChanged(p, Varbits.RAIDS_CLIENT_HIGHESTCOMBAT, run.scaling.highestCombatLevel.coerceAtMost(MAX_SEVEN_BITS))
                setIfChanged(p, Varbits.RAIDS_CLIENT_PARTYSIZE_SCALED, run.scaling.scaledSize.coerceAtMost(MAX_SEVEN_BITS))
                val current = rows ?: rosterRows(p, run).also { rows = it }
                if (p[SENT] != current) send(p, current)
            }
        }
    }

    /** IF_BUTTON1 on 500:2 "Refresh": the rows are sent again at once. */
    fun refresh(p: PlayerRef) {
        val run = CoxFlow.runOf(p) ?: return
        send(p, rosterRows(p, run))
    }

    /** The tab goes back to the channels tab when the player leaves the raid. */
    fun leave(p: PlayerRef) {
        p[SENT] = null
        if (p.isOpen(Interfaces.RAIDS_SIDEPANEL)) p.openOverlay(TAB_SLOT, Interfaces.SIDE_CHANNELS)
    }

    private fun send(p: PlayerRef, rows: List<String>) {
        p[SENT] = rows
        p.clientScript(SCRIPT_ROWS, rows.size)
        for (i in rows.indices) p.clientScript(SCRIPT_ROW, i, rows[i])
    }

    private fun rosterRows(p: PlayerRef, run: RaidRun): List<String> = run.party.members.map { CoxLobbyWorld.tabRow(p, it) }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    private val SENT = AttrKey<List<String>>("cox_party_tab_rows")

    /**
     * OPEN-CXUI3 (client-contract 9.4: the cache does not name the gameframe slot of 500): the friends / channels tab
     * (`SIDE_CHANNELS` in `SIDE7`), since the raid party is the leader's friends-chat channel (client-contract 2.1).
     */
    private const val TAB_SLOT = Components.Toplevel.SIDE7

    /** Server-run client scripts 1547(count) and 1548(row, string) (client-contract 2.5). */
    private const val SCRIPT_ROWS = 1547
    private const val SCRIPT_ROW = 1548

    /** Varbits 12285 and 9540 are 7 bits wide (client-contract 1.2). */
    private const val MAX_SEVEN_BITS = 127
}

/**
 * "Start raid" on the Raiding Party tab: the same start as walking on through the lobby passage (cox.md CX4;
 * [CoxFlow.beginRaid], the same start as the lobby passage).
 */
internal object CoxStart {
    /** IF_BUTTON1 on 500:14, shown by the client only to the leader while the raid has not begun (client-contract 2.5). */
    fun fromTab(player: PlayerRef) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        if (st.begun) {
            player.message(ALREADY_BEGUN)
            return
        }
        if (!run.party.leader.equals(player.username, ignoreCase = true)) {
            player.message(LEADER_STARTS)
            return
        }
        val cell = CoxMap.cellAt(st.plan, run, player.coord)
        if (cell == null || cell.kind != CoxKind.START || cell.floor != 0) {
            player.message(IN_LOBBY_ROOM)
            return
        }
        CoxFlow.beginRaid(player, run, st)
    }

    // OPEN-CX3: these lines are not captured.
    private const val ALREADY_BEGUN = "Your party's raid has already begun."
    private const val LEADER_STARTS = "Only the party leader can start the raid."
    private const val IN_LOBBY_ROOM = "You must be in the starting room to start the raid."
}
