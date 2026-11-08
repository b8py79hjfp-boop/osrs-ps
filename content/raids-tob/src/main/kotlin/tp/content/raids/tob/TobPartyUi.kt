package tp.content.raids.tob

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
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The ToB notice board at Ver Sinhaza (tob.md TB1; client-contract.md 1.5 and 2.2-2.4): the party list 364, the party
 * details 50 and the lobby text, which rides on the HUD 28 (`PARTYSTATUS`). The client scripts draw everything; the
 * server sends the data rows (2340, 2317, 2321, 2323) and answers the pause buttons (`RESUME_PAUSEBUTTON`) and the
 * arrow buttons. State lives in raids-core's [Party] and in [TobLobbyWorld]; plugin instances are shared by every
 * world, so there are no fields here. The room HUD is [TobHud].
 */
public class TobPartyUi : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.TOB_SURFACE_NOTICE_BOARD, OP1) { TobBoard.openList(player) }
        onButton(Components.Tob_Partylist.CONTENTS, PAUSE_BUTTON_OP) { TobBoard.listButton(this, it.slot) }
        onButton(Components.Tob_Partydetails.UNIVERSE, PAUSE_BUTTON_OP) { TobBoard.detailsButton(this, it.slot) }
        for (row in 0 until TobBoard.MAX_LIST_ROWS) {
            onButton(Components.Tob_Partylist._00 + row, OP1) { TobBoard.listRow(this, row) }
        }
        // The arrows are P1_UP, P1_DOWN, P2_UP, ... (client-contract 1.5): two components per member row.
        for (row in 0 until TobBoard.MEMBER_ROWS) {
            onButton(Components.Tob_Partydetails.P1_UP + ARROW_STRIDE * row, OP1) { TobBoard.moveButton(this, row, -1) }
            onButton(Components.Tob_Partydetails.P1_DOWN + ARROW_STRIDE * row, OP1) { TobBoard.moveButton(this, row, 1) }
        }
        onLogin {
            TobBoard.login(this)
            timer(LOBBY_CHECK, 1, soft = true) { TobBoard.lobbyTick(this) }
        }
        onLogout {
            TobBoard.logout(this)
            TobHud.logout(this)
        }
        onWorldTick { TobHud.tick(this) }
    }

    private companion object {
        val LOBBY_CHECK = TimerId("tob_lobby_check")
        const val OP1 = 1

        /** The op the engine gives a pause-button click that no dialogue script waits for (World `PAUSE_BUTTON_OP`). */
        const val PAUSE_BUTTON_OP = 1

        /** Up and Down of one row are adjacent components. */
        const val ARROW_STRIDE = 2
    }
}

/** What a viewer of 50 was sent, so a click on row n acts on the player shown there even if the party changed since. */
internal class TobPartyView(
    val partyId: Int,
    val members: List<String>,
    val applicants: List<String>,
)

/**
 * The lobby's world-scoped state beside raids-core's parties: when each party was made (the list's age column), who a
 * leader has blocked from applying, and the last stats seen of each player (rows of offline members).
 */
internal class TobLobbyWorld {
    /** Party id -> tick it was made. */
    val created = HashMap<Int, Long>()

    /** Party id -> lower-case names blocked from applying to it (OPEN-TB-UI3). */
    val blocked = HashMap<Int, MutableSet<String>>()

    /** Lower-case name -> [snapshot]. */
    val stats = HashMap<String, IntArray>()

    companion object {
        private val KEY = AttrKey<TobLobbyWorld>("tob_lobby_world")

        fun of(any: EntityRef): TobLobbyWorld = any.worldAttr(KEY) ?: TobLobbyWorld().also { any.setWorldAttr(KEY, it) }

        /** Stats fields: combat level, the seven combat stats of the row string, then the Normal / Entry / Hard completions. */
        private const val FIELDS = 11
        private const val COMPLETIONS_AT = 8

        /** The combat stats in the order of the row string (client-contract 2.3: att, str, rng, mag, def, hp, prayer). */
        private val ROW_STATS =
            intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.RANGED, Stats.MAGIC, Stats.DEFENCE, Stats.HITPOINTS, Stats.PRAYER)

        /** Completion varps by mode id (enum 3852 order Normal, Entry, Hard; client-contract 1.1). */
        private val COMPLETION_VARPS =
            intArrayOf(
                Varps.TOTAL_COMPLETED_THEATREOFBLOOD,
                Varps.TOTAL_COMPLETED_THEATREOFBLOOD_STORY,
                Varps.TOTAL_COMPLETED_THEATREOFBLOOD_HARD,
            )

        fun snapshot(p: PlayerRef): IntArray {
            val out = IntArray(FIELDS)
            out[0] = p.combatLevel
            for (i in ROW_STATS.indices) out[1 + i] = p.baseLevel(ROW_STATS[i])
            for (m in COMPLETION_VARPS.indices) out[COMPLETIONS_AT + m] = p.varp(COMPLETION_VARPS[m])
            return out
        }

        /** The row fields after the name: combat, the seven stats, and the completions of [mode]. */
        fun field(stats: IntArray?, index: Int, mode: Int): Int? {
            stats ?: return null
            return if (index < COMPLETIONS_AT) stats[index] else stats[COMPLETIONS_AT + mode.coerceIn(0, COMPLETION_VARPS.size - 1)]
        }

        /** Row fields in the string after the name (client-contract 2.3: combat, att, str, rng, mag, def, hp, prayer, runs). */
        const val ROW_FIELDS = 9
    }
}

/** Board state keys of one player (session only). */
private val VIEW = AttrKey<TobPartyView>("tob_party_view")
private val LIST_ROWS = AttrKey<IntArray>("tob_party_list_rows")
private val IN_LOBBY = AttrKey<Boolean>("tob_in_lobby")

internal object TobBoard {
    /** Rows of the party list: the components `_00`..`_44` (client-contract 1.5, enum 1937). */
    const val MAX_LIST_ROWS = 45

    /** Member rows of the details screen (TB1: 1-5 players). */
    const val MEMBER_ROWS = TobRaid.MAX_PARTY

    // ---- party list 364

    /** The notice board: every forming ToB party, oldest first (client-contract 2.4). */
    fun openList(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOB_PARTYLIST)
        val lobby = TobLobbyWorld.of(p)
        val parties =
            Raids
                .of(p)
                .parties(TobRaid.ID)
                .filter { it.state == PartyState.Forming }
                .take(MAX_LIST_ROWS)
        p[LIST_ROWS] = IntArray(parties.size) { parties[it].id.value }
        // Script 2340 finalises the layout on row 44: all 45 calls, in order, empty for vacant rows (client-contract 8.5).
        for (row in 0 until MAX_LIST_ROWS) {
            val party = parties.getOrNull(row)
            p.clientScript(SCRIPT_LIST_ROW, row, if (party == null) "" else partyListRow(p, party, lobby))
            // OPEN-TB-UI4: the "View party:" op is set by script 2340; the client only sends it with the op flag.
            if (party != null) p.ifEvents(Components.Tob_Partylist._00 + row, -1..-1, IfEvents.op(1))
        }
        if (p.saved(PREFERRED_MODE_KEY) == null) askPreferredMode(p)
    }

    /** `RESUME_PAUSEBUTTON` on 364:3: 0 Refresh, 1 Make party / My party, 2 Friends/Clan filter (client-contract 2.4). */
    fun listButton(p: PlayerRef, sub: Int) {
        if (!p.isOpen(Interfaces.TOB_PARTYLIST)) return
        when (sub) {
            LIST_REFRESH -> {
                openList(p)
            }

            LIST_MYPARTY -> {
                myParty(p)
            }

            LIST_FILTER -> {
                p.setVarbit(Varbits.TOB_LOBBY_FRIENDS_FILTER, 1 - p.varbit(Varbits.TOB_LOBBY_FRIENDS_FILTER))
                openList(p)
            }

            else -> {
                Unit
            }
        }
    }

    /** "View party:" on list row [row]: only rows this player was sent are valid (hostile input). */
    fun listRow(p: PlayerRef, row: Int) {
        if (!p.isOpen(Interfaces.TOB_PARTYLIST)) return
        val id = p[LIST_ROWS]?.getOrNull(row) ?: return
        val party = party(p, id)
        if (party == null) {
            p.message(PARTY_GONE)
            openList(p)
        } else {
            openDetails(p, party)
        }
    }

    private fun myParty(p: PlayerRef) {
        val mine = partyOf(p)
        if (mine != null) {
            openDetails(p, mine)
            return
        }
        makeParty(p)
    }

    private fun makeParty(p: PlayerRef) {
        val raids = Raids.of(p)
        val problem = TobConfig.requirement(p)
        val def = raids.definition(TobRaid.ID)
        if (problem != null || def == null) {
            problem?.let { p.message(it) }
            return
        }
        val party = raids.createParty(def, p.username)
        if (party == null) {
            p.message(ALREADY_IN_PARTY)
            return
        }
        var mode = preferredMode(p)
        if (mode == TobParties.MODE_HARD && TobConfig.hardRequirement(p) != null) mode = TobParties.MODE_NORMAL
        party.settings[TobParties.MODE_SETTING] = mode
        TobLobbyWorld.of(p).created[party.id.value] = p.tick
        syncMembers(p, party, emptyList())
        openDetails(p, party)
    }

    /** TB1: the first time a player uses the board a prompt asks which mode they prefer (1 Mar 2023). */
    private fun askPreferredMode(p: PlayerRef) {
        p.choiceDialog(MODE_PROMPT, MODE_NAMES) { pick ->
            val mode = pick - 1
            if (mode in MODE_NAMES.indices) setSaved(PREFERRED_MODE_KEY, mode.toLong())
        }
    }

    private fun preferredMode(p: PlayerRef): Int = (p.saved(PREFERRED_MODE_KEY) ?: 0L).toInt().coerceIn(0, MODE_NAMES.size - 1)

    private fun partyListRow(p: PlayerRef, party: Party, lobby: TobLobbyWorld): String {
        val sb = StringBuilder()
        val members = party.members
        sb.append(party.leader)
        for (slot in 1 until MEMBER_ROWS) sb.append(SEPARATOR).append(members.getOrNull(slot) ?: "")
        val age = (p.tick - lobby.created.getOrPut(party.id.value) { p.tick }).coerceAtLeast(0)
        sb.append(SEPARATOR).append(members.size)
        sb.append(SEPARATOR).append(party.settings[SETTING_SIZE] ?: 0)
        sb.append(SEPARATOR).append(party.settings[SETTING_LEVEL] ?: 0)
        sb.append(SEPARATOR).append(modeName(party.settings[TobParties.MODE_SETTING] ?: TobParties.MODE_NORMAL))
        // Script 1574 reads the age as game ticks and adds the time since varc 59 (set when 364 loads).
        sb.append(SEPARATOR).append(age)
        return sb.toString()
    }

    // ---- party details 50

    fun openDetails(p: PlayerRef, party: Party) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOB_PARTYDETAILS)
        val role = roleOf(p, party)
        val members = party.members.toList()
        val applicants = party.applicants.take(APPLICANT_ROWS)
        p[VIEW] = TobPartyView(party.id.value, members, applicants)
        val mode = party.settings[TobParties.MODE_SETTING] ?: TobParties.MODE_NORMAL
        // Send order (client-contract 8.5): 2317 x5 (rows 0..4, empty for vacant), 2321 per applicant, then 2323.
        for (row in 0 until MEMBER_ROWS) {
            p.clientScript(SCRIPT_MEMBER_ROW, role, members.getOrNull(row)?.let { statsRow(p, it, mode) } ?: "")
        }
        for (name in applicants) p.clientScript(SCRIPT_APPLICANT_ROW, statsRow(p, name, mode))
        p.clientScript(SCRIPT_SETTINGS, role, party.settings[SETTING_SIZE] ?: 0, party.settings[SETTING_LEVEL] ?: 0, mode)
    }

    /** `RESUME_PAUSEBUTTON` on 50:0: sub-ids 0-31 (client-contract 2.3). Every id and the viewer's role are checked here. */
    fun detailsButton(p: PlayerRef, sub: Int) {
        val (view, party) = viewed(p) ?: return
        when (sub) {
            SUB_BACK -> {
                openList(p)
            }

            SUB_REFRESH -> {
                openDetails(p, party)
            }

            SUB_UNBLOCK -> {
                leaderEdit(p, party) { TobLobbyWorld.of(p).blocked.remove(party.id.value) }
            }

            SUB_SIZE -> {
                askNumber(p, party, SETTING_SIZE, SIZE_PROMPT, 0, party.raid.partySize.last)
            }

            SUB_LEVEL -> {
                askNumber(p, party, SETTING_LEVEL, LEVEL_PROMPT, 0, TobConfig.boardMaxPreferredLevel)
            }

            SUB_MODE -> {
                askMode(p, party)
            }

            SUB_ACTION -> {
                action(p, party)
            }

            in SUB_KICK until SUB_KICK + MEMBER_ROWS -> {
                kick(p, party, view, sub - SUB_KICK)
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

    /** The open details screen's view and its party; null (after the right message) when either is gone. */
    private fun viewed(p: PlayerRef): Pair<TobPartyView, Party>? {
        if (!p.isOpen(Interfaces.TOB_PARTYDETAILS)) return null
        val view = p[VIEW] ?: return null
        val party = party(p, view.partyId)
        if (party == null) {
            p.message(PARTY_GONE)
            openList(p)
            return null
        }
        return view to party
    }

    /** IF_BUTTON1 on a Move up / Move down arrow of member row [row]; re-validated against what was sent. */
    fun moveButton(p: PlayerRef, row: Int, step: Int) {
        val (view, party) = viewed(p) ?: return
        if (!canEdit(p, party)) return
        val other = row + step
        val current = party.members
        val first = if (TobConfig.boardReorderMovesLeader) 0 else 1
        val valid = row in first until current.size && other in first until current.size && view.members == current
        if (valid) party.reorder(row, other)
        openDetails(p, party)
    }

    /** Runs [edit] when [p] leads [party] and it is still forming, then redraws the page. */
    private inline fun leaderEdit(p: PlayerRef, party: Party, edit: () -> Unit) {
        if (!canEdit(p, party)) return
        edit()
        openDetails(p, party)
    }

    private fun canEdit(p: PlayerRef, party: Party): Boolean {
        if (!isLeader(p, party)) return false
        if (party.state != PartyState.Forming) {
            p.message(PARTY_STARTED)
            return false
        }
        return true
    }

    /** Preferred size or level: a count dialog, re-checked when the answer arrives (it may come late). */
    private fun askNumber(p: PlayerRef, party: Party, key: String, prompt: String, min: Int, max: Int) {
        if (!canEdit(p, party)) return
        val id = party.id.value
        p.countDialog(prompt) { n ->
            val current = party(this, id)
            if (current != null && canEdit(this, current)) {
                if (n in min..max) {
                    current.settings[key] = n
                    openDetails(this, current)
                } else {
                    message("Enter a number from $min to $max.")
                }
            }
        }
    }

    /** The MODE button (leader only): Normal / Entry / Hard through the chat menu. */
    private fun askMode(p: PlayerRef, party: Party) {
        if (!canEdit(p, party)) return
        val id = party.id.value
        p.choiceDialog(MODE_PROMPT, MODE_NAMES) { pick ->
            val current = party(this, id)
            if (current != null && canEdit(this, current) && pick - 1 in MODE_NAMES.indices) {
                setMode(this, current, pick - 1)
                openDetails(this, current)
            }
        }
    }

    /** TB4: Hard needs a Normal completion from every participant; Entry and Normal need nothing. */
    private fun setMode(p: PlayerRef, party: Party, mode: Int) {
        if (mode == TobParties.MODE_HARD) {
            for (name in party.members) {
                val member = p.onlinePlayer(name) ?: continue
                TobConfig.hardRequirement(member)?.let {
                    p.message(if (member == p) it else "${member.username} has not completed the Theatre of Blood in Normal Mode.")
                    return
                }
            }
        }
        party.settings[TobParties.MODE_SETTING] = mode
    }

    /** The ACTION button by role: Disband (leader), Leave (member), Withdraw (applicant), Apply (script 2323 labels it). */
    private fun action(p: PlayerRef, party: Party) {
        val lobby = TobLobbyWorld.of(p)
        when (roleOf(p, party)) {
            ROLE_LEADER, ROLE_MEMBER -> {
                if (party.state != PartyState.Forming) {
                    p.message(PARTY_STARTED)
                    return
                }
                val leaving = if (isLeader(p, party)) party.members.toList() else listOf(p.username)
                for (name in leaving) party.leave(name)
                if (party.state == PartyState.Disbanded) dropParty(lobby, party)
                syncMembers(p, party, leaving)
                openList(p)
            }

            ROLE_APPLICANT -> {
                party.reject(p.username)
                openDetails(p, party)
            }

            ROLE_NONE -> {
                if (party.apply(p.username)) p.message("You have applied to join ${party.leader}'s party.")
                openDetails(p, party)
            }

            else -> {
                p.message(canApply(p, party) ?: PARTY_STARTED)
            }
        }
    }

    /** Kick, or on the leader's own row 0 "Quit party" (the next member then leads); leader only. */
    private fun kick(p: PlayerRef, party: Party, view: TobPartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.members.getOrNull(row) ?: return
        if (!party.has(name) || !view.members.first().equals(party.leader, ignoreCase = true)) return openDetails(p, party)
        val lobby = TobLobbyWorld.of(p)
        party.leave(name)
        if (row != 0 && TobConfig.boardKickBlocksReapply) lobby.blocked.getOrPut(party.id.value) { HashSet() } += name.lowercase()
        if (party.state == PartyState.Disbanded) dropParty(lobby, party)
        syncMembers(p, party, listOf(name))
        if (party.state == PartyState.Disbanded || row == 0) openList(p) else openDetails(p, party)
    }

    private fun accept(p: PlayerRef, party: Party, view: TobPartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.applicants.getOrNull(row)
        if (name != null && party.applicants.any { it == name }) {
            val hard = party.settings[TobParties.MODE_SETTING] == TobParties.MODE_HARD
            val problem = if (hard) p.onlinePlayer(name)?.let { TobConfig.hardRequirement(it) } else null
            if (Raids.of(p).partyOf(name) != null) {
                party.reject(name)
                p.message("$name has joined another party.")
            } else if (problem != null) {
                p.message("$name has not completed the Theatre of Blood in Normal Mode.")
            } else if (party.accept(name)) {
                syncMembers(p, party, emptyList())
            } else {
                p.message(PARTY_FULL)
            }
        }
        openDetails(p, party)
    }

    private fun reject(p: PlayerRef, party: Party, view: TobPartyView, row: Int) {
        if (!canEdit(p, party)) return
        val name = view.applicants.getOrNull(row)
        if (name != null && party.reject(name) && TobConfig.boardRejectBlocksReapply) {
            TobLobbyWorld.of(p).blocked.getOrPut(party.id.value) { HashSet() } += name.lowercase()
        }
        openDetails(p, party)
    }

    // ---- roles and rows

    private fun isLeader(p: PlayerRef, party: Party): Boolean = party.leader.equals(p.username, ignoreCase = true)

    /** Viewer role for scripts 2317 / 2323: 2 leader, 1 member, 3 applicant, 4 cannot apply, 0 may apply (client-contract 2.3). */
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
            TobLobbyWorld.of(p).blocked[party.id.value]?.contains(p.username.lowercase()) == true -> BLOCKED
            party.settings[TobParties.MODE_SETTING] == TobParties.MODE_HARD -> TobConfig.hardRequirement(p)
            else -> TobConfig.requirement(p)
        }

    /** One member or applicant row: `name|combat|att|str|rng|mag|def|hp|prayer|runs` (empty = "-"). */
    private fun statsRow(any: EntityRef, name: String, mode: Int): String {
        val lobby = TobLobbyWorld.of(any)
        val online = any.onlinePlayer(name)
        val stats =
            if (online != null) {
                TobLobbyWorld.snapshot(online).also { lobby.stats[name.lowercase()] = it }
            } else {
                lobby.stats[name.lowercase()]
            }
        val sb = StringBuilder(name)
        for (i in 0 until TobLobbyWorld.ROW_FIELDS) {
            sb.append(SEPARATOR)
            TobLobbyWorld.field(stats, i, mode)?.let { sb.append(it) }
        }
        return sb.toString()
    }

    // ---- shared party state

    private fun party(any: EntityRef, id: Int): Party? = Raids.of(any).parties(TobRaid.ID).firstOrNull { it.id.value == id }

    fun partyOf(p: PlayerRef): Party? = Raids.of(p).partyOf(p.username)?.takeIf { it.raid.id == TobRaid.ID }

    private fun dropParty(lobby: TobLobbyWorld, party: Party) {
        lobby.blocked.remove(party.id.value)
        lobby.created.remove(party.id.value)
    }

    private fun modeName(mode: Int): String = MODE_NAMES.getOrElse(mode) { MODE_NAMES.first() }

    /** Writes `TOB_MYCONTROLLER` (the party id, -1 none) for every online member and for [removed], and redraws their lobby text. */
    private fun syncMembers(any: EntityRef, party: Party, removed: Collection<String>) {
        for (name in party.members) any.onlinePlayer(name)?.let { setController(it, party) }
        for (name in removed) any.onlinePlayer(name)?.let { if (!party.has(name)) setController(it, null) }
    }

    private fun setController(p: PlayerRef, party: Party?) {
        val id = party?.id?.value ?: NO_PARTY
        if (p.varp(Varps.TOB_MYCONTROLLER) != id) p.setVarp(Varps.TOB_MYCONTROLLER, id)
        if (p[IN_LOBBY] == true) syncLobby(p)
    }

    // ---- login, logout and the lobby text on the HUD

    fun login(p: PlayerRef) {
        setController(p, partyOf(p))
    }

    /** Keeps the stats of a leaving player for the rows of a party he stays in. */
    fun logout(p: PlayerRef) {
        TobLobbyWorld.of(p).stats[p.username.lowercase()] = TobLobbyWorld.snapshot(p)
    }

    /**
     * Every tick for each player: a coordinate test and two attribute compares, no allocation. The status is rebuilt on
     * entering the lobby box, after party changes (via [syncMembers]) and every [LOBBY_REFRESH_TICKS] ticks (the run
     * starting, a closed run returning the party to the lobby).
     */
    fun lobbyTick(p: PlayerRef) {
        // Instances sit at x >= 6400 (client-contract 8.4): a raid HUD outside them belongs to a player who has left.
        if (TobHud.active(p) && p.coord.x < INSTANCE_MIN_X) TobHud.leave(p)
        val inside = inLobby(p.coord)
        if (inside != (p[IN_LOBBY] == true)) {
            p[IN_LOBBY] = inside
            p.areaQueue { if (inside) enterLobby(this) else leaveLobby(this) }
        } else if (inside && p.tick % LOBBY_REFRESH_TICKS == 0L) {
            syncLobby(p)
        }
    }

    /** The box script 2297 tests for the lobby text (client-contract 2.1): (3640, 3202, 0) .. (3684, 3236, 1). */
    fun inLobby(c: CoordGrid): Boolean =
        c.level in LOBBY_MIN_LEVEL..LOBBY_MAX_LEVEL && c.x in LOBBY_MIN_X..LOBBY_MAX_X && c.z in LOBBY_MIN_Z..LOBBY_MAX_Z

    private fun enterLobby(p: PlayerRef) {
        if (TobHud.active(p)) TobHud.leave(p)
        if (!p.isOpen(Interfaces.TOB_HUD)) p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TOB_HUD)
        syncLobby(p)
    }

    private fun leaveLobby(p: PlayerRef) {
        // The raid HUD (or its lobby-time status) now belongs to TobHud.
        if (TobHud.active(p)) return
        if (p.isOpen(Interfaces.TOB_HUD)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        if (p.varbit(Varbits.TOB_CLIENT_PARTYSTATUS) != STATUS_NONE) p.setVarbit(Varbits.TOB_CLIENT_PARTYSTATUS, STATUS_NONE)
    }

    /** Sends the lobby text's `PARTYSTATUS` (client-contract 2.2) when it changed. */
    fun syncLobby(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TOB_HUD) || TobHud.active(p)) return
        val status = statusOf(p, partyOf(p))
        if (p.varbit(Varbits.TOB_CLIENT_PARTYSTATUS) != status) p.setVarbit(Varbits.TOB_CLIENT_PARTYSTATUS, status)
    }

    /** 0 no party, 1 party, 2 when the run has started and this member has not stepped inside (client-contract 2.2). */
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

    /** Server-run client script 2340 (`is`): party list row (client-contract 2.4). */
    private const val SCRIPT_LIST_ROW = 2340

    /** Server-run client script 2317 (`is`): member row, viewer role first (client-contract 2.3). */
    private const val SCRIPT_MEMBER_ROW = 2317

    /** Server-run client script 2321 (`s`): applicant row. */
    private const val SCRIPT_APPLICANT_ROW = 2321

    /** Server-run client script 2323 (`iiii`): role, preferred size, preferred level, mode. */
    private const val SCRIPT_SETTINGS = 2323

    /** The row string separator read by script 632. */
    private const val SEPARATOR = '|'

    private const val LIST_REFRESH = 0
    private const val LIST_MYPARTY = 1
    private const val LIST_FILTER = 2

    // 50:0 pause-button sub-ids (client-contract 2.3)
    private const val SUB_BACK = 0
    private const val SUB_REFRESH = 1
    private const val SUB_UNBLOCK = 2
    private const val SUB_SIZE = 3
    private const val SUB_LEVEL = 4
    private const val SUB_ACTION = 5
    private const val SUB_MODE = 6
    private const val SUB_KICK = 7
    private const val SUB_ACCEPT = 12
    private const val SUB_REJECT = 22

    /** Applicant rows: accept 12..21 and reject 22..31 (client-contract 2.3: at most 10 applicants). */
    private const val APPLICANT_ROWS = 10

    // viewer roles, script 2323's switch (client-contract 2.3)
    private const val ROLE_NONE = 0
    private const val ROLE_MEMBER = 1
    private const val ROLE_LEADER = 2
    private const val ROLE_APPLICANT = 3
    private const val ROLE_BLOCKED = 4

    // Party.settings keys (the mode key is TobParties.MODE_SETTING)
    private const val SETTING_SIZE = "tob.preferredSize"
    private const val SETTING_LEVEL = "tob.preferredLevel"

    /** Enum 3852 (client-contract 6): mode id -> name, in the id order of [TobParties] (0 Normal, 1 Entry, 2 Hard). */
    private val MODE_NAMES = listOf("Normal", "Entry", "Hard")

    /** The player's remembered mode (TB1 first-use prompt), saved value. */
    private const val PREFERRED_MODE_KEY = "raids.tob.preferredMode"

    private const val NO_PARTY = -1

    /** Instances start at map square x 100 = tile x 6400 (client-contract 2.1 / 8.4, script 1935). */
    private const val INSTANCE_MIN_X = 6400

    // lobby box of script 2297 (client-contract 2.1)
    private const val LOBBY_MIN_X = 3640
    private const val LOBBY_MAX_X = 3684
    private const val LOBBY_MIN_Z = 3202
    private const val LOBBY_MAX_Z = 3236
    private const val LOBBY_MIN_LEVEL = 0
    private const val LOBBY_MAX_LEVEL = 1

    /** Ticks between status refreshes in the lobby (3 seconds); changes made through the board apply at once. */
    private const val LOBBY_REFRESH_TICKS = 5L

    private const val STATUS_NONE = 0
    private const val STATUS_PARTY = 1
    private const val STATUS_STEP_INSIDE = 2

    // OPEN-TB-UI1 (tob.md OPEN-TB1, lobby-rewards OPEN-LR3): none of these texts is captured; placeholders until seen in game.
    private const val MODE_PROMPT = "Which mode do you prefer?"
    private const val SIZE_PROMPT = "Enter the preferred party size:"
    private const val LEVEL_PROMPT = "Enter the preferred level (0 for none):"
    private const val PARTY_GONE = "That party no longer exists."
    private const val PARTY_STARTED = "That party has already entered the Theatre."
    private const val PARTY_FULL = "That party is full."
    private const val ALREADY_IN_PARTY = "You are already in a party."
    private const val BLOCKED = "The party leader has blocked you from applying."
}
