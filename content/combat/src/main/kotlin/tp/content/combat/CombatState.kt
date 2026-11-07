package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Varps

/** Per-entity combat state (world-scoped attributes). Clocks are absolute ticks. */
internal object Attr {
    /** Next tick the entity may attack (combat-timeline.md C1). */
    val ATTACK_CLOCK = AttrKey<Long>("attack_clock")

    /** Consumption clocks (consumables.md E3): next tick food / potions / karambwans are allowed. */
    val FOOD_CLOCK = AttrKey<Long>("food_clock")
    val POTION_CLOCK = AttrKey<Long>("potion_clock")
    val KARAMBWAN_CLOCK = AttrKey<Long>("karambwan_clock")

    /** Freeze immunity ends at this tick (freeze.md Z2). */
    val FREEZE_IMMUNE_UNTIL = AttrKey<Long>("freeze_immune_until")

    /** Who froze this entity (10-tile break, freeze.md Z5). */
    val FROZEN_BY = AttrKey<EntityRef>("frozen_by")

    /** Set while the death sequence runs; hits are discarded (combat-timeline.md OPEN-C2). */
    val DYING = AttrKey<Boolean>("dying")

    /** Last entity that hit this one, and when (retaliation, PJ timers, kill credit). */
    val LAST_HIT_BY = AttrKey<EntityRef>("last_hit_by")
    val LAST_HIT_TICK = AttrKey<Long>("last_hit_tick")

    /** NPC combat stats not in the cache (OPEN-N1): attack speed and max hit. */
    val NPC_ATTACK_SPEED = AttrKey<Int>("npc_attack_speed")

    /** Prayer: activation tick per prayer id (prayers.md P3) and the drain counter (P2). */
    val PRAYER_ACTIVATED = AttrKey<LongArray>("prayer_activated")
    val PRAYER_DRAIN_COUNTER = AttrKey<Int>("prayer_drain_counter")
}

internal fun EntityRef.attackClock(): Long = this[Attr.ATTACK_CLOCK] ?: 0L

internal fun EntityRef.setAttackClock(tick: Long) {
    this[Attr.ATTACK_CLOCK] = tick
}

internal val EntityRef.isDying: Boolean get() = this[Attr.DYING] == true

/** Worn obj in [slot], -1 if empty. */
internal fun PlayerRef.worn(slot: Int): Int = inv(Invs.WORN).obj(slot)

/** Sum of equipment bonus [param] over worn objs. */
internal fun PlayerRef.bonus(param: Int): Int {
    val inv = inv(Invs.WORN)
    var total = 0
    for (slot in 0 until inv.size) {
        val obj = inv.obj(slot)
        if (obj >= 0) total += objParam(obj, param, 0)
    }
    return total
}

internal fun PlayerRef.weaponCategory(): WeaponCategory {
    val weapon = worn(Wear.WEAPON)
    return if (weapon < 0) Weapons.UNARMED else Weapons.forObj(weapon, objCategory(weapon))
}

/** Current stance slot (varp com_mode 0..3) resolved to the weapon's stance and attack type. */
internal fun PlayerRef.stanceAndType(): Pair<Stance, AttackType> {
    val category = weaponCategory()
    val mode = varp(Varps.COM_MODE)
    return category.slots.getOrNull(mode) ?: category.slots.firstNotNullOf { it }
}

/** Weapon attack speed (param 14, default 4 unarmed; rapid −1: C8). */
internal fun PlayerRef.attackSpeed(): Int {
    val weapon = worn(Wear.WEAPON)
    val base =
        if (weapon <
            0
        ) {
            DEFAULT_SPEED
        } else {
            objParam(weapon, Param.ATTACK_SPEED, WeaponData.of(weapon)?.speed?.takeIf { it > 0 } ?: DEFAULT_SPEED)
        }
    return if (stanceAndType().first == Stance.RAPID) maxOf(1, base - 1) else base
}

/** Attack range in tiles: melee 1; ranged param 13 (+2 longrange, max 10: F11). */
internal fun PlayerRef.attackRange(): Int {
    val (stance, type) = stanceAndType()
    if (type.style != CombatStyle.RANGED) return 1
    val weapon = worn(Wear.WEAPON)
    val base = if (weapon < 0) 1 else objParam(weapon, Param.ATTACK_RANGE, WeaponData.of(weapon)?.range?.takeIf { it > 0 } ?: 1)
    return if (stance == Stance.LONGRANGE) minOf(MAX_RANGE, base + 2) else base
}

internal const val DEFAULT_SPEED = 4
internal const val MAX_RANGE = 10

/** Combat level from base levels (F21). */
internal fun PlayerRef.combatLevel(): Int =
    Formulas.combatLevel(
        baseLevel(Stat.ATTACK),
        baseLevel(Stat.STRENGTH),
        baseLevel(Stat.DEFENCE),
        baseLevel(Stat.HITPOINTS),
        baseLevel(Stat.PRAYER),
        baseLevel(Stat.RANGED),
        baseLevel(Stat.MAGIC),
    )

/** Client stat ids (RuneLite `Skill` order). */
internal object Stat {
    const val ATTACK = 0
    const val DEFENCE = 1
    const val STRENGTH = 2
    const val HITPOINTS = 3
    const val RANGED = 4
    const val PRAYER = 5
    const val MAGIC = 6
}

/** NPC level indices (cache NpcType stats order). */
internal object NpcStat {
    const val ATTACK = 0
    const val DEFENCE = 1
    const val STRENGTH = 2
    const val RANGED = 4
    const val MAGIC = 5
}
