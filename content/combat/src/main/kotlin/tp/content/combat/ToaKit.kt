package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.util.CoordGrid

/**
 * Shared ToA fight helpers (docs/mechanics/raids/toa.md TA14, TA28-TA31): the room's raiders, the raid and path levels
 * and the invocations raids-toa puts on every NPC (SharedAttrs.RAID_LEVEL / RAID_PATH_LEVEL / RAID_MODIFIERS), damage
 * scaling (raid level through RaidScale, path level +3 + 5 x level percent for the path bosses), and the prayer
 * invocations (Quiet Prayers: protection 10 percentage points less effective, OPEN-TA7).
 */
internal object ToaKit {
    fun raiders(npc: NpcRef): List<PlayerRef> = CoxKit.raiders(npc)

    fun pathLevel(npc: NpcRef): Int = npc[SharedAttrs.RAID_PATH_LEVEL] ?: 0

    fun raidLevel(npc: NpcRef): Int = npc[SharedAttrs.RAID_LEVEL] ?: 0

    fun has(npc: NpcRef, invocation: String): Boolean = npc[SharedAttrs.RAID_MODIFIERS]?.contains(invocation) == true

    /** TA29: mechanics step at path levels 2 and 4 (capped at 4): 0, 1 or 2. */
    fun step(npc: NpcRef): Int = minOf(pathLevel(npc), MECHANIC_CAP) / 2

    /** A stated base damage scaled by the raid level (cap +150%) and, for the path bosses, the path level (TA28). */
    fun dmg(npc: NpcRef, base: Int, pathScaled: Boolean = true): Int {
        val raid = RaidScale.damage(npc, base)
        val path = pathLevel(npc).coerceIn(0, MAX_PATH)
        return if (pathScaled && path >= 1) raid * (PERCENT + PATH_BASE + PATH_STEP * path) / PERCENT else raid
    }

    /**
     * Protection prayer against a ToA attack: with [rule] BLOCKS the hit is stopped, unless Quiet Prayers is on (then 10%
     * gets through: protection is "10% less effective", OPEN-TA7 flag `toa.quietPrayersPoints`).
     */
    fun afterPrayer(npc: NpcRef, victim: EntityRef, style: CombatStyle, damage: Int, rule: PrayerRule = PrayerRule.BLOCKS): Int {
        if (victim !is PlayerRef || !victim.protects(style) || rule == PrayerRule.IGNORED) return damage
        val quiet = has(npc, QUIET_PRAYERS)
        return when (rule) {
            PrayerRule.BLOCKS -> if (quiet) damage * QUIET_THROUGH / PERCENT else 0
            PrayerRule.HALVES -> if (quiet) damage * (HALF + QUIET_THROUGH) / PERCENT else damage / 2
            PrayerRule.IGNORED -> damage
        }
    }

    /** A ToA attack: accuracy (unless [accurate]), damage 0..[max] (already scaled), the prayer rule, landing after [delay]. */
    fun strike(
        npc: NpcRef,
        victim: EntityRef,
        type: AttackType,
        max: Int,
        delay: Int,
        rule: PrayerRule = PrayerRule.BLOCKS,
        accurate: Boolean = false,
        onLand: ((Int) -> Unit)? = null,
    ) {
        val hit = accurate || BossCombat.rolls(npc, victim, type)
        val raw = if (hit) BossCombat.roll(npc, if (max > 0) 1 else 0, max) else 0
        val damage = afterPrayer(npc, victim, type.style, raw, rule)
        CombatSystem.queueHit(victim, npc, damage, CombatSystem.hitStyle(type.style), delay) { onLand?.invoke(damage) }
    }

    /** Typeless area damage on [p] after [delay] ticks (no prayer). */
    fun areaHit(npc: NpcRef, p: EntityRef, damage: Int, delay: Int = 0) {
        CombatSystem.queueHit(p, npc, damage, tp.plugin.HitStyle.TYPELESS, delay)
    }

    /** The raiders standing on [tile] (or within [radius]) now. */
    fun on(npc: NpcRef, tile: CoordGrid, radius: Int = 0): List<PlayerRef> = BossCombat.playersOn(npc, tile, radius)

    /** Template tile (real map) -> this NPC's instance tile, through its room frame (unrotated ToA squares). */
    fun at(npc: NpcRef, template: CoordGrid): CoordGrid {
        val f = CoxKit.frame(npc) ?: return template
        return CoordGrid(f.origin.x + template.x - f.template.x, f.origin.z + template.z - f.template.z, template.level)
    }

    /** Instance tile -> template tile. */
    fun template(npc: NpcRef, tile: CoordGrid): CoordGrid {
        val f = CoxKit.frame(npc) ?: return tile
        return CoordGrid(f.template.x + tile.x - f.origin.x, f.template.z + tile.z - f.origin.z, tile.level)
    }

    fun loop(npc: NpcRef, body: () -> Unit) = CoxKit.loop(npc, body)

    /** A stun: the player cannot move for [ticks] (TA bosses: banana peels, Akkha, Kephri). */
    fun stun(p: PlayerRef, ticks: Int) {
        p.frozenUntil = maxOf(p.frozenUntil, p.tick + ticks)
        p.stopInteraction()
    }

    /** A boss drop for [p]: into the inventory, else on the ground under them, visible to them only. */
    fun giveOrDrop(p: PlayerRef, obj: Int, label: String) {
        if (p.transaction(label) { add(tp.symbols.Invs.INV, obj, 1) }) return
        p.transaction(label) { createOnGround(obj, 1, p.coord, p.username) }
    }

    /** Akkha's ice quadrant (akkha.md AK7): the player's attacks are one tick slower until [ICE_UNTIL]. */
    val ICE_UNTIL = tp.plugin.AttrKey<Long>("toa_ice_until")

    fun iceSlow(p: PlayerRef): Int = if ((p[ICE_UNTIL] ?: 0L) > p.tick) 1 else 0

    /** A protection prayer that lets [keepPercent] of the hit through (Akkha: melee ~25%, ranged / magic ~15%); Quiet Prayers +10. */
    fun reduced(npc: NpcRef, victim: EntityRef, style: CombatStyle, damage: Int, keepPercent: Int): Int {
        if (victim !is PlayerRef || !victim.protects(style)) return damage
        val keep = keepPercent + if (has(npc, QUIET_PRAYERS)) QUIET_THROUGH else 0
        return damage * minOf(PERCENT, keep) / PERCENT
    }

    /** Reports a fight event to the raid module (points ledger titles, Wardens downs). */
    fun signal(npc: NpcRef, player: PlayerRef?, key: String, amount: Int = 1) {
        npc.events.publish(tp.plugin.RaidSignal(npc, player, key, amount))
    }

    const val QUIET_PRAYERS = "Quiet Prayers"
    const val DEADLY_PRAYERS = "Deadly Prayers"
    private const val QUIET_THROUGH = 10
    private const val HALF = 50
    private const val PERCENT = 100
    private const val PATH_BASE = 3
    private const val PATH_STEP = 5
    private const val MAX_PATH = 6
    private const val MECHANIC_CAP = 4
}
