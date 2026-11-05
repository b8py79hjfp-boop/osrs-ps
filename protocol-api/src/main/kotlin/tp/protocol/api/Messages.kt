package tp.protocol.api

/** Decoded client input, revision-independent. Grows with each phase; Phase 2 needs movement only. */
sealed interface ClientCommand {
    /** Walk-here click on the game scene or minimap. [ctrl] = held run modifier (inverts run mode for this route). */
    data class MoveClick(val x: Int, val z: Int, val ctrl: Boolean, val minimap: Boolean) : ClientCommand

    /** `::command` typed in the chatbox (dev commands). */
    data class Cheat(val text: String) : ClientCommand

    /** Client finished loading the map after a rebuild. */
    data object MapBuildComplete : ClientCommand
}

/** Outgoing messages, revision-independent. Info packets and rebuilds are handled by the session itself. */
sealed interface ServerMessage {
    /** Chatbox game message. */
    data class GameMessage(val text: String) : ServerMessage

    /** Root (toplevel) interface. */
    data class OpenTop(val interfaceId: Int) : ServerMessage

    /** Opens [interfaceId] inside the parent component [destination] (packed `iface << 16 | comp`). */
    data class OpenSub(val destination: Int, val interfaceId: Int, val overlay: Boolean) : ServerMessage

    /** Sets a client varp. */
    data class Varp(val id: Int, val value: Int) : ServerMessage

    /** Map rebuild centred on the player's current position (sent when the build area changes). */
    data class Rebuild(val x: Int, val z: Int) : ServerMessage

    data object Logout : ServerMessage
}
