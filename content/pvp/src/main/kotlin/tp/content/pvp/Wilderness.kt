package tp.content.pvp

import tp.plugin.AttackCheck
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.util.CoordGrid

/**
 * Wilderness levels and combat brackets (docs/mechanics/wilderness.md; OSRS Wiki "Wilderness": "Players within combat
 * level range of each other can attack each other ... calculated by taking the combat level and adding or subtracting
 * the current Wilderness level").
 */
public class Wilderness : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { timer(AREA_CHECK, 1, soft = true) { updateArea() } }
        events.on<AttackCheck> { check ->
            val attacker = check.attacker as? PlayerRef ?: return@on
            val target = check.target as? PlayerRef ?: return@on
            val a = level(attacker.coord)
            val b = level(target.coord)
            when {
                a <= 0 -> {
                    check.deny("You can't attack players outside the Wilderness.")
                }

                b <= 0 -> {
                    check.deny("That player is not in the Wilderness.")
                }

                kotlin.math.abs(attacker.combatLevel - target.combatLevel) > minOf(a, b) -> {
                    check.deny("The difference between your Combat level and the Combat level of your target is too great.")
                }

                else -> {
                    check.allow()
                }
            }
        }
    }

    private fun PlayerRef.updateArea() {
        val inWild = level(coord) > 0
        if (this[IN_WILD] == inWild) return
        this[IN_WILD] = inWild
        playerOp(ATTACK_OP, if (inWild) "Attack" else null, top = true)
    }

    public companion object {
        private val AREA_CHECK = TimerId("wilderness_area")
        private val IN_WILD = tp.plugin.AttrKey<Boolean>("in_wilderness")

        /** Player op slot for "Attack" (matches content/combat `Combat.ATTACK_OP`). */
        private const val ATTACK_OP = 2

        // OPEN-W1: main Wilderness bounds and the level formula are not stated by the Wiki (the client computes the level
        // overlay in a clientscript). Implemented: x 2944..3391, z 3520..3967, level = (z - 3520) / 8 + 1, the first
        // two rows (z 3520..3521) safe ("The first two steps ... of the Wilderness are 'safe' zones").
        private const val MIN_X = 2944
        private const val MAX_X = 3391
        private const val MIN_Z = 3520
        private const val MAX_Z = 3967
        private const val SAFE_ROWS = 2
        private const val ROWS_PER_LEVEL = 8

        /** Wilderness level at [c], 0 outside or in the safe rows. */
        public fun level(c: CoordGrid): Int {
            if (c.level != 0 || c.x !in MIN_X..MAX_X || c.z !in MIN_Z..MAX_Z) return 0
            if (c.z < MIN_Z + SAFE_ROWS) return 0
            return (c.z - MIN_Z) / ROWS_PER_LEVEL + 1
        }
    }
}
