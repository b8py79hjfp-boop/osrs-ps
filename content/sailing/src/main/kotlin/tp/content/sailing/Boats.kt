package tp.content.sailing

import tp.plugin.EntityRef
import tp.plugin.WorldEntityRef
import tp.symbols.Locs
import tp.util.CoordGrid

/** Where and how a boat sits in the root world: pivot tile and facing (0 south, 512 west, 1024 north, 1536 east). */
internal class Pose(val tile: CoordGrid, val angle: Int)

/**
 * Builds and removes boats (docs/mechanics/sailing-boats.md B6, B9; sailing-building.md BU10): a world entity with the deck
 * copied from the template of the boat's hull tier, the mounted part locs placed on the deck at their db coordinates, shown and
 * put under [BoatMotion]. Boats are tracked in [ActiveBoats]. [rebuild] swaps the part locs of a live boat after a customisation
 * and recomputes its stats and its motion stats.
 */
internal class Boats(private val data: SailingData, val sites: DockSites) {
    /** Costs and lookups of shipbuilding (S4). */
    val build: ShipbuildingData = ShipbuildingData(data)

    /** Every part of an [OwnedBoat] resolved to its db rows. */
    private class Resolved(
        val boat: Boat,
        val tpl: DeckTemplate,
        val hull: BoatPart,
        val sail: BoatPart,
        val helm: BoatPart,
        val keel: BoatPart?,
        val flag: BoatPart?,
        val trim: BoatPart?,
        val brazier: BoatPart?,
        val pattern: BoatPart?,
        val facilities: List<BuiltFacility>,
    )

    private fun resolve(owned: OwnedBoat): Resolved? {
        val boat = data.boat(owned.type) ?: return null
        val tpl = BoatTemplates.of(owned.type) ?: return null
        val hull = boat.hullRows.getOrNull(owned.hull)?.let(data::part) ?: return null
        val sail = boat.sailRows.getOrNull(owned.sail)?.let(data::part) ?: return null
        val helm = boat.helmRows.getOrNull(owned.helm)?.let(data::part) ?: return null
        val keel = if (boat.keelRows.isEmpty()) null else boat.keelRows.getOrNull(owned.keel)?.let(data::part)
        val built =
            boat.hotspots.indices.mapNotNull { i ->
                val option = owned.hotspotAt(i)
                val facility = if (option > 0) build.options(boat, i).getOrNull(option - 1) else null
                facility?.let { BuiltFacility(i, it, option) }
            }
        return Resolved(
            boat = boat,
            tpl = tpl,
            hull = hull,
            sail = sail,
            helm = helm,
            keel = keel,
            flag = boat.flagRows.getOrNull(owned.flag - 1)?.let(data::part),
            trim = boat.trimRows.getOrNull(owned.trim)?.let(data::part),
            brazier = boat.brazierRows.getOrNull(owned.brazier - 1)?.let(data::part),
            pattern = boat.sailPatternRows.getOrNull(owned.pattern)?.let(data::part),
            facilities = built,
        )
    }

    /**
     * Spawns the boat of [owned] (boat slot [slot] of the account [owner]) at [pose]; null if the boat type has no
     * template. [ctx] is any entity of the world (the owner). A [shipyard] boat (sailing-building.md SY1) stands in the build
     * space: it has no motion and shows a placeholder on every empty hotspot.
     */
    fun spawn(ctx: EntityRef, owner: String, slot: Int, owned: OwnedBoat, pose: Pose, shipyard: Boolean = false): ActiveBoat? {
        val r = resolve(owned) ?: return null
        val tpl = r.tpl
        val ref = ctx.createWorldEntity(tpl.config, tpl.zonesX, tpl.zonesZ, BoatTemplates.DECK_LEVEL, pose.tile, pose.angle)
        copyDeck(ref, tpl, owned.hull)
        val active =
            ActiveBoat(
                ref = ref,
                owner = owner,
                slot = slot,
                boat = r.boat,
                hull = r.hull,
                sail = r.sail,
                helm = r.helm,
                keel = r.keel,
                hullStats = data.statsOf(r.hull.statsRow),
                sailStats = data.statsOf(r.sail.statsRow),
                helmStats = data.statsOf(r.helm.statsRow),
                keelStats = data.statsOf(r.keel?.statsRow),
                helmTile = deckTile(ref, tpl, r.helm.placement?.coord ?: tpl.entry),
                entry = deckTile(ref, tpl, tpl.entry),
                tier = owned.hull,
            )
        assign(active, r)
        placeAll(ctx, ref, r, shipyard, clear = false)
        active.hp = owned.hp.coerceAtMost(active.maxHp).takeIf { it > 0 } ?: active.maxHp
        if (shipyard) active.dock = Shipyard.DOCK
        ref.show()
        ActiveBoats.add(ctx, active)
        if (!shipyard) attachMotion(ctx, active)
        return active
    }

    /**
     * Brings a live boat in line with [owned] after a customisation: copies the deck again if the hull tier changed (which also
     * clears the old locs), swaps every part and facility loc, then recomputes the boat's stats and its motion stats.
     */
    fun rebuild(ctx: EntityRef, boat: ActiveBoat, owned: OwnedBoat, shipyard: Boolean) {
        val r = resolve(owned) ?: return
        val hullChanged = boat.tier != owned.hull
        if (hullChanged) copyDeck(boat.ref, r.tpl, owned.hull)
        boat.hull = r.hull
        boat.sail = r.sail
        boat.helm = r.helm
        boat.keel = r.keel
        boat.hullStats = data.statsOf(r.hull.statsRow)
        boat.sailStats = data.statsOf(r.sail.statsRow)
        boat.helmStats = data.statsOf(r.helm.statsRow)
        boat.keelStats = data.statsOf(r.keel?.statsRow)
        boat.helmTile = deckTile(boat.ref, r.tpl, r.helm.placement?.coord ?: r.tpl.entry)
        boat.tier = owned.hull
        assign(boat, r)
        placeAll(ctx, boat.ref, r, shipyard, clear = true)
        boat.hp = boat.hp.coerceAtMost(boat.maxHp)
        motion(ctx)?.motionOf(boat.ref)?.stats = motionStats(boat)
    }

    /** Cosmetics, facilities and stats of [boat] from [r]. */
    private fun assign(boat: ActiveBoat, r: Resolved) {
        boat.flag = r.flag
        boat.trim = r.trim
        boat.brazier = r.brazier
        boat.pattern = r.pattern
        boat.facilities = r.facilities
        boat.stats = BoatStats.of(data, boat)
    }

    /** Full hitpoints of a boat as [owned] is built: the sum of its mounted parts' maximum hitpoints (OPEN-B3). */
    fun maxHp(owned: OwnedBoat): Int {
        val boat = data.boat(owned.type) ?: return 0
        val rows = listOf(boat.hullRows, boat.sailRows, boat.helmRows, boat.keelRows)
        val picks = listOf(owned.hull, owned.sail, owned.helm, owned.keel)
        val parts = rows.indices.sumOf { i -> data.statsOf(rows[i].getOrNull(picks[i])?.let(data::part)?.statsRow)?.hpMax ?: 0 }
        val extras =
            listOfNotNull(
                boat.brazierRows.getOrNull(owned.brazier - 1),
            ).sumOf { data.statsOf(data.part(it)?.statsRow)?.hpMax ?: 0 }
        val facilities =
            boat.hotspots.indices.sumOf { i ->
                val option = owned.hotspotAt(i)
                val facility = if (option > 0) build.options(boat, i).getOrNull(option - 1) else null
                data.statsOf(facility?.statsRow)?.hpMax ?: 0
            }
        return parts + extras + facilities
    }

    /** Removes [boat]: motion, registry and world entity. Everyone aboard must have been moved off first. */
    fun despawn(ctx: EntityRef, boat: ActiveBoat) {
        motion(ctx)?.detach(boat.ref)
        ActiveBoats.remove(ctx, boat)
        boat.ref.remove()
    }

    /** Puts [boat] at the dock pose of [site] with its sails down, and records the mooring. */
    fun moor(ctx: EntityRef, boat: ActiveBoat, site: DockSite) {
        val sea = site.sea ?: return
        val fine = fine(sea)
        boat.ref.move(fine.first, fine.second, site.angle, teleport = true)
        val motion = motion(ctx)?.motionOf(boat.ref)
        if (motion != null) {
            motion.speed = 0
            motion.sails = BoatMotion.Sails.DOWN
            motion.heading = site.angle
            motion.angle = site.angle
        }
        boat.dock = site.dock.id
    }

    /** The [BoatMotion] of the world [ctx] belongs to (answered over the event bus), or null. */
    fun motion(ctx: EntityRef): BoatMotion? {
        val lookup = BoatMotion.Lookup()
        ctx.events.publish(lookup)
        return lookup.motion
    }

    /** The deck tile (instance coordinates) of the tier-0 template tile [template] on [boat]. */
    fun deckTileOf(
        boat: ActiveBoat,
        template: CoordGrid,
    ): CoordGrid? = BoatTemplates.of(boat.boat.type)?.let { deckTile(boat.ref, it, template) }

    /** Index of the hotspot of [boat] standing on the deck tile [coord], or null. */
    fun hotspotAt(boat: ActiveBoat, coord: CoordGrid): Int? {
        val tpl = BoatTemplates.of(boat.boat.type) ?: return null
        val index = boat.boat.hotspots.indexOfFirst { deckTile(boat.ref, tpl, it.tile) == coord }
        return index.takeIf { it >= 0 }
    }

    /** The motion stats of [boat]: hull speeds, sail acceleration and the footprint of its type. */
    private fun motionStats(boat: ActiveBoat): BoatMotion.Stats {
        val footprint = FOOTPRINTS[boat.boat.type] ?: Footprint(0, 0, 0)
        return BoatMotion.Stats(
            baseSpeed = boat.stats.baseSpeed,
            speedCap = boat.stats.speedCap,
            acceleration = boat.stats.acceleration,
            widthFine = footprint.width,
            lengthFine = footprint.length,
            turnPerTick = footprint.turn,
        )
    }

    private fun attachMotion(ctx: EntityRef, boat: ActiveBoat) {
        if (FOOTPRINTS[boat.boat.type] == null) return
        motion(ctx)?.attach(boat.ref, motionStats(boat))
    }

    private fun copyDeck(ref: WorldEntityRef, tpl: DeckTemplate, tier: Int) {
        // Only level 1 (the deck) is copied: the template's level 0 is open sea and draws a dark rectangle under the boat.
        for (zx in 0 until tpl.zonesX) {
            for (zz in 0 until tpl.zonesZ) {
                ref.copyZone(tpl.zone(tier, zx, zz), ref.deckBase.translate(zx * DeckTemplate.ZONE, zz * DeckTemplate.ZONE, DECK_LEVEL))
            }
        }
    }

    /**
     * Places every loc of [r] on the deck: helm, sail (or its pattern), keel, flag, trim, brazier, the static facility and one loc
     * per hotspot (the built facility, else in the [shipyard] the placeholder, else with [clear] nothing).
     */
    private fun placeAll(ctx: EntityRef, ref: WorldEntityRef, r: Resolved, shipyard: Boolean, clear: Boolean) {
        val tpl = r.tpl
        val sail = r.pattern?.takeIf { it.row != r.boat.sailPatternRows.firstOrNull() }?.placement ?: r.sail.placement
        for (placement in listOf(r.helm.placement, sail, r.keel?.placement, r.flag?.placement, r.trim?.placement, r.brazier?.placement)) {
            place(ctx, ref, tpl, placement)
        }
        place(ctx, ref, tpl, r.boat.staticFacility)
        for ((i, slot) in r.boat.hotspots.withIndex()) {
            val built = r.facilities.firstOrNull { it.hotspot == i }
            val loc = built?.facility?.locs?.firstOrNull()
            when {
                loc != null -> place(ctx, ref, tpl, LocPlacement(loc, slot.tile, slot.a, HOTSPOT_SHAPE))
                shipyard -> place(ctx, ref, tpl, LocPlacement(placeholderLoc(r.boat.type, i), slot.tile, slot.a, HOTSPOT_SHAPE))
                clear -> ctx.deleteLoc(deckTile(ref, tpl, slot.tile), HOTSPOT_SHAPE)
            }
        }
    }

    private fun place(ctx: EntityRef, ref: WorldEntityRef, tpl: DeckTemplate, placement: LocPlacement?) {
        if (placement == null) return
        ctx.addLoc(placement.loc, deckTile(ref, tpl, placement.coord), placement.shape, placement.angle)
    }

    /** The deck tile (instance coordinates) of the tier-0 template tile [template]. */
    private fun deckTile(ref: WorldEntityRef, tpl: DeckTemplate, template: CoordGrid): CoordGrid =
        ref.deckBase.translate(tpl.deckOffsetX(template), tpl.deckOffsetZ(template), DECK_LEVEL)

    private class Footprint(val width: Int, val length: Int, val turn: Int)

    internal companion object {
        private const val DECK_LEVEL = BoatTemplates.DECK_LEVEL

        /**
         * Loc shape of a facility hotspot and of the placed facility: the centrepiece shape every part tuple of db 177-186
         * carries (OPEN-S1: the tuple's `a` is used as the loc angle, `b` is unused).
         */
        private const val HOTSPOT_SHAPE = 10

        /** Fine units per tile and the centre of a tile (RSProt `CoordFine`). */
        private const val FINE_SHIFT = 7
        private const val HALF_TILE = 64

        /** Hotspot placeholder locs: raft 1, skiff 7, sloop 11 consecutive ids and two more for sloop hotspots 11 and 12. */
        private const val SLOOP_CONSECUTIVE_PLACEHOLDERS = 11

        /** The placeholder loc ("Facility hotspot", op 1 "Build") of hotspot [index] of a boat of [type]. */
        fun placeholderLoc(type: Int, index: Int): Int =
            when (type) {
                BoatTemplates.RAFT -> {
                    Locs.SAILING_BOAT_FACILITY_PLACEHOLDER_RAFT_0
                }

                BoatTemplates.SKIFF -> {
                    Locs.SAILING_BOAT_FACILITY_PLACEHOLDER_2X5_0 + index
                }

                else -> {
                    if (index < SLOOP_CONSECUTIVE_PLACEHOLDERS) {
                        Locs.SAILING_BOAT_FACILITY_PLACEHOLDER_3X8_0 + index
                    } else {
                        Locs.SAILING_BOAT_FACILITY_PLACEHOLDER_3X8_11 + index - SLOOP_CONSECUTIVE_PLACEHOLDERS
                    }
                }
            }

        /**
         * Motion footprints from the world-entity config bounds in fine units (raft 128 x 384, skiff 256 x 640, sloop
         * 384 x 1280; Will and Anne's boat is skiff-sized) and the turn per tick of the 2023 design blog (OPEN-M1): 256 for
         * small boats, 128 for the sloop.
         */
        private val FOOTPRINTS =
            mapOf(
                BoatTemplates.RAFT to Footprint(128, 384, 256),
                BoatTemplates.SKIFF to Footprint(256, 640, 256),
                BoatTemplates.SLOOP to Footprint(384, 1280, 128),
                BoatTemplates.INTRO to Footprint(256, 640, 256),
            )

        fun fine(tile: CoordGrid): Pair<Int, Int> = Pair((tile.x shl FINE_SHIFT) + HALF_TILE, (tile.z shl FINE_SHIFT) + HALF_TILE)
    }
}
