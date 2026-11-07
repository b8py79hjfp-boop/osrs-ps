package tp.engine.inv

import tp.cache.config.TypeList
import tp.cache.config.obj.ObjType
import tp.engine.zone.GroundObj
import tp.engine.zone.GroundObjs
import tp.util.CoordGrid

/**
 * The single item-movement API (brief §12): every add, remove, move or swap of any inventory happens inside one
 * transaction. It is atomic (any failed step rolls back every step) and journaled (a committed transaction is
 * reported to the [ItemJournal] with its reason and each slot change). No other code writes inventory slots.
 */
class ItemTransaction internal constructor(
    private val objTypes: TypeList<ObjType>,
    private val ground: GroundObjs,
    private val tick: Long,
    private val timing: GroundTiming,
) {
    private class Change(val inv: Inventory, val slot: Int, val oldObj: Int, val oldCount: Int)

    private class GroundChange(val obj: GroundObj, val added: Boolean)

    private val changes = ArrayList<Change>()
    private val groundChanges = ArrayList<GroundChange>()

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

    private fun fits(inv: Inventory, obj: Int, count: Int): Boolean {
        if (!stacks(inv, obj)) return inv.freeSlots >= count
        val slot = inv.slotOf(obj)
        return if (slot >= 0) inv.counts[slot].toLong() + count <= Int.MAX_VALUE else inv.firstFree() >= 0
    }

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

    /** Replaces [obj] in [slot] with [count] of [newObj] in the same slot (potion doses, charges). */
    fun replaceAt(inv: Inventory, slot: Int, obj: Int, newObj: Int, count: Int = 1): Boolean {
        if (failed) return false
        val valid = slot in 0 until inv.size && inv.objs[slot] == obj
        if (!valid || objTypes[newObj] == null || count <= 0) return fail()
        write(inv, slot, newObj, count)
        return true
    }

    /** Puts [count] of [obj] into the empty [slot] (equipment slots). */
    fun setAt(inv: Inventory, slot: Int, obj: Int, count: Int): Boolean {
        if (failed) return false
        val valid = slot in 0 until inv.size && inv.objs[slot] == Inventory.EMPTY
        if (!valid || objTypes[obj] == null || count <= 0) return fail()
        write(inv, slot, obj, count)
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

    /**
     * Drops [count] of [obj] from [slot] onto [at], private to [owner] (ground-items.md G1-G3): public after the private
     * window if tradeable, despawning after the tradeable/untradeable lifetime.
     */
    fun dropToGround(inv: Inventory, slot: Int, obj: Int, count: Int, at: CoordGrid, owner: String): Boolean {
        if (!removeAt(inv, slot, obj, count)) return false
        val tradeable = objTypes[obj]?.tradeable == true
        val lifetime = if (tradeable) timing.tradeableDespawn else timing.untradeableDespawn
        val publicAt = if (tradeable) tick + timing.privateTicks else -1L
        val g = GroundObj(obj, count, at, owner, publicAt, tick + lifetime)
        ground.add(g)
        groundChanges += GroundChange(g, added = true)
        return true
    }

    /**
     * Adds [count] of [obj] to [inv], or, if it doesn't fit, creates it on the ground at [at] private to [owner] (same
     * ground rules as [dropToGround]). For items handed over by an outside service (GE collection) that must not be lost.
     */
    fun addOrDrop(inv: Inventory, obj: Int, count: Int, at: CoordGrid, owner: String): Boolean {
        if (failed) return false
        if (count <= 0 || objTypes[obj] == null) return fail()
        if (fits(inv, obj, count)) return add(inv, obj, count)
        val tradeable = objTypes[obj]?.tradeable == true
        val lifetime = if (tradeable) timing.tradeableDespawn else timing.untradeableDespawn
        val publicAt = if (tradeable) tick + timing.privateTicks else -1L
        val g = GroundObj(obj, count, at, owner, publicAt, tick + lifetime)
        ground.add(g)
        groundChanges += GroundChange(g, added = true)
        return true
    }

    /**
     * Creates [count] of [obj] on the ground at [at], private to [owner] first (loot: e.g. a lost lair fee). [lifetime]
     * overrides the drop lifetime in ticks (marks of grace: 1000); -1 = the tradeable / untradeable default.
     */
    fun createOnGround(obj: Int, count: Int, at: CoordGrid, owner: String, lifetime: Int = -1): Boolean {
        if (failed) return false
        if (count <= 0 || objTypes[obj] == null) return fail()
        val tradeable = objTypes[obj]?.tradeable == true
        val default = if (tradeable) timing.tradeableDespawn else timing.untradeableDespawn
        val lifetime = if (lifetime > 0) lifetime else default
        val publicAt = if (tradeable) tick + timing.privateTicks else -1L
        val g = GroundObj(obj, count, at, owner, publicAt, tick + lifetime)
        ground.add(g)
        groundChanges += GroundChange(g, added = true)
        return true
    }

    /** Takes [obj] off the ground into [inv] (stacking rules of [inv]). */
    fun takeFromGround(obj: GroundObj, inv: Inventory): Boolean {
        if (failed) return false
        if (obj.removed) return fail()
        if (!add(inv, obj.id, obj.count)) return false
        ground.remove(obj)
        groundChanges += GroundChange(obj, added = false)
        return true
    }

    internal fun rollback() {
        for (i in groundChanges.indices.reversed()) {
            val g = groundChanges[i]
            if (g.added) ground.remove(g.obj) else ground.restore(g.obj)
        }
        groundChanges.clear()
        for (i in changes.indices.reversed()) {
            val c = changes[i]
            c.inv.write(c.slot, c.oldObj, c.oldCount)
        }
        changes.clear()
    }

    internal fun entries(): List<ItemJournal.SlotChange> =
        changes.map {
            ItemJournal.SlotChange(
                it.inv.invId,
                it.slot,
                it.oldObj,
                it.oldCount,
                it.inv.objs[it.slot],
                it.inv.counts[it.slot],
            )
        } +
            groundChanges.map {
                val o = it.obj
                if (it.added) {
                    ItemJournal.SlotChange(ItemJournal.GROUND, o.coord.packed, -1, 0, o.id, o.count)
                } else {
                    ItemJournal.SlotChange(ItemJournal.GROUND, o.coord.packed, o.id, o.count, -1, 0)
                }
            }

    internal val isEmpty: Boolean get() = changes.isEmpty() && groundChanges.isEmpty()
}

/** Where committed item transactions are recorded (audit trail; persistence in Phase 5). Must not block. */
fun interface ItemJournal {
    class SlotChange(val invId: Int, val slot: Int, val oldObj: Int, val oldCount: Int, val newObj: Int, val newCount: Int)

    fun record(tick: Long, owner: String, reason: String, changes: List<SlotChange>)

    companion object {
        /** [SlotChange.invId] of ground changes; their slot is the packed coordinate. */
        const val GROUND = -1

        val NONE: ItemJournal = ItemJournal { _, _, _, _ -> }
    }
}

/** Player-drop lifetimes in ticks (docs/mechanics/ground-items.md, OSRS Wiki "Drop"). */
data class GroundTiming(
    /** Tradeable drops appear to other players after 60 s = 100 ticks (G1). */
    val privateTicks: Int = 100,
    /** ...and disappear 120 s = 200 ticks after that: 300 ticks after the drop (G2). */
    val tradeableDespawn: Int = 300,
    /** Untradeable drops: never public, gone after 180 s = 300 ticks (G3). */
    val untradeableDespawn: Int = 300,
)
