package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Venenatis (multi lair) and Spindel (singles-plus lair), docs/mechanics/bosses.md VN1–VN6: melee when adjacent,
 * otherwise eight ranged then eight magic attacks, moving every four; spiderlings on the first ranged attack of a
 * cycle; the sticky web on the third magic attack.
 */
internal object Venenatis : Boss(Npcs.VENENATIS, Npcs.VENENATIS_SINGLES) {
    private class Variant(val spiderling: Int, val minX: Int, val maxX: Int, val minZ: Int, val maxZ: Int)

    /** Arenas: the Silk Chasm and the Web Chasm (boss-lairs.md). */
    private val VENENATIS = Variant(Npcs.VENENATIS_SPIDERLING, 3410, 3436, 10190, 10217)
    private val SPINDEL = Variant(Npcs.SPINDEL_SPIDERLING, 1618, 1644, 11534, 11561)

    private fun variant(npc: NpcRef) = if (npc.type == Npcs.VENENATIS) VENENATIS else SPINDEL

    /** Distance attacks made so far (the 16-attack ranged/magic cycle). */
    private val CYCLE = AttrKey<Int>("venenatis_cycle")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT_RANGE
        npc.setAttackClock(npc.tick + RESPAWN_PAUSE)
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val def = NpcCombatTable.of(npc.type)
        // VN1: melee "is the only normal attack she will use if the player is within melee distance".
        if (!distance) {
            npc.anim(Seqs.NPC_VENENATIS_MELEE_01)
            CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
            hit(npc, victim, AttackType.STAB, def?.maxFor(CombatStyle.MELEE) ?: 0, 0)
            return
        }
        val n = npc[CYCLE] ?: 0
        npc[CYCLE] = n + 1
        val step = n % CYCLE_LENGTH
        val ranged = step < HALF_CYCLE
        if (ranged && step == 0) summonSpiderlings(npc)
        if (!ranged && step == HALF_CYCLE + WEB_ON_MAGIC) {
            web(npc, victim)
        } else {
            val style = if (ranged) CombatStyle.RANGED else CombatStyle.MAGIC
            npc.anim(if (ranged) Seqs.NPC_VENENATIS_RANGED_01 else Seqs.NPC_VENENATIS_MAGIC_01)
            CombatSystem.announceAttack(npc, victim, style)
            val delay = BossCombat.delay(npc, victim, style)
            BossCombat.projectile(
                npc,
                victim,
                if (ranged) SpotAnims.FX_VENENATIS_RANGED_PROJECTILE else SpotAnims.FX_VENENATIS_MAGIC_PROJECTILE,
                delay,
            )
            hit(npc, victim, if (ranged) AttackType.RANGED else AttackType.MAGIC, def?.maxFor(style) ?: 0, delay)
        }
        // VN1: she repositions every four distance attacks (OPEN-VN1: where to — a random tile near her).
        if ((n + 1) % MOVE_EVERY == 0) reposition(npc, victim)
    }

    /**
     * VN3: with spiderlings alive her hits gain a damage component that ignores protection prayers ("increase her
     * damage and allow her to hit through prayers"), OPEN-VN2: 3 per spiderling, capped at 30.
     */
    private fun hit(npc: NpcRef, victim: EntityRef, type: AttackType, max: Int, delay: Int) {
        val spiderlings = Bosses.minions(npc).size
        val base = if (BossCombat.rolls(npc, victim, type)) BossCombat.roll(npc, 1, max) else 0
        val bonus = minOf(SPIDERLING_BONUS_CAP, spiderlings * SPIDERLING_BONUS)
        val damage = BossCombat.afterPrayer(victim, type.style, base, PrayerRule.BLOCKS) + bonus
        CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(type.style), delay)
    }

    private fun reposition(npc: NpcRef, victim: EntityRef) {
        val to = BossCombat.randomTile(npc, npc.coord, MOVE_RADIUS)
        npc.stopInteraction()
        npc.walkTo(to)
        npc.queueHit(MOVE_TICKS) { if (npc.isInWorld && !npc.isDying && !victim.isDying) NpcCombat.engage(npc, victim) }
    }

    /** VN3: "Venenatis will occasionally call some spiderlings"; Spindel at most 10 (OPEN-VN3: 2 per call). */
    private fun summonSpiderlings(npc: NpcRef) {
        val v = variant(npc)
        val alive = Bosses.minions(npc).size
        val players = BossCombat.playersIn(npc, v.minX, v.maxX, v.minZ, v.maxZ)
        if (players.isEmpty()) return
        repeat(minOf(SPIDERLINGS_PER_CALL, MAX_SPIDERLINGS - alive)) {
            val s = Bosses.summon(npc, v.spiderling, BossCombat.randomTile(npc, npc.coord, SPIDERLING_SPREAD))
            NpcCombat.engage(s, players[npc.random(players.size)])
        }
    }

    /**
     * VN4: the web lands on the target's tile and "expand[s] into a 7x5 AoE ... any player caught in it will take rapid
     * hits of 3 damage, have their prayer drained by 3, and run energy by 10"; aranea boots negate the first tick.
     */
    private fun web(npc: NpcRef, victim: EntityRef) {
        npc.anim(Seqs.NPC_VENENATIS_MAGIC_01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        val delay = BossCombat.delay(npc, victim, CombatStyle.MAGIC)
        BossCombat.projectile(npc, victim, SpotAnims.FX_VENENATIS_WEB_PROJECTILE, delay)
        val centre = victim.coord
        npc.queueHit(delay) {
            if (!npc.isInWorld) return@queueHit
            npc.spotAnimAt(centre, SpotAnims.FX_VENENATIS_WEB_IMPACT)
            for (dx in -WEB_HALF_X..WEB_HALF_X) {
                for (dz in -WEB_HALF_Z..WEB_HALF_Z) {
                    val t = CoordGrid(centre.x + dx, centre.z + dz, centre.level)
                    if (!npc.isWalkable(t)) continue
                    val (id, angle) = webPiece(dx, dz)
                    npc.addLoc(id, t, WEB_SHAPE, angle, WEB_TICKS)
                }
            }
            webTick(npc, centre, 0)
        }
    }

    /** Centre, edge or corner piece and its rotation for an offset inside the 7×5 web. */
    private fun webPiece(dx: Int, dz: Int): Pair<Int, Int> {
        val edgeX = kotlin.math.abs(dx) == WEB_HALF_X
        val edgeZ = kotlin.math.abs(dz) == WEB_HALF_Z
        return when {
            edgeX && edgeZ -> Locs.WBR_VENENATIS_WEB_CORNER to cornerAngle(dx, dz)
            edgeX -> Locs.WBR_VENENATIS_WEB_EDGE to if (dx < 0) ANGLE_WEST else ANGLE_EAST
            edgeZ -> Locs.WBR_VENENATIS_WEB_EDGE to if (dz > 0) ANGLE_NORTH else ANGLE_SOUTH
            else -> Locs.WBR_VENENATIS_WEB_CENTRE to 0
        }
    }

    private fun cornerAngle(dx: Int, dz: Int) =
        when {
            dx < 0 && dz > 0 -> ANGLE_WEST
            dx > 0 && dz > 0 -> ANGLE_NORTH
            dx > 0 -> ANGLE_EAST
            else -> ANGLE_SOUTH
        }

    private fun webTick(npc: NpcRef, centre: CoordGrid, elapsed: Int) {
        if (elapsed >= WEB_TICKS || !npc.isInWorld) return
        for (p in BossCombat.playersOn(npc, centre, radius = WEB_HALF_X)) {
            if (kotlin.math.abs(p.coord.z - centre.z) > WEB_HALF_Z) continue
            if (elapsed == 0 && p.worn(Wear.FEET) == Objs.ARANEA_BOOTS) continue
            CombatSystem.applyHit(p, npc, WEB_DAMAGE, HitStyle.TYPELESS)
            BossCombat.drainPrayer(p, WEB_PRAYER_DRAIN)
            p.runEnergy = maxOf(0, p.runEnergy - WEB_RUN_DRAIN)
        }
        npc.queueHit(1) { webTick(npc, centre, elapsed + 1) }
    }

    /** Spindel: "Player and thrall damage is slightly reduced while spiderlings are alive" (OPEN-VN4: 10 %). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        if (npc.type == Npcs.VENENATIS_SINGLES && source is PlayerRef && Bosses.minions(npc).isNotEmpty()) {
            damage * SPINDEL_REDUCED / PERCENT
        } else {
            damage
        }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
        Bosses.creditLairKill(npc)
    }

    private const val HUNT_RANGE = 20
    private const val RESPAWN_PAUSE = 5
    private const val CYCLE_LENGTH = 16
    private const val HALF_CYCLE = 8

    /** The third magic attack (index 2 of the magic half). */
    private const val WEB_ON_MAGIC = 2
    private const val MOVE_EVERY = 4
    private const val MOVE_RADIUS = 3
    private const val MOVE_TICKS = 3
    private const val SPIDERLINGS_PER_CALL = 2
    private const val MAX_SPIDERLINGS = 10
    private const val SPIDERLING_SPREAD = 3
    private const val SPIDERLING_BONUS = 3
    private const val SPIDERLING_BONUS_CAP = 30
    private const val WEB_HALF_X = 3
    private const val WEB_HALF_Z = 2

    /** OPEN-VN5: web lifetime isn't published. */
    private const val WEB_TICKS = 8
    private const val WEB_SHAPE = 22
    private const val WEB_DAMAGE = 3
    private const val WEB_PRAYER_DRAIN = 3

    /** "run energy by 10" (%) in the 0–10,000 run energy scale. */
    private const val WEB_RUN_DRAIN = 1000
    private const val SPINDEL_REDUCED = 90
    private const val PERCENT = 100
    private const val ANGLE_WEST = 0
    private const val ANGLE_NORTH = 1
    private const val ANGLE_EAST = 2
    private const val ANGLE_SOUTH = 3
}

/** VN3: spiderlings "drain prayer on every hit regardless if prayed against or hits". */
internal object Spiderling : Boss(Npcs.VENENATIS_SPIDERLING, Npcs.SPINDEL_SPIDERLING) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        BossCombat.strike(npc, victim, AttackType.STAB, NpcCombatTable.of(npc.type)?.maxFor(CombatStyle.MELEE) ?: 0, 0)
        if (victim is PlayerRef) victim.queueHit(0) { BossCombat.drainPrayer(victim, PRAYER_DRAIN) }
    }

    private const val PRAYER_DRAIN = 1
}
