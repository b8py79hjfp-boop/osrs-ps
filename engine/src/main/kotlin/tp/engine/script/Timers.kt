package tp.engine.script

import tp.engine.entity.Npc
import tp.engine.entity.Player

/** Identifies a timer; adding a timer with a key already present replaces it. Content defines keys as objects. */
open class TimerKey(val name: String) {
    override fun toString(): String = "TimerKey($name)"
}

internal class TimerState<E : tp.engine.entity.Entity>(
    val key: TimerKey,
    val interval: Int,
    var remaining: Int,
    val soft: Boolean,
    val script: Script<E>,
)

/**
 * Player timers (docs/mechanics/scripts.md T1–T6). No limit. New timers are pending until the end of the timers step
 * so they never fire on the tick they're added (T2). Soft timers fire regardless of state; normal timers halt at 0
 * while delayed or a modal is open and retry each tick (T4).
 */
class PlayerTimers internal constructor(private val player: Player) {
    private val active = LinkedHashMap<TimerKey, TimerState<Player>>()
    private val pending = LinkedHashMap<TimerKey, TimerState<Player>>()
    private val removed = HashSet<TimerKey>()

    fun add(key: TimerKey, interval: Int, soft: Boolean, script: Script<Player>) {
        require(interval > 0) { "timer interval must be positive" }
        removed.remove(key)
        pending[key] = TimerState(key, interval, interval, soft, script)
    }

    fun remove(key: TimerKey) {
        pending.remove(key)
        if (key in active) removed += key
    }

    fun has(key: TimerKey): Boolean = (key in active && key !in removed) || key in pending

    internal fun process() {
        if (active.isNotEmpty()) {
            for (t in active.values.toList()) {
                if (t.key in removed) continue
                if (t.remaining > 0) t.remaining--
                if (t.remaining > 0) continue
                if (!t.soft && (player.isDelayed || player.hasModal)) continue // halt at 0, retry next tick
                t.remaining = t.interval
                ScriptRunner.start(player, t.script)
            }
        }
        for (k in removed) active.remove(k)
        removed.clear()
        if (pending.isNotEmpty()) {
            active.putAll(pending)
            pending.clear()
        }
    }
}

/** NPC timer: at most one, always soft (T3, T5). Adding replaces the existing one. */
class NpcTimer internal constructor(private val npc: Npc) {
    private var current: TimerState<Npc>? = null
    private var pending: TimerState<Npc>? = null

    fun set(key: TimerKey, interval: Int, script: Script<Npc>) {
        require(interval > 0) { "timer interval must be positive" }
        pending = TimerState(key, interval, interval, true, script)
    }

    fun clear() {
        current = null
        pending = null
    }

    internal fun process() {
        current?.let { t ->
            if (t.remaining > 0) t.remaining--
            if (t.remaining == 0) {
                t.remaining = t.interval
                ScriptRunner.start(npc, t.script)
            }
        }
        pending?.let {
            current = it
            pending = null
        }
    }
}
