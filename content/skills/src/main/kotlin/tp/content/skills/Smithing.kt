package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Smithing (docs/mechanics/smithing.md): smelting bars at a furnace through the make-X menu ([SkillMulti]) and
 * smithing items at an anvil through the smithing interface (312) and the same menu for the quantity. Both are
 * [Production] batches: one action per few ticks, ended by any click.
 *
 * Recipes: bars from `recipes.tsv` (OSRS Wiki); anvil products, bars and levels from the cache (client script 430 and
 * enums 844 / 845 / 846); anvil xp is 12.5 per bar times the metal's tier.
 */
public class Smithing : ContentPlugin {
    /** Anvil product obj -> count it makes (enum 844), bars it needs (enum 845) and level (enum 846). */
    private var productCounts: Map<Int, Int> = emptyMap()
    private var productBars: Map<Int, Int> = emptyMap()
    private var productLevels: Map<Int, Int> = emptyMap()

    override fun ContentScope.register() {
        val smelts = RecipeTable.load().filter { it.stat == Stats.SMITHING && it.facility == FURNACE }.sortedBy { it.level }
        onWorldStart {
            productCounts = enumInts(ENUM_COUNT)
            productBars = enumInts(ENUM_BARS)
            productLevels = enumInts(ENUM_LEVEL)
        }
        val ores = smelts.flatMap { r -> r.inputs.map { it.obj } }.toSet()
        for (loc in SmithingData.FURNACES) {
            onOpLoc(loc, OP_SMELT) { smeltMenu(player, smelts) }
            for (ore in ores) onUseOnLoc(ore, loc) { smeltMenu(player, smelts) }
        }
        for (loc in SmithingData.ANVILS) {
            onOpLoc(loc, OP_SMITH) { openAnvil(player, -1) }
            onUseOnLoc(Objs.HAMMER, loc) { openAnvil(player, -1) }
            onUseOnLoc(Objs.IMCANDO_HAMMER, loc) { openAnvil(player, -1) }
            for (metal in SmithingData.ANVIL_BARS) onUseOnLoc(metal.bar, loc) { openAnvil(player, metal.bar) }
        }
        val components = SmithingData.ANVIL_BARS.flatMap { it.products.keys }.toSet()
        for (component in components) onButton(component, OP_SMITH) { smithPick(this, component) }
    }

    // ---- smelting

    private fun smeltMenu(p: PlayerRef, smelts: List<Recipe>) {
        val items = IntArray(smelts.size) { smelts[it].output }
        SkillMulti.open(p, SkillMulti.SMELT, "What would you like to smelt?", items) { index, quantity ->
            startSmelt(this, smelts[index], quantity)
        }
    }

    private fun startSmelt(p: PlayerRef, r: Recipe, quantity: Int) {
        Production.shortfall(p, r)?.let { return p.message(it) }
        val first = if (quantity == 1) SMELT_SINGLE_TICKS else SMELT_FIRST_TICKS
        Production.start(p, Batch(quantity, Seqs.HUMAN_FURNACE, SMELT_TICKS) { smeltOne(this, r) }, first)
    }

    /** One smelt: the ores go, the bar comes (iron fails half the time without a ring of forging, SM3). */
    private fun smeltOne(p: PlayerRef, r: Recipe): Boolean {
        Production.shortfall(p, r)?.let {
            // PR3: running out of materials ends an "All" batch silently; anything else says why.
            if (Production.hasMaterials(p, r)) p.message(it)
            return false
        }
        val forging = r.output == Objs.IRON_BAR && p.inv(Invs.WORN).obj(SmithingData.RING_SLOT) == Objs.RING_OF_FORGING
        val success = r.output != Objs.IRON_BAR || forging || p.random(IRON_FAIL_ODDS) == 0
        val done = p.transaction("smelting") { take(r.inputs) && (!success || add(Invs.INV, r.output, 1)) }
        if (!done) return false
        if (!success) {
            p.message("The ore is too impure and you fail to refine it.")
            return true
        }
        p.message("You retrieve a bar of ${p.objName(r.output).removeSuffix(" bar").lowercase()}.")
        val alive = !forging || useForgingCharge(p)
        return Production.award(p, Stats.SMITHING, smeltXp(p, r)) && alive
    }

    private fun smeltXp(p: PlayerRef, r: Recipe): Double {
        val gauntlets = p.inv(Invs.WORN).obj(SmithingData.GLOVES_SLOT) == Objs.GAUNTLETS_OF_GOLDSMITHING
        return if (r.output == Objs.GOLD_BAR && gauntlets) SmithingData.GOLDSMITH_GOLD_XP else r.xp
    }

    /** Uses one charge of the ring of forging; false once it melts (SM4). */
    private fun useForgingCharge(p: PlayerRef): Boolean {
        val left = (p.saved(FORGING_KEY) ?: SmithingData.FORGING_CHARGES.toLong()) - 1
        if (left > 0) {
            p.setSaved(FORGING_KEY, left)
            return true
        }
        p.setSaved(FORGING_KEY, null)
        p.transaction("ring of forging") { remove(Invs.WORN, Objs.RING_OF_FORGING, 1) }
        p.message("Your Ring of Forging has melted.")
        return false
    }

    // ---- anvil

    /** Opens the smithing interface for [usedBar] (a bar used on the anvil) or the best bar in the backpack. */
    private fun openAnvil(p: PlayerRef, usedBar: Int) {
        val inv = p.inv(Invs.INV)
        if (inv.total(Objs.HAMMER) == 0L && inv.total(Objs.IMCANDO_HAMMER) == 0L) {
            return p.message("You need a hammer to work the metal with.")
        }
        val metal =
            SmithingData.ANVIL_BARS.firstOrNull { it.bar == usedBar }
                ?: SmithingData.ANVIL_BARS.lastOrNull { inv.total(it.bar) > 0 }
                ?: return p.message("You don't have any metal bars to work with.")
        p.setVarbit(Varbits.SMITHING_BAR_TYPE, metal.index)
        p.stopInteraction() // the anvil interaction would run again when the interface closes
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.SMITHING)
        // The quantity row is not driven by any client script; quantities come from the make-X menu (SM6).
        p.ifHide(Components.Smithing.MAKEX, true)
        for (component in metal.products.keys) p.ifEvents(component, -1..-1, IfEvents.op(OP_SMITH))
    }

    /** A product click in the smithing interface: validated again, then the quantity menu. */
    private fun smithPick(p: PlayerRef, component: Int) {
        val metal = SmithingData.ANVIL_BARS.firstOrNull { it.index == p.varbit(Varbits.SMITHING_BAR_TYPE) } ?: return
        val product = metal.products[component] ?: return
        if (!unlocked(p, component)) return
        val bars = productBars[product] ?: return
        val level = productLevels[product] ?: return
        val inv = p.inv(Invs.INV)
        val message =
            when {
                p.level(Stats.SMITHING) < level -> "You need a Smithing level of $level to make ${p.objName(product).lowercase()}."

                inv.total(
                    metal.bar,
                ) < bars -> "You don't have enough ${p.objName(metal.bar).lowercase()}s to make ${p.objName(product).lowercase()}."

                else -> null
            }
        if (message != null) {
            p.closeModal()
            return p.message(message)
        }
        SkillMulti.open(p, SkillMulti.SMITH, "How many would you like to make?", intArrayOf(product)) { _, quantity ->
            startSmith(this, metal, product, quantity)
        }
    }

    /** Claws and dart tips are shown by client script 430 only after their quests (varps 314 and 197). */
    private fun unlocked(p: PlayerRef, component: Int): Boolean =
        when (component) {
            Components.Smithing.CLAWS -> p.varp(Varps.DEATH_EQUIPROOM) >= SmithingData.CLAWS_VARP_MIN
            Components.Smithing.DARTTIPS -> p.varp(Varps.DESERTRESCUE) >= SmithingData.DART_TIPS_VARP_MIN
            else -> true
        }

    private fun startSmith(p: PlayerRef, metal: AnvilBar, product: Int, quantity: Int) {
        if (!canSmith(p, metal, product)) return
        Production.start(p, Batch(quantity, Seqs.HUMAN_SMITHING, SMITH_TICKS) { smithOne(this, metal, product) }, SMITH_TICKS)
    }

    private fun canSmith(p: PlayerRef, metal: AnvilBar, product: Int): Boolean {
        val bars = productBars[product] ?: return false
        val level = productLevels[product] ?: return false
        val inv = p.inv(Invs.INV)
        val message =
            when {
                inv.total(Objs.HAMMER) == 0L && inv.total(Objs.IMCANDO_HAMMER) == 0L -> "You need a hammer to work the metal with."
                p.level(Stats.SMITHING) < level -> "You need a Smithing level of $level to make ${p.objName(product).lowercase()}."
                inv.total(metal.bar) < bars -> "You have run out of ${p.objName(metal.bar).lowercase()}s."
                else -> null
            }
        message?.let { p.message(it) }
        return message == null
    }

    /** One item: the bars go, the product comes; xp is 12.5 per bar times the tier (SM5). */
    private fun smithOne(p: PlayerRef, metal: AnvilBar, product: Int): Boolean {
        if (!canSmith(p, metal, product)) return false
        val bars = productBars.getValue(product)
        val count = productCounts[product] ?: 1
        val done = p.transaction("smithing") { remove(Invs.INV, metal.bar, bars) && add(Invs.INV, product, count) }
        if (!done) return false
        val what = if (count > 1) "$count ${p.objName(product).lowercase()}" else article(p.objName(product).lowercase())
        p.message("You hammer the ${p.objName(metal.bar).removeSuffix(" bar").lowercase()} and make $what.")
        return Production.award(p, Stats.SMITHING, bars * SmithingData.XP_PER_BAR * metal.index)
    }

    private fun article(name: String): String = (if (name.first() in "aeiou") "an " else "a ") + name

    private companion object {
        const val FURNACE = "furnace"

        /** Furnace "Smelt" is op 2 and anvil "Smith" op 1 of the cache locs. */
        const val OP_SMELT = 2
        const val OP_SMITH = 1

        /** Cache enums keyed by anvil product obj: items made per smith (844), bars used (845), Smithing level (846). */
        const val ENUM_COUNT = 844
        const val ENUM_BARS = 845
        const val ENUM_LEVEL = 846

        /** Bar smelting: 5 ticks per bar, Make-X starts after 4, a single bar after 6 (Wiki "Bronze bar" ticks note). */
        const val SMELT_TICKS = 5
        const val SMELT_FIRST_TICKS = 4
        const val SMELT_SINGLE_TICKS = 6

        /** Anvil smithing: 5 ticks per item (Wiki "Smithing": "a time interval of 5 ticks per item created"). */
        const val SMITH_TICKS = 5

        /** Smelting iron ore succeeds 1 time in 2 (Wiki "Smelting": "50% chance of success"). */
        const val IRON_FAIL_ODDS = 2

        /** Saved key: smelts left on the current ring of forging (absent = a fresh ring, 140). */
        const val FORGING_KEY = "smithing.forging"
    }
}
