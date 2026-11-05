package tp.engine.inv

import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage

/** Stacking rule of an inventory (server-side property, not in the cache: RS Mod `InvStackType`). */
enum class InvStack {
    /** Stackable objs stack; others take one slot each (backpack, equipment). */
    NORMAL,

    /** Every obj stacks (bank). */
    ALWAYS,
}

/**
 * One inventory: parallel obj/count arrays (obj -1 = empty). Read access is public; **every mutation goes through
 * [ItemTransaction]** (brief §12), which journals and can roll back. Changed slots are sent once at the end of the tick.
 */
class Inventory internal constructor(val invId: Int, val size: Int, val stack: InvStack) {
    internal val objs = IntArray(size) { EMPTY }
    internal val counts = IntArray(size)
    private val dirty = BooleanArray(size)
    private var dirtyCount = 0
    private var fullRefresh = true

    /** Whether changes are sent to the owner's client (start-transmit; the client binds it to components by id). */
    var transmitted: Boolean = false
        internal set

    fun obj(slot: Int): Int = objs[slot]

    fun count(slot: Int): Int = counts[slot]

    fun isEmpty(slot: Int): Boolean = objs[slot] == EMPTY

    val freeSlots: Int get() = objs.count { it == EMPTY }

    fun total(obj: Int): Long {
        var n = 0L
        for (i in 0 until size) if (objs[i] == obj) n += counts[i]
        return n
    }

    fun slotOf(obj: Int): Int = objs.indexOf(obj)

    fun firstFree(): Int = objs.indexOf(EMPTY)

    internal fun write(slot: Int, obj: Int, count: Int) {
        objs[slot] = if (count <= 0) EMPTY else obj
        counts[slot] = if (count <= 0) 0 else count
        if (!dirty[slot]) {
            dirty[slot] = true
            dirtyCount++
        }
    }

    /** Next send is a full update (login, start of transmit). */
    internal fun refreshAll() {
        fullRefresh = true
    }

    internal fun flush(session: ClientSession) {
        if (!transmitted) return
        if (fullRefresh) {
            session.send(ServerMessage.Inventory(invId, objs.copyOf(), counts.copyOf(), null))
        } else if (dirtyCount > 0) {
            val slots = IntArray(dirtyCount)
            var n = 0
            for (i in 0 until size) if (dirty[i]) slots[n++] = i
            // Parallel arrays indexed by slot (the adapter reads objs[slot]); only [slots] are sent.
            session.send(ServerMessage.Inventory(invId, objs.copyOf(), counts.copyOf(), slots))
        }
        fullRefresh = false
        if (dirtyCount > 0) {
            dirty.fill(false)
            dirtyCount = 0
        }
    }

    companion object {
        const val EMPTY = -1
    }
}
