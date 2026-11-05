package tp.engine.entity

import tp.engine.World
import tp.engine.script.AreaQueue
import tp.engine.script.PlayerQueue
import tp.engine.script.PlayerTimers
import tp.engine.script.ProtectedAccess
import tp.protocol.api.AvatarState
import tp.protocol.api.ClientSession
import tp.util.CoordGrid
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/** A logged-in player. World-thread only. */
class Player internal constructor(
    world: World,
    /** Player-info index, 1..2047. */
    val slot: Int,
    val username: String,
    val session: ClientSession,
    spawn: CoordGrid,
) : Entity(world, spawn) {
    override val size: Int get() = 1

    /** Persistent run toggle (run orb + energy arrive with stats). */
    var runMode: Boolean = false
        internal set

    /** Whether the current route was clicked with ctrl (inverts [runMode] for that route; movement.md M5). */
    internal var routeCtrl = false

    val queue = PlayerQueue(this)
    val timers = PlayerTimers(this)
    val areaQueue = AreaQueue(this)

    /** Open modal interface id, or -1. Interfaces system owns opening; queues/interactions read it. */
    var modal: Int = -1
        internal set

    val hasModal: Boolean get() = modal != -1

    /** A script waiting for dialogue/pause-button input. */
    internal var inputWait: Continuation<Any>? = null

    /** South-west tile of the current 104x104 build area (client map region). */
    internal var buildArea: CoordGrid = BuildArea.originFor(spawn)

    /** Client window mode (1 fixed, 2 resizable); content picks the toplevel from it. */
    var windowMode: Int = 1

    internal var invalidInputs = 0
    internal var invalidWindowEnd = 0L

    internal val isRunningNow: Boolean get() = runMode != routeCtrl

    /** Closing the modal removes weak scripts (Q3, Q7) and abandons any input wait. */
    fun closeModal() {
        if (modal != -1) {
            world.interfaces.closeModal(this)
            modal = -1
        }
        inputWait = null
        queue.clearWeak()
    }

    /** Resume a script waiting in [tp.engine.script.ScriptScope.awaitInput]. */
    fun resumeInput(value: Any) {
        val wait = inputWait ?: return
        inputWait = null
        wait.resume(value)
    }

    /** Runs [block] with protected access if the player may be mutated now (brief §5.4); returns whether it ran. */
    fun withProtectedAccess(block: ProtectedAccess.() -> Unit): Boolean {
        if (isDelayed || hasModal) return false
        ProtectedAccess(this).block()
        return true
    }

    internal fun avatarState(): AvatarState =
        AvatarState(coord.level, coord.x, coord.z, stepsThisTick, teleportedThisTick, isRunningNow)

    override fun toString(): String = "Player($slot, $username, $coord)"
}

/** Build area (map region) rules. Source: RS Mod `BuildAreaUtils` (104-tile area, rebuild within 2 zones of the edge). */
internal object BuildArea {
    const val SIZE = 104
    private const val ZONE = 8
    private const val REBUILD_BOUNDARY = ZONE * 2
    private const val VIEW_RADIUS_ZONES = SIZE / REBUILD_BOUNDARY

    fun originFor(coord: CoordGrid): CoordGrid {
        val zoneX = (coord.x shr 3) - VIEW_RADIUS_ZONES
        val zoneZ = (coord.z shr 3) - VIEW_RADIUS_ZONES
        return CoordGrid(maxOf(0, zoneX * ZONE), maxOf(0, zoneZ * ZONE), coord.level)
    }

    fun needsRebuild(coord: CoordGrid, origin: CoordGrid): Boolean {
        val dx = coord.x - origin.x
        val dz = coord.z - origin.z
        return dx < REBUILD_BOUNDARY || dz < REBUILD_BOUNDARY || dx >= SIZE - REBUILD_BOUNDARY || dz >= SIZE - REBUILD_BOUNDARY
    }
}
