package tp.content.raids.tob

import tp.content.raids.RaidRng
import tp.content.raids.RewardResult
import tp.content.raids.RunSnapshot
import tp.symbols.Objs

/**
 * The Theatre of Blood's reward roll (docs/mechanics/raids/tob/lobby-rewards.md LR16-LR21; OPEN-TB12: the in-game formula
 * is not published, this is the wiki calculator's model). Pure: no world state, every roll goes through the [RaidRng].
 *
 * The flow must put each member's `3 x rooms entered + MVP points` into [tp.content.raids.RaidMember.points] before
 * `Raids.complete` (see [points]); deaths are taken off here from the snapshot, never twice.
 */
internal object TobLoot {
    /** LR16: points per room a player took part in (wiki calculator, "3 x (6 - roomsSkipped)"). */
    const val POINTS_PER_ROOM = 3

    /** LR16: points lost per death. */
    const val POINTS_PER_DEATH = 4

    /** LR16: the team's maximum rooms-only score per player, 6 rooms x 3 points. */
    private const val MAX_ROOM_POINTS = 18

    /** LR16: the 14 MVP points of a raid (also asserted by TobRewards.MVP_POINTS). */
    private val MVP_TOTAL = TobRewards.MVP_POINTS.values.sum()

    /** LR20: three rolls on the common table. */
    const val COMMON_ROLLS = 3

    /** LR20: the common table has 30 weight slots (vial of blood 2, 28 other items 1 each). */
    private const val COMMON_SLOTS = 30

    /** LR20: the upper end of a quantity range is `floor(lo x 1.2)`. */
    private const val RANGE_FACTOR = 1.2

    /** LR20: quantity multiplier q in Entry (TB29: "20%"). */
    private const val Q_ENTRY = 0.20

    /** LR20: q in Normal. */
    private const val Q_NORMAL = 1.0

    /** LR20 / TB29: q in Hard (+15%). */
    private const val Q_HARD = 1.15

    /** LR20 / TB29: q in Hard inside the target time (+15% more). */
    private const val Q_HARD_IN_TIME = 1.30

    /** LR21: elite clue scroll chance by mode, as `n/25` (Entry 1, Normal 3, Hard 3.5 per 25). */
    private const val CLUE_DENOMINATOR = 25.0
    private const val CLUE_ENTRY = 1.0
    private const val CLUE_NORMAL = 3.0
    private const val CLUE_HARD = 3.5

    /** LR21: Lil' Zik at full performance, 1/650 Normal and 1/500 Hard. */
    private const val PET_NORMAL_BEST = 650.0
    private const val PET_HARD_BEST = 500.0

    /** LR21: Lil' Zik at the lowest performance, 1/6,500 Normal and 1/5,000 Hard. */
    private const val PET_NORMAL_WORST = 6_500.0
    private const val PET_HARD_WORST = 5_000.0

    /** LR21: Hard-only tertiaries: Sanguine dust 1/275, Sanguine ornament kit 1/150, Holy ornament kit 1/100. */
    private const val DUST_ONE_IN = 275
    private const val SANGUINE_KIT_ONE_IN = 150
    private const val HOLY_KIT_ONE_IN = 100

    /** Settings key the flow stores to tell whether the raid was finished inside the target time (1 = inside; TB29). */
    const val TARGET_TIME_SETTING = "tob.targetTime"

    /** TB29: target times in game ticks (0.6 s) by party size (1: 1:10:00, 2: 35:00, 3: 30:00, 4: 27:00, 5: 25:00). */
    fun targetTicks(partySize: Int): Int =
        when (partySize) {
            1 -> TARGET_SOLO_TICKS
            2 -> TARGET_DUO_TICKS
            3 -> TARGET_TRIO_TICKS
            4 -> TARGET_FOUR_TICKS
            else -> TARGET_FIVE_TICKS
        }

    private const val TARGET_SOLO_TICKS = 7_000
    private const val TARGET_DUO_TICKS = 3_500
    private const val TARGET_TRIO_TICKS = 3_000
    private const val TARGET_FOUR_TICKS = 2_700
    private const val TARGET_FIVE_TICKS = 2_500

    /**
     * LR16: [name]'s points before deaths: 3 per room fought plus MVP points. The flow adds this to the member with
     * `Raids.addPoints(run, name, TobLoot.points(TobFlow.state(run), name).toDouble())` before `Raids.complete`.
     */
    fun points(state: TobState, name: String): Int {
        val rooms = state.entered.values.count { set -> set.any { it.equals(name, ignoreCase = true) } }
        val mvp =
            state.mvp.entries
                .filter { it.key.equals(name, ignoreCase = true) }
                .sumOf { it.value }
        return POINTS_PER_ROOM * rooms + mvp
    }

    fun roll(snap: RunSnapshot, rng: RaidRng): Map<String, RewardResult> {
        val eligible = snap.members.filterValues { it.third }
        if (eligible.isEmpty()) return emptyMap()
        val inTime = (snap.settings[TARGET_TIME_SETTING] ?: 0) == 1
        val own = eligible.mapValues { (_, m) -> maxOf(0, m.first.toInt() - POINTS_PER_DEATH * m.second) }
        // LR16: the team's raw score (deaths of everybody), the ratio to the maximum.
        val raw = eligible.values.sumOf { it.first.toInt() - POINTS_PER_DEATH * it.second }
        val max = MAX_ROOM_POINTS * eligible.size + MVP_TOTAL
        val team = if (eligible.size == 1) maxOf(own.values.first(), raw, 0) else maxOf(raw, 0)
        val ratio = team.toDouble() / max
        val recipient = uniqueRecipient(snap.mode, own, team, ratio, rng)
        return eligible.mapValues { (name, _) ->
            val points = own.getValue(name)
            when {
                points <= 0 -> {
                    RewardResult(listOf(Objs.CABBAGE to 1, Objs.TOB_VAMPYRE_MESSAGE to 1), tags = setOf(JUNK_TAG))
                }

                name == recipient -> {
                    RewardResult(
                        listOf(uniqueItem(snap.mode, rng) to 1) + tertiaries(snap, points, inTime, rng),
                        unique = true,
                    )
                }

                else -> {
                    RewardResult(common(snap.mode, ratio, inTime, rng) + tertiaries(snap, points, inTime, rng))
                }
            }
        }
    }

    /** Reward tag of a cabbage-and-Message reward (LR17, as TFA's junk reward). */
    const val JUNK_TAG = "junk"

    /** LR17: one roll for the team; player i wins with `P x points_i / T`. Null = no unique. */
    private fun uniqueRecipient(mode: String, points: Map<String, Int>, team: Int, ratio: Double, rng: RaidRng): String? {
        val base = TobRewards.baseUniqueChance(mode)
        if (base <= 0.0 || team <= 0) return null
        val chance = base * ratio
        var pick = rng.nextDouble("unique")
        for ((name, p) in points) {
            val share = chance * p / team
            if (pick < share) return name
            pick -= share
        }
        return null
    }

    /** LR19: the unique by weight. The charged ids of TobRewards are paid as the uncharged items the chest gives. */
    private fun uniqueItem(mode: String, rng: RaidRng): Int {
        val weights = TobRewards.uniqueWeights(mode)
        var pick = rng.next(weights.values.sum(), "unique item")
        for ((obj, weight) in weights) {
            pick -= weight
            if (pick < 0) return uncharged(obj)
        }
        return uncharged(weights.keys.last())
    }

    private fun uncharged(obj: Int): Int =
        when (obj) {
            Objs.SANGUINESTI_STAFF -> Objs.SANGUINESTI_STAFF_UNCHARGED
            Objs.SCYTHE_OF_VITUR -> Objs.SCYTHE_OF_VITUR_UNCHARGED
            else -> obj
        }

    private fun quantityFactor(mode: String, inTime: Boolean): Double =
        when (mode) {
            TobRaid.ENTRY -> Q_ENTRY
            TobRaid.HARD -> if (inTime) Q_HARD_IN_TIME else Q_HARD
            else -> Q_NORMAL
        }

    /** LR20: three rolls on the common table; equal items merge. Zero quantities (Entry, low ratio) drop out (OPEN-LR10). */
    private fun common(mode: String, ratio: Double, inTime: Boolean, rng: RaidRng): List<Pair<Int, Int>> {
        val q = quantityFactor(mode, inTime)
        val merged = LinkedHashMap<Int, Int>()
        repeat(COMMON_ROLLS) {
            val entry = pick(rng)
            // OPEN-LR13: the wiki calculator floors after multiplying q x r.
            val lo = kotlin.math.floor(entry.min * q * ratio).toInt()
            val hi = kotlin.math.floor(lo * RANGE_FACTOR).toInt()
            val quantity = lo + rng.next(hi - lo + 1, "common quantity")
            if (quantity > 0) merged.merge(entry.obj, quantity, Int::plus)
        }
        return merged.map { (obj, quantity) -> obj to quantity }
    }

    private fun pick(rng: RaidRng): Entry {
        var pick = rng.next(COMMON_SLOTS, "common pick")
        for (entry in COMMON) {
            pick -= entry.weight
            if (pick < 0) return entry
        }
        return COMMON.last()
    }

    /**
     * LR21: elite clue (all modes), then Lil' Zik (not in Entry) and the Hard-only items. OPEN-TB13: only the pet scales
     * with performance (the one tertiary with a published range): linear from its lowest rate at 0 points to its best at
     * [TobConfig.petFullRatePoints]. OPEN-TB14: the Hard-only items need the target time when
     * [TobConfig.hardTertiariesNeedTargetTime].
     */
    private fun tertiaries(snap: RunSnapshot, points: Int, inTime: Boolean, rng: RaidRng): List<Pair<Int, Int>> {
        val items = ArrayList<Pair<Int, Int>>(2)
        val clue =
            when (snap.mode) {
                TobRaid.ENTRY -> CLUE_ENTRY
                TobRaid.HARD -> CLUE_HARD
                else -> CLUE_NORMAL
            } / CLUE_DENOMINATOR
        // The cache's "Clue scroll (elite)" is obj 12073 (gameval TRAIL_ELITE_EMOTE_EXP1), as in TFA.
        if (rng.nextDouble("elite clue") < clue) items += Objs.TRAIL_ELITE_EMOTE_EXP1 to 1
        if (snap.mode == TobRaid.ENTRY) return items
        val hard = snap.mode == TobRaid.HARD
        val best = if (hard) PET_HARD_BEST else PET_NORMAL_BEST
        val worst = if (hard) PET_HARD_WORST else PET_NORMAL_WORST
        val fraction = minOf(1.0, points.toDouble() / TobConfig.petFullRatePoints)
        val rate = 1.0 / worst + (1.0 / best - 1.0 / worst) * fraction
        if (rng.nextDouble("lil zik") < rate) items += TobRewards.LIL_ZIK to 1
        if (hard && (inTime || !TobConfig.hardTertiariesNeedTargetTime)) {
            if (rng.next(DUST_ONE_IN, "sanguine dust") == 0) items += Objs.TOB_HARDMODE_DUST to 1
            if (rng.next(SANGUINE_KIT_ONE_IN, "sanguine kit") == 0) items += Objs.TOB_HARDMODE_KIT_BLOOD to 1
            if (rng.next(HOLY_KIT_ONE_IN, "holy kit") == 0) items += Objs.TOB_HARDMODE_KIT to 1
        }
        return items
    }

    /** One common row: [min] is the full-score minimum quantity (LR20); [obj] is already the delivered (noted) form. */
    private class Entry(val obj: Int, val weight: Int, val min: Int)

    /** LR20: the common table (wiki Chest / Calc); non-stackable items are paid as bank notes (the CERT_ symbols). */
    private val COMMON: List<Entry> =
        listOf(
            Entry(Objs.CERT_VIAL_BLOOD, 2, 50),
            Entry(Objs.DEATHRUNE, 1, 500),
            Entry(Objs.BLOODRUNE, 1, 500),
            Entry(Objs.SWAMP_TAR, 1, 500),
            Entry(Objs.CERT_COAL, 1, 500),
            Entry(Objs.CERT_GOLD_ORE, 1, 300),
            Entry(Objs.CERT_MOLTEN_GLASS, 1, 200),
            Entry(Objs.CERT_ADAMANTITE_ORE, 1, 130),
            Entry(Objs.CERT_RUNITE_ORE, 1, 60),
            Entry(Objs.CERT_WINE_OF_ZAMORAK, 1, 50),
            Entry(Objs.CERT_CACTUS_POTATO, 1, 50),
            Entry(Objs.CERT_UNIDENTIFIED_CADANTINE, 1, 50),
            Entry(Objs.CERT_UNIDENTIFIED_AVANTOE, 1, 40),
            Entry(Objs.CERT_UNIDENTIFIED_TOADFLAX, 1, 37),
            Entry(Objs.CERT_UNIDENTIFIED_KWUARM, 1, 36),
            Entry(Objs.CERT_UNIDENTIFIED_IRIT, 1, 34),
            Entry(Objs.CERT_UNIDENTIFIED_RANARR, 1, 30),
            Entry(Objs.CERT_UNIDENTIFIED_SNAPDRAGON, 1, 27),
            Entry(Objs.CERT_UNIDENTIFIED_LANTADYME, 1, 26),
            Entry(Objs.CERT_UNIDENTIFIED_DWARF_WEED, 1, 24),
            Entry(Objs.CERT_UNIDENTIFIED_TORSTOL, 1, 20),
            Entry(Objs.CERT_BATTLESTAFF, 1, 15),
            Entry(Objs.CERT_RUNE_BATTLEAXE, 1, 4),
            Entry(Objs.CERT_RUNE_PLATEBODY, 1, 4),
            Entry(Objs.CERT_RUNE_CHAINBODY, 1, 4),
            Entry(Objs.PALM_TREE_SEED, 1, 3),
            Entry(Objs.YEW_SEED, 1, 3),
            Entry(Objs.MAGIC_TREE_SEED, 1, 3),
            Entry(Objs.MAHOGANY_SEED, 1, 10),
        )

    init {
        check(COMMON.sumOf { it.weight } == COMMON_SLOTS) { "ToB common table weights must sum to $COMMON_SLOTS" }
    }
}
