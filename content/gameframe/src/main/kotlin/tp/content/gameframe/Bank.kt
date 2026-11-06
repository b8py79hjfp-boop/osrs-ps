package tp.content.gameframe

import tp.content.gameframe.BankLayout.MAIN
import tp.plugin.AppearanceChanged
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EventBus
import tp.plugin.IfEvents
import tp.plugin.InvView
import tp.plugin.ItemTx
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * The bank (docs/mechanics/bank.md): `bankmain` (12) as the main modal with `bankside` (15) over the backpack, inv 95.
 * Opened from bank booths, bank chests, bankers and `::bank`. Rules, op numbers and component roles follow RS Mod
 * `content/interfaces/bank` (ISC, DECISIONS D-029); the layout and tab bookkeeping are in [BankLayout], the drag
 * operations in [BankDrag]. Every item movement is one item transaction.
 */
public class Bank : ContentPlugin {
    private var bus: EventBus? = null

    override fun ContentScope.register() {
        bus = events
        onOpLocNamed("Bank booth", "Bank") { player.openBank() }
        onOpLocNamed("Bank chest", "Use") { player.openBank() }
        onOpNpcOption("Bank") { player.openBank() }
        onOpNpcNamed("Banker", "Talk-to") { player.openBank() }
        // The OSRS client keeps "::bank" for itself (it says "Hey, everyone, I just tried to do something very silly!").
        onCommand("openbank") { openBank() }

        onButton(Components.Bankmain.ITEMS) { click -> mainItem(click) }
        onButton(Components.Bankside.ITEMS) { click -> sideItem(click) }
        onButton(Components.Bankmain.TABS) { click -> tab(click) }
        onButton(Components.Bankmain.DEPOSITINV) { depositAll(Invs.INV) }
        onButton(Components.Bankmain.DEPOSITWORN) { depositAll(Invs.WORN) }
        onButton(Components.Bankmain.INCINERATOR_CONFIRM) { click -> incinerate(click) }
        for (w in WORN_COMPONENTS) onButton(w.bankTab) { click -> wornItem(w.slot, click) }
        registerSettings()
        onButtonDrag(Components.Bankmain.ITEMS) { d -> dragItem(d) }
        onButtonDrag(Components.Bankmain.TABS) { d ->
            if (d.toComponent == Components.Bankmain.TABS) BankDrag.moveTab(this, d.fromSlot - TAB_CHILD_FIRST, d.toSlot - TAB_CHILD_FIRST)
        }
        onButtonDrag(Components.Bankside.ITEMS) { d ->
            if (d.toComponent ==
                Components.Bankside.ITEMS
            ) {
                swapInv(Invs.INV, d.fromSlot, d.toSlot)
            }
        }
    }

    /**
     * B10–B12 (RS Mod `dragMainInv` / `dragIntoTab`): onto another item -> swap or insert by `bank_insertmode`; onto a
     * tab's "extended" slot past the last bank slot or onto a tab button -> move to the end of that tab.
     */
    private fun PlayerRef.dragItem(d: tp.plugin.ButtonDrag) {
        val last = inv(Invs.BANK, stackAll = true).size - 1
        when (d.toComponent) {
            Components.Bankmain.TABS -> {
                val tab = d.toSlot - TAB_CHILD_FIRST
                BankDrag.intoTab(this, d.fromSlot, if (d.toSlot == TAB_CHILD_MAIN) MAIN else tab)
            }

            Components.Bankmain.ITEMS -> {
                val extended = last + EXTENDED_FIRST..last + EXTENDED_LAST
                if (d.toSlot in extended) {
                    // RS Mod: tab index = target - first extended slot; 0 = main, n = numbered tab n.
                    val index = d.toSlot - extended.first
                    BankDrag.intoTab(this, d.fromSlot, if (index == 0) MAIN else index - 1)
                } else if (varbit(Varbits.BANK_INSERTMODE) == 1) {
                    BankDrag.insert(this, d.fromSlot, d.toSlot)
                } else {
                    BankDrag.swap(this, d.fromSlot, d.toSlot)
                }
            }
        }
    }

    /** Swaps two backpack slots (bank side drag; RS Mod `dragSideInv` / `invMoveToSlot`). */
    private fun PlayerRef.swapInv(invId: Int, from: Int, to: Int) {
        val size = inv(invId).size
        if (from !in 0 until size || to !in 0 until size || from == to) return
        transaction("inv drag") { swap(invId, from, to) }
    }

    private fun ContentScope.registerSettings() {
        onButton(Components.Bankmain.SWAP_INSERT) { toggle(Varbits.BANK_INSERTMODE) }
        onButton(Components.Bankmain.NOTE) { toggle(Varbits.BANK_WITHDRAWNOTES) }
        onButton(Components.Bankmain.PLACEHOLDER) { toggle(Varbits.BANK_LEAVEPLACEHOLDERS) }
        onButton(Components.Bankmain.QUANTITY1) { setVarbit(Varbits.BANK_QUANTITY_TYPE, QTY_ONE) }
        onButton(Components.Bankmain.QUANTITY5) { setVarbit(Varbits.BANK_QUANTITY_TYPE, QTY_FIVE) }
        onButton(Components.Bankmain.QUANTITY10) { setVarbit(Varbits.BANK_QUANTITY_TYPE, QTY_TEN) }
        onButton(Components.Bankmain.QUANTITYALL) { setVarbit(Varbits.BANK_QUANTITY_TYPE, QTY_ALL) }
        onButton(Components.Bankmain.QUANTITYX) { click -> quantityX(click.op) }
        onButton(Components.Bankmain.DROPDOWN_CONTENT) { click -> tabDisplay(click.slot) }
        onButton(Components.Bankmain.INCINERATOR_TOGGLE) { toggle(Varbits.BANK_SHOWINCINERATOR) }
        onButton(Components.Bankmain.BANKTUT_TOGGLE) { toggle(Varbits.BANK_HIDEBANKTUT) }
        onButton(Components.Bankmain.SIDEOPS_TOGGLE) { toggle(Varbits.BANK_HIDESIDEOPS) }
        onButton(Components.Bankmain.DEPOSITINV_TOGGLE) { toggle(Varbits.BANK_HIDEDEPOSITINV) }
        onButton(Components.Bankmain.DEPOSITWORN_TOGGLE) { toggle(Varbits.BANK_HIDEDEPOSITWORN) }
        onButton(Components.Bankmain.RELEASE_PLACEHOLDERS) { releasePlaceholders(0, Int.MAX_VALUE) }
    }

    // ---- opening and closing

    private fun PlayerRef.openBank() {
        BankLayout.tidy(this)
        transmitInv(Invs.INV)
        transmitInv(Invs.WORN)
        transmitInv(Invs.BANK)
        // RS Mod `BankOpenScript.onBankOpen`: withdraw-as-note is off every time the bank opens.
        setVarbit(Varbits.BANK_WITHDRAWNOTES, 0)
        SideInventory.openPair(this, Interfaces.BANKMAIN, Interfaces.BANKSIDE)
        enableEvents()
        ifText(Components.Bankmain.CAPACITY, capacity(inv(Invs.BANK, stackAll = true)).toString())
        ModalWatch.start(this, Interfaces.BANKMAIN, onClose = { SideInventory.restore(this) })
    }

    /** IF_SETEVENTS of every bank component with item ops, as RS Mod `BankOpenScript.setBankIfEvents` sends them. */
    private fun PlayerRef.enableEvents() {
        val bankSize = inv(Invs.BANK, stackAll = true).size
        val last = bankSize - 1
        ifEvents(Components.Bankmain.ITEMS, 0..last, ALL_OPS or IfEvents.DEPTH2 or IfEvents.DRAG_TARGET)
        // The client builds extra drop targets after the last slot: one per tab to drop into, and the "new tab" slots.
        ifEvents(Components.Bankmain.ITEMS, last + TAB_OP_SLOTS_FIRST..last + TAB_OP_SLOTS_LAST, IfEvents.op(1))
        ifEvents(Components.Bankmain.ITEMS, last + EXTENDED_FIRST..last + EXTENDED_LAST, IfEvents.DRAG_TARGET)
        val mainTab = IfEvents.op(1) or IfEvents.op(OP_RELEASE) or IfEvents.DRAG_TARGET
        ifEvents(Components.Bankmain.TABS, TAB_CHILD_MAIN..TAB_CHILD_MAIN, mainTab)
        val otherTabs = mainTab or IfEvents.op(OP_COLLAPSE) or IfEvents.DEPTH1
        ifEvents(Components.Bankmain.TABS, TAB_CHILD_FIRST..TAB_CHILD_LAST, otherTabs)
        ifEvents(Components.Bankside.ITEMS, 0 until inv(Invs.INV).size, ALL_OPS or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET)
        ifEvents(Components.Bankmain.INCINERATOR_CONFIRM, 1..bankSize, IfEvents.op(1))
        ifEvents(Components.Bankmain.DROPDOWN_CONTENT, 0..DROPDOWN_LAST, IfEvents.op(1))
    }

    private fun capacity(bank: InvView): Int = minOf(DEFAULT_CAPACITY, bank.size)

    private fun PlayerRef.bankOpen(): Boolean = isOpen(Interfaces.BANKMAIN)

    // ---- withdrawing

    private fun PlayerRef.mainItem(click: ButtonClick) {
        val bank = inv(Invs.BANK, stackAll = true)
        val slot = click.slot
        val obj = click.obj
        if (click.op == OP_EXAMINE || !validSlot(bank, slot, obj)) return
        if (isPlaceholder(obj)) return clearSlots("bank release placeholder", listOf(slot))
        val have = bank.count(slot)
        when (click.op) {
            OP_LEFT -> withdraw(slot, obj, leftClickQuantity(), placeholder = false)
            OP_ONE -> withdraw(slot, obj, 1, placeholder = false)
            OP_FIVE -> withdraw(slot, obj, FIVE, placeholder = false)
            OP_TEN -> withdraw(slot, obj, TEN, placeholder = false)
            OP_LAST_X -> withdraw(slot, obj, lastQuantity(), placeholder = false)
            OP_X -> askQuantity { n -> withdraw(slot, obj, n, placeholder = false) }
            OP_ALL -> withdraw(slot, obj, have, placeholder = false)
            OP_ALL_BUT_ONE -> withdraw(slot, obj, have - 1, placeholder = false)
            OP_PLACEHOLDER -> withdraw(slot, obj, have, placeholder = true)
        }
    }

    /** Whether [slot] of [inv] holds [obj] (client-sent slot and obj are never trusted: brief section 12). */
    private fun validSlot(inv: InvView, slot: Int, obj: Int): Boolean = obj >= 0 && slot in 0 until inv.size && inv.obj(slot) == obj

    /**
     * Withdraws up to [requested] of [obj] from bank [slot] (re-checked: this also runs after a count dialog). As many
     * as fit are taken (RS Mod `invMoveFromSlot` with `strict = false`); a stack taken completely leaves a placeholder
     * when the always-placeholder setting is on or [placeholder] (op 9), else its slot closes.
     */
    private fun PlayerRef.withdraw(slot: Int, obj: Int, requested: Int, placeholder: Boolean) {
        val bank = inv(Invs.BANK, stackAll = true)
        if (!bankOpen() || requested <= 0 || !validSlot(bank, slot, obj)) return
        val want = minOf(requested, bank.count(slot))
        val asNote = varbit(Varbits.BANK_WITHDRAWNOTES) == 1
        val note = if (asNote) noted(obj) else -1
        val out = if (note >= 0) note else obj
        val room = backpackRoom(out, want)
        if (room <= 0) return message("You don't have enough inventory space.")
        val emptied = room == bank.count(slot)
        val keep = if (emptied && (placeholder || varbit(Varbits.BANK_LEAVEPLACEHOLDERS) == 1)) placeholderOf(obj) else -1
        if (!takeOut(slot, obj, out, room, keep)) return
        if (asNote && note < 0 && !objStackable(obj)) message("This item cannot be withdrawn as a note.")
        if (room < want) message("You don't have enough inventory space to withdraw that many.")
    }

    /**
     * Moves [count] of bank [obj] in [slot] to the backpack as [out]. A slot emptied completely becomes the placeholder
     * [keep] (when not -1) or closes.
     */
    private fun PlayerRef.takeOut(slot: Int, obj: Int, out: Int, count: Int, keep: Int): Boolean {
        val bank = inv(Invs.BANK, stackAll = true)
        val sizes = BankLayout.readSizes(this)
        val used = BankLayout.used(bank)
        val emptied = count == bank.count(slot)
        val ok =
            transaction("bank withdraw") {
                add(Invs.INV, out, count) &&
                    when {
                        !emptied -> removeAt(Invs.BANK, slot, obj, count)
                        keep >= 0 -> replaceAt(Invs.BANK, slot, obj, keep, 1)
                        else -> removeAt(Invs.BANK, slot, obj, count) && closeBankGap(slot, used)
                    }
            }
        if (ok && emptied && keep < 0) {
            BankLayout.shrink(sizes, slot)
            BankLayout.finish(this, sizes)
        }
        return ok
    }

    /** How many of [want] objs [out] the backpack can take: stackables need one slot or an existing stack. */
    private fun PlayerRef.backpackRoom(out: Int, want: Int): Int {
        val backpack = inv(Invs.INV)
        if (!objStackable(out)) return minOf(want, backpack.freeSlots)
        val held = backpack.total(out)
        if (held == 0L && backpack.freeSlots == 0) return 0
        return minOf(want.toLong(), Int.MAX_VALUE - held).toInt()
    }

    // ---- depositing

    private fun PlayerRef.sideItem(click: ButtonClick) {
        val slot = click.slot
        val obj = click.obj
        if (!validSlot(inv(Invs.INV), slot, obj)) return
        when (click.op) {
            OP_SIDE_LEFT -> depositOne(slot, obj, leftClickQuantity())
            OP_SIDE_ONE -> depositOne(slot, obj, 1)
            OP_SIDE_FIVE -> depositOne(slot, obj, FIVE)
            OP_SIDE_TEN -> depositOne(slot, obj, TEN)
            OP_SIDE_LAST_X -> depositOne(slot, obj, lastQuantity())
            OP_SIDE_X -> askQuantity { n -> depositOne(slot, obj, n) }
            OP_SIDE_ALL -> depositOne(slot, obj, Int.MAX_VALUE)
        }
    }

    /** Deposits [requested] of the backpack obj in [slot] (all copies count, as in RS Mod's `transfer`). */
    private fun PlayerRef.depositOne(slot: Int, obj: Int, requested: Int) {
        if (!bankOpen() || requested <= 0 || inv(Invs.INV).obj(slot) != obj) return
        val out = deposit(Invs.INV, listOf(obj to requested))
        when {
            out.noBank > 0 -> message(NO_BANK_MESSAGE)
            out.full > 0 -> message("You don't have enough space in your bank account.")
            out.overflow > 0 -> message("You already have a full stack of that item in the bank.")
        }
    }

    /** Deposit inventory (backpack) or Deposit worn items. */
    private fun PlayerRef.depositAll(source: Int) {
        if (!bankOpen()) return
        val from = inv(source)
        val wants = ArrayList<Pair<Int, Int>>()
        val seen = HashSet<Int>()
        for (slot in 0 until from.size) {
            val obj = from.obj(slot)
            if (obj >= 0 && seen.add(obj)) wants += obj to Int.MAX_VALUE
        }
        if (wants.isEmpty()) return message("You have nothing to deposit.")
        val out = deposit(source, wants)
        when {
            out.noBank > 0 && !out.moved && out.full == 0 -> message("Your items cannot be stored in the bank.")
            out.noBank > 0 -> message("Some of your items cannot be stored in the bank.")
        }
        if (out.full > 0) message("Your bank cannot hold your items.")
        if (out.overflow > 0) message("You already have a full stack of that item in the bank.")
    }

    private class Outcome {
        var moved = false
        var noBank = 0
        var full = 0
        var overflow = 0
    }

    private enum class Kind { STACK, REPLACE, NEW }

    private class Step(val obj: Int, val real: Int, val amount: Int, val kind: Kind, val slot: Int, val placeholder: Int)

    /** Where deposits of one real obj go: its bank [slot], the amount [held] there once the plan ran, and if it is a placeholder. */
    private class Target(val slot: Int, var held: Long, var placeholder: Int)

    /** The steps planned so far for one deposit; every [Kind.NEW] step opens the next slot after the [used] ones. */
    private class Plan(val used: Int, val capacity: Int) {
        val steps = ArrayList<Step>()
        val targets = HashMap<Int, Target>()
        val out = Outcome()
        var next = used

        /** New slots opened. */
        val created: Int get() = next - used
    }

    /**
     * Deposits [wants] (exact obj, requested count) from [source] (backpack or worn). Notes go in as the item they
     * stand for; a stack joins the obj's slot, replaces its placeholder in place, or opens a new slot at the end of
     * the selected tab if the bank has room (RS Mod `invDeposit` / `depositInventory`). One transaction: the removals,
     * the bank edits and the move of new slots into the selected tab.
     */
    private fun PlayerRef.deposit(source: Int, wants: List<Pair<Int, Int>>): Outcome {
        val bank = inv(Invs.BANK, stackAll = true)
        val plan = Plan(BankLayout.used(bank), capacity(bank))
        for ((obj, requested) in wants) planOne(plan, source, bank, obj, requested)
        val sizes = BankLayout.readSizes(this)
        val tab = BankLayout.selectedTab(this)
        val end = BankLayout.end(sizes, tab, plan.used)
        val ok =
            plan.steps.isNotEmpty() &&
                transaction("bank deposit") { applyPlan(plan, source) && (tab == MAIN || rotateBank(end, plan.used, plan.next)) }
        plan.out.moved = ok
        if (ok && tab != MAIN && plan.created > 0) {
            sizes[tab] += plan.created
            BankLayout.writeSizes(this, sizes)
        }
        if (ok && source == Invs.WORN) bus?.publish(AppearanceChanged(this))
        return plan.out
    }

    private fun ItemTx.applyPlan(plan: Plan, source: Int): Boolean =
        plan.steps.all { s ->
            remove(source, s.obj, s.amount) &&
                when (s.kind) {
                    Kind.REPLACE -> replaceAt(Invs.BANK, s.slot, s.placeholder, s.real, s.amount)
                    else -> add(Invs.BANK, s.real, s.amount)
                }
        }

    /** Plans the deposit of up to [requested] of [obj] (counts what the source holds of that exact obj). */
    private fun PlayerRef.planOne(plan: Plan, source: Int, bank: InvView, obj: Int, requested: Int) {
        if (objParam(obj, PARAM_NO_BANK, 0) != 0) {
            plan.out.noBank++
            return
        }
        val real = unnoted(obj)
        val amount = minOf(requested.toLong(), inv(source).total(obj)).toInt()
        if (amount <= 0) return
        val target = plan.targets[real] ?: findTarget(bank, real, plan.used)
        if (target == null) {
            if (plan.next >= plan.capacity) {
                plan.out.full++
                return
            }
            plan.steps += Step(obj, real, amount, Kind.NEW, plan.next, -1)
            plan.targets[real] = Target(plan.next++, amount.toLong(), -1)
            return
        }
        val take = minOf(amount.toLong(), Int.MAX_VALUE - target.held).toInt()
        if (take <= 0) {
            plan.out.overflow++
            return
        }
        val kind = if (target.placeholder >= 0) Kind.REPLACE else Kind.STACK
        plan.steps += Step(obj, real, take, kind, target.slot, target.placeholder)
        target.held += take
        target.placeholder = -1
        plan.targets[real] = target
    }

    /** The bank slot [real] stacks onto: itself, else its placeholder (replaced in place), else null (needs a new slot). */
    private fun PlayerRef.findTarget(bank: InvView, real: Int, used: Int): Target? {
        val slot = BankLayout.find(bank, real, used)
        if (slot >= 0) return Target(slot, bank.count(slot).toLong(), -1)
        val holder = placeholderOf(real)
        val held = BankLayout.find(bank, holder, used)
        return if (held >= 0) Target(held, 0L, holder) else null
    }

    private fun PlayerRef.wornItem(wornSlot: Int, click: ButtonClick) {
        val worn = inv(Invs.WORN)
        val obj = worn.obj(wornSlot)
        if (obj < 0 || !bankOpen()) return
        when (click.op) {
            // Op 1 removes to the backpack through the worn tab's own handler; op 2 deposits into the bank.
            1 -> dispatchButton(WORN_COMPONENTS.first { it.slot == wornSlot }.wornTab, 1)

            2 -> if (deposit(Invs.WORN, listOf(obj to worn.count(wornSlot))).noBank > 0) message(NO_BANK_MESSAGE)
        }
    }

    // ---- removing slots

    /** Removes the whole stacks in [slots] (placeholders, incinerated items) and closes the gaps they leave. */
    private fun PlayerRef.clearSlots(reason: String, slots: List<Int>) {
        if (!bankOpen() || slots.isEmpty()) return
        val bank = inv(Invs.BANK, stackAll = true)
        val sizes = BankLayout.readSizes(this)
        val used = BankLayout.used(bank)
        val ordered = slots.distinct().sortedDescending()
        if (ordered.any { it !in 0 until used }) return
        val ok =
            transaction(reason) {
                var count = used
                for (s in ordered) {
                    if (!removeAt(Invs.BANK, s, bank.obj(s), bank.count(s)) || !closeBankGap(s, count)) return@transaction false
                    count--
                }
                true
            }
        if (!ok) return
        // Highest slot first: lowering one tab's size never moves a lower slot into another tab.
        for (s in ordered) BankLayout.shrink(sizes, s)
        BankLayout.finish(this, sizes)
    }

    private fun PlayerRef.incinerate(click: ButtonClick) {
        // The confirm grid is 1-based over the bank slots (RS Mod `incinerate`: slot = comsub - 1).
        val slot = click.slot - 1
        val bank = inv(Invs.BANK, stackAll = true)
        if (!validSlot(bank, slot, click.obj) || isPlaceholder(click.obj)) return
        clearSlots("bank incinerate", listOf(slot))
    }

    /** Releases the placeholders in the slots [from, until) (a tab's range, or everything). */
    private fun PlayerRef.releasePlaceholders(from: Int, until: Int) {
        if (!bankOpen()) return
        val bank = inv(Invs.BANK, stackAll = true)
        val last = minOf(until, BankLayout.used(bank))
        val slots = (from until last).filter { isPlaceholder(bank.obj(it)) }
        if (slots.isEmpty()) return message("You don't have any placeholders to release.")
        clearSlots("bank release placeholders", slots)
    }

    // ---- tabs

    private fun PlayerRef.tab(click: ButtonClick) {
        val child = click.slot
        val tab =
            when (child) {
                TAB_CHILD_MAIN -> MAIN
                in TAB_CHILD_FIRST..TAB_CHILD_LAST -> child - TAB_CHILD_FIRST
                else -> return
            }
        val bank = inv(Invs.BANK, stackAll = true)
        val sizes = BankLayout.readSizes(this)
        val used = BankLayout.used(bank)
        if (tab != MAIN && sizes[tab] == 0) return message("To create a new tab, drag items from your bank onto this tab.")
        val from = BankLayout.start(sizes, tab)
        when (click.op) {
            1 -> BankLayout.selectTab(this, tab)
            OP_COLLAPSE -> if (tab != MAIN) collapse(tab, sizes, used)
            OP_RELEASE -> releasePlaceholders(from, BankLayout.end(sizes, tab, used))
        }
    }

    /** Moves a tab's items to the end of main, in order, and drops the tab (RS Mod `collapseTab`). */
    private fun PlayerRef.collapse(tab: Int, sizes: IntArray, used: Int) {
        val first = BankLayout.start(sizes, tab)
        val end = first + sizes[tab]
        if (!transaction("bank collapse tab") { rotateBank(first, end, used) }) return
        sizes[tab] = 0
        BankLayout.finish(this, sizes)
    }

    private fun PlayerRef.tabDisplay(child: Int) {
        // Dropdown rows: 1 item, 3 digit, 5 roman numeral (RS Mod `selectTabDisplay`); the odd children are separators.
        val mode = (child - 1) / 2
        if (child in TAB_DISPLAY_CHILDREN) setVarbit(Varbits.BANK_TAB_DISPLAY, mode)
    }

    // ---- quantity

    private fun PlayerRef.leftClickQuantity(): Int =
        when (varbit(Varbits.BANK_QUANTITY_TYPE)) {
            QTY_FIVE -> FIVE
            QTY_TEN -> TEN
            QTY_X -> lastQuantity()
            QTY_ALL -> Int.MAX_VALUE
            else -> 1
        }

    /** The last "X" typed; never below 1 (RS Mod `resolveLastDepositQty`). */
    private fun PlayerRef.lastQuantity(): Int = maxOf(1, varbit(Varbits.BANK_REQUESTEDQUANTITY))

    /** Asks for a number, remembers it as the last "X" when it fits the varbit, then runs [then] with it. */
    private fun PlayerRef.askQuantity(then: PlayerRef.(Int) -> Unit) {
        countDialog("Enter amount:") { n ->
            if (n > 0) {
                storeQuantity(n)
                then(n)
            }
        }
    }

    private fun PlayerRef.storeQuantity(n: Int) {
        if (varbitFits(Varbits.BANK_REQUESTEDQUANTITY, n)) setVarbit(Varbits.BANK_REQUESTEDQUANTITY, n)
    }

    /** "X" button: op 1 uses the last X, op 2 asks for a new one (RS Mod `selectQuantityX`). */
    private fun PlayerRef.quantityX(op: Int) {
        if (op == 2) {
            countDialog("Enter amount:") { n ->
                storeQuantity(n)
                useLastQuantity()
            }
        } else {
            useLastQuantity()
        }
    }

    private fun PlayerRef.useLastQuantity() {
        setVarbit(Varbits.BANK_QUANTITY_TYPE, if (varbit(Varbits.BANK_REQUESTEDQUANTITY) == 0) QTY_ONE else QTY_X)
    }

    private fun PlayerRef.toggle(id: Int) {
        setVarbit(id, 1 - varbit(id))
    }

    private companion object {
        /**
         * Members' bank capacity (RS Mod `BankConstants.default_capacity`; OSRS Wiki "Bank": members start with 800). The
         * cache inv holds more (1410) for slots a banker sells, which are not implemented (bank.md OPEN-B1).
         */
        const val DEFAULT_CAPACITY = 800

        /** Obj param `no_bank` (59): non-zero means the item cannot be banked (RS Mod `BaseParams.no_bank`). */
        const val PARAM_NO_BANK = 59

        const val NO_BANK_MESSAGE = "A magical force prevents you from banking this item!"

        const val FIVE = 5
        const val TEN = 10

        /** `bank_quantity_type` values: 1, 5, 10, X, All (RS Mod `QuantityMode`). */
        const val QTY_ONE = 0
        const val QTY_FIVE = 1
        const val QTY_TEN = 2
        const val QTY_X = 3
        const val QTY_ALL = 4

        /**
         * Withdraw ops on the bank grid (RS Mod `mainInvWithdraw`): left-click quantity, 1, 5, 10, last X, X, All,
         * All-but-1, All and leave a placeholder.
         */
        const val OP_LEFT = 1
        const val OP_ONE = 2
        const val OP_FIVE = 3
        const val OP_TEN = 4
        const val OP_LAST_X = 5
        const val OP_X = 6
        const val OP_ALL = 7
        const val OP_ALL_BUT_ONE = 8
        const val OP_PLACEHOLDER = 9

        /** Deposit ops on the backpack grid (RS Mod `sideInvOp`): left-click quantity, 1, 5, 10, last X, X, All. */
        const val OP_SIDE_LEFT = 2
        const val OP_SIDE_ONE = 3
        const val OP_SIDE_FIVE = 4
        const val OP_SIDE_TEN = 5
        const val OP_SIDE_LAST_X = 6
        const val OP_SIDE_X = 7
        const val OP_SIDE_ALL = 8

        /** Button op 10 on item grids is "Examine" (RS Mod `BankInvScript`); no examine texts exist yet (PROGRESS: deferred). */
        const val OP_EXAMINE = 10

        /** Tab ops: 6 collapses a tab, 7 releases its placeholders (RS Mod `selectTab`). */
        const val OP_COLLAPSE = 6
        const val OP_RELEASE = 7

        /** Sub-component of the main tab in `bankmain:tabs`; numbered tabs follow (RS Mod `BankSubComponents`). */
        const val TAB_CHILD_MAIN = 10
        const val TAB_CHILD_FIRST = 11
        const val TAB_CHILD_LAST = 19

        /** Sub-components after the last bank slot that carry ops / drag targets (RS Mod `setBankIfEvents`). */
        const val TAB_OP_SLOTS_FIRST = 10
        const val TAB_OP_SLOTS_LAST = 18
        const val EXTENDED_FIRST = 19
        const val EXTENDED_LAST = 28

        /** Rows of the tab display dropdown (`bankmain:dropdown_content` children 0..8; RS Mod `setBankIfEvents`). */
        const val DROPDOWN_LAST = 8
        val TAB_DISPLAY_CHILDREN = setOf(1, 3, 5)

        /** Ops 1..10 (RS Mod `setBankIfEvents` enables Op1..Op10 on the item grids). */
        val ALL_OPS: Long = (1..10).fold(0L) { acc, op -> acc or IfEvents.op(op) }

        /** Worn slot (inv 94 position), its component in `bankmain` and its slot component in the worn tab. */
        class WornSlot(val slot: Int, val bankTab: Int, val wornTab: Int)

        /** Bank worn-slot components by worn inv slot (`bank_equipment_tab_to_slots_map`, enum 2777). */
        val WORN_COMPONENTS: List<WornSlot> =
            listOf(
                WornSlot(0, Components.Bankmain.WORNSLOT0, Components.Wornitems.SLOT0),
                WornSlot(1, Components.Bankmain.WORNSLOT1, Components.Wornitems.SLOT1),
                WornSlot(2, Components.Bankmain.WORNSLOT2, Components.Wornitems.SLOT2),
                WornSlot(3, Components.Bankmain.WORNSLOT3, Components.Wornitems.SLOT3),
                WornSlot(4, Components.Bankmain.WORNSLOT4, Components.Wornitems.SLOT4),
                WornSlot(5, Components.Bankmain.WORNSLOT5, Components.Wornitems.SLOT5),
                WornSlot(7, Components.Bankmain.WORNSLOT7, Components.Wornitems.SLOT7),
                WornSlot(9, Components.Bankmain.WORNSLOT9, Components.Wornitems.SLOT9),
                WornSlot(10, Components.Bankmain.WORNSLOT10, Components.Wornitems.SLOT10),
                WornSlot(12, Components.Bankmain.WORNSLOT12, Components.Wornitems.SLOT12),
                WornSlot(13, Components.Bankmain.WORNSLOT13, Components.Wornitems.SLOT13),
            )
    }
}
