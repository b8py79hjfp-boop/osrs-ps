package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Bork and the Smorks, TFA room 2 east (docs/mechanics/raids/tfa/bork.md BK0-BK3). Bork's melee shockwave hits around
 * him and his armour grows as the Smorks take damage; the Smorks shoot everyone Bork is not focused on, drop burning
 * oil and heavy cannonballs that daze Bork. Both attack faster as the Smorks' HP falls; whichever dies first enrages
 * the other.
 */
internal object TfaBork : Boss(Npcs.TFA_BORK) {
    private class State {
        var attacks = 0
        var stunnedUntil = -1L
    }

    private val STATE = AttrKey<State>("tfa_bork")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State().also { npc[STATE] = it }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        s.attacks++
        val smorks = smorks(npc)
        if (smorks == null) {
            stomp(npc)
        } else {
            // BK1: "Bork smash [name] now" marks an aggression switch (OPEN-BK4: every 5th attack, a random player).
            if (s.attacks % SWITCH_EVERY == 0) switchTarget(npc)
            shockwave(npc, victim, first = s.attacks == 1)
        }
        npc.setAttackClock(npc.tick + speed(smorks))
    }

    /** BK1: an AoE melee shockwave; the first attack is stronger if the target is out of melee range (OPEN-BK1: max 40, x1.5). */
    private fun shockwave(npc: NpcRef, victim: EntityRef, first: Boolean) {
        npc.anim(Seqs.NPC_BORK_GONG_MELEE01)
        npc.spotAnim(SpotAnims.VFX_BORK_GONG_MELEE_SPOTANIM01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val far = victim is PlayerRef && TfaKit.gap(npc, victim) > 1
        val max = if (first && far) MELEE_MAX * FIRST_NUM / FIRST_DEN else MELEE_MAX
        for (p in TfaKit.arena(npc, SHOCKWAVE_SCAN)) {
            if (TfaKit.gap(npc, p) <= SHOCKWAVE_REACH || p == victim) BossCombat.strike(npc, p, AttackType.CRUSH, max, 1)
        }
    }

    /** BK3: with the Smorks dead Bork stomps the whole arena for heavy melee damage (OPEN-BK3: max 50). */
    private fun stomp(npc: NpcRef) {
        npc.anim(Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK01)
        npc.spotAnim(SpotAnims.VFX_BORK_ENRAGED_STOMP_SPOTANIM01)
        for (p in TfaKit.arena(npc)) BossCombat.strike(npc, p, AttackType.CRUSH, STOMP_MAX, 1)
    }

    /**
     * RuneLite (LU Bork.java:75-118, 411-422): after every stun Bork stomps four times, 3 ticks apart, about 17 ticks
     * after the stun, without moving in between. Wind-up at T ("Bork very angry!!"), the hit tiles flash at T+3, 6, 9,
     * 12 and the damage lands one stomp later: T+6, 9, 12, 15. Safe: the Smorks' shadow (shadowed).
     */
    private fun stompSequence(npc: NpcRef) {
        val t0 = STOMP_AFTER_STUN
        npc.frozenUntil = maxOf(npc.frozenUntil, npc.tick + t0 + STOMP_SPAN)
        npc.setAttackClock(maxOf(npc.attackClock(), npc.tick + t0 + STOMP_SPAN))
        npc.queueHit(t0) {
            if (!npc.isInWorld) return@queueHit
            npc.anim(Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK_IN01)
            npc.say("Bork very angry!!")
        }
        npc.queueHit(t0 + 2) { if (npc.isInWorld) npc.anim(Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK01) }
        npc.queueHit(t0 + STOMP_LOOP_AT) { if (npc.isInWorld) npc.anim(Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK_CYCLE_AND_OUT01) }
        for (k in 1..STOMPS) {
            npc.queueHit(t0 + k * STOMP_GAP) {
                if (!npc.isInWorld) return@queueHit
                for (p in TfaKit.arena(npc)) if (!shadowed(npc, p.coord)) npc.spotAnimAt(p.coord, SpotAnims.TFA_WAVE_DUST_SHORT)
            }
            npc.queueHit(t0 + (k + 1) * STOMP_GAP) {
                if (!npc.isInWorld) return@queueHit
                for (p in TfaKit.arena(npc)) if (!shadowed(npc, p.coord)) BossCombat.strike(npc, p, AttackType.CRUSH, STOMP_MAX, 0)
            }
        }
    }

    /**
     * RuneLite (LU Bork.java:148-224, fitted to 53 logged stomps / 316 safe tiles): a tile in the ring around the Smorks
     * is safe when at least 13 of 100 sample points in it are hidden, seen from Bork's centre (his south-west tile +
     * 2, 2), behind the Smorks' middle box (their south-west tile + 0.5 .. + 2.5).
     */
    private fun shadowed(bork: NpcRef, t: CoordGrid): Boolean {
        val smorks = smorks(bork) ?: return false
        val sx = smorks.coord.x
        val sz = smorks.coord.z
        val ring = t.x in sx - 1..sx + smorks.size && t.z in sz - 1..sz + smorks.size && !TfaKit.under(smorks, t)
        if (!ring) return false
        val ox = bork.coord.x + BORK_CENTRE
        val oz = bork.coord.z + BORK_CENTRE
        var hidden = 0
        for (i in 0 until SAMPLES_PER_SIDE) {
            for (j in 0 until SAMPLES_PER_SIDE) {
                val px = t.x + (i + HALF) / SAMPLES_PER_SIDE
                val pz = t.z + (j + HALF) / SAMPLES_PER_SIDE
                if (segmentHitsBox(ox, oz, px, pz, sx + BOX_MIN, sz + BOX_MIN, sx + BOX_MAX, sz + BOX_MAX)) hidden++
            }
        }
        return hidden >= SAFE_SAMPLES
    }

    /** Whether the segment (ax, az)-(bx, bz) crosses the axis-aligned box (Liang-Barsky clipping). */
    private fun segmentHitsBox(
        ax: Double,
        az: Double,
        bx: Double,
        bz: Double,
        minX: Double,
        minZ: Double,
        maxX: Double,
        maxZ: Double,
    ): Boolean {
        var t0 = 0.0
        var t1 = 1.0
        val dx = bx - ax
        val dz = bz - az
        val p = doubleArrayOf(-dx, dx, -dz, dz)
        val q = doubleArrayOf(ax - minX, maxX - ax, az - minZ, maxZ - az)
        for (k in 0 until SIDES) {
            if (p[k] == 0.0) {
                if (q[k] < 0) return false
            } else {
                val r = q[k] / p[k]
                if (p[k] < 0) t0 = maxOf(t0, r) else t1 = minOf(t1, r)
                if (t0 > t1) return false
            }
        }
        return true
    }

    private fun switchTarget(npc: NpcRef) {
        val players = TfaKit.arena(npc)
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        npc.say("Bork smash ${p.username} now")
        NpcCombat.engage(npc, p)
    }

    /** Bork's target: the Smorks shoot everyone else (BK2). */
    fun focus(bork: NpcRef): EntityRef? = bork.target

    fun stun(bork: NpcRef) {
        val s = state(bork)
        s.stunnedUntil = bork.tick + STUN_TICKS
        bork.anim(Seqs.NPC_BORK_STUN01)
        bork.spotAnim(SpotAnims.VFX_BORK_STUN_SPOTANIM01)
        bork.setAttackClock(maxOf(bork.attackClock(), s.stunnedUntil))
        if (smorks(bork) != null) stompSequence(bork)
    }

    override fun meleeAlwaysHits(npc: NpcRef): Boolean = npc.tick < state(npc).stunnedUntil

    /** BK0: flat armour 8, +2 for every 25% of damage the Smorks have taken (max 14); 0 while stunned. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (npc.tick < s.stunnedUntil || damage <= 0) return damage
        val smorks = smorks(npc)
        val lost = if (smorks == null) PERCENT else (smorks.maxHitpoints - smorks.hitpoints) * PERCENT / maxOf(1, smorks.maxHitpoints)
        val armour = minOf(ARMOUR_MAX, ARMOUR_BASE + ARMOUR_STEP * (lost / ARMOUR_QUARTER))
        return maxOf(0, damage - armour)
    }

    /** BK0: "both bosses attack faster as the Smorks' HP falls" (OPEN-BK3: 7 ticks down to 4). */
    fun speed(smorks: NpcRef?): Int {
        smorks ?: return BASE_SPEED
        val frac = smorks.hitpoints.toDouble() / maxOf(1, smorks.maxHitpoints)
        return MIN_SPEED + ((BASE_SPEED - MIN_SPEED) * frac).toInt()
    }

    fun smorks(bork: NpcRef): NpcRef? = bork.npcsWithin(bork.coord, PAIR_SCAN).firstOrNull { it.type == Npcs.TFA_SMORK && !it.isDying }

    fun bork(smorks: NpcRef): NpcRef? = smorks.npcsWithin(smorks.coord, PAIR_SCAN).firstOrNull { it.type == Npcs.TFA_BORK && !it.isDying }

    /** bork.md data: Bork HP for 2..8 players. */
    private val HP = intArrayOf(1500, 2180, 2810, 3440, 4070, 4700, 5330)

    private const val HUNT = 20
    private const val SWITCH_EVERY = 5
    private const val STOMP_AFTER_STUN = 17
    private const val STOMPS = 4
    private const val STOMP_GAP = 3
    private const val STOMP_LOOP_AT = 8
    private const val STOMP_SPAN = 15
    private const val BORK_CENTRE = 2.0
    private const val BOX_MIN = 0.5
    private const val BOX_MAX = 2.5
    private const val SAMPLES_PER_SIDE = 10
    private const val SAFE_SAMPLES = 13
    private const val HALF = 0.5
    private const val SIDES = 4
    private const val MELEE_MAX = 40
    private const val FIRST_NUM = 3
    private const val FIRST_DEN = 2
    private const val STOMP_MAX = 50
    private const val SHOCKWAVE_SCAN = 8
    private const val SHOCKWAVE_REACH = 2

    /** RuneLite (LU Bork.java:260): the cannon stun lasts 16 ticks (settles OPEN-BK7). */
    private const val STUN_TICKS = 16
    private const val ARMOUR_BASE = 8
    private const val ARMOUR_STEP = 2
    private const val ARMOUR_MAX = 14
    private const val ARMOUR_QUARTER = 25
    private const val PERCENT = 100
    private const val BASE_SPEED = 7
    private const val MIN_SPEED = 4
    private const val PAIR_SCAN = 30
}

/** The Smorks (bork.md BK2, BK3): ranged and magic at everyone Bork is not on, oil, heavy cannonballs. */
internal object TfaSmorks : Boss(Npcs.TFA_SMORK) {
    private val ATTACKS = AttrKey<Int>("tfa_smorks_attacks")
    private val SHOTS = AttrKey<Int>("tfa_smorks_shots")
    private val BOMB_AT = AttrKey<Int>("tfa_smorks_bomb_at")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        TfaKit.scale(npc, HP)
        val n = (npc[ATTACKS] ?: 0) + 1
        npc[ATTACKS] = n
        val bork = TfaBork.bork(npc)
        val players = TfaKit.arena(npc)
        // BK3: with Bork dead they hit everyone, much faster and harder.
        val focus = bork?.let { TfaBork.focus(it) }
        val targets = if (bork == null || players.size < 2) players else players.filter { it != focus }
        // RuneLite (LU Bork.java:445-452, BorkOverlay.java:20): a fire bomb after 5 or 6 normal shots (the bomb resets
        // the count), aimed at whoever they are attacking when they pick; a big cannonball about 20 ticks after each bomb.
        val shots = (npc[SHOTS] ?: 0) + 1
        val bombAt = npc[BOMB_AT] ?: (BOMB_MIN + npc.random(2)).also { npc[BOMB_AT] = it }
        if (shots > bombAt) {
            npc[SHOTS] = 0
            npc[BOMB_AT] = BOMB_MIN + npc.random(2)
            oil(npc, players)
            if (bork !=
                null
            ) {
                npc.queueHit(BIG_BALL_AFTER) { if (npc.isInWorld && bork.isInWorld) heavyCannonball(npc, bork, TfaKit.arena(npc)) }
            }
        } else {
            npc[SHOTS] = shots
            volley(npc, targets, enraged = bork == null)
        }
        npc.setAttackClock(npc.tick + if (bork == null) ENRAGED_SPEED else TfaBork.speed(npc))
    }

    /** BK2: ranged and magic at all players not focused by Bork (OPEN-BK5: max 25, alternating styles). */
    private fun volley(npc: NpcRef, targets: List<PlayerRef>, enraged: Boolean) {
        val ranged = (npc[ATTACKS] ?: 0) % 2 == 0
        npc.anim(Seqs.NPC_SMORK_CANNON_ATTACK01)
        val max = if (enraged) VOLLEY_MAX * ENRAGED_NUM / ENRAGED_DEN else VOLLEY_MAX
        for (p in targets) {
            val style = if (ranged) CombatStyle.RANGED else CombatStyle.MAGIC
            val delay = BossCombat.delay(npc, p, style)
            BossCombat.projectile(
                npc,
                p,
                if (ranged) SpotAnims.VFX_SMORK_RANGE_ATTACK_PROJANIM01 else SpotAnims.VFX_SMORK_MAGE_ATTACK_PROJANIM01,
                delay,
            )
            BossCombat.strike(npc, p, if (ranged) AttackType.RANGED else AttackType.MAGIC, max, delay)
        }
    }

    /** BK2: incendiary cannonballs leave oil that burns 6-7 per tick (OPEN-BK6: 3x3 under a player, 8 ticks). */
    private fun oil(npc: NpcRef, players: List<PlayerRef>) {
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        val centre = p.coord
        npc.anim(Seqs.NPC_SMORK_BIG_CANNON_ATTACK01)
        npc.spotAnimAt(centre, SpotAnims.VFX_SMORK_NAPALM_SPLAT_SPOTANIM01)
        for (t in 1..OIL_TICKS) {
            npc.queueHit(OIL_LAND + t) {
                if (!npc.isInWorld) return@queueHit
                if (t % OIL_FX_EVERY == 1) npc.spotAnimAt(centre, SpotAnims.VFX_SMORK_NAPALM_FIRE_SPOTANIM01)
                for (v in BossCombat.playersOn(npc, centre, radius = 1)) {
                    CombatSystem.applyHit(v, npc, BossCombat.roll(npc, OIL_MIN, OIL_MAX), HitStyle.TYPELESS)
                }
            }
        }
    }

    /** BK2: a heavy cannonball landing on Bork stuns him (OPEN-BK7: aimed at a player; 20 damage within 1 tile). */
    private fun heavyCannonball(npc: NpcRef, bork: NpcRef, players: List<PlayerRef>) {
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        val at: CoordGrid = p.coord
        npc.anim(Seqs.NPC_SMORK_BIG_CANNON_ATTACK01)
        npc.spotAnim(SpotAnims.VFX_SMORK_BIG_CANNON_ATTACK_FLASH_SPOTANIM01)
        npc.queueHit(HEAVY_LAND) {
            if (!npc.isInWorld) return@queueHit
            npc.spotAnimAt(at, SpotAnims.VFX_SMORK_BIG_CANNON_ATTACK_SPOTANIM01)
            for (v in BossCombat.playersOn(npc, at, radius = 1)) CombatSystem.applyHit(v, npc, HEAVY_DAMAGE, HitStyle.TYPELESS)
            val bx = (at.x - bork.coord.x) in -1..bork.size
            val bz = (at.z - bork.coord.z) in -1..bork.size
            if (bork.isInWorld && bx && bz) TfaBork.stun(bork)
        }
    }

    /** bork.md data: Smorks HP for 2..8 players. */
    private val HP = intArrayOf(600, 870, 1120, 1370, 1630, 1880, 2130)

    private const val HUNT = 20
    private const val BOMB_MIN = 5
    private const val BIG_BALL_AFTER = 20
    private const val ENRAGED_SPEED = 3
    private const val VOLLEY_MAX = 25
    private const val ENRAGED_NUM = 3
    private const val ENRAGED_DEN = 2
    private const val OIL_TICKS = 8
    private const val OIL_LAND = 2
    private const val OIL_FX_EVERY = 3
    private const val OIL_MIN = 6
    private const val OIL_MAX = 7
    private const val HEAVY_LAND = 3
    private const val HEAVY_DAMAGE = 20
}
