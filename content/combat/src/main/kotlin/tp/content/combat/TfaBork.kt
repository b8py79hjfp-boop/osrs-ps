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
 * Bork and the Smorks, TFA room 2 east (docs/mechanics/raids/tfa/bork.md BK0-BK3; rotations and numbers from the
 * live-game captures (capture-values.md), timings from RuneLite and the cache (D-080)).
 *
 * Bork's drum and the Smorks share one rate: 7 ticks, then 6, 5 and 4 at 75 / 50 / 25% of the Smorks' HP. Bork's gong
 * is two beats; the Smorks run a fixed 7-slot cycle (shot, shot, napalm, -, shot, shot, big bomb). A big bomb that
 * lands under Bork stuns him 14 ticks (the cache's `NPC_BORK_14_CYCLE_STUN01`; dazed: no armour, melee never misses)
 * and he then stomps four times over the arena but the cart's shadow, and only then picks a new target; a missed bomb
 * makes him stomp at once. Flat armour 10 on every melee hit otherwise.
 */
internal object TfaBork : Boss(Npcs.TFA_BORK) {
    private class State {
        var stunnedUntil = -1L
        var started = false
        var stomping = false
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
        if (!s.started) {
            // Capture: the first gong comes 8 ticks after he engages.
            s.started = true
            npc.setAttackClock(npc.tick + FIRST_GONG)
            return
        }
        if (s.stomping || npc.tick < s.stunnedUntil) return
        val smorks = smorks(npc)
        if (smorks == null) stompSet(npc, 0) else gong(npc, victim)
        npc.setAttackClock(npc.tick + rate(smorks))
    }

    /**
     * Capture: the gong is two beats, T and T+2. Beat 1 reaches max(1, his target's distance from his 4x4), beat 2 the
     * next ring; the cart's shadow is safe. Beat 1 16..28 (a quarter under Protect from Melee, 4..7), beat 2 20..33
     * (5..8).
     */
    private fun gong(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_BORK_GONG_MELEE01)
        npc.spotAnim(SpotAnims.VFX_BORK_GONG_MELEE_SPOTANIM01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val reach = maxOf(1, TfaKit.gap(npc, victim))
        for ((beat, ring) in listOf(0 to (1..reach), 2 to (reach + 1..reach + 1))) {
            npc.queueHit(beat) {
                if (!npc.isInWorld) return@queueHit
                val (min, max) = if (beat == 0) BEAT1 else BEAT2
                for (p in TfaKit.arena(npc)) {
                    if (TfaKit.gap(npc, p) !in ring || shadowed(npc, p.coord)) continue
                    val raw = BossCombat.roll(npc, min, max)
                    CombatSystem.applyHit(p, npc, if (p.protects(CombatStyle.MELEE)) raw / PRAYED_QUARTER else raw, HitStyle.MELEE)
                }
            }
        }
    }

    /**
     * Capture / RuneLite: a stomp set: the wind-up ("Bork very angry!!", seq 14549) at [windUp] ticks from now, then four
     * slams 3 ticks apart over the whole arena except the Smorks' shadow (14..50; 18..33 under Protect from Melee); a
     * new random target 3 ticks after the last (announced only when it changes), the drum 2 ticks later.
     */
    private fun stompSet(npc: NpcRef, windUp: Int) {
        val s = state(npc)
        s.stomping = true
        npc.frozenUntil = maxOf(npc.frozenUntil, npc.tick + windUp + STOMP_SPAN)
        npc.queueHit(windUp) {
            if (!npc.isInWorld) return@queueHit
            npc.anim(Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK_IN01)
            npc.say("Bork very angry!!")
        }
        for (k in 1..STOMPS) {
            npc.queueHit(windUp + k * STOMP_GAP) {
                if (!npc.isInWorld) return@queueHit
                npc.anim(if (k == STOMPS) Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK_OUT01 else Seqs.NPC_BORK_ENRAGED_STOMP_ATTACK01)
                npc.spotAnim(SpotAnims.VFX_BORK_ENRAGED_STOMP_SPOTANIM01)
                for (p in TfaKit.arena(npc)) {
                    if (shadowed(npc, p.coord)) continue
                    npc.spotAnimAt(p.coord, SpotAnims.TFA_WAVE_DUST_SHORT)
                    val dmg =
                        if (p.protects(
                                CombatStyle.MELEE,
                            )
                        ) {
                            BossCombat.roll(npc, SLAM_PRAYED_MIN, SLAM_PRAYED_MAX)
                        } else {
                            BossCombat.roll(npc, SLAM_MIN, SLAM_MAX)
                        }
                    CombatSystem.applyHit(p, npc, dmg, HitStyle.MELEE)
                }
            }
        }
        val retarget = windUp + STOMPS * STOMP_GAP + RETARGET_AFTER
        npc.queueHit(retarget) {
            if (!npc.isInWorld) return@queueHit
            s.stomping = false
            retarget(npc)
            npc.setAttackClock(npc.tick + DRUM_AFTER)
        }
        npc.setAttackClock(npc.tick + retarget + DRUM_AFTER)
    }

    /** Capture: a new random raider; "Bork smash <name> now." only when it changes. */
    private fun retarget(npc: NpcRef) {
        val players = TfaKit.arena(npc)
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        if (p != npc.target) npc.say("Bork smash ${p.username} now.")
        NpcCombat.engage(npc, p)
    }

    /** Bork's target: the Smorks shoot everyone else. */
    fun focus(bork: NpcRef): EntityRef? = bork.target

    /** Capture: a big bomb under his 4x4: stunned 14 ticks (seq 14545), then the stomp set 14 ticks after it landed. */
    fun stun(bork: NpcRef) {
        val s = state(bork)
        s.stunnedUntil = bork.tick + STUN_TICKS
        bork.anim(Seqs.NPC_BORK_14_CYCLE_STUN01)
        bork.spotAnim(SpotAnims.VFX_BORK_14_CYCLE_STUN_SPOTANIM01)
        if (smorks(bork) != null) stompSet(bork, STUN_TICKS)
    }

    /** Capture: a missed bomb: the stomp set starts 5 ticks after it landed (slams at +6, 9, 12, 15). */
    fun missed(bork: NpcRef) {
        val s = state(bork)
        if (!s.stomping && bork.tick >= s.stunnedUntil) stompSet(bork, MISS_WINDUP)
    }

    override fun meleeAlwaysHits(npc: NpcRef): Boolean = npc.tick < state(npc).stunnedUntil

    /** Capture (fitted, 98:1): 10 off every melee hit while not dazed, none while dazed. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (npc.tick < s.stunnedUntil || damage <= 0 || style != HitStyle.MELEE) return damage
        return maxOf(0, damage - FLAT_ARMOUR)
    }

    /** Capture: the shared rate: 7, then 6, 5, 4 ticks at 75 / 50 / 25% of the Smorks' HP. */
    fun rate(smorks: NpcRef?): Int {
        smorks ?: return BASE_RATE
        val pct = smorks.hitpoints * PERCENT / maxOf(1, smorks.maxHitpoints)
        return when {
            pct <= RATE_STEPS[2] -> BASE_RATE - 3
            pct <= RATE_STEPS[1] -> BASE_RATE - 2
            pct <= RATE_STEPS[0] -> BASE_RATE - 1
            else -> BASE_RATE
        }
    }

    fun smorks(bork: NpcRef): NpcRef? = bork.npcsWithin(bork.coord, PAIR_SCAN).firstOrNull { it.type == Npcs.TFA_SMORK && !it.isDying }

    fun bork(smorks: NpcRef): NpcRef? = smorks.npcsWithin(smorks.coord, PAIR_SCAN).firstOrNull { it.type == Npcs.TFA_BORK && !it.isDying }

    /**
     * RuneLite (LU Bork.java:148-224, fitted to 53 logged stomps / 316 safe tiles): a tile in the ring around the Smorks
     * is safe when at least 13 of 100 sample points in it are hidden, seen from Bork's centre (his south-west tile +
     * 2, 2), behind the Smorks' middle box (their south-west tile + 0.5 .. + 2.5).
     */
    fun shadowed(bork: NpcRef, t: CoordGrid): Boolean {
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

    /** bork.md data (capture-confirmed): Bork HP for 2..8 players. */
    private val HP = intArrayOf(1500, 2180, 2810, 3440, 4070, 4700, 5330)
    private val RATE_STEPS = intArrayOf(75, 50, 25)
    private val BEAT1 = 16 to 28
    private val BEAT2 = 20 to 33

    private const val HUNT = 20
    private const val FIRST_GONG = 8
    private const val PRAYED_QUARTER = 4
    private const val STOMPS = 4
    private const val STOMP_GAP = 3
    private const val STOMP_SPAN = 15
    private const val RETARGET_AFTER = 3
    private const val DRUM_AFTER = 2
    private const val SLAM_MIN = 14
    private const val SLAM_MAX = 50
    private const val SLAM_PRAYED_MIN = 18
    private const val SLAM_PRAYED_MAX = 33
    private const val STUN_TICKS = 14
    private const val MISS_WINDUP = 3
    private const val FLAT_ARMOUR = 10
    private const val BASE_RATE = 7
    private const val PERCENT = 100
    private const val PAIR_SCAN = 30
    private const val BORK_CENTRE = 2.0
    private const val BOX_MIN = 0.5
    private const val BOX_MAX = 2.5
    private const val SAMPLES_PER_SIDE = 10
    private const val SAFE_SAMPLES = 13
    private const val HALF = 0.5
    private const val SIDES = 4
}

/**
 * The Smorks (bork.md BK2, BK3; capture): a 7-slot cycle every rate tick: shot, shot, napalm, -, shot, shot, big bomb.
 * A shot is one projectile at every raider except Bork's target, one style per shot at random. With Bork dead, from 11
 * ticks after his death they shoot every raider every 2 ticks, the cycle going on.
 */
internal object TfaSmorks : Boss(Npcs.TFA_SMORK) {
    private val SLOT = AttrKey<Int>("tfa_smorks_slot")
    private val BORK_DEAD_AT = AttrKey<Long>("tfa_smorks_bork_dead")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        TfaKit.scale(npc, HP)
        val bork = TfaBork.bork(npc)
        val players = TfaKit.arena(npc)
        if (bork == null) {
            val deadAt = npc[BORK_DEAD_AT] ?: npc.tick.also { npc[BORK_DEAD_AT] = it }
            if (npc.tick - deadAt < ENRAGE_DELAY) {
                npc.setAttackClock(deadAt + ENRAGE_DELAY)
                return
            }
        }
        val slot = npc[SLOT] ?: 0
        npc[SLOT] = (slot + 1) % CYCLE.length
        val focus = bork?.let { TfaBork.focus(it) }
        when (CYCLE[slot]) {
            'S' -> shot(npc, if (bork == null) players else players.filter { it != focus || players.size == 1 }, bork == null)
            'N' -> napalm(npc, bork, players)
            'B' -> if (bork != null) bigBomb(npc, bork) else shot(npc, players, true)
            else -> Unit
        }
        npc.setAttackClock(npc.tick + if (bork == null) ENRAGED_RATE else TfaBork.rate(npc))
    }

    /**
     * Capture: a shot: magic (projectile 4057) or ranged (4056) for the whole shot, at every raider but Bork's target,
     * landing (end + 15) / 30 ticks later (end 150 / 120 / 90 / 90 client cycles at rate 7 / 6 / 5 / 4; 90 with Bork
     * dead). Damage (OPEN-BK5 model of the capture's distribution): the matching protection prayer blocks it (a 1 a
     * quarter of the time), otherwise 11..30 (10..28 with Bork dead).
     */
    private fun shot(npc: NpcRef, targets: List<PlayerRef>, enraged: Boolean) {
        val magic = npc.random(2) == 0
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        npc.anim(Seqs.NPC_SMORK_CANNON_ATTACK01)
        val end = if (enraged) END_FAST else END_BY_RATE[(TfaBork.rate(npc) - ENRAGED_RATE).coerceIn(0, END_BY_RATE.size - 1)]
        val delay = (end + CYCLE_PAD) / CYCLES_PER_TICK
        for (p in targets) {
            BossCombat.projectile(
                npc,
                p,
                if (magic) SpotAnims.VFX_SMORK_MAGE_ATTACK_PROJANIM01 else SpotAnims.VFX_SMORK_RANGE_ATTACK_PROJANIM01,
                delay,
            )
            val dmg =
                if (p.protects(style)) {
                    if (npc.random(CHIP_ONE_IN) == 0) 1 else 0
                } else if (enraged) {
                    BossCombat.roll(npc, ENRAGED_MIN, ENRAGED_MAX)
                } else {
                    BossCombat.roll(npc, SHOT_MIN, SHOT_MAX)
                }
            CombatSystem.queueHit(p, npc, dmg, CombatSystem.hitStyle(style), delay)
        }
    }

    /**
     * Capture: napalm (projectile 4045) lands 4 ticks later: 25..28 on its tile, a patch of 3..17 ragged tiles (loc
     * 62544) in one or two streaks lasting 199 ticks, 2 a tick on one and a burn of 1-3 every 4 ticks five times;
     * landing on Bork's 4x4 hits him for 25..28. Aimed at whoever they are attacking (RuneLite: the target is locked).
     */
    private fun napalm(npc: NpcRef, bork: NpcRef?, players: List<PlayerRef>) {
        val target = (npc.target as? PlayerRef) ?: players.firstOrNull() ?: return
        val at = target.coord
        npc.anim(Seqs.NPC_SMORK_BIG_CANNON_ATTACK01)
        BossCombat.projectile(npc, target, SpotAnims.VFX_SMORK_NAPALM_FIRE_PROJANIM01, NAPALM_FLIGHT)
        npc.queueHit(NAPALM_FLIGHT) {
            if (!npc.isInWorld) return@queueHit
            npc.spotAnimAt(at, SpotAnims.VFX_SMORK_NAPALM_SPLAT_SPOTANIM01)
            for (p in BossCombat.playersOn(
                npc,
                at,
            )) {
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, NAPALM_MIN, NAPALM_MAX), HitStyle.TYPELESS)
            }
            if (bork != null &&
                TfaKit.under(bork, at)
            ) {
                CombatSystem.applyHit(bork, npc, BossCombat.roll(npc, NAPALM_MIN, NAPALM_MAX), HitStyle.TYPELESS)
            }
            val tiles = ragged(npc, at)
            for (t in tiles) npc.addLoc(Locs.TFA_BORK_RAGGED_TILE, t, FLOOR_SHAPE, 0, RAGGED_TICKS)
            burnLoop(npc, tiles, RAGGED_TICKS)
        }
    }

    private fun ragged(npc: NpcRef, at: CoordGrid): List<CoordGrid> {
        val out = ArrayList<CoordGrid>()
        out += at
        val streaks = 1 + npc.random(2)
        val count = RAGGED_MIN + npc.random(RAGGED_MAX - RAGGED_MIN + 1)
        repeat(streaks) {
            val (dx, dz) = DIRS[npc.random(DIRS.size)]
            for (k in 1..count / streaks) out += CoordGrid(at.x + dx * k, at.z + dz * k, at.level)
        }
        return out.distinct()
    }

    private fun burnLoop(npc: NpcRef, tiles: List<CoordGrid>, left: Int) {
        if (left <= 0) return
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (!TfaKit.on(p, tiles)) continue
                CombatSystem.applyHit(p, npc, RAGGED_DAMAGE, HitStyle.TYPELESS)
                if ((p[BURNING_UNTIL] ?: 0L) < p.tick) {
                    p[BURNING_UNTIL] = p.tick + BURN_TICKS * BURN_EVERY
                    for (k in 1..BURN_TICKS) {
                        p.queueHit(k * BURN_EVERY, strong = false) {
                            if (!p.isDying) {
                                CombatSystem.applyHit(
                                    p,
                                    npc,
                                    1 + npc.random(BURN_MAX),
                                    HitStyle.TYPELESS,
                                )
                            }
                        }
                    }
                }
            }
            burnLoop(npc, tiles, left - 1)
        }
    }

    /**
     * Capture: the big bomb (projectile 4440) lands 5 ticks after its shot, at Bork's target (OPEN-BK9): if Bork's 4x4
     * was over the tile the tick before he is stunned (and hit 25..28 when it is one tile off his 4x4); otherwise it
     * hurts no one and Bork stomps at once.
     */
    private fun bigBomb(npc: NpcRef, bork: NpcRef) {
        val at = (TfaBork.focus(bork) as? PlayerRef)?.coord ?: return
        npc.anim(Seqs.NPC_SMORK_BIG_CANNON_ATTACK01)
        npc.spotAnim(SpotAnims.VFX_SMORK_BIG_CANNON_ATTACK_FLASH_SPOTANIM01)
        var underBefore = false
        npc.queueHit(BOMB_FLIGHT - 1) { underBefore = bork.isInWorld && TfaKit.under(bork, at) }
        npc.queueHit(BOMB_FLIGHT) {
            if (!npc.isInWorld || !bork.isInWorld) return@queueHit
            npc.spotAnimAt(at, SpotAnims.VFX_SMORK_BIG_CANNON_ATTACK_SPOTANIM01)
            if (underBefore) {
                TfaBork.stun(bork)
            } else {
                val oneOff = at.x in bork.coord.x - 1..bork.coord.x + bork.size && at.z in bork.coord.z - 1..bork.coord.z + bork.size
                if (oneOff) CombatSystem.applyHit(bork, npc, BossCombat.roll(npc, NAPALM_MIN, NAPALM_MAX), HitStyle.TYPELESS)
                TfaBork.missed(bork)
            }
        }
    }

    private val BURNING_UNTIL = AttrKey<Long>("tfa_smorks_burning")

    /** Capture: the Smorks' 7-slot cycle: S shot, N napalm, - nothing, B big bomb. */
    private const val CYCLE = "SSN-SSB"
    private val END_BY_RATE = intArrayOf(90, 90, 120, 150)
    private val DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    /** bork.md data: Smorks HP for 2..8 players. */
    private val HP = intArrayOf(600, 870, 1120, 1370, 1630, 1880, 2130)

    private const val HUNT = 20
    private const val ENRAGE_DELAY = 11
    private const val ENRAGED_RATE = 2
    private const val END_FAST = 90
    private const val CYCLE_PAD = 15
    private const val CYCLES_PER_TICK = 30
    private const val CHIP_ONE_IN = 4
    private const val SHOT_MIN = 11
    private const val SHOT_MAX = 30
    private const val ENRAGED_MIN = 10
    private const val ENRAGED_MAX = 28
    private const val NAPALM_FLIGHT = 4
    private const val NAPALM_MIN = 25
    private const val NAPALM_MAX = 28
    private const val RAGGED_MIN = 3
    private const val RAGGED_MAX = 17
    private const val RAGGED_TICKS = 199
    private const val RAGGED_DAMAGE = 2
    private const val BURN_TICKS = 5
    private const val BURN_EVERY = 4
    private const val BURN_MAX = 3
    private const val FLOOR_SHAPE = 22
    private const val BOMB_FLIGHT = 5
}
