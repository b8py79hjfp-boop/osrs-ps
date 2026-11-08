package tp.content.raids.toa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidMember
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RoomKind
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.HitEvent
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef

/** Per-player figures other rooms report for the titles (toa.md TA67); the content that sees the event calls [ToaPoints.stat]. */
internal enum class ToaStat {
    /** Food eaten (The Glutton, The Picky Eater). */
    FOOD_EATEN,

    /** Special attack energy used, in percent points (The Specialist). */
    SPECIAL_ENERGY,

    /** Scarabs killed in the Path of Scabaras and against Kephri (The Entomophobe). */
    SCARABS_KILLED,

    /** Ticks spent in Zebak's pool (The Swimmer needs 30 seconds = 50 ticks). */
    POOL_TICKS,

    /** Orbs dropped in the Akkha fight (The Baller needs more than 20). */
    ORBS_DROPPED,

    /** Deaths to a light beam in the Path of Het (The Moth). */
    LIGHT_BEAM_DEATHS,

    /** 1 for the player who dealt the Wardens' final blow while alone (The Clutch). */
    WARDENS_FINISHED,

    /** The Grand Exchange value of the loadout at the end (The Peasant, The Showoff); set by [ToaSummary]. */
    LOADOUT_VALUE,
}

/** One member's counters in one run. Plain fields: the hit handler must not allocate. */
internal class ToaTally {
    /** TA35: this room's points so far (capped per room). */
    var room = 0

    /** Damage dealt to the run's NPCs (summary 482, varbit 14325) and its split by style (titles). */
    var dealt = 0L
    var melee = 0L
    var ranged = 0L
    var magic = 0L

    /** Damage taken from NPCs (summary 482, varbit 14323). */
    var taken = 0L
    val stats = IntArray(ToaStat.entries.size)
}

/** The run's points ledger (toa.md TA35-TA41), kept in [ToaState.ledger]. */
internal class ToaLedger {
    /** The 5,000 start has been credited. */
    var started = false

    /** Downs of the phase-2 Wardens since the last wipe (TA36). */
    var p2Downs = 0

    /** Set once the raid completed in the tomb: the chests, summary and records are final. */
    var completed = false

    private val tallies = HashMap<String, ToaTally>()

    /** Cleared rooms: room id -> ticks the room took (the challenge time is the sum over the boss rooms). */
    val roomTicks = LinkedHashMap<String, Long>()

    /** Cleared rooms: room id -> each member's room points (the Leech counts the lowest). */
    val roomPoints = LinkedHashMap<String, Map<String, Int>>()

    /** Cleared rooms: room id -> the MVP's name (TA38). */
    val mvp = LinkedHashMap<String, String>()

    /** Puzzle rooms whose content reported its own points: no flat estimate is added. */
    val detailedPuzzles = HashSet<String>()

    /** The final titles by member name (title id), set when the summary is built. */
    val titles = HashMap<String, Int>()

    fun tally(name: String): ToaTally = tallies.getOrPut(name) { ToaTally() }

    fun tallyOrNull(name: String): ToaTally? = tallies[name]
}

/**
 * The points rules of the raid (toa.md TA35-TA41). raids-core's ledger is switched off for ToA (multiplier 0: see
 * [ToaRaid]); `RaidMember.points` is the player's **Total** (5,000 start, room points joined at room completion, MVP
 * bonuses, death penalties, the 64,000 cap). The room's points wait in [ToaTally.room] until [roomCleared].
 */
internal object ToaPoints {
    /** TA35: the Total every player starts with. */
    const val START_POINTS = 5_000

    /** TA35: room points cap per player per room. */
    const val ROOM_CAP = 20_000

    /** TA35: Total cap per player. */
    const val TOTAL_CAP = 64_000

    /** TA38: the MVP's bonus is this times the team size. */
    private const val MVP_PER_PLAYER = 300

    /** TA39: a death costs 20% of Total ... */
    private const val DEATH_PERCENT = 20

    /** ... but at least this many points. */
    private const val DEATH_MINIMUM = 1_000

    private const val PERCENT = 100

    /** Multipliers in tenths of a point per damage (TA36, runelite-timings RT165); integer so the truncation is exact. */
    private const val TENTHS = 10
    private const val X0 = 0
    private const val X05 = 5
    private const val X12 = 12
    private const val X15 = 15
    private const val X20 = 20
    private const val X25 = 25

    /** RT165: ids not in [ToaNpcs] - the Warden cores, the boulders, the phase-1 forms and the inactive phase-3 forms earn nothing. */
    private val NO_POINTS =
        setOf(11770, 11771, 11782, 11783, 11746, 11747, 11748, 11749, 11755, 11758, 11759, 11760)

    /** RT165: the three scarabs 0.5x (TA36). */
    private val HALF_POINTS = ToaNpcs.OVERLORDS

    /** RT165: all ten Path of Apmeken baboon ids 1.2x (TA36). */
    private val BABOONS = (11709..11718).toSet()

    /** RT165: Zebak's forms (and its transmog id 10680) and the obelisks 1.5x (TA36). */
    private val ONE_AND_HALF = ToaNpcs.ZEBAK + 10680 + ToaNpcs.OBELISKS

    /** RT165: Ba-Ba and the phase-2 Wardens 2x (TA36). */
    private val DOUBLE = ToaNpcs.BABA + ToaNpcs.P2_WARDENS

    /** RT165: Het's Seal (weakened form 11707) and the phase-3 Wardens 2.5x (TA36). */
    private val TWO_AND_HALF = ToaNpcs.P3_WARDENS + 11707

    /** TA36 as tenths of a point per damage; everything else (Akkha, Kephri, swarms, siphon ...) is 1x. */
    fun multiplierTenths(npcType: Int): Int =
        when (npcType) {
            in NO_POINTS -> X0
            in HALF_POINTS -> X05
            in BABOONS -> X12
            in ONE_AND_HALF -> X15
            in DOUBLE -> X20
            in TWO_AND_HALF -> X25
            else -> TENTHS
        }

    fun ledger(run: RaidRun): ToaLedger? = ToaFlow.state(run)?.ledger

    /** The room point cap of [roomId] (TA35, OPEN-TA14). */
    fun roomCap(roomId: String): Int =
        when (roomId) {
            "zebak" -> ToaConfig.zebakRoomPointCap
            ToaRaid.WARDENS -> ToaConfig.wardensRoomPointCap
            else -> ROOM_CAP
        }

    /** TA39: `max(20% of Total, 1,000)`, truncated; raids-core floors the Total at 0. */
    fun deathPenalty(member: RaidMember): Double = deathPenaltyOf(member.points)

    /** TA39 for a Total of [total]. */
    fun deathPenaltyOf(total: Double): Double = maxOf(total.toLong() * DEATH_PERCENT / PERCENT, DEATH_MINIMUM.toLong()).toDouble()

    /** The 5,000 start, once, when the first Nexus begins. */
    fun start(run: RaidRun) {
        val l = ledger(run) ?: return
        if (l.started) return
        l.started = true
        for (m in run.members.values) Raids.addPoints(run, m.name, START_POINTS.toDouble())
    }

    /** A hit by [name] on one of the run's NPCs (TA35 / TA36): the raw damage counts for the summary, the multiplied one for points. */
    fun dealt(run: RaidRun, name: String, npcType: Int, damage: Int, style: HitStyle) {
        val state = run.state as? RunState.InRoom ?: return
        val l = ledger(run) ?: return
        val m = run.member(name) ?: return
        val t = l.tally(m.name)
        t.dealt += damage
        when (style) {
            HitStyle.MELEE -> t.melee += damage
            HitStyle.RANGED -> t.ranged += damage
            HitStyle.MAGIC -> t.magic += damage
            HitStyle.TYPELESS -> Unit
        }
        val tenths = multiplierTenths(npcType)
        if (tenths == X0) return
        // TA36: the phase-2 Wardens stop paying after their Nth down (post: the third).
        if (npcType in ToaNpcs.P2_WARDENS && l.p2Downs >= ToaConfig.wardensP2PointDowns) return
        addRoom(run, m, t, state.room.id, damage * tenths / TENTHS)
    }

    /** A hit on a member: damage taken for the summary (self-inflicted damage has no NPC source). */
    fun taken(run: RaidRun, name: String, damage: Int) {
        val l = ledger(run) ?: return
        val m = run.member(name) ?: return
        l.tally(m.name).taken += damage
    }

    /** Room points from content (puzzle actions, TA40): capped like damage points. */
    fun add(run: RaidRun, name: String, points: Int) {
        val state = run.state as? RunState.InRoom ?: return
        val l = ledger(run) ?: return
        val m = run.member(name) ?: return
        addRoom(run, m, l.tally(m.name), state.room.id, points)
    }

    /**
     * One puzzle action's points (puzzles.md PC5, OPEN-PZ10 "insider knowledge"), only when the flat estimates are off
     * (`toa.puzzlePoints.flat=false`): the room then reports its own points. A negative value (a mistimed repair or pour,
     * a failed corruption) takes room points off, never below 0.
     */
    fun puzzleAction(run: RaidRun, name: String, points: Int) {
        if (ToaConfig.puzzleFlatPoints) return
        val state = run.state as? RunState.InRoom ?: return
        puzzleReportsPoints(run, state.room.id)
        if (points >= 0) {
            add(run, name, points)
            return
        }
        val l = ledger(run) ?: return
        val m = run.member(name) ?: return
        val t = l.tally(m.name)
        val off = minOf(-points, t.room)
        if (off <= 0) return
        t.room -= off
        if (ToaConfig.roomPointsLiveInTotal) Raids.addPoints(run, m.name, -off.toDouble())
    }

    /** PC5: 25 per Apmeken pillar repaired, vent neutralised, cure, or corruption cleared by the sight; -25 a mistake. */
    const val APMEKEN_ACTION: Int = 25

    /** PC5: 50 per puzzle completed, to each player in the lane (the Scabaras puzzles, each Het beam on the statue). */
    const val PUZZLE_COMPLETED: Int = 50

    /** PC5: 100 per matched tile in the memory puzzle, to the second player only. */
    const val MEMORY_MATCH: Int = 100

    /** PC5: 2 points per health of water given to the palm. */
    const val CRONDIS_PER_WATER: Int = 2

    /** A puzzle room that reports its own points (TA40): the flat estimate is not added for [roomId] at completion. */
    fun puzzleReportsPoints(run: RaidRun, roomId: String) {
        ledger(run)?.detailedPuzzles?.add(roomId)
    }

    /** A phase-2 Warden went down (TA36); the Wardens' fight calls this once per down. */
    fun wardenP2Down(run: RaidRun) {
        ledger(run)?.let { it.p2Downs++ }
    }

    /** A title figure (TA67); [amount] adds to the counter. */
    fun stat(run: RaidRun, name: String, stat: ToaStat, amount: Int = 1) {
        val l = ledger(run) ?: return
        val m = run.member(name) ?: return
        l.tally(m.name).stats[stat.ordinal] += amount
    }

    private fun addRoom(run: RaidRun, m: RaidMember, t: ToaTally, roomId: String, points: Int) {
        if (points <= 0) return
        val room = minOf(points, roomCap(roomId) - t.room)
        if (room <= 0) return
        t.room += room
        // OPEN-TA13 (RL-TOA's reading): the Total moves with the room points.
        if (ToaConfig.roomPointsLiveInTotal) addTotal(run, m, room)
    }

    /** Adds to the Total, never above the 64,000 cap (TA35). */
    private fun addTotal(run: RaidRun, m: RaidMember, points: Int) {
        val delta = minOf(points.toDouble(), TOTAL_CAP - m.points)
        if (delta > 0) Raids.addPoints(run, m.name, delta)
    }

    /**
     * A room was cleared (TA35 / TA38): the room points join the Total, the MVP of the room gets 300 x team size, and
     * the figures are kept for the summary. Puzzle rooms add their flat estimate first unless they reported their own.
     */
    fun roomCleared(run: RaidRun, roomId: String, kind: RoomKind, ticks: Long) {
        val l = ledger(run) ?: return
        l.roomTicks[roomId] = ticks
        if (kind != RoomKind.ENCOUNTER) return
        if (ToaConfig.puzzleFlatPoints && roomId !in l.detailedPuzzles) {
            flatPoints(roomId)?.let { flat -> for (m in run.members.values) if (m.inside) addRoom(run, m, l.tally(m.name), roomId, flat) }
        }
        val shares = LinkedHashMap<String, Int>()
        for (m in run.members.values) {
            val t = l.tally(m.name)
            shares[m.name] = t.room
            if (!ToaConfig.roomPointsLiveInTotal) addTotal(run, m, t.room)
        }
        l.roomPoints[roomId] = shares
        if (ToaConfig.mvpPuzzleRooms || ToaPath.entries.none { it.puzzle == roomId }) awardMvp(run, l, roomId, shares)
        for (m in run.members.values) l.tally(m.name).room = 0
    }

    private fun flatPoints(roomId: String): Int? =
        when (roomId) {
            "scabaras" -> ToaConfig.puzzlePointsScabaras
            "apmeken" -> ToaConfig.puzzlePointsApmeken
            "crondis" -> ToaConfig.puzzlePointsCrondis
            else -> null
        }

    /** TA38: the most room points in the room; a tie goes to the first in party order unless OPEN-TA18's flag pays all. */
    private fun awardMvp(run: RaidRun, l: ToaLedger, roomId: String, shares: Map<String, Int>) {
        val present = shares.filterKeys { run.member(it)?.inside == true }
        val best = present.values.maxOrNull() ?: return
        if (best <= 0) return
        val winners = present.filterValues { it == best }.keys.let { if (ToaConfig.mvpTieAwardsAll) it.toList() else it.take(1) }
        val bonus = MVP_PER_PLAYER * run.scaling.partySize
        for (name in winners) run.member(name)?.let { addTotal(run, it, bonus) }
        l.mvp[roomId] = winners.joinToString(",")
    }

    /** A wipe: the room's points are lost (TA35 official reading; RL-TOA's counter resets too) and the Wardens' downs restart. */
    fun roomFailed(run: RaidRun) {
        val l = ledger(run) ?: return
        for (m in run.members.values) l.tally(m.name).room = 0
        l.p2Downs = 0
    }

    /** The contribution of a player: Total minus the 5,000 start, never below 0 (TA37, OPEN-TA17). */
    fun contribution(total: Double): Int = maxOf(0, total.toInt() - START_POINTS)
}

/** Feeds the ledger from combat and raid events (toa.md TA35-TA39). */
public class ToaPointsPlugin : ContentPlugin {
    override fun ContentScope.register() {
        events.on<HitEvent> { e -> hit(e) }
        events.on<RaidEvent.RoomStarted> { e -> if (e.run.raid.id == ToaRaid.ID && e.room.id == ToaRaid.NEXUS) ToaPoints.start(e.run) }
        events.on<RaidEvent.RoomCleared> { e ->
            if (e.run.raid.id == ToaRaid.ID) ToaPoints.roomCleared(e.run, e.room.id, e.room.kind, e.ticks)
        }
        events.on<RaidEvent.RoomFailed> { e -> if (e.run.raid.id == ToaRaid.ID) ToaPoints.roomFailed(e.run) }
    }

    private fun hit(e: HitEvent) {
        val target = e.target
        val source = e.source
        if (target is NpcRef && source is PlayerRef) {
            val runId =
                tp.content.raids.RaidRun
                    .npcRun(target) ?: return
            val run = Raids.of(target).run(runId) ?: return
            // TA36 (OPEN-TA16): only the player's own hits count; thralls and pets are not the player.
            if (run.raid.id == ToaRaid.ID) ToaPoints.dealt(run, source.username, target.type, e.damage, e.style)
        } else if (target is PlayerRef && source is NpcRef) {
            val run = Raids.of(target).runOf(target.username) ?: return
            if (run.raid.id == ToaRaid.ID) ToaPoints.taken(run, target.username, e.damage)
        }
    }
}
