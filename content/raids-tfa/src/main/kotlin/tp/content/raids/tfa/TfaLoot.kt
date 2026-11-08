package tp.content.raids.tfa

import tp.content.raids.RaidRng
import tp.content.raids.RewardResult
import tp.content.raids.RunSnapshot
import tp.symbols.Objs

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
        return eligible.mapValues { (name, member) ->
            val deaths = member.second
            if (name == recipient) {
                RewardResult(listOf(uniqueItem(rng) to 1) + TfaTertiaries.roll(rng), unique = true)
            } else if (TfaTertiaries.rollCabbage(deaths, rng)) {
                RewardResult(listOf(TfaTertiaries.CABBAGE to 1), tags = setOf(TfaTertiaries.JUNK_TAG))
            } else {
                RewardResult(standard(rng) + TfaTertiaries.roll(rng))
            }
        }
    }

    /** TFA18 / TFA19: one roll for the team, then the recipient by contribution share. Null = no unique. */
    private fun uniqueRecipient(snap: RunSnapshot, points: Map<String, Double>, rng: RaidRng): String? {
        if (rng.nextDouble("unique") >= TfaRewards.uniqueChance(snap.partySize)) return null
        val shares = TfaRewards.recipientChances(points)
        // OPEN-TFA33: with no contribution at all (every member at 0) the unique goes to one of them evenly.
        val chances = if (shares.values.sum() <= 0.0) shares.mapValues { 1.0 / shares.size } else shares
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

/**
 * TFA21: the standard table, rolled [ROLLS] times when a player has no unique. Weights are per roll out of
 * [TOTAL_WEIGHT]; quantities are uniform in the inclusive range (Wiki "Chest (The Fractured Archive)", drop tables;
 * the cache's `TFA_DROPTABLE_ENTRY_*` dbrows list the same 33 items). Every roll goes through the [RaidRng].
 */
internal object TfaStandardTable {
    /** TFA21: "the standard loot table is rolled three times" (Wiki Chest, Standard loot). */
    const val ROLLS = 3

    /** TFA21: the rarity denominator of every entry (Wiki Chest: 11/330 ... 55/330). */
    const val TOTAL_WEIGHT = 330

    /** TFA21: "each roll has a 1/51 chance to be multiplied five times" (Wiki Chest). */
    const val BONUS_ONE_IN = 51

    /** TFA21: the multiplier of a bonus roll (Wiki Chest). */
    const val BONUS_MULTIPLIER = 5

    /** Marks an entry with no noted form (it stacks on its own). */
    private const val NOT_NOTED = -1

    /**
     * One table row. [noted] is the noted form delivered when the quantity is above one and the item does not stack
     * (OPEN-TFA17: the Wiki lists no "(noted)" and a 6-slot claim inventory cannot hold 900 loose ores).
     */
    private class Entry(val obj: Int, val weight: Int, val min: Int, val max: Int, val noted: Int = NOT_NOTED)

    // Obj ids by cache name (config obj, 2026-11-08); noted ids are the cache's note of the same item (certTemplate 799).
    private val ENTRIES: List<Entry> =
        listOf(
            // Weapons and armour, 11/330 each.
            Entry(Objs.BATTLESTAFF, 11, 34, 37, noted = 1392),
            Entry(Objs.DRAGON_PLATELEGS, 11, 1, 1, noted = 4088),
            Entry(Objs.DRAGON_PLATESKIRT, 11, 1, 1, noted = 4586),
            Entry(Objs.RUNE_KITESHIELD, 11, 5, 7, noted = 1202),
            // Runes, 11/330 each.
            Entry(Objs.NATURERUNE, 11, 301, 385),
            Entry(Objs.ASTRALRUNE, 11, 301, 385),
            Entry(Objs.BLOODRUNE, 11, 301, 385),
            Entry(Objs.SOULRUNE, 11, 301, 380),
            Entry(Objs.EARTHRUNE, 11, 3009, 3662),
            // Herbs, 7/330 each (grimy herbs: the gameval names them UNIDENTIFIED_*).
            Entry(Objs.UNIDENTIFIED_HARRALANDER, 7, 60, 66, noted = 206),
            Entry(Objs.UNIDENTIFIED_RANARR, 7, 18, 20, noted = 208),
            Entry(Objs.UNIDENTIFIED_TOADFLAX, 7, 24, 28, noted = 3050),
            // Seeds, 7/330 each.
            Entry(Objs.SNAPDRAGON_SEED, 7, 3, 4),
            Entry(Objs.KWUARM_SEED, 7, 7, 8),
            Entry(Objs.SNAPE_GRASS_SEED, 7, 7, 8),
            Entry(Objs.YEW_SEED, 7, 4, 5),
            // Cut gems, 11/330 each.
            Entry(Objs.SAPPHIRE, 11, 24, 27, noted = 1608),
            Entry(Objs.EMERALD, 11, 24, 26, noted = 1606),
            Entry(Objs.RUBY, 11, 23, 27, noted = 1604),
            Entry(Objs.DIAMOND, 11, 24, 26, noted = 1602),
            // Ores, 6/330 each.
            Entry(Objs.IRON_ORE, 6, 851, 915, noted = 441),
            Entry(Objs.ADAMANTITE_ORE, 6, 60, 72, noted = 450),
            Entry(Objs.RUNITE_ORE, 6, 25, 26, noted = 452),
            // Other.
            Entry(Objs.ASCENSION_SHARDS, 55, 75, 82),
            Entry(Objs.XBOWS_BOLT_TIPS_ONYX, 11, 50, 58),
            Entry(Objs.EARTH_ORB, 11, 68, 75, noted = 576),
            Entry(Objs.WINE_OF_ZAMORAK, 7, 32, 36, noted = 246),
            Entry(Objs.DESICCATED_PAGE, 6, 30, 36),
            Entry(Objs.COSMIC_SOUL_CATALYST, 6, 180, 191),
            Entry(Objs.BLANKRUNE_HIGH, 6, 1404, 1632, noted = 7937),
            Entry(Objs.MAHOGANY_LOGS, 6, 140, 152, noted = 8836),
            Entry(Objs.DRAGON_ARROWHEADS, 6, 350, 369),
            Entry(Objs.RAW_SHARK, 6, 80, 92, noted = 384),
        )

    init {
        check(ENTRIES.sumOf { it.weight } == TOTAL_WEIGHT) { "TFA standard table weights must sum to $TOTAL_WEIGHT" }
    }

    /** Three weighted rolls, equal items merged (their quantities add up so the claim inventory stays small). */
    fun roll(rng: RaidRng): List<Pair<Int, Int>> {
        val merged = LinkedHashMap<Int, Int>()
        repeat(ROLLS) {
            val entry = pick(rng)
            var quantity = entry.min + rng.next(entry.max - entry.min + 1, "standard quantity")
            if (rng.next(BONUS_ONE_IN, "standard bonus") == 0) quantity *= BONUS_MULTIPLIER
            val obj = if (quantity > 1 && entry.noted != NOT_NOTED) entry.noted else entry.obj
            merged.merge(obj, quantity, Int::plus)
        }
        return merged.map { (obj, quantity) -> obj to quantity }
    }

    private fun pick(rng: RaidRng): Entry {
        var pick = rng.next(TOTAL_WEIGHT, "standard pick")
        for (entry in ENTRIES) {
            pick -= entry.weight
            if (pick < 0) return entry
        }
        return ENTRIES.last()
    }
}

/**
 * TFA22 / TFA23: the tertiaries and the cabbage. The snapshot holds no player state, so the ownership rules
 * (OPEN-TFA16) cannot be applied here.
 */
internal object TfaTertiaries {
    /** Reward tag of a cabbage-only reward. */
    const val JUNK_TAG = "junk"

    /** TFA23: the only item of a poor run (Wiki Chest, Junk: "Cabbage", quantity 1, always). */
    const val CABBAGE: Int = Objs.CABBAGE

    /** TFA22: archivist's folio 1/15 (Wiki Chest, Tertiary). OPEN-TFA16: "does not drop if one is already owned" is not applied. */
    const val FOLIO_ONE_IN = 15

    /**
     * TFA22: elemental fragments 1/20, then one of the four (Wiki Chest, Tertiary footnote). OPEN-TFA16: the
     * duplicate protection needs the owned set, so the fragment is uniform over all four and never stops dropping.
     */
    const val FRAGMENT_ONE_IN = 20

    /** TFA22: elite clue scroll 1/10 (Wiki Chest, Tertiary). OPEN-TFA9: the 1/9 with the elite Combat Achievement tier is not applied. */
    const val ELITE_CLUE_ONE_IN = 10

    /** TFA22: Orion 1/750 (Wiki Chest, Tertiary; OPEN-TFA10). */
    const val ORION_ONE_IN = 750

    /**
     * OPEN-TFA11: the cabbage chance per death. The Wiki only says "each death increases the chance"; 10% per death
     * mirrors the 10% death penalty of TFA13. Capped at 1.
     */
    const val CABBAGE_CHANCE_PER_DEATH = 0.10

    /** "Clue scroll (elite)" by cache name (config obj 12073; the gameval names that id after an emote clue step). */
    const val ELITE_CLUE_SCROLL = 12073

    /** The four elemental fragments by cache name: living earth, deep water, whispering wind, burning fire. */
    private val FRAGMENTS = intArrayOf(34671, 34673, 34675, 34677)

    /** TFA23: whether this player's reward is the cabbage alone (more deaths, more likely). */
    fun rollCabbage(deaths: Int, rng: RaidRng): Boolean {
        if (deaths <= 0) return false
        return rng.nextDouble("cabbage") < minOf(1.0, deaths * CABBAGE_CHANCE_PER_DEATH)
    }

    /** TFA22: the tertiary drops of a player who did not get the cabbage. */
    fun roll(rng: RaidRng): List<Pair<Int, Int>> {
        val items = ArrayList<Pair<Int, Int>>(2)
        if (rng.next(FOLIO_ONE_IN, "folio") == 0) items += Objs.ARCHIVISTS_FOLIO_EMPTY to 1
        if (rng.next(FRAGMENT_ONE_IN, "fragment") == 0) items += FRAGMENTS[rng.next(FRAGMENTS.size, "fragment which")] to 1
        if (rng.next(ELITE_CLUE_ONE_IN, "elite clue") == 0) items += ELITE_CLUE_SCROLL to 1
        if (rng.next(ORION_ONE_IN, "orion") == 0) items += Objs.OCELLUSPET to 1
        return items
    }
}
