package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Locs
import tp.util.CoordGrid

/**
 * The Farming Guild's entrance (docs/mechanics/farming.md FG1; Wiki "Farming Guild"): the double door needs 45 Farming, boosts count.
 * The cache has no open model for the door, so the player is moved across it (the pattern of the other model-less doors, OPEN-FA19).
 * The intermediate (65) and advanced (85) tiers have no loc to click, so their gates are not implemented (OPEN-FA19).
 */
internal class FarmingGuild {
    fun ContentScope.registerGuild() {
        for (door in intArrayOf(Locs.KEBOS_FARMING_GUILD_DOOR_LEFT_CLOSED, Locs.KEBOS_FARMING_GUILD_DOOR_RIGHT_CLOSED)) {
            onOpLoc(door, OP_OPEN) { enter(player, it) }
        }
    }

    private fun enter(p: PlayerRef, door: LocRef) {
        if (p.level(Stats.FARMING) < ENTRY_LEVEL) {
            p.message("You need a Farming level of $ENTRY_LEVEL to enter the Farming Guild.")
            return
        }
        val at = door.coord
        // A wall loc of angle 0 / 1 / 2 / 3 sits on the west / north / east / south edge of its tile (the door is angle 3).
        val across =
            when (door.angle and ANGLE_MASK) {
                0 -> CoordGrid(at.x - 1, at.z, at.level)
                1 -> CoordGrid(at.x, at.z + 1, at.level)
                2 -> CoordGrid(at.x + 1, at.z, at.level)
                else -> CoordGrid(at.x, at.z - 1, at.level)
            }
        p.teleport(if (p.coord == at) across else at)
    }

    private companion object {
        const val OP_OPEN = 1

        /** Wiki "Farming Guild": "To enter the guild, a minimum Farming level of 45 is required." */
        const val ENTRY_LEVEL = 45

        /** Loc angles are 0..3. */
        const val ANGLE_MASK = 3
    }
}
