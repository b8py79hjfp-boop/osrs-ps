package tp.content.raids.tob

import tp.plugin.PlayerRef
import tp.symbols.Varps

/**
 * Named switches for ToB's OPEN rules (BRIEF section 0.4), read once from system properties `tp.tob.<name>`.
 */
internal object TobConfig {
    /** TB19 / OPEN-TB8: the escape crystal's shop price (cache `cost` and the 3 Nov 2026 change: 10,000). */
    val escapeCrystalPrice: Int = System.getProperty("tp.tob.escapeCrystalPrice")?.toIntOrNull() ?: DEFAULT_CRYSTAL_PRICE

    /** TB15: the fee to reclaim items after a Normal / Hard wipe. */
    val reclaimFee: Int = System.getProperty("tp.tob.reclaimFee")?.toIntOrNull() ?: DEFAULT_RECLAIM_FEE

    /** TB16 / OPEN-TB5: an Entry wipe retries the current room instead of ending the raid. */
    val entryWipeRestartsRoom: Boolean = System.getProperty("tp.tob.entry.wipeRestartsRoom") != "false"

    /** TB30 / OPEN-TB14: the Hard-only tertiaries need the target time. */
    val hardTertiariesNeedTargetTime: Boolean = System.getProperty("tp.tob.hardTertiariesNeedTargetTime") != "false"

    /** TB31 / OPEN-TB15: unclaimed rewards are lost on logout (off: they wait at the lobby chest). */
    val unclaimedLootExpiresOnLogout: Boolean = System.getProperty("tp.tob.unclaimedLootExpiresOnLogout") == "true"

    /** LR21: the individual points at which Lil' Zik reaches its best rate (10; the curve below it is OPEN-TB13, linear). */
    val petFullRatePoints: Int = System.getProperty("tp.tob.petFullRatePoints")?.toIntOrNull() ?: DEFAULT_PET_POINTS

    private const val DEFAULT_PET_POINTS = 10

    /** OPEN-TB-UI3 (tob.md OPEN-TB1): a kicked member cannot re-apply until the leader presses Unblock [I: the button exists]. */
    val boardKickBlocksReapply: Boolean = System.getProperty("tp.tob.board.kickBlocksReapply") != "false"

    /** OPEN-TB-UI3: as [boardKickBlocksReapply], for a rejected applicant. */
    val boardRejectBlocksReapply: Boolean = System.getProperty("tp.tob.board.rejectBlocksReapply") != "false"

    /** OPEN-TB-UI5: whether "Move up / down" may swap the leader's row 0 (which hands over the lead); off = rows 1.. only. */
    val boardReorderMovesLeader: Boolean = System.getProperty("tp.tob.board.reorderMovesLeader") == "true"

    /** OPEN-TB-UI2: the largest "Preferred Level" a leader may enter (bound unknown, client-contract 3; 126 = max combat level). */
    val boardMaxPreferredLevel: Int = System.getProperty("tp.tob.board.maxPreferredLevel")?.toIntOrNull() ?: DEFAULT_MAX_PREFERRED_LEVEL

    private const val DEFAULT_MAX_PREFERRED_LEVEL = 126

    /**
     * LR10 / OPEN-LR5: how a midway chest credits points. "tiers" (default) puts each member in the below / average /
     * above tier of the wiki's per-chest ranges by their performance against the team; "flat" gives everyone the
     * average tier's lowest value.
     */
    val supplyPointsModel: String = System.getProperty("tp.tob.supplyPointsModel") ?: MODEL_TIERS

    const val MODEL_TIERS = "tiers"
    const val MODEL_FLAT = "flat"

    private const val DEFAULT_CRYSTAL_PRICE = 10_000
    private const val DEFAULT_RECLAIM_FEE = 100_000

    /** TB3: the raid itself has no requirement on this server beyond access (Priest in Peril is not checked). */
    @Suppress("FunctionOnlyReturningConstant", "UnusedParameter") // the raid definition's hook: a requirement may be added here
    fun requirement(player: PlayerRef): String? = null

    /** TB4: Hard needs a Normal completion from every participant (OPEN-TB2: a Hard completion also counts). */
    fun hardRequirement(player: PlayerRef): String? =
        if (player.varp(Varps.TOTAL_COMPLETED_THEATREOFBLOOD) + player.varp(Varps.TOTAL_COMPLETED_THEATREOFBLOOD_HARD) > 0) {
            null
        } else {
            "You need to complete the Theatre of Blood in Normal Mode before attempting Hard Mode."
        }
}
