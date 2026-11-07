package tp.plugin

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.engine.ContentHandlers
import tp.engine.World
import tp.engine.entity.Player
import tp.engine.interact.InteractionTarget
import tp.engine.script.Script
import tp.symbols.Components
import tp.symbols.Invs
import java.util.ServiceLoader
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

private val logger = KotlinLogging.logger {}

/** Packed component id split (`iface << 16 | comp`). */
private const val COMPONENT_SHIFT = 16
private const val COMPONENT_MASK = 0xFFFF

/** OSRS's reply to using an item on something that has no use for it. */
private const val NOTHING_INTERESTING = "Nothing interesting happens."

/** Loc types have five ops (cache `LocType` opcodes 30-34). */
private const val LOC_OPS = 5

/**
 * Backpack button ops of iop1..iop5: iop1..iop3 = ops 2..4, iop4 = op 6, iop5 = op 7 (RS Mod `InvOpenScript` events Op2,
 * Op3, Op4, Op6, Op7; same mapping as content/combat `Equipment`). Returns the 0-based iop index, -1 for any other op.
 */
@Suppress("MagicNumber") // button op numbers are the client's
internal fun backpackIopIndex(op: Int): Int =
    when (op) {
        in 2..4 -> op - 2
        6 -> 3
        7 -> 4
        else -> -1
    }

/** A chatbox option menu waiting for an answer: [count] choices, [answer] runs with the 1-based pick. */
internal class PendingChoice(val count: Int, val answer: PlayerRef.(Int) -> Unit)

/**
 * Compiles every registered plugin into the engine's [ContentHandlers]. Built once at boot; lookups are hash maps,
 * no reflection after boot (brief §6).
 */
public class PluginContent private constructor(private val h: Handlers) : ContentHandlers {
    /** This world's content event bus. */
    internal val events: EventBus get() = h.events

    private val refs = HashMap<Player, PlayerRef>()

    /** Dialog callbacks opened from button handlers (world thread only). */
    internal val pendingDialogs = HashMap<Player, PlayerRef.(Any) -> Unit>()

    /** Chatbox option menus waiting for an answer (world thread only), see [PlayerRef.choiceDialog]. */
    internal val pendingChoices = HashMap<Player, PendingChoice>()

    private fun ref(player: Player): PlayerRef = refs.getOrPut(player) { PlayerRef(player) }

    override fun onWorldStart(world: World) {
        this.world = world
        resolveNamed(world)
        val ref = WorldRef(world)
        for (handler in h.worldStart) ref.handler()
    }

    /** Resolved name + op text handlers by (config id, op), built once so a click costs one map lookup. */
    private val opLocResolved = HashMap<Int, suspend PlayerScript.(LocRef) -> Unit>()
    private val opNpcResolved = HashMap<Int, suspend PlayerScript.(NpcRef) -> Unit>()

    private fun resolveNamed(world: World) {
        if (h.opLocNamed.isNotEmpty()) resolveLocs(world)
        if (h.opNpcNamed.isNotEmpty()) {
            for (type in world.data.configs.npcs) {
                for ((index, text) in type.ops.ops.withIndex()) {
                    val handler = text?.let { h.opNpcNamed[Handlers.namedKey(type.name, it)] } ?: continue
                    opNpcResolved[key(type.id, index + 1)] = handler
                }
            }
        }
    }

    /**
     * Name + op text handlers of loc types. A multi-loc (a loc that changes with a varbit) is clicked by its base id, so
     * it also gets the handlers of the types it can change into.
     */
    private fun resolveLocs(world: World) {
        val locs = world.data.configs.locs
        for (type in locs) {
            for ((index, text) in type.ops.ops.withIndex()) {
                val handler = text?.let { h.opLocNamed[Handlers.namedKey(type.name, it)] } ?: continue
                opLocResolved[key(type.id, index + 1)] = handler
            }
        }
        for (type in locs) {
            for (target in type.multiLocs ?: continue) {
                for (op in 1..LOC_OPS) opLocResolved[key(target, op)]?.let { opLocResolved.putIfAbsent(key(type.id, op), it) }
            }
        }
    }

    override fun onLogin(player: Player) {
        val ref = ref(player)
        for (handler in h.login) ref.handler()
        h.events.publish(LoginEvent(ref))
    }

    override fun onLogout(player: Player) {
        val ref = ref(player)
        h.events.publish(LogoutEvent(ref))
        for (handler in h.logout) ref.handler()
        refs.remove(player)
        pendingDialogs.remove(player)
        pendingChoices.remove(player)
    }

    override fun onCommand(player: Player, command: String, args: List<String>): Boolean {
        val handler = h.commands[command] ?: return false
        ref(player).handler(args)
        return true
    }

    override fun opLoc(locId: Int, option: Int): Script<Player>? =
        (h.opLoc[key(locId, option)] ?: opLocResolved[key(locId, option)] ?: h.opLocFallback[key(locId, option)])?.let(::locScript)

    override fun apLoc(locId: Int, option: Int): Script<Player>? = h.apLoc[key(locId, option)]?.let(::locScript)

    override fun opNpc(npcType: Int, option: Int): Script<Player>? =
        (h.opNpc[key(npcType, option)] ?: opNpcResolved[key(npcType, option)] ?: opText(npcType, option)?.let(h.opNpcByText::get))
            ?.let(::npcScript)

    override fun apNpc(npcType: Int, option: Int): Script<Player>? =
        (h.apNpc[key(npcType, option)] ?: opText(npcType, option)?.let(h.apNpcByText::get))?.let(::npcScript)

    private var world: World? = null

    private fun opText(npcType: Int, option: Int): String? =
        world
            ?.data
            ?.configs
            ?.npcs
            ?.get(npcType)
            ?.ops
            ?.ops
            ?.getOrNull(option - 1)
            ?.lowercase()

    override fun opPlayer(option: Int): Script<Player>? = h.opPlayer[option]?.let(::playerScript)

    override fun apPlayer(option: Int): Script<Player>? = h.apPlayer[option]?.let(::playerScript)

    override fun opPlayerT(component: Int): Script<Player>? = h.opPlayerT[component]?.let(::playerScript)

    override fun apPlayerT(component: Int): Script<Player>? = h.apPlayerT[component]?.let(::playerScript)

    override fun opNpcT(component: Int, obj: Int, npcType: Int): Script<Player>? =
        if (component == Components.Inventory.ITEMS) {
            useScript(h.useOnNpc[useKey(obj, npcType)] ?: h.useOnNpc[useKey(-1, npcType)], ::npcScript)
        } else {
            h.opNpcT[component]?.let(::npcScript)
        }

    override fun apNpcT(component: Int, obj: Int, npcType: Int): Script<Player>? =
        if (component == Components.Inventory.ITEMS) null else h.apNpcT[component]?.let(::npcScript)

    override fun opLocT(component: Int, obj: Int, locId: Int): Script<Player>? =
        if (component == Components.Inventory.ITEMS) {
            useScript(h.useOnLoc[useKey(obj, locId)] ?: h.useOnLoc[useKey(-1, locId)], ::locScript)
        } else {
            h.opLocT[component]?.let(::locScript)
        }

    override fun apLocT(component: Int, obj: Int, locId: Int): Script<Player>? = null

    override fun opObjT(component: Int, obj: Int, groundObj: Int): Script<Player>? =
        if (component == Components.Inventory.ITEMS) {
            useScript(h.useOnObj[useKey(obj, groundObj)] ?: h.useOnObj[useKey(-1, groundObj)], ::objScript)
        } else {
            h.opObjT[component]?.let(::objScript)
        }

    override fun apObjT(component: Int, obj: Int, groundObj: Int): Script<Player>? =
        if (component == Components.Inventory.ITEMS) null else h.apObjT[component]?.let(::objScript)

    override fun onButtonT(player: Player, fromComponent: Int, fromSub: Int, fromObj: Int, toComponent: Int, toSub: Int, toObj: Int) {
        val ref = ref(player)
        val event = ItemOnItem(fromComponent, fromSub, fromObj, toComponent, toSub, toObj)
        if (fromComponent != Components.Inventory.ITEMS) {
            h.buttonT[fromComponent]?.let { ref.it(event) }
            return
        }
        // Hostile input (brief §12): both slots must hold what the client says.
        val inv = player.world.inventory(player, Invs.INV)
        val fromOk = fromSub in 0 until inv.size && inv.obj(fromSub) == fromObj
        val toOk = toComponent != Components.Inventory.ITEMS || (toSub in 0 until inv.size && inv.obj(toSub) == toObj && toSub != fromSub)
        if (!fromOk || !toOk) return
        val handler =
            h.useOnItem[useKey(fromObj, toObj)] ?: h.useOnItem[useKey(fromObj, -1)]
                ?: h.useOnItem[useKey(toObj, -1)]?.let { hd -> { e: ItemOnItem -> hd(e.swapped()) } }
        if (handler == null) {
            ref.message(NOTHING_INTERESTING)
            return
        }
        ref.handler(event)
    }

    /** The world's ref for the per-tick world-entity hook (one per world, no allocation per tick). */
    private var worldRef: WorldRef? = null

    override fun onWorldEntityPhase(world: World) {
        val handler = h.worldEntityTick ?: return
        val ref = worldRef?.takeIf { it.w === world } ?: WorldRef(world).also { worldRef = it }
        ref.handler()
    }

    override fun onClanInvite(player: Player, from: String, clanName: String) {
        val handler = h.clanInvite ?: return
        ref(player).handler(from, clanName)
    }

    override fun onHeading(player: Player, heading: Int) {
        val handler = h.heading ?: return
        ref(player).handler(heading)
    }

    override fun onOpWorldEntity(player: Player, index: Int, option: Int) {
        val handler = h.opWorldEntity ?: return
        val entity = player.world.worldEntities.byIndex(index) ?: return
        ref(player).handler(WorldEntityRef(entity, player.world), option)
    }

    override fun onExamine(player: Player, kind: Int, id: Int) {
        val handler = h.examine ?: return
        ref(player).handler(Examined(Examined.Kind.entries[kind], id))
    }

    /** Item use-on: the handler if any (run only while the used slot still holds the item), else the OSRS default line. */
    private fun <T> useScript(
        handler: (suspend PlayerScript.(T) -> Unit)?,
        wrap: (suspend PlayerScript.(T) -> Unit) -> Script<Player>,
    ): Script<Player> {
        val body: suspend PlayerScript.(T) -> Unit = handler ?: { player.message(NOTHING_INTERESTING) }
        val inner = wrap(body)
        return {
            val used = self.usedItem
            val inv = self.world.inventory(self, Invs.INV)
            if (used != null && used.sub in 0 until inv.size && inv.obj(used.sub) == used.obj) inner()
        }
    }

    private fun useKey(a: Int, b: Int): Long = (a.toLong() shl Int.SIZE_BITS) or Integer.toUnsignedLong(b)

    override fun opObj(objId: Int, option: Int): Script<Player>? =
        (h.opObj[key(objId, option)] ?: h.opObj[key(ContentScope.DEFAULT_ID, option)])?.let(::objScript)

    override fun onButton(player: Player, component: Int, option: Int, slot: Int, obj: Int, subop: Int) {
        if (component == Components.Chatmenu.OPTIONS) return answerChoice(player, slot)
        if (component == Components.Inventory.ITEMS && itemOp(player, option, slot, obj, subop)) {
            return
        }
        val base = component.toLong() shl ContentScope.BUTTON_OP_BITS
        val handler = h.buttons[base or option.toLong()] ?: h.buttons[base]
        if (handler == null) {
            // Input-driven (not per tick): lets a tester report exactly which button has no content yet.
            logger.info {
                "unhandled button ${component ushr COMPONENT_SHIFT}:${component and COMPONENT_MASK} op=$option slot=$slot obj=$obj"
            }
            return
        }
        ref(player).handler(ButtonClick(component, option, slot, obj, subop))
    }

    /** Runs the [ContentScope.onItemOp] handler of the iop text behind backpack button op [option]; false if none. */
    private fun itemOp(player: Player, option: Int, slot: Int, obj: Int, subop: Int): Boolean {
        if (h.itemOps.isEmpty() && h.objItemOps.isEmpty()) return false
        val ref = ref(player)
        val index = backpackIopIndex(option)
        val text = (if (index >= 0) ref.objIop(obj, index) else null) ?: return false
        val handler = h.objItemOps[Handlers.namedKey(obj.toString(), text)] ?: h.itemOps[text.lowercase()] ?: return false
        ref.handler(ButtonClick(Components.Inventory.ITEMS, option, slot, obj, subop))
        return true
    }

    /** Answers the open chatbox option menu with 1-based choice [choice] (hostile input: out-of-range is ignored). */
    private fun answerChoice(player: Player, choice: Int) {
        val pending = pendingChoices[player] ?: return
        if (choice !in 1..pending.count) return
        pendingChoices.remove(player)
        val ref = ref(player)
        ref.closeModal()
        pending.answer(ref, choice)
    }

    override fun onModalClosed(player: Player, interfaceId: Int) {
        val list = h.modalClosed[interfaceId] ?: return
        val ref = PlayerRef(player)
        for (handler in list) ref.handler()
    }

    override fun onNpcSpawn(npc: tp.engine.entity.Npc) {
        if (h.npcSpawn.isEmpty()) return
        val ref = NpcRef(npc)
        for (handler in h.npcSpawn) ref.handler()
    }

    override fun npcHunt(npc: tp.engine.entity.Npc) {
        h.npcHunt?.invoke(NpcRef(npc))
    }

    override fun onButtonDrag(player: Player, fromComponent: Int, fromSub: Int, toComponent: Int, toSub: Int) {
        val handler = h.drags[fromComponent] ?: return
        ref(player).handler(ButtonDrag(fromComponent, fromSub, toComponent, toSub))
    }

    override fun onDialogAnswer(player: Player, value: Any) {
        val callback = pendingDialogs.remove(player) ?: return
        ref(player).callback(value)
    }

    private fun key(id: Int, op: Int): Int = (id shl ContentScope.OP_BITS) or op

    private fun locScript(s: suspend PlayerScript.(LocRef) -> Unit): Script<Player> =
        {
            val t = self.interactionTarget as? InteractionTarget.Loc
            if (t != null) run(PlayerScript(this), LocRef(t.id, t.coord, t.shape, t.angle), s)
        }

    private fun npcScript(s: suspend PlayerScript.(NpcRef) -> Unit): Script<Player> =
        {
            val t = self.interactionTarget as? InteractionTarget.OfNpc
            if (t != null) run(PlayerScript(this), NpcRef(t.npc), s)
        }

    private fun objScript(s: suspend PlayerScript.(ObjRef) -> Unit): Script<Player> =
        {
            val t = self.interactionTarget as? InteractionTarget.Obj
            val obj = t?.let { self.world.ground.find(it.coord, it.id, self) }
            if (obj != null) run(PlayerScript(this), ObjRef(obj), s)
        }

    private fun playerScript(s: suspend PlayerScript.(PlayerRef) -> Unit): Script<Player> =
        {
            val t = self.interactionTarget as? InteractionTarget.OfPlayer
            if (t != null) run(PlayerScript(this), ref(t.player), s)
        }

    /**
     * Runs a restricted content script inside the engine script: the restricted lambda is started on the current
     * continuation (no new coroutine, no dispatch), so its suspensions suspend the engine script.
     */
    private suspend fun <T> run(scope: PlayerScript, arg: T, s: suspend PlayerScript.(T) -> Unit) {
        val body: suspend PlayerScript.() -> Unit = { s(arg) }
        suspendCoroutineUninterceptedOrReturn { c -> body.startCoroutineUninterceptedOrReturn(scope, c) }
    }

    public companion object {
        /** Every registry on the classpath's `META-INF/services` index (generated by KSP per content module). */
        public fun load(): PluginContent = of(ServiceLoader.load(ContentRegistry::class.java).flatMap { it.plugins })

        public fun of(plugins: List<ContentPlugin>): PluginContent {
            val handlers = Handlers()
            val scope = ContentScope(handlers)
            for (plugin in plugins) with(plugin) { scope.register() }
            return PluginContent(handlers)
        }
    }

    /** Number of registered handlers, for the boot log. */
    public val summary: String
        get() = "commands=${h.commands.size} buttons=${h.buttons.size} oploc=${h.opLoc.size} opnpc=${h.opNpc.size} login=${h.login.size}"
}
