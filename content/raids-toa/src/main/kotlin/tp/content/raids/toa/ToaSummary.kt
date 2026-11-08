package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.content.raids.RunState
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits

/** The 17 honorary titles of the raid summary (toa.md TA67; cache enum 4668 keys, client-contract 4.4). */
internal object ToaTitles {
    const val LEECH = 1
    const val SWIMMER = 2
    const val SPECIALIST = 3
    const val PICKY_EATER = 4
    const val PEASANT = 5
    const val SHOWOFF = 6
    const val TANK = 7
    const val BALLER = 8
    const val ENTOMOPHOBE = 9
    const val MOTH = 10
    const val CARRY = 11
    const val ANCHOR = 12
    const val GLUTTON = 13
    const val SORCERER = 14
    const val ARCHER = 15
    const val BRAWLER = 16
    const val CLUTCH = 17

    /** The title nothing else earned: it has no struct in the cache, so the summary shows "-" (OPEN-TA32 / OPEN-TC7). */
    const val CHOCOLATE_TEAPOT = 0

    /** Enum 4668: title id -> struct; struct param 1528 is read as the title's priority (OPEN-TA32). */
    private const val ENUM_TITLES = 4668
    private const val P_PRIORITY = 1528

    /** TA67: "at least 30 seconds in Zebak's pool", at 0.6 s per tick. */
    private const val SWIMMER_TICKS = 50

    /** TA67: "more than 20 orbs" dropped in the Akkha fight. */
    private const val BALLER_ORBS = 20

    /** TA67: the Leech was the lowest contributor in at least five encounters. */
    private const val LEECH_ROOMS = 5

    /** The achieved flag varbit of each title (cache `TOA_TITLE_ACHIEVED_*`). */
    private val ACHIEVED =
        mapOf(
            LEECH to Varbits.TOA_TITLE_ACHIEVED_LEECH,
            SWIMMER to Varbits.TOA_TITLE_ACHIEVED_SWIMMER,
            SPECIALIST to Varbits.TOA_TITLE_ACHIEVED_SPECIALIST,
            PICKY_EATER to Varbits.TOA_TITLE_ACHIEVED_PICKYEATER,
            PEASANT to Varbits.TOA_TITLE_ACHIEVED_PEASANT,
            SHOWOFF to Varbits.TOA_TITLE_ACHIEVED_SHOWOFF,
            TANK to Varbits.TOA_TITLE_ACHIEVED_TANK,
            BALLER to Varbits.TOA_TITLE_ACHIEVED_BALLER,
            ENTOMOPHOBE to Varbits.TOA_TITLE_ACHIEVED_ENTOMOPHOBE,
            MOTH to Varbits.TOA_TITLE_ACHIEVED_MOTH,
            CARRY to Varbits.TOA_TITLE_ACHIEVED_CARRY,
            ANCHOR to Varbits.TOA_TITLE_ACHIEVED_ANCHOR,
            GLUTTON to Varbits.TOA_TITLE_ACHIEVED_GLUTTON,
            SORCERER to Varbits.TOA_TITLE_ACHIEVED_SORCERER,
            ARCHER to Varbits.TOA_TITLE_ACHIEVED_ARCHER,
            BRAWLER to Varbits.TOA_TITLE_ACHIEVED_BRAWLER,
            CLUTCH to Varbits.TOA_TITLE_ACHIEVED_CLUTCH,
            CHOCOLATE_TEAPOT to Varbits.TOA_TITLE_ACHIEVED_CHOCOLATETEAPOT,
        )

    /** Sets the achieved flag of [title] for [p] (the flags are saved client state; no client script reads them). */
    fun markAchieved(p: PlayerRef, title: Int) {
        val varbit = ACHIEVED[title] ?: return
        if (p.varbit(varbit) == 0) p.setVarbit(varbit, 1)
    }

    /**
     * Gives each member who finished one title (TA67). Relative titles (most / least of something) go to a unique leader
     * only, or to the one member of a solo run; threshold titles go to everyone who reached them. With several, the highest
     * cache priority (param 1528) wins, then the lower id (OPEN-TA32). The loadout values must be in the tallies first.
     */
    fun assign(run: RaidRun, any: PlayerRef) {
        val l = ToaPoints.ledger(run) ?: return
        val ms = run.members.values.filter { it.inside }
        if (ms.isEmpty()) return
        val t = ms.associate { it.name to l.tally(it.name) }
        val a = Awards(ms.size)
        a.relative(CARRY, t.mapValues { it.value.dealt })
        a.relative(TANK, t.mapValues { it.value.taken })
        a.relative(SORCERER, t.mapValues { it.value.magic })
        a.relative(ARCHER, t.mapValues { it.value.ranged })
        a.relative(BRAWLER, t.mapValues { it.value.melee })
        a.relative(SPECIALIST, stat(t, ToaStat.SPECIAL_ENERGY))
        a.relative(ENTOMOPHOBE, stat(t, ToaStat.SCARABS_KILLED))
        a.relative(GLUTTON, stat(t, ToaStat.FOOD_EATEN))
        a.relative(PICKY_EATER, stat(t, ToaStat.FOOD_EATEN), wantMax = false)
        a.relative(SHOWOFF, stat(t, ToaStat.LOADOUT_VALUE))
        a.relative(PEASANT, stat(t, ToaStat.LOADOUT_VALUE), wantMax = false)
        a.relative(ANCHOR, ms.associate { it.name to it.deaths.toLong() })
        a.threshold(SWIMMER, stat(t, ToaStat.POOL_TICKS), SWIMMER_TICKS)
        a.threshold(BALLER, stat(t, ToaStat.ORBS_DROPPED), BALLER_ORBS + 1)
        a.threshold(MOTH, stat(t, ToaStat.LIGHT_BEAM_DEATHS), 1)
        a.threshold(CLUTCH, stat(t, ToaStat.WARDENS_FINISHED), 1)
        if (ms.size > 1) leeches(run, l, t).forEach { a.award(LEECH, it) }
        val priorities = any.enumInts(ENUM_TITLES).mapValues { (_, struct) -> any.structInt(struct, P_PRIORITY) ?: 0 }
        val byPriority = compareBy<Int> { priorities[it] ?: 0 }.thenByDescending { it }
        for (m in ms) l.titles[m.name] = a.won[m.name]?.maxWithOrNull(byPriority) ?: CHOCOLATE_TEAPOT
    }

    private fun stat(t: Map<String, ToaTally>, s: ToaStat): Map<String, Long> = t.mapValues { it.value.stats[s.ordinal].toLong() }

    /** The titles earned so far, by member name. */
    private class Awards(private val members: Int) {
        val won = HashMap<String, MutableList<Int>>()

        fun award(title: Int, name: String) {
            won.getOrPut(name) { ArrayList() } += title
        }

        /** Most (or least) of something: a unique leader, or the only member of a solo run; the most needs at least one. */
        fun relative(title: Int, values: Map<String, Long>, wantMax: Boolean = true) {
            val best = (if (wantMax) values.values.maxOrNull() else values.values.minOrNull()) ?: return
            if (wantMax && best < 1) return
            val leaders = values.filterValues { it == best }.keys
            if (leaders.size == 1 || members == 1) leaders.forEach { award(title, it) }
        }

        fun threshold(title: Int, values: Map<String, Long>, atLeast: Int) {
            for ((name, v) in values) if (v >= atLeast) award(title, name)
        }
    }

    /**
     * TA67 / 19 Oct 2022: the lowest room contribution in at least five rooms, but not the highest damage dealer and not
     * an MVP.
     */
    private fun leeches(run: RaidRun, l: ToaLedger, t: Map<String, ToaTally>): List<String> {
        val topDamage = t.values.maxOf { it.dealt }
        return t.keys.filter { name ->
            val lowest =
                l.roomPoints.values.count { room -> room[name] == room.filterKeys { run.member(it)?.inside == true }.values.minOrNull() }
            val mvp = l.mvp.values.any { it.split(",").contains(name) }
            lowest >= LEECH_ROOMS && !mvp && t.getValue(name).dealt < topDamage
        }
    }

    /** The struct the summary's script 6590 takes for [title], or -1 ("-") for the Chocolate Teapot. */
    fun struct(any: PlayerRef, title: Int): Int = any.enumInts(ENUM_TITLES)[title] ?: -1
}

/**
 * The raid summary 482 at the burial chamber's scoreboard (toa.md TA66; client-contract 2.6): script 6590 builds one
 * player row, every other cell is plain text.
 */
internal object ToaSummary {
    /** Server-run script `6590(dealt, taken, deaths, titleStruct, unused, row, rowCount, mvp, name)` (client-contract 2.6). */
    private const val CS_PLAYER_ROW = 6590

    /** Client-contract 2.6: a value of -1 prints a grey "-". */
    private const val NO_VALUE = -1
    private const val UNUSED = 0

    private const val NO_TEXT = "-"

    private val BOSS_TIMES =
        mapOf(
            ToaRaid.WARDENS to Components.Toa_Raid_Summary.WARDENS_TIME,
            "baba" to Components.Toa_Raid_Summary.BABA_TIME,
            "zebak" to Components.Toa_Raid_Summary.ZEBAK_TIME,
            "akkha" to Components.Toa_Raid_Summary.AKKHA_TIME,
            "kephri" to Components.Toa_Raid_Summary.KEPHRI_TIME,
        )
    private val PATH_TIMES =
        mapOf(
            "crondis" to Components.Toa_Raid_Summary.CRONDIS_TIME,
            "apmeken" to Components.Toa_Raid_Summary.APMEKEN_TIME,
            "het" to Components.Toa_Raid_Summary.HET_TIME,
            "scabaras" to Components.Toa_Raid_Summary.SCABARAS_TIME,
        )

    /** "Read" on the burial chamber's scoreboard 44942: only a member of a completed run reads its summary. */
    fun open(p: PlayerRef) {
        val run = ToaFlow.runOf(p) ?: return
        val st = ToaFlow.state(run) ?: return
        val l = ToaPoints.ledger(run) ?: return
        if (run.state != RunState.Completed) return
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_RAID_SUMMARY)
        val rows = run.members.values.toList()
        val star = mvpOf(l)
        for ((i, m) in rows.withIndex()) {
            val t = l.tally(m.name)
            val title = l.titles[m.name]?.let { ToaTitles.struct(p, it) } ?: NO_VALUE
            p.clientScript(
                CS_PLAYER_ROW,
                t.dealt.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                t.taken.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                m.deaths,
                title,
                UNUSED,
                i,
                rows.size,
                if (m.name == star) 1 else 0,
                m.name,
            )
        }
        for ((room, component) in BOSS_TIMES + PATH_TIMES) p.ifText(component, time(p, l.roomTicks[room]))
        p.ifText(Components.Toa_Raid_Summary.CHALLENGE_TOTAL, time(p, ToaRecords.challengeTicks(l)))
        p.ifText(Components.Toa_Raid_Summary.OVERALL_TOTAL, time(p, run.endedTick - run.startedTick))
        // OPEN-TC6: the invocation table's text format is not captured; the level, then the active names one per line.
        val active = ToaInvocations.active(p, st.words)
        p.ifText(Components.Toa_Raid_Summary.RAID_LEVEL, st.raidLevel.toString())
        p.ifText(Components.Toa_Raid_Summary.ACTIVE, active.joinToString("<br>") { it.name }.ifEmpty { NO_TEXT })
    }

    /** The star goes to the member who was the MVP of the most rooms (the first to reach the highest count). */
    private fun mvpOf(l: ToaLedger): String? =
        l.mvp.values
            .flatMap { it.split(",") }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    private fun time(p: PlayerRef, ticks: Long?): String = if (ticks == null || ticks <= 0) NO_TEXT else ToaRecords.formatTime(p, ticks)

    /** The loadout's Grand Exchange value for The Peasant / The Showoff (OPEN-TA32: worn plus backpack by flag). */
    fun loadoutValue(p: PlayerRef): Int {
        var total = 0L
        val invs = if (ToaConfig.loadoutIncludesInventory) intArrayOf(Invs.WORN, Invs.INV) else intArrayOf(Invs.WORN)
        for (id in invs) {
            val inv = p.inv(id)
            for (slot in 0 until inv.size) if (inv.obj(slot) >= 0) total += p.objValue(inv.obj(slot)).toLong() * inv.count(slot)
        }
        return total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Records the loadout values and assigns the titles; called once when the raid completes. */
    fun finish(run: RaidRun, any: EntityRef) {
        val l = ToaPoints.ledger(run) ?: return
        val players = run.presentPlayers(any)
        for (p in players) run.member(p.username)?.let { l.tally(it.name).stats[ToaStat.LOADOUT_VALUE.ordinal] = loadoutValue(p) }
        val first = players.firstOrNull() ?: return
        ToaTitles.assign(run, first)
        for (p in players) {
            val title = run.member(p.username)?.let { l.titles[it.name] } ?: continue
            ToaTitles.markAchieved(p, title)
        }
    }
}
