package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Herblore (docs/mechanics/herblore.md): cleaning grimy herbs (the "Clean" item op), unfinished and finished potions
 * made by using one item on another through the make-X menu ([SkillMulti]) as a [Production] batch, and crushing
 * secondaries with the pestle and mortar. Every recipe is a row of `recipes.tsv` (OSRS Wiki `{{Recipe}}`).
 */
public class Herblore : ContentPlugin {
    private var cleaning: Map<Int, Recipe> = emptyMap()

    override fun ContentScope.register() {
        val recipes = RecipeTable.load().filter { it.stat == Stats.HERBLORE }
        val crushing = recipes.filter { it.tools == listOf(Objs.PESTLE_AND_MORTAR) }
        cleaning = recipes.filter { it.tools.isEmpty() && it.inputs.size == 1 }.associateBy { it.inputs.first().obj }
        onItemOp(CLEAN) { click -> clean(click) }
        for (r in crushing) {
            val material = r.inputs.first().obj
            onUseOnItem(Objs.PESTLE_AND_MORTAR, material) { crush(this, r) }
        }
        val mixes = recipes.filter { it.tools.isEmpty() && it.inputs.size == 2 }
        for (options in mixes.groupBy { pair(it) }.values) {
            val (a, b) = pair(options.first())
            onUseOnItem(a, b) { mix(this, options) }
        }
    }

    private fun pair(r: Recipe): Pair<Int, Int> = minOf(r.inputs[0].obj, r.inputs[1].obj) to maxOf(r.inputs[0].obj, r.inputs[1].obj)

    // ---- cleaning

    /**
     * "Clean" on a grimy herb: level check, then the herb becomes the clean one in place and gives xp at once; the other grimy herbs
     * of the kind follow one per [CLEAN_TICKS] (HB1, HB2).
     */
    private fun PlayerRef.clean(click: ButtonClick) {
        val inv = inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val r = cleaning[click.obj] ?: return
        interrupt() // inventory item actions interrupt (interactions.md I7)
        if (level(Stats.HERBLORE) < r.level) return message("You need a Herblore level of ${r.level} to clean this herb.")
        if (!cleanAt(this, r, click.slot)) return
        val levelled = !Production.award(this, Stats.HERBLORE, r.xp)
        val rest = inv.total(click.obj).toInt()
        if (rest == 0 || levelled) return
        Production.start(this, Batch(rest, NO_ANIM, CLEAN_TICKS) { cleanNext(this, r) }, CLEAN_TICKS)
    }

    /** One more herb of [r]: the first grimy one in the backpack. */
    private fun cleanNext(p: PlayerRef, r: Recipe): Boolean {
        val inv = p.inv(Invs.INV)
        val grimy = r.inputs.first().obj
        val slot = (0 until inv.size).firstOrNull { inv.obj(it) == grimy } ?: return false
        if (p.level(Stats.HERBLORE) < r.level || !cleanAt(p, r, slot)) return false
        return Production.award(p, Stats.HERBLORE, r.xp)
    }

    private fun cleanAt(p: PlayerRef, r: Recipe, slot: Int): Boolean {
        if (!p.transaction("herblore") { replaceAt(Invs.INV, slot, r.inputs.first().obj, r.output, 1) }) return false
        p.message("You clean the dirt from the ${p.objName(r.output).lowercase()}.", Skilling.SPAM)
        return true
    }

    // ---- potions

    /** Opens the make-X menu with the potions of [options] the player has both materials for. */
    private fun mix(p: PlayerRef, options: List<Recipe>) {
        val doable = options.filter { Production.hasMaterials(p, it) }.distinctBy { it.output }.take(SkillMulti.MAX_ITEMS)
        if (doable.isEmpty()) return p.message("You don't have the materials to make anything with that.")
        SkillMulti.open(
            p,
            SkillMulti.MAKE,
            "What would you like to make?",
            IntArray(doable.size) { doable[it].output },
        ) { index, quantity ->
            start(this, doable[index], quantity)
        }
    }

    private fun start(p: PlayerRef, r: Recipe, quantity: Int) {
        Production.shortfall(p, r)?.let { return p.message(it) }
        Production.start(p, Batch(quantity, Seqs.HUMAN_HERBING_VIAL, r.ticks) { mixOne(this, r) }, r.firstTicks)
    }

    /** One potion: both materials go, the potion comes, xp (a level-up ends the batch). */
    private fun mixOne(p: PlayerRef, r: Recipe): Boolean {
        Production.shortfall(p, r)?.let {
            p.message(it)
            return false
        }
        if (!p.transaction("herblore") { take(r.inputs) && add(Invs.INV, r.output, r.count) }) return false
        return Production.award(p, Stats.HERBLORE, r.xp)
    }

    // ---- pestle and mortar

    /**
     * Crushes every item of the kind in the backpack: the first at once, the second 2 ticks later, then one every 3 (HB3).
     * No level or xp is involved.
     */
    private fun crush(p: PlayerRef, r: Recipe) {
        val material = r.inputs.first().obj
        val count = p.inv(Invs.INV).total(material).toInt()
        if (count == 0) return
        p.interrupt()
        p.anim(Seqs.HUMAN_HERBING_GRIND)
        crushOne(p, r)
        if (count == 1) return
        Production.start(p, Batch(count - 1, Seqs.HUMAN_HERBING_GRIND, CRUSH_TICKS) { crushOne(this, r) }, CRUSH_SECOND_TICKS)
    }

    private fun crushOne(p: PlayerRef, r: Recipe): Boolean =
        p.transaction(
            "herblore",
        ) { take(r.inputs) && add(Invs.INV, r.output, r.count) }

    private companion object {
        /** Cache iop text of a grimy herb. */
        const val CLEAN = "Clean"

        /** OPEN-HB2: the other herbs clean automatically (Wiki "Herblore", change of 10 September 2020); the timing is not sourced. */
        const val CLEAN_TICKS = 1

        /** [Batch] animation meaning none. */
        const val NO_ANIM = -1

        /**
         * Crushing: "Auto-crushing ... is 0, then 2, then 3 ticks" (Wiki recipes of the dusts), "every three game ticks"
         * (Wiki "Pestle and mortar").
         */
        const val CRUSH_SECOND_TICKS = 2
        const val CRUSH_TICKS = 3
    }
}
