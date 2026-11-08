package tp.content.combat

import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Ocellus P2, the Blade (16675; OC2). Capture: a fixed script from its start T, the same to the tick in four fights:
 * rounds of 6 volleys at T+11, T+53 and T+95, a volley every 5 ticks (R+0 .. R+25); "This... is enough..." at T+123 and
 * P3 at T+131. Damage on the Blade comes off the shared P2-P4 pool. RuneLite (LU FinalBoss.java:1059-1190): each inner
 * wall has six portals 2 tiles apart; the firing portals light up (loc 62685) 4 ticks ahead; one portal per wall stays
 * dark (orange) and the orange lanes cross in a 2x2 safe area; the orange portals follow one of three fixed sequences
 * (capture: the first random, then in turn). A raider in a fired lane takes 19..25.
 */
internal object TfaOcellusP2 {
    fun start(npc: NpcRef, s: OcellusState) {
        TfaOcellus.become(npc, s, TfaOcellus.P2, Npcs.TFA_OCELLUS_P2, freshPool = true)
        npc.teleport(CoordGrid(s.centre.x - 1, s.centre.z - 1, s.centre.level))
        val t0 = npc.tick
        val sequence = npc.random(SEQUENCES.size)
        TfaOcellus.loop(npc, s) {
            val t = (npc.tick - t0).toInt()
            for ((round, r) in ROUNDS.withIndex()) {
                val k = t + PORTAL_TELEGRAPH - r
                if (k >= 0 && k < VOLLEYS * VOLLEY_EVERY && k % VOLLEY_EVERY == 0) {
                    val seq = (sequence + round) % SEQUENCES.size
                    portals(npc, s, SEQUENCES[seq][k / VOLLEY_EVERY])
                }
            }
            if (t == ENOUGH_AT) npc.say("This... is enough...")
            if (t == P3_AT) TfaOcellusP3.start(npc, s)
        }
    }

    private fun portals(npc: NpcRef, s: OcellusState, orange: Pair<Int, Int>) {
        val players = TfaKit.arena(npc)
        if (players.isEmpty()) return
        val (orangeRow, orangeCol) = orange
        val qx = Integer.signum(players[0].coord.x - s.centre.x).let { if (it == 0) 1 else it }
        val qz = Integer.signum(players[0].coord.z - s.centre.z).let { if (it == 0) 1 else it }
        for (k in 0 until PORTALS_PER_WALL) {
            if (k != orangeRow) {
                npc.addLoc(
                    Locs.TFA_OCELLUS_QUADRANT_WALL_ACTIVE,
                    CoordGrid(s.centre.x, s.centre.z + qz * (1 + k * 2), s.centre.level),
                    TfaOcellus.WALL_SHAPE,
                    0,
                    PORTAL_TELEGRAPH,
                )
            }
            if (k != orangeCol) {
                npc.addLoc(
                    Locs.TFA_OCELLUS_QUADRANT_WALL_ACTIVE,
                    CoordGrid(s.centre.x + qx * (1 + k * 2), s.centre.z, s.centre.level),
                    TfaOcellus.WALL_SHAPE,
                    0,
                    PORTAL_TELEGRAPH,
                )
            }
        }
        npc.queueHit(PORTAL_TELEGRAPH, strong = false) {
            if (!npc.isInWorld || s.phase != TfaOcellus.P2) return@queueHit
            for (p in TfaKit.arena(npc)) {
                val row = (kotlin.math.abs(p.coord.z - s.centre.z) - 1) / 2
                val col = (kotlin.math.abs(p.coord.x - s.centre.x) - 1) / 2
                if (row != orangeRow ||
                    col != orangeCol
                ) {
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, LANE_MIN, LANE_MAX), HitStyle.TYPELESS)
                }
            }
        }
    }

    /** RuneLite (LU FinalBoss.java:1182-1190): the three orange sequences, {row, column} per volley. */
    private val SEQUENCES =
        listOf(
            listOf(1 to 1, 3 to 1, 1 to 3, 1 to 1, 2 to 2, 4 to 1),
            listOf(2 to 2, 2 to 3, 1 to 4, 2 to 2, 2 to 4, 3 to 3),
            listOf(1 to 0, 1 to 3, 1 to 5, 3 to 3, 2 to 2, 3 to 0),
        )
    private val ROUNDS = intArrayOf(11, 53, 95)
    private const val VOLLEYS = 6
    private const val VOLLEY_EVERY = 5
    private const val PORTAL_TELEGRAPH = 4
    private const val PORTALS_PER_WALL = 6
    private const val LANE_MIN = 19
    private const val LANE_MAX = 25
    private const val ENOUGH_AT = 123
    private const val P3_AT = 131
}
