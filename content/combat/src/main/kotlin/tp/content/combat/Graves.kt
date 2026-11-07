package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.util.CoordGrid

/**
 * Graves (docs/mechanics/death.md DE1-DE7, DE10): what a PvM death does not keep goes into the player's grave inventory
 * (`gravestone`, 120 stacks, saved with the player), shown as a grave NPC at the death tile for 1500 ticks of the
 * owner's online time; then the items move to Death's Office storage (`death_permanent`).
 */
internal object Graves {
    /** Grave NPC (Wiki "Gravestone": basic grave 9856). */
    const val GRAVE_NPC = 9856

    /** DE5: 15 minutes. */
    const val DURATION = 1500L

    /** DE3: looting works from up to 7 tiles (with line of sight). */
    const val LOOT_RANGE = 7

    /** DE4: unstackables kept per obj in a grave on a later death. */
    private const val UNSTACKABLE_LIMIT = 28
    private const val TICKS_KEY = "death.grave_ticks"
    private const val COORD_KEY = "death.grave_coord"
    private val GRAVE = AttrKey<NpcRef>("death_grave_npc")
    private val COUNTDOWN = TimerId("death_grave")

    /** The owner of a grave NPC (world attribute through the NPC itself). */
    val OWNER = AttrKey<PlayerRef>("death_grave_owner")

    fun grave(player: PlayerRef): NpcRef? = player[GRAVE]?.takeIf { it.isInWorld }

    fun ticksLeft(player: PlayerRef): Long = player.saved(TICKS_KEY) ?: 0L

    fun coord(player: PlayerRef): CoordGrid? = player.saved(COORD_KEY)?.let { CoordGrid(it.toInt()) }

    /**
     * DE1-DE4: moves every item of [units] (inv, slot) of [dead] that is not kept into the grave (or a supply pile, or
     * nowhere in the Wilderness), at [at] unless a grave already stands elsewhere. Runs inside the death transaction.
     */
    fun bury(dead: PlayerRef, lost: List<Pair<Int, Int>>, at: CoordGrid, inWilderness: Boolean) {
        // Created bank-style before any transaction step names it (a grave stacks everything, like a bank).
        dead.inv(Invs.GRAVESTONE, stackAll = true)
        dead.inv(Invs.DEATH_PERMANENT, stackAll = true)
        val existing = coord(dead)?.takeIf { (dead.inv(Invs.GRAVESTONE, stackAll = true).isNotEmpty()) }
        val graveAt = if (existing != null && !(inWilderness && !Wilderness.contains(existing))) existing else at
        val refilled = existing != null
        if (refilled) tidy(dead, existing)
        var buried = 0
        dead.transaction("grave") {
            lost.all { (invId, slot) ->
                val inv = dead.inv(invId)
                val obj = inv.obj(slot)
                val count = inv.count(slot)
                when {
                    obj < 0 -> true
                    isSupply(dead, obj) && inWilderness -> removeAt(invId, slot, obj, count)
                    isSupply(dead, obj) -> dropAt(invId, slot, obj, count, at, dead.username)
                    else -> move(invId, slot, obj, count, Invs.GRAVESTONE).also { if (it) buried++ }
                }
            }
        }
        if (buried == 0 && dead.inv(Invs.GRAVESTONE, stackAll = true).isEmpty()) return
        if (refilled && graveAt == existing) dead.message("Some of your items have been added to your previous gravestone.")
        dead.setSaved(COORD_KEY, graveAt.packed.toLong())
        dead.setSaved(TICKS_KEY, DURATION)
        show(dead)
    }

    /** DE4: before a refill, extra unstackables and resources go to Death's Office, supplies to the ground under the grave. */
    private fun tidy(dead: PlayerRef, graveAt: CoordGrid) {
        val grave = dead.inv(Invs.GRAVESTONE, stackAll = true)
        var moved = false
        dead.transaction("grave tidy") {
            (0 until grave.size).all { slot ->
                val obj = grave.obj(slot)
                val count = grave.count(slot)
                when {
                    obj < 0 -> {
                        true
                    }

                    isSupply(dead, obj) -> {
                        dropAt(Invs.GRAVESTONE, slot, obj, count, graveAt, dead.username)
                    }

                    isResource(dead, obj) -> {
                        DeathsOffice.store(this, dead, Invs.GRAVESTONE, slot, obj, count).also { moved = true }
                    }

                    !dead.objStackable(obj) && count > UNSTACKABLE_LIMIT -> {
                        moved = true
                        DeathsOffice.store(this, dead, Invs.GRAVESTONE, slot, obj, count - UNSTACKABLE_LIMIT)
                    }

                    else -> {
                        true
                    }
                }
            }
        }
        if (moved) dead.message("Some items have been moved to Death's Office from your gravestone.")
    }

    /** Spawns the grave NPC for [player] if they have one (login, death) and starts its countdown. */
    fun show(player: PlayerRef) {
        val at = coord(player) ?: return
        if (ticksLeft(player) <= 0 || player.inv(Invs.GRAVESTONE, stackAll = true).isEmpty()) return clear(player)
        grave(player)?.despawn()
        val npc = player.spawnNpc(GRAVE_NPC, at)
        npc[OWNER] = player
        player[GRAVE] = npc
        player.stopTimer(COUNTDOWN)
        player.timer(COUNTDOWN, 1, soft = true) { countdown(this) }
    }

    /** DE5: one tick of the owner's online time, paused while the grave interface is open. */
    private fun countdown(player: PlayerRef) {
        if (DeathsOffice.lootingGrave(player)) return
        val left = ticksLeft(player) - 1
        if (left > 0) return player.setSaved(TICKS_KEY, left)
        expire(player)
    }

    /** DE7: the grave's items go to Death's Office. */
    fun expire(player: PlayerRef) {
        val grave = player.inv(Invs.GRAVESTONE, stackAll = true)
        player.transaction("grave expired") {
            (0 until grave.size).all { slot ->
                val obj = grave.obj(slot)
                obj < 0 || DeathsOffice.store(this, player, Invs.GRAVESTONE, slot, obj, grave.count(slot))
            }
        }
        clear(player)
    }

    /** The grave is gone (looted empty or expired): NPC, timer and saved keys. */
    fun clear(player: PlayerRef) {
        grave(player)?.despawn()
        player[GRAVE] = null
        player.stopTimer(COUNTDOWN)
        player.setSaved(TICKS_KEY, null)
        player.setSaved(COORD_KEY, null)
    }

    /** Called after items left the grave: the timer restarts (DE5), or the grave goes when it is empty. */
    fun changed(player: PlayerRef) {
        if (player.inv(Invs.GRAVESTONE, stackAll = true).isEmpty()) return clear(player)
        player.setSaved(TICKS_KEY, DURATION)
    }

    /** DE2: cooked food and potions (their first backpack op is Eat or Drink). */
    private fun isSupply(player: PlayerRef, obj: Int): Boolean = player.objIop(obj, 0)?.lowercase() in SUPPLY_OPS

    /** DE4: unpowered orbs, pure essence, bones, ores and planks (by cache name). */
    private fun isResource(player: PlayerRef, obj: Int): Boolean {
        val name = player.objName(obj).lowercase()
        return name == "unpowered orb" || name == "pure essence" || name.endsWith("bones") || name.endsWith(" ore") ||
            name.endsWith("plank")
    }

    private val SUPPLY_OPS = setOf("eat", "drink")

    private fun tp.plugin.InvView.isEmpty(): Boolean = freeSlots == size

    private fun tp.plugin.InvView.isNotEmpty(): Boolean = !isEmpty()

    /** The Wilderness test for a coord (PvP content keeps the player's flag; graves need a coord test). */
    private object Wilderness {
        fun contains(at: CoordGrid): Boolean = at.x in WILD_X && at.z in WILD_Z

        /** Wiki "Wilderness": x 2944-3391, z 3525-4351 above ground (dungeons below are not counted here). */
        private val WILD_X = 2944..3391
        private val WILD_Z = 3525..4351
    }
}
