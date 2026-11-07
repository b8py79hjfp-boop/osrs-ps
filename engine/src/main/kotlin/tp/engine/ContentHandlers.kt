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

    /** Interface component (spell) used on a player / NPC: scripts keyed by the packed component. */
    fun opPlayerT(component: Int): Script<Player>? = null

    fun apPlayerT(component: Int): Script<Player>? = null

    fun opNpcT(component: Int, obj: Int = -1, npcType: Int = -1): Script<Player>? = null

    fun apNpcT(component: Int, obj: Int = -1, npcType: Int = -1): Script<Player>? = null

    /** Interface component (an item: [obj] in slot [sub]; or a spell) used on loc [locId]. */
    fun opLocT(component: Int, obj: Int, locId: Int): Script<Player>? = null

    fun apLocT(component: Int, obj: Int, locId: Int): Script<Player>? = null

    /** Interface component used on ground obj [groundObj]. */
    fun opObjT(component: Int, obj: Int, groundObj: Int): Script<Player>? = null

    fun apObjT(component: Int, obj: Int, groundObj: Int): Script<Player>? = null

    /** Examine of a loc (kind 0), NPC type (1) or ground obj (2). */
    fun onExamine(player: Player, kind: Int, id: Int) {}

    /** IF_BUTTONT: slot [fromSub] ([fromObj]) of [fromComponent] used on slot [toSub] ([toObj]) of [toComponent]. */
    fun onButtonT(player: Player, fromComponent: Int, fromSub: Int, fromObj: Int, toComponent: Int, toSub: Int, toObj: Int) {}

    fun apObj(objId: Int, option: Int): Script<Player>? = null

    /** IF button on an open, validated component. */
    fun onButton(player: Player, component: Int, option: Int, slot: Int, obj: Int, subop: Int = -1) {}

    /** A string/count dialog answer that no script was waiting for (a button's pending dialog callback). */
    fun onDialogAnswer(player: Player, value: Any) {}

    /** The player at a boat's helm chose [heading] (0..15; sailing-movement.md). */
    fun onHeading(player: Player, heading: Int) {}

    /** Option [option] (1..5) on world entity [index], validated to exist and be in view (world-entities.md WE8). */
    fun onOpWorldEntity(player: Player, index: Int, option: Int) {}

    /** IF_BUTTOND: slot [fromSub] of [fromComponent] dropped on slot [toSub] of [toComponent] (both interfaces open). */
    fun onButtonDrag(player: Player, fromComponent: Int, fromSub: Int, toComponent: Int, toSub: Int) {}

    /** The modal [interfaceId] was closed (by the player, movement, another modal or content). */
    fun onModalClosed(player: Player, interfaceId: Int) {}

    /** An NPC was added to the world or respawned (content may set its hunt range, boss state). */
    fun onNpcSpawn(npc: tp.engine.entity.Npc) {}

    /** A hunting NPC's turn (aggression.md): it has a hunt range, no interaction and isn't delayed. */
    fun npcHunt(npc: tp.engine.entity.Npc) {}

    /** Inventory item op (iop1..5) on a validated slot. */
    fun onInvOp(player: Player, inv: Int, slot: Int, obj: Int, option: Int) {}

    companion object {
        val NONE: ContentHandlers = object : ContentHandlers {}
    }
}
