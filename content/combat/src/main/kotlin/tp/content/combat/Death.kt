package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs

/** Graves and Death's Office (docs/mechanics/death.md): the grave's Check / Loot, Death, the coffins, portal and coffer. */
public class Death : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { spawnNpc(DeathsOffice.DEATH_NPC, DEATH_TILE, 0) }
        onLogin { Graves.show(this) }
        onLogout { Graves.grave(this)?.despawn() }
        onOpNpc(Graves.GRAVE_NPC, OP_CHECK) { npc -> if (owns(player, npc)) check(player) }
        onApNpc(Graves.GRAVE_NPC, OP_LOOT) { npc ->
            if (currentApRange != Graves.LOOT_RANGE) return@onApNpc apRange(Graves.LOOT_RANGE)
            if (owns(player, npc)) loot(player)
        }
        onOpNpc(DeathsOffice.DEATH_NPC, OP_COLLECT) { npc -> DeathsOffice.collect(player, npc) }
        for (coffin in DeathsOffice.ENTRANCES) onOpLoc(coffin, OP_ENTER) { DeathsOffice.enter(player) }
        onOpLoc(DeathsOffice.PORTAL, OP_ENTER) { DeathsOffice.leave(player, CombatSystem.RESPAWN) }
        onOpLoc(DeathsOffice.COFFER, OP_ENTER) { player.message("Your coffer holds ${DeathsOffice.coffer(player)} coins.") }
        onUseOnLoc(-1, DeathsOffice.COFFER) { used?.let { DeathsOffice.sacrifice(player, it.slot, it.obj) } }
    }

    private fun owns(player: PlayerRef, npc: NpcRef): Boolean {
        if (npc[Graves.OWNER] == player) return true
        player.message("This isn't your gravestone.")
        return false
    }

    /** DE5: the time left, in whole minutes (OPEN-DE8: the Wiki gives no Check text). */
    private fun check(player: PlayerRef) {
        val minutes = (Graves.ticksLeft(player) + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE
        player.message("Your gravestone will collapse in about $minutes minute${if (minutes == 1L) "" else "s"}.")
    }

    /** DE6: everything that fits, at the grave fee (capped). */
    private fun loot(player: PlayerRef) {
        if (DeathsOffice.reclaim(player, Invs.GRAVESTONE, DeathsOffice::graveFee, DeathsOffice.GRAVE_FEE_CAP)) Graves.changed(player)
    }

    private companion object {
        const val OP_CHECK = 1
        const val OP_LOOT = 3
        const val OP_COLLECT = 3
        const val OP_ENTER = 1
        const val TICKS_PER_MINUTE = 100L

        /** Wiki "Death (NPC)": map pin 3180, 5727 (size 2, so its south-west tile). */
        val DEATH_TILE = tp.util.CoordGrid(3179, 5726, 0)
    }
}
