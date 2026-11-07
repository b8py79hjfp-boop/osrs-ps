package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits

/** One of Kourend's two recreated altars (Wiki "Blood Altar (Kourend)", "Soul Altar (Kourend)"): fragments become [rune]s. */
private class FragmentAltar(val loc: Int, val name: String, val rune: Int, val level: Int, val xpPerFragment: Double)

/**
 * Arceuus (docs/mechanics/runecraft.md RC17): dense runestone -> dense essence block -> (Dark Altar) dark essence block ->
 * (chisel) dark essence fragments -> blood and soul runes at Kourend's altars, which need no ruins or talisman.
 */
public class RunecraftZeah : ContentPlugin {
    override fun ContentScope.register() {
        for (stone in STONES) onOpLoc(stone, OP_CHIP) { chip(it) }
        onOpLoc(Locs.ARCHEUS_ALTAR_DARK, OP_VENERATE) { venerate(player) }
        onUseOnLoc(Objs.ARCEUUS_ESSENCE_BLOCK, Locs.ARCHEUS_ALTAR_DARK) { venerate(player) }
        onUseOnItem(Objs.CHISEL, Objs.ARCEUUS_ESSENCE_BLOCK_DARK) { crush(this) }
        for (altar in ALTARS) {
            onOpLoc(altar.loc, OP_BIND) { bind(player, altar) }
            onUseOnLoc(Objs.BIGBLANKRUNE, altar.loc) { bind(player, altar) }
        }
    }

    // ---- mining

    private fun PlayerScript.chip(loc: LocRef) {
        val p = player
        if (p.locAt(loc.coord, loc.shape)?.id != loc.id) return Skilling.stop(p)
        if (!canChip(p)) return Skilling.stop(p)
        val clock = Skilling.clock(p, Skilling.locKey(loc.id, loc.coord.packed))
        keepInteraction()
        val tick = p.tick
        if (clock.fresh) {
            clock.actionDelay = tick + CYCLE
            clock.animDelay = tick
        }
        if (clock.animDelay <= tick) {
            clock.animDelay = tick + ANIM_LOOP
            p.anim(Seqs.HUMAN_MINING_BRONZE_PICKAXE)
        }
        if (clock.actionDelay > tick) return
        clock.actionDelay = tick + CYCLE
        val blocks = if (p.varbit(Varbits.KOUREND_DIARY_MEDIUM_COMPLETE) == 1 && p.random(DOUBLE_ONE_IN) == 0) 2 else 1
        if (!p.transaction("dense runestone") { add(Invs.INV, Objs.ARCEUUS_ESSENCE_BLOCK, blocks) }) {
            p.message("Your inventory is too full to hold any more dense essence blocks.")
            Skilling.stop(p)
            return p.stopInteraction()
        }
        p.addXp(Stats.MINING, MINING_XP)
        p.addXp(Stats.CRAFTING, CRAFTING_XP)
    }

    private fun canChip(p: PlayerRef): Boolean {
        if (p.level(Stats.MINING) < LEVEL || p.level(Stats.CRAFTING) < LEVEL) {
            p.message("You need level $LEVEL Mining and level $LEVEL Crafting to chip this runestone.")
            return false
        }
        val inv = p.inv(Invs.INV)
        if (inv.total(Objs.CHISEL) == 0L || !hasPickaxe(p)) {
            p.message("You need a pickaxe and a chisel to chip this runestone.")
            return false
        }
        if (inv.freeSlots == 0) {
            p.message("Your inventory is too full to hold any more dense essence blocks.")
            return false
        }
        return true
    }

    private fun hasPickaxe(p: PlayerRef): Boolean {
        val worn = p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT)
        if (worn >= 0 && "pickaxe" in p.objName(worn).lowercase()) return true
        val inv = p.inv(Invs.INV)
        return (0 until inv.size).any { inv.obj(it) >= 0 && "pickaxe" in p.objName(inv.obj(it)).lowercase() }
    }

    // ---- the Dark Altar and the chisel

    /** Every dense essence block in the backpack becomes a dark essence block: 2.5 xp and 1 prayer point each (Wiki "Dark Altar"). */
    private fun venerate(p: PlayerRef) {
        val n = p.inv(Invs.INV).total(Objs.ARCEUUS_ESSENCE_BLOCK).toInt()
        if (n == 0) return p.message("You have no dense essence blocks.")
        val done =
            p.transaction("dark altar") {
                remove(Invs.INV, Objs.ARCEUUS_ESSENCE_BLOCK, n) &&
                    add(Invs.INV, Objs.ARCEUUS_ESSENCE_BLOCK_DARK, n)
            }
        if (!done) return
        p.setLevel(Stats.PRAYER, (p.level(Stats.PRAYER) - n).coerceAtLeast(0))
        p.addXp(Stats.RUNECRAFT, DARK_BLOCK_XP * n)
    }

    /** A chisel on dark essence blocks: 4 fragments and 8 Crafting xp each, at Crafting 38 (Wiki "Dark essence fragments"). */
    private fun crush(p: PlayerRef) {
        if (p.level(Stats.CRAFTING) < LEVEL) return p.message("You need level $LEVEL Crafting to do that.")
        val n = p.inv(Invs.INV).total(Objs.ARCEUUS_ESSENCE_BLOCK_DARK).toInt()
        if (n == 0) return
        val done =
            p.transaction("dark essence fragments") {
                remove(Invs.INV, Objs.ARCEUUS_ESSENCE_BLOCK_DARK, n) && add(Invs.INV, Objs.BIGBLANKRUNE, n * FRAGMENTS_PER_BLOCK)
            }
        if (done) p.addXp(Stats.CRAFTING, FRAGMENT_CRAFTING_XP * n)
    }

    // ---- the altars

    private fun bind(p: PlayerRef, altar: FragmentAltar) {
        val n = p.inv(Invs.INV).total(Objs.BIGBLANKRUNE).toInt()
        if (n == 0) return p.message("You have no dark essence fragments to bind.")
        // Wiki "Blood Altar (Kourend)": level 77 "(can be boosted)"; "Soul Altar (Kourend)": level 90.
        if (p.level(Stats.RUNECRAFT) <
            altar.level
        ) {
            return p.message("You need a Runecraft level of ${altar.level} to craft ${altar.name} runes.")
        }
        val done = p.transaction("runecraft zeah") { remove(Invs.INV, Objs.BIGBLANKRUNE, n) && add(Invs.INV, altar.rune, n) }
        if (!done) return p.message("You don't have enough inventory space.")
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(SpotAnims.RUNECRAFTING)
        p.message("You bind the temple's power into ${altar.name} runes.")
        p.addXp(Stats.RUNECRAFT, altar.xpPerFragment * n)
        p.setSaved(RunecraftAbyss.riftKey(altar.name), 1L)
    }

    private companion object {
        const val OP_CHIP = 1
        const val OP_VENERATE = 1
        const val OP_BIND = 1

        /** Cache locs `arceuus_runestone_{base,middle,top}_mine` (Wiki "Dense runestone": ids 8975, 8977, 8979). */
        val STONES = listOf(Locs.ARCEUUS_RUNESTONE_BASE_MINE, Locs.ARCEUUS_RUNESTONE_MIDDLE_MINE, Locs.ARCEUUS_RUNESTONE_TOP_MINE)

        /** Wiki "Dense runestone": Mining 38 and Crafting 38, 12 Mining xp and 8 Crafting xp per block. */
        const val LEVEL = 38
        const val MINING_XP = 12.0
        const val CRAFTING_XP = 8.0

        /** Wiki "Dense essence block": "always get dense essence blocks every nine ticks, regardless of the pickaxe". */
        const val CYCLE = 9L

        /** The mining animation repeats every 4 ticks (the length of the pickaxe swing; OPEN-RC17: tool-specific animations). */
        const val ANIM_LOOP = 4L

        /** Wiki "Dense essence block": a 5% chance to mine two at once after the medium Kourend diary. */
        const val DOUBLE_ONE_IN = 20

        /** Wiki "Dark Altar": 2.5 Runecraft xp per dark essence block. */
        const val DARK_BLOCK_XP = 2.5

        /** Wiki "Dark essence block" / "fragments": 4 fragments per block, 8 Crafting xp per block. */
        const val FRAGMENTS_PER_BLOCK = 4
        const val FRAGMENT_CRAFTING_XP = 8.0

        /** Wiki "Blood Altar (Kourend)": level 77, 23.8 xp per fragment; "Soul Altar (Kourend)": level 90, 29.7 xp per fragment. */
        val ALTARS =
            listOf(
                FragmentAltar(Locs.ARCHEUS_ALTAR_BLOOD, "blood", Objs.BLOODRUNE, 77, 23.8),
                FragmentAltar(Locs.ARCHEUS_ALTAR_SOUL, "soul", Objs.SOULRUNE, 90, 29.7),
            )
    }
}
