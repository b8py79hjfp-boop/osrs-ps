package tp.content.quests

import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatNpcAs
import tp.plugin.chatPlayer
import tp.plugin.mesbox
import tp.plugin.objbox
import tp.symbols.Invs

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
