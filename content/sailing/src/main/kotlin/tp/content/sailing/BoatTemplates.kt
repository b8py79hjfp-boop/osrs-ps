package tp.content.sailing

import tp.util.CoordGrid

/**
 * Where a boat's deck lives in the cache map and how it becomes a world entity (docs/mechanics/sailing-boats.md B6, B9;
 * docs/plans/sailing.md 2.4). The deck is level 1 of [zonesX] x [zonesZ] consecutive template zones whose south-west
 * zone starts at [origin]; each hull tier t has its own copy [TIER_STEP] tiles further east (tier-0 coordinates in the
 * db part rows are relative to [origin]).
 */
internal class DeckTemplate(
    /** World-entity config id (archive 2 group 72). */
    val config: Int,
    val zonesX: Int,
    val zonesZ: Int,
    /** South-west tile of the tier-0 template zone, level 1. */
    val origin: CoordGrid,
    /** Template tile (tier 0) where boarding players are put: a free interior deck tile. */
    val entry: CoordGrid,
    /** Whether the template exists once per hull tier ([TIER_STEP] apart) or once only (Will and Anne's boat). */
    val perTier: Boolean,
) {
    /** Template zone (x, z, level 1) for deck zone ([zx], [zz]) of hull tier [tier]. */
    fun zone(tier: Int, zx: Int, zz: Int): CoordGrid {
        val shift = if (perTier) tier * TIER_STEP else 0
        return origin.translate(shift + zx * ZONE, zz * ZONE)
    }

    /** Offset of the tier-0 template tile [c] from the deck's south-west corner. */
    fun deckOffsetX(c: CoordGrid): Int = c.x - origin.x

    fun deckOffsetZ(c: CoordGrid): Int = c.z - origin.z

    companion object {
        /** Tiles between the templates of consecutive hull tiers (plan 2.4: 7 hull locs spaced 8 apart, zone aligned). */
        const val TIER_STEP: Int = 8
        const val ZONE: Int = 8
    }
}

/**
 * The deck templates by `Sailing_Boat.TYPE_ID`. Origins and entries were read from the cache map and `collision` dumps
 * (plan 2.4): the raft deck is the 1x3 strip x 3843, z 6458..6460 in zone (3840, 6456); the skiff deck x 3843..3844 in
 * zone (3840, 6448); the sloop deck x 3842..3844, z 6436..6443 spans zones (3840, 6432) and (3840, 6440); Will and Anne's
 * boat is a skiff-sized hull at (3843, 6401) in zone (3840, 6400). Entries are interior tiles that no hotspot or part loc
 * covers. The pivot offset is OPEN-WE3 (the engine places the deck by its south-west corner).
 */
internal object BoatTemplates {
    /** `TYPE_ID` of the raft, skiff, sloop and Will and Anne's boat. */
    const val RAFT: Int = 0
    const val SKIFF: Int = 1
    const val SLOOP: Int = 2
    const val INTRO: Int = 3

    /** Level of the deck inside the entity's instance and of the template zones (world-entity config op 2: active level 1). */
    const val DECK_LEVEL: Int = 1

    private val raft = DeckTemplate(1, 1, 1, CoordGrid(3840, 6456, DECK_LEVEL), CoordGrid(3843, 6459, DECK_LEVEL), true)
    private val skiff = DeckTemplate(2, 1, 1, CoordGrid(3840, 6448, DECK_LEVEL), CoordGrid(3844, 6450, DECK_LEVEL), true)
    private val sloop = DeckTemplate(3, 1, 2, CoordGrid(3840, 6432, DECK_LEVEL), CoordGrid(3843, 6440, DECK_LEVEL), true)

    /** World-entity config 10 is one of the two non-player 2x5 boats (plan 2.1, OPEN-W1: 10 or 11). */
    private val intro = DeckTemplate(10, 1, 1, CoordGrid(3840, 6400, DECK_LEVEL), CoordGrid(3844, 6403, DECK_LEVEL), false)

    fun of(type: Int): DeckTemplate? =
        when (type) {
            RAFT -> raft
            SKIFF -> skiff
            SLOOP -> sloop
            INTRO -> intro
            else -> null
        }
}
