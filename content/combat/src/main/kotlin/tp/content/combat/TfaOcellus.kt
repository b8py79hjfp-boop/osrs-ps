package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Ocellus, the TFA final boss (docs/mechanics/raids/tfa/ocellus.md OC1-OC4), one NPC through four phases:
 * P1 ranged/magic from the corners with tendril channels and two centre blasts; P2 the Blade (purple portals; half the
 * damage dealt to it comes off P3's pool); P3 two melee and a wave per cycle with a special every two cycles (Prayer,
 * Untether, Prayer, Insolence); P4 below a quarter of P3's pool: deflection, void explosions and soul-split blasts, and
 * less damage from slow weapons.
 */
internal object TfaOcellus : Boss(Npcs.TFA_OCELLUS_P1, Npcs.TFA_OCELLUS_P2, Npcs.TFA_OCELLUS_P3) {
    private enum class Phase { P1, P2, P3, P4 }

    private class State(val centre: CoordGrid, val size: Int) {
        var phase = Phase.P1
        var attacks = 0
        var cycles = 0
        var specials = 0
        var centreBlasts = 0
        var bladeDamage = 0
        var explosionsLeft = 0
    }

    private val STATE = AttrKey<State>("tfa_ocellus")

    private fun state(npc: NpcRef): State =
        npc[STATE] ?: State(npc.spawn, TfaKit.size(npc)).also {
            npc[STATE] = it
            TfaKit.setPool(npc, P1_HP[it.size - MIN_SCALE])
            // ocellus.md: varbit TFA_OCELLUS_MET reveals his name and his stats category.
            for (p in TfaKit.arena(npc)) p.setVarbit(Varbits.TFA_OCELLUS_MET, 1)
        }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        s.attacks++
        when (s.phase) {
            Phase.P1 -> p1(npc, s, victim)
            Phase.P2 -> portals(npc)
            Phase.P3 -> p3(npc, s, victim)
            Phase.P4 -> p4(npc, s)
        }
    }

    // ---- P1 (OC1)

    private fun p1(npc: NpcRef, s: State, victim: EntityRef) {
        if (s.attacks % CORNER_EVERY == 0) {
            corner(npc, s)
            return
        }
        if (victim is PlayerRef && TfaKit.gap(npc, victim) <= SLAM_RANGE && npc.random(2) == 0) {
            for (p in TfaKit.arena(npc)) if (TfaKit.gap(npc, p) <= SLAM_RANGE) BossCombat.strike(npc, p, AttackType.CRUSH, SLAM_MAX, 1)
            return
        }
        val magic = s.attacks % 2 == 0
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        CombatSystem.announceAttack(npc, victim, style)
        val delay = BossCombat.delay(npc, victim, style)
        BossCombat.projectile(npc, victim, SpotAnims.TFA_OCELLUS_SF_PROJ, delay)
        BossCombat.strike(npc, victim, if (magic) AttackType.MAGIC else AttackType.RANGED, MAX_HIT, delay)
    }

    /**
     * OC1: at a corner seven tendrils channel into him; a player on a tendril mitigates it and takes a little damage.
     * OPEN-OC10: each unmitigated tendril heals him 20; a mitigating player takes 5.
     */
    private fun corner(npc: NpcRef, s: State) {
        val (dx, dz) = CORNERS[npc.random(CORNERS.size)]
        npc.teleport(CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level))
        val tendrils = List(TENDRILS) { BossCombat.randomTile(npc, npc.coord, TENDRIL_SPREAD) }
        for (t in tendrils) npc.spotAnimAt(t, SpotAnims.TFA_PORTAL)
        npc.queueHit(CHANNEL_TICKS) {
            if (!npc.isInWorld) return@queueHit
            val players = TfaKit.arena(npc)
            var unmitigated = 0
            for (t in tendrils) {
                val standing = players.filter { it.coord.x == t.x && it.coord.z == t.z }
                if (standing.isEmpty()) {
                    unmitigated++
                } else {
                    standing.forEach {
                        CombatSystem.applyHit(
                            it,
                            npc,
                            TENDRIL_HIT,
                            HitStyle.TYPELESS,
                        )
                    }
                }
            }
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + unmitigated * TENDRIL_HEAL)
        }
    }

    /**
     * OC1: at 66% and 25% he teleports to the centre, then "Embrace... annihilation..." (80+) or "Death... awaits..."
     * (17-18) by how many tendrils were mitigated (OPEN-OC11: annihilation when fewer than half the players stood on
     * the eight centre tendrils).
     */
    private fun centreBlast(npc: NpcRef, s: State) {
        s.centreBlasts++
        npc.teleport(s.centre)
        val tendrils = List(CENTRE_TENDRILS) { BossCombat.randomTile(npc, s.centre, TENDRIL_SPREAD) }
        for (t in tendrils) npc.spotAnimAt(t, SpotAnims.TFA_PORTAL)
        npc.setAttackClock(npc.tick + CENTRE_TICKS + 1)
        npc.queueHit(CENTRE_TICKS) {
            if (!npc.isInWorld) return@queueHit
            val players = TfaKit.arena(npc)
            val mitigated = players.count { p -> tendrils.any { it.x == p.coord.x && it.z == p.coord.z } }
            val annihilate = mitigated * 2 < players.size
            npc.say(if (annihilate) "Embrace... annihilation..." else "Death... awaits...")
            for (p in players) CombatSystem.applyHit(p, npc, if (annihilate) ANNIHILATION else DEATH_AWAITS, HitStyle.TYPELESS)
        }
    }

    // ---- P2 (OC2)

    /** OC2: purple portals damage the players standing in them (OPEN-OC5: 4 per player, 20 damage, 2-tick telegraph). */
    private fun portals(npc: NpcRef) {
        val tiles = TfaKit.arena(npc).flatMap { p -> List(PORTALS_PER_PLAYER) { BossCombat.randomTile(npc, p.coord, PORTAL_SPREAD) } }
        for (t in tiles) npc.spotAnimAt(t, SpotAnims.TFA_PORTAL)
        npc.queueHit(PORTAL_TELEGRAPH) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) if (TfaKit.on(p, tiles)) CombatSystem.applyHit(p, npc, PORTAL_DAMAGE, HitStyle.TYPELESS)
        }
    }

    // ---- P3 (OC3)

    private fun p3(npc: NpcRef, s: State, victim: EntityRef) {
        val step = s.attacks % CYCLE
        if (step != 0) {
            // Two melee, then the directional wave.
            if (step < CYCLE - 1) melee(npc, victim) else wave(npc, victim)
            return
        }
        s.cycles++
        if (s.cycles % 2 != 0) {
            melee(npc, victim)
            return
        }
        when (SPECIALS[s.specials++ % SPECIALS.size]) {
            Special.PRAYER -> prayerSpecial(npc)
            Special.UNTETHER -> untether(npc)
            Special.INSOLENCE -> insolence(npc)
        }
    }

    private fun melee(npc: NpcRef, victim: EntityRef) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        if (victim is PlayerRef && TfaKit.gap(npc, victim) > 1) {
            // OC3: a spread void shot (20+) when the tank is unreachable.
            for (p in TfaKit.arena(npc)) BossCombat.strike(npc, p, AttackType.MAGIC, VOID_SHOT, BossCombat.delay(npc, p, CombatStyle.MAGIC))
            return
        }
        BossCombat.strike(npc, victim, AttackType.SLASH, P3_MELEE_MAX, 0)
    }

    /** The directional wave at the target's line (OPEN-OC12: everyone within 1 tile of the line from him to the target, 25). */
    private fun wave(npc: NpcRef, victim: EntityRef) {
        for (p in TfaKit.arena(npc)) {
            val near = p == victim || p.distanceTo(victim) <= 1
            if (near) BossCombat.strike(npc, p, AttackType.RANGED, WAVE_MAX, BossCombat.delay(npc, p, CombatStyle.RANGED))
        }
    }

    /**
     * OC3 Prayer special: a player still using an overhead after the 3-tick window loses it (OPEN-OC7: and takes 20).
     */
    private fun prayerSpecial(npc: NpcRef) {
        npc.say("Pray...")
        npc.queueHit(PRAYER_WINDOW) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                val praying = p.protects(CombatStyle.MELEE) || p.protects(CombatStyle.RANGED) || p.protects(CombatStyle.MAGIC)
                if (praying) {
                    p.deactivateAllPrayers()
                    CombatSystem.applyHit(p, npc, PRAYER_PUNISH, HitStyle.TYPELESS)
                }
            }
        }
    }

    /** Untether: a void eruption of 40+ unless inside the purple lines (OPEN-OC13: within 2 tiles of him is safe). */
    private fun untether(npc: NpcRef) {
        npc.say("Untether...")
        npc.queueHit(UNTETHER_DELAY) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                if (TfaKit.gap(npc, p) >
                    UNTETHER_SAFE
                ) {
                    CombatSystem.applyHit(p, npc, UNTETHER_DAMAGE, HitStyle.TYPELESS)
                }
            }
        }
    }

    /** Insolence: waves at three players. */
    private fun insolence(npc: NpcRef) {
        npc.say("Insolence!")
        val players = TfaKit.arena(npc).shuffled(java.util.Random(npc.random(Int.MAX_VALUE).toLong())).take(INSOLENCE_TARGETS)
        for (p in players) wave(npc, p)
    }

    // ---- P4 (OC4)

    private fun p4(npc: NpcRef, s: State) {
        if (s.explosionsLeft > 0) {
            s.explosionsLeft--
            for (p in TfaKit.arena(npc)) {
                val t = BossCombat.randomTile(npc, p.coord, 1)
                npc.spotAnimAt(t, SpotAnims.TFA_PORTAL)
                npc.queueHit(1) {
                    if (npc.isInWorld && p.coord.x == t.x &&
                        p.coord.z == t.z
                    ) {
                        CombatSystem.applyHit(p, npc, EXPLOSION, HitStyle.TYPELESS)
                    }
                }
            }
            npc.setAttackClock(npc.tick + EXPLOSION_GAP)
            return
        }
        // Soul-split void blast: 40+ unprayed, 6-7 prayed (OPEN-OC8: the style alternates magic / ranged).
        val style = if (s.attacks % 2 == 0) CombatStyle.MAGIC else CombatStyle.RANGED
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
        if (s.attacks % P4_CYCLE == 0) s.explosionsLeft = EXPLOSION_CYCLES
    }

    // ---- damage and phase changes

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (s.phase == Phase.P2) s.bladeDamage += damage
        if (s.phase != Phase.P4) return damage
        // OC4: Deflect Melee halves melee; slow weapons do less (33% less for 3t, 50% for 4t, 60% for 5t+).
        var d = if (style == HitStyle.MELEE) damage / 2 else damage
        val speed = (source as? PlayerRef)?.attackSpeed() ?: return d
        d =
            when {
                speed >= SLOW_5 -> d * (PERCENT - CUT_5) / PERCENT
                speed == SLOW_4 -> d * (PERCENT - CUT_4) / PERCENT
                speed == SLOW_3 -> d * (PERCENT - CUT_3) / PERCENT
                else -> d
            }
        return d
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        if (s.phase == Phase.P1 && s.centreBlasts < CENTRE_AT.size && pct <= CENTRE_AT[s.centreBlasts]) centreBlast(npc, s)
        if (s.phase == Phase.P3 && pct <= P4_AT) {
            s.phase = Phase.P4
            npc.say("Enough!")
            s.explosionsLeft = EXPLOSION_CYCLES
        }
    }

    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        return when (s.phase) {
            Phase.P1 -> {
                // OC2: the Blade (P2), with defence restored (the phase's own stats).
                s.phase = Phase.P2
                npc.transform(Npcs.TFA_OCELLUS_P2, restoreStats = true)
                npc.teleport(s.centre)
                TfaKit.setPool(npc, P2_HP)
                true
            }

            Phase.P2 -> {
                // OPEN-OC4: P3 starts when the Blade's pool is gone; half the damage it took comes off P3's pool.
                s.phase = Phase.P3
                npc.transform(Npcs.TFA_OCELLUS_P3, restoreStats = true)
                TfaKit.setPool(npc, REMAINDER_HP[s.size - MIN_SCALE])
                npc.hitpoints = maxOf(1, npc.maxHitpoints - s.bladeDamage / 2)
                true
            }

            else -> {
                false
            }
        }
    }

    private enum class Special { PRAYER, UNTETHER, INSOLENCE }

    /** OC3: specials in the order Prayer, Untether, Prayer, Insolence. */
    private val SPECIALS = listOf(Special.PRAYER, Special.UNTETHER, Special.PRAYER, Special.INSOLENCE)

    /** ocellus.md: P1 HP and the "remainder" (P3) HP for 2..8 players; OPEN-OC2: P2 is a fixed 1,000 pool. */
    private val P1_HP = intArrayOf(2500, 3630, 4680, 5730, 6780, 7830, 8880)
    private val REMAINDER_HP = intArrayOf(5250, 7610, 9820, 12020, 14230, 16430, 18640)
    private const val P2_HP = 1000

    /** OC1: centre blasts at 66% and 25%. */
    private val CENTRE_AT = intArrayOf(66, 25)
    private val CORNERS = listOf(-8 to -8, 8 to -8, -8 to 8, 8 to 8)

    private const val MIN_SCALE = 2
    private const val HUNT = 24
    private const val MAX_HIT = 24
    private const val CORNER_EVERY = 8
    private const val SLAM_RANGE = 2
    private const val SLAM_MAX = 35
    private const val TENDRILS = 7
    private const val CENTRE_TENDRILS = 8
    private const val TENDRIL_SPREAD = 4
    private const val CHANNEL_TICKS = 6
    private const val TENDRIL_HIT = 5
    private const val TENDRIL_HEAL = 20
    private const val CENTRE_TICKS = 8
    private const val ANNIHILATION = 80
    private const val DEATH_AWAITS = 17
    private const val PORTALS_PER_PLAYER = 4
    private const val PORTAL_SPREAD = 3
    private const val PORTAL_TELEGRAPH = 2
    private const val PORTAL_DAMAGE = 20
    private const val CYCLE = 3
    private const val VOID_SHOT = 20
    private const val P3_MELEE_MAX = 30
    private const val WAVE_MAX = 25
    private const val PRAYER_WINDOW = 3
    private const val PRAYER_PUNISH = 20
    private const val UNTETHER_DELAY = 3
    private const val UNTETHER_SAFE = 2
    private const val UNTETHER_DAMAGE = 40
    private const val INSOLENCE_TARGETS = 3
    private const val P4_AT = 25
    private const val P4_CYCLE = 6
    private const val EXPLOSION_CYCLES = 6
    private const val EXPLOSION_GAP = 2
    private const val EXPLOSION = 15
    private const val SOUL_MIN = 40
    private const val SOUL_MAX = 45
    private const val SOUL_PRAYED_MIN = 6
    private const val SOUL_PRAYED_MAX = 7
    private const val SOUL_SPLIT = 4
    private const val PERCENT = 100
    private const val SLOW_3 = 3
    private const val SLOW_4 = 4
    private const val SLOW_5 = 5
    private const val CUT_3 = 33
    private const val CUT_4 = 50
    private const val CUT_5 = 60
}
