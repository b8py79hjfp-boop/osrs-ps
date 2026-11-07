package tp.plugin

import tp.engine.ClanRequest

/** Clan actions content can ask the hub for (docs/mechanics/clans.md CL1-CL8). */
public sealed interface ClanAction {
    /** Found a clan named [name] with the four players who accepted the invitations (CL1). */
    public class Found(public val name: String) : ClanAction

    /** Invite (recruit) [name] into the player's clan, or as a founder while founding (CL2-CL3). */
    public class Invite(public val name: String) : ClanAction

    /** Accept the pending invitation (CL3). */
    public data object Accept : ClanAction

    /** Leave the clan (CL4). */
    public data object Leave : ClanAction

    /** Set [name]'s rank (CL5). */
    public class SetRank(public val name: String, public val rank: Int) : ClanAction

    /** Kick [name] from the clan, banning them with [ban] (CL7). */
    public class Kick(public val name: String, public val ban: Boolean) : ClanAction

    /** Channel and recruiting ranks (CL8). */
    public class SetRules(
        public val talkRank: Int,
        public val kickRank: Int,
        public val allowGuests: Boolean,
        public val recruitRank: Int,
    ) : ClanAction

    /** Join [clanName]'s channel as a guest (CL6). */
    public class JoinGuest(public val clanName: String) : ClanAction

    /** Leave the guest channel (CL6). */
    public data object LeaveGuest : ClanAction
}

internal fun ClanAction.request(): ClanRequest =
    when (this) {
        is ClanAction.Found -> ClanRequest.Found(name)
        is ClanAction.Invite -> ClanRequest.Invite(name)
        ClanAction.Accept -> ClanRequest.Accept
        ClanAction.Leave -> ClanRequest.Leave
        is ClanAction.SetRank -> ClanRequest.SetRank(name, rank)
        is ClanAction.Kick -> ClanRequest.Kick(name, ban)
        is ClanAction.SetRules -> ClanRequest.SetRules(talkRank, kickRank, allowGuests, recruitRank)
        is ClanAction.JoinGuest -> ClanRequest.JoinGuest(clanName)
        ClanAction.LeaveGuest -> ClanRequest.LeaveGuest
    }
