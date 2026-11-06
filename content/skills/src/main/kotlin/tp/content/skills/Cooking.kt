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
 * Cooking (docs/mechanics/cooking.md): raw food used on a fire or range, or the "Cook" op of a range. With more than one raw item of
 * that kind the make-X menu ([SkillMulti]) asks how many to cook (CK1); with one it is cooked at once. Either way the first item is
 * cooked on arrival (or when the quantity is chosen), then one every [COOK_TICKS] ticks. A single item is an op script that keeps its
 * interaction like woodcutting, so walking away ends it; a menu choice is a [Production] batch, which any click ends.
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
        val key = Skilling.locKey(loc.id, loc.coord.packed)
        val clock = Skilling.clock(p, key)
        val fire = p.locName(loc.id).equals(FIRE_NAME, ignoreCase = true)
        if (clock.fresh) {
            p[JOB] = null
            val food = check(p, rawUsed, fire) ?: return Skilling.stop(p)
            val count = p.inv(Invs.INV).total(food.raw).toInt()
            if (count > 1) {
                Skilling.stop(p)
                return openMenu(p, loc, key, food, fire)
            }
            p[JOB] = Job(food, usedSlot, count)
        }
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

    /** Validates a fresh click and returns the food to cook; null (after the message) if nothing can be cooked. */
    private fun check(p: PlayerRef, rawUsed: Int, fire: Boolean): Food? {
        val raw = if (rawUsed == ANY_OBJ) firstCookable(p) else rawUsed
        val food = FOODS[raw]
        val problem =
            when {
                food == null -> if (rawUsed == ANY_OBJ) NOTHING_TO_COOK else NOTHING_INTERESTING
                p.level(Stats.COOKING) < food.level -> levelMessage(food)
                fire && food.rangeOnly -> NEEDS_RANGE
                else -> null
            }
        problem?.let { p.message(it) }
        return if (problem == null) food else null
    }

    private fun levelMessage(food: Food): String = "You need a Cooking level of ${food.level} to cook this."

    private fun firstCookable(p: PlayerRef): Int {
        val inv = p.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            if (FOODS.containsKey(inv.obj(slot))) return inv.obj(slot)
        }
        return ANY_OBJ
    }

    /** Asks how many [food] to cook (CK1); the choice starts a batch. */
    private fun openMenu(p: PlayerRef, loc: LocRef, key: Long, food: Food, fire: Boolean) {
        SkillMulti.open(
            p,
            SkillMulti.COOK,
            MENU_TITLE,
            intArrayOf(food.raw),
        ) { _, quantity -> startBatch(this, loc, key, food, fire, quantity) }
    }

    /**
     * The menu callback (hostile input, validated again): the first item is cooked now, or after the rest of the player's cooking
     * clock if they cooked within the last [COOK_TICKS] ticks; the others follow every [COOK_TICKS] ticks as a weak-queue batch.
     */
    private fun startBatch(p: PlayerRef, loc: LocRef, key: Long, food: Food, fire: Boolean, quantity: Int) {
        if (p.locAt(loc.coord, loc.shape)?.id != loc.id) return
        if (p.level(Stats.COOKING) < food.level) return p.message(levelMessage(food))
        val clock = Skilling.clock(p, key)
        val job = Job(food, NO_SLOT, quantity)
        val action: PlayerRef.() -> Boolean = {
            clock.actionDelay = tick + COOK_TICKS
            cookOne(this, job, fire)
        }
        val wait = (clock.actionDelay - p.tick).coerceAtLeast(0).toInt()
        if (wait > 0) return Production.start(p, Batch(quantity, NO_ANIM, COOK_TICKS, action), wait)
        if (p.action() && quantity > 1) Production.start(p, Batch(quantity - 1, NO_ANIM, COOK_TICKS, action), COOK_TICKS)
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

        /** [Batch] animation meaning none: each cook plays its own animation. */
        const val NO_ANIM = -1

        /** Cooking takes four game ticks per item (Wiki "Pay-to-play Cooking training": "Cooking most foods takes four game ticks"). */
        const val COOK_TICKS = 4

        /** Worn-inventory slots of the cape and the gloves (cache wear positions 1 and 9). */
        const val CAPE_SLOT = 1
        const val HANDS_SLOT = 9

        /** Cache loc name of every fire. */
        const val FIRE_NAME = "Fire"

        /** Title of the make-X menu: OPEN-CK8, wording not sourced. */
        const val MENU_TITLE = "How many would you like to cook?"

        /** The engine's default line for a use that does nothing (interactions.md I10). */
        const val NOTHING_INTERESTING = "Nothing interesting happens."

        /** OPEN-CK4: wording of these two lines is not sourced. */
        const val NOTHING_TO_COOK = "You have nothing to cook."
        const val NEEDS_RANGE = "You can't cook that on a fire."
    }
}
