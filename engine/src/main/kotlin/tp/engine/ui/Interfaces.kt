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

    /**
     * Gameframe redirects (gameframe-settings.md G8): toplevel id -> (fixed-mode component -> that toplevel's component).
     * Content always names destinations by their fixed-mode (`toplevel` 548) component; the engine sends the component of
     * the toplevel the player has open. Built once at world start from the cache's pane-redirect enums; empty = no redirects.
     */
    private var redirects: Map<Int, Map<Int, Int>> = emptyMap()

    private fun state(player: Player): State = states.getOrPut(player.slot) { State() }

    /**
     * Reads the cache's pane-redirect enums ([enum] = enum id -> its int map). They are keyed by `toplevel_osrs_stretch`
     * (161) components: enum 1129 maps them to `toplevel` (548), 1130 to 161 and 1131 to `toplevel_pre_eoc` (164)
     * (`cache-dump config enum 1129`; RS Mod `fixed_pane_redirect` / `resizable_basic_pane_redirect` /
     * `side_panels_resizable_pane_redirect`).
     */
    fun loadRedirects(enum: (Int) -> Map<Int, Int>) {
        val fixedToBase = HashMap<Int, Int>()
        for ((base, fixedComponent) in enum(ENUM_FIXED)) if (fixedComponent >= 0) fixedToBase.putIfAbsent(fixedComponent, base)
        redirects =
            mapOf(TOP_STRETCH to enum(ENUM_STRETCH), TOP_PRE_EOC to enum(ENUM_PRE_EOC)).mapValues { (_, map) ->
                fixedToBase.mapNotNull { (fixedComponent, base) -> map[base]?.takeIf { it >= 0 }?.let { fixedComponent to it } }.toMap()
            }
    }

    /** The component [destination] (named by its fixed-mode id) is in [top]. */
    private fun translate(top: Int, destination: Int): Int = redirects[top]?.get(destination) ?: destination

    fun openTop(player: Player, interfaceId: Int) {
        state(player).top = interfaceId
        player.session.send(ServerMessage.OpenTop(interfaceId))
    }

    /** The player's toplevel interface id (-1 before the gameframe opens). */
    fun top(player: Player): Int = states[player.slot]?.top ?: -1

    /**
     * G8: switches the toplevel and moves every open sub-interface to its component in the new one (IF_OPENTOP, then
     * IF_MOVESUB per sub, RS Mod `moveGameframe`); nothing is closed, the modal stays open.
     */
    fun moveTop(player: Player, interfaceId: Int) {
        val s = state(player)
        val old = s.top
        if (old == interfaceId) return
        s.top = interfaceId
        player.session.send(ServerMessage.OpenTop(interfaceId))
        for (destination in s.subs.keys) {
            val from = translate(old, destination)
            val to = translate(interfaceId, destination)
            if (from ushr COMPONENT_SHIFT == old && to ushr COMPONENT_SHIFT == interfaceId) {
                player.session.send(ServerMessage.MoveSub(from, to))
            }
        }
    }

    fun openOverlay(player: Player, destination: Int, interfaceId: Int) {
        val s = state(player)
        s.subs[destination] = interfaceId
        player.session.send(ServerMessage.OpenSub(translate(s.top, destination), interfaceId, overlay = true))
    }

    /** Closes the overlay in [destination] (a HUD left behind on leaving an area). */
    fun closeOverlay(player: Player, destination: Int) {
        val s = states[player.slot] ?: return
        if (s.subs.remove(destination) == null || destination == s.modalDestination) return
        player.session.send(ServerMessage.CloseSub(translate(s.top, destination)))
    }

    /** Opens a modal (closing any current one first: Q7). */
    fun openModal(player: Player, destination: Int, interfaceId: Int) {
        player.closeModal()
        val s = state(player)
        s.subs[destination] = interfaceId
        s.modalDestination = destination
        player.modal = interfaceId
        player.session.send(ServerMessage.OpenSub(translate(s.top, destination), interfaceId, overlay = false))
    }

    /** Called by [Player.closeModal]; sends the close. */
    internal fun closeModal(player: Player) {
        val s = states[player.slot] ?: return
        if (s.modalDestination == -1) return
        val closed = s.subs.remove(s.modalDestination)
        player.session.send(ServerMessage.CloseSub(translate(s.top, s.modalDestination)))
        s.modalDestination = -1
        if (closed != null) player.world.content.onModalClosed(player, closed)
    }

    /** Whether [interfaceId] is currently open for [player] (as toplevel or in any destination). */
    fun isOpen(player: Player, interfaceId: Int): Boolean {
        val s = states[player.slot] ?: return false
        return s.top == interfaceId || interfaceId in s.subs.values
    }

    internal fun forget(player: Player) {
        states.remove(player.slot)
    }

    private companion object {
        /** Packed component: interface id in the high 16 bits. */
        const val COMPONENT_SHIFT = 16

        /** `toplevel_osrs_stretch` (resizable classic) and `toplevel_pre_eoc` (resizable modern) interface ids. */
        const val TOP_STRETCH = 161
        const val TOP_PRE_EOC = 164

        /** Cache pane-redirect enums (see [loadRedirects]). */
        const val ENUM_FIXED = 1129
        const val ENUM_STRETCH = 1130
        const val ENUM_PRE_EOC = 1131
    }
}
