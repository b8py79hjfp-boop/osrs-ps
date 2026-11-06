package tp.engine.npc

import tp.engine.World
import tp.engine.entity.Npc

/**
 * NPC behaviour outside scripts: retreat (osrs-docs Retreat), hunting (content decides; aggression.md) and random walk
 * (docs/mechanics/random-walk.md).
 */
internal class NpcAi(private val world: World) {
    /** RW5 / OPEN-RW1: chance per server tick, out of [CHANCE_SCALE]. Default: the repeated-event reading (260/1000). */
    var randomWalkChance: Int = DEFAULT_RANDOM_WALK_CHANCE

    fun process(npc: Npc) {
        if (npc.retreatFrom != null) return retreat(npc)
        // Hunting before wandering (RS Mod `NpcPlayerHuntProcessor` runs in the NPC turn); content decides the target.
        if (npc.huntRange > 0 && npc.npcInteraction == null && !npc.isDelayed) world.content.npcHunt(npc)
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

    /**
     * osrs-docs Retreat: step away from the attacker in the direction its table gives, within the max range of the spawn
     * point. Halts when the direction is blocked and a side has a wall, or when the attacker is more than 25 tiles away.
     */
    private fun retreat(npc: Npc) {
        val from = npc.retreatFrom ?: return
        val c = npc.coord
        val t = from.coord
        val far = maxOf(kotlin.math.abs(t.x - c.x), kotlin.math.abs(t.z - c.z)) > RETREAT_GIVE_UP
        if (far || t.level != c.level || npc.hitpoints <= 0) {
            npc.retreatFrom = null
            return
        }
        // "target.x >= this.x && target.y >= this.y -> SouthWest; ... < -> NorthWest; ... SouthEast; else NorthEast"
        val dx = if (t.x >= c.x) -1 else 1
        val dz = if (t.z >= c.z) -1 else 1
        val nx = c.x + dx
        val nz = c.z + dz
        val inRange = kotlin.math.abs(nx - npc.spawn.x) <= npc.maxRange && kotlin.math.abs(nz - npc.spawn.z) <= npc.maxRange
        if (inRange && world.canNpcStep(npc, dx, dz)) {
            npc.route.clear()
            npc.route.add(nx, nz)
            return
        }
        // Blocked: stop only if one of the two sides has a wall; otherwise keep trying (max range edge).
        if (!world.canNpcStep(npc, dx, 0) || !world.canNpcStep(npc, 0, dz)) npc.retreatFrom = null
    }

    private companion object {
        /** osrs-docs Retreat: "The Chebyshev distance ... is greater than 25 game tiles." */
        const val RETREAT_GIVE_UP = 25
        const val CHANCE_SCALE = 1000
        const val DEFAULT_RANDOM_WALK_CHANCE = 260
    }
}
