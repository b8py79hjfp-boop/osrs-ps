package tp.server

import com.sun.management.HotSpotDiagnosticMXBean
import io.github.oshai.kotlinlogging.KotlinLogging
import jdk.jfr.Configuration
import jdk.jfr.Recording
import kotlinx.coroutines.runBlocking
import tp.engine.World
import tp.engine.tick.TickInspector
import tp.services.HubServices
import tp.services.WorldOp
import tp.services.WorldOpLine
import tp.util.Rank
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

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

    fun handle(op: WorldOp) {
        pool.execute {
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
        Files.createDirectories(dumps)
        when (op.kind) {
            "diag.threads" -> threads(op)
            "diag.heap" -> heap(op)
            "diag.jfr.start" -> jfrStart(op)
            "diag.jfr.stop" -> jfrStop(op)
            "diag.tick" -> tick(op)
            "command" -> reply(op, "Panel commands arrive with panel phase 6.", done = true, error = true)
            else -> reply(op, "Unknown operation ${op.kind}.", done = true, error = true)
        }
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

        /** Game tick length (brief §5.3: 600 ms). */
        const val TICK_MS = 600L
    }
}
