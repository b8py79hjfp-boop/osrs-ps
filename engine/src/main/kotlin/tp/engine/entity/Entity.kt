package tp.engine.entity

import tp.engine.World
import tp.engine.interact.Interaction
import tp.engine.move.Route
import tp.util.CoordGrid
import kotlin.coroutines.Continuation

/** State shared by players and NPCs: position, movement, delays, suspended scripts, current interaction. */
abstract class Entity internal constructor(
    val world: World,
    spawn: CoordGrid,
) {
    var coord: CoordGrid = spawn
        internal set

    /** Tiles occupied per side (players 1, NPCs from their config). */
    abstract val size: Int

    /** Tick the current delay ends (exclusive): delayed while `world.tick < delayedUntil` (docs scripts.md D1–D3). */
    private var delayedUntil: Long = 0

    /** `tick + 1` whenever the entity moves (osrs-docs interaction pseudocode); read by arrive delay. */
    internal var lastMovementClock: Long = -1

    /** Scripts suspended by a delay, resumed in order at delay end. */
    internal val suspended = ArrayList<Continuation<Unit>>(1)

    internal val route = Route()

    /** Current entity interaction (op/ap), or null. */
    internal var interaction: Interaction? = null

    // Per-tick movement results for the info pass.
    internal var stepsThisTick = 0
    internal var teleportedThisTick = false

    val isDelayed: Boolean get() = world.tick < delayedUntil

    internal fun delayUntil(tick: Long) {
        if (tick > delayedUntil) delayedUntil = tick
    }

    /** Moved this tick or last tick (arrive delay, D1). */
    internal fun movedRecently(): Boolean = lastMovementClock >= world.tick
}
