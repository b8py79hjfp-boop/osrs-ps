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
    /** Npc config id (`NpcType`). */
    val type: Int,
    override val size: Int,
    /** Spawn tile; respawn and random walk are anchored here. */
    val spawn: CoordGrid,
    /** Max random-walk distance from spawn (osrs-docs Random Walk), 0 = stationary. */
    val wanderRange: Int,
) : Entity(world, spawn) {
    val queue = NpcQueue(this)
    val timer = NpcTimer(this)

    /** Ticks until the next random-walk attempt; driven by the NPC AI step. */
    internal var randomWalkCountdown: Int = 0

    /** Current combat levels: attack, defence, strength, hitpoints, ranged, magic (NpcType stats order). */
    val levels: IntArray =
        world.data.configs.npcs
            .getValue(type)
            .stats
            .copyOf()

    override var hitpoints: Int
        get() = levels[HITPOINTS]
        set(value) {
            levels[HITPOINTS] = value.coerceAtLeast(0)
        }

    override val maxHitpoints: Int =
        world.data.configs.npcs
            .getValue(type)
            .stats[HITPOINTS]

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
