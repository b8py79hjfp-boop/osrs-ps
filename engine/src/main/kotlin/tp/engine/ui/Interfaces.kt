package tp.engine.ui

import tp.engine.entity.Player
import tp.protocol.api.ServerMessage

/**
 * IF3 interface state per player: the toplevel, and what is open in each destination component (modal or overlay).
 * Validation (brief §12) asks [isOpen] before any button handler runs. Opening or closing an interface is an
 * interruption that clears weak scripts (scripts.md Q7).
 */
class Interfaces {
    private class State {
        var top: Int = -1
        val subs = HashMap<Int, Int>() // destination component -> interface id
        var modalDestination: Int = -1
    }

    private val states = HashMap<Int, State>()

    private fun state(player: Player): State = states.getOrPut(player.slot) { State() }

    fun openTop(player: Player, interfaceId: Int) {
        state(player).top = interfaceId
        player.session.send(ServerMessage.OpenTop(interfaceId))
    }

    fun openOverlay(player: Player, destination: Int, interfaceId: Int) {
        state(player).subs[destination] = interfaceId
        player.session.send(ServerMessage.OpenSub(destination, interfaceId, overlay = true))
    }

    /** Opens a modal (closing any current one first: Q7). */
    fun openModal(player: Player, destination: Int, interfaceId: Int) {
        player.closeModal()
        val s = state(player)
        s.subs[destination] = interfaceId
        s.modalDestination = destination
        player.modal = interfaceId
        player.session.send(ServerMessage.OpenSub(destination, interfaceId, overlay = false))
    }

    /** Called by [Player.closeModal]; sends the close. */
    internal fun closeModal(player: Player) {
        val s = states[player.slot] ?: return
        if (s.modalDestination == -1) return
        s.subs.remove(s.modalDestination)
        player.session.send(ServerMessage.CloseSub(s.modalDestination))
        s.modalDestination = -1
    }

    /** Whether [interfaceId] is currently open for [player] (as toplevel or in any destination). */
    fun isOpen(player: Player, interfaceId: Int): Boolean {
        val s = states[player.slot] ?: return false
        return s.top == interfaceId || interfaceId in s.subs.values
    }

    internal fun forget(player: Player) {
        states.remove(player.slot)
    }
}
