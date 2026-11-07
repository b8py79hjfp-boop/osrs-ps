package tp.engine.move

import tp.engine.entity.Player

/**
 * Run energy per tick (docs/mechanics/run-energy.md, Wiki "Energy"): a tick in which the player ran (two steps) drains
 * `⌊60 + 67 × clamp(weight, 0, 64) / 64⌋ × (1 − agility / 300)` units (10,000 = 100%), times the content-set drain
 * permille (stamina potion 300, ring of endurance 850); any other tick restores `⌊agility / 10⌋ + 15` units times the
 * content-set restore permille (graceful), unless the player is delayed (agility obstacles, Wiki: no regeneration
 * while crossing an obstacle). At 0 run is switched off. Allocation-free: runs every tick per player.
 */
internal object RunEnergy {
    /** PlayerStats index of Agility (cache stat order). */
    private const val AGILITY = 16

    /** Cache inv ids: backpack `inv` 93, equipment `worn` 94 (weight counts both). */
    private const val INV = 93
    private const val WORN = 94

    /** Varp `option_run` (173): the client's run button. */
    private const val OPTION_RUN = 173

    private const val BASE_DRAIN = 60
    private const val WEIGHT_DRAIN = 67
    private const val MAX_WEIGHT_KG = 64
    private const val AGILITY_DIVISOR = 300
    private const val BASE_RESTORE = 15
    private const val RESTORE_LEVEL_DIVISOR = 10
    private const val PERMILLE = 1000
    private const val GRAMS_PER_KG = 1000

    fun process(player: Player) {
        val agility = player.stats.level(AGILITY)
        if (player.stepsThisTick >= 2) {
            val weightKg = (weightGrams(player) / GRAMS_PER_KG).coerceIn(0, MAX_WEIGHT_KG)
            val units = (BASE_DRAIN + WEIGHT_DRAIN * weightKg / MAX_WEIGHT_KG) * (AGILITY_DIVISOR - agility) / AGILITY_DIVISOR
            val drain = units * player.runDrainPermille / PERMILLE
            player.runEnergy -= minOf(drain, player.runEnergy)
            if (player.runEnergy == 0 && player.runMode) {
                player.runMode = false
                player.vars[OPTION_RUN] = 0
            }
        } else if (!player.isDelayed && player.runEnergy < Player.MAX_RUN_ENERGY) {
            val restore = (agility / RESTORE_LEVEL_DIVISOR + BASE_RESTORE) * player.runRestorePermille / PERMILLE
            player.runEnergy += restore
        }
    }

    /** Carried weight in grams (backpack + equipment; stackables weigh nothing in the cache). */
    private fun weightGrams(player: Player): Int = invGrams(player, INV) + invGrams(player, WORN)

    private fun invGrams(player: Player, id: Int): Int {
        val inv = player.invs[id] ?: return 0
        val objs = player.world.data.configs.objs
        var grams = 0
        for (slot in 0 until inv.size) {
            val obj = inv.objs[slot]
            if (obj >= 0) grams += (objs[obj]?.weight ?: 0) * inv.counts[slot]
        }
        return grams
    }
}
