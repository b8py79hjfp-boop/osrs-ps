package tp.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.engine.entity.BuildArea
import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.engine.interact.Interaction
import tp.engine.interact.InteractionProcessor
import tp.engine.interact.InteractionTarget
import tp.engine.move.Movement
import tp.engine.npc.NpcAi
import tp.engine.script.ScriptRunner
import tp.engine.ui.Interfaces
import tp.protocol.api.ClientCommand
import tp.protocol.api.GameNetwork
import tp.protocol.api.LoginDetails
import tp.protocol.api.LoginGate
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.protocol.api.ServerMessage
import tp.util.CoordGrid
import java.util.SplittableRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.LockSupport

private val logger = KotlinLogging.logger {}

/** PID re-randomisation windows (OSRS Wiki "Player identification number"). */
val PID_WINDOW_NORMAL: IntRange = 100..150

/** Bounty Hunter, PvP and tournament worlds (Wiki, change of 9 August 2018). */
val PID_WINDOW_PVP: IntRange = 40..60

data class WorldConfig(
    val id: Int,
    val spawn: CoordGrid,
    /** RNG seed: same inputs + same seed => same world (brief §1, determinism). */
    val seed: Long,
    /** Bounded admissions per tick (brief §4.3, §7 login storms). */
    val maxLoginsPerTick: Int = 50,
    /**
     * Per-player PID re-randomisation window in ticks: 100..150, or 40..60 on Bounty Hunter / PvP / tournament worlds
     * (OSRS Wiki "Player identification number"; tick.md PID).
     */
    val pidShuffle: IntRange = PID_WINDOW_NORMAL,
)

/**
 * One game world on its own platform thread, running the tick pipeline of docs/mechanics/tick.md.
 * Everything below runs on the world thread except [onLogin] (network threads enqueue).
 */
class World(
    val config: WorldConfig,
    collision: CollisionFlagMap,
    private val network: GameNetwork,
    val content: ContentHandlers = ContentHandlers.NONE,
) : LoginGate {
    /** The world's only RNG; content must use it (brief §5.2). */
    val rng: SplittableRandom = SplittableRandom(config.seed)

    var tick: Long = 0
        private set

    val movement = Movement(collision)
    internal val interactions = InteractionProcessor(collision, movement)
    val interfaces = Interfaces()
    private val npcAi = NpcAi(this)

    private val players = arrayOfNulls<Player>(MAX_PLAYERS)
    private val npcs = ArrayList<Npc?>()

    /** Slots in processing order (PID order); each player's position re-randomises on its own window. */
    private val pidOrder = IntArray(MAX_PLAYERS - 1) { it + 1 }

    /** Per slot: tick of that player's next PID re-randomisation. */
    private val nextPidShuffle = LongArray(MAX_PLAYERS)
    private var pidShuffleEnabled = true

    private val loginInbox = ConcurrentLinkedQueue<LoginRequest>()

    @Volatile private var running = false
    private var thread: Thread? = null

    /** Hooks for the tick inspector / scenario runner (null in production unless tracing is on). */
    var tracer: TickTracer? = null

    val playerCount: Int get() = players.count { it != null }

    fun forEachPlayer(action: (Player) -> Unit) {
        for (slot in pidOrder) players[slot]?.let(action)
    }

    fun player(slot: Int): Player? = players.getOrNull(slot)

    fun forEachNpc(action: (Npc) -> Unit) {
        for (npc in npcs) npc?.let(action)
    }

    /** Called from network threads; admitted in post-tick on the world thread. */
    override fun onLogin(request: LoginRequest) {
        loginInbox.add(request)
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Starts the network and the tick loop on a dedicated thread ("world-<id>"). Returns once the network is listening
     * (the world thread must own the network: RSProt's communication thread), rethrowing any startup failure.
     */
    @Suppress("TooGenericExceptionCaught") // any startup failure must be handed back to the starting thread
    fun start() {
        check(!running) { "World ${config.id} already running" }
        running = true
        val started = CompletableFuture<Unit>()
        thread =
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
                if (missed % MISSED_LOG_SAMPLE == 1L) {
                    logger.warn { "world ${config.id}: tick $tick overran by ${(now - deadline) / NANOS_PER_MS} ms ($missed total)" }
                }
            } else {
                LockSupport.parkNanos(deadline - now)
            }
        }
    }

    // ---------------------------------------------------------------- the tick (tick.md)

    /** One full tick. Public so the scenario runner and replay can drive a world without the clock or network. */
    fun runTick() {
        tick++
        tracer?.onTickStart(tick)
        inputPhase()
        npcPhase()
        playerPhase()
        postTick()
        infoPass()
        tracer?.onTickEnd(tick)
    }

    /** Phase 2: up to 10 commands per player in arrival order (RSProt enforces the cap); acted on now. */
    private fun inputPhase() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            if (!player.session.isOpen) continue
            player.session.pollCommands { handleCommand(player, it) }
        }
    }

    private fun handleCommand(player: Player, command: ClientCommand) {
        tracer?.onCommand(player, command)
        when (command) {
            is ClientCommand.MoveClick -> {
                if (player.isDelayed) return // D2: route events aren't processed while delayed
                interrupt(player)
                movement.routePlayer(player, command.x, command.z, command.ctrl)
            }

            is ClientCommand.Cheat -> {
                val parts = command.text.trim().split(WHITESPACE)
                val handled = parts.isNotEmpty() && content.onCommand(player, parts[0].lowercase(), parts.drop(1))
                if (!handled) player.session.send(ServerMessage.GameMessage("Unknown command: ${command.text}"))
            }

            ClientCommand.MapBuildComplete -> {
                Unit
            }
        }
    }

    /** Phase 3: NPCs in index order (tick.md). */
    private fun npcPhase() {
        for (npc in npcs) {
            if (npc == null) continue
            npc.stepsThisTick = 0
            if (ScriptRunner.resumeDelayed(npc)) tracer?.onPhase(npc, "delay-end")
            npc.timer.process()
            npc.queue.process()
            npcAi.process(npc)
            interactions.processNpc(npc)
        }
    }

    /** Phase 4: players in PID order (tick.md). */
    private fun playerPhase() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            player.stepsThisTick = 0
            if (ScriptRunner.resumeDelayed(player)) tracer?.onPhase(player, "delay-end")
            if (player.queue.hasStrong()) player.closeModal() // Q3
            player.queue.process()
            player.timers.process()
            player.areaQueue.process()
            interactions.processPlayer(player, if (player.isRunningNow) RUN_STEPS else WALK_STEPS)
            if (player.route.isEmpty) player.routeCtrl = false
        }
    }

    /** Phase 5: logouts, bounded logins, PID shuffle. */
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
        if (pidShuffleEnabled) shufflePids()
    }

    /** Phase 6: world state is frozen; sync avatars, build infos (parallel in RSProt), flush each session once. */
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
        for (npc in npcs) if (npc != null) network.npcSync(npc.index, npc.coord.level, npc.coord.x, npc.coord.z)
        network.buildInfos()
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            if (player.session.isOpen) player.session.flushInfos()
        }
    }

    // ---------------------------------------------------------------- operations used by content

    /** Hard interruption (I7, Q7): cancels the interaction, clears weak scripts and closes the modal. */
    fun interrupt(player: Player) {
        player.interaction = null
        player.route.clear()
        player.closeModal()
    }

    /** Starts an interaction (a new one interrupts the old: I7). */
    fun interact(player: Player, target: InteractionTarget, option: Int) {
        if (player.isDelayed) return
        interrupt(player)
        val (op, ap) =
            when (target) {
                is InteractionTarget.Loc -> content.opLoc(target.id, option) to content.apLoc(target.id, option)
                is InteractionTarget.Obj -> content.opObj(target.id, option) to content.apObj(target.id, option)
                is InteractionTarget.OfNpc -> content.opNpc(target.npc.type, option) to content.apNpc(target.npc.type, option)
                is InteractionTarget.OfPlayer -> content.opPlayer(option) to content.apPlayer(option)
            }
        player.interaction = Interaction(target, option, op, ap)
    }

    fun setRunMode(player: Player, enabled: Boolean) {
        player.runMode = enabled
    }

    fun teleport(entity: Entity, to: CoordGrid) {
        entity.route.clear()
        entity.coord = to
        entity.teleportedThisTick = true
        if (entity is Player) entity.interaction = null
    }

    /** Adds an NPC; index = first free slot (index order = processing order). */
    fun spawnNpc(type: Int, size: Int, at: CoordGrid, wanderRange: Int): Npc {
        var index = npcs.indexOf(null)
        if (index < 0) {
            index = npcs.size
            npcs += null
        }
        val npc = Npc(this, index, type, size, at, wanderRange)
        npcs[index] = npc
        network.npcAdded(npc.index, type, at.level, at.x, at.z)
        return npc
    }

    internal fun onScriptError(entity: Entity, error: Throwable) {
        logger.error(error) { "script error on $entity at tick $tick" }
    }

    // ---------------------------------------------------------------- login / logout / PID

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
        addPlayer(Player(this, slot, request.username, session, config.spawn))
    }

    /** Registers a player whose session is already accepted (login, scenario runner, replay). */
    fun addPlayer(player: Player) {
        check(players[player.slot] == null) { "slot ${player.slot} taken" }
        players[player.slot] = player
        // A PID is assigned on login (Wiki); first re-randomisation after one window.
        nextPidShuffle[player.slot] = tick + rng.nextInt(config.pidShuffle.first, config.pidShuffle.last + 1)
        player.session.beginGame(player.avatarState())
        player.session.syncAvatar(player.avatarState())
        content.onLogin(player)
        logger.info { "login: $player ($playerCount online)" }
    }

    /** Creates a player bound to [session] in [slot] (scenario runner / replay: no network login). */
    fun newPlayer(slot: Int, username: String, session: tp.protocol.api.ClientSession, at: CoordGrid): Player =
        Player(this, slot, username, session, at)

    private fun logout(player: Player) {
        player.queue.onLogout()
        content.onLogout(player)
        players[player.slot] = null
        interfaces.forget(player)
        player.session.close()
        logger.info { "logout: $player ($playerCount online)" }
    }

    /**
     * PID re-randomisation, per player (Wiki: "a PID is assigned which will randomise every 100 to 150 game ticks ...
     * for each player"): when a player's window expires, swap them to a uniformly random position in the order.
     * The swap necessarily moves one other slot too. tick.md OPEN-T1 records this interpretation.
     */
    private fun shufflePids() {
        for (slot in 1 until MAX_PLAYERS) {
            if (players[slot] == null || tick < nextPidShuffle[slot]) continue
            val from = pidOrder.indexOf(slot)
            val to = rng.nextInt(pidOrder.size)
            pidOrder[from] = pidOrder[to]
            pidOrder[to] = slot
            nextPidShuffle[slot] = tick + rng.nextInt(config.pidShuffle.first, config.pidShuffle.last + 1)
        }
    }

    /** Scenario runner: force a processing order (lowest PID first). Disables re-randomisation. */
    fun forcePidOrder(slots: List<Int>) {
        val rest = (1 until MAX_PLAYERS).filter { it !in slots }
        (slots + rest).forEachIndexed { i, s -> pidOrder[i] = s }
        pidShuffleEnabled = false
    }

    /** PID (0-based processing position) of a player slot. */
    fun pidOf(slot: Int): Int = pidOrder.indexOf(slot)

    companion object {
        /** Player indices 1..2047 (brief §5.2, §7). */
        const val MAX_PLAYERS = 2048

        /** 600 ms game tick (OSRS Wiki "Game tick"). */
        const val TICK_NANOS = 600_000_000L
        private const val NANOS_PER_MS = 1_000_000L
        private const val MISSED_LOG_SAMPLE = 100L
        private const val WALK_STEPS = 1
        private const val RUN_STEPS = 2
        private val WHITESPACE = Regex("\\s+")
    }
}

/** Tick inspector / scenario hooks (brief §5.5). Implementations must not block. */
interface TickTracer {
    fun onTickStart(tick: Long) {}

    fun onTickEnd(tick: Long) {}

    fun onCommand(player: Player, command: ClientCommand) {}

    fun onPhase(entity: Entity, phase: String) {}
}
