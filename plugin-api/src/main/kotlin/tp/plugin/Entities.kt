package tp.plugin

import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.engine.interact.InteractionTarget
import tp.engine.interact.NpcInteraction
import tp.engine.interact.currentInteraction
import tp.engine.interact.interactWith
import tp.engine.map.Instances
import tp.engine.script.QueueType
import tp.engine.script.Script
import tp.protocol.api.Visual
import tp.util.CoordGrid
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/** Typical projectile launch angle (RS Mod projanim defaults). */
private const val DEFAULT_ANGLE = 15

/** Fine units (RSProt `CoordFine`): 128 per tile; a tile centre is +64. */
private const val FINE_SHIFT = 7
private const val HALF_TILE = 64

/** Collision flags that stop a tile being stood on (rsmod-pathfinder `CollisionFlag.LOC` | `BLOCK_WALK`). */
private const val WALK_BLOCKERS: Int = org.rsmod.routefinder.flag.CollisionFlag.LOC or org.rsmod.routefinder.flag.CollisionFlag.BLOCK_WALK

/** Bus for entities in a world without plugin content (engine tests): nothing subscribes. */
private val NO_EVENTS = EventBus()

/** A typed key for per-entity content state (clocks, flags, timers). Compared by identity. */
public class AttrKey<T : Any>(public val name: String) {
    override fun toString(): String = "AttrKey($name)"
}

/** What players and NPCs share for combat: position, hitpoints, state, visuals, hit queueing. */
public sealed class EntityRef {
    internal abstract val e: Entity

    public val coord: CoordGrid get() = e.coord
    public val size: Int get() = e.size
    public val tick: Long get() = e.world.tick

    public var hitpoints: Int
        get() = e.hitpoints
        set(value) {
            e.hitpoints = value
        }

    public val maxHitpoints: Int get() = e.maxHitpoints

    /** Movement blocked until this tick (exclusive). */
    public var frozenUntil: Long
        get() = e.frozenUntil
        set(value) {
            e.frozenUntil = value
        }

    public val isFrozen: Boolean get() = e.isFrozen

    @Suppress("UNCHECKED_CAST") // keys are typed
    public operator fun <T : Any> get(key: AttrKey<T>): T? = e.attrs[key] as T?

    public operator fun <T : Any> set(key: AttrKey<T>, value: T?) {
        if (value == null) e.attrs.remove(key) else e.attrs[key] = value
    }

    /** The content event bus of this entity's world (one per world; content modules talk through it). */
    public val events: EventBus get() = (e.world.content as? PluginContent)?.events ?: NO_EVENTS

    /** World-scoped content state for [key] in this entity's world (plugins are shared by every world in the JVM). */
    @Suppress("UNCHECKED_CAST") // keys are typed
    public fun <T : Any> worldAttr(key: AttrKey<T>): T? = e.world.attrs[key] as T?

    public fun <T : Any> setWorldAttr(key: AttrKey<T>, value: T?) {
        if (value == null) e.world.attrs.remove(key) else e.world.attrs[key] = value
    }

    /** The online player called [username] in this entity's world (case-insensitive), or null. */
    public fun onlinePlayer(username: String): PlayerRef? {
        var found: tp.engine.entity.Player? = null
        e.world.forEachPlayer { if (found == null && it.username.equals(username, ignoreCase = true)) found = it }
        return found?.let { PlayerRef(it) }
    }

    public fun anim(seq: Int, delay: Int = 0) {
        e.visual(Visual.Animate(seq, delay))
    }

    public fun spotAnim(id: Int, height: Int = 0, delay: Int = 0, slot: Int = 0) {
        e.visual(Visual.SpotAnim(slot, id, height, delay))
    }

    /** A hitsplat; [selfType]/[sourceType]/[otherType] are hitmark ids (`otherType` -1 = private). */
    public fun hitsplat(source: EntityRef?, value: Int, selfType: Int, sourceType: Int, otherType: Int, delay: Int = 0) {
        e.visual(Visual.Hit(source?.e?.avatarRef, selfType, sourceType, otherType, value, delay))
    }

    public fun headbar(source: EntityRef?, bar: Int, fill: Int) {
        e.visual(Visual.HeadBar(source?.e?.avatarRef, bar, fill))
    }

    public fun face(target: EntityRef?) {
        e.visual(Visual.Face(target?.e?.avatarRef))
    }

    public fun say(text: String) {
        e.visual(Visual.Say(text))
    }

    /**
     * Queues [action] on this entity [delay] ticks from now: a strong queue script on players, the NPC queue on NPCs
     * (combat-timeline.md: hits are strong scripts on the defender).
     */
    public fun queueHit(delay: Int, strong: Boolean = true, action: () -> Unit) {
        when (val x = e) {
            is Player -> x.queue.add(if (strong) QueueType.STRONG else QueueType.NORMAL, delay) { action() }
            is Npc -> x.queue.add(delay) { action() }
        }
    }

    /** Line of sight from this entity to [other] (footprints, projectile rules). */
    public fun hasLineOfSight(other: EntityRef): Boolean = e.world.hasLineOfSight(e, other.e)

    /** Every player in this world. */
    public fun playersInWorld(): List<PlayerRef> {
        val out = ArrayList<PlayerRef>()
        e.world.forEachPlayer { out += PlayerRef(it) }
        return out
    }

    /** Players within [radius] tiles (Chebyshev, same level), excluding this entity. */
    public fun playersWithin(radius: Int): List<PlayerRef> {
        val out = ArrayList<PlayerRef>()
        e.world.forEachPlayer { p ->
            if (p !== e && p.coord.level == coord.level &&
                maxOf(kotlin.math.abs(p.coord.x - coord.x), kotlin.math.abs(p.coord.z - coord.z)) <= radius
            ) {
                out += PlayerRef(p)
            }
        }
        return out
    }

    /** NPCs in the world within [radius] tiles of [centre] (Chebyshev from their south-west tile, same level). */
    public fun npcsWithin(centre: CoordGrid, radius: Int): List<NpcRef> {
        val out = ArrayList<NpcRef>()
        e.world.forEachNpc { n ->
            if (!n.hidden && n.coord.level == centre.level &&
                maxOf(kotlin.math.abs(n.coord.x - centre.x), kotlin.math.abs(n.coord.z - centre.z)) <= radius
            ) {
                out += NpcRef(n)
            }
        }
        return out
    }

    /** Players within [radius] tiles of [centre] (Chebyshev, same level). */
    public fun playersAround(centre: CoordGrid, radius: Int): List<PlayerRef> {
        val out = ArrayList<PlayerRef>()
        e.world.forEachPlayer { p ->
            if (p.coord.level == centre.level &&
                maxOf(kotlin.math.abs(p.coord.x - centre.x), kotlin.math.abs(p.coord.z - centre.z)) <= radius
            ) {
                out += PlayerRef(p)
            }
        }
        return out
    }

    /** Whether this world is a PvP world (WorldConfig). */
    public val isPvpWorld: Boolean get() = e.world.config.pvpWorld

    /** Ticks until a hit queued on this entity now with [delay] lands (includes the processing-order +1). */
    public fun ticksUntilHit(delay: Int): Int = e.ticksUntilQueued(delay)

    /**
     * A projectile (spot anim [id]) from this entity to [target], arriving after [endCycles] client cycles. Heights are
     * MAP_PROJANIM_V2 units (no implicit × 4); [progress] is the fine-unit offset it starts from its source.
     */
    public fun projectile(
        target: EntityRef,
        id: Int,
        startHeight: Int,
        endHeight: Int,
        startCycles: Int,
        endCycles: Int,
        angle: Int = DEFAULT_ANGLE,
        progress: Int = 0,
    ) {
        e.world.projectile(e, target.e, id, startHeight, endHeight, startCycles, endCycles, angle, progress)
    }

    /** A spot anim on the tile [at] (MAP_ANIM), seen by everyone near it. */
    public fun spotAnimAt(at: CoordGrid, id: Int, height: Int = 0, delay: Int = 0) {
        e.world.mapAnim(at, id, height, delay)
    }

    /** Spawns NPC [type] at [at] (wandering [wanderRange]) and returns it; it respawns like any NPC unless removed. */
    public fun spawnNpc(type: Int, at: CoordGrid, wanderRange: Int = 0): NpcRef = NpcRef(e.world.spawnNpc(type, at, wanderRange))

    /** Allocates an empty instanced area of [widthSquares] × [lengthSquares] map squares (64 tiles each) in this world. */
    public fun createInstance(widthSquares: Int = 1, lengthSquares: Int = 1): InstanceRef =
        InstanceRef(e.world.instances.create(widthSquares, lengthSquares), e.world)

    /**
     * IN6: an instance of [widthSquares] x [lengthSquares] map squares whose whole template [squares] (and single
     * [zones]) are copied off the world thread; it is usable once [onReady] runs, in the next tick's pre-tick.
     */
    public fun buildInstance(
        widthSquares: Int,
        lengthSquares: Int,
        squares: List<InstanceSquare>,
        zones: List<InstanceZone> = emptyList(),
        onReady: (InstanceRef) -> Unit,
    ): InstanceRef {
        val w = e.world
        val built =
            w.buildInstance(
                widthSquares,
                lengthSquares,
                zones.map { Instances.ZoneCopy(it.from, it.to, it.rotation) },
                squares.map {
                    Instances.SquareCopy(
                        it.templateSquareX,
                        it.templateSquareZ,
                        it.dx,
                        it.dz,
                        it.rotation,
                        it.templateLevel,
                        it.level,
                        it.levels,
                    )
                },
            ) { onReady(InstanceRef(it, w)) }
        return InstanceRef(built, w)
    }

    /**
     * Creates a world entity (boat) of config [configId] with a [sizeX] × [sizeZ]-zone deck whose activity is on
     * [activeLevel], at root tile [at] facing [angle]. Copy its deck, then [WorldEntityRef.show] it.
     */
    public fun createWorldEntity(configId: Int, sizeX: Int, sizeZ: Int, activeLevel: Int, at: CoordGrid, angle: Int = 0): WorldEntityRef =
        WorldEntityRef(
            e.world.worldEntities.create(
                configId,
                sizeX,
                sizeZ,
                activeLevel,
                (at.x shl FINE_SHIFT) + HALF_TILE,
                (at.z shl FINE_SHIFT) + HALF_TILE,
                at.level,
                angle,
                (this as? PlayerRef)?.slot ?: 0,
            ),
            e.world,
        )

    /** The world entity whose deck holds [coord] (the boat a player stands on), or null. */
    public fun worldEntityAt(coord: CoordGrid): WorldEntityRef? =
        e.world.worldEntities
            .at(coord)
            ?.let { WorldEntityRef(it, e.world) }

    /** The live instance holding [coord], or null. */
    public fun instanceAt(coord: CoordGrid): InstanceRef? =
        e.world.instances
            .at(coord)
            ?.let { InstanceRef(it, e.world) }

    /** Puts loc [id] on the tile (replacing its layer); reverts after [duration] ticks if positive. */
    public fun addLoc(id: Int, at: CoordGrid, shape: Int, angle: Int, duration: Int = -1) {
        e.world.locs.add(id, at, shape, angle, e.world.tick, duration)
    }

    /** Removes the loc of [shape]'s layer on the tile; reverts after [duration] ticks if positive. */
    public fun deleteLoc(at: CoordGrid, shape: Int, duration: Int = -1) {
        e.world.locs.delete(at, shape, e.world.tick, duration)
    }

    /** Restores the cache loc of [shape]'s layer on the tile. */
    public fun revertLoc(at: CoordGrid, shape: Int) {
        e.world.locs.revert(
            at.level,
            at.x,
            at.z,
            tp.engine.map.DynamicLocs
                .layerOf(shape),
        )
    }

    /** The loc on [shape]'s layer at the tile right now, or null. */
    public fun locAt(at: CoordGrid, shape: Int): LocRef? =
        e.world.locs
            .at(
                at.level,
                at.x,
                at.z,
                tp.engine.map.DynamicLocs
                    .layerOf(shape),
            )?.let { LocRef(it.id, it.coord, it.shape, it.angle) }

    /** Whether [tile] (level 0 of the root world) is sea, where boats may sail (sailing-movement.md SM1). */
    public fun isSea(tile: CoordGrid): Boolean =
        e.world.data.locs
            .isSea(tile.x, tile.z)

    /** Int values of cache enum [id] (key → value); empty if absent. */
    public fun enumInts(id: Int): Map<Int, Int> =
        e.world.data.configs.enums[id]
            ?.intValues
            .orEmpty()

    /** String values of cache enum [id] (key -> value); empty if absent. */
    public fun enumStrings(id: Int): Map<Int, String> =
        e.world.data.configs.enums[id]
            ?.stringValues
            .orEmpty()

    /** Whether a boat may occupy [tile]: sea, with no blocking loc such as a rock (sailing-movement.md SM1, SM5). */
    public fun isSailable(tile: CoordGrid): Boolean =
        e.world.data.locs
            .isSea(tile.x, tile.z) &&
            e.world.collision.flags[tile.x, tile.z, 0] and org.rsmod.routefinder.flag.CollisionFlag.LOC == 0

    /** Whether [tile] can be stood on: no blocking loc and walkable floor (collision flags). */
    public fun isWalkable(tile: CoordGrid): Boolean =
        e.world.collision.flags[tile.x, tile.z, tile.level] and WALK_BLOCKERS == 0

    /** Whether a [size]-tile entity on [from] could step by ([dx], [dz]) (collision only: knockbacks, AoE checks). */
    public fun canStep(from: CoordGrid, dx: Int, dz: Int, size: Int = 1): Boolean =
        e.world.movement.canTravel(from.level, from.x, from.z, dx, dz, size)

    /** Records [label] for this entity in the tick inspector (no-op unless tracing). */
    public fun trace(label: String) {
        e.world.tracer?.onPhase(e, label)
    }

    /** Uniform random int in [0, bound) from the world's only RNG (brief §5.2: deterministic). */
    public fun random(bound: Int): Int = e.world.rng.nextInt(bound)

    /** Chebyshev distance between the two footprints, edge to edge (hit-delay distance, HD:3). */
    public fun distanceTo(other: EntityRef): Int {
        val a = coord
        val b = other.coord
        val dx = maxOf(0, b.x - (a.x + size - 1), a.x - (b.x + other.size - 1))
        val dz = maxOf(0, b.z - (a.z + size - 1), a.z - (b.z + other.size - 1))
        return maxOf(dx, dz)
    }
}

/** Content's view of an NPC. */
public class NpcRef internal constructor(internal val n: Npc) : EntityRef() {
    override val e: Entity get() = n
    public val type: Int get() = n.type
    public val index: Int get() = n.index

    /** Current combat level: 0 attack, 1 defence, 2 strength, 3 hitpoints, 4 ranged, 5 magic (cache order). */
    public fun level(stat: Int): Int = n.levels[stat]

    public fun setLevel(stat: Int, value: Int) {
        n.levels[stat] = value.coerceAtLeast(0)
    }

    /** The NPC's base level for [stat] (same order as [level]): its own override (IN10) or its config's; drains are measured against it. */
    public fun baseLevel(stat: Int): Int =
        n.baseStats?.getOrNull(stat) ?: n.world.data.configs.npcs[n.type]
            ?.stats
            ?.getOrNull(stat) ?: 0

    /**
     * IN10: gives this NPC its own base levels ([levels] order: attack, defence, strength, hitpoints, ranged, magic)
     * and, when [maxHit] >= 0, its own max hit; shared NPC configs are untouched (raid scaling).
     */
    public fun overrideStats(levels: IntArray, maxHit: Int = -1) {
        n.overrideStats(levels)
        n.maxHitOverride = maxHit
    }

    /** IN10: this NPC's own max hit, or -1 when its combat data's applies. */
    public val maxHitOverride: Int get() = n.maxHitOverride

    /** The NPC's name as players see it (cache name). */
    public val displayName: String get() =
        n.world.data.configs.npcs[n.type]
            ?.name ?: "null"

    /** The NPC config's op text for [op] (1-based), or null. */
    public fun opName(op: Int): String? =
        n.world.data.configs.npcs[n.type]
            ?.ops
            ?.ops
            ?.getOrNull(op - 1)

    /** Npc config param [id] (int), or [default]. */
    public fun param(id: Int, default: Int): Int =
        n.world.data.configs.npcs[n.type]
            ?.params
            ?.int(id) ?: default

    public val isInWorld: Boolean get() = !n.hidden && n.world.npc(n.index) === n

    /** Hunt range in tiles from the south-west tile; 0 = doesn't hunt (aggression.md). */
    public var huntRange: Int
        get() = n.huntRange
        set(value) {
            n.huntRange = value.coerceAtLeast(0)
        }

    /** Combat level from the cache (-1 when not attackable by level). */
    public val combatLevel: Int get() =
        n.world.data.configs.npcs[n.type]
            ?.combatLevel ?: -1

    /** How far from its spawn the NPC may move while fighting (default 7). */
    public val maxRange: Int get() = n.maxRange

    /** Starts (or, with null, stops) retreating from [from] (osrs-docs Retreat); stops any interaction. */
    public fun retreat(from: EntityRef?) {
        if (from != null) n.interactWith(null)
        n.retreatFrom = from?.e
    }

    /** Whether the NPC is retreating. */
    public val isRetreating: Boolean get() = n.retreatFrom != null

    /** Where the NPC (re)spawns. */
    public val spawn: CoordGrid get() = n.spawn

    /** The type the NPC spawns as (before any [transform]). */
    public val baseType: Int get() = n.baseType

    /** Turns the NPC into [newType] (phase changes); with [restoreStats] its levels and hitpoints become the new type's. */
    public fun transform(newType: Int, restoreStats: Boolean) {
        n.transform(newType, restoreStats)
    }

    /** Walks toward [to] (naive NPC stepping), replacing any route; the interaction, if any, keeps steering. */
    public fun walkTo(to: CoordGrid) {
        n.walkTo(to.x, to.z)
    }

    /** Removes the NPC for good (minions); it won't respawn. */
    public fun despawn() {
        n.world.removeNpc(n)
    }

    /** Removes the NPC now and respawns it at its spawn point after [delay] ticks. */
    public fun respawnAfter(delay: Int) {
        n.world.respawnNpc(n, delay)
    }

    /** Makes this NPC attack/follow [target], running [script] whenever it is in op range (I9). */
    public fun interact(target: EntityRef, script: suspend NpcScript.(EntityRef) -> Unit) {
        val s: Script<Npc> = { runNpc(NpcScript(this), target, script) }
        n.interactWith(NpcInteraction(interactionTarget(target), s, null))
    }

    /**
     * Makes this NPC attack [target] from up to [range] tiles (with line of sight): [atRange] runs when in approach
     * range but not op range, [inReach] (if any) in op range. With [closeIn] the NPC keeps walking toward op range while
     * attacking from range; without it, it stops once in range.
     */
    public fun interactAtRange(
        target: EntityRef,
        range: Int,
        closeIn: Boolean,
        inReach: (suspend NpcScript.(EntityRef) -> Unit)?,
        atRange: suspend NpcScript.(EntityRef) -> Unit,
    ) {
        val op: Script<Npc>? = inReach?.let { s -> { runNpc(NpcScript(this), target, s) } }
        val ap: Script<Npc> = { runNpc(NpcScript(this), target, atRange) }
        val i = NpcInteraction(interactionTarget(target), op, ap, closeIn)
        i.apRange = range
        n.interactWith(i)
    }

    private fun interactionTarget(target: EntityRef): InteractionTarget =
        when (target) {
            is PlayerRef -> InteractionTarget.OfPlayer(target.p)
            is NpcRef -> InteractionTarget.OfNpc(target.n)
        }

    /** Stops the NPC's interaction. */
    public fun stopInteraction() {
        n.interactWith(null)
    }

    /** What this NPC is interacting with, or null. */
    public val target: EntityRef?
        get() =
            when (val t = n.currentInteraction?.target) {
                is InteractionTarget.OfPlayer -> PlayerRef(t.player)
                is InteractionTarget.OfNpc -> NpcRef(t.npc)
                else -> null
            }

    override fun equals(other: Any?): Boolean = other is NpcRef && other.n === n

    override fun hashCode(): Int = n.hashCode()

    override fun toString(): String = n.toString()
}

/** Receiver of NPC scripts (attack scripts). Restricted like [PlayerScript]. */
@RestrictsSuspension
public class NpcScript internal constructor(private val scope: tp.engine.script.ScriptScope<Npc>) {
    public val npc: NpcRef = NpcRef(scope.self)

    public suspend fun delay(ticks: Int) {
        scope.delay(ticks)
    }
}

private suspend fun <T> runNpc(scope: NpcScript, arg: T, s: suspend NpcScript.(T) -> Unit) {
    val body: suspend NpcScript.() -> Unit = { s(arg) }
    suspendCoroutineUninterceptedOrReturn { c -> body.startCoroutineUninterceptedOrReturn(scope, c) }
}
