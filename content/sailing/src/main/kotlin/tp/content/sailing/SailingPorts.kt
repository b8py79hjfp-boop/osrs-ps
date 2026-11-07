package tp.content.sailing

import tp.util.CoordGrid

/**
 * One row of `Sailing_Dock` (db 194): a mooring point. Only identity, level and quest gating are in the shipped cache; the
 * land and sea coordinates are server-only and come from the Wiki import (`docks.tsv`, docs/mechanics/sailing-ports.md P1).
 */
internal class Dock(
    val row: Int,
    /** `DOCK_ID` (0 Port Sarim, 1 The Pandemonium ...). */
    val id: Int,
    val name: String,
    val inlineName: String?,
    val type: Int?,
    val levelRequired: Int,
    /** Quest db row needed to dock, or null. */
    val questRow: Int?,
    val portTaskQuestRow: Int?,
    val sprite: Int?,
)

/** Row of `Sailing_Sea` (db 201): a sea, an ocean, or a bonus group of the charting log. */
internal class Sea(
    val row: Int,
    /** `UNIQUE_ID`, also the suffix of the `SAILING_SEA_*` charting varbits. */
    val uniqueId: Int,
    val name: String,
    /** `TYPE` column: 0 sea, 1 ocean, 2 bonus group (Drinks, Currents, Dives, Weather), 3 the Miscellaneous group (cache 2757). */
    val type: Int,
    /** `OCEAN_ID` (set on the ocean rows only), or null. */
    val oceanId: Int?,
    /** Row of the ocean this sea belongs to, or null for oceans. */
    val oceanRow: Int?,
    val requiredLevel: Int,
    /** `XP_LEVEL`: set on two rows only, meaning OPEN. */
    val xpLevel: Int?,
    val centre: CoordGrid?,
    val hasChartingRapids: Boolean,
)

/** Row of `Sailing_Sea_Hazard` (db 200). Damage, frequency and areas are server-only, from the Wiki (sailing-movement.md). */
internal class SeaHazard(val row: Int, val id: Int, val name: String)

/**
 * One row of `Port_Task` (db 197): a courier (type 0) or bounty (type 1) task. XP, coin rewards, facility and combat
 * requirements are server-only columns; the per-task XP is imported from the Wiki into `port-task-xp.tsv`.
 */
internal class PortTask(
    val row: Int,
    val id: Int,
    val name: String,
    val type: Int,
    val levelRequired: Int,
    /** Dock ids (`Dock.id`) of the starting, cargo and ending port, or -1 if the row has none. */
    val startingDock: Int,
    val cargoDock: Int,
    val endingDock: Int,
    val cancellable: Boolean,
    /** Courier cargo (crate obj and quantity). */
    val cargo: List<Material>,
    val bountyTargetAlive: Int?,
    val bountyTargetDead: Int?,
    val bountyObj: Int?,
    val bountyAmount: Int?,
    /** Bounty drop rarity as 1/n. */
    val bountyRarity: Int?,
    val boardGraphic: Int?,
    val flavourText: String?,
) {
    val isCourier: Boolean get() = type == TYPE_COURIER

    val isBounty: Boolean get() = type == TYPE_BOUNTY

    companion object {
        /** `TASK_TYPE` of a courier task (cache `Port_Task`, docs/plans/sailing.md 4.2). */
        const val TYPE_COURIER: Int = 0

        /** `TASK_TYPE` of a bounty task. */
        const val TYPE_BOUNTY: Int = 1
    }
}
