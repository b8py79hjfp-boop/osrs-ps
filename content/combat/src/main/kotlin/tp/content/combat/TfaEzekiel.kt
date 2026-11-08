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
 * Ezekiel, TFA room 2 west (docs/mechanics/raids/tfa/ezekiel.md EZ1-EZ3; numbers and rotations from the live-game
 * captures (capture-values.md), timings from RuneLite where it has them; D-080).
 *
 * He stands in the middle 3x3 of a 9x9 arena (x 4059-4067, z 5531-5539). One clock: melee, melee (5 ticks apart),
 * the star special (5 after a melee, 7 before the next melee). The cleave hits the 16-tile ring round him except the
 * three tiles opposite the tank; out of reach he casts at everyone. The star lights a ring, arms and tips around him and
 * hits at S+3 and S+5. Swords take one of five layouts; a sword through his south-west lane hits him for its charge
 * (the wind-surge damage it took) and weakens him; a cross or two-row layout that misses him heals him a tenth. Under
 * 33.5% he jumps beside the cage (the middle cell, where the players go) for volleys of three strikes every 11 ticks in
 * a fixed six-volley order.
 */
internal object TfaEzekiel : Boss(Npcs.TFA_EZEKIEL) {
    private class State {
        var step = 0
        var enraged = false
        var volley = 0
        var weakUntil = -1L
        var front = 0 to 1
        var cell: CoordGrid? = null
    }

    private val STATE = AttrKey<State>("tfa_ezekiel")

    /** A sword's charge: the wind-spell damage it has taken (capture: capped at 80). */
    val CHARGE = AttrKey<Int>("tfa_ezekiel_sword_charge")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State().also { npc[STATE] = it }
    }

    /** Template tile -> this instance (Ezekiel spawns at the arena's middle cell, south-west tile (4062, 5534)). */
    private fun at(npc: NpcRef, x: Int, z: Int) = CoordGrid(npc.spawn.x + x - SPAWN_X, npc.spawn.z + z - SPAWN_Z, npc.spawn.level)

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (npc.tick < s.weakUntil) return
        if (s.enraged) {
            finaleVolley(npc, s)
            return
        }
        // Capture: one clock, M, M, S: melee -> melee 5, melee -> special 5, special -> melee 7.
        when (s.step++ % RHYTHM) {
            2 -> {
                star(npc)
                swords(npc)
                npc.setAttackClock(npc.tick + AFTER_SPECIAL)
            }

            else -> {
                if (distance) magic(npc) else cleave(npc, victim)
                npc.setAttackClock(npc.tick + ATTACK_SPEED)
            }
        }
    }

    /**
     * Capture: the cleave hits the 16-tile ring around his 3x3 except the three tiles opposite the tank (the mirror
     * through his centre plus its two neighbours on that side), and a raider under him (0..12). Through Protect from
     * Melee 1..9, unprayed 14..27.
     */
    private fun cleave(npc: NpcRef, tank: EntityRef) {
        npc.anim(Seqs.NPC_EZEKIEL_MELEE01)
        CombatSystem.announceAttack(npc, tank, CombatStyle.MELEE)
        val cx = npc.coord.x + 1
        val cz = npc.coord.z + 1
        val mx = cx - (tank.coord.x - cx)
        val mz = cz - (tank.coord.z - cz)
        for (p in TfaKit.arena(npc, MELEE_SCAN)) {
            val dx = p.coord.x - cx
            val dz = p.coord.z - cz
            val ring = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == 2
            val under = TfaKit.under(npc, p.coord)
            if (!ring && !under) continue
            val opposite = ring && maxOf(kotlin.math.abs(p.coord.x - mx), kotlin.math.abs(p.coord.z - mz)) <= 1
            if (opposite) continue
            val dmg =
                when {
                    under -> BossCombat.roll(npc, 0, UNDER_MAX)
                    p.protects(CombatStyle.MELEE) -> BossCombat.roll(npc, PRAYED_MIN, PRAYED_MAX)
                    else -> BossCombat.roll(npc, CLEAVE_MIN, CLEAVE_MAX)
                }
            CombatSystem.queueHit(p, npc, dmg, HitStyle.MELEE, 0)
        }
    }

    /** Capture: the tank out of reach: magic at every raider in the room, landing 2 ticks after the cast (4..21). */
    private fun magic(npc: NpcRef) {
        npc.anim(Seqs.NPC_EZEKIEL_MAGIC01)
        npc.spotAnim(SpotAnims.EZEKIEL_MAGIC_ATTACK_VFX01)
        for (p in TfaKit.arena(npc)) {
            CombatSystem.queueHit(p, npc, BossCombat.roll(npc, MAGIC_MIN, MAGIC_MAX), HitStyle.MAGIC, MAGIC_DELAY)
            p.spotAnim(SpotAnims.EZEKIEL_MAGIC_ATTACK_IMPACT_VFX01)
        }
    }

    /**
     * Capture: the star: the ring round him, arms 3 wide one tile further, the middle tile one further still, clipped to
     * the 9x9; every raider on a star tile is hit at S+3 and S+5 (S+5 also under him), 20..34 typeless.
     */
    private fun star(npc: NpcRef) {
        npc.anim(Seqs.NPC_EZEKIEL_SPECIAL01)
        npc.spotAnim(SpotAnims.EZEKIEL_SPECIAL_CRACK_VFX01)
        val tiles = starTiles(npc)
        for (t in tiles) npc.spotAnimAt(t, SpotAnims.VFX_EZEKIEL_STAR_EXPLOSION_MARKER01)
        for ((k, beat) in STAR_BEATS.withIndex()) {
            npc.queueHit(beat) {
                if (!npc.isInWorld || npc.isDying) return@queueHit
                for (t in tiles) npc.spotAnimAt(t, SpotAnims.VFX_EZEKIEL_STAR_EXPLOSION01)
                for (p in TfaKit.arena(npc)) {
                    val hit = TfaKit.on(p, tiles) || (k == 1 && TfaKit.under(npc, p.coord))
                    if (hit) CombatSystem.applyHit(p, npc, BossCombat.roll(npc, STAR_MIN, STAR_MAX), HitStyle.TYPELESS)
                }
            }
        }
    }

    private fun starTiles(npc: NpcRef): List<CoordGrid> {
        val cx = npc.coord.x + 1
        val cz = npc.coord.z + 1
        val out = ArrayList<CoordGrid>()
        for (dx in -STAR_REACH..STAR_REACH) {
            for (dz in -STAR_REACH..STAR_REACH) {
                val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz))
                val perp = if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) kotlin.math.abs(dz) else kotlin.math.abs(dx)
                val on = d == 2 || (d == 3 && perp <= 1) || (d == STAR_REACH && perp == 0)
                if (on) out += CoordGrid(cx + dx, cz + dz, npc.coord.level)
            }
        }
        return out
    }

    // ---- swords (EZ2)

    /**
     * Capture: five layouts (cross 21/42, three rows from the west 9/42, three columns from the north 6/42, the two rows
     * z 5532/5536 6/42): a cross is one column x in {4059, 4062, 4065} and one row z in {5531, 5534, 5537}, never the
     * middle cell nor (4059, 5531). RuneLite (LU): each sword flies 15 ticks after it appears, line layouts one a tick.
     * Swords are 80 HP (cache); their charge is the wind-spell damage they take, up to 80. Returns the lanes as
     * (horizontal, row-or-column) and whether they fly one a tick.
     */
    private fun layout(npc: NpcRef): Pair<List<Pair<Boolean, Int>>, Boolean> =
        when (npc.random(LAYOUT_WEIGHT_TOTAL)) {
            in 0 until CROSS_WEIGHT -> cross(npc) to false
            in CROSS_WEIGHT until CROSS_WEIGHT + ROWS_WEIGHT -> ROWS.map { true to it } to true
            in CROSS_WEIGHT + ROWS_WEIGHT until CROSS_WEIGHT + ROWS_WEIGHT + COLS_WEIGHT -> COLUMNS.map { false to it } to true
            else -> TWO_ROWS.map { true to it } to false
        }

    private fun cross(npc: NpcRef): List<Pair<Boolean, Int>> {
        var col: Int
        var row: Int
        do {
            col = COLUMNS[npc.random(COLUMNS.size)]
            row = ROWS[npc.random(ROWS.size)]
        } while (excludedCross(col, row))
        return listOf(false to col, true to row)
    }

    private fun excludedCross(col: Int, row: Int): Boolean = (col == MIDDLE_X && row == MIDDLE_Z) || (col == COLUMNS[0] && row == ROWS[0])

    private fun swords(npc: NpcRef) {
        val (lanes, line) = layout(npc)
        var hitHim = false
        val flights = lanes.size
        var landed = 0
        for ((k, lane) in lanes.withIndex()) {
            val (horizontal, pos) = lane
            val start = if (horizontal) at(npc, ARENA_MIN_X - SWORD_SIZE, pos) else at(npc, pos, ARENA_MAX_Z + 1)
            val sword = Bosses.summon(npc, Npcs.TFA_EZEKIEL_SWORD, start, huntRange = 0)
            sword.spotAnim(SpotAnims.VFX_EZEKIEL_SWORD_DROP01)
            val tiles = laneTiles(npc, horizontal, pos)
            val inPath = HashSet<String>()
            val fly = SWORD_FLY + if (line) k else 0
            sword.queueHit(fly - 1) { for (v in TfaKit.arena(npc)) if (TfaKit.on(v, tiles)) inPath += v.username }
            sword.queueHit(fly) {
                if (!npc.isInWorld || !sword.isInWorld) return@queueHit
                val charge = minOf(CHARGE_CAP, sword[CHARGE] ?: 0)
                for (v in TfaKit.arena(npc)) {
                    if (TfaKit.on(v, tiles)) {
                        CombatSystem.applyHit(v, npc, charge + BossCombat.roll(npc, BAND_MIN, BAND_MAX), HitStyle.TYPELESS)
                    } else if (v.username in inPath) {
                        // EZ2: a "perfect dodge" restores 3% special energy.
                        TfaKit.addSpecEnergy(v, DODGE_ENERGY)
                    }
                }
                if (tiles.any { it == npc.coord }) {
                    hitHim = true
                    CombatSystem.applyHit(npc, null, charge + BossCombat.roll(npc, SELF_MIN, SELF_MAX), HitStyle.TYPELESS)
                    weaken(npc)
                }
                sword.transform(Npcs.TFA_EZEKIEL_SWORD_NOOP, restoreStats = false)
                sword.despawn()
                if (++landed == flights && !hitHim && !line) heal(npc)
            }
        }
    }

    private fun laneTiles(npc: NpcRef, horizontal: Boolean, pos: Int): List<CoordGrid> {
        val out = ArrayList<CoordGrid>()
        // A row runs the arena's x range, a column its z range (3 tiles of run-up past each edge).
        val (from, to) = if (horizontal) ARENA_MIN_X to ARENA_MAX_X else ARENA_MIN_Z to ARENA_MAX_Z
        for (a in from - SWORD_SIZE..to + SWORD_SIZE) {
            for (w in 0 until SWORD_SIZE) out += if (horizontal) at(npc, a, pos + w) else at(npc, pos + w, a)
        }
        return out
    }

    /** Capture: weakened at W (seq 14691), back at W+8, next attack W+12; he does not step meanwhile. */
    private fun weaken(npc: NpcRef) {
        val s = state(npc)
        npc.anim(Seqs.NPC_EZEKIEL_IDLE_TO_WEAKEN01)
        s.weakUntil = npc.tick + WEAK_NEXT
        npc.frozenUntil = maxOf(npc.frozenUntil, npc.tick + WEAK_STILL)
        npc.queueHit(WEAK_RECOVER) { if (npc.isInWorld) npc.anim(Seqs.NPC_EZEKIEL_WEAKEN_TO_IDLE01) }
        npc.setAttackClock(maxOf(npc.attackClock(), s.weakUntil))
        s.step = 0
    }

    /** Capture: a cross or the two rows that all miss him heal a tenth of his max at fly+4 (a line layout heals nothing). */
    private fun heal(npc: NpcRef) {
        npc.queueHit(HEAL_DELAY - 2) { if (npc.isInWorld) npc.anim(Seqs.NPC_EZEKIEL_HEAL01) }
        npc.queueHit(HEAL_DELAY) {
            // No heal once the finale has begun (it would lift him back above its threshold).
            if (!npc.isInWorld || npc.isDying || state(npc).enraged) return@queueHit
            npc.spotAnim(SpotAnims.EZEKIEL_HEAL_SPOTANIM_VFX01)
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + npc.maxHitpoints / HEAL_TENTH)
        }
    }

    // ---- the finale (EZ3)

    /**
     * Capture: at 33.5% he jumps to the arena cell beside the cage (the middle cell) on the side he was nearest; the
     * players are put in the cage; F+6 the cage and seven fence swords, F+8 three V swords; volleys from F+12.
     */
    private fun enrage(npc: NpcRef, s: State) {
        s.enraged = true
        // The finale replaces the sword phase: swords in flight go.
        Bosses.dismissMinions(npc)
        npc.anim(Seqs.NPC_EZEKIEL_FINALE01)
        val tank = TfaKit.arena(npc).firstOrNull()
        val dx = Integer.signum((tank?.coord?.x ?: npc.coord.x) - (npc.coord.x + 1))
        val dz = if (dx == 0) Integer.signum((tank?.coord?.z ?: npc.coord.z) - (npc.coord.z + 1)).let { if (it == 0) 1 else it } else 0
        s.front = -dx to -dz
        val cage = at(npc, MIDDLE_X, MIDDLE_Z)
        s.cell = cage
        npc.teleport(CoordGrid(cage.x + dx * CELL, cage.z + dz * CELL, cage.level))
        for ((k, p) in TfaKit.arena(npc).withIndex()) p.teleport(CoordGrid(cage.x + k % CELL, cage.z + (k / CELL) % CELL, cage.level))
        npc.queueHit(CAGE_AT) {
            for (k in 0 until FENCE_SWORDS) {
                val t = CoordGrid(cage.x - 1 + k % (CELL + 2), cage.z - 1 + if (k < CELL + 2) 0 else CELL + 1, cage.level)
                Bosses.summon(npc, Npcs.TFA_EZEKIEL_SWORD_NOOP, t, huntRange = 0).spotAnim(SpotAnims.VFX_EZEKIEL_SWORD_DROP01)
            }
        }
        npc.queueHit(V_SWORDS_AT) {
            for (k in 0 until V_SWORDS) {
                Bosses.summon(
                    npc,
                    Npcs.TFA_EZEKIEL_SWORD_NOOP,
                    CoordGrid(cage.x + k, cage.z + CELL + 1, cage.level),
                    huntRange = 0,
                )
            }
        }
        npc.setAttackClock(npc.tick + FIRST_VOLLEY)
    }

    /**
     * Capture: a volley is three strikes at V+4, V+5, V+6 (RuneLite agrees), every 11 ticks, in a fixed six-volley order:
     * B l r / B r l / r B l / l B r / B r l / B l r. B = both sides (the far row is safe); l / r = the front row and that
     * side's column (the far two tiles of the other column are safe). "Both" is never last. OPEN-EZ5: 6..19 a strike.
     */
    private fun finaleVolley(npc: NpcRef, s: State) {
        val cage = s.cell ?: return
        npc.anim(Seqs.NPC_EZEKIEL_FINALE_LOOP01)
        npc.spotAnim(SpotAnims.VFX_EZEKIEL_FINALE_CAST01)
        val order = VOLLEYS[s.volley++ % VOLLEYS.size]
        val struck = HashSet<String>()
        val watching = TfaKit.arena(npc)
        val (fx, fz) = s.front
        for ((k, kind) in order.withIndex()) {
            val safe = safeTiles(cage, fx, fz, kind)
            npc.queueHit(VOLLEY_FIRST + k) {
                if (!npc.isInWorld || npc.isDying) return@queueHit
                npc.spotAnimAt(cage, IMPACTS[k])
                for (p in TfaKit.arena(npc)) {
                    if (safe.any { it.x == p.coord.x && it.z == p.coord.z }) continue
                    struck += p.username
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, VOLLEY_MIN, VOLLEY_MAX), HitStyle.TYPELESS)
                }
            }
        }
        // EZ3: dodging a whole volley restores 9% special energy.
        npc.queueHit(VOLLEY_FIRST + order.length) {
            for (p in watching) if (!p.isDying && p.username !in struck) TfaKit.addSpecEnergy(p, FINALE_DODGE_ENERGY)
        }
        npc.setAttackClock(npc.tick + VOLLEY_CYCLE)
    }

    /** The safe cage tiles for a strike [kind] ('B', 'l', 'r'). */
    private fun safeTiles(cage: CoordGrid, fx: Int, fz: Int, kind: Char): List<CoordGrid> =
        when (kind) {
            'B' -> (0..2).map { cageTile(cage, fx, fz, it, 2) }
            'l' -> listOf(cageTile(cage, fx, fz, 2, 1), cageTile(cage, fx, fz, 2, 2))
            else -> listOf(cageTile(cage, fx, fz, 0, 1), cageTile(cage, fx, fz, 0, 2))
        }

    /** Cage coordinates: column c (0..2) left to right seen from him, row r (0 = front, 2 = far). */
    private fun cageTile(cage: CoordGrid, fx: Int, fz: Int, c: Int, r: Int): CoordGrid {
        val x = if (fx != 0) cage.x + (if (fx > 0) 2 - r else r) else cage.x + (if (fz > 0) c else 2 - c)
        val z = if (fz != 0) cage.z + (if (fz > 0) 2 - r else r) else cage.z + (if (fx > 0) 2 - c else c)
        return CoordGrid(x, z, cage.level)
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (!s.enraged && npc.hitpoints * PERMILLE < npc.maxHitpoints * ENRAGE_PERMILLE) enrage(npc, s)
    }

    /** EZ3 / OPEN-EZ5: in the finale he takes less damage the lower his health: x(0.5 + HP fraction). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (!s.enraged) return damage
        val factor = HALF + npc.hitpoints.toDouble() / maxOf(1, npc.maxHitpoints)
        return (damage * factor).toInt()
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    /** Capture: the finale's fixed six-volley strike order. */
    private val VOLLEYS = listOf("Blr", "Brl", "rBl", "lBr", "Brl", "Blr")
    private val STAR_BEATS = intArrayOf(3, 5)
    private val COLUMNS = intArrayOf(4059, 4062, 4065)
    private val ROWS = intArrayOf(5531, 5534, 5537)

    /** Capture: the two rows centred on z 5532 and 5536 (south edges 5531 / 5535): his row 5534 stays clear. */
    private val TWO_ROWS = intArrayOf(5531, 5535)
    private val IMPACTS =
        intArrayOf(
            SpotAnims.VFX_EZEKIEL_FINALE_IMPACT_LARGE01,
            SpotAnims.VFX_EZEKIEL_FINALE_IMPACT_MEDIUM01,
            SpotAnims.VFX_EZEKIEL_FINALE_IMPACT_SMALL01,
        )

    /** ezekiel.md data (capture-confirmed): HP for 2..8 players. */
    private val HP = intArrayOf(3000, 4350, 5610, 6870, 8130, 9390, 10650)

    /** The 9x9 arena and his spawn (the middle cell's south-west tile), template coordinates. */
    private const val SPAWN_X = 4062
    private const val SPAWN_Z = 5534
    private const val MIDDLE_X = 4062
    private const val MIDDLE_Z = 5534
    private const val ARENA_MIN_X = 4059
    private const val ARENA_MAX_X = 4067
    private const val ARENA_MIN_Z = 5531
    private const val ARENA_MAX_Z = 5539

    private const val HUNT = 20
    private const val MELEE_SCAN = 6
    private const val RHYTHM = 3
    private const val ATTACK_SPEED = 5
    private const val AFTER_SPECIAL = 7
    private const val UNDER_MAX = 12
    private const val PRAYED_MIN = 1
    private const val PRAYED_MAX = 9
    private const val CLEAVE_MIN = 14
    private const val CLEAVE_MAX = 27
    private const val MAGIC_MIN = 4
    private const val MAGIC_MAX = 21
    private const val MAGIC_DELAY = 2
    private const val STAR_REACH = 4
    private const val STAR_MIN = 20
    private const val STAR_MAX = 34
    private const val LAYOUT_WEIGHT_TOTAL = 42
    private const val CROSS_WEIGHT = 21
    private const val ROWS_WEIGHT = 9
    private const val COLS_WEIGHT = 6
    private const val SWORD_SIZE = 3
    private const val SWORD_FLY = 15
    private const val CHARGE_CAP = 80
    private const val BAND_MIN = 27
    private const val BAND_MAX = 31
    private const val SELF_MIN = 12
    private const val SELF_MAX = 15
    private const val DODGE_ENERGY = 30
    private const val WEAK_NEXT = 12
    private const val WEAK_STILL = 9
    private const val WEAK_RECOVER = 8
    private const val HEAL_DELAY = 4
    private const val HEAL_TENTH = 10
    private const val CELL = 3
    private const val CAGE_AT = 6
    private const val V_SWORDS_AT = 8
    private const val FENCE_SWORDS = 7
    private const val V_SWORDS = 3
    private const val FIRST_VOLLEY = 12
    private const val VOLLEY_FIRST = 4
    private const val VOLLEY_CYCLE = 11
    private const val VOLLEY_MIN = 6
    private const val VOLLEY_MAX = 19
    private const val FINALE_DODGE_ENERGY = 90
    private const val ENRAGE_PERMILLE = 335
    private const val PERMILLE = 1000
    private const val HALF = 0.5
}

/** EZ2 swords: wind spells charge them (capture: the charge is the wind-surge damage, capped at 80). */
internal object TfaEzekielSword : Boss(Npcs.TFA_EZEKIEL_SWORD) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // A sword never attacks; it flies on Ezekiel's schedule.
    }

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val spell = (source as? PlayerRef)?.get(Attr.LAST_SPELL)
        if (style == HitStyle.MAGIC && spell?.startsWith("Wind") == true) npc[TfaEzekiel.CHARGE] = (npc[TfaEzekiel.CHARGE] ?: 0) + damage
        return 0
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }
}
