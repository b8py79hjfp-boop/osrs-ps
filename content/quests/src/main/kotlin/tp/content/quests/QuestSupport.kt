package tp.content.quests

import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatNpcAs
import tp.plugin.chatPlayer
import tp.plugin.mesbox
import tp.plugin.objbox
import tp.symbols.Invs
import tp.util.CoordGrid

// Small helpers shared by the quests' conversations and item steps (docs/mechanics/quests.md QU8).

/** True when the backpack holds at least [count] of [obj]. */
internal fun PlayerRef.has(obj: Int, count: Int = 1): Boolean = inv(Invs.INV).total(obj) >= count

/** Gives [count] of [obj] through an item transaction: false (nothing given) when the backpack has no room. */
internal fun PlayerRef.give(obj: Int, count: Int, reason: String): Boolean = transaction(reason) { add(Invs.INV, obj, count) }

/** Takes [count] of [obj] from the backpack through an item transaction: false (nothing taken) when it is not all there. */
internal fun PlayerRef.take(obj: Int, count: Int, reason: String): Boolean = transaction(reason) { remove(Invs.INV, obj, count) }

/** Gives [count] of [obj], dropping what does not fit at the player's feet (rewards): one item transaction. */
internal fun PlayerRef.giveOrDrop(obj: Int, count: Int, reason: String): Boolean = transaction(reason) { addOrDrop(Invs.INV, obj, count) }

/** A persisted yes/no flag of a quest (`saved` values of the account). */
internal fun PlayerRef.flag(key: String): Boolean = saved(key) == 1L

internal fun PlayerRef.setFlag(key: String, value: Boolean) {
    setSaved(key, if (value) 1L else null)
}

/** The NPC says [lines], one chat box each. */
internal suspend fun PlayerScript.npc(npc: NpcRef, vararg lines: String) {
    for (line in lines) chatNpc(npc, line)
}

/** A speaker who is not the NPC clicked (a cutscene conversation): NPC type [type] shown under [name]. */
internal suspend fun PlayerScript.npcAs(type: Int, name: String, vararg lines: String) {
    for (line in lines) chatNpcAs(type, name, line)
}

/** The player says [lines], one chat box each. */
internal suspend fun PlayerScript.me(vararg lines: String) {
    for (line in lines) chatPlayer(line)
}

/** A plain message box. */
internal suspend fun PlayerScript.box(text: String) = mesbox(text)

/** A message box with an item picture (the Wiki's `{{tbox|pic=...}}`). */
internal suspend fun PlayerScript.itemBox(obj: Int, text: String) = objbox(obj, text)

/** The player's name token `[player name]` of the Wiki transcripts. */
internal val PlayerScript.name: String get() = player.username

/**
 * Moves the player through a wall loc ([LocRef.shape] wall, e.g. a locked or secret door) to the other side. A wall loc of angle
 * 0 / 1 / 2 / 3 sits on the west / north / east / south edge of its tile, so its two sides are its tile and the neighbour across
 * that edge. The cache has no open model for these doors, so the player is teleported instead of walking (OPEN-QUA6).
 */
internal fun PlayerRef.stepAcross(wall: LocRef) {
    val d = wall.coord
    val across =
        when (wall.angle and WALL_ANGLE_MASK) {
            0 -> CoordGrid(d.x - 1, d.z, d.level)
            1 -> CoordGrid(d.x, d.z + 1, d.level)
            2 -> CoordGrid(d.x + 1, d.z, d.level)
            else -> CoordGrid(d.x, d.z - 1, d.level)
        }
    teleport(if (coord == d) across else d)
}

/** Loc angles are 0..3. */
private const val WALL_ANGLE_MASK = 3
