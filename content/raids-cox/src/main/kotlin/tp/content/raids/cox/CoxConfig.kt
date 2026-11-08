package tp.content.raids.cox

/** Named flags for the CoX rules the sources leave open (docs/mechanics/raids/cox.md); system properties override. */
internal object CoxConfig {
    /** OPEN-CX7: the regular pool draws small or medium layouts with equal weight (small = 5 rooms, 3 combat). */
    val poolSmallPatterns: Boolean = System.getProperty("tp.cox.poolSmallPatterns") != "false"

    /** OPEN-CX17: Main's "start of the previous floor" instead of Strat's end of the previous floor. */
    val respawnAtPreviousFloorStart: Boolean = System.getProperty("tp.cox.respawnAtPreviousFloorStart") == "true"

    /** OPEN-CX5: the leader hand-over also applies after the raid has started. */
    val leaderHandoverAfterStart: Boolean = System.getProperty("tp.cox.leaderHandoverAfterStart") != "false"
}
