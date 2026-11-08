package tp.content.raids.tob

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits

/** What one member has left in one midway chest (lobby-rewards LR9, LR12): the stamina potion and the note / Bandages. */
internal class StoreBook(val stamina: Int, val bandages: Int) {
    var staminaLeft: Int = stamina
    var bandagesLeft: Int = bandages
    var noteTaken: Boolean = false
}

/** A member's books for one run, by supply room ("bloat", "sotetseg"). */
internal class StoreBooks(val runId: Long) {
    val byRoom = HashMap<String, StoreBook>()
}

/**
 * The midway stores interface 405 (client-contract 2.5; lobby-rewards LR9-LR13). The client scripts build the stock from
 * enums 1952 / 1953 and read the points from varbit 6460, the stamina count from 12271 and the Buy-X memory from 12272;
 * the server answers the clicks. The note slot shows inv 612's first item, which is sent as a client-only view while the
 * interface is open (the real inv 612 is the reward claim inventory).
 */
internal object TobStoresUi {
    private val BOOKS = AttrKey<StoreBooks>("tob_store_books")
    private val VIEW = AttrKey<StoreBook>("tob_store_view")

    /** The chest [room]'s interface opens for [p]: the points and stamina varbits, the note view, the click events. */
    fun open(p: PlayerRef, run: RaidRun, room: String) {
        val book = bookOf(p, run, room)
        p[VIEW] = book
        sync(p, run)
        p.setVarbit(Varbits.TOB_MIDWAYCHEST_STAMINAPOT, book.staminaLeft)
        showNote(p, run, book)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOB_MIDWAY_STORES)
        p.ifEvents(Components.Tob_Midway_Stores.ITEMS, 0 until STOCK_SLOTS, STOCK_EVENTS)
        p.ifEvents(Components.Tob_Midway_Stores.VAMPYRE_NOTE, NO_SLOT..NO_SLOT, IfEvents.op(OP_TAKE) or IfEvents.op(OP_EXAMINE))
    }

    /** The interface closed, however it did: the client gets the real inv 612 back. */
    fun closed(p: PlayerRef) {
        if (p[VIEW] == null) return
        p[VIEW] = null
        p.transmitInv(Invs.TOB_CHESTS)
    }

    /** Mirrors the member's points into the client varbit (the interface redraws on the varp transmit). */
    fun sync(p: PlayerRef, run: RaidRun) {
        val points =
            TobFlow
                .state(run)
                .supplyPoints.entries
                .firstOrNull { it.key.equals(p.username, ignoreCase = true) }
                ?.value ?: 0
        if (p.varbit(Varbits.TOB_MIDWAYCHEST_POINTS) != points) p.setVarbit(Varbits.TOB_MIDWAYCHEST_POINTS, points)
    }

    /** A click on stock slot [click].slot of 405:8 (hostile input: the slot picks everything, the open view is checked). */
    fun stockOp(p: PlayerRef, click: ButtonClick) {
        val book = p[VIEW] ?: return
        val run = TobFlow.runOf(p) ?: return
        val obj = p.enumInts(ENUM_STOCK)[click.slot] ?: return
        val price = p.enumInts(ENUM_PRICE)[obj]?.takeIf { it > 0 } ?: return
        when (click.op) {
            OP_VALUE -> {
                p.message(valueText(p.objName(obj), price))
            }

            OP_BUY_1 -> {
                buy(p, run, book, obj, price, 1)
            }

            OP_BUY_LAST -> {
                val last = p.varbit(Varbits.TOB_MIDWAYCHEST_LAST_X)
                if (last > 0) buy(p, run, book, obj, price, last)
            }

            OP_BUY_ALL -> {
                buy(p, run, book, obj, price, Int.MAX_VALUE)
            }

            OP_BUY_X -> {
                p.countDialog(AMOUNT_PROMPT) { n ->
                    if (n > 0 && this[VIEW] === book) {
                        setVarbit(Varbits.TOB_MIDWAYCHEST_LAST_X, minOf(n, MAX_LAST_X))
                        buy(this, run, book, obj, price, n)
                    }
                }
            }
        }
    }

    /** "Take" on the note slot: the Message (Normal / Hard, once) or the chest's Bandages (Entry). */
    fun noteOp(p: PlayerRef, click: ButtonClick) {
        if (click.op != OP_TAKE) return
        val book = p[VIEW] ?: return
        val run = TobFlow.runOf(p) ?: return
        val (obj, count) = note(run, book)
        if (obj < 0) return
        val taken = minOf(count, roomFor(p, obj, count))
        if (taken == 0) return p.message(NOT_ENOUGH_SPACE)
        val entry = run.scaling.mode == TobRaid.ENTRY
        p.transaction("tob supplies note") {
            onCommit {
                if (entry) book.bandagesLeft -= taken else book.noteTaken = true
                showNote(p, run, book)
            }
            add(Invs.INV, obj, taken)
        }
    }

    /** Spends points for up to [wanted] of [obj]: capped by the points, the stamina potion's one per chest and the room. */
    private fun buy(p: PlayerRef, run: RaidRun, book: StoreBook, obj: Int, price: Int, wanted: Int) {
        val state = TobFlow.state(run)
        val key = state.supplyPoints.keys.firstOrNull { it.equals(p.username, ignoreCase = true) }
        val points = key?.let { state.supplyPoints[it] } ?: 0
        var count = minOf(wanted, points / price)
        if (obj == Objs._4DOSESTAMINA) {
            if (book.staminaLeft <= 0) return p.message(OUT_OF_STOCK)
            count = minOf(count, book.staminaLeft)
        }
        if (count <= 0) return p.message(NOT_ENOUGH_POINTS)
        count = minOf(count, roomFor(p, obj, count))
        if (count == 0 || key == null) return p.message(NOT_ENOUGH_SPACE)
        p.transaction("tob supplies") {
            onCommit {
                state.supplyPoints[key] = points - price * count
                if (obj == Objs._4DOSESTAMINA) {
                    book.staminaLeft -= count
                    p.setVarbit(Varbits.TOB_MIDWAYCHEST_STAMINAPOT, book.staminaLeft)
                }
                sync(p, run)
            }
            add(Invs.INV, obj, count)
        }
    }

    /** The first item of the chest's note slot: the Bandages (Entry) or the Message, with its count; -1 once taken. */
    private fun note(run: RaidRun, book: StoreBook): Pair<Int, Int> =
        if (run.scaling.mode == TobRaid.ENTRY) {
            if (book.bandagesLeft > 0) Objs.TOB_BANDAGES to book.bandagesLeft else NONE to 0
        } else {
            if (book.noteTaken) NONE to 0 else Objs.TOB_VAMPYRE_MESSAGE to 1
        }

    /** Sends inv 612 as the chest shows it (client-only: the claim inventory is not touched). */
    private fun showNote(p: PlayerRef, run: RaidRun, book: StoreBook) {
        val size = p.inv(Invs.TOB_CHESTS).size
        val objs = IntArray(size) { NONE }
        val counts = IntArray(size)
        val (obj, count) = note(run, book)
        if (obj >= 0) {
            objs[0] = obj
            counts[0] = count
        }
        p.showInv(Invs.TOB_CHESTS, objs, counts)
    }

    private fun bookOf(p: PlayerRef, run: RaidRun, room: String): StoreBook {
        var books = p[BOOKS]
        if (books == null || books.runId != run.id.value) {
            books = StoreBooks(run.id.value)
            p[BOOKS] = books
        }
        val entry = run.scaling.mode == TobRaid.ENTRY
        return books.byRoom.getOrPut(room) { StoreBook(stamina = STAMINA_PER_CHEST, bandages = if (entry) ENTRY_BANDAGES else 0) }
    }

    /** How many of [obj] (up to [wanted]) the backpack takes: free slots for loose items, one slot or a stack otherwise. */
    private fun roomFor(p: PlayerRef, obj: Int, wanted: Int): Int {
        val backpack = p.inv(Invs.INV)
        if (!p.objStackable(obj)) return minOf(wanted, backpack.freeSlots)
        if (backpack.total(obj) == 0L && backpack.freeSlots == 0) return 0
        return wanted
    }

    /**
     * OPEN-LR12: the "Value" line is not captured; the shop wording ("<item>: currently costs <n> coins.") with points.
     */
    private fun valueText(name: String, price: Int): String = "$name: currently costs $price points."

    /** Stock slots 0..7 of 405:8 carry the ops (the text children 8..15 have none). */
    private const val STOCK_SLOTS = 8

    /** Cache enum 1952: stock slot to obj (12625, 2434, ...); enum 1953: obj to point price (client-contract 2.5). */
    private const val ENUM_STOCK = 1952
    private const val ENUM_PRICE = 1953

    /** LR9: the Stamina potion(4) is limited to one per chest (3 Jun 2021). */
    private const val STAMINA_PER_CHEST = 1

    /** LR12: an Entry chest holds 10 Bandages. */
    private const val ENTRY_BANDAGES = 10

    /** Client-contract 1.2: varbit 12272 `TOB_MIDWAYCHEST_LAST_X` is 4 bits (0..15). */
    private const val MAX_LAST_X = 15

    /** Client-contract 2.5: 405:8 has Value 1, Buy-1 2, Buy-<x> 3, Buy-All 4, Buy-X 5, Examine 10; 405:3 has Take 1, Examine 10. */
    private const val OP_VALUE = 1
    private const val OP_BUY_1 = 2
    private const val OP_BUY_LAST = 3
    private const val OP_BUY_ALL = 4
    private const val OP_BUY_X = 5
    private const val OP_EXAMINE = 10
    private const val OP_TAKE = 1

    /** A component with no child slots is addressed as -1..-1. */
    private const val NO_SLOT = -1
    private const val NONE = -1

    @Suppress("MagicNumber") // op numbers
    private val STOCK_EVENTS: Long =
        IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(10)

    /** OPEN-LR12: the chatbox prompt of Buy-X and the refusals are not captured. */
    private const val AMOUNT_PROMPT = "Enter amount:"
    private const val NOT_ENOUGH_POINTS = "You don't have enough points."
    private const val OUT_OF_STOCK = "That item is out of stock."
    private const val NOT_ENOUGH_SPACE = "You don't have enough inventory space."
}

/** The supply chests' locs, the stores interface buttons, and the points / chest varbits that live only inside the raid. */
public class TobStoresPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }
        onOpLoc(Locs.TOB_MIDWAY_CHEST_CLOSED, OP1) { loc -> TobSupplies.use(player, loc, opening = true) }
        onOpLoc(Locs.TOB_MIDWAY_CHEST_OPEN, OP2) { loc -> TobSupplies.use(player, loc, opening = false) }
        onButton(Components.Tob_Midway_Stores.ITEMS) { click -> TobStoresUi.stockOp(this, click) }
        onButton(Components.Tob_Midway_Stores.VAMPYRE_NOTE) { click -> TobStoresUi.noteOp(this, click) }
        onModalClosed(Interfaces.TOB_MIDWAY_STORES) { TobStoresUi.closed(this) }

        // Points exist only inside the raid: the persistent varbits are cleared when a member leaves or logs in elsewhere.
        events.on<RaidEvent.Left> { e -> if (e.run.raid.id == TobRaid.ID) world?.onlinePlayer(e.member)?.let(::clearRaidVarbits) }
        onLogin { if (TobFlow.runOf(this) == null) clearRaidVarbits(this) }
    }

    private fun clearRaidVarbits(p: PlayerRef) {
        p.setVarbit(Varbits.TOB_MIDWAYCHEST_POINTS, 0)
        p.setVarbit(Varbits.TOB_MIDWAYCHEST_STAMINAPOT, 0)
    }

    private companion object {
        const val OP1 = 1

        /** Client-contract 2.7: the opened midway chest 32759 has its "Search" on op2. */
        const val OP2 = 2
    }
}
