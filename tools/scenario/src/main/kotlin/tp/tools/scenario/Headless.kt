package tp.tools.scenario

import tp.protocol.api.AvatarState
import tp.protocol.api.ClientCommand
import tp.protocol.api.ClientSession
import tp.protocol.api.GameNetwork
import tp.protocol.api.LoginGate
import tp.protocol.api.ServerMessage
import tp.protocol.api.Visual

/** A network with no sockets: the world's info pass runs, nothing is encoded. */
class HeadlessNetwork : GameNetwork {
    /** Tick source for [npcVisuals] (set by the scenario). */
    var clock: () -> Long = { 0L }

    /** Every NPC visual with the tick it happened on: (tick, npc index, visual). */
    val npcVisuals: MutableList<Triple<Long, Int, Visual>> = ArrayList()

    override fun npcVisual(index: Int, visual: Visual) {
        npcVisuals += Triple(clock(), index, visual)
    }

    override fun start(loginGate: LoginGate) = Unit

    override fun buildInfos() = Unit

    override fun shutdown() = Unit
}

/**
 * A scripted client session: commands queued with [input] are delivered in the next input phase (at most
 * [INPUT_CAP] per tick, like RSProt), and every outgoing message is kept in [sent].
 */
class HeadlessSession : ClientSession {
    private val inbox = ArrayDeque<ClientCommand>()
    private var closed = false
    private var closeAfterInput = false

    val sent: MutableList<ServerMessage> = ArrayList()

    /** Tick source for [visuals] (set by the scenario). */
    var clock: () -> Long = { 0L }

    /** Visuals on this player's avatar with the tick they happened on. */
    val visuals: MutableList<Pair<Long, Visual>> = ArrayList()

    override fun visual(visual: Visual) {
        visuals += clock() to visual
    }

    /** Last avatar state pushed in the info pass. */
    var avatar: AvatarState? = null
        private set

    override val isOpen: Boolean get() = !closed

    fun input(command: ClientCommand) {
        inbox += command
    }

    /** Disconnect after this tick's input is read (replay: logout recorded in that tick's post-tick). */
    fun disconnectAfterInput() {
        closeAfterInput = true
    }

    override fun pollCommands(sink: (ClientCommand) -> Unit) {
        var n = 0
        while (n < INPUT_CAP && inbox.isNotEmpty()) {
            sink(inbox.removeFirst())
            n++
        }
        if (closeAfterInput) closed = true
    }

    override fun beginGame(state: AvatarState) {
        avatar = state
    }

    override fun send(message: ServerMessage) {
        sent += message
    }

    override fun syncAvatar(coord: AvatarState) {
        avatar = coord
    }

    override fun flushInfos() = Unit

    override fun close() {
        closed = true
    }

    /** Chatbox lines received so far. */
    val messages: List<String> get() = sent.filterIsInstance<ServerMessage.GameMessage>().map { it.text }

    companion object {
        /** OSRS processes at most 10 user packets per player per tick (Henke; tick.md "Input cap"). */
        const val INPUT_CAP = 10
    }
}
