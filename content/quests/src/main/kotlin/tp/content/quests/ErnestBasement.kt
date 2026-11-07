package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.symbols.Locs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The secret room and the lever maze under Draynor Manor (docs/mechanics/quest-ernest-the-chicken.md EC6-EC9). Sources: OSRS Wiki
 * "Ernest the Chicken" (oil can checklist), "Draynor Manor" (lever table), "Bookcase (Draynor Manor)", "Lever (Draynor Manor)";
 * cache locs 132-161 and varbits 1788-1802 (`ERNESTLEVER_A..F`, `ERNESTDOOR_*`), which the cache itself uses to draw the lever and
 * door models, so the server only writes them.
 */
public class ErnestBasement : ContentPlugin {
    override fun ContentScope.register() {
        // The bookcase's "Search" opens the secret door and the player walks in (the open model is not paired, OPEN-QUA6).
        onOpLoc(Locs.HAUNTEDBOOKCASEL, OP_1) { _ -> player.teleport(SECRET_ROOM) }
        onOpLoc(Locs.HAUNTEDBOOKCASER, OP_1) { _ -> player.teleport(SECRET_ROOM) }
        onOpLoc(Locs.HAUNTEDLEVERUP, OP_1) { _ -> leaveSecretRoom() }
        onOpLoc(Locs.PUZZLE_LADDER_TOP, OP_1) { _ -> climb(down = true) }
        onOpLoc(Locs.PUZZLE_LADDER, OP_1) { _ -> climb(down = false) }
        for ((index, lever) in LEVER_LOCS.withIndex()) onOpLoc(lever, OP_1) { _ -> pull(index) }
        for (door in DOORS) onOpLoc(door.loc, OP_1) { loc -> cross(door, loc) }
        onLogin { syncDoors() }
    }

    /** The ladders between the secret room and the basement (climb animation, one tick, then the move; as `Climbing`). */
    private suspend fun PlayerScript.climb(down: Boolean) {
        val p = player
        p.anim(if (down) Seqs.HUMAN_PICKUPFLOOR else Seqs.HUMAN_REACHFORLADDER)
        delay(1)
        if (down) {
            // Wiki: "The levers can be reset by leaving the room and returning."
            for (lever in LEVER_VARBITS) p.setVarbit(lever, 0)
            p.syncDoors()
            p.teleport(BASEMENT_LANDING)
        } else {
            p.teleport(SECRET_ROOM_LADDER)
        }
    }

    /** Wiki "Lever (Draynor Manor)": "The lever opens the secret door!" and the player walks out. */
    private suspend fun PlayerScript.leaveSecretRoom() {
        player.anim(Seqs.HUMAN_LEVERDOWN)
        delay(1)
        player.message("The lever opens the secret door!")
        player.teleport(SECRET_ROOM_EXIT)
    }

    /** A basement lever toggles between up (0) and down (1) and every door is checked against the new positions. */
    private fun PlayerScript.pull(index: Int) {
        val p = player
        val down = p.varbit(LEVER_VARBITS[index]) == 0
        p.anim(if (down) Seqs.HUMAN_LEVERDOWN else Seqs.HUMAN_LEVERUP)
        p.setVarbit(LEVER_VARBITS[index], if (down) 1 else 0)
        p.syncDoors()
    }

    /**
     * A door whose levers are right shows "Open" (door varbit 1; closed it is a plain "Door" without option, loc 11449 vs 11450);
     * the player is moved to the tile across the door, as the cache has no open model for it (OPEN-QUC2).
     */
    private fun PlayerScript.cross(door: Door, loc: LocRef) {
        val p = player
        if (p.varbit(door.varbit) != DOOR_OPEN) return
        val d = loc.coord
        val here = p.coord
        // Doors of angle 0 sit on a west-east wall line, doors of angle 3 on a north-south one.
        val across =
            if (loc.angle and 1 == 0) {
                CoordGrid(if (here.x > d.x) d.x - 1 else d.x + 1, d.z, d.level)
            } else {
                CoordGrid(d.x, if (here.z > d.z) d.z - 1 else d.z + 1, d.level)
            }
        p.teleport(across)
    }

    private fun PlayerRef.syncDoors() {
        for (door in DOORS) setVarbit(door.varbit, if (door.isOpen(this)) DOOR_OPEN else DOOR_CLOSED)
    }

    /**
     * A basement door: the cache loc [loc], its varbit and the lever positions it needs, one char per lever A..F: `D` down,
     * `U` up, `.` not affected (Wiki "Draynor Manor" table; the cells the Wiki marks "?" are taken as printed, OPEN-QUC1).
     */
    private class Door(val loc: Int, val varbit: Int, val needs: String) {
        fun isOpen(p: PlayerRef): Boolean =
            needs.indices.all { i ->
                when (needs[i]) {
                    'D' -> p.varbit(LEVER_VARBITS[i]) == LEVER_DOWN
                    'U' -> p.varbit(LEVER_VARBITS[i]) == LEVER_UP
                    else -> true
                }
            }
    }

    private companion object {
        const val OP_1 = 1
        const val LEVER_UP = 0
        const val LEVER_DOWN = 1
        const val DOOR_CLOSED = 0
        const val DOOR_OPEN = 1

        val LEVER_LOCS = intArrayOf(Locs.LEVERA, Locs.LEVERB, Locs.LEVERC, Locs.LEVERD, Locs.LEVERE, Locs.LEVERF)
        val LEVER_VARBITS =
            intArrayOf(
                Varbits.ERNESTLEVER_A,
                Varbits.ERNESTLEVER_B,
                Varbits.ERNESTLEVER_C,
                Varbits.ERNESTLEVER_D,
                Varbits.ERNESTLEVER_E,
                Varbits.ERNESTLEVER_F,
            )

        /**
         * The Wiki's doors 1..9 matched to the cache's room-to-room door names (the cache labels its 3x3 room grid 1..9 from the
         * north-east; positions in `cache-dump map 48 152`): Wiki 1 = 4to7 (3108,9758), 2 = 4to5, 3 = 5to8, 4 = 5to6, 5 = 3to6,
         * 6 = 2to3, 7 = 1to2, 8 = 2to5, 9 = 8to9 (3100,9755, the oil can room). The match follows the Wiki map description.
         */
        val DOORS =
            listOf(
                Door(Locs._4TO7, Varbits.ERNESTDOOR_4TO7, "DDU.UU"),
                Door(Locs._4TO5, Varbits.ERNESTDOOR_4TO5, "D.U.UU"),
                Door(Locs._5TO8, Varbits.ERNESTDOOR_5TO8, "...DU."),
                Door(Locs._5TO6, Varbits.ERNESTDOOR_5TO6, "...DU."),
                Door(Locs._3TO6, Varbits.ERNESTDOOR_3TO6, ".U.D.U"),
                Door(Locs._2TO3, Varbits.ERNESTDOOR_2TO3, ".U.D.D"),
                Door(Locs._1TO2, Varbits.ERNESTDOOR_1TO2, "UU..DD"),
                Door(Locs._2TO5, Varbits.ERNESTDOOR_2TO5, "...DUD"),
                Door(Locs._8TO9, Varbits.ERNESTDOOR_8TO9, "..DD.D"),
            )

        /** Inside the secret room, west of the bookcase (3097,3358-3359; cache map 48 52). */
        val SECRET_ROOM = CoordGrid(3096, 3358, 0)

        /** East of the bookcase, in the manor's west room: where the lever lets the player out. */
        val SECRET_ROOM_EXIT = CoordGrid(3098, 3358, 0)

        /** Beside the ladder loc 133 at (3092,3362) in the secret room. */
        val SECRET_ROOM_LADDER = CoordGrid(3093, 3362, 0)

        /** Beside the basement ladder loc 132 at (3117,9754), in the entrance room with levers A and B. */
        val BASEMENT_LANDING = CoordGrid(3116, 9754, 0)
    }
}
