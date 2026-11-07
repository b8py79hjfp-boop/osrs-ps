package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Gutting leaping fish (docs/mechanics/fishing.md FS15): a knife used on a leaping trout, salmon or sturgeon cuts it, and every other
 * fish of the kind follows every [CUT_TICKS] ticks. A cut gives roe (caviar from the sturgeon) with a chance that grows with the
 * player's Cooking level, and then fish offcuts with a further chance; both work as bait at the barbarian spots.
 */
public class FishingGutting : ContentPlugin {
    override fun ContentScope.register() {
        for (fish in FISH) onUseOnItem(Objs.KNIFE, fish.obj) { start(this, fish) }
    }

    /**
     * A leaping fish: the roe it gives, its Cooking xp, the roe chance (the Cooking level out of [roeDenominator], so certain once
     * the level reaches it) and the offcuts chance [offcutsNumerator] / [offcutsDenominator].
     */
    private class Leaping(
        val obj: Int,
        val roe: Int,
        val xp: Double,
        val roeDenominator: Int,
        val offcutsNumerator: Int,
        val offcutsDenominator: Int,
    )

    private fun start(p: PlayerRef, fish: Leaping) {
        p.interrupt()
        if (!cutOne(p, fish)) return
        val rest = p.inv(Invs.INV).total(fish.obj).toInt()
        if (rest > 0) Production.start(p, Batch(rest, Seqs.BRUT_HUMAN_KNIFEUSE, CUT_TICKS) { cutOne(this, fish) }, CUT_TICKS)
    }

    /**
     * One fish: it is used up; with chance `Cooking level / roeDenominator` roe comes with the xp, and then offcuts with chance
     * `offcutsNumerator / offcutsDenominator`. False if there is no fish or no room.
     */
    private fun cutOne(p: PlayerRef, fish: Leaping): Boolean {
        val roe = p.random(fish.roeDenominator) < p.level(Stats.COOKING)
        val offcuts = roe && p.random(fish.offcutsDenominator) < fish.offcutsNumerator
        val done =
            p.transaction("fishing gutting") {
                remove(Invs.INV, fish.obj, 1) &&
                    (!roe || addOrDrop(Invs.INV, fish.roe, 1)) &&
                    (!offcuts || addOrDrop(Invs.INV, Objs.BRUT_FISH_CUTS, 1))
            }
        if (!done) return false
        p.anim(Seqs.BRUT_HUMAN_KNIFEUSE)
        if (roe) p.addXp(Stats.COOKING, fish.xp)
        return true
    }

    private companion object {
        /** "Auto-cutting leaping fish is 3 ticks" (Wiki "Fish offcuts" Recipe). */
        const val CUT_TICKS = 3

        /**
         * Roe chance 1/150 at level 1 and 99/150 at 99 for the trout, 1/80 at level 1 and certain from 80 for the salmon and
         * sturgeon; offcuts then 1/2, 3/4 and 5/6; Cooking xp 10, 10 and 15 (Wiki "Leaping trout", "Leaping salmon", "Leaping
         * sturgeon", quoting Mod Ash, 18 April 2018).
         */
        val FISH =
            listOf(
                Leaping(Objs.BRUT_SPAWNING_TROUT, Objs.BRUT_ROE, 10.0, 150, 1, 2),
                Leaping(Objs.BRUT_SPAWNING_SALMON, Objs.BRUT_ROE, 10.0, 80, 3, 4),
                Leaping(Objs.BRUT_STURGEON, Objs.BRUT_CAVIAR, 15.0, 80, 5, 6),
            )
    }
}
