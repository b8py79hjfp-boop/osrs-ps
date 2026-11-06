package tp.content.gameframe

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import java.util.Locale

/**
 * Worn items tab "Price-checker" (docs/mechanics/gameframe-buttons.md Q2): `ge_pricechecker` with the backpack in the
 * side slot. Items move between the backpack and the temporary `tradeoffer` inventory only through transactions and
 * all go back to the backpack when the panel closes. Layout and ops follow RS Mod `GuidePriceScript` (ISC, D-027).
 */
public class PriceChecker : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Wornitems.PRICECHECKER) { open() }
        onButton(Components.Ge_Pricechecker_Side.ITEMS) { click -> add(click) }
        onButton(Components.Ge_Pricechecker.ITEMS) { click -> take(click) }
        onButton(Components.Ge_Pricechecker.ALL) { addAll() }
        // OPEN-Q2b: "Search for item" needs the obj-search chatbox dialog (RESUME_P_OBJDIALOG), which plugin-api does not have.
        onButton(Components.Ge_Pricechecker.OTHER) { message("Searching for an item isn't available yet.") }
        // Items left in the offer by a crash or an interrupted close are given back at login.
        onLogin { returnAll() }
    }

    private fun PlayerRef.open() {
        returnAll()
        transmitInv(Invs.TRADEOFFER)
        transmitInv(Invs.INV)
        SideInventory.openPair(this, Interfaces.GE_PRICECHECKER, Interfaces.GE_PRICECHECKER_SIDE)
        ifObject(Components.Ge_Pricechecker.OTHERITEM, Objs.BLANKOBJECT, 1)
        val events =
            IfEvents.op(OP_ONE) or IfEvents.op(OP_FIVE) or IfEvents.op(OP_TEN) or IfEvents.op(OP_ALL) or IfEvents.op(OP_X) or
                IfEvents.op(OP_EXAMINE)
        ifEvents(Components.Ge_Pricechecker.ITEMS, 0..OFFER_LAST_SLOT, events)
        SideInventory.bindGrid(
            this,
            Components.Ge_Pricechecker_Side.ITEMS,
            Invs.INV,
            0,
            "Add<col=ff9040>",
            "Add-5<col=ff9040>",
            "Add-10<col=ff9040>",
            "Add-All<col=ff9040>",
            "Add-X<col=ff9040>",
        )
        ifEvents(Components.Ge_Pricechecker_Side.ITEMS, 0..BACKPACK_LAST_SLOT, events)
        refresh()
        ModalWatch.start(this, Interfaces.GE_PRICECHECKER, onClose = {
            returnAll()
            SideInventory.restore(this)
        })
    }

    /** Guide price of one [obj]. OPEN-Q2a: real OSRS uses the Grand Exchange guide price; this is the cache `cost`. */
    private fun PlayerRef.price(obj: Int): Int = objValue(obj)

    private fun PlayerRef.refresh() {
        val offer = inv(Invs.TRADEOFFER)
        val prices = Array<Any>(OFFER_SLOTS) { 0 }
        var total = 0L
        for (slot in 0 until OFFER_SLOTS) {
            val obj = offer.obj(slot)
            if (obj < 0) continue
            val price = price(obj)
            prices[slot] = price
            total += price.toLong() * offer.count(slot)
        }
        clientScript(CS_PRICES, *prices)
        clientScript(CS_TEXT_ALIGN, ALIGN_CENTRE, ALIGN_CENTRE, LINE_HEIGHT, Components.Ge_Pricechecker.OUTPUT)
        ifText(Components.Ge_Pricechecker.OUTPUT, "Total guide price:<br><col=ffffff>${amount(total)}</col>")
    }

    private fun amount(value: Long): String = String.format(Locale.US, "%,d", value)

    /** Number of items an op moves: 1, 5, 10, all; null for Add-X/Remove-X (asked with a count dialog) and Examine. */
    private fun count(op: Int): Int? =
        when (op) {
            OP_ONE -> 1
            OP_FIVE -> AMOUNT_FIVE
            OP_TEN -> AMOUNT_TEN
            OP_ALL -> Int.MAX_VALUE
            else -> null
        }

    private fun PlayerRef.add(click: ButtonClick) {
        val backpack = inv(Invs.INV)
        val obj = click.obj
        if (click.slot !in 0 until backpack.size || backpack.obj(click.slot) != obj) return
        if (click.op == OP_X) {
            val slot = click.slot
            return countDialog(COUNT_PROMPT) { n -> if (isOpen(Interfaces.GE_PRICECHECKER)) addAmount(slot, obj, n) }
        }
        addAmount(click.slot, obj, count(click.op) ?: return)
    }

    private fun PlayerRef.addAmount(slot: Int, obj: Int, wanted: Int) {
        val backpack = inv(Invs.INV)
        if (slot !in 0 until backpack.size || backpack.obj(slot) != obj) return
        if (!objTradeable(obj)) return message("You cannot trade that item.")
        val amount = minOf(wanted.toLong(), backpack.total(obj)).toInt()
        if (amount <= 0) return
        val ok = transaction("price checker add") { remove(Invs.INV, obj, amount) && add(Invs.TRADEOFFER, obj, amount) }
        if (ok) refresh()
    }

    private fun PlayerRef.addAll() {
        val backpack = inv(Invs.INV)
        val moves = (0 until backpack.size).filter { backpack.obj(it) >= 0 }
        if (moves.isEmpty()) return message("You have no items that can be checked.")
        val tradeable = moves.filter { objTradeable(backpack.obj(it)) }
        if (tradeable.isEmpty()) return message("You have items that cannot be traded.")
        val ok =
            transaction("price checker add all") {
                tradeable.all { slot ->
                    val obj = backpack.obj(slot)
                    move(Invs.INV, slot, obj, backpack.count(slot), Invs.TRADEOFFER)
                }
            }
        if (ok) refresh()
    }

    private fun PlayerRef.take(click: ButtonClick) {
        val offer = inv(Invs.TRADEOFFER)
        val obj = click.obj
        if (click.slot !in 0 until offer.size || offer.obj(click.slot) != obj) return
        if (click.op == OP_X) {
            val slot = click.slot
            return countDialog(COUNT_PROMPT) { n -> if (isOpen(Interfaces.GE_PRICECHECKER)) takeAmount(slot, obj, n) }
        }
        takeAmount(click.slot, obj, count(click.op) ?: return)
    }

    private fun PlayerRef.takeAmount(slot: Int, obj: Int, wanted: Int) {
        val offer = inv(Invs.TRADEOFFER)
        if (slot !in 0 until offer.size || offer.obj(slot) != obj) return
        val amount = minOf(wanted.toLong(), offer.total(obj)).toInt()
        if (amount <= 0) return
        val ok = transaction("price checker remove") { remove(Invs.TRADEOFFER, obj, amount) && add(Invs.INV, obj, amount) }
        if (!ok) return message("You don't have enough inventory space to do that.")
        compact()
        refresh()
    }

    /** Slides the offered items up so no gap is left (RS Mod `invCompress`). */
    private fun PlayerRef.compact() {
        val offer = inv(Invs.TRADEOFFER)
        val used = (0 until offer.size).filter { offer.obj(it) >= 0 }
        if (used.isEmpty() || used.last() == used.size - 1) return
        val items = used.map { offer.obj(it) to offer.count(it) }
        transaction("price checker compact") {
            used.all { slot -> removeAt(Invs.TRADEOFFER, slot, offer.obj(slot), offer.count(slot)) } &&
                items.all { (obj, count) -> add(Invs.TRADEOFFER, obj, count) }
        }
    }

    /** Everything in the offer goes back to the backpack; what no longer fits is dropped at the player's feet. */
    private fun PlayerRef.returnAll() {
        val offer = inv(Invs.TRADEOFFER)
        for (slot in 0 until offer.size) {
            val obj = offer.obj(slot)
            if (obj < 0) continue
            val count = offer.count(slot)
            val moved = transaction("price checker return") { move(Invs.TRADEOFFER, slot, obj, count, Invs.INV) }
            if (!moved) transaction("price checker return drop") { drop(Invs.TRADEOFFER, slot, obj, count) }
        }
    }

    private companion object {
        /** Button ops: Add/Remove 1, 5, 10, All, X (op 1..5) and Examine (op 10), RS Mod `IfEvent.Op1..Op10`. */
        const val OP_ONE = 1
        const val OP_FIVE = 2
        const val OP_TEN = 3
        const val OP_ALL = 4
        const val OP_X = 5
        const val OP_EXAMINE = 10
        const val COUNT_PROMPT = "Enter amount:"
        const val AMOUNT_FIVE = 5
        const val AMOUNT_TEN = 10

        /** Offer slots (inv `tradeoffer`, size 28) and backpack slots. */
        const val OFFER_SLOTS = 28
        const val OFFER_LAST_SLOT = 27
        const val BACKPACK_LAST_SLOT = 27

        /** Client script that shows the 28 slot prices under the offer (RS Mod `updatePrices`). */
        const val CS_PRICES = 785

        /** Client script `if_settextalign` (hAlign, vAlign, line height, component; RS Mod `ifSetTextAlign`). */
        const val CS_TEXT_ALIGN = 600
        const val ALIGN_CENTRE = 1
        const val LINE_HEIGHT = 15
    }
}
