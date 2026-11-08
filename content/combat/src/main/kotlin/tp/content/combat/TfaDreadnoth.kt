package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Dreadnoth the Jellied, TFA room 3 east (docs/mechanics/raids/tfa/dreadnoth.md DR1-DR4; rotation and numbers from
 * the live-game captures (capture-values.md), timings from RuneLite (D-080)).
 *
 * He crawls a tile every 2 ticks and melees everyone within a tile of his 5x5 on a 4-tick clock. At 85 / 65 / 45% a
 * slam phase: k slams 6 ticks apart, each a jump (seq 14777) that lands 6 ticks later hitting every raider, with three
 * shots and blood tiles; the phase's jellies (min(12, 3 x raiders x k)) hatch 8 ticks after the last. Blood tiles
 * (loc 62609) stay all fight. Below ~25% ("*glub glub*") he leaps at his target every 8 ticks (4 to land, RuneLite),
 * healing what the landing deals and crushing jellies for their HP. He bursts into seven jellies.
 */
internal object TfaDreadnoth : Boss(Npcs.TFA_JELLY_BOSS) {
    private class State {
        var phasesDone = 0
        var enraged = false
        var busyUntil = -1L

        /** The crawl's own hold (alternate ticks): not a root, so it never stops the enrage leap. */
        var crawlHoldUntil = -1L
        val blood = HashSet<CoordGrid>()
        val bloodHit = HashMap<String, Long>()
    }

    private val STATE = AttrKey<State>("tfa_dreadnoth")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State().also {
            npc[STATE] = it
            crawlLoop(npc, it)
        }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    /** Capture: freeze resistance: he holds floor(n x 0.6) of a freeze. */
    override val freezeHold: Double = HOLD

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (npc.tick < s.busyUntil) return
        if (s.enraged) {
            leap(npc, s, victim)
            return
        }
        // Capture: melee on a 4-tick clock whenever anyone is within a tile of his 5x5; never on a step tick.
        val near = TfaKit.arena(npc).filter { TfaKit.gap(npc, it) <= 1 }
        if (near.isNotEmpty()) {
            CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
            for (p in near) {
                val raw = BossCombat.roll(npc, 0, MELEE_MAX)
                CombatSystem.queueHit(p, npc, if (p.protects(CombatStyle.MELEE)) raw * 2 / 3 else raw, HitStyle.MELEE, 0)
            }
        }
        npc.setAttackClock(npc.tick + ATTACK_SPEED)
    }

    /** Capture: he crawls one tile every 2 ticks (held still on alternate ticks). */
    private fun crawlLoop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            if (npc.tick % CRAWL_EVERY == 0L) {
                s.crawlHoldUntil = npc.tick + 1
                npc.frozenUntil = maxOf(npc.frozenUntil, s.crawlHoldUntil)
            }
            bloodTick(npc, s)
            crawlLoop(npc, s)
        }
    }

    /** Capture: a blood tile hits 7 or 8 on the first tick a raider starts on it, then every 2nd tick. */
    private fun bloodTick(npc: NpcRef, s: State) {
        for (p in TfaKit.arena(npc)) {
            if (p.coord !in s.blood) {
                s.bloodHit.remove(p.username)
                continue
            }
            val last = s.bloodHit[p.username]
            if (last == null || npc.tick - last >= BLOOD_EVERY) {
                s.bloodHit[p.username] = npc.tick
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, BLOOD_MIN, BLOOD_MAX), HitStyle.TYPELESS)
            }
        }
    }

    private fun blood(npc: NpcRef, s: State, at: CoordGrid) {
        if (!s.blood.add(at)) return
        npc.addLoc(Locs.TFA_JELLY_BOSS_DAMAGE_TILE, at, FLOOR_SHAPE, 0)
    }

    /**
     * Capture: slam phase k: k slams 6 ticks apart; each is a jump J that hits every raider at landing (22..44, 0 under
     * Protect from Melee) and fires three shots (projectile 4211, 1..33, 0 under Protect from Missiles) plus 1-3 blood
     * tiles; the phase's jellies (min(12, 3 x living raiders x k), at most 3 a raider) hatch at the last landing + 8.
     * RuneLite (LU): a jump lands 6 ticks after it starts.
     */
    private fun slamPhase(npc: NpcRef, s: State, k: Int) {
        val span = (k - 1) * SLAM_GAP + JUMP_LAND_SLOW
        s.busyUntil = npc.tick + span + HATCH_AFTER
        npc.setAttackClock(s.busyUntil)
        for (i in 0 until k) {
            npc.queueHit(i * SLAM_GAP) {
                if (!npc.isInWorld || npc.isDying) return@queueHit
                npc.anim(Seqs.DREADNOTH_JUMP01)
                npc.queueHit(JUMP_LAND_SLOW) { if (npc.isInWorld && !npc.isDying) slamLands(npc, s) }
            }
        }
        val players = TfaKit.arena(npc)
        val jellies = minOf(MAX_JELLIES, JELLIES_PER * players.size * k)
        npc.queueHit(span + HATCH_AFTER) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            val targets = TfaKit.arena(npc)
            for (j in 0 until jellies) {
                val near = targets.getOrNull(j % maxOf(1, targets.size))?.coord ?: npc.coord
                Bosses.summon(npc, Npcs.TFA_JELLY_MINION, BossCombat.randomTile(npc, near, JELLY_SPREAD))
            }
        }
    }

    private fun slamLands(npc: NpcRef, s: State) {
        npc.spotAnim(SpotAnims.TFA_BOSS_JELLY_5X5_JUMP)
        val players = TfaKit.arena(npc)
        for (p in players) {
            val dmg = if (p.protects(CombatStyle.MELEE)) 0 else BossCombat.roll(npc, SLAM_MIN, SLAM_MAX)
            CombatSystem.applyHit(p, npc, dmg, HitStyle.MELEE)
        }
        repeat(SLAM_SHOTS) { k ->
            val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return@repeat
            val flight = SHOT_FIRST + k
            BossCombat.projectile(npc, p, SpotAnims.VFX_DREADNOTH_RANGED_PROJ01, flight)
            CombatSystem.queueHit(
                p,
                npc,
                if (p.protects(CombatStyle.RANGED)) 0 else BossCombat.roll(npc, 1, SHOT_MAX),
                HitStyle.RANGED,
                flight,
            )
        }
        repeat(1 + npc.random(SLAM_BLOOD)) { blood(npc, s, BossCombat.randomTile(npc, npc.coord, BLOOD_SPREAD)) }
    }

    /**
     * Capture: the enrage leap (seq 14780) every 8 ticks: centred on his target if within 5 tiles each way, else 5
     * tiles in the nearest of 8 ways; RuneLite: it lands 4 ticks later. Under his 5x5 25..44 and he heals what he deals;
     * within a tile of it 16..38 (0 under Protect from Melee); 1-5 blood tiles (projectile 4212); jellies under him are
     * crushed and he heals their HP. A root (e.g. the scorching bow's special) stops the leap (DR1).
     */
    private fun leap(npc: NpcRef, s: State, victim: EntityRef) {
        npc.setAttackClock(npc.tick + LEAP_EVERY)
        // DR1: only a real root stops the leap, not the crawl's alternate-tick hold.
        if (npc.isFrozen && npc.frozenUntil > s.crawlHoldUntil) return
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val dx = victim.coord.x - cx
        val dz = victim.coord.z - cz
        val to =
            if (kotlin.math.abs(dx) <= LEAP_REACH && kotlin.math.abs(dz) <= LEAP_REACH) {
                CoordGrid(victim.coord.x - npc.size / 2, victim.coord.z - npc.size / 2, npc.coord.level)
            } else {
                CoordGrid(npc.coord.x + Integer.signum(dx) * LEAP_REACH, npc.coord.z + Integer.signum(dz) * LEAP_REACH, npc.coord.level)
            }
        npc.anim(Seqs.DREADNOTH_JUMP02)
        npc.queueHit(JUMP_LAND) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            npc.teleport(to)
            npc.spotAnim(SpotAnims.TFA_BOSS_JELLY_5X5_JUMP)
            var healed = 0
            for (p in TfaKit.arena(npc, npc.size + 2)) {
                val gap = TfaKit.gap(npc, p)
                if (gap == 0) {
                    val dmg = BossCombat.roll(npc, LAND_MIN, LAND_MAX)
                    healed += dmg
                    CombatSystem.applyHit(p, npc, dmg, HitStyle.MELEE)
                } else if (gap == 1 && !p.protects(CombatStyle.MELEE)) {
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, SPLASH_MIN, SPLASH_MAX), HitStyle.MELEE)
                }
            }
            for (j in Bosses.minions(npc, Npcs.TFA_JELLY_MINION)) {
                if (TfaKit.under(npc, j.coord)) {
                    healed += j.hitpoints
                    j.despawn()
                }
            }
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + healed)
            repeat(1 + npc.random(LAND_BLOOD)) { blood(npc, s, BossCombat.randomTile(npc, npc.coord, BLOOD_SPREAD + npc.size)) }
        }
    }

    /** Scaled before the first hit lands (else the first hit comes off the cache HP and the pool refill wipes it). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        state(npc)
        return damage
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        if (s.phasesDone < SLAM_AT.size && pct <= SLAM_AT[s.phasesDone] && npc.tick >= s.busyUntil) {
            s.phasesDone++
            slamPhase(npc, s, s.phasesDone)
        }
        if (!s.enraged && pct <= ENRAGE_AT) {
            s.enraged = true
            // No slam phase after the enrage, even when one burst crossed their thresholds.
            s.phasesDone = SLAM_AT.size
            npc.say("*glub glub*")
            npc.setAttackClock(npc.tick + FIRST_LEAP)
        }
    }

    /** Capture: seven jellies jump out 3 ticks after his death, on seven distinct tiles of his 5x5, never the middle. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        val tiles =
            (0 until npc.size)
                .flatMap { x ->
                    (0 until npc.size).map { z -> x to z }
                }.filter { it != MIDDLE to MIDDLE }
                .toMutableList()
        val origin = npc.coord
        // His slam-phase jellies go with him; the seven of the burst are minions too, so they never respawn.
        Bosses.dismissMinions(npc)
        npc.queueHit(DEATH_JELLY_DELAY) {
            repeat(DEATH_JELLIES) {
                val (x, z) = tiles.removeAt(npc.random(tiles.size))
                Bosses.summon(npc, Npcs.TFA_JELLY_MINION, CoordGrid(origin.x + x, origin.z + z, origin.level), huntRange = HUNT)
            }
        }
    }

    /** dreadnoth.md data (capture-confirmed): HP for 2..8 players. */
    private val HP = intArrayOf(2100, 3050, 3930, 4810, 5690, 6570, 7460)

    /** DR2: slam phases at 85%, 65% and 45% HP. */
    private val SLAM_AT = intArrayOf(85, 65, 45)

    private const val HUNT = 20
    private const val HOLD = 0.6
    private const val ATTACK_SPEED = 4
    private const val MELEE_MAX = 45
    private const val CRAWL_EVERY = 2
    private const val BLOOD_EVERY = 2
    private const val BLOOD_MIN = 7
    private const val BLOOD_MAX = 8
    private const val FLOOR_SHAPE = 22
    private const val SLAM_GAP = 6
    private const val JUMP_LAND_SLOW = 6
    private const val HATCH_AFTER = 8
    private const val MAX_JELLIES = 12
    private const val JELLIES_PER = 3
    private const val JELLY_SPREAD = 2
    private const val SLAM_MIN = 22
    private const val SLAM_MAX = 44
    private const val SLAM_SHOTS = 3
    private const val SHOT_FIRST = 3
    private const val SHOT_MAX = 33
    private const val SLAM_BLOOD = 3
    private const val BLOOD_SPREAD = 4
    private const val LEAP_EVERY = 8
    private const val FIRST_LEAP = 5
    private const val LEAP_REACH = 5
    private const val JUMP_LAND = 4
    private const val LAND_MIN = 25
    private const val LAND_MAX = 44
    private const val SPLASH_MIN = 16
    private const val SPLASH_MAX = 38
    private const val LAND_BLOOD = 5
    private const val ENRAGE_AT = 25
    private const val PERCENT = 100
    private const val DEATH_JELLIES = 7
    private const val DEATH_JELLY_DELAY = 3
    private const val MIDDLE = 2
}

/** A Dreadnoth jelly (capture): 75 HP (cache), first swing 2 ticks after hatching then every 3, 5..10; holds 0.8 of a freeze. */
internal object TfaJelly : Boss(Npcs.TFA_JELLY_MINION) {
    private val HATCHED = AttrKey<Long>("tfa_jelly_hatched")

    override val freezeHold: Double = HOLD

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
        TfaKit.setPool(npc, HP)
        npc[HATCHED] = npc.tick
        npc.setAttackClock(npc.tick + FIRST_SWING)
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (distance) return
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val dmg = if (victim is PlayerRef && victim.protects(CombatStyle.MELEE)) 0 else BossCombat.roll(npc, MIN_HIT, MAX_HIT)
        CombatSystem.queueHit(victim, npc, dmg, HitStyle.MELEE, 0)
        npc.setAttackClock(npc.tick + SWING_EVERY)
    }

    private const val HUNT = 16
    private const val HP = 75
    private const val HOLD = 0.8
    private const val FIRST_SWING = 2
    private const val SWING_EVERY = 3
    private const val MIN_HIT = 5
    private const val MAX_HIT = 10
}
