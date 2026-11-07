package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Plant pots, seedlings and saplings (docs/mechanics/farming.md FS1-FS5; Wiki "Plant pot", "Seedling", "Sapling"): an empty plant
 * pot used on a clear patch with a gardening trowel in the backpack is filled with soil; a tree seed used on the filled pot makes a
 * seedling; watering it makes a "(w)" seedling, which becomes a sapling at the next five-minute growth tick, also in the bank and
 * while the player is offline.
 */
internal class FarmingSaplings(private val patches: FarmingPatches, private val chains: SaplingTable) {
    private val crops get() = patches.crops

    fun ContentScope.registerSaplings() {
        for (chain in chains.chains) {
            onUseOnItem(chain.seed, Objs.PLANTPOT_COMPOST) { plantSeed(this, chain) }
            for (can in FarmingItems.CANS.keys) {
                onUseOnItem(can, chain.seedling) { waterSeedling(this, chain, can) }
            }
        }
    }

    /** F-S1: an empty plant pot used on an empty, weed-free patch while carrying a gardening trowel fills it with soil. */
    fun fillPot(p: PlayerRef, patch: Patch) {
        val state = patches.load(p, patch).state
        val problem =
            when {
                state.phase != Phase.WEEDS || state.stage > 0 -> "The patch must be empty and free of weeds to fill a plant pot."
                p.inv(Invs.INV).total(Objs.GARDENING_TROWEL) == 0L -> "You need a gardening trowel to fill the plant pot."
                else -> null
            }
        if (problem != null) return p.message(problem)
        Production.start(
            p,
            Batch(1, Seqs.FARMING_FILLING_PLANTPOT, FarmingConst.PLANT_TICKS) {
                val done =
                    transaction("farming-fill-pot") {
                        remove(Invs.INV, Objs.PLANTPOT_EMPTY, 1) &&
                            add(Invs.INV, Objs.PLANTPOT_COMPOST, 1)
                    }
                if (done) message("You fill the plant pot with soil.")
                false
            },
            FarmingConst.PLANT_TICKS,
        )
    }

    /** F-S2: a tree seed on a filled plant pot, with a trowel and the Farming level of the sapling, makes a seedling. */
    private fun plantSeed(p: PlayerRef, chain: SaplingChain) {
        val crop = crops.bySeed[chain.sapling] ?: return
        val problem =
            when {
                p.inv(Invs.INV).total(Objs.GARDENING_TROWEL) == 0L -> "You need a gardening trowel to do that."
                p.level(Stats.FARMING) < crop.level -> "You need a Farming level of ${crop.level} to plant this seed."
                else -> null
            }
        if (problem != null) return p.message(problem)
        val done =
            p.transaction("farming-seedling") {
                remove(Invs.INV, chain.seed, 1) && remove(Invs.INV, Objs.PLANTPOT_COMPOST, 1) && add(Invs.INV, chain.seedling, 1)
            }
        if (done) p.message("You plant the ${p.objName(chain.seed).lowercase()} in the plant pot.")
    }

    /** F-S3: watering a seedling uses a can dose and starts the clock to the next growth tick. */
    private fun waterSeedling(p: PlayerRef, chain: SaplingChain, can: Int) {
        process(p)
        val doses = FarmingItems.CANS.getValue(can)
        val done =
            p.transaction("farming-water-seedling") {
                remove(Invs.INV, chain.seedling, 1) &&
                    add(Invs.INV, chain.wet, 1) &&
                    remove(Invs.INV, can, 1) &&
                    add(Invs.INV, FarmingItems.CAN_WITH[doses - 1], 1)
            }
        if (!done) return
        if (p.saved(PENDING) == null) p.setSaved(PENDING, p.epochMinutes)
        p.message("You water the seedling.")
    }

    /**
     * F-S4: turns the watered seedlings in the backpack and bank into saplings once a five-minute growth tick has passed since the
     * first of them was watered. A seedling watered later than that first one grows at the same tick (the Wiki says "within the
     * next five minutes"); the exact per-seedling time is not tracked (OPEN-FA5).
     */
    fun process(p: PlayerRef) {
        val since = p.saved(PENDING) ?: return
        if (p.epochMinutes / SAPLING_TICK_MIN <= since / SAPLING_TICK_MIN) return
        p.setSaved(PENDING, null)
        for (invId in intArrayOf(Invs.INV, Invs.BANK)) {
            val inv = p.inv(invId, stackAll = invId == Invs.BANK)
            for (slot in 0 until inv.size) {
                val chain = chains.byWet[inv.obj(slot)] ?: continue
                val count = inv.count(slot)
                p.transaction("farming-sapling-grows") { replaceAt(invId, slot, chain.wet, chain.sapling, count) }
            }
        }
    }

    private companion object {
        /** Wiki "Farming#Growth timing": tree seedlings grow on the five-minute tick. */
        const val SAPLING_TICK_MIN = 5L

        /** Saved: the minute the first still-growing watered seedling was watered. */
        const val PENDING = "farming.seedlings"
    }
}
