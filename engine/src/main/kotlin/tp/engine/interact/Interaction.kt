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

/**
 * An NPC's interaction with a player or NPC (I9). [view] adapts it to the shared reach checks.
 */
class NpcInteraction(
    val target: InteractionTarget,
    internal val opScript: Script<Npc>?,
    internal val apScript: Script<Npc>?,
    /** False: an NPC in approach range stays put (ranged/magic attackers); true: it keeps closing to op range. */
    internal val closeIn: Boolean = true,
) {
    internal var apRangeCalled: Boolean = false

    /** Combat interactions persist until cancelled. */
    var persistent: Boolean = true

    internal val view = Interaction(target, 0, null, null)

    var apRange: Int
        get() = view.apRange
        set(value) {
            view.apRange = value
            apRangeCalled = true
        }

    internal fun targetValid(): Boolean =
        when (val t = target) {
            is InteractionTarget.OfPlayer -> t.player.session.isOpen && t.player.world.player(t.player.slot) === t.player
            is InteractionTarget.OfNpc -> t.npc.hitpoints > 0
            else -> true
        }
}

/** Sets or clears what [this] NPC is interacting with. */
fun Npc.interactWith(interaction: NpcInteraction?) {
    npcInteraction = interaction
}

val Npc.currentInteraction: NpcInteraction? get() = npcInteraction

/** Current approach range of [this] player's interaction (I2), or null without one. */
val Player.currentApRange: Int? get() = interaction?.apRange

/** Ends [this] player's interaction without other interruption effects. */
fun Player.clearInteraction() {
    interaction = null
}

/** Called from a player's script: keep the current interaction after it runs (combat follow, OPEN-I3). */
fun Player.keepInteraction() {
    interaction?.persistent = true
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

    /** Line of sight between two footprints (projectile rules; rsmod-pathfinder `LineValidator`). */
    fun hasLineOfSight(from: Entity, to: Entity): Boolean =
        from.coord.level == to.coord.level &&
            lines.hasLineOfSight(
                from.coord.level,
                from.coord.x,
                from.coord.z,
                to.coord.x,
                to.coord.z,
                from.size,
                from.size,
                to.size,
                to.size,
            )

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

    /**
     * NPC turn (I9): the same two blocks around movement, without modals, engine scripts or messages. NPCs follow with
     * the naive ("dumb") step toward the target, never onto it (RS Mod naive pathing; osrs-docs Random Walk).
     */
    fun processNpc(npc: Npc) {
        val interaction = npc.npcInteraction
        if (interaction != null && !interaction.targetValid()) npc.npcInteraction = null
        val start = npc.npcInteraction
        val interacted = start != null && !npc.isDelayed && npcAttempt(npc, start, block = 1)
        val before = npc.coord
        npc.stepsThisTick = movement.step(npc, 1)
        val follow = start != null && npc.stepsThisTick == 0 && needsToFollow(npc, start)
        if (follow && start != null && !follow(npc, start)) return
        if (npc.coord != before) npc.lastMovementClock = npc.world.tick + 1
        if (start == null || start !== npc.npcInteraction || npc.isDelayed) return
        val ran = if (!interacted || start.apRangeCalled) npcAttempt(npc, start, block = 2) else interacted
        val keep = start.apRangeCalled || start.persistent
        if (ran && !keep && npc.npcInteraction === start) npc.npcInteraction = null
    }

    private fun needsToFollow(npc: Npc, i: NpcInteraction): Boolean =
        !npc.isDelayed && !npc.isFrozen && !inOpRange(npc, i.view) && (i.closeIn || i.apScript == null || !inApRange(npc, i.view))

    /** One attempt block for an NPC: op script in op range, else ap script in approach range. */
    private fun npcAttempt(npc: Npc, i: NpcInteraction, block: Int): Boolean {
        if (block == 1) i.apRangeCalled = false
        val op = i.opScript
        val ap = i.apScript
        return when {
            op != null && inOpRange(npc, i.view) -> {
                ScriptRunner.start(npc, op, "npc op (block $block)")
                true
            }

            ap != null && inApRange(npc, i.view) -> {
                if (block == 2) i.apRangeCalled = false
                ScriptRunner.start(npc, ap, "npc ap (block $block)")
                true
            }

            else -> {
                false
            }
        }
    }

    /** One follow step; false when the NPC gave up because the target left its aggression range (AG5). */
    private fun follow(npc: Npc, i: NpcInteraction): Boolean {
        if (followStep(npc, i)) {
            npc.stepsThisTick = 1
            return true
        }
        if (!outsideAggressionRange(npc, i)) return true
        npc.npcInteraction = null
        return false
    }

    /**
     * AG5 (Wiki "Aggressiveness"): the aggression range is the max range plus the attack range, around the spawn point,
     * accounting for the NPC's size. The target is outside it when no tile the NPC may stand on could reach it.
     */
    private fun outsideAggressionRange(npc: Npc, i: NpcInteraction): Boolean {
        val r = rect(i.target)
        val attackRange = if (i.apScript != null && i.view.apRange > 0) i.view.apRange else 1
        val reach = npc.maxRange + attackRange
        val s = npc.spawn
        val dx = maxOf(0, r.x - (s.x + npc.size - 1 + reach), (s.x - reach) - (r.x + r.width - 1))
        val dz = maxOf(0, r.z - (s.z + npc.size - 1 + reach), (s.z - reach) - (r.z + r.length - 1))
        return dx > 0 || dz > 0
    }

    /** Within max range of the spawn point (AG5). */
    private fun withinMaxRange(npc: Npc, x: Int, z: Int): Boolean =
        kotlin.math.abs(x - npc.spawn.x) <= npc.maxRange && kotlin.math.abs(z - npc.spawn.z) <= npc.maxRange

    /** One naive step toward the target that doesn't enter the target's footprint or leave the max range. */
    private fun followStep(npc: Npc, i: NpcInteraction): Boolean {
        val r = rect(i.target)
        val c = npc.coord
        val dx = Integer.signum((r.x + r.width / 2) - c.x)
        val dz = Integer.signum((r.z + r.length / 2) - c.z)
        for ((ox, oz) in listOf(dx to dz, dx to 0, 0 to dz)) {
            if (ox == 0 && oz == 0) continue
            val nx = c.x + ox
            val nz = c.z + oz
            val overlaps = nx < r.x + r.width && nx + npc.size > r.x && nz < r.z + r.length && nz + npc.size > r.z
            if (overlaps || !withinMaxRange(npc, nx, nz) || !movement.canTravel(c.level, c.x, c.z, ox, oz, npc.size)) continue
            npc.coord = CoordGrid(nx, nz, c.level)
            return true
        }
        return false
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
        // An approachable interaction already in approach range doesn't move the entity (I2: ranged/magic attacks).
        if (i.apScript != null && inApRange(entity, i)) {
            entity.route.clear()
            return
        }
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
        crossWorldReach(entity, i.target)?.let { return it }
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
        return crossWorldReach(entity, i.target) ?: sameWorldApRange(entity, i)
    }

    private fun sameWorldApRange(entity: Entity, i: Interaction): Boolean {
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

    /**
     * WE11 (docs/mechanics/world-entities.md): an entity on a deck targeting something outside that deck can't walk
     * there, so it reaches it from where it stands if the deck's pivot is within [CROSS_WORLD_RANGE]; content checks
     * the real distance (OPEN-WE4). Null when both sides are in the same world.
     */
    private fun crossWorldReach(entity: Entity, t: InteractionTarget): Boolean? {
        val entities = entity.world.worldEntities
        if (entities.all().isEmpty()) return null
        val deck = entities.at(entity.coord) ?: return null
        val target = targetCoord(t)
        if (target in deck) return null
        val root = deck.rootCoord
        return maxOf(kotlin.math.abs(root.x - target.x), kotlin.math.abs(root.z - target.z)) <= CROSS_WORLD_RANGE
    }

    private fun targetCoord(t: InteractionTarget): CoordGrid =
        when (t) {
            is InteractionTarget.Loc -> t.coord
            is InteractionTarget.Obj -> t.coord
            is InteractionTarget.OfNpc -> t.npc.coord
            is InteractionTarget.OfPlayer -> t.player.coord
        }

    companion object {
        /** WE6 / WE8: RSProt `DEFAULT_RENDER_DISTANCE`, the range a deck sees the root world in. */
        private const val CROSS_WORLD_RANGE = 31
        const val NOTHING_INTERESTING = "Nothing interesting happens."
        const val CANT_REACH = "I can't reach that!"

        /** rsmod-pathfinder ReachStrategy shapes: -2 exclusive rectangle (entities), 10 rectangle (objs, OPEN-I2). */
        private const val ENTITY_SHAPE = -2
        private const val RECTANGLE_SHAPE = 10
    }
}
