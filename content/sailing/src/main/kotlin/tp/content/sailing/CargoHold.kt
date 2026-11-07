package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.InvView
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Which cargo hold facility a boat has and what it holds (docs/mechanics/sailing-port-tasks.md PT6). The shipyard (S4) does not
 * exist yet, so every owned boat carries the cheapest cargo hold its hotspots allow, on the first hotspot that allows one
 * (OPEN-PT5); S4 replaces this with the built facility.
 */
internal object HoldFacility {
    /** Loc shape of scenery that stands on a tile (`CENTREPIECE_STRAIGHT`, the shape the cache hold locs use). */
    private const val SHAPE_CENTREPIECE = 10

    private fun pick(data: SailingData, boat: Boat): Pair<HotspotSlot, BoatFacility>? {
        for (slot in boat.hotspots) {
            val hotspot = data.hotspots[slot.hotspotRow] ?: continue
            val hold =
                hotspot.facilityRows
                    .mapNotNull { row -> data.facilities.firstOrNull { it.row == row } }
                    .filter { it.name.contains("cargo hold", ignoreCase = true) }
                    .minByOrNull { it.requirements.construction }
            if (hold != null) return slot to hold
        }
        return null
    }

    /** The loc of the hold on the deck of [boat], or null if the boat has no hotspot for one. */
    fun placement(data: SailingData, boat: Boat): LocPlacement? {
        val (slot, hold) = pick(data, boat) ?: return null
        return hold.locs.firstOrNull()?.let { LocPlacement(it, slot.tile, slot.a, SHAPE_CENTREPIECE) }
    }

    /** Slots of the hold of [boat]: the `CARGOHOLD_SIZE` of its facility's stats row (db 164), 0 if it has none. */
    fun capacity(data: SailingData, boat: Boat): Int = pick(data, boat)?.second?.statsRow?.let { data.stats[it]?.cargoHoldSize } ?: 0
}

/**
 * The cargo hold of the player's own boat: the deck loc, interface 943 over the backpack side panel 944, and inventories 963-967
 * (one per boat slot). The hold accepts the objs and obj categories of db 196, never noted items; crates come out into the
 * hands only. Every move is one item transaction. The client shows `inv_size` of the inventory as the capacity, so the hold is
 * sent with exactly [Open.capacity] slots by [PlayerRef.showInv] instead of the cache's 240.
 */
internal class CargoHold(private val state: SailingState, private val tasks: PortTasks) {
    /** The hold a player has open. */
    private class Open(val boatSlot: Int, val capacity: Int)

    fun ContentScope.register() {
        for (loc in PortSites.HOLD_LOCS) {
            onOpLoc(loc, OP_OPEN) { if (Cargo.held(player) >= 0) depositHeld(player) else open(player) }
            onOpLoc(loc, OP_DEPOSIT_ALL) { depositAll(player, includeHeld = true) }
        }
        onButton(Components.Sailing_Boat_Cargohold.ITEMS) { click -> withdrawClick(this, click) }
        onButton(Components.Sailing_Boat_Cargohold.ITEMS_CONTAINER) { click -> withdrawClick(this, click) }
        onButton(Components.Sailing_Boat_Cargohold_Side.ITEMS) { click -> depositClick(this, click) }
        onButton(
            Components.Sailing_Boat_Cargohold.DEPOSITALL_CARGO,
        ) { depositMatching(this, includeHeld = true) { obj -> isCargo(this, obj) } }
        onButton(Components.Sailing_Boat_Cargohold.DEPOSITALL_SALVAGE) { depositMatching(this, includeHeld = false) { it in SALVAGE } }
        onButton(Components.Sailing_Boat_Cargohold.DEPOSITALL_INVENTORY) { depositAll(this, includeHeld = false) }
        onModalClosed(Interfaces.SAILING_BOAT_CARGOHOLD) {
            this[OPEN] = null
            restoreBackpack(this)
        }
    }

    // ---- opening

    private fun open(p: PlayerRef) {
        val boat = ownBoat(p) ?: return
        val capacity = HoldFacility.capacity(state.data, boat.boat)
        if (capacity <= 0) return p.message("Your boat has no cargo hold.")
        p[OPEN] = Open(boat.slot, capacity)
        p.setVarp(Varps.SAILING_BOAT_CARGOHOLD_INV, Cargo.holdInv(boat.slot))
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.SAILING_BOAT_CARGOHOLD)
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.SAILING_BOAT_CARGOHOLD_SIDE)
        val ops = (1..LAST_OP).fold(IfEvents.op(OP_EXAMINE)) { acc, op -> acc or IfEvents.op(op) }
        p.ifEvents(Components.Sailing_Boat_Cargohold.ITEMS, 0 until HOLD_CHILDREN, ops)
        p.ifEvents(Components.Sailing_Boat_Cargohold.ITEMS_CONTAINER, 0 until HOLD_CHILDREN, ops)
        p.ifEvents(Components.Sailing_Boat_Cargohold_Side.ITEMS, 0 until BACKPACK_SLOTS, ops)
        for (button in BUTTONS) p.ifEvents(button, -1..-1, IfEvents.op(1))
        send(p)
    }

    /** The boat [p] stands on if it is their own (courier tasks and holds are for the owner's boat only: Wiki Courier tasks). */
    private fun ownBoat(p: PlayerRef): ActiveBoat? {
        val boat = ActiveBoats.of(p)
        return if (boat != null && boat.slot >= 0 && boat.owner == p.username.lowercase()) {
            boat
        } else {
            p.message("You can only use the cargo hold of your own boat.")
            null
        }
    }

    /** Sends the hold to the client with its capacity as size, and the backpack slots the hold accepts as a bit set. */
    private fun send(p: PlayerRef) {
        val open = p[OPEN] ?: return
        val hold = p.inv(Cargo.holdInv(open.boatSlot))
        p.showInv(
            hold.invId,
            IntArray(open.capacity) { hold.obj(it) },
            IntArray(open.capacity) { if (hold.obj(it) < 0) 0 else hold.count(it) },
        )
        val pack = p.inv(Invs.INV)
        var accepted = 0
        for (slot in 0 until BACKPACK_SLOTS) if (accepts(p, pack.obj(slot))) accepted = accepted or (1 shl slot)
        p.setVarp(Varps.SAILING_BOAT_CARGOHOLD_SIDE_WHITELIST, accepted)
    }

    /** Gives the backpack its side slot back (what gameframe's `SideInventory.restore` does for the other main+side modals). */
    private fun restoreBackpack(p: PlayerRef) {
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.INVENTORY)
        val ops = intArrayOf(2, 3, 4, 6, 7, 10).fold(0L) { acc, op -> acc or IfEvents.op(op) }
        p.ifEvents(Components.Inventory.ITEMS, 0 until BACKPACK_SLOTS, ops or BACKPACK_TARGETS)
    }

    // ---- what the hold takes

    /** db 196: the listed objs and obj categories, never a noted item. */
    private fun accepts(p: PlayerRef, obj: Int): Boolean =
        obj >= 0 && p.unnoted(obj) == obj && (obj in state.data.cargoWhitelist || p.objCategory(obj) in state.data.cargoCategories)

    private fun isCargo(p: PlayerRef, obj: Int): Boolean = p.objCategory(obj).let { it == Cargo.CRATE_CATEGORY || it == BOUNTY_CATEGORY }

    private fun used(hold: InvView): Int = hold.size - hold.freeSlots

    // ---- depositing

    /** Deposit-held: the crate in the hands goes into the hold. */
    private fun depositHeld(p: PlayerRef) {
        val boat = ownBoat(p) ?: return
        val capacity = HoldFacility.capacity(state.data, boat.boat)
        val crate = Cargo.held(p)
        val hold = Cargo.holdInv(boat.slot)
        if (crate < 0) return
        if (used(p.inv(hold)) >= capacity) return p.message("Your cargo hold is full.")
        if (p.transaction("cargo hold deposit") { removeAt(Invs.WORN, Cargo.WEAPON_SLOT, crate, 1) && add(hold, crate, 1) }) {
            Cargo.refresh(p)
            send(p)
        }
    }

    /** Backpack item clicked in the side panel: op 1 default quantity, 2 = 1, 3 = 5, 4 = 10, 5 = X, 6 = all (like the bank). */
    private fun depositClick(p: PlayerRef, click: ButtonClick) {
        val open = p[OPEN] ?: return
        val obj = click.obj
        if (click.slot !in 0 until BACKPACK_SLOTS || p.inv(Invs.INV).obj(click.slot) != obj || !accepts(p, obj)) return
        val have = p.inv(Invs.INV).total(obj).toInt()
        when (val n = quantity(p, click.op, have)) {
            null -> p.countDialog("Enter amount:") { amount -> if (amount > 0) deposit(this, open, obj, amount) }
            else -> deposit(p, open, obj, n)
        }
    }

    /** Moves up to [requested] of [obj] from the backpack into the hold, as many as fit; returns how many moved. */
    private fun deposit(p: PlayerRef, open: Open, obj: Int, requested: Int): Int {
        if (p[OPEN] !== open || !accepts(p, obj)) return 0
        val hold = Cargo.holdInv(open.boatSlot)
        val holdView = p.inv(hold)
        val stacks = p.objStackable(obj)
        val room = open.capacity - used(holdView)
        val fits = if (stacks) (if (holdView.total(obj) > 0 || room > 0) Int.MAX_VALUE else 0) else room
        val n = minOf(requested.toLong(), p.inv(Invs.INV).total(obj), fits.toLong()).toInt()
        if (n <= 0) {
            p.message("Your cargo hold is full.")
            return 0
        }
        if (!p.transaction("cargo hold deposit") { remove(Invs.INV, obj, n) && add(hold, obj, n) }) return 0
        send(p)
        return n
    }

    /** Loc option "Deposit-all" and the interface button "deposit inventory": every acceptable backpack item (and the crate in hand). */
    private fun depositAll(p: PlayerRef, includeHeld: Boolean) {
        depositMatching(p, includeHeld) { true }
    }

    private fun depositMatching(p: PlayerRef, includeHeld: Boolean, wanted: (Int) -> Boolean) {
        val boat = ownBoat(p) ?: return
        if (includeHeld && Cargo.held(p) >= 0) depositHeld(p)
        val open = p[OPEN] ?: Open(boat.slot, HoldFacility.capacity(state.data, boat.boat))
        val objs = (0 until BACKPACK_SLOTS).map { p.inv(Invs.INV).obj(it) }.filter { it >= 0 && accepts(p, it) && wanted(it) }.distinct()
        var moved = 0
        for (obj in objs) moved += deposit(p, open, obj, Int.MAX_VALUE)
        if (moved == 0 && objs.isEmpty()) p.message("You have nothing to deposit.")
    }

    // ---- withdrawing

    private fun withdrawClick(p: PlayerRef, click: ButtonClick) {
        val open = p[OPEN] ?: return
        val holdInv = Cargo.holdInv(open.boatSlot)
        val hold = p.inv(holdInv)
        val obj = click.obj
        if (obj < 0 || hold.total(obj) <= 0 || click.op == OP_EXAMINE) return
        if (Cargo.isCrate(p, obj)) {
            if (click.op == OP_DEFAULT) withdrawCrate(p, open, obj)
            return
        }
        val have = hold.total(obj).toInt()
        when (val n = quantity(p, click.op, have)) {
            null -> p.countDialog("Enter amount:") { amount -> if (amount > 0) withdraw(this, open, obj, amount) }
            else -> withdraw(p, open, obj, n)
        }
    }

    private fun withdrawCrate(p: PlayerRef, open: Open, crate: Int) {
        if (Cargo.held(p) >= 0 || !Cargo.handsFree(p)) return p.message("You need both hands free to carry cargo.")
        val hold = Cargo.holdInv(open.boatSlot)
        if (p.transaction("cargo hold withdraw") { remove(hold, crate, 1) && setAt(Invs.WORN, Cargo.WEAPON_SLOT, crate, 1) }) {
            Cargo.refresh(p)
            send(p)
        }
    }

    /** Moves up to [requested] of [obj] from the hold to the backpack, as many as fit. */
    private fun withdraw(p: PlayerRef, open: Open, obj: Int, requested: Int) {
        if (p[OPEN] !== open) return
        val hold = Cargo.holdInv(open.boatSlot)
        val pack = p.inv(Invs.INV)
        val fits = if (p.objStackable(obj)) (if (pack.total(obj) > 0 || pack.freeSlots > 0) Int.MAX_VALUE else 0) else pack.freeSlots
        val n = minOf(requested.toLong(), p.inv(hold).total(obj), fits.toLong()).toInt()
        if (n <= 0) return p.message("You don't have enough inventory space.")
        if (p.transaction("cargo hold withdraw") { remove(hold, obj, n) && add(Invs.INV, obj, n) }) send(p)
    }

    /** Amount of a bank-style op on a stack of [have]: null means "ask for a number". */
    private fun quantity(p: PlayerRef, op: Int, have: Int): Int? =
        when (op) {
            OP_DEFAULT -> {
                when (p.varbit(Varbits.DEPOSITBOX_MODE)) {
                    MODE_FIVE -> FIVE
                    MODE_TEN -> TEN
                    MODE_X -> null
                    MODE_ALL -> have
                    else -> 1
                }
            }

            OP_ONE -> {
                1
            }

            OP_FIVE -> {
                FIVE
            }

            OP_TEN -> {
                TEN
            }

            OP_X -> {
                null
            }

            OP_ALL -> {
                have
            }

            else -> {
                0
            }
        }

    private companion object {
        val OPEN = AttrKey<Open>("sailing.hold")

        /** Hold loc options (cache locs 60581 / 60577): Open (or Deposit-held with cargo in hand), Deposit-all, Modify (S4). */
        const val OP_OPEN = 1
        const val OP_DEPOSIT_ALL = 2

        /** Quantity ops of the hold item grids (client scripts 8873 and 8896, the same layout as the bank's). */
        const val OP_DEFAULT = 1
        const val OP_ONE = 2
        const val OP_FIVE = 3
        const val OP_TEN = 4
        const val OP_X = 5
        const val OP_ALL = 6
        const val OP_EXAMINE = 10
        const val LAST_OP = 6
        const val FIVE = 5
        const val TEN = 10

        /** Values of varbit `DEPOSITBOX_MODE` (4430), the left-click quantity the scripts read: 0 one, 1 five, 2 ten, 3 X, 4 all. */
        const val MODE_FIVE = 1
        const val MODE_TEN = 2
        const val MODE_X = 3
        const val MODE_ALL = 4

        const val BACKPACK_SLOTS = 28

        /** Child indexes enabled on the hold grid: the cache inventory size (963-967 have 240 slots). */
        const val HOLD_CHILDREN = 240

        /** Obj category of bounty parts (cache objs 32809-32846, `Mogre head` and so on) and the db 196 entry for them. */
        const val BOUNTY_CATEGORY = 2295

        /** Shipwreck salvage objs, `salvage` and `rare salvage` (docs/plans/sailing.md 6.4). */
        val SALVAGE: IntRange = 32847..32873

        /** Client script `toplevel_mainmodal_open`: sets the main modal background (colour, transparency; -1 = default). */
        const val CS_MAINMODAL_BACKGROUND = 2524

        /** The backpack's item targets for "use" (gameframe `SideInventory.ITEM_EVENTS`: target flags + depth 1 + drag). */
        val BACKPACK_TARGETS: Long =
            IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
                IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET

        val BUTTONS: IntArray =
            intArrayOf(
                Components.Sailing_Boat_Cargohold.DEPOSITALL_CARGO,
                Components.Sailing_Boat_Cargohold.DEPOSITALL_SALVAGE,
                Components.Sailing_Boat_Cargohold.DEPOSITALL_INVENTORY,
            )
    }
}
