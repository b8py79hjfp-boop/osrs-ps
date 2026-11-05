package tp.protocol.api

/** Decoded client input, revision-independent. Every field is untrusted until the engine validates it (brief §12). */
sealed interface ClientCommand {
    /** Walk-here click on the game scene or minimap. [ctrl] = held run modifier (inverts run mode for this route). */
    data class MoveClick(val x: Int, val z: Int, val ctrl: Boolean, val minimap: Boolean) : ClientCommand

    /** `::command` typed in the chatbox (dev commands). */
    data class Cheat(val text: String) : ClientCommand

    /** Client finished loading the map after a rebuild. */
    data object MapBuildComplete : ClientCommand

    /** Loc op (1-based option). */
    data class OpLoc(val option: Int, val id: Int, val x: Int, val z: Int, val ctrl: Boolean) : ClientCommand

    /** NPC op by NPC-info index. */
    data class OpNpc(val option: Int, val index: Int, val ctrl: Boolean) : ClientCommand

    /** Player op by player index (option 1..8). */
    data class OpPlayer(val option: Int, val index: Int, val ctrl: Boolean) : ClientCommand

    /** Ground item op. */
    data class OpObj(val option: Int, val id: Int, val x: Int, val z: Int, val ctrl: Boolean) : ClientCommand

    /** IF3 button / inventory slot op. [component] is packed `iface << 16 | comp`; [sub] the slot, [obj] the item. */
    data class Button(val component: Int, val option: Int, val sub: Int, val obj: Int) : ClientCommand

    /** Public chat line (already Huffman-decoded). */
    data class PublicChat(val text: String, val colour: Int, val effect: Int, val type: Int) : ClientCommand

    /** "Click here to continue" / pause button. */
    data class ResumePause(val component: Int, val sub: Int) : ClientCommand

    /** Client closed its modal (Escape / close button). */
    data object CloseModal : ClientCommand

    /** Client window mode changed (1 fixed, 2 resizable). */
    data class WindowStatus(val mode: Int, val width: Int, val height: Int) : ClientCommand
}

/** Outgoing messages, revision-independent. Info packets and rebuilds are handled by the session itself. */
sealed interface ServerMessage {
    /** Chatbox game message. */
    data class GameMessage(val text: String) : ServerMessage

    /** Root (toplevel) interface. */
    data class OpenTop(val interfaceId: Int) : ServerMessage

    /** Opens [interfaceId] inside the parent component [destination] (packed `iface << 16 | comp`). */
    data class OpenSub(val destination: Int, val interfaceId: Int, val overlay: Boolean) : ServerMessage

    /** Closes whatever is open in [destination] (packed component). */
    data class CloseSub(val destination: Int) : ServerMessage

    /** Allowed ops/events on a component's slot range (IF_SETEVENTS). */
    data class InterfaceEvents(val component: Int, val start: Int, val end: Int, val events: Long) : ServerMessage

    /** Sets a client varp. */
    data class Varp(val id: Int, val value: Int) : ServerMessage

    /** Zeroes every client varp (sent at login before transmitting server values). */
    data object VarpReset : ServerMessage

    /**
     * Inventory contents for [invId] (the client binds inventories to components itself, by id). If [slots] is null this
     * is a full update of [objs]/[counts] (index = slot); otherwise a partial update of only those slots. Empty: obj -1.
     */
    class Inventory(
        val invId: Int,
        val objs: IntArray,
        val counts: IntArray,
        val slots: IntArray?,
    ) : ServerMessage

    /** Skill level/xp (stat id per the client's stat order). */
    data class Stat(val stat: Int, val level: Int, val baseLevel: Int, val xp: Int) : ServerMessage

    /** Run energy, 0..10000 (client shows /100). */
    data class RunEnergy(val energy: Int) : ServerMessage

    /** Runs client script [id] with [args] (Int or String). */
    data class ClientScript(val id: Int, val args: List<Any>) : ServerMessage

    /** Right-click option [slot] (1..8) on other players. */
    data class PlayerOption(val slot: Int, val text: String?, val top: Boolean) : ServerMessage

    /** Chat filter settings (public, trade). */
    data class ChatFilters(val publicFilter: Int, val tradeFilter: Int) : ServerMessage

    /** Map rebuild centred on the player's current position (sent when the build area changes). */
    data class Rebuild(val x: Int, val z: Int) : ServerMessage

    /** Zone header (full): the client clears the zone at ([dx], [dz]) from the build-area origin, then the following ops refill it. */
    data class ZoneReset(val dx: Int, val dz: Int, val level: Int) : ServerMessage

    /** Zone header (partial): the following zone ops apply to the zone at ([dx], [dz]) from the build-area origin. */
    data class ZoneUpdate(val dx: Int, val dz: Int, val level: Int) : ServerMessage

    /** Ground obj appears at ([x], [z]) inside the current zone (0..7). */
    data class ObjAdd(val x: Int, val z: Int, val id: Int, val count: Int) : ServerMessage

    /** Ground obj removed at ([x], [z]) inside the current zone. */
    data class ObjDel(val x: Int, val z: Int, val id: Int, val count: Int) : ServerMessage

    data object Logout : ServerMessage
}
