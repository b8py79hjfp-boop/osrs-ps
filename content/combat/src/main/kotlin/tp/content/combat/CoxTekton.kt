package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Tekton, CoX combat room (docs/mechanics/raids/cox/tekton.md TK1-TK10).
 * - TK1: works at his anvil until a raider comes near, then fights.
 * - TK2: melee every 3 ticks (stab / slash / crush), max from his scaled strength (52 / enraged 59 at a maxed solo).
 * - TK3: after 10-14 attacks, or when no raider is in reach at an attack, he walks back to the anvil; a raider under him on
 *   the way takes stomp damage.
 * - TK4: at the anvil he repairs about 1.33% of his max HP every few ticks and sends two sparks at each raider per set
 *   (3 x 3, 10-20), up to five sets.
 * - TK5: then enraged for about five attacks (defence and max hit up), then standard again.
 * - TK6: the first dragon warhammer / elder maul special that reaches him always lands; ranged does nothing, magic a fifth.
 */
internal object CoxTekton : Boss(
    Npcs.RAIDS_TEKTON_WAITING,
    Npcs.RAIDS_TEKTON_WALKING_STANDARD,
    Npcs.RAIDS_TEKTON_FIGHTING_STANDARD,
    Npcs.RAIDS_TEKTON_WALKING_ENRAGED,
    Npcs.RAIDS_TEKTON_FIGHTING_ENRAGED,
    Npcs.RAIDS_TEKTON_HAMMERING,
) {
    private enum class Phase { WAITING, FIGHTING, RETURNING, ANVIL, ENRAGED }

    private class State {
        var phase = Phase.WAITING
        var attacks = 0
        var attacksUntilAnvil = 0
        var anvilTicks = 0
        var sparkSets = 0
        var enragedAttacks = 0
        var specialTaken = false
    }

    private val STATE = AttrKey<State>("cox_tekton")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.RAIDS_TEKTON_WAITING) return
        npc.huntRange = 0
        npc.anim(Seqs.TEKTON_SPAWN)
        val s = state(npc)
        CoxKit.loop(npc) { tick(npc, s) }
    }

    /** He is only in the fight when he can be reached: the anvil forms take hits too (op1 Attack on 7545). */
    override fun retaliates(npc: NpcRef): Boolean = state(npc).phase == Phase.FIGHTING || state(npc).phase == Phase.ENRAGED

    override fun specialLands(npc: NpcRef, special: String): Boolean {
        val s = state(npc)
        if (s.specialTaken || special !in SURE_SPECIALS) return false
        s.specialTaken = true
        return true
    }

    /** TK8: immune to ranged; magic damage is a fifth (Mod Ash "divided by 5"). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        when (style) {
            HitStyle.RANGED -> 0
            HitStyle.MAGIC -> damage / MAGIC_DIVISOR
            else -> damage
        }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (s.phase != Phase.FIGHTING && s.phase != Phase.ENRAGED) return
        // TK3: no raider in reach at an attack sends him back to the anvil.
        if (distance) {
            if (s.phase == Phase.FIGHTING) toAnvil(npc, s)
            return
        }
        val enraged = s.phase == Phase.ENRAGED
        if (!enraged) npc.transform(Npcs.RAIDS_TEKTON_FIGHTING_STANDARD, restoreStats = false)
        val type = TYPES[npc.random(TYPES.size)]
        npc.anim(if (enraged) ENRAGED_ANIMS[type.ordinal] else ANIMS[type.ordinal])
        val max = CoxKit.max(npc, NpcStat.STRENGTH, if (enraged) ENRAGED_STR_BONUS else STR_BONUS)
        // OPEN-TK3: the front-plus-right footprint is not stated; the hit goes to the target.
        BossCombat.strike(npc, victim, type, max, delay = 0)
        if (enraged) {
            if (++s.enragedAttacks >= ENRAGED_ATTACKS) {
                s.phase = Phase.FIGHTING
                npc.transform(Npcs.RAIDS_TEKTON_FIGHTING_STANDARD, restoreStats = false)
                s.attacks = 0
                s.attacksUntilAnvil = ATTACKS_MIN + npc.random(ATTACKS_MAX - ATTACKS_MIN + 1)
            }
        } else if (++s.attacks >= s.attacksUntilAnvil) {
            toAnvil(npc, s)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        val raiders = CoxKit.raiders(npc)
        when (s.phase) {
            Phase.WAITING -> {
                if (raiders.any { npc.distanceTo(it) <= NOTICE }) wake(npc, s)
            }

            Phase.RETURNING -> {
                // TK3: a raider under him on the way back takes stomp damage.
                for (p in raiders) if (CoxKit.under(npc, p)) BossCombat.areaHit(npc, p, BossCombat.roll(npc, 1, STOMP_MAX))
                if (npc.coord == npc.spawn) startRepair(npc, s) else npc.walkTo(npc.spawn)
            }

            Phase.ANVIL -> {
                repair(npc, s, raiders)
            }

            else -> {
                Unit
            }
        }
    }

    private fun wake(npc: NpcRef, s: State) {
        npc.anim(Seqs.TEKTON_AVIL_TRANSITION_TO_IDLE)
        npc.transform(Npcs.RAIDS_TEKTON_WALKING_STANDARD, restoreStats = false)
        s.phase = Phase.FIGHTING
        s.attacks = 0
        s.attacksUntilAnvil = ATTACKS_MIN + npc.random(ATTACKS_MAX - ATTACKS_MIN + 1)
        npc.huntRange = HUNT
    }

    private fun toAnvil(npc: NpcRef, s: State) {
        s.phase = Phase.RETURNING
        npc.stopInteraction()
        npc.huntRange = 0
        npc.transform(Npcs.RAIDS_TEKTON_WALKING_STANDARD, restoreStats = false)
        npc.walkTo(npc.spawn)
    }

    private fun startRepair(npc: NpcRef, s: State) {
        s.phase = Phase.ANVIL
        s.anvilTicks = 0
        s.sparkSets = 0
        npc.transform(Npcs.RAIDS_TEKTON_HAMMERING, restoreStats = false)
        npc.anim(Seqs.TEKTON_IDLE_TRANSITION_TO_ANVIL)
    }

    /** TK4: about 17 ticks at the anvil; a repair every [REPAIR_EVERY] ticks and a spark set every [SPARK_EVERY]. */
    private fun repair(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        s.anvilTicks++
        if (s.anvilTicks % REPAIR_EVERY == 0) {
            // TK4: 4 HP of 300 (1.33%); TK6: Defence is not reset at the anvil (29 Nov 2023).
            val heal = maxOf(1, npc.maxHitpoints * REPAIR_PER_MILLE / PER_MILLE)
            npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + heal)
        }
        if (s.anvilTicks % SPARK_EVERY == 0 && s.sparkSets < SPARK_SETS) {
            s.sparkSets++
            for (p in raiders) {
                repeat(SPARKS_PER_PLAYER) {
                    // Two sparks per raider; OPEN-TK7: each lands near the raider's tile at launch, after the delay.
                    val at = if (it == 0) p.coord else BossCombat.randomTile(npc, p.coord, 1)
                    spark(npc, at)
                }
            }
        }
        if (s.anvilTicks >= ANVIL_TICKS) enrage(npc, s)
    }

    private fun spark(npc: NpcRef, at: CoordGrid) {
        npc.queueHit(SPARK_DELAY, strong = false) {
            if (!npc.isInWorld) return@queueHit
            for (p in CoxKit.inArea(npc, at)) BossCombat.areaHit(npc, p, BossCombat.roll(npc, SPARK_MIN, SPARK_MAX))
        }
    }

    /** TK5: enraged (defence and max hit up) for about five attacks. */
    private fun enrage(npc: NpcRef, s: State) {
        s.phase = Phase.ENRAGED
        s.enragedAttacks = 0
        npc.anim(Seqs.TEKTON_AVIL_TRANSITION_TO_IDLE_ENRAGED)
        npc.transform(Npcs.RAIDS_TEKTON_FIGHTING_ENRAGED, restoreStats = false)
        npc.huntRange = HUNT
    }

    /** TK6: the specials whose first contact always lands (dragon warhammer "Smash", elder maul "Pulverize"). */
    private val SURE_SPECIALS = setOf("Smash", "Pulverize")
    private val TYPES = listOf(AttackType.STAB, AttackType.SLASH, AttackType.CRUSH)

    /** OPEN-TK9: the attack animation per damage type is not stated; stab / slash / crush by name. */
    private val ANIMS = intArrayOf(Seqs.TEKTON_ATTACK_STAB, Seqs.TEKTON_SLASH, Seqs.TEKTON_HAMMER_CRUSH)
    private val ENRAGED_ANIMS = intArrayOf(Seqs.TEKTON_ATTACK_STAB_ENRAGED, Seqs.TEKTON_SLASH_ENRAGED, Seqs.TEKTON_HAMMER_CRUSH_ENRAGED)

    /** Strength bonus 20 standard, 30 enraged (tekton.md Data). */
    private const val STR_BONUS = 20
    private const val ENRAGED_STR_BONUS = 30
    private const val MAGIC_DIVISOR = 5

    /** Strat: 10-14 attacks before the anvil. */
    private const val ATTACKS_MIN = 10
    private const val ATTACKS_MAX = 14

    /** Strat: "around ~5 or so attacks" enraged (OPEN-TK8). */
    private const val ENRAGED_ATTACKS = 5

    /** OPEN-TK2: the notice range. */
    private const val NOTICE = 6
    private const val HUNT = 24

    /** OPEN-TK5: the stomp under him. */
    private const val STOMP_MAX = 10

    /** Page: about 10 s at the anvil (17 ticks); Strat: up to five spark sets; OPEN-TK6 / OPEN-TK7 the intervals. */
    private const val ANVIL_TICKS = 17
    private const val REPAIR_EVERY = 3
    private const val SPARK_EVERY = 3
    private const val SPARK_SETS = 5
    private const val SPARKS_PER_PLAYER = 2
    private const val SPARK_DELAY = 3
    private const val SPARK_MIN = 10
    private const val SPARK_MAX = 20

    /** 1.33% of max HP (4 of 300) per repair. */
    private const val REPAIR_PER_MILLE = 13
    private const val PER_MILLE = 1000
}
