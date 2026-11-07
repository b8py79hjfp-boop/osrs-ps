package tp.content.sailing

/**
 * Base Sailing XP per port task, the server-only column the cache strips from `Port_Task` (db 197 `XP_REWARD`). Read from
 * `courier-tasks.tsv` and `bounty-tasks.tsv` (tools/wiki-import/sailing.py, OSRS Wiki `CourierTaskLine` / `BountyTaskLine`
 * keyed by the cache's task id). There is no closed XP formula (OPEN-T1, docs/mechanics/sailing-ports.md P5), so the
 * imported value is the rule. The Wiki lists 441 of the cache's 443 courier tasks; [of] is null for the two it omits.
 */
internal class PortTaskXp private constructor(private val xp: Map<Int, Int>) {
    /** Base XP of task [taskId] before the Horizon's lure ale multiplier, or null if the Wiki does not list it. */
    fun of(taskId: Int): Int? = xp[taskId]

    val size: Int get() = xp.size

    companion object {
        /** Column of the task id in both files. */
        private const val TASK = 0

        /** Column of the XP in both files. */
        private const val XP = 2

        fun load(): PortTaskXp {
            val xp = HashMap<Int, Int>()
            for (file in listOf("courier-tasks.tsv", "bounty-tasks.tsv")) {
                val text = PortTaskXp::class.java.getResource(file)?.readText() ?: error("$file missing")
                for (line in text.lineSequence().drop(1)) {
                    val cells = line.split('\t')
                    val task = cells.getOrNull(TASK)?.toIntOrNull() ?: continue
                    xp[task] = cells.getOrNull(XP)?.toIntOrNull() ?: continue
                }
            }
            return PortTaskXp(xp)
        }
    }
}
