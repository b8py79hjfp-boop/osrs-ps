package tp.engine.entity

import tp.engine.World
import tp.engine.script.NpcQueue
import tp.engine.script.NpcTimer
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

    override fun toString(): String = "Npc($index, type=$type, $coord)"
}
