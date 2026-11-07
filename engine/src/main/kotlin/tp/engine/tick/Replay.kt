package tp.engine.tick

import tp.engine.TickTracer
import tp.engine.World
import tp.engine.entity.Player
import tp.protocol.api.ClientCommand
import java.io.Writer

/**
 * Replay log (brief §5.5): the world seed plus every decoded input, login and logout per tick, and a state hash at the
 * end of every tick. `tools/replay` re-runs a log headlessly and diffs the hashes. One event per line, space-separated:
 *
 * ```
 * W <worldId> <seed> <spawnPacked>
 * L <tick> <slot> <username>          admitted in that tick's post-tick
 * C <tick> <slot> <command...>        see CommandCodec
 * X <tick> <slot>                     logged out in that tick's post-tick
 * H <tick> <stateHash>
 * ```
 */
class ReplayRecorder(private val world: World, private val out: Writer) : TickTracer {
    init {
        out.write("W ${world.config.id} ${world.config.seed} ${world.config.spawn.packed}\n")
    }

    override fun onCommand(player: Player, command: ClientCommand) {
        out.write("C ${world.tick} ${player.slot} ${CommandCodec.encode(command)}\n")
    }

    override fun onLogin(player: Player) {
        out.write("L ${world.tick} ${player.slot} ${player.username}\n")
    }

    override fun onLogout(player: Player) {
        out.write("X ${world.tick} ${player.slot}\n")
    }

    override fun onTickEnd(tick: Long) {
        out.write("H $tick ${world.stateHash()}\n")
        out.flush()
    }
}

/** Text form of [ClientCommand]s for replay logs. Strings are percent-encoded so they never contain spaces. */
object CommandCodec {
    /** Field index of a button's optional sub-op (recordings made before IF_SUBOP have four fields). */
    private const val SUBOP_FIELD = 5

    @Suppress("CyclomaticComplexMethod") // one branch per command type
    fun encode(c: ClientCommand): String =
        when (c) {
            is ClientCommand.MoveClick -> "move ${c.x} ${c.z} ${c.ctrl} ${c.minimap}"
            is ClientCommand.Cheat -> "cheat ${str(c.text)}"
            ClientCommand.MapBuildComplete -> "mapbuilt"
            is ClientCommand.OpLoc -> "oploc ${c.option} ${c.id} ${c.x} ${c.z} ${c.ctrl}"
            is ClientCommand.OpNpc -> "opnpc ${c.option} ${c.index} ${c.ctrl}"
            is ClientCommand.OpPlayer -> "opplayer ${c.option} ${c.index} ${c.ctrl}"
            is ClientCommand.OpObj -> "opobj ${c.option} ${c.id} ${c.x} ${c.z} ${c.ctrl}"
            is ClientCommand.OpPlayerT -> "opplayert ${c.index} ${c.component} ${c.sub} ${c.obj} ${c.ctrl}"
            is ClientCommand.OpNpcT -> "opnpct ${c.index} ${c.component} ${c.sub} ${c.obj} ${c.ctrl}"
            is ClientCommand.OpLocT -> "oploct ${c.id} ${c.x} ${c.z} ${c.component} ${c.sub} ${c.obj} ${c.ctrl}"
            is ClientCommand.OpObjT -> "opobjt ${c.id} ${c.x} ${c.z} ${c.component} ${c.sub} ${c.obj} ${c.ctrl}"
            is ClientCommand.Examine -> "examine ${c.kind} ${c.id}"
            is ClientCommand.SetHeading -> "heading ${c.heading}"
            is ClientCommand.OpWorldEntity -> "opworldentity ${c.option} ${c.index} ${c.ctrl}"
            is ClientCommand.ButtonT -> "buttont ${c.fromComponent} ${c.fromSub} ${c.fromObj} ${c.toComponent} ${c.toSub} ${c.toObj}"
            is ClientCommand.Button -> "button ${c.component} ${c.option} ${c.sub} ${c.obj} ${c.subop}"
            is ClientCommand.PublicChat -> "chat ${str(c.text)} ${c.colour} ${c.effect} ${c.type}"
            is ClientCommand.ResumePause -> "resume ${c.component} ${c.sub}"
            is ClientCommand.ResumeString -> "resumestring ${str(c.text)}"
            is ClientCommand.ResumeCount -> "resumecount ${c.count}"
            is ClientCommand.ResumeObj -> "resumeobj ${c.obj}"
            is ClientCommand.ButtonDrag -> "drag ${c.fromComponent} ${c.fromSub} ${c.fromObj} ${c.toComponent} ${c.toSub} ${c.toObj}"
            ClientCommand.CloseModal -> "closemodal"
            is ClientCommand.WindowStatus -> "window ${c.mode} ${c.width} ${c.height}"
            is ClientCommand.Social -> "social ${c.action} ${str(c.name)} ${str(c.text)} ${c.rank}"
        }

    @Suppress("CyclomaticComplexMethod", "MagicNumber", "LongMethod") // one branch per command; indices are field positions
    fun decode(parts: List<String>): ClientCommand {
        fun i(n: Int) = parts[n].toInt()

        fun b(n: Int) = parts[n].toBooleanStrict()
        return when (parts[0]) {
            "move" -> {
                ClientCommand.MoveClick(i(1), i(2), b(3), b(4))
            }

            "cheat" -> {
                ClientCommand.Cheat(unstr(parts[1]))
            }

            "mapbuilt" -> {
                ClientCommand.MapBuildComplete
            }

            "oploc" -> {
                ClientCommand.OpLoc(i(1), i(2), i(3), i(4), b(5))
            }

            "opnpc" -> {
                ClientCommand.OpNpc(i(1), i(2), b(3))
            }

            "opplayer" -> {
                ClientCommand.OpPlayer(i(1), i(2), b(3))
            }

            "opobj" -> {
                ClientCommand.OpObj(i(1), i(2), i(3), i(4), b(5))
            }

            "opplayert" -> {
                ClientCommand.OpPlayerT(i(1), i(2), i(3), i(4), b(5))
            }

            "opnpct" -> {
                ClientCommand.OpNpcT(i(1), i(2), i(3), i(4), b(5))
            }

            "oploct" -> {
                ClientCommand.OpLocT(i(1), i(2), i(3), i(4), i(5), i(6), b(7))
            }

            "opobjt" -> {
                ClientCommand.OpObjT(i(1), i(2), i(3), i(4), i(5), i(6), b(7))
            }

            "examine" -> {
                ClientCommand.Examine(i(1), i(2))
            }

            "heading" -> {
                ClientCommand.SetHeading(i(1))
            }

            "opworldentity" -> {
                ClientCommand.OpWorldEntity(i(1), i(2), b(3))
            }

            "buttont" -> {
                ClientCommand.ButtonT(i(1), i(2), i(3), i(4), i(5), i(6))
            }

            "button" -> {
                ClientCommand.Button(i(1), i(2), i(3), i(4), if (parts.size > SUBOP_FIELD) i(SUBOP_FIELD) else -1)
            }

            "chat" -> {
                ClientCommand.PublicChat(unstr(parts[1]), i(2), i(3), i(4))
            }

            "resume" -> {
                ClientCommand.ResumePause(i(1), i(2))
            }

            "resumeobj" -> {
                ClientCommand.ResumeObj(i(1))
            }

            "drag" -> {
                ClientCommand.ButtonDrag(i(1), i(2), i(3), i(4), i(5), i(6))
            }

            "resumestring" -> {
                ClientCommand.ResumeString(unstr(parts[1]))
            }

            "resumecount" -> {
                ClientCommand.ResumeCount(i(1))
            }

            "closemodal" -> {
                ClientCommand.CloseModal
            }

            "window" -> {
                ClientCommand.WindowStatus(i(1), i(2), i(3))
            }

            "social" -> {
                ClientCommand.Social(
                    tp.protocol.api.SocialAction
                        .valueOf(parts[1]),
                    unstr(parts[2]),
                    unstr(parts[3]),
                    parts.getOrNull(4)?.toInt() ?: 0,
                )
            }

            else -> {
                error("unknown command '${parts[0]}'")
            }
        }
    }

    private fun str(s: String): String =
        java.net.URLEncoder
            .encode(s, Charsets.UTF_8)
            .ifEmpty { "%00" }

    private fun unstr(s: String): String = if (s == "%00") "" else java.net.URLDecoder.decode(s, Charsets.UTF_8)
}
