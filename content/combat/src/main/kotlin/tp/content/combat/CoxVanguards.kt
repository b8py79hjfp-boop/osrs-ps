package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * The Vanguards, CoX combat room (docs/mechanics/raids/cox/vanguards.md VG1-VG7; RuneLite: a 4-tick cycle).
 * - VG1: three underground (7525) until a raider comes near a hole; then all three burst out as melee / ranged / magic.
 * - VG2: each attack is three hits every 4 ticks; melee hits its target three times, ranged and magic launch three
 *   projectiles (one at the target, two elsewhere); protection prayers reduce, not block.
 * - VG3 / VG6: if one is more than 40% (five or more raiders: 33.3%) of its max HP ahead of the lowest, all three shuffle
 *   and heal in full.
 * - VG5: they also shuffle every 20-36 ticks; while moving they cannot be targeted, a fatal hit leaves 1 HP, drained stats
 *   are restored, and raiders in their way take 3-6.
 */
internal object CoxVanguards : Boss(
    Npcs.RAIDS_VANGUARD_DORMANT,
    Npcs.RAIDS_VANGUARD_WALKING,
    Npcs.RAIDS_VANGUARD_MELEE,
    Npcs.RAIDS_VANGUARD_RANGED,
    Npcs.RAIDS_VANGUARD_MAGIC,
) {
    private class Group(val members: List<NpcRef>) {
        var awake = false
        var nextShuffle = 0L
        var shufflingUntil = -1L
        var clockwise = true
    }

    private val GROUP = AttrKey<Group>("cox_vanguards")

    /** The fighting form each Vanguard takes (by spawn order: melee, ranged, magic). */
    private val FORM = AttrKey<Int>("cox_vanguard_form")

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.RAIDS_VANGUARD_DORMANT) return
        npc.huntRange = 0
        npc.anim(Seqs.VANGUARD_DORMANT)
        // The room spawns three; the first one to spawn runs the group once its siblings exist.
        npc.queueHit(1, strong = false) { join(npc) }
    }

    private fun join(npc: NpcRef) {
        if (npc[GROUP] != null) return
        val all = CoxKit.roomNpcs(npc, setOf(Npcs.RAIDS_VANGUARD_DORMANT)).sortedBy { it.index }
        val g = Group(all)
        for ((k, v) in all.withIndex()) {
            v[GROUP] = g
            v[FORM] = FORMS[k % FORMS.size]
        }
        CoxKit.loop(npc) { tick(npc, g) }
    }

    override fun retaliates(npc: NpcRef): Boolean = npc[GROUP]?.let { it.shufflingUntil < npc.tick } ?: false

    /** VG5: "in their shell" while shuffling: no damage gets through (attacks made before still land: OPEN-VG6). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val g = npc[GROUP] ?: return 0
        if (!g.awake) return 0
        return damage
    }

    /** VG5: a fatal hit while they shuffle leaves 1 HP. */
    override fun onLethal(npc: NpcRef): Boolean {
        val g = npc[GROUP] ?: return false
        if (g.shufflingUntil < npc.tick) return false
        npc.hitpoints = 1
        return true
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val g = npc[GROUP] ?: return
        if (g.shufflingUntil >= npc.tick) return
        // VG3: the most-healthy one more than the threshold (of max HP) ahead of the least: a forced reset.
        val alive = g.members.filter { it.isInWorld && !it.isDying }
        if (alive.size < 2) return
        val pct = alive.map { it.hitpoints * PERCENT / maxOf(1, it.maxHitpoints) }
        val gap = pct.max() - pct.min()
        val threshold = if ((npc[tp.plugin.SharedAttrs.RAID_SCALE] ?: 1) >= LARGE_GROUP) LARGE_THRESHOLD else SMALL_THRESHOLD
        if (gap > threshold) shuffle(npc, g, heal = true)
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val g = npc[GROUP] ?: return
        if (!g.awake || g.shufflingUntil >= npc.tick) return
        when (npc.type) {
            Npcs.RAIDS_VANGUARD_MELEE -> {
                if (distance) return
                npc.anim(Seqs.VANGUARD_ATTACK_MELEE)
                val max = CoxKit.scaledMax(npc, NpcStat.STRENGTH, BASE_LEVEL, MELEE_MAX)
                repeat(HITS) { BossCombat.strike(npc, victim, AttackType.CRUSH, max, delay = 0, rule = PrayerRule.HALVES) }
            }

            Npcs.RAIDS_VANGUARD_RANGED -> {
                volley(npc, victim, CombatStyle.RANGED)
            }

            Npcs.RAIDS_VANGUARD_MAGIC -> {
                volley(npc, victim, CombatStyle.MAGIC)
            }
        }
    }

    /** VG2: three projectiles, one at the target, two at other raiders or tiles near it (OPEN-VG4: who else). */
    private fun volley(npc: NpcRef, victim: EntityRef, style: CombatStyle) {
        val magic = style == CombatStyle.MAGIC
        npc.anim(if (magic) Seqs.VANGUARD_ATTACK_MAGIC else Seqs.VANGUARD_ATTACK_RANGED)
        val max =
            if (magic) {
                CoxKit.scaledMax(npc, NpcStat.MAGIC, BASE_LEVEL, MAGIC_MAX)
            } else {
                CoxKit.scaledMax(npc, NpcStat.RANGED, BASE_LEVEL, RANGED_MAX)
            }
        val others = CoxKit.raiders(npc).filter { it != victim }
        val targets = listOf(victim) + List(HITS - 1) { CoxKit.pick(npc, others) ?: victim }
        for ((k, t) in targets.withIndex()) {
            val delay = BossCombat.delay(npc, t, style)
            BossCombat.projectile(npc, t, if (magic) SpotAnims.RAIDS_VANGUARD_MAGIC else RANGE_PROJ[k], delay)
            BossCombat.strike(npc, t, if (magic) AttackType.MAGIC else AttackType.RANGED, max, delay, rule = PrayerRule.HALVES)
        }
    }

    private fun tick(npc: NpcRef, g: Group) {
        val alive = g.members.filter { it.isInWorld && !it.isDying }
        if (!g.awake) {
            if (CoxKit.raiders(npc).any { p -> alive.any { it.distanceTo(p) <= NOTICE } }) wake(npc, g, alive)
            return
        }
        if (g.shufflingUntil >= npc.tick) {
            // VG5: raiders in their path take 3-6 every few ticks.
            if ((g.shufflingUntil - npc.tick) % PATH_HIT_EVERY == 0L) {
                for (v in alive) {
                    for (p in CoxKit.raiders(npc)) if (CoxKit.under(v, p)) BossCombat.areaHit(v, p, BossCombat.roll(v, PATH_MIN, PATH_MAX))
                }
            }
            if (g.shufflingUntil == npc.tick) emerge(alive)
            return
        }
        if (npc.tick >= g.nextShuffle) shuffle(npc, g, heal = false)
    }

    private fun wake(npc: NpcRef, g: Group, alive: List<NpcRef>) {
        g.awake = true
        for (v in alive) {
            v.anim(Seqs.VANGUARD_SPAWN)
            v.transform(v[FORM] ?: Npcs.RAIDS_VANGUARD_MELEE, restoreStats = false)
            v.huntRange = HUNT
        }
        g.nextShuffle = npc.tick + SHUFFLE_MIN + npc.random(SHUFFLE_MAX - SHUFFLE_MIN + 1)
    }

    /** VG5 / VG6: into the shell, around the room, drained stats restored (and HP in full on a forced reset). */
    private fun shuffle(npc: NpcRef, g: Group, heal: Boolean) {
        val alive = g.members.filter { it.isInWorld && !it.isDying }
        g.clockwise = npc.random(2) == 0
        g.shufflingUntil = npc.tick + SHUFFLE_TICKS
        g.nextShuffle = g.shufflingUntil + SHUFFLE_MIN + npc.random(SHUFFLE_MAX - SHUFFLE_MIN + 1)
        val spots = alive.map { it.coord }
        for ((k, v) in alive.withIndex()) {
            v.stopInteraction()
            v.huntRange = 0
            v.transform(Npcs.RAIDS_VANGUARD_WALKING, restoreStats = false)
            v.anim(Seqs.VANGUARD_WALK)
            for (stat in COMBAT_STATS) v.setLevel(stat, v.baseLevel(stat))
            if (heal) v.hitpoints = v.maxHitpoints
            // They move round to the next one's place (clockwise or anticlockwise).
            val to = spots[(k + if (g.clockwise) 1 else spots.size - 1) % spots.size]
            v.walkTo(to)
        }
    }

    private fun emerge(alive: List<NpcRef>) {
        for (v in alive) {
            v.transform(v[FORM] ?: Npcs.RAIDS_VANGUARD_MELEE, restoreStats = false)
            v.anim(Seqs.VANGUARD_SPAWN)
            v.huntRange = HUNT
        }
    }

    private val FORMS = intArrayOf(Npcs.RAIDS_VANGUARD_MELEE, Npcs.RAIDS_VANGUARD_RANGED, Npcs.RAIDS_VANGUARD_MAGIC)
    private val RANGE_PROJ =
        intArrayOf(SpotAnims.RAIDS_VANGUARD_RANGE_0, SpotAnims.RAIDS_VANGUARD_RANGE_1, SpotAnims.RAIDS_VANGUARD_RANGE_2)
    private val COMBAT_STATS = intArrayOf(NpcStat.ATTACK, NpcStat.DEFENCE, NpcStat.STRENGTH, NpcStat.RANGED, NpcStat.MAGIC)

    /** VG2: three hits per attack. */
    private const val HITS = 3

    /** Infobox max hits at a maxed solo (magic 22, melee 18, ranged 22) at base level 150. */
    private const val MELEE_MAX = 18
    private const val RANGED_MAX = 22
    private const val MAGIC_MAX = 22
    private const val BASE_LEVEL = 150

    /** VG3: 40% for four or fewer raiders, 33% for five or more (28 May 2020). */
    private const val SMALL_THRESHOLD = 40
    private const val LARGE_THRESHOLD = 33
    private const val LARGE_GROUP = 5
    private const val PERCENT = 100

    /** VG5: shuffles every 20-36 ticks (Mod Ash); 3-6 to raiders in the way. */
    private const val SHUFFLE_MIN = 20
    private const val SHUFFLE_MAX = 36
    private const val PATH_MIN = 3
    private const val PATH_MAX = 6

    /** OPEN-VG6: how long a shuffle lasts and how often the path hurts; OPEN-VG3 the wake range. */
    private const val SHUFFLE_TICKS = 8
    private const val PATH_HIT_EVERY = 2L
    private const val NOTICE = 5
    private const val HUNT = 24
}
