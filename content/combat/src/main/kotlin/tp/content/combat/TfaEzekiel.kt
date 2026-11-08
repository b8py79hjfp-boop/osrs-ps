package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Ezekiel, TFA room 2 west (docs/mechanics/raids/tfa/ezekiel.md EZ1-EZ3): a melee cleave around him with one safe
 * tile behind, magic at everyone when he cannot reach his target, a cross of lightning every second melee attack
 * followed by Animated Swords (a sword that hits him stuns him, a miss heals him), and below 35% the finale: three
 * lightning patterns per 11-tick cycle while he takes less damage.
 */
internal object TfaEzekiel : Boss(Npcs.TFA_EZEKIEL) {
    private class State {
        var melees = 0
        var enraged = false

        /** EZ3: the centre of the 3x3 the players are forced into for the finale. */
        var box: CoordGrid? = null
    }

    private val STATE = AttrKey<State>("tfa_ezekiel")

    private fun state(npc: NpcRef): State {
        TfaKit.scale(npc, HP)
        return npc[STATE] ?: State().also { npc[STATE] = it }
    }

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val s = state(npc)
        if (s.enraged) {
            finale(npc, s)
            return
        }
        if (distance) {
            magic(npc)
            return
        }
        cleave(npc, victim)
        s.melees++
        if (s.melees % LIGHTNING_EVERY == 0) {
            lightning(npc, delay = 1)
            swords(npc)
        }
    }

    /** EZ1: melee cleaves "almost 360°"; the safe tile is directly behind him, opposite the tank. */
    private fun cleave(npc: NpcRef, tank: EntityRef) {
        npc.anim(Seqs.NPC_EZEKIEL_MELEE01)
        CombatSystem.announceAttack(npc, tank, CombatStyle.MELEE)
        val safe = behind(npc, tank.coord)
        for (p in TfaKit.arena(npc, MELEE_SCAN)) {
            if (TfaKit.gap(npc, p) != 1 || (p.coord.x == safe.x && p.coord.z == safe.z)) continue
            BossCombat.strike(npc, p, AttackType.SLASH, MAX_HIT, 0)
        }
    }

    private fun behind(npc: NpcRef, tank: CoordGrid): CoordGrid {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val dx = -Integer.signum(tank.x - cx)
        val dz = -Integer.signum(tank.z - cz)
        val reach = npc.size / 2 + 1
        return CoordGrid(cx + dx * reach, cz + dz * reach, npc.coord.level)
    }

    /** EZ1: "if he cannot reach his target he casts magic at everyone" (OPEN-EZ6: max 40 each). */
    private fun magic(npc: NpcRef) {
        npc.anim(Seqs.NPC_EZEKIEL_MAGIC01)
        npc.spotAnim(SpotAnims.EZEKIEL_MAGIC_ATTACK_VFX01)
        for (p in TfaKit.arena(npc)) {
            val delay = BossCombat.delay(npc, p, CombatStyle.MAGIC)
            BossCombat.strike(npc, p, AttackType.MAGIC, MAGIC_MAX, delay) { p.spotAnim(SpotAnims.EZEKIEL_MAGIC_ATTACK_IMPACT_VFX01) }
        }
        // EZ1: "after a magic attack Ezekiel may mark another player as the tank" (OPEN-EZ9: 1 in 3, a random other).
        val others = TfaKit.arena(npc).filter { it != npc.target }
        if (others.isNotEmpty() && npc.random(TANK_SWITCH_ONE_IN) == 0) {
            val tank = others[npc.random(others.size)]
            npc.say("${tank.username}!")
            NpcCombat.engage(npc, tank)
        }
    }

    /**
     * EZ2: a cross-shaped lightning AoE on every player in two bursts 2 ticks apart (dodge diagonally or in an L).
     * OPEN-EZ7: arm length 2, 0-30 typeless per burst.
     */
    private fun lightning(npc: NpcRef, delay: Int, struck: MutableSet<String>? = null) {
        npc.anim(Seqs.NPC_EZEKIEL_SPECIAL01)
        val tiles = TfaKit.arena(npc).flatMap { TfaKit.cross(it.coord, CROSS_ARM) }.distinct()
        for (t in tiles) npc.spotAnimAt(t, SpotAnims.VFX_EZEKIEL_STAR_EXPLOSION_MARKER01)
        for (burst in 0 until BURSTS) {
            npc.queueHit(delay + burst * BURST_GAP) {
                if (!npc.isInWorld) return@queueHit
                for (t in tiles) npc.spotAnimAt(t, SpotAnims.VFX_EZEKIEL_STAR_EXPLOSION01)
                for (p in TfaKit.arena(npc)) {
                    if (!TfaKit.on(p, tiles)) continue
                    struck?.add(p.username)
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, LIGHTNING_MAX), HitStyle.TYPELESS)
                }
            }
        }
    }

    /**
     * EZ2: 3-4 Animated Swords charge along a player's row or column (charge damage at most 80). A sword that crosses
     * Ezekiel stuns him; one that misses heals him. OPEN-EZ1: heal 50; OPEN-EZ8: telegraph 4 ticks, stun 5 ticks,
     * swords line up on random players.
     */
    private fun swords(npc: NpcRef) {
        val players = TfaKit.arena(npc)
        if (players.isEmpty()) return
        val count = SWORDS_MIN + npc.random(SWORD_VARIANTS)
        repeat(count) {
            val p = players[npc.random(players.size)]
            val horizontal = npc.random(2) == 0
            val line = line(p.coord, horizontal)
            val start =
                if (horizontal) {
                    CoordGrid(p.coord.x - SWORD_REACH, p.coord.z, p.coord.level)
                } else {
                    CoordGrid(
                        p.coord.x,
                        p.coord.z - SWORD_REACH,
                        p.coord.level,
                    )
                }
            val sword = Bosses.summon(npc, Npcs.TFA_EZEKIEL_SWORD, start, huntRange = 0)
            // EZ2: a "perfect dodge" (in the sword's path, out of it when it lands) restores 3% special energy.
            val inPath = TfaKit.arena(npc).filter { TfaKit.on(it, line) }
            sword.spotAnim(SpotAnims.VFX_EZEKIEL_SWORD_TELEGRAPH01)
            sword.queueHit(SWORD_TELEGRAPH) {
                if (!npc.isInWorld) return@queueHit
                for (v in TfaKit.arena(npc)) {
                    if (TfaKit.on(v, line)) CombatSystem.applyHit(v, npc, BossCombat.roll(npc, 0, SWORD_MAX), HitStyle.TYPELESS)
                }
                for (v in inPath) if (!v.isDying && !TfaKit.on(v, line)) TfaKit.addSpecEnergy(v, DODGE_ENERGY)
                if (line.any { TfaKit.under(npc, it) }) {
                    npc.anim(Seqs.NPC_EZEKIEL_WEAKEN01)
                    npc.setAttackClock(maxOf(npc.attackClock(), npc.tick + STUN_TICKS))
                } else {
                    npc.anim(Seqs.NPC_EZEKIEL_HEAL01)
                    npc.spotAnim(SpotAnims.EZEKIEL_HEAL_SPOTANIM_VFX01)
                    npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + SWORD_HEAL)
                }
                sword.despawn()
            }
        }
    }

    private fun line(c: CoordGrid, horizontal: Boolean): List<CoordGrid> =
        (-SWORD_REACH..SWORD_REACH).map { d -> if (horizontal) CoordGrid(c.x + d, c.z, c.level) else CoordGrid(c.x, c.z + d, c.level) }

    /**
     * EZ3: the finale: three lightning patterns in a row over the players' 3x3 on an 11-tick cycle (OPEN-EZ4: the 11
     * ticks cover all three). Each pattern leaves one column of the box safe; the middle column is never the last
     * pattern of a burst. Anyone outside the box, or in the box off the safe column, is struck (OPEN-EZ7: 0-30).
     * Dodging all three restores 9% special energy.
     */
    private fun finale(npc: NpcRef, s: State) {
        val box = s.box ?: return
        npc.anim(Seqs.NPC_EZEKIEL_FINALE_LOOP01)
        npc.spotAnim(SpotAnims.VFX_EZEKIEL_FINALE_CAST01)
        val struck = HashSet<String>()
        val watching = TfaKit.arena(npc)
        val patterns = IntArray(FINALE_PATTERNS) { npc.random(BOX) }
        if (patterns.last() == MIDDLE) patterns[FINALE_PATTERNS - 1] = if (npc.random(2) == 0) 0 else BOX - 1
        for ((k, safeColumn) in patterns.withIndex()) {
            val safeX = box.x - 1 + safeColumn
            val struckTiles =
                (-1..1).flatMap { dx -> (-1..1).map { dz -> CoordGrid(box.x + dx, box.z + dz, box.level) } }.filter {
                    it.x !=
                        safeX
                }
            val at = 1 + k * FINALE_GAP
            npc.queueHit(at) { for (t in struckTiles) npc.spotAnimAt(t, SpotAnims.VFX_EZEKIEL_SWORD_TELEGRAPH01) }
            npc.queueHit(at + BURST_GAP) {
                if (!npc.isInWorld) return@queueHit
                for (t in struckTiles) npc.spotAnimAt(t, SpotAnims.VFX_EZEKIEL_FINALE_IMPACT_MEDIUM01)
                for (p in TfaKit.arena(npc)) {
                    val inBox = kotlin.math.abs(p.coord.x - box.x) <= 1 && kotlin.math.abs(p.coord.z - box.z) <= 1
                    if (inBox && p.coord.x == safeX) continue
                    struck += p.username
                    CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, LIGHTNING_MAX), HitStyle.TYPELESS)
                }
            }
        }
        npc.queueHit(1 + (FINALE_PATTERNS - 1) * FINALE_GAP + BURST_GAP + 1) {
            for (p in watching) if (!p.isDying && p.username !in struck) TfaKit.addSpecEnergy(p, FINALE_DODGE_ENERGY)
        }
        npc.setAttackClock(npc.tick + FINALE_CYCLE)
    }

    /**
     * EZ3: below 35% he moves to one side, the players are forced into a 3x3 in front of him and three more swords
     * appear (OPEN-EZ10: he moves 6 tiles south of his spawn; the box is 4 tiles north of him).
     */
    private fun enrage(npc: NpcRef, s: State) {
        s.enraged = true
        npc.anim(Seqs.NPC_EZEKIEL_FINALE01)
        val side = CoordGrid(npc.spawn.x, npc.spawn.z - SIDE_SHIFT, npc.spawn.level)
        npc.teleport(side)
        val box = CoordGrid(side.x + npc.size / 2, side.z + npc.size + BOX_GAP, side.level)
        s.box = box
        for ((k, p) in TfaKit.arena(npc).withIndex()) {
            p.teleport(CoordGrid(box.x - 1 + k % BOX, box.z - 1 + (k / BOX) % BOX, box.level))
        }
        for (k in 0 until FINALE_SWORDS) {
            val at = CoordGrid(box.x - 2 + k * 2, box.z + 2, box.level)
            Bosses.summon(npc, Npcs.TFA_EZEKIEL_SWORD_NOOP, at, huntRange = 0).spotAnim(SpotAnims.VFX_EZEKIEL_SWORD_DROP01)
        }
        npc.setAttackClock(npc.tick + 1)
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val s = state(npc)
        if (!s.enraged && npc.hitpoints * PERCENT < npc.maxHitpoints * ENRAGE_PERCENT) enrage(npc, s)
    }

    /** EZ3 / OPEN-EZ5: in the finale he takes less damage the lower his health: x(0.5 + HP fraction). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val s = state(npc)
        if (!s.enraged) return damage
        val factor = HALF + npc.hitpoints.toDouble() / maxOf(1, npc.maxHitpoints)
        return (damage * factor).toInt()
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
    }

    /** ezekiel.md data: HP for 2..8 players. */
    private val HP = intArrayOf(3000, 4350, 5610, 6870, 8130, 9390, 10650)

    private const val HUNT = 20
    private const val MELEE_SCAN = 6
    private const val MAX_HIT = 77
    private const val MAGIC_MAX = 40
    private const val LIGHTNING_EVERY = 2
    private const val CROSS_ARM = 2
    private const val BURSTS = 2
    private const val BURST_GAP = 2
    private const val LIGHTNING_MAX = 30
    private const val SWORDS_MIN = 3

    /** EZ2: "3-4 Animated Swords". */
    private const val SWORD_VARIANTS = 2
    private const val SWORD_REACH = 10
    private const val SWORD_TELEGRAPH = 4
    private const val SWORD_MAX = 80
    private const val SWORD_HEAL = 50

    /** EZ2 / EZ3: 3% and 9% of the 1,000-unit special bar. */
    private const val DODGE_ENERGY = 30
    private const val FINALE_DODGE_ENERGY = 90
    private const val STUN_TICKS = 5
    private const val FINALE_PATTERNS = 3
    private const val FINALE_SWORDS = 3
    private const val BOX = 3
    private const val MIDDLE = 1
    private const val SIDE_SHIFT = 6
    private const val BOX_GAP = 1
    private const val TANK_SWITCH_ONE_IN = 3
    private const val FINALE_GAP = 3
    private const val FINALE_CYCLE = 11
    private const val ENRAGE_PERCENT = 35
    private const val PERCENT = 100
    private const val HALF = 0.5
}
