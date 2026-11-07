package tp.content.sailing

import tp.util.CoordGrid

/**
 * A deck hotspot: `Sailing_Boat.HOTSPOT` tuple `(deck tile, a, b, hotspot row)`. [a] in {0,1,3} and [b] in {1,2,3} are
 * probably loc rotation and footprint (OPEN-S1, docs/mechanics/sailing-boats.md B5).
 */
internal class HotspotSlot(val tile: CoordGrid, val a: Int, val b: Int, val hotspotRow: Int)

/**
 * One row of `Sailing_Boat` (db 166): raft, skiff, sloop and Will and Anne's boat. The option lists name the part rows
 * (db 177-186, 230) the boat can mount, in tier order. Origin, entry, entity data, acceleration, turn speed and wake
 * coordinates are server-only columns that the shipped cache strips, so they are absent here (docs/plans/sailing.md 6.5).
 */
internal class Boat(
    /** `TYPE_ID`: 0 raft, 1 skiff, 2 sloop, 3 Will and Anne's boat (varbit `SAILING_BOARDED_BOAT_TYPE`). */
    val type: Int,
    val row: Int,
    val name: String,
    val inlineName: String,
    val displayName: String?,
    val levelRequired: Int,
    val playerCapacity: Int,
    /** `BOAT_SIZE` enum value: 1 tiny (raft), 2 small, 3 medium. */
    val size: Int,
    val sizeDescription: String,
    val combinedNavigation: Boolean,
    /** Coins to recover the boat from a shipwright (cache base value; the Wiki's higher figure is OPEN, sailing-boats.md B2). */
    val retrievalCost: Int,
    val crewCapacity: Int,
    val facilityAmount: Int,
    val sameFacilitiesPortAndStarboard: Boolean,
    /** `TEMPORARY_BOAT`: Will and Anne's boat only, not owned or persisted. */
    val temporary: Boolean,
    val sprite: Int?,
    val keelRows: List<Int>,
    val hullRows: List<Int>,
    val sailRows: List<Int>,
    val sailPatternRows: List<Int>,
    val helmRows: List<Int>,
    val flagRows: List<Int>,
    val trimRows: List<Int>,
    val brazierRows: List<Int>,
    val hotspots: List<HotspotSlot>,
    /** The non-removable facility of a temporary boat (Will and Anne's boat), or null. */
    val staticFacility: LocPlacement?,
)
