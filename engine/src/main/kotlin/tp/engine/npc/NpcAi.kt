package tp.engine.npc

import tp.engine.World
import tp.engine.entity.Npc

/**
 * Basic NPC behaviour for Phase 3: random walk (docs/mechanics/random-walk.md). Aggression and retreat (osrs-docs)
 * arrive with combat in Phase 4.
 */
internal class NpcAi(private val world: World) {
    /** RW5 / OPEN-RW1: chance per server tick, out of [CHANCE_SCALE]. Default: the repeated-event reading (260/1000). */
    var randomWalkChance: Int = DEFAULT_RANDOM_WALK_CHANCE

    fun process(npc: Npc) {
        val occupied = npc.npcInteraction != null || npc.interaction != null || npc.isDelayed
        if (npc.wanderRange <= 0 || occupied) return // RW1
        if (world.rng.nextInt(CHANCE_SCALE) >= randomWalkChance) return
        // RW2: may re-roll while still walking. RW3: random tile within the wander range of the spawn point.
        val dx = world.rng.nextInt(-npc.wanderRange, npc.wanderRange + 1)
        val dz = world.rng.nextInt(-npc.wanderRange, npc.wanderRange + 1)
        if (dx == 0 && dz == 0) return
        npc.route.clear()
        npc.route.add(npc.spawn.x + dx, npc.spawn.z + dz) // RW4: naive stepping toward it
    }

    private companion object {
        const val CHANCE_SCALE = 1000
        const val DEFAULT_RANDOM_WALK_CHANCE = 260
    }
}
