package tp.content.raids.tfa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DbRowRef
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.symbols.Components
import tp.symbols.DbRows
import tp.symbols.DbTables
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The Quartermaster's "Alliance Supplies" shop (docs/mechanics/raids/tfa.md TFA7, TFA16; tfa/client-contract.md 1.6).
 * It is the cache's omnishop (interfaces 819 + 806, table 39 rows 17150 / 17151) paid in supply credits, which live in
 * the varbit `TFA_SUPPLY_POINTS`: the client scripts 7257 / 7258 read and write that varbit for the currency row 17149.
 * The shops plugin cannot be reused: it is a coin shop on `shopmain` (300). Prices, order and buy quantities come from
 * the cache rows; the server owns credits and stock per run ([TfaState]) and sends the stock counts in inv 1030.
 */
public class TfaSupplies : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        var data: SupplyData? = null
        onWorldStart {
            world = this
            data = SupplyData.read(this)
        }
        onOpNpc(Npcs.TFA_MIDRAID_SUPPLIES_NPC, OP1) {
            val shop = data
            if (shop == null) player.message(UNAVAILABLE) else TfaSupplyShop.open(player, shop)
        }
        onOpNpc(Npcs.TFA_MIDRAID_SUPPLIES_NPC, OP3) { npc -> chatNpc(npc, TALK) }
        onOpNpc(Npcs.TFA_MIDRAID_SUPPLIES_NPC_INACTIVE, OP1) { npc -> chatNpc(npc, TALK) }

        // The omnishop's own clientscripts report a click on a stock row (IF_SCRIPTTRIGGER, hostile input).
        onScriptTrigger(Components.Omnishop_Main.TRIGGER_BUY, "iii") { t ->
            TfaSupplyShop.buy(this, t.args[0] as Int, t.args[1] as Int, t.args[2] as Int)
        }
        onScriptTrigger(Components.Omnishop_Main.TRIGGER_REQUEST_INFO, "ii") { t ->
            TfaSupplyShop.info(this, t.args[0] as Int, t.args[1] as Int)
        }
        onModalClosed(Interfaces.OMNISHOP_MAIN) { TfaSupplyShop.closed(this) }

        events.on<RaidEvent.RoomCleared> { e ->
            if (e.run.raid.id == TfaRaid.ID && e.run.roomsTaken.size == TfaSupplyShop.ROOMS_BEFORE_SHOP) TfaSupplyShop.grant(e.run)
        }
        // Credits exist only inside the raid: the varbit is cleared when a member enters or leaves.
        events.on<RaidEvent.Entered> { e -> resetCredits(world, e.run, e.member) }
        events.on<RaidEvent.Left> { e -> resetCredits(world, e.run, e.member) }
    }

    private fun resetCredits(world: WorldRef?, run: RaidRun, member: String) {
        if (run.raid.id != TfaRaid.ID) return
        TfaFlow.state(run).supplyCredits.remove(member.lowercase())
        world?.onlinePlayer(member)?.setVarbit(Varbits.TFA_SUPPLY_POINTS, 0)
    }
}

private const val OP1 = 1
private const val OP3 = 3

// OPEN-NX8: the Quartermaster's dialogue is not captured. The line is the shop's own intro text (cache row 17150 col 11).
private const val TALK = "Help yourself to what little supplies we can offer to aid you in the battles to come."
private const val UNAVAILABLE = "The Quartermaster has nothing to offer."

/** One stock row of an omnishop (table 40), as read from the cache. */
internal class SupplyLine(val row: Int, val obj: Int, val price: Int, val buyable: Boolean)

/** One omnishop row (table 39): its stock in display order and the buy options it offers (option to quantity). */
internal class SupplyShop(val row: Int, val lines: List<SupplyLine>, val options: Map<Int, Int>) {
    /** The lines that have a stock count; inv 1030 holds exactly these, in this order (size 10 = the failure shop). */
    val stocked: List<SupplyLine> = lines.filter { it.buyable }
}

/** Both Alliance Supplies variants, read once per world from the cache. */
internal class SupplyData(val normal: SupplyShop, val failure: SupplyShop) {
    fun variant(poor: Boolean): SupplyShop = if (poor) failure else normal

    companion object {
        fun read(world: WorldRef): SupplyData? {
            val shops = world.dbRows(DbTables.Omnishop_Shop_Data.ID).associateBy { it.id }
            val stock = world.dbRows(DbTables.Omnishop_Stock_Data.ID).associateBy { it.id }
            val quantities = world.enumInts(ENUM_BUY_QUANTITIES)

            fun shop(row: Int): SupplyShop? {
                val r = shops[row] ?: return null
                val ids = ints(r, DbTables.Omnishop_Shop_Data.COL_OMNISHOP_SHOP_STOCK)
                val lines = ids.mapNotNull { id -> stock[id]?.let { line(id, it) } }
                val options =
                    BUY_COLUMNS
                        .mapNotNull { col ->
                            val option = r.int(col) ?: return@mapNotNull null
                            quantities[option]?.takeIf { it > 0 }?.let { option to it }
                        }.toMap()
                return SupplyShop(row, lines, options)
            }
            val normal = shop(DbRows.TFA_MIDRAID_SUPPLIES_SHOP) ?: return null
            val failure = shop(DbRows.TFA_MIDRAID_SUPPLIES_SHOP_FAILURE) ?: return null
            return SupplyData(normal, failure)
        }

        private fun line(id: Int, r: DbRowRef): SupplyLine? {
            val obj = r.int(DbTables.Omnishop_Stock_Data.COL_OMNISHOP_STOCK_OBJ) ?: return null
            val currency = r.int(DbTables.Omnishop_Stock_Data.COL_OMNISHOP_STOCK_COST, 0, 0)
            val price = r.int(DbTables.Omnishop_Stock_Data.COL_OMNISHOP_STOCK_COST, 0, 1) ?: return null
            // Column 18 (buyable) defaults to true, column 21 (view only) to false; the note is the one view-only row.
            val buyable =
                r.int(DbTables.Omnishop_Stock_Data.COL_OMNISHOP_STOCK_BUYABLE) != 0 &&
                    r.int(DbTables.Omnishop_Stock_Data.COL_OMNISHOP_STOCK_VIEW_ONLY) != 1 &&
                    currency == DbRows.OMNISHOP_CURRENCY_TFA_SUPPLY_POINTS
            return SupplyLine(id, obj, price, buyable)
        }

        private fun ints(r: DbRowRef, column: Int): List<Int> {
            val out = ArrayList<Int>()
            while (true) out += r.int(column, out.size) ?: break
            return out
        }

        /** Cache enum 5844 `omnishop_buy_quantities`: a shop's buy option (table 39 cols 20-23) to the quantity it buys. */
        private const val ENUM_BUY_QUANTITIES = 5844

        private val BUY_COLUMNS =
            listOf(
                DbTables.Omnishop_Shop_Data.COL_OMNISHOP_BUY_Q1,
                DbTables.Omnishop_Shop_Data.COL_OMNISHOP_BUY_Q2,
                DbTables.Omnishop_Shop_Data.COL_OMNISHOP_BUY_Q3,
                DbTables.Omnishop_Shop_Data.COL_OMNISHOP_BUY_Q4,
            )
    }
}

/** A player's open Alliance Supplies: the shop variant the client was sent. */
internal class SupplyView(val shop: SupplyShop)

/** The shop's rules and interface (omnishop protocol as OpenRune's `content/interfaces/omnishop`, ISC; ids from the cache). */
internal object TfaSupplyShop {
    private val VIEWING = AttrKey<SupplyView>("tfa_supplies_view")

    /** The Quartermaster arrives after the third boss (TFA7): the Guardian and two side rooms are cleared. */
    const val ROOMS_BEFORE_SHOP = 3

    fun open(p: PlayerRef, data: SupplyData) {
        val run = runOf(p) ?: return
        val state = TfaFlow.state(run)
        val name = p.username.lowercase()
        val shop = data.variant(name in state.poorPerformers)
        p.setVarp(Varps.OMNISHOP_SELECTED_SIDE_ID, -1)
        p.setVarp(Varps.OMNISHOP_SELECTED_ID, -1)
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.OMNISHOP_MAIN)
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.OMNISHOP_SIDE)
        p.setVarp(Varps.OMNISHOP_LASTSHOP, shop.row)
        p.setVarbit(Varbits.TFA_SUPPLY_POINTS, state.supplyCredits[name] ?: 0)
        p[VIEWING] = SupplyView(shop)
        push(p, state, shop)
        p.clientScript(CS_MAIN_INIT, shop.row, -1, -1)
        p.clientScript(CS_SIDE_INIT, shop.row)
        p.ifEvents(Components.Omnishop_Main.LIST, 0..LIST_SLOTS, LIST_EVENTS)
        p.ifEvents(Components.Omnishop_Side.ITEMS, 0..LAST_BACKPACK_SLOT, SIDE_EVENTS)
        p.ifEvents(Components.Omnishop_Side.INFO_LAYER, 0..0, IfEvents.op(1))
        p.ifEvents(Components.Omnishop_Main.TRIGGER_BUY, -1..-1, IfEvents.SCRIPT_TRIGGER)
        p.ifEvents(Components.Omnishop_Main.TRIGGER_REQUEST_INFO, -1..-1, IfEvents.SCRIPT_TRIGGER)
        p.ifEvents(Components.Omnishop_Main.DROPDOWN_CONTENT, 0..DROPDOWN_SLOTS, IfEvents.op(1))
    }

    /** The shop closed, however it did: the backpack takes its side slot back. */
    fun closed(p: PlayerRef) {
        if (p[VIEWING] == null) return
        p[VIEWING] = null
        p.setVarp(Varps.OMNISHOP_LASTSHOP, -1)
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.INVENTORY)
        p.ifEvents(Components.Inventory.ITEMS, 0..LAST_BACKPACK_SLOT, BACKPACK_EVENTS)
    }

    /** A click on stock row [index] of the shop [shopRow]: the item's description panel. */
    fun info(p: PlayerRef, shopRow: Int, index: Int) {
        val view = p[VIEWING] ?: return
        if (shopRow != view.shop.row) return
        val line = view.shop.lines.getOrNull(index) ?: return
        p.setVarp(Varps.OMNISHOP_SELECTED_ID, index)
        val text = p.objParamString(line.obj, PARAM_ITEM_INFO) ?: p.objName(line.obj)
        p.clientScript(CS_INFO_UPDATE, shopRow, -1, index, text, 1, 0, 1)
    }

    /** A buy button: stock row [index] of [shopRow], buy option [option] (client-supplied, so every part is checked). */
    fun buy(p: PlayerRef, shopRow: Int, index: Int, option: Int) {
        val view = p[VIEWING] ?: return
        if (shopRow != view.shop.row) return
        val line =
            view.shop.lines
                .getOrNull(index)
                ?.takeIf { it.buyable } ?: return
        val wanted = view.shop.options[option] ?: return
        val run = runOf(p) ?: return
        val state = TfaFlow.state(run)
        val name = p.username.lowercase()
        val left = remaining(state, name, line.row)
        if (left <= 0) return p.message(OUT_OF_STOCK)
        var count = minOf(wanted, left)
        if (line.price > 0) {
            count = minOf(count, (state.supplyCredits[name] ?: 0) / line.price)
            if (count == 0) return p.message(NOT_ENOUGH_CREDITS)
        }
        count = minOf(count, roomFor(p, line.obj, count))
        if (count == 0) return p.message(NOT_ENOUGH_SPACE)
        val ok =
            p.transaction("tfa supplies") {
                onCommit { spend(p, state, view.shop, name, line, count) }
                add(Invs.INV, line.obj, count)
            }
        if (!ok) p.message(NOT_ENOUGH_SPACE)
    }

    /** Runs when the purchase committed: credits and stock go down, the client redraws (OpenRune toggles `omnishop_redraw`). */
    private fun spend(p: PlayerRef, state: TfaState, shop: SupplyShop, name: String, line: SupplyLine, count: Int) {
        val credits = (state.supplyCredits[name] ?: 0) - line.price * count
        state.supplyCredits[name] = credits
        val stock = stockOf(state, name, line.row)
        stock[line.row] = (stock[line.row] ?: 0) - count
        p.setVarbit(Varbits.TFA_SUPPLY_POINTS, credits)
        push(p, state, shop)
        p.setVarp(Varps.OMNISHOP_REDRAW, p.varp(Varps.OMNISHOP_REDRAW) xor 1)
    }

    /** Sends the stock counts: inv 1030 holds the buyable lines in order, padded to the inv's size. */
    private fun push(p: PlayerRef, state: TfaState, shop: SupplyShop) {
        val name = p.username.lowercase()
        val objs = IntArray(INV_SIZE) { -1 }
        val counts = IntArray(INV_SIZE)
        for ((slot, line) in shop.stocked.withIndex().take(INV_SIZE)) {
            objs[slot] = line.obj
            counts[slot] = remaining(state, name, line.row)
        }
        p.showInv(Invs.TFA_MIDRAID_SUPPLIES_SHOP, objs, counts)
    }

    /**
     * OPEN-TFA7: the credits a member earns when the Quartermaster arrives: [TfaConfig.supplyCreditsBase] for a deathless
     * equal share of the first three rooms, scaled by their points so far (`RaidMember.points`, deaths already deducted)
     * and capped at one and a half shares. A share under [TfaConfig.supplyPoorShare] gets the failure shop (TFA16).
     */
    fun grant(run: RaidRun) {
        val state = TfaFlow.state(run)
        val expected = ROOMS_BEFORE_SHOP * TfaRewards.ROOM_CONTRIBUTION.toDouble() / maxOf(1, run.scaling.partySize)
        for ((name, member) in run.members) {
            if (!member.inside) continue
            val share = (member.points / expected).coerceIn(0.0, MAX_SHARE)
            val key = name.lowercase()
            state.supplyCredits[key] = Math.round(share * TfaConfig.supplyCreditsBase).toInt().coerceAtMost(TfaConfig.MAX_SUPPLY_CREDITS)
            if (share < TfaConfig.supplyPoorShare) state.poorPerformers += key
        }
    }

    private fun remaining(state: TfaState, name: String, row: Int): Int =
        stockOf(state, name, row).getOrPut(row) { INITIAL_STOCK[row] ?: 0 }

    private fun stockOf(state: TfaState, name: String, row: Int): MutableMap<Int, Int> {
        val shared = !TfaConfig.supplyStockPerPlayer && row != DbRows.TFA_MIDRAID_SUPPLIES_LEFTOVERS
        return state.supplyStock.getOrPut(if (shared) "" else name) { HashMap() }
    }

    private fun runOf(p: PlayerRef): RaidRun? {
        val run = Raids.of(p).runOf(p.username) ?: return null
        return run.takeIf { it.raid.id == TfaRaid.ID && it.member(p.username)?.inside == true }
    }

    /** How many of [obj] (up to [wanted]) the backpack takes: free slots for loose items, one slot or a stack otherwise. */
    private fun roomFor(p: PlayerRef, obj: Int, wanted: Int): Int {
        val backpack = p.inv(Invs.INV)
        if (!p.objStackable(obj)) return minOf(wanted, backpack.freeSlots)
        if (backpack.total(obj) == 0L && backpack.freeSlots == 0) return 0
        return wanted
    }

    /** TFA16: stock per item (the stock rows are named `TFA_MIDRAID_SUPPLIES_SHOP_*`). */
    private val INITIAL_STOCK =
        mapOf(
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_4DOSEPOTIONOFSARADOMIN to 6,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_4DOSE2RESTORE to 4,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_4DOSEPRAYERRESTORE to 4,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_MANTARAY to 10,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_HALIBUT to 5,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_KRILL to 10,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_2DOSEPRAYERRESTORE to 1,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_2DOSERANGERSPOTION to 1,
            DbRows.TFA_MIDRAID_SUPPLIES_SHOP_2DOSE2COMBAT to 1,
            // OPEN-TFA7: the Leftovers' count is not given anywhere; one per player.
            DbRows.TFA_MIDRAID_SUPPLIES_LEFTOVERS to 1,
        )

    /** OPEN-TFA7: the most a player's performance can scale the credits (one and a half equal shares). */
    private const val MAX_SHARE = 1.5

    /** Client-contract 1.4: inv 1030 `TFA_MIDRAID_SUPPLIES_SHOP` has 10 slots. */
    private const val INV_SIZE = 10

    /** Omnishop clientscripts (OpenRune `omnishop_main_init`, `omnishop_side_init`, `omnishop_info_update`; ids in the cache). */
    private const val CS_MAIN_INIT = 7140
    private const val CS_SIDE_INIT = 7246
    private const val CS_INFO_UPDATE = 9772

    /** Client script `toplevel_mainmodal_open` (gameframe `SideInventory`): sets the main modal background; -1 = default. */
    private const val CS_MAINMODAL_BACKGROUND = 2524

    /** Cache param 1784 `omnishop_item_info`: an item's description in the omnishop. */
    private const val PARAM_ITEM_INFO = 1784

    /** Slot counts of the omnishop components (OpenRune `Omnishop.kt`). */
    private const val LIST_SLOTS = 288
    private const val DROPDOWN_SLOTS = 8
    private const val LAST_BACKPACK_SLOT = 27

    @Suppress("MagicNumber") // op numbers
    private val LIST_EVENTS: Long =
        IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(6) or IfEvents.op(10)

    /** The side items only show the backpack; this shop buys nothing (table 40 col 19 sellable = 0). */
    @Suppress("MagicNumber") // op numbers
    private val SIDE_EVENTS: Long =
        IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(6) or IfEvents.op(10) or
            IfEvents.DEPTH1 or IfEvents.DRAG_TARGET

    /** The backpack's item targets and ops (gameframe `SideInventory.ITEM_EVENTS`). */
    @Suppress("MagicNumber") // op numbers
    private val BACKPACK_EVENTS: Long =
        IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(6) or IfEvents.op(7) or IfEvents.op(10) or
            IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
            IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET

    private const val OUT_OF_STOCK = "That item is currently out of stock."
    private const val NOT_ENOUGH_CREDITS = "You don't have enough Supply Credits."
    private const val NOT_ENOUGH_SPACE = "You don't have enough inventory space."
}
