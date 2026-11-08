package tp.hub

import tp.services.ChannelMember
import tp.services.ChannelSettings
import tp.services.SocialEvent
import tp.util.Rank

/**
 * Friends chat channels (docs/mechanics/friends-chat.md). Every account owns one channel, named after its display name.
 * Channels exist while someone is in them. Ranks are persistent: the owner gives them to friends. Membership is hub
 * state, so it spans worlds. Events go to each member's world through [deliver].
 * State is guarded by `this`; store calls happen outside the lock.
 */
internal class FriendChats(
    private val store: HubStore,
    private val online: Map<Long, Int>,
    private val deliver: (worldId: Int, event: SocialEvent) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Channel(val ownerId: Long, val ownerName: String, var settings: ChannelSettings) {
        val members = LinkedHashMap<Long, ChannelMember>()

        /** Kicked account id -> epoch ms until which it may not rejoin (FC1). */
        val kicked = HashMap<Long, Long>()
    }

    private val channels = HashMap<Long, Channel>()
    private val memberOf = HashMap<Long, Long>()

    fun join(accountId: Long, ownerName: String): String? {
        val owner = store.account(ownerName) ?: return "The channel you tried to join does not exist."
        val me = store.account(accountId) ?: return "Unable to join the channel."
        val world = online[accountId] ?: return "Unable to join the channel."
        val rank = rankOf(owner.id, me)
        val settings = store.channelSettings(owner.id) ?: ChannelSettings.DEFAULT
        // FC6: a channel exists only once its owner has named it; FC7: enter rank.
        if (settings.name == null) return "The channel you tried to join does not exist."
        if (rank < settings.enterRank) return "You do not have a high enough rank to join this channel."
        leave(accountId)
        val joined: Channel
        synchronized(this) {
            val channel = channels.getOrPut(owner.id) { Channel(owner.id, owner.displayName, settings) }
            if ((channel.kicked[accountId] ?: 0L) > now()) return "You are temporarily blocked from joining this channel."
            if (channel.members.size >= MAX_MEMBERS) return "The channel you tried to join is full."
            val member = ChannelMember(me.displayName, world, rank)
            channel.members[accountId] = member
            memberOf[accountId] = owner.id
            joined = channel
            broadcast(channel, except = accountId) { SocialEvent.ChannelMemberUpdate(it, member, removed = false) }
        }
        val members = synchronized(this) { joined.members.values.toList() }
        deliver(world, joinedEvent(accountId, joined, members))
        return null
    }

    fun leave(accountId: Long, kicked: Boolean = false) {
        synchronized(this) {
            val ownerId = memberOf.remove(accountId) ?: return
            val channel = channels[ownerId] ?: return
            val member = channel.members.remove(accountId) ?: return
            online[accountId]?.let { deliver(it, SocialEvent.ChannelLeft(accountId, kicked)) }
            broadcast(channel) { SocialEvent.ChannelMemberUpdate(it, member, removed = true) }
            if (channel.members.isEmpty() && channel.kicked.values.none { it > now() }) channels.remove(ownerId)
        }
    }

    fun message(accountId: Long, text: String): String? {
        val me = store.account(accountId) ?: return null
        if (me.mutedUntil < 0 || me.mutedUntil > now()) return "You are muted."
        synchronized(this) {
            val channel = memberOf[accountId]?.let(channels::get) ?: return "You are not in a channel."
            val member = channel.members[accountId] ?: return "You are not in a channel."
            if (member.rank < channel.settings.talkRank) return "You do not have a high enough rank to talk in this channel."
            val name = channel.settings.name.orEmpty()
            broadcast(channel) { SocialEvent.ChannelMessage(it, me.displayName, me.rights, name, member.worldId, text) }
        }
        return null
    }

    fun kick(accountId: Long, name: String): String? {
        synchronized(this) {
            val channel = memberOf[accountId]?.let(channels::get) ?: return "You are not in a channel."
            val myRank = channel.members[accountId]?.rank ?: return "You are not in a channel."
            if (myRank < channel.settings.kickRank) return "You do not have a high enough rank to kick in this channel."
            val target = channel.members.entries.firstOrNull { it.value.name.equals(name, ignoreCase = true) }
            if (target == null) return "$name is not in this channel."
            if (target.value.rank >= myRank) return "You cannot kick someone of equal or higher rank."
            channel.kicked[target.key] = now() + KICK_BLOCK_MS
            leave(target.key, kicked = true)
        }
        return null
    }

    fun setRank(ownerId: Long, name: String, rank: Int): String? {
        if (rank !in UNRANKED..GENERAL) return "Invalid rank."
        val target = store.account(name) ?: return "Unknown player."
        if (rank >= 0 && store.friends(ownerId).none { it.equals(target.displayName, ignoreCase = true) }) {
            return "You can only rank players on your friend list."
        }
        store.setChannelRank(ownerId, target.displayName, rank)
        val newRank = rankOf(ownerId, target)
        synchronized(this) {
            val channel = channels[ownerId] ?: return null
            val current = channel.members[target.id] ?: return null
            val updated = ChannelMember(current.name, current.worldId, newRank)
            channel.members[target.id] = updated
            broadcast(channel) { SocialEvent.ChannelMemberUpdate(it, updated, removed = false) }
        }
        return null
    }

    fun settings(ownerId: Long): ChannelSettings = store.channelSettings(ownerId) ?: ChannelSettings.DEFAULT

    /**
     * Owner changes their channel (FC8): disabling it empties the channel; members below a raised enter rank are removed;
     * everyone left gets the new name and kick rank.
     */
    fun setSettings(ownerId: Long, settings: ChannelSettings) {
        store.setChannelSettings(ownerId, settings)
        synchronized(this) {
            val channel = channels[ownerId] ?: return
            channel.settings = settings
            val removed = channel.members.filter { settings.name == null || (it.key != ownerId && it.value.rank < settings.enterRank) }.keys
            removed.toList().forEach { leave(it) }
            val members = channel.members.values.toList()
            for ((id, member) in channel.members) deliver(member.worldId, joinedEvent(id, channel, members))
        }
    }

    private fun joinedEvent(to: Long, channel: Channel, members: List<ChannelMember>) =
        SocialEvent.ChannelJoined(to, channel.ownerName, channel.settings.name.orEmpty(), channel.settings.kickRank, members)

    /** FC2: owner 7, administrators 127 (Jagex moderator crown), stored ranks, else friend 0, else unranked -1. */
    private fun rankOf(ownerId: Long, account: AccountRow): Int =
        when {
            account.id == ownerId -> {
                OWNER
            }

            account.rights.atLeast(Rank.ADMINISTRATOR) -> {
                JMOD
            }

            else -> {
                store.channelRanks(ownerId)[account.displayName.lowercase()]
                    ?: if (store.friends(ownerId).any { it.equals(account.displayName, ignoreCase = true) }) FRIEND else UNRANKED
            }
        }

    private inline fun broadcast(channel: Channel, except: Long = -1, event: (Long) -> SocialEvent) {
        for ((id, member) in channel.members) if (id != except) deliver(member.worldId, event(id))
    }

    private companion object {
        const val UNRANKED = -1
        const val FRIEND = 0
        const val GENERAL = 6
        const val OWNER = 7
        const val JMOD = 127

        /** OSRS Wiki "Friends chat": channels hold up to 100 players. */
        const val MAX_MEMBERS = 100

        /** FC1: a kicked player cannot rejoin for an hour (OSRS Wiki "Friends chat"). */
        const val KICK_BLOCK_MS = 60 * 60 * 1000L
    }
}
