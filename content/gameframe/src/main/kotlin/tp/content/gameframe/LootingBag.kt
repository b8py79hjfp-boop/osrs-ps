package tp.content.gameframe

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.ObjRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The looting bag (docs/mechanics/looting-bag.md LB1-LB8; Wiki "Looting bag"): 28 stacks, kept in inv `looting_bag`
 * (516). Tradeable items go in only in the Wilderness: used on the bag (one, five or all, or always all by the setting)
 * or through "Deposit" (the bag's side panel, interface 81, over the backpack: Store-1/5/All/X). "Open" makes tradeable
 * pickups in the Wilderness go into the bag ("Close" stops it). "Check" shows the contents; only a bank empties it.
 */
public class LootingBag : ContentPlugin {
    override fun ContentScope.register() {
        for (bag in BAGS) {
            onItemOp(bag, "Check") { show(this, Invs.LOOTING_BAG) }
            onItemOp(bag, "Deposit") { deposit(this) }
            onItemOp(bag, "Settings") { settings(this) }
            onUseOnItem(bag, -1) { use -> useOn(this, use.toSlot, use.toObj) }
        }
        onItemOp(Objs.LOOTING_BAG, "Open") { click -> swap(this, click, Objs.LOOTING_BAG_OPEN) }
        onItemOp(Objs.LOOTING_BAG_OPEN, "Close") { click -> swap(this, click, Objs.LOOTING_BAG) }
        onButton(Components.Wilderness_Lootingbag.COM_2) { close(this) }
        onButton(Components.Wilderness_Lootingbag.ITEMS) { click -> panelItem(this, click) }
    }

    internal companion object {
        val BAGS = intArrayOf(Objs.LOOTING_BAG, Objs.LOOTING_BAG_OPEN)

        /** Script 497 draws the panel from an inv: 93 (the backpack, Store ops) or 516 (the bag's contents). */
        private const val CS_DRAW = 497

        /** Script 1235: the panel's per-slot value tooltips (28 longs) for the drawn inv. */
        private const val CS_VALUES = 1235
        private const val BACKPACK = 93
        private const val SLOTS = 28
        private val STORE_OPS = (1..5).fold(0L) { acc, op -> acc or IfEvents.op(op) } or IfEvents.op(EXAMINE)
        private const val EXAMINE = 10
        private const val OP_ONE = 1
        private const val OP_FIVE = 2
        private const val OP_ALL = 3
        private const val OP_X = 4
        private const val FIVE = 5

        fun holds(p: PlayerRef): Boolean = BAGS.any { p.inv(Invs.INV).total(it) > 0 }

        private fun inWilderness(p: PlayerRef): Boolean = p[SharedAttrs.IN_WILDERNESS] == true

        /** The bag's side panel over the backpack, drawn from [invId]. */
        fun show(p: PlayerRef, invId: Int) {
            p.openOverlay(Components.Toplevel.SIDE3, Interfaces.WILDERNESS_LOOTINGBAG)
            p.transmitInv(Invs.LOOTING_BAG)
            p.ifText(Components.Wilderness_Lootingbag.TITLE, if (invId == Invs.LOOTING_BAG) "Looting bag" else "Add to bag")
            val view = p.inv(if (invId == Invs.LOOTING_BAG) Invs.LOOTING_BAG else Invs.INV)
            val value =
                (0 until view.size).sumOf { s ->
                    view.obj(s).takeIf { it >= 0 }?.let { p.objValue(it).toLong() * view.count(s) }
                        ?: 0L
                }
            p.ifText(Components.Wilderness_Lootingbag.TOTAL, "Value: ${"%,d".format(value)} coins")
            val drawn = if (invId == Invs.LOOTING_BAG) Invs.LOOTING_BAG else BACKPACK
            p.clientScript(CS_DRAW, drawn)
            val values =
                (0 until SLOTS).map { s ->
                    view.obj(s).takeIf { s < view.size && it >= 0 }?.let { p.objValue(it).toLong() } ?: -1L
                }
            p.clientScript(CS_VALUES, drawn, *values.toTypedArray())
            if (invId != Invs.LOOTING_BAG) p.ifEvents(Components.Wilderness_Lootingbag.ITEMS, 0 until SLOTS, STORE_OPS)
        }

        private fun close(p: PlayerRef) = SideInventory.restore(p)

        private fun deposit(p: PlayerRef) {
            if (!inWilderness(p)) return p.message("You can't put items in the bag unless you're in the Wilderness.")
            show(p, Invs.INV)
        }

        private fun panelItem(p: PlayerRef, click: ButtonClick) {
            val backpack = p.inv(Invs.INV)
            if (click.slot !in 0 until backpack.size || backpack.obj(click.slot) != click.obj || click.obj < 0) return
            if (click.op == EXAMINE) return Examines.item(p, click.obj, backpack.count(click.slot))
            when (click.op) {
                OP_ONE -> store(p, click.obj, 1)
                OP_FIVE -> store(p, click.obj, FIVE)
                OP_ALL -> store(p, click.obj, Int.MAX_VALUE)
                OP_X -> p.countDialog("Enter amount:") { n -> if (n > 0) store(p, click.obj, n) }
            }
            show(p, Invs.INV)
        }

        /** Use an item on the bag: one, five or all (always all with the setting on). */
        private fun useOn(p: PlayerRef, slot: Int, obj: Int) {
            if (obj in BAGS) return
            if (p.inv(Invs.INV).obj(slot) != obj) return
            if (p.varbit(Varbits.LOOTINGBAG_USEALLITEMS) == 1) return store(p, obj, Int.MAX_VALUE)
            p.choiceDialog("How many do you want to deposit?", listOf("One", "Five", "All", "X")) { pick ->
                when (pick) {
                    1 -> store(this, obj, 1)
                    2 -> store(this, obj, FIVE)
                    3 -> store(this, obj, Int.MAX_VALUE)
                    4 -> countDialog("Enter amount:") { n -> if (n > 0) store(this, obj, n) }
                }
            }
        }

        /** LB2-LB3: up to [requested] of [obj] from the backpack into the bag (Wilderness, tradeable, room for the stack). */
        fun store(p: PlayerRef, obj: Int, requested: Int) {
            when {
                !inWilderness(p) -> return p.message("You can't put items in the bag unless you're in the Wilderness.")
                obj in BAGS -> return p.message("You may be surprised to learn that bagception is not permitted.")
                !p.objTradeable(obj) -> return p.message("Only tradeable items can be put in the bag.")
            }
            val n = minOf(requested.toLong(), p.inv(Invs.INV).total(obj)).toInt()
            if (n <= 0) return
            if (!p.transaction("looting bag store") { move(Invs.INV, slotOf(p, obj), obj, n, Invs.LOOTING_BAG) }) {
                p.message("The bag's too full.")
            }
        }

        private fun slotOf(p: PlayerRef, obj: Int): Int = (0 until p.inv(Invs.INV).size).first { p.inv(Invs.INV).obj(it) == obj }

        /** LB4: an open bag in the Wilderness takes tradeable pickups; false when it didn't (the backpack takes it). */
        fun pickUp(p: PlayerRef, obj: ObjRef): Boolean {
            if (p.inv(Invs.INV).total(Objs.LOOTING_BAG_OPEN) <= 0 || !inWilderness(p) || !p.objTradeable(obj.id)) return false
            return p.transaction("looting bag pickup") { take(obj, Invs.LOOTING_BAG) }
        }

        private fun swap(p: PlayerRef, click: ButtonClick, to: Int) {
            p.transaction("looting bag toggle") { replaceAt(Invs.INV, click.slot, click.obj, to) }
        }

        /**
         * LB7 Wiki "Looting bag": destroyed in the Wilderness, "all items inside are dropped on the ground and are available
         * for everyone to immediately see", except un-noted cooked food, potions and vials of water, which are destroyed;
         * outside the Wilderness "all items inside [are] lost".
         */
        fun destroy(p: PlayerRef, slot: Int, obj: Int) {
            val bag = p.inv(Invs.LOOTING_BAG)
            val wild = inWilderness(p)
            p.transaction("looting bag destroy") {
                removeAt(Invs.INV, slot, obj, 1) &&
                    (0 until bag.size).all { s ->
                        val item = bag.obj(s)
                        val n = bag.count(s)
                        item < 0 ||
                            (
                                removeAt(Invs.LOOTING_BAG, s, item, n) &&
                                    (!wild || lostOnDestroy(p, item) || createOnGroundPublic(item, n, p.coord))
                            )
                    }
            }
        }

        /** Un-noted food, potions and vials of water: an "Eat" / "Drink" op, or the vial of water itself. */
        private fun lostOnDestroy(p: PlayerRef, obj: Int): Boolean =
            obj == Objs.VIAL_WATER || (0 until IOPS).any { p.objIop(obj, it) == "Eat" || p.objIop(obj, it) == "Drink" }

        private const val IOPS = 5

        /** LB3 "Settings": whether using an item on the bag stores all at once. */
        private fun settings(p: PlayerRef) {
            p.choiceDialog("When using items on the bag:", listOf("Ask how many to store", "Always store as many as possible")) { pick ->
                setVarbit(Varbits.LOOTINGBAG_USEALLITEMS, if (pick == 2) 1 else 0)
            }
        }
    }
}
