package tp.content.gameframe

import tp.content.gameframe.BankLayout.MAIN
import tp.plugin.ItemTx
import tp.plugin.PlayerRef
import tp.symbols.Interfaces
import tp.symbols.Invs
import kotlin.math.abs

/**
 * Rearranging the bank by dragging (docs/mechanics/bank.md B10-B12). The operations act on the packed layout of
 * [BankLayout]: a drag moves slots with swaps inside one item transaction and then updates the tab sizes. Slots come
 * straight from a client packet, so each operation re-checks them against the occupied range.
 * Called from `Bank`'s `onButtonDrag` handlers (the client's IF_BUTTOND; client scripts 278 and 506 create the draggable
 * grid slots and tab buttons): `bankmain:items` onto `bankmain:items` -> [swap] or [insert] by varbit `bank_insertmode`;
 * `bankmain:items` onto `bankmain:tabs` (child 10 = main, 11..19 = tabs 1..9, the first empty one is the "New tab" slot)
 * -> [intoTab]; `bankmain:tabs` onto `bankmain:tabs` -> [moveTab].
 */
internal object BankDrag {
    /** Swap mode: the two slots trade places; tab sizes do not change (RS Mod `dragMainInvSwap`). */
    fun swap(p: PlayerRef, from: Int, to: Int) {
        val used = open(p) ?: return
        if (from !in 0 until used || from == to) return
        // Dropping on empty space below the last item moves the item to the end of the main tab.
        if (to !in 0 until used) return intoTab(p, from, MAIN, sameTabOk = true)
        p.transaction("bank swap") { swap(Invs.BANK, from, to) }
    }

    /**
     * Insert mode: the item takes slot [to] and the items between shift by one (RS Mod `dragMainInvShift`). Moving forward
     * into another tab lands one slot earlier, because the removal shifts that tab left. Neighbouring slots swap.
     */
    fun insert(p: PlayerRef, from: Int, to: Int) {
        val used = open(p) ?: return
        if (from !in 0 until used || from == to) return
        if (to !in 0 until used) return intoTab(p, from, MAIN, sameTabOk = true)
        if (abs(from - to) == 1) return swap(p, from, to)
        val sizes = BankLayout.readSizes(p)
        val fromTab = BankLayout.tabOf(sizes, from)
        val toTab = BankLayout.tabOf(sizes, to)
        val final = if (from < to && fromTab != toTab) to - 1 else to
        if (!p.transaction("bank insert") { bubble(from, final) }) return
        if (fromTab != toTab) {
            if (fromTab != MAIN) sizes[fromTab]--
            if (toTab != MAIN) sizes[toTab]++
        }
        BankLayout.finish(p, sizes)
    }

    /**
     * Moves the item in [from] to the end of [tab] (drop on a tab, or on its "new tab" slot; RS Mod `dragMainInvExtendedSlot`).
     * A numbered tab can only be dropped on when it has items or is the first empty one, which starts a new tab. Dropping
     * on the item's own tab does nothing unless [sameTabOk].
     */
    fun intoTab(p: PlayerRef, from: Int, tab: Int, sameTabOk: Boolean = false) {
        val used = open(p) ?: return
        if (from !in 0 until used || tab !in 0..MAIN) return
        val sizes = BankLayout.readSizes(p)
        if (tab != MAIN && sizes[tab] == 0 && tab != sizes.count { it > 0 }) return
        val fromTab = BankLayout.tabOf(sizes, from)
        if (fromTab == tab && !sameTabOk) return
        val tabEnd = BankLayout.end(sizes, tab, used)
        val final = if (from < tabEnd) tabEnd - 1 else tabEnd
        if (final != from && !p.transaction("bank move to tab") { bubble(from, final) }) return
        if (fromTab != tab) {
            if (fromTab != MAIN) sizes[fromTab]--
            if (tab != MAIN) sizes[tab]++
        }
        BankLayout.finish(p, sizes)
    }

    /** Reorders two numbered tabs: [fromTab] goes where [toTab] is, the tabs in between shift (RS Mod `dragTab`). */
    fun moveTab(p: PlayerRef, fromTab: Int, toTab: Int) {
        val tabs = 0 until BankLayout.TAB_COUNT
        if (open(p) == null || fromTab == toTab) return
        if (fromTab !in tabs || toTab !in tabs) return
        val sizes = BankLayout.readSizes(p)
        if (sizes[fromTab] == 0 || sizes[toTab] == 0) return
        val first = BankLayout.start(sizes, fromTab)
        val end = first + sizes[fromTab]
        val moved =
            p.transaction("bank move tab") {
                if (fromTab < toTab) {
                    rotateBank(first, end, BankLayout.end(sizes, toTab, 0))
                } else {
                    rotateBank(BankLayout.start(sizes, toTab), first, end)
                }
            }
        if (!moved) return
        val order = (0 until BankLayout.TAB_COUNT).toMutableList()
        order.add(toTab, order.removeAt(fromTab))
        val selected = BankLayout.selectedTab(p)
        val reordered = IntArray(BankLayout.TAB_COUNT) { sizes[order[it]] }
        if (selected != MAIN) BankLayout.selectTab(p, order.indexOf(selected))
        BankLayout.finish(p, reordered)
    }

    /** Number of occupied slots if the bank is open, else null. */
    private fun open(p: PlayerRef): Int? =
        if (p.isOpen(Interfaces.BANKMAIN)) BankLayout.used(p.inv(Invs.BANK, stackAll = true)) else null

    /** Moves the item in [from] to [to], shifting the slots in between by one, with adjacent swaps. */
    private fun ItemTx.bubble(from: Int, to: Int): Boolean {
        var at = from
        while (at < to) {
            if (!swap(Invs.BANK, at, at + 1)) return false
            at++
        }
        while (at > to) {
            if (!swap(Invs.BANK, at, at - 1)) return false
            at--
        }
        return true
    }
}
