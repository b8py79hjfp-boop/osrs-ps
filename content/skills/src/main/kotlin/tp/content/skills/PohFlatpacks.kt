@file:Suppress("MagicNumber") // data table: workbench locs of cache 2757 (op "Work-at") and the Wiki "Flatpack" level caps

package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Seqs

/**
 * Flat-packs (docs/mechanics/construction.md FP1-FP2): "Work-at" a workbench of the workshop makes the flat-pack of a piece of
 * furniture from the materials it needs, up to the bench's level; a flat-pack is later used on a hotspot ([HouseMenus]) without
 * the level and without experience.
 */
public class PohFlatpacks : ContentPlugin {
    override fun ContentScope.register() {
        for ((loc, max) in BENCHES) onOpLoc(loc, OP_WORK) { workAt(max) }
    }

    private suspend fun PlayerScript.workAt(max: Int) {
        val p = player
        val options =
            ConstructionData
                .furniture()
                .filter { it.flatpack > 0 && it.level <= max }
                .distinctBy { it.flatpack }
                .sortedWith(compareBy({ it.level }, { it.name }))
        var start = 0
        var pick: Furniture? = null
        while (pick == null) {
            val page = options.drop(start).take(PAGE)
            val more = start + PAGE < options.size
            val answer =
                choice(
                    "What do you want to make?",
                    *page.map { "${it.name} (level ${it.level})" }.toTypedArray(),
                    if (more) MORE else CANCEL,
                )
            when {
                answer == 0 -> return
                answer <= page.size -> pick = page[answer - 1]
                more -> start += PAGE
                else -> return
            }
        }
        make(p, pick)
    }

    /** FP1: level, tools and materials as for building it; 8 ticks (Wiki "Flatpack"); the flat-pack is made un-noted. */
    private suspend fun PlayerScript.make(p: PlayerRef, f: Furniture) {
        shortfall(p, f)?.let { return p.message(it) }
        p.anim(Seqs.HUMAN_POH_BUILD)
        delay(MAKE_TICKS)
        shortfall(p, f)?.let { return p.message(it) }
        if (!p.transaction("flatpack") {
                take(f.inputs) && add(Invs.INV, f.flatpack, 1)
            }
        ) {
            return p.message("You don't have the materials to make that.")
        }
        p.addXp(Stats.CONSTRUCTION, f.xp * CarpenterOutfit.multiplier(p))
    }

    private fun shortfall(p: PlayerRef, f: Furniture): String? {
        val inv = p.inv(Invs.INV)
        return when {
            p.level(Stats.CONSTRUCTION) < f.level -> "You need a Construction level of ${f.level} to make that."
            f.tools.any { inv.total(it) == 0L } -> "You need a hammer and a saw to make that."
            f.inputs.any { inv.total(it.obj) < it.count } -> "You don't have the materials to make that."
            inv.freeSlots == 0 -> "You don't have enough inventory space."
            else -> null
        }
    }

    private companion object {
        const val OP_WORK = 1
        const val PAGE = 4
        const val MORE = "More..."
        const val CANCEL = "Cancel"

        /** Wiki "Flatpack": creating one takes 8 game ticks. */
        const val MAKE_TICKS = 8

        /**
         * Workbench locs (cache 2757: ops 1 "Work-at") and the highest flat-pack level each makes (Wiki "Flatpack", Workbench table:
         * wooden 20, oak 40, steel framed 60, bench with vice 80, bench with lathe all).
         */
        val BENCHES = listOf(6791 to 20, 6792 to 40, 6793 to 60, 6794 to 80, 6795 to Stats.MAX_LEVEL)
    }
}
