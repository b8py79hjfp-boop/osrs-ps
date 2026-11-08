package tp.content.raids.cox

import tp.content.raids.RaidMember
import tp.content.raids.RaidRun
import tp.content.raids.Raids

/**
 * The points ledger (cox.md CX21-CX32): personal points live on [RaidMember.points] (damage through raids-core's
 * contribution rules, other sources through [award]); the team total is their sum plus team-only changes (the CX30 5%
 * case, the CX32 Challenge Mode bonus), kept in [CoxState.teamAdjust].
 */
internal object CoxPoints {
    /**
     * OPEN-CX13: points per HP of damage. Only "an additional 2 points" per HP (31 Jan 2019) is stated; 4 assumes the
     * earlier rate was 2. Named so a capture can replace it.
     */
    const val DAMAGE_POINTS_PER_HP = 4.0

    /** CX30: a death costs 40% of the points held. */
    private const val DEATH_SHARE = 0.4

    /** CX30: below 5% of the team total, the team loses 5% instead. */
    private const val LOW_SHARE = 0.05

    /** CX32: Challenge Mode inside the target time adds 5,000 points per player to the team total. */
    const val CM_BONUS_PER_PLAYER = 5000.0

    /** CX23: the common-loot quantities read at most 131,071 personal points. */
    const val COMMON_CAP = 131_071

    /** Points per HP on [npcType] (OPEN-CX13: no per-NPC multipliers, mini-boss decay or thrall rule is sourced yet). */
    fun multiplier(
        @Suppress("UNUSED_PARAMETER") npcType: Int,
    ): Double = DAMAGE_POINTS_PER_HP

    /** The team total (CX21). */
    fun team(run: RaidRun): Double = maxOf(0.0, run.members.values.sumOf { it.points } + (CoxFlow.state(run)?.teamAdjust ?: 0.0))

    /**
     * CX30 (OPEN-CX15: the team also loses the player's 40%; in the low-points case the player's own 40% still goes and
     * the team ends 5% lower in all). Returns the personal loss for raids-core.
     */
    fun deathPenalty(m: RaidMember, run: RaidRun): Double {
        val team = team(run)
        val lost = m.points * DEATH_SHARE
        if (team > 0 && m.points < team * LOW_SHARE) {
            CoxFlow.state(run)?.let { it.teamAdjust -= maxOf(0.0, team * LOW_SHARE - lost) }
        }
        return lost
    }

    /** Points from a non-damage source (storage, shortcuts, grubs, kindling, food, potions). */
    fun award(run: RaidRun, name: String, points: Double) {
        Raids.addPoints(run, name, points)
    }

    /** CX32 (OPEN-CX16: per player in the party at the start). */
    fun challengeBonus(run: RaidRun) {
        CoxFlow.state(run)?.let { it.teamAdjust += CM_BONUS_PER_PLAYER * run.scaling.partySize }
        run.markDirty()
    }
}
