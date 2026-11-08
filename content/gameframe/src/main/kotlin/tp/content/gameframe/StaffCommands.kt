package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * `::broadcast text` and `::update seconds` (administrators), until they move onto the registry with the administrator
 * and owner suites. Sanctions moved to content/staff (commands brief §6).
 */
public class StaffCommands : ContentPlugin {
    override fun ContentScope.register() {
        for (command in ADMIN_COMMANDS) staff(command, ADMIN)
    }

    private fun ContentScope.staff(command: String, level: tp.util.Rank) {
        onCommand(command, level) { args ->
            if (args.isEmpty()) {
                message("Usage: ::$command <${if (command == "update") "seconds" else "name"}> ...")
            } else {
                staffCommand(command, args)
            }
        }
    }

    private companion object {
        val ADMIN = tp.util.Rank.ADMINISTRATOR
        val ADMIN_COMMANDS = listOf("update")
    }
}
