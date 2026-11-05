package tp.engine.inv

import tp.cache.config.TypeList
import tp.cache.config.obj.ObjType

/**
 * The single item-movement API (brief §12): every add, remove, move or swap of any inventory happens inside one
 * transaction. It is atomic (any failed step rolls back every step) and journaled (a committed transaction is
 * reported to the [ItemJournal] with its reason and each slot change). No other code writes inventory slots.
 */
class ItemTransaction internal constructor(private val objTypes: TypeList<ObjType>) {
    private class Change(val inv: Inventory, val slot: Int, val oldObj: Int, val oldCount: Int)

    private val changes = ArrayList<Change>()

    /** Set when a step fails; the transaction will roll back. */
    var failed: Boolean = false
        private set

    private fun write(inv: Inventory, slot: Int, obj: Int, count: Int) {
        changes += Change(inv, slot, inv.objs[slot], inv.counts[slot])
        inv.write(slot, obj, count)
    }

    private fun fail(): Boolean {
        failed = true
        return false
    }

    private fun stacks(inv: Inventory, obj: Int): Boolean =
        inv.stack == InvStack.ALWAYS || objTypes[obj]?.isStackable == true

    /** Adds [count] of [obj]: onto its stack if it stacks here, else one per free slot. */
    @Suppress("ReturnCount") // each failure is an early abort
    fun add(inv: Inventory, obj: Int, count: Int): Boolean {
        if (failed) return false
        if (count <= 0 || objTypes[obj] == null) return fail()
        if (stacks(inv, obj)) {
            val slot = inv.slotOf(obj).takeIf { it >= 0 } ?: inv.firstFree()
            if (slot < 0) return fail()
            val total = inv.counts[slot].toLong() + count
            if (total > Int.MAX_VALUE) return fail()
            write(inv, slot, obj, total.toInt())
            return true
        }
        if (inv.freeSlots < count) return fail()
        repeat(count) { write(inv, inv.firstFree(), obj, 1) }
        return true
    }

    /** Removes [count] of [obj] from anywhere in [inv] (lowest slots first). Fails if there aren't that many. */
    fun remove(inv: Inventory, obj: Int, count: Int): Boolean {
        if (failed) return false
        if (count <= 0 || inv.total(obj) < count) return fail()
        var left = count
        for (slot in 0 until inv.size) {
            if (left == 0) break
            if (inv.objs[slot] != obj) continue
            val take = minOf(left, inv.counts[slot])
            write(inv, slot, obj, inv.counts[slot] - take)
            left -= take
        }
        return true
    }

    /** Removes [count] from exactly [slot], which must hold [obj] (client-named slots are verified: brief §12). */
    fun removeAt(inv: Inventory, slot: Int, obj: Int, count: Int): Boolean {
        if (failed) return false
        val holds = slot in 0 until inv.size && inv.objs[slot] == obj
        if (!holds || count !in 1..inv.counts[slot]) return fail()
        write(inv, slot, obj, inv.counts[slot] - count)
        return true
    }

    /** Swaps two slots of one inventory (drag). */
    fun swap(inv: Inventory, from: Int, to: Int): Boolean {
        if (failed) return false
        if (from !in 0 until inv.size || to !in 0 until inv.size) return fail()
        if (from == to) return true
        val obj = inv.objs[from]
        val count = inv.counts[from]
        write(inv, from, inv.objs[to], inv.counts[to])
        write(inv, to, obj, count)
        return true
    }

    /** Moves [count] of [obj] from [fromSlot] of [from] into [to] (stacking rules of [to]). */
    fun move(from: Inventory, fromSlot: Int, obj: Int, count: Int, to: Inventory): Boolean =
        removeAt(from, fromSlot, obj, count) && add(to, obj, count)

    internal fun rollback() {
        for (i in changes.indices.reversed()) {
            val c = changes[i]
            c.inv.write(c.slot, c.oldObj, c.oldCount)
        }
        changes.clear()
    }

    internal fun entries(): List<ItemJournal.SlotChange> =
        changes.map { ItemJournal.SlotChange(it.inv.invId, it.slot, it.oldObj, it.oldCount, it.inv.objs[it.slot], it.inv.counts[it.slot]) }

    internal val isEmpty: Boolean get() = changes.isEmpty()
}

/** Where committed item transactions are recorded (audit trail; persistence in Phase 5). Must not block. */
fun interface ItemJournal {
    class SlotChange(val invId: Int, val slot: Int, val oldObj: Int, val oldCount: Int, val newObj: Int, val newCount: Int)

    fun record(tick: Long, owner: String, reason: String, changes: List<SlotChange>)

    companion object {
        val NONE: ItemJournal = ItemJournal { _, _, _, _ -> }
    }
}
