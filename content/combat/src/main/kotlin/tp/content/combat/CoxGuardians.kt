package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * The Stone Guardians, CoX combat room (docs/mechanics/raids/cox/guardians.md GU1-GU7).
 * - GU1 / GU2: only pickaxe hits count; the damage is multiplied by (50 + Mining + pickaxe level) / 150, with the crystal
 *   pickaxe capped at the dragon pickaxe.
 * - GU4: a slash every 4 ticks; GU5: now and then a stomp: a rock falls on the 3 x 3 around the target's tile at the
 *   moment of the attack.
 * - GU6: a raider stepping between them into their passage is knocked back.
 * - GU7: a destroyed statue becomes the demolished form; the room is done once both are destroyed.
 */
internal object CoxGuardians : Boss(Npcs.RAIDS_STONEGUARDIANS_LEFT, Npcs.RAIDS_STONEGUARDIANS_RIGHT) {
    private val NEXT_SHOVE = AttrKey<Long>("cox_guardian_shove")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
        CoxKit.loop(npc) { shove(npc) }
    }

    /** GU1: only pickaxes; GU2: the Mining multiplier. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val p = source as? PlayerRef ?: return 0
        if (style != HitStyle.MELEE) return 0
        val level = PICKAXES[p.worn(Wear.WEAPON)] ?: return 0
        // OPEN-GU4: the boosted level and the rounding; the crystal pickaxe is capped at the dragon pickaxe (61).
        val mining = p.level(Stats.MINING)
        return damage * (BASE + mining + minOf(level, DRAGON_LEVEL)) / DIVISOR
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (distance) return
        val max = CoxKit.scaledMax(npc, NpcStat.STRENGTH, BASE_STR, MAX_HIT)
        if (npc.random(STOMP_ODDS) == 0) {
            // GU5: a rock falls on the 3 x 3 centred on the target's tile at the attack (OPEN-GU6: damage, delay).
            val at: CoordGrid = victim.coord
            npc.queueHit(STOMP_DELAY, strong = false) {
                if (!npc.isInWorld) return@queueHit
                for (p in CoxKit.inArea(npc, at)) BossCombat.areaHit(npc, p, BossCombat.roll(npc, STOMP_MIN, max))
            }
            return
        }
        BossCombat.strike(npc, victim, AttackType.SLASH, max, delay = 0)
    }

    /** GU6: a raider between the two statues (in the passage they guard) is pushed back (OPEN-GU7). */
    private fun shove(npc: NpcRef) {
        if (npc.tick < (npc[NEXT_SHOVE] ?: 0L)) return
        val other = CoxKit.roomNpcs(npc, ALIVE).firstOrNull { it != npc } ?: return
        for (p in CoxKit.raiders(npc)) {
            val between =
                p.coord.x in minOf(npc.coord.x, other.coord.x)..maxOf(npc.coord.x, other.coord.x) &&
                    p.coord.z in minOf(npc.coord.z, other.coord.z)..maxOf(npc.coord.z, other.coord.z)
            if (!between || npc.distanceTo(p) > 1) continue
            npc[NEXT_SHOVE] = npc.tick + SHOVE_COOLDOWN
            val dx = Integer.signum(p.coord.x - npc.coord.x)
            val dz = Integer.signum(p.coord.z - npc.coord.z)
            p.teleport(BossCombat.randomTile(npc, CoordGrid(p.coord.x + dx * SHOVE, p.coord.z + dz * SHOVE, p.coord.level), 1))
            BossCombat.areaHit(npc, p, BossCombat.roll(npc, SHOVE_MIN, SHOVE_MAX))
        }
    }

    /** GU7: the demolished statue stays (7571 / 7572). */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        val left = npc.type == Npcs.RAIDS_STONEGUARDIANS_LEFT
        val dead = if (left) Npcs.RAIDS_STONEGUARDIANS_LEFT_DEAD else Npcs.RAIDS_STONEGUARDIANS_RIGHT_DEAD
        npc.spawnNpc(dead, npc.coord)
    }

    private val ALIVE = setOf(Npcs.RAIDS_STONEGUARDIANS_LEFT, Npcs.RAIDS_STONEGUARDIANS_RIGHT)

    /** Pickaxe obj -> Mining level requirement (Wiki "Pickaxe"). */
    private val PICKAXES: Map<Int, Int> =
        mapOf(
            tp.symbols.Objs.BRONZE_PICKAXE to 1,
            tp.symbols.Objs.IRON_PICKAXE to 1,
            tp.symbols.Objs.STEEL_PICKAXE to 6,
            tp.symbols.Objs.BLACK_PICKAXE to 11,
            tp.symbols.Objs.MITHRIL_PICKAXE to 21,
            tp.symbols.Objs.ADAMANT_PICKAXE to 31,
            tp.symbols.Objs.RUNE_PICKAXE to 41,
            tp.symbols.Objs.DRAGON_PICKAXE to DRAGON_LEVEL,
            tp.symbols.Objs.CRYSTAL_PICKAXE to CRYSTAL_LEVEL,
        )

    /** GU2: D = (50 + M + P) / 150. */
    private const val BASE = 50
    private const val DIVISOR = 150
    private const val DRAGON_LEVEL = 61
    private const val CRYSTAL_LEVEL = 71

    /** Infobox max hit 20 at strength 140 (maxed solo). */
    private const val MAX_HIT = 20
    private const val BASE_STR = 140

    /** OPEN-GU6: stomp odds, delay and minimum. */
    private const val STOMP_ODDS = 5
    private const val STOMP_DELAY = 3
    private const val STOMP_MIN = 5

    /** OPEN-GU7: the shove. */
    private const val SHOVE = 2
    private const val SHOVE_MIN = 5
    private const val SHOVE_MAX = 10
    private const val SHOVE_COOLDOWN = 4L
    private const val HUNT = 6
}
