package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * The Maiden of Sugadinti, ToB room 1 (docs/mechanics/raids/tob/maiden.md MA1-MA7; timings runelite-timings.md, values
 * capture-values.md, D-080), all three modes.
 * - Every 10 ticks (the first 9 after she appears) either the Blackstorm at the raider nearest her centre (projectile
 *   1577, end 109 + 10 x distance cycles; always hits for 36.5 + 3.5c, halved by Protect from Magic; 30% of storms drain
 *   floor(hit / 5) + 1 on the throw tick) or the blood throw (one splat per raider plus two near the furthest one; not
 *   again for two attacks). A splat lives 11 ticks; each tick on it deals 5..10 + 2c and heals her as much; 10 ticks
 *   after landing it may raise a blood spawn (9%, 12% if stood on; at most 8, one when all dodged).
 * - At 70 / 50 / 30% she changes form and spawns Matomenos: 2 a raider (at most 10; Hard always 10) on the north and
 *   south walls; they walk a tile a tick from the tick after, and each that reaches her heals her twice its HP and adds
 *   one to c. Hard: the first five also quicken her attacks (to at least 3 ticks).
 * - Blood spawns wander and leave trails (29 ticks; Hard: never fade) that hurt and heal her.
 */
internal object TobMaiden : Boss(
    Npcs.TOB_MAIDEN_100,
    Npcs.TOB_MAIDEN_70,
    Npcs.TOB_MAIDEN_50,
    Npcs.TOB_MAIDEN_30,
    Npcs.TOB_MAIDEN_100_STORY,
    Npcs.TOB_MAIDEN_70_STORY,
    Npcs.TOB_MAIDEN_50_STORY,
    Npcs.TOB_MAIDEN_30_STORY,
    Npcs.TOB_MAIDEN_100_HARD,
    Npcs.TOB_MAIDEN_70_HARD,
    Npcs.TOB_MAIDEN_50_HARD,
    Npcs.TOB_MAIDEN_30_HARD,
) {
    private class Pool(val tile: CoordGrid, val until: Long, val trail: Boolean) {
        var stoodOn = false
    }

    private class Batch {
        val pools = ArrayList<Pool>()
        var spawned = false
    }

    private class State(val mode: Int) {
        var leaked = 0
        var bloodCooldown = 0
        var waves = 0
        var speed = ATTACK_SPEED
        var hardQuickened = 0
        var lastTarget: String? = null
        var sameTarget = 0
        val pools = ArrayList<Pool>()
        var started = false
    }

    private val STATE = AttrKey<State>("tob_maiden")

    private fun state(npc: NpcRef): State =
        npc[STATE] ?: State(TobModes.of(npc.type)).also {
            npc[STATE] = it
        }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
        // Capture: her first attack comes 9 ticks after she appears.
        npc.setAttackClock(npc.tick + FIRST_ATTACK)
        val s = state(npc)
        if (!s.started) {
            s.started = true
            loop(npc, s)
        }
    }

    private fun centre(npc: NpcRef) = CoordGrid(npc.coord.x + CENTRE, npc.coord.z + CENTRE, npc.coord.level)

    private fun cheb(a: CoordGrid, b: CoordGrid) = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        val raiders = TfaKit.arena(npc)
        if (raiders.isEmpty()) return
        npc.setAttackClock(npc.tick + s.speed)
        // OPEN-MA10: when the blood throw is allowed she uses it half the time.
        if (s.bloodCooldown <= 0 && npc.random(2) == 0) {
            blood(npc, s, raiders)
            s.bloodCooldown = BLOOD_LOCKOUT
        } else {
            s.bloodCooldown--
            storm(npc, s, raiders)
        }
    }

    /** MA1 / capture: the Blackstorm at the raider nearest her centre. */
    private fun storm(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        val c = centre(npc)
        val target = raiders.minBy { cheb(it.coord, c) }
        npc.anim(Seqs.MAIDEN_ATTACK_SPECIAL)
        CombatSystem.announceAttack(npc, target, CombatStyle.MAGIC)
        val end = STORM_END_BASE + STORM_END_PER_TILE * cheb(target.coord, c)
        npc.projectile(target, SpotAnims.MAIDEN_SHADOW_PROJ, PROJ_HEIGHT, PROJ_HEIGHT, STORM_START, end)
        if (target.username == s.lastTarget) s.sameTarget++ else s.sameTarget = 1
        s.lastTarget = target.username
        var hit = (STORM_BASE_X10 + STORM_PER_CRAB_X10 * s.leaked) / TEN
        if (s.mode == TobModes.HARD && s.sameTarget > 2) hit += HARD_RAMP * (s.sameTarget - 2)
        hit = TobModes.damage(s.mode, hit)
        val dmg =
            when {
                !target.protects(CombatStyle.MAGIC) -> hit
                s.mode == TobModes.HARD -> minOf(HARD_PRAYED_CAP, hit / 2 + HARD_RAMP * maxOf(0, s.sameTarget - 2))
                else -> hit / 2
            }
        // Capture: 30% of storms drain floor(hit / 5) + 1 on the throw tick (OPEN-MA12: the melee stats).
        if (npc.random(PERCENT) < DRAIN_CHANCE) {
            val drain = dmg / DRAIN_DIV + 1
            for (stat in DRAINED) target.setLevel(stat, maxOf(0, target.level(stat) - drain))
            target.message("Your stats are drained.")
        }
        CombatSystem.queueHit(target, npc, dmg, HitStyle.MAGIC, end / CYCLES_PER_TICK)
    }

    /** MA2 / RuneLite / capture: a splat on every raider's tile, two more within 2 of the furthest raider. */
    private fun blood(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        val c = centre(npc)
        npc.anim(Seqs.MAIDEN_ATTACK_BLOOD)
        val furthest = raiders.maxBy { cheb(it.coord, c) }
        val throws = ArrayList<Pair<CoordGrid, Int>>()
        for (p in raiders) {
            val d = cheb(p.coord, c)
            npc.projectile(p, SpotAnims.MAIDEN_BLOOD_PROJ, PROJ_HEIGHT, 0, BLOOD_START, BLOOD_END_BASE + BLOOD_END_PER_TILE * d)
            throws += p.coord to (BLOOD_END_BASE + BLOOD_END_PER_TILE * d) / CYCLES_PER_TICK
        }
        repeat(EXTRA_SPLATS) {
            val at = BossCombat.randomTile(npc, furthest.coord, EXTRA_RADIUS)
            throws += at to (BLOOD_END_BASE + BLOOD_END_PER_TILE * cheb(at, c) + EXTRA_END) / CYCLES_PER_TICK
        }
        val batch = Batch()
        for ((tile, land) in throws) {
            npc.queueHit(land, strong = false) {
                if (!npc.isInWorld) return@queueHit
                npc.spotAnimAt(tile, SpotAnims.MAIDEN_LINGERING_BLOOD)
                val pool = Pool(tile, npc.tick + SPLAT_LIFE, trail = false)
                s.pools += pool
                batch.pools += pool
                npc.queueHit(SPAWN_AFTER, strong = false) { maybeSpawn(npc, s, pool, batch) }
            }
        }
    }

    /** Capture: 10 ticks after landing a splat raises a blood spawn 9% of the time, 12% if it was stood on. */
    private fun maybeSpawn(npc: NpcRef, s: State, pool: Pool, batch: Batch) {
        if (!npc.isInWorld) return
        val alive = Bosses.minions(npc).count { it.type in BLOOD_SPAWNS }
        if (alive >= MAX_SPAWNS) return
        // 12 Aug 2021: at most one spawn from a throw everyone dodged.
        val allDodged = batch.pools.none { it.stoodOn }
        if (allDodged && batch.spawned) return
        val chance = if (pool.stoodOn) SPAWN_STOOD else SPAWN_DODGED
        if (npc.random(PERCENT) >= chance) return
        batch.spawned = true
        Bosses.summon(npc, BLOOD_SPAWN.getValue(s.mode), pool.tile, huntRange = 0)
    }

    /** Every tick: pools and trails hurt and heal her, Matomenos walk, blood spawns wander and leave trails. */
    private fun loop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            tickPools(npc, s)
            crabs(npc, s)
            spawns(npc, s)
            loop(npc, s)
        }
    }

    private fun tickPools(npc: NpcRef, s: State) {
        s.pools.removeAll { it.until in 0 until npc.tick }
        if (s.pools.isEmpty()) return
        for (p in TfaKit.arena(npc)) {
            val pool = s.pools.firstOrNull { it.tile == p.coord } ?: continue
            pool.stoodOn = true
            val raw =
                if (pool.trail) {
                    if (s.mode ==
                        TobModes.ENTRY
                    ) {
                        BossCombat.roll(npc, ENTRY_TRAIL_MIN, ENTRY_TRAIL_MAX)
                    } else {
                        BossCombat.roll(npc, TRAIL_MIN, TRAIL_MAX)
                    }
                } else {
                    TobModes.damage(s.mode, BossCombat.roll(npc, POOL_MIN, POOL_MAX) + POOL_PER_CRAB * s.leaked)
                }
            CombatSystem.applyHit(p, npc, raw, HitStyle.TYPELESS)
            if (!pool.trail) BossCombat.drainPrayer(p, raw / PRAYER_DRAIN_DIV)
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + raw)
        }
    }

    private fun crabs(npc: NpcRef, s: State) {
        for (crab in Bosses.minions(npc).filter { it.type in MATOMENOS }) {
            if (TfaKit.gap(npc, crab) <= 1) {
                npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + 2 * crab.hitpoints)
                s.leaked++
                crab.despawn()
                if (s.mode == TobModes.HARD && s.hardQuickened < HARD_QUICKEN_CRABS) {
                    s.hardQuickened++
                    s.speed = maxOf(HARD_MIN_SPEED, s.speed - 1)
                }
            } else if (!crab.isFrozen && crab.tick > (crab[BORN] ?: 0L)) {
                crab.walkTo(centre(npc))
            }
        }
    }

    private fun spawns(npc: NpcRef, s: State) {
        for (spawn in Bosses.minions(npc).filter { it.type in BLOOD_SPAWNS }) {
            val t = spawn.coord
            if (s.pools.none { it.tile == t }) {
                s.pools += Pool(t, if (s.mode == TobModes.HARD) -1L else npc.tick + TRAIL_LIFE, trail = true)
                npc.addLoc(Locs.TOB_MAIDEN_BLOOD, t, FLOOR_SHAPE, 0, if (s.mode == TobModes.HARD) -1 else TRAIL_LIFE)
            }
            if (npc.random(2) == 0) spawn.walkTo(BossCombat.randomTile(npc, t, WANDER))
        }
    }

    /** MA4: the 70 / 50 / 30% form changes and Matomenos waves. */
    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (s.waves >= THRESHOLDS.size) return
        if (npc.hitpoints * PERCENT > npc.maxHitpoints * THRESHOLDS[s.waves]) return
        s.waves++
        val hp = npc.hitpoints
        npc.transform(npc.type + 1, restoreStats = false)
        npc.hitpoints = hp
        wave(npc, s)
    }

    private fun wave(npc: NpcRef, s: State) {
        val n = TfaKit.arena(npc).size
        val count =
            when (s.mode) {
                TobModes.HARD -> if (n <= 1) HARD_SOLO_CRABS else MAX_CRABS
                TobModes.ENTRY -> ENTRY_CRABS
                else -> minOf(MAX_CRABS, CRABS_PER_RAIDER * maxOf(1, n))
            }
        val tiles = CRAB_TILES.shuffledBy(npc).take(count)
        for ((dx, dz) in tiles) {
            val at = CoordGrid(npc.spawn.x + dx, npc.spawn.z + dz, npc.spawn.level)
            val crab = Bosses.summon(npc, MATOMENOS_BY_MODE.getValue(s.mode), at, huntRange = 0, inheritRun = true)
            TobModes.scale(crab)
            crab[BORN] = crab.tick
        }
    }

    private fun <T> List<T>.shuffledBy(npc: NpcRef): List<T> {
        val out = toMutableList()
        for (i in out.indices.reversed()) {
            val j = npc.random(i + 1)
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.MAIDEN_DEATH_A)
        Bosses.dismissMinions(npc)
    }

    private val BORN = AttrKey<Long>("tob_maiden_crab_born")
    private val THRESHOLDS = intArrayOf(70, 50, 30)
    private val DRAINED = intArrayOf(Stats.ATTACK, Stats.STRENGTH, Stats.DEFENCE)
    private val MATOMENOS = setOf(Npcs.MAIDEN_ELEMENTAL, Npcs.MAIDEN_ELEMENTAL_STORY, Npcs.MAIDEN_ELEMENTAL_HARD)
    private val BLOOD_SPAWNS = setOf(Npcs.MAIDEN_BLOOD_SLUG, Npcs.MAIDEN_BLOOD_SLUG_STORY, Npcs.MAIDEN_BLOOD_SLUG_HARD)
    private val MATOMENOS_BY_MODE =
        mapOf(
            TobModes.ENTRY to Npcs.MAIDEN_ELEMENTAL_STORY,
            TobModes.NORMAL to Npcs.MAIDEN_ELEMENTAL,
            TobModes.HARD to Npcs.MAIDEN_ELEMENTAL_HARD,
        )
    private val BLOOD_SPAWN =
        mapOf(
            TobModes.ENTRY to Npcs.MAIDEN_BLOOD_SLUG_STORY,
            TobModes.NORMAL to Npcs.MAIDEN_BLOOD_SLUG,
            TobModes.HARD to Npcs.MAIDEN_BLOOD_SLUG_HARD,
        )

    /**
     * RuneLite (BL MaidenCrab.java): N1-N3, N4 inner/outer, S1-S3, S4 inner/outer on the walls at z 4456 / 4436
     * (x 3173..3185). OPEN-MA9: the per-spawn x columns, as offsets from her south-west tile (3162, 4444).
     */
    private val CRAB_TILES =
        listOf(11 to 12, 15 to 12, 19 to 12, 22 to 12, 23 to 11, 11 to -8, 15 to -8, 19 to -8, 22 to -8, 23 to -7)

    private const val HUNT = 30
    private const val FIRST_ATTACK = 9
    private const val ATTACK_SPEED = 10
    private const val BLOOD_LOCKOUT = 2
    private const val CENTRE = 3
    private const val PROJ_HEIGHT = 43
    private const val STORM_START = 109
    private const val STORM_END_BASE = 109
    private const val STORM_END_PER_TILE = 10
    private const val CYCLES_PER_TICK = 30

    /** MA1: 36.5 + 3.5 per leaked Matomenos, in tenths (OPEN-MA11: rounded down). */
    private const val STORM_BASE_X10 = 365
    private const val STORM_PER_CRAB_X10 = 35
    private const val TEN = 10

    /** OPEN-MA8: Hard's ramp per attack on the same raider beyond two, and the prayed cap (captures: up to 50). */
    private const val HARD_RAMP = 5
    private const val HARD_PRAYED_CAP = 50
    private const val PERCENT = 100
    private const val DRAIN_CHANCE = 30
    private const val DRAIN_DIV = 5
    private const val BLOOD_START = 20
    private const val BLOOD_END_BASE = 20
    private const val BLOOD_END_PER_TILE = 15
    private const val EXTRA_END = 25
    private const val EXTRA_SPLATS = 2
    private const val EXTRA_RADIUS = 2
    private const val SPLAT_LIFE = 11
    private const val SPAWN_AFTER = 10
    private const val SPAWN_STOOD = 12
    private const val SPAWN_DODGED = 9
    private const val MAX_SPAWNS = 8
    private const val POOL_MIN = 5
    private const val POOL_MAX = 10
    private const val POOL_PER_CRAB = 2

    /** OPEN-MA13: the prayer a splat drains (MA2 says only "drains prayer"): half its damage. */
    private const val PRAYER_DRAIN_DIV = 2
    private const val TRAIL_MIN = 5
    private const val TRAIL_MAX = 13
    private const val ENTRY_TRAIL_MIN = 2
    private const val ENTRY_TRAIL_MAX = 5
    private const val TRAIL_LIFE = 29
    private const val FLOOR_SHAPE = 22
    private const val WANDER = 3
    private const val CRABS_PER_RAIDER = 2
    private const val MAX_CRABS = 10

    /** 9 Jun 2021 hotfix: Hard solo has no S1 spawn. */
    private const val HARD_SOLO_CRABS = 9

    /** OPEN-MA6: Entry's count ("a red Nylocas Matomenos on either side"). */
    private const val ENTRY_CRABS = 2
    private const val HARD_QUICKEN_CRABS = 5
    private const val HARD_MIN_SPEED = 3
}

/** The Maiden's Matomenos and blood spawns never attack; her script moves them (MA3, MA4). */
internal object TobMaidenMinions : Boss(
    Npcs.MAIDEN_ELEMENTAL,
    Npcs.MAIDEN_ELEMENTAL_STORY,
    Npcs.MAIDEN_ELEMENTAL_HARD,
    Npcs.MAIDEN_BLOOD_SLUG,
    Npcs.MAIDEN_BLOOD_SLUG_STORY,
    Npcs.MAIDEN_BLOOD_SLUG_HARD,
) {
    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        // MA3 / MA4: they do not attack.
    }

    /** MA-Hard: Hard blood spawns are invulnerable (still attackable). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        if (npc.type == Npcs.MAIDEN_BLOOD_SLUG_HARD) 0 else damage
}
