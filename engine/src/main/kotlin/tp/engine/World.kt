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
import tp.engine.save.PlayerSave
import tp.engine.script.Script
import tp.engine.script.ScriptRunner
import tp.engine.tick.TickMetrics
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
import tp.protocol.api.SocialAction
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
@Suppress("LargeClass") // the tick pipeline and its world-scoped state; split along phases when it grows further
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

    /** This world's collision (copy-on-write over the shared cache collision; dynamic locs edit it). */
    val collision = tp.engine.map.WorldCollision(data.collision)

    /** Loc changes on top of the cache map (doors, traps, webs). */
    val locs = tp.engine.map.DynamicLocs(data.configs.locs, data.locs, collision)

    /** Instanced areas built from template zones (docs/mechanics/instances.md). */
    val instances = tp.engine.map.Instances(data.locs, data.configs.locs, locs, collision)

    /** IN6: instance builds requested this tick, attached in the next tick's pre-tick (deterministic). */
    private val pendingBuilds = ArrayList<PendingBuild>()

    private class PendingBuild(
        val requestedTick: Long,
        val future: java.util.concurrent.Future<tp.engine.map.Instances.BuildPlan>,
        val onAttached: (tp.engine.map.Instances.Instance) -> Unit,
    )

    /**
     * IN6: allocates an instance of [widthSquares] x [lengthSquares] map squares now and prepares its zones off the
     * world thread; the zones attach in the pre-tick of the next tick (waiting for the build if it is not done, so the
     * attach tick never depends on thread timing), then [onAttached] runs on the world thread.
     */
    fun buildInstance(
        widthSquares: Int,
        lengthSquares: Int,
        copies: List<tp.engine.map.Instances.ZoneCopy>,
        squares: List<tp.engine.map.Instances.SquareCopy>,
        onAttached: (tp.engine.map.Instances.Instance) -> Unit,
    ): tp.engine.map.Instances.Instance {
        val instance = instances.create(widthSquares, lengthSquares)
        // A copy aimed outside instance space is relative to the new instance's south-west corner (generated layouts).
        val placed =
            copies.map { c ->
                if (instances.isInstanceSpace(c.to)) {
                    c
                } else {
                    tp.engine.map.Instances.ZoneCopy(
                        c.from,
                        CoordGrid(instance.baseX + c.to.x, instance.baseZ + c.to.z, c.to.level),
                        c.rotation,
                    )
                }
            }
        val future = INSTANCE_BUILDER.submit<tp.engine.map.Instances.BuildPlan> { instances.prepare(instance, placed, squares) }
        pendingBuilds += PendingBuild(tick, future, onAttached)
        return instance
    }

    /** Pre-tick: attaches every build requested on an earlier tick, in request order. */
    private fun attachBuilds() {
        if (pendingBuilds.isEmpty()) return
        val due = pendingBuilds.filter { it.requestedTick < tick }
        pendingBuilds.removeAll(due.toSet())
        for (b in due) {
            val plan = b.future.get()
            val start = System.nanoTime()
            instances.attach(plan)
            metrics.instanceAttachNanos = System.nanoTime() - start
            if (!plan.instance.destroyed) b.onAttached(plan.instance)
        }
    }

    /**
     * IN9: tears an instance down with nothing left behind: its NPCs (live, dead or waiting to respawn) and ground objs
     * go, then its zones, collision and locs; players still inside are moved out on the next info pass (IN8).
     */
    fun destroyInstance(instance: tp.engine.map.Instances.Instance) {
        if (instance.destroyed) return
        for (i in npcs.indices) {
            val npc = npcs[i] ?: continue
            if (npc.coord in instance || npc.spawn in instance) removeNpc(npc)
        }
        for (sx in 0 until instance.widthSquares * ZONES_PER_SQUARE) {
            for (sz in 0 until instance.lengthSquares * ZONES_PER_SQUARE) {
                for (level in 0 until tp.engine.map.Instances.LEVELS) {
                    val zone =
                        tp.engine.zone.ZoneKey
                            .of(level, (instance.baseX shr ZONE_BITS) + sx, (instance.baseZ shr ZONE_BITS) + sz)
                    for (obj in ground.inZone(zone).toList()) ground.remove(obj)
                }
            }
        }
        instances.destroy(instance)
    }

    /** Boats and other moving worlds (docs/mechanics/world-entities.md). */
    val worldEntities = tp.engine.map.WorldEntities(instances) { network }

    val movement = Movement(collision.flags)
    internal val interactions = InteractionProcessor(collision.flags, movement)

    /** Whether [npc] can take one step by ([dx], [dz]) from where it stands (collision only). */
    internal fun canNpcStep(npc: Npc, dx: Int, dz: Int): Boolean =
        movement.canTravel(npc.coord.level, npc.coord.x, npc.coord.z, dx, dz, npc.size)

    /** Line of sight between two entities' footprints (projectile rules). */
    fun hasLineOfSight(from: Entity, to: Entity): Boolean = interactions.hasLineOfSight(from, to)

    val interfaces =
        Interfaces().apply {
            loadRedirects { id ->
                data.configs.enums[id]
                    ?.intValues
                    .orEmpty()
            }
        }

    /** World-scoped content state (plugin instances are shared by every world in the JVM; their state lives here). */
    val attrs: HashMap<Any, Any> = HashMap()
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
        // By index over the size at the start: content may spawn NPCs while this runs.
        val count = npcs.size
        for (i in 0 until count) npcs[i]?.let(action)
    }

    /** Called from network threads; admitted in post-tick on the world thread. */
    override fun onLogin(request: LoginRequest) {
        val bridge = accounts
        if (bridge == null) loginInbox.add(request) else bridge.authorize(request, this)
    }

    /** Account authority (hub); null = admit by username (dev, tests). Set before [start]. */
    var accounts: AccountBridge? = null

    private val authorizedInbox = ConcurrentLinkedQueue<Pair<LoginRequest, AccountProfile>>()
    private val tasks = ConcurrentLinkedQueue<() -> Unit>()

    /** Runs [task] on the world thread at the start of the next tick (hub events: kicks, broadcasts, messages). */
    fun execute(task: () -> Unit) {
        tasks.add(task)
    }

    /** The online player with hub account [accountId], or null. World thread. */
    fun playerByAccount(accountId: Long): Player? = players.firstOrNull { it != null && it.accountId == accountId }

    /** Called by the [AccountBridge] from any thread: admit this login in the next post-tick with its profile. */
    fun admitAuthorized(request: LoginRequest, profile: AccountProfile) {
        authorizedInbox.add(request to profile)
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
            try {
                runTick()
            } catch (
                @Suppress("TooGenericExceptionCaught") e: RuntimeException,
            ) {
                // Last line of defence: one faulty tick must never stop the world (every player would freeze).
                // Per-player work is already isolated; this catches anything that escaped it. Logged every time,
                // since it should never happen and must be fixed at its source.
                logger.error(e) { "world ${config.id}: tick $tick failed; continuing" }
            }
            val deadline = start + (tick * TICK_NANOS)
            val now = System.nanoTime()
            if (now > deadline) {
                missed++
                metrics.missedDeadlines = missed
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
        val start = System.nanoTime()
        attachBuilds()
        while (true) (tasks.poll() ?: break).invoke()
        inputPhase()
        worldEntityPhase()
        npcPhase()
        playerPhase()
        content.onWorldTick(this)
        postTick()
        val logicEnd = System.nanoTime()
        infoPass()
        metrics.logic.record(logicEnd - start)
        metrics.info.record(System.nanoTime() - logicEnd)
        metrics.ticks = tick
        tracer?.onTickEnd(tick)
    }

    /** Tick-time histograms (health checks, metrics, alerts, load tests). */
    val metrics = TickMetrics()

    /**
     * Phase 2b (world-entities.md WE10): world entities (boats) move after input, so a heading or sail change of this
     * tick acts this tick, and before NPCs and players, who then act on the decks' new positions.
     */
    private fun worldEntityPhase() {
        if (worldEntities.all().isEmpty()) return
        content.onWorldEntityPhase(this)
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
        // IN11: an observer cannot move or interact with the world; interface input (its HUD) still works.
        if (player.observer && command.isWorldAction()) return
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
                val loc = locs.find(player.coord.level, command.x, command.z, command.id) ?: return invalid(player, command)
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
                interact(
                    player,
                    InteractionTarget.OfPlayer(other ?: return),
                    command.component,
                    targeted = true,
                    used = UsedItem(command.component, command.sub, command.obj),
                )
            }

            is ClientCommand.OpNpcT -> {
                val npc = npcs.getOrNull(command.index)
                val open = interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)
                if (!open || npc == null || !inView(player, npc)) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(
                    player,
                    InteractionTarget.OfNpc(npc),
                    command.component,
                    targeted = true,
                    used = UsedItem(command.component, command.sub, command.obj),
                )
            }

            is ClientCommand.OpLocT -> {
                if (!inBuildArea(player, command.x, command.z) || !interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)) {
                    return invalid(player, command)
                }
                val loc = locs.find(player.coord.level, command.x, command.z, command.id) ?: return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(player, loc, command.component, targeted = true, used = UsedItem(command.component, command.sub, command.obj))
            }

            is ClientCommand.OpObjT -> {
                val at = CoordGrid(command.x, command.z, player.coord.level)
                val obj = ground.find(at, command.id, player)
                if (obj == null || !interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)) return invalid(player, command)
                player.routeCtrl = command.ctrl
                interact(
                    player,
                    InteractionTarget.Obj(at, obj.id),
                    command.component,
                    targeted = true,
                    used = UsedItem(command.component, command.sub, command.obj),
                )
            }

            is ClientCommand.SetHeading -> {
                if (command.heading !in 0 until HEADINGS) return invalid(player, command)
                content.onHeading(player, command.heading)
            }

            is ClientCommand.OpWorldEntity -> {
                val entity = worldEntities.byIndex(command.index)
                if (command.option !in OP_RANGE || entity == null || !worldEntityInView(player, entity)) return invalid(player, command)
                content.onOpWorldEntity(player, command.index, command.option)
            }

            is ClientCommand.Examine -> {
                val known =
                    when (command.kind) {
                        0 -> data.configs.locs[command.id] != null
                        1 -> data.configs.npcs[command.id] != null
                        2 -> data.configs.objs[command.id] != null
                        else -> false
                    }
                if (!known) return invalid(player, command)
                content.onExamine(player, command.kind, command.id)
            }

            is ClientCommand.ButtonT -> {
                val fromOpen = interfaces.isOpen(player, command.fromComponent ushr COMPONENT_SHIFT)
                val toOpen = interfaces.isOpen(player, command.toComponent ushr COMPONENT_SHIFT)
                if (!fromOpen || !toOpen) return invalid(player, command)
                content.onButtonT(
                    player,
                    command.fromComponent,
                    command.fromSub,
                    command.fromObj,
                    command.toComponent,
                    command.toSub,
                    command.toObj,
                )
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
                if (command.subop < -1 || command.subop > MAX_SUBOP) return invalid(player, command)
                content.onButton(player, command.component, command.option, command.sub, command.obj, command.subop)
            }

            is ClientCommand.ButtonDrag -> {
                val fromOpen = interfaces.isOpen(player, command.fromComponent ushr COMPONENT_SHIFT)
                val toOpen = interfaces.isOpen(player, command.toComponent ushr COMPONENT_SHIFT)
                if (!fromOpen || !toOpen) return invalid(player, command)
                content.onButtonDrag(player, command.fromComponent, command.fromSub, command.toComponent, command.toSub)
            }

            is ClientCommand.PublicChat -> {
                if (command.text.isEmpty() || command.text.length > MAX_CHAT_LENGTH) return invalid(player, command)
                if (player.isMuted()) return player.session.send(ServerMessage.GameMessage(MUTED_MESSAGE))
                if (command.type == CHAT_TYPE_CLAN || command.type == CHAT_TYPE_GUEST_CLAN) {
                    // RSProt MessagePublic chat types: 3 = clan, 4 = guest clan.
                    val bridge = accounts ?: return player.session.send(ServerMessage.GameMessage(NO_HUB_MESSAGE))
                    return bridge.clan(player, ClanRequest.Message(command.type == CHAT_TYPE_GUEST_CLAN, command.text)) { p, err ->
                        err?.let { p.session.send(ServerMessage.GameMessage(it)) }
                    }
                }
                if (command.type == CHAT_TYPE_FRIENDS_CHANNEL) {
                    // "/" lines go to the friends chat (RSProt MessagePublic chat types: 2 = friend channel).
                    val bridge = accounts ?: return player.session.send(ServerMessage.GameMessage(NO_HUB_MESSAGE))
                    return bridge.social(player, SocialAction.CHANNEL_MESSAGE, "", command.text)
                }
                player.session.publicChat(command.text, command.colour, command.effect)
            }

            is ClientCommand.ClanFullRequest -> {
                // CL9: answer from the last snapshot, or "no clan" so the panel stops waiting. Group ironman (1+) has none.
                val guest = command.clanId < 0
                val i = if (guest) 1 else 0
                val last = if (command.settings) player.clanSettings[i] else player.clanChannel[i]
                val none = if (command.settings) ServerMessage.ClanSettingsLeft(guest) else ServerMessage.ClanChannelLeft(guest)
                player.session.send(if (command.clanId > 0) none else last ?: none)
            }

            is ClientCommand.ChatFilters -> {
                // Gameframe G6: the client filters what it shows; the server keeps the setting and sends it back.
                if (command.publicChat !in 0..CHAT_FILTER_MAX || command.privateChat !in 0..2 || command.trade !in 0..2) {
                    return invalid(player, command)
                }
                val packed = (command.publicChat.toLong() shl CHAT_FILTER_BITS * 2) or (command.privateChat.toLong() shl CHAT_FILTER_BITS)
                player.saved[CHAT_FILTER_KEY] = packed or command.trade.toLong()
                player.session.send(ServerMessage.ChatFilters(command.publicChat, command.privateChat, command.trade))
            }

            is ClientCommand.Social -> {
                val textOk = command.action != SocialAction.PRIVATE_MESSAGE || command.text.length in 1..MAX_CHAT_LENGTH
                val nameOk = command.action == SocialAction.CHANNEL_LEAVE || command.name.length in 1..MAX_NAME_LENGTH
                if (!nameOk || !textOk || command.rank !in CHANNEL_RANKS) return invalid(player, command)
                val bridge = accounts ?: return player.session.send(ServerMessage.GameMessage(NO_HUB_MESSAGE))
                if (command.action == SocialAction.CLAN_KICK || command.action == SocialAction.CLAN_BAN) {
                    // CL7: kick (or ban) from the clan channel's member menu; the hub checks the kicker's rank.
                    return bridge.clan(player, ClanRequest.Kick(command.name, command.action == SocialAction.CLAN_BAN)) { p, err ->
                        err?.let { p.session.send(ServerMessage.GameMessage(it)) }
                    }
                }
                if (command.action == SocialAction.CLAN_MESSAGE || command.action == SocialAction.CLAN_GUEST_MESSAGE) {
                    return invalid(player, command)
                }
                bridge.social(player, command.action, command.name, command.text, command.rank)
            }

            is ClientCommand.ResumePause -> {
                if (!interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)) return invalid(player, command)
                // A waiting dialogue script takes it; otherwise it is a pause-button click on an open panel (op 1).
                if (!player.resumeInput(PauseInput(command.component, command.sub))) {
                    content.onButton(player, command.component, PAUSE_BUTTON_OP, command.sub, -1)
                }
            }

            is ClientCommand.ResumeString -> {
                if (command.text.length > MAX_CHAT_LENGTH) return invalid(player, command)
                if (!player.resumeInput(command.text)) content.onDialogAnswer(player, command.text)
            }

            is ClientCommand.ScriptTrigger -> {
                if (command.payload.size > MAX_TRIGGER_BYTES) return invalid(player, command)
                if (!interfaces.isOpen(player, command.component ushr COMPONENT_SHIFT)) return invalid(player, command)
                content.onScriptTrigger(player, command.component, command.sub, command.obj, command.crc, command.payload)
            }

            is ClientCommand.ReportAbuse -> {
                // G7: names are at most 12 characters; rules are the client's list (ids < 64); only staff may mute.
                if (command.name.length > MAX_NAME_LENGTH || command.rule !in 0 until MAX_REPORT_RULE) return invalid(player, command)
                val mute = command.mute && player.staffLevel > 0
                logger.info { "report: ${player.username} reported '${command.name}' rule ${command.rule} mute=$mute" }
                accounts?.reported(player, command.name, command.rule, mute)
            }

            is ClientCommand.ResumeCount -> {
                if (!player.resumeInput(command.count)) content.onDialogAnswer(player, command.count)
            }

            is ClientCommand.ResumeObj -> {
                // Hostile input (§12): only -1 (cancel) or an obj the cache defines.
                if (command.obj != -1 && data.configs.objs[command.obj] == null) return invalid(player, command)
                content.onDialogAnswer(player, ObjDialogAnswer(command.obj))
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
        (x - player.buildArea.x in 0 until BuildArea.SIZE && z - player.buildArea.z in 0 until BuildArea.SIZE) ||
            onSameWorldEntity(player, x, z)

    /** WE8: a world entity is in view when it is shown and its pivot is near the player's root position. */
    private fun worldEntityInView(player: Player, entity: tp.engine.map.WorldEntities.WorldEntity): Boolean {
        if (!entity.visible) return false
        val root = worldEntities.at(player.coord)?.rootCoord ?: player.coord
        val pivot = entity.rootCoord
        return pivot.level == root.level && maxOf(kotlin.math.abs(pivot.x - root.x), kotlin.math.abs(pivot.z - root.z)) <= WORLD_ENTITY_VIEW
    }

    /** WE5: clicks on the deck of the world entity the player stands on name the deck's own tiles. */
    private fun onSameWorldEntity(player: Player, x: Int, z: Int): Boolean {
        val boat = worldEntities.at(player.coord) ?: return false
        return CoordGrid(x, z, player.coord.level) in boat
    }

    private fun isOtherVisible(player: Player, other: Player?): Boolean =
        other != null && other !== player && !other.observer && inView(player, other)

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
        // By index: NPC scripts may spawn NPCs (boss minions) during the phase; ones added now act from the next tick.
        val count = npcs.size
        for (i in 0 until count) {
            val npc = npcs[i]
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
            // Running needs energy (Wiki "Energy"): with none left the player walks.
            interactions.processPlayer(player, if (player.isRunningNow && player.runEnergy > 0) RUN_STEPS else WALK_STEPS)
            tp.engine.move.RunEnergy
                .process(player)
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
            val authorized = authorizedInbox.poll()
            if (authorized != null) {
                admit(authorized.first, authorized.second)
            } else {
                admit(loginInbox.poll() ?: break, null)
            }
            admitted++
        }
        autosave()
        if (pidShuffleEnabled) shufflePids()
        ground.process(tick)
        locs.process(tick)
        processRespawns()
        checkSystemUpdate()
    }

    /**
     * Sends a new build area when the player nears its edge, or a region rebuild when the player's instance changed
     * (instances.md IN4: copied or cleared zones show at once). A player in instance space with no instance there (a
     * torn-down instance, a stale login) is moved to the world spawn (IN5).
     */
    private fun rebuildIfNeeded(player: Player) {
        val inSpace = instances.isInstanceSpace(player.coord)
        val instance = if (inSpace) instances.at(player.coord) else null
        if (inSpace && instance == null) leaveLostInstance(player)
        // WE4: on a world entity the build area is the root world around the entity, rebuilt like any root area.
        val boat = instance?.let(worldEntities::of)
        if (boat != null) {
            val root = boat.rootCoord
            if (!BuildArea.needsRebuild(root, player.buildArea) && player.regionStamp == NO_REGION) return
            player.regionStamp = NO_REGION
            player.buildArea = BuildArea.originFor(root)
            player.session.send(ServerMessage.Rebuild(root.x, root.z))
            player.zoneSync.reset()
            player.deckSync.reset()
            return
        }
        val stamp = instance?.let { (it.id.toLong() shl Int.SIZE_BITS) or it.version.toLong() } ?: NO_REGION
        if (!BuildArea.needsRebuild(player.coord, player.buildArea) && stamp == player.regionStamp) return
        val reload = stamp != player.regionStamp
        player.regionStamp = stamp
        player.buildArea = BuildArea.originFor(player.coord)
        val message =
            if (instance == null) {
                ServerMessage.Rebuild(player.coord.x, player.coord.z)
            } else {
                val zones = instances.buildArea(instance, player.coord.x shr ZONE_BITS, player.coord.z shr ZONE_BITS)
                ServerMessage.RebuildRegion(player.coord.x, player.coord.z, reload, zones)
            }
        player.session.send(message)
        player.zoneSync.reset()
        player.deckSync.reset()
    }

    /** IN8: a player in instance space with no instance goes to their saved exit (raids) or the world spawn. */
    private fun leaveLostInstance(player: Player) {
        val exit = player.saved.remove(tp.engine.map.Instances.EXIT_KEY)
        teleport(player, exit?.let { CoordGrid(it.toInt()) } ?: config.spawn)
        content.onInstanceLost(player, exit != null)
    }

    /** Phase 6: world state is frozen; sync avatars, build infos (parallel in RSProt), flush each session once. */
    private fun infoPass() {
        for (slot in pidOrder) {
            val player = players[slot] ?: continue
            isolated(player, "info pass") {
                rebuildIfNeeded(player)
                player.session.syncAvatar(player.avatarState())
                player.teleportedThisTick = false
                player.flushState()
                val root = worldEntities.at(player.coord)?.rootCoord ?: player.coord
                player.zoneSync.process(player, ground, locs, root)
                player.deckSync.process(player, root, worldEntities, locs, ground)
            }
        }
        ground.clearEvents()
        locs.clearEvents()
        for (npc in npcs) if (npc != null && !npc.hidden) network.npcSync(npc.index, npc.coord.level, npc.coord.x, npc.coord.z)
        worldEntities.sync()
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
    fun interact(player: Player, target: InteractionTarget, option: Int, targeted: Boolean = false, used: UsedItem? = null) {
        if (player.isDelayed) return
        interrupt(player)
        player.usedItem = used
        val obj = used?.obj ?: -1
        val (op, ap) =
            when {
                targeted && target is InteractionTarget.OfPlayer -> {
                    content.opPlayerT(option) to content.apPlayerT(option)
                }

                targeted && target is InteractionTarget.OfNpc -> {
                    content.opNpcT(option, obj, target.npc.type) to
                        content.apNpcT(option, obj, target.npc.type)
                }

                targeted && target is InteractionTarget.Loc -> {
                    content.opLocT(option, obj, target.id) to
                        content.apLocT(option, obj, target.id)
                }

                targeted && target is InteractionTarget.Obj -> {
                    content.opObjT(option, obj, target.id) to
                        content.apObjT(option, obj, target.id)
                }

                else -> {
                    scriptsFor(player, target, option)
                }
            }
        player.interaction = Interaction(target, option, op, ap)
    }

    private fun scriptsFor(player: Player, target: InteractionTarget, option: Int): Pair<Script<Player>?, Script<Player>?> =
        when (target) {
            is InteractionTarget.Loc -> content.opLoc(target.id, option) to content.apLoc(target.id, option)
            is InteractionTarget.Obj -> content.opObj(target.id, option) to content.apObj(target.id, option)
            is InteractionTarget.OfNpc -> player.npcScripts(target.npc.type, option)
            is InteractionTarget.OfPlayer -> content.opPlayer(option) to content.apPlayer(option)
        }

    /** NPC op scripts: those of the type this player sees ([seenNpcType]) first, then the parent type's. */
    private fun Player.npcScripts(type: Int, option: Int): Pair<Script<Player>?, Script<Player>?> {
        val seen = seenNpcType(this, type)
        val op = (if (seen != type && seen >= 0) content.opNpc(seen, option) else null) ?: content.opNpc(type, option)
        val ap = (if (seen != type && seen >= 0) content.apNpc(seen, option) else null) ?: content.apNpc(type, option)
        return op to ap
    }

    /**
     * The NPC type [player] sees for [type]: a multi-NPC resolves through its varbit / varp (out of range = the last
     * entry; -1 = invisible), as the client does (NpcType `multinpc`).
     */
    fun seenNpcType(player: Player, type: Int): Int {
        val t = data.configs.npcs[type] ?: return type
        val multi = t.multiNpcs ?: return type
        val value =
            when {
                t.multiVarbit >= 0 -> player.vars.getBit(t.multiVarbit)
                t.multiVarp >= 0 -> player.vars[t.multiVarp]
                else -> -1
            }
        return if (value in 0 until multi.size - 1) multi[value] else multi.last()
    }

    /** Net gain per obj over all slots a transaction changed (null if nothing was created). */
    private fun created(entries: List<tp.engine.inv.ItemJournal.SlotChange>): Map<Int, Long>? {
        var net: HashMap<Int, Long>? = null
        for (e in entries) {
            if (e.oldObj == e.newObj && e.oldCount == e.newCount) continue
            val n = net ?: HashMap<Int, Long>().also { net = it }
            if (e.oldObj >= 0) n.merge(e.oldObj, -e.oldCount.toLong(), Long::plus)
            if (e.newObj >= 0) n.merge(e.newObj, e.newCount.toLong(), Long::plus)
        }
        return net?.filterValues { it > 0 }?.takeIf { it.isNotEmpty() }
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
        if (!tx.isEmpty) {
            val entries = tx.entries()
            itemJournal.record(tick, player.username, reason, entries)
            created(entries)?.let { content.onItemsCreated(player, reason, it) }
        }
        return true
    }

    /** [player]'s inventory [invId] (created empty on first use, sized from the `inv` config). */
    fun inventory(player: Player, invId: Int, stack: InvStack = defaultStack(invId)): Inventory =
        player.invs.getOrPut(invId) {
            Inventory(
                invId,
                data.configs.invs
                    .getValue(invId)
                    .size,
                stack,
            )
        }

    /** The bank stacks everything (RS Mod `InvStackType.Always` for `inv.bank`); other invs follow the obj. */
    private fun defaultStack(invId: Int): InvStack = if (invId == BANK_INV) InvStack.ALWAYS else InvStack.NORMAL

    /** Starts sending [inv] to its owner; the next send is a full update. */
    fun transmitInventory(inv: Inventory) {
        inv.transmitted = true
        inv.refreshAll()
    }

    /**
     * Sends [player]'s look: [worn] = obj per wear position 0..11 (-1 empty); each obj's `wearpos2/3` (cache) hide
     * other positions (e.g. a full helm hides hair).
     */
    fun refreshAppearance(
        player: Player,
        worn: IntArray,
        combatLevel: Int,
        skullIcon: Int,
        prayerIcon: Int,
        renderAnims: IntArray? = null,
    ) {
        val hidden = IntArray(APPEARANCE_SLOTS * 2) { -1 }
        for (pos in 0 until APPEARANCE_SLOTS) {
            val type = worn.getOrNull(pos)?.takeIf { it >= 0 }?.let { data.configs.objs[it] } ?: continue
            hidden[pos * 2] = type.wearPos2
            hidden[pos * 2 + 1] = type.wearPos3
        }
        player.session.appearance(
            tp.protocol.api.Appearance(worn.copyOf(APPEARANCE_SLOTS), hidden, combatLevel, skullIcon, prayerIcon, renderAnims),
        )
    }

    /** Obj ids by exact cache name (not noted, not placeholders), built on first use. */
    private val objsByName: Map<String, List<Int>> by lazy {
        data.configs.objs
            .filter { !it.isNoted && !it.isPlaceholder && it.name != "null" }
            .groupBy({ it.name }, { it.id })
    }

    fun objIdsNamed(name: String): List<Int> = objsByName[name].orEmpty()

    /** Tradeable, unnoted, non-placeholder objs whose name contains [query] (case-insensitive), at most [limit]. */
    fun tradeableObjsContaining(query: String, limit: Int): Set<Int> {
        val q = query.lowercase()
        val out = LinkedHashSet<Int>()
        for ((name, ids) in objsByName) {
            if (!name.lowercase().contains(q)) continue
            for (id in ids) {
                val t = data.configs.objs[id] ?: continue
                if (t.tradeable && !t.isNoted && !t.isPlaceholder) out += id
                if (out.size >= limit) return out
            }
        }
        return out
    }

    /** Obj config (wear positions, params) for content facades. */
    fun objType(id: Int): tp.cache.config.obj.ObjType? = data.configs.objs[id]

    /**
     * A projectile from [from] to [to] (visual only), timed in client cycles from this tick's info pass: it starts
     * after [startCycles] and arrives after [endCycles] (combat-timeline.md OPEN-C1).
     */
    fun projectile(
        from: Entity,
        to: Entity,
        id: Int,
        startHeight: Int,
        endHeight: Int,
        startCycles: Int,
        endCycles: Int,
        angle: Int,
        progress: Int = 0,
    ) {
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
                progress,
                from.avatarRef,
                to.avatarRef,
                to.coord.x,
                to.coord.z,
                to.coord.level,
            )
        ground.transient(c, op)
    }

    /** Tick at which a scheduled system update stops the world (-1 = none). */
    var systemUpdateAt: Long = -1
        private set

    /**
     * System update (brief §12): every client shows the countdown; new logins are refused; when it reaches zero every
     * player is logged out with a final save (durable at the hub before their accounts free up).
     */
    fun systemUpdate(seconds: Int) {
        val ticks = (seconds * MS_PER_SECOND / TICK_MS).toInt()
        systemUpdateAt = tick + ticks
        forEachPlayer { it.session.send(ServerMessage.SystemUpdate(ticks)) }
    }

    private fun checkSystemUpdate() {
        if (systemUpdateAt < 0 || tick < systemUpdateAt) return
        forEachPlayer { requestLogout(it) }
    }

    /** Grand Exchange guide prices (obj -> coins), refreshed from the hub by the world node; empty without a hub. */
    @Volatile var guidePrices: Map<Int, Int> = emptyMap()

    /** Worlds a player can hop to (id -> address), refreshed from the hub's directory by the world node. */
    @Volatile var worldDirectory: Map<Int, WorldAddress> = emptyMap()

    /**
     * World hop: tells the client to reconnect to [worldId] and logs the player out (final save; the hub holds the new
     * login until it lands). False if [worldId] is this world or unknown. Content checks the logout lock first.
     */
    fun hop(player: Player, worldId: Int): Boolean {
        if (worldId == config.id) return false
        val address = worldDirectory[worldId] ?: return false
        player.session.send(ServerMessage.LogoutTransfer(address.host, worldId, address.flags))
        player.session.close()
        return true
    }

    private fun Player.isMuted(): Boolean = mutedUntil < 0 || mutedUntil > System.currentTimeMillis()

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

    /** Removes [npc] for good (minions, summons): no respawn. */
    fun removeNpc(npc: Npc) {
        if (npcs.getOrNull(npc.index) !== npc) return
        if (!npc.hidden) network.npcRemoved(npc.index)
        npc.hidden = true
        npc.route.clear()
        npc.npcInteraction = null
        respawns.removeAll { it.second === npc }
        npcs[npc.index] = null
    }

    /** Shows loc [id] at [at] to [viewer] only (LOC_ADD_CHANGE, look only: collision and other viewers are unchanged). */
    fun privateLocChange(at: CoordGrid, id: Int, shape: Int, angle: Int, viewer: String) {
        ground.transient(
            at,
            tp.protocol.api.ZoneOp
                .LocAddChange(at.x and IN_ZONE_MASK, at.z and IN_ZONE_MASK, id, shape, angle),
            viewer,
        )
    }

    /** Seq [id] on the loc of [shape] at [at] (LOC_ANIM), for everyone observing its zone, or for [viewer] only. */
    fun locAnim(at: CoordGrid, shape: Int, angle: Int, id: Int, viewer: String? = null) {
        ground.transient(
            at,
            tp.protocol.api.ZoneOp
                .LocAnim(at.x and IN_ZONE_MASK, at.z and IN_ZONE_MASK, shape, angle, id),
            viewer,
        )
    }

    /** A spot anim on a tile (MAP_ANIM) for everyone observing its zone, or for [viewer] only. */
    fun mapAnim(at: CoordGrid, id: Int, height: Int, delay: Int, viewer: String? = null) {
        ground.transient(
            at,
            tp.protocol.api.ZoneOp
                .MapAnim(at.x and IN_ZONE_MASK, at.z and IN_ZONE_MASK, id, height, delay),
            viewer,
        )
    }

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
            content.onNpcSpawn(npc)
        }
    }

    /**
     * Adds an NPC of config [type] (size from its config). [init] runs before the spawn hooks, so attributes it sets
     * (a raid's run, scaling and room) are already there when content's spawn handlers see the NPC.
     */
    fun spawnNpc(type: Int, at: CoordGrid, wanderRange: Int, init: ((Npc) -> Unit)? = null): Npc =
        spawnNpc(
            type,
            data.configs.npcs
                .getValue(type)
                .size,
            at,
            wanderRange,
            init,
        )

    /** Adds an NPC; index = first free slot (index order = processing order). */
    fun spawnNpc(type: Int, size: Int, at: CoordGrid, wanderRange: Int, init: ((Npc) -> Unit)? = null): Npc {
        var index = npcs.indexOf(null)
        if (index < 0) {
            index = npcs.size
            npcs += null
        }
        val npc = Npc(this, index, type, size, at, wanderRange)
        npcs[index] = npc
        init?.invoke(npc)
        network.npcAdded(npc.index, type, at.level, at.x, at.z)
        content.onNpcSpawn(npc)
        return npc
    }

    internal fun npcVisual(index: Int, visual: tp.protocol.api.Visual) {
        network.npcVisual(index, visual)
    }

    internal fun onScriptError(entity: Entity, error: Throwable) {
        logger.error(error) { "script error on $entity at tick $tick" }
    }

    // ---------------------------------------------------------------- login / logout / PID

    private fun admit(request: LoginRequest, profile: AccountProfile?) {
        if (!request.crcValid) return request.reject(LoginRejection.OUT_OF_DATE)
        if (systemUpdateAt >= 0) return request.reject(LoginRejection.UPDATE_IN_PROGRESS)
        if (request.username.isBlank()) return request.reject(LoginRejection.INVALID_CREDENTIALS)
        if (players.any { it?.username.equals(request.username, ignoreCase = true) }) return request.reject(LoginRejection.ALREADY_ONLINE)
        val slot = (1 until MAX_PLAYERS).firstOrNull { players[it] == null } ?: return request.reject(LoginRejection.WORLD_FULL)
        val session =
            try {
                request.accept(slot, LoginDetails(staffModLevel = profile?.staffLevel ?: 0))
            } catch (
                @Suppress("TooGenericExceptionCaught") e: RuntimeException,
            ) {
                // The client can be gone by the time the hub answers (timeout, closed window): RSProt then fails on
                // the dead channel's pipeline (NoSuchElementException) or session state (IllegalStateException).
                logger.warn { "login accept failed for ${request.username}: $e" }
                profile?.let { accounts?.refused(it) }
                return
            }
        val player = newPlayer(slot, profile?.displayName ?: request.username, session, config.spawn)
        player.loginAddress = request.address
        player.loginResizable = request.resizable
        if (profile != null) {
            player.accountId = profile.accountId
            player.staffLevel = profile.staffLevel
            player.mutedUntil = profile.mutedUntil
            profile.save?.let { PlayerSave.apply(player, it) }
        }
        addPlayer(player)
        accounts?.loggedIn(player)
    }

    /** Staggered autosaves (brief §7): each player every [AUTOSAVE_TICKS], offset by slot. */
    private fun autosave() {
        val bridge = accounts ?: return
        for (slot in 1 until MAX_PLAYERS) {
            val player = players[slot] ?: continue
            if ((tick + slot) % AUTOSAVE_TICKS == 0L) bridge.saved(player, PlayerSave.snapshot(player), final = false)
        }
    }

    /** Saves every player now (graceful shutdown / system update); final saves release their accounts. */
    fun saveAll(final: Boolean) {
        val bridge = accounts ?: return
        for (slot in 1 until MAX_PLAYERS) players[slot]?.let { bridge.saved(it, PlayerSave.snapshot(it), final) }
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
        // G6: always sent at login (all "on" for a new account); the client redraws the chatbox when it arrives.
        val f = player.saved[CHAT_FILTER_KEY] ?: 0L
        val mask = (1L shl CHAT_FILTER_BITS) - 1
        val public = (f shr CHAT_FILTER_BITS * 2 and mask).toInt()
        player.session.send(ServerMessage.ChatFilters(public, (f shr CHAT_FILTER_BITS and mask).toInt(), (f and mask).toInt()))
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
        accounts?.saved(player, PlayerSave.snapshot(player), final = true)
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
        /** SET_HEADING values: 16 directions (sailing-movement.md). */
        private const val HEADINGS = 16

        /** RSProt `WorldEntityInfo.DEFAULT_RENDER_DISTANCE` (tiles). */
        private const val WORLD_ENTITY_VIEW = 31

        /** [Player.regionStamp] of a normal (non-instanced) build area. */
        private const val NO_REGION = -1L

        /** Tile to zone coordinate shift. */
        private const val ZONE_BITS = 3
        private const val ZONES_PER_SQUARE = 8

        /** IN6: shared pool that prepares instance builds off the world threads (daemon; plans are pure data). */
        private val INSTANCE_BUILDER =
            java.util.concurrent.Executors
                .newFixedThreadPool(2) { r -> Thread(r, "instance-builder").apply { isDaemon = true } }

        /** Player indices 1..2047 (brief §5.2, §7). */
        const val MAX_PLAYERS = 2048

        /** 600 ms game tick (OSRS Wiki "Game tick"). */
        const val TICK_NANOS = 600_000_000L
        private const val NANOS_PER_MS = 1_000_000L
        private const val MISSED_LOG_SAMPLE = 100L
        private const val WALK_STEPS = 1
        private const val RUN_STEPS = 2

        private const val ZONE_MASK = 7

        private const val MS_PER_SECOND = 1000L
        private const val TICK_MS = 600L

        /** Autosave interval per player (5 minutes), staggered by slot. */
        const val AUTOSAVE_TICKS = 500L

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

        /** Highest IF_SUBOP sub-option index accepted from the client (0-based sub-options, hostile input). */
        private const val MAX_SUBOP = 31
        private const val COMPONENT_SHIFT = 16

        /** Saved chat filters: public << 8 | private << 4 | trade. */
        private const val CHAT_FILTER_KEY = "chat.filters"
        private const val CHAT_FILTER_BITS = 4
        private const val CHAT_FILTER_MAX = 3

        /** Public chat line limit: the client's chatbox input is capped at 80 characters. */
        const val MAX_CHAT_LENGTH = 80

        /** IF_SCRIPTTRIGGER payloads larger than this are rejected (a search string is < 100 bytes). */
        const val MAX_TRIGGER_BYTES = 512

        /** G7: report rule ids fit the client's list (CS 5095; well under 64). */
        const val MAX_REPORT_RULE = 64

        /** Display names are at most 12 characters (OSRS Wiki "Display name"). */
        private const val MAX_NAME_LENGTH = 12

        /** Pause-button clicks reach content as button op 1 (they carry no op). */
        private const val PAUSE_BUTTON_OP = 1

        /** Public chat type for friends-chat lines (RSProt `MessagePublic` chat types). */
        private const val CHAT_TYPE_FRIENDS_CHANNEL = 2
        private const val CHAT_TYPE_CLAN = 3
        private const val CHAT_TYPE_GUEST_CLAN = 4

        /** Friends chat ranks a client may set: -1 (unranked) .. 6 (general); others carry 0. */
        private val CHANNEL_RANKS = -1..6
        private const val MUTED_MESSAGE = "You are muted and cannot talk."
        private const val NO_HUB_MESSAGE = "Friends and private messages need the hub."

        /** Player/NPC view distance in tiles (RSProt player/NPC info default view distance 15). */
        private const val VIEW_DISTANCE = 15

        /** Tile coordinate within its 8×8 zone. */
        private const val IN_ZONE_MASK = 7

        /** Cache inv `bank` (95). */
        private const val BANK_INV = 95

        /** Invalid-input budget (ours, DECISIONS D-022): 50 per 100 ticks before disconnect. */
        private const val INVALID_INPUT_LIMIT = 50
        private const val INVALID_INPUT_WINDOW = 100L
    }
}

/** The chatbox item search's answer (RESUME_P_OBJDIALOG); obj -1 = cancelled. Passed to content as a dialog answer. */
data class ObjDialogAnswer(val obj: Int)

/** The interface slot a targeted interaction uses: an item ([obj] in [sub] of [component]) or a spell component. */
data class UsedItem(val component: Int, val sub: Int, val obj: Int)

/** A pause-button click (RESUME_PAUSEBUTTON) a waiting dialogue script receives: [sub] is the chosen option slot. */
data class PauseInput(val component: Int, val sub: Int)

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

/** Movement and world interactions (what an observer may not do). */
private fun ClientCommand.isWorldAction(): Boolean =
    this is ClientCommand.MoveClick || this is ClientCommand.OpLoc || this is ClientCommand.OpNpc ||
        this is ClientCommand.OpPlayer || this is ClientCommand.OpObj || this is ClientCommand.OpLocT ||
        this is ClientCommand.OpObjT || this is ClientCommand.OpNpcT || this is ClientCommand.OpPlayerT ||
        this is ClientCommand.OpWorldEntity
