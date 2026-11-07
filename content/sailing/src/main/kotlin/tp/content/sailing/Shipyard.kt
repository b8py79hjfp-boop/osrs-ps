package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** A player's visit to the shipyard with the boat of [slot]: the boat standing in the build space and where to go back to. */
internal class ShipyardSession(val slot: Int, val boat: ActiveBoat, val dockId: Int, val back: CoordGrid)

/**
 * The Shipyard (docs/mechanics/sailing-building.md SY1-SY6): the island south-west of the Isle of Souls where boats are
 * customised. A shipwright's "Customise-boat" takes a boat that is moored at that port into the build space (the live boat of
 * the port is removed while it is here) and the player to the island; the boat schematics board changes the core parts, a
 * facility hotspot on the deck changes the facilities, and the Shipyard Portal (or the helm's Escape on the deck, or a logout)
 * takes the player back near the shipwright and puts the boat back at its port.
 */
internal class Shipyard(private val state: SailingState) {
    val builder: BoatBuilder = BoatBuilder(state)
    val customisation: Customisation = Customisation(state, builder)

    fun ContentScope.register() {
        for (npc in SHIPWRIGHT_NPCS) onOpNpc(npc, OP_CUSTOMISE) { with(this@Shipyard) { customise(it.coord) } }
        onOpLoc(Locs.SAILING_SHIPYARD_SCHEMATICS, OP_SCHEMATICS) {
            player.stopInteraction()
            board(player)
        }
        onOpLoc(Locs.SAILING_SHIPYARD_PORTAL_EXIT, OP_PORTAL) {
            player.stopInteraction()
            exit(player)
        }
        for (loc in PLACEHOLDER_LOCS) {
            onOpLoc(loc, OP_BUILD) {
                player.stopInteraction()
                buildAt(player, it)
            }
        }
        for (loc in FACILITY_LOCS) {
            onOpLoc(loc, OP_MODIFY) { with(this@Shipyard) { modify(it) } }
            onUseOnLoc(Objs.SAILING_FACILITY_BOTTLE_EMPTY, loc) { with(this@Shipyard) { bottle(it) } }
        }
        with(customisation) { register() }
    }

    // ---- sessions

    /** The shipyard session of [p], or null. */
    fun session(p: PlayerRef): ShipyardSession? = p[SESSION]?.takeIf { valid(p, it) }

    /** Whether [s] is [p]'s session and its boat still stands. */
    fun valid(p: PlayerRef, s: ShipyardSession): Boolean = p[SESSION] === s && ActiveBoats.at(p, s.boat.ref) === s.boat

    /** Whether [p] stands on the deck of the boat of [s]. */
    fun aboard(p: PlayerRef, s: ShipyardSession): Boolean = p.coord in s.boat.ref

    /** Whether [boat] is a boat standing in the build space. */
    fun owns(boat: ActiveBoat): Boolean = boat.dock == DOCK

    /** Opens the customisation of the core parts at the boat schematics board. */
    private fun board(p: PlayerRef) {
        val s = session(p) ?: return p.message("You need to bring a boat here first. Ask a shipwright to customise it.")
        customisation.open(p, s, null)
    }

    /** "Build" on a facility hotspot of the deck: the facility menu of that hotspot. */
    private fun buildAt(p: PlayerRef, loc: LocRef) {
        val s = deckSession(p) ?: return
        val hotspot = state.boats.hotspotAt(s.boat, loc.coord) ?: return
        customisation.open(p, s, hotspot)
    }

    private fun deckSession(p: PlayerRef): ShipyardSession? {
        val s = session(p)
        if (s == null || !aboard(p, s)) {
            p.message("You can only modify your boat from its deck in the shipyard.")
            return null
        }
        return s
    }

    // ---- entering and leaving

    /** A shipwright's Customise-boat: choose a boat moored at the shipwright's port and take it to the shipyard. */
    suspend fun PlayerScript.customise(npcTile: CoordGrid) {
        player.stopInteraction()
        val site = Ports.nearest(state.sites, npcTile) ?: return player.message("The shipwright can't help you here.")
        if (player.baseLevel(tp.plugin.Stats.SAILING) < site.dock.levelRequired) {
            return player.message("You need level ${site.dock.levelRequired} Sailing to dock at ${site.dock.name}.")
        }
        val eligible =
            BoatSave.owned(player).filter { slot ->
                val owned = BoatSave.get(player, slot)
                owned != null && !owned.bottled && !owned.capsized && owned.dock == site.dock.id
            }
        if (eligible.isEmpty()) return player.message("You don't have a boat moored here that can be customised.")
        val slot = with(BoatPicker) { pickBoat(state, "Which boat do you want to customise?", eligible) } ?: return
        enter(player, slot, site.dock.id)
    }

    /** Takes the boat of [slot] (moored at [dockId]) to the build space and [p] to the island; the live boat of the port goes. */
    fun enter(p: PlayerRef, slot: Int, dockId: Int) {
        val owned = BoatSave.get(p, slot) ?: return
        val name = p.username.lowercase()
        val live = ActiveBoats.ownedBy(p, name)
        if (live != null) {
            if (p.playersInWorld().any { it.coord in live.ref }) return p.message("Everyone must get off your boat first.")
            state.boats.despawn(p, live)
        }
        val boat =
            state.boats.spawn(p, name, slot, owned, BUILD_POSE, shipyard = true) ?: return p.message("That boat can't be customised.")
        p[SESSION] = ShipyardSession(slot, boat, dockId, p.coord)
        p.teleport(landing(p))
        p.message("You arrive at the shipyard. Use the boat schematics board to change the parts of your boat.")
    }

    /** Ends [p]'s visit: everyone aboard is put on the island, the boat returns to its port (it was never moved) and [p] goes back. */
    fun exit(p: PlayerRef) {
        val s = p[SESSION] ?: return p.message("You have no boat here.")
        close(p, s)
    }

    /** A logout inside the shipyard (Wiki Shipyard: the player is placed outside near the shipwright used). */
    fun logout(p: PlayerRef) {
        p[SESSION]?.let { close(p, it) }
    }

    /** The Escape option of the helm on a build-space boat: get off the deck onto the island. */
    fun leaveDeck(p: PlayerRef) {
        p.setVarbit(Varbits.SAILING_PLAYER_IS_ON_PLAYER_BOAT, 0)
        p.setVarbit(Varbits.SAILING_BOAT_FACILITY_LOCKEDIN, 0)
        p.teleport(landing(p))
    }

    private fun close(p: PlayerRef, s: ShipyardSession) {
        p[SESSION] = null
        customisation.close(p)
        for (q in p.playersInWorld()) if (q.coord in s.boat.ref) leaveDeck(q)
        state.boats.motion(p)?.stopNavigating(p)
        if (ActiveBoats.at(p, s.boat.ref) === s.boat) {
            BoatSave.get(p, s.slot)?.let { BoatSave.set(p, s.slot, it.copy(hp = s.boat.hp)) }
            state.boats.despawn(p, s.boat)
        }
        p.setVarbit(Varbits.SAILING_PLAYER_IS_ON_PLAYER_BOAT, 0)
        p.teleport(s.back)
    }

    /** The first walkable tile around the arrival point on the island, searching outward. */
    private fun landing(p: PlayerRef): CoordGrid {
        for (r in 0..LANDING_SEARCH) {
            for (dx in -r..r) {
                for (dz in -r..r) {
                    val tile = ARRIVAL.translate(dx, dz)
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) == r && p.isWalkable(tile)) return tile
                }
            }
        }
        return ARRIVAL
    }

    // ---- facilities on the deck

    /** "Modify" on a built facility (op 5): change it, remove it or leave it. */
    private suspend fun PlayerScript.modify(loc: LocRef) {
        player.stopInteraction()
        val s = deckSession(player) ?: return
        val hotspot = state.boats.hotspotAt(s.boat, loc.coord) ?: return
        when (choice("Modify the facility", "Change it", "Remove it", "Never mind")) {
            1 -> {
                customisation.open(player, s, hotspot)
            }

            2 -> {
                if (builder.removeFacility(player, s, hotspot)) player.message("You take the facility apart. Nothing is returned.")
            }

            else -> {}
        }
    }

    /** An empty facility bottle used on a built facility: the facility is stored in the bottle (Wiki Facility bottle). */
    private suspend fun PlayerScript.bottle(loc: LocRef) {
        player.stopInteraction()
        val s = deckSession(player) ?: return
        val hotspot = state.boats.hotspotAt(s.boat, loc.coord) ?: return
        val slot = used?.slot ?: return
        store(player, s, hotspot, slot)?.let { player.message(it) }
    }

    /** Stores the facility of [hotspot] in the empty bottle in backpack slot [slot]; the message, or null if the click does not apply. */
    private fun store(p: PlayerRef, s: ShipyardSession, hotspot: Int, slot: Int): String? {
        val built = builder.builtAt(s, hotspot) ?: return null
        if (p.inv(Invs.INV).obj(slot) != Objs.SAILING_FACILITY_BOTTLE_EMPTY) return null
        val kind = built.facility.bottleId
        val problem = if (kind == null) "That can't be stored in a bottle." else builder.removalBlock(p, s, hotspot)
        val stored =
            problem == null && kind != null &&
                p.transaction("sailing facility bottle") {
                    replaceAt(Invs.INV, slot, Objs.SAILING_FACILITY_BOTTLE_EMPTY, Objs.SAILING_FACILITY_BOTTLE_FULL)
                }
        if (stored && kind != null) {
            FacilityBottles.add(p, kind)
            builder.removeFacility(p, s, hotspot)
        }
        return problem ?: if (stored) "You store the ${built.facility.name.lowercase()} in the bottle." else null
    }

    internal companion object {
        val SESSION = AttrKey<ShipyardSession>("sailing.shipyard")

        /**
         * Pseudo dock id of a boat standing in the build space (`Dock.id` is 0..60, [BoatSave.AT_SEA] 63): the boat counts as
         * moored so its owner can board it, but no dock site exists for it.
         */
        const val DOCK: Int = 62

        /** Loc ops: schematics "Modify", portal "Exit", hotspot placeholder "Build" (cache 2757) and the facilities' op 5 "Modify". */
        const val OP_SCHEMATICS = 1
        const val OP_PORTAL = 1
        const val OP_BUILD = 1
        const val OP_MODIFY = 5

        /** Hotspots per boat type (`Sailing_Boat.FACILITY_AMOUNT`: raft 1, skiff 7, sloop 13) for the placeholder locs. */
        private val HOTSPOT_COUNTS = mapOf(BoatTemplates.RAFT to 1, BoatTemplates.SKIFF to 7, BoatTemplates.SLOOP to 13)

        /** The "Facility hotspot" locs (op 1 "Build") of every boat type; known at registration, before the cache tables are read. */
        val PLACEHOLDER_LOCS: List<Int> =
            HOTSPOT_COUNTS.flatMap { (type, count) -> (0 until count).map { Boats.placeholderLoc(type, it) } }.distinct()

        /**
         * The base locs of the facilities (`Sailing_Boat_Facility.LOC` and `ALTERNATE_LOCS`, cache 2757), which carry op 5
         * "Modify" in the shipyard. Registered by id because content registers before the db tables exist; [ShipbuildingData.check]
         * fails the boot if the cache names a facility loc that is missing here.
         */
        val FACILITY_LOCS: List<Int> =
            locRanges(
                "29533,29538-29544,59446-59452,59682-59687,59689,59691-59699,59700,59701,59703,59704,59706,59715,59716," +
                    "59743-59768,60245-60285,60483-60510,60722",
            )

        /** Parses "a,b-c,d" into the ids it lists (no sorting, duplicates removed). */
        private fun locRanges(text: String): List<Int> =
            text
                .split(',')
                .flatMap { part ->
                    val bounds = part.split('-')
                    (bounds.first().toInt()..bounds.last().toInt()).toList()
                }.distinct()

        /** Shipwright op 4 "Customise-boat" (cache 2757: op3 "Buy-boat", op4 "Customise-boat", op5 "Recover-boat"). */
        const val OP_CUSTOMISE = 4

        /**
         * The build space (OPEN-SY1): the dry dock of the shipyard island, the walkable 11 x 8 basin at x 2088..2098, z 2720..2727
         * edged by the pier locs (`collision 2096 2723 0`). Db 174 `Sailing_Shipyard` would say, but its server columns are
         * stripped. The boat faces east.
         */
        val BUILD_POSE = Pose(CoordGrid(2093, 2724, 0), 1536)

        /** Where a player arrives: next to the boat schematics board (2082, 2724) and Shipwright Seb (OPEN-SY1). */
        val ARRIVAL = CoordGrid(2084, 2725, 0)
        const val LANDING_SEARCH = 6

        /** The shipwright NPCs: 15 port shipwrights and the four variants of Junior Jim (Wiki Shipwright). */
        val SHIPWRIGHT_NPCS: List<Int> =
            (Npcs.SAILING_SHIPWRIGHT_PORT_SARIM..Npcs.SAILING_SHIPWRIGHT_PRIFDDINAS).toList() +
                (Npcs.JUNIOR_JIM..Npcs.JUNIOR_JIM_4OPS).toList()
    }
}

/** Port lookups shared by the shipwright services. */
internal object Ports {
    /** The dock whose land tile (gangplank or mooring point) is nearest to [tile], within [MAX_DISTANCE] tiles, or null. */
    fun nearest(sites: DockSites, tile: CoordGrid): DockSite? =
        sites.all
            .filter { it.land != null }
            .minByOrNull { it.land?.chebyshev(tile) ?: Int.MAX_VALUE }
            ?.takeIf { (it.land?.chebyshev(tile) ?: Int.MAX_VALUE) <= MAX_DISTANCE }

    /** A shipwright stands within this many tiles of the gangplank of his port (the farthest is Musa Point, about 40; OPEN-SY3). */
    const val MAX_DISTANCE = 60
}
