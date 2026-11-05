package tp.plugin

import tp.engine.entity.Player
import tp.engine.interact.apRange
import tp.engine.script.ScriptScope
import kotlin.coroutines.RestrictsSuspension

/**
 * Receiver of interaction and queue scripts. [RestrictsSuspension]: content can only suspend through the members
 * here, so a script never leaves the world thread (brief §5.4, docs/mechanics/scripts.md).
 */
@RestrictsSuspension
public class PlayerScript internal constructor(private val scope: ScriptScope<Player>) {
    public val player: PlayerRef = PlayerRef(scope.self)

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

    /** Changes the approach range of the current ap interaction (interactions.md I2); -1 turns it into an op. */
    public fun apRange(range: Int) {
        scope.self.apRange(range)
    }
}
