package tp.content.sailing

import tp.symbols.Varbits

/** Construction XP and shipwright fee of a part or facility (Wiki import `shipbuilding.tsv`); [fee] is null if no shipwright builds it. */
internal class BuildCost(val xp: Double, val fee: Int?)

/**
 * What building costs and what a part needs beyond the cache rows (docs/mechanics/sailing-building.md BU1-BU9): the Wiki-only
 * Construction XP and shipwright fees (`shipbuilding.tsv`, produced by `tools/wiki-import/sailing_build.py`), the schematic
 * varbit of the tiers that must be discovered first, and lookups from the boat's option lists to part and facility rows.
 */
internal class ShipbuildingData(private val data: SailingData) {
    private val core = HashMap<String, BuildCost>()
    private val facilityCosts = HashMap<String, BuildCost>()

    /** `Sailing_Boat_Facility` (db 176) rows by row id. */
    val facilityByRow: Map<Int, BoatFacility> = data.facilities.associateBy { it.row }

    init {
        val text = ShipbuildingData::class.java.getResource("shipbuilding.tsv")?.readText() ?: error("shipbuilding.tsv missing")
        for (line in text.lineSequence().drop(1)) {
            val c = line.split('\t')
            if (c.size < COLUMNS) continue
            val cost = BuildCost(c[XP_COL].toDouble(), c[FEE_COL].toInt().takeIf { it >= 0 })
            if (c[KIND_COL] ==
                "facility"
            ) {
                facilityCosts[c[NAME_COL]] = cost
            } else {
                core[coreKey(c[KIND_COL], c[BOAT_COL].toInt(), c[INDEX_COL].toInt())] =
                    cost
            }
        }
    }

    /**
     * Cost of building [part] on a boat of [boatType]: hull, sail, helm and keel are keyed by their tier (position in the boat's
     * option list), flag and brazier by kind alone, the sail patterns by position (the Wiki lists the plain sail first).
     */
    fun partCost(part: BoatPart, boatType: Int): BuildCost? {
        val boat = data.boat(boatType) ?: return null
        return when (part.kind) {
            PartKind.HULL -> core[coreKey("hull", boatType, boat.hullRows.indexOf(part.row))]
            PartKind.SAIL -> core[coreKey("sail", boatType, boat.sailRows.indexOf(part.row))]
            PartKind.HELM -> core[coreKey("helm", boatType, boat.helmRows.indexOf(part.row))]
            PartKind.KEEL -> core[coreKey("keel", boatType, boat.keelRows.indexOf(part.row))]
            PartKind.FLAG -> core[coreKey("flag", -1, 0)]
            PartKind.BRAZIER -> core[coreKey("brazier", -1, 0)]
            PartKind.SAIL_PATTERN -> core[coreKey("pattern", -1, boat.sailPatternRows.indexOf(part.row))]
            PartKind.TRIM -> null
        }
    }

    /** Cost of building [facility] (the Wiki names equal the cache names after the importer's aliases). */
    fun facilityCost(facility: BoatFacility): BuildCost? = facilityCosts[facility.name]

    /** Facility rows that may be built on hotspot [index] of [boat], in the hotspot row's option order (the saved index + 1). */
    fun options(boat: Boat, index: Int): List<BoatFacility> {
        val slot = boat.hotspots.getOrNull(index) ?: return emptyList()
        val rows = data.hotspots[slot.hotspotRow]?.facilityRows ?: return emptyList()
        return rows.mapNotNull { facilityByRow[it] }
    }

    /** The option rows of [boat] for the core part [kind], in tier order. */
    fun partRows(boat: Boat, kind: PartKind): List<Int> =
        when (kind) {
            PartKind.HULL -> boat.hullRows
            PartKind.SAIL -> boat.sailRows
            PartKind.HELM -> boat.helmRows
            PartKind.KEEL -> boat.keelRows
            PartKind.FLAG -> boat.flagRows
            PartKind.BRAZIER -> boat.brazierRows
            PartKind.TRIM -> boat.trimRows
            PartKind.SAIL_PATTERN -> boat.sailPatternRows
        }

    /** Fails if a facility kind of the cache has no imported cost, so a stale `shipbuilding.tsv` stops the boot. */
    fun check() {
        for (facility in data.facilities) {
            if (facility.subtype == 0) continue
            check(facilityCosts.containsKey(facility.name)) { "facility ${facility.name} has no entry in shipbuilding.tsv" }
        }
        val known = Shipyard.FACILITY_LOCS.toSet()
        val missing =
            data.facilities
                .flatMap { it.locs + it.alternateLocs }
                .filter { it !in known }
                .distinct()
        check(missing.isEmpty()) { "facility locs not registered in Shipyard.FACILITY_LOCS: $missing" }
    }

    internal companion object {
        /**
         * Whether a player whose current Sailing level is [sailingLevel] may use [facility] (rule BU6, sailing-building.md): the cargo
         * hold is a support facility that needs no level once built, every other facility keeps its Sailing requirement.
         * Callers: the salvaging, trawling and cannon content (S6, S7, S10) and the crew code (S9).
         */
        fun canOperate(sailingLevel: Int, facility: BoatFacility): Boolean =
            facility.subtype == CARGO_HOLD_SUBTYPE || sailingLevel >= facility.requirements.sailing

        /** `FACILITY_SUBTYPE` of the cargo hold tiers: the one facility that needs no level after it is built (BU6). */
        const val CARGO_HOLD_SUBTYPE: Int = 2

        /** `FACILITY_SUBTYPE` of the cannons (at most [BoatFacility.buildMax] per boat, 2). */
        const val CANNON_SUBTYPE: Int = 4

        /** `FACILITY_SUBTYPE` of the teleport focus and greater teleport focus. */
        const val TELEPORT_FOCUS_SUBTYPE: Int = 9

        /**
         * Lost schematic varbits by part or facility row (client script 9078, which hides a part whose schematic is unknown):
         * the rosewood hull / sail / cargo hold, dragon helm / keel / hook / cannon, eternal brazier, salvaging station, gale
         * catcher, ballistic attractor and bosun's workbench.
         */
        val SCHEMATICS: Map<Int, Int> =
            buildMap {
                fun schematic(varbit: Int, vararg rows: Int) = rows.forEach { put(it, varbit) }
                schematic(Varbits.LOST_SCHEMATIC_SALVAGING_STATION, 8495, 8496, 8497)
                schematic(Varbits.LOST_SCHEMATIC_GALE_CATCHER, 8517)
                schematic(Varbits.LOST_SCHEMATIC_ETERNAL_BRAZIER, 8523, 8524, 8525)
                schematic(Varbits.LOST_SCHEMATIC_DRAGON_SALVAGING_HOOK, 8440, 8447, 8460, 8461)
                schematic(Varbits.LOST_SCHEMATIC_ROSEWOOD_CARGO_HOLD, 8468, 8475, 8482)
                schematic(Varbits.LOST_SCHEMATIC_DRAGON_CANNON, 8433)
                schematic(Varbits.LOST_SCHEMATIC_ROSEWOOD_HULL, 8270, 8277, 8284)
                schematic(Varbits.LOST_SCHEMATIC_ROSEWOOD_SAIL, 8305, 8312, 8319)
                schematic(Varbits.LOST_SCHEMATIC_DRAGON_TILLER, 8326, 8333, 8340)
                schematic(Varbits.LOST_SCHEMATIC_DRAGON_KEEL, 8291, 8298)
                schematic(Varbits.LOST_SCHEMATIC_BALLISTIC_ATTRACTOR, 13307)
                schematic(Varbits.LOST_SCHEMATIC_BOSUNS_WORKBENCH, 16313)
            }

        private const val KIND_COL = 0
        private const val BOAT_COL = 1
        private const val INDEX_COL = 2
        private const val NAME_COL = 3
        private const val XP_COL = 4
        private const val FEE_COL = 5
        private const val COLUMNS = 6

        private fun coreKey(kind: String, boat: Int, index: Int): String = "$kind:$boat:$index"
    }
}
