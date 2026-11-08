package tp.content.raids.toa

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.IfEvents
import tp.plugin.InvView
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The reward interface 771 over the persisted claim inventory `TOA_CHESTS` 811 (toa/client-contract.md 2.9;
 * lobby-rewards.md TL18, TR27). Like the Alliance chest and ToB's interface 23 the client has no script for the item
 * layer (OPEN-TC4), so the server binds the inventory to `771:10` with the generic grid script 149 and answers every
 * op. Every movement is one item transaction out of the claim inventory, so a reward can be claimed once.
 */
internal object ToaChestUi {
    /** Client script `interface_inv_init`: binds an inventory to a grid component (as ToB's interface 23). */
    private const val CS_INV_INIT = 149

    /** OPEN-TC4: the 92 x 119 pixel layer holds the nine slots of inventory 811 as a 3 x 3 grid. */
    private const val GRID_COLUMNS = 3
    private const val GRID_ROWS = 3
    private const val DRAG_NONE = 0

    private const val OP_TAKE = 1
    private const val OP_BANK = 2
    private const val OP_DISCARD = 3

    /** Obj param 59: the item cannot be banked (content/gameframe Bank `PARAM_NO_BANK`). */
    private const val PARAM_NO_BANK = 59

    private const val NO_BANK_MESSAGE = "A magical force prevents you from banking this item!"
    private const val NO_INV_SPACE = "You don't have enough inventory space."
    private const val NO_BANK_SPACE = "You don't have enough space in your bank account."

    /** OPEN-TR16: the line for a niche with nothing to claim is not captured. */
    const val NOTHING_TO_CLAIM = "You have no rewards to claim."

    /** Loot tracker contract (runelite-timings RT176): inventory 811 is sent before interface 771 opens. */
    fun open(p: PlayerRef) {
        p.transmitInv(Invs.TOA_CHESTS)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_CHESTS)
        p.clientScript(
            CS_INV_INIT,
            Components.Toa_Chests.ITEMS,
            Invs.TOA_CHESTS,
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
        p.ifEvents(
            Components.Toa_Chests.ITEMS,
            0 until p.inv(Invs.TOA_CHESTS).size,
            IfEvents.op(OP_TAKE) or IfEvents.op(OP_BANK) or IfEvents.op(OP_DISCARD),
        )
        sync(p)
    }

    /** The Rewards Niche "Claim" (TR29): the interface when loot waits, a line otherwise. */
    fun claim(p: PlayerRef) {
        if (isEmpty(p)) p.message(NOTHING_TO_CLAIM) else open(p)
    }

    /** `TOA_SHOULD_HAVE_LOOT` is 1 exactly while the claim inventory holds something (it morphs the lobby niche) and the chests follow. */
    fun sync(p: PlayerRef) {
        val want = if (isEmpty(p)) NICHE_FALSE_DOOR else NICHE_OPEN
        if (p.varbit(Varbits.TOA_SHOULD_HAVE_LOOT) != want) p.setVarbit(Varbits.TOA_SHOULD_HAVE_LOOT, want)
        ToaFlow.runOf(p)?.let { ToaTomb.syncChests(p, it) }
    }

    fun isEmpty(p: PlayerRef): Boolean {
        val inv = p.inv(Invs.TOA_CHESTS)
        return inv.freeSlots == inv.size
    }

    /** One item slot's op; the client's slot and obj are checked against the inventory first (hostile input). */
    fun itemOp(p: PlayerRef, click: ButtonClick) {
        val inv = p.inv(Invs.TOA_CHESTS)
        if (!p.isOpen(Interfaces.TOA_CHESTS) || !validSlot(inv, click.slot, click.obj)) return
        when (click.op) {
            OP_TAKE -> {
                take(p, click.slot, click.obj)
            }

            OP_BANK -> {
                bank(p, click.slot, click.obj)
            }

            OP_DISCARD -> {
                val slot = click.slot
                val obj = click.obj
                val count = inv.count(slot)
                p.choiceDialog("Discard $count x ${p.objName(obj)}?", listOf("Yes, discard it.", "No.")) { pick ->
                    if (pick == 1) discard(this, slot, obj, count)
                }
            }
        }
        sync(p)
    }

    /** Hostile input: the slot must exist and hold the obj the client names. */
    private fun validSlot(inv: InvView, slot: Int, obj: Int): Boolean = slot in 0 until inv.size && obj >= 0 && inv.obj(slot) == obj

    /** "Take-all": every stack into the backpack as far as it fits. */
    fun takeAll(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TOA_CHESTS)) return
        for (slot in 0 until p.inv(Invs.TOA_CHESTS).size) {
            val obj = p.inv(Invs.TOA_CHESTS).obj(slot)
            if (obj >= 0 && !take(p, slot, obj)) break
        }
        sync(p)
    }

    /** "Bank-all": every stack into the bank (notes as the item they stand for), without needing a bank open. */
    fun bankAll(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TOA_CHESTS)) return
        for (slot in 0 until p.inv(Invs.TOA_CHESTS).size) {
            val obj = p.inv(Invs.TOA_CHESTS).obj(slot)
            if (obj >= 0 && !bank(p, slot, obj)) break
        }
        sync(p)
    }

    /** "Discard-all": asked first; destroys the whole claim inventory. */
    fun discardAll(p: PlayerRef) {
        if (!p.isOpen(Interfaces.TOA_CHESTS) || isEmpty(p)) return
        p.choiceDialog("Discard all of your loot?", listOf("Yes, discard it all.", "No.")) { pick ->
            if (pick == 1) {
                clear(this)
                sync(this)
            }
        }
    }

    /** Destroys the claim inventory (a transaction; used by Discard-all and by entering another raid with loot, TR29). */
    fun clear(p: PlayerRef) {
        val inv = p.inv(Invs.TOA_CHESTS)
        val slots = (0 until inv.size).filter { inv.obj(it) >= 0 }
        if (slots.isEmpty()) return
        p.transaction("toa chest discard all") { slots.all { removeAt(Invs.TOA_CHESTS, it, inv.obj(it), inv.count(it)) } }
    }

    private fun take(p: PlayerRef, slot: Int, obj: Int): Boolean {
        val count = p.inv(Invs.TOA_CHESTS).count(slot)
        val ok = p.transaction("toa chest take") { move(Invs.TOA_CHESTS, slot, obj, count, Invs.INV) }
        if (!ok) p.message(NO_INV_SPACE)
        return ok
    }

    private fun bank(p: PlayerRef, slot: Int, obj: Int): Boolean {
        if (p.objParam(obj, PARAM_NO_BANK, 0) != 0) {
            p.message(NO_BANK_MESSAGE)
            return false
        }
        val count = p.inv(Invs.TOA_CHESTS).count(slot)
        val real = p.unnoted(obj)
        val ok = p.transaction("toa chest bank") { removeAt(Invs.TOA_CHESTS, slot, obj, count) && add(Invs.BANK, real, count) }
        if (!ok) p.message(NO_BANK_SPACE)
        return ok
    }

    private fun discard(p: PlayerRef, slot: Int, obj: Int, count: Int) {
        val inv = p.inv(Invs.TOA_CHESTS)
        if (slot !in 0 until inv.size || inv.obj(slot) != obj || inv.count(slot) != count) return
        p.transaction("toa chest discard") { removeAt(Invs.TOA_CHESTS, slot, obj, count) }
        sync(p)
    }

    /** `TOA_SHOULD_HAVE_LOOT` 14319: 0 = False Door, 1 = Rewards Niche (client-contract 7.2). */
    private const val NICHE_FALSE_DOOR = 0
    private const val NICHE_OPEN = 1
}

/**
 * The burial chamber after the Wardens (lobby-rewards TL15-TL17, TR11, TR25; client-contract 2.9, 7.3). Rewards are rolled
 * once when the raid completes and paid into inventory 811 by raids-core. Each member of the party has the chest of
 * their orb-list slot (`TOA_VAULT_CHEST_k` is 2 for the owner, 4 once it is empty); the player who won the unique opens
 * the sarcophagus instead (`TOA_VAULT_SARCOPHAGUS` odd = purple for the whole team). The unique winner's other items stay
 * with the unique (OPEN-TR8).
 */
internal object ToaTomb {
    /** The eight chests' template tiles in `TOA_VAULT_CHEST_LOC0..7` order (client-contract 6.2). */
    private val CHESTS =
        listOf(
            CoordGrid(3686, 5145),
            CoordGrid(3673, 5145),
            CoordGrid(3686, 5142),
            CoordGrid(3673, 5142),
            CoordGrid(3686, 5148),
            CoordGrid(3673, 5148),
            CoordGrid(3686, 5139),
            CoordGrid(3673, 5139),
        )
    private val CHEST_VARBITS =
        intArrayOf(
            Varbits.TOA_VAULT_CHEST_0,
            Varbits.TOA_VAULT_CHEST_1,
            Varbits.TOA_VAULT_CHEST_2,
            Varbits.TOA_VAULT_CHEST_3,
            Varbits.TOA_VAULT_CHEST_4,
            Varbits.TOA_VAULT_CHEST_5,
            Varbits.TOA_VAULT_CHEST_6,
            Varbits.TOA_VAULT_CHEST_7,
        )

    /** Client-contract 7.3: the chest multi's values: 2 = mine ("Open"), 4 = emptied ("Search"), anything else not mine. */
    private const val CHEST_NOT_MINE = 0
    private const val CHEST_MINE = 2
    private const val CHEST_EMPTY = 4

    /** Client-contract 7.3: odd sarcophagus values show the purple form. */
    private const val SARCOPHAGUS_WHITE = 0
    private const val SARCOPHAGUS_PURPLE = 1

    /** A click within a tile of a chest's placement belongs to it (the chests are 2 x 2 and three tiles apart). */
    private const val CHEST_REACH = 1

    /** The burial chamber pin of Osmumten and the vault spirit (client-contract 8: multi 11829; wiki pin (3680, 5144)). */
    private val OSMUMTEN_AT = CoordGrid(3680, 5144)

    /** TL16: the tomb's reward is final and paid; set up the player's view and the Osmumten who lets them leave. */
    fun completed(run: RaidRun, any: EntityRef) {
        val l = ToaPoints.ledger(run) ?: return
        ToaSummary.finish(run, any)
        for (p in run.presentPlayers(any)) personal(p, run)
        // OPEN-TA33: where Osmumten stands is the wiki pin; the NPC is a multi on the viewer's quest varbit.
        Raids.spawn(any, run, Npcs.TOA_OSMUMTEN_VAULT, OSMUMTEN_AT)
        l.completed = true
    }

    /** What the client reads when the raid ends (runelite-timings RT160, RT176) and what the chests need. */
    private fun personal(p: PlayerRef, run: RaidRun) {
        val m = run.member(p.username) ?: return
        val l = ToaPoints.ledger(run) ?: return
        val t = l.tally(m.name)
        // RT160: the contribution without the 5,000 start, sent once at the end.
        p.setVarp(Varps.TOA_PERSONAL_CONTRIBUTION, ToaPoints.contribution(m.points))
        p.setVarbit(Varbits.TOA_DAMAGE_DONE, t.dealt.coerceAtMost(DAMAGE_VARBIT_MAX).toInt())
        p.setVarbit(Varbits.TOA_DAMAGE_TAKEN, t.taken.coerceAtMost(DAMAGE_VARBIT_MAX).toInt())
        if (m.reward?.tags?.contains(ToaLoot.TAG_THREAD) == true) p.setSaved(ToaLoot.THREAD_SAVED, 1L)
        ToaChestUi.sync(p)
    }

    /** `TOA_DAMAGE_DONE` and `_TAKEN` are 16-bit varbits. */
    private const val DAMAGE_VARBIT_MAX = 65_535L

    /** [p]'s view of the eight chests and the sarcophagus (a no-op until the raid has completed). */
    fun syncChests(p: PlayerRef, run: RaidRun) {
        if (run.state != RunState.Completed) return
        val me = run.party.members.indexOfFirst { it.equals(p.username, ignoreCase = true) }
        val winner = isWinner(run, p.username)
        val purple = run.members.values.any { it.reward?.tags?.contains(ToaLoot.TAG_PURPLE) == true }
        for (k in CHEST_VARBITS.indices) {
            val value =
                when {
                    k != me || winner || run.party.members.getOrNull(k) == null -> CHEST_NOT_MINE
                    ToaChestUi.isEmpty(p) -> CHEST_EMPTY
                    else -> CHEST_MINE
                }
            if (p.varbit(CHEST_VARBITS[k]) != value) p.setVarbit(CHEST_VARBITS[k], value)
        }
        val flames = if (purple) SARCOPHAGUS_PURPLE else SARCOPHAGUS_WHITE
        if (p.varbit(Varbits.TOA_VAULT_SARCOPHAGUS) != flames) p.setVarbit(Varbits.TOA_VAULT_SARCOPHAGUS, flames)
    }

    /** The viewer leaves the burial chamber: their chest and flame varbits go. */
    fun left(p: PlayerRef) {
        for (v in CHEST_VARBITS) if (p.varbit(v) != 0) p.setVarbit(v, 0)
        if (p.varbit(Varbits.TOA_VAULT_SARCOPHAGUS) != 0) p.setVarbit(Varbits.TOA_VAULT_SARCOPHAGUS, 0)
    }

    private fun isWinner(run: RaidRun, name: String): Boolean =
        run
            .member(name)
            ?.reward
            ?.tags
            ?.contains(ToaLoot.TAG_PURPLE) == true

    /** The chest slot [loc] belongs to, or null when it is none of the run's chests. */
    private fun slotOf(run: RaidRun, loc: LocRef): Int? =
        CHESTS.indices.firstOrNull { k ->
            val at = run.at(CHESTS[k])
            at.level == loc.coord.level && kotlin.math.abs(at.x - loc.coord.x) <= CHEST_REACH &&
                kotlin.math.abs(at.z - loc.coord.z) <= CHEST_REACH
        }

    /** "Open" / "Search" on a chest: only its owner, only once the raid is complete; the loot interface shows inventory 811. */
    fun openChest(p: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(p) ?: return
        if (run.state != RunState.Completed || run.instance?.let { loc.coord in it } != true) return
        val k = slotOf(run, loc) ?: return
        val owner = run.party.members.getOrNull(k)
        if (owner == null || !owner.equals(p.username, ignoreCase = true) || isWinner(run, p.username)) {
            p.message(NOT_YOURS)
            return
        }
        if (ToaChestUi.isEmpty(p)) p.message(CHEST_EMPTY_MESSAGE) else ToaChestUi.open(p)
    }

    /** "Open" / "Search" on the sarcophagus: only the unique winner. */
    fun openSarcophagus(p: PlayerRef, loc: LocRef) {
        val run = ToaFlow.runOf(p) ?: return
        if (run.state != RunState.Completed || run.instance?.let { loc.coord in it } != true) return
        if (!isWinner(run, p.username)) {
            p.message(NOT_YOURS)
            return
        }
        if (ToaChestUi.isEmpty(p)) p.message(CHEST_EMPTY_MESSAGE) else ToaChestUi.open(p)
    }

    /** TA69: the bags hold The jackal's torch; one copy per player. */
    fun searchBags(p: PlayerRef) {
        val run = ToaFlow.runOf(p) ?: return
        if (run.state != RunState.Completed) return
        val book = Objs.TOA_BOOK_ICTHLARIN
        val owned = p.inv(Invs.INV).total(book) > 0 || p.inv(Invs.BANK).total(book) > 0
        if (owned) {
            p.message(NOTHING_ELSE)
        } else if (p.transaction("toa jackal book") { add(Invs.INV, book) }) {
            p.message(BOOK_FOUND)
        } else {
            p.message(NO_SPACE)
        }
    }

    /** Osmumten's "Leave" (TA22, OPEN-TA33): out of the raid; raid-only items go, the claim inventory stays. */
    fun leave(p: PlayerRef) {
        val run = ToaFlow.runOf(p) ?: return
        Raids.leave(p, run)
    }

    /** OPEN-TR17 / OPEN-TA33: none of these lines is captured. */
    private const val NOT_YOURS = "This is not your chest."
    private const val CHEST_EMPTY_MESSAGE = "The chest is empty."
    private const val NOTHING_ELSE = "You find nothing else of interest."
    private const val BOOK_FOUND = "You find a book among the bags."
    private const val NO_SPACE = "You don't have enough inventory space."
}

/** The burial chamber's locs and Osmumten, interface 771's buttons and the claim state on login and on leaving. */
public class ToaTombPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }
        onButton(Components.Toa_Chests.BANK) { ToaChestUi.bankAll(this) }
        onButton(Components.Toa_Chests.INVENTORY) { ToaChestUi.takeAll(this) }
        onButton(Components.Toa_Chests.DISCARD) { ToaChestUi.discardAll(this) }
        onButton(Components.Toa_Chests.ITEMS) { click -> ToaChestUi.itemOp(this, click) }
        // The eight chest multis (parents LOC0..7) and the children a client that resolves them sends.
        for (id in CHEST_LOCS) onOpLoc(id, OP1) { loc -> ToaTomb.openChest(player, loc) }
        // The sarcophagus multi and its two openable forms.
        for (id in SARCOPHAGUS_LOCS) onOpLoc(id, OP1) { loc -> ToaTomb.openSarcophagus(player, loc) }
        onOpLoc(Locs.TOA_VAULT_SCOREBOARD, OP1) { ToaSummary.open(player) }
        onOpLoc(Locs.TOA_VAULT_BOOK_SACKS, OP1) { ToaTomb.searchBags(player) }
        // Osmumten in the burial chamber: "Leave" is op 3 of the third form (cache npc 11693).
        onOpNpc(Npcs.TOA_OSMUMTEN_VAULT_VIS_2, OP_LEAVE) { ToaTomb.leave(player) }
        events.on<RaidEvent.Left> { e ->
            if (e.run.raid.id == ToaRaid.ID) {
                world?.onlinePlayer(e.member)?.let {
                    ToaTomb.left(it)
                    ToaChestUi.sync(it)
                }
            }
        }
        events.on<RaidEvent.Reconnected> { e ->
            if (e.run.raid.id == ToaRaid.ID) world?.onlinePlayer(e.member)?.let { ToaChestUi.sync(it) }
        }
        // TR29 / OPEN-TR16: entering another raid without claiming loses the loot (by flag).
        events.on<RaidEvent.Entered> { e ->
            if (e.run.raid.id == ToaRaid.ID && ToaConfig.unclaimedLootLostOnEntry) {
                world?.onlinePlayer(e.member)?.let {
                    if (!ToaChestUi.isEmpty(it)) {
                        ToaChestUi.clear(it)
                        it.message(LOOT_LOST)
                    }
                }
            }
        }
        onLogin {
            ToaChestUi.sync(this)
            // A reward paid by the raid plugin's own login hook (a run still alive) lands after this one.
            weakQueue(0) { ToaChestUi.sync(player) }
        }
    }

    private companion object {
        const val OP1 = 1
        const val OP_LEAVE = 3
        const val LOOT_LOST = "Your unclaimed loot from the last raid was lost when you entered the tombs."

        val CHEST_LOCS =
            intArrayOf(
                Locs.TOA_VAULT_CHEST_LOC0,
                Locs.TOA_VAULT_CHEST_LOC1,
                Locs.TOA_VAULT_CHEST_LOC2,
                Locs.TOA_VAULT_CHEST_LOC3,
                Locs.TOA_VAULT_CHEST_LOC4,
                Locs.TOA_VAULT_CHEST_LOC5,
                Locs.TOA_VAULT_CHEST_LOC6,
                Locs.TOA_VAULT_CHEST_LOC7,
                Locs.TOA_VAULT_CHEST_NOTMINE_STANDARD,
                Locs.TOA_VAULT_CHEST_MINE_STANDARD,
                Locs.TOA_VAULT_CHEST_OPEN,
                Locs.TOA_VAULT_CHEST_OPEN02,
                Locs.TOA_VAULT_CHEST_OPEN03,
                Locs.TOA_VAULT_CHEST_EMPTY,
                Locs.TOA_VAULT_CHEST_EMPTY02,
                Locs.TOA_VAULT_CHEST_EMPTY03,
            )
        val SARCOPHAGUS_LOCS =
            intArrayOf(
                Locs.TOA_VAULT_SARCOPHAGUS_PARENT,
                Locs.TOA_VAULT_SARCOPHAGUS_CLOSED_RARE,
                Locs.TOA_VAULT_SARCOPHAGUS_OPEN,
            )
    }
}
