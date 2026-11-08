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
import tp.util.CoordGrid

/**
 * The Ice demon puzzle's braziers (puzzles.md ID1-ID5, ID8, ID9): kindling lights and feeds the four braziers (Firemaking
 * 8% at 1 to 78% at 99); each burns its kindling down, an icefiend in front of a lit brazier tries to remove a charge every
 * 3-4 ticks (1 in 6); the burnt kindling thaws the storm and the demon walks out to fight. 36 points per kindling burned
 * and 32 per brazier lit. On its death every raider gets fiendish ashes, stinkhorns, endarkened juice and cicely.
 */
internal object CoxIceDemonRoom {
    private class Room(val cell: CoxCell, val thawAt: Int) {
        val charges = HashMap<CoordGrid, Int>()
        val fiends = HashMap<CoordGrid, NpcRef>()
        val nextFiend = HashMap<CoordGrid, Long>()
        var burnt = 0
        var demon: NpcRef? = null
        var thawed = false
    }

    private fun rooms(run: RaidRun): HashMap<Int, Room> {
        val st = requireNotNull(CoxFlow.state(run))
        @Suppress("UNCHECKED_CAST")
        return st.puzzles.getOrPut(KEY) { HashMap<Int, Room>() } as HashMap<Int, Room>
    }

    fun register(scope: ContentScope) {
        CoxFlow.scripts[CoxKind.ICE_DEMON] = CoxRoomScript { run, cell, any -> start(run, cell, any) }
        with(scope) {
            onOpLoc(Locs.RAIDS_ICEDEMON_BRAZIER_UNLIT, OP1) { loc -> fuel(player, loc, light = true) }
            onOpLoc(Locs.RAIDS_ICEDEMON_BRAZIER_LIT, OP1) { loc -> fuel(player, loc, light = false) }
            onOpLoc(Locs.RAIDS_ICEDEMON_TINDERBOX, OP1) { give(player, Objs.TINDERBOX) }
            onOpLoc(Locs.RAIDS_ICEDEMON_AXE, OP1) { give(player, Objs.BRONZE_AXE) }
        }
    }

    private fun start(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val n = run.scaling.scaledSize
        // ID3 (OPEN-PZ9): solo needs 54 kindling; the burn is weighted by the players, assumed linear.
        val room = Room(cell, SOLO_THAW * n)
        rooms(run)[cell.index] = room
        val frame = CoxRooms.frame(run, cell)
        val geo = CoxRoomGeo(any, run, cell)
        val braziers = CoxMap.findLocs(any, run, cell, Locs.RAIDS_ICEDEMON_BRAZIER_UNLIT)
        val centre =
            if (braziers.isEmpty()) {
                geo.centre(DEMON_SIZE)
            } else {
                CoordGrid(
                    braziers.sumOf { it.coord.x } / braziers.size - 1,
                    braziers.sumOf { it.coord.z } / braziers.size - 1,
                    braziers[0].coord.level,
                )
            }
        val demon = Raids.spawnAt(any, run, Npcs.RAIDS_ICEDEMON_NONCOMBAT, centre)
        demon[SharedAttrs.RAID_ROOM] = frame
        demon[CoxRooms.CELL] = cell.index
        room.demon = demon
        // ID1: one icefiend per player, at most four, each at a brazier.
        for (b in braziers.take(minOf(n, MAX_FIENDS))) {
            val fiend =
                Raids.spawnAt(
                    any,
                    run,
                    Npcs.RAIDS_ICEFIEND,
                    CoxMap.freeNear(
                        any,
                        CoordGrid(
                            (b.coord.x + centre.x) / 2,
                            (b.coord.z + centre.z) / 2,
                            b.coord.level,
                        ),
                    ),
                )
            fiend.huntRange = 0
            room.fiends[b.coord] = fiend
        }
        for (b in braziers) room.charges[b.coord] = 0
        CoxRooms.placeBlocker(any, run, cell, Locs.RAIDS_ICEDEMON_SNOW)
        tick(run, room, any)
    }

    /** ID2 / ID9: light (Firemaking roll; 32 points) or feed a brazier: all kindling carried goes in (36 points each). */
    private fun fuel(player: PlayerRef, loc: LocRef, light: Boolean) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, loc.coord) ?: return
        val room = rooms(run)[cell.index] ?: return
        val kindling = player.inv(Invs.INV).total(Objs.RAIDS_WOOD).toInt()
        if (kindling <= 0) {
            player.message(NO_KINDLING)
            return
        }
        if (light) {
            if (player.inv(Invs.INV).total(Objs.TINDERBOX) <= 0) {
                player.message(NO_TINDERBOX)
                return
            }
            val level = player.level(Stats.FIREMAKING).coerceIn(1, Stats.MAX_LEVEL)
            val chance = LIGHT_MIN + (LIGHT_MAX - LIGHT_MIN) * (level - 1) / (Stats.MAX_LEVEL - 1)
            if (player.random(PERCENT) >= chance) {
                player.message(LIGHT_FAILED)
                return
            }
            CoxPoints.award(run, player.username, LIGHT_POINTS)
            player.addLoc(Locs.RAIDS_ICEDEMON_BRAZIER_LIT, loc.coord, loc.shape, loc.angle)
        }
        if (!player.transaction("cox_kindling") { remove(Invs.INV, Objs.RAIDS_WOOD, kindling) }) return
        room.charges[loc.coord] = (room.charges[loc.coord] ?: 0) + kindling
        CoxPoints.award(run, player.username, kindling * KINDLING_POINTS)
    }

    private fun tick(run: RaidRun, room: Room, any: EntityRef) {
        any.queueHit(1, strong = false) {
            if (run.instance == null || CoxFlow.state(run)?.done?.get(room.cell.index) != false) return@queueHit
            if (!room.thawed) burn(run, room, any)
            tick(run, room, any)
        }
    }

    private fun burn(run: RaidRun, room: Room, any: EntityRef) {
        for ((at, left) in room.charges.entries.toList()) {
            if (left <= 0) continue
            // ID4: the icefiend in front tries every 3-4 ticks, 1 in 6.
            if (room.fiends.containsKey(at) && any.tick >= (room.nextFiend[at] ?: 0L)) {
                room.nextFiend[at] = any.tick + FIEND_MIN + any.random(FIEND_MAX - FIEND_MIN + 1)
                if (any.random(FIEND_ODDS) == 0) room.charges[at] = left - 1
            }
            // ID3 (OPEN-PZ9): one kindling burns every [BURN_EVERY] ticks.
            if (any.tick % BURN_EVERY == 0L) {
                val now = (room.charges[at] ?: 0) - 1
                room.charges[at] = maxOf(0, now)
                room.burnt++
            }
            if ((room.charges[at] ?: 0) <=
                0
            ) {
                any.locAt(at, CENTREPIECE)?.let { any.addLoc(Locs.RAIDS_ICEDEMON_BRAZIER_UNLIT, at, it.shape, it.angle) }
            }
        }
        if (room.burnt >= room.thawAt) thaw(run, room, any)
    }

    /** ID5: the storm is dispelled, the icefiends vanish and the demon walks out and fights. */
    private fun thaw(run: RaidRun, room: Room, any: EntityRef) {
        room.thawed = true
        CoxRooms.removeBlocker(any, run, room.cell)
        for (f in room.fiends.values) if (f.isInWorld) f.despawn()
        val demon = room.demon ?: return
        demon.transform(Npcs.RAIDS_ICEDEMON_COMBAT, restoreStats = false)
        demon.huntRange = DEMON_HUNT
        CoxRooms.require(run, room.cell, demon)
    }

    /** ID8: every raider gets the demon's drop (added 29 Nov 2023). */
    fun demonDied(run: RaidRun, any: EntityRef) {
        for (p in run.presentPlayers(any)) {
            p.transaction("cox_icedemon_drop") {
                add(Invs.INV, Objs.FIENDISH_ASHES)
                add(Invs.INV, Objs.RAIDS_STINKHORN_MUSHROOM, DROP_STINKHORNS)
                add(Invs.INV, Objs.RAIDS_ENDARKENED_JUICE, DROP_JUICE)
                add(Invs.INV, Objs.RAIDS_CICELY, DROP_CICELY)
            }
        }
    }

    private fun give(player: PlayerRef, obj: Int) {
        if (player.inv(Invs.INV).total(obj) > 0) return
        player.transaction("cox_icedemon_tool") { add(Invs.INV, obj) }
    }

    private const val KEY = "icedemon"
    private const val OP1 = 1
    private const val CENTREPIECE = 10
    private const val DEMON_SIZE = 2
    private const val MAX_FIENDS = 4
    private const val SOLO_THAW = 54
    private const val DEMON_HUNT = 20

    /** ID2: 8% at 1 to 78% at 99 (Mod Ash). */
    private const val LIGHT_MIN = 8
    private const val LIGHT_MAX = 78
    private const val PERCENT = 100

    /** ID9: 32 per brazier lit, 36 per kindling. */
    private const val LIGHT_POINTS = 32.0
    private const val KINDLING_POINTS = 36.0

    /** ID4: every 3-4 ticks, 1 in 6. */
    private const val FIEND_MIN = 3
    private const val FIEND_MAX = 4
    private const val FIEND_ODDS = 6

    /** OPEN-PZ9: the burn rate per brazier. */
    private const val BURN_EVERY = 4L
    private const val DROP_STINKHORNS = 7
    private const val DROP_JUICE = 7
    private const val DROP_CICELY = 5
    private const val NO_KINDLING = "You need kindling to fuel the brazier."
    private const val NO_TINDERBOX = "You need a tinderbox to light the brazier."
    private const val LIGHT_FAILED = "You fail to light the brazier."
}
