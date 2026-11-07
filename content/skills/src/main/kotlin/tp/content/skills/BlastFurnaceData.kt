@file:Suppress("MagicNumber") // data tables: levels, xp, ratios and fees from the OSRS Wiki pages named in docs/mechanics/blast-furnace.md

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * One bar the Blast Furnace makes: the [bar], its primary ore, the second ore ([partner], bronze only, else -1), the coal per
 * bar ([coal], half the normal furnace's: Wiki "Blast Furnace"), the Smithing [level] and the [xp] per bar (Wiki "Blast
 * Furnace" > Experience rates: bronze 6.2, iron 12.5, silver 13.6, steel 17.5, gold 22.5, mithril 30, adamantite 37.5, runite 50).
 */
internal class BarRecipe(val bar: Int, val primary: Int, val partner: Int, val coal: Int, val level: Int, val xp: Double)

internal object BlastFurnaceData {
    /** Bars in smelting order; steel precedes iron because iron ore with coal in the pot becomes steel (Wiki "Blast Furnace" notes). */
    val BARS: List<BarRecipe> =
        listOf(
            BarRecipe(Objs.BRONZE_BAR, Objs.COPPER_ORE, Objs.TIN_ORE, 0, 1, 6.2),
            BarRecipe(Objs.STEEL_BAR, Objs.IRON_ORE, -1, 1, 30, 17.5),
            BarRecipe(Objs.IRON_BAR, Objs.IRON_ORE, -1, 0, 15, 12.5),
            BarRecipe(Objs.SILVER_BAR, Objs.SILVER_ORE, -1, 0, 20, 13.6),
            BarRecipe(Objs.GOLD_BAR, Objs.GOLD_ORE, -1, 0, 40, 22.5),
            BarRecipe(Objs.MITHRIL_BAR, Objs.MITHRIL_ORE, -1, 2, 50, 30.0),
            BarRecipe(Objs.ADAMANTITE_BAR, Objs.ADAMANTITE_ORE, -1, 3, 70, 37.5),
            BarRecipe(Objs.RUNITE_BAR, Objs.RUNITE_ORE, -1, 4, 85, 50.0),
        )

    /** Ores the belt accepts, coal first; the saved counts are keyed by position in this list. */
    val ORES: IntArray =
        intArrayOf(
            Objs.COAL,
            Objs.COPPER_ORE,
            Objs.TIN_ORE,
            Objs.IRON_ORE,
            Objs.SILVER_ORE,
            Objs.GOLD_ORE,
            Objs.MITHRIL_ORE,
            Objs.ADAMANTITE_ORE,
            Objs.RUNITE_ORE,
        )

    /** Smithing level to put an ore on the belt: the lowest bar level that uses it (coal none). */
    fun oreLevel(ore: Int): Int = BARS.filter { it.primary == ore || it.partner == ore }.minOfOrNull { it.level } ?: 1

    /** Wiki "Blast Furnace": at most 254 coal (and tin) in the furnace, at most 28 primary ores and 28 bars. */
    const val COAL_MAX = 254
    const val PRIMARY_MAX = 28

    /** Wiki "Blast Furnace" > Requirements: 2500 coins per 10 minutes below 60 Smithing (base, not boostable). */
    const val FOREMAN_FEE = 2500
    const val FOREMAN_MINUTES = 10L
    const val FREE_SMITHING_LEVEL = 60

    /** Wiki "Coffer (Blast Furnace)": the dwarves cost 12 coins per tick (72,000 an hour), spent only inside the basement. */
    const val COFFER_COINS_PER_TICK = 12
    const val COFFER_MAX = Int.MAX_VALUE / 2

    /** Wiki "Pedals": 1 Agility xp and 0.5% run energy (50 of 10000) per tick, level 30; "Pump": 2 Strength xp per tick, level 30. */
    const val PEDAL_ENERGY = 50
    const val PEDAL_AGILITY_LEVEL = 30

    /** Wiki "Stove": 5 Firemaking xp per spadeful, 30 spadefuls fill it; level 30. */
    const val STOVE_FULL = 30
    const val STOVE_XP = 5.0

    /** Wiki "Cogs", "Drive belt": 50 Crafting xp for a repair with a hammer, level 30. */
    const val REPAIR_XP = 50.0

    /** Wiki "Bar dispenser" > Contribution experience: 1.5 Smithing xp to helpers, 10 ticks apart (manual operation only). */
    const val CONTRIBUTION_XP = 1.5

    /** Wiki "Goldsmith gauntlets": 56.2 xp per gold bar in the Blast Furnace too (Wiki "Blast Furnace" > Experience rates). */
    const val GOLDSMITH_GOLD_XP = 56.2

    /** Basement map square (cache `map 30 77`, region 7757): the fee and the machinery run only inside it. */
    const val MIN_X = 1920
    const val MAX_X = 1983
    const val MIN_Z = 4928
    const val MAX_Z = 4991

    fun inside(c: CoordGrid): Boolean = c.x in MIN_X..MAX_X && c.z in MIN_Z..MAX_Z

    /** Dispenser loc variants (varbit 936 morph: empty, animating, full, cooled), all with "Take" and "Check". */
    val DISPENSERS: IntArray =
        intArrayOf(
            Locs.BLAST_FURNACE_ORE_DISPENSER_EMPTY,
            Locs.BLAST_FURNACE_ORE_DISPENSER_FORANIM,
            Locs.BLAST_FURNACE_ORE_DISPENSER_FULL,
            Locs.BLAST_FURNACE_ORE_DISPENSER_COOLED,
        )

    /** The stove in its three fill states (all "Refuel"). */
    val STOVES: IntArray = intArrayOf(Locs.BLAST_FURNACE_STOVE_LOW, Locs.BLAST_FURNACE_STOVE_MEDIUM, Locs.BLAST_FURNACE_STOVE_FULL)

    /** First cogs and drive belt, working and broken (cache locs 9102 / 9103 at (1944, 4967) and 9104 / 9105 at (1945, 4967)). */
    val BELT_AT = CoordGrid(1944, 4967, 0)
    val COGS_AT = CoordGrid(1945, 4967, 0)
    const val PART_SHAPE = 10
}
