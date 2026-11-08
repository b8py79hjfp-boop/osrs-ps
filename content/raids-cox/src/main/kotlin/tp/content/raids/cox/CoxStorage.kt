package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ButtonDrag
import tp.plugin.EntityRef
import tp.plugin.IfEvents
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The storage unit tiers (cox.md CX37-CX38): the tiny unit every hotspot starts as (25 private slots), then the units
 * built or upgraded with mallignum root planks, a hammer and Construction levels. [sharedSlots] 0 = no shared storage.
 */
internal enum class CoxTier(val loc: Int, val level: Int, val sharedSlots: Int, val privateSlots: Int, val label: String) {
    TINY(Locs.RAIDS_STORAGE_HOTSPOT, 0, 0, 25, "tiny"),
    SMALL(Locs.RAIDS_STORAGE_1, 30, 250, 30, "small"),
    MEDIUM(Locs.RAIDS_STORAGE_2, 60, 500, 60, "medium"),
    LARGE(Locs.RAIDS_STORAGE_3, 90, 1000, 90, "large"),
    MASSIVE(Locs.RAIDS_STORAGE_4, 99, 1500, 120, "massive"),
}

/** A run's storage: the tier of its units and the shared party storage (obj -> count; the party loses it with the run, CX39). */
internal class CoxStorageState {
    var tier: CoxTier = CoxTier.TINY
    val shared = LinkedHashMap<Int, Int>()
}

/**
 * Storage units, the shared storage 550 and the private storage 271 with the side panel 551 (cox.md CX37-CX41;
 * client-contract 2.7). The shared storage is the party's state in the run (sent as a mirrored inventory, `invother`, with
 * one slot per unstackable unit); the private storage is the player's own inventory 583. Every deposit and withdrawal is
 * one item transaction; the shared map changes in its commit hook.
 */
internal object CoxStorage {
    private val STATES = AttrKey<HashMap<Long, CoxStorageState>>("cox_storage")
    private val MODE = AttrKey<Int>("cox_storage_mode")

    fun state(any: EntityRef, run: RaidRun): CoxStorageState {
        val map = any.worldAttr(STATES) ?: HashMap<Long, CoxStorageState>().also { any.setWorldAttr(STATES, it) }
        return map.getOrPut(run.id.value) { CoxStorageState() }
    }

    /** Drops the state of runs that no longer exist (called now and then, not every tick). */
    fun prune(world: WorldRef) {
        val map = world.worldAttr(STATES) ?: return
        if (map.isEmpty()) return
        val live =
            Raids
                .of(world)
                .activeRuns
                .map { it.id.value }
                .toSet()
        map.keys.removeIf { it !in live }
    }

    // ---- opening

    /** A hotspot's "Private" op (inside the Chambers only). */
    fun usePrivate(p: PlayerRef) {
        val run = CoxFlow.runOf(p) ?: return
        openPrivate(p, run)
    }

    /** A built unit's "Shared" op (inside the Chambers only). */
    fun useShared(p: PlayerRef) {
        val run = CoxFlow.runOf(p) ?: return
        openShared(p, run)
    }

    /** The unit's "Private" op, inside the Chambers or at the tent (loc 30107) when [run] is null. */
    fun openPrivate(p: PlayerRef, run: RaidRun?) {
        if (isUltimate(p) && !CoxUiConfig.uimPrivateStorage) {
            p.message(UIM_NO_STORAGE)
            return
        }
        p.setVarbit(Varbits.RAIDS_USINGPRIVATESTORAGE, 1)
        openPair(p, Interfaces.RAIDS_STORAGE_PRIVATE)
        p[MODE] = MODE_PRIVATE
        val ops = ITEM_OPS or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET
        p.ifEvents(Components.Raids_Storage_Private.ITEMS, 0 until privateSize(p), ops)
        for (button in PRIVATE_BUTTONS) p.ifEvents(button, NO_SLOT..NO_SLOT, IfEvents.op(1))
        sendPrivate(p, run)
    }

    /** The unit's "Shared" op (small units and above). */
    fun openShared(p: PlayerRef, run: RaidRun) {
        val st = state(p, run)
        if (st.tier.sharedSlots <= 0) {
            p.message(NO_SHARED)
            return
        }
        p.setVarbit(Varbits.RAIDS_USINGPRIVATESTORAGE, 0)
        setIfChanged(p, Varbits.RAIDS_CLIENT_IRONSHARING_PERMITTED, if (mayRetrieve(p, run)) 1 else 0)
        openPair(p, Interfaces.RAIDS_STORAGE_SHARED)
        p[MODE] = MODE_SHARED
        p.ifEvents(Components.Raids_Storage_Shared.ITEMS, 0 until p.enumInts(ENUM_SHARED_OBJS).size, ITEM_OPS)
        p.ifEvents(Components.Raids_Storage_Shared.PRIVATE, NO_SLOT..NO_SLOT, IfEvents.op(1))
        sendShared(p, run)
    }

    /** The main modal and the side panel over the backpack (RS Mod `ifOpenMainSidePair`; gameframe `SideInventory.openPair`). */
    private fun openPair(p: PlayerRef, main: Int) {
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, main)
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.RAIDS_STORAGE_SIDE)
        p.ifEvents(Components.Raids_Storage_Side.ITEMS, 0 until BACKPACK_SLOTS, ITEM_OPS)
        p.ifEvents(Components.Raids_Storage_Side.DISMISS, NO_SLOT..NO_SLOT, IfEvents.op(1))
    }

    /** Either storage closed: the backpack gets its side slot back. */
    fun closed(p: PlayerRef) {
        p[MODE] = null
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.INVENTORY)
        p.ifEvents(Components.Inventory.ITEMS, 0 until BACKPACK_SLOTS, BACKPACK_EVENTS)
    }

    /** IF_BUTTON1 on 551:8 "Dismiss": the shared-storage warning stays away (varbit 5509, persistent). */
    fun dismissWarning(p: PlayerRef) {
        p.setVarbit(Varbits.RAIDS_SHAREDSTORAGE_WARNING_DISMISSED, 1)
    }

    /** The "Shared" / "Private" switch buttons (271:5, 550:5). */
    fun switchToShared(p: PlayerRef) {
        val run = CoxFlow.runOf(p)
        if (run == null) p.message(SHARED_IN_RAID) else openShared(p, run)
    }

    fun switchToPrivate(p: PlayerRef) {
        openPrivate(p, CoxFlow.runOf(p))
    }

    // ---- sending

    private fun privateSize(p: PlayerRef): Int = viewSize(p, CoxFlow.runOf(p))

    /** The slots shown: the unit's capacity, or more when items from a bigger unit sit beyond it (they stay reachable). */
    private fun viewSize(p: PlayerRef, run: RaidRun?): Int {
        val inv = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        var last = -1
        for (slot in 0 until inv.size) if (inv.obj(slot) >= 0) last = slot
        return maxOf(capacity(p, run), last + 1).coerceAtMost(inv.size)
    }

    /** CX38: the private slots of the run's tier; outside, the whole inventory 583 (client-contract 1.4: 120 slots). */
    private fun capacity(p: PlayerRef, run: RaidRun?): Int =
        if (run == null) p.inv(Invs.RAIDS_PRIVATESTORAGE).size else state(p, run).tier.privateSlots

    fun sendPrivate(p: PlayerRef, run: RaidRun?) {
        val inv = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        val size = viewSize(p, run)
        p.showInv(
            Invs.RAIDS_PRIVATESTORAGE,
            IntArray(size) { inv.obj(it) },
            IntArray(size) { if (inv.obj(it) < 0) 0 else inv.count(it) },
        )
    }

    /** The shared storage as a mirrored inventory of the tier's size: a stack in one slot, every other unit in its own. */
    fun sendShared(p: PlayerRef, run: RaidRun) {
        val st = state(p, run)
        val size = st.tier.sharedSlots
        val objs = IntArray(size) { EMPTY }
        val counts = IntArray(size)
        var slot = 0
        for ((obj, n) in st.shared) {
            if (p.objStackable(obj)) {
                if (slot < size) {
                    objs[slot] = obj
                    counts[slot++] = n
                }
            } else {
                repeat(n) {
                    if (slot < size) {
                        objs[slot] = obj
                        counts[slot++] = 1
                    }
                }
            }
        }
        p.showInv(Invs.RAIDS_SHAREDSTORAGE, objs, counts, other = true)
    }

    /** Every party member with the shared storage open sees the change. */
    private fun refreshShared(any: EntityRef, run: RaidRun) {
        for (viewer in run.presentPlayers(any)) {
            if (viewer.isOpen(Interfaces.RAIDS_STORAGE_SHARED)) sendShared(viewer, run)
        }
    }

    // ---- item clicks

    /** A click on a stored item (271:6 or 550:7): withdraw by the op's amount. Hostile input: slot and obj are checked. */
    fun withdrawClick(p: PlayerRef, click: ButtonClick, shared: Boolean) {
        if (click.op == OP_EXAMINE || click.obj < 0) return
        val mode = p[MODE]
        if (mode != if (shared) MODE_SHARED else MODE_PRIVATE) return
        val run = CoxFlow.runOf(p)
        val total =
            if (shared) {
                run?.let { state(p, it).shared[click.obj] } ?: 0
            } else {
                p.inv(Invs.RAIDS_PRIVATESTORAGE).total(click.obj).toInt()
            }
        if (total <= 0) return
        val amount = amountOf(click.op, total)
        if (amount == null) {
            val obj = click.obj
            p.countDialog(AMOUNT_PROMPT) { n -> if (n > 0 && this[MODE] == mode) withdraw(this, shared, obj, n) }
        } else {
            withdraw(p, shared, click.obj, amount)
        }
    }

    private fun withdraw(p: PlayerRef, shared: Boolean, obj: Int, requested: Int) {
        if (shared) {
            val run = CoxFlow.runOf(p) ?: return
            withdrawShared(p, run, obj, requested)
        } else {
            withdrawPrivate(p, obj, requested)
        }
    }

    /** A click on a backpack item in the side panel (551:1): donate or store by the op's amount. */
    fun depositClick(p: PlayerRef, click: ButtonClick) {
        if (click.op == OP_EXAMINE) return
        val mode = p[MODE] ?: return
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until BACKPACK_SLOTS || click.obj < 0 || inv.obj(click.slot) != click.obj) return
        val total = inv.total(click.obj).toInt()
        val amount = amountOf(click.op, total)
        if (amount == null) {
            val obj = click.obj
            p.countDialog(AMOUNT_PROMPT) { n -> if (n > 0 && this[MODE] == mode) deposit(this, mode, obj, n) }
        } else {
            deposit(p, mode, click.obj, amount)
        }
    }

    private fun deposit(p: PlayerRef, mode: Int, obj: Int, requested: Int) {
        if (mode == MODE_SHARED) {
            val run = CoxFlow.runOf(p) ?: return
            depositShared(p, run, obj, requested)
        } else {
            depositPrivate(p, obj, requested)
        }
    }

    /** Ops 1..5 of the item lists: Withdraw / Store (1), -5, -10, -All, -X (null = ask); client-contract 2.7. */
    private fun amountOf(op: Int, total: Int): Int? =
        when (op) {
            OP_ONE -> 1
            OP_FIVE -> FIVE
            OP_TEN -> TEN
            OP_ALL -> total
            else -> null
        }

    // ---- private storage (the player's inventory 583)

    private fun depositPrivate(p: PlayerRef, obj: Int, requested: Int) {
        val run = CoxFlow.runOf(p)
        if (!acceptsPrivate(p, obj)) {
            p.message(CANNOT_STORE)
            return
        }
        val store = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        val free = capacity(p, run) - (store.size - store.freeSlots)
        val have = p.inv(Invs.INV).total(obj).toInt()
        var n = minOf(requested, have)
        if (p.objStackable(obj)) {
            if (store.total(obj) == 0L && free <= 0) n = 0
        } else {
            n = minOf(n, free.coerceAtLeast(0))
        }
        if (n <= 0) {
            p.message(STORAGE_FULL)
            return
        }
        val amount = n
        p.transaction("cox private storage deposit") {
            var left = amount
            val inv = p.inv(Invs.INV)
            for (slot in 0 until BACKPACK_SLOTS) {
                if (left == 0) break
                if (inv.obj(slot) != obj) continue
                val take = minOf(left, inv.count(slot))
                if (!move(Invs.INV, slot, obj, take, Invs.RAIDS_PRIVATESTORAGE)) return@transaction false
                left -= take
            }
            left == 0
        }
        sendPrivate(p, run)
    }

    private fun withdrawPrivate(p: PlayerRef, obj: Int, requested: Int) {
        val store = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        val backpack = p.inv(Invs.INV)
        var n = minOf(requested.toLong(), store.total(obj)).toInt()
        n =
            if (p.objStackable(obj)) {
                if (backpack.total(obj) == 0L && backpack.freeSlots == 0) 0 else n
            } else {
                minOf(n, backpack.freeSlots)
            }
        if (n <= 0) {
            p.message(NO_INV_SPACE)
            return
        }
        val amount = n
        p.transaction("cox private storage withdraw") {
            var left = amount
            for (slot in 0 until store.size) {
                if (left == 0) break
                if (store.obj(slot) != obj) continue
                val take = minOf(left, store.count(slot))
                if (!move(Invs.RAIDS_PRIVATESTORAGE, slot, obj, take, Invs.INV)) return@transaction false
                left -= take
            }
            left == 0
        }
        sendPrivate(p, CoxFlow.runOf(p))
    }

    /** CX39: "most equippable or tradeable items" and the raid's own items; kindling never (12 Dec 2023). */
    private fun acceptsPrivate(p: PlayerRef, obj: Int): Boolean =
        obj != Objs.RAIDS_WOOD && (obj in raidObjs(p) || p.objTradeable(obj) || p.objWearPos(obj) >= 0)

    /** 271:9 "Withdraw all": every stack back into the backpack, as far as it fits. */
    fun withdrawAll(p: PlayerRef) {
        val store = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        val objs = (0 until store.size).map { store.obj(it) }.filter { it >= 0 }.distinct()
        for (obj in objs) withdrawPrivate(p, obj, Int.MAX_VALUE)
    }

    /** 271:10: "Deposit all" inside the Chambers; outside it is "Bank all" (client-contract 2.7, varbit 5432). */
    fun depositOrBankAll(p: PlayerRef) {
        if (CoxFlow.runOf(p) == null) bankAll(p) else depositAll(p)
    }

    private fun depositAll(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val objs = (0 until BACKPACK_SLOTS).map { inv.obj(it) }.filter { it >= 0 && acceptsPrivate(p, it) }.distinct()
        for (obj in objs) depositPrivate(p, obj, Int.MAX_VALUE)
    }

    /** "Bank all" outside: every stored stack into the bank (the same `add` the bank uses; notes as the item they stand for). */
    private fun bankAll(p: PlayerRef) {
        val store = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        for (slot in 0 until store.size) {
            val obj = store.obj(slot)
            if (obj < 0) continue
            val count = store.count(slot)
            val real = p.unnoted(obj)
            if (!p.transaction("cox private storage bank") {
                    removeAt(Invs.RAIDS_PRIVATESTORAGE, slot, obj, count) &&
                        add(Invs.BANK, real, count)
                }
            ) {
                p.message(NO_BANK_SPACE)
                break
            }
        }
        sendPrivate(p, null)
    }

    /** A drag inside 271:6 swaps two slots (script 1609 / 1610). */
    fun drag(p: PlayerRef, d: ButtonDrag) {
        if (p[MODE] != MODE_PRIVATE || d.toComponent != Components.Raids_Storage_Private.ITEMS) return
        val size = viewSize(p, CoxFlow.runOf(p))
        if (d.fromSlot !in 0 until size || d.toSlot !in 0 until size || d.fromSlot == d.toSlot) return
        p.transaction("cox private storage reorder") { swap(Invs.RAIDS_PRIVATESTORAGE, d.fromSlot, d.toSlot) }
        sendPrivate(p, CoxFlow.runOf(p))
    }

    // ---- shared storage (the party's map)

    private fun depositShared(p: PlayerRef, run: RaidRun, obj: Int, requested: Int) {
        val st = state(p, run)
        if (st.tier.sharedSlots <= 0) return
        if (isUltimate(p) && !CoxUiConfig.uimSharedStorageDepositOnly) {
            p.message(UIM_NO_SHARED)
            return
        }
        if (obj == Objs.RAIDS_WOOD || obj !in raidObjs(p)) {
            p.message(CANNOT_DONATE)
            return
        }
        val stackable = p.objStackable(obj)
        val free = st.tier.sharedSlots - usedSlots(p, st)
        var n = minOf(requested, p.inv(Invs.INV).total(obj).toInt())
        if (stackable) {
            if (!st.shared.containsKey(obj) && free <= 0) n = 0
            n = minOf(n.toLong(), Int.MAX_VALUE.toLong() - (st.shared[obj] ?: 0)).toInt()
        } else {
            n = minOf(n, free.coerceAtLeast(0))
        }
        if (n <= 0) {
            p.message(STORAGE_FULL)
            return
        }
        val amount = n
        p.transaction("cox shared storage deposit") {
            onCommit { st.shared.merge(obj, amount, Int::plus) }
            remove(Invs.INV, obj, amount)
        }
        refreshShared(p, run)
    }

    private fun withdrawShared(p: PlayerRef, run: RaidRun, obj: Int, requested: Int) {
        if (!mayRetrieve(p, run)) {
            p.message(CANNOT_RETRIEVE)
            return
        }
        val st = state(p, run)
        val stored = st.shared[obj] ?: return
        val backpack = p.inv(Invs.INV)
        var n = minOf(requested, stored)
        n =
            if (p.objStackable(obj)) {
                if (backpack.total(obj) == 0L && backpack.freeSlots == 0) 0 else n
            } else {
                minOf(n, backpack.freeSlots)
            }
        if (n <= 0) {
            p.message(NO_INV_SPACE)
            return
        }
        val amount = n
        p.transaction("cox shared storage withdraw") {
            onCommit {
                val left = stored - amount
                if (left > 0) st.shared[obj] = left else st.shared.remove(obj)
            }
            add(Invs.INV, obj, amount)
        }
        refreshShared(p, run)
    }

    /** Slots the stored items use: one per stack, one per unstackable unit (inventory semantics). */
    private fun usedSlots(p: PlayerRef, st: CoxStorageState): Int {
        var used = 0
        for ((obj, n) in st.shared) used += if (p.objStackable(obj)) 1 else n
        return used
    }

    /** Client-contract 2.7 / CX39: the objs the shared storage takes (enum 1666, the client's own list). */
    private fun raidObjs(p: PlayerRef): Collection<Int> = p.enumInts(ENUM_SHARED_OBJS).values

    // ---- account rules (CX39; client-contract 2.7)

    private fun ironman(p: PlayerRef): Int = p.varbit(Varbits.IRONMAN)

    private fun isUltimate(p: PlayerRef): Boolean = ironman(p) == IRONMAN_ULTIMATE

    /**
     * Client-contract 2.7 (script 1602 / 1624): an Ironman who donates "cannot retrieve" unless the raid is a solo one
     * (varbit 13069 = 1); an Ultimate Ironman never retrieves. Everyone else takes freely.
     */
    private fun mayRetrieve(p: PlayerRef, run: RaidRun): Boolean =
        when {
            isUltimate(p) -> !CoxUiConfig.uimSharedStorageDepositOnly && run.scaling.partySize == 1
            ironman(p) != 0 -> run.scaling.partySize == 1
            else -> true
        }

    // ---- storage units (hotspots)

    /** The hotspot's "Build" / "Upgrade": pick the tier (when several are above the current one), then [attempt] it. */
    fun build(p: PlayerRef) {
        val run = CoxFlow.runOf(p) ?: return
        val st = state(p, run)
        val options = CoxTier.entries.filter { it > st.tier }
        when {
            options.isEmpty() -> {
                p.message(FULLY_BUILT)
            }

            options.size == 1 -> {
                attempt(p, run, options.first())
            }

            else -> {
                val labels = options.map { "${it.label.replaceFirstChar(Char::uppercase)} storage unit (Construction ${it.level})" }
                p.choiceDialog(BUILD_PROMPT, labels.take(MAX_CHOICES)) { pick ->
                    options.getOrNull(pick - 1)?.let { attempt(this, run, it) }
                }
            }
        }
    }

    /**
     * CX37 / CX38: builds the units up to [target] for the planks per tier (cumulative, OPEN-CX20) with a hammer and the
     * Construction level; 150 XP and 100 points per tier crossed (CX24, via [CoxPoints.award]); every hotspot of the run
     * changes at once. A tier skipped counts as built (OPEN-CXUI4: the Main page's "instantly" large unit).
     */
    private fun attempt(p: PlayerRef, run: RaidRun, target: CoxTier) {
        val st = state(p, run)
        if (target <= st.tier || CoxFlow.runOf(p)?.id != run.id) return
        val tiers = target.ordinal - st.tier.ordinal
        val planks = tiers * CoxUiConfig.storagePlanksPerTier
        when {
            p.level(Stats.CONSTRUCTION) < target.level -> {
                p.message("You need a Construction level of ${target.level} to build that.")
            }

            !hasHammer(p) -> {
                p.message(NEED_HAMMER)
            }

            p.inv(Invs.INV).total(Objs.RAIDS_PLANK) < planks -> {
                p.message("You need $planks mallignum root planks to build that.")
            }

            else -> {
                val from = st.tier
                val ok =
                    p.transaction("cox storage build") {
                        onCommit { built(p, run, st, from, target, tiers) }
                        remove(Invs.INV, Objs.RAIDS_PLANK, planks)
                    }
                if (!ok) p.message(NEED_PLANKS_SPACE)
            }
        }
    }

    private fun built(p: PlayerRef, run: RaidRun, st: CoxStorageState, from: CoxTier, to: CoxTier, tiers: Int) {
        st.tier = to
        swapLocs(p, run, from, to)
        p.addXp(Stats.CONSTRUCTION, XP_PER_TIER * tiers)
        CoxPoints.award(run, p.username, POINTS_PER_TIER * tiers)
        p.message("You build a ${to.label} storage unit.")
    }

    /** CX38 / CX40: the unit changes in every hotspot of the run (floor ends, Ice demon, thieving, resource cells, Olm antechamber). */
    private fun swapLocs(any: EntityRef, run: RaidRun, from: CoxTier, to: CoxTier) {
        val plan = CoxFlow.state(run)?.plan ?: return
        for (cell in plan.cells) {
            if (cell.kind !in HOTSPOT_KINDS) continue
            for (loc in CoxMap.findLocs(any, run, cell, from.loc)) any.addLoc(to.loc, loc.coord, loc.shape, loc.angle)
        }
        for (template in OLM_HOTSPOTS) {
            val at = CoxMap.olm(run, template)
            val loc: LocRef = SHAPES.asList().firstNotNullOfOrNull { any.locAt(at, it) }?.takeIf { it.id == from.loc } ?: continue
            any.addLoc(to.loc, at, loc.shape, loc.angle)
        }
    }

    /** CX37: a hammer, a dragon warhammer or an elder maul (19 Jan 2017), carried or wielded. */
    private fun hasHammer(p: PlayerRef): Boolean {
        val inv = p.inv(Invs.INV)
        val worn = p.inv(Invs.WORN).obj(WEAPON_SLOT)
        return HAMMERS.any { inv.total(it) > 0 || worn == it }
    }

    // ---- entry (a hook for CoxFlow)

    /** CX41: whether the private storage still holds something the player must collect before entering. */
    fun holdsItems(p: PlayerRef): Boolean {
        val store = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        return store.freeSlots < store.size
    }

    /** CX41: the interface that shows when entering with items left, and its text (the Chambers entrance steps call it). */
    fun blockEntry(p: PlayerRef) {
        p.message(MUST_EMPTY)
        openPrivate(p, null)
    }

    /** Leaving the raid: raid-only items still in the private storage go (client-contract 2.7: only non-raiding items stay). */
    fun clearRaidItems(p: PlayerRef) {
        val store = p.inv(Invs.RAIDS_PRIVATESTORAGE)
        val slots = (0 until store.size).filter { store.obj(it) in CoxItems.RAID_ONLY }
        if (slots.isEmpty()) return
        p.transaction("cox private storage raid items removed") {
            slots.all { removeAt(Invs.RAIDS_PRIVATESTORAGE, it, store.obj(it), store.count(it)) }
        }
    }

    private fun setIfChanged(p: PlayerRef, varbit: Int, value: Int) {
        if (p.varbit(varbit) != value) p.setVarbit(varbit, value)
    }

    // ---- constants

    private const val MODE_PRIVATE = 1
    private const val MODE_SHARED = 2
    private const val NO_SLOT = -1
    private const val EMPTY = -1

    /** Client-contract 2.7: the shared list shows one entry per obj of enum 1666 (the raid's items). */
    private const val ENUM_SHARED_OBJS = 1666

    /** Client script `toplevel_mainmodal_open` (gameframe `SideInventory`): the default main-modal background. */
    private const val CS_MAINMODAL_BACKGROUND = 2524

    private const val BACKPACK_SLOTS = 28

    /** Client-contract 2.7: ops 1 Withdraw / Store, 2 -5, 3 -10, 4 -All, 5 -X; 10 Examine. */
    private const val OP_ONE = 1
    private const val OP_FIVE = 2
    private const val OP_TEN = 3
    private const val OP_ALL = 4
    private const val OP_EXAMINE = 10
    private const val FIVE = 5
    private const val TEN = 10

    @Suppress("MagicNumber") // op numbers
    private val ITEM_OPS: Long =
        IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(10)

    /** The backpack's ops again once the side panel closes (gameframe `SideInventory.ITEM_EVENTS`). */
    @Suppress("MagicNumber") // op numbers
    private val BACKPACK_EVENTS: Long =
        IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(6) or IfEvents.op(7) or IfEvents.op(10) or
            IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
            IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET

    private val PRIVATE_BUTTONS =
        intArrayOf(
            Components.Raids_Storage_Private.SHARED,
            Components.Raids_Storage_Private.WITHDRAW,
            Components.Raids_Storage_Private.BANK,
        )

    /** Varbit 1777 `IRONMAN`: 2 = Ultimate (client-contract 2.7: script 1602 tests `== 2`). */
    private const val IRONMAN_ULTIMATE = 2

    /** CX37: Construction experience per tier built. */
    private const val XP_PER_TIER = 150.0

    /** CX24: points per tier built. */
    private const val POINTS_PER_TIER = 100.0

    /** Worn weapon slot (RS Mod `Wear` index 3). */
    private const val WEAPON_SLOT = 3

    /** CX37: hammer, dragon warhammer, elder maul. */
    private val HAMMERS = intArrayOf(Objs.HAMMER, Objs.DRAGON_WARHAMMER, Objs.ELDER_MAUL)

    /** Centrepiece loc shapes (10 plain, 11 diagonal). */
    private val SHAPES = intArrayOf(10, 11)

    /** CX40 / client-contract 6.4: the cells that carry a storage hotspot. */
    private val HOTSPOT_KINDS = setOf(CoxKind.END, CoxKind.ICE_DEMON, CoxKind.THIEVING, CoxKind.FARMING_FISH, CoxKind.FARMING_BATS)

    /** Client-contract 6.6: the two hotspots of the Olm antechamber, (3228, 5727) and (3237, 5727). */
    private val OLM_HOTSPOTS = listOf(CoordGrid(3228, 5727), CoordGrid(3237, 5727))

    /** Six options at most fit a chat choice (plugin-api `choiceDialog`). */
    private const val MAX_CHOICES = 6

    // OPEN-CXUI1: none of these texts is captured; placeholders until seen in game.
    private const val AMOUNT_PROMPT = "Enter amount:"
    private const val STORAGE_FULL = "Your storage unit is full."
    private const val NO_INV_SPACE = "You don't have enough inventory space."
    private const val NO_BANK_SPACE = "You don't have enough space in your bank account."
    private const val CANNOT_STORE = "You cannot store that item here."
    private const val CANNOT_DONATE = "You cannot donate that item to the shared storage."
    private const val CANNOT_RETRIEVE = "As an Ironman you cannot retrieve items from the shared storage."
    private const val UIM_NO_STORAGE = "Ultimate Ironmen cannot use the storage unit."
    private const val UIM_NO_SHARED = "Ultimate Ironmen cannot donate to the shared storage."
    private const val NO_SHARED = "This storage unit has no shared storage; upgrade it first."
    private const val SHARED_IN_RAID = "The shared storage is only open inside the Chambers."
    private const val FULLY_BUILT = "This is already the largest storage unit."
    private const val BUILD_PROMPT = "Which storage unit?"
    private const val NEED_HAMMER = "You need a hammer to build a storage unit."
    private const val NEED_PLANKS_SPACE = "You cannot build that right now."
    private const val MUST_EMPTY = "You must retrieve all your items from the private storage before you can enter the Chambers of Xeric."
}
