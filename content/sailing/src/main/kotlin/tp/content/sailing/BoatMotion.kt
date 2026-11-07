package tp.content.sailing

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.WorldEntityRef
import tp.plugin.WorldRef
import tp.util.CoordGrid
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Boat movement (docs/mechanics/sailing-movement.md SM1-SM8): each tick, in the world-entity phase, every attached boat
 * turns toward its heading, eases its speed toward what its sails ask for, and moves its pivot along its facing if
 * the new footprint is sea with no rock. The navigator at the helm sets the heading (SET_HEADING, 16 steps) and the
 * sails. Speeds are fine units per tick (128 per tile). The plugin instance belongs to one world, so its state is
 * world-scoped.
 */
public class BoatMotion : ContentPlugin {
    /** Sail state (SM2; cache sail states down / half / full / reverse). */
    internal enum class Sails { DOWN, HALF, FULL, REVERSE }

    /** A boat's motion stats: speeds and acceleration in fine units per tick, footprint in fine units, turn per tick. */
    internal class Stats(
        val baseSpeed: Int,
        val speedCap: Int,
        val acceleration: Int,
        val widthFine: Int,
        val lengthFine: Int,
        val turnPerTick: Int,
    )

    /** Live motion of one boat. [stats] is replaced when the boat is customised ([Boats.rebuild], S4). */
    internal class Motion(val boat: WorldEntityRef, var stats: Stats) {
        var heading: Int = boat.angle
        var angle: Int = boat.angle
        var speed: Int = 0
        var sails: Sails = Sails.DOWN
        var blocked: Boolean = false
        var navigator: PlayerRef? = null
    }

    /** Request published by S1 ([Boats]) on the world's event bus: the answering [BoatMotion] of this world fills [motion]. */
    internal class Lookup : tp.plugin.Event {
        var motion: BoatMotion? = null
    }

    private val motions = HashMap<Int, Motion>()

    override fun ContentScope.register() {
        onWorldEntityTick { tickBoats(this) }
        onHeading { heading -> heading(this, heading) }
        onLogout { stopNavigating(this) }
        events.on<Lookup> { it.motion = this@BoatMotion }
    }

    /** Puts [boat] under motion with [stats] (S1 calls this when it spawns a boat). */
    internal fun attach(boat: WorldEntityRef, stats: Stats): Motion = Motion(boat, stats).also { motions[boat.index] = it }

    /** Stops moving [boat] (despawn). */
    internal fun detach(boat: WorldEntityRef) {
        motions.remove(boat.index)?.navigator?.let(::stopNavigating)
    }

    internal fun motionOf(boat: WorldEntityRef): Motion? = motions[boat.index]

    /** SM1: [player] takes the helm of [boat]: clicks on the root world now set headings. */
    internal fun navigate(player: PlayerRef, boat: WorldEntityRef): Boolean {
        val motion = motions[boat.index] ?: return false
        if (motion.navigator != null && motion.navigator != player) return false
        motion.navigator = player
        player[NAVIGATING] = boat.index
        player.setInteractionMode(ROOT_WORLD, TILE_HEADING, ENTITY_EXAMINE)
        return true
    }

    /** SM1: [player] lets go of the helm; the boat keeps its heading and sails. */
    internal fun stopNavigating(player: PlayerRef) {
        val index = player[NAVIGATING] ?: return
        player[NAVIGATING] = null
        motions[index]?.takeIf { it.navigator == player }?.navigator = null
        player.setInteractionMode(ROOT_WORLD, TILE_WALK, ENTITY_ENABLED)
    }

    /** SM2: the sail control of [boat]. */
    internal fun setSails(boat: WorldEntityRef, sails: Sails) {
        motions[boat.index]?.sails = sails
    }

    private fun heading(player: PlayerRef, heading: Int) {
        val index = player[NAVIGATING] ?: return
        val motion = motions[index] ?: return
        motion.heading = heading * HEADING_UNITS
        // SM2: setting a heading starts a stopped boat (setting `sailing_start_boat_when_setting_heading`, OPEN-M5 default).
        if (motion.sails == Sails.DOWN) motion.sails = Sails.FULL
    }

    private fun tickBoats(world: WorldRef) {
        for (motion in motions.values) step(world, motion)
    }

    /** One tick of [motion] (SM3, SM4, SM5): turn, change speed, move if the new footprint is sailable. */
    private fun step(world: WorldRef, motion: Motion) {
        motion.angle = turned(motion.angle, motion.heading, motion.stats.turnPerTick)
        motion.speed = eased(motion.speed, target(motion), motion.stats.acceleration)
        val boat = motion.boat
        if (motion.speed == 0) {
            if (motion.angle != boat.angle) boat.move(boat.fineX, boat.fineZ, motion.angle)
            return
        }
        val radians = motion.angle * RADIANS_PER_UNIT
        val nx = boat.fineX - (sin(radians) * motion.speed).roundToInt()
        val nz = boat.fineZ - (cos(radians) * motion.speed).roundToInt()
        // SM5 / OPEN-M6: only points that are newly blocked stop the boat, so a boat docked against the pier legs can leave.
        val blockedNow = blockedPoints(world, boat.fineX, boat.fineZ, motion)
        if (blockedPoints(world, nx, nz, motion) and blockedNow.inv() != 0) {
            motion.speed = 0
            if (!motion.blocked) motion.navigator?.message("Your boat can't go any further that way.")
            motion.blocked = true
            boat.move(boat.fineX, boat.fineZ, motion.angle)
            return
        }
        motion.blocked = false
        boat.move(nx, nz, motion.angle)
    }

    private fun target(motion: Motion): Int =
        when (motion.sails) {
            Sails.DOWN -> 0
            Sails.HALF -> motion.stats.baseSpeed / 2
            Sails.FULL -> minOf(motion.stats.baseSpeed, motion.stats.speedCap)
            Sails.REVERSE -> -motion.stats.baseSpeed / REVERSE_DIVISOR
        }

    /** SM5: bit mask of the corner, edge and centre points of the footprint around ([fineX], [fineZ]) that are not sailable. */
    private fun blockedPoints(world: WorldRef, fineX: Int, fineZ: Int, motion: Motion): Int {
        val radians = motion.angle * RADIANS_PER_UNIT
        val fx = -sin(radians)
        val fz = -cos(radians)
        val halfLength = motion.stats.lengthFine / 2.0
        val halfWidth = motion.stats.widthFine / 2.0
        var mask = 0
        var bit = 1
        for (along in intArrayOf(-1, 0, 1)) {
            for (across in intArrayOf(-1, 0, 1)) {
                val px = fineX + fx * halfLength * along - fz * halfWidth * across
                val pz = fineZ + fz * halfLength * along + fx * halfWidth * across
                val tile = CoordGrid(px.toInt() shr FINE_SHIFT, pz.toInt() shr FINE_SHIFT, 0)
                if (!world.isSailable(tile)) mask = mask or bit
                bit = bit shl 1
            }
        }
        return mask
    }

    private companion object {
        val NAVIGATING = tp.plugin.AttrKey<Int>("sailing.navigating")

        /** SET_HEADING step: 16 headings of the 2048-unit circle. */
        const val HEADING_UNITS = 128
        const val FULL_TURN = 2048
        const val HALF_TURN = 1024
        const val RADIANS_PER_UNIT = 2 * Math.PI / FULL_TURN
        const val FINE_SHIFT = 7

        /** OPEN-M2 / M4: reverse is assumed to run at a quarter of the base speed. */
        const val REVERSE_DIVISOR = 4

        /**
         * SET_INTERACTION_MODE values (RSProt `SetInteractionMode`). World -2 is every world: heading clicks must work on
         * the root world and on the decks alike (verified in the client).
         */
        const val ROOT_WORLD = -2
        const val TILE_WALK = 1
        const val TILE_HEADING = 2
        const val ENTITY_ENABLED = 1
        const val ENTITY_EXAMINE = 2

        /** Turns [from] toward [to] by at most [step] units along the shorter way. */
        fun turned(from: Int, to: Int, step: Int): Int {
            val diff = Math.floorMod(to - from + HALF_TURN, FULL_TURN) - HALF_TURN
            if (kotlin.math.abs(diff) <= step) return to and (FULL_TURN - 1)
            return Math.floorMod(from + if (diff > 0) step else -step, FULL_TURN)
        }

        /** Moves [speed] toward [target] by at most [acceleration]. */
        fun eased(speed: Int, target: Int, acceleration: Int): Int =
            when {
                speed < target -> minOf(target, speed + acceleration)
                speed > target -> maxOf(target, speed - acceleration)
                else -> speed
            }
    }
}
