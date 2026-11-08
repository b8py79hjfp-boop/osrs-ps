package tp.content.raids.toa

import tp.content.raids.RaidRng
import tp.content.raids.RaidRun
import tp.content.raids.RewardResult
import tp.content.raids.RunSnapshot
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The Tombs of Amascut reward roll (docs/mechanics/raids/toa/lobby-rewards.md TR1-TR28). Pure: every roll goes through the
 * run's [RaidRng]; what only a player's state can tell (kill count, owned items, noted ids, the final raid level, the
 * invocation conditions of the remnants) is put into `RaidRun.extraSettings` by [prepare] before `Raids.complete`.
 *
 * Order (TR28): the team's unique roll from the team contribution, its recipient by contribution, the item by weight
 * and the 1/50 gate; per player the fossilised dung or the three commons; the tertiaries; the challenge rewards.
 */
internal object ToaLoot {
    // ---- settings keys written by prepare() and read by roll()

    /** The raid level after the time-limit penalty (TA12): the level every reward formula uses. */
    const val FINAL_LEVEL = "toa.finalLevel"
    private const val REMNANTS = "toa.remnants"
    private const val KC = "toa.kc."
    private const val THREAD = "toa.thread."
    private const val OWN = "toa.own."
    private const val NOTED = "toa.noted."

    /** The saved flag of a player who received the Thread of Elidinis once (TR18: afterwards the chance is flat 1/50). */
    const val THREAD_SAVED = "toa.thread.received"

    // ---- unique roll (TR3-TR10)

    /** TR3: the team's chance is capped at 55%. */
    private const val UNIQUE_CAP = 0.55

    /** TR3: 1% per `10,500 - 20 x RL'` points: the 100 turns the percent into a fraction. */
    private const val UNIQUE_BASE = 10_500
    private const val UNIQUE_PER_LEVEL = 20
    private const val PERCENT_DIVISOR = 100.0

    /** TR3: the Calc module's floor on the denominator (150,000 / 100). */
    private const val MIN_DENOMINATOR = 1_500

    /** TR3: break points of the scaled level (Chest page; integer steps as in Calc and RL-TOA). */
    private const val BREAK_LOW = 310
    private const val BREAK_HIGH = 430
    private const val CLAMP_LEVEL = 550
    private const val OUTER_DIVISOR = 2
    private const val INNER_DIVISOR = 3

    /** TR7: the weights of the five uniques that never change with the level (Ward, Mask, Body, Chaps, Shadow). */
    private const val WEIGHT_WARD = 30
    private const val WEIGHT_ARMOUR_PIECE = 20
    private const val WEIGHT_SHADOW = 10

    /** TR9: the weight of the Fang and the Lightbearer up to raid level 300. */
    private const val WEIGHT_TOP = 70

    /** TR9 anchors where the weights change (25 Jun 2025 / 20 Aug 2025). */
    private const val LEVEL_300 = 300
    private const val LEVEL_350 = 350
    private const val LEVEL_400 = 400
    private const val LEVEL_450 = 450
    private const val LEVEL_500 = 500

    /** TR9: values at the anchors: Fang 60 at 350, 40 at 400 and 450, 30 at 500; Lightbearer 60, 50, 40, 35. */
    private const val FANG_AT_350 = 60
    private const val FANG_AT_450 = 40
    private const val FANG_AT_500 = 30
    private const val LIGHT_AT_350 = 60
    private const val LIGHT_AT_400 = 50
    private const val LIGHT_AT_450 = 40
    private const val LIGHT_AT_500 = 35
    private const val STEP_5 = 5
    private const val STEP_10 = 10

    /** TR10: raid level needed for the Fang and Lightbearer, and for the other five uniques. */
    private const val GATE_TOP = 50
    private const val GATE_OTHER = 150

    /** TR10: below its threshold a drawn unique needs a further 1/50 roll. */
    private const val GATE_ODDS = 50

    /** TR8 (cache): the unique ids, in the weight order of [uniqueWeights]. */
    private val FANG = Objs.OSMUMTENS_FANG
    private val LIGHTBEARER = Objs.LIGHTBEARER

    // ---- fossilised dung (TR6)

    /** TR6: Total (or contribution, by flag) below this gives fossilised dung instead of a reward. */
    private const val DUNG_BELOW = 1_500

    // ---- commons (TR12-TR16)

    /** TR12: three rolls on the common table, 27 equal entries. */
    private const val COMMON_ROLLS = 3
    private const val SCALE_FROM_LEVEL = 300
    private const val SCALE_BASE_PERCENT = 115
    private const val SCALE_STEP_LEVELS = 5
    private const val SCALE_ENTRY_BELOW = 150
    private const val PERCENT = 100

    /** A common drop: the cache obj, TR15's divisor (0 = exactly one, the cache of runes) and whether it is paid as a note. */
    private class Common(val obj: Int, val divisor: Int, val noted: Boolean)

    /** TR15 in the Calc module's order (the Chest page's divisors; ids from the cache). */
    private val COMMONS =
        listOf(
            Common(Objs.COINS, 1, false),
            Common(Objs.DEATHRUNE, 20, false),
            Common(Objs.SOULRUNE, 40, false),
            Common(Objs.GOLD_ORE, 90, true),
            Common(Objs.DRAGON_DART_TIP, 100, false),
            Common(MAHOGANY_LOGS, 180, true),
            Common(Objs.SAPPHIRE, 200, true),
            Common(Objs.EMERALD, 250, true),
            Common(Objs.GOLD_BAR, 250, true),
            Common(Objs.CACTUS_POTATO, 250, true),
            Common(Objs.RAW_SHARK, 250, true),
            Common(Objs.RUBY, 300, true),
            Common(Objs.DIAMOND, 400, true),
            Common(Objs.TOA_RUNE_CACHE, 0, false),
            Common(Objs.CACTUS_SPINE, 600, true),
            Common(Objs.DRAGONSTONE, 600, true),
            Common(Objs.BATTLESTAFF, 1_100, true),
            Common(Objs.VIAL_COCONUT_MILK, 1_100, true),
            Common(Objs.LILY_OF_THE_SANDS, 1_100, true),
            Common(Objs.TOADFLAX_SEED, 2_000, false),
            Common(Objs.RANARR_SEED, 2_500, false),
            Common(Objs.TORSTOL_SEED, 3_200, false),
            Common(Objs.SNAPDRAGON_SEED, 3_200, false),
            Common(Objs.DRAGON_MED_HELM, 4_000, true),
            Common(Objs.MAGIC_TREE_SEED, 6_500, false),
            Common(Objs.BLOOD_ESSENCE_INACTIVE, 7_500, false),
            Common(Objs.RAW_MANTARAY, 450, true),
        )

    /** Cache obj 6332 "Mahogany logs" (the generated symbol of that id is named after the teak logs). */
    private const val MAHOGANY_LOGS = 6332

    // ---- tertiaries (TR17-TR21)

    /** TR17 / TR18: the thread's base chance 1/10, bad luck mitigation reaching 3x at 1.5 x 10 kills; flat 1/50 after the first. */
    private const val THREAD_BASE = 0.1
    private const val THREAD_DENOMINATOR = 10
    private const val THREAD_AFTER_FIRST = 0.02

    /** TR17 / TR18: any of the four jewels 4/50, reaching 3x at 1.5 x 50 kills. */
    private const val JEWEL_BASE = 0.08
    private const val JEWEL_DENOMINATOR = 50

    /** TR18: the chance climbs linearly to this multiple of the base at 1.5 x the base denominator. */
    private const val BML_FULL_MULTIPLE = 3.0
    private const val BML_KILL_FACTOR = 1.5

    /** TR20: 1% per 2,000 points, capped at 25%. */
    private const val CLUE_POINTS_PER_UNIT = 200_000.0
    private const val CLUE_CAP = 0.25

    /** TR21: the pet's denominator `350,000 - 700 x RL''` per 1%. */
    private const val PET_BASE = 350_000
    private const val PET_PER_LEVEL = 700
    private const val PET_BREAK = 400
    private const val PET_TOP = 550
    private const val PET_TOP_SCALED = 450

    // ---- challenge rewards (TR22-TR24)

    private const val KIT_LEVEL = 350
    private const val MENAPHITE_LEVEL = 425
    private const val PHALANX_LEVEL = 500
    private const val REMNANT_LEVEL = 450

    /** TR23: the Kit page's "at least 1500 points" for the Masori crafting kit (OPEN-TR15: this kit only). */
    private const val KIT_MIN_CONTRIBUTION = 1_500

    /** TR23: path level a boss must have been beaten at for its remnant. */
    private const val REMNANT_PATH_LEVEL = 4

    // ---- ownership bits (settings OWN)

    private const val OWN_MASORI_KIT = 1 shl 0
    private const val OWN_MENAPHITE = 1 shl 1
    private const val OWN_PHALANX = 1 shl 2
    private const val OWN_JEWELS_SHIFT = 3
    private const val OWN_REMNANTS_SHIFT = 7

    private val JEWELS = intArrayOf(Objs.BREACH_OF_THE_SCARAB, Objs.EYE_OF_THE_CORRUPTOR, Objs.JEWEL_OF_THE_SUN, Objs.JEWEL_OF_AMASCUT)

    /** TR22: the Keris partisan forms count as owning the jewel they carry (same order as [JEWELS]). */
    private val PARTISANS =
        intArrayOf(Objs.KERIS_PARTISAN_BREACH, Objs.KERIS_PARTISAN_CORRUPTION, Objs.KERIS_PARTISAN_SUN, Objs.KERIS_PARTISAN_AMASCUT)

    /** TR24: the transmog remnants in priority order (Akkha, Zebak, Ba-Ba, Kephri, Ancient) with the path each needs. */
    private class Remnant(val obj: Int, val path: ToaPath?, val category: Int, val unlocks: IntArray)

    private val REMNANTS_BY_PRIORITY =
        listOf(
            Remnant(Objs.TOA_PET_MORPH_AKKHA, ToaPath.HET, ToaInvocations.CAT_AKKHA, intArrayOf(Varbits.TOA_UNLOCKED_PET_MORPH_AKKHA)),
            Remnant(Objs.TOA_PET_MORPH_ZEBAK, ToaPath.CRONDIS, ToaInvocations.CAT_ZEBAK, intArrayOf(Varbits.TOA_UNLOCKED_PET_MORPH_ZEBAK)),
            Remnant(Objs.TOA_PET_MORPH_BABA, ToaPath.APMEKEN, ToaInvocations.CAT_BABA, intArrayOf(Varbits.TOA_UNLOCKED_PET_MORPH_BABA)),
            Remnant(
                Objs.TOA_PET_MORPH_KEPHRI,
                ToaPath.SCABARAS,
                ToaInvocations.CAT_KEPHRI,
                intArrayOf(Varbits.TOA_UNLOCKED_PET_MORPH_KEPHRI),
            ),
            Remnant(
                Objs.TOA_PET_MORPH_WARDENS,
                null,
                ToaInvocations.CAT_WARDENS,
                intArrayOf(Varbits.TOA_UNLOCKED_PET_MORPH_DESTROYED_TUMEKEN, Varbits.TOA_UNLOCKED_PET_MORPH_DESTROYED_ELIDINIS),
            ),
        )

    // ---- the maths (also asserted by RaidRewardMathTest)

    /** TR3: the scaled level `RL'` of the unique chance, with integer divisions in two steps (Calc / RL-TOA). */
    fun scaledUniqueLevel(raidLevel: Int): Int {
        var x = if (ToaConfig.uniqueLevelClamp550) minOf(raidLevel, CLAMP_LEVEL) else raidLevel
        if (x > BREAK_LOW) {
            if (x > BREAK_HIGH) x = BREAK_HIGH + (x - BREAK_HIGH) / OUTER_DIVISOR
            x = BREAK_LOW + (x - BREAK_LOW) / INNER_DIVISOR
        }
        return x
    }

    /** TR3: the points per 1% of the unique chance. */
    fun uniqueDenominator(raidLevel: Int): Int = maxOf(MIN_DENOMINATOR, UNIQUE_BASE - UNIQUE_PER_LEVEL * scaledUniqueLevel(raidLevel))

    /** TR3 / TR5: the team's one-roll chance from the team contribution [team], capped at 55%. */
    fun uniqueChance(team: Long, raidLevel: Int): Double =
        if (team <= 0) 0.0 else minOf(UNIQUE_CAP, team / (PERCENT_DIVISOR * uniqueDenominator(raidLevel)))

    private fun step(numerator: Int, divisor: Int): Int =
        if (ToaConfig.purpleReweightRounding == "nearest") (numerator + divisor / 2) / divisor else numerator / divisor

    /** TR9: the Osmumten's fang weight by raid level. */
    fun fangWeight(rl: Int): Int =
        when {
            rl <= LEVEL_300 -> WEIGHT_TOP
            rl <= LEVEL_350 -> WEIGHT_TOP - step(rl - LEVEL_300, STEP_5)
            rl <= LEVEL_400 -> FANG_AT_350 - step(2 * (rl - LEVEL_350), STEP_5)
            rl <= LEVEL_450 -> FANG_AT_450
            rl <= LEVEL_500 -> FANG_AT_450 - step(rl - LEVEL_450, STEP_5)
            else -> FANG_AT_500
        }

    /** TR9: the Lightbearer weight by raid level. */
    fun lightbearerWeight(rl: Int): Int =
        when {
            rl <= LEVEL_300 -> WEIGHT_TOP
            rl <= LEVEL_350 -> WEIGHT_TOP - step(rl - LEVEL_300, STEP_5)
            rl <= LEVEL_400 -> LIGHT_AT_350 - step(rl - LEVEL_350, STEP_5)
            rl <= LEVEL_450 -> LIGHT_AT_400 - step(rl - LEVEL_400, STEP_5)
            rl <= LEVEL_500 -> LIGHT_AT_450 - step(rl - LEVEL_450, STEP_10)
            else -> LIGHT_AT_500
        }

    /** TR7 / TR9: the seven uniques with their weights at [rl]. */
    fun uniqueWeights(rl: Int): List<Pair<Int, Int>> =
        listOf(
            FANG to fangWeight(rl),
            LIGHTBEARER to lightbearerWeight(rl),
            Objs.ELIDINIS_WARD to WEIGHT_WARD,
            Objs.MASORI_MASK to WEIGHT_ARMOUR_PIECE,
            Objs.MASORI_BODY to WEIGHT_ARMOUR_PIECE,
            Objs.MASORI_CHAPS to WEIGHT_ARMOUR_PIECE,
            Objs.TUMEKENS_SHADOW_UNCHARGED to WEIGHT_SHADOW,
        )

    /** TR10: the raid level a unique needs before it drops without the extra 1/50 roll. */
    fun uniqueGate(obj: Int): Int = if (obj == FANG || obj == LIGHTBEARER) GATE_TOP else GATE_OTHER

    /** TR13 / TR14: the percent the common quantity is scaled by at [rl]. */
    fun commonScalePercent(rl: Int): Int =
        when {
            rl >= SCALE_FROM_LEVEL -> SCALE_BASE_PERCENT + (rl - SCALE_FROM_LEVEL) / SCALE_STEP_LEVELS
            rl < SCALE_ENTRY_BELOW -> ToaConfig.entryCommonScalePercent
            else -> PERCENT
        }

    /** TR13: `max(1, floor(floor(C / divisor) x scale))` (OPEN-TR10: the Calc module's truncation order). */
    fun commonQuantity(contribution: Int, divisor: Int, rl: Int): Int =
        if (divisor <= 0) 1 else maxOf(1, contribution / divisor * commonScalePercent(rl) / PERCENT)

    /** TR21: the pet's `RL''`. */
    fun petLevel(rl: Int): Int =
        when {
            rl <= PET_BREAK -> rl
            rl <= PET_TOP -> PET_BREAK + (rl - PET_BREAK) / INNER_DIVISOR
            else -> PET_TOP_SCALED
        }

    /** TR21: points per 1% of the pet chance. */
    fun petDenominator(rl: Int): Int = PET_BASE - PET_PER_LEVEL * petLevel(rl)

    /** TR21: the pet's chance for [points] (the player's contribution, or the team average by flag). */
    fun petChance(points: Int, rl: Int): Double = points / (PERCENT_DIVISOR * petDenominator(rl))

    /** TR20: the elite clue's chance. */
    fun clueChance(contribution: Int): Double =
        minOf(CLUE_CAP, contribution / CLUE_POINTS_PER_UNIT) * ToaConfig.eliteClueMultiplierPercent / PERCENT

    /** TR18: the chance rises linearly from [base] to 3 x [base] at 1.5 x [denominator] kills. */
    private fun bml(base: Double, denominator: Int, kills: Int): Double {
        val full = BML_KILL_FACTOR * denominator
        return base * (1.0 + (BML_FULL_MULTIPLE - 1.0) * minOf(kills.toDouble(), full) / full)
    }

    /** TR18: the Thread of Elidinis. */
    fun threadChance(
        kills: Int,
        received: Boolean,
    ): Double = if (received) THREAD_AFTER_FIRST else bml(THREAD_BASE, THREAD_DENOMINATOR, kills)

    /** TR18: any Keris partisan jewel. */
    fun jewelChance(kills: Int): Double = bml(JEWEL_BASE, JEWEL_DENOMINATOR, kills)

    /** TR6 (OPEN-TR6): the dung rule; reading A tests Total, reading B (flag) the contribution. */
    fun dungApplies(total: Double, contribution: Int): Boolean =
        if (ToaConfig.dungThresholdOnContribution) contribution < DUNG_BELOW else total < DUNG_BELOW

    // ---- the roll

    private class Builder {
        val items = LinkedHashMap<Int, Int>()
        val tags = LinkedHashSet<String>()
        var unique = false

        fun give(obj: Int, count: Int = 1) {
            items.merge(obj, count, Int::plus)
        }
    }

    fun roll(snap: RunSnapshot, rng: RaidRng): Map<String, RewardResult> {
        val rl = snap.settings[FINAL_LEVEL] ?: 0
        val names = snap.members.keys.toList()
        val contribution = IntArray(names.size) { ToaPoints.contribution(snap.members.getValue(names[it]).first) }
        val dung = BooleanArray(names.size) { dungApplies(snap.members.getValue(names[it]).first, contribution[it]) }
        // TR1: leavers' points are in the team contribution.
        val team = contribution.sumOf { it.toLong() }
        val builders = Array(names.size) { Builder() }

        val winner = rollUnique(snap, rng, rl, team, names, contribution, dung, builders)

        val out = LinkedHashMap<String, RewardResult>()
        for ((i, name) in names.withIndex()) {
            if (!snap.members.getValue(name).third) continue
            val b = builders[i]
            val lower = name.lowercase()
            if (dung[i]) {
                b.give(Objs.TOA_LOOT_POO)
                b.tags += TAG_DUNG
            } else {
                if (i != winner) commons(snap, rng, rl, contribution[i], b)
                tertiaries(snap, rng, rl, lower, contribution[i], team / names.size, b)
                challenge(snap, rl, lower, contribution[i], b)
            }
            out[name] = RewardResult(b.items.map { it.key to it.value }, b.unique, b.tags)
        }
        return out
    }

    /** TR3-TR11: returns the winner's index (-1 for none) and fills the winner's builder. */
    private fun rollUnique(
        snap: RunSnapshot,
        rng: RaidRng,
        rl: Int,
        team: Long,
        names: List<String>,
        contribution: IntArray,
        dung: BooleanArray,
        builders: Array<Builder>,
    ): Int {
        val chance = uniqueChance(team, rl)
        if (chance <= 0.0 || rng.nextDouble("toa unique") >= chance) return -1
        // TR5: by contribution; a leaver is drawn only with the flag (and then loses the item with the run).
        val weights =
            IntArray(names.size) { i ->
                if (dung[i] || (!ToaConfig.leaverCanWinUnique && !snap.members.getValue(names[i]).third)) 0 else contribution[i]
            }
        val sum = weights.sumOf { it.toLong() }
        if (sum <= 0) return -1
        var pick = rng.next(sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), "toa unique recipient")
        var winner = -1
        for (i in names.indices) {
            if (pick < weights[i]) {
                winner = i
                break
            }
            pick -= weights[i]
        }
        if (winner < 0) return -1
        val b = builders[winner]
        b.tags += TAG_PURPLE
        val table = uniqueWeights(snap.settings[FINAL_LEVEL] ?: 0)
        var roll = rng.next(table.sumOf { it.second }, "toa unique item")
        var item = table.last().first
        for ((obj, weight) in table) {
            if (roll < weight) {
                item = obj
                break
            }
            roll -= weight
        }
        // TR10: below its threshold the drawn unique needs one more 1/50 roll, else an untradeable tertiary comes instead.
        if (rl < uniqueGate(item) && rng.next(GATE_ODDS, "toa unique gate") != 0) {
            fallback(snap, rng, names[winner].lowercase(), b)
        } else {
            b.give(item)
            b.unique = true
            b.tags += TAG_UNIQUE
        }
        return winner
    }

    /** TR19 (OPEN-TR13): the tertiary a failed gated unique turns into. */
    private fun fallback(snap: RunSnapshot, rng: RaidRng, lower: String, b: Builder) {
        val candidates = ArrayList<Int>(JEWELS.size + 1)
        candidates += Objs.THREAD_OF_ELIDINIS
        when (ToaConfig.gatedFailFallback) {
            "none" -> return
            "thread" -> Unit
            else -> unowned(snap, lower).forEach { candidates += it }
        }
        val pick = candidates[rng.next(candidates.size, "toa gated fallback")]
        b.give(pick)
        b.tags += if (pick == Objs.THREAD_OF_ELIDINIS) TAG_THREAD else TAG_JEWEL
    }

    /** The jewels [lower] does not own; all four when they own every one (TR19: duplicates are then allowed). */
    private fun unowned(snap: RunSnapshot, lower: String): List<Int> {
        val own = snap.settings[OWN + lower] ?: 0
        val missing = JEWELS.indices.filter { own and (1 shl (OWN_JEWELS_SHIFT + it)) == 0 }.map { JEWELS[it] }
        return missing.ifEmpty { JEWELS.toList() }
    }

    /** TR12 / TR13 / TR15: three independent rolls on the 27-entry table. */
    private fun commons(snap: RunSnapshot, rng: RaidRng, rl: Int, contribution: Int, b: Builder) {
        repeat(COMMON_ROLLS) {
            val c = COMMONS[rng.next(COMMONS.size, "toa common")]
            val quantity = commonQuantity(contribution, c.divisor, rl)
            val obj = if (c.noted) snap.settings[NOTED + c.obj]?.takeIf { it > 0 } ?: c.obj else c.obj
            b.give(obj, quantity)
        }
    }

    /** TR17-TR21: thread, jewel, elite clue and pet, each rolled on its own (a unique does not stop them). */
    private fun tertiaries(
        snap: RunSnapshot,
        rng: RaidRng,
        rl: Int,
        lower: String,
        contribution: Int,
        average: Long,
        b: Builder,
    ) {
        val kills = snap.settings[KC + lower] ?: 0
        val received = (snap.settings[THREAD + lower] ?: 0) == 1
        if (rng.nextDouble("toa thread") < threadChance(kills, received)) {
            b.give(Objs.THREAD_OF_ELIDINIS)
            b.tags += TAG_THREAD
        }
        if (rng.nextDouble("toa jewel") < jewelChance(kills)) {
            val pool = unowned(snap, lower)
            b.give(pool[rng.next(pool.size, "toa jewel pick")])
            b.tags += TAG_JEWEL
        }
        if (rng.nextDouble("toa elite clue") < clueChance(contribution)) {
            b.give(Objs.TRAIL_ELITE_EMOTE_EXP1)
            b.tags += TAG_CLUE
        }
        val petPoints = if (ToaConfig.petUsesPersonalPoints) contribution else average.toInt()
        if (rng.nextDouble("toa pet") < petChance(petPoints, rl)) {
            b.give(Objs.WARDENPET_TUMEKEN)
            b.tags += TAG_PET
        }
    }

    /** TR22-TR24: always given when met; nobody on the team may have died. No RNG. */
    private fun challenge(snap: RunSnapshot, rl: Int, lower: String, contribution: Int, b: Builder) {
        if (snap.teamDeaths > 0) return
        val own = snap.settings[OWN + lower] ?: 0
        if (rl >= KIT_LEVEL && contribution >= KIT_MIN_CONTRIBUTION && own and OWN_MASORI_KIT == 0) {
            b.give(Objs.AVAS_ASSEMBLER_ORNAMENT_KIT)
            b.tags += TAG_KIT
        }
        if (rl >= MENAPHITE_LEVEL && own and OWN_MENAPHITE == 0) {
            b.give(Objs.ELIDINIS_WARD_ORNAMENT_KIT)
            b.tags += TAG_KIT
        }
        if (rl >= PHALANX_LEVEL && own and OWN_PHALANX == 0) {
            b.give(Objs.OSMUMTENS_FANG_ORNAMENT_KIT)
            b.tags += TAG_KIT
        }
        // TR24: one transmog per person per raid, by priority.
        val eligible = snap.settings[REMNANTS] ?: 0
        if (rl < REMNANT_LEVEL) return
        for ((k, r) in REMNANTS_BY_PRIORITY.withIndex()) {
            if (eligible and (1 shl k) == 0 || own and (1 shl (OWN_REMNANTS_SHIFT + k)) != 0) continue
            b.give(r.obj)
            b.tags += TAG_REMNANT
            break
        }
    }

    // ---- what only the world knows (called once before Raids.complete)

    /**
     * Fixes the final raid level (the time limit, TA12), the remnants the invocations and path levels allow (TR23), each
     * present player's kill count, thread flag and owned items, and the noted ids of the commons, as run settings.
     */
    fun prepare(run: RaidRun, any: EntityRef, st: ToaState) {
        val players = run.presentPlayers(any)
        val first = players.firstOrNull()
        val level = ToaRecords.finalLevel(st, first)
        st.raidLevel = level
        val extra = run.extraSettings
        extra[FINAL_LEVEL] = level
        extra[REMNANTS] = if (first == null) 0 else remnantMask(first, st)
        if (first != null) for (c in COMMONS) if (c.noted) extra[NOTED + c.obj] = first.noted(c.obj)
        for (p in players) {
            val lower = (run.member(p.username)?.name ?: p.username).lowercase()
            extra[KC + lower] = completions(p)
            extra[THREAD + lower] = if (p.saved(THREAD_SAVED) == 1L) 1 else 0
            extra[OWN + lower] = ownedMask(p)
        }
    }

    /** TR18 (OPEN-TR12): completions before this raid, Entry included by flag. */
    private fun completions(p: PlayerRef): Int =
        p.varp(Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT) +
            p.varp(Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT) +
            if (ToaConfig.bmlCountsEntry) p.varp(Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY) else 0

    /** TR23: the remnants whose invocations are all active and, for a boss, whose path ended at level 4 or more. */
    private fun remnantMask(p: PlayerRef, st: ToaState): Int {
        val all = ToaInvocations.all(p)
        var mask = 0
        for ((k, r) in REMNANTS_BY_PRIORITY.withIndex()) {
            val invocations = all.filter { it.category == r.category }
            val active = invocations.isNotEmpty() && invocations.all { ToaInvocations.isActive(st.words, it) }
            val pathOk = r.path == null || st.pathLevels[r.path.ordinal] >= REMNANT_PATH_LEVEL
            if (active && pathOk) mask = mask or (1 shl k)
        }
        return mask
    }

    /** TR22: inventory, worn equipment and bank are checked for the item or its modified form. */
    private fun ownedMask(p: PlayerRef): Int {
        var mask = 0
        if (owns(
                p,
                Objs.AVAS_ASSEMBLER_ORNAMENT_KIT,
                Objs.AVAS_ASSEMBLER_MASORI,
                Objs.AVAS_ASSEMBLER_MASORI_TROUVER,
                Objs.AVAS_ASSEMBLER_MASORI_BROKEN,
            )
        ) {
            mask = mask or OWN_MASORI_KIT
        }
        if (owns(p, Objs.ELIDINIS_WARD_ORNAMENT_KIT, Objs.ELIDINIS_WARD_ORNAMENT)) mask = mask or OWN_MENAPHITE
        if (owns(p, Objs.OSMUMTENS_FANG_ORNAMENT_KIT, Objs.OSMUMTENS_FANG_ORNAMENT)) mask = mask or OWN_PHALANX
        for (k in JEWELS.indices) if (owns(p, JEWELS[k], PARTISANS[k])) mask = mask or (1 shl (OWN_JEWELS_SHIFT + k))
        for ((k, r) in REMNANTS_BY_PRIORITY.withIndex()) {
            if (owns(p, r.obj) || r.unlocks.any { p.varbit(it) != 0 }) mask = mask or (1 shl (OWN_REMNANTS_SHIFT + k))
        }
        return mask
    }

    private fun owns(p: PlayerRef, vararg objs: Int): Boolean =
        objs.any { p.inv(Invs.INV).total(it) > 0 || p.inv(Invs.WORN).total(it) > 0 || p.inv(Invs.BANK).total(it) > 0 }

    /** Reward tags: the chest colour, the broadcast and the collection-log hooks read these. */
    const val TAG_UNIQUE = "unique"
    const val TAG_PURPLE = "purple"
    const val TAG_DUNG = "dung"
    const val TAG_THREAD = "thread"
    const val TAG_JEWEL = "jewel"
    const val TAG_CLUE = "clue"
    const val TAG_PET = "pet"
    const val TAG_KIT = "kit"
    const val TAG_REMNANT = "remnant"
}
