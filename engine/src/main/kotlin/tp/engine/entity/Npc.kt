package tp.engine.entity

import tp.engine.World
import tp.engine.interact.NpcInteraction
import tp.engine.script.NpcQueue
import tp.engine.script.NpcTimer
import tp.protocol.api.AvatarRef
import tp.protocol.api.Visual
import tp.util.CoordGrid

/** A non-player character. World-thread only. Index is its NPC-info slot; NPCs act in index order (tick.md). */
class Npc internal constructor(
    world: World,
    val index: Int,
    /** Npc config id it spawns as (`NpcType`). */
    val baseType: Int,
    override val size: Int,
    /** Spawn tile; respawn and random walk are anchored here. */
    val spawn: CoordGrid,
    /** Max random-walk distance from spawn (osrs-docs Random Walk), 0 = stationary. */
    val wanderRange: Int,
) : Entity(world, spawn) {
    /** Current npc config id: [baseType] unless transformed ([transform]). */
    var type: Int = baseType
        private set

    val queue = NpcQueue(this)
    val timer = NpcTimer(this)

    /** Ticks until the next random-walk attempt; driven by the NPC AI step. */
    internal var randomWalkCountdown: Int = 0

    /** Current combat levels: attack, defence, strength, hitpoints, ranged, magic (NpcType stats order). */
    val levels: IntArray =
        world.data.configs.npcs
            .getValue(baseType)
            .stats
            .copyOf()

    override var hitpoints: Int
        get() = levels[HITPOINTS]
        set(value) {
            levels[HITPOINTS] = value.coerceAtLeast(0)
        }

    override val maxHitpoints: Int
        get() =
            world.data.configs.npcs
                .getValue(type)
                .stats[HITPOINTS]

    /** Replaces the route with a naive walk toward ([x], [z]) (content-driven repositioning). */
    fun walkTo(x: Int, z: Int) {
        route.clear()
        route.add(x, z)
    }

    /**
     * Turns this NPC into config [newType] (boss phases): observers see the new model at once (NPC transmogrification);
     * with [restoreStats] its levels and hitpoints become the new type's. Respawning restores [baseType].
     */
    fun transform(newType: Int, restoreStats: Boolean) {
        type = newType
        if (restoreStats) {
            world.data.configs.npcs
                .getValue(newType)
                .stats
                .copyInto(levels)
        }
        visual(
            tp.protocol.api.Visual
                .Transform(newType),
        )
    }

    /** Removed from the world until [World.respawnNpc] brings it back (dead NPCs). */
    var hidden: Boolean = false
        internal set

    internal fun resetForRespawn() {
        this.type = baseType
        val type =
            world.data.configs.npcs
                .getValue(baseType)
        type.stats.copyInto(levels)
        coord = spawn
        npcInteraction = null
        attrs.clear()
        queue.clear()
        frozenUntil = 0
        hidden = false
    }

    /** Max range: how far from its spawn the NPC may move while fighting (aggression.md AG5; Wiki: "almost always seven"). */
    var maxRange: Int = DEFAULT_MAX_RANGE

    /** The entity this NPC is retreating from (osrs-docs Retreat), or null. */
    var retreatFrom: Entity? = null

    /** Hunt range in tiles from the south-west tile (aggression.md AG3); 0 = doesn't hunt. Set by content. */
    var huntRange: Int = 0

    /** The NPC's own interaction (NPCs attacking players: interactions.md I9). */
    internal var npcInteraction: NpcInteraction? = null

    override fun visual(visual: Visual) {
        world.npcVisual(index, visual)
    }

    override val avatarRef: AvatarRef = AvatarRef.OfNpc(index)

    override fun toString(): String = "Npc($index, type=$type, $coord)"

    companion object {
        /** NpcType stats index of Hitpoints (cache order: attack, defence, strength, hitpoints, ranged, magic). */
        const val HITPOINTS = 3
    }
}

/** Wiki "Retreat": "The max range is almost always seven tiles." */
const val DEFAULT_MAX_RANGE: Int = 7
