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
        System.err.println(
            "usage: botswarm <count> [host=127.0.0.1] [port=43594] [centreX=3222] [centreZ=3218] " +
                "[walk|fight|gear|hop|channel|mixed] [namePrefix=bot]",
        )
        exitProcess(2)
    }
    val host = args.getOrNull(1) ?: "127.0.0.1"
    val port = args.getOrNull(2)?.toIntOrNull() ?: DEFAULT_PORT
    val centreX = args.getOrNull(CENTRE_X_ARG)?.toIntOrNull() ?: DEFAULT_CENTRE_X
    val centreZ = args.getOrNull(CENTRE_Z_ARG)?.toIntOrNull() ?: DEFAULT_CENTRE_Z
    val mode = args.getOrNull(MODE_ARG) ?: "walk"
    val fight = mode == "fight"
    // Account names are global across worlds (the hub refuses a second session), so each world's swarm needs its own.
    val prefix = args.getOrNull(PREFIX_ARG) ?: "bot"
    val modulus = BigInteger(Files.readString(Path.of(".data", "rsa", "modulus.hex")).trim(), HEX)

    val master = BotClient(host, port, modulus, IntArray(0)).use { it.js5Fetch(listOf(MASTER to MASTER)).single() }
    val crcs = masterCrcs(master)
    val pool = Executors.newVirtualThreadPerTaskExecutor()
    // Bots start [RAMP_MS] apart (-Dbotswarm.rampMs, default 0): thousands of simultaneous logins only measure the
    // login storm, not the steady state.
    val rampMs = java.lang.Long.getLong("botswarm.rampMs", 0L)
    repeat(count) { i ->
        if (rampMs > 0) Thread.sleep(rampMs)
        pool.execute {
            BotClient(host, port, modulus, crcs).use { bot ->
                val response = bot.login("$prefix${i + 1}", "bot")
                if (response != LOGIN_OK) {
                    System.err.println("$prefix${i + 1}: login response $response")
                    return@use
                }
                println("$prefix${i + 1}: logged in as index ${bot.playerIndex}")
                // mixed: a quarter each of walkers, clumped multi-combat fighters, gear switchers and (with
                // --loopback-hosts on the server) world hoppers.
                val behaviour = if (mode == "mixed") MIXED[i % MIXED.size] else mode
                when (behaviour) {
                    "fight" -> fight(bot, count)
                    "hop" -> hop(bot, "$prefix${i + 1}", modulus, crcs)
                    "clienthop" -> clientHop(bot, "$prefix${i + 1}", modulus, crcs)
                    "gear" -> gear(bot)
                    "channel" -> channel(bot, "$prefix${i + 1}", "${prefix}1")
                    else -> wander(bot, centreX, centreZ)
                }
            }
        }
    }
    pool.close()
}

/**
 * Multi-combat brawl for the Phase 4 check: teleport into the Main Wilderness multi area (3300, 3800; level 36), get
 * 99 Hitpoints, then every few seconds attack a random other player index (bots take indices 1..count on a fresh
 * server; a wrong guess is just a rejected command).
 */
private fun fight(bot: BotClient, count: Int) {
    val rng = ThreadLocalRandom.current()
    bot.cheat("tele ${FIGHT_X + rng.nextInt(-FIGHT_SPREAD, FIGHT_SPREAD + 1)} ${FIGHT_Z + rng.nextInt(-FIGHT_SPREAD, FIGHT_SPREAD + 1)}")
    bot.cheat("xp 3 $MAX_XP")
    var nextAttack = System.nanoTime() + MIN_WALK_INTERVAL_NANOS
    while (true) {
        bot.readPacket()
        val now = System.nanoTime()
        if (now >= nextAttack) {
            var target = rng.nextInt(1, count + 1)
            if (target == bot.playerIndex) target = target % count + 1
            bot.attackPlayer(target)
            nextAttack = now + rng.nextLong(MIN_WALK_INTERVAL_NANOS, MAX_WALK_INTERVAL_NANOS)
        }
    }
}

private const val FIGHT_X = 3300
private const val FIGHT_Z = 3800
private const val FIGHT_SPREAD = 3
private const val MAX_XP = 13_034_431

/**
 * World-hop loop (server started with `--loopback-hosts`): every few seconds open the World Switcher and click the next
 * world (1 -> 2 -> 3 -> 1; slot = world id, docs/mechanics/world-hop.md), follow LOGOUT_TRANSFER to that world's host and
 * log in again. Prints each hop; a login that fails (e.g. save not durable in time) is reported.
 */
private fun hop(first: BotClient, name: String, modulus: BigInteger, crcs: IntArray) {
    var bot = first
    var world = 1
    var hops = 0
    while (true) {
        Thread.sleep(ThreadLocalRandom.current().nextLong(MIN_WALK_INTERVAL_NANOS, MAX_WALK_INTERVAL_NANOS) / NANOS_PER_MS)
        val target = world % HOP_WORLDS + 1
        bot.button(LOGOUT_WORLD_SWITCHER, 1)
        bot.button(WORLDSWITCHER_BUTTONS, 1, sub = target)
        val transfer = bot.awaitTransfer() ?: error("$name: no LOGOUT_TRANSFER")
        bot.close()
        bot = BotClient(transfer.first, DEFAULT_PORT, modulus, crcs)
        val response = bot.login(name, "bot")
        if (response != LOGIN_OK) {
            System.err.println("$name: hop to ${transfer.second} refused: $response")
            return
        }
        world = transfer.second
        hops++
        println("$name: hop $hops -> world $world")
    }
}

/**
 * Gear-switch spam: whip in slot 0 and DDS in slot 1, then wield one every tick or two. Each wield swaps the other weapon
 * back into slot 1 (the item's old slot), so the bot alternates obj ids on slot 1.
 */
private fun gear(bot: BotClient) {
    bot.cheat("empty")
    bot.cheat("item $WHIP")
    bot.cheat("item $DDS")
    bot.button(INVENTORY_ITEMS, WIELD_OP, sub = 0, obj = WHIP)
    var next = DDS
    var nextSwitch = System.nanoTime() + MIN_WALK_INTERVAL_NANOS
    while (true) {
        bot.readPacket()
        val now = System.nanoTime()
        if (now >= nextSwitch) {
            bot.button(INVENTORY_ITEMS, WIELD_OP, sub = 1, obj = next)
            next = if (next == DDS) WHIP else DDS
            nextSwitch = now + ThreadLocalRandom.current().nextLong(SWITCH_MIN_NANOS, SWITCH_MAX_NANOS)
        }
    }
}

/** Joins [owner]'s friends chat and reports each full channel update (UPDATE_FRIENDCHAT_CHANNEL_FULL_V2, opcode 71). */
private fun channel(bot: BotClient, name: String, owner: String) {
    bot.joinChannel(owner)
    while (true) {
        val packet = bot.readPacket()
        if (packet.opcode == FRIENDCHAT_FULL) println("$name: channel update (${packet.payload.size} bytes)")
    }
}

private const val FRIENDCHAT_FULL = 71

/** Backpack items component (cache interface 149 comp 0); Wield/Wear is button op 3 (iop index 1). */
private const val INVENTORY_ITEMS = 149 shl 16
private const val WIELD_OP = 3
private const val WHIP = 4151
private const val DDS = 1215
private const val SWITCH_MIN_NANOS = 600_000_000L
private const val SWITCH_MAX_NANOS = 1_200_000_000L
private val MIXED = listOf("walk", "fight", "gear", "walk")

/**
 * Client-side hop, like RuneLite's world hopper: no server involvement, the client drops its connection and logs in
 * to the next world's address (server started with `--loopback-hosts`: world n = 127.0.0.n).
 */
private fun clientHop(first: BotClient, name: String, modulus: BigInteger, crcs: IntArray) {
    var bot = first
    var world = 1
    var hops = 0
    while (true) {
        Thread.sleep(ThreadLocalRandom.current().nextLong(MIN_WALK_INTERVAL_NANOS, MAX_WALK_INTERVAL_NANOS) / NANOS_PER_MS)
        val target = world % HOP_WORLDS + 1
        bot.close()
        bot = BotClient("127.0.0.$target", DEFAULT_PORT, modulus, crcs)
        val started = System.nanoTime()
        val response = bot.login(name, "bot")
        val ms = (System.nanoTime() - started) / NANOS_PER_MS
        if (response != LOGIN_OK) {
            System.err.println("$name: client hop to $target refused: $response after $ms ms")
            return
        }
        world = target
        hops++
        println("$name: client hop $hops -> world $world ($ms ms)")
    }
}

/** Logout tab "World Switcher" and the switcher's world list (cache interface 182 comp 3, interface 69 comp 18). */
private const val LOGOUT_WORLD_SWITCHER = 182 shl 16 or 3
private const val WORLDSWITCHER_BUTTONS = 69 shl 16 or 18
private const val HOP_WORLDS = 3
private const val NANOS_PER_MS = 1_000_000L

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
private const val MODE_ARG = 5
private const val PREFIX_ARG = 6
private const val HEX = 16
private const val MASTER = 255
private const val MASTER_ENTRY = 8
private const val LOGIN_OK = 2
private const val WANDER_RADIUS = 8
private const val MIN_WALK_INTERVAL_NANOS = 2_000_000_000L
private const val MAX_WALK_INTERVAL_NANOS = 6_000_000_000L
