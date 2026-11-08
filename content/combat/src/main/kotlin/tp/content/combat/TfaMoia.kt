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
 * Moia, TFA room 3 west (docs/mechanics/raids/tfa/moia.md MO0-MO3; rotation and numbers from the live-game captures,
 * capture-values.md; timings from RuneLite where it has them; D-080).
 *
 * An action every 5 ticks: a dagger volley at every raider, or an event. Events run blink, blink, infection, the first
 * 25 ticks after she appears, then every 20 or 25. A blink takes her to one of four points 7 tiles from the binders'
 * middle and throws a slow magic dagger at everyone. The infection coughs its target, then every 8 ticks four times,
 * spreading to raiders and binders within 2. Eight binders walk a nine-point track in 4-tile legs every 15 ticks and are
 * refilled to six every 30. At 34% she leaves ("Cease this futile resistance!"), returns 14 ticks later to the middle
 * healing 65 a standing binder (at most 215), and chants: 41, 39, 37, 35 ticks apart, each ending 5 ticks early with
 * 25 to everyone per binder still standing.
 */
internal object TfaMoia : Boss(Npcs.TFA_MOIA) {
    private class State(val start: CoordGrid) {
        var slots = 0
        var events = 0
        var nextEventAt = -1L
        var enraged = false
        var chants = 0
        var chantBlastAt = -1L
        var legAt = -1L
        var refillAt = -1L
        var ring = 0
    }

    private val STATE = AttrKey<State>("tfa_moia")

    /** Per player and binder: coughs left. */
    private val COUGHS = AttrKey<Int>("tfa_moia_coughs")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State(npc.spawn).also {
            npc[STATE] = it
            it.nextEventAt = npc.tick + FIRST_EVENT
            it.legAt = npc.tick + FIRST_LEG
            it.refillAt = npc.tick + FIRST_REFILL
            npc.queueHit(BINDERS_AT) { if (npc.isInWorld) binders(npc, it, BINDERS) }
            binderLoop(npc, it)
        }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        npc.setAttackClock(npc.tick + SLOT)
        s.slots++
        if (s.enraged) {
            daggers(npc)
            return
        }
        if (npc.tick >= s.nextEventAt) {
            // Capture: events blink, blink, infection; then every 20 or 25 ticks.
            s.nextEventAt = npc.tick + if (npc.random(2) == 0) EVENT_SHORT else EVENT_LONG
            if (s.events++ % EVENT_CYCLE == EVENT_CYCLE - 1) infection(npc) else blink(npc, s)
            return
        }
        daggers(npc)
    }

    /** Capture: a dagger volley: one at every raider (projectile 4214), landing 1 tick later; unprayed 5..32. */
    private fun daggers(npc: NpcRef) {
        npc.anim(Seqs.MOIA_PROJECTILE_ATTACK01)
        for (p in TfaKit.arena(npc)) {
            BossCombat.projectile(npc, p, SpotAnims.MOIA_DAGGER, DAGGER_FLIGHT)
            val dmg = if (p.protects(CombatStyle.RANGED)) 0 else BossCombat.roll(npc, DAGGER_MIN, DAGGER_MAX)
            CombatSystem.queueHit(p, npc, dmg, HitStyle.RANGED, DAGGER_FLIGHT)
        }
    }

    /**
     * Capture: a blink to one of W (4055, 5662), N (4062, 5669), E (4069, 5662), S (4062, 5655) (her south-west tiles)
     * and a magic dagger at every raider (projectile 4213), landing 6 ticks later; unprayed 11..44.
     */
    private fun blink(npc: NpcRef, s: State) {
        npc.anim(Seqs.MOIA_TELEPORT01)
        npc.spotAnim(SpotAnims.MOIA_TELEPORT_SPOTANIM)
        val (dx, dz) = BLINKS[npc.random(BLINKS.size)]
        npc.teleport(CoordGrid(s.start.x + dx, s.start.z + dz, s.start.level))
        npc.spotAnim(SpotAnims.MOIA_TELEPORT_IN_SPOTANIM)
        for (p in TfaKit.arena(npc)) {
            BossCombat.projectile(npc, p, SpotAnims.MOIA_DAGGER_MAGIC, MAGIC_FLIGHT)
            val dmg = if (p.protects(CombatStyle.MAGIC)) 0 else BossCombat.roll(npc, MAGIC_MIN, MAGIC_MAX)
            CombatSystem.queueHit(p, npc, dmg, HitStyle.MAGIC, MAGIC_FLIGHT) { p.spotAnim(SpotAnims.MOIA_DAGGER_MAGIC_IMPACT) }
        }
    }

    // ---- the infection (MO2)

    /** Capture: the infection (projectile 4218) at one random raider; RuneLite (LU): the first cough 3 ticks after. */
    private fun infection(npc: NpcRef) {
        npc.anim(Seqs.MOIA_DAGGER_ATTACK01)
        val players = TfaKit.arena(npc)
        val p = players.getOrNull(npc.random(maxOf(1, players.size))) ?: return
        BossCombat.projectile(npc, p, SpotAnims.MOIA_INFECT_ATTACK_PROJANIM, FIRST_COUGH)
        npc.queueHit(FIRST_COUGH) { if (npc.isInWorld) infect(npc, p) }
    }

    /** A raider or binder starts coughing unless already ("being coughed on again does not restart it"). */
    private fun infect(npc: NpcRef, e: EntityRef) {
        if ((e[COUGHS] ?: 0) > 0) return
        e[COUGHS] = MAX_COUGHS
        cough(npc, e)
    }

    /**
     * Capture: a cough every 8 ticks, four in all: a poison splat of 2 the tick after on a raider (a serpentine helm
     * stops it); a coughing binder hits raiders within 2 for 20; each cough spreads to raiders and binders within 2.
     */
    private fun gone(e: EntityRef): Boolean = (e is PlayerRef && e.isDying) || (e is NpcRef && !e.isInWorld)

    private fun cough(npc: NpcRef, e: EntityRef) {
        if (!npc.isInWorld || npc.isDying || gone(e)) {
            e[COUGHS] = 0
            return
        }
        e.spotAnim(SpotAnims.MOIA_INFECT_SPOTANIM)
        val players = TfaKit.arena(npc)
        if (e is PlayerRef) {
            if (!Toxic.wearsSerpentineHelm(e)) CombatSystem.queueHit(e, npc, COUGH_SPLAT, HitStyle.TYPELESS, 1)
        } else {
            for (p in players) if (p.distanceTo(e) <= SPREAD) CombatSystem.applyHit(p, npc, BINDER_COUGH, HitStyle.TYPELESS)
        }
        for (p in players) if (p != e && p.distanceTo(e) <= SPREAD) infect(npc, p)
        for (b in Bosses.minions(npc, Npcs.TFA_MOIA_MINION)) if (b != e && b.distanceTo(e) <= SPREAD) infect(npc, b)
        val left = (e[COUGHS] ?: 0) - 1
        e[COUGHS] = left
        if (left > 0) npc.queueHit(COUGH_EVERY) { cough(npc, e) }
    }

    // ---- undead binders (MO1)

    private fun spot(s: State, k: Int): CoordGrid {
        val (dx, dz) = SPOTS[(k + s.ring) % SPOTS.size]
        return CoordGrid(s.start.x + dx, s.start.z + dz, s.start.level)
    }

    private fun binders(npc: NpcRef, s: State, upTo: Int) {
        val have = Bosses.minions(npc, Npcs.TFA_MOIA_MINION).size
        for (k in have until upTo) Bosses.summon(npc, Npcs.TFA_MOIA_MINION, spot(s, k), huntRange = 0)
    }

    /**
     * Capture: 8 binders 6 ticks after she appears on a nine-point track round the middle; from +13 every 15 ticks each
     * walks its 4-tile leg to the next point; from +23 every 30 ticks the track is refilled back to six; anyone on a
     * binder's own tile is pushed a tile ("Grah!").
     */
    private fun binderLoop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            if (npc.tick >= s.legAt) {
                s.legAt = npc.tick + LEG_EVERY
                s.ring++
                for ((k, b) in Bosses.minions(npc, Npcs.TFA_MOIA_MINION).withIndex()) {
                    b.walkTo(spot(s, k))
                    b[LEG_STARTED] = npc.tick
                }
            }
            // Binders swing on their own clock (they are summoned with no hunt range, so they never get a target).
            for (b in Bosses.minions(npc, Npcs.TFA_MOIA_MINION)) TfaMoiaBinder.swingTick(b)
            if (!s.enraged && npc.tick >= s.refillAt) {
                s.refillAt = npc.tick + REFILL_EVERY
                binders(npc, s, REFILL_TO)
            }
            binderLoop(npc, s)
        }
    }

    // ---- enrage (MO3)

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.enraged || npc.hitpoints * PERCENT > npc.maxHitpoints * ENRAGE_PERCENT) return
        s.enraged = true
        npc.say("Cease this futile resistance!")
        // Capture: +1 every binder is healed to full and she leaves; +14 she is back at the middle, healing 65 a binder
        // standing (at most 215); the first chant 2 ticks after.
        npc.queueHit(1) {
            for (b in Bosses.minions(npc, Npcs.TFA_MOIA_MINION)) {
                b.hitpoints = b.maxHitpoints
                b.spotAnim(SpotAnims.MOIA_MINION_EMPOWER_SPOTANIM_01)
            }
            npc.spotAnim(SpotAnims.MOIA_TELEPORT_SPOTANIM)
        }
        npc.setAttackClock(npc.tick + RETURN_AT + FIRST_CHANT + 1)
        npc.queueHit(RETURN_AT) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            npc.teleport(CoordGrid(s.start.x + MIDDLE.first, s.start.z + MIDDLE.second, s.start.level))
            npc.spotAnim(SpotAnims.MOIA_TELEPORT_IN_SPOTANIM)
            val standing = Bosses.minions(npc, Npcs.TFA_MOIA_MINION).size
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + minOf(RETURN_HEAL_CAP, RETURN_HEAL * standing))
            npc.queueHit(FIRST_CHANT) { if (npc.isInWorld) chant(npc, s) }
        }
    }

    /** Capture / RuneLite: chants 41, 39, 37, 35 ... ticks apart; each blasts 5 ticks before the next. */
    private fun chant(npc: NpcRef, s: State) {
        npc.anim(Seqs.MOIA_CHANT01)
        npc.say(if (s.chants == 0) "Useless corpses... Give me your strength!" else "Again!")
        val gap = maxOf(MIN_CHANT, FIRST_CHANT_GAP - CHANT_STEP * s.chants)
        s.chants++
        s.chantBlastAt = npc.tick + gap - BLAST_EARLY
        // The blast keeps its own time (5 ticks before the next chant), with or without a target in reach.
        npc.queueHit(gap - BLAST_EARLY) { if (npc.isInWorld && !npc.isDying) chantBlast(npc, s) }
        npc.queueHit(gap) { if (npc.isInWorld && !npc.isDying) chant(npc, s) }
    }

    /** Capture: 25 to every raider per empowered binder still standing. */
    private fun chantBlast(npc: NpcRef, s: State) {
        s.chantBlastAt = -1
        val alive = Bosses.minions(npc, Npcs.TFA_MOIA_MINION)
        for (b in alive) b.spotAnim(SpotAnims.MOIA_CHANT_PLAYER_EXPLODE)
        if (alive.isNotEmpty()) for (p in TfaKit.arena(npc)) CombatSystem.applyHit(p, npc, BINDER_BLAST * alive.size, HitStyle.TYPELESS)
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.say("Hmph")
        Bosses.dismissMinions(npc)
        // An infection still running ends with her (else the raider stays "infected" and immune next fight).
        for (p in TfaKit.arena(npc)) p[COUGHS] = 0
    }

    /** When a binder last started a leg (its first swing is 7 ticks into a leg). */
    val LEG_STARTED = AttrKey<Long>("tfa_moia_leg")

    /** Capture: HP 1,900 at 1-2 and 2,760 at three (x1.45, then +0.42 a raider, rounded to 10). */
    private val HP = intArrayOf(1900, 2760, 3550, 4350, 5150, 5950, 6750)

    /** RuneLite (LU): the nine binder spots from her start tile in walking order; the middle is start + (8, 1). */
    private val SPOTS = listOf(4 to 5, 8 to 3, 12 to 5, 12 to 1, 12 to -3, 8 to -1, 4 to -3, 4 to 1, 8 to 1)
    private val MIDDLE = 7 to 0

    /** Capture: the blink points as offsets from her start (the W point, (4055, 5662)). */
    private val BLINKS = listOf(0 to 0, 7 to 7, 14 to 0, 7 to -7)

    private const val HUNT = 20
    private const val SLOT = 5
    private const val FIRST_EVENT = 25
    private const val EVENT_SHORT = 20
    private const val EVENT_LONG = 25
    private const val EVENT_CYCLE = 3
    private const val DAGGER_FLIGHT = 1
    private const val DAGGER_MIN = 5
    private const val DAGGER_MAX = 32
    private const val MAGIC_FLIGHT = 6
    private const val MAGIC_MIN = 11
    private const val MAGIC_MAX = 44
    private const val FIRST_COUGH = 3
    private const val COUGH_EVERY = 8
    private const val MAX_COUGHS = 4
    private const val COUGH_SPLAT = 2
    private const val BINDER_COUGH = 20
    private const val SPREAD = 2
    private const val BINDERS = 8
    private const val BINDERS_AT = 6
    private const val FIRST_LEG = 13
    private const val LEG_EVERY = 15
    private const val FIRST_REFILL = 23
    private const val REFILL_EVERY = 30
    private const val REFILL_TO = 6
    private const val ENRAGE_PERCENT = 34
    private const val PERCENT = 100
    private const val RETURN_AT = 14
    private const val RETURN_HEAL = 65
    private const val RETURN_HEAL_CAP = 215
    private const val FIRST_CHANT = 2
    private const val FIRST_CHANT_GAP = 41
    private const val CHANT_STEP = 2
    private const val MIN_CHANT = 7
    private const val BLAST_EARLY = 5
    private const val BINDER_BLAST = 25
}

/**
 * Capture: a binder swings at a raider within 2 (its own tile included) 7 ticks after a leg starts, then every 5:
 * 4..22; a raider on its own tile is pushed a tile ("Grah!"). Protect from Melee blocks it (MO1).
 */
internal object TfaMoiaBinder : Boss(Npcs.TFA_MOIA_MINION) {
    private val NEXT_SWING = AttrKey<Long>("tfa_moia_binder_swing")

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Swings come from Moia's loop (swingTick).
        npc.setAttackClock(npc.tick + SWING_EVERY)
    }

    /** One tick of a binder: its swing 7 ticks into a leg, then every 5, at a raider within 2. */
    fun swingTick(npc: NpcRef) {
        if (npc.isDying) return
        val leg = npc[TfaMoia.LEG_STARTED] ?: -1L
        if (leg >= 0 && npc.tick - leg < FIRST_SWING) return
        if (npc.tick < (npc[NEXT_SWING] ?: 0L)) return
        val p = TfaKit.arena(npc, REACH).firstOrNull() ?: return
        npc[NEXT_SWING] = npc.tick + SWING_EVERY
        npc.anim(Seqs.MOIA_MINION_ATTACK)
        if (p.coord == npc.coord) {
            npc.say("Grah!")
            p.teleport(CoordGrid(p.coord.x + 1, p.coord.z, p.coord.level))
        }
        val dmg = if (p.protects(CombatStyle.MELEE)) 0 else BossCombat.roll(npc, MIN_HIT, MAX_HIT)
        CombatSystem.queueHit(p, npc, dmg, HitStyle.MELEE, 0)
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = REACH
    }

    private const val REACH = 2
    private const val MIN_HIT = 4
    private const val MAX_HIT = 22
    private const val FIRST_SWING = 7
    private const val SWING_EVERY = 5
}
