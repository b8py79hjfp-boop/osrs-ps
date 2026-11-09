package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.TpListingView
import tp.plugin.TpReply
import tp.plugin.TpRequest
import tp.plugin.tradingPost
import tp.symbols.Clientscripts
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * The Trading Post (docs/mechanics/trading-post.md TP1-TP6, a custom feature, D-070): the packed `trading_post` interface
 * drawn by the `trading_post_*` clientscripts. Listings live in the hub; every coin and item moves through an item
 * transaction here, before the hub call (list, buy) or after it (collect), and is refunded when the hub refuses.
 */
public class TradingPost : ContentPlugin {
    override fun ContentScope.register() {
        onCommand("tradingpost") { open() }
        onScriptTrigger(Components.Trading_Post.SEARCHTEXT, "iis") { t ->
            state().query = (t.args[2] as String).trim()
            state().history = false
            refresh()
        }
        onButton(Components.Trading_Post.RADIO_ITEM) { setMode(byPlayer = false) }
        onButton(Components.Trading_Post.RADIO_PLAYER) { setMode(byPlayer = true) }
        onButton(Components.Trading_Post.SHOWALL) {
            state().query = ""
            state().history = false
            clientScript(Clientscripts.TRADING_POST_SEARCH_CLEAR, Components.Trading_Post.SEARCHTEXT)
            refresh()
        }
        onButton(Components.Trading_Post.HDR_ITEM) { sort(SORT_ITEM) }
        onButton(Components.Trading_Post.HDR_PRICE) { sort(SORT_PRICE) }
        onButton(Components.Trading_Post.HDR_SELLER) { sort(SORT_SELLER) }
        onButton(Components.Trading_Post.HDR_AGE) { sort(SORT_AGE) }
        onButton(Components.Trading_Post.HISTORYBTN) {
            state().history = !state().history
            refresh()
        }
        onButton(Components.Trading_Post.LIST) { click -> listClick(click) }
        onButton(Components.Trading_Post.MINE) { click -> mineClick(click) }
        onButton(Components.Trading_Post.COLLECTBTN) { collect() }
        onButton(Components.Trading_Post.SELLBTN) { sell() }
        onModalClosed(Interfaces.TRADING_POST) {
            this[STATE] = null
            // Closed while its search box had the keyboard: give the chatbox its input back.
            clientScript(Clientscripts.CHAT_RESTORE_INPUT)
        }
    }

    /** Per-player view state while the interface is open. */
    private class View {
        var query = ""
        var byPlayer = false
        var sortCol = SORT_AGE
        var descending = true
        var history = false
        var rows: List<TpListingView> = emptyList()
        var mine: List<TpListingView> = emptyList()

        /** Request counters: hub replies arrive asynchronously, so only the newest request of each kind draws. */
        var tableSeq = 0
        var mineSeq = 0
    }

    private fun PlayerRef.state(): View = this[STATE] ?: View().also { this[STATE] = it }

    private fun PlayerRef.open() {
        if (!isOpen(Interfaces.TRADING_POST)) {
            this[STATE] = View()
            openModal(Components.Toplevel.MAINMODAL, Interfaces.TRADING_POST)
            ifEvents(Components.Trading_Post.LIST, 0..LIST_SUBS, IfEvents.op(1))
            ifEvents(Components.Trading_Post.MINE, 0..MINE_SUBS, IfEvents.op(1))
            ifEvents(Components.Trading_Post.SEARCHTEXT, -1..-1, IfEvents.SCRIPT_TRIGGER)
        }
        refresh()
    }

    private fun PlayerRef.setMode(byPlayer: Boolean) {
        state().byPlayer = byPlayer
        refresh()
    }

    private fun PlayerRef.sort(col: Int) {
        val v = state()
        if (v.sortCol == col) v.descending = !v.descending else v.sortCol = col
        refresh()
    }

    // ---- drawing

    private fun PlayerRef.refresh() {
        if (!isOpen(Interfaces.TRADING_POST)) return
        val v = state()
        clientScript(Clientscripts.TRADING_POST_BUTTONS, if (v.byPlayer) 1 else 0, v.sortCol, if (v.history) 1 else 0)
        if (v.history) showHistory() else showListings(v)
        showMine()
    }

    private fun PlayerRef.showListings(v: View) {
        val query = v.query.lowercase()
        val objs = if (query.isEmpty() || v.byPlayer) null else matchingObjs(query)
        if (objs != null && objs.isEmpty()) {
            v.tableSeq++
            return drawRows(emptyList(), "", MODE_LISTINGS)
        }

        val seller = if (v.byPlayer && query.isNotEmpty()) query else null
        val seq = ++v.tableSeq
        tradingPost(TpRequest.Search(objs, seller, MAX_ROWS)) { reply ->
            val view = this[STATE]?.takeIf { it.tableSeq == seq } ?: return@tradingPost
            view.rows = sorted(reply?.listings.orEmpty(), view)
            val now = System.currentTimeMillis()
            val data =
                view.rows.joinToString("|") { "${commas(it.price.toLong())};${it.seller};${if (it.accountId == accountId) 1 else 0}" }
            drawRows(view.rows.map { Triple(it.obj, it.remaining, ageSeconds(it.listedAtMs, now)) }, data, MODE_LISTINGS)
        }
    }

    private fun PlayerRef.showHistory() {
        val seq = ++state().tableSeq
        tradingPost(TpRequest.History) { reply ->
            val view = this[STATE]?.takeIf { it.tableSeq == seq } ?: return@tradingPost
            view.rows = emptyList()
            val trades = reply?.trades.orEmpty().take(MAX_ROWS)
            val now = System.currentTimeMillis()
            val data = trades.joinToString("|") { "${if (it.bought) "Bought" else "Sold"};${commas(it.price.toLong())};${it.other}" }
            drawRows(trades.map { Triple(it.obj, it.quantity, ageSeconds(it.atMs, now)) }, data, MODE_HISTORY)
        }
    }

    /** Rows: (obj, quantity, age in seconds); the ages ride in inv `tp_ages` (coins, count = seconds) for the client's clock. */
    private fun PlayerRef.drawRows(items: List<Triple<Int, Int, Long>>, data: String, mode: Int) {
        showInv(
            Invs.TP_AGES,
            IntArray(items.size) {
                Objs.COINS
            },
            IntArray(items.size) { items[it].third.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() },
        )
        showInv(Invs.TP_RESULTS, IntArray(items.size) { items[it].first }, IntArray(items.size) { items[it].second })
        clientScript(
            Clientscripts.TRADING_POST_ROWS,
            Components.Trading_Post.LIST,
            Components.Trading_Post.LIST_SCROLLBAR,
            Components.Trading_Post.STATUS,
            data,
            mode,
        )
    }

    private fun PlayerRef.showMine() {
        val seq = ++state().mineSeq
        tradingPost(TpRequest.Mine) { mine ->
            tradingPost(TpRequest.Pending) { box ->
                val view = this[STATE]?.takeIf { it.mineSeq == seq } ?: return@tradingPost
                view.mine = mine?.listings.orEmpty()
                val l = view.mine
                showInv(Invs.TP_MINE, IntArray(l.size) { l[it].obj }, IntArray(l.size) { l[it].quantity })
                clientScript(
                    Clientscripts.TRADING_POST_MINE,
                    Components.Trading_Post.MINE,
                    Components.Trading_Post.MINE_SCROLLBAR,
                    Components.Trading_Post.MINE_TITLE,
                    Components.Trading_Post.COLLECTBTN,
                    l.joinToString("|") { "${commas(it.price.toLong())};${it.sold}/${it.quantity}" },
                    l.size,
                    MAX_LISTINGS,
                    collectText(box),
                )
            }
        }
    }

    private fun collectText(box: TpReply?): String =
        when {
            box == null || (box.coins == 0L && box.items.isEmpty()) -> "Nothing to collect"
            box.items.isEmpty() -> "${commas(box.coins)} coins"
            box.coins == 0L -> "${box.items.values.sum()} items"
            else -> "${commas(box.coins)} gp + items"
        }

    // ---- actions

    /** TP2: a row of the table: "Buy" someone's listing, "Cancel" your own, op 2 examines. */
    private fun PlayerRef.listClick(click: ButtonClick) {
        val v = state()
        if (v.history) return
        val row = v.rows.getOrNull(click.slot / LIST_CHILDREN) ?: return
        if (row.accountId == accountId) return cancel(row)
        countDialog("How many would you like to buy? (${row.remaining} for ${commas(row.price.toLong())} each)") { wanted ->
            buy(row, wanted)
        }
    }

    private fun PlayerRef.buy(row: TpListingView, wanted: Int) {
        val n = minOf(wanted, row.remaining)
        if (n <= 0) return
        val cost = n.toLong() * row.price
        if (cost > Int.MAX_VALUE) return message("You can't buy that many at once.")
        if (!transaction("trading post pay") { remove(Invs.INV, Objs.COINS, cost.toInt()) }) {
            return message("You don't have enough coins: that costs ${commas(cost)} gp.")
        }
        tradingPost(TpRequest.Buy(row.id, n, row.price)) { reply ->
            val bought = reply?.bought ?: 0
            val refund = (n - bought).toLong() * row.price
            if (refund > 0) transaction("trading post refund") { addOrDrop(Invs.INV, Objs.COINS, refund.toInt()) }
            if (bought > 0) {
                transaction("trading post buy") { addOrDrop(Invs.INV, row.obj, bought) }
                message("You buy ${commas(bought.toLong())} x ${objName(row.obj)} for ${commas(bought.toLong() * row.price)} gp.")
            } else {
                message(reply?.error ?: "The trading post is unavailable right now.")
            }
            refresh()
        }
    }

    private fun PlayerRef.mineClick(click: ButtonClick) {
        val row = state().mine.getOrNull(click.slot / MINE_CHILDREN) ?: return
        cancel(row)
    }

    /** TP3: cancelling moves the unsold rest to the collection box. */
    private fun PlayerRef.cancel(row: TpListingView) {
        tradingPost(TpRequest.Cancel(row.id)) { reply ->
            if (reply?.listings.isNullOrEmpty()) message("That listing has already sold.") else message("Your listing has been cancelled.")
            refresh()
        }
    }

    /** TP3: everything waiting, if the backpack can take it (nothing is taken otherwise). */
    private fun PlayerRef.collect() {
        tradingPost(TpRequest.Pending) { box ->
            if (box == null || (box.coins == 0L && box.items.isEmpty())) return@tradingPost message("You have nothing to collect.")
            val inv = inv(Invs.INV)
            val coinsHeld = inv.total(Objs.COINS)
            if (coinsHeld + box.coins > Int.MAX_VALUE) return@tradingPost message("You have too many coins to collect that.")
            val newSlots =
                box.items.keys.count { inv.total(it) == 0L || !objStackable(it) } + (if (coinsHeld == 0L && box.coins > 0) 1 else 0)
            if (newSlots > inv.freeSlots) return@tradingPost message("You don't have enough space in your backpack.")
            tradingPost(TpRequest.Collect) { taken ->
                if (taken == null) return@tradingPost message("The trading post is unavailable right now.")
                transaction("trading post collect") {
                    if (taken.coins > 0) addOrDrop(Invs.INV, Objs.COINS, taken.coins.toInt())
                    for ((obj, count) in taken.items) addOrDrop(Invs.INV, obj, count)
                    true
                }
                message("You collect your trading post earnings.")
                refresh()
            }
        }
    }

    /** TP1 / TP6: choose a tradeable item you carry, then quantity and price; the items leave the backpack first. */
    private fun PlayerRef.sell() {
        if (state().mine.size >= MAX_LISTINGS) return message("You already have $MAX_LISTINGS listings.")
        objDialog("What would you like to sell?") { picked ->
            if (picked < 0) return@objDialog
            val have = inv(Invs.INV).total(picked) + noted(picked).let { if (it >= 0) inv(Invs.INV).total(it) else 0L }
            if (have <= 0) return@objDialog message("You don't have any ${objName(picked)} in your backpack.")
            if (!objTradeable(picked)) return@objDialog message("You can't trade that item.")
            countDialog("How many would you like to sell? (you have $have)") { qty ->
                val n = minOf(qty.toLong(), have).toInt()
                if (n <= 0) return@countDialog
                countDialog("Price for each ${objName(picked)}:") { price -> list(picked, n, price) }
            }
        }
    }

    private fun PlayerRef.list(obj: Int, n: Int, price: Int) {
        if (price <= 0) return message("Enter a price of at least 1 coin.")
        if (n.toLong() * price > Int.MAX_VALUE) return message("That listing would be worth more than a coin stack can hold.")
        val note = noted(obj)
        val inv = inv(Invs.INV)
        val fromUnnoted = minOf(n.toLong(), inv.total(obj)).toInt()
        val fromNoted = n - fromUnnoted
        val taken =
            transaction("trading post list") {
                (fromUnnoted == 0 || remove(Invs.INV, obj, fromUnnoted)) && (fromNoted == 0 || remove(Invs.INV, note, fromNoted))
            }
        if (!taken) return message("You don't have that many.")
        tradingPost(TpRequest.Listing(obj, n, price, MAX_LISTINGS)) { reply ->
            if (reply == null || reply.listings.isEmpty()) {
                transaction("trading post list refund") { addOrDrop(Invs.INV, if (note >= 0) note else obj, n) }
                message(reply?.error ?: "The trading post is unavailable right now.")
            } else {
                message("You list ${commas(n.toLong())} x ${objName(obj)} for ${commas(price.toLong())} gp each.")
            }
            refresh()
        }
    }

    // ---- helpers

    private fun PlayerRef.matchingObjs(query: String): Set<Int> = tradeableObjsContaining(query)

    private fun sorted(rows: List<TpListingView>, v: View): List<TpListingView> {
        val c: Comparator<TpListingView> =
            when (v.sortCol) {
                SORT_ITEM -> compareBy { it.obj }
                SORT_PRICE -> compareBy { it.price }
                SORT_SELLER -> compareBy { it.seller.lowercase() }
                else -> compareBy { -it.listedAtMs }
            }
        return rows.sortedWith(if (v.descending) c.reversed() else c)
    }

    private fun ageSeconds(at: Long, now: Long): Long = ((now - at) / MILLIS_PER_SECOND).coerceAtLeast(0)

    private fun commas(n: Long): String = "%,d".format(n)

    private companion object {
        val STATE = AttrKey<View>("trading_post_view")

        /** TP1: "Your listings n/10". */
        const val MAX_LISTINGS = 10

        /** Rows the table shows (inv `tp_results` has 60 slots). */
        const val MAX_ROWS = 60

        /** Children per row in the clientscripts (row slot * 8 / * 6 + the clickable cover). */
        const val LIST_CHILDREN = 8
        const val MINE_CHILDREN = 6
        const val LIST_SUBS = MAX_ROWS * LIST_CHILDREN
        const val MINE_SUBS = MAX_LISTINGS * MINE_CHILDREN

        const val MODE_LISTINGS = 0
        const val MODE_HISTORY = 1
        const val SORT_ITEM = 0
        const val SORT_PRICE = 1
        const val SORT_SELLER = 2
        const val SORT_AGE = 3
        const val MILLIS_PER_SECOND = 1000L
    }
}
