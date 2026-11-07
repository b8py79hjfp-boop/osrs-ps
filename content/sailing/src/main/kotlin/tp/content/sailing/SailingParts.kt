package tp.content.sailing

import tp.plugin.DbRowRef
import tp.plugin.WorldRef
import tp.symbols.DbTables.Sailing_Boat_Brazier as BrazierT
import tp.symbols.DbTables.Sailing_Boat_Facility as FacilityT
import tp.symbols.DbTables.Sailing_Boat_Facility_Stats as StatsT
import tp.symbols.DbTables.Sailing_Boat_Flag as FlagT
import tp.symbols.DbTables.Sailing_Boat_Hotspot as HotspotT
import tp.symbols.DbTables.Sailing_Boat_Hull as HullT
import tp.symbols.DbTables.Sailing_Boat_Keel as KeelT
import tp.symbols.DbTables.Sailing_Boat_Sail as SailT
import tp.symbols.DbTables.Sailing_Boat_Sail_Pattern as PatternT
import tp.symbols.DbTables.Sailing_Boat_Steering as HelmT
import tp.symbols.DbTables.Sailing_Boat_Trim as TrimT

/** The column ids of one core part table (every part table shares the names but not the numbers). */
private data class PartLayout(
    val table: Int,
    val kind: PartKind,
    val name: Int,
    val description: Int,
    val loc: Int,
    /** Tuple layout of [loc], or null if [loc] is a plain loc cell (hulls). */
    val placement: PlacementLayout?,
    val requirements: RequirementColumns,
    /** Stats link column, or -1 when the table has none. */
    val stats: Int,
    /** Crew requirement column, or -1. */
    val crew: Int,
)

/** The five build requirement columns every part table has (ids differ per table). */
private data class RequirementColumns(val sailing: Int, val construction: Int, val coins: Int, val quest: Int, val material: Int)

/** Readers of the core part tables (db 177-186, 230) and the facility, hotspot and stats tables (db 176, 175, 164). */
internal object SailingParts {
    private fun layout(kind: PartKind): PartLayout =
        when (kind) {
            PartKind.HULL -> hullLayout()
            PartKind.SAIL -> sailLayout()
            PartKind.HELM -> helmLayout()
            PartKind.KEEL -> keelLayout()
            PartKind.FLAG -> flagLayout()
            PartKind.BRAZIER -> brazierLayout()
            PartKind.TRIM -> trimLayout()
            PartKind.SAIL_PATTERN -> patternLayout()
        }

    private fun hullLayout() =
        PartLayout(
            HullT.ID,
            PartKind.HULL,
            HullT.COL_NAME,
            HullT.COL_DESCRIPTION,
            HullT.COL_LOC,
            null,
            RequirementColumns(
                HullT.COL_SAILING_REQUIREMENT,
                HullT.COL_CONSTRUCTION_REQUIREMENT,
                HullT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                HullT.COL_QUEST_REQUIREMENT,
                HullT.COL_MATERIAL,
            ),
            HullT.COL_FACILITY_STATS,
            -1,
        )

    private fun sailLayout() =
        PartLayout(
            SailT.ID,
            PartKind.SAIL,
            SailT.COL_NAME,
            SailT.COL_DESCRIPTION,
            SailT.COL_LOC,
            PlacementLayout.LOC_FIRST,
            RequirementColumns(
                SailT.COL_SAILING_REQUIREMENT,
                SailT.COL_CONSTRUCTION_REQUIREMENT,
                SailT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                SailT.COL_QUEST_REQUIREMENT,
                SailT.COL_MATERIAL,
            ),
            SailT.COL_FACILITY_STATS,
            -1,
        )

    private fun helmLayout() =
        PartLayout(
            HelmT.ID,
            PartKind.HELM,
            HelmT.COL_NAME,
            HelmT.COL_DESCRIPTION,
            HelmT.COL_LOC,
            PlacementLayout.COORD_FIRST,
            RequirementColumns(
                HelmT.COL_SAILING_REQUIREMENT,
                HelmT.COL_CONSTRUCTION_REQUIREMENT,
                HelmT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                HelmT.COL_QUEST_REQUIREMENT,
                HelmT.COL_MATERIAL,
            ),
            HelmT.COL_FACILITY_STATS,
            HelmT.COL_CREW_STAT_REQUIREMENT,
        )

    private fun keelLayout() =
        PartLayout(
            KeelT.ID,
            PartKind.KEEL,
            KeelT.COL_NAME,
            KeelT.COL_DESCRIPTION,
            KeelT.COL_LOC,
            PlacementLayout.COORD_FIRST,
            RequirementColumns(
                KeelT.COL_SAILING_REQUIREMENT,
                KeelT.COL_CONSTRUCTION_REQUIREMENT,
                KeelT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                KeelT.COL_QUEST_REQUIREMENT,
                KeelT.COL_MATERIAL,
            ),
            KeelT.COL_FACILITY_STATS,
            -1,
        )

    private fun flagLayout() =
        PartLayout(
            FlagT.ID,
            PartKind.FLAG,
            FlagT.COL_NAME,
            FlagT.COL_DESCRIPTION,
            FlagT.COL_LOC,
            PlacementLayout.COORD_FIRST,
            RequirementColumns(
                FlagT.COL_SAILING_REQUIREMENT,
                FlagT.COL_CONSTRUCTION_REQUIREMENT,
                FlagT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                FlagT.COL_QUEST_REQUIREMENT,
                FlagT.COL_MATERIAL,
            ),
            FlagT.COL_FACILITY_STATS,
            -1,
        )

    private fun brazierLayout() =
        PartLayout(
            BrazierT.ID,
            PartKind.BRAZIER,
            BrazierT.COL_NAME,
            BrazierT.COL_DESCRIPTION,
            BrazierT.COL_LOC,
            PlacementLayout.COORD_FIRST,
            RequirementColumns(
                BrazierT.COL_SAILING_REQUIREMENT,
                BrazierT.COL_CONSTRUCTION_REQUIREMENT,
                BrazierT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                BrazierT.COL_QUEST_REQUIREMENT,
                BrazierT.COL_MATERIAL,
            ),
            BrazierT.COL_FACILITY_STATS,
            -1,
        )

    private fun trimLayout() =
        PartLayout(
            TrimT.ID,
            PartKind.TRIM,
            TrimT.COL_NAME,
            TrimT.COL_DESCRIPTION,
            TrimT.COL_LOC,
            PlacementLayout.COORD_FIRST,
            RequirementColumns(
                TrimT.COL_SAILING_REQUIREMENT,
                TrimT.COL_CONSTRUCTION_REQUIREMENT,
                TrimT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                TrimT.COL_QUEST_REQUIREMENT,
                TrimT.COL_MATERIAL,
            ),
            TrimT.COL_FACILITY_STATS,
            -1,
        )

    private fun patternLayout() =
        PartLayout(
            PatternT.ID,
            PartKind.SAIL_PATTERN,
            PatternT.COL_NAME,
            PatternT.COL_DESCRIPTION,
            PatternT.COL_LOC,
            PlacementLayout.LOC_FIRST,
            RequirementColumns(
                PatternT.COL_SAILING_REQUIREMENT,
                PatternT.COL_CONSTRUCTION_REQUIREMENT,
                PatternT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                PatternT.COL_QUEST_REQUIREMENT,
                PatternT.COL_MATERIAL,
            ),
            PatternT.COL_FACILITY_STATS,
            -1,
        )

    /** Every row of the part table of [kind], in row-id order. */
    fun parts(world: WorldRef, kind: PartKind): List<BoatPart> {
        val l = layout(kind)
        return world.dbRows(l.table).map { row ->
            val placement = l.placement?.let { row.placement(l.loc, it) }
            BoatPart(
                kind = kind,
                row = row.id,
                name = row.string(l.name).orEmpty(),
                description = row.string(l.description).orEmpty(),
                loc = if (l.placement == null) row.int(l.loc) else placement?.loc,
                placement = placement,
                requirements = requirements(row, l.requirements),
                statsRow = if (l.stats >= 0) row.int(l.stats) else null,
                crewRequirement = if (l.crew >= 0) crewRequirement(row, l.crew) else null,
            )
        }
    }

    private fun requirements(row: DbRowRef, c: RequirementColumns): PartRequirements =
        PartRequirements(
            sailing = row.zero(c.sailing),
            construction = row.zero(c.construction),
            coinsAlternative = row.flag(c.coins),
            questRow = row.int(c.quest),
            materials = row.materials(c.material),
        )

    private fun crewRequirement(row: DbRowRef, column: Int): CrewRequirement? {
        val stat = row.int(column, 0, 0) ?: return null
        return CrewRequirement(stat, row.int(column, 0, 1) ?: 0)
    }

    /** Every row of `Sailing_Boat_Facility` (db 176). */
    fun facilities(world: WorldRef): List<BoatFacility> {
        val facilityColumns =
            RequirementColumns(
                FacilityT.COL_SAILING_REQUIREMENT,
                FacilityT.COL_CONSTRUCTION_REQUIREMENT,
                FacilityT.COL_CONSTRUCTION_COINS_ALTERNATIVE,
                FacilityT.COL_QUEST_REQUIREMENT,
                FacilityT.COL_MATERIAL,
            )
        return world.dbRows(FacilityT.ID).map { row ->
            BoatFacility(
                row = row.id,
                name = row.string(FacilityT.COL_NAME).orEmpty(),
                description = row.string(FacilityT.COL_DESCRIPTION).orEmpty(),
                type = row.int(FacilityT.COL_FACILITY_TYPE),
                subtype = row.zero(FacilityT.COL_FACILITY_SUBTYPE),
                locs = row.ints(FacilityT.COL_LOC),
                alternateLocs = row.ints(FacilityT.COL_ALTERNATE_LOCS),
                requirements = requirements(row, facilityColumns),
                buildMax = row.int(FacilityT.COL_BUILD_MAX),
                variants = row.ints(FacilityT.COL_VARIANTS),
                statsRow = row.int(FacilityT.COL_FACILITY_STATS),
                combatRow = row.int(FacilityT.COL_COMBAT_FACILITY_DATA),
                crewFacility = row.int(FacilityT.COL_CREW_FACILITY),
                crewRequirement = crewRequirement(row, FacilityT.COL_CREW_STAT_REQUIREMENT),
                bottleId = row.int(FacilityT.COL_FACILITY_BOTTLE_ID),
            )
        }
    }

    /** Every row of `Sailing_Boat_Hotspot` (db 175). */
    fun hotspots(world: WorldRef): List<BoatHotspot> =
        world.dbRows(HotspotT.ID).map {
            BoatHotspot(it.id, it.ints(HotspotT.COL_OPTION))
        }

    /** Every row of `Sailing_Boat_Facility_Stats` (db 164). */
    fun stats(world: WorldRef): List<FacilityStats> =
        world.dbRows(StatsT.ID).map { row ->
            FacilityStats(
                row = row.id,
                hpMax = row.zero(StatsT.COL_BOAT_HP_MAX),
                defence = row.zero(StatsT.COL_BOAT_DEFENCE),
                stabDefence = row.zero(StatsT.COL_BOAT_STABDEFENCE),
                slashDefence = row.zero(StatsT.COL_BOAT_SLASHDEFENCE),
                crushDefence = row.zero(StatsT.COL_BOAT_CRUSHDEFENCE),
                heavyRangedDefence = row.zero(StatsT.COL_BOAT_HEAVYRANGEDDEFENCE),
                standardRangedDefence = row.zero(StatsT.COL_BOAT_STANDARDRANGEDDEFENCE),
                lightRangedDefence = row.zero(StatsT.COL_BOAT_LIGHTRANGEDDEFENCE),
                magicDefence = row.zero(StatsT.COL_BOAT_MAGICDEFENCE),
                magicLevel = row.zero(StatsT.COL_BOAT_MAGICLEVEL),
                armour = row.zero(StatsT.COL_BOAT_ARMOUR),
                airMagicArmour = row.zero(StatsT.COL_BOAT_AIRMAGICARMOUR),
                earthMagicArmour = row.zero(StatsT.COL_BOAT_EARTHMAGICARMOUR),
                fireMagicArmour = row.zero(StatsT.COL_BOAT_FIREMAGICARMOUR),
                waterMagicArmour = row.zero(StatsT.COL_BOAT_WATERMAGICARMOUR),
                lightRangedArmour = row.zero(StatsT.COL_BOAT_LIGHTRANGEDARMOUR),
                standardRangedArmour = row.zero(StatsT.COL_BOAT_STANDARDRANGEDARMOUR),
                heavyRangedArmour = row.zero(StatsT.COL_BOAT_HEAVYRANGEDARMOUR),
                stabArmour = row.zero(StatsT.COL_BOAT_STABARMOUR),
                slashArmour = row.zero(StatsT.COL_BOAT_SLASHARMOUR),
                crushArmour = row.zero(StatsT.COL_BOAT_CRUSHARMOUR),
                stormResistance = row.zero(StatsT.COL_BOAT_STORMRESISTANCE),
                rapidResistance = row.zero(StatsT.COL_BOAT_RAPIDRESISTANCE),
                crystalHelmResistance = row.zero(StatsT.COL_BOAT_CRYSTALHELM_RESISTANCE),
                baseSpeed = row.zero(StatsT.COL_BOAT_BASESPEED),
                speedCap = row.zero(StatsT.COL_BOAT_SPEEDCAP),
                acceleration = row.int(StatsT.COL_BOAT_ACCELERATION),
                speedBoostDuration = row.zero(StatsT.COL_BOAT_SPEEDBOOST_DURATION),
                cargoHoldSize = row.zero(StatsT.COL_BOAT_CARGOHOLD_SIZE),
                fetidWaterResistant = row.flag(StatsT.COL_BOAT_FETID_WATER_RESISTANT),
                crystalFleckedResistant = row.flag(StatsT.COL_BOAT_CRYSTAL_FLECKED_RESISTANT),
                tangledKelpResistant = row.flag(StatsT.COL_BOAT_TANGLED_KELP_RESISTANT),
                icySeasResistant = row.flag(StatsT.COL_BOAT_ICY_SEAS_RESISTANT),
                maxWindMotes = row.zero(StatsT.COL_BOAT_MAX_WIND_MOTES),
                additionalRecoveryCostPercentage = row.zero(StatsT.COL_BOAT_ADDITIONAL_RECOVERY_COST_PERCENTAGE),
                ammoSavePercentage = row.zero(StatsT.COL_BOAT_AMMO_SAVE_PERCENTAGE),
                autoRepairRate = row.zero(StatsT.COL_BOAT_AUTO_REPAIR_RATE),
            )
        }
}
