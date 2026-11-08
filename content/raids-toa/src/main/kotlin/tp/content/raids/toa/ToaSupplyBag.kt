package tp.content.raids.toa

import tp.plugin.ButtonClick
import tp.plugin.ButtonDrag
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * The supplies bag (toa.md TA54; client-contract 2.8): item 27314 "Supplies" holds inventory 810 (28 slots, one per
 * potion) and is shown by interface 778 "Raid Supplies". The item's ops are Open, Withdraw 1, Withdraw All and Resupply
 * (Destroy is the generic backpack one); an item used on the bag is stored; the interface withdraws 1 / 5 / All, drops,
 * and rearranges by dragging. An empty bag is destroyed with "The bag disintegrates.". Every move is an item transaction.
 */
internal object ToaSupplyBag {
    private const val BAG = Objs.TOA_MIDRAIDLOOT_BAG
    private const val BAG_INV = Invs.TOA_MIDRAIDLOOT_BAG
    private const val EMPTY = -1

    /** Cache iops of item 27314 (TA54). */
    private const val OPEN = "Open"
    private const val WITHDRAW_1 = "Withdraw 1"
    private const val WITHDRAW_ALL = "Withdraw All"
    private const val RESUPPLY = "Resupply"

    /** Client-contract 2.8: 778:5 ops 1 Withdraw-1, 2 Withdraw-5, 3 Withdraw-All, 4 Drop, 9 Examine. */
    private const val OP_WITHDRAW_1 = 1
    private const val OP_WITHDRAW_5 = 2
    private const val OP_WITHDRAW_ALL = 3
    private const val OP_DROP = 4
    private const val OP_EXAMINE = 9

    /** TA54: "Withdraw-5" of the interface (client-contract 2.8). */
    private const val FIVE = 5

    private val BAG_EVENTS: Long =
        IfEvents.op(OP_WITHDRAW_1) or IfEvents.op(OP_WITHDRAW_5) or IfEvents.op(OP_WITHDRAW_ALL) or IfEvents.op(OP_DROP) or
            IfEvents.op(OP_EXAMINE) or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET

    // TA54 gives the disintegration line; OPEN-TA38: the other lines were not captured.
    private const val DISINTEGRATES = "The bag disintegrates."
    private const val NO_SPACE = "You don't have enough inventory space."
    private const val BAG_FULL = "The bag is full."
    private const val NOTHING_TO_RESUPPLY = "You have nothing to resupply."

    fun register(scope: ContentScope) {
        with(scope) {
            onItemOp(BAG, OPEN) { click -> open(click.slot) }
            onItemOp(BAG, WITHDRAW_1) { click -> withdrawFromItem(click.slot, all = false) }
            onItemOp(BAG, WITHDRAW_ALL) { click -> withdrawFromItem(click.slot, all = true) }
            onItemOp(BAG, RESUPPLY) { click -> resupply(click.slot) }
            // TA54: using a potion on the bag stores it in the first empty slot.
            for (obj in ToaSupply.ITEMS) {
                onUseOnItem(obj, BAG) { e -> store(e.fromSlot, e.fromObj, e.toSlot, e.toObj) }
            }
            onButton(Components.Toa_Midraidloot_Bag.ITEMS) { click -> interfaceClick(click) }
            onButton(Components.Toa_Midraidloot_Bag.COM_2) { closeModal() }
            onButtonDrag(Components.Toa_Midraidloot_Bag.ITEMS) { d -> reorder(d) }
        }
    }

    /** The bag item sits in [slot] of the backpack and the player is in a ToA run (hostile input). */
    private fun PlayerRef.holdsBag(slot: Int): Boolean = ToaFlow.runOf(this) != null && inv(Invs.INV).obj(slot) == BAG

    private fun PlayerRef.filledSlots(): List<Int> {
        val bag = inv(BAG_INV)
        return (0 until bag.size).filter { bag.obj(it) != EMPTY }
    }

    private fun PlayerRef.open(slot: Int) {
        if (!holdsBag(slot)) return
        if (filledSlots().isEmpty()) return disintegrate(slot)
        transmitInv(BAG_INV)
        openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_MIDRAIDLOOT_BAG)
        ifEvents(Components.Toa_Midraidloot_Bag.ITEMS, 0 until inv(BAG_INV).size, BAG_EVENTS)
    }

    /** TA54: "Withdraw 1" takes the first item left to right, top to bottom; "Withdraw All" repeats until empty or full. */
    private fun PlayerRef.withdrawFromItem(slot: Int, all: Boolean) {
        if (!holdsBag(slot)) return
        val filled = filledSlots()
        if (filled.isEmpty()) return disintegrate(slot)
        take(if (all) filled else filled.take(1))
    }

    /**
     * Moves the bag items in [slots] (bag slots, in order) into the backpack as far as free slots allow. TA54: it fails
     * when the backpack is full unless the item is the bag's last, because the bag then disintegrates and frees its slot.
     */
    private fun PlayerRef.take(slots: List<Int>) {
        val backpack = inv(Invs.INV)
        val bag = inv(BAG_INV)
        val bagSlot = (0 until backpack.size).firstOrNull { backpack.obj(it) == BAG } ?: return
        val filled = filledSlots().size
        var n = minOf(slots.size, backpack.freeSlots)
        if (slots.size >= filled && n == filled - 1) n = filled
        if (n <= 0) return message(NO_SPACE)
        val moving = slots.take(n)
        val disintegrates = moving.size == filled
        val ok =
            transaction("toa supplies withdraw") {
                if (disintegrates && !removeAt(Invs.INV, bagSlot, BAG, 1)) return@transaction false
                for (s in moving) {
                    val obj = bag.obj(s)
                    if (obj == EMPTY || !move(BAG_INV, s, obj, 1, Invs.INV)) return@transaction false
                }
                true
            }
        if (!ok) return message(NO_SPACE)
        if (disintegrates) closedBag()
    }

    /** TA54: tops up partly used potions in the backpack from the bag (a salt (1) becomes (2)), in slot order (OPEN-TA43). */
    private fun PlayerRef.resupply(slot: Int) {
        if (!holdsBag(slot)) return
        val backpack = inv(Invs.INV)
        val bag = inv(BAG_INV)
        val ok =
            transaction("toa supplies resupply") {
                var changed = false
                for (s in 0 until backpack.size) {
                    val (supply, dose) = ToaSupply.of(backpack.obj(s)) ?: continue
                    var have = dose
                    for (b in 0 until bag.size) {
                        if (have >= supply.maxDoses) break
                        val (other, doses) = ToaSupply.of(bag.obj(b)) ?: continue
                        if (other != supply) continue
                        val give = minOf(supply.maxDoses - have, doses)
                        have += give
                        val moved =
                            if (give == doses) {
                                removeAt(BAG_INV, b, bag.obj(b), 1)
                            } else {
                                replaceAt(BAG_INV, b, bag.obj(b), supply.item(doses - give))
                            }
                        if (!moved) return@transaction false
                    }
                    if (have != dose) {
                        changed = true
                        if (!replaceAt(Invs.INV, s, backpack.obj(s), supply.item(have))) return@transaction false
                    }
                }
                changed
            }
        if (!ok) return message(NOTHING_TO_RESUPPLY)
        if (filledSlots().isEmpty()) disintegrate(slot)
    }

    /** TA54: a potion used on the bag goes to the first empty slot (hostile input: both slots are checked). */
    private fun PlayerRef.store(fromSlot: Int, fromObj: Int, toSlot: Int, toObj: Int) {
        if (ToaFlow.runOf(this) == null || toObj != BAG || ToaSupply.of(fromObj) == null) return
        val backpack = inv(Invs.INV)
        if (fromSlot !in 0 until backpack.size || toSlot !in 0 until backpack.size) return
        if (backpack.obj(fromSlot) != fromObj || backpack.obj(toSlot) != BAG) return
        if (inv(BAG_INV).freeSlots == 0) return message(BAG_FULL)
        transaction("toa supplies store") { move(Invs.INV, fromSlot, fromObj, 1, BAG_INV) }
    }

    // ---- the interface 778

    /** IF_BUTTON on 778:5, child = slot (client-contract 2.8). Hostile input: the slot must hold the clicked obj. */
    private fun PlayerRef.interfaceClick(click: ButtonClick) {
        if (ToaFlow.runOf(this) == null) return
        if (inv(Invs.INV).total(BAG) == 0L) return closeModal()
        val bag = inv(BAG_INV)
        val slot = click.slot
        if (slot !in 0 until bag.size || click.obj == EMPTY || bag.obj(slot) != click.obj) return
        // OPEN-TA43: "Withdraw-5" and "Withdraw-All" take the clicked potion and then more of the same item, in slot order.
        val same = (0 until bag.size).filter { it != slot && bag.obj(it) == click.obj }
        when (click.op) {
            OP_WITHDRAW_1 -> take(listOf(slot))
            OP_WITHDRAW_5 -> take((listOf(slot) + same).take(FIVE))
            OP_WITHDRAW_ALL -> take(listOf(slot) + same)
            OP_DROP -> dropItem(slot, click.obj)
        }
    }

    /** TA54: items can be dropped from the bag interface; the clicked slot is the one dropped. */
    private fun PlayerRef.dropItem(slot: Int, obj: Int) {
        if (!transaction("toa supplies drop") { drop(BAG_INV, slot, obj, 1) }) return
        if (filledSlots().isEmpty()) closedBag()
    }

    /** A drag inside 778:5 swaps two slots ("free rearranging", TA54). */
    private fun PlayerRef.reorder(d: ButtonDrag) {
        if (ToaFlow.runOf(this) == null || d.toComponent != Components.Toa_Midraidloot_Bag.ITEMS) return
        val size = inv(BAG_INV).size
        if (d.fromSlot !in 0 until size || d.toSlot !in 0 until size || d.fromSlot == d.toSlot) return
        transaction("toa supplies reorder") { swap(BAG_INV, d.fromSlot, d.toSlot) }
    }

    // ---- ending the bag

    /** An empty bag in [slot] is destroyed (TA54). */
    private fun PlayerRef.disintegrate(slot: Int) {
        if (inv(Invs.INV).obj(slot) != BAG) return
        if (transaction("toa supplies bag disintegrates") { removeAt(Invs.INV, slot, BAG, 1) }) closedBag()
    }

    private fun PlayerRef.closedBag() {
        message(DISINTEGRATES)
        if (isOpen(Interfaces.TOA_MIDRAIDLOOT_BAG)) closeModal()
    }

    /** The bag's contents go when the raid does (the bag item is a raid-only item and leaves with it). */
    fun empty(p: PlayerRef) {
        val bag = p.inv(BAG_INV)
        val slots = (0 until bag.size).filter { bag.obj(it) != EMPTY }
        if (slots.isEmpty()) return
        p.transaction("toa supplies cleared") { slots.all { removeAt(BAG_INV, it, bag.obj(it), bag.count(it)) } }
    }
}
