package tp.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.oshai.kotlinlogging.KotlinLogging
import tp.engine.World
import tp.engine.tick.TickMetrics
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * World-node operations (brief §12, docs/OPERATIONS.md). `GET /health` returns 200 "ok", or 503 while any world breaks
 * its budget. `GET /metrics` serves Prometheus text with tick-time percentiles, missed deadlines, players and save
 * latency. A monitor checks each [ALERT_WINDOW_SECONDS] window and logs `ALERT` lines (ship them to your alerting) when:
 * - logic p99 is over [LOGIC_BUDGET_MS], or info p99 is over [INFO_BUDGET_MS] (§7 budget);
 * - a tick missed its deadline;
 * - the slowest save took longer than [SAVE_ALERT_MS].
 */
class Ops(private val worlds: List<World>, port: Int) : AutoCloseable {
    private val http = HttpServer.create(InetSocketAddress(port), 0)
    private val scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())
    private val previous = HashMap<Int, Window>()

    @Volatile private var degraded = emptyList<String>()

    private class Window(val logic: LongArray, val info: LongArray, val missed: Long)

    fun start(): Ops {
        http.executor = Executors.newVirtualThreadPerTaskExecutor()
        http.createContext("/health") { ex -> health(ex) }
        http.createContext("/metrics") { ex -> metrics(ex) }
        // Load tests: start a fresh measurement window (e.g. after the login ramp). Monitoring-only; racy by design.
        http.createContext("/reset") { ex ->
            worlds.forEach {
                it.metrics.logic.reset()
                it.metrics.info.reset()
            }
            reply(ex, HTTP_OK, "reset\n")
        }
        http.start()
        scheduler.scheduleAtFixedRate({ runCatching { check() } }, ALERT_WINDOW_SECONDS, ALERT_WINDOW_SECONDS, TimeUnit.SECONDS)
        logger.info { "ops: /health and /metrics on :${http.address.port}" }
        return this
    }

    /** One alert window: percentiles of the ticks recorded since the previous check. */
    private fun check() {
        val problems = ArrayList<String>()
        for (w in worlds) {
            val m = w.metrics
            val now = Window(m.logic.snapshot(), m.info.snapshot(), m.missedDeadlines)
            val before = previous.put(w.config.id, now) ?: continue
            val logicP99 = percentile(diff(now.logic, before.logic), P99)
            val infoP99 = percentile(diff(now.info, before.info), P99)
            if (logicP99 > LOGIC_BUDGET_MS) problems += "world ${w.config.id}: logic p99 $logicP99 ms > $LOGIC_BUDGET_MS"
            if (infoP99 > INFO_BUDGET_MS) problems += "world ${w.config.id}: info p99 $infoP99 ms > $INFO_BUDGET_MS"
            val missed = now.missed - before.missed
            if (missed > 0) problems += "world ${w.config.id}: $missed missed tick deadlines"
        }
        val saveMs = SaveMetrics.takeWindowMaxNanos() / NANOS_PER_MS
        if (saveMs > SAVE_ALERT_MS) problems += "save latency max $saveMs ms > $SAVE_ALERT_MS"
        problems.forEach { logger.error { "ALERT $it" } }
        degraded = problems
    }

    private fun health(ex: HttpExchange) {
        val problems = degraded
        val status = if (problems.isEmpty()) HTTP_OK else HTTP_UNAVAILABLE
        reply(
            ex,
            status,
            if (problems.isEmpty()) "ok players=${worlds.sumOf { it.playerCount }}\n" else problems.joinToString("\n", postfix = "\n"),
        )
    }

    private fun metrics(ex: HttpExchange) {
        val sb = StringBuilder()
        for (w in worlds) {
            val id = w.config.id
            val m = w.metrics
            sb.append("tp_players{world=\"$id\"} ${w.playerCount}\n")
            sb.append("tp_ticks_total{world=\"$id\"} ${m.ticks}\n")
            sb.append("tp_missed_deadlines_total{world=\"$id\"} ${m.missedDeadlines}\n")
            for ((phase, h) in listOf("logic" to m.logic, "info" to m.info)) {
                for ((label, q) in QUANTILES) {
                    sb.append(
                        "tp_tick_ms{world=\"$id\",phase=\"$phase\",quantile=\"$label\"} ${h.percentileMs(q)}\n",
                    )
                }
                sb.append("tp_tick_ms_max{world=\"$id\",phase=\"$phase\"} ${h.maxNanos / NANOS_PER_MS}\n")
            }
            // Raids brief section 7: build p99 <= 50 ms (off-thread), attach <= 1 ms (world thread).
            sb.append("tp_instances{world=\"$id\"} ${w.instances.live}\n")
            for ((phase, h) in listOf("build" to m.instanceBuild, "attach" to m.instanceAttach)) {
                for ((label, q) in QUANTILES) {
                    sb.append("tp_instance_us{world=\"$id\",phase=\"$phase\",quantile=\"$label\"} ${h.percentileNanos(q) / NANOS_PER_US}\n")
                }
                sb.append("tp_instance_us_max{world=\"$id\",phase=\"$phase\"} ${h.maxNanos / NANOS_PER_US}\n")
            }
        }
        sb.append("tp_save_last_ms ${SaveMetrics.lastNanos / NANOS_PER_MS}\n")
        sb.append("tp_save_max_ms ${SaveMetrics.maxNanos / NANOS_PER_MS}\n")
        reply(ex, HTTP_OK, sb.toString())
    }

    override fun close() {
        scheduler.shutdownNow()
        http.stop(0)
    }

    companion object {
        /** §7 budget: logic p99 ≤ 120 ms, info + flush p99 ≤ 80 ms. */
        const val LOGIC_BUDGET_MS = 120
        const val INFO_BUDGET_MS = 80

        /** A save slower than this is an incident (logout saves gate the next login). */
        const val SAVE_ALERT_MS = 1_000L
        const val ALERT_WINDOW_SECONDS = 10L
        const val DEFAULT_PORT = 9090
        private const val P99 = 0.99
        private const val NANOS_PER_MS = 1_000_000L
        private const val NANOS_PER_US = 1_000L
        private const val HTTP_OK = 200
        private const val HTTP_UNAVAILABLE = 503
        private val QUANTILES = listOf("0.5" to 0.5, "0.99" to P99, "0.999" to 0.999)

        private fun diff(a: LongArray, b: LongArray) = LongArray(a.size) { a[it] - b[it] }

        /** Bucket upper bound (ms) at quantile [q] of a histogram copy ([TickMetrics] buckets). */
        fun percentile(counts: LongArray, q: Double): Int {
            val total = counts.sum()
            if (total == 0L) return 0
            val rank =
                kotlin.math
                    .ceil(q * total)
                    .toLong()
                    .coerceAtLeast(1)
            var seen = 0L
            for (ms in counts.indices) {
                seen += counts[ms]
                if (seen >= rank) return ms + 1
            }
            return TickMetrics.MAX_MS
        }

        private fun reply(ex: HttpExchange, code: Int, text: String) {
            val body = text.toByteArray()
            ex.responseHeaders.add("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
            ex.sendResponseHeaders(code, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
    }
}
