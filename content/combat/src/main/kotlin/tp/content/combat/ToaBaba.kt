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
 * Ba-Ba, the Path of Apmeken boss (docs/mechanics/raids/toa/baba.md BB1-BB15).
 * - BB2: melee every 6 ticks (4 in the last form 11779), crush max 24 (32 in the last form, derived F3), Protect from
 *   Melee blocks it (Quiet Prayers lets 10% through); out of reach she throws (ranged, OPEN-BB3).
 * - BB3-BB7: specials on a fixed cycle (OPEN-BB4, `toa.baba.specialCycle`): Shockwave (plus-shaped area in front),
 *   Rockfall (a rock on each player's tile and two debris piles), Rock Throw (a boulder at every player, nulled next to
 *   debris / an unopened sarcophagus / a baboon), Baboon's Discharge (1-4 baboons that break the sarcophagi open).
 * - BB8: Jungle Japes: a dead baboon leaves two peels (stun 5 ticks).
 * - BB9-BB10: at 66.6% and 33.3% she leaps to the throne, screams the party to the bottom, destroys the debris and
 *   rolls ten sets of five boulders down (one cracked); Boulderdash doubles the pace; a melee hit past the boulders' start
 *   ends it. BB11: with Mind the Gap! a knock into the pit kills. BB12: the last form after the second boulder phase.
 * - BB13: her Defence drain stops 20 below base.
 */
internal object ToaBaba : Boss(Npcs.TOA_BABA, Npcs.TOA_BABA_COFFIN, Npcs.TOA_BABA_DIGGING) {
    private enum class Phase { FIGHT, THRONE }

    private class State {
        var phase = Phase.FIGHT
        var boulderPhases = 0
        var attacks = 0
        var special = 0
        var sets = 0
        var setTimer = 0
        var cracked = -1
        var rockfallNext = false
        val sarcophagi = ArrayList<NpcRef>()
        val opened = HashSet<CoordGrid>()
    }

    private val STATE = AttrKey<State>("toa_baba")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.TOA_BABA || npc[STATE] != null) return
        val s = state(npc)
        npc.huntRange = HUNT
        // BB7: the sarcophagi's health bars (NPC 11788 on each of the eight coffins; cache HP 60, `toa.baba.sarcophagusHp`).
        for (t in SARCOPHAGI) {
            s.sarcophagi +=
                Bosses.summon(npc, Npcs.TOA_BABA_SARC_NPC, ToaKit.at(npc, t), huntRange = 0, inheritRun = true)
        }
        ToaKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = state(npc).phase == Phase.FIGHT

    /** OPEN-BB10: no damage on the throne, except the melee hit past the boulder start that ends the phase (BB10). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (s.phase != Phase.THRONE) return damage
        if (style == HitStyle.MELEE && source is PlayerRef && ToaKit.template(npc, source.coord).x >= BOULDER_START_X) endThrone(npc, s)
        return 0
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        // BB13: drained Defence stops 20 below its base.
        val floor = npc.baseLevel(NpcStat.DEFENCE) - DEFENCE_DRAIN_CAP
        if (npc.level(NpcStat.DEFENCE) < floor) npc.setLevel(NpcStat.DEFENCE, floor)
        if (s.phase != Phase.FIGHT) return
        val thresholds = intArrayOf(THRESHOLD_1, THRESHOLD_2)
        if (s.boulderPhases < thresholds.size && npc.hitpoints * PER_MILLE <= npc.maxHitpoints * thresholds[s.boulderPhases]) {
            toThrone(npc, s)
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (s.phase != Phase.FIGHT) return
        if (++s.attacks % SPECIAL_CYCLE == 0) {
            special(npc, s, victim)
            return
        }
        val last = npc.type == Npcs.TOA_BABA_COFFIN
        if (distance) {
            // OPEN-BB3: the infobox lists ranged; out of reach she throws at her target (projectile 2244 by name).
            npc.anim(Seqs.NPC_MANDRILL_ATTACK_RANGED_01)
            BossCombat.projectile(npc, victim, THROWN, RANGED_DELAY)
            ToaKit.strike(npc, victim, AttackType.RANGED, ToaKit.dmg(npc, if (last) LAST_MAX else MELEE_MAX), RANGED_DELAY)
            return
        }
        npc.anim(Seqs.NPC_BABA_ATTACK_MELEE)
        ToaKit.strike(npc, victim, AttackType.CRUSH, ToaKit.dmg(npc, if (last) LAST_MAX else MELEE_MAX), delay = 0)
    }

    /** BB3 / OPEN-BB4: the four specials in turn; Rockfall again right after a Rock Throw that destroyed debris. */
    private fun special(npc: NpcRef, s: State, victim: EntityRef) {
        if (s.rockfallNext) {
            s.rockfallNext = false
            rockfall(npc)
            return
        }
        when (s.special++ % SPECIALS) {
            0 -> shockwave(npc, victim)
            1 -> rockfall(npc)
            2 -> rockThrow(npc, s)
            else -> discharge(npc)
        }
    }

    /** BB4: a plus shape in front of her (bounding 5x5, 7x7 with Shaking Things Up), struck after the shadow. */
    private fun shockwave(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_MELEE)
        val big = ToaKit.has(npc, SHAKING)
        val core = if (big) 2 else 1
        val centre = victim.coord
        val tiles = ArrayList<CoordGrid>()
        for (dx in -core..core) for (dz in -core..core) tiles += CoordGrid(centre.x + dx, centre.z + dz, centre.level)
        for ((dx, dz) in ARMS) tiles += CoordGrid(centre.x + dx * (core + 1), centre.z + dz * (core + 1), centre.level)
        npc.queueHit(SHOCKWAVE_DELAY, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (t in tiles) {
                npc.spotAnimAt(t, SLAM_IMPACT)
                for (p in ToaKit.on(npc, t)) ToaKit.hitRolled(npc, p, SHOCKWAVE_MAX)
            }
        }
    }

    /** BB5: a rock on every player's tile (2250 / 2251 / 2252 by path level, 6 / 4 / 2 ticks) and two debris piles. */
    private fun rockfall(npc: NpcRef) {
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_SPECIAL_RANGED01)
        val step = ToaKit.step(npc)
        val gfx = ROCK_GFX[step]
        val delay = ROCK_DELAY[step]
        for (p in ToaKit.raiders(npc)) {
            val tile = p.coord
            npc.spotAnimAt(tile, gfx)
            npc.queueHit(delay, strong = false) {
                if (!npc.isInWorld) return@queueHit
                for (v in ToaKit.on(npc, tile)) ToaKit.hitRolled(npc, v, ROCK_MAX)
            }
        }
        repeat(DEBRIS_PER_FALL) {
            val at = BossCombat.randomTile(npc, ToaKit.at(npc, ARENA_CENTRE), ARENA_RADIUS)
            npc.spotAnimAt(at, gfx)
            npc.queueHit(delay, strong = false) {
                if (!npc.isInWorld || npc.isDying) return@queueHit
                // BB5: players right under it are hit, the rest of its 3x3 pushed aside.
                for (v in ToaKit.on(npc, at, 1)) {
                    if (v.coord == at) {
                        ToaKit.hitRolled(npc, v, ROCK_MAX)
                    } else {
                        v.teleport(
                            CoordGrid(at.x + 2 * Integer.signum(v.coord.x - at.x), at.z + 2 * Integer.signum(v.coord.z - at.z), at.level),
                        )
                    }
                }
                val debris =
                    Bosses.summon(
                        npc,
                        RUBBLE[npc.random(RUBBLE.size)],
                        CoordGrid(at.x - 1, at.z - 1, at.level),
                        huntRange = 0,
                        inheritRun = true,
                    )
                // OPEN-BB6 / conflict 3: 10 HP plus 10 per player (Strat), flag `toa.baba.rubbleHpPerPlayer`.
                val hp = RUBBLE_HP_PER_PLAYER * maxOf(1, ToaKit.raiders(npc).size) + RUBBLE_HP_BASE
                debris.overrideStats(IntArray(STATS) { i -> if (i == HITPOINTS) hp else debris.baseLevel(i) })
                debris.hitpoints = hp
            }
        }
    }

    /** BB6: a boulder at every player, landing 7 ticks after the animation (MT); objects beside them take it instead. */
    private fun rockThrow(npc: NpcRef, s: State) {
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_RANGED_01)
        val solo = ToaKit.raiders(npc).size <= 1
        for (p in ToaKit.raiders(npc)) {
            BossCombat.projectile(npc, p, THROWN, THROW_DELAY)
            npc.queueHit(THROW_DELAY, strong = false) {
                if (!npc.isInWorld || npc.isDying || state(npc).phase != Phase.FIGHT) return@queueHit
                val shield = shieldFor(npc, s, p)
                if (shield == null) {
                    // OPEN-BB7: "massive", less in a group than solo.
                    ToaKit.areaHit(npc, p, ToaKit.dmg(npc, if (solo) THROW_SOLO else THROW_GROUP))
                    return@queueHit
                }
                npc.spotAnimAt(shield.coord, RUBBLE_ABSORB)
                val absorbed =
                    when (shield.type) {
                        Npcs.TOA_BABA_SARC_NPC -> SARCO_TAKES
                        Npcs.TOA_BABA_BABOON -> BABOON_TAKES
                        else -> DEBRIS_TAKES
                    }
                CombatSystem.queueHit(shield, npc, minOf(absorbed, shield.hitpoints), HitStyle.TYPELESS, 0)
                if (shield.type in RUBBLE && absorbed >= shield.hitpoints) s.rockfallNext = true
            }
        }
    }

    /** BB6: the object that nulls a throw at [p]: debris (5x5 around its 3x3), an unopened sarcophagus or a baboon next to them. */
    private fun shieldFor(npc: NpcRef, s: State, p: PlayerRef): NpcRef? {
        val near =
            Bosses.minions(npc).filter { m ->
                when (m.type) {
                    in RUBBLE -> p.coord.x in m.coord.x - 1..m.coord.x + DEBRIS_SIZE && p.coord.z in m.coord.z - 1..m.coord.z + DEBRIS_SIZE
                    Npcs.TOA_BABA_SARC_NPC -> m.coord !in s.opened && p.distanceTo(m) <= 1
                    Npcs.TOA_BABA_BABOON -> p.distanceTo(m) <= 1
                    else -> false
                }
            }
        return near.firstOrNull()
    }

    /** BB7: 1-4 baboons by party size (OPEN-BB8); "periodically" one breaks a sarcophagus open (20 a hit). */
    private fun discharge(npc: NpcRef) {
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_SPECIAL_JUMP01)
        val n = ToaKit.raiders(npc).size.coerceIn(1, MAX_BABOONS)
        repeat(n) {
            val at = BossCombat.randomTile(npc, ToaKit.at(npc, ARENA_CENTRE), ARENA_RADIUS)
            val b = Bosses.summon(npc, Npcs.TOA_BABA_BABOON, at, huntRange = HUNT, inheritRun = true)
            b.anim(Seqs.NPC_MANDRILL_SPAWN01)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        sarcophagi(npc, s)
        peels(npc)
        if (s.phase == Phase.THRONE) throne(npc, s)
    }

    /**
     * BB7: every [BREAK_EVERY] ticks one living baboon hits an unopened sarcophagus for 20; at 0 the coffin opens (loc
     * 45752 -> 45753) and blasts the tiles in front of it ([BLAST_EVERY] ticks): 1x1, wider every two path levels;
     * Gotta Have Faith adds damage by the target's missing prayer (OPEN-BB8, `toa.baba.faithMorePrayerMoreDamage` off).
     */
    private fun sarcophagi(npc: NpcRef, s: State) {
        val now = npc.tick
        val baboons = Bosses.minions(npc, Npcs.TOA_BABA_BABOON)
        if (now % BREAK_EVERY == 0L && baboons.isNotEmpty()) {
            val target = s.sarcophagi.filter { it.isInWorld && it.coord !in s.opened }.minByOrNull { baboons.first().distanceTo(it) }
            if (target != null) {
                baboons.first().anim(Seqs.NPC_MANDRILL_ATTACK_MELEE)
                CombatSystem.queueHit(target, baboons.first(), BABOON_BREAKS, HitStyle.TYPELESS, 0)
            }
        }
        for (sarc in s.sarcophagi) {
            if (sarc.coord in s.opened) continue
            if (sarc.isInWorld && sarc.hitpoints > 0) continue
            s.opened += sarc.coord
            val loc = ToaKit.template(npc, sarc.coord)
            npc.addLoc(Locs.TOA_BABA_COFFIN_OPEN, ToaKit.at(npc, loc), CENTREPIECE, 0)
        }
        if (now % BLAST_EVERY != 0L) return
        val radius = ToaKit.step(npc)
        for (c in s.opened) {
            // The cone in front of the coffin: the row towards the arena middle (OPEN-BB8: shape and rate).
            val dz = if (ToaKit.template(npc, c).z < ARENA_CENTRE.z) 1 else -1
            for (i in 1..BLAST_RANGE) {
                val t = CoordGrid(c.x, c.z + dz * i, c.level)
                for (p in ToaKit.on(npc, t, radius)) {
                    var dmg = BossCombat.roll(npc, 1, ToaKit.dmg(npc, BLAST_MAX))
                    if (ToaKit.has(npc, FAITH)) {
                        val missing = p.baseLevel(Stat.PRAYER) - p.level(Stat.PRAYER)
                        dmg += if (FAITH_MORE_PRAYER_MORE) p.level(Stat.PRAYER) / FAITH_DIV else missing / FAITH_DIV
                    }
                    BossCombat.projectile(npc, p, BLAST, 1)
                    ToaKit.areaHit(npc, p, dmg, 1)
                }
            }
        }
    }

    /** BB8: Jungle Japes peels: a player on one slips (small damage, 5-tick stun), the peel goes. */
    private fun peels(npc: NpcRef) {
        val peels = npc[PEELS] ?: return
        val it = peels.iterator()
        while (it.hasNext()) {
            val t = it.next()
            val p = ToaKit.on(npc, t).firstOrNull() ?: continue
            p.anim(Seqs.ROYAL_HUMAN_SLIP_FALL)
            p.spotAnim(SLIP_GFX)
            ToaKit.areaHit(npc, p, BossCombat.roll(npc, 1, PEEL_MAX))
            ToaKit.stun(p, PEEL_STUN)
            npc.revertLoc(t, CENTREPIECE)
            it.remove()
        }
    }

    private val PEELS = AttrKey<MutableList<CoordGrid>>("toa_baba_peels")

    /** A fight baboon died: two peels with Jungle Japes (BB8; called from [died] of the minion via the registry). */
    fun baboonDied(owner: NpcRef, baboon: NpcRef) {
        if (!ToaKit.has(owner, JUNGLE_JAPES)) return
        val list = owner[PEELS] ?: ArrayList<CoordGrid>().also { owner[PEELS] = it }
        for (t in listOf(baboon.coord, BossCombat.randomTile(owner, baboon.coord, 1))) {
            owner.addLoc(Locs.TOA_BABA_BANANA_PEEL, t, CENTREPIECE, 0)
            list += t
        }
    }

    /** BB9 / BB10: the throne: scream, debris destroyed, ten sets of five boulders (one cracked). */
    private fun toThrone(npc: NpcRef, s: State) {
        s.phase = Phase.THRONE
        s.sets = 0
        s.setTimer = 0
        s.cracked = -1
        npc.stopInteraction()
        npc.transform(Npcs.TOA_BABA_DIGGING, restoreStats = false)
        npc.teleport(ToaKit.at(npc, THRONE))
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_SPECIAL_JUMP01)
        for (m in Bosses.minions(npc)) if (m.type in RUBBLE) m.despawn()
        for (p in ToaKit.raiders(npc)) {
            // The scream knocks everyone to the bottom of the arena (west).
            val to = CoordGrid(ToaKit.atX(npc, SCREAM_X), p.coord.z, p.coord.level)
            p.anim(Seqs.HUMAN_TROLL_FLYBACK_MERGE)
            knock(npc, p, to)
        }
    }

    private fun throne(npc: NpcRef, s: State) {
        val interval = if (ToaKit.has(npc, BOULDERDASH)) SET_INTERVAL / 2 else SET_INTERVAL
        if (++s.setTimer >= interval && s.sets < SETS) {
            s.setTimer = 0
            s.sets++
            boulderSet(npc, s)
        }
        rollBoulders(npc)
        if (s.sets >= SETS && Bosses.minions(npc).none { it.type == Npcs.TOA_BABA_BOULDER || it.type == Npcs.TOA_BABA_BOULDER_WEAK }) {
            endThrone(npc, s)
        }
    }

    /** BB10 (Strat): the first cracked boulder in one of the middle three lanes, each later one within two lanes. */
    private fun boulderSet(npc: NpcRef, s: State) {
        s.cracked =
            if (s.cracked < 0) {
                1 + npc.random(LANES - 2)
            } else {
                (s.cracked - 2 + npc.random(CRACK_SPREAD)).coerceIn(0, LANES - 1)
            }
        val pathLevel = ToaKit.pathLevel(npc)
        val party = ToaKit.raiders(npc).size.coerceAtLeast(1)
        for (lane in 0 until LANES) {
            val at = ToaKit.at(npc, CoordGrid(BOULDER_START_X, ARENA_MIN_Z + lane * DEBRIS_SIZE))
            val type = if (lane == s.cracked) Npcs.TOA_BABA_BOULDER_WEAK else Npcs.TOA_BABA_BOULDER
            val b = Bosses.summon(npc, type, at, huntRange = 0, inheritRun = true)
            val hp = boulderHp(party, pathLevel)
            b.overrideStats(IntArray(STATS) { i -> if (i == HITPOINTS) hp else b.baseLevel(i) })
            b.hitpoints = hp
        }
    }

    /** Scaling: base 25, the party at half effect, then +10% (path 2-3) or +25% (4+), floored after each step (derived). */
    fun boulderHp(party: Int, pathLevel: Int): Int {
        val n = party.coerceIn(1, MAX_PARTY)
        val tenths =
            PER_TEN +
                when {
                    n >= 3 -> HALF_EARLY * 2 + HALF_LATE * (n - 3)
                    n == 2 -> HALF_EARLY
                    else -> 0
                }
        var hp = BOULDER_BASE * tenths / PER_TEN_HUNDRED
        hp =
            when {
                pathLevel >= PATH_HIGH -> hp * BOULDER_HIGH / PERCENT
                pathLevel >= PATH_LOW -> hp * BOULDER_LOW / PERCENT
                else -> hp
            }
        return hp
    }

    /** BB10: boulders roll west one tile a tick (OPEN-BB10); contact knocks back 4 and hits hard, debris and baboons go. */
    private fun rollBoulders(npc: NpcRef) {
        for (b in Bosses.minions(npc)) {
            if (b.type != Npcs.TOA_BABA_BOULDER && b.type != Npcs.TOA_BABA_BOULDER_WEAK) continue
            val next = CoordGrid(b.coord.x - 1, b.coord.z, b.coord.level)
            if (ToaKit.template(npc, next).x <= WALL_X) {
                b.despawn()
                continue
            }
            b.teleport(next)
            for (m in Bosses.minions(npc)) {
                val crushable = m.type in RUBBLE || m.type == Npcs.TOA_BABA_BABOON
                if (crushable && m.distanceTo(b) <= 1) m.despawn()
            }
            for (p in ToaKit.raiders(npc)) {
                if (p.coord.x !in next.x..next.x + 2 || p.coord.z !in next.z..next.z + 2) continue
                p.anim(Seqs.HUMAN_KNOCKDOWN)
                ToaKit.hitRolled(npc, p, BOULDER_HIT)
                knock(npc, p, CoordGrid(p.coord.x - KNOCKBACK, p.coord.z, p.coord.level))
            }
        }
    }

    /** A knock to [to]; with Mind the Gap! a landing in the pit kills (BB11); without it the pit is not entered. */
    private fun knock(npc: NpcRef, p: PlayerRef, to: CoordGrid) {
        val t = ToaKit.template(npc, to)
        val inPit = t.x in PIT_MIN_X..PIT_MAX_X && t.z in PIT_MIN_Z..PIT_MAX_Z
        if (inPit && ToaKit.has(npc, MIND_THE_GAP)) {
            p.teleport(to)
            ToaKit.areaHit(npc, p, p.hitpoints)
            return
        }
        val x = if (t.x <= PIT_MAX_X) ToaKit.at(npc, CoordGrid(WALL_X + 1, 0)).x else to.x
        p.teleport(CoordGrid(x, to.z, to.level))
    }

    /** BB10 end: back to the fight (the last form after the second boulder phase, BB12). */
    private fun endThrone(npc: NpcRef, s: State) {
        if (s.phase != Phase.THRONE) return
        s.phase = Phase.FIGHT
        s.boulderPhases++
        for (m in Bosses.minions(npc)) if (m.type == Npcs.TOA_BABA_BOULDER || m.type == Npcs.TOA_BABA_BOULDER_WEAK) m.despawn()
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_SPECIAL_END01)
        npc.transform(if (s.boulderPhases >= 2) Npcs.TOA_BABA_COFFIN else Npcs.TOA_BABA, restoreStats = false)
        npc.teleport(ToaKit.at(npc, ARENA_CENTRE))
    }

    /** BB14 / Data: Apmeken's capture (if unread) and the Big banana to the top damage dealer. */
    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
        val top = Bosses.topDamager(npc) ?: return
        val drops =
            buildList {
                add(Objs.TOA_BABA_BANANA)
                if (top.varbit(Varbits.TOA_BOOK_BABA) == 0) add(Objs.TOA_BOOK_BABA)
            }
        for (obj in drops) ToaKit.giveOrDrop(top, obj, "ba-ba drop")
    }

    // ---- data (template tiles: region 15188, base (3776, 5376); cache map)

    /** The eight sarcophagi 45752 (cache map: x 3803 / 3808 / 3813 / 3818 at z 5400 and 5416). */
    private val SARCOPHAGI =
        listOf(3803, 3808, 3813, 3818).flatMap { x -> listOf(CoordGrid(x, SARCO_SOUTH_Z), CoordGrid(x, SARCO_NORTH_Z)) }
    private const val SARCO_SOUTH_Z = 5400
    private const val SARCO_NORTH_Z = 5416

    /** OPEN-BB2: the arena middle between the coffin rows; the throne 45756 at (3822, 5407). */
    private val ARENA_CENTRE = CoordGrid(3810, 5406)
    private val THRONE = CoordGrid(3821, 5405)
    private const val ARENA_RADIUS = 6
    private const val ARENA_MIN_Z = 5401

    /** BB11 (MT): the pit = region-local x 20-21, y 30-34; the boulders' wall column x 22. */
    private const val PIT_MIN_X = 3796
    private const val PIT_MAX_X = 3797
    private const val PIT_MIN_Z = 5406
    private const val PIT_MAX_Z = 5410
    private const val WALL_X = 3798

    /** BB10: the scream knocks players to the bottom (one column in from the wall); boulders start in front of the throne. */
    private const val SCREAM_X = 3799
    private const val BOULDER_START_X = 3818

    private val ARMS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val RUBBLE = setOf(Npcs.TOA_BABA_RUBBLE, Npcs.TOA_BABA_RUBBLE_2, Npcs.TOA_BABA_RUBBLE_3, Npcs.TOA_BABA_RUBBLE_4).toList()

    /** BB2: max 24 (infobox), last form 32 (derived F3 on the cache 11779, OPEN-BB11). */
    private const val MELEE_MAX = 24
    private const val LAST_MAX = 32
    private const val RANGED_DELAY = 2

    /** OPEN-BB4: a special every fourth attack (`toa.baba.specialCycle`). */
    private const val SPECIAL_CYCLE = 4
    private const val SPECIALS = 4

    /** OPEN-BB5: the shadow lasts 2 ticks; damage up to her melee max. */
    private const val SHOCKWAVE_DELAY = 2
    private const val SHOCKWAVE_MAX = 24

    /** BB5 (MT): 2250 / 2251 hit 6 / 4 ticks after they appear; 2252 inferred 2 (OPEN-BB6); "heavy" (OPEN-BB6: 40). */
    private val ROCK_GFX =
        intArrayOf(
            tp.symbols.SpotAnims.TOA_BABA_ROCK_FALL,
            tp.symbols.SpotAnims.TOA_BABA_ROCK_FALL_FASTER,
            tp.symbols.SpotAnims.TOA_BABA_ROCK_FALL_FASTEST,
        )
    private val ROCK_DELAY = intArrayOf(6, 4, 2)
    private const val ROCK_MAX = 40
    private const val DEBRIS_PER_FALL = 2
    private const val DEBRIS_SIZE = 3
    private const val RUBBLE_HP_PER_PLAYER = 10
    private const val RUBBLE_HP_BASE = 0

    /** BB6: lands 7 ticks after 9744 (MT); "massive" (OPEN-BB7: 60 solo, 45 in a group); objects take 10 / 30 / 12. */
    private const val THROW_DELAY = 7
    private const val THROW_SOLO = 60
    private const val THROW_GROUP = 45
    private const val DEBRIS_TAKES = 10
    private const val SARCO_TAKES = 30
    private const val BABOON_TAKES = 12

    /** BB7: 1-4 baboons; a baboon deals 20 a hit to a sarcophagus; OPEN-BB8 cadences, cone length and blast damage. */
    private const val MAX_BABOONS = 4
    private const val BABOON_BREAKS = 20
    private const val BREAK_EVERY = 8L
    private const val BLAST_EVERY = 4L
    private const val BLAST_RANGE = 6
    private const val BLAST_MAX = 15
    private const val FAITH_DIV = 5
    private const val FAITH_MORE_PRAYER_MORE = false

    /** BB8: OPEN-BB9 peel damage; stun 3 s = 5 ticks; slip graphic 1575 (MT). */
    private const val PEEL_MAX = 5
    private const val PEEL_STUN = 5
    private const val SLIP_GFX = 1575

    /** BB9: 66.6% / 33.3% of max HP. */
    private const val THRESHOLD_1 = 666
    private const val THRESHOLD_2 = 333
    private const val PER_MILLE = 1000

    /** BB10: ten sets of five lanes (3 tiles each), one cracked; OPEN-BB10 interval 6 ticks (Boulderdash: half). */
    private const val SETS = 10
    private const val LANES = 5
    private const val CRACK_SPREAD = 5
    private const val SET_INTERVAL = 6
    private const val BOULDER_HIT = 30
    private const val KNOCKBACK = 4

    /** Scaling: boulder HP base 25, party at half effect (tenths x 100: 1 player 1000), +10% / +25% at path 2 / 4. */
    private const val BOULDER_BASE = 25
    private const val PER_TEN = 1000
    private const val PER_TEN_HUNDRED = 1000
    private const val HALF_EARLY = 450
    private const val HALF_LATE = 300
    private const val BOULDER_LOW = 110
    private const val BOULDER_HIGH = 125
    private const val PATH_LOW = 2
    private const val PATH_HIGH = 4
    private const val PERCENT = 100
    private const val MAX_PARTY = 8

    /** BB13: drain stops 20 below base. */
    private const val DEFENCE_DRAIN_CAP = 20

    private const val HUNT = 24
    private const val STATS = 6
    private const val HITPOINTS = 3
    private const val CENTREPIECE = 10

    /** Graphic ids (MT / cache names): slam impact 1103, rubble absorb 1463, thrown boulder 2244, sarcophagus ball 2245. */
    private const val SLAM_IMPACT = 1103
    private const val RUBBLE_ABSORB = 1463
    private const val THROWN = 2244
    private const val BLAST = 2245

    private const val SHAKING = "Shaking Things Up"
    private const val FAITH = "Gotta Have Faith"
    private const val JUNGLE_JAPES = "Jungle Japes"
    private const val BOULDERDASH = "Boulderdash"
    private const val MIND_THE_GAP = "Mind the Gap!"
}

/** Ba-Ba's fight baboons (11781): Jungle Japes peels on death (BB8); their attacks are the generic NPC combat. */
internal object ToaBabaBaboon : Boss(Npcs.TOA_BABA_BABOON) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.anim(Seqs.NPC_MANDRILL_ATTACK_RANGED_01)
        BossCombat.projectile(npc, victim, SHOT, SHOT_DELAY)
        ToaKit.strike(npc, victim, AttackType.RANGED, ToaKit.dmg(npc, SHOT_MAX, pathScaled = false), SHOT_DELAY)
    }

    /** BB7: "players are guaranteed to land max hits on them" (10 HP): any landing hit kills. */
    override fun incoming(
        npc: NpcRef,
        source: EntityRef?,
        damage: Int,
        style: HitStyle,
    ): Int = if (source is PlayerRef) npc.hitpoints else damage

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc[Bosses.OWNER]?.let { ToaBaba.baboonDied(it, npc) }
    }

    /** Baboon shot 2242 (name only), infobox max hit "1?" (OPEN-BB8). */
    private const val SHOT = 2242
    private const val SHOT_DELAY = 2
    private const val SHOT_MAX = 1
}
