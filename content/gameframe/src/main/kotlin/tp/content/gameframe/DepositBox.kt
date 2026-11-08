package tp.content.gameframe

import tp.plugin.BankTransfer
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The bank deposit box (docs/mechanics/deposit-box.md): `bank_depositbox` (192) as the main modal. It shows the backpack
 * (inv 93) and the worn items (inv 94) and moves items into the bank without opening it. The bank-insertion rules (stacking,
 * notes, placeholders, tabs, full bank) are the bank's own: every deposit is a [BankTransfer], which [Bank] runs as one item
 * transaction. Left-click quantity is varbit `DEPOSITBOX_MODE`, the last "X" is varp `DEPOSITBOX_REQUESTEDQUANTITY`.
 */
public class DepositBox : ContentPlugin {
    override fun ContentScope.register() {
        for (name in LOC_NAMES) onOpLocNamed(name, "Deposit") { player.openBox() }

        onButton(Components.Bank_Depositbox.INVENTORY) { click -> backpackItem(click) }
        for (w in WORN_SLOTS) onButton(w.second) { click -> wornItem(w.first, click) }
        onButton(Components.Bank_Depositbox.DEPOSIT_INV) { depositAll(Invs.INV) }
        onButton(Components.Bank_Depositbox.DEPOSIT_WORN) { depositAll(Invs.WORN) }
        onButton(Components.Bank_Depositbox.DEPOSIT_LOOTINGBAG) { depositLoot() }
        onButton(Components.Bank_Depositbox._1) { setMode(MODE_ONE) }
        onButton(Components.Bank_Depositbox._5) { setMode(MODE_FIVE) }
        onButton(Components.Bank_Depositbox._10) { setMode(MODE_TEN) }
        onButton(Components.Bank_Depositbox.ALL) { setMode(MODE_ALL) }
        onButton(Components.Bank_Depositbox.X) { click -> quantityX(click.op) }
        onButton(Components.Bank_Depositbox.DEPOSITWORN_TOGGLE) {
            setVarbit(Varbits.DEPOSITBOX_HIDEDEPOSITWORN, 1 - varbit(Varbits.DEPOSITBOX_HIDEDEPOSITWORN))
        }
    }

    private fun PlayerRef.openBox() {
        transmitInv(Invs.INV)
        transmitInv(Invs.WORN)
        // The backpack column's extra op 9 (wear / eat, script 2576) is not offered here (OPEN-DB4): clear what a bank left set.
        setVarp(Varps.IF1, 0)
        setVarp(Varps.IF2, 0)
        setVarp(Varps.IF3, 0)
        setVarp(Varps.IF4, 0)
        SideInventory.openMain(this, Interfaces.BANK_DEPOSITBOX)
        ifEvents(Components.Bank_Depositbox.INVENTORY, 0 until BACKPACK_SLOTS, ITEM_OPS)
    }

    private fun tp.plugin.InvView.holds(slot: Int, obj: Int): Boolean = slot in 0 until size && this.obj(slot) == obj

    private fun PlayerRef.boxOpen(): Boolean = isOpen(Interfaces.BANK_DEPOSITBOX)

    // ---- items

    /** Script 172 sets the ops of a backpack slot; the slot and obj come from the client and are checked here. */
    private fun PlayerRef.backpackItem(click: ButtonClick) {
        val slot = click.slot
        val obj = click.obj
        val backpack = inv(Invs.INV)
        if (!boxOpen() || obj < 0 || !backpack.holds(slot, obj)) return
        when (click.op) {
            OP_LEFT -> {
                depositOne(slot, obj, leftClickQuantity())
            }

            OP_ONE -> {
                depositOne(slot, obj, 1)
            }

            OP_FIVE -> {
                depositOne(slot, obj, FIVE)
            }

            OP_TEN -> {
                depositOne(slot, obj, TEN)
            }

            OP_X -> {
                countDialog("Enter amount:") { n ->
                    if (n > 0) {
                        setVarp(Varps.DEPOSITBOX_REQUESTEDQUANTITY, n)
                        depositOne(slot, obj, n)
                    }
                }
            }

            OP_ALL -> {
                depositOne(slot, obj, Int.MAX_VALUE)
            }

            // Op 9 (OPEN-DB4) is not offered; op 10 is "Examine" while deposit locks (not implemented) are off.
            OP_EXAMINE -> {
                Examines.item(this, obj, backpack.count(slot))
            }
        }
    }

    /** Deposits up to [requested] of [obj] (all copies count); re-checks the slot, as a count dialog answers later. */
    private fun PlayerRef.depositOne(slot: Int, obj: Int, requested: Int) {
        if (!boxOpen() || requested <= 0 || inv(Invs.INV).obj(slot) != obj) return
        events.publish(BankTransfer(this, obj, requested, deposit = true, source = Invs.INV))
    }

    private fun PlayerRef.wornItem(wornSlot: Int, click: ButtonClick) {
        val worn = inv(Invs.WORN)
        val obj = worn.obj(wornSlot)
        if (!boxOpen() || obj < 0) return
        when (click.op) {
            OP_WORN_DEPOSIT -> events.publish(BankTransfer(this, obj, worn.count(wornSlot), deposit = true, source = Invs.WORN))
            OP_WORN_EXAMINE -> Examines.item(this, obj, worn.count(wornSlot))
        }
    }

    // ---- buttons

    /** Deposit inventory / Deposit worn items: every distinct obj of [source], in slot order, with an unlimited amount. */
    private fun PlayerRef.depositAll(source: Int) {
        if (!boxOpen()) return
        val from = inv(source)
        val objs = LinkedHashSet<Int>()
        for (slot in 0 until from.size) from.obj(slot).takeIf { it >= 0 }?.let { objs += it }
        if (objs.isEmpty()) return message("You have nothing to deposit.")
        for (obj in objs) events.publish(BankTransfer(this, obj, Int.MAX_VALUE, deposit = true, source = source))
    }

    /** "Deposit loot": the looting bag's contents, as the bank's own button does it (only with a bag in the backpack). */
    private fun PlayerRef.depositLoot() {
        if (!boxOpen() || !LootingBag.holds(this)) return
        BankStorage.depositLoot(this)
    }

    // ---- quantity

    private fun PlayerRef.leftClickQuantity(): Int =
        when (varbit(Varbits.DEPOSITBOX_MODE)) {
            MODE_FIVE -> FIVE
            MODE_TEN -> TEN
            MODE_X -> lastQuantity()
            MODE_ALL -> Int.MAX_VALUE
            else -> 1
        }

    /** The last "X" typed; never below 1. */
    private fun PlayerRef.lastQuantity(): Int = maxOf(1, varp(Varps.DEPOSITBOX_REQUESTEDQUANTITY))

    private fun PlayerRef.setMode(mode: Int) {
        if (boxOpen()) setVarbit(Varbits.DEPOSITBOX_MODE, mode)
    }

    /** "X" button: op 1 uses the last X, op 2 ("Set custom quantity") asks for a new one (as the bank's X button). */
    private fun PlayerRef.quantityX(op: Int) {
        if (!boxOpen()) return
        if (op == OP_X_ASK) {
            countDialog("Enter amount:") { n ->
                if (n > 0) setVarp(Varps.DEPOSITBOX_REQUESTEDQUANTITY, n)
                useLastQuantity()
            }
        } else {
            useLastQuantity()
        }
    }

    private fun PlayerRef.useLastQuantity() {
        if (boxOpen()) setVarbit(Varbits.DEPOSITBOX_MODE, if (varp(Varps.DEPOSITBOX_REQUESTEDQUANTITY) == 0) MODE_ONE else MODE_X)
    }

    private companion object {
        /** Cache names of the deposit locs; every one has the single op "Deposit" (`cache-dump config loc`). */
        val LOC_NAMES = listOf("Bank deposit box", "Deposit box", "Deposit pot", "Bank deposit pot")

        const val BACKPACK_SLOTS = 28
        const val FIVE = 5
        const val TEN = 10

        /** Values of varbit `DEPOSITBOX_MODE` (4430), read by script 172: 0 one, 1 five, 2 ten, 3 X, 4 all. */
        const val MODE_ONE = 0
        const val MODE_FIVE = 1
        const val MODE_TEN = 2
        const val MODE_X = 3
        const val MODE_ALL = 4

        /** Backpack slot ops (CS 172, RuneLite `ShiftDepositMode`): 1 left-click quantity, 2..6 = 1, 5, 10, X, all. */
        const val OP_LEFT = 1
        const val OP_ONE = 2
        const val OP_FIVE = 3
        const val OP_TEN = 4
        const val OP_X = 5
        const val OP_ALL = 6
        const val OP_EXAMINE = 10

        /** Op 2 of the X button is "Set custom quantity" (`cache-dump interface 192`, component 38). */
        const val OP_X_ASK = 2

        /** Worn slot ops (cache: ops 1, 2 and 10 on the slot components, as the bank's worn page): 2 deposits, 10 examines (OPEN-DB3). */
        const val OP_WORN_DEPOSIT = 2
        const val OP_WORN_EXAMINE = 10

        /** Ops 1..6 and 10 of the backpack grid. */
        val ITEM_OPS: Long = (1..OP_ALL).fold(IfEvents.op(OP_EXAMINE)) { acc, op -> acc or IfEvents.op(op) }

        /** Worn slot (inv 94 position) and its component in `bank_depositbox` (SLOT n = worn slot n; slots 6, 8 and 11 have none). */
        val WORN_SLOTS: List<Pair<Int, Int>> =
            listOf(
                0 to Components.Bank_Depositbox.SLOT0,
                1 to Components.Bank_Depositbox.SLOT1,
                2 to Components.Bank_Depositbox.SLOT2,
                3 to Components.Bank_Depositbox.SLOT3,
                4 to Components.Bank_Depositbox.SLOT4,
                5 to Components.Bank_Depositbox.SLOT5,
                7 to Components.Bank_Depositbox.SLOT7,
                9 to Components.Bank_Depositbox.SLOT9,
                10 to Components.Bank_Depositbox.SLOT10,
                12 to Components.Bank_Depositbox.SLOT12,
                13 to Components.Bank_Depositbox.SLOT13,
            )
    }
}
