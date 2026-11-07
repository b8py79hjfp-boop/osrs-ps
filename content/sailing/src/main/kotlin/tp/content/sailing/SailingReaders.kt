package tp.content.sailing

import tp.plugin.DbRowRef
import tp.plugin.WorldRef
import tp.symbols.DbTables.Boat_Selection_Type as SelectionT
import tp.symbols.DbTables.Port_Task as TaskT
import tp.symbols.DbTables.Sailing_Boat as BoatT
import tp.symbols.DbTables.Sailing_Boat_Cargohold_Whitelist_Obj as WhitelistT
import tp.symbols.DbTables.Sailing_Bt_Trial_Core as TrialT
import tp.symbols.DbTables.Sailing_Charting_Core as CoreT
import tp.symbols.DbTables.Sailing_Charting_Generic as GenericT
import tp.symbols.DbTables.Sailing_Charting_Tool_Recovery as ToolT
import tp.symbols.DbTables.Sailing_Crew as CrewT
import tp.symbols.DbTables.Sailing_Dock as DockT
import tp.symbols.DbTables.Sailing_Sea as SeaT
import tp.symbols.DbTables.Sailing_Sea_Hazard as HazardT

/** Readers of the boat, port, sea, charting, trial and crew tables (docs/plans/sailing.md 6.2). */
internal object SailingReaders {
    /** Slot of the hotspot row in the `Sailing_Boat.HOTSPOT` tuple `(coord, a, b, hotspot row)`. */
    private const val HOTSPOT_A = 1
    private const val HOTSPOT_B = 2
    private const val HOTSPOT_ROW = 3

    /** Every row of `Sailing_Boat` (db 166). */
    fun boats(world: WorldRef): List<Boat> =
        world.dbRows(BoatT.ID).map { row ->
            Boat(
                type = row.zero(BoatT.COL_TYPE_ID),
                row = row.id,
                name = row.string(BoatT.COL_NAME).orEmpty(),
                inlineName = row.string(BoatT.COL_INLINE_NAME).orEmpty(),
                displayName = row.string(BoatT.COL_DISPLAYNAME),
                levelRequired = row.zero(BoatT.COL_LEVEL_REQUIRED),
                playerCapacity = row.zero(BoatT.COL_PLAYER_CAPACITY),
                size = row.zero(BoatT.COL_BOAT_SIZE),
                sizeDescription = row.string(BoatT.COL_SIZE_DESC).orEmpty(),
                combinedNavigation = row.flag(BoatT.COL_COMBINED_NAVIGATION),
                retrievalCost = row.zero(BoatT.COL_RETRIEVAL_COST),
                crewCapacity = row.zero(BoatT.COL_CREW_CAPACITY),
                facilityAmount = row.zero(BoatT.COL_FACILITY_AMOUNT),
                sameFacilitiesPortAndStarboard = row.flag(BoatT.COL_SAME_FACILITIES_PORT_AND_STARBOARD),
                temporary = row.flag(BoatT.COL_TEMPORARY_BOAT),
                sprite = row.int(BoatT.COL_BOAT_SPRITE),
                keelRows = row.ints(BoatT.COL_KEEL_OPTION),
                hullRows = row.ints(BoatT.COL_HULL_OPTION),
                sailRows = row.ints(BoatT.COL_SAIL_OPTION),
                sailPatternRows = row.ints(BoatT.COL_SAIL_PATTERN_OPTION),
                helmRows = row.ints(BoatT.COL_STEERING_OPTION),
                flagRows = row.ints(BoatT.COL_FLAG_OPTION),
                trimRows = row.ints(BoatT.COL_TRIM_OPTION),
                brazierRows = row.ints(BoatT.COL_BRAZIER_OPTION),
                hotspots = hotspotSlots(row),
                staticFacility = row.placement(BoatT.COL_STATIC_FACILITY, PlacementLayout.COORD_FIRST),
            )
        }

    private fun hotspotSlots(row: DbRowRef): List<HotspotSlot> {
        val column = BoatT.COL_HOTSPOT
        val out = ArrayList<HotspotSlot>()
        var field = 0
        while (true) {
            val tile = row.coord(column, field) ?: break
            out +=
                HotspotSlot(
                    tile,
                    row.int(column, field, HOTSPOT_A) ?: 0,
                    row.int(column, field, HOTSPOT_B) ?: 0,
                    row.int(column, field, HOTSPOT_ROW) ?: -1,
                )
            field++
        }
        return out
    }

    /** Every row of `Sailing_Dock` (db 194). */
    fun docks(world: WorldRef): List<Dock> =
        world.dbRows(DockT.ID).map { row ->
            Dock(
                row = row.id,
                id = row.zero(DockT.COL_DOCK_ID),
                name = row.string(DockT.COL_NICE_NAME).orEmpty(),
                inlineName = row.string(DockT.COL_INLINE_NAME),
                type = row.int(DockT.COL_TYPE),
                levelRequired = row.zero(DockT.COL_LEVEL_REQUIRED),
                questRow = row.int(DockT.COL_QUEST_REQUIRED),
                portTaskQuestRow = row.int(DockT.COL_PORT_TASK_QUEST_REQUIRED),
                sprite = row.int(DockT.COL_DOCK_SPRITE_SMALL),
            )
        }

    /** Every row of `Sailing_Sea` (db 201). */
    fun seas(world: WorldRef): List<Sea> =
        world.dbRows(SeaT.ID).map { row ->
            Sea(
                row = row.id,
                uniqueId = row.zero(SeaT.COL_UNIQUE_ID),
                name = row.string(SeaT.COL_NAME).orEmpty(),
                type = row.zero(SeaT.COL_TYPE),
                oceanId = row.int(SeaT.COL_OCEAN_ID),
                oceanRow = row.int(SeaT.COL_OCEAN),
                requiredLevel = row.zero(SeaT.COL_REQUIRED_LEVEL),
                xpLevel = row.int(SeaT.COL_XP_LEVEL),
                centre = row.coord(SeaT.COL_CENTRE_COORD),
                hasChartingRapids = row.flag(SeaT.COL_HAS_CHARTING_RAPIDS),
            )
        }

    /** Every row of `Sailing_Sea_Hazard` (db 200). */
    fun hazards(world: WorldRef): List<SeaHazard> =
        world.dbRows(HazardT.ID).map {
            SeaHazard(it.id, it.zero(HazardT.COL_HAZARD_ID), it.string(HazardT.COL_NAME).orEmpty())
        }

    /** Every row of `Port_Task` (db 197); [dockIdByRow] maps `Sailing_Dock` row ids to dock ids. */
    fun portTasks(world: WorldRef, dockIdByRow: Map<Int, Int>): List<PortTask> =
        world.dbRows(TaskT.ID).map { row ->
            PortTask(
                row = row.id,
                id = row.zero(TaskT.COL_TASK_ID),
                name = row.string(TaskT.COL_NAME).orEmpty(),
                type = row.zero(TaskT.COL_TASK_TYPE),
                levelRequired = row.zero(TaskT.COL_LEVEL_REQUIRED),
                startingDock = row.int(TaskT.COL_STARTING_PORT)?.let { dockIdByRow[it] } ?: -1,
                cargoDock = row.int(TaskT.COL_CARGO_PORT)?.let { dockIdByRow[it] } ?: -1,
                endingDock = row.int(TaskT.COL_ENDING_PORT)?.let { dockIdByRow[it] } ?: -1,
                // Unset on 605 of 606 rows; the single row that sets it sets 0, so unset means cancellable.
                cancellable = row.int(TaskT.COL_CANCELLABLE) != 0,
                cargo = row.materials(TaskT.COL_CARGO),
                bountyTargetAlive = row.int(TaskT.COL_BOUNTY_TARGET_ALIVE),
                bountyTargetDead = row.int(TaskT.COL_BOUNTY_TARGET_DEAD),
                bountyObj = row.int(TaskT.COL_BOUNTY_OBJECT),
                bountyAmount = row.int(TaskT.COL_BOUNTY_OBJECT_AMOUNT),
                bountyRarity = row.int(TaskT.COL_BOUNTY_OBJECT_RARITY),
                boardGraphic = row.int(TaskT.COL_TASK_BOARD_GRAPHIC),
                flavourText = row.string(TaskT.COL_FLAVOUR_TEXT),
            )
        }

    /** Every row of `Sailing_Charting_Core` (db 154) joined with its type table row. */
    fun chartingTasks(world: WorldRef): List<ChartingTask> {
        val typeRows = HashMap<Int, Int>()
        for (type in ChartingType.entries) {
            for (row in world.dbRows(type.tableId)) {
                row.int(GenericT.COL_SAILING_CHARTING_CORE)?.let { typeRows[it] = row.id }
            }
        }
        return world.dbRows(CoreT.ID).mapNotNull { row ->
            val type = ChartingType.ofTable(row.int(CoreT.COL_CHARTING_TYPE) ?: return@mapNotNull null) ?: return@mapNotNull null
            ChartingTask(
                row = row.id,
                uniqueId = row.zero(CoreT.COL_UNIQUE_ID),
                hint = row.string(CoreT.COL_HINT).orEmpty(),
                type = type,
                typeRow = typeRows[row.id],
                seaRow = row.int(CoreT.COL_SAILING_SEA) ?: return@mapNotNull null,
                secondarySeaRow = row.int(CoreT.COL_SAILING_SEA_SECONDARY),
                giveRepeatXp = row.flag(CoreT.COL_GIVE_REPEAT_XP),
                hazardRow = row.int(CoreT.COL_HAZARD),
                requiredDockRow = row.int(CoreT.COL_REQUIRED_DOCK),
            )
        }
    }

    /** Every row of `Sailing_Charting_Tool_Recovery` (db 161). */
    fun chartingTools(world: WorldRef): List<ChartingTool> =
        world.dbRows(ToolT.ID).map {
            ChartingTool(
                it.id,
                it.string(ToolT.COL_NAME).orEmpty(),
                it.string(ToolT.COL_INLINE_NAME),
                it.ints(ToolT.COL_TOOL),
                it.zero(ToolT.COL_UNIQUE_ID),
            )
        }

    /** Slots of the `RANK_DATA` tuple. */
    private const val RANK_TICKS = 0
    private const val RANK_LAPS = 1
    private const val RANK_SUPPLIES = 2
    private const val RANK_BONUS = 3
    private const val RANK_XP = 4

    private val TRACKER_ICON_COLUMNS =
        listOf(TrialT.COL_TRACKER_ICON1, TrialT.COL_TRACKER_ICON2, TrialT.COL_TRACKER_ICON3, TrialT.COL_TRACKER_ICON4)

    /** Every row of `Sailing_Bt_Trial_Core` (db 143); the trial index is the row's position. */
    fun trials(world: WorldRef): List<BarracudaTrial> =
        world.dbRows(TrialT.ID).mapIndexed { index, row ->
            val ranks = ArrayList<BarracudaRank>()
            var field = 0
            while (row.int(TrialT.COL_RANK_DATA, field, RANK_TICKS) != null) {
                ranks +=
                    BarracudaRank(
                        rank = field,
                        targetTicks = row.zero(TrialT.COL_RANK_DATA, field, RANK_TICKS),
                        laps = row.zero(TrialT.COL_RANK_DATA, field, RANK_LAPS),
                        supplies = row.zero(TrialT.COL_RANK_DATA, field, RANK_SUPPLIES),
                        bonusXpTenths = row.zero(TrialT.COL_RANK_DATA, field, RANK_BONUS),
                        trialXpTenths = row.zero(TrialT.COL_RANK_DATA, field, RANK_XP),
                    )
                field++
            }
            BarracudaTrial(
                row = row.id,
                index = index,
                name = row.string(TrialT.COL_TRIAL_NAME).orEmpty(),
                trackerIcons = TRACKER_ICON_COLUMNS.mapNotNull { row.int(it) },
                primaryRequirement = row.string(TrialT.COL_PRIMARY_REQ_DESC).orEmpty(),
                secondaryRequirement = row.string(TrialT.COL_SECONDARY_REQ_DESC).orEmpty(),
                ranks = ranks,
                rewards = listOf(TrialT.COL_REWARD_SWORDFISH, TrialT.COL_REWARD_SHARK, TrialT.COL_REWARD_MARLIN).mapNotNull { row.int(it) },
            )
        }

    private fun DbRowRef.zero(column: Int, field: Int, component: Int): Int = int(column, field, component) ?: 0

    /** Every row of `Sailing_Crew` (db 173). */
    fun crew(world: WorldRef): List<CrewMember> =
        world.dbRows(CrewT.ID).map {
            CrewMember(
                row = it.id,
                uniqueId = it.zero(CrewT.COL_UNIQUE_ID),
                boatNpc = it.zero(CrewT.COL_BOAT_NPC),
                cargoNpcs = it.ints(CrewT.COL_CARGO_NPC),
                sprite = it.int(CrewT.COL_SPRITE),
                helmsmanship = it.zero(CrewT.COL_STAT_HELMSMANSHIP),
                privateering = it.zero(CrewT.COL_STAT_PRIVATEERING),
                deckhandiness = it.zero(CrewT.COL_STAT_DECKHANDINESS),
            )
        }

    /** Every row of `Boat_Selection_Type` (db 149). */
    fun boatSelectionTypes(world: WorldRef): List<BoatSelectionType> =
        world.dbRows(SelectionT.ID).map {
            BoatSelectionType(
                type = it.zero(SelectionT.COL_TYPE),
                title = it.string(SelectionT.COL_TITLE).orEmpty(),
                selectionText = it.string(SelectionT.COL_SELECTION_TEXT),
                selectionEnabled = it.flag(SelectionT.COL_SELECTION_ENABLED),
                showRecentBoat = it.flag(SelectionT.COL_SHOW_RECENT_BOAT),
                showRetrievalCost = it.flag(SelectionT.COL_SHOW_RETRIEVAL_COST),
                showBoatHealth = it.flag(SelectionT.COL_SHOW_BOAT_HEALTH),
                showEmptyBoatSlots = it.flag(SelectionT.COL_SHOW_EMPTY_BOAT_SLOTS),
                showCargoHold = it.flag(SelectionT.COL_SHOW_CARGO_HOLD),
                bottledNotAllowed = it.flag(SelectionT.COL_BOTTLED_SHIPS_NOT_ALLOWED),
                capsizedNotAllowed = it.flag(SelectionT.COL_CAPSIZED_SHIPS_NOT_ALLOWED),
                lostNotAllowed = it.flag(SelectionT.COL_LOST_SHIPS_NOT_ALLOWED),
                onlyBoatsAtCurrentPort = it.flag(SelectionT.COL_ONLY_BOATS_AT_CURRENT_PORT),
                requiresTeleportFocus = it.flag(SelectionT.COL_REQUIRES_TELEPORT_FOCUS),
                requiresGreaterTeleportFocus = it.flag(SelectionT.COL_REQUIRES_GREATER_TELEPORT_FOCUS),
                requiresEmptyCargoHold = it.flag(SelectionT.COL_REQUIRES_EMPTY_CARGO_HOLD),
            )
        }

    /** Objs of `Sailing_Boat_Cargohold_Whitelist_Obj` (db 196) that may be stored in a boat's cargo hold. */
    fun cargoWhitelist(world: WorldRef): Set<Int> =
        world
            .dbRows(WhitelistT.ID)
            .flatMap { it.ints(WhitelistT.COL_ENTRY) }
            .toSet()

    /** Obj categories of `Sailing_Boat_Cargohold_Whitelist_Obj` (db 196) that may be stored in a boat's cargo hold. */
    fun cargoCategories(world: WorldRef): Set<Int> =
        world
            .dbRows(WhitelistT.ID)
            .flatMap { it.ints(WhitelistT.COL_ENTRY_CATEGORY) }
            .toSet()
}
