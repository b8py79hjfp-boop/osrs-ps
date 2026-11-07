package tp.server

import tp.engine.ClanRequest
import tp.engine.entity.Player
import tp.protocol.api.ClanAffined
import tp.protocol.api.ClanRow
import tp.protocol.api.ServerMessage
import tp.services.ClanRules
import tp.services.SocialEvent
import tp.services.SocialService

/** Hub call for a world clan request (docs/mechanics/clans.md); null = done, else the message to show. */
internal suspend fun SocialService.clan(account: Long, r: ClanRequest): String? =
    when (r) {
        is ClanRequest.Found -> clanFound(account, r.name)
        is ClanRequest.Invite -> clanInvite(account, r.name)
        ClanRequest.Accept -> clanAccept(account)
        ClanRequest.Leave -> clanLeave(account)
        is ClanRequest.SetRank -> clanSetRank(account, r.name, r.rank)
        is ClanRequest.Kick -> clanKick(account, r.name, r.ban)
        is ClanRequest.SetRules -> clanSetRules(account, ClanRules(r.talkRank, r.kickRank, r.allowGuests, r.recruitRank))
        is ClanRequest.JoinGuest -> clanJoinGuest(account, r.clanName)
        ClanRequest.LeaveGuest -> clanLeaveGuest(account).let { null }
        is ClanRequest.Message -> clanMessage(account, r.guest, r.text)
    }

/**
 * World thread: a hub clan event for [player] (CL9-CL11). Channel and settings packets go out together; a newly
 * joined channel greets the player with the clan's system line.
 */
internal class ClanLinks(private val worldId: Int, private val nextCounter: () -> Int) {
    private var updateNum = 0L

    fun onEvent(player: Player, e: SocialEvent) {
        when (e) {
            is SocialEvent.ClanState -> {
                state(player, e)
            }

            is SocialEvent.ClanMessage -> {
                player.session.send(ServerMessage.ClanMessage(e.guest, e.from, e.worldId, nextCounter(), e.fromRights.ordinal, e.text))
            }

            is SocialEvent.ClanInvite -> {
                player.world.content.onClanInvite(player, e.from, e.clanName)
            }

            else -> {
                Unit
            }
        }
    }

    private fun state(player: Player, e: SocialEvent.ClanState) {
        val clan = e.clan
        val before = if (e.guest) player.guestClanName else player.clanName
        if (clan == null) {
            if (e.guest) player.guestClanName = null else player.clanName = null
            if (!e.guest) player.clanRank = 0
            player.session.send(ServerMessage.ClanChannelLeft(e.guest))
            player.session.send(ServerMessage.ClanSettingsLeft(e.guest))
            if (before != null) player.session.send(ServerMessage.GameMessage(if (e.guest) LEFT_GUEST else LEFT))
            return
        }
        if (e.guest) {
            player.guestClanName = clan.name
        } else {
            player.clanName = clan.name
            player.clanRank = clan.members.firstOrNull { it.accountId == player.accountId }?.rank ?: 0
        }
        val members = e.online.map { ClanRow(it.name, it.rank, it.worldId) }
        player.session.send(
            ServerMessage.ClanChannel(e.guest, clan.name, clan.rules.kickRank, clan.rules.talkRank, members, ++updateNum),
        )
        player.session.send(
            ServerMessage.ClanSettings(
                e.guest,
                clan.name,
                clan.createdMinute.toInt(),
                clan.rules.allowGuests,
                clan.rules.talkRank,
                clan.rules.kickRank,
                clan.members.map { ClanAffined(it.name, it.rank, it.joinedDay) },
                clan.banned,
                updateNum.toInt(),
            ),
        )
        if (before == null) {
            val prefix = if (e.guest) "///" else "//"
            player.session.send(ServerMessage.ClanSystemMessage(e.guest, worldId, nextCounter(), "Now talking in ${clan.name}."))
            player.session.send(ServerMessage.GameMessage("To talk in the clan channel, start each line of chat with $prefix."))
        }
    }

    private companion object {
        /** OPEN-CL5: our wording; the Wiki gives none. */
        const val LEFT = "You are no longer a member of a clan."
        const val LEFT_GUEST = "You have left the guest clan channel."
    }
}
