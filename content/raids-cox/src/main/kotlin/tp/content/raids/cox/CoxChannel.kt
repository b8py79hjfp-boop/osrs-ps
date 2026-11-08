package tp.content.raids.cox

import tp.content.raids.Party
import tp.content.raids.PartyState
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.FriendsChatView
import tp.plugin.PlayerRef
import tp.plugin.WorldRef

/**
 * The raid party is the leader's friends chat channel (cox.md CX2, CX6; cox/client-contract.md 2.5).
 *
 * - While a party forms, members of the leader's own channel with rank recruit or higher are in it. Anyone who leaves
 *   the channel, is kicked (the client's own `clan_kickuser`) or drops below recruit is out.
 * - When the leader leaves the raid, the highest-ranked channel member (recruit at least) leads. If nobody qualifies,
 *   everyone is thrown out. After the start this happens only with [CoxConfig.leaderHandoverAfterStart] (OPEN-CX5).
 *
 * Without a hub (no channel data), the board joins directly and the next slot leads (OPEN-CX1 fallback).
 */
internal object CoxChannel {
    /** Hub friends-chat ranks: -1 guest, 0 friend, 1 recruit .. 6 general, 7 owner (CX2: recruit and above may join). */
    const val RECRUIT = 1

    /** The leader's channel when [leader] owns the one they are in (the party channel), else null. */
    fun partyChannel(
        leader: PlayerRef,
    ): FriendsChatView? = leader.friendsChat?.takeIf { it.owner.equals(leader.username, ignoreCase = true) }

    /** [name]'s rank in [chan] (case-insensitive), or null when not in it. */
    fun rankOf(
        chan: FriendsChatView,
        name: String,
    ): Int? =
        chan.members.entries
            .firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value

    /** Whether [viewer] is in [party]'s channel: varbit 5428, which gates the "Kick" op on 507 member rows (script 1520). */
    fun inPartyChannel(viewer: PlayerRef, party: Party): Boolean =
        viewer.friendsChat?.owner?.equals(party.leader, ignoreCase = true) == true

    /** Per-tick sync of every forming CoX party to its leader's channel. */
    fun tick(world: WorldRef) {
        val raids = Raids.of(world)
        for (party in raids.parties(CoxRaid.ID)) {
            if (party.state != PartyState.Forming) continue
            val leader = world.onlinePlayer(party.leader) ?: continue
            val chan = partyChannel(leader) ?: continue
            sync(world, raids, party, chan)
        }
    }

    private fun sync(world: WorldRef, raids: Raids, party: Party, chan: FriendsChatView) {
        val removed = ArrayList<String>()
        for (name in party.members.drop(1)) {
            val rank = rankOf(chan, name)
            if (rank == null || rank < RECRUIT) removed += name
        }
        for (name in removed) {
            party.leave(name)
            world.onlinePlayer(name)?.let { CoxBoard.setHolder(it, null) }
        }
        var added = false
        for ((name, rank) in chan.members) {
            // Party.join refuses a full party and a member already in it.
            if (rank < RECRUIT || raids.partyOf(name) != null) continue
            if (world.onlinePlayer(name) == null) continue
            if (party.join(name)) added = true
        }
        if (added) for (name in party.members) world.onlinePlayer(name)?.let { CoxBoard.setHolder(it, party) }
    }

    /**
     * CX6: [leaving] (the leader) left [run]. The new leader is the highest-ranked member of the old leader's channel
     * who is still inside, rank recruit or higher (slot order breaks ties). With no channel data the next slot inside
     * leads. If nobody qualifies, everyone left is thrown out.
     */
    fun leaderLeft(world: WorldRef, run: RaidRun, leaving: String) {
        if (run.raid.id != CoxRaid.ID || !run.party.leader.equals(leaving, ignoreCase = true)) return
        if (run.state == RunState.Completed || run.state is RunState.Failed) return
        val begun = CoxFlow.state(run)?.begun == true
        if (begun && !CoxConfig.leaderHandoverAfterStart) return
        val inside = run.presentPlayers(world.anyPlayer(run) ?: return).filterNot { it.username.equals(leaving, ignoreCase = true) }
        if (inside.isEmpty()) return
        val chan = inside.firstNotNullOfOrNull { p -> p.friendsChat?.takeIf { it.owner.equals(leaving, ignoreCase = true) } }
        val next =
            if (chan == null) {
                inside.first()
            } else {
                inside
                    .filter { (rankOf(chan, it.username) ?: -1) >= RECRUIT }
                    .maxByOrNull { rankOf(chan, it.username) ?: -1 }
            }
        if (next == null) {
            Raids.reset(inside.first(), run)
            return
        }
        run.party.promote(next.username)
        run.markDirty()
    }

    /** Any online member of [run], as the entity handle raids-core needs. */
    private fun WorldRef.anyPlayer(run: RaidRun): PlayerRef? = run.members.keys.firstNotNullOfOrNull { onlinePlayer(it) }
}
