@file:Suppress("MagicNumber") // data tables: levels, xp and 1/256 rates from the OSRS Wiki pages named in docs/mechanics/fishing.md

package tp.content.skills

import tp.symbols.Objs
import tp.symbols.Seqs

/** Success rates out of 256 at level 1 and 99, one pair per tool tier (harpoons); a shorter list repeats its last pair. */
internal class Rates(private val low: IntArray, private val high: IntArray) {
    fun low(tier: Int): Int = low[tier.coerceAtMost(low.size - 1)]

    fun high(tier: Int): Int = high[tier.coerceAtMost(high.size - 1)]
}

/**
 * A fish: [level], [xp], [rates], the [message] on a catch, and the rare oversized variant that replaces it with chance
 * 1 in [bigOneIn] (0 = none): [bigObj] and its [bigMessage] (Wiki "Big bass", "Big swordfish", "Big shark").
 */
internal class Fish(
    val obj: Int,
    val level: Int,
    val xp: Double,
    val rates: Rates,
    val message: String,
    val bigObj: Int = -1,
    val bigOneIn: Int = 0,
    val bigMessage: String = "",
)

private fun fish(obj: Int, level: Int, xp: Double, low: Int, high: Int, message: String): Fish =
    Fish(obj, level, xp, Rates(intArrayOf(low), intArrayOf(high)), message)

/** Tuna, swordfish and shark rates per harpoon tier (0 harpoon, 1 dragon, 2 crystal), Wiki "Raw tuna" / "Raw shark". */
private fun tiered(low: IntArray, high: IntArray) = Rates(low, high)

private val SHRIMPS = fish(Objs.RAW_SHRIMP, 1, 10.0, 48, 256, "You catch some raw shrimps.")
private val ANCHOVIES = fish(Objs.RAW_ANCHOVIES, 15, 40.0, 24, 128, "You catch some raw anchovies.")
private val SARDINE = fish(Objs.RAW_SARDINE, 5, 20.0, 32, 192, "You catch a raw sardine.")
private val HERRING = fish(Objs.RAW_HERRING, 10, 30.0, 24, 128, "You catch a raw herring.")
private val TROUT = fish(Objs.RAW_TROUT, 20, 50.0, 32, 192, "You catch a raw trout.")
private val SALMON = fish(Objs.RAW_SALMON, 30, 70.0, 16, 96, "You catch a raw salmon.")
private val RAINBOW_FISH = fish(Objs.HUNTING_RAW_FISH_SPECIAL, 38, 80.0, 8, 64, "You catch a raw rainbow fish.")
private val PIKE = fish(Objs.RAW_PIKE, 25, 60.0, 16, 96, "You catch a raw pike.")
private val LOBSTER = fish(Objs.RAW_LOBSTER, 40, 90.0, 6, 95, "You catch a raw lobster.")
private val MONKFISH = fish(Objs.RAW_MONKFISH, 62, 120.0, 48, 90, "You catch a raw monkfish.")
private val MACKEREL = fish(Objs.RAW_MACKEREL, 16, 20.0, 5, 65, "You catch a raw mackerel.")
private val COD = fish(Objs.RAW_COD, 23, 45.0, 4, 55, "You catch a raw cod.")
private val BASS =
    Fish(
        Objs.RAW_BASS,
        46,
        100.0,
        Rates(intArrayOf(3), intArrayOf(40)),
        "You catch a raw bass.",
        Objs.POH_TROPHYDROP_BASS,
        1000,
        "You catch an enormous bass!",
    )
private val TUNA = Fish(Objs.RAW_TUNA, 35, 80.0, tiered(intArrayOf(8, 9, 10), intArrayOf(64, 76, 86)), "You catch a raw tuna.")
private val SWORDFISH =
    Fish(
        Objs.RAW_SWORDFISH,
        50,
        100.0,
        tiered(intArrayOf(4, 4, 5), intArrayOf(48, 57, 64)),
        "You catch a raw swordfish.",
        Objs.POH_TROPHYDROP_SWORDFISH,
        2500,
        "You catch an enormous swordfish!",
    )
private val SHARK =
    Fish(
        Objs.RAW_SHARK,
        76,
        110.0,
        tiered(intArrayOf(3, 3, 4), intArrayOf(40, 48, 54)),
        "You catch a raw shark.",
        Objs.POH_TROPHYDROP_SHARK,
        5000,
        "You catch an enormous shark!",
    )

/** A fishing tool: the Fishing level to use it, the animation, and its harpoon [tier] (0 for everything else). */
internal class Tool(val level: Int, val anim: Int, val tier: Int)

/** What a method consumes on a catch ([obj] = -1 for nothing) and the fish it can give, best first (cascading rolls). */
internal class Bait(val obj: Int, val catches: List<Fish>) {
    /** The lowest Fishing level that catches anything with this bait. */
    val minLevel: Int = catches.minOf { it.level }
}

private val ROD = mapOf(Objs.FISHING_ROD to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0))

/** Harpoon tiers: the Wiki's 20% (dragon, infernal) and 35% (crystal) catch-rate gains are the tier 1 and 2 rates. */
private val HARPOONS =
    mapOf(
        Objs.HARPOON to Tool(1, Seqs.HUMAN_HARPOON, 0),
        Objs.HUNTING_BARBED_HARPOON to Tool(1, Seqs.HUMAN_HARPOON_BARBED, 0),
        Objs.DRAGON_HARPOON to Tool(61, Seqs.HUMAN_HARPOON_DRAGON, 1),
        Objs.INFERNAL_HARPOON to Tool(61, Seqs.HUMAN_HARPOON_INFERNAL, 1),
        Objs.CRYSTAL_HARPOON to Tool(71, Seqs.HUMAN_HARPOON_CRYSTAL, 2),
    )

/** A way to fish a spot (docs/mechanics/fishing.md FS2): tools, what each bait gives, and the texts. */
internal enum class Method(
    val tools: Map<Int, Tool>,
    val baits: List<Bait>,
    val start: String,
    val noTool: String,
    val noBait: String = "",
    /** 1 in N rolls is one tick slower (Wiki "Raw pike"), 0 = never. */
    val slowOneIn: Int = 0,
) {
    SMALL_NET(
        mapOf(Objs.NET to Tool(1, Seqs.HUMAN_SMALLNET, 0)),
        listOf(Bait(-1, listOf(ANCHOVIES, SHRIMPS))),
        "You cast out your net...",
        "You need a small fishing net to catch these fish.",
    ),
    TUTORIAL_NET(
        mapOf(Objs.NET to Tool(1, Seqs.HUMAN_SMALLNET, 0)),
        listOf(Bait(-1, listOf(SHRIMPS))),
        "You cast out your net...",
        "You need a small fishing net to catch these fish.",
    ),
    SEA_BAIT(
        ROD,
        listOf(Bait(Objs.FISHING_BAIT, listOf(HERRING, SARDINE))),
        "You cast out your line...",
        "You need a fishing rod to fish at this spot.",
        "You don't have any fishing bait left.",
    ),
    RIVER_BAIT(
        ROD,
        listOf(Bait(Objs.FISHING_BAIT, listOf(PIKE))),
        "You cast out your line...",
        "You need a fishing rod to fish at this spot.",
        "You don't have any fishing bait left.",
        slowOneIn = 5,
    ),
    LURE(
        mapOf(Objs.FLY_FISHING_ROD to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0)),
        listOf(Bait(Objs.FEATHER, listOf(SALMON, TROUT)), Bait(Objs.HUNTING_STRIPY_BIRD_FEATHER, listOf(RAINBOW_FISH))),
        "You cast out your line...",
        "You need a fly fishing rod to fish at this spot.",
        "You don't have any feathers left.",
    ),
    CAGE(
        mapOf(Objs.LOBSTER_POT to Tool(1, Seqs.HUMAN_LOBSTER, 0)),
        listOf(Bait(-1, listOf(LOBSTER))),
        "You attempt to catch a lobster.",
        "You need a lobster pot to catch lobsters.",
    ),
    HARPOON_FISH(
        HARPOONS,
        listOf(Bait(-1, listOf(SWORDFISH, TUNA))),
        "You start harpooning fish.",
        "You need a harpoon to catch these fish.",
    ),
    HARPOON_SHARK(
        HARPOONS,
        listOf(Bait(-1, listOf(SHARK))),
        "You start harpooning fish.",
        "You need a harpoon to catch these fish.",
    ),
    MONKFISH_NET(
        mapOf(Objs.NET to Tool(1, Seqs.HUMAN_SMALLNET, 0)),
        listOf(Bait(-1, listOf(MONKFISH))),
        "You cast out your net...",
        "You need a small fishing net to catch these fish.",
    ),
    BIG_NET(
        mapOf(Objs.BIG_NET to Tool(1, Seqs.HUMAN_LARGENET, 0)),
        listOf(Bait(-1, listOf(MACKEREL, COD, BASS))),
        "You cast out your net...",
        "You need a big fishing net to catch these fish.",
    ),
    ;

    /** Tool -> Fishing level, for [Skilling.bestTool]. */
    val toolLevels: Map<Int, Int> = tools.mapValues { it.value.level }
}

/** One roll of a big net catch: an item, the Fishing level it needs, and the rates (Wiki "Fishing spot (big net, harpoon)"). */
internal class NetRoll(val fish: Fish?, val obj: Int, val level: Int, val low: Int, val high: Int)

private fun junk(obj: Int, low: Int, high: Int) = NetRoll(null, obj, 16, low, high)

/** The nine rolls of a big net cast, in the Wiki's order (boots, seaweed, gloves, mackerel twice, oyster, casket, cod, bass). */
internal val BIG_NET_ROLLS: List<NetRoll> =
    listOf(
        junk(Objs.LEATHER_BOOTS, 10, 10),
        junk(Objs.SEAWEED, 10, 10),
        junk(Objs.LEATHER_GLOVES, 5, 5),
        NetRoll(MACKEREL, MACKEREL.obj, MACKEREL.level, 5, 65),
        NetRoll(MACKEREL, MACKEREL.obj, MACKEREL.level, 5, 65),
        junk(Objs.OYSTERSHELL, 3, 7),
        junk(Objs.CASKET, 1, 2),
        NetRoll(COD, COD.obj, COD.level, 4, 55),
        NetRoll(BASS, BASS.obj, BASS.level, 3, 40),
    )

/** The two ops of a spot type: op 1 (Net, Lure, Cage, Big Net) and op 3 (Bait, Harpoon); `fishing-spots.tsv`. */
internal enum class SpotKind(val primary: Method, val secondary: Method?) {
    NET_BAIT(Method.SMALL_NET, Method.SEA_BAIT),
    LURE_BAIT(Method.LURE, Method.RIVER_BAIT),
    CAGE_HARPOON(Method.CAGE, Method.HARPOON_FISH),
    BIGNET_HARPOON(Method.BIG_NET, Method.HARPOON_SHARK),
    NET_HARPOON(Method.MONKFISH_NET, Method.HARPOON_FISH),
    TUTORIAL(Method.TUTORIAL_NET, null),
}
