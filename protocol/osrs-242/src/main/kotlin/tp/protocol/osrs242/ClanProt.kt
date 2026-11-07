package tp.protocol.osrs242

import net.rsprot.protocol.game.outgoing.clan.ClanChannelFull
import net.rsprot.protocol.game.outgoing.clan.ClanSettingsFull
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannel
import net.rsprot.protocol.game.outgoing.clan.MessageClanChannelSystem
import net.rsprot.protocol.message.OutgoingGameMessage
import tp.protocol.api.ServerMessage

/** clanType of the player's own clan (RuneLite ClanID.CLAN). */
private const val CLAN_TYPE_MAIN = 0

/** clanType of the guest channel (OPEN-CL4: the client keeps guest clans under a negative id). */
private const val CLAN_TYPE_GUEST = -1

/** The client keeps no loot/coin share in OSRS; the field takes "nobody" (JMOD rank 127). */
private const val NO_SHARE_RANK = 127

private fun type(guest: Boolean) = if (guest) CLAN_TYPE_GUEST else CLAN_TYPE_MAIN

/** docs/mechanics/clans.md CL9-CL11: clan channel, settings and chat. */
internal fun clanProt(message: ServerMessage): OutgoingGameMessage =
    when (message) {
        is ServerMessage.ClanChannel -> {
            ClanChannelFull(
                type(message.guest),
                ClanChannelFull.JoinUpdate(
                    message.clanName
                        .lowercase()
                        .hashCode()
                        .toLong(),
                    message.updateNum,
                    message.clanName,
                    false,
                    message.kickRank,
                    message.talkRank,
                    message.members.map { ClanChannelFull.ClanMember(it.name, it.rank, it.worldId) },
                ),
            )
        }

        is ServerMessage.ClanChannelLeft -> {
            ClanChannelFull(type(message.guest), ClanChannelFull.LeaveUpdate)
        }

        is ServerMessage.ClanSettings -> {
            ClanSettingsFull(
                type(message.guest),
                ClanSettingsFull.JoinUpdate(
                    message.updateNum,
                    message.createdMinute,
                    message.clanName,
                    message.allowGuests,
                    message.talkRank,
                    message.kickRank,
                    NO_SHARE_RANK,
                    NO_SHARE_RANK,
                    message.members.map { ClanSettingsFull.AffinedClanMember(it.name, it.rank, 0, it.joinedDay, false) },
                    message.banned.map { ClanSettingsFull.BannedClanMember(it) },
                    emptyList(),
                ),
            )
        }

        is ServerMessage.ClanSettingsLeft -> {
            ClanSettingsFull(type(message.guest), ClanSettingsFull.LeaveUpdate)
        }

        is ServerMessage.ClanMessage -> {
            MessageClanChannel(type(message.guest), message.from, message.worldId, message.counter, message.crown, message.text)
        }

        is ServerMessage.ClanSystemMessage -> {
            MessageClanChannelSystem(type(message.guest), message.worldId, message.counter, message.text)
        }

        else -> {
            error("not a clan message: $message")
        }
    }
