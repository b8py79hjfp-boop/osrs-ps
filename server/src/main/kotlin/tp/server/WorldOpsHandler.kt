package tp.server

import com.sun.management.HotSpotDiagnosticMXBean
import io.github.oshai.kotlinlogging.KotlinLogging
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.runBlocking
import tp.engine.World
import tp.engine.tick.TickInspector
import tp.plugin.PluginContent
import tp.plugin.commands.CommandDispatcher.Outcome
import tp.plugin.commands.CommandResult
import tp.protocol.api.AvatarState
import tp.protocol.api.ClientCommand
import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage
import tp.services.HubServices
import tp.services.WorldOp
import tp.services.WorldOpLine
import tp.util.Rank
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Operations the hub routes to this world (panel plan §3 #13, #14). Diagnostics run on their own thread, never the
 * world thread, and write into `.data/dumps/`, where the panel lists them for download:
 * - `diag.threads`: a full thread dump (every frame);
 * - `diag.heap`: a heap dump of live objects (`.hprof`; the world pauses while the JVM writes it);
 * - `diag.jfr.start` / `diag.jfr.stop`: a Flight Recorder recording with the `profile` settings, [DEFAULT_JFR_SECONDS]
 *   unless `args` gives seconds;
 * - `diag.tick`: the tick inspector's JSONL trace for [DEFAULT_TICKS] ticks (or `args`), then the previous tracer is put
 *   back.
 * The hub checks the actor's rank before routing; this checks it again.
 */
internal class WorldOpsHandler(private val world: World, private val hub: HubServices) : AutoCloseable {
    private val pool =
        Executors.newSingleThreadExecutor(
            Thread
                .ofPlatform()
                .name("world-ops")
                .daemon()
                .factory(),
        )
    private val dumps: Path = Path.of(".data", "dumps")

    @Volatile private var recording: Recording? = null

    /** One reply sink per panel actor, kept so a `::confirm` reaches the same stand-in. */
    private val sessions = ConcurrentHashMap<Long, RemoteSession>()

    /** Panel commands run on their own thread, so a long diagnostic (a tick capture) never holds up the console. */
    private val commands =
        Executors.newSingleThreadExecutor(
            Thread
                .ofPlatform()
                .name("world-ops-commands")
                .daemon()
                .factory(),
        )

    fun handle(op: WorldOp) {
        (if (op.kind == "command") commands else pool).execute {
            runCatching { run(op) }.onFailure {
                logger.warn(it) { "world op ${op.kind} failed" }
                reply(op, "Failed: ${it.javaClass.simpleName}: ${it.message}", done = true, error = true)
            }
        }
    }

    private fun reply(op: WorldOp, line: String, done: Boolean = false, error: Boolean = false) {
        runCatching { runBlocking { hub.worldOps.reply(WorldOpLine(op.id, line, done, error)) } }
            .onFailure { logger.warn(it) { "world op reply failed" } }
    }

    private fun run(op: WorldOp) {
        if (op.kind.startsWith("diag.") && !op.actorRank.atLeast(Rank.DEVELOPER)) {
            return reply(op, "Diagnostics are for developers.", done = true, error = true)
        }
        if (op.kind.startsWith("diag.")) Files.createDirectories(dumps)
        when (op.kind) {
            "diag.threads" -> threads(op)
            "diag.heap" -> heap(op)
            "diag.jfr.start" -> jfrStart(op)
            "diag.jfr.stop" -> jfrStop(op)
            "diag.tick" -> tick(op)
            "command" -> command(op)
            else -> reply(op, "Unknown operation ${op.kind}.", done = true, error = true)
        }
    }

    /**
     * Panel brief §3 ExecuteCommand: [op]'s line runs on the world thread for the actor's remote stand-in, through the
     * typed-command pipeline as `Source.PANEL` (rank, targeting, policy, rate limit, confirmation, audit). Every chat
     * line the pipeline sends that actor comes back as output; a command answered later by the hub (Pending) gets
     * [PENDING_WAIT_MS] for its reply.
     */
    private fun command(op: WorldOp) {
        val content =
            world.content as? PluginContent
                ?: return reply(op, "This world has no command pipeline.", done = true, error = true)
        val session = sessions.computeIfAbsent(op.actorAccount) { RemoteSession() }
        session.lines.clear()
        val outcome = CompletableFuture<Outcome>()
        world.execute {
            runCatching {
                val actor = world.remoteActor(op.actorAccount, op.actorName, op.actorRank, session)
                content.runRemote(actor, op.args.trim().removePrefix("::"))
            }.fold(outcome::complete, outcome::completeExceptionally)
        }
        val result = outcome.get(WORLD_WAIT_MS, TimeUnit.MILLISECONDS)
        val pending =
            result is Outcome.Ran && result.result is CommandResult.Pending
        val failed =
            result is Outcome.Refused ||
                result is Outcome.Unknown ||
                (result is Outcome.Ran && result.result is CommandResult.Failed)
        val sent = forward(op, session, pending)
        val end =
            when {
                result is Outcome.Dropped -> "Too many commands; slow down."
                result is Outcome.AwaitingConfirm -> "Waiting: send ::confirm to run it."
                pending && sent == 0 -> "Sent; no reply yet (the hub may still be working)."
                failed -> "Refused."
                else -> "Done."
            }
        reply(op, end, done = true, error = failed || result is Outcome.Dropped)
    }

    /** Sends the actor's chat lines to the panel; a Pending command also gets its later hub reply. Returns the count. */
    private fun forward(op: WorldOp, session: RemoteSession, pending: Boolean): Int {
        var sent = 0
        while (true) {
            val line = session.lines.poll() ?: break
            reply(op, ChatMarkup.plain(line))
            sent++
        }
        if (!pending) return sent
        val deadline = System.currentTimeMillis() + PENDING_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            val line = session.lines.poll(if (sent == 0) PENDING_WAIT_MS else QUIET_MS, TimeUnit.MILLISECONDS) ?: break
            reply(op, ChatMarkup.plain(line))
            sent++
        }
        return sent
    }

    private fun file(prefix: String, ext: String): Path =
        dumps.resolve(
            "$prefix-w${world.config.id}-${LocalDateTime.now().format(STAMP)}.$ext",
        )

    private fun threads(op: WorldOp) {
        val infos = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true)
        val out = file("threads", "txt")
        Files.newBufferedWriter(out).use { w ->
            for (t in infos) {
                w.write("\"${t.threadName}\" id=${t.threadId} ${t.threadState}")
                t.lockName?.let { w.write(" on $it") }
                t.lockOwnerName?.let { w.write(" owned by \"$it\"") }
                w.write("\n")
                for (frame in t.stackTrace) w.write("    at $frame\n")
                w.write("\n")
            }
        }
        reply(op, "Wrote ${out.fileName} (${infos.size} threads).", done = true)
    }

    private fun heap(op: WorldOp) {
        val out = file("heap", "hprof")
        reply(op, "Writing ${out.fileName}; the world pauses while the JVM writes it.")
        ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean::class.java).dumpHeap(out.toAbsolutePath().toString(), true)
        reply(op, "Wrote ${out.fileName} (${Files.size(out) / BYTES_PER_MIB} MiB).", done = true)
    }

    private fun jfrStart(op: WorldOp) {
        if (recording != null) return reply(op, "A recording is already running; stop it first.", done = true, error = true)
        val seconds =
            op.args
                .trim()
                .toLongOrNull()
                ?.coerceIn(1, MAX_JFR_SECONDS) ?: DEFAULT_JFR_SECONDS
        val out = file("recording", "jfr")
        val r = Recording(Configuration.getConfiguration("profile"))
        r.setDestination(out)
        r.duration = java.time.Duration.ofSeconds(seconds)
        r.start()
        recording = r
        reply(op, "Recording to ${out.fileName} for $seconds s (stop early with diag.jfr.stop).", done = true)
    }

    private fun jfrStop(op: WorldOp) {
        val r = recording ?: return reply(op, "No recording is running.", done = true, error = true)
        recording = null
        val out = r.destination
        r.stop()
        r.close()
        reply(op, "Stopped; wrote ${out?.fileName ?: "the recording"}.", done = true)
    }

    private fun tick(op: WorldOp) {
        val ticks =
            op.args
                .trim()
                .toIntOrNull()
                ?.coerceIn(1, MAX_TICKS) ?: DEFAULT_TICKS
        val out = file("ticks", "jsonl")
        val writer = Files.newBufferedWriter(out)
        val previous =
            java.util.concurrent.atomic
                .AtomicReference<tp.engine.TickTracer?>()
        world.execute {
            previous.set(world.tracer)
            world.tracer = TickInspector(writer)
        }
        reply(op, "Capturing $ticks ticks into ${out.fileName}…")
        Thread.sleep(ticks * TICK_MS)
        val done = java.util.concurrent.CountDownLatch(1)
        world.execute {
            world.tracer = previous.get()
            writer.close()
            done.countDown()
        }
        done.await()
        reply(op, "Wrote ${out.fileName} ($ticks ticks).", done = true)
    }

    override fun close() {
        recording?.close()
        pool.shutdownNow()
        commands.shutdownNow()
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        const val BYTES_PER_MIB = 1024L * 1024L

        /** Panel brief §3: a JFR recording of 2 minutes by default. */
        const val DEFAULT_JFR_SECONDS = 120L

        /** An hour at most (our limit: the file grows with time). */
        const val MAX_JFR_SECONDS = 3_600L

        /** One minute of ticks by default; at most ten (our limits: the trace is large). */
        const val DEFAULT_TICKS = 100
        const val MAX_TICKS = 1_000

        /** How long the world thread may take to run one panel command (a few ticks at most). */
        const val WORLD_WAIT_MS = 5_000L

        /** A Pending command's hub reply: up to 5 s for the first line, then 300 ms of quiet ends it (our values). */
        const val PENDING_WAIT_MS = 5_000L
        const val QUIET_MS = 300L

        /** Game tick length (brief §5.3: 600 ms). */
        const val TICK_MS = 600L
    }
}

/**
 * The panel actor's "client" (panel brief §3): it has no game screen, so it keeps only chat lines, for the panel to
 * show. Every other message is dropped. Lines are added on the world thread and read by the ops thread.
 */
internal class RemoteSession : ClientSession {
    val lines = LinkedBlockingQueue<String>(MAX_LINES)

    override val isOpen: Boolean get() = true

    override fun pollCommands(sink: (ClientCommand) -> Unit) = Unit

    override fun beginGame(state: AvatarState) = Unit

    override fun send(message: ServerMessage) {
        if (message is ServerMessage.GameMessage) lines.offer(message.text)
    }

    override fun syncAvatar(coord: AvatarState) = Unit

    override fun flushInfos() = Unit

    override fun close() = Unit

    private companion object {
        /** Bounds one command's output (the longest lists, `::audit`, are far below this). */
        const val MAX_LINES = 500
    }
}

/** Chat markup out of a line for the panel (it escapes the rest as HTML itself). */
internal object ChatMarkup {
    private val TAG = Regex("<(/?col(=[0-9a-fA-F]{6})?|/?shad(=[0-9a-fA-F]{6})?|br|img=\\d+|/?u|/?str)>")

    fun plain(line: String): String = TAG.replace(line, "").replace("<lt>", "<").replace("<gt>", ">")
}
