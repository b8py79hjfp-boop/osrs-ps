package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.TimerId
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** A house portal outside a town: loc [loc], house location number [location] (Wiki order) and the loc's south-west tile. */
internal class HousePortal(val loc: Int, val location: Int, val name: String, val x: Int, val z: Int)

/**
 * Player-owned houses (docs/mechanics/construction.md H1-H8): the house portals, entering and leaving the instanced house,
 * build mode, guests and the build-mode actions ([HouseMenus]).
 *
 * A house is an instance ([House]) built from the saved layout on entry and destroyed when the last player has left. The
 * owner's layout lives in saved values ([PohSave]).
 */
public class PlayerOwnedHouse : ContentPlugin {
    override fun ContentScope.register() {
        for (portal in PORTALS) {
            onOpLoc(portal.loc, OP_ENTER) { enterMenu(player, portal) }
            onOpLoc(portal.loc, OP_HOME) { enterOwn(player, portal, buildMode = false) }
            onOpLoc(portal.loc, OP_BUILD_MODE) { enterOwn(player, portal, buildMode = true) }
            onOpLoc(portal.loc, OP_FRIEND) { player.stringDialog("Enter name:") { name -> enterFriend(this, portal, name) } }
        }
        onOpLoc(Locs.POH_EXIT_PORTAL, OP_ENTER) { exit(player) }
        onOpLoc(Locs.POH_EXIT_PORTAL, OP_LOCK) { toggleLock(player) }
        onOpLoc(Locs.POH_EXIT_PORTAL, OP_REMOVE) { player.message("You can't remove the exit portal.") }
        for (name in PohRooms.hotspotNames()) onOpLocNamed(name, BUILD) { with(HouseMenus) { build(it) } }
        onOpLocNamed(PohRooms.DOOR_HOTSPOT, BUILD) { with(HouseMenus) { doorway(it) } }
        for (loc in PohFurniture.builtLocs) {
            if (loc != Locs.POH_EXIT_PORTAL) onOpLoc(loc, OP_REMOVE) { with(HouseMenus) { removePiece(it) } }
        }
        onButton(Components.Poh_Options.BUILD_MODE_ON) { setMode(this, true) }
        onButton(Components.Poh_Options.BUILD_MODE_OFF) { setMode(this, false) }
        onButton(Components.Poh_Options.LEAVE_HOUSE) { exit(this) }
        onItemOp(OP_TELE_POH) { teleportHome(this, it) }
        onLogin { setVarbit(Varbits.POH_BUILDING_MODE, 0) }
        onLogout { exit(this) }
    }

    // ---- entering

    /** Op 1 "Enter": the four choices of the Wiki's "Portal (Player-owned house)". */
    private suspend fun PlayerScript.enterMenu(p: PlayerRef, portal: HousePortal) {
        when (choice("Go to...", "Your house", "Your house (building mode)", "A friend's house", "Never mind")) {
            ENTER_HOME -> enterOwn(p, portal, buildMode = false)
            ENTER_BUILD -> enterOwn(p, portal, buildMode = true)
            ENTER_FRIEND -> p.stringDialog("Enter name:") { name -> enterFriend(this, portal, name) }
        }
    }

    /**
     * H2: enter your own house, if it is at this portal's location. The first visit gives the starter layout. Entering in build
     * mode needs a house with no guests in it.
     */
    private fun enterOwn(p: PlayerRef, portal: HousePortal, buildMode: Boolean) {
        if (p[House.CURRENT] != null) return
        val location = (p.saved(LOCATION_KEY) ?: DEFAULT_LOCATION).toInt()
        if (location != portal.location) return p.message("Your house is not in this area.")
        val houses = houses(p)
        val key = p.username.lowercase()
        val live = houses[key]?.takeIf { !it.instance.destroyed }
        val house =
            when {
                live == null -> create(p, portal, buildMode)
                live.players.isEmpty() -> live.also { if (it.buildMode != buildMode) HouseBuilder.setBuildMode(p, it, buildMode) }
                else -> live.also { if (buildMode) p.message("Building mode cannot be turned on while guests are in the house.") }
            }
        houses[key] = house
        join(p, house, p.coord)
        Servants.spawn(p, house)
    }

    /**
     * CO15: the Construction cape's backpack op "Tele to POH" enters your own house wherever you are, as often as you like (Wiki
     * "Construct. cape": "unlimited teleports to the player's own house"). Leaving returns you to the house portal outside.
     */
    private fun teleportHome(p: PlayerRef, click: tp.plugin.ButtonClick) {
        val inv = p.inv(Invs.INV)
        val cape = click.obj == Objs.SKILLCAPE_CONSTRUCTION || click.obj == Objs.SKILLCAPE_CONSTRUCTION_TRIMMED
        val held = click.slot in 0 until inv.size && inv.obj(click.slot) == click.obj
        if (!cape || !held || p[House.CURRENT] != null) return
        val location = (p.saved(LOCATION_KEY) ?: DEFAULT_LOCATION).toInt()
        val portal = PORTALS.firstOrNull { it.location == location } ?: return
        enterOwn(p, portal, buildMode = false)
        val house = p[House.CURRENT] ?: return
        p[House.RETURN] = portalOutside(p, house)
    }

    private fun create(p: PlayerRef, portal: HousePortal, buildMode: Boolean): House {
        val layout = PohSave.load(p) ?: HouseLayout.starter().also { PohSave.save(p, it) }
        val style = (p.saved(STYLE_KEY) ?: 0L).toInt().coerceIn(0, PohRooms.STYLES - 1)
        return HouseBuilder.create(p, layout, style, buildMode).also { it.location = portal.location }
    }

    /** H2: "Friend's house": join the live house of the player named [name] on this world (OPEN-CO9: no ignore list, no Ironman rule). */
    private fun enterFriend(p: PlayerRef, portal: HousePortal, name: String) {
        if (p[House.CURRENT] != null) return
        val house = houses(p)[name.trim().lowercase()]?.takeIf { !it.instance.destroyed && it.location == portal.location }
        when {
            house == null -> p.message("That player isn't at home.")
            house.buildMode -> p.message("That player is in building mode.")
            house.locked -> p.message("That player's house is locked.")
            else -> join(p, house, p.coord)
        }
    }

    private fun join(p: PlayerRef, house: House, from: CoordGrid) {
        house.players += p
        p[House.CURRENT] = house
        p[House.RETURN] = from
        p.setVarbit(Varbits.POH_BUILDING_MODE, if (house.buildMode && house.owner == p.username.lowercase()) 1 else 0)
        p.timer(WATCH, WATCH_TICKS, soft = true) { watch(this) }
        p.teleport(HouseBuilder.arrival(p, house))
    }

    // ---- leaving

    /** Op 1 on the exit portal (and the options panel's "Leave house"): back outside. */
    private fun exit(p: PlayerRef) {
        val house = p[House.CURRENT] ?: return
        val to = p[House.RETURN] ?: portalOutside(p, house)
        p.teleport(to)
        leave(p, house)
    }

    /** The player is no longer in [house] (walked out, teleported, logged out): the last one out destroys it. */
    private fun leave(p: PlayerRef, house: House) {
        p[House.CURRENT] = null
        p[House.RETURN] = null
        p.stopTimer(WATCH)
        p.setVarbit(Varbits.POH_BUILDING_MODE, 0)
        house.players -= p
        if (house.players.isEmpty() && !house.instance.destroyed) {
            Servants.despawn(house)
            houses(p).remove(house.owner)
            house.instance.destroy()
        }
    }

    /** Soft timer while inside: a teleport or any other way out of the instance counts as leaving. */
    private fun watch(p: PlayerRef) {
        val house = p[House.CURRENT] ?: return p.stopTimer(WATCH)
        if (house.instance.destroyed || p.instanceAt(p.coord) != house.instance) leave(p, house)
    }

    /** The walkable tile next to the portal of [house]'s location, when the player did not walk in (nothing remembered). */
    private fun portalOutside(p: PlayerRef, house: House): CoordGrid {
        val portal = PORTALS.firstOrNull { it.location == house.location } ?: PORTALS.first()
        val front = CoordGrid(portal.x + PORTAL_WIDTH / 2, portal.z - 1)
        val ring = ArrayList<CoordGrid>()
        for (dx in -1..PORTAL_WIDTH) for (dz in -1..PORTAL_LENGTH) ring += CoordGrid(portal.x + dx, portal.z + dz)
        return ring
            .filter { it.x !in portal.x until portal.x + PORTAL_WIDTH || it.z !in portal.z until portal.z + PORTAL_LENGTH }
            .sortedBy { (it.x - front.x) * (it.x - front.x) + (it.z - front.z) * (it.z - front.z) }
            .firstOrNull { p.isWalkable(it) } ?: front
    }

    // ---- options

    private fun toggleLock(p: PlayerRef) {
        val house = p[House.CURRENT] ?: return
        if (house.owner != p.username.lowercase()) return p.message("Only the owner can do that.")
        house.locked = !house.locked
        p.message(if (house.locked) "Your house is now locked to visitors." else "Your house is now open to visitors.")
    }

    /** The options panel's "Build mode" buttons: switch the live house; guests block turning it on. */
    private fun setMode(p: PlayerRef, on: Boolean) {
        val house = p[House.CURRENT]
        if (house == null) {
            p.setVarbit(Varbits.POH_BUILDING_MODE, if (on) 1 else 0)
            return
        }
        if (house.owner != p.username.lowercase() || house.buildMode == on) return
        if (on && house.players.size > 1) return p.message("Building mode cannot be turned on while guests are in the house.")
        HouseBuilder.setBuildMode(p, house, on)
        p.setVarbit(Varbits.POH_BUILDING_MODE, if (on) 1 else 0)
    }

    private fun houses(p: PlayerRef): HashMap<String, House> =
        p.worldAttr(House.HOUSES) ?: HashMap<String, House>().also { p.setWorldAttr(House.HOUSES, it) }

    private companion object {
        const val OP_ENTER = 1
        const val OP_HOME = 2
        const val OP_BUILD_MODE = 3
        const val OP_FRIEND = 4
        const val OP_LOCK = 2
        const val OP_REMOVE = 5
        const val BUILD = "Build"

        /** Cache 2757: the Construction cape's backpack action 3. */
        const val OP_TELE_POH = "Tele to POH"

        /** The 1-based picks of [enterMenu]. */
        const val ENTER_HOME = 1
        const val ENTER_BUILD = 2
        const val ENTER_FRIEND = 3

        /** Saved: the house location (Wiki order: 0 Rimmington, the default) and house style (0 = first template column). */
        const val LOCATION_KEY = "poh.location"
        const val STYLE_KEY = "poh.style"
        const val DEFAULT_LOCATION = 0L

        val WATCH = TimerId("poh.watch")
        const val WATCH_TICKS = 2

        /** House portals are 5x2 locs (cache 2757: ids 15477-15482, 28822, 34947; Aldarin's is 5x1). */
        const val PORTAL_WIDTH = 5
        const val PORTAL_LENGTH = 2

        /** The nine portals in the Wiki's location order, with the south-west tile of each (cache 2757 `find-locs`). */
        val PORTALS: List<HousePortal> =
            listOf(
                HousePortal(Locs.POH_RIMMINGTON_PORTAL, 0, "Rimmington", 2951, 3222),
                HousePortal(Locs.POH_TAVERLY_PORTAL, 1, "Taverley", 2891, 3463),
                HousePortal(Locs.POH_POLLNIVNEACH_PORTAL, 2, "Pollnivneach", 3338, 3001),
                HousePortal(Locs.POH_KOUREND_PORTAL, 3, "Hosidius", 1740, 3515),
                HousePortal(Locs.POH_RELLEKKA_PORTAL, 4, "Rellekka", 2668, 3629),
                HousePortal(Locs.POH_ALDARIN_PORTAL, 5, "Aldarin", 1420, 2962),
                HousePortal(Locs.POH_BRIMHAVEN_PORTAL, 6, "Brimhaven", 2755, 3176),
                HousePortal(Locs.POH_YANILLE_PORTAL, 7, "Yanille", 2542, 3097),
                HousePortal(Locs.POH_PRIFDDINAS_PORTAL, 8, "Prifddinas", 3237, 6077),
            )
    }
}
