package tp.content.skills

import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * Materials for house building taken from the backpack and then from the plank sack (docs/mechanics/construction.md CO28; Wiki
 * "Plank sack": planks "can be used directly from the sack"). Flat-packs still use the backpack only.
 */
internal object PohMaterials {
    /** How many of [obj] the player can use: the backpack's and, for planks, the sack's. */
    private fun available(p: PlayerRef, obj: Int): Long =
        p.inv(Invs.INV).total(obj) + if (PlankSack.owns(p)) PlankSack.countOf(p, obj).toLong() else 0L

    fun covers(p: PlayerRef, inputs: List<Ingredient>): Boolean = inputs.all { available(p, it.obj) >= it.count }

    /** Takes [inputs]: the backpack's items in one item transaction, then the rest from the sack. Nothing is taken if it fails. */
    fun take(p: PlayerRef, reason: String, inputs: List<Ingredient>): Boolean {
        if (!covers(p, inputs)) return false
        val fromBackpack = inputs.map { minOf(p.inv(Invs.INV).total(it.obj), it.count.toLong()).toInt() }
        val paid =
            p.transaction(reason) {
                inputs.withIndex().all { (i, input) -> fromBackpack[i] == 0 || remove(Invs.INV, input.obj, fromBackpack[i]) }
            }
        if (!paid) return false
        for ((i, input) in inputs.withIndex()) {
            val rest = input.count - fromBackpack[i]
            if (rest > 0) PlankSack.takeOf(p, input.obj, rest)
        }
        return true
    }
}
