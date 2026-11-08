package tp.content.gameframe

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.commands.economyToolsAllowed
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The bank's potion store (docs/mechanics/bank.md B20-B26): potion doses, unfinished potions and empty vials kept in varps
 * instead of bank slots. The client draws the list itself from those varps (client scripts 6080, 6081, 6360); the server
 * keeps the varps, deposits into them, withdraws out of them and answers the row ops. Owned by [Bank], which routes
 * deposits here. Every item movement is one item transaction; the varps follow right after the commit in the same tick
 * (the amounts are capped beforehand so they cannot fail).
 */
internal class PotionStore {
    private var catalog: PotionCatalog? = null

    fun ContentScope.registerPotions() {
        onWorldStart { catalog = PotionCatalog(this) }
        onLogin { normaliseFavourites() }
        onButton(Components.Bankmain.POTIONSTORE_ITEMS) { click -> item(click) }
        onButton(Components.Bankmain.POTIONSTORE_BUTTON) { toggleTab() }
        onButton(Components.Bankmain.DEPOSITPOTION_TOGGLE) { toggle(Varbits.BANK_DEPOSITPOTION) }
        onButton(Components.Bankmain.POPUP_TAB_FORGETPOS) { toggle(Varbits.BANK_SETTING_FORGET_POPUP) }
        // Stands in for buying the unlock from the Mixology Rewards shop (OPEN-B5d): administrators only.
        onCommand("potionstore") {
            if (!economyToolsAllowed) {
                message("You do not have permission to use that command.")
            } else {
                setVarbit(Varbits.MM_POTION_STORAGE_UNLOCKED, 1 - varbit(Varbits.MM_POTION_STORAGE_UNLOCKED))
                normaliseFavourites()
                message("Potion storage unlocked: ${varbit(Varbits.MM_POTION_STORAGE_UNLOCKED) == 1}.")
            }
        }
    }

    // ---- bank open

    /**
     * Called when the bank opens: enables the ops of the list's children (IF_SETEVENTS, as for the other bank grids), and
     * leaves the store tab if it is not available or the "forget storage tab" setting is on (OPEN-B5f).
     */
    fun onOpen(p: PlayerRef) {
        val rows = catalog?.families?.size ?: return
        p.ifEvents(Components.Bankmain.POTIONSTORE_ITEMS, 0 until rows * ROW_CHILDREN + TAIL_CHILDREN, ROW_OPS)
        if (!p.unlocked()) {
            if (p.onStoreTab()) p.setVarbit(Varbits.BANK_CURRENTTAB, 0)
            return
        }
        p.normaliseFavourites()
        if (p.onStoreTab() && p.varbit(Varbits.BANK_SETTING_FORGET_POPUP) == 1) p.setVarbit(Varbits.BANK_CURRENTTAB, 0)
    }

    // ---- deposits (called by Bank)

    /** Whether a deposit of [obj] from the backpack goes to the store: unlocked, storable, and the setting or the store tab asks for it. */
    fun takes(p: PlayerRef, obj: Int): Boolean {
        val cat = catalog ?: return false
        if (!p.unlocked() || obj !in cat.deposits) return false
        return p.varbit(Varbits.BANK_DEPOSITPOTION) == 1 || p.onStoreTab()
    }

    private class Step(val obj: Int, val amount: Int, val deposit: PotionCatalog.Deposit)

    /**
     * Stores up to the requested count of each (obj, count) of [wants] from the backpack: the amount is capped by how many
     * of that exact obj the backpack holds. Potions add their doses to the family's varp and one vial each; an empty vial
     * adds one vial. A family with no doses stored takes the dose count of its first potion as the dose size to withdraw.
     */
    fun deposit(p: PlayerRef, wants: List<Pair<Int, Int>>) {
        val cat = catalog ?: return
        val backpack = p.inv(Invs.INV)
        val pending = HashMap<Int, Long>()
        var vials = 0L
        val steps = ArrayList<Step>()
        for ((obj, requested) in wants) {
            val deposit = cat.deposits[obj] ?: continue
            var amount = minOf(requested.toLong(), backpack.total(obj))
            amount = minOf(amount, Int.MAX_VALUE - p.vials() - vials)
            if (deposit.family >= 0) {
                val held = p.varp(cat.families[deposit.family].entry.doseVarp).toLong() + pending.getOrDefault(deposit.family, 0L)
                amount = minOf(amount, (Int.MAX_VALUE - held) / deposit.doses)
            }
            if (amount <= 0) continue
            steps += Step(obj, amount.toInt(), deposit)
            vials += amount
            if (deposit.family >= 0) pending.merge(deposit.family, amount * deposit.doses, Long::plus)
        }
        if (steps.isEmpty() || !p.transaction("potion store deposit") { steps.all { remove(Invs.INV, it.obj, it.amount) } }) return
        for (s in steps) {
            val family = s.deposit.family
            if (family >= 0) {
                val f = cat.families[family]
                val held = p.varp(f.entry.doseVarp)
                if (held == 0 && f.entry.sizeVarbit >= 0) p.setVarbit(f.entry.sizeVarbit, minOf(s.deposit.doses, f.maxDoses) - 1)
                p.setVarp(f.entry.doseVarp, held + s.amount * s.deposit.doses)
            }
            p.setVarp(Varps.POTIONSTORE_VIALS, p.vials() + s.amount)
        }
    }

    // ---- clicks

    /** The list's children: rows of [ROW_CHILDREN] (row, icon, name, doses, star) in cache order, then the headers' children. */
    private fun PlayerRef.item(click: ButtonClick) {
        val cat = catalog ?: return
        val child = click.slot
        val rows = cat.families.size * ROW_CHILDREN
        if (!storeOpen() || child < 0 || child >= rows + TAIL_CHILDREN) return
        if (child >= rows) return quantity(click.op, vials()) { n -> withdrawVials(n) }
        val family = child / ROW_CHILDREN
        when (child % ROW_CHILDREN) {
            CHILD_ROW -> quantity(click.op, potionsAvailable(cat.families[family])) { n -> withdraw(family, n) }
            CHILD_ICON -> setDoseSize(cat.families[family], click.op)
            CHILD_STAR -> if (click.op == 1) toggleFavourite(cat.families[family])
        }
    }

    /** Ops 1..8 of a row or the vials line (client scripts 6360 / 6081): left-click quantity, 1, 5, 10, last X, X, All, All-but-1. */
    private fun PlayerRef.quantity(op: Int, available: Int, then: PlayerRef.(Int) -> Unit) {
        when (op) {
            OP_LEFT -> {
                then(leftClickQuantity())
            }

            OP_ONE -> {
                then(1)
            }

            OP_FIVE -> {
                then(FIVE)
            }

            OP_TEN -> {
                then(TEN)
            }

            OP_LAST_X -> {
                then(lastQuantity())
            }

            OP_X -> {
                countDialog("Enter amount:") { n ->
                    if (n > 0) {
                        if (varbitFits(Varbits.BANK_REQUESTEDQUANTITY, n)) setVarbit(Varbits.BANK_REQUESTEDQUANTITY, n)
                        then(n)
                    }
                }
            }

            OP_ALL -> {
                then(Int.MAX_VALUE)
            }

            OP_ALL_BUT_ONE -> {
                then(available - 1)
            }
        }
    }

    /**
     * Withdraws up to [requested] potions of family [index], each of the family's dose size (the last one holds what is
     * left when everything is taken), one vial per potion, as many as the stored vials and the free backpack slots allow.
     */
    private fun PlayerRef.withdraw(index: Int, requested: Int) {
        val f = catalog?.families?.getOrNull(index) ?: return
        val doses = varp(f.entry.doseVarp)
        if (!storeOpen() || requested <= 0 || doses <= 0) return
        val vials = vials()
        if (vials <= 0) return message(NO_VIALS)
        val size = doseSize(f)
        val potions = potionsAvailable(f)
        val wanted = minOf(requested, potions)
        val room = inv(Invs.INV).freeSlots
        if (room <= 0) return message(NO_SPACE)
        val count = minOf(wanted, vials, room)
        val everything = count == potions
        val last = if (everything) doses - (potions - 1) * size else size
        val full = if (everything && last != size) count - 1 else count
        val taken = if (everything) doses else count * size
        val ok =
            transaction("potion store withdraw") {
                (full == 0 || add(Invs.INV, f.objs[size], full)) && (full == count || add(Invs.INV, f.objs[last], 1))
            }
        if (!ok) return
        setVarp(f.entry.doseVarp, doses - taken)
        setVarp(Varps.POTIONSTORE_VIALS, vials - count)
        if (count < minOf(wanted, vials)) message(NOT_ENOUGH_SPACE)
    }

    private fun PlayerRef.withdrawVials(requested: Int) {
        val cat = catalog ?: return
        val vials = vials()
        if (!storeOpen() || requested <= 0) return
        if (vials <= 0) return message(NO_VIALS)
        val room = inv(Invs.INV).freeSlots
        if (room <= 0) return message(NO_SPACE)
        val count = minOf(requested, vials, room)
        if (!transaction("potion store withdraw vials") { add(Invs.INV, cat.vialObj, count) }) return
        setVarp(Varps.POTIONSTORE_VIALS, vials - count)
        if (count < minOf(requested, vials)) message(NOT_ENOUGH_SPACE)
    }

    /** The icon's ops "1 Dose" .. "4 Dose" (ops 6..9) set how many doses each withdrawn potion holds (client script 6360). */
    private fun PlayerRef.setDoseSize(f: PotionCatalog.Family, op: Int) {
        val size = op - OP_DOSE_FIRST + 1
        if (f.entry.sizeVarbit < 0 || f.maxDoses < 2 || size !in 1..f.maxDoses) return
        setVarbit(f.entry.sizeVarbit, size - 1)
    }

    /** The star: remove the family from the favourites, or add it into the first free of the eight slots (client script 6558). */
    private fun PlayerRef.toggleFavourite(f: PotionCatalog.Family) {
        val obj = f.objs[1]
        val held = FAVOURITES.firstOrNull { varp(it) == obj }
        if (held != null) {
            setVarp(held, NO_FAVOURITE)
        } else if (varp(f.entry.doseVarp) > 0) {
            FAVOURITES.firstOrNull { varp(it) == NO_FAVOURITE }?.let { setVarp(it, obj) }
        }
    }

    /** The "Potion storage" button: opens the store tab, or goes back to the tab shown before (client script 504). */
    private fun PlayerRef.toggleTab() {
        if (!unlocked()) return
        val current = varbit(Varbits.BANK_CURRENTTAB)
        if (current == TAB_STORE) {
            val previous = varbit(Varbits.POTIONSTORE_PREVIOUS_TAB)
            if (previous in 0..BankLayout.TAB_COUNT) setVarbit(Varbits.BANK_CURRENTTAB, previous)
        } else if (current in 0..BankLayout.TAB_COUNT) {
            setVarbit(Varbits.POTIONSTORE_PREVIOUS_TAB, current)
            setVarbit(Varbits.BANK_CURRENTTAB, TAB_STORE)
        }
    }

    // ---- state

    private fun PlayerRef.toggle(id: Int) {
        setVarbit(id, 1 - varbit(id))
    }

    private fun PlayerRef.unlocked(): Boolean = varbit(Varbits.MM_POTION_STORAGE_UNLOCKED) == 1

    private fun PlayerRef.onStoreTab(): Boolean = varbit(Varbits.BANK_CURRENTTAB) == TAB_STORE

    private fun PlayerRef.storeOpen(): Boolean = isOpen(Interfaces.BANKMAIN) && unlocked() && onStoreTab()

    private fun PlayerRef.vials(): Int = varp(Varps.POTIONSTORE_VIALS).coerceAtLeast(0)

    /** Doses per withdrawn potion: the family's size varbit plus one (client script 4818), never above the largest potion. */
    private fun PlayerRef.doseSize(f: PotionCatalog.Family): Int =
        if (f.entry.sizeVarbit < 0) 1 else (varbit(f.entry.sizeVarbit) + 1).coerceIn(1, f.maxDoses)

    /** Potions a full withdrawal gives: the stored doses split by the dose size, the last one holding the rest. */
    private fun PlayerRef.potionsAvailable(f: PotionCatalog.Family): Int {
        val doses = varp(f.entry.doseVarp).toLong().coerceAtLeast(0L)
        val size = doseSize(f).toLong()
        return ((doses + size - 1) / size).toInt()
    }

    /** Favourite varps hold -1 when empty (client script 6075); a new account has 0 there, which the client counts as a favourite. */
    private fun PlayerRef.normaliseFavourites() {
        if (!unlocked()) return
        for (slot in FAVOURITES) if (varp(slot) == 0) setVarp(slot, NO_FAVOURITE)
    }

    /** `bank_quantity_type` (also read by the store's lines, client script 2579): 1, 5, 10, X, All. */
    private fun PlayerRef.leftClickQuantity(): Int =
        when (varbit(Varbits.BANK_QUANTITY_TYPE)) {
            QTY_FIVE -> FIVE
            QTY_TEN -> TEN
            QTY_X -> lastQuantity()
            QTY_ALL -> Int.MAX_VALUE
            else -> 1
        }

    private fun PlayerRef.lastQuantity(): Int = maxOf(1, varbit(Varbits.BANK_REQUESTEDQUANTITY))

    private companion object {
        /** `bank_currenttab` value of the potion store tab (client script 504: valid targets are 0..9 and 15). */
        const val TAB_STORE = 15

        /** Children per potion in `bankmain:potionstore_items`: row, icon, name, doses, star (client script 6360; 6558 divides by 5). */
        const val ROW_CHILDREN = 5
        const val CHILD_ROW = 0
        const val CHILD_ICON = 1
        const val CHILD_STAR = 4

        /**
         * Children after the potion rows (client script 6080): per section a filler cell (1) and a header (script 6081: 5 children,
         * the fifth is the "Vials" line), for the favourites, potions and unfinished potions sections, plus the tutorial text.
         */
        const val TAIL_CHILDREN = 3 * (1 + 5) + 1

        /** Row ops: withdraw 1..8 (script 6360), dose sizes 6..9, the star's op 1. */
        const val OP_LEFT = 1
        const val OP_ONE = 2
        const val OP_FIVE = 3
        const val OP_TEN = 4
        const val OP_LAST_X = 5
        const val OP_X = 6
        const val OP_ALL = 7
        const val OP_ALL_BUT_ONE = 8
        const val OP_DOSE_FIRST = 6
        const val OPS_LAST = 9
        val ROW_OPS: Long = (1..OPS_LAST).fold(0L) { acc, op -> acc or IfEvents.op(op) }

        const val FIVE = 5
        const val TEN = 10

        /** `bank_quantity_type` values 1, 5, 10, X, All = 0..4 (client script 2579). */
        const val QTY_FIVE = 1
        const val QTY_TEN = 2
        const val QTY_X = 3
        const val QTY_ALL = 4

        /** The eight favourite slots, `-1` when free (client scripts 6075, 6558). */
        val FAVOURITES =
            intArrayOf(
                Varps.POTIONSTORE_FAVOURITE_1,
                Varps.POTIONSTORE_FAVOURITE_2,
                Varps.POTIONSTORE_FAVOURITE_3,
                Varps.POTIONSTORE_FAVOURITE_4,
                Varps.POTIONSTORE_FAVOURITE_5,
                Varps.POTIONSTORE_FAVOURITE_6,
                Varps.POTIONSTORE_FAVOURITE_7,
                Varps.POTIONSTORE_FAVOURITE_8,
            )
        const val NO_FAVOURITE = -1

        /** Wiki "Potion storage": the message for a withdrawal with no vials stored. */
        const val NO_VIALS = "You don't have any vials."

        /** The bank's wording for a withdrawal without backpack room (B5); the store's own is OPEN-B5e. */
        const val NO_SPACE = "You don't have enough inventory space."
        const val NOT_ENOUGH_SPACE = "You don't have enough inventory space to withdraw that many."
    }
}
