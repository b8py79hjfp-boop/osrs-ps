/*
 * Tree, stump, axe and success-rate data adapted from RS Mod (ISC licence, https://github.com/rsmod/rsmod):
 * content/skills/woodcutting `TreeLocs`, `WoodcuttingRates`, `WoodcuttingAxes`.
 * Copyright (c) RS Mod contributors. Permission to use, copy, modify, and/or distribute this software for any purpose
 * with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear
 * in all copies.
 */
@file:Suppress("MagicNumber") // data tables: levels, xp, ticks and 1/256 rates from the source above

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * A kind of tree (docs/mechanics/woodcutting.md WC2): level, xp, product and depletion. [despawnTicks] > 0 is the
 * Forestry tree timer; otherwise each log depletes the tree with chance [depleteChance] in 255 being beaten (0 =
 * every log). [rates] is each axe's (low, high) success out of 256.
 */
internal enum class TreeKind(
    val level: Int,
    val xp: Double,
    val logs: Int,
    val despawnTicks: Int,
    val respawn: IntRange,
    val depleteChance: Int,
    val rates: AxeRates,
) {
    REGULAR(1, 25.0, Objs.LOGS, 0, 60..100, 0, AxeRates.REGULAR),
    ACHEY(1, 25.0, Objs.ACHEY_TREE_LOGS, 0, 60..100, 0, AxeRates.REGULAR),
    BURNT(1, 25.0, Objs.CHARCOAL, 0, 60..100, 0, AxeRates.REGULAR),
    OAK(15, 37.5, Objs.OAK_LOGS, 45, 15..15, 0, AxeRates.OAK),
    WILLOW(30, 67.5, Objs.WILLOW_LOGS, 50, 15..15, 0, AxeRates.WILLOW),
    TEAK(35, 85.0, Objs.TEAK_LOGS, 50, 16..16, 0, AxeRates.TEAK),
    JUNIPER(42, 35.0, Objs.JUNIPER_LOGS, 0, 15..15, 16, AxeRates.REGULAR),
    MAPLE(45, 100.0, Objs.MAPLE_LOGS, 100, 60..60, 0, AxeRates.MAPLE),
    HOLLOW(45, 82.5, Objs.HOLLOW_BARK, 60, 43..43, 0, AxeRates.HOLLOW),
    MAHOGANY(50, 125.0, Objs.MAHOGANY_LOGS, 100, 15..15, 0, AxeRates.MAHOGANY),
    ARCTIC(54, 40.0, Objs.ARCTIC_PINE_LOG, 140, 15..15, 0, AxeRates.ARCTIC),
    YEW(60, 175.0, Objs.YEW_LOGS, 190, 100..100, 0, AxeRates.YEW),
    MAGIC(75, 250.0, Objs.MAGIC_LOGS, 390, 200..200, 0, AxeRates.MAGIC),
    REDWOOD(90, 380.0, Objs.REDWOOD_LOGS, 440, 200..200, 0, AxeRates.REDWOOD),
}

/** A choppable loc: its kind and the stump it becomes. */
internal class Tree(val kind: TreeKind, val stump: Int)

/** An axe: its woodcutting animation and its tier in [AxeRates] (0 bronze .. 9 crystal). */
internal class Axe(val anim: Int, val tier: Int)

/** Success rates per axe tier, packed `low shl 16 or high` (RS Mod `WoodcuttingRates`). */
internal enum class AxeRates(vararg val packed: Int) {
    REGULAR(
        AxeRatePack.r(64, 200),
        AxeRatePack.r(96, 300),
        AxeRatePack.r(128, 400),
        AxeRatePack.r(144, 450),
        AxeRatePack.r(160, 500),
        AxeRatePack.r(192, 600),
        AxeRatePack.r(224, 700),
        AxeRatePack.r(240, 750),
        AxeRatePack.r(250, 800),
    ),
    OAK(
        AxeRatePack.r(32, 100),
        AxeRatePack.r(48, 150),
        AxeRatePack.r(64, 200),
        AxeRatePack.r(72, 225),
        AxeRatePack.r(80, 250),
        AxeRatePack.r(96, 300),
        AxeRatePack.r(112, 350),
        AxeRatePack.r(120, 375),
        AxeRatePack.r(120, 375),
    ),
    WILLOW(
        AxeRatePack.r(16, 50),
        AxeRatePack.r(24, 75),
        AxeRatePack.r(32, 100),
        AxeRatePack.r(36, 112),
        AxeRatePack.r(40, 125),
        AxeRatePack.r(48, 150),
        AxeRatePack.r(56, 175),
        AxeRatePack.r(60, 187),
        AxeRatePack.r(60, 187),
    ),
    TEAK(
        AxeRatePack.r(15, 46),
        AxeRatePack.r(23, 70),
        AxeRatePack.r(31, 93),
        AxeRatePack.r(35, 102),
        AxeRatePack.r(39, 117),
        AxeRatePack.r(47, 140),
        AxeRatePack.r(55, 164),
        AxeRatePack.r(60, 190),
        AxeRatePack.r(60, 200),
    ),
    MAPLE(
        AxeRatePack.r(8, 25),
        AxeRatePack.r(12, 37),
        AxeRatePack.r(16, 50),
        AxeRatePack.r(18, 56),
        AxeRatePack.r(20, 62),
        AxeRatePack.r(24, 75),
        AxeRatePack.r(28, 87),
        AxeRatePack.r(30, 93),
        AxeRatePack.r(30, 93),
    ),
    ARCTIC(
        AxeRatePack.r(6, 30),
        AxeRatePack.r(8, 44),
        AxeRatePack.r(11, 60),
        AxeRatePack.r(13, 67),
        AxeRatePack.r(14, 74),
        AxeRatePack.r(17, 90),
        AxeRatePack.r(20, 104),
        AxeRatePack.r(21, 112),
        AxeRatePack.r(21, 112),
    ),
    MAHOGANY(
        AxeRatePack.r(8, 25),
        AxeRatePack.r(12, 38),
        AxeRatePack.r(16, 50),
        AxeRatePack.r(18, 54),
        AxeRatePack.r(20, 63),
        AxeRatePack.r(25, 75),
        AxeRatePack.r(29, 88),
        AxeRatePack.r(34, 94),
        AxeRatePack.r(36, 97),
    ),
    YEW(
        AxeRatePack.r(4, 12),
        AxeRatePack.r(6, 19),
        AxeRatePack.r(8, 25),
        AxeRatePack.r(9, 28),
        AxeRatePack.r(10, 31),
        AxeRatePack.r(12, 37),
        AxeRatePack.r(14, 44),
        AxeRatePack.r(15, 47),
        AxeRatePack.r(15, 47),
    ),
    MAGIC(
        AxeRatePack.r(2, 6),
        AxeRatePack.r(3, 9),
        AxeRatePack.r(4, 12),
        AxeRatePack.r(5, 13),
        AxeRatePack.r(5, 15),
        AxeRatePack.r(6, 18),
        AxeRatePack.r(7, 21),
        AxeRatePack.r(7, 22),
        AxeRatePack.r(7, 22),
    ),
    REDWOOD(
        AxeRatePack.r(2, 6),
        AxeRatePack.r(3, 9),
        AxeRatePack.r(4, 12),
        AxeRatePack.r(4, 14),
        AxeRatePack.r(5, 15),
        AxeRatePack.r(6, 18),
        AxeRatePack.r(7, 21),
        AxeRatePack.r(7, 30),
        AxeRatePack.r(8, 35),
    ),
    HOLLOW(
        AxeRatePack.r(18, 26),
        AxeRatePack.r(28, 40),
        AxeRatePack.r(36, 54),
        AxeRatePack.r(42, 57),
        AxeRatePack.r(46, 68),
        AxeRatePack.r(59, 81),
        AxeRatePack.r(64, 94),
        AxeRatePack.r(67, 101),
        AxeRatePack.r(67, 101),
    ),
    ;

    fun low(tier: Int): Int = packed[tier] ushr 16

    fun high(tier: Int): Int = packed[tier] and 0xFFFF
}

/**
 * Packs a (low, high) rate. In its own object so [AxeRates] initialisation does not run this file's class initialiser
 * (which builds [TREES] from [TreeKind], itself still initialising when it first touches [AxeRates]).
 */
private object AxeRatePack {
    fun r(low: Int, high: Int): Int = (low shl 16) or high
}

/** Axe tiers: bronze, iron, steel, black, mithril, adamant, rune (and gilded), dragon (and 3rd age, infernal), crystal. */
private const val BRONZE = 0
private const val IRON = 1
private const val STEEL = 2
private const val BLACK = 3
private const val MITHRIL = 4
private const val ADAMANT = 5
private const val RUNE = 6
private const val DRAGON = 7
private const val CRYSTAL = 8

/** Every woodcutting axe (RS Mod `WoodcuttingAxes`; the blessed axe is not a woodcutting axe there). */
internal val AXES: Map<Int, Axe> =
    mapOf(
        Objs.BRONZE_AXE to Axe(Seqs.HUMAN_WOODCUTTING_BRONZE_AXE, BRONZE),
        Objs.IRON_AXE to Axe(Seqs.HUMAN_WOODCUTTING_IRON_AXE, IRON),
        Objs.STEEL_AXE to Axe(Seqs.HUMAN_WOODCUTTING_STEEL_AXE, STEEL),
        Objs.BLACK_AXE to Axe(Seqs.HUMAN_WOODCUTTING_BLACK_AXE, BLACK),
        Objs.MITHRIL_AXE to Axe(Seqs.HUMAN_WOODCUTTING_MITHRIL_AXE, MITHRIL),
        Objs.ADAMANT_AXE to Axe(Seqs.HUMAN_WOODCUTTING_ADAMANT_AXE, ADAMANT),
        Objs.RUNE_AXE to Axe(Seqs.HUMAN_WOODCUTTING_RUNE_AXE, RUNE),
        Objs.TRAIL_GILDED_AXE to Axe(Seqs.HUMAN_WOODCUTTING_GILDED_AXE, RUNE),
        Objs.DRAGON_AXE to Axe(Seqs.HUMAN_WOODCUTTING_DRAGON_AXE, DRAGON),
        Objs.TRAILBLAZER_AXE_NO_INFERNAL to Axe(Seqs.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL, DRAGON),
        Objs._3A_AXE to Axe(Seqs.HUMAN_WOODCUTTING_3A_AXE, DRAGON),
        Objs.INFERNAL_AXE to Axe(Seqs.HUMAN_WOODCUTTING_INFERNAL_AXE, DRAGON),
        Objs.TRAILBLAZER_AXE to Axe(Seqs.HUMAN_WOODCUTTING_TRAILBLAZER_AXE, DRAGON),
        Objs.CRYSTAL_AXE to Axe(Seqs.HUMAN_WOODCUTTING_CRYSTAL_AXE, CRYSTAL),
    )

/** Every choppable loc and its stump (RS Mod `WoodcuttingTrees`). */
internal val TREES: Map<Int, Tree> =
    mapOf(
        Locs.ACHEY_TREE to Tree(TreeKind.ACHEY, 3371),
        Locs.ARCTIC_PINE to Tree(TreeKind.ARCTIC, 21274),
        Locs.FOSSIL_DEADTREE_LARGE1 to Tree(TreeKind.BURNT, 30855),
        Locs.FOSSIL_DEADTREE_SMALL1 to Tree(TreeKind.BURNT, 30856),
        Locs.DEADTREE1 to Tree(TreeKind.REGULAR, 1347),
        Locs.DEADTREE1_LARGE to Tree(TreeKind.REGULAR, 1347),
        Locs.DEADTREE4 to Tree(TreeKind.REGULAR, 6212),
        Locs.LIGHTDEADTREE1 to Tree(TreeKind.REGULAR, 1349),
        Locs.DEADTREE2 to Tree(TreeKind.REGULAR, 1351),
        Locs.DEADTREE2_DARK to Tree(TreeKind.REGULAR, 1353),
        Locs.DEADTREE3 to Tree(TreeKind.REGULAR, 1354),
        Locs.DEADTREE2_SNOWY to Tree(TreeKind.REGULAR, 23054),
        Locs.DEADTREE2_SWAMP to Tree(TreeKind.REGULAR, 1352),
        Locs.DEADTREE6 to Tree(TreeKind.REGULAR, 1358),
        Locs.DEADTREE_BURNT to Tree(TreeKind.REGULAR, 1353),
        Locs.MDAUGHTER_PASSABLE_TREE to Tree(TreeKind.REGULAR, 5905),
        Locs.FAI_VARROCK_DEAD_TREE to Tree(TreeKind.REGULAR, 3649),
        Locs.EVERGREEN_VSNOWY_LARGE to Tree(TreeKind.REGULAR, 1355),
        Locs.EVERGREEN_SNOWY_LARGE to Tree(TreeKind.REGULAR, 1355),
        Locs.EVERGREEN to Tree(TreeKind.REGULAR, 1342),
        Locs.EVERGREEN_LARGE to Tree(TreeKind.REGULAR, 1355),
        Locs.HOLLOW_TREE to Tree(TreeKind.HOLLOW, 2310),
        Locs.HOLLOW_TREE_BIG to Tree(TreeKind.HOLLOW, 4061),
        Locs.MM_BUSH_KHARAZI_JUNGLE_TREE1 to Tree(TreeKind.REGULAR, 4819),
        Locs.MM_BUSH_KHARAZI_JUNGLE_TREE2 to Tree(TreeKind.REGULAR, 4821),
        Locs.MAGICTREE to Tree(TreeKind.MAGIC, 9713),
        Locs.PRIF_MAHOGANYTREE to Tree(TreeKind.MAHOGANY, 36689),
        Locs.MAHOGANYTREE_UPDATE to Tree(TreeKind.MAHOGANY, 40761),
        Locs.MAHOGANYTREE to Tree(TreeKind.MAHOGANY, 9035),
        Locs.MAPLETREE to Tree(TreeKind.MAPLE, 9712),
        Locs.PRIF_MAPLETREE to Tree(TreeKind.MAPLE, 36682),
        Locs.MAPLETREE_UPDATE to Tree(TreeKind.MAPLE, 40755),
        Locs.MATURE_JUNIPER_TREE to Tree(TreeKind.JUNIPER, 27500),
        Locs.OAKTREE to Tree(TreeKind.OAK, 1356),
        Locs.TREE_OAK_DEFAULT01 to Tree(TreeKind.OAK, 42396),
        Locs.REDWOODTREE_L to Tree(TreeKind.REDWOOD, 29669),
        Locs.REDWOODTREE_R to Tree(TreeKind.REDWOOD, 29671),
        Locs.TREE to Tree(TreeKind.REGULAR, 1342),
        Locs.LIGHTTREE to Tree(TreeKind.REGULAR, 1343),
        Locs.TREE2 to Tree(TreeKind.REGULAR, 1342),
        Locs.TREE3 to Tree(TreeKind.REGULAR, 1342),
        Locs.LIGHTTREE2 to Tree(TreeKind.REGULAR, 1343),
        Locs.SNOWTREE1 to Tree(TreeKind.REGULAR, 1355),
        Locs.SNOWTREE2 to Tree(TreeKind.REGULAR, 1355),
        Locs.SNOWTREE3 to Tree(TreeKind.REGULAR, 1355),
        Locs.PEST_TREE to Tree(TreeKind.REGULAR, 1342),
        Locs.PEST_TREE2 to Tree(TreeKind.REGULAR, 1342),
        Locs.PRIF_TREE_NORMAL_1 to Tree(TreeKind.REGULAR, 36673),
        Locs.PRIF_TREE_NORMAL_2 to Tree(TreeKind.REGULAR, 36675),
        Locs.PRIF_OUTSIDE_TREE_NORMAL_1 to Tree(TreeKind.REGULAR, 36678),
        Locs.PRIF_OUTSIDE_TREE_NORMAL_2 to Tree(TreeKind.REGULAR, 36678),
        Locs.REGICIDE_TREE_LARGE to Tree(TreeKind.REGULAR, 3880),
        Locs.REGICIDE_TREE_LARGE2 to Tree(TreeKind.REGULAR, 3880),
        Locs.REGICIDE_TREE_LARGE3 to Tree(TreeKind.REGULAR, 3880),
        Locs.REGICIDE_TREE_SMALL to Tree(TreeKind.REGULAR, 3884),
        Locs.TREE_UPDATE_1 to Tree(TreeKind.REGULAR, 40751),
        Locs.TREE_UPDATE_2 to Tree(TreeKind.REGULAR, 40753),
        Locs.TREE_NORMAL_DEFAULT01 to Tree(TreeKind.REGULAR, 42394),
        Locs.AVIUM_TREE_1 to Tree(TreeKind.REGULAR, 51763),
        Locs.AVIUM_TREE_2 to Tree(TreeKind.REGULAR, 51765),
        Locs.TREE4 to Tree(TreeKind.REGULAR, 1342),
        Locs.AVIUM_TREE_DARK to Tree(TreeKind.REGULAR, 53189),
        Locs.PRIF_TEAKTREE to Tree(TreeKind.TEAK, 36687),
        Locs.TEAKTREE_UPDATE to Tree(TreeKind.TEAK, 40759),
        Locs.TEAKTREE to Tree(TreeKind.TEAK, 9037),
        Locs.WILLOWTREE to Tree(TreeKind.WILLOW, 9711),
        Locs.WILLOW_TREE2 to Tree(TreeKind.WILLOW, 9471),
        Locs.WILLOW_TREE3 to Tree(TreeKind.WILLOW, 9471),
        Locs.WILLOW_TREE4 to Tree(TreeKind.WILLOW, 9471),
        Locs.YEWTREE to Tree(TreeKind.YEW, 9714),
        Locs.PRIF_YEWTREE to Tree(TreeKind.YEW, 36684),
        Locs.YEWTREE_UPDATE to Tree(TreeKind.YEW, 40757),
        Locs.TREE_YEW_DEFAULT01 to Tree(TreeKind.YEW, 42392),
        Locs.DEADMAN_YEWTREE_INSURANCESTALL to Tree(TreeKind.YEW, 9714),
    )
