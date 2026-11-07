package tp.content.skills

import tp.plugin.BankItemOp
import tp.plugin.BankTransfer
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * The coal bag and the gem bag (docs/mechanics/motherlode.md ML13): Fill / Empty / Check / Open / Close. The coal bag
 * holds 27 coal (36 with the Smithing cape), the gem bag 60 of each uncut sapphire, emerald, ruby, diamond and dragonstone
 * (Wiki "Coal bag", "Gem bag"). Contents are saved per player, not per bag item (one bag at a time, as on the Wiki).
 */
public class Bags : ContentPlugin {
    override fun ContentScope.register() {
        for (bag in intArrayOf(Objs.COAL_BAG, Objs.COAL_BAG_OPEN)) {
            onItemOp(bag, "Fill") { holds(this, it, Objs.COAL_BAG, Objs.COAL_BAG_OPEN)?.let { fillCoal(this) } }
            onItemOp(bag, "Empty") { holds(this, it, Objs.COAL_BAG, Objs.COAL_BAG_OPEN)?.let { emptyCoal(this) } }
            onItemOp(bag, "Check") { holds(this, it, Objs.COAL_BAG, Objs.COAL_BAG_OPEN)?.let { checkCoal(this) } }
        }
        for (bag in intArrayOf(Objs.GEM_BAG, Objs.GEM_BAG_OPEN)) {
            onItemOp(bag, "Fill") { holds(this, it, Objs.GEM_BAG, Objs.GEM_BAG_OPEN)?.let { fillGems(this) } }
            onItemOp(bag, "Empty") { holds(this, it, Objs.GEM_BAG, Objs.GEM_BAG_OPEN)?.let { emptyGems(this) } }
            onItemOp(bag, "Check") { holds(this, it, Objs.GEM_BAG, Objs.GEM_BAG_OPEN)?.let { checkGems(this) } }
        }
        events.on<BankItemOp> { op -> if (op.obj == Objs.COAL_BAG || op.obj == Objs.COAL_BAG_OPEN) bankCoal(op) }
        onItemOp(Objs.COAL_BAG, "Open") { toggle(this, it, Objs.COAL_BAG, Objs.COAL_BAG_OPEN, "coal") }
        onItemOp(Objs.COAL_BAG_OPEN, "Close") { toggle(this, it, Objs.COAL_BAG_OPEN, Objs.COAL_BAG, "coal") }
        onItemOp(Objs.GEM_BAG, "Open") { toggle(this, it, Objs.GEM_BAG, Objs.GEM_BAG_OPEN, "gem") }
        onItemOp(Objs.GEM_BAG_OPEN, "Close") { toggle(this, it, Objs.GEM_BAG_OPEN, Objs.GEM_BAG, "gem") }
    }

    private fun holds(p: PlayerRef, click: ButtonClick, closed: Int, open: Int): Unit? {
        val inv = p.inv(Invs.INV)
        val ok = click.slot in 0 until inv.size && inv.obj(click.slot) == click.obj && (click.obj == closed || click.obj == open)
        return if (ok) Unit else null
    }

    private fun toggle(p: PlayerRef, click: ButtonClick, from: Int, to: Int, what: String) {
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != from) return
        if (!p.transaction("bag toggle") { replaceAt(Invs.INV, click.slot, from, to) }) return
        p.message(if (to == Objs.COAL_BAG_OPEN || to == Objs.GEM_BAG_OPEN) "You open your $what bag." else "You close your $what bag.")
    }

    // ---- coal

    /** Bank B30: "Fill" takes coal from the bank into the bag, "Empty" puts the bag's coal into the bank. */
    private fun bankCoal(op: BankItemOp) {
        op.handled = true
        val p = op.player
        if (op.text == "Fill") {
            val room = coalCapacity(p) - coal(p)
            if (room <= 0) return p.message("Your coal bag is full.")
            val t = BankTransfer(p, Objs.COAL, room, deposit = false, onMoved = { n -> p.setSaved(COAL_KEY, (coal(p) + n).toLong()) })
            p.events.publish(t)
            if (t.moved == 0) p.message("You don't have any coal in your bank.")
        } else {
            if (coal(p) == 0) return p.message("Your coal bag is empty.")
            p.events.publish(
                BankTransfer(p, Objs.COAL, coal(p), deposit = true, onMoved = { n ->
                    p.setSaved(
                        COAL_KEY,
                        (coal(p) - n).toLong().takeIf {
                            it >
                                0
                        },
                    )
                }),
            )
        }
    }

    private fun fillCoal(p: PlayerRef) {
        val room = coalCapacity(p) - coal(p)
        val have = p.inv(Invs.INV).total(Objs.COAL).toInt()
        if (room <= 0) return p.message("Your coal bag is full.")
        if (have == 0) return p.message("You don't have any coal to put in your bag.")
        val n = minOf(room, have)
        if (p.transaction("coal bag") { remove(Invs.INV, Objs.COAL, n) }) p.setSaved(COAL_KEY, (coal(p) + n).toLong())
    }

    private fun emptyCoal(p: PlayerRef) {
        val n = minOf(coal(p), p.inv(Invs.INV).freeSlots)
        if (coal(p) == 0) return p.message("Your coal bag is empty.")
        if (n == 0) return p.message("You don't have enough free space to empty your bag.")
        if (p.transaction("coal bag") { add(Invs.INV, Objs.COAL, n) }) p.setSaved(COAL_KEY, (coal(p) - n).toLong().takeIf { it > 0 })
    }

    private fun checkCoal(p: PlayerRef) {
        val n = coal(p)
        p.message(if (n == 0) "Your coal bag is empty." else "Your coal bag contains $n pieces of coal.")
    }

    // ---- gems

    private fun fillGems(p: PlayerRef) {
        var moved = 0
        var packed = p.saved(GEMS_KEY) ?: 0L
        for ((i, gem) in GEMS.withIndex()) {
            val room = GEM_CAPACITY - gemCount(packed, i)
            val n = minOf(room, p.inv(Invs.INV).total(gem).toInt())
            if (n <= 0 || !p.transaction("gem bag") { remove(Invs.INV, gem, n) }) continue
            packed = packed + (n.toLong() shl (i * GEM_BITS))
            moved += n
        }
        if (moved == 0) return p.message("You don't have any gems to put in your bag.")
        p.setSaved(GEMS_KEY, packed)
        p.message(if (moved == 1) "You add the gem to your bag." else "You add the gems to your bag.")
    }

    private fun emptyGems(p: PlayerRef) {
        var packed = p.saved(GEMS_KEY) ?: 0L
        if (packed == 0L) return p.message("Your gem bag is empty.")
        for ((i, gem) in GEMS.withIndex()) {
            val n = minOf(gemCount(packed, i), p.inv(Invs.INV).freeSlots)
            if (n <= 0 || !p.transaction("gem bag") { add(Invs.INV, gem, n) }) continue
            packed = packed - (n.toLong() shl (i * GEM_BITS))
        }
        p.setSaved(GEMS_KEY, packed.takeIf { it != 0L })
    }

    private fun checkGems(p: PlayerRef) {
        val packed = p.saved(GEMS_KEY) ?: 0L
        val text = GEMS.indices.joinToString(", ") { "${gemCount(packed, it)} ${p.objName(GEMS[it]).removePrefix("Uncut ").lowercase()}" }
        p.message("Your gem bag contains: $text.")
    }

    internal companion object {
        const val COAL_KEY = "bags.coal"
        const val GEMS_KEY = "bags.gems"

        /** Wiki "Coal bag": 27 coal, 36 with a Smithing cape equipped. */
        private const val COAL_CAPACITY = 27
        private const val COAL_CAPACITY_CAPE = 36

        /** Wiki "Gem bag": 60 of each of the five gems. */
        private const val GEM_CAPACITY = 60
        private const val GEM_BITS = 8
        private const val GEM_MASK = 0xFF

        /** Worn-inventory slot of the cape (cache wear position 1). */
        private const val CAPE_SLOT = 1

        /** Gem bag contents order (Wiki "Gem bag"). */
        val GEMS = intArrayOf(Objs.UNCUT_SAPPHIRE, Objs.UNCUT_EMERALD, Objs.UNCUT_RUBY, Objs.UNCUT_DIAMOND, Objs.UNCUT_DRAGONSTONE)

        private fun gemCount(packed: Long, i: Int): Int = ((packed shr (i * GEM_BITS)) and GEM_MASK.toLong()).toInt()

        fun coal(p: PlayerRef): Int = (p.saved(COAL_KEY) ?: 0L).toInt()

        fun coalCapacity(p: PlayerRef): Int {
            val cape = p.inv(Invs.WORN).obj(CAPE_SLOT)
            return if (cape == Objs.SKILLCAPE_SMITHING || cape == Objs.SKILLCAPE_SMITHING_TRIMMED) COAL_CAPACITY_CAPE else COAL_CAPACITY
        }

        fun hasCoalBag(p: PlayerRef): Boolean = p.inv(Invs.INV).let { it.total(Objs.COAL_BAG) + it.total(Objs.COAL_BAG_OPEN) > 0 }

        /**
         * Moves coal from the bag into the backpack so that [needed] coal is in it (Wiki "Coal bag": "coal in the bag will be used
         * after the coal in the inventory"). Returns the coal now in the backpack.
         */
        fun topUpCoal(p: PlayerRef, needed: Int): Int {
            val have = p.inv(Invs.INV).total(Objs.COAL).toInt()
            if (have >= needed || !hasCoalBag(p)) return have
            val n = minOf(needed - have, coal(p), p.inv(Invs.INV).freeSlots)
            if (n > 0 &&
                p.transaction("coal bag") { add(Invs.INV, Objs.COAL, n) }
            ) {
                p.setSaved(COAL_KEY, (coal(p) - n).toLong().takeIf { it > 0 })
            }
            return p.inv(Invs.INV).total(Objs.COAL).toInt()
        }

        /** A mined [obj] goes straight into an open bag when it fits (Wiki "Coal bag", "Gem bag": the open bag stores mined items). */
        fun autoStore(p: PlayerRef, obj: Int) {
            val inv = p.inv(Invs.INV)
            if (obj == Objs.COAL && inv.total(Objs.COAL_BAG_OPEN) > 0 && coal(p) < coalCapacity(p)) {
                if (p.transaction("coal bag") { remove(Invs.INV, Objs.COAL, 1) }) {
                    p.setSaved(COAL_KEY, (coal(p) + 1).toLong())
                    p.message("You put it straight into your open coal bag.")
                }
                return
            }
            val i = GEMS.indexOf(obj)
            if (i < 0 || inv.total(Objs.GEM_BAG_OPEN) == 0L) return
            val packed = p.saved(GEMS_KEY) ?: 0L
            if (gemCount(packed, i) >= GEM_CAPACITY) return
            if (p.transaction("gem bag") { remove(Invs.INV, obj, 1) }) {
                p.setSaved(GEMS_KEY, packed + (1L shl (i * GEM_BITS)))
                p.message("You put it straight into your open gem bag.")
            }
        }
    }
}
