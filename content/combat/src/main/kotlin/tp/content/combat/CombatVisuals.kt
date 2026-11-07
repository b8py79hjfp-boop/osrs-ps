package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Seqs

/**
 * Combat animations that are server data (docs/mechanics/npc-combat.md N10, combat-data.md CD-W3): an NPC's attack
 * anim per style, its block and death anims (`npc-anims.tsv`, derived from the cache by tools/wiki-import/npc_anims.py),
 * and the block anim a player's shield or weapon plays when hit (RS Mod obj data, `weapons.tsv`).
 */
internal object CombatVisuals {
    private class NpcAnims(val melee: Int, val ranged: Int, val magic: Int, val block: Int, val death: Int)

    private val npcs: Map<Int, NpcAnims> = parse()

    /** The NPC's attack anim for [style] (melee falls back to any attack anim it has). */
    fun npcAttack(npc: NpcRef, style: CombatStyle) {
        val a = npcs[npc.type] ?: return
        val anim =
            when (style) {
                CombatStyle.MELEE -> a.melee
                CombatStyle.RANGED -> a.ranged.takeIf { it >= 0 } ?: a.melee
                CombatStyle.MAGIC -> a.magic.takeIf { it >= 0 } ?: a.melee
            }
        if (anim >= 0) npc.anim(anim)
    }

    /** The NPC's death anim, played when it dies. */
    fun npcDeath(npc: NpcRef) {
        val anim = npcs[npc.type]?.death ?: return
        if (anim >= 0) npc.anim(anim)
    }

    /**
     * The defender's block anim, timed to land with the hit [delay] ticks from now (RS Mod `combatPlayDefendAnim`): a
     * player plays the shield's anim, else the weapon's, else the unarmed block; an NPC its own block anim.
     */
    fun block(target: EntityRef, delay: Int) {
        val cycles = maxOf(0, target.ticksUntilHit(delay) - 1) * CYCLES_PER_TICK
        when (target) {
            is PlayerRef -> {
                if (target.isMoving) return
                val shield =
                    target
                        .worn(Wear.SHIELD)
                        .takeIf { it >= 0 }
                        ?.let { WeaponData.of(it)?.blockAnim }
                        ?.takeIf { it >= 0 }
                val weapon =
                    target
                        .worn(Wear.WEAPON)
                        .takeIf { it >= 0 }
                        ?.let { WeaponData.of(it)?.blockAnim }
                        ?.takeIf { it >= 0 }
                target.anim(shield ?: weapon ?: Seqs.HUMAN_UNARMEDBLOCK, delay = cycles)
            }

            is NpcRef -> {
                val anim = npcs[target.type]?.block ?: return
                if (anim >= 0 && !target.isDying) target.anim(anim, delay = cycles)
            }
        }
    }

    private fun parse(): Map<Int, NpcAnims> {
        val text = CombatVisuals::class.java.getResource("npc-anims.tsv")?.readText() ?: error("npc-anims.tsv missing")
        val out = HashMap<Int, NpcAnims>()
        for (line in text.lineSequence().drop(1).filter { it.isNotBlank() }) {
            val c = line.split('\t')

            fun i(n: Int): Int = c.getOrNull(n)?.toIntOrNull() ?: -1
            out[i(0)] = NpcAnims(i(MELEE), i(RANGED), i(MAGIC), i(BLOCK), i(DEATH))
        }
        return out
    }

    private const val MELEE = 2
    private const val RANGED = 3
    private const val MAGIC = 4
    private const val BLOCK = 5
    private const val DEATH = 6
    private const val CYCLES_PER_TICK = 30
}
