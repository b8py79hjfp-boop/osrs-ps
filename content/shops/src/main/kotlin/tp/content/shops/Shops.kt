package tp.content.shops

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import java.util.Locale

/**
 * NPC shops (docs/mechanics/shops.md): the "Trade" op opens `shopmain` (300) as the main modal with `shopside` (301)
 * over the backpack. Stock, restocking and prices live in [ShopStock] / [ShopPricing]; this class is the interface and
 * the buy / sell actions, with the rules and messages of RS Mod `api/shops` (ISC, DECISIONS D-032) and the Wiki. The
 * stock is per world (plugins are shared by every world in the JVM) and every purchase or sale is one item transaction.
 */
public class Shops : ContentPlugin {
    private val catalog: ShopCatalog by lazy { ShopCatalog.load() }

    /** A player's open shop: its stock and the stock version the client last received. */
    private class Viewing(val stock: ShopStock, var seen: Int)

    /** World-scoped state: the stock of every shop that has been opened since the world started. */
    private class WorldShops {
        val stocks = HashMap<Int, ShopStock>()
    }

    override fun ContentScope.register() {
        onOpNpcOption("Trade") { npc ->
            val def = catalog.forNpc(npc.type) ?: catalog.forNpc(player.seenType(npc))
            if (def == null) player.message("This shop isn't available yet.") else player.open(def)
        }
        events.on<OpenShopRequest> { e ->
            val def = e.shopName?.let { n -> catalog.shops.firstOrNull { it.name == n } } ?: catalog.forNpc(e.npcType)
            if (def == null) e.player.message("This shop isn't available yet.") else e.player.open(def)
        }
        onButton(Components.Shopmain.ITEMS) { click -> shopItem(click) }
        onButton(Components.Shopside.ITEMS) { click -> sideItem(click) }
        // The quantity buttons of the item panel: the client switches its own `shop_quantity` at once; the server keeps the
        // same value because that decides what the left click on an item means (client script 1076, op 1).
        onButton(Components.Shopmain.VALUE) { quantityMode(MODE_VALUE) }
        onButton(Components.Shopmain.QUANTITY1) { quantityMode(MODE_ONE) }
        onButton(Components.Shopmain.QUANTITY5) { quantityMode(MODE_FIVE) }
        onButton(Components.Shopmain.QUANTITY10) { quantityMode(MODE_TEN) }
        onButton(Components.Shopmain.QUANTITY50) { quantityMode(MODE_FIFTY) }
        onModalClosed(Interfaces.SHOPMAIN) { closed() }
        onLogout { closed() }
    }

    // ---- opening and closing

    private fun PlayerRef.stockOf(def: ShopDef): ShopStock {
        val world = worldAttr(WORLD) ?: WorldShops().also { setWorldAttr(WORLD, it) }
        return world.stocks.getOrPut(def.id) { ShopStock(def, tick) }
    }

    private fun PlayerRef.open(def: ShopDef) {
        // Opening a modal closes the current one first, which runs [closed] for a shop that was open.
        clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        openModal(Components.Toplevel.MAINMODAL, Interfaces.SHOPMAIN)
        openOverlay(Components.Toplevel.SIDE3, Interfaces.SHOPSIDE)
        val stock = stockOf(def)
        stock.sync(tick)
        transmitInv(Invs.INV)
        val viewing = Viewing(stock, -1)
        this[VIEWING] = viewing
        push(viewing)
        clientScript(CS_SHOP_MAIN_INIT, def.inv, def.name, -1, 0, 1)
        ifEvents(Components.Shopmain.ITEMS, FIRST_SHOP_CHILD..def.slots, SHOP_EVENTS)
        clientScript(
            CS_INTERFACE_INV_INIT,
            Components.Shopside.ITEMS,
            Invs.INV,
            GRID_ROWS,
            GRID_COLUMNS,
            0,
            -1,
            "Value<col=ff9040>",
            "Sell 1<col=ff9040>",
            "Sell 5<col=ff9040>",
            "Sell 10<col=ff9040>",
            "Sell 50<col=ff9040>",
        )
        ifEvents(Components.Shopside.ITEMS, 0..LAST_BACKPACK_SLOT, SIDE_EVENTS)
        timer(REFRESH, 1, soft = true) { refresh() }
    }

    /** The shop modal closed, however it did (walking away, another interface, logout): the backpack comes back. */
    private fun PlayerRef.closed() {
        if (this[VIEWING] == null) return
        this[VIEWING] = null
        stopTimer(REFRESH)
        openOverlay(Components.Toplevel.SIDE3, Interfaces.INVENTORY)
        ifEvents(Components.Inventory.ITEMS, 0..LAST_BACKPACK_SLOT, BACKPACK_EVENTS)
    }

    /** Sends the shop inv (display only: the stock is not a player inventory). */
    private fun PlayerRef.push(viewing: Viewing) {
        val stock = viewing.stock
        showInv(stock.def.inv, stock.objs.copyOf(), stock.counts.copyOf())
        viewing.seen = stock.version
    }

    /** Every tick while the shop is open: restocks that fell due, and other players' purchases, reach the client. */
    private fun PlayerRef.refresh() {
        val viewing = this[VIEWING] ?: return stopTimer(REFRESH)
        viewing.stock.sync(tick)
        if (viewing.seen != viewing.stock.version) push(viewing)
    }

    private fun PlayerRef.quantityMode(mode: Int) {
        if (this[VIEWING] == null) return
        setVarbit(Varbits.SHOP_QUANTITY, mode)
    }

    // ---- shop item clicks (buying)

    private fun PlayerRef.shopItem(click: ButtonClick) {
        val viewing = this[VIEWING] ?: return
        val stock = viewing.stock
        val slot = click.slot - FIRST_SHOP_CHILD
        // The client names the item it clicked; it must be what the server has in that slot (brief §12).
        if (slot !in stock.objs.indices || stock.objs[slot] < 0 || stock.objs[slot] != click.obj) return
        val mode = varbit(Varbits.SHOP_QUANTITY).takeIf { it in MODE_ONE..MODE_FIFTY } ?: MODE_VALUE
        when (click.op) {
            OP_LEFT -> if (mode == MODE_VALUE) shopValue(viewing, slot) else buy(viewing, slot, QUANTITIES[mode])
            OP_VALUE -> shopValue(viewing, slot)
            OP_BUY_1 -> buy(viewing, slot, QUANTITIES[MODE_ONE])
            OP_BUY_5 -> buy(viewing, slot, QUANTITIES[MODE_FIVE])
            OP_BUY_10 -> buy(viewing, slot, QUANTITIES[MODE_TEN])
            OP_BUY_50 -> buy(viewing, slot, QUANTITIES[MODE_FIFTY])
            else -> Unit // Examine (op 10): no examine texts exist yet.
        }
    }

    private fun PlayerRef.shopValue(viewing: Viewing, slot: Int) {
        val stock = viewing.stock
        stock.sync(tick)
        val obj = stock.objs[slot]
        if (obj < 0) return
        val price = ShopPricing.buyPrice(stock.def, objCost(obj), stock.item(slot)?.sellPrice ?: -1, stock.baseOf(slot), stock.counts[slot])
        message("${objName(obj)}: currently costs ${amountText(price, stock.def.currency)}.")
    }

    private fun PlayerRef.buy(viewing: Viewing, slot: Int, request: Int) {
        val stock = viewing.stock
        val def = stock.def
        stock.sync(tick)
        val obj = stock.objs[slot]
        if (obj < 0 || stock.counts[slot] <= 0) {
            push(viewing)
            return message(OUT_OF_STOCK)
        }
        val value = objCost(obj)
        val fixed = stock.item(slot)?.sellPrice ?: -1
        val purse = minOf(inv(Invs.INV).total(def.currency), Int.MAX_VALUE.toLong())
        val wanted = minOf(request, stock.counts[slot])
        val room = roomFor(obj, wanted)
        var count = 0
        var total = 0L
        var short = false
        while (count < minOf(wanted, room)) {
            val price = ShopPricing.buyPrice(def, value, fixed, stock.baseOf(slot), stock.counts[slot] - count).toLong()
            if (total + price > purse) {
                short = true
                break
            }
            total += price
            count++
        }
        if (count == 0) {
            val first = ShopPricing.buyPrice(def, value, fixed, stock.baseOf(slot), stock.counts[slot])
            return message(if (first > purse) notEnough(def) else NOT_ENOUGH_SPACE)
        }
        val ok = transaction("shop buy") { (total == 0L || remove(Invs.INV, def.currency, total.toInt())) && add(Invs.INV, obj, count) }
        if (!ok) return message(NOT_ENOUGH_SPACE)
        stock.take(slot, count)
        push(viewing)
        // RS Mod StandardGpShopOperations: a short purchase says which limit stopped it.
        if (count < wanted) message(if (short) notEnough(def) else NOT_ENOUGH_SPACE)
    }

    private fun PlayerRef.notEnough(def: ShopDef): String =
        "You don't have enough ${if (def.currency == COINS) "coins" else objName(def.currency).lowercase()}."

    /** How many of [obj] (up to [wanted]) the backpack can take: free slots for loose items, one slot or a stack for stackables. */
    private fun PlayerRef.roomFor(obj: Int, wanted: Int): Int {
        val backpack = inv(Invs.INV)
        if (!objStackable(obj)) return minOf(wanted, backpack.freeSlots)
        val held = backpack.total(obj)
        if (held == 0L && backpack.freeSlots == 0) return 0
        return minOf(wanted.toLong(), Int.MAX_VALUE - held).toInt().coerceAtLeast(0)
    }

    // ---- backpack item clicks (selling)

    private fun PlayerRef.sideItem(click: ButtonClick) {
        val viewing = this[VIEWING] ?: return
        val backpack = inv(Invs.INV)
        if (click.slot !in 0 until backpack.size || backpack.obj(click.slot) != click.obj || click.obj < 0) return
        when (click.op) {
            OP_LEFT -> sideValue(viewing, click.obj)
            OP_SELL_1 -> sell(viewing, click.obj, 1)
            OP_SELL_5 -> sell(viewing, click.obj, QUANTITIES[MODE_FIVE])
            OP_SELL_10 -> sell(viewing, click.obj, QUANTITIES[MODE_TEN])
            OP_SELL_50 -> sell(viewing, click.obj, QUANTITIES[MODE_FIFTY])
            else -> Unit // Examine (op 10): no examine texts exist yet.
        }
    }

    /** Why the shop would not take [obj], or null when it would. [obj] is the unnoted item. */
    private fun PlayerRef.refusal(stock: ShopStock, obj: Int): String? =
        when {
            !objTradeable(obj) && !isGeTradeable(obj) -> "You can't sell this item."
            obj == COINS || obj == stock.def.currency -> "You can't sell this item to this shop."
            !stock.def.general && stock.slotOf(obj) < 0 -> "You can't sell this item to this shop."
            else -> null
        }

    private fun PlayerRef.sideValue(viewing: Viewing, clicked: Int) {
        val stock = viewing.stock
        stock.sync(tick)
        val obj = unnoted(clicked)
        refusal(stock, obj)?.let { return message(it) }
        val slot = stock.slotOf(obj)
        val price = sellPriceAt(stock, obj, slot, if (slot >= 0) stock.counts[slot] else 0)
        message("${objName(obj)}: shop will buy for ${amountText(price, stock.def.currency)}.")
    }

    private fun PlayerRef.sellPriceAt(stock: ShopStock, obj: Int, slot: Int, held: Int): Int =
        ShopPricing.sellPrice(
            stock.def,
            objCost(obj),
            stock.item(slot)?.buyPrice ?: -1,
            if (slot >= 0) stock.baseOf(slot) else 0,
            held,
        )

    private fun PlayerRef.sell(viewing: Viewing, clicked: Int, request: Int) {
        val stock = viewing.stock
        val def = stock.def
        stock.sync(tick)
        val obj = unnoted(clicked)
        refusal(stock, obj)?.let { return message(it) }
        val count = minOf(request.toLong(), inv(Invs.INV).total(clicked)).toInt()
        if (count <= 0) return
        val slot = stock.slotOf(obj)
        val held = if (slot >= 0) stock.counts[slot] else 0
        if ((slot < 0 && stock.freeSlot() < 0) || held.toLong() + count > Int.MAX_VALUE) return message(SHOP_FULL)
        var payment = 0L
        for (i in 0 until count) payment += sellPriceAt(stock, obj, slot, held + i)
        val coinsHeld = inv(Invs.INV).total(def.currency)
        val ok =
            coinsHeld + payment <= Int.MAX_VALUE &&
                transaction("shop sell") {
                    remove(Invs.INV, clicked, count) && (payment == 0L || add(Invs.INV, def.currency, payment.toInt()))
                }
        if (!ok) return message(NOT_ENOUGH_SPACE)
        stock.put(obj, count)
        push(viewing)
    }

    private fun PlayerRef.amountText(amount: Int, currency: Int): String {
        val number = String.format(Locale.US, "%,d", amount)
        return when {
            currency != COINS -> "$number ${objName(currency).lowercase()}"
            amount == 1 -> "1 coin"
            else -> "$number coins"
        }
    }

    private companion object {
        val WORLD = AttrKey<WorldShops>("shops_world")
        val VIEWING = AttrKey<Viewing>("shops_viewing")
        val REFRESH = TimerId("shops_refresh")

        /** Cache obj 995 "Coins". */
        const val COINS = 995

        /** Client script `toplevel_mainmodal_open`: sets the main modal background (colour, transparency; -1 = default). */
        const val CS_MAINMODAL_BACKGROUND = 2524

        /** Client script 1074 (RS Mod `ClientScripts.shopMainInit`): inv, title, custom buy obj, custom amount, "Buy 50" shown. */
        const val CS_SHOP_MAIN_INIT = 1074

        /** Client script `interface_inv_init`: binds an inv to a grid component (RS Mod `ClientScripts.interfaceInvInit`). */
        const val CS_INTERFACE_INV_INIT = 149
        const val GRID_ROWS = 4
        const val GRID_COLUMNS = 7
        const val LAST_BACKPACK_SLOT = 27

        /** Client script 1076 creates the item buttons as children 1..size of `shopmain:items`; IF_BUTTON's slot is that child. */
        const val FIRST_SHOP_CHILD = 1

        /** `shop_quantity` (varbit 6348) values and the amount each stands for: client script 2228 ("Value", "Buy 1" ... "Buy 50"). */
        const val MODE_VALUE = 0
        const val MODE_ONE = 1
        const val MODE_FIVE = 2
        const val MODE_TEN = 3
        const val MODE_FIFTY = 4
        val QUANTITIES = intArrayOf(0, 1, 5, 10, 50)

        /**
         * Ops of a shop item (client script 1076): op 1 is "Value" or, once a quantity is chosen, "Buy N"; ops 2..5 are
         * "Buy 1/5/10/50"; op 6 is "Value" while op 1 is a purchase; op 10 is Examine.
         */
        const val OP_LEFT = 1
        const val OP_BUY_1 = 2
        const val OP_BUY_5 = 3
        const val OP_BUY_10 = 4
        const val OP_BUY_50 = 5
        const val OP_VALUE = 6
        const val OP_SELL_1 = 2
        const val OP_SELL_5 = 3
        const val OP_SELL_10 = 4
        const val OP_SELL_50 = 5

        @Suppress("MagicNumber") // op numbers
        val SHOP_EVENTS: Long =
            IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(6) or IfEvents.op(10)

        @Suppress("MagicNumber") // op numbers
        val SIDE_EVENTS: Long =
            IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(10)

        /** The backpack tab's events (gameframe `SideInventory.ITEM_EVENTS`), re-enabled when the shop gives the slot back. */
        @Suppress("MagicNumber") // op numbers
        val BACKPACK_EVENTS: Long =
            IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(6) or IfEvents.op(7) or IfEvents.op(10) or
                IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
                IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET

        /** RS Mod `StandardGpShopOperations` messages (ISC). */
        const val NOT_ENOUGH_SPACE = "You don't have enough inventory space."
        const val OUT_OF_STOCK = "That item is currently out of stock."

        /** OPEN-SH5: no source for the full-shop message. */
        const val SHOP_FULL = "The shop is too full to buy that item."
    }
}
