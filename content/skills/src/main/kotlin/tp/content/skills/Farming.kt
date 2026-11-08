package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.InvView
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Farming (docs/mechanics/farming.md): every farming patch of the Wiki "Farming/Patch locations" the cache has locs for (allotment,
 * flower, herb, hops, bush, tree, fruit tree, hardwood, redwood, spirit tree, calquat, celastrus, cactus, mushroom, belladonna,
 * seaweed, anima, Hespori and crystal tree patches, including the Farming Guild), the compost bins, plant pots and saplings, the
 * tool leprechaun's store, the farmers' protection payments and the Farming Guild's contracts.
 *
 * Patches belong to the player: each one's state is a saved Long ([PatchState]) and the minute it was last brought up to
 * date, so crops keep growing while the player is offline ([FarmingGrowth], [FarmingPatches]). The client draws a patch from a
 * varbit shared by all farms, so while the player is at a farm the plugin writes that farm's patch states into the varbits
 * ([syncSite]); a click on a patch reports the patch's multi-loc parent id, which names the patch.
 */
public class Farming : ContentPlugin {
    private val crops = CropTable.load()
    private val varbits = VarbitTable.load()
    private val chains = SaplingTable.load()
    private val payments = PaymentTable.load()
    private val patches = FarmingPatches(crops, varbits)
    private val saplings = FarmingSaplings(patches, chains)
    private val bins = FarmingBins(patches)
    private val tools = FarmingTools(crops, chains)
    private val contracts = FarmingContracts(patches, chains)
    private val guild = FarmingGuild()

    /** What a farmed tree needs to be chopped (Wiki "Woodcutting": the regular tree of the kind, or [OPEN-FA2] for the new hardwoods). */
    private class ChopSpec(val level: Int, val xp: Double, val rates: AxeRates)

    private val chopSpecs: Map<String, ChopSpec> =
        buildMap {
            for ((key, kind) in TREE_KINDS) put(key, ChopSpec(kind.level, kind.xp, kind.rates))
            // Wiki "Camphor tree" / "Ironwood tree" / "Rosewood tree": level and xp; the axe success rates are OPEN-FA2 (mahogany's).
            put("CAMPHOR", ChopSpec(CAMPHOR_LEVEL, CAMPHOR_XP, AxeRates.MAHOGANY))
            put("IRONWOOD", ChopSpec(IRONWOOD_LEVEL, IRONWOOD_XP, AxeRates.MAHOGANY))
            put("ROSEWOOD", ChopSpec(ROSEWOOD_LEVEL, ROSEWOOD_XP, AxeRates.MAHOGANY))
        }

    /** Axe -> woodcutting level it needs (cache param `levelrequire`). */
    private var axeLevels: Map<Int, Int> = emptyMap()

    /** Farmer npc -> the patches whose protection it sells. */
    private val farmers: Map<Int, List<Patch>> =
        FarmingSites.SITES
            .flatMap { it.patches }
            .flatMap { patch -> patch.farmers.map { it to patch } }
            .groupBy({ it.first }, { it.second })

    override fun ContentScope.register() {
        onWorldStart { axeLevels = AXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 } }
        for (site in FarmingSites.SITES) {
            for (patch in site.patches) {
                for (loc in patch.locs) {
                    onOpLoc(loc, OP_PRIMARY) { act(player, patch) }
                    onOpLoc(loc, OP_INSPECT) { inspect(player, patch) }
                    onOpLoc(loc, OP_CLEAR) { clearOp(player, patch) }
                    onUseOnLoc(ANY_ITEM, loc) { useOn(player, patch, used?.obj ?: ANY_ITEM) }
                }
                if (patch.type == PatchType.SPIRIT_TREE) {
                    for (loc in patch.locs) onOpLoc(loc, OP_SPIRIT_INSPECT) { inspect(player, patch) }
                }
            }
        }
        for (npc in FarmingSites.LEPRECHAUNS) onUseOnNpc(ANY_ITEM, npc) { note(player, used?.obj ?: ANY_ITEM) }
        for ((npc, owned) in farmers) {
            onUseOnNpc(ANY_ITEM, npc) { payWith(player, owned, used?.obj ?: ANY_ITEM) }
            onOpNpc(npc, OP_PAY_FIRST) { pay(player, owned, 0) }
            if (owned.size > 1 && owned.map { it.payIndex }.distinct().size > 1) onOpNpc(npc, OP_PAY_SECOND) { pay(player, owned, 1) }
        }
        with(saplings) { registerSaplings() }
        with(bins) { registerBins() }
        with(tools) { registerTools() }
        with(contracts) { registerContracts() }
        with(guild) { registerGuild() }
        onLogin {
            syncSite()
            saplings.process(this)
            timer(SYNC, SYNC_TICKS, soft = true) {
                syncSite()
                saplings.process(this)
            }
        }
    }

    // ---- state

    /** Draws every patch and bin of the nearest farm, again every [FarmingConst.REFRESH_TICKS] ticks while the player stays (F8). */
    private fun PlayerRef.syncSite() {
        val sync = patches.sync(this)
        val site = FarmingSites.nearestSite(coord.x, coord.z)
        val due = site >= 0 && tick - sync.drawnAt >= FarmingConst.REFRESH_TICKS
        if (site == sync.site && !due) return
        sync.site = site
        sync.drawnAt = tick
        if (site < 0) return
        patches.drawSite(this, site)
        bins.draw(this, FarmingSites.SITES[site])
    }

    // ---- clicks

    /** Op 1 of a patch: the action of whatever the patch holds, as the client labels it (Rake, Harvest, Pick, Cure, Clear ...). */
    private fun act(p: PlayerRef, patch: Patch) {
        val state = patches.load(p, patch).state
        when (state.phase) {
            Phase.WEEDS -> if (state.stage > 0) rake(p, patch) else p.message("The patch is already clear.")
            Phase.GROWING -> p.message("The ${crops.at(state.crop)?.name?.lowercase() ?: "crop"} is still growing.")
            Phase.DISEASED -> treatDiseased(p, patch, ANY_ITEM)
            Phase.DEAD, Phase.STUMP -> dig(p, patch)
            Phase.HARVESTABLE -> harvestOrChop(p, patch, state)
            Phase.CHECK -> check(p, patch)
        }
    }

    /** Op 5 of a patch ("Clear"): digs up a dead crop, a stump or a finished plant (bush, cactus, spirit tree, anima plant ...). */
    private fun clearOp(p: PlayerRef, patch: Patch) {
        val state = patches.load(p, patch).state
        when (state.phase) {
            Phase.DEAD, Phase.STUMP -> dig(p, patch)
            Phase.HARVESTABLE -> if (patch.type.harvest == Harvest.CHOP) p.message(NOTHING) else dig(p, patch)
            else -> p.message(NOTHING)
        }
    }

    /** Op 2 of a patch ("Inspect"). Texts are OPEN-FM10 (the Wiki's per-model examines are not imported). */
    private fun inspect(p: PlayerRef, patch: Patch) {
        val state = patches.load(p, patch).state
        val crop = crops.at(state.crop)
        val name = crop?.name?.lowercase() ?: "crop"
        p.message(
            when (state.phase) {
                Phase.WEEDS -> {
                    if (state.stage >
                        0
                    ) {
                        "The ${patch.type.label} is overgrown with weeds."
                    } else {
                        "The ${patch.type.label} is empty."
                    }
                }

                Phase.GROWING -> {
                    "A $name is growing in this ${patch.type.label}."
                }

                Phase.DISEASED -> {
                    "This $name is diseased."
                }

                Phase.DEAD -> {
                    "This $name has become diseased and died."
                }

                Phase.HARVESTABLE -> {
                    "This $name is fully grown."
                }

                Phase.CHECK -> {
                    "This $name is fully grown. Its health has not been checked."
                }

                Phase.STUMP -> {
                    "You can uproot this stump with a spade."
                }
            },
        )
    }

    /** An item used on a patch (F3). */
    private fun useOn(p: PlayerRef, patch: Patch, obj: Int) {
        val seed = crops.bySeed[obj]
        when {
            obj == Objs.RAKE -> rakeClick(p, patch)
            seed != null -> plant(p, patch, seed)
            obj in SeedBox.BOXES -> plantFromBox(p, patch)
            obj == Objs.PLANTPOT_EMPTY -> saplings.fillPot(p, patch)
            obj in FarmingItems.COMPOST -> compost(p, patch, obj)
            obj in FarmingItems.CANS -> water(p, patch, obj)
            obj == Objs.PLANT_CURE || obj in FarmingItems.SECATEURS -> treatDiseased(p, patch, obj)
            obj == Objs.SPADE -> act(p, patch)
            else -> p.message(NOTHING)
        }
    }

    private fun rakeClick(p: PlayerRef, patch: Patch) {
        val state = patches.load(p, patch).state
        if (state.phase == Phase.WEEDS && state.stage > 0) rake(p, patch) else p.message(NOTHING)
    }

    // ---- actions

    /** F5: one rake pull every [FarmingConst.RAKE_TICKS] ticks, a weed and 4 xp each, until the patch is clear. */
    private fun rake(p: PlayerRef, patch: Patch) {
        if (p.inv(Invs.INV).total(Objs.RAKE) == 0L) return p.message("You need a rake to clear this patch.")
        Production.start(
            p,
            Batch(Int.MAX_VALUE, Seqs.FARMING_RAKING, FarmingConst.RAKE_TICKS) { rakeOnce(this, patch) },
            FarmingConst.RAKE_TICKS,
        )
    }

    private fun rakeOnce(p: PlayerRef, patch: Patch): Boolean {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        if (state.phase != Phase.WEEDS || state.stage == 0) return false
        if (!p.transaction("farming-rake") { addOrDrop(Invs.INV, Objs.WEEDS, 1) }) return false
        patches.store(p, patch, state.with(stage = state.stage - 1), p.epochMinutes)
        p.addXp(Stats.FARMING, FarmingConst.RAKE_XP)
        return state.stage > 1
    }

    /**
     * SB6 Wiki "Seed box": "Using the box on a patch will automatically plant the first applicable seed of that patch's type"
     * (box order; enough seeds and the Farming level). **OPEN-SB2:** the message when none fits.
     */
    private fun plantFromBox(p: PlayerRef, patch: Patch) {
        val box = p.inv(Invs.SEED_BOX)
        val crop =
            (0 until box.size)
                .mapNotNull { box.obj(it).takeIf { o -> o >= 0 }?.let { o -> crops.bySeed[o] } }
                .firstOrNull { it.type == patch.type && p.level(Stats.FARMING) >= it.level && box.total(it.seed) >= it.seedsPer }
                ?: return p.message("You don't have any seeds in your seed box that can be planted here.")
        plant(p, patch, crop, Invs.SEED_BOX)
    }

    /** F3: sow [crop] on a raked patch: the seeds (or sapling) are used up, the planting xp is given at once (rev 242, OPEN-FM1). */
    private fun plant(p: PlayerRef, patch: Patch, crop: Crop, from: Int = Invs.INV) {
        val state = patches.load(p, patch).state
        val tool = if (crop.isSapling) Objs.SPADE else Objs.DIBBER
        val problem =
            when {
                crop.type != patch.type -> "You can't plant that in a ${patch.type.label}."
                state.phase == Phase.WEEDS && state.stage > 0 -> "You need to rake the weeds off the patch first."
                state.phase != Phase.WEEDS -> "There is already something growing here."
                p.level(Stats.FARMING) < crop.level -> "You need a Farming level of ${crop.level} to plant this."
                p.inv(Invs.INV).total(tool) == 0L -> "You need a ${p.objName(tool).lowercase()} to plant this."
                p.inv(from).total(crop.seed) < crop.seedsPer -> "You need ${crop.seedsPer} seeds to plant in this patch."
                else -> null
            }
        if (problem != null) return p.message(problem)
        val anim = if (crop.isSapling) Seqs.HUMAN_DIG else Seqs.FARMING_SEED_DIBBING
        Production.start(p, Batch(1, anim, FarmingConst.PLANT_TICKS) { plantNow(this, patch, crop, from) }, FarmingConst.PLANT_TICKS)
    }

    private fun plantNow(p: PlayerRef, patch: Patch, crop: Crop, from: Int): Boolean {
        val state = patches.load(p, patch).state
        if (state.phase != Phase.WEEDS || state.stage != 0) return false
        // Wiki "Plant pot": planting a sapling returns the empty plant pot.
        val pot = chains.chains.any { it.sapling == crop.seed }
        val done =
            p.transaction("farming-plant") {
                remove(from, crop.seed, crop.seedsPer) && (!pot || add(Invs.INV, Objs.PLANTPOT_EMPTY, 1))
            }
        if (!done) return false
        patches.store(p, patch, state.with(crop = crop.index, phase = Phase.GROWING, stage = 0, watered = false), p.epochMinutes)
        p.addXp(Stats.FARMING, crop.plantXp)
        p.message("You plant the ${p.objName(crop.seed).lowercase()} in the ${patch.type.label}.")
        return true
    }

    /** F4: compost, supercompost or ultracompost on a patch that has none (any stage but fully grown). */
    private fun compost(p: PlayerRef, patch: Patch, bucket: Int) {
        val state = patches.load(p, patch).state
        val tier = FarmingItems.COMPOST.getValue(bucket)
        val problem =
            when {
                state.phase == Phase.WEEDS && state.stage > 0 -> "You need to rake the weeds off the patch first."
                state.phase == Phase.DEAD || state.phase == Phase.STUMP -> "You need to clear the patch first."
                state.phase == Phase.HARVESTABLE || state.phase == Phase.CHECK -> "The crop is fully grown; compost would do nothing."
                state.compost > 0 -> "This patch has already been treated with compost."
                else -> null
            }
        if (problem != null) return p.message(problem)
        Production.start(
            p,
            Batch(1, Seqs.FARMING_POUR_WATER, FarmingConst.COMPOST_TICKS) {
                compostNow(this, patch, bucket, tier)
            },
            FarmingConst.COMPOST_TICKS,
        )
    }

    private fun compostNow(p: PlayerRef, patch: Patch, bucket: Int, tier: Int): Boolean {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        if (state.compost > 0 || state.phase == Phase.HARVESTABLE || state.phase == Phase.CHECK) return false
        if (!p.transaction("farming-compost") { remove(Invs.INV, bucket, 1) && add(Invs.INV, Objs.BUCKET_EMPTY, 1) }) return false
        patches.store(p, patch, state.with(compost = tier), cursor.anchor)
        p.addXp(Stats.FARMING, FarmingConst.COMPOST_XP[tier])
        p.message("You treat the ${patch.type.label} with ${p.objName(bucket).lowercase().replace("bucket of ", "")}.")
        return true
    }

    /** F4: watering a growing allotment, flower or hops patch: the next growth tick's disease chance is halved (OPEN-FM3). */
    private fun water(p: PlayerRef, patch: Patch, can: Int) {
        val state = patches.load(p, patch).state
        val problem =
            when {
                !patch.type.canWater -> "Watering this would do nothing."
                state.phase != Phase.GROWING -> "The patch doesn't need watering."
                state.watered -> "This patch has already been watered."
                else -> null
            }
        if (problem != null) return p.message(problem)
        Production.start(
            p,
            Batch(1, Seqs.FARMING_WATERING, FarmingConst.WATER_TICKS) { waterNow(this, patch, can) },
            FarmingConst.WATER_TICKS,
        )
    }

    private fun waterNow(p: PlayerRef, patch: Patch, can: Int): Boolean {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        if (state.phase != Phase.GROWING || state.watered) return false
        val doses = FarmingItems.CANS.getValue(can)
        if (!p.transaction("farming-water") { remove(Invs.INV, can, 1) && add(Invs.INV, FarmingItems.CAN_WITH[doses - 1], 1) }) return false
        patches.store(p, patch, state.with(watered = true), cursor.anchor)
        p.message("You water the ${patch.type.label}.")
        return true
    }

    /**
     * F4: a diseased crop is cured with plant cure or pruned with secateurs, 3 in 4 times (Wiki "Disease (Farming)": plant cure for
     * herbs, flowers, allotments, hops, cacti, belladonna, mushrooms and seaweed; secateurs for trees, fruit trees, spirit trees,
     * bushes, calquat and redwood; hardwoods and celastrus take either). [tool] is what the player used on the patch, or [ANY_ITEM]
     * when they clicked "Cure" / "Prune" and the right tool is looked for.
     */
    private fun treatDiseased(p: PlayerRef, patch: Patch, tool: Int) {
        val state = patches.load(p, patch).state
        if (state.phase != Phase.DISEASED) return p.message(NOTHING)
        val inv = p.inv(Invs.INV)
        val cure = patch.type.cure
        val canPrune = cure != Cure.PLANT_CURE
        val canCure = cure != Cure.SECATEURS
        val secateurs = if (canPrune) FarmingItems.SECATEURS.firstOrNull { carries(inv, it, tool) } else null
        val plantCure = if (canCure && carries(inv, Objs.PLANT_CURE, tool)) Objs.PLANT_CURE else null
        val wrongTool = (tool == Objs.PLANT_CURE && !canCure) || (tool in FarmingItems.SECATEURS && !canPrune)
        if (wrongTool) return p.message(NOTHING)
        val have = secateurs ?: plantCure
        if (have == null) {
            return p.message(if (cure == Cure.SECATEURS) NEED_SECATEURS else NEED_CURE)
        }
        val anim = if (have == plantCure) Seqs.FARMING_PLANT_CURE else Seqs.PRUNING_ALL
        Production.start(p, Batch(1, anim, FarmingConst.CURE_TICKS) { treatNow(this, patch, have) }, FarmingConst.CURE_TICKS)
    }

    /** Whether the backpack holds [item] and the player either used it or used nothing in particular ([tool] [ANY_ITEM]). */
    private fun carries(inv: InvView, item: Int, tool: Int): Boolean = inv.total(item) > 0 && (tool == ANY_ITEM || tool == item)

    private fun treatNow(p: PlayerRef, patch: Patch, tool: Int): Boolean {
        val state = patches.load(p, patch).state
        if (state.phase != Phase.DISEASED) return false
        if (tool in FarmingItems.SECATEURS) {
            if (p.random(FarmingConst.PRUNE_FAIL_IN) == 0) {
                p.message("There are still some diseased leaves left.")
                return true
            }
            p.message("You have successfully removed all the diseased leaves.")
        } else {
            if (!p.transaction("farming-cure") { remove(Invs.INV, tool, 1) && add(Invs.INV, Objs.VIAL_EMPTY, 1) }) return false
            p.message("You treat the ${patch.type.label} with the plant cure.")
        }
        patches.store(p, patch, state.with(phase = Phase.GROWING, watered = false), p.epochMinutes)
        return true
    }

    /** F5: a spade clears a dead crop at once; a stump may need several tries (OPEN-FM7: success is Farming level out of 99). */
    private fun dig(p: PlayerRef, patch: Patch) {
        if (p.inv(Invs.INV).total(Objs.SPADE) == 0L) return p.message("You need a spade to clear this patch.")
        Production.start(p, Batch(Int.MAX_VALUE, Seqs.HUMAN_DIG, FarmingConst.DIG_TICKS) { digOnce(this, patch) }, FarmingConst.DIG_TICKS)
    }

    private fun digOnce(p: PlayerRef, patch: Patch): Boolean {
        val phase = patches.load(p, patch).state.phase
        if (phase != Phase.DEAD && phase != Phase.STUMP && phase != Phase.HARVESTABLE) return false
        if (phase == Phase.STUMP && p.random(Stats.MAX_LEVEL) >= p.level(Stats.FARMING)) return true
        patches.clear(p, patch)
        p.message("You clear the ${patch.type.label}.")
        return false
    }

    /**
     * F7: checking a fully grown tree's health gives its big xp and makes it choppable (tree), fruiting (fruit tree, calquat), pickable
     * (bush, cactus, celastrus) or usable (spirit tree).
     */
    private fun check(p: PlayerRef, patch: Patch) {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop) ?: return
        if (state.phase != Phase.CHECK) return
        patches.store(p, patch, FarmingGrowth.harvestable(state, crop), p.epochMinutes)
        p.addXp(Stats.FARMING, crop.checkXp)
        p.message("You examine the ${patch.type.label.removeSuffix(" patch")} for signs of disease and find that it is healthy.")
        contracts.onChecked(p, patch, crop)
    }

    // ---- harvest

    private fun harvestOrChop(p: PlayerRef, patch: Patch, state: PatchState) {
        val crop = crops.at(state.crop) ?: return
        val type = patch.type
        when {
            type.harvest == Harvest.CHOP || (type == PatchType.FRUIT_TREE && state.stage == 0) -> {
                chop(p, patch, crop)
            }

            type.harvest == Harvest.NONE -> {
                nothingToHarvest(p, type)
            }

            // a bare bush, cactus or calquat can only be cleared
            (type.usesLives && state.lives == 0) || (type.harvest == Harvest.FRUIT && state.stage == 0) -> {
                dig(p, patch)
            }

            (type == PatchType.ALLOTMENT || type == PatchType.HOPS) && p.inv(Invs.INV).total(Objs.SPADE) == 0L -> {
                p.message("You need a spade to harvest here.")
            }

            else -> {
                val anim =
                    when {
                        type == PatchType.MUSHROOM -> Seqs.FARMING_PICK_MUSHROOM
                        type == PatchType.FRUIT_TREE || type == PatchType.CALQUAT -> Seqs.PICKING_HIGH
                        else -> Seqs.PICKING_LOW
                    }
                Production.start(
                    p,
                    Batch(Int.MAX_VALUE, anim, FarmingConst.PICK_TICKS) { harvestOnce(this, patch) },
                    FarmingConst.PICK_TICKS,
                )
            }
        }
    }

    /** F-N: spirit trees (travel: OPEN-FA7), anima plants (they act while growing) and the Hespori (fight: OPEN-FA8) have no harvest. */
    private fun nothingToHarvest(p: PlayerRef, type: PatchType) {
        p.message(
            when (type) {
                PatchType.HESPORI -> "The Hespori can't be woken yet."
                PatchType.SPIRIT_TREE -> "Spirit tree travel isn't available from this patch yet."
                else -> NOTHING
            },
        )
    }

    /**
     * F6: one pick: the produce and its xp, then a roll to keep a harvest life (allotment, herb ...); the patch is cleared when none
     * are left. Fruit trees give one of their fruit per pick. Returns whether the player goes on picking.
     */
    private fun harvestOnce(p: PlayerRef, patch: Patch): Boolean {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop)
        if (crop == null || state.phase != Phase.HARVESTABLE) return false
        val type = patch.type
        val fruit = type.harvest == Harvest.FRUIT
        if (fruit && state.stage == 0) return false
        val amount = yield(p, crop)
        if (!p.transaction("farming-harvest") { add(Invs.INV, crop.produce, amount) }) {
            p.message("Your inventory is too full to hold any more ${p.objName(crop.produce).lowercase()}.")
            return false
        }
        p.addXp(Stats.FARMING, crop.harvestXp)
        if (fruit) return pickFruit(p, patch, cursor, crop)
        if (type.harvest == Harvest.SINGLE) {
            patches.clear(p, patch)
            contracts.onHarvested(p, patch, crop)
            return false
        }
        val lives = if (type.usesLives && keepsLife(p, crop)) state.lives else state.lives - 1
        if (lives > 0) {
            patches.store(p, patch, state.with(lives = lives), cursor.anchor)
            return true
        }
        if (crop.regrows) {
            patches.store(p, patch, state.with(lives = 0, stage = 0), p.epochMinutes)
        } else {
            patches.clear(p, patch)
        }
        contracts.onHarvested(p, patch, crop)
        return false
    }

    /** One fruit off a fruit tree, calquat or mushroom patch; a picked-out mushroom patch is clear again. */
    private fun pickFruit(p: PlayerRef, patch: Patch, cursor: Cursor, crop: Crop): Boolean {
        val state = cursor.state
        val left = state.stage - 1
        if (patch.type == PatchType.MUSHROOM && left == 0) {
            patches.clear(p, patch)
            contracts.onHarvested(p, patch, crop)
            return false
        }
        val anchor = if (state.stage == FarmingConst.MAX_FRUIT) p.epochMinutes else cursor.anchor
        patches.store(p, patch, state.with(stage = left), anchor)
        return left > 0
    }

    /**
     * Items from one pick: one; for limpwurt three plus a bonus that grows with the level (OPEN-FM9); for belladonna six plus a bonus
     * that grows to 12 at level 99 (OPEN-FA4); 28 to 32 shards from a crystal tree (Wiki "Crystal tree").
     */
    private fun yield(p: PlayerRef, crop: Crop): Int =
        when {
            crop.key == FarmingConst.LIMPWURT_KEY -> {
                val bonus = maxOf(0, (p.level(Stats.FARMING) - 1) / FarmingConst.LIMPWURT_STEP)
                FarmingConst.LIMPWURT_MIN + p.random(bonus + 1)
            }

            crop.key == FarmingConst.BELLADONNA_KEY -> {
                val span = Stats.MAX_LEVEL - crop.level
                val bonus = maxOf(0, (p.level(Stats.FARMING) - crop.level) * FarmingConst.BELLADONNA_BONUS_MAX / maxOf(span, 1))
                FarmingConst.BELLADONNA_MIN + p.random(bonus + 1)
            }

            crop.type == PatchType.CRYSTAL_TREE -> {
                FarmingConst.CRYSTAL_SHARDS_MIN + p.random(FarmingConst.CRYSTAL_SHARDS_SPAN)
            }

            else -> {
                1
            }
        }

    /**
     * F6: the roll to keep a harvest life; magic secateurs (held or wielded) raise both constants by 10% (not for seaweed, Wiki
     * "Farming#Average yield per crop type"), a live Attas plant by 5% (Wiki "Attas plant").
     */
    private fun keepsLife(p: PlayerRef, crop: Crop): Boolean {
        val magic =
            crop.type != PatchType.SEAWEED &&
                (
                    p.inv(Invs.INV).total(Objs.FAIRY_ENCHANTED_SECATEURS) > 0 ||
                        p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT) == Objs.FAIRY_ENCHANTED_SECATEURS
                )
        var low = if (magic) crop.ctsLow * FarmingConst.SECATEURS_PERCENT / PERCENT else crop.ctsLow
        var high = if (magic) crop.ctsHigh * FarmingConst.SECATEURS_PERCENT / PERCENT else crop.ctsHigh
        if (patches.anima(p)?.key == FarmingPatches.ATTAS) {
            low = low * FarmingConst.ATTAS_PERCENT / PERCENT
            high = high * FarmingConst.ATTAS_PERCENT / PERCENT
        }
        return p.statRandom(Stats.FARMING, low, high)
    }

    // ---- chopping

    /** F7: a grown tree is chopped like any tree of its kind and falls with chance 1/8 per log (OPEN-FM6 for the fruit trees). */
    private fun chop(p: PlayerRef, patch: Patch, crop: Crop) {
        val spec = chopSpecs[crop.key]
        if (spec != null && p.level(Stats.WOODCUTTING) < spec.level) {
            return p.message("You need a Woodcutting level of ${spec.level} to chop down this tree.")
        }
        val axe = Skilling.bestTool(p, Stats.WOODCUTTING, axeLevels)
        if (axe < 0) return p.message("You need an axe to chop down this tree.")
        Production.start(
            p,
            Batch(Int.MAX_VALUE, AXES.getValue(axe).anim, FarmingConst.CHOP_TICKS) {
                chopOnce(this, patch)
            },
            FarmingConst.CHOP_TICKS,
        )
    }

    private fun chopOnce(p: PlayerRef, patch: Patch): Boolean {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop)
        if (crop == null || state.phase != Phase.HARVESTABLE) return false
        val spec = chopSpecs[crop.key]
        if (spec == null) {
            // OPEN-FM6: a fruit tree falls to one successful swing and gives no logs.
            patches.store(p, patch, state.with(phase = Phase.STUMP, stage = 0), p.epochMinutes)
            return false
        }
        val axe = Skilling.bestTool(p, Stats.WOODCUTTING, axeLevels)
        if (axe < 0) return false
        val tier = AXES.getValue(axe).tier
        if (!p.statRandom(Stats.WOODCUTTING, spec.rates.low(tier), spec.rates.high(tier))) return true
        if (!p.transaction("farming-chop") { add(Invs.INV, crop.produce, 1) }) {
            p.message("Your inventory is too full to hold any more ${p.objName(crop.produce).lowercase()}.")
            return false
        }
        p.addXp(Stats.WOODCUTTING, spec.xp)
        p.message("You get some ${p.objName(crop.produce).lowercase()}.", Skilling.SPAM)
        if (p.random(FarmingConst.TREE_DEPLETE_IN) != 0) return true
        patches.store(p, patch, state.with(phase = Phase.STUMP, stage = 0), p.epochMinutes)
        return false
    }

    // ---- farmers

    /**
     * F-P1: pay a farmer (op 3 / op 4 "Pay"). A farmer with two patches at the farm the player stands at has one op for each
     * (the cache names them "Pay (north)" / "Pay (south)"); otherwise the patch the player can pay for is the one protected.
     */
    private fun pay(p: PlayerRef, owned: List<Patch>, index: Int) {
        val here = nearbyPatches(p, owned)
        val target =
            when {
                here.isEmpty() -> null
                here.size == 2 -> here.firstOrNull { it.payIndex == index }
                else -> here.firstOrNull { affordable(p, it) } ?: here.first()
            }
        if (target == null) return p.message("There is nothing here to protect.")
        protect(p, target)
    }

    /** Whether [patch] has a growing crop with a price the player carries (noted or not). */
    private fun affordable(p: PlayerRef, patch: Patch): Boolean {
        val state = patches.load(p, patch).state
        val price = crops.at(state.crop)?.let { payments.of(it.key) }.orEmpty()
        val inv = p.inv(Invs.INV)
        return state.phase == Phase.GROWING && !state.paid && price.isNotEmpty() &&
            price.all { inv.total(it.item) + inv.total(p.noted(it.item)) >= it.count }
    }

    /** F-P1: an item used on a farmer pays for the patch of his that the item is the price of (Wiki "Seeds": payment, noted or not). */
    private fun payWith(p: PlayerRef, owned: List<Patch>, obj: Int) {
        if (obj == ANY_ITEM) return p.message(NOTHING)
        val item = p.unnoted(obj)
        val target =
            nearbyPatches(p, owned).firstOrNull { patch ->
                val state = patches.load(p, patch).state
                state.phase == Phase.GROWING && crops.at(state.crop)?.let { payments.of(it.key).any { pay -> pay.item == item } } == true
            }
        if (target == null) return p.message(NOTHING)
        protect(p, target)
    }

    private fun nearbyPatches(p: PlayerRef, owned: List<Patch>): List<Patch> {
        val site = FarmingSites.nearestSite(p.coord.x, p.coord.z)
        if (site < 0) return emptyList()
        val here = FarmingSites.SITES[site].patches
        return owned.filter { o -> here.any { it === o } }
    }

    /**
     * F-P1: takes the payment for [patch]'s crop and marks the crop as protected until it is grown (Wiki "Farming#Disease, payment,
     * compost, and watering": farmers protect allotments, hops, bushes, trees, fruit trees and the special patches; payment may be
     * noted; a payment is for one patch and one crop).
     */
    private fun protect(p: PlayerRef, patch: Patch) {
        val cursor = patches.load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop)
        if (crop == null || state.phase != Phase.GROWING) return p.message("There is nothing growing there that I can look after.")
        val price = payments.of(crop.key)
        val problem =
            when {
                state.paid -> "I'm already looking after that for you."
                price.isEmpty() -> "I can't protect that crop."
                else -> null
            }
        if (problem != null) return p.message(problem)
        val inv = p.inv(Invs.INV)
        val missing = price.firstOrNull { inv.total(it.item) + inv.total(p.noted(it.item)) < it.count }
        if (missing != null) return p.message("I'll need ${missing.count} x ${p.objName(missing.item).lowercase()} for that.")
        val paid =
            p.transaction("farming-protect") {
                price.all { pay ->
                    val plain = minOf(inv.total(pay.item).toInt(), pay.count)
                    (plain == 0 || remove(Invs.INV, pay.item, plain)) &&
                        (pay.count - plain == 0 || remove(Invs.INV, p.noted(pay.item), pay.count - plain))
                }
            }
        if (!paid) return
        patches.store(p, patch, state.with(paid = true), cursor.anchor)
        p.message("You pay the farmer. They will look after your ${crop.name.lowercase()}.")
    }

    // ---- tool leprechaun

    /** Wiki "Tool Leprechaun": he notes farming produce, herbs and saplings used on him (not logs, baskets, sacks or untradeables). */
    private fun note(p: PlayerRef, obj: Int) {
        val farmed = obj != ANY_ITEM && tools.isNotable(obj)
        if (!farmed) return p.message(NOTHING)
        val noted = p.noted(obj)
        val count = p.inv(Invs.INV).total(obj).toInt()
        if (noted == obj || count == 0) return p.message("The leprechaun can't note that.")
        val done = p.transaction("farming-note") { remove(Invs.INV, obj, count) && add(Invs.INV, noted, count) }
        if (done) p.message("The leprechaun exchanges your ${p.objName(obj).lowercase()} for banknotes.")
    }

    private companion object {
        /** Op 1, 2, 3 and 5 of every patch loc ("Rake", "Harvest", "Pick" ...; "Inspect"; a spirit tree's "Inspect"; "Clear"). */
        const val OP_PRIMARY = 1
        const val OP_INSPECT = 2
        const val OP_SPIRIT_INSPECT = 3
        const val OP_CLEAR = 5

        /** Farmer ops "Pay" / "Pay (south-east)" (cache npc 2663 ...). */
        const val OP_PAY_FIRST = 3
        const val OP_PAY_SECOND = 4

        /** Any item in a use-on registration (plugin-api: -1). */
        const val ANY_ITEM = -1

        const val NOTHING = "Nothing interesting happens."
        const val NEED_SECATEURS = "You need secateurs to prune the diseased branches."
        const val NEED_CURE = "You need a plant cure to treat this."
        const val PERCENT = 100

        /** Wiki "Camphor tree" / "Ironwood tree" / "Rosewood tree": Woodcutting level and xp per log. */
        const val CAMPHOR_LEVEL = 66
        const val CAMPHOR_XP = 143.5
        const val IRONWOOD_LEVEL = 80
        const val IRONWOOD_XP = 175.0
        const val ROSEWOOD_LEVEL = 92
        const val ROSEWOOD_XP = 212.5

        /** Which [TreeKind] each farmed tree is chopped as. */
        val TREE_KINDS: Map<String, TreeKind> =
            mapOf(
                "OAK" to TreeKind.OAK,
                "WILLOW" to TreeKind.WILLOW,
                "MAPLE" to TreeKind.MAPLE,
                "YEW" to TreeKind.YEW,
                "MAGIC" to TreeKind.MAGIC,
                "TEAK" to TreeKind.TEAK,
                "MAHOGANY" to TreeKind.MAHOGANY,
                "REDWOOD" to TreeKind.REDWOOD,
            )

        /** Ticks between two checks of which farm the player is at. A lag of a few ticks only delays when patches are drawn. */
        const val SYNC_TICKS = 3

        val SYNC = TimerId("farming.sync")
    }
}
