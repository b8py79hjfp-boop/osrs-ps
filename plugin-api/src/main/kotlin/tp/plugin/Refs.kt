package tp.plugin

import tp.engine.entity.Npc
import tp.engine.entity.Player
import tp.engine.inv.InvStack
import tp.engine.inv.Inventory
import tp.engine.inv.ItemTransaction
import tp.protocol.api.ServerMessage
import tp.util.CoordGrid

/** Content's view of a player. Every change goes through the engine (interfaces, vars, item transactions). */
public class PlayerRef internal constructor(internal val p: Player) {
    public val username: String get() = p.username
    public val coord: CoordGrid get() = p.coord
    public val tick: Long get() = p.world.tick
    public val worldId: Int get() = p.world.config.id

    /** Current processing position (0 = acts first). */
    public val pid: Int get() = p.world.pidOf(p.slot)
    public val windowMode: Int get() = p.windowMode
    public val isDelayed: Boolean get() = p.isDelayed

    public var runMode: Boolean
        get() = p.runMode
        set(value) = p.world.setRunMode(p, value)

    public var runEnergy: Int
        get() = p.runEnergy
        set(value) {
            p.runEnergy = value
        }

    public fun message(text: String) {
        p.session.send(ServerMessage.GameMessage(text))
    }

    public fun teleport(to: CoordGrid) {
        p.world.teleport(p, to)
    }

    // ---- interfaces

    public fun openTop(interfaceId: Int) {
        p.world.interfaces.openTop(p, interfaceId)
    }

    public fun openOverlay(destination: Int, interfaceId: Int) {
        p.world.interfaces.openOverlay(p, destination, interfaceId)
    }

    public fun openModal(destination: Int, interfaceId: Int) {
        p.world.interfaces.openModal(p, destination, interfaceId)
    }

    public fun closeModal() {
        p.closeModal()
    }

    public fun isOpen(interfaceId: Int): Boolean = p.world.interfaces.isOpen(p, interfaceId)

    /** Allowed ops/events on [component]'s slots [slots] (IF_SETEVENTS bit set). */
    public fun ifEvents(component: Int, slots: IntRange, events: Long) {
        p.session.send(ServerMessage.InterfaceEvents(component, slots.first, slots.last, events))
    }

    /** Runs client script [id] with Int/String [args]. */
    public fun clientScript(id: Int, vararg args: Any) {
        require(args.all { it is Int || it is String }) { "client script args must be Int or String" }
        p.session.send(ServerMessage.ClientScript(id, args.toList()))
    }

    /** Right-click option [slot] (1..8) on other players; null text removes it. */
    public fun playerOp(slot: Int, text: String?, top: Boolean = false) {
        p.session.send(ServerMessage.PlayerOption(slot, text, top))
    }

    public fun chatFilters(publicFilter: Int, tradeFilter: Int) {
        p.session.send(ServerMessage.ChatFilters(publicFilter, tradeFilter))
    }

    // ---- vars

    public fun varp(id: Int): Int = p.vars[id]

    public fun setVarp(id: Int, value: Int) {
        p.vars[id] = value
    }

    public fun varbit(id: Int): Int = p.vars.getBit(id)

    public fun setVarbit(id: Int, value: Int) {
        p.vars.setBit(id, value)
    }

    // ---- stats

    public fun level(stat: Int): Int = p.stats.level(stat)

    public fun baseLevel(stat: Int): Int = p.stats.baseLevel(stat)

    /** Adds [xp] (fractional xp allowed, stored in tenths); returns levels gained. */
    public fun addXp(stat: Int, xp: Double): Int = p.stats.addXp(stat, (xp * XP_TENTHS).toInt())

    // ---- inventories

    /** Inventory [invId]; [stackAll] = every obj stacks (bank). */
    public fun inv(invId: Int, stackAll: Boolean = false): InvView =
        InvView(p.world.inventory(p, invId, if (stackAll) InvStack.ALWAYS else InvStack.NORMAL))

    /** Starts sending inventory [invId] to the client (full update at the end of this tick). */
    public fun transmitInv(invId: Int) {
        p.world.transmitInventory(p.world.inventory(p, invId))
    }

    /** One atomic, journaled item transaction (brief §12): all steps happen or none. */
    public fun transaction(reason: String, block: ItemTx.() -> Boolean): Boolean =
        p.world.transaction(p, reason) { ItemTx(this, this@PlayerRef).block() }

    override fun equals(other: Any?): Boolean = other is PlayerRef && other.p === p

    override fun hashCode(): Int = p.hashCode()

    override fun toString(): String = p.toString()

    private companion object {
        const val XP_TENTHS = 10
    }
}

/** Read-only view of an inventory. Change it with [PlayerRef.transaction]. */
public class InvView internal constructor(internal val inv: Inventory) {
    public val invId: Int get() = inv.invId
    public val size: Int get() = inv.size
    public val freeSlots: Int get() = inv.freeSlots

    public fun obj(slot: Int): Int = inv.obj(slot)

    public fun count(slot: Int): Int = inv.count(slot)

    public fun total(obj: Int): Long = inv.total(obj)
}

/** Steps of an item transaction; each returns false (and dooms the transaction) on failure. */
public class ItemTx internal constructor(private val tx: ItemTransaction, private val player: PlayerRef) {
    private fun of(invId: Int) = player.p.world.inventory(player.p, invId)

    public fun add(invId: Int, obj: Int, count: Int = 1): Boolean = tx.add(of(invId), obj, count)

    public fun remove(invId: Int, obj: Int, count: Int = 1): Boolean = tx.remove(of(invId), obj, count)

    public fun removeAt(invId: Int, slot: Int, obj: Int, count: Int = 1): Boolean = tx.removeAt(of(invId), slot, obj, count)

    public fun swap(invId: Int, from: Int, to: Int): Boolean = tx.swap(of(invId), from, to)

    public fun move(fromInv: Int, slot: Int, obj: Int, count: Int, toInv: Int): Boolean = tx.move(of(fromInv), slot, obj, count, of(toInv))
}

public class NpcRef internal constructor(internal val n: Npc) {
    public val type: Int get() = n.type
    public val index: Int get() = n.index
    public val coord: CoordGrid get() = n.coord
}

/** A map loc an interaction targets. */
public class LocRef internal constructor(public val id: Int, public val coord: CoordGrid, public val shape: Int, public val angle: Int)
