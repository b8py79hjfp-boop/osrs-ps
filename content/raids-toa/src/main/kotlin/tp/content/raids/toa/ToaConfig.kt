package tp.content.raids.toa

/** Named flags for the ToA rules the sources leave open (docs/mechanics/raids/toa.md); system properties override. */
internal object ToaConfig {
    /** OPEN-TA5: Blazing Tombs I / II are Leagues and event content; not offered by default. */
    val blazingTombsEnabled: Boolean = System.getProperty("tp.toa.blazingTombsEnabled") == "true"

    /** OPEN-TA2: a player with no party who enters the raid door starts a solo raid (a party of one). */
    val soloWithoutParty: Boolean = System.getProperty("tp.toa.soloWithoutParty") != "false"

    /** OPEN-TA8: the mode of a finished raid (records, KC) follows the final raid level, not the starting one. */
    val modeUsesFinalLevel: Boolean = System.getProperty("tp.toa.modeUsesFinalLevel") != "false"

    /** OPEN-TA11: the party size used for scaling is the size at the start (fixed for the run). */
    val partySizeAtStart: Boolean = System.getProperty("tp.toa.partySizeAtStart") != "false"

    /** OPEN-TA23: when the leader leaves a forming party, the next slot leads. */
    val leaderHandover: Boolean = System.getProperty("tp.toa.leaderHandover") != "false"
}
