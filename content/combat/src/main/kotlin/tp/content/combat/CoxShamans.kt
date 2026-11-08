package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Lizardman shamans, CoX combat room (docs/mechanics/raids/cox/shamans.md SH1-SH6; the surface shaman's attack set).
 * - SH2: melee in reach (diagonals too) or ranged, every 4 ticks.
 * - SH3: acid spit, up to 39 (25% above the surface 25-30), poisons from 12, ignores prayers.
 * - SH4: a jump onto the raider's tile: 20-25 typeless in the 3 x 3, not if the landing spot is blocked.
 * - SH5: three purple spawns next to the raider explode a few seconds later for 5-10 in a 5 x 5; the summon has a cooldown.
 */
internal object CoxShamans : Boss(Npcs.RAIDS_LIZARDSHAMAN_A, Npcs.RAIDS_LIZARDSHAMAN_B) {
    private val NEXT_SPAWNS = AttrKey<Long>("cox_shaman_spawns")
    private val JUMPING = AttrKey<Long>("cox_shaman_jump")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if ((npc[JUMPING] ?: -1L) >= npc.tick) return
        // OPEN-SH6: the special odds (spit 1/5, jump 1/6, spawns 1/6 off cooldown, else the standard attack).
        when {
            npc.random(SPIT_ODDS) == 0 -> {
                spit(npc, victim)
            }

            npc.random(JUMP_ODDS) == 0 && jump(npc, victim) -> {
                Unit
            }

            npc.random(SPAWN_ODDS) == 0 && npc.tick >= (npc[NEXT_SPAWNS] ?: 0L) -> {
                spawns(npc, victim)
            }

            !distance -> {
                npc.anim(Seqs.SHAY_LIZARD_WARRIOR_ATTACK_MELEE)
                BossCombat.strike(npc, victim, AttackType.STAB, CoxKit.max(npc, NpcStat.STRENGTH, STR_BONUS), delay = 0)
            }

            else -> {
                npc.anim(Seqs.SHAY_LIZARD_WARRIOR_ATTACK_RANGED)
                val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
                BossCombat.strike(npc, victim, AttackType.RANGED, CoxKit.max(npc, NpcStat.RANGED, RANGED_STR), delay)
            }
        }
    }

    /** SH3: ignores prayers; poisons from 12 (OPEN-SH5: the Shayzien armour reduction inside the raid). */
    private fun spit(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.SHAY_LIZARD_WARRIOR_ATTACK_RANGED)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.LIZARDSHAMAN_SPIT_ACID, delay)
        val at = victim.coord
        npc.queueHit(delay, strong = false) {
            if (!npc.isInWorld) return@queueHit
            for (p in CoxKit.inArea(npc, at, 0)) {
                p.spotAnim(SpotAnims.LIZARDSHAMAN_ACID_SPLASH)
                BossCombat.areaHit(npc, p, BossCombat.roll(npc, 0, SPIT_MAX))
                Poison.poison(p, SPIT_POISON)
            }
        }
    }

    /** SH4: lands on the raider's tile area 20-25 typeless (3 x 3); not when the spot is blocked. */
    private fun jump(npc: NpcRef, victim: EntityRef): Boolean {
        val at = victim.coord
        if (!npc.isWalkable(at)) return false
        npc[JUMPING] = npc.tick + JUMP_TICKS
        npc.queueHit(JUMP_TICKS, strong = false) {
            if (!npc.isInWorld) return@queueHit
            npc.teleport(CoordGrid(at.x - 1, at.z - 1, at.level))
            for (p in CoxKit.inArea(npc, at)) BossCombat.areaHit(npc, p, BossCombat.roll(npc, JUMP_MIN, JUMP_MAX))
        }
        return true
    }

    /** SH5: three spawns next to the raider; each explodes for 5-10 by proximity (5 x 5). */
    private fun spawns(npc: NpcRef, victim: EntityRef) {
        npc[NEXT_SPAWNS] = npc.tick + SPAWN_COOLDOWN
        repeat(SPAWNS) {
            val at = BossCombat.randomTile(npc, victim.coord, 1)
            val spawn = Bosses.summon(npc, Npcs.RAIDS_LIZARDSHAMAN_BLOCKER, at, huntRange = 0, inheritRun = true)
            spawn.queueHit(SPAWN_FUSE, strong = false) {
                if (!spawn.isInWorld) return@queueHit
                spawn.spotAnim(SpotAnims.LIZARDSHAMAN_SPAWN_EXPLODE)
                for (p in CoxKit.inArea(spawn, spawn.coord, SPAWN_RADIUS)) {
                    val near = maxOf(kotlin.math.abs(p.coord.x - spawn.coord.x), kotlin.math.abs(p.coord.z - spawn.coord.z))
                    BossCombat.areaHit(
                        spawn,
                        p,
                        if (near == 0) {
                            SPAWN_MAX
                        } else if (near == 1) {
                            SPAWN_MID
                        } else {
                            SPAWN_MIN
                        },
                    )
                }
                spawn.despawn()
            }
        }
    }

    /** Infobox: strength bonus 52, ranged strength 49. */
    private const val STR_BONUS = 52
    private const val RANGED_STR = 49

    /** SH3: Page up to 39 (Strat 40); poison from 12. */
    private const val SPIT_MAX = 39
    private const val SPIT_POISON = 12

    /** SH4: 20-25. */
    private const val JUMP_MIN = 20
    private const val JUMP_MAX = 25

    /** SH5: three spawns, 5-10 by proximity in a radius of 2. */
    private const val SPAWNS = 3
    private const val SPAWN_RADIUS = 2
    private const val SPAWN_MIN = 5
    private const val SPAWN_MID = 7
    private const val SPAWN_MAX = 10

    /** OPEN-SH6: odds, jump air time, spawn fuse ("a few seconds") and cooldown. */
    private const val SPIT_ODDS = 5
    private const val JUMP_ODDS = 6
    private const val SPAWN_ODDS = 6
    private const val JUMP_TICKS = 3
    private const val SPAWN_FUSE = 5
    private const val SPAWN_COOLDOWN = 20L
    private const val HUNT = 16
}
