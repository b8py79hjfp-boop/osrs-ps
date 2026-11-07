@file:Suppress("MagicNumber") // data tables: levels, xp and 1/256 rates from the OSRS Wiki pages named in docs/mechanics/fishing.md

package tp.content.skills

import tp.plugin.Stats
import tp.symbols.Objs
import tp.symbols.Seqs

/** Success rates out of 256 at level 1 and 99, one pair per tool tier (harpoons); a shorter list repeats its last pair. */
internal class Rates(private val low: IntArray, private val high: IntArray) {
    fun low(tier: Int): Int = low[tier.coerceAtMost(low.size - 1)]

    fun high(tier: Int): Int = high[tier.coerceAtMost(high.size - 1)]
}

/** Experience in a second skill that a catch gives (barbarian fishing: Strength and Agility). */
internal class StatXp(val stat: Int, val xp: Double)

/** A level in another skill that a fish needs besides the Fishing level (barbarian fishing: Strength and Agility). */
internal class StatReq(val stat: Int, val level: Int)

/**
 * A fish: [level], [xp], [rates], the [message] on a catch, and the rare oversized variant that replaces it with chance
 * 1 in [bigOneIn] (0 = none): [bigObj] and its [bigMessage] (Wiki "Big bass", "Big swordfish", "Big shark"). [extras] are the xp
 * given in other skills and [requires] the levels needed in them (Wiki "Barbarian Fishing").
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
    val extras: List<StatXp> = emptyList(),
    val requires: List<StatReq> = emptyList(),
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

private fun caught(obj: Int, level: Int, xp: Double, low: Int, high: Int, name: String): Fish =
    fish(obj, level, xp, low, high, "You catch ${if (name.first() in "aeiou") "an" else "a"} $name.")

/** Raw karambwan: 65 Fishing, 50 xp, rate 5 / 160 (Wiki "Raw karambwan"; the message is OPEN-FS6). */
private val KARAMBWAN = caught(Objs.TBWT_RAW_KARAMBWAN, 65, 50.0, 5, 160, "raw karambwan")

/** A leaping fish: Fishing level, xp, rates, and the Strength and Agility it needs and gives (Wiki "Barbarian Fishing"). */
private fun leaping(obj: Int, level: Int, xp: Double, low: Int, high: Int, other: Int, otherXp: Double, name: String): Fish =
    Fish(
        obj,
        level,
        xp,
        Rates(intArrayOf(low), intArrayOf(high)),
        "You catch a $name.",
        extras = listOf(StatXp(Stats.STRENGTH, otherXp), StatXp(Stats.AGILITY, otherXp)),
        requires = listOf(StatReq(Stats.STRENGTH, other), StatReq(Stats.AGILITY, other)),
    )

private val LEAPING_TROUT = leaping(Objs.BRUT_SPAWNING_TROUT, 48, 50.0, 32, 192, 15, 5.0, "leaping trout")
private val LEAPING_SALMON = leaping(Objs.BRUT_SPAWNING_SALMON, 58, 70.0, 16, 96, 30, 6.0, "leaping salmon")
private val LEAPING_STURGEON = leaping(Objs.BRUT_STURGEON, 70, 80.0, 8, 64, 45, 7.0, "leaping sturgeon")

/** The leaping fish a barbarian rod catches, best first (cascading rolls; Wiki "Fishing spot (barbarian)"). */
private val LEAPING = listOf(LEAPING_STURGEON, LEAPING_SALMON, LEAPING_TROUT)

/** Raw dark crab: 85 Fishing, 130 xp, 3 / 40 (Wiki "Raw dark crab"; the Elite Wilderness Diary's 13 / 55 is OPEN-FS9). */
private val DARK_CRAB = caught(Objs.RAW_DARK_CRAB, 85, 130.0, 3, 40, "raw dark crab")

/** Raw anglerfish: 82 Fishing; 120 xp and 3 / 36 with sandworms, 79.2 xp and 13 / 100 with diabolic worms (Wiki "Raw anglerfish"). */
private val ANGLERFISH_SANDWORMS = caught(Objs.RAW_ANGLERFISH, 82, 120.0, 3, 36, "raw anglerfish")
private val ANGLERFISH_DIABOLIC = caught(Objs.RAW_ANGLERFISH, 82, 79.2, 13, 100, "raw anglerfish")

/** Eels with a rod and bait: both roll 10 / 80, cascading, 80 xp (Wiki "Raw slimy eel" chart), 65 xp for slimy eels in Mort Myre. */
private val CAVE_EEL = caught(Objs.RAW_CAVE_EEL, 38, 80.0, 10, 80, "raw cave eel")
private val SLIMY_EEL = caught(Objs.MORT_SLIMEY_EEL, 28, 80.0, 10, 80, "raw slimy eel")
private val SWAMP_SLIMY_EEL = caught(Objs.MORT_SLIMEY_EEL, 28, 65.0, 10, 80, "raw slimy eel")

/** Infernal eel 80 Fishing, 95 xp, 30 / 93 (Wiki "Raw infernal eel"); sacred eel 87, 105 xp, 0 / 60 (Wiki "Raw sacred eel"). */
private val INFERNAL_EEL = caught(Objs.INFERNAL_EEL, 80, 95.0, 30, 93, "infernal eel")
private val SACRED_EEL = caught(Objs.SNAKEBOSS_EEL, 87, 105.0, 0, 60, "sacred eel")

/**
 * A fishing tool: the Fishing level to use it, the animation, and its harpoon [tier] (0 for everything else). [cooks] is set
 * for the infernal harpoon (Wiki "Infernal harpoon": a caught fish is cooked and destroyed with chance 1 in 3 while it has charges).
 */
internal class Tool(val level: Int, val anim: Int, val tier: Int, val cooks: Boolean = false)

/** What a method consumes on a catch ([obj] = -1 for nothing) and the fish it can give, best first (cascading rolls). */
internal class Bait(val obj: Int, val catches: List<Fish>) {
    /** The lowest Fishing level that catches anything with this bait. */
    val minLevel: Int = catches.minOf { it.level }
}

/** The pearl fishing rod is "an equipable version of the fishing rod" (Wiki "Pearl fishing rod"). */
private val ROD =
    mapOf(
        Objs.FISHING_ROD to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
        Objs.FISHINGROD_PEARL to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
    )

/** The oily fishing rods, needed for infernal eels (Wiki "Fishing spot (infernal eel)"). */
private val OILY_ROD =
    mapOf(
        Objs.OILY_FISHING_ROD to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
        Objs.FISHINGROD_PEARL_OILY to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
    )

/** The karambwan vessel, empty or loaded with karambwanji (Wiki "Fishing spot (karambwan)"; the loading is OPEN-FS10). */
private val VESSEL =
    mapOf(
        Objs.TBWT_KARAMBWAN_VESSEL to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
        Objs.TBWT_KARAMBWAN_VESSEL_LOADED_WITH_KARAMBWANJI to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
    )

/** The barbarian rod and its pearl version (Wiki "Barbarian rod", "Pearl barbarian rod"). */
private val BARBARIAN_ROD =
    mapOf(
        Objs.BRUT_FISHING_ROD to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
        Objs.FISHINGROD_PEARL_BRUT to Tool(1, Seqs.HUMAN_FISHING_CASTING, 0),
    )

/** Harpoon tiers: the Wiki's 20% (dragon, infernal) and 35% (crystal) catch-rate gains are the tier 1 and 2 rates. */
private val HARPOONS =
    mapOf(
        Objs.HARPOON to Tool(1, Seqs.HUMAN_HARPOON, 0),
        Objs.HUNTING_BARBED_HARPOON to Tool(1, Seqs.HUMAN_HARPOON_BARBED, 0),
        Objs.DRAGON_HARPOON to Tool(61, Seqs.HUMAN_HARPOON_DRAGON, 1),
        Objs.INFERNAL_HARPOON to Tool(61, Seqs.HUMAN_HARPOON_INFERNAL, 1, cooks = true),
        Objs.CRYSTAL_HARPOON to Tool(71, Seqs.HUMAN_HARPOON_CRYSTAL, 2),
    )

/**
 * A quest or miniquest a method needs (docs/mechanics/fishing.md FS11). None of the three is implemented in content/quests, so
 * `FishingGates` enforces them only when its flag is switched on.
 */
internal enum class Gate { NONE, TAI_BWO_WANNAI_TRIO, BARBARIAN_TRAINING, REGICIDE }

/** A way to fish a spot (docs/mechanics/fishing.md FS2): tools, what each bait gives, and the texts. */
internal enum class Method(
    val tools: Map<Int, Tool>,
    val baits: List<Bait>,
    val start: String,
    val noTool: String,
    val noBait: String = "",
    /** 1 in N rolls is one tick slower (Wiki "Raw pike"), 0 = never. */
    val slowOneIn: Int = 0,
    /** The bait is used up on a failed roll too (Wiki "Raw karambwan": bait is consumed even without a catch). */
    val baitOnMiss: Boolean = false,
    /** The quest the method needs (FS11). */
    val gate: Gate = Gate.NONE,
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
    KARAMBWAN_VESSEL(
        VESSEL,
        listOf(Bait(Objs.TBWT_RAW_KARAMBWANJI, listOf(KARAMBWAN))),
        "You cast out your vessel...",
        "You need a karambwan vessel to catch these fish.",
        "You don't have any karambwanji left.",
        baitOnMiss = true,
        gate = Gate.TAI_BWO_WANNAI_TRIO,
    ),
    BARBARIAN(
        BARBARIAN_ROD,
        listOf(
            Bait(Objs.BRUT_FISH_CUTS, LEAPING),
            Bait(Objs.FISHING_BAIT, LEAPING),
            Bait(Objs.FEATHER, LEAPING),
            Bait(Objs.BRUT_ROE, LEAPING),
            Bait(Objs.BRUT_CAVIAR, LEAPING),
        ),
        "You cast out your line...",
        "You need a barbarian rod to fish at this spot.",
        "You don't have any bait left.",
        gate = Gate.BARBARIAN_TRAINING,
    ),
    DARK_CRAB_CAGE(
        mapOf(Objs.LOBSTER_POT to Tool(1, Seqs.HUMAN_LOBSTER, 0)),
        listOf(Bait(Objs.WILDERNESS_FISHING_BAIT, listOf(DARK_CRAB)), Bait(-1, listOf(LOBSTER))),
        "You attempt to catch a lobster.",
        "You need a lobster pot to catch lobsters.",
    ),
    ANGLER_BAIT(
        ROD,
        listOf(
            Bait(Objs.PISCARILIUS_SANDWORMS, listOf(ANGLERFISH_SANDWORMS)),
            Bait(Objs.DIABOLIC_WORMS, listOf(ANGLERFISH_DIABOLIC)),
        ),
        "You cast out your line...",
        "You need a fishing rod to fish at this spot.",
        "You don't have any sandworms or diabolic worms left.",
    ),
    EEL_BAIT(
        ROD,
        listOf(Bait(Objs.FISHING_BAIT, listOf(CAVE_EEL, SLIMY_EEL))),
        "You cast out your line...",
        "You need a fishing rod to fish at this spot.",
        "You don't have any fishing bait left.",
    ),
    SWAMP_BAIT(
        ROD,
        listOf(Bait(Objs.FISHING_BAIT, listOf(SWAMP_SLIMY_EEL))),
        "You cast out your line...",
        "You need a fishing rod to fish at this spot.",
        "You don't have any fishing bait left.",
    ),
    INFERNAL_EEL_BAIT(
        OILY_ROD,
        listOf(Bait(Objs.FISHING_BAIT, listOf(INFERNAL_EEL))),
        "You cast out your line...",
        "You need an oily fishing rod to fish at this spot.",
        "You don't have any fishing bait left.",
    ),
    SACRED_EEL_BAIT(
        ROD,
        listOf(Bait(Objs.FISHING_BAIT, listOf(SACRED_EEL))),
        "You cast out your line...",
        "You need a fishing rod to fish at this spot.",
        "You don't have any fishing bait left.",
        gate = Gate.REGICIDE,
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
internal enum class SpotKind(val primary: Method?, val secondary: Method?) {
    NET_BAIT(Method.SMALL_NET, Method.SEA_BAIT),
    LURE_BAIT(Method.LURE, Method.RIVER_BAIT),
    CAGE_HARPOON(Method.CAGE, Method.HARPOON_FISH),
    BIGNET_HARPOON(Method.BIG_NET, Method.HARPOON_SHARK),
    NET_HARPOON(Method.MONKFISH_NET, Method.HARPOON_FISH),
    TUTORIAL(Method.TUTORIAL_NET, null),
    KARAMBWAN(Method.KARAMBWAN_VESSEL, null),
    BARBARIAN(Method.BARBARIAN, null),
    DARK_CRAB(Method.DARK_CRAB_CAGE, null),
    ANGLER(Method.ANGLER_BAIT, null),

    /** Op 1 nets frog spawn and swamp weed (OPEN-FS12), op 3 baits eels. */
    EEL(null, Method.EEL_BAIT),
    SWAMP(Method.SWAMP_BAIT, null),
    INFERNAL_EEL(Method.INFERNAL_EEL_BAIT, null),
    SACRED_EEL(Method.SACRED_EEL_BAIT, null),
}
