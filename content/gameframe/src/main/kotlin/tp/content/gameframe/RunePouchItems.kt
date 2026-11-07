/*
 * Adapted from OpenRune-Server `content/skills/magic/rune-pouch` RunePouchScript (ISC licence, Copyright (c) 2025 RS
 * Mod; DECISIONS D-077): the pouch interface's ops, quantities and messages.
 */
package tp.content.gameframe

import tp.plugin.BankItemOp
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.RunePouch
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Rune pouch and divine rune pouch in the backpack (docs/mechanics/rune-pouch.md RP6-RP10): "Open" (interface 190),
 * "Empty", runes used on the pouch, the divine pouch's "Revert", and stitching a thread of Elidinis into a pouch.
 */
public class RunePouchItems : ContentPlugin {
    override fun ContentScope.register() {
        for (pouch in RunePouch.ALL) {
            onItemOp(pouch, "Open") { open(this) }
            onItemOp(pouch, "Empty") { empty(this) }
            for (rune in RUNES) onUseOnItem(rune, pouch) { store(this, rune, Int.MAX_VALUE) }
        }
        events.on<BankItemOp> { op ->
            if (op.obj in RunePouch.ALL) {
                op.handled = true
                if ((0 until RunePouch.slots(op.player)).none { RunePouch.slot(op.player, it) != null }) return@on op.player.message(EMPTY)
                BankStorage.depositRunes(op.player)
            }
        }
        for (divine in RunePouch.DIVINE) onItemOp(divine, "Revert") { click -> revert(this, click) }
        onUseOnItem(Objs.THREAD_OF_ELIDINIS, Objs.BH_RUNE_POUCH) { upgrade(this) }
        onButton(Components.Rune_Pouch.POUCH) { click -> pouchOp(this, click) }
        onButton(Components.Rune_Pouch.INVENTORY) { click -> inventoryOp(this, click) }
        onButtonDrag(Components.Rune_Pouch.POUCH) { d ->
            val n = RunePouch.slots(this)
            if (d.toComponent == Components.Rune_Pouch.POUCH && d.fromSlot in 0 until n && d.toSlot in 0 until n) {
                RunePouch.swap(this, d.fromSlot, d.toSlot)
            }
        }
        onButtonDrag(Components.Rune_Pouch.INVENTORY) { d ->
            val size = inv(Invs.INV).size
            if (d.toComponent == Components.Rune_Pouch.INVENTORY && d.fromSlot in 0 until size && d.toSlot in 0 until size) {
                transaction("rune pouch inventory drag") { swap(Invs.INV, d.fromSlot, d.toSlot) }
            }
        }
        onButton(Components.Rune_Pouch._1) { setVarbit(Varbits.RUNE_POUCH_SELECTEDQUANTITY, QTY_ONE) }
        onButton(Components.Rune_Pouch._5) { setVarbit(Varbits.RUNE_POUCH_SELECTEDQUANTITY, QTY_FIVE) }
        onButton(Components.Rune_Pouch.ALL) { setVarbit(Varbits.RUNE_POUCH_SELECTEDQUANTITY, QTY_ALL) }
        onButton(Components.Rune_Pouch.X) { click ->
            if (click.op == OP_SET_X || varbit(Varbits.RUNE_POUCH_CUSTOMQUANTITY) <= 0) {
                countDialog("Enter amount:") { n ->
                    if (n > 0) setVarbit(Varbits.RUNE_POUCH_CUSTOMQUANTITY, minOf(n, MAX_CUSTOM))
                    setVarbit(Varbits.RUNE_POUCH_SELECTEDQUANTITY, QTY_CUSTOM)
                }
            } else {
                setVarbit(Varbits.RUNE_POUCH_SELECTEDQUANTITY, QTY_CUSTOM)
            }
        }
    }

    internal companion object {
        /** Cache enum 55 `rune_compact_ids` keys: every rune the pouch takes. */
        val RUNES =
            intArrayOf(
                556,
                555,
                557,
                554,
                558,
                562,
                560,
                565,
                564,
                561,
                563,
                559,
                566,
                9075,
                4695,
                4698,
                4696,
                4699,
                4694,
                4697,
                21880,
                28929,
                30843,
            )

        private const val QTY_ONE = 0
        private const val QTY_FIVE = 1
        private const val QTY_ALL = 2
        private const val QTY_CUSTOM = 3
        private const val OP_SET_X = 2

        /** The custom quantity varbit's width (OPEN-RP1: 16,000 is the pouch's own limit). */
        private const val MAX_CUSTOM = 16_000

        /** Pouch slots 0..3 and backpack slots: ops 1-6 (left click, 1, 5, X-last, X, All) and 10 (Examine), dragging. */
        private val SLOT_EVENTS: Long =
            (1..6).fold(0L) { acc, op -> acc or IfEvents.op(op) } or IfEvents.op(EXAMINE) or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET
        private const val EXAMINE = 10

        /** Wiki "Divine rune pouch": stitching needs 75 Crafting (boosts work). */
        private const val DIVINE_CRAFTING = 75

        const val NOT_A_RUNE = "You can only store runes in the rune pouch."
        const val DONT_BE_SILLY = "Don't be silly."
        const val NO_FREE_SLOT = "Your rune pouch has no room for another type of rune."
        const val FULL = "Your rune pouch cannot hold any more of those runes."
        const val NO_SPACE = "You don't have enough inventory space."
        const val EMPTY = "Your rune pouch is empty."

        fun open(p: PlayerRef) {
            // OpenRune: varp `if1` carries the number of slots the interface draws.
            p.setVarp(Varps.IF1, RunePouch.slots(p))
            p.openModal(Components.Toplevel.MAINMODAL, Interfaces.RUNE_POUCH)
            p.ifEvents(Components.Rune_Pouch.POUCH, 0..RunePouch.MAX_SLOTS + 1, SLOT_EVENTS)
            p.ifEvents(Components.Rune_Pouch.INVENTORY, 0 until p.inv(Invs.INV).size, SLOT_EVENTS)
        }

        private fun pouchOp(p: PlayerRef, click: ButtonClick) {
            if (click.slot !in 0 until RunePouch.slots(p)) return
            val (rune, _) = RunePouch.slot(p, click.slot) ?: return
            if (click.op == EXAMINE) return Examines.item(p, rune, 1)
            amount(p, click.op) { n -> withdraw(p, click.slot, n) }
        }

        private fun inventoryOp(p: PlayerRef, click: ButtonClick) {
            val backpack = p.inv(Invs.INV)
            if (click.slot !in 0 until backpack.size || backpack.obj(click.slot) != click.obj || click.obj < 0) return
            if (click.op == EXAMINE) return Examines.item(p, click.obj, backpack.count(click.slot))
            if (click.obj in RunePouch.ALL) return p.message(DONT_BE_SILLY)
            if (click.obj !in RUNES) return p.message(NOT_A_RUNE)
            amount(p, click.op) { n -> store(p, click.obj, n) }
        }

        /** The amount an op asks for: op 1 the selected quantity, 2 one, 3 five, 4 the custom amount, 5 X, 6 all. */
        @Suppress("MagicNumber") // interface 190's op numbers and their fixed amounts
        private fun amount(p: PlayerRef, op: Int, then: (Int) -> Unit) {
            val custom = p.varbit(Varbits.RUNE_POUCH_CUSTOMQUANTITY)
            val ask = { p.countDialog("Enter amount:") { n -> if (n > 0) then(n) } }
            when (op) {
                1 -> {
                    when (p.varbit(Varbits.RUNE_POUCH_SELECTEDQUANTITY)) {
                        QTY_FIVE -> then(5)
                        QTY_ALL -> then(Int.MAX_VALUE)
                        QTY_CUSTOM -> if (custom > 0) then(custom) else ask()
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
                    if (custom > 0) then(custom) else ask()
                }

                5 -> {
                    ask()
                }

                6 -> {
                    then(Int.MAX_VALUE)
                }
            }
        }

        /** Stores up to [requested] of [rune] from the backpack; returns how many went in. */
        fun store(p: PlayerRef, rune: Int, requested: Int): Int {
            if (RunePouch.slots(p) == 0) return 0
            val held = p.inv(Invs.INV).total(rune)
            if (held <= 0) return 0
            if (RunePouch.slotFor(p, rune) == null) {
                p.message(NO_FREE_SLOT)
                return 0
            }
            val room = RunePouch.room(p, rune)
            if (room <= 0) {
                p.message(FULL)
                return 0
            }
            val n = minOf(requested.toLong(), held, room.toLong()).toInt()
            val ok =
                p.transaction("rune pouch store") {
                    remove(Invs.INV, rune, n).also { if (it) onCommit { RunePouch.add(p, rune, n) } }
                }
            return if (ok) n else 0
        }

        /** Takes up to [requested] from pouch [slot] into the backpack; returns how many came out. */
        fun withdraw(p: PlayerRef, slot: Int, requested: Int): Int {
            val (rune, held) = RunePouch.slot(p, slot) ?: return 0
            val n = minOf(requested, held)
            val ok =
                p.transaction("rune pouch withdraw") {
                    add(Invs.INV, rune, n).also {
                        if (it) {
                            onCommit {
                                RunePouch.set(
                                    p,
                                    slot,
                                    rune,
                                    held - n,
                                )
                            }
                        }
                    }
                }
            if (!ok) p.message(NO_SPACE)
            return if (ok) n else 0
        }

        private fun empty(p: PlayerRef) {
            val n = RunePouch.slots(p)
            if ((0 until n).none { RunePouch.slot(p, it) != null }) return p.message(EMPTY)
            for (slot in 0 until n) {
                if (RunePouch.slot(p, slot) != null && withdraw(p, slot, Int.MAX_VALUE) == 0) return
            }
        }

        /** Wiki "Divine rune pouch": reverting returns the thread and the pouch; the 4th slot's runes are lost (warned). */
        private fun revert(p: PlayerRef, click: ButtonClick) {
            val fourth = RunePouch.slot(p, RunePouch.MAX_SLOTS - 1)
            val doIt = {
                val ok =
                    p.transaction("divine rune pouch revert") {
                        replaceAt(Invs.INV, click.slot, click.obj, Objs.BH_RUNE_POUCH) && add(Invs.INV, Objs.THREAD_OF_ELIDINIS, 1)
                    }
                if (ok) {
                    RunePouch.set(p, RunePouch.MAX_SLOTS - 1, -1, 0)
                    p.message("You remove the thread of Elidinis from the rune pouch.")
                }
            }
            if (fourth == null) return doIt()
            p.choiceDialog("All runes in the fourth slot will be lost. Revert anyway?", listOf("Yes", "No")) { i -> if (i == 1) doIt() }
        }

        /** Needle + thread of Elidinis + rune pouch, 75 Crafting: the divine rune pouch (Wiki). */
        private fun upgrade(p: PlayerRef) {
            val backpack = p.inv(Invs.INV)
            if (backpack.total(Objs.NEEDLE) <= 0) return p.message("You need a needle to do that.")
            if (p.level(Stats.CRAFTING) < DIVINE_CRAFTING) return p.message("You need a Crafting level of $DIVINE_CRAFTING to do that.")
            val ok =
                p.transaction("divine rune pouch") {
                    remove(Invs.INV, Objs.THREAD_OF_ELIDINIS, 1) && remove(Invs.INV, Objs.BH_RUNE_POUCH, 1) &&
                        add(Invs.INV, Objs.DIVINE_RUNE_POUCH, 1)
                }
            if (ok) p.message("You stitch the thread of Elidinis into the rune pouch.")
        }
    }
}
