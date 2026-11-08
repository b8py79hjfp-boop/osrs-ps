package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * The tightrope puzzle (puzzles.md TR1-TR7): deathly rangers and mages on a platform turn on whoever crosses the rope;
 * the crossing needs Agility (80-100% of the team's average, drawn once per raid: OPEN-PZ16); the keystone from the far
 * side dispels the shimmering barrier and kills the rangers and mages that are left.
 */
internal object CoxTightrope {
    private class Room(val cell: CoxCell, val agility: Int) {
        val foes = ArrayList<NpcRef>()
    }

    private fun rooms(run: RaidRun): HashMap<Int, Room> {
        val st = requireNotNull(CoxFlow.state(run))
        @Suppress("UNCHECKED_CAST")
        return st.puzzles.getOrPut(KEY) { HashMap<Int, Room>() } as HashMap<Int, Room>
    }

    fun register(scope: ContentScope) {
        CoxFlow.scripts[CoxKind.TIGHTROPE] = CoxRoomScript { run, cell, any -> start(run, cell, any) }
        with(scope) {
            onOpLoc(Locs.RAIDS_TIGHTROPE_END, OP1) { loc -> cross(player, loc) }
            onOpLoc(Locs.RAIDS_TIGHTROPE_KEYSTONE_LOC, OP1) { take(player) }
            onOpLoc(Locs.RAIDS_TIGHTROPE_BARRIER, OP1) { loc -> dispel(player, loc) }
        }
    }

    private fun start(run: RaidRun, cell: CoxCell, any: EntityRef) {
        // TR3: 80-100% of the team's average Agility level, randomly scaled.
        val average = run.scaling.skillSums.getOrElse(Stats.AGILITY) { 1 } / maxOf(1, run.scaling.partySize)
        val pct = MIN_PCT + run.rng.next(MAX_PCT - MIN_PCT + 1, "cox tightrope agility")
        val room = Room(cell, maxOf(1, average * pct / PERCENT))
        rooms(run)[cell.index] = room
        // TR1 (OPEN-PZ15): 2-4 of each by party size: 2 up to 3 raiders, 3 up to 7, 4 from 8.
        val n = run.scaling.scaledSize
        val each =
            if (n <= SMALL) {
                2
            } else if (n <= MEDIUM) {
                3
            } else {
                4
            }
        val geo = CoxRoomGeo(any, run, cell)
        val frame = CoxRooms.frame(run, cell)
        for ((k, at) in geo.spread(each * 2, 1, fromEntry = false).withIndex()) {
            val type = if (k % 2 == 0) Npcs.RAIDS_TIGHTROPE_RANGER else Npcs.RAIDS_TIGHTROPE_MAGE
            val npc = Raids.spawnAt(any, run, type, at)
            npc[SharedAttrs.RAID_ROOM] = frame
            npc[CoxRooms.CELL] = cell.index
            room.foes += npc
        }
        CoxRooms.placeBlocker(any, run, cell, Locs.RAIDS_TIGHTROPE_BARRIER)
    }

    /** TR2 / TR3: cross to the other end; the enemies not already fighting turn on the crosser. */
    private suspend fun tp.plugin.PlayerScript.cross(player: PlayerRef, loc: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, loc.coord) ?: return
        val room = rooms(run)[cell.index] ?: return
        if (player.level(Stats.AGILITY) < room.agility) {
            player.message("You need an Agility level of ${room.agility} to cross the tightrope.")
            return
        }
        val other = CoxMap.findLocs(player, run, cell, Locs.RAIDS_TIGHTROPE_END).firstOrNull { it.coord != loc.coord } ?: return
        for (foe in room.foes) {
            if (!foe.isInWorld || foe.target != null) continue
            foe[SharedAttrs.RAID_FOCUS] = player.username
            foe.huntRange = FOCUS_RANGE
        }
        val steps = maxOf(kotlin.math.abs(other.coord.x - loc.coord.x), kotlin.math.abs(other.coord.z - loc.coord.z))
        player.anim(CROSS_ANIM)
        delay(maxOf(1, steps))
        player.teleport(CoxMap.freeNear(player, other.coord))
    }

    /** TR5: the keystone crystal on the far side. */
    private fun take(player: PlayerRef) {
        if (player.inv(Invs.INV).total(Objs.RAIDS_TIGHTROPE_KEYSTONE) > 0) return
        player.transaction("cox_keystone") { add(Invs.INV, Objs.RAIDS_TIGHTROPE_KEYSTONE) }
    }

    /** TR5 / TR7: the keystone dispels the barrier and kills the rangers and mages that are left (OPEN-PZ19: points). */
    private fun dispel(player: PlayerRef, loc: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, loc.coord) ?: return
        val room = rooms(run)[cell.index] ?: return
        if (!player.transaction("cox_keystone_use") { remove(Invs.INV, Objs.RAIDS_TIGHTROPE_KEYSTONE) }) {
            player.message(NEED_KEYSTONE)
            return
        }
        CoxRooms.removeBlocker(player, run, cell)
        for (foe in room.foes) if (foe.isInWorld) player.events.publish(tp.plugin.DamageRequest(foe, null, foe.hitpoints))
        CoxPoints.award(run, player.username, KEYSTONE_POINTS)
        CoxFlow.roomDone(run, cell)
    }

    private const val KEY = "tightrope"
    private const val OP1 = 1
    private const val CENTREPIECE = 10
    private const val MIN_PCT = 80
    private const val MAX_PCT = 100
    private const val PERCENT = 100
    private const val SMALL = 3
    private const val MEDIUM = 7
    private const val FOCUS_RANGE = 24

    /** OPEN-PZ19: "some points" for the keystone. */
    private const val KEYSTONE_POINTS = 0.0

    /** The balance-walk animation (cache seq `MYQ3_HUMAN_TIGHTROPE`). */
    private val CROSS_ANIM = tp.symbols.Seqs.MYQ3_HUMAN_TIGHTROPE
    private const val NEED_KEYSTONE = "The barrier needs a keystone crystal to dispel it."
}
