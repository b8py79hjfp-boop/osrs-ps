package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Xarpus, ToB room 5 (docs/mechanics/raids/tob/xarpus.md XA1-XA5; counts and timings from capture-values.md and
 * runelite-timings.md, damage from the wiki, D-080), all three modes. The arena is on level 1.
 * - Phase 1: he wakes at 75% when a raider enters and feeds: exhumeds rise across the arena (solo 7 every 12 ticks, two
 *   9 every 8, three 12 every 8, four 15 every 4; Hard more), each living 11 ticks (Hard 9) and firing an orb at him every
 *   tick from 3 ticks after it appears unless a raider stands on it; an orb heals him 20 / 16 / 12 / 9 (by party).
 *   8 ticks after the last exhumed closes he takes his combat form.
 * - Phase 2: a spit every 4 ticks (first 9 after the transform) at the next raider in orb order, at the tile they stood
 *   on 2 ticks before (flight 30 + 30 x max(1, d) cycles): 3 x 3 poison splash, the centre a pool for the rest of the
 *   fight (4-8 a tick); it splashes on to the next one or two raiders' tiles. A raider under him is pelted (two 3-7 hits).
 *   Missed absorbs raise the poison and counter damage (OPEN-XA4: 1% each).
 * - Phase 3 at 25% (Entry 22.5%): "Screeeeech!", he stares at a quadrant, turning 9 ticks later and then every 8 (never
 *   the same twice); an attack from the stared quadrant is answered with 50-75 poison damage (Hard: he turns to the
 *   quadrant he is hit from).
 * - Hard: at the start the two outer layers are poisoned except the six entrance tiles.
 */
internal object TobXarpus : Boss(
    Npcs.TOB_XARPUS_STATIC,
    Npcs.TOB_XARPUS_FEEDING,
    Npcs.TOB_XARPUS_COMBAT,
    Npcs.TOB_XARPUS_STATIC_STORY,
    Npcs.TOB_XARPUS_FEEDING_STORY,
    Npcs.TOB_XARPUS_COMBAT_STORY,
    XARPUS_HARD_STATIC,
    XARPUS_HARD_FEEDING,
    Npcs.TOB_XARPUS_COMBAT_HARD,
) {
    private class Exhumed(val tile: CoordGrid, val until: Long, val firstOrb: Long)

    private class State(val mode: Int, val home: CoordGrid) {
        var phase = 0
        var spawned = 0
        var total = 0
        var every = 0
        var nextExhumed = 0L
        var lastClosed = -1L
        var missed = 0
        var turnAt = 0L
        var spits = 0
        var lastSpit = 0L
        var quadrant = -1
        val exhumeds = ArrayList<Exhumed>()
        val pools = HashSet<CoordGrid>()
        val history = HashMap<String, ArrayDeque<CoordGrid>>()
    }

    private val STATE = AttrKey<State>("tob_xarpus")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State(TobModes.of(npc.type), npc.spawn).also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
        if (npc[STATE] == null) loop(npc, state(npc))
    }

    /** Raiders in the arena (template 3164..3176 x 4380..4394 on level 1; offsets from his spawn 3169, 4386). */
    private fun raiders(npc: NpcRef, s: State): List<PlayerRef> =
        TfaKit.arena(npc).filter {
            it.coord.level == s.home.level && it.coord.x - s.home.x in MIN_X..MAX_X &&
                it.coord.z - s.home.z in MIN_Z..MAX_Z
        }

    private fun loop(npc: NpcRef, s: State) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld) return@queueHit
            tick(npc, s)
            loop(npc, s)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        val raiders = raiders(npc, s)
        for (p in raiders) {
            s.history.getOrPut(p.username) { ArrayDeque() }.apply {
                addLast(p.coord)
                while (size > HISTORY) removeFirst()
            }
        }
        when (s.phase) {
            0 -> if (raiders.isNotEmpty()) startFeeding(npc, s, raiders.size)
            1 -> feeding(npc, s, raiders)
            else -> fighting(npc, s, raiders)
        }
        pools(npc, s, raiders)
    }

    private fun startFeeding(npc: NpcRef, s: State, n: Int) {
        s.phase = 1
        npc.transform(npc.type + 1, restoreStats = false)
        // XA1: he starts at 75% of his HP.
        npc.hitpoints = npc.maxHitpoints * START_PERCENT / PERCENT
        val i = (n - 1).coerceIn(0, COUNTS_NORMAL.size - 1)
        s.total = if (s.mode == TobModes.HARD) COUNTS_HARD[i] else COUNTS_NORMAL[i]
        s.every = EVERY[i]
        s.nextExhumed = npc.tick + FIRST_EXHUMED_MIN + npc.random(FIRST_EXHUMED_SPREAD)
        if (s.mode == TobModes.HARD) hardOuterPoison(npc, s)
    }

    private fun feeding(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        if (s.spawned < s.total && npc.tick >= s.nextExhumed) {
            s.spawned++
            s.nextExhumed = npc.tick + s.every
            var tile: CoordGrid
            do {
                tile =
                    CoordGrid(
                        s.home.x + MIN_X + npc.random(MAX_X - MIN_X + 1),
                        s.home.z + MIN_Z + npc.random(MAX_Z - MIN_Z + 1),
                        s.home.level,
                    )
            } while (TfaKit.under(npc, tile) && npc.random(GIVE_UP) != 0)
            val life = if (s.mode == TobModes.HARD) HARD_LIFE else LIFE
            s.exhumeds += Exhumed(tile, npc.tick + life, npc.tick + FIRST_ORB)
            npc.addLoc(Locs.TOB_XARPUS_EXHUMED, tile, FLOOR_SHAPE, 0, life)
        }
        val heal = HEAL[(raiders.size - 1).coerceIn(0, HEAL.size - 1)]
        val iter = s.exhumeds.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (npc.tick >= e.until) {
                iter.remove()
                s.lastClosed = npc.tick
                continue
            }
            if (npc.tick < e.firstOrb || raiders.any { it.coord == e.tile }) continue
            // Capture: an orb's heal lands floor(end / 30) + 1 after it (end 10 x distance).
            npc.spotAnimAt(e.tile, SpotAnims.TOB_XARPUS_EXHUMED_ENERGYORB)
            val d = maxOf(kotlin.math.abs(e.tile.x - npc.coord.x), kotlin.math.abs(e.tile.z - npc.coord.z))
            s.missed++
            npc.queueHit(ORB_CYCLES_PER_TILE * d / CYCLES_PER_TICK + 1, strong = false) {
                if (npc.isInWorld) npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + heal)
            }
        }
        val allClosed = s.spawned >= s.total && s.exhumeds.isEmpty()
        if (allClosed && s.lastClosed >= 0 && npc.tick - s.lastClosed >= TO_COMBAT) {
            s.phase = 2
            val hp = npc.hitpoints
            npc.transform(npc.type + 1, restoreStats = false)
            npc.teleport(CoordGrid(npc.coord.x - 1, npc.coord.z - 1, npc.coord.level))
            npc.hitpoints = hp
            s.lastSpit = npc.tick + FIRST_SPIT - SPIT_EVERY
        }
    }

    private fun scaleUp(s: State, base: Int): Int = base * (PERCENT + s.missed * MISSED_PERCENT) / PERCENT

    private fun fighting(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        // Capture: a raider under him takes two 3-7 hits the next tick.
        for (p in raiders) {
            if (!TfaKit.under(npc, p.coord)) continue
            npc.spotAnimAt(p.coord, SpotAnims.TOB_XARPUS_GUANO)
            repeat(
                2,
            ) { CombatSystem.queueHit(p, npc, TobModes.damage(s.mode, BossCombat.roll(npc, GUANO_MIN, GUANO_MAX)), HitStyle.TYPELESS, 1) }
        }
        val pct = npc.hitpoints * PER_MILLE / maxOf(1, npc.maxHitpoints)
        val stareAt = if (s.mode == TobModes.ENTRY) ENTRY_STARE_PERMILLE else STARE_PERMILLE
        if (s.phase == 2 && pct <= stareAt && npc.tick - s.lastSpit >= STARE_AFTER_SPIT) {
            s.phase = 3
            npc.say("Screeeeech!")
            for (p in raiders) p.message("Xarpus begins to stare intently.")
            s.quadrant = npc.random(QUADRANTS)
            s.turnAt = npc.tick + FIRST_TURN
        }
        if (s.phase == 3) {
            if (npc.tick >= s.turnAt && s.mode != TobModes.HARD) {
                s.quadrant = (s.quadrant + 1 + npc.random(QUADRANTS - 1)) % QUADRANTS
                s.turnAt = npc.tick + TURN_EVERY
            }
            return
        }
        if (npc.tick - s.lastSpit >= SPIT_EVERY && raiders.isNotEmpty()) {
            s.lastSpit = npc.tick
            spit(npc, s, raiders)
        }
    }

    /** XA2 / capture: the spit at the next raider in orb order, at their tile 2 ticks ago; splashes on to one or two more. */
    private fun spit(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        val order = npc[tp.plugin.SharedAttrs.RAID_ORDER].orEmpty()
        val sorted =
            raiders.sortedBy { p ->
                order.indexOfFirst { it.equals(p.username, ignoreCase = true) }.let {
                    if (it <
                        0
                    ) {
                        Int.MAX_VALUE
                    } else {
                        it
                    }
                }
            }
        val i = s.spits++ % sorted.size
        val target = sorted[i]
        val tile = locked(s, target)
        npc.anim(Seqs.TOB_XARPUS_ATTACK_RANGED)
        val d = maxOf(1, maxOf(kotlin.math.abs(tile.x - npc.coord.x), kotlin.math.abs(tile.z - npc.coord.z)))
        val land = (SPIT_BASE + SPIT_PER_TILE * d) / CYCLES_PER_TICK
        npc.projectile(target, SpotAnims.TOB_XARPUS_ACIDSPIT, PROJ_HEIGHT, 0, 0, SPIT_BASE + SPIT_PER_TILE * d)
        npc.queueHit(land, strong = false) {
            if (!npc.isInWorld) return@queueHit
            splash(npc, s, tile)
            val next = if (i == 0) 1 else 2
            val onward = sorted.drop(i + 1).take(next).ifEmpty { emptyList() }
            if (onward.isEmpty() && sorted.size <= 1) {
                splash(
                    npc,
                    s,
                    CoordGrid(
                        s.home.x + MIN_X + npc.random(MAX_X - MIN_X + 1),
                        s.home.z + MIN_Z + npc.random(MAX_Z - MIN_Z + 1),
                        s.home.level,
                    ),
                )
            }
            for (o in onward) {
                val t = locked(s, o)
                if (t == tile) continue
                npc.queueHit(SPLASH_ON, strong = false) { if (npc.isInWorld) splash(npc, s, t) }
            }
        }
    }

    private fun locked(s: State, p: PlayerRef): CoordGrid = s.history[p.username]?.firstOrNull() ?: p.coord

    private fun splash(npc: NpcRef, s: State, tile: CoordGrid) {
        npc.spotAnimAt(tile, SpotAnims.TOB_XARPUS_ACIDSPLASH)
        for (p in raiders(npc, s)) {
            if (kotlin.math.abs(p.coord.x - tile.x) > 1 || kotlin.math.abs(p.coord.z - tile.z) > 1) continue
            // XA2: the splash poisons anyone in its 3 x 3, starting at 2.
            Poison.poison(p, POISON_START * Poison.SEVERITY_PER_DAMAGE)
        }
        if (s.pools.add(tile)) npc.addLoc(Locs.TOB_XARPUS_ACIDPOOL, tile, FLOOR_SHAPE, 0)
    }

    /** XA2: a pool hurts 4-8 a tick (raised by the missed absorbs). */
    private fun pools(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        if (s.pools.isEmpty()) return
        for (p in raiders) {
            if (p.coord in
                s.pools
            ) {
                CombatSystem.applyHit(
                    p,
                    npc,
                    TobModes.damage(s.mode, scaleUp(s, BossCombat.roll(npc, POOL_MIN, POOL_MAX))),
                    HitStyle.TYPELESS,
                )
            }
        }
    }

    /** XA-Hard / OPEN-XA6: the two outer layers poisoned except the six entrance tiles (two rows of three, north). */
    private fun hardOuterPoison(npc: NpcRef, s: State) {
        for (x in MIN_X..MAX_X) {
            for (z in MIN_Z..MAX_Z) {
                val edge = minOf(x - MIN_X, MAX_X - x, z - MIN_Z, MAX_Z - z)
                if (edge >= OUTER_LAYERS) continue
                if (z >= MAX_Z - 1 && x in ENTRANCE_X) continue
                val t = CoordGrid(s.home.x + x, s.home.z + z, s.home.level)
                s.pools += t
                npc.addLoc(Locs.TOB_XARPUS_ACIDPOOL, t, FLOOR_SHAPE, 0)
            }
        }
    }

    private fun quadrantOf(npc: NpcRef, p: PlayerRef): Int {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val east = p.coord.x >= cx
        val north = p.coord.z >= cz
        return (if (north) 2 else 0) + if (east) 1 else 0
    }

    /** XA3: an attack from the stared quadrant is answered with 50-75 (raised by 40% of the absorbs' increase). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        // XA1: asleep and while he feeds he takes no damage.
        if (s.phase < 2) return 0
        val p = source as? PlayerRef ?: return damage
        if (s.phase != 3) return damage
        val q = quadrantOf(npc, p)
        if (s.mode == TobModes.HARD && q != s.quadrant) s.quadrant = q
        if (q == s.quadrant) {
            p.message("Xarpus is staring in your direction and retaliates with a powerful attack!")
            val base = BossCombat.roll(npc, COUNTER_MIN, COUNTER_MAX)
            val raised = base * (PERCENT + s.missed * MISSED_PERCENT * COUNTER_SHARE / PERCENT) / PERCENT
            CombatSystem.applyHit(p, npc, TobModes.damage(s.mode, raised), HitStyle.TYPELESS)
        }
        return damage
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) = Unit

    override fun died(npc: NpcRef, killer: EntityRef?) {
        npc.anim(Seqs.TOB_XARPUS_DEATH_A)
    }

    /** Capture (solo .. four) and RuneLite (five, Hard): exhumeds a phase by party size 1..5. */
    private val COUNTS_NORMAL = intArrayOf(7, 9, 12, 15, 18)
    private val COUNTS_HARD = intArrayOf(9, 13, 16, 20, 24)

    /** Capture: ticks between exhumeds by party size (OPEN-XA1: five as four). */
    private val EVERY = intArrayOf(12, 8, 8, 4, 4)

    /** Capture: an orb's heal by party size (OPEN-XA4: five as four). */
    private val HEAL = intArrayOf(20, 16, 12, 9, 9)
    private val ENTRANCE_X = -1..1

    private const val MIN_X = -5
    private const val MAX_X = 7
    private const val MIN_Z = -6
    private const val MAX_Z = 8
    private const val HISTORY = 3
    private const val START_PERCENT = 75
    private const val PERCENT = 100
    private const val PER_MILLE = 1000
    private const val FIRST_EXHUMED_MIN = 8
    private const val FIRST_EXHUMED_SPREAD = 4
    private const val GIVE_UP = 20
    private const val LIFE = 11
    private const val HARD_LIFE = 9
    private const val FIRST_ORB = 3
    private const val ORB_CYCLES_PER_TILE = 10
    private const val CYCLES_PER_TICK = 30
    private const val TO_COMBAT = 8
    private const val FIRST_SPIT = 9
    private const val SPIT_EVERY = 4
    private const val SPIT_BASE = 30
    private const val SPIT_PER_TILE = 30
    private const val SPLASH_ON = 1
    private const val PROJ_HEIGHT = 90
    private const val POISON_START = 2
    private const val POOL_MIN = 4
    private const val POOL_MAX = 8

    /** OPEN-XA4: each missed absorb raises poison and counter damage by 1%. */
    private const val MISSED_PERCENT = 1
    private const val COUNTER_SHARE = 40
    private const val GUANO_MIN = 3
    private const val GUANO_MAX = 7
    private const val STARE_PERMILLE = 250
    private const val ENTRY_STARE_PERMILLE = 225
    private const val STARE_AFTER_SPIT = 3
    private const val QUADRANTS = 4
    private const val FIRST_TURN = 9
    private const val TURN_EVERY = 8
    private const val COUNTER_MIN = 50
    private const val COUNTER_MAX = 75
    private const val OUTER_LAYERS = 2
    private const val FLOOR_SHAPE = 22
}

/** Client contract 4: Hard Xarpus static 10770 and feeding 10771. */
private const val XARPUS_HARD_STATIC = 10770
private const val XARPUS_HARD_FEEDING = 10771
