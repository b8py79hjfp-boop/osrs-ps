package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * Staff commands (brief §12: kick, mute, ban account/IP, broadcasts, system update). Rights are data on the account;
 * the hub re-checks them and writes every action to the audit log.
 * `::kick name`, `::mute name [minutes] [reason]`, `::ban name [minutes] [reason]` (0 = permanent), `::ipban name`,
 * `::unmute name`, `::unban name`, `::broadcast text` and `::update seconds` (administrators only).
 */
public class StaffCommands : ContentPlugin {
    override fun ContentScope.register() {
        for (command in MODERATOR_COMMANDS) staff(command, MODERATOR)
        for (command in ADMIN_COMMANDS) staff(command, ADMIN)
    }

    private fun ContentScope.staff(command: String, level: tp.util.Rank) {
        onCommand(command) { args ->
            if (!rank.atLeast(level)) {
                message("You do not have permission to use that command.")
            } else if (args.isEmpty()) {
                message("Usage: ::$command <${if (command == "update") "seconds" else "name"}> ...")
            } else {
                staffCommand(command, args)
            }
        }
    }

    private companion object {
        val MODERATOR = tp.util.Rank.MODERATOR
        val ADMIN = tp.util.Rank.ADMINISTRATOR
        val MODERATOR_COMMANDS = listOf("kick", "mute", "unmute")
        val ADMIN_COMMANDS = listOf("ban", "ipban", "unban", "broadcast", "update")
    }
}
