package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Npcs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * The ToA puzzle rooms' special foes (docs/mechanics/raids/toa/puzzles.md AP10-AP13, CR6-CR7, HE5). Room effects go back
 * to raids-toa as [tp.plugin.RaidSignal]s (`toa.apmeken.venom`, `toa.crondis.palm_bite`, `toa.crondis.container_bite`,
 * `toa.het.orb_hit`, `toa.het.dirty_mirror`); the room keeps SharedAttrs.RAID_CARRYING / RAID_TRAP_HITS on players.
 */
internal object ToaPuzzleFoes : Boss(
    Npcs.TOA_PATH_APMEKEN_BABOON_SHAMAN,
    Npcs.TOA_PATH_APMEKEN_BABOON_ZOMBIE,
    Npcs.TOA_PATH_APMEKEN_BABOON_CURSED,
    Npcs.TOA_PATH_APMEKEN_BABOON_THRALL,
    Npcs.TOA_CRONDIS_CROCODILE,
    Npcs.TOA_HET_ORB,
) {
    private val TIMER = AttrKey<Int>("toa_foe_timer")
    private val DIR = AttrKey<Pair<Int, Int>>("toa_orb_dir")
    private val ATTACKED_BY = AttrKey<MutableSet<PlayerRef>>("toa_croc_attackers")

    override fun spawned(npc: NpcRef) {
        npc[TIMER] = 0
        if (npc.type == Npcs.TOA_HET_ORB) {
            // HE5: from an edge straight to the other end (towards the room's middle, then on).
            val t = ToaKit.template(npc, npc.coord)
            val dx = Integer.signum(HET_CENTRE.x - t.x)
            val dz = Integer.signum(HET_CENTRE.z - t.z)
            npc[DIR] = if (kotlin.math.abs(HET_CENTRE.x - t.x) >= kotlin.math.abs(HET_CENTRE.z - t.z)) dx to 0 else 0 to dz
        }
        ToaKit.loop(npc) { tick(npc) }
    }

    override fun retaliates(npc: NpcRef): Boolean = npc.type != Npcs.TOA_HET_ORB && npc.type != Npcs.TOA_CRONDIS_CROCODILE

    /** AP12: Cursed baboons are immune to thralls (the player's thrall NPCs never reach here: Thralls hit as NPC sources). */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        if (npc.type == Npcs.TOA_CRONDIS_CROCODILE && source is PlayerRef) {
            (npc[ATTACKED_BY] ?: HashSet<PlayerRef>().also { npc[ATTACKED_BY] = it }) += source
        }
        if (npc.type == Npcs.TOA_PATH_APMEKEN_BABOON_CURSED && source is NpcRef) return 0
        return damage
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        when (npc.type) {
            Npcs.TOA_PATH_APMEKEN_BABOON_SHAMAN -> shamanAttack(npc, victim)
            Npcs.TOA_PATH_APMEKEN_BABOON_THRALL -> thrallAttack(npc, victim, distance)
            Npcs.TOA_CRONDIS_CROCODILE -> if (victim is PlayerRef && !distance) bite(npc, victim)
            else -> Unit
        }
    }

    private fun tick(npc: NpcRef) {
        val t = (npc[TIMER] ?: 0) + 1
        npc[TIMER] = t
        when (npc.type) {
            Npcs.TOA_PATH_APMEKEN_BABOON_SHAMAN -> shamanTick(npc, t)
            Npcs.TOA_PATH_APMEKEN_BABOON_ZOMBIE -> volatileTick(npc)
            Npcs.TOA_PATH_APMEKEN_BABOON_CURSED -> cursedTick(npc, t)
            Npcs.TOA_CRONDIS_CROCODILE -> crocTick(npc, t)
            Npcs.TOA_HET_ORB -> orbTick(npc)
        }
    }

    // ---- Apmeken

    /** AP10: magic, speed 8, max 7; the hit drains prayer equal to the damage when not protected (OPEN-PZ15). */
    private fun shamanAttack(npc: NpcRef, victim: EntityRef) {
        npc.setAttackClock(npc.tick + SHAMAN_SPEED)
        val raw = if (BossCombat.rolls(npc, victim, AttackType.MAGIC)) BossCombat.roll(npc, 1, SHAMAN_MAX) else 0
        val dealt = ToaKit.afterPrayer(npc, victim, CombatStyle.MAGIC, raw)
        CombatSystem.queueHit(victim, npc, dealt, HitStyle.MAGIC, MAGIC_DELAY) {
            if (victim is PlayerRef && dealt > 0) BossCombat.drainPrayer(victim, dealt)
        }
    }

    /** AP10 (OPEN-PZ15): a thrall every 10 ticks up to 4, none while poisoned or envenomed. */
    private fun shamanTick(npc: NpcRef, t: Int) {
        if (t % THRALL_EVERY != 0 || Poison.state(npc) > 0) return
        if (Bosses.minions(npc, Npcs.TOA_PATH_APMEKEN_BABOON_THRALL).size >= THRALL_CAP) return
        val at = BossCombat.randomTile(npc, npc.coord, 1)
        Bosses.summon(npc, Npcs.TOA_PATH_APMEKEN_BABOON_THRALL, at, huntRange = HUNT, inheritRun = true)
    }

    /** AP11: melee max 5; drains a share of the hit (OPEN-PZ15: half) even when the prayer blocks it. */
    private fun thrallAttack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (distance) return
        val raw = if (BossCombat.rolls(npc, victim, AttackType.CRUSH)) BossCombat.roll(npc, 1, THRALL_MAX) else 0
        val dealt = ToaKit.afterPrayer(npc, victim, CombatStyle.MELEE, raw)
        CombatSystem.queueHit(victim, npc, dealt, HitStyle.MELEE, 0) {
            if (victim is PlayerRef && raw > 0) BossCombat.drainPrayer(victim, raw * THRALL_DRAIN / PERCENT)
        }
    }

    /** AP12 (OPEN-PZ16): it runs at the nearest player and explodes in a 3x3 next to them after a 2-tick fuse. */
    private fun volatileTick(npc: NpcRef) {
        val target = ToaKit.raiders(npc).minByOrNull { npc.distanceTo(it) } ?: return
        if (npc.distanceTo(target) > 1) {
            npc.walkTo(target.coord)
            return
        }
        if (npc[FUSE] == null) {
            npc[FUSE] = npc.tick + VOLATILE_FUSE
            return
        }
        if ((npc[FUSE] ?: 0L) > npc.tick) return
        npc.spotAnimAt(npc.coord, SpotAnims.SPOTANIM_MANDRILL_EXPLODE)
        for (p in ToaKit.on(npc, npc.coord, 1)) ToaKit.areaHit(npc, p, BossCombat.roll(npc, 1, VOLATILE_MAX))
        npc.hitpoints = 0
        CombatSystem.queueHit(npc, null, 1, HitStyle.TYPELESS, 0)
    }

    private val FUSE = AttrKey<Long>("toa_volatile_fuse")

    /** AP13 (OPEN-PZ17): the cursed baboon wanders towards players leaving venom pools (one every 4 ticks). */
    private fun cursedTick(npc: NpcRef, t: Int) {
        val target = ToaKit.raiders(npc).minByOrNull { npc.distanceTo(it) }
        if (target != null && npc.distanceTo(target) > 1) npc.walkTo(target.coord)
        if (t % POOL_EVERY == 0) ToaKit.signal(npc, null, "toa.apmeken.venom")
    }

    // ---- Crondis

    /**
     * CR6: a carrier of a non-empty container within range first, then the palm (any growth), then a container-less
     * player who attacked it; the palm bite (OPEN-PZ23: 10 units) goes to the room.
     */
    private fun crocTick(npc: NpcRef, t: Int) {
        val carriers = ToaKit.raiders(npc).filter { it[SharedAttrs.RAID_CARRYING] == true && npc.distanceTo(it) <= CROC_RANGE }
        val carrier = carriers.minByOrNull { npc.distanceTo(it) }
        if (carrier != null) {
            if (npc.target != carrier) NpcCombat.engage(npc, carrier)
            return
        }
        val palm = npc.npcsWithin(npc.coord, CROC_RANGE * 2).firstOrNull { it.type in GROWN_PALMS }
        if (palm != null) {
            npc.stopInteraction()
            if (npc.distanceTo(palm) > 1) {
                npc.walkTo(palm.coord)
            } else if (t % CROC_SPEED == 0) {
                npc.anim(CROC_ATTACK)
                ToaKit.signal(npc, null, "toa.crondis.palm_bite", PALM_BITE)
            }
            return
        }
        val angry = npc[ATTACKED_BY]?.firstOrNull { !it.isDying && it[SharedAttrs.RAID_CARRYING] != true }
        if (angry != null && npc.target != angry) NpcCombat.engage(npc, angry)
    }

    /** CR7: 18 plus 3 per recent trap hit (cap 36); Protect from Melee drains 12 prayer and lets a third through; carriers lose water. */
    private fun bite(npc: NpcRef, p: PlayerRef) {
        npc.setAttackClock(npc.tick + CROC_SPEED)
        npc.anim(CROC_ATTACK)
        if (!BossCombat.rolls(npc, p, AttackType.CRUSH)) {
            CombatSystem.queueHit(p, npc, 0, HitStyle.MELEE, 0)
            return
        }
        var dmg = minOf(BITE_CAP, BITE_BASE + BITE_PER_TRAP * (p[SharedAttrs.RAID_TRAP_HITS] ?: 0))
        if (p.protects(CombatStyle.MELEE)) {
            BossCombat.drainPrayer(p, BITE_PRAYER)
            dmg /= BITE_PRAYER_DIV
        }
        CombatSystem.queueHit(p, npc, dmg, HitStyle.MELEE, 0)
        if (p[SharedAttrs.RAID_CARRYING] == true) ToaKit.signal(npc, p, "toa.crondis.container_bite")
    }

    // ---- Het

    /** HE5 (OPEN-PZ27): one tile a tick in a straight line; the first player on its tile is hit, a mirror on it turns dirty. */
    private fun orbTick(npc: NpcRef) {
        val (dx, dz) = npc[DIR] ?: return
        val next = CoordGrid(npc.coord.x + dx, npc.coord.z + dz, npc.coord.level)
        val t = ToaKit.template(npc, next)
        if (t.x !in HET_MIN_X..HET_MAX_X || t.z !in HET_MIN_Z..HET_MAX_Z) {
            npc.despawn()
            return
        }
        npc.teleport(next)
        ToaKit.signal(npc, null, "toa.het.dirty_mirror")
        val p = ToaKit.on(npc, next).firstOrNull() ?: return
        ToaKit.signal(npc, p, "toa.het.orb_hit")
    }

    private val GROWN_PALMS = setOf(Npcs.TOA_CRONDIS_TREE_2, Npcs.TOA_CRONDIS_TREE_3, Npcs.TOA_CRONDIS_TREE_4)

    /** AP10 / AP11: shaman speed 8, max 7; thrall max 5; OPEN-PZ15 cap 4, a summon every 10 ticks, 50% drain. */
    private const val SHAMAN_SPEED = 8
    private const val SHAMAN_MAX = 7
    private const val MAGIC_DELAY = 2
    private const val THRALL_EVERY = 10
    private const val THRALL_CAP = 4
    private const val THRALL_MAX = 5
    private const val THRALL_DRAIN = 50

    /** OPEN-PZ16: a 2-tick fuse, 3x3, 15. OPEN-PZ17: a venom pool every 4 ticks. */
    private const val VOLATILE_FUSE = 2L
    private const val VOLATILE_MAX = 15
    private const val POOL_EVERY = 4

    /** CR6 / CR7: aggression range (OPEN-PZ22: 10); speed 7; 18 + 3 a trap hit, cap 36; prayer 12 and a third; palm bite 10. */
    private const val CROC_RANGE = 10
    private const val CROC_SPEED = 7
    private const val BITE_BASE = 18
    private const val BITE_PER_TRAP = 3
    private const val BITE_CAP = 36
    private const val BITE_PRAYER = 12
    private const val BITE_PRAYER_DIV = 3
    private const val PALM_BITE = 10
    private const val CROC_ATTACK = tp.symbols.Seqs.CROC_ATTACK

    /** Het's arena (raids-toa ToaHet: a 21-tile grid from template (3670, 5270), the Seal at its middle). */
    private val HET_CENTRE = CoordGrid(3679, 5279)
    private const val HET_MIN_X = 3670
    private const val HET_MAX_X = 3690
    private const val HET_MIN_Z = 5270
    private const val HET_MAX_Z = 5290

    private const val HUNT = 16
    private const val PERCENT = 100
}
