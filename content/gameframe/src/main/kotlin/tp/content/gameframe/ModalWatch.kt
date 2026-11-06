package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.plugin.TimerId

/**
 * Detects that a main modal closed. plugin-api has no close hook (the client's close request, another modal or an
 * interrupt all just close it), so a modal that borrowed state (the inventory side tab, a temporary inventory) polls
 * with a soft timer once per tick and gives the state back as soon as its interface is gone. At most one watch runs per
 * player; starting another one first finishes the previous (its modal was necessarily replaced).
 */
internal object ModalWatch {
    private val TIMER = TimerId("gameframe_modal_watch")
    private val CURRENT = AttrKey<Watch>("gameframe_modal_watch")

    private class Watch(val interfaceId: Int, val whileOpen: PlayerRef.() -> Unit, val onClose: PlayerRef.() -> Unit)

    /** Watches [interfaceId]: [whileOpen] runs every tick it stays open, [onClose] once when it is no longer open. */
    fun start(player: PlayerRef, interfaceId: Int, whileOpen: PlayerRef.() -> Unit = {}, onClose: PlayerRef.() -> Unit) {
        finish(player)
        player[CURRENT] = Watch(interfaceId, whileOpen, onClose)
        player.timer(TIMER, 1, soft = true) { poll() }
    }

    /** Runs the pending [Watch.onClose] now (a new modal replaced it, or the player is leaving). */
    fun finish(player: PlayerRef) {
        val watch = player[CURRENT] ?: return
        player[CURRENT] = null
        player.stopTimer(TIMER)
        watch.onClose(player)
    }

    private fun PlayerRef.poll() {
        val watch = this[CURRENT] ?: return stopTimer(TIMER)
        if (isOpen(watch.interfaceId)) watch.whileOpen(this) else finish(this)
    }
}

/** Gives back whatever a watched modal borrowed when its player leaves the world with it still open. */
public class ModalWatches : tp.plugin.ContentPlugin {
    override fun tp.plugin.ContentScope.register() {
        onLogout { ModalWatch.finish(this) }
    }
}
