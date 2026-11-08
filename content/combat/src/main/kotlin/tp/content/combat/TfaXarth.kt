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
 * Xarth, TFA room 4 east (docs/mechanics/raids/tfa/xarth.md XA1-XA2; cycle, counts and damage from the live-game
 * captures (capture-values.md), the 4-tick attack clock from RuneLite (D-080)).
 *
 * Phase 1 is three fixed 31-tick cycles (the first 6 ticks after engaging): fireballs at C, +4, +8, +12, +16 (they land
 * +3 and stack burn), at C+20 a room-wide AoE that binds 3 ticks, at C+23 the flame blast that only a pillar softens,
 * at C+26 the pillar the raiders hid behind falls. The third fall (D) starts phase 2: 8 arena fires, arrows at D+7 and
 * D+11, then from D+15 every 21 ticks a corpse volley (corpses walk to him and heal him for their HP), 3 more fires and
 * four arrows (melee when beside him).
 */
internal object TfaXarth : Boss(Npcs.TFA_XARTH) {
    private class State {
        var started = false
        var falls = 0
        var shielded = true
        var phase2 = false
        var cycleAt = 0L
        var lastHeal = -1L
        val pillars = ArrayList<NpcRef>(PILLARS.size)
        val fires = HashSet<CoordGrid>()
    }

    private val STATE = AttrKey<State>("tfa_xarth")
    private val BURN_LEVEL = AttrKey<Int>("tfa_xarth_burn")
    private val BURN_GEN = AttrKey<Int>("tfa_xarth_burn_gen")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State().also {
            npc[STATE] = it
            // Capture: three 2x2 rubble pillars at (6,2), (3,6), (3,-4) from his south-west tile.
            for ((dx, dz) in PILLARS) {
                it.pillars +=
                    Bosses.summon(
                        npc,
                        Npcs.TFA_XARTH_RUBBLE_NPC,
                        CoordGrid(npc.spawn.x + dx, npc.spawn.z + dz, npc.spawn.level),
                        huntRange = 0,
                    )
            }
        }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    /** The first swing engages him; from then on his fixed script drives every attack. */
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        engage(npc, state(npc))
        npc.setAttackClock(npc.tick + IDLE_CHECK)
    }

    /** Capture: the first cycle is 6 ticks after he is engaged (his first swing or the first hit on him). */
    private fun engage(npc: NpcRef, s: State) {
        if (s.started) return
        s.started = true
        s.cycleAt = npc.tick + FIRST_CYCLE
        script(npc, s)
    }

    private fun script(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            val at = (npc.tick - s.cycleAt).toInt()
            if (s.phase2) {
                phase2(npc, s, at)
                fireTick(npc, s)
            } else {
                phase1(npc, s, at)
            }
            script(npc, s)
        }
    }

    // ---- phase 1

    private fun phase1(npc: NpcRef, s: State, at: Int) {
        when {
            at < 0 -> {
                Unit
            }

            at < AOE_AT -> {
                if (at % ATTACK_SPEED == 0) fireball(npc)
            }

            at == AOE_AT -> {
                aoe(npc)
            }

            at == BLAST_AT -> {
                blast(npc, s)
            }

            at == FALL_AT -> {
                pillarFalls(npc, s)
                if (!s.phase2) s.cycleAt += CYCLE
            }
        }
    }

    /** Capture: a fireball (projectile 4034) at the closest raider, landing +3: 1..8 through Protect from Magic, 25 unprayed. */
    private fun fireball(npc: NpcRef) {
        val target = TfaKit.arena(npc).minByOrNull { TfaKit.gap(npc, it) } ?: return
        npc.anim(Seqs.XARTH_ATTACK_MAGIC_01)
        CombatSystem.announceAttack(npc, target, CombatStyle.MAGIC)
        BossCombat.projectile(npc, target, SpotAnims.LUC2_UNDEAD_DEMON_FIREBALL_PROJ_BIG, FIREBALL_FLIGHT)
        npc.queueHit(FIREBALL_FLIGHT, strong = false) {
            if (!npc.isInWorld || target.isDying) return@queueHit
            val dmg = if (target.protects(CombatStyle.MAGIC)) BossCombat.roll(npc, 1, FIREBALL_PRAYED_MAX) else FIREBALL_UNPRAYED
            CombatSystem.applyHit(target, npc, dmg, HitStyle.MAGIC)
            burn(npc, target)
        }
    }

    /** Capture: burn (hitsplat 74) rises by 1 per fireball that hits, never past 5; six beats 4 ticks apart, then back to 1. */
    private fun burn(npc: NpcRef, p: PlayerRef) {
        val level = minOf(BURN_CAP, (p[BURN_LEVEL] ?: 0) + 1)
        val gen = (p[BURN_GEN] ?: 0) + 1
        p[BURN_LEVEL] = level
        p[BURN_GEN] = gen
        for (k in 1..BURN_BEATS) {
            p.queueHit(k * BURN_EVERY, strong = false) {
                if (p[BURN_GEN] != gen || p.isDying) return@queueHit
                CombatSystem.applyHit(p, npc, p[BURN_LEVEL] ?: 1, HitStyle.TYPELESS)
                if (k == BURN_BEATS) p[BURN_LEVEL] = 0
            }
        }
    }

    /** Capture: C+20, the root (seq 14991): 19 to every raider and a 3-tick bind. */
    private fun aoe(npc: NpcRef) {
        npc.say("Burn!")
        npc.anim(Seqs.XARTH_ATTACK_ROOT_01)
        for (p in TfaKit.arena(npc)) {
            p.frozenUntil = maxOf(p.frozenUntil, npc.tick + BIND_TICKS)
            CombatSystem.applyHit(p, npc, AOE_DAMAGE, HitStyle.MAGIC)
        }
    }

    /**
     * XA1 / capture: C+23, the flame blast (seq 14992): 45 to a raider he can see, 7 to one behind a pillar or another
     * raider; it takes his shield down after the first (OPEN-XA6).
     */
    private fun blast(npc: NpcRef, s: State) {
        npc.anim(Seqs.XARTH_ATTACK_AOE_01)
        val players = TfaKit.arena(npc)
        val standing = s.pillars.filter { it.type == Npcs.TFA_XARTH_RUBBLE_NPC }
        for (p in players) {
            val covered =
                !npc.hasLineOfSight(p) || blocker(npc, p.coord, standing) != null ||
                    lineTiles(npc, p.coord).any { t -> players.any { it.coord == t } }
            CombatSystem.applyHit(p, npc, if (covered) COVERED_DAMAGE else EXPOSED_DAMAGE, HitStyle.MAGIC)
        }
        s.shielded = false
    }

    /**
     * Capture: C+26, the pillar the raiders hid behind falls (a 100 splat on it); with nobody behind one, the standing
     * pillar closest to the raiders (OPEN-XA9). The third fall is phase 2.
     */
    private fun pillarFalls(npc: NpcRef, s: State) {
        s.falls++
        val standing = s.pillars.filter { it.type == Npcs.TFA_XARTH_RUBBLE_NPC && it.isInWorld }
        if (standing.isNotEmpty()) {
            val players = TfaKit.arena(npc)
            val hid = players.mapNotNull { blocker(npc, it.coord, standing) }
            val falls =
                hid
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key
                    ?: standing.minBy { pl -> players.minOfOrNull { TfaKit.gap(pl, it) } ?: 0 }
            falls.hitsplat(npc, PILLAR_SPLAT, HITMARK_DAMAGE, HITMARK_DAMAGE, HITMARK_DAMAGE)
            falls.transform(Npcs.TFA_XARTH_RUBBLE_NPC_DESTROYED, restoreStats = false)
        }
        if (s.falls >= PILLARS.size) startPhase2(npc, s)
    }

    /** The standing pillar on the line from his centre to [to], if any. */
    private fun blocker(npc: NpcRef, to: CoordGrid, pillars: List<NpcRef>): NpcRef? =
        lineTiles(npc, to).firstNotNullOfOrNull { t -> pillars.firstOrNull { TfaKit.under(it, t) } }

    /** The tiles strictly between his centre and [to]. */
    private fun lineTiles(npc: NpcRef, to: CoordGrid): List<CoordGrid> {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val steps = maxOf(kotlin.math.abs(to.x - cx), kotlin.math.abs(to.z - cz))
        return (1 until steps).map { i -> CoordGrid(cx + (to.x - cx) * i / steps, cz + (to.z - cz) * i / steps, to.level) }
    }

    // ---- phase 2

    /** Capture: phase 2 starts at the third fall (D): 8 arena fires (loc 62713); arrows D+7, D+11; the first volley D+15. */
    private fun startPhase2(npc: NpcRef, s: State) {
        s.phase2 = true
        s.cycleAt = npc.tick + FIRST_VOLLEY
        addFires(npc, s, FIRES_AT_START)
    }

    /** Capture: arrows at M-8 and M-4 before the first volley M, then the volley every 21 ticks with four arrows 4 apart from M+5. */
    private fun phase2(npc: NpcRef, s: State, at: Int) {
        val shot =
            when {
                at < 0 -> {
                    at == FIRST_ARROW - FIRST_VOLLEY || at == FIRST_ARROW - FIRST_VOLLEY + ATTACK_SPEED
                }

                else -> {
                    val m = at % VOLLEY_EVERY
                    if (m == 0) {
                        volley(npc, s)
                        false
                    } else {
                        m >= ARROWS_AFTER && (m - ARROWS_AFTER) % ATTACK_SPEED == 0 && m < ARROWS_AFTER + ARROWS * ATTACK_SPEED
                    }
                }
            }
        if (!shot) return
        val victim = TfaKit.arena(npc).minByOrNull { TfaKit.gap(npc, it) } ?: return
        if (TfaKit.gap(npc, victim) <= 1) {
            npc.anim(Seqs.XARTH_ATTACK_MELEE_01)
            CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
            val dmg = if (victim.protects(CombatStyle.MELEE)) 0 else BossCombat.roll(npc, MELEE_MIN, MELEE_MAX)
            CombatSystem.queueHit(victim, npc, dmg, HitStyle.MELEE, 0)
        } else {
            arrow(npc, victim)
        }
    }

    /** XA2 / capture: an arrow, damage decided on impact: 11..29, nothing through Protect from Missiles. */
    private fun arrow(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.XARTH_ATTACK_RANGED_01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        npc.queueHit(delay, strong = false) {
            if (!npc.isInWorld) return@queueHit
            val dmg = if (victim is PlayerRef && victim.protects(CombatStyle.RANGED)) 0 else BossCombat.roll(npc, ARROW_MIN, ARROW_MAX)
            CombatSystem.applyHit(victim, npc, dmg, HitStyle.RANGED)
        }
    }

    /**
     * Capture / RuneLite: the corpse volley (seq 15017): fireballs 2 ticks apart at tiles 8 out from his middle, each
     * raising a corpse 3 ticks later; max(min(n, 2), (n + 1) / 2) corpses for n living raiders. 3 more fires at M+12.
     */
    private fun volley(npc: NpcRef, s: State) {
        npc.anim(Seqs.XARTH_ATTACK_SUMMON_01)
        val n = TfaKit.arena(npc).size
        val corpses = maxOf(minOf(n, 2), (n + 1) / 2)
        val mid = CoordGrid(npc.coord.x + npc.size / 2, npc.coord.z + npc.size / 2, npc.coord.level)
        for (k in 0 until corpses) {
            npc.queueHit(k * FIREBALL_GAP, strong = false) {
                if (!npc.isInWorld) return@queueHit
                val (dx, dz) = RING8[npc.random(RING8.size)]
                val at = CoordGrid(mid.x + dx, mid.z + dz, mid.level)
                npc.spotAnimAt(at, SpotAnims.LUC2_UNDEAD_DEMON_FIREBALL_PROJ_BIG)
                npc.queueHit(CORPSE_RISE, strong = false) { if (npc.isInWorld) spawnCorpseAt(npc, at) }
            }
        }
        npc.queueHit(MORE_FIRES_AT, strong = false) { if (npc.isInWorld) addFires(npc, s, FIRES_PER_VOLLEY) }
    }

    private fun addFires(npc: NpcRef, s: State, count: Int) {
        repeat(count) {
            val t = BossCombat.randomTile(npc, npc.spawn, FIRE_SPREAD)
            if (TfaKit.under(npc, t) || !s.fires.add(t)) return@repeat
            npc.addLoc(Locs.TFA_XARTH_ARENAFIRE, t, FLOOR_SHAPE, 0)
        }
    }

    /** Capture: the "fire tile 2" splats: 2 a tick to a raider standing in an arena fire (OPEN-XA4). */
    private fun fireTick(npc: NpcRef, s: State) {
        for (p in TfaKit.arena(npc)) if (p.coord in s.fires) CombatSystem.applyHit(p, npc, FIRE_TILE_DAMAGE, HitStyle.TYPELESS)
        stepCorpses(npc)
    }

    /** RuneLite: a corpse (16687) stands still 2 ticks, captures show it walking from +1; one tile a tick to him. */
    private fun spawnCorpseAt(npc: NpcRef, at: CoordGrid) {
        val corpse = Bosses.summon(npc, Npcs.TFA_XARTH_CORPSE, at, huntRange = 0)
        TfaKit.setPool(corpse, CORPSE_HP)
        corpse.queueHit(1, strong = false) { if (corpse.isInWorld && !corpse.isFrozen) corpse.walkTo(npc.coord) }
    }

    private fun stepCorpses(npc: NpcRef) {
        for (c in Bosses.minions(npc, Npcs.TFA_XARTH_CORPSE)) {
            when {
                TfaKit.gap(npc, c) <= 1 -> corpseArrived(npc, c)
                !c.isFrozen -> c.walkTo(npc.coord)
            }
        }
    }

    /** XA2: fire spells on a corpse make another at a room edge (OPEN-XA10). */
    fun spawnCorpse(npc: NpcRef) {
        val (dx, dz) = RING8[npc.random(RING8.size)]
        spawnCorpseAt(npc, CoordGrid(npc.coord.x + npc.size / 2 + dx, npc.coord.z + npc.size / 2 + dz, npc.coord.level))
    }

    /**
     * Capture: a corpse that reaches him heals him its HP (as a heal splat). XA2: two heals close together set off a
     * wave: 43..48 inside its ring (capture, low confidence; OPEN-XA2: the window and ring).
     */
    fun corpseArrived(xarth: NpcRef, corpse: NpcRef) {
        val s = state(xarth)
        xarth.hitpoints = minOf(xarth.maxHitpoints, xarth.hitpoints + corpse.hitpoints)
        corpse.despawn()
        if (s.lastHeal >= 0 && xarth.tick - s.lastHeal <= HEAL_WINDOW) {
            for (p in TfaKit.arena(xarth)) {
                if (TfaKit.gap(xarth, p) <=
                    WAVE_RING
                ) {
                    CombatSystem.applyHit(p, xarth, BossCombat.roll(xarth, WAVE_MIN, WAVE_MAX), HitStyle.TYPELESS)
                }
            }
        }
        s.lastHeal = xarth.tick
    }

    /** XA1: his shield stands until the first flame blast (OPEN-XA6: it halves damage). */
    override fun incoming(
        npc: NpcRef,
        source: EntityRef?,
        damage: Int,
        style: HitStyle,
    ): Int {
        val s = state(npc)
        engage(npc, s)
        return if (s.shielded) damage / 2 else damage
    }

    /** Phase 2 waits for the third pillar; he cannot die before it (capture: phase 2 is not an HP threshold). */
    override fun onLethal(npc: NpcRef): Boolean {
        if (state(npc).phase2) return false
        npc.hitpoints = 1
        return true
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
        for (p in TfaKit.arena(npc)) p[BURN_GEN] = (p[BURN_GEN] ?: 0) + 1
    }

    /** xarth.md data: HP for 2..8 players. */
    private val HP = intArrayOf(2600, 3770, 4860, 5950, 7050, 8140, 9230)

    /** Capture: pillar offsets from his south-west tile. */
    private val PILLARS = listOf(6 to 2, 3 to 6, 3 to -4)

    /** Capture: corpse fireballs land 8 tiles from his middle. */
    private val RING8 = listOf(8 to 0, -8 to 0, 0 to 8, 0 to -8, 6 to 6, -6 to 6, 6 to -6, -6 to -6)

    private const val HUNT = 20
    private const val FIRST_CYCLE = 6
    private const val CYCLE = 31
    private const val AOE_AT = 20
    private const val BLAST_AT = 23
    private const val FALL_AT = 26

    /** RuneLite (LU Xarth.java:27-35): every attack, specials included, is on a 4-tick cycle. */
    private const val ATTACK_SPEED = 4
    private const val FIREBALL_FLIGHT = 3
    private const val FIREBALL_PRAYED_MAX = 8
    private const val FIREBALL_UNPRAYED = 25
    private const val BURN_CAP = 5
    private const val BURN_BEATS = 6
    private const val BURN_EVERY = 4
    private const val BIND_TICKS = 3
    private const val AOE_DAMAGE = 19
    private const val EXPOSED_DAMAGE = 45
    private const val COVERED_DAMAGE = 7
    private const val PILLAR_SPLAT = 100

    /** The standard damage hitmark. */
    private const val HITMARK_DAMAGE = 1
    private const val FIRST_ARROW = 7
    private const val FIRST_VOLLEY = 15
    private const val VOLLEY_EVERY = 21

    /** OPEN-XA3: the four arrows after a volley, M+5 .. M+17. */
    private const val ARROWS_AFTER = 5
    private const val ARROWS = 4
    private const val IDLE_CHECK = 5
    private const val FIREBALL_GAP = 2
    private const val CORPSE_RISE = 3
    private const val CORPSE_HP = 62
    private const val MORE_FIRES_AT = 12
    private const val FIRES_AT_START = 8
    private const val FIRES_PER_VOLLEY = 3
    private const val FIRE_SPREAD = 9
    private const val FIRE_TILE_DAMAGE = 2
    private const val FLOOR_SHAPE = 22
    private const val ARROW_MIN = 11
    private const val ARROW_MAX = 29
    private const val MELEE_MIN = 9
    private const val MELEE_MAX = 11
    private const val HEAL_WINDOW = 10
    private const val WAVE_RING = 8
    private const val WAVE_MIN = 43
    private const val WAVE_MAX = 48
}

/**
 * XA2: a corpse walks to Xarth and heals him; freezes and water spells stop it, Undead Grasp holds it 24 ticks (under
 * Mark of Darkness), fire spells make more. It never attacks.
 */
internal object TfaXarthCorpse : Boss(Npcs.TFA_XARTH_CORPSE) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Corpses are moved by Xarth's own loop.
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val caster = source as? PlayerRef
        val spell = caster?.get(Attr.LAST_SPELL)
        if (style == HitStyle.MAGIC && spell != null) {
            when {
                spell == "Undead Grasp" && SpellEffects.marked(caster) -> {
                    npc.frozenUntil = maxOf(npc.frozenUntil, npc.tick + GRASP_TICKS)
                }

                spell.startsWith("Water") || FREEZES.any { spell.startsWith(it) } -> {
                    npc.frozenUntil =
                        maxOf(npc.frozenUntil, npc.tick + STOP_TICKS)
                }

                spell.startsWith("Fire") -> {
                    npc[Bosses.OWNER]?.let { TfaXarth.spawnCorpse(it) }
                }
            }
        }
        return damage
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    private val FREEZES = listOf("Ice", "Bind", "Snare", "Entangle")
    private const val GRASP_TICKS = 24

    /** OPEN-XA11: how long a freeze or water spell stops a corpse. */
    private const val STOP_TICKS = 10
}
