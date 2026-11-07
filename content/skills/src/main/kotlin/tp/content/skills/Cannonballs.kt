package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Steel cannonballs (docs/mechanics/smithing.md SM14; Wiki "Steel cannonball"): an ammo mould (or the double ammo mould) and
 * steel bars used on a furnace. Four balls per bar (eight per two bars with the double mould), level 35, 25.6 xp per bar,
 * 8 ticks to the first and 10 to each following one ("Make-X is 8, then 10 ticks"). The Dwarf Cannon quest gate is OPEN-SM9.
 */
public class Cannonballs : ContentPlugin {
    override fun ContentScope.register() {
        for (loc in SmithingData.FURNACES) {
            onUseOnLoc(Objs.STEEL_BAR, loc) { open(player) }
            onUseOnLoc(Objs.AMMO_MOULD, loc) { open(player) }
            onUseOnLoc(Objs.DOUBLE_AMMO_MOULD, loc) { open(player) }
        }
    }

    /** The mould in the backpack that the next cast uses: the double one when held, else the ammo mould, or -1. */
    private fun mould(p: PlayerRef): Int {
        val inv = p.inv(Invs.INV)
        return when {
            inv.total(Objs.DOUBLE_AMMO_MOULD) > 0 -> Objs.DOUBLE_AMMO_MOULD
            inv.total(Objs.AMMO_MOULD) > 0 -> Objs.AMMO_MOULD
            else -> -1
        }
    }

    private fun open(p: PlayerRef) {
        // OPEN-SM9: completion of Dwarf Cannon (quest varp 0) is not checked, the quest is not in content/quests; the mould is
        // only obtainable through it, so holding one is the gate.
        if (mould(p) < 0) return p.message("You need an ammo mould to make cannonballs.")
        if (p.inv(Invs.INV).total(Objs.STEEL_BAR) == 0L) return p.message("You need a steel bar to make cannonballs.")
        SkillMulti.open(p, SkillMulti.SMELT, "How many would you like to make?", intArrayOf(Objs.MCANNONBALL)) { _, quantity ->
            start(this, quantity)
        }
    }

    private fun start(p: PlayerRef, quantity: Int) {
        if (!canCast(p)) return
        val first = FIRST_TICKS
        Production.start(p, Batch(quantity, Seqs.HUMAN_FURNACE, NEXT_TICKS) { castOne(this) }, first)
    }

    private fun canCast(p: PlayerRef): Boolean {
        val mould = mould(p)
        val bars = if (mould == Objs.DOUBLE_AMMO_MOULD) 2 else 1
        val message =
            when {
                mould < 0 -> "You need an ammo mould to make cannonballs."
                p.level(Stats.SMITHING) < LEVEL -> "You need a Smithing level of $LEVEL to make cannonballs."
                p.inv(Invs.INV).total(Objs.STEEL_BAR) < bars -> "You have run out of steel bars."
                else -> null
            }
        message?.let { p.message(it) }
        return message == null
    }

    private fun castOne(p: PlayerRef): Boolean {
        if (!canCast(p)) return false
        val double = mould(p) == Objs.DOUBLE_AMMO_MOULD
        val bars = if (double) 2 else 1
        val balls = bars * PER_BAR
        val done = p.transaction("cannonballs") { remove(Invs.INV, Objs.STEEL_BAR, bars) && add(Invs.INV, Objs.MCANNONBALL, balls) }
        if (!done) return false
        p.message("You heat the steel bar${if (double) "s" else ""} into $balls cannonballs.")
        return Production.award(p, Stats.SMITHING, bars * XP_PER_BAR)
    }

    private companion object {
        /** Wiki "Steel cannonball": level 35, 25.6 xp per bar, 4 cannonballs per bar. */
        const val LEVEL = 35
        const val XP_PER_BAR = 25.6
        const val PER_BAR = 4

        /** Wiki "Steel cannonball": "ticks = 8; Make-X is 8, then 10 ticks". */
        const val FIRST_TICKS = 8
        const val NEXT_TICKS = 10
    }
}
