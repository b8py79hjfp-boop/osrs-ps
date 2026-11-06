package tp.plugin

import tp.engine.map.Instances
import tp.util.CoordGrid

/**
 * An instanced map area (docs/mechanics/instances.md): a block of map squares in instance space whose zones are
 * copies of cache template zones, drawn for the players inside with a region rebuild. Create one with
 * [EntityRef.createInstance], fill it with [copyZone], move players in with [PlayerRef.teleport] and tear it down with
 * [destroy] once everyone has left (players still inside are sent to the world spawn).
 */
public class InstanceRef internal constructor(
    internal val i: Instances.Instance,
    private val instances: Instances,
) {
    /** South-west tile of the instance (level 0). */
    public val base: CoordGrid get() = CoordGrid(i.baseX, i.baseZ, 0)

    /** Width and length in tiles. */
    public val width: Int get() = i.widthSquares * SQUARE_SIZE
    public val length: Int get() = i.lengthSquares * SQUARE_SIZE

    /** Whether the instance has been torn down. */
    public val destroyed: Boolean get() = i.destroyed

    /** Whether [coord] is inside this instance. */
    public operator fun contains(coord: CoordGrid): Boolean = coord in i

    /**
     * Copies the cache zone holding template tile [from] (its level is the template level) to the instance zone holding
     * [to] (its level is the instance level), turned [rotation] quarter turns clockwise (0..3). The zone's collision
     * and locs follow; players inside see it on the next tick.
     */
    public fun copyZone(from: CoordGrid, to: CoordGrid, rotation: Int = 0) {
        instances.copyZone(i, from, to, rotation)
    }

    /** Empties the instance zone holding [at] (nothing drawn, not walkable). */
    public fun clearZone(at: CoordGrid) {
        instances.clearZone(i, at)
    }

    /** The instance tile that template tile ([dx], [dz]) of a zone copied with [rotation] lands on, within [zone]. */
    public fun rotatedTile(zone: CoordGrid, dx: Int, dz: Int, rotation: Int): CoordGrid =
        CoordGrid(
            (zone.x and ZONE_ALIGN) + Instances.rotateX(dx, dz, 1, 1, rotation),
            (zone.z and ZONE_ALIGN) + Instances.rotateZ(dx, dz, 1, 1, rotation),
            zone.level,
        )

    /** Tears the instance down: zones, collision and locs go, the space is reused. */
    public fun destroy() {
        instances.destroy(i)
    }

    override fun equals(other: Any?): Boolean = other is InstanceRef && other.i === i

    override fun hashCode(): Int = i.hashCode()

    private companion object {
        const val SQUARE_SIZE = 64
        const val ZONE_ALIGN = 7.inv()
    }
}
