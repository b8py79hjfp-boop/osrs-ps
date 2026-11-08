package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Ocellus P4, the last quadrant (OC4), on a 60-tick cycle from its start D (capture-values.md, 11/11 cycles):
 * - Deflect Melee D+0..30, Deflect Missiles/Magic D+31..59: a deflected hit is blocked and a tenth of it comes back the
 *   next tick.
 * - Wind at D+4 and D+35: every raider loses half their health; each gets a soul-fragment orb: taken, it gives the
 *   wind's hit back (on its ready tick, W+8, at the earliest); not taken by W+25 (W+19 for the second), he heals that
 *   raider's wind hit. Six explosions after each wind, 2 ticks apart, on the raider's start-of-tick tile, landing the
 *   next tick (14..16); a dodged one cuts the raider's next attack by 3 ticks (OC4).
 * - Hybrid at D+24 and D+52: one kind for the whole party (16 magic : 13 ranged : 4 melee), 5..8 under the matching
 *   prayer, +1 a cycle from the third (at most +3); unprayed 35. Soul split at D+25 and D+53 (OC4: 40..45 unprayed,
 *   6..7 prayed, healing him a quarter).
 * - Void floor 9..11 a tick: a ring D+5..18, a quadrant D+37..48 (OPEN-OC8: their shapes).
 * - Rifts at D+59: 2, 3, 4, then 5 every cycle.
 * - Slower weapons do less (OC4: 33% less for 3 ticks, 50% for 4, 60% for 5+).
 */
internal object TfaOcellusP4 {
    private val ORB_AT = AttrKey<CoordGrid>("tfa_ocellus_orb")
    private val ORB_OWED = AttrKey<Int>("tfa_ocellus_orb_owed")

    fun start(npc: NpcRef, s: OcellusState) {
        s.phase = TfaOcellus.P4
        s.generation++
        s.p4Start = npc.tick
        npc.say("Enough!")
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_DEFLECT_IN)
        TfaOcellus.loop(npc, s) { tick(npc, s) }
    }

    private fun tick(npc: NpcRef, s: OcellusState) {
        TfaOcellusP3.patchDamage(npc, s)
        val c = ((npc.tick - s.p4Start) % CYCLE).toInt()
        if (c == 0 && npc.tick != s.p4Start) s.cycles++
        s.deflect = if (c <= MELEE_DEFLECT_END) setOf(HitStyle.MELEE) else setOf(HitStyle.RANGED, HitStyle.MAGIC)
        if (c == 0 || c == MELEE_DEFLECT_END + 1) npc.anim(Seqs.NPC_OCELLUS_PHASE3_DEFLECT_IN)
        when (c) {
            WIND_1 -> wind(npc, s, ORB_DEADLINE_1)
            WIND_2 -> wind(npc, s, ORB_DEADLINE_2)
            HYBRID_1, HYBRID_2 -> hybrid(npc, s)
            SOUL_1, SOUL_2 -> soulSplit(npc)
            RIFTS_AT -> TfaOcellusP3.spawnRifts(npc, RIFT_KINDS[s.cycles % RIFT_KINDS.size], minOf(MAX_RIFTS, FIRST_RIFTS + s.cycles))
        }
        if (c in RING_FROM..RING_TO) voidFloor(npc) { p -> TfaKit.gap(npc, p) in RING_IN..RING_OUT }
        if (c in QUAD_FROM..QUAD_TO) {
            val q = s.cycles % QUADRANT_SIGNS.size
            val (sx, sz) = QUADRANT_SIGNS[q]
            val mx = npc.coord.x + npc.size / 2
            val mz = npc.coord.z + npc.size / 2
            voidFloor(npc) { p -> Integer.signum(p.coord.x - mx) == sx && Integer.signum(p.coord.z - mz) == sz }
        }
    }

    private fun voidFloor(npc: NpcRef, onIt: (PlayerRef) -> Boolean) {
        for (p in TfaKit.arena(
            npc,
        )) {
            if (onIt(p)) CombatSystem.applyHit(p, npc, BossCombat.roll(npc, FLOOR_MIN, FLOOR_MAX), HitStyle.TYPELESS)
        }
    }

    /** Capture: the wind halves every raider's health; the orb gives it back, or he takes it. */
    private fun wind(npc: NpcRef, s: OcellusState, deadline: Int) {
        npc.spotAnim(SpotAnims.VFX_OCELLUS_DEFLECT_WIND_SPOTANIM_01)
        val gen = s.generation
        for (p in TfaKit.arena(npc)) {
            val owed = p.hitpoints / 2
            if (owed > 0) CombatSystem.applyHit(p, npc, owed, HitStyle.TYPELESS)
            val orb = BossCombat.randomTile(npc, p.coord, ORB_SPREAD)
            p[ORB_AT] = orb
            p[ORB_OWED] = owed
            npc.spotAnimAt(orb, SpotAnims.VFX_OCELLUS_SOUL_FRAGMENT_SPAWN_6_TICKS)
            watchOrb(npc, s, gen, p, ORB_READY, deadline, owed)
        }
        explosions(npc)
    }

    private fun watchOrb(npc: NpcRef, s: OcellusState, gen: Int, p: PlayerRef, ready: Int, deadline: Int, owed: Int) {
        for (k in 1..deadline) {
            npc.queueHit(k, strong = false) {
                if (s.generation != gen || p[ORB_OWED] != owed || owed == 0) return@queueHit
                val orb = p[ORB_AT] ?: return@queueHit
                when {
                    k >= ready && p.coord == orb -> {
                        p[ORB_OWED] = 0
                        p.spotAnim(SpotAnims.VFX_OCELLUS_SOUL_FRAGMENT_PLAYER_REGAIN)
                        p.hitpoints = minOf(p.maxHitpoints, p.hitpoints + owed)
                    }

                    k == deadline -> {
                        p[ORB_OWED] = 0
                        npc.spotAnimAt(orb, SpotAnims.VFX_OCELLUS_SOUL_FRAGMENT_DESPAWN)
                        if (npc.isInWorld) npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + owed)
                    }
                }
            }
        }
    }

    /** Capture / RuneLite: six explosions 2 ticks apart on each raider's start-of-tick tile, landing the next tick. */
    private fun explosions(npc: NpcRef) {
        for (k in 0 until EXPLOSIONS) {
            npc.queueHit(CHAIN_LEAD + k * CHAIN_GAP, strong = false) {
                if (!npc.isInWorld) return@queueHit
                for (p in TfaKit.arena(npc)) {
                    val t = p.coord
                    npc.spotAnimAt(t, SpotAnims.VFX_VOID_CHAIN_EXPLOSION)
                    npc.queueHit(1, strong = false) {
                        if (p.coord == t) {
                            CombatSystem.applyHit(p, npc, BossCombat.roll(npc, EXPLOSION_MIN, EXPLOSION_MAX), HitStyle.TYPELESS)
                        } else {
                            p.setAttackClock(maxOf(p.tick, p.attackClock() - DODGE_CUT))
                        }
                    }
                }
            }
        }
    }

    /** Capture: one kind for the party (16 magic : 13 ranged : 4 melee); 5..8 prayed + 1 a cycle from the third, 35 unprayed. */
    private fun hybrid(npc: NpcRef, s: OcellusState) {
        val r = npc.random(HYBRID_WEIGHT)
        val style =
            when {
                r < MAGIC_WEIGHT -> CombatStyle.MAGIC
                r < MAGIC_WEIGHT + RANGED_WEIGHT -> CombatStyle.RANGED
                else -> CombatStyle.MELEE
            }
        npc.anim(Seqs.NPC_OCELLUS_PHASE3_MELEE_HYBRID)
        npc.spotAnim(
            when (style) {
                CombatStyle.MAGIC -> SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID_MAGIC
                CombatStyle.RANGED -> SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID_RANGED
                else -> SpotAnims.VFX_OCELLUS_PHASE3_MELEE_HYBRID
            },
        )
        val bonus = minOf(MAX_BONUS, maxOf(0, s.cycles - 1))
        val prayed = BossCombat.roll(npc, PRAYED_MIN, PRAYED_MAX) + bonus
        for (p in TfaKit.arena(npc)) {
            val dmg = if (p.protects(style)) prayed else UNPRAYED
            CombatSystem.queueHit(p, npc, dmg, CombatSystem.hitStyle(style), 1)
        }
    }

    /** OC4: the soul-split blast, 40..45 unprayed, 6..7 prayed; it heals him a quarter of what it deals. */
    private fun soulSplit(npc: NpcRef) {
        val style = if (npc.random(2) == 0) CombatStyle.MAGIC else CombatStyle.RANGED
        for (p in TfaKit.arena(npc)) {
            val dmg =
                if (p.protects(
                        style,
                    )
                ) {
                    BossCombat.roll(npc, SOUL_PRAYED_MIN, SOUL_PRAYED_MAX)
                } else {
                    BossCombat.roll(npc, SOUL_MIN, SOUL_MAX)
                }
            CombatSystem.queueHit(p, npc, dmg, CombatSystem.hitStyle(style), BossCombat.delay(npc, p, style)) {
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + dmg / SOUL_SPLIT)
            }
        }
    }

    /** A deflected hit is blocked, a tenth returning next tick (capture); slower weapons do less (OC4). */
    fun incoming(npc: NpcRef, s: OcellusState, p: PlayerRef?, damage: Int, style: HitStyle): Int {
        if (style in s.deflect) {
            val back = damage / DEFLECT_BACK
            if (p != null &&
                back > 0
            ) {
                npc.queueHit(1, strong = false) { if (!p.isDying) CombatSystem.applyHit(p, npc, back, HitStyle.TYPELESS) }
            }
            return 0
        }
        val speed = p?.attackSpeed() ?: return damage
        val cut =
            when {
                speed >= SLOW_5 -> CUT_5
                speed == SLOW_4 -> CUT_4
                speed == SLOW_3 -> CUT_3
                else -> 0
            }
        return damage * (TfaOcellus.PERCENT - cut) / TfaOcellus.PERCENT
    }

    /** OPEN-OC8: the void quadrant, a different one each cycle. */
    private val QUADRANT_SIGNS = listOf(1 to 1, 1 to -1, -1 to -1, -1 to 1)
    private val RIFT_KINDS =
        intArrayOf(Npcs.TFA_OCELLUS_P3_BOMBPORTAL_HEAL, Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BASIC, Npcs.TFA_OCELLUS_P3_BOMBPORTAL_BUFF)

    private const val CYCLE = 60
    private const val MELEE_DEFLECT_END = 30
    private const val WIND_1 = 4
    private const val WIND_2 = 35
    private const val ORB_READY = 8
    private const val ORB_DEADLINE_1 = 25
    private const val ORB_DEADLINE_2 = 19

    /** OPEN-OC8: how far from its raider an orb appears. */
    private const val ORB_SPREAD = 4
    private const val HYBRID_1 = 24
    private const val HYBRID_2 = 52
    private const val SOUL_1 = 25
    private const val SOUL_2 = 53
    private const val RIFTS_AT = 59
    private const val FIRST_RIFTS = 2
    private const val MAX_RIFTS = 5
    private const val RING_FROM = 5
    private const val RING_TO = 18
    private const val QUAD_FROM = 37
    private const val QUAD_TO = 48

    /** OPEN-OC8: the ring's tiles, 2..4 out from him. */
    private const val RING_IN = 2
    private const val RING_OUT = 4
    private const val FLOOR_MIN = 9
    private const val FLOOR_MAX = 11
    private const val EXPLOSIONS = 6

    /** OPEN-OC8: the first explosion 2 ticks after the wind. */
    private const val CHAIN_LEAD = 2
    private const val CHAIN_GAP = 2
    private const val EXPLOSION_MIN = 14
    private const val EXPLOSION_MAX = 16
    private const val DODGE_CUT = 3
    private const val HYBRID_WEIGHT = 33
    private const val MAGIC_WEIGHT = 16
    private const val RANGED_WEIGHT = 13
    private const val PRAYED_MIN = 5
    private const val PRAYED_MAX = 8
    private const val MAX_BONUS = 3
    private const val UNPRAYED = 35
    private const val SOUL_MIN = 40
    private const val SOUL_MAX = 45
    private const val SOUL_PRAYED_MIN = 6
    private const val SOUL_PRAYED_MAX = 7
    private const val SOUL_SPLIT = 4
    private const val DEFLECT_BACK = 10
    private const val SLOW_3 = 3
    private const val SLOW_4 = 4
    private const val SLOW_5 = 5
    private const val CUT_3 = 33
    private const val CUT_4 = 50
    private const val CUT_5 = 60
}
