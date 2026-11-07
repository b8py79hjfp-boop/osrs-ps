package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * Login sequence after the engine part (rebuild, VarpReset + vars). Order and client scripts follow RS Mod
 * `LoginScript.engineLogin` (captured from live OSRS): chat filters, welcome, chat unlock, invs, client scripts,
 * player ops, display-name script, stats and run energy (stats/energy go out at the end of the tick).
 */
public class Login : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { login() }
    }

    private fun PlayerRef.login() {
        message("Welcome to world $worldId.")
        // Chat input is locked until the client knows the display name (RS Mod `chatboxUnlocked`).
        setVarbit(Varbits.HAS_DISPLAYNAME_TRANSMITTER, 1)
        setVarbit(Varbits.OPTION_HIDE_ROOFTOPS, 1)
        transmitInv(Invs.INV)
        transmitInv(Invs.WORN)
        clientScript(CS_2498, 1, 0, 0)
        clientScript(CS_828, 1)
        clientScript(CS_5141)
        playerOp(PLAYER_OP_FOLLOW, "Follow")
        playerOp(PLAYER_OP_TRADE, "Trade with")
        playerOp(PLAYER_OP_REPORT, "Report")
        clientScript(CS_DISPLAY_NAME, tick.toInt(), 0, username, "REGULAR")
    }

    private companion object {
        // Client script ids as sent by live OSRS at login (RS Mod `LoginScript`); their purpose isn't named there.
        const val CS_2498 = 2498
        const val CS_828 = 828
        const val CS_5141 = 5141

        /** Sets the display name shown in the chatbox (RS Mod `LoginScript`: `876, mapClock, 0, name, "REGULAR"`). */
        const val CS_DISPLAY_NAME = 876

        // Player op slots (RS Mod `LoginScript.sendPlayerOps`).
        const val PLAYER_OP_FOLLOW = 3
        const val PLAYER_OP_TRADE = 4
        const val PLAYER_OP_REPORT = 8
    }
}
