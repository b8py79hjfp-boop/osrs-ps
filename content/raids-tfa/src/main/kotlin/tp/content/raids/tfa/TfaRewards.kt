package tp.content.raids.tfa

/**
 * The Fractured Archive's reward math (docs/mechanics/raids/tfa.md TFA12-TFA20). Pure functions: no world state.
 */
public object TfaRewards {
    /** TFA12: every room is worth 100 contribution, Ocellus 300: 700 per raid (QM3Nov; Chest). */
    public const val ROOM_CONTRIBUTION: Int = 100
    public const val OCELLUS_CONTRIBUTION: Int = 300
    public const val RAID_CONTRIBUTION: Int = 4 * ROOM_CONTRIBUTION + OCELLUS_CONTRIBUTION

    /** TFA18: base unique chance 1/14 for one or two players (Chest). */
    public const val BASE_UNIQUE_DENOMINATOR: Double = 14.0

    /** TFA18: +4% of the base per player beyond two (Chest's table: 3 -> 1/13.46, 8 -> 1/11.29). */
    public const val PER_PLAYER_BONUS: Double = 0.04

    /** TFA13: a death costs 10% of the player's expected contribution (QM3Nov). */
    public const val DEATH_PENALTY_SHARE: Double = 0.10

    private const val SCALE_FLOOR = 2

    /** The team's unique chance for [players] players (before the OPEN-TFA5 team-death reduction). */
    public fun uniqueChance(players: Int): Double = (1.0 + PER_PLAYER_BONUS * maxOf(0, players - SCALE_FLOOR)) / BASE_UNIQUE_DENOMINATOR

    /** A player's expected contribution: an equal share of 700 (QM3Nov: 350 in a duo, 87.5 in an eight). */
    public fun expectedContribution(players: Int): Double = RAID_CONTRIBUTION.toDouble() / maxOf(1, players)

    /** TFA13: the points one death costs (35 in a duo). */
    public fun deathPenalty(players: Int): Double = DEATH_PENALTY_SHARE * expectedContribution(players)

    /** TFA19: each player's chance to receive the unique: contribution over the team's total. */
    public fun recipientChances(contribution: Map<String, Double>): Map<String, Double> {
        val total = contribution.values.sum()
        return if (total <= 0.0) contribution.mapValues { 0.0 } else contribution.mapValues { it.value / total }
    }

    /**
     * TFA20: unique weights out of 15 (QM3Nov; Chest). Keys are the obj ids (wiki infoboxes; the cache names the Horn
     * of Bran `tfa_zeal_reward`, which is the `TFA_DROPTABLE_ENTRY_UNIQUE_ZEAL` row).
     */
    public val UNIQUE_WEIGHTS: Map<Int, Int> =
        linkedMapOf(
            RONDACHE to 5,
            ZORYAS_TOME_UNCHARGED to 4,
            HORN_OF_BRAN to 3,
            ASCENSION_CROSSBOWS to 2,
            OBLIGATOR_UNCHARGED to 1,
        )

    /** Cache dbrow names of `TFA_DROPTABLE_ENTRIES_UNIQUE`, mapped to the same objs (gamevals). */
    public val UNIQUE_ROWS: Map<String, Int> =
        mapOf(
            "TFA_DROPTABLE_ENTRY_UNIQUE_RONDACHE" to RONDACHE,
            "TFA_DROPTABLE_ENTRY_UNIQUE_ZORYAS_TOME" to ZORYAS_TOME_UNCHARGED,
            "TFA_DROPTABLE_ENTRY_UNIQUE_ZEAL" to HORN_OF_BRAN,
            "TFA_DROPTABLE_ENTRY_UNIQUE_ASCENSION_CROSSBOWS" to ASCENSION_CROSSBOWS,
            "TFA_DROPTABLE_ENTRY_UNIQUE_OBLIGATOR" to OBLIGATOR_UNCHARGED,
        )

    // Obj ids from the items' wiki infoboxes, checked against cache names (2026-11-08).
    public const val RONDACHE: Int = 34719
    public const val ZORYAS_TOME_UNCHARGED: Int = 34727
    public const val HORN_OF_BRAN: Int = 34679
    public const val ASCENSION_CROSSBOWS: Int = 34681
    public const val OBLIGATOR_UNCHARGED: Int = 34717
}
