package tp.content.raids.tob

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Varbits

/**
 * The loot interface 23 over the persisted claim inventory `TOB_CHESTS` 612 (docs/mechanics/raids/tob/lobby-rewards.md
 * LR14 / LR15, client-contract.md 1.4, 1.5 and 2.7). Like the Alliance chest, the client has no script for the item
 * layer, so the server binds the inv to `23:12` with the generic grid script 149 and handles every op: "Bank-all",
 * "Take-all", "Discard-all" and, per item, Take / Bank / Discard. Every item movement is one item transaction out of the
 * claim inventory, so a reward can be claimed once.
 */
internal object TobChestUi {
    /** Client script `interface_inv_init`: binds an inv to a grid component (same call as content/skills SeedBox). */
    private const val CS_INV_INIT = 149

    /**
     * OPEN-LR19: the grid's geometry in `23:12` is not in the cache dump; 6 across by 2 rows mirrors the Alliance chest
     * (script 153 divides by rows - 1, so it needs two or more).
     */
    private const val GRID_COLUMNS = 6
    private const val GRID_ROWS = 2

    /** Script 149's drag type argument: 0 = not draggable. */
    private const val DRAG_NONE = 0

    /** Item op numbers: the labels passed to script 149 are ops 1..3 in this order. */
    private const val OP_TAKE = 1
    private const val OP_BANK = 2
    private const val OP_DISCARD = 3

    /** Obj param 59: the item cannot be banked (content/gameframe Bank `PARAM_NO_BANK`). */
    private const val PARAM_NO_BANK = 59

    /** The bank's own refusal text (docs/mechanics/bank.md). */
    private const val NO_BANK_MESSAGE = "A magical force prevents you from banking this item!"

    private const val NO_INV_SPACE = "You don't have enough inventory space."
    private const val NO_BANK_SPACE = "You don't have enough space in your bank account."

    /** OPEN-LR20: the line for an empty lobby chest is not captured. */
    const val NOTHING_TO_CLAIM = "You have no rewards to claim."

    /** Loot Tracker contract (client-contract 2.7): inv 612 is sent before interface 23 opens. */
    fun open(player: PlayerRef) {
        player.transmitInv(Invs.TOB_CHESTS)
        player.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOB_CHESTS)
        player.clientScript(
            CS_INV_INIT,
            Components.Tob_Chests.ITEMS,
            Invs.TOB_CHESTS,
            GRID_COLUMNS,
            GRID_ROWS,
            DRAG_NONE,
            -1,
            "Take",
            "Bank",
            "Discard",
            "",
            "",
        )
        player.ifEvents(
            Components.Tob_Chests.ITEMS,
            0 until player.inv(Invs.TOB_CHESTS).size,
            IfEvents.op(OP_TAKE) or IfEvents.op(OP_BANK) or IfEvents.op(OP_DISCARD),
        )
        sync(player)
    }

    /** The lobby rewards chest "Claim" (LR15): the interface when loot waits, a line otherwise. */
    fun claim(player: PlayerRef) {
        if (isEmpty(player)) player.message(NOTHING_TO_CLAIM) else open(player)
    }

    /** `TOB_SHOULD_HAVE_LOOT` is 1 exactly while the claim inventory holds something (it morphs the lobby chest, 2.7). */
    fun sync(player: PlayerRef) {
        val want = if (isEmpty(player)) 0 else 1
        if (player.varbit(Varbits.TOB_SHOULD_HAVE_LOOT) != want) player.setVarbit(Varbits.TOB_SHOULD_HAVE_LOOT, want)
    }

    private fun isEmpty(player: PlayerRef): Boolean {
        val inv = player.inv(Invs.TOB_CHESTS)
        return inv.freeSlots == inv.size
    }

    /** One item slot's op; the client's slot and obj are checked against the inventory first. */
    fun itemOp(player: PlayerRef, click: ButtonClick) {
        val inv = player.inv(Invs.TOB_CHESTS)
        if (click.slot !in 0 until inv.size || click.obj < 0 || inv.obj(click.slot) != click.obj) return
        when (click.op) {
            OP_TAKE -> {
                take(player, click.slot, click.obj)
            }

            OP_BANK -> {
                bank(player, click.slot, click.obj)
            }

            OP_DISCARD -> {
                val slot = click.slot
                val obj = click.obj
                val count = inv.count(slot)
                val question = "Discard $count x ${player.objName(obj)}?"
                player.choiceDialog(question, listOf("Yes, discard it.", "No.")) { pick -> if (pick == 1) discard(this, slot, obj, count) }
            }
        }
        sync(player)
    }

    /** "Take-all": every stack into the backpack as far as it fits. */
    fun takeAll(player: PlayerRef) {
        for (slot in 0 until player.inv(Invs.TOB_CHESTS).size) {
            val obj = player.inv(Invs.TOB_CHESTS).obj(slot)
            if (obj >= 0 && !take(player, slot, obj)) break
        }
        sync(player)
    }

    /** "Bank-all": every stack into the bank (notes as the item they stand for), without needing a bank open. */
    fun bankAll(player: PlayerRef) {
        for (slot in 0 until player.inv(Invs.TOB_CHESTS).size) {
            val obj = player.inv(Invs.TOB_CHESTS).obj(slot)
            if (obj >= 0 && !bank(player, slot, obj)) break
        }
        sync(player)
    }

    /** "Discard-all": asked first; destroys the whole claim inventory. */
    fun discardAll(player: PlayerRef) {
        if (isEmpty(player)) return
        player.choiceDialog("Discard all of your loot?", listOf("Yes, discard it all.", "No.")) { pick ->
            if (pick == 1) {
                clear(this)
                sync(this)
            }
        }
    }

    /** TB31 / OPEN-TB15 (flag `tp.tob.unclaimedLootExpiresOnLogout`): unclaimed loot is lost when its owner logs out. */
    fun expire(player: PlayerRef) {
        if (TobConfig.unclaimedLootExpiresOnLogout) clear(player)
    }

    private fun clear(player: PlayerRef) {
        val inv = player.inv(Invs.TOB_CHESTS)
        val slots = (0 until inv.size).filter { inv.obj(it) >= 0 }
        if (slots.isEmpty()) return
        player.transaction("tob chest discard all") { slots.all { removeAt(Invs.TOB_CHESTS, it, inv.obj(it), inv.count(it)) } }
    }

    private fun take(player: PlayerRef, slot: Int, obj: Int): Boolean {
        val count = player.inv(Invs.TOB_CHESTS).count(slot)
        val ok = player.transaction("tob chest take") { move(Invs.TOB_CHESTS, slot, obj, count, Invs.INV) }
        if (!ok) player.message(NO_INV_SPACE)
        return ok
    }

    /**
     * Banks the stack straight into the bank inv (the same `add` the bank uses). It lands at the end of the main tab,
     * and a placeholder of the item is left in place (OPEN-LR21: the bank's tab and placeholder bookkeeping is private
     * to content/gameframe).
     */
    private fun bank(player: PlayerRef, slot: Int, obj: Int): Boolean {
        if (player.objParam(obj, PARAM_NO_BANK, 0) != 0) {
            player.message(NO_BANK_MESSAGE)
            return false
        }
        val count = player.inv(Invs.TOB_CHESTS).count(slot)
        val real = player.unnoted(obj)
        val ok = player.transaction("tob chest bank") { removeAt(Invs.TOB_CHESTS, slot, obj, count) && add(Invs.BANK, real, count) }
        if (!ok) player.message(NO_BANK_SPACE)
        return ok
    }

    private fun discard(player: PlayerRef, slot: Int, obj: Int, count: Int) {
        val inv = player.inv(Invs.TOB_CHESTS)
        if (slot !in 0 until inv.size || inv.obj(slot) != obj || inv.count(slot) != count) return
        player.transaction("tob chest discard") { removeAt(Invs.TOB_CHESTS, slot, obj, count) }
        sync(player)
    }
}

/** Interface 23's buttons, the lobby rewards chest ("Claim" while loot waits) and the claim flag on login. */
public class TobChestPlugin : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Tob_Chests.BANK) { TobChestUi.bankAll(this) }
        onButton(Components.Tob_Chests.INVENTORY) { TobChestUi.takeAll(this) }
        onButton(Components.Tob_Chests.DISCARD) { TobChestUi.discardAll(this) }
        onButton(Components.Tob_Chests.ITEMS) { click -> TobChestUi.itemOp(this, click) }
        // The lobby chest multi 41437 -> 41436 (closed) / 41435 (open), both "Claim" (client-contract 2.7); the parent
        // is the id the map holds, the children are what a client that resolves the multi sends.
        for (id in LOBBY_CHEST) onOpLoc(id, OP1) { TobChestUi.claim(player) }
        onLogin {
            TobChestUi.sync(this)
            // A reward paid by the raid plugin's own login hook (a run still alive) lands after this one.
            weakQueue(0) { TobChestUi.sync(player) }
        }
        onLogout { TobChestUi.expire(this) }
    }

    private companion object {
        const val OP1 = 1
        val LOBBY_CHEST =
            intArrayOf(Locs.TOB_REWARDS_CHEST_LOBBY_MULTI, Locs.TOB_REWARDS_CHEST_LOBBY_OPEN, Locs.TOB_REWARDS_CHEST_LOBBY_CLOSED)
    }
}
