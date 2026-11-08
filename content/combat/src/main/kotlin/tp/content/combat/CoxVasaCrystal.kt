package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs

/** Vasa's glowing crystal (vasa.md Data, Crystal page): immune to ranged, a third of magic damage; it never attacks. */
internal object CoxVasaCrystal : Boss(Npcs.RAIDS_VASANISTIRIO_CRYSTAL) {
    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        when (style) {
            HitStyle.RANGED -> 0
            HitStyle.MAGIC -> damage / MAGIC_DIVISOR
            else -> damage
        }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.setAttackClock(npc.tick + IDLE)
    }

    private const val MAGIC_DIVISOR = 3
    private const val IDLE = 10
}
