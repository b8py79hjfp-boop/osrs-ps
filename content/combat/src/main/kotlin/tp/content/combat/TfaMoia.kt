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
 * Moia, TFA room 3 west (docs/mechanics/raids/tfa/moia.md MO0-MO3; timings from RuneLite, luuj/fracturedarchive
 * Necromancer.java, in runelite-timings.md). 3-4 fast ranged attacks, then a special in the order magic, magic, cough:
 * a magic special is a teleport and a slow magic dagger landing 6 ticks after the throw; the cough lands 3 ticks after
 * its throw and every coughing player coughs again every 8 ticks, at most four times, spreading it to whoever is next
 * to them or to a binder they touch. Eight undead binders walk nine spots and shuffle one spot on every 15 ticks. In
 * the enrage ("Cease this futile...") she vanishes, then charges ("Useless corpses... Give me your strength!", then
 * "Again!") for 41, 39, 37 ... ticks; each charge ends with every surviving binder hitting everyone.
 */
internal object TfaMoia : Boss(Npcs.TFA_MOIA) {
    private class State(val start: CoordGrid) {
        var ranged = 0
        var rangedBurst = 0
        var specials = 0
        var enraged = false
        var charge = 0
        var chargeEndsAt = -1L
        var shuffleAt = -1L
        var ring = 0
    }

    private val STATE = AttrKey<State>("tfa_moia")

    /** Per player: coughs left. */
    private val COUGHS = AttrKey<Int>("tfa_moia_coughs")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State(npc.spawn).also {
            npc[STATE] = it
            it.rangedBurst = BURST_MIN + npc.random(BURST_VARIANTS)
            it.shuffleAt = npc.tick + SHUFFLE_TICKS
            // MO0: she starts outside the arena (her start tile; RuneLite: the binders' grid centre = start + (8, 1)).
            binders(npc, it)
        }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        tickBinders(npc, s)
        if (s.enraged) {
            enragedAttack(npc, s, victim)
            return
        }
        if (s.ranged < s.rangedBurst) {
            s.ranged++
            ranged(npc, victim)
            return
        }
        s.ranged = 0
        s.rangedBurst = BURST_MIN + npc.random(BURST_VARIANTS)
        // RuneLite (LU Necromancer.java:131-149): before the enrage the specials repeat magic, magic, cough.
        if (s.specials++ % SPECIAL_CYCLE == SPECIAL_CYCLE - 1) {
            cough(npc, victim)
        } else {
            teleport(npc, s)
            magic(npc, victim)
        }
    }

    /** The fast ranged dagger (projectile 4214 `MOIA_DAGGER`). */
    private fun ranged(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.MOIA_PROJECTILE_ATTACK01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.MOIA_DAGGER, delay)
        BossCombat.strike(npc, victim, AttackType.RANGED, RANGED_MAX, delay)
    }

    /** RuneLite (LU Necromancer.java:66): the slow magic dagger (projectile 4213) lands 6 ticks after the throw. */
    private fun magic(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.MOIA_PROJECTILE_ATTACK01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        BossCombat.projectile(npc, victim, SpotAnims.MOIA_DAGGER_MAGIC, MAGIC_FLIGHT)
        BossCombat.strike(npc, victim, AttackType.MAGIC, MAGIC_MAX, MAGIC_FLIGHT) { victim.spotAnim(SpotAnims.MOIA_DAGGER_MAGIC_IMPACT) }
    }

    /** MO2 / RuneLite (seq 14788 = the magic special): she teleports to another side of the binders' grid. */
    private fun teleport(npc: NpcRef, s: State) {
        npc.anim(Seqs.MOIA_TELEPORT01)
        npc.spotAnim(SpotAnims.MOIA_TELEPORT_SPOTANIM)
        val centre = gridCentre(s)
        val (dx, dz) = SIDE_OFFSETS[npc.random(SIDE_OFFSETS.size)]
        npc.teleport(CoordGrid(centre.x + dx, centre.z + dz, centre.level))
        npc.spotAnim(SpotAnims.MOIA_TELEPORT_IN_SPOTANIM)
    }

    // ---- the cough (MO2; RuneLite LU Necromancer.java:69-79, 348-406)

    /** The dagger throw (seq 14786, projectile 4218) coughs its target 3 ticks later. */
    private fun cough(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.MOIA_DAGGER_ATTACK01)
        val p = victim as? PlayerRef ?: return
        BossCombat.projectile(npc, p, SpotAnims.MOIA_INFECT_ATTACK_PROJANIM, FIRST_COUGH)
        npc.queueHit(FIRST_COUGH) { if (npc.isInWorld) infect(npc, p) }
    }

    /** [p] starts coughing unless already ("being coughed on again does not restart it"). */
    private fun infect(npc: NpcRef, p: PlayerRef) {
        if ((p[COUGHS] ?: 0) > 0) return
        p[COUGHS] = MAX_COUGHS
        coughOnce(npc, p, 0)
    }

    /** One cough: the wiki's ramp (2, 5, 13, 20) and a prayer drain (OPEN-MO8: 10); it spreads by contact. */
    private fun coughOnce(npc: NpcRef, p: PlayerRef, k: Int) {
        if (!npc.isInWorld || p.isDying) {
            p[COUGHS] = 0
            return
        }
        p.spotAnim(SpotAnims.MOIA_INFECT_SPOTANIM)
        if (!Toxic.wearsSerpentineHelm(p)) CombatSystem.applyHit(p, npc, COUGH_DAMAGE[minOf(k, COUGH_DAMAGE.size - 1)], HitStyle.TYPELESS)
        BossCombat.drainPrayer(p, COUGH_DRAIN)
        val binders = Bosses.minions(npc, Npcs.TFA_MOIA_MINION)
        for (other in TfaKit.arena(npc)) {
            if (other == p) continue
            val touching = other.distanceTo(p) <= 1 || binders.any { TfaKit.gap(it, other) <= 1 && TfaKit.gap(it, p) <= 1 }
            if (touching) infect(npc, other)
        }
        val left = (p[COUGHS] ?: 0) - 1
        p[COUGHS] = left
        if (left > 0) npc.queueHit(COUGH_EVERY) { coughOnce(npc, p, k + 1) }
    }

    // ---- undead binders (MO1; RuneLite LU Necromancer.java:152-181)

    private fun gridCentre(s: State) = CoordGrid(s.start.x + GRID_CENTRE.first, s.start.z + GRID_CENTRE.second, s.start.level)

    private fun spot(s: State, k: Int): CoordGrid {
        val (dx, dz) = SPOTS[(k + s.ring) % SPOTS.size]
        return CoordGrid(s.start.x + dx, s.start.z + dz, s.start.level)
    }

    private fun binders(npc: NpcRef, s: State) {
        for (k in 0 until BINDERS) Bosses.summon(npc, Npcs.TFA_MOIA_MINION, spot(s, k), huntRange = 0)
    }

    /** Every 15 ticks each binder moves one spot on (the last loops to the first); anyone under one is pushed out. */
    private fun tickBinders(npc: NpcRef, s: State) {
        if (npc.tick < s.shuffleAt) return
        s.shuffleAt = npc.tick + SHUFFLE_TICKS
        s.ring++
        for ((k, b) in Bosses.minions(npc, Npcs.TFA_MOIA_MINION).withIndex()) {
            val to = spot(s, k)
            b.teleport(to)
            for (p in BossCombat.playersOn(npc, to)) CombatSystem.applyHit(p, b, PUSH_DAMAGE, HitStyle.TYPELESS)
        }
        // MO1: binders are respawned during the fight (not in the enrage).
        val missing = BINDERS - Bosses.minions(npc, Npcs.TFA_MOIA_MINION).size
        if (!s.enraged) repeat(missing) { Bosses.summon(npc, Npcs.TFA_MOIA_MINION, spot(s, BINDERS - 1 - it), huntRange = 0) }
    }

    // ---- enrage (MO3)

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.enraged || npc.hitpoints * THIRDS > npc.maxHitpoints) return
        s.enraged = true
        npc.say("Cease this futile resistance!")
        // RuneLite (LU Necromancer.java:367-374): she vanishes for about 10 ticks.
        npc.setAttackClock(npc.tick + VANISH_TICKS)
        for (b in Bosses.minions(npc, Npcs.TFA_MOIA_MINION)) {
            b.hitpoints = b.maxHitpoints
            b.spotAnim(SpotAnims.MOIA_MINION_EMPOWER_SPOTANIM_01)
        }
        // MO3: 10 ticks later she consumes a binder, +80 HP (OPEN-MO5: the amount by scale).
        npc.queueHit(CONSUME_DELAY) {
            val eaten = Bosses.minions(npc, Npcs.TFA_MOIA_MINION).firstOrNull() ?: return@queueHit
            eaten.despawn()
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + CONSUME_HEAL)
        }
        startCharge(npc, s)
    }

    /** RuneLite (LU Necromancer.java:126-136): the first charge lasts 41 ticks, each next one 2 less. */
    private fun startCharge(npc: NpcRef, s: State) {
        npc.anim(Seqs.MOIA_CHANT01)
        npc.say(if (s.charge == 0) "Useless corpses... Give me your strength!" else "Again!")
        s.chargeEndsAt = npc.tick + maxOf(MIN_CHARGE, FIRST_CHARGE - CHARGE_STEP * s.charge)
        s.charge++
    }

    /** RuneLite (LU Necromancer.java:183-188): in the enrage she attacks every 5 ticks, one animation for both styles. */
    private fun enragedAttack(npc: NpcRef, s: State, victim: EntityRef) {
        if (s.chargeEndsAt >= 0 && npc.tick >= s.chargeEndsAt) {
            // OPEN-MO3: each surviving binder deals about 25 to everyone.
            val alive = Bosses.minions(npc, Npcs.TFA_MOIA_MINION)
            for (b in alive) b.spotAnim(SpotAnims.MOIA_CHANT_PLAYER_EXPLODE)
            if (alive.isNotEmpty()) for (p in TfaKit.arena(npc)) CombatSystem.applyHit(p, npc, BINDER_BLAST * alive.size, HitStyle.TYPELESS)
            startCharge(npc, s)
        }
        val magic = npc.random(2) == 0
        npc.anim(Seqs.MOIA_PROJECTILE_ATTACK01)
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        CombatSystem.announceAttack(npc, victim, style)
        val delay = if (magic) MAGIC_FLIGHT else BossCombat.delay(npc, victim, style)
        BossCombat.projectile(npc, victim, if (magic) SpotAnims.MOIA_DAGGER_MAGIC else SpotAnims.MOIA_DAGGER, delay)
        BossCombat.strike(npc, victim, if (magic) AttackType.MAGIC else AttackType.RANGED, if (magic) MAGIC_MAX else RANGED_MAX, delay)
        npc.setAttackClock(npc.tick + ENRAGED_SPEED)
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.say("Hmph")
        Bosses.dismissMinions(npc)
    }

    /**
     * OPEN-MO2: only the base HP (1,900) is published; the other sizes follow Ezekiel's published ratios
     * (3,000 / 4,350 / 5,610 / ... / 10,650).
     */
    private val HP = intArrayOf(1900, 2755, 3553, 4351, 5149, 5947, 6745)

    /** RuneLite (LU Necromancer.java:152-181): the nine binder spots from her start tile, in walking order. */
    private val SPOTS = listOf(4 to 5, 8 to 3, 12 to 5, 12 to 1, 12 to -3, 8 to -1, 4 to -3, 4 to 1, 8 to 1)

    /** RuneLite: the binders' grid centre is her start tile + (8, 1). */
    private val GRID_CENTRE = 8 to 1

    /** OPEN-MO7: her four teleport sides, 7 tiles out from the grid centre. */
    private val SIDE_OFFSETS = listOf(0 to 7, 7 to 0, 0 to -7, -7 to 0)

    /** MO2 (wiki): the cough ramp. */
    private val COUGH_DAMAGE = intArrayOf(2, 5, 13, 20)

    private const val HUNT = 20
    private const val BURST_MIN = 3
    private const val BURST_VARIANTS = 2
    private const val SPECIAL_CYCLE = 3
    private const val RANGED_MAX = 32
    private const val MAGIC_MAX = 44
    private const val MAGIC_FLIGHT = 6
    private const val FIRST_COUGH = 3
    private const val COUGH_EVERY = 8
    private const val MAX_COUGHS = 4
    private const val COUGH_DRAIN = 10
    private const val BINDERS = 8
    private const val SHUFFLE_TICKS = 15
    private const val PUSH_DAMAGE = 3
    private const val THIRDS = 3
    private const val VANISH_TICKS = 10
    private const val CONSUME_DELAY = 10
    private const val CONSUME_HEAL = 80
    private const val FIRST_CHARGE = 41
    private const val CHARGE_STEP = 2
    private const val MIN_CHARGE = 5
    private const val BINDER_BLAST = 25
    private const val ENRAGED_SPEED = 5
}

/** MO1: an undead binder melees anyone within 2 tiles; Protect from Melee blocks it (OPEN-MO9: max 15). */
internal object TfaMoiaBinder : Boss(Npcs.TFA_MOIA_MINION) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.anim(Seqs.MOIA_MINION_ATTACK)
        for (p in TfaKit.arena(npc, REACH)) BossCombat.strike(npc, p, AttackType.SLASH, MAX_HIT, 0)
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = REACH
    }

    private const val REACH = 2
    private const val MAX_HIT = 15
}
