package tp.engine.map

import tp.engine.zone.ZoneKey
import tp.protocol.api.GameNetwork
import tp.protocol.api.WorldEntitySpec
import tp.util.CoordGrid

/**
 * World entities of one world (docs/mechanics/world-entities.md): boats and other moving worlds. Each owns an
 * instance ([Instances]) holding its deck, copied from template zones; players and NPCs on board stand on the
 * instance's tiles, and the client draws the deck at the entity's position in the root world, turned by its angle.
 */
class WorldEntities internal constructor(
    private val instances: Instances,
    private val network: () -> GameNetwork,
) {
    /**
     * One world entity: config [configId] (archive 2 group 72), [sizeX] × [sizeZ] zones of deck in [instance] from its
     * south-west corner, with activity on [activeLevel].
     */
    class WorldEntity internal constructor(
        val index: Int,
        val configId: Int,
        val instance: Instances.Instance,
        val sizeX: Int,
        val sizeZ: Int,
        val activeLevel: Int,
        val ownerIndex: Int,
    ) {
        /** Pivot in the root world, fine units (128 per tile). */
        var fineX: Int = 0
            internal set
        var fineZ: Int = 0
            internal set

        /** Root level the entity renders on. */
        var level: Int = 0
            internal set

        /** 0 south, 512 west, 1024 north, 1536 east. */
        var angle: Int = 0
            internal set

        var visible: Boolean = false
            internal set

        internal var moved = false
        internal var teleported = false

        /** The root-world tile under the pivot. */
        val rootCoord: CoordGrid get() = CoordGrid(fineX shr FINE_SHIFT, fineZ shr FINE_SHIFT, level)

        /** South-west tile of the deck in the instance (level 0). */
        val deckBase: CoordGrid get() = CoordGrid(instance.baseX, instance.baseZ, 0)

        operator fun contains(coord: CoordGrid): Boolean =
            coord in instance &&
                coord.x - instance.baseX in 0 until sizeX * ZONE_SIZE &&
                coord.z - instance.baseZ in 0 until sizeZ * ZONE_SIZE
    }

    private val byIndex = HashMap<Int, WorldEntity>()
    private val byInstance = HashMap<Int, WorldEntity>()

    /**
     * Creates a world entity (not yet shown): an empty deck of [sizeX] × [sizeZ] zones at root position ([fineX],
     * [fineZ]) on [level]. Copy the deck's zones into [WorldEntity.instance], then call [show].
     */
    fun create(
        configId: Int,
        sizeX: Int,
        sizeZ: Int,
        activeLevel: Int,
        fineX: Int,
        fineZ: Int,
        level: Int,
        angle: Int,
        ownerIndex: Int = 0,
    ): WorldEntity {
        require(sizeX in 1..MAX_ZONES && sizeZ in 1..MAX_ZONES) { "world entity size $sizeX×$sizeZ zones" }
        val index = (1..MAX_INDEX).firstOrNull { it !in byIndex } ?: error("world entity indices exhausted")
        val instance = instances.create(1, 1)
        val entity = WorldEntity(index, configId, instance, sizeX, sizeZ, activeLevel, ownerIndex)
        entity.fineX = fineX
        entity.fineZ = fineZ
        entity.level = level
        entity.angle = angle and ANGLE_MASK
        byIndex[index] = entity
        byInstance[instance.id] = entity
        return entity
    }

    /** Makes [entity] visible to players (WE1); its deck zones must be copied first. */
    fun show(entity: WorldEntity) {
        if (entity.visible) return
        entity.visible = true
        network().worldEntityAdded(spec(entity))
    }

    /** Moves [entity]'s pivot to ([fineX], [fineZ]) facing [angle]; [teleport] jumps instead of gliding (WE3). */
    fun move(entity: WorldEntity, fineX: Int, fineZ: Int, angle: Int, teleport: Boolean = false) {
        entity.fineX = fineX
        entity.fineZ = fineZ
        entity.angle = angle and ANGLE_MASK
        entity.moved = true
        entity.teleported = entity.teleported || teleport
    }

    /** Removes [entity] and tears its deck down (players on board must be moved off first). */
    fun remove(entity: WorldEntity) {
        if (byIndex.remove(entity.index) == null) return
        byInstance.remove(entity.instance.id)
        if (entity.visible) network().worldEntityRemoved(entity.index)
        entity.visible = false
        instances.destroy(entity.instance)
    }

    /** The world entity whose deck holds [coord], or null. */
    fun at(coord: CoordGrid): WorldEntity? {
        val instance = instances.at(coord) ?: return null
        return byInstance[instance.id]?.takeIf { coord in it }
    }

    /** Every world entity (check [WorldEntity.visible]); a live view, no copy (info-pass hot loop). */
    fun all(): Collection<WorldEntity> = byIndex.values

    /** The world entity built on [instance], or null. */
    fun of(instance: Instances.Instance): WorldEntity? = byInstance[instance.id]

    /** Info pass: movement of this tick goes to the network (WE3). */
    internal fun sync() {
        for (entity in byIndex.values) {
            if (!entity.moved || !entity.visible) continue
            network().worldEntityMoved(entity.index, entity.level, entity.fineX, entity.fineZ, entity.angle, entity.teleported)
            entity.moved = false
            entity.teleported = false
        }
    }

    private fun spec(entity: WorldEntity): WorldEntitySpec {
        val swX = entity.instance.baseX shr ZONE_SHIFT
        val swZ = entity.instance.baseZ shr ZONE_SHIFT
        val zones = IntArray(LEVELS * entity.sizeX * entity.sizeZ) { Instances.NO_ZONE }
        var i = 0
        for (level in 0 until LEVELS) {
            for (dx in 0 until entity.sizeX) {
                for (dz in 0 until entity.sizeZ) {
                    zones[i++] = entity.instance.zones[ZoneKey.of(level, swX + dx, swZ + dz).packed] ?: Instances.NO_ZONE
                }
            }
        }
        return WorldEntitySpec(
            entity.index,
            entity.configId,
            entity.ownerIndex,
            entity.sizeX,
            entity.sizeZ,
            swX,
            swZ,
            0,
            LEVELS - 1,
            entity.fineX,
            entity.fineZ,
            entity.level,
            entity.activeLevel,
            entity.angle,
            zones,
        )
    }

    companion object {
        /** Fine units per tile (RSProt `CoordFine`). */
        const val FINE_SHIFT = 7

        /** RSProt `WorldEntityAvatarRepository`: indices 1..4094. */
        private const val MAX_INDEX = 4094

        /** REBUILD_WORLDENTITY takes at most 12 zones per axis; one instance square holds 8. */
        private const val MAX_ZONES = 8
        private const val ZONE_SHIFT = 3
        private const val ZONE_SIZE = 8
        private const val LEVELS = 4
        private const val ANGLE_MASK = 2047
    }
}
