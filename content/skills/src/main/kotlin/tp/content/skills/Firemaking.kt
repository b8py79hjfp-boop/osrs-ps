package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ObjRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Firemaking (docs/mechanics/firemaking.md): a tinderbox on logs in the backpack or on the ground, or "Light" on
 * ground logs, starts an attempt that rolls every [ATTEMPT_TICKS] ticks until it lights (FM1). The attempt is a
 * player timer, so walking away or losing the logs ends it, as in OSRS. A lit log becomes a [FIRE_LOC] that burns
 * out after 60 to 119 seconds and leaves ashes (FM4); the player steps west, else east, south or north (FM5).
 */
public class Firemaking : ContentPlugin {
    override fun ContentScope.register() {
        for (log in BURNABLE_LOGS) {
            onUseOnItem(Objs.TINDERBOX, log.obj) { begin(log, null) }
            onUseOnObj(Objs.TINDERBOX, log.obj) { ground -> player.begin(log, ground) }
            onOpObj(log.obj, OP_LIGHT) { ground -> player.begin(log, ground) }
        }
    }

    /** What the running attempt is lighting: the log, the tile the fire goes on and the ground log (null: backpack). */
    private class Attempt(val log: BurnableLog, val at: CoordGrid, val ground: ObjRef?)

    private fun PlayerRef.begin(log: BurnableLog, ground: ObjRef?) {
        val at = ground?.coord ?: coord
        if (inv(Invs.INV).total(Objs.TINDERBOX) == 0L) return message(NO_TINDERBOX)
        if (level(Stats.FIREMAKING) < log.level) {
            return message("You need a Firemaking level of ${log.level} to burn ${objName(log.obj).lowercase()}.")
        }
        if (!canLightAt(at)) return message("You can't light a fire here.")
        this[ATTEMPT] = Attempt(log, at, ground)
        message("You attempt to light the logs.")
        anim(Seqs.HUMAN_CREATEFIRE)
        timer(TIMER, ATTEMPT_TICKS, soft = false) { attempt() }
    }

    /** A fire needs a free centrepiece layer on the tile: no other fire and no object (FM3). */
    private fun PlayerRef.canLightAt(at: CoordGrid): Boolean = locAt(at, FIRE_SHAPE) == null

    private fun PlayerRef.attempt() {
        val a = this[ATTEMPT]
        if (a == null || !stillValid(a)) return end()
        if (!statRandom(Stats.FIREMAKING, LOW, HIGH)) return anim(Seqs.HUMAN_CREATEFIRE)
        end()
        light(a)
    }

    private fun PlayerRef.stillValid(a: Attempt): Boolean {
        val backpack = inv(Invs.INV)
        val here = a.ground != null || coord == a.at
        val logs = a.ground != null || backpack.total(a.log.obj) > 0
        return here && logs && !isMoving && backpack.total(Objs.TINDERBOX) > 0
    }

    private fun PlayerRef.end() {
        stopTimer(TIMER)
        this[ATTEMPT] = null
    }

    private fun PlayerRef.light(a: Attempt) {
        if (!canLightAt(a.at)) return message("You can't light a fire here.")
        val consumed =
            transaction("firemaking") {
                (a.ground == null || take(a.ground, Invs.INV)) && remove(Invs.INV, a.log.obj, 1)
            }
        if (!consumed) return message(NO_ROOM)
        val burn = BURN_MIN_TICKS + random(BURN_SPREAD)
        addLoc(FIRE_LOC, a.at, FIRE_SHAPE, 0, burn)
        message("The fire catches and the logs begin to burn.")
        addXp(Stats.FIREMAKING, a.log.xp)
        if (coord == a.at) stepOff(a.at)
        queueHit(burn, strong = false) { transaction("fire ashes") { createOnGround(Objs.ASHES, 1, a.at, username) } }
    }

    /** West, else east, south, north: the first open direction (Wiki "Fire"). Stays put if all four are blocked. */
    private fun PlayerRef.stepOff(from: CoordGrid) {
        for ((dx, dz) in STEP_ORDER) {
            if (canStep(from, dx, dz)) {
                walkTo(CoordGrid(from.x + dx, from.z + dz, from.level))
                return
            }
        }
    }

    private companion object {
        val ATTEMPT = AttrKey<Attempt>("firemaking.attempt")
        val TIMER = TimerId("firemaking")

        /** Ground obj op 4 of every log ("Light", cache `groundOps`). */
        const val OP_LIGHT = 4

        /** The light roll comes 3 ticks after the click and repeats at that pace: OPEN-FM1 (4 ticks per lit log with a click each tick). */
        const val ATTEMPT_TICKS = 3

        /** Wiki "Firemaking" Success chance chart: low 64, high 512 (out of 256) for every log but coloured ones. */
        const val LOW = 64
        const val HIGH = 512

        /** Fires last "60-119 secs randomly" (Mod Ash via Wiki "Fire"): 100 ticks plus 0..98. OPEN-FM2: second to tick rounding. */
        const val BURN_MIN_TICKS = 100
        const val BURN_SPREAD = 99

        /** Loc shape 10, centrepiece (straight): the layer a fire occupies (RS Mod `LocShape.CENTREPIECE_STRAIGHT`). */
        const val FIRE_SHAPE = 10

        /** Wiki "Fire": the step after lighting goes west, else east, south, north. */
        val STEP_ORDER = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)

        const val NO_TINDERBOX = "You need a tinderbox to light a fire."
        const val NO_ROOM = "You don't have enough room to light that."
    }
}
