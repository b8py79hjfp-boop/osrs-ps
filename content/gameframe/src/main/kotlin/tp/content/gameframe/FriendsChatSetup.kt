package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.FriendsChatSettings
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces

/**
 * The friends chat side panel and its setup window (docs/mechanics/friends-chat.md FC6-FC8). Join and Leave are
 * client-side: the client asks for the name and sends FRIENDCHAT_JOIN_LEAVE. The settings live in the hub.
 */
public class FriendsChatSetup : ContentPlugin {
    override fun ContentScope.register() {
        // The channels side tab (side_channels) shows its friends chat panel in the contents layer.
        onLogin { openOverlay(Components.Side_Channels.CONTENTS, Interfaces.CHATCHANNEL_CURRENT) }
        onButton(Components.Side_Channels.TAB_0) { openOverlay(Components.Side_Channels.CONTENTS, Interfaces.CHATCHANNEL_CURRENT) }

        onButton(Components.Chatchannel_Current.SETUP) {
            channelSettings { settings -> if (settings == null) unavailable() else open(settings) }
        }
        onButton(Components.Chatchannel_Setup.OPT_PREFIX, OP_SET_PREFIX) {
            stringDialog("Enter chat prefix:") { text ->
                val name = text.trim()
                if (name.isEmpty() || name.length > MAX_NAME) {
                    message("Chat prefixes are 1-$MAX_NAME characters.")
                } else {
                    update { FriendsChatSettings(name, it.enterRank, it.talkRank, it.kickRank) }
                }
            }
        }
        onButton(Components.Chatchannel_Setup.OPT_PREFIX, OP_DISABLE) {
            update { FriendsChatSettings(null, it.enterRank, it.talkRank, it.kickRank) }
        }
        // Option lists: op n is rank n - 2 (op 1 "Anyone" = -1 ... op 9 "Only me" = 7; cache interface 94 actions).
        onButton(Components.Chatchannel_Setup.OPT_ENTER) { click ->
            update { FriendsChatSettings(it.name, rankFor(click.op), it.talkRank, it.kickRank) }
        }
        onButton(Components.Chatchannel_Setup.OPT_TALK) { click ->
            update { FriendsChatSettings(it.name, it.enterRank, rankFor(click.op), it.kickRank) }
        }
        onButton(Components.Chatchannel_Setup.OPT_KICK) { click ->
            // Kick offers Corporal+ .. Only me (ops 4..9).
            val rank = rankFor(click.op)
            if (rank >= MIN_KICK_RANK) update { FriendsChatSettings(it.name, it.enterRank, it.talkRank, rank) }
        }
    }

    private fun PlayerRef.open(settings: FriendsChatSettings) {
        openModal(Components.Toplevel.MAINMODAL, Interfaces.CHATCHANNEL_SETUP)
        show(settings)
    }

    private fun PlayerRef.show(settings: FriendsChatSettings) {
        ifText(Components.Chatchannel_Setup.OPT_PREFIX, settings.name ?: "Chat disabled")
        ifText(Components.Chatchannel_Setup.OPT_ENTER, label(settings.enterRank))
        ifText(Components.Chatchannel_Setup.OPT_TALK, label(settings.talkRank))
        ifText(Components.Chatchannel_Setup.OPT_KICK, label(settings.kickRank))
    }

    /** Reads the current settings, applies [change], stores it and refreshes the window. */
    private fun PlayerRef.update(change: (FriendsChatSettings) -> FriendsChatSettings) {
        channelSettings { current ->
            if (current == null) return@channelSettings unavailable()
            channelSettings(change(current)) { saved -> if (saved == null) unavailable() else show(saved) }
        }
    }

    private fun PlayerRef.unavailable() = message("Friends chat settings are unavailable right now.")

    private fun rankFor(op: Int): Int = (op - 2).coerceIn(ANYONE, ONLY_ME)

    private fun label(rank: Int): String = LABELS[(rank + 1).coerceIn(0, LABELS.lastIndex)]

    private companion object {
        const val OP_SET_PREFIX = 1
        const val OP_DISABLE = 2
        const val ANYONE = -1
        const val ONLY_ME = 7

        /** Corporal is the lowest kick option the setup window offers (cache interface 94 comp 19 actions). */
        const val MIN_KICK_RANK = 2

        /** Chat prefixes follow display-name length (12). */
        const val MAX_NAME = 12

        /** Option texts in rank order -1..7 (cache interface 94 actions). */
        val LABELS =
            listOf("Anyone", "Any friends", "Recruit+", "Corporal+", "Sergeant+", "Lieutenant+", "Captain+", "General+", "Only me")
    }
}
