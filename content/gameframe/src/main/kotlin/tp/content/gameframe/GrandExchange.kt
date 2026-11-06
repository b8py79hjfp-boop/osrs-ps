package tp.content.gameframe

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.GeOffer
import tp.plugin.GeRequest
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The Grand Exchange interface (docs/mechanics/grand-exchange.md GE4–GE11). Offers are matched by the hub; this side
 * drives the client's offer screen (interface 465 + side 467), mirrors the quantity/price arithmetic the client does
 * locally (client scripts 777/778/5156), and moves coins and items only through item transactions.
 * Commands remain for testing: `::ge`, `::gebuy`, `::gesell`, `::gecollect`, `::geabort`, `::geui`.
 */
public class GrandExchange : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpcOption("Exchange") { player.open() }
        onCommand("geui") { open() }
        onCommand("ge") { exchange(GeRequest.Offers) { offers -> list(offers) } }
        onCommand("gebuy") { args -> placeFromCommand(args, buy = true) }
        onCommand("gesell") { args -> placeFromCommand(args, buy = false) }
        onCommand("gecollect") { args -> slotArg(args)?.let { slot -> collect(slot) } }
        onCommand("geabort") { args -> slotArg(args)?.let { slot -> abort(slot) } }

        for ((n, component) in SLOT_COMPONENTS.withIndex()) onButton(component) { click -> slotButton(n, click) }
        onButton(Components.Ge_Offers.BACK) { showIndex() }
        onButton(Components.Ge_Offers.SETUP) { click -> setupButton(click) }
        onButton(Components.Ge_Offers.SETUP_CONFIRM) { confirm() }
        onButton(Components.Ge_Offers.DETAILS_STATUS) { click -> if (click.slot == 0) selectedSlot()?.let { abort(it) } }
        onButton(Components.Ge_Offers.DETAILS_COLLECT) { selectedSlot()?.let { collect(it) } }
        onButton(Components.Ge_Offers.COLLECTALL) { click -> if (click.slot == 0) collectAll() }
        onButton(Components.Ge_Offers_Side.ITEMS) { click -> if (click.op == 1) offerFromInventory(click.slot) }
    }

    // ---- opening and refreshing

    private fun PlayerRef.open() {
        // GE5: the client treats 0 as obj 0, so "none" must be -1 (an empty slot otherwise renders as a sell offer).
        setVarp(Varps.TRADINGPOST_SEARCH, NONE)
        if (varp(Varps.GE_LAST_SEARCHED) == 0) setVarp(Varps.GE_LAST_SEARCHED, NONE)
        if (varp(Varps.GE_LAST_OFFER_ITEM) == 0) setVarp(Varps.GE_LAST_OFFER_ITEM, NONE)
        for (v in ITEMSINK_OBJ_VARPS) setVarp(v, NONE)
        setVarbit(Varbits.GE_SELECTEDSLOT, 0)
        SideInventory.openPair(this, Interfaces.GE_OFFERS, Interfaces.GE_OFFERS_SIDE)
        enableEvents()
        refresh()
    }

    /** GE4: IF_SETEVENTS for every op the client's scripts create (client scripts 773, 783, 794, 816). */
    private fun PlayerRef.enableEvents() {
        for (c in SLOT_COMPONENTS) ifEvents(c, 2..4, ops(1, 2, 3))
        ifEvents(Components.Ge_Offers.COLLECTALL, 0..1, ops(1, 2))
        ifEvents(Components.Ge_Offers.SETUP, 0..SETUP_LAST_CHILD, ops(1, 2))
        ifEvents(Components.Ge_Offers.DETAILS_STATUS, 0..1, ops(1))
        ifEvents(Components.Ge_Offers.DETAILS_COLLECT, 2..3, ops(1, 2, 3, EXAMINE_OP))
        ifEvents(Components.Ge_Offers_Side.ITEMS, 0..LAST_INV_SLOT, ops(1, EXAMINE_OP))
        // BACK, SETUP_CONFIRM and HISTORY transmit op 1 by their cache click mask.
    }

    private fun ops(vararg op: Int): Long = op.fold(0L) { acc, o -> acc or IfEvents.op(o) }

    /** GE6: every slot's state and collect inv, from the hub. */
    private fun PlayerRef.refresh() {
        exchange(GeRequest.Offers) { offers ->
            if (offers == null) return@exchange message("The Grand Exchange is unavailable.")
            for (slot in 0 until SLOTS) sendSlot(slot, offers.firstOrNull { it.slot == slot })
        }
    }

    private fun PlayerRef.sendSlot(slot: Int, o: GeOffer?) {
        if (o == null) {
            stockMarketSlot(slot, 0, 0, 0, 0, 0, 0)
            showInv(COLLECT_INVS[slot], intArrayOf(-1, -1), intArrayOf(0, 0))
            return
        }
        val state = if (o.completed < o.quantity) STATE_ACTIVE else STATE_FINISHED
        val status = state or if (o.buy) 0 else STATUS_SELL
        val gold = minOf(Int.MAX_VALUE.toLong(), o.completed.toLong() * o.price).toInt()
        stockMarketSlot(slot, status, o.obj, o.price, o.quantity, o.completed, gold)
        val coins = minOf(Int.MAX_VALUE.toLong(), o.collectCoins).toInt()
        showInv(
            COLLECT_INVS[slot],
            intArrayOf(if (o.collectItems > 0) o.obj else -1, if (coins > 0) COINS else -1),
            intArrayOf(o.collectItems, coins),
        )
    }

    private fun PlayerRef.selectedSlot(): Int? = (varbit(Varbits.GE_SELECTEDSLOT) - 1).takeIf { it in 0 until SLOTS }

    private fun PlayerRef.showIndex() {
        setVarbit(Varbits.GE_SELECTEDSLOT, 0)
        setVarp(Varps.TRADINGPOST_SEARCH, NONE)
    }

    // ---- index page

    /** GE7: slot children 2 (view / abort / modify), 3 (create buy), 4 (create sell). */
    private fun PlayerRef.slotButton(slot: Int, click: ButtonClick) {
        when (click.slot) {
            CHILD_VIEW -> {
                when (click.op) {
                    1 -> setVarbit(Varbits.GE_SELECTEDSLOT, slot + 1)
                    2 -> abort(slot)
                    else -> message("Offers can't be modified yet.") // OPEN-GE3
                }
            }

            CHILD_BUY -> {
                startSetup(slot, buy = true)
            }

            CHILD_SELL -> {
                startSetup(slot, buy = false)
            }
        }
    }

    private fun PlayerRef.startSetup(slot: Int, buy: Boolean) {
        setVarbit(Varbits.GE_SELECTEDSLOT, slot + 1)
        setVarbit(Varbits.GE_NEWOFFER_TYPE, if (buy) TYPE_BUY else TYPE_SELL)
        setVarp(Varps.TRADINGPOST_SEARCH, NONE)
        setVarbit(Varbits.GE_NEWOFFER_QUANTITY, 0)
        setPrice(0)
        if (buy) chooseItem()
    }

    private fun PlayerRef.buying(): Boolean = varbit(Varbits.GE_NEWOFFER_TYPE) == TYPE_BUY

    private fun PlayerRef.item(): Int = varp(Varps.TRADINGPOST_SEARCH)

    private fun PlayerRef.quantity(): Int = varbit(Varbits.GE_NEWOFFER_QUANTITY)

    private fun PlayerRef.price(): Long = this[PRICE] ?: 0L

    private fun PlayerRef.setPrice(price: Long) {
        this[PRICE] = price
        sendVarpLong(Varps.GE_NEWOFFER_PRICE, price)
    }

    /** GE8: select [obj] (unnoted) with quantity 1 and the guide price, as the client shows for a fresh selection. */
    private fun PlayerRef.select(obj: Int) {
        val item = unnoted(obj)
        if (!isGeTradeable(item)) return message("You can't trade that item on the Grand Exchange.")
        setVarp(Varps.TRADINGPOST_SEARCH, item)
        if (buying()) setVarp(Varps.GE_LAST_SEARCHED, item)
        setVarbit(Varbits.GE_NEWOFFER_QUANTITY, 1)
        setPrice(objValue(item).toLong())
        ifText(Components.Ge_Offers.SETUP_MARKETPRICE, "%,d coins".format(objValue(item)))
    }

    private fun PlayerRef.chooseItem() {
        objDialog("What would you like to buy?") { obj -> if (obj >= 0 && selectedSlot() != null) select(obj) }
    }

    private fun PlayerRef.offerFromInventory(invSlot: Int) {
        val obj = inv(Invs.INV).obj(invSlot)
        if (obj < 0) return
        if (selectedSlot() == null) {
            // GE9: from the index page, "Offer" opens a sell offer in the first free slot (OPEN-GE4).
            exchange(GeRequest.Offers) { offers ->
                val free = (0 until SLOTS).firstOrNull { s -> offers?.none { it.slot == s } == true }
                if (free == null) return@exchange message("All your offer slots are in use.")
                startSetup(free, buy = false)
                select(obj)
            }
            return
        }
        if (!buying()) select(obj)
    }

    // ---- setup screen (GE10: the client already changed its copy; the server repeats the same arithmetic)

    private fun PlayerRef.setupButton(click: ButtonClick) {
        if (click.slot == CHILD_CHOOSE) return if (buying()) chooseItem() else Unit
        if (item() == NONE) return
        when (click.slot) {
            in QUANTITY_CHILDREN -> quantityButton(click)
            in PRICE_CHILDREN -> priceButton(click.slot)
            else -> message("Custom percentage buttons aren't available yet.") // OPEN-GE5
        }
    }

    /** Setup children 1..7 (client script 773/777). */
    @Suppress("MagicNumber") // setup child indices, client script 773
    private fun PlayerRef.quantityButton(click: ButtonClick) {
        when (click.slot) {
            1 -> {
                changeQuantity(-1)
            }

            2, 3 -> {
                changeQuantity(1)
            }

            4 -> {
                changeQuantity(TEN)
            }

            5 -> {
                changeQuantity(HUNDRED)
            }

            6 -> {
                changeQuantity(if (buying()) THOUSAND else Int.MAX_VALUE)
            }

            7 -> {
                if (click.op == 1) {
                    enterQuantity()
                } else if (buying()) {
                    maxAffordable()
                }
            }
        }
    }

    /** Setup children 8..13 (client script 773/778). */
    @Suppress("MagicNumber") // setup child indices, client script 773
    private fun PlayerRef.priceButton(child: Int) {
        val step = maxOf(1L, price() / FIVE_PERCENT_DIVISOR)
        when (child) {
            8 -> setPrice(clampPrice(price() - 1))
            9 -> setPrice(clampPrice(price() + 1))
            10 -> setPrice(clampPrice(price() - step))
            11 -> setPrice(objValue(item()).toLong())
            12 -> countDialog("Set a price for each item:") { n -> if (item() != NONE) setPrice(clampPrice(n.toLong())) }
            13 -> setPrice(clampPrice(price() + step))
        }
    }

    /** Client script 777: +delta (delta − 1 when moving off quantity 1), capped; sells cap at what is held. */
    private fun PlayerRef.changeQuantity(delta: Int) {
        val now = quantity()
        val cap = if (buying()) Int.MAX_VALUE else maxOf(1L, held(item())).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val next =
            when {
                delta == Int.MAX_VALUE -> cap
                delta > 0 -> minOf(cap.toLong(), now.toLong() + if (delta > 1 && now == 1) delta - 1 else delta).toInt()
                else -> maxOf(1, now + delta)
            }
        setVarbit(Varbits.GE_NEWOFFER_QUANTITY, next)
    }

    private fun PlayerRef.enterQuantity() {
        countDialog("How many do you wish to ${if (buying()) "buy" else "sell"}?") { n ->
            if (item() == NONE) return@countDialog
            val cap = if (buying()) Int.MAX_VALUE else held(item()).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
            setVarbit(Varbits.GE_NEWOFFER_QUANTITY, n.coerceIn(1, cap))
        }
    }

    /** Client script 5156: coins / price, at least 1. */
    private fun PlayerRef.maxAffordable() {
        val price = price()
        if (price <= 0) return
        val coins = inv(Invs.INV).total(COINS) + PLATINUM_VALUE * inv(Invs.INV).total(PLATINUM)
        setVarbit(Varbits.GE_NEWOFFER_QUANTITY, (coins / price).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
    }

    /** Client script 778 keeps price in 1..2,149,631,130,647; the hub stores Int prices, so we cap at Int.MAX. */
    private fun clampPrice(p: Long): Long = p.coerceIn(1L, Int.MAX_VALUE.toLong())

    /** Unnoted + noted count held (client script 813). */
    private fun PlayerRef.held(obj: Int): Long {
        val inv = inv(Invs.INV)
        val noted = noted(obj)
        return inv.total(obj) + if (noted >= 0) inv.total(noted) else 0L
    }

    // ---- confirm, collect, abort

    /** GE11: the client disables Confirm for bad values, but the packet is hostile: check everything again. */
    private fun PlayerRef.confirm() {
        val slot = selectedSlot() ?: return
        val obj = item()
        val quantity = quantity()
        val price = price()
        if (obj == NONE || quantity <= 0 || price <= 0) return
        place(slot, buying(), obj, quantity, price.toInt()) { showIndex() }
    }

    private fun PlayerRef.place(slot: Int, buy: Boolean, obj: Int, quantity: Int, price: Int, after: PlayerRef.() -> Unit = {}) {
        val cost = quantity.toLong() * price
        if (buy && cost > Int.MAX_VALUE) return message("That offer is too large.")
        exchange(GeRequest.Offers) { offers ->
            if (offers == null) return@exchange message("The Grand Exchange is unavailable.")
            if (offers.any { it.slot == slot }) return@exchange message("That offer slot is in use.")
            val taken = if (buy) takeCoins(cost.toInt()) else takeItems(obj, quantity)
            if (taken == null) return@exchange message(if (buy) "You don't have enough coins." else "You don't have enough of that item.")
            exchange(GeRequest.Place(slot, buy, obj, quantity, price)) { placed ->
                if (placed == null) {
                    transaction("ge place refund") { taken.all { (o, n) -> add(Invs.INV, o, n) } }
                    return@exchange message("The Grand Exchange is unavailable.")
                }
                setVarp(Varps.GE_LAST_OFFER_ITEM, obj)
                setVarp(Varps.GE_LAST_OFFER_QUANTITY, quantity)
                setVarp(Varps.GE_LAST_OFFER_PRICE, price)
                setVarp(Varps.GE_LAST_OFFER_TYPE, if (buy) TYPE_BUY else TYPE_SELL)
                after()
                placed.forEach { sendSlot(it.slot, it) }
            }
        }
    }

    /** Removes [amount] coins; returns what was taken (for a refund) or null. */
    private fun PlayerRef.takeCoins(amount: Int): List<Pair<Int, Int>>? =
        listOf(COINS to amount).takeIf { transaction("ge place") { remove(Invs.INV, COINS, amount) } }

    /** Removes [quantity] of [obj], unnoted first, then notes (client script 813 counts both). */
    private fun PlayerRef.takeItems(obj: Int, quantity: Int): List<Pair<Int, Int>>? {
        val plain = minOf(quantity.toLong(), inv(Invs.INV).total(obj)).toInt()
        val noted = noted(obj)
        val fromNotes = quantity - plain
        if (fromNotes > 0 && (noted < 0 || inv(Invs.INV).total(noted) < fromNotes)) return null
        val taken = listOfNotNull((obj to plain).takeIf { plain > 0 }, (noted to fromNotes).takeIf { fromNotes > 0 })
        return taken.takeIf { transaction("ge place") { it.all { (o, n) -> remove(Invs.INV, o, n) } } }
    }

    private fun PlayerRef.collect(slot: Int) = takeBack(slot, GeRequest.Collect(slot), "collect")

    private fun PlayerRef.abort(slot: Int) = takeBack(slot, GeRequest.Abort(slot), "abort")

    private fun PlayerRef.collectAll() {
        exchange(GeRequest.Offers) { offers ->
            offers?.filter { it.collectCoins > 0 || it.collectItems > 0 }?.forEach { collect(it.slot) }
        }
    }

    /** Needs room for coins and items first, since the hub hands them over once. Items over 1 come back noted. */
    private fun PlayerRef.takeBack(slot: Int, request: GeRequest, what: String) {
        if (inv(Invs.INV).freeSlots < 2) return message("You need two free inventory slots.")
        exchange(request) { offers ->
            val o = offers?.firstOrNull() ?: return@exchange message("Nothing to $what.")
            val coins = minOf(o.collectCoins, Int.MAX_VALUE.toLong()).toInt()
            val itemObj = if (o.collectItems > 1 && noted(o.obj) >= 0) noted(o.obj) else o.obj
            val ok =
                transaction("ge $what") {
                    (coins == 0 || add(Invs.INV, COINS, coins)) && (o.collectItems == 0 || add(Invs.INV, itemObj, o.collectItems))
                }
            if (!ok) message("Your inventory is too full.")
            if (isOpen(Interfaces.GE_OFFERS)) {
                if (selectedSlot() == slot && what == "abort") showIndex()
                refresh()
            } else {
                message("You collect $coins coins and ${o.collectItems} x ${objName(o.obj)}.")
            }
        }
    }

    // ---- commands

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

    private fun PlayerRef.placeFromCommand(args: List<String>, buy: Boolean) {
        val obj = args.getOrNull(0)?.toIntOrNull()?.takeIf { it != COINS }
        val quantity = args.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
        val price = args.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }
        if (obj == null || quantity == null || price == null) {
            return message("Usage: ::${if (buy) "gebuy" else "gesell"} <obj> <quantity> <price each>")
        }
        exchange(GeRequest.Offers) { offers ->
            val slot = (0 until SLOTS).firstOrNull { s -> offers?.none { it.slot == s } == true }
            if (slot == null) return@exchange message("All your offer slots are in use.")
            place(slot, buy, unnoted(obj), quantity, price) { message("Offer placed in slot $slot.") }
        }
    }

    private fun PlayerRef.slotArg(args: List<String>): Int? {
        val slot = args.getOrNull(0)?.toIntOrNull()
        if (slot == null || slot !in 0 until SLOTS) message("Usage: slot 0-${SLOTS - 1}")
        return slot?.takeIf { it in 0 until SLOTS }
    }

    private companion object {
        val PRICE = tp.plugin.AttrKey<Long>("ge_newoffer_price")

        /** Coins and platinum tokens (cache objs 995, 13204; platinum = 1,000 coins). */
        const val COINS = 995
        const val PLATINUM = 13204
        const val PLATINUM_VALUE = 1_000L

        /** Grand Exchange offer slots (OSRS Wiki "Grand Exchange": 8 for members). */
        const val SLOTS = 8

        const val NONE = -1

        /** Slot components INDEX_0..7 (enum 245: slot n -> 465:(7 + n)). */
        val SLOT_COMPONENTS =
            listOf(
                Components.Ge_Offers.INDEX_0,
                Components.Ge_Offers.INDEX_1,
                Components.Ge_Offers.INDEX_2,
                Components.Ge_Offers.INDEX_3,
                Components.Ge_Offers.INDEX_4,
                Components.Ge_Offers.INDEX_5,
                Components.Ge_Offers.INDEX_6,
                Components.Ge_Offers.INDEX_7,
            )

        /** Collect invs per slot (enum 150): tradingpost_sell_0..5, ge_collect_6/7; slot 0 item, slot 1 coins. */
        val COLLECT_INVS = intArrayOf(518, 519, 520, 521, 522, 523, 539, 540)

        /** varps ge_itemsink_obj_0..7 (3204 + 2n); must be -1 or empty slots render as sell offers (GE5). */
        val ITEMSINK_OBJ_VARPS = (0 until SLOTS).map { 3204 + 2 * it }

        // Slot children (client script 794/798).
        const val CHILD_VIEW = 2
        const val CHILD_BUY = 3
        const val CHILD_SELL = 4

        /** Setup child 0 "Choose item"; children 0..15 (client script 773). */
        const val CHILD_CHOOSE = 0
        const val SETUP_LAST_CHILD = 15
        val QUANTITY_CHILDREN = 1..7
        val PRICE_CHILDREN = 8..13

        /** varbit ge_newoffer_type: 0 buy, 1 sell. */
        const val TYPE_BUY = 0
        const val TYPE_SELL = 1

        /** UPDATE_STOCKMARKET_SLOT status: state in bits 0-2 (2 active, 5 finished), bit 3 = sell. */
        const val STATE_ACTIVE = 2
        const val STATE_FINISHED = 5
        const val STATUS_SELL = 8

        const val TEN = 10
        const val HUNDRED = 100
        const val THOUSAND = 1000

        /** ±5% step = price / 20, at least 1 (client script 778). */
        const val FIVE_PERCENT_DIVISOR = 20L

        const val LAST_INV_SLOT = 27
        const val EXAMINE_OP = 10
    }
}
