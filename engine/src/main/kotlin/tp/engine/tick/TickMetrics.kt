package tp.engine.tick

/**
 * Tick-time histograms for the §7 budget (logic p99 ≤ 120 ms, info + flush p99 ≤ 80 ms). Recording is allocation-free
 * and happens on the world thread. Other threads (health, metrics, alerts) read counters that may be one tick stale,
 * which is fine for monitoring.
 * Buckets are 1 ms wide up to [MAX_MS]; anything slower lands in the last bucket.
 */
class TickMetrics {
    val logic = Histogram()
    val info = Histogram()

    /** Duration of the last instance attach on the world thread (IN6; budget 1 ms, raids brief section 7). */
    @Volatile var instanceAttachNanos: Long = 0
        internal set

    /** Ticks that ended after their 600 ms deadline. */
    @Volatile var missedDeadlines: Long = 0
        internal set

    @Volatile var ticks: Long = 0
        internal set

    class Histogram internal constructor() {
        private val counts = LongArray(MAX_MS + 1)

        @Volatile private var total = 0L

        @Volatile var maxNanos: Long = 0
            private set

        internal fun record(nanos: Long) {
            val ms = (nanos / NANOS_PER_MS).toInt().coerceIn(0, MAX_MS)
            counts[ms]++
            total++
            if (nanos > maxNanos) maxNanos = nanos
        }

        val count: Long get() = total

        /** Upper bound (ms) of the bucket holding quantile [q] (0..1); 0 when empty. */
        fun percentileMs(q: Double): Int {
            val n = total
            if (n == 0L) return 0
            val rank =
                kotlin.math
                    .ceil(q * n)
                    .toLong()
                    .coerceAtLeast(1)
            var seen = 0L
            for (ms in counts.indices) {
                seen += counts[ms]
                if (seen >= rank) return ms + 1
            }
            return MAX_MS
        }

        /** Copy of the bucket counts (index = ms). */
        fun snapshot(): LongArray = counts.copyOf()

        fun reset() {
            counts.fill(0)
            total = 0
            maxNanos = 0
        }
    }

    companion object {
        /** Histogram range: 2 s at 1 ms resolution (beyond 600 ms a tick has already failed its deadline). */
        const val MAX_MS = 2000
        private const val NANOS_PER_MS = 1_000_000L
    }
}
