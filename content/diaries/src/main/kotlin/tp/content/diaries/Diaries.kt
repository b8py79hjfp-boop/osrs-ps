package tp.content.diaries

import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Diary state and the completion API (docs/mechanics/achievement-diaries.md AD1-AD8). Task completion is a bit per task in the
 * saved value `diary.<area>.<tier>`; tier counts, "tier complete", "reward claimed" and "started" are derived from it and
 * mirrored into the varbits the client scripts read.
 */
internal class Diaries(val data: DiaryData) {
    /**
     * Completes [id] for [player] (AD1): a no-op returning false when it is already done, unknown or a skill requirement is
     * not met (the content that calls this has normally checked the requirement itself; this is the diary's own check, AD2).
     * Sets the task bit, the tier count and started varbits, sends the chat line and, when it was the tier's last task, the
     * tier is complete (AD4).
     */
    fun complete(player: PlayerRef, id: TaskId): Boolean {
        val task = data.task(id) ?: return false
        val key = tasksKey(id.area, id.tier)
        val mask = player.saved(key) ?: 0L
        val bit = 1L shl (id.index - 1)
        if (mask and bit != 0L || !meetsSkills(player, task)) return false
        player.setSaved(key, mask or bit)
        sync(player, id.area, id.tier)
        val area = data.areas[id.area]
        player.message(
            "Well done! You have completed ${article(id.tier)} ${TIER_NAMES[id.tier]} task in the ${area.name} area. " +
                "Your Achievement Diary has been updated.",
        )
        if (isTierDone(player, id.area, id.tier)) {
            player.message(
                "Congratulations! You have completed all of the ${TIER_NAMES[id.tier]} tasks in the ${area.name} area. " +
                    "Speak to ${area.taskmaster} to claim your reward.",
            )
        }
        return true
    }

    fun isDone(player: PlayerRef, id: TaskId): Boolean = (player.saved(tasksKey(id.area, id.tier)) ?: 0L) and (1L shl (id.index - 1)) != 0L

    /** Tasks of [tier] in [area] that [player] has done. */
    fun doneCount(player: PlayerRef, area: Int, tier: Int): Int = java.lang.Long.bitCount(player.saved(tasksKey(area, tier)) ?: 0L)

    fun isTierDone(player: PlayerRef, area: Int, tier: Int): Boolean = doneCount(player, area, tier) == data.areas[area].tasks[tier].size

    fun isClaimed(player: PlayerRef, area: Int, tier: Int): Boolean = (player.saved(claimedKey(area)) ?: 0L) and (1L shl tier) != 0L

    /**
     * The tier [player] can claim from [area]'s taskmaster now (AD5): the lowest complete tier not yet claimed, if every tier
     * before it is complete too (Wiki "Achievement Diary": "claiming the rewards for a tier requires all preceding tiers of that
     * diary to be completed as well"). Null when there is none.
     */
    fun claimable(player: PlayerRef, area: Int): Int? {
        for (tier in 0 until TIER_COUNT) {
            if (!isTierDone(player, area, tier)) return null
            if (!isClaimed(player, area, tier)) return tier
        }
        return null
    }

    /** The highest tier [player] has claimed in [area], or null. */
    fun highestClaimed(player: PlayerRef, area: Int): Int? = (TIER_COUNT - 1 downTo 0).firstOrNull { isClaimed(player, area, it) }

    /**
     * Claims [tier] of [area]: the tier item and its Antique lamp in one item transaction (AD5), then the claimed flag. False
     * (nothing given) when it is not claimable or the backpack lacks room.
     */
    fun claim(player: PlayerRef, area: Int, tier: Int): Boolean {
        if (claimable(player, area) != tier) return false
        val reward = data.areas[area].rewards[tier]
        val given =
            player.transaction("achievement diary reward") {
                add(Invs.INV, reward.obj, 1) && add(Invs.INV, lampObj(area, tier), 1)
            }
        if (!given) return false
        player.setSaved(claimedKey(area), (player.saved(claimedKey(area)) ?: 0L) or (1L shl tier))
        sync(player, area, tier)
        return true
    }

    /** Whether [player] holds [area]'s tier item of [tier] in the backpack, worn or in the bank (for "Can I have another"). */
    fun owns(player: PlayerRef, area: Int, tier: Int): Boolean {
        val obj = data.areas[area].rewards[tier].obj
        return intArrayOf(Invs.INV, Invs.WORN, Invs.BANK).any { player.inv(it).total(obj) > 0 }
    }

    /** Gives another copy of the tier item of the highest claimed tier (AD6); false without room. */
    fun replace(player: PlayerRef, area: Int, tier: Int): Boolean =
        player.transaction("achievement diary item") { add(Invs.INV, data.areas[area].rewards[tier].obj, 1) }

    /** Login: every diary's varbits from the saved task bits (AD3). */
    fun syncAll(player: PlayerRef) {
        for (area in data.areas) for (tier in 0 until TIER_COUNT) sync(player, area.index, tier)
    }

    /** Re-sends the count, complete and claimed varbits of one tier and the area's started and any-complete flags. */
    private fun sync(player: PlayerRef, area: Int, tier: Int) {
        val vars = AREA_VARS[area]
        val done = doneCount(player, area, tier)
        val complete = isTierDone(player, area, tier)
        player.setVarbit(vars.count[tier], done)
        player.setVarbit(vars.complete[tier], if (complete) completeValue(area, tier) else 0)
        player.setVarbit(vars.reward[tier], if (isClaimed(player, area, tier)) 1 else 0)
        val any = (0 until TIER_COUNT).any { doneCount(player, area, it) > 0 }
        player.setVarbit(vars.started, if (any) 1 else 0)
        if (area == WILDERNESS) {
            player.setVarbit(WILDERNESS_ANY_COMPLETE, if ((0 until TIER_COUNT).any { isTierDone(player, area, it) }) 1 else 0)
        }
    }

    /**
     * Value of a tier-complete varbit: 1, but Karamja's easy, medium and hard fields are two bits wide and client script 56
     * compares them with 2 (OPEN-AD2).
     */
    private fun completeValue(area: Int, tier: Int): Int = if (area == KARAMJA && tier < KARAMJA_ELITE) 2 else 1

    /** The Antique lamp obj of a tier: Karamja has its own three lower lamps (cache obj names `atjun_*_lamp`), the rest `diary_*_lamp`. */
    fun lampObj(area: Int, tier: Int): Int =
        if (area == KARAMJA && tier < KARAMJA_ELITE) {
            KARAMJA_LAMPS[tier]
        } else {
            DIARY_LAMPS[tier]
        }

    /** Skill requirements (AD2): boostable ones against the current level, the others against the base level. */
    private fun meetsSkills(player: PlayerRef, task: DiaryTask): Boolean =
        task.skills.all { req ->
            when (req.stat) {
                SkillReq.COMBAT -> player.combatLevel >= req.level

                SkillReq.QUEST_POINTS -> true

                // OPEN-AD2: no quest point count in the repository
                else -> (if (req.boostable) player.level(req.stat) else player.baseLevel(req.stat)) >= req.level
            }
        }

    private fun article(tier: Int): String = if (tier == ELITE) "an" else "a"

    companion object {
        /** Saved bitmask of the tasks done in a tier (bit n-1 = Wiki task n). */
        fun tasksKey(area: Int, tier: Int): String = "diary.$area.$tier"

        /** Saved bitmask of the claimed tiers of an area (bit = tier). */
        fun claimedKey(area: Int): String = "diary.claimed.$area"

        /** Saved bitmask of the parts done of a multi-part task (several kills; AD7). */
        fun partsKey(id: TaskId): String = "diary.part.${id.raw}"

        /** Cache enum 595 indices of the areas the code treats specially. */
        const val KARAMJA = 0
        const val WILDERNESS = 9
        private const val KARAMJA_ELITE = 3
        private const val ELITE = 3

        private val KARAMJA_LAMPS = intArrayOf(Objs.ATJUN_EASY_LAMP, Objs.ATJUN_MED_LAMP, Objs.ATJUN_HARD_LAMP)
        private val DIARY_LAMPS = intArrayOf(Objs.DIARY_EASY_LAMP, Objs.DIARY_MED_LAMP, Objs.DIARY_HARD_LAMP, Objs.DIARY_ELITE_LAMP)
    }
}
