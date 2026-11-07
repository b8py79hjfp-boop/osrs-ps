package tp.engine.entity

import tp.engine.World
import tp.engine.inv.Inventory
import tp.engine.script.AreaQueue
import tp.engine.script.PlayerQueue
import tp.engine.script.PlayerTimers
import tp.engine.script.ProtectedAccess
import tp.engine.stat.PlayerStats
import tp.engine.vars.PlayerVars
import tp.engine.zone.ZoneSync
import tp.protocol.api.AvatarRef
import tp.protocol.api.AvatarState
import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage
import tp.protocol.api.Visual
import tp.util.CoordGrid
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/** A logged-in player. World-thread only. */
class Player internal constructor(
    world: World,
    /** Player-info index, 1..2047. */
    val slot: Int,
    val username: String,
    val session: ClientSession,
    spawn: CoordGrid,
    /** Varps/varbits, sent at the end of the tick. */
    val vars: PlayerVars,
) : Entity(world, spawn) {
    val stats = PlayerStats()

    /** The item or spell slot of the current targeted interaction (use-on), or null. */
    var usedItem: tp.engine.UsedItem? = null

    /** Persisted content state outside varps (fee paid flags, cooldown ticks): saved as the `attrs` object. */
    val saved: HashMap<String, Long> = HashMap()

    /** Inventories by inv id; created through [World.inventory], mutated only by [World.transaction]. */
    internal val invs = HashMap<Int, Inventory>()

    internal val zoneSync = ZoneSync()

    /** Hub account id (0 without an account authority). */
    var accountId: Long = 0
        internal set

    /** Staff level (0 player, 1 moderator, 2 admin): permissions as data (brief §12). */
    var staffLevel: Int = 0
        internal set

    /** Muted until (epoch ms); 0 = not muted, negative = permanently (hub moderation; set live by the world node). */
    var mutedUntil: Long = 0

    /** The player's clan and their rank in it, and the clan they are a guest in (set live by the world node). */
    var clanName: String? = null
    var clanRank: Int = 0
    var guestClanName: String? = null

    override var hitpoints: Int
        get() = stats.level(PlayerStats.HITPOINTS)
        set(value) {
            stats.setLevel(PlayerStats.HITPOINTS, value.coerceAtLeast(0))
        }

    override val maxHitpoints: Int get() = stats.baseLevel(PlayerStats.HITPOINTS)

    override fun visual(visual: Visual) {
        session.visual(visual)
    }

    override val avatarRef: AvatarRef = AvatarRef.OfPlayer(slot)

    /** Run energy 0..10000 (client shows /100). */
    var runEnergy: Int = MAX_RUN_ENERGY
        set(value) {
            val v = value.coerceIn(0, MAX_RUN_ENERGY)
            if (v != field) runEnergyDirty = true
            field = v
        }
    private var runEnergyDirty = true

    override val size: Int get() = 1

    /** Persistent run toggle (run orb + energy arrive with stats). */
    var runMode: Boolean = false
        internal set

    /** Whether the current route was clicked with ctrl (inverts [runMode] for that route; movement.md M5). */
    internal var routeCtrl = false

    val queue = PlayerQueue(this)
    val timers = PlayerTimers(this)
    val areaQueue = AreaQueue(this)

    /** Open modal interface id, or -1. Interfaces system owns opening; queues/interactions read it. */
    var modal: Int = -1
        internal set

    val hasModal: Boolean get() = modal != -1

    /** A script waiting for dialogue/pause-button input. */
    internal var inputWait: Continuation<Any>? = null

    /** South-west tile of the current 104x104 build area (client map region). */
    internal var buildArea: CoordGrid = BuildArea.originFor(spawn)

    /** Deck zone sync of the world entities in view (world-entities.md WE6). */
    internal val deckSync = tp.engine.zone.DeckSync()

    /** Instance id and version of the last region rebuild sent, or -1 for a normal build area (instances.md IN4). */
    internal var regionStamp: Long = -1

    /** Client window mode (1 fixed, 2 resizable); content picks the toplevel from it. */
    var windowMode: Int = 1

    internal var invalidInputs = 0
    internal var invalidWindowEnd = 0L

    internal val isRunningNow: Boolean get() = runMode != routeCtrl

    /** Closing the modal removes weak scripts (Q3, Q7) and abandons any input wait. */
    fun closeModal() {
        if (modal != -1) {
            world.interfaces.closeModal(this)
            modal = -1
        }
        inputWait = null
        queue.clearWeak()
    }

    /** Resume a script waiting in [tp.engine.script.ScriptScope.awaitInput]; false if none was waiting. */
    fun resumeInput(value: Any): Boolean {
        val wait = inputWait ?: return false
        inputWait = null
        wait.resume(value)
        return true
    }

    /** Runs [block] with protected access if the player may be mutated now (brief §5.4); returns whether it ran. */
    fun withProtectedAccess(block: ProtectedAccess.() -> Unit): Boolean {
        if (isDelayed || hasModal) return false
        ProtectedAccess(this).block()
        return true
    }

    /** Info pass: vars, stats, inventories and run energy changed this tick go out once. */
    internal fun flushState() {
        vars.flush(session)
        stats.flush(session)
        for (inv in invs.values) inv.flush(session)
        if (runEnergyDirty) {
            runEnergyDirty = false
            session.send(ServerMessage.RunEnergy(runEnergy))
        }
    }

    internal fun avatarState(): AvatarState =
        AvatarState(coord.level, coord.x, coord.z, stepsThisTick, teleportedThisTick, isRunningNow)

    override fun toString(): String = "Player($slot, $username, $coord)"

    companion object {
        /** Full run energy: 100.00% (client shows energy / 100). */
        const val MAX_RUN_ENERGY = 10_000
    }
}

/** Build area (map region) rules. Source: RS Mod `BuildAreaUtils` (104-tile area, rebuild within 2 zones of the edge). */
internal object BuildArea {
    const val SIZE = 104
    private const val ZONE = 8
    private const val REBUILD_BOUNDARY = ZONE * 2
    private const val VIEW_RADIUS_ZONES = SIZE / REBUILD_BOUNDARY

    fun originFor(coord: CoordGrid): CoordGrid {
        val zoneX = (coord.x shr 3) - VIEW_RADIUS_ZONES
        val zoneZ = (coord.z shr 3) - VIEW_RADIUS_ZONES
        return CoordGrid(maxOf(0, zoneX * ZONE), maxOf(0, zoneZ * ZONE), coord.level)
    }

    fun needsRebuild(coord: CoordGrid, origin: CoordGrid): Boolean {
        val dx = coord.x - origin.x
        val dz = coord.z - origin.z
        return dx < REBUILD_BOUNDARY || dz < REBUILD_BOUNDARY || dx >= SIZE - REBUILD_BOUNDARY || dz >= SIZE - REBUILD_BOUNDARY
    }
}
