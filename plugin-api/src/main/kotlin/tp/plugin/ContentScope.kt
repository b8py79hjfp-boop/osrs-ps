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

    /** Runs for every NPC added to the world (spawn data, content), e.g. to set its hunt range. */
    public fun onNpcSpawn(handler: NpcRef.() -> Unit) {
        handlers.npcSpawn += handler
    }

    /** The hunt turn of an NPC with a hunt range and no interaction (one handler per world: aggression content). */
    public fun onNpcHunt(handler: NpcRef.() -> Unit) {
        check(handlers.npcHunt == null) { "duplicate handler: npc hunt" }
        handlers.npcHunt = handler
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

    /**
     * NPC op by its text (e.g. "Attack") for every NPC type without an id-specific handler for that op; [script] also
     * receives the op number.
     */
    public fun onOpNpcOption(text: String, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.opNpcByText, text.lowercase(), script, "opnpc text '$text'")
    }

    /** Approach variant of [onOpNpcOption] (ranged/magic attacks call `apRange`). */
    public fun onApNpcOption(text: String, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.apNpcByText, text.lowercase(), script, "apnpc text '$text'")
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

    /** Interface component [component] (e.g. a spell, packed) used on a player: op (reach) / ap (approach) scripts. */
    public fun onOpPlayerT(component: Int, script: suspend PlayerScript.(PlayerRef) -> Unit) {
        handlers.put(handlers.opPlayerT, component, script, "opplayert $component")
    }

    public fun onApPlayerT(component: Int, script: suspend PlayerScript.(PlayerRef) -> Unit) {
        handlers.put(handlers.apPlayerT, component, script, "applayert $component")
    }

    public fun onOpNpcT(component: Int, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.opNpcT, component, script, "opnpct $component")
    }

    public fun onApNpcT(component: Int, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.apNpcT, component, script, "apnpct $component")
    }

    /** Ground obj op [op] (1..5) on [obj]. */
    public fun onOpObj(obj: Int, op: Int, script: suspend PlayerScript.(ObjRef) -> Unit) {
        handlers.put(handlers.opObj, key(obj, op), script, "opobj $obj/$op")
    }

    /** Ground obj op [op] for every obj without its own handler (e.g. Take). */
    public fun onOpObjDefault(op: Int, script: suspend PlayerScript.(ObjRef) -> Unit) {
        handlers.put(handlers.opObj, key(DEFAULT_ID, op), script, "opobj default/$op")
    }

    /**
     * IF3 button on [component] (packed, `tp.symbols.Components`); runs only while its interface is open. [op] 0 = any
     * op without its own handler.
     */
    public fun onButton(component: Int, op: Int = 0, handler: PlayerRef.(ButtonClick) -> Unit) {
        handlers.put(handlers.buttons, (component.toLong() shl BUTTON_OP_BITS) or op.toLong(), handler, "button $component/$op")
    }

    private fun key(id: Int, op: Int): Int = (id shl OP_BITS) or op

    internal companion object {
        const val OP_BITS = 4

        /** Key id of default handlers (no real config id is negative). */
        const val DEFAULT_ID = -1
        const val BUTTON_OP_BITS = 8
    }
}

internal class Handlers {
    val events = EventBus()
    val worldStart = ArrayList<WorldRef.() -> Unit>()
    val login = ArrayList<PlayerRef.() -> Unit>()
    val logout = ArrayList<PlayerRef.() -> Unit>()
    val npcSpawn = ArrayList<NpcRef.() -> Unit>()
    var npcHunt: (NpcRef.() -> Unit)? = null
    val commands = HashMap<String, PlayerRef.(List<String>) -> Unit>()
    val opLoc = HashMap<Int, suspend PlayerScript.(LocRef) -> Unit>()
    val apLoc = HashMap<Int, suspend PlayerScript.(LocRef) -> Unit>()
    val opNpcByText = HashMap<String, suspend PlayerScript.(NpcRef) -> Unit>()
    val apNpcByText = HashMap<String, suspend PlayerScript.(NpcRef) -> Unit>()
    val opNpc = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()
    val apNpc = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()
    val opPlayer = HashMap<Int, suspend PlayerScript.(PlayerRef) -> Unit>()
    val apPlayer = HashMap<Int, suspend PlayerScript.(PlayerRef) -> Unit>()
    val opPlayerT = HashMap<Int, suspend PlayerScript.(PlayerRef) -> Unit>()
    val apPlayerT = HashMap<Int, suspend PlayerScript.(PlayerRef) -> Unit>()
    val opNpcT = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()
    val apNpcT = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()
    val opObj = HashMap<Int, suspend PlayerScript.(ObjRef) -> Unit>()
    val buttons = HashMap<Long, PlayerRef.(ButtonClick) -> Unit>()

    fun <K, V> put(map: MutableMap<K, V>, key: K, value: V, what: String) {
        check(map.put(key, value) == null) { "duplicate handler: $what" }
    }
}
