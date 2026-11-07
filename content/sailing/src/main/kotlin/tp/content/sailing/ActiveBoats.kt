package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.plugin.WorldEntityRef
import tp.util.CoordGrid

/**
 * A boat that exists in the world right now: its world entity, who owns it and its mounted parts with their stats rows
 * (db 164). Created by [Boats.spawn]; the motion code (BoatMotion, S2) reads the stats and the helm tile from here.
 */
internal class ActiveBoat(
    val ref: WorldEntityRef,
    /** Lower-case account name of the owner. */
    val owner: String,
    /** Boat slot 0..4 of the owner, or -1 for the temporary Will and Anne's boat. */
    val slot: Int,
    /** `Sailing_Boat` row (db 166). */
    val boat: Boat,
    val hull: BoatPart,
    val sail: BoatPart,
    val helm: BoatPart,
    val keel: BoatPart?,
    val hullStats: FacilityStats?,
    val sailStats: FacilityStats?,
    val helmStats: FacilityStats?,
    val keelStats: FacilityStats?,
    /** Deck tile (instance coordinates, level 1) of the helm loc. */
    val helmTile: CoordGrid,
    /** Deck tile (instance coordinates, level 1) where boarding players appear. */
    val entry: CoordGrid,
    /** Hull tier 0..6 (index into the db option lists). */
    val tier: Int,
) {
    /** Dock id the boat is moored at, or [BoatSave.AT_SEA] while it sails. */
    var dock: Int = BoatSave.AT_SEA

    /** Current hitpoints (persisted when the boat is moored). */
    var hp: Int = 0

    /** Sum of the mounted parts' maximum hitpoints (combination rule OPEN-B3: plain sum). */
    val maxHp: Int get() = (hullStats?.hpMax ?: 0) + (sailStats?.hpMax ?: 0) + (helmStats?.hpMax ?: 0) + (keelStats?.hpMax ?: 0)

    /** Whether players other than the owner may be aboard right now: the boat is moored. */
    val isDocked: Boolean get() = dock != BoatSave.AT_SEA
}

/**
 * Registry of the boats alive in a world, kept in a world attribute (no global state). Entry points for the motion code:
 * [of] (the boat a player stands on), [ownedBy], [at] and [all].
 */
internal object ActiveBoats {
    private val REGISTRY = AttrKey<HashMap<Int, ActiveBoat>>("sailing.boats")

    private fun registry(any: EntityRef): HashMap<Int, ActiveBoat> =
        any.worldAttr(REGISTRY) ?: HashMap<Int, ActiveBoat>().also { any.setWorldAttr(REGISTRY, it) }

    /** The boat whose deck [player] stands on, or null. */
    fun of(player: PlayerRef): ActiveBoat? {
        val entity = player.worldEntityAt(player.coord) ?: return null
        return registry(player)[entity.index]
    }

    /** The boat of [entity], or null (it is not one of ours). */
    fun at(any: EntityRef, entity: WorldEntityRef): ActiveBoat? = registry(any)[entity.index]

    /** The live boat owned by the account [owner] (lower case), or null. One per owner. */
    fun ownedBy(any: EntityRef, owner: String): ActiveBoat? = registry(any).values.firstOrNull { it.owner == owner }

    /** Every live boat of the world [any] belongs to. */
    fun all(any: EntityRef): Collection<ActiveBoat> = registry(any).values

    internal fun add(any: EntityRef, boat: ActiveBoat) {
        registry(any)[boat.ref.index] = boat
    }

    internal fun remove(any: EntityRef, boat: ActiveBoat) {
        registry(any).remove(boat.ref.index)
    }
}
