package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.RaidRoomFrame
import tp.plugin.SharedAttrs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * The combat rooms' set-up (the fights themselves are boss scripts in content/combat): when the first player walks into
 * a combat cell its NPCs spawn, the room-lock blocker the timer plugins watch (runelite-timings "room-complete detection",
 * client-contract 5.1) appears in front of the exit, and the room is done once its required NPCs are dead. NPC spawn
 * tiles are not in the cache (client-contract 6.4 "static NPC spawns ... the server places them"): OPEN-CX32, chosen from
 * the cell's own features (Tekton's anvil, Vespula's portal, the Guardians' doorway) or spread over its walkable floor.
 */
internal object CoxRooms {
    /** Per run and cell: the required NPCs still alive. */
    private val ALIVE = AttrKey<HashMap<Int, MutableSet<NpcRef>>>("cox_room_alive")

    /** Which cell an NPC belongs to. */
    val CELL = AttrKey<Int>("cox_cell")

    fun register(scope: tp.plugin.ContentScope) {
        for (kind in CoxKind.entries.filter { it.combat }) CoxFlow.scripts[kind] = CoxRoomScript { run, cell, any -> start(run, cell, any) }
        CoxThieving.register(scope)
        CoxTightrope.register(scope)
        CoxIceDemonRoom.register(scope)
        CoxCrabs.register(scope)
    }

    /** A room NPC that must die before the room is done, added after the start (the Ice demon once it thaws). */
    fun require(run: RaidRun, cell: CoxCell, npc: NpcRef) {
        run.roomAlive().getOrPut(cell.index) { HashSet() } += npc
    }

    fun frame(run: RaidRun, cell: CoxCell): RaidRoomFrame =
        RaidRoomFrame(
            CoxMap.inCell(run, cell, 0, 0),
            cell.rotation,
            CoxLayout.CELL,
            cell.template,
        )

    private fun start(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val frame = frame(run, cell)
        val geo = CoxRoomGeo(any, run, cell)
        val required = ArrayList<NpcRef>()

        fun spawn(type: Int, at: CoordGrid, needed: Boolean = true): NpcRef {
            val npc = Raids.spawnAt(any, run, type, at)
            npc[SharedAttrs.RAID_ROOM] = frame
            npc[CELL] = cell.index
            if (needed) required += npc
            return npc
        }
        populate(run, cell, frame, geo, ::spawn)
        BLOCKERS[cell.kind]?.let { id -> placeBlocker(any, run, cell, id) }
        val alive = run.roomAlive()
        alive[cell.index] = required.toMutableSet()
        if (required.isEmpty()) done(run, cell, any)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun populate(run: RaidRun, cell: CoxCell, frame: RaidRoomFrame, geo: CoxRoomGeo, make: (Int, CoordGrid, Boolean) -> NpcRef) {
        var placed = 0

        fun spawn(type: Int, at: CoordGrid, needed: Boolean = true): NpcRef = make(type, at, needed).also { if (needed) placed++ }
        val size = run.scaling.scaledSize
        val variant = variantOf(cell)
        when (cell.kind) {
            CoxKind.TEKTON -> {
                val (ax, az) = TEKTON_ANVIL[variant]
                spawn(Npcs.RAIDS_TEKTON_WAITING, geo.spotNear(frame.tile(ax, az), TEKTON_SIZE))
            }

            CoxKind.VASA -> {
                spawn(Npcs.RAIDS_VASANISTIRIO_DORMANT, geo.centre(VASA_SIZE))
                for (corner in geo.corners(CRYSTAL_SIZE)) spawn(Npcs.RAIDS_VASANISTIRIO_CRYSTAL, corner, needed = false)
            }

            CoxKind.VANGUARDS -> {
                for (at in geo.spread(VANGUARDS, VANGUARD_SIZE)) spawn(Npcs.RAIDS_VANGUARD_DORMANT, at)
            }

            CoxKind.VESPULA -> {
                val (px, pz) = VESPULA_PORTAL[variant]
                val portal = spawn(Npcs.RAIDS_VESPULA_PORTAL, geo.spotNear(frame.tile(px, pz), PORTAL_SIZE, walkable = false))
                spawn(Npcs.RAIDS_VESPULA_FLYING, geo.spotNear(portal.coord, VESPULA_SIZE), needed = false)
                for (at in geo.spread(GRUBS, GRUB_SIZE)) spawn(Npcs.RAIDS_VESPULA_CATERPILLAR_HEALTHY, at, needed = false)
            }

            CoxKind.MUTTADILES -> {
                spawn(Npcs.RAIDS_DOGODILE_JUNIOR, geo.centre(SMALL_MUTT_SIZE))
                spawn(Npcs.RAIDS_DOGODILE_SUBMERGED, geo.spread(1, LARGE_MUTT_SIZE, fromEntry = false).first())
                CoxRoomActions.treeSpawned(run, spawn(Npcs.RAIDS_DOGODILE_MEAT_TREE, geo.spread(1, TREE_SIZE).first(), needed = false))
            }

            CoxKind.GUARDIANS -> {
                for (at in geo.flankExit(GUARDIAN_SIZE)) spawn(guardianFor(placed), at)
            }

            CoxKind.SHAMANS -> {
                for (at in geo.spread(shamans(size), SHAMAN_SIZE)) spawn(SHAMANS[placed % SHAMANS.size], at)
            }

            CoxKind.MYSTICS -> {
                for (at in geo.spread(mystics(size), MYSTIC_SIZE)) spawn(MYSTICS[placed % MYSTICS.size], at)
            }

            else -> {
                Unit
            }
        }
    }

    /** An NPC of a combat room died: when it was the room's last required one, the room is done. */
    fun npcDied(run: RaidRun, npc: NpcRef, any: EntityRef) {
        val index = npc[CELL] ?: return
        val set = run.roomAlive()[index] ?: return
        if (!set.removeIf { it == npc }) return
        if (npc.type == Npcs.RAIDS_ICEDEMON_COMBAT) CoxIceDemonRoom.demonDied(run, any)
        if (set.isEmpty()) {
            val st = CoxFlow.state(run) ?: return
            val cell = st.plan.cells[index]
            if (cell.kind.combat) done(run, cell, any) else CoxFlow.roomDone(run, cell)
        }
    }

    private fun done(run: RaidRun, cell: CoxCell, any: EntityRef) {
        removeBlocker(any, run, cell)
        if (cell.kind == CoxKind.MYSTICS) CoxRoomActions.mysticsDone(run, cell, any)
        CoxFlow.roomDone(run, cell)
    }

    /**
     * The room-lock blocker [id] in front of [cell]'s exit doorway, on the room's floor (the arrival pocket by the cell
     * edge does not join the floor in every template); its tile is kept, since it blocks the tile it stands on.
     */
    fun placeBlocker(any: EntityRef, run: RaidRun, cell: CoxCell, id: Int) {
        val st = CoxFlow.state(run)
        val door = CoxMap.findLocs(any, run, cell, Locs.RAIDS_DOORWAY).firstOrNull { st?.spawnedPassages?.containsKey(it.coord) != true }
        val at = if (door != null) CoxRoomGeo(any, run, cell).spotNear(door.coord, 1) else CoxMap.arrival(any, run, cell, cell.exitSide)
        any.addLoc(id, at, CENTREPIECE, 0)
        CoxFlow.state(run)?.blockers?.put(cell.index, at)
    }

    /** A room opened by its own rule (a puzzle): the blocker goes and the passage lets the party on. */
    fun complete(run: RaidRun, cell: CoxCell, any: EntityRef) {
        removeBlocker(any, run, cell)
        CoxFlow.roomDone(run, cell)
    }

    /** Removes [cell]'s blocker (the room is done, or a puzzle opened the way). */
    fun removeBlocker(any: EntityRef, run: RaidRun, cell: CoxCell) {
        val at = CoxFlow.state(run)?.blockers?.remove(cell.index) ?: return
        any.deleteLoc(at, CENTREPIECE)
    }

    private fun RaidRun.roomAlive(): HashMap<Int, MutableSet<NpcRef>> {
        val st = requireNotNull(CoxFlow.state(this))
        return st.roomAlive
    }

    /** 0 CCW, 1 THRU, 2 CW (client-contract 6.2: the template cell's x). */
    private fun variantOf(cell: CoxCell): Int = (cell.template.x - CCW_X) / CoxLayout.CELL

    /** shamans.md: scale 1-4 -> 2, 5-9 -> 3, 10-14 -> 4, 15+ -> 5. */
    fun shamans(scale: Int): Int =
        when {
            scale <= SHAMAN_STEP_1 -> 2
            scale <= SHAMAN_STEP_2 -> 3
            scale <= SHAMAN_STEP_3 -> 4
            else -> MAX_SHAMANS
        }

    /** mystics.md: 3 at scale 1-2, one more per 3 of scale from 3, up to 12 at 27+. */
    fun mystics(
        scale: Int,
    ): Int = (MIN_MYSTICS + (scale - MYSTIC_FIRST_STEP + MYSTIC_STEP) / MYSTIC_STEP).coerceIn(MIN_MYSTICS, MAX_MYSTICS)

    private fun guardianFor(placed: Int): Int = if (placed == 0) Npcs.RAIDS_STONEGUARDIANS_LEFT else Npcs.RAIDS_STONEGUARDIANS_RIGHT

    /** Client-contract 5.1 / runelite-timings: the blocker each room spawns and removes when it is done. */
    private val BLOCKERS: Map<CoxKind, Int> =
        mapOf(
            CoxKind.TEKTON to Locs.RAIDS_BLOCKAGE_ORANGE,
            CoxKind.VANGUARDS to Locs.RAIDS_BLOCKAGE_ORANGE,
            CoxKind.VASA to Locs.RAIDS_BLOCKAGE_PURPLE_SMALL,
            CoxKind.MUTTADILES to Locs.RAIDS_BLOCKAGE_GREEN,
            CoxKind.VESPULA to Locs.RAIDS_VESPULA_BOIL_BLOCKING,
            CoxKind.MYSTICS to Locs.RAIDS_SKELETALMYSTICS_SYMBOL,
            CoxKind.SHAMANS to SHAMAN_GUARDIAN_BLOCKER,
            CoxKind.GUARDIANS to SHAMAN_GUARDIAN_BLOCKER,
        )

    /** Client-contract 6.4: Tekton's anvil and Vespula's portal per variant (CCW, THRU, CW), template-local. */
    private val TEKTON_ANVIL = listOf(14 to 22, 7 to 13, 12 to 22)
    private val VESPULA_PORTAL = listOf(17 to 21, 23 to 11, 11 to 21)
    private val SHAMANS = intArrayOf(Npcs.RAIDS_LIZARDSHAMAN_A, Npcs.RAIDS_LIZARDSHAMAN_B)
    private val MYSTICS = intArrayOf(Npcs.RAIDS_SKELETONMYSTIC_A, Npcs.RAIDS_SKELETONMYSTIC_B, Npcs.RAIDS_SKELETONMYSTIC_C)

    /** Loc 26209 (no symbol): the shamans' / thieving / guardians' blocker (runelite-timings). */
    private const val SHAMAN_GUARDIAN_BLOCKER = 26209
    private const val CENTREPIECE = 10
    private const val CCW_X = 3264
    private const val TEKTON_SIZE = 4
    private const val VASA_SIZE = 5
    private const val CRYSTAL_SIZE = 4
    private const val VANGUARDS = 3
    private const val VANGUARD_SIZE = 3
    private const val PORTAL_SIZE = 4
    private const val VESPULA_SIZE = 5

    /** vespula.md VE1: four lux grubs. */
    private const val GRUBS = 4
    private const val GRUB_SIZE = 3
    private const val SMALL_MUTT_SIZE = 3
    private const val LARGE_MUTT_SIZE = 5
    private const val TREE_SIZE = 2
    private const val GUARDIAN_SIZE = 2
    private const val SHAMAN_SIZE = 3
    private const val MYSTIC_SIZE = 2
    private const val SHAMAN_STEP_1 = 4
    private const val SHAMAN_STEP_2 = 9
    private const val SHAMAN_STEP_3 = 14
    private const val MAX_SHAMANS = 5
    private const val MIN_MYSTICS = 3
    private const val MAX_MYSTICS = 12
    private const val MYSTIC_FIRST_STEP = 3
    private const val MYSTIC_STEP = 3
}
