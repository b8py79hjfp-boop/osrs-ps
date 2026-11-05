package tp.engine

import tp.engine.entity.Player
import tp.engine.script.Script

/**
 * What the engine asks content for. Implemented by the plugin layer (`plugin-api`), which compiles handlers registered
 * by content modules. The engine itself knows no content (brief §1).
 */
interface ContentHandlers {
    /** Once, on the world thread, before the first tick (spawns). */
    fun onWorldStart(world: World) {}

    fun onLogin(player: Player) {}

    fun onLogout(player: Player) {}

    /** `::command` typed in chat. Return false if unknown. */
    fun onCommand(player: Player, command: String, args: List<String>): Boolean = false

    /** Interaction scripts by target type, config id and option (1-based). Null = none registered (engine default). */
    fun opLoc(locId: Int, option: Int): Script<Player>? = null

    fun apLoc(locId: Int, option: Int): Script<Player>? = null

    fun opNpc(npcType: Int, option: Int): Script<Player>? = null

    fun apNpc(npcType: Int, option: Int): Script<Player>? = null

    fun opPlayer(option: Int): Script<Player>? = null

    fun apPlayer(option: Int): Script<Player>? = null

    fun opObj(objId: Int, option: Int): Script<Player>? = null

    fun apObj(objId: Int, option: Int): Script<Player>? = null

    /** IF button on an open, validated component. */
    fun onButton(player: Player, component: Int, option: Int, slot: Int, obj: Int) {}

    /** Inventory item op (iop1..5) on a validated slot. */
    fun onInvOp(player: Player, inv: Int, slot: Int, obj: Int, option: Int) {}

    companion object {
        val NONE: ContentHandlers = object : ContentHandlers {}
    }
}
