package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Xarth, TFA room 4 east (docs/mechanics/raids/tfa/xarth.md XA1-XA2). Phase 1: magic at the closest player; every
 * fifth attack binds everyone and charges an explosion that only a pillar (line of sight) softens; his shield goes
 * after the first explosion. Phase 2: static ranged and melee, spreading flames, and corpses that walk to him and heal
 * him; two heals close together trigger an arena-wide blast.
 */
internal object TfaXarth : Boss(Npcs.TFA_XARTH) {
    private class State {
        var magics = 0
        var shielded = true
        var phase2 = false
        var nextCorpse = -1L
        var lastHeal = -1L
        var attacks = 0
    }

    private val STATE = AttrKey<State>("tfa_xarth")

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
        if (s.phase2) {
            phase2(npc, s, victim, distance)
            return
        }
        s.magics++
        if (s.magics % EXPLOSION_EVERY == 0) {
            explosion(npc, s)
            return
        }
        // XA1: phase 1 targets the closest player.
        val target = TfaKit.arena(npc).minByOrNull { TfaKit.gap(npc, it) } ?: return
        CombatSystem.announceAttack(npc, target, CombatStyle.MAGIC)
        BossCombat.strike(npc, target, AttackType.MAGIC, MAGIC_MAX, BossCombat.delay(npc, target, CombatStyle.MAGIC))
    }

    /**
     * XA1: binds everyone, then a charged explosion: a player in his line of sight takes ~45, one hidden behind a pillar
     * ~7 (OPEN-XA3: bind 5 ticks, explosion 4 ticks after the bind).
     */
    private fun explosion(npc: NpcRef, s: State) {
        npc.say("Burn!")
        for (p in TfaKit.arena(npc)) p.frozenUntil = maxOf(p.frozenUntil, npc.tick + BIND_TICKS)
        npc.queueHit(EXPLOSION_DELAY) {
            if (!npc.isInWorld) return@queueHit
            for (p in TfaKit.arena(npc)) {
                val exposed = npc.hasLineOfSight(p)
                CombatSystem.applyHit(p, npc, if (exposed) EXPOSED_DAMAGE else COVERED_DAMAGE, HitStyle.MAGIC)
            }
            s.shielded = false
        }
    }

    private fun phase2(npc: NpcRef, s: State, victim: EntityRef, distance: Boolean) {
        corpses(npc, s)
        if (s.attacks % FLAME_EVERY == 0) flames(npc)
        if (!distance) {
            CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
            BossCombat.strike(npc, victim, AttackType.SLASH, MELEE_MAX, 0)
        } else {
            CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
            // XA2: ranged damage is decided on impact.
            val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
            npc.queueHit(delay) {
                if (npc.isInWorld) BossCombat.strike(npc, victim, AttackType.RANGED, RANGED_MAX, 0)
            }
        }
    }

    /** XA2: flames land on a player and spread to adjacent tiles (OPEN-XA4: 3 spreads, 4 ticks apart, 8 per tick). */
    private fun flames(npc: NpcRef) {
        val players = TfaKit.arena(npc)
        val centre = players.getOrNull(npc.random(maxOf(1, players.size)))?.coord ?: return
        for (spread in 0..FLAME_SPREADS) {
            for (t in 0 until FLAME_SPREAD_TICKS) {
                npc.queueHit(1 + spread * FLAME_SPREAD_TICKS + t) {
                    if (!npc.isInWorld) return@queueHit
                    for (v in BossCombat.playersOn(
                        npc,
                        centre,
                        radius = spread,
                    )) {
                        CombatSystem.applyHit(v, npc, FLAME_DAMAGE, HitStyle.TYPELESS)
                    }
                }
            }
        }
    }

    /** XA2: corpses spawn at the room's edge and walk to him (OPEN-XA5: one every 15 ticks, 12 tiles out). */
    private fun corpses(npc: NpcRef, s: State) {
        if (npc.tick < s.nextCorpse) return
        s.nextCorpse = npc.tick + CORPSE_EVERY
        val (dx, dz) = EDGES[npc.random(EDGES.size)]
        val at = CoordGrid(npc.coord.x + dx, npc.coord.z + dz, npc.coord.level)
        val corpse = Bosses.summon(npc, Npcs.TFA_XARTH_CORPSE, at, huntRange = 0)
        corpse.walkTo(npc.coord)
    }

    /** A corpse that reached him heals him for its HP; two within [HEAL_WINDOW] ticks blast the arena (OPEN-XA2). */
    fun corpseArrived(xarth: NpcRef, corpse: NpcRef) {
        val s = state(xarth)
        xarth.hitpoints = minOf(xarth.maxHitpoints, xarth.hitpoints + corpse.hitpoints)
        corpse.despawn()
        if (s.lastHeal >= 0 && xarth.tick - s.lastHeal <= HEAL_WINDOW) {
            for (p in TfaKit.arena(xarth)) CombatSystem.applyHit(p, xarth, BLAST_DAMAGE, HitStyle.TYPELESS)
        }
        s.lastHeal = xarth.tick
    }

    /** XA1: his shield stands until the first explosion (OPEN-XA6: it halves damage). Phase 2 at 50% (OPEN-XA7). */
    override fun incoming(
        npc: NpcRef,
        source: EntityRef?,
        damage: Int,
        style: HitStyle,
    ): Int = if (state(npc).shielded) damage / 2 else damage

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (!s.phase2 && npc.hitpoints * 2 <= npc.maxHitpoints) {
            s.phase2 = true
            s.nextCorpse = npc.tick
        }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    /** xarth.md data: HP for 2..8 players. */
    private val HP = intArrayOf(2600, 3770, 4860, 5950, 7050, 8140, 9230)
    private val EDGES = listOf(12 to 0, -12 to 0, 0 to 12, 0 to -12)

    private const val HUNT = 20

    /** OPEN-XA1: max hits (magic 35, ranged 30, melee 35). */
    private const val MAGIC_MAX = 35
    private const val RANGED_MAX = 30
    private const val MELEE_MAX = 35
    private const val EXPLOSION_EVERY = 6
    private const val BIND_TICKS = 5
    private const val EXPLOSION_DELAY = 4
    private const val EXPOSED_DAMAGE = 45
    private const val COVERED_DAMAGE = 7
    private const val FLAME_EVERY = 3
    private const val FLAME_SPREADS = 3
    private const val FLAME_SPREAD_TICKS = 4
    private const val FLAME_DAMAGE = 8
    private const val CORPSE_EVERY = 15
    private const val HEAL_WINDOW = 10
    private const val BLAST_DAMAGE = 30
}

/** XA2: a corpse walks to Xarth and heals him when it gets there; it never attacks. */
internal object TfaXarthCorpse : Boss(Npcs.TFA_XARTH_CORPSE) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val xarth = npc[Bosses.OWNER] ?: return
        if (TfaKit.gap(xarth, npc) <= 1 || TfaKit.under(xarth, npc.coord)) TfaXarth.corpseArrived(xarth, npc) else npc.walkTo(xarth.coord)
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }
}
