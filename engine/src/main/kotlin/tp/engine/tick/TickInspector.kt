package tp.engine.tick

import tp.engine.TickTracer
import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.protocol.api.ClientCommand
import tp.protocol.api.ServerMessage
import java.io.Writer

/**
 * Tick inspector (brief §5.5): every per-entity step of the tick pipeline (input, script starts with their phase,
 * movement, invalid input) as one JSONL line per event when [out] is set, and streamed to the chatbox of players who
 * typed `::trace`. Debug tool: it allocates per event, so production runs keep [out] null and nobody watching.
 */
class TickInspector(private val out: Writer? = null) : TickTracer {
    private val watchers = HashSet<Player>()
    private var tick = 0L

    /** Toggles chatbox tracing of [player]'s own events; returns the new state. */
    fun toggle(player: Player): Boolean = if (watchers.remove(player)) false else watchers.add(player)

    override fun onTickStart(tick: Long) {
        this.tick = tick
        if (watchers.isNotEmpty()) watchers.removeIf { !it.session.isOpen } // the predicate lambda allocates per call
    }

    override fun onTickEnd(tick: Long) {
        out?.flush()
    }

    override fun onCommand(player: Player, command: ClientCommand) = event(player, "input $command")

    override fun onPhase(entity: Entity, phase: String) = event(entity, phase)

    override fun onInvalid(player: Player, command: ClientCommand) = event(player, "invalid $command")

    private fun event(entity: Entity, what: String) {
        if (out != null) {
            val id =
                when (entity) {
                    is Player -> "\"player\":${entity.slot},\"pid\":${entity.world.pidOf(entity.slot)}"
                    is Npc -> "\"npc\":${entity.index}"
                    else -> "\"entity\":\"${escape(entity.toString())}\""
                }
            out.write("{\"tick\":$tick,$id,\"event\":\"${escape(what)}\"}\n")
        }
        if (entity is Player && entity in watchers) entity.session.send(ServerMessage.GameMessage("[$tick] $what"))
    }

    private fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
