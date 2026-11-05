package tp.server

import tp.engine.ContentHandlers
import tp.engine.entity.Player
import tp.protocol.api.ServerMessage
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.util.CoordGrid

/**
 * Stand-in content until plugin-api lands (Phase 3, task 4): the fixed-mode gameframe and dev commands
 * `::tele x z [level]`, `::pos`, `::run`.
 */
internal object DevContent : ContentHandlers {
    override fun onLogin(player: Player) {
        val ui = player.world.interfaces
        ui.openTop(player, Interfaces.TOPLEVEL)
        ui.openOverlay(player, Components.Toplevel.CHAT_CONTAINER, Interfaces.CHATBOX)
        ui.openOverlay(player, Components.Toplevel.ORBS, Interfaces.ORBS)
        message(player, "Welcome to world ${player.world.config.id}. Dev commands: ::tele x z [level], ::pos, ::run")
    }

    override fun onCommand(player: Player, command: String, args: List<String>): Boolean {
        when (command) {
            "pos" -> {
                message(player, "${player.coord} tick ${player.world.tick} pid ${player.world.pidOf(player.slot)}")
            }

            "run" -> {
                player.world.setRunMode(player, !player.runMode)
                message(player, "Run: ${player.runMode}")
            }

            "tele" -> {
                val x = args.getOrNull(0)?.toIntOrNull()
                val z = args.getOrNull(1)?.toIntOrNull()
                val level = args.getOrNull(2)?.toIntOrNull() ?: player.coord.level
                if (x == null ||
                    z == null
                ) {
                    message(player, "Usage: ::tele x z [level]")
                } else {
                    player.world.teleport(player, CoordGrid(x, z, level))
                }
            }

            else -> {
                return false
            }
        }
        return true
    }

    private fun message(player: Player, text: String) = player.session.send(ServerMessage.GameMessage(text))
}
