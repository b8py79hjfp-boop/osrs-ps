@file:Suppress("MagicNumber") // data table: levels and xp from the OSRS Wiki "Firemaking" (docs/mechanics/firemaking.md FM2)

package tp.content.skills

import tp.symbols.Locs
import tp.symbols.Objs

/** A log a tinderbox can light: the Firemaking level it needs and the xp a lit one gives. */
internal class BurnableLog(val obj: Int, val level: Int, val xp: Double)

/** The Wiki's "List of combustible logs" (https://oldschool.runescape.wiki/w/Firemaking#List_of_combustible_logs). */
internal val BURNABLE_LOGS: List<BurnableLog> =
    listOf(
        BurnableLog(Objs.LOGS, 1, 40.0),
        BurnableLog(Objs.ACHEY_TREE_LOGS, 1, 40.0),
        BurnableLog(Objs.OAK_LOGS, 15, 60.0),
        BurnableLog(Objs.WILLOW_LOGS, 30, 90.0),
        BurnableLog(Objs.TEAK_LOGS, 35, 105.0),
        BurnableLog(Objs.JATOBA_LOGS, 40, 120.0),
        BurnableLog(Objs.ARCTIC_PINE_LOG, 42, 125.0),
        BurnableLog(Objs.MAPLE_LOGS, 45, 135.0),
        BurnableLog(Objs.MAHOGANY_LOGS, 50, 157.5),
        BurnableLog(Objs.YEW_LOGS, 60, 202.5),
        BurnableLog(Objs.BLISTERWOOD_LOGS, 62, 96.0),
        BurnableLog(Objs.CAMPHOR_LOGS, 66, 180.0),
        BurnableLog(Objs.MAGIC_LOGS, 75, 303.8),
        BurnableLog(Objs.IRONWOOD_LOGS, 80, 220.5),
        BurnableLog(Objs.REDWOOD_LOGS, 90, 350.0),
        BurnableLog(Objs.ROSEWOOD_LOGS, 92, 268.0),
    )

/** The fire a lit log becomes (cache loc `fire`; coloured fires belong to coloured logs, not implemented). */
internal const val FIRE_LOC: Int = Locs.FIRE

/**
 * A coloured log (docs/mechanics/firemaking.md FM6): a plain log coated with a gnomish [firelighter] burns with the
 * matching coloured [fire] loc, never fails to light and gives 50 xp.
 */
internal class ColouredLog(val log: Int, val fire: Int, val firelighter: Int, val colour: String)

/** Cache obj ids found with `cache-dump config obj --all`: purple 10326 / white 10327 firelighters, white 10328 / purple 10329 logs. */
private const val PURPLE_FIRELIGHTER = 10326
private const val WHITE_FIRELIGHTER = 10327
private const val WHITE_LOGS = 10328
private const val PURPLE_LOGS = 10329

/** The five gnomish firelighter colours (Wiki "Gnomish firelighters"); white and purple fire locs are the cache's `trail_*_fire`. */
internal val COLOURED_LOGS: List<ColouredLog> =
    listOf(
        ColouredLog(Objs.RED_LOGS, Locs.RED_FIRE, Objs.GNOMISH_FIRELIGHTER_RED, "red"),
        ColouredLog(Objs.GREEN_LOGS, Locs.GREEN_FIRE, Objs.GNOMISH_FIRELIGHTER_GREEN, "green"),
        ColouredLog(Objs.BLUE_LOGS, Locs.BLUE_FIRE, Objs.GNOMISH_FIRELIGHTER_BLUE, "blue"),
        ColouredLog(WHITE_LOGS, Locs.TRAIL_WHITE_FIRE, WHITE_FIRELIGHTER, "white"),
        ColouredLog(PURPLE_LOGS, Locs.TRAIL_PURPLE_FIRE, PURPLE_FIRELIGHTER, "purple"),
    )
