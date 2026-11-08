package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.symbols.Npcs
import tp.symbols.SpotAnims

/**
 * Vespula's portal, grubs and soldiers (vespula.md VE3, VE4, VE6, VE8).
 * - Portal: drains 3 prayer every 2 ticks from everyone within 7 while it is being attacked (3 damage at 0 prayer); its
 *   death removes Vespula and her offspring.
 * - Grub: once stung its meter runs down; at zero it bursts into a vespine soldier.
 * - Soldier: crush, max 14, every 4 ticks; healing Vespula and the portal while alive; at low HP it drops to the ground
 *   (melee only) and explodes after about 33 ticks.
 */
internal object CoxVespulaPortal : Boss(Npcs.RAIDS_VESPULA_PORTAL) {
    private val LAST_HIT = AttrKey<Long>("cox_portal_hit")

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
        CoxKit.loop(npc) { tick(npc) }
    }

    override fun retaliates(npc: NpcRef): Boolean = false

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        npc.setAttackClock(npc.tick + IDLE)
    }

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        npc[LAST_HIT] = npc.tick
        CoxVespula.portalHit(npc)
    }

    override fun died(npc: NpcRef, killer: EntityRef?) {
        // VE8: the portal's end removes Vespula and her offspring.
        for (n in CoxKit.roomNpcs(npc, OFFSPRING)) n.despawn()
    }

    private fun tick(npc: NpcRef) {
        CoxVespulaGrubs.tick(npc)
        val hit = npc[LAST_HIT] ?: return
        if (npc.tick - hit > DRAIN_WINDOW || npc.tick % DRAIN_EVERY != 0L) return
        for (p in CoxKit.raiders(npc)) {
            if (npc.distanceTo(p) > DRAIN_RANGE) continue
            BossCombat.projectile(npc, p, SpotAnims.RAIDS_VESPULA_PORTAL_ATTACK, 1)
            if (p.level(tp.plugin.Stats.PRAYER) > 0) BossCombat.drainPrayer(p, DRAIN) else BossCombat.areaHit(npc, p, DRAIN)
        }
    }

    private val OFFSPRING =
        setOf(
            Npcs.RAIDS_VESPULA_FLYING,
            Npcs.RAIDS_VESPULA_ENRAGED,
            Npcs.RAIDS_VESPULA_WALKING,
            Npcs.RAIDS_VESPULA_CATERPILLAR_HEALTHY,
            Npcs.RAIDS_VESPULA_CATERPILLAR_SICKLY,
            Npcs.RAIDS_VESPULA_CATERPILLAR_INFECTED,
            Npcs.RAIDS_VESPULA_CATERPILLAR_DEAD,
            Npcs.RAIDS_VESPULA_VESPINE_FLYING,
            Npcs.RAIDS_VESPULA_VESPINE_WALKING,
        )

    /** VE6: 3 every 2 ticks within 7. */
    private const val DRAIN = 3
    private const val DRAIN_EVERY = 2L
    private const val DRAIN_RANGE = 7

    /** OPEN-VE6: how long after a hit it still counts as "attacked". */
    private const val DRAIN_WINDOW = 4L
    private const val IDLE = 10
}

/** The lux grubs' meters and bursts (VE3), run from the portal's tick. */
internal object CoxVespulaGrubs {
    fun tick(portal: NpcRef) {
        for (g in CoxKit.roomNpcs(portal, STUNG)) {
            val left = (g[CoxVespula.GRUB_METER] ?: CoxVespula.GRUB_METER_FULL) - 1
            g[CoxVespula.GRUB_METER] = left
            if (left == CoxVespula.GRUB_METER_FULL / 2) g.transform(Npcs.RAIDS_VESPULA_CATERPILLAR_INFECTED, restoreStats = false)
            if (left <= 0) burst(portal, g)
        }
    }

    private fun burst(portal: NpcRef, grub: NpcRef) {
        grub.spotAnim(SpotAnims.RAIDS_VESPULA_VESPINE_HATCH)
        grub.transform(Npcs.RAIDS_VESPULA_CATERPILLAR_DEAD, restoreStats = false)
        grub[CoxVespula.GRUB_METER] = null
        Bosses.summon(portal, Npcs.RAIDS_VESPULA_VESPINE_FLYING, grub.coord, inheritRun = true, scale = true)
    }

    private val STUNG = setOf(Npcs.RAIDS_VESPULA_CATERPILLAR_SICKLY, Npcs.RAIDS_VESPULA_CATERPILLAR_INFECTED)
}

internal object CoxVespineSoldier : Boss(Npcs.RAIDS_VESPULA_VESPINE_FLYING, Npcs.RAIDS_VESPULA_VESPINE_WALKING) {
    private val GROUNDED_AT = AttrKey<Long>("cox_soldier_grounded")

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.RAIDS_VESPULA_VESPINE_FLYING) return
        npc.huntRange = HUNT
        CoxKit.loop(npc) { tick(npc) }
    }

    /** VE4: on the ground it is immune to ranged. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int =
        if (npc.type == Npcs.RAIDS_VESPULA_VESPINE_WALKING && style == HitStyle.RANGED) 0 else damage

    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        if (npc.type != Npcs.RAIDS_VESPULA_VESPINE_FLYING) return
        if (npc.hitpoints * PERCENT <= npc.maxHitpoints * GROUND_BELOW) {
            npc.stopInteraction()
            npc.huntRange = 0
            npc.transform(Npcs.RAIDS_VESPULA_VESPINE_WALKING, restoreStats = false)
            npc[GROUNDED_AT] = npc.tick
        }
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (npc.type != Npcs.RAIDS_VESPULA_VESPINE_FLYING || distance) return
        BossCombat.strike(npc, victim, AttackType.CRUSH, CoxKit.scaledMax(npc, NpcStat.STRENGTH, BASE_STR, MAX_HIT), delay = 0)
    }

    private fun tick(npc: NpcRef) {
        val grounded = npc[GROUNDED_AT]
        if (grounded == null) {
            // VE3: the soldier heals Vespula and the portal while it lives (OPEN-VE6: 1 HP each every 2 ticks).
            if (npc.tick % HEAL_EVERY == 0L) {
                for (n in CoxKit.roomNpcs(npc, HEALED)) n.hitpoints = minOf(n.maxHitpoints, n.hitpoints + HEAL)
                npc.spotAnim(SpotAnims.RAIDS_VESPULA_VESPINE_HEALPORTAL)
            }
            return
        }
        if (npc.tick - grounded < EXPLODE_AFTER) return
        // VE4: it explodes for moderate damage to anyone near (OPEN-VE7: 10-20 within 1).
        npc.spotAnim(SpotAnims.RAIDS_VESPULA_VESPINE_EXPLODE)
        for (p in CoxKit.raiders(npc)) {
            if (TfaKit.gap(npc, p) <= 1) BossCombat.areaHit(npc, p, BossCombat.roll(npc, EXPLODE_MIN, EXPLODE_MAX))
        }
        npc.despawn()
    }

    private val HEALED = setOf(Npcs.RAIDS_VESPULA_FLYING, Npcs.RAIDS_VESPULA_ENRAGED, Npcs.RAIDS_VESPULA_WALKING, Npcs.RAIDS_VESPULA_PORTAL)

    /** Infobox: crush max 14 at strength 150. */
    private const val MAX_HIT = 14
    private const val BASE_STR = 150

    /** OPEN-VE7: the HP at which it drops to the ground. */
    private const val GROUND_BELOW = 30
    private const val PERCENT = 100

    /** VE4: explodes after 20 s (about 33 ticks). */
    private const val EXPLODE_AFTER = 33L
    private const val EXPLODE_MIN = 10
    private const val EXPLODE_MAX = 20
    private const val HEAL_EVERY = 2L
    private const val HEAL = 1
    private const val HUNT = 16
}
