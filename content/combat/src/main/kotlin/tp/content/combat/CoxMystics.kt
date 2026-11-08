package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs

/**
 * Skeletal mystics, CoX combat room (docs/mechanics/raids/cox/mystics.md MY1-MY5).
 * - MY1: not aggressive; they join the fight when attacked.
 * - MY2: magic (Fire Blast or a damaging Vulnerability) at range 10, or melee: a mystic that can reach a raider praying
 *   against magic punches it; otherwise it keeps casting.
 * - MY5: when the last one dies the three portals appear (raids-cox).
 */
internal object CoxMystics : Boss(Npcs.RAIDS_SKELETONMYSTIC_A, Npcs.RAIDS_SKELETONMYSTIC_B, Npcs.RAIDS_SKELETONMYSTIC_C) {
    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val prays = (victim as? PlayerRef)?.protects(CombatStyle.MAGIC) == true
        if (!distance && prays) {
            npc.anim(Seqs.SKELETON_UPDATE_ATTACK_WEAPON)
            BossCombat.strike(npc, victim, AttackType.CRUSH, CoxKit.max(npc, NpcStat.STRENGTH, STR_BONUS), delay = 0)
            return
        }
        npc.anim(Seqs.SKELETON_UPDATE_ATTACK_WEAPON)
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC)
        // OPEN-MY1: the Vulnerability's debuff; MY2 Strat: Protect from Magic cuts accuracy and damage by about half.
        val max = CoxKit.scaledMax(npc, NpcStat.MAGIC, BASE_MAGIC, MAGIC_MAX)
        BossCombat.strike(npc, victim, AttackType.MAGIC, max, delay, rule = PrayerRule.HALVES)
    }

    /** Infobox: strength bonus 50; OPEN-MY5: the magic max ("Varies") at a maxed solo, Fire Blast 16 + 38% magic damage. */
    private const val STR_BONUS = 50
    private const val MAGIC_MAX = 22
    private const val BASE_MAGIC = 140
}
