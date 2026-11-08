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
import tp.symbols.Varps

/**
 * The in-raid HUD 513 (client-contract 2.2): `Total:` = varbit 5431 (team points), the player's own line = varp 4609,
 * `Time:` = varbit 6386, which the client advances by itself once a tick while progress 5425 is below 5 (the server
 * writes it to start and to resync). Also the party varbits the side tab and the lobby steps read (5423 leader, 5424
 * size, 5432 in the dungeon, 6385 Challenge Mode, 5408 map pool). Only changed values are sent.
 */
internal object CoxHud {
    fun tick(world: WorldRef) {
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != CoxRaid.ID) continue
            val st = CoxFlow.state(run) ?: continue
            val inst = run.instance ?: continue
            val team =
                CoxPoints
                    .team(run)
                    .toLong()
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            val progress = progress(run, st)
            val ticks = elapsed(run, st, world.tick)
            for (m in run.members.values) {
                if (!m.inside || !m.online) continue
                val p = world.onlinePlayer(m.name)?.takeIf { it.coord in inst } ?: continue
                show(p, run, st, team, m.points, progress, ticks)
            }
        }
    }

    private fun show(p: PlayerRef, run: RaidRun, st: CoxState, team: Int, personal: Double, progress: Int, ticks: Int) {
        if (!p.isOpen(Interfaces.RAIDS_OVERLAY)) {
            p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.RAIDS_OVERLAY)
            p[TIMER_SENT] = null
        }
        p[IN_HUD] = run.id.value
        setIfChanged(p, Varbits.RAIDS_CLIENT_INDUNGEON, 1)
        setIfChanged(p, Varbits.RAIDS_CLIENT_ISLEADER, if (run.party.leader.equals(p.username, ignoreCase = true)) 1 else 0)
        setIfChanged(p, Varbits.RAIDS_CLIENT_PARTYSIZE, run.scaling.partySize.coerceAtMost(MAX_SIZE))
        setIfChanged(p, Varbits.RAIDS_CHALLENGE_MODE, if (run.scaling.mode == CoxRaid.CHALLENGE) 1 else 0)
        setIfChanged(p, Varbits.RAIDS_MAP_POOL_SELECTED, if (run.scaling.mode == CoxRaid.CHALLENGE) POOL_FULL else POOL_REGULAR)
        setIfChanged(p, Varbits.RAIDS_CLIENT_PARTYSCORE, team)
        val own = personal.toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (p.varp(Varps.RAIDS_PLAYERSCORE) != own) p.setVarp(Varps.RAIDS_PLAYERSCORE, own)
        // The timer: written when it starts, when it stops and when the HUD opens; the client counts in between.
        val key = progress * PROGRESS_KEY + if (st.begun) 1 else 0
        if (p[TIMER_SENT] != key) {
            p[TIMER_SENT] = key
            setIfChanged(p, Varbits.RAIDS_CLIENT_PROGRESS, progress)
            p.setVarbit(Varbits.RAIDS_TIMER, ticks.coerceAtMost(MAX_TIMER))
        }
    }

    /** Takes the HUD off a player leaving the raid. */
    fun leave(p: PlayerRef) {
        if (p[IN_HUD] == null) return
        p[IN_HUD] = null
        p[TIMER_SENT] = null
        if (p.isOpen(Interfaces.RAIDS_OVERLAY)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        setIfChanged(p, Varbits.RAIDS_CLIENT_INDUNGEON, 0)
        setIfChanged(p, Varbits.RAIDS_CLIENT_PROGRESS, 0)
        setIfChanged(p, Varbits.RAIDS_TIMER, 0)
        setIfChanged(p, Varbits.RAIDS_CLIENT_PARTYSCORE, 0)
        if (p.varp(Varps.RAIDS_PLAYERSCORE) != 0) p.setVarp(Varps.RAIDS_PLAYERSCORE, 0)
    }

    /**
     * Progress 5425 (client-contract 2.2): 0 before the start, 1 on the upper level, then the "middle" / "lower" values,
     * 4 in the Olm's room, 5 when the raid is over. Two-floor raids show "lower" on their second floor (value 3; which
     * value the server writes there is [?] in the contract).
     */
    fun progress(run: RaidRun, st: CoxState): Int =
        when {
            !st.begun -> 0
            run.state == RunState.Completed || st.olmDeadTick >= 0 -> PROGRESS_OVER
            st.floorReached >= st.plan.floors -> PROGRESS_OLM
            st.floorReached == 0 -> 1
            st.plan.floors == 2 -> PROGRESS_LOWER
            else -> 1 + st.floorReached
        }

    /** Ticks since the leader started the raid (to the Olm's death once it is over). */
    fun elapsed(run: RaidRun, st: CoxState, now: Long): Int {
        if (!st.begun) return 0
        val end =
            if (st.olmDeadTick >= 0) {
                st.olmDeadTick
            } else if (run.endedTick >= 0) {
                run.endedTick
            } else {
                now
            }
        return (end - st.begunTick).coerceIn(0, MAX_TIMER.toLong()).toInt()
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    private val IN_HUD = AttrKey<Long>("cox_hud")
    private val TIMER_SENT = AttrKey<Int>("cox_hud_timer")
    private const val PROGRESS_KEY = 2
    private const val PROGRESS_LOWER = 3
    private const val PROGRESS_OLM = 4
    private const val PROGRESS_OVER = 5
    private const val POOL_REGULAR = 0
    private const val POOL_FULL = 2

    /** Varbit widths (client-contract 1.2): 5424 is 7 bits, 6386 is 16 bits. */
    private const val MAX_SIZE = 127
    private const val MAX_TIMER = 65_535
}
