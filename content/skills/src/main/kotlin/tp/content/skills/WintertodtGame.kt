package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.DamageRequest
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Wintertodt's shared game of one world (docs/mechanics/wintertodt.md WT1 to WT12): the round clock, the energy
 * meter, the four braziers and pyromancers, and the storm's attacks. It advances once per tick, driven by whichever
 * player in the prison runs its timer first ([advance]); with nobody inside the game is reset on the next entry
 * (OPEN-WT1), so no empty world is simulated.
 */
internal class WintertodtGame {
    class Brazier(val x: Int, val z: Int) {
        var state: Int = UNLIT
        var generation: Int = 0
        var warnAt: Long = NONE
        var warnHeavy: Boolean = false

        val coord: CoordGrid get() = CoordGrid(x, z, 0)
    }

    class Pyromancer(val at: CoordGrid) {
        var npc: NpcRef? = null
        var hp: Int = PYRO_MAX_HP
        var nextBolt: Long = NONE
        var warnAt: Long = NONE
    }

    /** A 3x3 snow warning centred on [centre], landing at tick [at]. */
    class AreaWarning(val centre: CoordGrid, val at: Long)

    var active: Boolean = false
    var startAt: Long = 0
    var energy: Int = ENERGY_MAX
    private var lastAdvance: Long = NONE
    private var restoreAt: Long = NONE
    private var nextAttackCheck: Long = NONE
    private var nextAreaCheck: Long = NONE
    private var area: AreaWarning? = null
    val players = ArrayList<PlayerRef>()
    val braziers = Array(BRAZIER_TILES.size) { Brazier(BRAZIER_TILES[it].first, BRAZIER_TILES[it].second) }
    val pyromancers = Array(PYRO_TILES.size) { Pyromancer(PYRO_TILES[it]) }

    val litBraziers: Int get() = braziers.count { it.state == LIT }

    /** Resets the game to its lobby countdown (first entry, or every round end). */
    fun reset(anchor: PlayerRef) {
        active = false
        startAt = anchor.tick + ROUND_WAIT_TICKS
        energy = ENERGY_MAX
        area = null
        restoreAt = NONE
        for (b in braziers) setBrazier(anchor, b, UNLIT)
        for (p in pyromancers) {
            p.hp = PYRO_MAX_HP
            p.warnAt = NONE
            p.nextBolt = NONE
            val npc = p.npc ?: anchor.spawnNpc(Npcs.WINT_WIZARD, p.at, 0).also { p.npc = it }
            if (npc.type != Npcs.WINT_WIZARD) npc.transform(Npcs.WINT_WIZARD, true)
        }
    }

    /** Puts the brazier loc for [state] on its tile (the cache locs `wint_brazier`, `_broken`, `_lit`). */
    fun setBrazier(anchor: PlayerRef, b: Brazier, state: Int) {
        if (b.state != state) b.generation++
        b.state = state
        val loc =
            when (state) {
                LIT -> Locs.WINT_BRAZIER_LIT
                BROKEN -> Locs.WINT_BRAZIER_BROKEN
                else -> Locs.WINT_BRAZIER
            }
        anchor.addLoc(loc, b.coord, BRAZIER_SHAPE, 0)
        if (state != LIT && litBraziers == 0) restoreAt = anchor.tick + anchor.random(RESTORE_TICKS)
    }

    /** Hands a pyromancer back to the fight after a relight or a heal: a short random pause before the next bolt (WT3). */
    fun resumeBolts(anchor: PlayerRef, index: Int) {
        pyromancers[index].nextBolt = anchor.tick + RESUME_MIN_TICKS + anchor.random(RESUME_SPREAD_TICKS)
    }

    fun heal(anchor: PlayerRef, index: Int) {
        val p = pyromancers[index]
        p.hp = PYRO_MAX_HP
        p.npc?.transform(Npcs.WINT_WIZARD, true)
        resumeBolts(anchor, index)
    }

    /** Runs the game for [tick]; [anchor] is any player inside (world calls go through it). True when the round was subdued. */
    fun advance(anchor: PlayerRef, tick: Long): Boolean {
        if (tick == lastAdvance) return false
        lastAdvance = tick
        if (!active) {
            if (tick >= startAt) start(tick)
            return false
        }
        bolts(tick)
        if (energy <= 0) return true
        restore(tick)
        attacks(anchor, tick)
        return false
    }

    private fun start(tick: Long) {
        active = true
        energy = ENERGY_MAX
        nextAttackCheck = tick + ATTACK_CHECK_TICKS
        nextAreaCheck = tick + AREA_CHECK_TICKS
        restoreAt = NONE
        for (i in braziers.indices) pyromancers[i].nextBolt = NONE
    }

    /** WT3: each healthy pyromancer at a lit brazier takes 1 energy every [BOLT_TICKS] (Wiki "Wintertodt/Strategies"). */
    private fun bolts(tick: Long) {
        for (i in braziers.indices) {
            val pyro = pyromancers[i]
            if (braziers[i].state != LIT || !ready(pyro, tick)) continue
            energy = (energy - 1).coerceAtLeast(0)
            pyro.nextBolt = tick + BOLT_TICKS
        }
    }

    private fun ready(pyro: Pyromancer, tick: Long): Boolean = pyro.hp > 0 && pyro.nextBolt != NONE && tick >= pyro.nextBolt

    /** WT3: with every brazier out the storm regains 1 energy every [RESTORE_TICKS], after a random first delay. */
    private fun restore(tick: Long) {
        if (litBraziers > 0 || restoreAt == NONE || tick < restoreAt) return
        energy = (energy + 1).coerceAtMost(ENERGY_MAX)
        restoreAt = tick + RESTORE_TICKS
    }

    private fun attacks(anchor: PlayerRef, tick: Long) {
        resolveWarnings(anchor, tick)
        if (tick >= nextAttackCheck) {
            nextAttackCheck = tick + ATTACK_CHECK_TICKS
            // WT4: the roll is a number up to the maximum energy and the attack goes through when it is above the energy left.
            if (anchor.random(ENERGY_MAX) >= energy) warnBrazierOrPyromancer(anchor, tick)
        }
        if (tick >= nextAreaCheck) {
            nextAreaCheck = tick + AREA_CHECK_TICKS
            warnArea(anchor, tick)
        }
    }

    private fun warnBrazierOrPyromancer(anchor: PlayerRef, tick: Long) {
        val pick = anchor.random(braziers.size * 2)
        val i = pick / 2
        if (pick % 2 == 0) {
            val b = braziers[i]
            if (b.state != LIT || b.warnAt != NONE) return
            b.warnAt = tick + WARN_TICKS
            b.warnHeavy = anchor.random(2) == 0
            anchor.spotAnimAt(b.coord, SpotAnims.WINT_AREA_ATTACK_SPOT)
        } else {
            val p = pyromancers[i]
            if (p.hp <= 0 || p.warnAt != NONE) return
            p.warnAt = tick + WARN_TICKS
            anchor.spotAnimAt(p.at, SpotAnims.WINT_AREA_ATTACK_SPOT)
        }
    }

    /** WT4: a player who is outside the safe area and has no obstacle next to them is picked for the 3x3 snowfall. */
    private fun warnArea(anchor: PlayerRef, tick: Long) {
        if (area != null) return
        val candidates = players.filter { !isSafe(it.coord) && openAround(it) }
        if (candidates.isEmpty()) return
        val target = candidates[anchor.random(candidates.size)]
        area = AreaWarning(target.coord, tick + WARN_TICKS)
        for (dx in -1..1) {
            for (dz in -1..1) {
                anchor.spotAnimAt(
                    CoordGrid(target.coord.x + dx, target.coord.z + dz, 0),
                    SpotAnims.WINT_AREA_ATTACK_SPOT,
                )
            }
        }
    }

    private fun openAround(p: PlayerRef): Boolean =
        AROUND.all { (dx, dz) -> p.isWalkable(CoordGrid(p.coord.x + dx, p.coord.z + dz, p.coord.level)) }

    private fun resolveWarnings(anchor: PlayerRef, tick: Long) {
        resolveBraziers(anchor, tick)
        resolvePyromancers(tick)
        resolveArea(anchor, tick)
    }

    private fun resolveBraziers(anchor: PlayerRef, tick: Long) {
        for (b in braziers) {
            if (b.warnAt == NONE || tick < b.warnAt) continue
            b.warnAt = NONE
            if (b.state != LIT) continue
            if (b.warnHeavy) {
                setBrazier(anchor, b, BROKEN)
                for (p in players) if (nextToBrazier(b, p.coord)) cold(p, brazierDamage(p))
            } else {
                setBrazier(anchor, b, UNLIT)
            }
        }
    }

    private fun resolvePyromancers(tick: Long) {
        for (pyro in pyromancers) {
            if (pyro.warnAt == NONE || tick < pyro.warnAt) continue
            pyro.warnAt = NONE
            if (pyro.hp <= 0) continue
            pyro.hp = (pyro.hp - PYRO_HIT).coerceAtLeast(0)
            if (pyro.hp == 0) pyro.npc?.transform(Npcs.WINT_WIZARD_DOWN, true)
        }
    }

    private fun resolveArea(anchor: PlayerRef, tick: Long) {
        val a = area ?: return
        if (tick < a.at) return
        area = null
        anchor.spotAnimAt(a.centre, SpotAnims.WINT_AREA_ATTACK_IMPACT)
        for (p in players) {
            val c = p.coord
            if (kotlin.math.abs(c.x - a.centre.x) <= 1 && kotlin.math.abs(c.z - a.centre.z) <= 1) cold(p, areaDamage(p))
        }
    }

    /** Players standing directly next to the 3x3 brazier when it shatters. */
    private fun nextToBrazier(b: Brazier, c: CoordGrid): Boolean {
        val inside = c.x in b.x until b.x + BRAZIER_SIZE && c.z in b.z until b.z + BRAZIER_SIZE
        return !inside && c.x in b.x - 1..b.x + BRAZIER_SIZE && c.z in b.z - 1..b.z + BRAZIER_SIZE
    }

    fun standardDamage(p: PlayerRef): Int =
        (STANDARD_BASE - warmItems(p) - minOf(2 * litBraziers, STANDARD_BRAZIER_CAP)) * PERCENT / fmLevel(p)

    private fun brazierDamage(p: PlayerRef): Int = BRAZIER_MULTIPLIER * ((SPECIAL_BASE - warmItems(p)) * PERCENT / fmLevel(p))

    private fun areaDamage(p: PlayerRef): Int = AREA_MULTIPLIER * ((SPECIAL_BASE - warmItems(p)) * PERCENT / fmLevel(p))

    /** Obj ids of warm clothing (Wiki "Warmth"), found by cache name when the world starts. */
    var warmIds: Set<Int> = emptySet()

    /** Wiki "Warmth": the number of worn warm items, at most 4 count. */
    fun warmClothing(p: PlayerRef): Int {
        val worn = p.inv(Invs.WORN)
        var count = 0
        for (slot in 0 until worn.size) if (worn.obj(slot) in warmIds) count++
        return minOf(count, WARM_CAP)
    }

    private fun warmItems(p: PlayerRef): Int = warmClothing(p)

    /**
     * Takes [percent] of [p]'s warmth (WT5). The warmth meter replaces hitpoints in the prison (Wiki "Warmth"), so only a
     * cold hitsplat shows; at 0% the player dies through the combat module.
     */
    fun cold(p: PlayerRef, percent: Int) {
        if (percent <= 0) return
        val left = ((p[WARMTH] ?: WARMTH_FULL) - percent * WARMTH_PER_PERCENT).coerceAtLeast(0)
        p[WARMTH] = left
        p[HIT_AT] = p.tick
        p.setVarbit(Varbits.WINT_WARMTH, left)
        p.hitsplat(null, percent, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_TINT)
        if (left == 0) kill(p)
    }

    private fun fmLevel(p: PlayerRef): Int = p.baseLevel(Stats.FIREMAKING).coerceAtLeast(1)

    /** Kills [p] through the combat module (warmth reached 0%, Wiki "Warmth"). */
    fun kill(p: PlayerRef) {
        val request = DamageRequest(p, null, p.hitpoints)
        p.events.publish(request)
        if (!request.handled) p.hitpoints = 0
    }

    companion object {
        const val UNLIT = 0
        const val LIT = 1
        const val BROKEN = 2
        const val NONE = -1L

        /** Cache `wint_brazier` locs (3x3, shape 10, angle 0) at their south-west corners (map square 25,62). */
        val BRAZIER_TILES = listOf(1620 to 3997, 1620 to 4015, 1638 to 3997, 1638 to 4015)

        /** The pyromancers stand diagonally outside each brazier's corner (Wiki "Pyromancer" map pins), in [BRAZIER_TILES] order. */
        val PYRO_TILES = listOf(CoordGrid(1619, 3996, 0), CoordGrid(1619, 4018, 0), CoordGrid(1641, 3996, 0), CoordGrid(1641, 4018, 0))

        const val BRAZIER_SHAPE = 10
        const val BRAZIER_SIZE = 3

        /** Wiki "Wintertodt/Strategies": 1% energy per bolt and 100% energy at the start. */
        const val ENERGY_MAX = 100

        /** Wiki "Wintertodt/Strategies": a pyromancer fires a bolt every 14 ticks. */
        const val BOLT_TICKS = 14

        /** Wiki: with every brazier out the storm regains 1% every 35 ticks. */
        const val RESTORE_TICKS = 35

        /** Wiki: "roughly 1-5 seconds" (2 to 8 ticks) before a relit or healed pyromancer attacks again. */
        const val RESUME_MIN_TICKS = 2
        const val RESUME_SPREAD_TICKS = 7

        /** Wiki "Pyromancer": 14 health, incapacitated after two hits (7 each). */
        const val PYRO_MAX_HP = 14
        const val PYRO_HIT = 7

        /** Wiki "Wintertodt" Changes (13 Sep 2023) and Strategies: a minute between rounds. */
        const val ROUND_WAIT_TICKS = 100

        /** OPEN-WT3: attack rolls and the warning delay are not published; these are the named placeholders. */
        const val ATTACK_CHECK_TICKS = 8
        const val AREA_CHECK_TICKS = 20
        const val WARN_TICKS = 4

        /** Wiki damage formulas (Mod Ed, via "Wintertodt/Strategies"): standard 16, brazier and area 10, multipliers 1, 2, 3. */
        const val STANDARD_BASE = 16
        const val STANDARD_BRAZIER_CAP = 6
        const val SPECIAL_BASE = 10
        const val BRAZIER_MULTIPLIER = 2
        const val AREA_MULTIPLIER = 3
        const val PERCENT = 100

        private val AROUND = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

        /** Wiki "Warmth": at most four warm items count. */
        const val WARM_CAP = 4

        /**
         * OPEN-WT2: the warmth varbit (`wint_warmth`, 10 bits) is treated as tenths of a percent, so 1000 is full; the client
         * script that draws the bar is not decoded.
         */
        const val WARMTH_PER_PERCENT = 10
        const val WARMTH_FULL = 100 * WARMTH_PER_PERCENT

        /** Generic damage hitmarks, as `Pickpocketing` uses: no cold hitsplat id is sourced (OPEN-WT2). */
        const val HITMARK_DAMAGE_LIT = 16
        const val HITMARK_DAMAGE_TINT = 17

        /** Wiki "Wintertodt/Strategies": the safe area is the lobby (OPEN-WT1: x 1624..1636, z 3965..3987 is an estimate). */
        fun isSafe(c: CoordGrid): Boolean = c.x in SAFE_X && c.z in SAFE_Z

        /** The prison interior from the cache locs (storm at 1627,4004; walls near x 1611 and 1649); OPEN-WT1: an estimate. */
        fun inside(c: CoordGrid): Boolean = c.level == 0 && c.x in PRISON_X && c.z in PRISON_Z

        private val SAFE_X = 1624..1636
        private val SAFE_Z = 3965..3987
        val PRISON_X = 1604..1652
        val PRISON_Z = 3965..4030

        val GAME = AttrKey<WintertodtGame>("wintertodt.game")
        val WARMTH = AttrKey<Int>("wintertodt.warmth")
        val POINTS = AttrKey<Int>("wintertodt.points")
        val HIT_AT = AttrKey<Long>("wintertodt.hit_at")
    }
}
