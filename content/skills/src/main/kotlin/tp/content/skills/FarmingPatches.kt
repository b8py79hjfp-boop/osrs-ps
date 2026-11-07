package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.PlayerRef

/**
 * The saved state of a player's patches and how it is drawn (docs/mechanics/farming.md F1, F2, F8). Every patch's state is a saved
 * Long ([PatchState]) and the minute it was last brought up to date, so crops keep growing while the player is offline
 * ([FarmingGrowth]). The client draws a patch from a varbit shared by all farms, so while the player is at a farm the plugin
 * writes that farm's patch states into the varbits ([drawSite]).
 */
internal class FarmingPatches(val crops: CropTable, val varbits: VarbitTable) {
    /** What is remembered of one player: the farm whose patches the varbits show, and reusable cursors. */
    class Sync {
        var site = -1
        var drawnAt = Long.MIN_VALUE
        val cursor = Cursor()
        val anima = Cursor()
        val flower = Cursor()
    }

    /** The flower patch of the farm each allotment patch belongs to (Wiki "Farming#Allotment": flowers protect the allotments). */
    private val flowerOf: Map<Patch, Patch> =
        FarmingSites.SITES
            .flatMap { site ->
                val flower = site.patches.firstOrNull { it.type == PatchType.FLOWER }
                if (flower == null) emptyList() else site.patches.filter { it.type == PatchType.ALLOTMENT }.map { it to flower }
            }.toMap()

    fun sync(p: PlayerRef): Sync = p[SYNC_KEY] ?: Sync().also { p[SYNC_KEY] = it }

    /** The live anima plant of the player (Attas, Iasor or Kronos in the Farming Guild's anima patch), or null (Wiki "Anima seed"). */
    fun anima(p: PlayerRef): Crop? {
        val patch = FarmingSites.ANIMA_PATCH
        val saved = p.saved(patch.stateKey) ?: return null
        val cursor = sync(p).anima
        val now = p.epochMinutes
        cursor.state = PatchState(saved)
        cursor.anchor = p.saved(patch.timeKey) ?: now
        FarmingGrowth.advance(p, crops, cursor, now, immune = true, iasor = false)
        return crops.at(cursor.state.crop)?.takeIf { cursor.state.phase == Phase.GROWING }
    }

    /** Loads [patch] for [p], brings its growth up to date (saving it if it moved) and returns the player's cursor. */
    fun load(p: PlayerRef, patch: Patch): Cursor {
        val cursor = sync(p).cursor
        cursor.state = p.saved(patch.stateKey)?.let { PatchState(it) } ?: PatchState.FRESH
        val now = p.epochMinutes
        cursor.anchor = p.saved(patch.timeKey) ?: now
        val growing = cursor.state.phase == Phase.GROWING
        val iasor = patch.type != PatchType.ANIMA && growing && anima(p)?.key == IASOR
        val immune = patch.neverDiseased || (growing && flowerProtects(p, patch, cursor.state.crop))
        if (FarmingGrowth.advance(p, crops, cursor, now, immune, iasor)) store(p, patch, cursor.state, cursor.anchor)
        return cursor
    }

    /**
     * Whether a fully grown flower in the farm's flower patch protects the allotment crop [cropIndex] from disease (Wiki "Allotment
     * patch/Seeds", Flower column: marigold for potatoes, onions and tomatoes, rosemary for cabbages, nasturtium for watermelons, white
     * lily for every crop). The flower is judged as it stands when the allotment is looked at, grown if it would have grown without
     * disease, not tick by tick (OPEN-FA14); the scarecrow is not implemented.
     */
    private fun flowerProtects(p: PlayerRef, patch: Patch, cropIndex: Int): Boolean {
        val flower = flowerOf[patch] ?: return false
        val crop = crops.at(cropIndex) ?: return false
        val saved = p.saved(flower.stateKey) ?: return false
        val now = p.epochMinutes
        val cursor = sync(p).flower
        cursor.state = PatchState(saved)
        cursor.anchor = p.saved(flower.timeKey) ?: now
        FarmingGrowth.advance(p, crops, cursor, now, immune = true, iasor = false)
        if (cursor.state.phase != Phase.HARVESTABLE) return false
        val grown = crops.at(cursor.state.crop)?.key ?: return false
        return grown == WHITE_LILY || FLOWER_PROTECTS[crop.key] == grown
    }

    /** Saves [state] with its growth clock at [anchor] and redraws the patch if the player can see it. */
    fun store(p: PlayerRef, patch: Patch, state: PatchState, anchor: Long) {
        p.setSaved(patch.stateKey, state.bits)
        p.setSaved(patch.timeKey, anchor)
        val site = sync(p).site
        if (site >= 0 && FarmingSites.SITES[site].patches.any { it === patch }) p.setVarbit(patch.slot, render(patch, state))
    }

    /** A cleared patch: no crop, raked, no compost, no protection; weeds grow back from now (F5). */
    fun clear(p: PlayerRef, patch: Patch) {
        store(p, patch, PatchState.FRESH.with(stage = 0), p.epochMinutes)
    }

    /** Writes every patch of site [site] into the shared varbits. */
    fun drawSite(p: PlayerRef, site: Int) {
        for (patch in FarmingSites.SITES[site].patches) {
            val state = load(p, patch).state
            p.setVarbit(patch.slot, render(patch, state))
        }
    }

    /** The varbit value that draws [state] on [patch] (F1). */
    fun render(patch: Patch, state: PatchState): Int {
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
                renderGrown(type, key, crop, state)
            }

            Phase.CHECK -> {
                val check = varbits.last(type, key, VarbitTable.CropState.GROWING, crop?.cycles ?: 0, -1)
                if (check >= 0) check else renderGrown(type, key, crop, state.with(lives = 1))
            }

            Phase.STUMP -> {
                varbits.last(type, key, VarbitTable.CropState.HARVESTABLE, 0, 0)
            }
        }
    }

    /**
     * A fully grown crop: the harvestable look with the fruit still on a fruit tree, mushroom or calquat, the full bush or cactus
     * (the Wiki "Bush Yield Improvements": only full or bare), else the plain one; anima plants and spirit trees have only a grown look.
     */
    private fun renderGrown(type: PatchType, key: String, crop: Crop?, state: PatchState): Int {
        val harvestStage =
            when {
                type == PatchType.MUSHROOM -> state.stage - 1
                type.harvest == Harvest.FRUIT -> state.stage
                crop?.regrows == true -> if (state.lives > 0) varbits.maxStage(type, key, VarbitTable.CropState.HARVESTABLE) else 0
                else -> 0
            }
        val grown = varbits.plain(type, key, VarbitTable.CropState.HARVESTABLE, harvestStage, -1)
        if (grown >= 0) return grown
        val last = crop?.cycles ?: 0
        return varbits.plain(
            type,
            key,
            VarbitTable.CropState.GROWING,
            last,
            varbits.plain(
                type,
                key,
                VarbitTable.CropState.GROWING,
                last - 1,
                0,
            ),
        )
    }

    companion object {
        const val WEEDS = "WEEDS"
        const val ANY_HERB = "ANYHERB"

        /** RuneLite `Produce` key of the Iasor plant (`farming-varbits.tsv`). */
        const val IASOR = "IASOR"
        const val ATTAS = "ATTAS"
        const val WHITE_LILY = "WHITE_LILY"

        /** Wiki "Allotment patch/Seeds" (Flower column): allotment crop -> the flower that protects it besides the white lily. */
        val FLOWER_PROTECTS: Map<String, String> =
            mapOf(
                "POTATO" to "MARIGOLD",
                "ONION" to "MARIGOLD",
                "TOMATO" to "MARIGOLD",
                "CABBAGE" to "ROSEMARY",
                "WATERMELON" to "NASTURTIUM",
            )

        val SYNC_KEY = AttrKey<Sync>("farming.sync")
    }
}
