package tp.content.raids.toa

import tp.content.raids.Party
import tp.content.raids.PartyState
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits
import tp.symbols.Varps

/** What a viewer of 774 was sent, so a click on row n acts on the player shown there even if the party changed since. */
internal class ToaPartyView(
    val partyId: Int,
    val members: List<String>,
    val applicants: List<String>,
)

/**
 * The lobby's world-scoped state beside raids-core's parties: when each party was made (the list's age column), who a
 * leader has blocked from applying, and the last stats seen of each player (rows of offline members).
 */
internal class ToaLobbyWorld {
    /** Party id -> tick it was made. */
    val created = HashMap<Int, Long>()

    /** Party id -> lower-case names blocked from applying to it (OPEN-TAUI3). */
    val blocked = HashMap<Int, MutableSet<String>>()

    /** Lower-case name -> [snapshot]. */
    val stats = HashMap<String, IntArray>()

    companion object {
        private val KEY = AttrKey<ToaLobbyWorld>("toa_lobby_world")

        fun of(any: EntityRef): ToaLobbyWorld = any.worldAttr(KEY) ?: ToaLobbyWorld().also { any.setWorldAttr(KEY, it) }

        /** Snapshot fields: combat level, the seven combat stats of the row string, then the Entry / Normal / Expert completions. */
        private const val FIELDS = 11
        private const val COMPLETIONS_AT = 8

        /** The combat stats in the order of the row string (client-contract 2.4: att, str, rng, mag, def, hp, prayer). */
        private val ROW_STATS =
            intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.RANGED, Stats.MAGIC, Stats.DEFENCE, Stats.HITPOINTS, Stats.PRAYER)

        /** Completion varps in [ToaRaid.ENTRY], [ToaRaid.NORMAL], [ToaRaid.EXPERT] order (client-contract 1.1). */
        private val COMPLETION_VARPS =
            intArrayOf(
                Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY,
                Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT,
                Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT,
            )
        private val MODE_ORDER = listOf(ToaRaid.ENTRY, ToaRaid.NORMAL, ToaRaid.EXPERT)

        fun snapshot(p: PlayerRef): IntArray {
            val out = IntArray(FIELDS)
            out[0] = p.combatLevel
            for (i in ROW_STATS.indices) out[1 + i] = p.baseLevel(ROW_STATS[i])
            for (m in COMPLETION_VARPS.indices) out[COMPLETIONS_AT + m] = p.varp(COMPLETION_VARPS[m])
            return out
        }

        /** The completions of [mode] in [stats], 0 when the player was never seen. */
        fun completions(stats: IntArray?, mode: String): Int {
            stats ?: return 0
            return stats[COMPLETIONS_AT + MODE_ORDER.indexOf(mode).coerceAtLeast(0)]
        }

        /** The row field [index] (0 combat, 1..7 stats, 8 completions of [mode]), or null when the player was never seen. */
        fun field(stats: IntArray?, index: Int, mode: String): Int? {
            stats ?: return null
            return if (index < COMPLETIONS_AT) stats[index] else completions(stats, mode)
        }

        /** Row fields in the string after the name (client-contract 2.4: combat, att, str, rng, mag, def, hp, prayer, completions). */
        const val ROW_FIELDS = 9
    }
}

/** Board state keys of one player (session only). */
private val VIEW = AttrKey<ToaPartyView>("toa_party_view")
private val LIST_ROWS = AttrKey<IntArray>("toa_party_list_rows")
private val TAB = AttrKey<Int>("toa_party_tab")

/**
 * The Grouping Obelisk's party list 772 and party details 774 (toa.md TA2, TA3, TA6; toa/client-contract.md 2.3, 2.4,
 * 2.5, 4.1-4.3, section 3): the client scripts draw everything, the server sends the data rows (6601, 6722, 6727, 6729)
 * and answers the pause buttons. The invocation words live in the party's settings ([ToaInvocations]); only the leader
 * edits them and only while the party is forming. Preset buttons are in [ToaPresets]; the lobby overlay in [ToaLobby].
 */
internal object ToaBoard {
    /** Rows of the party list: the components `_00`..`_44` (client-contract 2.3, enum 4793). */
    const val MAX_LIST_ROWS = 45

    /** Member rows of the details screen: 6722 is called exactly this often (client-contract 2.4 step 2). */
    private const val MEMBER_ROWS = ToaRaid.MAX_PARTY

    /** Applicant rows: accept / reject sub-ids cover rows 0..7 (client-contract 2.4 step 3). */
    private const val APPLICANT_ROWS = 8

    /** Invocation tiles: the keys of enum 4664 (client-contract 2.5). */
    private const val TILE_COUNT = 46

    // ---- party list 772

    /** The Obelisk: every forming ToA party, oldest first (client-contract 2.3). */
    fun openList(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_PARTYLIST)
        val lobby = ToaLobbyWorld.of(p)
        val parties =
            Raids
                .of(p)
                .parties(ToaRaid.ID)
                .filter { it.state == PartyState.Forming }
                .take(MAX_LIST_ROWS)
        p[LIST_ROWS] = IntArray(parties.size) { parties[it].id.value }
        // Script 6601 finalises the layout on row 44: all 45 calls, in order, empty for vacant rows (client-contract 2.3).
        for (row in 0 until MAX_LIST_ROWS) {
            val party = parties.getOrNull(row)
            p.clientScript(SCRIPT_LIST_ROW, row, if (party == null) "" else listRow(p, party, lobby))
        }
    }

    /** The Invocation Board 46073 "Read" (client-contract 7.2): 776 draws the 44 invocations itself; nothing is sent. */
    fun openInvocationBoard(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_INVOCATIONS)
    }

    /** `RESUME_PAUSEBUTTON` on 772:1: 0 Refresh, 1 Make party / My party, 2 Friends/Clan filter (client-contract 2.3). */
    fun listButton(p: PlayerRef, sub: Int) {
        if (!p.isOpen(Interfaces.TOA_PARTYLIST)) return
        when (sub) {
            LIST_REFRESH -> {
                openList(p)
            }

            LIST_MYPARTY -> {
                myParty(p)
            }

            LIST_FILTER -> {
                p.setVarbit(Varbits.TOA_PARTYLIST_FILTER, 1 - p.varbit(Varbits.TOA_PARTYLIST_FILTER))
                openList(p)
            }

            else -> {
                Unit
            }
        }
    }

    /** "View Party:" on list row [row] (resume on 772:16, sub = row): only rows this player was sent are valid (hostile input). */
    fun listRow(p: PlayerRef, row: Int) {
        if (!p.isOpen(Interfaces.TOA_PARTYLIST)) return
        val id = p[LIST_ROWS]?.getOrNull(row) ?: return
        val party = party(p, id)
        if (party == null) {
            p.message(PARTY_GONE)
            openList(p)
        } else {
            openDetails(p, party, TAB_MEMBERS)
        }
    }

    private fun myParty(p: PlayerRef) {
        val mine = partyOf(p)
        if (mine != null) {
            openDetails(p, mine, TAB_MEMBERS)
            return
        }
        val raids = Raids.of(p)
        val def = raids.definition(ToaRaid.ID) ?: return
        def.requirements(p)?.let {
            p.message(it)
            return
        }
        val party = raids.createParty(def, p.username)
        if (party == null) {
            p.message(ALREADY_IN_PARTY)
            return
        }
        ToaLobbyWorld.of(p).created[party.id.value] = p.tick
        syncMembers(p, party, emptyList())
        openDetails(p, party, TAB_MEMBERS)
    }

    /**
     * Row string of script 6601, 14 `|` fields (client-contract 2.3): the leader, seven further members (blank when
     * vacant), member count, completions requirement, active invocations, raid level, difficulty, age in ticks.
     */
    private fun listRow(p: PlayerRef, party: Party, lobby: ToaLobbyWorld): String {
        val words = ToaInvocations.words(party)
        val level = ToaInvocations.raidLevel(p, words)
        val age = (p.tick - lobby.created.getOrPut(party.id.value) { p.tick }).coerceAtLeast(0)
        val sb = StringBuilder(party.leader)
        for (slot in 1 until MEMBER_ROWS) sb.append(SEPARATOR).append(party.members.getOrNull(slot) ?: "")
        sb.append(SEPARATOR).append(party.members.size)
        sb.append(SEPARATOR).append(party.settings[SETTING_MIN_COMPLETIONS] ?: 0)
        sb.append(SEPARATOR).append(ToaInvocations.active(p, words).size)
        sb.append(SEPARATOR).append(level)
        sb.append(SEPARATOR).append(modeName(ToaRaid.modeOf(level)))
        // Script 1574 reads the age as game ticks and adds the time since varc 59 (set when 772 loads).
        sb.append(SEPARATOR).append(age)
        return sb.toString()
    }

    // ---- party details 774

    /** Opens the screen at [tab] and sends the rows in the contract order: 6722 x 8, 6727 per applicant, 6729 last. */
    fun openDetails(p: PlayerRef, party: Party, tab: Int) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_PARTYDETAILS)
        p[TAB] = tab
        val role = roleOf(p, party)
        val members = party.members.toList()
        val applicants = party.applicants.take(APPLICANT_ROWS)
        p[VIEW] = ToaPartyView(party.id.value, members, applicants)
        val mode = ToaRaid.modeOf(ToaInvocations.raidLevel(p, ToaInvocations.words(party)))
        for (row in 0 until MEMBER_ROWS) {
            p.clientScript(SCRIPT_MEMBER_ROW, role, members.getOrNull(row)?.let { statsRow(p, it, mode) } ?: "")
        }
        for (name in applicants) p.clientScript(SCRIPT_APPLICANT_ROW, statsRow(p, name, mode))
        sendSettings(p, party, role, tab)
    }

    /** Script 6729: role, completions minimum, active count, raid level, tab and the three invocation words (client-contract 2.4). */
    private fun sendSettings(p: PlayerRef, party: Party, role: Int, tab: Int) {
        val words = ToaInvocations.words(party)
        p.clientScript(
            SCRIPT_SETTINGS,
            role,
            party.settings[SETTING_MIN_COMPLETIONS] ?: 0,
            ToaInvocations.active(p, words).size,
            ToaInvocations.raidLevel(p, words),
            tab,
            words[0],
            words[1],
            words[2],
        )
    }

    /** Re-runs 6729 alone (tabs, tiles, presets, completions text), or re-opens when [ToaUiConfig.reopenOnEdit] (OPEN-TAUI4). */
    private fun refresh(p: PlayerRef, party: Party) {
        val tab = p[TAB] ?: TAB_MEMBERS
        if (ToaUiConfig.reopenOnEdit) openDetails(p, party, tab) else sendSettings(p, party, roleOf(p, party), tab)
    }

    /** `RESUME_PAUSEBUTTON` on 774:1: sub-ids 0-97 (client-contract 2.4). Every id and the viewer's role are checked here. */
    fun detailsButton(p: PlayerRef, sub: Int) {
        val (view, party) = viewed(p) ?: return
        val tab = p[TAB] ?: TAB_MEMBERS
        when (sub) {
            SUB_BACK -> {
                openList(p)
            }

            SUB_REFRESH -> {
                openDetails(p, party, tab)
            }

            SUB_UNBLOCK -> {
                if (canEdit(p, party)) {
                    ToaLobbyWorld.of(p).blocked.remove(party.id.value)
                    refresh(p, party)
                }
            }

            SUB_COMPLETIONS -> {
                askCompletions(p, party)
            }

            SUB_ACTION -> {
                action(p, party)
            }

            SUB_CLEAR -> {
                setInvocations(p, party, IntArray(WORDS))
            }

            SUB_PRESET_LOAD -> {
                ToaPresets.load(p, party)
            }

            SUB_PRESET_SAVE -> {
                ToaPresets.save(p, party)
            }

            in SUB_TAB until SUB_TAB + TABS -> {
                p[TAB] = sub - SUB_TAB
                refresh(p, party)
            }

            in SUB_KICK until SUB_KICK + MEMBER_ROWS -> {
                kick(p, party, view, sub - SUB_KICK)
            }

            in SUB_UP until SUB_UP + MEMBER_ROWS -> {
                move(p, party, view, sub - SUB_UP, -1)
            }

            in SUB_DOWN until SUB_DOWN + MEMBER_ROWS -> {
                move(p, party, view, sub - SUB_DOWN, 1)
            }

            in SUB_ACCEPT until SUB_ACCEPT + APPLICANT_ROWS -> {
                accept(p, party, view, sub - SUB_ACCEPT)
            }

            in SUB_REJECT until SUB_REJECT + APPLICANT_ROWS -> {
                reject(p, party, view, sub - SUB_REJECT)
            }

            in SUB_TILE until SUB_TILE + TILE_COUNT -> {
                toggleTile(p, party, sub - SUB_TILE)
            }

            else -> {
                Unit
            }
        }
    }

    /** The open details screen's view and its party; null (after the right message) when either is gone. */
    private fun viewed(p: PlayerRef): Pair<ToaPartyView, Party>? {
        if (!p.isOpen(Interfaces.TOA_PARTYDETAILS)) return null
        val view = p[VIEW] ?: return null
        val party = party(p, view.partyId)
        if (party == null) {
            p.message(PARTY_GONE)
            openList(p)
            return null
        }
        return view to party
    }

    /** IF_BUTTON1 / 2 on 774:98 child [slot]: preset Select / Clear (client-contract 2.4, 4.3); any viewer (their own varps). */
    fun presetClick(p: PlayerRef, slot: Int, clear: Boolean) {
        // The presets are the viewer's own varps (client-contract 4.3): anyone with the screen open manages their own.
        val (_, party) = viewed(p) ?: return
        if (slot !in 0 until ToaPresets.SLOTS) return
        if (clear) ToaPresets.clear(p, slot) else ToaPresets.select(p, slot)
        refresh(p, party)
    }

    /** The leader ticks tile [key] (client-contract 2.4 sub 52 + key; rules in [ToaInvocations.toggle], OPEN-TA4). */
    private fun toggleTile(p: PlayerRef, party: Party, key: Int) {
        if (!canEdit(p, party)) return
        setInvocations(p, party, ToaInvocations.toggle(p, ToaInvocations.words(party), key))
    }

    /** Stores [words] as the party's invocations, moves the members' client raid level, and redraws the viewer. */
    fun setInvocations(p: PlayerRef, party: Party, words: IntArray) {
        if (!canEdit(p, party)) return
        ToaInvocations.setWords(party, words)
        syncMembers(p, party, emptyList())
        refresh(p, party)
    }

    /** Runs the edit only for the leader of a forming party; says why when the party has started. */
    fun canEdit(p: PlayerRef, party: Party): Boolean {
        if (!isLeader(p, party)) return false
        if (party.state != PartyState.Forming) {
            p.message(PARTY_STARTED)
            return false
        }
        return true
    }

    /**
     * Sub 3: the leader's "Set" op shows `...` and resumes at once, script 6744 restores the old text after 80 client
     * cycles (1.6 s), so 6729 is re-sent right away (client-contract 2.4) and again when the number arrives.
     */
    private fun askCompletions(p: PlayerRef, party: Party) {
        if (!canEdit(p, party)) return
        refresh(p, party)
        val id = party.id.value
        p.countDialog(COMPLETIONS_PROMPT) { n ->
            val current = party(this, id)
            if (current != null && canEdit(this, current)) {
                if (n in 0..ToaUiConfig.maxCompletions) {
                    current.settings[SETTING_MIN_COMPLETIONS] = n
                    if (isOpen(Interfaces.TOA_PARTYDETAILS) && this[VIEW]?.partyId == id) refresh(this, current)
                } else {
                    message("Enter a number from 0 to ${ToaUiConfig.maxCompletions}.")
                }
            }
        }
    }

    /** The ACTION button by role: Disband (leader), Leave (member), Withdraw (applicant), Apply (script 6729 labels it). */
    private fun action(p: PlayerRef, party: Party) {
        when (roleOf(p, party)) {
            ROLE_LEADER, ROLE_MEMBER -> {
                if (party.state != PartyState.Forming) {
                    p.message(PARTY_STARTED)
                    return
                }
                val leaving = if (isLeader(p, party)) party.members.toList() else listOf(p.username)
                for (name in leaving) party.leave(name)
                if (party.state == PartyState.Disbanded) dropParty(ToaLobbyWorld.of(p), party)
                syncMembers(p, party, leaving)
                openList(p)
            }

            ROLE_APPLICANT -> {
                party.reject(p.username)
                openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
            }

            ROLE_NONE -> {
                if (party.apply(p.username)) p.message("You have applied to join ${party.leader}'s party.")
                openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
            }

            else -> {
                p.message(canApply(p, party) ?: PARTY_STARTED)
            }
        }
    }

    /** Kick, or on the leader's own row 0 "Quit Party" (the next member then leads, OPEN-TA23); leader only. */
    private fun kick(p: PlayerRef, party: Party, view: ToaPartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.members.getOrNull(row) ?: return
        if (view.members != party.members) return openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
        val lobby = ToaLobbyWorld.of(p)
        val quitting = row == 0
        val removed = if (quitting && !ToaConfig.leaderHandover) party.members.toList() else listOf(name)
        for (n in removed) party.leave(n)
        if (!quitting && ToaUiConfig.kickBlocksReapply) lobby.blocked.getOrPut(party.id.value) { HashSet() } += name.lowercase()
        if (party.state == PartyState.Disbanded) dropParty(lobby, party)
        syncMembers(p, party, removed)
        if (!quitting) p.onlinePlayer(name)?.message(KICKED)
        if (party.state == PartyState.Disbanded || quitting) openList(p) else openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
    }

    /**
     * "Move up" / "Move down" of member row [row] (pause subs 20 + row / 28 + row); re-validated against what was
     * sent. Move up on row 1 promotes the member to leader when [ToaConfig.moveUpPromotes] (OPEN-TC13), the old leader
     * taking row 1; the leader's own row never moves.
     */
    private fun move(p: PlayerRef, party: Party, view: ToaPartyView, row: Int, step: Int) {
        if (!canEdit(p, party)) return
        val members = party.members
        val other = row + step
        if (view.members == members && row in 1 until members.size && other in 0 until members.size) {
            if (other == 0) {
                if (ToaConfig.moveUpPromotes) party.promote(members[row])
            } else {
                party.reorder(row, other)
            }
        }
        openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
    }

    private fun accept(p: PlayerRef, party: Party, view: ToaPartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.applicants.getOrNull(row)
        if (name != null && party.applicants.any { it == name }) {
            val problem = applicantProblem(p, party, name)
            if (Raids.of(p).partyOf(name) != null) {
                party.reject(name)
                p.message("$name has joined another party.")
            } else if (problem != null) {
                p.message(problem)
            } else if (party.accept(name)) {
                syncMembers(p, party, emptyList())
            } else {
                p.message(PARTY_FULL)
            }
        }
        openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
    }

    private fun reject(p: PlayerRef, party: Party, view: ToaPartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.applicants.getOrNull(row)
        if (name != null && party.reject(name) && ToaUiConfig.rejectBlocksReapply) {
            ToaLobbyWorld.of(p).blocked.getOrPut(party.id.value) { HashSet() } += name.lowercase()
        }
        openDetails(p, party, p[TAB] ?: TAB_MEMBERS)
    }

    // ---- roles and rows

    private fun isLeader(p: PlayerRef, party: Party): Boolean = party.leader.equals(p.username, ignoreCase = true)

    /** Viewer role for script 6729 / 6722: 2 leader, 1 member, 3 applicant, 4 cannot apply, 0 may apply (client-contract 2.4). */
    private fun roleOf(p: PlayerRef, party: Party): Int =
        when {
            isLeader(p, party) -> ROLE_LEADER
            party.has(p.username) -> ROLE_MEMBER
            party.applicants.any { it.equals(p.username, ignoreCase = true) } -> ROLE_APPLICANT
            canApply(p, party) == null -> ROLE_NONE
            else -> ROLE_BLOCKED
        }

    private fun canApply(p: PlayerRef, party: Party): String? =
        when {
            party.state != PartyState.Forming -> PARTY_STARTED
            party.isFull -> PARTY_FULL
            Raids.of(p).partyOf(p.username) != null -> ALREADY_IN_PARTY
            ToaLobbyWorld.of(p).blocked[party.id.value]?.contains(p.username.lowercase()) == true -> BLOCKED
            else -> party.raid.requirements(p) ?: completionsProblem(p, party, ToaLobbyWorld.snapshot(p))
        }

    /** The reason [name] may not join [party] as an applicant being accepted, or null. */
    private fun applicantProblem(p: PlayerRef, party: Party, name: String): String? {
        val online = p.onlinePlayer(name)
        val lobby = ToaLobbyWorld.of(p)
        val stats = if (online != null) ToaLobbyWorld.snapshot(online) else lobby.stats[name.lowercase()]
        return online?.let { party.raid.requirements(it) } ?: completionsProblem(p, party, stats)
    }

    /** OPEN-TAUI2: the "Set Completions" minimum against the completions of the party's current mode. */
    private fun completionsProblem(p: PlayerRef, party: Party, stats: IntArray?): String? {
        val min = party.settings[SETTING_MIN_COMPLETIONS] ?: 0
        if (!ToaUiConfig.completionsGateApply || min <= 0) return null
        val mode = ToaRaid.modeOf(ToaInvocations.raidLevel(p, ToaInvocations.words(party)))
        return if (ToaLobbyWorld.completions(stats, mode) >= min) null else "You need at least $min completions to join this party."
    }

    /** One member or applicant row: `name|combat|att|str|rng|mag|def|hp|prayer|completions` (empty = "-"). */
    private fun statsRow(any: EntityRef, name: String, mode: String): String {
        val lobby = ToaLobbyWorld.of(any)
        val online = any.onlinePlayer(name)
        val stats =
            if (online != null) {
                ToaLobbyWorld.snapshot(online).also { lobby.stats[name.lowercase()] = it }
            } else {
                lobby.stats[name.lowercase()]
            }
        val sb = StringBuilder(name)
        for (i in 0 until ToaLobbyWorld.ROW_FIELDS) {
            sb.append(SEPARATOR)
            ToaLobbyWorld.field(stats, i, mode)?.let { sb.append(it) }
        }
        return sb.toString()
    }

    // ---- shared party state

    private fun party(any: EntityRef, id: Int): Party? = Raids.of(any).parties(ToaRaid.ID).firstOrNull { it.id.value == id }

    fun partyOf(p: PlayerRef): Party? = Raids.of(p).partyOf(p.username)?.takeIf { it.raid.id == ToaRaid.ID }

    private fun dropParty(lobby: ToaLobbyWorld, party: Party) {
        lobby.blocked.remove(party.id.value)
        lobby.created.remove(party.id.value)
    }

    private fun modeName(mode: String): String = MODE_NAMES[mode] ?: mode

    /**
     * Writes `TOA_MYCONTROLLER` (the party id, -1 none) and, while the party is forming, `TOA_CLIENT_RAID_LEVEL`
     * (kept current until the raid starts, toa.md TA7) for every online member and for [removed].
     */
    fun syncMembers(any: PlayerRef, party: Party, removed: Collection<String>) {
        val level = ToaInvocations.raidLevel(any, ToaInvocations.words(party))
        for (name in party.members) {
            any.onlinePlayer(name)?.let {
                setController(it, party)
                if (party.state == PartyState.Forming) setIfChanged(it, Varbits.TOA_CLIENT_RAID_LEVEL, level)
            }
        }
        for (name in removed) {
            any.onlinePlayer(name)?.let {
                if (!party.has(name)) {
                    setController(it, null)
                    setIfChanged(it, Varbits.TOA_CLIENT_RAID_LEVEL, 0)
                }
            }
        }
    }

    /** Keeps `TOA_MYCONTROLLER` (script 6598 turns the list button into "Make Party" / "My Party") in step with [party]. */
    fun setController(p: PlayerRef, party: Party?) {
        val id = party?.id?.value ?: NO_PARTY
        if (p.varp(Varps.TOA_MYCONTROLLER) != id) p.setVarp(Varps.TOA_MYCONTROLLER, id)
        ToaLobby.syncStatus(p)
    }

    /** Keeps the stats of a leaving player for the rows of a party he stays in. */
    fun logout(p: PlayerRef) {
        ToaLobbyWorld.of(p).stats[p.username.lowercase()] = ToaLobbyWorld.snapshot(p)
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    // ---- constants

    /** Party.settings key of the leader's completions minimum (774:72, client-contract 2.4). */
    const val SETTING_MIN_COMPLETIONS = "toa_min_completions"

    /** Server-run client script 6601 (`is`): party list row (client-contract 2.3). */
    private const val SCRIPT_LIST_ROW = 6601

    /** Server-run client script 6722 (`is`): member row, viewer role first (client-contract 2.4). */
    private const val SCRIPT_MEMBER_ROW = 6722

    /** Server-run client script 6727 (`s`): applicant row. */
    private const val SCRIPT_APPLICANT_ROW = 6727

    /** Server-run client script 6729 (`iiiiiiii`): role, completions, active count, raid level, tab, three words. */
    private const val SCRIPT_SETTINGS = 6729

    /** The row string separator read by the row scripts. */
    private const val SEPARATOR = '|'

    /** The invocation words per party (client-contract 2.5). */
    private const val WORDS = 3

    // 772:1 pause-button sub-ids (enum 4794, client-contract 2.3)
    private const val LIST_REFRESH = 0
    private const val LIST_MYPARTY = 1
    private const val LIST_FILTER = 2

    // 774:1 pause-button sub-ids (enums 4792 / 5345 / 6725 / 6746 / 6754, client-contract 2.4)
    private const val SUB_BACK = 0
    private const val SUB_REFRESH = 1
    private const val SUB_UNBLOCK = 2
    private const val SUB_COMPLETIONS = 3
    private const val SUB_ACTION = 4
    private const val SUB_CLEAR = 5
    private const val SUB_PRESET_LOAD = 6
    private const val SUB_PRESET_SAVE = 7
    private const val SUB_TAB = 8
    private const val SUB_KICK = 12
    private const val SUB_UP = 20
    private const val SUB_DOWN = 28
    private const val SUB_ACCEPT = 36
    private const val SUB_REJECT = 44
    private const val SUB_TILE = 52

    // tab values of script 6729 (client-contract 2.4)
    private const val TAB_MEMBERS = 0
    private const val TABS = 4

    // viewer roles, script 6729's switch (client-contract 2.4)
    private const val ROLE_NONE = 0
    private const val ROLE_MEMBER = 1
    private const val ROLE_LEADER = 2
    private const val ROLE_APPLICANT = 3
    private const val ROLE_BLOCKED = 4

    private const val NO_PARTY = -1

    /** The difficulty column (client-contract 2.3 field 13 [I]; the strings match the "Mode:" line of 774:74). */
    private val MODE_NAMES = mapOf(ToaRaid.ENTRY to "Entry", ToaRaid.NORMAL to "Normal", ToaRaid.EXPERT to "Expert")

    /**
     * OPEN-TAUI1: none of these texts is captured; placeholders until seen in game (the screens themselves are drawn
     * by the client scripts, only these server messages and the number prompt are ours).
     */
    private const val COMPLETIONS_PROMPT = "Enter the minimum completions (0 for none):"
    private const val PARTY_GONE = "That party no longer exists."
    private const val PARTY_STARTED = "That party has already entered the Tombs."
    private const val PARTY_FULL = "That party is full."
    private const val ALREADY_IN_PARTY = "You are already in a party."
    private const val BLOCKED = "The party leader has blocked you from applying."
    private const val KICKED = "You have been removed from the party."
}
