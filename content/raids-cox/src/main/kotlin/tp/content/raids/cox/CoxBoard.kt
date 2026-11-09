package tp.content.raids.cox

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

/** What a viewer of the party details 507 was sent, so a click acts on the party shown there even if things changed since. */
internal class CoxPartyView(val partyId: Int)

/**
 * The lobby's world-scoped state beside raids-core's parties: when each party was made (the list's age column), which
 * parties the leader advertised on the board, and the last stats seen of each player (rows of offline members).
 */
internal class CoxLobbyWorld {
    /** Party id -> tick it was made. */
    val created = HashMap<Int, Long>()

    /** Ids of the parties their leader listed on the recruiting board. */
    val listed = HashSet<Int>()

    /** Lower-case name -> [snapshot]. */
    val stats = HashMap<String, IntArray>()

    companion object {
        private val KEY = AttrKey<CoxLobbyWorld>("cox_lobby_world")

        fun of(any: EntityRef): CoxLobbyWorld = any.worldAttr(KEY) ?: CoxLobbyWorld().also { any.setWorldAttr(KEY, it) }

        /** The 24 skills in the order of enum 681 (client-contract 2.5: the "Stats" popup of a member row). */
        val SKILL_ORDER: IntArray =
            intArrayOf(
                Stats.ATTACK,
                Stats.STRENGTH,
                Stats.RANGED,
                Stats.MAGIC,
                Stats.DEFENCE,
                Stats.HITPOINTS,
                Stats.PRAYER,
                Stats.AGILITY,
                Stats.HERBLORE,
                Stats.THIEVING,
                Stats.CRAFTING,
                Stats.RUNECRAFT,
                Stats.MINING,
                Stats.SMITHING,
                Stats.FISHING,
                Stats.COOKING,
                Stats.FIREMAKING,
                Stats.WOODCUTTING,
                Stats.FLETCHING,
                Stats.SLAYER,
                Stats.FARMING,
                Stats.CONSTRUCTION,
                Stats.HUNTER,
                Stats.SAILING,
            )

        /** Snapshot layout: combat level, skill total, completions, then the levels in [SKILL_ORDER]. */
        private const val COMBAT = 0
        private const val TOTAL = 1
        private const val KILLS = 2
        private const val LEVELS = 3

        /** The highest skill total the board can ask for: 24 skills at 99 (client-contract 2.5; enum 681 has 24 entries). */
        val MAX_SKILL_TOTAL: Int = SKILL_ORDER.size * Stats.MAX_LEVEL

        fun snapshot(p: PlayerRef): IntArray {
            val out = IntArray(LEVELS + SKILL_ORDER.size)
            var total = 0
            for (i in SKILL_ORDER.indices) {
                out[LEVELS + i] = p.baseLevel(SKILL_ORDER[i])
                total += out[LEVELS + i]
            }
            out[COMBAT] = p.combatLevel
            out[TOTAL] = total
            // OPEN-CXUI5: the "kill total" column is the normal-mode completion count (the 8 Feb 2018 post predates Challenge Mode).
            out[KILLS] = p.varp(Varps.TOTAL_COMPLETED_XERICCHAMBERS)
            return out
        }

        fun combat(stats: IntArray?): Int = stats?.get(COMBAT) ?: 0

        fun total(stats: IntArray?): Int = stats?.get(TOTAL) ?: 0

        /** `name|combat|skilltotal|killtotal|level x 24` (client-contract 2.5, script 1517); vacant stats show as 0. */
        fun memberRow(any: EntityRef, name: String): String {
            val lobby = of(any)
            val online = any.onlinePlayer(name)
            val stats = if (online != null) snapshot(online).also { lobby.stats[name.lowercase()] = it } else lobby.stats[name.lowercase()]
            val sb = StringBuilder(name)
            for (i in 0 until LEVELS + SKILL_ORDER.size) sb.append('|').append(stats?.get(i) ?: 0)
            return sb.toString()
        }

        /** `name|combat|skilltotal` (client-contract 2.5, script 1548). */
        fun tabRow(any: EntityRef, name: String): String {
            val lobby = of(any)
            val online = any.onlinePlayer(name)
            val stats = if (online != null) snapshot(online).also { lobby.stats[name.lowercase()] = it } else lobby.stats[name.lowercase()]
            return "$name|${combat(stats)}|${total(stats)}"
        }
    }
}

/**
 * The Chambers' recruiting board (cox.md CX1-CX3; client-contract 2.1, 2.4, 2.5): the party list 499 and the party
 * details 507. The client scripts draw everything; the server sends the rows (1566, 1517, 1524) and answers the pause
 * buttons. State lives in raids-core's [Party], its settings and [CoxLobbyWorld]. The Raiding Party tab is [CoxPartyTab].
 *
 * OPEN-CX1: the client has no applicant list (client-contract 2.5), so "Join" joins directly and a kick runs through the
 * friends-chat packet; the server does not mirror a friends-chat channel into the party (a hook the main session owns).
 */
internal object CoxBoard {
    /** Party settings (read by [CoxFlow.enterChambers] / raids-core's `ScalingContext.settings`). */
    const val SETTING_SIZE = "cox_pref_size"
    const val SETTING_COMBAT = "cox_min_combat"
    const val SETTING_SKILL = "cox_min_skill"

    /** The leader's chosen scaled party size (varbit 9539); 0 = not set. */
    const val SETTING_SCALING = "cox_scaling"

    /** The map layout pool (varbit 5408): 0 Small, 1 Large, 2 Full (client-contract 1.2). */
    const val SETTING_POOL = "cox_pool"

    const val POOL_SMALL = 0
    const val POOL_LARGE = 1
    const val POOL_FULL = 2

    /** Rows of the party list: all 40 are sent, the layout runs on row 39 (client-contract 2.4, enum 1537). */
    private const val LIST_ROWS = 40

    // ---- board and party list 499

    /** The board: every advertised forming party, oldest first (client-contract 2.4). */
    fun openList(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.RAIDS_LOBBY_PARTYLIST)
        val lobby = CoxLobbyWorld.of(p)
        val parties =
            Raids
                .of(p)
                .parties(CoxRaid.ID)
                .filter { it.state == PartyState.Forming && it.id.value in lobby.listed }
                .take(LIST_ROWS)
        p[SHOWN_ROWS] = IntArray(parties.size) { parties[it].id.value }
        for (row in 0 until LIST_ROWS) {
            val party = parties.getOrNull(row)
            p.clientScript(SCRIPT_LIST_ROW, row, if (party == null) "" else listRow(p, party, lobby))
        }
    }

    /** `RESUME_PAUSEBUTTON` on 499:3: 0 Refresh, 1 Make party / My party (client-contract 2.4). */
    fun listButton(p: PlayerRef, sub: Int) {
        if (!p.isOpen(Interfaces.RAIDS_LOBBY_PARTYLIST)) return
        when (sub) {
            LIST_REFRESH -> openList(p)
            LIST_MYPARTY -> myParty(p)
        }
    }

    /** `RESUME_PAUSEBUTTON` on 499:15: "View party:" on row [row]; only rows this player was sent are valid (hostile input). */
    fun listRow(p: PlayerRef, row: Int) {
        if (!p.isOpen(Interfaces.RAIDS_LOBBY_PARTYLIST)) return
        val id = p[SHOWN_ROWS]?.getOrNull(row) ?: return
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
        val raids = Raids.of(p)
        val def = raids.definition(CoxRaid.ID) ?: return
        Raids.claim(p, CoxRaid.ID)
        val party = raids.createParty(def, p.username)
        if (party == null) {
            p.message(ALREADY_IN_PARTY)
            return
        }
        // Client-contract 1.2 (server-only 3288 / 9541): the last map pool and scaling the leader chose.
        party.settings[SETTING_POOL] = p.varbit(Varbits.RAIDS_REMEMBER_LAST_PARTYTYPE).coerceIn(POOL_SMALL, POOL_FULL)
        party.settings[SETTING_SCALING] = p.varbit(Varbits.RAIDS_REMEMBER_LAST_SCALING)
        CoxLobbyWorld.of(p).created[party.id.value] = p.tick
        syncMembers(p, party, emptyList())
        openDetails(p, party)
    }

    /** Row string of script 1566: `leader|members|size|combat|skills|scaling|cm|age` (client-contract 2.4). */
    private fun listRow(p: PlayerRef, party: Party, lobby: CoxLobbyWorld): String {
        val age = (p.tick - lobby.created.getOrPut(party.id.value) { p.tick }).coerceAtLeast(0)
        val sb = StringBuilder(party.leader)
        sb.append('|').append(party.members.size)
        sb.append('|').append(party.settings[SETTING_SIZE] ?: 0)
        sb.append('|').append(party.settings[SETTING_COMBAT] ?: 0)
        sb.append('|').append(party.settings[SETTING_SKILL] ?: 0)
        sb.append('|').append(party.settings[SETTING_SCALING] ?: 0)
        sb.append('|').append(party.settings[CoxFlow.CM_SETTING] ?: 0)
        sb.append('|').append(age)
        return sb.toString()
    }

    // ---- party details 507

    /** Opens the screen: the viewed party's settings go into the viewer's varbits (script 1516 reads them), then the rows. */
    fun openDetails(p: PlayerRef, party: Party) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.RAIDS_LOBBY_PARTYDETAILS)
        p[VIEW] = CoxPartyView(party.id.value)
        val role = roleOf(p, party)
        showSettings(p, party)
        // Send order (client-contract 2.5): 1517 once per member, then 1524.
        for (name in party.members) p.clientScript(SCRIPT_MEMBER_ROW, 0, CoxLobbyWorld.memberRow(p, name))
        val listed = if (role == ROLE_LEADER && party.id.value in CoxLobbyWorld.of(p).listed) 1 else 0
        p.clientScript(SCRIPT_DETAILS, role, listed, advertsLeft(p), "${party.leader}'s party")
    }

    /** The viewed party's values in the viewer's own varbits; 5428 is 1 in the leader's channel (the client then offers "Kick" by rank). */
    private fun showSettings(p: PlayerRef, party: Party) {
        setIfChanged(p, Varbits.RAIDS_LOBBY_PARTYSIZE, party.settings[SETTING_SIZE] ?: 0)
        setIfChanged(p, Varbits.RAIDS_LOBBY_MINCOMBAT, party.settings[SETTING_COMBAT] ?: 0)
        setIfChanged(p, Varbits.RAIDS_LOBBY_MINSKILLTOTAL, party.settings[SETTING_SKILL] ?: 0)
        setIfChanged(p, Varbits.RAIDS_SCALING, party.settings[SETTING_SCALING] ?: 0)
        setIfChanged(p, Varbits.RAIDS_CHALLENGE_MODE, party.settings[CoxFlow.CM_SETTING] ?: 0)
        setIfChanged(p, Varbits.RAIDS_MAP_POOL_SELECTED, party.settings[SETTING_POOL] ?: POOL_SMALL)
        setIfChanged(p, Varbits.RAIDS_CLIENT_CHANNELMEMBER, if (CoxChannel.inPartyChannel(p, party)) 1 else 0)
    }

    /** `RESUME_PAUSEBUTTON` on 507:3, sub-ids of enum 1539 (client-contract 2.5). Every id and the viewer's role are checked here. */
    fun detailsButton(p: PlayerRef, sub: Int) {
        val party = viewed(p) ?: return
        when (sub) {
            SUB_JOIN -> join(p, party)
            SUB_ADVERTISE -> advertise(p, party)
            SUB_DISBAND -> disbandOrLeave(p, party)
            SUB_REFRESH -> openDetails(p, party)
            SUB_BACK -> openList(p)
            SUB_SIZE -> askNumber(p, party, SETTING_SIZE, SIZE_PROMPT, 0, CoxRaid.MAX_PARTY)
            SUB_COMBAT -> askNumber(p, party, SETTING_COMBAT, COMBAT_PROMPT, 0, MAX_COMBAT)
            SUB_SKILL -> askNumber(p, party, SETTING_SKILL, SKILL_PROMPT, 0, CoxLobbyWorld.MAX_SKILL_TOTAL)
            SUB_SCALING -> askNumber(p, party, SETTING_SCALING, SCALING_PROMPT, 0, CoxRaid.MAX_PARTY)
            SUB_CHALLENGE -> toggleChallenge(p, party)
            SUB_POOL -> askPool(p, party)
        }
    }

    /** The open details screen's party; null (after the right message) when it is gone. */
    private fun viewed(p: PlayerRef): Party? {
        if (!p.isOpen(Interfaces.RAIDS_LOBBY_PARTYDETAILS)) return null
        val view = p[VIEW] ?: return null
        val party = party(p, view.partyId)
        if (party == null) {
            p.message(PARTY_GONE)
            openList(p)
        }
        return party
    }

    private fun join(p: PlayerRef, party: Party) {
        when {
            party.state != PartyState.Forming -> {
                p.message(PARTY_STARTED)
            }

            party.isFull -> {
                p.message(PARTY_FULL)
            }

            partyOf(p) != null || Raids.of(p).blockingParty(p.username, CoxRaid.ID) != null -> {
                p.message(ALREADY_IN_PARTY)
            }

            channelJoin(p, party) -> {
                Unit
            }

            Raids.claim(p, CoxRaid.ID) && party.join(p.username) -> {
                p.message("You have joined ${party.leader}'s party.")
                syncMembers(p, party, emptyList())
            }
        }
        openDetails(p, party)
    }

    /**
     * CX2: when the leader owns a friends chat channel, joining the party is joining that channel; [CoxChannel] then adds
     * the player once their rank is recruit or higher. False (direct join) without channel data (no hub, OPEN-CX1).
     */
    private fun channelJoin(p: PlayerRef, party: Party): Boolean {
        val leader = p.onlinePlayer(party.leader) ?: return false
        if (CoxChannel.partyChannel(leader) == null) return false
        if (CoxChannel.inPartyChannel(p, party)) {
            p.message(RANK_TOO_LOW)
        } else {
            p.joinFriendsChat(party.leader)
        }
        return true
    }

    /** Advertise / De-list (leader). Each new advert spends one of the day's adverts (varbit 5414; OPEN-CX1). */
    private fun advertise(p: PlayerRef, party: Party) {
        if (!canEdit(p, party)) return
        val lobby = CoxLobbyWorld.of(p)
        if (party.id.value in lobby.listed) {
            lobby.listed.remove(party.id.value)
        } else if (advertsLeft(p) > 0) {
            useAdvert(p)
            lobby.listed += party.id.value
        } else {
            p.message(NO_ADVERTS)
        }
        openDetails(p, party)
    }

    /** The red button: the leader disbands the party, a member leaves it. */
    private fun disbandOrLeave(p: PlayerRef, party: Party) {
        if (!party.has(p.username)) return
        if (party.state != PartyState.Forming) {
            p.message(PARTY_STARTED)
            return
        }
        val leaving = if (isLeader(p, party)) party.members.toList() else listOf(p.username)
        for (name in leaving) {
            // CX2: the party is the leader's channel, so a member leaving it leaves the channel too (or the sync re-adds them).
            val member = p.onlinePlayer(name)
            if (member != null && name != party.leader && CoxChannel.inPartyChannel(member, party)) member.leaveFriendsChat()
        }
        for (name in leaving) party.leave(name)
        val lobby = CoxLobbyWorld.of(p)
        if (party.state == PartyState.Disbanded || party.members.isEmpty()) {
            lobby.listed.remove(party.id.value)
            lobby.created.remove(party.id.value)
        }
        syncMembers(p, party, leaving)
        openList(p)
    }

    /** Runs [edit] when [p] leads [party] and it is still forming, then redraws the page (the 1.6 s restore, client-contract 2.5). */
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

    /** A numeric setting: a count dialog, re-checked when the answer arrives (it may come late). */
    private fun askNumber(p: PlayerRef, party: Party, key: String, prompt: String, min: Int, max: Int) {
        if (!canEdit(p, party)) return
        val id = party.id.value
        p.countDialog(prompt) { n ->
            val current = party(this, id)
            if (current == null || !canEdit(this, current)) return@countDialog
            // OPEN-CX2: a scaled size below the real party size would remove players; 0 clears it.
            val low = if (key == SETTING_SCALING && n != 0) maxOf(min, current.members.size) else min
            if (n in low..max || n == 0) {
                current.settings[key] = n
                if (key == SETTING_SCALING) setVarbit(Varbits.RAIDS_REMEMBER_LAST_SCALING, n)
            } else {
                message("Enter a number from $low to $max, or 0 for none.")
            }
            openDetails(this, current)
        }
    }

    /** The Map Layout row: Small / Large / Full. Leaving Full clears Challenge Mode (the checkbox is only live on Full). */
    private fun askPool(p: PlayerRef, party: Party) {
        if (!canEdit(p, party)) return
        val id = party.id.value
        p.choiceDialog(POOL_PROMPT, POOL_NAMES) { pick ->
            val current = party(this, id)
            if (current != null && canEdit(this, current) && pick - 1 in POOL_NAMES.indices) {
                current.settings[SETTING_POOL] = pick - 1
                if (pick - 1 != POOL_FULL) current.settings[CoxFlow.CM_SETTING] = 0
                setVarbit(Varbits.RAIDS_REMEMBER_LAST_PARTYTYPE, pick - 1)
                openDetails(this, current)
            }
        }
    }

    /** The checkbox: live only on the Full layout (client-contract 2.5, script 2294); CX49 asks for completions first. */
    private fun toggleChallenge(p: PlayerRef, party: Party) {
        leaderEdit(p, party) {
            val on = party.settings[CoxFlow.CM_SETTING] == 1
            when {
                on -> {
                    party.settings[CoxFlow.CM_SETTING] = 0
                }

                (party.settings[SETTING_POOL] ?: POOL_SMALL) != POOL_FULL -> {
                    p.message(CM_NEEDS_FULL)
                }

                p.varp(Varps.TOTAL_COMPLETED_XERICCHAMBERS) < CoxUiConfig.cmMinCompletions -> {
                    p.message(CM_LOCKED)
                }

                else -> {
                    party.settings[CoxFlow.CM_SETTING] = 1
                }
            }
        }
    }

    // ---- adverts

    /** Adverts the leader still has today (saved day + count; the day rolls over at midnight UTC, OPEN-CX1). */
    private fun advertsLeft(p: PlayerRef): Int {
        val used = if (p.saved(ADVERT_DAY_KEY) == today(p)) (p.saved(ADVERT_USED_KEY) ?: 0L).toInt() else 0
        val left = (CoxUiConfig.dailyAdverts - used).coerceIn(0, CoxUiConfig.ADVERTS_VARBIT_MAX)
        setIfChanged(p, Varbits.RAIDS_DAILY_ADVERTS, left)
        return left
    }

    private fun useAdvert(p: PlayerRef) {
        val day = today(p)
        val used = if (p.saved(ADVERT_DAY_KEY) == day) p.saved(ADVERT_USED_KEY) ?: 0L else 0L
        p.setSaved(ADVERT_DAY_KEY, day)
        p.setSaved(ADVERT_USED_KEY, used + 1)
    }

    private fun today(p: PlayerRef): Long = p.epochMinutes / MINUTES_PER_DAY

    // ---- roles and shared party state

    private fun isLeader(p: PlayerRef, party: Party): Boolean = party.leader.equals(p.username, ignoreCase = true)

    /** Viewer role for script 1524: 2 leader, 1 member, anything else may join (client-contract 2.5). */
    private fun roleOf(p: PlayerRef, party: Party): Int =
        when {
            isLeader(p, party) -> ROLE_LEADER
            party.has(p.username) -> ROLE_MEMBER
            else -> ROLE_OUTSIDER
        }

    private fun party(any: EntityRef, id: Int): Party? = Raids.of(any).parties(CoxRaid.ID).firstOrNull { it.id.value == id }

    fun partyOf(p: PlayerRef): Party? = Raids.of(p).partyOf(p.username)?.takeIf { it.raid.id == CoxRaid.ID }

    /** Writes `RAIDS_PARTY_GROUPHOLDER` (the party id, -1 none) for every online member and for [removed]. */
    fun syncMembers(any: EntityRef, party: Party, removed: Collection<String>) {
        for (name in party.members) any.onlinePlayer(name)?.let { setHolder(it, party) }
        for (name in removed) any.onlinePlayer(name)?.let { if (!party.has(name)) setHolder(it, null) }
    }

    fun setHolder(p: PlayerRef, party: Party?) {
        val id = party?.id?.value ?: NO_PARTY
        if (p.varp(Varps.RAIDS_PARTY_GROUPHOLDER) != id) p.setVarp(Varps.RAIDS_PARTY_GROUPHOLDER, id)
    }

    fun login(p: PlayerRef) {
        setHolder(p, partyOf(p))
    }

    /** Keeps the stats of a leaving player for the rows of a party he stays in. */
    fun logout(p: PlayerRef) {
        CoxLobbyWorld.of(p).stats[p.username.lowercase()] = CoxLobbyWorld.snapshot(p)
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    // ---- constants

    private val VIEW = AttrKey<CoxPartyView>("cox_party_view")
    private val SHOWN_ROWS = AttrKey<IntArray>("cox_party_list_rows")

    /** Server-run client script 1566 (`is`): party list row (client-contract 2.4). */
    private const val SCRIPT_LIST_ROW = 1566

    /** Server-run client script 1517 (`is`): member row (the int is unused, client-contract 2.5). */
    private const val SCRIPT_MEMBER_ROW = 1517

    /** Server-run client script 1524 (`iiis`): role, listed, adverts, title (client-contract 2.5). */
    private const val SCRIPT_DETAILS = 1524

    // 499:3 pause-button sub-ids (enum 1538, client-contract 2.4)
    private const val LIST_REFRESH = 0
    private const val LIST_MYPARTY = 1

    // 507:3 pause-button sub-ids (enum 1539, client-contract 2.5)
    private const val SUB_JOIN = 0
    private const val SUB_ADVERTISE = 1
    private const val SUB_DISBAND = 2
    private const val SUB_REFRESH = 3
    private const val SUB_BACK = 4
    private const val SUB_SIZE = 5
    private const val SUB_COMBAT = 6
    private const val SUB_SKILL = 7
    private const val SUB_SCALING = 8
    private const val SUB_CHALLENGE = 9
    private const val SUB_POOL = 10

    // viewer roles, script 1524 (client-contract 2.5)
    private const val ROLE_LEADER = 2
    private const val ROLE_MEMBER = 1
    private const val ROLE_OUTSIDER = 0

    private const val NO_PARTY = -1

    /** The highest combat level (OSRS Wiki "Combat level"); varbit 5426 is 7 bits (client-contract 1.2). */
    private const val MAX_COMBAT = 126

    private const val MINUTES_PER_DAY = 1440L
    private const val ADVERT_DAY_KEY = "raids.cox.advertDay"
    private const val ADVERT_USED_KEY = "raids.cox.advertsUsed"

    /** Map layout names in varbit 5408 order (client-contract 1.2). */
    private val POOL_NAMES = listOf("Small", "Large", "Full")

    // OPEN-CXUI1 (cox.md OPEN-CX1): none of these texts is captured; placeholders until seen in game.
    private const val POOL_PROMPT = "Which map layout?"
    private const val SIZE_PROMPT = "Enter the preferred party size (0 for none):"
    private const val COMBAT_PROMPT = "Enter the preferred combat level (0 for none):"
    private const val SKILL_PROMPT = "Enter the preferred skill total (0 for none):"
    private const val SCALING_PROMPT = "Enter the scaled party size (0 for none):"
    private const val PARTY_GONE = "That party no longer exists."
    private const val PARTY_STARTED = "That party has already entered the Chambers."
    private const val PARTY_FULL = "That party is full."
    private const val RANK_TOO_LOW = "You need at least the rank of recruit in the leader's chat-channel to join."
    private const val ALREADY_IN_PARTY = "You are already in a party."
    private const val NO_ADVERTS = "You have no adverts left today."
    private const val CM_NEEDS_FULL = "Challenge Mode needs the Full map layout."
    private const val CM_LOCKED = "You have not completed enough raids for Challenge Mode."
}
