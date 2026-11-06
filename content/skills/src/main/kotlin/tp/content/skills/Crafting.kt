package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Crafting (docs/mechanics/crafting.md): leather and dragonhide with a needle, gem cutting, spinning, pottery, glass
 * (molten glass at a furnace and glassblowing), jewellery and silver at a furnace, and stringing amulets. Every recipe is
 * a row of `recipes.tsv` (OSRS Wiki `{{Recipe}}`); the products are chosen in the make-X menu ([SkillMulti]) and made as
 * a [Production] batch.
 */
public class Crafting : ContentPlugin {
    /** How one family of recipes is presented and timed: the menu, the animation, and tick overrides (null = the recipe's own). */
    private class Kind(
        val menu: Int,
        val title: String,
        val anim: Int,
        val first: Int? = null,
        val ticks: Int? = null,
        val refund: Int = -1,
    )

    override fun ContentScope.register() {
        val recipes = RecipeTable.load().filter { it.stat == Stats.CRAFTING }
        leather(recipes)
        gems(recipes)
        spinning(recipes)
        pottery(recipes)
        glass(recipes)
        furnaceJewellery(recipes)
        stringing(recipes)
    }

    // ---- families

    private fun ContentScope.leather(recipes: List<Recipe>) {
        val kind = Kind(SkillMulti.MAKE, "What would you like to make?", Seqs.HUMAN_LEATHER_CRAFTING, ticks = CraftingData.LEATHER_TICKS)
        val byMaterial = recipes.filter { it.thread && it.tools == listOf(Objs.NEEDLE) }.groupBy { it.inputs.first().obj }
        for ((material, options) in byMaterial) {
            val handler: PlayerRef.(tp.plugin.ItemOnItem) -> Unit = { menu(this, kind, options) }
            onUseOnItem(Objs.NEEDLE, material, handler)
            onUseOnItem(Objs.THREAD, material, handler)
        }
    }

    private fun ContentScope.gems(recipes: List<Recipe>) {
        for (r in recipes.filter { it.tools == listOf(Objs.CHISEL) && it.inputs.size == 1 }) {
            val uncut = r.inputs.first().obj
            val gem = CraftingData.GEMS[uncut] ?: continue
            onUseOnItem(Objs.CHISEL, uncut) {
                SkillMulti.open(this, SkillMulti.CUT, "What would you like to cut?", intArrayOf(r.output)) { _, quantity ->
                    startCut(this, r, uncut, gem, quantity)
                }
            }
        }
    }

    private fun ContentScope.spinning(recipes: List<Recipe>) {
        val kind =
            Kind(
                SkillMulti.SPIN,
                "What would you like to spin?",
                CraftingData.SPINNING_ANIM,
                CraftingData.SPIN_FIRST_TICKS,
                CraftingData.SPIN_TICKS,
            )
        val options = recipes.filter { it.facility == SPINNING_WHEEL }
        val materials = options.flatMap { r -> r.inputs.map { it.obj } }.toSet()
        for (wheel in CraftingData.SPINNING_WHEELS) {
            onOpLoc(wheel, OP_SPIN) { menu(player, kind, options) }
            for (material in materials) onUseOnLoc(material, wheel) { menu(player, kind, options) }
        }
    }

    private fun ContentScope.pottery(recipes: List<Recipe>) {
        val shape = Kind(SkillMulti.MAKE, "What would you like to make?", Seqs.HUMAN_POTTERYWHEEL)
        val fire = Kind(SkillMulti.FIRE, "What would you like to fire?", Seqs.HUMAN_POTTERYWHEEL)
        val shaping = recipes.filter { it.facility == POTTERS_WHEEL }
        val firing = recipes.filter { it.facility == POTTERY_OVEN }
        for (wheel in CraftingData.POTTERS_WHEELS) {
            onOpLoc(wheel, OP_WHEEL) { menu(player, shape, shaping) }
            onUseOnLoc(Objs.SOFTCLAY, wheel) { menu(player, shape, shaping) }
        }
        for (oven in CraftingData.POTTERY_OVENS) {
            onOpLoc(oven, OP_WHEEL) { menu(player, fire, firing) }
            for (unfired in firing.map { it.inputs.first().obj }) onUseOnLoc(unfired, oven) { menu(player, fire, firing) }
        }
    }

    private fun ContentScope.glass(recipes: List<Recipe>) {
        val blow = Kind(SkillMulti.MAKE, "What would you like to make?", Seqs.HUMAN_GLASSBLOWING)
        val blowing = recipes.filter { it.tools == listOf(Objs.GLASSBLOWINGPIPE) }
        onUseOnItem(Objs.GLASSBLOWINGPIPE, Objs.MOLTEN_GLASS) { menu(this, blow, blowing) }
        val melt = Kind(SkillMulti.MAKE, "What would you like to make?", Seqs.HUMAN_FURNACE, refund = Objs.BUCKET_EMPTY)
        val melting = recipes.filter { it.facility == FURNACE && it.output == Objs.MOLTEN_GLASS }
        for (furnace in SmithingData.FURNACES) {
            onUseOnLoc(Objs.BUCKET_SAND, furnace) { menu(player, melt, melting) }
            onUseOnLoc(Objs.SODA_ASH, furnace) { menu(player, melt, melting) }
        }
    }

    private fun ContentScope.furnaceJewellery(recipes: List<Recipe>) {
        val kind = Kind(SkillMulti.MAKE, "What would you like to make?", Seqs.HUMAN_FURNACE)
        val options = recipes.filter { it.facility == FURNACE && it.tools.isNotEmpty() }.sortedBy { it.level }
        for (bar in listOf(Objs.GOLD_BAR, Objs.SILVER_BAR)) {
            val own = options.filter { r -> r.inputs.any { it.obj == bar } }
            for (furnace in SmithingData.FURNACES) onUseOnLoc(bar, furnace) { menu(player, kind, own) }
        }
    }

    private fun ContentScope.stringing(recipes: List<Recipe>) {
        val kind = Kind(SkillMulti.STRING, "What would you like to string?", -1)
        val options = recipes.filter { it.tools.isEmpty() && it.facility.isEmpty() && it.inputs.any { i -> i.obj == Objs.BALL_OF_WOOL } }
        for (r in options) {
            val unstrung = r.inputs.first { it.obj != Objs.BALL_OF_WOOL }.obj
            onUseOnItem(Objs.BALL_OF_WOOL, unstrung) { menu(this, kind, listOf(r)) }
        }
    }

    // ---- the menu and the batch

    /** Opens the menu with the products of [options] the player has the materials for now. */
    private fun menu(p: PlayerRef, kind: Kind, options: List<Recipe>) {
        val doable = options.filter { Production.hasMaterials(p, it) && (!it.thread || p.inv(Invs.INV).total(Objs.THREAD) > 0) }
        val shown = doable.distinctBy { it.output }.take(SkillMulti.MAX_ITEMS)
        if (shown.isEmpty()) return p.message("You don't have the materials to make anything with that.")
        SkillMulti.open(p, kind.menu, kind.title, IntArray(shown.size) { shown[it].output }) { index, quantity ->
            start(this, kind, shown[index], quantity)
        }
    }

    private fun start(p: PlayerRef, kind: Kind, r: Recipe, quantity: Int) {
        blocker(p, r)?.let { return p.message(it) }
        val ticks = kind.ticks ?: r.ticks
        Production.start(p, Batch(quantity, kind.anim, ticks) { craftOne(this, kind, r) }, kind.first ?: ticks)
    }

    /** The reason [r] cannot be made now, or null: levels, tools, materials, and thread when a new spool is needed. */
    private fun blocker(p: PlayerRef, r: Recipe): String? {
        Production.shortfall(p, r)?.let { return it }
        val needsSpool = r.thread && threadUses(p)[0] == 0 && p.inv(Invs.INV).total(Objs.THREAD) == 0L
        return if (needsSpool) "You need some thread to make this." else null
    }

    /** One action: the materials go, the product comes, thread wears (CR2), xp (a level-up ends the batch). */
    private fun craftOne(p: PlayerRef, kind: Kind, r: Recipe): Boolean {
        blocker(p, r)?.let {
            p.message(it)
            return false
        }
        val done =
            p.transaction("crafting") {
                take(r.inputs) && add(Invs.INV, r.output, r.count) && (kind.refund < 0 || add(Invs.INV, kind.refund, 1))
            }
        if (!done) return false
        if (r.thread) wearThread(p)
        return Production.award(p, Stats.CRAFTING, r.xp)
    }

    private fun threadUses(p: PlayerRef): IntArray = p[THREAD_USES] ?: IntArray(1).also { p[THREAD_USES] = it }

    /** The fifth action on a spool uses it up (Wiki "Thread": "Each thread spool has 5 uses before it is consumed"). */
    private fun wearThread(p: PlayerRef) {
        val uses = threadUses(p)
        uses[0]++
        if (uses[0] < CraftingData.THREAD_USES) return
        uses[0] = 0
        if (p.transaction("thread") { remove(Invs.INV, Objs.THREAD, 1) }) p.message("You use up a reel of your thread.")
    }

    // ---- gem cutting

    private fun startCut(p: PlayerRef, r: Recipe, uncut: Int, gem: Gem, quantity: Int) {
        Production.shortfall(p, r)?.let { return p.message(it) }
        Production.start(p, Batch(quantity, gem.anim, r.ticks) { cutOne(this, r, uncut, gem) }, r.ticks)
    }

    /** One cut; opal, jade and red topaz crush with the level-dependent chance (CR3). */
    private fun cutOne(p: PlayerRef, r: Recipe, uncut: Int, gem: Gem): Boolean {
        Production.shortfall(p, r)?.let {
            p.message(it)
            return false
        }
        val crush = gem.crush
        val cut = crush == null || p.random(CraftingData.GEM_ODDS) < cutChance(p.level(Stats.CRAFTING), crush)
        val name = p.objName(r.output).lowercase()
        val done = p.transaction("cutting") { remove(Invs.INV, uncut, 1) && add(Invs.INV, if (cut) r.output else Objs.CRUSHED_GEMSTONE, 1) }
        if (!done) return false
        if (cut) {
            p.message("You cut the $name.")
            return Production.award(p, Stats.CRAFTING, r.xp)
        }
        p.message("You mis-hit the chisel and smash the $name into pieces!")
        return Production.award(p, Stats.CRAFTING, crush?.xp ?: 0.0)
    }

    /** Chance out of [CraftingData.GEM_ODDS] to cut [crush]'s gem at [level]. */
    private fun cutChance(level: Int, crush: Crush): Double = (level - 1) * crush.slope / CraftingData.GEM_SLOPE_DIVISOR + crush.base

    private companion object {
        const val FURNACE = "furnace"
        const val SPINNING_WHEEL = "spinning wheel"
        const val POTTERS_WHEEL = "potter's wheel"
        const val POTTERY_OVEN = "pottery oven"

        /** Spinning wheel "Spin" is op 2; potter's wheel "Use" and pottery oven "Fire" are op 1 of the cache locs. */
        const val OP_SPIN = 2
        const val OP_WHEEL = 1

        /** Actions done on the current spool of thread, per player. */
        val THREAD_USES = AttrKey<IntArray>("crafting.thread")
    }
}
