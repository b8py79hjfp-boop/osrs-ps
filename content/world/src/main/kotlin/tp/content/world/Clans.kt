package tp.content.world

import tp.plugin.AttrKey
import tp.plugin.ClanAction
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Clans (docs/mechanics/clans.md): the clan and guest side tabs, founding at the Clan Hub clerk with the creation side
 * panel, "Recruit" on nearby players in the Clan Hub or with a clan vexillum, and the invitation prompt. The clan
 * itself (members, ranks, chat) lives in the hub.
 */
public class Clans : ContentPlugin {
    override fun ContentScope.register() {
        // CL9: the channels side tab: 1 = your clan, 2 = guest clan, 3 = grouping.
        onButton(Components.Side_Channels.TAB_1) { openOverlay(Components.Side_Channels.CONTENTS, Interfaces.CLANS_SIDEPANEL) }
        onButton(Components.Side_Channels.TAB_2) { openOverlay(Components.Side_Channels.CONTENTS, Interfaces.CLANS_GUEST_SIDEPANEL) }
        onButton(Components.Side_Channels.TAB_3) { openOverlay(Components.Side_Channels.CONTENTS, Interfaces.GROUPING) }

        // CL1: talking to the clerk while clanless opens the creation side panel, then his usual dialogue.
        onOpNpc(Npcs.CLAN_CLERK, 1) { npc ->
            if (player.clanName == null) player.openOverlay(Components.Side_Channels.CONTENTS, Interfaces.CLANS_CREATION_SIDEPANEL)
            talkTranscript(npc)
        }
        onButton(Components.Clans_Creation_Sidepanel.NAME) {
            stringDialog("Enter a name for your clan:") { name ->
                clan(ClanAction.Found(name)) { err ->
                    if (err != null) return@clan message(err)
                    this[FOUNDING] = name.trim()
                    message("Invite four founding members to form ${name.trim()}.")
                }
            }
        }
        onButton(Components.Clans_Creation_Sidepanel.INVITE) { invitePrompt() }
        onButton(Components.Clans_Creation_Sidepanel.CONFIRM) {
            // CL1: the clan forms when the fourth founder accepts; the panel button only reminds the founder.
            message(if (this[FOUNDING] == null) "Choose a name for your clan first." else FORMS_ON_ACCEPT)
        }
        onButton(Components.Clans_Creation_Sidepanel.CANCEL) {
            this[FOUNDING] = null
            openOverlay(Components.Side_Channels.CONTENTS, Interfaces.CLANS_SIDEPANEL)
        }

        // CL6: the guest panel's search box joins a clan's channel as a guest; again to leave it.
        onButton(Components.Clans_Guest_Sidepanel.SEARCH) {
            if (guestClanName != null) {
                clan(ClanAction.LeaveGuest)
            } else {
                stringDialog("Enter the name of the clan to join as a guest:") { name -> clan(ClanAction.JoinGuest(name.trim())) }
            }
        }

        // CL2: "Recruit" near the clerk or with a vexillum worn.
        onLogin { timer(RECRUIT_TIMER, 1, soft = true) { updateRecruit() } }
        onOpPlayer(RECRUIT_OP) { target ->
            if (!canRecruit(player)) return@onOpPlayer
            player.clan(ClanAction.Invite(target.username)) { err ->
                message(err ?: "You have invited ${target.username} to join your clan.")
            }
        }

        // CL3: the invitation (to join or to found a clan).
        onClanInvite { from, clanName ->
            weakQueue(0) {
                val pick = choice("$from invites you to join $clanName.", "Accept the invitation.", "Decline.")
                if (pick == 1) player.clan(ClanAction.Accept) { err -> message(err ?: "You have accepted the invitation to $clanName.") }
            }
        }
    }

    private fun PlayerRef.invitePrompt() {
        if (this[FOUNDING] == null) return message("Choose a name for your clan first.")
        stringDialog("Enter the name of the player to invite:") { name ->
            clan(ClanAction.Invite(name.trim())) { err -> message(err ?: "You have invited ${name.trim()} to found the clan.") }
        }
    }

    private fun PlayerRef.updateRecruit() {
        val can = canRecruit(this)
        if (this[RECRUITING] == can) return
        this[RECRUITING] = can
        playerOp(RECRUIT_OP, if (can) "Recruit" else null)
    }

    private fun canRecruit(p: PlayerRef): Boolean {
        if (p.clanName == null && p[FOUNDING] == null) return false
        val c = p.coord
        val inHub = c.level == 0 && c.x in HUB_MIN_X..HUB_MAX_X && c.z in HUB_MIN_Z..HUB_MAX_Z
        return inHub || (0 until WORN_SLOTS).any { p.inv(Invs.WORN).obj(it) in VEXILLUMS }
    }

    private companion object {
        val FOUNDING = AttrKey<String>("clan_founding")
        val RECRUITING = AttrKey<Boolean>("clan_recruit_op")
        val RECRUIT_TIMER = TimerId("clan_recruit_op")

        /** OPEN-CL6: the slot of "Recruit" (free slots 5..7; RS Mod has no clans). */
        const val RECRUIT_OP = 6

        /** OPEN-CL7: Clan Hub bounds around the Wiki map point 3180,3474 (Grand Exchange south-east corner). */
        const val HUB_MIN_X = 3172
        const val HUB_MAX_X = 3190
        const val HUB_MIN_Z = 3464
        const val HUB_MAX_Z = 3480

        /** Worn inventory size (14 slots, cache inv `worn`). */
        const val WORN_SLOTS = 14

        val VEXILLUMS =
            setOf(
                Objs.CLAN_VEXILLUM_BLACK,
                Objs.CLAN_VEXILLUM_ORANGE,
                Objs.CLAN_VEXILLUM_BLUE,
                Objs.CLAN_VEXILLUM_RED,
                Objs.CLAN_VEXILLUM_GREEN,
                Objs.CLAN_VEXILLUM_YELLOW,
                Objs.CLAN_VEXILLUM_LIGHTBLUE,
                Objs.CLAN_VEXILLUM_PINK,
            )

        const val FORMS_ON_ACCEPT = "Your clan is formed once four founding members accept your invitations."
    }
}
