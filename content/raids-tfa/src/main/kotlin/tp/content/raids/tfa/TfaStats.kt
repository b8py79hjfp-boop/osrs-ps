package tp.content.raids.tfa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RoomDef
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DbRowRef
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.DbRows
import tp.symbols.DbTables
import tp.symbols.Interfaces
import tp.symbols.Locs

/**
 * The Alliance records (stats interface 979 `TFA_STATS`; docs/mechanics/raids/tfa.md TFA27, tfa/client-contract.md 2.5).
 * The out-of-raid scoreboard opens it; the client then asks for a category (IF_SCRIPTTRIGGER on 979:34) and the server
 * answers with one client script 10125 per stat row of the category's cache rows (tables 264 / 265). Personal values are
 * player saves (`tfa.stats.<category>.<row>[.<party size>]`) kept up to date from [RaidEvent]s; the other columns come
 * from [TfaWorldStats] (OPEN-TFA14: no global service exists, so Global is this world's all-time value).
 */
public class TfaStats : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        var layout: StatsLayout? = null
        onWorldStart {
            world = this
            layout = StatsLayout.read(this)
        }
        // The Record Keeper (NPC 16692) only has "Talk-to"; the scoreboard's "Read" is the one entry point we know.
        onOpLoc(Locs.TFA_OUT_OF_RAID_SCOREBOARD, OP1) { TfaStatsUi.open(player) }
        onButton(Components.Tfa_Stats.CLOSE_BUTTON) { closeModal() }
        // Client script 10123 (hostile input): the category must be one of table 264's rows and the interface open.
        onScriptTrigger(Components.Tfa_Stats.ALL_STATS_TABLES, "i") { t ->
            val l = layout
            if (l != null) TfaStatsUi.send(this, l, t.args[0] as Int)
        }

        events.on<RaidEvent.Entered> { e -> withWorld(world, e.run) { TfaStatsLedger.entered(it, e.run, e.member) } }
        events.on<RaidEvent.RoomStarted> { e -> withWorld(world, e.run) { TfaStatsLedger.roomStarted(it, e.run, e.room) } }
        events.on<RaidEvent.RoomCleared> { e -> withWorld(world, e.run) { TfaStatsLedger.roomCleared(it, e.run, e.room, e.ticks) } }
        events.on<RaidEvent.Died> { e -> withWorld(world, e.run) { TfaStatsLedger.died(it, e.run, e.member) } }
        events.on<RaidEvent.RoomFailed> { e -> withWorld(world, e.run) { TfaStatsLedger.wiped(it, e.run) } }
        events.on<RaidEvent.Left> { e -> withWorld(world, e.run) { TfaStatsLedger.left(it, e.run, e.member) } }
        events.on<RaidEvent.Completed> { e -> withWorld(world, e.run) { TfaStatsLedger.completed(it, e.run) } }
    }

    private fun withWorld(world: WorldRef?, run: RaidRun, action: (WorldRef) -> Unit) {
        if (world != null && run.raid.id == TfaRaid.ID) action(world)
    }
}

private const val OP1 = 1

/** One stat row of a category (table 265): its dbrow, the name used in save keys, and whether it is a time. */
internal class StatRow(val row: Int, val name: String, val time: Boolean, val perPartySize: Boolean)

/** One category (table 264): its dbrow, the save-key name and its rows in display order. */
internal class StatCategory(val row: Int, val key: String, val rows: List<StatRow>)

/** The categories and rows of the stats interface, read once per world from tables 264 and 265. */
internal class StatsLayout(val categories: Map<Int, StatCategory>) {
    companion object {
        fun read(world: WorldRef): StatsLayout {
            val rows = world.dbRows(DbTables.Tfa_Stats_Row.ID).associateBy { it.id }
            val out = HashMap<Int, StatCategory>()
            for (c in world.dbRows(DbTables.Tfa_Stats_Category.ID)) {
                val room = c.int(DbTables.Tfa_Stats_Category.COL_ASSOCIATED_ROOM)
                val key = if (room == null) OVERALL else ROOM_IDS.getOrNull(room - 1) ?: continue
                val ids = ArrayList<Int>()
                while (true) ids += c.int(DbTables.Tfa_Stats_Category.COL_ROWS, ids.size) ?: break
                out[c.id] = StatCategory(c.id, key, ids.mapNotNull { id -> rows[id]?.let { row(id, it) } })
            }
            return StatsLayout(out)
        }

        private fun row(id: Int, r: DbRowRef): StatRow? {
            val name = ROW_NAMES[id] ?: return null
            return StatRow(
                id,
                name,
                time = r.int(DbTables.Tfa_Stats_Row.COL_IS_TIME) == 1,
                perPartySize = r.int(DbTables.Tfa_Stats_Row.COL_ROW_PER_PARTY_SIZE) == 1,
            )
        }

        const val OVERALL = "overall"

        /** Table 264 `associated_room` 1..8 as the raid's room ids (client-contract 7: Guardian, Ezekiel, Xarth, Dagon'hai, ...). */
        val ROOM_IDS = listOf("guardian", "ezekiel", "xarth", "dagonhai", "bork", "moia", "dreadnoth", "ocellus")

        /** Table 265 rows (gamevals `TFA_STATS_ROW_*`) and the save-key segment of each. */
        val ROW_NAMES =
            mapOf(
                DbRows.TFA_STATS_ROW_ATTEMPTS to TfaStatsLedger.ATTEMPTS,
                DbRows.TFA_STATS_ROW_COMPLETIONS to TfaStatsLedger.COMPLETIONS,
                DbRows.TFA_STATS_ROW_COMPLETIONS_PRACTICE to TfaStatsLedger.PRACTICE,
                DbRows.TFA_STATS_ROW_DEATHS to TfaStatsLedger.DEATHS,
                DbRows.TFA_STATS_ROW_WIPES to TfaStatsLedger.WIPES,
                DbRows.TFA_STATS_ROW_FAILURES to TfaStatsLedger.FAILURES,
                DbRows.TFA_STATS_ROW_TIME to TfaStatsLedger.TIME,
            )
    }
}

/**
 * This world's counters (OPEN-TFA14): [total] since the world started, [today] since the UTC day [day] began. Keys are
 * the personal save keys. Times are the best (lowest) ticks; counts are sums. Not persisted.
 */
internal class TfaWorldStats {
    val total = HashMap<String, Long>()
    val today = HashMap<String, Long>()
    private var day = Long.MIN_VALUE

    /** Starts a new "today" when [now] (a UTC epoch day) is not the day the counters belong to. */
    fun roll(now: Long) {
        if (now == day) return
        day = now
        today.clear()
    }

    fun add(now: Long, key: String) {
        roll(now)
        total.merge(key, 1L, Long::plus)
        today.merge(key, 1L, Long::plus)
    }

    fun best(now: Long, key: String, ticks: Long) {
        roll(now)
        total.merge(key, ticks) { old, new -> minOf(old, new) }
        today.merge(key, ticks) { old, new -> minOf(old, new) }
    }

    companion object {
        private val KEY = AttrKey<TfaWorldStats>("tfa_world_stats")

        fun of(world: WorldRef): TfaWorldStats = world.worldAttr(KEY) ?: TfaWorldStats().also { world.setWorldAttr(KEY, it) }

        fun of(player: PlayerRef): TfaWorldStats = player.worldAttr(KEY) ?: TfaWorldStats().also { player.setWorldAttr(KEY, it) }
    }
}

/** Keeps the personal and world counters up to date from the raid's events. */
internal object TfaStatsLedger {
    const val ATTEMPTS = "attempts"
    const val COMPLETIONS = "completions"
    const val PRACTICE = "practice"
    const val DEATHS = "deaths"
    const val WIPES = "wipes"
    const val FAILURES = "failures"
    const val TIME = "time"

    /** The save key of a counter; [size] > 0 only for the per-party-size time rows. */
    fun key(category: String, row: String, size: Int = 0): String =
        if (size > 0) "tfa.stats.$category.$row.$size" else "tfa.stats.$category.$row"

    /** A member's first entry into a run is one attempt (practice runs count too: OPEN-TFA14). */
    fun entered(world: WorldRef, run: RaidRun, member: String) {
        if (!TfaFlow.state(run).statsAttempted.add(member.lowercase())) return
        world.onlinePlayer(member)?.let { count(world, it, StatsLayout.OVERALL, ATTEMPTS) }
    }

    fun roomStarted(world: WorldRef, run: RaidRun, room: RoomDef) {
        if (room.id !in StatsLayout.ROOM_IDS) return
        each(world, run) { count(world, it, room.id, ATTEMPTS) }
    }

    /** A boss room cleared: a completion (practice mode has its own row) and the room time for the party size. */
    fun roomCleared(world: WorldRef, run: RaidRun, room: RoomDef, ticks: Long) {
        if (room.id !in StatsLayout.ROOM_IDS) return
        val row = if (run.scaling.mode == PRACTICE_MODE) PRACTICE else COMPLETIONS
        each(world, run) {
            count(world, it, room.id, row)
            best(world, it, room.id, partySize(run), ticks)
        }
    }

    fun died(world: WorldRef, run: RaidRun, member: String) {
        val player = world.onlinePlayer(member) ?: return
        count(world, player, StatsLayout.OVERALL, DEATHS)
        val room = (run.state as? RunState.InRoom)?.room?.id
        if (room != null && room in StatsLayout.ROOM_IDS) count(world, player, room, DEATHS)
    }

    /** A team wipe: one wipe for everyone still inside (the dying player's `inside` is cleared after this event). */
    fun wiped(world: WorldRef, run: RaidRun) {
        each(world, run) { count(world, it, StatsLayout.OVERALL, WIPES) }
    }

    /**
     * OPEN-TFA14: a failure is a member leaving a run that is still going (an escape); the wipe ends the run before the
     * others are ejected, so those are wipes only.
     */
    fun left(world: WorldRef, run: RaidRun, member: String) {
        if (run.state !is RunState.InRoom && run.state !is RunState.BetweenRooms) return
        world.onlinePlayer(member)?.let { count(world, it, StatsLayout.OVERALL, FAILURES) }
    }

    /** The raid is done: a completion and the total time for the party size. Practice raids have no Overall row. */
    fun completed(world: WorldRef, run: RaidRun) {
        if (run.scaling.mode == PRACTICE_MODE) return
        val ticks = run.endedTick - run.startedTick
        each(world, run) {
            count(world, it, StatsLayout.OVERALL, COMPLETIONS)
            best(world, it, StatsLayout.OVERALL, partySize(run), ticks)
        }
    }

    private fun each(world: WorldRef, run: RaidRun, action: (PlayerRef) -> Unit) {
        for (m in run.members.values) {
            if (m.inside) world.onlinePlayer(m.name)?.let(action)
        }
    }

    private fun count(world: WorldRef, p: PlayerRef, category: String, row: String) {
        val key = key(category, row)
        p.setSaved(key, (p.saved(key) ?: 0L) + 1)
        TfaWorldStats.of(world).add(day(p), key)
    }

    private fun best(world: WorldRef, p: PlayerRef, category: String, size: Int, ticks: Long) {
        if (ticks <= 0) return
        val key = key(category, TIME, size)
        val old = p.saved(key) ?: 0L
        if (old <= 0L || ticks < old) p.setSaved(key, ticks)
        TfaWorldStats.of(world).best(day(p), key, ticks)
    }

    private fun partySize(run: RaidRun): Int = run.scaling.partySize.coerceIn(1, TfaRaid.MAX_PARTY)

    fun day(p: PlayerRef): Long = p.epochMinutes / MINUTES_PER_DAY

    /** The mode id of a practice run (TfaRaid.DEFINITION modes). */
    private const val PRACTICE_MODE = "practice"
    private const val MINUTES_PER_DAY = 24L * 60L
}

/** The stats interface: opening it and answering the client's category requests. */
internal object TfaStatsUi {
    fun open(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TFA_STATS)
        p.ifEvents(Components.Tfa_Stats.ALL_STATS_TABLES, -1..-1, IfEvents.SCRIPT_TRIGGER)
        p.clientScript(CS_START, if (activeRun(p) != null) 1 else 0)
    }

    /** One 10125 per row (per party size for the time row), then 10124 hides the loading spinner. */
    fun send(p: PlayerRef, layout: StatsLayout, categoryRow: Int) {
        if (!p.isOpen(Interfaces.TFA_STATS)) return
        val category = layout.categories[categoryRow] ?: return
        val run = activeRun(p)
        val world = TfaWorldStats.of(p)
        world.roll(TfaStatsLedger.day(p))
        for (row in category.rows) {
            if (row.perPartySize) {
                for (size in 1..TfaRaid.MAX_PARTY) send(p, category, row, size, run, world)
            } else {
                send(p, category, row, 0, run, world)
            }
        }
        p.clientScript(CS_DONE)
    }

    private fun send(p: PlayerRef, category: StatCategory, row: StatRow, size: Int, run: RaidRun?, world: TfaWorldStats) {
        val key = TfaStatsLedger.key(category.key, row.name, size)
        val today = clamp(world.today[key])
        p.clientScript(
            CS_ROW,
            category.row,
            row.row,
            size,
            current(p, category, row, size, run),
            0,
            clamp(p.saved(key)),
            clamp(world.total[key]),
            today,
            today,
        )
    }

    /**
     * The "Current" column (shown only inside a raid): this run's deaths, and the live timer of the run (or the current
     * room) in the row of its party size; -1 elsewhere. The flag argument is 0, the value for which the client runs a
     * time cell as a live timer (client-contract 2.5; the argument roles are partly inferred).
     */
    private fun current(p: PlayerRef, category: StatCategory, row: StatRow, size: Int, run: RaidRun?): Int {
        run ?: return NONE
        if (row.name == TfaStatsLedger.DEATHS && category.key == StatsLayout.OVERALL) return run.member(p.username)?.deaths ?: NONE
        if (!row.time || size != run.scaling.partySize.coerceIn(1, TfaRaid.MAX_PARTY)) return NONE
        val since =
            if (category.key == StatsLayout.OVERALL) {
                run.startedTick
            } else {
                (run.state as? RunState.InRoom)?.takeIf { it.room.id == category.key }?.startedTick ?: return NONE
            }
        return clamp(p.tick - since)
    }

    private fun activeRun(p: PlayerRef): RaidRun? {
        val run = Raids.of(p).runOf(p.username) ?: return null
        return run.takeIf { it.raid.id == TfaRaid.ID && it.member(p.username)?.inside == true }
    }

    private fun clamp(value: Long?): Int = (value ?: 0L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /** The Current column's "no value" (client-contract 2.5: current > -1 means a value). */
    private const val NONE = -1

    /** Server-run client scripts of interface 979 (client-contract 2.5): 10102(inActiveRaid), 10125(9 ints), 10124(). */
    private const val CS_START = 10102
    private const val CS_ROW = 10125
    private const val CS_DONE = 10124
}
