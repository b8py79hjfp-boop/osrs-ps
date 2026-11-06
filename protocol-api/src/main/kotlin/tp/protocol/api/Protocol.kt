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

    /** Called on the world thread. Returns the session bound to player slot [slot] (1..2047). */
    fun accept(slot: Int, details: LoginDetails): ClientSession

    fun reject(reason: LoginRejection)
}

data class LoginDetails(val staffModLevel: Int = 0, val member: Boolean = true)

enum class LoginRejection {
    INVALID_CREDENTIALS,
    ALREADY_ONLINE,
    WORLD_FULL,
    OUT_OF_DATE,
    LOGIN_LIMIT_EXCEEDED,
    UPDATE_IN_PROGRESS,
    SERVER_ERROR,
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
     * Info pass, step 0: push this player's avatar state for the tick. [moveSteps] is 0 (none or teleport),
     * 1 (walk) or 2 (run). [teleported] marks a jump rather than a step.
     */
    fun syncAvatar(coord: AvatarState)

    /** Public chat from this player's avatar this tick (player info extended info). */
    fun publicChat(text: String, colour: Int, effects: Int) {}

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
)
