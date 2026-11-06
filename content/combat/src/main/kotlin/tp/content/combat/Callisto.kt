package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Callisto (multi lair) and Artio (singles-plus lair), docs/mechanics/bosses.md CA1–CA8: an AoE melee swipe, a ranged
 * attack at everyone in the den when he can't reach his target, the knockback orb, and the bear traps at 66 % / 33 %.
 */
internal object Callisto : Boss(Npcs.CALLISTO, Npcs.CALLISTO_SINGLES) {
    private class Variant(
        val meleeMin: Int,
        val meleeMax: Int,
        val rangedMax: Int,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
    )

    /** CA1/CA2: Callisto melee 35–55, ranged 31; arena = Callisto's Den (cache flood fill, boss-lairs.md). */
    private val CALLISTO = Variant(35, 55, 31, 3351, 3367, 10317, 10336)

    /** CA1/CA2: Artio melee 15–35, ranged 17; arena = Hunter's End. */
    private val ARTIO = Variant(15, 35, 17, 1751, 1767, 11533, 11552)

    private fun variant(npc: NpcRef) = if (npc.type == Npcs.CALLISTO) CALLISTO else ARTIO

    override val firstFreezeLands: Boolean = true

    private val STAGE = AttrKey<Int>("callisto_trap_stage")
    private val TRAPS = AttrKey<MutableList<CoordGrid>>("callisto_traps")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT_RANGE
        // B4: "will now wait a short period of time after respawning before attacking players" (OPEN-B2: length).
        npc.setAttackClock(npc.tick + RESPAWN_PAUSE)
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val v = variant(npc)
        // CA5: after the 33 % roar, "appear afterwards with every attack".
        if ((npc[STAGE] ?: 0) >= 2) deployTraps(npc, v)
        if (victim is PlayerRef && npc.random(KNOCKBACK_CHANCE) == 0) return knockback(npc, victim)
        if (!distance) melee(npc, victim, v) else ranged(npc, v)
    }

    /** CA1: "AoE capabilities, damaging anyone else near his main target ... can also attack diagonally across". */
    private fun melee(npc: NpcRef, victim: EntityRef, v: Variant) {
        npc.anim(Seqs.NPC_CALLISTO_ATTACK_MELEE01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val type = NpcCombatTable.of(npc.type)?.meleeTypes?.firstOrNull() ?: AttackType.SLASH
        val targets = LinkedHashSet<EntityRef>()
        targets += victim
        for (p in BossCombat.playersOn(npc, victim.coord, radius = 1)) if (npc.distanceTo(p) <= 1) targets += p
        // CA1: "using Protect from Melee reduces the damage by ~50%".
        for (t in targets) BossCombat.strike(npc, t, type, v.meleeMax, 0, min = v.meleeMin, rule = PrayerRule.HALVES)
    }

    /** CA2: "launches crescent-shaped projectiles at everyone in the den if he cannot attack with melee". */
    private fun ranged(npc: NpcRef, v: Variant) {
        npc.anim(Seqs.NPC_CALLISTO_ATTACK_RANGED01)
        for (p in BossCombat.playersIn(npc, v.minX, v.maxX, v.minZ, v.maxZ)) {
            val delay = BossCombat.delay(npc, p, CombatStyle.RANGED)
            CombatSystem.announceAttack(npc, p, CombatStyle.RANGED)
            BossCombat.projectile(npc, p, SpotAnims.FX_CALLISTO_RANGED_PROJECTILE, delay)
            BossCombat.strike(npc, p, AttackType.RANGED, v.rangedMax, delay)
        }
    }

    /**
     * CA3: the orb hits "if Protect from Magic is not activated when the attack visually hits", splashing a 3×3 around
     * the target; it pushes 3 tiles away from Callisto: 5 damage, +15 per tile it can't complete, 5 when stopped by a
     * trap (plus the trap).
     */
    private fun knockback(npc: NpcRef, victim: PlayerRef) {
        npc.anim(Seqs.NPC_CALLISTO_ATTACK_MAGIC01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC)
        victim.queueHit(delay) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in BossCombat.playersOn(npc, victim.coord, radius = 1)) {
                if (p.protects(CombatStyle.MAGIC)) continue
                push(npc, p)
            }
        }
    }

    private fun push(npc: NpcRef, p: PlayerRef) {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val dx = Integer.signum(p.coord.x - cx)
        var dz = Integer.signum(p.coord.z - cz)
        if (dx == 0 && dz == 0) dz = 1
        var at = p.coord
        var moved = 0
        var trapped = false
        while (moved < KNOCKBACK_TILES && npc.canStep(at, dx, dz)) {
            at = CoordGrid(at.x + dx, at.z + dz, at.level)
            moved++
            if (isTrap(npc, at)) {
                trapped = true
                break
            }
        }
        if (moved > 0) p.teleport(at)
        val damage = if (trapped) KNOCKBACK_BASE else KNOCKBACK_BASE + KNOCKBACK_STEP * (KNOCKBACK_TILES - moved)
        CombatSystem.applyHit(p, npc, damage, HitStyle.MAGIC)
        if (trapped) trigger(npc, p, at)
    }

    // ---- CA4/CA5 bear traps

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        val stage = npc[STAGE] ?: 0
        val hp = npc.hitpoints * THIRDS
        val next =
            when {
                stage == 0 && hp <= npc.maxHitpoints * 2 -> 1
                stage == 1 && hp <= npc.maxHitpoints -> 2
                else -> return
            }
        npc[STAGE] = next
        roar(npc)
    }

    /** CA4: "lifts his head, becomes unfrozen, and deploys bear traps". */
    private fun roar(npc: NpcRef) {
        npc.anim(Seqs.NPC_CALLISTO_ENRAGED01)
        npc.spotAnim(SpotAnims.FX_CALLISTO_ENRAGE)
        npc.frozenUntil = npc.tick
        deployTraps(npc, variant(npc))
    }

    /** A fresh set of traps on free arena tiles, replacing the old set (CA5). OPEN-CA1: count and lifetime. */
    private fun deployTraps(npc: NpcRef, v: Variant) {
        clearTraps(npc)
        val traps = ArrayList<CoordGrid>(TRAP_COUNT)
        repeat(TRAP_COUNT) {
            val t = CoordGrid(v.minX + npc.random(v.maxX - v.minX + 1), v.minZ + npc.random(v.maxZ - v.minZ + 1), npc.coord.level)
            if (!npc.isWalkable(t) || t in traps || inside(npc, t)) return@repeat
            npc.addLoc(Locs.CALLISTO_TRAP_LOC, t, TRAP_SHAPE, 0, TRAP_TICKS)
            npc.spotAnimAt(t, SpotAnims.FX_CALLISTO_TRAP)
            traps += t
        }
        npc[TRAPS] = traps
        watchTraps(npc)
    }

    private fun inside(npc: NpcRef, t: CoordGrid) = t.x - npc.coord.x in 0 until npc.size && t.z - npc.coord.z in 0 until npc.size

    private fun isTrap(npc: NpcRef, t: CoordGrid) = npc.locAt(t, TRAP_SHAPE)?.id == Locs.CALLISTO_TRAP_LOC

    /** Checks every tick for players standing on a trap. */
    private fun watchTraps(npc: NpcRef) {
        npc.queueHit(1) {
            val traps = npc[TRAPS] ?: return@queueHit
            if (!npc.isInWorld || npc.isDying) return@queueHit
            traps.removeAll { !isTrap(npc, it) }
            if (traps.isEmpty()) return@queueHit
            for (t in traps.toList()) for (p in BossCombat.playersOn(npc, t)) trigger(npc, p, t)
            watchTraps(npc)
        }
    }

    /** CA4: "Getting caught in a trap will deal up to 15 damage and briefly bind the player." */
    private fun trigger(npc: NpcRef, p: PlayerRef, t: CoordGrid) {
        if (!isTrap(npc, t)) return
        npc.revertLoc(t, TRAP_SHAPE)
        npc[TRAPS]?.remove(t)
        CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 1, TRAP_MAX), HitStyle.TYPELESS)
        Freezes.freeze(p, npc, BIND_TICKS)
    }

    private fun clearTraps(npc: NpcRef) {
        val traps = npc[TRAPS] ?: return
        for (t in traps) if (isTrap(npc, t)) npc.revertLoc(t, TRAP_SHAPE)
        traps.clear()
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        clearTraps(npc)
        Bosses.creditLairKill(npc)
    }

    /** OPEN-B3: boss hunt range = the whole den. */
    private const val HUNT_RANGE = 20

    /** OPEN-B2: the post-respawn pause isn't published. */
    private const val RESPAWN_PAUSE = 5

    /** OPEN-CA2: "uses his knockback attack less frequently" (no rate published). */
    private const val KNOCKBACK_CHANCE = 6
    private const val KNOCKBACK_TILES = 3
    private const val KNOCKBACK_BASE = 5
    private const val KNOCKBACK_STEP = 15

    private const val THIRDS = 3
    private const val TRAP_MAX = 15
    private const val TRAP_COUNT = 10
    private const val TRAP_TICKS = 25

    /** Ground decoration layer: traps never displace scenery. */
    private const val TRAP_SHAPE = 22

    /** OPEN-CA3: "briefly bind" — no length published. */
    private const val BIND_TICKS = 3
}
