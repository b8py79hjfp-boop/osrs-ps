package tp.content.skills

import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * The build-mode actions (docs/mechanics/construction.md H5-H6): "Build" on a hotspot, "Remove" on built furniture and "Build"
 * on a door hotspot (room creation and removal). The furniture and room pickers are chatbox option menus, not the cache's
 * `poh_furniture_creation` / `poh_add_room` interfaces (OPEN-CO4).
 */
internal object HouseMenus {
    /** Entries per menu page; the fifth option is "More..." or "Cancel". */
    private const val PAGE = 4
    private const val MORE = "More..."
    private const val CANCEL = "Cancel"
    private const val YES = "Yes"
    private const val NO = "No"

    /** The yes answer of a [YES] / [NO] menu. */
    private const val ANSWER_YES = 1

    /** Hotspot loc shapes: walls are 0..3, wall decor 4..8, ground decor 22 (the client's layer rule). */
    private const val WALL_LAST = 3
    private const val WALL_DECOR_LAST = 8
    private const val GROUND_DECOR = 22

    /** The house the player stands in, if they own it and it is in build mode; else tells them why not. */
    private fun ownBuildHouse(p: PlayerRef): House? {
        val house = p[House.CURRENT]
        return when {
            house == null || house.instance.destroyed -> {
                null
            }

            house.owner != p.username.lowercase() -> {
                p.message("Only the owner can build in this house.")
                null
            }

            !house.buildMode -> {
                p.message("You need to be in building mode to do that.")
                null
            }

            else -> {
                house
            }
        }
    }

    // ---- furniture

    /** Op 5 "Build" on a furniture hotspot: pick a piece, check level, tools and materials, build it. */
    suspend fun PlayerScript.build(loc: LocRef) {
        val house = ownBuildHouse(player) ?: return
        val group = house.groupAt(loc.coord) ?: return
        if (group.built != null) return
        val options = PohFurniture.forHotspot(group.room.template.furnitureKey, group.name)
        if (options.isEmpty()) return player.message("There is nothing you can build here yet.")
        val piece = pick("What do you want to build?", options) { "${it.name} (level ${it.furniture.level})" } ?: return
        val f = piece.furniture
        shortfall(player, f)?.let { return player.message(it) }
        player.anim(animFor(loc.shape))
        delay(f.ticks)
        if (house.instance.destroyed || player[House.CURRENT] !== house || group.built != null) return
        shortfall(player, f)?.let { return player.message(it) }
        val paid = player.transaction("construction") { take(f.inputs) }
        if (!paid) return player.message("You don't have the materials to build that.")
        HouseBuilder.buildFurniture(player, house, group, piece)
        player.addXp(Stats.CONSTRUCTION, f.xp)
    }

    /** Op 5 "Remove" on built furniture: after a confirmation the hotspot is back. Nothing is refunded (Wiki "Construction"). */
    suspend fun PlayerScript.removePiece(loc: LocRef) {
        val house = ownBuildHouse(player) ?: return
        val group = house.groupAt(loc.coord) ?: return
        val piece = group.built ?: return
        if (piece.id == House.EXIT_PORTAL_PIECE) return player.message("You can't remove the exit portal.")
        if (choice("Really remove it?", YES, NO) != ANSWER_YES) return
        if (house.instance.destroyed || group.built !== piece) return
        player.anim(animFor(loc.shape))
        HouseBuilder.removeFurniture(player, house, group)
    }

    /** The first reason [f] cannot be built now (level, tools, materials) as a game message, or null. */
    private fun shortfall(p: PlayerRef, f: Furniture): String? {
        val inv = p.inv(Invs.INV)
        return when {
            p.level(Stats.CONSTRUCTION) < f.level -> "You need a Construction level of ${f.level} to build that."
            f.tools.any { inv.total(it) == 0L } -> "You need ${toolList(p, f)} to build that."
            f.inputs.any { inv.total(it.obj) < it.count } -> "You don't have the materials to build that."
            else -> null
        }
    }

    private fun toolList(p: PlayerRef, f: Furniture): String = f.tools.joinToString(" and ") { "a ${p.objName(it).lowercase()}" }

    /** Wall hotspots use the wall build animation, floor hotspots the floor one, the rest the plain one (OPEN-CO7). */
    private fun animFor(shape: Int): Int =
        when {
            shape == GROUND_DECOR -> Seqs.HUMAN_POH_BUILD_FLOOR
            shape <= WALL_LAST || shape <= WALL_DECOR_LAST -> Seqs.HUMAN_POH_BUILD_WALL
            else -> Seqs.HUMAN_POH_BUILD
        }

    // ---- rooms

    /** Op 5 "Build" on a door hotspot: add the room beyond it, or remove the room that is there. */
    suspend fun PlayerScript.doorway(loc: LocRef) {
        val house = ownBuildHouse(player) ?: return
        val room = house.roomOf(loc.coord) ?: return
        val side =
            house.scans[room]
                ?.doors
                ?.firstOrNull { it.loc.coord == loc.coord }
                ?.side ?: return
        val x = room.x + side.dx
        val z = room.z + side.dz
        val there = house.roomAt(room.floor, x, z)
        val limit = minOf(house.dim, HouseBuilder.maxDimension(player.baseLevel(Stats.CONSTRUCTION)))
        when {
            there != null -> removeRoom(house, there)
            x !in 1..limit || z !in 1..limit -> player.message("You can't build a room there.")
            else -> addRoom(house, room, side, x, z)
        }
    }

    private suspend fun PlayerScript.addRoom(house: House, from: PlacedRoom, side: Side, x: Int, z: Int) {
        if (house.layout.rooms.size >= HouseBuilder.maxRooms(player.baseLevel(Stats.CONSTRUCTION))) {
            return player.message("You have reached the most rooms your Construction level allows.")
        }
        val costs = ConstructionData.rooms().associateBy { it.name }
        val types =
            PohRooms.ALL.indices
                .filter { !PohRooms.ALL[it].basement && PohRooms.rotationsFacing(it, side.opposite).isNotEmpty() }
                .sortedBy { costs[PohRooms.ALL[it].name]?.level ?: 0 }
        val type =
            pick("Choose a room to build", types) {
                val room = PohRooms.ALL[it]
                val cost = costs[room.name]
                "${room.label} (level ${cost?.level}, ${cost?.cost} gp)"
            } ?: return
        val template = PohRooms.ALL[type]
        val cost = costs[template.name] ?: return
        if (player.level(Stats.CONSTRUCTION) <
            cost.level
        ) {
            return player.message("You need a Construction level of ${cost.level} to build that.")
        }
        if (player.inv(Invs.INV).total(Objs.COINS) < cost.cost) return player.message("You don't have enough coins to build that.")
        val turns = PohRooms.rotationsFacing(type, side.opposite)
        val rotation = if (turns.size == 1) turns.first() else pick("Which way should the room face?", turns) { facing(type, it) } ?: return
        if (house.instance.destroyed || house.roomAt(from.floor, x, z) != null) return
        if (!player.transaction("construction room") { remove(Invs.INV, Objs.COINS, cost.cost) }) {
            return player.message("You don't have enough coins to build that.")
        }
        HouseBuilder.addRoom(player, house, PlacedRoom(type, rotation, from.floor, x, z))
    }

    /** Describes a turn of room [type] by the sides its doors then face. */
    private fun facing(type: Int, rotation: Int): String =
        "Doors face " + PohRooms.doors(type, rotation).sortedBy { it.ordinal }.joinToString(", ") { it.name.lowercase() }

    private suspend fun PlayerScript.removeRoom(house: House, room: PlacedRoom) {
        if (house.scans[room]?.groups?.any { it.built?.id == House.EXIT_PORTAL_PIECE } == true) {
            return player.message("You can't remove the room with the exit portal.")
        }
        if (choice("Do you want to remove the ${room.template.label.lowercase()}?", YES, NO) != ANSWER_YES) return
        if (house.instance.destroyed || room !in house.layout.rooms) return
        HouseBuilder.removeRoom(player, house, room)
        if (house.roomOf(player.coord) == null) player.teleport(HouseBuilder.arrival(player, house))
    }

    // ---- menus

    /** Asks for one of [items] on pages of [PAGE]; null if the menu was closed or cancelled. */
    private suspend fun <T> PlayerScript.pick(title: String, items: List<T>, label: (T) -> String): T? {
        if (items.isEmpty()) return null
        var start = 0
        while (true) {
            val page = items.drop(start).take(PAGE)
            val more = start + PAGE < items.size
            val answer = choice(title, *page.map(label).toTypedArray(), if (more) MORE else CANCEL)
            when {
                answer == 0 -> return null
                answer <= page.size -> return page[answer - 1]
                more -> start += PAGE
                else -> return null
            }
        }
    }
}
