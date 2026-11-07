package tp.content.sailing

import tp.plugin.EntityRef
import tp.plugin.WorldEntityRef
import tp.util.CoordGrid

/** Where and how a boat sits in the root world: pivot tile and facing (0 south, 512 west, 1024 north, 1536 east). */
internal class Pose(val tile: CoordGrid, val angle: Int)

/**
 * Builds and removes boats (docs/mechanics/sailing-boats.md B6, B9): a world entity with the deck copied from the
 * template of the boat's hull tier, the mounted part locs placed on the deck at their db coordinates, shown and put under
 * [BoatMotion]. Boats are tracked in [ActiveBoats].
 */
internal class Boats(private val data: SailingData, val sites: DockSites) {
    /**
     * Spawns the boat of [owned] (boat slot [slot] of the account [owner]) at [pose]; null if the boat type has no
     * template. [ctx] is any entity of the world (the owner).
     */
    fun spawn(ctx: EntityRef, owner: String, slot: Int, owned: OwnedBoat, pose: Pose): ActiveBoat? {
        val boat = data.boat(owned.type) ?: return null
        val tpl = BoatTemplates.of(owned.type) ?: return null
        val hull = boat.hullRows.getOrNull(owned.hull)?.let(data::part) ?: return null
        val sail = boat.sailRows.getOrNull(owned.sail)?.let(data::part) ?: return null
        val helm = boat.helmRows.getOrNull(owned.helm)?.let(data::part) ?: return null
        val keel = if (boat.keelRows.isEmpty()) null else boat.keelRows.getOrNull(owned.keel)?.let(data::part)
        val ref = ctx.createWorldEntity(tpl.config, tpl.zonesX, tpl.zonesZ, BoatTemplates.DECK_LEVEL, pose.tile, pose.angle)
        copyDeck(ref, tpl, owned.hull)
        for (part in listOf(helm, sail, keel)) place(ctx, ref, tpl, part?.placement)
        place(ctx, ref, tpl, boat.staticFacility)
        val active =
            ActiveBoat(
                ref = ref,
                owner = owner,
                slot = slot,
                boat = boat,
                hull = hull,
                sail = sail,
                helm = helm,
                keel = keel,
                hullStats = data.statsOf(hull.statsRow),
                sailStats = data.statsOf(sail.statsRow),
                helmStats = data.statsOf(helm.statsRow),
                keelStats = data.statsOf(keel?.statsRow),
                helmTile = deckTile(ref, tpl, helm.placement?.coord ?: tpl.entry),
                entry = deckTile(ref, tpl, tpl.entry),
                tier = owned.hull,
            )
        active.hp = owned.hp.coerceAtMost(active.maxHp).takeIf { it > 0 } ?: active.maxHp
        ref.show()
        ActiveBoats.add(ctx, active)
        attachMotion(ctx, active)
        return active
    }

    /** Full hitpoints of a boat as [owned] is built: the sum of its mounted parts' maximum hitpoints (OPEN-B3). */
    fun maxHp(owned: OwnedBoat): Int {
        val boat = data.boat(owned.type) ?: return 0
        val rows = listOf(boat.hullRows, boat.sailRows, boat.helmRows, boat.keelRows)
        val picks = listOf(owned.hull, owned.sail, owned.helm, owned.keel)
        return rows.indices.sumOf { i -> data.statsOf(rows[i].getOrNull(picks[i])?.let(data::part)?.statsRow)?.hpMax ?: 0 }
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

    private fun attachMotion(ctx: EntityRef, boat: ActiveBoat) {
        val hull = boat.hullStats
        val footprint = FOOTPRINTS[boat.boat.type] ?: return
        val stats =
            BoatMotion.Stats(
                baseSpeed = hull?.baseSpeed ?: 0,
                speedCap = hull?.speedCap ?: 0,
                acceleration = boat.sailStats?.acceleration ?: DEFAULT_ACCELERATION,
                widthFine = footprint.width,
                lengthFine = footprint.length,
                turnPerTick = footprint.turn,
            )
        motion(ctx)?.attach(boat.ref, stats)
    }

    private fun copyDeck(ref: WorldEntityRef, tpl: DeckTemplate, tier: Int) {
        // Only level 1 (the deck) is copied: the template's level 0 is open sea and draws a dark rectangle under the boat.
        for (zx in 0 until tpl.zonesX) {
            for (zz in 0 until tpl.zonesZ) {
                ref.copyZone(tpl.zone(tier, zx, zz), ref.deckBase.translate(zx * DeckTemplate.ZONE, zz * DeckTemplate.ZONE, DECK_LEVEL))
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

        /** Fine units per tile and the centre of a tile (RSProt `CoordFine`). */
        private const val FINE_SHIFT = 7
        private const val HALF_TILE = 64

        /**
         * Default sail acceleration of the tiers whose db row has none (OPEN-S3 / OPEN-M3): the cache stores 64 for the
         * Wiki's 1.0 (camphor and better), so the Wiki's 0.5 is 32 [docs/plans/sailing.md 2.2].
         */
        private const val DEFAULT_ACCELERATION = 32

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
