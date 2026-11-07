package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * The links between the floors of a house (docs/mechanics/construction.md CO23): staircases, the oubliette ladder, the throne
 * room trapdoor and the garden's dungeon entrance. A floor is an instance level (basement 0, ground 1, upper 2); climbing moves
 * the player to the room of the next floor in the same grid cell. In building mode the owner may add the missing room from the
 * staircase.
 */
public class PohFloors : ContentPlugin {
    override fun ContentScope.register() {
        for ((up, down) in PohFurniture.STAIRS.values) {
            onOpLoc(up, OP_CLIMB) { climb(it, up = true) }
            onOpLoc(down, OP_CLIMB) { climb(it, up = false) }
        }
        for (spiral in PohFurniture.SPIRALS.values) {
            onOpLoc(spiral, OP_CLIMB) { chooseWay(it) }
            onOpLoc(spiral, OP_CLIMB_UP) { climb(it, up = true) }
            onOpLoc(spiral, OP_CLIMB_DOWN) { climb(it, up = false) }
        }
        onOpLoc(DUNGEON_ENTRANCE, OP_CLIMB) { climb(it, up = false) }
        for (ladder in LADDERS) onOpLoc(ladder, OP_CLIMB) { climb(it, up = true, animation = Seqs.HUMAN_REACHFORLADDER) }
        for (trapdoor in OPEN_TRAPDOORS) onOpLoc(trapdoor, OP_CLIMB) { climb(it, up = false, animation = LADDER_DOWN) }
    }

    /** The spiral staircase's plain "Climb": ask which way (Wiki "Staircase": "a message will ask the player to choose"). */
    private suspend fun PlayerScript.chooseWay(loc: LocRef) {
        when (choice("Climb up or down?", "Climb up", "Climb down", "Cancel")) {
            1 -> climb(loc, up = true)
            2 -> climb(loc, up = false)
        }
    }

    private suspend fun PlayerScript.climb(loc: LocRef, up: Boolean, animation: Int = NO_ANIMATION) {
        val p = player
        val house = p[House.CURRENT] ?: return
        val group = house.groupAt(loc.coord) ?: return
        val from = group.room
        val floor = from.floor + if (up) 1 else -1
        if (floor !in PohRooms.BASEMENT..PohRooms.UPPER) return p.message("You can't go any further that way.")
        val target = house.roomAt(floor, from.x, from.z) ?: return offerRoom(house, group, floor)
        if (animation != NO_ANIMATION) {
            p.anim(animation)
            delay(LADDER_TICKS)
            if (house.instance.destroyed || p[House.CURRENT] !== house) return
        }
        p.teleport(landing(p, house, target, loc.coord.translate(0, 0, floor - from.floor)))
    }

    /**
     * The tile to arrive on: the nearest walkable tile to the tile of the loc one floor over, inside the target room (a built
     * staircase there blocks its own tiles).
     */
    private fun landing(p: PlayerRef, house: House, room: PlacedRoom, near: CoordGrid): CoordGrid {
        val origin = house.cellOrigin(room)
        for (r in 0..PohRooms.ZONE) {
            for (dz in -r..r) {
                for (dx in -r..r) {
                    if (maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz)) != r) continue
                    val tile = near.translate(dx, dz)
                    val inside = tile.x - origin.x in 0 until PohRooms.ZONE && tile.z - origin.z in 0 until PohRooms.ZONE
                    if (inside && p.isWalkable(tile)) return tile
                }
            }
        }
        return origin.translate(PohRooms.ZONE / 2, PohRooms.ZONE / 2)
    }

    /**
     * Climbing where no room exists: the owner in building mode is asked which room to add there (Wiki "Oak staircase": "the
     * construction interface will ask if you want to add a room at the top of the staircase. The rooms available at the top are
     * either a quest hall or a skill hall", and a "Down" staircase offers a dungeon stairs room); a trapdoor offers the oubliette.
     */
    private suspend fun PlayerScript.offerRoom(house: House, group: HotspotGroup, floor: Int) {
        val p = player
        val owner = house.owner == p.username.lowercase()
        if (!owner || !house.buildMode) return p.message("You can't go that way yet.")
        val from = group.room
        val types =
            when {
                group.name == TRAPDOOR_SPACE -> listOf(OUBLIETTE)
                floor == PohRooms.UPPER -> listOf(SKILL_HALL, QUEST_HALL)
                floor == PohRooms.BASEMENT -> listOf(DUNGEON_STAIRS)
                else -> emptyList()
            }
        if (types.isEmpty()) return p.message("You can't go that way yet.")
        val costs = ConstructionData.rooms().associateBy { it.name }
        val type =
            with(HouseMenus) {
                pick("Add a room here?", types) {
                    val cost = costs[PohRooms.ALL[it].name]
                    "${PohRooms.ALL[it].label} (level ${cost?.level}, ${cost?.cost} gp)"
                }
            } ?: return
        val cost = costs[PohRooms.ALL[type].name] ?: return
        val level = p.baseLevel(Stats.CONSTRUCTION)
        when {
            house.layout.rooms.size >= HouseBuilder.maxRooms(level) -> {
                return p.message("You have reached the most rooms your Construction level allows.")
            }

            p.level(Stats.CONSTRUCTION) < cost.level -> {
                return p.message("You need a Construction level of ${cost.level} to build that.")
            }

            house.instance.destroyed || house.roomAt(floor, from.x, from.z) != null -> {
                return
            }
        }
        if (!p.transaction("construction room") { remove(Invs.INV, Objs.COINS, cost.cost) }) {
            return p.message("You don't have enough coins to build that.")
        }
        HouseBuilder.addRoom(p, house, PlacedRoom(type, 0, floor, from.x, from.z))
    }

    private companion object {
        /** Cache 2757: staircase op 1 "Climb-up" / "Climb-down", ladder "Climb", open trapdoor "Go-down", dungeon entrance "Enter". */
        const val OP_CLIMB = 1
        const val OP_CLIMB_UP = 2
        const val OP_CLIMB_DOWN = 3

        /** Cache 2757: `Dungeon entrance`, the garden centrepiece that leads down (op 1 "Enter"). */
        const val DUNGEON_ENTRANCE = 4529

        /** Cache 2757: the oak, teak and mahogany ladders of the oubliette (op 1 "Climb"). */
        val LADDERS = listOf(13328, 13329, 13330)

        /** Cache 2757: the open oak, teak and mahogany trapdoors of the throne room (op 1 "Go-down"). */
        val OPEN_TRAPDOORS = listOf(13678, 13679, 13680)

        /** Lower-case name of the throne room's trapdoor hotspot. */
        const val TRAPDOOR_SPACE = "trapdoor space"

        /** Room type ids (indexes of [PohRooms.ALL]) the floor links add. */
        val SKILL_HALL: Int = PohRooms.ALL.indexOfFirst { it.name == "Hall (skill trophies)" }
        val QUEST_HALL: Int = PohRooms.ALL.indexOfFirst { it.name == "Hall (quest trophies)" }
        val OUBLIETTE: Int = PohRooms.ALL.indexOfFirst { it.name == "Oubliette" }
        val DUNGEON_STAIRS: Int = PohRooms.ALL.indexOfFirst { it.label == "Dungeon stairs" }

        /**
         * OPEN-CO7: ladder animations. Animation 828 is the cache's `human_reachforladder` and, as in RS Mod, the way up; 827 is
         * the way down (cache name `human_pickupfloor`); no source ties them to the house ladder.
         */
        const val LADDER_DOWN = 827
        const val LADDER_TICKS = 2
        const val NO_ANIMATION = -1
    }
}
