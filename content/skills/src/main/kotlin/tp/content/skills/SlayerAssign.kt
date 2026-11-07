package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Varbits
import tp.symbols.Varps

/** What a master rolled: a regular task of the master's list or, with "Like a boss", one boss. */
internal sealed interface SlayerPick {
    class Regular(val task: SlayerTask, val row: SlayerTaskRow) : SlayerPick

    class Boss(val boss: SlayerBoss) : SlayerPick
}

/** The loaded cache tables and the requirement checker; built at world start (the cache is the same in every world). */
internal class SlayerRuntime(val cache: SlayerCache) {
    val gate: SlayerGate = SlayerGate(cache)

    /** Cache task id to the monster groups of `slayer-monsters.tsv` that count for it (the Wiki task name is matched to the cache's). */
    val groupsOf: Map<Int, Set<String>> =
        SlayerData.TASKS.values
            .flatten()
            .mapNotNull { t -> cache.taskNamed(t.name)?.let { it.id to t.group } }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.toSet() }
}

/**
 * Which tasks a master can give a player and which one it rolls (docs/mechanics/slayer.md SL3, SL4, SL10, SL11). Wiki "Slayer task":
 * "the weights of blocked tasks, tasks that are toggled off, and tasks that the player does not have the Slayer level or quest
 * requirements for are not added to the sum".
 */
internal class SlayerAssign(private val runtime: () -> SlayerRuntime) {
    private class Entry(val weight: Int, val make: () -> SlayerPick)

    /** The tasks (and the boss entry) [master] can give [p] now, with their weights. */
    private fun pool(p: PlayerRef, master: SlayerMaster): List<Entry> {
        val rt = runtime()
        val out = ArrayList<Entry>()
        for (task in SlayerData.TASKS[master.key].orEmpty()) {
            if (task.group == BOSS_GROUP) continue
            val row = rt.cache.taskNamed(task.name) ?: continue
            if (!eligible(p, master, task, row)) continue
            val pick = SlayerPick.Regular(task, row)
            out += Entry(weightOf(p, task, row)) { pick }
        }
        val weight = rt.cache.bossWeight[master.id] ?: 0
        val unlock = rt.cache.unlockOrNull(LIKE_A_BOSS)
        if (weight > 0 && unlock != null && SlayerState.unlocked(p, unlock.bit)) {
            val bosses = bosses(p, master)
            if (bosses.isNotEmpty()) out += Entry(weight) { SlayerPick.Boss(bosses[p.random(bosses.size)]) }
        }
        return out
    }

    /** Whether [p] may be assigned [t] by [master]: levels, quests and unlocks, and not blocked or toggled off. */
    fun eligible(p: PlayerRef, master: SlayerMaster, t: SlayerTask, row: SlayerTaskRow): Boolean {
        val rt = runtime()
        if (p.combatLevel < t.combat || p.baseLevel(Stats.SLAYER) < t.slayer || t.max <= 0) return false
        if (!t.skills.all { (stat, level) -> p.baseLevel(stat) >= level }) return false
        if (SlayerState.isBlocked(p, master, row.id)) return false
        if (stopped(p, row)) return false
        return rt.gate.met(p, SlayerData.requirementOf(master.key, t))
    }

    /** Stop the Wyvern: with the unlock bought and switched on the task is not given (Wiki "Slayer Rewards", Unlock). */
    private fun stopped(p: PlayerRef, row: SlayerTaskRow): Boolean {
        if (row.blockUnlock < 0) return false
        val unlock = runtime().cache.unlocksByRow[row.blockUnlock] ?: return false
        return SlayerState.unlocked(p, unlock.bit) && p.varbit(Varbits.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK) == 0
    }

    private fun weightOf(p: PlayerRef, task: SlayerTask, row: SlayerTaskRow): Int {
        val heavy = row.heavy ?: return task.weight
        val unlock = runtime().cache.unlocksByRow[heavy.unlockRow] ?: return task.weight
        return if (SlayerState.unlocked(p, unlock.bit)) heavy.min else task.weight
    }

    /** The bosses [master] can give [p] (Wiki "Boss Slayer": Krystilia only the Wilderness ones, Konar alone the Alchemical Hydra). */
    fun bosses(p: PlayerRef, master: SlayerMaster): List<SlayerBoss> =
        SlayerData.BOSSES.filter { b ->
            (b.only == null || b.only == master.key) && (master.key != WILDERNESS_MASTER || b.wild) && runtime().gate.met(p, b.expr)
        }

    /** One weighted pick among what [master] can give [p], or null if nothing. */
    fun roll(p: PlayerRef, master: SlayerMaster): SlayerPick? {
        val pool = pool(p, master)
        val total = pool.sumOf { it.weight }
        if (total <= 0) return null
        var pick = p.random(total)
        for (e in pool) {
            pick -= e.weight
            if (pick < 0) return e.make()
        }
        return pool.last().make()
    }

    /** The master's tasks that [p] could be given, whatever the pool rolls (Turael's change of task asks it). */
    fun gives(p: PlayerRef, master: SlayerMaster, taskId: Int): Boolean =
        SlayerData.TASKS[master.key].orEmpty().any { t ->
            val row = runtime().cache.taskNamed(t.name)
            row != null && row.id == taskId && eligible(p, master, t, row)
        }

    /**
     * The amount of [task]: uniform in its range, or in the longer range once its "Extend" reward is bought (Wiki "Slayer Rewards",
     * Extend: the amounts of the master tables' extended columns; the cache's own amounts when the Wiki table has none).
     */
    fun amount(p: PlayerRef, task: SlayerTask, row: SlayerTaskRow): Int {
        var min = task.min
        var max = task.max
        val cache = runtime().cache
        val ext = row.extension ?: row.additive
        if (ext != null && cache.unlocksByRow[ext.unlockRow]?.let { SlayerState.unlocked(p, it.bit) } == true) {
            if (task.emin > 0 && task.emax >= task.emin) {
                min = task.emin
                max = task.emax
            } else if (row.extension != null) {
                min = ext.min
                max = ext.max
            } else {
                min += ext.min
                max += ext.max
            }
        }
        return min + p.random(max - min + 1)
    }

    /** The lower-case name of the task the player has, for messages ("dust devils"); "monsters" if it is unknown. */
    fun taskName(p: PlayerRef): String {
        if (SlayerState.taskId(p) == SlayerCache.BOSS_TASK_ID) {
            return SlayerData.BOSSES.firstOrNull { it.id == SlayerState.bossId(p) }?.name ?: "bosses"
        }
        return runtime().cache.tasksById[SlayerState.taskId(p)]?.name ?: "monsters"
    }

    /** The cache master id of the master called [key]. */
    fun idOf(key: String): Int = SlayerData.MASTERS.first { it.key == key }.id

    /** The master object for cache master id [id]. */
    fun masterOf(id: Int): SlayerMaster? = SlayerData.MASTERS.firstOrNull { it.id == id }

    /** Sets a regular task of [amount] kills from [master], with the location Konar gives it in if the cache lists any. */
    fun give(p: PlayerRef, master: SlayerMaster, pick: SlayerPick.Regular, amount: Int): String {
        SlayerState.set(p, pick.row.id, amount, 0, master.id)
        val areas = runtime().cache.areasOf(master.id, pick.row)
        if (areas.isEmpty()) return ""
        val area = runtime().cache.areaOf[areas[p.random(areas.size)]] ?: return ""
        p.setVarp(Varps.SLAYER_AREA, area.id)
        return " " + area.text
    }

    companion object {
        /** TSV group of the Wiki's boss rows: replaced by the boss entry of the cache (weights 8, Duradel 12). */
        const val BOSS_GROUP = "bosse"

        /** The master whose tasks count only in the Wilderness and have their own streak (Wiki "Krystilia"). */
        const val WILDERNESS_MASTER = "Krystilia"

        /** Name of the unlock that lets masters give bosses (db table 117, Wiki "Slayer Rewards"). */
        const val LIKE_A_BOSS = "Like a Boss"
    }
}
