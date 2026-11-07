package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.BankTransfer
import tp.plugin.PlayerRef
import tp.plugin.RunePouch

/**
 * Rune pouch load-outs in the bank (docs/mechanics/rune-pouch.md RP11-RP14; Wiki "Rune pouch": "ten load-outs available
 * to customise by both the types and quantities of runes and a name for each preset"). Each load-out slot is a rune
 * compact id (enum 55) and an amount in the varbits script 4895 reads; names are enum 5865 keys in
 * `rune_pouch_loadout_name_a..j`.
 */
internal object RunePouchLoadouts {
    const val LOADOUTS = 10
    private const val SLOTS = RunePouch.MAX_SLOTS

    /** One stored value split over varbits: (varbit, shift) parts, highest first (script 4895's `<<` chains). */
    private class Field(vararg val parts: Pair<Int, Int>) {
        fun read(p: PlayerRef): Int = parts.sumOf { (varbit, shift) -> p.varbit(varbit) shl shift }

        fun write(p: PlayerRef, value: Int) {
            var upper = Int.MAX_VALUE
            for ((varbit, shift) in parts) {
                val part = (value shr shift) and if (upper == Int.MAX_VALUE) Int.MAX_VALUE else (1 shl (upper - shift)) - 1
                p.setVarbit(varbit, part)
                upper = shift
            }
        }
    }

    private fun f(varbit: Int) = Field(varbit to 0)

    private fun f(hi: Int, lo: Int) = Field(hi to LO_BITS, lo to 0)

    /** Script 4895: rune 1 / 2 of load-outs A, E, I span two varbits (the low part 4 bits). */
    private const val LO_BITS = 4

    /** Script 4895: the third amount of load-outs D and H spans three varbits (10 / 6 / 0). */
    private fun q3(a: Int, b: Int, c: Int) = Field(a to Q_HI, b to Q_MID, c to 0)

    private const val Q_HI = 10
    private const val Q_MID = 6

    /** Script 4895's varbits per load-out (A-J) and slot (1-4): rune then amount. */
    @Suppress("MagicNumber") // a table of cache varbit ids (script 4895)
    private val TABLE: Array<Array<Pair<Field, Field>>> =
        arrayOf(
            arrayOf(f(9725, 9717) to f(9686), f(9726, 9722) to f(9690), f(9681) to f(9696), f(9682) to f(9700)),
            arrayOf(f(9685) to f(9705), f(9687) to f(9707), f(9688) to f(9708), f(9689) to f(9712)),
            arrayOf(f(9691) to f(9713), f(9694) to f(9715), f(9695) to f(9716), f(9697) to f(9718)),
            arrayOf(f(9698) to f(9721), f(9699) to f(9723), f(9703) to q3(9714, 9709, 9706), f(9704) to f(9724)),
            arrayOf(f(18003, 17997) to f(17972), f(18004, 18000) to f(17976), f(17969) to f(17980), f(17970) to f(17984)),
            arrayOf(f(17971) to f(17987), f(17973) to f(17989), f(17974) to f(17990), f(17975) to f(17992)),
            arrayOf(f(17977) to f(17993), f(17978) to f(17995), f(17979) to f(17996), f(17981) to f(17998)),
            arrayOf(f(17982) to f(17999), f(17983) to f(18001), f(17985) to q3(17994, 17991, 17988), f(17986) to f(18002)),
            arrayOf(f(18021, 18020) to f(18008), f(18022, 18019) to f(18012), f(18005) to f(18013), f(18006) to f(18014)),
            arrayOf(f(18007) to f(18018), f(18009) to f(18015), f(18010) to f(18016), f(18011) to f(18017)),
        )

    /** `rune_pouch_loadout_name_a` (18024) .. `_j` (18033): enum 5865 keys. */
    private const val NAME_A = 18024

    /** The load-out slot (loadout * 4 + slot) waiting for a rune from the select list. */
    private val EDITING = AttrKey<Int>("rune_pouch_loadout_editing")

    /** The load-out waiting for a name from the name list. */
    private val NAMING = AttrKey<Int>("rune_pouch_loadout_naming")

    private const val COMPACT_ENUM = 55

    /** Cache enum 5864: the name list's sort order (index -> enum 5865 key). */
    private const val NAME_ORDER_ENUM = 5864

    fun rune(p: PlayerRef, loadout: Int, slot: Int): Pair<Int, Int>? {
        val (type, qty) = TABLE[loadout][slot]
        val id = type.read(p)
        val n = qty.read(p)
        if (id <= 0 || n <= 0) return null
        val rune =
            p
                .enumInts(COMPACT_ENUM)
                .entries
                .firstOrNull { it.value == id }
                ?.key ?: return null
        return rune to n
    }

    fun setRune(p: PlayerRef, loadout: Int, slot: Int, rune: Int, amount: Int) {
        val (type, qty) = TABLE[loadout][slot]
        val id = if (rune < 0 || amount <= 0) 0 else p.enumInts(COMPACT_ENUM)[rune] ?: 0
        type.write(p, id)
        qty.write(p, if (id == 0) 0 else minOf(amount, RunePouch.CAPACITY))
    }

    /**
     * RP12 "Load": the pouch's runes go to the bank, then each load-out rune comes out of the bank into the pouch (as many
     * as the bank holds, up to the load-out amount; OPEN-RP4: the real game's message for a short bank is unknown).
     */
    fun load(p: PlayerRef, loadout: Int) {
        val slots = RunePouch.slots(p)
        if (slots == 0 || loadout !in 0 until LOADOUTS) return
        val wanted = (0 until slots).mapNotNull { slot -> rune(p, loadout, slot)?.let { slot to it } }
        if (wanted.isEmpty()) return p.message("That load-out is empty.")
        for (slot in 0 until slots) {
            val (rune, held) = RunePouch.slot(p, slot) ?: continue
            p.events.publish(BankTransfer(p, rune, held, deposit = true, onMoved = { n -> RunePouch.remove(p, rune, n) }))
        }
        if ((0 until slots).any { RunePouch.slot(p, it) != null }) return p.message("You don't have enough space in your bank account.")
        var short = false
        // Each rune goes into the pouch slot it has in the load-out.
        for ((slot, want) in wanted) {
            val (rune, amount) = want
            val t = BankTransfer(p, rune, amount, deposit = false, onMoved = { n -> RunePouch.set(p, slot, rune, n) })
            p.events.publish(t)
            if (t.moved < amount) short = true
        }
        if (short) p.message("You don't have enough runes in your bank to fill that load-out.")
    }

    /** "Set" / "Change" on a load-out row's child [child] (1-4 = slots 1-4, script 4885): the rune list then picks its rune. */
    fun editSlot(p: PlayerRef, loadout: Int, child: Int) {
        if (child !in 1..SLOTS) return
        p[EDITING] = loadout * SLOTS + child - 1
    }

    /** The rune list's [child]: a rune compact id (enum 55; seen in game: nature rune = child 10), then its amount. */
    fun pickRune(p: PlayerRef, child: Int) {
        val editing = p[EDITING] ?: return
        p[EDITING] = null
        val loadout = editing / SLOTS
        val slot = editing % SLOTS
        if (child == 0) return setRune(p, loadout, slot, -1, 0)
        val rune =
            p
                .enumInts(COMPACT_ENUM)
                .entries
                .firstOrNull { it.value == child }
                ?.key ?: return
        p.countDialog("Enter amount:") { n -> if (n > 0) setRune(this, loadout, slot, rune, n) }
    }

    fun editName(p: PlayerRef, loadout: Int) {
        p[NAMING] = loadout
    }

    /** The name list's [child]: script 8092 stores enum 5864's value at index child - (enum 5864's size), an enum 5865 key. */
    fun pickName(p: PlayerRef, child: Int) {
        val loadout = p[NAMING] ?: return
        p[NAMING] = null
        val order = p.enumInts(NAME_ORDER_ENUM)
        val name = order[child - order.size] ?: return
        p.setVarbit(NAME_A + loadout, name)
    }
}
