package tp.engine.script

import tp.engine.entity.Entity
import tp.engine.entity.Player
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Content scripts are Kotlin `suspend` code run synchronously on the world thread (brief §5.4): a script starts inline
 * where it is triggered and runs until it finishes or suspends. Resumption also happens inline, from the tick
 * pipeline. Stdlib intrinsics only: no dispatcher, no thread hops. docs/mechanics/scripts.md.
 */
typealias Script<E> = suspend ScriptScope<E>.() -> Unit

/**
 * The receiver of every script. Suspension points live here. Content never sees this type: plugin-api wraps it in a
 * `@RestrictsSuspension` scope, which keeps foreign suspends (thread hops) out of content.
 */
class ScriptScope<E : Entity> internal constructor(val self: E) {
    /** Normal delay (D1): delays the entity for [ticks] and suspends this script until the delay ends (D3). */
    suspend fun delay(ticks: Int) {
        require(ticks > 0) { "delay must be positive: $ticks" }
        self.delayUntil(self.world.tick + ticks)
        suspendCoroutine { self.suspended += it }
    }

    /** Arrive delay (D1): one tick if the entity moved this tick or last tick, otherwise returns immediately. */
    suspend fun arriveDelay() {
        if (self.movedRecently()) delay(1)
    }

    /**
     * Suspends until the player answers (pause button, count/name/string dialog). [Player.resumeInput] resumes it
     * from the client-input phase.
     */
    suspend fun awaitInput(): Any {
        val player = self as? Player ?: error("Only players can wait for input")
        return suspendCoroutine { player.inputWait = it }
    }
}

/**
 * Proof that the engine allowed a script to mutate a player now: not delayed and no modal open (brief §5.4).
 * Functions that change a player require `context(access: ProtectedAccess)`, so this is checked at compile time.
 */
class ProtectedAccess internal constructor(val player: Player)

internal object ScriptRunner {
    /** Starts [script] on [entity] now. Runs until it finishes or first suspends. */
    fun <E : Entity> start(entity: E, script: Script<E>) {
        val scope = ScriptScope(entity)
        script.createCoroutine(scope, Completion(entity)).resume(Unit)
    }

    /** Resumes scripts whose delay ended (delay-end phase, D3). Returns whether anything resumed. */
    fun resumeDelayed(entity: Entity): Boolean {
        if (entity.suspended.isEmpty() || entity.isDelayed) return false
        val ready = ArrayList(entity.suspended)
        entity.suspended.clear()
        for (continuation in ready) continuation.resume(Unit)
        return true
    }

    private class Completion(private val entity: Entity) : Continuation<Unit> {
        override val context: CoroutineContext = EmptyCoroutineContext

        override fun resumeWith(result: Result<Unit>) {
            result.exceptionOrNull()?.let { entity.world.onScriptError(entity, it) }
        }
    }
}
