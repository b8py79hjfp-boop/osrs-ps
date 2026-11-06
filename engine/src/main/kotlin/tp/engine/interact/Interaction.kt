package tp.engine.interact

import org.rsmod.routefinder.LineValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.reach.ReachStrategy
import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.engine.move.Movement
import tp.engine.script.Script
import tp.engine.script.ScriptRunner
import tp.protocol.api.ServerMessage
import tp.util.CoordGrid

/** What an interaction points at (docs/mechanics/interactions.md). */
sealed interface InteractionTarget {
    /** Map loc (static). [width]/[length] are the config's unrotated size (ReachStrategy rotates them). */
    data class Loc(
        val coord: CoordGrid,
        val id: Int,
        val shape: Int,
        val angle: Int,
        val width: Int,
        val length: Int,
        val forceApproach: Int,
    ) : InteractionTarget

    /** Ground item (static). */
    data class Obj(val coord: CoordGrid, val id: Int) : InteractionTarget

    data class OfNpc(val npc: Npc) : InteractionTarget

    data class OfPlayer(val player: Player) : InteractionTarget
}

/** One interaction in progress (osrs-docs pseudocode state). */
class Interaction(
    val target: InteractionTarget,
    /** Option number (op1..op5 etc.); content keys handlers by it. */
    val option: Int,
    internal val opScript: Script<Player>?,
    internal val apScript: Script<Player>?,
) {
    internal var apRange: Int = DEFAULT_AP_RANGE
    internal var apRangeCalled: Boolean = false

    /** Persistent interactions survive a successful run (e.g. combat follow). */
    var persistent: Boolean = false

    internal val isMobileTarget: Boolean get() = target is InteractionTarget.OfNpc || target is InteractionTarget.OfPlayer

    companion object {
        /** Default approach distance (osrs-docs: "The default distance for approachable interactions is 10"). */
        const val DEFAULT_AP_RANGE = 10
    }
}

/** Called from an ap script: change the approach range (I2); -1 converts the ap into an op. */
fun Player.apRange(range: Int) {
    val i = interaction ?: return
    i.apRange = range
    i.apRangeCalled = true
}

/**
 * osrs-docs Entity Interactions `processInteractions()`, transcribed in structure (I3–I8). Movement happens in the
 * middle; for entities without an interaction it is just movement.
 */
class InteractionProcessor internal constructor(
    private val flags: CollisionFlagMap,
    private val movement: Movement,
) {
    private val lines = LineValidator(flags)

    /** Player turn: interactions + movement. [maxSteps] = 1 walk / 2 run. */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth") // transcription of osrs-docs' pseudocode
    fun processPlayer(player: Player, maxSteps: Int) {
        val interaction = player.interaction
        var interacted = false
        if (interaction != null) interaction.apRangeCalled = false

        if (interaction != null && !blocked(player)) {
            updateRoute(player, interaction)
            when {
                interaction.opScript != null && inOpRange(player, interaction) && interaction.isMobileTarget -> {
                    run(player, interaction.opScript, "op (block 1)")
                    interacted = true
                }

                interaction.apScript != null && inApRange(player, interaction) -> {
                    run(player, interaction.apScript, "ap (block 1)")
                    interacted = true
                }

                inApRange(player, interaction) -> {
                    Unit
                }

                // first block: engine ap is a no-op
                inOpRange(player, interaction) && interaction.isMobileTarget -> {
                    nothingInteresting(player)
                    interacted = true
                }
            }
        }

        val before = player.coord
        player.stepsThisTick = movement.step(player, maxSteps)
        val moved = player.coord != before
        if (moved) {
            player.lastMovementClock = player.world.tick + 1
            player.world.tracer?.onPhase(player, "move ${player.coord}")
        }

        val current = player.interaction ?: return
        if (current !== interaction) return // replaced by a script during this turn
        if (!blocked(player) && (!interacted || current.apRangeCalled)) {
            when {
                current.opScript != null && inOpRange(player, current) && (current.isMobileTarget || !moved) -> {
                    run(player, current.opScript, "op (block 2)")
                    interacted = true
                }

                current.apScript != null && inApRange(player, current) -> {
                    current.apRangeCalled = false
                    run(player, current.apScript, "ap (block 2)")
                    interacted = true
                }

                // Engine ap only stands in for a missing script: with an op script the player keeps walking into op
                // range (interactions.md I3, SOURCE-CONFLICT with the literal pseudocode).
                current.opScript == null && inApRange(player, current) -> {
                    nothingInteresting(player)
                    interacted = true
                }

                inOpRange(player, current) && (current.isMobileTarget || !moved) -> {
                    nothingInteresting(player)
                    interacted = true
                }
            }
        }
        if (!blocked(player) && player.interaction === current) {
            if (!interacted && !moved && player.route.isEmpty) {
                player.session.send(ServerMessage.GameMessage(CANT_REACH))
                player.interaction = null
            }
            if (interacted && !current.apRangeCalled && !current.persistent) player.interaction = null
        }
    }

    /** NPC turn: same algorithm without modals, engine scripts or messages (I9). Phase 3 NPCs only move. */
    fun processNpc(npc: Npc) {
        val before = npc.coord
        npc.stepsThisTick = movement.step(npc, 1)
        if (npc.coord != before) npc.lastMovementClock = npc.world.tick + 1
    }

    private fun blocked(player: Player): Boolean = player.isDelayed || player.hasModal

    private fun run(player: Player, script: Script<Player>, label: String) = ScriptRunner.start(player, script, label)

    private fun nothingInteresting(player: Player) {
        player.world.tracer?.onPhase(player, "engine interaction")
        player.session.send(ServerMessage.GameMessage(NOTHING_INTERESTING))
    }

    /** Re-route toward a mobile target when out of reach (OPEN-I1); toward a static target once. */
    private fun updateRoute(entity: Entity, i: Interaction) {
        if (inOpRange(entity, i)) return
        if (!entity.route.isEmpty && !i.isMobileTarget) return
        val t = i.target
        if (t is InteractionTarget.Loc) {
            movement.routeToTarget(entity, t.coord.x, t.coord.z, t.width, t.length, t.shape, t.angle, t.forceApproach)
        } else {
            val r = rect(t)
            movement.routeToTarget(entity, r.x, r.z, r.width, r.length, shapeOf(i), 0, 0)
        }
    }

    private class Rect(val x: Int, val z: Int, val width: Int, val length: Int)

    private fun rect(t: InteractionTarget): Rect =
        when (t) {
            is InteractionTarget.Loc -> Rect(t.coord.x, t.coord.z, t.width, t.length)
            is InteractionTarget.Obj -> Rect(t.coord.x, t.coord.z, 1, 1)
            is InteractionTarget.OfNpc -> Rect(t.npc.coord.x, t.npc.coord.z, t.npc.size, t.npc.size)
            is InteractionTarget.OfPlayer -> Rect(t.player.coord.x, t.player.coord.z, 1, 1)
        }

    private fun levelOf(t: InteractionTarget): Int =
        when (t) {
            is InteractionTarget.Loc -> t.coord.level
            is InteractionTarget.Obj -> t.coord.level
            is InteractionTarget.OfNpc -> t.npc.coord.level
            is InteractionTarget.OfPlayer -> t.player.coord.level
        }

    private fun shapeOf(i: Interaction): Int = if (i.isMobileTarget) ENTITY_SHAPE else RECTANGLE_SHAPE

    /**
     * I1: op = reached per target shape. Locs use the loc's own shape (walls, wall decor, rectangles — RS Mod
     * `BoundValidator` / rsmod-pathfinder `ReachStrategy`; not stated by osrs-docs). Entities: exclusive rectangle
     * (cardinal adjacency). Objs: 1x1 rectangle (OPEN-I2).
     */
    fun inOpRange(entity: Entity, i: Interaction): Boolean {
        val c = entity.coord
        val t = i.target
        if (levelOf(t) != c.level) return false
        if (t is InteractionTarget.Loc) {
            return ReachStrategy.reached(
                flags,
                c.level,
                c.x,
                c.z,
                t.coord.x,
                t.coord.z,
                t.width,
                t.length,
                entity.size,
                t.angle,
                t.shape,
                t.forceApproach,
            )
        }
        val r = rect(t)
        return ReachStrategy.reached(flags, c.level, c.x, c.z, r.x, r.z, r.width, r.length, entity.size, 0, shapeOf(i))
    }

    /** I2: within [Interaction.apRange] (Chebyshev to the target rectangle) with line of sight; -1 means op only. */
    fun inApRange(entity: Entity, i: Interaction): Boolean {
        if (i.apRange < 0) return inOpRange(entity, i)
        val c = entity.coord
        if (levelOf(i.target) != c.level) return false
        val r = rect(i.target)
        val underTarget = c.x in r.x until r.x + r.width && c.z in r.z until r.z + r.length
        if (i.isMobileTarget && underTarget) return false
        val dx = maxOf(0, r.x - c.x, c.x - (r.x + r.width - 1))
        val dz = maxOf(0, r.z - c.z, c.z - (r.z + r.length - 1))
        if (maxOf(dx, dz) > i.apRange) return false
        return lines.hasLineOfSight(c.level, c.x, c.z, r.x, r.z, entity.size, entity.size, r.width, r.length)
    }

    companion object {
        const val NOTHING_INTERESTING = "Nothing interesting happens."
        const val CANT_REACH = "I can't reach that!"

        /** rsmod-pathfinder ReachStrategy shapes: -2 exclusive rectangle (entities), 10 rectangle (objs, OPEN-I2). */
        private const val ENTITY_SHAPE = -2
        private const val RECTANGLE_SHAPE = 10
    }
}
