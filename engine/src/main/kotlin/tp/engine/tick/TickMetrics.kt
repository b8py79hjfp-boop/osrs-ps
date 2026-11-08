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

    /** Duration of the last instance build (off the world thread). */
    @Volatile var instanceBuildNanos: Long = 0
        internal set

    /** Raids brief section 7: instance builds off the world thread (p99 <= 50 ms), 10 µs buckets up to 200 ms. */
    val instanceBuild = Histogram(FINE_BUCKET_NANOS, FINE_BUCKETS)

    /** Raids brief section 7: instance attaches on the world thread (<= 1 ms), 10 µs buckets up to 200 ms. */
    val instanceAttach = Histogram(FINE_BUCKET_NANOS, FINE_BUCKETS)

    /** Ticks that ended after their 600 ms deadline. */
    @Volatile var missedDeadlines: Long = 0
        internal set

    @Volatile var ticks: Long = 0
        internal set

    /** [buckets] buckets of [bucketNanos] each; anything slower lands in the last. The default is 1 ms up to [MAX_MS]. */
    class Histogram internal constructor(
        private val bucketNanos: Long = NANOS_PER_MS,
        buckets: Int = MAX_MS + 1,
    ) {
        private val counts = LongArray(buckets)

        @Volatile private var total = 0L

        @Volatile var maxNanos: Long = 0
            private set

        internal fun record(nanos: Long) {
            val bucket = (nanos / bucketNanos).toInt().coerceIn(0, counts.size - 1)
            counts[bucket]++
            total++
            if (nanos > maxNanos) maxNanos = nanos
        }

        val count: Long get() = total

        /** Upper bound (ms) of the bucket holding quantile [q] (0..1); 0 when empty (1 ms buckets). */
        fun percentileMs(q: Double): Int = (percentileNanos(q) / NANOS_PER_MS).toInt()

        /** Upper bound (ns) of the bucket holding quantile [q] (0..1); 0 when empty. */
        fun percentileNanos(q: Double): Long {
            val n = total
            if (n == 0L) return 0
            val rank =
                kotlin.math
                    .ceil(q * n)
                    .toLong()
                    .coerceAtLeast(1)
            var seen = 0L
            for (b in counts.indices) {
                seen += counts[b]
                if (seen >= rank) return (b + 1) * bucketNanos
            }
            return counts.size * bucketNanos
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

        /** The instance histograms: 10 µs buckets, 20,000 of them (200 ms). */
        private const val FINE_BUCKET_NANOS = 10_000L
        private const val FINE_BUCKETS = 20_000
    }
}
