package tp.content.skills

import tp.plugin.ItemTx
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs

/**
 * A running production action (smelting N bars, smithing N items, crafting N items): [remaining] actions of
 * [interval] ticks each, playing [anim] (-1 = none). [action] does one action and returns whether the batch goes on.
 * docs/mechanics/skilling.md PR1-PR3.
 */
internal class Batch(
    var remaining: Int,
    val anim: Int,
    val interval: Int,
    val action: PlayerRef.() -> Boolean,
)

internal object Production {
    /**
     * Starts [batch] for [p]: the animation plays now and the first action lands [firstDelay] ticks later (PR1). Each
     * action is a weak queue script (scripts.md Q7), so a click that walks or interacts, or any interface opening, ends
     * the batch without any bookkeeping here.
     */
    fun start(p: PlayerRef, batch: Batch, firstDelay: Int) {
        play(p, batch)
        queue(p, batch, firstDelay)
    }

    private fun play(p: PlayerRef, batch: Batch) {
        if (batch.anim >= 0) p.anim(batch.anim)
    }

    private fun queue(p: PlayerRef, batch: Batch, delay: Int) {
        p.weakQueue(delay) { step(player, batch) }
    }

    private fun step(p: PlayerRef, batch: Batch) {
        if (!batch.action(p)) return
        batch.remaining--
        if (batch.remaining <= 0) return
        play(p, batch)
        queue(p, batch, batch.interval)
    }

    /**
     * Adds [xp] in [stat]. Returns whether the batch may go on: a level-up ends the action (Wiki "Smithing": the player
     * keeps smithing until "the player levels up").
     */
    fun award(p: PlayerRef, stat: Int, xp: Double): Boolean = p.addXp(stat, xp) == 0

    /**
     * The first reason [r] cannot be made now (level, tools, materials) as a game message, or null. Thread is not
     * checked here (it wears over several actions: [Recipe.thread]).
     */
    fun shortfall(p: PlayerRef, r: Recipe): String? {
        val inv = p.inv(Invs.INV)
        if (p.level(r.stat) < r.level) {
            return "You need a ${Stats.name(r.stat)} level of ${r.level} to make ${p.objName(r.output).lowercase()}."
        }
        for (tool in r.tools) {
            if (inv.total(tool) == 0L) return "You need a ${p.objName(tool).lowercase()} to make this."
        }
        for (i in r.inputs) {
            if (inv.total(i.obj) < i.count) return "You do not have enough ${p.objName(i.obj).lowercase()}."
        }
        return null
    }

    /** Whether [p] holds every tool and material of [r] (levels aside): the menus list only what can be made now. */
    fun hasMaterials(p: PlayerRef, r: Recipe): Boolean {
        val inv = p.inv(Invs.INV)
        return r.tools.all { inv.total(it) > 0 } && r.inputs.all { inv.total(it.obj) >= it.count }
    }
}

/** Removes every ingredient of [inputs] from the backpack inside the transaction. */
internal fun ItemTx.take(inputs: List<Ingredient>): Boolean {
    for (i in inputs) if (!remove(Invs.INV, i.obj, i.count)) return false
    return true
}
