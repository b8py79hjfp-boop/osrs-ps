package tp.plugin

import tp.engine.entity.Player
import tp.engine.interact.apRange
import tp.engine.interact.currentApRange
import tp.engine.interact.keepInteraction
import tp.engine.script.ScriptScope
import kotlin.coroutines.RestrictsSuspension

/**
 * Receiver of interaction and queue scripts. [RestrictsSuspension]: content can only suspend through the members
 * here, so a script never leaves the world thread (brief §5.4, docs/mechanics/scripts.md).
 */
@RestrictsSuspension
public class PlayerScript internal constructor(private val scope: ScriptScope<Player>) {
    public val player: PlayerRef = refOf(scope.self)

    /** Normal delay: delays the player [ticks] and resumes this script when it ends (scripts.md D1, D3). */
    public suspend fun delay(ticks: Int) {
        scope.delay(ticks)
    }

    /** Arrive delay: one tick if the player moved this or last tick (D1). */
    public suspend fun arriveDelay() {
        scope.arriveDelay()
    }

    /** Waits for a pause-button / dialogue answer. */
    public suspend fun awaitInput(): Any = scope.awaitInput()

    /** The item (or spell slot) this use-on interaction started from, or null. */
    public val used: UsedItem? get() = scope.self.usedItem?.let { UsedItem(it.component, it.sub, it.obj) }

    /** The entity this script's interaction targets (player or NPC), or null. */
    public val target: EntityRef?
        get() =
            when (val t = scope.self.interactionTarget) {
                is tp.engine.interact.InteractionTarget.OfPlayer -> refOf(t.player)
                is tp.engine.interact.InteractionTarget.OfNpc -> refOf(t.npc)
                else -> null
            }

    /** The interaction's current approach range (I2). */
    public val currentApRange: Int get() = scope.self.currentApRange ?: tp.engine.interact.Interaction.DEFAULT_AP_RANGE

    /** Keeps the interaction after this script (combat: re-run every tick while in range). */
    public fun keepInteraction() {
        scope.self.keepInteraction()
    }

    /** Changes the approach range of the current ap interaction (interactions.md I2); -1 turns it into an op. */
    public fun apRange(range: Int) {
        scope.self.apRange(range)
    }
}
