package tp.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.engine.entity.BuildArea
import tp.engine.entity.Entity
import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.engine.interact.Interaction
import tp.engine.interact.InteractionProcessor
import tp.engine.interact.InteractionTarget
import tp.engine.inv.GroundTiming
import tp.engine.inv.InvStack
import tp.engine.inv.Inventory
import tp.engine.inv.ItemJournal
import tp.engine.inv.ItemTransaction
import tp.engine.move.Movement
import tp.engine.npc.NpcAi
import tp.engine.script.Script
import tp.engine.script.ScriptRunner
import tp.engine.ui.Interfaces
import tp.engine.vars.PlayerVars
import tp.engine.zone.GroundObjs
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
    /** PvP world: everywhere outside safe zones counts as Wilderness level 15 (+ the real level) (Wiki "Wilderness"). */
    val pvpWorld: Boolean = false,
    /** Ground obj lifetimes (ground-items.md; OPEN-G1 is this flag). */
    val groundTiming: GroundTiming = GroundTiming(),
)

/**
 * One game world on its own platform thread, running the tick pipeline of docs/mechanics/tick.md.
 * Everything below runs on the world thread except [onLogin] (network threads enqueue).
 */
class World(
    val config: WorldConfig,
    val data: WorldData,
    private val network: GameNetwork,
    val content: ContentHandlers = ContentHandlers.NONE,
    /** Audit trail of committed item transactions. */
    private val itemJournal: ItemJournal = ItemJournal.NONE,
) : LoginGate {
    /** The world's only RNG; content must use it (brief §5.2). */
    val rng: SplittableRandom = SplittableRandom(config.seed)

    var tick: Long = 0
        private set

    val movement = Movement(data.collision)
    internal val interactions = InteractionProcessor(data.collision, movement)
    val interfaces = Interfaces()
    val ground = GroundObjs()
    private val npcAi = NpcAi(this)

    private val players = arrayOfNulls<Player>(MAX_PLAYERS)
    private val npcs = ArrayList<Npc?>()

    /** Slots in processing order (PID order); each player's position re-randomises on its own window. */
    private val pidOrder = IntArray(MAX_PLAYERS - 1) { it + 1 }

    /** Per slot: tick of that player's next PID re-randomisation. */
    private val nextPidShuffle = LongArray(MAX_PLAYERS)
    private var pidShuffleEnabled = true

    private val loginInbox = ConcurrentLinkedQueue<LoginRequest>()

    /** Players added without a network login (scenario runner, replay); admitted in post-tick like logins. */
    private val pendingAdds = ArrayDeque<Player>()

    /** The entity whose turn (NPC/player phase) is running, else null (queue timing, scripts.md Q13). */
    internal var currentTurn: Entity? = null
        private set

    @Volatile private var running = false
    private var booted = false
    private var thread: Thread? = null

    /** Hooks for the tick inspector / scenario runner (null in production unless tracing is on). */
    var tracer: TickTracer? = null

    val playerCount: Int get() = players.count { it != null }

    fun forEachPlayer(action: (Player) -> Unit) {
        for (slot in pidOrder) players[slot]?.let(action)
    }

    fun player(slot: Int): Player? = players.getOrNull(slot)

    fun npc(index: Int): Npc? = npcs.getOrNull(index)

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
        if (!booted) {
            // World-start content (spawns) runs once before the first tick, however the world is driven.
            booted = true
            content.onWorldStart(this)
        }
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

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount") // one branch per command; early-out on invalid input
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

            is ClientCommand.OpLoc -> {
                if (command.option !in OP_RANGE || !inBuildArea(player, command.x, command.z)) return invalid(player, command)
                val loc = data.locs.find(player.coord.level, command.x, command.z, command.id) ?: return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, loc, command.option)
            }

            is ClientCommand.OpNpc -> {
                val npc = npcs.getOrNull(command.index)
                if (command.option !in OP_RANGE || npc == null || !inView(player, npc)) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, InteractionTarget.OfNpc(npc), command.option)
            }

            is ClientCommand.OpPlayer -> {
                val other = players.getOrNull(command.index)
                if (command.option !in PLAYER_OP_RANGE || !isOtherVisible(player, other)) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, InteractionTarget.OfPlayer(other ?: return), command.option)
            }

            is ClientCommand.OpPlayerT -> {
                val other = players.getOrNull(command.index)
                val open = interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)
                if (!open || !isOtherVisible(player, other)) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, InteractionTarget.OfPlayer(other ?: return), command.component, targeted = true)
            }

            is ClientCommand.OpNpcT -> {
                val npc = npcs.getOrNull(command.index)
                val open = interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)
                if (!open || npc == null || !inView(player, npc)) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, InteractionTarget.OfNpc(npc), command.component, targeted = true)
            }

            is ClientCommand.OpObj -> {
                val at = CoordGrid(command.x, command.z, player.coord.level)
                val obj = ground.find(at, command.id, player)
                if (command.option !in OP_RANGE || obj == null) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, InteractionTarget.Obj(at, obj.id), command.option)
            }

            is ClientCommand.Button -> {
                if (command.option !in BUTTON_OP_RANGE || !interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)) {
                    return invalid(player, command)
                }
                content.onButton(player, command.component, command.option, command.sub, command.obj)
            }

            is ClientCommand.PublicChat -> {
                if (command.text.isEmpty() || command.text.length > MAX_CHAT_LENGTH) return invalid(player, command)
                player.session.publicChat(command.text, command.colour, command.effect)
            }

            is ClientCommand.ResumePause -> {
                if (!interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)) return invalid(player, command)
                player.resumeInput(Unit)
            }

            ClientCommand.CloseModal -> {
                player.closeModal()
            }

            is ClientCommand.WindowStatus -> {
                player.windowMode = command.mode
            }
        }
    }

    /** Ops are only possible on things the client can see: the 104x104 build area. */
    private fun inBuildArea(player: Player, x: Int, z: Int): Boolean =
        x - player.buildArea.x in 0 until BuildArea.SIZE && z - player.buildArea.z in 0 until BuildArea.SIZE

    private fun isOtherVisible(player: Player, other: Player?): Boolean = other != null && other !== player && inView(player, other)

    private fun inView(player: Player, other: Entity): Boolean =
        other.coord.level == player.coord.level &&
            maxOf(kotlin.math.abs(other.coord.x - player.coord.x), kotlin.math.abs(other.coord.z - player.coord.z)) <= VIEW_DISTANCE

    /**
     * Hostile/stale input (brief §12): dropped and counted. Stale clicks happen legitimately (an NPC walked away), so only
     * a sustained rate disconnects: more than [INVALID_INPUT_LIMIT] in one [INVALID_INPUT_WINDOW]-tick window.
     */
    private fun invalid(player: Player, command: ClientCommand) {
        tracer?.onInvalid(player, command)
        if (tick >= player.invalidWindowEnd) {
            player.invalidWindowEnd = tick + INVALID_INPUT_WINDOW
            player.invalidInputs = 0
        }
        if (++player.invalidInputs > INVALID_INPUT_LIMIT) {
            logger.warn { "disconnecting $player: ${player.invalidInputs} invalid commands, last $command" }
            player.session.close()
        }
    }

    /** Phase 3: NPCs in index order (tick.md). */
    private fun npcPhase() {
        for (npc in npcs) {
            if (npc == null || npc.hidden) continue
            npc.turnTick = tick
            currentTurn = npc
            npc.stepsThisTick = 0
            if (ScriptRunner.resumeDelayed(npc)) tracer?.onPhase(npc, "delay-end")
            npc.timer.process()
            npc.queue.process()
            npcAi.process(npc)
            interactions.processNpc(npc)
        }
        currentTurn = null
    }

    /** Phase 4: players in PID order (tick.md). */
    private fun playerPhase() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            player.turnTick = tick
            currentTurn = player
            player.stepsThisTick = 0
            if (ScriptRunner.resumeDelayed(player)) tracer?.onPhase(player, "delay-end")
            if (player.queue.hasStrong()) player.closeModal() // Q3
            player.queue.process()
            player.timers.process()
            player.areaQueue.process()
            interactions.processPlayer(player, if (player.isRunningNow) RUN_STEPS else WALK_STEPS)
            if (player.route.isEmpty) player.routeCtrl = false
        }
        currentTurn = null
    }

    /** Phase 5: logouts, bounded logins, PID shuffle. */
    private fun postTick() {
        for (slot in 1 until MAX_PLAYERS) {
            val player = players[slot] ?: continue
            if (!player.session.isOpen) logout(player)
        }
        while (pendingAdds.isNotEmpty()) addPlayer(pendingAdds.removeFirst())
        var admitted = 0
        while (admitted < config.maxLoginsPerTick) {
            val request = loginInbox.poll() ?: break
            admit(request)
            admitted++
        }
        if (pidShuffleEnabled) shufflePids()
        ground.process(tick)
        processRespawns()
    }

    /** Phase 6: world state is frozen; sync avatars, build infos (parallel in RSProt), flush each session once. */
    private fun infoPass() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            isolated(player, "info pass") {
                if (BuildArea.needsRebuild(player.coord, player.buildArea)) {
                    player.buildArea = BuildArea.originFor(player.coord)
                    player.session.send(ServerMessage.Rebuild(player.coord.x, player.coord.z))
                    player.zoneSync.reset()
                }
                player.session.syncAvatar(player.avatarState())
                player.teleportedThisTick = false
                player.flushState()
                player.zoneSync.process(player, ground)
            }
        }
        ground.clearEvents()
        for (npc in npcs) if (npc != null && !npc.hidden) network.npcSync(npc.index, npc.coord.level, npc.coord.x, npc.coord.z)
        network.buildInfos()
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            if (player.session.isOpen) isolated(player, "flush") { player.session.flushInfos() }
        }
        network.afterFlush()
    }

    /**
     * A failure while talking to one player's session (protocol encoding, a closed channel) disconnects that player
     * instead of stopping the world thread. Logged at error level: it is always a bug or a broken connection.
     */
    @Suppress("TooGenericExceptionCaught") // any per-session failure must be contained
    private inline fun isolated(player: Player, what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: RuntimeException) {
            logger.error(e) { "$what failed for $player; disconnecting" }
            player.session.close()
        }
    }

    // ---------------------------------------------------------------- operations used by content

    /** Hard interruption (I7, Q7): cancels the interaction, clears weak scripts and closes the modal. */
    fun interrupt(player: Player) {
        player.interaction = null
        player.route.clear()
        player.closeModal()
    }

    /**
     * Starts an interaction (a new one interrupts the old: I7). [targeted]: [option] is a packed interface component
     * used on the target (spells: OPPLAYERT/OPNPCT).
     */
    fun interact(player: Player, target: InteractionTarget, option: Int, targeted: Boolean = false) {
        if (player.isDelayed) return
        interrupt(player)
        val (op, ap) =
            when {
                targeted && target is InteractionTarget.OfPlayer -> content.opPlayerT(option) to content.apPlayerT(option)
                targeted && target is InteractionTarget.OfNpc -> content.opNpcT(option) to content.apNpcT(option)
                else -> scriptsFor(target, option)
            }
        player.interaction = Interaction(target, option, op, ap)
    }

    private fun scriptsFor(target: InteractionTarget, option: Int): Pair<Script<Player>?, Script<Player>?> =
        when (target) {
            is InteractionTarget.Loc -> content.opLoc(target.id, option) to content.apLoc(target.id, option)
            is InteractionTarget.Obj -> content.opObj(target.id, option) to content.apObj(target.id, option)
            is InteractionTarget.OfNpc -> content.opNpc(target.npc.type, option) to content.apNpc(target.npc.type, option)
            is InteractionTarget.OfPlayer -> content.opPlayer(option) to content.apPlayer(option)
        }

    /**
     * Runs [block] as one atomic item transaction for [player] (brief §12). If [block] returns false or any step fails,
     * every change is rolled back and false is returned; otherwise the changes are journaled with [reason].
     */
    fun transaction(player: Player, reason: String, block: ItemTransaction.() -> Boolean): Boolean {
        val tx = ItemTransaction(data.configs.objs, ground, tick, config.groundTiming)
        val ok = tx.block() && !tx.failed
        if (!ok) {
            tx.rollback()
            return false
        }
        if (!tx.isEmpty) itemJournal.record(tick, player.username, reason, tx.entries())
        return true
    }

    /** [player]'s inventory [invId] (created empty on first use, sized from the `inv` config). */
    fun inventory(player: Player, invId: Int, stack: InvStack = InvStack.NORMAL): Inventory =
        player.invs.getOrPut(invId) {
            Inventory(
                invId,
                data.configs.invs
                    .getValue(invId)
                    .size,
                stack,
            )
        }

    /** Starts sending [inv] to its owner; the next send is a full update. */
    fun transmitInventory(inv: Inventory) {
        inv.transmitted = true
        inv.refreshAll()
    }

    /**
     * Sends [player]'s look: [worn] = obj per wear position 0..11 (-1 empty); each obj's `wearpos2/3` (cache) hide
     * other positions (e.g. a full helm hides hair).
     */
    fun refreshAppearance(player: Player, worn: IntArray, combatLevel: Int, skullIcon: Int, prayerIcon: Int) {
        val hidden = IntArray(APPEARANCE_SLOTS * 2) { -1 }
        for (pos in 0 until APPEARANCE_SLOTS) {
            val type = worn.getOrNull(pos)?.takeIf { it >= 0 }?.let { data.configs.objs[it] } ?: continue
            hidden[pos * 2] = type.wearPos2
            hidden[pos * 2 + 1] = type.wearPos3
        }
        player.session.appearance(tp.protocol.api.Appearance(worn.copyOf(APPEARANCE_SLOTS), hidden, combatLevel, skullIcon, prayerIcon))
    }

    /** Obj config (wear positions, params) for content facades. */
    fun objType(id: Int): tp.cache.config.obj.ObjType? = data.configs.objs[id]

    /**
     * A projectile from [from] to [to] (visual only), timed in client cycles from this tick's info pass: it starts
     * after [startCycles] and arrives after [endCycles] (combat-timeline.md OPEN-C1).
     */
    fun projectile(from: Entity, to: Entity, id: Int, startHeight: Int, endHeight: Int, startCycles: Int, endCycles: Int, angle: Int) {
        val c = from.coord
        val op =
            tp.protocol.api.ZoneOp.Projectile(
                c.x and ZONE_MASK,
                c.z and ZONE_MASK,
                id,
                startHeight,
                endHeight,
                startCycles,
                endCycles,
                angle,
                0,
                from.avatarRef,
                to.avatarRef,
                to.coord.x,
                to.coord.z,
                to.coord.level,
            )
        ground.transient(c, op)
    }

    /** Logs [player] out at the end of this tick (logout button). */
    fun requestLogout(player: Player) {
        player.session.send(ServerMessage.Logout)
        player.session.close()
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

    private val respawns = ArrayList<Pair<Long, Npc>>()

    /** Removes [npc] from view now and puts it back at its spawn, fully healed, after [delay] ticks. */
    fun respawnNpc(npc: Npc, delay: Int) {
        if (npc.hidden) return
        npc.hidden = true
        npc.route.clear()
        network.npcRemoved(npc.index)
        respawns += (tick + delay) to npc
    }

    private fun processRespawns() {
        val due = respawns.filter { it.first <= tick }
        if (due.isEmpty()) return
        respawns.removeAll(due.toSet())
        for ((_, npc) in due) {
            npc.resetForRespawn()
            network.npcAdded(npc.index, npc.type, npc.coord.level, npc.coord.x, npc.coord.z)
        }
    }

    /** Adds an NPC of config [type] (size from its config). */
    fun spawnNpc(type: Int, at: CoordGrid, wanderRange: Int): Npc =
        spawnNpc(
            type,
            data.configs.npcs
                .getValue(type)
                .size,
            at,
            wanderRange,
        )

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

    internal fun npcVisual(index: Int, visual: tp.protocol.api.Visual) {
        network.npcVisual(index, visual)
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
        addPlayer(newPlayer(slot, request.username, session, config.spawn))
    }

    /** Scenario runner / replay: admit [player] in the next post-tick, exactly where a network login would be. */
    fun queueAdmission(player: Player) {
        pendingAdds += player
    }

    /** Registers a player whose session is already accepted (login, scenario runner, replay). */
    fun addPlayer(player: Player) {
        check(players[player.slot] == null) { "slot ${player.slot} taken" }
        players[player.slot] = player
        // A PID is assigned on login (Wiki); first re-randomisation after one window.
        nextPidShuffle[player.slot] = tick + rng.nextInt(config.pidShuffle.first, config.pidShuffle.last + 1)
        player.session.beginGame(player.avatarState())
        player.session.syncAvatar(player.avatarState())
        player.vars.sendAll(player.session)
        content.onLogin(player)
        tracer?.onLogin(player)
        logger.info { "login: $player ($playerCount online)" }
    }

    /** Creates a player bound to [session] in [slot] (scenario runner / replay: no network login). */
    fun newPlayer(slot: Int, username: String, session: tp.protocol.api.ClientSession, at: CoordGrid): Player =
        Player(this, slot, username, session, at, PlayerVars(data.configs.varps.size, data.configs.varbits))

    private fun logout(player: Player) {
        tracer?.onLogout(player)
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

    /**
     * Hash of the logic state replay compares tick by tick: every player's slot, coord, run mode, delay, inventories,
     * varps and stats, and every NPC's coord. Not on a hot path (replay/scenario only).
     */
    @Suppress("MagicNumber") // bit shifts that pack two values into one hash input
    fun stateHash(): Long {
        var h = tick

        fun mix(v: Long) {
            h = (h xor v) * HASH_PRIME
        }
        for (slot in pidOrder) {
            val p = players[slot] ?: continue
            mix(slot.toLong())
            mix(p.coord.packed.toLong())
            mix(if (p.runMode) 1 else 0)
            mix(p.delayedUntil)
            mix(p.runEnergy.toLong())
            for (inv in p.invs.values.sortedBy { it.invId }) {
                for (i in 0 until inv.size) {
                    mix(inv.obj(i).toLong() shl 32 or inv.count(i).toLong())
                }
            }
            mix(p.vars.hash())
            for (stat in 0 until tp.engine.stat.PlayerStats.COUNT) {
                mix(
                    p.stats.xpTenths(stat).toLong() shl 8 or p.stats.level(stat).toLong(),
                )
            }
        }
        for (npc in npcs) if (npc != null) mix(npc.coord.packed.toLong())
        return h
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

        private const val ZONE_MASK = 7

        /** Appearance wear positions 0..11. */
        private const val APPEARANCE_SLOTS = 12

        /** FNV-1a 64-bit prime, for [stateHash]. */
        private const val HASH_PRIME = 0x100000001b3L
        private val WHITESPACE = Regex("\\s+")

        /** Loc/NPC/obj ops 1..5 (op6 = examine, handled separately). */
        private val OP_RANGE = 1..5

        /** Player ops 1..8 (SET_PLAYER_OP slots). */
        private val PLAYER_OP_RANGE = 1..8

        /** IF3 button ops 1..32 (IF_SETEVENTS op bits). */
        private val BUTTON_OP_RANGE = 1..32
        private const val COMPONENT_SHIFT = 16

        /** Public chat line limit: the client's chatbox input is capped at 80 characters. */
        private const val MAX_CHAT_LENGTH = 80

        /** Player/NPC view distance in tiles (RSProt player/NPC info default view distance 15). */
        private const val VIEW_DISTANCE = 15

        /** Invalid-input budget (ours, DECISIONS D-022): 50 per 100 ticks before disconnect. */
        private const val INVALID_INPUT_LIMIT = 50
        private const val INVALID_INPUT_WINDOW = 100L
    }
}

/** Tick inspector / scenario hooks (brief §5.5). Implementations must not block. */
interface TickTracer {
    fun onTickStart(tick: Long) {}

    fun onTickEnd(tick: Long) {}

    fun onCommand(player: Player, command: ClientCommand) {}

    fun onPhase(entity: Entity, phase: String) {}

    fun onInvalid(player: Player, command: ClientCommand) {}

    fun onLogin(player: Player) {}

    fun onLogout(player: Player) {}
}

/** Several tracers at once (inspector + replay recorder). */
class Tracers(private vararg val all: TickTracer) : TickTracer {
    override fun onTickStart(tick: Long) = all.forEach { it.onTickStart(tick) }

    override fun onTickEnd(tick: Long) = all.forEach { it.onTickEnd(tick) }

    override fun onCommand(player: Player, command: ClientCommand) = all.forEach { it.onCommand(player, command) }

    override fun onPhase(entity: Entity, phase: String) = all.forEach { it.onPhase(entity, phase) }

    override fun onInvalid(player: Player, command: ClientCommand) = all.forEach { it.onInvalid(player, command) }

    override fun onLogin(player: Player) = all.forEach { it.onLogin(player) }

    override fun onLogout(player: Player) = all.forEach { it.onLogout(player) }
}
