package tp.protocol.api

/**
 * The engine's view of the network. Implemented per revision (`protocol/osrs-<rev>`); nothing here names a packet
 * opcode or a library type, so a revision bump is a module swap (brief §4.3).
 *
 * Threading: [GameNetwork] methods and every [ClientSession] method are called on the world thread only, except
 * [LoginGate.onLogin], which the network calls from its own threads.
 */
interface GameNetwork {
    /** Starts listeners (game + JS5 on the game port). Must be called on the world thread (RSProt binds it). */
    fun start(loginGate: LoginGate)

    /**
     * Info pass, step 1 (brief §5.3 phase 6): world state is frozen; build every player's info packets in parallel.
     * Call after each session's [ClientSession.syncAvatar].
     */
    fun buildInfos()

    /** An NPC entered the world at [index] (NPC-info slot). World thread. */
    fun npcAdded(index: Int, type: Int, level: Int, x: Int, z: Int) {}

    fun npcRemoved(index: Int) {}

    /** Info pass: the NPC's tile this tick. A one-tile change is a walk step; anything else a teleport. */
    fun npcSync(index: Int, level: Int, x: Int, z: Int) {}

    /** A visual on NPC [index] this tick. */
    fun npcVisual(index: Int, visual: Visual) {}

    /** World entity [spec] (a boat) appeared in the world (docs/mechanics/world-entities.md WE1). */
    fun worldEntityAdded(spec: WorldEntitySpec) {}

    /** World entity [index] left the world. */
    fun worldEntityRemoved(index: Int) {}

    /** World entity [index] moved to fine root coordinates ([fineX], [fineZ]) facing [angle] (WE3). */
    fun worldEntityMoved(index: Int, level: Int, fineX: Int, fineZ: Int, angle: Int, teleport: Boolean) {}

    /** End of the info pass, after every session flushed (release per-tick shared buffers). */
    fun afterFlush() {}

    fun shutdown()
}

/** Login decisions. The world queues requests and answers them in its post-tick phase (bounded admissions). */
fun interface LoginGate {
    fun onLogin(request: LoginRequest)
}

/** One decoded, PoW- and RSA-verified login. Answer exactly once via [accept] or [reject]. */
interface LoginRequest {
    val username: String

    /** Archive CRCs matched our master index (client up to date). Checked by the protocol layer. */
    val crcValid: Boolean

    /** The password from the login block (empty for token logins). Never logged. */
    val password: String

    /** Client IP address (the real one behind a PROXY-protocol proxy). */
    val address: String

    /**
     * The login block's 24-byte machine id as lower-case hex, or empty when the client sent none (all zero). Device
     * bans and alt checks key on it (commands brief §6, D-088).
     */
    val device: String get() = ""

    /** The client's window was resizable at login (login block flag); picks the gameframe (gameframe-settings.md G8). */
    val resizable: Boolean get() = false

    /** Called on the world thread. Returns the session bound to player slot [slot] (1..2047). */
    fun accept(slot: Int, details: LoginDetails): ClientSession

    fun reject(reason: LoginRejection)
}

/** [staffModLevel] and [playerMod] are the login response's staff fields (tp.util.Rank, commands brief §1). */
data class LoginDetails(val staffModLevel: Int = 0, val member: Boolean = true, val playerMod: Boolean = false)

enum class LoginRejection {
    INVALID_CREDENTIALS,
    ALREADY_ONLINE,
    WORLD_FULL,
    OUT_OF_DATE,
    LOGIN_LIMIT_EXCEEDED,
    UPDATE_IN_PROGRESS,
    SERVER_ERROR,
    BANNED,
    TOO_MANY_ATTEMPTS,

    /** The previous world's logout save isn't durable yet (world hop too fast). */
    HOP_BLOCKED,
}

/** Per-player connection. All calls on the world thread. */
interface ClientSession {
    val isOpen: Boolean

    /**
     * Client input phase (brief §5.3 phase 2): delivers this tick's queued commands in arrival order. The protocol layer
     * already caps them at the OSRS limit of 10 user packets per tick.
     */
    fun pollCommands(sink: (ClientCommand) -> Unit)

    /** First message after an accepted login: the initial map build (rev 242 carries player-info init in it). */
    fun beginGame(state: AvatarState)

    fun send(message: ServerMessage)

    /**
     * Decodes a [ClientCommand.ScriptTrigger] payload against [types] (`i` int, `s` string, `I` int array, `S` string
     * array): Int / String / IntArray / Array<String>, or null if the bytes don't match the signature exactly.
     */
    fun decodeScriptArgs(payload: ByteArray, types: String): List<Any>? = null

    /**
     * Info pass, step 0: push this player's avatar state for the tick. [moveSteps] is 0 (none or teleport),
     * 1 (walk) or 2 (run). [teleported] marks a jump rather than a step.
     */
    fun syncAvatar(coord: AvatarState)

    /** Public chat from this player's avatar this tick (player info extended info); [modIcon] 0 none (tp.util.Rank.crown). */
    fun publicChat(text: String, colour: Int, effects: Int, modIcon: Int = 0) {}

    /** A visual on this player's avatar this tick (seen by everyone who sees the avatar). */
    fun visual(visual: Visual) {}

    /** This player's look changed (equipment, icons, combat level). */
    fun appearance(appearance: Appearance) {}

    /** Info pass, step 2: queue this player's info packets (after [GameNetwork.buildInfos]) and flush the session. */
    fun flushInfos()

    fun close()
}

/** What the info protocols need about one player at the end of a tick. */
data class AvatarState(
    val level: Int,
    val x: Int,
    val z: Int,
    val moveSteps: Int,
    val teleported: Boolean,
    val running: Boolean,
    /** Hidden from every other player (an observer: raid spectators). */
    val hidden: Boolean = false,
)

/**
 * A world entity as the client sees it (RSProt `WorldEntityAvatarFactory.alloc`, docs/mechanics/world-entities.md):
 * [index] 1..4094, config [id] (archive 2 group 72), [ownerIndex] (> 0 a player, < 0 an NPC, 0 none), size in zones,
 * the south-west zone of its instance land, the levels that belong to it, its pivot in the root world in fine units
 * (128 per tile), the root level it renders on, the level activity happens on, its [angle] (0 south, 512 west, 1024
 * north, 1536 east) and its instance's reference zones: [sizeX] × [sizeZ] × 4 levels, level-major then x then z, each
 * packed like `ServerMessage.RebuildRegion` (-1 = none).
 */
class WorldEntitySpec(
    val index: Int,
    val id: Int,
    val ownerIndex: Int,
    val sizeX: Int,
    val sizeZ: Int,
    val southWestZoneX: Int,
    val southWestZoneZ: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val fineX: Int,
    val fineZ: Int,
    val projectedLevel: Int,
    val activeLevel: Int,
    val angle: Int,
    val zones: IntArray,
)
