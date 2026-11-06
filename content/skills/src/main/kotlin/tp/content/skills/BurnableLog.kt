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
