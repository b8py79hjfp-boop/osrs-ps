package tp.plugin

import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.engine.interact.InteractionTarget
import tp.engine.interact.NpcInteraction
import tp.engine.interact.currentInteraction
import tp.engine.interact.interactWith
import tp.engine.script.QueueType
import tp.engine.script.Script
import tp.protocol.api.Visual
import tp.util.CoordGrid
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

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
    public fun queueHit(delay: Int, action: () -> Unit) {
        when (val x = e) {
            is Player -> x.queue.add(QueueType.STRONG, delay) { action() }
            is Npc -> x.queue.add(delay) { action() }
        }
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

    /** Removes the NPC now and respawns it at its spawn point after [delay] ticks. */
    public fun respawnAfter(delay: Int) {
        n.world.respawnNpc(n, delay)
    }

    /** Makes this NPC attack/follow [target], running [script] whenever it is in op range (I9). */
    public fun interact(target: EntityRef, script: suspend NpcScript.(EntityRef) -> Unit) {
        val t =
            when (target) {
                is PlayerRef -> InteractionTarget.OfPlayer(target.p)
                is NpcRef -> InteractionTarget.OfNpc(target.n)
            }
        val s: Script<Npc> = { runNpc(NpcScript(this), target, script) }
        n.interactWith(NpcInteraction(t, s, null))
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
