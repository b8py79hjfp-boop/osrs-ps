package tp.content.raids.toa

/**
 * Named flags for the lobby party screens the sources leave open (docs/mechanics/raids/toa.md TA2, TA3, TA6;
 * toa/client-contract.md section 10); system properties override. Each flag names its OPEN item.
 */
internal object ToaUiConfig {
    /** OPEN-TAUI2: the leader's "Set Completions" figure is a minimum an applicant (or accepted member) must meet. */
    val completionsGateApply: Boolean = System.getProperty("tp.toa.completionsGateApply") != "false"

    /** OPEN-TAUI3: a rejected applicant cannot apply again until the leader presses Unblock (client-contract 2.4 sub 2). */
    val rejectBlocksReapply: Boolean = System.getProperty("tp.toa.rejectBlocksReapply") != "false"

    /** OPEN-TAUI3: a kicked member is blocked from applying again until Unblock. */
    val kickBlocksReapply: Boolean = System.getProperty("tp.toa.kickBlocksReapply") != "false"

    /** OPEN-TAUI4: edits re-open the details screen instead of re-running script 6729 alone (client-contract 2.4 step 4 is [I]). */
    val reopenOnEdit: Boolean = System.getProperty("tp.toa.reopenOnEdit") == "true"

    /** OPEN-TC12: the number dialog behind "Set Completions" has no bounds in the cache; 0 clears the requirement. */
    val maxCompletions: Int = System.getProperty("tp.toa.maxCompletions")?.toIntOrNull() ?: DEFAULT_MAX_COMPLETIONS

    /** OPEN-TC12 placeholder: no source gives a cap; the figure only has to fit the int argument of script 6729. */
    private const val DEFAULT_MAX_COMPLETIONS = 9999
}
