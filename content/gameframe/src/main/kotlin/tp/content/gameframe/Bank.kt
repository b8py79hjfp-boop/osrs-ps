package tp.content.gameframe

import tp.content.gameframe.BankLayout.MAIN
import tp.plugin.AppearanceChanged
import tp.plugin.ButtonClick
import tp.plugin.ConsumeRequest
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EquipRequest
import tp.plugin.EventBus
import tp.plugin.IfEvents
import tp.plugin.InvView
import tp.plugin.ItemTx
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The bank (docs/mechanics/bank.md): `bankmain` (12) as the main modal with `bankside` (15) over the backpack, inv 95.
 * Opened from bank booths, bank chests, bankers and `::bank`. Rules, op numbers and component roles follow RS Mod
 * `content/interfaces/bank` (ISC, DECISIONS D-029); the layout and tab bookkeeping are in [BankLayout], the drag
 * operations in [BankDrag]. Every item movement is one item transaction.
 */
public class Bank : ContentPlugin {
    private var bus: EventBus? = null
    private val potions = PotionStore()

    override fun ContentScope.register() {
        bus = events
        // BP2: every way in asks for the bank PIN first (BankPin.unlock returns at once without one).
        events.on<tp.plugin.OpenBankRequest> { it.player.weakQueue(0) { if (with(BankPin) { unlock() }) player.openBank() } }
        onOpLocNamed("Bank booth", "Bank") { if (with(BankPin) { unlock() }) player.openBank() }
        onOpLocNamed("Bank chest", "Use") { if (with(BankPin) { unlock() }) player.openBank() }
        onOpLocNamed("Grand Exchange booth", "Bank") { if (with(BankPin) { unlock() }) player.openBank() }
        onOpNpcOption("Bank") { if (with(BankPin) { unlock() }) player.openBank() }
        onOpNpcNamed("Banker", "Talk-to") { npc -> banker(npc) }
        // The OSRS client keeps "::bank" for itself (it says "Hey, everyone, I just tried to do something very silly!").
        onCommand("openbank") { weakQueue(0) { if (with(BankPin) { unlock() }) player.openBank() } }
        events.on<tp.plugin.BankPinRequest> { r -> r.player.weakQueue(0) { if (with(BankPin) { unlock() }) r.then(player) } }
        onLogin { BankPin.onLogin(this) }
        onLogout { BankPin.onLogout(this) }

        onButton(Components.Bankmain.ITEMS) { click -> mainItem(click) }
        onButton(Components.Bankside.ITEMS) { click -> sideItem(click) }
        onButton(Components.Bankmain.TABS) { click -> tab(click) }
        onButton(Components.Bankmain.DEPOSITINV) { depositAll(Invs.INV) }
        onButton(Components.Bankmain.DEPOSITWORN) { depositAll(Invs.WORN) }
        onButton(Components.Bankmain.INCINERATOR_CONFIRM) { click -> incinerate(click) }
        for (w in WORN_COMPONENTS) onButton(w.bankTab) { click -> wornItem(w.slot, click) }
        registerSettings()
        with(potions) { registerPotions() }
        onButtonDrag(Components.Bankmain.ITEMS) { d -> dragItem(d) }
        onButtonDrag(Components.Bankmain.TABS) { d ->
            // Only a numbered tab (children 11..19) can be dragged, and only onto another tab (script 506 builds them).
            val from = d.fromSlot - TAB_CHILD_FIRST
            val to = d.toSlot - TAB_CHILD_FIRST
            if (d.toComponent == Components.Bankmain.TABS) BankDrag.moveTab(this, from, to)
        }
        onButtonDrag(Components.Bankside.ITEMS) { d ->
            if (d.toComponent == Components.Bankside.ITEMS) swapInv(Invs.INV, d.fromSlot, d.toSlot)
        }
    }

    /**
     * B10-B12 (client scripts 506 and 278; RS Mod `dragMainInv` / `dragIntoTab`): onto another slot of the grid -> swap or
     * insert by `bank_insertmode`; onto a tab button (`bankmain:tabs` child 10 = main, 11..19 = tabs 1..9) -> move to the
     * end of that tab. Slots and children come from the client and are checked by [BankDrag].
     */
    private fun PlayerRef.dragItem(d: tp.plugin.ButtonDrag) {
        when (d.toComponent) {
            Components.Bankmain.TABS -> {
                val tab =
                    when (d.toSlot) {
                        TAB_CHILD_MAIN -> MAIN
                        in TAB_CHILD_FIRST..TAB_CHILD_LAST -> d.toSlot - TAB_CHILD_FIRST
                        else -> return
                    }
                BankDrag.intoTab(this, d.fromSlot, tab)
            }

            // A drop on no slot (-1) does nothing; a drop on an empty slot past the last item is [BankDrag]'s "move to the end".
            Components.Bankmain.ITEMS -> {
                if (d.toSlot < 0) {
                    return
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
        val valid = from in 0 until size && to in 0 until size && from != to
        if (bankOpen() && valid) transaction("inv drag") { swap(invId, from, to) }
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
        // B9b: asked first (RS Mod `BankInvScript` release-all).
        onButton(Components.Bankmain.RELEASE_PLACEHOLDERS) {
            confirmOverlay(
                Components.Bankmain.POPUP,
                "Release all placeholders?",
                RELEASE_ALL_TEXT,
                "<col=ff0000>Cancel</col>",
                "<col=0dc10d>Release them all</col>",
            ) { yes -> if (yes && bankOpen()) releasePlaceholders(0, Int.MAX_VALUE) }
        }
        // Bank fillers: the amount buttons set `bank_fillermode` (client script 2210 / enum 1907: children 1, 10, 50, X = 1..4,
        // All = anything else, RS Mod `BankFillerMode` 0), "Fill" adds them.
        onButton(Components.Bankmain.BANK_FILLER_1) { setVarbit(Varbits.BANK_FILLERMODE, FILLER_ONE) }
        onButton(Components.Bankmain.BANK_FILLER_10) { setVarbit(Varbits.BANK_FILLERMODE, FILLER_TEN) }
        onButton(Components.Bankmain.BANK_FILLER_50) { setVarbit(Varbits.BANK_FILLERMODE, FILLER_FIFTY) }
        onButton(Components.Bankmain.BANK_FILLER_X) { setVarbit(Varbits.BANK_FILLERMODE, FILLER_X) }
        onButton(Components.Bankmain.BANK_FILLER_ALL) { setVarbit(Varbits.BANK_FILLERMODE, FILLER_ALL) }
        onButton(Components.Bankmain.BANK_FILLER_CONFIRM) { fill() }
    }

    /**
     * B1 / BP5: Wiki "Transcript:Banker" standard dialogue. OPEN-B1: "I'd like to buy more bank slots." is left out until
     * bought bank space exists.
     */
    private suspend fun PlayerScript.banker(npc: NpcRef) {
        chatNpc(npc, "Good day, how may I help you?")
        when (
            choice(
                "Select an Option",
                "I'd like to access my bank account, please.",
                "I'd like to check my PIN settings.",
                "I'd like to collect items.",
                "What is this place?",
            )
        ) {
            1 -> {
                if (with(BankPin) { unlock() }) player.openBank()
            }

            2 -> {
                with(BankPin) { settings() }
            }

            3 -> {
                bus?.publish(tp.plugin.OpenCollectionBox(player))
            }

            4 -> {
                chatPlayer("What is this place?")
                chatNpc(npc, "This is a branch of the Bank of Gielinor. We have branches in many towns.")
                chatPlayer("And what do you do?")
                chatNpc(npc, "We will look after your items and money for you. Leave your valuables with us if you want to keep them safe.")
            }
        }
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
        potions.onOpen(this)
        ifText(Components.Bankmain.CAPACITY, capacity(inv(Invs.BANK, stackAll = true)).toString())
        // RS Mod `onBankOpen`: the capacity tooltip (client script 1495 takes the text, the hover layer and the tooltip box).
        clientScript(CS_TOOLTIP, CAPACITY_TOOLTIP, Components.Bankmain.CAPACITY_LAYER, Components.Bankmain.TOOLTIP)
        // The extra backpack ops and the worn items page follow the backpack and the worn items, which change while the
        // bank is open (wield from the bank, eating, deposits): they are compared once per tick and redone on a change.
        val seen = IntArray(inv(Invs.INV).size + WornBonusPanel.WORN_SLOTS)
        refreshView(seen, force = true)
        ModalWatch.start(
            this,
            Interfaces.BANKMAIN,
            whileOpen = { refreshView(seen, force = false) },
            onClose = { SideInventory.restore(this) },
        )
    }

    /**
     * Sets the backpack column's extra-op bits (varps `if2` = wear, `if3` = eat / drink, read by client script 2576 for
     * the ninth op) and the worn items page texts, if the backpack or the worn items differ from [seen].
     */
    private fun PlayerRef.refreshView(seen: IntArray, force: Boolean) {
        val backpack = inv(Invs.INV)
        val worn = inv(Invs.WORN)
        var changed = force
        for (i in 0 until backpack.size) {
            if (seen[i] != backpack.obj(i)) {
                seen[i] = backpack.obj(i)
                changed = true
            }
        }
        for (i in 0 until WornBonusPanel.WORN_SLOTS) {
            if (seen[backpack.size + i] != worn.obj(i)) {
                seen[backpack.size + i] = worn.obj(i)
                changed = true
            }
        }
        if (!changed) return
        setBanksideExtraOps(backpack)
        WornBonusPanel.show(this, WORN_PAGE)
    }

    /**
     * Script 2576, per backpack slot: bit n of varp 262 = a wearable obj whose wear op (obj param 148, default 2) is a
     * Wield / Wear / Equip text; bit n of varp 263 = food or a potion (iop1 "Eat" / "Drink"). Varps 261 (rune and essence
     * pouches, coal bag: "Empty" / "Fill") and 264 ("Fill") stay clear: OPEN-B4b.
     */
    private fun PlayerRef.setBanksideExtraOps(backpack: InvView) {
        var wear = 0
        var consume = 0
        for (slot in 0 until backpack.size) {
            val obj = backpack.obj(slot)
            if (obj < 0) continue
            if (wearOp(obj)) {
                wear = wear or (1 shl slot)
            } else if (consumeOp(obj)) {
                consume = consume or (1 shl slot)
            }
        }
        setVarp(Varps.IF1, 0)
        setVarp(Varps.IF2, wear)
        setVarp(Varps.IF3, consume)
        setVarp(Varps.IF4, 0)
    }

    private fun PlayerRef.wearOp(obj: Int): Boolean {
        if (objWearPos(obj) < 0) return false
        val index = objParam(obj, PARAM_WEAR_OP_INDEX, DEFAULT_WEAR_OP_INDEX) - 1
        return objIop(obj, index)?.lowercase() in WEAR_VERBS
    }

    private fun PlayerRef.consumeOp(obj: Int): Boolean = objIop(obj, 0)?.lowercase() in CONSUME_VERBS

    /** IF_SETEVENTS of every bank component with item ops (client scripts 278, 297, 506, 507; RS Mod `setBankIfEvents`). */
    private fun PlayerRef.enableEvents() {
        val bankSize = inv(Invs.BANK, stackAll = true).size
        val last = bankSize - 1
        // The grid's children are exactly the inv's slots; nothing is created after the last one in rev 242 (OPEN-B8).
        ifEvents(Components.Bankmain.ITEMS, 0..last, MAIN_OPS or IfEvents.DEPTH2 or IfEvents.DRAG_TARGET)
        val mainTab = IfEvents.op(1) or IfEvents.op(OP_RELEASE) or IfEvents.DRAG_TARGET
        ifEvents(Components.Bankmain.TABS, TAB_CHILD_MAIN..TAB_CHILD_MAIN, mainTab)
        val otherTabs = mainTab or IfEvents.op(OP_COLLAPSE) or IfEvents.DEPTH1
        ifEvents(Components.Bankmain.TABS, TAB_CHILD_FIRST..TAB_CHILD_LAST, otherTabs)
        ifEvents(Components.Bankside.ITEMS, 0 until inv(Invs.INV).size, SIDE_OPS or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET)
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
        if (click.op == OP_EXAMINE) {
            if (validSlot(bank, slot, obj)) Examines.item(this, obj, bank.count(slot))
            return
        }
        if (!validSlot(bank, slot, obj)) return
        if (isPlaceholder(obj)) return clearSlots("bank release placeholder", listOf(slot))
        if (obj == Objs.BANK_FILLER) return fillerOp(click.op, slot)
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

            OP_SIDE_EXTRA -> extraOp(slot, obj)

            // Op 10 is "Lock-slot" while deposit locks are on (script 297); they are not implemented, so it is Examine.
            OP_SIDE_EXAMINE -> Examines.item(this, obj, inv(Invs.INV).count(slot))
        }
    }

    /**
     * The backpack column's ninth op (script 2576 shows the obj's wear or eat / drink text): the request goes to
     * content/combat as [EquipRequest] / [ConsumeRequest], which must act without closing the bank (OPEN-B4). A request
     * nobody handles does nothing.
     */
    private fun PlayerRef.extraOp(slot: Int, obj: Int) {
        if (!bankOpen() || varbit(Varbits.BANK_HIDESIDEOPS) == 1) return
        val events = bus ?: return
        if (wearOp(obj)) {
            events.publish(EquipRequest(this, Invs.INV, slot, obj))
        } else if (consumeOp(obj)) {
            events.publish(ConsumeRequest(this, Invs.INV, slot, obj))
        }
    }

    /** Deposits [requested] of the backpack obj in [slot] (all copies count, as in RS Mod's `transfer`). */
    private fun PlayerRef.depositOne(slot: Int, obj: Int, requested: Int) {
        if (!bankOpen() || requested <= 0 || inv(Invs.INV).obj(slot) != obj) return
        // B22: potions and empty vials go to the potion store when it is unlocked and asked for.
        if (potions.takes(this, obj)) return potions.deposit(this, listOf(obj to requested))
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
        // B22: what the potion store takes is split off first; the rest goes to the bank as before.
        val toStore = if (source == Invs.INV) wants.filter { potions.takes(this, it.first) } else emptyList()
        if (toStore.isNotEmpty()) potions.deposit(this, toStore)
        wants.removeAll(toStore)
        if (wants.isEmpty()) return
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
            OP_WORN_EXAMINE -> Examines.item(this, obj, worn.count(wornSlot))

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

    // ---- bank fillers

    /** A bank filler's ops (script 278): "Clear" removes this one, "Clear-All" every filler of the bank. */
    private fun PlayerRef.fillerOp(op: Int, slot: Int) {
        when (op) {
            OP_FILLER_CLEAR -> {
                clearSlots("bank clear filler", listOf(slot))
            }

            // B16 / B9b: asked first (RS Mod `removeFillers`); the bank is read again after the answer.
            OP_FILLER_CLEAR_ALL -> {
                confirmOverlay(
                    Components.Bankmain.POPUP,
                    "Clear all fillers?",
                    CLEAR_ALL_TEXT,
                    "<col=ff0000>Cancel</col>",
                    "<col=0dc10d>Clear them all</col>",
                ) { yes ->
                    val now = inv(Invs.BANK, stackAll = true)
                    if (yes && bankOpen()) {
                        clearSlots("bank clear fillers", (0 until BankLayout.used(now)).filter { now.obj(it) == Objs.BANK_FILLER })
                    }
                }
            }
        }
    }

    /** The "Fill" button: adds the fillers of the selected amount (RS Mod `addBankFillers`). */
    private fun PlayerRef.fill() {
        if (!bankOpen()) return
        when (varbit(Varbits.BANK_FILLERMODE)) {
            FILLER_ONE -> addFillers(1)
            FILLER_TEN -> addFillers(TEN)
            FILLER_FIFTY -> addFillers(FIFTY)
            FILLER_X -> countDialog("Enter amount:") { n -> addFillers(n) }
            else -> addFillers(Int.MAX_VALUE)
        }
    }

    /**
     * Puts up to [requested] fillers, one per slot, into the free slots after the last item of the main tab, within the
     * capacity ("Bank fillers will now fill only the accessible part of your bank", Wiki "Bank", 5 October 2017).
     */
    private fun PlayerRef.addFillers(requested: Int) {
        if (!bankOpen() || requested <= 0) return
        val bank = inv(Invs.BANK, stackAll = true)
        val used = BankLayout.used(bank)
        val count = minOf(requested, capacity(bank) - used)
        if (count <= 0) return message("Your bank is already full, so there is no reason to add any bank fillers.")
        if (!transaction("bank fillers") { (0 until count).all { setAt(Invs.BANK, used + it, Objs.BANK_FILLER, 1) } }) return
        message("You add $count bank ${if (count == 1) "filler" else "fillers"} to your bank.")
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
         * Withdraw ops on the bank grid, numbered as client script 278 / 669 set them (rev 242 has "Configure-Charges" as op 9,
         * which moves the placeholder op to 10 and Examine to 11 against RS Mod): left-click quantity, 1, 5, 10, last X, X,
         * All, All-but-1.
         */
        const val OP_LEFT = 1
        const val OP_ONE = 2
        const val OP_FIVE = 3
        const val OP_TEN = 4
        const val OP_LAST_X = 5
        const val OP_X = 6
        const val OP_ALL = 7
        const val OP_ALL_BUT_ONE = 8

        /** Op 9 "Configure-Charges" (bank item options, `bank_bankops_toggle_on`): OPEN-B14, not handled. */
        const val OP_PLACEHOLDER = 10

        /** A bank filler's own ops (script 278): "Clear" one, "Clear-All". */
        const val OP_FILLER_CLEAR = 7
        const val OP_FILLER_CLEAR_ALL = 8

        const val RELEASE_ALL_TEXT =
            "This option will release <col=ffb83f>all</col> the placeholders throughout your <col=ffb83f>whole bank</col>." +
                "<br><br>Are you sure?"
        const val CLEAR_ALL_TEXT =
            "This option will clear <col=ffb83f>all</col> the bank fillers throughout your <col=ffb83f>whole bank</col>." +
                "<br><br>Are you sure?"

        /** Deposit ops on the backpack grid (RS Mod `sideInvOp`): left-click quantity, 1, 5, 10, last X, X, All. */
        const val OP_SIDE_LEFT = 2
        const val OP_SIDE_ONE = 3
        const val OP_SIDE_FIVE = 4
        const val OP_SIDE_TEN = 5
        const val OP_SIDE_LAST_X = 6
        const val OP_SIDE_X = 7
        const val OP_SIDE_ALL = 8

        /** "Examine" is op 11 on the bank grid (script 278) and op 10 on the backpack column and the worn column (script 297). */
        const val OP_EXAMINE = 11
        const val OP_SIDE_EXAMINE = 10
        const val OP_WORN_EXAMINE = 10

        /** The backpack column's ninth op (script 297 / 2576): wield, wear, eat or drink without leaving the bank. */
        const val OP_SIDE_EXTRA = 9

        /** Tab ops: 6 collapses a tab, 7 releases its placeholders (RS Mod `selectTab`). */
        const val OP_COLLAPSE = 6
        const val OP_RELEASE = 7

        /** Sub-component of the main tab in `bankmain:tabs`; numbered tabs follow (RS Mod `BankSubComponents`). */
        const val TAB_CHILD_MAIN = 10
        const val TAB_CHILD_FIRST = 11
        const val TAB_CHILD_LAST = 19

        /** Rows of the tab display dropdown (`bankmain:dropdown_content` children 0..8; RS Mod `setBankIfEvents`). */
        const val DROPDOWN_LAST = 8
        val TAB_DISPLAY_CHILDREN = setOf(1, 3, 5)

        /** Ops 1..11 of the bank grid (scripts 278 / 669 set up to op 11) and 1..10 of the backpack column (script 297). */
        val MAIN_OPS: Long = (1..OP_EXAMINE).fold(0L) { acc, op -> acc or IfEvents.op(op) }
        val SIDE_OPS: Long = (1..OP_SIDE_EXAMINE).fold(0L) { acc, op -> acc or IfEvents.op(op) }

        /**
         * `bank_fillermode` values: client script 2213 shows 1..4 as "1", "10", "50", "X" and anything else as "All"
         * (RS Mod `BankFillerMode`).
         */
        const val FILLER_ALL = 0
        const val FILLER_ONE = 1
        const val FILLER_TEN = 2
        const val FILLER_FIFTY = 3
        const val FILLER_X = 4

        /** Amounts of the filler modes that have a fixed one (the wiki: "quantities of 1, 10, 50, X or All"). */
        const val FIFTY = 50

        /** Client script `tooltip` (cache script 1495: text, hover layer, tooltip box; RS Mod `ClientScripts.tooltip`). */
        const val CS_TOOLTIP = 1495

        /** RS Mod `BankOpenScript.onBankOpen` (members' 800, up to 400 more from a banker: OPEN-B1). */
        const val CAPACITY_TOOLTIP = "Members' capacity: 800<br>A banker can sell you up to 400 more."

        /** Obj param 148 `wear_op_index`: the 1-based iop that equips the obj (cache default 2); script 2576. */
        const val PARAM_WEAR_OP_INDEX = 148
        const val DEFAULT_WEAR_OP_INDEX = 2
        val WEAR_VERBS = setOf("wield", "wear", "equip")
        val CONSUME_VERBS = setOf("eat", "drink")

        /** The bank's worn items page (`bankmain:stabatt` ...; names as RS Mod `BankInterfaces`, defence lines named as their text). */
        val WORN_PAGE =
            BonusComponents(
                stab = Components.Bankmain.STABATT,
                slash = Components.Bankmain.SLASHATT,
                crush = Components.Bankmain.CRUSHATT,
                magic = Components.Bankmain.MAGICATT,
                range = Components.Bankmain.RANGEATT,
                speedBase = Components.Bankmain.ATTACKSPEEDBASE,
                speedActual = Components.Bankmain.ATTACKSPEEDACTUAL,
                defStab = Components.Bankmain.STABDEF,
                defSlash = Components.Bankmain.SLASHDEF,
                defCrush = Components.Bankmain.CRUSHDEF,
                defMagic = Components.Bankmain.MAGICDEF,
                defRange = Components.Bankmain.RANGEDEF,
                meleeStrength = Components.Bankmain.MELEESTRENGTH,
                rangedStrength = Components.Bankmain.RANGESTRENGTH,
                magicDamage = Components.Bankmain.MAGICDAMAGE,
                prayer = Components.Bankmain.PRAYER,
                undead = Components.Bankmain.TYPEMULTIPLIER,
                slayer = Components.Bankmain.SLAYERMULTIPLIER,
                tooltip = Components.Bankmain.TOOLTIP,
            )

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
