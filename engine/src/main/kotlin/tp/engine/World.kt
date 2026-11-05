package tp.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import org.rsmod.routefinder.RouteFinding
import org.rsmod.routefinder.StepValidator
import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.protocol.api.ClientCommand
import tp.protocol.api.GameNetwork
import tp.protocol.api.LoginDetails
import tp.protocol.api.LoginGate
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.protocol.api.ServerMessage
import tp.util.CoordGrid
import java.util.SplittableRandom
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport

private val logger = KotlinLogging.logger {}

data class WorldConfig(
    val id: Int,
    val spawn: CoordGrid,
    /** RNG seed: same inputs + same seed => same world (brief §1, determinism). */
    val seed: Long,
    /** Bounded admissions per tick (brief §4.3, §7 login storms). */
    val maxLoginsPerTick: Int = 50,
)

/**
 * One game world on its own platform thread. Phase 2 slice of the tick pipeline (brief §5.3, docs/mechanics/tick.md
 * in Phase 3):
 *
 * 1. pre-tick — advance clock
 * 2. client input — players in PID order poll their commands (clicks from tick N act in tick N+1)
 * 3. NPCs — none yet
 * 4. players in PID order — movement (queues/timers/interactions arrive in Phase 3)
 * 5. post-tick — logouts, bounded logins
 * 6. info pass — avatars synced, infos built (parallel inside RSProt), each session flushed once
 */
class World(
    val config: WorldConfig,
    private val collision: CollisionFlagMap,
    private val network: GameNetwork,
    private val hooks: WorldHooks = WorldHooks.NONE,
) : LoginGate {
    val rng: SplittableRandom = SplittableRandom(config.seed)

    var tick: Long = 0
        private set

    private val players = arrayOfNulls<Player>(MAX_PLAYERS)

    /** Slot processing order (PID order). Phase 3 shuffles it on the PID window; identity for now. */
    private val pidOrder = IntArray(MAX_PLAYERS - 1) { it + 1 }
    private val loginInbox = ConcurrentLinkedQueue<LoginRequest>()
    private val routeFinding = RouteFinding(collision)
    private val steps = StepValidator(collision)

    @Volatile private var running = false
    private var thread: Thread? = null

    val playerCount: Int get() = players.count { it != null }

    fun forEachPlayer(action: (Player) -> Unit) {
        for (slot in pidOrder) players[slot]?.let(action)
    }

    /** Called from network threads; admitted in post-tick on the world thread. */
    override fun onLogin(request: LoginRequest) {
        loginInbox.add(request)
    }

    /**
     * Starts the network and the tick loop on a dedicated thread ("world-<id>"). Returns once the network is listening
     * (the world thread must own the network: RSProt's communication thread), rethrowing any startup failure.
     */
    @Suppress("TooGenericExceptionCaught") // any startup failure must be handed back to the starting thread
    fun start() {
        check(!running) { "World ${config.id} already running" }
        running = true
        val started = java.util.concurrent.CompletableFuture<Unit>()
        val t =
            Thread.ofPlatform().name("world-${config.id}").start {
                try {
                    network.start(this)
                    started.complete(Unit)
                } catch (e: Exception) {
                    running = false
                    started.completeExceptionally(e)
                    return@start
                }
                loop()
            }
        thread = t
        started.join()
    }

    fun stop() {
        running = false
        thread?.join()
        network.shutdown()
    }

    /** Fixed 600 ms cadence anchored to the start instant: no drift; an overrun runs the next tick immediately. */
    private fun loop() {
        val start = System.nanoTime()
        var missed = 0L
        while (running) {
            runTick()
            val deadline = start + (tick * TICK_NANOS)
            val now = System.nanoTime()
            if (now > deadline) {
                missed++
                if (missed % MISSED_LOG_SAMPLE ==
                    1L
                ) {
                    logger.warn { "world ${config.id}: tick $tick overran by ${(now - deadline) / NANOS_PER_MS} ms ($missed total)" }
                }
            } else {
                LockSupport.parkNanos(deadline - now)
            }
        }
    }

    /** One full tick. Public so tests and tools/scenario can drive the world without the clock. */
    fun runTick() {
        tick++
        inputPhase()
        playerPhase()
        postTick()
        infoPass()
    }

    private fun inputPhase() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            if (!player.session.isOpen) continue
            player.session.pollCommands { handleCommand(player, it) }
        }
    }

    private fun handleCommand(player: Player, command: ClientCommand) {
        when (command) {
            is ClientCommand.MoveClick -> setRoute(player, command.x, command.z, command.ctrl)
            is ClientCommand.Cheat -> hooks.onCheat(this, player, command.text)
            ClientCommand.MapBuildComplete -> Unit
        }
    }

    /** Server-side route from the player's tile to the clicked tile (brief §5.2). Failed routes leave the player idle. */
    internal fun setRoute(player: Player, destX: Int, destZ: Int, ctrl: Boolean) {
        player.route.clear()
        val c = player.coord
        val route = routeFinding.findRoute(c.level, c.x, c.z, destX, destZ)
        if (route.failed) return
        for (wp in route.waypoints) player.route.add(wp.x, wp.z)
        player.routeCtrl = ctrl
    }

    fun setRunMode(player: Player, enabled: Boolean) {
        player.runMode = enabled
    }

    fun teleport(player: Player, to: CoordGrid) {
        player.route.clear()
        player.coord = to
        player.teleportedThisTick = true
    }

    private fun playerPhase() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            player.stepsThisTick = 0
            move(player)
        }
    }

    /** Walk = 1 tile per tick, run = 2 (brief §5.2). Each step heads straight/diagonally to the next waypoint. */
    private fun move(player: Player) {
        val maxSteps = if (player.isRunningNow) RUN_STEPS else WALK_STEPS
        while (player.stepsThisTick < maxSteps && !player.route.isEmpty) {
            val c = player.coord
            val tx = player.route.peekX()
            val tz = player.route.peekZ()
            val dx = Integer.signum(tx - c.x)
            val dz = Integer.signum(tz - c.z)
            if (dx == 0 && dz == 0) {
                player.route.advance()
                continue
            }
            if (!steps.canTravel(c.level, c.x, c.z, dx, dz)) {
                player.route.clear()
                break
            }
            player.coord = CoordGrid(c.x + dx, c.z + dz, c.level)
            player.stepsThisTick++
            if (player.coord.x == tx && player.coord.z == tz) player.route.advance()
        }
        if (player.route.isEmpty) player.routeCtrl = false
    }

    private fun postTick() {
        for (slot in 1 until MAX_PLAYERS) {
            val player = players[slot] ?: continue
            if (!player.session.isOpen) logout(player)
        }
        var admitted = 0
        while (admitted < config.maxLoginsPerTick) {
            val request = loginInbox.poll() ?: break
            admit(request)
            admitted++
        }
    }

    private fun admit(request: LoginRequest) {
        if (!request.crcValid) return request.reject(LoginRejection.OUT_OF_DATE)
        if (request.username.isBlank()) return request.reject(LoginRejection.INVALID_CREDENTIALS)
        if (players.any { it?.username.equals(request.username, ignoreCase = true) }) return request.reject(LoginRejection.ALREADY_ONLINE)
        val slot = (1 until MAX_PLAYERS).firstOrNull { players[it] == null } ?: return request.reject(LoginRejection.WORLD_FULL)
        val session =
            try {
                request.accept(slot, LoginDetails())
            } catch (e: IllegalStateException) {
                logger.warn(e) { "login accept failed for ${request.username}" }
                return
            }
        val player = Player(slot, request.username, session, config.spawn)
        players[slot] = player
        session.beginGame(player.avatarState())
        session.syncAvatar(player.avatarState())
        hooks.onLogin(this, player)
        logger.info { "login: $player ($playerCount online)" }
    }

    private fun logout(player: Player) {
        players[player.slot] = null
        player.session.close()
        logger.info { "logout: $player ($playerCount online)" }
    }

    private fun infoPass() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            if (BuildArea.needsRebuild(player.coord, player.buildArea)) {
                player.buildArea = BuildArea.originFor(player.coord)
                player.session.send(ServerMessage.Rebuild(player.coord.x, player.coord.z))
            }
            player.session.syncAvatar(player.avatarState())
            player.teleportedThisTick = false
        }
        network.buildInfos()
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            if (player.session.isOpen) player.session.flushInfos()
        }
    }

    companion object {
        /** Player indices 1..2047 (brief §5.2, §7). */
        const val MAX_PLAYERS = 2048

        /** 600 ms game tick (OSRS Wiki "Game tick"). */
        const val TICK_NANOS = 600_000_000L
        private const val NANOS_PER_MS = 1_000_000L
        private const val MISSED_LOG_SAMPLE = 100L
        private const val WALK_STEPS = 1
        private const val RUN_STEPS = 2
    }
}

/** Content callbacks. Phase 2 stand-in for the plugin system (brief §6); `server` provides the implementation. */
interface WorldHooks {
    fun onLogin(world: World, player: Player) {}

    fun onCheat(world: World, player: Player, command: String) {}

    companion object {
        val NONE: WorldHooks = object : WorldHooks {}
    }
}
