package tp.content.sailing

import tp.util.CoordGrid

/** A (obj, quantity) pair of a part's build materials (cache tuple `obj, int`; docs/mechanics/sailing-boats.md B3). */
internal class Material(val obj: Int, val quantity: Int)

/** A loc placed on a boat's deck template by the server: the tuple `(coord, loc, angle, shape)` of the part tables. */
internal class LocPlacement(val loc: Int, val coord: CoordGrid, val angle: Int, val shape: Int)

/**
 * Crew stat requirement tuple `(stat, level)` of a helm (db 181 col `CREW_STAT_REQUIREMENT`) or facility (db 176 col
 * `CREW_STAT_REQUIREMENT`). Stat ids follow `Sailing_Crew`: 1 helmsmanship, 2 privateering, 3 deckhandiness (the
 * pairing is derived by matching skiff and sloop helms and the cannon / hook tiers, docs/mechanics/sailing-crew.md C2).
 */
internal class CrewRequirement(val stat: Int, val level: Int)

/**
 * Build requirements common to every part table (columns `SAILING_REQUIREMENT`, `CONSTRUCTION_REQUIREMENT`,
 * `CONSTRUCTION_COINS_ALTERNATIVE`, `QUEST_REQUIREMENT`, `MATERIAL`). Levels are not boostable for boats but Construction is
 * (docs/plans/sailing.md section 1.3).
 */
internal class PartRequirements(
    val sailing: Int,
    val construction: Int,
    /** True if the Construction requirement can be paid in coins instead (cache boolean, set on the shipwright-built tiers). */
    val coinsAlternative: Boolean,
    /** Quest db row required to build, or null (every cache row leaves it unset). */
    val questRow: Int?,
    val materials: List<Material>,
)

/** The core part tables, each with its own db table id (`DbTables.Sailing_Boat_*`). */
internal enum class PartKind {
    HULL,
    SAIL,
    HELM,
    KEEL,
    FLAG,
    BRAZIER,
    TRIM,
    SAIL_PATTERN,
}

/**
 * One row of a core part table: hull (db 178), sail (179), helm (`Sailing_Boat_Steering`, 181), keel (177), flag (184),
 * brazier (185), trim (186) or sail pattern (230). A boat lists the rows it can mount in its option columns.
 */
internal class BoatPart(
    val kind: PartKind,
    val row: Int,
    val name: String,
    val description: String,
    /** The loc this part is shown with: the placement tuple's loc, or the plain `LOC` for hulls (which have no coord). */
    val loc: Int?,
    /** Where the server puts the loc on the deck template (tier 0 coordinates, docs/plans/sailing.md 2.4); null for hulls. */
    val placement: LocPlacement?,
    val requirements: PartRequirements,
    /** Row of [FacilityStats] (db 164) this part contributes, or null (the sail pattern and trim rows have none). */
    val statsRow: Int?,
    /** Helms only: the crew stat needed to steer with crew (sloop = helmsmanship 3). */
    val crewRequirement: CrewRequirement?,
)

/**
 * One row of `Sailing_Boat_Facility` (db 176): cannon, hook, cargo hold, range, nets and so on. [subtype] groups the tiers
 * of one facility kind (docs/plans/sailing.md 2.3); [type] is set on 70 of the 99 rows and its meaning is OPEN.
 */
internal class BoatFacility(
    val row: Int,
    val name: String,
    val description: String,
    val type: Int?,
    val subtype: Int,
    /** Locs the facility is shown with (one per boat size or tier variant, in column order). */
    val locs: List<Int>,
    val alternateLocs: List<Int>,
    val requirements: PartRequirements,
    /** Most of this facility a boat may carry, or null for no explicit limit. */
    val buildMax: Int?,
    /** Rows of the other tiers of the same facility the customisation screen switches between. */
    val variants: List<Int>,
    val statsRow: Int?,
    /** Row of the combat facility table (db 162) for cannons, or null. */
    val combatRow: Int?,
    val crewFacility: Int?,
    val crewRequirement: CrewRequirement?,
    /** Id of the facility bottle item (db column `FACILITY_BOTTLE_ID`; not an obj id, see sailing-boats.md OPEN-B4), or null. */
    val bottleId: Int?,
)

/** One row of `Sailing_Boat_Hotspot` (db 175): the facility rows that may be built on a hotspot of this shape. */
internal class BoatHotspot(val row: Int, val facilityRows: List<Int>)

/**
 * One row of `Sailing_Boat_Facility_Stats` (db 164), every numeric stat column. A part leaves a column unset when it adds
 * nothing, so unset reads 0 except [acceleration] where the default is not in the cache (OPEN-S3, sailing-movement.md).
 * Speeds are fine units per tick, 128 per tile (docs/plans/sailing.md 2.2).
 */
internal data class FacilityStats(
    val row: Int,
    val hpMax: Int,
    val defence: Int,
    val stabDefence: Int,
    val slashDefence: Int,
    val crushDefence: Int,
    val heavyRangedDefence: Int,
    val standardRangedDefence: Int,
    val lightRangedDefence: Int,
    val magicDefence: Int,
    val magicLevel: Int,
    val armour: Int,
    val airMagicArmour: Int,
    val earthMagicArmour: Int,
    val fireMagicArmour: Int,
    val waterMagicArmour: Int,
    val lightRangedArmour: Int,
    val standardRangedArmour: Int,
    val heavyRangedArmour: Int,
    val stabArmour: Int,
    val slashArmour: Int,
    val crushArmour: Int,
    val stormResistance: Int,
    val rapidResistance: Int,
    val crystalHelmResistance: Int,
    val baseSpeed: Int,
    val speedCap: Int,
    val acceleration: Int?,
    val speedBoostDuration: Int,
    val cargoHoldSize: Int,
    val fetidWaterResistant: Boolean,
    val crystalFleckedResistant: Boolean,
    val tangledKelpResistant: Boolean,
    val icySeasResistant: Boolean,
    val maxWindMotes: Int,
    /** Percentage added to the recovery cost of a boat with this hull (0..30). */
    val additionalRecoveryCostPercentage: Int,
    val ammoSavePercentage: Int,
    val autoRepairRate: Int,
)
