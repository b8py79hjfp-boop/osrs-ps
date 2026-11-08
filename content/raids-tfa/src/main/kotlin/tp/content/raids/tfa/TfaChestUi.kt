package tp.content.raids.tfa

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
 * The loot interface 973 over the persisted claim inventory `TFA_CHESTS` (docs/mechanics/raids/tfa/lobby-nexus-vault.md
 * NX-F6, client-contract.md 1.5 and 2.6). The client has no script for the item layer, so the server binds the inv to
 * `973:11` with the generic grid script 149 and handles every op: "Bank-all", "Take-all", "Discard-all" and, per item,
 * Take / Bank / Discard. Every item movement is one item transaction.
 */
internal object TfaChestUi {
    /** Client script `interface_inv_init`: binds an inv to a grid component (same call as content/skills SeedBox). */
    private const val CS_INV_INIT = 149

    /**
     * OPEN-TFA20: the grid's geometry in `973:11` is not in the cache dump; 6 across by 2 rows mirrors the seed box
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

    fun open(player: PlayerRef) {
        player.transmitInv(Invs.TFA_CHESTS)
        player.openModal(Components.Toplevel.MAINMODAL, Interfaces.TFA_CHESTS)
        player.clientScript(
            CS_INV_INIT,
            Components.Tfa_Chests.ITEMS,
            Invs.TFA_CHESTS,
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
            Components.Tfa_Chests.ITEMS,
            0 until player.inv(Invs.TFA_CHESTS).size,
            IfEvents.op(OP_TAKE) or IfEvents.op(OP_BANK) or IfEvents.op(OP_DISCARD),
        )
        sync(player)
    }

    /** `TFA_HAS_CLAIMABLE_LOOT` is 1 exactly while the claim inventory holds something (it morphs the lobby chest, NX-F6). */
    fun sync(player: PlayerRef) {
        val inv = player.inv(Invs.TFA_CHESTS)
        val want = if (inv.freeSlots == inv.size) 0 else 1
        if (player.varbit(Varbits.TFA_HAS_CLAIMABLE_LOOT) != want) player.setVarbit(Varbits.TFA_HAS_CLAIMABLE_LOOT, want)
    }

    /** One item slot's op; the client's slot and obj are checked against the inventory first. */
    fun itemOp(player: PlayerRef, click: ButtonClick) {
        val inv = player.inv(Invs.TFA_CHESTS)
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
        for (slot in 0 until player.inv(Invs.TFA_CHESTS).size) {
            val obj = player.inv(Invs.TFA_CHESTS).obj(slot)
            if (obj >= 0 && !take(player, slot, obj)) break
        }
        sync(player)
    }

    /** "Bank-all": every stack into the bank (notes as the item they stand for), without needing a bank open. */
    fun bankAll(player: PlayerRef) {
        for (slot in 0 until player.inv(Invs.TFA_CHESTS).size) {
            val obj = player.inv(Invs.TFA_CHESTS).obj(slot)
            if (obj >= 0 && !bank(player, slot, obj)) break
        }
        sync(player)
    }

    /** "Discard-all": asked first; destroys the whole claim inventory. */
    fun discardAll(player: PlayerRef) {
        if (player.inv(Invs.TFA_CHESTS).freeSlots == player.inv(Invs.TFA_CHESTS).size) return
        player.choiceDialog("Discard all of your loot?", listOf("Yes, discard it all.", "No.")) { pick ->
            if (pick == 1) {
                val inv = inv(Invs.TFA_CHESTS)
                val slots = (0 until inv.size).filter { inv.obj(it) >= 0 }
                transaction("tfa chest discard all") { slots.all { removeAt(Invs.TFA_CHESTS, it, inv.obj(it), inv.count(it)) } }
                sync(this)
            }
        }
    }

    private fun take(player: PlayerRef, slot: Int, obj: Int): Boolean {
        val count = player.inv(Invs.TFA_CHESTS).count(slot)
        val ok = player.transaction("tfa chest take") { move(Invs.TFA_CHESTS, slot, obj, count, Invs.INV) }
        if (!ok) player.message(NO_INV_SPACE)
        return ok
    }

    /**
     * Banks the stack straight into the bank inv (the same `add` the bank uses). It lands at the end of the main tab,
     * and a placeholder of the item is left in place (OPEN-TFA21: the bank's tab and placeholder bookkeeping is private
     * to content/gameframe).
     */
    private fun bank(player: PlayerRef, slot: Int, obj: Int): Boolean {
        if (player.objParam(obj, PARAM_NO_BANK, 0) != 0) {
            player.message(NO_BANK_MESSAGE)
            return false
        }
        val count = player.inv(Invs.TFA_CHESTS).count(slot)
        val real = player.unnoted(obj)
        val ok = player.transaction("tfa chest bank") { removeAt(Invs.TFA_CHESTS, slot, obj, count) && add(Invs.BANK, real, count) }
        if (!ok) player.message(NO_BANK_SPACE)
        return ok
    }

    private fun discard(player: PlayerRef, slot: Int, obj: Int, count: Int) {
        val inv = player.inv(Invs.TFA_CHESTS)
        if (slot !in 0 until inv.size || inv.obj(slot) != obj || inv.count(slot) != count) return
        player.transaction("tfa chest discard") { removeAt(Invs.TFA_CHESTS, slot, obj, count) }
        sync(player)
    }
}

/** Interface 973's buttons, the lobby rewards chest ("Search" while loot waits) and the claim flag on login. */
public class TfaChestPlugin : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Tfa_Chests.BANK) { TfaChestUi.bankAll(this) }
        onButton(Components.Tfa_Chests.INVENTORY) { TfaChestUi.takeAll(this) }
        onButton(Components.Tfa_Chests.DISCARD) { TfaChestUi.discardAll(this) }
        onButton(Components.Tfa_Chests.ITEMS) { click -> TfaChestUi.itemOp(this, click) }
        // The lobby chest multi 63299 -> 63300 (closed, no ops) / 63301 (open, "Search"); the engine aliases the parent id.
        onOpLoc(Locs.TFA_REWARDS_CHEST_LOBBY_OPEN, 1) { TfaChestUi.open(player) }
        onLogin {
            TfaChestUi.sync(this)
            // A reward paid by the raid plugin's own login hook (a run still alive) lands after this one.
            weakQueue(0) { TfaChestUi.sync(player) }
        }
    }
}
