package tp.services

import tp.services.grpc.ChannelMember as PbMember
import tp.services.grpc.ClanInvite as PbClanInvite
import tp.services.grpc.ClanMemberRow as PbClanMemberRow
import tp.services.grpc.ClanMessage as PbClanMessage
import tp.services.grpc.ClanRecord as PbClanRecord
import tp.services.grpc.ClanRules as PbClanRules
import tp.services.grpc.ClanState as PbClanState

/** Clan model <-> gRPC messages, shared by [GrpcHubClient] and the hub's gRPC server. */
object ClanPb {
    fun rules(r: ClanRules): PbClanRules =
        PbClanRules
            .newBuilder()
            .setTalkRank(r.talkRank)
            .setKickRank(r.kickRank)
            .setAllowGuests(r.allowGuests)
            .setRecruitRank(r.recruitRank)
            .build()

    fun rules(r: PbClanRules): ClanRules = ClanRules(r.talkRank, r.kickRank, r.allowGuests, r.recruitRank)

    private fun record(c: ClanRecord): PbClanRecord =
        PbClanRecord
            .newBuilder()
            .setId(c.id)
            .setName(c.name)
            .setCreatedMinute(c.createdMinute)
            .addAllMembers(
                c.members.map {
                    PbClanMemberRow
                        .newBuilder()
                        .setAccountId(it.accountId)
                        .setName(it.name)
                        .setRank(it.rank)
                        .setJoinedDay(it.joinedDay)
                        .build()
                },
            ).addAllBanned(c.banned)
            .setRules(rules(c.rules))
            .build()

    private fun record(c: PbClanRecord): ClanRecord =
        ClanRecord(
            c.id,
            c.name,
            c.createdMinute,
            c.membersList.map { ClanMemberRow(it.accountId, it.name, it.rank, it.joinedDay) },
            c.bannedList.toList(),
            rules(c.rules),
        )

    fun state(e: SocialEvent.ClanState): PbClanState {
        val b = PbClanState.newBuilder().setGuest(e.guest)
        e.clan?.let { b.setClan(record(it)) }
        e.online.forEach {
            b.addOnline(
                PbMember
                    .newBuilder()
                    .setName(it.name)
                    .setRank(it.rank)
                    .setWorldId(it.worldId),
            )
        }
        return b.build()
    }

    fun state(to: Long, s: PbClanState): SocialEvent.ClanState =
        SocialEvent.ClanState(
            to,
            s.guest,
            if (s.hasClan()) record(s.clan) else null,
            s.onlineList.map { ClanChannelMember(it.name, it.rank, it.worldId) },
        )

    fun message(e: SocialEvent.ClanMessage): PbClanMessage =
        PbClanMessage
            .newBuilder()
            .setGuest(e.guest)
            .setClanName(e.clanName)
            .setFrom(e.from)
            .setFromRights(e.fromRights.name)
            .setRank(e.rank)
            .setWorldId(e.worldId)
            .setText(e.text)
            .build()

    fun message(to: Long, m: PbClanMessage): SocialEvent.ClanMessage =
        SocialEvent.ClanMessage(to, m.guest, m.clanName, m.from, Rights.valueOf(m.fromRights), m.rank, m.worldId, m.text)

    fun invite(e: SocialEvent.ClanInvite): PbClanInvite =
        PbClanInvite
            .newBuilder()
            .setFrom(e.from)
            .setClanName(e.clanName)
            .build()

    fun invite(to: Long, i: PbClanInvite): SocialEvent.ClanInvite = SocialEvent.ClanInvite(to, i.from, i.clanName)
}
