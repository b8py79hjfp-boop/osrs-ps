package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * The resource rooms' farming (resources.md FM1-FM4, HL2): the weeds, the tool rack, the two herb patches, the gourd tree
 * and the geyser. The patches, rack, tree and geyser are static locs of the resource cells (client-contract 6.4), so the
 * handlers are keyed by loc id and need no room set-up; a patch's plant lives in [CoxResState.patches].
 */
internal object CoxFarming {
    /** One seed: its herb, level, XP and the five patch locs (seed, growth 1-3, fully grown). */
    private class Herb(val seed: Int, val grimy: Int, val level: Int, val plantXp: Double, val harvestXp: Double, val locs: IntArray)

    /** FM3 table: golpar 27 (4 / 10), buchu 39 (6 / 15), noxifer 55 (12 / 30). */
    private val HERBS =
        listOf(
            Herb(
                Objs.RAIDS_SEED_GOLPAR,
                Objs.RAIDS_GRIMY_GOLPAR,
                GOLPAR_LEVEL,
                GOLPAR_PLANT_XP,
                GOLPAR_HARVEST_XP,
                intArrayOf(
                    Locs.RAIDS_PATCH_GOLPAR_SEED,
                    Locs.RAIDS_PATCH_GOLPAR_GROWTH1,
                    Locs.RAIDS_PATCH_GOLPAR_GROWTH2,
                    Locs.RAIDS_PATCH_GOLPAR_GROWTH3,
                    Locs.RAIDS_PATCH_GOLPAR_FULLYGROWN,
                ),
            ),
            Herb(
                Objs.RAIDS_SEED_BUCHULEAF,
                Objs.RAIDS_GRIMY_BUCHULEAF,
                BUCHU_LEVEL,
                BUCHU_PLANT_XP,
                BUCHU_HARVEST_XP,
                intArrayOf(
                    Locs.RAIDS_PATCH_BUCHU_SEED,
                    Locs.RAIDS_PATCH_BUCHU_GROWTH1,
                    Locs.RAIDS_PATCH_BUCHU_GROWTH2,
                    Locs.RAIDS_PATCH_BUCHU_GROWTH3,
                    Locs.RAIDS_PATCH_BUCHU_FULLYGROWN,
                ),
            ),
            Herb(
                Objs.RAIDS_SEED_NOXIFER,
                Objs.RAIDS_GRIMY_NOXIFER,
                NOXIFER_LEVEL,
                NOXIFER_PLANT_XP,
                NOXIFER_HARVEST_XP,
                intArrayOf(
                    Locs.RAIDS_PATCH_NOXIFER_SEED,
                    Locs.RAIDS_PATCH_NOXIFER_GROWTH1,
                    Locs.RAIDS_PATCH_NOXIFER_GROWTH2,
                    Locs.RAIDS_PATCH_NOXIFER_GROWTH3,
                    Locs.RAIDS_PATCH_NOXIFER_FULLYGROWN,
                ),
            ),
        )

    private const val GOLPAR_LEVEL = 27
    private const val GOLPAR_PLANT_XP = 4.0
    private const val GOLPAR_HARVEST_XP = 10.0
    private const val BUCHU_LEVEL = 39
    private const val BUCHU_PLANT_XP = 6.0
    private const val BUCHU_HARVEST_XP = 15.0
    private const val NOXIFER_LEVEL = 55
    private const val NOXIFER_PLANT_XP = 12.0
    private const val NOXIFER_HARVEST_XP = 30.0

    /** Every herb-patch loc: empty, then the 15 seed / growth / grown locs (client-contract 5.2: 29997-30011). */
    private val PATCH_LOCS: IntArray = intArrayOf(Locs.RAIDS_PATCH_EMPTY) + HERBS.flatMap { it.locs.toList() }

    /** The grimy herbs and what they clean to (FM3; OPEN-RS8: no level or XP is stated for cleaning). */
    private val CLEANING =
        mapOf(
            Objs.RAIDS_GRIMY_GOLPAR to Objs.RAIDS_GOLPAR,
            Objs.RAIDS_GRIMY_BUCHULEAF to Objs.RAIDS_BUCHULEAF,
            Objs.RAIDS_GRIMY_NOXIFER to Objs.RAIDS_NOXIFER,
        )

    /** FM3: a planted seed is the herb 30 seconds (50 ticks) later, shown in five stages (seed, three growths, grown). */
    private const val GROW_TICKS = 50L
    private const val LAST_STAGE = 4

    /** FM2 / OPEN-RS20: ticks per rake pull; the doc gives none. The weeds never run out (raking "continues until cancelled"). */
    private const val RAKE_TICKS = 3

    /** FM2: each success gives 1-3 of one seed, each seed 1/3. */
    private const val SEED_KINDS = 3
    private const val SEED_QTY_MAX = 3

    /** OPEN-RS7: herbs per patch. The doc only says it scales with the party; at least this many, else one per player. */
    private const val HERBS_MIN = 3

    /** OPEN-RS25: ticks to pick one herb; XP is given per herb. */
    private const val PICK_TICKS = 2

    private const val OP1 = 1
    private const val OP2 = 2
    private const val OP3 = 3
    private const val OP4 = 4

    fun register(scope: ContentScope) {
        with(scope) {
            onOpLoc(Locs.RAIDS_FARMING_TOOLS, OP1) { chooseTool(player) }
            onOpLoc(Locs.RAIDS_FARMING_TOOLS, OP2) { takeTool(player, Objs.RAKE) }
            onOpLoc(Locs.RAIDS_FARMING_TOOLS, OP3) { takeTool(player, Objs.SPADE) }
            onOpLoc(Locs.RAIDS_FARMING_TOOLS, OP4) { takeTool(player, Objs.DIBBER) }
            onOpLoc(Locs.RAIDS_WEEDS, OP1) { rake() }
            for (path in PATCH_LOCS) onOpLoc(path, OP2) { loc -> inspect(player, loc) }
            for ((i, herb) in HERBS.withIndex()) {
                onUseOnLoc(herb.seed, Locs.RAIDS_PATCH_EMPTY) { loc -> plant(loc, i) }
                onOpLoc(herb.locs[LAST_STAGE], OP1) { loc -> pick(loc, i) }
                onOpLoc(herb.locs[LAST_STAGE], OP3) { loc -> clear(player, loc) }
            }
            onOpLoc(Locs.RAIDS_GOURD_TREE, OP1) { pickVials(player, all = false) }
            onOpLoc(Locs.RAIDS_GOURD_TREE, OP2) { pickVials(player, all = true) }
            onUseOnLoc(Objs.RAIDS_VIAL_EMPTY, Locs.RAIDS_GEYSER) { fillVials(player) }
            for ((grimy, clean) in CLEANING) onItemOp(grimy, "Clean") { click -> clean(click.slot, grimy, clean) }
        }
    }

    // ---- tools and weeds (FM2)

    /** FM2: the rack's first op offers a choice of the three tools (right-click selects one, 18 Jun 2020). */
    private fun chooseTool(player: PlayerRef) {
        player.choiceDialog("Take which tool?", listOf("Rake", "Spade", "Seed dibber")) { pick ->
            takeTool(this, intArrayOf(Objs.RAKE, Objs.SPADE, Objs.DIBBER)[pick - 1])
        }
    }

    private fun takeTool(player: PlayerRef, tool: Int) {
        if (CoxFlow.runOf(player) == null) return
        if (CoxRes.held(player, tool) > 0L) return player.message("You already have one.")
        if (!player.transaction("cox_take_tool") { add(Invs.INV, tool, 1) }) player.message(FULL)
    }

    /** FM2: "Rake" until cancelled; each pull gives one of buchu, golpar or noxifer seed(s) with 1/3 each, quantity 1-3. */
    private suspend fun PlayerScript.rake() {
        if (CoxFlow.runOf(player) == null) return
        if (CoxRes.held(player, Objs.RAKE) == 0L) return player.message("You need a rake to clear the weeds.")
        while (CoxRes.held(player, Objs.RAKE) > 0L) {
            player.anim(Seqs.FARMING_RAKING)
            delay(RAKE_TICKS)
            val seed = HERBS[player.random(SEED_KINDS)].seed
            val qty = 1 + player.random(SEED_QTY_MAX)
            if (!player.transaction("cox_rake_weeds") { addOrDrop(Invs.INV, seed, qty) }) return
        }
    }

    // ---- patches (FM3)

    private fun inspect(player: PlayerRef, loc: LocRef) {
        val stage = HERBS.firstNotNullOfOrNull { h -> h.locs.indexOf(loc.id).takeIf { it >= 0 } }
        player.message(
            when {
                loc.id == Locs.RAIDS_PATCH_EMPTY -> "An empty herb patch."
                stage == LAST_STAGE -> "The herb is fully grown."
                else -> "The herb is still growing."
            },
        )
    }

    /** FM3: a seed on an empty patch, with a seed dibber and the Farming level; planting gives the plant XP. */
    private suspend fun PlayerScript.plant(loc: LocRef, herbIndex: Int) {
        val run = CoxFlow.runOf(player) ?: return
        val herb = HERBS[herbIndex]
        if (CoxRes.held(player, Objs.DIBBER) == 0L) return player.message("You need a seed dibber to plant that.")
        if (player.level(Stats.FARMING) < herb.level) return player.message("You need a Farming level of ${herb.level} to plant that.")
        player.anim(Seqs.FARMING_SEED_DIBBING)
        delay(PICK_TICKS)
        if (player.locAt(loc.coord, loc.shape)?.id != Locs.RAIDS_PATCH_EMPTY) return
        if (!player.transaction("cox_plant") { remove(Invs.INV, herb.seed, 1) }) return
        player.addLoc(herb.locs[0], loc.coord, loc.shape, loc.angle)
        CoxRes.state(player, run).patches[loc.coord] = CoxPatch(herbIndex, player.tick, loc.shape, loc.angle, herbsPerPatch(run))
        player.addXp(Stats.FARMING, herb.plantXp)
    }

    private fun herbsPerPatch(run: RaidRun): Int = maxOf(HERBS_MIN, run.scaling.scaledSize)

    /** FM3: "Pick" takes the herbs one by one (to the ground when the backpack is full); the patch is empty after the last. */
    private suspend fun PlayerScript.pick(loc: LocRef, herbIndex: Int) {
        val run = CoxFlow.runOf(player) ?: return
        val herb = HERBS[herbIndex]
        val st = CoxRes.state(player, run)
        val patch = st.patches[loc.coord] ?: return
        while (patch.remaining > 0 && player.locAt(loc.coord, loc.shape)?.id == herb.locs[LAST_STAGE]) {
            player.anim(Seqs.HUMAN_PICKUPFLOOR)
            delay(PICK_TICKS)
            if (patch.remaining <= 0) return
            patch.remaining--
            player.transaction("cox_pick_herb") { addOrDrop(Invs.INV, herb.grimy, 1) }
            player.addXp(Stats.FARMING, herb.harvestXp)
        }
        if (patch.remaining <= 0) emptyPatch(player, run, st, loc)
    }

    /** FM3: "Clear" removes the plant. */
    private fun clear(player: PlayerRef, loc: LocRef) {
        val run = CoxFlow.runOf(player) ?: return
        emptyPatch(player, run, CoxRes.state(player, run), loc)
    }

    /** FM3: the herb does not grow back; a party notice is shown when someone clears a patch (23 Feb 2017, OPEN-RS30: wording). */
    private fun emptyPatch(player: PlayerRef, run: RaidRun, st: CoxResState, loc: LocRef) {
        if (st.patches.remove(loc.coord) == null) return
        player.addLoc(Locs.RAIDS_PATCH_EMPTY, loc.coord, loc.shape, loc.angle)
        for (member in run.presentPlayers(player)) member.message("${player.username} has cleared a herb patch.")
    }

    /** Growth: the stage follows the ticks since planting (the loc changes four times in [GROW_TICKS]). */
    fun tick(world: WorldRef, st: CoxResState, now: Long) {
        if (st.patches.isEmpty()) return
        for ((coord, patch) in st.patches) {
            if (patch.stage >= LAST_STAGE) continue
            val stage = ((now - patch.plantedTick) * LAST_STAGE / GROW_TICKS).toInt().coerceIn(0, LAST_STAGE)
            if (stage == patch.stage) continue
            patch.stage = stage
            world.addLoc(HERBS[patch.herb].locs[stage], coord, patch.shape, patch.angle)
        }
    }

    // ---- gourd tree and geyser (HL2)

    /** HL2: "Pick" gives an empty gourd vial, "Pick-lots" fills the backpack with them. */
    private fun pickVials(player: PlayerRef, all: Boolean) {
        if (CoxFlow.runOf(player) == null) return
        val free = player.inv(Invs.INV).freeSlots
        if (free == 0) return player.message(FULL)
        player.transaction("cox_pick_vials") { add(Invs.INV, Objs.RAIDS_VIAL_EMPTY, if (all) free else 1) }
    }

    /** HL2: an empty gourd vial on the geyser fills every empty vial carried (OPEN-RS31: one or all is not stated). */
    private fun fillVials(player: PlayerRef) {
        if (CoxFlow.runOf(player) == null) return
        val inv = player.inv(Invs.INV)
        val slots = (0 until inv.size).filter { inv.obj(it) == Objs.RAIDS_VIAL_EMPTY }
        if (slots.isEmpty()) return
        player.anim(Seqs.HUMAN_FILLBUCKET_SANDPIT)
        player.transaction("cox_fill_vials") { slots.all { replaceAt(Invs.INV, it, Objs.RAIDS_VIAL_EMPTY, Objs.RAIDS_VIAL_WATER) } }
    }

    /** FM3 / OPEN-RS8: a grimy herb cleans to the clean herb, with no level and no XP. */
    private fun PlayerRef.clean(slot: Int, grimy: Int, clean: Int) {
        if (inv(Invs.INV).obj(slot) != grimy) return
        transaction("cox_clean_herb") { replaceAt(Invs.INV, slot, grimy, clean) }
    }

    private const val FULL = "You don't have enough free inventory space to do that."
}
