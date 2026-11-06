package tp.engine.entity

import tp.engine.World
import tp.engine.interact.Interaction
import tp.engine.move.Route
import tp.protocol.api.AvatarRef
import tp.protocol.api.Visual
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
    internal var delayedUntil: Long = 0
        private set

    /** `tick + 1` whenever the entity moves (osrs-docs interaction pseudocode); read by arrive delay. */
    internal var lastMovementClock: Long = -1

    /** Scripts suspended by a delay, resumed in order at delay end. */
    internal val suspended = ArrayList<Continuation<Unit>>(1)

    internal val route = Route()

    /** Current entity interaction (op/ap), or null. */
    internal var interaction: Interaction? = null

    /** What the current interaction targets (read-only view for content). */
    val interactionTarget: tp.engine.interact.InteractionTarget? get() = interaction?.target

    // Per-tick movement results for the info pass.
    internal var stepsThisTick = 0
    internal var teleportedThisTick = false

    val isDelayed: Boolean get() = world.tick < delayedUntil

    /**
     * Content-owned state (combat clocks, timers, flags), keyed by plugin-api attribute keys. World-scoped and
     * deterministic; cleared when the entity leaves the world.
     */
    val attrs: HashMap<Any, Any> = HashMap()

    /** Movement is blocked while `world.tick < frozenUntil` (freezes: docs/mechanics/freeze.md); routes are dropped. */
    var frozenUntil: Long = 0

    val isFrozen: Boolean get() = world.tick < frozenUntil

    /** Current hitpoints (players: the Hitpoints stat's current level; NPCs: their own counter). */
    abstract var hitpoints: Int

    /** Base hitpoints. */
    abstract val maxHitpoints: Int

    /** A visual on this entity's avatar this tick (seen by every observer). */
    abstract fun visual(visual: Visual)

    /** This entity as a visual reference (hitsplat source, face target). */
    abstract val avatarRef: AvatarRef

    internal fun delayUntil(tick: Long) {
        if (tick > delayedUntil) delayedUntil = tick
    }

    /** Moved this tick or last tick (arrive delay, D1). */
    internal fun movedRecently(): Boolean = lastMovementClock >= world.tick
}
