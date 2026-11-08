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
        return npc[STATE] ?: State().also {
            npc[STATE] = it
            // XA1: the pillars are the rubble NPCs; OPEN-XA9: four, 5 tiles out on the diagonals.
            for ((dx, dz) in RUBBLE) {
                Bosses.summon(npc, Npcs.TFA_XARTH_RUBBLE_NPC, CoordGrid(npc.coord.x + dx, npc.coord.z + dz, npc.coord.level), huntRange = 0)
            }
        }
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
        val delay = BossCombat.delay(npc, target, CombatStyle.MAGIC)
        BossCombat.strike(npc, target, AttackType.MAGIC, MAGIC_MAX, delay) { dealt -> if (dealt > 0) burn(npc, target, delay) }
    }

    /** XA1: his magic hits stack burn (OPEN-XA8: each hit burns 1 every 4 ticks, five times; stacks add up). */
    private fun burn(npc: NpcRef, p: EntityRef, after: Int) {
        for (k in 1..BURN_TICKS) {
            npc.queueHit(after + k * BURN_EVERY) {
                if (npc.isInWorld && p is tp.plugin.PlayerRef && !p.isDying) CombatSystem.applyHit(p, npc, BURN_DAMAGE, HitStyle.TYPELESS)
            }
        }
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
            val players = TfaKit.arena(npc)
            val pillars = Bosses.minions(npc, Npcs.TFA_XARTH_RUBBLE_NPC)
            for (p in players) {
                // XA1: a pillar, or a player acting as one, between him and the player takes the blast.
                val covered = !npc.hasLineOfSight(p) || blocked(npc, p.coord, pillars, players)
                CombatSystem.applyHit(p, npc, if (covered) COVERED_DAMAGE else EXPOSED_DAMAGE, HitStyle.MAGIC)
            }
            s.shielded = false
        }
    }

    /** Whether a rubble pillar or another player stands on the line from his centre to [to] (strictly between). */
    private fun blocked(npc: NpcRef, to: CoordGrid, pillars: List<NpcRef>, players: List<tp.plugin.PlayerRef>): Boolean {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val steps = maxOf(kotlin.math.abs(to.x - cx), kotlin.math.abs(to.z - cz))
        for (i in 1 until steps) {
            val x = cx + (to.x - cx) * i / steps
            val z = cz + (to.z - cz) * i / steps
            val t = CoordGrid(x, z, to.level)
            if (pillars.any { TfaKit.under(it, t) } || players.any { it.coord == t }) return true
        }
        return false
    }

    private fun phase2(npc: NpcRef, s: State, victim: EntityRef, distance: Boolean) {
        corpses(npc, s)
        stepCorpses(npc)
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

    /** Corpses keep walking to him (unless frozen) and heal him on arrival. */
    private fun stepCorpses(npc: NpcRef) {
        for (c in Bosses.minions(npc, Npcs.TFA_XARTH_CORPSE)) {
            when {
                TfaKit.gap(npc, c) <= 1 -> corpseArrived(npc, c)
                !c.isFrozen -> c.walkTo(npc.coord)
            }
        }
    }

    /** XA2: fire spells on a corpse make more (OPEN-XA10: one more at a room edge). */
    fun spawnCorpse(npc: NpcRef) {
        val (dx, dz) = EDGES[npc.random(EDGES.size)]
        Bosses
            .summon(
                npc,
                Npcs.TFA_XARTH_CORPSE,
                CoordGrid(npc.coord.x + dx, npc.coord.z + dz, npc.coord.level),
                huntRange = 0,
            ).walkTo(npc.coord)
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
            // XA2: the pillars are gone in phase 2.
            for (r in Bosses.minions(npc, Npcs.TFA_XARTH_RUBBLE_NPC)) r.transform(Npcs.TFA_XARTH_RUBBLE_NPC_DESTROYED, restoreStats = false)
        }
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    /** xarth.md data: HP for 2..8 players. */
    private val HP = intArrayOf(2600, 3770, 4860, 5950, 7050, 8140, 9230)
    private val EDGES = listOf(12 to 0, -12 to 0, 0 to 12, 0 to -12)
    private val RUBBLE = listOf(-5 to -5, 6 to -5, -5 to 6, 6 to 6)
    private const val BURN_TICKS = 5
    private const val BURN_EVERY = 4
    private const val BURN_DAMAGE = 1

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

/**
 * XA2: a corpse walks to Xarth and heals him; freezes and water spells stop it, Undead Grasp holds it 24 ticks (under
 * Mark of Darkness), fire spells make more. It never attacks.
 */
internal object TfaXarthCorpse : Boss(Npcs.TFA_XARTH_CORPSE) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Corpses are moved by Xarth's own loop.
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val spell = (source as? tp.plugin.PlayerRef)?.get(Attr.LAST_SPELL)
        if (style == HitStyle.MAGIC && spell != null) {
            when {
                spell == "Undead Grasp" && source is tp.plugin.PlayerRef && SpellEffects.marked(source) -> {
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
