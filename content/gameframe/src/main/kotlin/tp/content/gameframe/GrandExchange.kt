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
    /** Tax-exempt obj ids, resolved from cache names at world start (immutable afterwards). */
    private var exempt: Set<Int> = emptySet()

    override fun ContentScope.register() {
        onWorldStart { exempt = TAX_EXEMPT.flatMap { objIdsNamed(it) }.toSet() }
        onOpNpcOption("Exchange") { player.open() }
        onOpNpcOption("History") { player.history() }
        onOpNpcOption("Collect") { player.openCollectionBox() }
        onCommand("geui") { open() }
        onCommand("ge") { exchange(GeRequest.Offers) { offers -> list(offers) } }
        onCommand("gebuy") { args -> placeFromCommand(args, buy = true) }
        onCommand("gesell") { args -> placeFromCommand(args, buy = false) }
        onCommand("gecollect") { args -> slotArg(args)?.let { slot -> collect(slot, ItemForm.DEFAULT, coins = true) } }
        onCommand("geabort") { args -> slotArg(args)?.let { slot -> abort(slot) } }

        for ((n, component) in SLOT_COMPONENTS.withIndex()) onButton(component) { click -> slotButton(n, click) }
        onButton(Components.Ge_Offers.BACK) { showIndex() }
        onButton(Components.Ge_Offers.SETUP) { click -> setupButton(click) }
        onButton(Components.Ge_Offers.SETUP_CONFIRM) { confirm() }
        onButton(Components.Ge_Offers.DETAILS_STATUS) { click ->
            val slot = selectedSlot() ?: return@onButton
            if (click.slot == 0) abort(slot) else modify(slot)
        }
        onButton(Components.Ge_Offers.HISTORY) { history() }
        onButton(Components.Ge_History.EXCHANGE) { open() }
        onButton(Components.Ge_Offers.DETAILS_COLLECT) { click -> selectedSlot()?.let { collectClick(it, click.slot - 2, click.op) } }
        onButton(Components.Ge_Offers.COLLECTALL) { click ->
            if (click.slot == 0) collectAll(toBank = click.op == 2) else repeatOffer()
        }
        onButton(Components.Ge_History.LIST) { click -> if (click.op == 1) buyFromHistory(click.slot / HISTORY_ROW_CHILDREN) }
        for ((n, component) in COLLECT_BOX_COMPONENTS.withIndex()) {
            onButton(
                component,
            ) { click -> collectClick(n, click.slot - 3, click.op) }
        }
        onButton(Components.Ge_Collect.COLLECT_INV) { collectAll(toBank = false) }
        onButton(Components.Ge_Collect.COLLECT_BANK) { collectAll(toBank = true) }
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
        // GE13: an aborted offer shows as finished (completed < total) until its collect box is empty.
        val state = if (o.completed < o.quantity && !o.aborted) STATE_ACTIVE else STATE_FINISHED
        val status = state or if (o.buy) 0 else STATUS_SELL
        val gold = minOf(Int.MAX_VALUE.toLong(), o.gold).toInt()
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
                    else -> modify(slot)
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

    private fun PlayerRef.startSetup(slot: Int, buy: Boolean, choose: Boolean = true) {
        this[MODIFYING] = null
        setVarbit(Varbits.GE_SELECTEDSLOT, slot + 1)
        setVarbit(Varbits.GE_NEWOFFER_TYPE, if (buy) TYPE_BUY else TYPE_SELL)
        setVarp(Varps.TRADINGPOST_SEARCH, NONE)
        setVarbit(Varbits.GE_NEWOFFER_QUANTITY, 0)
        setPrice(0)
        setVarbit(Varbits.GE_TRANSMIT_TAXRATE, 0)
        if (buy && choose) chooseItem()
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
        setVarbit(Varbits.GE_TRANSMIT_TAXRATE, taxRate(item, buying()))
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
            CHILD_CUSTOM_DOWN, CHILD_CUSTOM_UP -> customPercent(click)
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
        if (this[MODIFYING] == slot) {
            // OPEN-GE3: Modify replaces the offer: abort it (everything comes back), then place the new one in its slot.
            this[MODIFYING] = null
            val buy = buying()
            return exchange(GeRequest.Abort(slot)) {
                takeBack(
                    GeRequest.Collect(slot),
                    ItemForm.DEFAULT,
                    "modify",
                ) { place(slot, buy, obj, quantity, price.toInt()) { showIndex() } }
            }
        }
        place(slot, buying(), obj, quantity, price.toInt()) { showIndex() }
    }

    /** OPEN-GE3: opens the setup screen filled from the offer in [slot]; Confirm replaces the offer. */
    private fun PlayerRef.modify(slot: Int) {
        exchange(GeRequest.Offers) { offers ->
            val o = offers?.firstOrNull { it.slot == slot && it.completed < it.quantity } ?: return@exchange
            setVarbit(Varbits.GE_SELECTEDSLOT, slot + 1)
            setVarbit(Varbits.GE_NEWOFFER_TYPE, if (o.buy) TYPE_BUY else TYPE_SELL)
            setVarp(Varps.TRADINGPOST_SEARCH, o.obj)
            setVarbit(Varbits.GE_NEWOFFER_QUANTITY, o.quantity - o.completed)
            setPrice(o.price.toLong())
            ifText(Components.Ge_Offers.SETUP_MARKETPRICE, "%,d coins".format(objValue(o.obj)))
            this[MODIFYING] = slot
        }
    }

    /** History panel 383 (client scripts 1644 init, 1645 row, 1646 finish): newest trades first. */
    private fun PlayerRef.history() {
        exchange(GeRequest.History) { rows ->
            if (rows == null) return@exchange message("The Grand Exchange is unavailable.")
            SideInventory.openMain(this, Interfaces.GE_HISTORY)
            this[HISTORY_OBJS] = rows.map { it.obj }
            ifEvents(Components.Ge_History.LIST, 0 until rows.size * HISTORY_ROW_CHILDREN, ops(1, EXAMINE_OP))
            clientScript(CS_HISTORY_INIT)
            rows.forEachIndexed { i, o ->
                // Row: index, obj, 0 bought / 1 sold, amount traded, coins (long), fee (long).
                clientScript(CS_HISTORY_ROW, i, o.obj, if (o.buy) 0 else 1, o.completed, o.gold, o.tax)
            }
            clientScript(CS_HISTORY_DONE)
        }
    }

    /** Setup children 14/15: op 2 "Customise" asks for N; op 1 applies −N% / +N% (step price × N / 100, at least 1). */
    private fun PlayerRef.customPercent(click: ButtonClick) {
        val percent = varbit(Varbits.GE_PRICE_CUSTOM)
        if (click.op == 2 || percent == 0) {
            countDialog("Set a percentage to adjust the price by:") { n ->
                setVarbit(Varbits.GE_PRICE_CUSTOM, n.coerceIn(1, MAX_CUSTOM_PERCENT))
            }
            return
        }
        val step = maxOf(1L, price() * percent / PERCENT)
        setPrice(clampPrice(if (click.slot == CHILD_CUSTOM_DOWN) price() - step else price() + step))
    }

    private fun PlayerRef.place(slot: Int, buy: Boolean, obj: Int, quantity: Int, price: Int, after: PlayerRef.() -> Unit = {}) {
        val cost = quantity.toLong() * price
        if (buy && cost > Int.MAX_VALUE) return message("That offer is too large.")
        exchange(GeRequest.Offers) { offers ->
            if (offers == null) return@exchange message("The Grand Exchange is unavailable.")
            if (offers.any { it.slot == slot }) return@exchange message("That offer slot is in use.")
            val taken = if (buy) takeCoins(cost.toInt()) else takeItems(obj, quantity)
            if (taken == null) return@exchange message(if (buy) "You don't have enough coins." else "You don't have enough of that item.")
            exchange(GeRequest.Place(slot, buy, obj, quantity, price, taxRate(obj, buy))) { placed ->
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

    /** GE14: 2% (20 tenths of a percent) on sells, 0 on buys and tax-exempt items; the client shows the fee from it. */
    private fun taxRate(obj: Int, buy: Boolean): Int = if (buy || obj in exempt) 0 else TAX_RATE

    /** GE13: aborting moves the unfilled part to the collect box; the player collects it like anything else. */
    private fun PlayerRef.abort(slot: Int) {
        exchange(GeRequest.Abort(slot)) { offers ->
            if (offers.isNullOrEmpty()) return@exchange message("Nothing to abort.")
            if (isOpen(Interfaces.GE_OFFERS)) refresh() else message("Offer aborted; collect it at the Grand Exchange.")
        }
    }

    /**
     * A click on a collect slot (child 0 = item part, 1 = coins), from the offer details or the collection box (client
     * scripts 816/818/9909): item with a noted form, count 1: op 1 item, op 2 note; count > 1: op 1 notes, op 2 items;
     * no noted form: op 2. Op 3 sends it to the bank.
     */
    private fun PlayerRef.collectClick(slot: Int, part: Int, op: Int) {
        if (part == 1) return collect(slot, if (op == BANK_OP) ItemForm.BANK else ItemForm.DEFAULT, coins = true, items = false)
        exchange(GeRequest.Offers) { offers ->
            val o = offers?.firstOrNull { it.slot == slot } ?: return@exchange
            val form =
                when {
                    op == BANK_OP -> ItemForm.BANK
                    noted(o.obj) < 0 -> ItemForm.UNNOTED
                    o.collectItems == 1 -> if (op == 1) ItemForm.UNNOTED else ItemForm.NOTED
                    else -> if (op == 1) ItemForm.NOTED else ItemForm.UNNOTED
                }
            collect(slot, form, coins = false)
        }
    }

    private fun PlayerRef.collect(slot: Int, form: ItemForm, coins: Boolean, items: Boolean = true) =
        takeBack(GeRequest.Collect(slot, items, coins), form, "collect")

    private fun PlayerRef.collectAll(toBank: Boolean) {
        exchange(GeRequest.Offers) { offers ->
            offers
                ?.filter { it.collectCoins > 0 || it.collectItems > 0 }
                ?.forEach { collect(it.slot, if (toBank) ItemForm.BANK else ItemForm.DEFAULT, coins = true) }
        }
    }

    /** "Repeat Offer" (client script 796): the last offer's type, item, quantity and price in the first free slot. */
    private fun PlayerRef.repeatOffer() {
        val obj = varp(Varps.GE_LAST_OFFER_ITEM)
        if (obj <= 0) return
        exchange(GeRequest.Offers) { offers ->
            val free =
                (0 until SLOTS).firstOrNull { s -> offers?.none { it.slot == s } == true }
                    ?: return@exchange message("All your offer slots are in use.")
            val buy = varp(Varps.GE_LAST_OFFER_TYPE) == TYPE_BUY
            startSetup(free, buy, choose = false)
            select(obj)
            setVarbit(Varbits.GE_NEWOFFER_QUANTITY, varp(Varps.GE_LAST_OFFER_QUANTITY).coerceAtLeast(1))
            setPrice(varp(Varps.GE_LAST_OFFER_PRICE).toLong().coerceAtLeast(1L))
        }
    }

    /** History row "Buy-offer" (client script 1645): a buy offer for that item in the first free slot. */
    private fun PlayerRef.buyFromHistory(row: Int) {
        val obj = this[HISTORY_OBJS]?.getOrNull(row) ?: return
        open()
        exchange(GeRequest.Offers) { offers ->
            val free =
                (0 until SLOTS).firstOrNull { s -> offers?.none { it.slot == s } == true }
                    ?: return@exchange message("All your offer slots are in use.")
            startSetup(free, buy = true, choose = false)
            select(obj)
        }
    }

    /** The collection box (402 `ge_collect`, client script 788): every slot's collect inv, without the offer screen. */
    private fun PlayerRef.openCollectionBox() {
        SideInventory.openMain(this, Interfaces.GE_COLLECT)
        for (c in COLLECT_BOX_COMPONENTS) ifEvents(c, 3..4, ops(1, 2, 3, EXAMINE_OP))
        refresh()
    }

    /** How a collected item part arrives. */
    private enum class ItemForm { DEFAULT, NOTED, UNNOTED, BANK }

    /**
     * Hands over what the hub released. Backpack first; what doesn't fit goes to the ground under the player (private
     * to them), so nothing is lost. [ItemForm.BANK] puts items (unnoted) and coins in the bank when there's room.
     */
    private fun PlayerRef.takeBack(request: GeRequest, form: ItemForm, what: String, after: (PlayerRef.() -> Unit)? = null) {
        exchange(request) { offers ->
            val o = offers?.firstOrNull() ?: return@exchange message("Nothing to $what.")
            val coins = minOf(o.collectCoins, Int.MAX_VALUE.toLong()).toInt()
            val noted = noted(o.obj)
            val itemObj =
                when (form) {
                    ItemForm.NOTED -> if (noted >= 0) noted else o.obj
                    ItemForm.UNNOTED, ItemForm.BANK -> o.obj
                    ItemForm.DEFAULT -> if (o.collectItems > 1 && noted >= 0) noted else o.obj
                }
            val before = inv(Invs.INV).freeSlots
            transaction("ge $what") {
                val bank = form == ItemForm.BANK
                val coinsOk = coins == 0 || (bank && add(Invs.BANK, COINS, coins)) || addOrDrop(Invs.INV, COINS, coins)
                coinsOk &&
                    (
                        o.collectItems == 0 || (bank && add(Invs.BANK, itemObj, o.collectItems)) ||
                            addOrDrop(Invs.INV, itemObj, o.collectItems)
                    )
            }
            if (inv(Invs.INV).freeSlots == 0 && before < 2) message("Some items were placed on the ground as your inventory is full.")
            if (after != null) return@exchange after()
            if (isOpen(Interfaces.GE_OFFERS) || isOpen(Interfaces.GE_COLLECT)) {
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
        val MODIFYING = tp.plugin.AttrKey<Int>("ge_modifying_slot")
        val HISTORY_OBJS = tp.plugin.AttrKey<List<Int>>("ge_history_objs")

        /** History rows are 6 created components each; the first carries the ops (client script 1645). */
        const val HISTORY_ROW_CHILDREN = 6

        /** GE14: "Most transactions ... are subject to a 2% tax" (OSRS Wiki "Grand Exchange"), in tenths of a percent. */
        const val TAX_RATE = 20

        /** GE14: OSRS Wiki "Grand Exchange#Exempt from tax", by cache name. */
        val TAX_EXEMPT =
            listOf(
                "Old school bond",
                "Energy potion(4)",
                "Energy potion(3)",
                "Energy potion(2)",
                "Energy potion(1)",
                "Bronze arrow",
                "Bronze dart",
                "Iron arrow",
                "Iron dart",
                "Mind rune",
                "Steel arrow",
                "Steel dart",
                "Bass",
                "Bread",
                "Cake",
                "Cooked chicken",
                "Cooked meat",
                "Herring",
                "Lobster",
                "Mackerel",
                "Meat pie",
                "Pike",
                "Salmon",
                "Shrimps",
                "Tuna",
                "Ardougne teleport",
                "Camelot teleport",
                "Civitas illa fortis teleport",
                "Falador teleport",
                "Games necklace(8)",
                "Kourend castle teleport",
                "Lumbridge teleport",
                "Ring of dueling(8)",
                "Teleport to house",
                "Varrock teleport",
                "Chisel",
                "Gardening trowel",
                "Glassblowing pipe",
                "Hammer",
                "Needle",
                "Pestle and mortar",
                "Rake",
                "Saw",
                "Secateurs",
                "Seed dibber",
                "Shears",
                "Spade",
                "Watering can(8)",
            )

        /** Collect op 3 "Bank". */
        const val BANK_OP = 3

        /** Collection box slots COLLECT_0..7 (enum 1011). */
        val COLLECT_BOX_COMPONENTS =
            listOf(
                Components.Ge_Collect.COLLECT_0,
                Components.Ge_Collect.COLLECT_1,
                Components.Ge_Collect.COLLECT_2,
                Components.Ge_Collect.COLLECT_3,
                Components.Ge_Collect.COLLECT_4,
                Components.Ge_Collect.COLLECT_5,
                Components.Ge_Collect.COLLECT_6,
                Components.Ge_Collect.COLLECT_7,
            )

        /** Client scripts of the history panel 383 (cache 2757). */
        const val CS_HISTORY_INIT = 1644
        const val CS_HISTORY_ROW = 1645
        const val CS_HISTORY_DONE = 1646

        /** Setup children 14 (−N%) and 15 (+N%); N in varbit ge_price_custom (7 bits, OPEN-GE5 limit 1..100). */
        const val CHILD_CUSTOM_DOWN = 14
        const val CHILD_CUSTOM_UP = 15
        const val MAX_CUSTOM_PERCENT = 100
        const val PERCENT = 100L

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
