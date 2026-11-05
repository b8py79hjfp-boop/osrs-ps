package tp.plugin

/**
 * Registration DSL handed to [ContentPlugin.register]. Handlers are keyed by gameval symbols (`tp.symbols.*`).
 * Registering the same key twice fails at boot: one owner per handler.
 */
public class ContentScope internal constructor(internal val handlers: Handlers) {
    public val events: EventBus get() = handlers.events

    /** Once at world start (spawns). */
    public fun onWorldStart(handler: WorldRef.() -> Unit) {
        handlers.worldStart += handler
    }

    public fun onLogin(handler: PlayerRef.() -> Unit) {
        handlers.login += handler
    }

    public fun onLogout(handler: PlayerRef.() -> Unit) {
        handlers.logout += handler
    }

    /** `::name args` typed in the chatbox. */
    public fun onCommand(name: String, handler: PlayerRef.(args: List<String>) -> Unit) {
        handlers.put(handlers.commands, name.lowercase(), handler, "command ::$name")
    }

    /** Operable loc interaction ([op] 1..5): runs when the player reaches the loc (interactions.md I1). */
    public fun onOpLoc(loc: Int, op: Int, script: suspend PlayerScript.(LocRef) -> Unit) {
        handlers.put(handlers.opLoc, key(loc, op), script, "oploc $loc/$op")
    }

    /** Approachable loc interaction: runs within approach range and line of sight (I2). */
    public fun onApLoc(loc: Int, op: Int, script: suspend PlayerScript.(LocRef) -> Unit) {
        handlers.put(handlers.apLoc, key(loc, op), script, "aploc $loc/$op")
    }

    public fun onOpNpc(npc: Int, op: Int, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.opNpc, key(npc, op), script, "opnpc $npc/$op")
    }

    public fun onApNpc(npc: Int, op: Int, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.apNpc, key(npc, op), script, "apnpc $npc/$op")
    }

    /** Player op slot [op] (1..8, as set with [PlayerRef.playerOp]). */
    public fun onOpPlayer(op: Int, script: suspend PlayerScript.(PlayerRef) -> Unit) {
        handlers.put(handlers.opPlayer, op, script, "opplayer $op")
    }

    public fun onApPlayer(op: Int, script: suspend PlayerScript.(PlayerRef) -> Unit) {
        handlers.put(handlers.apPlayer, op, script, "applayer $op")
    }

    /** Ground obj op [op] (1..5) on [obj]. */
    public fun onOpObj(obj: Int, op: Int, script: suspend PlayerScript.(ObjRef) -> Unit) {
        handlers.put(handlers.opObj, key(obj, op), script, "opobj $obj/$op")
    }

    /** Ground obj op [op] for every obj without its own handler (e.g. Take). */
    public fun onOpObjDefault(op: Int, script: suspend PlayerScript.(ObjRef) -> Unit) {
        handlers.put(handlers.opObj, key(DEFAULT_ID, op), script, "opobj default/$op")
    }

    /** IF3 button on [component] (packed, `tp.symbols.Components`); runs only while its interface is open. */
    public fun onButton(component: Int, handler: PlayerRef.(ButtonClick) -> Unit) {
        handlers.put(handlers.buttons, component, handler, "button $component")
    }

    private fun key(id: Int, op: Int): Int = (id shl OP_BITS) or op

    internal companion object {
        const val OP_BITS = 4

        /** Key id of default handlers (no real config id is negative). */
        const val DEFAULT_ID = -1
    }
}

internal class Handlers {
    val events = EventBus()
    val worldStart = ArrayList<WorldRef.() -> Unit>()
    val login = ArrayList<PlayerRef.() -> Unit>()
    val logout = ArrayList<PlayerRef.() -> Unit>()
    val commands = HashMap<String, PlayerRef.(List<String>) -> Unit>()
    val opLoc = HashMap<Int, suspend PlayerScript.(LocRef) -> Unit>()
    val apLoc = HashMap<Int, suspend PlayerScript.(LocRef) -> Unit>()
    val opNpc = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()
    val apNpc = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()
    val opPlayer = HashMap<Int, suspend PlayerScript.(PlayerRef) -> Unit>()
    val apPlayer = HashMap<Int, suspend PlayerScript.(PlayerRef) -> Unit>()
    val opObj = HashMap<Int, suspend PlayerScript.(ObjRef) -> Unit>()
    val buttons = HashMap<Int, PlayerRef.(ButtonClick) -> Unit>()

    fun <K, V> put(map: MutableMap<K, V>, key: K, value: V, what: String) {
        check(map.put(key, value) == null) { "duplicate handler: $what" }
    }
}
