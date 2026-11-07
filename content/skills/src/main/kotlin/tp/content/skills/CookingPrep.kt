package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs

private val WINE_TIMER = TimerId("cooking.wine")

/**
 * Cooking preparation (docs/mechanics/cooking.md CK13, CK14): the item-on-item steps before the range (dough, pie shells, uncooked
 * pies, pizza bases and toppings, cakes, stews, curries) and wine making. The steps are the rows of `cooking-prep.tsv` (OSRS Wiki
 * `{{Recipe}}`, `tools/wiki-import/cooking_prep.py`); using one ingredient on another opens the make-X menu ([SkillMulti]) and a
 * choice runs as a [Production] batch, like Herblore.
 */
public class CookingPrep : ContentPlugin {
    override fun ContentScope.register() {
        val steps = RecipeTable.load(FILE)
        for ((pair, options) in steps.flatMap { r -> pairs(r).map { it to r } }.groupBy({ it.first }, { it.second })) {
            onUseOnItem(pair.first, pair.second) { prepare(this, options) }
        }
        // Unfermented wine in the backpack at login ferments from then on: the timer is not saved.
        onLogin { if (inv(Invs.INV).total(Objs.JUG_UNFERMENTED_WINE) > 0) startFermenting(this) }
    }

    /** Every unordered pair of distinct ingredients of [r]: using any one on any other starts the step. */
    private fun pairs(r: Recipe): List<Pair<Int, Int>> {
        val ids = r.inputs.map { it.obj }.distinct()
        return ids.flatMapIndexed { i, a -> ids.drop(i + 1).map { b -> minOf(a, b) to maxOf(a, b) } }
    }

    /** Opens the make-X menu with the products of [options] the player holds every ingredient for. */
    private fun prepare(p: PlayerRef, options: List<Recipe>) {
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
        Production.start(p, Batch(quantity, NO_ANIM, r.ticks) { stepOne(this, r) }, r.firstTicks)
    }

    /** One step: the ingredients go, the product and what the step gives back come, xp (a level-up ends the batch). */
    private fun stepOne(p: PlayerRef, r: Recipe): Boolean {
        Production.shortfall(p, r)?.let {
            p.message(it)
            return false
        }
        val done =
            p.transaction("cooking prep") {
                take(r.inputs) && add(Invs.INV, r.output, r.count) && r.returns.all { addOrDrop(Invs.INV, it.obj, it.count) }
            }
        if (!done) return false
        if (r.output == Objs.JUG_UNFERMENTED_WINE) startFermenting(p)
        return r.xp == 0.0 || Production.award(p, Stats.COOKING, r.xp)
    }

    // ---- wine (CK14)

    /** (Re)starts the 20-tick fermenting timer: it restarts whenever another unfermented wine is made (Wiki "Jug of wine"). */
    private fun startFermenting(p: PlayerRef) {
        p.timer(WINE_TIMER, FERMENT_TICKS, true) { ferment(this) }
    }

    /**
     * Every unfermented wine in the backpack ferments: 15 batches of `count / 15` plus the remainder one by one, each with one
     * skilling roll (Wiki "Jug of wine" > Success chance, Mod Ash 8 August 2021); a success is a jug of wine and 200 xp, a failure
     * a jug of bad wine. The bank is not searched (OPEN-CK11).
     */
    private fun ferment(p: PlayerRef) {
        p.stopTimer(WINE_TIMER)
        val count = p.inv(Invs.INV).total(Objs.JUG_UNFERMENTED_WINE).toInt()
        if (count == 0) return
        val size = count / WINE_BATCHES
        var good = 0
        repeat(WINE_BATCHES) { if (size > 0 && p.statRandom(Stats.COOKING, WINE_LOW, WINE_HIGH)) good += size }
        repeat(count - size * WINE_BATCHES) { if (p.statRandom(Stats.COOKING, WINE_LOW, WINE_HIGH)) good++ }
        val stored =
            p.transaction("cooking wine") {
                remove(Invs.INV, Objs.JUG_UNFERMENTED_WINE, count) &&
                    (good == 0 || add(Invs.INV, Objs.JUG_WINE, good)) &&
                    (good == count || add(Invs.INV, Objs.JUG_BAD_WINE, count - good))
            }
        if (stored && good > 0) p.addXp(Stats.COOKING, WINE_XP * good)
    }

    private companion object {
        const val FILE = "cooking-prep.tsv"

        /** [Batch] animation meaning none: the Wiki gives no animation for these steps (OPEN-CK12). */
        const val NO_ANIM = -1

        /** "The process of fermenting takes 12 seconds" = 20 ticks (Wiki "Jug of wine" Recipe: "20 ticks to ferment"). */
        const val FERMENT_TICKS = 20

        /** 15 batches (Mod Ash, 8 August 2021, quoted by the Wiki "Jug of wine"). */
        const val WINE_BATCHES = 15

        /** The jug of wine's roll: 48 / 352 out of 256 (Wiki "Jug of wine" chart; at 35 about 60%, 100% from level 68). */
        const val WINE_LOW = 48
        const val WINE_HIGH = 352

        /** 200 Cooking xp per jug of wine (Wiki "Jug of wine"). */
        const val WINE_XP = 200.0
    }
}
