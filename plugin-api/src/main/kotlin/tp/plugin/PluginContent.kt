package tp.plugin

import io.github.oshai.kotlinlogging.KotlinLogging
import tp.engine.ContentHandlers
import tp.engine.World
import tp.engine.entity.Player
import tp.engine.interact.InteractionTarget
import tp.engine.script.Script
import java.util.ServiceLoader
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

private val logger = KotlinLogging.logger {}

/** Packed component id split (`iface << 16 | comp`). */
private const val COMPONENT_SHIFT = 16
private const val COMPONENT_MASK = 0xFFFF

/**
 * Compiles every registered plugin into the engine's [ContentHandlers]. Built once at boot; lookups are hash maps,
 * no reflection after boot (brief §6).
 */
public class PluginContent private constructor(private val h: Handlers) : ContentHandlers {
    private val refs = HashMap<Player, PlayerRef>()

    /** Dialog callbacks opened from button handlers (world thread only). */
    internal val pendingDialogs = HashMap<Player, PlayerRef.(Any) -> Unit>()

    private fun ref(player: Player): PlayerRef = refs.getOrPut(player) { PlayerRef(player) }

    override fun onWorldStart(world: World) {
        this.world = world
        val ref = WorldRef(world)
        for (handler in h.worldStart) ref.handler()
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
    }

    override fun onCommand(player: Player, command: String, args: List<String>): Boolean {
        val handler = h.commands[command] ?: return false
        ref(player).handler(args)
        return true
    }

    override fun opLoc(locId: Int, option: Int): Script<Player>? = h.opLoc[key(locId, option)]?.let(::locScript)

    override fun apLoc(locId: Int, option: Int): Script<Player>? = h.apLoc[key(locId, option)]?.let(::locScript)

    override fun opNpc(npcType: Int, option: Int): Script<Player>? =
        (h.opNpc[key(npcType, option)] ?: opText(npcType, option)?.let(h.opNpcByText::get))?.let(::npcScript)

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

    override fun opNpcT(component: Int): Script<Player>? = h.opNpcT[component]?.let(::npcScript)

    override fun apNpcT(component: Int): Script<Player>? = h.apNpcT[component]?.let(::npcScript)

    override fun opObj(objId: Int, option: Int): Script<Player>? =
        (h.opObj[key(objId, option)] ?: h.opObj[key(ContentScope.DEFAULT_ID, option)])?.let(::objScript)

    override fun onButton(player: Player, component: Int, option: Int, slot: Int, obj: Int) {
        val base = component.toLong() shl ContentScope.BUTTON_OP_BITS
        val handler = h.buttons[base or option.toLong()] ?: h.buttons[base]
        if (handler == null) {
            // Input-driven (not per tick): lets a tester report exactly which button has no content yet.
            logger.info {
                "unhandled button ${component ushr COMPONENT_SHIFT}:${component and COMPONENT_MASK} op=$option slot=$slot obj=$obj"
            }
            return
        }
        ref(player).handler(ButtonClick(component, option, slot, obj))
    }

    override fun onNpcSpawn(npc: tp.engine.entity.Npc) {
        if (h.npcSpawn.isEmpty()) return
        val ref = NpcRef(npc)
        for (handler in h.npcSpawn) ref.handler()
    }

    override fun npcHunt(npc: tp.engine.entity.Npc) {
        h.npcHunt?.invoke(NpcRef(npc))
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
