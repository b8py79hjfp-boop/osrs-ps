package tp.content.raids.cox

/**
 * Named flags for the lobby, storage and reward-chest rules the sources leave open (docs/mechanics/raids/cox.md,
 * cox/lobby-rewards.md, cox/client-contract.md); system properties override. Each flag names its OPEN item.
 */
internal object CoxUiConfig {
    /** CR12 / OPEN-CR10: unclaimed chest rewards go to the private storage when leaving (the ground when it is full). */
    val unclaimedLootToStorage: Boolean = System.getProperty("tp.cox.unclaimedLootToStorage") != "false"

    /** CX39 / OPEN-CX21: an Ultimate Ironman may deposit into the shared storage but not take anything out (U-KD). */
    val uimSharedStorageDepositOnly: Boolean = System.getProperty("tp.cox.uimSharedStorageDepositOnly") != "false"

    /** OPEN-CXUI2: CR12's 27 Jul 2022 line says Ultimate Ironmen "cannot use storage"; false keeps them out of the private one. */
    val uimPrivateStorage: Boolean = System.getProperty("tp.cox.uimPrivateStorage") == "true"

    /** CX38 / OPEN-CX20: mallignum root planks per tier built (cumulative reading: a large unit from nothing costs 6). */
    val storagePlanksPerTier: Int = System.getProperty("tp.cox.storagePlanksPerTier")?.toIntOrNull() ?: DEFAULT_PLANKS_PER_TIER

    /** CX49 / OPEN-CX27: normal-mode completions the leader needs before the Challenge Mode box can be ticked (0 = none). */
    val cmMinCompletions: Int = System.getProperty("tp.cox.cmMinCompletions")?.toIntOrNull() ?: DEFAULT_CM_MIN_COMPLETIONS

    /** CX2 / OPEN-CX1: adverts a leader may place per day; the varbit 5414 holds at most 15, which is the default. */
    val dailyAdverts: Int = System.getProperty("tp.cox.dailyAdverts")?.toIntOrNull() ?: ADVERTS_VARBIT_MAX

    /** CX49 (9 Dec 2021): the lowered Challenge Mode minimum killcount is 5. */
    private const val DEFAULT_CM_MIN_COMPLETIONS = 5

    /** CX38 (U 8 Feb 2018): "2, 4 and 6 respectively" planks, read cumulatively (2 per tier). */
    private const val DEFAULT_PLANKS_PER_TIER = 2

    /** Client-contract 1.2: `RAIDS_DAILY_ADVERTS` 5414 is 4 bits wide. */
    const val ADVERTS_VARBIT_MAX = 15
}
