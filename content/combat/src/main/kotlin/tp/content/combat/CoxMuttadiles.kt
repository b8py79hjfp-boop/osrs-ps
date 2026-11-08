package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.Seqs

/**
 * The Muttadiles, CoX combat room (docs/mechanics/raids/cox/muttadiles.md MU1-MU9).
 * - MU2: the small one fights with melee in reach and ranged from a distance.
 * - MU3: while it lives the large one stays submerged (not attackable) and casts at every raider in its sight with a 1/3
 *   chance each (CM: 20-80% of its max).
 * - MU4 / MU5: when the small one dies the large one emerges: melee in reach (or a stomp, 1.5x the melee max, at everyone in
 *   reach), ranged or magic from a distance, staying on a style at least three distance attacks.
 * - MU7: below 50% each goes to the meat tree and eats up to 50% of its max, at most three times, giving up after 25 ticks
 *   without reaching it; the tree can be cut down (raids-cox: Cut), which ends the healing.
 */
internal object CoxMuttadiles : Boss(Npcs.RAIDS_DOGODILE_JUNIOR, Npcs.RAIDS_DOGODILE_SUBMERGED, Npcs.RAIDS_DOGODILE) {
    private class State {
        var meals = 0
        var eating = false
        var seekingSince = -1L
        var eaten = 0
        var styleAttacks = 0
        var magic = false
        var nextSubmergedCast = 0L
    }

    private val STATE = AttrKey<State>("cox_muttadile")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc[STATE] != null) return
        val s = state(npc)
        if (npc.type == Npcs.RAIDS_DOGODILE_SUBMERGED) {
            npc.huntRange = 0
            npc.anim(Seqs.DOHGADYLE_IDLE_WATER)
        } else {
            npc.huntRange = HUNT
        }
        CoxKit.loop(npc) { tick(npc, s) }
    }

    override fun retaliates(npc: NpcRef): Boolean = npc.type != Npcs.RAIDS_DOGODILE_SUBMERGED && !state(npc).eating

    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        if (npc.type == Npcs.RAIDS_DOGODILE_SUBMERGED) 0 else damage

    override fun died(npc: NpcRef, killer: EntityRef?) {
        if (npc.type != Npcs.RAIDS_DOGODILE_JUNIOR) return
        // MU4: the large one emerges when the small one dies.
        for (big in CoxKit.roomNpcs(npc, setOf(Npcs.RAIDS_DOGODILE_SUBMERGED))) {
            big.anim(Seqs.DOHGADYLE_EMERGE)
            big.transform(Npcs.RAIDS_DOGODILE, restoreStats = false)
            big.huntRange = HUNT
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (s.eating || npc.type == Npcs.RAIDS_DOGODILE_SUBMERGED) return
        val large = npc.type == Npcs.RAIDS_DOGODILE
        if (!distance) {
            val inReach = CoxKit.raiders(npc).filter { TfaKit.gap(npc, it) <= 1 }
            if (large && npc.random(STOMP_ODDS) == 0) {
                // MU5: the stomp hits everyone in melee range, 50% above the melee max.
                npc.anim(Seqs.DOHGADYLE_SLAM)
                val max = CoxKit.max(npc, NpcStat.STRENGTH, LARGE_STR) * STOMP_PERCENT / PERCENT
                for (p in inReach) BossCombat.strike(npc, p, AttackType.CRUSH, max, delay = 0)
                return
            }
            npc.anim(Seqs.DOHGADYLE_BITE)
            val max = CoxKit.max(npc, NpcStat.STRENGTH, if (large) LARGE_STR else SMALL_STR)
            BossCombat.strike(npc, victim, AttackType.STAB, max, delay = 0)
            return
        }
        // MU4 (29 Nov 2023): at least three distance attacks on a style before a swap (OPEN-MU7: the swap odds).
        if (large && s.styleAttacks >= MIN_STYLE_RUN && npc.random(2) == 0) {
            s.magic = !s.magic
            s.styleAttacks = 0
        }
        s.styleAttacks++
        val magic = large && s.magic
        npc.anim(if (magic) Seqs.DOHGADYLE_LAZOR else Seqs.DOHGADYLE_BARK)
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        val delay = BossCombat.delay(npc, victim, style)
        val max =
            when {
                magic -> CoxKit.scaledMax(npc, NpcStat.MAGIC, LARGE_BASE, LARGE_MAGIC_MAX)
                large -> CoxKit.max(npc, NpcStat.RANGED, LARGE_RANGED_STR)
                else -> CoxKit.max(npc, NpcStat.RANGED, SMALL_RANGED_STR)
            }
        // MU6: protection prayers only reduce ranged and magic (OPEN-MU9: halved).
        BossCombat.strike(npc, victim, if (magic) AttackType.MAGIC else AttackType.RANGED, max, delay, rule = PrayerRule.HALVES)
    }

    private fun tick(npc: NpcRef, s: State) {
        if (npc.type == Npcs.RAIDS_DOGODILE_SUBMERGED) {
            submerged(npc, s)
            return
        }
        hunger(npc, s)
    }

    /** MU3: the submerged one's chamber-wide magic: each raider in its sight, 1 in 3 (OPEN-MU6: every 4 ticks). */
    private fun submerged(npc: NpcRef, s: State) {
        val small = CoxKit.roomNpcs(npc, setOf(Npcs.RAIDS_DOGODILE_JUNIOR))
        if (small.isEmpty() || npc.tick < s.nextSubmergedCast) return
        val raiders = CoxKit.raiders(npc)
        if (raiders.isEmpty()) return
        s.nextSubmergedCast = npc.tick + ATTACK_SPEED
        val full = CoxKit.scaledMax(npc, NpcStat.MAGIC, LARGE_BASE, LARGE_MAGIC_MAX)
        for (p in raiders) {
            if (!npc.hasLineOfSight(p) || npc.random(SUBMERGED_ODDS) != 0) continue
            val delay = BossCombat.delay(npc, p, CombatStyle.MAGIC)
            if (CoxKit.cm(npc)) {
                // 16 Jun 2021: in Challenge Mode it deals 20-80% of its max.
                val raw = BossCombat.roll(npc, full * CM_MIN / PERCENT, full * CM_MAX / PERCENT)
                val dmg = BossCombat.afterPrayer(p, CombatStyle.MAGIC, raw, PrayerRule.HALVES)
                CombatSystem.queueHit(p, npc, dmg, HitStyle.MAGIC, delay)
            } else {
                BossCombat.strike(npc, p, AttackType.MAGIC, full, delay, rule = PrayerRule.HALVES)
            }
        }
    }

    /** MU7: below 50% to the meat tree, up to three meals of up to 50%; give up after 25 ticks without reaching it. */
    private fun hunger(npc: NpcRef, s: State) {
        val tree = CoxKit.roomNpcs(npc, setOf(Npcs.RAIDS_DOGODILE_MEAT_TREE)).firstOrNull()
        if (!s.eating) {
            if (tree == null || s.meals >= MAX_MEALS || npc.hitpoints * PERCENT >= npc.maxHitpoints * HUNGRY_BELOW) return
            s.eating = true
            s.eaten = 0
            s.seekingSince = npc.tick
            npc.stopInteraction()
            npc.huntRange = 0
        }
        if (tree == null) {
            stopEating(npc, s)
            return
        }
        if (TfaKit.gap(npc, tree) > 1) {
            if (npc.tick - s.seekingSince > GIVE_UP_AFTER) {
                s.meals = MAX_MEALS
                stopEating(npc, s)
            } else {
                npc.walkTo(tree.coord)
            }
            return
        }
        // OPEN-MU10: a bite of 5% of max every 2 ticks until 50% has been eaten or it is full.
        if (npc.tick % BITE_EVERY != 0L) return
        npc.anim(Seqs.DOHGADYLE_BITE)
        val bite = maxOf(1, npc.maxHitpoints * BITE_PERCENT / PERCENT)
        npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + bite)
        s.eaten += bite
        if (s.eaten >= npc.maxHitpoints * MEAL_PERCENT / PERCENT || npc.hitpoints >= npc.maxHitpoints) {
            s.meals++
            stopEating(npc, s)
        }
    }

    private fun stopEating(npc: NpcRef, s: State) {
        s.eating = false
        npc.huntRange = HUNT
    }

    /** Strength bonuses (infobox): small 48, large 55; ranged strength small 56, large 47. */
    private const val SMALL_STR = 48
    private const val LARGE_STR = 55
    private const val SMALL_RANGED_STR = 56
    private const val LARGE_RANGED_STR = 47

    /** Infobox: the large magic max 23 at a maxed solo (magic 250). */
    private const val LARGE_MAGIC_MAX = 23
    private const val LARGE_BASE = 250

    /** MU5: the stomp is 1.5x the melee max (Mod Ash); OPEN-MU8 its odds. */
    private const val STOMP_PERCENT = 150
    private const val STOMP_ODDS = 4
    private const val MIN_STYLE_RUN = 3
    private const val PERCENT = 100
    private const val ATTACK_SPEED = 4
    private const val SUBMERGED_ODDS = 3
    private const val CM_MIN = 20
    private const val CM_MAX = 80

    /** MU7: below 50% (29 Nov 2023), up to 50% eaten per meal, three meals, ~15 s (25 ticks) to reach the tree. */
    private const val HUNGRY_BELOW = 50
    private const val MEAL_PERCENT = 50
    private const val MAX_MEALS = 3
    private const val GIVE_UP_AFTER = 25L
    private const val BITE_EVERY = 2L
    private const val BITE_PERCENT = 5
    private const val HUNT = 24
}
