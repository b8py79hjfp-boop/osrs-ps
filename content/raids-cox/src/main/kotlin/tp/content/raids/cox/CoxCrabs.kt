package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * The jewelled crab puzzle (puzzles.md CR1-CR11): the carving sends one white orb at a time; it travels in a straight line,
 * takes the colour of the first crab it meets and turns right (CM-Strat; OPEN-PZ4 for Normal), vanishes on a crab of
 * another colour, a wall or a raider (10-30 to the raider, CR5), and turns a crystal white when its colour matches
 * (black: white orb, yellow: blue, cyan: red, magenta: green). Four white crystals finish the room (OPEN-PZ2).
 */
internal object CoxCrabs {
    private class Room(val cell: CoxCell, val source: CoordGrid, val direction: Int) {
        var orb: NpcRef? = null
        var dir = direction
        var colour = WHITE
        val solved = HashSet<CoordGrid>()
        var crystals = 0
    }

    private fun rooms(run: RaidRun): HashMap<Int, Room> {
        val st = requireNotNull(CoxFlow.state(run))
        @Suppress("UNCHECKED_CAST")
        return st.puzzles.getOrPut(KEY) { HashMap<Int, Room>() } as HashMap<Int, Room>
    }

    fun register(scope: ContentScope) {
        CoxFlow.scripts[CoxKind.CRABS] = CoxRoomScript { run, cell, any -> start(run, cell, any) }
        with(scope) {
            onOpLoc(Locs.RAIDS_LASERCRABS_HAMMER, OP1) { take(player) }
            for (crab in CRABS) onOpNpc(crab, OP_SMASH) { npc -> smash(player, npc) }
        }
    }

    private fun start(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val relief = CoxMap.findLoc(any, run, cell, Locs.RAIDS_LASERCRABS_XERIC_RELIEF)
        val crystals = CRYSTALS.keys.flatMap { CoxMap.findLocs(any, run, cell, it) }
        val source = relief?.coord ?: CoxRoomGeo(any, run, cell).centre(1)
        // OPEN-PZ4: the launch direction: of the sides with an open run in front of the carving, the one heading closest
        // to the middle of the crystals; the orb leaves from the first free tile on it.
        val cx = crystals.sumOf { it.coord.x } / maxOf(1, crystals.size)
        val cz = crystals.sumOf { it.coord.z } / maxOf(1, crystals.size)
        val options =
            (0 until SIDES).mapNotNull { d ->
                val (sx, sz) = DELTA[d]
                val first =
                    (1..LAUNCH_SEARCH)
                        .map {
                            CoordGrid(
                                source.x + sx * it,
                                source.z + sz * it,
                                source.level,
                            )
                        }.firstOrNull { any.isWalkable(it) }
                        ?: return@mapNotNull null
                if (!any.isWalkable(CoordGrid(first.x + sx, first.z + sz, first.level))) return@mapNotNull null
                val ahead = CoordGrid(first.x + sx * AHEAD, first.z + sz * AHEAD, first.level)
                Triple(d, first, kotlin.math.abs(ahead.x - cx) + kotlin.math.abs(ahead.z - cz))
            }
        val best = options.minByOrNull { it.third }
        val dir = best?.first ?: NORTH
        val start = best?.second ?: source
        val room = Room(cell, start, dir)
        room.crystals = crystals.size
        rooms(run)[cell.index] = room
        // CR6 (OPEN-PZ6): three crabs, one more per two extra raiders.
        val crabs = CRABS_MIN + (run.scaling.scaledSize - 1) / 2
        val frame = CoxRooms.frame(run, cell)
        for (at in CoxRoomGeo(any, run, cell).spread(crabs, 1)) {
            val crab = Raids.spawnAt(any, run, Npcs.RAIDS_LASERCRABS_CRAB_GREY, at)
            crab[SharedAttrs.RAID_ROOM] = frame
            crab[CoxRooms.CELL] = cell.index
        }
        CoxRooms.placeBlocker(any, run, cell, Locs.RAIDS_LASERCRABS_BIGCRYSTAL_1)
        tick(run, room, any)
    }

    private fun tick(run: RaidRun, room: Room, any: EntityRef) {
        any.queueHit(1, strong = false) {
            if (run.instance == null || CoxFlow.state(run)?.done?.get(room.cell.index) != false) return@queueHit
            step(run, room, any)
            tick(run, room, any)
        }
    }

    /** CR1: a new orb as soon as the last one is gone; each tick it moves one tile (OPEN-PZ4: speed). */
    private fun step(run: RaidRun, room: Room, any: EntityRef) {
        val orb = room.orb
        if (orb == null || !orb.isInWorld) {
            room.colour = WHITE
            room.dir = room.direction
            room.orb = Raids.spawnAt(any, run, ORB[WHITE], room.source)
            return
        }
        val (dx, dz) = DELTA[room.dir]
        val next = CoordGrid(orb.coord.x + dx, orb.coord.z + dz, orb.coord.level)
        val keeps =
            when {
                CoxMap.cellAt(requireNotNull(CoxFlow.state(run)).plan, run, next) != room.cell -> {
                    false
                }

                crystalAt(any, next) != null -> {
                    hitCrystal(run, room, any, next)
                }

                crabAt(any, next) != null -> {
                    hitCrab(run, room, orb, any, requireNotNull(crabAt(any, next)))
                }

                run.presentPlayers(any).any { it.coord == next } -> {
                    hitPlayer(run, any, next)
                }

                !any.isWalkable(next) -> {
                    false
                }

                else -> {
                    orb.teleport(next)
                    true
                }
            }
        if (!keeps) vanish(room, orb)
    }

    /** CR3: a crystal turns white for the matching orb; the orb is spent either way. */
    private fun hitCrystal(run: RaidRun, room: Room, any: EntityRef, next: CoordGrid): Boolean {
        val (id, at) = crystalAt(any, next) ?: return false
        if (CRYSTALS[id] == room.colour && at !in room.solved) {
            any.addLoc(Locs.RAIDS_LASERCRABS_SMALLCRYSTAL_WHITE, at, CENTREPIECE, 0)
            any.spotAnimAt(at, SpotAnims.RAIDS_LASERCRABS_SHATTERCRYSTAL)
            room.solved += at
            if (room.solved.size >= room.crystals) solve(run, room, any)
        }
        return false
    }

    /** CR3 / CR4: a white orb takes the crab's colour, a coloured one passes only its own colour; it turns right. */
    private fun hitCrab(run: RaidRun, room: Room, orb: NpcRef, any: EntityRef, crab: NpcRef): Boolean {
        val c = CRAB_COLOUR[crab.type] ?: WHITE
        if (room.colour == WHITE) {
            if (c != WHITE) recolour(room, orb, c, any, run)
        } else if (c != room.colour) {
            return false
        }
        room.dir = (room.dir + 1) % SIDES
        return true
    }

    /** CR5 (CM-Strat 10-30; OPEN-PZ5 in Normal): a raider in the way is hit and the orb is gone. */
    private fun hitPlayer(run: RaidRun, any: EntityRef, next: CoordGrid): Boolean {
        val p = run.presentPlayers(any).firstOrNull { it.coord == next } ?: return false
        any.events.publish(tp.plugin.DamageRequest(p, null, ORB_MIN + any.random(ORB_MAX - ORB_MIN + 1)))
        return false
    }

    private fun crabAt(any: EntityRef, at: CoordGrid): NpcRef? = any.npcsWithin(at, 0).firstOrNull { it.type in CRABS && it.coord == at }

    private fun recolour(room: Room, orb: NpcRef, colour: Int, any: EntityRef, run: RaidRun) {
        room.colour = colour
        val at = orb.coord
        orb.despawn()
        room.orb = Raids.spawnAt(any, run, ORB[colour], at)
    }

    private fun vanish(room: Room, orb: NpcRef) {
        orb.despawn()
        room.orb = null
    }

    private fun crystalAt(any: EntityRef, at: CoordGrid): Pair<Int, CoordGrid>? {
        val loc = any.locAt(at, CENTREPIECE) ?: return null
        return if (loc.id in CRYSTALS || loc.id == Locs.RAIDS_LASERCRABS_SMALLCRYSTAL_WHITE) loc.id to loc.coord else null
    }

    private fun solve(run: RaidRun, room: Room, any: EntityRef) {
        CoxRooms.removeBlocker(any, run, room.cell)
        room.orb?.despawn()
        room.orb = null
        CoxFlow.roomDone(run, room.cell)
    }

    /** CR2 / CR7: Smash with a hammer or a blunt weapon stuns the crab and turns it red for the party-size time. */
    private fun smash(player: PlayerRef, crab: NpcRef) {
        val run = CoxFlow.runOf(player) ?: return
        val weapon = player.objName(player.inv(Invs.WORN).obj(WEAPON_SLOT)).lowercase()
        val hammer = player.inv(Invs.INV).total(Objs.HAMMER) > 0 || BLUNT.any { it in weapon }
        if (!hammer) {
            player.message(NEED_HAMMER)
            return
        }
        val n = run.scaling.partySize
        val band = STUN_BANDS.indexOfFirst { n <= it }.let { if (it < 0) STUN_BANDS.size else it }
        val lo = STUN_MIN[band]
        val hi = lo + STUN_SPREAD
        val ticks = lo + player.random(hi - lo + 1)
        crab.frozenUntil = maxOf(crab.frozenUntil, crab.tick + ticks)
        if (crab.type != Npcs.RAIDS_LASERCRABS_CRAB_RED) crab.transform(Npcs.RAIDS_LASERCRABS_CRAB_RED, restoreStats = false)
        crab[SharedAttrs.RAID_UNTIL] = crab.tick + ticks
    }

    private fun take(player: PlayerRef) {
        if (player.inv(Invs.INV).total(Objs.HAMMER) > 0) return
        player.transaction("cox_crab_hammer") { add(Invs.INV, Objs.HAMMER) }
    }

    private const val KEY = "crabs"
    private const val OP1 = 1
    private const val OP_SMASH = 3
    private const val CENTREPIECE = 10
    private const val WEAPON_SLOT = 3
    private const val WHITE = 0
    private const val RED = 1
    private const val GREEN = 2
    private const val BLUE = 3
    private const val NORTH = 0
    private const val EAST = 1
    private const val SOUTH = 2
    private const val WEST = 3
    private const val SIDES = 4
    private const val CRABS_MIN = 3

    /** CR7 (Mod Ash): stun ticks by party size: 1 -> 50-60, 2-3 -> 30-40, 4-5 -> 20-30, 6+ -> 10-20. */
    private val STUN_BANDS = intArrayOf(1, 3, 5)
    private val STUN_MIN = intArrayOf(50, 30, 20, 10)
    private const val STUN_SPREAD = 10
    private const val LAUNCH_SEARCH = 4
    private const val AHEAD = 5
    private const val ORB_MIN = 10
    private const val ORB_MAX = 30
    private val DELTA = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
    private val ORB =
        intArrayOf(
            Npcs.RAIDS_LASERCRABS_ENERGY_WHITE,
            Npcs.RAIDS_LASERCRABS_ENERGY_RED,
            Npcs.RAIDS_LASERCRABS_ENERGY_GREEN,
            Npcs.RAIDS_LASERCRABS_ENERGY_BLUE,
        )
    private val CRABS =
        setOf(
            Npcs.RAIDS_LASERCRABS_CRAB_GREY,
            Npcs.RAIDS_LASERCRABS_CRAB_RED,
            Npcs.RAIDS_LASERCRABS_CRAB_GREEN,
            Npcs.RAIDS_LASERCRABS_CRAB_BLUE,
        )
    private val CRAB_COLOUR =
        mapOf(
            Npcs.RAIDS_LASERCRABS_CRAB_GREY to WHITE,
            Npcs.RAIDS_LASERCRABS_CRAB_RED to RED,
            Npcs.RAIDS_LASERCRABS_CRAB_GREEN to GREEN,
            Npcs.RAIDS_LASERCRABS_CRAB_BLUE to BLUE,
        )

    /** CR3: crystal -> the orb colour that turns it white. */
    private val CRYSTALS =
        mapOf(
            Locs.RAIDS_LASERCRABS_SMALLCRYSTAL_BLACK to WHITE,
            Locs.RAIDS_LASERCRABS_SMALLCRYSTAL_YELLOW to BLUE,
            Locs.RAIDS_LASERCRABS_SMALLCRYSTAL_CYAN to RED,
            Locs.RAIDS_LASERCRABS_SMALLCRYSTAL_MAGENTA to GREEN,
        )

    /** CR2: blunt weapons that can Smash (the room's hammer, elder maul, dragon warhammer, bludgeon, Torag's, granite maul). */
    private val BLUNT = listOf("hammer", "maul", "bludgeon", "warhammer")
    private const val NEED_HAMMER = "You need a hammer to smash the crab."
}
