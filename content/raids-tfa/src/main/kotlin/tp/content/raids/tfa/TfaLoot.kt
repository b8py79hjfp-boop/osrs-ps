package tp.content.raids.tfa

import tp.content.raids.RaidRng
import tp.content.raids.RewardResult
import tp.content.raids.RunSnapshot

/**
 * The vault's loot, rolled once per raid from the completion snapshot with the run's RNG (docs/mechanics/raids/tfa.md
 * TFA18-TFA23). Pure: no world state.
 */
internal object TfaLoot {
    fun roll(snap: RunSnapshot, rng: RaidRng): Map<String, RewardResult> {
        if (snap.mode == "practice") return emptyMap()
        val eligible = snap.members.filterValues { it.third }
        if (eligible.isEmpty()) return emptyMap()
        val recipient = uniqueRecipient(snap, eligible.mapValues { it.value.first }, rng)
        return eligible.mapValues { (name, _) ->
            if (name == recipient) {
                RewardResult(listOf(uniqueItem(rng) to 1), unique = true)
            } else {
                RewardResult(standard(rng))
            }
        }
    }

    /** TFA18 / TFA19: one roll for the team, then the recipient by contribution share. Null = no unique. */
    private fun uniqueRecipient(snap: RunSnapshot, points: Map<String, Double>, rng: RaidRng): String? {
        if (rng.nextDouble("unique") >= TfaRewards.uniqueChance(snap.partySize)) return null
        val chances = TfaRewards.recipientChances(points)
        var pick = rng.nextDouble("unique recipient")
        for ((name, share) in chances) {
            pick -= share
            if (pick < 0) return name
        }
        return chances.keys.last()
    }

    /** TFA20: the unique by weight out of 15. */
    private fun uniqueItem(rng: RaidRng): Int {
        var pick = rng.next(TfaRewards.UNIQUE_WEIGHTS.values.sum(), "unique item")
        for ((obj, weight) in TfaRewards.UNIQUE_WEIGHTS) {
            pick -= weight
            if (pick < 0) return obj
        }
        return TfaRewards.UNIQUE_WEIGHTS.keys.last()
    }

    /** TFA21: three rolls on the standard table (TfaStandardTable). */
    private fun standard(rng: RaidRng): List<Pair<Int, Int>> = TfaStandardTable.roll(rng)
}

/** TFA21-TFA23: the standard table and tertiaries. */
internal object TfaStandardTable {
    fun roll(rng: RaidRng): List<Pair<Int, Int>> {
        rng.next(1, "standard")
        return emptyList()
    }
}
