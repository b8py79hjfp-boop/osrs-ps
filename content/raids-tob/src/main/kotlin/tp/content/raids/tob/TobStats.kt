package tp.content.raids.tob

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits

/**
 * The scoreboard 363 and the performance panel 459 (docs/mechanics/raids/tob/lobby-rewards.md LR22 / LR23;
 * client-contract.md 2.6). No client script writes either interface's cells, so the server fills them with IF_SETTEXT.
 * Personal values are player saves (`tob.stats.<mode>.<row>[.<party size>]`) kept up to date from [RaidEvent]s; the Global
 * column is this world's counters (OPEN-LR11: no global service exists).
 */
public class TobStatsPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }
        // Scoreboard in the lobby's eastern hall (LR3), strategy table (participants) and war table (spectators) in the vault (LR22).
        onOpLoc(Locs.TOB_SCOREBOARD, OP1) { TobScoreboard.open(player) }
        onOpLoc(Locs.TOB_DUNGEON_TREASURE_ROOM_LIBRARY_TABLE_A, OP1) { TobPanel.open(player) }
        onOpLoc(Locs.TOB_DUNGEON_TREASURE_ROOM_WAR_TABLE, OP1) { TobPanel.open(player) }
        // The client switches the tab varbit itself and then the click reaches the server (client-contract 2.6).
        onButton(Components.Tob_Scoreboard.STORY_TAB) { TobScoreboard.tab(this, TobScoreboard.TAB_ENTRY) }
        onButton(Components.Tob_Scoreboard.NORMAL_TAB) { TobScoreboard.tab(this, TobScoreboard.TAB_NORMAL) }
        onButton(Components.Tob_Scoreboard.HARD_TAB) { TobScoreboard.tab(this, TobScoreboard.TAB_HARD) }

        events.on<RaidEvent.Entered> { e -> withWorld(world, e.run) { TobStatsLedger.entered(it, e.run, e.member) } }
        events.on<RaidEvent.Died> { e -> withWorld(world, e.run) { TobStatsLedger.died(it, e.run, e.member) } }
        events.on<RaidEvent.Completed> { e -> withWorld(world, e.run) { TobStatsLedger.completed(it, e.run) } }
    }

    private fun withWorld(world: WorldRef?, run: RaidRun, action: (WorldRef) -> Unit) {
        if (world != null && run.raid.id == TobRaid.ID) action(world)
    }

    private companion object {
        const val OP1 = 1
    }
}

/**
 * This world's counters (OPEN-LR11): counts are sums, times are the best (lowest) ticks. Keys are the personal save
 * keys. Not persisted.
 */
internal class TobWorldStats {
    val total = HashMap<String, Long>()

    fun add(key: String) {
        total.merge(key, 1L, Long::plus)
    }

    fun best(key: String, ticks: Long) {
        total.merge(key, ticks) { old, new -> minOf(old, new) }
    }

    companion object {
        private val KEY = AttrKey<TobWorldStats>("tob_world_stats")

        fun of(world: WorldRef): TobWorldStats = world.worldAttr(KEY) ?: TobWorldStats().also { world.setWorldAttr(KEY, it) }

        fun of(player: PlayerRef): TobWorldStats = player.worldAttr(KEY) ?: TobWorldStats().also { player.setWorldAttr(KEY, it) }
    }
}

/** Keeps the personal and world counters up to date from the raid's events. */
internal object TobStatsLedger {
    const val ATTEMPTS = "attempts"
    const val COMPLETIONS = "completions"
    const val DEATHS = "deaths"

    /** Best challenge time (boss fights only) and best overall time per party size (TB34). */
    const val CHALLENGE = "challenge"
    const val OVERALL = "overall"

    private val ATTEMPTED = AttrKey<Long>("tob_stats_attempted_run")

    /** The save key of a counter of [mode]; [size] > 0 only for the per-party-size time rows. */
    fun key(mode: String, row: String, size: Int = 0): String = if (size > 0) "tob.stats.$mode.$row.$size" else "tob.stats.$mode.$row"

    /** A member's first entry into a run is one attempt. */
    fun entered(world: WorldRef, run: RaidRun, member: String) {
        val player = world.onlinePlayer(member) ?: return
        if (player[ATTEMPTED] == run.id.value) return
        player[ATTEMPTED] = run.id.value
        count(world, player, run.scaling.mode, ATTEMPTS)
    }

    fun died(world: WorldRef, run: RaidRun, member: String) {
        world.onlinePlayer(member)?.let { count(world, it, run.scaling.mode, DEATHS) }
    }

    /** The raid is done: a completion and the challenge and overall times for the party size, for every member who finished. */
    fun completed(world: WorldRef, run: RaidRun) {
        val mode = run.scaling.mode
        val size = run.scaling.partySize.coerceIn(1, TobRaid.MAX_PARTY)
        val challenge = TobFlow.state(run).challengeTicks
        val overall = run.endedTick - run.startedTick
        for (m in run.members.values) {
            if (!m.inside) continue
            val player = world.onlinePlayer(m.name) ?: continue
            count(world, player, mode, COMPLETIONS)
            best(world, player, key(mode, CHALLENGE, size), challenge)
            best(world, player, key(mode, OVERALL, size), overall)
        }
    }

    private fun count(world: WorldRef, p: PlayerRef, mode: String, row: String) {
        val key = key(mode, row)
        p.setSaved(key, (p.saved(key) ?: 0L) + 1)
        TobWorldStats.of(world).add(key)
    }

    private fun best(world: WorldRef, p: PlayerRef, key: String, ticks: Long) {
        if (ticks <= 0) return
        val old = p.saved(key) ?: 0L
        if (old <= 0L || ticks < old) p.setSaved(key, ticks)
        TobWorldStats.of(world).best(key, ticks)
    }
}

/** The scoreboard: the mode tab is varbit 12270 and the cells are text (OPEN-LR11: formats are not in the cache). */
internal object TobScoreboard {
    /** Client-contract 2.6: varbit 12270 `TOB_SCOREBOARD_TAB` is 1 on the Entry (story) tab, 0 Normal, 2 Hard. */
    const val TAB_NORMAL = 0
    const val TAB_ENTRY = 1
    const val TAB_HARD = 2

    /** A cell without a value. */
    private const val NO_VALUE = "-"

    /** A component with no child slots is addressed as -1..-1. */
    private const val NO_SLOT = -1

    /** Per party size 1..5: personal overall, personal challenge, global overall, global challenge cells (client-contract 1.5). */
    private val TIME_CELLS: List<IntArray> =
        listOf(
            intArrayOf(
                Components.Tob_Scoreboard.DATA_SOLO_P_O,
                Components.Tob_Scoreboard.DATA_SOLO_P_R,
                Components.Tob_Scoreboard.DATA_SOLO_G_O,
                Components.Tob_Scoreboard.DATA_SOLO_G_R,
            ),
            intArrayOf(
                Components.Tob_Scoreboard.DATA_2MAN_P_O,
                Components.Tob_Scoreboard.DATA_2MAN_P_R,
                Components.Tob_Scoreboard.DATA_2MAN_G_O,
                Components.Tob_Scoreboard.DATA_2MAN_G_R,
            ),
            intArrayOf(
                Components.Tob_Scoreboard.DATA_3MAN_P_O,
                Components.Tob_Scoreboard.DATA_3MAN_P_R,
                Components.Tob_Scoreboard.DATA_3MAN_G_O,
                Components.Tob_Scoreboard.DATA_3MAN_G_R,
            ),
            intArrayOf(
                Components.Tob_Scoreboard.DATA_4MAN_P_O,
                Components.Tob_Scoreboard.DATA_4MAN_P_R,
                Components.Tob_Scoreboard.DATA_4MAN_G_O,
                Components.Tob_Scoreboard.DATA_4MAN_G_R,
            ),
            intArrayOf(
                Components.Tob_Scoreboard.DATA_5MAN_P_O,
                Components.Tob_Scoreboard.DATA_5MAN_P_R,
                Components.Tob_Scoreboard.DATA_5MAN_G_O,
                Components.Tob_Scoreboard.DATA_5MAN_G_R,
            ),
        )

    fun open(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOB_SCOREBOARD)
        for (tab in intArrayOf(
            Components.Tob_Scoreboard.STORY_TAB,
            Components.Tob_Scoreboard.NORMAL_TAB,
            Components.Tob_Scoreboard.HARD_TAB,
        )) {
            p.ifEvents(tab, NO_SLOT..NO_SLOT, IfEvents.op(1))
        }
        send(p)
    }

    /** A tab click: the server mirrors the client's local varbit change and re-sends the cells for that mode. */
    fun tab(p: PlayerRef, value: Int) {
        if (!p.isOpen(Interfaces.TOB_SCOREBOARD)) return
        p.setVarbit(Varbits.TOB_SCOREBOARD_TAB, value)
        send(p)
    }

    private fun modeOf(p: PlayerRef): String =
        when (p.varbit(Varbits.TOB_SCOREBOARD_TAB)) {
            TAB_ENTRY -> TobRaid.ENTRY
            TAB_HARD -> TobRaid.HARD
            else -> TobRaid.NORMAL
        }

    private fun send(p: PlayerRef) {
        val mode = modeOf(p)
        val world = TobWorldStats.of(p)

        fun count(row: String) = TobStatsLedger.key(mode, row)
        p.ifText(Components.Tob_Scoreboard.DATA_A_P, number(p.saved(count(TobStatsLedger.ATTEMPTS))))
        p.ifText(Components.Tob_Scoreboard.DATA_A_G, number(world.total[count(TobStatsLedger.ATTEMPTS)]))
        p.ifText(Components.Tob_Scoreboard.DATA_C_P, number(p.saved(count(TobStatsLedger.COMPLETIONS))))
        p.ifText(Components.Tob_Scoreboard.DATA_C_G, number(world.total[count(TobStatsLedger.COMPLETIONS)]))
        p.ifText(Components.Tob_Scoreboard.DATA_D_P, number(p.saved(count(TobStatsLedger.DEATHS))))
        p.ifText(Components.Tob_Scoreboard.DATA_D_G, number(world.total[count(TobStatsLedger.DEATHS)]))
        for ((i, cells) in TIME_CELLS.withIndex()) {
            val size = i + 1
            val overall = TobStatsLedger.key(mode, TobStatsLedger.OVERALL, size)
            val challenge = TobStatsLedger.key(mode, TobStatsLedger.CHALLENGE, size)
            p.ifText(cells[CELL_P_OVERALL], time(p.saved(overall)))
            p.ifText(cells[CELL_P_CHALLENGE], time(p.saved(challenge)))
            p.ifText(cells[CELL_G_OVERALL], time(world.total[overall]))
            p.ifText(cells[CELL_G_CHALLENGE], time(world.total[challenge]))
        }
    }

    private fun number(value: Long?): String = (value ?: 0L).toString()

    private fun time(ticks: Long?): String = if (ticks == null || ticks <= 0) NO_VALUE else Raids.formatTicks(ticks)

    private const val CELL_P_OVERALL = 0
    private const val CELL_P_CHALLENGE = 1
    private const val CELL_G_OVERALL = 2
    private const val CELL_G_CHALLENGE = 3
}

/** The strategy table / war table board 459 (LR22): deaths per player, the MVP, the room times, challenge and overall time. */
internal object TobPanel {
    private val NAMES =
        intArrayOf(
            Components.Tob_Infoboard.PLAYERNAME0,
            Components.Tob_Infoboard.PLAYERNAME1,
            Components.Tob_Infoboard.PLAYERNAME2,
            Components.Tob_Infoboard.PLAYERNAME3,
            Components.Tob_Infoboard.PLAYERNAME4,
        )
    private val DEATHS =
        intArrayOf(
            Components.Tob_Infoboard.DEATHS0,
            Components.Tob_Infoboard.DEATHS1,
            Components.Tob_Infoboard.DEATHS2,
            Components.Tob_Infoboard.DEATHS3,
            Components.Tob_Infoboard.DEATHS4,
        )

    /** The room times in [TobRaid.ORDER] order: Maiden, Bloat, Nylocas, Sotetseg, Xarpus, Verzik. */
    private val ROOM_TIMES =
        intArrayOf(
            Components.Tob_Infoboard.CH1_TIME,
            Components.Tob_Infoboard.CH2_TIME,
            Components.Tob_Infoboard.CH3_TIME,
            Components.Tob_Infoboard.CH4_TIME,
            Components.Tob_Infoboard.CH5_TIME,
            Components.Tob_Infoboard.CH6_TIME,
        )

    /** A time or name that is not known (yet). */
    private const val NO_VALUE = "-"

    fun open(p: PlayerRef) {
        val run = runFor(p) ?: return
        val state = TobFlow.state(run)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOB_INFOBOARD)
        for (i in NAMES.indices) {
            val name = run.party.members.getOrNull(i)
            p.ifText(NAMES[i], name ?: "")
            p.ifText(DEATHS[i], if (name == null) "" else (run.member(name)?.deaths ?: 0).toString())
        }
        p.ifText(Components.Tob_Infoboard.TOTALDEATHS, run.teamDeaths.toString())
        // OPEN-LR23: the MVP line's format is not captured; the name replaces the "Player -" placeholder.
        p.ifText(Components.Tob_Infoboard.MVP_PLAYER, TobRewards.mvp(state.mvp) ?: NO_VALUE)
        for ((i, room) in TobRaid.ORDER.withIndex()) p.ifText(ROOM_TIMES[i], time(state.roomTicks[room]))
        p.ifText(Components.Tob_Infoboard.CHALLENGE_TIME_TIME, time(state.challengeTicks))
        p.ifText(Components.Tob_Infoboard.OVERALL_TIME_TIME, time(if (run.endedTick >= 0) run.endedTick - run.startedTick else null))
    }

    /** The run [p] belongs to (strategy table) or watches (war table). */
    private fun runFor(p: PlayerRef): RaidRun? =
        Raids.of(p).let { raids ->
            raids.runOf(p.username)?.takeIf { it.raid.id == TobRaid.ID }
                ?: raids.activeRuns.firstOrNull {
                    it.raid.id == TobRaid.ID &&
                        it.spectators.any { s -> s.equals(p.username, ignoreCase = true) }
                }
        }

    private fun time(ticks: Long?): String = if (ticks == null || ticks <= 0) NO_VALUE else Raids.formatTicks(ticks)
}
