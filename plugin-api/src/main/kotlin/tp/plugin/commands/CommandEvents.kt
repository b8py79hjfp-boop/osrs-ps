package tp.plugin.commands

import tp.plugin.Event
import tp.plugin.PlayerRef

/**
 * Long command output (commands brief §2: lists, histories, drop tables) for the `::commands` interface to show.
 * Unhandled, the first [CHAT_ROWS] rows go to chat.
 */
public class CommandListView(public val player: PlayerRef, public val title: String, public val rows: List<String>) : Event {
    public var handled: Boolean = false

    public companion object {
        /** Rows shown in chat when no interface takes the list. */
        public const val CHAT_ROWS: Int = 10
    }
}

/** A staff member acting from the control panel: no body in the world, replies go to the panel (panel brief §3). */
public val PlayerRef.isRemote: Boolean get() = p.remote

/**
 * Shows [rows] under [title]: in the interface, or the first rows in chat. A [private] list (IP addresses, device
 * ids, alt links: commands brief §8) never falls back to chat.
 */
public fun PlayerRef.showList(title: String, rows: List<String>, private: Boolean = false) {
    if (isRemote) {
        // The panel shows every row to the signed-in actor only (panel brief §3); no interface to open.
        message("$title (${rows.size}):")
        rows.forEach(::message)
        return
    }
    val view = CommandListView(this, title, rows)
    events.publish(view)
    if (view.handled) return
    if (private) return message("$title: open ::commands to view it (it is never shown in chat).")
    message("$title (${rows.size}):")
    for (row in rows.take(CommandListView.CHAT_ROWS)) message(row)
    if (rows.size > CommandListView.CHAT_ROWS) message("... and ${rows.size - CommandListView.CHAT_ROWS} more.")
}

/** `::skull` (commands brief §6): the PvP module skulls [player] as if they had attacked someone. */
public class SkullRequest(public val player: PlayerRef) : Event {
    public var handled: Boolean = false
}

/** The hub changed [player]'s sanctions (commands brief §7); [before] is what was in force. */
public class SanctionsChanged(public val player: PlayerRef, public val before: tp.util.Sanctions) : Event

/** A moderator's `::warn` reached [player] (commands brief §6). */
public class Warned(public val player: PlayerRef, public val reason: String) : Event

/** [player]'s rank changed live (commands brief §8); [before] was the old one. A demotion closes staff views. */
public class RankChanged(public val player: PlayerRef, public val before: tp.util.Rank) : Event
