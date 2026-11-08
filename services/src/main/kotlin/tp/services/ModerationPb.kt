package tp.services

import tp.services.grpc.LookupReply
import tp.services.grpc.ModRecordPb
import tp.services.grpc.SanctionsPb
import tp.util.ModRecord
import tp.util.PlayerCard
import tp.util.Rank
import tp.util.RecordType
import tp.util.Sanctions

// Moderation wire mapping (commands brief §7), shared by the hub's gRPC server and the world's client.

fun Sanctions.toPb(): SanctionsPb =
    SanctionsPb
        .newBuilder()
        .setMutedUntil(mutedUntil)
        .setMuteReason(muteReason)
        .setYellMutedUntil(yellMutedUntil)
        .setYellMuteReason(yellMuteReason)
        .setJailedUntil(jailedUntil)
        .setJailReason(jailReason)
        .build()

fun SanctionsPb.toSanctions(): Sanctions = Sanctions(mutedUntil, muteReason, yellMutedUntil, yellMuteReason, jailedUntil, jailReason)

fun ModRecord.toPb(): ModRecordPb =
    ModRecordPb
        .newBuilder()
        .setId(id)
        .setAccountId(accountId)
        .setType(type.name)
        .setActor(actor)
        .setReason(reason)
        .setStart(start)
        .setExpires(expires)
        .setScope(scope)
        .setLiftedBy(liftedBy)
        .setLiftedAt(liftedAt)
        .build()

fun ModRecordPb.toRecord(): ModRecord =
    ModRecord(id, accountId, RecordType.valueOf(type), actor, reason, start, expires, scope, liftedBy, liftedAt)

fun PlayerCard.toPb(): LookupReply =
    LookupReply
        .newBuilder()
        .setAccountId(accountId)
        .setName(name)
        .setRank(rank.stored)
        .setWorld(world)
        .setSanctions(sanctions.toPb())
        .setBannedUntil(bannedUntil)
        .setRecords(records)
        .setLastIp(lastIp)
        .setDonator(donator.name)
        .setDonatedCents(donatedCents)
        .build()

fun LookupReply.toCard(): PlayerCard =
    PlayerCard(
        accountId,
        name,
        Rank.parseOrPlayer(rank),
        world,
        sanctions.toSanctions(),
        bannedUntil,
        records,
        lastIp,
        tp.util.DonatorTier.parse(donator) ?: tp.util.DonatorTier.NONE,
        donatedCents,
    )
