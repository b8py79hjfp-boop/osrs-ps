package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Vet'ion (multi lair) and Calvar'ion (singles-plus lair), docs/mechanics/bosses.md VE1–VE7: lightning bolts on 3×3
 * areas, the shield bash, two skeleton hellhounds at half health (full immunity while they live) and the enraged
 * second form after the first form's hitpoints run out.
 */
internal object Vetion : Boss(Npcs.VETION, Npcs.VETION_2, Npcs.VETION_SINGLE, Npcs.VETION_2_SINGLE) {
    private class Variant(
        val first: Int,
        val second: Int,
        val houndJnr: Int,
        val houndSnr: Int,
        val lightningMax: Int,
        val bashMax: Int,
        val minX: Int,
        val maxX: Int,
        val minZ: Int,
        val maxZ: Int,
    )

    /** VE1/VE2: Vet'ion lightning 44, bash 35; arena = Vet'ion's Rest (boss-lairs.md). */
    private val VETION =
        Variant(Npcs.VETION, Npcs.VETION_2, Npcs.VETION_HELLHOUND_JNR, Npcs.VETION_HELLHOUND_SNR, 44, 35, 3286, 3304, 10190, 10214)

    /** VE1/VE2: Calvar'ion lightning 26, bash 26; arena = the Skeletal Tomb. */
    private val CALVARION =
        Variant(
            Npcs.VETION_SINGLE,
            Npcs.VETION_2_SINGLE,
            Npcs.VETION_HELLHOUND_JNR_SINGLES,
            Npcs.VETION_HELLHOUND_SNR_SINGLES,
            26,
            26,
            1878,
            1896,
            11534,
            11558,
        )

    private fun variant(npc: NpcRef) = if (npc.type == Npcs.VETION || npc.type == Npcs.VETION_2) VETION else CALVARION

    private fun enraged(npc: NpcRef) = npc.type == Npcs.VETION_2 || npc.type == Npcs.VETION_2_SINGLE

    /** 0 = no hounds yet this form, 1 = summoning, 2 = summoned. */
    private val HOUNDS = AttrKey<Int>("vetion_hounds")
    private val ENRAGED_AT = AttrKey<Long>("vetion_enraged_at")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = HUNT_RANGE
        npc.setAttackClock(npc.tick + RESPAWN_PAUSE)
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val v = variant(npc)
        // VE2: the bash "is only used when in melee distance". OPEN-VE1: the choice between attacks isn't published.
        when (if (distance) 0 else npc.random(MELEE_CHOICES)) {
            0 -> lightning(npc, victim, v)
            1 -> bash(npc, victim, v)
            else -> melee(npc, victim)
        }
    }

    private fun melee(npc: NpcRef, victim: EntityRef) {
        npc.anim(if (enraged(npc)) Seqs.NPC_VETION_ATTACK_MELEE_02 else Seqs.NPC_VETION_ATTACK_MELEE_01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val def = NpcCombatTable.of(npc.type)
        BossCombat.strike(npc, victim, def?.meleeTypes?.firstOrNull() ?: AttackType.SLASH, def?.maxFor(CombatStyle.MELEE) ?: 0, 0)
    }

    /**
     * VE1: "When soloing, one bolt will always target the player's tile or an adjacent tile, while four more will target
     * random tiles ... In groups, one bolt will appear for every player in the chamber, capped at seven." Tiles glow
     * first; a bolt hits its 3×3 area, harder on the centre tile.
     */
    private fun lightning(npc: NpcRef, victim: EntityRef, v: Variant) {
        npc.anim(if (enraged(npc)) Seqs.NPC_VETION_ATTACK_MAGIC_02 else Seqs.NPC_VETION_ATTACK_MAGIC_01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MAGIC)
        val players = BossCombat.playersIn(npc, v.minX, v.maxX, v.minZ, v.maxZ)
        val bolts = ArrayList<CoordGrid>()
        if (players.size <= 1) {
            bolts += near(npc, victim.coord)
            repeat(SOLO_RANDOM_BOLTS) { bolts += BossCombat.randomTile(npc, centre(v, npc.coord.level), ARENA_RADIUS) }
        } else {
            for (p in players.take(MAX_GROUP_BOLTS)) bolts += near(npc, p.coord)
        }
        for (b in bolts) strikeBolt(npc, b, v, orange = enraged(npc))
    }

    private fun near(npc: NpcRef, c: CoordGrid) = CoordGrid(c.x + npc.random(3) - 1, c.z + npc.random(3) - 1, c.level)

    private fun centre(v: Variant, level: Int) = CoordGrid((v.minX + v.maxX) / 2, (v.minZ + v.maxZ) / 2, level)

    private fun strikeBolt(npc: NpcRef, tile: CoordGrid, v: Variant, orange: Boolean) {
        npc.spotAnimAt(tile, if (orange) SpotAnims.FX_VETION_ATTACK_MAGIC_02 else SpotAnims.FX_VETION_ATTACK_MAGIC_01)
        npc.queueHit(LIGHTNING_DELAY) {
            if (!npc.isInWorld) return@queueHit
            npc.spotAnimAt(tile, if (orange) SpotAnims.FX_VETION_ATTACK_MAGIC_IMPACT_02 else SpotAnims.FX_VETION_ATTACK_MAGIC_IMPACT_01)
            for (p in BossCombat.playersOn(npc, tile, radius = 1)) {
                val centre = p.coord.x == tile.x && p.coord.z == tile.z
                // OPEN-VE2: "light damage" on the edge isn't quantified; a quarter of the max.
                val max = if (centre) v.lightningMax else v.lightningMax / EDGE_DIVISOR
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, max), HitStyle.MAGIC)
            }
        }
    }

    /**
     * VE2: shadows mark "two tiles thick on a side or diagonal, or one tile thick on every side"; anyone still there
     * takes up to the max and is "unable to attack for the next 4.8 seconds" (8 ticks).
     */
    private fun bash(npc: NpcRef, victim: EntityRef, v: Variant) {
        npc.anim(if (enraged(npc)) Seqs.NPC_VETION_DEFEND_SHIELD_02 else Seqs.NPC_VETION_DEFEND_SHIELD_01)
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val area = if (npc.random(2) == 0) ring(npc) else side(npc, victim)
        for (t in area) npc.spotAnimAt(t, SpotAnims.FX_VETION_ATTACK_MELEE)
        npc.queueHit(BASH_DELAY) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (p in BossCombat.playersOn(npc, npc.coord, radius = npc.size + 1)) {
                if (area.none { it.x == p.coord.x && it.z == p.coord.z }) continue
                CombatSystem.applyHit(p, npc, BossCombat.roll(npc, 0, v.bashMax), HitStyle.MELEE)
                p.setAttackClock(maxOf(p.attackClock(), p.tick + BASH_STUN))
            }
        }
    }

    /** One tile thick on every side, plus underneath. */
    private fun ring(npc: NpcRef): List<CoordGrid> {
        val c = npc.coord
        val out = ArrayList<CoordGrid>()
        for (x in c.x - 1..c.x + npc.size) for (z in c.z - 1..c.z + npc.size) out += CoordGrid(x, z, c.level)
        return out
    }

    /** Two tiles thick on the side (or diagonal) facing [victim], plus underneath. */
    private fun side(npc: NpcRef, victim: EntityRef): List<CoordGrid> {
        val c = npc.coord
        val s = npc.size
        val dx = Integer.signum(victim.coord.x - (c.x + s / 2))
        val dz = Integer.signum(victim.coord.z - (c.z + s / 2))
        val out = ArrayList<CoordGrid>()
        val xs =
            when (dx) {
                -1 -> c.x - 2 until c.x + s
                1 -> c.x..c.x + s + 1
                else -> c.x until c.x + s
            }
        val zs =
            when (dz) {
                -1 -> c.z - 2 until c.z + s
                1 -> c.z..c.z + s + 1
                else -> c.z until c.z + s
            }
        for (x in xs) for (z in zs) out += CoordGrid(x, z, c.level)
        return out
    }

    // ---- VE3 hellhounds, VE4 enraged form

    /** VE3: "complete damage immunity while present"; OPEN-VE3: 90 % resistance while they're being summoned. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        when (npc[HOUNDS] ?: 0) {
            1 -> damage / RESIST_DIVISOR
            2 -> if (Bosses.minions(npc).isNotEmpty()) 0 else damage
            else -> damage
        }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        if ((npc[HOUNDS] ?: 0) != 0 || npc.hitpoints * 2 > npc.maxHitpoints) return
        npc[HOUNDS] = 1
        val v = variant(npc)
        npc.queueHit(HOUND_SUMMON_DELAY) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            for (type in intArrayOf(v.houndJnr, v.houndSnr)) {
                val hound = Bosses.summon(npc, type, BossCombat.randomTile(npc, npc.coord, HOUND_SPREAD))
                val target = BossCombat.playersIn(npc, v.minX, v.maxX, v.minZ, v.maxZ).randomOrNull(npc) ?: continue
                NpcCombat.engage(hound, target)
            }
            npc[HOUNDS] = 2
        }
    }

    /** VE4: the first form's death restores full health in the enraged form; the second form dies. */
    override fun onLethal(npc: NpcRef): Boolean {
        if (enraged(npc)) return false
        val v = variant(npc)
        Bosses.dismissMinions(npc)
        npc.transform(v.second, restoreStats = true)
        npc[HOUNDS] = 0
        npc[Attr.NPC_ATTACK_SPEED] = ENRAGED_SPEED
        npc.anim(Seqs.NPC_VETION_ENRAGE_01)
        val started = npc.tick
        npc[ENRAGED_AT] = started
        // "summon orange lightning bolts ... in a circle around him".
        val c = CoordGrid(npc.coord.x + npc.size / 2, npc.coord.z + npc.size / 2, npc.coord.level)
        for ((dx, dz) in RING) strikeBolt(npc, CoordGrid(c.x + dx, c.z + dz, c.level), v, orange = true)
        // OPEN-VE4: the enraged form reverts to the first form, healed, if not killed within 500 ticks.
        npc.queueHit(ENRAGE_TICKS) {
            if (!npc.isInWorld || npc.isDying || npc[ENRAGED_AT] != started) return@queueHit
            Bosses.dismissMinions(npc)
            npc.transform(v.first, restoreStats = true)
            npc[HOUNDS] = 0
            npc[Attr.NPC_ATTACK_SPEED] = null
        }
        return true
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        Bosses.dismissMinions(npc)
        Bosses.creditLairKill(npc)
    }

    private fun <T> List<T>.randomOrNull(npc: NpcRef): T? = if (isEmpty()) null else this[npc.random(size)]

    private const val HUNT_RANGE = 20
    private const val RESPAWN_PAUSE = 5
    private const val MELEE_CHOICES = 3
    private const val SOLO_RANDOM_BOLTS = 4
    private const val MAX_GROUP_BOLTS = 7
    private const val ARENA_RADIUS = 9

    /** OPEN-VE5: the glow-to-strike time isn't published. */
    private const val LIGHTNING_DELAY = 3
    private const val EDGE_DIVISOR = 4
    private const val BASH_DELAY = 2

    /** VE2: "unable to attack for the next 4.8 seconds" = 8 ticks. */
    private const val BASH_STUN = 8
    private const val RESIST_DIVISOR = 10
    private const val HOUND_SUMMON_DELAY = 2
    private const val HOUND_SPREAD = 3

    /** Enraged form attack speed (bosses.md VE4: "faster attack speed"; Wiki infobox 5). */
    private const val ENRAGED_SPEED = 5
    private const val ENRAGE_TICKS = 500
    private val RING = listOf(-3 to -3, 0 to -3, 3 to -3, -3 to 0, 3 to 0, -3 to 3, 0 to 3, 3 to 3)
}

/** Vet'ion's / Calvar'ion's skeleton hellhounds (VE3): plain melee minions. */
internal object VetionHound : Boss(
    Npcs.VETION_HELLHOUND_JNR,
    Npcs.VETION_HELLHOUND_SNR,
    Npcs.VETION_HELLHOUND_JNR_SINGLES,
    Npcs.VETION_HELLHOUND_SNR_SINGLES,
) {
    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        CombatSystem.announceAttack(npc, victim, CombatStyle.MELEE)
        val def = NpcCombatTable.of(npc.type)
        BossCombat.strike(npc, victim, def?.meleeTypes?.firstOrNull() ?: AttackType.CRUSH, def?.maxFor(CombatStyle.MELEE) ?: 0, 0)
    }
}
