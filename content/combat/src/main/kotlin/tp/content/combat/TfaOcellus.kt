package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** Ocellus's fight state, shared by the four phase scripts. */
internal class OcellusState(val centre: CoordGrid, val size: Int) {
    var phase = 1

    /** Bumped on every phase change: a loop from an older phase stops. */
    var generation = 0
    var started = false
    var quadrant = 0

    // P1
    var meter = 0
    var middles = 0
    var corner = 0
    var magicWalk = false

    // P3
    var segment = 0
    var moves = 0
    var busyUntil = 0L
    var spinNext = true
    var spun = false
    var vined = false
    var batches = 0
    var bigs = 0
    var pendingMove = false
    var moving = false
    val patches = HashSet<CoordGrid>()

    // P4
    var p4Start = 0L
    var cycles = 0
    var deflect: Set<HitStyle> = emptySet()
}

/**
 * Ocellus, the TFA final boss (docs/mechanics/raids/tfa/ocellus.md OC1-OC4): P1 [TfaOcellusP1], P2 the Blade
 * [TfaOcellusP2], P3 [TfaOcellusP3], P4 [TfaOcellusP4]. Numbers and rotations from the live-game captures (capture-values.md),
 * timings from RuneLite (runelite-timings.md, D-080). P1 has its own pool; P2 to P4 share one ("the Blade's damage is
 * his"). Every phase runs on its own tick script once he is engaged.
 */
internal object TfaOcellus : Boss(Npcs.TFA_OCELLUS_P1, Npcs.TFA_OCELLUS_P2, Npcs.TFA_OCELLUS_P3) {
    /** Its own script moves and targets it: a hit does not make it chase. */
    override fun retaliates(npc: NpcRef): Boolean = false

    private val STATE = AttrKey<OcellusState>("tfa_ocellus")

    /** Censer buffs on a player: (style, until tick). */
    val CENSER_STYLE = AttrKey<HitStyle>("tfa_censer_style")
    val CENSER_UNTIL = AttrKey<Long>("tfa_censer_until")

    /** Capture: lines a raider stood on, paid back as a heal on their next landing hit. */
    val LINES_STOOD = AttrKey<Int>("tfa_ocellus_lines")

    fun state(npc: NpcRef): OcellusState =
        npc[STATE] ?: OcellusState(CoordGrid(npc.spawn.x + 1, npc.spawn.z + 1, npc.spawn.level), TfaKit.size(npc)).also {
            npc[STATE] = it
            TfaKit.setPool(npc, P1_HP[it.size - MIN_SCALE])
            placeCensers(npc, it)
            // ocellus.md: varbit TFA_OCELLUS_MET reveals his name and his stats category.
            for (p in TfaKit.arena(npc)) p.setVarbit(Varbits.TFA_OCELLUS_MET, 1)
        }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    /** The first swing engages him; from then on each phase's own script drives him (no chasing). */
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        engage(npc, state(npc))
        npc.setAttackClock(npc.tick + IDLE_CHECK)
    }

    /** His first swing or the first hit on him starts P1's script. */
    private fun engage(npc: NpcRef, s: OcellusState) {
        if (s.started) return
        s.started = true
        TfaOcellusP1.start(npc, s)
    }

    /**
     * OC3 censers (locs 62693-62700, cache: "Interact", each with an inactive form): one in each quadrant, shuffled every
     * fight; the fight starts in the quadrant diagonally opposite the Bandosian Censer (RuneLite LU FinalBoss.java:117-122).
     * OPEN-OC14: each sits 4 tiles beyond its quadrant's landing spot, away from the centre.
     */
    private fun placeCensers(npc: NpcRef, s: OcellusState) {
        val order = CENSERS.toMutableList()
        val placed = IntArray(QUADRANTS.size)
        for (q in QUADRANTS.indices) placed[q] = order.removeAt(npc.random(order.size))
        for ((q, id) in placed.withIndex()) {
            val (dx, dz) = QUADRANTS[q]
            val at =
                CoordGrid(
                    s.centre.x + dx + Integer.signum(dx) * CENSER_OUT,
                    s.centre.z + dz + Integer.signum(dz) * CENSER_OUT,
                    s.centre.level,
                )
            npc.addLoc(id, at, WALL_SHAPE, 0)
        }
        val bandos = placed.indexOf(Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE)
        s.quadrant = (bandos + 2) % QUADRANTS.size
    }

    /**
     * A censer used (OC3; capture: used at range, lit across all phases): Bandosian = melee, Armadylean = ranged
     * (OPEN-OC14: +10% damage for 100 ticks), Zarosian = +50% special energy, Saradomin = +40 Hitpoints and +40 Prayer;
     * the censer goes inactive for the rest of the fight.
     */
    fun useCenser(p: PlayerRef, id: Int, at: CoordGrid) {
        when (id) {
            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE -> {
                buff(p, HitStyle.MELEE)
            }

            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_RANGED -> {
                buff(p, HitStyle.RANGED)
            }

            Locs.TFA_OCELLUS_ARTIFACT_SPEC -> {
                TfaKit.addSpecEnergy(p, ZAROS_SPEC)
            }

            Locs.TFA_OCELLUS_ARTIFACT_HEAL -> {
                p.hitpoints = minOf(p.maxHitpoints, p.hitpoints + SARA_RESTORE)
                p.setLevel(Stat.PRAYER, minOf(p.baseLevel(Stat.PRAYER), p.level(Stat.PRAYER) + SARA_RESTORE))
            }
        }
        p.addLoc(id + 1, at, WALL_SHAPE, 0)
    }

    private fun buff(p: PlayerRef, style: HitStyle) {
        p[CENSER_STYLE] = style
        p[CENSER_UNTIL] = p.tick + CENSER_TICKS
    }

    // ---- damage and phase changes

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        engage(npc, s)
        val p = source as? PlayerRef
        var d = censer(p, damage, style)
        if (p != null && d > 0) payLineHeal(npc, p)
        if (s.phase == P3) d = opposite(npc, s, p, d)
        if (s.phase == P4) d = TfaOcellusP4.incoming(npc, s, p, d, style)
        return d
    }

    /** Capture: a raider's line heal is 4 a line - 1 + d2 + d3 (1 line 3..6 ... 4 lines 14..18), on their next landing hit. */
    private fun payLineHeal(npc: NpcRef, p: PlayerRef) {
        val n = p[LINES_STOOD] ?: 0
        if (n <= 0) return
        p[LINES_STOOD] = 0
        val heal = LINE_HEAL_PER * n - 1 + npc.random(2) + npc.random(LINE_HEAL_D3)
        p.hitpoints = minOf(p.maxHitpoints, p.hitpoints + heal)
    }

    /** A Bandosian (melee) or Armadylean (ranged) censer's buff (OPEN-OC14: +10% damage of that style for 100 ticks). */
    private fun censer(p: PlayerRef?, damage: Int, style: HitStyle): Int {
        val until = p?.get(CENSER_UNTIL) ?: return damage
        return if (p.tick < until && p[CENSER_STYLE] == style) damage * (PERCENT + CENSER_BONUS) / PERCENT else damage
    }

    /** OC3: +10% damage from attacks made from directly opposite the tank. */
    private fun opposite(npc: NpcRef, s: OcellusState, p: PlayerRef?, damage: Int): Int {
        p ?: return damage
        val tank = TfaOcellusP3.tank(npc, s) ?: return damage
        if (p == tank) return damage
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val opposite =
            Integer.signum(p.coord.x - cx) == -Integer.signum(tank.coord.x - cx) &&
                Integer.signum(p.coord.z - cz) == -Integer.signum(tank.coord.z - cz)
        return if (opposite) damage * (PERCENT + OPPOSITE_BONUS) / PERCENT else damage
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.phase == P3) TfaOcellusP3.checkQuadrant(npc, s)
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
        state(npc).generation++
    }

    /** P1's pool gone: P2 with the shared pool. P2 cannot die (its end is a fixed script); P3 cannot die before P4. */
    override fun onLethal(npc: NpcRef): Boolean {
        val s = state(npc)
        return when (s.phase) {
            P1 -> {
                TfaOcellusP2.start(npc, s)
                true
            }

            P2 -> {
                npc.hitpoints = 1
                true
            }

            P3 -> {
                npc.hitpoints = 1
                TfaOcellusP3.checkQuadrant(npc, s)
                true
            }

            else -> {
                false
            }
        }
    }

    /** Moves him to phase [phase]'s NPC, keeping the shared pool's hitpoints. */
    fun become(npc: NpcRef, s: OcellusState, phase: Int, type: Int, freshPool: Boolean) {
        s.phase = phase
        s.generation++
        val hp = npc.hitpoints
        npc.transform(type, restoreStats = true)
        TfaKit.setPool(npc, REMAINDER_HP[s.size - MIN_SCALE])
        if (!freshPool) npc.hitpoints = hp
    }

    /** Runs [action] every tick while phase [phase] of this fight lasts. */
    fun loop(npc: NpcRef, s: OcellusState, action: () -> Unit) {
        val gen = s.generation

        fun step() {
            npc.queueHit(1, strong = false) {
                if (!npc.isInWorld || s.generation != gen) return@queueHit
                action()
                step()
            }
        }
        step()
    }

    fun quadrantSpot(s: OcellusState, q: Int): CoordGrid {
        val (dx, dz) = QUADRANTS[q % QUADRANTS.size]
        return CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level)
    }

    /** RuneLite (LU FinalBoss.java:1820-1864): quadrant landing spots (south-west tile) from the arena centre. */
    val QUADRANTS = listOf(-6 to -6, 6 to -6, 6 to 6, -6 to 6)

    /** The four active censers (cache gamevals `TFA_OCELLUS_ARTIFACT_*`; the inactive form is id + 1). */
    val CENSERS =
        intArrayOf(
            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_MELEE,
            Locs.TFA_OCELLUS_ARTIFACT_DAMAGE_RANGED,
            Locs.TFA_OCELLUS_ARTIFACT_SPEC,
            Locs.TFA_OCELLUS_ARTIFACT_HEAL,
        )

    /** Capture: P1 HP 2500 at 1-2, 3630, 4680 ...; P2-P4 one pool 5250 at 1-2, 7610, 9820 ... (ocellus.md data). */
    private val P1_HP = intArrayOf(2500, 3630, 4680, 5730, 6780, 7830, 8880)
    private val REMAINDER_HP = intArrayOf(5250, 7610, 9820, 12020, 14230, 16430, 18640)

    const val P1 = 1
    const val P2 = 2
    const val P3 = 3
    const val P4 = 4
    const val PERCENT = 100
    const val FLOOR_SHAPE = 22
    const val WALL_SHAPE = 10
    private const val MIN_SCALE = 2
    private const val HUNT = 24
    private const val IDLE_CHECK = 5
    private const val LINE_HEAL_PER = 4
    private const val LINE_HEAL_D3 = 3
    private const val OPPOSITE_BONUS = 10
    private const val CENSER_BONUS = 10
    private const val CENSER_TICKS = 100
    private const val CENSER_OUT = 4
    private const val ZAROS_SPEC = 500
    private const val SARA_RESTORE = 40
}
