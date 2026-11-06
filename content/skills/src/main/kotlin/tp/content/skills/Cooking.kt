package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Cooking (docs/mechanics/cooking.md): raw food used on a fire or range, or the "Cook" op of a range, cooks every raw
 * item of that kind in the backpack, one every [COOK_TICKS] ticks, the first on arrival (CK1). The make-X dialogue is
 * not implemented: the Wiki's default, "cook all", is always used (OPEN-CK3). Like woodcutting it is an op script that
 * keeps its interaction, so walking away ends it.
 */
public class Cooking : ContentPlugin {
    override fun ContentScope.register() {
        for (loc in RANGE_LOCS + FIRE_LOCS) onUseOnLoc(ANY_OBJ, loc) { target -> cook(target, used?.obj ?: ANY_OBJ, used?.slot ?: NO_SLOT) }
        for (name in COOK_LOC_NAMES) onOpLocNamed(name, "Cook") { target -> cook(target, ANY_OBJ, NO_SLOT) }
    }

    /** What is being cooked: the food, the backpack slot the click used (kept to the end, see [slotToCook]) and how many are left. */
    private class Job(val food: Food, val usedSlot: Int, var remaining: Int)

    private fun PlayerScript.cook(loc: LocRef, rawUsed: Int, usedSlot: Int) {
        val p = player
        if (p.locAt(loc.coord, loc.shape)?.id != loc.id) return Skilling.stop(p)
        val clock = Skilling.clock(p, Skilling.locKey(loc.id, loc.coord.packed))
        val fire = p.locName(loc.id).equals(FIRE_NAME, ignoreCase = true)
        if (clock.fresh && !begin(p, rawUsed, usedSlot, fire)) return Skilling.stop(p)
        val job = p[JOB] ?: return Skilling.stop(p)
        keepInteraction()
        val tick = p.tick
        if (clock.actionDelay > tick) return
        clock.actionDelay = tick + COOK_TICKS
        if (!cookOne(p, job, fire) || job.remaining == 0) {
            p[JOB] = null
            p.stopInteraction()
            Skilling.stop(p)
        }
    }

    /** Validates a fresh click and sets [JOB]; false (after the message) if nothing can be cooked. */
    private fun begin(p: PlayerRef, rawUsed: Int, usedSlot: Int, fire: Boolean): Boolean {
        p[JOB] = null
        val raw = if (rawUsed == ANY_OBJ) firstCookable(p) else rawUsed
        val food = FOODS[raw]
        val problem =
            when {
                food == null -> if (rawUsed == ANY_OBJ) NOTHING_TO_COOK else NOTHING_INTERESTING
                p.level(Stats.COOKING) < food.level -> "You need a Cooking level of ${food.level} to cook this."
                fire && food.rangeOnly -> NEEDS_RANGE
                else -> null
            }
        if (problem != null || food == null) {
            problem?.let { p.message(it) }
            return false
        }
        p[JOB] = Job(food, usedSlot, p.inv(Invs.INV).total(food.raw).toInt())
        return true
    }

    private fun firstCookable(p: PlayerRef): Int {
        val inv = p.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            if (FOODS.containsKey(inv.obj(slot))) return inv.obj(slot)
        }
        return ANY_OBJ
    }

    /** Cooks one item of [job]; false if none is left. */
    private fun cookOne(p: PlayerRef, job: Job, fire: Boolean): Boolean {
        val food = job.food
        val slot = slotToCook(p, food.raw, job.usedSlot)
        if (slot < 0) return false
        val success = cannotBurn(p) || rate(p, food, fire).let { p.statRandom(Stats.COOKING, it.low, it.high) }
        val product = if (success) food.cooked else food.burnt
        if (!p.transaction("cooking") { replaceAt(Invs.INV, slot, food.raw, product, 1) }) return false
        job.remaining--
        p.anim(if (fire) Seqs.HUMAN_FIRECOOKING else Seqs.HUMAN_COOKING)
        val verb = if (food.rangeOnly) "bake" else "cook"
        val name = p.objName(food.cooked).lowercase()
        if (success) {
            p.addXp(Stats.COOKING, food.xp)
            p.message("You successfully $verb the $name.", Skilling.SPAM)
        } else {
            p.message("You accidentally burn the $name.", Skilling.SPAM)
        }
        return true
    }

    /**
     * The backpack slot to cook next. The slot the click used goes last: a use-on script only runs while that slot still
     * holds the clicked item (interactions.md I10), and cooking it replaces the item.
     */
    private fun slotToCook(p: PlayerRef, raw: Int, usedSlot: Int): Int {
        val inv = p.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            if (slot != usedSlot && inv.obj(slot) == raw) return slot
        }
        return if (usedSlot >= 0 && inv.obj(usedSlot) == raw) usedSlot else -1
    }

    /** The burn roll for [food] here: the worn cooking gauntlets' when they apply, else the heat source's (docs CK3, CK4). */
    private fun rate(p: PlayerRef, food: Food, fire: Boolean): Rate {
        val gloves = p.inv(Invs.WORN).obj(HANDS_SLOT) == Objs.GAUNTLETS_OF_COOKING
        food.gauntlets?.let { if (gloves) return it }
        return if (fire) food.fire ?: food.range else food.range
    }

    /** The Cooking cape (untrimmed or trimmed) never burns food (Wiki "Cooking cape"). */
    private fun cannotBurn(p: PlayerRef): Boolean {
        val cape = p.inv(Invs.WORN).obj(CAPE_SLOT)
        return cape == Objs.SKILLCAPE_COOKING || cape == Objs.SKILLCAPE_COOKING_TRIMMED
    }

    private companion object {
        val JOB = AttrKey<Job>("cooking.job")

        /** Item-on-loc key meaning "any item" (ContentScope.onUseOnLoc). */
        const val ANY_OBJ = -1
        const val NO_SLOT = -1

        /** Cooking takes four game ticks per item (Wiki "Pay-to-play Cooking training": "Cooking most foods takes four game ticks"). */
        const val COOK_TICKS = 4

        /** Worn-inventory slots of the cape and the gloves (cache wear positions 1 and 9). */
        const val CAPE_SLOT = 1
        const val HANDS_SLOT = 9

        /** Cache loc name of every fire. */
        const val FIRE_NAME = "Fire"

        /** The engine's default line for a use that does nothing (interactions.md I10). */
        const val NOTHING_INTERESTING = "Nothing interesting happens."

        /** OPEN-CK4: wording of these two lines is not sourced. */
        const val NOTHING_TO_COOK = "You have nothing to cook."
        const val NEEDS_RANGE = "You can't cook that on a fire."
    }
}
