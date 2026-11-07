package tp.content.sailing

/**
 * One rank of a Barracuda trial from `Sailing_Bt_Trial_Core.RANK_DATA` (db 143 col 22), tuple
 * `(target ticks, laps, supplies required, bonus xp x10, trial xp x10)` (docs/plans/sailing.md 5.3; verified against the
 * three Wiki tables, docs/mechanics/sailing-barracuda.md R1). Rank 0 is the unranked completion (no target time, no bonus).
 */
internal class BarracudaRank(
    /** 0 unranked, 1 Swordfish, 2 Shark, 3 Marlin. */
    val rank: Int,
    /** Time to beat in game ticks; 0 for the unranked tier. */
    val targetTicks: Int,
    val laps: Int,
    val supplies: Int,
    /** One-off rank bonus XP in tenths of an XP point (the cache stores XP x10). */
    val bonusXpTenths: Int,
    /** Completion XP in tenths of an XP point. */
    val trialXpTenths: Int,
) {
    val bonusXp: Int get() = bonusXpTenths / XP_TENTHS
    val trialXp: Int get() = trialXpTenths / XP_TENTHS

    private companion object {
        /** XP is stored x10 so one decimal fits the int cell (client XP drops use the same convention). */
        const val XP_TENTHS = 10
    }
}

/**
 * One row of `Sailing_Bt_Trial_Core` (db 143). Trial level, crew rule, start tile, boundary, paint odds and the pet rate are
 * server-only columns the cache strips; the trial number is the row's position (0 Tempor Tantrum, 1 Jubbly Jive, 2 Gwenith Glide).
 */
internal class BarracudaTrial(
    val row: Int,
    val index: Int,
    val name: String,
    val trackerIcons: List<Int>,
    /** HUD label of the main objective counter ("Rum delivered"). */
    val primaryRequirement: String,
    /** HUD label of the secondary counter ("Lost supplies"). */
    val secondaryRequirement: String,
    /** Ranks 0..3 in order. */
    val ranks: List<BarracudaRank>,
    /** Reward objs of Swordfish, Shark and Marlin (cache `REWARD_*` cells: key, barrel stand / wind mote, fabric roll). */
    val rewards: List<Int>,
)
