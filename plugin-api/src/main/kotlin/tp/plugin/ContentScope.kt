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

    /** Runs when modal [interfaceId] closes for a player, however it closed (decline, walking away, logout). */
    public fun onModalClosed(interfaceId: Int, handler: PlayerRef.() -> Unit) {
        handlers.modalClosed.getOrPut(interfaceId) { ArrayList() } += handler
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

    /**
     * Operable loc interaction for every loc type whose cache name is [name] and whose op number [text] reads (e.g.
     * "Bank booth" / "Bank"; both compared ignoring case). Resolved once at world start; an id-specific [onOpLoc] wins.
     */
    public fun onOpLocNamed(name: String, text: String, script: suspend PlayerScript.(LocRef) -> Unit) {
        handlers.put(handlers.opLocNamed, namedKey(name, text), script, "oploc '$name'/'$text'")
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

    /**
     * NPC op by cache name and op text (e.g. "Banker" / "Talk-to"), for NPC types without an id-specific handler. Wins
     * over [onOpNpcOption]. Resolved once at world start.
     */
    public fun onOpNpcNamed(name: String, text: String, script: suspend PlayerScript.(NpcRef) -> Unit) {
        handlers.put(handlers.opNpcNamed, namedKey(name, text), script, "opnpc '$name'/'$text'")
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

    /** A drag that starts on [fromComponent] (any target; the handler checks [ButtonDrag.toComponent]). */
    public fun onButtonDrag(fromComponent: Int, handler: PlayerRef.(ButtonDrag) -> Unit) {
        handlers.put(handlers.drags, fromComponent, handler, "drag $fromComponent")
    }

    /**
     * IF3 button on [component] (packed, `tp.symbols.Components`); runs only while its interface is open. [op] 0 = any
     * op without its own handler.
     */
    public fun onButton(component: Int, op: Int = 0, handler: PlayerRef.(ButtonClick) -> Unit) {
        handlers.put(handlers.buttons, (component.toLong() shl BUTTON_OP_BITS) or op.toLong(), handler, "button $component/$op")
    }

    /**
     * Backpack item action whose cache iop text is [text] (e.g. "Break", "Rub"; compared ignoring case), for every obj.
     * One owner per text: [handler] decides whether the clicked obj is its own and ignores it otherwise. It runs instead
     * of the generic backpack buttons registered with [onButton], so a text here must not be one of theirs ("Eat",
     * "Drink", "Wield", "Wear", "Equip", "Empty", "Drop", "Destroy"). The click is not checked against the backpack:
     * the handler verifies that [ButtonClick.slot] holds [ButtonClick.obj].
     */
    public fun onItemOp(text: String, handler: PlayerRef.(ButtonClick) -> Unit) {
        handlers.put(handlers.itemOps, text.lowercase(), handler, "item op '$text'")
    }

    private fun key(id: Int, op: Int): Int = (id shl OP_BITS) or op

    private fun namedKey(name: String, text: String): String = Handlers.namedKey(name, text)

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
    val modalClosed = HashMap<Int, MutableList<PlayerRef.() -> Unit>>()
    var npcHunt: (NpcRef.() -> Unit)? = null
    val commands = HashMap<String, PlayerRef.(List<String>) -> Unit>()
    val opLoc = HashMap<Int, suspend PlayerScript.(LocRef) -> Unit>()
    val opLocNamed = HashMap<String, suspend PlayerScript.(LocRef) -> Unit>()
    val opNpcNamed = HashMap<String, suspend PlayerScript.(NpcRef) -> Unit>()
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
    val drags = HashMap<Int, PlayerRef.(ButtonDrag) -> Unit>()
    val itemOps = HashMap<String, PlayerRef.(ButtonClick) -> Unit>()

    companion object {
        /** Key of a name + op text handler. */
        fun namedKey(name: String, text: String): String = "${name.lowercase()}|${text.lowercase()}"
    }

    fun <K, V> put(map: MutableMap<K, V>, key: K, value: V, what: String) {
        check(map.put(key, value) == null) { "duplicate handler: $what" }
    }
}
