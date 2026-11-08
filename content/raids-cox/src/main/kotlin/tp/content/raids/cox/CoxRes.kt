package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.util.CoordGrid

/** A herb patch with a plant in it (resources.md FM3). */
internal class CoxPatch(val herb: Int, val plantedTick: Long, val shape: Int, val angle: Int, var remaining: Int) {
    var stage: Int = 0
}

/** A large scavenger room's shortcut (resources.md SC5): which kind, the level it asks for, and whether it is cleared. */
internal class CoxShortcut(val kind: Int, val level: Int) {
    var done: Boolean = false
}

/** A fishing room (resources.md FD2): the three spots and the cave snake that sits by one of them. */
internal class CoxFishRoom(val spots: List<CoordGrid>, val snakeTiles: List<CoordGrid>, val snake: NpcRef) {
    var snakeAt: Int = 0
    var swapTick: Long = 0
    var biteTick: Long = 0
}

/** A bat room (resources.md FD3): the bats and the floor they fly over. */
internal class CoxBatRoom(val bats: List<NpcRef>, val floor: List<CoordGrid>) {
    var moveTick: Long = 0
}

/**
 * A run's resource-room state (scavenger shortcuts, patches, fishing and bat rooms) and its two ledgers of who made which
 * food and potions. World-scoped (a world attribute keyed by run id), not a global: [CoxRes.tick] drops the entries of
 * runs that no longer exist.
 */
internal class CoxResState {
    val patches = HashMap<CoordGrid, CoxPatch>()
    val shortcuts = HashMap<CoordGrid, CoxShortcut>()
    val fishRooms = ArrayList<CoxFishRoom>()
    val batRooms = ArrayList<CoxBatRoom>()

    /** FD6 approximation: cooked food obj -> the cooks in the order they made it (the oldest is credited first). */
    val cooks = HashMap<Int, ArrayDeque<String>>()

    /** HL10 approximation: potion family (kind and tier) -> the makers, one entry per dose. */
    val makers = HashMap<Int, ArrayDeque<String>>()

    /** FD6: pieces of food eaten per cook, for the soft and hard caps. */
    val foodEaten = HashMap<String, Int>()

    /** HL10: doses drunk per maker, for the dose cap. */
    val dosesDrunk = HashMap<String, Int>()
}

/** Resource-room plumbing: the per-run state, the ledgers and the one world-tick driver. */
internal object CoxRes {
    private val STATE = AttrKey<HashMap<Long, CoxResState>>("cox_resources")

    /** Runs are checked for removal this often (ticks), not every tick. */
    private const val PRUNE_TICKS = 600L

    fun state(any: EntityRef, run: RaidRun): CoxResState {
        val map = any.worldAttr(STATE) ?: HashMap<Long, CoxResState>().also { any.setWorldAttr(STATE, it) }
        return map.getOrPut(run.id.value) { CoxResState() }
    }

    /** Adds [count] entries of [name] under [key] of a ledger. */
    fun push(ledger: HashMap<Int, ArrayDeque<String>>, key: Int, name: String, count: Int) {
        val queue = ledger.getOrPut(key) { ArrayDeque() }
        repeat(count) { queue.addLast(name) }
    }

    /** The oldest entry under [key], removed, or null. */
    fun pop(ledger: HashMap<Int, ArrayDeque<String>>, key: Int): String? = ledger[key]?.removeFirstOrNull()

    /** The team's average base level of [stat] at the start of the raid (SC5 / CX20; 0 when the sums are unknown). */
    fun teamAverage(run: RaidRun, stat: Int): Int = run.scaling.skillSums.getOrElse(stat) { 0 } / maxOf(1, run.scaling.partySize)

    /** Total of [obj] in the player's backpack. */
    fun held(player: tp.plugin.PlayerRef, obj: Int): Long = player.inv(Invs.INV).total(obj)

    fun tick(world: WorldRef) {
        val map = world.worldAttr(STATE) ?: return
        if (map.isEmpty()) return
        val now = world.tick
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != CoxRaid.ID) continue
            val st = map[run.id.value] ?: continue
            CoxFarming.tick(world, st, now)
            CoxFood.tick(run, st, now)
        }
        if (now % PRUNE_TICKS == 0L) {
            val live =
                Raids
                    .of(world)
                    .activeRuns
                    .map { it.id.value }
                    .toSet()
            map.keys.removeIf { it !in live }
        }
    }
}
