package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Moia, TFA room 3 west (docs/mechanics/raids/tfa/moia.md MO0-MO3): 3-4 ranged attacks, a teleport and a slow magic
 * projectile; every second magic attack a smoke cough poisons and drains prayer (at most four); eight undead binders
 * circle the arena and shuffle every 15 ticks; at 33% she heals the binders, consumes one and a countdown ends with
 * every surviving binder hitting everyone.
 */
internal object TfaMoia : Boss(Npcs.TFA_MOIA) {
    private class State(val centre: CoordGrid) {
        var ranged = 0
        var rangedBurst = 0
        var magics = 0
        var coughs = 0
        var enraged = false
        var countdownAt = -1L
        var shuffleAt = -1L
        var ring = 0
    }

    private val STATE = AttrKey<State>("tfa_moia")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State(npc.spawn).also {
            npc[STATE] = it
            it.rangedBurst = BURST_MIN + npc.random(BURST_VARIANTS)
            it.shuffleAt = npc.tick + SHUFFLE_TICKS
            binders(npc, it)
        }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        tickBinders(npc, s)
        if (s.countdownAt >= 0 && npc.tick >= s.countdownAt) countdown(npc, s)
        if (s.ranged < s.rangedBurst) {
            s.ranged++
            ranged(npc, victim)
            return
        }
        s.ranged = 0
        s.rangedBurst = BURST_MIN + npc.random(BURST_VARIANTS)
        teleport(npc, s)
        magic(npc, victim)
        s.magics++
        if (s.magics % MAGICS_PER_COUGH == 0 && s.coughs < MAX_COUGHS) cough(npc, s)
    }

    private fun ranged(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.MOIA_DAGGER_ATTACK01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.RANGED)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.MOIA_DAGGER_SPIN, delay)
        BossCombat.strike(npc, victim, AttackType.RANGED, RANGED_MAX, delay)
    }

    /** MO2: a slow magic projectile after the teleport (OPEN-MO6: 4 ticks extra flight). */
    private fun magic(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.MOIA_PROJECTILE_ATTACK01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC) + SLOW_EXTRA
        BossCombat.projectile(npc, victim, SpotAnims.MOIA_DAGGER_MAGIC, delay)
        BossCombat.strike(npc, victim, AttackType.MAGIC, MAGIC_MAX, delay) { victim.spotAnim(SpotAnims.MOIA_DAGGER_MAGIC_IMPACT) }
    }

    /** MO2: "she teleports to another side" (OPEN-MO7: a random point on a ring around the arena centre). */
    private fun teleport(npc: NpcRef, s: State) {
        npc.spotAnim(SpotAnims.MOIA_TELEPORT_SPOTANIM)
        val side = npc.random(SIDES)
        val (dx, dz) = SIDE_OFFSETS[side]
        npc.teleport(CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level))
        npc.spotAnim(SpotAnims.MOIA_TELEPORT_IN_SPOTANIM)
    }

    /** MO2: a smoke cough: poison ramping 2 -> 5 -> 13 -> 20 and a prayer drain (OPEN-MO8: drain 10). */
    private fun cough(npc: NpcRef, s: State) {
        val severity = COUGH_POISON[minOf(s.coughs, COUGH_POISON.size - 1)]
        s.coughs++
        npc.anim(Seqs.MOIA_CHANT01)
        // MO2: the cough lands on the target; for the next 8 ticks contact (a tile away) with a coughed player or a
        // binder spreads it, so the target leaving the group in time keeps it to themself.
        val target = TfaKit.arena(npc).let { it.getOrNull(npc.random(maxOf(1, it.size))) } ?: return
        val infected = HashSet<String>()
        infect(target, severity, infected)
        for (t in 1..SPREAD_TICKS) {
            npc.queueHit(t) {
                if (!npc.isInWorld) return@queueHit
                val players = TfaKit.arena(npc)
                val carriers = players.filter { it.username in infected }
                val binders = Bosses.minions(npc, Npcs.TFA_MOIA_MINION)
                for (p in players) {
                    if (p.username in infected) continue
                    val touching = carriers.any { it.distanceTo(p) <= 1 } || binders.any { TfaKit.gap(it, p) <= 1 }
                    if (touching) infect(p, severity, infected)
                }
            }
        }
    }

    private fun infect(p: tp.plugin.PlayerRef, severity: Int, infected: MutableSet<String>) {
        infected += p.username
        p.spotAnim(SpotAnims.MOIA_INFECT_SPOTANIM)
        if (!Toxic.wearsSerpentineHelm(p)) Poison.poison(p, severity)
        BossCombat.drainPrayer(p, COUGH_DRAIN)
    }

    // ---- undead binders (MO1)

    private fun binders(npc: NpcRef, s: State) {
        for (k in 0 until BINDERS) Bosses.summon(npc, Npcs.TFA_MOIA_MINION, ringTile(s, k), huntRange = 0)
    }

    private fun ringTile(s: State, k: Int): CoordGrid {
        val (dx, dz) = RING[(k + s.ring) % RING.size]
        return CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level)
    }

    /** MO1: the binders cycle clockwise every 15 ticks; anyone underneath is pushed out with minor damage. */
    private fun tickBinders(npc: NpcRef, s: State) {
        if (npc.tick < s.shuffleAt) return
        s.shuffleAt = npc.tick + SHUFFLE_TICKS
        s.ring++
        for ((k, b) in Bosses.minions(npc, Npcs.TFA_MOIA_MINION).withIndex()) {
            val to = ringTile(s, k)
            b.teleport(to)
            for (p in BossCombat.playersOn(npc, to)) CombatSystem.applyHit(p, b, PUSH_DAMAGE, HitStyle.TYPELESS)
        }
        // MO1: binders are respawned during the fight.
        val missing = BINDERS - Bosses.minions(npc, Npcs.TFA_MOIA_MINION).size
        if (!s.enraged) repeat(missing) { Bosses.summon(npc, Npcs.TFA_MOIA_MINION, ringTile(s, BINDERS - 1 - it), huntRange = 0) }
    }

    /** MO3: at 33% she heals the binders, consumes the middle one 10 ticks later (+80 HP) and the countdown starts. */
    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.enraged || npc.hitpoints * THIRDS > npc.maxHitpoints) return
        s.enraged = true
        val binders = Bosses.minions(npc, Npcs.TFA_MOIA_MINION)
        for (b in binders) {
            b.hitpoints = b.maxHitpoints
            b.spotAnim(SpotAnims.MOIA_MINION_EMPOWER_SPOTANIM_01)
        }
        npc.queueHit(CONSUME_DELAY) {
            val eaten = Bosses.minions(npc, Npcs.TFA_MOIA_MINION).firstOrNull() ?: return@queueHit
            eaten.despawn()
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + CONSUME_HEAL)
        }
        s.countdownAt = npc.tick + CONSUME_DELAY + COUNTDOWN
    }

    /** MO3 / OPEN-MO3: each surviving binder deals about 25 to everyone; then a fresh countdown. */
    private fun countdown(npc: NpcRef, s: State) {
        val alive = Bosses.minions(npc, Npcs.TFA_MOIA_MINION)
        for (b in alive) b.spotAnim(SpotAnims.MOIA_CHANT_PLAYER_EXPLODE)
        for (p in TfaKit.arena(npc)) if (alive.isNotEmpty()) CombatSystem.applyHit(p, npc, BINDER_BLAST * alive.size, HitStyle.TYPELESS)
        s.countdownAt = npc.tick + COUNTDOWN
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    /**
     * OPEN-MO2: only the base HP (1,900) is published; the other sizes follow Ezekiel's published ratios
     * (3,000 / 4,350 / 5,610 / ... / 10,650).
     */
    private val HP = intArrayOf(1900, 2755, 3553, 4351, 5149, 5947, 6745)

    /** MO1 / OPEN-MO7: eight binders evenly on a ring of radius 6 (and Moia's four sides at radius 9). */
    private val RING = listOf(0 to 6, 4 to 4, 6 to 0, 4 to -4, 0 to -6, -4 to -4, -6 to 0, -4 to 4)
    private val SIDE_OFFSETS = listOf(0 to 9, 9 to 0, 0 to -9, -9 to 0)
    private val COUGH_POISON = intArrayOf(2, 5, 13, 20)

    private const val HUNT = 20
    private const val BURST_MIN = 3
    private const val BURST_VARIANTS = 2
    private const val MAGICS_PER_COUGH = 2
    private const val MAX_COUGHS = 4
    private const val RANGED_MAX = 32
    private const val MAGIC_MAX = 44
    private const val SLOW_EXTRA = 4
    private const val SIDES = 4
    private const val COUGH_DRAIN = 10
    private const val SPREAD_TICKS = 8
    private const val BINDERS = 8
    private const val SHUFFLE_TICKS = 15
    private const val PUSH_DAMAGE = 3
    private const val THIRDS = 3
    private const val CONSUME_DELAY = 10
    private const val CONSUME_HEAL = 80
    private const val COUNTDOWN = 40
    private const val BINDER_BLAST = 25
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
