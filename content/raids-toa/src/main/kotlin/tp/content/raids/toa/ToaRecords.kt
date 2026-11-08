package tp.content.raids.toa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Varbits

/**
 * Kill counts, personal bests and chat lines (toa.md TA60-TA64, TA12). Kill counts and the `total completion time` line
 * are raids-core's (see [ToaRaid.DEFINITION]); this adds the Wardens block with the challenge time, the target time
 * line, the per-mode attempts / completions / deaths and best times for the scoreboards, and the time-limit penalty.
 */
internal object ToaRecords {
    /** TA12: missing the limit lowers the raid level by twice the invocation's own value (20 / 30 / 40 / 50). */
    const val TIME_PENALTY_FACTOR = 2

    /** The boss rooms whose durations make the challenge time (TA63: "time spent actually fighting bosses"). */
    private val BOSS_ROOMS = ToaPath.entries.map { it.boss } + ToaRaid.WARDENS

    private const val SECONDS_PER_MINUTE = 60L
    private const val TICKS_PER_SECOND_NUMERATOR = 3
    private const val TICKS_PER_SECOND_DENOMINATOR = 5

    // ---- save keys (shared with the lobby scoreboard)

    const val ATTEMPTS = "attempts"
    const val COMPLETIONS = "completions"
    const val DEATHS = "deaths"

    /** Best challenge time and best overall time per party size (TA63). */
    const val CHALLENGE = "challenge"
    const val OVERALL = "overall"

    /** The save key of a counter of [mode]; [size] > 0 only for the time rows. */
    fun key(mode: String, row: String, size: Int = 0): String = if (size > 0) "toa.stats.$mode.$row.$size" else "toa.stats.$mode.$row"

    // ---- time

    /** TA62: `m:ss`, with two decimals when the precise-timing option is on (the scoreboard always shows them). */
    fun formatTime(p: PlayerRef, ticks: Long): String =
        tp.content.raids.Raids
            .formatTime(p, ticks)

    /** TA12: the raid level after the time limit: lowered by twice the invocation's value when the timer ran out. */
    fun finalLevel(st: ToaState, any: PlayerRef?): Int {
        // OPEN-TA6 (`toa.timeLimitPenaltyAtEndOnly=false`): the penalty already applied when the limit passed.
        if (st.timePenalised) return st.raidLevel
        val start = st.raidLevel
        if (any == null || st.timerStart < 0 || st.endTick < 0) return start
        val limit = ToaInvocations.active(any, st.words).firstOrNull { it.category == ToaInvocations.CAT_TIME_LIMIT } ?: return start
        return if (st.endTick - st.timerStart > limit.value) maxOf(0, start - TIME_PENALTY_FACTOR * limit.level) else start
    }

    /** TA64: with a time-limit invocation the raid also says `Overall time to beat: mm:ss` (RL reads the minutes). */
    fun targetTimeMessage(st: ToaState, any: PlayerRef): String? {
        val limit = ToaInvocations.active(any, st.words).firstOrNull { it.category == ToaInvocations.CAT_TIME_LIMIT } ?: return null
        val seconds = limit.value * TICKS_PER_SECOND_NUMERATOR / TICKS_PER_SECOND_DENOMINATOR
        return "Overall time to beat: %02d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
    }

    /** TA63: the sum of the boss rooms' durations. */
    fun challengeTicks(l: ToaLedger): Long = BOSS_ROOMS.sumOf { l.roomTicks[it] ?: 0L }

    private fun modeName(mode: String): String =
        when (mode) {
            ToaRaid.EXPERT -> ": Expert Mode"
            ToaRaid.ENTRY -> ": Entry Mode"
            else -> ""
        }

    /**
     * TA62: the Wardens' completion block (RL matches `Challenge complete: The Wardens` and the challenge time), then the
     * personal best. Sent once to each member in the tomb before raids-core adds the total time and the kill count, so the
     * order the plugins expect holds (PB, then KC).
     */
    fun wardensBlock(run: RaidRun, any: EntityRef) {
        val l = ToaPoints.ledger(run) ?: return
        val mode = run.scaling.mode
        val size = run.scaling.partySize.coerceIn(1, ToaRaid.MAX_PARTY)
        val challenge = challengeTicks(l)
        val duration = l.roomTicks[ToaRaid.WARDENS] ?: 0L
        for (p in run.presentPlayers(any)) {
            val key = key(mode, CHALLENGE, size)
            val best = p.saved(key)
            val newBest = best == null || challenge < best
            if (newBest) p.setSaved(key, challenge)
            val head = "Challenge complete: The Wardens. Duration: <col=ef1020>${formatTime(p, duration)}</col><br>"
            val line = "Tombs of Amascut${modeName(mode)} challenge completion time: <col=ef1020>${formatTime(p, challenge)}</col>"
            p.message(head + line + if (newBest || best == null) " (new personal best)" else ". Personal best: ${formatTime(p, best)}")
            ToaWorldStats.of(p).best(key, challenge)
        }
    }
}

/** This world's counters (OPEN-TA31: no global service exists yet): counts are sums, times the best (lowest) ticks. Not persisted. */
internal class ToaWorldStats {
    val total = HashMap<String, Long>()

    fun add(key: String) {
        total.merge(key, 1L, Long::plus)
    }

    fun best(key: String, ticks: Long) {
        if (ticks > 0) total.merge(key, ticks) { old, new -> minOf(old, new) }
    }

    companion object {
        private val KEY = AttrKey<ToaWorldStats>("toa_world_stats")

        fun of(world: WorldRef): ToaWorldStats = world.worldAttr(KEY) ?: ToaWorldStats().also { world.setWorldAttr(KEY, it) }

        fun of(player: PlayerRef): ToaWorldStats = player.worldAttr(KEY) ?: ToaWorldStats().also { player.setWorldAttr(KEY, it) }
    }
}

/** Keeps the personal and world counters of the lobby scoreboard (775) up to date from the raid's events (TA66). */
public class ToaRecordsPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }
        events.on<RaidEvent.Entered> { e -> world?.takeIf { e.run.raid.id == ToaRaid.ID }?.let { entered(it, e.run, e.member) } }
        events.on<RaidEvent.Died> { e ->
            world?.takeIf { e.run.raid.id == ToaRaid.ID }?.let { w ->
                w.onlinePlayer(e.member)?.let { count(it, e.run.scaling.mode, ToaRecords.DEATHS) }
            }
        }
        events.on<RaidEvent.Completed> { e -> world?.takeIf { e.run.raid.id == ToaRaid.ID }?.let { completed(it, e.run) } }
    }

    /** A member's first entry into a run is one attempt. */
    private fun entered(world: WorldRef, run: RaidRun, member: String) {
        val p = world.onlinePlayer(member) ?: return
        if (p[ATTEMPTED] == run.id.value) return
        p[ATTEMPTED] = run.id.value
        count(p, run.scaling.mode, ToaRecords.ATTEMPTS)
    }

    /** One completion and the overall time (TA63: entering the raid to the final blow) for every member who finished. */
    private fun completed(world: WorldRef, run: RaidRun) {
        val mode = run.scaling.mode
        val size = run.scaling.partySize.coerceIn(1, ToaRaid.MAX_PARTY)
        val overall = run.endedTick - run.startedTick
        for (m in run.members.values) {
            if (!m.inside) continue
            val p = world.onlinePlayer(m.name) ?: continue
            count(p, mode, ToaRecords.COMPLETIONS)
            val key = ToaRecords.key(mode, ToaRecords.OVERALL, size)
            val old = p.saved(key)
            if (old == null || overall < old) p.setSaved(key, overall)
            ToaWorldStats.of(p).best(key, overall)
        }
        ToaPoints.ledger(run)?.completed = true
    }

    private fun count(p: PlayerRef, mode: String, row: String) {
        val key = ToaRecords.key(mode, row)
        p.setSaved(key, (p.saved(key) ?: 0L) + 1)
        ToaWorldStats.of(p).add(key)
    }

    private companion object {
        val ATTEMPTED = AttrKey<Long>("toa_stats_attempted_run")
    }
}
