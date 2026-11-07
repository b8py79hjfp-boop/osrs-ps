package tp.plugin

import tp.engine.World
import tp.engine.map.WorldEntities
import tp.util.CoordGrid

/**
 * A world entity (a boat; docs/mechanics/world-entities.md): a deck in its own instance, drawn by the client at a
 * moving, turning position in the root world. Create one with [EntityRef.createWorldEntity], copy its deck with
 * [copyZone], [show] it, then [move] it every tick it sails. Players board by being teleported onto [deckBase] tiles.
 */
public class WorldEntityRef internal constructor(
    internal val e: WorldEntities.WorldEntity,
    private val world: World,
) {
    public val index: Int get() = e.index
    public val configId: Int get() = e.configId

    /** South-west tile of the deck (level 0); deck tiles are `deckBase + (0 until sizeX * 8, 0 until sizeZ * 8)`. */
    public val deckBase: CoordGrid get() = e.deckBase

    /** The root-world tile under the entity's pivot. */
    public val rootCoord: CoordGrid get() = e.rootCoord

    /** Pivot in fine units (128 per tile) and facing (0 south, 512 west, 1024 north, 1536 east). */
    public val fineX: Int get() = e.fineX
    public val fineZ: Int get() = e.fineZ
    public val angle: Int get() = e.angle

    /** Whether [coord] is on this entity's deck. */
    public operator fun contains(coord: CoordGrid): Boolean = coord in e

    /** Copies the cache zone holding template tile [from] onto the deck zone holding [to] (see [InstanceRef.copyZone]). */
    public fun copyZone(from: CoordGrid, to: CoordGrid, rotation: Int = 0) {
        world.instances.copyZone(e.instance, from, to, rotation)
    }

    /** Shows the entity to players (after its deck is copied). */
    public fun show() {
        world.worldEntities.show(e)
    }

    /** Moves the pivot to ([fineX], [fineZ]) facing [angle]; [teleport] jumps instead of gliding. */
    public fun move(fineX: Int, fineZ: Int, angle: Int = e.angle, teleport: Boolean = false) {
        world.worldEntities.move(e, fineX, fineZ, angle, teleport)
    }

    /** Removes the entity and its deck (move players off first). */
    public fun remove() {
        world.worldEntities.remove(e)
    }

    override fun equals(other: Any?): Boolean = other is WorldEntityRef && other.e === e

    override fun hashCode(): Int = e.hashCode()
}
