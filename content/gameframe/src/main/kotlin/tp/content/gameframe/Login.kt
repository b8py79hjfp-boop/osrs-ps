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
        // New-account defaults only: a setting the player changed later is saved with their varps and kept.
        if (saved(DEFAULTS_KEY) == null) {
            setVarbit(Varbits.OPTION_HIDE_ROOFTOPS, 1)
            setSaved(DEFAULTS_KEY, 1L)
        }
        transmitInv(Invs.INV)
        transmitInv(Invs.WORN)
        clientScript(CS_2498, 1, 0, 0)
        clientScript(CS_828, 1)
        // RS Mod also sends script 5141 with no arguments; in rev 242 it is a key handler taking two ints, so it is not sent.
        // CS 626: sets the camera zoom limits (varcs 1338-1341 = 128..896) that CS 42 `camera_do_zoom` clamps to;
        // without it the scroll wheel cannot zoom (RS Mod / OpenRune `LoginScript` sends it at login).
        clientScript(CS_ZOOM_LIMITS)
        playerOp(PLAYER_OP_FOLLOW, "Follow")
        playerOp(PLAYER_OP_TRADE, "Trade with")
        playerOp(PLAYER_OP_REPORT, "Report")
        clientScript(CS_DISPLAY_NAME, tick.toInt(), 0, username, "REGULAR")
    }

    private companion object {
        // Client script ids as sent by live OSRS at login (RS Mod `LoginScript`); their purpose isn't named there.
        const val CS_2498 = 2498
        const val CS_828 = 828
        const val CS_ZOOM_LIMITS = 626

        /** Saved once the new-account setting defaults were applied. */
        const val DEFAULTS_KEY = "login.defaults"

        /** Sets the display name shown in the chatbox (RS Mod `LoginScript`: `876, mapClock, 0, name, "REGULAR"`). */
        const val CS_DISPLAY_NAME = 876

        // Player op slots (RS Mod `LoginScript.sendPlayerOps`).
        const val PLAYER_OP_FOLLOW = 3
        const val PLAYER_OP_TRADE = 4
        const val PLAYER_OP_REPORT = 8
    }
}
