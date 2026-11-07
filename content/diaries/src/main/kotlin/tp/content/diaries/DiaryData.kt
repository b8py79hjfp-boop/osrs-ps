package tp.content.diaries

import tp.plugin.Stats
import tp.symbols.Varbits

/** Tiers of every diary, in cache order (client script 4073 returns the task counts in this order). */
internal val TIER_NAMES: List<String> = listOf("easy", "medium", "hard", "elite")

/** Tiers per diary (Wiki "Achievement Diary": easy, medium, hard, elite). */
internal const val TIER_COUNT = 4

/**
 * A diary task: area (cache enum 595 index) * 1000 + tier * 100 + the Wiki task number (1-based, at most 19). Stable because
 * the tier counts are fixed by the cache (client script 4073), so it can be used as a key by hooks and saves.
 */
@JvmInline
internal value class TaskId(val raw: Int) {
    val area: Int get() = raw / AREA_STRIDE
    val tier: Int get() = raw % AREA_STRIDE / TIER_STRIDE
    val index: Int get() = raw % TIER_STRIDE

    companion object {
        private const val AREA_STRIDE = 1000
        private const val TIER_STRIDE = 100

        fun of(area: Int, tier: Int, index: Int): TaskId = TaskId(area * AREA_STRIDE + tier * TIER_STRIDE + index)
    }
}

/** A skill level requirement; [stat] is a client stat id, [COMBAT] or [QUEST_POINTS]. */
internal class SkillReq(val stat: Int, val level: Int, val boostable: Boolean) {
    companion object {
        /** Pseudo stat: combat level (the Wiki lists it as `{{SCP|Combat|n}}`). */
        const val COMBAT = -1

        /** Pseudo stat: quest points (the Wiki lists it as `{{SCP|Quest|n}}`). */
        const val QUEST_POINTS = -2
    }
}

/** A quest requirement by Wiki name; [mode] is "complete", "started" or "partial". Not enforced (OPEN-AD2). */
internal class QuestReq(val name: String, val mode: String)

internal class DiaryTask(
    val id: TaskId,
    val text: String,
    val skills: List<SkillReq>,
    val quests: List<QuestReq>,
    /** Item and miscellaneous requirements as the Wiki words them (informational). */
    val other: String,
)

/** What a tier gives (Wiki "<Area> Diary", Rewards): the tier item and the Antique lamp's experience and minimum level. */
internal class TierReward(val obj: Int, val item: String, val lampXp: Int, val lampMinLevel: Int, val perks: List<String>)

internal class DiaryArea(
    /** Index in cache enum 595 (the order of the client's list and of its scripts). */
    val index: Int,
    val name: String,
    /** The taskmaster NPC's name (Wiki infobox). */
    val taskmaster: String,
    /** Tasks per tier, in Wiki order. */
    val tasks: List<List<DiaryTask>>,
    val rewards: List<TierReward>,
)

/**
 * The varbits the client reads for one diary (client scripts 56 area status, 2200 counts and claimed flags, 4072 task counts,
 * 3971 / 3980 summary totals). Task bits themselves are not read by any script (OPEN-AD3).
 */
internal class AreaVars(
    /** Non-zero once the diary is started (script 56: 0 not started, 1 started, 2 all four tiers complete). */
    val started: Int,
    /** Tasks done per tier (scripts 2200, 4072). */
    val count: IntArray,
    /** Tier complete flags (script 56); Karamja's three older ones are 2-bit fields that script 56 compares with 2. */
    val complete: IntArray,
    /** Reward claimed flags (scripts 2200, 4134). */
    val reward: IntArray,
)

/** All twelve diaries. Immutable. */
internal class DiaryData(val areas: List<DiaryArea>) {
    private val byId: Map<TaskId, DiaryTask> = areas.flatMap { a -> a.tasks.flatten() }.associateBy { it.id }

    fun task(id: TaskId): DiaryTask? = byId[id]

    companion object {
        private const val AREAS_TSV = "diary-areas.tsv"
        private const val TASKS_TSV = "diary-tasks.tsv"
        private const val REWARDS_TSV = "diary-rewards.tsv"
        private const val AREA_COLUMNS = 3
        private const val TASK_COLUMNS = 7
        private const val REWARD_COLUMNS = 7
        private const val PERK_SEPARATOR = " | "

        /** Reads the three TSVs written by tools/wiki-import/achievement_diaries.py. */
        fun load(): DiaryData {
            val areaRows = rows(AREAS_TSV, AREA_COLUMNS + 1)
            val tasksByAreaTier = HashMap<Int, MutableList<DiaryTask>>()
            for (c in rows(TASKS_TSV, TASK_COLUMNS)) {
                val area = c[0].toInt()
                val tier = c[1].toInt()
                val task = DiaryTask(TaskId.of(area, tier, c[2].toInt()), c[3], skills(c[4]), quests(c[5]), c[6])
                tasksByAreaTier.getOrPut(area * TIER_COUNT + tier) { ArrayList() } += task
            }
            val rewardsByArea = HashMap<Int, Array<TierReward?>>()
            for (c in rows(REWARDS_TSV, REWARD_COLUMNS)) {
                val perks = if (c[6].isEmpty()) emptyList() else c[6].split(PERK_SEPARATOR)
                val reward = TierReward(c[2].toInt(), c[3], c[4].toInt(), c[5].toInt(), perks)
                rewardsByArea.getOrPut(c[0].toInt()) { arrayOfNulls(TIER_COUNT) }[c[1].toInt()] = reward
            }
            val areas =
                areaRows.map { c ->
                    val index = c[0].toInt()
                    DiaryArea(
                        index,
                        c[1],
                        c[2],
                        List(TIER_COUNT) { tier -> tasksByAreaTier[index * TIER_COUNT + tier].orEmpty() },
                        rewardsByArea.getValue(index).map { requireNotNull(it) { "missing reward in area $index" } },
                    )
                }
            return DiaryData(areas)
        }

        private fun rows(name: String, columns: Int): List<List<String>> {
            val text = DiaryData::class.java.getResource(name)?.readText() ?: error("$name missing")
            return text.lines().drop(1).filter { it.isNotEmpty() }.map { line ->
                line.split('\t').also { check(it.size >= columns) { "$name: expected $columns columns in '$line'" } }
            }
        }

        private fun skills(field: String): List<SkillReq> =
            if (field.isEmpty()) {
                emptyList()
            } else {
                field.split(';').map { part ->
                    val (name, level, boostable) = part.split(':')
                    SkillReq(statOf(name), level.toInt(), boostable == "1")
                }
            }

        private fun quests(field: String): List<QuestReq> =
            if (field.isEmpty()) {
                emptyList()
            } else {
                field.split(';').map { part -> QuestReq(part.substringBeforeLast(':'), part.substringAfterLast(':')) }
            }

        private fun statOf(name: String): Int =
            when (name) {
                "Combat" -> {
                    SkillReq.COMBAT
                }

                "Quest" -> {
                    SkillReq.QUEST_POINTS
                }

                else -> {
                    (0 until Stats.COUNT).firstOrNull { Stats.name(it).equals(name, ignoreCase = true) }
                        ?: error("unknown skill '$name'")
                }
            }
    }
}

/** Varbit tables per area, in cache enum 595 order (index = [DiaryArea.index]). Names are the cache's own (symbols). */
internal val AREA_VARS: List<AreaVars> =
    listOf(
        // 0 Karamja
        AreaVars(
            started = Varbits.ATJUN_STARTED,
            count =
                intArrayOf(
                    Varbits.KARAMJA_EASY_COUNT,
                    Varbits.KARAMJA_MED_COUNT,
                    Varbits.KARAMJA_HARD_COUNT,
                    Varbits.KARAMJA_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.ATJUN_EASY_DONE,
                    Varbits.ATJUN_MED_DONE,
                    Varbits.ATJUN_HARD_DONE,
                    Varbits.KARAMJA_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.ATJUN_EASY_REWARD,
                    Varbits.ATJUN_MED_REWARD,
                    Varbits.ATJUN_HARD_REWARD,
                    Varbits.KARAMJA_ELITE_REWARD,
                ),
        ),
        // 1 Ardougne
        AreaVars(
            started = Varbits.STARTED_ARDOUGNE_DIARY,
            count =
                intArrayOf(
                    Varbits.ARDOUGNE_EASY_COUNT,
                    Varbits.ARDOUGNE_MED_COUNT,
                    Varbits.ARDOUGNE_HARD_COUNT,
                    Varbits.ARDOUGNE_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.ARDOUGNE_DIARY_EASY_COMPLETE,
                    Varbits.ARDOUGNE_DIARY_MEDIUM_COMPLETE,
                    Varbits.ARDOUGNE_DIARY_HARD_COMPLETE,
                    Varbits.ARDOUGNE_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.ARDOUGNE_EASY_REWARD,
                    Varbits.ARDOUGNE_MEDIUM_REWARD,
                    Varbits.ARDOUGNE_HARD_REWARD,
                    Varbits.ARDOUGNE_ELITE_REWARD,
                ),
        ),
        // 2 Falador
        AreaVars(
            started = Varbits.STARTED_FALADOR_DIARY,
            count =
                intArrayOf(
                    Varbits.FALADOR_EASY_COUNT,
                    Varbits.FALADOR_MED_COUNT,
                    Varbits.FALADOR_HARD_COUNT,
                    Varbits.FALADOR_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.FALADOR_DIARY_EASY_COMPLETE,
                    Varbits.FALADOR_DIARY_MEDIUM_COMPLETE,
                    Varbits.FALADOR_DIARY_HARD_COMPLETE,
                    Varbits.FALADOR_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.FALADOR_EASY_REWARD,
                    Varbits.FALADOR_MEDIUM_REWARD,
                    Varbits.FALADOR_HARD_REWARD,
                    Varbits.FALADOR_ELITE_REWARD,
                ),
        ),
        // 3 Fremennik
        AreaVars(
            started = Varbits.STARTED_FREMENNIK_DIARY,
            count =
                intArrayOf(
                    Varbits.FREMENNIK_EASY_COUNT,
                    Varbits.FREMENNIK_MED_COUNT,
                    Varbits.FREMENNIK_HARD_COUNT,
                    Varbits.FREMENNIK_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.FREMENNIK_DIARY_EASY_COMPLETE,
                    Varbits.FREMENNIK_DIARY_MEDIUM_COMPLETE,
                    Varbits.FREMENNIK_DIARY_HARD_COMPLETE,
                    Varbits.FREMENNIK_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.FREMENNIK_EASY_REWARD,
                    Varbits.FREMENNIK_MEDIUM_REWARD,
                    Varbits.FREMENNIK_HARD_REWARD,
                    Varbits.FREMENNIK_ELITE_REWARD,
                ),
        ),
        // 4 Kandarin
        AreaVars(
            started = Varbits.STARTED_KANDARIN_DIARY,
            count =
                intArrayOf(
                    Varbits.KANDARIN_EASY_COUNT,
                    Varbits.KANDARIN_MED_COUNT,
                    Varbits.KANDARIN_HARD_COUNT,
                    Varbits.KANDARIN_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.KANDARIN_DIARY_EASY_COMPLETE,
                    Varbits.KANDARIN_DIARY_MEDIUM_COMPLETE,
                    Varbits.KANDARIN_DIARY_HARD_COMPLETE,
                    Varbits.KANDARIN_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.KANDARIN_EASY_REWARD,
                    Varbits.KANDARIN_MEDIUM_REWARD,
                    Varbits.KANDARIN_HARD_REWARD,
                    Varbits.KANDARIN_ELITE_REWARD,
                ),
        ),
        // 5 Desert
        AreaVars(
            started = Varbits.STARTED_DESERT_DIARY,
            count =
                intArrayOf(
                    Varbits.DESERT_EASY_COUNT,
                    Varbits.DESERT_MED_COUNT,
                    Varbits.DESERT_HARD_COUNT,
                    Varbits.DESERT_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.DESERT_DIARY_EASY_COMPLETE,
                    Varbits.DESERT_DIARY_MEDIUM_COMPLETE,
                    Varbits.DESERT_DIARY_HARD_COMPLETE,
                    Varbits.DESERT_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.DESERT_EASY_REWARD,
                    Varbits.DESERT_MEDIUM_REWARD,
                    Varbits.DESERT_HARD_REWARD,
                    Varbits.DESERT_ELITE_REWARD,
                ),
        ),
        // 6 Lumbridge
        AreaVars(
            started = Varbits.STARTED_LUMBRIDGE_DIARY,
            count =
                intArrayOf(
                    Varbits.LUMBRIDGE_EASY_COUNT,
                    Varbits.LUMBRIDGE_MED_COUNT,
                    Varbits.LUMBRIDGE_HARD_COUNT,
                    Varbits.LUMBRIDGE_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.LUMBRIDGE_DIARY_EASY_COMPLETE,
                    Varbits.LUMBRIDGE_DIARY_MEDIUM_COMPLETE,
                    Varbits.LUMBRIDGE_DIARY_HARD_COMPLETE,
                    Varbits.LUMBRIDGE_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.LUMBRIDGE_EASY_REWARD,
                    Varbits.LUMBRIDGE_MEDIUM_REWARD,
                    Varbits.LUMBRIDGE_HARD_REWARD,
                    Varbits.LUMBRIDGE_ELITE_REWARD,
                ),
        ),
        // 7 Morytania
        AreaVars(
            started = Varbits.STARTED_MORYTANIA_DIARY,
            count =
                intArrayOf(
                    Varbits.MORYTANIA_EASY_COUNT,
                    Varbits.MORYTANIA_MED_COUNT,
                    Varbits.MORYTANIA_HARD_COUNT,
                    Varbits.MORYTANIA_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.MORYTANIA_DIARY_EASY_COMPLETE,
                    Varbits.MORYTANIA_DIARY_MEDIUM_COMPLETE,
                    Varbits.MORYTANIA_DIARY_HARD_COMPLETE,
                    Varbits.MORYTANIA_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.MORYTANIA_EASY_REWARD,
                    Varbits.MORYTANIA_MEDIUM_REWARD,
                    Varbits.MORYTANIA_HARD_REWARD,
                    Varbits.MORYTANIA_ELITE_REWARD,
                ),
        ),
        // 8 Varrock
        AreaVars(
            started = Varbits.STARTED_VARROCK_DIARY,
            count =
                intArrayOf(
                    Varbits.VARROCK_EASY_COUNT,
                    Varbits.VARROCK_MED_COUNT,
                    Varbits.VARROCK_HARD_COUNT,
                    Varbits.VARROCK_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.VARROCK_DIARY_EASY_COMPLETE,
                    Varbits.VARROCK_DIARY_MEDIUM_COMPLETE,
                    Varbits.VARROCK_DIARY_HARD_COMPLETE,
                    Varbits.VARROCK_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.VARROCK_EASY_REWARD,
                    Varbits.VARROCK_MEDIUM_REWARD,
                    Varbits.VARROCK_HARD_REWARD,
                    Varbits.VARROCK_ELITE_REWARD,
                ),
        ),
        // 9 Wilderness
        AreaVars(
            started = Varbits.STARTED_WILDERNESS_DIARY,
            count =
                intArrayOf(
                    Varbits.WILDERNESS_EASY_COUNT,
                    Varbits.WILDERNESS_MED_COUNT,
                    Varbits.WILDERNESS_HARD_COUNT,
                    Varbits.WILDERNESS_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.WILDERNESS_DIARY_EASY_COMPLETE,
                    Varbits.WILDERNESS_DIARY_MEDIUM_COMPLETE,
                    Varbits.WILDERNESS_DIARY_HARD_COMPLETE,
                    Varbits.WILDERNESS_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.WILDERNESS_EASY_REWARD,
                    Varbits.WILDERNESS_MEDIUM_REWARD,
                    Varbits.WILDERNESS_HARD_REWARD,
                    Varbits.WILDERNESS_ELITE_REWARD,
                ),
        ),
        // 10 Western
        AreaVars(
            started = Varbits.STARTED_WESTERN_DIARY,
            count =
                intArrayOf(
                    Varbits.WESTERN_EASY_COUNT,
                    Varbits.WESTERN_MED_COUNT,
                    Varbits.WESTERN_HARD_COUNT,
                    Varbits.WESTERN_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.WESTERN_DIARY_EASY_COMPLETE,
                    Varbits.WESTERN_DIARY_MEDIUM_COMPLETE,
                    Varbits.WESTERN_DIARY_HARD_COMPLETE,
                    Varbits.WESTERN_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.WESTERN_EASY_REWARD,
                    Varbits.WESTERN_MEDIUM_REWARD,
                    Varbits.WESTERN_HARD_REWARD,
                    Varbits.WESTERN_ELITE_REWARD,
                ),
        ),
        // 11 Kourend
        AreaVars(
            started = Varbits.STARTED_KOUREND_DIARY,
            count =
                intArrayOf(
                    Varbits.KOUREND_EASY_COUNT,
                    Varbits.KOUREND_MED_COUNT,
                    Varbits.KOUREND_HARD_COUNT,
                    Varbits.KOUREND_ELITE_COUNT,
                ),
            complete =
                intArrayOf(
                    Varbits.KOUREND_DIARY_EASY_COMPLETE,
                    Varbits.KOUREND_DIARY_MEDIUM_COMPLETE,
                    Varbits.KOUREND_DIARY_HARD_COMPLETE,
                    Varbits.KOUREND_DIARY_ELITE_COMPLETE,
                ),
            reward =
                intArrayOf(
                    Varbits.KOUREND_EASY_REWARD,
                    Varbits.KOUREND_MEDIUM_REWARD,
                    Varbits.KOUREND_HARD_REWARD,
                    Varbits.KOUREND_ELITE_REWARD,
                ),
        ),
    )

/** Varbit 4470: set while any Wilderness tier is complete (client scripts 99 and 2520). */
internal const val WILDERNESS_ANY_COMPLETE = Varbits.WILDERNESS_DIARY_ANY_COMPLETE
