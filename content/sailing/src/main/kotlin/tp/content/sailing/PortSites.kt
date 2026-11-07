package tp.content.sailing

import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Which dock a notice board, ledger table, port master or reward bag belongs to (docs/mechanics/sailing-port-tasks.md PT1).
 * The cache names these locs and NPCs per port (`PORT_TASK_BOARD_*`, `DOCK_LOADING_BAY_LEDGER_TABLE_*`,
 * `PORT_TASK_MASTER_*`, `PORT_TASK_LOOTSACK_T0_*`) but links none of them to a `Sailing_Dock` row, so the order of the
 * symbol blocks is listed here by dock name and joined to db 194 by name with punctuation ignored, as [DockSites] does.
 */
internal class PortSites(docks: List<Dock>) {
    private val dockByName: Map<String, Int> = docks.associate { normalise(it.name) to it.id }

    private val boardByLoc: Map<Int, Int> = index(Locs.PORT_TASK_BOARD_PORT_SARIM, BOARDS)
    private val ledgerByLoc: Map<Int, Int> = index(Locs.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_SARIM, LEDGERS)
    private val bagByDock: Map<Int, Int> =
        BAGS.mapNotNull { (name, obj) -> dockByName[normalise(name)]?.let { it to obj } }.toMap()

    /** Dock id of a notice board loc, or null. */
    fun ofBoard(loc: Int): Int? = boardByLoc[loc]

    /** Dock id of a ledger table loc (base id, before the client resolves the take / deposit variant), or null. */
    fun ofLedger(loc: Int): Int? = ledgerByLoc[loc]

    /** Obj id of the tier-0 (tiny) port reward bag of [dockId], or null if the port has none. */
    fun rewardBag(dockId: Int): Int? = bagByDock[dockId]

    private fun index(first: Int, names: List<String>): Map<Int, Int> =
        names.withIndex().mapNotNull { (i, name) -> dockByName[normalise(name)]?.let { (first + i) to it } }.toMap()

    companion object {
        /** NPC ids of the port masters (`PORT_TASK_MASTER_*`, one per port including Mos Le'Harmless, which is no dock). */
        val MASTERS: IntRange = Npcs.PORT_TASK_MASTER_PORT_SARIM..Npcs.PORT_TASK_MASTER_LUNAR_ISLE

        /** Loc ids of the notice boards and of the ledger tables, in symbol order. */
        val BOARD_LOCS: IntRange = Locs.PORT_TASK_BOARD_PORT_SARIM..Locs.PORT_TASK_BOARD_LUNAR_ISLE
        val LEDGER_LOCS: IntRange = Locs.DOCK_LOADING_BAY_LEDGER_TABLE_PORT_SARIM..Locs.DOCK_LOADING_BAY_LEDGER_TABLE_LUNAR_ISLE

        /** Loc ids of the cargo holds `SAILING_BOAT_CARGO_HOLD_*`: 7 tiers x raft, 2x5, large, two ids apart. */
        val HOLD_LOCS: List<Int> = (0 until HOLD_VARIANTS).map { Locs.SAILING_BOAT_CARGO_HOLD_REGULAR_RAFT + it * 2 }

        private const val HOLD_VARIANTS = 21

        /** `PORT_TASK_BOARD_PORT_SARIM .. _LUNAR_ISLE` in symbol order. */
        private val BOARDS =
            listOf(
                "Port Sarim",
                "The Pandemonium",
                "Catherby",
                "Port Khazard",
                "Ruins of Unkah",
                "Rellekka",
                "Port Piscarilius",
                "Civitas illa Fortis",
                "Land's End",
                "Musa Point",
                "Ardougne",
                "Brimhaven",
                "Corsair Cove",
                "The Summer Shore",
                "Aldarin",
                "Void Knights' Outpost",
                "Port Roberts",
                "Red Rock",
                "Etceteria",
                "Port Tyras",
                "Deepfin Point",
                "Prifddinas",
                "Lunar Isle",
            )

        /** `DOCK_LOADING_BAY_LEDGER_TABLE_PORT_SARIM .. _LUNAR_ISLE` in symbol order. */
        private val LEDGERS =
            listOf(
                "Port Sarim",
                "Musa Point",
                "The Pandemonium",
                "Catherby",
                "Entrana",
                "Ardougne",
                "Brimhaven",
                "Port Khazard",
                "Corsair Cove",
                "Ruins of Unkah",
                "Port Piscarilius",
                "Civitas illa Fortis",
                "Rellekka",
                "Land's End",
                "Hosidius",
                "Cairn Isle",
                "Sunset Coast",
                "The Summer Shore",
                "Aldarin",
                "Void Knights' Outpost",
                "Port Roberts",
                "Red Rock",
                "Etceteria",
                "Neitiznot",
                "Jatizso",
                "Port Tyras",
                "Deepfin Point",
                "Prifddinas",
                "Piscatoris",
                "Lunar Isle",
            )

        /** Tier 0 of each port's reward bag; tier t is two ids further (the placeholder obj sits between). */
        private val BAGS =
            listOf(
                "Cairn Isle" to Objs.PORT_TASK_LOOTSACK_T0_CAIRN_ISLE,
                "Entrana" to Objs.PORT_TASK_LOOTSACK_T0_ENTRANA,
                "Neitiznot" to Objs.PORT_TASK_LOOTSACK_T0_NEITIZNOT,
                "Jatizso" to Objs.PORT_TASK_LOOTSACK_T0_JATIZSO,
                "Hosidius" to Objs.PORT_TASK_LOOTSACK_T0_HOSIDIUS,
                "Sunset Coast" to Objs.PORT_TASK_LOOTSACK_T0_SUNSET_COAST,
                "Piscatoris" to Objs.PORT_TASK_LOOTSACK_T0_PISCATORIS,
                "Etceteria" to Objs.PORT_TASK_LOOTSACK_T0_ETCETERIA,
                "Ruins of Unkah" to Objs.PORT_TASK_LOOTSACK_T0_RUINS_OF_UNKAH,
                "Lunar Isle" to Objs.PORT_TASK_LOOTSACK_T0_LUNAR_ISLE,
                "Corsair Cove" to Objs.PORT_TASK_LOOTSACK_T0_CORSAIR_COVE,
                "Prifddinas" to Objs.PORT_TASK_LOOTSACK_T0_PRIFDDINAS,
                "Land's End" to Objs.PORT_TASK_LOOTSACK_T0_LANDS_END,
                "Aldarin" to Objs.PORT_TASK_LOOTSACK_T0_ALDARIN,
                "The Summer Shore" to Objs.PORT_TASK_LOOTSACK_T0_SUMMER_SHORE,
                "Musa Point" to Objs.PORT_TASK_LOOTSACK_T0_MUSA_POINT,
                "Rellekka" to Objs.PORT_TASK_LOOTSACK_T0_RELLEKKA,
                "Void Knights' Outpost" to Objs.PORT_TASK_LOOTSACK_T0_VOID_KNIGHTS_OUTPOST,
                "The Pandemonium" to Objs.PORT_TASK_LOOTSACK_T0_THE_PANDEMONIUM,
                "Red Rock" to Objs.PORT_TASK_LOOTSACK_T0_RED_ROCK,
                "Brimhaven" to Objs.PORT_TASK_LOOTSACK_T0_BRIMHAVEN,
                "Port Tyras" to Objs.PORT_TASK_LOOTSACK_T0_PORT_TYRAS,
                "Catherby" to Objs.PORT_TASK_LOOTSACK_T0_CATHERBY,
                "Civitas illa Fortis" to Objs.PORT_TASK_LOOTSACK_T0_CIVITAS_ILLA_FORTIS,
                "Ardougne" to Objs.PORT_TASK_LOOTSACK_T0_ARDOUGNE,
                "Port Khazard" to Objs.PORT_TASK_LOOTSACK_T0_PORT_KHAZARD,
                "Deepfin Point" to Objs.PORT_TASK_LOOTSACK_T0_DEEPFIN_POINT,
                "Port Sarim" to Objs.PORT_TASK_LOOTSACK_T0_PORT_SARIM,
                "Port Piscarilius" to Objs.PORT_TASK_LOOTSACK_T0_PORT_PISCARILIUS,
                "Port Roberts" to Objs.PORT_TASK_LOOTSACK_T0_PORT_ROBERTS,
            )

        fun normalise(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
    }
}
