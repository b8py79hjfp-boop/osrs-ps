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
    private val world: tp.engine.World,
) {
    private val instances: Instances get() = world.instances

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

    /**
     * Tears the instance down (IN9): its NPCs and ground objs, then zones, collision and locs go and the space is reused.
     * Players still inside are moved to their saved exit or the world spawn (IN8).
     */
    public fun destroy() {
        world.destroyInstance(i)
    }

    /** IN7: the instance tile showing template tile [template] (a [sizeX] x [sizeZ] footprint), or null. */
    public fun fromTemplate(template: CoordGrid, sizeX: Int = 1, sizeZ: Int = 1): CoordGrid? = i.fromTemplate(template, sizeX, sizeZ)

    /** IN7: the template tile instance tile [coord] shows, or null. */
    public fun toTemplate(coord: CoordGrid): CoordGrid? = i.toTemplate(coord)

    override fun equals(other: Any?): Boolean = other is InstanceRef && other.i === i

    override fun hashCode(): Int = i.hashCode()

    private companion object {
        const val SQUARE_SIZE = 64
        const val ZONE_ALIGN = 7.inv()
    }
}

/**
 * A whole template map square to copy into an instance (IN7): square ([templateSquareX], [templateSquareZ]) to the
 * instance's square ([dx], [dz]) counted from its south-west square, turned [rotation] quarter turns; [levels] levels
 * from [templateLevel] to [level].
 */
public class InstanceSquare(
    public val templateSquareX: Int,
    public val templateSquareZ: Int,
    public val dx: Int,
    public val dz: Int,
    public val rotation: Int = 0,
    public val templateLevel: Int = 0,
    public val level: Int = 0,
    public val levels: Int = Instances.LEVELS,
)

/**
 * One template zone (holding tile [from]) to copy to the instance zone holding [to], turned [rotation] times. A [to]
 * outside instance space is an offset from the instance's south-west corner (a layout built before the instance is).
 */
public class InstanceZone(public val from: CoordGrid, public val to: CoordGrid, public val rotation: Int = 0)
