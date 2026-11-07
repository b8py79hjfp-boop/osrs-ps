/*
 * Adapted from OpenRune-Server `content/skills/magic/rune-pouch` RunePouch (ISC licence, Copyright (c) 2025 RS Mod;
 * DECISIONS D-077): contents in the client-visible pouch varbits, rune types as `rune_compact_ids` (enum 55) values.
 */
package tp.plugin

import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The rune pouch's contents (docs/mechanics/rune-pouch.md RP1-RP4): slot n's rune is `rune_pouch_type_n` (the rune's
 * compact id, cache enum 55) and its amount `rune_pouch_quantity_n`, so the pouch interface, the spellbook and the
 * RuneLite plugin all read the same varbits. A rune pouch holds 3 kinds, the divine rune pouch 4, at most 16,000 each.
 * Only a pouch in the backpack counts.
 */
public object RunePouch {
    /** Wiki "Rune pouch": "can store 16,000 of three types of runes". */
    public const val CAPACITY: Int = 16_000

    public val REGULAR: Set<Int> = setOf(Objs.BH_RUNE_POUCH, Objs.BH_RUNE_POUCH_TROUVER)
    public val DIVINE: Set<Int> = setOf(Objs.DIVINE_RUNE_POUCH, Objs.DIVINE_RUNE_POUCH_TROUVER)
    public val ALL: Set<Int> = REGULAR + DIVINE

    private val TYPE =
        intArrayOf(Varbits.RUNE_POUCH_TYPE_1, Varbits.RUNE_POUCH_TYPE_2, Varbits.RUNE_POUCH_TYPE_3, Varbits.RUNE_POUCH_TYPE_4)
    private val QUANTITY =
        intArrayOf(
            Varbits.RUNE_POUCH_QUANTITY_1,
            Varbits.RUNE_POUCH_QUANTITY_2,
            Varbits.RUNE_POUCH_QUANTITY_3,
            Varbits.RUNE_POUCH_QUANTITY_4,
        )

    /** Cache enum 55 `rune_compact_ids`: rune obj -> compact id. */
    private const val COMPACT_ENUM = 55

    public const val MAX_SLOTS: Int = 4
    private const val DIVINE_SLOTS = 4
    private const val REGULAR_SLOTS = 3

    private fun compact(p: PlayerRef): Map<Int, Int> = p.enumInts(COMPACT_ENUM)

    /** Whether [obj] is a rune the pouch takes. */
    public fun isRune(p: PlayerRef, obj: Int): Boolean = obj in compact(p)

    /** Slots of the pouch in the backpack: 4 (divine), 3, or 0 without one. */
    public fun slots(p: PlayerRef): Int {
        val backpack = p.inv(Invs.INV)
        return when {
            DIVINE.any { backpack.total(it) > 0 } -> DIVINE_SLOTS
            REGULAR.any { backpack.total(it) > 0 } -> REGULAR_SLOTS
            else -> 0
        }
    }

    /** Slot [slot]'s (rune obj, amount), or null when empty. */
    public fun slot(p: PlayerRef, slot: Int): Pair<Int, Int>? {
        val amount = p.varbit(QUANTITY[slot])
        val type = p.varbit(TYPE[slot])
        if (amount <= 0 || type <= 0) return null
        val rune = compact(p).entries.firstOrNull { it.value == type }?.key ?: return null
        return rune to amount
    }

    public fun set(p: PlayerRef, slot: Int, rune: Int, amount: Int) {
        val empty = rune < 0 || amount <= 0
        p.setVarbit(TYPE[slot], if (empty) 0 else compact(p)[rune] ?: 0)
        p.setVarbit(QUANTITY[slot], if (empty) 0 else amount)
    }

    /** How many of [rune] the pouch holds (0 without a pouch in the backpack). */
    public fun count(p: PlayerRef, rune: Int): Int {
        val n = slots(p)
        return (0 until n).sumOf { i -> slot(p, i)?.takeIf { it.first == rune }?.second ?: 0 }
    }

    /** The slot holding [rune], else the first empty one, else null. */
    public fun slotFor(p: PlayerRef, rune: Int, slots: Int = slots(p)): Int? =
        (0 until slots).firstOrNull { slot(p, it)?.first == rune } ?: (0 until slots).firstOrNull { slot(p, it) == null }

    /** How many more of [rune] fit (0 when no slot can take it). */
    public fun room(p: PlayerRef, rune: Int): Int {
        val target = slotFor(p, rune) ?: return 0
        return CAPACITY - (slot(p, target)?.second ?: 0)
    }

    /** Adds [amount] of [rune] (call after checking [room]). */
    public fun add(p: PlayerRef, rune: Int, amount: Int) {
        val target = slotFor(p, rune) ?: return
        set(p, target, rune, (slot(p, target)?.second ?: 0) + amount)
    }

    /** Removes up to [amount] of [rune]; returns how many came out. */
    public fun remove(p: PlayerRef, rune: Int, amount: Int): Int {
        var left = amount
        for (i in 0 until slots(p)) {
            val (r, held) = slot(p, i) ?: continue
            if (r != rune || left <= 0) continue
            val take = minOf(left, held)
            set(p, i, r, held - take)
            left -= take
        }
        return amount - left
    }

    /** Swaps two slots (drag in the pouch interface). */
    public fun swap(p: PlayerRef, a: Int, b: Int) {
        val x = slot(p, a)
        val y = slot(p, b)
        set(p, a, y?.first ?: -1, y?.second ?: 0)
        set(p, b, x?.first ?: -1, x?.second ?: 0)
    }
}
