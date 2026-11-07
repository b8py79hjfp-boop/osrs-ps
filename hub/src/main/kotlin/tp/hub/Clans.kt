package tp.hub

import tp.services.ClanChannelMember
import tp.services.ClanMemberRow
import tp.services.ClanRecord
import tp.services.ClanRules
import tp.services.SocialEvent

/**
 * Clans (docs/mechanics/clans.md CL1-CL12): one ranked clan per account, any number of guest listeners, founding with
 * five founders, recruiting, ranks, kicks and bans, the clan and guest-clan chat. Clans are hub state (all worlds) and
 * persisted through [HubStore]; channel membership follows who is online. State is guarded by `this`; store writes
 * happen inside the lock (clans are small and writes rare).
 */
internal class Clans(
    private val store: HubStore,
    private val online: Map<Long, Int>,
    private val deliver: (worldId: Int, event: SocialEvent) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val clans = HashMap<Long, ClanRecord>()
    private val byName = HashMap<String, Long>()
    private val clanOf = HashMap<Long, Long>()
    private val guestOf = HashMap<Long, Long>()

    /** CL2: founding in progress: founder -> (name, founders who accepted). */
    private class Founding(val name: String, val founders: LinkedHashMap<Long, String>)

    private val foundings = HashMap<Long, Founding>()

    /** CL4: pending invitations: invited account -> (clan id or founding founder id, is founding). */
    private val invites = HashMap<Long, Pair<Long, Boolean>>()

    init {
        for (clan in store.clans()) index(clan)
    }

    private fun index(clan: ClanRecord) {
        clans[clan.id] = clan
        byName[clan.name.lowercase()] = clan.id
        for (m in clan.members) clanOf[m.accountId] = clan.id
    }

    fun clanOf(accountId: Long): ClanRecord? = synchronized(this) { clanOf[accountId]?.let(clans::get) }

    // ---- founding (CL2)

    /** CL2: [accountId] starts founding [name] at the Clan Hub clerk. */
    fun found(accountId: Long, name: String): String? {
        val me = store.account(accountId) ?: return UNABLE
        val clean = name.trim()
        if (clean.length !in 1..MAX_NAME || !clean.all { it.isLetterOrDigit() || it == ' ' }) return "That clan name is not allowed."
        synchronized(this) {
            if (clanOf.containsKey(accountId)) return "You are already in a clan."
            if (byName.containsKey(clean.lowercase()) || foundings.values.any { it.name.equals(clean, ignoreCase = true) }) {
                return "A clan with that name already exists."
            }
            foundings[accountId] = Founding(clean, linkedMapOf(accountId to me.displayName))
        }
        return null
    }

    // ---- invitations (CL2 founders, CL4 recruits)

    /** Invites [targetName]: to the founding [accountId] leads, else to [accountId]'s clan if their rank may recruit. */
    fun invite(accountId: Long, targetName: String): String? {
        val me = store.account(accountId) ?: return UNABLE
        val target = store.account(targetName) ?: return OFFLINE
        val world = online[target.id] ?: return OFFLINE
        val clanName: String
        synchronized(this) {
            if (clanOf.containsKey(target.id)) return "That player is already in a clan."
            val founding = foundings[accountId]
            if (founding != null) {
                invites[target.id] = accountId to true
                clanName = founding.name
            } else {
                val clan = clanOf[accountId]?.let(clans::get) ?: return "You are not in a clan."
                recruitRefusal(clan, accountId, target.displayName)?.let { return it }
                invites[target.id] = clan.id to false
                clanName = clan.name
            }
        }
        deliver(world, SocialEvent.ClanInvite(target.id, me.displayName, clanName))
        return null
    }

    private fun recruitRefusal(clan: ClanRecord, accountId: Long, targetName: String): String? =
        when {
            clan.members.first { it.accountId == accountId }.rank < clan.rules.recruitRank -> {
                "You do not have a high enough rank to recruit."
            }

            clan.members.size >= MAX_MEMBERS -> {
                "Your clan is full."
            }

            clan.banned.any { it.equals(targetName, ignoreCase = true) } -> {
                "That player is banned from your clan."
            }

            else -> {
                null
            }
        }

    /** The invited player accepts the latest invitation. */
    fun accept(accountId: Long): String? {
        val me = store.account(accountId) ?: return UNABLE
        val (ref, founding) = synchronized(this) { invites.remove(accountId) } ?: return "You have no clan invitation."
        if (founding) return acceptFounding(accountId, me.displayName, ref)
        val updated: ClanRecord
        synchronized(this) {
            if (clanOf.containsKey(accountId)) return "You are already in a clan."
            val clan = clans[ref] ?: return "That clan no longer exists."
            if (clan.members.size >= MAX_MEMBERS) return "That clan is full."
            updated = clan.copy(members = clan.members + ClanMemberRow(accountId, me.displayName, RECRUIT, runeDay()))
            put(updated)
            guestOf.remove(accountId)
        }
        broadcastState(updated)
        return null
    }

    private fun acceptFounding(accountId: Long, name: String, founderId: Long): String? {
        val created: ClanRecord
        synchronized(this) {
            val founding = foundings[founderId]
            val refusal =
                when {
                    clanOf.containsKey(accountId) -> "You are already in a clan."
                    founding == null -> "That clan is no longer being founded."
                    else -> null
                }
            if (refusal != null || founding == null) return refusal
            founding.founders[accountId] = name
            if (founding.founders.size < FOUNDERS) return null
            foundings.remove(founderId)
            if (byName.containsKey(founding.name.lowercase())) return "A clan with that name already exists."
            val day = runeDay()
            val members =
                founding.founders.map { (id, n) -> ClanMemberRow(id, n, if (id == founderId) OWNER else RECRUIT, day) }
            created = ClanRecord(nextId(), founding.name, now() / MILLIS_PER_MINUTE, members, emptyList(), DEFAULT_RULES)
            put(created)
        }
        broadcastState(created)
        return null
    }

    // ---- leaving, ranks, kicks, bans

    /** CL5: leaves the clan; the owner's last departure hands the clan to the highest rank, the oldest first. */
    fun leave(accountId: Long): String? {
        val updated: ClanRecord?
        synchronized(this) {
            val clan = clanOf.remove(accountId)?.let(clans::get) ?: return "You are not in a clan."
            val rest = clan.members.filter { it.accountId != accountId }
            if (rest.isEmpty()) {
                clans.remove(clan.id)
                byName.remove(clan.name.lowercase())
                store.deleteClan(clan.id)
                updated = null
            } else {
                val leaving = clan.members.first { it.accountId == accountId }
                val members =
                    if (leaving.rank == OWNER) {
                        val heir = rest.sortedWith(compareByDescending<ClanMemberRow> { it.rank }.thenBy { it.joinedDay }).first()
                        rest.map { if (it === heir) it.copy(rank = OWNER) else it }
                    } else {
                        rest
                    }
                updated = clan.copy(members = members)
                put(updated)
            }
        }
        online[accountId]?.let { deliver(it, SocialEvent.ClanState(accountId, guest = false, clan = null, online = emptyList())) }
        updated?.let(::broadcastState)
        return null
    }

    /** CL5: administrators and above rank members below them to a rank below their own. */
    private fun mayRank(mine: Int, current: Int, rank: Int): Boolean = mine >= ADMINISTRATOR && current < mine && rank in RECRUIT until mine

    /** CL5: an administrator or above sets a lower member's rank, below their own. */
    fun setRank(accountId: Long, targetName: String, rank: Int): String? {
        val updated: ClanRecord
        synchronized(this) {
            val clan = clanOf[accountId]?.let(clans::get) ?: return "You are not in a clan."
            val mine = clan.members.first { it.accountId == accountId }.rank
            val target =
                clan.members.firstOrNull { it.name.equals(targetName, ignoreCase = true) } ?: return "That player is not in your clan."
            if (!mayRank(mine, target.rank, rank)) {
                return "You do not have a high enough rank to do that."
            }
            updated = clan.copy(members = clan.members.map { if (it === target) it.copy(rank = rank) else it })
            put(updated)
        }
        broadcastState(updated)
        return null
    }

    /** CL7: removes a member (rank below the kicker's, kicker at the kick rank) or kicks a guest out of the channel. */
    fun kick(accountId: Long, targetName: String, ban: Boolean): String? {
        val target = store.account(targetName) ?: return "That player is not in your clan."
        val updated: ClanRecord
        synchronized(this) {
            val clan = clanOf[accountId]?.let(clans::get) ?: return "You are not in a clan."
            val mine = clan.members.first { it.accountId == accountId }.rank
            if (mine < clan.rules.kickRank) return "You do not have a high enough rank to do that."
            val member = clan.members.firstOrNull { it.accountId == target.id }
            if (member != null && member.rank >= mine) return "You do not have a high enough rank to do that."
            val banned = if (ban) (clan.banned + target.displayName).distinct() else clan.banned
            updated = clan.copy(members = clan.members.filter { it.accountId != target.id }, banned = banned)
            put(updated)
            clanOf.remove(target.id)
            if (guestOf[target.id] == clan.id) guestOf.remove(target.id)
        }
        online[target.id]?.let { world ->
            deliver(world, SocialEvent.ClanState(target.id, guest = false, clan = null, online = emptyList()))
            deliver(world, SocialEvent.ClanState(target.id, guest = true, clan = null, online = emptyList()))
        }
        broadcastState(updated)
        return null
    }

    /** CL8: talk rank, kick rank, guests, recruit rank: administrators and above. */
    fun setRules(accountId: Long, rules: ClanRules): String? {
        val updated: ClanRecord
        synchronized(this) {
            val clan = clanOf[accountId]?.let(clans::get) ?: return "You are not in a clan."
            if (clan.members.first { it.accountId == accountId }.rank <
                ADMINISTRATOR
            ) {
                return "You do not have a high enough rank to do that."
            }
            updated = clan.copy(rules = rules)
            put(updated)
        }
        broadcastState(updated)
        return null
    }

    // ---- guests (CL9)

    fun joinGuest(accountId: Long, clanName: String): String? {
        val me = store.account(accountId) ?: return UNABLE
        val clan: ClanRecord
        synchronized(this) {
            clan = byName[clanName.trim().lowercase()]?.let(clans::get) ?: return "That clan could not be found."
            if (clanOf[accountId] == clan.id) return "You are already a member of that clan."
            if (!clan.rules.allowGuests) return "That clan does not allow guests."
            if (clan.banned.any { it.equals(me.displayName, ignoreCase = true) }) return "You are banned from that clan."
            guestOf[accountId] = clan.id
        }
        online[accountId]?.let { deliver(it, SocialEvent.ClanState(accountId, guest = true, clan = clan, online = onlineOf(clan))) }
        return null
    }

    fun leaveGuest(accountId: Long) {
        synchronized(this) { guestOf.remove(accountId) } ?: return
        online[accountId]?.let { deliver(it, SocialEvent.ClanState(accountId, guest = true, clan = null, online = emptyList())) }
    }

    // ---- chat (CL10)

    /** "//" (clan) and "///" (guest clan) lines. */
    fun message(accountId: Long, guest: Boolean, text: String): String? {
        val me = store.account(accountId) ?: return null
        if (me.mutedUntil < 0 || me.mutedUntil > now()) return "You are muted."
        val world = online[accountId] ?: return null
        val clan: ClanRecord
        val rank: Int
        synchronized(this) {
            val id =
                (if (guest) guestOf[accountId] else clanOf[accountId])
                    ?: return if (guest) "You are not in a guest clan." else "You are not in a clan."
            clan = clans[id] ?: return null
            rank = clan.members.firstOrNull { it.accountId == accountId }?.rank ?: GUEST
            if (rank < clan.rules.talkRank) return "You do not have a high enough rank to talk in this clan channel."
        }
        for ((to, toWorld, asGuest) in listeners(clan)) {
            deliver(toWorld, SocialEvent.ClanMessage(to, asGuest, clan.name, me.displayName, me.rights, rank, world, text))
        }
        return null
    }

    // ---- presence

    /** Login / logout / hop: the player gets their clan state and the others see the new world. */
    fun presenceChanged(accountId: Long) {
        val clan = clanOf(accountId) ?: return
        broadcastState(clan)
        if (online[accountId] == null) leaveGuest(accountId)
    }

    // ---- internals

    private fun put(clan: ClanRecord) {
        clans[clan.id] = clan
        byName[clan.name.lowercase()] = clan.id
        for (m in clan.members) clanOf[m.accountId] = clan.id
        store.putClan(clan)
    }

    private fun onlineOf(clan: ClanRecord): List<ClanChannelMember> =
        clan.members.mapNotNull { m -> online[m.accountId]?.let { ClanChannelMember(m.name, m.rank, it) } }

    /** Online members (as members) and guests (if guests may listen). */
    private fun listeners(clan: ClanRecord): List<Triple<Long, Int, Boolean>> =
        synchronized(this) {
            val out = ArrayList<Triple<Long, Int, Boolean>>()
            for (m in clan.members) online[m.accountId]?.let { out += Triple(m.accountId, it, false) }
            if (clan.rules.allowGuests) {
                for ((acc, id) in guestOf) if (id == clan.id) online[acc]?.let { out += Triple(acc, it, true) }
            }
            out
        }

    private fun broadcastState(clan: ClanRecord) {
        val members = onlineOf(clan)
        for ((to, world, asGuest) in listeners(clan)) deliver(world, SocialEvent.ClanState(to, asGuest, clan, members))
    }

    private fun nextId(): Long = (clans.keys.maxOrNull() ?: 0L) + 1

    /** Rune days: days since 27 February 2002 (the client's date origin). */
    private fun runeDay(): Int = ((now() - RUNE_EPOCH_MILLIS) / MILLIS_PER_DAY).toInt()

    private fun ClanRecord.copy(
        members: List<ClanMemberRow> = this.members,
        banned: List<String> = this.banned,
        rules: ClanRules = this.rules,
    ) = ClanRecord(id, name, createdMinute, members, banned, rules)

    private fun ClanMemberRow.copy(rank: Int) = ClanMemberRow(accountId, name, rank, joinedDay)

    companion object {
        /** RuneLite `ClanRank`: owner 126, deputy owner 125, administrator 100, guest -1. */
        const val OWNER = 126
        const val ADMINISTRATOR = 100
        const val GUEST = -1

        /** OPEN-CL1: the rank number a newly recruited member gets (lowest custom rank). */
        const val RECRUIT = 10

        /** Wiki "Clan": five founders, at most 500 members. */
        const val FOUNDERS = 5
        const val MAX_MEMBERS = 500

        /** OPEN-CL2: name rules (length 12 like display names; letters, digits, spaces). */
        const val MAX_NAME = 12

        /** OPEN-CL3: new clans: everyone talks, administrators kick and recruit, guests allowed. */
        val DEFAULT_RULES = ClanRules(talkRank = GUEST, kickRank = ADMINISTRATOR, allowGuests = true, recruitRank = ADMINISTRATOR)

        private const val UNABLE = "Unable to do that right now."
        private const val OFFLINE = "That player is currently offline."
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MILLIS_PER_DAY = 86_400_000L

        /** 2002-02-27T00:00:00Z. */
        private const val RUNE_EPOCH_MILLIS = 1_014_768_000_000L
    }
}
