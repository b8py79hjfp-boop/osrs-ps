package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Path of Crondis (puzzles.md CR1-CR11): fill water containers at the four waterfalls (Fill) and water the Palm of
 * Resourcefulness (Water) until it is fully grown. Traps hurt, drain stats and halve the container; crocodiles spawn
 * periodically. TODO hooks for the main session (crocodile AI): [palmDamaged] (a bite shrinks the palm) and
 * [containerBitten] (a bite costs the carrier's water); the crocodiles' targeting (CR6) and bite damage (CR7) are not here.
 */
internal object ToaCrondis {
    private const val ROOM = "crondis"
    private const val OP1 = 1

    private class State(val startTick: Long, val required: Int) {
        var palm: NpcRef? = null
        var water = 0
        var done = false
        var nextCroc = 0L
        val crocs = ArrayList<NpcRef>()

        /** Units in each raider's container (lower case name). */
        val container = HashMap<String, Int>()

        /** Instance tile -> trap phase, and the tiles that only warn (the lighter cloud). */
        val traps = HashMap<Int, Int>()

        /** CR7: the ticks of each raider's last trap hits. */
        val trapHits = HashMap<String, ArrayList<Long>>()
    }

    private val store = RunStore<State>("toa_crondis")

    /** CR8 / OPEN-PZ37: the palm's water survives a wipe's retry (the doc says progress is saved), as a number per run. */
    private val saved = RunStore<IntArray>("toa_crondis_saved")

    fun register(scope: ContentScope) {
        with(scope) {
            onOpLoc(Locs.TOA_CRONDIS_WATER_SOURCE, OP1) { loc -> fill(player, loc) }
            onOpLoc(Locs.TOA_CRONDIS_WATER_SOURCE_EMPTY, OP1) { player.message("The waterfall has run dry for now.") }
            for (tree in TREES.take(TREES.size - 1)) onOpNpc(tree, OP1) { palm -> water(player, palm) }
            onItemOp(Objs.TOA_CRONDIS_WATER_CONTAINER, "Check") { check(this) }
        }
    }

    /** CR2 (Strat, before the 25 Jun 2025 change; OPEN-PZ18): 175 units plus 125 for each further player. */
    fun required(partySize: Int): Int = WATER_BASE + ToaConfig.crondisWaterPerPlayer * (partySize - 1).coerceAtLeast(0)

    fun start(run: RaidRun, any: EntityRef) {
        val s = State(any.tick, required(run.scaling.partySize))
        s.water = if (ToaConfig.crondisKeepGrowthOnWipe) saved[any, run]?.get(0) ?: 0 else 0
        s.nextCroc = any.tick + ToaConfig.crondisCrocSpawnTicks
        store[any, run] = s
        scanTraps(run, any, s)
        s.palm = ToaRooms.spawn(any, run, ROOM, stageType(s), PALM_AT)
        for (p in run.presentPlayers(any)) {
            // CR3: a container per raider (OPEN-PZ19: the ground pick-up tiles are not documented).
            s.container[p.username.lowercase()] = 0
            if (ToaPuzzles.count(p, Objs.TOA_CRONDIS_WATER_CONTAINER) == 0) {
                ToaPuzzles.give(p, Objs.TOA_CRONDIS_WATER_CONTAINER, 1, "toa crondis container")
            }
        }
    }

    /** CR4: the spear rows (45416, 3 wide) and the damaging cloud tiles found in the instance, each with a phase. */
    private fun scanTraps(run: RaidRun, any: EntityRef, s: State) {
        for (x in AREA_X0..AREA_X1) {
            for (z in AREA_Z0..AREA_Z1) {
                val at = run.at(CoordGrid(x, z))
                val spear = any.locAt(at, SHAPE_OBJECT)
                if (spear != null && spear.id == Locs.TOA_CRONDIS_ROW_TRAP_FIRE) {
                    for (dx in 0 until SPEAR_WIDTH) s.traps[ToaPuzzles.key(CoordGrid(at.x + dx, at.z, at.level))] = z / 2
                }
                val cloud = any.locAt(at, SHAPE_GROUND)
                if (cloud != null && cloud.id == ToaConfig.crondisDarkCloudLoc) s.traps[ToaPuzzles.key(at)] = x + z
            }
        }
    }

    fun tick(run: RaidRun, any: EntityRef) {
        val s = store[any, run] ?: return
        if (s.done) return
        val now = any.tick
        val players = ToaPuzzles.alive(run, any)
        val period = ToaConfig.crondisTrapPeriodTicks
        for (p in players) {
            val phase = s.traps[ToaPuzzles.key(p.coord)] ?: continue
            if ((now + phase).mod(period) < ToaConfig.crondisTrapActiveTicks) trapHit(s, p, now)
        }
        s.crocs.removeAll { !it.isInWorld }
        if (now >= s.nextCroc) {
            s.nextCroc = now + ToaConfig.crondisCrocSpawnTicks
            spawnCrocs(run, any, s)
        }
    }

    /** CR4: a trap hit halves the container (16% case instead), lowers Defence 6, Agility 3, Prayer 2, and hurts. */
    private fun trapHit(s: State, p: PlayerRef, now: Long) {
        val name = p.username.lowercase()
        s.trapHits.getOrPut(name) { ArrayList() }.add(now)
        loseWater(s, name, small = p.random(PERCENT) < ToaConfig.crondisSmallLossPercent)
        drain(p, Stats.DEFENCE, DEFENCE_DRAIN)
        drain(p, Stats.AGILITY, AGILITY_DRAIN)
        drain(p, Stats.PRAYER, PRAYER_DRAIN)
        ToaPuzzles.hit(p, ToaConfig.crondisTrapDamage)
    }

    private fun drain(p: PlayerRef, stat: Int, by: Int) {
        p.setLevel(stat, (p.level(stat) - by).coerceAtLeast(0))
    }

    /** CR4: 50% of the current amount (16% in the small case), never below 1%. */
    private fun loseWater(s: State, name: String, small: Boolean) {
        val have = s.container[name] ?: return
        if (have <= 0) return
        val lost = have * (if (small) SMALL_LOSS else HALF_LOSS) / PERCENT
        s.container[name] = (have - lost).coerceAtLeast(MIN_WATER).coerceAtMost(have)
    }

    /** CR7: the trap hits [name] took in the last 50 ticks (the crocodile's bite adds 3 each, up to 36). */
    fun recentTrapHits(run: RaidRun, any: EntityRef, name: String): Int {
        val s = store[any, run] ?: return 0
        val list = s.trapHits[name.lowercase()] ?: return 0
        list.removeAll { any.tick - it >= TRAP_MEMORY }
        return list.size
    }

    private fun spawnCrocs(run: RaidRun, any: EntityRef, s: State) {
        // CR5 (OPEN-PZ21): one per corner per period, at most eight alive.
        for (corner in CROC_CORNERS) {
            if (s.crocs.size >= MAX_CROCS) return
            val croc = ToaRooms.spawn(any, run, ROOM, Npcs.TOA_CRONDIS_CROCODILE, corner)
            croc.huntRange = CROC_HUNT
            s.crocs += croc
        }
    }

    // ---- CR3 water

    private fun fill(player: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if (ToaPuzzles.count(player, Objs.TOA_CRONDIS_WATER_CONTAINER) == 0) {
            player.message("You need a water container.")
            return
        }
        player.anim(FILL_ANIM)
        s.container[player.username.lowercase()] = CAPACITY
        // CR3 (OPEN-PZ19): the waterfall runs dry, then comes back (the loc reverts after the delay).
        player.addLoc(Locs.TOA_CRONDIS_WATER_SOURCE_EMPTY, loc.coord, loc.shape, loc.angle, ToaConfig.crondisRefillTicks)
    }

    private fun water(player: PlayerRef, palm: NpcRef) {
        val run = ToaFlow.runOf(player) ?: return
        val s = store[player, run] ?: return
        if (s.done) return
        val name = player.username.lowercase()
        val units = s.container[name] ?: 0
        if (units <= 0 || ToaPuzzles.count(player, Objs.TOA_CRONDIS_WATER_CONTAINER) == 0) {
            player.message("Your water container is empty.")
            return
        }
        player.anim(FILL_ANIM)
        s.container[name] = 0
        palm.hitsplat(player, units, HITMARK_UP, HITMARK_UP, HITMARK_UP)
        grow(run, player, s, units)
    }

    private fun grow(run: RaidRun, any: EntityRef, s: State, by: Int) {
        s.water = (s.water + by).coerceIn(0, s.required)
        saved[any, run] = intArrayOf(s.water)
        refreshPalm(s)
        if (s.water >= s.required) {
            s.done = true
            for (c in s.crocs) if (c.isInWorld) c.despawn()
            ToaFlow.roomDone(any, run, ROOM)
        }
    }

    /** CR2: growth stage 0 / 25 / 50 / 75 / 100% of the required water (OPEN-PZ18: derived from the stage names). */
    private fun stageType(s: State): Int = TREES[(s.water * (TREES.size - 1) / s.required).coerceIn(0, TREES.size - 1)]

    private fun refreshPalm(s: State) {
        val palm = s.palm ?: return
        val type = stageType(s)
        if (palm.type != type) palm.transform(type, restoreStats = false)
    }

    /** TODO hook (crocodile AI, CR7): a bite shrinks the palm by [amount] units; also reports the Perfect Crondis loss. */
    fun palmDamaged(run: RaidRun, any: EntityRef, amount: Int) {
        val s = store[any, run] ?: return
        if (s.done) return
        s.water = (s.water - amount).coerceAtLeast(0)
        saved[any, run] = intArrayOf(s.water)
        refreshPalm(s)
    }

    /** TODO hook (crocodile AI, CR4 / CR7): a bite costs the carrier 50% of the container, 10% once it is under 15%. */
    fun containerBitten(run: RaidRun, any: EntityRef, name: String) {
        val s = store[any, run] ?: return
        val have = s.container[name.lowercase()] ?: return
        val lost = have * (if (have < LOW_CONTAINER) LOW_BITE else HALF_LOSS) / PERCENT
        s.container[name.lowercase()] = (have - lost).coerceAtLeast(MIN_WATER).coerceAtMost(have)
    }

    private fun check(player: PlayerRef) {
        val run = ToaFlow.runOf(player) ?: return
        val units = store[player, run]?.container?.get(player.username.lowercase()) ?: 0
        player.message("Your water container is $units% full.")
    }

    private val TREES =
        listOf(
            Npcs.TOA_CRONDIS_TREE_1,
            Npcs.TOA_CRONDIS_TREE_2,
            Npcs.TOA_CRONDIS_TREE_3,
            Npcs.TOA_CRONDIS_TREE_4,
            Npcs.TOA_CRONDIS_TREE_5,
        )

    /** Data: the palm's 5x5 (MT interaction ring: x 30-34, y 30-34 of region (61, 82)); south-west tile. */
    private val PALM_AT = CoordGrid(3934, 5278)

    /** OPEN-PZ21: the crocodiles' spawn points (the room's four corners next to the waterfalls; not documented). */
    private val CROC_CORNERS = listOf(CoordGrid(3925, 5253), CoordGrid(3945, 5253), CoordGrid(3925, 5307), CoordGrid(3945, 5307))

    private const val AREA_X0 = 3920
    private const val AREA_X1 = 3956
    private const val AREA_Z0 = 5250
    private const val AREA_Z1 = 5310
    private const val SHAPE_OBJECT = 10
    private const val SHAPE_GROUND = 22

    /** Data: the spear loc 45416 is 3 wide (cache). */
    private const val SPEAR_WIDTH = 3

    /** CR2: units for one solo palm (Strat). */
    private const val WATER_BASE = 175

    /** CR2 (inferred from MT's hitsplat cap): a full container holds 100 units. */
    private const val CAPACITY = 100
    private const val PERCENT = 100

    /** CR4: 50% lost to a hit, 16% in the small case, at least 1% stays. */
    private const val HALF_LOSS = 50
    private const val SMALL_LOSS = 16
    private const val MIN_WATER = 1

    /** CR4: a crocodile bite takes 10% once the container is under 15%. */
    private const val LOW_CONTAINER = 15
    private const val LOW_BITE = 10

    /** CR4: Defence -6, Agility -3, Prayer -2 per trap hit. */
    private const val DEFENCE_DRAIN = 6
    private const val AGILITY_DRAIN = 3
    private const val PRAYER_DRAIN = 2

    /** CR7: trap hits count for 30 s = 50 ticks. */
    private const val TRAP_MEMORY = 50L

    /** CR5: at most eight crocodiles alive. */
    private const val MAX_CROCS = 8

    /** OPEN-PZ22: crocodile aggression range (not documented). */
    private const val CROC_HUNT = 16

    /** MT: palm hitsplat type 11 (water up). */
    private const val HITMARK_UP = 11

    /** MT: the emptying / filling animation 827. */
    private const val FILL_ANIM = tp.symbols.Seqs.HUMAN_PICKUPFLOOR
}
