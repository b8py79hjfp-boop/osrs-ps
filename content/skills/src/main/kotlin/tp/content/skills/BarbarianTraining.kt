package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Barbarian Training miniquest (docs/mechanics/barbarian-training.md): Otto Godblessed at his grotto teaches the heavy rod,
 * firemaking with a bow, barbarian mixes and spears and hastae ([BarbarianOtto]); his bed hands out the barbarian rod, the barbarian
 * anvil forges the spears and hastae, and roe or caviar turns two-dose potions into mixes. The state is [BarbarianProgress]; the
 * barbarian fishing spots (Fishing) and the bow (Firemaking) read it. Farming, barehanded fishing and pyre ships are not
 * implemented (OPEN-BT2, OPEN-BT3).
 */
public class BarbarianTraining : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            // Headless scenarios turn the world's NPCs off (golden tests need empty ground), as content/world Spawns does.
            if (System.getProperty("tp.worldSpawns") != "false") spawnNpc(Npcs.BRUT_OTTO, OTTO_TILE, OTTO_WANDER)
        }
        onOpNpc(Npcs.BRUT_OTTO, OP_FIRST) { otto ->
            player.stopInteraction()
            with(BarbarianOtto) { talk(otto) }
        }
        onOpLoc(Locs.BRUT_BARBARIAN_BED, OP_FIRST) { searchBed() }
        registerAnvil()
        registerMixes()
    }

    // ---- Otto's bed

    /** Wiki "Barbarian bed": searching it gives a barbarian rod "during or after" the miniquest, always (spare copies too). */
    private fun PlayerScript.searchBed() {
        val p = player
        if (!BarbarianProgress.rodTaught(p)) return p.message(NOTHING_INTERESTING)
        if (!p.transaction("barbarian bed") { add(Invs.INV, Objs.BRUT_FISHING_ROD, 1) }) {
            return p.message("You don't have enough inventory space.")
        }
        p.message("You find a barbarian rod under the bed.")
    }

    // ---- the barbarian anvil

    private fun ContentScope.registerAnvil() {
        for (tier in BarbarianData.SPEARS) {
            onUseOnLoc(tier.bar, Locs.BRUT_ANVIL) { forge(tier) }
            onUseOnLoc(tier.logs, Locs.BRUT_ANVIL) { forge(tier) }
        }
        onUseOnLoc(Objs.HAMMER, Locs.BRUT_ANVIL) { forge(null) }
        onOpLoc(Locs.BRUT_ANVIL, OP_FIRST) { forge(null) }
    }

    /** Whether the backpack holds a hammer, a bar of [tier] and its logs. */
    private fun PlayerRef.hasMaterials(tier: Spear): Boolean {
        val inv = inv(Invs.INV)
        return inv.total(tier.bar) > 0 && inv.total(tier.logs) > 0
    }

    /**
     * Wiki "Barbarian Training" > Smithing: a bar and its logs used on the barbarian anvil make a spear, or, once Otto has explained
     * them, a hasta. [tier] null (hammer or "Smith" on the anvil) picks the best metal the player has bar and logs for.
     */
    private suspend fun PlayerScript.forge(tier: Spear?) {
        val p = player
        if (p.varbit(Varbits.BRUT_SMITH_SPEAR) < BarbarianProgress.SPEAR_TAUGHT) return p.message(UNFAMILIAR_ANVIL)
        val inv = p.inv(Invs.INV)
        if (inv.total(Objs.HAMMER) == 0L && inv.total(Objs.IMCANDO_HAMMER) == 0L) {
            return p.message("You need a hammer to work the metal with.")
        }
        val metal = tier ?: BarbarianData.SPEARS.lastOrNull { p.hasMaterials(it) }
        if (metal == null || !p.hasMaterials(metal)) return p.message("You need a metal bar and the matching logs to make a spear.")
        if (p.level(Stats.SMITHING) < metal.level) {
            return p.message("You need a Smithing level of ${metal.level} to make ${p.objName(metal.spear).lowercase()}.")
        }
        val hasta = p.varbit(Varbits.BRUT_SMITH_HASTA) >= BarbarianProgress.HASTA_TAUGHT && choice(MAKE, "Spear", "Hasta") == 2
        p.stopInteraction()
        Production.start(p, Batch(1, Seqs.BRUT_HUMAN_SMITHING, FORGE_TICKS) { forgeOne(this, metal, hasta) }, FORGE_TICKS)
    }

    private fun forgeOne(p: PlayerRef, metal: Spear, hasta: Boolean): Boolean {
        val product = if (hasta) metal.hasta else metal.spear
        val made =
            p.transaction("barbarian anvil") {
                remove(Invs.INV, metal.bar, 1) && remove(Invs.INV, metal.logs, 1) &&
                    add(Invs.INV, product, 1)
            }
        if (!made) return false
        if (hasta) {
            p.setVarbit(Varbits.BRUT_SMITH_HASTA, BarbarianProgress.HASTA_MADE)
        } else if (p.varbit(Varbits.BRUT_SMITH_SPEAR) == BarbarianProgress.SPEAR_TAUGHT) {
            p.setVarbit(Varbits.BRUT_SMITH_SPEAR, BarbarianProgress.SPEAR_MADE)
        }
        p.message("You make ${p.objName(product).lowercase()}.")
        return Production.award(p, Stats.SMITHING, metal.xp)
    }

    // ---- barbarian mixes

    private fun ContentScope.registerMixes() {
        for (mix in BarbarianData.MIXES) {
            onUseOnItem(mix.potion, Objs.BRUT_CAVIAR) { stir(mix, Objs.BRUT_CAVIAR) }
            if (!mix.caviarOnly) onUseOnItem(mix.potion, Objs.BRUT_ROE) { stir(mix, Objs.BRUT_ROE) }
        }
    }

    /**
     * Wiki "Barbarian Training" > Barbarian potions: one roe (or caviar) and a two-dose potion make the two-dose mix, 1 tick, for
     * Herblore xp; Otto must have asked for the attack mix first (OPEN-BT5).
     */
    private fun PlayerRef.stir(mix: Mix, egg: Int) {
        if (varbit(Varbits.BRUT_HERB_POTION) < BarbarianProgress.HERB_TAUGHT) return message(NOTHING_INTERESTING)
        if (level(Stats.HERBLORE) < mix.level) {
            return message("You need a Herblore level of ${mix.level} to make ${objName(mix.mix).lowercase()}.")
        }
        interrupt()
        val made = transaction("barbarian mix") { remove(Invs.INV, mix.potion, 1) && remove(Invs.INV, egg, 1) && add(Invs.INV, mix.mix, 1) }
        if (!made) return
        anim(Seqs.HUMAN_HERBING_VIAL)
        addXp(Stats.HERBLORE, mix.xp)
    }

    private companion object {
        const val OP_FIRST = 1

        /** Wiki "Otto Godblessed" map pin: 2502,3489 in his grotto; the wander range is the pin's radius of 3. */
        val OTTO_TILE = CoordGrid(2502, 3489, 0)
        const val OTTO_WANDER = 3

        /** Smithing at an anvil is a 5 tick action (smithing.md; the barbarian anvil is not listed separately, OPEN-BT6). */
        const val FORGE_TICKS = 5

        /** Wiki "Barbarian anvil" trivia. */
        const val UNFAMILIAR_ANVIL = "This anvil is unfamiliar and barbaric, you can't seem to use it."

        const val NOTHING_INTERESTING = "Nothing interesting happens."
        const val MAKE = "What would you like to make?"
    }
}
