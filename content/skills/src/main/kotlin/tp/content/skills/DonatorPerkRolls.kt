package tp.content.skills

import tp.plugin.PlayerRef
import tp.plugin.commands.perk
import tp.util.DonatorPerk

/** Percent denominator of the donator perk rows (D-093, util/src/main/resources/donator/perks.tsv). */
internal const val DONATOR_PERCENT = 100

/**
 * One world-RNG roll of a percentage donator [perk] (D-093). A perk value of 0 (no tier, or boosts off) rolls nothing, so
 * the unmodified OSRS path consumes exactly the same random numbers.
 */
internal fun PlayerRef.donatorRoll(perk: DonatorPerk): Boolean {
    val chance = perk(perk)
    return chance > 0 && random(DONATOR_PERCENT) < chance
}

/**
 * [obj], or its noted form when the donator auto-note perk rolls (D-093, perks.tsv row "Gain a chance of automatically noting
 * resources when Fishing, Woodcutting or Mining"); [obj] itself when it has no note. No roll at perk 0.
 */
internal fun PlayerRef.autoNoted(obj: Int): Int =
    if (donatorRoll(DonatorPerk.GAIN_A_CHANCE_OF_AUTOMATICALLY_NOTING_RESOURCES_WHEN_FISHING)) noted(obj).takeIf { it >= 0 } ?: obj else obj
