package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.BankTransfer
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.RunePouch
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * Storage items in the bank (docs/mechanics/bank.md B30-B34): the backpack column's "Fill" / "Empty" for essence pouches,
 * the coal bag and the rune pouch (script 2576), the looting bag's "View" panel (Deposit loot, deposit an item) and the
 * rune pouch's "Configure" panel (deposit runes, ten loadouts with names). The panels are drawn client-side (scripts
 * 490 / 491 set varbit 9727); the server learns of them from the side op 1 click and the panel's buttons.
 */
public class BankStorage : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Bankside.LOOTINGBAG_BANKALL) { depositLoot(this) }
        onButton(Components.Bankside.LOOTINGBAG_ITEMS) { click -> lootItem(this, click) }
        onButton(Components.Bankside.LOOTINGBAG_DISMISS) { this[PANEL] = null }
        onButton(Components.Bankside.RUNEPOUCH_BANKALL) { depositRunes(this) }
        onButton(Components.Bankside.RUNEPOUCH_TOP) { click -> pouchItem(this, click) }
        onButton(Components.Bankside.RUNEPOUCH_DISMISS) { this[PANEL] = null }
        for ((i, load) in LOADS.withIndex()) onButton(load) { RunePouchLoadouts.load(this, i) }
        for ((i, comp) in LOADOUTS.withIndex()) onButton(comp) { click -> RunePouchLoadouts.editSlot(this, i, click.slot) }
        for ((i, comp) in NAMES.withIndex()) onButton(comp) { RunePouchLoadouts.editName(this, i) }
        onButton(Components.Bankside.RUNEPOUCH_SELECT_LIST) { click -> RunePouchLoadouts.pickRune(this, click.slot) }
        onButton(Components.Bankside.RUNEPOUCH_NAME_SELECT_LIST) { click -> RunePouchLoadouts.pickName(this, click.slot) }
    }

    internal companion object {
        /** The bank side panel the client shows: the looting bag or a rune pouch (null = none). */
        private val PANEL = AttrKey<Int>("bank_storage_panel")

        private val LOADS =
            intArrayOf(
                Components.Bankside.RUNEPOUCH_LOAD_A,
                Components.Bankside.RUNEPOUCH_LOAD_B,
                Components.Bankside.RUNEPOUCH_LOAD_C,
                Components.Bankside.RUNEPOUCH_LOAD_D,
                Components.Bankside.RUNEPOUCH_LOAD_E,
                Components.Bankside.RUNEPOUCH_LOAD_F,
                Components.Bankside.RUNEPOUCH_LOAD_G,
                Components.Bankside.RUNEPOUCH_LOAD_H,
                Components.Bankside.RUNEPOUCH_LOAD_I,
                Components.Bankside.RUNEPOUCH_LOAD_J,
            )
        private val LOADOUTS =
            intArrayOf(
                Components.Bankside.RUNEPOUCH_LOADOUT_A,
                Components.Bankside.RUNEPOUCH_LOADOUT_B,
                Components.Bankside.RUNEPOUCH_LOADOUT_C,
                Components.Bankside.RUNEPOUCH_LOADOUT_D,
                Components.Bankside.RUNEPOUCH_LOADOUT_E,
                Components.Bankside.RUNEPOUCH_LOADOUT_F,
                Components.Bankside.RUNEPOUCH_LOADOUT_G,
                Components.Bankside.RUNEPOUCH_LOADOUT_H,
                Components.Bankside.RUNEPOUCH_LOADOUT_I,
                Components.Bankside.RUNEPOUCH_LOADOUT_J,
            )
        private val NAMES =
            intArrayOf(
                Components.Bankside.RUNEPOUCH_NAME_A,
                Components.Bankside.RUNEPOUCH_NAME_B,
                Components.Bankside.RUNEPOUCH_NAME_C,
                Components.Bankside.RUNEPOUCH_NAME_D,
                Components.Bankside.RUNEPOUCH_NAME_E,
                Components.Bankside.RUNEPOUCH_NAME_F,
                Components.Bankside.RUNEPOUCH_NAME_G,
                Components.Bankside.RUNEPOUCH_NAME_H,
                Components.Bankside.RUNEPOUCH_NAME_I,
                Components.Bankside.RUNEPOUCH_NAME_J,
            )

        /** Essence pouch objs per varp 261 bit (script 2576's switch) and the varbit holding each pouch's count. */
        private val POUCH_BITS: List<Pair<IntArray, Int>> =
            listOf(
                intArrayOf(Objs.RCU_POUCH_SMALL) to Varbits.SMALL_ESSENCE_POUCH,
                intArrayOf(Objs.RCU_POUCH_MEDIUM, Objs.RCU_POUCH_MEDIUM_DEGRADE) to Varbits.MEDIUM_ESSENCE_POUCH,
                intArrayOf(Objs.RCU_POUCH_LARGE, Objs.RCU_POUCH_LARGE_DEGRADE) to Varbits.LARGE_ESSENCE_POUCH,
                intArrayOf(Objs.RCU_POUCH_GIANT, Objs.RCU_POUCH_GIANT_DEGRADE) to Varbits.GIANT_ESSENCE_POUCH,
                intArrayOf(Objs.RCU_POUCH_COLOSSAL, Objs.RCU_POUCH_COLOSSAL_DEGRADE) to Varbits.COLOSSAL_ESSENCE_POUCH,
            )
        private val COAL_BAGS = intArrayOf(Objs.COAL_BAG, Objs.COAL_BAG_OPEN)
        private const val COAL_BIT = 5
        private const val COAL_KEY = "bags.coal"

        /** Obj param 595: the backpack column's storage op text ("Fill" / "Empty"). */
        private const val PARAM_STORAGE_OP = 595

        /** Script 4881's ops on panel items: 1 the bank quantity, 2 one, 3 five, 4 ten, 5 last X, 6 X, 7 all, 10 examine. */
        private val PANEL_OPS = (1..7).fold(0L) { acc, op -> acc or IfEvents.op(op) } or IfEvents.op(EXAMINE)
        private const val EXAMINE = 10
        private const val LOOT_SLOTS = 28
        private const val LOADOUT_CHILDREN = 32
        private const val SELECT_CHILDREN = 64
        private const val NAME_CHILDREN = 128

        /** B30: varp 261 for script 2576: bits 0-4 = that essence pouch holds essence ("Empty"); bit 5 = coal bag empty ("Fill"). */
        fun storageBits(p: PlayerRef): Int {
            var bits = 0
            for ((i, pouch) in POUCH_BITS.withIndex()) if (p.varbit(pouch.second) > 0) bits = bits or (1 shl i)
            if ((p.saved(COAL_KEY) ?: 0L) == 0L) bits = bits or (1 shl COAL_BIT)
            return bits
        }

        /** Script 2576's op text for [obj] in the backpack column, or null when it has none. */
        fun extraOpText(p: PlayerRef, obj: Int): String? {
            POUCH_BITS.withIndex().firstOrNull { obj in it.value.first }?.let { (i, _) ->
                return if (storageBits(p) and (1 shl i) != 0) "Empty" else "Fill"
            }
            if (obj in COAL_BAGS) return if (storageBits(p) and (1 shl COAL_BIT) != 0) "Fill" else "Empty"
            return p.objParamString(obj, PARAM_STORAGE_OP)?.takeIf { it.isNotEmpty() }
        }

        fun onOpen(p: PlayerRef) {
            p[PANEL] = null
            p.transmitInv(Invs.LOOTING_BAG)
            p.ifEvents(Components.Bankside.LOOTINGBAG_ITEMS, 0 until LOOT_SLOTS, PANEL_OPS)
            p.ifEvents(Components.Bankside.RUNEPOUCH_TOP, 1..RunePouch.MAX_SLOTS, PANEL_OPS)
            for (c in LOADS) p.ifEvents(c, -1..-1, IfEvents.op(1))
            for (c in NAMES) p.ifEvents(c, -1..-1, IfEvents.op(1))
            for (c in LOADOUTS) p.ifEvents(c, 0 until LOADOUT_CHILDREN, IfEvents.op(1))
            p.ifEvents(Components.Bankside.RUNEPOUCH_SELECT_LIST, 0 until SELECT_CHILDREN, IfEvents.op(1))
            p.ifEvents(Components.Bankside.RUNEPOUCH_NAME_SELECT_LIST, 0 until NAME_CHILDREN, IfEvents.op(1))
        }

        /** Side op 1 on the looting bag ("View") or a rune pouch ("Configure"). */
        fun panelOpened(p: PlayerRef, obj: Int) {
            if (obj in LootingBag.BAGS || obj in RunePouch.ALL) p[PANEL] = obj
            if (obj in LootingBag.BAGS) p.transmitInv(Invs.LOOTING_BAG)
        }

        /** B32: with the rune pouch panel open, how many of [obj] a bank withdrawal puts into the pouch (0 = the backpack). */
        fun pouchRoom(p: PlayerRef, obj: Int): Int {
            if (p[PANEL] !in RunePouch.ALL || !RunePouch.isRune(p, obj)) return 0
            return RunePouch.room(p, obj)
        }

        /** The bank's quantity for op 1 / the last X (the bank's own settings, script 4881's labels). */
        @Suppress("MagicNumber") // script 4881's op numbers, the bank's quantity types and their fixed amounts
        private fun amount(p: PlayerRef, op: Int, then: (Int) -> Unit) {
            val last = maxOf(1, p.varbit(Varbits.BANK_REQUESTEDQUANTITY))
            when (op) {
                1 -> {
                    when (p.varbit(Varbits.BANK_QUANTITY_TYPE)) {
                        1 -> then(5)
                        2 -> then(10)
                        3 -> then(last)
                        4 -> then(Int.MAX_VALUE)
                        else -> then(1)
                    }
                }

                2 -> {
                    then(1)
                }

                3 -> {
                    then(5)
                }

                4 -> {
                    then(10)
                }

                5 -> {
                    then(last)
                }

                6 -> {
                    p.countDialog("Enter amount:") { n -> if (n > 0) then(n) }
                }

                7 -> {
                    then(Int.MAX_VALUE)
                }
            }
        }

        // ---- looting bag panel

        /** B33 "Deposit loot": everything in the bag that the bank takes; unbankable items go to the backpack (Wiki). */
        fun depositLoot(p: PlayerRef) {
            val bag = p.inv(Invs.LOOTING_BAG)
            val objs = (0 until bag.size).map { bag.obj(it) }.filter { it >= 0 }.distinct()
            if (objs.isEmpty()) return p.message("The bag is empty.")
            for (obj in objs) lootToBank(p, obj, Int.MAX_VALUE)
        }

        private fun lootItem(p: PlayerRef, click: ButtonClick) {
            val bag = p.inv(Invs.LOOTING_BAG)
            if (click.slot !in 0 until bag.size || bag.obj(click.slot) < 0) return
            val obj = bag.obj(click.slot)
            if (click.op == EXAMINE) return Examines.item(p, obj, bag.count(click.slot))
            amount(p, click.op) { n -> lootToBank(p, obj, n) }
        }

        private fun lootToBank(p: PlayerRef, obj: Int, requested: Int) {
            val held = p.inv(Invs.LOOTING_BAG).total(obj)
            val n = minOf(requested.toLong(), held).toInt()
            if (n <= 0) return
            p.events.publish(BankTransfer(p, obj, n, deposit = true, source = Invs.LOOTING_BAG))
        }

        // ---- rune pouch panel

        /** B34 "Deposit runes": every rune in the pouch into the bank. */
        fun depositRunes(p: PlayerRef) {
            for (slot in 0 until RunePouch.slots(p)) {
                val (rune, held) = RunePouch.slot(p, slot) ?: continue
                p.events.publish(BankTransfer(p, rune, held, deposit = true, onMoved = { moved -> RunePouch.remove(p, rune, moved) }))
            }
        }

        /** The panel's pouch row: `runepouch_top` children 1..n are pouch slots 0..n-1 (script 4881; seen in game). */
        private fun pouchItem(p: PlayerRef, click: ButtonClick) {
            val slot = click.slot - 1
            if (slot !in 0 until RunePouch.slots(p)) return
            val (rune, held) = RunePouch.slot(p, slot) ?: return
            if (click.op == EXAMINE) return Examines.item(p, rune, held)
            amount(p, click.op) { n ->
                p.events.publish(
                    BankTransfer(p, rune, minOf(n, held), deposit = true, onMoved = { moved ->
                        RunePouch.remove(p, rune, moved)
                    }),
                )
            }
        }
    }
}
