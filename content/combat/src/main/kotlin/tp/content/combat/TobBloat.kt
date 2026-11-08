package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Pestilent Bloat, ToB room 2 (docs/mechanics/raids/tob/bloat.md BL1-BL8; timings runelite-timings.md and
 * capture-values.md, damage from the wiki since the ToB captures are seasonal, D-080), all three modes.
 * - It walks the ring around the central tank from the moment a raider is in its arena; direction random, no turn for 32
 *   ticks (Hard 16), walking at 60%+ HP, running at 40-60%, alternating per attack below 40%. Damage to it while it walks
 *   is halved.
 * - Flies every tick at each raider it can see (10-20, Protect from Missiles -25%; Entry 8 max) and spread to raiders
 *   who can see a flown raider.
 * - After 38-47 ticks awake it sleeps 32 ticks: the stomp at +30 hits +31 (40-80; Entry 40 max) everyone within 6 of it
 *   it can see, not those under it, and restores drained Defence; it rises at +33.
 * - Falling flesh in waves every 4 or 6 ticks, 16 or 15 shadows on random tiles, splatting 3 ticks later: 30-50 and a
 *   stun to a raider on the tile. Normal: while walking below 90%; Hard: always; Entry: after its first stomp.
 */
internal object TobBloat : Boss(Npcs.TOB_BLOAT, Npcs.TOB_BLOAT_STORY, Npcs.TOB_BLOAT_HARD) {
    /** Its own script moves and targets it: a hit does not make it chase. */
    override fun retaliates(npc: NpcRef): Boolean = false

    private class State(val mode: Int) {
        var started = false
        var awakeUntil = 0L
        var downAt = -1L
        var clockwise = true
        var corner = 0
        var turnCooldown = FIRST_TURN_LOCK
        var noDownUntil = 0L
        var running = false
        var hitThisDown = false
        var stomped = false
        var nextWave = 0L
    }

    private val STATE = AttrKey<State>("tob_bloat")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State(TobModes.of(npc.type)).also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
        loop(npc, state(npc))
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // Flies and stomps run on its own tick script.
        npc.setAttackClock(npc.tick + IDLE)
    }

    /** The raiders inside its 16 x 16 arena (template 3288..3303 x 4440..4455; it spawns at the south-west corner). */
    private fun raiders(npc: NpcRef): List<PlayerRef> =
        TfaKit.arena(npc).filter { it.coord.x - npc.spawn.x in 0 until ARENA && it.coord.z - npc.spawn.z in 0 until ARENA }

    private fun loop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            tick(npc, s)
            loop(npc, s)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        val raiders = raiders(npc)
        if (!s.started) {
            if (raiders.isEmpty()) return
            // BL1: on entry the direction is random; capture: the first sleep 38-47 ticks after the fight starts.
            s.started = true
            s.clockwise = npc.random(2) == 0
            s.awakeUntil = npc.tick + awake(npc, s, first = true)
        }
        if (s.downAt >= 0) {
            down(npc, s, raiders)
            return
        }
        walk(npc, s)
        flies(npc, s, raiders)
        flesh(npc, s, raiders, walking = true)
        if (npc.tick >= s.awakeUntil && npc.tick >= s.noDownUntil) {
            s.downAt = npc.tick
            s.hitThisDown = false
            npc.anim(Seqs.TOB_BLOAT_SLEEP)
        }
    }

    /** Capture: 38-47 ticks awake (the first and every later walk); BL3: 4 longer when it was not attacked while down. */
    private fun awake(npc: NpcRef, s: State, first: Boolean): Int {
        val base = AWAKE_MIN + npc.random(AWAKE_MAX - AWAKE_MIN + 1)
        return if (!first && !s.hitThisDown) base + UNATTACKED_BONUS else base
    }

    private fun down(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        val t = (npc.tick - s.downAt).toInt()
        if (s.mode == TobModes.HARD) flesh(npc, s, raiders, walking = false)
        when (t) {
            STOMP_AT -> {
                npc.anim(Seqs.TOB_BLOAT_READY)
            }

            STOMP_HIT -> {
                stomp(npc, s, raiders)
            }

            RISE_AT -> {
                s.downAt = -1
                s.awakeUntil = npc.tick + awake(npc, s, first = false)
                npc.anim(Seqs.TOB_BLOAT_WALK)
            }
        }
    }

    /** BL4 / capture: the stomp hits raiders within 6 of it that it can see, not those under it; Defence comes back. */
    private fun stomp(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        s.stomped = true
        for (p in raiders) {
            val gap = TfaKit.gap(npc, p)
            if (gap == 0 || gap > STOMP_REACH || !npc.hasLineOfSight(p)) continue
            CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, STOMP_MIN, STOMP_MAX)), HitStyle.TYPELESS)
        }
        npc.setLevel(Stats.DEFENCE, maxOf(npc.level(Stats.DEFENCE), npc.baseLevel(Stats.DEFENCE)))
    }

    /** BL1 / BL5 / BL6: one tile (walking) or two (running) along the ring; a turn now and then once allowed. */
    private fun walk(npc: NpcRef, s: State) {
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        val running =
            when {
                pct >= RUN_FROM -> false
                pct >= ALTERNATE_BELOW -> true
                else -> s.running
            }
        if (running != s.running && pct >= ALTERNATE_BELOW) s.noDownUntil = npc.tick + NO_DOWN_AFTER_CHANGE
        s.running = running
        if (s.turnCooldown > 0) {
            s.turnCooldown--
        } else if (npc.random(TURN_ODDS) == 0) {
            s.clockwise = !s.clockwise
            s.corner = (s.corner + if (s.clockwise) 1 else CORNERS.size - 1) % CORNERS.size
            s.turnCooldown = if (s.mode == TobModes.HARD) HARD_TURN_COOLDOWN else TURN_COOLDOWN
            s.noDownUntil = npc.tick + NO_DOWN_AFTER_CHANGE
        }
        step(npc, s, if (running) 2 else 1)
    }

    /** Moves [tiles] along the ring toward the next corner (turning at corners). */
    private fun step(npc: NpcRef, s: State, tiles: Int) {
        var x = npc.coord.x
        var z = npc.coord.z
        repeat(tiles) {
            val (cx, cz) = CORNERS[s.corner]
            if (x == npc.spawn.x + cx &&
                z == npc.spawn.z + cz
            ) {
                s.corner = (s.corner + if (s.clockwise) 1 else CORNERS.size - 1) % CORNERS.size
            }
            val (nx, nz) = CORNERS[s.corner]
            x += Integer.signum(npc.spawn.x + nx - x)
            z += Integer.signum(npc.spawn.z + nz - z)
        }
        npc.walkTo(CoordGrid(x, z, npc.coord.level))
    }

    /** BL2 / capture: a splat every tick on each raider it sees, landing next tick; the swarm spreads to raiders who see them. */
    private fun flies(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        val seen = raiders.filter { TfaKit.gap(npc, it) > 0 && npc.hasLineOfSight(it) }
        if (seen.isEmpty()) return
        npc.anim(Seqs.TOB_BLOAT_FLIES_SMALL)
        val hit = LinkedHashSet<PlayerRef>(seen)
        for (p in raiders) if (p !in hit && seen.any { it.hasLineOfSight(p) && it.coord != p.coord }) hit += p
        for (p in hit) {
            p.spotAnim(SpotAnims.TOB_BLOAT_FLIES_SMALL)
            val raw =
                if (s.mode ==
                    TobModes.ENTRY
                ) {
                    BossCombat.roll(npc, ENTRY_FLIES_MIN, ENTRY_FLIES_MAX)
                } else {
                    BossCombat.roll(npc, FLIES_MIN, FLIES_MAX)
                }
            val dmg = if (p.protects(CombatStyle.RANGED)) raw * (PERCENT - FLIES_PRAYED_CUT) / PERCENT else raw
            CombatSystem.queueHit(p, npc, dmg, HitStyle.TYPELESS, 1)
        }
    }

    /** BL7 / capture: falling flesh waves (every 4 or 6 ticks, 16 or 15 tiles), splatting 3 ticks later with a stun. */
    private fun flesh(npc: NpcRef, s: State, raiders: List<PlayerRef>, walking: Boolean) {
        val pct = npc.hitpoints * PERCENT / maxOf(1, npc.maxHitpoints)
        val active =
            when (s.mode) {
                TobModes.HARD -> true
                TobModes.ENTRY -> walking && s.stomped
                else -> walking && pct < FLESH_BELOW
            }
        if (!active || npc.tick < s.nextWave || raiders.isEmpty()) return
        // Capture: waves 4 ticks apart 55 times in 83, else 6.
        s.nextWave = npc.tick + if (npc.random(WAVE_ODDS_TOTAL) < WAVE_ODDS_FAST) WAVE_FAST else WAVE_SLOW
        val count = if (npc.random(2) == 0) WAVE_TILES else WAVE_TILES - 1
        val tiles = HashSet<CoordGrid>()
        repeat(count) {
            tiles += CoordGrid(npc.spawn.x + npc.random(ARENA), npc.spawn.z + npc.random(ARENA), npc.spawn.level)
        }
        for ((k, t) in tiles.withIndex()) npc.spotAnimAt(t, FLESH[k % FLESH.size])
        npc.queueHit(FLESH_FALL, strong = false) {
            if (!npc.isInWorld) return@queueHit
            for (t in tiles) npc.spotAnimAt(t, SpotAnims.TOB_BLOAT_BLOOD_SPLAT)
            for (p in raiders(npc)) {
                if (p.coord !in tiles) continue
                CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, FLESH_MIN, FLESH_MAX)), HitStyle.TYPELESS)
                p.spotAnim(SpotAnims.TOB_BLOAT_STUNNED)
                p.frozenUntil = maxOf(p.frozenUntil, p.tick + STUN_TICKS)
            }
        }
    }

    /** BL2: damage to it while it walks is halved (rounded down); BL5: below 40% every attack switches walk / run. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (s.downAt >= 0) {
            s.hitThisDown = true
            return damage
        }
        if (source is PlayerRef && npc.hitpoints * PERCENT < npc.maxHitpoints * ALTERNATE_BELOW) {
            s.running = !s.running
            s.noDownUntil = npc.tick + NO_DOWN_AFTER_CHANGE
        }
        return damage / 2
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.TOB_BLOAT_DEATH)
    }

    /** Its ring: south-west tiles of the four corners from its spawn (the arena's south-west corner), anticlockwise. */
    private val CORNERS = listOf(0 to 0, 11 to 0, 11 to 11, 0 to 11)
    private val FLESH =
        intArrayOf(
            SpotAnims.TOB_BLOAT_FALLING_FLESH1,
            SpotAnims.TOB_BLOAT_FALLING_FLESH2,
            SpotAnims.TOB_BLOAT_FALLING_FLESH3,
            SpotAnims.TOB_BLOAT_FALLING_FLESH4,
        )

    private const val IDLE = 5
    private const val ARENA = 16
    private const val FIRST_TURN_LOCK = 32
    private const val TURN_COOLDOWN = 32
    private const val HARD_TURN_COOLDOWN = 16

    /** OPEN-BL1: the chance it turns on a tick it may. */
    private const val TURN_ODDS = 8
    private const val NO_DOWN_AFTER_CHANGE = 5
    private const val AWAKE_MIN = 38
    private const val AWAKE_MAX = 47
    private const val UNATTACKED_BONUS = 4
    private const val STOMP_AT = 30
    private const val STOMP_HIT = 31
    private const val RISE_AT = 33
    private const val STOMP_REACH = 6
    private const val STOMP_MIN = 40
    private const val STOMP_MAX = 80
    private const val PERCENT = 100
    private const val RUN_FROM = 60
    private const val ALTERNATE_BELOW = 40
    private const val FLIES_MIN = 10
    private const val FLIES_MAX = 20

    /** OPEN-BL9: Entry flies (wiki: 8 max) from half of Normal's minimum. */
    private const val ENTRY_FLIES_MIN = 5
    private const val ENTRY_FLIES_MAX = 8
    private const val FLIES_PRAYED_CUT = 25
    private const val FLESH_BELOW = 90
    private const val WAVE_ODDS_TOTAL = 83
    private const val WAVE_ODDS_FAST = 55
    private const val WAVE_FAST = 4
    private const val WAVE_SLOW = 6
    private const val WAVE_TILES = 16
    private const val FLESH_FALL = 3
    private const val FLESH_MIN = 30
    private const val FLESH_MAX = 50

    /** OPEN-BL8: the stun ("a few seconds"; seq TOB_BLOAT_STUNNED_SHORT). */
    private const val STUN_TICKS = 5
}
