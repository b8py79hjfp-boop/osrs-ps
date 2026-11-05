package tp.engine

import tp.protocol.api.AvatarState
import tp.protocol.api.ClientSession
import tp.util.CoordGrid

/**
 * A logged-in player. World-thread only. Phase 2 slice: position, route and movement state; Phase 3 adds
 * queues/timers/delays/interactions, vars and inventories.
 */
class Player internal constructor(
    /** Player-info index, 1..2047. */
    val slot: Int,
    val username: String,
    val session: ClientSession,
    spawn: CoordGrid,
) {
    var coord: CoordGrid = spawn
        internal set

    /** Persistent run toggle (Phase 3: run orb + energy). */
    var runMode: Boolean = false
        internal set

    internal val route = Route()

    /** Whether the current route was clicked with ctrl (inverts [runMode] for that route). */
    internal var routeCtrl = false

    // Per-tick movement results, consumed by the info pass.
    internal var stepsThisTick = 0
    internal var teleportedThisTick = false

    /** South-west tile of the current 104x104 build area (client map region). */
    internal var buildArea: CoordGrid = BuildArea.originFor(spawn)

    internal val isRunningNow: Boolean get() = runMode != routeCtrl

    internal fun avatarState(): AvatarState =
        AvatarState(
            level = coord.level,
            x = coord.x,
            z = coord.z,
            moveSteps = stepsThisTick,
            teleported = teleportedThisTick,
            running = isRunningNow,
        )

    override fun toString(): String = "Player($slot, $username, $coord)"
}

/** Waypoints from the route finder, consumed one step at a time. */
internal class Route {
    private val xs = IntArray(MAX_WAYPOINTS)
    private val zs = IntArray(MAX_WAYPOINTS)
    private var size = 0
    private var next = 0

    val isEmpty: Boolean get() = next >= size

    fun clear() {
        size = 0
        next = 0
    }

    fun add(x: Int, z: Int) {
        if (size < MAX_WAYPOINTS) {
            xs[size] = x
            zs[size] = z
            size++
        }
    }

    fun peekX(): Int = xs[next]

    fun peekZ(): Int = zs[next]

    fun advance() {
        next++
    }

    companion object {
        /** rsmod-pathfinder default `maxWaypoints` (the client also caps routes at 25 waypoints). */
        const val MAX_WAYPOINTS = 25
    }
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
