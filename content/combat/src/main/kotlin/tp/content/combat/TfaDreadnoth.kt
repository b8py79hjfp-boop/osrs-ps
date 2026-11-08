package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Dreadnoth the Jellied, TFA room 3 east (docs/mechanics/raids/tfa/dreadnoth.md DR1-DR4): melee on everyone around
 * him; at 85%, 65% and 45% one, two and three arena slams each followed by three ranged attacks, leaving floor jelly
 * and spawning jellies he can absorb; below 25% he jumps on random players; he bursts into jellies on death.
 */
internal object TfaDreadnoth : Boss(Npcs.TFA_JELLY_BOSS) {
    private class State {
        var slamsDone = 0
        var queuedSlams = 0
        var rangedLeft = 0
        var enraged = false
    }

    private val STATE = AttrKey<State>("tfa_dreadnoth")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State().also { npc[STATE] = it }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        when {
            s.enraged -> {
                jump(npc)
            }

            s.queuedSlams > 0 -> {
                s.queuedSlams--
                slam(npc)
                s.rangedLeft += RANGED_AFTER_SLAM
            }

            s.rangedLeft > 0 -> {
                s.rangedLeft--
                ranged(npc, victim)
            }

            else -> {
                melee(npc, victim)
            }
        }
    }

    /** DR1: melee hits everyone around him. */
    private fun melee(npc: NpcRef, victim: EntityRef) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        for (p in TfaKit.arena(npc, npc.size + 1)) if (TfaKit.gap(npc, p) <= 1) BossCombat.strike(npc, p, AttackType.CRUSH, MAX_HIT, 0)
    }

    /** RuneLite (LU Dreadnoth.java:47-52): a blood blob (projectile 4211/4212) aimed at a player is his ranged attack. */
    private fun ranged(npc: NpcRef, victim: EntityRef) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.VFX_DREADNOTH_RANGED_PROJ01, delay)
        BossCombat.strike(npc, victim, AttackType.RANGED, MAX_HIT, delay)
    }

    /**
     * DR2: an arena-wide slam (Protect from Melee) that leaves floor jelly (6-8 per tick) and spawns jellies.
     * OPEN-DR1: one jelly per two players; OPEN-DR3: slam max 45, jelly puddles 3x3 under each player for 10 ticks.
     */
    private fun slam(npc: NpcRef) {
        // RuneLite (LU Dreadnoth.java:61-67): a slam is a jump (seq 14777) that lands 6 ticks later.
        npc.anim(Seqs.DREADNOTH_JUMP01)
        npc.setAttackClock(npc.tick + JUMP_LAND_SLOW)
        npc.queueHit(JUMP_LAND_SLOW) {
            if (!npc.isInWorld) return@queueHit
            npc.spotAnim(SpotAnims.TFA_BOSS_JELLY_5X5_JUMP)
            val players = TfaKit.arena(npc)
            for (p in players) BossCombat.strike(npc, p, AttackType.CRUSH, MAX_HIT, 0)
            for (p in players) splatter(npc, p.coord)
            repeat(maxOf(1, players.size / 2)) {
                val at = BossCombat.randomTile(npc, npc.coord, JELLY_SPREAD)
                Bosses.summon(npc, Npcs.TFA_JELLY_MINION, at)
            }
        }
    }

    /**
     * RuneLite (LU Dreadnoth.java:223-241): a blob aimed at a tile leaves floor jelly, loc 62609
     * `TFA_JELLY_BOSS_DAMAGE_TILE` (6-8 per tick, DR2); OPEN-DR3: one tile per player for 10 ticks.
     */
    private fun splatter(npc: NpcRef, at: CoordGrid) {
        npc.queueHit(BLOB_FLIGHT) {
            if (!npc.isInWorld) return@queueHit
            npc.addLoc(Locs.TFA_JELLY_BOSS_DAMAGE_TILE, at, FLOOR_SHAPE, 0, PUDDLE_TICKS)
            for (t in 1..PUDDLE_TICKS) {
                npc.queueHit(t) {
                    if (!npc.isInWorld) return@queueHit
                    for (v in BossCombat.playersOn(
                        npc,
                        at,
                    )) {
                        CombatSystem.applyHit(v, npc, BossCombat.roll(npc, PUDDLE_MIN, PUDDLE_MAX), HitStyle.TYPELESS)
                    }
                }
            }
        }
    }

    /** DR3: jumps on a random player, healing ~30-40 if they are underneath, then one ranged attack (OPEN-DR4: heal 35). */
    private fun jump(npc: NpcRef) {
        // DR1: a root (the scorching bow's special binds demons for 20 ticks, SP55) holds him in place.
        if (npc.isFrozen) {
            ranged(npc, TfaKit.arena(npc).firstOrNull() ?: return)
            return
        }
        val players = TfaKit.arena(npc)
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        val at = p.coord
        npc.anim(Seqs.DREADNOTH_JUMP02)
        npc.say("*glub glub*")
        // RuneLite (LU Dreadnoth.java:65-72): after "glub glub" (or below 25%) a jump lands in 4 ticks and he waits 4
        // more before the next.
        npc.setAttackClock(npc.tick + JUMP_LAND + JUMP_COOLDOWN)
        npc.queueHit(JUMP_LAND) {
            if (!npc.isInWorld) return@queueHit
            npc.teleport(CoordGrid(at.x - npc.size / 2, at.z - npc.size / 2, at.level))
            npc.spotAnim(SpotAnims.TFA_BOSS_JELLY_5X5_JUMP)
            for (v in TfaKit.arena(npc, npc.size + 1)) {
                if (TfaKit.gap(npc, v) == 0) {
                    BossCombat.strike(npc, v, AttackType.CRUSH, MAX_HIT, 0)
                    npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + JUMP_HEAL)
                }
            }
            ranged(npc, p)
        }
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        while (s.slamsDone < SLAM_AT.size && pct <= SLAM_AT[s.slamsDone]) {
            s.slamsDone++
            s.queuedSlams += s.slamsDone
        }
        if (!s.enraged && pct <= ENRAGE_AT) s.enraged = true
        absorb(npc)
    }

    /** DR2: he absorbs jellies he lands on and heals (OPEN-DR5: their remaining HP). */
    private fun absorb(npc: NpcRef) {
        for (j in Bosses.minions(npc, Npcs.TFA_JELLY_MINION)) {
            if (TfaKit.under(npc, j.coord)) {
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + j.hitpoints)
                j.despawn()
            }
        }
    }

    /** DR4 / OPEN-DR2: he bursts into seven jellies (the jelly page's "always 7"). */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        repeat(DEATH_JELLIES) {
            val at = BossCombat.randomTile(npc, npc.coord, JELLY_SPREAD)
            val j = npc.spawnNpc(Npcs.TFA_JELLY_MINION, at, wanderRange = 0)
            j.huntRange = HUNT
        }
    }

    /** dreadnoth.md data: HP for 2..8 players. */
    private val HP = intArrayOf(2100, 3050, 3930, 4810, 5690, 6570, 7460)

    /** DR2: slams at 85%, 65% and 45% HP. */
    private val SLAM_AT = intArrayOf(85, 65, 45)

    private const val HUNT = 20
    private const val MAX_HIT = 45
    private const val RANGED_AFTER_SLAM = 3
    private const val JELLY_SPREAD = 5
    private const val PUDDLE_TICKS = 10
    private const val PUDDLE_MIN = 6
    private const val PUDDLE_MAX = 8
    private const val JUMP_LAND = 4
    private const val JUMP_COOLDOWN = 4
    private const val JUMP_LAND_SLOW = 6
    private const val BLOB_FLIGHT = 2
    private const val FLOOR_SHAPE = 22
    private const val JUMP_HEAL = 35
    private const val ENRAGE_AT = 25
    private const val PERCENT = 100
    private const val DEATH_JELLIES = 7
}
