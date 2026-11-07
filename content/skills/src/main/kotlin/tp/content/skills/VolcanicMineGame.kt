package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.util.CoordGrid
import tp.content.skills.VolcanicMineData as D

/**
 * A boulder in one of the lava channels: [channel] 0 central, 1 western, 2 eastern, its [stage] (0 attached .. 4 small) and
 * hitpoints left.
 */
internal class VmBoulder(val channel: Int) {
    var stage: Int = 0
    var hp: Int = 0

    fun at(): CoordGrid = CoordGrid(D.CHANNEL_X[channel], if (stage == D.STAGES - 1) D.SMALL_Z[channel] else D.STAGE_Z[stage], 1)
}

/** Hardened lava: the tile, the angle of the lava loc, the tick its colour next changes and its stage (0 grey, 1 orange, 2 white). */
internal class VmHard(val tile: CoordGrid, val angle: Int, var due: Long, var stage: Int)

/**
 * The one running Volcanic Mine of a world (docs/mechanics/volcanic-mine.md): the preparation countdown, the ten-minute game, the
 * chambers and the boulders. Owned by the [VolcanicMine] plugin instance, never shared between worlds.
 */
internal class VmGame(val startsAt: Long) {
    val endsAt: Long = startsAt + D.GAME_TICKS
    var running: Boolean = false
    val players = ArrayList<PlayerRef>()
    val boulders = ArrayList<VmBoulder>()
    val hardened = ArrayList<VmHard>()

    /**
     * Chamber state: pressure in percent, blocked, the tick a taken rock is back, and the pressure last revealed by a vent (127
     * = unknown).
     */
    val pressure = IntArray(CHAMBERS)
    val blocked = BooleanArray(CHAMBERS)
    val rockBackAt = LongArray(CHAMBERS)
    val revealed = IntArray(CHAMBERS) { UNKNOWN }
    var stability: Int = FULL
    var shiftAt: Long = 0
    var closed: Boolean = false

    /** Players present when the game started (the boulder hitpoints and count scale with it). */
    var starters: Int = 0

    fun closing(now: Long): Boolean = now >= endsAt - D.CLOSING_TICKS

    fun boulderAt(at: CoordGrid, id: Int): VmBoulder? = boulders.firstOrNull { it.at() == at && D.STAGE_LOCS[it.stage] == id }

    /** Chamber bits for the HUD (varbit 5936): bit i set when chamber i is blocked. */
    fun blockedBits(): Int = (if (blocked[0]) 1 else 0) or (if (blocked[1]) 2 else 0) or (if (blocked[2]) 4 else 0)

    companion object {
        const val CHAMBERS = 3
        const val FULL = 100

        /** Vent status 127 is "unknown" on the HUD (client script 2020). */
        const val UNKNOWN = 127
        val GAME = AttrKey<VmGame>("volcanicMine.game")
        val POINTS = AttrKey<Int>("volcanicMine.points")
        val CAPPED = AttrKey<Int>("volcanicMine.capped")
        val WATER = AttrKey<Int>("volcanicMine.water")
        val LAST_SWING = AttrKey<Long>("volcanicMine.lastSwing")
    }
}
