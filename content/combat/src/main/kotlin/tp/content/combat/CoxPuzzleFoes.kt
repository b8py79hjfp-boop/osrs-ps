package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.HitStyle
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Npcs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * The jewelled crabs (docs/mechanics/raids/cox/puzzles.md CR2, CR6, CR8): a hit changes a crab's colour for about 8
 * ticks (melee red, magic blue, ranged green) and does no damage; they are aggressive with a short range and a fairly
 * accurate melee (OPEN-PZ1: their stats and max hit). raids-cox runs the orb and the crystals.
 */
internal object CoxCrab : Boss(
    Npcs.RAIDS_LASERCRABS_CRAB_GREY,
    Npcs.RAIDS_LASERCRABS_CRAB_RED,
    Npcs.RAIDS_LASERCRABS_CRAB_GREEN,
    Npcs.RAIDS_LASERCRABS_CRAB_BLUE,
) {
    /** The tick a crab's colour fades (raids-cox's Smash sets it too). */
    val COLOUR_UNTIL = tp.plugin.SharedAttrs.RAID_UNTIL

    override fun spawned(npc: NpcRef) {
        if (npc.type != Npcs.RAIDS_LASERCRABS_CRAB_GREY || npc[COLOUR_UNTIL] != null) return
        npc[COLOUR_UNTIL] = 0L
        npc.huntRange = AGGRO
        CoxKit.loop(npc) {
            if (npc.type != Npcs.RAIDS_LASERCRABS_CRAB_GREY && npc.tick >= (npc[COLOUR_UNTIL] ?: 0L)) {
                npc.transform(Npcs.RAIDS_LASERCRABS_CRAB_GREY, restoreStats = false)
            }
        }
    }

    /** CR2: the hit's style sets the colour; crabs take no damage. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        val colour =
            when (style) {
                HitStyle.MELEE -> Npcs.RAIDS_LASERCRABS_CRAB_RED
                HitStyle.MAGIC -> Npcs.RAIDS_LASERCRABS_CRAB_BLUE
                HitStyle.RANGED -> Npcs.RAIDS_LASERCRABS_CRAB_GREEN
                HitStyle.TYPELESS -> return 0
            }
        colour(npc, colour, COLOUR_TICKS)
        return 0
    }

    fun colour(npc: NpcRef, type: Int, ticks: Int) {
        if (npc.type != type) npc.transform(type, restoreStats = false)
        npc[COLOUR_UNTIL] = npc.tick + ticks
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (distance) return
        BossCombat.strike(npc, victim, AttackType.CRUSH, MAX_HIT, delay = 0)
    }

    /** CR2: "about 5 s" (8 ticks; OPEN-PZ3 the rounding). */
    private const val COLOUR_TICKS = 8

    /** OPEN-PZ1: the crab's max hit and aggression range ("low"). */
    private const val MAX_HIT = 10
    private const val AGGRO = 3
}

/**
 * The Ice demon (puzzles.md ID5-ID7): once the storm is gone it fights at attack speed 3 with a 3 x 3 snowball (ranged)
 * or Ice Burst (magic); it reads the target's protection prayer (Missiles: only the snowball, Magic: only the burst);
 * all damage to it is cut by 67% except fire spells and demonbane weapons.
 */
internal object CoxIceDemon : Boss(Npcs.RAIDS_ICEDEMON_NONCOMBAT, Npcs.RAIDS_ICEDEMON_COMBAT) {
    override fun spawned(npc: NpcRef) {
        npc.huntRange = if (npc.type == Npcs.RAIDS_ICEDEMON_COMBAT) HUNT else 0
    }

    override fun retaliates(npc: NpcRef): Boolean = npc.type == Npcs.RAIDS_ICEDEMON_COMBAT

    /** ID1: not attackable in the storm; ID7: -67% except fire spells (OPEN-PZ11: all magic) and demonbane weapons. */
    override fun incoming(npc: NpcRef, source: EntityRef?, damage: Int, style: HitStyle): Int {
        if (npc.type != Npcs.RAIDS_ICEDEMON_COMBAT) return 0
        if (style == HitStyle.MAGIC) return damage
        val p = source as? PlayerRef
        if (p != null && DEMONBANE.any { p.objName(p.worn(Wear.WEAPON)).contains(it, ignoreCase = true) }) return damage
        return damage * (PERCENT - REDUCTION) / PERCENT
    }

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        if (npc.type != Npcs.RAIDS_ICEDEMON_COMBAT) return
        val p = victim as? PlayerRef
        val magic =
            when {
                p?.protects(CombatStyle.MAGIC) == true -> true
                p?.protects(CombatStyle.RANGED) == true -> false
                else -> npc.random(2) == 0
            }
        val style = if (magic) CombatStyle.MAGIC else CombatStyle.RANGED
        val delay = BossCombat.delay(npc, victim, style)
        BossCombat.projectile(npc, victim, SpotAnims.RAIDS_ICEDEMON_ICEBALL_TRAVEL, delay)
        val at: CoordGrid = victim.coord
        val max =
            if (magic) CoxKit.scaledMax(npc, NpcStat.MAGIC, BASE, MAX_HIT) else CoxKit.scaledMax(npc, NpcStat.RANGED, BASE, MAX_HIT)
        npc.queueHit(delay, strong = false) {
            if (!npc.isInWorld) return@queueHit
            for (t in CoxKit.inArea(npc, at)) {
                t.spotAnim(SpotAnims.RAIDS_ICEDEMON_ICEBALL_HIT)
                val dmg = BossCombat.afterPrayer(t, style, BossCombat.roll(npc, 0, max), PrayerRule.HALVES)
                BossCombat.areaHit(npc, t, dmg, if (magic) HitStyle.MAGIC else HitStyle.RANGED)
                // ID6 Strat: Ice Burst "has a chance to freeze" (OPEN-PZ10: 1 in 4, 8 ticks).
                if (magic && npc.random(FREEZE_ODDS) == 0) t.frozenUntil = maxOf(t.frozenUntil, t.tick + FREEZE_TICKS)
            }
        }
    }

    /** Weapons with demonbane (Wiki "Demonbane weapons"). */
    private val DEMONBANE = listOf("silverlight", "darklight", "arclight", "emberlight", "scorching bow", "burning claws", "purging staff")
    private const val REDUCTION = 67
    private const val PERCENT = 100

    /** OPEN-PZ10: damage ("Varies"), freeze odds and duration. */
    private const val MAX_HIT = 30
    private const val BASE = 390
    private const val FREEZE_ODDS = 4
    private const val FREEZE_TICKS = 8L
    private const val HUNT = 20
}

/**
 * The tightrope room's deathly rangers and mages (puzzles.md TR2, TR4, TR5): passive until a raider crosses the rope,
 * then they focus the crosser (unless already fighting); ranged 70 / magic 22 at a maxed solo every 4 ticks; protection
 * prayers halve the damage.
 */
internal object CoxDeathly : Boss(Npcs.RAIDS_TIGHTROPE_RANGER, Npcs.RAIDS_TIGHTROPE_MAGE) {
    /** NPC attr (set by raids-cox): the raider crossing the rope, whom they turn on. */
    val FOCUS = tp.plugin.SharedAttrs.RAID_FOCUS

    override fun spawned(npc: NpcRef) {
        npc.huntRange = 0
    }

    override fun hunts(npc: NpcRef, player: PlayerRef): Boolean = npc[FOCUS]?.equals(player.username, ignoreCase = true) ?: false

    override fun attack(npc: NpcRef, victim: EntityRef, distance: Boolean) {
        val ranger = npc.type == Npcs.RAIDS_TIGHTROPE_RANGER
        val style = if (ranger) CombatStyle.RANGED else CombatStyle.MAGIC
        val delay = BossCombat.delay(npc, victim, style)
        val max =
            if (ranger) {
                // CM 99 (TR4) is the scaled 70 at CM's ranged level, so the solo value scales into it.
                CoxKit.scaledMax(npc, NpcStat.RANGED, BASE_LEVEL, RANGER_MAX)
            } else {
                CoxKit.scaledMax(npc, NpcStat.MAGIC, BASE_LEVEL, MAGE_MAX)
            }
        BossCombat.strike(npc, victim, if (ranger) AttackType.RANGED else AttackType.MAGIC, max, delay, rule = PrayerRule.HALVES)
    }

    /** TR4 / infobox: ranger 70 (CM 99), mage 22 (CM OPEN-PZ12), at ranged / magic 210. */
    private const val RANGER_MAX = 70
    private const val MAGE_MAX = 22
    private const val BASE_LEVEL = 210
}
