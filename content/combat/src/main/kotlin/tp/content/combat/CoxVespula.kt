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
 * Vespula, CoX combat room (docs/mechanics/raids/cox/vespula.md VE1-VE8).
 * - VE1 / VE2: not aggressive (retaliates); while flying only magic, ranged and halberds harm her; ranged every 3 ticks.
 * - VE3 / VE4: she stings a lux grub now and then; its meter runs down and at zero it bursts into a vespine soldier that heals
 *   her and the portal while it lives; feeding the grub a medivaemia blossom restores the meter (raids-cox handles Feed).
 * - VE5: from 20% HP each hit may land her (sure before 1 HP); grounded about 33 ticks, then she flies and heals.
 * - VE6 / VE7: the portal drains the prayer of everyone within 7 while it is attacked (3 every 2 ticks, 3 damage at 0);
 *   attacking it while she flies enrages her in front of it until it is left alone. Its death ends the room.
 */
internal object CoxVespula : Boss(Npcs.RAIDS_VESPULA_FLYING, Npcs.RAIDS_VESPULA_ENRAGED, Npcs.RAIDS_VESPULA_WALKING) {
    private class State {
        var started = false
        var groundedUntil = -1L
        var nextSting = 0L
        var lastPortalHit = -1L
    }

    private val STATE = AttrKey<State>("cox_vespula")

    private fun state(npc: NpcRef): State = npc[STATE] ?: State().also { npc[STATE] = it }

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.RAIDS_VESPULA_FLYING || npc[STATE] != null) return
        npc.huntRange = 0
        val s = state(npc)
        CoxKit.loop(npc) { tick(npc, s) }
    }

    /** VE2: flying, only magic, ranged and halberds; on the ground everything. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        if (npc.type == Npcs.RAIDS_VESPULA_WALKING || style != HitStyle.MELEE) return damage
        val p = source as? PlayerRef ?: return 0
        return if (p.objName(p.worn(Wear.WEAPON)).contains(HALBERD, ignoreCase = true)) damage else 0
    }

    /** VE5: from 20% each hit may land her. */
    override fun afterHit(npc: NpcRef, source: EntityRef?) {
        if (npc.type == Npcs.RAIDS_VESPULA_WALKING) return
        if (npc.hitpoints * PERCENT <= npc.maxHitpoints * LAND_BELOW && npc.random(LAND_ODDS) == 0) land(npc)
    }

    /** VE5: sure to land before she would die in the air. */
    override fun onLethal(npc: NpcRef): Boolean {
        if (npc.type == Npcs.RAIDS_VESPULA_WALKING) return true.also { npc.hitpoints = 1 }
        npc.hitpoints = 1
        land(npc)
        return true
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (npc.type != Npcs.RAIDS_VESPULA_FLYING) return
        npc.anim(Seqs.VESPULA_ATTACK_RANGED_FLYING)
        val delay = BossCombat.delay(npc, victim, CombatStyle.RANGED)
        BossCombat.projectile(npc, victim, SpotAnims.RAIDS_VESPULA_POISON, delay)
        BossCombat.strike(npc, victim, AttackType.RANGED, CoxKit.scaledMax(npc, NpcStat.RANGED, BASE_RANGED, RANGED_MAX), delay)
    }

    /** Called by the portal when it is hit (VE6 / VE7). */
    fun portalHit(portal: NpcRef) {
        val vespula = CoxKit.roomNpcs(portal, ALL).firstOrNull() ?: return
        val s = state(vespula)
        s.lastPortalHit = portal.tick
        if (vespula.type == Npcs.RAIDS_VESPULA_FLYING) {
            vespula.transform(Npcs.RAIDS_VESPULA_ENRAGED, restoreStats = false)
            vespula.walkTo(portal.coord)
        }
    }

    private fun tick(npc: NpcRef, s: State) {
        val raiders = CoxKit.raiders(npc)
        if (!s.started) {
            // VE1: nothing until the party is in the room.
            if (raiders.isEmpty()) return
            s.started = true
            s.nextSting = npc.tick + STING_EVERY
        }
        when (npc.type) {
            Npcs.RAIDS_VESPULA_WALKING -> {
                if (npc.tick >= s.groundedUntil) takeOff(npc)
            }

            Npcs.RAIDS_VESPULA_ENRAGED -> {
                enraged(npc, s, raiders)
            }

            else -> {
                if (npc.tick >= s.nextSting) sting(npc, s)
            }
        }
    }

    /** VE7: she jabs anyone in melee range for 5-20 typeless; under her 2-8 a tick; calm once the portal is left alone. */
    private fun enraged(npc: NpcRef, s: State, raiders: List<PlayerRef>) {
        for (p in raiders) {
            if (CoxKit.under(npc, p)) {
                BossCombat.areaHit(npc, p, BossCombat.roll(npc, STOMP_MIN, STOMP_MAX))
            } else if (npc.tick % ATTACK_SPEED == 0L && TfaKit.gap(npc, p) <= 1) {
                npc.anim(Seqs.VESPULA_ATTACK_MELEE_FLYING)
                BossCombat.areaHit(npc, p, BossCombat.roll(npc, JAB_MIN, JAB_MAX))
            }
        }
        if (npc.tick - s.lastPortalHit > CALM_AFTER) npc.transform(Npcs.RAIDS_VESPULA_FLYING, restoreStats = false)
    }

    /** VE3: a sting starts a grub's meter (raids-cox runs the meter and the burst). */
    private fun sting(npc: NpcRef, s: State) {
        s.nextSting = npc.tick + STING_EVERY
        val grub = CoxKit.pick(npc, CoxKit.roomNpcs(npc, setOf(Npcs.RAIDS_VESPULA_CATERPILLAR_HEALTHY))) ?: return
        npc.anim(Seqs.VESPULA_ATTACK_MELEE_FLYING)
        grub.transform(Npcs.RAIDS_VESPULA_CATERPILLAR_SICKLY, restoreStats = false)
        grub[GRUB_METER] = GRUB_METER_FULL
    }

    private fun land(npc: NpcRef) {
        val s = state(npc)
        npc.stopInteraction()
        npc.anim(Seqs.VESPULA_LANDING)
        npc.transform(Npcs.RAIDS_VESPULA_WALKING, restoreStats = false)
        s.groundedUntil = npc.tick + GROUNDED_TICKS
    }

    /** VE5: the portal survived: she flies and restores her health (OPEN-VE7: to the landing threshold and a quarter). */
    private fun takeOff(npc: NpcRef) {
        npc.anim(Seqs.VESPULA_TAKEOFF)
        npc.transform(Npcs.RAIDS_VESPULA_FLYING, restoreStats = false)
        npc.hitpoints = minOf(npc.maxHitpoints, npc.hitpoints + npc.maxHitpoints * TAKEOFF_HEAL / PERCENT)
    }

    /** A grub's meter (ticks left), set by the sting; raids-cox's Feed fills it again. */
    val GRUB_METER = tp.plugin.SharedAttrs.RAID_METER

    /** OPEN-VE6: the meter length. */
    const val GRUB_METER_FULL = 60
    private val ALL = setOf(Npcs.RAIDS_VESPULA_FLYING, Npcs.RAIDS_VESPULA_ENRAGED, Npcs.RAIDS_VESPULA_WALKING)
    private const val HALBERD = "halberd"

    /** Infobox: ranged 14 at a maxed solo (ranged 150); attack speed 3. */
    private const val RANGED_MAX = 14
    private const val BASE_RANGED = 150
    private const val ATTACK_SPEED = 3

    /** VE5: below 20% (Page; the Portal page says 23%); OPEN: the odds per hit. */
    private const val LAND_BELOW = 20
    private const val LAND_ODDS = 3
    private const val PERCENT = 100

    /** VE5: about 20 s on the ground. */
    private const val GROUNDED_TICKS = 33L
    private const val TAKEOFF_HEAL = 25

    /** VE7: jabs 5-20, stomp 2-8 a tick. */
    private const val JAB_MIN = 5
    private const val JAB_MAX = 20
    private const val STOMP_MIN = 2
    private const val STOMP_MAX = 8

    /** OPEN-VE6 / OPEN-VE8: the sting interval and how long after the last portal hit she calms. */
    private const val STING_EVERY = 25L
    private const val CALM_AFTER = 4L
}
