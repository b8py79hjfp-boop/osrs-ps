package tp.content.raids.toa

import tp.content.raids.Party
import tp.content.raids.PartyState
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Tombs lobby's boards (toa.md TA2-TA4, TA6; toa/client-contract.md 2.2-2.4, 7.2): the Grouping Obelisk opens the
 * party list ([ToaBoard]), the Invocation Board the read-only invocations list, and the lobby overlay 773 shows the
 * party status while the player stands in region 13454. State lives in raids-core's [tp.content.raids.Party] and in
 * [ToaLobbyWorld]; plugin instances are shared by every world, so there are no fields here.
 */
public class ToaPartyUi : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.TOA_GROUPING_BOARD, OP1) { ToaBoard.openList(player) }
        onOpLoc(Locs.TOA_INVOCATION_BOARD, OP1) { ToaBoard.openInvocationBoard(player) }
        onButton(Components.Toa_Partylist.CONTENTS, PAUSE_BUTTON_OP) { ToaBoard.listButton(this, it.slot) }
        onButton(Components.Toa_Partylist.LIST, PAUSE_BUTTON_OP) { ToaBoard.listRow(this, it.slot) }
        onButton(Components.Toa_Partydetails.PAUSEBUTTONS, PAUSE_BUTTON_OP) { ToaBoard.detailsButton(this, it.slot) }
        onButton(Components.Toa_Partydetails.PRESETS_BUTTON_CLICK, OP1) { ToaBoard.presetClick(this, it.slot, clear = false) }
        onButton(Components.Toa_Partydetails.PRESETS_BUTTON_CLICK, OP2) { ToaBoard.presetClick(this, it.slot, clear = true) }
        onLogin {
            ToaBoard.setController(this, ToaBoard.partyOf(this))
            timer(LOBBY_CHECK, 1, soft = true) { ToaLobby.tick(this) }
        }
        onLogout { ToaBoard.logout(this) }
    }

    private companion object {
        val LOBBY_CHECK = TimerId("toa_lobby_check")
        const val OP1 = 1
        const val OP2 = 2

        /** The op the engine gives a pause-button click that no dialogue script waits for (World `PAUSE_BUTTON_OP`). */
        const val PAUSE_BUTTON_OP = 1
    }
}

private val IN_LOBBY = AttrKey<Boolean>("toa_in_lobby")

/** The lobby overlay 773 (client-contract 2.2): opened only inside the lobby region, closed on entry. */
internal object ToaLobby {
    /** Every tick for each player: a region test and one attribute compare, no allocation. */
    fun tick(p: PlayerRef) {
        val inside = inLobby(p.coord)
        if (inside != (p[IN_LOBBY] == true)) {
            p[IN_LOBBY] = inside
            p.areaQueue { if (inside) enter(this) else leave(this) }
        } else if (inside && p.tick % REFRESH_TICKS == 0L) {
            // Parties change under the player (the leader starts the run, a closed run returns the party).
            ToaBoard.setController(p, ToaBoard.partyOf(p))
        }
    }

    /** Region id (squareX << 8 | squareZ) 13454, the lobby inside the pyramid (client-contract 6.1). */
    private fun inLobby(c: CoordGrid): Boolean = ((c.x ushr SQUARE_SHIFT) shl REGION_ROW_SHIFT or (c.z ushr SQUARE_SHIFT)) == LOBBY_REGION

    private fun enter(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TOA_HUD) && !p.isOpen(Interfaces.TOA_LOBBY)) {
            p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.TOA_LOBBY)
        }
        ToaBoard.setController(p, ToaBoard.partyOf(p))
        syncStatus(p)
    }

    private fun leave(p: PlayerRef) {
        // Entering the raid replaces 773 with the HUD 481 in the same slot; only close what is still ours.
        if (p.isOpen(Interfaces.TOA_LOBBY)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
        if (p.varbit(Varbits.TOA_CLIENT_PARTYSTATUS) != STATUS_NONE) p.setVarbit(Varbits.TOA_CLIENT_PARTYSTATUS, STATUS_NONE)
    }

    /** Sends `TOA_CLIENT_PARTYSTATUS` (script 6613) when it changed and the overlay is up. */
    fun syncStatus(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TOA_LOBBY)) return
        val status = statusOf(p, ToaBoard.partyOf(p))
        if (p.varbit(Varbits.TOA_CLIENT_PARTYSTATUS) != status) p.setVarbit(Varbits.TOA_CLIENT_PARTYSTATUS, status)
    }

    /** 0 no party, 1 party, 2 when the leader's run has started and this member has not stepped inside (client-contract 2.2). */
    private fun statusOf(p: PlayerRef, party: Party?): Int {
        if (party == null) return STATUS_NONE
        val run = (party.state as? PartyState.Locked)?.run ?: return STATUS_PARTY
        val waiting =
            run.instance != null &&
                run.state is RunState.InRoom &&
                run.roomsTaken.size == 1 &&
                run.member(p.username)?.inside == false
        return if (waiting) STATUS_STEP_INSIDE else STATUS_PARTY
    }

    /** Pixels of a map square are 64 tiles: x >> 6 is the square (client-contract 6.1). */
    private const val SQUARE_SHIFT = 6
    private const val REGION_ROW_SHIFT = 8

    /** The lobby inside the pyramid, `REGION_LOBBY` of the RuneLite plugins (client-contract 6.1). */
    private const val LOBBY_REGION = 13454

    /** Ticks between status refreshes in the lobby (3 seconds); changes made through the board apply at once. */
    private const val REFRESH_TICKS = 5L

    private const val STATUS_NONE = 0
    private const val STATUS_PARTY = 1
    private const val STATUS_STEP_INSIDE = 2
}
