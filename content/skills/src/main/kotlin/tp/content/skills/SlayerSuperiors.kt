package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.LootEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.symbols.Objs
import tp.symbols.Varbits
import kotlin.math.abs
import kotlin.math.max

/**
 * Superior Slayer monsters (docs/mechanics/slayer.md SL12). Wiki "Superior slayer monster": with the "Bigger and Badder" unlock
 * bought and switched on, the death of a monster on the player's task spawns its superior with a 1/200 chance (1/180 in the
 * Wilderness); only one at a time per player; only that player can attack it; it leaves after two minutes if left alone and
 * (checked every 30 seconds) once its player has gone. Its death gives Slayer xp and the two unique drop tables.
 */
internal class SlayerSuperiors(private val runtime: () -> SlayerRuntime) {
    /** Called for the death of [npc] on [player]'s task: may spawn the superior. */
    fun rollSpawn(player: PlayerRef, npc: NpcRef) {
        val sup = SlayerData.SUPERIORS[npc.displayName.lowercase()] ?: return
        if (!enabled(player)) return
        if (player[ACTIVE]?.isInWorld == true) return
        val den = if (player[SharedAttrs.IN_WILDERNESS] == true) SPAWN_WILDERNESS else SPAWN
        if (player.random(den) != 0) return
        val s = npc.spawnNpc(sup.npc, npc.coord, 0)
        s[OWNER] = player.username
        player[ACTIVE] = s
        player.message("<col=ef1020>A superior foe has appeared...</col>")
        watch(s, player.username, IDLE_TICKS)
    }

    private fun enabled(player: PlayerRef): Boolean {
        val unlock = runtime().cache.unlock(BIGGER_AND_BADDER)
        return SlayerState.unlocked(player, unlock.bit) && player.varbit(Varbits.SLAYER_TOGGLEOFF_SUPERIORMOBS) == 0
    }

    /** Whether [npc] is a superior that [attacker] may not attack; the denial text if so. */
    fun denial(attacker: PlayerRef, npc: NpcRef): String? {
        val owner = npc[OWNER] ?: return null
        return if (owner == attacker.username) null else NOT_YOURS
    }

    /**
     * The superior [npc] died to [player]: Slayer xp unless [xpGiven] (the task kill already gave it), the unique drops, and it
     * does not come back. Returns true if [npc] was a superior of [player].
     */
    fun died(player: PlayerRef, npc: NpcRef, xpGiven: Boolean): Boolean {
        if (npc[OWNER] != player.username) return false
        val sup = SlayerData.SUPERIOR_BY_NPC[npc.type]
        if (sup != null) {
            if (!xpGiven) player.addXp(Stats.SLAYER, sup.xp)
            uniques(player, npc, sup)
        }
        player[ACTIVE] = null
        npc.despawn()
        return true
    }

    /**
     * The two unique drop tables, rolled once per kill (Wiki: each is `1/(200 - (SlayerReq + 55)^2 / 125)`): the first gives the
     * imbued heart 1/8 and a dust or a mist battlestaff 7/16 each, the second an eternal gem 1/8.
     */
    private fun uniques(player: PlayerRef, npc: NpcRef, sup: SlayerSuperior) {
        val d = UDT_BASE - (sup.slayer + UDT_LEVEL_OFFSET).toDouble().let { it * it } / UDT_DIVISOR
        val chance = 1.0 / max(d, 1.0)
        if (hit(player, chance)) {
            val pick = player.random(UDT_HEART_BATTLESTAFF_PARTS)
            val obj =
                when {
                    pick < HEART_PARTS -> Objs.IMBUED_HEART
                    pick < HEART_PARTS + STAFF_PARTS -> Objs.DUST_BATTLESTAFF
                    else -> Objs.MIST_BATTLESTAFF
                }
            drop(player, npc, obj)
        }
        if (hit(player, chance) && player.random(GEM_ODDS) == 0) drop(player, npc, Objs.SLAYER_ETERNAL_GEM)
    }

    private fun hit(player: PlayerRef, chance: Double): Boolean = player.random(RESOLUTION) < chance * RESOLUTION

    private fun drop(player: PlayerRef, npc: NpcRef, obj: Int) {
        val at = npc.coord
        if (player.transaction("superior drop") { createOnGround(obj, 1, at, player.username) }) {
            player.events.publish(LootEvent(player, obj, 1))
        }
    }

    /** Every [CHECK_TICKS]: gone if its player left or logged out, or if nobody fought it for [IDLE_TICKS] (Wiki, Mod Ash). */
    private fun watch(npc: NpcRef, owner: String, idleLeft: Int) {
        npc.queueHit(CHECK_TICKS) {
            if (npc.isInWorld) {
                val player = npc.playersInWorld().firstOrNull { it.username == owner }
                val near = player != null && player.coord.level == npc.coord.level && distance(player, npc) <= LEAVE_RADIUS
                val engaged = npc.target != null || npc.hitpoints < npc.maxHitpoints
                val left = if (engaged) IDLE_TICKS else idleLeft - CHECK_TICKS
                if (!near || left <= 0) npc.despawn() else watch(npc, owner, left)
            }
        }
    }

    private fun distance(a: PlayerRef, b: NpcRef): Int = max(abs(a.coord.x - b.coord.x), abs(a.coord.z - b.coord.z))

    companion object {
        /** The username of the player a superior belongs to (set on the superior npc). */
        val OWNER = AttrKey<String>("slayer_superior_owner")

        /** The superior a player has out (set on the player). */
        val ACTIVE = AttrKey<NpcRef>("slayer_superior_active")

        /** Name of the unlock in db table 117; Wiki "Slayer Rewards" calls it "Bigger and Badder". */
        private const val BIGGER_AND_BADDER = "Bigger and Badder"

        /** Wiki: "a 1/200 chance that a superior spawns upon the death of one of its normal counterparts"; 1/180 in the Wilderness. */
        private const val SPAWN = 200
        private const val SPAWN_WILDERNESS = 180

        /** Wiki: "They disappear after 2 mins if you stay there ignoring them" (200 ticks) and the check is made every 30 secs (50). */
        private const val IDLE_TICKS = 200
        private const val CHECK_TICKS = 50

        /**
         * How far the player may be before a check counts them as gone. OPEN-SL11: the Wiki says "leave the area" without a distance;
         * 15 tiles is the radius the client loads around a player.
         */
        private const val LEAVE_RADIUS = 15

        /** Wiki "Superior slayer monster", Drop rate: `1/(200 - (SlayerReq + 55)^2 / 125)` per unique table. */
        private const val UDT_BASE = 200.0
        private const val UDT_LEVEL_OFFSET = 55
        private const val UDT_DIVISOR = 125.0

        /** First table: heart 1/8 = 2/16, dust battlestaff 7/16, mist battlestaff 7/16; second table: eternal gem 1/8. */
        private const val UDT_HEART_BATTLESTAFF_PARTS = 16
        private const val HEART_PARTS = 2
        private const val STAFF_PARTS = 7
        private const val GEM_ODDS = 8

        /** Probability resolution of a roll: 2^30 steps (as content/drops). */
        private const val RESOLUTION = 1 shl 30

        /** Wiki: "attempting to do so produces the message 'That is not your superior creature.'" */
        const val NOT_YOURS = "That is not your superior creature."
    }
}
