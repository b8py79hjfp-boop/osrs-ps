package tp.content.combat

import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Ocellus P1 (16674; OC1, capture-values.md, runelite-timings.md). From his first step a volley at every raider every 5
 * ticks, one element a walk (fire = ranged, water = magic): prayed 0, else an accuracy roll then 2..24, landing +3
 * within 4 tiles, +4 beyond. He walks corner to corner; at a corner the charge sends 7 crack lines 2 ticks apart from
 * his three inner tiles in turn, each closing 5 ticks after it appears; the smash at C+18 hits anyone within 2 of him at
 * C+19 (28..30). A closing line: each raider on it takes 3..5 (and earns a heal); unless every raider stands on it, it
 * adds 2 to the meter (of 96) and heals him 2. At 66% and 25% (checked on volley ticks) he goes to the middle: four beats
 * 8 ticks apart of two diagonals each (6 and 8 ticks), then the strike: at meter 0..10 "Death... awaits...", one shot at
 * each raider of 17..19, landing +1 (within 4) or +2.
 */
internal object TfaOcellusP1 {
    private enum class Mode { WALK, CHARGE, MIDDLE }

    fun start(npc: NpcRef, s: OcellusState) {
        npc.huntRange = 0
        npc.stopInteraction()
        val start = npc.tick
        var mode = Mode.WALK
        var since = start
        var walkTo = corner(s, s.corner)
        beginWalk(npc, s, walkTo)
        TfaOcellus.loop(npc, s) {
            val t = (npc.tick - start).toInt()
            val volleyTick = t % VOLLEY_EVERY == 0
            if (volleyTick && mode != Mode.MIDDLE) {
                val pct = npc.hitpoints * TfaOcellus.PERCENT / maxOf(1, npc.maxHitpoints)
                if (s.middles < MIDDLE_AT.size && pct <= MIDDLE_AT[s.middles]) {
                    s.middles++
                    mode = Mode.MIDDLE
                    since = npc.tick
                    middle(npc, s)
                    return@loop
                }
                volley(npc, s)
            }
            val elapsed = (npc.tick - since).toInt()
            when (mode) {
                Mode.WALK -> {
                    if (npc.coord == walkTo || elapsed >= WALK_LIMIT) {
                        if (npc.coord != walkTo) npc.teleport(walkTo)
                        mode = Mode.CHARGE
                        since = npc.tick
                        charge(npc, s)
                    } else {
                        npc.walkTo(walkTo)
                    }
                }

                Mode.CHARGE -> {
                    if (elapsed >= CHARGE_LENGTH) {
                        s.corner++
                        walkTo = corner(s, s.corner)
                        mode = Mode.WALK
                        since = npc.tick
                        beginWalk(npc, s, walkTo)
                    }
                }

                Mode.MIDDLE -> {
                    if (elapsed >= MIDDLE_LENGTH) {
                        walkTo = corner(s, s.corner)
                        mode = Mode.WALK
                        since = npc.tick
                        beginWalk(npc, s, walkTo)
                    }
                }
            }
        }
    }

    /** Capture: one element a walk (it changed 14 times in 65 walks: a fresh pick each walk). */
    private fun beginWalk(npc: NpcRef, s: OcellusState, to: CoordGrid) {
        s.magicWalk = npc.random(2) == 0
        npc.walkTo(to)
    }

    private fun corner(s: OcellusState, k: Int): CoordGrid {
        val (dx, dz) = CORNERS[k % CORNERS.size]
        return CoordGrid(s.centre.x + dx, s.centre.z + dz, s.centre.level)
    }

    /** RuneLite (LU FinalBoss.java:405-410) / capture: water (magic, 4228/4229) or fire (ranged, 4231/4232) shots at everyone. */
    private fun volley(npc: NpcRef, s: OcellusState) {
        val magic = s.magicWalk
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        npc.spotAnim(if (magic) SpotAnims.VFX_OCELLUS_WATER_ATTACK01_SPOTANIM else SpotAnims.VFX_OCELLUS_FIRE_ATTACK01_SPOTANIM)
        for (p in TfaKit.arena(npc)) {
            val delay = if (TfaKit.gap(npc, p) <= NEAR) NEAR_FLIGHT else FAR_FLIGHT
            BossCombat.projectile(npc, p, SpotAnims.TFA_OCELLUS_SF_PROJ, delay)
            val dmg =
                when {
                    p.protects(style) -> 0
                    !BossCombat.rolls(npc, p, if (magic) AttackType.MAGIC else AttackType.RANGED) -> 0
                    else -> BossCombat.roll(npc, VOLLEY_MIN, VOLLEY_MAX)
                }
            CombatSystem.queueHit(p, npc, dmg, if (magic) HitStyle.MAGIC else HitStyle.RANGED, delay)
        }
    }

    /** Capture: 7 crack lines every 2 ticks from his three inner tiles in turn; the smash C+18, its hit C+19. */
    private fun charge(npc: NpcRef, s: OcellusState) {
        npc.anim(Seqs.NPC_OCELLUS_PHASE1_CHARGE)
        val sx = Integer.signum(s.centre.x - npc.coord.x - 1)
        val sz = Integer.signum(s.centre.z - npc.coord.z - 1)
        val mx = npc.coord.x + 1
        val mz = npc.coord.z + 1
        val inner =
            listOf(
                CoordGrid(mx + sx, mz + sz, npc.coord.level),
                CoordGrid(mx + sx, mz, npc.coord.level),
                CoordGrid(mx, mz + sz, npc.coord.level),
            )
        for (k in 0 until CORNER_LINES) {
            npc.queueHit(k * LINE_GAP, strong = false) {
                if (!npc.isInWorld || s.phase != TfaOcellus.P1) return@queueHit
                val from = inner[k % inner.size]
                val players = TfaKit.arena(npc)
                val aim = players.getOrNull(npc.random(maxOf(1, players.size)))?.coord ?: s.centre
                crackLine(npc, s, from, aim, CORNER_FUSE)
            }
        }
        npc.queueHit(SMASH_AT, strong = false) {
            if (!npc.isInWorld || s.phase != TfaOcellus.P1) return@queueHit
            npc.anim(Seqs.NPC_OCELLUS_PHASE1_EXPEND)
            val near = TfaKit.arena(npc).filter { TfaKit.gap(npc, it) <= SMASH_RANGE }
            for (p in near) CombatSystem.queueHit(p, npc, BossCombat.roll(npc, SMASH_MIN, SMASH_MAX), HitStyle.MELEE, 1)
        }
    }

    /** A line from [from] through [aim] out to [LINE_LENGTH] tiles, closing after [fuse] ticks. */
    private fun crackLine(npc: NpcRef, s: OcellusState, from: CoordGrid, aim: CoordGrid, fuse: Int) {
        val dx = aim.x - from.x
        val dz = aim.z - from.z
        val d = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz), 1)
        val end = CoordGrid(from.x + dx * LINE_LENGTH / d, from.z + dz * LINE_LENGTH / d, from.level)
        lineAlong(npc, s, TfaKit.line(from, end), fuse)
    }

    private fun lineAlong(npc: NpcRef, s: OcellusState, tiles: List<CoordGrid>, fuse: Int) {
        for ((i, t) in tiles.withIndex()) if (i % CRACK_SPACING == 0) npc.spotAnimAt(t, SpotAnims.VFX_OCELLUS_P1_FLOOR_CRACK_01)
        npc.queueHit(fuse, strong = false) {
            if (!npc.isInWorld || s.phase != TfaOcellus.P1) return@queueHit
            val players = TfaKit.arena(npc)
            val on = players.filter { TfaKit.on(it, tiles) }
            for (p in on) {
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, LINE_HIT_MIN, LINE_HIT_MAX), HitStyle.TYPELESS)
                p[TfaOcellus.LINES_STOOD] = (p[TfaOcellus.LINES_STOOD] ?: 0) + 1
            }
            if (on.size < players.size || players.isEmpty()) {
                s.meter = minOf(METER_MAX, s.meter + METER_STEP)
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + LINE_SELF_HEAL)
            }
        }
    }

    /** RuneLite (LU FinalBoss.java:415-434) / capture: the middle: four beats 8 ticks apart, two diagonals each, then the strike. */
    private fun middle(npc: NpcRef, s: OcellusState) {
        npc.teleport(CoordGrid(s.centre.x - 1, s.centre.z - 1, s.centre.level))
        npc.anim(Seqs.NPC_OCELLUS_PHASE1_TELEPORT_IN)
        for (k in 0 until MIDDLE_BEATS) {
            npc.queueHit(1 + k * BEAT_GAP, strong = false) {
                if (!npc.isInWorld || s.phase != TfaOcellus.P1) return@queueHit
                npc.anim(Seqs.NPC_OCELLUS_PHASE1_CHARGE)
                val (a, b) = DIAGONAL_PAIRS[k % DIAGONAL_PAIRS.size]
                diagonal(npc, s, a, FIRST_FUSE)
                diagonal(npc, s, b, SECOND_FUSE)
            }
        }
        npc.queueHit(STRIKE_AT, strong = false) {
            if (!npc.isInWorld || s.phase != TfaOcellus.P1) return@queueHit
            npc.anim(Seqs.NPC_OCELLUS_PHASE1_EXPEND)
            strike(npc, s)
        }
    }

    private fun diagonal(npc: NpcRef, s: OcellusState, dir: Pair<Int, Int>, fuse: Int) {
        val from = CoordGrid(s.centre.x + dir.first * 2, s.centre.z + dir.second * 2, s.centre.level)
        val to = CoordGrid(s.centre.x + dir.first * LINE_LENGTH, s.centre.z + dir.second * LINE_LENGTH, s.centre.level)
        lineAlong(npc, s, TfaKit.line(from, to), fuse)
    }

    /**
     * Capture: at meter 0..10 of 96 "Death... awaits...": one shot each of 17..19. Above that "Embrace... annihilation..."
     * was never recorded (OPEN-OC11: 80, the wiki's "80+").
     */
    private fun strike(npc: NpcRef, s: OcellusState) {
        val death = s.meter <= DEATH_METER
        npc.say(if (death) "Death... awaits..." else "Embrace... annihilation...")
        for (p in TfaKit.arena(npc)) {
            val delay = if (TfaKit.gap(npc, p) <= NEAR) 1 else 2
            val dmg = if (death) BossCombat.roll(npc, DEATH_MIN, DEATH_MAX) else ANNIHILATION
            CombatSystem.queueHit(p, npc, dmg, HitStyle.TYPELESS, delay)
        }
        s.meter = 0
    }

    private val CORNERS = listOf(-8 to -8, 8 to -8, 8 to 8, -8 to 8)
    private val DIAGONALS = listOf(1 to 1, 1 to -1, -1 to -1, -1 to 1)
    private val DIAGONAL_PAIRS =
        listOf(
            DIAGONALS[0] to DIAGONALS[1],
            DIAGONALS[1] to DIAGONALS[2],
            DIAGONALS[2] to DIAGONALS[3],
            DIAGONALS[3] to DIAGONALS[0],
        )
    private val MIDDLE_AT = intArrayOf(66, 25)

    private const val VOLLEY_EVERY = 5
    private const val NEAR = 4
    private const val NEAR_FLIGHT = 3
    private const val FAR_FLIGHT = 4
    private const val VOLLEY_MIN = 2
    private const val VOLLEY_MAX = 24

    /** OPEN-OC3: the longest walk between corners before he is placed there. */
    private const val WALK_LIMIT = 16
    private const val CORNER_LINES = 7
    private const val LINE_GAP = 2
    private const val CORNER_FUSE = 5
    private const val SMASH_AT = 18
    private const val CHARGE_LENGTH = 20
    private const val SMASH_RANGE = 2
    private const val SMASH_MIN = 28
    private const val SMASH_MAX = 30

    /** OPEN-OC3: how far a crack line runs. */
    private const val LINE_LENGTH = 14
    private const val CRACK_SPACING = 2
    private const val LINE_HIT_MIN = 3
    private const val LINE_HIT_MAX = 5
    private const val METER_STEP = 2
    private const val METER_MAX = 96
    private const val LINE_SELF_HEAL = 2
    private const val MIDDLE_BEATS = 4
    private const val BEAT_GAP = 8
    private const val FIRST_FUSE = 6
    private const val SECOND_FUSE = 8
    private const val STRIKE_AT = 1 + (MIDDLE_BEATS - 1) * BEAT_GAP + SECOND_FUSE + 1
    private const val MIDDLE_LENGTH = STRIKE_AT + 3
    private const val DEATH_METER = 10
    private const val DEATH_MIN = 17
    private const val DEATH_MAX = 19
    private const val ANNIHILATION = 80
}
