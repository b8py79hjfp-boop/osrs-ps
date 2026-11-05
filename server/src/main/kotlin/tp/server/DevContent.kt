package tp.server

import tp.engine.Player
import tp.engine.World
import tp.engine.WorldHooks
import tp.protocol.api.ServerMessage
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.util.CoordGrid

/**
 * Phase 2 content stand-in until plugin-api exists (Phase 3): the fixed-mode gameframe and a few dev cheats.
 * `::tele x z [level]`, `::pos`, `::run` (toggle run mode).
 */
internal object DevContent : WorldHooks {
    override fun onLogin(world: World, player: Player) {
        val s = player.session
        s.send(ServerMessage.OpenTop(Interfaces.TOPLEVEL))
        s.send(ServerMessage.OpenSub(Components.Toplevel.CHAT_CONTAINER, Interfaces.CHATBOX, overlay = true))
        s.send(ServerMessage.OpenSub(Components.Toplevel.ORBS, Interfaces.ORBS, overlay = true))
        s.send(ServerMessage.GameMessage("Welcome to world ${world.config.id}. Dev commands: ::tele x z [level], ::pos, ::run"))
    }

    override fun onCheat(world: World, player: Player, command: String) {
        val parts = command.trim().split(Regex("\\s+"))
        when (parts.firstOrNull()?.lowercase()) {
            "pos" -> {
                player.session.send(ServerMessage.GameMessage("${player.coord}"))
            }

            "run" -> {
                world.setRunMode(player, !player.runMode)
                player.session.send(ServerMessage.GameMessage("Run: ${player.runMode}"))
            }

            "tele" -> {
                val x = parts.getOrNull(1)?.toIntOrNull()
                val z = parts.getOrNull(2)?.toIntOrNull()
                val level = parts.getOrNull(TELE_LEVEL_ARG)?.toIntOrNull() ?: player.coord.level
                if (x == null || z == null) {
                    player.session.send(ServerMessage.GameMessage("Usage: ::tele x z [level]"))
                } else {
                    world.teleport(player, CoordGrid(x, z, level))
                }
            }

            else -> {
                player.session.send(ServerMessage.GameMessage("Unknown command: $command"))
            }
        }
    }

    private const val TELE_LEVEL_ARG = 3
}
