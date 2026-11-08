package tp.content.raids.cox

import tp.content.raids.RaidRng
import tp.content.raids.RewardResult
import tp.content.raids.RunSnapshot
import tp.symbols.Objs

/**
 * The Chambers' reward chest (docs/mechanics/raids/cox/lobby-rewards.md CR1-CR13): the team's points give up to six
 * unique rolls (65.7% each for every 570,000, the rest for the last, at 8,675 points per percent: OPEN-CR2), each won by
 * a member drawn by personal points (a leaver's is discarded); a unique comes from the weighted table (Normal /60, CM
 * /56) with a 1/53 Olmlet roll; everyone else gets two different common drops (quantity = min(points, 131,071) / divisor),
 * the Dark journal, the ancient tablet at 1/10 and the elite clue at 1/12; Challenge Mode inside the target time adds the
 * colour kit (1/75) and the metamorphic dust (1/400).
 */
internal object CoxLoot {
    private class Unique(val obj: Int, val normal: Int, val cm: Int)

    /** CR4 (12 Aug 2026). */
    private val UNIQUES =
        listOf(
            Unique(Objs.RAIDS_PRAYERSCROLL, 14, 12),
            Unique(Objs.RAIDS_PRAYERSCROLL_AUGURY, 14, 12),
            Unique(Objs.TWISTED_BUCKLER, 4, 4),
            Unique(Objs.DRAGONHUNTER_XBOW, 4, 4),
            Unique(Objs.DINHS_BULWARK, 3, 3),
            Unique(Objs.ANCESTRAL_HAT, 4, 4),
            Unique(Objs.ANCESTRAL_ROBE_TOP, 4, 4),
            Unique(Objs.ANCESTRAL_ROBE_BOTTOM, 4, 4),
            Unique(Objs.DRAGON_CLAWS, 3, 3),
            Unique(Objs.ELDER_MAUL, 2, 2),
            Unique(Objs.KODAI_INSIGNIA, 2, 2),
            Unique(Objs.TWISTED_BOW, 2, 2),
        )

    /** A common drop: obj, divisor (CR9; 0 = exactly one), weight out of 99 (Calc), and the herb it is the seed of. */
    private class Common(val obj: Int, val divisor: Int, val weight: Int, val group: Int)

    /** CR9 table (runes and arrows, herbs and their seeds, ores and gems, the others). */
    private val COMMONS: List<Common> =
        buildList {
            fun add(obj: Int, divisor: Int, weight: Int = 3, group: Int = obj) = add(Common(obj, divisor, weight, group))
            add(Objs.DEATHRUNE, 36)
            add(Objs.BLOODRUNE, 32)
            add(Objs.SOULRUNE, 20)
            add(Objs.RUNE_ARROW, 14)
            add(Objs.DRAGON_ARROW, 202)
            for (h in HERBS) {
                add(h.herb, h.divisor, 2, h.herb)
                add(h.seed, h.divisor * h.perSeed, 1, h.herb)
            }
            add(Objs.CERT_SILVER_ORE, 20)
            add(Objs.CERT_COAL, 20)
            add(Objs.CERT_GOLD_ORE, 44)
            add(Objs.CERT_MITHRIL_ORE, 32)
            add(Objs.CERT_ADAMANTITE_ORE, 166)
            add(Objs.CERT_RUNITE_ORE, 2000)
            add(Objs.CERT_UNCUT_SAPPHIRE, 188)
            add(Objs.CERT_UNCUT_EMERALD, 142)
            add(Objs.CERT_UNCUT_RUBY, 242)
            add(Objs.CERT_UNCUT_DIAMOND, 508)
            add(Objs.LIZARDMAN_FANG, 28)
            add(Objs.CERT_BLANKRUNE_HIGH, 2)
            add(CERT_SALTPETRE, 28)
            add(Objs.CERT_PLANK_TEAK, 96)
            add(Objs.CERT_PLANK_MAHOGANY, 238)
            add(CERT_DYNAMITE, 54)
            add(TORN_PRAYER_SCROLL, 0)
            add(DARK_RELIC, 0)
        }

    /** Herb (noted), seed, divisor, herbs per seed (CR8: 7 for ranarr, snapdragon, torstol; 8 for the rest). */
    private data class Herb(val herb: Int, val seed: Int, val divisor: Int, val perSeed: Int)

    private val HERBS
        get() =
            listOf(
                Herb(Objs.CERT_UNIDENTIFIED_RANARR, Objs.RANARR_SEED, 946, SEVEN),
                Herb(Objs.CERT_UNIDENTIFIED_TOADFLAX, Objs.TOADFLAX_SEED, 624, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_IRIT, Objs.IRIT_SEED, 194, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_AVANTOE, Objs.AVANTOE_SEED, 389, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_KWUARM, Objs.KWUARM_SEED, 454, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_SNAPDRAGON, Objs.SNAPDRAGON_SEED, 1560, SEVEN),
                Herb(Objs.CERT_UNIDENTIFIED_CADANTINE, Objs.CADANTINE_SEED, 396, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_LANTADYME, Objs.LANTADYME_SEED, 297, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_DWARF_WEED, Objs.DWARF_WEED_SEED, 240, EIGHT),
                Herb(Objs.CERT_UNIDENTIFIED_TORSTOL, Objs.TORSTOL_SEED, 972, SEVEN),
            )

    fun roll(snap: RunSnapshot, rng: RaidRng): Map<String, RewardResult> {
        val cm = snap.mode == CoxRaid.CHALLENGE
        val team =
            (
                snap.settings[CoxOlmRoom.TEAM_POINTS] ?: snap.members.values
                    .sumOf { it.first }
                    .toInt()
            ).toLong()
        val items = HashMap<String, MutableList<Pair<Int, Int>>>()
        val uniques = HashSet<String>()
        val tags = HashMap<String, MutableSet<String>>()
        for (chance in uniqueChances(team)) {
            if (rng.nextDouble("cox unique roll") >= chance) continue
            val winner = recipient(snap, rng) ?: continue
            // CR3: a leaver can be drawn, but the reward is discarded.
            if (snap.members[winner]?.third != true) continue
            val obj = drawUnique(cm, rng)
            items.getOrPut(winner) { ArrayList() } += obj to 1
            uniques += winner
            tags.getOrPut(winner) { HashSet() } += TAG_UNIQUE
            // CR6: 1/53 for the Olmlet with each unique received (OPEN-CR5).
            if (rng.next(OLMLET_ODDS, "cox olmlet") == 0) items.getValue(winner) += Objs.OLMPET to 1
        }
        val inTime = cm && snap.settings[CoxOlmRoom.IN_TIME] == 1
        val out = LinkedHashMap<String, RewardResult>()
        for ((name, m) in snap.members) {
            if (!m.third) continue
            val list = items.getOrPut(name) { ArrayList() }
            if (name !in uniques) {
                commons(m.first, rng, list)
                list += DARK_JOURNAL to 1
                // CR7 / OPEN-CR6: the ancient tablet at 1/10 on the common path; CR10 / OPEN-CR8: the elite clue 1/12.
                if (rng.next(TABLET_ODDS, "cox tablet") == 0) list += ANCIENT_TABLET to 1
                if (rng.next(CLUE_ODDS, "cox elite clue") == 0) list += ELITE_CLUE to 1
            }
            if (inTime) {
                // CR11: per player, beside a unique or the commons.
                if (rng.next(KIT_ODDS, "cox kit") == 0) {
                    list += COLOUR_KIT to 1
                    tags.getOrPut(name) { HashSet() } += TAG_KIT
                }
                if (rng.next(DUST_ODDS, "cox dust") == 0) {
                    list += METAMORPHIC_DUST to 1
                    tags.getOrPut(name) { HashSet() } += TAG_DUST
                }
            }
            out[name] = RewardResult(list, unique = name in uniques, tags = tags[name].orEmpty())
        }
        return out
    }

    /** CR2: min(6, ceil(T / 570,000)) rolls: 65.7% each, the last (T - 570,000 (n - 1)) / D percent. */
    fun uniqueChances(team: Long): List<Double> {
        val t = team.coerceIn(0, MAX_TEAM_POINTS)
        if (t <= 0) return emptyList()
        val n = ((t + PER_ROLL - 1) / PER_ROLL).toInt().coerceAtMost(MAX_ROLLS)
        return List(n) { k ->
            val points = if (k < n - 1) PER_ROLL else t - PER_ROLL * (n - 1)
            points.toDouble() / POINTS_PER_PERCENT / PERCENT
        }
    }

    /** CR3: weighted by personal points (a zero-point member can still win when everyone has zero: OPEN-CR3). */
    private fun recipient(snap: RunSnapshot, rng: RaidRng): String? {
        val pool = snap.members.entries.toList()
        if (pool.isEmpty()) return null
        val total = pool.sumOf { maxOf(0.0, it.value.first) }
        if (total <= 0) return pool[rng.next(pool.size, "cox unique recipient")].key
        var r = rng.nextDouble("cox unique recipient") * total
        for (e in pool) {
            r -= maxOf(0.0, e.value.first)
            if (r < 0) return e.key
        }
        return pool.last().key
    }

    private fun drawUnique(cm: Boolean, rng: RaidRng): Int {
        val total = UNIQUES.sumOf { if (cm) it.cm else it.normal }
        var r = rng.next(total, "cox unique item")
        for (u in UNIQUES) {
            r -= if (cm) u.cm else u.normal
            if (r < 0) return u.obj
        }
        return UNIQUES.last().obj
    }

    /** CR7-CR9: two different drops by weight; a herb and its seed count as the same drop (OPEN-CR13). */
    private fun commons(points: Double, rng: RaidRng, out: MutableList<Pair<Int, Int>>) {
        val capped = minOf(points.toLong(), CoxPoints.COMMON_CAP.toLong())
        var taken = -1
        repeat(COMMON_ROLLS) {
            val pool = COMMONS.filter { it.group != taken }
            var r = rng.next(pool.sumOf { it.weight }, "cox common")
            val pick = pool.first { c -> (r - c.weight).also { r = it } < 0 }
            taken = pick.group
            // OPEN-CR7: the quantity is the calculator's floor(points / divisor), at least 1.
            val qty = if (pick.divisor == 0) 1 else maxOf(1, (capped / pick.divisor).toInt())
            out += pick.obj to qty
        }
    }

    /** CR2: 570,000 points per full roll, six rolls at most, 8,675 points per percent (OPEN-CR2). */
    private const val PER_ROLL = 570_000L
    private const val MAX_ROLLS = 6
    private const val MAX_TEAM_POINTS = PER_ROLL * MAX_ROLLS
    private const val POINTS_PER_PERCENT = 8675.0
    private const val PERCENT = 100.0
    private const val OLMLET_ODDS = 53
    private const val COMMON_ROLLS = 2
    private const val TABLET_ODDS = 10
    private const val CLUE_ODDS = 12
    private const val KIT_ODDS = 75
    private const val DUST_ODDS = 400
    private const val SEVEN = 7
    private const val EIGHT = 8

    /** Objs without a generated symbol name used here (cache 2757 `config obj`). */
    private const val CERT_SALTPETRE = 13422
    private const val CERT_DYNAMITE = 13574
    private const val TORN_PRAYER_SCROLL = 21047
    private const val DARK_RELIC = 21027
    private const val ANCIENT_TABLET = 21046
    private const val ELITE_CLUE = 12073
    private const val COLOUR_KIT = 24670
    private const val METAMORPHIC_DUST = 22386

    /** The Dark journal is the raid's reward book (`RAIDS_REWARD_BOOK`). */
    private const val DARK_JOURNAL = Objs.RAIDS_REWARD_BOOK

    /** CR12: the chest light (purple unique, green kit, turquoise dust). */
    const val TAG_UNIQUE = "unique"
    const val TAG_KIT = "kit"
    const val TAG_DUST = "dust"
}
