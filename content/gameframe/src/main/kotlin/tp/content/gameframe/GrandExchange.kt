package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.GeOffer
import tp.plugin.GeRequest
import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * Grand Exchange skeleton (Phase 5): offers are matched by the hub. There is no interface yet, only commands.
 * Coins and items move only through item transactions: they are removed before an offer is placed, and added back
 * on collect or abort.
 * `::ge` lists the offers. `::gebuy <obj> <qty> <price>` and `::gesell <obj> <qty> <price>` place an offer.
 * `::gecollect <slot>` collects what is waiting, and `::geabort <slot>` aborts an offer.
 */
public class GrandExchange : ContentPlugin {
    override fun ContentScope.register() {
        onCommand("ge") { exchange(GeRequest.Offers) { offers -> list(offers) } }
        onCommand("gebuy") { args -> place(args, buy = true) }
        onCommand("gesell") { args -> place(args, buy = false) }
        onCommand("gecollect") { args -> slotArg(args)?.let { slot -> takeBack(GeRequest.Collect(slot), "collect") } }
        onCommand("geabort") { args -> slotArg(args)?.let { slot -> takeBack(GeRequest.Abort(slot), "abort") } }
    }

    private fun PlayerRef.list(offers: List<GeOffer>?) {
        when {
            offers == null -> {
                message("The Grand Exchange is unavailable.")
            }

            offers.isEmpty() -> {
                message("You have no Grand Exchange offers.")
            }

            else -> {
                for (o in offers) {
                    message(
                        "Slot ${o.slot}: ${if (o.buy) "buy" else "sell"} ${o.obj} x${o.quantity} @ ${o.price} " +
                            "(${o.completed} done; collect ${o.collectCoins} coins, ${o.collectItems} items)",
                    )
                }
            }
        }
    }

    private fun PlayerRef.place(args: List<String>, buy: Boolean) {
        val obj = args.getOrNull(0)?.toIntOrNull()
        val quantity = args.getOrNull(1)?.toIntOrNull()
        val price = args.getOrNull(2)?.toIntOrNull()
        if (obj == null || quantity == null || price == null) return usage(buy)
        if (quantity <= 0 || price <= 0 || obj == COINS) return usage(buy)
        val cost = quantity.toLong() * price
        if (buy && cost > Int.MAX_VALUE) return message("That offer is too large.")
        exchange(GeRequest.Offers) { offers ->
            if (offers == null) return@exchange message("The Grand Exchange is unavailable.")
            val slot =
                (0 until SLOTS).firstOrNull { s -> offers.none { it.slot == s } }
                    ?: return@exchange message("All your offer slots are in use.")
            val (removeObj, removeCount) = if (buy) COINS to cost.toInt() else obj to quantity
            if (!transaction("ge place") { remove(Invs.INV, removeObj, removeCount) }) {
                return@exchange message(if (buy) "You don't have enough coins." else "You don't have enough of that item.")
            }
            exchange(GeRequest.Place(slot, buy, obj, quantity, price)) { placed ->
                if (placed == null) {
                    // The hub refused: give back what was taken (same transaction API).
                    transaction("ge place refund") { add(Invs.INV, removeObj, removeCount) }
                    message("The Grand Exchange is unavailable.")
                } else {
                    message("Offer placed in slot $slot.")
                    list(placed)
                }
            }
        }
    }

    private fun PlayerRef.usage(buy: Boolean) = message("Usage: ::${if (buy) "gebuy" else "gesell"} <obj> <quantity> <price each>")

    /** Collect/abort: needs room for coins and items first, since the hub hands them over once. */
    private fun PlayerRef.takeBack(request: GeRequest, what: String) {
        if (inv(Invs.INV).freeSlots < 2) return message("You need two free inventory slots.")
        exchange(request) { offers ->
            val o = offers?.firstOrNull() ?: return@exchange message("Nothing to $what.")
            val coins = minOf(o.collectCoins, Int.MAX_VALUE.toLong()).toInt()
            val ok =
                transaction("ge $what") {
                    (coins == 0 || add(Invs.INV, COINS, coins)) && (o.collectItems == 0 || add(Invs.INV, o.obj, o.collectItems))
                }
            message(if (ok) "You collect $coins coins and ${o.collectItems} x ${o.obj}." else "Your inventory is too full.")
        }
    }

    private fun PlayerRef.slotArg(args: List<String>): Int? {
        val slot = args.getOrNull(0)?.toIntOrNull()
        if (slot == null || slot !in 0 until SLOTS) message("Usage: slot 0-${SLOTS - 1}")
        return slot?.takeIf { it in 0 until SLOTS }
    }

    private companion object {
        /** Coins (cache obj 995). */
        const val COINS = 995

        /** Grand Exchange offer slots for members (OSRS Wiki "Grand Exchange": 8). */
        const val SLOTS = 8
    }
}
