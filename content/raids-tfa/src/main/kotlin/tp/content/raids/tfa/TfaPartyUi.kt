package tp.content.raids.tfa

import tp.content.raids.Party
import tp.content.raids.PartyState
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The TFA party-forming interfaces (tfa.md TFA4, TFA5, TFA17; client-contract.md 1.5 and 2.2-2.4): the Grouping Board's
 * party list 977, the party details 976 and the lobby overlay 975. The client scripts draw everything; the server sends
 * the data rows (10094, 10053, 10058, 10060) and answers the pause buttons (`RESUME_PAUSEBUTTON`). State lives in
 * raids-core's [Party] (members, applicants, [Party.settings]) and in [TfaLobbyWorld]; plugin instances are shared by
 * every world, so there are no fields here.
 */
public class TfaPartyUi : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.TFA_GROUPING_BOARD, OP1) { TfaParties.openList(player) }
        onButton(Components.Tfa_Partylist.CONTENTS, PAUSE_BUTTON_OP) { TfaParties.listButton(this, it.slot) }
        onButton(Components.Tfa_Partydetails.PAUSEBUTTONS, PAUSE_BUTTON_OP) { TfaParties.detailsButton(this, it.slot) }
        for (row in 0 until TfaParties.MAX_LIST_ROWS) {
            onButton(Components.Tfa_Partylist._00 + row) { TfaParties.listRow(this, row) }
        }
        onLogin {
            TfaParties.login(this)
            timer(LOBBY_CHECK, 1, soft = true) { TfaParties.lobbyTick(this) }
        }
        onLogout { TfaParties.logout(this) }
    }

    private companion object {
        val LOBBY_CHECK = TimerId("tfa_lobby_check")
        const val OP1 = 1

        /** The op the engine gives a pause-button click that no dialogue script waits for (World `PAUSE_BUTTON_OP`). */
        const val PAUSE_BUTTON_OP = 1
    }
}

/** Lobby overlay and party state keys of one player (session only). */
private val VIEW = AttrKey<PartyView>("tfa_party_view")
private val LIST_ROWS = AttrKey<IntArray>("tfa_party_list_rows")
private val IN_LOBBY = AttrKey<Boolean>("tfa_in_lobby")
private val LOBBY_SIGNATURE = AttrKey<Int>("tfa_lobby_signature")

internal object TfaParties {
    /** Rows of the party list: the components `_00`..`_44` (client-contract 1.5, enum 6058). */
    const val MAX_LIST_ROWS = 45

    // ---- party list 977

    /** The Grouping Board: every forming TFA party, oldest first (client-contract 2.4). */
    fun openList(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TFA_PARTYLIST)
        val lobby = TfaLobbyWorld.of(p)
        val parties =
            Raids
                .of(p)
                .parties(TfaRaid.ID)
                .filter { it.state == PartyState.Forming }
                .take(MAX_LIST_ROWS)
        p[LIST_ROWS] = IntArray(parties.size) { parties[it].id.value }
        // 10094 with an empty row hides it and runs the layout tail, which shows the "No parties" text.
        if (parties.isEmpty()) p.clientScript(SCRIPT_LIST_ROW, 0, "")
        for ((row, party) in parties.withIndex()) {
            p.clientScript(SCRIPT_LIST_ROW, row, partyListRow(p, party, lobby))
            // OPEN-TFA-UI2: the "View Party:" op is set by script 10095; the client only sends it with the op flag.
            p.ifEvents(Components.Tfa_Partylist._00 + row, -1..-1, IfEvents.op(1))
        }
    }

    /** `RESUME_PAUSEBUTTON` on 977:1: 0 Refresh, 1 Make Party / My Party, 2 Friends/Clan filter (client-contract 2.4). */
    fun listButton(p: PlayerRef, sub: Int) {
        when {
            sub == LIST_REFRESH -> {
                openList(p)
            }

            sub == LIST_MYPARTY -> {
                myParty(p)
            }

            sub == LIST_FILTER -> {
                p.setVarbit(Varbits.TFA_LOBBY_FRIENDS_FILTER, 1 - p.varbit(Varbits.TFA_LOBBY_FRIENDS_FILTER))
                openList(p)
            }

            // OPEN-TFA-UI2: if the rows are pause buttons of 977:1 their subs follow the three fixed buttons.
            sub >= LIST_ROW_SUB_BASE -> {
                listRow(p, sub - LIST_ROW_SUB_BASE)
            }
        }
    }

    /** "View Party:" on list row [row]: only rows this player was sent are valid (hostile input). */
    fun listRow(p: PlayerRef, row: Int) {
        if (!p.isOpen(Interfaces.TFA_PARTYLIST)) return
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
        setController(p, null)
        makeParty(p)
    }

    private fun makeParty(p: PlayerRef) {
        val raids = Raids.of(p)
        val problem = TfaConfig.requirement(p)
        val def = raids.definition(TfaRaid.ID)
        if (problem != null || def == null) {
            problem?.let { p.message(it) }
            return
        }
        val party = raids.createParty(def, p.username)
        if (party == null) {
            p.message(ALREADY_IN_PARTY)
            return
        }
        // [I] the varbit `TFA_PREFERRED_MODE` is the player's remembered mode; it starts the new party's mode.
        party.settings[SETTING_MODE] = p.varbit(Varbits.TFA_PREFERRED_MODE).coerceIn(MODE_NORMAL, MODE_PRACTICE)
        TfaLobbyWorld.of(p).created[party.id.value] = p.tick
        syncMembers(p, party, emptyList())
        openDetails(p, party, TAB_MEMBERS)
    }

    private fun partyListRow(p: PlayerRef, party: Party, lobby: TfaLobbyWorld): String {
        val sb = StringBuilder()
        val members = party.members
        sb.append(party.leader)
        for (slot in 1 until PARTY_ROWS) sb.append(SEPARATOR).append(members.getOrNull(slot) ?: "")
        val size = party.settings[SETTING_SIZE] ?: DEFAULT_PREFERRED_SIZE
        val age = (p.tick - lobby.created.getOrPut(party.id.value) { p.tick }).coerceAtLeast(0)
        sb.append(SEPARATOR).append(members.size)
        sb.append(SEPARATOR).append(size)
        sb.append(SEPARATOR).append(party.settings[SETTING_COMPLETIONS] ?: 0)
        sb.append(SEPARATOR).append(modeName(party.settings[SETTING_MODE] ?: MODE_NORMAL))
        // Script 1574 reads the age as game ticks and adds the time since varc 59 (set when 977 loads).
        sb.append(SEPARATOR).append(age)
        return sb.toString()
    }

    // ---- party details 976

    fun openDetails(p: PlayerRef, party: Party, tab: Int) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TFA_PARTYDETAILS)
        val role = roleOf(p, party)
        val members = party.members.toList()
        val applicants = party.applicants.take(APPLICANT_ROWS)
        p[VIEW] = PartyView(party.id.value, tab, members, applicants)
        // 10053 numbers its rows with varc 178 and 10058 continues at row 8: always exactly eight member calls.
        for (row in 0 until PARTY_ROWS) p.clientScript(SCRIPT_MEMBER_ROW, role, members.getOrNull(row)?.let { statsRow(p, it) } ?: "")
        for (name in applicants) p.clientScript(SCRIPT_APPLICANT_ROW, statsRow(p, name))
        p.clientScript(
            SCRIPT_SETTINGS,
            role,
            party.settings[SETTING_COMPLETIONS] ?: 0,
            party.settings[SETTING_MODE] ?: MODE_NORMAL,
            party.settings[SETTING_SIZE] ?: DEFAULT_PREFERRED_SIZE,
            party.settings[SETTING_SPECTATE_OFF] ?: 0,
            tab,
        )
    }

    /** `RESUME_PAUSEBUTTON` on 976:1: sub-ids 0-47 (client-contract 2.3). Every id and the viewer's role are checked here. */
    fun detailsButton(p: PlayerRef, sub: Int) {
        val view = p[VIEW] ?: return
        val party = party(p, view.partyId)
        if (party == null) {
            p.message(PARTY_GONE)
            openList(p)
            return
        }
        when (sub) {
            SUB_BACK -> {
                openList(p)
            }

            SUB_REFRESH -> {
                openDetails(p, party, view.tab)
            }

            SUB_UNBLOCK -> {
                leaderEdit(p, party, view) { TfaLobbyWorld.of(p).blocked.remove(party.id.value) }
            }

            SUB_PREFERRED_SIZE -> {
                askNumber(p, party, SETTING_SIZE, SIZE_PROMPT, 1, party.raid.partySize.last)
            }

            SUB_PREFERRED_COMPLETIONS -> {
                askNumber(p, party, SETTING_COMPLETIONS, COMPLETIONS_PROMPT, 0, MAX_PREFERRED_COMPLETIONS)
            }

            SUB_PREFERRED_MODE -> {
                leaderEdit(p, party, view) { toggleMode(p, party) }
            }

            SUB_ACTION -> {
                action(p, party, view)
            }

            SUB_MEMBERS_TAB -> {
                openDetails(p, party, TAB_MEMBERS)
            }

            SUB_APPLICANTS_TAB -> {
                openDetails(p, party, TAB_APPLICANTS)
            }

            SUB_SPECTATE_TOGGLE -> {
                leaderEdit(p, party, view) { party.settings[SETTING_SPECTATE_OFF] = 1 - (party.settings[SETTING_SPECTATE_OFF] ?: 0) }
            }

            in SUB_KICK until SUB_KICK + PARTY_ROWS -> {
                kick(p, party, view, sub - SUB_KICK)
            }

            in SUB_UP until SUB_UP + PARTY_ROWS -> {
                move(p, party, view, sub - SUB_UP, -1)
            }

            in SUB_DOWN until SUB_DOWN + PARTY_ROWS -> {
                move(p, party, view, sub - SUB_DOWN, 1)
            }

            in SUB_ACCEPT until SUB_ACCEPT + APPLICANT_ROWS -> {
                accept(p, party, view, sub - SUB_ACCEPT)
            }

            in SUB_REJECT until SUB_REJECT + APPLICANT_ROWS -> {
                reject(p, party, view, sub - SUB_REJECT)
            }

            else -> {
                Unit
            }
        }
    }

    /** Runs [edit] when [p] leads [party] and it is still forming, then redraws the page. */
    private inline fun leaderEdit(p: PlayerRef, party: Party, view: PartyView, edit: () -> Unit) {
        if (!canEdit(p, party)) return
        edit()
        openDetails(p, party, view.tab)
    }

    private fun canEdit(p: PlayerRef, party: Party): Boolean {
        if (!isLeader(p, party)) return false
        if (party.state != PartyState.Forming) {
            p.message(PARTY_STARTED)
            return false
        }
        return true
    }

    private fun toggleMode(p: PlayerRef, party: Party) {
        val mode = if ((party.settings[SETTING_MODE] ?: MODE_NORMAL) == MODE_NORMAL) MODE_PRACTICE else MODE_NORMAL
        party.settings[SETTING_MODE] = mode
        p.setVarbit(Varbits.TFA_PREFERRED_MODE, mode)
    }

    /** Preferred party size or completions: a count dialog, re-checked when the answer arrives (it may come late). */
    private fun askNumber(p: PlayerRef, party: Party, key: String, prompt: String, min: Int, max: Int) {
        if (!canEdit(p, party)) return
        val id = party.id.value
        p.countDialog(prompt) { n ->
            val current = party(this, id)
            if (current != null && canEdit(this, current)) {
                if (n in min..max) {
                    current.settings[key] = n
                    openDetails(this, current, TAB_MEMBERS)
                } else {
                    message("Enter a number from $min to $max.")
                }
            }
        }
    }

    /** The ACTION button: Apply / Withdraw / Leave / Disband, by the viewer's role (script 10061 labels it). */
    private fun action(p: PlayerRef, party: Party, view: PartyView) {
        val lobby = TfaLobbyWorld.of(p)
        when (roleOf(p, party)) {
            ROLE_LEADER, ROLE_MEMBER -> {
                if (party.state != PartyState.Forming) {
                    p.message(PARTY_STARTED)
                    return
                }
                val all = party.members.toList()
                val leaving = if (isLeader(p, party)) all else listOf(p.username)
                for (name in leaving) party.leave(name)
                if (party.state == PartyState.Disbanded) dropParty(lobby, party)
                syncMembers(p, party, leaving)
                openList(p)
            }

            ROLE_APPLICANT -> {
                party.reject(p.username)
                openDetails(p, party, view.tab)
            }

            ROLE_NONE -> {
                if (party.apply(p.username)) p.message("You have applied to join ${party.leader}'s party.")
                openDetails(p, party, view.tab)
            }

            else -> {
                p.message(canApply(p, party) ?: PARTY_STARTED)
            }
        }
    }

    /** Kick (or, on the leader's own row 0, "Quit Party"); leader only. */
    private fun kick(p: PlayerRef, party: Party, view: PartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.members.getOrNull(row) ?: return
        if (!party.has(name) || view.members.first() != party.leader) return openDetails(p, party, view.tab)
        val lobby = TfaLobbyWorld.of(p)
        party.leave(name)
        if (row != 0 && BLOCK_ON_KICK) lobby.blocked.getOrPut(party.id.value) { HashSet() } += name.lowercase()
        if (party.state == PartyState.Disbanded) dropParty(lobby, party)
        syncMembers(p, party, listOf(name))
        if (party.state == PartyState.Disbanded || row == 0) openList(p) else openDetails(p, party, view.tab)
    }

    /** Move up / down: swaps two non-leader rows; the leader is slot 0 and stays there. */
    private fun move(p: PlayerRef, party: Party, view: PartyView, row: Int, step: Int) {
        if (!canEdit(p, party)) return
        val other = row + step
        val current = party.members
        val valid = row in 1 until current.size && other in 1 until current.size && view.members == current
        if (valid) party.reorder(row, other)
        openDetails(p, party, view.tab)
    }

    private fun accept(p: PlayerRef, party: Party, view: PartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.applicants.getOrNull(row)
        if (name != null && party.applicants.any { it == name }) {
            if (Raids.of(p).partyOf(name) != null) {
                party.reject(name)
                p.message("$name has joined another party.")
            } else if (party.accept(name)) {
                syncMembers(p, party, emptyList())
            } else {
                p.message(PARTY_FULL)
            }
        }
        openDetails(p, party, view.tab)
    }

    private fun reject(p: PlayerRef, party: Party, view: PartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.applicants.getOrNull(row)
        if (name != null && party.reject(name) && BLOCK_ON_REJECT) {
            TfaLobbyWorld.of(p).blocked.getOrPut(party.id.value) { HashSet() } += name.lowercase()
        }
        openDetails(p, party, view.tab)
    }

    // ---- roles and rows

    private fun isLeader(p: PlayerRef, party: Party): Boolean = party.leader.equals(p.username, ignoreCase = true)

    /** Viewer role for scripts 10053 / 10060: 2 leader, 1 member, 3 applicant, 4 cannot apply, 0 may apply (script 10061's switch). */
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
            partyOf(p) != null || Raids.of(p).partyOf(p.username) != null -> ALREADY_IN_PARTY
            TfaLobbyWorld.of(p).blocked[party.id.value]?.contains(p.username.lowercase()) == true -> BLOCKED
            else -> TfaConfig.requirement(p)
        }

    /** One member or applicant row: `name|combat|att|str|rng|mag|def|hp|prayer|completions|practice` (empty = "-"). */
    private fun statsRow(any: EntityRef, name: String): String {
        val lobby = TfaLobbyWorld.of(any)
        val online = any.onlinePlayer(name)
        val stats =
            if (online !=
                null
            ) {
                TfaLobbyWorld.snapshot(online).also { lobby.stats[name.lowercase()] = it }
            } else {
                lobby.stats[name.lowercase()]
            }
        val sb = StringBuilder(name)
        for (i in 0 until TfaLobbyWorld.FIELDS) {
            sb.append(SEPARATOR)
            val v = stats?.get(i) ?: TfaLobbyWorld.UNKNOWN
            if (v != TfaLobbyWorld.UNKNOWN) sb.append(v)
        }
        return sb.toString()
    }

    // ---- shared party state

    private fun party(any: EntityRef, id: Int): Party? = Raids.of(any).parties(TfaRaid.ID).firstOrNull { it.id.value == id }

    private fun partyOf(p: PlayerRef): Party? = Raids.of(p).partyOf(p.username)?.takeIf { it.raid.id == TfaRaid.ID }

    private fun dropParty(lobby: TfaLobbyWorld, party: Party) {
        lobby.blocked.remove(party.id.value)
        lobby.created.remove(party.id.value)
    }

    private fun modeName(mode: Int): String = if (mode == MODE_PRACTICE) "Practice" else "Normal"

    /** TFA4 run mode of [party] for [tp.content.raids.Raids.start] (the preferred mode of the leader). */
    fun modeOf(party: Party): String = if (party.settings[SETTING_MODE] == MODE_PRACTICE) "practice" else "normal"

    /** Writes `TFA_MYCONTROLLER` (the party id, -1 none) for every online member and for [removed], and redraws their lobby overlay. */
    private fun syncMembers(any: EntityRef, party: Party, removed: Collection<String>) {
        for (name in party.members) any.onlinePlayer(name)?.let { setController(it, party) }
        for (name in removed) any.onlinePlayer(name)?.let { if (!party.has(name)) setController(it, null) }
    }

    private fun setController(p: PlayerRef, party: Party?) {
        val id = party?.id?.value ?: NO_PARTY
        if (p.varp(Varps.TFA_MYCONTROLLER) != id) p.setVarp(Varps.TFA_MYCONTROLLER, id)
        if (p[IN_LOBBY] == true) syncLobby(p)
    }

    // ---- login, logout and the lobby overlay 975

    fun login(p: PlayerRef) {
        setController(p, partyOf(p))
    }

    /** Keeps the stats of a leaving player for the rows of a party he stays in (TFA5: members survive a logout). */
    fun logout(p: PlayerRef) {
        TfaLobbyWorld.of(p).stats[p.username.lowercase()] = TfaLobbyWorld.snapshot(p)
    }

    /**
     * Every tick for each player: one coordinate test and one attribute compare, no allocation. The overlay content is
     * rebuilt on entering the lobby square, after party changes (via [syncMembers]) and every [LOBBY_REFRESH_TICKS] ticks
     * (the run starting, a closed run returning the party to the lobby).
     */
    fun lobbyTick(p: PlayerRef) {
        val inside = inLobby(p.coord)
        if (inside != (p[IN_LOBBY] == true)) {
            p[IN_LOBBY] = inside
            p.areaQueue { if (inside) enterLobby(this) else leaveLobby(this) }
        } else if (inside && p.tick % LOBBY_REFRESH_TICKS == 0L) {
            syncLobby(p)
        }
    }

    private fun inLobby(c: CoordGrid): Boolean = c.level == 0 && c.x in LOBBY_MIN_X..LOBBY_MAX_X && c.z in LOBBY_MIN_Z..LOBBY_MAX_Z

    private fun enterLobby(p: PlayerRef) {
        // A wiped party arrives with the raid HUD (974) still on this overlay slot.
        if (p.isOpen(Interfaces.TFA_HUD)) TfaHud.close(p)
        p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TFA_LOBBY)
        p[LOBBY_SIGNATURE] = null
        syncLobby(p)
    }

    private fun leaveLobby(p: PlayerRef) {
        if (p.isOpen(Interfaces.TFA_LOBBY)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        if (p.varbit(Varbits.TFA_CLIENT_PARTYSTATUS) != STATUS_NONE) p.setVarbit(Varbits.TFA_CLIENT_PARTYSTATUS, STATUS_NONE)
        p[LOBBY_SIGNATURE] = null
    }

    /** Sends the overlay's party status (varbit) and member names (975:5) when they changed. */
    private fun syncLobby(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TFA_LOBBY)) return
        val party = partyOf(p)
        val status = statusOf(p, party)
        var signature = status
        if (party != null) {
            signature = signature * SIGNATURE_PRIME + party.id.value
            val members = party.members
            for (i in 0 until members.size) signature = signature * SIGNATURE_PRIME + members[i].hashCode()
        }
        if (p[LOBBY_SIGNATURE] == signature) return
        p[LOBBY_SIGNATURE] = signature
        if (p.varbit(Varbits.TFA_CLIENT_PARTYSTATUS) != status) p.setVarbit(Varbits.TFA_CLIENT_PARTYSTATUS, status)
        p.ifText(Components.Tfa_Lobby.NAMES, party?.members?.joinToString(LINE_BREAK) ?: "")
    }

    /** 0 no party, 1 party, 2 when the run has started and this member has not stepped inside yet (client-contract 2.2). */
    private fun statusOf(p: PlayerRef, party: Party?): Int {
        if (party == null) return STATUS_NONE
        val run = (party.state as? PartyState.Locked)?.run ?: return STATUS_PARTY
        val waiting =
            run.instance != null &&
                run.state is RunState.InRoom &&
                run.roomsTaken.size == 1 &&
                run.member(p.username)?.inside == false
        return if (waiting) STATUS_STEP_INSIDE else STATUS_PARTY
    }

    // ---- constants

    /** Server-run client script 10094 (`is`): party list row (client-contract 2.4). */
    private const val SCRIPT_LIST_ROW = 10094

    /** Server-run client script 10053 (`is`): member row, viewer role first (client-contract 2.3). */
    private const val SCRIPT_MEMBER_ROW = 10053

    /** Server-run client script 10058 (`s`): applicant row. */
    private const val SCRIPT_APPLICANT_ROW = 10058

    /** Server-run client script 10060 (`iiiiii`): role, preferred completions, mode, preferred size, spectate off, tab. */
    private const val SCRIPT_SETTINGS = 10060

    /** The row string separator read by script 632. */
    private const val SEPARATOR = '|'

    /** Chat-style line break the client's text components render. */
    private const val LINE_BREAK = "<br>"

    private const val LIST_REFRESH = 0
    private const val LIST_MYPARTY = 1
    private const val LIST_FILTER = 2

    /** OPEN-TFA-UI2: the sub-id of the first row button on 977:1 is not read from the scripts; rows are 977:15..59 components. */
    private const val LIST_ROW_SUB_BASE = 3

    // 976:1 pause-button sub-ids (client-contract 2.3)
    private const val SUB_BACK = 0
    private const val SUB_REFRESH = 1
    private const val SUB_UNBLOCK = 2
    private const val SUB_PREFERRED_SIZE = 3
    private const val SUB_PREFERRED_COMPLETIONS = 4
    private const val SUB_PREFERRED_MODE = 5
    private const val SUB_ACTION = 6
    private const val SUB_MEMBERS_TAB = 7
    private const val SUB_APPLICANTS_TAB = 8
    private const val SUB_SPECTATE_TOGGLE = 9
    private const val SUB_KICK = 10
    private const val SUB_UP = 18
    private const val SUB_DOWN = 26
    private const val SUB_ACCEPT = 34
    private const val SUB_REJECT = 41

    /** Member rows of 976 (TFA4: 1-8 players). */
    private const val PARTY_ROWS = 8

    /** Applicant rows: accept 34..40 and reject 41..47 leave seven (the leader holds a seat). */
    private const val APPLICANT_ROWS = 7

    private const val TAB_MEMBERS = 0
    private const val TAB_APPLICANTS = 1

    // viewer roles, the switch of script 10061
    private const val ROLE_NONE = 0
    private const val ROLE_MEMBER = 1
    private const val ROLE_LEADER = 2
    private const val ROLE_APPLICANT = 3
    private const val ROLE_BLOCKED = 4

    // Party.settings keys
    const val SETTING_SIZE = "tfa.preferredSize"
    const val SETTING_COMPLETIONS = "tfa.preferredCompletions"
    const val SETTING_MODE = "tfa.mode"

    /** 1 = external spectators disallowed (script 10060's flag 1 shows "Enable spectating"; TFA17). */
    const val SETTING_SPECTATE_OFF = "tfa.spectateOff"

    /** Enum 6051: 0 Normal, 1 Practice (client-contract 2.3). */
    private const val MODE_NORMAL = 0
    private const val MODE_PRACTICE = 1

    /** The "Preferred Party size: 8" the component shows before the leader sets one (client-contract 1.5). */
    private const val DEFAULT_PREFERRED_SIZE = 8

    /** OPEN-TFA-UI5: the largest preferred completions accepted; the list column fits five digits [I]. */
    private const val MAX_PREFERRED_COMPLETIONS = 99_999

    /** OPEN-TFA-UI3: the unblock button exists, so kicked members are blocked from re-applying until it is pressed [I]. */
    private const val BLOCK_ON_KICK = true

    /** OPEN-TFA-UI3: as [BLOCK_ON_KICK], for rejected applicants. */
    private const val BLOCK_ON_REJECT = true

    private const val NO_PARTY = -1

    // lobby overlay: map square (64, 86) is x 4096..4159, z 5504..5567 (tfa.md TFA8)
    private const val LOBBY_MIN_X = 4096
    private const val LOBBY_MAX_X = 4159
    private const val LOBBY_MIN_Z = 5504
    private const val LOBBY_MAX_Z = 5567

    /** Ticks between content refreshes of the lobby overlay (3 seconds); changes made through the board redraw at once. */
    private const val LOBBY_REFRESH_TICKS = 5L
    private const val SIGNATURE_PRIME = 31

    private const val STATUS_NONE = 0
    private const val STATUS_PARTY = 1
    private const val STATUS_STEP_INSIDE = 2

    private const val SIZE_PROMPT = "Enter the preferred party size (1-8):"
    private const val COMPLETIONS_PROMPT = "Enter the preferred completions (0 for none):"

    // OPEN-NX8: none of these texts is captured; plausible placeholders until seen in game.
    private const val PARTY_GONE = "That party no longer exists."
    private const val PARTY_STARTED = "That party has already entered the Archive."
    private const val PARTY_FULL = "That party is full."
    private const val ALREADY_IN_PARTY = "You are already in a party."
    private const val BLOCKED = "The party leader has blocked you from applying."
}

/** What a viewer of 976 was sent, so a click on row n acts on the player shown there even if the party changed since. */
internal class PartyView(
    val partyId: Int,
    val tab: Int,
    val members: List<String>,
    val applicants: List<String>,
)
