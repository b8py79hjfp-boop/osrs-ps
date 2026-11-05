package tp.tools.botswarm

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import kotlin.system.exitProcess

/**
 * Phase 2 swarm: N bots log into a running world and wander near spawn so a real client "sees bots" (Phase 2 DoD).
 * Phase 5 grows this into the scale acceptance test (scripted behaviours, histograms, >= 3 worlds).
 *
 * `./gradlew :tools:botswarm:run --args="<count> [host] [port] [centreX] [centreZ]"`; RSA modulus from `.data/rsa/modulus.hex`.
 */
fun main(args: Array<String>) {
    val count = args.getOrNull(0)?.toIntOrNull()
    if (count == null) {
        System.err.println("usage: botswarm <count> [host=127.0.0.1] [port=43594] [centreX=3222] [centreZ=3218]")
        exitProcess(2)
    }
    val host = args.getOrNull(1) ?: "127.0.0.1"
    val port = args.getOrNull(2)?.toIntOrNull() ?: DEFAULT_PORT
    val centreX = args.getOrNull(CENTRE_X_ARG)?.toIntOrNull() ?: DEFAULT_CENTRE_X
    val centreZ = args.getOrNull(CENTRE_Z_ARG)?.toIntOrNull() ?: DEFAULT_CENTRE_Z
    val modulus = BigInteger(Files.readString(Path.of(".data", "rsa", "modulus.hex")).trim(), HEX)

    val master = BotClient(host, port, modulus, IntArray(0)).use { it.js5Fetch(listOf(MASTER to MASTER)).single() }
    val crcs = masterCrcs(master)
    val pool = Executors.newVirtualThreadPerTaskExecutor()
    repeat(count) { i ->
        pool.submit {
            BotClient(host, port, modulus, crcs).use { bot ->
                val response = bot.login("bot${i + 1}", "bot")
                if (response != LOGIN_OK) {
                    System.err.println("bot${i + 1}: login response $response")
                    return@use
                }
                println("bot${i + 1}: logged in as index ${bot.playerIndex}")
                wander(bot, centreX, centreZ)
            }
        }
    }
    pool.close()
}

/** Drains server packets; every few seconds clicks a random tile within [WANDER_RADIUS] of the centre. */
private fun wander(bot: BotClient, centreX: Int, centreZ: Int) {
    val rng = ThreadLocalRandom.current()
    var nextWalk = System.nanoTime()
    while (true) {
        bot.readPacket()
        val now = System.nanoTime()
        if (now >= nextWalk) {
            bot.walkTo(centreX + rng.nextInt(-WANDER_RADIUS, WANDER_RADIUS + 1), centreZ + rng.nextInt(-WANDER_RADIUS, WANDER_RADIUS + 1))
            nextWalk = now + rng.nextLong(MIN_WALK_INTERVAL_NANOS, MAX_WALK_INTERVAL_NANOS)
        }
    }
}

private fun masterCrcs(container: ByteArray): IntArray {
    val buf = ByteBuffer.wrap(container)
    buf.get()
    val length = buf.getInt()
    return IntArray(length / MASTER_ENTRY) {
        val crc = buf.getInt()
        buf.getInt()
        crc
    }
}

private const val DEFAULT_PORT = 43594
private const val DEFAULT_CENTRE_X = 3222
private const val DEFAULT_CENTRE_Z = 3218
private const val CENTRE_X_ARG = 3
private const val CENTRE_Z_ARG = 4
private const val HEX = 16
private const val MASTER = 255
private const val MASTER_ENTRY = 8
private const val LOGIN_OK = 2
private const val WANDER_RADIUS = 8
private const val MIN_WALK_INTERVAL_NANOS = 2_000_000_000L
private const val MAX_WALK_INTERVAL_NANOS = 6_000_000_000L
