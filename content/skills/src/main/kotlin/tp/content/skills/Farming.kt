package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Farming (docs/mechanics/farming.md): the allotment, flower and herb patches of Falador, Catherby, Ardougne and Port
 * Phasmatys, the tree patches of Taverley, Falador, Varrock, Lumbridge and the Gnome Stronghold, and the fruit tree patches
 * of the Gnome Stronghold, Tree Gnome Village, Brimhaven and Catherby.
 *
 * Patches belong to the player: each one's state is a saved Long ([PatchState]) and the minute it was last brought up to
 * date, so crops keep growing while the player is offline ([FarmingGrowth]). The client draws a patch from a varbit shared by
 * all farms (`FARMING_TRANSMIT_A..D`), so while the player is at a farm the plugin writes that farm's patch states into
 * the varbits ([syncSite]); a click on a patch reports the patch's multi-loc parent id, which names the patch.
 */
public class Farming : ContentPlugin {
    private val crops = CropTable.load()
    private val varbits = VarbitTable.load()

    /** Which [TreeKind] each farmed tree is chopped as. */
    private val treeKinds: Map<String, TreeKind> =
        mapOf(
            "OAK" to TreeKind.OAK,
            "WILLOW" to TreeKind.WILLOW,
            "MAPLE" to TreeKind.MAPLE,
            "YEW" to TreeKind.YEW,
            "MAGIC" to TreeKind.MAGIC,
        )

    /** Axe -> woodcutting level it needs (cache param `levelrequire`). */
    private var axeLevels: Map<Int, Int> = emptyMap()

    /** What the plugin remembers of one player: the farm whose patches the varbits show, and a reusable [Cursor]. */
    private class Sync {
        var site = -1
        var drawnAt = Long.MIN_VALUE
        val cursor = Cursor()
    }

    override fun ContentScope.register() {
        onWorldStart { axeLevels = AXES.keys.associateWith { objParamInt(it, Skilling.PARAM_LEVEL_REQUIRE) ?: 1 } }
        for (site in FarmingData.SITES) {
            for (patch in site.patches) {
                onOpLoc(patch.loc, OP_PRIMARY) { act(player, patch) }
                onOpLoc(patch.loc, OP_INSPECT) { inspect(player, patch) }
                onUseOnLoc(ANY_ITEM, patch.loc) { useOn(player, patch, used?.obj ?: ANY_ITEM) }
            }
        }
        for (npc in FarmingData.LEPRECHAUNS) onUseOnNpc(ANY_ITEM, npc) { note(player, used?.obj ?: ANY_ITEM) }
        onLogin {
            syncSite()
            timer(SYNC, SYNC_TICKS, soft = true) { syncSite() }
        }
    }

    // ---- state

    private fun PlayerRef.sync(): Sync = this[SYNC_KEY] ?: Sync().also { this[SYNC_KEY] = it }

    /** Loads [patch] for [p], brings its growth up to date (saving it if it moved) and returns the player's cursor. */
    private fun load(p: PlayerRef, patch: Patch): Cursor {
        val cursor = p.sync().cursor
        cursor.state = p.saved(patch.stateKey)?.let { PatchState(it) } ?: PatchState.FRESH
        val now = p.epochMinutes
        cursor.anchor = p.saved(patch.timeKey) ?: now
        if (FarmingGrowth.advance(p, crops, cursor, now)) store(p, patch, cursor.state, cursor.anchor)
        return cursor
    }

    /** Saves [state] with its growth clock at [anchor] and redraws the patch if the player can see it. */
    private fun store(p: PlayerRef, patch: Patch, state: PatchState, anchor: Long) {
        p.setSaved(patch.stateKey, state.bits)
        p.setSaved(patch.timeKey, anchor)
        val site = p.sync().site
        if (site >= 0 && FarmingData.SITES[site].patches.any { it === patch }) p.setVarbit(patch.slot, render(patch, state))
    }

    /** Draws every patch of the nearest farm, and redraws them every [FarmingConst.REFRESH_TICKS] ticks while the player stays (F8). */
    private fun PlayerRef.syncSite() {
        val sync = sync()
        val site = FarmingData.nearestSite(coord.x, coord.z)
        val due = site >= 0 && tick - sync.drawnAt >= FarmingConst.REFRESH_TICKS
        if (site == sync.site && !due) return
        sync.site = site
        sync.drawnAt = tick
        if (site < 0) return
        for (patch in FarmingData.SITES[site].patches) {
            val state = load(this, patch).state
            setVarbit(patch.slot, render(patch, state))
        }
    }

    /** The varbit value that draws [state] on [patch] (F1). */
    private fun render(patch: Patch, state: PatchState): Int {
        val type = patch.type
        val crop = crops.at(state.crop)
        val key = crop?.key ?: WEEDS
        return when (state.phase) {
            Phase.WEEDS -> {
                varbits.plain(type, WEEDS, VarbitTable.CropState.GROWING, state.stage, 0)
            }

            Phase.GROWING -> {
                if (state.watered && type.canWater) {
                    varbits.watered(type, key, state.stage, 0)
                } else {
                    varbits.plain(type, key, VarbitTable.CropState.GROWING, state.stage, 0)
                }
            }

            Phase.DISEASED -> {
                varbits.plain(type, key, VarbitTable.CropState.DISEASED, state.stage, 0)
            }

            Phase.DEAD -> {
                varbits.plain(type, if (type == PatchType.HERB) ANY_HERB else key, VarbitTable.CropState.DEAD, state.stage, 0)
            }

            Phase.HARVESTABLE -> {
                varbits.plain(type, key, VarbitTable.CropState.HARVESTABLE, if (type == PatchType.FRUIT_TREE) state.stage else 0, 0)
            }

            Phase.CHECK -> {
                varbits.plain(type, key, VarbitTable.CropState.GROWING, crop?.cycles ?: 0, 0)
            }

            Phase.STUMP -> {
                varbits.last(type, key, VarbitTable.CropState.HARVESTABLE, 0, 0)
            }
        }
    }

    /** A cleared patch: no crop, raked, no compost; weeds grow back from [now] (F5). */
    private fun clear(p: PlayerRef, patch: Patch) {
        store(p, patch, PatchState.FRESH.with(stage = 0), p.epochMinutes)
    }

    // ---- clicks

    /** Op 1 of a patch: the action of whatever the patch holds, as the client labels it (Rake, Harvest, Pick, Cure, Clear ...). */
    private fun act(p: PlayerRef, patch: Patch) {
        val state = load(p, patch).state
        when (state.phase) {
            Phase.WEEDS -> if (state.stage > 0) rake(p, patch) else p.message("The patch is already clear.")
            Phase.GROWING -> p.message("The ${crops.at(state.crop)?.name?.lowercase() ?: "crop"} is still growing.")
            Phase.DISEASED -> treatDiseased(p, patch, ANY_ITEM)
            Phase.DEAD, Phase.STUMP -> dig(p, patch)
            Phase.HARVESTABLE -> harvestOrChop(p, patch, state)
            Phase.CHECK -> check(p, patch)
        }
    }

    /** Op 2 of a patch ("Inspect"). Texts are OPEN-FM10 (the Wiki's per-model examines are not imported). */
    private fun inspect(p: PlayerRef, patch: Patch) {
        val state = load(p, patch).state
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
            obj in FarmingItems.COMPOST -> compost(p, patch, obj)
            obj in FarmingItems.CANS -> water(p, patch, obj)
            obj == Objs.PLANT_CURE || obj in FarmingItems.SECATEURS -> treatDiseased(p, patch, obj)
            obj == Objs.SPADE -> act(p, patch)
            else -> p.message(NOTHING)
        }
    }

    private fun rakeClick(p: PlayerRef, patch: Patch) {
        val state = load(p, patch).state
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
        val cursor = load(p, patch)
        val state = cursor.state
        if (state.phase != Phase.WEEDS || state.stage == 0) return false
        if (!p.transaction("farming-rake") { addOrDrop(Invs.INV, Objs.WEEDS, 1) }) return false
        store(p, patch, state.with(stage = state.stage - 1), p.epochMinutes)
        p.addXp(Stats.FARMING, FarmingConst.RAKE_XP)
        return state.stage > 1
    }

    /** F3: sow [crop] on a raked patch: the seeds (or sapling) are used up, the planting xp is given at once (rev 242, OPEN-FM1). */
    private fun plant(p: PlayerRef, patch: Patch, crop: Crop) {
        val state = load(p, patch).state
        val tool = if (crop.type.isTree) Objs.SPADE else Objs.DIBBER
        val problem =
            when {
                crop.type != patch.type -> "You can't plant that in a ${patch.type.label}."
                state.phase == Phase.WEEDS && state.stage > 0 -> "You need to rake the weeds off the patch first."
                state.phase != Phase.WEEDS -> "There is already something growing here."
                p.level(Stats.FARMING) < crop.level -> "You need a Farming level of ${crop.level} to plant this."
                p.inv(Invs.INV).total(tool) == 0L -> "You need a ${p.objName(tool).lowercase()} to plant this."
                p.inv(Invs.INV).total(crop.seed) < crop.seedsPer -> "You need ${crop.seedsPer} seeds to plant in this patch."
                else -> null
            }
        if (problem != null) return p.message(problem)
        val anim = if (crop.type.isTree) Seqs.HUMAN_DIG else Seqs.FARMING_SEED_DIBBING
        Production.start(p, Batch(1, anim, FarmingConst.PLANT_TICKS) { plantNow(this, patch, crop) }, FarmingConst.PLANT_TICKS)
    }

    private fun plantNow(p: PlayerRef, patch: Patch, crop: Crop): Boolean {
        val state = load(p, patch).state
        if (state.phase != Phase.WEEDS || state.stage != 0) return false
        if (!p.transaction("farming-plant") { remove(Invs.INV, crop.seed, crop.seedsPer) }) return false
        store(p, patch, state.with(crop = crop.index, phase = Phase.GROWING, stage = 0, watered = false), p.epochMinutes)
        p.addXp(Stats.FARMING, crop.plantXp)
        p.message("You plant the ${p.objName(crop.seed).lowercase()} in the ${patch.type.label}.")
        return true
    }

    /** F4: compost, supercompost or ultracompost on a patch that has none (any stage but fully grown). */
    private fun compost(p: PlayerRef, patch: Patch, bucket: Int) {
        val state = load(p, patch).state
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
        val cursor = load(p, patch)
        val state = cursor.state
        if (state.compost > 0 || state.phase == Phase.HARVESTABLE || state.phase == Phase.CHECK) return false
        if (!p.transaction("farming-compost") { remove(Invs.INV, bucket, 1) && add(Invs.INV, Objs.BUCKET_EMPTY, 1) }) return false
        store(p, patch, state.with(compost = tier), cursor.anchor)
        p.addXp(Stats.FARMING, FarmingConst.COMPOST_XP[tier])
        p.message("You treat the ${patch.type.label} with ${p.objName(bucket).lowercase().replace("bucket of ", "")}.")
        return true
    }

    /** F4: watering a growing allotment or flower: the next growth tick's disease chance is halved (OPEN-FM3). */
    private fun water(p: PlayerRef, patch: Patch, can: Int) {
        val state = load(p, patch).state
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
        val cursor = load(p, patch)
        val state = cursor.state
        if (state.phase != Phase.GROWING || state.watered) return false
        val doses = FarmingItems.CANS.getValue(can)
        if (!p.transaction("farming-water") { remove(Invs.INV, can, 1) && add(Invs.INV, FarmingItems.CAN_WITH[doses - 1], 1) }) return false
        store(p, patch, state.with(watered = true), cursor.anchor)
        p.message("You water the ${patch.type.label}.")
        return true
    }

    /**
     * F4: a diseased crop is cured with plant cure (allotment, flower, herb) or pruned with secateurs, 3 in 4 times (trees). [tool] is
     * what the player used on the patch, or [ANY_ITEM] when they clicked "Cure" / "Prune" and the right tool is looked for.
     */
    private fun treatDiseased(p: PlayerRef, patch: Patch, tool: Int) {
        val state = load(p, patch).state
        if (state.phase != Phase.DISEASED) return p.message(NOTHING)
        val inv = p.inv(Invs.INV)
        val tree = patch.type.isTree
        val have =
            if (tree) {
                FarmingItems.SECATEURS.firstOrNull { inv.total(it) > 0 && (tool == ANY_ITEM || tool == it) }
            } else {
                Objs.PLANT_CURE.takeIf {
                    inv.total(it) >
                        0
                }
            }
        val wrongTool = if (tree) tool == Objs.PLANT_CURE else tool in FarmingItems.SECATEURS
        if (wrongTool) return p.message(NOTHING)
        if (have ==
            null
        ) {
            return p.message(if (tree) "You need secateurs to prune the diseased branches." else "You need a plant cure to treat this.")
        }
        val anim = if (tree) Seqs.PRUNING_ALL else Seqs.FARMING_PLANT_CURE
        Production.start(p, Batch(1, anim, FarmingConst.CURE_TICKS) { treatNow(this, patch, have) }, FarmingConst.CURE_TICKS)
    }

    private fun treatNow(p: PlayerRef, patch: Patch, tool: Int): Boolean {
        val state = load(p, patch).state
        if (state.phase != Phase.DISEASED) return false
        if (patch.type.isTree) {
            if (p.random(FarmingConst.PRUNE_FAIL_IN) == 0) {
                p.message("There are still some diseased leaves left.")
                return true
            }
            p.message("You have successfully removed all the diseased leaves.")
        } else {
            if (!p.transaction("farming-cure") { remove(Invs.INV, tool, 1) && add(Invs.INV, Objs.VIAL_EMPTY, 1) }) return false
            p.message("You treat the ${patch.type.label} with the plant cure.")
        }
        store(p, patch, state.with(phase = Phase.GROWING, watered = false), p.epochMinutes)
        return true
    }

    /** F5: a spade clears a dead crop at once; a stump may need several tries (OPEN-FM7: success is Farming level out of 99). */
    private fun dig(p: PlayerRef, patch: Patch) {
        if (p.inv(Invs.INV).total(Objs.SPADE) == 0L) return p.message("You need a spade to clear this patch.")
        Production.start(p, Batch(Int.MAX_VALUE, Seqs.HUMAN_DIG, FarmingConst.DIG_TICKS) { digOnce(this, patch) }, FarmingConst.DIG_TICKS)
    }

    private fun digOnce(p: PlayerRef, patch: Patch): Boolean {
        val phase = load(p, patch).state.phase
        if (phase != Phase.DEAD && phase != Phase.STUMP) return false
        if (phase == Phase.STUMP && p.random(Stats.MAX_LEVEL) >= p.level(Stats.FARMING)) return true
        clear(p, patch)
        p.message("You clear the ${patch.type.label}.")
        return false
    }

    /** F7: checking a fully grown tree's health gives its big xp and makes it choppable (tree) or fruiting (fruit tree). */
    private fun check(p: PlayerRef, patch: Patch) {
        val cursor = load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop) ?: return
        if (state.phase != Phase.CHECK) return
        val fruit = patch.type == PatchType.FRUIT_TREE
        store(p, patch, state.with(phase = Phase.HARVESTABLE, stage = if (fruit) FarmingConst.MAX_FRUIT else 0), p.epochMinutes)
        p.addXp(Stats.FARMING, crop.checkXp)
        p.message("You examine the tree for signs of disease and find that it is healthy.")
    }

    // ---- harvest

    private fun harvestOrChop(p: PlayerRef, patch: Patch, state: PatchState) {
        val crop = crops.at(state.crop) ?: return
        when {
            patch.type == PatchType.TREE || (patch.type == PatchType.FRUIT_TREE && state.stage == 0) -> {
                chop(p, patch, crop)
            }

            patch.type == PatchType.ALLOTMENT && p.inv(Invs.INV).total(Objs.SPADE) == 0L -> {
                p.message("You need a spade to harvest here.")
            }

            else -> {
                val anim = if (patch.type == PatchType.FRUIT_TREE) Seqs.PICKING_HIGH else Seqs.PICKING_LOW
                Production.start(
                    p,
                    Batch(Int.MAX_VALUE, anim, FarmingConst.PICK_TICKS) { harvestOnce(this, patch) },
                    FarmingConst.PICK_TICKS,
                )
            }
        }
    }

    /**
     * F6: one pick: the produce and its xp, then a roll to keep a harvest life (allotment, herb); the patch is cleared when none are
     * left. Fruit trees give one of their fruit per pick. Returns whether the player goes on picking.
     */
    private fun harvestOnce(p: PlayerRef, patch: Patch): Boolean {
        val cursor = load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop)
        if (crop == null || state.phase != Phase.HARVESTABLE) return false
        val fruit = patch.type == PatchType.FRUIT_TREE
        if (fruit && state.stage == 0) return false
        val amount = yield(p, crop)
        if (!p.transaction("farming-harvest") { add(Invs.INV, crop.produce, amount) }) {
            p.message("Your inventory is too full to hold any more ${p.objName(crop.produce).lowercase()}.")
            return false
        }
        p.addXp(Stats.FARMING, crop.harvestXp)
        if (fruit) {
            val anchor = if (state.stage == FarmingConst.MAX_FRUIT) p.epochMinutes else cursor.anchor
            store(p, patch, state.with(stage = state.stage - 1), anchor)
            return state.stage > 1
        }
        val lives = if (crop.type.usesLives && keepsLife(p, crop)) state.lives else state.lives - 1
        if (lives <= 0) {
            clear(p, patch)
            return false
        }
        store(p, patch, state.with(lives = lives), cursor.anchor)
        return true
    }

    /** Items from one pick: one, or for limpwurt three plus a bonus that grows with the level (OPEN-FM9). */
    private fun yield(p: PlayerRef, crop: Crop): Int {
        if (crop.key != FarmingConst.LIMPWURT_KEY) return 1
        val bonus = maxOf(0, (p.level(Stats.FARMING) - 1) / FarmingConst.LIMPWURT_STEP)
        return FarmingConst.LIMPWURT_MIN + p.random(bonus + 1)
    }

    /** F6: the roll to keep a harvest life; magic secateurs (held or wielded) raise both constants by 10%. */
    private fun keepsLife(p: PlayerRef, crop: Crop): Boolean {
        val magic =
            p.inv(Invs.INV).total(Objs.FAIRY_ENCHANTED_SECATEURS) > 0 ||
                p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT) == Objs.FAIRY_ENCHANTED_SECATEURS
        val low = if (magic) crop.ctsLow * FarmingConst.SECATEURS_PERCENT / PERCENT else crop.ctsLow
        val high = if (magic) crop.ctsHigh * FarmingConst.SECATEURS_PERCENT / PERCENT else crop.ctsHigh
        return p.statRandom(Stats.FARMING, low, high)
    }

    // ---- chopping

    /** F7: a grown tree is chopped like any tree of its kind and falls with chance 1/8 per log (OPEN-FM6 for the fruit trees). */
    private fun chop(p: PlayerRef, patch: Patch, crop: Crop) {
        val kind = treeKinds[crop.key]
        if (kind != null && p.level(Stats.WOODCUTTING) < kind.level) {
            return p.message("You need a Woodcutting level of ${kind.level} to chop down this tree.")
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
        val cursor = load(p, patch)
        val state = cursor.state
        val crop = crops.at(state.crop)
        if (crop == null || state.phase != Phase.HARVESTABLE) return false
        val kind = treeKinds[crop.key]
        if (kind == null) {
            // OPEN-FM6: a fruit tree falls to one successful swing and gives no logs.
            store(p, patch, state.with(phase = Phase.STUMP, stage = 0), p.epochMinutes)
            return false
        }
        val axe = Skilling.bestTool(p, Stats.WOODCUTTING, axeLevels)
        if (axe < 0) return false
        val tier = AXES.getValue(axe).tier
        if (!p.statRandom(Stats.WOODCUTTING, kind.rates.low(tier), kind.rates.high(tier))) return true
        if (!p.transaction("farming-chop") { add(Invs.INV, kind.logs, 1) }) {
            p.message("Your inventory is too full to hold any more ${p.objName(kind.logs).lowercase()}.")
            return false
        }
        p.addXp(Stats.WOODCUTTING, kind.xp)
        p.message("You get some ${p.objName(kind.logs).lowercase()}.", Skilling.SPAM)
        if (p.random(FarmingConst.TREE_DEPLETE_IN) != 0) return true
        store(p, patch, state.with(phase = Phase.STUMP, stage = 0), p.epochMinutes)
        return false
    }

    // ---- tool leprechaun

    /** Wiki "Tool Leprechaun": the leprechaun notes any farming produce used on him. */
    private fun note(p: PlayerRef, obj: Int) {
        if (obj == ANY_ITEM || crops.crops.none { it.produce == obj || it.clean == obj }) return p.message(NOTHING)
        val noted = p.noted(obj)
        val count = p.inv(Invs.INV).total(obj).toInt()
        if (noted == obj || count == 0) return p.message("The leprechaun can't note that.")
        val done = p.transaction("farming-note") { remove(Invs.INV, obj, count) && add(Invs.INV, noted, count) }
        if (done) p.message("The leprechaun exchanges your ${p.objName(obj).lowercase()} for banknotes.")
    }

    private companion object {
        /** Op 1 and op 2 of every patch loc ("Rake", "Harvest", "Pick", "Cure", "Clear", "Check-health" ...; "Inspect"). */
        const val OP_PRIMARY = 1
        const val OP_INSPECT = 2

        /** Any item in a use-on registration (plugin-api: -1). */
        const val ANY_ITEM = -1

        const val NOTHING = "Nothing interesting happens."
        const val WEEDS = "WEEDS"
        const val ANY_HERB = "ANYHERB"
        const val PERCENT = 100

        /** Ticks between two checks of which farm the player is at. A lag of a few ticks only delays when patches are drawn. */
        const val SYNC_TICKS = 3

        val SYNC = TimerId("farming.sync")
        val SYNC_KEY = AttrKey<Sync>("farming.sync")
    }
}
