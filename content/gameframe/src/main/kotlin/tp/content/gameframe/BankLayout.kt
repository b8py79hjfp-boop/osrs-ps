package tp.content.gameframe

import tp.plugin.InvView
import tp.plugin.ItemTx
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * Slot layout of the bank (inv 95). The client lays the bank out from the nine tab size varbits `bank_tab_1..9`: tab 1
 * takes the first `bank_tab_1` slots, tab 2 the next `bank_tab_2`, and so on; the main tab is every slot after the last
 * tab (RS Mod `BankTab`, which keeps the main size in a server-only varbit; here it is derived).
 *
 * This server keeps the occupied slots packed at the front of the inventory (no gaps): every step that empties a slot
 * closes the gap with swaps, every new item is appended at the end of its tab. RS Mod tolerates gaps and compacts on a
 * queue after the bank closes (`bank_compress`); packing immediately gives the same layout the client shows once that
 * has run. Every slot move is a swap or an edit inside one item transaction (brief §12); the tab size varbits are
 * written after the transaction commits.
 */
internal object BankLayout {
    /** Numbered tabs (`bank_tab_1..9`, RS Mod `BankTab.Tab1..Tab9`). */
    const val TAB_COUNT = 9

    /** Index of the main tab in tab arithmetic: after the nine numbered ones (RS Mod `BankTab.Main.index`). */
    const val MAIN = TAB_COUNT

    private val SIZE_VARBITS =
        intArrayOf(
            Varbits.BANK_TAB_1,
            Varbits.BANK_TAB_2,
            Varbits.BANK_TAB_3,
            Varbits.BANK_TAB_4,
            Varbits.BANK_TAB_5,
            Varbits.BANK_TAB_6,
            Varbits.BANK_TAB_7,
            Varbits.BANK_TAB_8,
            Varbits.BANK_TAB_9,
        )

    fun readSizes(p: PlayerRef): IntArray = IntArray(TAB_COUNT) { p.varbit(SIZE_VARBITS[it]) }

    fun writeSizes(p: PlayerRef, sizes: IntArray) {
        for (i in 0 until TAB_COUNT) p.setVarbit(SIZE_VARBITS[i], sizes[i])
    }

    /** The selected tab as a tab index: `bank_currenttab` is 0 for main and n for numbered tab n (RS Mod `BankTab.varValue`). */
    fun selectedTab(p: PlayerRef): Int = p.varbit(Varbits.BANK_CURRENTTAB).let { if (it in 1..TAB_COUNT) it - 1 else MAIN }

    fun selectTab(p: PlayerRef, tab: Int) {
        p.setVarbit(Varbits.BANK_CURRENTTAB, if (tab == MAIN) 0 else tab + 1)
    }

    /** Number of occupied slots (the first empty slot, as the layout has no gaps). */
    fun used(bank: InvView): Int {
        var n = 0
        while (n < bank.size && bank.obj(n) >= 0) n++
        return n
    }

    /** Slot of [obj] among the first [used] slots, or -1. */
    fun find(bank: InvView, obj: Int, used: Int): Int {
        if (obj < 0) return -1
        for (slot in 0 until used) if (bank.obj(slot) == obj) return slot
        return -1
    }

    /** First slot of [tab] (main starts after the last numbered tab). */
    fun start(sizes: IntArray, tab: Int): Int {
        var n = 0
        for (t in 0 until tab) n += sizes[t]
        return n
    }

    /** One past the last slot of [tab]; the main tab ends at [used]. */
    fun end(sizes: IntArray, tab: Int, used: Int): Int = if (tab == MAIN) used else start(sizes, tab) + sizes[tab]

    /** Tab holding [slot] (a slot past every numbered tab is in main). */
    fun tabOf(sizes: IntArray, slot: Int): Int {
        var first = 0
        for (tab in 0 until TAB_COUNT) {
            if (slot < first + sizes[tab]) return tab
            first += sizes[tab]
        }
        return MAIN
    }

    /** Reduces the size of the tab that held [slot] (before it was removed). */
    fun shrink(sizes: IntArray, slot: Int) {
        val tab = tabOf(sizes, slot)
        if (tab != MAIN) sizes[tab]--
    }

    /**
     * Moves emptied numbered tabs to the end, keeping the order of the rest (RS Mod `compactEmptyTabs`), and writes the
     * sizes. The selected tab follows its tab, or becomes main when it is gone (RS Mod `selectedTab = Main`).
     */
    fun finish(p: PlayerRef, sizes: IntArray) {
        val selected = p.varbit(Varbits.BANK_CURRENTTAB)
        val remap = IntArray(TAB_COUNT) { -1 }
        val packed = IntArray(TAB_COUNT)
        var n = 0
        for (tab in 0 until TAB_COUNT) {
            if (sizes[tab] <= 0) continue
            remap[tab] = n
            packed[n++] = sizes[tab]
        }
        writeSizes(p, packed)
        if (selected in 1..TAB_COUNT) {
            val moved = remap[selected - 1]
            p.setVarbit(Varbits.BANK_CURRENTTAB, if (moved < 0) 0 else moved + 1)
        }
    }

    /**
     * Makes the layout valid before the bank opens: gaps are closed (the numbered tabs then lose their meaning, so they
     * are folded into main), tab sizes that exceed the occupied slots are cleared, and a selected tab that is empty
     * falls back to main. Only a bank written by something else than this layout can need any of it.
     */
    fun tidy(p: PlayerRef) {
        val bank = p.inv(Invs.BANK, stackAll = true)
        var occupied = 0
        var last = -1
        for (slot in 0 until bank.size) {
            if (bank.obj(slot) < 0) continue
            occupied++
            last = slot
        }
        val sizes = readSizes(p)
        if (last + 1 != occupied) {
            p.transaction("bank tidy") {
                var write = 0
                for (read in 0..last) {
                    if (bank.obj(read) < 0) continue
                    if (write != read && !swap(Invs.BANK, write, read)) return@transaction false
                    write++
                }
                true
            }
            sizes.fill(0)
        } else if (sizes.sum() > occupied) {
            sizes.fill(0)
        }
        finish(p, sizes)
    }
}

/** Swaps slots lo..hi (inclusive) end for end. */
private fun ItemTx.reverseBank(lo: Int, hi: Int): Boolean {
    var a = lo
    var b = hi
    while (a < b) {
        if (!swap(Invs.BANK, a, b)) return false
        a++
        b--
    }
    return true
}

/**
 * Rotates two neighbouring blocks: [mid, hi) ends up in front of [lo, mid) (three reversals, about hi - lo swaps).
 */
internal fun ItemTx.rotateBank(lo: Int, mid: Int, hi: Int): Boolean {
    if (lo >= mid || mid >= hi) return true
    val tail = hi - mid
    return reverseBank(lo, hi - 1) && reverseBank(lo, lo + tail - 1) && reverseBank(lo + tail, hi - 1)
}

/** Closes the gap at [slot] (just emptied) by moving the later of the [used] items one slot to the left. */
internal fun ItemTx.closeBankGap(slot: Int, used: Int): Boolean {
    for (i in slot until used - 1) if (!swap(Invs.BANK, i, i + 1)) return false
    return true
}
