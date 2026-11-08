package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.TimerId
import tp.util.CoordGrid

/**
 * NPC aggression and tolerance (docs/mechanics/aggression.md). Aggressive NPCs (Wiki table) hunt players near them;
 * a player who stays in the same 21×21 tolerance regions for 10 minutes is left alone until they move out.
 */
public class Aggression : ContentPlugin {
    override fun ContentScope.register() {
        onNpcSpawn { if (NpcCombatTable.of(type)?.aggressive == true) huntRange = DEFAULT_HUNT_RANGE }
        onNpcHunt { hunt(this) }
        onLogin { timer(TOLERANCE_TIMER, 1, soft = true) { updateTolerance() } }
    }

    /** AG2/AG3: a uniformly random eligible player in hunt range becomes the target (RS Mod reservoir pick). */
    private fun hunt(npc: NpcRef) {
        if (npc.isDying || !npc.isInWorld) return
        var target: PlayerRef? = null
        var count = 0
        // Runs for every hunting NPC every tick: the scratch list (eligible() makes no other scratch query).
        val near = npc.playersWithinScratch(npc.huntRange)
        for (i in near.indices) {
            val player = near[i]
            if (!eligible(npc, player)) continue
            count++
            if (npc.random(count) == 0) target = player
        }
        val chosen = target ?: return
        if (!CombatSystem.canFight(npc, chosen)) return
        NpcCombat.engage(npc, chosen)
    }

    private fun eligible(npc: NpcRef, player: PlayerRef): Boolean {
        if (player.isDying || player.hasModal) return false // AG2 "not busy" (OPEN-AG2)
        val inWild = player[SharedAttrs.IN_WILDERNESS] == true
        // AG2: "a player whose combat level is less than or equal to twice the monster's level", except in the Wilderness.
        if (!inWild && player.combatLevel > npc.combatLevel * 2) return false
        // AG2: not someone already in combat, outside multi (RS Mod `checkNotCombat`: lastcombat + 8 ticks).
        val lastCombat = player[SharedAttrs.LAST_COMBAT_TICK] ?: Long.MIN_VALUE / 2
        if (player[SharedAttrs.IN_MULTI] != true && player.tick - lastCombat < ACTIVE_COMBAT_TICKS) return false
        if (player[SharedAttrs.IN_MULTI] != true && engagedByOther(npc, player)) return false
        val boss = Bosses.of(npc.type)
        if (boss != null && !boss.hunts(npc, player)) return false
        if ((boss == null || !boss.ignoresTolerance) && tolerant(player)) return false
        return npc.hasLineOfSight(player)
    }

    /** AG2 single-way: another living NPC is still fighting this player. */
    private fun engagedByOther(npc: NpcRef, player: PlayerRef): Boolean {
        val other = player[SharedAttrs.ENGAGED_BY] ?: return false
        return other != npc && other.isInWorld && !other.isDying && other.target == player
    }

    // ---- AG4 tolerance (OSRS Wiki "Tolerance")

    /** Tolerant after 10 minutes in the combined regions (bosses ignore tolerance: they set their own hunt rules). */
    private fun tolerant(player: PlayerRef): Boolean = player.tick - (player[TOLERANCE_SINCE] ?: player.tick) >= TOLERANCE_TICKS

    /**
     * "Tolerance regions are 21x21 tile zones centred on and set by the player ... When the player moves outside the
     * combined tolerance regions, the first 21x21 tile region is removed and re-generated centred on the tile upon which
     * the player moved outside ... this resets the 10-minute aggression timer."
     */
    private fun PlayerRef.updateTolerance() {
        val here = coord
        val a = this[REGION_A]
        val b = this[REGION_B]
        if (a == null || b == null) {
            this[REGION_A] = here
            this[REGION_B] = here
            this[TOLERANCE_SINCE] = tick
            return
        }
        if (inRegion(a, here) || inRegion(b, here)) return
        this[REGION_B] = a
        this[REGION_A] = here
        this[TOLERANCE_SINCE] = tick
    }

    private fun inRegion(centre: CoordGrid, c: CoordGrid): Boolean =
        centre.level == c.level && kotlin.math.abs(centre.x - c.x) <= REGION_RADIUS && kotlin.math.abs(centre.z - c.z) <= REGION_RADIUS

    private companion object {
        /** OPEN-AG1: per-NPC hunt ranges aren't published; RS Mod `NpcTypeBuilder.DEFAULT_HUNT_RANGE`. */
        const val DEFAULT_HUNT_RANGE = 5

        /** RS Mod `constants.combat_activecombat_delay`. */
        const val ACTIVE_COMBAT_TICKS = 8

        /** "After a player remains within a standard monster's tolerance region for 10 minutes" = 1,000 ticks. */
        const val TOLERANCE_TICKS = 1000L

        /** 21×21 regions: 10 tiles each way from the centre. */
        const val REGION_RADIUS = 10

        val TOLERANCE_TIMER = TimerId("tolerance")
        val REGION_A = AttrKey<CoordGrid>("tolerance_region_a")
        val REGION_B = AttrKey<CoordGrid>("tolerance_region_b")
        val TOLERANCE_SINCE = AttrKey<Long>("tolerance_since")
    }
}
