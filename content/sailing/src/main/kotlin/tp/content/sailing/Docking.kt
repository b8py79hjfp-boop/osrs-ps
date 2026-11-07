package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Docking, boarding and disembarking (docs/mechanics/sailing-ports.md P2, P6-P9): the gangplank and mooring point locs
 * (Board, Board-previous, Board-friend, Disembark), the buoys (Dock) and the helm (Navigate, Stop-navigating, Escape).
 *
 * The client resolves a gangplank or mooring point loc through varbit `SAILING_PLAYER_IS_ON_PLAYER_BOAT` (multi loc: embark
 * variant while it is 0, disembark variant while 1) and the helm through `SAILING_BOAT_FACILITY_LOCKEDIN`; the server sees the
 * base loc id and the op number, so each handler branches on the player's own state, which it keeps in those varbits.
 */
internal class Docking(private val state: SailingState) {
    fun ContentScope.register() {
        for (loc in Locs.SAILING_GANGPLANK_PORT_SARIM..Locs.SAILING_GANGPLANK_LUNAR_ISLE) registerDock(loc)
        for (loc in Locs.SAILING_MOORING_ISLE_OF_SOULS..Locs.SAILING_MOORING_GRIMSTONE) registerDock(loc)
        registerDock(Locs.SAILING_GANGPLANK_WYRMSCRAIG)
        registerDock(Locs.SAILING_GANGPLANK_WYRMSCRAIG_CAVE)
        for (loc in Locs.SAILING_DOCKING_BUOY_PORT_SARIM..Locs.SAILING_DOCKING_BUOY_GRIMSTONE) registerBuoy(loc)
        registerBuoy(Locs.SAILING_DOCKING_BUOY_WYRMSCRAIG)
        registerBuoy(Locs.SAILING_DOCKING_BUOY_WYRMSCRAIG_CAVE)
        for (loc in HELM_LOCS) {
            onOpLoc(loc, OP_HELM) { helm(player) }
            onOpLoc(loc, OP_ESCAPE) { escape(player) }
        }
        onOpWorldEntity { entity, option ->
            val boat = ActiveBoats.at(this, entity)
            if (boat != null && option == 1) boardOwn(this, boat)
        }
        onLogin {
            setVarbit(Varbits.SAILING_PLAYER_IS_ON_PLAYER_BOAT, 0)
            setVarbit(Varbits.SAILING_BOAT_FACILITY_LOCKEDIN, 0)
        }
        onLogout { leave(this) }
    }

    private fun ContentScope.registerDock(loc: Int) {
        for (op in intArrayOf(OP_BOARD, OP_BOARD_PREVIOUS, OP_BOARD_FRIEND)) {
            onOpLoc(loc, op) { dockOp(player, loc, op) }
            // P14: a gangplank at the pier's end is often reachable only diagonally (its own tile and the water beside it
            // block), which the rectangle reach rule refuses; within one tile the approach script boards instead.
            onApLoc(loc, op) {
                if (currentApRange != GANGPLANK_REACH) return@onApLoc apRange(GANGPLANK_REACH)
                dockOp(player, loc, op)
            }
        }
    }

    private fun ContentScope.registerBuoy(loc: Int) {
        onOpLoc(loc, OP_DOCK) { state.sites.ofBuoy(loc)?.let { buoy(player, it) } }
    }

    // ---- gangplank and mooring point

    /** A gangplank or mooring point op: the base loc [loc] resolves to the Disembark variant while the player is aboard. */
    private fun dockOp(p: PlayerRef, loc: Int, op: Int) {
        val dockId = state.sites.ofGangplank(loc) ?: state.sites.ofMooring(loc) ?: return
        val aboard = ActiveBoats.of(p)
        when {
            aboard != null -> if (op == OP_DISEMBARK) disembark(p, aboard, dockId)
            op == OP_BOARD -> BoatSelection.board(p, state, dockId) { slot -> boardSlot(p, slot, dockId) }
            op == OP_BOARD_PREVIOUS -> boardPrevious(p, dockId)
            else -> boardFriend(p, dockId)
        }
    }

    private fun boardPrevious(p: PlayerRef, dockId: Int) {
        val slot = (p.saved(BoatSave.LAST_BOAT_KEY) ?: 0L).toInt()
        val owned = BoatSave.get(p, slot)
        if (owned == null || owned.dock != dockId) return p.message("Your most recent boat isn't moored here.")
        boardSlot(p, slot, dockId)
    }

    /** Board-friend: asks for a name, then boards that player's moored boat if it is at this dock. Anyone may be a guest (P9). */
    private fun boardFriend(p: PlayerRef, dockId: Int) {
        p.stringDialog("Enter name:") { name ->
            val friend = playersInWorld().firstOrNull { it.username.equals(name.trim(), ignoreCase = true) }
            val boat = friend?.let { ActiveBoats.ownedBy(this, it.username.lowercase()) }
            when {
                boat == null || boat.dock != dockId -> message("That player's boat isn't moored here.")
                else -> board(this, boat)
            }
        }
    }

    /** Boards the owner's boat in [slot], spawning it at dock [dockId] first if it is not in the world. */
    fun boardSlot(p: PlayerRef, slot: Int, dockId: Int) {
        val owned = BoatSave.get(p, slot) ?: return
        val boat = state.data.boat(owned.type) ?: return
        if (p.baseLevel(Stats.SAILING) <
            boat.levelRequired
        ) {
            return p.message("You need level ${boat.levelRequired} Sailing to board that boat.")
        }
        val site = state.sites.of(dockId)
        val pose = site?.sea?.let { Pose(it, site.angle) } ?: return p.message("You can't board a boat here.")
        val name = p.username.lowercase()
        var active = ActiveBoats.ownedBy(p, name)
        if (active != null && active.slot != slot) {
            if (playersAboard(p, active).isNotEmpty()) return p.message("Your other boat still has people aboard.")
            state.boats.despawn(p, active)
            active = null
        }
        val live = active ?: state.boats.spawn(p, name, slot, owned, pose) ?: return p.message("You can't board a boat here.")
        board(p, live)
    }

    /** Puts [p] on the deck of [boat]: remembers the tile to return to and switches the gangplank to its disembark variant. */
    fun board(p: PlayerRef, boat: ActiveBoat) {
        if (playersAboard(p, boat).size >= boat.boat.playerCapacity) return p.message("The boat is full.")
        p[RETURN] = p.coord
        if (boat.owner == p.username.lowercase() && boat.slot >= 0) p.setSaved(BoatSave.LAST_BOAT_KEY, boat.slot.toLong())
        p.setVarbit(Varbits.SAILING_PLAYER_IS_ON_PLAYER_BOAT, 1)
        p.teleport(boat.entry)
    }

    private fun boardOwn(p: PlayerRef, boat: ActiveBoat) {
        if (ActiveBoats.of(p) != null || !boat.isDocked || boat.owner != p.username.lowercase()) return
        board(p, boat)
    }

    /** Leaves the boat at a gangplank or mooring point in range; the owner's boat is moored there (P2). */
    private fun disembark(p: PlayerRef, boat: ActiveBoat, dockId: Int) {
        val site = state.sites.of(dockId) ?: return
        if (boat.slot < 0) return p.message("You can't leave the boat yet.")
        if (!inRange(boat, site.land)) return p.message("Your boat is too far away from here.")
        val owner = boat.owner == p.username.lowercase()
        if (owner && p.baseLevel(Stats.SAILING) < site.dock.levelRequired) {
            return p.message("You need level ${site.dock.levelRequired} Sailing to dock at ${site.dock.name}.")
        }
        stepOff(p, site)
        if (owner) {
            state.boats.moor(p, boat, site)
            p.setSaved(BoatSave.LAST_DOCK_KEY, dockId.toLong())
            save(p, boat)
        }
    }

    // ---- buoy

    /** The buoy's Dock option: moors the player's own boat there if it is in range (sets the capsize / escape dock, P2). */
    private fun buoy(p: PlayerRef, dockId: Int) {
        val boat = ActiveBoats.of(p)
        val site = state.sites.of(dockId)
        when {
            boat == null || site == null || boat.owner != p.username.lowercase() || boat.slot < 0 -> {
                p.message("You can only dock your own boat.")
            }

            p.baseLevel(Stats.SAILING) < site.dock.levelRequired -> {
                p.message("You need level ${site.dock.levelRequired} Sailing to dock at ${site.dock.name}.")
            }

            !inRange(boat, site.sea) -> {
                p.message("Your boat is too far from the buoy.")
            }

            else -> {
                state.boats.moor(p, boat, site)
                p.setSaved(BoatSave.LAST_DOCK_KEY, dockId.toLong())
                save(p, boat)
                p.message("You dock your boat at ${site.dock.name}.")
            }
        }
    }

    // ---- helm

    /** Helm option 1: Navigate / Stop-navigating, the client having resolved which one from varbit 19105. Captain only (OPEN-S2). */
    private fun helm(p: PlayerRef) {
        val boat = ActiveBoats.of(p) ?: return
        val motion = state.boats.motion(p) ?: return
        if (p.varbit(Varbits.SAILING_BOAT_FACILITY_LOCKEDIN) == 1) {
            motion.stopNavigating(p)
            p.setVarbit(Varbits.SAILING_BOAT_FACILITY_LOCKEDIN, 0)
        } else if (boat.owner != p.username.lowercase() && boat.slot >= 0) {
            p.message("Only the captain can take the helm.")
        } else if (motion.navigate(p, boat.ref)) {
            p.setVarbit(Varbits.SAILING_BOAT_FACILITY_LOCKEDIN, 1)
        } else {
            p.message("Someone else is already at the helm.")
        }
    }

    /** Helm option 4, Escape: abandon the boat for the dock of the last disembark (OPEN-ESC: no recovery fee, cargo kept). */
    private fun escape(p: PlayerRef) {
        val boat = ActiveBoats.of(p) ?: return
        if (boat.slot < 0) return p.message("You can't leave the boat yet.")
        val dockId = (p.saved(BoatSave.LAST_DOCK_KEY) ?: DEFAULT_DOCK.toLong()).toInt()
        val site = state.sites.of(dockId) ?: return
        stepOff(p, site)
        if (boat.owner == p.username.lowercase()) {
            state.boats.moor(p, boat, site)
            save(p, boat)
        }
    }

    // ---- shared

    /** Puts [p] ashore at dock [dockId] (quest scenes): off the deck, varbits reset, standing at the landing. */
    fun ashoreAt(p: PlayerRef, dockId: Int) {
        state.sites.of(dockId)?.let { stepOff(p, it) }
    }

    /** Moves [p] off its boat to the tile it boarded from, or to the walkable tile next to [site]'s landing. */
    private fun stepOff(p: PlayerRef, site: DockSite) {
        state.boats.motion(p)?.stopNavigating(p)
        p.setVarbit(Varbits.SAILING_BOAT_FACILITY_LOCKEDIN, 0)
        p.setVarbit(Varbits.SAILING_PLAYER_IS_ON_PLAYER_BOAT, 0)
        val to = p[RETURN]?.takeIf { it.chebyshev(site.land ?: it) <= RETURN_RANGE } ?: landingTile(p, site)
        p[RETURN] = null
        p.teleport(to)
    }

    /** The first walkable tile around the landing of [site], searching outward (the gangplank loc itself blocks). */
    private fun landingTile(p: PlayerRef, site: DockSite): CoordGrid {
        val land = site.land ?: return CoordGrid(SPAWN_X, SPAWN_Z, 0)
        for (r in 1..LANDING_SEARCH) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    val tile = land.translate(dx, dz)
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == r && p.isWalkable(tile)) return tile
                }
            }
        }
        return land
    }

    private fun inRange(boat: ActiveBoat, target: CoordGrid?): Boolean {
        val t = target ?: return true
        return boat.ref.rootCoord.chebyshev(t) <= DOCK_RANGE
    }

    private fun playersAboard(any: PlayerRef, boat: ActiveBoat): List<PlayerRef> = any.playersInWorld().filter { it.coord in boat.ref }

    /** Persists the mooring and hitpoints of [boat] into the owner's slot. */
    fun save(owner: PlayerRef, boat: ActiveBoat) {
        val owned = BoatSave.get(owner, boat.slot) ?: return
        BoatSave.set(owner, boat.slot, owned.copy(dock = boat.dock, hp = boat.hp))
    }

    /** Logout: [p] leaves its boat; if [p] owns a live boat everyone aboard is put ashore and the boat is removed. */
    private fun leave(p: PlayerRef) {
        val own = ActiveBoats.ownedBy(p, p.username.lowercase())
        val aboard = ActiveBoats.of(p)
        if (aboard != null) ashore(p, aboard)
        if (own != null) {
            for (guest in playersAboard(p, own)) ashore(guest, own)
            save(p, own)
            state.boats.despawn(p, own)
        }
    }

    private fun ashore(p: PlayerRef, boat: ActiveBoat) {
        val dock = if (boat.isDocked) boat.dock else (p.saved(BoatSave.LAST_DOCK_KEY) ?: DEFAULT_DOCK.toLong()).toInt()
        val site = state.sites.of(dock) ?: state.sites.of(DEFAULT_DOCK) ?: return
        stepOff(p, site)
    }

    private companion object {
        val RETURN = AttrKey<CoordGrid>("sailing.return")

        /** Gangplank loc options (cache `SAILING_GANGPLANK_EMBARK` / `_DISEMBARK` ops). */
        const val OP_BOARD = 1

        /** P14: Board works from any tile within one of the gangplank, diagonals included. */
        const val GANGPLANK_REACH = 1
        const val OP_BOARD_PREVIOUS = 2
        const val OP_BOARD_FRIEND = 3
        const val OP_DISEMBARK = 1
        const val OP_DOCK = 1

        /** Helm options: Navigate / Stop-navigating and Escape (cache locs 59555 / 59556). */
        const val OP_HELM = 1
        const val OP_ESCAPE = 4

        /** Dock id of Port Sarim, the fallback landing. */
        const val DEFAULT_DOCK = 0
        const val SPAWN_X = 3222
        const val SPAWN_Z = 3218

        /**
         * Tiles from the gangplank, mooring point or buoy within which a boat counts as docked there. No source: the Wiki
         * only says "in range of" them and "within the perimeter of the buoys" (OPEN-P5).
         */
        const val DOCK_RANGE = 15
        const val RETURN_RANGE = 40
        const val LANDING_SEARCH = 4

        /**
         * Base ids of the helm locs: the helm part rows (db 181) of the raft, skiff and sloop start at these locs and step by 3
         * per tier (bronze .. dragon); the intro boat's helm is 59620 with the variants 59621 / 59622.
         */
        val HELM_LOCS: List<Int> =
            listOf(RAFT_HELM, SKIFF_HELM, SLOOP_HELM).flatMap { start -> (0 until TIERS).map { start + it * HELM_STEP } } +
                listOf(Locs.SAILING_INTRO_NAVIGATING, Locs.SAILING_INTRO_HELM, Locs.SAILING_INTRO_HELM_NOT_IN_USE)
        const val RAFT_HELM = 59554
        const val SKIFF_HELM = 59576
        const val SLOOP_HELM = 59598
        const val HELM_STEP = 3
        const val TIERS = 7
    }
}
