package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The plank sack (docs/mechanics/mahogany-homes.md MH9): it holds up to [CAPACITY] planks of any type, "Fill" moves the planks
 * of the backpack in, "Empty" moves them out, "Check" tells what is inside, and planks used for Mahogany Homes and sawmill
 * planks go through it. The counts are one saved value; the client's per-plank varbits (cache 2757 `plank_sack_plain` ..
 * `plank_sack_rosewood`) mirror them.
 */
public class PlankSack : ContentPlugin {
    override fun ContentScope.register() {
        onItemOp(Objs.PLANK_SACK, "Fill") { if (holds(this, it)) fill(this) }
        onItemOp(Objs.PLANK_SACK, "Empty") { if (holds(this, it)) empty(this) }
        onItemOp(Objs.PLANK_SACK, "Check") { if (holds(this, it)) check(this) }
        for (type in ConstructionData.PLANKS) onUseOnItem(type.plank, Objs.PLANK_SACK) { fill(this) }
        onLogin { sync(this) }
    }

    private fun holds(p: PlayerRef, click: ButtonClick): Boolean {
        val inv = p.inv(Invs.INV)
        return click.slot in 0 until inv.size && inv.obj(click.slot) == click.obj && click.obj == Objs.PLANK_SACK
    }

    private fun fill(p: PlayerRef) {
        var moved = 0
        for (type in ConstructionData.PLANKS) {
            val n = minOf(CAPACITY - total(p), p.inv(Invs.INV).total(type.plank).toInt())
            if (n > 0 && p.transaction("plank sack") { remove(Invs.INV, type.plank, n) }) {
                add(p, type, n)
                moved += n
            }
        }
        when {
            moved > 0 -> p.message("You add the planks to your sack.")
            total(p) >= CAPACITY -> p.message("Your sack is full.")
            else -> p.message("You have no planks to put in your sack.")
        }
    }

    private fun empty(p: PlayerRef) {
        if (total(p) == 0) return p.message("Your sack is empty.")
        for (type in ConstructionData.PLANKS) {
            val n = minOf(count(p, type), p.inv(Invs.INV).freeSlots)
            if (n > 0 && p.transaction("plank sack") { add(Invs.INV, type.plank, n) }) take(p, type, n)
        }
    }

    private fun check(p: PlayerRef) {
        if (total(p) == 0) return p.message("Your sack is empty.")
        val parts = ConstructionData.PLANKS.filter { count(p, it) > 0 }.map { "${count(p, it)} ${p.objName(it.plank).lowercase()}" }
        p.message("Your sack contains: ${parts.joinToString(", ")}.")
    }

    internal companion object {
        /** Wiki "Plank sack": "The sack holds a total of 28 planks of any type". */
        const val CAPACITY = 28

        private const val KEY = "construction.plank_sack"

        /** Bits per plank type in the saved value: 28 needs five. */
        private const val BITS = 5
        private const val MASK = (1L shl BITS) - 1

        /** The seven plank varbits in [ConstructionData.PLANKS] order (cache 2757 `plank_sack_plain` .. `plank_sack_rosewood`). */
        private val VARBITS =
            intArrayOf(
                Varbits.PLANK_SACK_PLAIN,
                Varbits.PLANK_SACK_OAK,
                Varbits.PLANK_SACK_TEAK,
                Varbits.PLANK_SACK_MAHOGANY,
                Varbits.PLANK_SACK_CAMPHOR,
                Varbits.PLANK_SACK_IRONWOOD,
                Varbits.PLANK_SACK_ROSEWOOD,
            )

        fun owns(p: PlayerRef): Boolean = p.inv(Invs.INV).total(Objs.PLANK_SACK) > 0

        fun count(p: PlayerRef, type: PlankType): Int =
            ((p.saved(KEY) ?: 0L) ushr (BITS * ConstructionData.PLANKS.indexOf(type))).toInt() and MASK.toInt()

        fun total(p: PlayerRef): Int = ConstructionData.PLANKS.sumOf { count(p, it) }

        /** Count of the sack's planks of obj [plank] (0 if it is not a plank type). */
        fun countOf(p: PlayerRef, plank: Int): Int = ConstructionData.PLANKS.firstOrNull { it.plank == plank }?.let { count(p, it) } ?: 0

        /** Puts up to [n] planks of [type] in the sack; returns how many fitted. */
        fun add(p: PlayerRef, type: PlankType, n: Int): Int {
            val fit = minOf(n, CAPACITY - total(p))
            if (fit > 0) write(p, type, count(p, type) + fit)
            return fit
        }

        fun take(p: PlayerRef, type: PlankType, n: Int) {
            write(p, type, (count(p, type) - n).coerceAtLeast(0))
        }

        /** Takes [n] planks of obj [plank] out of the sack (the caller checked [countOf]). */
        fun takeOf(p: PlayerRef, plank: Int, n: Int) {
            ConstructionData.PLANKS.firstOrNull { it.plank == plank }?.let { take(p, it, n) }
        }

        private fun write(p: PlayerRef, type: PlankType, value: Int) {
            val index = ConstructionData.PLANKS.indexOf(type)
            val old = p.saved(KEY) ?: 0L
            val next = (old and (MASK shl (BITS * index)).inv()) or (value.toLong() shl (BITS * index))
            p.setSaved(KEY, next.takeIf { it != 0L })
            sync(p)
        }

        private fun sync(p: PlayerRef) {
            for ((i, type) in ConstructionData.PLANKS.withIndex()) {
                if (p.varbitFits(VARBITS[i], count(p, type))) p.setVarbit(VARBITS[i], count(p, type))
            }
        }
    }
}
