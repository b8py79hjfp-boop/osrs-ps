package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * Player op "Follow" (slot 3, set at login). The interaction stays alive while the target moves; the engine re-paths
 * toward a moving target whenever it is out of reach (interactions.md OPEN-I1). OPEN-F1: OSRS follows onto the
 * target's previous tile; we stop adjacent.
 */
public class PlayerOps : ContentPlugin {
    override fun ContentScope.register() {
        onOpPlayer(FOLLOW_OP) { target ->
            player.face(target)
            keepInteraction()
        }
    }

    private companion object {
        /** Login sets slot 3 to "Follow" (RS Mod `LoginScript.sendPlayerOps`). */
        const val FOLLOW_OP = 3
    }
}
