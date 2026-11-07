package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.symbols.Varps

/** What a pool of the superior garden restores: each tier adds to the one before (Wiki "Superior garden", Pool table). */
private enum class Restores { SPECIAL, RUN_ENERGY, PRAYER, STATS, HITPOINTS }

/**
 * The superior garden's pools (docs/mechanics/construction.md CO25): op 1 "Drink" restores special attack energy
 * (restoration pool), run energy (revitalisation), prayer (rejuvenation), every reduced stat (fancy) and hitpoints (ornate).
 * The pools are upgraded in place by [PohUpgrades].
 */
public class PohPools : ContentPlugin {
    override fun ContentScope.register() {
        for ((index, loc) in POOLS.withIndex()) onOpLoc(loc, OP_DRINK) { drink(player, index) }
    }

    private fun drink(p: PlayerRef, tier: Int) {
        val lastPvp = p[SharedAttrs.LAST_PVP_TICK]
        if (lastPvp != null && p.tick - lastPvp < PVP_COMBAT_TICKS) return p.message(IN_COMBAT)
        if (tier >= Restores.SPECIAL.ordinal) p.setVarp(Varps.SA_ENERGY, FULL_SPECIAL)
        if (tier >= Restores.RUN_ENERGY.ordinal) p.runEnergy = FULL_RUN_ENERGY
        if (tier >= Restores.PRAYER.ordinal) p.setLevel(Stats.PRAYER, p.baseLevel(Stats.PRAYER))
        if (tier >= Restores.STATS.ordinal) {
            for (stat in 0..Stats.SAILING) {
                if (stat != Stats.HITPOINTS && p.level(stat) < p.baseLevel(stat)) p.setLevel(stat, p.baseLevel(stat))
            }
        }
        if (tier >= Restores.HITPOINTS.ordinal) p.setLevel(Stats.HITPOINTS, p.baseLevel(Stats.HITPOINTS))
        p.message("You feel the pool's waters restore you.")
    }

    private companion object {
        /**
         * Ticks after the last PvP attack during which the pools refuse (Wiki "Restoration pool": "recently in PvP combat"; the
         * time is OPEN-CO30: the logout lock of 16 ticks, wilderness.md W7, is used).
         */
        const val PVP_COMBAT_TICKS = 16

        /** Wiki "Restoration pool": the refusal message. */
        const val IN_COMBAT = "You can't drink from the pool during combat."

        /** Cache 2757: op 1 "Drink" on the five pools. */
        const val OP_DRINK = 1

        /** Cache 2757: `Pool of Restoration` .. `Ornate pool of Rejuvenation` (Wiki infoboxes id1), in tier order. */
        val POOLS = listOf(29237, 29238, 29239, 29240, 29241)

        /** A full special attack bar in varp `sa_energy` (content/combat `Specials.FULL_ENERGY`, 1000 = 100%). */
        const val FULL_SPECIAL = 1000

        /** Run energy is stored in hundredths of a percent (a full bar is 10,000; MIN to run is 100 = 1%). */
        const val FULL_RUN_ENERGY = 10_000
    }
}
