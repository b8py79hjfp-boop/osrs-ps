package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * Fletching (docs/mechanics/fletching.md): a knife on logs, tipping, feathering and stringing, by using one item on another.
 * Every recipe is a row of `recipes.tsv` (OSRS Wiki `{{Recipe}}`). Most products are chosen in the make-X menu
 * ([SkillMulti]) and made as a [Production] batch; darts and bolts are made one set per use, instantly (FL3).
 */
public class Fletching : ContentPlugin {
    override fun ContentScope.register() {
        val recipes = RecipeTable.load().filter { it.stat == Stats.FLETCHING }
        for (options in recipes.groupBy { pair(it) }.values) {
            val (a, b) = pair(options.first())
            onUseOnItem(a, b) { use(this, options) }
        }
    }

    /**
     * The two backpack items that start [r], in a fixed order: its two materials, or its tool and its only material
     * (knife on logs, chisel on a gem). The Wiki's hammer for crossbows is not part of the click.
     */
    private fun pair(r: Recipe): Pair<Int, Int> {
        val items = if (r.inputs.size >= 2) r.inputs.map { it.obj } else r.tools.take(1) + r.inputs.map { it.obj }
        return minOf(items[0], items[1]) to maxOf(items[0], items[1])
    }

    private fun use(p: PlayerRef, options: List<Recipe>) {
        val first = options.first()
        if (first.ticks == FletchingData.INSTANT_TICKS) {
            val r = options.firstOrNull { Production.hasMaterials(p, it) } ?: first
            Production.shortfall(p, r)?.let { return p.message(it) }
            // One set per use: the animation plays and the set is made at once (FL3).
            animate(p, r)
            makeSet(p, r)
            return
        }
        val doable = options.filter { Production.hasMaterials(p, it) }.distinctBy { it.output }.take(SkillMulti.MAX_ITEMS)
        if (doable.isEmpty()) return p.message("You don't have the materials to make anything with that.")
        val stringing = first.inputs.any { it.obj in FletchingData.STRINGS }
        val sets = first.tools.isEmpty() && first.count > 1
        val type =
            when {
                stringing -> SkillMulti.STRING
                sets -> SkillMulti.MAKE_SETS
                else -> SkillMulti.MAKE
            }
        val title =
            when {
                stringing -> "What would you like to string?"
                sets -> "How many sets would you like to make?"
                else -> "What would you like to make?"
            }
        SkillMulti.open(p, type, title, IntArray(doable.size) { doable[it].output }) { index, quantity ->
            start(this, doable[index], quantity)
        }
    }

    private fun start(p: PlayerRef, r: Recipe, quantity: Int) {
        Production.shortfall(p, r)?.let { return p.message(it) }
        val sets = r.name.endsWith(JAVELIN)
        val ticks = if (sets) FletchingData.SET_TICKS else r.ticks
        val first = if (sets) ticks else r.firstTicks
        Production.start(p, Batch(quantity, animOf(p, r), ticks) { makeSet(this, r) }, first)
    }

    /** One action: the materials go, the product comes, xp (a level-up ends the batch). */
    private fun makeSet(p: PlayerRef, r: Recipe): Boolean {
        Production.shortfall(p, r)?.let {
            p.message(it)
            return false
        }
        if (!p.transaction("fletching") { take(r.inputs) && add(Invs.INV, r.output, r.count) }) return false
        return Production.award(p, Stats.FLETCHING, r.xp)
    }

    private fun animate(p: PlayerRef, r: Recipe) {
        val anim = animOf(p, r)
        if (anim >= 0) p.anim(anim)
    }

    private fun animOf(p: PlayerRef, r: Recipe): Int = FletchingData.anim(r, p.objName(r.inputs.first().obj).lowercase())

    private companion object {
        /** Javelins are made in sets of 15 like arrows (Wiki "Pay-to-play Fletching training"); the Recipe ticks of the tiers disagree. */
        const val JAVELIN = " javelin"
    }
}
