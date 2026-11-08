package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * The four puzzle rooms' rules (docs/mechanics/raids/toa/puzzles.md): this plugin registers each room's handlers and
 * drives their per-tick state from one world tick. Room setup runs from [ToaFlow.roomStarted]; a wipe's retry
 * ([ToaRooms]) enters the room again, so every setup starts from scratch except what the doc says is saved.
 */
public class ToaPuzzles : ContentPlugin {
    override fun ContentScope.register() {
        ToaApmeken.register(this)
        ToaCrondis.register(this)
        ToaHet.register(this)
        ToaScabaras.register(this)
        onWorldTick { ToaPuzzles.tick(this) }
    }

    internal companion object {
        /** PC4 / AP2 / CR3 / HE3: items the puzzle rooms hand out, removed when the raider leaves the raid. */
        val RAID_ONLY: Set<Int> = setOf(Objs.TOA_CRONDIS_WATER_CONTAINER, Objs.TOA_HET_MIRROR, Objs.TOA_PATH_APMEKEN_POTION)

        fun tick(world: WorldRef) {
            for (run in Raids.of(world).activeRuns) {
                if (run.raid.id != ToaRaid.ID) continue
                val room = (run.state as? RunState.InRoom)?.room?.id ?: continue
                val any = anyPlayer(world, run) ?: continue
                when (room) {
                    "apmeken" -> ToaApmeken.tick(run, any)
                    "crondis" -> ToaCrondis.tick(run, any)
                    "het" -> ToaHet.tick(run, any)
                    "scabaras" -> ToaScabaras.tick(run, any)
                    else -> Unit
                }
            }
        }

        /** Called by [ToaFlow.roomStarted]: builds the puzzle room's state. */
        fun roomStarted(run: RaidRun, any: EntityRef, room: String) {
            when (room) {
                "apmeken" -> ToaApmeken.start(run, any)
                "crondis" -> ToaCrondis.start(run, any)
                "het" -> ToaHet.start(run, any)
                "scabaras" -> ToaScabaras.start(run, any)
                else -> Unit
            }
        }

        /** Any member inside and online: the handle for spawning and world queries from a world tick. */
        fun anyPlayer(world: WorldRef, run: RaidRun): PlayerRef? {
            for (m in run.members.values) {
                if (m.inside && m.online) world.onlinePlayer(m.name)?.let { return it }
            }
            return null
        }

        /** Members inside the instance, online and not dead. */
        fun alive(run: RaidRun, any: EntityRef): List<PlayerRef> = run.presentPlayers(any).filter { it.hitpoints > 0 }

        /**
         * A typeless area hit (puzzle hazards): asked of the combat module like a pickpocket's strike; without a combat
         * module the hit is applied here and leaves one hitpoint.
         */
        fun hit(target: PlayerRef, amount: Int) {
            if (amount <= 0 || target.hitpoints <= 0) return
            val request = DamageRequest(target, null, amount, HitStyle.TYPELESS)
            target.events.publish(request)
            if (!request.handled) {
                target.hitpoints = (target.hitpoints - amount).coerceAtLeast(1)
                target.hitsplat(null, amount, HITMARK_DAMAGE, HITMARK_DAMAGE, HITMARK_TINT)
            }
        }

        fun count(p: PlayerRef, obj: Int): Int = p.inv(Invs.INV).total(obj).toInt()

        /** Hands out [n] of [obj] through the transaction API. */
        fun give(p: PlayerRef, obj: Int, n: Int, reason: String): Boolean = p.transaction(reason) { add(Invs.INV, obj, n) }

        fun take(p: PlayerRef, obj: Int, n: Int, reason: String): Boolean = p.transaction(reason) { remove(Invs.INV, obj, n) }

        /** Tile packing for per-tick lookups without allocation. */
        fun key(c: CoordGrid): Int = c.packed

        /** Chebyshev distance on x/z. */
        fun dist(a: CoordGrid, b: CoordGrid): Int = maxOf(kotlin.math.abs(a.x - b.x), kotlin.math.abs(a.z - b.z))

        /** Hitmarks of the normal damage splat (used by every room hazard). */
        private const val HITMARK_DAMAGE = 16
        private const val HITMARK_TINT = 17
    }
}

/** World-scoped per-run state of one room: plugins are shared by every world, so nothing lives in the objects. */
internal class RunStore<S : Any>(name: String) {
    private val key = AttrKey<HashMap<Long, S>>(name)

    private fun map(any: EntityRef): HashMap<Long, S> = any.worldAttr(key) ?: HashMap<Long, S>().also { any.setWorldAttr(key, it) }

    operator fun get(any: EntityRef, run: RaidRun): S? = map(any)[run.id.value]

    /** Stores [state] for [run]; entries of runs that have closed go first (raids-core has no close event to hook). */
    operator fun set(any: EntityRef, run: RaidRun, state: S) {
        val m = map(any)
        val raids =
            tp.content.raids.Raids
                .of(any)
        m.keys.removeIf { it != run.id.value && raids.run(it) == null }
        m[run.id.value] = state
    }

    fun forget(any: EntityRef, run: RaidRun) {
        map(any).remove(run.id.value)
    }
}
