package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.util.CoordGrid
import kotlin.math.abs

/**
 * The scavenger rooms (resources.md SC1-SC5): the beasts that spawn when the first raider walks in, and in the large rooms
 * a shortcut (rotten sapling, rocks or boulder) that can be cleared for points. These rooms never hold the party back:
 * [CoxFlow.enterCell] marks a room with no combat or puzzle flag done at once. The beasts' drops come from content/drops.
 */
internal object CoxScavengers {
    /** One shortcut kind (SC5): the intact and cleared locs, the skill that clears it and its animation. */
    private class Kind(val intact: Int, val cleared: Int, val stat: Int, val verb: String, val anim: Int)

    /** Client-contract 5.1: sapling (Chop) -> stump (Cross), rocks (Mine) -> rubble (Cross), boulder (Push), which leaves no loc. */
    private val KINDS =
        listOf(
            Kind(
                Locs.RAIDS_CORRIDOR_ROOTS,
                Locs.RAIDS_CORRIDOR_ROOTS_CLEARED,
                Stats.WOODCUTTING,
                "chop it down",
                Seqs.HUMAN_WOODCUTTING_BRONZE_AXE,
            ),
            Kind(Locs.RAIDS_CORRIDOR_ROCKS, Locs.RAIDS_CORRIDOR_ROCKS_CLEARED, Stats.MINING, "mine it", Seqs.HUMAN_MINING_BRONZE_PICKAXE),
            Kind(Locs.RAIDS_CORRIDOR_BOULDER, NO_LOC, Stats.STRENGTH, "push it", Seqs.HUMAN_PUSH),
        )

    /** The cleared form of the boulder: none (the loc is removed). */
    private const val NO_LOC = -1

    /** Cache: the scavenger beast (npcs 7548 / 7549) is size 2. */
    private const val BEAST_SIZE = 2

    /**
     * OPEN-RS2: the doc says the number of scavengers scales with the party but not how. Two beasts, one more for every
     * [PLAYERS_PER_EXTRA] players past the first, at most [MAX_BEASTS].
     */
    private const val BASE_BEASTS = 2
    private const val PLAYERS_PER_EXTRA = 3
    private const val MAX_BEASTS = 8

    /** SC5 / CX25: points = 5 x the level requirement. */
    private const val POINTS_PER_LEVEL = 5.0

    /** SC5 / CX20: the requirement is the team average plus or minus 2, capped at 99. */
    private const val LEVEL_SPREAD = 2
    private const val LEVEL_ROLLS = 2 * LEVEL_SPREAD + 1

    /** OPEN-RS24: ticks to clear a shortcut (sped up on 29 Nov 2023, no duration is stated). */
    private const val CLEAR_TICKS = 5

    /** OPEN-RS22: where the shortcut stands is not stated; a floor tile this far from the room's doors with open floor around it. */
    private const val SHORTCUT_MIN_FROM_DOOR = 5

    /** OPEN-RS23: crossing a cleared shortcut moves the player to the far side, two tiles on. */
    private const val SHAPE = 10
    private const val OP1 = 1

    fun register(scope: ContentScope) {
        CoxFlow.scripts[CoxKind.SCAVENGERS] = CoxRoomScript { run, cell, any -> start(run, cell, any, large = false) }
        CoxFlow.scripts[CoxKind.SCAVENGERS_LARGE] = CoxRoomScript { run, cell, any -> start(run, cell, any, large = true) }
        with(scope) {
            for (kind in KINDS) {
                onOpLoc(kind.intact, OP1) { loc -> clearShortcut(loc, kind) }
                if (kind.cleared != NO_LOC) onOpLoc(kind.cleared, OP1) { loc -> cross(player, loc) }
            }
        }
    }

    /** SC1: the beasts of the room, scaled by raids-core's `spawnAt`; Challenge Mode uses the second variant (SC1, conflict 1). */
    private fun start(run: RaidRun, cell: CoxCell, any: EntityRef, large: Boolean) {
        val type = if (run.scaling.mode == CoxRaid.CHALLENGE) Npcs.RAIDS_SCAVENGER_BEAST_B else Npcs.RAIDS_SCAVENGER_BEAST_A
        val geo = CoxRoomGeo(any, run, cell)
        val frame = CoxRooms.frame(run, cell)
        for (at in geo.spread(beasts(run.scaling.scaledSize), BEAST_SIZE)) {
            val npc = Raids.spawnAt(any, run, type, at)
            npc[SharedAttrs.RAID_ROOM] = frame
            npc[CoxRooms.CELL] = cell.index
        }
        if (large) placeShortcut(run, cell, any, geo)
    }

    fun beasts(scale: Int): Int = (BASE_BEASTS + (scale - 1) / PLAYERS_PER_EXTRA).coerceIn(BASE_BEASTS, MAX_BEASTS)

    /** SC5: one of the three shortcuts on open floor away from the doors, asking the team's average level plus or minus 2. */
    private fun placeShortcut(run: RaidRun, cell: CoxCell, any: EntityRef, geo: CoxRoomGeo) {
        val kindIndex = run.rng.next(KINDS.size, "cox_shortcut_kind")
        val kind = KINDS[kindIndex]
        val doors = listOf(CoxMap.arrival(any, run, cell, cell.entrySide), CoxMap.arrival(any, run, cell, cell.exitSide))
        val floor = geo.floor.toHashSet()
        val spots =
            geo.floor.filter { t ->
                doors.all { abs(t.x - it.x) >= SHORTCUT_MIN_FROM_DOOR || abs(t.z - it.z) >= SHORTCUT_MIN_FROM_DOOR } &&
                    (-1..1).all { dx -> (-1..1).all { dz -> CoordGrid(t.x + dx, t.z + dz, t.level) in floor } }
            }
        if (spots.isEmpty()) return
        val at = spots[run.rng.next(spots.size, "cox_shortcut_at")]
        val level =
            (
                CoxRes.teamAverage(
                    run,
                    kind.stat,
                ) + run.rng.next(LEVEL_ROLLS, "cox_shortcut_level") - LEVEL_SPREAD
            ).coerceIn(1, Stats.MAX_LEVEL)
        any.addLoc(kind.intact, at, SHAPE, 0)
        CoxRes.state(any, run).shortcuts[at] = CoxShortcut(kindIndex, level)
    }

    /** SC5: clears the shortcut for 5 x its level in points, with the skill's tool and level. */
    private suspend fun PlayerScript.clearShortcut(loc: LocRef, kind: Kind) {
        val run = CoxFlow.runOf(player) ?: return
        val shortcut = CoxRes.state(player, run).shortcuts[loc.coord]?.takeUnless { it.done } ?: return
        if (player.level(kind.stat) < shortcut.level) {
            return player.message("You need a ${Stats.name(kind.stat)} level of ${shortcut.level} to ${kind.verb}.")
        }
        if (!hasTool(player, kind.stat)) return player.message("You need the right tool to ${kind.verb}.")
        player.anim(kind.anim)
        delay(CLEAR_TICKS)
        if (shortcut.done) return
        shortcut.done = true
        if (kind.cleared == NO_LOC) player.deleteLoc(loc.coord, loc.shape) else player.addLoc(kind.cleared, loc.coord, loc.shape, loc.angle)
        CoxPoints.award(run, player.username, POINTS_PER_LEVEL * shortcut.level)
    }

    private fun hasTool(player: PlayerRef, stat: Int): Boolean =
        when (stat) {
            Stats.WOODCUTTING -> CoxItems.hasAxe(player)
            Stats.MINING -> CoxItems.hasPickaxe(player)
            else -> true
        }

    /** OPEN-RS23: "Cross" steps over the cleared stump or rubble to the tile opposite the player's. */
    private fun cross(player: PlayerRef, loc: LocRef) {
        val from = player.coord
        player.teleport(CoxMap.freeNear(player, CoordGrid(2 * loc.coord.x - from.x, 2 * loc.coord.z - from.z, from.level)))
    }
}
