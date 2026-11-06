package tp.engine.move

import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.util.CoordGrid

/** Waypoints consumed one step at a time (players: smart route; NPCs: a single naive destination). */
class Route {
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
        /** Player routes cap at 25 waypoints (rsmod-pathfinder default; osrs-docs Random Walk: server player paths 24 turns). */
        const val MAX_WAYPOINTS = 25
    }
}

/**
 * Movement rules (docs/mechanics/movement.md). Players walk 1 / run 2 tiles per tick along smart routes; NPCs step
 * naively toward their destination (osrs-docs Random Walk "dumb pathfinder"), 1 tile per tick.
 */
class Movement internal constructor(collision: CollisionFlagMap) {
    private val routeFinding = RouteFinding(collision)
    private val steps = StepValidator(collision)

    /** Server-side smart route (M1). Returns false if no route exists. */
    fun routePlayer(player: Player, destX: Int, destZ: Int, ctrl: Boolean): Boolean {
        player.route.clear()
        val c = player.coord
        val route = routeFinding.findRoute(c.level, c.x, c.z, destX, destZ)
        if (route.failed) return false
        for (wp in route.waypoints) player.route.add(wp.x, wp.z)
        player.routeCtrl = ctrl
        return true
    }

    /** Smart route to a target rectangle (interactions): reach the target's edge, not its tiles. */
    fun routeToTarget(entity: Entity, x: Int, z: Int, width: Int, length: Int, locShape: Int, locAngle: Int, forceApproach: Int): Boolean {
        entity.route.clear()
        val c = entity.coord
        val route =
            routeFinding.findRoute(
                level = c.level,
                srcX = c.x,
                srcZ = c.z,
                destX = x,
                destZ = z,
                srcSize = entity.size,
                destWidth = width,
                destLength = length,
                locAngle = locAngle,
                locShape = locShape,
                blockAccessFlags = forceApproach,
            )
        if (route.failed) return false
        for (wp in route.waypoints) entity.route.add(wp.x, wp.z)
        return true
    }

    /** Takes up to [maxSteps] steps along the entity's route; a blocked step ends the route (M3). Returns tiles moved. */
    fun step(entity: Entity, maxSteps: Int): Int {
        if (entity.isFrozen) {
            entity.route.clear()
            return 0
        }
        var moved = 0
        while (moved < maxSteps && !entity.route.isEmpty) {
            val c = entity.coord
            val tx = entity.route.peekX()
            val tz = entity.route.peekZ()
            val dx = Integer.signum(tx - c.x)
            val dz = Integer.signum(tz - c.z)
            if (dx == 0 && dz == 0) {
                entity.route.advance()
                continue
            }
            if (!steps.canTravel(c.level, c.x, c.z, dx, dz, entity.size)) {
                if (entity is Npc) break // NPC paths aren't cleared when blocked (osrs-docs Random Walk)
                entity.route.clear()
                break
            }
            entity.coord = CoordGrid(c.x + dx, c.z + dz, c.level)
            moved++
            if (entity.coord.x == tx && entity.coord.z == tz) entity.route.advance()
        }
        return moved
    }

    /**
     * Naive NPC step toward a destination (osrs-docs Random Walk "dumb pathfinder", RS Mod naive pathing): diagonal if
     * possible, else slide along x or z.
     */
    fun naiveStep(npc: Npc, destX: Int, destZ: Int): Boolean {
        val c = npc.coord
        val dx = Integer.signum(destX - c.x)
        val dz = Integer.signum(destZ - c.z)
        if (dx == 0 && dz == 0) return false
        val options = arrayOf(intArrayOf(dx, dz), intArrayOf(dx, 0), intArrayOf(0, dz))
        for ((ox, oz) in options.map { it[0] to it[1] }) {
            if (ox == 0 && oz == 0) continue
            if (steps.canTravel(c.level, c.x, c.z, ox, oz, npc.size)) {
                npc.coord = CoordGrid(c.x + ox, c.z + oz, c.level)
                return true
            }
        }
        return false
    }

    fun canTravel(level: Int, x: Int, z: Int, dx: Int, dz: Int, size: Int): Boolean = steps.canTravel(level, x, z, dx, dz, size)
}
