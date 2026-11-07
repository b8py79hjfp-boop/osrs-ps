package tp.content.sailing

import tp.plugin.WorldRef

/**
 * Typed, immutable view of the Sailing tables of the cache (db 143-233, docs/plans/sailing.md 6.2), read once at world
 * start. Every list is in row-id order, which is the order the cache and the client use. Server-only columns (docks'
 * coordinates, task XP, shoals, shipwrecks, encounters, NPC boats) are not in the shipped cache and come from
 * `tools/wiki-import/sailing.py` resources instead (docs/mechanics/sailing-*.md).
 *
 * Cross-references are kept as db row ids and resolved by the lookup maps below; [check] verifies that every reference
 * resolves so a cache mismatch fails at boot rather than in a player's boat.
 */
internal class SailingData(world: WorldRef) {
    val boats: List<Boat> = SailingReaders.boats(world)

    /** `Sailing_Boat_Facility_Stats` rows (db 164) by row id. */
    val stats: Map<Int, FacilityStats> = SailingParts.stats(world).associateBy { it.row }

    val hulls: List<BoatPart> = SailingParts.parts(world, PartKind.HULL)
    val sails: List<BoatPart> = SailingParts.parts(world, PartKind.SAIL)
    val helms: List<BoatPart> = SailingParts.parts(world, PartKind.HELM)
    val keels: List<BoatPart> = SailingParts.parts(world, PartKind.KEEL)
    val flags: List<BoatPart> = SailingParts.parts(world, PartKind.FLAG)
    val braziers: List<BoatPart> = SailingParts.parts(world, PartKind.BRAZIER)
    val trims: List<BoatPart> = SailingParts.parts(world, PartKind.TRIM)
    val sailPatterns: List<BoatPart> = SailingParts.parts(world, PartKind.SAIL_PATTERN)
    val facilities: List<BoatFacility> = SailingParts.facilities(world)

    /** `Sailing_Boat_Hotspot` rows (db 175) by row id. */
    val hotspots: Map<Int, BoatHotspot> = SailingParts.hotspots(world).associateBy { it.row }

    val docks: List<Dock> = SailingReaders.docks(world)
    val seas: List<Sea> = SailingReaders.seas(world)
    val hazards: List<SeaHazard> = SailingReaders.hazards(world)
    val portTasks: List<PortTask> = SailingReaders.portTasks(world, docks.associate { it.row to it.id })
    val chartingTasks: List<ChartingTask> = SailingReaders.chartingTasks(world)
    val chartingTools: List<ChartingTool> = SailingReaders.chartingTools(world)
    val trials: List<BarracudaTrial> = SailingReaders.trials(world)
    val crew: List<CrewMember> = SailingReaders.crew(world)
    val boatSelectionTypes: List<BoatSelectionType> = SailingReaders.boatSelectionTypes(world)

    /** Base XP per port task from the Wiki import (the cache has none, db 197 `XP_REWARD` is stripped). */
    val taskXp: PortTaskXp = PortTaskXp.load()

    /** Objs a boat's cargo hold accepts besides crates (db 196). */
    val cargoWhitelist: Set<Int> = SailingReaders.cargoWhitelist(world)

    /** Obj categories a boat's cargo hold accepts (db 196 `ENTRY_CATEGORY`: crates, bounty parts and so on). */
    val cargoCategories: Set<Int> = SailingReaders.cargoCategories(world)

    private val partsByRow: Map<Int, BoatPart> =
        (hulls + sails + helms + keels + flags + braziers + trims + sailPatterns).associateBy { it.row }

    /** The core part with db row [row] (part rows are unique across the part tables), or null. */
    fun part(row: Int): BoatPart? = partsByRow[row]

    /** The stats a part or facility contributes, or null if it has none. */
    fun statsOf(statsRow: Int?): FacilityStats? = statsRow?.let { stats[it] }

    fun boat(type: Int): Boat? = boats.firstOrNull { it.type == type }

    fun dock(id: Int): Dock? = docks.firstOrNull { it.id == id }

    /**
     * Fails if the tables are not self-consistent: every part option of a boat, every hotspot slot and every part stats link
     * must resolve, and every port task must name known docks. Returns normally for the shipped cache 2757.
     */
    fun check() {
        check(boats.isNotEmpty()) { "Sailing_Boat table is empty: not a Sailing cache" }
        for (boat in boats) {
            val optionRows = with(boat) { keelRows + hullRows + sailRows + sailPatternRows + helmRows + flagRows + trimRows + brazierRows }
            for (row in optionRows) check(partsByRow.containsKey(row)) { "boat ${boat.name}: unknown part row $row" }
            for (slot in boat.hotspots) {
                check(
                    hotspots.containsKey(slot.hotspotRow),
                ) { "boat ${boat.name}: unknown hotspot ${slot.hotspotRow}" }
            }
        }
        for (part in partsByRow.values) {
            part.statsRow?.let { check(stats.containsKey(it)) { "part ${part.name}: unknown stats row $it" } }
        }
        for (facility in facilities) {
            facility.statsRow?.let { check(stats.containsKey(it)) { "facility ${facility.name}: unknown stats row $it" } }
        }
        for (task in portTasks) {
            check(!task.isBounty || taskXp.of(task.id) != null) { "bounty task ${task.id} (${task.name}) has no imported XP" }
            check(task.endingDock >= 0) { "port task ${task.id} (${task.name}): unknown ending dock" }
        }
    }
}
