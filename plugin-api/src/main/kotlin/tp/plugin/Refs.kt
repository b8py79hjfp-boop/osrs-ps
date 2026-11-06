package tp.plugin

import tp.engine.World
import tp.engine.entity.Entity
import tp.engine.entity.Player
import tp.engine.interact.clearInteraction
import tp.engine.inv.InvStack
import tp.engine.inv.Inventory
import tp.engine.inv.ItemTransaction
import tp.engine.tick.TickInspector
import tp.engine.zone.GroundObj
import tp.protocol.api.ServerMessage
import tp.util.CoordGrid

/** Content's view of a player. Every change goes through the engine (interfaces, vars, item transactions). */
public class PlayerRef internal constructor(internal val p: Player) : EntityRef() {
    override val e: Entity get() = p
    public val username: String get() = p.username
    public val slot: Int get() = p.slot
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

    /** Hard interruption: cancels the interaction and route, closes the modal, clears weak scripts (interactions.md I7). */
    public fun interrupt() {
        p.world.interrupt(p)
    }

    /**
     * Starts an interaction with [target] using op [op] (e.g. auto-retaliate's attack), exactly as if the player had
     * clicked it (interrupts the current one).
     */
    public fun interact(target: EntityRef, op: Int) {
        val t =
            when (target) {
                is PlayerRef -> {
                    tp.engine.interact.InteractionTarget
                        .OfPlayer(target.p)
                }

                is NpcRef -> {
                    tp.engine.interact.InteractionTarget
                        .OfNpc(target.n)
                }
            }
        p.world.interact(p, t, op)
    }

    /** Ends the current interaction (no other interruption effects). */
    public fun stopInteraction() {
        p.clearInteraction()
    }

    /** Whether the player has a route to walk. */
    public val isMoving: Boolean get() = p.isRouting

    /** Whether the player has an interaction in progress. */
    public val isInteracting: Boolean get() = p.interactionTarget != null

    /** Starts (or replaces) timer [id] every [interval] ticks; soft timers run even while delayed (scripts.md T3/T4). */
    public fun timer(id: TimerId, interval: Int, soft: Boolean, action: PlayerRef.() -> Unit) {
        p.timers.add(id.key, interval, soft) { action() }
    }

    public fun stopTimer(id: TimerId) {
        p.timers.remove(id.key)
    }

    public fun hasTimer(id: TimerId): Boolean = p.timers.has(id.key)

    /** Toggles streaming this player's tick trace to their chatbox (`::trace`); false if no inspector is installed. */
    public fun toggleTrace(): Boolean = (p.world.tracer as? TickInspector)?.toggle(p) ?: false

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

    /** Sets the current (boosted/drained) level of [stat]. */
    public fun setLevel(stat: Int, level: Int) {
        p.stats.setLevel(stat, level)
    }

    public fun level(stat: Int): Int = p.stats.level(stat)

    public fun baseLevel(stat: Int): Int = p.stats.baseLevel(stat)

    /** Adds [xp] (fractional xp allowed, stored in tenths); returns levels gained. */
    public fun addXp(stat: Int, xp: Double): Int = p.stats.addXp(stat, (xp * XP_TENTHS).toInt())

    // ---- appearance and objs

    /** Combat level from base levels (OSRS Wiki "Combat level"). */
    public val combatLevel: Int
        get() {
            val st = p.stats
            val base = (st.baseLevel(DEF) + st.baseLevel(HP) + st.baseLevel(PRAY) / 2) / 4.0
            val melee = MULT * (st.baseLevel(ATT) + st.baseLevel(STR))
            val range = MULT * (st.baseLevel(RNG) * 3 / 2)
            val mage = MULT * (st.baseLevel(MAG) * 3 / 2)
            return kotlin.math.floor(base + maxOf(melee, range, mage)).toInt()
        }

    /** Sends this player's look: worn obj per wear position 0..11 (-1 empty). */
    public fun appearance(worn: IntArray, combatLevel: Int, skullIcon: Int = -1, prayerIcon: Int = -1) {
        p.world.refreshAppearance(p, worn, combatLevel, skullIcon, prayerIcon)
    }

    /** Obj config param [param] of [obj] (int), or [default]. */
    public fun objParam(obj: Int, param: Int, default: Int): Int =
        p.world
            .objType(obj)
            ?.params
            ?.int(param) ?: default

    /** Primary wear position of [obj] (-1 = not wearable). */
    public fun objWearPos(obj: Int): Int = p.world.objType(obj)?.wearPos1 ?: -1

    /** Secondary wear position of [obj] (two-handed weapons occupy the shield position: 5). */
    public fun objWearPos2(obj: Int): Int = p.world.objType(obj)?.wearPos2 ?: -1

    /** Obj category (cache `category`), -1 if none. */
    public fun objCategory(obj: Int): Int = p.world.objType(obj)?.category ?: -1

    public fun objName(obj: Int): String = p.world.objType(obj)?.name ?: "null"

    public fun objTradeable(obj: Int): Boolean = p.world.objType(obj)?.tradeable ?: false

    /** Value used for items kept on death (cache `cost`). */
    public fun objValue(obj: Int): Int = p.world.objType(obj)?.cost ?: 0

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
        const val ATT = 0
        const val DEF = 1
        const val STR = 2
        const val HP = 3
        const val RNG = 4
        const val PRAY = 5
        const val MAG = 6

        /** 13/40 (Combat level formula, CL:37-57). */
        const val MULT = 0.325
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

    public fun replaceAt(invId: Int, slot: Int, obj: Int, newObj: Int, count: Int = 1): Boolean =
        tx.replaceAt(of(invId), slot, obj, newObj, count)

    public fun setAt(invId: Int, slot: Int, obj: Int, count: Int = 1): Boolean = tx.setAt(of(invId), slot, obj, count)

    /** Drops [count] of [obj] from [slot] of [invId] under the player, private to them at first. */
    public fun drop(invId: Int, slot: Int, obj: Int, count: Int): Boolean =
        tx.dropToGround(of(invId), slot, obj, count, player.coord, player.username)

    /** Picks [obj] up into [invId]. */
    public fun take(obj: ObjRef, invId: Int): Boolean = tx.takeFromGround(obj.g, of(invId))
}

/** A ground obj the player can see. */
public class ObjRef internal constructor(internal val g: GroundObj) {
    public val id: Int get() = g.id
    public val count: Int get() = g.count
    public val coord: CoordGrid get() = g.coord
}

/** World-level operations for content (spawns, cache data). */
public class WorldRef internal constructor(internal val w: World) {
    public val tick: Long get() = w.tick

    /** Int values of cache enum [id] (key → value); empty if absent. */
    public fun enumInts(id: Int): Map<Int, Int> =
        w.data.configs.enums[id]
            ?.intValues
            .orEmpty()

    public fun objParamInt(obj: Int, param: Int): Int? = w.objType(obj)?.params?.int(param)

    public fun objParamString(obj: Int, param: Int): String? = w.objType(obj)?.params?.string(param)

    /** Spawns NPC [type] at [at], wandering up to [wanderRange] tiles from it (random-walk.md). */
    public fun spawnNpc(type: Int, at: CoordGrid, wanderRange: Int) {
        w.spawnNpc(type, at, wanderRange)
    }
}

/** A map loc an interaction targets. */
public class LocRef internal constructor(public val id: Int, public val coord: CoordGrid, public val shape: Int, public val angle: Int)

/** Identifies a player timer. Define as a top-level `val`. */
public class TimerId(name: String) {
    internal val key: tp.engine.script.TimerKey = tp.engine.script.TimerKey(name)
}
