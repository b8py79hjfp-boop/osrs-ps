package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Zebak, the Path of Crondis boss (docs/mechanics/raids/toa/zebak.md ZB1-ZB18). He lies at the west end of the arena
 * (plane 1, an oval x 3923-3944, z 5398-5418; cache collision) and does not move.
 * - ZB2: one attack per cycle: speed 7 (enraged 4; path level 2-3: 6 / 3, OPEN-ZB4; 4+: 5 / 2). Melee on a player in
 *   reach, otherwise ranged or magic (OPEN-ZB5: even odds).
 * - ZB3: melee 38 base on everyone in the wider ring, one tick after the animation; Protect from Melee halves it
 *   (OPEN-ZB6); a chance of bleed.
 * - ZB4 / ZB5: magic (pot, red orbs) and ranged (rock, fragments), 16 base, damage 2 ticks after the burst; the
 *   prayer counts at impact (flicking works).
 * - ZB6-ZB10: at 85 / 70 / 55 / 40% a special (roar, waves, roar, waves; OPEN-ZB2) with poison and jugs each time;
 *   the roar after four of his attacks (three hits, a rock shields up to three tiles behind it and takes 50); waves
 *   sweep from the south first, then the north, and push a player hit.
 * - ZB12-ZB14 (Not Just a Head): blood blitz (8-11, heals ~66%) or blood clouds; Arterial Spray widens and heals more;
 *   Blood Thinners three thinned clouds. ZB15 Upset Stomach: acid 5x5, water 3x3. ZB16: enraged below 25%, no more
 *   specials. ZB17: Defence drain stops at 50.
 */
internal object ToaZebak : Boss(Npcs.TOA_ZEBAK, Npcs.TOA_ZEBAK_ENRAGED) {
    private enum class Special { ROAR, WAVES }

    private class State {
        var specials = 0
        var roarIn = -1
        var waveTicks = -1
        var waveFromSouth = true
        var waveRow = 0
        var bloodIn = BLOOD_EVERY
        val pools = HashMap<CoordGrid, Int>()
        val bleeding = HashMap<PlayerRef, Int>()
    }

    private val STATE = AttrKey<State>("toa_zebak")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.TOA_ZEBAK || npc[STATE] != null) return
        val s = state(npc)
        npc.huntRange = HUNT
        // ZB1: he lies at the arena's end and never moves.
        npc.frozenUntil = Long.MAX_VALUE
        // ZB1: the tail NPC lies with him (11731).
        Bosses.summon(npc, Npcs.TOA_ZEBAK_TAIL, npc.coord, huntRange = 0)
        ToaKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        // ZB17: drain stops 20 below base (70 -> 50).
        val floor = npc.baseLevel(NpcStat.DEFENCE) - DEFENCE_DRAIN_CAP
        if (npc.level(NpcStat.DEFENCE) < floor) npc.setLevel(NpcStat.DEFENCE, floor)
        val permille = npc.hitpoints * PER_MILLE / maxOf(1, npc.maxHitpoints)
        // ZB16: below 25% enraged, no more specials (a queued one is dropped).
        if (npc.type == Npcs.TOA_ZEBAK && permille < ENRAGE) {
            npc.transform(Npcs.TOA_ZEBAK_ENRAGED, restoreStats = false)
            s.roarIn = -1
            s.waveTicks = -1
            return
        }
        if (npc.type != Npcs.TOA_ZEBAK) return
        if (s.specials < THRESHOLDS.size && permille <= THRESHOLDS[s.specials]) {
            special(npc, s, if (s.specials % 2 == 0) Special.ROAR else Special.WAVES)
            s.specials++
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        val enraged = npc.type == Npcs.TOA_ZEBAK_ENRAGED
        npc.setAttackClock(npc.tick + speed(npc, enraged))
        if (s.roarIn > 0 && --s.roarIn == 0) {
            roar(npc)
            return
        }
        if (ToaKit.has(npc, NOT_JUST_A_HEAD) && --s.bloodIn <= 0) {
            s.bloodIn = BLOOD_EVERY
            if (npc.random(2) == 0) bloodBlitz(npc) else bloodClouds(npc)
            return
        }
        val inReach = ToaKit.raiders(npc).filter { reach(npc, it) <= MELEE_REACH }
        if (inReach.isNotEmpty()) {
            melee(npc, enraged)
            return
        }
        if (npc.random(2) ==
            0
        ) {
            projectile(npc, victim, magic = true, enraged = enraged)
        } else {
            projectile(npc, victim, magic = false, enraged = enraged)
        }
    }

    /** ZB2 / ZB16 / OPEN-ZB4: 7 / 4; path level 2-3: 6 / 3; 4+: 5 / 2. */
    private fun speed(npc: NpcRef, enraged: Boolean): Int {
        val step = ToaKit.step(npc)
        return (if (enraged) ENRAGED_SPEED else SPEED) - step
    }

    /** The chebyshev gap between a player and his 9x9 body. */
    private fun reach(npc: NpcRef, p: PlayerRef): Int {
        val dx = maxOf(npc.coord.x - p.coord.x, p.coord.x - (npc.coord.x + SIZE - 1), 0)
        val dz = maxOf(npc.coord.z - p.coord.z, p.coord.z - (npc.coord.z + SIZE - 1), 0)
        return maxOf(dx, dz)
    }

    /** ZB3: every player in the wider ring, one tick later; Protect from Melee halves (OPEN-ZB6); bleed chance. */
    private fun melee(npc: NpcRef, enraged: Boolean) {
        npc.anim(if (enraged) Seqs.NPC_ZEBAK01_ATTACK_MELEE_ENRAGED else Seqs.NPC_ZEBAK01_ATTACK_MELEE)
        for (p in ToaKit.raiders(npc)) {
            if (reach(npc, p) > MELEE_RING) continue
            ToaKit.strike(npc, p, AttackType.CRUSH, ToaKit.dmg(npc, MELEE_MAX), MELEE_DELAY, rule = PrayerRule.HALVES) { dealt ->
                if (dealt > 0 && npc.random(BLEED_ODDS) == 0) state(npc).bleeding[p] = BLEED_TICKS
            }
        }
    }

    /** ZB4 / ZB5: the pot or rock, then its burst; damage 2 ticks after the burst, prayer read at impact. */
    private fun projectile(npc: NpcRef, victim: EntityRef, magic: Boolean, enraged: Boolean) {
        npc.anim(if (enraged) Seqs.NPC_ZEBAK01_ATTACK_RANGED_ENRAGED else Seqs.NPC_ZEBAK01_ATTACK_RANGED)
        val first = if (magic) (if (enraged) MAGE_ENRAGED else MAGE) else (if (enraged) RANGE_ENRAGED else RANGE)
        BossCombat.projectile(npc, victim, first, LAUNCH)
        val type = if (magic) AttackType.MAGIC else AttackType.RANGED
        val hit = BossCombat.rolls(npc, victim, type)
        val raw = if (hit) BossCombat.roll(npc, 1, ToaKit.dmg(npc, RANGED_MAX)) else 0
        npc.queueHit(LAUNCH, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            victim.spotAnim(if (magic) MAGE_BURST else RANGE_BURST)
            BossCombat.projectile(npc, victim, if (magic) MAGE_SPLIT else RANGE_SPLIT, BURST_TO_HIT)
            npc.queueHit(BURST_TO_HIT, strong = false) {
                val damage = ToaKit.afterPrayer(npc, victim, type.style, raw)
                CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(type.style), 0)
            }
        }
    }

    // ---- specials

    private fun special(npc: NpcRef, s: State, which: Special) {
        npc.anim(Seqs.NPC_ZEBAK01_ATTACK_SPECIAL)
        poison(npc, s)
        jugs(npc)
        when (which) {
            Special.ROAR -> {
                rocks(npc)
                s.roarIn = ROAR_AFTER
            }

            Special.WAVES -> {
                s.waveTicks = WAVE_DELAY
                s.waveRow = 0
            }
        }
    }

    /** ZB7: poison splashes on a random tile and may splash a 3x3 (5x5 with Upset Stomach) around it. */
    private fun poison(npc: NpcRef, s: State) {
        val r = if (ToaKit.has(npc, UPSET_STOMACH)) 2 else 1
        repeat(POISON_SPITS) {
            val centre = arenaTile(npc)
            for (dx in -r..r) {
                for (dz in -r..r) {
                    val t = CoordGrid(centre.x + dx, centre.z + dz, centre.level)
                    if (!npc.isWalkable(t) || t in s.pools) continue
                    npc.addLoc(POOLS[npc.random(POOLS.size)], t, GROUND_DECOR, 0)
                    s.pools[t] = 0
                }
            }
        }
    }

    /** ZB8: jugs (OPEN-ZB8: four, "two extra" since 1 Sep 2022 from two) on random arena tiles. */
    private fun jugs(npc: NpcRef) {
        repeat(JUGS) { Bosses.summon(npc, Npcs.TOA_ZEBAK_JUG, arenaTile(npc), huntRange = 0, inheritRun = true) }
    }

    /** ZB9: two or three rocks (150 HP) in front of him. */
    private fun rocks(npc: NpcRef) {
        for (z in ROCK_ROWS) {
            val t = ToaKit.at(npc, CoordGrid(ROCK_X, z, ARENA_LEVEL))
            Bosses.summon(npc, Npcs.TOA_ZEBAK_SAFESPOT, t, huntRange = 0, inheritRun = true)
        }
    }

    /** ZB9: three roar hits; a rock shields up to three tiles behind it (east) and takes 50 a hit (OPEN-ZB9). */
    private fun roar(npc: NpcRef) {
        npc.anim(Seqs.NPC_ZEBAK01_ATTACK_ROAR)
        val rocks = Bosses.minions(npc, Npcs.TOA_ZEBAK_SAFESPOT)
        repeat(ROAR_HITS) { i ->
            npc.queueHit(i * ROAR_GAP, strong = false) {
                if (!npc.isInWorld || npc.isDying) return@queueHit
                for (p in ToaKit.raiders(npc)) {
                    val shield = rocks.firstOrNull { it.isInWorld && p.coord.z == it.coord.z && p.coord.x - it.coord.x in 1..ROCK_SHADOW }
                    if (shield != null) {
                        CombatSystem.queueHit(shield, npc, ROCK_TAKES, HitStyle.TYPELESS, 0)
                        continue
                    }
                    p.spotAnim(ROAR_DUST)
                    ToaKit.hitRolled(npc, p, ROAR_MAX)
                    p.teleport(
                        CoordGrid(minOf(p.coord.x + ROAR_KNOCK, ToaKit.atX(npc, ARENA_MAX_X)), p.coord.z, p.coord.level),
                    )
                }
            }
        }
    }

    /**
     * ZB10: after the tail slam a wave line sweeps the arena row by row (one row a tick, OPEN-ZB10) with a gap of
     * [WAVE_GAP] tiles (one tile tighter per two path levels); a player hit takes 6-10 and is pushed 3 tiles on.
     */
    private fun wave(npc: NpcRef, s: State) {
        if (s.waveTicks < 0) return
        if (s.waveTicks > 0) {
            if (s.waveTicks == WAVE_DELAY) npc.anim(Seqs.NPC_ZEBAK01_ATTACK_TAIL)
            s.waveTicks--
            return
        }
        val rows = ARENA_MAX_Z - ARENA_MIN_Z + 1
        val z = if (s.waveFromSouth) ARENA_MIN_Z + s.waveRow else ARENA_MAX_Z - s.waveRow
        val dir = if (s.waveFromSouth) 1 else -1
        val gap = (WAVE_GAP - ToaKit.step(npc)).coerceAtLeast(1)
        val gapStart = ARENA_MIN_X + (npc.tick % (ARENA_MAX_X - ARENA_MIN_X - gap)).toInt()
        for (p in ToaKit.raiders(npc)) {
            val t = ToaKit.template(npc, p.coord)
            if (t.z != z || t.x in gapStart until gapStart + gap) continue
            ToaKit.areaHit(npc, p, BossCombat.roll(npc, WAVE_MIN, ToaKit.dmg(npc, WAVE_MAX)))
            val to = CoordGrid(p.coord.x, p.coord.z + dir * WAVE_PUSH, p.coord.level)
            val tt = ToaKit.template(npc, to)
            // Pushed off the platform: into the water below (ZB10); the rock steps lead back up.
            p.teleport(if (tt.z !in ARENA_MIN_Z..ARENA_MAX_Z) CoordGrid(to.x, to.z, 0) else to)
        }
        for (pool in s.pools.keys.filter { ToaKit.template(npc, it).z == z && npc.random(2) == 0 }) clearPool(npc, s, pool)
        if (++s.waveRow >= rows) {
            s.waveTicks = -1
            s.waveFromSouth = !s.waveFromSouth
        }
    }

    /** ZB12 (a): blood blitz on everyone, 8-11 base (more with others close), heals ~66% (OPEN-ZB11); Protect from Magic blocks. */
    private fun bloodBlitz(npc: NpcRef) {
        val spray = ToaKit.has(npc, ARTERIAL_SPRAY)
        val raiders = ToaKit.raiders(npc)
        for (p in raiders) {
            val near = raiders.count { it != p && it.distanceTo(p) <= if (spray) SPRAY_RADIUS else BLITZ_RADIUS }
            val raw = BossCombat.roll(npc, BLITZ_MIN, ToaKit.dmg(npc, BLITZ_MAX)) + near * BLITZ_PER_NEIGHBOUR
            val damage = ToaKit.afterPrayer(npc, p, CombatStyle.MAGIC, raw)
            CombatSystem.queueHit(p, npc, damage, HitStyle.MAGIC, BLITZ_DELAY) {
                if (damage >
                    0
                ) {
                    npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + damage * (if (spray) SPRAY_HEAL else BLITZ_HEAL) / PERCENT)
                }
            }
        }
    }

    /** ZB12 (b) / ZB13: one blood cloud by a ramp (three thinned ones with Blood Thinners). */
    private fun bloodClouds(npc: NpcRef) {
        val thin = ToaKit.has(npc, BLOOD_THINNERS)
        repeat(if (thin) THIN_CLOUDS else 1) { i ->
            val ramp = RAMPS[(i + npc.random(2)) % RAMPS.size]
            Bosses.summon(
                npc,
                if (thin) Npcs.TOA_ZEBAK_BLOOD_CLOUD_SMALL else Npcs.TOA_ZEBAK_BLOOD_CLOUD,
                ToaKit.at(npc, ramp),
                huntRange = HUNT,
                inheritRun = true,
            )
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        // The Swimmer: ticks a player spends in the water (plane 0 below the platform).
        for (p in ToaKit.raiders(npc)) if (p.coord.level == 0) ToaKit.signal(npc, p, "toa.zebak.water_tick")
        wave(npc, s)
        pools(npc, s)
        bleed(npc, s)
        clouds(npc)
        jugsBreak(npc, s)
    }

    /** ZB7: a pool hurts from its second tick, low then quickly spiking (OPEN-ZB7: doubling up to 20), and poisons (2). */
    private fun pools(npc: NpcRef, s: State) {
        for ((t, age) in s.pools.entries.toList()) s.pools[t] = age + 1
        for (p in ToaKit.raiders(npc)) {
            val age = s.pools[p.coord] ?: continue
            if (age <= 1) continue
            val stood = (p[STOOD] ?: 0) + 1
            p[STOOD] = stood
            ToaKit.areaHit(npc, p, minOf(POOL_MAX, 1 shl minOf(stood - 1, POOL_DOUBLINGS)))
            Poison.poison(p, POISON_DAMAGE)
        }
        for (p in ToaKit.raiders(npc)) if (p.coord !in s.pools) p[STOOD] = null
    }

    private val STOOD = AttrKey<Int>("toa_zebak_pool_ticks")

    /** ZB3: the bleed (OPEN-ZB6: 1 a tick, 2 while moving, for 10 ticks). */
    private fun bleed(npc: NpcRef, s: State) {
        for ((p, left) in s.bleeding.entries.toList()) {
            if (left <= 0 || p.isDying) {
                s.bleeding.remove(p)
                continue
            }
            s.bleeding[p] = left - 1
            ToaKit.areaHit(npc, p, if (p.isMoving) BLEED_MOVING else BLEED_STILL)
        }
    }

    /** ZB13: clouds lose 2 HP a tick, siphon a player they touch (OPEN-ZB11: 2 a tick, healing the cloud). */
    private fun clouds(npc: NpcRef) {
        for (c in Bosses.minions(npc)) {
            if (c.type != Npcs.TOA_ZEBAK_BLOOD_CLOUD && c.type != Npcs.TOA_ZEBAK_BLOOD_CLOUD_SMALL) continue
            c.hitpoints -= CLOUD_DECAY
            if (c.hitpoints <= 0) {
                c.despawn()
                continue
            }
            for (p in ToaKit.raiders(npc)) {
                if (p.distanceTo(c) > 0) continue
                ToaKit.areaHit(npc, p, CLOUD_SIPHON)
                c.hitpoints = minOf(c.maxHitpoints, c.hitpoints + CLOUD_SIPHON)
            }
        }
    }

    /** ZB8: a jug that died (attacked) or rolled into a rock breaks: water clears 5x5 (3x3 with Upset Stomach). */
    private fun jugsBreak(npc: NpcRef, s: State) {
        val rocks = Bosses.minions(npc, Npcs.TOA_ZEBAK_SAFESPOT)
        for (j in Bosses.minions(npc)) {
            if (j.type != Npcs.TOA_ZEBAK_JUG && j.type != Npcs.TOA_ZEBAK_JUG_ROLLING) continue
            val atRock = rocks.any { it.distanceTo(j) <= 1 }
            if (!atRock) continue
            breakJug(npc, s, j, despawn = true)
        }
    }

    private fun breakJug(owner: NpcRef, s: State, jug: NpcRef, despawn: Boolean) {
        val r = if (ToaKit.has(owner, UPSET_STOMACH)) 1 else 2
        owner.spotAnimAt(jug.coord, JUG_BREAK)
        for (t in s.pools.keys.toList()) {
            if (kotlin.math.abs(t.x - jug.coord.x) <= r && kotlin.math.abs(t.z - jug.coord.z) <= r) clearPool(owner, s, t)
        }
        if (despawn) jug.despawn()
    }

    private fun clearPool(npc: NpcRef, s: State, t: CoordGrid) {
        s.pools.remove(t)
        npc.revertLoc(t, GROUND_DECOR)
    }

    /**
     * ZB8: Push / Pull (ops 1 / 3) roll a jug two tiles straight or diagonally away from / towards the player; Hit (op4)
     * breaks it (the player's own attack on a jug kills it too, 5 HP).
     */
    fun moveJug(player: PlayerRef, jug: NpcRef, pull: Boolean) {
        val dx = Integer.signum(jug.coord.x - player.coord.x) * if (pull) -1 else 1
        val dz = Integer.signum(jug.coord.z - player.coord.z) * if (pull) -1 else 1
        val to = CoordGrid(jug.coord.x + dx * JUG_ROLL, jug.coord.z + dz * JUG_ROLL, jug.coord.level)
        jug.walkTo(if (jug.isWalkable(to)) to else jug.coord)
    }

    /** A jug killed by an attack (5 HP) or the "Hit" op: its water clears the pools around it. */
    fun hitJug(jug: NpcRef) {
        val owner = jug[Bosses.OWNER] ?: return
        breakJug(owner, state(owner), jug, despawn = false)
    }

    /** ZB10: "Climb-up" on the rock steps 45509 out of the water: back onto the platform by that ramp. */
    fun climb(player: PlayerRef, steps: CoordGrid) {
        val zebak = player.npcsWithin(steps, CLIMB_SCAN).firstOrNull { it.type in ids } ?: return
        val t = ToaKit.template(zebak, steps)
        val up = if (t.z < (ARENA_MIN_Z + ARENA_MAX_Z) / 2) ARENA_MIN_Z + 1 else ARENA_MAX_Z - 1
        player.teleport(ToaKit.at(zebak, CoordGrid(t.x, up, ARENA_LEVEL)))
    }

    private fun arenaTile(npc: NpcRef): CoordGrid =
        BossCombat.randomTile(
            npc,
            ToaKit.at(npc, CoordGrid((ARENA_MIN_X + ARENA_MAX_X) / 2, (ARENA_MIN_Z + ARENA_MAX_Z) / 2, ARENA_LEVEL)),
            ARENA_RADIUS,
        )

    /** ZB18: Crondis' capture (if not read) and the Fang to the top damage dealer. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.NPC_ZEBAK01_DEATH)
        Bosses.dismissMinions(npc)
        val top = Bosses.topDamager(npc) ?: return
        ToaKit.giveOrDrop(top, Objs.TOA_ZEBAK_FANG, "zebak drop")
        if (top.varbit(Varbits.TOA_BOOK_ZEBAK) == 0) ToaKit.giveOrDrop(top, Objs.TOA_BOOK_ZEBAK, "zebak drop")
    }

    // ---- data (template: region 15700, base (3904, 5376); the arena oval on plane 1 from the cache collision)

    private const val SIZE = 9
    private const val ARENA_LEVEL = 1
    private const val ARENA_MIN_X = 3923
    private const val ARENA_MAX_X = 3944
    private const val ARENA_MIN_Z = 5398
    private const val ARENA_MAX_Z = 5418
    private const val ARENA_RADIUS = 9

    /** ZB13: clouds start "by the ramps" (the rock steps 45509 at (3933, 5397) and (3933, 5419)). */
    private val RAMPS = listOf(CoordGrid(3933, 5399, ARENA_LEVEL), CoordGrid(3933, 5417, ARENA_LEVEL))

    /** ZB2 / ZB16: speeds 7 / 4 (wiki), path steps -1 each (OPEN-ZB4). */
    private const val SPEED = 7
    private const val ENRAGED_SPEED = 4

    /** ZB3: melee reach 1 ("red tiles"), damage ring 2 ("yellow tiles", OPEN-ZB3), one tick, 38 base. */
    private const val MELEE_REACH = 1
    private const val MELEE_RING = 2
    private const val MELEE_DELAY = 1
    private const val MELEE_MAX = 38

    /** OPEN-ZB6: bleed 1 in 4, 10 ticks, 1 a tick (2 moving). */
    private const val BLEED_ODDS = 4
    private const val BLEED_TICKS = 10
    private const val BLEED_STILL = 1
    private const val BLEED_MOVING = 2

    /** ZB4 / ZB5: 16 base; OPEN-ZB5 launch-to-burst 3 ticks; damage 2 ticks after the burst (CA-Praise). */
    private const val RANGED_MAX = 16
    private const val LAUNCH = 3
    private const val BURST_TO_HIT = 2
    private const val MAGE = 2176
    private const val MAGE_ENRAGED = 2177
    private const val RANGE = 2178
    private const val RANGE_ENRAGED = 2179
    private const val MAGE_BURST = 2186
    private const val RANGE_BURST = 2185
    private const val MAGE_SPLIT = 2181
    private const val RANGE_SPLIT = 2187

    /** ZB6: 85 / 70 / 55 / 40%; ZB16 enrage below 25%. */
    private val THRESHOLDS = intArrayOf(850, 700, 550, 400)
    private const val ENRAGE = 250
    private const val PER_MILLE = 1000

    /** OPEN-ZB7: two spits a special; pool damage doubles from 1 to 20; poison 2 (Strat). */
    private const val POISON_SPITS = 2
    private val POOLS = (Locs.TOA_ZEBAK_VOMIT01..Locs.TOA_ZEBAK_VOMIT07).toList()
    private const val POOL_MAX = 20
    private const val POOL_DOUBLINGS = 5
    private const val POISON_DAMAGE = 2
    private const val GROUND_DECOR = 22

    private const val JUGS = 4
    private const val JUG_ROLL = 2
    private const val JUG_BREAK = 2192

    /** ZB9: three rocks at x 3927 in front of him (OPEN-ZB9 placement), roar after four of his attacks, three hits. */
    private const val ROCK_X = 3927
    private val ROCK_ROWS = intArrayOf(5403, 5408, 5413)
    private const val ROAR_AFTER = 4
    private const val ROAR_HITS = 3
    private const val ROAR_GAP = 2
    private const val ROCK_SHADOW = 3
    private const val ROCK_TAKES = 50
    private const val ROAR_MAX = 35
    private const val ROAR_KNOCK = 2
    private const val ROAR_DUST = 2184

    /** ZB10: 6-10 base, push 3 tiles (MT), gap 3 tiles (OPEN-ZB10), first wave 3 ticks after the slam. */
    private const val WAVE_MIN = 6
    private const val WAVE_MAX = 10
    private const val WAVE_PUSH = 3
    private const val WAVE_GAP = 3
    private const val WAVE_DELAY = 3

    /** ZB12-ZB14 (OPEN-ZB11): a blood spell every 6th attack; 8-11; +3 per neighbour within 1 (2 with Arterial Spray); heal 66% (90%). */
    private const val BLOOD_EVERY = 6
    private const val BLITZ_MIN = 8
    private const val BLITZ_MAX = 11
    private const val BLITZ_RADIUS = 1
    private const val SPRAY_RADIUS = 2
    private const val BLITZ_PER_NEIGHBOUR = 3
    private const val BLITZ_DELAY = 2
    private const val BLITZ_HEAL = 66
    private const val SPRAY_HEAL = 90
    private const val THIN_CLOUDS = 3
    private const val CLOUD_DECAY = 2
    private const val CLOUD_SIPHON = 2
    private const val PERCENT = 100

    private const val DEFENCE_DRAIN_CAP = 20
    private const val HUNT = 30
    private const val CLIMB_SCAN = 40

    private const val NOT_JUST_A_HEAD = "Not Just a Head"
    private const val ARTERIAL_SPRAY = "Arterial Spray"
    private const val BLOOD_THINNERS = "Blood Thinners"
    private const val UPSET_STOMACH = "Upset Stomach"
}

/** Zebak's jugs: Push / Pull / Hit (ZB8). */
internal object ToaZebakJug : Boss(Npcs.TOA_ZEBAK_JUG, Npcs.TOA_ZEBAK_JUG_ROLLING) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun died(npc: NpcRef, killer: EntityRef?) = ToaZebak.hitJug(npc)
}
