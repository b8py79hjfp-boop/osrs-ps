package tp.content.skills

import tp.plugin.BankItemOp
import tp.plugin.BankTransfer
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.ObjPickedUp
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * The seed box (docs/mechanics/storage-bags.md SB1-SB6; Wiki "Seed box"): six kinds of seed, any amount, in inv 573
 * (`seed_box`). Fill / Empty / Check (interface 128) / Open / Close; an open box takes seeds picked up; used on a patch
 * it plants the first seed that fits (content/skills Farming); "Empty" in the bank banks them.
 */
public class SeedBox : ContentPlugin {
    override fun ContentScope.register() {
        for (box in BOXES) {
            onItemOp(box, "Fill") { click -> if (holds(this, click)) fill(this) }
            onItemOp(box, "Empty") { click -> if (holds(this, click)) empty(this) }
            onItemOp(box, "Check") { click -> if (holds(this, click)) check(this) }
            onUseOnItem(box, -1) { use -> if (isSeed(this, use.toObj)) store(this, use.toObj) }
        }
        onItemOp(Objs.SEED_BOX, "Open") { click -> swap(this, click, Objs.SEED_BOX_OPEN) }
        onItemOp(Objs.SEED_BOX_OPEN, "Close") { click -> swap(this, click, Objs.SEED_BOX) }
        onButton(Components.Hosidius_Seedbox.EMPTY_BUTTON) { empty(this) }
        onButton(Components.Hosidius_Seedbox.FILL_BUTTON) { fill(this) }
        onButton(Components.Hosidius_Seedbox.CLOSE_BUTTON) { closeModal() }
        onButtonDrag(Components.Hosidius_Seedbox.SEED_LAYER) { d ->
            val n = inv(Invs.SEED_BOX).size
            if (d.toComponent == Components.Hosidius_Seedbox.SEED_LAYER && d.fromSlot in 0 until n && d.toSlot in 0 until n) {
                transaction("seed box reorder") { swap(Invs.SEED_BOX, d.fromSlot, d.toSlot) }
            }
        }
        events.on<ObjPickedUp> { e -> if (isSeed(e.player, e.obj)) autoStore(e.player, e.obj) }
        events.on<BankItemOp> { op -> if (op.obj in BOXES) bankEmpty(op) }
    }

    internal companion object {
        val BOXES = setOf(Objs.SEED_BOX, Objs.SEED_BOX_OPEN)

        /** Obj param 709: the farming patch type a seed is planted in; every seed, spore and frag has it. */
        private const val PARAM_PATCH_TYPE = 709

        /** Seed box slots in interface 128's seed layer: drag to reorder (Wiki, 3 June 2026). */
        private const val SLOTS = 6
        private const val CS_INV_INIT = 149
        private const val GRID_COLUMNS = 6
        private const val GRID_ROWS = 2
        private const val EXAMINE = 10

        fun isSeed(p: PlayerRef, obj: Int): Boolean = obj >= 0 && p.objParamInt(obj, PARAM_PATCH_TYPE) != null

        private fun holds(p: PlayerRef, click: ButtonClick): Boolean =
            p.inv(Invs.INV).let { click.slot in 0 until it.size && it.obj(click.slot) == click.obj }

        /** SB2: up to six kinds; a seventh kind stays in the backpack. Returns whether any moved. */
        private fun store(p: PlayerRef, seed: Int): Boolean {
            val held = p.inv(Invs.INV).total(seed)
            if (held <= 0) return false
            val box = p.inv(Invs.SEED_BOX)
            val stack = box.total(seed)
            val n = minOf(held, Int.MAX_VALUE - stack).toInt()
            if (n <= 0) return false
            if (stack == 0L && box.freeSlots == 0) {
                p.message("Your seed box is full.")
                return false
            }
            return p.transaction("seed box store") { remove(Invs.INV, seed, n) && add(Invs.SEED_BOX, seed, n) }
        }

        /** SB2 "Fill": every seed in the backpack, in backpack order. **OPEN-SB1:** messages. */
        private fun fill(p: PlayerRef) {
            val inv = p.inv(Invs.INV)
            val seeds = (0 until inv.size).map { inv.obj(it) }.filter { isSeed(p, it) }.distinct()
            if (seeds.isEmpty()) return p.message("You don't have any seeds to store.")
            var moved = false
            for (seed in seeds) if (store(p, seed)) moved = true
            if (moved) p.message("You put the seeds into the seed box.")
        }

        /** SB3 "Empty": every kind into the backpack as far as it fits. */
        private fun empty(p: PlayerRef) {
            val box = p.inv(Invs.SEED_BOX)
            if (box.freeSlots == box.size) return p.message("The seed box is empty.")
            for (slot in 0 until box.size) {
                val seed = box.obj(slot)
                if (seed < 0) continue
                if (!p.transaction("seed box empty") { move(Invs.SEED_BOX, slot, seed, box.count(slot), Invs.INV) }) {
                    return p.message("You don't have enough inventory space.")
                }
            }
        }

        /** SB4 "Check": interface 128 over the box's inv, and the contents in chat (Wiki, 20 July 2022). */
        private fun check(p: PlayerRef) {
            p.transmitInv(Invs.SEED_BOX)
            p.openModal(Components.Toplevel.MAINMODAL, Interfaces.HOSIDIUS_SEEDBOX)
            // CS 149 / 153 `interface_inv_init`: inv 573 in the seed layer, 6 across (CS 153 divides by rows - 1, so 2 rows), draggable.
            p.clientScript(
                CS_INV_INIT,
                Components.Hosidius_Seedbox.SEED_LAYER,
                Invs.SEED_BOX,
                GRID_COLUMNS,
                GRID_ROWS,
                1,
                -1,
                "",
                "",
                "",
                "",
                "",
            )
            p.ifEvents(
                Components.Hosidius_Seedbox.SEED_LAYER,
                0 until SLOTS,
                IfEvents.op(EXAMINE) or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET,
            )
            val box = p.inv(Invs.SEED_BOX)
            val held = (0 until box.size).filter { box.obj(it) >= 0 }
            if (held.isEmpty()) return p.message("The seed box is empty.")
            p.message("The seed box contains:")
            for (slot in held) p.message("${box.count(slot)} x ${p.objName(box.obj(slot))}")
        }

        private fun swap(p: PlayerRef, click: ButtonClick, to: Int) {
            if (holds(p, click)) p.transaction("seed box toggle") { replaceAt(Invs.INV, click.slot, click.obj, to) }
        }

        /** SB5: an open box takes seeds picked up while it can. */
        private fun autoStore(p: PlayerRef, seed: Int) {
            if (p.inv(Invs.INV).total(Objs.SEED_BOX_OPEN) > 0) store(p, seed)
        }

        private fun bankEmpty(op: BankItemOp) {
            op.handled = true
            val p = op.player
            val box = p.inv(Invs.SEED_BOX)
            if (box.freeSlots == box.size) return p.message("The seed box is empty.")
            for (slot in 0 until box.size) {
                val seed = box.obj(slot)
                if (seed >= 0) p.events.publish(BankTransfer(p, seed, box.count(slot), deposit = true, source = Invs.SEED_BOX))
            }
        }
    }
}
