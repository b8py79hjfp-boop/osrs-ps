@file:Suppress("MagicNumber") // data tables: levels, xp, ticks and 1/256 rates from the OSRS Wiki pages named in docs/mechanics/mining.md

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs

/** What a rock does when mined (docs/mechanics/mining.md MN2). */
internal enum class RockKind {
    /** One roll for the ore; the rock depletes with it. */
    ORE,

    /** Every attempt succeeds and the rock never depletes (rune and pure essence). */
    ESSENCE,

    /** A roll for any gem, then a weighted pick of the gem (gem rocks). */
    GEM,

    /** Cascading rolls from the heaviest block to the lightest (sandstone, granite). */
    CASCADE,

    /** One roll for the amethyst; the rock depletes after 2 or 3 of them. */
    AMETHYST,
}

/** One product of a rock: [obj], [xp], and the success rates out of 256 at level 1 and 99 ([low], [high]). */
internal class Yield(val obj: Int, val xp: Double, val low: Int, val high: Int)

/**
 * A kind of rock: required [level], what it [yields] (a cascade lists the heaviest first), whether the Mining gem drop
 * table is rolled before each attempt ([gemTable]), the respawn time in ticks, and the texts: [verb] and [mined] for
 * "You manage to mine some copper." and [noun] for the full-inventory message.
 */
internal class Rock(
    val kind: RockKind,
    val level: Int,
    val yields: List<Yield>,
    val respawn: Int,
    val gemTable: Boolean,
    val verb: String,
    val mined: String,
    val noun: String,
)

/** A rock loc in the cache and the empty rock it turns into while it respawns. */
internal class RockLoc(val rock: Rock, val empty: Int)

/**
 * A pickaxe: its mining animation, its ticks between rolls, and [fastOneIn] (1 in N rolls is one tick faster, 0 = never;
 * Wiki "Pickaxe": dragon 3 ticks with a 1/6 chance of 2, crystal 3 ticks with a 1/4 chance of 2).
 */
internal class Pickaxe(val anim: Int, val ticks: Int, val fastOneIn: Int)

/** Ticks between rolls and the faster-roll odds as listed on the Wiki's "Pickaxe" page ("Ticks between rolls"). */
private const val DRAGON_FAST_ONE_IN = 6
private const val CRYSTAL_FAST_ONE_IN = 4

/** Every pickaxe with a mining animation in the cache (Wiki "Pickaxe" > "Ticks between rolls"). */
internal val PICKAXES: Map<Int, Pickaxe> =
    mapOf(
        Objs.BRONZE_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_BRONZE_PICKAXE, 8, 0),
        Objs.IRON_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_IRON_PICKAXE, 7, 0),
        Objs.STEEL_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_STEEL_PICKAXE, 6, 0),
        Objs.BLACK_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_BLACK_PICKAXE, 5, 0),
        Objs.MITHRIL_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_MITHRIL_PICKAXE, 5, 0),
        Objs.ADAMANT_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_ADAMANT_PICKAXE, 4, 0),
        Objs.RUNE_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_RUNE_PICKAXE, 3, 0),
        Objs.TRAIL_GILDED_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_GILDED_PICKAXE, 3, 0),
        Objs.DRAGON_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_DRAGON_PICKAXE, 3, DRAGON_FAST_ONE_IN),
        Objs.DRAGON_PICKAXE_PRETTY to Pickaxe(Seqs.HUMAN_MINING_DRAGON_PICKAXE_PRETTY, 3, DRAGON_FAST_ONE_IN),
        Objs._3A_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_3A_PICKAXE, 3, DRAGON_FAST_ONE_IN),
        Objs.INFERNAL_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_INFERNAL_PICKAXE, 3, DRAGON_FAST_ONE_IN),
        Objs.TRAILBLAZER_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_TRAILBLAZER_PICKAXE, 3, DRAGON_FAST_ONE_IN),
        Objs.TRAILBLAZER_PICKAXE_NO_INFERNAL to Pickaxe(Seqs.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL, 3, DRAGON_FAST_ONE_IN),
        Objs.TRAILBLAZER_RELOADED_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE, 3, DRAGON_FAST_ONE_IN),
        Objs.TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL to
            Pickaxe(Seqs.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL, 3, DRAGON_FAST_ONE_IN),
        Objs.CRYSTAL_PICKAXE to Pickaxe(Seqs.HUMAN_MINING_CRYSTAL_PICKAXE, 3, CRYSTAL_FAST_ONE_IN),
    )

private fun ore(level: Int, xp: Double, obj: Int, low: Int, high: Int, respawn: Int, mined: String, noun: String = "$mined ore"): Rock =
    Rock(RockKind.ORE, level, listOf(Yield(obj, xp, low, high)), respawn, true, "mine", mined, noun)

/** Mining level 30 swaps rune essence for pure essence (Wiki "Rune Essence Mine", "Rune Essence (rock)"). */
internal const val PURE_ESSENCE_LEVEL = 30

/** Both essence products; the rock picks by level ([PURE_ESSENCE_LEVEL]). */
internal val ESSENCE_ROCK: Rock =
    Rock(
        RockKind.ESSENCE,
        1,
        listOf(Yield(Objs.BLANKRUNE, 5.0, 0, 0), Yield(Objs.BLANKRUNE_HIGH, 5.0, 0, 0)),
        0,
        false,
        "mine",
        "essence",
        "essence",
    )

private val COPPER = ore(1, 17.5, Objs.COPPER_ORE, 100, 350, 4, "copper")
private val TIN = ore(1, 17.5, Objs.TIN_ORE, 100, 350, 4, "tin")
private val CLAY = ore(1, 5.0, Objs.CLAY, 128, 400, 2, "clay", "clay")
private val IRON = ore(15, 35.0, Objs.IRON_ORE, 96, 350, 9, "iron")
private val SILVER = ore(20, 40.0, Objs.SILVER_ORE, 25, 200, 100, "silver")
private val COAL = ore(30, 50.0, Objs.COAL, 16, 100, 50, "coal", "coal")
private val GOLD = ore(40, 65.0, Objs.GOLD_ORE, 7, 75, 100, "gold")
private val MITHRIL = ore(55, 80.0, Objs.MITHRIL_ORE, 4, 50, 200, "mithril")
private val ADAMANTITE = ore(70, 95.0, Objs.ADAMANTITE_ORE, 2, 25, 400, "adamantite")
private val RUNITE = ore(85, 125.0, Objs.RUNITE_ORE, 1, 18, 1200, "runite")

/** Amethyst: low -18 and high 10 reproduce the Wiki's 3.5% at level 92 and 4.3% at level 99 (Wiki "Amethyst crystals"). */
private val AMETHYST = Rock(RockKind.AMETHYST, 92, listOf(Yield(Objs.AMETHYST, 240.0, -18, 10)), 125, true, "mine", "amethyst", "amethyst")

/** Gem rocks: the 65 xp / 28 and 70 roll; the gem itself is picked by [GEM_WEIGHTS] (Wiki "Gem rocks"). */
private val GEM_ROCK = Rock(RockKind.GEM, 40, listOf(Yield(0, 65.0, 28, 70)), 99, false, "mine", "gem", "uncut gems")

/** Sandstone blocks heaviest first with their xp and rates (Wiki "Sandstone rocks"). */
private val SANDSTONE =
    Rock(
        RockKind.CASCADE,
        35,
        listOf(
            Yield(Objs.ENAKH_SANDSTONE_LARGE, 60.0, 4, 50),
            Yield(Objs.ENAKH_SANDSTONE_MEDIUM, 50.0, 8, 75),
            Yield(Objs.ENAKH_SANDSTONE_SMALL, 40.0, 16, 100),
            Yield(Objs.ENAKH_SANDSTONE_TINY, 30.0, 25, 200),
        ),
        9,
        false,
        "quarry",
        "sandstone",
        "sandstone",
    )

/** Granite blocks heaviest first with their xp and rates (Wiki "Granite rocks"). */
private val GRANITE =
    Rock(
        RockKind.CASCADE,
        45,
        listOf(
            Yield(Objs.ENAKH_GRANITE_MEDIUM, 75.0, 6, 64),
            Yield(Objs.ENAKH_GRANITE_SMALL, 60.0, 8, 75),
            Yield(Objs.ENAKH_GRANITE_TINY, 50.0, 16, 100),
        ),
        9,
        false,
        "quarry",
        "granite",
        "granite",
    )

/**
 * Gems of a gem rock and their weights out of 128, rarest last (Wiki "Gem rocks" drop table): opal 60, jade 30, red topaz
 * 15, sapphire 9, emerald 5, ruby 5, diamond 4.
 */
internal val GEM_WEIGHTS: List<Pair<Int, Int>> =
    listOf(
        Objs.UNCUT_OPAL to 60,
        Objs.UNCUT_JADE to 30,
        Objs.UNCUT_RED_TOPAZ to 15,
        Objs.UNCUT_SAPPHIRE to 9,
        Objs.UNCUT_EMERALD to 5,
        Objs.UNCUT_RUBY to 5,
        Objs.UNCUT_DIAMOND to 4,
    )

/** Total of [GEM_WEIGHTS]. */
internal const val GEM_WEIGHT_TOTAL = 128

/** Chance 1 in 256 that the Mining gem drop table is rolled on an attempt (Wiki "Mining gem drop table"). */
internal const val GEM_TABLE_ONE_IN = 256

/**
 * The Mining gem drop table in 1/128 shares: nothing 70, sapphire 32, emerald 16, ruby 8, diamond 2
 * (Wiki template MiningPreGemDropTable).
 */
internal val PRE_ROLL_GEMS: List<Pair<Int, Int>> =
    listOf(
        -1 to 70,
        Objs.UNCUT_SAPPHIRE to 32,
        Objs.UNCUT_EMERALD to 16,
        Objs.UNCUT_RUBY to 8,
        Objs.UNCUT_DIAMOND to 2,
    )

/** Amethyst depletes after 2 or 3 amethysts (Wiki "Amethyst crystals"; OPEN-MN4: the choice is assumed to be uniform). */
internal const val AMETHYST_MIN_ORES = 2

/** See [AMETHYST_MIN_ORES]. */
internal const val AMETHYST_MAX_ORES = 3

private fun locs(rock: Rock, first: Pair<Int, Int>, vararg more: Pair<Int, Int>): Map<Int, RockLoc> =
    (listOf(first) + more).associate { (loc, empty) -> loc to RockLoc(rock, empty) }

/** Empty rock variants (Wiki "Rocks (empty)": ids 11390, 11391 respawn, 11392 permanently empty). */
private const val EMPTY_1 = Locs.ROCKS1
private const val EMPTY_2 = Locs.ROCKS2

/**
 * Every minable rock loc and the empty rock it leaves (OPEN-MN1: which of the two empty variants goes with which ore variant
 * is not sourced; the "…ROCK1" locs get [EMPTY_1] and the "…ROCK2" locs [EMPTY_2]).
 */
internal val ROCKS: Map<Int, RockLoc> =
    buildMap {
        putAll(
            locs(
                COPPER,
                Locs.COPPERROCK1 to EMPTY_1,
                Locs.COPPERROCK2 to EMPTY_2,
                Locs.NEWBIECOPPERROCK to Locs.NEWBIEROCKS1,
                Locs.TUT2_COPPERROCK to Locs.TUT2_EMPTY_ROCK,
            ),
        )
        putAll(
            locs(
                TIN,
                Locs.TINROCK1 to EMPTY_1,
                Locs.TINROCK2 to EMPTY_2,
                Locs.NEWBIETINROCK to Locs.NEWBIEROCKS1,
                Locs.TUT2_TINROCK to Locs.TUT2_EMPTY_ROCK,
            ),
        )
        putAll(locs(CLAY, Locs.CLAYROCK1 to EMPTY_1, Locs.CLAYROCK2 to EMPTY_2))
        putAll(locs(IRON, Locs.IRONROCK1 to EMPTY_1, Locs.IRONROCK2 to EMPTY_2, Locs.PRIF_MINE_IRONROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY))
        putAll(
            locs(
                SILVER,
                Locs.SILVERROCK1 to EMPTY_1,
                Locs.SILVERROCK2 to EMPTY_2,
                Locs.PRIF_MINE_SILVERROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY,
            ),
        )
        putAll(locs(COAL, Locs.COALROCK1 to EMPTY_1, Locs.COALROCK2 to EMPTY_2, Locs.PRIF_MINE_COALROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY))
        putAll(locs(GOLD, Locs.GOLDROCK1 to EMPTY_1, Locs.GOLDROCK2 to EMPTY_2, Locs.PRIF_MINE_GOLDROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY))
        putAll(
            locs(
                MITHRIL,
                Locs.MITHRILROCK1 to EMPTY_1,
                Locs.MITHRILROCK2 to EMPTY_2,
                Locs.PRIF_MINE_MITHRILROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY,
            ),
        )
        putAll(
            locs(
                ADAMANTITE,
                Locs.ADAMANTITEROCK1 to EMPTY_1,
                Locs.ADAMANTITEROCK2 to EMPTY_2,
                Locs.PRIF_MINE_ADAMANTITEROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY,
            ),
        )
        putAll(
            locs(
                RUNITE,
                Locs.RUNITEROCK1 to EMPTY_1,
                Locs.RUNITEROCK2 to EMPTY_2,
                Locs.PRIF_MINE_RUNITEROCK1 to Locs.PRIF_MINE_ROCKS1_EMPTY,
            ),
        )
        putAll(locs(AMETHYST, Locs.AMETHYSTROCK1 to Locs.AMETHYSTROCK_EMPTY, Locs.AMETHYSTROCK2 to Locs.AMETHYSTROCK_EMPTY))
        putAll(locs(GEM_ROCK, Locs.GEMROCK1 to EMPTY_1, Locs.GEMROCK to EMPTY_2))
        put(Locs.ENAKH_SANDSTONE_ROCKS, RockLoc(SANDSTONE, EMPTY_1))
        put(Locs.ENAKH_GRANITE_ROCKS, RockLoc(GRANITE, EMPTY_1))
        put(Locs.BLANKRUNESTONE, RockLoc(ESSENCE_ROCK, 0))
    }

/** Empty rocks: mining one only prints the Wiki's "no ore available" message ("Rocks (empty)"). */
internal val EMPTY_ROCKS: Set<Int> =
    setOf(
        Locs.ROCKS1,
        Locs.ROCKS2,
        Locs.ROCKS3,
        Locs.AMETHYSTROCK_EMPTY,
        Locs.NEWBIEROCKS1,
        Locs.PRIF_MINE_ROCKS1_EMPTY,
        Locs.TUT2_EMPTY_ROCK,
    )
